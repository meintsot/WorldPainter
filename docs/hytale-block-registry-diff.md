# Hytale Block Registry Diff

Comparison of Hytale's current asset files (`BlockTypeList/*.json`) against
WorldPainter's hardcoded `HytaleBlockRegistry`.

- **Source (Hytale):** `%APPDATA%\Hytale\install\release\package\game\latest\Assets\Server\BlockTypeList\` (733 blocks)
- **Source (WP):** `HytaleBlockRegistry.java` `addBlocks()` calls (746 unique block IDs)
- **Date:** 2026-03-26

---

## New in Hytale, missing from WP registry (184 blocks)

All of these are wood/tree structural blocks added in the recent update,
plus one misc block (`Wood_Sticks`).

### Amber

- `Wood_Amber_Branch_Corner`
- `Wood_Amber_Branch_Long`
- `Wood_Amber_Branch_Short`
- `Wood_Amber_Roots`
- `Wood_Amber_Trunk`
- `Wood_Amber_Trunk_Full`

### Ash

- `Wood_Ash_Branch_Corner`
- `Wood_Ash_Branch_Long`
- `Wood_Ash_Branch_Short`
- `Wood_Ash_Roots`
- `Wood_Ash_Trunk`
- `Wood_Ash_Trunk_Full`

### Aspen

- `Wood_Aspen_Branch_Corner`
- `Wood_Aspen_Branch_Long`
- `Wood_Aspen_Branch_Short`
- `Wood_Aspen_Roots`
- `Wood_Aspen_Trunk`
- `Wood_Aspen_Trunk_Full`

### Azure

- `Wood_Azure_Branch_Corner`
- `Wood_Azure_Branch_Long`
- `Wood_Azure_Branch_Short`
- `Wood_Azure_Roots`
- `Wood_Azure_Trunk`
- `Wood_Azure_Trunk_Full`

### Bamboo

- `Wood_Bamboo_Branch_Long`
- `Wood_Bamboo_Trunk`

### Banyan

- `Wood_Banyan_Branch_Corner`
- `Wood_Banyan_Branch_Long`
- `Wood_Banyan_Branch_Short`
- `Wood_Banyan_Roots`
- `Wood_Banyan_Trunk`
- `Wood_Banyan_Trunk_Full`

### Beech

- `Wood_Beech_Branch_Corner`
- `Wood_Beech_Branch_Long`
- `Wood_Beech_Branch_Short`
- `Wood_Beech_Roots`
- `Wood_Beech_Trunk`
- `Wood_Beech_Trunk_Full`

### Birch

- `Wood_Birch_Branch_Corner`
- `Wood_Birch_Branch_Long`
- `Wood_Birch_Branch_Short`
- `Wood_Birch_Roots`
- `Wood_Birch_Trunk`
- `Wood_Birch_Trunk_Full`

### Bottletree

- `Wood_Bottletree_Branch_Corner`
- `Wood_Bottletree_Branch_Long`
- `Wood_Bottletree_Branch_Short`
- `Wood_Bottletree_Roots`
- `Wood_Bottletree_Trunk`
- `Wood_Bottletree_Trunk_Full`

### Burnt

- `Wood_Burnt_Branch_Corner`
- `Wood_Burnt_Branch_Long`
- `Wood_Burnt_Branch_Short`
- `Wood_Burnt_Roots`
- `Wood_Burnt_Trunk`
- `Wood_Burnt_Trunk_Full`

### Camphor

- `Wood_Camphor_Branch_Corner`
- `Wood_Camphor_Branch_Long`
- `Wood_Camphor_Branch_Short`
- `Wood_Camphor_Roots`
- `Wood_Camphor_Trunk`
- `Wood_Camphor_Trunk_Full`

### Cedar

- `Wood_Cedar_Branch_Corner`
- `Wood_Cedar_Branch_Long`
- `Wood_Cedar_Branch_Short`
- `Wood_Cedar_Roots`
- `Wood_Cedar_Trunk`
- `Wood_Cedar_Trunk_Full`

### Crystal

- `Wood_Crystal_Branch_Corner`
- `Wood_Crystal_Branch_Long`
- `Wood_Crystal_Branch_Short`
- `Wood_Crystal_Roots`
- `Wood_Crystal_Trunk`
- `Wood_Crystal_Trunk_Full`

### Dry

- `Wood_Dry_Branch_Corner`
- `Wood_Dry_Branch_Long`
- `Wood_Dry_Branch_Short`
- `Wood_Dry_Roots`
- `Wood_Dry_Trunk`
- `Wood_Dry_Trunk_Full`

### Fig Blue

- `Wood_Fig_Blue_Branch_Corner`
- `Wood_Fig_Blue_Branch_Long`
- `Wood_Fig_Blue_Branch_Short`
- `Wood_Fig_Blue_Roots`
- `Wood_Fig_Blue_Trunk`
- `Wood_Fig_Blue_Trunk_Full`

### Fir

- `Wood_Fir_Branch_Corner`
- `Wood_Fir_Branch_Long`
- `Wood_Fir_Branch_Short`
- `Wood_Fir_Roots`
- `Wood_Fir_Trunk`
- `Wood_Fir_Trunk_Full`

### Fire

- `Wood_Fire_Branch_Corner`
- `Wood_Fire_Branch_Long`
- `Wood_Fire_Branch_Short`
- `Wood_Fire_Trunk`
- `Wood_Fire_Trunk_Full`

### Gnarled

- `Wood_Gnarled_Roots`

### Gumboab

- `Wood_Gumboab_Branch_Corner`
- `Wood_Gumboab_Branch_Long`
- `Wood_Gumboab_Branch_Short`
- `Wood_Gumboab_Roots`
- `Wood_Gumboab_Trunk`
- `Wood_Gumboab_Trunk_Full`

### Ice

- `Wood_Ice_Trunk`

### Jungle

- `Wood_Jungle_Branch_Corner`
- `Wood_Jungle_Branch_Long`
- `Wood_Jungle_Branch_Short`
- `Wood_Jungle_Roots`
- `Wood_Jungle_Trunk`
- `Wood_Jungle_Trunk_Full`

### Maple

- `Wood_Maple_Branch_Corner`
- `Wood_Maple_Branch_Long`
- `Wood_Maple_Branch_Short`
- `Wood_Maple_Roots`
- `Wood_Maple_Trunk`
- `Wood_Maple_Trunk_Full`

### Oak

- `Wood_Oak_Branch_Corner`
- `Wood_Oak_Branch_Long`
- `Wood_Oak_Branch_Short`
- `Wood_Oak_Roots`
- `Wood_Oak_Trunk`
- `Wood_Oak_Trunk_Full`

### Palm

- `Wood_Palm_Branch_Corner`
- `Wood_Palm_Branch_Long`
- `Wood_Palm_Branch_Short`
- `Wood_Palm_Roots`
- `Wood_Palm_Trunk`
- `Wood_Palm_Trunk_Full`

### Palo

- `Wood_Palo_Branch_Corner`
- `Wood_Palo_Branch_Long`
- `Wood_Palo_Branch_Short`
- `Wood_Palo_Roots`
- `Wood_Palo_Trunk`
- `Wood_Palo_Trunk_Full`

### Petrified

- `Wood_Petrified_Branch_Corner`
- `Wood_Petrified_Branch_Long`
- `Wood_Petrified_Branch_Short`
- `Wood_Petrified_Roots`
- `Wood_Petrified_Trunk`
- `Wood_Petrified_Trunk_Full`

### Poisoned

- `Wood_Poisoned_Branch_Corner`
- `Wood_Poisoned_Branch_Long`
- `Wood_Poisoned_Branch_Short`
- `Wood_Poisoned_Roots`
- `Wood_Poisoned_Trunk`
- `Wood_Poisoned_Trunk_Full`

### Redwood

- `Wood_Redwood_Branch_Corner`
- `Wood_Redwood_Branch_Long`
- `Wood_Redwood_Branch_Short`
- `Wood_Redwood_Roots`
- `Wood_Redwood_Trunk`
- `Wood_Redwood_Trunk_Full`

### Sallow

- `Wood_Sallow_Branch_Corner`
- `Wood_Sallow_Branch_Long`
- `Wood_Sallow_Branch_Short`
- `Wood_Sallow_Roots`
- `Wood_Sallow_Trunk`
- `Wood_Sallow_Trunk_Full`

### Spiral

- `Wood_Spiral_Branch_Corner`
- `Wood_Spiral_Branch_Long`
- `Wood_Spiral_Branch_Short`
- `Wood_Spiral_Roots`
- `Wood_Spiral_Trunk`
- `Wood_Spiral_Trunk_Full`

### Stormbark

- `Wood_Stormbark_Branch_Corner`
- `Wood_Stormbark_Branch_Long`
- `Wood_Stormbark_Branch_Short`
- `Wood_Stormbark_Roots`
- `Wood_Stormbark_Trunk`
- `Wood_Stormbark_Trunk_Full`

### Windwillow

- `Wood_Windwillow_Branch_Corner`
- `Wood_Windwillow_Branch_Long`
- `Wood_Windwillow_Branch_Short`
- `Wood_Windwillow_Roots`
- `Wood_Windwillow_Trunk`
- `Wood_Windwillow_Trunk_Full`

### Wisteria Wild

- `Wood_Wisteria_Wild_Branch_Corner`
- `Wood_Wisteria_Wild_Branch_Long`
- `Wood_Wisteria_Wild_Branch_Short`
- `Wood_Wisteria_Wild_Roots`
- `Wood_Wisteria_Wild_Trunk`
- `Wood_Wisteria_Wild_Trunk_Full`

### Misc

- `Wood_Sticks`

---

## In WP registry but removed from Hytale assets (198 blocks)

These blocks exist in `HytaleBlockRegistry.java` but are no longer present
in Hytale's `BlockTypeList` JSON files. They may have been removed, renamed,
or moved to a different definition system in the recent update.

### Bones / Decoration

- `Deco_Bone_Full`
- `Deco_Bone_Pile`
- `Deco_Bone_Skulls`
- `Deco_Bone_Spike`
- `Deco_Bone_Spine`

### Editor

- `Editor_Anchor`
- `Editor_Block`
- `Editor_Empty`

### Fluids

- `Fluid_Lava`
- `Fluid_Lava_Source`
- `Fluid_Poison`
- `Fluid_Poison_Source`
- `Fluid_Slime`
- `Fluid_Slime_Red`
- `Fluid_Slime_Red_Source`
- `Fluid_Slime_Source`
- `Fluid_Tar`
- `Fluid_Tar_Source`
- `Fluid_Water`
- `Fluid_Water_Finite`
- `Fluid_Water_Source`
- `Lava_Source`
- `Water_Source`

### Portals

- `Portal_Device`
- `Portal_Return`
- `Portal_Void`

### Rails

- `Rail_Kart`

### Crops (removed)

- `Plant_Crop_Aubergine`
- `Plant_Crop_Potato_Sweet_Block`

### Rock — Crystals

- `Rock_Crystal_Blue_Block`
- `Rock_Crystal_Blue_Large`
- `Rock_Crystal_Blue_Medium`
- `Rock_Crystal_Blue_Small`
- `Rock_Crystal_Cyan_Block`
- `Rock_Crystal_Cyan_Large`
- `Rock_Crystal_Cyan_Medium`
- `Rock_Crystal_Cyan_Small`
- `Rock_Crystal_Green_Block`
- `Rock_Crystal_Green_Large`
- `Rock_Crystal_Green_Medium`
- `Rock_Crystal_Green_Small`
- `Rock_Crystal_Pink_Block`
- `Rock_Crystal_Pink_Large`
- `Rock_Crystal_Pink_Medium`
- `Rock_Crystal_Pink_Small`
- `Rock_Crystal_Purple_Block`
- `Rock_Crystal_Purple_Large`
- `Rock_Crystal_Purple_Medium`
- `Rock_Crystal_Purple_Small`
- `Rock_Crystal_Red_Block`
- `Rock_Crystal_Red_Large`
- `Rock_Crystal_Red_Medium`
- `Rock_Crystal_Red_Small`
- `Rock_Crystal_White_Block`
- `Rock_Crystal_White_Large`
- `Rock_Crystal_White_Medium`
- `Rock_Crystal_White_Small`
- `Rock_Crystal_Yellow_Block`
- `Rock_Crystal_Yellow_Large`
- `Rock_Crystal_Yellow_Medium`
- `Rock_Crystal_Yellow_Small`

### Rock — Gems

- `Rock_Gem_Diamond`
- `Rock_Gem_Emerald`
- `Rock_Gem_Ruby`
- `Rock_Gem_Sapphire`
- `Rock_Gem_Topaz`
- `Rock_Gem_Voidstone`
- `Rock_Gem_Zephyr`

### Rock — Ice

- `Rock_Ice`
- `Rock_Ice_Blue`
- `Rock_Ice_Icicles`
- `Rock_Ice_Permafrost`
- `Rock_Ice_Stalactite_Large`
- `Rock_Ice_Stalactite_Small`

### Rock — Magma

- `Rock_Magma_Cooled`
- `Rock_Magma_Cooled_Half`

### Rock — Runic

- `Rock_Runic_Blue_Brick`
- `Rock_Runic_Blue_Brick_Beam`
- `Rock_Runic_Blue_Brick_Half`
- `Rock_Runic_Blue_Brick_Ornate`
- `Rock_Runic_Blue_Brick_Stairs`
- `Rock_Runic_Blue_Brick_Wall`
- `Rock_Runic_Blue_Cobble`
- `Rock_Runic_Brick`
- `Rock_Runic_Brick_Beam`
- `Rock_Runic_Brick_Half`
- `Rock_Runic_Brick_Ornate`
- `Rock_Runic_Brick_Pillar_Base`
- `Rock_Runic_Brick_Pillar_Middle`
- `Rock_Runic_Brick_Stairs`
- `Rock_Runic_Brick_Wall`
- `Rock_Runic_Cobble`
- `Rock_Runic_Cobble_Beam`
- `Rock_Runic_Cobble_Half`
- `Rock_Runic_Cobble_Stairs`
- `Rock_Runic_Dark_Brick`
- `Rock_Runic_Dark_Brick_Beam`
- `Rock_Runic_Dark_Brick_Half`
- `Rock_Runic_Dark_Brick_Ornate`
- `Rock_Runic_Dark_Brick_Stairs`
- `Rock_Runic_Dark_Brick_Wall`
- `Rock_Runic_Dark_Cobble`
- `Rock_Runic_Pipe`
- `Rock_Runic_Teal_Brick`
- `Rock_Runic_Teal_Brick_Beam`
- `Rock_Runic_Teal_Brick_Half`
- `Rock_Runic_Teal_Brick_Ornate`
- `Rock_Runic_Teal_Brick_Stairs`
- `Rock_Runic_Teal_Brick_Wall`
- `Rock_Runic_Teal_Cobble`

### Soil — Clay

- `Soil_Clay`
- `Soil_Clay_Black`
- `Soil_Clay_Blue`
- `Soil_Clay_Brick`
- `Soil_Clay_Brick_Beam`
- `Soil_Clay_Brick_Half`
- `Soil_Clay_Brick_Stairs`
- `Soil_Clay_Brick_Wall`
- `Soil_Clay_Cyan`
- `Soil_Clay_Green`
- `Soil_Clay_Grey`
- `Soil_Clay_Lime`
- `Soil_Clay_Ocean`
- `Soil_Clay_Ocean_Brick`
- `Soil_Clay_Ocean_Brick_Beam`
- `Soil_Clay_Ocean_Brick_Decorative`
- `Soil_Clay_Ocean_Brick_Half`
- `Soil_Clay_Ocean_Brick_Ornate`
- `Soil_Clay_Ocean_Brick_Roof`
- `Soil_Clay_Ocean_Brick_Roof_Flap`
- `Soil_Clay_Ocean_Brick_Roof_Flat`
- `Soil_Clay_Ocean_Brick_Roof_Vertical`
- `Soil_Clay_Ocean_Brick_Stairs`
- `Soil_Clay_Ocean_Brick_Wall`
- `Soil_Clay_Orange`
- `Soil_Clay_Pink`
- `Soil_Clay_Purple`
- `Soil_Clay_Red`
- `Soil_Clay_Smooth_Black`
- `Soil_Clay_Smooth_Blue`
- `Soil_Clay_Smooth_Cyan`
- `Soil_Clay_Smooth_Green`
- `Soil_Clay_Smooth_Grey`
- `Soil_Clay_Smooth_Lime`
- `Soil_Clay_Smooth_Orange`
- `Soil_Clay_Smooth_Pink`
- `Soil_Clay_Smooth_Purple`
- `Soil_Clay_Smooth_Red`
- `Soil_Clay_Smooth_White`
- `Soil_Clay_Smooth_Yellow`
- `Soil_Clay_Stalactite_Large`
- `Soil_Clay_Stalactite_Small`
- `Soil_Clay_White`
- `Soil_Clay_Yellow`

### Soil — Gravel (half variants)

- `Soil_Gravel_Half`
- `Soil_Gravel_Mossy_Half`
- `Soil_Gravel_Sand_Half`
- `Soil_Gravel_Sand_Red_Half`
- `Soil_Gravel_Sand_White_Half`

### Soil — Hive

- `Soil_Hive`
- `Soil_Hive_Brick`
- `Soil_Hive_Brick_Beam`
- `Soil_Hive_Brick_Fence`
- `Soil_Hive_Brick_Half`
- `Soil_Hive_Brick_Smooth`
- `Soil_Hive_Brick_Stairs`
- `Soil_Hive_Corrupted`
- `Soil_Hive_Corrupted_Brick`
- `Soil_Hive_Corrupted_Brick_Beam`
- `Soil_Hive_Corrupted_Brick_Fence`
- `Soil_Hive_Corrupted_Brick_Half`
- `Soil_Hive_Corrupted_Brick_Smooth`
- `Soil_Hive_Corrupted_Brick_Stairs`

### Soil — Misc

- `Soil_Ash`
- `Soil_Leaves`
- `Soil_Leaves_Autumn`
- `Soil_Mud`
- `Soil_Mud_Dry`
- `Soil_Needles`
- `Soil_Pathway`
- `Soil_Pathway_Half`
- `Soil_Pathway_Quarter`
- `Soil_Pathway_ThreeQuarter`
- `Soil_Roots_Poisoned`
- `Soil_Sand`
- `Soil_Sand_Ashen`
- `Soil_Sand_Red`
- `Soil_Sand_White`
- `Soil_Sand_White_Path_Half`
- `Soil_Seaweed_Block`

### Soil — Snow (construction)

- `Soil_Snow_Brick`
- `Soil_Snow_Brick_Beam`
- `Soil_Snow_Brick_Half`
- `Soil_Snow_Brick_Stairs`
- `Soil_Snow_Brick_Wall`

### Traps

- `Trap_Ancient_Platform`
- `Trap_Ice`
- `Trap_Slate`
