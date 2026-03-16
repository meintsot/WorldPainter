package org.pepsoft.worldpainter.hytale;

import org.pepsoft.minecraft.ChunkStore;
import org.pepsoft.minecraft.Chunk;
import org.pepsoft.minecraft.MinecraftCoords;
import org.pepsoft.util.ProgressReceiver;
import org.pepsoft.worldpainter.*;
import org.pepsoft.worldpainter.history.HistoryEntry;
import org.pepsoft.worldpainter.importing.MapImporter;
import org.pepsoft.worldpainter.layers.ReadOnly;

import java.awt.Point;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.pepsoft.worldpainter.Constants.*;
import static org.pepsoft.worldpainter.DefaultPlugin.HYTALE;
import static org.pepsoft.worldpainter.Dimension.Anchor;

/**
 * Imports an existing Hytale server world into WorldPainter. Reads chunks via
 * {@link HytaleChunkStore}, maps blocks to {@link HytaleTerrain}, and preserves
 * environment &amp; fluid data for round-trip fidelity.
 */
public class HytaleMapImporter extends MapImporter {

    private static final Logger logger = Logger.getLogger(HytaleMapImporter.class.getName());

    private static final int HYTALE_CHUNK_SIZE = HytaleChunk.CHUNK_SIZE; // 32
    /** Number of Hytale chunks per WP tile side: 128 / 32 = 4 */
    private static final int CHUNKS_PER_TILE = TILE_SIZE / HYTALE_CHUNK_SIZE; // 4
    /** Bit shift: log2(4) = 2 */
    private static final int CHUNKS_PER_TILE_BITS = 2;

    private final File worldDir;
    private final TileFactory tileFactory;
    private final Set<MinecraftCoords> chunksToSkip;
    private final ReadOnlyOption readOnlyOption;
    private String warnings;

    public HytaleMapImporter(File worldDir, TileFactory tileFactory,
                             Set<MinecraftCoords> chunksToSkip,
                             ReadOnlyOption readOnlyOption) {
        this.worldDir = Objects.requireNonNull(worldDir);
        this.tileFactory = Objects.requireNonNull(tileFactory);
        this.chunksToSkip = chunksToSkip;
        this.readOnlyOption = (readOnlyOption != null) ? readOnlyOption : ReadOnlyOption.NONE;
    }

