package org.pepsoft.worldpainter.hytale;

import org.pepsoft.minecraft.Material;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps WorldPainter/Minecraft materials to Hytale block IDs (strings) and HytaleBlock objects.
 * 
 * <p>Hytale uses string-based block IDs like "Rock_Stone" whereas WorldPainter uses Minecraft-style materials.
 * Hytale block IDs have no namespace prefix - they use underscore-separated names like:
 * <ul>
 *   <li>Soil_Dirt, Soil_Grass, Soil_Sand</li>
 *   <li>Rock_Stone, Rock_Bedrock, Rock_Ice</li>
 *   <li>Wood_Oak_Trunk, Plant_Leaves_Oak</li>
 *   <li>Ore_Iron_Stone, Ore_Gold_Stone</li>
 *   <li>Water_Source, Lava_Source (fluids)</li>
 * </ul>
 * 
 * <p>This class provides bidirectional mapping between Minecraft materials and Hytale blocks,
 * as well as support for native HytaleBlock passthrough when working in Hytale-native mode.
 * 
 * @see HytaleBlock
 * @see HytaleBlockRegistry
 */
public class HytaleBlockMapping {
    
    private static final Map<String, String> MINECRAFT_TO_HYTALE = new HashMap<>();
    private static final Map<String, String> HYTALE_TO_MINECRAFT = new HashMap<>();
    
    // Block ID constants for Hytale (actual Hytale block names)
        public static final String HY_AIR = "Empty";  // Empty block
    public static final String HY_STONE = "Rock_Stone";
    public static final String HY_STONE_MOSSY = "Rock_Stone_Mossy";
    public static final String HY_COBBLESTONE = "Rock_Stone_Cobble";
    public static final String HY_COBBLESTONE_MOSSY = "Rock_Stone_Cobble_Mossy";
    public static final String HY_DIRT = "Soil_Dirt";
    public static final String HY_GRASS_BLOCK = "Soil_Grass";
    public static final String HY_SAND = "Soil_Sand";
    public static final String HY_SAND_RED = "Soil_Sand_Red";
    public static final String HY_SAND_WHITE = "Soil_Sand_White";
    public static final String HY_GRAVEL = "Soil_Gravel";
    public static final String HY_GRAVEL_MOSSY = "Soil_Gravel_Mossy";
    public static final String HY_WATER = "Water_Source";
    public static final String HY_LAVA = "Lava_Source";
    public static final String HY_BEDROCK = "Rock_Bedrock";
    public static final String HY_ICE = "Rock_Ice";
    public static final String HY_SNOW = "Soil_Snow";
    public static final String HY_CLAY = "Soil_Clay";
    public static final String HY_MUD = "Soil_Mud";
    public static final String HY_SANDSTONE = "Rock_Sandstone";
    public static final String HY_SANDSTONE_RED = "Rock_Sandstone_Red";
    public static final String HY_SANDSTONE_WHITE = "Rock_Sandstone_White";
    public static final String HY_SHALE = "Rock_Shale";
    public static final String HY_SLATE = "Rock_Slate";
    public static final String HY_BASALT = "Rock_Basalt";
    public static final String HY_MARBLE = "Rock_Marble";
    public static final String HY_QUARTZITE = "Rock_Quartzite";
    public static final String HY_CALCITE = "Rock_Calcite";
    public static final String HY_CHALK = "Rock_Chalk";
    public static final String HY_VOLCANIC = "Rock_Volcanic";
    public static final String HY_MAGMA_COOLED = "Rock_Magma_Cooled";  // Closest to obsidian
    
    // Wood types
    public static final String HY_OAK_LOG = "Wood_Oak_Trunk";
    public static final String HY_BIRCH_LOG = "Wood_Birch_Trunk";
    public static final String HY_FIR_LOG = "Wood_Fir_Trunk";
    public static final String HY_REDWOOD_LOG = "Wood_Redwood_Trunk";
    public static final String HY_PALM_LOG = "Wood_Palm_Trunk";
    public static final String HY_MAPLE_LOG = "Wood_Maple_Trunk";
    public static final String HY_CEDAR_LOG = "Wood_Cedar_Trunk";
    public static final String HY_ASPEN_LOG = "Wood_Aspen_Trunk";
    public static final String HY_ASH_LOG = "Wood_Ash_Trunk";
    public static final String HY_BEECH_LOG = "Wood_Beech_Trunk";
    public static final String HY_JUNGLE_LOG = "Wood_Jungle_Trunk";
    
