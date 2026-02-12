#!/usr/bin/env python3
"""
Generate visually distinct colours for HytaleTerrain.java terrain definitions.

Strategy: Each terrain category gets a distinct hue range, and within each category,
terrains are spread across different saturation/value levels to maximise visual distinction.
"""

import colorsys
import re
import os

JAVA_FILE = r"c:\Users\Sotirios\Desktop\WorldPainter\WorldPainter\WPCore\src\main\java\org\pepsoft\worldpainter\hytale\HytaleTerrain.java"


def hsv_to_hex(h, s, v):
    """Convert HSV (0-360, 0-100, 0-100) to hex colour string like 0xRRGGBB."""
    r, g, b = colorsys.hsv_to_rgb(h / 360, s / 100, v / 100)
    ri = max(0, min(255, int(round(r * 255))))
    gi = max(0, min(255, int(round(g * 255))))
    bi = max(0, min(255, int(round(b * 255))))
    return f"0x{ri:02x}{gi:02x}{bi:02x}"


def spread_colors(base_hue, hue_range, count, sat_range=(40, 95), val_range=(30, 90)):
    """Generate `count` visually distinct colours around base_hue ± hue_range."""
    colors = []
    if count == 0:
        return colors
    if count == 1:
        return [hsv_to_hex(base_hue, (sat_range[0]+sat_range[1])//2, (val_range[0]+val_range[1])//2)]

    # Create a grid of (hue, sat, val) combinations, then pick `count` well-spaced ones
    # Use golden angle for hue spread and interleave sat/val levels
    import math
    golden_angle = 137.508  # degrees

    for i in range(count):
        # Spread hue across the range
        if hue_range > 0:
            hue_frac = (i * golden_angle / 360) % 1.0
            h = (base_hue - hue_range + hue_frac * 2 * hue_range) % 360
        else:
            h = base_hue

        # Alternate saturation levels
        sat_levels = 4
        sat_idx = i % sat_levels
        s = sat_range[0] + (sat_range[1] - sat_range[0]) * sat_idx / max(1, sat_levels - 1)

        # Alternate value levels (offset from sat to avoid correlation)
        val_levels = 5
        val_idx = (i + 2) % val_levels
        v = val_range[0] + (val_range[1] - val_range[0]) * val_idx / max(1, val_levels - 1)

        colors.append(hsv_to_hex(h, s, v))
    return colors


# Define the terrain categories and their target hue ranges
# Category: (base_hue, hue_range, sat_range, val_range)
# Hues: Red=0, Orange=30, Yellow=60, Green=120, Cyan=180, Blue=240, Purple=270, Pink=330

CATEGORIES = {
    # Ground blocks - diverse: stones, sands, grasses, crystals
    "GROUND_STONE": {  # stones, basalt, slate, shale, etc
        "hue": 0, "hue_range": 20,
        "sat": (5, 20), "val": (25, 80),
        "terrains": [
            "BASALT", "BASALT_COBBLE", "STONE", "SHALE", "SHALE_COBBLE",
            "SLATE", "SLATE_COBBLE", "CRACKED_SLATE", "VOLCANIC_ROCK",
            "CRACKED_VOLCANIC_ROCK", "COLD_MAGMA", "MARBLE", "QUARTZITE",
            "CHALK", "MOSSY_STONE",
        ]
    },
    "GROUND_CALCITE_SALT": {
        "hue": 40, "hue_range": 10,
        "sat": (8, 25), "val": (75, 95),
        "terrains": [
            "CALCITE", "CALCITE_COBBLE", "SALT_BLOCK",
        ]
    },
    "GROUND_SAND": {
        "hue": 38, "hue_range": 15,
        "sat": (30, 70), "val": (60, 90),
        "terrains": [
            "SAND", "SANDSTONE", "SANDSTONE_BRICK_SMOOTH", "ASHEN_SAND",
            "WHITE_SAND", "WHITE_SANDSTONE", "WHITE_SANDSTONE_BRICK_SMOOTH",
            "RED_SAND", "RED_SANDSTONE", "RED_SANDSTONE_BRICK_SMOOTH",
        ]
    },
    "GROUND_AQUA": {
        "hue": 190, "hue_range": 10,
        "sat": (40, 70), "val": (55, 80),
        "terrains": ["AQUA_COBBLE", "AQUA_STONE"]
    },
    "GROUND_ICE": {
        "hue": 210, "hue_range": 10,
        "sat": (20, 50), "val": (80, 95),
        "terrains": ["BLUE_ICE", "ICE"]
    },
    "GROUND_CRYSTAL": {
        "hue": 0, "hue_range": 180,  # full spectrum
        "sat": (60, 90), "val": (50, 85),
        "terrains": [
            "BLUE_CRYSTAL", "CYAN_CRYSTAL", "GREEN_CRYSTAL", "PINK_CRYSTAL",
            "PURPLE_CRYSTAL", "RED_CRYSTAL", "WHITE_CRYSTAL", "YELLOW_CRYSTAL",
        ]
    },
    "GROUND_GRASS": {
        "hue": 100, "hue_range": 30,
        "sat": (35, 85), "val": (30, 75),
        "terrains": [
            "GRASS", "FULL_GRASS", "DEEP_GRASS", "SUMMER_GRASS", "WET_GRASS",
            "COLD_GRASS", "DRY_GRASS", "BURNED_GRASS",
        ]
    },
    "GROUND_VOLCANIC_POISON": {
        "hue": 90, "hue_range": 30,
        "sat": (20, 50), "val": (18, 35),
        "terrains": ["POISONED_VOLCANIC_ROCK"]
    },

    # Leaves - greens, oranges, reds, blues with wide spread
    "LEAVES_GREEN": {
        "hue": 115, "hue_range": 35,
        "sat": (30, 90), "val": (25, 75),
        "terrains": [
            "ASH_LEAVES", "ASPEN_LEAVES", "BAMBOO_LEAVES", "BANYAN_LEAVES",
            "BEECH_LEAVES", "BIRCH_LEAVES", "BOTTLE_TREE_LEAVES",
            "BRAMBLE_LEAVES", "CAMPHOR_LEAVES", "CEDAR_LEAVES",
            "FILTER_TREE_LEAVES", "FILTER_TREE_WOOD_AND_LEAVES",
            "FIR_LEAVES", "FIR_LEAVES_TIP",
            "FOREST_FLOOR_LEAVES", "GIANT_PALM_LEAVES",
            "GUMBOAB_LEAVES", "JUNGLE_FLOOR_LEAVES",
            "OAK_LEAVES", "PALM_LEAVES", "PALO_LEAVES",
            "REDWOOD_LEAVES", "SPIRAL_LEAVES",
            "STORM_BARK_LEAVES", "TROPICAL_LEAVES", "WILLOW_LEAVES",
            "ARID_PALM_LEAVES",
        ]
    },
    "LEAVES_WARM": {  # oranges, reds, yellows, browns
        "hue": 25, "hue_range": 25,
        "sat": (50, 90), "val": (40, 80),
        "terrains": [
            "AMBER_LEAVES", "AUTUMN_LEAVES", "DRY_LEAVES",
            "FIRE_LEAVES", "MAPLE_LEAVES", "SHALLOW_LEAVES",
            "BURNED_LEAVES", "DEAD_LEAVES",
        ]
    },
    "LEAVES_SPECIAL": {  # blue, crystal, snowy, wisteria, poisoned, petrified
        "hue": 220, "hue_range": 60,
        "sat": (20, 70), "val": (50, 90),
        "terrains": [
            "AZURE_LEAVES", "BLUE_FIG_LEAVES", "CRYSTAL_LEAVES",
            "PETRIFIED_PINE_LEAVES", "POISONED_LEAVES",
            "RED_FIR_LEAVES", "SNOWY_FIR_LEAVES", "SNOWY_FIR_LEAVES_TIP",
            "SNOWY_LEAVES", "WILD_WISTERIA_LEAVES",
        ]
    },

    # Bushes & Brambles
    "BUSHES_GREEN": {
        "hue": 110, "hue_range": 25,
        "sat": (30, 85), "val": (25, 70),
        "terrains": [
            "BUSH", "GREEN_BUSH", "JUNGLE_BUSH", "LUSH_BIG_BUSH",
            "WET_BUSH", "EXOTIC_HANGING_BUSH", "GREEN_BRAMBLE",
        ]
    },
    "BUSHES_ARID_DRY": {
        "hue": 40, "hue_range": 20,
        "sat": (25, 65), "val": (35, 65),
        "terrains": [
            "ARID_BUSH", "ARID_PALM_BUSH", "BUSHY_ARID_GRASS",
            "ARID_BRAMBLE", "DRY_BRAMBLE", "RED_ARID_BUSH",
        ]
    },
    "BUSHES_DEAD_WINTER": {
        "hue": 30, "hue_range": 30,
        "sat": (10, 40), "val": (30, 80),
        "terrains": [
            "DEAD_BUSH", "DEAD_HANGING_BUSH", "SHRUB", "LARGE_DEAD_BUSH",
            "FROZEN_SHRUB", "BUSHY_WINTER_GRASS", "SNOWY_WINTER_BUSH",
            "RED_WINTER_BUSH", "WINTER_BRAMBLE", "BIG_CRYSTAL_BUSH",
        ]
    },

    # Cacti
    "CACTI": {
        "hue": 130, "hue_range": 15,
        "sat": (35, 80), "val": (30, 65),
        "terrains": [
            "CACTUS_BOTTOM", "CACTUS_MIDDLE", "CACTUS_TOP",
            "CACTUS_BALL", "LARGE_FLAT_CACTUS", "FLAT_CACTUS",
            "SMALL_FLAT_CACTUS",
        ]
    },
    "CACTUS_FLOWER_ONLY": {
        "hue": 330, "hue_range": 5,
        "sat": (60, 80), "val": (70, 85),
        "terrains": ["CACTUS_FLOWER"]
    },

    # Coral Blocks - each has a distinct colour by design
    "CORAL_BLOCKS": {
        "hue": 0, "hue_range": 180,
        "sat": (50, 90), "val": (45, 85),
        "terrains": [
            "BLUE_CORAL_BLOCK", "CYAN_CORAL_BLOCK", "GREEN_CORAL_BLOCK",
            "GRAY_CORAL_BLOCK", "LIME_GREEN_CORAL_BLOCK", "ORANGE_CORAL_BLOCK",
            "PINK_CORAL_BLOCK", "PURPLE_CORAL_BLOCK", "POISONED_CORAL_BLOCK",
            "RED_CORAL_BLOCK", "VIOLET_CORAL_BLOCK", "WHITE_CORAL_BLOCK",
            "YELLOW_CORAL_BLOCK",
        ]
    },
    "CORAL_BUSHES": {
        "hue": 0, "hue_range": 180,
        "sat": (45, 85), "val": (40, 80),
        "terrains": [
            "BLUE_CORAL_BUSH", "CYAN_CORAL_BUSH", "GREEN_CORAL_BUSH",
            "GRAY_CORAL_BUSH", "NEON_CORAL_BUSH", "ORANGE_CORAL_BUSH",
            "PINK_CORAL_BUSH", "POISONED_CORAL_BUSH", "PURPLE_CORAL_BUSH",
            "RED_CORAL_BUSH", "VIOLET_CORAL_BUSH", "WHITE_CORAL_BUSH",
            "YELLOW_CORAL_BUSH",
        ]
    },
    "CORAL_MODELS": {
        "hue": 0, "hue_range": 180,
        "sat": (50, 88), "val": (42, 82),
        "terrains": [
            "BLUE_CORAL_SPONGE", "CYAN_CORAL_SPONGE", "GREEN_CORAL_TUBES",
            "GRAY_BRACKET_CORAL", "LIME_CORAL_SPONGE", "ORANGE_BRACKET_CORAL",
            "PINK_FAN_CORAL", "PURPLE_CORAL_TUBES", "RED_CORAL_SPONGE",
            "SEA_ANEMONE", "WHITE_CORAL_SPONGE", "YELLOW_CORAL_TUBES",
        ]
    },

    # Berry bushes
    "BERRY_BUSHES": {
        "hue": 120, "hue_range": 30,
        "sat": (25, 55), "val": (30, 50),
        "terrains": ["BERRY_BUSH", "WET_BERRY_BUSH", "WINTER_BERRY_BUSH"]
    },

    # Mushrooms
    "MUSHROOMS": {
        "hue": 0, "hue_range": 180,
        "sat": (40, 95), "val": (30, 90),
        "terrains": [
            "BLOOD_CAP_MUSHROOM", "AZURE_CAP_MUSHROOM",
            "BROWN_MUSHROOM_MYCELIUM", "LARGE_BOOMSHROOM", "SMALL_BOOMSHROOM",
            "BROWN_CAP_MUSHROOM", "SPOTTED_GREEN_CAP_MUSHROOM",
            "SPOTTED_ALLIUM_CAP_MUSHROOM", "RED_CAP_MUSHROOM",
            "WHITE_CAP_MUSHROOM", "BLUE_COMMON_MUSHROOM",
            "BROWN_COMMON_MUSHROOM", "PUFFY_GREEN_COMMON_MUSHROOM",
            "BLUE_FLAT_CAP_MUSHROOM", "GREEN_FLAT_CAP_MUSHROOM",
            "BLUE_GLOWING_MUSHROOM", "GREEN_GLOWING_MUSHROOM",
            "ORANGE_GLOWING_MUSHROOM", "PURPLE_GLOWING_MUSHROOM",
            "RED_GLOWING_MUSHROOM", "VIOLET_GLOWING_MUSHROOM",
            "BROWN_MUSHROOM_SHELF", "GREEN_MUSHROOM_SHELF",
            "YELLOW_MUSHROOM_SHELVES", "STORM_CAP_MUSHROOM",
        ]
    },

    # Storm/Special
    "STORM_SPECIAL": {
        "hue": 200, "hue_range": 30,
        "sat": (30, 60), "val": (60, 85),
        "terrains": ["STORM_THISTLE", "STORM_SAPLING"]
    },

    # Ferns
    "FERNS": {
        "hue": 115, "hue_range": 30,
        "sat": (30, 85), "val": (25, 65),
        "terrains": [
            "FERN", "ARID_FERN", "FOREST_FERN", "JUNGLE_FERN",
            "TALL_FERN", "WET_FERN", "GIANT_WET_FERN", "LARGE_FERN",
            "FROST_LEAF",
        ]
    },

    # Nettles / Bushy flowers
    "NETTLES_BUSHY": {
        "hue": 0, "hue_range": 180,
        "sat": (45, 85), "val": (40, 80),
        "terrains": [
            "BLUE_NETTLE", "CYAN_FESTUCA", "NETTLE", "ASHY_BUSH",
            "BUSHY_ORANGE_FERN", "POISONED_NETTLE", "PURPLE_NETTLE",
            "RED_FEATHER_LEAF", "PURPLE_FLOWERS", "BLOOD_LEAF",
            "BLOOD_ROSE", "AZURE_FERN", "AZURE_KELP",
        ]
    },

    # Flowers - wide hue spread
    "FLOWERS_COMMON": {
        "hue": 0, "hue_range": 180,
        "sat": (40, 85), "val": (45, 90),
        "terrains": [
            "YELLOW_ARID_FLOWER_BUSH", "BLUE_HIBISCUS", "BLUE_ALOE",
            "CYAN_ARID_FLOWER", "CYAN_HIBISCUS", "LINEN_WEED", "SANDY_LION",
            "JUNGLE_FLOWER", "LIME_SUCCULENT", "CHRYSANTHEMUM",
            "COMMON_ORANGE_FLOWER", "COMMON_PINK_FLOWER", "ALLIUM",
            "CARMINE_PATCHED_THORN", "COMMON_PINK_FLOWER_POISONED",
            "PURPLE_ARID_FLOWER", "LAVA_FLOWER", "RED_ARID_FLOWER",
            "POPPY", "CAMPANULA_FLOWER", "VIOLETS",
            "WHITE_HYDRANGEA", "DAISY",
        ]
    },
    "FLOWERS_FLAX_TALL": {
        "hue": 0, "hue_range": 180,
        "sat": (40, 80), "val": (50, 88),
        "terrains": [
            "YELLOW_HIBISCUS", "BLUE_FLAX", "FIRE_FLOWER", "DANDELION",
            "PINK_FLAX", "BERRY_FLAX", "SMALL_DAISIES", "LUCERNE",
            "HEMLOCK", "BLUE_CAVEWEED", "AZURE_FLOWER",
        ]
    },
    "ORCHIDS": {
        "hue": 0, "hue_range": 180,
        "sat": (50, 90), "val": (20, 90),
        "terrains": [
            "ORANGE_ORCHID", "PINK_ORCHID", "BLACK_ORCHID",
            "PURPLE_ORCHID", "RED_ORCHID", "WHITE_ORCHID", "YELLOW_ORCHID",
        ]
    },
    "FLOWERS_TALL": {
        "hue": 0, "hue_range": 180,
        "sat": (45, 85), "val": (45, 85),
        "terrains": [
            "POISONED_FLOWER", "DELPHINIUM", "BUSHY_CYAN_FERN",
            "CYAN_FLOWER", "PINK_CAMELLIA", "LAVENDER",
            "TALL_RED_RAFFLESIA", "LARKSPUR", "SUNFLOWER",
        ]
    },
    "WATER_PLANTS": {
        "hue": 0, "hue_range": 180,
        "sat": (40, 85), "val": (40, 85),
        "terrains": [
            "BLUE_WATER_LILY", "DUCKWEED", "WATER_LILY",
            "PURPLE_WATER_LILY", "RED_WATER_LILY", "WHITE_WATER_LILY",
        ]
    },

    # Grass plants (the plant versions, not soil)
    "GRASS_DRY_ARID": {
        "hue": 55, "hue_range": 15,
        "sat": (30, 70), "val": (35, 65),
        "terrains": [
            "DRY_GRASS_PLANT", "TALL_DRY_GRASS", "PLANT_GRASS_ARID",
            "SHORT_DRY_GRASS", "BUSHY_SAVANNA_GRASS",
        ]
    },
    "GRASS_GNARLED_ROCKY": {
        "hue": 90, "hue_range": 15,
        "sat": (30, 65), "val": (30, 60),
        "terrains": [
            "SHORT_CAVE_GRASS", "GNARLED_GRASS", "SHORT_GNARLED_GRASS",
            "TALL_GNARLED_GRASS", "ROCKY_GRASS", "SHORT_ROCKY_GRASS",
            "TALL_ROCKY_GRASS",
        ]
    },
    "GRASS_JUNGLE": {
        "hue": 130, "hue_range": 10,
        "sat": (50, 88), "val": (20, 50),
        "terrains": [
            "JUNGLE_GRASS", "SHORT_JUNGLE_GRASS", "TALL_JUNGLE_GRASS",
        ]
    },
    "GRASS_LUSH": {
        "hue": 125, "hue_range": 12,
        "sat": (45, 80), "val": (35, 65),
        "terrains": [
            "LUSH_GRASS_PLANT", "SHORT_LUSH_GRASS_PLANT",
            "TALL_LUSH_GRASS_PLANT",
        ]
    },
    "GRASS_POISON": {
        "hue": 75, "hue_range": 10,
        "sat": (15, 35), "val": (25, 40),
        "terrains": ["POISON_GRASS", "SHORT_POISON_GRASS"]
    },
    "GRASS_SHARP": {
        "hue": 120, "hue_range": 15,
        "sat": (40, 80), "val": (28, 60),
        "terrains": [
            "GRASS_PLANT", "OVERGROWN_SHARP_GRASS", "SHORT_SHARP_GRASS",
            "TALL_GRASS", "WILD_SHARP_GRASS",
        ]
    },
    "GRASS_SNOWY": {
        "hue": 170, "hue_range": 15,
        "sat": (8, 25), "val": (82, 95),
        "terrains": ["SNOWY_GRASS", "SHORT_SNOWY_GRASS", "TALL_SNOWY_GRASS"]
    },
    "GRASS_WET": {
        "hue": 115, "hue_range": 12,
        "sat": (45, 82), "val": (28, 62),
        "terrains": [
            "WET_GRASS_PLANT", "OVERGROWN_WET_GRASS", "SHORT_WET_GRASS",
            "TALL_WET_GRASS", "WILD_WET_GRASS",
        ]
    },
    "GRASS_WINTER": {
        "hue": 140, "hue_range": 15,
        "sat": (8, 20), "val": (48, 62),
        "terrains": ["WINTER_GRASS", "SHORT_WINTER_GRASS", "TALL_WINTER_GRASS"]
    },

    # Moss variants
    "MOSS_MAIN": {
        "hue": 0, "hue_range": 180,
        "sat": (45, 85), "val": (30, 70),
        "terrains": [
            "BLUE_MOSS", "MOSS", "DARK_GREEN_MOSS",
            "RED_MOSS", "YELLOW_MOSS",
        ]
    },
    "MOSS_HANGING": {
        "hue": 0, "hue_range": 180,
        "sat": (50, 85), "val": (30, 75),
        "terrains": [
            "BLUE_HANGING_MOSS", "GREEN_HANGING_MOSS",
            "DARK_GREEN_HANGING_MOSS", "RED_HANGING_MOSS",
            "YELLOW_HANGING_MOSS",
        ]
    },
    "MOSS_RUGS": {
        "hue": 0, "hue_range": 180,
        "sat": (40, 80), "val": (35, 75),
        "terrains": [
            "BLUE_MOSS_RUG", "GREEN_MOSS_RUG", "DARK_GREEN_MOSS_RUG",
            "SORREL_RUG", "PINK_MOSS_RUG", "RED_MOSS_RUG", "YELLOW_MOSS_RUG",
        ]
    },
    "MOSS_SHORT": {
        "hue": 0, "hue_range": 180,
        "sat": (50, 88), "val": (25, 75),
        "terrains": [
            "SHORT_BLUE_MOSS", "SHORT_MOSS", "SHORT_DARK_GREEN_MOSS",
            "SHORT_RED_MOSS", "SHORT_YELLOW_MOSS",
        ]
    },
    "MOSS_BLOCKS": {
        "hue": 0, "hue_range": 180,
        "sat": (50, 85), "val": (30, 70),
        "terrains": [
            "YELLOW_MOSS_BLOCK", "RED_MOSS_BLOCK", "DARK_GREEN_MOSS_BLOCK",
            "BLUE_MOSS_BLOCK", "GREEN_MOSS_BLOCK",
        ]
    },

    # Reeds
    "REEDS": {
        "hue": 0, "hue_range": 180,
        "sat": (25, 70), "val": (30, 65),
        "terrains": [
            "PAPYRUS_REEDS", "LAVA_REEDS", "RIVER_REEDS", "POISON_REEDS",
            "TALL_WATER_REEDS", "WET_REEDS", "WINTER_REEDS",
        ]
    },

    # Cave roots
    "CAVE_ROOTS": {
        "hue": 80, "hue_range": 30,
        "sat": (25, 50), "val": (30, 50),
        "terrains": ["LEAFY_CAVE_ROOTS", "CAVE_ROOTS"]
    },

    # Seaweed
    "SEAWEED_ARID": {
        "hue": 40, "hue_range": 20,
        "sat": (30, 70), "val": (35, 65),
        "terrains": [
            "RED_ARID_SEAWEED", "ARID_SEAWEED_STACK", "SHORT_ARID_SEAWEED",
            "TALL_ARID_SEAWEED", "YELLOW_ARID_SEAWEED",
        ]
    },
    "SEAWEED_DEAD": {
        "hue": 30, "hue_range": 20,
        "sat": (5, 25), "val": (25, 60),
        "terrains": [
            "EERIE_DEAD_SEAWEED", "GHOSTLY_DEAD_SEAWEED",
            "SHORT_DEAD_SEAWEED", "DEAD_SEAWEED_STACK", "TALL_DEAD_SEAWEED",
        ]
    },
    "SEAWEED_GREEN": {
        "hue": 135, "hue_range": 12,
        "sat": (40, 75), "val": (25, 50),
        "terrains": [
            "SHORT_SEAWEED", "GREEN_SEAWEED_BULBS", "GREEN_SEAWEED",
            "SEAWEED_MIDDLE", "TALL_SEAWEED", "WET_SEAWEED",
        ]
    },
    "SEAWEED_WINTER": {
        "hue": 200, "hue_range": 20,
        "sat": (15, 50), "val": (45, 70),
        "terrains": [
            "AURORA_SEAWEED", "BLUE_WINTER_SEAWEED",
            "SHORT_WINTER_SEAWEED", "WINTER_SEAWEED_STACK",
            "TALL_WINTER_SEAWEED",
        ]
    },

    # Vine rug
    "VINE": {
        "hue": 120, "hue_range": 5,
        "sat": (60, 70), "val": (55, 60),
        "terrains": ["VINE_RUG"]
    },
}


def assign_colours_for_named_terrains(category_name, terrains, base_hue, hue_range, sat_range, val_range):
    """For colour-named terrains (coral, moss, crystals), assign the obvious colour."""
    colour_hues = {
        "BLUE": (220, 70, 60), "CYAN": (185, 65, 65), "GREEN": (130, 70, 55),
        "GRAY": (0, 5, 55), "GREY": (0, 5, 55), "LIME": (90, 75, 65),
        "LIME_GREEN": (90, 70, 65),
        "ORANGE": (25, 85, 80), "PINK": (340, 55, 80), "PURPLE": (275, 70, 60),
        "POISONED": (80, 30, 35), "POISON": (80, 30, 35),
        "RED": (5, 80, 70), "VIOLET": (260, 60, 60), "WHITE": (0, 5, 93),
        "YELLOW": (50, 80, 75), "NEON": (100, 90, 85), "BLACK": (0, 5, 15),
        "BROWN": (25, 50, 45), "DARK_GREEN": (135, 75, 35),
        "AMBER": (30, 75, 70), "AUTUMN": (18, 80, 72),
        "FIRE": (10, 90, 80), "DRY": (45, 40, 55), "DEAD": (30, 20, 40),
        "BURNED": (20, 35, 30), "BURNT": (20, 35, 30),
        "CRYSTAL": (200, 40, 80), "SNOWY": (195, 15, 88), "PETRIFIED": (60, 15, 40),
        "AZURE": (210, 55, 70), "SHALLOW": (55, 65, 70),
        "BLOOD": (0, 80, 65), "STORM": (220, 25, 70),
        "SPOTTED_GREEN": (100, 60, 55), "SPOTTED_ALLIUM": (290, 40, 50),
    }

    # Use a category-unique seed for offsets
    cat_seed = sum(ord(c) for c in category_name)

    results = {}
    unmatched = []
    # Track how many times each colour key is used within this category
    colour_key_count = {}

    for t in terrains:
        matched = False
        for colour_key, (h, s, v) in sorted(colour_hues.items(), key=lambda x: -len(x[0])):
            if colour_key in t:
                # Track index for this colour within category
                idx = colour_key_count.get(colour_key, 0)
                colour_key_count[colour_key] = idx + 1

                # Apply category-specific and index-specific offsets
                h_off = (cat_seed * 7) % 15 - 7 + idx * 5
                s_off = (cat_seed * 3) % 20 - 10 + idx * 8
                v_off = (cat_seed * 5) % 16 - 8 - idx * 6

                h2 = (h + h_off) % 360
                s2 = max(5, min(100, s + s_off))
                v2 = max(10, min(100, v + v_off))
                results[t] = hsv_to_hex(h2, s2, v2)
                matched = True
                break
        if not matched:
            unmatched.append(t)

    # Handle unmatched with spread_colors
    if unmatched:
        colors = spread_colors(base_hue, hue_range, len(unmatched), sat_range, val_range)
        for i, t in enumerate(unmatched):
            results[t] = colors[i]

    return results


def main():
    with open(JAVA_FILE, 'r', encoding='utf-8') as f:
        content = f.read()

    # Collect all colour assignments
    all_colours = {}

    # Manual overrides for key terrains that need natural-looking colours
    MANUAL_COLOURS = {
        # Ground grass — must be distinct greens
        "GRASS": "0x59a52c", "FULL_GRASS": "0x3cb820", "DEEP_GRASS": "0x1f6e12",
        "SUMMER_GRASS": "0x8abf30", "WET_GRASS": "0x38964a", "COLD_GRASS": "0x5e855a",
        "DRY_GRASS": "0xa89940", "BURNED_GRASS": "0x5c4020",
        # Stones — distinct greys
        "STONE": "0x808080", "BASALT": "0x3a3a3a", "BASALT_COBBLE": "0x4a4046",
        "SHALE": "0x5a5a65", "SHALE_COBBLE": "0x686060", "SLATE": "0x505058",
        "SLATE_COBBLE": "0x585c56", "CRACKED_SLATE": "0x484848",
        "VOLCANIC_ROCK": "0x2e282c", "CRACKED_VOLCANIC_ROCK": "0x3a2020",
        "COLD_MAGMA": "0x180808", "MARBLE": "0xeeeef0", "QUARTZITE": "0xdcdce0",
        "CHALK": "0xf8f8fa", "MOSSY_STONE": "0x607850",
        "POISONED_VOLCANIC_ROCK": "0x3a4a2a",
        # Calcite/Salt
        "CALCITE": "0xdbd7ca", "CALCITE_COBBLE": "0xcbc7ba", "SALT_BLOCK": "0xf0e8e0",
        # Sand
        "SAND": "0xdbc497", "SANDSTONE": "0xd4c099", "SANDSTONE_BRICK_SMOOTH": "0xdcbc9d",
        "WHITE_SAND": "0xf4e8c6", "WHITE_SANDSTONE": "0xe8e0d0",
        "WHITE_SANDSTONE_BRICK_SMOOTH": "0xf0dcd4",
        "RED_SAND": "0xc4633c", "RED_SANDSTONE": "0xb45030",
        "RED_SANDSTONE_BRICK_SMOOTH": "0xbc4c34", "ASHEN_SAND": "0x908870",
        # Ice/Aqua
        "BLUE_ICE": "0xa0d0ff", "ICE": "0xc0e0f8",
        "AQUA_COBBLE": "0x4090a0", "AQUA_STONE": "0x50a0b0",
        # Moss (the plain one) — must be green
        "MOSS": "0x428640", "DARK_GREEN_MOSS": "0x2a5a20", "BLUE_MOSS": "0x3c68d4",
        "RED_MOSS": "0xcc3438", "YELLOW_MOSS": "0xdcc438",
    }
    for cat_name, cat_info in CATEGORIES.items():
        terrains = cat_info["terrains"]
        base_hue = cat_info["hue"]
        hue_range = cat_info["hue_range"]
        sat_range = cat_info.get("sat", (40, 90))
        val_range = cat_info.get("val", (30, 85))

        # For categories that have explicit colour names in terrain names (coral, moss, crystal, orchid, etc)
        has_colour_names = any(
            word in t for t in terrains
            for word in ["BLUE", "CYAN", "GREEN", "GRAY", "LIME", "ORANGE", "PINK",
                        "PURPLE", "POISONED", "POISON", "RED", "VIOLET", "WHITE",
                        "YELLOW", "NEON", "BLACK", "BROWN", "DARK_GREEN"]
        )

        if has_colour_names and hue_range >= 30:
            colours = assign_colours_for_named_terrains(cat_name, terrains, base_hue, hue_range, sat_range, val_range)
        else:
            colors = spread_colors(base_hue, hue_range, len(terrains), sat_range, val_range)
            colours = dict(zip(terrains, colors))

        all_colours.update(colours)

    # Apply manual overrides LAST so they take priority
    all_colours.update(MANUAL_COLOURS)

    # Ensure all colours are unique — nudge any duplicates
    used_colours = {}
    for terrain_name in list(all_colours.keys()):
        colour = all_colours[terrain_name]
        while colour in used_colours:
            # Parse and nudge
            val = int(colour, 16)
            r = (val >> 16) & 0xFF
            g = (val >> 8) & 0xFF
            b = val & 0xFF
            # Nudge each channel slightly in a deterministic direction
            r = max(0, min(255, r + 3))
            g = max(0, min(255, g + 7))
            b = max(0, min(255, b - 5))
            colour = f"0x{r:02x}{g:02x}{b:02x}"
        used_colours[colour] = terrain_name
        all_colours[terrain_name] = colour

    # Now apply the colours to the Java file
    # Terrain defs span two lines:
    #   public static final HytaleTerrain NAME = new HytaleTerrain("...",
    #       HytaleBlock.of("..."), 0xRRGGBB);
    changed = 0
    lines = content.split('\n')
    new_lines = []
    pending_terrain = None

    for line in lines:
        # Check if this line starts a terrain definition
        m = re.search(r'public static final HytaleTerrain (\w+)\s*=', line)
        if m:
            pending_terrain = m.group(1)

        # Check if this line has a hex colour (the second line of the definition)
        if pending_terrain and re.search(r'0x[0-9a-fA-F]{6}\)', line):
            if pending_terrain in all_colours:
                new_colour = all_colours[pending_terrain]
                old_line = line
                line = re.sub(r'0x[0-9a-fA-F]{6}\)', f'{new_colour})', line)
                if line != old_line:
                    changed += 1
            pending_terrain = None

        new_lines.append(line)

    print(f"Changed {changed} colour values out of {len(all_colours)} planned")

    # Check for any planned terrains not found
    found_terrains = set()
    for line in content.split('\n'):
        m = re.search(r'public static final HytaleTerrain (\w+)\s*=', line)
        if m:
            found_terrains.add(m.group(1))

    missing = set(all_colours.keys()) - found_terrains
    if missing:
        print(f"WARNING: {len(missing)} terrains in categories not found in Java file:")
        for m in sorted(missing):
            print(f"  {m}")

    uncategorised = found_terrains - set(all_colours.keys())
    if uncategorised:
        print(f"WARNING: {len(uncategorised)} terrains in Java file not in any category:")
        for u in sorted(uncategorised):
            print(f"  {u}")

    with open(JAVA_FILE, 'w', encoding='utf-8') as f:
        f.write('\n'.join(new_lines))

    print("Done!")


if __name__ == "__main__":
    main()
