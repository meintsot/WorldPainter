# Hytale Block Variants Research Report

## Executive Summary

**There is no block variant system in Hytale.** Every named block is a fully standalone, unique block type with its own ID, textures, and properties. What appear to be "variants" (e.g., `Soil_Dirt_Cold`, `Rock_Stone_Mossy`) are actually **completely independent block types** that happen to share a naming convention.

The worldgen/biome system selects which specific block to place using noise functions and `MaterialProvider` rules, but never substitutes or transforms one block into another. Each block occupies its own slot in the block registry.

---

## Evidence

### 1. Decompiled `BlockType.java` — No Variant Fields

The decompiled `BlockType` class (at `decompiled-src/com/hypixel/hytale/server/core/asset/type/blocktype/config/BlockType.java`) defines every property a block can have. Critically:

- **No `variant` field** — there is no field linking one block to another as a "base" or "variant".
- **No `biome` field** — blocks are not aware of what biome they belong to.
- **No `parentBlock` or `baseBlock`** — there is no inheritance chain from a variant back to a base block.
- **Each block has its own `id`** (unique string), its own `textures`, its own `material`, `drawType`, `opacity`, etc.

The code documentation states: *"The definition for a block in the game. Can only be defined within an Item and not standalone."*

### 2. `VariantRotation` — NOT Biome Variants

The `VariantRotation` enum in the source code is about **block rotation**, not biome variation. Its values are:

| Value | Meaning |
|-------|---------|
| `None` | No rotation |
| `Wall` | 2-way horizontal rotation |
| `UpDown` | Up/down flipping |
| `Pipe` | Pipe-style rotation (3 axes) |
| `NESW` | 4-way compass rotation |
| `UpDownNESW` | 4-way + up/down |
| `All` | Full 64-rotation support |

This controls how a block can be **oriented when placed** (like stairs or logs), not how it varies by biome.

### 3. `StateData` — Functional States Only

