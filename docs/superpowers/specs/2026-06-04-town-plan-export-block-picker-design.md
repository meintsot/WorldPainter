# Town Plan Export-Block Picker — Design

**Date:** 2026-06-04
**Status:** Approved for planning
**Component:** TalePainter (WorldPainter Hytale edition) — WPCore + WPGUI
**Builds on:** Plan A backend (`TownLayoutSettings.block` already exists) + B1 GUI (`TownPlanOperation`).

## 1. Purpose

Let the user choose which block the Town Layout footprint exports as, instead of the hardcoded
default. The default becomes **stone**, overridable **per world** via a block picker in the Town
Plan tool panel.

## 2. Scope

In scope:
1. Change `TownLayoutSettings` default `block` from black wool to **stone** (`Material.STONE`).
2. An **"Export block: `<name>`"** button in the Town Plan tool's options panel that opens a dialog
   hosting the existing `MaterialSelector` (Minecraft + Hytale blocks, with search), and stores the
   chosen block in the dimension's `TownLayout` layer settings.
3. The button label reflects the current export block.

Out of scope (deferred):
- **Global remembered default** across worlds — per-world override + the stone built-in default
  already covers "default stone, override when needed." Easy to add later if wanted.
- **Marker-height / export on-off UI** — remains B3.

## 3. Components

### 3.1 Backend — `TownLayoutSettings` (WPCore)
Change the default `block` field initializer from `Material.get(BLK_WOOL, 15)` to `Material.STONE`.
No other backend change: `TownLayoutExporter` already reads `settings.getBlock()` and falls back to
`new TownLayoutSettings()` (now stone) when the dimension has no stored settings.

### 3.2 GUI — `TownPlanOperation` (WPGUI)
- Add an **"Export block: `<name>`"** `JButton` to `buildOptionsPanel()`, placed between the Rotation
  controls and the Stamp button.
- A `chooseBlock()` handler: open a small modal dialog (a `WorldPainterDialog`/`JDialog`) hosting a
  `MaterialSelector` pre-set to the current block, plus OK/Cancel. On OK, read the selected
  `Material`, then **get-or-create** the `TownLayoutSettings` for the current dimension
  (`dimension.getLayerSettings(TownLayout.INSTANCE)`, or a new `TownLayoutSettings()` if null), call
  `setBlock(selected)`, store it back via `dimension.setLayerSettings(TownLayout.INSTANCE, settings)`,
  and refresh the button label.
- On panel build (and tool activate), set the button label from the current settings' block, or the
  stone default when unset.
- `MaterialSelector`'s get/set-material accessors are confirmed during plan-writing (it's an existing
  reusable `JPanel`).

## 4. Data flow

chosen block → `TownLayoutSettings.block` → stored in the **dimension's layer settings** (saved with
the world) → read by `TownLayoutExporter` at export, on both Minecraft and Hytale. **No exporter
changes** beyond the default.

## 5. Testing

- **Backend (unit):** assert `new TownLayoutSettings().getBlock()` equals `Material.STONE`. Existing
  exporter/integration tests set the block explicitly, so they are unaffected by the default change.
- **Picker wiring (build + run verified):** the dialog opens with the current block selected; OK
  updates the button label and stores the setting; an export uses the chosen block. (Swing/JIDE
  picker UI is not meaningfully unit-testable.)

## 6. Open questions

None. Decisions: default = stone; per-world override only (no global default); picker in the Town
Plan tool panel; reuse `MaterialSelector` for the chooser.
