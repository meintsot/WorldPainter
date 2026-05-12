package org.pepsoft.worldpainter.hytale;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.TileFactory;
import org.pepsoft.worldpainter.TileFactoryFactory;
import org.pepsoft.worldpainter.World2;
import org.pepsoft.worldpainter.exporting.WorldExportSettings;

import java.io.File;
import java.util.Collections;
import java.util.TreeSet;

import static org.junit.Assert.*;
import static org.pepsoft.worldpainter.Constants.DIM_NORMAL;
import static org.pepsoft.worldpainter.DefaultPlugin.HYTALE;

/**
 * End-to-end integration tests for TP-56: the auto-vegetation layer hooks into
 * {@link HytaleWorldExporter#populateChunkFromTile} and places biome-driven
 * plant blocks at {@code height + 1}.
 *
 * <p><b>Test 1</b> ({@link #paintedAutoVegPlacesConfiguredPlant}): painted
 * auto-veg pixel with a 100%-coverage config on a flooded column must produce
 * the configured plant block at {@code height + 1} and survive the
 * post-export seal pass (proves the block is seal-protected, following the
 * same pattern used in {@code Tp53UnderwaterPlantTest}).
 *
 * <p><b>Test 2</b> ({@link #reExportingIsByteIdentical}): two exports with
 * the same fixed settings seed must place exactly the same plant blocks at
 * the same positions.
 *
 * <p><b>Test 3</b> ({@link #lazyDefaultsAreSeededOnFirstExport}): when no
 * settings are attached before export, the exporter lazily seeds the curated
 * defaults and stores them back on the dimension, with the shipped JSON values.
 */
