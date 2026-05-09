# TP-53 Follow-up — Underwater Bundled Prefabs and Terrain Plants Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make bundled Hytale prefabs and terrain plants survive the post-export "seal-above-terrain" pass when painted on flooded tiles, fixing the residual half of TP-53 that commit `6503a06a` did not cover.

**Architecture:** Two surgical one-line additions: a `chunk.setSealProtected(..., true)` call placed immediately after each `chunk.setHytaleBlock(...)` call in (1) `HytalePrefabPaster.paste()` (every prefab block) and (2) the plant-export inner loop of `HytaleWorldExporter` (every plant block). Each change is paired with a regression test in the existing project test style: a unit-level test for the prefab paster and a hybrid-level test for the plant export.

**Tech Stack:** Java 17, Maven (from `WorldPainter/`), JUnit 4. Tests follow existing patterns in `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/`:
- `HytaleSealAboveTerrainColumnTest` — direct in-memory `HytaleChunk` tests around `sealAboveTerrainColumn`.
- `Tp60PlantSubstrateTest` — end-to-end export + region readback regression tests.
- `HytalePrefabTerrainCoexistenceTest` — in-memory chunk tests that simulate paste behavior.

**Reference spec:** [`docs/superpowers/specs/2026-05-09-tp53-followup-hytale-underwater-prefabs-plants-design.md`](../specs/2026-05-09-tp53-followup-hytale-underwater-prefabs-plants-design.md).

---

## File Structure

| File | Role | Status |
|---|---|---|
| `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytalePrefabPaster.java` | Add `chunk.setSealProtected(bx, by, bz, true)` after the existing `chunk.setHytaleBlock(...)` call inside the per-block loop. | **Modify** (1 line added) |
| `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleWorldExporter.java` | Add `chunk.setSealProtected(localX, height + 1, localZ, true)` after the existing `chunk.setHytaleBlock(localX, height + 1, localZ, plantBlock)` call in the plant-export inner loop. | **Modify** (1 line added) |
| `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/Tp53UnderwaterPrefabPasterTest.java` | New JUnit test class: writes a fixture prefab JSON to a temp folder, calls `HytalePrefabPaster.paste()`, runs `HytaleWorldExporter.sealAboveTerrainColumn`, asserts placed prefab blocks survive in flooded columns. | **Create** |
| `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/Tp53UnderwaterPlantTest.java` | New JUnit test class: builds a small Hytale world with terrain below water level, paints a plant via `HytalePlantsLayer.setPlantIndex`, exports it, reads the region file back, asserts the plant block survives at `terrainHeight + 1`. | **Create** |

No other files are touched. No new public APIs. No UI changes.

---

## Conventions Used in This Plan

- **Project root:** `C:\Users\Sotirios\Desktop\WorldPainter\.claude\worktrees\wonderful-montalcini-c7e178` (the worktree path; all relative paths below resolve from here).
- **Maven build directory:** `WorldPainter/` (the directory containing `pom.xml`). Run all `mvn` commands from there: `cd WorldPainter && mvn ...`. Or equivalently, pass `-f WorldPainter/pom.xml` from the project root.
- **Run a single test class:**
  ```
  cd WorldPainter && mvn -pl WPCore -am test -Dtest=<TestClassName>
  ```
- **Compile-only sanity check:**
  ```
  cd WorldPainter && mvn -pl WPCore -am compile -DskipTests=true
  ```
- **Run the existing Hytale-test regression suite** (used at the end to confirm no regressions):
  ```
  cd WorldPainter && mvn -pl WPCore -am test -Dtest='org.pepsoft.worldpainter.hytale.*Test'
  ```
- **Commit discipline:** Each "Commit" step stages exactly the files modified in that task by listing them by name. Never `git add -A` or `git add .`. If pre-existing uncommitted changes are present in the worktree from prior work, do **not** include them in these commits.
- **Memory note:** After the implementation lands and the developer is ready to mark TP-53 resolved, the YouTrack stage must be set to **Review** (not Done). See Task 3.

---

## Task 1 — Fix HytalePrefabPaster (failing test → fix → passing test → regression coverage)

This task addresses the bundled-Hytale-prefab path: blocks pasted by `HytalePrefabPaster.paste()` get wiped by the seal pass when painted on flooded tiles.

### Files

- Modify: `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytalePrefabPaster.java` (line 79, inside the `for (PrefabBlock block : data.blocks)` loop)
- Create: `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/Tp53UnderwaterPrefabPasterTest.java`

