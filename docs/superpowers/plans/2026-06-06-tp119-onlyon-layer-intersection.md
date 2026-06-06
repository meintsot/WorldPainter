# TP-119 "Only on" Layer Intersection Toggle — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user choose, when 2+ "only on" filter items are selected, whether an operation applies where *any* item is present (union, current default) or only where *all* overlap (intersection) — shared by brushes and Global Operations.

**Architecture:** Thread an `onlyOnIntersection` boolean from the "only on" popup menu in `BrushOptions` → `DefaultFilter` → `OnlyOnTerrainOrLayerFilter.create()`, which already has the building blocks: `AnyOfFilter` (max strength = union) and `AllOfFilter` (min strength = intersection). Persist the choice in `FilterPreset`. Default stays union, so all existing behavior and saved state are unchanged.

**Tech Stack:** Java 17, Maven (JDK 17 toolchain), JUnit 4, Swing. Modules: WPCore (model + persistence + tests), WPGUI (UI panel).

**Working directory for all Maven commands:** `<worktree>/WorldPainter` (the directory containing `pom.xml`).

**Reference spec:** `docs/superpowers/specs/2026-06-06-tp119-onlyon-layer-intersection-design.md`

---

## File Structure

- **Modify** `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/panels/OnlyOnTerrainOrLayerFilter.java` — add 3-arg `create(dimension, item, intersection)`.
- **Modify** `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/panels/DefaultFilter.java` — new constructor overload + `onlyOnIntersection` field + getter + Builder method.
- **Modify** `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/panels/FilterPreset.java` — `onlyOnIntersection` field + getter/setter + `captureFrom` param.
- **Modify** `WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/panels/BrushOptions.java` — field + popup menu items + `getFilter`/`setFilter`/`saveFilterPreset`/`loadFilterPreset` wiring.
- **Create** `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/panels/OnlyOnIntersectionFilterTest.java`
- **Create** `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/panels/DefaultFilterOnlyOnIntersectionTest.java`
- **Create** `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/panels/FilterPresetTest.java`

---

## Task 0: Verify clean baseline

- [ ] **Step 1: Compile the whole project**

Run (from `<worktree>/WorldPainter`):
```
mvn -DskipTests=true -pl WPGUI -am install
```
Expected: `BUILD SUCCESS`. (This compiles WPCore + WPGUI. If it fails, stop and report — the baseline is not clean.)

---

## Task 1: `OnlyOnTerrainOrLayerFilter.create()` intersection overload

**Files:**
- Create: `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/panels/OnlyOnIntersectionFilterTest.java`
- Modify: `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/panels/OnlyOnTerrainOrLayerFilter.java`

- [ ] **Step 1: Write the failing test**

