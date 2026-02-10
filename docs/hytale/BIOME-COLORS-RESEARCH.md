# Hytale Biome-Specific Grass & Water Colors — Research Report

## Executive Summary

Hytale uses a **two-system approach** for biome coloring:
1. **Vegetation Tint** — A per-column ARGB color stored in `BlockChunk`, derived from `TintProvider` in biome JSONs. This tints grass, leaves, and vegetation — analogous to Minecraft's biome grass/foliage colors.
2. **Water Tint** — A hex color string stored in `Environment` JSONs (`WaterTint` field). Each environment defines its own water color.

WorldPainter's Hytale implementation fully supports both systems, storing tints per-column in exported chunks and mapping biomes to environments (which carry water tints).

---

## 1. How Hytale's Tint System Works (Decompiled Source)

### 1.1 TintProvider Hierarchy

From the decompiled Hytale source (`com.hypixel.hytale.builtin.hytalegenerator.tintproviders`):

| Class | Purpose |
|---|---|
| `TintProvider` (abstract) | Base class. Default tint = `Color(91, -98, 40)` → `#5B9E28` (grass green) |
| `ConstantTintProvider` | Returns a single fixed ARGB color |
| `DensityDelimitedTintProvider` | Uses a `Density` (noise function) to select between multiple `Constant.TintProvider` colors based on density ranges |
| `NoTintProvider` | Returns `Result.WITHOUT_VALUE` (no tint) |

### 1.2 Tint Generation Flow

From `ChunkGeneratorExecution.generateTintMapping()`:

1. For each (x, z) column in a 32×32 chunk (plus 4-block radius for smoothing):
   - Look up the biome at that position
   - Call `biome.getTintContainer().getTintColorAt(seed, x, z)` → returns an ARGB int
2. **Smooth the tint** using a circular 4-block radius average (box blur):
   - For each column, average all tint colors within a circle of radius 4 blocks
   - Result: `0xFF000000 | (avgR << 16 | avgG << 8 | avgB)`
3. Store via `blockChunk.setTint(x, z, color)` — tint is **per-column** (x, z), not per-block

### 1.3 TintContainer

The `TintContainer` uses a weighted noise map with conditions:
- A list of `TintContainerEntry` objects, each with a noise property and optional coordinate condition
- Falls back to a `DefaultTintContainerEntry` if no entry matches
- `colorMapping.get(seed, x, z, ...)` retrieves the actual color using noise

### 1.4 What Gets Tinted

The tint is a **per-column color** applied to the entire vertical column. Hytale blocks have an `IsTinted` flag — only blocks marked as tintable (grass, leaves, foliage) receive the tint color during rendering. The tint color itself is stored at the chunk level, not per-block.

---

## 2. Biome JSON TintProvider Data

### 2.1 TintProvider Structure in JSON

Each biome JSON file has a `TintProvider` section. Two patterns exist:

**Pattern A — Constant (single color):**
```json
"TintProvider": {
    "$NodeId": "Constant.TintProvider-...",
    "Type": "Constant",
    "Color": "#5b9e28"
}
```

**Pattern B — DensityDelimited (noise-based multiple colors):**
```json
"TintProvider": {
    "$NodeId": "DensityDelimited.TintProvider-...",
    "Density": {
        "Type": "SimplexNoise2D",
        "Lacunarity": 5,
        "Persistence": 0.2,
        "Octaves": 2,
        "Scale": 100,
        "Seed": "tints"
    },
    "Delimiters": [
        {
            "Tint": { "Type": "Constant", "Color": "#508A29" },
            "Range": { "MinInclusive": -1, "MaxExclusive": -0.33 }
        },
        {
            "Tint": { "Type": "Constant", "Color": "#598D26" },
            "Range": { "MinInclusive": -0.33, "MaxExclusive": 0.33 }
        },
        {
            "Tint": { "Type": "Constant", "Color": "#5F8F26" },
            "Range": { "MinInclusive": 0.33, "MaxExclusive": 1 }
        }
    ]
}
```

### 2.2 Complete Biome Tint Color Table

All tint colors extracted from biome JSONs (119 total color entries across all files):

#### Zone 1 — Emerald Grove (Plains1)

| Biome File | Tint Colors | Pattern |
|---|---|---|
| **Plains1_Oak** | `#5b9e28`, `#6ca229`, `#7ea629` | DensityDelimited (3 colors) |
| **Plains1_River** | `#5b9e28`, `#6ca229`, `#7ea629` | DensityDelimited (3 colors) |
| **Plains1_Shore** | `#5b9e28`, `#6ca229`, `#7ea629` | DensityDelimited (3 colors) |
| **Plains1_Mountains** | `#5b9e28`, `#6ca229`, `#7ea629` | DensityDelimited (3 colors) |
| **Plains1_Deeproot** | `#5b9e28`, `#6ca229` | DensityDelimited (2 colors) |
| **Plains1_Gorges** | `#5b9e28`, `#65ab30`, `#74ab30` | DensityDelimited (3 colors, brighter) |

