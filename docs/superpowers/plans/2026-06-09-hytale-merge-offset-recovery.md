# Hytale Merge Offset Recovery & Persistence — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Hytale merges reuse the exact block offset the existing map was exported with — via a stored sidecar, falling back to spawn-based recovery validated against the existing chunks — so revamped worlds merge into the right place, and abort rather than misplace when alignment can't be determined.

**Architecture:** Export writes the block offset to a `.talepainter-export.json` sidecar. The merger resolves the offset once, early in `merge()` (before any rename), by: stored sidecar → spawn recovery (`saveSpawn − worldSpawn`) → today's centering heuristic, picking the candidate whose tiles best cover the existing regions, and aborting if none clears a coverage threshold. The resolved offset feeds `determineBlockOffset` for both the in-place and full merge paths.

**Tech Stack:** Java 17, Maven (JDK 17 toolchain), JUnit 4, GSON. Module: `WPCore`.

---

## Background / why

`HytaleWorldMerger.determineBlockOffset` recomputes `centeringOffset(getTileCoords())`, which depends on the tile bounding box. When a world is revamped (tiles added/removed) the center drifts, so merged tiles land displaced from the chunks already exported. The merge has no record of the original offset. Fix: persist it (sidecar) and, for maps exported before the sidecar existed, recover it from the spawn point the export baked into `config.json` (`saveSpawn = worldSpawn + offset`, `HytaleWorldConfigWriter.java:83`), validated against the existing chunk layout. See `docs/superpowers/specs/2026-06-09-hytale-merge-offset-recovery-design.md`.

## File structure

- **Create** `WPCore/.../hytale/export/HytaleExportMetadata.java` — sidecar read/write (one responsibility, independently testable).
- **Create** `WPCore/.../test/.../hytale/export/HytaleExportMetadataTest.java` — sidecar unit tests.
- **Modify** `WPCore/.../hytale/export/HytaleWorldExporter.java` — write the sidecar in `exportDimension`.
- **Modify** `WPCore/.../hytale/export/HytaleWorldMerger.java` — offset resolution (`resolveBlockOffset`, spawn recovery, region enumeration, coverage), wire into `merge()`, `determineBlockOffset` returns the resolved value.
- **Modify** `WPCore/.../test/.../hytale/export/HytaleWorldMergerTest.java` — resolution + recovery + abort + E2E tests.

---

## Task 1: Sidecar storage (`HytaleExportMetadata`) + write at export

**Files:**
- Create: `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/export/HytaleExportMetadata.java`
- Create: `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/export/HytaleExportMetadataTest.java`
- Modify: `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/export/HytaleWorldExporter.java`

- [ ] **Step 1: Write the failing test**

Create `HytaleExportMetadataTest.java`:

```java
package org.pepsoft.worldpainter.hytale.export;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.awt.Point;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.*;

public class HytaleExportMetadataTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Test
    public void writeThenReadRoundTripsTheOffset() throws Exception {
        File worldDir = tempDir.newFolder("world");
        HytaleExportMetadata.writeBlockOffset(worldDir, -512, 256);

        File sidecar = new File(worldDir, HytaleExportMetadata.SIDECAR_NAME);
        assertTrue("Sidecar file must be created", sidecar.isFile());

        Point read = HytaleExportMetadata.readBlockOffset(worldDir);
        assertNotNull(read);
        assertEquals(-512, read.x);
        assertEquals(256, read.y);
    }

    @Test
    public void readReturnsNullWhenAbsent() throws Exception {
        assertNull(HytaleExportMetadata.readBlockOffset(tempDir.newFolder("empty")));
    }

    @Test
    public void readReturnsNullWhenMalformed() throws Exception {
        File worldDir = tempDir.newFolder("bad");
        Files.write(new File(worldDir, HytaleExportMetadata.SIDECAR_NAME).toPath(),
                "{ not valid json".getBytes(StandardCharsets.UTF_8));
        assertNull("Malformed sidecar must read as null, not throw",
                HytaleExportMetadata.readBlockOffset(worldDir));
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `mvn -pl WPCore -am test -Dtest=HytaleExportMetadataTest`
Expected: FAIL to **compile** ("cannot find symbol HytaleExportMetadata"). That is the expected red for a not-yet-created class.

- [ ] **Step 3: Create `HytaleExportMetadata`**

```java
package org.pepsoft.worldpainter.hytale.export;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Point;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Reads/writes the TalePainter export sidecar that records the block offset a Hytale world was
 * exported with, so later merges reuse the exact same alignment instead of recomputing a centering
 * offset that drifts when the world's tile bounds change.
 *
 * <p>Stored as a sidecar next to {@code config.json} (not inside it): Hytale rewrites config.json
 * during play and may drop unknown fields, whereas it ignores — and therefore preserves — this file.
 */
