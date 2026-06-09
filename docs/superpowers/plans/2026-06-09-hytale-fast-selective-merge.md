# Hytale Fast In-Place Selective-Tile Merge — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make "merge N selected tiles" into an already-exported Hytale world cost ~generate+write of the selected tiles only, instead of an O(world-size) decompress/recompress of every untouched chunk.

**Architecture:** When a tile selection is active, `HytaleWorldMerger` patches the existing world in place: it opens each region file containing a selected tile with `openOrCreate()` (not `create()`), overwrites only that tile's chunks via `writeChunk`, and never touches any other region file. No backup, no rename, no rebuild — a pre-merge dialog warns the user instead. The per-tile merge logic (entities, block health, biomes, merge flags) is reused unchanged, reading each original chunk from the live world before it is overwritten.

**Tech Stack:** Java 17, Maven (JDK 17 toolchain), JUnit 4. Modules: `WPCore` (merger/exporter/tests), `WPGUI` (merge dialog). Hytale region format: `HytaleRegionFile` (random-access indexed store, Zstd + BSON).

---

## Background (why this works)

- Today `HytaleWorldMerger.merge()` renames the save to a backup, calls `super.export()` (which only regenerates selected tiles), then `preserveUntouchedOriginalChunks` walks **every** backup chunk and decompress→recompress→rewrites it. That preserve step is the O(world-size) cost.
- `HytaleRegionFile.writeChunk(localX, localZ, chunk)` is random-access: it writes one chunk blob and patches one 4-byte index entry; it does **not** rewrite the file. The only reason the current merge can't use this is `exportRegion` opens region files with `create()` (truncates). Switching to `openOrCreate()` for the in-place path preserves every other chunk's blob for free.
- Scope is Hytale only. The Minecraft `JavaWorldMerger` already raw-copies untouched regions; it is untouched. Full merges (no selection) keep today's behavior.

## File structure

- **Modify** `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/export/HytaleWorldExporter.java`
  — add an in-place open-mode flag + a slim `exportSelectedTilesInPlace` entry point; switch `exportRegion`'s region-file open from `create()` to `openOrCreate()` when in-place.
- **Modify** `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/export/HytaleWorldMerger.java`
  — gate `merge()` on a tile selection and add `mergeSelectedTilesInPlace`.
- **Modify** `WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/MergeWorldDialog.java`
  — add the pre-merge in-place/backup warning for the Hytale + selection case.
- **Modify** `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/export/HytaleWorldMergerTest.java`
  — add the byte-preservation driver test and two behavior-lock tests.

---

## Task 1: In-place selective-merge engine

**Files:**
- Test: `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/export/HytaleWorldMergerTest.java`
- Modify: `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/export/HytaleWorldExporter.java`
- Modify: `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/export/HytaleWorldMerger.java`

- [ ] **Step 1: Write the failing test**

Add this method to `HytaleWorldMergerTest` (after the existing `mergeWithSelectionInOneRegionPreservesOtherWholeRegions` test, before the `// ── Centering-offset regression` section). It uses the existing `createExportedHytaleMap(name, tiles)` and `buildImportedWorld(mapDir, tiles)` helpers.

```java
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
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -pl WPCore -am test -Dtest=HytaleWorldMergerTest#selectiveMergeLeavesUntouchedRegionFilesByteIdentical`
Expected: FAIL on `assertArrayEquals` ("non-selected region file must be byte-identical…"). Today's selective merge renames the save to a backup and re-serialises every untouched chunk via `preserveUntouchedOriginalChunks`, so region `-1.-1.region.bin` is rewritten and its bytes differ.

- [ ] **Step 3: Add the in-place open-mode flag to `HytaleWorldExporter`**

In `HytaleWorldExporter.java`, find the offset fields:

```java
    // Offset to center terrain at world origin (computed during export)
    private int blockOffsetX = 0;
    private int blockOffsetZ = 0;
```

Add directly below them:

