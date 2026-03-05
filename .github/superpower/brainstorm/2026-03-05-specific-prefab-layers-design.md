# Specific Prefab Layers — Design Document

**Date:** 2026-03-05  
**Status:** Approved

## Problem

The current Prefabs tab in WorldPainter only offers 11 broad categories (Trees, Dungeon, NPC Settlement, etc.) via a NIBBLE layer limited to 16 values. Hytale has hundreds of specific prefab files across 75+ tree variants, 10+ dungeon types, 9 NPC factions, and more. Users cannot select a specific prefab like `Oak_Stage5_003.prefab.json` or create custom combinations.

## Solution Overview

Replace the category grid in the Prefabs tab with a searchable flat list of all individual `.prefab.json` files discovered from `HytaleAssets/Server/Prefabs/`. Users multi-select prefabs, pick a custom color, and generate a persistent BIT layer that appears in the Layers panel. Painting randomly places one of the selected prefabs per tile.

---

## Section 1: Prefab Discovery & Data Model

### Discovery
- At startup, scan `HytaleAssets/Server/Prefabs/` recursively for all `.prefab.json` files
- Each discovered file becomes a `PrefabFileEntry`:
  - **displayName**: filename without `.prefab.json` (e.g., `Oak_Stage5_003`)
  - **category**: top-level folder (e.g., `Trees`)
  - **subCategory**: second-level folder (e.g., `Oak`)
  - **relativePath**: full relative path used during export (e.g., `Prefabs/Trees/Oak/Stage_5/Oak_Stage5_003.prefab.json`)

### Layer Model
- New class: `HytaleSpecificPrefabLayer extends Layer`
  - **DataSize**: `BIT` (on/off per tile column)
  - Stores: list of `PrefabFileEntry` paths, user-chosen display name, user-chosen ARGB color
  - Each instance is a separate layer, persisted with the world file via serialization
  - Unique color derived from user's color picker choice (not auto-hashed)

---

## Section 2: Prefabs Tab UI

Replace the current 11-button category grid with:

1. **Search field** at the top — type-ahead filtering across all ~500+ prefab files (matches name, category, sub-category)
2. **Scrollable JList** with multi-select enabled (Ctrl+click, Shift+click) — each row shows: `[Category] SubCategory / FileName`
3. **"Create Layer" button** — opens a dialog:
   - **Name field**: auto-generated (e.g., "Oak Trees (5 variants)"), user-editable
   - **Color picker**: JColorChooser for the map overlay color
   - **Selected prefabs list**: shows included files, with remove button
   - **OK/Cancel**: OK creates the layer, Cancel discards
4. **Show/Solo checkboxes** remain at top, controlling visibility of all specific prefab layers

### Layers Panel Integration
- Each created prefab combination layer appears in the **Layers panel** (image 2) alongside Water/Lava, Frost, Void, Resources, Read Only
- Layer icon: small colored square matching the user-chosen color
- Show/hide and solo toggles per layer
- Clicking the layer in the Layers panel activates it as the current paint brush

---

## Section 3: Painting & Export

### Painting
- Select a specific prefab layer from the Layers panel to activate it as the brush
- Left-click paints (sets BIT=1), right-click erases (sets BIT=0)
- Map renderer shows the user-chosen color at painted tile columns

### Export
- The exporter iterates all `HytaleSpecificPrefabLayer` instances on each tile column
- Where BIT is set, randomly select one prefab path from the layer's list
- Random selection uses a deterministic seed derived from `(worldX, worldZ, layerSeed)` for reproducible exports
- Write a `PrefabMarker` into the chunk at `(x, height+1, z)` with the selected prefab's exact relative path and category

### Backward Compatibility
- The old `HytalePrefabLayer` (NIBBLE, 11 categories) is **kept** for existing worlds
- Both systems coexist: old category markers and new specific-file markers are both written to chunk `PrefabMarkers` array
- No migration needed — old worlds continue to work unchanged

---

## Files to Create/Modify

### New Files
| File | Purpose |
|------|---------|
| `WPCore/.../hytale/HytaleSpecificPrefabLayer.java` | New BIT layer class storing prefab file list + color |
| `WPCore/.../hytale/PrefabFileEntry.java` | Data class for a discovered prefab file |
| `WPCore/.../hytale/HytalePrefabDiscovery.java` | Scans assets dir, returns all `PrefabFileEntry` instances |
| `WPCore/.../hytale/renderers/HytaleSpecificPrefabLayerRenderer.java` | Renderer for the new layer type |
| `WPGUI/.../hytale/CreatePrefabLayerDialog.java` | Dialog with name + color picker + prefab list |

### Modified Files
| File | Change |
|------|--------|
| `WPGUI/.../App.java` → `createPrefabsPanel()` | Replace category grid with search + list + create button |
| `WPCore/.../hytale/HytaleWorldExporter.java` | Add export logic for `HytaleSpecificPrefabLayer` |
| `WPCore/.../DefaultPlugin.java` | Register new layer type |

---

## Out of Scope
- Prefab 3D preview in the selection list
- Import/export of layer definitions between worlds
- Folder-based randomization mode (may add later)