Create `OnlyOnIntersectionFilterTest.java`:
```java
package org.pepsoft.worldpainter.panels;

import org.junit.Test;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.operations.Filter;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OnlyOnIntersectionFilterTest {
    @Test
    public void createWithListAndIntersectionReturnsAllOfFilter() {
        Filter f = OnlyOnTerrainOrLayerFilter.create(null, List.of(Terrain.GRASS, Terrain.SAND), true);
        assertTrue("expected AllOfFilter, got " + f.getClass(), f instanceof AllOfFilter);
        assertEquals(2, ((AllOfFilter) f).getFilters().size());
    }

    @Test
    public void createWithListAndUnionReturnsAnyOfFilter() {
        Filter f = OnlyOnTerrainOrLayerFilter.create(null, List.of(Terrain.GRASS, Terrain.SAND), false);
        assertTrue("expected AnyOfFilter, got " + f.getClass(), f instanceof AnyOfFilter);
        assertEquals(2, ((AnyOfFilter) f).getFilters().size());
    }

    @Test
    public void createWithSingleItemIgnoresIntersection() {
        Filter f = OnlyOnTerrainOrLayerFilter.create(null, Terrain.GRASS, true);
        assertTrue(f instanceof OnlyOnTerrainOrLayerFilter);
    }

    @Test
    public void twoArgCreateStillDefaultsToUnion() {
        Filter f = OnlyOnTerrainOrLayerFilter.create(null, List.of(Terrain.GRASS, Terrain.SAND));
        assertTrue(f instanceof AnyOfFilter);
    }

    // Lock the union (max) vs intersection (min) semantics of the combiners themselves,
    // using stub sub-filters so no Dimension is needed.
    @Test
    public void anyOfIsUnion() {
        // List.<Filter>of(...) gives the lambdas their target functional-interface type.
        Filter union = new AnyOfFilter(List.<Filter>of(
                (x, y, s) -> (x == 1) ? s : 0.0f,
                (x, y, s) -> (x == 2) ? s : 0.0f));
        assertEquals(1.0f, union.modifyStrength(1, 0, 1.0f), 0.0f);
        assertEquals(1.0f, union.modifyStrength(2, 0, 1.0f), 0.0f);
        assertEquals(0.0f, union.modifyStrength(3, 0, 1.0f), 0.0f);
    }

    @Test
    public void allOfIsIntersection() {
        Filter intersection = new AllOfFilter(List.<Filter>of(
                (x, y, s) -> (x >= 1) ? s : 0.0f,
                (x, y, s) -> (x <= 1) ? s : 0.0f));
        assertEquals(1.0f, intersection.modifyStrength(1, 0, 1.0f), 0.0f); // both pass only at x==1
        assertEquals(0.0f, intersection.modifyStrength(0, 0, 1.0f), 0.0f);
        assertEquals(0.0f, intersection.modifyStrength(2, 0, 1.0f), 0.0f);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run (from `<worktree>/WorldPainter`):
```
mvn -pl WPCore test -Dtest=OnlyOnIntersectionFilterTest
```
Expected: FAIL — compilation error, `create(...)` with 3 args (and a `boolean`) does not exist.

- [ ] **Step 3: Add the 3-arg overload (minimal implementation)**

In `OnlyOnTerrainOrLayerFilter.java`, replace the existing `create` method:
```java
    @SuppressWarnings("unchecked") // Guaranteed by code
    public static Filter create(Dimension dimension, Object item) {
        if (item instanceof List) {
            return new AnyOfFilter(((List<Object>) item).stream()
                    .map(object -> OnlyOnTerrainOrLayerFilter.create(dimension, object))
                    .collect(toList()));
        } else {
            return new OnlyOnTerrainOrLayerFilter(dimension, item);
        }
    }
