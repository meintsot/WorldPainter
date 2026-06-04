# Town Plan Layout Stamper — Design (v1)

**Date:** 2026-06-04
**Status:** Approved for planning
**Component:** TalePainter (WorldPainter Hytale edition) — WPCore + WPGUI

## 1. Purpose

Let world builders import a 2D "town plan" image (e.g. a fantasy city map), place it
interactively on the map (move / rotate / scale directly on the canvas), and then **stamp**
its footprint into a non-destructive layer that becomes real blocks on export. The goal is to
move the laborious "lay out the town footprint" work out of the game and into TalePainter,
where it is fast and visual.

This v1 produces the **X-Z layout only** (a silhouette of the built-up area). Block heights
(Y) and per-building detail remain the builder's job in-game. Richer features (feature-type
mapping, prefab placement) are explicitly deferred — see §9.

## 2. Scope (v1)

In scope:

1. Import a town-plan image (PNG/JPG) as a placeable overlay.
2. Position it with **full on-canvas handles**: drag body to move, corner handles to scale,
   edge/rotate handle to rotate. WYSIWYG, no numeric dialog required (numeric fields optional/secondary).
3. A **brightness threshold + invert** control with an optional **crop rectangle** to exclude
   labels / legend / compass.
4. A **live red preview** on the map showing exactly which columns will be stamped, updated as
   the threshold/placement changes — before committing.
5. **Set** bakes the footprint into a dedicated, re-editable **Town Layout** layer.
6. The layer exports as a chosen block (default: a distinct dark block) at each footprint column,
   draped on the terrain surface.

## 3. Non-goals (explicitly deferred)

- ❌ **Feature-type / category mapping** (walls vs roads vs buildings vs water → different blocks).
  v1 is a single silhouette only.
- ❌ **Prefab / Bo2 / schematic placement** into grid cells or building footprints.
- ❌ **Automatic terrain height shaping** from the plan. v1 never alters the height map.
- ❌ **Scale-bar calibration** (reading "200 m" off the plan). Scale is set by eye via the handles.

These are the natural Phase 2/3 and the v1 layer + placement model is designed to be their foundation.

## 4. User flow

1. **Tools → Town Plan…** opens the placement mode. *(Tools menu — NOT Cloud; this feature is
   entirely local with no backend.)*
2. User picks an image file. It appears as a semi-transparent overlay centered on the current view.
3. User positions it with on-canvas handles (move / scale / rotate).
4. A **Town Plan** side panel exposes: brightness threshold slider, invert toggle, optional crop
   rectangle, target layer (default the built-in Town Layout layer), export block, marker height,
   and a **Set** button.
5. While adjusting, a translucent **red preview** shows the resulting footprint columns live.
6. **Set** writes the footprint into the Town Layout layer (undoable). The user may then:
   - touch up / erase with the normal layer brush,
   - move the image and **Set** again (additive),
   - toggle the layer's Show / Solo / export like any other layer.
7. On **export**, the Town Layout layer places the chosen block at each footprint column.

## 5. Architecture & components

Grounded in existing classes; we extend/mirror rather than invent.

### 5.1 Placement model — extend `Overlay`
`WPCore/.../Overlay.java` already stores `file, scale, transparency, offsetX, offsetY, enabled`
and fires property changes, and multiple overlays are already managed via
`WPGUI/.../OverlaysTableModel.java`. We extend `Overlay` with:

- `rotation` (degrees, float)
- `cropRect` (nullable rectangle in image-pixel space)
- `threshold` (0–255), `invert` (boolean)

Placement persists with the world (like existing overlays), so a plan can be re-stamped later.
Multiple plans coexist for free.

### 5.2 On-canvas handles — map view + glass pane
The map view component `WPGUI/.../WorldPainter.java` already paints overlays; the interactive
handles (hit-testing, drag/rotate/scale gestures, live transform) are added there and/or on
`WPGUI/.../GlassPane.java`. This is the bulk of the new UI code. The applied transform is a
standard affine: translate → rotate → scale, in block coordinates
(`Constants.TILE_SIZE_BITS` converts tile↔block, as `ConfigureOverlayDialog` already does).

