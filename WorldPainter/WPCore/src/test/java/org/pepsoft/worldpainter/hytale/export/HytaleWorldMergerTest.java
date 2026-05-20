package org.pepsoft.worldpainter.hytale.export;

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
import org.pepsoft.worldpainter.hytale.HytaleTerrain;
import org.pepsoft.worldpainter.hytale.HytaleTerrainLayer;
import org.pepsoft.worldpainter.hytale.chunk.HytaleChunk;
import org.pepsoft.worldpainter.hytale.chunk.HytaleChunkStore;
import org.pepsoft.worldpainter.merging.InvalidMapException;

import java.io.File;

import static org.junit.Assert.*;
import static org.pepsoft.worldpainter.Constants.DIM_NORMAL;
import static org.pepsoft.worldpainter.DefaultPlugin.HYTALE;

/**
 * Phase 1 smoke tests for {@link HytaleWorldMerger}. Verifies:
 * <ul>
 *   <li>Settings getter/setter pairs round-trip.</li>
 *   <li>Sanity checks reject worlds that weren't imported from a Hytale map.</li>
 *   <li>A full merge against a previously-exported Hytale world renames the original to the
 *       backup, regenerates chunks, and leaves the chunks dir readable through
 *       {@link HytaleChunkStore}.</li>
 * </ul>
 * Block-level merge options (above/under ground, surface depth, biome preserve) are exposed
 * as getter/setter pairs but not yet wired into chunk generation — Phase 2 will cover those.
 */
