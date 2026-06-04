# Town Plan GUI B1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the `TownLayout` layer visible/editable in the GUI and add a palette **Town Plan** tool that drags/rotates/scales a plan image on the map and stamps its footprint into the layer.

**Architecture:** Two pure, unit-tested cores in WPCore (`TownLayoutRenderer`; `TownPlanPlacement` geometry), then thin WPGUI glue (rotated overlay rendering; a custom `TownPlanOperation` that installs its own mouse handlers and drives the view + `TownPlanStamper`). The placement transform exactly matches `TownPlanStamper`'s convention (image pixel→world = `origin + R(θ)·(scale·pixel)`, `origin` = world pos of pixel (0,0), θ clockwise), so what you drag is what gets stamped; rotate/scale feel center-based by recomputing the stored top-left origin to keep the image center fixed.

**Tech Stack:** Java 17, Maven, JUnit 4, AWT geometry (`Math`, `Point2D`), Swing (`JPanel`, `MouseListener`), the existing `Operation`/`AbstractOperation` framework.

**Build/test commands** (from `C:/Users/Sotirios/Desktop/WorldPainter/WorldPainter`):
- WPCore tests: `mvn -pl WPCore -am test -Dtest=<Class>`
- Compile WPGUI (needs JIDE eval jars per BUILDING.md): `mvn -DskipTests=true -pl WPGUI -am install`
- Run the app: `mvn -pl WPGUI exec:exec`

