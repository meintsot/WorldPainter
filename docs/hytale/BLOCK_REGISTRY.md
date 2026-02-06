# Hytale Complete Block Type Registry

Compiled from all HytaleAssets sources. **Total unique block types: 700+**

---

## Source Files

| Source File | Description |
|---|---|
| `HytaleAssets/Server/BlockTypeList/Empty.json` | Air/empty block |
| `HytaleAssets/Server/BlockTypeList/Soils.json` | Soil & grass blocks |
| `HytaleAssets/Server/BlockTypeList/Gravel.json` | Gravel & pebble blocks |
| `HytaleAssets/Server/BlockTypeList/Snow.json` | Snow blocks |
| `HytaleAssets/Server/BlockTypeList/Rock.json` | Base rock types |
| `HytaleAssets/Server/BlockTypeList/Ores.json` | Ore variants (per host rock) |
| `HytaleAssets/Server/BlockTypeList/TreeWood.json` | Tree trunk/branch blocks |
| `HytaleAssets/Server/BlockTypeList/TreeLeaves.json` | Tree leaf blocks |
| `HytaleAssets/Server/BlockTypeList/TreeWoodAndLeaves.json` | Combined wood+leaves list |
| `HytaleAssets/Server/BlockTypeList/PlantsAndTrees.json` | Master plant/tree list (606 entries) |
| `HytaleAssets/Server/BlockTypeList/PlantScatter.json` | Scatter-placed plants |
| `HytaleAssets/Server/BlockTypeList/AllScatter.json` | All scatter blocks (plants + rubble + deco) |
| `HytaleAssets/Common/BlockTextures/` | Block texture PNGs (indicates block existence) |
| `HytaleAssets/Common/Blocks/` | Block models (.blockymodel) and animations |
| `HytaleAssets/Server/World/Default/Zones/Layers/` | World layer configs (block stacking order) |
| `HytaleAssets/Server/HytaleGenerator/Biomes/` | Biome configs (block references) |
| `docs/hytale/02-BLOCKS.md` | Block system documentation |

---

## 1. TECHNICAL / SPECIAL (ID 0-3)

| Block ID | Source |
|---|---|
| `Empty` | `BlockTypeList/Empty.json` (Air, ID 0) |
| `Unknown` | Built-in (ID 1) |
| `Debug_Cube` | Built-in (ID 2) |
| `Debug_Model` | Built-in (ID 3) |
| `Block_PrefabSpawner` | `docs/hytale/02-BLOCKS.md` |

---

## 2. SOILS & EARTH

### Dirt
Source: `BlockTypeList/Soils.json`

| Block ID |
|---|
| `Soil_Dirt` |
| `Soil_Dirt_Burnt` |
| `Soil_Dirt_Cold` |
| `Soil_Dirt_Dry` |
| `Soil_Dirt_Poisoned` |

### Grass (on Dirt)
Source: `BlockTypeList/Soils.json`

| Block ID |
|---|
| `Soil_Grass` |
| `Soil_Grass_Burnt` |
| `Soil_Grass_Cold` |
| `Soil_Grass_Deep` |
| `Soil_Grass_Dry` |
| `Soil_Grass_Full` |
| `Soil_Grass_Sunny` |
| `Soil_Grass_Wet` |

### Snow
Source: `BlockTypeList/Snow.json`

| Block ID |
|---|
| `Soil_Snow` |
| `Soil_Snow_Half` |

### Gravel & Pebbles
Source: `BlockTypeList/Gravel.json`

| Block ID |
|---|
| `Soil_Gravel` |
| `Soil_Gravel_Mossy` |
| `Soil_Gravel_Sand` |
| `Soil_Gravel_Sand_Red` |
| `Soil_Gravel_Sand_White` |
| `Soil_Pebbles` |
| `Soil_Pebbles_Frozen` |

### Sand
Source: `HytaleGenerator/Biomes/`, `BlockTextures/`

| Block ID |
|---|
| `Soil_Sand` |
| `Soil_Sand_Ashen` |
| `Soil_Sand_Red` |
| `Soil_Sand_White` |

### Mud & Misc Soils
Source: `HytaleGenerator/Biomes/`, `BlockTextures/`

| Block ID |
|---|
| `Soil_Mud` |
| `Soil_Mud_Dry` |
| `Soil_Ash` |
| `Soil_Clay` |
| `Soil_Leaves` |
| `Soil_Needles` |
| `Soil_Pathway` |
| `Soil_Seaweed_Block` |

### Tilled/Farming Soil
Source: `BlockTextures/`

| Block ID |
|---|
| `Soil_Dirt_Tilled` |
| `Soil_Dirt_Tilled_Fertilized` |
| `Soil_Dirt_Tilled_Watered` |
| `Soil_Dirt_Tilled_Watered_Fertilized` |

### Hive Blocks
Source: `BlockTextures/`

| Block ID |
|---|
| `Soil_Hive` |
| `Soil_Hive_Brick` |
| `Soil_Hive_Brick_Smooth` |
| `Soil_Hive_Corrupted` |
| `Soil_Hive_Corrupted_Brick` |
| `Soil_Hive_Corrupted_Brick_Smooth` |

---

## 3. ROCK & STONE

### Base Rock Types
Source: `BlockTypeList/Rock.json`

| Block ID |
|---|
| `Rock_Stone` |
| `Rock_Stone_Mossy` |
| `Rock_Shale` |
| `Rock_Slate` |
| `Rock_Quartzite` |
| `Rock_Sandstone` |
| `Rock_Sandstone_Red` |
| `Rock_Sandstone_White` |
| `Rock_Basalt` |
| `Rock_Volcanic` |
| `Rock_Marble` |
| `Rock_Calcite` |
| `Rock_Aqua` |
| `Rock_Chalk` |
| `Rock_Bedrock` |
| `Rock_Salt` |

### Additional Rock (from Biomes/Textures)
Source: `HytaleGenerator/Biomes/`, `BlockTextures/`

| Block ID |
|---|
| `Rock_Ice` |
| `Rock_Ice_Permafrost` |
| `Rock_Ice_Blue` |
| `Rock_Ice_Cracked` |
| `Rock_Magma_Cooled` |
| `Rock_Stone_Cobble` |
| `Rock_Stone_Poisoned` |
| `Rock_Crystal_Red` |
| `Rock_Concrete` |
| `Rock_Ledgestone` |
| `Rock_Limestone` |

### Stone/Rock Brick Variants
Source: `BlockTextures/`

