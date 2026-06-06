# TP-119 — "Only on" layer intersection toggle — Design

**Date:** 2026-06-06
**Status:** Approved for planning
**Component:** TalePainter (WorldPainter Hytale edition) — WPCore + WPGUI
**Issue:** [TP-119] Global operations: toggle to apply only where selected layers overlap (intersection)

## 1. Purpose

When 2+ "only on" items are selected for a filter, let the user choose whether the operation
applies where **any** selected item is present (union, current behavior) or only where **all** of
them overlap (intersection). This is exposed in the shared brush-options filter panel, so it works
for both **brushes** and **Global Operations** (the Fill dialog).

## 2. Background / current behavior (important correction to the ticket)

The ticket's premise — *"brush mode already does the intersection"* — is **not accurate in the
code**. Brushes and Global Operations share one filter-construction path:

```
BrushOptions.getFilter() → new DefaultFilter(... onlyOn ...) → OnlyOnTerrainOrLayerFilter.create(dimension, onlyOnItem)
```

When the "only on" item is a `List` (2+ items), `OnlyOnTerrainOrLayerFilter.create()` wraps the
sub-filters in **`AnyOfFilter`** (returns the max strength → **OR / union**). This is true for
**both** brushes and global operations today; neither does intersection. Multi-select "only on"
support was added recently (commits `320babe8`, `f3da7b87`, `9b159e17`).

The building block for the requested behavior already exists: **`AllOfFilter`** (returns the min
strength → **AND / intersection**), currently used by `ExceptOnTerrainOrLayerFilter.create()`.

So the feature is: let the user select `AllOfFilter` instead of `AnyOfFilter` for the multi-item
"only on" case, defaulting to the current `AnyOfFilter`.

## 3. Scope

In scope:
1. A boolean "intersection" choice threaded UI → `DefaultFilter` → `OnlyOnTerrainOrLayerFilter.create()`.
2. Two radio buttons in `BrushOptions` — **"any of these"** (default) / **"all of these"** — shown
   only when 2+ "only on" items are selected.
3. Persistence of the choice in saved filter presets (`FilterPreset`).
4. Unit tests for the model + preset round-trip.

