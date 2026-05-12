# TP-56 — Populate with Vegetation (Hytale)

Date: 2026-05-12

YouTrack: [TP-56](https://fsproject.youtrack.cloud/issue/TP-56)

## Background

WorldPainter's Hytale path can today place plants only one of two ways:

1. **Manual paint via `HytalePlantsLayer`** — the user picks a single Hytale terrain entry (e.g. `Plant_Grass_Tall`) and paints it pixel-by-pixel. Stored as a per-pixel 16-bit terrain index.
2. **Bundled prefabs via `HytalePrefabLayer`** — pre-authored multi-block scenes, painted on/off per pixel.

Neither knows about biomes. Painting a forest across `Seedling Woods` and an oasis across `Deserts` means the user has to swap brushes per region. Ferstborn's request (TP-56 comment, 2026-05-07): a single Layers-tab option that uses the **biome** the user already painted to decide what to spawn.

Minecraft has an analogue: `PlantLayer` (`WPCore/.../layers/plants/PlantLayer.java`) — a `CustomLayer` that carries a `PlantSettings[]` of plants and occurrence weights, exported via `Bo2ObjectProvider`. It is **not biome-aware**; the user creates one instance per intended plant mix.

This spec defines a Hytale-only, biome-driven auto-vegetation layer. Minecraft is out of scope (Approach A from the brainstorm).

## Problem

A user painting a Hytale map with multiple biomes (Drifting Plains, Tundra, Deserts, Boreal, Volcanic, …) currently has no way to express "fill each biome with the plants that belong there." They must hand-paint vegetation pixel-by-pixel or accept bare terrain. For a 43-biome game, this is the dominant time sink in landscape authoring.

## Goals

- One built-in Layers-tab entry on Hytale dimensions: **Auto Vegetation**.
- Painting it on a region → at export, every pixel in that region procedurally gets a plant whose species depends on the biome under the pixel.
- Per-biome configuration: coverage % and an ordered plant list with occurrence weights.
- Curated defaults shipped, so the layer "just works" the moment the user paints it.
- User-painted plants and prefabs take priority over auto-generated ones at the same pixel.
- Deterministic generation: re-exporting an unchanged dimension produces byte-identical block placement.
- Underwater plants survive the seal pass (TP-53 follow-up pattern).

## Non-Goals

- **Minecraft support.** Existing `PlantLayer` covers the Minecraft side; reuse later if we feel the need, but not in this ticket.
- **Per-pixel intensity.** Bit-per-pixel storage (on/off). No "denser-here-sparser-there" within a single biome.
- **Per-asset frost/heat tags in code.** Frost tolerance, heat tolerance, etc. are expressed implicitly by which plants the curated JSON lists for each biome — not by typed flags on the plant.
- **Multiple named mixes per dimension.** One global per-dimension settings object; no "Forest mix" / "Desert mix" CustomLayer instances.
- **Tree placement (multi-block plants).** Out of scope for v1; the algorithm places single-block surface-only plants. Multi-block trees can be added later by routing entries through the existing `HytalePrefabPaster` rather than `chunk.setHytaleBlock`.

## Design

### Mental model

```
terrain → fluid → painted plant overlay → AUTO VEG (new) → environment → entity
```

Auto vegetation slots in **after** the painted plant overlay in the existing per-pixel loop in `HytaleWorldExporter.populateChunkFromTile`. If the painted overlay already placed a plant at this pixel, auto-veg yields. Otherwise it consults the per-dimension settings: at the cell's biome, sample coverage %, then weighted-pick a plant from that biome's configured list. The chosen plant is placed at `height + 1` and `setSealProtected(true)` — same pattern the TP-53 follow-up established for painted plants.

### Architecture

```
WPCore (no UI)
├── hytale/
│   ├── HytaleAutoVegetationLayer.java        ← new. Singleton bit-per-pixel Layer.
│   ├── HytaleAutoVegetationSettings.java     ← new. Per-dimension ExporterSettings.
│   └── HytaleAutoVegetationDefaults.java     ← new. Loads + applies curated defaults.
└── resources/hytale/auto-vegetation-defaults.json  ← new. Curated biome → plant-mix table.

WPGUI
└── hytale/HytaleAutoVegetationDialog.java    ← new. Settings dialog (Swing).
```

Plus three small touch-points in existing files:

- `HytaleWorldExporter.populateChunkFromTile` — insert the auto-veg per-pixel block immediately after the existing painted plant overlay (currently at `HytaleWorldExporter.java:1632-1662`, marked `── Plant Overlay Layer ──`).
- `DefaultPlugin.java` — register `HytaleAutoVegetationLayer.INSTANCE` next to the other built-in `Layer` instances (`Frost`/`Caves`/`Populate` pattern).
- `DefaultPlugin.java` (same file) — register `HytaleAutoVegetationRenderer` next to the other built-in `LayerRenderer` instances.

### Data model

```java
public final class HytaleAutoVegetationLayer extends Layer {
    public static final HytaleAutoVegetationLayer INSTANCE = new HytaleAutoVegetationLayer();
    private HytaleAutoVegetationLayer() {
        super("HyAutoVeg", "Auto Vegetation", DataSize.BIT, false, 0);
    }
    private Object readResolve() { return INSTANCE; }
    private static final long serialVersionUID = 1L;
}

public final class HytaleAutoVegetationSettings implements ExporterSettings {
    private final Map<Integer /*biomeId*/, BiomeVegetationConfig> byBiome = new HashMap<>();
    private long globalSeed = new Random().nextLong();
    private boolean enabled = true;

    // getters / setters / clone()

    public static final class BiomeVegetationConfig implements Serializable {
        int coveragePercent;      // 0..100
        List<PlantEntry> plants;  // ordered for stable display; may be empty
    }

    public static final class PlantEntry implements Serializable {
        UUID hytaleTerrainId;     // resolves via HytaleTerrain.getById
        int occurrenceWeight;     // 1..100, relative
    }

    private static final long serialVersionUID = 1L;
}
```

Settings live on the Dimension via `dimension.setLayerSettings(HytaleAutoVegetationLayer.INSTANCE, settings)` — same pattern every other layer follows.

### Curated defaults

`auto-vegetation-defaults.json` lives in `WPCore/src/main/resources/hytale/`. Shape:

```json
{
  "Zone1_Drifting_Plains": {
    "coverage": 12,
    "plants": [
      {"terrain": "Plant_Grass_Tall",     "weight": 60},
      {"terrain": "Plant_Flower_White",   "weight": 20},
      {"terrain": "Plant_Flower_Yellow",  "weight": 20}
    ]
  },
  "Zone3_Frostmarch_Tundra": {
    "coverage": 4,
    "plants": [
      {"terrain": "Plant_Grass_Frosted", "weight": 70},
      {"terrain": "Sapling_Pine",         "weight": 30}
    ]
  }
}
```

- Keys are `HytaleBiome.getName()` strings (the `Zone*_*` names already in the registry).
- Plant entries are `HytaleTerrain.name` strings.
- Unknown biome names: warn + skip. Unknown plant names: warn + drop from that biome's list. A biome with all-unknown plants behaves as `coverage 0`.
- `HytaleAutoVegetationDefaults.applyTo(settings, allBiomes)` is the single entry point — used both at "Reset to defaults" time and at the lazy-seed-on-first-export moment described below.

### Settings dialog (UI)

Right-click the Auto Vegetation button in the Layers tab → Settings. Modal Swing dialog with:

- Top bar: **Enabled** checkbox, **Seed** numeric field, **Reset all to defaults** button.
- Main: a vertically-scrolling table, one row per biome in `HytaleBiome.getBiomeOrder()` order (with the same zone-grouped spacers used by the biome panel). Columns:
  - **Biome**: color swatch (from `HytaleBiome.getDisplayColor()`) + display name.
  - **Coverage**: 0–100 slider with the numeric value to its right. 0 dims the row.
  - **Plants & weights**: a flow of chips (`Plant_Grass_Tall · 60 ✕`); click a chip for an inline weight editor; click ✕ to remove; **+ add plant** opens a picker filtered to surface-only Hytale terrains via `HytaleBlockRegistry.isSurfaceOnlyBlock`.
- Bottom: **Cancel** / **OK**.

Stale chips (plant resolved to `null` because the registry no longer knows the name) render greyed-out with a tooltip.

### Export pipeline integration

Inside `HytaleWorldExporter.populateChunkFromTile`, immediately **after** the existing painted-plant-overlay block and **before** the environment / entity blocks. Pseudocode:

```java
if (autoVegEnabled && tile.getBitLayerValue(HytaleAutoVegetationLayer.INSTANCE, xInTile, zInTile)) {
    boolean alreadyHasPlant = HytalePlantsLayer.getPlantIndex(tile, xInTile, zInTile) != 0
                              || chunk.getHytaleBlock(localX, height + 1, localZ).isPresent();
    if (!alreadyHasPlant) {
        int biomeId = resolveBiome(tile, xInTile, zInTile, terrainBiomeName);
        BiomeVegetationConfig cfg = autoVegSettings.byBiome.get(biomeId);
        if (cfg != null && cfg.coveragePercent > 0 && !cfg.plants.isEmpty()) {
            long pixelSeed = mix(autoVegSettings.globalSeed, tile.getX(), tile.getZ(), xInTile, zInTile);
            Random rng = new Random(pixelSeed);
            if (rng.nextInt(100) < cfg.coveragePercent) {
                HytaleTerrain plant = weightedPick(cfg.plants, rng);
                HytaleBlock plantBlock = plant.getPrimaryBlock();
                if (isValidSubstrateFor(plantBlock, columnBelow)) {
                    chunk.setHytaleBlock(localX, height + 1, localZ, plantBlock);
                    chunk.setSealProtected(localX, height + 1, localZ, true);
                }
            }
        }
    }
}
```

Five notes:

1. **Deterministic RNG.** The seed mix combines the user-visible `globalSeed` with global block coordinates (`tile.getX() * 128 + xInTile`, same for z) — not tile-relative — so plants don't reshuffle when re-exporting and don't produce visible seams at tile boundaries.
2. **`resolveBiome` handles auto-biome (255)** by routing through `HytaleBiome.fromTerrainBiomeName(terrainBiomeName)`, reusing the exporter's existing terrain → biome heuristic.
3. **Substrate compatibility.** The current painted-plant path (`HytaleWorldExporter.java:1632-1662`) places at `height+1` unconditionally — the user chose the pixel manually, so any substrate is honored. Auto-veg is different: we don't want to drop grass on lava or cactus on ice. Introduce a **new** static helper `HytaleAutoVegetationLayer.isValidSubstrateFor(HytaleBlock plant, HytaleBlock substrate)` with a small allowlist: substrate must be a solid non-fluid, non-magma, non-ice block (default `true`; reject `HY_LAVA`, `HY_WATER`, `Magma`, `Ice`, `Snow` unless the plant's curated metadata explicitly allows the surface — punt that nuance to a v2 if needed). Call it only from the auto-veg block, not from the painted path.
4. **Seal-protection is unconditional**, matching the TP-53 follow-up rule for painted plants. The flag is a no-op on dry columns and is the only thing keeping the plant alive on flooded columns.
5. **`HytaleAutoVegetationLayer` storage check is O(1) per pixel** when the bit is zero — for tiles where the user never painted the layer, zero overhead. For painted tiles, one `Random` construction + one weighted-pick per pixel = 16k cheap ops per 128×128 tile.