| Block ID |
|---|
| `Rock_Stone_Brick` |
| `Rock_Stone_Brick_Decorative` |
| `Rock_Stone_Brick_Mossy` |
| `Rock_Stone_Brick_Ornate` |
| `Rock_Stone_Brick_Smooth` |
| `Rock_Stone_Cobble_Mossy` |
| `Rock_Basalt_Brick` |
| `Rock_Basalt_Brick_Decorative` |
| `Rock_Basalt_Brick_Ornate` |
| `Rock_Basalt_Brick_Smooth` |
| `Rock_Basalt_Cobblestone` |
| `Rock_Marble_Brick_Decorative` |
| `Rock_Marble_Brick_Ornate` |
| `Rock_Marble_Brick_Side` |
| `Rock_Marble_Cobble` |
| `Rock_Quartzite_Brick` |
| `Rock_Quartzite_Brick_Decorative` |
| `Rock_Quartzite_Brick_Ornate` |
| `Rock_Quartzite_Brick_Smooth` |
| `Rock_Quartzite_Cobble` |
| `Rock_Quartzite_Rune_Fennec` |
| `Rock_Quartzite_Rune_Human` |
| `Rock_Quartzite_Rune_Turtle` |
| `Rock_Quartzite_Rune_Kweebec` |
| `Rock_Shale_Brick` |
| `Rock_Shale_Brick_Decorative` |
| `Rock_Shale_Brick_Ornate` |
| `Rock_Shale_Brick_Smooth` |
| `Rock_Shale_Cobble` |
| `Rock_Slate_Cobble` |
| `Rock_Slate_Cracked` |
| `Rock_Sandstone_Brick` |
| `Rock_Sandstone_Brick_Smooth` |
| `Rock_Sandstone_Cobble` |
| `Rock_Sandstone_Decorative` |
| `Rock_Sandstone_Red_Brick` |
| `Rock_Sandstone_Red_Brick_Ornate` |
| `Rock_Sandstone_Red_Brick_Smooth` |
| `Rock_Sandstone_Red_Cobblestone` |
| `Rock_Sandstone_Red_Decorative` |
| `Rock_Sandstone_White_Brick` |
| `Rock_Sandstone_White_Cobble` |
| `Rock_Volcanic_Brick` |
| `Rock_Volcanic_Brick_Decorative` |
| `Rock_Volcanic_Brick_Ornate` |
| `Rock_Volcanic_Brick_Smooth` |
| `Rock_Volcanic_Cobble` |
| `Rock_Volcanic_LavaCracks` |
| `Rock_Aqua_Brick_Ornate` |
| `Rock_Aqua_Brick_Side` |
| `Rock_Aqua_Cobble` |
| `Rock_Aqua_Decorative` |
| `Rock_Chalk_Brick` |
| `Rock_Ledgestone_Brick` |
| `Rock_Ledgestone_Brick_Decorative` |
| `Rock_Ledgestone_Brick_Ornate` |
| `Rock_Ledgestone_Cobble` |
| `Rock_Limestone_Brick` |
| `Rock_Limestone_Brick_Decorative` |
| `Rock_Limestone_Brick_Ornate` |
| `Rock_Limestone_Cobble` |
| `Rock_Gold_Brick` |
| `Rock_Gold_Decorative` |
| `Rock_Gold_Ornate` |
| `Rock_Concrete_Ornate` |

### Crystal Blocks
Source: `BlockTextures/`

| Block ID |
|---|
| `Rock_Crystal_Blue` |
| `Rock_Crystal_Cyan` |
| `Rock_Crystal_Green` |
| `Rock_Crystal_Pink` |
| `Rock_Crystal_Purple` |
| `Rock_Crystal_Red` |
| `Rock_Crystal_White` |
| `Rock_Crystal_Yellow` |

### Runic Blocks
Source: `BlockTextures/`

| Block ID |
|---|
| `Rock_Runic_Blue_Brick` |
| `Rock_Runic_Blue_Brick_O` |
| `Rock_Runic_Cobble` |
| `Runic_Brick` |
| `Runic_Brick_Dark` |
| `Runic_Brick_DarkBlue` |
| `Runic_Brick_Ornate` |
| `Runic_Brick_Ornate_Dark` |

### Calcite Brick Variants
Source: `BlockTextures/`

| Block ID |
|---|
| `Calcite` |
| `Calcite_Brick_Decorative` |
| `Calcite_Brick_Ornate` |
| `Calcite_Brick_Side` |
| `Calcite_Brick_Smooth` |
| `Calcite_Cobble` |
| `Chalk` |

### Peachstone Variants
Source: `BlockTextures/`

| Block ID |
|---|
| `Peachstone_Brick` |
| `Peachstone_Cobble` |

### Light Stone Runes
Source: `BlockTextures/`

| Block ID |
|---|
| `LightStoneAlgiz` |
| `LightStoneRuneDagaz` |
| `LightStoneRuneFeho` |
| `LightStoneRuneGebo` |

---

## 4. ORES (48 total)
Source: `BlockTypeList/Ores.json`

Format: `Ore_{Metal}_{HostRock}`

### Copper Ore
| Block ID |
|---|
| `Ore_Copper_Basalt` |
| `Ore_Copper_Sandstone` |
| `Ore_Copper_Shale` |
| `Ore_Copper_Stone` |
| `Ore_Copper_Volcanic` |

### Iron Ore
| Block ID |
|---|
| `Ore_Iron_Basalt` |
| `Ore_Iron_Sandstone` |
| `Ore_Iron_Shale` |
| `Ore_Iron_Slate` |
| `Ore_Iron_Stone` |
| `Ore_Iron_Volcanic` |

### Gold Ore
| Block ID |
|---|
| `Ore_Gold_Basalt` |
| `Ore_Gold_Sandstone` |
| `Ore_Gold_Shale` |
| `Ore_Gold_Stone` |
| `Ore_Gold_Volcanic` |

### Silver Ore
| Block ID |
|---|
| `Ore_Silver_Basalt` |
| `Ore_Silver_Sandstone` |
| `Ore_Silver_Shale` |
| `Ore_Silver_Slate` |
| `Ore_Silver_Stone` |
| `Ore_Silver_Volcanic` |

### Cobalt Ore
| Block ID |
|---|
| `Ore_Cobalt_Basalt` |
| `Ore_Cobalt_Sandstone` |
| `Ore_Cobalt_Shale` |
| `Ore_Cobalt_Slate` |
| `Ore_Cobalt_Stone` |
| `Ore_Cobalt_Volcanic` |

### Mithril Ore
| Block ID |
|---|
| `Ore_Mithril_Basalt` |
| `Ore_Mithril_Magma` |
| `Ore_Mithril_Slate` |
| `Ore_Mithril_Stone` |
| `Ore_Mithril_Volcanic` |

### Adamantite Ore
| Block ID |
|---|
| `Ore_Adamantite_Basalt` |
| `Ore_Adamantite_Shale` |
| `Ore_Adamantite_Slate` |
| `Ore_Adamantite_Stone` |
| `Ore_Adamantite_Volcanic` |

### Onyxium Ore
| Block ID |
|---|
| `Ore_Onyxium_Basalt` |
| `Ore_Onyxium_Sandstone` |
| `Ore_Onyxium_Shale` |
| `Ore_Onyxium_Stone` |
| `Ore_Onyxium_Volcanic` |

### Thorium Ore
| Block ID |
|---|
| `Ore_Thorium_Basalt` |
| `Ore_Thorium_Sandstone` |
| `Ore_Thorium_Shale` |
| `Ore_Thorium_Stone` |
| `Ore_Thorium_Volcanic` |

---

## 5. TREE WOOD (Trunks, Branches, Roots)
Source: `BlockTypeList/TreeWood.json`, `BlockTypeList/PlantsAndTrees.json`

### Tree Species (30 species)
Each species has: `_Trunk`, `_Trunk_Full`, `_Roots`, `_Branch_Short`, `_Branch_Long`, `_Branch_Corner`

