package org.pepsoft.worldpainter.hytale;

import org.pepsoft.worldpainter.DefaultPlugin;
import org.pepsoft.worldpainter.Platform;
import org.pepsoft.worldpainter.Terrain;

import java.util.*;

/**
 * Helper class that bridges between Minecraft-native {@link Terrain} enum and
 * Hytale-native {@link HytaleTerrain} instances. Used by UI components that need
 * to present the correct terrain list depending on the active platform.
 *
 * <p>When the active platform is Hytale, terrain combo boxes should show
 * {@link HytaleTerrain#PICK_LIST} terrains. When Minecraft, they show
 * {@link Terrain#PICK_LIST} as usual.
 *
 * <p>This class also maps Minecraft terrains to their closest Hytale equivalents
 * for export purposes.
 */
public final class HytaleTerrainHelper {

    private HytaleTerrainHelper() {} // Utility class, no instances

    /**
     * Check if the given platform is the Hytale platform.
     *
     * @param platform The platform to check
     * @return true if the platform is the Hytale platform
     */
    public static boolean isHytale(Platform platform) {
        return platform != null && DefaultPlugin.HYTALE.id.equals(platform.id);
    }

    /**
     * Get the pick list items appropriate for the given platform.
     * Returns an Object array that can be used with JComboBox models.
     * For Hytale: HytaleTerrain[] from HytaleTerrain.PICK_LIST
     * For Minecraft: Terrain[] from Terrain.PICK_LIST
     *
     * @param platform The active platform
     * @return Array of terrain objects for the platform
     */
    public static Object[] getPickList(Platform platform) {
        if (isHytale(platform)) {
            return HytaleTerrain.PICK_LIST;
        } else {
            return Terrain.PICK_LIST;
        }
    }

    /**
     * Get the display name for a terrain object (either Terrain or HytaleTerrain).
     *
     * @param terrain The terrain object
     * @return Display name string
     */
    public static String getDisplayName(Object terrain) {
        if (terrain instanceof HytaleTerrain) {
            HytaleTerrain ht = (HytaleTerrain) terrain;
            if (ht.getBiome() != null) {
                return ht.getName() + " (" + ht.getBiome() + ")";
            }
            return ht.getName();
        } else if (terrain instanceof Terrain) {
            return ((Terrain) terrain).getName();
        }
        return terrain != null ? terrain.toString() : "";
    }

    /**
     * Get the colour for a terrain object (either Terrain or HytaleTerrain).
     *
     * @param terrain The terrain object
     * @return RGB colour value
     */
    public static int getTerrainColour(Object terrain) {
        if (terrain instanceof HytaleTerrain) {
            return ((HytaleTerrain) terrain).getEffectiveColour();
        }
        return 0x808080; // Default grey for Terrain (which has its own colour scheme)
    }

