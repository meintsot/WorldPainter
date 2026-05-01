# TP-51 — Eyedropper Discrete-Layer Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop the Eyedropper from crashing on Hytale discrete layers (Environment, Fluid, Entity, Prefab) and let users pick those layers' specific values from the popup, the same way Biome and Annotations already work.

**Architecture:** Single-file change to `Eyedropper.java` plus one new JUnit test file. Per-layer naming is extracted into a package-private static helper `discreteLayerEntry(Layer, int)` that returns a `(name, icon)` `DiscreteEntry` record-like class. The popup-builder loop replaces its `throw` branch with a call to that helper. A generic fallback at the end of the helper (`"<layer name>: <value>"`) means no future discrete layer can crash the Eyedropper.

**Tech Stack:** Java 17, Maven (`mvn -pl WPGUI test`), JUnit 4 (existing test convention in `WPGUI/src/test/java`), Swing (only for `Icon`/`ImageIcon` types — no Swing event-loop interaction in the tests).

**Reference spec:** `docs/superpowers/specs/2026-05-01-tp51-eyedropper-discrete-layer-design.md`.

---

## File Structure

| File | Role | Status |
|---|---|---|
| `WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/tools/Eyedropper.java` | Adds `DiscreteEntry` static class + `discreteLayerEntry(Layer, int)` helper. Replaces the `throw` branch in the popup-builder loop with a call to that helper. Adds 4 imports (the Hytale layer classes + `HytaleEnvironmentData`). | **Modify** |
| `WorldPainter/WPGUI/src/test/java/org/pepsoft/worldpainter/tools/EyedropperDiscreteLayerTest.java` | New JUnit 4 test class exercising the helper for each Hytale layer (happy path + out-of-range) and the generic fallback (via a private static `Layer` test stub). | **Create** |

No other files are touched. No new classes outside the test file. No production-side dependency additions.

---

## Conventions used in this plan

- All file paths are repo-relative from `C:\Users\Sotirios\Desktop\WorldPainter` (the project root that contains `WorldPainter/`, `docs/`, etc.).
- Test command, run from the project root:
  - `mvn -pl WPGUI -am test -Dtest=EyedropperDiscreteLayerTest`
- Build command (compile-only sanity check):
  - `mvn -pl WPGUI -am compile -DskipTests=true`
- After every "Commit" step, the working tree should be clean for the touched files. Any stray uncommitted changes that pre-existed (e.g. `HytaleWorldExporter.java` modification mentioned in the initial git status, or the unrelated `HytaleSealAboveTerrainColumnTest.java` untracked file) must NOT be added by these commits — only stage the exact files this task changed.

---

## Task 1 — Bootstrap the helper skeleton in `Eyedropper.java`

