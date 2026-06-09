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
import org.pepsoft.worldpainter.hytale.HytaleBlock;
import org.pepsoft.worldpainter.hytale.HytaleTerrain;
import org.pepsoft.worldpainter.hytale.HytaleTerrainLayer;
import org.pepsoft.worldpainter.hytale.chunk.HytaleChunk;
import org.pepsoft.worldpainter.hytale.chunk.HytaleChunkStore;
import org.pepsoft.worldpainter.merging.InvalidMapException;

import java.awt.Point;
import java.io.File;

import static org.junit.Assert.*;
import static org.pepsoft.worldpainter.Constants.DIM_NORMAL;
import static org.pepsoft.worldpainter.DefaultPlugin.HYTALE;
import static org.pepsoft.worldpainter.Dimension.Anchor.NORMAL_DETAIL;

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
    public void sanityChecksAcceptWorldNotImportedFromMap() throws Exception {
        // Matches JavaWorldMerger: merging a non-imported TalePainter world into an existing
        // Hytale save is allowed. App.merge() already surfaces a confirmation dialog ("world
        // was not imported, are you sure?") before opening MergeWorldDialog, so the merger
        // itself does not need to enforce this.
        File mapDir = createExportedHytaleMap("not_imported");

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
        // Should not throw — world without importedFrom is allowed.
        merger.performSanityChecks();
    }

    @Test
    public void mergeBacksUpOriginalAndRegeneratesChunksWithTilesPainted() throws Exception {
        // 1. Build & export an initial Hytale world (acts as the "existing map"). mapDir is
        //    the inner world dir (.../universe/worlds/default) per the test helper contract,
        //    matching what HytalePlatformProvider.identifyMap() returns in production.
        File mapDir = createExportedHytaleMap("merge_smoke");
        File originalChunksDir = new File(mapDir, "chunks");
        assertTrue("Setup precondition: original chunks dir exists", originalChunksDir.isDirectory());
        int originalRegionFileCount =
                originalChunksDir.listFiles((d, n) -> n.endsWith(".region.bin")).length;
        assertTrue("Setup precondition: at least one original region file", originalRegionFileCount > 0);

        // 2. Build a world that pretends it was imported from that map. We don't go through
        //    the full HytaleMapImporter pipeline here to keep the test fast and focused on
        //    the merger; we just create a small Dimension and set importedFrom to the map's
        //    config.json (the same path HytaleMapImporter sets).
        World2 world = buildImportedWorld(mapDir);

        // 3. Merge. The merger backs up the save root (three levels up from mapDir) and
        //    rewrites the save at that level, so backupDir lives next to the save root.
        File backupDir = new File(tempDir.getRoot(), "merge_smoke_backup");
        HytaleWorldMerger merger = new HytaleWorldMerger(world, new WorldExportSettings(), mapDir, HYTALE);
        merger.merge(backupDir, null);

        // 4. Verify backup was created via rename at the save-root level
        assertTrue("Backup dir should exist after merge", backupDir.isDirectory());
        File backupInner = new File(new File(new File(backupDir, "universe"), "worlds"), "default");
        File backupChunks = new File(backupInner, "chunks");
        assertTrue("Backup should contain the original chunks dir", backupChunks.isDirectory());
        assertEquals("Backup should contain the same number of region files as the original",
                originalRegionFileCount,
                backupChunks.listFiles((d, n) -> n.endsWith(".region.bin")).length);

        // 5. Verify the inner world dir was repopulated with a fresh save by the exporter
        assertTrue("mapDir should be recreated after merge", mapDir.isDirectory());
        File newChunks = new File(mapDir, "chunks");
        assertTrue("Fresh chunks dir should exist after merge", newChunks.isDirectory());
        File[] newRegionFiles = newChunks.listFiles((d, n) -> n.endsWith(".region.bin"));
        assertNotNull(newRegionFiles);
        assertTrue("Fresh chunks dir should contain at least one region file", newRegionFiles.length > 0);

        // 6. Verify the fresh chunks are readable and contain TalePainter terrain
        try (HytaleChunkStore store = new HytaleChunkStore(mapDir, 0, 320)) {
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

    // ── Phase 2 (per-block merge decisions) ──────────────────────────────────────────

    @Test
    public void mergeBlocksUndergroundFalseReplacesOriginalSubstrate() throws Exception {
        File mapDir = createExportedHytaleMap("merge_under_false");
        // Inject a uniquely identifiable block deep underground in the to-be-backup chunk.
        injectIntoOriginal(mapDir, 0, 0, chunk -> {
            chunk.setHytaleBlock(0, 20, 0, HytaleBlock.of("Cloth_Wool")); // man-made → distinct
        });

        HytaleWorldMerger merger = newMerger(mapDir);
        merger.setMergeBlocksUnderground(false);
        merger.setSurfaceMergeDepth(1);
        merger.merge(new File(tempDir.getRoot(), "merge_under_false_bkp"), null);

        try (HytaleChunkStore store = new HytaleChunkStore(innerWorld(mapDir), 0, 320)) {
            HytaleChunk freshChunk = (HytaleChunk) store.getChunk(0, 0);
            assertNotNull("Fresh chunk (0,0) must exist", freshChunk);
            assertNotEquals("With mergeBlocksUnderground=false, original Cloth_Wool at y=20 must NOT survive",
                    "Cloth_Wool", idOrEmpty(freshChunk.getHytaleBlock(0, 20, 0)));
            // TalePainter's stone substrate should be present instead
            assertEquals("Surface block at terrainHeight must remain TalePainter's stone",
                    HytaleTerrain.STONE.getPrimaryBlock().id, freshChunk.getHytaleBlock(0, 64, 0).id);
        }
    }

    @Test
    public void mergeBlocksUndergroundTrueKeepsOriginalSubstrate() throws Exception {
        File mapDir = createExportedHytaleMap("merge_under_true");
        injectIntoOriginal(mapDir, 0, 0, chunk -> {
            chunk.setHytaleBlock(0, 20, 0, HytaleBlock.of("Cloth_Wool"));
        });

        HytaleWorldMerger merger = newMerger(mapDir);
        merger.setMergeBlocksUnderground(true);
        merger.setMergeBlocksAboveGround(false); // isolate the underground decision
        merger.setSurfaceMergeDepth(1);
        merger.merge(new File(tempDir.getRoot(), "merge_under_true_bkp"), null);

        try (HytaleChunkStore store = new HytaleChunkStore(innerWorld(mapDir), 0, 320)) {
            HytaleChunk freshChunk = (HytaleChunk) store.getChunk(0, 0);
            assertNotNull("Fresh chunk (0,0) must exist", freshChunk);
            assertEquals("With mergeBlocksUnderground=true, original Cloth_Wool at y=20 must survive",
                    "Cloth_Wool", idOrEmpty(freshChunk.getHytaleBlock(0, 20, 0)));
            assertEquals("Surface band still owned by TalePainter",
                    HytaleTerrain.STONE.getPrimaryBlock().id, freshChunk.getHytaleBlock(0, 64, 0).id);
        }
    }

    @Test
    public void surfaceMergeDepthControlsBlendDepth() throws Exception {
        File mapDir = createExportedHytaleMap("merge_depth");
        // Put a beacon block at y=63 (just under terrainHeight=64) and another at y=59
        // (deeper than the surface band at depth=4). With surfaceMergeDepth=4:
        //   surfaceBottom = 64 - 4 + 1 = 61
        //   y in [61..64]  = surface band (TalePainter wins, original is NOT copied)
        //   y < 61         = underground band (mergeBlocksUnderground=true → original wins)
        injectIntoOriginal(mapDir, 0, 0, chunk -> {
            chunk.setHytaleBlock(0, 63, 0, HytaleBlock.of("Cloth_Wool")); // in surface band
            chunk.setHytaleBlock(0, 59, 0, HytaleBlock.of("Cloth_Wool")); // below surface band
        });

        HytaleWorldMerger merger = newMerger(mapDir);
        merger.setMergeBlocksAboveGround(false);
        merger.setMergeBlocksUnderground(true);
        merger.setSurfaceMergeDepth(4);
        merger.merge(new File(tempDir.getRoot(), "merge_depth_bkp"), null);

        try (HytaleChunkStore store = new HytaleChunkStore(innerWorld(mapDir), 0, 320)) {
            HytaleChunk freshChunk = (HytaleChunk) store.getChunk(0, 0);
            assertNotNull(freshChunk);
            assertNotEquals("Original block at y=63 sits inside the surface band → REPLACED by TalePainter",
                    "Cloth_Wool", idOrEmpty(freshChunk.getHytaleBlock(0, 63, 0)));
            assertEquals("Original block at y=59 sits below the surface band → PRESERVED",
                    "Cloth_Wool", idOrEmpty(freshChunk.getHytaleBlock(0, 59, 0)));
        }
    }

    @Test
    public void mergeBlocksAboveGroundTrueKeepsOriginalSkyBlocks() throws Exception {
        File mapDir = createExportedHytaleMap("merge_above_true");
        // terrainHeight=64; place a block at y=70 (above-ground) in the original.
        injectIntoOriginal(mapDir, 0, 0, chunk -> {
            chunk.setHytaleBlock(0, 70, 0, HytaleBlock.of("Cloth_Wool"));
        });

        HytaleWorldMerger merger = newMerger(mapDir);
        merger.setMergeBlocksAboveGround(true);
        // No clear-man-made-above-ground (false by default) so Cloth_Wool survives.
        merger.merge(new File(tempDir.getRoot(), "merge_above_true_bkp"), null);

        try (HytaleChunkStore store = new HytaleChunkStore(innerWorld(mapDir), 0, 320)) {
            HytaleChunk freshChunk = (HytaleChunk) store.getChunk(0, 0);
            assertNotNull(freshChunk);
            assertEquals("With mergeBlocksAboveGround=true, original above-ground block must survive",
                    "Cloth_Wool", idOrEmpty(freshChunk.getHytaleBlock(0, 70, 0)));
        }
    }

    @Test
    public void replaceChunksTrueIgnoresAllMergeOptions() throws Exception {
        File mapDir = createExportedHytaleMap("replace_chunks");
        // Inject blocks in all bands. With replaceChunks=true, none should appear.
        injectIntoOriginal(mapDir, 0, 0, chunk -> {
            chunk.setHytaleBlock(0, 20, 0, HytaleBlock.of("Cloth_Wool")); // underground
            chunk.setHytaleBlock(0, 70, 0, HytaleBlock.of("Cloth_Wool")); // above-ground
            chunk.setBiomeName(0, 0, "TundraSnowy");
        });

        HytaleWorldMerger merger = newMerger(mapDir);
        merger.setReplaceChunks(true);
        // All flags ON would normally apply — replaceChunks must short-circuit them.
        merger.setMergeBlocksAboveGround(true);
        merger.setMergeBlocksUnderground(true);
        merger.setMergeBiomes(true);
        merger.merge(new File(tempDir.getRoot(), "replace_chunks_bkp"), null);

        try (HytaleChunkStore store = new HytaleChunkStore(innerWorld(mapDir), 0, 320)) {
            HytaleChunk freshChunk = (HytaleChunk) store.getChunk(0, 0);
            assertNotNull(freshChunk);
            assertNotEquals("replaceChunks=true must drop original underground block",
                    "Cloth_Wool", idOrEmpty(freshChunk.getHytaleBlock(0, 20, 0)));
            assertNotEquals("replaceChunks=true must drop original above-ground block",
                    "Cloth_Wool", idOrEmpty(freshChunk.getHytaleBlock(0, 70, 0)));
            assertNotEquals("replaceChunks=true must drop original biome",
                    "TundraSnowy", freshChunk.getBiomeName(0, 0));
        }
    }

    /**
     * The Hytale BSON format does not currently round-trip per-column biomes (only environments
     * are persisted; see HytaleBsonChunkSerializer/Deserializer). To exercise the biome-merge
     * decision without depending on that round-trip we drive the {@code applyMergeOverrides}
     * hook directly against an in-memory original-chunk store. Same-package access lets us
     * set the protected {@code originalChunkStore} field and invoke the protected method.
     */
    @Test
    public void mergeBiomesPreservesOriginalBiomeWhenSet() throws Exception {
        File mapDir = createExportedHytaleMap("merge_biomes");
        HytaleWorldMerger merger = newMerger(mapDir);
        merger.setMergeBiomes(true);

        // Build an in-memory "backup" chunk whose biome at (0,0) is TundraSnowy.
        HytaleChunk originalChunk = new HytaleChunk(0, 0, 0, 320);
        originalChunk.setBiomeName(0, 0, "TundraSnowy");

        // Wire a stub chunk store that returns the prepared chunk for (0,0).
        merger.originalChunkStore = new InMemoryHytaleChunkStore(innerWorld(mapDir), 0, 320, originalChunk);

        // Build a fresh "new" chunk (TalePainter-generated; biome stays default "Grassland").
        HytaleChunk newChunk = new HytaleChunk(0, 0, 0, 320);
        Tile tile = freshFlatTile(64);

        merger.applyMergeOverrides(newChunk, 0, 0, tile);

        assertEquals("Original biome must be preserved when TalePainter left the column at default",
                "TundraSnowy", newChunk.getBiomeName(0, 0));
        // And a neighbouring column where the original is still default must remain default.
        assertEquals("Untouched columns keep TalePainter's default biome",
                "Grassland", newChunk.getBiomeName(1, 0));
    }

    /**
     * Regression test for TP-59 Fix B: chunks that lived in the original Hytale save outside
     * TalePainter's tile coverage must be preserved through a merge. With Fix A also in
     * place (merger disables centering), the original chunk coordinates line up 1:1 with
     * the fresh save, so a marker block at (8,8) survives unmoved.
     */
    @Test
    public void mergePreservesOriginalChunksOutsideTalePainterTileBounds() throws Exception {
        File mapDir = createExportedHytaleMap("preserve_untouched");

        // The exported save has chunks for tile (0,0): chunks (0,0)..(3,3) inclusive.
        // Chunk (8,8) lies well outside that. Inject a marker into a chunk we'll create
        // there. We do it directly via HytaleChunkStore.saveChunk so the chunk exists
        // even though the original export didn't write one at (8,8).
        try (HytaleChunkStore store = new HytaleChunkStore(innerWorld(mapDir), 0, 320)) {
            HytaleChunk chunk88 = new HytaleChunk(8, 8, 0, 320);
            chunk88.setHytaleBlock(0, 50, 0, HytaleBlock.of("Rock_Basalt"));
            store.saveChunk(chunk88);
            store.flush();
        }

        // Build an imported world that only adds tile (0,0) — so chunk (8,8) is outside
        // TalePainter's tile coverage and must be carried over from the backup.
        World2 world = buildImportedWorld(mapDir);

        File backupDir = new File(tempDir.getRoot(), "preserve_untouched_backup");
        HytaleWorldMerger merger = new HytaleWorldMerger(world, new WorldExportSettings(), mapDir, HYTALE);
        merger.merge(backupDir, null);

        // The original chunk at (8,8) should survive the merge — read it back from the fresh
        // save and verify the marker block is still there.
        try (HytaleChunkStore freshStore = new HytaleChunkStore(innerWorld(mapDir), 0, 320)) {
            HytaleChunk chunk88 = (HytaleChunk) freshStore.getChunk(8, 8);
            assertNotNull("Original chunk at (8,8) should be preserved after merge", chunk88);
            assertEquals("Original marker block at (0,50,0) of chunk (8,8) should survive",
                    "Rock_Basalt", idOrEmpty(chunk88.getHytaleBlock(0, 50, 0)));
        }
    }

    // ── Partial-selection chunk preservation (the "merged world is split" bug) ────────

    /**
     * Reproduction for the reported "merged world comes out split / fragmented" bug. The world
     * spans a 2x2 block of tiles (0,0),(1,0),(0,1),(1,1) — all inside Hytale region (0,0), and
     * symmetric about the origin so the centering offset is (0,0) (matching the user's world per
     * the logs). A merge that selects ONLY tile (0,0) must regenerate that tile's chunks AND
     * preserve every other tile's chunks. If the non-selected tiles in the same region are lost,
     * the world is split (the repainted tile floats alone, the rest disappears).
     */
    @Test
    public void mergeWithPartialSelectionPreservesNonSelectedTilesInSameRegion() throws Exception {
        java.util.Set<Point> worldTiles = new java.util.HashSet<>(java.util.Arrays.asList(
                new Point(0, 0), new Point(1, 0), new Point(0, 1), new Point(1, 1)));
        File mapDir = createExportedHytaleMap("partial_sel", worldTiles);

        // Precondition: the full export wrote chunks for all four tiles. tile->chunk = tile*4
        // (offset 0). Tile (1,1) -> chunks (4..7, 4..7); verify chunk (4,4) and (7,7) exist.
        try (HytaleChunkStore store = new HytaleChunkStore(innerWorld(mapDir), 0, 320)) {
            assertNotNull("Setup: tile (0,0) chunk (0,0) exported", store.getChunk(0, 0));
            assertNotNull("Setup: tile (1,1) chunk (4,4) exported", store.getChunk(4, 4));
            assertNotNull("Setup: tile (1,1) chunk (7,7) exported", store.getChunk(7, 7));
        }

        // Imported world with the same four tiles; select ONLY tile (0,0) for the merge.
        World2 world = buildImportedWorld(mapDir, worldTiles);
        WorldExportSettings settings = new WorldExportSettings(
                java.util.Collections.singleton(DIM_NORMAL),
                java.util.Collections.singleton(new Point(0, 0)),
                null);

        HytaleWorldMerger merger = new HytaleWorldMerger(world, settings, mapDir, HYTALE);
        merger.merge(new File(tempDir.getRoot(), "partial_sel_bkp"), null);

        try (HytaleChunkStore store = new HytaleChunkStore(innerWorld(mapDir), 0, 320)) {
            // Selected tile regenerated:
            assertNotNull("Selected tile (0,0) chunk (0,0) must exist after merge", store.getChunk(0, 0));
            // Non-selected tiles in the SAME region must be preserved:
            assertNotNull("Non-selected tile (1,1) chunk (4,4) must survive merge", store.getChunk(4, 4));
            assertNotNull("Non-selected tile (1,1) chunk (7,7) must survive merge", store.getChunk(7, 7));
            assertNotNull("Non-selected tile (1,0) chunk (4,0) must survive merge", store.getChunk(4, 0));
            assertNotNull("Non-selected tile (0,1) chunk (0,4) must survive merge", store.getChunk(0, 4));
        }
    }

    /**
     * Closer reproduction of the user's 4-region world: tiles at (-4,-4),(-4,4),(4,-4),(4,4),
     * one per Hytale region {(-1,-1),(-1,0),(0,-1),(0,0)}, symmetric about the origin so the
     * export offset is (0,0). A merge that selects ONLY tile (4,4) regenerates region (0,0) and
     * must preserve the three OTHER whole regions by copying them from the backup. If those
     * regions are lost, the world is split into disconnected islands — the reported symptom.
     */
    @Test
    public void mergeWithSelectionInOneRegionPreservesOtherWholeRegions() throws Exception {
        java.util.Set<Point> worldTiles = new java.util.HashSet<>(java.util.Arrays.asList(
                new Point(-4, -4), new Point(-4, 4), new Point(4, -4), new Point(4, 4)));
        File mapDir = createExportedHytaleMap("multi_region", worldTiles);

        // tile t -> first chunk t*4 (offset 0). Verify all four corners exported.
        try (HytaleChunkStore store = new HytaleChunkStore(innerWorld(mapDir), 0, 320)) {
            assertNotNull("Setup: tile (4,4) chunk (16,16)", store.getChunk(16, 16));
            assertNotNull("Setup: tile (-4,-4) chunk (-16,-16)", store.getChunk(-16, -16));
            assertNotNull("Setup: tile (-4,4) chunk (-16,16)", store.getChunk(-16, 16));
            assertNotNull("Setup: tile (4,-4) chunk (16,-16)", store.getChunk(16, -16));
        }

        World2 world = buildImportedWorld(mapDir, worldTiles);
        WorldExportSettings settings = new WorldExportSettings(
                java.util.Collections.singleton(DIM_NORMAL),
                java.util.Collections.singleton(new Point(4, 4)),   // select region (0,0) only
                null);

        HytaleWorldMerger merger = new HytaleWorldMerger(world, settings, mapDir, HYTALE);
        merger.merge(new File(tempDir.getRoot(), "multi_region_bkp"), null);

        try (HytaleChunkStore store = new HytaleChunkStore(innerWorld(mapDir), 0, 320)) {
            assertNotNull("Selected tile (4,4) regenerated", store.getChunk(16, 16));
            assertNotNull("Other-region tile (-4,-4) must survive merge", store.getChunk(-16, -16));
            assertNotNull("Other-region tile (-4,4) must survive merge", store.getChunk(-16, 16));
            assertNotNull("Other-region tile (4,-4) must survive merge", store.getChunk(16, -16));
        }
    }

    /**
     * The point of the fast in-place selective merge: a region file that contains no selected tile
     * must never be rewritten. Tiles (-4,-4),(-4,4),(4,-4),(4,4) each live in a different Hytale
     * region {(-1,-1),(-1,0),(0,-1),(0,0)} (offset 0). Selecting only tile (4,4) (region (0,0))
     * must leave region (-1,-1)'s file byte-for-byte identical. The old rename+rebuild merge
     * re-serialised every untouched chunk, changing those bytes; this asserts we no longer do.
     */
    @Test
    public void selectiveMergeLeavesUntouchedRegionFilesByteIdentical() throws Exception {
        java.util.Set<Point> worldTiles = new java.util.HashSet<>(java.util.Arrays.asList(
                new Point(-4, -4), new Point(-4, 4), new Point(4, -4), new Point(4, 4)));
        File mapDir = createExportedHytaleMap("inplace_bytes", worldTiles);

        File untouchedRegion = new File(new File(mapDir, "chunks"), "-1.-1.region.bin");
        assertTrue("Setup: untouched region file must exist", untouchedRegion.isFile());
        byte[] before = java.nio.file.Files.readAllBytes(untouchedRegion.toPath());

        World2 world = buildImportedWorld(mapDir, worldTiles);
        WorldExportSettings settings = new WorldExportSettings(
                java.util.Collections.singleton(DIM_NORMAL),
                java.util.Collections.singleton(new Point(4, 4)),
                null);
        HytaleWorldMerger merger = new HytaleWorldMerger(world, settings, mapDir, HYTALE);
        merger.merge(new File(tempDir.getRoot(), "inplace_bytes_bkp"), null);

        byte[] after = java.nio.file.Files.readAllBytes(untouchedRegion.toPath());
        assertArrayEquals("A non-selected region file must be byte-identical after an in-place "
                + "selective merge (it must never be rewritten)", before, after);

        try (HytaleChunkStore store = new HytaleChunkStore(mapDir, 0, 320)) {
            assertNotNull("Selected tile (4,4) chunk (16,16) must exist after merge", store.getChunk(16, 16));
        }
    }

    // ── Centering-offset regression (the "merged result is split / corrupted" bug) ────

    /**
     * Regression test for the Hytale merge centering bug. When the existing map was produced by
     * a normal (centered) full export of a painted-from-scratch world, the merger must reproduce
     * that same centering offset, so regenerated tiles land on top of the chunks preserved from
     * the backup instead of in a separate, shifted copy of the world.
     *
     * <p>Every other test in this class uses a single tile at the origin, where the centering
     * offset is (0,0) and the bug is invisible — which is exactly why it shipped. This test uses
     * an off-origin multi-tile world so the offset is non-zero.
     */
    @Test
    public void mergeReproducesExportCenteringOffsetForPaintedFromScratchWorld() throws Exception {
        File mapDir = createExportedHytaleMap("offset_repro");

        // A painted-from-scratch world (NOT imported) whose tiles are far from the origin, so the
        // export's centering offset is clearly non-zero.
        World2 world = buildFromScratchWorld("FromScratch", 4, 5, 4, 5);
        Dimension dim = world.getDimension(NORMAL_DETAIL);

        // tiles span x,y in [4,5] -> center tile (4,4) -> block offset (-512,-512)
        Point exportOffset = HytaleWorldExporter.centeringOffset(dim.getTileCoords());
        assertEquals("Precondition: this world must have a non-zero centering offset",
                new Point(-512, -512), exportOffset);

        HytaleWorldMerger merger = new HytaleWorldMerger(world, new WorldExportSettings(), mapDir, HYTALE);
        Point mergeOffset = merger.determineBlockOffset(dim, dim.getTileCoords());

        assertEquals("Merger must reproduce the offset a full export applies, so regenerated tiles "
                + "align with the preserved chunks instead of being shifted into a disjoint copy",
                exportOffset, mergeOffset);
    }

    /**
     * Companion to the regression test above: an <em>imported</em> map's chunks already sit at
     * WorldPainter-aligned (un-centered) coordinates, so the merger must keep offset (0,0). This
     * is the original, working round-trip behaviour that the fix must not regress.
     */
    @Test
    public void mergeKeepsZeroOffsetForImportedMap() throws Exception {
        File mapDir = createExportedHytaleMap("offset_imported");
        World2 world = buildImportedWorld(mapDir);   // sets importedFrom
        Dimension dim = world.getDimension(NORMAL_DETAIL);

        HytaleWorldMerger merger = new HytaleWorldMerger(world, new WorldExportSettings(), mapDir, HYTALE);
        Point mergeOffset = merger.determineBlockOffset(dim, dim.getTileCoords());

        assertEquals("Imported maps must merge with no offset (native coordinates are 1:1)",
                new Point(0, 0), mergeOffset);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────

    /**
     * Build a small TalePainter world and export it as a Hytale save. Returns the INNER
     * WORLD DIR ({@code <saveRoot>/universe/worlds/default}) — the same directory
     * {@link org.pepsoft.worldpainter.platforms.HytalePlatformProvider#identifyMap(File)}
     * returns and the same {@code mapDir} shape {@link HytaleWorldMerger}'s constructor
     * expects.
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

        File saveDir = new File(baseDir, name);
        assertTrue("Setup precondition: exported save exists", saveDir.isDirectory());
        File worldDir = new File(new File(new File(saveDir, "universe"), "worlds"), "default");
        assertTrue("Setup precondition: inner world dir exists", worldDir.isDirectory());
        return worldDir;
    }

    /**
     * Build a tiny TalePainter world that mimics a freshly-imported Hytale world: same
     * dimensions/height as the exported save, importedFrom set to {@code mapDir/inner/config.json}.
     */
    private World2 buildImportedWorld(File mapDir) {
        // mapDir is the inner world dir per createExportedHytaleMap().
        World2 world = new World2(HYTALE, 0, 320);
        world.setName("merger-test");
        world.setCreateGoodiesChest(false);
        world.setImportedFrom(new File(mapDir, "config.json"));

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

    /**
     * Multi-tile variant of {@link #createExportedHytaleMap(String)}: builds and exports a world
     * with the given set of tiles (each filled with STONE at height 64). Returns the inner world
     * dir. Choose a tile set symmetric about the origin to keep the export's centering offset at
     * (0,0) when you want to isolate non-centering behaviour.
     */
    private File createExportedHytaleMap(String name, java.util.Set<Point> tiles) throws Exception {
        World2 world = new World2(HYTALE, 0, 320);
        world.setName(name);
        world.setCreateGoodiesChest(false);

        long seed = 42L;
        TileFactory tileFactory = TileFactoryFactory.createFlatTileFactory(
                seed, Terrain.GRASS, 0, 320, 64, 62, false, false);
        Dimension.Anchor anchor = new Dimension.Anchor(DIM_NORMAL, Dimension.Role.DETAIL, false, 0);
        Dimension dim = new Dimension(world, "Surface", seed, tileFactory, anchor);
        dim.setEventsInhibited(true);
        for (Point t : tiles) {
            Tile tile = tileFactory.createTile(t.x, t.y);
            for (int x = 0; x < 128; x++) {
                for (int z = 0; z < 128; z++) {
                    tile.setHeight(x, z, 64);
                    tile.setTerrain(x, z, Terrain.STONE);
                    HytaleTerrainLayer.setTerrainIndex(tile, x, z, HytaleTerrain.STONE.getLayerIndex());
                }
            }
            dim.addTile(tile);
        }
        dim.setEventsInhibited(false);
        world.addDimension(dim);

        File baseDir = tempDir.newFolder("base_" + name);
        new HytaleWorldExporter(world, new WorldExportSettings())
                .export(baseDir, name, null, null);

        File saveDir = new File(baseDir, name);
        assertTrue("Setup precondition: exported save exists", saveDir.isDirectory());
        File worldDir = new File(new File(new File(saveDir, "universe"), "worlds"), "default");
        assertTrue("Setup precondition: inner world dir exists", worldDir.isDirectory());
        return worldDir;
    }

    /**
     * Multi-tile variant of {@link #buildImportedWorld(File)}: an imported world (importedFrom set)
     * containing the given tiles, each filled with STONE at height 64.
     */
    private World2 buildImportedWorld(File mapDir, java.util.Set<Point> tiles) {
        World2 world = new World2(HYTALE, 0, 320);
        world.setName("merger-test");
        world.setCreateGoodiesChest(false);
        world.setImportedFrom(new File(mapDir, "config.json"));

        long seed = 99L;
        TileFactory tileFactory = TileFactoryFactory.createFlatTileFactory(
                seed, Terrain.STONE, 0, 320, 64, 62, false, false);
        Dimension.Anchor anchor = new Dimension.Anchor(DIM_NORMAL, Dimension.Role.DETAIL, false, 0);
        Dimension dim = new Dimension(world, "Surface", seed, tileFactory, anchor);
        dim.setEventsInhibited(true);
        for (Point t : tiles) {
            Tile tile = tileFactory.createTile(t.x, t.y);
            for (int x = 0; x < 128; x++) {
                for (int z = 0; z < 128; z++) {
                    tile.setHeight(x, z, 64);
                    tile.setTerrain(x, z, Terrain.STONE);
                    HytaleTerrainLayer.setTerrainIndex(tile, x, z, HytaleTerrain.STONE.getLayerIndex());
                }
            }
            dim.addTile(tile);
        }
        dim.setEventsInhibited(false);
        world.addDimension(dim);
        return world;
    }

    /**
     * Build a painted-from-scratch (NOT imported) Hytale world whose surface dimension has a
     * solid block of tiles spanning {@code [minTileX..maxTileX] x [minTileY..maxTileY]}.
     * Deliberately does not call {@code setImportedFrom}, so the merger treats it as a
     * from-scratch (centered) world.
     */
    private World2 buildFromScratchWorld(String name, int minTileX, int maxTileX, int minTileY, int maxTileY) {
        World2 world = new World2(HYTALE, 0, 320);
        world.setName(name);
        world.setCreateGoodiesChest(false);

        long seed = 7L;
        TileFactory tileFactory = TileFactoryFactory.createFlatTileFactory(
                seed, Terrain.GRASS, 0, 320, 64, 62, false, false);
        Dimension.Anchor anchor = new Dimension.Anchor(DIM_NORMAL, Dimension.Role.DETAIL, false, 0);
        Dimension dim = new Dimension(world, "Surface", seed, tileFactory, anchor);
        dim.setEventsInhibited(true);
        for (int tx = minTileX; tx <= maxTileX; tx++) {
            for (int ty = minTileY; ty <= maxTileY; ty++) {
                dim.addTile(tileFactory.createTile(tx, ty));
            }
        }
        dim.setEventsInhibited(false);
        world.addDimension(dim);
        return world;
    }

    /** {@code mapDir} is already the inner world dir per the test helpers; this is a no-op alias kept for readability at call sites. */
    private static File innerWorld(File mapDir) {
        return mapDir;
    }

    /** Build a merger over a freshly-exported map with the same importedFrom world setup. */
    private HytaleWorldMerger newMerger(File mapDir) {
        World2 world = buildImportedWorld(mapDir);
        return new HytaleWorldMerger(world, new WorldExportSettings(), mapDir, HYTALE);
    }

    /**
     * Edit a chunk in the freshly-exported save (which will become the backup once the
     * merger renames it). Used to seed "original" state — extra blocks, biomes, etc.
     */
    private void injectIntoOriginal(File mapDir, int chunkX, int chunkZ, ChunkMutator mutator) throws Exception {
        try (HytaleChunkStore store = new HytaleChunkStore(innerWorld(mapDir), 0, 320)) {
            HytaleChunk chunk = (HytaleChunk) store.getChunk(chunkX, chunkZ);
            assertNotNull("Pre-merge: original chunk (" + chunkX + "," + chunkZ + ") must exist", chunk);
            mutator.mutate(chunk);
            store.saveChunk(chunk);
            store.flush();
        }
    }

    @FunctionalInterface
    private interface ChunkMutator {
        void mutate(HytaleChunk chunk) throws Exception;
    }

    /** Null-safe block id lookup — sections may return null for unset positions. */
    private static String idOrEmpty(HytaleBlock block) {
        return (block == null) ? "Empty" : block.id;
    }

    /** Build a tile suitable for direct applyMergeOverrides() tests: every column at the given height. */
    private Tile freshFlatTile(int terrainHeight) {
        long seed = 1L;
        TileFactory tileFactory = TileFactoryFactory.createFlatTileFactory(
                seed, Terrain.STONE, 0, 320, terrainHeight, terrainHeight - 2, false, false);
        Tile tile = tileFactory.createTile(0, 0);
        for (int x = 0; x < 128; x++) {
            for (int z = 0; z < 128; z++) {
                tile.setHeight(x, z, terrainHeight);
                tile.setTerrain(x, z, Terrain.STONE);
            }
        }
        return tile;
    }

    /**
     * Lightweight in-memory chunk store that returns a single prepared chunk from
     * {@code getChunk(0, 0)} and ignores everything else. Used for biome-merge unit tests
     * because the BSON serializer does not currently persist per-column biome names.
     */
    private static final class InMemoryHytaleChunkStore extends HytaleChunkStore {
        private final HytaleChunk fixedChunk;

        InMemoryHytaleChunkStore(File worldDir, int minHeight, int maxHeight, HytaleChunk fixedChunk) {
            super(worldDir, minHeight, maxHeight);
            this.fixedChunk = fixedChunk;
        }

        @Override
        public org.pepsoft.minecraft.Chunk getChunk(int x, int z) {
            if (x == fixedChunk.getxPos() && z == fixedChunk.getzPos()) {
                return fixedChunk;
            }
            return null;
        }
    }
}
