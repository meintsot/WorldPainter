# Town Tool — Surface-Flush Placement + Erase Brush — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Town Plan footprint export flush into the terrain surface (instead of a pillar of blocks above it), and add a radius erase-brush so areas can be removed after stamping.

**Architecture:** Two surgical changes to the existing v1 Town Plan tool. (1) The `TownLayoutExporter` replaces the surface terrain block at each footprint column instead of stacking a marker pillar; its `markerHeight` setting becomes `surfaceDepth` (downward replacement depth, default 1). (2) `TownPlanStamper` gains pure `computeDisc` + thin `erase` helpers, and `TownPlanOperation` gains an "Erase footprint" toggle + radius slider that clear footprint columns under the cursor on drag. The footprint stays a re-editable `TownLayout` bit layer throughout.

**Tech Stack:** Java 17, Maven (multi-module: WPCore, WPGUI), JUnit 4, Swing.

**Working directory:** Worktree at `.claude/worktrees/town-tool-surface-erase` on branch `worktree-town-tool-surface-erase` (fresh off origin/master). All commands below are run from `<worktree>/WorldPainter` (the directory containing `pom.xml`).

**Spec:** `docs/superpowers/specs/2026-06-06-town-tool-surface-and-erase-design.md`

## File map

- `WPCore/.../layers/exporters/TownLayoutSettings.java` — rename `markerHeight` → `surfaceDepth` (default 1).
- `WPCore/.../layers/exporters/TownLayoutExporter.java` — `placeSurfaceColumn`; `addFeatures` uses `surfaceDepth`.
- `WPCore/.../layers/TownLayout.java` — class-doc wording.
- `WPCore/.../townplan/TownPlanStamper.java` — add `computeDisc` + `erase`.
- `WPGUI/.../operations/TownPlanOperation.java` — erase toggle + radius slider + erase branch in mouse handler.
- Tests: `TownLayoutSettingsTest`, `TownLayoutExporterTest`, `TownPlanStampIntegrationTest`, `TownPlanStamperTest`.

---

## Task 1: Surface-flush export (settings rename + exporter rewrite)

These three production files and their three tests are tightly coupled (a method rename means WPCore will not compile until all are updated), so they land together as one TDD cycle: update the tests to the new surface behaviour first (compile failure = the "red"), then implement.

**Files:**
- Modify: `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/layers/exporters/TownLayoutSettings.java`
- Modify: `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/layers/exporters/TownLayoutExporter.java`
- Modify: `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/layers/TownLayout.java`
- Test: `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/layers/exporters/TownLayoutSettingsTest.java`
- Test: `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/layers/exporters/TownLayoutExporterTest.java`
- Test: `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/townplan/TownPlanStampIntegrationTest.java`

- [ ] **Step 1: Update the failing tests to the new surface behaviour**

In `TownLayoutSettingsTest.java`, replace the two `setMarkerHeight`/`getMarkerHeight` lines in `equalsAndHashCodeReflectFields` and the default assertion. The method body becomes:

```java
    @Test
    public void equalsAndHashCodeReflectFields() {
        TownLayoutSettings a = new TownLayoutSettings();
        TownLayoutSettings b = new TownLayoutSettings();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        b.setSurfaceDepth(7);
        assertNotEquals(a, b);
        b.setSurfaceDepth(a.getSurfaceDepth());
        assertEquals(a, b);

        b.setExport(! a.isExport());
        assertNotEquals(a, b);
        b.setExport(a.isExport());

        b.setBlock(Material.get(BLK_WOOL, 0)); // white wool, differs from the stone default
        assertNotEquals(a, b);
    }
```

Add a new test for the default depth (place it after `defaultBlockIsStone`):

```java
    @Test
    public void defaultSurfaceDepthIsOne() {
        assertEquals(1, new TownLayoutSettings().getSurfaceDepth());
    }
```

In `TownLayoutExporterTest.java`, replace the whole class body's two pillar tests (`placesPillarFromSurfaceUp` and `stopsAtSolidBlock`) with these three surface tests (keep `layerResolvesExporterType` unchanged):

