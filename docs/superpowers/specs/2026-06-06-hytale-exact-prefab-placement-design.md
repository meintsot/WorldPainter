# Feature: Exact Prefab Placement (Hytale)

- **Status:** Design approved, ready for implementation planning
- **Date:** 2026-06-06
- **Branch / worktree:** `worktree-hytale-prefab-exact-placement` (`.claude/worktrees/hytale-prefab-exact-placement`)
- **Platform scope:** Hytale only
- **Tracking issue:** none assigned yet (TP-XX TBD — offer to create on YouTrack, set to "Review" when resolved per project workflow)

## Summary

Add a Hytale-only tool that lets a user **drag a prefab from a thumbnail palette onto a precise map location**, creating a **persistent, editable placement** with exact position, height, and **free (continuous) rotation**. Placements are stored in the world, drawn as on-map handles synced to a master list, and realized at export as **baked blocks** (with a server-marker fallback).

This is fundamentally different from the existing Hytale prefab *layers* (`HytalePrefabLayer`, `HytaleSpecificPrefabLayer`), which scatter prefabs with density/grid/frequency/randomness. This feature is deterministic single-instance placement — "put *this* prefab at *exactly* here."

## Goals

- Drag-and-drop placement of a specific prefab at a specific `(x, y)` map location.
- Persistent, editable placements: select, move, rotate, delete; survive save/reload; re-export identically.
- Free continuous rotation (0–360°), with cardinal angles lossless and off-cardinal angles resampled.
- A thumbnail palette for browsing prefabs (built-in registry + discovered `*.prefab.json`).
- On-map handles + a synced master "Placements" panel with exact numeric fields.
- Realize placements at export as baked blocks, with a marker fallback when a prefab file can't be resolved.

## Non-goals (YAGNI)

- Non-Hytale (Minecraft) platform support.
- Scale and mirroring of placements (rotation only for v1).
- Isometric thumbnails (top-down only for v1; iso is a later upgrade).
- Editing individual blocks of a placed prefab.
- Server-side-yaw realization route (we bake blocks in WorldPainter; markers are best-effort fallback only).

## Key decisions (from brainstorming)

| Decision | Choice |
|---|---|
| Placement persistence | Persistent & editable — stored in the world model, realized at export |
| Authoring interaction | Drag-and-drop from a palette onto the map, then edit exact coords |
| Palette content | Rendered thumbnails (new `HytalePrefabThumbnailRenderer`) |
| On-map management | On-map handles (footprint, drag-move, rotate handle) + synced master list panel |
| Export realization | Inline block paste + `PrefabMarker` fallback |
| Rotation | Free continuous (0–360°); cardinals exact, off-cardinal resampled |

## Background — current state (anchors)