```java

    // When true, exportRegion opens existing region files (openOrCreate) and overwrites only the
    // selected tiles' chunks instead of truncating (create). Enables fast in-place selective
    // merges; never set during a normal export. Set by exportSelectedTilesInPlace().
    private boolean inPlaceMerge = false;
```

- [ ] **Step 4: Switch `exportRegion`'s open mode when in-place**

In `HytaleWorldExporter.exportRegion`, find:

```java
        try (HytaleRegionFile regionFile = new HytaleRegionFile(regionPath)) {
            regionFile.create();
```

Replace with:

```java
        try (HytaleRegionFile regionFile = new HytaleRegionFile(regionPath)) {
            if (inPlaceMerge) {
                // In-place selective merge: open the existing region file and overwrite ONLY the
                // selected tiles' chunks, preserving every other chunk's blob. create() would
                // truncate the file and lose the untouched chunks.
                regionFile.openOrCreate();
            } else {
                regionFile.create();
            }
```

- [ ] **Step 5: Add the `exportSelectedTilesInPlace` entry point**

In `HytaleWorldExporter.java`, insert this method immediately before the private `exportDimension` method (the line `private ChunkFactory.Stats exportDimension(File worldDir, Dimension dimension, Set<Point> selectedTiles, ProgressReceiver progressReceiver)`):

```java
    /**
     * Fast in-place selective merge: regenerate only the tiles in the active tile selection and
     * write them directly into the existing world at {@code worldDir}, overwriting just those
     * tiles' chunks and leaving every other chunk and region file untouched on disk. Unlike
     * {@link #export}, this does not wipe or rebuild the save and makes no backup.
     *
     * <p>The caller (e.g. {@link HytaleWorldMerger}) must pre-set {@link #originalChunkStore} to
     * the live world so the per-chunk merge ({@code mergeOriginalChunkData} /
     * {@code applyMergeOverrides}) reads each original chunk before it is overwritten. Within a
     * region all original reads happen before any write, so the read-then-overwrite on the same
     * file is safe.
     *
     * @param worldDir the inner Hytale world dir (.../universe/worlds/default) to patch in place
     */
    protected void exportSelectedTilesInPlace(File worldDir, ProgressReceiver progressReceiver)
            throws IOException, ProgressReceiver.OperationCancelled {
        HytaleBlockRegistry.ensureMaterialsRegistered();
        Dimension dim0 = world.getDimension(NORMAL_DETAIL);
        if (dim0 == null) {
            return;
        }
        prefabPaster = new HytalePrefabPaster(HytaleTerrain.getHytaleAssetsDir());
        final Set<Point> selectedTiles = worldExportSettings.getTilesToExport();
        inPlaceMerge = true;
        try {
            exportDimension(worldDir, dim0, selectedTiles, progressReceiver);
        } finally {
            inPlaceMerge = false;
        }
    }

```

(`HytaleBlockRegistry`, `HytaleTerrain`, `HytalePrefabPaster`, `Set`, `Point`, `ProgressReceiver`, `IOException` are all already imported/used in this file — see `export()` which calls `HytaleBlockRegistry.ensureMaterialsRegistered()` and `HytaleTerrain.getHytaleAssetsDir()`.)

- [ ] **Step 6: Gate `HytaleWorldMerger.merge()` on a tile selection**

In `HytaleWorldMerger.merge`, find:

```java
        performSanityChecks();

        Objects.requireNonNull(backupDir, "backupDir");
```

Replace with:

```java
        performSanityChecks();

        // Fast path: a tile selection means "apply only these tiles to the already-exported
        // world". Patch them in place — overwrite just the selected tiles' chunks and touch
        // nothing else — instead of renaming the whole save to a backup and rebuilding it. Cost
        // is proportional to the selection, not the world size. No backup is made; the merge
        // dialog warns the user first.
        if (worldExportSettings.getTilesToExport() != null) {
            logger.info("Fast in-place selective merge of {} tile(s) into {}",
                    worldExportSettings.getTilesToExport().size(), mapDir);
            mergeSelectedTilesInPlace(progressReceiver);
            return;
        }

        Objects.requireNonNull(backupDir, "backupDir");
```