| Species Prefix | Trunk | Full Trunk | Roots | Branch Short | Branch Long | Branch Corner |
|---|---|---|---|---|---|---|
| `Wood_Amber` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `Wood_Ash` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `Wood_Aspen` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `Wood_Azure` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `Wood_Bamboo` | ✓ | — | — | — | ✓ | — |
| `Wood_Banyan` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `Wood_Beech` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `Wood_Birch` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `Wood_Bottletree` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `Wood_Burnt` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `Wood_Camphor` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `Wood_Cedar` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `Wood_Crystal` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `Wood_Dry` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `Wood_Fig_Blue` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `Wood_Fir` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `Wood_Fire` | ✓ | ✓ | — | ✓ | ✓ | ✓ |
| `Wood_Gnarled` | — | — | ✓ | — | — | — |
| `Wood_Gumboab` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `Wood_Ice` | ✓ | — | — | — | — | — |
| `Wood_Jungle` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `Wood_Maple` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `Wood_Oak` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `Wood_Palm` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `Wood_Palo` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `Wood_Petrified` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `Wood_Poisoned` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `Wood_Redwood` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `Wood_Sallow` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `Wood_Spiral` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `Wood_Stormbark` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `Wood_Windwillow` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `Wood_Wisteria_Wild` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |

### Additional Wood Blocks
Source: `HytaleGenerator/Biomes/`, `BlockTextures/`

| Block ID |
|---|
| `Wood_Sticks` |

---

## 6. WOOD PLANKS & CONSTRUCTION
Source: `BlockTextures/`

| Block ID |
|---|
| `Wood_Blackwood_Planks` |
| `Wood_Blackwood_Decorative` |
| `Wood_Blackwood_Ornate` |
| `Wood_Darkwood_Planks` |
| `Wood_Darkwood_Decorative` |
| `Wood_Darkwood_Ornate` |
| `Wood_Deadwood_Planks` |
| `Wood_Deadwood_Decorative` |
| `Wood_Deadwood_Ornate` |
| `Wood_Drywood_Planks` |
| `Wood_Drywood_Decorative` |
| `Wood_Drywood_Ornate` |
| `Wood_Goldenwood_Planks` |
| `Wood_Goldenwood_Decorative` |
| `Wood_Goldenwood_Ornate` |
| `Wood_Green` |
| `Wood_Greenwood_Decorative` |
| `Wood_Greenwood_Ornate` |
| `Wood_Hardwood_Planks` |
| `Wood_Hardwood_Decorative` |
| `Wood_Hardwood_Ornate` |
| `Wood_Lightwood_Planks` |
| `Wood_Lightwood_Decorative` |
| `Wood_Lightwood_Ornate` |
| `Wood_Redwood_Planks` |
| `Wood_Redwood_Decorative` |
| `Wood_Redwood_Ornate` |
| `Wood_Softwood_Planks` |
| `Wood_Softwood_Decorative` |
| `Wood_Softwood_Ornate` |
| `Wood_Tropicalwood_Planks` |
| `Wood_Tropicalwood_Decorative` |
| `Wood_Tropicalwood_Ornate` |

### Village Wall Blocks
Source: `BlockTextures/`

| Block ID |
|---|
| `Wood_Village_Wall_Blue` |
| `Wood_Village_Wall_Cyan` |
| `Wood_Village_Wall_Green` |
| `Wood_Village_Wall_Grey` |
| `Wood_Village_Wall_Lime` |
| `Wood_Village_Wall_Red` |
| `Wood_Village_Wall_White` |
| `Wood_Village_Wall_Yellow` |

---

## 7. TREE LEAVES (44 types)
Source: `BlockTypeList/TreeLeaves.json`

| Block ID |
|---|
| `Plant_Leaves_Amber` |
| `Plant_Leaves_Ash` |
| `Plant_Leaves_Aspen` |
| `Plant_Leaves_Autumn` |
| `Plant_Leaves_Autumn_Floor` |
| `Plant_Leaves_Azure` |
| `Plant_Leaves_Bamboo` |
| `Plant_Leaves_Banyan` |
| `Plant_Leaves_Beech` |
| `Plant_Leaves_Birch` |
| `Plant_Leaves_Bottle` |
| `Plant_Leaves_Bramble` |
| `Plant_Leaves_Burnt` |
| `Plant_Leaves_Camphor` |
| `Plant_Leaves_Cedar` |
| `Plant_Leaves_Crystal` |
| `Plant_Leaves_Dead` |
| `Plant_Leaves_Dry` |
| `Plant_Leaves_Fig_Blue` |
| `Plant_Leaves_Fir` |
| `Plant_Leaves_Fir_Red` |
| `Plant_Leaves_Fir_Snow` |
| `Plant_Leaves_Fire` |
| `Plant_Leaves_Goldentree` |
| `Plant_Leaves_Gumboab` |
| `Plant_Leaves_Jungle` |
| `Plant_Leaves_Jungle_Floor` |
| `Plant_Leaves_Maple` |
| `Plant_Leaves_Oak` |
| `Plant_Leaves_Palm` |
| `Plant_Leaves_Palm_Arid` |
| `Plant_Leaves_Palm_Oasis` |
| `Plant_Leaves_Palo` |
| `Plant_Leaves_Petrified` |
| `Plant_Leaves_Poisoned` |
| `Plant_Leaves_Poisoned_Floor` |
| `Plant_Leaves_Redwood` |
| `Plant_Leaves_Rhododendron` |
| `Plant_Leaves_Sallow` |
| `Plant_Leaves_Snow` |
| `Plant_Leaves_Spiral` |
| `Plant_Leaves_Stormbark` |
| `Plant_Leaves_Windwillow` |
| `Plant_Leaves_Wisteria_Wild` |

---

## 8. GRASS (Plant Scatter)
Source: `BlockTypeList/PlantScatter.json`, `BlockTypeList/PlantsAndTrees.json`

| Block ID |
|---|
| `Plant_Grass_Arid` |
| `Plant_Grass_Arid_Short` |
| `Plant_Grass_Arid_Tall` |
| `Plant_Grass_Cave_Short` |
| `Plant_Grass_Dry` |
| `Plant_Grass_Dry_Tall` |
| `Plant_Grass_Gnarled` |
| `Plant_Grass_Gnarled_Short` |
| `Plant_Grass_Gnarled_Tall` |
| `Plant_Grass_Jungle` |
| `Plant_Grass_Jungle_Short` |
| `Plant_Grass_Jungle_Tall` |
| `Plant_Grass_Lush` |
| `Plant_Grass_Lush_Short` |
| `Plant_Grass_Lush_Tall` |
| `Plant_Grass_Poisoned` |
| `Plant_Grass_Poisoned_Short` |
| `Plant_Grass_Rocky` |
| `Plant_Grass_Rocky_Short` |
| `Plant_Grass_Rocky_Tall` |
| `Plant_Grass_Sharp` |
| `Plant_Grass_Sharp_Overgrown` |
| `Plant_Grass_Sharp_Short` |
| `Plant_Grass_Sharp_Tall` |
| `Plant_Grass_Sharp_Wild` |
| `Plant_Grass_Snowy` |
| `Plant_Grass_Snowy_Short` |
| `Plant_Grass_Snowy_Tall` |
| `Plant_Grass_Wet` |
| `Plant_Grass_Wet_Overgrown` |
| `Plant_Grass_Wet_Short` |
| `Plant_Grass_Wet_Tall` |
| `Plant_Grass_Wet_Wild` |
| `Plant_Grass_Winter` |
| `Plant_Grass_Winter_Short` |
| `Plant_Grass_Winter_Tall` |

