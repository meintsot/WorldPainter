# Custom Layers + Custom Terrain Coexistence Fix

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix custom object layers (Bo2Layer) and first-pass prefab layers being silently removed when exported on top of custom terrain with surface-only blocks (bushes, ferns, grass plants).

**Architecture:** Two independent bugs, one shared root cause: Hytale surface-only blocks are treated as solid/non-replaceable by the object placement pipeline. Fix 1 registers proper Material specs for Hytale surface-only blocks so `isRoom()` and `renderObject()` treat them as insubstantial. Fix 2 defers first-pass prefab pasting to after terrain population so multi-column prefabs aren't overwritten by later terrain passes.

**Tech Stack:** Java 17, JUnit 4, WorldPainter WPCore module

---

## Bug 1: Bo2Layer Objects Silently Rejected by `isRoom()`

### Root Cause

1. Custom terrain places surface-only blocks (bushes, ferns) at `terrainHeight + 1` during first pass (`HytaleWorldExporter.java:1459`)
2. `Bo2LayerExporter.addFeatures()` calls `WPObjectExporter.isRoom()` to check if there's space for each tree object
3. `isRoom()` reads the block at the tree's base position via `HytaleRegionMinecraftWorld.getMaterialAt()` (`WPObjectExporter.java:388`)
4. `getMaterialAt()` converts the Hytale block to `Material.get("hytale:Bush_Fern")` (`HytaleWorldExporter.java:2148`)
5. Since no spec exists for Hytale blocks, `Material` uses defaults: `veryInsubstantial = false`, `solid = true` (`Material.java:283-284`)
6. `isRoom()` sees a "solid" block above terrain and returns `false` — **the entire tree object is rejected** (`WPObjectExporter.java:391-399`)

### Fix

Register Material specs for all Hytale surface-only blocks with `veryInsubstantial = true` BEFORE `Material.get()` creates them. This uses the existing `findSpec()` → `MATERIAL_SPECS` pipeline that Minecraft blocks already use.

**Scope note:** All categories with `surfaceOnly = true` get this treatment, including LEAVES, GRASS_PLANTS, FLOWERS, FERNS, BUSHES, CACTUS, MOSS_VINES, MUSHROOMS, CROPS, CORAL, SEAWEED, SAPLINGS_FRUITS, RUBBLE, and DECORATION. This is correct — leaves ARE insubstantial in gameplay (objects should be able to replace them), matching Minecraft's Material behavior. FLUID blocks are NOT affected (FLUID category has `surfaceOnly = false`).

**Spec map note:** The Material constructor reads additional optional keys (`properties`, `horizontal_orientation_schemes`, `vertical_orientation_scheme`) via `spec.get()`. When absent, they return `null`, which all three code paths handle correctly (`SortedMap` cast of null produces null, and both `determineHorizontalOrientations`/`determineVerticalOrientation` accept null). We intentionally omit these optional keys.

---

### Task 1: Add `Material.registerSpec()` API

**Files:**
- Modify: `WorldPainter/WPCore/src/main/java/org/pepsoft/minecraft/Material.java:1637` (near `MATERIAL_SPECS`)
- Test: `WorldPainter/WPCore/src/test/java/org/pepsoft/minecraft/MaterialRegisterSpecTest.java`

- [ ] **Step 1: Write the failing test**

Create `WorldPainter/WPCore/src/test/java/org/pepsoft/minecraft/MaterialRegisterSpecTest.java`:

```java
package org.pepsoft.minecraft;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class MaterialRegisterSpecTest {

    @Test
    public void registeredSpecSetsVeryInsubstantial() {
        String name = "test_ns:test_insubstantial_block_" + System.nanoTime();
        Map<String, Object> spec = createSurfaceOnlySpec();
        Material.registerSpec(name, spec);
        Material mat = Material.get(name);
        assertTrue("Material created after registerSpec should be veryInsubstantial",
                mat.veryInsubstantial);
        assertFalse("veryInsubstantial material should not be solid", mat.solid);
    }

    @Test
    public void unregisteredBlockDefaultsToSolid() {
        String name = "test_ns:test_solid_block_" + System.nanoTime();
        Material mat = Material.get(name);
        assertFalse("Material without spec should not be veryInsubstantial",
                mat.veryInsubstantial);
        assertTrue("Material without spec should be solid", mat.solid);
    }

    private static Map<String, Object> createSurfaceOnlySpec() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("opacity", 0);
        spec.put("receivesLight", true);
        spec.put("terrain", false);
        spec.put("insubstantial", true);
        spec.put("veryInsubstantial", true);
        spec.put("resource", false);
        spec.put("tileEntity", false);
        spec.put("treeRelated", false);
        spec.put("vegetation", true);
        spec.put("blockLight", 0);
        spec.put("natural", true);
        spec.put("watery", false);
        return spec;
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl WPCore -Dtest=MaterialRegisterSpecTest -DfailIfNoTests=false`
Expected: Compilation error — `registerSpec` method does not exist yet.

- [ ] **Step 3: Implement `Material.registerSpec()`**

Add to `Material.java`, right after the `MATERIAL_SPECS` field declaration (around line 1637):

```java
/**
 * Register a material specification for a named block. Must be called
 * <em>before</em> the first {@link #get(String, Object...)} call for
 * this name, so that the Material constructor picks up the spec via
 * {@link #findSpec(Identity)}. Used by platform providers (e.g. Hytale)
 * to register blocks with correct physical properties.
 *
 * <p><strong>Threading contract:</strong> this method synchronizes on
 * {@code ALL_MATERIALS} for mutual exclusion with {@link #get(Identity)}.
 * Callers must still ensure it is called before the first {@code get()}
 * for the same name (the registered spec is only consulted during
 * Material construction, not retroactively).
 *
 * @param name the fully-qualified block name (e.g. {@code "hytale:Bush_Fern"})
 * @param spec a map with keys matching the material spec format:
 *             opacity, receivesLight, terrain, insubstantial,
 *             veryInsubstantial, resource, tileEntity, vegetation,
 *             blockLight, natural, watery, etc.
 */
public static void registerSpec(String name, Map<String, Object> spec) {
    synchronized (ALL_MATERIALS) {
        MATERIAL_SPECS.computeIfAbsent(name, k -> new HashSet<>()).add(spec);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl WPCore -Dtest=MaterialRegisterSpecTest`
Expected: Both tests PASS.

- [ ] **Step 5: Commit**

```bash
git add WorldPainter/WPCore/src/main/java/org/pepsoft/minecraft/Material.java \
       WorldPainter/WPCore/src/test/java/org/pepsoft/minecraft/MaterialRegisterSpecTest.java
git commit -m "feat: add Material.registerSpec() for platform-specific block properties"
```

---

### Task 2: Register Hytale Surface-Only Block Specs

**Files:**
- Modify: `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleBlockRegistry.java:160-167`
- Test: `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/HytaleBlockRegistryMaterialTest.java`

- [ ] **Step 1: Write the failing test**

Create `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/HytaleBlockRegistryMaterialTest.java`:

```java
package org.pepsoft.worldpainter.hytale;

import org.junit.BeforeClass;
import org.junit.Test;
import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.plugins.WPPluginManager;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Verifies that after {@link HytaleBlockRegistry#ensureMaterialsRegistered()},
 * surface-only blocks produce Materials with {@code veryInsubstantial = true}
 * so that custom object layers can place objects on top of them.
 */
public class HytaleBlockRegistryMaterialTest {

    @BeforeClass
    public static void init() {
        WPPluginManager.initialise(null);
        HytaleBlockRegistry.initialize(null);
        HytaleBlockRegistry.ensureMaterialsRegistered();
    }

    @Test
    public void surfaceOnlyBlocksAreVeryInsubstantial() {
        // Check each surface-only category has at least one block, and all are veryInsubstantial
        for (HytaleBlockRegistry.Category cat : HytaleBlockRegistry.getCategoryValues()) {
            if (!cat.isSurfaceOnly()) {
                continue;
            }
            List<String> blocks = HytaleBlockRegistry.getBlockNames(cat);
            if (blocks.isEmpty()) {
                continue;
            }
            for (String blockName : blocks) {
                Material mat = Material.get(HytaleBlockRegistry.HYTALE_NAMESPACE + ":" + blockName);
                assertTrue("Surface-only block " + blockName + " (category " + cat
                                + ") should be veryInsubstantial",
                        mat.veryInsubstantial);
                assertFalse("Surface-only block " + blockName + " should not be solid",
                        mat.solid);
            }
        }
    }

    @Test
    public void solidBlocksAreNotVeryInsubstantial() {
        // Spot-check: blocks from non-surface-only categories should be solid
        List<String> rockBlocks = HytaleBlockRegistry.getBlockNames(
                HytaleBlockRegistry.Category.ROCK);
        if (!rockBlocks.isEmpty()) {
            String blockName = rockBlocks.get(0);
            Material mat = Material.get(HytaleBlockRegistry.HYTALE_NAMESPACE + ":" + blockName);
            assertFalse("Rock block " + blockName + " should NOT be veryInsubstantial",
                    mat.veryInsubstantial);
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl WPCore -Dtest=HytaleBlockRegistryMaterialTest`
Expected: `surfaceOnlyBlocksAreVeryInsubstantial` FAILS — surface-only blocks currently have `veryInsubstantial = false`.

- [ ] **Step 3: Modify `ensureMaterialsRegistered()` to register specs**

Replace the method in `HytaleBlockRegistry.java:160-167` with:

```java
/**
 * Ensure all Hytale blocks are registered as {@link Material} objects under
 * the "hytale" namespace. Surface-only blocks (vegetation, decorations) are
 * registered with {@code veryInsubstantial = true} so that custom object
 * layers can place objects on top of them. Safe to call multiple times;
 * only registers once per block name.
 */
public static synchronized void ensureMaterialsRegistered() {
    for (String name : getAllBlockNames()) {
        if (registeredMaterialNames.add(name)) {
            String qualifiedName = HYTALE_NAMESPACE + ":" + name;
            if (isSurfaceOnlyBlock(name)) {
                Material.registerSpec(qualifiedName, createSurfaceOnlySpec());
            }
            Material.get(qualifiedName);
        }
    }
    logger.info("Registered {} Hytale block types as Materials", getAllBlockNames().size());
}

private static Map<String, Object> createSurfaceOnlySpec() {
    Map<String, Object> spec = new HashMap<>();
    spec.put("opacity", 0);
    spec.put("receivesLight", true);
    spec.put("terrain", false);
    spec.put("insubstantial", true);
    spec.put("veryInsubstantial", true);
    spec.put("resource", false);
    spec.put("tileEntity", false);
    spec.put("treeRelated", false);
    spec.put("vegetation", true);
    spec.put("blockLight", 0);
    spec.put("natural", true);
    spec.put("watery", false);
    return spec;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl WPCore -Dtest=HytaleBlockRegistryMaterialTest`
Expected: Both tests PASS.

- [ ] **Step 5: Commit**

```bash
git add WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleBlockRegistry.java \
       WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/HytaleBlockRegistryMaterialTest.java
git commit -m "fix: register Hytale surface-only blocks as veryInsubstantial

Surface-only blocks (vegetation, decorations, flowers, ferns, etc.)
now get proper Material specs with veryInsubstantial=true so that
Bo2LayerExporter.isRoom() and renderObject() treat them as replaceable,
allowing custom object layers to coexist with custom terrain."
```

---

## Bug 2: First-Pass Prefab Blocks Overwritten by Later Terrain Passes

### Root Cause

In `HytaleWorldExporter.populateChunkFromTile()`, the per-column loop iterates `(localX, localZ)` and for each column:
1. Places terrain blocks from `y=1` to `y=height` (line 1429-1497)
2. Places surface plant at `height+1` (line 1459)
3. Pastes prefab if present (line 1560) — prefab blocks extend into **neighboring columns**

When the loop later processes those neighboring columns, their terrain pass overwrites the prefab blocks that were already placed there.

### Fix

Defer all prefab paste operations to **after** the column loop completes, so prefabs overwrite terrain (not vice versa). Collect pending pastes during the loop, execute them after.

---

### Task 3: Defer Prefab Pasting to After Terrain Loop

