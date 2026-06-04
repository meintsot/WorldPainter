# Town Plan Stamper — Backend Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the non-GUI foundation for the Town Plan Layout Stamper: extend the placement model, add a `TownLayout` layer + exporter that places marker blocks on both Minecraft and Hytale exports, and a pure image→footprint conversion core — all headless-testable.

**Architecture:** Mirror the proven `Annotations` layer + `AnnotationsExporter` pattern (a `SecondPassLayerExporter` run at the `ADD_FEATURES` stage, auto-discovered by reflection from package/class naming). Conversion is a pure function from a placed image + transform to a set of world columns; a thin adapter writes those columns into the layer. The map renderer, Tools menu, on-canvas handles and live preview are deliberately deferred to **Plan B (GUI)**.

**Tech Stack:** Java 17, Maven (module `WPCore`), JUnit 4, AWT `BufferedImage`/`AffineTransform`, `org.pepsoft.minecraft.Material`.

**Scope note — what this plan does NOT do (Plan B):** no `TownLayoutRenderer` (the layer will not draw on the map yet — fine, it is headless here), no Tools menu, no on-canvas handles, no live preview, no Layers-panel tab. This plan's deliverable is verified by tests and by programmatic stamping, not by clicking in the UI.

---

## File Structure

All paths are under `C:\Users\Sotirios\Desktop\WorldPainter\WorldPainter\`.

**Create:**
- `WPCore/src/main/java/org/pepsoft/worldpainter/layers/TownLayout.java` — the layer singleton (BIT, on/off).
- `WPCore/src/main/java/org/pepsoft/worldpainter/layers/exporters/TownLayoutSettings.java` — exporter settings (export flag, block, marker height).
- `WPCore/src/main/java/org/pepsoft/worldpainter/layers/exporters/TownLayoutExporter.java` — places marker blocks at ADD_FEATURES.
- `WPCore/src/main/java/org/pepsoft/worldpainter/townplan/TownPlanStamper.java` — pure footprint computation + Dimension write.
- `WPCore/src/test/java/org/pepsoft/worldpainter/OverlayTest.java`
- `WPCore/src/test/java/org/pepsoft/worldpainter/layers/exporters/TownLayoutExporterTest.java`
- `WPCore/src/test/java/org/pepsoft/worldpainter/townplan/TownPlanStamperTest.java`

**Modify:**
- `WPCore/src/main/java/org/pepsoft/worldpainter/Overlay.java` — add `rotation`, `threshold`, `invert`, `cropRect` properties.

**Build/test commands (run from `WorldPainter/` dir containing the parent `pom.xml`):**
- Compile core: `mvn -DskipTests=true -pl WPCore -am install`
- Run one test class: `mvn -pl WPCore -am test -Dtest=OverlayTest`

---

## Task 1: Extend the `Overlay` placement model

**Files:**
- Modify: `WPCore/src/main/java/org/pepsoft/worldpainter/Overlay.java`
- Test: `WPCore/src/test/java/org/pepsoft/worldpainter/OverlayTest.java`

`Overlay` already stores `scale, transparency, offsetX, offsetY, enabled` and fires `PropertyChangeSupport` events. We add four placement/conversion properties following the exact same getter/setter-with-event style. `java.awt.Rectangle` is `Serializable`, so adding it keeps the class serialization-compatible (old worlds deserialize with defaults via `defaultReadObject`).

- [ ] **Step 1: Write the failing test**

Create `WPCore/src/test/java/org/pepsoft/worldpainter/OverlayTest.java`:

```java
package org.pepsoft.worldpainter;

import org.junit.Test;