public class HytaleWorldMergerTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Test
    public void settingsGettersAndSettersRoundTrip() throws Exception {
        File mapDir = createExportedHytaleMap("settings_test");
        World2 world = buildImportedWorld(mapDir);

        HytaleWorldMerger merger = new HytaleWorldMerger(world, new WorldExportSettings(), mapDir, HYTALE);

        assertEquals(mapDir, merger.getMapDir());

        // Defaults — match the field defaults declared in HytaleWorldMerger
        assertFalse(merger.isReplaceChunks());
        assertTrue(merger.isMergeBlocksAboveGround());
        assertTrue(merger.isMergeBlocksUnderground());
        assertTrue(merger.isMergeBiomes());
        assertEquals(1, merger.getSurfaceMergeDepth());
        assertFalse(merger.isClearTrees());
        assertFalse(merger.isClearVegetation());
        assertFalse(merger.isClearManMadeAboveGround());
        assertFalse(merger.isClearManMadeBelowGround());

        // Round-trip every setter
        merger.setReplaceChunks(true);
        merger.setMergeBlocksAboveGround(false);
        merger.setMergeBlocksUnderground(false);
        merger.setMergeBiomes(false);
        merger.setSurfaceMergeDepth(7);
        merger.setClearTrees(true);
        merger.setClearVegetation(true);
        merger.setClearManMadeAboveGround(true);
        merger.setClearManMadeBelowGround(true);

        assertTrue(merger.isReplaceChunks());
        assertFalse(merger.isMergeBlocksAboveGround());
        assertFalse(merger.isMergeBlocksUnderground());
        assertFalse(merger.isMergeBiomes());
        assertEquals(7, merger.getSurfaceMergeDepth());
        assertTrue(merger.isClearTrees());
        assertTrue(merger.isClearVegetation());
        assertTrue(merger.isClearManMadeAboveGround());
        assertTrue(merger.isClearManMadeBelowGround());
    }

    @Test
    public void sanityChecksRejectWorldNotImportedFromMap() throws Exception {
        File mapDir = createExportedHytaleMap("not_imported");

        // Build a world WITHOUT setting setImportedFrom — sanity check should fail
        World2 world = new World2(HYTALE, 0, 320);
        world.setName("NotImported");
        long seed = 1L;
        TileFactory tileFactory = TileFactoryFactory.createFlatTileFactory(
            seed, Terrain.GRASS, 0, 320, 64, 62, false, false);
        Dimension.Anchor anchor = new Dimension.Anchor(DIM_NORMAL, Dimension.Role.DETAIL, false, 0);
        Dimension dim = new Dimension(world, "Surface", seed, tileFactory, anchor);
        dim.setEventsInhibited(true);
        dim.addTile(tileFactory.createTile(0, 0));
        dim.setEventsInhibited(false);
        world.addDimension(dim);

        HytaleWorldMerger merger = new HytaleWorldMerger(world, new WorldExportSettings(), mapDir, HYTALE);
        try {
            merger.performSanityChecks();
            fail("Expected InvalidMapException for world without importedFrom");
        } catch (InvalidMapException expected) {
            // ok
        }
    }

    @Test
    public void mergeBacksUpOriginalAndRegeneratesChunksWithTilesPainted() throws Exception {
        // 1. Build & export an initial Hytale world (acts as the "existing map")
        File mapDir = createExportedHytaleMap("merge_smoke");
        File mapInnerDir = new File(new File(new File(mapDir, "universe"), "worlds"), "default");
        File originalChunksDir = new File(mapInnerDir, "chunks");
        assertTrue("Setup precondition: original chunks dir exists", originalChunksDir.isDirectory());
        int originalRegionFileCount =
                originalChunksDir.listFiles((d, n) -> n.endsWith(".region.bin")).length;
        assertTrue("Setup precondition: at least one original region file", originalRegionFileCount > 0);

        // 2. Build a world that pretends it was imported from that map. We don't go through
        //    the full HytaleMapImporter pipeline here to keep the test fast and focused on
        //    the merger; we just create a small Dimension and set importedFrom to the map's
        //    inner-world config.json (the same path HytaleMapImporter sets).
        World2 world = buildImportedWorld(mapDir);

        // 3. Merge
        File backupDir = new File(tempDir.getRoot(), "merge_smoke_backup");
        HytaleWorldMerger merger = new HytaleWorldMerger(world, new WorldExportSettings(), mapDir, HYTALE);
        merger.merge(backupDir, null);

        // 4. Verify backup was created via rename
        assertTrue("Backup dir should exist after merge", backupDir.isDirectory());
        File backupInner = new File(new File(new File(backupDir, "universe"), "worlds"), "default");
        File backupChunks = new File(backupInner, "chunks");
        assertTrue("Backup should contain the original chunks dir", backupChunks.isDirectory());
        assertEquals("Backup should contain the same number of region files as the original",
                originalRegionFileCount,
                backupChunks.listFiles((d, n) -> n.endsWith(".region.bin")).length);

        // 5. Verify mapDir was repopulated with a fresh save by the exporter
        assertTrue("mapDir should be recreated after merge", mapDir.isDirectory());
        File newInner = new File(new File(new File(mapDir, "universe"), "worlds"), "default");
        File newChunks = new File(newInner, "chunks");
        assertTrue("Fresh chunks dir should exist after merge", newChunks.isDirectory());
        File[] newRegionFiles = newChunks.listFiles((d, n) -> n.endsWith(".region.bin"));
        assertNotNull(newRegionFiles);
        assertTrue("Fresh chunks dir should contain at least one region file", newRegionFiles.length > 0);

        // 6. Verify the fresh chunks are readable and contain TalePainter terrain
        try (HytaleChunkStore store = new HytaleChunkStore(newInner, 0, 320)) {
            assertTrue("Fresh save should contain at least one chunk", store.getChunkCount() > 0);

            // The painted tile is at (0,0); after centering, chunk (0,0) is at the
            // post-offset origin and should contain the painted block stack.
            HytaleChunk chunk = (HytaleChunk) store.getChunk(0, 0);
            assertNotNull("Chunk (0,0) should exist after merge", chunk);
            assertEquals("Painted column should keep TalePainter's terrain block",
                    HytaleTerrain.STONE.getPrimaryBlock().id,
                    chunk.getHytaleBlock(0, 64, 0).id);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────

    /**
     * Build a small TalePainter world and export it as a Hytale save. Returns the save dir
     * (the top-level directory containing {@code universe/}, {@code config.json}, etc.) — this
     * is the same shape that {@link HytaleWorldMerger}'s {@code mapDir} parameter expects.
     */
    private File createExportedHytaleMap(String name) throws Exception {
        World2 world = new World2(HYTALE, 0, 320);
        world.setName(name);
        world.setCreateGoodiesChest(false);

        long seed = 42L;
        TileFactory tileFactory = TileFactoryFactory.createFlatTileFactory(
            seed, Terrain.GRASS, 0, 320, 64, 62, false, false);
        Dimension.Anchor anchor = new Dimension.Anchor(DIM_NORMAL, Dimension.Role.DETAIL, false, 0);
        Dimension dim = new Dimension(world, "Surface", seed, tileFactory, anchor);
        dim.setEventsInhibited(true);

        Tile tile = tileFactory.createTile(0, 0);
        for (int x = 0; x < 128; x++) {
            for (int z = 0; z < 128; z++) {
                tile.setHeight(x, z, 64);
                tile.setTerrain(x, z, Terrain.STONE);
                HytaleTerrainLayer.setTerrainIndex(tile, x, z, HytaleTerrain.STONE.getLayerIndex());
            }
        }
        dim.addTile(tile);
        dim.setEventsInhibited(false);
        world.addDimension(dim);

        File baseDir = tempDir.newFolder("base_" + name);
        new HytaleWorldExporter(world, new WorldExportSettings())
            .export(baseDir, name, null, null);

        File mapDir = new File(baseDir, name);
        assertTrue("Setup precondition: exported save exists", mapDir.isDirectory());
        return mapDir;
    }

    /**
     * Build a tiny TalePainter world that mimics a freshly-imported Hytale world: same
     * dimensions/height as the exported save, importedFrom set to {@code mapDir/inner/config.json}.
     */
    private World2 buildImportedWorld(File mapDir) {
        World2 world = new World2(HYTALE, 0, 320);
        world.setName("merger-test");
        world.setCreateGoodiesChest(false);
        File innerWorldDir = new File(new File(new File(mapDir, "universe"), "worlds"), "default");
        world.setImportedFrom(new File(innerWorldDir, "config.json"));

        long seed = 99L;
        TileFactory tileFactory = TileFactoryFactory.createFlatTileFactory(
            seed, Terrain.STONE, 0, 320, 64, 62, false, false);
        Dimension.Anchor anchor = new Dimension.Anchor(DIM_NORMAL, Dimension.Role.DETAIL, false, 0);
        Dimension dim = new Dimension(world, "Surface", seed, tileFactory, anchor);
        dim.setEventsInhibited(true);
        Tile tile = tileFactory.createTile(0, 0);
        for (int x = 0; x < 128; x++) {
            for (int z = 0; z < 128; z++) {
                tile.setHeight(x, z, 64);
                tile.setTerrain(x, z, Terrain.STONE);
                HytaleTerrainLayer.setTerrainIndex(tile, x, z, HytaleTerrain.STONE.getLayerIndex());
            }
        }
        dim.addTile(tile);
        dim.setEventsInhibited(false);
        world.addDimension(dim);
        return world;
    }
}