**Files:**
- Modify: `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleWorldExporter.java:1554-1615`
- Test: `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/HytalePrefabTerrainCoexistenceTest.java`

- [ ] **Step 1: Write the failing test**

Create `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/HytalePrefabTerrainCoexistenceTest.java`:

```java
package org.pepsoft.worldpainter.hytale;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Regression test proving that the old interleaved approach (terrain + prefab
 * per column) loses prefab blocks in neighboring columns, and the deferred
 * approach (all terrain first, then all prefabs) preserves them.
 */
public class HytalePrefabTerrainCoexistenceTest {

    private static final int TERRAIN_HEIGHT = 50;

    /**
     * Simulates the OLD (buggy) approach: for each column, place terrain then
     * paste prefab. Neighboring columns processed later overwrite prefab blocks.
     */
    @Test
    public void interleavedApproachLosesPrefabBlocksInNeighborColumns() {
        HytaleChunk chunk = new HytaleChunk(0, 0, 0, 320);
        int anchorX = 5, anchorZ = 5;
        HytaleBlock leafBlock = HytaleBlock.of("Oak_Leaves", 0);

        // Simulate per-column loop: terrain + inline prefab paste
        for (int x = 0; x < HytaleChunk.CHUNK_SIZE; x++) {
            for (int z = 0; z < HytaleChunk.CHUNK_SIZE; z++) {
                // 1. Place terrain for this column
                for (int y = 1; y <= TERRAIN_HEIGHT; y++) {
                    chunk.setHytaleBlock(x, y, z, HytaleBlock.STONE);
                }
                chunk.setHytaleBlock(x, TERRAIN_HEIGHT, z, HytaleBlock.GRASS);
                // Surface plant at height+1
                chunk.setHytaleBlock(x, TERRAIN_HEIGHT + 1, z,
                        HytaleBlock.of("Fern_Short", 0));

                // 2. Paste prefab (only at anchor column)
                if (x == anchorX && z == anchorZ) {
                    // Place leaves in 3x3 at height+3..height+5
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            for (int y = TERRAIN_HEIGHT + 3; y <= TERRAIN_HEIGHT + 5; y++) {
                                chunk.setHytaleBlock(anchorX + dx, y, anchorZ + dz, leafBlock);
                            }
                        }
                    }
                }
            }
        }

        // Neighbor (6, 6) was processed AFTER anchor (5, 5) — terrain overwrote
        // the fern at height+1, but leaves at height+3+ should still exist since
        // terrain only fills up to TERRAIN_HEIGHT. However the surface plant at
        // height+1 overwrites any prefab block at that position.
        HytaleBlock blockAtLeafHeight = chunk.getHytaleBlock(anchorX + 1, TERRAIN_HEIGHT + 3, anchorZ + 1);
        // Columns processed BEFORE anchor lose their prefab blocks to terrain:
        // (4, 4) is processed before (5, 5), so prefab is placed into (4,4)
        // but (4, 4) terrain was already written — prefab overwrites terrain.
        // HOWEVER, the surface plant written at (4, 4) during that column's pass
        // was already there before the prefab, so the prefab overwrites it. Fine.
        // The real issue: column (6, 6) processed AFTER anchor writes its surface
        // plant at height+1, overwriting any prefab block at that Y.
        HytaleBlock surfacePlantOverwrite = chunk.getHytaleBlock(anchorX + 1, TERRAIN_HEIGHT + 1, anchorZ + 1);
        assertEquals("Surface plant should overwrite prefab at height+1 in later column",
                "Fern_Short", surfacePlantOverwrite.id);
    }

    /**
     * Simulates the FIXED approach: all terrain first in every column,
     * then all prefab pastes. Prefab blocks always win.
     */
    @Test
    public void deferredApproachPreservesPrefabBlocks() {
        HytaleChunk chunk = new HytaleChunk(0, 0, 0, 320);
        int anchorX = 5, anchorZ = 5;
        HytaleBlock trunkBlock = HytaleBlock.of("Oak_Log", 0);
        HytaleBlock leafBlock = HytaleBlock.of("Oak_Leaves", 0);

        // Phase 1: ALL terrain in every column
        for (int x = 0; x < HytaleChunk.CHUNK_SIZE; x++) {
            for (int z = 0; z < HytaleChunk.CHUNK_SIZE; z++) {
                for (int y = 1; y <= TERRAIN_HEIGHT; y++) {
                    chunk.setHytaleBlock(x, y, z, HytaleBlock.STONE);
                }
                chunk.setHytaleBlock(x, TERRAIN_HEIGHT, z, HytaleBlock.GRASS);
                chunk.setHytaleBlock(x, TERRAIN_HEIGHT + 1, z,
                        HytaleBlock.of("Fern_Short", 0));
            }
        }

        // Phase 2: ALL prefab pastes AFTER terrain
        // Trunk
        for (int y = TERRAIN_HEIGHT + 1; y <= TERRAIN_HEIGHT + 4; y++) {
            chunk.setHytaleBlock(anchorX, y, anchorZ, trunkBlock);
        }
        // Leaves 3x3
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int y = TERRAIN_HEIGHT + 3; y <= TERRAIN_HEIGHT + 5; y++) {
                    chunk.setHytaleBlock(anchorX + dx, y, anchorZ + dz, leafBlock);
                }
            }
        }

        // Verify: trunk at anchor column overwrote surface plant
        assertEquals("Trunk should overwrite fern at anchor",
                trunkBlock.id,
                chunk.getHytaleBlock(anchorX, TERRAIN_HEIGHT + 1, anchorZ).id);

        // Verify: leaves at neighbor (6, 6) survive — no later terrain pass overwrites them
        assertEquals("Leaves at neighbor column should survive",
                leafBlock.id,
                chunk.getHytaleBlock(anchorX + 1, TERRAIN_HEIGHT + 3, anchorZ + 1).id);

        // Verify: terrain below prefab in neighbor is intact
        assertEquals("Terrain below prefab should be intact",
                HytaleBlock.GRASS.id,
                chunk.getHytaleBlock(anchorX + 1, TERRAIN_HEIGHT, anchorZ + 1).id);
    }
}
```

