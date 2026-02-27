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
     * Deduplicate a Minecraft {@link Terrain} list for Hytale UI usage.
     * <p>
     * Multiple Minecraft terrains map to the same Hytale block (for example
     * stained clay variants). Showing all of them in Hytale mode produces
     * duplicate-looking entries. This method keeps one representative terrain
     * per mapped Hytale terrain while preserving configured custom terrains.
     *
     * @param terrains The source terrains to reduce.
     * @return A deduplicated array suitable for Hytale combo boxes.
     */
    public static Terrain[] deduplicateForHytaleUi(Terrain[] terrains) {
        if ((terrains == null) || (terrains.length == 0)) {
            return new Terrain[0];
        }

        final Map<HytaleTerrain, Terrain> representatives = new LinkedHashMap<>();
        final List<Terrain> customTerrains = new ArrayList<>();
        final Set<Terrain> availableTerrains = new HashSet<>(Arrays.asList(terrains));

        for (Terrain terrain : terrains) {
            if (terrain == null) {
                continue;
            }
            if (terrain.isCustom()) {
                customTerrains.add(terrain);
            } else {
                final HytaleTerrain mapped = fromMinecraftTerrain(terrain);
                representatives.putIfAbsent(mapped, terrain);
            }
        }

        final List<Terrain> result = new ArrayList<>();
        for (HytaleTerrain hytaleTerrain : HytaleTerrain.PICK_LIST) {
            final Terrain representative = representatives.get(hytaleTerrain);
            if (representative != null) {
                if (! result.contains(representative)) {
                    result.add(representative);
                }
            } else {
                final Terrain fallback = toMinecraftTerrain(hytaleTerrain);
                if (availableTerrains.contains(fallback) && (! result.contains(fallback))) {
                    result.add(fallback);
                }
            }
        }

        for (Terrain representative : representatives.values()) {
            if (! result.contains(representative)) {
                result.add(representative);
            }
        }
        for (Terrain customTerrain : customTerrains) {
            if (! result.contains(customTerrain)) {
                result.add(customTerrain);
            }
        }

        return result.toArray(new Terrain[0]);
    }

    /**
     * Get the display name for a terrain object (either Terrain or HytaleTerrain).
     *
     * @param terrain The terrain object
     * @return Display name string
     */
    public static String getDisplayName(Object terrain) {
        if (terrain instanceof HytaleTerrain) {
            return ((HytaleTerrain) terrain).getName();
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

        // Sand/Desert
        if (name.contains("red sand")) return HytaleTerrain.RED_SAND;
        if (name.contains("red desert")) return HytaleTerrain.RED_SAND;
        if (name.contains("desert")) return HytaleTerrain.SAND;
        if (name.contains("mesa")) return HytaleTerrain.RED_SAND;
        if (name.contains("sand")) return HytaleTerrain.SAND;

        // Snow/Ice
        if (name.contains("snow")) return HytaleTerrain.ICE;
        if (name.contains("ice")) return HytaleTerrain.ICE;

        // Rock types
        if (name.contains("obsidian")) return HytaleTerrain.COLD_MAGMA;
        if (name.contains("basalt")) return HytaleTerrain.BASALT;
        if (name.contains("blackstone")) return HytaleTerrain.VOLCANIC_ROCK;
        if (name.contains("deepslate")) return HytaleTerrain.SLATE;
        if (name.contains("tuff")) return HytaleTerrain.SHALE;
        if (name.contains("calcite")) return HytaleTerrain.CALCITE;
        if (name.contains("granite")) return HytaleTerrain.SHALE;
        if (name.contains("diorite")) return HytaleTerrain.QUARTZITE;
        if (name.contains("andesite")) return HytaleTerrain.SLATE;
        if (name.contains("stone mix")) return HytaleTerrain.STONE;
        if (name.contains("cobblestone")) return HytaleTerrain.MOSSY_STONE;
        if (name.contains("red sandstone")) return HytaleTerrain.RED_SANDSTONE;
        if (name.contains("sandstone")) return HytaleTerrain.SANDSTONE;
        if (name.equals("rock") || name.equals("stone")) return HytaleTerrain.STONE;

        // Earth
        if (name.contains("gravel")) return HytaleTerrain.STONE;
        if (name.contains("clay")) return HytaleTerrain.STONE;
        if (name.contains("mud")) return HytaleTerrain.WET_GRASS;
        if (name.contains("podzol")) return HytaleTerrain.DEEP_GRASS;
        if (name.contains("dirt") || name.contains("permadirt")) return HytaleTerrain.DIRT;
        if (name.contains("mycelium")) return HytaleTerrain.DEEP_GRASS;
        if (name.contains("magma")) return HytaleTerrain.COLD_MAGMA;

        // Grass types
        if (name.contains("grass")) return HytaleTerrain.GRASS;

        // Beach
        if (name.contains("beach")) return HytaleTerrain.SAND;

        // Nether
        if (name.contains("netherrack") || name.contains("netherlike")) return HytaleTerrain.VOLCANIC_ROCK;
        if (name.contains("soul")) return HytaleTerrain.BURNED_GRASS;
        if (name.contains("nylium")) return HytaleTerrain.GRASS;

        // End
        if (name.contains("end stone")) return HytaleTerrain.CHALK;

        // Stained clay / terracotta
        if (name.contains("stained clay") || name.contains("terracotta") || name.contains("hardened clay")) {
            return HytaleTerrain.RED_SANDSTONE;
        }

        // Resources (mixed ore terrain)
        if (name.equals("resources")) return HytaleTerrain.STONE;

        // Lava / Water - map to stone/grass since we removed fluid terrains
        if (name.contains("lava")) return HytaleTerrain.VOLCANIC_ROCK;
        if (name.contains("water")) return HytaleTerrain.GRASS;

        // Bedrock - map to basalt
        if (name.contains("bedrock")) return HytaleTerrain.BASALT;

        // Moss
        if (name.contains("moss")) return HytaleTerrain.MOSSY_STONE;

        return HytaleTerrain.GRASS;
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

        // Ground blocks
        map.put(HytaleTerrain.GRASS, Terrain.GRASS);
        map.put(HytaleTerrain.FULL_GRASS, Terrain.GRASS);
        map.put(HytaleTerrain.DEEP_GRASS, Terrain.GRASS);
        map.put(HytaleTerrain.SUMMER_GRASS, Terrain.GRASS);
        map.put(HytaleTerrain.WET_GRASS, Terrain.GRASS);
        map.put(HytaleTerrain.COLD_GRASS, Terrain.GRASS);
        map.put(HytaleTerrain.DRY_GRASS, Terrain.GRASS);
        map.put(HytaleTerrain.BURNED_GRASS, Terrain.PERMADIRT);
        map.put(HytaleTerrain.DIRT, Terrain.DIRT);
        map.put(HytaleTerrain.BURNT_DIRT, Terrain.PERMADIRT);
        map.put(HytaleTerrain.COLD_DIRT, Terrain.DIRT);
        map.put(HytaleTerrain.DRY_DIRT, Terrain.PERMADIRT);
        map.put(HytaleTerrain.POISONED_DIRT, Terrain.PERMADIRT);
        map.put(HytaleTerrain.SAND, Terrain.SAND);
        map.put(HytaleTerrain.RED_SAND, Terrain.RED_SAND);
        map.put(HytaleTerrain.WHITE_SAND, Terrain.SAND);
        map.put(HytaleTerrain.ASHEN_SAND, Terrain.SAND);
        map.put(HytaleTerrain.STONE, Terrain.STONE);
        map.put(HytaleTerrain.MOSSY_STONE, Terrain.MOSSY_COBBLESTONE);
        map.put(HytaleTerrain.SANDSTONE, Terrain.SANDSTONE);
        map.put(HytaleTerrain.SANDSTONE_BRICK_SMOOTH, Terrain.SANDSTONE);
        map.put(HytaleTerrain.RED_SANDSTONE, Terrain.RED_SANDSTONE);
        map.put(HytaleTerrain.RED_SANDSTONE_BRICK_SMOOTH, Terrain.RED_SANDSTONE);
        map.put(HytaleTerrain.WHITE_SANDSTONE, Terrain.SANDSTONE);
        map.put(HytaleTerrain.WHITE_SANDSTONE_BRICK_SMOOTH, Terrain.SANDSTONE);
        map.put(HytaleTerrain.SHALE, Terrain.TUFF);
        map.put(HytaleTerrain.SHALE_COBBLE, Terrain.TUFF);
        map.put(HytaleTerrain.SLATE, Terrain.DEEPSLATE);
        map.put(HytaleTerrain.SLATE_COBBLE, Terrain.DEEPSLATE);
        map.put(HytaleTerrain.CRACKED_SLATE, Terrain.DEEPSLATE);
        map.put(HytaleTerrain.BASALT, Terrain.BASALT);
        map.put(HytaleTerrain.BASALT_COBBLE, Terrain.BASALT);
        map.put(HytaleTerrain.AQUA_STONE, Terrain.STONE);
        map.put(HytaleTerrain.AQUA_COBBLE, Terrain.STONE);
        map.put(HytaleTerrain.MARBLE, Terrain.DIORITE);
        map.put(HytaleTerrain.QUARTZITE, Terrain.DIORITE);
        map.put(HytaleTerrain.CALCITE, Terrain.CALCITE);
        map.put(HytaleTerrain.CALCITE_COBBLE, Terrain.CALCITE);
        map.put(HytaleTerrain.CHALK, Terrain.END_STONE);
        map.put(HytaleTerrain.SALT_BLOCK, Terrain.END_STONE);
        map.put(HytaleTerrain.VOLCANIC_ROCK, Terrain.NETHERRACK);
        map.put(HytaleTerrain.CRACKED_VOLCANIC_ROCK, Terrain.NETHERRACK);
        map.put(HytaleTerrain.POISONED_VOLCANIC_ROCK, Terrain.NETHERRACK);
        map.put(HytaleTerrain.COLD_MAGMA, Terrain.OBSIDIAN);
        map.put(HytaleTerrain.ICE, Terrain.DEEP_SNOW);
        map.put(HytaleTerrain.BLUE_ICE, Terrain.DEEP_SNOW);

        // Crystals
        map.put(HytaleTerrain.BLUE_CRYSTAL, Terrain.STONE);
        map.put(HytaleTerrain.CYAN_CRYSTAL, Terrain.STONE);
        map.put(HytaleTerrain.GREEN_CRYSTAL, Terrain.STONE);
        map.put(HytaleTerrain.PINK_CRYSTAL, Terrain.STONE);
        map.put(HytaleTerrain.PURPLE_CRYSTAL, Terrain.STONE);
        map.put(HytaleTerrain.RED_CRYSTAL, Terrain.STONE);
        map.put(HytaleTerrain.WHITE_CRYSTAL, Terrain.STONE);
        map.put(HytaleTerrain.YELLOW_CRYSTAL, Terrain.STONE);

        // All vegetation terrains map to GRASS (they are decorative overlay blocks)
        // The Minecraft terrain simply holds the terrain type for painting;
        // the actual block is resolved from HytaleTerrain during export.

        HYTALE_TO_MC = map;
    }

    private static volatile Map<HytaleTerrain, Terrain> HYTALE_TO_MC;
}