```java
    @Test
    public void replacesSurfaceBlockFlush() {
        // Volume: x 0..16, y 0..16, vertical z 0..64. maxHeight must be a power of two.
        Box volume = new Box(0, 16, 0, 16, 0, 64);
        MinecraftWorldObject world = new MinecraftWorldObject("test", volume, 256, 32);
        Material blackWool = Material.get(BLK_WOOL, 15);

        // Surface height 10, depth 1 -> replace only the surface block at z = 10, flush.
        TownLayoutExporter.placeSurfaceColumn(world, 4, 4, 10, 1, blackWool);

        assertEquals(blackWool, world.getMaterialAt(4, 4, 10)); // surface replaced
        assertEquals(AIR, world.getMaterialAt(4, 4, 11));       // nothing added above the surface
    }

    @Test
    public void depthReplacesDownward() {
        Box volume = new Box(0, 16, 0, 16, 0, 64);
        MinecraftWorldObject world = new MinecraftWorldObject("test", volume, 256, 32);
        Material blackWool = Material.get(BLK_WOOL, 15);

        // Surface height 10, depth 3 -> replace z = 10, 9, 8.
        TownLayoutExporter.placeSurfaceColumn(world, 4, 4, 10, 3, blackWool);

        assertEquals(AIR, world.getMaterialAt(4, 4, 11));       // nothing above the surface
        assertEquals(blackWool, world.getMaterialAt(4, 4, 10));
        assertEquals(blackWool, world.getMaterialAt(4, 4, 9));
        assertEquals(blackWool, world.getMaterialAt(4, 4, 8));
        assertEquals(AIR, world.getMaterialAt(4, 4, 7));        // depth respected
    }

    @Test
    public void depthClampsAtWorldFloor() {
        Box volume = new Box(0, 16, 0, 16, 0, 64);
        MinecraftWorldObject world = new MinecraftWorldObject("test", volume, 256, 32);
        Material blackWool = Material.get(BLK_WOOL, 15);

        // Surface height 1 with depth 5 must not write below the world floor (z = 0) or throw.
        TownLayoutExporter.placeSurfaceColumn(world, 4, 4, 1, 5, blackWool);

        assertEquals(blackWool, world.getMaterialAt(4, 4, 1));
        assertEquals(blackWool, world.getMaterialAt(4, 4, 0)); // floor reached, no underflow
    }
```

In `TownPlanStampIntegrationTest.java`, change `settings.setMarkerHeight(3);` to `settings.setSurfaceDepth(1);` and replace the four post-export assertions with:

```java
        assertEquals(blackWool, world.getMaterialAt(0, 0, terrainHeight));       // surface replaced, flush
        assertEquals(AIR, world.getMaterialAt(0, 0, terrainHeight + 1));         // nothing added above
        assertNotEquals(blackWool, world.getMaterialAt(0, 0, terrainHeight - 1)); // depth 1: below surface untouched
        assertEquals(STONE, world.getMaterialAt(5, 5, terrainHeight));          // unstamped column surface untouched
```

Add the `STONE` static import to `TownPlanStampIntegrationTest.java`'s import block (next to the existing `AIR` import):

```java
import static org.pepsoft.minecraft.Material.AIR;
import static org.pepsoft.minecraft.Material.STONE;
```

- [ ] **Step 2: Run the tests to verify they fail (compile error)**

Run: `mvn -q -pl WPCore test -Dtest='TownLayoutSettingsTest,TownLayoutExporterTest,TownPlanStampIntegrationTest' -DfailIfNoTests=false`
Expected: BUILD FAILURE — compilation errors, because `setSurfaceDepth`/`getSurfaceDepth` and `placeSurfaceColumn` do not exist yet.

- [ ] **Step 3: Implement the production changes**

In `TownLayoutSettings.java`, replace the `getMarkerHeight`/`setMarkerHeight` accessors:

```java
    public int getSurfaceDepth() {
        return surfaceDepth;
    }

    public void setSurfaceDepth(int surfaceDepth) {
        this.surfaceDepth = surfaceDepth;
    }
```

In the same file, in `hashCode()` change the marker line to:

```java
        hash = 41 * hash + this.surfaceDepth;
```

In `equals()` change the final return to:

```java
        return this.surfaceDepth == other.surfaceDepth;
```

And change the field declaration:

```java
    private int surfaceDepth = 1;
```

In `TownLayoutExporter.java`, change the body of `addFeatures` (the `markerHeight` line and the call):

```java
        final Material block = settings.getBlock();
        final int depth = Math.max(1, settings.getSurfaceDepth());
        for (int x = area.x; x < area.x + area.width; x++) {
            for (int y = area.y; y < area.y + area.height; y++) {
                if (dimension.getBitLayerValueAt(TownLayout.INSTANCE, x, y)) {
                    placeSurfaceColumn(minecraftWorld, x, y, dimension.getIntHeightAt(x, y), depth, block);
                }
            }
        }
        return null;
```