---

## 9. FLOWERS
Source: `BlockTypeList/PlantScatter.json`, `BlockTypeList/PlantsAndTrees.json`

### Common Flowers (24 colors × 2 variants)
| Block ID |
|---|
| `Plant_Flower_Common_Blue` / `Plant_Flower_Common_Blue2` |
| `Plant_Flower_Common_Cyan` / `Plant_Flower_Common_Cyan2` |
| `Plant_Flower_Common_Grey` / `Plant_Flower_Common_Grey2` |
| `Plant_Flower_Common_Lime` / `Plant_Flower_Common_Lime2` |
| `Plant_Flower_Common_Orange` / `Plant_Flower_Common_Orange2` |
| `Plant_Flower_Common_Pink` / `Plant_Flower_Common_Pink2` |
| `Plant_Flower_Common_Poisoned` / `Plant_Flower_Common_Poisoned2` |
| `Plant_Flower_Common_Purple` / `Plant_Flower_Common_Purple2` |
| `Plant_Flower_Common_Red` / `Plant_Flower_Common_Red2` |
| `Plant_Flower_Common_Violet` / `Plant_Flower_Common_Violet2` |
| `Plant_Flower_Common_White` / `Plant_Flower_Common_White2` |
| `Plant_Flower_Common_Yellow` / `Plant_Flower_Common_Yellow2` |

### Bushy Flowers (11)
| Block ID |
|---|
| `Plant_Flower_Bushy_Blue` |
| `Plant_Flower_Bushy_Cyan` |
| `Plant_Flower_Bushy_Green` |
| `Plant_Flower_Bushy_Grey` |
| `Plant_Flower_Bushy_Orange` |
| `Plant_Flower_Bushy_Poisoned` |
| `Plant_Flower_Bushy_Purple` |
| `Plant_Flower_Bushy_Red` |
| `Plant_Flower_Bushy_Violet` |
| `Plant_Flower_Bushy_White` |
| `Plant_Flower_Bushy_Yellow` |

### Tall Flowers (8)
| Block ID |
|---|
| `Plant_Flower_Tall_Blue` |
| `Plant_Flower_Tall_Cyan` |
| `Plant_Flower_Tall_Cyan2` |
| `Plant_Flower_Tall_Pink` |
| `Plant_Flower_Tall_Purple` |
| `Plant_Flower_Tall_Red` |
| `Plant_Flower_Tall_Violet` |
| `Plant_Flower_Tall_Yellow` |

### Orchid Flowers (9)
| Block ID |
|---|
| `Plant_Flower_Orchid_Blue` |
| `Plant_Flower_Orchid_Cyan` |
| `Plant_Flower_Orchid_Orange` |
| `Plant_Flower_Orchid_Pink` |
| `Plant_Flower_Orchid_Poisoned` |
| `Plant_Flower_Orchid_Purple` |
| `Plant_Flower_Orchid_Red` |
| `Plant_Flower_Orchid_White` |
| `Plant_Flower_Orchid_Yellow` |

### Flax Flowers (6)
| Block ID |
|---|
| `Plant_Flower_Flax_Blue` |
| `Plant_Flower_Flax_Orange` |
| `Plant_Flower_Flax_Pink` |
| `Plant_Flower_Flax_Purple` |
| `Plant_Flower_Flax_White` |
| `Plant_Flower_Flax_Yellow` |

### Water Flowers (6)
| Block ID |
|---|
| `Plant_Flower_Water_Blue` |
| `Plant_Flower_Water_Duckweed` |
| `Plant_Flower_Water_Green` |
| `Plant_Flower_Water_Purple` |
| `Plant_Flower_Water_Red` |
| `Plant_Flower_Water_White` |

### Special Flowers
| Block ID |
|---|
| `Plant_Flower_Hemlock` |
| `Plant_Flower_Poisoned_Orange` |
| `Plant_Sunflower_Stage_0` |
| `Plant_Lavender_Stage_0` |

---

## 10. FERNS
Source: `BlockTypeList/PlantsAndTrees.json`

| Block ID |
|---|
| `Plant_Fern` |
| `Plant_Fern_Arid` |
| `Plant_Fern_Forest` |
| `Plant_Fern_Jungle` |
| `Plant_Fern_Jungle_Trunk` |
| `Plant_Fern_Tall` |
| `Plant_Fern_Wet_Big` |
| `Plant_Fern_Wet_Giant` |
| `Plant_Fern_Wet_Giant_Trunk` |
| `Plant_Fern_Winter` |

---

## 11. BUSHES & BRAMBLES
Source: `BlockTypeList/PlantsAndTrees.json`

### Bushes
| Block ID |
|---|
| `Plant_Bush` |
| `Plant_Bush_Arid` |
| `Plant_Bush_Arid_Palm` |
| `Plant_Bush_Arid_Red` |
| `Plant_Bush_Arid_Sharp` |
| `Plant_Bush_Bramble` |
| `Plant_Bush_Crystal` |
| `Plant_Bush_Dead` |
| `Plant_Bush_Dead_Hanging` |
| `Plant_Bush_Dead_Tall` |
| `Plant_Bush_Dead_Twisted` |
| `Plant_Bush_Green` |
| `Plant_Bush_Hanging` |
| `Plant_Bush_Jungle` |
| `Plant_Bush_Lush` |
| `Plant_Bush_Wet` |
| `Plant_Bush_Winter` |
| `Plant_Bush_Winter_Red` |
| `Plant_Bush_Winter_Sharp` |
| `Plant_Bush_Winter_Snow` |

### Brambles
| Block ID |
|---|
| `Plant_Bramble_Dead_Lavathorn` |
| `Plant_Bramble_Dead_Twisted` |
| `Plant_Bramble_Dry_Magic` |
| `Plant_Bramble_Dry_Sandthorn` |
| `Plant_Bramble_Dry_Twisted` |
| `Plant_Bramble_Moss_Twisted` |
| `Plant_Bramble_Winter` |

---

## 12. CACTI
Source: `BlockTypeList/PlantsAndTrees.json`

| Block ID |
|---|
| `Plant_Cactus_1` |
| `Plant_Cactus_2` |
| `Plant_Cactus_3` |
| `Plant_Cactus_Ball_1` |
| `Plant_Cactus_Flat_1` |
| `Plant_Cactus_Flat_2` |
| `Plant_Cactus_Flat_3` |
| `Plant_Cactus_Flower` |

---

## 13. CROPS & FARMING
Source: `BlockTypeList/PlantsAndTrees.json`

