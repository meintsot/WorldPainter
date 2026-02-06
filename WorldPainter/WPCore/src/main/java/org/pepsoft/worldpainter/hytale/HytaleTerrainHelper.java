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
}