Replace the entire `placeMarkerColumn` method with:

```java
    /**
     * Replace the terrain surface block at {@code (x, y)} with {@code block}, flush with the ground,
     * continuing downward for {@code depth} blocks total (clamped at the world floor). Unlike a marker
     * pillar, this overwrites solid terrain — the footprint sits in the surface, not above it.
     */
    static void placeSurfaceColumn(MinecraftWorld world, int x, int y, int terrainHeight, int depth, Material block) {
        final int minZ = world.getMinHeight();
        for (int d = 0; d < depth; d++) {
            final int z = terrainHeight - d;
            if (z < minZ) {
                break;
            }
            world.setMaterialAt(x, y, z, block);
        }
    }
```

In `TownLayout.java`, replace the class Javadoc:

```java
/**
 * Marks the X-Z footprint of an imported town plan. On export, {@code TownLayoutExporter} replaces
 * the terrain surface block at each footprint column (flush with the ground, not a pillar above it).
 * A single on/off (BIT) layer.
 */
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -q -pl WPCore test -Dtest='TownLayoutSettingsTest,TownLayoutExporterTest,TownPlanStampIntegrationTest' -DfailIfNoTests=false`
Expected: BUILD SUCCESS — `Tests run: 8, Failures: 0, Errors: 0` (3 settings + 4 exporter + 1 integration).

- [ ] **Step 5: Commit**

```bash
git add WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/layers/exporters/TownLayoutSettings.java \
        WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/layers/exporters/TownLayoutExporter.java \
        WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/layers/TownLayout.java \
        WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/layers/exporters/TownLayoutSettingsTest.java \
        WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/layers/exporters/TownLayoutExporterTest.java \
        WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/townplan/TownPlanStampIntegrationTest.java
git commit -m "feat(town-plan): stamp footprint into the terrain surface, not blocks above it"
```

---

## Task 2: Erase core — `computeDisc` + `erase`

**Files:**
- Modify: `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/townplan/TownPlanStamper.java`
- Test: `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/townplan/TownPlanStamperTest.java`

- [ ] **Step 1: Write the failing tests**

In `TownPlanStamperTest.java`, add these imports to the existing import block:

```java
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.TestData;
import org.pepsoft.worldpainter.layers.TownLayout;
```

Add these four test methods at the end of the class (before the closing brace):

```java
    @Test
    public void discRadiusZeroIsJustCentre() {
        Set<Point> cols = TownPlanStamper.computeDisc(5, 7, 0.0);
        assertEquals(1, cols.size());
        assertTrue(cols.contains(new Point(5, 7)));
    }

    @Test
    public void discRadiusOneIsPlusShape() {
        // radius 1: centre + 4 orthogonal neighbours; diagonals (dx^2 + dz^2 = 2) fall outside.
        Set<Point> cols = TownPlanStamper.computeDisc(0, 0, 1.0);
        assertEquals(5, cols.size());
        assertTrue(cols.contains(new Point(0, 0)));
        assertTrue(cols.contains(new Point(1, 0)));
        assertTrue(cols.contains(new Point(-1, 0)));
        assertTrue(cols.contains(new Point(0, 1)));
        assertTrue(cols.contains(new Point(0, -1)));
        assertFalse(cols.contains(new Point(1, 1)));   // diagonal excluded
    }

    @Test
    public void discIsCentredOnGivenColumn() {
        Set<Point> cols = TownPlanStamper.computeDisc(10, -4, 2.0);
        assertTrue(cols.contains(new Point(10, -4)));   // centre
        assertTrue(cols.contains(new Point(12, -4)));   // +2 in x, on the rim (4 <= 4)
        assertTrue(cols.contains(new Point(10, -2)));   // +2 in z, on the rim
        assertFalse(cols.contains(new Point(12, -2)));  // (2,2): 8 > 4, outside
        assertFalse(cols.contains(new Point(13, -4)));  // +3 in x: 9 > 4, outside
    }

    @Test
    public void eraseClearsDiscAndLeavesRest() {
        final Rectangle area = new Rectangle(0, 0, 128, 128);
        final Dimension dimension = TestData.createDimension(area, 64);
        // Paint a 3x3 block of footprint around (10,10).
        for (int x = 9; x <= 11; x++) {
            for (int z = 9; z <= 11; z++) {
                dimension.setBitLayerValueAt(TownLayout.INSTANCE, x, z, true);
            }
        }
        // Erase radius 1 at the centre clears (10,10) and its 4 orthogonal neighbours only.
        int cleared = TownPlanStamper.erase(dimension, 10, 10, 1.0);
        assertEquals(5, cleared);
        assertFalse(dimension.getBitLayerValueAt(TownLayout.INSTANCE, 10, 10));
        assertFalse(dimension.getBitLayerValueAt(TownLayout.INSTANCE, 11, 10));
        assertTrue(dimension.getBitLayerValueAt(TownLayout.INSTANCE, 9, 9)); // diagonal corner survives
    }
```