### Steps

- [ ] **Step 1.1 — Orient: read the two reference test files**

Read these two files end-to-end (no edits) to internalize the patterns this task copies from. The new test file follows their conventions exactly:
- `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/HytaleSealAboveTerrainColumnTest.java` (constructs a `HytaleChunk` in memory; calls `HytaleWorldExporter.sealAboveTerrainColumn`; asserts on `getHytaleBlock` and `getFluidId`).
- `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/HytalePrefabTerrainCoexistenceTest.java` (uses `HytaleChunk` directly; package-private access to seal pass and chunk methods).

Also re-read the production file you'll be editing:
- `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytalePrefabPaster.java` (lines 58-102 are the `paste` method).

- [ ] **Step 1.2 — Write the failing test class**

Create `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/Tp53UnderwaterPrefabPasterTest.java` with the contents below.

```java
package org.pepsoft.worldpainter.hytale;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

import static org.junit.Assert.*;

/**
 * Regression test for TP-53 follow-up: blocks pasted by {@link HytalePrefabPaster}
 * (i.e. bundled Hytale prefabs from the catalog) must be marked seal-protected so
 * the post-export seal pass does NOT wipe them when they land in a flooded column.
 *
 * <p>Prior to the fix, {@code paste()} called {@code chunk.setHytaleBlock(...)}
 * with no follow-up {@code setSealProtected(...)} call, so each pasted block
 * defaulted to {@code SUPPORT_NONE} with {@code isSealProtected() == false},
 * and {@link HytaleWorldExporter#sealAboveTerrainColumn} cleared it.
 *
 * <p>The fix adds {@code chunk.setSealProtected(bx, by, bz, true)} immediately
 * after the per-block {@code setHytaleBlock} call in {@link HytalePrefabPaster#paste}.
 */
public class Tp53UnderwaterPrefabPasterTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    private static final int TERRAIN_HEIGHT = 50;
    private static final int WATER_LEVEL = 64;
    private static final String FLUID_ID = HytaleBlockMapping.HY_WATER;
    private static final String SEAWEED_ID = "Plant_Seaweed_Arid_Stack";

    /**
     * Single-block prefab pasted at terrain+1 in a flooded column must
     * survive the seal pass. Without the fix this test fails because the
     * seaweed block has SUPPORT_NONE + !isSealProtected, so the seal pass
     * clears it.
     */
    @Test
    public void singleBlockPrefabSurvivesSealPassWhenSubmerged() throws Exception {
        File assetsDir = writeFixturePrefab("Prefabs/Test/SingleSeaweed.prefab.json",
                /*anchorX*/ 0, /*anchorY*/ 0, /*anchorZ*/ 0,
                new FixtureBlock(0, 0, 0, SEAWEED_ID, 0));

        HytalePrefabPaster paster = new HytalePrefabPaster(assetsDir);
        HytaleChunk chunk = new HytaleChunk(0, 0, 0, 320);

        boolean pasted = paster.paste(chunk, /*anchorX*/ 5, /*anchorY*/ TERRAIN_HEIGHT + 1, /*anchorZ*/ 5,
                /*worldX*/ 5, /*worldZ*/ 5, "Prefabs/Test/SingleSeaweed.prefab.json");
        assertTrue("Fixture prefab must paste successfully", pasted);

        // Sanity: the block IS placed before the seal pass runs.
        HytaleBlock placed = chunk.getHytaleBlock(5, TERRAIN_HEIGHT + 1, 5);
        assertNotNull("Prefab block should be placed before seal pass", placed);
        assertEquals(SEAWEED_ID, placed.id);

        // Run the seal pass over the flooded column.
        HytaleWorldExporter.sealAboveTerrainColumn(
                chunk, 5, 5, TERRAIN_HEIGHT, WATER_LEVEL, FLUID_ID);

        HytaleBlock survived = chunk.getHytaleBlock(5, TERRAIN_HEIGHT + 1, 5);
        assertNotNull("Submerged prefab block must survive the seal pass", survived);
        assertEquals("Submerged prefab block must still be the seaweed block",
                SEAWEED_ID, survived.id);

        // The fluid layer must still be filled around the surviving block.
        int fluidId = chunk.getSections()[(TERRAIN_HEIGHT + 1) >> 5]
                .getFluidId(5, (TERRAIN_HEIGHT + 1) & 31, 5);
        assertTrue("Fluid must be present around the surviving prefab block", fluidId > 0);
    }

    /**
     * Multi-block prefab spanning the water surface: blocks below
     * {@code waterLevel} are inside the seal range; blocks above are not.
     * All must survive — submerged blocks because they are seal-protected,
     * above-water blocks because they are outside the seal pass range.
     */
    @Test
    public void tallPrefabSpanningWaterSurfaceSurvivesEntirely() throws Exception {
        // 10-block-tall stack at the same column, anchored at terrain+1.
        FixtureBlock[] tallStack = new FixtureBlock[10];
        for (int i = 0; i < 10; i++) {
            tallStack[i] = new FixtureBlock(0, i, 0, SEAWEED_ID, 0);
        }
        File assetsDir = writeFixturePrefab("Prefabs/Test/TallSeaweed.prefab.json",
                0, 0, 0, tallStack);

        HytalePrefabPaster paster = new HytalePrefabPaster(assetsDir);
        HytaleChunk chunk = new HytaleChunk(0, 0, 0, 320);

        // anchorY = TERRAIN_HEIGHT + 1 = 51. Plant occupies y=51..60. Water
        // level is 64, so y=51..64 is in the seal range, y=65+ is not.
        boolean pasted = paster.paste(chunk, 5, TERRAIN_HEIGHT + 1, 5,
                5, 5, "Prefabs/Test/TallSeaweed.prefab.json");
        assertTrue(pasted);

        HytaleWorldExporter.sealAboveTerrainColumn(
                chunk, 5, 5, TERRAIN_HEIGHT, WATER_LEVEL, FLUID_ID);

        // Every plant block must remain. y=51..60 (10 blocks).
        for (int dy = 0; dy < 10; dy++) {
            int y = TERRAIN_HEIGHT + 1 + dy;
            HytaleBlock survived = chunk.getHytaleBlock(5, y, 5);
            assertNotNull("Block at y=" + y + " must survive seal pass", survived);
            assertEquals("Block at y=" + y + " must still be seaweed",
                    SEAWEED_ID, survived.id);
        }
    }

    /**
     * Regression: prefab pasted on a non-flooded column (water level <= terrain
     * height) must continue to work as it does today. The seal pass has a
     * zero-length iteration on dry columns so this is a no-op, but we verify
     * explicitly.
     */
    @Test
    public void prefabOnDryColumnUnaffectedByFix() throws Exception {
        File assetsDir = writeFixturePrefab("Prefabs/Test/DryBlock.prefab.json",
                0, 0, 0, new FixtureBlock(0, 0, 0, SEAWEED_ID, 0));

        HytalePrefabPaster paster = new HytalePrefabPaster(assetsDir);
        HytaleChunk chunk = new HytaleChunk(0, 0, 0, 320);

        paster.paste(chunk, 5, TERRAIN_HEIGHT + 1, 5, 5, 5, "Prefabs/Test/DryBlock.prefab.json");

        // No flooding: waterLevel == terrainHeight, so seal pass loop is empty.
        HytaleWorldExporter.sealAboveTerrainColumn(
                chunk, 5, 5, TERRAIN_HEIGHT, /*waterLevel*/ TERRAIN_HEIGHT, FLUID_ID);

        HytaleBlock placed = chunk.getHytaleBlock(5, TERRAIN_HEIGHT + 1, 5);
        assertNotNull(placed);
        assertEquals(SEAWEED_ID, placed.id);
    }

    // ── Test helpers ──────────────────────────────────────────────────────

    /**
     * Writes a minimal prefab JSON fixture under {@code <tempDir>/Server/<relativePath>}
     * and returns the assets directory ({@code <tempDir>}) for {@link HytalePrefabPaster}'s
     * constructor. The paster expects assetsDir/Server/ as its prefab root.
     */
    private File writeFixturePrefab(String relativePath, int anchorX, int anchorY, int anchorZ,
                                    FixtureBlock... blocks) throws IOException {
        File assetsDir = tempDir.newFolder("HytaleAssets_" + System.nanoTime());
        File serverDir = new File(assetsDir, "Server");
        File prefabFile = new File(serverDir, relativePath.replace('/', File.separatorChar));
        if (! prefabFile.getParentFile().mkdirs() && ! prefabFile.getParentFile().isDirectory()) {
            throw new IOException("Failed to create fixture parent dir: " + prefabFile.getParentFile());
        }

        StringBuilder json = new StringBuilder();
        json.append("{\"anchorX\":").append(anchorX)
            .append(",\"anchorY\":").append(anchorY)
            .append(",\"anchorZ\":").append(anchorZ)
            .append(",\"blocks\":[");
        for (int i = 0; i < blocks.length; i++) {
            if (i > 0) json.append(',');
            FixtureBlock b = blocks[i];
            json.append("{\"x\":").append(b.x)
                .append(",\"y\":").append(b.y)
                .append(",\"z\":").append(b.z)
                .append(",\"name\":\"").append(b.name)
                .append("\",\"rotation\":").append(b.rotation)
                .append('}');
        }
        json.append("],\"fluids\":[]}");

        try (Writer w = new FileWriter(prefabFile)) {
            w.write(json.toString());
        }
        return assetsDir;
    }

    private static final class FixtureBlock {
        final int x, y, z;
        final String name;
        final int rotation;

        FixtureBlock(int x, int y, int z, String name, int rotation) {
            this.x = x; this.y = y; this.z = z; this.name = name; this.rotation = rotation;
        }
    }
}
```