- **Prefab block paster (export):** `WPCore/.../hytale/prefab/HytalePrefabPaster.java` — `paste(chunksByCoords, anchorWorldX, anchorY, anchorWorldZ, blockOffsetX, blockOffsetZ, prefabPath)` bakes a prefab's blocks/fluids inline into `HytaleChunk` data at an exact anchor. Parses `*.prefab.json` into a sparse `PrefabBlockData` (`PrefabBlock{x,y,z,blockName,rotation}` + `PrefabFluid`). Sets `sealProtected` on placed blocks. **No rotation parameter today.**
- **Export enqueue path:** `WPCore/.../hytale/export/HytaleWorldExporter.java` — `PendingPrefabPaste{localX, anchorY, localZ, worldX, worldZ, prefabPath, prefabName}` collected per region (`enqueuePrefabLayerPaste`, `enqueueSpecificPrefabPastes`), executed after region chunks are populated; on paste failure falls back to `chunk.addPrefabMarker(x, y, z, category, path)`. Centering offsets `blockOffsetX/blockOffsetZ` come from `centeringOffset()`.
- **Marker (server-side fallback):** `HytaleChunk.PrefabMarker{x, y, z, category, prefabPath}` — **no rotation field**; (de)serialized in `HytaleBsonChunkSerializer` / `HytaleBsonChunkDeserializer` under `"PrefabMarkers"`.
- **Prefab catalog:** built-in `HytalePrefabLayer.BUILT_IN_PREFABS` (~84 across ~13 categories; `getCategories()`, `getPrefabsInCategory()`); user files via `HytalePrefabDiscovery.discoverPrefabs(baseDir)` → `PrefabFileEntry{displayName, category, subCategory, relativePath, frequency, matchesSearch()}`.
- **Prefab as WPObject:** `HytalePrefabJsonObject` loads a prefab into a `WPObject` storing each block's Hytale rotation as the `hytale_rotation` material property (0–63).
- **Rotation encoding (documented in `2026-04-07-hytale-rotation-mirror-fix-design.md`):** `rotation = rx*16 + ry*4 + rz` (0–63); `rx`=pitch(X), `ry`=yaw(Y/vertical), `rz`=roll(Z); yaw 0=N,1=E,2=S,3=W. `Material.rotate(steps, platform)` already transforms yaw via `new_ry = (old_ry + steps) % 4`; `RotatedObject` already rotates a WPObject's positions + facings for **cardinal** steps. → **Per-block facing is discrete to 4 yaw orientations.**
- **Coordinate conventions:** WorldPainter uses `x` (E–W), `y` (N–S) horizontal, `z` vertical. The Hytale exporter's `PendingPrefabPaste`/paster name the second horizontal axis `worldZ` (= WP world `y`) and the vertical `anchorY`. **This spec stores placements in WP terms (`x`, `y` horizontal; `height` vertical)** and maps at export: `anchorWorldX = x`, `anchorWorldZ = y`, `anchorY = height`.
- **Map view / overlays:** canvas is `WPGUI/.../WorldPainter.java` (extends `WorldPainterView` → `TiledImageViewer`); `drawOverlays()` paints `dimension.getOverlays()` with offset/rotation/scale at paint time (overlays are **ephemeral**, not persisted). Pixel↔world conversion available (used by `TownPlanPlacement.pixelToWorld`).
- **Operation pattern:** `Operation`/`AbstractOperation`/`MouseOrTabletOperation`; tools added in `App.java` via `createButtonForOperation(...)`; `TownPlanOperation` is the closest precedent for a place-at-exact-location-with-rotation tool.
- **DnD infra:** `WPTransferHandler` (global file drops on the frame), `DnDToggleButton` (a `DragSource`/`DragGestureListener` example). **No `DropTarget` on the canvas today — must be added.**
- **Persistence idiom:** `Dimension` is `Serializable` with `CURRENT_WP_VERSION` (currently 13) and a `readObject` migration ladder; add a new field + bump version + initialize it for old worlds in `readObject`.

## Architecture overview

```
WPCore (model + export)                       WPGUI (UI)
─────────────────────────                     ─────────────────────────
HytalePrefabPlacement (Serializable)          HytalePrefabPalette (dockable, DragSource)
Dimension.hytalePrefabPlacements: List<>      PrefabPlacementOperation (tool + canvas DropTarget)
PrefabRotator (pure transform fn)             HytalePrefabPlacementsPanel (master list, synced)
HytalePrefabPaster (+rotation overload)       HytalePrefabThumbnailRenderer (+ cache)
PrefabMarker (+optional rotation)             map overlay painting (placement footprints/handles)
HytaleWorldExporter (enqueue placements)
```

Data flow: **palette drag → canvas drop → `Dimension.addPrefabPlacement(...)` → overlay repaint + list refresh**. At save, the list serializes with the dimension. At export, `HytaleWorldExporter` enqueues placements intersecting each region and calls the rotation-aware paster (marker fallback on failure).

## Data model (WPCore)

`HytalePrefabPlacement` — `Serializable`, fixed `serialVersionUID`:

- `long id` — stable identity for selection/sync (assign from an incrementing counter or `UUID`-derived long).
- `String prefabPath` — relative path (e.g. `Prefabs/Trees/Oak/.../Oak_Stage5_003.prefab.json`). **Stored by path, not registry ordinal**, to avoid the TP-57-style ordinal remap hazard.
- `String prefabName` — display label / marker category.
- `int x, y` — WorldPainter horizontal block coords.
- `Integer height` — explicit vertical (block Y at export); `null` allowed only when `snapToSurface`.
- `boolean snapToSurface` — if true, height resolved at export from terrain height (+1) at `(x, y)`.
- `double rotationDegrees` — 0–360, yaw about the vertical axis.

Provide value semantics (`equals`/`hashCode` by `id`), and small `withX(...)` copy helpers for edits.

`Dimension` changes:

- `private List<HytalePrefabPlacement> hytalePrefabPlacements = new ArrayList<>();`
- Accessors: `getHytalePrefabPlacements()` (unmodifiable view), `addHytalePrefabPlacement(p)`, `removeHytalePrefabPlacement(p)`, `replaceHytalePrefabPlacement(old, new)` / `updateHytalePrefabPlacement(id, mutator)` — each marks the dimension dirty and fires the existing dimension change/listener notification used for repaint.
- `CURRENT_WP_VERSION` 13 → 14. In `readObject`, after `defaultReadObject()`: `if (wpVersion < 14 && hytalePrefabPlacements == null) hytalePrefabPlacements = new ArrayList<>();`. No `writeObject` change (defaultWriteObject persists it).

## Persistence

- Old worlds (`wpVersion < 14`) load with an empty list — no migration of existing data.
- Placements reference prefabs by **path string**, so registry edits/reorders don't silently remap them (see `feedback_block_registry_changes`).
- Round-trip is covered by a save/reload unit test.

## Thumbnail renderer (WPGUI)

`HytalePrefabThumbnailRenderer`:

- Input: `prefabPath` (+ target pixel size). Loads prefab blocks (reuse the existing prefab-JSON parse; factor the parse out of `HytalePrefabPaster` into a shared loader if cleaner, otherwise call a small shared parser).
- Render: **top-down orthographic** — for each `(x, z)` column take the **topmost** non-empty block, map its block name → color, with light height-based shading for readability. Empty columns transparent.
- Block→color: reuse any existing Hytale block/terrain color source (verify what exists — e.g. registry/terrain colors used by `HytaleBlockPalette`/terrain rendering); deterministic hash-derived fallback color keyed by block name when no mapping exists.
- Cache: `Map<key(prefabPath,size), BufferedImage>`; render off the EDT; show a placeholder until ready. Bound the cache (LRU or simple cap).
- *Iso projection is a later upgrade; the renderer interface should not preclude it.*

## UI components (WPGUI)

**`HytalePrefabPalette`** (dockable):
- Searchable, category-grouped grid of thumbnails (built-in registry + `HytalePrefabDiscovery`). Search reuses `PrefabFileEntry.matchesSearch`.
- Each cell is a **drag source** (`DragGestureListener`/`Transferable`) exposing a custom `DataFlavor` carrying `{prefabPath, prefabName}` (a small serializable record or a `text/plain` path with an internal flavor).

**`PrefabPlacementOperation`** (extends `MouseOrTabletOperation`/`AbstractOperation`, modeled on `TownPlanOperation`):
- On activate: installs a **`DropTarget`** on the canvas accepting the prefab flavor; on drop, converts drop pixel → world `(x, y)`, builds a placement (`snapToSurface=true`, `rotationDegrees=0`), calls `Dimension.addHytalePrefabPlacement`, selects it.
- Mouse handling on existing placements: hit-test footprints; **drag body = move**; **drag rotate handle = set `rotationDegrees`** (continuous; hold **Shift** to snap to 15°/cardinal increments); **Delete** removes selected; click empty space deselects.
- Owns the options panel and coordinates selection with the list panel.
- On deactivate: removes the canvas `DropTarget` and selection handles.

**`HytalePrefabPlacementsPanel`** (dockable master list):
- Lists all placements (name + `(x, y)` summary). Two-way selection sync with the map.
- Editable fields for the selected placement: `X`, `Y`, `height`, `rotation (deg)`, `snap-to-surface` checkbox. Buttons: Delete, Duplicate (offset copy).
- Edits go through the `Dimension` update methods so the overlay repaints.