public class Tp56AutoVegetationExportTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    private static final int TERRAIN_HEIGHT = 50;
    // Water level above terrain to create flooded columns (mirrors Tp53UnderwaterPlantTest).
    private static final int WATER_LEVEL = 64;
    // Region of pixels painted with the auto-veg layer.
    private static final int REGION_SIZE = 16;

    // =========================================================================
    // Test 1 — flooded column: plant placed + survives seal pass
    // =========================================================================

    /**
     * A painted auto-veg pixel with 100 % coverage on a flooded column must
     * have the configured plant at {@code height + 1} after export. Survival
     * through the post-export seal pass proves the block is seal-protected.
     */
    @Test
    public void paintedAutoVegPlacesConfiguredPlant() throws Exception {
        HytaleTerrain lushTallGrass = HytaleTerrain.getByBlockId("Plant_Grass_Lush_Tall");
        assertNotNull("HytaleTerrain for Plant_Grass_Lush_Tall must be registered", lushTallGrass);

        HytaleAutoVegetationSettings settings = buildSinglePlantSettings(
                HytaleBiome.DRIFTING_PLAINS.getId(), 100, lushTallGrass);

        World2 world = buildWorld(settings, /* flooded */ true, /* paintBiome */ true);
        File exportDir = tempDir.newFolder("tp56_test1");
        new HytaleWorldExporter(world, new WorldExportSettings())
                .export(exportDir, "Tp56Test1", null, null);

        int count = countPlantBlocksAtY(exportDir, "Tp56Test1",
                TERRAIN_HEIGHT + 1, "Plant_Grass_Lush_Tall");

        // Coverage is 100 % → every painted pixel must have a plant.
        assertTrue("Expected >= 1 Plant_Grass_Lush_Tall at height+1 after export (got " + count + ")",
                count >= 1);
        // A flooded column at height+1 < waterLevel: if the block were not
        // seal-protected the seal pass would overwrite it with water/air.
        // Its presence here proves seal protection, mirroring the Tp53 pattern.
        assertEquals("Every painted pixel (coverage=100) must have a plant — "
                + "expected " + REGION_SIZE * REGION_SIZE + ", got " + count,
                REGION_SIZE * REGION_SIZE, count);
    }

    // =========================================================================
    // Test 2 — two exports with identical seeds produce identical blocks
    // =========================================================================

    @Test
    public void reExportingIsByteIdentical() throws Exception {
        HytaleTerrain lushTallGrass = HytaleTerrain.getByBlockId("Plant_Grass_Lush_Tall");
        assertNotNull(lushTallGrass);

        // First export
        HytaleAutoVegetationSettings s1 = buildSinglePlantSettings(
                HytaleBiome.DRIFTING_PLAINS.getId(), 100, lushTallGrass);
        s1.setSeed(12345L);
        World2 world1 = buildWorld(s1, /* flooded */ false, /* paintBiome */ true);
        File dir1 = tempDir.newFolder("tp56_test2_run1");
        new HytaleWorldExporter(world1, new WorldExportSettings())
                .export(dir1, "Tp56Test2", null, null);

        // Second export — rebuild from scratch to avoid any shared state.
        HytaleAutoVegetationSettings s2 = buildSinglePlantSettings(
                HytaleBiome.DRIFTING_PLAINS.getId(), 100, lushTallGrass);
        s2.setSeed(12345L);
        World2 world2 = buildWorld(s2, /* flooded */ false, /* paintBiome */ true);
        File dir2 = tempDir.newFolder("tp56_test2_run2");
        new HytaleWorldExporter(world2, new WorldExportSettings())
                .export(dir2, "Tp56Test2", null, null);

        BlockSnapshot snap1 = collectBlocksAtY(dir1, "Tp56Test2", TERRAIN_HEIGHT + 1);
        BlockSnapshot snap2 = collectBlocksAtY(dir2, "Tp56Test2", TERRAIN_HEIGHT + 1);

        assertEquals("Re-export must produce the same number of non-empty blocks at height+1",
                snap1.nonEmpty, snap2.nonEmpty);
        assertEquals("Re-export must place blocks at exactly the same positions with the same IDs",
                snap1.plantBlockIds, snap2.plantBlockIds);
    }

    // =========================================================================
    // Test 3 — lazy defaults seeded when no settings are present
    // =========================================================================

    @Test
    public void lazyDefaultsAreSeededOnFirstExport() throws Exception {
        // No settings attached — the exporter must lazily seed them.
        World2 world = buildWorld(null, /* flooded */ false, /* paintBiome */ false);
        Dimension dim = world.getDimension(
                new Dimension.Anchor(DIM_NORMAL, Dimension.Role.DETAIL, false, 0));
        assertNotNull(dim);
        assertNull("Dimension must have no auto-veg settings before export",
                dim.getLayerSettings(HytaleAutoVegetationLayer.INSTANCE));

        File exportDir = tempDir.newFolder("tp56_test3");
        new HytaleWorldExporter(world, new WorldExportSettings())
                .export(exportDir, "Tp56Test3", null, null);

        HytaleAutoVegetationSettings seeded = (HytaleAutoVegetationSettings)
                dim.getLayerSettings(HytaleAutoVegetationLayer.INSTANCE);
        assertNotNull("Settings must be lazily seeded onto the dimension during export", seeded);
        assertFalse("Lazily seeded settings must contain at least one biome config",
                seeded.getByBiome().isEmpty());

        // Spot-check: Zone1_Drifting_Plains must have coverage == 12 (from shipped JSON).
        HytaleAutoVegetationSettings.BiomeVegetationConfig dpCfg =
                seeded.getByBiome().get(HytaleBiome.DRIFTING_PLAINS.getId());
        assertNotNull("Shipped defaults must include Zone1_Drifting_Plains", dpCfg);
        assertEquals("Zone1_Drifting_Plains shipped coverage must be 12",
                12, dpCfg.getCoveragePercent());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Build a small world with a single tile at (0,0), flat at
     * {@link #TERRAIN_HEIGHT}, with a {@link #REGION_SIZE} x {@link #REGION_SIZE}
     * region painted with {@link HytaleAutoVegetationLayer}. Stone is used as
     * the substrate so {@code isValidSubstrateFor} always passes.
     *
     * @param settings   auto-veg settings to attach, or {@code null} for none.
     * @param flooded    if true, also set water level to {@link #WATER_LEVEL} on
     *                   every pixel (creates flooded columns — proves seal protection).
     * @param paintBiome if true, paint every pixel with DRIFTING_PLAINS biome so
     *                   the exporter does not auto-derive it.
     */
    private World2 buildWorld(HytaleAutoVegetationSettings settings,
                              boolean flooded, boolean paintBiome) {
        World2 world = new World2(HYTALE, 0, 320);
        world.setName("Tp56World");
        world.setCreateGoodiesChest(false);

        long seed = 42L;
        int waterLevel = flooded ? WATER_LEVEL : 0;
        TileFactory tileFactory = TileFactoryFactory.createFlatTileFactory(
                seed, Terrain.GRASS, 0, 320, TERRAIN_HEIGHT, waterLevel, false, false);
        Dimension.Anchor anchor = new Dimension.Anchor(DIM_NORMAL, Dimension.Role.DETAIL, false, 0);
        Dimension dim = new Dimension(world, "Surface", seed, tileFactory, anchor);
        dim.setEventsInhibited(true);

        Tile tile = tileFactory.createTile(0, 0);
        for (int x = 0; x < 128; x++) {
            for (int z = 0; z < 128; z++) {
                tile.setHeight(x, z, TERRAIN_HEIGHT);
                if (flooded) {
                    tile.setWaterLevel(x, z, WATER_LEVEL);
                }
                tile.setTerrain(x, z, Terrain.GRASS);
                // Stone substrate: solid, not in REJECTED_SUBSTRATE_IDS.
                HytaleTerrainLayer.setTerrainIndex(tile, x, z, HytaleTerrain.STONE.getLayerIndex());
            }
        }
        // Paint auto-veg layer (and optionally the biome) on the REGION_SIZE region.
        for (int x = 0; x < REGION_SIZE; x++) {
            for (int z = 0; z < REGION_SIZE; z++) {
                tile.setBitLayerValue(HytaleAutoVegetationLayer.INSTANCE, x, z, true);
                if (paintBiome) {
                    tile.setLayerValue(org.pepsoft.worldpainter.layers.Biome.INSTANCE, x, z,
                            HytaleBiome.DRIFTING_PLAINS.getId());
                }
            }
        }
        dim.addTile(tile);

        if (settings != null) {
            dim.setLayerSettings(HytaleAutoVegetationLayer.INSTANCE, settings);
        }

        dim.setEventsInhibited(false);
        world.addDimension(dim);
        return world;
    }

    /** Build settings with a single biome config and a single plant entry. */
    private HytaleAutoVegetationSettings buildSinglePlantSettings(
            int biomeId, int coveragePercent, HytaleTerrain terrain) {
        HytaleAutoVegetationSettings s = new HytaleAutoVegetationSettings();
        HytaleAutoVegetationSettings.PlantEntry entry =
                new HytaleAutoVegetationSettings.PlantEntry(terrain.getId(), 100);
        HytaleAutoVegetationSettings.BiomeVegetationConfig cfg =
                new HytaleAutoVegetationSettings.BiomeVegetationConfig(
                        coveragePercent, Collections.singletonList(entry));
        s.setBiomeConfig(biomeId, cfg);
        return s;
    }

    // ---- region-file walkers ------------------------------------------------

    private static final class BlockSnapshot {
        int nonEmpty;
        String plantBlockIds;
    }

    private File chunksDir(File exportBase, String worldName) {
        return new File(new File(new File(new File(exportBase, worldName), "universe"),
                "worlds"), "default/chunks");
    }

    /**
     * Count how many blocks with {@code blockId} exist at world Y == {@code worldY}
     * across every chunk in the exported world.
     */
    private int countPlantBlocksAtY(
            File exportBase, String worldName, int worldY, String blockId) throws Exception {
        int count = 0;
        File chunksDir = chunksDir(exportBase, worldName);
        File[] regionFiles = chunksDir.listFiles((d, n) -> n.endsWith(".region.bin"));
        assertNotNull("Export must produce a chunks directory", regionFiles);
        assertTrue("Export must produce at least one region file", regionFiles.length > 0);

        for (File rfile : regionFiles) {
            HytaleRegionFile rf = new HytaleRegionFile(rfile.toPath());
            try {
                rf.open();
                for (int cx = 0; cx < HytaleChunk.CHUNK_SIZE; cx++) {
                    for (int cz = 0; cz < HytaleChunk.CHUNK_SIZE; cz++) {
                        if (!rf.hasChunk(cx, cz)) {
                            continue;
                        }
                        HytaleChunk chunk = rf.readChunk(cx, cz, 0, HytaleChunk.DEFAULT_MAX_HEIGHT);
                        if (chunk == null) {
                            continue;
                        }
                        for (int x = 0; x < HytaleChunk.CHUNK_SIZE; x++) {
                            for (int z = 0; z < HytaleChunk.CHUNK_SIZE; z++) {
                                HytaleBlock b = chunk.getHytaleBlock(x, worldY, z);
                                if (b != null && !b.isEmpty() && blockId.equals(b.id)) {
                                    count++;
                                }
                            }
                        }
                    }
                }
            } finally {
                rf.close();
            }
        }
        return count;
    }

    /**
     * Collect a snapshot of non-empty blocks at world Y == {@code worldY}
     * for determinism comparison between two exports.
     */
    private BlockSnapshot collectBlocksAtY(
            File exportBase, String worldName, int worldY) throws Exception {
        BlockSnapshot snap = new BlockSnapshot();
        File chunksDir = chunksDir(exportBase, worldName);
        File[] regionFiles = chunksDir.listFiles((d, n) -> n.endsWith(".region.bin"));
        assertNotNull("Export must produce a chunks directory", regionFiles);
        assertTrue("Export must produce at least one region file", regionFiles.length > 0);

        TreeSet<String> ids = new TreeSet<>();
        for (File rfile : regionFiles) {
            HytaleRegionFile rf = new HytaleRegionFile(rfile.toPath());
            try {
                rf.open();
                for (int cx = 0; cx < HytaleChunk.CHUNK_SIZE; cx++) {
                    for (int cz = 0; cz < HytaleChunk.CHUNK_SIZE; cz++) {
                        if (!rf.hasChunk(cx, cz)) {
                            continue;
                        }
                        HytaleChunk chunk = rf.readChunk(cx, cz, 0, HytaleChunk.DEFAULT_MAX_HEIGHT);
                        if (chunk == null) {
                            continue;
                        }
                        for (int x = 0; x < HytaleChunk.CHUNK_SIZE; x++) {
                            for (int z = 0; z < HytaleChunk.CHUNK_SIZE; z++) {
                                HytaleBlock b = chunk.getHytaleBlock(x, worldY, z);
                                if (b != null && !b.isEmpty()) {
                                    snap.nonEmpty++;
                                    ids.add(x + "," + worldY + "," + z + "=" + b.id);
                                }
                            }
                        }
                    }
                }
            } finally {
                rf.close();
            }
        }
        snap.plantBlockIds = String.join("|", ids);
        return snap;
    }
}
