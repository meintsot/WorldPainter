# Hytale Exact Prefab Placement — Plan A: Model & Export (WPCore)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a persistent, rotation-aware "exact prefab placement" backend to WorldPainter's Hytale support — placements stored on `Dimension`, realized at export as baked blocks (with a marker fallback) at exact coordinates and arbitrary yaw.

**Architecture:** A serializable `HytalePrefabPlacement` value object lives in a new `List` on `Dimension` (persisted via a `wpVersion` migration). At export, `HytaleWorldExporter` enqueues placements intersecting each region and pastes them via a rotation-aware `HytalePrefabPaster` overload; rotation math is a pure `PrefabRotator` (cardinal = exact, off-cardinal = destination-grid resample) reusing a shared `HytaleRotations` yaw helper extracted verbatim from `Material`. Unresolvable prefabs fall back to a rotation-bearing `PrefabMarker`.

**Tech Stack:** Java 17, Maven (JDK 17 toolchain), JUnit 4, MongoDB BSON driver (Hytale chunk serialization). GUI is **out of scope** for this plan (see Plan B).

**Spec:** `docs/superpowers/specs/2026-06-06-hytale-exact-prefab-placement-design.md`

**Conventions / commands (run from the worktree root):**
- Build everything once: `mvn -f WorldPainter/pom.xml -DskipTests=true -pl WPCore -am install`
- Run one WPCore test class: `mvn -f WorldPainter/pom.xml -pl WPCore test -Dtest=<ClassName> -q`
- WorldPainter coordinate terms in this plan: `x` = east–west, `y` = north–south (both horizontal), `height` = vertical. At export these map to the paster's `anchorWorldX = x`, `anchorWorldZ = y`, `anchorY = height`.

---

## Task 0: Establish clean baseline

**Files:** none (verification only)

- [ ] **Step 1: Build WPCore from the worktree**

Run: `mvn -f WorldPainter/pom.xml -DskipTests=true -pl WPCore -am install`
Expected: `BUILD SUCCESS`. (First run downloads deps; JIDE eval jars are only needed by WPGUI, not WPCore.)

- [ ] **Step 2: Run an existing Hytale test to confirm the test harness works**

Run: `mvn -f WorldPainter/pom.xml -pl WPCore test -Dtest=HytaleChunkTest -q`
Expected: `BUILD SUCCESS`, tests run with 0 failures. If this fails for environment reasons (missing toolchain/deps), stop and report before continuing.

---

## Task 1: `HytalePrefabPlacement` value object

**Files:**
- Create: `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytalePrefabPlacement.java`
- Test: `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/HytalePrefabPlacementTest.java`

- [ ] **Step 1: Write the failing test**

```java
package org.pepsoft.worldpainter.hytale;

import org.junit.Test;
import java.io.*;
import static org.junit.Assert.*;

public class HytalePrefabPlacementTest {
    @Test
    public void normalizesRotationIntoZeroTo360() {
        HytalePrefabPlacement p = new HytalePrefabPlacement(
                1L, "Prefabs/Trees/Oak.prefab.json", "Oak", 10, 20, 70, false, -90.0);
        assertEquals(270.0, p.getRotationDegrees(), 1.0e-9);
    }

    @Test
    public void withRotationKeepsIdentityAndPosition() {
        HytalePrefabPlacement p = new HytalePrefabPlacement(
                7L, "p.prefab.json", "P", 3, 4, null, true, 0.0);
        HytalePrefabPlacement r = p.withRotation(45.0);
        assertEquals(7L, r.getId());
        assertEquals(3, r.getX());
        assertEquals(4, r.getY());
        assertTrue(r.isSnapToSurface());
        assertEquals(45.0, r.getRotationDegrees(), 1.0e-9);
        assertEquals(p, r); // equality is by id
    }

    @Test
    public void serializesRoundTrip() throws Exception {
        HytalePrefabPlacement p = new HytalePrefabPlacement(
                42L, "Prefabs/Spawn/Camp.prefab.json", "Camp", -100, 250, 64, false, 137.5);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(p);
        }
        HytalePrefabPlacement back;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            back = (HytalePrefabPlacement) in.readObject();
        }
        assertEquals(42L, back.getId());
        assertEquals("Prefabs/Spawn/Camp.prefab.json", back.getPrefabPath());
        assertEquals("Camp", back.getPrefabName());
        assertEquals(-100, back.getX());
        assertEquals(250, back.getY());
        assertEquals(Integer.valueOf(64), back.getHeight());
        assertFalse(back.isSnapToSurface());
        assertEquals(137.5, back.getRotationDegrees(), 1.0e-9);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -f WorldPainter/pom.xml -pl WPCore test -Dtest=HytalePrefabPlacementTest -q`
Expected: FAIL — compilation error, `HytalePrefabPlacement` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package org.pepsoft.worldpainter.hytale;

import java.io.Serializable;

/**
 * An exact, user-authored placement of a single Hytale prefab at a specific
 * map location, with explicit (or surface-snapped) height and free yaw
 * rotation. Stored on the {@code Dimension} and realized at export by
 * {@code HytaleWorldExporter}. Immutable; edits produce copies via the
 * {@code with*} helpers.
 *
 * <p>Coordinates are in WorldPainter terms: {@code x} (east-west) and
 * {@code y} (north-south) are horizontal; {@code height} is vertical.</p>
 */
public final class HytalePrefabPlacement implements Serializable {
    private static final long serialVersionUID = 1L;

    private final long id;
    private final String prefabPath;
    private final String prefabName;
    private final int x;
    private final int y;
    private final Integer height;        // explicit vertical; may be null only when snapToSurface
    private final boolean snapToSurface; // if true, height resolved from terrain at export
    private final double rotationDegrees; // 0..360, yaw about the vertical axis

    public HytalePrefabPlacement(long id, String prefabPath, String prefabName,
                                 int x, int y, Integer height, boolean snapToSurface,
                                 double rotationDegrees) {
        this.id = id;
        this.prefabPath = prefabPath;
        this.prefabName = prefabName;
        this.x = x;
        this.y = y;
        this.height = height;
        this.snapToSurface = snapToSurface;
        this.rotationDegrees = normalizeDegrees(rotationDegrees);
    }

    public long getId() { return id; }
    public String getPrefabPath() { return prefabPath; }
    public String getPrefabName() { return prefabName; }
    public int getX() { return x; }
    public int getY() { return y; }
    public Integer getHeight() { return height; }
    public boolean isSnapToSurface() { return snapToSurface; }
    public double getRotationDegrees() { return rotationDegrees; }

    public HytalePrefabPlacement withPosition(int newX, int newY) {
        return new HytalePrefabPlacement(id, prefabPath, prefabName, newX, newY, height, snapToSurface, rotationDegrees);
    }

    public HytalePrefabPlacement withHeight(Integer newHeight, boolean newSnapToSurface) {
        return new HytalePrefabPlacement(id, prefabPath, prefabName, x, y, newHeight, newSnapToSurface, rotationDegrees);
    }