### Lazy defaults

A user can paint the layer and export without ever opening the Settings dialog. To avoid "I painted it but nothing happened":

- When the exporter discovers the layer is painted and `dimension.getLayerSettings(HytaleAutoVegetationLayer.INSTANCE) == null`, it calls `HytaleAutoVegetationDefaults.applyTo(...)` to populate the curated defaults, attaches them to the dimension, and continues. The seeding is persisted, so subsequent exports don't redo it.
- Opening the Settings dialog on a layer with `null` settings does the same seed-and-attach before showing the table.

### Renderer

Subclass `BitLayerRenderer` for `HytaleAutoVegetationLayer`. A translucent leafy-green tint over painted pixels. Plug into `LayerManager` the same way the other Hytale-layer renderers do.

## Files Changed

**New:**
- `WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleAutoVegetationLayer.java`
- `WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleAutoVegetationSettings.java`
- `WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleAutoVegetationDefaults.java`
- `WPCore/src/main/resources/hytale/auto-vegetation-defaults.json`
- `WPGUI/src/main/java/org/pepsoft/worldpainter/hytale/HytaleAutoVegetationDialog.java`
- `WPCore/src/main/java/org/pepsoft/worldpainter/layers/renderers/HytaleAutoVegetationRenderer.java`

**Modified:**
- `WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleWorldExporter.java` — insert the auto-veg per-pixel block in `populateChunkFromTile` immediately after the `── Plant Overlay Layer ──` block (currently lines 1632-1662).
- `WPCore/src/main/java/org/pepsoft/worldpainter/DefaultPlugin.java` — register `HytaleAutoVegetationLayer.INSTANCE` and `HytaleAutoVegetationRenderer`.
- The right-click → Settings dispatch for the Hytale layers (locate the existing dispatcher used by `HytalePlantsLayer`/`HytaleFluidLayer` settings dialogs in WPGUI) — route the new layer to `HytaleAutoVegetationDialog`. Exact file to be confirmed at planning time.