import java.awt.Rectangle;
import java.beans.PropertyChangeEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class OverlayTest {
    @Test
    public void defaultsAreSensible() {
        Overlay overlay = new Overlay(new File("plan.png"));
        assertEquals(0.0f, overlay.getRotation(), 0.0f);
        assertEquals(128, overlay.getThreshold());
        assertFalse(overlay.isInvert());
        assertNull(overlay.getCropRect());
    }

    @Test
    public void settersFirePropertyChanges() {
        Overlay overlay = new Overlay(new File("plan.png"));
        List<PropertyChangeEvent> events = new ArrayList<>();
        overlay.addPropertyChangeListener(events::add);

        overlay.setRotation(45.0f);
        overlay.setThreshold(90);
        overlay.setInvert(true);
        Rectangle crop = new Rectangle(10, 20, 100, 200);
        overlay.setCropRect(crop);

        assertEquals(45.0f, overlay.getRotation(), 0.0f);
        assertEquals(90, overlay.getThreshold());
        assertTrue(overlay.isInvert());
        assertEquals(crop, overlay.getCropRect());

        assertEquals(4, events.size());
        assertEquals("rotation", events.get(0).getPropertyName());
        assertEquals("threshold", events.get(1).getPropertyName());
        assertEquals("invert", events.get(2).getPropertyName());
        assertEquals("cropRect", events.get(3).getPropertyName());
    }

    @Test
    public void settingSameValueFiresNoEvent() {
        Overlay overlay = new Overlay(new File("plan.png"));
        List<PropertyChangeEvent> events = new ArrayList<>();
        overlay.addPropertyChangeListener(events::add);
        overlay.setRotation(0.0f); // already 0
        assertTrue(events.isEmpty());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl WPCore -am test -Dtest=OverlayTest`
Expected: FAIL — compilation error, `getRotation()`/`getThreshold()`/`isInvert()`/`getCropRect()` not defined on `Overlay`.

- [ ] **Step 3: Add the properties to `Overlay`**

In `Overlay.java`, add `import java.awt.Rectangle;` near the other imports. Add these accessors after the existing `setOffsetY(...)` method (before `isEnabled()`):

```java
    public float getRotation() {
        return rotation;
    }

    public void setRotation(float rotation) {
        if (rotation != this.rotation) {
            final float oldRotation = this.rotation;
            this.rotation = rotation;
            propertyChangeSupport.firePropertyChange("rotation", oldRotation, rotation);
        }
    }

    public int getThreshold() {
        return threshold;
    }

    public void setThreshold(int threshold) {
        if (threshold != this.threshold) {
            final int oldThreshold = this.threshold;
            this.threshold = threshold;
            propertyChangeSupport.firePropertyChange("threshold", oldThreshold, threshold);
        }
    }

    public boolean isInvert() {
        return invert;
    }

    public void setInvert(boolean invert) {
        if (invert != this.invert) {
            this.invert = invert;
            propertyChangeSupport.firePropertyChange("invert", ! invert, invert);
        }
    }

    public Rectangle getCropRect() {
        return cropRect;
    }

    public void setCropRect(Rectangle cropRect) {
        if (! Objects.equals(cropRect, this.cropRect)) {
            final Rectangle oldCropRect = this.cropRect;
            this.cropRect = cropRect;
            propertyChangeSupport.firePropertyChange("cropRect", oldCropRect, cropRect);
        }
    }
```

Then add the backing fields next to the existing `offsetX, offsetY` field declarations:

```java
    private float rotation = 0.0f;
    private int threshold = 128;
    private boolean invert = false;
    private Rectangle cropRect = null;
```

(`Objects` is already imported in `Overlay.java`.)

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl WPCore -am test -Dtest=OverlayTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/Overlay.java WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/OverlayTest.java
git commit -m "feat(town-plan): add rotation/threshold/invert/cropRect to Overlay"
```

---

## Task 2: Create the `TownLayout` layer

**Files:**
- Create: `WPCore/src/main/java/org/pepsoft/worldpainter/layers/TownLayout.java`
- Test: covered by Task 3's exporter test (the layer alone has no behavior to assert beyond identity).

A built-in singleton bit layer, modeled exactly on `layers/Annotations.java`. `Layer.init()` will reflectively look for `layers.renderers.TownLayoutRenderer` (absent in this plan → renderer stays `null`, acceptable headless) and `layers.exporters.TownLayoutExporter` (added in Task 3 → auto-discovered).

- [ ] **Step 1: Write the layer**

Create `WPCore/src/main/java/org/pepsoft/worldpainter/layers/TownLayout.java`:

```java
package org.pepsoft.worldpainter.layers;

/**
 * Marks the X-Z footprint of an imported town plan. Exported as marker blocks at the terrain
 * surface by {@code TownLayoutExporter}. A single on/off (BIT) layer.
 */
public class TownLayout extends Layer {
    private TownLayout() {
        super("org.pepsoft.TownLayout", "Town Layout",
                "Footprint of an imported town plan, exported as marker blocks", DataSize.BIT, true, 66);
    }

    public static final TownLayout INSTANCE = new TownLayout();

    private static final long serialVersionUID = 1L;
}
```

- [ ] **Step 2: Compile to verify it builds**

Run: `mvn -DskipTests=true -pl WPCore -am install`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/layers/TownLayout.java
git commit -m "feat(town-plan): add TownLayout bit layer"
```

---

## Task 3: Create `TownLayoutSettings` + `TownLayoutExporter`

**Files:**
- Create: `WPCore/src/main/java/org/pepsoft/worldpainter/layers/exporters/TownLayoutSettings.java`
- Create: `WPCore/src/main/java/org/pepsoft/worldpainter/layers/exporters/TownLayoutExporter.java`
- Test: `WPCore/src/test/java/org/pepsoft/worldpainter/layers/exporters/TownLayoutExporterTest.java`

The exporter mirrors `AnnotationsExporter`: a `SecondPassLayerExporter` running only at `ADD_FEATURES`, walking set columns and placing the configured `Material` from the surface up `markerHeight` blocks, only into insubstantial space. The per-column placement is extracted into a package-private static `placeMarkerColumn(...)` so it is unit-testable against an in-memory `MinecraftWorldObject` without constructing a `Dimension`. Because both `AbstractWorldExporter` (Minecraft) and `HytaleWorldExporter` discover and run `SecondPassLayerExporter`s, this lands blocks on both platforms with no export-pipeline changes.

- [ ] **Step 1: Write the failing test**

Create `WPCore/src/test/java/org/pepsoft/worldpainter/layers/exporters/TownLayoutExporterTest.java`:

```java
package org.pepsoft.worldpainter.layers.exporters;

import org.junit.Test;
import org.pepsoft.minecraft.Material;
import org.pepsoft.util.Box;
import org.pepsoft.worldpainter.objects.MinecraftWorldObject;

import static org.junit.Assert.assertEquals;
import static org.pepsoft.minecraft.Constants.BLK_WOOL;
import static org.pepsoft.minecraft.Material.AIR;

public class TownLayoutExporterTest {
    @Test
    public void placesPillarFromSurfaceUp() {
        // Volume: x 0..16, y 0..16, vertical z 0..64. maxHeight must be a power of two.
        Box volume = new Box(0, 16, 0, 16, 0, 64);
        MinecraftWorldObject world = new MinecraftWorldObject("test", volume, 256, 32);
        Material blackWool = Material.get(BLK_WOOL, 15);

        // Surface height 10, marker height 3 -> place at z = 11, 12, 13.
        TownLayoutExporter.placeMarkerColumn(world, 4, 4, 10, 3, blackWool);

        assertEquals(AIR, world.getMaterialAt(4, 4, 10));   // surface untouched
        assertEquals(blackWool, world.getMaterialAt(4, 4, 11));
        assertEquals(blackWool, world.getMaterialAt(4, 4, 12));
        assertEquals(blackWool, world.getMaterialAt(4, 4, 13));
        assertEquals(AIR, world.getMaterialAt(4, 4, 14));   // above the pillar
    }

    @Test
    public void stopsAtSolidBlock() {
        Box volume = new Box(0, 16, 0, 16, 0, 64);
        MinecraftWorldObject world = new MinecraftWorldObject("test", volume, 256, 32);
        Material blackWool = Material.get(BLK_WOOL, 15);
        Material stone = Material.get(1); // minecraft:stone, solid

        world.setMaterialAt(4, 4, 12, stone); // obstruction within the pillar
        TownLayoutExporter.placeMarkerColumn(world, 4, 4, 10, 3, blackWool);

        assertEquals(blackWool, world.getMaterialAt(4, 4, 11)); // placed below obstruction
        assertEquals(stone, world.getMaterialAt(4, 4, 12));     // obstruction preserved
        assertEquals(AIR, world.getMaterialAt(4, 4, 13));       // stopped, not placed
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl WPCore -am test -Dtest=TownLayoutExporterTest`
Expected: FAIL — `TownLayoutExporter` does not exist.

- [ ] **Step 3: Write `TownLayoutSettings`**

Create `WPCore/src/main/java/org/pepsoft/worldpainter/layers/exporters/TownLayoutSettings.java`. It implements the same `ExporterSettings` methods that `AnnotationsSettings` implements (`isApplyEverywhere()`, `getLayer()`), plus our own properties:

```java
package org.pepsoft.worldpainter.layers.exporters;

import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.layers.Layer;
import org.pepsoft.worldpainter.layers.TownLayout;

import static org.pepsoft.minecraft.Constants.BLK_WOOL;

public class TownLayoutSettings implements ExporterSettings {
    @Override
    public boolean isApplyEverywhere() {
        return false;
    }

    @Override
    public Layer getLayer() {
        return TownLayout.INSTANCE;
    }

    public boolean isExport() {
        return export;
    }

    public void setExport(boolean export) {
        this.export = export;
    }

    public Material getBlock() {
        return block;
    }

    public void setBlock(Material block) {
        this.block = block;
    }

    public int getMarkerHeight() {
        return markerHeight;
    }

    public void setMarkerHeight(int markerHeight) {
        this.markerHeight = markerHeight;
    }

    private boolean export = true;
    private Material block = Material.get(BLK_WOOL, 15); // black wool
    private int markerHeight = 3;

    private static final long serialVersionUID = 1L;
}
```

Note: if compilation reveals `ExporterSettings` declares additional abstract methods beyond the two above, implement them exactly as `AnnotationsSettings` does (open `layers/exporters/AnnotationsExporter.java`, inner class `AnnotationsSettings`, and copy the signatures).

- [ ] **Step 4: Write `TownLayoutExporter`**

Create `WPCore/src/main/java/org/pepsoft/worldpainter/layers/exporters/TownLayoutExporter.java`:

```java
package org.pepsoft.worldpainter.layers.exporters;

import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Platform;
import org.pepsoft.worldpainter.exporting.AbstractLayerExporter;
import org.pepsoft.worldpainter.exporting.Fixup;
import org.pepsoft.worldpainter.exporting.MinecraftWorld;
import org.pepsoft.worldpainter.exporting.SecondPassLayerExporter;
import org.pepsoft.worldpainter.layers.TownLayout;

import java.awt.Rectangle;
import java.util.List;
import java.util.Set;

import static java.util.Collections.singleton;
import static org.pepsoft.worldpainter.exporting.SecondPassLayerExporter.Stage.ADD_FEATURES;

public class TownLayoutExporter extends AbstractLayerExporter<TownLayout> implements SecondPassLayerExporter {
    public TownLayoutExporter(Dimension dimension, Platform platform, ExporterSettings settings) {
        super(dimension, platform, (settings != null) ? settings : new TownLayoutSettings(), TownLayout.INSTANCE);
    }

    @Override
    public Set<Stage> getStages() {
        return singleton(ADD_FEATURES);
    }

    @Override
    public List<Fixup> addFeatures(Rectangle area, Rectangle exportedArea, MinecraftWorld minecraftWorld) {
        final TownLayoutSettings settings = (TownLayoutSettings) super.settings;
        if (! settings.isExport()) {
            return null;
        }
        final Material block = settings.getBlock();
        final int markerHeight = Math.max(1, settings.getMarkerHeight());
        for (int x = area.x; x < area.x + area.width; x++) {
            for (int y = area.y; y < area.y + area.height; y++) {
                if (dimension.getBitLayerValueAt(TownLayout.INSTANCE, x, y)) {
                    placeMarkerColumn(minecraftWorld, x, y, dimension.getIntHeightAt(x, y), markerHeight, block);
                }
            }
        }
        return null;
    }

    /**
     * Place a marker pillar of {@code block} starting one block above {@code terrainHeight}, up to
     * {@code markerHeight} blocks tall. Only fills insubstantial space; stops at the first solid block
     * and never exceeds the world's maximum height.
     */
    static void placeMarkerColumn(MinecraftWorld world, int x, int y, int terrainHeight, int markerHeight, Material block) {
        final int maxZ = world.getMaxHeight() - 1;
        for (int dz = 1; dz <= markerHeight; dz++) {
            final int z = terrainHeight + dz;
            if (z > maxZ) {
                break;
            }
            final Material existing = world.getMaterialAt(x, y, z);
            if (existing.veryInsubstantial || (existing == Material.ICE)) {
                world.setMaterialAt(x, y, z, block);
            } else {
                break;
            }
        }
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -pl WPCore -am test -Dtest=TownLayoutExporterTest`
Expected: PASS (2 tests). If `Material.get(1)` for stone is not recognized, substitute any solid material constant from `org.pepsoft.minecraft.Material` (e.g. `Material.STONE`) — verify the exact constant name in `Material.java`.

- [ ] **Step 6: Verify the exporter auto-wires to the layer**

Run: `mvn -pl WPCore -am test -Dtest=TownLayoutExporterTest` again is sufficient, but also confirm discovery by adding this assertion to the test class and re-running:

```java
    @Test
    public void layerResolvesExporterType() {
        assertEquals(TownLayoutExporter.class, TownLayout.INSTANCE.getExporterType());
    }
```

Add `import org.pepsoft.worldpainter.layers.TownLayout;` to the test imports.
Expected: PASS — confirms `Layer.init()` reflectively found `TownLayoutExporter`.

- [ ] **Step 7: Commit**

```bash
git add WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/layers/exporters/TownLayoutSettings.java WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/layers/exporters/TownLayoutExporter.java WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/layers/exporters/TownLayoutExporterTest.java
git commit -m "feat(town-plan): TownLayout exporter places surface marker blocks (MC + Hytale)"
```

---

## Task 4: Pure image→footprint conversion core (`TownPlanStamper`)

**Files:**
- Create: `WPCore/src/main/java/org/pepsoft/worldpainter/townplan/TownPlanStamper.java`
- Test: `WPCore/src/test/java/org/pepsoft/worldpainter/townplan/TownPlanStamperTest.java`

`computeFootprint(...)` is a pure function: for each world column in `worldArea`, inverse-transform it into image-pixel space (undo translation → rotation → scale), reject if outside the image or the crop rectangle or fully transparent, then include it if the pixel's brightness passes the threshold. The transform is defined explicitly here so **Plan B's on-canvas rendering must use the identical mapping**:

```
world(wx, wz) -> pixel:
  dx = wx - originX ; dz = wz - originZ           (block offset from image origin = top-left)
  (rx, rz) = rotate(dx, dz) by -rotationDeg        (undo the image's on-map rotation)
  u = rx / blocksPerPixel ; v = rz / blocksPerPixel
```

A separate `stamp(...)` writes the computed columns into a `Dimension` via `setLayerValueAt`, keeping all `Dimension` coupling in a 3-line loop.

- [ ] **Step 1: Write the failing test**

Create `WPCore/src/test/java/org/pepsoft/worldpainter/townplan/TownPlanStamperTest.java`:

```java
package org.pepsoft.worldpainter.townplan;

import org.junit.Test;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Set;

import static org.junit.Assert.*;

public class TownPlanStamperTest {
    /** 2x2 image: only the top-left pixel (0,0) is black; the rest white. */
    private static BufferedImage topLeftBlack() {
        BufferedImage img = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, 0xFF000000); // opaque black
        img.setRGB(1, 0, 0xFFFFFFFF);
        img.setRGB(0, 1, 0xFFFFFFFF);
        img.setRGB(1, 1, 0xFFFFFFFF);
        return img;
    }

    @Test
    public void darkPixelMapsToColumnAtUnitScale() {
        Set<Point> cols = TownPlanStamper.computeFootprint(
                topLeftBlack(), 0, 0, 1.0, 0.0, null, 128, false,
                new Rectangle(0, 0, 2, 2));
        assertEquals(1, cols.size());
        assertTrue(cols.contains(new Point(0, 0)));
    }

    @Test
    public void scaleExpandsOnePixelToBlockBlock() {
        // blocksPerPixel = 2 -> the single black pixel covers a 2x2 block area at world origin.
        Set<Point> cols = TownPlanStamper.computeFootprint(
                topLeftBlack(), 0, 0, 2.0, 0.0, null, 128, false,
                new Rectangle(0, 0, 4, 4));
        assertEquals(4, cols.size());
        assertTrue(cols.contains(new Point(0, 0)));
        assertTrue(cols.contains(new Point(1, 0)));
        assertTrue(cols.contains(new Point(0, 1)));
        assertTrue(cols.contains(new Point(1, 1)));
    }

    @Test
    public void invertSelectsLightPixels() {
        Set<Point> cols = TownPlanStamper.computeFootprint(
                topLeftBlack(), 0, 0, 1.0, 0.0, null, 128, true,
                new Rectangle(0, 0, 2, 2));
        assertEquals(3, cols.size());               // the three white pixels
        assertFalse(cols.contains(new Point(0, 0))); // not the black one
    }

    @Test
    public void cropRectExcludesOutsidePixels() {
        // Crop to just the black pixel's 1x1 region; nothing else qualifies anyway, but
        // verify a crop that excludes (0,0) yields empty.
        Set<Point> cols = TownPlanStamper.computeFootprint(
                topLeftBlack(), 0, 0, 1.0, 0.0, new Rectangle(1, 0, 1, 2), 128, false,
                new Rectangle(0, 0, 2, 2));
        assertTrue(cols.isEmpty());
    }

    @Test
    public void fullyTransparentPixelsAreIgnored() {
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, 0x00000000); // transparent black
        Set<Point> cols = TownPlanStamper.computeFootprint(
                img, 0, 0, 1.0, 0.0, null, 128, false, new Rectangle(0, 0, 1, 1));
        assertTrue(cols.isEmpty());
    }

    @Test
    public void rotation90MapsCorrectly() {
        // Image origin at world (0,0), 90 deg rotation. The black pixel at image (0,0) stays at
        // the origin (rotation pivot), so world column (0,0) is still selected.
        Set<Point> cols = TownPlanStamper.computeFootprint(
                topLeftBlack(), 0, 0, 1.0, 90.0, null, 128, false,
                new Rectangle(-2, -2, 4, 4));
        assertTrue(cols.contains(new Point(0, 0)));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl WPCore -am test -Dtest=TownPlanStamperTest`
Expected: FAIL — `TownPlanStamper` does not exist.

- [ ] **Step 3: Write `TownPlanStamper`**

Create `WPCore/src/main/java/org/pepsoft/worldpainter/townplan/TownPlanStamper.java`:

```java
package org.pepsoft.worldpainter.townplan;

import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.layers.TownLayout;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;

/**
 * Pure conversion from a placed town-plan image to the set of world columns that form its footprint,
 * plus a thin adapter to write those columns into a {@link Dimension}'s {@link TownLayout} layer.
 *
 * <p>Transform convention (must match the on-map rendering in the GUI): the image's top-left pixel
 * (0,0) sits at world block ({@code originX}, {@code originZ}); the image is scaled by
 * {@code blocksPerPixel} and rotated {@code rotationDeg} degrees clockwise about that origin.
 */
public final class TownPlanStamper {
    private TownPlanStamper() {
    }

    /**
     * Compute the world columns covered by the dark (or, if {@code invert}, light) parts of the image.
     *
     * @param image         the plan image (ARGB or RGB).
     * @param originX       world block X of image pixel (0,0).
     * @param originZ       world block Z of image pixel (0,0).
     * @param blocksPerPixel world blocks per image pixel (image scale). Must be > 0.
     * @param rotationDeg   clockwise rotation of the image on the map, in degrees.
     * @param cropPx        optional crop rectangle in image-pixel space; only pixels inside it count. May be null.
     * @param threshold     brightness threshold 0..255.
     * @param invert        if false, pixels darker than the threshold are selected; if true, lighter.
     * @param worldArea     the world block area to scan.
     * @return the set of selected world columns (Point.x = world X, Point.y = world Z).
     */
    public static Set<Point> computeFootprint(BufferedImage image, double originX, double originZ,
                                              double blocksPerPixel, double rotationDeg, Rectangle cropPx,
                                              int threshold, boolean invert, Rectangle worldArea) {
        if (blocksPerPixel <= 0.0) {
            throw new IllegalArgumentException("blocksPerPixel must be > 0");
        }
        final Set<Point> result = new HashSet<>();
        final int imgW = image.getWidth(), imgH = image.getHeight();
        final double theta = Math.toRadians(rotationDeg);
        // Inverse rotation (by -theta): [ cos  sin ; -sin  cos ]
        final double cos = Math.cos(theta), sin = Math.sin(theta);
        for (int wx = worldArea.x; wx < worldArea.x + worldArea.width; wx++) {
            for (int wz = worldArea.y; wz < worldArea.y + worldArea.height; wz++) {
                final double dx = wx - originX, dz = wz - originZ;
                final double rx = cos * dx + sin * dz;
                final double rz = -sin * dx + cos * dz;
                final int u = (int) Math.floor(rx / blocksPerPixel);
                final int v = (int) Math.floor(rz / blocksPerPixel);
                if ((u < 0) || (u >= imgW) || (v < 0) || (v >= imgH)) {
                    continue;
                }
                if ((cropPx != null) && (! cropPx.contains(u, v))) {
                    continue;
                }
                final int argb = image.getRGB(u, v);
                final int alpha = (argb >>> 24) & 0xff;
                if (alpha == 0) {
                    continue; // fully transparent -> empty
                }
                final int r = (argb >> 16) & 0xff, g = (argb >> 8) & 0xff, b = argb & 0xff;
                final int brightness = (r + g + b) / 3;
                final boolean selected = invert ? (brightness > threshold) : (brightness < threshold);
                if (selected) {
                    result.add(new Point(wx, wz));
                }
            }
        }
        return result;
    }

    /**
     * Compute the footprint for the given parameters and write it into the dimension's
     * {@link TownLayout} layer (value 1). Existing layer values are left untouched (additive).
     *
     * @return the number of columns set.
     */
    public static int stamp(Dimension dimension, BufferedImage image, double originX, double originZ,
                            double blocksPerPixel, double rotationDeg, Rectangle cropPx,
                            int threshold, boolean invert, Rectangle worldArea) {
        final Set<Point> columns = computeFootprint(image, originX, originZ, blocksPerPixel, rotationDeg,
                cropPx, threshold, invert, worldArea);
        for (Point p : columns) {
            dimension.setBitLayerValueAt(TownLayout.INSTANCE, p.x, p.y, true);
        }
        return columns.size();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl WPCore -am test -Dtest=TownPlanStamperTest`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/townplan/TownPlanStamper.java WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/townplan/TownPlanStamperTest.java
git commit -m "feat(town-plan): pure image->footprint conversion core (TownPlanStamper)"
```

---

## Task 5: Full-module build + final commit

**Files:** none (verification only).

- [ ] **Step 1: Compile and run all three new test classes together**

Run: `mvn -pl WPCore -am test -Dtest=OverlayTest,TownLayoutExporterTest,TownPlanStamperTest`
Expected: PASS — all tests green, confirming the foundation builds and behaves.

- [ ] **Step 2: Full core compile (catches any wider breakage)**

Run: `mvn -DskipTests=true -pl WPCore -am install`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Confirm clean tree**

```bash
git status
```
Expected: clean (all work committed in Tasks 1-4).

---

## What Plan B (GUI) will add — context only, not built here

- `layers/renderers/TownLayoutRenderer.java` so the layer draws on the map (mirror a simple bit-layer renderer). **Must exist before** the layer is wired into the Layers panel/brush, to avoid a null-renderer NPE in the view.
- `Tools → Town Plan…` menu item in `App.java` opening the placement mode.
- On-canvas move/rotate/scale handles in `WorldPainter.java` + `GlassPane.java`, applying the **same transform** documented in `TownPlanStamper` (origin = image top-left at world `originX/originZ`, `blocksPerPixel` scale, clockwise `rotationDeg`).
- A live red preview overlay running `computeFootprint(...)` continuously.
- A "Town Plan" side panel (threshold slider, invert, crop, marker height, block picker, **Set** button calling `TownPlanStamper.stamp(...)`).
- Layers-panel tab (mirror `createAnnotationsPanel`) + `DimensionPropertiesEditor` export checkbox bound to `TownLayoutSettings.isExport()`.
- A Hytale-specific block picker (resolve from `HytaleBlockRegistry`) for `TownLayoutSettings.setBlock(...)`; the default black wool already exports on both platforms.

---

## Self-Review

- **Spec coverage (against §2/§4/§5 of the design):** placement model fields (rotation/threshold/invert/crop) → Task 1; non-destructive re-editable layer → Task 2 (additive `stamp`, brush-editable like any layer); surface-following marker + configurable block + marker height → Task 3; threshold + invert + crop + alpha conversion → Task 4; both-platform export → verified by exporter discovery (Task 3 Step 6) + confirmed `HytaleWorldExporter`/`AbstractWorldExporter` both run `addFeatures`. GUI items (on-canvas handles, live preview, Tools menu) are explicitly Plan B, matching the agreed decomposition.
- **Placeholder scan:** no TBD/TODO; the only conditional notes (ExporterSettings extra methods in Task 3 Step 3; the stone constant in Task 3 Step 5) point to an exact sibling file to copy from, not vague guidance.
- **Type consistency:** `TownLayout.INSTANCE`, `TownLayoutSettings` (export/block/markerHeight), `TownLayoutExporter.placeMarkerColumn(MinecraftWorld,int,int,int,int,Material)`, and `TownPlanStamper.computeFootprint(...)/stamp(...)` signatures are used identically across tasks and tests. `Point.x` = world X, `Point.y` = world Z is consistent throughout.