- [ ] **Step 1.3 — Run the new test and confirm the first case fails for the right reason**

Run:
```
cd WorldPainter && mvn -pl WPCore -am test -Dtest=Tp53UnderwaterPrefabPasterTest
```

**Expected before the fix:**
- `singleBlockPrefabSurvivesSealPassWhenSubmerged` — **FAIL** at the assertion `assertNotNull("Submerged prefab block must survive the seal pass", survived)` (the seal pass cleared the block).
- `tallPrefabSpanningWaterSurfaceSurvivesEntirely` — **FAIL** for the same reason on submerged blocks.
- `prefabOnDryColumnUnaffectedByFix` — **PASS** (the seal pass has a zero-length loop when water level ≤ terrain height).

If `prefabOnDryColumnUnaffectedByFix` fails, stop and investigate — the fixture or test plumbing is wrong, not the production code.

- [ ] **Step 1.4 — Apply the fix to `HytalePrefabPaster.paste()`**

In `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytalePrefabPaster.java`, locate the per-block placement loop (lines 65-80) and add a single `setSealProtected` call immediately after the `setHytaleBlock` call.

**Old (lines 78-79):**
```java
            HytaleBlock hBlock = HytaleBlock.of(block.blockName, block.rotation);
            chunk.setHytaleBlock(bx, by, bz, hBlock);
        }
```

