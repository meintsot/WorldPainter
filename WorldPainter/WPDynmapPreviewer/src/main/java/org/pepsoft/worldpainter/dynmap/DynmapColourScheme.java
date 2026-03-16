/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.pepsoft.worldpainter.dynmap;

import org.dynmap.ColorScheme;
import org.dynmap.renderer.DynmapBlockState;
import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.ColourScheme;
import org.pepsoft.worldpainter.hytale.HytaleBlockRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.pepsoft.minecraft.Constants.MC_WATER;
import static org.pepsoft.minecraft.Material.WATER;

/**
 * An implementation of {@link ColourScheme} which delegates to a Dynmap {@link ColorScheme}.
 *
 * @author pepijn
 */
public final class DynmapColourScheme implements ColourScheme {
    private DynmapColourScheme(ColorScheme dynmapColorScheme, int step) {
        this.dynmapColorScheme = dynmapColorScheme;
        this.step = step;
    }

    @Override
    public int getColour(Material material) {
        // TODO: optimise this further?
        return cache.computeIfAbsent(material, k -> {
            if (material.isNamed(MC_WATER)) {
                // TODO find out how to properly determine water colour from Dynmap
                return WATER.colour;
            }
            // Check for Hytale namespace blocks — these don't have Dynmap block states
            if (material.namespace.equals(HytaleBlockRegistry.HYTALE_NAMESPACE)) {
                return getHytaleBlockColour(material);
            }
            final DynmapBlockState blockState = DynmapBlockStateHelper.getDynmapBlockState(material);
            if (blockState != null) {
                if (blockState.globalStateIndex < dynmapColorScheme.colors.length) {
                    if (dynmapColorScheme.colors[blockState.globalStateIndex] != null) {
                        return dynmapColorScheme.colors[blockState.globalStateIndex][step].getARGB();
                    } else {
                        logger.warn("Colour table contains null for global state index {}\nMaterial: {}\nDynmapBlockState: {}", blockState.globalStateIndex, material.toFullString(), blockState);
                    }
                } else {
                    logger.warn("Global state index {} exceeds colour table bounds\nMaterial: {}\nDynmapBlockState: {}", blockState.globalStateIndex, material.toFullString(), blockState);
                }
            } else {
                logger.warn("DynmapBlockState missing for material: {}", material.toFullString());
            }
            return material.colour;
        });
    }

    /**
     * Get the colour for a Hytale block material. First checks the
     * {@link HytaleBlockRegistry} for a known block colour (derived from
     * HytaleTerrain data), then falls back to a category-based default.
     */
    private int getHytaleBlockColour(Material material) {
        // Try the terrain-based colour map first
        Integer colour = HytaleBlockRegistry.getBlockColour(material.simpleName);
        if (colour != null) {
            return colour;
        }
        // Fall back to category-based colours
        HytaleBlockRegistry.Category category = HytaleBlockRegistry.getCategoryForBlock(material.simpleName);
        if (category != null) {
            return getCategoryColour(category);
        }
        // Name-based heuristics as a last resort
        return guessColourFromName(material.simpleName);
    }

    private static int getCategoryColour(HytaleBlockRegistry.Category category) {
        switch (category) {
            case SOIL:              return 0xFF8B6914; // brown
            case SAND:              return 0xFFE8D5A3; // sandy
            case CLAY:              return 0xFFC4A882; // clay tan
            case SNOW_ICE:          return 0xFFEEF0F0; // white
            case GRAVEL:            return 0xFF9E9E9E; // grey
            case ROCK:              return 0xFF808080; // grey
            case ROCK_CONSTRUCTION: return 0xFFAAAAAA; // light grey
            case ORE:               return 0xFF8A7A6A; // brownish grey
            case CRYSTAL_GEM:       return 0xFF9ABDE0; // light blue
            case WOOD_NATURAL:      return 0xFF8B6B3A; // wood brown
            case WOOD_PLANKS:       return 0xFFB89560; // light wood
            case LEAVES:            return 0xFF3A7A28; // green
            case GRASS_PLANTS:      return 0xFF4C9A2A; // bright green
            case FLOWERS:           return 0xFFD25B9A; // pink
            case FERNS:             return 0xFF3E8A30; // dark green
            case BUSHES:            return 0xFF2E6A20; // dark green
            case CACTUS:            return 0xFF5A8A40; // cactus green
            case MOSS_VINES:        return 0xFF4A7A30; // moss green
            case MUSHROOMS:         return 0xFFA06040; // mushroom brown
            case CROPS:             return 0xFF8AAA40; // crop green/yellow
            case CORAL:             return 0xFFE08060; // coral orange
            case SEAWEED:           return 0xFF2A7A50; // aquatic green
            case SAPLINGS_FRUITS:   return 0xFF6AAA30; // light green
            case RUBBLE:            return 0xFF7A7A6A; // rubble grey
            case DECORATION:        return 0xFFAA8A60; // decoration brown
            case CLOTH:             return 0xFFD0C0B0; // cloth beige
            case HIVE:              return 0xFFC8A840; // hive gold
            case RUNIC:             return 0xFF6060AA; // runic blue
            case FLUID:             return 0xFF3070D0; // water blue
            case SPECIAL:           return 0xFF505050; // dark grey
            default:                return 0xFFA0A0A0; // neutral grey
        }
    }

    /**
     * Guess a colour from the block name when no category or terrain colour is available.
     */
    private static int guessColourFromName(String name) {
        String lower = name.toLowerCase();
        if (lower.contains("stone") || lower.contains("rock") || lower.contains("cobble")) {
            return 0xFF808080;
        } else if (lower.contains("dirt") || lower.contains("soil") || lower.contains("mud")) {
            return 0xFF8B6914;
        } else if (lower.contains("grass")) {
            return 0xFF4C9A2A;
        } else if (lower.contains("sand")) {
            return 0xFFE8D5A3;
        } else if (lower.contains("wood") || lower.contains("log") || lower.contains("trunk") || lower.contains("bark")) {
            return 0xFF8B6B3A;
        } else if (lower.contains("plank")) {
            return 0xFFB89560;
        } else if (lower.contains("leaf") || lower.contains("leaves")) {
            return 0xFF3A7A28;
        } else if (lower.contains("snow") || lower.contains("ice")) {
            return 0xFFEEF0F0;
        } else if (lower.contains("water") || lower.contains("fluid")) {
            return 0xFF3070D0;
        } else if (lower.contains("iron") || lower.contains("metal")) {
            return 0xFFC0C0C0;
        } else if (lower.contains("gold")) {
            return 0xFFD4AA30;
        } else if (lower.contains("flower") || lower.contains("rose") || lower.contains("tulip")) {
            return 0xFFD25B9A;
        }
        return 0xFFA0A0A0; // neutral grey as final fallback
    }

    public static DynmapColourScheme loadDynMapColourScheme(String name, int step) {
        DynmapBlockStateHelper.initialise();
        final ColorScheme colorScheme = ColorScheme.getScheme(null, name);
        return new DynmapColourScheme(colorScheme, step);
    }

    private final ColorScheme dynmapColorScheme;
    private final int step;
    private final Map<Material, Integer> cache = new ConcurrentHashMap<>();

    private static final Logger logger = LoggerFactory.getLogger(DynmapColourScheme.class);
}