    @Override
    public World2 doImport(ProgressReceiver progressReceiver) throws IOException, ProgressReceiver.OperationCancelled {
        logger.info("Importing Hytale world from " + worldDir.getAbsolutePath());
        final long start = System.currentTimeMillis();

        // 1. Create World2
        final World2 world = new World2(HYTALE, tileFactory.getMinHeight(), tileFactory.getMaxHeight());
        world.setName(worldDir.getName());
        world.setCreateGoodiesChest(false);
        world.setImportedFrom(new File(worldDir, "config.json"));
        world.addHistoryEntry(HistoryEntry.WORLD_IMPORTED_FROM_MINECRAFT_MAP, worldDir.getName(), worldDir);

        // 2. Create Dimension
        final long seed = new Random().nextLong();
        tileFactory.setSeed(seed);
        final Anchor anchor = new Anchor(DIM_NORMAL, Dimension.Role.DETAIL, false, 0);
        final Dimension dimension = new Dimension(world, "Surface", seed, tileFactory, anchor);
        dimension.setEventsInhibited(true);

        final HytaleImportBlockMapper blockMapper = new HytaleImportBlockMapper();

        try {
            dimension.setCoverSteepTerrain(false);
            dimension.setSubsurfaceMaterial(Terrain.STONE);
            dimension.setBorderLevel(62);

            // 3. Iterate chunks
            try (ChunkStore chunkStore = new HytaleChunkStore(worldDir, 0, HytaleChunk.DEFAULT_MAX_HEIGHT)) {
                final Set<MinecraftCoords> allCoords = chunkStore.getChunkCoords();
                final int totalChunks = allCoords.size();
                final AtomicInteger processedCount = new AtomicInteger();

                logger.info("Found " + totalChunks + " chunks to import");

                for (MinecraftCoords coords : allCoords) {
                    if (progressReceiver != null) {
                        progressReceiver.setProgress((float) processedCount.getAndIncrement() / totalChunks);
                    }

                    if (chunksToSkip != null && chunksToSkip.contains(coords)) {
                        skipChunkCount++;
                        continue;
                    }

                    final Chunk chunk;
                    try {
                        chunk = chunkStore.getChunk(coords.x, coords.z);
                    } catch (Exception e) {
                        nullChunkCount++;
                        if (nullChunkCount <= 10) {
                            logger.warning("Exception reading chunk at " + coords.x + "," + coords.z + ": " + e.getMessage());
                        }
                        continue;
                    }
                    if (chunk == null) {
                        nullChunkCount++;
                        if (nullChunkCount <= 10) {
                            logger.warning("Chunk at " + coords.x + "," + coords.z + " returned null");
                        }
                        continue;
                    }

                    importChunk((HytaleChunk) chunk, coords.x, coords.z, dimension, blockMapper);
                }

                logger.info("Import summary: " + totalChunks + " enumerated, "
                    + importChunkCount + " imported, " + nullChunkCount + " null/failed, "
                    + skipChunkCount + " skipped, " + dimension.getTileCount() + " tiles created");
            }

        // 4. Read-only is now applied per-chunk in importChunk()

        } finally {
            dimension.setEventsInhibited(false);
        }

        world.addDimension(dimension);

        // 5. Build warnings (shown to user via dialog)
        final StringBuilder sb = new StringBuilder();

        // Chunk failure summary
        if (nullChunkCount > 0) {
            sb.append("Import summary: ").append(importChunkCount).append(" chunks imported, ")
                .append(nullChunkCount).append(" chunks failed to load (those areas will be blank).\n\n");
        }

        // Unmapped block IDs
        final Set<String> unmapped = blockMapper.getUnmappedBlockIds();
        if (!unmapped.isEmpty()) {
            sb.append("The following ").append(unmapped.size())
                .append(" block ID(s) were not mapped and used fallback terrain (STONE):\n");
            for (String id : unmapped) {
                sb.append("  - ").append(id).append('\n');
            }
        }

        if (sb.length() > 0) {
            warnings = sb.toString();
            logger.warning(warnings);
        }

        logger.info("Hytale import completed in " + (System.currentTimeMillis() - start) + " ms");
        return world;
    }

    @Override
    public String getWarnings() {
        return warnings;
    }

    private int importChunkCount = 0;
    private int nullChunkCount = 0;
    private int skipChunkCount = 0;