**New (lines 78-80, with one-line addition between the two existing calls):**
```java
            HytaleBlock hBlock = HytaleBlock.of(block.blockName, block.rotation);
            chunk.setHytaleBlock(bx, by, bz, hBlock);
            chunk.setSealProtected(bx, by, bz, true);
        }
```

Do **not** modify the fluid loop (lines 83-99). The `setFluid` call there writes into the separate fluid layer which is the seal pass's *output*, not its input — adding seal-protection there would have no effect.

Do **not** add `setDecorative()`. The spec rejects this explicitly: decorative blocks bypass the runtime gathering interaction (the player gets the block itself instead of its configured drops — see comment at `HytaleWorldExporter.java:1610-1616`), which would change runtime drop behavior of every block in every prefab.

- [ ] **Step 1.5 — Run the new test and confirm all three cases pass**

Run:
```
cd WorldPainter && mvn -pl WPCore -am test -Dtest=Tp53UnderwaterPrefabPasterTest
```

**Expected after the fix:** all three tests PASS.

- [ ] **Step 1.6 — Run the existing prefab test suite to confirm no regressions**

Run:
```
cd WorldPainter && mvn -pl WPCore -am test -Dtest='HytalePrefab*Test,HytaleSeal*Test,HytaleSpecificPrefab*Test'
```

**Expected:** all existing prefab and seal-pass tests still pass. If any fail, do not proceed — diagnose first. The most likely failure mode would be that some existing test relied on prefab blocks NOT being seal-protected (improbable but worth ruling out).

- [ ] **Step 1.7 — Commit**

```
git add WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytalePrefabPaster.java WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/Tp53UnderwaterPrefabPasterTest.java
git commit -m "fix(hytale): seal-protect bundled prefab blocks (TP-53 follow-up)

Bundled Hytale prefabs pasted by HytalePrefabPaster were being wiped by
the post-export seal-above-terrain pass when painted on flooded columns,
because pasted blocks defaulted to SUPPORT_NONE with isSealProtected
== false. Mark every pasted block seal-protected so the seal pass leaves
them alone; Hytale's separate fluid layer continues to fill water around
the block.

Does not touch decorative state — that flag changes the runtime
gathering interaction (player gets block itself instead of configured
drops) and is out of scope for the export-time seal-pass concern."
```