Out of scope (YAGNI):
- Any analogous toggle for **"except on"**. Its current `AllOfFilter` ("block where *any*
  exception matches") is already the intuitive behavior and the ticket does not request a change.
- No change to `FillDialog` itself — it already consumes `brushOptions1.getFilter()`.
- Driving/asserting the Swing UI automatically (not meaningfully unit-testable, consistent with
  prior tickets).

## 4. Components

### 4.1 Model — `OnlyOnTerrainOrLayerFilter.create()` (WPCore)
Add an overload:
```java
public static Filter create(Dimension dimension, Object item, boolean intersection)
```
- When `item instanceof List`: wrap the mapped sub-filters in `AllOfFilter` if `intersection`,
  else `AnyOfFilter` (unchanged).
- Single-item case: unchanged (no combiner; `intersection` irrelevant).
- Keep the existing 2-arg `create(dimension, item)` delegating to the 3-arg with
  `intersection = false`, so all other call sites are untouched.

### 4.2 Model — `DefaultFilter` (WPCore)
- Add a `final boolean onlyOnIntersection` field.
- **Add a new constructor overload** with the extra trailing `boolean onlyOnIntersection` param; the
  **existing 12-arg constructor is kept** and delegates to the new one with `false`. This leaves the
  scripting caller `CreateFilterOp` (line 292) and any other direct caller untouched — they keep
  union behavior.
- The new constructor builds `onlyOnFilter` via the 3-arg
  `create(dimension, onlyOnItem, onlyOnIntersection)`.
- Add `isOnlyOnIntersection()` getter (used by `setFilter()` / preset capture for round-tripping).
- Add `Builder.onlyOnIntersection(boolean)` (default `false`); `Builder.build()` calls the new
  constructor.
- Direct callers of `new DefaultFilter(...)`: `Builder.build()` (updated to pass the flag),
  `BrushOptions.getFilter()` (updated to pass the radio state), and `CreateFilterOp`
  (**unchanged** — uses the kept 12-arg overload, stays union). Scripting-API intersection support
  is out of scope.

### 4.3 UI — `BrushOptions` (WPGUI, NetBeans `.form` panel)
- Add a `ButtonGroup` of two `JRadioButton`s — **"any of these"** (selected by default) and
  **"all of these"** — placed under the "only on" row.
- **Visibility:** shown/enabled only when `onlyOn` is a `List` with ≥ 2 items; hidden/disabled
  otherwise (with 0–1 items, union == intersection, so the choice is meaningless). Wire this into
  the existing `installPaint(onlyOn, …)` / `setControlStates()` path that already runs whenever the
  "only on" selection changes.
- `getFilter()`: pass the radio state as the `onlyOnIntersection` argument to the `DefaultFilter`
  constructor.
- `setFilter(DefaultFilter)`: set the radio from the filter shape — `onlyOnFilter instanceof
  AllOfFilter` → "all of these", otherwise "any of these". (`AllOfFilter` is already detected at the
  existing branch in `setFilter()`.)
- Radio changes trigger the existing `filterChanged()` notification so the live filter updates.
- **Implementation note / main risk:** inserting the radio pair into the existing
  `GroupLayout`-managed `.form`. Resolve in the plan: prefer editing `BrushOptions.form` +
  regenerated `initComponents()` consistently; fall back to adding a small sub-panel programmatically
  after `initComponents()` if a clean `.form` edit proves impractical.

### 4.4 Persistence — `FilterPreset` (WPCore, `Serializable`)
- Add `private boolean onlyOnIntersection;` (default `false`) with getter/setter.
- Include it in `captureFrom(...)` (new parameter) and apply it on load.
- Wire `BrushOptions.saveFilterPreset()` to capture the radio state and `loadFilterPreset()` to
  restore it.
- **Back-compat:** adding a field to a `Serializable` class is compatible; presets serialized before
  this change deserialize the missing field as `false` (union) — the correct legacy behavior. No
  `serialVersionUID` change.

## 5. Data flow

```
radio ("any"/"all") in BrushOptions
  → BrushOptions.getFilter() passes onlyOnIntersection
    → new DefaultFilter(... onlyOnItem, onlyOnIntersection ...)
      → OnlyOnTerrainOrLayerFilter.create(dim, onlyOnItem, onlyOnIntersection)
        → AllOfFilter (intersection)  |  AnyOfFilter (union, default)
          → modifyStrength() during brush paint AND global Fill operations
```
Round-trip (preset save/load and `setFilter`/`getFilter`) preserves the choice via the
`AllOfFilter` vs `AnyOfFilter` shape and the `onlyOnIntersection` flag.

## 6. Testing

- **Model (unit, WPCore):**
  - `OnlyOnTerrainOrLayerFilter.create(dim, [a,b], true)` returns an `AllOfFilter`;
    `create(dim, [a,b], false)` returns an `AnyOfFilter`; single item returns a plain
    `OnlyOnTerrainOrLayerFilter` regardless of the flag.
  - `DefaultFilter` with two "only on" layers: with `onlyOnIntersection = true`,
    `modifyStrength` returns 0 unless **both** layers are present at (x,y); with `false`, returns the
    input strength where **either** is present. (Use a small synthetic `Dimension`/tile or existing
    test fixtures.)
- **Persistence (unit, WPCore):** `FilterPreset.captureFrom(..., onlyOnIntersection = true)` →
  `resolveOnlyOn()` + `isOnlyOnIntersection()` preserves both the items and the flag across a
  serialize/deserialize round-trip; a preset missing the field loads as `false`.
- **UI (build-verified + manual):** with 2+ "only on" layers the radios appear and default to "any";
  switching to "all" makes a brush / a Fill operation affect only the overlap; restoring a saved
  "all" preset re-selects the radio. (Swing/JIDE panel not unit-tested.)

## 7. Open questions

None. Decisions locked: shared scope (brushes + global ops); two radio buttons "any of these" /
"all of these"; default = "any" (union); persisted in `FilterPreset`; "except on" unchanged.