**Verification note:** Tasks 1–2 are fully unit-tested (WPCore, no GUI). Tasks 3–4 are WPGUI glue that is **build- and run-verified** (Swing/JIDE can't be meaningfully unit-tested here); each lists the exact manual check.

---

## File Structure

Paths under `C:/Users/Sotirios/Desktop/WorldPainter/WorldPainter/`.

**Create:**
- `WPCore/src/main/java/org/pepsoft/worldpainter/layers/renderers/TownLayoutRenderer.java` — magenta bit-layer renderer.
- `WPCore/src/main/java/org/pepsoft/worldpainter/townplan/TownPlanPlacement.java` — pure placement geometry (corners, hit-test, move/rotate/scale).
- `WPGUI/src/main/java/org/pepsoft/worldpainter/operations/TownPlanOperation.java` — the palette tool + its options panel.
- `WPCore/src/test/java/org/pepsoft/worldpainter/townplan/TownPlanPlacementTest.java`
- `WPCore/src/test/java/org/pepsoft/worldpainter/layers/renderers/TownLayoutRendererTest.java`

**Modify:**
- `WPCore/src/main/java/org/pepsoft/worldpainter/DefaultPlugin.java` — register `TownLayout.INSTANCE`.
- `WPGUI/src/main/java/org/pepsoft/worldpainter/WorldPainter.java` — rotate overlays in `drawOverlays()`; draw placement handles; hold placement state.
- `WPGUI/src/main/java/org/pepsoft/worldpainter/App.java` — add the tool button in `createToolPanel()`.

---

## Task 1: Visible layer — `TownLayoutRenderer` + register in `DefaultPlugin`

**Files:**
- Create: `WPCore/src/main/java/org/pepsoft/worldpainter/layers/renderers/TownLayoutRenderer.java`
- Test: `WPCore/src/test/java/org/pepsoft/worldpainter/layers/renderers/TownLayoutRendererTest.java`
- Modify: `WPCore/src/main/java/org/pepsoft/worldpainter/DefaultPlugin.java` (line 38 layer list)

`BitLayerRenderer` is the one-method interface `int getPixelColour(int x, int y, int underlyingColour, boolean value)` (see sibling `FrostRenderer`). Magenta `0xFF00FF` where set; underlying colour otherwise. Auto-discovered via the `renderers.<Layer>Renderer` naming convention used by `Layer.init()`.

- [ ] **Step 1: Write the failing test**

Create `WPCore/src/test/java/org/pepsoft/worldpainter/layers/renderers/TownLayoutRendererTest.java`:

```java
package org.pepsoft.worldpainter.layers.renderers;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TownLayoutRendererTest {
    @Test
    public void setColumnsRenderMagenta() {
        TownLayoutRenderer renderer = new TownLayoutRenderer();
        assertEquals(0xFF00FF, renderer.getPixelColour(0, 0, 0x123456, true));
    }

    @Test
    public void unsetColumnsKeepUnderlyingColour() {
        TownLayoutRenderer renderer = new TownLayoutRenderer();
        assertEquals(0x123456, renderer.getPixelColour(0, 0, 0x123456, false));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl WPCore -am test -Dtest=TownLayoutRendererTest`
Expected: FAIL — `TownLayoutRenderer` does not exist.

- [ ] **Step 3: Write the renderer**

Create `WPCore/src/main/java/org/pepsoft/worldpainter/layers/renderers/TownLayoutRenderer.java`:

```java
package org.pepsoft.worldpainter.layers.renderers;

/**
 * Renders the {@code TownLayout} footprint as a high-visibility magenta on the map. This is an
 * editor-only colour and is independent of the block the layer exports.
 */
public class TownLayoutRenderer implements BitLayerRenderer {
    @Override
    public int getPixelColour(int x, int y, int underlyingColour, boolean value) {
        return value ? 0xFF00FF : underlyingColour;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl WPCore -am test -Dtest=TownLayoutRendererTest`
Expected: PASS (2 tests).

- [ ] **Step 5: Register the layer in `DefaultPlugin`**

In `WPCore/src/main/java/org/pepsoft/worldpainter/DefaultPlugin.java`, the `getLayers()` method returns `Arrays.asList(Frost.INSTANCE, Caves.INSTANCE, …, Resources.INSTANCE/*, River.INSTANCE*/, …)`. Add `org.pepsoft.worldpainter.layers.TownLayout.INSTANCE` to that list (append it after the last real entry — keep it before any trailing commented-out entries are irrelevant; just add it as another element). Example edit (add the element):

```java
        return Arrays.asList(Frost.INSTANCE, Caves.INSTANCE, Caverns.INSTANCE, Chasms.INSTANCE, DeciduousForest.INSTANCE, PineForest.INSTANCE, SwampLand.INSTANCE, Jungle.INSTANCE, org.pepsoft.worldpainter.layers.Void.INSTANCE, Resources.INSTANCE, org.pepsoft.worldpainter.layers.TownLayout.INSTANCE);
```

(Match the existing element list exactly as it is in the file; only ADD the `TownLayout.INSTANCE` element. If the existing list spans multiple lines or has more entries than shown, preserve them and just append the new element.)

- [ ] **Step 6: Compile to verify registration builds**

Run: `mvn -DskipTests=true -pl WPCore -am install`
Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/layers/renderers/TownLayoutRenderer.java WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/layers/renderers/TownLayoutRendererTest.java WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/DefaultPlugin.java
git commit -m "feat(town-plan): TownLayoutRenderer (magenta) + register TownLayout as a built-in layer"
```
End the message with a blank line then:
`Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`

---

## Task 2: Pure placement geometry — `TownPlanPlacement`

**Files:**
- Create: `WPCore/src/main/java/org/pepsoft/worldpainter/townplan/TownPlanPlacement.java`
- Test: `WPCore/src/test/java/org/pepsoft/worldpainter/townplan/TownPlanPlacementTest.java`

The placement state is the same `(originX, originZ, scale, rotationDeg)` that `TownPlanStamper` consumes (`origin` = world pos of image pixel (0,0); `scale` = blocks per pixel; θ clockwise). This helper computes handle world positions, hit-tests a world point, and produces updated state for move/rotate/scale gestures. Rotate and scale keep the **image center** fixed (natural feel) by recomputing `origin`. All pure; unit-tested.

Forward transform (must match `TownPlanStamper`): `world = origin + scale·R(θ)·pixel`, `R(θ) = [cos −sin; sin cos]`. Inverse (point-in-image test): `pixel = (1/scale)·R(−θ)·(world − origin)`, `R(−θ) = [cos sin; −sin cos]`.

- [ ] **Step 1: Write the failing test**

Create `WPCore/src/test/java/org/pepsoft/worldpainter/townplan/TownPlanPlacementTest.java`:

```java
package org.pepsoft.worldpainter.townplan;

import org.junit.Test;

import java.awt.geom.Point2D;

import static org.junit.Assert.*;
import static org.pepsoft.worldpainter.townplan.TownPlanPlacement.Handle;

public class TownPlanPlacementTest {
    private static final double EPS = 1e-6;

    @Test
    public void pixelToWorldNoRotation() {
        Point2D.Double p = TownPlanPlacement.pixelToWorld(10, 4, 100, 200, 2.0, 0.0);
        assertEquals(120.0, p.x, EPS); // 100 + 2*10
        assertEquals(208.0, p.y, EPS); // 200 + 2*4
    }

    @Test
    public void pixelToWorld90Clockwise() {
        // theta=90: R=[0 -1; 1 0]; world = origin + scale*( -v, u )
        Point2D.Double p = TownPlanPlacement.pixelToWorld(10, 0, 0, 0, 1.0, 90.0);
        assertEquals(0.0, p.x, EPS);   // -v*scale = 0
        assertEquals(10.0, p.y, EPS);  //  u*scale = 10
    }

    @Test
    public void hitTestCornersBodyAndOutside() {
        // 100x80 image, origin (0,0), scale 1, no rotation: corners at (0,0),(100,0),(0,80),(100,80).
        double handleR = 5.0;
        assertEquals(Handle.NW, TownPlanPlacement.hitTest(0, 0, 0, 0, 1.0, 0.0, 100, 80, handleR));
        assertEquals(Handle.SE, TownPlanPlacement.hitTest(100, 80, 0, 0, 1.0, 0.0, 100, 80, handleR));
        assertEquals(Handle.BODY, TownPlanPlacement.hitTest(50, 40, 0, 0, 1.0, 0.0, 100, 80, handleR));
        assertEquals(Handle.NONE, TownPlanPlacement.hitTest(500, 500, 0, 0, 1.0, 0.0, 100, 80, handleR));
    }

    @Test
    public void hitTestRotateHandleAboveTopEdge() {
        // Rotate handle sits 'rotateOffset' world units beyond the top-edge midpoint (50,0),
        // in the outward (negative v) direction => (50, -rotateOffset).
        double handleR = 5.0;
        double off = TownPlanPlacement.ROTATE_HANDLE_WORLD_OFFSET;
        assertEquals(Handle.ROTATE, TownPlanPlacement.hitTest(50, -off, 0, 0, 1.0, 0.0, 100, 80, handleR));
    }

    @Test
    public void moveTranslatesOrigin() {
        double[] s = TownPlanPlacement.applyMove(100, 200, 5, -3); // dx=5, dz=-3
        assertEquals(105.0, s[0], EPS); // originX
        assertEquals(197.0, s[1], EPS); // originZ
    }

    @Test
    public void rotateKeepsCenterFixed() {
        int W = 100, H = 80;
        double originX = 0, originZ = 0, scale = 1.0, theta = 0.0;
        Point2D.Double centerBefore = TownPlanPlacement.centerWorld(originX, originZ, scale, theta, W, H);
        // Mouse straight to the right of the center => up-vector points right => theta = +90 (deg).
        double[] s = TownPlanPlacement.applyRotate(centerBefore.x + 50, centerBefore.y, originX, originZ, scale, theta, W, H);
        assertEquals(90.0, normalize(s[2]), 1e-6);
        Point2D.Double centerAfter = TownPlanPlacement.centerWorld(s[0], s[1], scale, s[2], W, H);
        assertEquals(centerBefore.x, centerAfter.x, 1e-6);
        assertEquals(centerBefore.y, centerAfter.y, 1e-6);
    }

    @Test
    public void scaleKeepsCenterFixedAndMatchesCornerDistance() {
        int W = 100, H = 80;
        double originX = 0, originZ = 0, scale = 1.0, theta = 0.0;
        Point2D.Double center = TownPlanPlacement.centerWorld(originX, originZ, scale, theta, W, H); // (50,40)
        double halfDiagPixels = Math.hypot(W / 2.0, H / 2.0);
        // Put the mouse at twice the current corner distance => scale doubles.
        double targetDist = 2.0 * scale * halfDiagPixels;
        double[] s = TownPlanPlacement.applyScale(center.x + targetDist, center.y, originX, originZ, scale, theta, W, H);
        assertEquals(2.0, s[2], 1e-6); // new scale
        Point2D.Double centerAfter = TownPlanPlacement.centerWorld(s[0], s[1], s[2], theta, W, H);
        assertEquals(center.x, centerAfter.x, 1e-6);
        assertEquals(center.y, centerAfter.y, 1e-6);
    }

    private static double normalize(double deg) {
        double d = deg % 360.0;
        if (d < 0) {
            d += 360.0;
        }
        return d;
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl WPCore -am test -Dtest=TownPlanPlacementTest`
Expected: FAIL — `TownPlanPlacement` does not exist.

- [ ] **Step 3: Write `TownPlanPlacement`**

Create `WPCore/src/main/java/org/pepsoft/worldpainter/townplan/TownPlanPlacement.java`:

```java
package org.pepsoft.worldpainter.townplan;

import java.awt.geom.Point2D;

/**
 * Pure geometry for placing/transforming a town-plan image on the map. State is
 * {@code (originX, originZ, scale, rotationDeg)} where {@code origin} is the world position of
 * image pixel (0,0), {@code scale} is world blocks per image pixel, and rotation is clockwise
 * degrees. The convention matches {@link TownPlanStamper}: world = origin + scale·R(θ)·pixel,
 * R(θ) = [cos −sin; sin cos].
 *
 * <p>Returned state arrays are {@code [originX, originZ, scale, rotationDeg]} (rotate/scale also
 * return the recomputed origin so the image center stays fixed). Move returns {@code [originX, originZ]}.
 */
public final class TownPlanPlacement {
    private TownPlanPlacement() {
    }

    public enum Handle { BODY, NW, NE, SW, SE, ROTATE, NONE }

    /** World-unit distance the rotate handle sits beyond the top-edge midpoint. */
    public static final double ROTATE_HANDLE_WORLD_OFFSET = 24.0;

    public static Point2D.Double pixelToWorld(double u, double v, double originX, double originZ,
                                              double scale, double thetaDeg) {
        final double rad = Math.toRadians(thetaDeg);
        final double cos = Math.cos(rad), sin = Math.sin(rad);
        return new Point2D.Double(
                originX + scale * (cos * u - sin * v),
                originZ + scale * (sin * u + cos * v));
    }

    /** Inverse of {@link #pixelToWorld}: world point -> image pixel coordinates. */
    public static Point2D.Double worldToPixel(double wx, double wz, double originX, double originZ,
                                              double scale, double thetaDeg) {
        final double rad = Math.toRadians(thetaDeg);
        final double cos = Math.cos(rad), sin = Math.sin(rad);
        final double dx = wx - originX, dz = wz - originZ;
        return new Point2D.Double((cos * dx + sin * dz) / scale, (-sin * dx + cos * dz) / scale);
    }

    public static Point2D.Double centerWorld(double originX, double originZ, double scale,
                                             double thetaDeg, int imgW, int imgH) {
        return pixelToWorld(imgW / 2.0, imgH / 2.0, originX, originZ, scale, thetaDeg);
    }

    /** World position of the rotate handle (beyond the top-edge midpoint, outward). */
    public static Point2D.Double rotateHandleWorld(double originX, double originZ, double scale,
                                                   double thetaDeg, int imgW, int imgH) {
        final Point2D.Double topMid = pixelToWorld(imgW / 2.0, 0, originX, originZ, scale, thetaDeg);
        final Point2D.Double center = centerWorld(originX, originZ, scale, thetaDeg, imgW, imgH);
        double dirX = topMid.x - center.x, dirZ = topMid.y - center.y;
        final double len = Math.hypot(dirX, dirZ);
        if (len < 1e-9) {
            return topMid;
        }
        dirX /= len;
        dirZ /= len;
        return new Point2D.Double(topMid.x + dirX * ROTATE_HANDLE_WORLD_OFFSET,
                topMid.y + dirZ * ROTATE_HANDLE_WORLD_OFFSET);
    }

    /**
     * Determine which handle (or the body, or nothing) the given world point hits. Corner and rotate
     * handles win over the body; {@code handleRadius} is the pick tolerance in world units.
     */
    public static Handle hitTest(double wx, double wz, double originX, double originZ, double scale,
                                 double thetaDeg, int imgW, int imgH, double handleRadius) {
        final double r2 = handleRadius * handleRadius;
        if (near(wx, wz, rotateHandleWorld(originX, originZ, scale, thetaDeg, imgW, imgH), r2)) {
            return Handle.ROTATE;
        }
        if (near(wx, wz, pixelToWorld(0, 0, originX, originZ, scale, thetaDeg), r2)) {
            return Handle.NW;
        }
        if (near(wx, wz, pixelToWorld(imgW, 0, originX, originZ, scale, thetaDeg), r2)) {
            return Handle.NE;
        }
        if (near(wx, wz, pixelToWorld(0, imgH, originX, originZ, scale, thetaDeg), r2)) {
            return Handle.SW;
        }
        if (near(wx, wz, pixelToWorld(imgW, imgH, originX, originZ, scale, thetaDeg), r2)) {
            return Handle.SE;
        }
        final Point2D.Double px = worldToPixel(wx, wz, originX, originZ, scale, thetaDeg);
        if ((px.x >= 0) && (px.x <= imgW) && (px.y >= 0) && (px.y <= imgH)) {
            return Handle.BODY;
        }
        return Handle.NONE;
    }

    /** Move gesture: translate the origin by the world drag delta. Returns {@code [originX, originZ]}. */
    public static double[] applyMove(double originX, double originZ, double dWorldX, double dWorldZ) {
        return new double[] { originX + dWorldX, originZ + dWorldZ };
    }

    /**
     * Rotate gesture: set rotation so the image's 'up' (center -> top-edge) points at the mouse, keeping
     * the center fixed. Returns {@code [originX, originZ, scale, rotationDeg]}.
     */
    public static double[] applyRotate(double mouseWx, double mouseWz, double originX, double originZ,
                                       double scale, double thetaDeg, int imgW, int imgH) {
        final Point2D.Double center = centerWorld(originX, originZ, scale, thetaDeg, imgW, imgH);
        final double phiDeg = Math.toDegrees(Math.atan2(mouseWz - center.y, mouseWx - center.x));
        final double newTheta = phiDeg + 90.0; // 'up' direction angle equals theta - 90
        final double[] s = originForFixedCenter(center.x, center.y, scale, newTheta, imgW, imgH);
        return new double[] { s[0], s[1], scale, newTheta };
    }

    /**
     * Scale gesture: set scale so the dragged corner sits at the mouse distance from the center, keeping
     * the center fixed. Returns {@code [originX, originZ, scale, rotationDeg]}.
     */
    public static double[] applyScale(double mouseWx, double mouseWz, double originX, double originZ,
                                      double scale, double thetaDeg, int imgW, int imgH) {
        final Point2D.Double center = centerWorld(originX, originZ, scale, thetaDeg, imgW, imgH);
        final double halfDiagPixels = Math.hypot(imgW / 2.0, imgH / 2.0);
        final double dist = Math.hypot(mouseWx - center.x, mouseWz - center.y);
        double newScale = (halfDiagPixels > 1e-9) ? (dist / halfDiagPixels) : scale;
        if (newScale < MIN_SCALE) {
            newScale = MIN_SCALE;
        }
        final double[] s = originForFixedCenter(center.x, center.y, newScale, thetaDeg, imgW, imgH);
        return new double[] { s[0], s[1], newScale, thetaDeg };
    }

    /** Origin such that the image center lands on {@code (centerX, centerZ)} for the given scale/rotation. */
    private static double[] originForFixedCenter(double centerX, double centerZ, double scale,
                                                 double thetaDeg, int imgW, int imgH) {
        // center = origin + scale·R(θ)·(W/2, H/2)  =>  origin = center − scale·R(θ)·(W/2, H/2)
        final double rad = Math.toRadians(thetaDeg);
        final double cos = Math.cos(rad), sin = Math.sin(rad);
        final double cu = imgW / 2.0, cv = imgH / 2.0;
        final double offX = scale * (cos * cu - sin * cv);
        final double offZ = scale * (sin * cu + cos * cv);
        return new double[] { centerX - offX, centerZ - offZ };
    }

    private static boolean near(double wx, double wz, Point2D.Double p, double r2) {
        final double dx = wx - p.x, dz = wz - p.y;
        return (dx * dx + dz * dz) <= r2;
    }

    private static final double MIN_SCALE = 0.01;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl WPCore -am test -Dtest=TownPlanPlacementTest`
Expected: PASS (8 tests). If `pixelToWorld90Clockwise` or `rotateKeepsCenterFixed` fail, do NOT weaken them — the rotation sign must match `TownPlanStamper`; re-derive against `TownPlanStamper.computeFootprint` (its inverse uses `R(−θ)=[cos sin; −sin cos]`).

- [ ] **Step 5: Commit**

```bash
git add WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/townplan/TownPlanPlacement.java WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/townplan/TownPlanPlacementTest.java
git commit -m "feat(town-plan): pure placement geometry (move/rotate/scale, center-fixed)"
```
End with the `Co-Authored-By` trailer as above.

---

## Task 3: Rotated overlay rendering in `WorldPainter.drawOverlays()`

**Files:**
- Modify: `WPGUI/src/main/java/org/pepsoft/worldpainter/WorldPainter.java` (`drawOverlays()`, ~lines 1135–1184)

`drawOverlays()` draws each overlay with `g2.drawImage(overlayImage, offsetX, offsetY, width, height, null)` in world-transformed graphics. Wrap each overlay's draw in a rotation about its origin (offsetX, offsetY) using `overlay.getRotation()`, so the image rotates about its top-left — matching `TownPlanPlacement`/`TownPlanStamper`.

- [ ] **Step 1: Edit `drawOverlays()`**

In `WorldPainter.java`, inside the `for (Overlay overlay: dimension.getOverlays())` loop in `drawOverlays(Graphics2D g2)`, wrap the existing `g2.drawImage(...)` calls (both the 1:1 branch and the scaled branch) with a saved/rotated/restored transform. Replace the block that currently reads:

```java
                    final float overlayScale = overlay.getScale();
                    final int overlayOffsetX = overlay.getOffsetX(), overlayOffsetY = overlay.getOffsetY();
                    if ((overlayType == SCALE_ON_LOAD) || (overlayScale == 1.0f)) {
                        // 1:1 scale, or the image has already been scaled on loading
                        g2.drawImage(overlayImage, overlayOffsetX, overlayOffsetY, null);
                    } else {
                        final int width = Math.round(overlayImage.getWidth() * overlayScale);
                        final int height = Math.round(overlayImage.getHeight() * overlayScale);
                        final Object savedInterpolation = g2.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
                        try {
                            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                            g2.drawImage(overlayImage, overlayOffsetX, overlayOffsetY, width, height, null);
                        } finally {
                            if (savedInterpolation != null) {
                                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, savedInterpolation);
                            }
                        }
                    }
```

with (adds the rotation wrapper; everything else unchanged):

```java
                    final float overlayScale = overlay.getScale();
                    final int overlayOffsetX = overlay.getOffsetX(), overlayOffsetY = overlay.getOffsetY();
                    final float overlayRotation = overlay.getRotation();
                    final AffineTransform savedOverlayTransform = g2.getTransform();
                    if (overlayRotation != 0.0f) {
                        // Rotate clockwise about the image origin (top-left), matching TownPlanPlacement/TownPlanStamper
                        g2.rotate(Math.toRadians(overlayRotation), overlayOffsetX, overlayOffsetY);
                    }
                    try {
                        if ((overlayType == SCALE_ON_LOAD) || (overlayScale == 1.0f)) {
                            // 1:1 scale, or the image has already been scaled on loading
                            g2.drawImage(overlayImage, overlayOffsetX, overlayOffsetY, null);
                        } else {
                            final int width = Math.round(overlayImage.getWidth() * overlayScale);
                            final int height = Math.round(overlayImage.getHeight() * overlayScale);
                            final Object savedInterpolation = g2.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
                            try {
                                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                                g2.drawImage(overlayImage, overlayOffsetX, overlayOffsetY, width, height, null);
                            } finally {
                                if (savedInterpolation != null) {
                                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, savedInterpolation);
                                }
                            }
                        }
                    } finally {
                        g2.setTransform(savedOverlayTransform);
                    }
```

`AffineTransform` is already imported (line 28). Note `overlayScale` is in world blocks per image pixel here (the `g2` is world-transformed), which equals `TownPlanStamper`'s `blocksPerPixel`.

- [ ] **Step 2: Compile WPGUI**

Run: `mvn -DskipTests=true -pl WPGUI -am install`
Expected: BUILD SUCCESS (requires JIDE eval jars installed per BUILDING.md).

- [ ] **Step 3: Manual verification (run the app)**

Run: `mvn -pl WPGUI exec:exec`. Add an image overlay (existing overlay feature), set its `rotation` (temporarily via Task 4's tool once built, or programmatically). Confirm the overlay renders rotated about its top-left corner and stays aligned when you pan/zoom. (Until Task 4 exists, this step can be deferred to Task 4's run-check; mark it done after Task 4 if needed.)

- [ ] **Step 4: Commit**

```bash
git add WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/WorldPainter.java
git commit -m "feat(town-plan): rotate overlays about their origin in drawOverlays"
```
End with the `Co-Authored-By` trailer.

---

## Task 4: The Town Plan tool — `TownPlanOperation` + handle drawing + registration

**Files:**
- Create: `WPGUI/src/main/java/org/pepsoft/worldpainter/operations/TownPlanOperation.java`
- Modify: `WPGUI/src/main/java/org/pepsoft/worldpainter/WorldPainter.java` (placement state + handle drawing)
- Modify: `WPGUI/src/main/java/org/pepsoft/worldpainter/App.java` (`createToolPanel()` tool button)

This is GUI glue: build- and run-verified, not unit-tested (the geometry it relies on was unit-tested in Task 2). The operation extends `AbstractOperation` (NOT the brush `MouseOrTabletOperation`), installs its own mouse handlers on `activate()`, drives the active overlay's transform via `TownPlanPlacement`, tells the view to draw handles, and stamps via `TownPlanStamper`.

> **Implementer:** before writing, confirm three things by reading the code, and adapt the snippets to the real signatures if they differ (they are expected to match):
> 1. `WorldPainter.viewToWorld(Point)` returns world block coords, and `worldToView(int x, int y)` exists (both confirmed present).
> 2. How an `Overlay` is added to a dimension and its image loaded — search `Dimension.addOverlay`/`getOverlays` and `WorldPainter.loadOverlay` (the view lazy-loads `overlay.getImage()`); reuse that path so the selected image shows.
> 3. The `Operation` options panel is shown via `operation.getOptionsPanel()` (App.java ~6236). Returning a `JPanel` is sufficient.

- [ ] **Step 1: Add placement state + handle drawing to `WorldPainter`**

In `WorldPainter.java` add a field and setter so the operation can tell the view which overlay is being placed:

```java
    public void setPlacementOverlay(Overlay placementOverlay) {
        this.placementOverlay = placementOverlay;
        repaint();
    }

    private Overlay placementOverlay;
```

(Place the field near the other overlay-related fields, and the setter near the other public methods.)

Then, at the end of `drawOverlays(Graphics2D g2)` (after the overlay loop, before the method returns / before restoring composite), draw handles for the placement overlay. Add this helper method and call it from `drawOverlays` (call `drawPlacementHandles(g2)` just before the closing of the try in `drawOverlays`):

```java
    private void drawPlacementHandles(Graphics2D g2) {
        if ((placementOverlay == null) || (placementOverlay.getImage() == null)) {
            return;
        }
        final int imgW = placementOverlay.getImage().getWidth(), imgH = placementOverlay.getImage().getHeight();
        final double ox = placementOverlay.getOffsetX(), oz = placementOverlay.getOffsetY();
        final double scale = placementOverlay.getScale(), theta = placementOverlay.getRotation();
        final java.awt.geom.Point2D.Double[] corners = {
                org.pepsoft.worldpainter.townplan.TownPlanPlacement.pixelToWorld(0, 0, ox, oz, scale, theta),
                org.pepsoft.worldpainter.townplan.TownPlanPlacement.pixelToWorld(imgW, 0, ox, oz, scale, theta),
                org.pepsoft.worldpainter.townplan.TownPlanPlacement.pixelToWorld(imgW, imgH, ox, oz, scale, theta),
                org.pepsoft.worldpainter.townplan.TownPlanPlacement.pixelToWorld(0, imgH, ox, oz, scale, theta)
        };
        final java.awt.geom.Point2D.Double rotate =
                org.pepsoft.worldpainter.townplan.TownPlanPlacement.rotateHandleWorld(ox, oz, scale, theta, imgW, imgH);
        final java.awt.Stroke savedStroke = g2.getStroke();
        final java.awt.Color savedColor = g2.getColor();
        try {
            // g2 is world-transformed; use a thin world-space outline + small square handles.
            g2.setColor(java.awt.Color.YELLOW);
            for (int i = 0; i < 4; i++) {
                final java.awt.geom.Point2D.Double a = corners[i], b = corners[(i + 1) % 4];
                g2.drawLine((int) Math.round(a.x), (int) Math.round(a.y), (int) Math.round(b.x), (int) Math.round(b.y));
            }
            final int hs = Math.max(2, (int) Math.round(4 / Math.pow(2.0, getZoom()))); // ~4px handle in world units
            for (java.awt.geom.Point2D.Double c : corners) {
                g2.fillRect((int) Math.round(c.x) - hs, (int) Math.round(c.y) - hs, hs * 2, hs * 2);
            }
            g2.setColor(java.awt.Color.CYAN);
            g2.fillOval((int) Math.round(rotate.x) - hs, (int) Math.round(rotate.y) - hs, hs * 2, hs * 2);
        } finally {
            g2.setStroke(savedStroke);
            g2.setColor(savedColor);
        }
    }
```

(If `drawOverlays` does not already have access to `getZoom()`, it does — `getZoom()` is used elsewhere in the class. If the world-transformed integer rounding produces visibly chunky handles at high zoom, that is acceptable for B1.)

- [ ] **Step 2: Create `TownPlanOperation`**

Create `WPGUI/src/main/java/org/pepsoft/worldpainter/operations/TownPlanOperation.java`:

```java
package org.pepsoft.worldpainter.operations;

import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Overlay;
import org.pepsoft.worldpainter.WorldPainter;
import org.pepsoft.worldpainter.WorldPainterView;
import org.pepsoft.worldpainter.layers.TownLayout;
import org.pepsoft.worldpainter.townplan.TownPlanPlacement;
import org.pepsoft.worldpainter.townplan.TownPlanStamper;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.beans.PropertyVetoException;
import java.io.File;

/**
 * Palette tool for placing a town-plan image on the map (drag to move, corner handles to scale,
 * cyan knob to rotate) and stamping its footprint into the {@link TownLayout} layer.
 */
public class TownPlanOperation extends AbstractOperation {
    public TownPlanOperation(WorldPainter view) {
        super("Town Plan", "Place a town-plan image and stamp its footprint", "townplan");
        setView(view);
    }

    @Override
    public JPanel getOptionsPanel() {
        if (optionsPanel == null) {
            optionsPanel = buildOptionsPanel();
        }
        return optionsPanel;
    }

    @Override
    protected void activate() throws PropertyVetoException {
        final WorldPainter view = (WorldPainter) getView();
        view.addMouseListener(mouseHandler);
        view.addMouseMotionListener(mouseHandler);
        if (overlay != null) {
            view.setPlacementOverlay(overlay);
        }
    }

    @Override
    protected void deactivate() {
        final WorldPainter view = (WorldPainter) getView();
        view.removeMouseListener(mouseHandler);
        view.removeMouseMotionListener(mouseHandler);
        view.setPlacementOverlay(null);
    }

    private JPanel buildOptionsPanel() {
        final JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        final JButton selectButton = new JButton("Select image…");
        selectButton.addActionListener(e -> selectImage());

        thresholdSlider = new JSlider(0, 255, 128);
        invertCheckBox = new JCheckBox("Invert (light = footprint)");
        final JButton stampButton = new JButton("Stamp");
        stampButton.addActionListener(e -> stamp());

        panel.add(selectButton);
        panel.add(new JLabel("Brightness threshold"));
        panel.add(thresholdSlider);
        panel.add(invertCheckBox);
        panel.add(stampButton);
        return panel;
    }

    private void selectImage() {
        final WorldPainter view = (WorldPainter) getView();
        final Dimension dimension = getDimension();
        if (dimension == null) {
            return;
        }
        final JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(view) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        final File file = chooser.getSelectedFile();
        final BufferedImage image;
        try {
            image = ImageIO.read(file);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(view, "Could not read image:\n" + ex.getMessage(),
                    "Town Plan", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (image == null) {
            JOptionPane.showMessageDialog(view, "Not a supported image file.", "Town Plan", JOptionPane.ERROR_MESSAGE);
            return;
        }
        // Add as an overlay centered on the current view, semi-transparent, scale 1 block/pixel.
        overlay = new Overlay(file);
        overlay.setImage(image);
        overlay.setTransparency(0.5f);
        overlay.setScale(1.0f);
        final Point center = view.getViewCentreInWorldCoords();
        overlay.setOffsetX(center.x - image.getWidth() / 2);
        overlay.setOffsetY(center.y - image.getHeight() / 2);
        overlay.setEnabled(true);
        dimension.addOverlay(overlay);
        view.setPlacementOverlay(overlay);
        view.repaint();
    }

    private void stamp() {
        final WorldPainter view = (WorldPainter) getView();
        final Dimension dimension = getDimension();
        if ((overlay == null) || (overlay.getImage() == null) || (dimension == null)) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        final BufferedImage image = overlay.getImage();
        final double scale = overlay.getScale();
        final double rot = overlay.getRotation();
        final int ox = overlay.getOffsetX(), oz = overlay.getOffsetY();
        // World area = axis-aligned bounding box over the four transformed corners.
        final java.awt.geom.Point2D.Double[] corners = {
                TownPlanPlacement.pixelToWorld(0, 0, ox, oz, scale, rot),
                TownPlanPlacement.pixelToWorld(image.getWidth(), 0, ox, oz, scale, rot),
                TownPlanPlacement.pixelToWorld(image.getWidth(), image.getHeight(), ox, oz, scale, rot),
                TownPlanPlacement.pixelToWorld(0, image.getHeight(), ox, oz, scale, rot)
        };
        double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (java.awt.geom.Point2D.Double c : corners) {
            minX = Math.min(minX, c.x);
            minZ = Math.min(minZ, c.y);
            maxX = Math.max(maxX, c.x);
            maxZ = Math.max(maxZ, c.y);
        }
        final Rectangle area = new Rectangle((int) Math.floor(minX), (int) Math.floor(minZ),
                (int) Math.ceil(maxX - minX) + 1, (int) Math.ceil(maxZ - minZ) + 1);
        final int count = TownPlanStamper.stamp(dimension, image, ox, oz, scale, rot, null,
                thresholdSlider.getValue(), invertCheckBox.isSelected(), area);
        dimension.armSavePoint();
        view.repaint();
        JOptionPane.showMessageDialog(view, "Stamped " + count + " columns into the Town Layout layer.",
                "Town Plan", JOptionPane.INFORMATION_MESSAGE);
    }

    private final MouseAdapter mouseHandler = new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
            final WorldPainter view = (WorldPainter) getView();
            if ((overlay == null) || (overlay.getImage() == null)) {
                return;
            }
            final Point w = view.viewToWorld(e.getPoint());
            final int imgW = overlay.getImage().getWidth(), imgH = overlay.getImage().getHeight();
            final double handleR = 6.0 / Math.pow(2.0, view.getZoom()); // ~6px in world units
            activeHandle = TownPlanPlacement.hitTest(w.x, w.y, overlay.getOffsetX(), overlay.getOffsetY(),
                    overlay.getScale(), overlay.getRotation(), imgW, imgH, handleR);
            lastWorld = w;
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            final WorldPainter view = (WorldPainter) getView();
            if ((overlay == null) || (overlay.getImage() == null)
                    || (activeHandle == TownPlanPlacement.Handle.NONE) || (lastWorld == null)) {
                return;
            }
            final Point w = view.viewToWorld(e.getPoint());
            final int imgW = overlay.getImage().getWidth(), imgH = overlay.getImage().getHeight();
            switch (activeHandle) {
                case BODY: {
                    final double[] s = TownPlanPlacement.applyMove(overlay.getOffsetX(), overlay.getOffsetY(),
                            w.x - lastWorld.x, w.y - lastWorld.y);
                    overlay.setOffsetX((int) Math.round(s[0]));
                    overlay.setOffsetY((int) Math.round(s[1]));
                    break;
                }
                case ROTATE: {
                    final double[] s = TownPlanPlacement.applyRotate(w.x, w.y, overlay.getOffsetX(),
                            overlay.getOffsetY(), overlay.getScale(), overlay.getRotation(), imgW, imgH);
                    overlay.setOffsetX((int) Math.round(s[0]));
                    overlay.setOffsetY((int) Math.round(s[1]));
                    overlay.setRotation((float) s[3]);
                    break;
                }
                case NW: case NE: case SW: case SE: {
                    final double[] s = TownPlanPlacement.applyScale(w.x, w.y, overlay.getOffsetX(),
                            overlay.getOffsetY(), overlay.getScale(), overlay.getRotation(), imgW, imgH);
                    overlay.setOffsetX((int) Math.round(s[0]));
                    overlay.setOffsetY((int) Math.round(s[1]));
                    overlay.setScale((float) s[2]);
                    break;
                }
                default:
                    break;
            }
            lastWorld = w;
            view.repaint();
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            activeHandle = TownPlanPlacement.Handle.NONE;
        }
    };

    private JPanel optionsPanel;
    private JSlider thresholdSlider;
    private JCheckBox invertCheckBox;
    private Overlay overlay;
    private Point lastWorld;
    private TownPlanPlacement.Handle activeHandle = TownPlanPlacement.Handle.NONE;
}
```

> **Implementer notes for Step 2 (resolve against real signatures, keeping behavior identical):**
> - `view.getViewCentreInWorldCoords()` — confirm the exact method name on `WorldPainter`/`WorldPainterView` for "centre of the view in world coords" (search `getViewCentre`, `viewToWorld`, or compute via `viewToWorld(new Point(getWidth()/2, getHeight()/2))`). If absent, replace with `view.viewToWorld(new Point(view.getWidth() / 2, view.getHeight() / 2))`.
> - `dimension.addOverlay(overlay)` — confirm the add-overlay method name (search `addOverlay` on `Dimension`). If overlays are added differently, use that path; the goal is the overlay shows on the map.
> - `view.getZoom()` is package-or-public on `WorldPainter` (used internally); if not accessible from this package, add a public `getZoom()` accessor or compute scale from `view` another way.
> - The icon: `AbstractOperation` loads `org/pepsoft/worldpainter/icons/townplan.png`. Copy an existing 16×16 icon from `WPGUI/src/main/resources/org/pepsoft/worldpainter/icons/` to `townplan.png` (e.g. duplicate `spawn.png`) so the tool button shows an image. Verify the button renders.

- [ ] **Step 3: Register the tool button in `App.createToolPanel()`**

In `App.java`, method `createToolPanel()` (~line 3453), the tools are added as `toolPanel.add(createButtonForOperation(new Xxx(view), 'k'));`. Add the Town Plan tool next to `SetSpawnPoint` (after line 3525 `toolPanel.add(createButtonForOperation(new SetSpawnPoint(view)));`):

```java
        toolPanel.add(createButtonForOperation(new org.pepsoft.worldpainter.operations.TownPlanOperation(view), 't'));
```

(`view` here is the `WorldPainter` field used by the other operations. If `createButtonForOperation`'s signature differs, match the existing calls exactly — they take `(Operation, char)` or `(Operation)`.)

- [ ] **Step 4: Compile WPGUI**

Run: `mvn -DskipTests=true -pl WPGUI -am install`
Expected: BUILD SUCCESS. Fix any signature mismatches surfaced here using the implementer notes above (real method names for view-centre / addOverlay / getZoom).

- [ ] **Step 5: Manual verification (run the app)**

Run: `mvn -pl WPGUI exec:exec`. Then:
1. Open or create a world; click the **Town Plan** tool in the Tools palette (its panel appears in Tool Settings).
2. **Select image…** → pick a town-plan PNG → it appears centered, semi-transparent, with yellow corner handles + a cyan rotate knob.
3. Drag the body to move; drag a corner to scale (image stays centered); drag the cyan knob to rotate — verify the image and handles track the mouse and stay aligned on pan/zoom.
4. Set the **threshold**, click **Stamp** → a magenta footprint appears (the `TownLayout` layer). Confirm `Town Layout` is listed in the Layers panel with Show/Solo, and toggling Show hides/shows the magenta.
5. Confirm the rotated image (Task 3) matches where the footprint lands (drag-what-you-stamp consistency).

- [ ] **Step 6: Commit**

```bash
git add WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/operations/TownPlanOperation.java WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/WorldPainter.java WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/App.java WorldPainter/WPGUI/src/main/resources/org/pepsoft/worldpainter/icons/townplan.png
git commit -m "feat(town-plan): Town Plan palette tool — drag/rotate/scale handles + Stamp"
```
End with the `Co-Authored-By` trailer.

---

## Self-Review

- **Spec coverage:** §2.1 visible layer → Task 1 (renderer + DefaultPlugin). §2.2 renderer/magenta → Task 1. §2.3 rotated overlay rendering → Task 3. §2.4 on-canvas move/rotate/scale handles → Task 2 (geometry) + Task 4 (operation + view handles). §2.4 options panel (Select image / threshold / invert / Stamp) → Task 4. Stamp via `TownPlanStamper` → Task 4 `stamp()`. Deferrals (§3: live preview, crop, export UI) — not implemented, correctly. Entry = palette tool button → Task 4 Step 3.
- **Placeholder scan:** No TBD/TODO. The "Implementer notes" call out exact real signatures to confirm (view-centre, addOverlay, getZoom, createButtonForOperation, icon) — these are grounded confirmations against named methods, not vague guidance, and are unavoidable for GUI glue that compiles only with the JIDE-dependent WPGUI module.
- **Type consistency:** `TownPlanPlacement.pixelToWorld/worldToPixel/centerWorld/rotateHandleWorld/hitTest/applyMove/applyRotate/applyScale` and `Handle{BODY,NW,NE,SW,SE,ROTATE,NONE}` are used identically in Task 2's tests, Task 4's operation, and the view's `drawPlacementHandles`. The placement state ordering `[originX, originZ, scale, rotationDeg]` is consistent across `applyRotate`/`applyScale`; `applyMove` returns `[originX, originZ]` (documented). `TownPlanStamper.stamp(dimension, image, originX, originZ, blocksPerPixel, rotationDeg, cropPx, threshold, invert, worldArea)` matches the Plan A signature (Overlay `scale` == `blocksPerPixel`, `offsetX/offsetY` == `originX/originZ`, `rotation` == `rotationDeg`).
- **Testability:** Tasks 1–2 are pure WPCore units with full TDD. Tasks 3–4 are GUI glue, build- and run-verified with explicit manual checks, because Swing/JIDE rendering and mouse interaction cannot be meaningfully unit-tested in this codebase.