- [ ] **Step 2: Run the tests to verify they fail (compile error)**

Run: `mvn -q -pl WPCore test -Dtest='TownPlanStamperTest' -DfailIfNoTests=false`
Expected: BUILD FAILURE — `computeDisc` and `erase` are undefined.

- [ ] **Step 3: Implement `computeDisc` and `erase`**

In `TownPlanStamper.java`, add these two methods after the existing `stamp` method (before the class's closing brace). The imports `java.awt.Point`, `java.util.HashSet`, `java.util.Set`, and `org.pepsoft.worldpainter.Dimension` / `org.pepsoft.worldpainter.layers.TownLayout` are already present in this file.

```java
    /**
     * Compute the world columns whose centre lies within {@code radius} (Euclidean, inclusive) of
     * {@code (centerX, centerZ)}. Radius 0 returns just the centre column.
     */
    public static Set<Point> computeDisc(int centerX, int centerZ, double radius) {
        final Set<Point> result = new HashSet<>();
        final int r = (int) Math.floor(radius);
        final double r2 = radius * radius;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (((dx * dx) + (dz * dz)) <= r2) {
                    result.add(new Point(centerX + dx, centerZ + dz));
                }
            }
        }
        return result;
    }

    /**
     * Clear the {@link TownLayout} footprint from every column in the disc around
     * {@code (centerX, centerZ)} of the given {@code radius}. Columns already clear are left as-is.
     *
     * @return the number of columns actually cleared (were set, now unset).
     */
    public static int erase(Dimension dimension, int centerX, int centerZ, double radius) {
        int cleared = 0;
        for (Point p : computeDisc(centerX, centerZ, radius)) {
            if (dimension.getBitLayerValueAt(TownLayout.INSTANCE, p.x, p.y)) {
                dimension.setBitLayerValueAt(TownLayout.INSTANCE, p.x, p.y, false);
                cleared++;
            }
        }
        return cleared;
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -q -pl WPCore test -Dtest='TownPlanStamperTest' -DfailIfNoTests=false`
Expected: BUILD SUCCESS — `Tests run: 10, Failures: 0, Errors: 0` (6 existing + 4 new).

- [ ] **Step 5: Commit**

```bash
git add WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/townplan/TownPlanStamper.java \
        WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/townplan/TownPlanStamperTest.java
git commit -m "feat(town-plan): add computeDisc + erase helpers for the erase brush"
```

---

## Task 3: Erase brush GUI wiring

No automated test (Swing mouse wiring); the pure geometry it depends on is covered by Task 2's `computeDisc`/`erase` tests. Verified by compiling WPGUI and a manual run.

**Files:**
- Modify: `WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/operations/TownPlanOperation.java`

- [ ] **Step 1: Add the two fields**

In `TownPlanOperation.java`, in the instance-field block at the bottom of the class, add after `private JCheckBox invertCheckBox;`:

```java
    private JToggleButton eraseToggle;
    private JSlider eraseRadiusSlider;
```

- [ ] **Step 2: Add the erase controls to the options panel**

In `buildOptionsPanel()`, immediately before `return panel;`, add:

```java
        eraseToggle = new JToggleButton("Erase footprint");
        eraseToggle.setToolTipText("When on, drag on the map to erase the stamped footprint under the brush");
        eraseRadiusSlider = new JSlider(1, 64, 8);
        eraseRadiusSlider.setToolTipText("Erase brush radius, in blocks");
        panel.add(new JLabel("Erase brush radius"));
        panel.add(eraseRadiusSlider);
        panel.add(eraseToggle);
```

- [ ] **Step 3: Add the erase helpers**

Add these two private methods to the class (e.g. directly after the `stamp()` method):

```java
    private boolean isErasing() {
        return (eraseToggle != null) && eraseToggle.isSelected();
    }

    /** Erase the stamped footprint under {@code world} using the current brush radius, and repaint. */
    private void eraseAt(Point world) {
        final Dimension dimension = getDimension();
        if (dimension == null) {
            return;
        }
        TownPlanStamper.erase(dimension, world.x, world.y, eraseRadiusSlider.getValue());
        ((WorldPainter) getView()).repaint();
    }
```

- [ ] **Step 4: Branch the mouse handler into erase mode**

In the `mouseHandler` anonymous `MouseAdapter`, add an erase branch at the top of `mousePressed` (immediately after the `final WorldPainter view = (WorldPainter) getView();` line):

```java
            if (isErasing()) {
                eraseAt(view.viewToWorld(e.getPoint()));
                return;
            }
```

Add the same branch at the top of `mouseDragged` (immediately after its `final WorldPainter view = (WorldPainter) getView();` line):

```java
            if (isErasing()) {
                eraseAt(view.viewToWorld(e.getPoint()));
                return;
            }
```

Replace the body of `mouseReleased` so an erase stroke is a single undo step:

```java
        @Override
        public void mouseReleased(MouseEvent e) {
            if (isErasing()) {
                final Dimension dimension = getDimension();
                if (dimension != null) {
                    dimension.armSavePoint();
                }
            }
            activeHandle = TownPlanPlacement.Handle.NONE;
        }
```

- [ ] **Step 5: Compile WPGUI to verify it builds**

Run: `mvn -q -pl WPGUI -am compile`
Expected: BUILD SUCCESS (no compile errors). `JToggleButton`, `JSlider`, `JLabel` resolve via the existing `import javax.swing.*;`; `Dimension`, `Point`, `TownPlanStamper`, `WorldPainter` are already imported.

- [ ] **Step 6: Commit**

```bash
git add WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/operations/TownPlanOperation.java
git commit -m "feat(town-plan): erase-brush toggle + radius slider in the Town Plan tool"
```

---

## Task 4: Full verification

**Files:** none (verification only).

- [ ] **Step 1: Run the whole town-plan + town-layout test set**

Run: `mvn -q -pl WPCore test -Dtest='TownLayoutExporterTest,TownLayoutSettingsTest,TownLayoutRendererTest,TownPlanStampIntegrationTest,TownPlanStamperTest,TownPlanPlacementTest' -DfailIfNoTests=false`
Expected: BUILD SUCCESS — `Tests run: 27, Failures: 0, Errors: 0` (baseline was 25; +2 settings/exporter and +4 stamper, −2 removed pillar tests → 29? recount at run time; the key check is 0 failures/errors).

Note: exact count will be whatever the edited files produce; the pass criterion is **0 failures and 0 errors**, not a specific total.

- [ ] **Step 2: Compile both modules together**

Run: `mvn -q -pl WPGUI -am test-compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Manual smoke test (record result, do not auto-pass)**

Build and run the app:

```bash
mvn -DskipTests=true -pl WPGUI -am install
mvn -pl WPGUI exec:exec
```

Then, in the app:
1. Tools → Town Plan, select an image, position it, click **Stamp** — the footprint appears as the Town Layout overlay.
2. Toggle **Erase footprint** on, set the radius, drag across part of the footprint — the footprint disappears under the brush as you drag.
3. Toggle erase off — dragging moves/scales/rotates the placement image again as before.
4. (Optional) Export a small test world and confirm the footprint block sits flush at the surface (replacing the top block), with nothing floating above it.

Record what actually happened. If anything misbehaves, treat it as a bug to investigate, not a pass.

---

## Self-review notes

- **Spec coverage:** Item 1 (surface placement) → Task 1; settings rename → Task 1; Item 2 core (computeDisc/erase) → Task 2; Item 2 GUI (toggle/slider/erase brush/undo) → Task 3; testing → Tasks 1, 2, 4. All spec sections map to a task.
- **Type consistency:** `getSurfaceDepth`/`setSurfaceDepth`/`surfaceDepth` used identically in settings, exporter, and all three tests. `placeSurfaceColumn(MinecraftWorld, int, int, int, int, Material)` signature matches its three test call sites. `computeDisc(int, int, double)` and `erase(Dimension, int, int, double)` match their test and GUI call sites (`eraseRadiusSlider.getValue()` int widens to the `double radius` parameter).
- **No placeholders:** every code step shows the exact code; every run step shows the command and expected outcome.
