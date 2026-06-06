# Hytale Terrain Palette Backward-Compat Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop painted Hytale terrain from silently remapping to the wrong block when the block registry changes (e.g. TP-57's +304 blocks turned saved "clay" into a brick), by making the per-pixel terrain index self-describing in the `.world` file and adding a one-time remap for worlds saved before this fix.

**Architecture:** Painted Hytale terrain is stored per pixel as a 1-based ordinal into `HytaleTerrain.ALL_TERRAINS`, which is `PICK_LIST` (curated) followed by auto-generated terrains for every registry block in global alphabetical order. Because adding registry blocks shifts those alphabetical positions, the ordinal's meaning changes between builds. We fix this two ways: (1) **going forward**, `Dimension.writeObject` snapshots the ordered block-id list into `managedAttributes["hytaleTerrainPalette"]`, and `Dimension.readObject` remaps stored ordinals back to current ordinals **by block id**; (2) **legacy**, worlds with terrain data but no palette get a one-time remap against a frozen snapshot of the pre-TP-57 `ALL_TERRAINS` block-id ordering. A new `hytaleTerrainVersion = 3` gates the new logic; the existing V0/V1 (`< 11`) migration is untouched.

**Tech Stack:** Java 17, JUnit 4, Maven (`mvn -pl WPCore test`), Java serialization, WorldPainter `Tile`/`Layer`/`Dimension` model.

---

## File Structure

- **Create** `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleTerrainPalette.java` — pure helper: build the current palette (ordered block-id list of `ALL_TERRAINS`), and remap a tile collection given an `oldIndex -> blockId` palette. No I/O, no statics that touch disk. Fully unit-testable.
- **Create** `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleTerrainV2Snapshot.java` — holds the frozen pre-TP-57 `ALL_TERRAINS` block-id array (generated artifact, see Task 6) used only for the legacy bridge.
- **Modify** `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleTerrain.java` — add `currentTerrainPaletteBlockIds()` accessor (exposes `ALL_TERRAINS` block ids in index order) so the palette helper and tests don't reach into private statics.
- **Modify** `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/Dimension.java` — `writeObject` snapshots the palette; `readObject` (`wpVersion < 13` block) applies palette remap or legacy remap; bump `CURRENT_WP_VERSION` 12 → 13.
- **Test** `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/HytaleTerrainPaletteTest.java` — unit tests for the helper.
- **Test** `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/HytaleTerrainPaletteRoundTripTest.java` — end-to-end: paint terrain, serialize a `Dimension`, mutate the registry/ordering, deserialize, assert the block survived.

**Index-space invariant (read before coding):** `HytaleTerrain.getLayerIndex()` returns a **1-based** index; `0` means "no terrain". `HytaleTerrainLayer.getTerrainIndex(tile,x,y)` returns the stored 1-based value; `getByLayerIndex(i)` resolves it. The palette stores element `i-1` = block id for stored index `i`. Remapping a removed/unknown block id yields index `0` (clear), never a wrong block.

---

## Task 1: Expose current terrain palette from HytaleTerrain

**Files:**
- Modify: `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleTerrain.java` (add a public static accessor near `getByLayerIndex`, around line 2933)

- [ ] **Step 1: Write the failing test**

Create `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/HytaleTerrainPaletteTest.java`:

```java
package org.pepsoft.worldpainter.hytale;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class HytaleTerrainPaletteTest {

    @Test
    public void currentPaletteIsAlignedWithLayerIndices() {
        List<String> palette = HytaleTerrain.currentTerrainPaletteBlockIds();
        assertFalse("palette must not be empty", palette.isEmpty());
        // Element (i-1) of the palette must be the block id resolved by layer index i.
        for (int i = 1; i <= palette.size(); i++) {
            HytaleTerrain t = HytaleTerrain.getByLayerIndex(i);
            assertNotNull("no terrain for index " + i, t);
            assertEquals("palette/index mismatch at " + i,
                    (t.getBlock() != null) ? t.getBlock().id : null,
                    palette.get(i - 1));
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl WPCore test -Dtest=HytaleTerrainPaletteTest`
Expected: FAIL — compile error, `currentTerrainPaletteBlockIds()` does not exist.