| Block ID |
|---|
| `Plant_Crop_Aubergine` |
| `Plant_Crop_Aubergine_Block` |
| `Plant_Crop_Berry_Block` |
| `Plant_Crop_Berry_Wet_Block` |
| `Plant_Crop_Berry_Winter_Block` |
| `Plant_Crop_Carrot_Block` |
| `Plant_Crop_Cauliflower_Block` |
| `Plant_Crop_Chilli_Block` |
| `Plant_Crop_Corn_Block` |
| `Plant_Crop_Cotton_Block` |
| `Plant_Crop_Health1` |
| `Plant_Crop_Health1_Block` |
| `Plant_Crop_Health2` |
| `Plant_Crop_Health2_Block` |
| `Plant_Crop_Health3` |
| `Plant_Crop_Health3_Block` |
| `Plant_Crop_Lettuce_Block` |
| `Plant_Crop_Mana1` |
| `Plant_Crop_Mana1_Block` |
| `Plant_Crop_Mana2` |
| `Plant_Crop_Mana2_Block` |
| `Plant_Crop_Mana3` |
| `Plant_Crop_Mana3_Block` |
| `Plant_Crop_Onion_Block` |
| `Plant_Crop_Potato_Block` |
| `Plant_Crop_Potato_Sweet_Block` |
| `Plant_Crop_Pumpkin_Block` |
| `Plant_Crop_Rice_Block` |
| `Plant_Crop_Stamina1` |
| `Plant_Crop_Stamina1_Block` |
| `Plant_Crop_Stamina2` |
| `Plant_Crop_Stamina2_Block` |
| `Plant_Crop_Stamina3` |
| `Plant_Crop_Stamina3_Block` |
| `Plant_Crop_Tomato_Block` |
| `Plant_Crop_Turnip_Block` |
| `Plant_Crop_Wheat_Block` |
| `Plant_Crop_Wheat_Stage_4_Burnt` |

---

## 14. MUSHROOMS
Source: `BlockTypeList/PlantsAndTrees.json`

### Small/Model Mushrooms
| Block ID |
|---|
| `Plant_Crop_Mushroom_Block` |
| `Plant_Crop_Mushroom_Boomshroom_Large` |
| `Plant_Crop_Mushroom_Boomshroom_Small` |
| `Plant_Crop_Mushroom_Cap_Brown` |
| `Plant_Crop_Mushroom_Cap_Green` |
| `Plant_Crop_Mushroom_Cap_Poison` |
| `Plant_Crop_Mushroom_Cap_Red` |
| `Plant_Crop_Mushroom_Cap_White` |
| `Plant_Crop_Mushroom_Common_Blue` |
| `Plant_Crop_Mushroom_Common_Brown` |
| `Plant_Crop_Mushroom_Common_Lime` |
| `Plant_Crop_Mushroom_Flatcap_Blue` |
| `Plant_Crop_Mushroom_Flatcap_Green` |
| `Plant_Crop_Mushroom_Shelve_Brown` |
| `Plant_Crop_Mushroom_Shelve_Green` |
| `Plant_Crop_Mushroom_Shelve_Yellow` |

### Glowing Mushrooms
| Block ID |
|---|
| `Plant_Crop_Mushroom_Glowing_Blue` |
| `Plant_Crop_Mushroom_Glowing_Green` |
| `Plant_Crop_Mushroom_Glowing_Orange` |
| `Plant_Crop_Mushroom_Glowing_Purple` |
| `Plant_Crop_Mushroom_Glowing_Red` |
| `Plant_Crop_Mushroom_Glowing_Violet` |

### Giant Mushroom Blocks (Cube blocks) — 7 colors × (Block + Trunk + Mycelium + Branch)
| Block ID |
|---|
| `Plant_Crop_Mushroom_Block_Blue` |
| `Plant_Crop_Mushroom_Block_Blue_Trunk` |
| `Plant_Crop_Mushroom_Block_Blue_Mycelium` |
| `Plant_Crop_Mushroom_Block_Blue_Branch` |
| `Plant_Crop_Mushroom_Block_Brown` |
| `Plant_Crop_Mushroom_Block_Brown_Trunk` |
| `Plant_Crop_Mushroom_Block_Brown_Mycelium` |
| `Plant_Crop_Mushroom_Block_Brown_Branch` |
| `Plant_Crop_Mushroom_Block_Green` |
| `Plant_Crop_Mushroom_Block_Green_Trunk` |
| `Plant_Crop_Mushroom_Block_Green_Mycelium` |
| `Plant_Crop_Mushroom_Block_Green_Branch` |
| `Plant_Crop_Mushroom_Block_Purple` |
| `Plant_Crop_Mushroom_Block_Purple_Trunk` |
| `Plant_Crop_Mushroom_Block_Purple_Mycelium` |
| `Plant_Crop_Mushroom_Block_Purple_Branch` |
| `Plant_Crop_Mushroom_Block_Red` |
| `Plant_Crop_Mushroom_Block_Red_Trunk` |
| `Plant_Crop_Mushroom_Block_Red_Mycelium` |
| `Plant_Crop_Mushroom_Block_Red_Branch` |
| `Plant_Crop_Mushroom_Block_White` |
| `Plant_Crop_Mushroom_Block_White_Trunk` |
| `Plant_Crop_Mushroom_Block_White_Mycelium` |
| `Plant_Crop_Mushroom_Block_White_Branch` |
| `Plant_Crop_Mushroom_Block_Yellow` |
| `Plant_Crop_Mushroom_Block_Yellow_Trunk` |
| `Plant_Crop_Mushroom_Block_Yellow_Mycelium` |
| `Plant_Crop_Mushroom_Block_Yellow_Branch` |

---

## 15. CORAL (Underwater)
Source: `BlockTypeList/PlantsAndTrees.json`

### Coral Blocks (solid cubes, 13 colors)
| Block ID |
|---|
| `Plant_Coral_Block_Blue` |
| `Plant_Coral_Block_Cyan` |
| `Plant_Coral_Block_Green` |
| `Plant_Coral_Block_Grey` |
| `Plant_Coral_Block_Lime` |
| `Plant_Coral_Block_Orange` |
| `Plant_Coral_Block_Pink` |
| `Plant_Coral_Block_Poison` |
| `Plant_Coral_Block_Purple` |
| `Plant_Coral_Block_Red` |
| `Plant_Coral_Block_Violet` |
| `Plant_Coral_Block_White` |
| `Plant_Coral_Block_Yellow` |

### Coral Bushes (models, 13 colors)
| Block ID |
|---|
| `Plant_Coral_Bush_Blue` |
| `Plant_Coral_Bush_Cyan` |
| `Plant_Coral_Bush_Green` |
| `Plant_Coral_Bush_Grey` |
| `Plant_Coral_Bush_Lime` |
| `Plant_Coral_Bush_Orange` |
| `Plant_Coral_Bush_Pink` |
| `Plant_Coral_Bush_Poisoned` |
| `Plant_Coral_Bush_Purple` |
| `Plant_Coral_Bush_Red` |
| `Plant_Coral_Bush_Violet` |
| `Plant_Coral_Bush_White` |
| `Plant_Coral_Bush_Yellow` |

### Coral Models (decorative, 12 colors)
| Block ID |
|---|
| `Plant_Coral_Model_Blue` |
| `Plant_Coral_Model_Cyan` |
| `Plant_Coral_Model_Green` |
| `Plant_Coral_Model_Grey` |
| `Plant_Coral_Model_Lime` |
| `Plant_Coral_Model_Orange` |
| `Plant_Coral_Model_Pink` |
| `Plant_Coral_Model_Purple` |
| `Plant_Coral_Model_Red` |
| `Plant_Coral_Model_Violet` |
| `Plant_Coral_Model_White` |
| `Plant_Coral_Model_Yellow` |