    // Leaves types
    public static final String HY_OAK_LEAVES = "Plant_Leaves_Oak";
    public static final String HY_BIRCH_LEAVES = "Plant_Leaves_Birch";
    public static final String HY_FIR_LEAVES = "Plant_Leaves_Fir";
    public static final String HY_REDWOOD_LEAVES = "Plant_Leaves_Redwood";
    public static final String HY_PALM_LEAVES = "Plant_Leaves_Palm";
    public static final String HY_MAPLE_LEAVES = "Plant_Leaves_Maple";
    public static final String HY_CEDAR_LEAVES = "Plant_Leaves_Cedar";
    public static final String HY_JUNGLE_LEAVES = "Plant_Leaves_Jungle";
    
    // Planks
    public static final String HY_SOFTWOOD_PLANKS = "Wood_Softwood_Planks";
    public static final String HY_HARDWOOD_PLANKS = "Wood_Hardwood_Planks";
    
    // Ores (Hytale has ores embedded in rock type, e.g., Ore_Iron_Stone)
    public static final String HY_IRON_ORE = "Ore_Iron_Stone";
    public static final String HY_GOLD_ORE = "Ore_Gold_Stone";
    public static final String HY_COPPER_ORE = "Ore_Copper_Stone";
    public static final String HY_SILVER_ORE = "Ore_Silver_Stone";
    public static final String HY_COBALT_ORE = "Ore_Cobalt_Stone";
    public static final String HY_MITHRIL_ORE = "Ore_Mithril_Stone";
    public static final String HY_ADAMANTITE_ORE = "Ore_Adamantite_Stone";
    public static final String HY_THORIUM_ORE = "Ore_Thorium_Stone";
    public static final String HY_ONYXIUM_ORE = "Ore_Onyxium_Stone";
    
    // Plants
    public static final String HY_GRASS_PLANT = "Plant_Grass_Lush";
    public static final String HY_FLOWER = "Plant_Flower_Common_Yellow";
    public static final String HY_CACTUS = "Plant_Cactus_1";
    public static final String HY_FERN = "Plant_Fern";
    