    private void importChunk(HytaleChunk chunk, int chunkX, int chunkZ,
                             Dimension dimension, HytaleImportBlockMapper mapper) {
        // Tile coords: 4 Hytale chunks per WP tile side
        final int tileX = chunkX >> CHUNKS_PER_TILE_BITS;
        final int tileZ = chunkZ >> CHUNKS_PER_TILE_BITS;
        final Point tileCoords = new Point(tileX, tileZ);

        Tile tile = dimension.getTile(tileCoords);
        if (tile == null) {
            tile = tileFactory.createTile(tileCoords.x, tileCoords.y);
            dimension.addTile(tile);
        }

        // Offset within tile: 0, 32, 64, or 96
        final int offX = (chunkX & (CHUNKS_PER_TILE - 1)) * HYTALE_CHUNK_SIZE;
        final int offZ = (chunkZ & (CHUNKS_PER_TILE - 1)) * HYTALE_CHUNK_SIZE;

        if (importChunkCount < 5) {
            int minH = Integer.MAX_VALUE, maxH = Integer.MIN_VALUE;
            int nonAirCount = 0;
            for (int lx = 0; lx < HYTALE_CHUNK_SIZE; lx++) {
                for (int lz = 0; lz < HYTALE_CHUNK_SIZE; lz++) {
                    int h = chunk.getHeight(lx, lz);
                    if (h < minH) minH = h;
                    if (h > maxH) maxH = h;
                    HytaleBlock b = chunk.getHytaleBlock(lx, h, lz);
                    if (b != null && !b.isEmpty()) nonAirCount++;
                }
            }
            logger.info("importChunk: global=(" + chunkX + "," + chunkZ + ") \u2192 tile=(" + tileX + "," + tileZ + ") offset=(" + offX + "," + offZ + ")"
                + " heightRange=[" + minH + "," + maxH + "] blocksAtHeight=" + nonAirCount);
        }

        // Reset per-chunk man-made tracking
        chunkHasManMadeAboveGround = false;
        chunkHasManMadeBelowGround = false;

        for (int localX = 0; localX < HYTALE_CHUNK_SIZE; localX++) {
            for (int localZ = 0; localZ < HYTALE_CHUNK_SIZE; localZ++) {
                final int tilePixelX = offX + localX;
                final int tilePixelZ = offZ + localZ;

                importColumn(chunk, localX, localZ, tile, tilePixelX, tilePixelZ, mapper);
            }
        }

        // Apply per-chunk read-only based on option
        boolean applyReadOnly = false;
        switch (readOnlyOption) {
            case ALL:
                applyReadOnly = true;
                break;
            case MAN_MADE:
                applyReadOnly = chunkHasManMadeAboveGround || chunkHasManMadeBelowGround;
                break;
            case MAN_MADE_ABOVE_GROUND:
                applyReadOnly = chunkHasManMadeAboveGround;
                break;
            default:
                break;
        }
        if (applyReadOnly) {
            for (int lx = 0; lx < HYTALE_CHUNK_SIZE; lx++) {
                for (int lz = 0; lz < HYTALE_CHUNK_SIZE; lz++) {
                    tile.setBitLayerValue(ReadOnly.INSTANCE, offX + lx, offZ + lz, true);
                }
            }
        }

        // Preserve PrefabMarker data as HytalePrefabLayer values
        for (HytaleChunk.PrefabMarker marker : chunk.getPrefabMarkers()) {
            int prefabValue = mapPrefabCategory(marker.category, marker.prefabPath);
            if (prefabValue > 0) {
                int px = offX + marker.x;
                int pz = offZ + marker.z;
                if (px >= 0 && px < TILE_SIZE && pz >= 0 && pz < TILE_SIZE) {
                    tile.setLayerValue(HytalePrefabLayer.INSTANCE, px, pz, prefabValue);
                }
            }
        }

        importChunkCount++;
    }

    private void importColumn(HytaleChunk chunk, int localX, int localZ,
                              Tile tile, int tilePixelX, int tilePixelZ,
                              HytaleImportBlockMapper mapper) {
        // Use heightmap from chunk
        final int height = chunk.getHeight(localX, localZ);

        // Find the topmost solid block to determine terrain type
        HytaleTerrain hytaleTerrain = null;
        Terrain mcTerrain = Terrain.STONE;
        int surfaceY = height;
        boolean surfaceFound = false;
        final boolean needManMadeCheck = (readOnlyOption == ReadOnlyOption.MAN_MADE
                                       || readOnlyOption == ReadOnlyOption.MAN_MADE_ABOVE_GROUND);

        for (int y = height; y >= 0; y--) {
            HytaleBlock block = chunk.getHytaleBlock(localX, y, localZ);
            if (block != null && !block.isEmpty()) {
                // Man-made block detection
                if (needManMadeCheck && !mapper.isNatural(block.id)) {
                    if (!surfaceFound) {
                        chunkHasManMadeAboveGround = true;
                    } else {
                        chunkHasManMadeBelowGround = true;
                    }
                }

                // Surface terrain detection
                if (!surfaceFound) {
                    hytaleTerrain = mapper.map(block.id);
                    if (hytaleTerrain != null) {
                        mcTerrain = HytaleTerrainHelper.toMinecraftTerrain(hytaleTerrain);
                        surfaceY = y;
                        surfaceFound = true;
                        // Only continue scanning below surface for MAN_MADE (underground detection)
                        if (readOnlyOption != ReadOnlyOption.MAN_MADE) {
                            break;
                        }
                    }
                }
            }
        }

        // Set terrain height (WP uses float, half-block offset for best appearance)
        float wpHeight = Math.max(surfaceY - 0.4375f, 0);
        tile.setHeight(tilePixelX, tilePixelZ, wpHeight);
        tile.setTerrain(tilePixelX, tilePixelZ, mcTerrain);

        // Store native HytaleTerrain index for round-trip fidelity
        if (hytaleTerrain != null) {
            int layerIndex = hytaleTerrain.getLayerIndex();
            if (layerIndex > 0) {
                HytaleTerrainLayer.setTerrainIndex(tile, tilePixelX, tilePixelZ, layerIndex);
            }
        }

        // Detect water level from fluid data
        int waterLevel = 0; // Hytale minZ = 0
        for (int y = height + 1; y < HytaleChunk.DEFAULT_MAX_HEIGHT && y <= height + 32; y++) {
            HytaleChunk.HytaleSection section = chunk.getSections()[y >> 5];
            if (section != null) {
                int fluidId = section.getFluidId(localX, y & 31, localZ);
                if (fluidId > 0) {
                    // Found fluid above surface
                    waterLevel = y;
                    // Look for top of fluid column
                    for (int fy = y + 1; fy < HytaleChunk.DEFAULT_MAX_HEIGHT; fy++) {
                        HytaleChunk.HytaleSection fSec = chunk.getSections()[fy >> 5];
                        if (fSec == null || fSec.getFluidId(localX, fy & 31, localZ) == 0) {
                            waterLevel = fy - 1;
                            break;
                        }
                    }
                    break;
                }
            }
        }
        tile.setWaterLevel(tilePixelX, tilePixelZ, waterLevel);

        // Fluid type layer (detect lava vs water varieties)
        if (waterLevel > surfaceY) {
            HytaleChunk.HytaleSection sec = chunk.getSections()[waterLevel >> 5];
            if (sec != null) {
                int fId = sec.getFluidId(localX, waterLevel & 31, localZ);
                if (fId > 0) {
                    String fluidName = sec.getFluidPalette().get(fId);
                    int fluidLayerValue = mapFluidToLayer(fluidName);
                    if (fluidLayerValue > 0) {
                        tile.setLayerValue(HytaleFluidLayer.INSTANCE, tilePixelX, tilePixelZ, fluidLayerValue);
                    }
                }
            }
        }

        // Environment layer
        String envName = chunk.getEnvironment(localX, localZ);
        if (envName != null && !envName.equals("Default")) {
            HytaleEnvironmentData envData = HytaleEnvironmentData.getByName(envName);
            if (envData != null && envData.getId() > 0 && envData.getId() < 255) {
                tile.setLayerValue(HytaleEnvironmentLayer.INSTANCE, tilePixelX, tilePixelZ, envData.getId());
            }
        }
    }