---

## 16. MOSS & VINES
Source: `BlockTypeList/PlantsAndTrees.json`

### Moss Blocks (5 colors × 5 forms)
| Block ID |
|---|
| `Plant_Moss_Block_Blue` |
| `Plant_Moss_Block_Green` |
| `Plant_Moss_Block_Green_Dark` |
| `Plant_Moss_Block_Red` |
| `Plant_Moss_Block_Yellow` |
| `Plant_Moss_Cave_Blue` |
| `Plant_Moss_Cave_Green` |
| `Plant_Moss_Cave_Green_Dark` |
| `Plant_Moss_Cave_Red` |
| `Plant_Moss_Cave_Yellow` |
| `Plant_Moss_Rug_Blue` |
| `Plant_Moss_Rug_Green` |
| `Plant_Moss_Rug_Green_Dark` |
| `Plant_Moss_Rug_Lime` |
| `Plant_Moss_Rug_Pink` |
| `Plant_Moss_Rug_Red` |
| `Plant_Moss_Rug_Yellow` |
| `Plant_Moss_Short_Blue` |
| `Plant_Moss_Short_Green` |
| `Plant_Moss_Short_Green_Dark` |
| `Plant_Moss_Short_Red` |
| `Plant_Moss_Short_Yellow` |
| `Plant_Moss_Wall_Blue` |
| `Plant_Moss_Wall_Green` |
| `Plant_Moss_Wall_Green_Dark` |
| `Plant_Moss_Wall_Red` |
| `Plant_Moss_Wall_Yellow` |
| `Plant_Moss_Blue` |
| `Plant_Moss_Green` |
| `Plant_Moss_Green_Dark` |
| `Plant_Moss_Red` |
| `Plant_Moss_Yellow` |

### Vines
| Block ID |
|---|
| `Plant_Vine` |
| `Plant_Vine_Green_Hanging` |
| `Plant_Vine_Hanging` |
| `Plant_Vine_Jungle` |
| `Plant_Vine_Liana` |
| `Plant_Vine_Liana_End` |
| `Plant_Vine_Red_Hanging` |
| `Plant_Vine_Rug` |
| `Plant_Vine_Wall` |
| `Plant_Vine_Wall_Dead` |
| `Plant_Vine_Wall_Dry` |
| `Plant_Vine_Wall_Poisoned` |
| `Plant_Vine_Wall_Winter` |

---

## 17. SEAWEED & AQUATIC PLANTS
Source: `BlockTypeList/PlantsAndTrees.json`

| Block ID |
|---|
| `Plant_Seaweed_Arid_Red` |
| `Plant_Seaweed_Arid_Short` |
| `Plant_Seaweed_Arid_Stack` |
| `Plant_Seaweed_Arid_Tall` |
| `Plant_Seaweed_Arid_Yellow` |
| `Plant_Seaweed_Dead_Eerie` |
| `Plant_Seaweed_Dead_Ghostly` |
| `Plant_Seaweed_Dead_Short` |
| `Plant_Seaweed_Dead_Stack` |
| `Plant_Seaweed_Dead_Tall` |
| `Plant_Seaweed_Grass` |
| `Plant_Seaweed_Grass_Bulbs` |
| `Plant_Seaweed_Grass_Green` |
| `Plant_Seaweed_Grass_Short` |
| `Plant_Seaweed_Grass_Stack` |
| `Plant_Seaweed_Grass_Tall` |
| `Plant_Seaweed_Wet_Stack` |
| `Plant_Seaweed_Winter_Aurora` |
| `Plant_Seaweed_Winter_Blue` |
| `Plant_Seaweed_Winter_Short` |
| `Plant_Seaweed_Winter_Stack` |
| `Plant_Seaweed_Winter_Tall` |
| `Plant_Barnacles` |

---

## 18. REEDS & ROOTS
Source: `BlockTypeList/PlantsAndTrees.json`

| Block ID |
|---|
| `Plant_Reeds_Arid` |
| `Plant_Reeds_Lava` |
| `Plant_Reeds_Marsh` |
| `Plant_Reeds_Poison` |
| `Plant_Reeds_Water` |
| `Plant_Reeds_Wet` |
| `Plant_Reeds_Winter` |
| `Plant_Roots_Cave` |
| `Plant_Roots_Cave_Small` |
| `Plant_Roots_Leafy` |

---

## 19. SAPLINGS
Source: `BlockTypeList/PlantsAndTrees.json`

| Block ID |
|---|
| `Plant_Sapling_Ash` |
| `Plant_Sapling_Beech` |
| `Plant_Sapling_Birch` |
| `Plant_Sapling_Cedar` |
| `Plant_Sapling_Crystal` |
| `Plant_Sapling_Dry` |
| `Plant_Sapling_Oak` |
| `Plant_Sapling_Palm` |
| `Plant_Sapling_Poisoned` |
| `Plant_Sapling_Redwood` |
| `Plant_Sapling_Spruce` |
| `Plant_Sapling_Spruce_Frozen` |
| `Plant_Sapling_Windwillow` |

---

## 20. FRUIT BLOCKS
Source: `BlockTypeList/PlantsAndTrees.json`

| Block ID |
|---|
| `Plant_Fruit_Apple` |
| `Plant_Fruit_Azure` |
| `Plant_Fruit_Coconut` |
| `Plant_Fruit_Mango` |
| `Plant_Fruit_Pinkberry` |
| `Plant_Fruit_Spiral` |
| `Plant_Fruit_Windwillow` |

---

## 21. MISC PLANTS
Source: `BlockTypeList/PlantsAndTrees.json`

| Block ID |
|---|
| `Kweebec_Ancient` |
| `Plant_Hay_Bundle` |
| `Plant_Seeds_Pine` |
| `Plant_Test_Tree_Block` |

---

## 22. FLUIDS
Source: `BlockTextures/`, `HytaleGenerator/Biomes/`

| Block ID |
|---|
| `Fluid_Water` |
| `Fluid_Lava` |
| `Fluid_Poison` |
| `Fluid_Slime` |
| `Fluid_Slime_Red` |
| `Fluid_Tar` |

---

## 23. COLORED CLAY (Decorative Cubes)
Source: `BlockTextures/`

### Raw Clay (14 colors)
| Block ID |
|---|
| `Clay_Black` |
| `Clay_Blue` |
| `Clay_Cyan` |
| `Clay_Green` |
| `Clay_Grey` |
| `Clay_Lime` |
| `Clay_Orange` |
| `Clay_Pink` |
| `Clay_Purple` |
| `Clay_Red` |
| `Clay_White` |
| `Clay_Yellow` |

### Smooth Clay (14 colors)
| Block ID |
|---|
| `Clay_Smooth_Black` |
| `Clay_Smooth_Blue` |
| `Clay_Smooth_Cyan` |
| `Clay_Smooth_Green` |
| `Clay_Smooth_Grey` |
| `Clay_Smooth_Lime` |
| `Clay_Smooth_Orange` |
| `Clay_Smooth_Pink` |
| `Clay_Smooth_Purple` |
| `Clay_Smooth_Red` |
| `Clay_Smooth_White` |
| `Clay_Smooth_Yellow` |