    static {
        // Basic terrain blocks
        register("minecraft:air", HY_AIR);
        register("minecraft:cave_air", HY_AIR);
        register("minecraft:void_air", HY_AIR);
        register("minecraft:stone", HY_STONE);
        register("minecraft:dirt", HY_DIRT);
        register("minecraft:grass_block", HY_GRASS_BLOCK);
        register("minecraft:sand", HY_SAND);
        register("minecraft:red_sand", HY_SAND_RED);
        register("minecraft:gravel", HY_GRAVEL);
        register("minecraft:water", HY_WATER);
        register("minecraft:bedrock", HY_BEDROCK);
        register("minecraft:cobblestone", HY_COBBLESTONE);
        register("minecraft:mossy_cobblestone", HY_COBBLESTONE_MOSSY);
        
        // Wood blocks - map to closest Hytale equivalents
        register("minecraft:oak_log", HY_OAK_LOG);
        register("minecraft:spruce_log", HY_FIR_LOG);
        register("minecraft:birch_log", HY_BIRCH_LOG);
        register("minecraft:jungle_log", HY_JUNGLE_LOG);
        register("minecraft:acacia_log", HY_PALM_LOG);
        register("minecraft:dark_oak_log", HY_OAK_LOG);
        register("minecraft:mangrove_log", HY_JUNGLE_LOG);
        register("minecraft:cherry_log", HY_MAPLE_LOG);
        
        // Leaves
        register("minecraft:oak_leaves", HY_OAK_LEAVES);
        register("minecraft:spruce_leaves", HY_FIR_LEAVES);
        register("minecraft:birch_leaves", HY_BIRCH_LEAVES);
        register("minecraft:jungle_leaves", HY_JUNGLE_LEAVES);
        register("minecraft:acacia_leaves", HY_PALM_LEAVES);
        register("minecraft:dark_oak_leaves", HY_OAK_LEAVES);
        register("minecraft:mangrove_leaves", HY_JUNGLE_LEAVES);
        register("minecraft:cherry_leaves", HY_MAPLE_LEAVES);
        register("minecraft:azalea_leaves", HY_OAK_LEAVES);
        
        // Planks
        register("minecraft:oak_planks", HY_HARDWOOD_PLANKS);
        register("minecraft:spruce_planks", HY_SOFTWOOD_PLANKS);
        register("minecraft:birch_planks", HY_SOFTWOOD_PLANKS);
        register("minecraft:jungle_planks", HY_HARDWOOD_PLANKS);
        register("minecraft:acacia_planks", HY_HARDWOOD_PLANKS);
        register("minecraft:dark_oak_planks", HY_HARDWOOD_PLANKS);
        
        // Stone variants - map to closest Hytale rocks
        register("minecraft:granite", HY_SHALE);
        register("minecraft:diorite", HY_QUARTZITE);
        register("minecraft:andesite", HY_SLATE);
        register("minecraft:deepslate", HY_SLATE);
        register("minecraft:tuff", HY_VOLCANIC);
        register("minecraft:calcite", HY_CALCITE);
        register("minecraft:dripstone_block", HY_CALCITE);
        register("minecraft:basalt", HY_BASALT);
        register("minecraft:smooth_basalt", HY_BASALT);
        register("minecraft:blackstone", HY_VOLCANIC);
        
        // Other terrain
        register("minecraft:clay", HY_CLAY);
        register("minecraft:mud", HY_MUD);
        register("minecraft:muddy_mangrove_roots", HY_MUD);
        register("minecraft:sandstone", HY_SANDSTONE);
        register("minecraft:red_sandstone", HY_SANDSTONE_RED);
        register("minecraft:snow", HY_SNOW);
        register("minecraft:snow_block", HY_SNOW);
        register("minecraft:powder_snow", HY_SNOW);
        register("minecraft:ice", HY_ICE);
        register("minecraft:packed_ice", HY_ICE);
        register("minecraft:blue_ice", HY_ICE);
        register("minecraft:obsidian", HY_MAGMA_COOLED);
        register("minecraft:crying_obsidian", HY_MAGMA_COOLED);
        
        // Ores - map to Hytale equivalents
        register("minecraft:coal_ore", HY_STONE);  // No coal in Hytale, use stone
        register("minecraft:deepslate_coal_ore", HY_SLATE);
        register("minecraft:iron_ore", HY_IRON_ORE);
        register("minecraft:deepslate_iron_ore", HY_IRON_ORE);
        register("minecraft:gold_ore", HY_GOLD_ORE);
        register("minecraft:deepslate_gold_ore", HY_GOLD_ORE);
        register("minecraft:copper_ore", HY_COPPER_ORE);
        register("minecraft:deepslate_copper_ore", HY_COPPER_ORE);
        register("minecraft:diamond_ore", HY_ADAMANTITE_ORE);  // Map diamond to adamantite
        register("minecraft:deepslate_diamond_ore", HY_ADAMANTITE_ORE);
        register("minecraft:emerald_ore", HY_MITHRIL_ORE);
        register("minecraft:deepslate_emerald_ore", HY_MITHRIL_ORE);
        register("minecraft:lapis_ore", HY_COBALT_ORE);
        register("minecraft:deepslate_lapis_ore", HY_COBALT_ORE);
        register("minecraft:redstone_ore", HY_THORIUM_ORE);
        register("minecraft:deepslate_redstone_ore", HY_THORIUM_ORE);
        register("minecraft:nether_gold_ore", HY_GOLD_ORE);
        register("minecraft:nether_quartz_ore", HY_QUARTZITE);
        register("minecraft:ancient_debris", HY_ONYXIUM_ORE);
        
        // Fluids
        register("minecraft:lava", HY_LAVA);
        
        // Plants
        register("minecraft:grass", HY_GRASS_PLANT);
        register("minecraft:short_grass", HY_GRASS_PLANT);
        register("minecraft:tall_grass", HY_GRASS_PLANT);
        register("minecraft:fern", HY_FERN);
        register("minecraft:large_fern", HY_FERN);
        register("minecraft:cactus", HY_CACTUS);
        register("minecraft:dandelion", HY_FLOWER);
        register("minecraft:poppy", HY_FLOWER);
        
        // Legacy names (pre-flattening)
        register("minecraft:grass", HY_GRASS_PLANT);
    }
    
    private static void register(String minecraft, String hytale) {
        MINECRAFT_TO_HYTALE.put(minecraft, hytale);
        if (!HYTALE_TO_MINECRAFT.containsKey(hytale)) {
            HYTALE_TO_MINECRAFT.put(hytale, minecraft);
        }
    }
    