    /**
     * Map a Minecraft Terrain to the closest Hytale equivalent.
     *
     * @param terrain Minecraft terrain type
     * @return The closest HytaleTerrain, or GRASS as fallback
     */
    public static HytaleTerrain fromMinecraftTerrain(Terrain terrain) {
        if (terrain == null) return HytaleTerrain.GRASS;

        String name = terrain.getName().toLowerCase();

        // Direct name matches
        if (name.contains("lava")) return HytaleTerrain.LAVA;
        if (name.contains("water")) return HytaleTerrain.WATER;

        // Sand/Desert
        if (name.contains("red sand")) return HytaleTerrain.SAND_RED;
        if (name.contains("red desert")) return HytaleTerrain.ZONE2_RED_DESERT;
        if (name.contains("desert")) return HytaleTerrain.ZONE2_DESERT;
        if (name.contains("mesa")) return HytaleTerrain.ZONE2_MESA;
        if (name.contains("sand")) return HytaleTerrain.SAND;

        // Snow/Ice
        if (name.contains("deep snow")) return HytaleTerrain.ZONE3_TUNDRA;
        if (name.contains("snow")) return HytaleTerrain.SNOW;
        if (name.contains("ice")) return HytaleTerrain.ICE;

        // Rock types
        if (name.contains("bedrock")) return HytaleTerrain.BEDROCK;
        if (name.contains("obsidian")) return HytaleTerrain.MAGMA_COOLED;
        if (name.contains("basalt")) return HytaleTerrain.BASALT;
        if (name.contains("blackstone")) return HytaleTerrain.VOLCANIC;
        if (name.contains("deepslate")) return HytaleTerrain.SLATE;
        if (name.contains("tuff")) return HytaleTerrain.SHALE;
        if (name.contains("calcite")) return HytaleTerrain.CALCITE;
        if (name.contains("granite")) return HytaleTerrain.SHALE;
        if (name.contains("diorite")) return HytaleTerrain.QUARTZITE;
        if (name.contains("andesite")) return HytaleTerrain.SLATE;
        if (name.contains("stone mix")) return HytaleTerrain.STONE_MIX;
        if (name.contains("cobblestone") && name.contains("mossy")) return HytaleTerrain.COBBLESTONE_MOSSY;
        if (name.contains("cobblestone")) return HytaleTerrain.COBBLESTONE;
        if (name.contains("red sandstone")) return HytaleTerrain.SANDSTONE_RED;
        if (name.contains("sandstone")) return HytaleTerrain.SANDSTONE;
        if (name.equals("rock") || name.equals("stone")) return HytaleTerrain.STONE;

        // Earth
        if (name.contains("gravel")) return HytaleTerrain.GRAVEL;
        if (name.contains("clay")) return HytaleTerrain.CLAY;
        if (name.contains("mud")) return HytaleTerrain.MUD;
        if (name.contains("podzol")) return HytaleTerrain.DIRT;
        if (name.contains("dirt") || name.contains("permadirt")) return HytaleTerrain.DIRT;
        if (name.contains("mycelium")) return HytaleTerrain.MUD;
        if (name.contains("moss")) return HytaleTerrain.GRAVEL_MOSSY;
        if (name.contains("magma")) return HytaleTerrain.MAGMA_COOLED;

        // Grass types
        if (name.contains("grass path")) return HytaleTerrain.DIRT;
        if (name.contains("bare grass")) return HytaleTerrain.GRASS;
        if (name.contains("grass")) return HytaleTerrain.GRASS;

        // Beach
        if (name.contains("beach")) return HytaleTerrain.ZONE1_BEACH;

        // Nether
        if (name.contains("netherrack") || name.contains("netherlike")) return HytaleTerrain.VOLCANIC;
        if (name.contains("soul")) return HytaleTerrain.MUD;
        if (name.contains("nylium")) return HytaleTerrain.GRASS;

        // End
        if (name.contains("end stone")) return HytaleTerrain.CHALK;

        // Stained clay → clay with colour
        if (name.contains("stained clay") || name.contains("terracotta") || name.contains("hardened clay")) {
            return HytaleTerrain.CLAY;
        }

        // Resources (mixed ore terrain)
        if (name.equals("resources")) return HytaleTerrain.STONE_MIX;

        return HytaleTerrain.GRASS;
    }

    /**
     * Map a Minecraft Terrain to a Hytale biome name.
     *
     * @param terrain Minecraft terrain
     * @return Hytale biome name
     */
    public static String getHytaleBiome(Terrain terrain) {
        HytaleTerrain ht = fromMinecraftTerrain(terrain);
        return ht.getBiome() != null ? ht.getBiome() : "Grassland";
    }

    /**
     * Map a HytaleTerrain to the closest Minecraft Terrain enum value.
     * This is used by the terrain toolbar buttons so clicking a Hytale terrain
     * sets the correct Terrain paint on tiles.
     *
     * @param hytaleTerrain The Hytale terrain type
     * @return The closest Minecraft Terrain enum value
     */
    public static Terrain toMinecraftTerrain(HytaleTerrain hytaleTerrain) {
        if (hytaleTerrain == null) return Terrain.GRASS;
        if (HYTALE_TO_MC == null) {
            buildReverseMap();
        }
        Terrain result = HYTALE_TO_MC.get(hytaleTerrain);
        return result != null ? result : Terrain.GRASS;
    }