### 5.3 Town Layout layer + exporter — mirror `Annotations`
- **`TownLayout` layer** (`WPCore/.../layers/`) — a built-in singleton bit layer modeled on
  `layers/Annotations.java` (`DataSize.BIT`, single on/off value). Gets Show/Solo/export and
  brush editing for free via the existing layer framework.
- **`TownLayoutExporter`** (`WPCore/.../layers/exporters/`) — mirrors
  `exporters/AnnotationsExporter.java`: an `AbstractLayerExporter` implementing
  `SecondPassLayerExporter` at `Stage.ADD_FEATURES`. Walks set columns; for each, places the
  configured block at the surface (`dimension.getIntHeightAt(x, y)`), repeated up `markerHeight`
  blocks, only into insubstantial space (same guard as `AnnotationsExporter`).
- **`TownLayoutSettings`** — exporter settings (like `AnnotationsSettings`): `export` flag,
  `block`, `markerHeight`.

### 5.4 Block selection (platform-aware)
- Minecraft export: an `org.pepsoft.minecraft.Material` (default a distinct dark block).
- Hytale export: a block resolved from `HytaleBlockRegistry` (default a distinct dark block).

## 6. Conversion pipeline (image → footprint)

On **Set** (and continuously for the live preview), for each world column `(x, z)` inside the
placed image's transformed bounding box:

1. Inverse-transform `(x, z)` back into image-pixel space (undo scale, rotation, translation),
   so rotation/scale are honored exactly.
2. If the pixel is outside the image or outside the `cropRect`, skip.
3. Sample the pixel's brightness. Mark the column if `brightness < threshold`
   (or `> threshold` when `invert` is set).
4. **Set:** set the Town Layout layer value at `(x, z)`. **Preview:** paint the column red.

The computation is resolution-independent (driven by world columns, not image pixels), so it
works at any scale. The live preview runs the same predicate into a translucent overlay.

Alpha handling: if the source image has an alpha channel, fully-transparent pixels are treated
as "empty" regardless of brightness (lets a pre-masked PNG work cleanly without extra UI).

## 7. Y handling (kept deliberately minimal)

v1 is X-Z only. The marker is **surface-following**: placed at the terrain surface at each
footprint column (drapes over hills, exactly like `AnnotationsExporter`).

- **Export block:** default a distinct dark block (e.g. black concrete / obsidian), configurable.
- **Marker height:** default a short pillar (~3 blocks) for in-world visibility and easy
  identification/removal; `1` = flush single block.

These markers are temporary guides intended to be replaced by builders. Flat-at-sea-level was
considered and rejected as the default (breaks on uneven terrain).

## 8. Persistence, undo, multiple plans

- **Persistence:** the placement (file reference + transform + threshold/crop) is saved with the
  world like existing overlays; the stamped result lives in the Town Layout layer and saves with
  the dimension.
- **Undo:** stamping mutates layer data through the standard layer path, so it participates in
  WorldPainter's existing undo.
- **Multiple plans:** multiple overlays already supported; all stamp into the single Town Layout
  layer in v1. (Multiple named layout layers with per-layer blocks is a future enhancement.)

## 9. Future phases (context only, not built here)

- **Phase 2 — category mapping:** map brightness bands / colors / regions to multiple blocks or
  layers (walls, roads, buildings, water), per the legends in real town-plan art.
- **Phase 3 — prefab grid:** drop Bo2/schematic/custom objects into grid cells or building
  footprints. The existing **Prefabs** panel in the UI is the natural home.

## 10. Testing

- **Conversion core (unit):** the transform + threshold + crop → column-set logic is pure and
  testable with small synthetic images; no GUI. Fits the existing JUnit 4 setup.
- **Exporter (unit/integration):** mirror existing Hytale/annotation export tests — stamp a known
  footprint, export, assert the chosen block lands at the correct X-Z and surface Y, and respects
  `markerHeight` and the insubstantial-space guard.
- **Placement transform:** unit-test the affine round-trip (world↔pixel) for representative
  rotations/scales so the preview matches the stamp.

## 11. Open questions

None blocking. Defaults chosen: surface-following short pillar (~3 blocks), distinct dark export
block, single shared Town Layout layer. Revisit multi-layer-per-town if builders ask for
per-district export blocks before Phase 2.