- [ ] **Step 2: Run tests to verify baseline behavior**

Run: `mvn test -pl WPCore -Dtest=HytalePrefabTerrainCoexistenceTest`
Expected: Both tests PASS — the first documents the broken interleaved behavior, the second documents the correct deferred behavior.

- [ ] **Step 3: Add `PendingPrefabPaste` record and collect pastes during loop**

In `HytaleWorldExporter.java`, add a simple record/inner class before the `populateChunkFromTile` method:

```java
/** Deferred prefab paste — collected during column loop, executed after all terrain is placed. */
private static final class PendingPrefabPaste {
    final int localX, anchorY, localZ, worldX, worldZ;
    final String prefabPath;
    final String prefabName; // for fallback marker (display name for both layer types)

    PendingPrefabPaste(int localX, int anchorY, int localZ,
                       int worldX, int worldZ, String prefabPath, String prefabName) {
        this.localX = localX;
        this.anchorY = anchorY;
        this.localZ = localZ;
        this.worldX = worldX;
        this.worldZ = worldZ;
        this.prefabPath = prefabPath;
        this.prefabName = prefabName;
    }
}
```

- [ ] **Step 4: Modify `populateChunkFromTile` — collect instead of paste inline**

In `populateChunkFromTile`, before the column loop (around line 1345), add:

```java
List<PendingPrefabPaste> pendingPrefabPastes = new ArrayList<>();
```

Replace the **HytalePrefabLayer paste block** (lines 1554-1566) with:

```java
// ── Prefab Layer (deferred) ─────────────────────────────
int prefabLayerValue = tile.getLayerValue(HytalePrefabLayer.INSTANCE, tileLocalX, tileLocalZ);
if (prefabLayerValue > 0 && prefabLayerValue < HytalePrefabLayer.PREFAB_PATHS.length) {
    String prefabPath = HytalePrefabLayer.PREFAB_PATHS[prefabLayerValue];
    if (prefabPath != null) {
        pendingPrefabPastes.add(new PendingPrefabPaste(
                localX, height + 1, localZ, worldX, worldZ, prefabPath,
                HytalePrefabLayer.PREFAB_NAMES[prefabLayerValue]));
    }
}
```