The `StateData` system maps state names to block keys (e.g., a door's "open" state to `*Door_Oak_Open`). This is for **functional state changes** (open/closed, processing/idle), not biome variation. States generate block keys with the pattern `*ParentKey_StateName`.

### 4. `BiomeTint` — Visual Color Overlay

The `BlockType` class has `biomeTintUp`, `biomeTintDown`, `biomeTintNorth`, etc. integer fields. This is a per-face **color tint index** that the client resolves based on biome. This is analogous to Minecraft's biome-colored grass/foliage — it tints the block visually, but does NOT change the block type. A `Soil_Grass` block is always `Soil_Grass` regardless of biome; only its rendered color changes.

### 5. WorldGen MaterialProvider — Direct Block Selection

Biome generator files (e.g., `Plains1_Oak.json`, `Desert1_Rocky.json`) use `MaterialProvider` to select blocks. They directly reference block names:

```json
{
  "MaterialProvider": {
    "$type": "SpaceAndDepth",
    "Entries": [
      { "Depth": 0, "Solid": "Soil_Grass" },
      { "Depth": 1, "Solid": "Soil_Grass_Sunny" },
      { "Depth": 3, "Solid": "Soil_Dirt" },
      { "Depth": 100, "Solid": "Rock_Marble" }
    ]
  }
}
```

There is no substitution, replacement, or variant resolution happening. The worldgen says "place this exact block here."

### 6. BlockTypeList Files — Curated Brush Lists

The files in `Server/BlockTypeList/` are NOT exhaustive registries. They are **curated lists for the in-game builder tool brushes**. Many blocks (Sand, Mud, Clay, Ashes, Hive, etc.) are absent from these lists but exist as valid blocks used in worldgen and prefabs. The `BlockType.java` field `blockListAssetId` confirms these are brush categorizations.

---

## Block-by-Block Analysis

### Soil/Dirt Blocks — All Standalone

| Block Name | Standalone? | Has Own Texture? | Used in WorldGen? |
|---|---|---|---|
| `Soil_Dirt` | ✅ Yes | ✅ `Soil_Dirt.png` | ✅ Plains, Forest, etc. |
| `Soil_Dirt_Burnt` | ✅ Yes | ✅ `Soil_Dirt_Burnt.png` | ✅ Volcanic biomes |
| `Soil_Dirt_Cold` | ✅ Yes | ✅ `Soil_Dirt_Cold.png` | ✅ Boreal/Taiga biomes |
| `Soil_Dirt_Dry` | ✅ Yes | ✅ `Soil_Dirt_Dry.png` | ✅ Desert biomes |
| `Soil_Dirt_Poisoned` | ✅ Yes | ✅ `Soil_Dirt_Poisonned.png` | ✅ Poisoned areas |

**Conclusion:** Each dirt type is a separate block with its own texture, listed in `Soils.json` BlockTypeList. There is no runtime system that converts `Soil_Dirt` into `Soil_Dirt_Cold` based on biome. The biome generator explicitly picks which one to place.

### Grass Blocks — All Standalone

| Block Name | Standalone? | Has Own Texture? |
|---|---|---|
| `Soil_Grass` | ✅ Yes | ✅ `Soil_Grass_GS.png` (greyscale + BiomeTint) |
| `Soil_Grass_Burnt` | ✅ Yes | ✅ `Soil_Grass_Burnt_Side_GS.png` |
| `Soil_Grass_Cold` | ✅ Yes | ✅ `Soil_Grass_Cold_Side_GS.png` |
| `Soil_Grass_Deep` | ✅ Yes | ✅ `Soil_Grass_Deep.png` |
| `Soil_Grass_Dry` | ✅ Yes | ✅ `Soil_Grass_Dry_Side_GS.png` |
| `Soil_Grass_Full` | ✅ Yes | ✅ `Soil_Grass_Side_Full_GS.png` |
| `Soil_Grass_Sunny` | ✅ Yes | ✅ `Soil_Grass_Sunny.png` |
| `Soil_Grass_Wet` | ✅ Yes | ✅ `Soil_Grass_Wet_GS.png` |

**Note:** Some grass textures use `_GS` (greyscale) suffix, meaning they use `BiomeTint` for color. But they are still distinct block types with different textures (different grass pattern/density), just biome-tinted for color variation.

### Sand Blocks — All Standalone

| Block Name | Has Own Texture? | Found In |
|---|---|---|
| `Soil_Sand` | ✅ `Soil_Sand.png` | WorldGen (`Plains1_Shore.json`) |
| `Soil_Sand_Red` | ✅ `Soil_Sand_Red.png` | WorldGen (Desert biomes) |
| `Soil_Sand_White` | ✅ `Soil_Sand_White.png` | WorldGen (`Desert1_Rocky.json`, `Desert1_Shore.json`, etc.) |
| `Soil_Sand_Ashen` | ✅ `Soil_Sand_Ashen.png` | WorldGen (`Volcanic1_Jungle_*.json`) |

**Note:** Sand blocks are NOT in any `BlockTypeList/*.json` file. They exist as valid blocks used in worldgen and have their own textures and transition textures.

### Mud Blocks — All Standalone

| Block Name | Has Own Texture? | Found In |
|---|---|---|
| `Soil_Mud` | ✅ `Soil_Mud.png` | WorldGen (River biomes, Deeproot, Oasis) |
| `Soil_Mud_Dry` | ✅ `Soil_Mud_Dry.png` | WorldGen (Deeproot, Oasis), Prefabs (Goblin dungeon) |

### Gravel Blocks — All Standalone

| Block Name | Has Own Texture? | In BlockTypeList? |
|---|---|---|
| `Soil_Gravel` | ✅ `Soil_Gravel.png` | ✅ `Gravel.json` |
| `Soil_Gravel_Mossy` | ✅ `Soil_Gravel_Mossy.png` | ✅ `Gravel.json` |
| `Soil_Gravel_Sand` | ✅ `Soil_Gravel_Sand.png` | ✅ `Gravel.json` |
| `Soil_Gravel_Sand_Red` | ✅ `Soil_Gravel_Sand_Red.png` | ✅ `Gravel.json` |
| `Soil_Gravel_Sand_White` | — (no texture found) | ✅ `Gravel.json` |
| `Soil_Pebbles` | ✅ `Soil_Pebbles.png` | ✅ `Gravel.json` |
| `Soil_Pebbles_Frozen` | ✅ `Soil_Pebbles_Cold.png` | ✅ `Gravel.json` |

### Rock/Stone Blocks — All Standalone

| Block Name | Has Own Texture? | In BlockTypeList? |
|---|---|---|
| `Rock_Stone` | ✅ `Rock_Stone.png` (x3 variants) | ✅ `Rock.json` |
| `Rock_Stone_Mossy` | ✅ `Rock_Stone_Mossy.png` | ✅ `Rock.json` |
| `Rock_Shale` | ✅ `Rock_Shale.png` | ✅ `Rock.json` |
| `Rock_Slate` | ✅ `Rock_Slate.png` | ✅ `Rock.json` |
| `Rock_Quartzite` | ✅ `Rock_Quartzite.png` | ✅ `Rock.json` |
| `Rock_Sandstone` | ✅ `Rock_Sandstone_Side.png` | ✅ `Rock.json` |
| `Rock_Sandstone_Red` | ✅ `Rock_Sandstone_Red.png` | ✅ `Rock.json` |
| `Rock_Sandstone_White` | ✅ `Rock_Sandstone_White_Side.png` | ✅ `Rock.json` |
| `Rock_Basalt` | ✅ `Rock_Basalt.png` | ✅ `Rock.json` |
| `Rock_Volcanic` | ✅ `Rock_Volcanic.png` | ✅ `Rock.json` |
| `Rock_Marble` | ✅ `Rock_Marble.png` | ✅ `Rock.json` |
| `Rock_Calcite` | ✅ `Calcite.png` | ✅ `Rock.json` |
| `Rock_Aqua` | ✅ `Rock_Aqua.png` | ✅ `Rock.json` |
| `Rock_Chalk` | ✅ (Chalk texture set) | ✅ `Rock.json` |
| `Rock_Bedrock` | ✅ `Rock_Bedrock.png` | ✅ `Rock.json` |
| `Rock_Salt` | ✅ `Rock_Salt.png` | ✅ `Rock.json` |

**Note:** `Rock_Stone_Mossy` is NOT a biome-conditional version of `Rock_Stone`. It's a separate block with its own `Rock_Stone_Mossy.png` texture. The worldgen system places it independently.

### Ore Blocks — All Standalone

Every ore-in-rock combination is its own block:
- `Ore_Iron_Stone`, `Ore_Iron_Basalt`, `Ore_Iron_Marble`, `Ore_Iron_Shale`, etc.
- ~48 total ore blocks, each with a unique block ID

There is no system where an ore "adapts" to the surrounding rock type. Each combination was pre-defined as a separate block.

---

## Transition Textures — Visual Blending, Not Block Variants

The texture directory contains many `Transition_*.png` files:
- `Transition_Soil_Dirt.png`, `Transition_Soil_Dirt_Burnt.png`, `Transition_Soil_Grass_GS.png`, etc.

These are used by the rendering engine to **visually blend adjacent blocks** of different types. This is controlled by the `TransitionTexture` and `TransitionToGroups` fields in `BlockType.java`. This is a rendering feature, not a block variant system.

---

## Additional Blocks Found in Textures (Not in BlockTypeList files)

These blocks have textures but are NOT in any BlockTypeList file (they exist as blocks but aren't categorized for builder tool brushes):

- **Sand:** `Soil_Sand`, `Soil_Sand_Red`, `Soil_Sand_White`, `Soil_Sand_Ashen`
- **Mud:** `Soil_Mud`, `Soil_Mud_Dry`
- **Clay:** `Soil_Clay`, `Soil_Clay_Ocean`
- **Other Soil:** `Soil_Ashes`, `Soil_Hive`, `Soil_Hive_Corrupted`, `Soil_Needles`, `Soil_Pathway`, `Soil_Roots_Poison`, `Soil_Seaweed_Block`, `Soil_Leaves`
- **Ice:** `Rock_Ice`, `Rock_Ice_Blue`, `Rock_Ice_Cracked`
- **Magma:** `Rock_Magma_Cooled`
- **Crystal:** `Rock_Crystal_Blue/Cyan/Green/Pink/Purple/Red/White/Yellow`
- **Concrete:** `Rock_Concrete`

---

## Implications for WorldPainter

1. **Every block is a first-class citizen.** WorldPainter should treat all blocks as independent types. There's no need to implement any variant resolution logic.

2. **BiomeTint is the only biome-aware feature.** Some blocks (especially grass) use a greyscale texture + `BiomeTint` integer to get biome-colored rendering. WorldPainter may need to handle this for map coloring, but it doesn't affect block identity.

3. **Block registration is flat.** Each block has a unique string ID (e.g., `"Soil_Dirt_Cold"`). The ID is used directly in chunk storage, worldgen, prefabs, and everywhere else. No namespace prefix like Minecraft's `minecraft:` — just the bare name.

4. **BlockTypeList files are incomplete.** They only categorize blocks for the builder tool. Many valid terrain blocks (Sand, Mud, Clay, etc.) are absent. To get a complete block list, you'd need to parse the Item asset store definitions or enumerate all blocks used in worldgen/prefab files.

5. **Ore blocks are pre-combined.** Unlike Minecraft where ores override stone blocks, Hytale has pre-defined ore-in-rock blocks (`Ore_Iron_Stone`, `Ore_Iron_Basalt`). WorldPainter needs to know which rock types pair with which ores.

---

## Summary Table

| Concept | Minecraft Equivalent | Hytale Implementation |
|---|---|---|
| Block variant by biome | Block states / biome-dependent textures | **Does not exist.** Each variant is its own block type. |
| Biome coloring (grass) | Biome color maps | `BiomeTint` integer field per face |
| Block states (door open/closed) | Block states | `StateData` with state-to-block mapping |
| Block rotation | Block states (facing, axis) | `VariantRotation` enum + rotation index in chunk data |
| Ore in different rock | Stone variants + ore overlay | Pre-combined blocks (`Ore_Iron_Stone`, `Ore_Iron_Basalt`) |
| Visual block blending | None (hard edges) | `TransitionTexture` + `TransitionToGroups` |