---

## Task 2 — Fix HytaleWorldExporter plant-export loop (failing test → fix → passing test)

This task addresses the terrain-plant path: plants placed via `HytalePlantsLayer` get wiped by the seal pass when their column is flooded, regardless of the user's `plantsPhysicsExempt` setting.

### Files

- Modify: `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleWorldExporter.java` (line 1624, inside the plant-export loop at lines 1617-1630)
- Create: `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/Tp53UnderwaterPlantTest.java`

### Steps

- [ ] **Step 2.1 — Orient: read the reference plant test**

Read end-to-end (no edits):
- `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/Tp60PlantSubstrateTest.java` — this is the closest precedent. Build a `World2` with HYTALE platform, paint a plant via `HytalePlantsLayer.setPlantIndex(tile, x, z, layerIndex)`, run `HytaleWorldExporter.export(...)`, walk every region file with `HytaleRegionFile`, scan chunks for the painted block.

Also re-read the production lines you'll be modifying:
- `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleWorldExporter.java` lines 1617-1630.

- [ ] **Step 2.2 — Write the failing test class**

Create `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/Tp53UnderwaterPlantTest.java`:

```java
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

import static org.junit.Assert.*;
import static org.pepsoft.worldpainter.Constants.DIM_NORMAL;
import static org.pepsoft.worldpainter.DefaultPlugin.HYTALE;

/**
 * Regression test for TP-53 follow-up: a plant painted on a flooded column
 * via {@link HytalePlantsLayer} must survive the post-export seal pass.
 *
 * <p>Prior to the fix, the plant-export inner loop in
 * {@link HytaleWorldExporter} called {@code chunk.setHytaleBlock} with no
 * unconditional {@code setSealProtected} call. When the column was flooded
 * (water level above terrain height), the seal pass cleared the plant.
 *
 * <p>The fix adds an unconditional {@code chunk.setSealProtected(localX,
 * height + 1, localZ, true)} call after the existing {@code setHytaleBlock}
 * placement, independent of the {@code plantsPhysicsExempt} flag (which
 * gates a different concern: runtime physics + gathering interaction).
 */
public class Tp53UnderwaterPlantTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    private static final int TERRAIN_HEIGHT = 50;
    private static final int WATER_LEVEL = 64;
    private static final int PLANT_TILE_X = 5;
    private static final int PLANT_TILE_Z = 7;

    @Test
    public void plantPaintedOnFloodedColumnSurvivesExportSealPass() throws Exception {
        World2 world = buildWorldWithPlantOnFloodedTile();

        File exportBaseDir = tempDir.newFolder("tp53_underwater_plant_export");
        new HytaleWorldExporter(world, new WorldExportSettings())
                .export(exportBaseDir, "Tp53UnderwaterPlant", null, null);

        int bushCount = countPlantBushBlocksAt(exportBaseDir, "Tp53UnderwaterPlant",
                PLANT_TILE_X, TERRAIN_HEIGHT + 1, PLANT_TILE_Z);

        assertTrue("Plant_Bush at terrain+1 must survive the seal pass on a flooded column "
                        + "(found " + bushCount + " matching blocks)",
                bushCount >= 1);
    }

    private World2 buildWorldWithPlantOnFloodedTile() {
        World2 world = new World2(HYTALE, 0, 320);
        world.setName("Tp53UnderwaterPlant");
        world.setCreateGoodiesChest(false);

        long seed = 42L;
        // Flat tile factory: terrain at TERRAIN_HEIGHT (50), water at WATER_LEVEL (64).
        // Result: every column is flooded — water is above terrain everywhere.
        TileFactory tileFactory = TileFactoryFactory.createFlatTileFactory(
                seed, Terrain.GRASS, 0, 320, TERRAIN_HEIGHT, WATER_LEVEL, false, false);
        Dimension.Anchor anchor = new Dimension.Anchor(DIM_NORMAL, Dimension.Role.DETAIL, false, 0);
        Dimension dim = new Dimension(world, "Surface", seed, tileFactory, anchor);
        dim.setEventsInhibited(true);

        Tile tile = tileFactory.createTile(0, 0);
        for (int x = 0; x < 128; x++) {
            for (int z = 0; z < 128; z++) {
                tile.setHeight(x, z, TERRAIN_HEIGHT);
                tile.setWaterLevel(x, z, WATER_LEVEL);
                tile.setTerrain(x, z, Terrain.GRASS);
                HytaleTerrainLayer.setTerrainIndex(tile, x, z, HytaleTerrain.STONE.getLayerIndex());
            }
        }
        // Paint a Bush plant at one pixel.
        HytalePlantsLayer.setPlantIndex(tile, PLANT_TILE_X, PLANT_TILE_Z,
                HytaleTerrain.BUSH.getLayerIndex());
        dim.addTile(tile);
        dim.setEventsInhibited(false);
        world.addDimension(dim);
        return world;
    }

    /**
     * Walks every region file in the exported world and counts how many
     * Plant_Bush blocks sit at the given column (worldX, worldY, worldZ),
     * where worldX/worldZ are the tile-local pixel coordinates of tile (0,0)
     * and worldY is the absolute world Y. Returns the count so the assertion
     * can produce a useful message on failure.
     */
    private int countPlantBushBlocksAt(File exportBaseDir, String worldName,
                                       int worldX, int worldY, int worldZ) throws Exception {
        File chunksDir = new File(new File(new File(new File(exportBaseDir, worldName), "universe"),
                "worlds"), "default/chunks");
        File[] regionFiles = chunksDir.listFiles((d, n) -> n.endsWith(".region.bin"));
        assertNotNull("Export should produce a chunks directory", regionFiles);
        assertTrue("Export should produce at least one region file", regionFiles.length > 0);

        int matchCount = 0;
        for (File rfile : regionFiles) {
            HytaleRegionFile rf = new HytaleRegionFile(rfile.toPath());
            try {
                rf.open();
                for (int cx = 0; cx < HytaleChunk.CHUNK_SIZE; cx++) {
                    for (int cz = 0; cz < HytaleChunk.CHUNK_SIZE; cz++) {
                        if (! rf.hasChunk(cx, cz)) continue;
                        HytaleChunk chunk = rf.readChunk(cx, cz, 0, HytaleChunk.DEFAULT_MAX_HEIGHT);
                        if (chunk == null) continue;
                        for (int x = 0; x < HytaleChunk.CHUNK_SIZE; x++) {
                            for (int z = 0; z < HytaleChunk.CHUNK_SIZE; z++) {
                                HytaleBlock b = chunk.getHytaleBlock(x, worldY, z);
                                if (b != null && ! b.isEmpty() && "Plant_Bush".equals(b.id)) {
                                    matchCount++;
                                }
                            }
                        }
                    }
                }
            } finally {
                rf.close();
            }
        }
        return matchCount;
    }
}
```