```
with:
```java
    public static Filter create(Dimension dimension, Object item) {
        return create(dimension, item, false);
    }

    @SuppressWarnings("unchecked") // Guaranteed by code
    public static Filter create(Dimension dimension, Object item, boolean intersection) {
        if (item instanceof List) {
            final List<Filter> subFilters = ((List<Object>) item).stream()
                    .map(object -> OnlyOnTerrainOrLayerFilter.create(dimension, object, intersection))
                    .collect(toList());
            return intersection ? new AllOfFilter(subFilters) : new AnyOfFilter(subFilters);
        } else {
            return new OnlyOnTerrainOrLayerFilter(dimension, item);
        }
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run:
```
mvn -pl WPCore test -Dtest=OnlyOnIntersectionFilterTest
```
Expected: PASS (6 tests, 0 failures).

- [ ] **Step 5: Commit**

```
git add WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/panels/OnlyOnTerrainOrLayerFilter.java WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/panels/OnlyOnIntersectionFilterTest.java
git commit -m "feat(TP-119): OnlyOnTerrainOrLayerFilter.create intersection overload"
```

---

## Task 2: `DefaultFilter` intersection support

**Files:**
- Create: `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/panels/DefaultFilterOnlyOnIntersectionTest.java`
- Modify: `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/panels/DefaultFilter.java`

- [ ] **Step 1: Write the failing test**

Create `DefaultFilterOnlyOnIntersectionTest.java` (same package → can read the package-private `onlyOnFilter` field):
```java
package org.pepsoft.worldpainter.panels;

import org.junit.Test;
import org.pepsoft.worldpainter.Terrain;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DefaultFilterOnlyOnIntersectionTest {
    @Test
    public void intersectionBuildsAllOfFilter() {
        DefaultFilter f = new DefaultFilter(null, false, false,
                Integer.MIN_VALUE, Integer.MIN_VALUE, false,
                true, List.of(Terrain.GRASS, Terrain.SAND), true,
                false, null, -1, false);
        assertTrue(f.onlyOnFilter instanceof AllOfFilter);
        assertTrue(f.isOnlyOnIntersection());
    }

    @Test
    public void unionBuildsAnyOfFilter() {
        DefaultFilter f = new DefaultFilter(null, false, false,
                Integer.MIN_VALUE, Integer.MIN_VALUE, false,
                true, List.of(Terrain.GRASS, Terrain.SAND), false,
                false, null, -1, false);
        assertTrue(f.onlyOnFilter instanceof AnyOfFilter);
        assertFalse(f.isOnlyOnIntersection());
    }

    @Test
    public void legacyConstructorDefaultsToUnion() {
        DefaultFilter f = new DefaultFilter(null, false, false,
                Integer.MIN_VALUE, Integer.MIN_VALUE, false,
                true, List.of(Terrain.GRASS, Terrain.SAND),
                false, null, -1, false);
        assertTrue(f.onlyOnFilter instanceof AnyOfFilter);
        assertFalse(f.isOnlyOnIntersection());
    }

    @Test
    public void builderSupportsIntersection() {
        DefaultFilter f = DefaultFilter.buildForDimension(null)
                .onlyOn(List.of(Terrain.GRASS, Terrain.SAND))
                .onlyOnIntersection(true)
                .build();
        assertTrue(f.onlyOnFilter instanceof AllOfFilter);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```
mvn -pl WPCore test -Dtest=DefaultFilterOnlyOnIntersectionTest
```
Expected: FAIL — compilation error: the 13-arg constructor, `isOnlyOnIntersection()`, and `Builder.onlyOnIntersection(boolean)` do not exist.

- [ ] **Step 3a: Replace the constructor with a delegating overload pair**

In `DefaultFilter.java`, replace the existing constructor header line:
```java
    public DefaultFilter(Dimension dimension, boolean inSelection, boolean outsideSelection, int aboveLevel, int belowLevel, boolean feather, boolean onlyOn, Object onlyOnItem, boolean exceptOn, Object exceptOnItem, int aboveDegrees, boolean slopeIsAbove) {
```
with the kept 12-arg delegator immediately followed by the new 13-arg constructor header:
```java
    public DefaultFilter(Dimension dimension, boolean inSelection, boolean outsideSelection, int aboveLevel, int belowLevel, boolean feather, boolean onlyOn, Object onlyOnItem, boolean exceptOn, Object exceptOnItem, int aboveDegrees, boolean slopeIsAbove) {
        this(dimension, inSelection, outsideSelection, aboveLevel, belowLevel, feather, onlyOn, onlyOnItem, false, exceptOn, exceptOnItem, aboveDegrees, slopeIsAbove);
    }

    public DefaultFilter(Dimension dimension, boolean inSelection, boolean outsideSelection, int aboveLevel, int belowLevel, boolean feather, boolean onlyOn, Object onlyOnItem, boolean onlyOnIntersection, boolean exceptOn, Object exceptOnItem, int aboveDegrees, boolean slopeIsAbove) {
```
(The original constructor *body* — from `this.dimension = dimension;` down to its closing brace — now belongs to the new 13-arg constructor. Do not duplicate it.)

- [ ] **Step 3b: Build `onlyOnFilter` with the flag and store it**

In that constructor body, replace the line:
```java
        onlyOnFilter = (onlyOnItem != null) ? OnlyOnTerrainOrLayerFilter.create(dimension, onlyOnItem) : null;
```
with:
```java
        this.onlyOnIntersection = onlyOnIntersection;
        onlyOnFilter = (onlyOnItem != null) ? OnlyOnTerrainOrLayerFilter.create(dimension, onlyOnItem, onlyOnIntersection) : null;
```

- [ ] **Step 3c: Add the field**

In the fields block, replace:
```java
    final boolean checkLevel, onlyOn, exceptOn, feather, checkSlope,
            slopeIsAbove, inSelection, outsideSelection;
```
with:
```java
    final boolean checkLevel, onlyOn, exceptOn, feather, checkSlope,
            slopeIsAbove, inSelection, outsideSelection, onlyOnIntersection;
```

- [ ] **Step 3d: Add the getter**

Immediately after the existing `getExceptOnLayer()` method, add:
```java
    public boolean isOnlyOnIntersection() {
        return onlyOnIntersection;
    }
```

- [ ] **Step 3e: Add the Builder support**

In the `Builder` class, add the field next to the existing `private Object onlyOn, exceptOn;`:
```java
        private boolean onlyOnIntersection;
```
Add the builder method (next to the existing `onlyOn(Object item)` method):
```java
        public Builder onlyOnIntersection(boolean intersection) {
            onlyOnIntersection = intersection;
            return this;
        }
```
Replace `Builder.build()`:
```java
        public DefaultFilter build() {
            return new DefaultFilter(dimension, inSelection, outsideSelection, aboveLevel, belowLevel, feather, onlyOn != null, onlyOn, exceptOn != null, exceptOn, aboveDegrees, slopeIsAbove);
        }
```
with:
```java
        public DefaultFilter build() {
            return new DefaultFilter(dimension, inSelection, outsideSelection, aboveLevel, belowLevel, feather, onlyOn != null, onlyOn, onlyOnIntersection, exceptOn != null, exceptOn, aboveDegrees, slopeIsAbove);
        }
```

- [ ] **Step 4: Run test to verify it passes**

Run:
```
mvn -pl WPCore test -Dtest=DefaultFilterOnlyOnIntersectionTest
```
Expected: PASS (4 tests, 0 failures).

- [ ] **Step 5: Commit**

```
git add WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/panels/DefaultFilter.java WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/panels/DefaultFilterOnlyOnIntersectionTest.java
git commit -m "feat(TP-119): thread onlyOnIntersection through DefaultFilter"
```

---

## Task 3: `FilterPreset` persistence of the choice

**Files:**
- Create: `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/panels/FilterPresetTest.java`
- Modify: `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/panels/FilterPreset.java`

- [ ] **Step 1: Write the failing test**

Create `FilterPresetTest.java`:
```java
package org.pepsoft.worldpainter.panels;

import org.junit.Test;
import org.pepsoft.worldpainter.Terrain;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FilterPresetTest {
    @Test
    public void capturesAndResolvesIntersectionFlag() {
        FilterPreset preset = new FilterPreset("test");
        preset.captureFrom(false, false, Integer.MIN_VALUE, Integer.MIN_VALUE, false,
                List.of(Terrain.GRASS, Terrain.SAND), null, -1, false, true);
        assertTrue(preset.isOnlyOnIntersection());
        Object onlyOn = preset.resolveOnlyOn();
        assertTrue(onlyOn instanceof List);
        assertEquals(2, ((List<?>) onlyOn).size());
    }

    @Test
    public void defaultsToUnion() {
        FilterPreset preset = new FilterPreset("test");
        assertFalse(preset.isOnlyOnIntersection());
    }

    @Test
    public void roundTripsThroughSerialization() throws Exception {
        FilterPreset preset = new FilterPreset("test");
        preset.captureFrom(false, false, Integer.MIN_VALUE, Integer.MIN_VALUE, false,
                List.of(Terrain.GRASS, Terrain.SAND), null, -1, false, true);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(preset);
        }
        FilterPreset restored;
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))) {
            restored = (FilterPreset) ois.readObject();
        }
        assertTrue(restored.isOnlyOnIntersection());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```
mvn -pl WPCore test -Dtest=FilterPresetTest
```
Expected: FAIL — compilation error: `captureFrom(...)` has no `boolean` 10th parameter and `isOnlyOnIntersection()` does not exist.

- [ ] **Step 3a: Add the field**

In `FilterPreset.java`, after the line `private boolean slopeIsAbove;` add:
```java
    private boolean onlyOnIntersection;
```

- [ ] **Step 3b: Add getter/setter**

After the existing `isSlopeIsAbove()` / `setSlopeIsAbove(...)` accessors add:
```java
    public boolean isOnlyOnIntersection() { return onlyOnIntersection; }
    public void setOnlyOnIntersection(boolean onlyOnIntersection) { this.onlyOnIntersection = onlyOnIntersection; }
```

- [ ] **Step 3c: Extend `captureFrom`**

Replace the `captureFrom` signature and body opening:
```java
    @SuppressWarnings("unchecked")
    public void captureFrom(boolean inSelection, boolean outsideSelection,
                            int aboveLevel, int belowLevel, boolean feather,
                            Object onlyOn, Object exceptOn,
                            int slopeDegrees, boolean slopeIsAbove) {
        this.inSelection = inSelection;
```
with:
```java
    @SuppressWarnings("unchecked")
    public void captureFrom(boolean inSelection, boolean outsideSelection,
                            int aboveLevel, int belowLevel, boolean feather,
                            Object onlyOn, Object exceptOn,
                            int slopeDegrees, boolean slopeIsAbove,
                            boolean onlyOnIntersection) {
        this.onlyOnIntersection = onlyOnIntersection;
        this.inSelection = inSelection;
```

- [ ] **Step 4: Run test to verify it passes**

Run:
```
mvn -pl WPCore test -Dtest=FilterPresetTest
```
Expected: PASS (3 tests, 0 failures).

- [ ] **Step 5: Commit**

```
git add WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/panels/FilterPreset.java WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/panels/FilterPresetTest.java
git commit -m "feat(TP-119): persist onlyOnIntersection in FilterPreset"
```

---

## Task 4: `BrushOptions` UI wiring (build-verified)

No new unit test — Swing panel behavior is verified by build + manual checklist (Task 5). Each edit below is exact.

**File:** `WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/panels/BrushOptions.java`

- [ ] **Step 1: Add the instance field**

Replace:
```java
    private Object onlyOn, exceptOn;
```
with:
```java
    private Object onlyOn, exceptOn;
    private boolean onlyOnIntersection;
```

- [ ] **Step 2: Pass the flag in `getFilter()`**

In `getFilter()`, replace:
```java
                    checkBoxReplace.isSelected(),
                    onlyOn,
                    checkBoxExceptOn.isSelected(),
```
with:
```java
                    checkBoxReplace.isSelected(),
                    onlyOn,
                    onlyOnIntersection,
                    checkBoxExceptOn.isSelected(),
```

- [ ] **Step 3: Restore the flag in `setFilter()`**

In `setFilter(DefaultFilter filter)`, in the `if (filter == null)` branch, replace:
```java
            onlyOn = exceptOn = null;
```
with:
```java
            onlyOn = exceptOn = null;
            onlyOnIntersection = false;
```
And in the `else` branch, replace:
```java
            checkBoxReplace.setSelected(filter.onlyOn);
            checkBoxExceptOn.setSelected(filter.exceptOn);
```
with:
```java
            checkBoxReplace.setSelected(filter.onlyOn);
            onlyOnIntersection = filter.isOnlyOnIntersection();
            checkBoxExceptOn.setSelected(filter.exceptOn);
```

- [ ] **Step 4: Add the match-mode radio items to the "only on" popup**

Replace the whole `createReplaceMenu()` method:
```java
    private JPopupMenu createReplaceMenu() {
        final JMenu menu = createObjectSelectionMenu(MENU_ONLY_ON, (object, name, icon) -> {
            onlyOn = object;
            if (onlyOn == null) {
                checkBoxReplace.setSelected(false);
                setControlStates();
            }
            installPaint(onlyOn, buttonReplace, checkBoxReplace);
        }, false, onlyOn);
        final JPopupMenu popupMenu = new BetterJPopupMenu();
        Arrays.stream(menu.getMenuComponents()).forEach(popupMenu::add);
        return popupMenu;
    }
```
with:
```java
    private JPopupMenu createReplaceMenu() {
        final JMenu menu = createObjectSelectionMenu(MENU_ONLY_ON, (object, name, icon) -> {
            onlyOn = object;
            if (onlyOn == null) {
                checkBoxReplace.setSelected(false);
                setControlStates();
            }
            installPaint(onlyOn, buttonReplace, checkBoxReplace);
        }, false, onlyOn);
        final JPopupMenu popupMenu = new BetterJPopupMenu();
        Arrays.stream(menu.getMenuComponents()).forEach(popupMenu::add);
        if ((onlyOn instanceof List) && (((List<?>) onlyOn).size() >= 2)) {
            popupMenu.addSeparator();
            final ButtonGroup matchGroup = new ButtonGroup();
            final JRadioButtonMenuItem anyItem = new JRadioButtonMenuItem("Match any of these (union)", ! onlyOnIntersection);
            anyItem.addActionListener(e -> {
                onlyOnIntersection = false;
                filterChanged();
            });
            matchGroup.add(anyItem);
            popupMenu.add(anyItem);
            final JRadioButtonMenuItem allItem = new JRadioButtonMenuItem("Match all of these (intersection)", onlyOnIntersection);
            allItem.addActionListener(e -> {
                onlyOnIntersection = true;
                filterChanged();
            });
            matchGroup.add(allItem);
            popupMenu.add(allItem);
        }
        return popupMenu;
    }
```

- [ ] **Step 5: Persist the flag when saving a preset**

In `saveFilterPreset()`, replace:
```java
                    (checkBoxAboveSlope.isSelected() || checkBoxBelowSlope.isSelected()) ? (Integer) spinnerSlope.getValue() : -1,
                    checkBoxAboveSlope.isSelected());
            Configuration.getInstance().addFilterPreset(preset);
```
with:
```java
                    (checkBoxAboveSlope.isSelected() || checkBoxBelowSlope.isSelected()) ? (Integer) spinnerSlope.getValue() : -1,
                    checkBoxAboveSlope.isSelected(),
                    onlyOnIntersection);
            Configuration.getInstance().addFilterPreset(preset);
```

- [ ] **Step 6: Restore the flag when loading a preset**

In `loadFilterPreset(FilterPreset preset)`, replace:
```java
        onlyOn = preset.resolveOnlyOn();
        exceptOn = preset.resolveExceptOn();
```
with:
```java
        onlyOn = preset.resolveOnlyOn();
        exceptOn = preset.resolveExceptOn();
        onlyOnIntersection = preset.isOnlyOnIntersection();
```

- [ ] **Step 7: Compile WPGUI**

Run:
```
mvn -DskipTests=true -pl WPGUI -am install
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 8: Commit**

```
git add WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/panels/BrushOptions.java
git commit -m "feat(TP-119): match any/all menu in only-on popup, wired to filter + presets"
```

---

## Task 5: Full verification

- [ ] **Step 1: Run the three new test classes together**

Run (from `<worktree>/WorldPainter`):
```
mvn -pl WPCore test -Dtest=OnlyOnIntersectionFilterTest,DefaultFilterOnlyOnIntersectionTest,FilterPresetTest
```
Expected: PASS (13 tests, 0 failures).

- [ ] **Step 2: Full compile of GUI module**

Run:
```
mvn -DskipTests=true -pl WPGUI -am install
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Manual verification checklist (Swing — run the app)**

Run: `mvn -pl WPGUI exec:exec`, then:
1. Open a world, select a brush, open its filter ("only on"). Add **two** layers/terrains.
2. Open the "only on" button menu → two radio items appear: "Match any of these (union)" (selected) and "Match all of these (intersection)".
3. Paint with "Match any" → affects where *either* is present. Switch to "Match all" → affects only the overlap.
4. With only **one** item selected, the two radio items do **not** appear.
5. Repeat via **Operations → Global Operations** (the Fill dialog): the same menu items appear and intersection affects only the overlap.
6. Save a preset with "Match all" selected; clear it; load the preset → "Match all" is restored.

- [ ] **Step 4: Final commit (only if Step 3 surfaced fixes)**

```
git add -A
git commit -m "fix(TP-119): address manual verification findings"
```

---

## Self-Review notes (author)

- **Spec coverage:** §4.1 → Task 1; §4.2 → Task 2; §4.3 → Task 4; §4.4 → Task 3; §6 tests → Tasks 1–3 + Task 5 manual.
- **Type consistency:** `onlyOnIntersection` boolean used consistently across `OnlyOnTerrainOrLayerFilter.create(…, boolean)`, the 13-arg `DefaultFilter` constructor, `DefaultFilter.isOnlyOnIntersection()`, `Builder.onlyOnIntersection(boolean)`, `FilterPreset.captureFrom(…, boolean)` / `isOnlyOnIntersection()` / `setOnlyOnIntersection(boolean)`, and `BrushOptions.onlyOnIntersection`.
- **Back-compat:** legacy 12-arg `DefaultFilter` constructor preserved (delegates with `false`); `CreateFilterOp` untouched; old serialized `FilterPreset` loads the missing field as `false` (union).
- **Terrain constants:** `Terrain.GRASS` and `Terrain.SAND` are standard built-in terrains usable without a `Dimension`; if either is unavailable, substitute any two distinct `Terrain` enum constants.