**Plains1 color palette:** Vibrant greens (`#5b9e28` → `#7ea629`), shifting toward yellow-green

#### Zone 2 — Howling Sands (Desert1)

| Biome File | Tint Colors | Pattern |
|---|---|---|
| **Desert1_River** | `#f2cd66`, `#d4b253`, `#e6c66e` | DensityDelimited (3 colors) |
| **Desert1_Rocky** | `#f2cd66`, `#d4b253`, `#e6c66e` | DensityDelimited (3 colors) |
| **Desert1_Shore** | `#C9A653`, `#B6A44E`, `#A09D45`, `#909A3F` | DensityDelimited (4 colors) |
| **Desert1_Oasis** | `#cdab4b` (outer), `#7c9530` (transition), `#50265e` (special), `#457b26`, `#5a8728`, `#6a8728` (inner lush) | Complex nested DensityDelimited |
| **Desert1_Stacks** | `#5b9e28` | Constant |

**Desert1 color palette:**
- **Inland desert:** Golden yellows (`#f2cd66`, `#d4b253`, `#e6c66e`)
- **Shore/transition:** Sandy gold → olive (`#C9A653` → `#909A3F`)
- **Oasis:** Green-dominated with lush vegetation (`#457b26` → `#6a8728`)
- **Stacks:** Default green (presumably rocky without vegetation)

#### Zone 3 — Borea (Boreal1 / Taiga1)

| Biome File | Tint Colors | Pattern |
|---|---|---|
| **Boreal1_Hedera** | `#27633c` | DensityDelimited (1 color = constant) |
| **Boreal1_Henges** | `#5b9e28` | Constant |
| **Taiga1_Redwood** | `#2b602e`, `#256f2c` | DensityDelimited (2 colors) |
| **Taiga1_Mountains** | `#C9A653`, `#B6A44E`, `#A09D45`, `#909A3F` | DensityDelimited (4 colors) |
| **Taiga1_River** | `#C9A653`, `#B6A44E`, `#A09D45`, `#909A3F` | DensityDelimited (4 colors) |
| **Taiga1_Shore** | `#C9A653`, `#B6A44E`, `#A09D45`, `#909A3F` | DensityDelimited (4 colors) |

**Borea color palette:**
- **Hedera:** Deep forest greens (`#27633c` — very dark teal-green)
- **Redwood:** Dark cold greens (`#2b602e`, `#256f2c`)
- **Mountains/River/Shore:** Shared sandy-olive palette (`#C9A653` → `#909A3F`), same as Desert1_Shore — used for alpine/tundra areas with sparse vegetation

#### Zone 4 — Devastated Lands (Volcanic1)

| Biome File | Tint Colors | Pattern |
|---|---|---|
| **Volcanic1_River** | `#508A29`, `#598D26`, `#5F8F26` | DensityDelimited (3 colors) |
| **Volcanic1_Jungle** | `#508A29`, `#598D26`, `#5F8F26` | DensityDelimited (3 colors) |
| **Volcanic1_Shore** | `#5b9e28` | Constant |
| **Volcanic1_Caldera** | *(not in color search results — likely constant or no tint)* | — |

**Volcanic1 color palette:** Darker, more muted greens (`#508A29` → `#5F8F26`), slightly olive-shifted compared to Plains1

#### Ocean (Ocean1)

| Biome File | Tint Colors | Pattern |
|---|---|---|
| **Oceans** | `#5b9e28`, `#6ca229` | DensityDelimited (2 colors) |

#### Root / Special Biomes

| Biome File | Tint Colors | Pattern |
|---|---|---|
| **Basic** | `#5b9e28` | Constant |
| **Void** | `#5b9e28` | Constant |
| **Void_Buffer** | `#5b9e28` | Constant |
| **Void_Buffer_Oasis** | `#5b9e28` | Constant |

#### Experimental Biomes

| Biome File | Tint Colors | Notes |
|---|---|---|
| **Glacial1_River** | `#335927`, `#4F681D`, `#847632` | Cold dark greens to brown |
| **Dunes** | `#e3be62`, `#f2d079`, `#fcdf92` | Warm sandy yellows |
| **BasicForest** | `#50911f`, `#45911f` (canopy), `#5b9e28`, `#6ca229` (ground) | Complex nested tint system |
| **Mountains** | `#508A29`, `#598D26`, `#5F8F26` | Same as Volcanic |
| **ForestRivers** | `#508A29`, `#598D26`, `#5F8F26` | Same as Volcanic |