## Test Plan

### Unit — defaults loader (`HytaleAutoVegetationDefaultsTest`)
1. Well-formed JSON parses into the expected biome → config map.
2. Unknown biome key → warning logged, key skipped, other keys parse normally.
3. Unknown plant terrain name → warning logged, entry dropped, biome still parses.
4. Empty JSON → empty map.

### Unit — algorithm (`HytaleAutoVegetationAlgorithmTest`)
1. Bit unset → no plant placed.
2. Bit set, coverage 0 → no plant placed.
3. Bit set, coverage 100 → plant placed every time.
4. Bit set, coverage 50 → ~50% over 10000 samples (within ±2%).
5. Weighted pick (60/30/10) over 1000 samples lands within ±5% of expected proportions.
6. Painted plant at same pixel → auto-veg skips.
7. Same `(seed, x, z)` → same plant twice (determinism).
8. Different `(x, z)` → varied plants (no degenerate same-plant-everywhere).
9. Auto biome (255) → routes through `fromTerrainBiomeName` correctly.

### Integration — end-to-end (`Tp56AutoVegetationExportTest`)
1. Tiny Hytale world, paint auto-veg over a Drifting Plains region, coverage 100% with one plant. Export. Read back BSON: assert plant at `height+1` in every painted cell, all seal-protected.
2. Same world, flooded region: plant survives seal pass, fluid layer contains water.
3. Re-export without changes: byte-identical block placement (determinism).
4. Lazy defaults: dimension with painted layer and `null` settings → after export, settings is non-null and matches the curated JSON.