    /**
     * Map a fluid name from the chunk palette to a {@link HytaleFluidLayer} value.
     */
    private static int mapFluidToLayer(String fluidName) {
        if (fluidName == null || fluidName.equals("Empty")) return 0;
        if (fluidName.contains("Lava"))  return HytaleFluidLayer.FLUID_LAVA;
        if (fluidName.contains("Water")) return HytaleFluidLayer.FLUID_ZONE1_WATER;
        return 0;
    }

    /**
     * Map a PrefabMarker category (and optional path) to a {@link HytalePrefabLayer} constant.
     */
    private static int mapPrefabCategory(String category, String prefabPath) {
        if (category == null) return 0;
        switch (category) {
            case "Trees":
            case "TestTree":
                return HytalePrefabLayer.PREFAB_TREES;
            case "Rock_Formations":
                return HytalePrefabLayer.PREFAB_ROCKS;
            case "Plants":
                return HytalePrefabLayer.PREFAB_PLANTS;
            case "Cave":
                return HytalePrefabLayer.PREFAB_CAVE;
            case "Dungeon":
                return HytalePrefabLayer.PREFAB_DUNGEON;
            case "Npc":
                return HytalePrefabLayer.PREFAB_NPC_SETTLEMENT;
            case "Mineshaft":
            case "Mineshaft_Drift":
                return HytalePrefabLayer.PREFAB_MINESHAFT;
            case "Monuments":
                if (prefabPath != null) {
                    if (prefabPath.contains("Encounter")) return HytalePrefabLayer.PREFAB_MONUMENT_ENCOUNTER;
                    if (prefabPath.contains("Story"))     return HytalePrefabLayer.PREFAB_MONUMENT_STORY;
                    if (prefabPath.contains("Unique"))    return HytalePrefabLayer.PREFAB_MONUMENT_UNIQUE;
                }
                return HytalePrefabLayer.PREFAB_MONUMENT_INCIDENTAL;
            default:
                return 0;
        }
    }

    // Per-chunk man-made structure tracking (reset in importChunk)
    private boolean chunkHasManMadeAboveGround;
    private boolean chunkHasManMadeBelowGround;
}
