# Town Tool — Surface-Flush Placement + Erase Brush — Design

**Date:** 2026-06-06
**Status:** Approved for planning
**Component:** TalePainter (WorldPainter Hytale edition) — WPCore + WPGUI
**Builds on:** `2026-06-04-town-plan-layout-stamper-design.md`

## 1. Purpose

Two refinements to the existing Town Plan tool, requested after using v1:

1. **In surface, not extra blocks.** The stamped footprint should mark the terrain *surface*
   itself, flush with the ground — not float a pillar of marker blocks *above* the surface.
2. **Erase some areas after stamp.** Provide a way, from within the Town Plan tool, to remove
   parts of the stamped footprint with a radius brush (freeform touch-up), rather than only being
   able to add.

Both are small, surgical changes to the v1 model. The footprint remains a re-editable
`TownLayout` bit layer; only the export rendering (item 1) and the tool's interaction (item 2)
change.

## 2. Item 1 — Surface-flush placement

### 2.1 Current behaviour

`TownLayoutExporter.placeMarkerColumn` places a pillar of the chosen block at
`z = terrainHeight + 1 … terrainHeight + markerHeight` (default 3 tall), filling only
insubstantial (air) space and stopping at the first solid block. The footprint therefore shows
up as 3-block-tall markers stacked **on top of** the ground.

### 2.2 New behaviour

Replace `placeMarkerColumn` with `placeSurfaceColumn(world, x, y, terrainHeight, depth, block)`,
which **overwrites the terrain block at `z = terrainHeight`** (the actual surface block) with the
chosen block — flush with the ground, no blocks above the surface.

- The replacement is **unconditional** for the surface column (it is replacing ground, not
  filling air), so the old "stop at first solid block / insubstantial-only" guard is removed.
- `depth` controls how many blocks **downward** from the surface are replaced
  (`z = terrainHeight, terrainHeight − 1, …`), clamped at the world floor. This lets a builder
  optionally make a thicker band without ever adding blocks above the surface. Default `depth = 1`
  (just the top surface block), matching the request.

### 2.3 Settings rename

`TownLayoutSettings.markerHeight` is repurposed and renamed to **`surfaceDepth`**
(getter/setter `getSurfaceDepth`/`setSurfaceDepth`, default `1`). The name "markerHeight" would be
misleading once it means downward replacement depth. This feature is new and unreleased, so the
serialized-field rename carries no real backward-compatibility cost.

`export` and `block` are unchanged.

### 2.4 Touched files

- `WPCore/.../layers/exporters/TownLayoutExporter.java` — `placeSurfaceColumn`, `addFeatures` call.
- `WPCore/.../layers/exporters/TownLayoutSettings.java` — `surfaceDepth` field + accessors,
  equals/hashCode.
- `WPCore/.../layers/TownLayout.java` — class doc ("replaces the surface block …").
- Tests: `TownLayoutExporterTest`, `TownLayoutSettingsTest`, `TownPlanStampIntegrationTest`
  updated to assert flush-surface replacement and downward depth.

## 3. Item 2 — Erase brush

### 3.1 Core (WPCore) — pure geometry + thin adapter

Mirror the existing `computeFootprint` / `stamp` split (pure logic in WPCore, thin GUI adapter):

- `TownPlanStamper.computeDisc(int centerX, int centerZ, double radius) → Set<Point>` — pure;
  returns the world columns whose centre lies within `radius` of `(centerX, centerZ)`
  (Euclidean, inclusive). Unit-tested.
- `TownPlanStamper.erase(Dimension dimension, int centerX, int centerZ, double radius) → int` —
  thin adapter; sets each disc column's `TownLayout` value to **false** and returns the count.

No new layer or exporter: cleared columns simply stop exporting.

### 3.2 GUI (WPGUI) — tool interaction

In `TownPlanOperation`:

- Add an **"Erase footprint"** `JToggleButton` and an **erase-radius** `JSlider` (1–64, default 8)
  to the options panel.
- When the toggle is **on**, the mouse handler erases instead of placing:
  - `mousePressed` / `mouseDragged` call `TownPlanStamper.erase(dimension, world.x, world.y,
    radius)` at the cursor and `view.repaint()` so the footprint visibly disappears under the
    brush. Placement hit-testing/handles are skipped while erasing, so the two modes never fight.
  - `mouseReleased` calls `dimension.armSavePoint()` once, so a whole erase stroke is a single
    undo step (matching how `stamp()` arms a save point after writing).
- When the toggle is **off**, the mouse behaves exactly as today (move / scale / rotate the
  placement image).

### 3.3 Touched files

- `WPCore/.../townplan/TownPlanStamper.java` — `computeDisc`, `erase`.
- `WPGUI/.../operations/TownPlanOperation.java` — toggle + slider + erase branch in mouse handler.
- Tests: `TownPlanStamperTest` gains `computeDisc` cases.

## 4. Interaction between the two items

Independent. Item 1 changes only what export writes; item 2 changes only the layer's set/clear
columns in the editor. Erasing a column removes it from the layer, so it is neither rendered in
the editor nor written on export — no special-casing needed.

## 5. Testing

- **Exporter (unit):** surface block at `terrainHeight` is replaced with the chosen block;
  `depth > 1` replaces downward and clamps at the world floor; `depth = 1` leaves
  `terrainHeight − 1` untouched. The old "pillar above surface" and "stop at solid" tests are
  replaced.
- **Integration:** stamp a known column, export, assert the block lands **at** `terrainHeight`
  (not `terrainHeight + 1`) and that `terrainHeight + 1` is air.
- **`computeDisc` (unit):** radius 0 → just the centre; a small radius selects the expected
  in-circle columns and excludes corners; centre offset honoured.
- **Settings (unit):** `surfaceDepth` participates in equals/hashCode; default is 1.

GUI mouse wiring is verified manually (run the app, stamp, toggle Erase, drag) since the pure disc
geometry it relies on is covered by `computeDisc` tests.

## 6. Non-goals

- No custom brush cursor / radius ring overlay in v1 (possible polish later).
- No reuse of WorldPainter's global brush/radius system; the tool keeps its own self-contained
  radius slider, consistent with its existing self-contained threshold slider.
- No change to the placement/threshold/rotation pipeline.