### Soil Clay Variants
Source: `BlockTextures/`, `HytaleGenerator/Biomes/`

| Block ID |
|---|
| `Soil_Clay` |
| `Soil_Clay_Brick` |
| `Soil_Clay_Ocean` |
| `Soil_Clay_Ocean_Brick` |
| `Soil_Clay_Ocean_Brick_Decorative` |
| `Soil_Clay_Ocean_Brick_Ornate` |
| `Soil_Clay_Ocean_Brick_Smooth` |

---

## 24. CLOTH (10 colors)
Source: `BlockTextures/`

| Block ID |
|---|
| `Cloth_Black` |
| `Cloth_Blue` |
| `Cloth_Cyan` |
| `Cloth_Green` |
| `Cloth_Orange` |
| `Cloth_Pink` |
| `Cloth_Purple` |
| `Cloth_Red` |
| `Cloth_White` |
| `Cloth_Yellow` |

---

## 25. RUBBLE (Scatter Decoration)
Source: `BlockTypeList/AllScatter.json`

| Block ID |
|---|
| `Rubble_Aqua` |
| `Rubble_Aqua_Medium` |
| `Rubble_Basalt` |
| `Rubble_Basalt_Medium` |
| `Rubble_Calcite` |
| `Rubble_Calcite_Medium` |
| `Rubble_Ice` |
| `Rubble_Ice_Medium` |
| `Rubble_Marble` |
| `Rubble_Marble_Medium` |
| `Rubble_Quartzite` |
| `Rubble_Quartzite_Medium` |
| `Rubble_Sandstone` |
| `Rubble_Sandstone_Medium` |
| `Rubble_Sandstone_Red` |
| `Rubble_Sandstone_Red_Medium` |
| `Rubble_Sandstone_White` |
| `Rubble_Sandstone_White_Medium` |
| `Rubble_Shale` |
| `Rubble_Shale_Medium` |
| `Rubble_Slate` |
| `Rubble_Slate_Medium` |
| `Rubble_Stone` |
| `Rubble_Stone_Medium` |
| `Rubble_Stone_Mossy` |
| `Rubble_Stone_Mossy_Medium` |
| `Rubble_Volcanic` |
| `Rubble_Volcanic_Medium` |

---

## 26. DECORATION BLOCKS (Scatter & Models)
Source: `BlockTypeList/AllScatter.json`, `Common/Blocks/Decorative_Sets/`

### Scatter Decorations
| Block ID |
|---|
| `Deco_Bone_Ribs_Feran` |
| `Deco_Bone_Skulls_Feran` |
| `Deco_Bone_Skulls_Feran_Large` |
| `Deco_Bone_Skulls_Feran_Wall` |
| `Deco_Bone_Skulls_Wall` |
| `Deco_Coral_Shell` |
| `Deco_Coral_Shell_Purple` |
| `Deco_Coral_Shell_Sanddollar` |
| `Deco_Coral_Shell_Swirly` |
| `Deco_Coral_Shell_Urchin` |
| `Deco_Nest` |
| `Deco_Starfish` |
| `Deco_Trash` |
| `Deco_Trash_Pile_Large` |
| `Deco_Trash_Pile_Small` |
| `Deco_Greenscreen` |
| `Deco_Hay` |

### Bone Blocks
Source: `BlockTextures/`

| Block ID |
|---|
| `Bone_Block` |

### Sap Block
Source: `BlockTextures/`

| Block ID |
|---|
| `Sap_Glob` |

### Portal Block
Source: `BlockTextures/`

| Block ID |
|---|
| `Portal_Void` |

### Snow Brick
Source: `BlockTextures/`

| Block ID |
|---|
| `Soil_Snow_Brick` |

---

## 27. CRAFTING BENCHES (Model Blocks)
Source: `Common/Blocks/Benches/`

| Block ID |
|---|
| `Bench_Alchemy` |
| `Bench_Anvil` |
| `Bench_ArcaneTable` |
| `Bench_Armor` |
| `Bench_Armory` |
| `Bench_Bedroll` |
| `Bench_Builder` |
| `Bench_Campfire` |
| `Bench_Campfire_Billycan` |
| `Bench_Campfire_Cooking` |
| `Bench_Campfire_Off` |
| `Bench_Carpenter` |
| `Bench_Cooking` |
| `Bench_Farming` |
| `Bench_Furnace` |
| `Bench_Furnace2` |
| `Bench_Furnace_Simple` |
| `Bench_Furniture` |
| `Bench_Loom` |
| `Bench_Lumbermill` |
| `Bench_Memory_Bench` |
| `Bench_Salvage` |
| `Bench_Tannery` |
| `Bench_Weapon` |
| `Bench_Workbench` |
| `Bench_Workbench2` |
| `Bench_Workbench3` |

---

## 28. FURNITURE / DECORATIVE SETS (Model Blocks)
Source: `Common/Blocks/Decorative_Sets/`

### Sets Available
Each set contains: Bed, Bench, Bookshelf, Candle, Chair, Chandelier, Chest (Large/Small), Coffin, Counter, Door, Ladder, Lantern, Painting, Platform, Planter, Pot, Sack, Shelf, Sign, Statue, Stool, Table, Torch, Trapdoor, Wardrobe, Window (not all items in every set).

| Set Name | Path |
|---|---|
| `Ancient` | `Common/Blocks/Decorative_Sets/Ancient/` |
| `Christmas` | `Common/Blocks/Decorative_Sets/Christmas/` |
| `Crude` | `Common/Blocks/Decorative_Sets/Crude/` |
| `Desert` | `Common/Blocks/Decorative_Sets/Desert/` |
| `Feran` | `Common/Blocks/Decorative_Sets/Feran/` |
| `Frozen_Castle` | `Common/Blocks/Decorative_Sets/Frozen_Castle/` |
| `Human_Ruins` | `Common/Blocks/Decorative_Sets/Human_Ruins/` |
| `Jungle` | `Common/Blocks/Decorative_Sets/Jungle/` |
| `Kweebec` | `Common/Blocks/Decorative_Sets/Kweebec/` |
| `Lumberjack` | `Common/Blocks/Decorative_Sets/Lumberjack/` |

### Standard Furniture Types Per Set
| Item | Typical Block ID Pattern |
|---|---|
| Bed | `Deco_{Set}_Bed` |
| Bench | `Deco_{Set}_Bench` |
| Bookshelf | `Deco_{Set}_Bookshelf` |
| Candle | `Deco_{Set}_Candle` |
| Chair | `Deco_{Set}_Chair` |
| Chandelier | `Deco_{Set}_Chandelier` |
| Chest_Large | `Deco_{Set}_Chest_Large` |
| Chest_Small | `Deco_{Set}_Chest_Small` |
| Coffin | `Deco_{Set}_Coffin` |
| Door | `Deco_{Set}_Door` |
| Ladder | `Deco_{Set}_Ladder` |
| Lantern | `Deco_{Set}_Lantern` |
| Painting | `Deco_{Set}_Painting` |
| Platform | `Deco_{Set}_Platform` |
| Planter | `Deco_{Set}_Planter` |
| Pot | `Deco_{Set}_Pot` |
| Shelf | `Deco_{Set}_Shelf` |
| Sign | `Deco_{Set}_Sign` |
| Stool | `Deco_{Set}_Stool` |
| Table | `Deco_{Set}_Table` |
| Torch | `Deco_{Set}_Torch` |
| Trapdoor | `Deco_{Set}_Trapdoor` |
| Wardrobe | `Deco_{Set}_Wardrobe` |
| Window | `Deco_{Set}_Window` |