**Map overlay painting:**
- Hook the same paint path `WorldPainter` uses for overlays to draw, per placement: an **oriented footprint rectangle** (rotated by `rotationDegrees`, sized to the prefab footprint), a small **label**, selection highlight, and a **rotation handle** for the selected one — all transformed by the view's zoom/scroll. Footprint extent comes from the prefab's bounding box (cached alongside the thumbnail).

**Wiring / gating:**
- Add the tool button in `App.java` via `createButtonForOperation`. Provide the operation through the existing operation/plugin registration used by other Hytale tools (follow the `TownPlanOperation` registration pattern).
- Palette, tool button, and panel are **visible/enabled only for Hytale-platform worlds**.

## Interaction flow

1. User selects the Prefab Placement tool (Hytale world) → palette + Placements panel appear.
2. Drag a thumbnail → drop on the map → placement created at the drop `(x, y)`, height snapped to surface, rotation 0°, auto-selected; Placements panel focuses its fields.
3. Drag the body to move; drag the handle to rotate (Shift = snap); type in fields for exact values; Delete to remove.
4. List ↔ map selection stay in sync.
5. Save the world → placements persist. Reload → restored and redrawn. Export → baked (below).

## Rotation & resampling

Yaw about the vertical axis only. Vertical `height` is unaffected by yaw; resampling is purely in the horizontal `(x, z)` plane, per vertical layer.

`PrefabRotator.rotate(PrefabBlockData src, double degrees) → PrefabBlockData` — pure, unit-testable:

- **Cardinal (degrees ≡ 0/90/180/270, within epsilon):** exact integer rotation. Rotate each block's `(x, z)` offset (about the prefab anchor) by the quarter-turn matrix; transform each block's `rotation` int's yaw via the proven `new_ry = (old_ry + steps) % 4` (reuse a shared helper — see below). Lossless; no holes/overlaps. Equivalent in spirit to the existing `RotatedObject` + `Material.rotate` path, but applied to the paster's `PrefabBlockData`.
- **Off-cardinal:** **destination-grid nearest-neighbor** resample. Build a sparse source lookup `(x,y,z) → block` from `src`. Compute the rotated footprint bounds. For each vertical layer `y`, iterate destination `(dx, dz)` cells in the rotated bounds, inverse-rotate to source `(sx, sz)` (round to nearest), look up the source block at `(sx, y, sz)`; if present, emit a destination block with its yaw **snapped to the nearest cardinal** (`steps = round(degrees/90) mod 4`, then `(old_ry + steps) % 4`). Sampling the destination grid guarantees no holes; thin diagonal features may alias and duplicate (documented limitation). Fluids transformed the same way.

**Shared rotation helper:** extract the Hytale yaw math (`rotation = rx*16 + ry*4 + rz`; yaw transform `(ry + steps) % 4`) into one place (e.g. a `HytaleRotations` util or methods on `HytaleBlock`) and have **both** `Material.rotate`'s `hytale_rotation` handling and `PrefabRotator` call it — single source of truth, avoids drift.

## Export integration (WPCore)

In `HytaleWorldExporter`, alongside the existing layer-driven enqueue:

- After computing the region's chunks (same point as the current pending-paste execution), enqueue every placement whose **rotated footprint bounds intersect the region** (not just the anchor column — handles border-spanning prefabs, mirroring how layer pastes span chunks).
- Resolve height: `snapToSurface` → terrain height at `(x, y)` + 1 (consistent with layer behavior); else `placement.height`. If the column has no tile (Void) and `snapToSurface`, skip with a visible warning (or require manual height) — see edge cases.
- Apply `PrefabRotator.rotate(data, rotationDegrees)` to the loaded prefab data, then paste at `anchorWorldX = x`, `anchorWorldZ = y`, `anchorY = resolvedHeight` using the centering `blockOffsetX/blockOffsetZ`.
- **Paster change:** add a rotation-aware path. Either (a) add `paste(..., double rotationDegrees)` overloads that internally call `PrefabRotator`, or (b) accept pre-rotated `PrefabBlockData`. Keep the existing signatures delegating with `rotationDegrees = 0` for the layer callers (no behavior change for layers).
- **Marker fallback:** on paste failure (unresolvable prefab), `chunk.addPrefabMarker(x, y, z, category, path)` extended to carry rotation.