**Note on the `setWaterLevel` call:** the per-pixel `tile.setWaterLevel(x, z, WATER_LEVEL)` is included for safety so the water level is unambiguous on every painted tile. If `createFlatTileFactory`'s 6th argument already establishes the water level for every column (verify by reading `TileFactoryFactory`), the per-pixel call is a redundant but harmless re-assertion. If the factory does not bake water level per-tile, the per-pixel call is required.

- [ ] **Step 2.3 — Run the new test and confirm it fails**

Run:
```
cd WorldPainter && mvn -pl WPCore -am test -Dtest=Tp53UnderwaterPlantTest
```

**Expected before the fix:** `plantPaintedOnFloodedColumnSurvivesExportSealPass` — **FAIL** at the assertion that `bushCount >= 1`. The plant block was placed at `(5, 51, 7)` during the chunk-fill loop, then cleared by the seal pass at lines 1227-1238 because the block had `SUPPORT_NONE` and `isSealProtected() == false`.

If the test fails for a different reason (e.g. `assertNotNull("Export should produce a chunks directory", regionFiles)` or build error), stop and investigate the test plumbing — it's not the production bug.

- [ ] **Step 2.4 — Apply the fix to the plant-export loop**

In `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleWorldExporter.java`, locate the plant-export loop at lines 1617-1630 and add a single `setSealProtected` call immediately after the `setHytaleBlock` call.

**Old (lines 1617-1630, current state):**
```java
                int plantIndex = HytalePlantsLayer.getPlantIndex(tile, tileLocalX, tileLocalZ);
                if (plantIndex > 0) {
                    HytaleTerrain plantTerrain = HytaleTerrain.getByLayerIndex(plantIndex);
                    if (plantTerrain != null) {
                        HytaleBlock plantBlock = plantTerrain.getBlock(seed, worldX, worldZ, 0);
                        if ((plantBlock != null) && (! plantBlock.isEmpty()) && (! plantBlock.isFluid())
                                && ((height + 1) < dimension.getMaxHeight())) {
                            chunk.setHytaleBlock(localX, height + 1, localZ, plantBlock);
                            if (plantsPhysicsExempt) {
                                chunk.setDecorative(localX, height + 1, localZ, true);
                            }
                        }
                    }
                }
```