### 2.3 Default Tint Color

The Hytale engine default tint (from `TintProvider.DEFAULT_TINT`) is:
```java
ColorParseUtil.colorToARGBInt(new Color(91, -98, 40))
```
This maps to `#5B9E28` — the grass green seen in most plains/basic biomes.

---

## 3. Water Tint System (Environment-Based)

Water color in Hytale is controlled by the **Environment**, not the biome directly. Each environment JSON has a `WaterTint` hex field.

### 3.1 Complete Water Tint Table

| Environment | Water Tint | Zone | Visual |
|---|---|---|---|
| **Env_Zone0** | `#1070b0` | Ocean Deep | Deep blue |
| **Env_Zone0_Cold** | `#2076b5` | Ocean Cold | Cool blue |
| **Env_Zone0_Temperate** | `#1983d9` | Ocean Temperate | Standard blue |
| **Env_Zone0_Warm** | `#198dea` | Ocean Warm | Bright blue |
| **Env_Zone1** (Base) | `#1983d9` | Emerald Grove | Standard blue |
| **Env_Zone1_Autumn** | `#616f6f` | Autumn Forest | Dark gray-teal |
| **Env_Zone1_Swamps** | `#66682b` | Swamps | Murky green-brown |
| **Env_Zone1_Azure** | `#20a0ff` | Azure Forest | Bright cyan-blue |
| **Env_Zone2** (Base) | `#198dea` | Howling Sands | Bright blue |
| **Env_Zone2_Oasis** | `#30b8c0` | Oasis | Teal/turquoise |
| **Env_Zone3** (Base) | `#2076b5` | Borea | Cool blue |
| **Env_Zone3_Glacial** | `#a0d8ef` | Glacial | Light icy blue |
| **Env_Zone3_Hedera** | `#264D3D` | Hedera | Dark poisoned green |
| **Env_Zone4** (Base) | `#667030` | Devastated Lands | Murky green |
| **Env_Zone4_Jungles** | `#4a6020` | Jungles | Dark swamp green |
| **Env_Zone4_Volcanoes** | `#c04020` | Volcanoes | Lava red-orange |
| **Env_Zone4_Shores** | `#667030` | Shores | Murky green |
| **Env_Portals_Oasis** | `#30b8c0` | Portal — Oasis | Teal |
| **Env_Portals_Hedera** | `#2076b5` | Portal — Hedera | Cool blue |

Most Zone 1 and Zone 2 environments share standard blue water (`#1983d9` or `#198dea`). Notable exceptions:
- **Swamp water:** `#66682b` (murky green-brown)
- **Autumn water:** `#616f6f` (dark gray)
- **Glacial water:** `#a0d8ef` (icy light blue)
- **Volcanic water:** `#c04020` (lava red)
- **Zone 4 water:** `#667030` (toxic green)
- **Oasis water:** `#30b8c0` (tropical teal)

---

## 4. WorldPainter Implementation

### 4.1 Architecture Overview

WorldPainter implements biome-specific colors through these classes:

| Class | Role |
|---|---|
| `HytaleBiome` | Registry of all biomes with tint color (ARGB int), environment name, and display color |
| `HytaleEnvironmentData` | Registry of all environments with water tint hex strings |
| `HytaleChunk` | Per-column storage for biome name, environment, tint (int), and water tint (String) |
| `HytaleWorldExporter` | Resolves biome → environment → tint during export, writes to chunks |
| `HytaleBsonChunkSerializer` | Serializes tint as `IntBytePalette` in BlockChunk BSON, water tints as metadata |

### 4.2 Biome → Tint Resolution (Export Flow)

In `HytaleWorldExporter.populateChunkFromTile()`:

1. **Check if user painted a biome** via the Biome layer (`paintedBiomeId`)
2. If painted: Look up `HytaleBiome.getById(id)` → get tint color + environment
3. If auto (`BIOME_AUTO = 255`): Derive biome from terrain type via `HytaleBiome.fromTerrainBiomeName()`
4. Set per-column: `chunk.setBiomeName()`, `chunk.setEnvironment()`, `chunk.setTint()`
5. Water tint from fluid layer override or environment: `chunk.setWaterTint()`

### 4.3 Tint Storage in Chunk Data

Tints are stored in the `BlockChunk` BSON using Hytale's `IntBytePalette` format:
- A palette of unique ARGB int values
- A bit-field array (10 bits per entry, 1024 entries for 32×32) mapping each column to a palette index
- This matches Hytale's native chunk format exactly