- [ ] **Step 7: Add `mergeSelectedTilesInPlace` to `HytaleWorldMerger`**

In `HytaleWorldMerger.java`, insert this method immediately after the `merge(...)` method (before `computeSaveRoot`):

```java
    /**
     * Apply only the active tile selection to the existing world in place. Overwrites just the
     * selected tiles' chunks and leaves every other chunk and region file untouched on disk; no
     * rename, no backup, no save-structure rebuild. Reads each original chunk from the live world
     * (for entity / block-health / biome / block merge) before overwriting it.
     */
    private void mergeSelectedTilesInPlace(ProgressReceiver progressReceiver)
            throws IOException, ProgressReceiver.OperationCancelled {
        Dimension surface = world.getDimension(NORMAL_DETAIL);
        // Read originals from the LIVE world. exportDimension() leaves a pre-set originalChunkStore
        // untouched (see HytaleWorldExporter.openOriginalChunkStore) and closes it when done.
        originalChunkStore = new HytaleChunkStore(mapDir, surface.getMinHeight(), surface.getMaxHeight());
        exportSelectedTilesInPlace(mapDir, progressReceiver);
    }
```

(`HytaleChunkStore`, `Dimension`, `ProgressReceiver`, `IOException`, `NORMAL_DETAIL` are already imported in this file; `originalChunkStore` and `exportSelectedTilesInPlace` are inherited from `HytaleWorldExporter`; `mapDir` is the inner world dir field.)

- [ ] **Step 8: Run the test to verify it passes**

Run: `mvn -pl WPCore -am test -Dtest=HytaleWorldMergerTest#selectiveMergeLeavesUntouchedRegionFilesByteIdentical`
Expected: PASS. The selected region (0,0) is patched in place; region (-1,-1) is never opened, so its bytes are unchanged.

- [ ] **Step 9: Run the full merger test class to confirm no regressions**

Run: `mvn -pl WPCore -am test -Dtest=HytaleWorldMergerTest`
Expected: PASS, including the existing `mergeWithPartialSelectionPreservesNonSelectedTilesInSameRegion` and `mergeWithSelectionInOneRegionPreservesOtherWholeRegions` (these now exercise the fast path) and `mergeBacksUpOriginalAndRegeneratesChunksWithTilesPainted` (no selection → unchanged full-merge path, backup still created).

- [ ] **Step 10: Commit**

```bash
git add WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/export/HytaleWorldExporter.java \
        WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/export/HytaleWorldMerger.java \
        WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/export/HytaleWorldMergerTest.java
git commit -m "feat(hytale): in-place selective-tile merge (no whole-world rewrite)"
```

---

## Task 2: Behavior-lock tests (repaint lands; original blocks preserved)

These lock the two behaviors the in-place path must keep: the selected tile's repaint is applied, and the original chunk's blocks are merged back (read from the live world before overwrite). Both are expected to PASS on the Task 1 implementation; they guard against future regressions.

**Files:**
- Test: `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/export/HytaleWorldMergerTest.java`

- [ ] **Step 1: Add the "repaint lands in place" test**

Add to `HytaleWorldMergerTest` (next to the Task 1 test):

```java
/**
 * The repaint on a selected tile must actually land in the in-place-merged chunk. The original
 * export put terrain up to y=64; raising the selected tile to height 100 must make y=90 solid
 * stone after the merge.
 */
@Test
public void selectiveMergeAppliesRepaintToSelectedTileInPlace() throws Exception {
    File mapDir = createExportedHytaleMap("inplace_update");   // single tile (0,0), height 64

    World2 world = buildImportedWorld(mapDir);
    Dimension dim = world.getDimension(NORMAL_DETAIL);
    Tile tile = dim.getTile(0, 0);
    dim.setEventsInhibited(true);
    for (int x = 0; x < 128; x++) {
        for (int z = 0; z < 128; z++) {
            tile.setHeight(x, z, 100);
        }
    }
    dim.setEventsInhibited(false);

    WorldExportSettings settings = new WorldExportSettings(
            java.util.Collections.singleton(DIM_NORMAL),
            java.util.Collections.singleton(new Point(0, 0)),
            null);
    HytaleWorldMerger merger = new HytaleWorldMerger(world, settings, mapDir, HYTALE);
    merger.merge(new File(tempDir.getRoot(), "inplace_update_bkp"), null);

    try (HytaleChunkStore store = new HytaleChunkStore(mapDir, 0, 320)) {
        HytaleChunk chunk = (HytaleChunk) store.getChunk(0, 0);
        assertNotNull(chunk);
        assertEquals("Repaint to height 100 must land in the in-place-merged chunk",
                HytaleTerrain.STONE.getPrimaryBlock().id, idOrEmpty(chunk.getHytaleBlock(0, 90, 0)));
    }
}
```