**New (with one-line addition between the existing `setHytaleBlock` call and the `if (plantsPhysicsExempt)` block):**
```java
                int plantIndex = HytalePlantsLayer.getPlantIndex(tile, tileLocalX, tileLocalZ);
                if (plantIndex > 0) {
                    HytaleTerrain plantTerrain = HytaleTerrain.getByLayerIndex(plantIndex);
                    if (plantTerrain != null) {
                        HytaleBlock plantBlock = plantTerrain.getBlock(seed, worldX, worldZ, 0);
                        if ((plantBlock != null) && (! plantBlock.isEmpty()) && (! plantBlock.isFluid())
                                && ((height + 1) < dimension.getMaxHeight())) {
                            chunk.setHytaleBlock(localX, height + 1, localZ, plantBlock);
                            chunk.setSealProtected(localX, height + 1, localZ, true);
                            if (plantsPhysicsExempt) {
                                chunk.setDecorative(localX, height + 1, localZ, true);
                            }
                        }
                    }
                }
```

The new call is **unconditional** (not gated on `plantsPhysicsExempt`). Seal-protection is an export-time concern; runtime physics is a separate concern that stays gated as before.

- [ ] **Step 2.5 — Run the new test and confirm it passes**

Run:
```
cd WorldPainter && mvn -pl WPCore -am test -Dtest=Tp53UnderwaterPlantTest
```

**Expected after the fix:** `plantPaintedOnFloodedColumnSurvivesExportSealPass` — **PASS**. The plant survives the seal pass because `isSealProtected(localX, height + 1, localZ) == true`.

- [ ] **Step 2.6 — Run the existing TP-60 plant test suite to confirm no regressions**

Run:
```
cd WorldPainter && mvn -pl WPCore -am test -Dtest='Tp60*Test,HytaleSeal*Test'
```

**Expected:** all existing plant and seal-pass tests still pass. The TP-60 substrate test does not export to a flooded column so it should be unaffected; the seal-pass tests already cover seal-protected blocks surviving and should continue to pass.

- [ ] **Step 2.7 — Commit**

```
git add WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleWorldExporter.java WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/Tp53UnderwaterPlantTest.java
git commit -m "fix(hytale): seal-protect terrain plants on Hytale export (TP-53 follow-up)

Plants painted via HytalePlantsLayer were being wiped by the
post-export seal pass when their column was flooded. The exporter
called setHytaleBlock at terrain+1 but only called setDecorative when
plantsPhysicsExempt was on, which is a different concern (runtime
gravity + gathering interaction).

Add an unconditional setSealProtected call after every plant placement.
The existing plantsPhysicsExempt-gated setDecorative call is unchanged."
```

---

## Task 3 — Build, full regression, smoke test, YouTrack update

Tasks 1 and 2 each verified their own slice. This task verifies the whole repo still builds and behaves correctly with both fixes together, and closes out the YouTrack ticket.

### Files

- No code changes in this task. Only build, test, manual smoke, and ticket update.

### Steps

- [ ] **Step 3.1 — Run the full WPCore Hytale test suite**

```
cd WorldPainter && mvn -pl WPCore -am test -Dtest='org.pepsoft.worldpainter.hytale.*Test'
```

**Expected:** all tests in the `hytale` package pass, including both newly-added test classes and all existing ones.

- [ ] **Step 3.2 — Build the WPGUI fat JAR**

```
cd WorldPainter && mvn -DskipTests=true package -pl WPGUI -am
```

**Expected:** Maven succeeds. Output JAR at `WorldPainter/WPGUI/target/WPGUI-*-full.jar`.

If the build fails, stop. The fixes are 1 line each, so any build failure is almost certainly an unrelated stale-state problem (e.g. JIDE Docking jars not installed locally — see `BUILDING.md`).

- [ ] **Step 3.3 — Manual smoke test in the GUI**

Launch the app:
```
cd WorldPainter && mvn -pl WPGUI exec:exec
```

Then in the GUI, on a Hytale-platform world:

1. **Underwater bundled prefab.** Create a flat island with a low water region. From the Custom Layers / Prefab catalog, pick `Plants/Seaweed` (the path established in `HytalePrefabLayer.PREFAB_PATHS[3]`). Paint it on a tile whose water level is above terrain. Export. Open the export in Hytale; verify the seaweed prefab is visible underwater rather than missing.
2. **Underwater terrain plant.** Paint a Bush plant from the plant brush on a tile in a flooded area. Export. Verify the plant is visible underwater rather than missing.
3. **Tall prefab spanning the surface.** If the catalog includes a tall enough prefab (e.g. `Plants/Seaweed`), paint it where the water depth is less than the prefab height. Export. Verify the underwater portion is intact and the above-water portion sticks up out of the water.
4. **Dry-tile regression.** Paint the same prefab and the same plant on a dry tile. Export. Verify they appear as before — no visual change.
5. **`plantsPhysicsExempt` toggle.** With the plant painted on a flooded tile, export once with `plantsPhysicsExempt` enabled and once disabled (the toggle is in the export dialog). Verify the plant is present in both cases. (The flag still controls drop behavior, but presence in the world is independent.)

If any of these visual checks fail, capture screenshots and diagnose before declaring the task done. The automated tests cover block presence; visual rendering is the part that automated tests cannot prove.

- [ ] **Step 3.4 — Update YouTrack TP-53**

Use the `mcp__youtrack__add_issue_comment` tool to add a resolution comment, and `mcp__youtrack__update_issue` to set Stage to **Review** (not Done — see the user's saved feedback memory).

Resolution comment template:
```
Follow-up to commit 6503a06a fixing the residual cases the original fix did not cover:

1. **Bundled Hytale prefabs** (HytalePrefabPaster.paste): pasted blocks are now seal-protected so the post-export seal pass leaves them intact when painted on flooded tiles.
2. **Terrain plants** (HytaleWorldExporter plant-export loop): plants painted via HytalePlantsLayer are now seal-protected unconditionally, independent of the plantsPhysicsExempt setting.

Hytale's separate block/fluid layers mean a seal-protected block at terrain+1 in a flooded column simply IS underwater — no per-asset aquatic flag, no UI checkbox, no placement-mode change required.

Spec: docs/superpowers/specs/2026-05-09-tp53-followup-hytale-underwater-prefabs-plants-design.md
Plan: docs/superpowers/plans/2026-05-09-tp53-followup-hytale-underwater-prefabs-plants.md

Floating-on-water-surface placement (lily-pad style) is explicitly out of scope; spin off a separate ticket if/when an asset needs it.
```

After the comment is posted, set Stage to **Review** via:
```
mcp__youtrack__update_issue with issueId="TP-53" and a custom-field update setting Stage to "Review"
```

(If the user prefers to update YouTrack manually, they will say so — in that case skip this step and just remind them of the open Stage transition.)

---

## Self-Review

Run this checklist against the spec at `docs/superpowers/specs/2026-05-09-tp53-followup-hytale-underwater-prefabs-plants-design.md`:

**1. Spec coverage:**
- "Bundled prefabs survive seal pass on flooded tiles" → Task 1 (`singleBlockPrefabSurvivesSealPassWhenSubmerged`).
- "Terrain plants survive seal pass on flooded tiles" → Task 2 (`plantPaintedOnFloodedColumnSurvivesExportSealPass`).
- "Multi-block prefabs spanning the water surface render correctly" → Task 1 (`tallPrefabSpanningWaterSurfaceSurvivesEntirely`).
- "No new UI" → No file in the plan touches WPGUI or any user-facing dialog. ✓
- "No regressions on dry tiles or on the existing Bo2 path" → Task 1 (`prefabOnDryColumnUnaffectedByFix`) covers prefab dry-tile; Task 1 Step 1.6 + Task 2 Step 2.6 run existing test suites which cover the Bo2 path and the dry-tile plant path. ✓
- "Seal-protection independent of plantsPhysicsExempt" → Task 2's fix applies setSealProtected unconditionally; Task 3 Step 3.3 case 5 verifies both toggle states manually.

**2. Placeholder scan:** No "TBD", "TODO", "implement later", or vague "add error handling" patterns. Each step contains either exact code or exact commands with expected output. ✓

**3. Type consistency:** `HytalePrefabPaster`, `HytaleChunk`, `HytaleWorldExporter.sealAboveTerrainColumn`, `HytalePlantsLayer.setPlantIndex`, `HytaleTerrain.BUSH.getLayerIndex()`, `HytaleBlockMapping.HY_WATER`, `HytaleRegionFile` — all referenced consistently across both test files and verified against existing test files in the repo. ✓