    public HytalePrefabPlacement withRotation(double newDegrees) {
        return new HytalePrefabPlacement(id, prefabPath, prefabName, x, y, height, snapToSurface, newDegrees);
    }

    private static double normalizeDegrees(double d) {
        double r = d % 360.0;
        if (r < 0.0) {
            r += 360.0;
        }
        return r;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (! (o instanceof HytalePrefabPlacement)) {
            return false;
        }
        return id == ((HytalePrefabPlacement) o).id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    @Override
    public String toString() {
        return "HytalePrefabPlacement{id=" + id + ", prefab='" + prefabName + "', x=" + x + ", y=" + y
                + ", height=" + height + ", snap=" + snapToSurface + ", rot=" + rotationDegrees + '}';
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -f WorldPainter/pom.xml -pl WPCore test -Dtest=HytalePrefabPlacementTest -q`
Expected: PASS, 3 tests, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytalePrefabPlacement.java \
        WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/HytalePrefabPlacementTest.java
git commit -m "feat(hytale): add HytalePrefabPlacement value object"
```

---

## Task 2: Store placements on `Dimension` (field + accessors + migration)

**Files:**
- Modify: `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/Dimension.java`
- Test: `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/DimensionPrefabPlacementsTest.java`

**Context:** `Dimension` is `Serializable` with `CURRENT_WP_VERSION = 13` (line ~2980), `wpVersion` field (line ~2938), a `readObject` migration ladder (starts line ~2542), a private `int changeNo` bumped on mutation, and a `List<Overlay> overlays` field (line ~2952) whose `addOverlay`/`removeOverlay` (lines ~1311-1327) are the mutator template. **`defaultReadObject` does not run field initializers**, so old worlds deserialize the new list as `null` — the migration must initialize it.

- [ ] **Step 1: Write the failing test**

```java
package org.pepsoft.worldpainter.hytale;

import org.junit.Test;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.TestData;

import java.awt.Rectangle;
import java.io.*;

import static org.junit.Assert.*;
import static org.pepsoft.worldpainter.Constants.TILE_SIZE;

public class DimensionPrefabPlacementsTest {

    private static Dimension roundTrip(Dimension dim) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(dim);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (Dimension) in.readObject();
        }
    }

    @Test
    public void emptyByDefault() {
        Dimension dim = TestData.createDimension(new Rectangle(0, 0, TILE_SIZE, TILE_SIZE), 64);
        assertNotNull(dim.getHytalePrefabPlacements());
        assertTrue(dim.getHytalePrefabPlacements().isEmpty());
    }

    @Test
    public void addRemoveReplace() {
        Dimension dim = TestData.createDimension(new Rectangle(0, 0, TILE_SIZE, TILE_SIZE), 64);
        HytalePrefabPlacement p = new HytalePrefabPlacement(1L, "a.prefab.json", "A", 5, 6, 70, false, 0.0);
        dim.addHytalePrefabPlacement(p);
        assertEquals(1, dim.getHytalePrefabPlacements().size());

        HytalePrefabPlacement moved = p.withPosition(9, 9);
        dim.replaceHytalePrefabPlacement(p, moved);   // replace matches by id (equals)
        assertEquals(9, dim.getHytalePrefabPlacements().get(0).getX());

        assertTrue(dim.removeHytalePrefabPlacement(moved));
        assertTrue(dim.getHytalePrefabPlacements().isEmpty());
    }

    @Test
    public void survivesSerialization() throws Exception {
        Dimension dim = TestData.createDimension(new Rectangle(0, 0, TILE_SIZE, TILE_SIZE), 64);
        dim.addHytalePrefabPlacement(
                new HytalePrefabPlacement(11L, "Prefabs/x.prefab.json", "X", 12, 34, null, true, 90.0));
        Dimension back = roundTrip(dim);
        assertEquals(1, back.getHytalePrefabPlacements().size());
        HytalePrefabPlacement p = back.getHytalePrefabPlacements().get(0);
        assertEquals("Prefabs/x.prefab.json", p.getPrefabPath());
        assertEquals(12, p.getX());
        assertTrue(p.isSnapToSurface());
        assertEquals(90.0, p.getRotationDegrees(), 1.0e-9);
    }

    @Test
    public void returnedListIsUnmodifiable() {
        Dimension dim = TestData.createDimension(new Rectangle(0, 0, TILE_SIZE, TILE_SIZE), 64);
        try {
            dim.getHytalePrefabPlacements().add(
                    new HytalePrefabPlacement(2L, "b.prefab.json", "B", 0, 0, 0, false, 0.0));
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // good
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -f WorldPainter/pom.xml -pl WPCore test -Dtest=DimensionPrefabPlacementsTest -q`
Expected: FAIL — `getHytalePrefabPlacements`/`addHytalePrefabPlacement` not defined.

- [ ] **Step 3a: Add the import (if not already present)**

In `Dimension.java`, ensure these imports exist (add only the missing ones):
```java
import java.util.Collections;
import org.pepsoft.worldpainter.hytale.HytalePrefabPlacement;
```

- [ ] **Step 3b: Add the field** next to the `private List<Overlay> overlays = new ArrayList<>();` declaration (~line 2952):

```java
private List<HytalePrefabPlacement> hytalePrefabPlacements = new ArrayList<>();
```

- [ ] **Step 3c: Add accessors** near the overlay accessors (`getOverlays`, `addOverlay`, `removeOverlay`, ~lines 1307-1327):

```java
public List<HytalePrefabPlacement> getHytalePrefabPlacements() {
    return Collections.unmodifiableList(hytalePrefabPlacements);
}

public void addHytalePrefabPlacement(HytalePrefabPlacement placement) {
    hytalePrefabPlacements.add(placement);
    changeNo++;
}

public boolean removeHytalePrefabPlacement(HytalePrefabPlacement placement) {
    final boolean removed = hytalePrefabPlacements.remove(placement);
    if (removed) {
        changeNo++;
    }
    return removed;
}

public void replaceHytalePrefabPlacement(HytalePrefabPlacement oldPlacement, HytalePrefabPlacement newPlacement) {
    final int index = hytalePrefabPlacements.indexOf(oldPlacement);
    if (index >= 0) {
        hytalePrefabPlacements.set(index, newPlacement);
        changeNo++;
    }
}
```

- [ ] **Step 3d: Add the migration.** Find the **last** `if (wpVersion < N) { ... }` block in `readObject` (the highest N; the spec references `wpVersion < 12` at line ~2756, there may be a `< 13`). Immediately after that final block, add:

```java
if (wpVersion < 14) {
    if (hytalePrefabPlacements == null) {
        hytalePrefabPlacements = new ArrayList<>();
    }
}
```

- [ ] **Step 3e: Bump the version constant** (~line 2980):

```java
private static final int CURRENT_WP_VERSION = 14;
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -f WorldPainter/pom.xml -pl WPCore test -Dtest=DimensionPrefabPlacementsTest -q`
Expected: PASS, 4 tests, 0 failures.

- [ ] **Step 5: Regression-check Dimension serialization didn't break existing round-trip tests**

Run: `mvn -f WorldPainter/pom.xml -pl WPCore test -Dtest=HytaleRoundTripTest,HytaleTerrainSaveSnapshotTest -q`
Expected: PASS (no regressions from the version bump / new field).

- [ ] **Step 6: Commit**

```bash
git add WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/Dimension.java \
        WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/DimensionPrefabPlacementsTest.java
git commit -m "feat(hytale): persist exact prefab placements on Dimension (wpVersion 14)"
```

---

## Task 3: Extract shared `HytaleRotations` yaw helper from `Material`

**Files:**
- Create: `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleRotations.java`
- Modify: `WorldPainter/WPCore/src/main/java/org/pepsoft/minecraft/Material.java` (`rotateHytaleRotation`, ~lines 752-769)
- Test: `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/HytaleRotationsTest.java`

**Context:** `Material.rotateHytaleRotation(String, int)` currently decodes `roll=rot>>4; pitch=(rot>>2)&3; yaw=rot&3;`, applies `yaw = ((yaw - (steps % 4)) + 4) % 4;`, recomposes `(roll<<4)|(pitch<<2)|yaw`. We extract this **verbatim** into a reusable helper so `PrefabRotator` transforms block facings identically to the shipped prefab-layer rotation. Do **not** change the formula — only relocate it.

- [ ] **Step 1: Write the failing test** (pins the exact existing behavior)

```java
package org.pepsoft.worldpainter.hytale;

import org.junit.Test;
import static org.junit.Assert.*;

public class HytaleRotationsTest {
    @Test
    public void rotateRawMatchesMaterialFormula() {
        // yaw lives in bits 0-1; transform is (yaw - steps + 4) % 4, roll/pitch unchanged.
        // rot=1 (yaw=1), 1 step  -> yaw=0  => 0
        assertEquals(0, HytaleRotations.rotateRaw(1, 1));
        // rot=1 (yaw=1), 2 steps -> yaw=3  => 3
        assertEquals(3, HytaleRotations.rotateRaw(1, 2));
        // rot=0, 1 step -> yaw=3 => 3
        assertEquals(3, HytaleRotations.rotateRaw(0, 1));
        // roll/pitch preserved: rot=0b110110=54 (roll=3,pitch=1,yaw=2), 1 step -> yaw=1 => 0b110101=53
        assertEquals(53, HytaleRotations.rotateRaw(54, 1));
        // four steps == identity
        for (int r = 0; r <= 63; r++) {
            assertEquals(r, HytaleRotations.rotateRaw(r, 4));
        }
        // out-of-range passthrough
        assertEquals(-5, HytaleRotations.rotateRaw(-5, 1));
        assertEquals(99, HytaleRotations.rotateRaw(99, 1));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -f WorldPainter/pom.xml -pl WPCore test -Dtest=HytaleRotationsTest -q`
Expected: FAIL — `HytaleRotations` does not exist.

- [ ] **Step 3a: Create the helper**

```java
package org.pepsoft.worldpainter.hytale;

/**
 * Shared Hytale block-rotation math. The 0-63 rotation value encodes three
 * axes (2 bits each); {@link #rotateRaw(int, int)} transforms only the yaw
 * component, matching {@code Material.rotate(steps, …)}'s {@code hytale_rotation}
 * handling — the convention already used by prefab-layer rotation. Reused by
 * {@code PrefabRotator} so exact-placement rotation behaves identically.
 */
public final class HytaleRotations {
    private HytaleRotations() {
        // utility class
    }

    /**
     * Rotate a Hytale 0-63 rotation value by {@code steps} 90° turns, transforming
     * only the yaw component. Verbatim port of {@code Material.rotateHytaleRotation}.
     * Values outside 0-63 are returned unchanged.
     */
    public static int rotateRaw(int rotation, int steps) {
        if ((rotation < 0) || (rotation > 63)) {
            return rotation;
        }
        final int roll = rotation >> 4;
        final int pitch = (rotation >> 2) & 3;
        int yaw = rotation & 3;
        yaw = ((yaw - (steps % 4)) + 4) % 4;
        return (roll << 4) | (pitch << 2) | yaw;
    }
}
```

- [ ] **Step 3b: Refactor `Material.rotateHytaleRotation` to delegate** (DRY — single source of truth). Replace the decode/transform/recompose body with a call to the helper, preserving the `withProperty` wrapping:

```java
private Material rotateHytaleRotation(String hytaleRotStr, int steps) {
    try {
        int rot = Integer.parseInt(hytaleRotStr);
        if ((rot >= 0) && (rot <= 63)) {
            int newRot = org.pepsoft.worldpainter.hytale.HytaleRotations.rotateRaw(rot, steps);
            if (newRot != rot) {
                return withProperty(HYTALE_ROTATION_PROPERTY, Integer.toString(newRot));
            }
        }
    } catch (NumberFormatException e) {
        // Ignore invalid rotation value
    }
    return this;
}
```

- [ ] **Step 4: Run tests to verify the helper passes and Material behavior is unchanged**

Run: `mvn -f WorldPainter/pom.xml -pl WPCore test -Dtest=HytaleRotationsTest -q`
Expected: PASS.
Run: `mvn -f WorldPainter/pom.xml -pl WPCore test -Dtest=*Rotation*,*Mirror* -q`
Expected: PASS — existing rotation/mirror tests still green (confirms the refactor is behavior-preserving). If no such tests match, run the full Hytale package: `mvn -f WorldPainter/pom.xml -pl WPCore test -Dtest=org.pepsoft.worldpainter.hytale.* -q`.

- [ ] **Step 5: Commit**

```bash
git add WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleRotations.java \
        WorldPainter/WPCore/src/main/java/org/pepsoft/minecraft/Material.java \
        WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/HytaleRotationsTest.java
git commit -m "refactor(hytale): extract shared HytaleRotations.rotateRaw used by Material.rotate"
```

---

## Task 4: `PrefabRotator` — rotate prefab block data (cardinal exact, off-cardinal resample)

**Files:**
- Create: `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/prefab/PrefabRotator.java`
- Test: `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/prefab/PrefabRotatorTest.java`

**Context:** `HytalePrefabPaster` declares package-private static classes `PrefabBlockData{anchorX,anchorY,anchorZ, List<PrefabBlock> blocks, List<PrefabFluid> fluids}`, `PrefabBlock{x,y,z,blockName,rotation}`, `PrefabFluid{x,y,z,fluidName,level}` — `x`/`z` horizontal, `y` vertical, with package-private constructors. `PrefabRotator` lives in the **same package** to use them. Rotation is yaw about the vertical axis, in the `(x,z)` plane about `(anchorX, anchorZ)`; facings use `HytaleRotations.rotateRaw`.

**Direction convention (pinned by tests; the one tunable knob):** position rotation uses the standard matrix `dx = ox·cosθ − oz·sinθ; dz = ox·sinθ + oz·cosθ`. At θ=90° this gives `(ox,oz) → (−oz, ox)`. Facings use `rotateRaw(rot, steps)`. If a directional prefab (branches/stairs) visually points the wrong way at 90° during Plan B manual testing, the **only** change needed is to flip facing steps to `(4 − steps) % 4` here — documented at the call site.

- [ ] **Step 1: Write the failing test**

```java
package org.pepsoft.worldpainter.hytale.prefab;

import org.junit.Test;
import org.pepsoft.worldpainter.hytale.prefab.HytalePrefabPaster.PrefabBlock;
import org.pepsoft.worldpainter.hytale.prefab.HytalePrefabPaster.PrefabBlockData;

import java.util.*;

import static org.junit.Assert.*;

public class PrefabRotatorTest {

    private static PrefabBlockData data(List<PrefabBlock> blocks) {
        return new PrefabBlockData(0, 0, 0, blocks, Collections.emptyList());
    }

    private static Map<Long, PrefabBlock> index(PrefabBlockData d) {
        Map<Long, PrefabBlock> m = new HashMap<>();
        for (PrefabBlock b : d.blocks) {
            m.put((((long) b.x) << 32) ^ (b.z & 0xFFFFFFFFL), b);
        }
        return m;
    }

    @Test
    public void zeroDegreesIsNoOp() {
        PrefabBlockData d = data(Collections.singletonList(new PrefabBlock(2, 5, 3, "Rock_Stone", 1)));
        assertSame(d, PrefabRotator.rotate(d, 0.0));
        assertSame(d, PrefabRotator.rotate(d, 360.0));
    }

    @Test
    public void cardinal90MovesPositionsAndYaw() {
        // single block at offset (x=2,z=0), yaw rot=0
        PrefabBlockData d = data(Collections.singletonList(new PrefabBlock(2, 5, 0, "Rock_Stone", 0)));
        PrefabBlockData r = PrefabRotator.rotate(d, 90.0);
        PrefabBlock b = r.blocks.get(0);
        // (ox,oz)=(2,0) -> (-0, 2) -> (0,2)
        assertEquals(0, b.x);
        assertEquals(2, b.z);
        assertEquals(5, b.y);                 // vertical unchanged
        assertEquals(3, b.rotation);          // rotateRaw(0,1) = yaw 3
    }

    @Test
    public void fourCardinalStepsRestoreOriginal() {
        PrefabBlockData d = data(Arrays.asList(
                new PrefabBlock(2, 0, 0, "A", 1),
                new PrefabBlock(0, 0, 3, "B", 2),
                new PrefabBlock(-1, 1, 2, "C", 0)));
        PrefabBlockData r = PrefabRotator.rotate(
                PrefabRotator.rotate(PrefabRotator.rotate(PrefabRotator.rotate(d, 90), 90), 90), 90);
        Map<Long, PrefabBlock> before = index(d), after = index(r);
        assertEquals(before.keySet(), after.keySet());
        for (Long k : before.keySet()) {
            assertEquals(before.get(k).blockName, after.get(k).blockName);
            assertEquals(before.get(k).rotation, after.get(k).rotation);
        }
    }

    @Test
    public void freeAngleProducesNoHolesOverFootprint() {
        // a solid 5x5 single-layer slab; after 37° every source cell must map somewhere,
        // and the destination must have no interior holes (destination-grid sampling).
        List<PrefabBlock> blocks = new ArrayList<>();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                blocks.add(new PrefabBlock(x, 0, z, "Rock_Stone", 0));
            }
        }
        PrefabBlockData r = PrefabRotator.rotate(data(blocks), 37.0);
        assertFalse(r.blocks.isEmpty());
        // destination is contiguous: no (x,z) gap fully surrounded by filled cells
        Set<Long> filled = new HashSet<>();
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (PrefabBlock b : r.blocks) {
            filled.add((((long) b.x) << 32) ^ (b.z & 0xFFFFFFFFL));
            minX = Math.min(minX, b.x); maxX = Math.max(maxX, b.x);
            minZ = Math.min(minZ, b.z); maxZ = Math.max(maxZ, b.z);
        }
        for (int x = minX + 1; x < maxX; x++) {
            for (int z = minZ + 1; z < maxZ; z++) {
                long k = (((long) x) << 32) ^ (z & 0xFFFFFFFFL);
                boolean hasLeft = filled.contains((((long) (x - 1)) << 32) ^ (z & 0xFFFFFFFFL));
                boolean hasRight = filled.contains((((long) (x + 1)) << 32) ^ (z & 0xFFFFFFFFL));
                if (hasLeft && hasRight) {
                    assertTrue("interior hole at " + x + "," + z, filled.contains(k));
                }
            }
        }
    }

    @Test
    public void freeAngleSnapsYawToNearestCardinal() {
        PrefabBlockData d = data(Collections.singletonList(new PrefabBlock(0, 0, 0, "A", 0)));
        // 80° rounds to 1 step -> rotateRaw(0,1)=3
        PrefabBlockData r = PrefabRotator.rotate(d, 80.0);
        assertEquals(3, r.blocks.get(0).rotation);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -f WorldPainter/pom.xml -pl WPCore test -Dtest=PrefabRotatorTest -q`
Expected: FAIL — `PrefabRotator` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package org.pepsoft.worldpainter.hytale.prefab;

import org.pepsoft.worldpainter.hytale.HytaleRotations;
import org.pepsoft.worldpainter.hytale.prefab.HytalePrefabPaster.PrefabBlock;
import org.pepsoft.worldpainter.hytale.prefab.HytalePrefabPaster.PrefabBlockData;
import org.pepsoft.worldpainter.hytale.prefab.HytalePrefabPaster.PrefabFluid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rotates {@link PrefabBlockData} by an arbitrary yaw angle about the vertical
 * axis, in the horizontal (x,z) plane about the prefab anchor.
 *
 * <ul>
 *   <li>Cardinal angles (0/90/180/270) rotate positions exactly (integer turns)
 *       and transform each block's facing via {@link HytaleRotations#rotateRaw}.</li>
 *   <li>Off-cardinal angles use <b>destination-grid nearest-neighbour</b> resampling
 *       (iterate destination cells, inverse-rotate to the nearest source cell) so the
 *       footprint has no holes; each emitted block's facing snaps to the nearest
 *       cardinal turn.</li>
 * </ul>
 */
public final class PrefabRotator {
    private static final double EPS = 1.0e-6;

    private PrefabRotator() {
        // utility
    }

    public static PrefabBlockData rotate(PrefabBlockData data, double degrees) {
        final double norm = ((degrees % 360.0) + 360.0) % 360.0;
        if (norm < EPS) {
            return data; // exact no-op
        }
        final int steps = ((int) Math.round(norm / 90.0)) % 4;
        final boolean cardinal = Math.abs(norm - (steps * 90.0)) < EPS;
        return cardinal ? rotateCardinal(data, steps) : rotateFree(data, norm, steps);
    }

    private static PrefabBlockData rotateCardinal(PrefabBlockData data, int steps) {
        final List<PrefabBlock> outBlocks = new ArrayList<>(data.blocks.size());
        for (PrefabBlock b : data.blocks) {
            final int[] p = rotateOffset(b.x - data.anchorX, b.z - data.anchorZ, steps);
            // NOTE: facing uses rotateRaw(rot, steps); if directional blocks point wrong
            // at 90° in manual testing, change `steps` here to `((4 - steps) % 4)`.
            outBlocks.add(new PrefabBlock(data.anchorX + p[0], b.y, data.anchorZ + p[1],
                    b.blockName, HytaleRotations.rotateRaw(b.rotation, steps)));
        }
        final List<PrefabFluid> outFluids = new ArrayList<>(data.fluids.size());
        for (PrefabFluid f : data.fluids) {
            final int[] p = rotateOffset(f.x - data.anchorX, f.z - data.anchorZ, steps);
            outFluids.add(new PrefabFluid(data.anchorX + p[0], f.y, data.anchorZ + p[1], f.fluidName, f.level));
        }
        return new PrefabBlockData(data.anchorX, data.anchorY, data.anchorZ, outBlocks, outFluids);
    }

    /** 90°-step clockwise rotation of an (x,z) offset. At 90°: (x,z) -> (-z, x). */
    private static int[] rotateOffset(int x, int z, int steps) {
        switch (((steps % 4) + 4) % 4) {
            case 1:  return new int[]{-z,  x};
            case 2:  return new int[]{-x, -z};
            case 3:  return new int[]{ z, -x};
            default: return new int[]{ x,  z};
        }
    }

    private static PrefabBlockData rotateFree(PrefabBlockData data, double degrees, int nearestSteps) {
        final double rad = Math.toRadians(degrees);
        final double cos = Math.cos(rad), sin = Math.sin(rad);

        // Group source blocks by vertical layer; resample each layer independently
        // (yaw doesn't move blocks between layers).
        final Map<Integer, Map<Long, PrefabBlock>> byLayer = new HashMap<>();
        for (PrefabBlock b : data.blocks) {
            byLayer.computeIfAbsent(b.y, k -> new HashMap<>())
                    .put(key(b.x - data.anchorX, b.z - data.anchorZ), b);
        }

        final List<PrefabBlock> outBlocks = new ArrayList<>();
        for (Map.Entry<Integer, Map<Long, PrefabBlock>> entry : byLayer.entrySet()) {
            final int y = entry.getKey();
            final Map<Long, PrefabBlock> src = entry.getValue();

            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
            for (PrefabBlock b : src.values()) {
                final int ox = b.x - data.anchorX, oz = b.z - data.anchorZ;
                final double dx = (ox * cos) - (oz * sin);
                final double dz = (ox * sin) + (oz * cos);
                minX = Math.min(minX, (int) Math.floor(dx)); maxX = Math.max(maxX, (int) Math.ceil(dx));
                minZ = Math.min(minZ, (int) Math.floor(dz)); maxZ = Math.max(maxZ, (int) Math.ceil(dz));
            }

            for (int dx = minX; dx <= maxX; dx++) {
                for (int dz = minZ; dz <= maxZ; dz++) {
                    // inverse rotation: source = R(-θ) · dest
                    final int sx = (int) Math.round((dx * cos) + (dz * sin));
                    final int sz = (int) Math.round((-dx * sin) + (dz * cos));
                    final PrefabBlock b = src.get(key(sx, sz));
                    if (b != null) {
                        outBlocks.add(new PrefabBlock(data.anchorX + dx, y, data.anchorZ + dz,
                                b.blockName, HytaleRotations.rotateRaw(b.rotation, nearestSteps)));
                    }
                }
            }
        }

        // Fluids are sparse; rotate each position with nearest-neighbour rounding.
        final List<PrefabFluid> outFluids = new ArrayList<>(data.fluids.size());
        for (PrefabFluid f : data.fluids) {
            final int ox = f.x - data.anchorX, oz = f.z - data.anchorZ;
            final int nx = (int) Math.round((ox * cos) - (oz * sin));
            final int nz = (int) Math.round((ox * sin) + (oz * cos));
            outFluids.add(new PrefabFluid(data.anchorX + nx, f.y, data.anchorZ + nz, f.fluidName, f.level));
        }
        return new PrefabBlockData(data.anchorX, data.anchorY, data.anchorZ, outBlocks, outFluids);
    }

    private static long key(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xFFFFFFFFL);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -f WorldPainter/pom.xml -pl WPCore test -Dtest=PrefabRotatorTest -q`
Expected: PASS, 5 tests, 0 failures.

> If `PrefabBlock`/`PrefabBlockData`/`PrefabFluid` or their constructors are not visible from this package, they are package-private nested classes of `HytalePrefabPaster` in the **same** package `org.pepsoft.worldpainter.hytale.prefab` — confirm the new files are in that package (they are per the paths above). Do not widen their visibility.

- [ ] **Step 5: Commit**

```bash
git add WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/prefab/PrefabRotator.java \
        WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/prefab/PrefabRotatorTest.java
git commit -m "feat(hytale): add PrefabRotator (cardinal-exact + free-angle resample)"
```

---

## Task 5: Rotation-aware `HytalePrefabPaster.paste` overload

**Files:**
- Modify: `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/prefab/HytalePrefabPaster.java`
- Test: `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/prefab/HytalePrefabPasterRotationTest.java`

**Context:** the multi-chunk `paste(Map, int worldX, int anchorY, int worldZ, int blockOffsetX, int blockOffsetZ, String prefabPath)` (lines 70-88) loads `PrefabBlockData` via `loadPrefab` and writes via the private `placeBlocksAndFluids(data, anchorY, locator)`. We add a new overload that applies `PrefabRotator.rotate` before placing, and make the existing method delegate with `0.0` so layer pastes are unchanged.

- [ ] **Step 1: Write the failing test** (synthetic prefab on disk — no real assets needed)

```java
package org.pepsoft.worldpainter.hytale.prefab;

import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.Rule;
import org.pepsoft.worldpainter.hytale.HytaleBlock;
import org.pepsoft.worldpainter.hytale.chunk.HytaleChunk;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class HytalePrefabPasterRotationTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private File writeSinglePrefab() throws Exception {
        // anchor (0,0,0); one block at (x=2,y=0,z=0) named Rock_Stone, rotation 0
        String json = "{ \"anchorX\":0, \"anchorY\":0, \"anchorZ\":0, "
                + "\"blocks\": [ { \"x\":2, \"y\":0, \"z\":0, \"name\":\"Rock_Stone\", \"rotation\":0 } ] }";
        File assets = tmp.newFolder("HytaleAssets");
        File dir = new File(assets, "Server/Prefabs/Test");
        assertTrue(dir.mkdirs());
        File file = new File(dir, "single.prefab.json");
        Files.write(file.toPath(), json.getBytes(StandardCharsets.UTF_8));
        return assets;
    }

    private static long chunkKey(int cx, int cz) {
        return (((long) cx) << 32) ^ (cz & 0xFFFFFFFFL);
    }

    @Test
    public void rotates90AboutAnchor() throws Exception {
        File assets = writeSinglePrefab();
        HytalePrefabPaster paster = new HytalePrefabPaster(assets);

        // single chunk covering origin; anchor at world (0,0), height 64, no centering offset
        HytaleChunk chunk = new HytaleChunk(0, 0, 0, 320);
        Map<Long, HytaleChunk> chunks = new HashMap<>();
        chunks.put(chunkKey(0, 0), chunk);

        boolean ok = paster.paste(chunks, 0, 64, 0, 0, 0,
                "Prefabs/Test/single.prefab.json", 90.0);
        assertTrue(ok);

        // unrotated block sits at offset (2,0); after 90° -> (0,2). At anchorY=64, prefab y=0 -> world 64.
        HytaleBlock atRotated = chunk.getHytaleBlock(0, 64, 2);
        assertNotNull(atRotated);
        assertEquals("Rock_Stone", atRotated.getId());
        // original offset cell should now be empty
        HytaleBlock atOriginal = chunk.getHytaleBlock(2, 64, 0);
        assertTrue(atOriginal == null || "Empty".equals(atOriginal.getId()));
    }

    @Test
    public void zeroRotationStillPastes() throws Exception {
        File assets = writeSinglePrefab();
        HytalePrefabPaster paster = new HytalePrefabPaster(assets);
        HytaleChunk chunk = new HytaleChunk(0, 0, 0, 320);
        Map<Long, HytaleChunk> chunks = new HashMap<>();
        chunks.put(chunkKey(0, 0), chunk);
        assertTrue(paster.paste(chunks, 0, 64, 0, 0, 0, "Prefabs/Test/single.prefab.json", 0.0));
        HytaleBlock at = chunk.getHytaleBlock(2, 64, 0);
        assertNotNull(at);
        assertEquals("Rock_Stone", at.getId());
    }
}
```

> Confirm `HytaleChunk`'s constructor signature and `getHytaleBlock(x,y,z)` accessor against the source (Task 0 saw `new HytaleChunk(0,0,0,320)` and `getHytaleBlock` exists). If the getter returns `Empty` rather than `null` for unset cells, the assertions above already tolerate both.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -f WorldPainter/pom.xml -pl WPCore test -Dtest=HytalePrefabPasterRotationTest -q`
Expected: FAIL — no `paste(..., double)` overload.

- [ ] **Step 3: Add the overload and delegate.** In `HytalePrefabPaster.java`, change the existing multi-chunk `paste(...)` (lines 70-88) so its body moves into a new 8-arg overload that rotates first, and the old 7-arg signature delegates:

```java
public boolean paste(Map<Long, HytaleChunk> chunksByCoords,
                     int anchorWorldX, int anchorY, int anchorWorldZ,
                     int blockOffsetX, int blockOffsetZ,
                     String prefabPath) {
    return paste(chunksByCoords, anchorWorldX, anchorY, anchorWorldZ,
            blockOffsetX, blockOffsetZ, prefabPath, 0.0);
}

/**
 * As {@link #paste(Map, int, int, int, int, int, String)} but rotates the prefab
 * by {@code rotationDegrees} (yaw) about its anchor before placing. Cardinal angles
 * are exact; off-cardinal angles are resampled (see {@link PrefabRotator}).
 */
public boolean paste(Map<Long, HytaleChunk> chunksByCoords,
                     int anchorWorldX, int anchorY, int anchorWorldZ,
                     int blockOffsetX, int blockOffsetZ,
                     String prefabPath, double rotationDegrees) {
    PrefabBlockData data = loadPrefab(prefabPath);
    if ((data == null) || data.blocks.isEmpty()) {
        return false;
    }
    PrefabBlockData rotated = PrefabRotator.rotate(data, rotationDegrees);
    placeBlocksAndFluids(rotated, anchorY, (offsetX, offsetZ) -> {
        int wpBX = anchorWorldX + offsetX;
        int wpBZ = anchorWorldZ + offsetZ;
        HytaleChunk chunk = lookupChunk(chunksByCoords, wpBX, wpBZ, blockOffsetX, blockOffsetZ);
        if (chunk == null) {
            return null;
        }
        int localX = Math.floorMod(wpBX + blockOffsetX, HytaleChunk.CHUNK_SIZE);
        int localZ = Math.floorMod(wpBZ + blockOffsetZ, HytaleChunk.CHUNK_SIZE);
        return new ChunkLocation(chunk, localX, localZ);
    });
    return true;
}
```

(The lambda body is identical to the original method's — just relocated into the rotation-aware overload. `loadPrefab` caches the unrotated data; `PrefabRotator.rotate` returns a fresh `PrefabBlockData`, so the cache is never mutated.)

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -f WorldPainter/pom.xml -pl WPCore test -Dtest=HytalePrefabPasterRotationTest -q`
Expected: PASS, 2 tests, 0 failures.

- [ ] **Step 5: Regression-check existing prefab paste tests**

Run: `mvn -f WorldPainter/pom.xml -pl WPCore test -Dtest=TpPrefabCrossChunkTest -q`
Expected: PASS — the delegating 7-arg path is unchanged for layer callers.

- [ ] **Step 6: Commit**

```bash
git add WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/prefab/HytalePrefabPaster.java \
        WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/prefab/HytalePrefabPasterRotationTest.java
git commit -m "feat(hytale): rotation-aware HytalePrefabPaster.paste overload"
```

---

## Task 6: Carry rotation on `PrefabMarker` (fallback) + BSON round-trip

**Files:**
- Modify: `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/chunk/HytaleChunk.java` (`PrefabMarker`, `addPrefabMarker`)
- Modify: `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/.../HytaleBsonChunkSerializer.java` (PrefabMarkers write, ~lines 215-230)
- Modify: `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/.../HytaleBsonChunkDeserializer.java` (PrefabMarkers read, ~lines 821-833)
- Test: `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/PrefabMarkerRotationBsonTest.java`

**Context:** `PrefabMarker{int x,y,z; String category, prefabPath}` with `addPrefabMarker(x,y,z,category,path)`. BSON writes `{x,y,z,category,path}` under `"PrefabMarkers"`; deserializer reads them. We add an **optional** `double rotation` (default 0.0 when absent → backward compatible). Keep the 5-arg `addPrefabMarker` working (existing callers in the exporter and deserializer).

- [ ] **Step 1: Write the failing test**

```java
package org.pepsoft.worldpainter.hytale;

import org.junit.Test;
import org.pepsoft.worldpainter.hytale.chunk.HytaleChunk;
// import the actual serializer/deserializer package as seen in source:
import org.pepsoft.worldpainter.hytale.bson.HytaleBsonChunkSerializer;     // ADJUST package to match source
import org.pepsoft.worldpainter.hytale.bson.HytaleBsonChunkDeserializer;   // ADJUST package to match source

import static org.junit.Assert.*;

public class PrefabMarkerRotationBsonTest {

    @Test
    public void rotationSurvivesBsonRoundTrip() throws Exception {
        HytaleChunk chunk = new HytaleChunk(0, 0, 0, 320);
        chunk.addPrefabMarker(1, 64, 2, "Oak", "Prefabs/Trees/Oak.prefab.json", 137.5);

        byte[] bytes = HytaleBsonChunkSerializer.serialize(chunk);          // ADJUST to real API
        HytaleChunk back = HytaleBsonChunkDeserializer.deserialize(bytes);  // ADJUST to real API

        assertEquals(1, back.getPrefabMarkers().size());
        HytaleChunk.PrefabMarker m = back.getPrefabMarkers().get(0);
        assertEquals(137.5, m.rotation, 1.0e-9);
        assertEquals("Prefabs/Trees/Oak.prefab.json", m.prefabPath);
    }

    @Test
    public void legacyMarkerWithoutRotationDefaultsToZero() {
        // 5-arg path (existing callers) must still compile and default rotation to 0.
        HytaleChunk chunk = new HytaleChunk(0, 0, 0, 320);
        chunk.addPrefabMarker(0, 0, 0, "X", "p.prefab.json");
        assertEquals(0.0, chunk.getPrefabMarkers().get(0).rotation, 1.0e-9);
    }
}
```

> **Before writing code:** open `HytaleBsonChunkSerializer`/`Deserializer` to confirm (a) their exact package, and (b) the exact serialize/deserialize entry-point method names/signatures, and adjust the two imports + the `serialize`/`deserialize` calls above to match. If (de)serialization needs more than the chunk (e.g. a context object), mirror what an existing serializer test (e.g. `HytaleRoundTripTest`) does.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -f WorldPainter/pom.xml -pl WPCore test -Dtest=PrefabMarkerRotationBsonTest -q`
Expected: FAIL — `addPrefabMarker(...,double)` and `PrefabMarker.rotation` don't exist.

- [ ] **Step 3a: Extend `PrefabMarker` + `addPrefabMarker`** in `HytaleChunk.java`:

```java
public static class PrefabMarker {
    public final int x, y, z;
    public final String category;
    public final String prefabPath;
    public final double rotation;

    public PrefabMarker(int x, int y, int z, String category, String prefabPath) {
        this(x, y, z, category, prefabPath, 0.0);
    }

    public PrefabMarker(int x, int y, int z, String category, String prefabPath, double rotation) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.category = category;
        this.prefabPath = prefabPath;
        this.rotation = rotation;
    }
}

public void addPrefabMarker(int x, int y, int z, String category, String prefabPath) {
    prefabMarkers.add(new PrefabMarker(x, y, z, category, prefabPath));
}

public void addPrefabMarker(int x, int y, int z, String category, String prefabPath, double rotation) {
    prefabMarkers.add(new PrefabMarker(x, y, z, category, prefabPath, rotation));
}
```

- [ ] **Step 3b: Write rotation in the serializer** (`HytaleBsonChunkSerializer`, the PrefabMarkers block ~lines 215-230). Add one line inside the per-marker `entry`:

```java
entry.put("path", new BsonString(pm.prefabPath));
entry.put("rotation", new BsonDouble(pm.rotation));   // NEW
```
(ensure `import org.bson.BsonDouble;` is present.)

- [ ] **Step 3c: Read rotation in the deserializer** (`HytaleBsonChunkDeserializer`, the PrefabMarkers block ~lines 821-833). Replace the `addPrefabMarker(...)` call so it reads an optional rotation defaulting to 0:

```java
double rotation = entry.containsKey("rotation") ? entry.getDouble("rotation").getValue() : 0.0;
chunk.addPrefabMarker(
        entry.getInt32("x").getValue(),
        entry.getInt32("y").getValue(),
        entry.getInt32("z").getValue(),
        entry.getString("category").getValue(),
        entry.getString("path").getValue(),
        rotation);
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -f WorldPainter/pom.xml -pl WPCore test -Dtest=PrefabMarkerRotationBsonTest -q`
Expected: PASS, 2 tests, 0 failures.

- [ ] **Step 5: Regression-check chunk round-trip**

Run: `mvn -f WorldPainter/pom.xml -pl WPCore test -Dtest=HytaleRoundTripTest -q`
Expected: PASS — existing markers (no rotation key) still deserialize (rotation defaults 0).

- [ ] **Step 6: Commit**

```bash
git add WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/chunk/HytaleChunk.java \
        WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/**/HytaleBsonChunk*.java \
        WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/PrefabMarkerRotationBsonTest.java
git commit -m "feat(hytale): carry optional rotation on PrefabMarker (BSON back-compatible)"
```

---

## Task 7: Realize placements at export in `HytaleWorldExporter`

**Files:**
- Modify: `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/export/HytaleWorldExporter.java`
- Test: `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/export/ExactPlacementExportTest.java`

**Context:** per region the exporter builds `chunksByCoords` and a `List<PendingPrefabPaste> regionPrefabPastes` (line ~559), enqueues layer pastes via `enqueuePrefabLayerPaste`, then (lines ~636-659) runs `prefabPaster.paste(chunksByCoords, pending.worldX, pending.anchorY, pending.worldZ, blockOffsetX, blockOffsetZ, pending.prefabPath)` with an `addPrefabMarker` fallback. `blockOffsetX/blockOffsetZ` are fields set at line ~366. The `Dimension` being exported is available (e.g. `dimension`); confirm the exact field/variable name in the region method's scope.

**Approach:** (1) add `double rotationDegrees` to `PendingPrefabPaste` (default 0 for layer pastes); (2) add a helper that enqueues every `Dimension` placement whose footprint could touch this region; (3) call the rotation-aware paste; (4) pass rotation to the fallback marker.

- [ ] **Step 1: Write the failing test.** A full region export is heavy; this test drives the new enqueue helper in isolation. Make the helper **package-private** so the test (same package) can call it directly.

```java
package org.pepsoft.worldpainter.hytale.export;

import org.junit.Test;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.TestData;
import org.pepsoft.worldpainter.hytale.HytalePrefabPlacement;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.pepsoft.worldpainter.Constants.TILE_SIZE;

public class ExactPlacementExportTest {

    @Test
    public void enqueuesPlacementInsideRegionBoundsWithRotation() {
        Dimension dim = TestData.createDimension(new Rectangle(0, 0, TILE_SIZE, TILE_SIZE), 64);
        dim.addHytalePrefabPlacement(
                new HytalePrefabPlacement(1L, "Prefabs/x.prefab.json", "X", 10, 20, null, true, 90.0));
        dim.addHytalePrefabPlacement(
                new HytalePrefabPlacement(2L, "Prefabs/y.prefab.json", "Y", 99999, 99999, 70, false, 0.0));

        List<HytaleWorldExporter.PendingPrefabPaste> out = new ArrayList<>();
        // region world bounds covering [0,128) x [0,128)
        HytaleWorldExporter.enqueueExactPlacements(dim, 0, 0, 128, 128, out);

        assertEquals(1, out.size());
        HytaleWorldExporter.PendingPrefabPaste p = out.get(0);
        assertEquals(10, p.worldX);
        assertEquals(20, p.worldZ);
        assertEquals(90.0, p.rotationDegrees, 1.0e-9);
        // snapToSurface -> anchorY resolved from terrain (64) + 1
        assertEquals(65, p.anchorY);
        assertEquals("Prefabs/x.prefab.json", p.prefabPath);
    }
}
```

> Adjust visibility/names if `PendingPrefabPaste` differs. If exposing the inner class to the test is undesirable, instead assert via a small package-private record the helper returns. The intent: prove (a) region filtering, (b) snap-to-surface height resolution, (c) rotation passthrough.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -f WorldPainter/pom.xml -pl WPCore test -Dtest=ExactPlacementExportTest -q`
Expected: FAIL — `enqueueExactPlacements` and `PendingPrefabPaste.rotationDegrees` don't exist.

- [ ] **Step 3a: Add `rotationDegrees` to `PendingPrefabPaste`** (lines ~1039-1054):

```java
private static final class PendingPrefabPaste {
    final int localX, anchorY, localZ, worldX, worldZ;
    final String prefabPath;
    final String prefabName;
    final double rotationDegrees;

    PendingPrefabPaste(int localX, int anchorY, int localZ,
                       int worldX, int worldZ, String prefabPath, String prefabName) {
        this(localX, anchorY, localZ, worldX, worldZ, prefabPath, prefabName, 0.0);
    }

    PendingPrefabPaste(int localX, int anchorY, int localZ,
                       int worldX, int worldZ, String prefabPath, String prefabName,
                       double rotationDegrees) {
        this.localX = localX;
        this.anchorY = anchorY;
        this.localZ = localZ;
        this.worldX = worldX;
        this.worldZ = worldZ;
        this.prefabPath = prefabPath;
        this.prefabName = prefabName;
        this.rotationDegrees = rotationDegrees;
    }
}
```

> The test references `HytaleWorldExporter.PendingPrefabPaste` and its fields; relax `PendingPrefabPaste` from `private` to package-private (`static final class`) so the same-package test can read it. Keep fields package-visible (drop no access — they're already non-private within the class; ensure the class itself is package-visible).

- [ ] **Step 3b: Add the enqueue helper** (package-private, static so it's unit-testable). Place it near `enqueuePrefabLayerPaste`:

```java
/**
 * Enqueue every exact prefab placement whose anchor falls within the given region
 * world bounds [minWorldX, maxWorldX) x [minWorldZ, maxWorldZ). Resolves snap-to-surface
 * height from the dimension. (Anchor-in-region is a simple, correct first cut; a future
 * refinement can widen to footprint-bounds intersection for border-spanning prefabs.)
 */
static void enqueueExactPlacements(Dimension dimension,
                                   int minWorldX, int minWorldZ, int maxWorldX, int maxWorldZ,
                                   List<PendingPrefabPaste> out) {
    for (HytalePrefabPlacement placement : dimension.getHytalePrefabPlacements()) {
        final int wx = placement.getX();
        final int wz = placement.getY(); // WP y is the second horizontal axis (paster's worldZ)
        if ((wx < minWorldX) || (wx >= maxWorldX) || (wz < minWorldZ) || (wz >= maxWorldZ)) {
            continue;
        }
        final int anchorY;
        if (placement.isSnapToSurface() || (placement.getHeight() == null)) {
            anchorY = dimension.getIntHeightAt(wx, wz) + 1;
        } else {
            anchorY = placement.getHeight();
        }
        out.add(new PendingPrefabPaste(
                0, anchorY, 0, wx, wz, placement.getPrefabPath(),
                placement.getPrefabName(), placement.getRotationDegrees()));
    }
}
```

> Confirm `Dimension.getIntHeightAt(int, int)` exists (it is used widely in exporters; if the exact name differs, use the height accessor the surrounding exporter code already uses for surface height). Add imports for `HytalePrefabPlacement` and `java.util.List` if missing.

- [ ] **Step 3c: Call the helper per region.** In the region export method, right after `regionPrefabPastes` is populated by the layer enqueues (and before the paste loop at ~line 636), add:

```java
enqueueExactPlacements(dimension,
        regionMinWorldX, regionMinWorldZ, regionMaxWorldX, regionMaxWorldZ,
        regionPrefabPastes);
```

> Use the region's actual world-bounds variables in scope at that point (derive from the region's tile range × `TILE_SIZE` / chunk extent — mirror how the surrounding code computes the region's covered world area). If those bounds aren't already computed there, compute them from the region/chunk coordinates the method already has.

- [ ] **Step 3d: Use rotation in the paste loop + fallback** (lines ~636-659). Change the paste call and the fallback marker:

```java
boolean pasted = prefabPaster.paste(chunksByCoords,
        pending.worldX, pending.anchorY, pending.worldZ,
        blockOffsetX, blockOffsetZ, pending.prefabPath, pending.rotationDegrees);
if (!pasted && pending.prefabName != null) {
    HytaleChunk anchorChunk = lookupAnchorChunk(chunksByCoords, pending.worldX, pending.worldZ);
    if (anchorChunk != null) {
        int aLocalX = Math.floorMod(pending.worldX + blockOffsetX, HytaleChunk.CHUNK_SIZE);
        int aLocalZ = Math.floorMod(pending.worldZ + blockOffsetZ, HytaleChunk.CHUNK_SIZE);
        anchorChunk.addPrefabMarker(aLocalX, pending.anchorY, aLocalZ,
                pending.prefabName, pending.prefabPath, pending.rotationDegrees);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -f WorldPainter/pom.xml -pl WPCore test -Dtest=ExactPlacementExportTest -q`
Expected: PASS, 1 test, 0 failures.

- [ ] **Step 5: Build WPCore to confirm the exporter still compiles, and run the Hytale export tests**

Run: `mvn -f WorldPainter/pom.xml -pl WPCore test -Dtest=Tp49EndToEndExportTest,TpPrefabCrossChunkTest -q`
Expected: PASS — no regression to existing layer-driven export.

- [ ] **Step 6: Commit**

```bash
git add WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/export/HytaleWorldExporter.java \
        WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/export/ExactPlacementExportTest.java
git commit -m "feat(hytale): export exact prefab placements (rotation-aware, marker fallback)"
```

---

## Plan A self-review checklist (run before handing off to Plan B)

- [ ] Full WPCore test run is green: `mvn -f WorldPainter/pom.xml -pl WPCore test -q` (note any pre-existing failures unrelated to this work and report them).
- [ ] Spec coverage: data model (T1), persistence + migration (T2), shared rotation helper (T3), rotation algorithm cardinal+free (T4), export realization inline paste (T5/T7), marker fallback w/ rotation (T6/T7). ✔
- [ ] No placeholders introduced; every "ADJUST/confirm" note is a real verification step against named source, not a TODO in shipped code.
- [ ] Backward compatibility: old worlds load (T2 migration), old chunks deserialize (T6 default-0 rotation), layer pastes unchanged (T5/T7 delegating paths).

## Notes carried to Plan B (UI)

- Rotation direction knob: if directional blocks point wrong at 90° in manual testing, flip facing `steps` in `PrefabRotator.rotateCardinal` to `((4 - steps) % 4)` (single documented site).
- `Dimension` UI sync: mutators bump `changeNo` (dirty/save tracking) but do **not** fire the `Dimension.Listener` overlay events; Plan B's operation/panel repaint the view and refresh the list directly after each mutation.
- Placement id allocation: Plan B assigns ids (e.g. an incrementing counter seeded from `max(existing ids)+1`, or `System.nanoTime()`); ids only need to be unique within a dimension.