final class HytaleExportMetadata {

    static final String SIDECAR_NAME = ".talepainter-export.json";
    private static final int VERSION = 1;
    private static final Logger logger = LoggerFactory.getLogger(HytaleExportMetadata.class);

    private HytaleExportMetadata() {
    }

    /** Write the export block offset to {@code <worldDir>/.talepainter-export.json}. */
    static void writeBlockOffset(File worldDir, int blockOffsetX, int blockOffsetZ) {
        final JsonObject json = new JsonObject();
        json.addProperty("version", VERSION);
        json.addProperty("blockOffsetX", blockOffsetX);
        json.addProperty("blockOffsetZ", blockOffsetZ);
        final File file = new File(worldDir, SIDECAR_NAME);
        try {
            Files.write(file.toPath(), new Gson().toJson(json).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            logger.warn("Could not write export sidecar {}: {}", file, e.getMessage());
        }
    }

    /** Read the stored block offset, or {@code null} if the sidecar is absent or unreadable. */
    static Point readBlockOffset(File worldDir) {
        final File file = new File(worldDir, SIDECAR_NAME);
        if (! file.isFile()) {
            return null;
        }
        try {
            final String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            final JsonObject json = JsonParser.parseString(text).getAsJsonObject();
            if ((! json.has("blockOffsetX")) || (! json.has("blockOffsetZ"))) {
                return null;
            }
            return new Point(json.get("blockOffsetX").getAsInt(), json.get("blockOffsetZ").getAsInt());
        } catch (Exception e) {
            logger.warn("Could not read export sidecar {}: {}", file, e.getMessage());
            return null;
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -pl WPCore -am test -Dtest=HytaleExportMetadataTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Write the sidecar at export**

In `HytaleWorldExporter.exportDimension`, find where the offset is set:

```java
            Point blockOffset = determineBlockOffset(dimension, tileCoords);
            this.blockOffsetX = blockOffset.x;
            this.blockOffsetZ = blockOffset.y;

            logger.info("Block offset for export: ({},{})", blockOffsetX, blockOffsetZ);
```

Insert the sidecar write immediately after the `logger.info` line:

```java
            logger.info("Block offset for export: ({},{})", blockOffsetX, blockOffsetZ);

            // Persist the offset so later merges reuse this exact alignment instead of recomputing
            // a centering offset that drifts when the world's tile bounds change. Covers every path
            // through exportDimension: plain export, full merge, and in-place selective merge.
            HytaleExportMetadata.writeBlockOffset(worldDir, blockOffset.x, blockOffset.y);
```

- [ ] **Step 6: Add a test that export writes the sidecar**

Add to `HytaleWorldMergerTest` (it already has `createExportedHytaleMap` which performs a real export):

```java
@Test
public void exportWritesBlockOffsetSidecar() throws Exception {
    File mapDir = createExportedHytaleMap("sidecar_written");
    Point stored = HytaleExportMetadata.readBlockOffset(mapDir);
    assertNotNull("Export must write the block-offset sidecar", stored);
}
```

(Imports already present in that test file: `java.awt.Point`, `java.io.File`. `HytaleExportMetadata` is same-package.)

- [ ] **Step 7: Run the merger test class**

Run: `mvn -pl WPCore -am test -Dtest=HytaleWorldMergerTest,HytaleExportMetadataTest`
Expected: PASS. (`HytaleWorldMergerTest` now has its prior tests + `exportWritesBlockOffsetSidecar`.)

- [ ] **Step 8: Commit**

```bash
git add WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/export/HytaleExportMetadata.java \
        WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/export/HytaleExportMetadataTest.java \
        WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/export/HytaleWorldExporter.java \
        WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/export/HytaleWorldMergerTest.java
git commit -m "feat(hytale): persist export block offset in a sidecar"
```

---

## Task 2: Merger offset helpers (spawn recovery, region enumeration, coverage)

**Files:**
- Modify: `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/export/HytaleWorldMerger.java`
- Modify: `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/export/HytaleWorldMergerTest.java`

- [ ] **Step 1: Write the failing tests**

Add to `HytaleWorldMergerTest`:

```java
@Test
public void coverageCountsExistingRegionsExplainedByTiles() throws Exception {
    File mapDir = createExportedHytaleMap("coverage_unit");
    HytaleWorldMerger merger = newMerger(mapDir);

    java.util.Set<Point> existing = new java.util.HashSet<>(java.util.Arrays.asList(
            new Point(0, 0), new Point(-1, 0)));
    // Offset 0: tile (0,0) -> region 0,0 ; tile (8,0) -> block 1024 -> region 0,0 too (1024>>10=1? no: 1024>>10=1)
    // Use tiles that land one in each existing region under offset (0,0):
    //   tile (0,0)  -> block 0..127      -> region 0,0
    //   tile (-8,0) -> block -1024..-897 -> region -1,0
    java.util.Set<Point> tiles = new java.util.HashSet<>(java.util.Arrays.asList(
            new Point(0, 0), new Point(-8, 0)));
    assertEquals("Both existing regions explained -> coverage 1.0",
            1.0, merger.coverage(tiles, new Point(0, 0), existing), 1e-9);

    // A wrong offset that lands neither tile on an existing region -> 0.0
    assertEquals("No existing region explained -> coverage 0.0",
            0.0, merger.coverage(new java.util.HashSet<>(java.util.Arrays.asList(new Point(50, 50))),
                    new Point(0, 0), existing), 1e-9);
}

@Test
public void recoverOffsetFromSpawnSubtractsWorldSpawnFromSaveSpawn() throws Exception {
    File mapDir = createExportedHytaleMap("spawn_unit");
    // Overwrite config.json with a known spawn so the math is deterministic.
    File config = new File(mapDir, "config.json");
    String json = "{ \"SpawnProvider\": { \"SpawnPoint\": { \"X\": 2448.0, \"Y\": 142.0, \"Z\": 1184.0 } } }";
    java.nio.file.Files.write(config.toPath(), json.getBytes(java.nio.charset.StandardCharsets.UTF_8));

    World2 world = buildImportedWorld(mapDir);
    world.setSpawnPoint(new Point(400, 184));   // wpSpawn
    HytaleWorldMerger merger = new HytaleWorldMerger(world, new WorldExportSettings(), mapDir, HYTALE);

    Point off = merger.recoverOffsetFromSpawn(mapDir);
    assertNotNull(off);
    assertEquals("offsetX = saveSpawn.X - wpSpawn.x = 2448 - 400", 2048, off.x);
    assertEquals("offsetZ = saveSpawn.Z - wpSpawn.y = 1184 - 184", 1000, off.y);
}

@Test
public void recoverOffsetFromSpawnReturnsNullWhenNoSpawnInConfig() throws Exception {
    File mapDir = createExportedHytaleMap("spawn_none");
    File config = new File(mapDir, "config.json");
    java.nio.file.Files.write(config.toPath(), "{ }".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    World2 world = buildImportedWorld(mapDir);
    world.setSpawnPoint(new Point(0, 0));
    HytaleWorldMerger merger = new HytaleWorldMerger(world, new WorldExportSettings(), mapDir, HYTALE);
    assertNull(merger.recoverOffsetFromSpawn(mapDir));
}
```

(`World2.setSpawnPoint(Point)` exists and is used across the codebase. `buildImportedWorld`, `newMerger` are existing helpers.)

- [ ] **Step 2: Run to verify failure**

Run: `mvn -pl WPCore -am test -Dtest=HytaleWorldMergerTest#coverageCountsExistingRegionsExplainedByTiles+recoverOffsetFromSpawnSubtractsWorldSpawnFromSaveSpawn+recoverOffsetFromSpawnReturnsNullWhenNoSpawnInConfig`
Expected: FAIL to compile (`coverage`, `recoverOffsetFromSpawn` not defined).

- [ ] **Step 3: Add the helper methods to `HytaleWorldMerger`**

Add these imports to `HytaleWorldMerger.java` (if not already present):

```java
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
```

Add these methods (package-private so the same-package test can call them) near the other private helpers, e.g. just after `determineBlockOffset`:

```java
    /** Coverage threshold below which the resolved offset is rejected (abort rather than misplace). */
    private static final double COVERAGE_THRESHOLD = 0.9;

    /**
     * Read the region coordinates present in {@code <worldDir>/chunks} (files named
     * {@code "<x>.<z>.region.bin"}). Used to validate a candidate block offset against the chunks
     * already in the existing map.
     */
    static Set<Point> readExistingRegionCoords(File worldDir) {
        final Set<Point> coords = new HashSet<>();
        final File chunksDir = new File(worldDir, CHUNKS_DIR);
        final File[] files = chunksDir.listFiles((d, n) -> n.endsWith(".region.bin"));
        if (files == null) {
            return coords;
        }
        for (File f : files) {
            final String[] parts = f.getName().split("\\.");
            if (parts.length >= 3) {
                try {
                    coords.add(new Point(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
                } catch (NumberFormatException ignored) {
                    // skip non-coordinate filenames
                }
            }
        }
        return coords;
    }

    /**
     * Recover the offset the existing map was exported with from its spawn point. The export wrote
     * {@code saveSpawn = world.getSpawnPoint() + blockOffset} (HytaleWorldConfigWriter), so
     * {@code blockOffset = saveSpawn - world.getSpawnPoint()}. Returns {@code null} when the world
     * has no spawn point or the existing {@code config.json} has no usable {@code SpawnProvider.SpawnPoint}.
     */
    Point recoverOffsetFromSpawn(File worldDir) {
        final Point wpSpawn = world.getSpawnPoint();
        if (wpSpawn == null) {
            return null;
        }
        final File configFile = new File(worldDir, "config.json");
        if (! configFile.isFile()) {
            return null;
        }
        try {
            final String text = new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8);
            final JsonObject root = JsonParser.parseString(text).getAsJsonObject();
            if (! root.has("SpawnProvider")) {
                return null;
            }
            final JsonObject sp = root.getAsJsonObject("SpawnProvider");
            if (! sp.has("SpawnPoint")) {
                return null;
            }
            final JsonObject pt = sp.getAsJsonObject("SpawnPoint");
            if ((! pt.has("X")) || (! pt.has("Z"))) {
                return null;
            }
            final int saveX = (int) Math.round(pt.get("X").getAsDouble());
            final int saveZ = (int) Math.round(pt.get("Z").getAsDouble());
            return new Point(saveX - wpSpawn.x, saveZ - wpSpawn.y);
        } catch (Exception e) {
            logger.warn("Could not recover offset from spawn in {}: {}", configFile, e.getMessage());
            return null;
        }
    }

    /**
     * Fraction of {@code existingRegions} that are "explained" by some tile in {@code tileCoords}
     * under {@code offset}: i.e. the tile, once offset, falls in that region. A correct offset lands
     * the unchanged tiles back on the regions the export wrote (coverage near 1.0); a drifted offset
     * scores lower. Returns 1.0 for an empty {@code existingRegions} (nothing to validate against).
     */
    static double coverage(Set<Point> tileCoords, Point offset, Set<Point> existingRegions) {
        if (existingRegions.isEmpty()) {
            return 1.0;
        }
        final Set<Point> hit = new HashSet<>();
        for (Point t : tileCoords) {
            final int bx0 = (t.x * 128) + offset.x;
            final int bz0 = (t.y * 128) + offset.y;
            // region = chunk >> 5 = block >> 10. A 128-block tile may straddle a 1024-block boundary.
            final int rx0 = bx0 >> 10, rx1 = (bx0 + 127) >> 10;
            final int rz0 = bz0 >> 10, rz1 = (bz0 + 127) >> 10;
            for (int rx = rx0; rx <= rx1; rx++) {
                for (int rz = rz0; rz <= rz1; rz++) {
                    final Point r = new Point(rx, rz);
                    if (existingRegions.contains(r)) {
                        hit.add(r);
                    }
                }
            }
        }
        return (double) hit.size() / existingRegions.size();
    }
```

- [ ] **Step 4: Run the unit tests**

Run: `mvn -pl WPCore -am test -Dtest=HytaleWorldMergerTest#coverageCountsExistingRegionsExplainedByTiles+recoverOffsetFromSpawnSubtractsWorldSpawnFromSaveSpawn+recoverOffsetFromSpawnReturnsNullWhenNoSpawnInConfig`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/export/HytaleWorldMerger.java \
        WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/export/HytaleWorldMergerTest.java
git commit -m "feat(hytale): spawn-based offset recovery + region-coverage helpers"
```

---

## Task 3: Resolve the offset and wire it into the merge

**Files:**
- Modify: `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/export/HytaleWorldMerger.java`
- Modify: `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/export/HytaleWorldMergerTest.java`

- [ ] **Step 1: Write the failing tests**

Add to `HytaleWorldMergerTest`. The E2E scenario is deterministic: export tiles `{(0,0),(8,0)}` with world spawn `(0,0)` → centering offset `(-512,0)` → existing regions `{(-1,0),(0,0)}` and `config.json` spawn `(-512,0)`. Revamp by adding tile `(100,0)` so `centeringOffset` drifts to `(-6400,0)`. Merging selection `{(0,0)}` must use the recovered/stored `(-512,0)`, landing tile `(0,0)`'s chunks in region `(-1,0)` and **not** in the drifted region `(-7,0)`.

```java
/** A from-scratch world with the given tiles and an explicit spawn at WP (0,0). */
private World2 buildScratchWorldWithSpawn(String name, java.util.Set<Point> tiles) {
    World2 world = new World2(HYTALE, 0, 320);
    world.setName(name);
    world.setCreateGoodiesChest(false);
    world.setSpawnPoint(new Point(0, 0));
    long seed = 11L;
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

@Test
public void resolveUsesSidecarOffsetWhenPresent() throws Exception {
    File mapDir = createExportedHytaleMap("resolve_sidecar");
    // Stored sidecar says the map is at (-512, 0); a recomputed centering would say (0,0).
    HytaleExportMetadata.writeBlockOffset(mapDir, -512, 0);
    HytaleWorldMerger merger = newMerger(mapDir);
    assertEquals(new Point(-512, 0), merger.resolveBlockOffset());
}

@Test
public void resolvePrefersSpawnRecoveryOverDriftedCenteringWhenNoSidecar() throws Exception {
    java.util.Set<Point> exportTiles = new java.util.HashSet<>(java.util.Arrays.asList(
            new Point(0, 0), new Point(8, 0)));
    World2 exportWorld = buildScratchWorldWithSpawn("drift_export", exportTiles);
    File baseDir = tempDir.newFolder("base_drift");
    new HytaleWorldExporter(exportWorld, new WorldExportSettings()).export(baseDir, "drift_export", null, null);
    File mapDir = new File(new File(new File(new File(baseDir, "drift_export"), "universe"), "worlds"), "default");

    // Simulate a legacy map: remove the sidecar this export just wrote.
    assertTrue(new File(mapDir, HytaleExportMetadata.SIDECAR_NAME).delete());

    // Revamp: add a far tile so centeringOffset drifts badly, keep the same spawn (0,0).
    java.util.Set<Point> revampedTiles = new java.util.HashSet<>(exportTiles);
    revampedTiles.add(new Point(100, 0));
    World2 revamped = buildScratchWorldWithSpawn("drift_revamp", revampedTiles);

    HytaleWorldMerger merger = new HytaleWorldMerger(revamped, new WorldExportSettings(), mapDir, HYTALE);
    // Export offset was centeringOffset({(0,0),(8,0)}) = (-512, 0); recovery must reproduce it.
    assertEquals("Resolved offset must match the original export, not the drifted centering",
            new Point(-512, 0), merger.resolveBlockOffset());
}

@Test
public void inPlaceMergeWithDriftedBoundsLandsTileAtOriginalRegion() throws Exception {
    java.util.Set<Point> exportTiles = new java.util.HashSet<>(java.util.Arrays.asList(
            new Point(0, 0), new Point(8, 0)));
    World2 exportWorld = buildScratchWorldWithSpawn("place_export", exportTiles);
    File baseDir = tempDir.newFolder("base_place");
    new HytaleWorldExporter(exportWorld, new WorldExportSettings()).export(baseDir, "place_export", null, null);
    File mapDir = new File(new File(new File(new File(baseDir, "place_export"), "universe"), "worlds"), "default");
    assertTrue(new File(mapDir, HytaleExportMetadata.SIDECAR_NAME).delete()); // legacy map

    // Under offset (-512,0): tile (0,0) -> blocks -512..-385 -> chunks -16..-13 -> region (-1,0).
    try (HytaleChunkStore store = new HytaleChunkStore(innerWorld(mapDir), 0, 320)) {
        assertNotNull("Setup: tile (0,0) chunk (-16,0) exists in region (-1,0)", store.getChunk(-16, 0));
    }

    java.util.Set<Point> revampedTiles = new java.util.HashSet<>(exportTiles);
    revampedTiles.add(new Point(100, 0));
    World2 revamped = buildScratchWorldWithSpawn("place_revamp", revampedTiles);
    WorldExportSettings settings = new WorldExportSettings(
            java.util.Collections.singleton(DIM_NORMAL),
            java.util.Collections.singleton(new Point(0, 0)),
            null);
    HytaleWorldMerger merger = new HytaleWorldMerger(revamped, settings, mapDir, HYTALE);
    merger.merge(new File(tempDir.getRoot(), "place_bkp"), null);

    try (HytaleChunkStore store = new HytaleChunkStore(innerWorld(mapDir), 0, 320)) {
        // Correct: still in region (-1,0). Drifted offset (-6400,0) would have put tile (0,0) at
        // block -6400 -> chunk -200 (region -7,0).
        assertNotNull("Selected tile (0,0) must remain in region (-1,0) after merge", store.getChunk(-16, 0));
        assertNull("Tile must NOT land at the drifted chunk (-200,0) / region (-7,0)", store.getChunk(-200, 0));
    }
    // And the merge should have written a sidecar so the next merge is direct.
    assertEquals(new Point(-512, 0), HytaleExportMetadata.readBlockOffset(mapDir));
}

@Test
public void resolveAbortsWhenNoOffsetExplainsTheExistingChunks() throws Exception {
    // NOTE: the centering heuristic always re-centers the current tiles around the origin, so a
    // single tile would land on region (0,0) and accidentally match a centered export. To force a
    // genuine no-match we replace the existing chunks with one region FAR from the origin (50,50)
    // and remove any recoverable spawn — neither the (0,0) heuristic nor recovery can explain it.
    File mapDir = createExportedHytaleMap("abort_case");
    assertTrue(new File(mapDir, HytaleExportMetadata.SIDECAR_NAME).delete());
    File chunksDir = new File(mapDir, "chunks");
    for (File f : chunksDir.listFiles((d, n) -> n.endsWith(".region.bin"))) {
        assertTrue(f.delete());
    }
    assertTrue("create a region file far from origin", new File(chunksDir, "50.50.region.bin").createNewFile());
    java.nio.file.Files.write(new File(mapDir, "config.json").toPath(),
            "{ }".getBytes(java.nio.charset.StandardCharsets.UTF_8));   // no SpawnProvider

    World2 world = buildImportedWorld(mapDir);   // tile (0,0); importedFrom set -> heuristic offset (0,0)
    world.setSpawnPoint(null);                   // no spawn anchor -> no spawn candidate
    HytaleWorldMerger merger = new HytaleWorldMerger(world, new WorldExportSettings(), mapDir, HYTALE);
    try {
        merger.resolveBlockOffset();             // heuristic (0,0) maps tile (0,0) to region (0,0), not (50,50)
        fail("Expected InvalidMapException when no candidate offset explains the existing chunks");
    } catch (InvalidMapException expected) {
        // ok
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `mvn -pl WPCore -am test -Dtest=HytaleWorldMergerTest#resolveUsesSidecarOffsetWhenPresent`
Expected: FAIL to compile (`resolveBlockOffset` not defined).

- [ ] **Step 3: Add `resolvedBlockOffset` field + `heuristicOffset()` + `resolveBlockOffset()` and rewrite `determineBlockOffset`**

In `HytaleWorldMerger.java`, add the field near the other transient run state (next to `aborted`):

```java
    /** Block offset resolved once per merge (before any rename) and reused for every chunk. */
    private Point resolvedBlockOffset;
```

Add these methods (next to the Task 2 helpers):

```java
    /** Today's offset behavior: (0,0) for imported maps, else center the current tile set. */
    private Point heuristicOffset() {
        if (world.getImportedFrom() != null) {
            return new Point(0, 0);
        }
        return HytaleWorldExporter.centeringOffset(world.getDimension(NORMAL_DETAIL).getTileCoords());
    }

    /**
     * Resolve the block offset to write the merge with, reading the existing map at {@link #mapDir}.
     * MUST be called before any backup rename (while mapDir still holds the chunks and config.json).
     * Order: stored sidecar (authoritative) → best of {spawn recovery, centering heuristic} validated
     * by region coverage → abort if nothing clears {@link #COVERAGE_THRESHOLD}.
     */
    Point resolveBlockOffset() {
        final Point sidecar = HytaleExportMetadata.readBlockOffset(mapDir);
        if (sidecar != null) {
            logger.info("Using stored export offset {} from sidecar in {}", sidecar, mapDir);
            return sidecar;
        }
        final Set<Point> existingRegions = readExistingRegionCoords(mapDir);
        if (existingRegions.isEmpty()) {
            return heuristicOffset();   // nothing to align to (degenerate / empty map)
        }
        final Set<Point> allTiles = world.getDimension(NORMAL_DETAIL).getTileCoords();
        final Point oSpawn = recoverOffsetFromSpawn(mapDir);
        final Point oHeuristic = heuristicOffset();
        // Evaluate spawn recovery first so it wins ties (it is exact when the spawn is unchanged).
        final java.util.List<Point> candidates = (oSpawn != null)
                ? java.util.Arrays.asList(oSpawn, oHeuristic)
                : java.util.Collections.singletonList(oHeuristic);
        Point best = null;
        double bestCoverage = -1.0;
        for (Point candidate : candidates) {
            final double cov = coverage(allTiles, candidate, existingRegions);
            if (cov > bestCoverage) {
                bestCoverage = cov;
                best = candidate;
            }
        }
        if (bestCoverage >= COVERAGE_THRESHOLD) {
            logger.info("Resolved export offset {} (matches {}% of existing regions) for merge into {}",
                    best, Math.round(bestCoverage * 100), mapDir);
            return best;
        }
        throw new InvalidMapException("Could not determine the original block alignment of the existing "
                + "Hytale map (best match " + Math.round(bestCoverage * 100) + "% of existing regions). "
                + "Aborting so tiles are not misplaced. This map was exported before TalePainter stored its "
                + "export offset, and spawn-based recovery did not line up — re-check the world's spawn point, "
                + "or do a full export to reset alignment.");
    }
```

Now replace the existing `determineBlockOffset` override. Find:

```java
    @Override
    protected Point determineBlockOffset(Dimension dimension, Set<Point> exportedTileCoords) {
        if (world.getImportedFrom() != null) {
            return new Point(0, 0);
        }
        return HytaleWorldExporter.centeringOffset(dimension.getTileCoords());
    }
```

Replace with:

```java
    @Override
    protected Point determineBlockOffset(Dimension dimension, Set<Point> exportedTileCoords) {
        // merge() resolves the offset once (before any rename) and caches it here. When that hasn't
        // run (e.g. determineBlockOffset called directly in a unit test), fall back to the heuristic.
        return (resolvedBlockOffset != null) ? resolvedBlockOffset : heuristicOffset();
    }
```

- [ ] **Step 4: Resolve the offset early in `merge()`**

In `HytaleWorldMerger.merge`, find:

```java
        performSanityChecks();

        // Fast path: a tile selection means "apply only these tiles to the already-exported
```

Insert the resolution between them:

```java
        performSanityChecks();

        // Resolve the block offset ONCE, now, while mapDir still holds the existing chunks and
        // config.json (the full-merge path renames mapDir to a backup further down). Both the
        // in-place and full-merge paths reach determineBlockOffset, which returns this value.
        // Throws InvalidMapException (aborting before any modification) if alignment can't be found.
        this.resolvedBlockOffset = resolveBlockOffset();

        // Fast path: a tile selection means "apply only these tiles to the already-exported
```

- [ ] **Step 5: Run the new tests**

Run: `mvn -pl WPCore -am test -Dtest=HytaleWorldMergerTest#resolveUsesSidecarOffsetWhenPresent+resolvePrefersSpawnRecoveryOverDriftedCenteringWhenNoSidecar+inPlaceMergeWithDriftedBoundsLandsTileAtOriginalRegion+resolveAbortsWhenNoOffsetExplainsTheExistingChunks`
Expected: PASS (4 tests).

- [ ] **Step 6: Run the whole merger + metadata classes (regression)**

Run: `mvn -pl WPCore -am test -Dtest=HytaleWorldMergerTest,HytaleExportMetadataTest`
Expected: ALL pass. The existing `mergeReproducesExportCenteringOffsetForPaintedFromScratchWorld` and `mergeKeepsZeroOffsetForImportedMap` (direct `determineBlockOffset` calls) still pass via the `resolvedBlockOffset == null` → `heuristicOffset()` fallback. Existing in-place/partial-selection tests still pass (their worlds have unchanged bounds, so the resolved offset equals the old centering, and a sidecar — written by their `createExportedHytaleMap` export — makes resolution authoritative).

- [ ] **Step 7: Commit**

```bash
git add WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/export/HytaleWorldMerger.java \
        WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/export/HytaleWorldMergerTest.java
git commit -m "feat(hytale): resolve merge offset (sidecar > spawn recovery > heuristic, else abort)"
```

---

## Task 4: Full verification

**Files:** none (verification only).

- [ ] **Step 1: Run the full Hytale export/merge test classes**

Run: `mvn -pl WPCore -am test -Dtest=HytaleWorldMergerTest,HytaleExportMetadataTest,HytaleWorldExporterLayerOrderingTest`
Expected: ALL pass.

- [ ] **Step 2: Confirm the GUI module still builds** (the merger lives in WPCore, but the dialog references the merger)

Run: `mvn -DskipTests=true -pl WPGUI -am install`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Manual verification (verify skill), on Calandor**

Open the existing Calandor merge in TalePainter, select a few revamped tiles, merge in place. Confirm: (a) the merge does **not** abort (spawn recovery validates), (b) in Hytale the selected tiles now appear in the correct location aligned with the surrounding map, and (c) a `.talepainter-export.json` now exists in the save's `universe/worlds/default/`. If it **does** abort, that means the spawn couldn't be recovered — report back with the message and we'll derive the offset another way.

---

## Self-review

- **Spec coverage:** sidecar storage (Task 1) ✓; spawn recovery (Task 2) ✓; region coverage validation + candidate selection + abort (Tasks 2–3) ✓; resolution order sidecar→spawn→heuristic→abort wired into both merge paths via `resolveBlockOffset` + `determineBlockOffset` (Task 3) ✓; sidecar written by every export path (Task 1, in `exportDimension`) ✓; backward-compat for direct `determineBlockOffset` tests via `heuristicOffset()` fallback (Task 3) ✓; tests 1–8 from the spec all present.
- **Placeholder scan:** none — all steps have concrete code and exact `mvn` commands.
- **Type/name consistency:** `HytaleExportMetadata.SIDECAR_NAME` / `writeBlockOffset` / `readBlockOffset`; `resolveBlockOffset` / `recoverOffsetFromSpawn` / `readExistingRegionCoords` / `coverage` / `heuristicOffset` / `resolvedBlockOffset` / `COVERAGE_THRESHOLD` used identically across tasks. `CHUNKS_DIR` is the existing field in `HytaleWorldMerger`. `centeringOffset` is the existing static on `HytaleWorldExporter`.
- **Numeric check (E2E):** export `{(0,0),(8,0)}` → min 0 max 8 → centerTile 4 → offset `(-512,0)`; tile (0,0) → block `-512..-385` → chunk `-16..-13` → region `-1`; config spawn `(0,0)+(-512,0) = (-512,0)`; recovery `(-512,0) - (0,0) = (-512,0)` ✓. Revamp add (100,0) → max 100 → centerTile 50 → drifted `(-6400,0)`; tile (0,0) under drift → block `-6400..` → chunk `-200..` → region `-7` ✓ (asserted absent at chunk -216).
```