    /**
     * Convert a WorldPainter Material to a Hytale block ID string.
     */
    public static String toHytale(Material material) {
        if (material == null || material == Material.AIR) {
            return HY_AIR;
        }
        if (material == Material.WATER || material == Material.LAVA) {
            return HY_AIR;
        }
        
        String mcName = material.name;
        if (mcName != null) {
            // Handle "hytale:" namespace - strip prefix and use simpleName directly
            // as it is already a native Hytale block ID (e.g., "hytale:Soil_Dirt" → "Soil_Dirt")
            if (mcName.startsWith("hytale:")) {
                return mcName.substring(7);
            }
            String hytale = MINECRAFT_TO_HYTALE.get(mcName);
            if (hytale != null) {
                return hytale;
            }
            // Try with the minecraft: prefix
            if (!mcName.startsWith("minecraft:")) {
                hytale = MINECRAFT_TO_HYTALE.get("minecraft:" + mcName);
                if (hytale != null) {
                    return hytale;
                }
            }
        }
        
        // Fallback based on block type ID for legacy materials
        int blockType = material.blockType;
        switch (blockType) {
            case 0: return HY_AIR;
            case 1: return HY_STONE;
            case 2: return HY_GRASS_BLOCK;
            case 3: return HY_DIRT;
            case 4: return HY_COBBLESTONE;
            case 7: return HY_BEDROCK;
            case 8: case 9: return HY_AIR;
            case 10: case 11: return HY_AIR;
            case 12: return HY_SAND;
            case 13: return HY_GRAVEL;
            case 14: return HY_GOLD_ORE;
            case 15: return HY_IRON_ORE;
            case 16: return HY_STONE;  // Coal ore -> stone (no coal in Hytale)
            case 17: return HY_OAK_LOG;
            case 18: return HY_OAK_LEAVES;
            case 24: return HY_SANDSTONE;
            case 49: return HY_MAGMA_COOLED;  // Obsidian -> cooled magma
            case 56: return HY_ADAMANTITE_ORE;  // Diamond -> adamantite
            case 78: return HY_SNOW;
            case 79: return HY_ICE;
            case 82: return HY_CLAY;
            default: return HY_STONE; // Default fallback
        }
    }
    
    /**
     * Convert a Hytale block ID to a numeric ID used internally.
     * This is used for palette indexing in the region file format.
     * Note: These are internal IDs for the palette system, not actual Hytale registry IDs.
     */
    public static int toHytaleNumericId(String hytaleBlockId) {
        switch (hytaleBlockId) {
            case HY_AIR: return 0;
            case HY_STONE: return 1;
            case HY_GRASS_BLOCK: return 2;
            case HY_DIRT: return 3;
            case HY_COBBLESTONE: return 4;
            case HY_BEDROCK: return 5;
            case HY_WATER: return 6;
            case HY_LAVA: return 7;
            case HY_SAND: return 8;
            case HY_GRAVEL: return 9;
            case HY_GOLD_ORE: return 10;
            case HY_IRON_ORE: return 11;
            case HY_COPPER_ORE: return 12;
            case HY_ADAMANTITE_ORE: return 13;
            case HY_OAK_LOG: return 14;
            case HY_OAK_LEAVES: return 15;
            case HY_SOFTWOOD_PLANKS: return 16;
            case HY_HARDWOOD_PLANKS: return 17;
            case HY_SANDSTONE: return 18;
            case HY_MAGMA_COOLED: return 19;
            case HY_SNOW: return 20;
            case HY_ICE: return 21;
            case HY_CLAY: return 22;
            case HY_SHALE: return 23;
            case HY_SLATE: return 24;
            case HY_QUARTZITE: return 25;
            case HY_BASALT: return 26;
            case HY_VOLCANIC: return 27;
            case HY_MARBLE: return 28;
            case HY_CALCITE: return 29;
            case HY_CHALK: return 30;
            case HY_MUD: return 31;
            case HY_SAND_RED: return 32;
            case HY_SAND_WHITE: return 33;
            case HY_SANDSTONE_RED: return 34;
            case HY_SANDSTONE_WHITE: return 35;
            case HY_BIRCH_LOG: return 36;
            case HY_FIR_LOG: return 37;
            case HY_JUNGLE_LOG: return 38;
            case HY_PALM_LOG: return 39;
            case HY_BIRCH_LEAVES: return 40;
            case HY_FIR_LEAVES: return 41;
            case HY_JUNGLE_LEAVES: return 42;
            case HY_PALM_LEAVES: return 43;
            case HY_SILVER_ORE: return 44;
            case HY_COBALT_ORE: return 45;
            case HY_MITHRIL_ORE: return 46;
            case HY_THORIUM_ORE: return 47;
            case HY_ONYXIUM_ORE: return 48;
            case HY_GRASS_PLANT: return 49;
            case HY_FERN: return 50;
            case HY_FLOWER: return 51;
            case HY_CACTUS: return 52;
            default: return 1; // Default to stone
        }
    }
    