- [ ] **Step 2: Add the "original above-ground blocks preserved" test**

Add to `HytaleWorldMergerTest`:

```java
/**
 * The in-place path must read each original chunk from the LIVE world (the same file it then
 * overwrites) and merge its data back. Seed a man-made block above the terrain in the original;
 * with mergeBlocksAboveGround (default true) it must survive the in-place selective merge.
 */
@Test
public void selectiveMergePreservesOriginalAboveGroundBlocksInPlace() throws Exception {
    File mapDir = createExportedHytaleMap("inplace_meta");   // tile (0,0), terrainHeight 64
    injectIntoOriginal(mapDir, 0, 0, chunk ->
            chunk.setHytaleBlock(0, 70, 0, HytaleBlock.of("Cloth_Wool")));   // above-ground, man-made

    World2 world = buildImportedWorld(mapDir);   // mergeBlocksAboveGround defaults true
    WorldExportSettings settings = new WorldExportSettings(
            java.util.Collections.singleton(DIM_NORMAL),
            java.util.Collections.singleton(new Point(0, 0)),
            null);
    HytaleWorldMerger merger = new HytaleWorldMerger(world, settings, mapDir, HYTALE);
    merger.merge(new File(tempDir.getRoot(), "inplace_meta_bkp"), null);

    try (HytaleChunkStore store = new HytaleChunkStore(mapDir, 0, 320)) {
        HytaleChunk chunk = (HytaleChunk) store.getChunk(0, 0);
        assertNotNull(chunk);
        assertEquals("In-place merge must read the original (live) chunk and keep its above-ground "
                + "block", "Cloth_Wool", idOrEmpty(chunk.getHytaleBlock(0, 70, 0)));
    }
}
```

- [ ] **Step 3: Run both new tests**

Run: `mvn -pl WPCore -am test -Dtest=HytaleWorldMergerTest#selectiveMergeAppliesRepaintToSelectedTileInPlace+selectiveMergePreservesOriginalAboveGroundBlocksInPlace`
Expected: PASS for both.

- [ ] **Step 4: Commit**

```bash
git add WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/export/HytaleWorldMergerTest.java
git commit -m "test(hytale): lock in-place selective-merge repaint + original-block preservation"
```

---

## Task 3: Pre-merge warning in the merge dialog

Adds the "fast in-place, no backup, don't change the tile layout" warning for the Hytale + tile-selection case. The dialog already shows a confirm built from a `StringBuilder sb`; we append one bullet and force the confirm to show even if the generic tile-selection warning was disabled. No automated test (Swing dialog); verified manually.

**Files:**
- Modify: `WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/MergeWorldDialog.java`

- [ ] **Step 1: Append the in-place warning bullet**

In `MergeWorldDialog.java`, find the end of the existing tile-selection warning block:

```java
            sb.append("<li>A tile selection is active! Only " + selectedTiles.size() + " tiles of the<br>" + dim + " dimension are going to be merged.");
            showWarning = true;
        }
        sb.append("</ul>Do you want to continue with the merge?</html>");
```

Replace with:

```java
            sb.append("<li>A tile selection is active! Only " + selectedTiles.size() + " tiles of the<br>" + dim + " dimension are going to be merged.");
            showWarning = true;
        }
        if (isHytale && radioButtonExportSelection.isSelected()) {
            // The Hytale selective merge patches the selected tiles directly into the existing
            // world and makes no backup (see HytaleWorldMerger.mergeSelectedTilesInPlace). Warn
            // regardless of disableTileSelectionWarning, and state the one assumption that matters:
            // the tile layout must not have changed since the export.
            sb.append("<li><b>Fast in-place merge:</b> the selected tiles are written directly into "
                    + "your exported world and <b>no backup is made</b>.<br>"
                    + "Make sure you have your own backup, and that you have <b>not added or removed "
                    + "tiles</b> since the last export (only repaint existing tiles).");
            showWarning = true;
        }
        sb.append("</ul>Do you want to continue with the merge?</html>");
```

(`isHytale` is the local declared earlier in this method: `final boolean isHytale = HytaleTerrainHelper.isHytale(platform);`. `radioButtonExportSelection` and `selectedTiles` are existing fields.)

- [ ] **Step 2: Build WPGUI to confirm it compiles**

Run: `mvn -DskipTests=true -pl WPGUI -am install`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/MergeWorldDialog.java
git commit -m "feat(hytale): warn about in-place no-backup merge before applying selected tiles"
```

---

## Task 4: Full verification

**Files:** none (verification only).

- [ ] **Step 1: Run the full Hytale merger + exporter test classes**

Run: `mvn -pl WPCore -am test -Dtest=HytaleWorldMergerTest,HytaleWorldExporterLayerOrderingTest`
Expected: PASS. Confirms the in-place change didn't break the full-merge path or layer ordering.

- [ ] **Step 2: Build the GUI fat path to confirm the whole tree compiles**

Run: `mvn -DskipTests=true -pl WPGUI -am install`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Manual verification (verify skill)**

On a large already-exported Hytale world: open Merge, pick the existing map, choose "merge N selected tiles" for 2–3 tiles, confirm the new warning appears, proceed. Confirm: (a) it finishes in seconds rather than full-export time; (b) the world loads in Hytale with the edited tiles updated and everything else intact.

- [ ] **Step 4: Final commit (if any manual fixups were needed)**

```bash
git add -A
git commit -m "chore(hytale): finalize fast selective-merge verification"
```

---

## Notes / known limitations (per approved spec)

- **No rollback.** Without a backup, a cancel or crash mid-merge leaves the already-written regions updated and the rest original — partial but non-corrupt. Accepted; surfaced by the warning.
- **No layout-change guard.** By decision, the code assumes only existing tiles were repainted; a changed tile extent shifts the centering offset and can misplace chunks. Surfaced in the warning, not checked.
- **Concurrent original reads.** For a few selected tiles the work is usually one region (`maxConcurrentRegions` collapses to 1), so the shared `originalChunkStore` is read single-threaded. A many-region selection could read it from multiple region threads; if that ever proves a problem, synchronize `HytaleChunkStore.getRegionFile`. Out of scope here.

## Self-review

- **Spec coverage:** in-place patching via `openOrCreate` (Task 1, steps 4–5); selection gating + `mergeSelectedTilesInPlace` reading live originals (Task 1, steps 6–7); merge semantics unchanged — reuses `exportDimension`/`mergeOriginalChunkData`/`applyMergeOverrides` (Task 1) and locked by Task 2; pre-merge warning, no backup, layout assumption stated (Task 3); tests incl. byte-preservation proof + mixed-region via existing tests (Tasks 1–2); Minecraft + full-merge paths untouched (Task 1 gate). All spec sections map to a task.
- **Placeholder scan:** none — every code step shows complete code and exact `mvn` commands with expected results.
- **Type/name consistency:** `inPlaceMerge` (field) / `exportSelectedTilesInPlace` (exporter) / `mergeSelectedTilesInPlace` (merger) used identically across tasks; helper/method names (`createExportedHytaleMap`, `buildImportedWorld`, `injectIntoOriginal`, `idOrEmpty`, `HytaleTerrain.STONE.getPrimaryBlock().id`, `HytaleChunkStore`, `WorldExportSettings(Set,Set,Set)`) match the existing test file and source.