**`PrefabMarker` + BSON change:** add an **optional** `double`/`int rotation` field; serialize under the existing `"PrefabMarkers"` array; deserialize with **default 0 when absent** (backward compatible with existing exports). Note: whether the Hytale server honors marker yaw is **unverified** — baked blocks are the source of truth; the marker is best-effort.

## Edge cases

- **Missing prefab file at export** → marker fallback + log warning (no silent drop).
- **Drop on Void / no-tile column** → can't snap height; require a manual height or skip that placement with a visible warning.
- **Overlap with terrain / other placements** → pasted blocks are `sealProtected` (paster already does this) so terrain won't overwrite them; placements paste in list order (later wins on conflict).
- **Border-spanning / large prefabs** → enqueue by footprint-bounds intersection per region; multi-chunk paste handles cross-chunk writes.
- **Non-Hytale world** → feature hidden/disabled.
- **Undo/redo** → integrate add/move/delete with WorldPainter's undo manager **if it slots in cleanly**; otherwise v1 ships with a delete confirmation and undo is a fast-follow. *(Verify during implementation.)*

## Testing

- **Unit — `PrefabRotator`:** cardinal rotations are exact (positions + yaw); `0°` and `360°` == identity; four 90° steps == identity; off-cardinal destination sampling produces no holes over the footprint; yaw snaps to nearest cardinal. Reuse/parallel the verification table from the `2026-04-07` rotation spec for the yaw formula.
- **Unit — persistence:** `HytalePrefabPlacement` round-trips through a `Dimension` save/reload; old-world load yields an empty list.
- **Unit — thumbnail:** renderer yields a non-empty image for a known prefab; cache hit on second call.
- **Integration — export:** a world with one placement bakes blocks at the expected chunk coords (and, for a forced-missing prefab, writes a rotation-bearing marker).
- **Manual:** drag-drop → move → rotate (incl. Shift-snap) → edit fields → delete → save/reload → export; verify on Hytale only and hidden on Minecraft.

## Open items to verify during implementation

1. Existing Hytale block→color source for thumbnails (reuse vs. fallback palette).
2. Cleanest place for the shared rotation helper (`HytaleBlock` vs. a new `HytaleRotations`) and the prefab-JSON parse extraction.
3. Operation/plugin registration path used by `TownPlanOperation` (mirror it).
4. Undo/redo integration feasibility.
5. Whether the Hytale server honors `PrefabMarker` rotation (affects only the fallback's fidelity, not the baked path).

## File-by-file change list (anchors)

**WPCore:**
- `objects`/`hytale`: new `HytalePrefabPlacement.java`.
- `hytale/prefab/`: new `PrefabRotator.java`; `HytalePrefabPaster.java` (+rotation path, factor out parse if shared).
- `hytale/` or `minecraft/`: shared yaw-rotation helper (`HytaleRotations` or methods on `HytaleBlock`), reused by `Material.rotate`.
- `Dimension.java`: new list field + accessors + `readObject` migration + `CURRENT_WP_VERSION` bump.
- `hytale/chunk/HytaleChunk.java`: `PrefabMarker` + `addPrefabMarker` rotation.
- `hytale/chunk/HytaleBsonChunkSerializer.java` / `HytaleBsonChunkDeserializer.java`: marker rotation (optional, default 0).
- `hytale/export/HytaleWorldExporter.java`: enqueue placements per region + rotation-aware paste + marker fallback.

**WPGUI:**
- new `HytalePrefabThumbnailRenderer.java`, `HytalePrefabPalette.java`, `PrefabPlacementOperation.java`, `HytalePrefabPlacementsPanel.java`.
- `WorldPainter.java`: placement overlay painting + canvas `DropTarget` hooks.
- `App.java`: tool button + dockable panels, gated to Hytale.