### Regression
- `Tp60PlantSubstrateTest`, `Tp60PlantsPhysicsExemptToggleTest`, `Tp53UnderwaterPlantTest` must still pass — the existing painted-plant pipeline is untouched.

### Manual smoke (run before merge)
1. Fresh world, paint auto-veg over Drifting Plains → grass + flowers after export.
2. Paint over Tundra → sparse frosted grass / pine saplings.
3. Single-pixel manual plant inside an auto-veg region → that pixel keeps the manual plant; surrounding pixels get curated.
4. Auto-veg on a flooded column → plant on seabed, water above it.
5. Settings → Tundra coverage to 0 → re-export → Tundra bare, Drifting Plains unchanged.
6. Settings → Reset to defaults → values revert.
7. Load a pre-feature save → no auto-veg, no errors.

## Risks

- **Curation labor.** ~43 biomes × ~3-5 plants each ≈ 150 lines of curated JSON. One-time cost; landlord-level decision about which plants belong where. Mitigation: ship a coarse first pass (4-zone defaults) and iterate.
- **Substrate-helper extraction.** Refactoring the inline substrate check out of the painted-plant path is a small risk for regressions on that path. Mitigation: the regression tests above (`Tp60PlantSubstrateTest`) directly exercise it.
- **JSON loading at startup.** A malformed shipped JSON would prevent the layer from working. Mitigation: a focused unit test (`HytaleAutoVegetationDefaultsTest`) parses the actual shipped JSON; CI catches malformed files.
- **Plant-name drift over time.** As Hytale's asset list evolves, curated names may rot. Mitigation: stale chips render greyed-out in the dialog; unresolved names log warnings (don't crash); a periodic registry-vs-defaults audit can catch drift.
