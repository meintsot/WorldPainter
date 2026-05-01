# TP-51 — Eyedropper crash on Hytale discrete layers

**Issue:** [TP-51](https://fsproject.youtrack.cloud/issue/TP-51) — Major
**Date:** 2026-05-01
**Status:** Design approved; pending implementation plan.

## Problem

`Eyedropper.java:135` throws `UnsupportedOperationException("Discrete layer <name> not supported")` whenever the popup-builder encounters a non-ReadOnly discrete layer that is neither `Biome` nor `Annotations`. The Hytale platform contributes four such layers — `HytaleEnvironmentLayer`, `HytaleFluidLayer`, `HytaleEntityLayer`, `HytalePrefabLayer` — so any "Eyedropper" / "Select on map" interaction at a column carrying any of them crashes.

Reporter scenario (from the YouTrack ticket): import a Talepainter-generated Hytale world, pick the spray tool with `Pink_Crystal_Block`, click "Select on map" under "Only on", then click the map. The Eyedropper inspects the clicked column, sees the `Hytale Environment` layer in `layers`, and throws.

## Goal

Match WorldPainter-for-Minecraft behavior: every discrete layer (Biome, Annotations, every Hytale layer) is fully selectable in the Eyedropper popup, with a human-readable name and an appropriate icon. No crash, even on unknown discrete layers.

## Non-goals

* Changing the `Layer` base class API (rejected option B during brainstorming).
* Introducing a generic `DiscreteLayerRenderer` registry (rejected option C).
* Restructuring the existing `Biome` / `Annotations` special cases.
* Any Hytale-side changes — every per-value name source already exists.

## Approach

Approach **A** from brainstorming: extend the existing `if/else if` chain in `Eyedropper.java` (lines 109-144) with explicit `instanceof` branches for the four Hytale discrete layers, plus a generic fallback for any other discrete non-ReadOnly layer.

## Files touched

| File | Change |
|---|---|
| `WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/tools/Eyedropper.java` | Replace the `throw` branch at lines 134-136 with per-layer cases and a fallback. Extract per-value naming into a package-private static helper `discreteLayerEntry(Layer, int)` for unit-testability. |
| `WorldPainter/WPGUI/src/test/java/org/pepsoft/worldpainter/tools/EyedropperDiscreteLayerTest.java` | New JUnit 4 test class exercising the helper. |

No other files change. No imports of new top-level classes are introduced beyond the four Hytale layer classes (already in `WPCore`, accessible from `WPGUI`).

## Naming and icon contract

Per-value names come from existing static tables — no new data is introduced.

| Layer | Name shown in popup | Out-of-range fallback |
|---|---|---|
| `HytaleEnvironmentLayer` | `"Hytale Environment: " + HytaleEnvironmentData.getById(value).getDisplayName()` | `"Hytale Environment: value " + value` |
| `HytaleFluidLayer` | `"Hytale Fluid: " + FLUID_NAMES[HytaleFluidLayer.normalizeFluidValue(value)]` | `"Hytale Fluid: value " + value` |
| `HytaleEntityLayer` | `"Entities: " + DENSITY_NAMES[value]` | `"Entities: value " + value` |
| `HytalePrefabLayer` | `"Prefabs: " + PREFAB_NAMES[value]` | `"Prefabs: value " + value` |
| **Generic fallback** (any other discrete non-ReadOnly layer) | `layer.getName() + ": " + value` | (this IS the fallback — no further fallback needed) |

For all branches the icon is `new ImageIcon(scaleIcon(layer.getIcon(), 16))` — the same call already used at line 133 for non-discrete layers.

## Helper extraction

For testability we move the per-layer naming into a package-private static method:

```java
static class DiscreteEntry {
    final String name;
    final Icon  icon;
    DiscreteEntry(String name, Icon icon) { this.name = name; this.icon = icon; }
}

static DiscreteEntry discreteLayerEntry(Layer layer, int value) { ... }
```

The popup-builder calls `discreteLayerEntry(layer, value)` and adds the resulting menu action with the existing `callback.layerSelected(layer, value)` handler — identical to the current Biome/Annotations path.

`Biome` and `Annotations` keep their dedicated branches above the new code; we don't refactor them in this change.

## Selection behavior

Unchanged: clicking the menu entry calls `callback.layerSelected(layer, value)`. The downstream consumers of `layerSelected` (Spray paint "Only on" / "Except on" filter, etc.) already accept any `(Layer, int)` tuple, including the four Hytale layers.

## Edge cases

* **Out-of-range value** (corrupted save, future migration, raw nibble bits) → fallback string `"<Layer name>: value <raw>"`. No exception.
* **paintTypes filter** — the existing `if ((paintTypes != null) && (! paintTypes.contains(LAYER))) return;` guard (already applied to non-discrete layers at line 129) is mirrored at the top of the new branch so users who restrict the eyedropper to terrain/biome don't see Hytale layer entries.
* **SYSTEM_LAYERS** continue to be skipped first by the existing line 126-127 check; nothing reaches the new code for them.
* **ReadOnly discrete layers** continue to take the existing non-discrete path at line 128.

## Testing

New JUnit 4 test class `EyedropperDiscreteLayerTest` covers `discreteLayerEntry(Layer, int)`:

1. `HytaleEnvironmentLayer.INSTANCE` with a valid id obtained at runtime via `HytaleEnvironmentData.getByName("Env_Zone1_Forests").getId()` (ids are sequential and assigned at class init, so don't hardcode them) → name contains `"Forests"`.
2. `HytaleEnvironmentLayer.INSTANCE` with id `9999` (out of range) → fallback string, no exception.
3. `HytaleFluidLayer.INSTANCE` with `FLUID_LAVA` → name contains `"Lava"`.
4. `HytaleFluidLayer.INSTANCE` with a legacy value (e.g. `9`) → migrated to `Lava` via `normalizeFluidValue`, name contains `"Lava"`.
5. `HytaleEntityLayer.INSTANCE` with `DENSITY_DENSE` → name contains `"Dense"`.
6. `HytalePrefabLayer.INSTANCE` with `PREFAB_DUNGEON` → name contains `"Dungeon"`.
7. Generic non-Hytale discrete layer (a small private static test stub class declared inside `EyedropperDiscreteLayerTest`, extending `Layer` with `discrete=true` via the protected constructor) with value `5` → fallback string ends with `": 5"`, no exception.
8. Each test asserts that `discreteLayerEntry(...).icon` is non-null.

The existing `Eyedropper.tick(...)` flow is not unit-tested directly (it requires Swing wiring); the helper extraction is the testable seam. Manual reproduction of the original ticket repro (import Hytale world → spray "Only on" → Select on map → click) becomes the integration verification.

## Risks

* **Hytale layer reference from WPGUI** — `Eyedropper.java` (in WPGUI) will import four Hytale layer classes from WPCore. WPGUI already depends on WPCore and already references Hytale-specific classes (e.g. `HytaleAssetsLocator`), so this isn't a new architectural seam.
* **Icon size** — `layer.getIcon()` returns a 16×16 `BufferedImage` for all four Hytale layers (verified in their `createIcon()` methods); `scaleIcon(..., 16)` is a no-op cost.
* **Future discrete layers** — covered by the generic fallback. New layers won't crash the eyedropper; if they want richer per-value names they can be added to the chain like the Hytale ones.

## Out-of-scope follow-ups

If we later add a fifth Hytale discrete layer or a discrete custom-layer plugin, refactoring toward Approach B (`Layer.getValueName(int)` API) becomes attractive. Not done now to keep the Major bug fix surgical.
