package org.pepsoft.worldpainter.hytale.export;

import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.hytale.*;
import org.pepsoft.worldpainter.hytale.chunk.HytaleChunk;
import org.pepsoft.worldpainter.layers.FloodWithLava;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Post-export passes that run over the populated chunk map after all layer
 * processing has completed. Each pass walks every chunk and applies one
 * invariant:
 *
 * <ul>
 *   <li>{@link #sealFluidBodies} — restore fluid in columns that should have
 *       water/lava/etc., including underground voids and above-terrain
 *       columns up to the water level.</li>
 *   <li>{@link #convertCoveredGrass} — turn grass into dirt when buried by a
 *       solid block, pre-applying Hytale's runtime conversion.</li>
 *   <li>{@link #enforceVoidColumns} — clear blocks and fluids from columns
 *       marked Void by the user, in case any layer placed material there.</li>
 *   <li>{@link #sealAboveTerrainColumn} (static) — per-column helper called by
 *       both {@code sealFluidBodies} and prefab tests to restore fluid in the
 *       {@code [terrainHeight + 1, waterLevel]} range of one column.</li>
 * </ul>
 *
 * <p>Extracted from {@link HytaleWorldExporter} for clarity.
 */
class HytaleChunkPostProcessor {
    private static final Logger logger = LoggerFactory.getLogger(HytaleChunkPostProcessor.class);

    private final int blockOffsetX;
    private final int blockOffsetZ;

    HytaleChunkPostProcessor(int blockOffsetX, int blockOffsetZ) {
        this.blockOffsetX = blockOffsetX;
        this.blockOffsetZ = blockOffsetZ;
    }

    /**
     * Restore fluid in columns with a non-zero water level. Above terrain
     * height, replaces any block (including solid blocks placed by ground
     * cover) with fluid; below terrain, fills only empty blocks (caves,
     * chasms). Must run after all layer processing and before frost.
     */
    void sealFluidBodies(Dimension dimension, Map<Long, HytaleChunk> chunksByCoords) {
        int sealed = 0;

        for (HytaleChunk chunk : chunksByCoords.values()) {
            int chunkBlockX = chunk.getxPos() << 5;
            int chunkBlockZ = chunk.getzPos() << 5;

            for (int localX = 0; localX < HytaleChunk.CHUNK_SIZE; localX++) {
                for (int localZ = 0; localZ < HytaleChunk.CHUNK_SIZE; localZ++) {
                    int worldX = chunkBlockX + localX - blockOffsetX;
                    int worldZ = chunkBlockZ + localZ - blockOffsetZ;

                    int waterLevel = dimension.getWaterLevelAt(worldX, worldZ);
                    if (waterLevel == Integer.MIN_VALUE || waterLevel <= 0) {
                        continue;
                    }

                    int terrainHeight = dimension.getIntHeightAt(worldX, worldZ);
                    if (waterLevel <= terrainHeight) {
                        continue; // Terrain is at or above water level; no water column
                    }

                    String fluidId = resolveFluidId(dimension, worldX, worldZ);

                    // Below terrain: fill only empty blocks (underground voids)
                    for (int y = 1; y <= terrainHeight; y++) {
                        HytaleBlock block = chunk.getHytaleBlock(localX, y, localZ);
                        if (block != null && !block.isEmpty()) {
                            continue;
                        }
                        HytaleChunk.HytaleSection section = chunk.getSections()[y >> 5];
                        if (section.getFluidId(localX, y & 31, localZ) == 0) {
                            chunk.setHytaleBlock(localX, y, localZ, HytaleBlock.EMPTY);
                            section.setFluid(localX, y & 31, localZ, fluidId, 1);
                            sealed++;
                        }
                    }

                    sealed += sealAboveTerrainColumn(chunk, localX, localZ, terrainHeight, waterLevel, fluidId);
                }
            }
        }

        if (sealed > 0) {
            logger.info("Fluid seal pass: restored {} missing fluid blocks", sealed);
        }
    }

    /**
     * Restore fluid in the {@code [terrainHeight + 1, waterLevel]} range of a
     * single chunk column. Blocks placed by Bo2 custom-object layers are
     * preserved with a transient seal-protection marker; Hytale stores blocks
     * and fluids separately, so the block coexists with the surrounding water.
     * Unmarked blocks with no support value (e.g. ground-cover/terrain plants
     * that bled into the water column during chunk generation) are cleared and
     * replaced with fluid.
     *
     * @return Number of (x, y, z) cells modified.
     */
    public static int sealAboveTerrainColumn(HytaleChunk chunk, int localX, int localZ, int terrainHeight, int waterLevel, String fluidId) {
        int sealed = 0;
        for (int y = terrainHeight + 1; y <= waterLevel; y++) {
            if ((chunk.getSupportValue(localX, y, localZ) == HytaleChunk.SUPPORT_NONE)
                    && (! chunk.isSealProtected(localX, y, localZ))) {
                chunk.setHytaleBlock(localX, y, localZ, HytaleBlock.EMPTY);
            }
            chunk.getSections()[y >> 5].setFluid(localX, y & 31, localZ, fluidId, 1);
            sealed++;
        }
        return sealed;
    }

    /** Resolve the fluid type for a column from dimension layer data. */
    private String resolveFluidId(Dimension dimension, int worldX, int worldZ) {
        int fluidLayerValue = HytaleFluidLayer.normalizeFluidValue(
            dimension.getLayerValueAt(HytaleFluidLayer.INSTANCE, worldX, worldZ));
        if (fluidLayerValue > 0) {
            return HytaleFluidLayer.getFluidBlockId(fluidLayerValue);
        }
        if (dimension.getBitLayerValueAt(FloodWithLava.INSTANCE, worldX, worldZ)) {
            return HytaleBlockMapping.HY_LAVA;
        }
        return HytaleBlockMapping.HY_WATER;
    }

    /**
     * Convert grass to dirt when covered by a solid/opaque block, but preserve
     * grass when the block above is a plant, decoration, fluid, or other
     * non-solid block. Pre-applies Hytale's in-game grass-to-dirt conversion,
     * avoiding unnecessary runtime work. Must run after all block-placement
     * passes (terrain, layers, custom objects, frost) but before lighting.
     */
    void convertCoveredGrass(Map<Long, HytaleChunk> chunksByCoords) {
        int converted = 0;
        for (HytaleChunk chunk : chunksByCoords.values()) {
            for (int x = 0; x < HytaleChunk.CHUNK_SIZE; x++) {
                for (int z = 0; z < HytaleChunk.CHUNK_SIZE; z++) {
                    int height = chunk.getHeight(x, z);
                    for (int y = 0; y <= height; y++) {
                        HytaleBlock block = chunk.getHytaleBlock(x, y, z);
                        if (block != null && block.isGrass()) {
                            HytaleBlock above = chunk.getHytaleBlock(x, y + 1, z);
                            if (above != null && !above.isEmpty()
                                    && !HytaleBlockRegistry.preservesGrassBelow(above.id)) {
                                chunk.setHytaleBlock(x, y, z, HytaleBlock.DIRT);
                                converted++;
                            }
                        }
                    }
                }
            }
        }
        if (converted > 0) {
            logger.debug("Converted {} covered grass blocks to dirt", converted);
        }
    }

    /**
     * Clear blocks, fluids, and support data from columns marked Void, in case
     * any first/second-pass layer, custom object, or frost step placed
     * material there. Guarantees void areas are truly empty in the exported
     * chunk data.
     */
    void enforceVoidColumns(Dimension dimension, Map<Long, HytaleChunk> chunksByCoords) {
        int cleared = 0;
        for (Map.Entry<Long, HytaleChunk> entry : chunksByCoords.entrySet()) {
            HytaleChunk chunk = entry.getValue();
            int hyChunkX = chunk.getxPos();
            int hyChunkZ = chunk.getzPos();

            // Convert to world block coords and then to tile coords
            int blockX = (hyChunkX << 5) - blockOffsetX;
            int blockZ = (hyChunkZ << 5) - blockOffsetZ;
            int tileX = blockX >> 7;
            int tileZ = blockZ >> 7;

            Tile tile = dimension.getTile(tileX, tileZ);
            if (tile == null) {
                continue;
            }

            int maxHeight = chunk.getMaxHeight();
            for (int localX = 0; localX < HytaleChunk.CHUNK_SIZE; localX++) {
                for (int localZ = 0; localZ < HytaleChunk.CHUNK_SIZE; localZ++) {
                    int worldX = blockX + localX;
                    int worldZ = blockZ + localZ;
                    int tileLocalX = worldX & 0x7F;
                    int tileLocalZ = worldZ & 0x7F;

                    if (!tile.getBitLayerValue(org.pepsoft.worldpainter.layers.Void.INSTANCE, tileLocalX, tileLocalZ)) {
                        continue;
                    }

                    // This column is void — clear everything
                    chunk.setHeight(localX, localZ, 0);
                    for (int y = 0; y < maxHeight; y++) {
                        HytaleBlock existing = chunk.getHytaleBlock(localX, y, localZ);
                        if (existing != null && !existing.isEmpty()) {
                            chunk.setHytaleBlock(localX, y, localZ, HytaleBlock.EMPTY);
                            cleared++;
                        }
                        HytaleChunk.HytaleSection section = chunk.getSections()[y >> 5];
                        int localY = y & 31;
                        if (section.getFluidId(localX, localY, localZ) > 0) {
                            section.clearFluid(localX, localY, localZ);
                            cleared++;
                        }
                    }
                }
            }
        }
        if (cleared > 0) {
            logger.info("Void enforcement pass cleared {} blocks/fluids", cleared);
        }
    }
}
