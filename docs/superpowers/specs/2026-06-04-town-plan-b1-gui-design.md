# Town Plan GUI — B1 Design (visible layer + placement tool)

**Date:** 2026-06-04
**Status:** Approved for planning
**Component:** TalePainter (WorldPainter Hytale edition) — WPCore + WPGUI
**Builds on:** Plan A backend foundation (`Overlay` fields, `TownLayout` layer, `TownLayoutExporter`, `TownPlanStamper`) — see `2026-06-04-town-plan-stamper-backend.md`.

## 1. Purpose

Plan A delivered the headless engine. B1 makes it **usable and visible**: load a town-plan image, drag / rotate / scale it directly on the map, hit **Stamp**, and watch the footprint appear as a real, toggleable `TownLayout` layer. This is the first of the incremental GUI plans (B1 → B2 → B3).

## 2. Scope (B1)

In scope:
1. Make `TownLayout` a **visible, built-in layer** in the Layers panel (Show/Solo, brush-paintable for touch-ups, exports) — like `Frost`.
2. A **`TownLayoutRenderer`** so the footprint draws on the map (distinct magenta; map colour is editor-only and independent of the exported block).
3. **Rotated overlay rendering** — the placed image rotates on the map per `Overlay.rotation`.
4. A **Town Plan tool** (palette tool button) providing **on-canvas move / rotate / scale handles** plus an options panel with **Select image…**, a **brightness threshold** slider, an **invert** toggle, and a **Stamp** button that calls `TownPlanStamper.stamp(...)`.

## 3. Non-goals (deferred to later increments)

- ❌ **Live red footprint preview** (continuous `computeFootprint` overlay) → **B2**.
- ❌ **Crop rectangle** UI → **B2**.
- ❌ **Export settings UI** — block picker, marker-height field, export on/off toggle → **B3** (export works in B1 via the Plan A defaults: black block, ~3-block pillar, export = true).
- ❌ Scale-bar calibration, prefab placement, category mapping (later phases of the overall feature).

## 4. User flow

1. Click the **Town Plan** tool in the Tools palette → the tool activates; its options appear in the Tool Settings dock.
2. **Select image…** → the chosen plan is added as a semi-transparent overlay, centered on the view.
3. **Position it on the map** with handles: drag the body to move, corner handles to scale, the rotate handle to spin. WYSIWYG.
4. Adjust the **threshold** slider / **invert** toggle to choose which pixels count (dark→block by default).
5. **Stamp** → the footprint is written into the `TownLayout` layer and immediately renders (magenta). Touch up with the brush, re-position and Stamp again (additive), or toggle the layer's Show/Solo.
6. On **export**, the layer places the marker blocks (Plan A behavior) on both Minecraft and Hytale.

## 5. Architecture & components

### 5.1 Layer visibility — `DefaultPlugin` + renderer (WPCore)
- Register `TownLayout.INSTANCE` in `DefaultPlugin.getLayers()` (the `Arrays.asList(Frost.INSTANCE, …)` list). It then flows through `LayerManager` → App's layer list → the Layers panel, with Show/Solo and brush paintability, like the other built-in bit layers.
- Add `TownLayoutRenderer implements BitLayerRenderer` (mirrors `FrostRenderer`): returns a distinct magenta where `value` is true, else the underlying colour. Auto-discovered by the `renderers.<Layer>Renderer` naming convention.

### 5.2 Rotated overlay rendering — `WorldPainter.drawOverlays()` (WPGUI)
Extend the existing overlay paint loop (`WorldPainter.drawOverlays()`) to apply an `AffineTransform` rotation about the image origin (top-left) using `overlay.getRotation()`, matching the clockwise-about-origin convention documented in `TownPlanStamper` so the on-screen image and the stamped footprint align exactly.

### 5.3 Placement tool — `TownPlanOperation` (WPGUI)
A custom `Operation` (extending `AbstractOperation`, NOT the brush-style `MouseOrTabletOperation`), registered as a Tools-palette tool button.
- On `activate()`: installs its own `MouseListener` + `MouseMotionListener` on the view; on `deactivate()`: removes them. (This is the same lifecycle the brush operations use, minus the radius/tick model.)
- **Interaction:** mouse press hit-tests the active overlay's body and its handles; drag updates the overlay's `offsetX/offsetY` (move), `scale` (corner handles), or `rotation` (rotate handle), converting screen positions via `viewToWorld()` and the current zoom; release ends the gesture. Each update repaints the view.
- **Pure geometry:** handle hit-testing and the drag→transform math live in a pure, unit-testable helper (e.g. `TownPlanPlacement`), separate from the Swing event plumbing — mirroring how Plan A kept `computeFootprint` pure.
- **Handle rendering:** the **view** draws the rotated overlay and its handles (it already owns overlay drawing). The operation sets the "overlay being placed" + active-handle state the view reads while painting. The handle glyphs (corner squares, rotate knob) are drawn in screen space on top of the rotated image's bounding box.
- **Options panel:** the operation's options panel (shown in the Tool Settings dock) hosts **Select image…**, the **threshold** slider, the **invert** toggle, and **Stamp**. Stamp computes the world area from the overlay's transformed bounds and calls `TownPlanStamper.stamp(dimension, image, originX, originZ, blocksPerPixel, rotationDeg, null /*crop*/, threshold, invert, worldArea)`, then refreshes the view.

### 5.4 Integration points to finalize in the plan
These are known hooks; exact signatures get pinned during plan-writing by reading the code:
- How Tools-palette operations are registered/added (the operations list + tool button wiring in `App.java`).
- The `Operation` options-panel mechanism (how the Tool Settings dock shows the active operation's panel).
- The exact view hook for drawing the placement handles (extend `paintComponent`/`drawOverlays`, reading placement state set by the operation).

## 6. Testing

- **`TownLayoutRenderer.getPixelColour`** — pure unit test (set → magenta, unset → underlying colour).
- **Placement geometry** (`TownPlanPlacement`) — pure unit tests: handle hit-testing at representative positions/rotations; drag deltas → correct `offset`/`scale`/`rotation`; the overlay→world bounds passed to `stamp` round-trip with `TownPlanStamper`'s transform convention.
- **Rotated rendering** — unit-test the rotated bounding-box computation used for both painting and the stamp area.
- **Swing wiring** (tool button, options panel, paint, Stamp) — verified by building and running the app; the draggable plan and the appearing magenta footprint are the acceptance check. Note: WPGUI requires the JIDE eval jars to build/run.

## 7. Open questions

None blocking. Confirmed decisions: entry = Tools-palette tool button; map footprint colour = magenta (editor-only); real drag/rotate/scale handles are in this increment (not deferred); live preview / crop / export-UI deferred to B2/B3.