    private static synchronized void buildReverseMap() {
        if (HYTALE_TO_MC != null) return;
        Map<HytaleTerrain, Terrain> map = new HashMap<>();
        // Soil / Surface
        map.put(HytaleTerrain.GRASS, Terrain.GRASS);
        map.put(HytaleTerrain.GRASS_LUSH, Terrain.GRASS);
        map.put(HytaleTerrain.GRASS_DRY, Terrain.BARE_GRASS);
        map.put(HytaleTerrain.GRASS_FROZEN, Terrain.GRASS);
        map.put(HytaleTerrain.DIRT, Terrain.PERMADIRT);
        map.put(HytaleTerrain.FARMLAND, Terrain.PERMADIRT);
        map.put(HytaleTerrain.SAND, Terrain.SAND);
        map.put(HytaleTerrain.SAND_RED, Terrain.RED_SAND);
        map.put(HytaleTerrain.SAND_WHITE, Terrain.SAND);
        map.put(HytaleTerrain.SNOW, Terrain.DEEP_SNOW);
        map.put(HytaleTerrain.GRAVEL, Terrain.GRAVEL);
        map.put(HytaleTerrain.GRAVEL_MOSSY, Terrain.GRAVEL);
        map.put(HytaleTerrain.CLAY, Terrain.CLAY);
        map.put(HytaleTerrain.MUD, Terrain.MUD);
        // Rock
        map.put(HytaleTerrain.STONE, Terrain.STONE);
        map.put(HytaleTerrain.COBBLESTONE, Terrain.COBBLESTONE);
        map.put(HytaleTerrain.COBBLESTONE_MOSSY, Terrain.MOSSY_COBBLESTONE);
        map.put(HytaleTerrain.SANDSTONE, Terrain.SANDSTONE);
        map.put(HytaleTerrain.SANDSTONE_RED, Terrain.RED_SANDSTONE);
        map.put(HytaleTerrain.SHALE, Terrain.TUFF);
        map.put(HytaleTerrain.SLATE, Terrain.DEEPSLATE);
        map.put(HytaleTerrain.BASALT, Terrain.BASALT);
        map.put(HytaleTerrain.MARBLE, Terrain.DIORITE);
        map.put(HytaleTerrain.QUARTZITE, Terrain.DIORITE);
        map.put(HytaleTerrain.CALCITE, Terrain.CALCITE);
        map.put(HytaleTerrain.CHALK, Terrain.END_STONE);
        map.put(HytaleTerrain.VOLCANIC, Terrain.NETHERRACK);
        map.put(HytaleTerrain.MAGMA_COOLED, Terrain.OBSIDIAN);
        map.put(HytaleTerrain.ICE, Terrain.DEEP_SNOW);
        map.put(HytaleTerrain.BEDROCK, Terrain.BEDROCK);
        // Zone-specific
        map.put(HytaleTerrain.ZONE1_GRASSLAND, Terrain.GRASS);
        map.put(HytaleTerrain.ZONE1_FOREST_FLOOR, Terrain.PODZOL);
        map.put(HytaleTerrain.ZONE1_MEADOW, Terrain.GRASS);
        map.put(HytaleTerrain.ZONE1_BEACH, Terrain.BEACHES);
        map.put(HytaleTerrain.ZONE1_RIVERBED, Terrain.CLAY);
        map.put(HytaleTerrain.ZONE2_DESERT, Terrain.DESERT);
        map.put(HytaleTerrain.ZONE2_RED_DESERT, Terrain.RED_DESERT);
        map.put(HytaleTerrain.ZONE2_MESA, Terrain.MESA);
        map.put(HytaleTerrain.ZONE2_OASIS, Terrain.GRASS);
        map.put(HytaleTerrain.ZONE2_SAVANNA, Terrain.BARE_GRASS);
        map.put(HytaleTerrain.ZONE3_TUNDRA, Terrain.DEEP_SNOW);
        map.put(HytaleTerrain.ZONE3_TAIGA, Terrain.PODZOL);
        map.put(HytaleTerrain.ZONE3_FROZEN_LAKE, Terrain.DEEP_SNOW);
        map.put(HytaleTerrain.ZONE3_SNOWY_PEAKS, Terrain.ROCK);
        map.put(HytaleTerrain.ZONE4_VOLCANIC_PLAINS, Terrain.NETHERRACK);
        map.put(HytaleTerrain.ZONE4_LAVA_FIELDS, Terrain.MAGMA);
        map.put(HytaleTerrain.ZONE4_ASH_WASTE, Terrain.NETHERLIKE);
        // Mixed natural
        map.put(HytaleTerrain.STONE_MIX, Terrain.STONE_MIX);
        map.put(HytaleTerrain.MOUNTAIN_ROCK, Terrain.ROCK);
        map.put(HytaleTerrain.SWAMP, Terrain.MUD);
        map.put(HytaleTerrain.TROPICAL_BEACH, Terrain.BEACHES);
        map.put(HytaleTerrain.OCEAN_FLOOR, Terrain.CLAY);
        // Layered
        map.put(HytaleTerrain.GRASSLAND_LAYERED, Terrain.GRASS);
        map.put(HytaleTerrain.DESERT_LAYERED, Terrain.DESERT);
        map.put(HytaleTerrain.TUNDRA_LAYERED, Terrain.DEEP_SNOW);
        // Fluids
        map.put(HytaleTerrain.WATER, Terrain.WATER);
        map.put(HytaleTerrain.LAVA, Terrain.LAVA);
        HYTALE_TO_MC = map;
    }

    private static volatile Map<HytaleTerrain, Terrain> HYTALE_TO_MC;
}