---

## 29. MISCELLANEOUS & STRUCTURAL
Source: `Common/Blocks/Miscellaneous/`, `Common/Blocks/Structures/`, `BlockTextures/`

### Misc Model Blocks
Source: `Common/Blocks/Miscellaneous/`

| Block ID | Description |
|---|---|
| `Fire` | Fire block (animated) |
| `EditorBlock` | Editor-only block |
| `EditorBlockPrefabAnchor` | Prefab anchor marker |
| `EditorBlockScreen` | Editor screen block |
| `EditorBlockTrigger` | Editor trigger |

### Prototype / Debug Blocks
Source: `BlockTextures/`

| Block ID |
|---|
| `Prototype_Floor1` |
| `Prototype_Floor2` |
| `Prototype_Floor3` |
| `WhiteTiles` |
| `StoneSlabAqua` |

---

## 30. ANIMATED BLOCK TYPES
Source: `Common/Blocks/Animations/`

| Animation Set | Block Types |
|---|---|
| `Candle` | Candle_Burn |
| `Chest` | Chest_Open, Chest_Close |
| `Coffin` | Coffin_Open, Coffin_Close |
| `Door` | Door_Open_In, Door_Open_Out, Door_Close_In, Door_Close_Out, Door_Open_Slide_In, Door_Open_Slide_Out |
| `Fire` | Fire_Burn, Fire_Small_Burn |
| `Light` | Light_On, Light_Off |
| `Trapdoor` | Trapdoor_Open, Trapdoor_Close |
| `Wardrobe` | Wardrobe_Open, Wardrobe_Close |

---

## SUMMARY BY CATEGORY

| Category | Count | Primary Source |
|---|---|---|
| Technical/Special | 5 | Built-in |
| Soils (Dirt/Grass/Sand/Mud/etc.) | ~40 | `BlockTypeList/Soils.json`, `Gravel.json`, `Snow.json`, Biomes |
| Rock (base + bricks/cobble) | ~90+ | `BlockTypeList/Rock.json`, `BlockTextures/` |
| Ores | 48 | `BlockTypeList/Ores.json` |
| Tree Wood (Trunk/Branch/Root) | ~190 | `BlockTypeList/TreeWood.json` |
| Wood Planks & Construction | ~40 | `BlockTextures/` |
| Tree Leaves | 44 | `BlockTypeList/TreeLeaves.json` |
| Grass (plant scatter) | 36 | `BlockTypeList/PlantScatter.json` |
| Flowers | ~80 | `BlockTypeList/PlantScatter.json` |
| Ferns | 10 | `BlockTypeList/PlantsAndTrees.json` |
| Bushes & Brambles | 27 | `BlockTypeList/PlantsAndTrees.json` |
| Cacti | 8 | `BlockTypeList/PlantsAndTrees.json` |
| Crops & Farming | 38 | `BlockTypeList/PlantsAndTrees.json` |
| Mushrooms | 50 | `BlockTypeList/PlantsAndTrees.json` |
| Coral (Block/Bush/Model) | 38 | `BlockTypeList/PlantsAndTrees.json` |
| Moss & Vines | 45 | `BlockTypeList/PlantsAndTrees.json` |
| Seaweed & Aquatic | 23 | `BlockTypeList/PlantsAndTrees.json` |
| Reeds & Roots | 10 | `BlockTypeList/PlantsAndTrees.json` |
| Saplings | 13 | `BlockTypeList/PlantsAndTrees.json` |
| Fruits | 7 | `BlockTypeList/PlantsAndTrees.json` |
| Fluids | 6 | `BlockTextures/` |
| Colored Clay | 26 | `BlockTextures/` |
| Cloth | 10 | `BlockTextures/` |
| Rubble | 28 | `BlockTypeList/AllScatter.json` |
| Scatter Decorations | 17 | `BlockTypeList/AllScatter.json` |
| Crafting Benches | 27 | `Common/Blocks/Benches/` |
| Furniture Sets | ~240 (10 sets × ~24 items) | `Common/Blocks/Decorative_Sets/` |
| Crystal Blocks | 8 | `BlockTextures/` |
| Hive Blocks | 6 | `BlockTextures/` |
| **TOTAL UNIQUE** | **~1100+** | |

---

## World Layer Block Usage
Source: `HytaleAssets/Server/World/Default/Zones/Layers/`

| Zone | Layer File | Blocks Used |
|---|---|---|
| Zone 1 | `Static.json` | `Rock_Stone`, `Rock_Basalt` |
| Zone 1 | `Volcanic.json` | `Rock_Volcanic` |
| Zone 1 | `Bedrock.json` | `Rock_Bedrock` |
| Zone 2 | `Static.json` | `Rock_Sandstone`, `Rock_Sandstone_White`, `Soil_Dirt_Dry`, `Rock_Sandstone_Red` |
| Zone 2 | `Static_Desert.json` | `Rock_Sandstone`, `Rock_Sandstone_White` |
| Zone 2 | `Static_Desert_Red.json` | `Soil_Clay`, `Rock_Sandstone_Red` |
| Zone 2 | `Static_Mountain.json` | `Rock_Sandstone`, `Rock_Sandstone_White` |
| Zone 2 | `Static_Oasis.json` | `Rock_Sandstone`, `Rock_Sandstone_White`, `Soil_Dirt_Dry`, `Rock_Sandstone_Red` |
| Zone 2 | `Static_Savannah.json` | `Rock_Sandstone`, `Rock_Sandstone_Red` |
| Zone 3 | `Static.json` | `Rock_Shale`, `Rock_Slate`, `Rock_Basalt`, `Soil_Dirt_Cold`, `Rock_Volcanic` |

## Biome Surface Blocks
Source: `HytaleAssets/Server/HytaleGenerator/Biomes/`

Additional blocks referenced as surface/top layer in biome configs:
- `Soil_Ash`, `Soil_Clay`, `Soil_Clay_Grey`, `Soil_Clay_Orange`, `Soil_Clay_Smooth_Black`, `Soil_Clay_Yellow`
- `Soil_Dirt`, `Soil_Dirt_Cold`, `Soil_Dirt_Dry`
- `Soil_Grass`, `Soil_Grass_Cold`, `Soil_Grass_Deep`, `Soil_Grass_Dry`, `Soil_Grass_Full`, `Soil_Grass_Sunny`
- `Soil_Gravel`, `Soil_Gravel_Sand`, `Soil_Gravel_Sand_White`
- `Soil_Leaves`, `Soil_Mud`, `Soil_Mud_Dry`, `Soil_Needles`, `Soil_Pathway`
- `Soil_Pebbles`, `Soil_Pebbles_Frozen`
- `Soil_Sand`, `Soil_Sand_Ashen`, `Soil_Sand_White`, `Soil_Snow`
- `Rock_Ice`, `Rock_Ice_Permafrost`, `Rock_Magma_Cooled`, `Rock_Stone_Cobble`, `Rock_Crystal_Red`
- `Fluid_Water`