Adds the `DiscreteEntry` inner class and a stub `discreteLayerEntry(Layer, int)` that throws so we can write tests against the signature. Does NOT yet wire it into the popup-builder loop (we'll do that after the helper has real behavior).

**Files:**
- Modify: `WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/tools/Eyedropper.java` (add imports near line 10; add inner class + helper near the bottom of the class, after the `PaintType` enum at line 170)

- [ ] **Step 1.1 — Add the four Hytale-layer imports**

In `Eyedropper.java`, locate the existing import group (lines 1-24) and add these lines below the existing `org.pepsoft.worldpainter.hytale.HytaleTerrainLayer` import (which is currently line 9):

```java
import org.pepsoft.worldpainter.hytale.HytaleEntityLayer;
import org.pepsoft.worldpainter.hytale.HytaleEnvironmentData;
import org.pepsoft.worldpainter.hytale.HytaleEnvironmentLayer;
import org.pepsoft.worldpainter.hytale.HytaleFluidLayer;
import org.pepsoft.worldpainter.hytale.HytalePrefabLayer;
```

- [ ] **Step 1.2 — Add the `DiscreteEntry` inner class and helper stub**

Insert the following block at the end of the `Eyedropper` class body, immediately before the closing `}` of the class (i.e. after the `public enum PaintType { ... }` line at 170 and any blank line after it):

```java
    /**
     * Result of resolving a (layer, value) pair into a popup-menu entry. Used
     * by the eyedropper popup builder for discrete layers other than Biome
     * and Annotations (which have their own special-cased rendering above).
     */
    static final class DiscreteEntry {
        final String name;
        final Icon icon;

        DiscreteEntry(String name, Icon icon) {
            this.name = name;
            this.icon = icon;
        }
    }

    /**
     * Resolve a discrete layer (Hytale Environment / Fluid / Entity / Prefab,
     * or any other discrete non-ReadOnly layer) and a raw value to a
     * popup-menu entry. The icon is the layer's own icon scaled to 16x16.
     *
     * <p>Values that fall outside the layer's known range render with a
     * generic "&lt;Layer name&gt;: value &lt;raw&gt;" label rather than throwing.
     */
    static DiscreteEntry discreteLayerEntry(Layer layer, int value) {
        throw new UnsupportedOperationException("discreteLayerEntry not implemented yet");
    }
```

- [ ] **Step 1.3 — Compile-check**

Run from the project root:
```
mvn -pl WPGUI -am compile -DskipTests=true
```
Expected: `BUILD SUCCESS`. No new warnings about unused imports (the stubs reference `Icon` and `Layer`, both imported via the existing `javax.swing.*` and `org.pepsoft.worldpainter.layers.*` wildcards).

- [ ] **Step 1.4 — Commit**

```
git add WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/tools/Eyedropper.java
git commit -m "refactor(eyedropper): add DiscreteEntry helper skeleton for TP-51"
```

---

## Task 2 — Test + implement Hytale Environment branch

**Files:**
- Create: `WorldPainter/WPGUI/src/test/java/org/pepsoft/worldpainter/tools/EyedropperDiscreteLayerTest.java`
- Modify: `WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/tools/Eyedropper.java` (replace the `throw` in `discreteLayerEntry`)

- [ ] **Step 2.1 — Write the failing test class with the Environment happy-path test**

Create the file with this exact content:

```java
package org.pepsoft.worldpainter.tools;

import org.junit.Test;
import org.pepsoft.worldpainter.hytale.HytaleEnvironmentData;
import org.pepsoft.worldpainter.hytale.HytaleEnvironmentLayer;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class EyedropperDiscreteLayerTest {

    @Test
    public void environment_validId_returnsDisplayName() {
        int id = HytaleEnvironmentData.getByName("Env_Zone1_Forests").getId();

        Eyedropper.DiscreteEntry entry = Eyedropper.discreteLayerEntry(
                HytaleEnvironmentLayer.INSTANCE, id);

        assertNotNull("entry must not be null", entry);
        assertNotNull("icon must not be null", entry.icon);
        assertTrue(
                "name should contain the display name 'Forests', got: " + entry.name,
                entry.name.contains("Forests"));
        assertTrue(
                "name should be prefixed with the layer name, got: " + entry.name,
                entry.name.startsWith("Hytale Environment"));
    }
}
```

- [ ] **Step 2.2 — Run the test to confirm it fails**

```
mvn -pl WPGUI -am test -Dtest=EyedropperDiscreteLayerTest#environment_validId_returnsDisplayName
```
Expected: failing test with `UnsupportedOperationException: discreteLayerEntry not implemented yet`.

- [ ] **Step 2.3 — Implement the Environment branch (and lay down the `if`-chain shape we'll extend in later tasks)**

Replace the body of `discreteLayerEntry` in `Eyedropper.java` with:

```java
    static DiscreteEntry discreteLayerEntry(Layer layer, int value) {
        final Icon icon = new ImageIcon(scaleIcon(layer.getIcon(), 16));
        if (layer instanceof HytaleEnvironmentLayer) {
            HytaleEnvironmentData env = HytaleEnvironmentData.getById(value);
            String label = (env != null) ? env.getDisplayName() : ("value " + value);
            return new DiscreteEntry("Hytale Environment: " + label, icon);
        }
        // Generic fallback (will be tightened to per-layer cases in Tasks 3-5;
        // covered by its own test in Task 7).
        return new DiscreteEntry(layer.getName() + ": " + value, icon);
    }
```

Note: `scaleIcon` is already statically imported at line 20 of `Eyedropper.java`; `ImageIcon` is in `javax.swing` and already covered by the existing `javax.swing.*` import.

- [ ] **Step 2.4 — Run the test to confirm it passes**

```
mvn -pl WPGUI -am test -Dtest=EyedropperDiscreteLayerTest#environment_validId_returnsDisplayName
```
Expected: 1 test, 0 failures.

- [ ] **Step 2.5 — Commit**

```
git add WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/tools/Eyedropper.java WorldPainter/WPGUI/src/test/java/org/pepsoft/worldpainter/tools/EyedropperDiscreteLayerTest.java
git commit -m "feat(eyedropper): handle HytaleEnvironmentLayer in discreteLayerEntry"
```

---

## Task 3 — Test + implement Hytale Fluid branch (incl. legacy migration)

**Files:**
- Modify: `WorldPainter/WPGUI/src/test/java/org/pepsoft/worldpainter/tools/EyedropperDiscreteLayerTest.java`
- Modify: `WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/tools/Eyedropper.java`

- [ ] **Step 3.1 — Add two failing fluid tests**

Append these test methods inside the `EyedropperDiscreteLayerTest` class (before the closing `}`), and add the matching import at the top:

Add to imports:
```java
import org.pepsoft.worldpainter.hytale.HytaleFluidLayer;
```

Add to class body:
```java
    @Test
    public void fluid_lava_returnsLavaName() {
        Eyedropper.DiscreteEntry entry = Eyedropper.discreteLayerEntry(
                HytaleFluidLayer.INSTANCE, HytaleFluidLayer.FLUID_LAVA);

        assertNotNull(entry);
        assertNotNull(entry.icon);
        assertTrue("expected name to contain 'Lava', got: " + entry.name,
                entry.name.contains("Lava"));
        assertTrue("expected layer-name prefix, got: " + entry.name,
                entry.name.startsWith("Hytale Fluid"));
    }

    @Test
    public void fluid_legacyValue9_migratesToLava() {
        // Old saves stored fluid value 9 to mean lava; the eyedropper must
        // resolve it through HytaleFluidLayer.normalizeFluidValue rather
        // than indexing FLUID_NAMES with the raw 9.
        Eyedropper.DiscreteEntry entry = Eyedropper.discreteLayerEntry(
                HytaleFluidLayer.INSTANCE, 9);

        assertNotNull(entry);
        assertTrue("expected legacy 9 to migrate to 'Lava', got: " + entry.name,
                entry.name.contains("Lava"));
    }
```

- [ ] **Step 3.2 — Run the tests to confirm they fail**

```
mvn -pl WPGUI -am test -Dtest=EyedropperDiscreteLayerTest
```
Expected: 2 new failures with messages like `expected name to contain 'Lava', got: Hytale Fluid: 1` (the generic fallback is currently being hit).

- [ ] **Step 3.3 — Implement the Fluid branch**

In `Eyedropper.java`, expand `discreteLayerEntry` so the body becomes:

```java
    static DiscreteEntry discreteLayerEntry(Layer layer, int value) {
        final Icon icon = new ImageIcon(scaleIcon(layer.getIcon(), 16));
        if (layer instanceof HytaleEnvironmentLayer) {
            HytaleEnvironmentData env = HytaleEnvironmentData.getById(value);
            String label = (env != null) ? env.getDisplayName() : ("value " + value);
            return new DiscreteEntry("Hytale Environment: " + label, icon);
        }
        if (layer instanceof HytaleFluidLayer) {
            int normalized = HytaleFluidLayer.normalizeFluidValue(value);
            String label = (normalized >= 0 && normalized < HytaleFluidLayer.FLUID_NAMES.length)
                    ? HytaleFluidLayer.FLUID_NAMES[normalized]
                    : ("value " + value);
            return new DiscreteEntry("Hytale Fluid: " + label, icon);
        }
        return new DiscreteEntry(layer.getName() + ": " + value, icon);
    }
```

- [ ] **Step 3.4 — Run the tests to confirm they pass**

```
mvn -pl WPGUI -am test -Dtest=EyedropperDiscreteLayerTest
```
Expected: 3 tests, 0 failures.

- [ ] **Step 3.5 — Commit**

```
git add WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/tools/Eyedropper.java WorldPainter/WPGUI/src/test/java/org/pepsoft/worldpainter/tools/EyedropperDiscreteLayerTest.java
git commit -m "feat(eyedropper): handle HytaleFluidLayer (incl legacy values) in discreteLayerEntry"
```

---

## Task 4 — Test + implement Hytale Entity branch

**Files:**
- Modify: `WorldPainter/WPGUI/src/test/java/org/pepsoft/worldpainter/tools/EyedropperDiscreteLayerTest.java`
- Modify: `WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/tools/Eyedropper.java`

- [ ] **Step 4.1 — Write the failing Entity test**

Add to imports in the test file:
```java
import org.pepsoft.worldpainter.hytale.HytaleEntityLayer;
```

Append to the test class body:
```java
    @Test
    public void entity_dense_returnsDenseName() {
        Eyedropper.DiscreteEntry entry = Eyedropper.discreteLayerEntry(
                HytaleEntityLayer.INSTANCE, HytaleEntityLayer.DENSITY_DENSE);

        assertNotNull(entry);
        assertNotNull(entry.icon);
        assertTrue("expected name to contain 'Dense', got: " + entry.name,
                entry.name.contains("Dense"));
        assertTrue("expected layer-name prefix 'Entities', got: " + entry.name,
                entry.name.startsWith("Entities"));
    }
```

- [ ] **Step 4.2 — Run, see it fail**

```
mvn -pl WPGUI -am test -Dtest=EyedropperDiscreteLayerTest#entity_dense_returnsDenseName
```
Expected: 1 failure, current name will look like `Entities: 6` (the layer's own name happens to be `"Entities"`, so the fallback prefix already matches — the test that fails is the `contains("Dense")` assertion).

- [ ] **Step 4.3 — Implement the Entity branch**

Insert the following block in `discreteLayerEntry`, after the Fluid `if`-block and before the generic fallback:

```java
        if (layer instanceof HytaleEntityLayer) {
            String label = (value >= 0 && value < HytaleEntityLayer.DENSITY_NAMES.length)
                    ? HytaleEntityLayer.DENSITY_NAMES[value]
                    : ("value " + value);
            return new DiscreteEntry("Entities: " + label, icon);
        }
```

- [ ] **Step 4.4 — Run, see it pass**

```
mvn -pl WPGUI -am test -Dtest=EyedropperDiscreteLayerTest
```
Expected: 4 tests, 0 failures.

- [ ] **Step 4.5 — Commit**

```
git add WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/tools/Eyedropper.java WorldPainter/WPGUI/src/test/java/org/pepsoft/worldpainter/tools/EyedropperDiscreteLayerTest.java
git commit -m "feat(eyedropper): handle HytaleEntityLayer in discreteLayerEntry"
```

---

## Task 5 — Test + implement Hytale Prefab branch

**Files:**
- Modify: `WorldPainter/WPGUI/src/test/java/org/pepsoft/worldpainter/tools/EyedropperDiscreteLayerTest.java`
- Modify: `WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/tools/Eyedropper.java`

- [ ] **Step 5.1 — Write the failing Prefab test**

Add to imports in the test file:
```java
import org.pepsoft.worldpainter.hytale.HytalePrefabLayer;
```

Append to the test class body:
```java
    @Test
    public void prefab_dungeon_returnsDungeonName() {
        Eyedropper.DiscreteEntry entry = Eyedropper.discreteLayerEntry(
                HytalePrefabLayer.INSTANCE, HytalePrefabLayer.PREFAB_DUNGEON);

        assertNotNull(entry);
        assertNotNull(entry.icon);
        assertTrue("expected name to contain 'Dungeon', got: " + entry.name,
                entry.name.contains("Dungeon"));
        assertTrue("expected layer-name prefix 'Prefabs', got: " + entry.name,
                entry.name.startsWith("Prefabs"));
    }
```

- [ ] **Step 5.2 — Run, see it fail**

```
mvn -pl WPGUI -am test -Dtest=EyedropperDiscreteLayerTest#prefab_dungeon_returnsDungeonName
```
Expected: 1 failure, name will look like `Prefabs: 7`.

- [ ] **Step 5.3 — Implement the Prefab branch**

Insert the following block in `discreteLayerEntry`, after the Entity `if`-block and before the generic fallback:

```java
        if (layer instanceof HytalePrefabLayer) {
            String label = (value >= 0 && value < HytalePrefabLayer.PREFAB_NAMES.length)
                    ? HytalePrefabLayer.PREFAB_NAMES[value]
                    : ("value " + value);
            return new DiscreteEntry("Prefabs: " + label, icon);
        }
```

- [ ] **Step 5.4 — Run, see it pass**

```
mvn -pl WPGUI -am test -Dtest=EyedropperDiscreteLayerTest
```
Expected: 5 tests, 0 failures.

- [ ] **Step 5.5 — Commit**

```
git add WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/tools/Eyedropper.java WorldPainter/WPGUI/src/test/java/org/pepsoft/worldpainter/tools/EyedropperDiscreteLayerTest.java
git commit -m "feat(eyedropper): handle HytalePrefabLayer in discreteLayerEntry"
```

---

## Task 6 — Out-of-range fallbacks for the four Hytale layers

Verifies the `value >= 0 && value < ...length` guards (already implemented above) actually do route to the fallback string and never throw.

**Files:**
- Modify: `WorldPainter/WPGUI/src/test/java/org/pepsoft/worldpainter/tools/EyedropperDiscreteLayerTest.java`

- [ ] **Step 6.1 — Add the four out-of-range tests**

Append to the test class body:

```java
    @Test
    public void environment_invalidId_fallsBackToValueLabel() {
        Eyedropper.DiscreteEntry entry = Eyedropper.discreteLayerEntry(
                HytaleEnvironmentLayer.INSTANCE, 9999);

        assertNotNull(entry);
        assertTrue("expected fallback 'value 9999', got: " + entry.name,
                entry.name.contains("value 9999"));
    }

    @Test
    public void fluid_invalidValue_fallsBackToValueLabel() {
        Eyedropper.DiscreteEntry entry = Eyedropper.discreteLayerEntry(
                HytaleFluidLayer.INSTANCE, 99);

        assertNotNull(entry);
        assertTrue("expected fallback 'value 99', got: " + entry.name,
                entry.name.contains("value 99"));
    }

    @Test
    public void entity_invalidValue_fallsBackToValueLabel() {
        Eyedropper.DiscreteEntry entry = Eyedropper.discreteLayerEntry(
                HytaleEntityLayer.INSTANCE, 99);

        assertNotNull(entry);
        assertTrue("expected fallback 'value 99', got: " + entry.name,
                entry.name.contains("value 99"));
    }

    @Test
    public void prefab_invalidValue_fallsBackToValueLabel() {
        Eyedropper.DiscreteEntry entry = Eyedropper.discreteLayerEntry(
                HytalePrefabLayer.INSTANCE, 99);

        assertNotNull(entry);
        assertTrue("expected fallback 'value 99', got: " + entry.name,
                entry.name.contains("value 99"));
    }
```

- [ ] **Step 6.2 — Run the full test class**

```
mvn -pl WPGUI -am test -Dtest=EyedropperDiscreteLayerTest
```
Expected: 9 tests, 0 failures (the bounds checks added in Tasks 2-5 already cover these cases).

- [ ] **Step 6.3 — Commit**

```
git add WorldPainter/WPGUI/src/test/java/org/pepsoft/worldpainter/tools/EyedropperDiscreteLayerTest.java
git commit -m "test(eyedropper): cover out-of-range values for all four Hytale layers"
```

---

## Task 7 — Generic fallback for any other discrete layer

Locks in the behavior that any future or unknown discrete layer renders with `"<layer name>: <value>"` instead of throwing. Uses a private static `Layer` test stub since `Layer` is `abstract` with `protected` constructors.

**Files:**
- Modify: `WorldPainter/WPGUI/src/test/java/org/pepsoft/worldpainter/tools/EyedropperDiscreteLayerTest.java`

- [ ] **Step 7.1 — Add the test stub layer and the fallback test**

Add to imports in the test file:
```java
import org.pepsoft.worldpainter.layers.Layer;

import java.awt.image.BufferedImage;
```

Append to the test class body:

```java
    @Test
    public void unknownDiscreteLayer_fallsBackToLayerNameAndValue() {
        Eyedropper.DiscreteEntry entry = Eyedropper.discreteLayerEntry(
                StubDiscreteLayer.INSTANCE, 5);

        assertNotNull(entry);
        assertNotNull("fallback must still produce an icon", entry.icon);
        assertTrue("expected '<name>: 5' suffix, got: " + entry.name,
                entry.name.endsWith(": 5"));
        assertTrue("expected layer name in label, got: " + entry.name,
                entry.name.startsWith("Stub Discrete"));
    }

    /**
     * Minimal {@link Layer} subclass used only by the fallback test. Discrete = true
     * so it would have hit the original {@code throw} branch in the eyedropper.
     */
    private static final class StubDiscreteLayer extends Layer {
        static final StubDiscreteLayer INSTANCE = new StubDiscreteLayer();

        private StubDiscreteLayer() {
            super("test.stub.discrete", "Stub Discrete", "test only",
                  DataSize.NIBBLE, true, 100);
        }

        @Override
        public BufferedImage getIcon() {
            return new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        }
    }
```

- [ ] **Step 7.2 — Run the full test class**

```
mvn -pl WPGUI -am test -Dtest=EyedropperDiscreteLayerTest
```
Expected: 10 tests, 0 failures. The generic fallback was already implemented in Task 2 as the closing return statement.

- [ ] **Step 7.3 — Commit**

```
git add WorldPainter/WPGUI/src/test/java/org/pepsoft/worldpainter/tools/EyedropperDiscreteLayerTest.java
git commit -m "test(eyedropper): cover generic discrete-layer fallback via stub"
```

---

## Task 8 — Wire the popup-builder loop to the helper

Replaces the throwing branch in the popup-builder lambda with a call to `discreteLayerEntry`, completing the production-side fix. The `Biome`, `Annotations`, `SYSTEM_LAYERS`, and non-discrete branches above are left untouched (per spec non-goal).

**Files:**
- Modify: `WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/tools/Eyedropper.java` (lines 134-136)

- [ ] **Step 8.1 — Replace the `throw` branch**

In `Eyedropper.java`, locate this current block (lines 134-136):

```java
                    } else {
                        throw new UnsupportedOperationException("Discrete layer " + layer + " not supported");
                    }
```

Replace it with:

```java
                    } else {
                        if ((paintTypes != null) && (! paintTypes.contains(LAYER))) {
                            return;
                        }
                        DiscreteEntry entry = discreteLayerEntry(layer, value);
                        name = entry.name;
                        icon = entry.icon;
                    }
```

- [ ] **Step 8.2 — Compile-check the whole module**

```
mvn -pl WPGUI -am compile -DskipTests=true
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 8.3 — Run the full test class one more time as a sanity check**

```
mvn -pl WPGUI -am test -Dtest=EyedropperDiscreteLayerTest
```
Expected: 10 tests, 0 failures.

- [ ] **Step 8.4 — Commit**

```
git add WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/tools/Eyedropper.java
git commit -m "fix(eyedropper): use discreteLayerEntry for non-Biome discrete layers (TP-51)"
```

---

## Task 9 — Manual reproduction of the original bug

Reproduce the exact scenario from the YouTrack ticket to confirm the crash is gone end-to-end. The unit tests verify the helper; this verifies the popup-builder wiring under real Swing.

**Files:** None.

- [ ] **Step 9.1 — Build the GUI fat jar**

```
mvn -DskipTests=true -pl WPGUI -am package
```
Expected: `BUILD SUCCESS` and a file at `WorldPainter/WPGUI/target/WPGUI-*-full.jar`.

- [ ] **Step 9.2 — Launch TalePainter**

```
mvn -pl WPGUI exec:exec
```

- [ ] **Step 9.3 — Reproduce the ticket steps**

1. **File → Import → Existing Hytale world…** — pick any Hytale world that contains the `Hytale Environment` layer (the ticket reporter used `Calandor Map revamp tester`; any imported Hytale world works).
2. Once the world is loaded, select the **Spray Paint** tool from the toolbar.
3. In the spray panel, pick a brush and choose **`Pink_Crystal_Block`** (or any Hytale block) as the paint.
4. Tick **Only on** and click the **Select on map** button.
5. Click anywhere on the map that has the **Hytale Environment** layer painted.

Expected (post-fix): a popup appears listing the terrain at that column plus one entry per layer present, including `"Hytale Environment: <env name>"` (e.g. `"Hytale Environment: Forests"`). Selecting that entry sets the "Only on" filter to that layer/value.

Pre-fix behavior (for reference): an `UnsupportedOperationException: Discrete layer Hytale Environment not supported` error dialog.

- [ ] **Step 9.4 — Sanity-check Biome and Annotation paths haven't regressed**

In the same session, click on a tile with biomes painted and confirm biome entries still appear with their proper names; same for an Annotation if any is painted. (No code change touched those paths — this is a quick visual confirmation.)

- [ ] **Step 9.5 — No commit**

This task adds no files; it's a verification gate. If anything fails, stop and diagnose before declaring TP-51 complete.

---

## Self-Review

**1. Spec coverage:**

| Spec section | Covered by |
|---|---|
| Helper extraction (`DiscreteEntry`, `discreteLayerEntry`) | Task 1 (skeleton), Tasks 2-5 (real implementation) |
| Per-layer naming for Environment | Task 2 |
| Per-layer naming for Fluid (incl. legacy migration) | Task 3 |
| Per-layer naming for Entity | Task 4 |
| Per-layer naming for Prefab | Task 5 |
| Out-of-range fallbacks for the four Hytale layers | Task 6 |
| Generic fallback for any other discrete layer | Task 2 (impl) + Task 7 (test) |
| paintTypes LAYER filter applied to the new branch | Task 8 |
| Popup-builder wiring (replace `throw` with helper call) | Task 8 |
| 8 testing scenarios listed in the spec | Tasks 2-7 deliver 10 tests covering all 8 scenarios (env happy + env invalid; fluid lava + fluid legacy + fluid invalid; entity dense + entity invalid; prefab dungeon + prefab invalid; generic fallback) |
| Manual repro of the ticket scenario | Task 9 |

No spec gaps.

**2. Placeholder scan:** No "TBD"/"TODO"/"implement later"/"similar to Task N" anywhere. Each step has the actual code or the actual command. The Task 1 stub explicitly throws so we have something to drive Task 2's failing test against; that's intentional, not a placeholder.

**3. Type / signature consistency:**
- `Eyedropper.DiscreteEntry` (Task 1) → used as `Eyedropper.DiscreteEntry` in every test (Tasks 2-7) ✓
- `Eyedropper.discreteLayerEntry(Layer, int)` (Task 1) → called with that signature throughout ✓
- `entry.name` and `entry.icon` (Task 1 fields) → matches every test access ✓
- `HytaleFluidLayer.normalizeFluidValue` (Task 3 impl) → confirmed in spec and source (`HytaleFluidLayer.java:144`) ✓
- `HytaleEntityLayer.DENSITY_NAMES`, `DENSITY_DENSE` → confirmed in `HytaleEntityLayer.java:73, 84-97` ✓
- `HytalePrefabLayer.PREFAB_NAMES`, `PREFAB_DUNGEON` → confirmed in `HytalePrefabLayer.java:284, 293-306` ✓
- `HytaleEnvironmentData.getById`, `getByName`, `getDisplayName` → confirmed in `HytaleEnvironmentData.java:198, 203, 36` ✓

No drift between tasks.