- [ ] **Step 3: Add the accessor**

In `HytaleTerrain.java`, immediately after `getByLayerIndex` (around line 2936), add:

```java
    /**
     * The block ids of every terrain in {@link #ALL_TERRAINS}, in layer-index
     * order (element {@code i} corresponds to 1-based layer index {@code i + 1}).
     * Used to write a self-describing terrain palette into saved worlds so that
     * stored per-pixel indices can be remapped by block id when the terrain list
     * changes (e.g. when the block registry grows). Entries are never null for
     * curated/auto terrains, but the type allows null defensively.
     */
    public static List<String> currentTerrainPaletteBlockIds() {
        List<String> ids = new ArrayList<>(ALL_TERRAINS.length);
        for (HytaleTerrain t : ALL_TERRAINS) {
            ids.add((t.block != null) ? t.block.id : null);
        }
        return ids;
    }
```

(`java.util.List` and `java.util.ArrayList` are already imported in this file.)

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -pl WPCore test -Dtest=HytaleTerrainPaletteTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleTerrain.java \
        WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/HytaleTerrainPaletteTest.java
git commit -m "feat(hytale): expose current terrain palette block ids (TP-57 compat)"
```

---

## Task 2: Palette helper — build + remap by block id

**Files:**
- Create: `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleTerrainPalette.java`
- Test: `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/HytaleTerrainPaletteTest.java` (extend)

- [ ] **Step 1: Write the failing test (append to HytaleTerrainPaletteTest)**

```java
    @Test
    public void remapByBlockIdHandlesShiftAndRemoval() {
        // Simulated "old" palette: index 1 -> Rock_Stone, index 2 -> Soil_Clay,
        // index 3 -> a block id that no longer exists.
        java.util.Map<Integer, String> oldPalette = new java.util.HashMap<>();
        oldPalette.put(1, "Rock_Stone");
        oldPalette.put(2, "Soil_Clay");
        oldPalette.put(3, "Block_That_Was_Deleted");

        int stoneNow = HytaleTerrain.getByBlockId("Rock_Stone").getLayerIndex();
        int clayNow  = HytaleTerrain.getByBlockId("Soil_Clay").getLayerIndex();

        assertEquals(stoneNow, HytaleTerrainPalette.remapIndex(1, oldPalette));
        assertEquals(clayNow,  HytaleTerrainPalette.remapIndex(2, oldPalette));
        assertEquals("unknown block id clears the pixel",
                0, HytaleTerrainPalette.remapIndex(3, oldPalette));
        assertEquals("index 0 stays 0",
                0, HytaleTerrainPalette.remapIndex(0, oldPalette));
        assertEquals("index absent from palette is left unchanged",
                7, HytaleTerrainPalette.remapIndex(7, oldPalette));
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl WPCore test -Dtest=HytaleTerrainPaletteTest`
Expected: FAIL — `HytaleTerrainPalette` does not exist.

- [ ] **Step 3: Create the helper**

`HytaleTerrainPalette.java`:

```java
package org.pepsoft.worldpainter.hytale;

import org.pepsoft.worldpainter.Tile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helpers for the self-describing Hytale terrain palette.
 *
 * <p>Per-pixel Hytale terrain is stored as a 1-based ordinal into
 * {@link HytaleTerrain#currentTerrainPaletteBlockIds()}. That ordinal's meaning
 * shifts whenever the terrain list changes (notably when the block registry
 * grows), so a world that stored "clay" can read back as a different block. To
 * keep saves stable we persist the ordinal-&gt;block-id palette at save time and
 * remap stored ordinals back to current ordinals by block id at load time.</p>
 */
public final class HytaleTerrainPalette {

    private static final Logger logger = LoggerFactory.getLogger(HytaleTerrainPalette.class);

    private HytaleTerrainPalette() {}

    /**
     * Build the current palette as a {@code stored-index -> block-id} map for
     * every 1-based index in {@link HytaleTerrain#currentTerrainPaletteBlockIds()}.
     */
    public static Map<Integer, String> currentPalette() {
        List<String> ids = HytaleTerrain.currentTerrainPaletteBlockIds();
        Map<Integer, String> palette = new HashMap<>(ids.size() * 2);
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            if (id != null) {
                palette.put(i + 1, id);
            }
        }
        return palette;
    }

    /**
     * Remap a single stored 1-based index using {@code oldPalette}
     * ({@code stored-index -> block-id} as it was when the world was saved).
     * Returns the current layer index for that block id, {@code 0} if the block
     * id no longer resolves to a terrain, or the original index unchanged if it
     * is {@code 0} or not present in the palette.
     */
    public static int remapIndex(int oldIndex, Map<Integer, String> oldPalette) {
        if (oldIndex <= 0) {
            return 0;
        }
        String blockId = oldPalette.get(oldIndex);
        if (blockId == null) {
            // Index not described by the palette — cannot safely remap; leave as-is.
            return oldIndex;
        }
        HytaleTerrain current = HytaleTerrain.getByBlockId(blockId);
        return (current != null) ? current.getLayerIndex() : 0;
    }

    /**
     * Remap every pixel of every tile that carries Hytale terrain data using
     * {@code oldPalette}. Pixels whose index is unchanged are skipped. Returns
     * the number of pixels actually rewritten.
     */
    public static int remapTiles(Collection<? extends Tile> tiles, Map<Integer, String> oldPalette) {
        int changed = 0;
        for (Tile tile : tiles) {
            if (!HytaleTerrainLayer.hasTerrainData(tile)) {
                continue;
            }
            for (int y = 0; y < 128; y++) {
                for (int x = 0; x < 128; x++) {
                    int oldIndex = HytaleTerrainLayer.getTerrainIndex(tile, x, y);
                    if (oldIndex <= 0) {
                        continue;
                    }
                    int newIndex = remapIndex(oldIndex, oldPalette);
                    if (newIndex != oldIndex) {
                        HytaleTerrainLayer.setTerrainIndex(tile, x, y, newIndex);
                        changed++;
                    }
                }
            }
        }
        if (changed > 0) {
            logger.info("Hytale terrain palette remap: rewrote {} pixels", changed);
        }
        return changed;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -pl WPCore test -Dtest=HytaleTerrainPaletteTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleTerrainPalette.java \
        WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/HytaleTerrainPaletteTest.java
git commit -m "feat(hytale): terrain palette build + remap-by-block-id helper (TP-57 compat)"
```

---

## Task 3: remapTiles integration test (paint → remap → verify)

**Files:**
- Test: `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/HytaleTerrainPaletteTest.java` (extend)

- [ ] **Step 1: Write the failing test**

This needs a `Tile`. Reuse the project's tile-construction pattern — find an existing Hytale test that builds a tile (e.g. grep `new Tile(` or a `TileFactory` usage under `WPCore/src/test`) and copy its setup. Append:

```java
    @Test
    public void remapTilesRewritesStoredClayIndexToCurrentClayIndex() {
        org.pepsoft.worldpainter.Tile tile =
                new org.pepsoft.worldpainter.Tile(0, 0, 0, 256); // minHeight 0, maxHeight 256
        // Pretend this world was saved when "Soil_Clay" lived at stored index 5.
        HytaleTerrainLayer.setTerrainIndex(tile, 10, 10, 5);
        java.util.Map<Integer, String> oldPalette = new java.util.HashMap<>();
        oldPalette.put(5, "Soil_Clay");

        int rewritten = HytaleTerrainPalette.remapTiles(
                java.util.Collections.singletonList(tile), oldPalette);

        assertEquals(1, rewritten);
        int clayNow = HytaleTerrain.getByBlockId("Soil_Clay").getLayerIndex();
        assertEquals(clayNow, HytaleTerrainLayer.getTerrainIndex(tile, 10, 10));
    }
```

> If `new Tile(...)` signature differs, match the constructor used by the nearest existing WPCore test (do not invent one). The behavioural assertions stay identical.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl WPCore test -Dtest=HytaleTerrainPaletteTest#remapTilesRewritesStoredClayIndexToCurrentClayIndex`
Expected: FAIL only if the index already equals clay's current index (it won't, since 5 is arbitrary) — i.e. it should PASS once `Tile` setup compiles. If it fails to compile, fix the `Tile` constructor per the note above.

- [ ] **Step 3: (No implementation needed — `remapTiles` already exists)**

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -q -pl WPCore test -Dtest=HytaleTerrainPaletteTest`
Expected: PASS (all methods).

- [ ] **Step 5: Commit**

```bash
git add WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/HytaleTerrainPaletteTest.java
git commit -m "test(hytale): remapTiles rewrites stored clay index to current"
```

---

## Task 4: Snapshot palette on save (Dimension.writeObject)

**Files:**
- Modify: `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/Dimension.java:2808-2812` (writeObject)

- [ ] **Step 1: Write the failing test**

Create `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/HytaleTerrainPaletteRoundTripTest.java`. Build a `Dimension` the same way existing WPCore dimension tests do (grep `new Dimension(` under `WPCore/src/test` and copy the closest constructor/fixture). Then:

```java
    @Test
    public void savedDimensionEmbedsTerrainPaletteWhenTerrainPainted() throws Exception {
        Dimension dim = newTestDimension();           // helper mirroring existing tests
        org.pepsoft.worldpainter.Tile tile = dim.getTileForEditing(0, 0); // or dim's fixture tile
        int clayIdx = HytaleTerrain.getByBlockId("Soil_Clay").getLayerIndex();
        HytaleTerrainLayer.setTerrainIndex(tile, 1, 1, clayIdx);

        byte[] bytes = serialize(dim);                // ObjectOutputStream round-trip helper
        Dimension reloaded = (Dimension) deserialize(bytes);

        // The reloaded dimension must still resolve to clay.
        org.pepsoft.worldpainter.Tile rtile = reloaded.getTile(0, 0);
        int idx = HytaleTerrainLayer.getTerrainIndex(rtile, 1, 1);
        assertEquals("Soil_Clay", HytaleTerrain.getByLayerIndex(idx).getBlock().id);
    }
```

Add `serialize`/`deserialize`/`newTestDimension` helpers in the test class (use `ByteArrayOutputStream` + `ObjectOutputStream`). This test passes trivially today (no ordering change yet); its real value is regression-locking the save path and is the harness reused in Task 5/7.

- [ ] **Step 2: Run test to verify it fails or passes**

Run: `mvn -q -pl WPCore test -Dtest=HytaleTerrainPaletteRoundTripTest`
Expected: compiles and PASSES (no registry mutation yet). If it fails to compile, align `Dimension` construction with existing tests.

- [ ] **Step 3: Snapshot the palette in writeObject**

Replace `Dimension.writeObject` (lines 2808-2812) with:

```java
    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        prepareForSaving();
        // Persist a self-describing Hytale terrain palette so per-pixel terrain
        // ordinals can be remapped by block id if the terrain list changes in a
        // later build (e.g. when the block registry grows). Only written when this
        // dimension actually has Hytale terrain data.
        if (hasHytaleTerrainData()) {
            if (managedAttributes == null) {
                managedAttributes = new HashMap<>();
            }
            managedAttributes.put("hytaleTerrainPalette",
                    new HashMap<>(org.pepsoft.worldpainter.hytale.HytaleTerrainPalette.currentPalette()));
            managedAttributes.put("hytaleTerrainVersion", 3);
        }
        out.defaultWriteObject();
    }

    private boolean hasHytaleTerrainData() {
        for (Tile tile : tiles.values()) {
            if (org.pepsoft.worldpainter.hytale.HytaleTerrainLayer.hasTerrainData(tile)) {
                return true;
            }
        }
        return false;
    }
```

(`java.util.HashMap` is already imported in `Dimension.java`.)

- [ ] **Step 4: Strengthen the test to assert the palette is embedded**

Append to `savedDimensionEmbedsTerrainPaletteWhenTerrainPainted` (before the existing assert):

```java
        @SuppressWarnings("unchecked")
        java.util.Map<Integer, String> palette =
                (java.util.Map<Integer, String>) reloaded.getMetadataForTests("hytaleTerrainPalette");
        assertNotNull("palette must be embedded on save", palette);
        assertEquals("Soil_Clay", palette.get(clayIdx));
```

If `Dimension` has no test accessor for a managed attribute, add a minimal package-private one next to the migration code:

```java
    Object getMetadataForTests(String key) {
        return (managedAttributes != null) ? managedAttributes.get(key) : null;
    }
```

- [ ] **Step 5: Run + commit**

Run: `mvn -q -pl WPCore test -Dtest=HytaleTerrainPaletteRoundTripTest`
Expected: PASS.

```bash
git add WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/Dimension.java \
        WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/HytaleTerrainPaletteRoundTripTest.java
git commit -m "feat(hytale): embed self-describing terrain palette on save (TP-57 compat)"
```

---

## Task 5: Remap on load when palette present (Dimension.readObject) + bump version

**Files:**
- Modify: `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/Dimension.java:2753` area (add a `wpVersion < 13` block) and `:2887` (`CURRENT_WP_VERSION` 12 → 13)

- [ ] **Step 1: Write the failing test (the real regression)**

Append to `HytaleTerrainPaletteRoundTripTest` a test that simulates an ordering change between save and load. Since we cannot mutate the static registry mid-test, simulate by hand-editing the embedded palette so a stored index maps to a block id whose **current** index differs, then assert load remaps it:

```java
    @Test
    public void loadRemapsWhenStoredPaletteDisagreesWithCurrentOrdering() throws Exception {
        Dimension dim = newTestDimension();
        org.pepsoft.worldpainter.Tile tile = dim.getTileForEditing(0, 0);
        int clayNow = HytaleTerrain.getByBlockId("Soil_Clay").getLayerIndex();
        int stoneNow = HytaleTerrain.getByBlockId("Rock_Stone").getLayerIndex();
        // Store STONE's current index, but lie in the palette that this index meant CLAY
        // (as an older build would have, before the registry grew).
        HytaleTerrainLayer.setTerrainIndex(tile, 2, 2, stoneNow);

        // Force a pre-fix-style save: palette says stoneNow -> Soil_Clay, version 3.
        dim.putMetadataForTests("hytaleTerrainVersion", 3);
        java.util.Map<Integer, String> lyingPalette = new java.util.HashMap<>();
        lyingPalette.put(stoneNow, "Soil_Clay");
        dim.putMetadataForTests("hytaleTerrainPalette", lyingPalette);

        Dimension reloaded = (Dimension) deserialize(serialize(dim));
        int idx = HytaleTerrainLayer.getTerrainIndex(reloaded.getTile(0, 0), 2, 2);
        assertEquals("stored index must be remapped to clay's CURRENT index", clayNow, idx);
    }
```

Add package-private `void putMetadataForTests(String key, Object value)` to `Dimension` (writes into `managedAttributes`, creating it if null). Note: `writeObject` (Task 4) only overwrites the palette when `hasHytaleTerrainData()` — keep a non-clay block painted so it does, OR guard the test by also painting clay; simplest is to assert against the reloaded value only.

> Because `writeObject` recomputes the palette from the *current* ordering, this test must bypass that recompute to preserve the "lying" palette. Implement `putMetadataForTests` to set a transient flag `suppressTerrainPaletteSnapshot` that `writeObject` honours when set (test-only). Add:
> ```java
>     transient boolean suppressTerrainPaletteSnapshotForTests;
> ```
> and in `writeObject` wrap the snapshot block with `if (!suppressTerrainPaletteSnapshotForTests && hasHytaleTerrainData()) { ... }`. Set it in the test via a setter `setSuppressTerrainPaletteSnapshotForTests(true)`.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl WPCore test -Dtest=HytaleTerrainPaletteRoundTripTest#loadRemapsWhenStoredPaletteDisagreesWithCurrentOrdering`
Expected: FAIL — load does not remap yet (idx stays `stoneNow`).

- [ ] **Step 3: Add the load-time remap block**

In `Dimension.readObject`, immediately after the `if (wpVersion < 12) { ... }` block (ends ~line 2769) and before `wpVersion = CURRENT_WP_VERSION;`, insert:

```java
        if (wpVersion < 13) {
            // Self-describing terrain palette remap. Worlds saved with version >= 3
            // carry a stored-index -> block-id palette; remap stored ordinals to the
            // current ordering by block id so registry/terrain-list changes can no
            // longer silently substitute blocks (TP-57: +304 blocks shifted indices).
            Object versionObj = (managedAttributes != null)
                    ? managedAttributes.get("hytaleTerrainVersion") : null;
            int htv = (versionObj instanceof Integer) ? (Integer) versionObj : 0;
            @SuppressWarnings("unchecked")
            Map<Integer, String> storedPalette = (managedAttributes != null)
                    ? (Map<Integer, String>) managedAttributes.get("hytaleTerrainPalette") : null;
            if ((htv >= 3) && (storedPalette != null)) {
                org.pepsoft.worldpainter.hytale.HytaleTerrainPalette.remapTiles(tiles.values(), storedPalette);
            }
            // (Legacy worlds without a palette are handled in Task 7.)
        }
```

- [ ] **Step 4: Bump CURRENT_WP_VERSION**

Change `Dimension.java:2887` from `private static final int CURRENT_WP_VERSION = 12;` to `= 13;`.

- [ ] **Step 5: Run + commit**

Run: `mvn -q -pl WPCore test -Dtest=HytaleTerrainPaletteRoundTripTest`
Expected: PASS (all methods).

```bash
git add WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/Dimension.java \
        WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/HytaleTerrainPaletteRoundTripTest.java
git commit -m "feat(hytale): remap terrain by stored palette on load; wpVersion 13 (TP-57 compat)"
```

---

## Task 6: Generate the frozen pre-TP-57 terrain snapshot

**Files:**
- Create: `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleTerrainV2Snapshot.java`

- [ ] **Step 1: Dump the pre-TP-57 ordering from the parent of commit a5812f65**

The legacy bridge needs `ALL_TERRAINS` block ids exactly as they were before TP-57. Generate, do not hand-write:

```bash
# From repo root. Parent of TP-57.
git rev-parse a5812f65^            # note the hash, e.g. <PARENT>
git stash --include-untracked       # protect WIP if any
git checkout <PARENT>
```

Add a throwaway dump test at the parent checkout:
`WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/DumpTerrainOrdering.java`

```java
package org.pepsoft.worldpainter.hytale;
import org.junit.Test;
public class DumpTerrainOrdering {
    @Test public void dump() {
        StringBuilder sb = new StringBuilder();
        for (HytaleTerrain t : HytaleTerrain.getAllTerrains()) {
            sb.append('"').append(t.getBlock() != null ? t.getBlock().id : "").append("\",\n");
        }
        System.out.println("=== BEGIN PRE_TP57 ===");
        System.out.println(sb);
        System.out.println("=== END PRE_TP57 ===");
    }
}
```

Run: `mvn -q -pl WPCore test -Dtest=DumpTerrainOrdering` and capture the lines between the markers. Then:

```bash
git checkout -          # back to the working branch
git stash pop           # restore WIP if stashed
```

> `getAllTerrains()` returns `ALL_TERRAINS` order (Task-1 invariant). If the dump test fails to compile at the parent (older API), fall back to printing `getPickListWithIcons()` then the auto blocks via `HytaleBlockRegistry.getAllBlockNames()` minus curated — match whatever public API exists at that commit.

- [ ] **Step 2: Embed the captured ordering**

Create `HytaleTerrainV2Snapshot.java`:

```java
package org.pepsoft.worldpainter.hytale;

import java.util.HashMap;
import java.util.Map;

/**
 * Frozen snapshot of {@link HytaleTerrain} {@code ALL_TERRAINS} block ids as they
 * were immediately before commit a5812f65 (TP-57, +304 registry blocks). Used only
 * to migrate worlds saved before the self-describing terrain palette existed: their
 * stored per-pixel ordinals are interpreted against this ordering and remapped to the
 * current ordering by block id. Best-effort — worlds from even older registry eras
 * may only be partially corrected; re-saving in the fixed build makes them
 * self-describing thereafter.
 */
final class HytaleTerrainV2Snapshot {

    private HytaleTerrainV2Snapshot() {}

    /** Block ids in pre-TP-57 layer-index order (element i == 1-based index i+1). */
    static final String[] BLOCK_IDS = {
        // <<< paste the captured lines from Step 1 here >>>
    };

    /** {@code stored-index -> block-id} palette for the pre-TP-57 ordering. */
    static Map<Integer, String> asPalette() {
        Map<Integer, String> palette = new HashMap<>(BLOCK_IDS.length * 2);
        for (int i = 0; i < BLOCK_IDS.length; i++) {
            if (BLOCK_IDS[i] != null && !BLOCK_IDS[i].isEmpty()) {
                palette.put(i + 1, BLOCK_IDS[i]);
            }
        }
        return palette;
    }
}
```

- [ ] **Step 3: Sanity test the snapshot**

Append to `HytaleTerrainPaletteTest`:

```java
    @Test
    public void preTp57SnapshotResolvesClayAndStone() {
        Map<Integer, String> snap = HytaleTerrainV2Snapshot.asPalette();
        assertTrue("snapshot must be populated", snap.size() > 40);
        assertTrue("snapshot must contain Soil_Clay", snap.containsValue("Soil_Clay"));
        assertTrue("snapshot must contain Rock_Stone", snap.containsValue("Rock_Stone"));
    }
```

(Add `import java.util.Map;` and `import static org.junit.Assert.assertTrue;` if needed.)

- [ ] **Step 4: Run + commit**

Run: `mvn -q -pl WPCore test -Dtest=HytaleTerrainPaletteTest`
Expected: PASS. Delete the throwaway `DumpTerrainOrdering.java` if it lingered.

```bash
git add WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleTerrainV2Snapshot.java \
        WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/HytaleTerrainPaletteTest.java
git commit -m "feat(hytale): frozen pre-TP-57 terrain ordering snapshot (legacy compat)"
```

---

## Task 7: Legacy remap for worlds with terrain data but no palette

**Files:**
- Modify: `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/Dimension.java` (the `wpVersion < 13` block from Task 5)
- Test: `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/HytaleTerrainPaletteRoundTripTest.java` (extend)

- [ ] **Step 1: Write the failing test**

```java
    @Test
    public void legacyWorldWithoutPaletteIsRemappedViaPreTp57Snapshot() throws Exception {
        Dimension dim = newTestDimension();
        org.pepsoft.worldpainter.Tile tile = dim.getTileForEditing(0, 0);
        // Find the pre-TP-57 index that meant Soil_Clay and store THAT raw value,
        // with NO palette and a pre-3 version (a genuine legacy save).
        Map<Integer, String> snap = HytaleTerrainV2Snapshot.asPalette();
        int oldClayIndex = snap.entrySet().stream()
                .filter(e -> "Soil_Clay".equals(e.getValue())).findFirst().orElseThrow().getKey();
        HytaleTerrainLayer.setTerrainIndex(tile, 3, 3, oldClayIndex);
        dim.setSuppressTerrainPaletteSnapshotForTests(true);   // emulate an old save
        dim.putMetadataForTests("hytaleTerrainVersion", 2);
        dim.removeMetadataForTests("hytaleTerrainPalette");

        Dimension reloaded = (Dimension) deserialize(serialize(dim));
        int idx = HytaleTerrainLayer.getTerrainIndex(reloaded.getTile(0, 0), 3, 3);
        assertEquals("Soil_Clay", HytaleTerrain.getByLayerIndex(idx).getBlock().id);
    }
```

Add package-private `void removeMetadataForTests(String key)` to `Dimension`.

> **Why this is safe against double-migration:** the test stores a *pre-TP-57* raw index and expects it to land on clay's *current* index. Because the suppress flag prevents the save from writing a palette, the world deserializes as a true legacy world.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -q -pl WPCore test -Dtest=HytaleTerrainPaletteRoundTripTest#legacyWorldWithoutPaletteIsRemappedViaPreTp57Snapshot`
Expected: FAIL — no legacy branch yet, index unchanged.

- [ ] **Step 3: Add the legacy branch**

In the `wpVersion < 13` block (Task 5), replace the trailing comment `// (Legacy worlds without a palette are handled in Task 7.)` with:

```java
            else if (htv < 3) {
                // Legacy world saved before the self-describing palette existed.
                // Interpret stored ordinals against the frozen pre-TP-57 ordering
                // and remap by block id. Best-effort (see HytaleTerrainV2Snapshot).
                boolean hasTerrain = false;
                for (Tile t : tiles.values()) {
                    if (org.pepsoft.worldpainter.hytale.HytaleTerrainLayer.hasTerrainData(t)) {
                        hasTerrain = true;
                        break;
                    }
                }
                if (hasTerrain) {
                    logger.info("Applying legacy (pre-TP-57) Hytale terrain remap");
                    org.pepsoft.worldpainter.hytale.HytaleTerrainPalette.remapTiles(
                            tiles.values(),
                            org.pepsoft.worldpainter.hytale.HytaleTerrainV2Snapshot.asPalette());
                }
            }
            if (managedAttributes == null) {
                managedAttributes = new HashMap<>();
            }
            managedAttributes.put("hytaleTerrainVersion", 3);
```

> Ordering note: the V0/V1 migration (`wpVersion < 11`) runs first and rewrites stored indices into the *then-current* ordering. For a world that genuinely predates TP-57, "then-current" == pre-TP-57, so the snapshot matches. Keep the `wpVersion < 11` block unchanged.

- [ ] **Step 4: Run + commit**

Run: `mvn -q -pl WPCore test -Dtest=HytaleTerrainPaletteRoundTripTest`
Expected: PASS (all methods).

```bash
git add WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/Dimension.java \
        WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/HytaleTerrainPaletteRoundTripTest.java
git commit -m "feat(hytale): legacy pre-TP-57 terrain remap on load (TP-57 compat)"
```

---

## Task 8: Full regression run + YouTrack

**Files:** none (verification)

- [ ] **Step 1: Run the Hytale + dimension test suites**

Run: `mvn -q -pl WPCore test -Dtest=Hytale*,Dimension*`
Expected: PASS. Investigate any failure before proceeding (especially `HytaleRoundTripTest`, `HytaleTerrainEnumIntegrityTest`).

- [ ] **Step 2: Confirm no behavioural change for non-Hytale worlds**

Manually verify (read): the new `writeObject` snapshot is gated by `hasHytaleTerrainData()`, and the `readObject` `wpVersion < 13` block is a no-op when there is no terrain data and no palette. A pure-Minecraft world writes no palette and skips both branches.

- [ ] **Step 3: Update YouTrack**

Create/locate the ticket for this regression (the user reported clay→brick). Comment with: root cause (registry-ordinal terrain storage shifted by TP-57's +304 blocks), the two-part fix (self-describing palette + pre-TP-57 legacy remap), the known limitation (worlds saved in the narrow post-TP-57/pre-fix window may be partially mis-remapped; re-saving fixes them going forward), and set Stage → Review (per project workflow, resolved issues go to "Review", not "Done").

---

## Self-Review Notes

- **Spec coverage:** self-describing palette (Tasks 1–5) + legacy remap (Tasks 6–7) — both halves of the approved approach are covered. Versioning via `hytaleTerrainVersion` (3) and `CURRENT_WP_VERSION` (13). No-harm for non-Hytale worlds (Task 8 Step 2).
- **Known limitation (documented, intentional):** one frozen snapshot can only target one era; we target pre-TP-57 (the reported regression and dominant population). Worlds last saved in the brief post-TP-57/pre-fix window lack a marker distinguishing them and may be mis-remapped on first load; re-saving makes them self-describing. If this window matters, a follow-up could add an explicit per-save ordering hash to disambiguate.
- **Type consistency:** `currentTerrainPaletteBlockIds(): List<String>`, `currentPalette()/asPalette(): Map<Integer,String>`, `remapIndex(int, Map): int`, `remapTiles(Collection, Map): int`. Metadata keys: `"hytaleTerrainPalette"`, `"hytaleTerrainVersion"`. These names are used identically across Tasks 2, 4, 5, 6, 7.
- **Tile/Dimension constructors** in tests are intentionally deferred to "match nearest existing test" because WPCore fixtures vary; this is the one place the executor must copy an existing pattern rather than invent.