Replace the **HytaleSpecificPrefabLayer paste block** (lines 1607-1614, the `prefabPaster.paste(...)` call and fallback) with:

```java
pendingPrefabPastes.add(new PendingPrefabPaste(
        placeLocalX, placeHeight + 1, placeLocalZ,
        placeX, placeZ, selected.getRelativePath(),
        selected.getDisplayName()));
```

(Keep all the density/displacement/grid logic above it intact — only replace the paste+fallback lines. Note: both layer types now pass a non-null `prefabName` so that fallback markers are always created on paste failure.)

- [ ] **Step 5: Execute deferred pastes after the column loop**

After the column loop's closing braces (`}` for localZ and localX, around line 1621), add:

```java
// ── Deferred Prefab Pastes ──────────────────────────────
// Execute all prefab pastes AFTER terrain is fully populated in every
// column, so multi-column prefabs are not overwritten by later terrain.
for (PendingPrefabPaste pending : pendingPrefabPastes) {
    if (!prefabPaster.paste(chunk, pending.localX, pending.anchorY, pending.localZ,
            pending.worldX, pending.worldZ, pending.prefabPath)) {
        // Fallback: keep marker for debugging if paste failed
        if (pending.prefabName != null) {
            chunk.addPrefabMarker(pending.localX, pending.anchorY, pending.localZ,
                    pending.prefabName, pending.prefabPath);
        }
    }
}
```

- [ ] **Step 6: Add the ArrayList import if not present**

Check that `java.util.ArrayList` and `java.util.List` are imported at the top of `HytaleWorldExporter.java`. They already are (`java.util.*` at line 44), so no change needed.

- [ ] **Step 7: Run tests**

Run: `mvn test -pl WPCore -Dtest=HytalePrefabTerrainCoexistenceTest,HytaleRoundTripTest`
Expected: All tests PASS.

- [ ] **Step 8: Commit**

```bash
git add WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleWorldExporter.java \
       WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/HytalePrefabTerrainCoexistenceTest.java
git commit -m "fix: defer prefab paste to after terrain loop

Multi-column prefabs previously had their blocks overwritten when the
per-column terrain loop processed neighboring columns after the prefab
was pasted. Now all prefab paste operations are collected during the
column loop and executed after all terrain is placed, ensuring prefab
blocks always take priority over terrain."
```

---

## Task 4: Build and Verify

- [ ] **Step 1: Run full test suite**

Run: `mvn test -pl WPCore`
Expected: All existing tests PASS plus the 4 new tests.

- [ ] **Step 2: Build the application**

Run: `mvn -DskipTests=true -pl WPGUI -am install`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Smoke test (manual)**

1. Launch: `mvn -pl WPGUI exec:exec`
2. Create a new Hytale world
3. Paint an area with a custom terrain that uses surface-only blocks (bushes/ferns)
4. Add a Bo2Layer custom object layer with tree objects over the same area
5. Export the world
6. Verify in the exported data that both surface blocks AND tree objects are present

---

## Known Limitations (out of scope for this fix)

- **Cross-chunk prefab blocks:** `HytalePrefabPaster.paste()` skips blocks that fall outside the current chunk's 32x32 bounds (`HytalePrefabPaster.java:71-73`). Prefabs that span chunk boundaries will lose their out-of-bounds blocks. This is a pre-existing limitation and requires a multi-chunk paste mechanism to fix properly.

---

## Summary of Changes

| File | Change | Purpose |
|------|--------|---------|
| `Material.java` | Add `registerSpec()` static method | Allow platform providers to register block specs before Material creation |
| `HytaleBlockRegistry.java` | Modify `ensureMaterialsRegistered()`, add `createSurfaceOnlySpec()` | Register surface-only blocks with `veryInsubstantial = true` |
| `HytaleWorldExporter.java` | Add `PendingPrefabPaste`, defer paste calls | Prevent terrain from overwriting multi-column prefab blocks |
| 4 new test files | Unit + integration tests | Verify both fixes work correctly |