    /**
     * Get all registered mappings.
     */
    public static Map<String, String> getMappings() {
        return new HashMap<>(MINECRAFT_TO_HYTALE);
    }
    
    // ----- Native HytaleBlock support -----
    
    /**
     * Convert a WorldPainter Material to a HytaleBlock.
     * This is the preferred method for new code working with native Hytale blocks.
     * 
     * @param material The Minecraft material to convert
     * @return A HytaleBlock representing the closest Hytale equivalent
     */
    public static HytaleBlock toHytaleBlock(Material material) {
        String hytaleId = toHytale(material);
        return HytaleBlock.of(hytaleId);
    }
    
    /**
     * Convert a WorldPainter Material to a HytaleBlock with rotation.
     * Attempts to derive rotation from the material's properties.
     * 
     * @param material The Minecraft material to convert
     * @return A HytaleBlock with appropriate rotation
     */
    public static HytaleBlock toHytaleBlockWithRotation(Material material) {
        String hytaleId = toHytale(material);
        int rotation = deriveRotation(material);
        return HytaleBlock.of(hytaleId, rotation);
    }
    
    /**
     * Derive Hytale rotation from Minecraft material properties.
     * 
     * <p>Hytale rotation is encoded as rx*16 + ry*4 + rz where each component
     * is 0-3 representing 0°, 90°, 180°, 270° rotations.
     * 
     * @param material The material to derive rotation from
     * @return A rotation value 0-63
     */
    private static int deriveRotation(Material material) {
        if (material == null || material.getProperties() == null) {
            return 0;
        }
        
        Map<String, String> props = material.getProperties();
        
        // Handle axis-oriented blocks (logs, pillars)
        String axis = props.get("axis");
        if (axis != null) {
            switch (axis) {
                case "y": return 0;          // Vertical (default)
                case "x": return 1 * 4;      // Rotated to point along X (ry=1)
                case "z": return 1;          // Rotated to point along Z (rz=1)
            }
        }
        
        // Handle facing-oriented blocks (stairs, buttons, etc.)
        String facing = props.get("facing");
        if (facing != null) {
            switch (facing) {
                case "north": return 0;      // Default facing
                case "south": return 2 * 4;  // ry=2 (180° around Y)
                case "east": return 1 * 4;   // ry=1 (90° around Y)
                case "west": return 3 * 4;   // ry=3 (270° around Y)
                case "up": return 0;
                case "down": return 2 * 16;  // rx=2 (180° around X, flipped)
            }
        }
        
        // Handle half blocks (slabs, stairs)
        String half = props.get("half");
        if ("top".equals(half)) {
            return 2 * 16; // Flip upside down
        }
        
        return 0;
    }
    
    /**
     * Check if a Hytale block ID is valid (registered in the registry or known).
     * 
     * @param hytaleId The Hytale block ID to check
     * @return true if the block is recognized
     */
    public static boolean isValidHytaleBlock(String hytaleId) {
        if (hytaleId == null || hytaleId.isEmpty()) {
            return false;
        }
        // Check registry first
        HytaleBlockRegistry registry = HytaleBlockRegistry.getInstance();
        if (registry.contains(hytaleId)) {
            return true;
        }
        // Also check our reverse mapping
        return HYTALE_TO_MINECRAFT.containsKey(hytaleId);
    }
    
    /**
     * Get the numeric index for a HytaleBlock, suitable for palette serialization.
     * This delegates to HytaleBlockRegistry for consistent indexing.
     * 
     * @param block The HytaleBlock to get an index for
     * @return A numeric index for the block
     */
    public static int getBlockIndex(HytaleBlock block) {
        return HytaleBlockRegistry.getInstance().getIndex(block.id);
    }
    
    /**
     * Get a HytaleBlock from a numeric index.
     * 
     * @param index The palette index
     * @return The HytaleBlock at that index
     */
    public static HytaleBlock getBlockFromIndex(int index) {
        String id = HytaleBlockRegistry.getInstance().getId(index);
        return HytaleBlock.of(id);
    }
    
    /**
     * Convert a HytaleTerrain to a HytaleBlock at the given position.
     * 
     * @param terrain The terrain type
     * @param seed World seed
     * @param x X coordinate
     * @param y Y coordinate  
     * @param z Z coordinate (depth)
     * @return The HytaleBlock to place
     */
    public static HytaleBlock fromTerrain(HytaleTerrain terrain, long seed, int x, int y, int z) {
        if (terrain == null) {
            return HytaleBlock.of(HY_STONE);
        }
        return terrain.getBlock(seed, x, y, z);
    }
}
