# Normal-Priority Issues Batch Fix Design

Date: 2026-05-02

## TP-52: Creative Mode Should Also Set /op

### Problem
When exporting to Hytale creative mode, the player must manually type `/op self` even though they're already in creative mode.

### Root Cause
`HytaleWorldExporter.writeServerBoilerplate()` writes `permissions.json` with an empty `users` map. The OP group exists with `["*"]` permissions but no user is assigned to it.

### Fix
In `writeServerBoilerplate()` (line ~509), when `world.getGameType() == CREATIVE`, add `"Player": "OP"` to the users map. "Player" is Hytale's default player name for singleplayer.

### Files
- `HytaleWorldExporter.java` — `writeServerBoilerplate()` method

---

## TP-53: Prefabs and Plants Not Placed Underwater

### Problem
Custom layer prefabs and terrain plants don't appear underwater in exported Hytale worlds.

### Root Cause (two-part)
1. `Bo2LayerExporter.getPlacement()` checks `ATTRIBUTE_SPAWN_IN_WATER` (default: false). When the column is flooded and this attribute is not set, it returns `Placement.NONE` — silently skipping the object.
2. `sealAboveTerrainColumn()` clears non-decorative blocks above terrain up to water level, overwriting any objects that did get placed.

### Fix
For Hytale platform only, modify `getPlacement()` to allow underwater placement by default. Hytale has no runtime water physics — blocks and fluids coexist in separate data layers. The seal pass already preserves SUPPORT_DECORATIVE blocks (noPhysics objects) and fills fluid around them.

Specifically: when `flooded == true` and the platform is Hytale and the object has `SPAWN_ON_LAND = true` (default), treat it as `Placement.ON_LAND` instead of `Placement.NONE`. The object gets placed at terrain+1 and the seal pass fills water around it.

For objects WITHOUT noPhysics: mark all Hytale Bo2 objects as decorative during the seal pass regardless of the noPhysics flag, since Hytale blocks always coexist with fluids. This means changing `sealAboveTerrainColumn` to preserve ALL non-empty blocks (not just decorative ones) for the Hytale platform. Actually, the simpler approach: after Bo2 layer export for Hytale, mark all placed blocks as decorative. But the cleanest approach is: in `sealAboveTerrainColumn`, preserve any non-empty block that was placed by a layer exporter (i.e., above the terrain height), since the terrain loop only places blocks up to terrain height.

**Recommended approach:** In `getPlacement()`, add a Hytale platform check. When flooded on Hytale and the object is ON_LAND with no explicit water spawn attributes, still return ON_LAND. Then ensure the block survives the seal pass by marking Bo2-placed blocks as decorative for Hytale (set `decoratePlacedBlocks = true` for all Hytale Bo2 layers, not just noPhysics ones).

### Files
- `Bo2LayerExporter.java` — `getPlacement()` method
- `HytaleWorldExporter.java` — `applyCustomObjectLayers()` where `decoratePlacedBlocks` is set

---

## TP-50: Hytale Vanilla World Generation Past Map Borders

### Problem
Areas outside the painted map are empty void. Users want Hytale's native world generator to fill those areas.

### Root Cause
`HytaleWorldExporter.writeWorldConfig()` hardcodes `WorldGen.Type` to `"Void"`.

### Fix
Add a new world attribute `ATTRIBUTE_WORLD_GEN_TYPE` in `HytaleWorldSettings` with default `"Void"`. Expose it in `ExportWorldDialog` as a dropdown with options: "Void", "Standard", "Elevated" (Hytale's known generation types). Write the selected value to `config.json` during export.

### Files
- `HytaleWorldSettings.java` — new attribute constant
- `HytaleWorldExporter.java` — `writeWorldConfig()` uses attribute instead of hardcoded "Void"
- `ExportWorldDialog.java` — new dropdown for world gen type

---

## TP-46: Map Merging (Design Only — Deferred to Separate Cycle)

Large feature requiring its own design → plan → implementation cycle. Involves adding "Import Map and Merge" to TileEditor, loading a .world file via WorldIO, cloning tiles with offset, and conflict resolution. Estimated ~200-400 lines of new code.