Water tints are stored in a separate WorldPainter metadata BSON document as key-value pairs (`"x,z": "#hexcolor"`).

### 4.4 Separate Grass Types Per Biome

Hytale does **not** use separate grass block types per biome (unlike some implementations). Instead:
- There is one `Soil_Grass` block type
- The block has an `IsTinted` flag
- The per-column tint color is applied to all tintable blocks in that column during rendering
- This means grass color is controlled entirely by the tint system, not by different block types

WorldPainter maps `Material.GRASS_BLOCK` → `"Soil_Grass"` for all biomes, then applies the appropriate tint color based on the biome.

### 4.5 WorldPainter's Biome Color Assignments

WorldPainter's `HytaleBiome` class defines tint colors for each biome. Key surface biomes:

**Zone 1 (Emerald Grove):**
| Biome | Tint (ARGB) | Hex |
|---|---|---|
| Plains | `0xFF5B9E28` | `#5B9E28` |
| Forests | `0xFF4A8A22` | `#4A8A22` |
| Mountains | `0xFF6CA229` | `#6CA229` |
| Swamps | `0xFF5A7A20` | `#5A7A20` |
| Shores | `0xFF7EC850` | `#7EC850` |
| Autumn | `0xFFC87420` | `#C87420` |
| Azure | `0xFF4080C0` | `#4080C0` |

**Zone 2 (Howling Sands):**
| Biome | Tint (ARGB) | Hex |
|---|---|---|
| Deserts | `0xFFBDB76B` | `#BDB76B` |
| Savanna | `0xFFA0A020` | `#A0A020` |
| Oasis | `0xFF60B040` | `#60B040` |
| Scrubland | `0xFF908040` | `#908040` |

**Zone 3 (Borea):**
| Biome | Tint (ARGB) | Hex |
|---|---|---|
| Boreal Forests | `0xFF2A6A3A` | `#2A6A3A` |
| Tundra | `0xFF80B497` | `#80B497` |
| Glacial | `0xFFA0C8D0` | `#A0C8D0` |
| Hedera | `0xFF40A060` | `#40A060` |

**Zone 4 (Devastated Lands):**
| Biome | Tint (ARGB) | Hex |
|---|---|---|
| Dark Forests | `0xFF304020` | `#304020` |
| Jungles | `0xFF1A6030` | `#1A6030` |
| Volcanoes | `0xFF8B2500` | `#8B2500` |
| Wastes | `0xFF505020` | `#505020` |

---

## 5. Key Differences from Minecraft

| Aspect | Minecraft | Hytale |
|---|---|---|
| Grass color source | Temperature/humidity lookup table (biome_colors.png) | Per-column ARGB tint from noise-based TintProvider |
| Water color source | Per-biome hardcoded or datapack | Environment JSON `WaterTint` field |
| Tint storage | Per-biome ID, resolved client-side | Per-column ARGB int stored in chunk data |
| Smoothing | Client-side 4×4 biome blending | Server-side 4-block radius circular blur |
| Grass block types | One type, tinted by biome | One type (`Soil_Grass`), tinted per-column |
| Foliage | Separate foliage color map | Same tint system (blocks with `IsTinted` flag) |

---

## 6. TintGradients Directory (Not Biome-Related)

The `HytaleAssets/Common/TintGradients/` directory contains **cosmetic character/item gradients**, NOT biome tints:
- `Colored_Cotton/`, `Hair/`, `Skin_Tones/`, `Eyes/`, `Faded_Leather/`, etc.
- These are PNG gradient strips used for character customization
- They have no connection to vegetation or terrain tinting

---

## 7. Summary of Color Systems

```
Biome JSON
├── TintProvider (vegetation/grass color)
│   ├── Constant.TintProvider → single hex color
│   └── DensityDelimited.TintProvider → noise-based color selection
│       ├── Density (SimplexNoise2D with seed, scale, octaves)
│       └── Delimiters[] → Range → Constant.TintProvider → Color hex
│
├── EnvironmentProvider (links to Environment JSON)
│   └── Environment JSON
│       └── WaterTint: "#hexcolor" (water color for this environment)
│
└── MaterialProvider (block types — grass is always Soil_Grass)

WorldPainter Export
├── HytaleBiome → tint (ARGB int) + environment name
├── HytaleEnvironmentData → waterTint (hex string)
├── HytaleChunk
│   ├── tints[1024] — int[] per-column vegetation tint
│   ├── environments[1024] — per-column environment
│   └── waterTints[1024] — String[] per-column water tint override
└── HytaleBsonChunkSerializer → IntBytePalette for tints
```
