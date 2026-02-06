package org.pepsoft.worldpainter.hytale;

import org.pepsoft.minecraft.Material;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Comprehensive registry of all known Hytale block types, organized by category.
 * Blocks are also registered as {@link Material} objects in the "hytale" namespace,
 * allowing seamless integration with the existing {@link org.pepsoft.worldpainter.MixedMaterial}
 * and custom terrain system.
 *
 * <p>Block names come from HytaleAssets BlockTypeList JSON files and asset directories.
 * The registry preserves backward-compatible API for palette serialization
 * ({@link #getIndex(String)}, {@link #getId(int)}) and block lookup ({@link #getBlock(String)}).
 *
 * @see HytaleBlock
 */
public class HytaleBlockRegistry {

    private static final Logger logger = LoggerFactory.getLogger(HytaleBlockRegistry.class);

    public static final String HYTALE_NAMESPACE = "hytale";

    // =========================================================================
    // Category enum for organized UI display
    // =========================================================================

    /**
     * Categories of Hytale blocks for organized display in the block selection UI.
     */
    public enum Category {
        SOIL("Soils & Earth"),
        SAND("Sand"),
        CLAY("Clay"),
        SNOW_ICE("Snow & Ice"),
        GRAVEL("Gravel & Pebbles"),
        ROCK("Rock"),
        ROCK_CONSTRUCTION("Rock Construction"),
        ORE("Ores"),
        CRYSTAL_GEM("Crystals & Gems"),
        WOOD_NATURAL("Wood (Natural)"),
        WOOD_PLANKS("Wood Planks"),
        LEAVES("Tree Leaves"),
        GRASS_PLANTS("Grass"),
        FLOWERS("Flowers"),
        FERNS("Ferns"),
        BUSHES("Bushes & Brambles"),
        CACTUS("Cacti"),
        MOSS_VINES("Moss & Vines"),
        MUSHROOMS("Mushrooms"),
        CROPS("Crops & Farming"),
        CORAL("Coral"),
        SEAWEED("Seaweed & Aquatic"),
        SAPLINGS_FRUITS("Saplings & Fruits"),
        RUBBLE("Rubble & Scatter"),
        DECORATION("Decorations"),
        CLOTH("Cloth & Wool"),
        HIVE("Hive Blocks"),
        RUNIC("Runic Blocks"),
        FLUID("Fluids"),
        SPECIAL("Special & Technical");

        private final String displayName;

        Category(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() { return displayName; }

        @Override
        public String toString() { return displayName; }
    }

    // =========================================================================
    // Singleton & Material registration
    // =========================================================================

    /** Singleton instance. */
    private static HytaleBlockRegistry instance;

    /** Whether Material objects have been registered. */
    private static volatile boolean materialsRegistered = false;

    /** Path to HytaleAssets directory (optional). */
    private Path assetsPath;

    /** All registered block definitions, keyed by ID. */
    private final Map<String, BlockDefinition> blocks = new ConcurrentHashMap<>();

    /** Block ID to numeric index mapping for palette serialization. */
    private final Map<String, Integer> idToIndex = new ConcurrentHashMap<>();

    /** Numeric index to block ID mapping. */
    private final Map<Integer, String> indexToId = new ConcurrentHashMap<>();

    /** Blocks organized by legacy string category (for backward compat). */
    private final Map<String, List<String>> blocksByLegacyCategory = new LinkedHashMap<>();

    /** Next available index for new blocks. */
    private int nextIndex = 0;

    /** Whether the registry has been loaded. */
    private boolean loaded = false;

    /**
     * Get the singleton instance, initializing with the comprehensive block list.
     */
    public static synchronized HytaleBlockRegistry getInstance() {
        if (instance == null) {
            instance = new HytaleBlockRegistry();
            instance.loadComprehensiveDefaults();
        }
        return instance;
    }

    /**
     * Initialize the registry with a HytaleAssets path (optional).
     */
    public static synchronized boolean initialize(Path assetsPath) {
        getInstance(); // Ensure defaults loaded
        instance.assetsPath = assetsPath;
        instance.loaded = true;
        return true;
    }

    /**
     * Ensure all Hytale blocks are registered as {@link Material} objects under
     * the "hytale" namespace. Safe to call multiple times; only registers once.
     */
    public static synchronized void ensureMaterialsRegistered() {
        if (materialsRegistered) {
            return;
        }
        getInstance(); // Ensure block defs loaded
        for (Map.Entry<Category, List<String>> entry : BLOCKS_BY_CATEGORY.entrySet()) {
            for (String name : entry.getValue()) {
                Material.get(HYTALE_NAMESPACE + ":" + name);
            }
        }
        materialsRegistered = true;
        logger.info("Registered {} Hytale block types as Materials", getAllBlockNames().size());
    }

    // =========================================================================
    // Category-based API (new)
    // =========================================================================

    /** Get all categories. */
    public static Category[] getCategoryValues() {
        return Category.values();
    }

    /** Get all block names for a given category, sorted alphabetically. */
    public static List<String> getBlockNames(Category category) {
        return Collections.unmodifiableList(
                BLOCKS_BY_CATEGORY.getOrDefault(category, Collections.emptyList()));
    }

    /** Get all block names across all categories, sorted alphabetically. */
    public static List<String> getAllBlockNames() {
        if (allBlockNamesSorted == null) {
            List<String> all = new ArrayList<>();
            for (List<String> names : BLOCKS_BY_CATEGORY.values()) {
                all.addAll(names);
            }
            Collections.sort(all);
            allBlockNamesSorted = Collections.unmodifiableList(all);
        }
        return allBlockNamesSorted;
    }

    /** Get total block count. */
    public static int getBlockCount() {
        return getAllBlockNames().size();
    }

    /** Find the category for a block name, or null if not found. */
    public static Category getCategoryForBlock(String blockName) {
        for (Map.Entry<Category, List<String>> entry : BLOCKS_BY_CATEGORY.entrySet()) {
            if (entry.getValue().contains(blockName)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** Format a block ID for display (replace underscores with spaces). */
    public static String formatDisplayName(String blockId) {
        return blockId.replace('_', ' ');
    }

    // =========================================================================
    // Legacy / backward-compatible API
    // =========================================================================

    public boolean isLoaded() { return loaded; }
    public Path getAssetsPath() { return assetsPath; }

    /** Register a block definition (backward compat). */
    public void registerBlock(BlockDefinition def) {
        blocks.put(def.id, def);
        if (!idToIndex.containsKey(def.id)) {
            int index = nextIndex++;
            idToIndex.put(def.id, index);
            indexToId.put(index, def.id);
        }
        blocksByLegacyCategory.computeIfAbsent(def.category, k -> new ArrayList<>()).add(def.id);
    }

    /** Get block definition by ID. */
    public BlockDefinition getBlock(String id) {
        return blocks.get(id);
    }

    /** Get numeric index for a block ID. */
    public int getIndex(String id) {
        Integer index = idToIndex.get(id);
        if (index == null) {
            index = nextIndex++;
            idToIndex.put(id, index);
            indexToId.put(index, id);
        }
        return index;
    }

    /** Get block ID for a numeric index. */
    public String getId(int index) {
        return indexToId.getOrDefault(index, "Empty");
    }

    /** Get all block IDs in a legacy category. */
    public List<String> getBlocksInCategory(String category) {
        return blocksByLegacyCategory.getOrDefault(category, Collections.emptyList());
    }

    /** Get all legacy categories. */
    public Set<String> getCategories() {
        return Collections.unmodifiableSet(blocksByLegacyCategory.keySet());
    }

    /** Get all registered block IDs. */
    public Set<String> getAllBlockIds() {
        return Collections.unmodifiableSet(blocks.keySet());
    }

    /** Get total number of registered blocks. */
    public int size() { return blocks.size(); }

    /** Check if a block ID is registered. */
    public boolean contains(String id) { return blocks.containsKey(id); }

    // =========================================================================
    // Comprehensive block data loading
    // =========================================================================

    /**
     * Load the comprehensive block list and register as BlockDefinitions.
     */
    private void loadComprehensiveDefaults() {
        for (Map.Entry<Category, List<String>> entry : BLOCKS_BY_CATEGORY.entrySet()) {
            Category cat = entry.getKey();
            String legacyCat = toLegacyCategory(cat);
            for (String name : entry.getValue()) {
                BlockDefinition def = new BlockDefinition();
                def.id = name;
                def.displayName = name.replace('_', ' ');
                def.category = legacyCat;
                def.material = guessMaterial(cat);
                def.drawType = guessDrawType(cat);
                def.opacity = guessOpacity(cat);
                def.group = legacyCat;
                def.hardness = 1.0f;
                def.lightEmission = 0;
                def.canRotate = cat == Category.WOOD_NATURAL || cat == Category.WOOD_PLANKS;
                registerBlock(def);
            }
        }
        loaded = true;
        logger.info("Loaded {} comprehensive Hytale block definitions", blocks.size());
    }

    private String toLegacyCategory(Category cat) {
        switch (cat) {
            case SOIL: case SAND: case CLAY: case SNOW_ICE: case GRAVEL: case HIVE: return "Soil";
            case ROCK: case ROCK_CONSTRUCTION: case CRYSTAL_GEM: case RUNIC: return "Rock";
            case ORE: return "Ore";
            case WOOD_NATURAL: case WOOD_PLANKS: return "Wood";
            case LEAVES: case GRASS_PLANTS: case FLOWERS: case FERNS: case BUSHES:
            case CACTUS: case MOSS_VINES: case MUSHROOMS: case CROPS: case CORAL:
            case SEAWEED: case SAPLINGS_FRUITS: return "Plant";
            case RUBBLE: case DECORATION: return "Decoration";
            case CLOTH: return "Cloth";
            case FLUID: return "Fluid";
            case SPECIAL: return "Misc";
            default: return "Misc";
        }
    }

    private String guessMaterial(Category cat) {
        switch (cat) {
            case FLUID: return "Fluid";
            case LEAVES: case GRASS_PLANTS: case FLOWERS: case FERNS: case BUSHES:
            case MOSS_VINES: case SEAWEED: return "Foliage";
            case SPECIAL: return "Empty";
            default: return "Solid";
        }
    }

    private String guessDrawType(Category cat) {
        switch (cat) {
            case GRASS_PLANTS: case FLOWERS: case FERNS: case SEAWEED: return "Cross";
            case FLUID: return "Fluid";
            default: return "Cube";
        }
    }

    private String guessOpacity(Category cat) {
        switch (cat) {
            case GRASS_PLANTS: case FLOWERS: case FERNS: case SEAWEED:
            case FLUID: case SPECIAL: return "Transparent";
            case LEAVES: case BUSHES: case MOSS_VINES: return "SemiTransparent";
            default: return "Opaque";
        }
    }

    // =========================================================================
    // Comprehensive block registry data (static)
    // =========================================================================

    private static List<String> allBlockNamesSorted;
    private static final Map<Category, List<String>> BLOCKS_BY_CATEGORY = new LinkedHashMap<>();

    static {
        // --- SOILS ---
        addBlocks(Category.SOIL,
            "Soil_Dirt", "Soil_Dirt_Burnt", "Soil_Dirt_Cold", "Soil_Dirt_Dry", "Soil_Dirt_Poisoned",
            "Soil_Grass", "Soil_Grass_Burnt", "Soil_Grass_Cold", "Soil_Grass_Deep",
            "Soil_Grass_Dry", "Soil_Grass_Full", "Soil_Grass_Sunny", "Soil_Grass_Wet",
            "Soil_Mud", "Soil_Mud_Dry", "Soil_Ash",
            "Soil_Leaves", "Soil_Needles", "Soil_Roots_Poisoned", "Soil_Seaweed_Block",
            "Soil_Pathway", "Soil_Pathway_Half", "Soil_Pathway_Quarter", "Soil_Pathway_ThreeQuarter"
        );

        // --- SAND ---
        addBlocks(Category.SAND,
            "Soil_Sand", "Soil_Sand_Ashen", "Soil_Sand_Red", "Soil_Sand_White",
            "Soil_Sand_White_Path_Half"
        );

        // --- CLAY ---
        addBlocks(Category.CLAY,
            "Soil_Clay",
            "Soil_Clay_Black", "Soil_Clay_Blue", "Soil_Clay_Cyan", "Soil_Clay_Green",
            "Soil_Clay_Grey", "Soil_Clay_Lime", "Soil_Clay_Orange", "Soil_Clay_Pink",
            "Soil_Clay_Purple", "Soil_Clay_Red", "Soil_Clay_White", "Soil_Clay_Yellow",
            "Soil_Clay_Smooth_Black", "Soil_Clay_Smooth_Blue", "Soil_Clay_Smooth_Cyan",
            "Soil_Clay_Smooth_Green", "Soil_Clay_Smooth_Grey", "Soil_Clay_Smooth_Lime",
            "Soil_Clay_Smooth_Orange", "Soil_Clay_Smooth_Pink", "Soil_Clay_Smooth_Purple",
            "Soil_Clay_Smooth_Red", "Soil_Clay_Smooth_White", "Soil_Clay_Smooth_Yellow",
            "Soil_Clay_Brick", "Soil_Clay_Brick_Beam", "Soil_Clay_Brick_Half",
            "Soil_Clay_Brick_Stairs", "Soil_Clay_Brick_Wall",
            "Soil_Clay_Ocean", "Soil_Clay_Ocean_Brick",
            "Soil_Clay_Ocean_Brick_Beam", "Soil_Clay_Ocean_Brick_Decorative",
            "Soil_Clay_Ocean_Brick_Half", "Soil_Clay_Ocean_Brick_Ornate",
            "Soil_Clay_Ocean_Brick_Roof", "Soil_Clay_Ocean_Brick_Roof_Flat",
            "Soil_Clay_Ocean_Brick_Roof_Flap", "Soil_Clay_Ocean_Brick_Roof_Vertical",
            "Soil_Clay_Ocean_Brick_Stairs", "Soil_Clay_Ocean_Brick_Wall",
            "Soil_Clay_Stalactite_Large", "Soil_Clay_Stalactite_Small"
        );

        // --- SNOW & ICE ---
        addBlocks(Category.SNOW_ICE,
            "Soil_Snow", "Soil_Snow_Half",
            "Soil_Snow_Brick", "Soil_Snow_Brick_Beam", "Soil_Snow_Brick_Half",
            "Soil_Snow_Brick_Stairs", "Soil_Snow_Brick_Wall",
            "Rock_Ice", "Rock_Ice_Icicles", "Rock_Ice_Permafrost",
            "Rock_Ice_Stalactite_Large", "Rock_Ice_Stalactite_Small"
        );

        // --- GRAVEL ---
        addBlocks(Category.GRAVEL,
            "Soil_Gravel", "Soil_Gravel_Half",
            "Soil_Gravel_Mossy", "Soil_Gravel_Mossy_Half",
            "Soil_Gravel_Sand", "Soil_Gravel_Sand_Half",
            "Soil_Gravel_Sand_Red", "Soil_Gravel_Sand_Red_Half",
            "Soil_Gravel_Sand_White", "Soil_Gravel_Sand_White_Half",
            "Soil_Pebbles", "Soil_Pebbles_Frozen"
        );

        // --- ROCK (base types) ---
        addBlocks(Category.ROCK,
            "Rock_Stone", "Rock_Stone_Mossy", "Rock_Shale", "Rock_Slate", "Rock_Quartzite",
            "Rock_Sandstone", "Rock_Sandstone_Red", "Rock_Sandstone_White",
            "Rock_Basalt", "Rock_Volcanic", "Rock_Marble", "Rock_Calcite", "Rock_Aqua",
            "Rock_Chalk", "Rock_Salt", "Rock_Bedrock",
            "Rock_Magma_Cooled", "Rock_Magma_Cooled_Half"
        );

        // --- ROCK CONSTRUCTION ---
        List<String> rockConstruction = new ArrayList<>();
        for (String base : new String[]{
            "Rock_Stone", "Rock_Stone_Mossy", "Rock_Basalt", "Rock_Calcite", "Rock_Marble",
            "Rock_Quartzite", "Rock_Sandstone", "Rock_Sandstone_Red", "Rock_Sandstone_White",
            "Rock_Shale", "Rock_Slate", "Rock_Aqua", "Rock_Volcanic", "Rock_Chalk",
            "Rock_Gold", "Rock_Concrete", "Rock_Peach", "Rock_Ledge", "Rock_Lime"
        }) {
            addRockVariants(rockConstruction, base);
        }
        rockConstruction.add("Rock_Volcanic_Cracked_Lava");
        rockConstruction.add("Rock_Volcanic_Cracked_Poisoned");
        Collections.sort(rockConstruction);
        BLOCKS_BY_CATEGORY.put(Category.ROCK_CONSTRUCTION, rockConstruction);

        // --- ORES ---
        addBlocks(Category.ORE,
            "Ore_Adamantite_Basalt", "Ore_Adamantite_Shale", "Ore_Adamantite_Slate",
            "Ore_Adamantite_Stone", "Ore_Adamantite_Volcanic",
            "Ore_Cobalt_Basalt", "Ore_Cobalt_Sandstone", "Ore_Cobalt_Shale",
            "Ore_Cobalt_Slate", "Ore_Cobalt_Stone", "Ore_Cobalt_Volcanic",
            "Ore_Copper_Basalt", "Ore_Copper_Sandstone", "Ore_Copper_Shale",
            "Ore_Copper_Stone", "Ore_Copper_Volcanic",
            "Ore_Gold_Basalt", "Ore_Gold_Sandstone", "Ore_Gold_Shale",
            "Ore_Gold_Stone", "Ore_Gold_Volcanic",
            "Ore_Iron_Basalt", "Ore_Iron_Sandstone", "Ore_Iron_Shale",
            "Ore_Iron_Slate", "Ore_Iron_Stone", "Ore_Iron_Volcanic",
            "Ore_Mithril_Basalt", "Ore_Mithril_Magma", "Ore_Mithril_Slate",
            "Ore_Mithril_Stone", "Ore_Mithril_Volcanic",
            "Ore_Onyxium_Basalt", "Ore_Onyxium_Sandstone", "Ore_Onyxium_Shale",
            "Ore_Onyxium_Stone", "Ore_Onyxium_Volcanic",
            "Ore_Silver_Basalt", "Ore_Silver_Sandstone", "Ore_Silver_Shale",
            "Ore_Silver_Slate", "Ore_Silver_Stone", "Ore_Silver_Volcanic",
            "Ore_Thorium_Basalt", "Ore_Thorium_Sandstone", "Ore_Thorium_Shale",
            "Ore_Thorium_Stone", "Ore_Thorium_Volcanic"
        );

        // --- CRYSTALS & GEMS ---
        addBlocks(Category.CRYSTAL_GEM,
            "Rock_Crystal_Blue_Block", "Rock_Crystal_Cyan_Block", "Rock_Crystal_Green_Block",
            "Rock_Crystal_Pink_Block", "Rock_Crystal_Purple_Block", "Rock_Crystal_Red_Block",
            "Rock_Crystal_White_Block", "Rock_Crystal_Yellow_Block",
            "Rock_Crystal_Blue_Large", "Rock_Crystal_Blue_Medium", "Rock_Crystal_Blue_Small",
            "Rock_Crystal_Cyan_Large", "Rock_Crystal_Cyan_Medium", "Rock_Crystal_Cyan_Small",
            "Rock_Crystal_Green_Large", "Rock_Crystal_Green_Medium", "Rock_Crystal_Green_Small",
            "Rock_Crystal_Pink_Large", "Rock_Crystal_Pink_Medium", "Rock_Crystal_Pink_Small",
            "Rock_Crystal_Purple_Large", "Rock_Crystal_Purple_Medium", "Rock_Crystal_Purple_Small",
            "Rock_Crystal_Red_Large", "Rock_Crystal_Red_Medium", "Rock_Crystal_Red_Small",
            "Rock_Crystal_White_Large", "Rock_Crystal_White_Medium", "Rock_Crystal_White_Small",
            "Rock_Crystal_Yellow_Large", "Rock_Crystal_Yellow_Medium", "Rock_Crystal_Yellow_Small",
            "Rock_Gem_Diamond", "Rock_Gem_Emerald", "Rock_Gem_Ruby",
            "Rock_Gem_Sapphire", "Rock_Gem_Topaz", "Rock_Gem_Voidstone", "Rock_Gem_Zephyr"
        );

        // --- WOOD (Natural / Trees) ---
        List<String> woodNatural = new ArrayList<>();
        for (String tree : new String[]{
            "Amber", "Ash", "Aspen", "Azure", "Banyan", "Beech", "Birch", "Bottletree",
            "Burnt", "Camphor", "Cedar", "Crystal", "Dry", "Fig_Blue", "Fir", "Fire",
            "Gumboab", "Jungle", "Maple", "Oak", "Palm", "Palo", "Petrified", "Poisoned",
            "Redwood", "Sallow", "Spiral", "Stormbark", "Windwillow", "Wisteria_Wild"
        }) {
            woodNatural.add("Wood_" + tree + "_Trunk");
            woodNatural.add("Wood_" + tree + "_Trunk_Full");
            woodNatural.add("Wood_" + tree + "_Roots");
            woodNatural.add("Wood_" + tree + "_Branch_Short");
            woodNatural.add("Wood_" + tree + "_Branch_Long");
            woodNatural.add("Wood_" + tree + "_Branch_Corner");
        }
        // Special cases
        woodNatural.add("Wood_Bamboo_Trunk");
        woodNatural.add("Wood_Bamboo_Branch_Long");
        woodNatural.add("Wood_Gnarled_Roots");
        woodNatural.add("Wood_Ice_Trunk");
        Collections.sort(woodNatural);
        BLOCKS_BY_CATEGORY.put(Category.WOOD_NATURAL, woodNatural);

        // --- WOOD PLANKS & CONSTRUCTION ---
        List<String> woodPlanks = new ArrayList<>();
        for (String type : new String[]{
            "Blackwood", "Darkwood", "Deadwood", "Drywood", "Goldenwood",
            "Greenwood", "Hardwood", "Lightwood", "Redwood", "Softwood", "Tropicalwood"
        }) {
            addWoodVariants(woodPlanks, "Wood_" + type);
        }
        woodPlanks.add("Wood_Village_Wall_Full");
        for (String c : new String[]{"Blue","Cyan","Green","Grey","Lime","Red","White","Yellow"}) {
            woodPlanks.add("Wood_Village_Wall_" + c + "_Full");
        }
        Collections.sort(woodPlanks);
        BLOCKS_BY_CATEGORY.put(Category.WOOD_PLANKS, woodPlanks);

        // --- TREE LEAVES ---
        addBlocks(Category.LEAVES,
            "Plant_Leaves_Amber", "Plant_Leaves_Ash", "Plant_Leaves_Aspen",
            "Plant_Leaves_Autumn", "Plant_Leaves_Autumn_Floor",
            "Plant_Leaves_Azure", "Plant_Leaves_Bamboo",
            "Plant_Leaves_Banyan", "Plant_Leaves_Beech", "Plant_Leaves_Birch",
            "Plant_Leaves_Bottle", "Plant_Leaves_Bramble", "Plant_Leaves_Burnt",
            "Plant_Leaves_Camphor", "Plant_Leaves_Cedar", "Plant_Leaves_Crystal",
            "Plant_Leaves_Dead", "Plant_Leaves_Dry",
            "Plant_Leaves_Fig_Blue", "Plant_Leaves_Fir", "Plant_Leaves_Fir_Red",
            "Plant_Leaves_Fir_Snow", "Plant_Leaves_Fire",
            "Plant_Leaves_Goldentree", "Plant_Leaves_Gumboab",
            "Plant_Leaves_Jungle", "Plant_Leaves_Jungle_Floor",
            "Plant_Leaves_Maple", "Plant_Leaves_Oak",
            "Plant_Leaves_Palm", "Plant_Leaves_Palm_Arid", "Plant_Leaves_Palm_Oasis",
            "Plant_Leaves_Palo", "Plant_Leaves_Petrified",
            "Plant_Leaves_Poisoned", "Plant_Leaves_Poisoned_Floor",
            "Plant_Leaves_Redwood", "Plant_Leaves_Rhododendron",
            "Plant_Leaves_Sallow", "Plant_Leaves_Snow",
            "Plant_Leaves_Spiral", "Plant_Leaves_Stormbark",
            "Plant_Leaves_Windwillow", "Plant_Leaves_Wisteria_Wild"
        );

        // --- GRASS ---
        addBlocks(Category.GRASS_PLANTS,
            "Plant_Grass_Arid", "Plant_Grass_Arid_Short", "Plant_Grass_Arid_Tall",
            "Plant_Grass_Cave_Short",
            "Plant_Grass_Dry", "Plant_Grass_Dry_Tall",
            "Plant_Grass_Gnarled", "Plant_Grass_Gnarled_Short", "Plant_Grass_Gnarled_Tall",
            "Plant_Grass_Jungle", "Plant_Grass_Jungle_Short", "Plant_Grass_Jungle_Tall",
            "Plant_Grass_Lush", "Plant_Grass_Lush_Short", "Plant_Grass_Lush_Tall",
            "Plant_Grass_Poisoned", "Plant_Grass_Poisoned_Short",
            "Plant_Grass_Rocky", "Plant_Grass_Rocky_Short", "Plant_Grass_Rocky_Tall",
            "Plant_Grass_Sharp", "Plant_Grass_Sharp_Overgrown", "Plant_Grass_Sharp_Short",
            "Plant_Grass_Sharp_Tall", "Plant_Grass_Sharp_Wild",
            "Plant_Grass_Snowy", "Plant_Grass_Snowy_Short", "Plant_Grass_Snowy_Tall",
            "Plant_Grass_Wet", "Plant_Grass_Wet_Overgrown", "Plant_Grass_Wet_Short",
            "Plant_Grass_Wet_Tall", "Plant_Grass_Wet_Wild",
            "Plant_Grass_Winter", "Plant_Grass_Winter_Short", "Plant_Grass_Winter_Tall"
        );

        // --- FLOWERS ---
        addBlocks(Category.FLOWERS,
            "Plant_Flower_Common_Blue", "Plant_Flower_Common_Blue2",
            "Plant_Flower_Common_Cyan", "Plant_Flower_Common_Cyan2",
            "Plant_Flower_Common_Grey", "Plant_Flower_Common_Grey2",
            "Plant_Flower_Common_Lime", "Plant_Flower_Common_Lime2",
            "Plant_Flower_Common_Orange", "Plant_Flower_Common_Orange2",
            "Plant_Flower_Common_Pink", "Plant_Flower_Common_Pink2",
            "Plant_Flower_Common_Poisoned", "Plant_Flower_Common_Poisoned2",
            "Plant_Flower_Common_Purple", "Plant_Flower_Common_Purple2",
            "Plant_Flower_Common_Red", "Plant_Flower_Common_Red2",
            "Plant_Flower_Common_Violet", "Plant_Flower_Common_Violet2",
            "Plant_Flower_Common_White", "Plant_Flower_Common_White2",
            "Plant_Flower_Common_Yellow", "Plant_Flower_Common_Yellow2",
            "Plant_Flower_Bushy_Blue", "Plant_Flower_Bushy_Cyan", "Plant_Flower_Bushy_Green",
            "Plant_Flower_Bushy_Grey", "Plant_Flower_Bushy_Orange", "Plant_Flower_Bushy_Poisoned",
            "Plant_Flower_Bushy_Purple", "Plant_Flower_Bushy_Red", "Plant_Flower_Bushy_Violet",
            "Plant_Flower_Bushy_White", "Plant_Flower_Bushy_Yellow",
            "Plant_Flower_Tall_Blue", "Plant_Flower_Tall_Cyan", "Plant_Flower_Tall_Cyan2",
            "Plant_Flower_Tall_Pink", "Plant_Flower_Tall_Purple", "Plant_Flower_Tall_Red",
            "Plant_Flower_Tall_Violet", "Plant_Flower_Tall_Yellow",
            "Plant_Flower_Orchid_Blue", "Plant_Flower_Orchid_Cyan", "Plant_Flower_Orchid_Orange",
            "Plant_Flower_Orchid_Pink", "Plant_Flower_Orchid_Poisoned", "Plant_Flower_Orchid_Purple",
            "Plant_Flower_Orchid_Red", "Plant_Flower_Orchid_White", "Plant_Flower_Orchid_Yellow",
            "Plant_Flower_Flax_Blue", "Plant_Flower_Flax_Orange", "Plant_Flower_Flax_Pink",
            "Plant_Flower_Flax_Purple", "Plant_Flower_Flax_White", "Plant_Flower_Flax_Yellow",
            "Plant_Flower_Water_Blue", "Plant_Flower_Water_Duckweed", "Plant_Flower_Water_Green",
            "Plant_Flower_Water_Purple", "Plant_Flower_Water_Red", "Plant_Flower_Water_White",
            "Plant_Flower_Hemlock", "Plant_Flower_Poisoned_Orange",
            "Plant_Sunflower_Stage_0", "Plant_Lavender_Stage_0"
        );

        // --- FERNS ---
        addBlocks(Category.FERNS,
            "Plant_Fern", "Plant_Fern_Arid", "Plant_Fern_Forest", "Plant_Fern_Jungle",
            "Plant_Fern_Jungle_Trunk", "Plant_Fern_Tall",
            "Plant_Fern_Wet_Big", "Plant_Fern_Wet_Giant", "Plant_Fern_Wet_Giant_Trunk",
            "Plant_Fern_Winter"
        );

        // --- BUSHES ---
        addBlocks(Category.BUSHES,
            "Plant_Bush", "Plant_Bush_Arid", "Plant_Bush_Arid_Palm", "Plant_Bush_Arid_Red",
            "Plant_Bush_Arid_Sharp", "Plant_Bush_Crystal",
            "Plant_Bush_Dead", "Plant_Bush_Dead_Hanging", "Plant_Bush_Dead_Tall", "Plant_Bush_Dead_Twisted",
            "Plant_Bush_Green", "Plant_Bush_Hanging",
            "Plant_Bush_Jungle", "Plant_Bush_Lush", "Plant_Bush_Wet",
            "Plant_Bush_Winter", "Plant_Bush_Winter_Red", "Plant_Bush_Winter_Sharp", "Plant_Bush_Winter_Snow",
            "Plant_Bramble_Dead_Lavathorn", "Plant_Bramble_Dead_Twisted",
            "Plant_Bramble_Dry_Magic", "Plant_Bramble_Dry_Sandthorn", "Plant_Bramble_Dry_Twisted",
            "Plant_Bramble_Moss_Twisted", "Plant_Bramble_Winter"
        );

        // --- CACTI ---
        addBlocks(Category.CACTUS,
            "Plant_Cactus_1", "Plant_Cactus_2", "Plant_Cactus_3",
            "Plant_Cactus_Ball_1", "Plant_Cactus_Flat_1", "Plant_Cactus_Flat_2", "Plant_Cactus_Flat_3",
            "Plant_Cactus_Flower"
        );

        // --- MOSS & VINES ---
        addBlocks(Category.MOSS_VINES,
            "Plant_Moss_Block_Blue", "Plant_Moss_Block_Green", "Plant_Moss_Block_Green_Dark",
            "Plant_Moss_Block_Red", "Plant_Moss_Block_Yellow",
            "Plant_Moss_Rug_Blue", "Plant_Moss_Rug_Green", "Plant_Moss_Rug_Green_Dark",
            "Plant_Moss_Rug_Lime", "Plant_Moss_Rug_Pink", "Plant_Moss_Rug_Red", "Plant_Moss_Rug_Yellow",
            "Plant_Moss_Short_Blue", "Plant_Moss_Short_Green", "Plant_Moss_Short_Green_Dark",
            "Plant_Moss_Short_Red", "Plant_Moss_Short_Yellow",
            "Plant_Moss_Wall_Blue", "Plant_Moss_Wall_Green", "Plant_Moss_Wall_Green_Dark",
            "Plant_Moss_Wall_Red", "Plant_Moss_Wall_Yellow",
            "Plant_Moss_Cave_Blue", "Plant_Moss_Cave_Green", "Plant_Moss_Cave_Green_Dark",
            "Plant_Moss_Cave_Red", "Plant_Moss_Cave_Yellow",
            "Plant_Moss_Blue", "Plant_Moss_Green", "Plant_Moss_Green_Dark",
            "Plant_Moss_Red", "Plant_Moss_Yellow",
            "Plant_Vine", "Plant_Vine_Green_Hanging", "Plant_Vine_Hanging",
            "Plant_Vine_Jungle", "Plant_Vine_Liana", "Plant_Vine_Liana_End",
            "Plant_Vine_Red_Hanging", "Plant_Vine_Rug",
            "Plant_Vine_Wall", "Plant_Vine_Wall_Dead", "Plant_Vine_Wall_Dry",
            "Plant_Vine_Wall_Poisoned", "Plant_Vine_Wall_Winter",
            "Plant_Barnacles"
        );

        // --- MUSHROOMS ---
        addBlocks(Category.MUSHROOMS,
            "Plant_Crop_Mushroom_Boomshroom_Large", "Plant_Crop_Mushroom_Boomshroom_Small",
            "Plant_Crop_Mushroom_Cap_Brown", "Plant_Crop_Mushroom_Cap_Green",
            "Plant_Crop_Mushroom_Cap_Poison", "Plant_Crop_Mushroom_Cap_Red", "Plant_Crop_Mushroom_Cap_White",
            "Plant_Crop_Mushroom_Common_Blue", "Plant_Crop_Mushroom_Common_Brown", "Plant_Crop_Mushroom_Common_Lime",
            "Plant_Crop_Mushroom_Flatcap_Blue", "Plant_Crop_Mushroom_Flatcap_Green",
            "Plant_Crop_Mushroom_Glowing_Blue", "Plant_Crop_Mushroom_Glowing_Green",
            "Plant_Crop_Mushroom_Glowing_Orange", "Plant_Crop_Mushroom_Glowing_Purple",
            "Plant_Crop_Mushroom_Glowing_Red", "Plant_Crop_Mushroom_Glowing_Violet",
            "Plant_Crop_Mushroom_Shelve_Brown", "Plant_Crop_Mushroom_Shelve_Green",
            "Plant_Crop_Mushroom_Shelve_Yellow", "Plant_Crop_Mushroom_Block",
            "Plant_Crop_Mushroom_Block_Blue", "Plant_Crop_Mushroom_Block_Blue_Branch",
            "Plant_Crop_Mushroom_Block_Blue_Mycelium", "Plant_Crop_Mushroom_Block_Blue_Trunk",
            "Plant_Crop_Mushroom_Block_Brown", "Plant_Crop_Mushroom_Block_Brown_Branch",
            "Plant_Crop_Mushroom_Block_Brown_Mycelium", "Plant_Crop_Mushroom_Block_Brown_Trunk",
            "Plant_Crop_Mushroom_Block_Green", "Plant_Crop_Mushroom_Block_Green_Branch",
            "Plant_Crop_Mushroom_Block_Green_Mycelium", "Plant_Crop_Mushroom_Block_Green_Trunk",
            "Plant_Crop_Mushroom_Block_Purple", "Plant_Crop_Mushroom_Block_Purple_Branch",
            "Plant_Crop_Mushroom_Block_Purple_Mycelium", "Plant_Crop_Mushroom_Block_Purple_Trunk",
            "Plant_Crop_Mushroom_Block_Red", "Plant_Crop_Mushroom_Block_Red_Branch",
            "Plant_Crop_Mushroom_Block_Red_Mycelium", "Plant_Crop_Mushroom_Block_Red_Trunk",
            "Plant_Crop_Mushroom_Block_White", "Plant_Crop_Mushroom_Block_White_Branch",
            "Plant_Crop_Mushroom_Block_White_Mycelium", "Plant_Crop_Mushroom_Block_White_Trunk",
            "Plant_Crop_Mushroom_Block_Yellow", "Plant_Crop_Mushroom_Block_Yellow_Branch",
            "Plant_Crop_Mushroom_Block_Yellow_Mycelium", "Plant_Crop_Mushroom_Block_Yellow_Trunk"
        );

        // --- CROPS ---
        addBlocks(Category.CROPS,
            "Plant_Crop_Aubergine", "Plant_Crop_Aubergine_Block",
            "Plant_Crop_Berry_Block", "Plant_Crop_Berry_Wet_Block", "Plant_Crop_Berry_Winter_Block",
            "Plant_Crop_Carrot_Block", "Plant_Crop_Cauliflower_Block",
            "Plant_Crop_Chilli_Block", "Plant_Crop_Corn_Block", "Plant_Crop_Cotton_Block",
            "Plant_Crop_Health1", "Plant_Crop_Health1_Block",
            "Plant_Crop_Health2", "Plant_Crop_Health2_Block",
            "Plant_Crop_Health3", "Plant_Crop_Health3_Block",
            "Plant_Crop_Lettuce_Block",
            "Plant_Crop_Mana1", "Plant_Crop_Mana1_Block",
            "Plant_Crop_Mana2", "Plant_Crop_Mana2_Block",
            "Plant_Crop_Mana3", "Plant_Crop_Mana3_Block",
            "Plant_Crop_Onion_Block", "Plant_Crop_Potato_Block", "Plant_Crop_Potato_Sweet_Block",
            "Plant_Crop_Pumpkin_Block", "Plant_Crop_Rice_Block",
            "Plant_Crop_Stamina1", "Plant_Crop_Stamina1_Block",
            "Plant_Crop_Stamina2", "Plant_Crop_Stamina2_Block",
            "Plant_Crop_Stamina3", "Plant_Crop_Stamina3_Block",
            "Plant_Crop_Tomato_Block", "Plant_Crop_Turnip_Block",
            "Plant_Crop_Wheat_Block", "Plant_Crop_Wheat_Stage_4_Burnt",
            "Plant_Hay_Bundle"
        );

        // --- CORAL ---
        addBlocks(Category.CORAL,
            "Plant_Coral_Block_Blue", "Plant_Coral_Block_Cyan", "Plant_Coral_Block_Green",
            "Plant_Coral_Block_Grey", "Plant_Coral_Block_Lime", "Plant_Coral_Block_Orange",
            "Plant_Coral_Block_Pink", "Plant_Coral_Block_Poison", "Plant_Coral_Block_Purple",
            "Plant_Coral_Block_Red", "Plant_Coral_Block_Violet", "Plant_Coral_Block_White",
            "Plant_Coral_Block_Yellow",
            "Plant_Coral_Bush_Blue", "Plant_Coral_Bush_Cyan", "Plant_Coral_Bush_Green",
            "Plant_Coral_Bush_Grey", "Plant_Coral_Bush_Lime", "Plant_Coral_Bush_Orange",
            "Plant_Coral_Bush_Pink", "Plant_Coral_Bush_Poisoned", "Plant_Coral_Bush_Purple",
            "Plant_Coral_Bush_Red", "Plant_Coral_Bush_Violet", "Plant_Coral_Bush_White",
            "Plant_Coral_Bush_Yellow",
            "Plant_Coral_Model_Blue", "Plant_Coral_Model_Cyan", "Plant_Coral_Model_Green",
            "Plant_Coral_Model_Grey", "Plant_Coral_Model_Lime", "Plant_Coral_Model_Orange",
            "Plant_Coral_Model_Pink", "Plant_Coral_Model_Purple",
            "Plant_Coral_Model_Red", "Plant_Coral_Model_Violet", "Plant_Coral_Model_White",
            "Plant_Coral_Model_Yellow"
        );

        // --- SEAWEED ---
        addBlocks(Category.SEAWEED,
            "Plant_Seaweed_Arid_Red", "Plant_Seaweed_Arid_Short", "Plant_Seaweed_Arid_Stack",
            "Plant_Seaweed_Arid_Tall", "Plant_Seaweed_Arid_Yellow",
            "Plant_Seaweed_Dead_Eerie", "Plant_Seaweed_Dead_Ghostly",
            "Plant_Seaweed_Dead_Short", "Plant_Seaweed_Dead_Stack", "Plant_Seaweed_Dead_Tall",
            "Plant_Seaweed_Grass", "Plant_Seaweed_Grass_Bulbs", "Plant_Seaweed_Grass_Green",
            "Plant_Seaweed_Grass_Short", "Plant_Seaweed_Grass_Stack", "Plant_Seaweed_Grass_Tall",
            "Plant_Seaweed_Wet_Stack",
            "Plant_Seaweed_Winter_Aurora", "Plant_Seaweed_Winter_Blue",
            "Plant_Seaweed_Winter_Short", "Plant_Seaweed_Winter_Stack", "Plant_Seaweed_Winter_Tall"
        );

        // --- SAPLINGS & FRUITS ---
        addBlocks(Category.SAPLINGS_FRUITS,
            "Plant_Sapling_Ash", "Plant_Sapling_Beech", "Plant_Sapling_Birch",
            "Plant_Sapling_Cedar", "Plant_Sapling_Crystal", "Plant_Sapling_Dry",
            "Plant_Sapling_Oak", "Plant_Sapling_Palm", "Plant_Sapling_Poisoned",
            "Plant_Sapling_Redwood", "Plant_Sapling_Spruce", "Plant_Sapling_Spruce_Frozen",
            "Plant_Sapling_Windwillow", "Plant_Seeds_Pine",
            "Plant_Fruit_Apple", "Plant_Fruit_Azure", "Plant_Fruit_Coconut",
            "Plant_Fruit_Mango", "Plant_Fruit_Pinkberry", "Plant_Fruit_Spiral", "Plant_Fruit_Windwillow",
            "Plant_Reeds_Arid", "Plant_Reeds_Lava", "Plant_Reeds_Marsh", "Plant_Reeds_Poison",
            "Plant_Reeds_Water", "Plant_Reeds_Wet", "Plant_Reeds_Winter",
            "Plant_Roots_Cave", "Plant_Roots_Cave_Small", "Plant_Roots_Leafy",
            "Plant_Test_Tree_Block"
        );

        // --- RUBBLE ---
        addBlocks(Category.RUBBLE,
            "Rubble_Aqua", "Rubble_Aqua_Medium",
            "Rubble_Basalt", "Rubble_Basalt_Medium",
            "Rubble_Calcite", "Rubble_Calcite_Medium",
            "Rubble_Ice", "Rubble_Ice_Medium",
            "Rubble_Marble", "Rubble_Marble_Medium",
            "Rubble_Quartzite", "Rubble_Quartzite_Medium",
            "Rubble_Sandstone", "Rubble_Sandstone_Medium",
            "Rubble_Sandstone_Red", "Rubble_Sandstone_Red_Medium",
            "Rubble_Sandstone_White", "Rubble_Sandstone_White_Medium",
            "Rubble_Shale", "Rubble_Shale_Medium",
            "Rubble_Slate", "Rubble_Slate_Medium",
            "Rubble_Stone", "Rubble_Stone_Medium",
            "Rubble_Stone_Mossy", "Rubble_Stone_Mossy_Medium",
            "Rubble_Volcanic", "Rubble_Volcanic_Medium"
        );

        // --- DECORATIONS ---
        addBlocks(Category.DECORATION,
            "Deco_Bone_Full", "Deco_Bone_Pile", "Deco_Bone_Ribs_Feran",
            "Deco_Bone_Skulls", "Deco_Bone_Skulls_Feran", "Deco_Bone_Skulls_Feran_Large",
            "Deco_Bone_Skulls_Feran_Wall", "Deco_Bone_Skulls_Wall",
            "Deco_Bone_Spike", "Deco_Bone_Spine",
            "Deco_Coral_Shell", "Deco_Coral_Shell_Purple", "Deco_Coral_Shell_Sanddollar",
            "Deco_Coral_Shell_Swirly", "Deco_Coral_Shell_Urchin",
            "Deco_Nest", "Deco_Starfish",
            "Deco_Trash", "Deco_Trash_Pile_Large", "Deco_Trash_Pile_Small",
            "Kweebec_Ancient"
        );

        // --- CLOTH & WOOL ---
        List<String> cloth = new ArrayList<>();
        for (String c : new String[]{"Black","Blue","Cyan","Green","Orange","Pink","Purple","Red","White","Yellow"}) {
            cloth.add("Cloth_Block_Wool_" + c);
        }
        for (String c : new String[]{"Black","Blue","Cyan","Green","Orange","Pink","Red","White"}) {
            cloth.add("Cloth_Roof_" + c);
            cloth.add("Cloth_Roof_" + c + "_Flat");
            cloth.add("Cloth_Roof_" + c + "_Flap");
            cloth.add("Cloth_Roof_" + c + "_Vertical");
        }
        Collections.sort(cloth);
        BLOCKS_BY_CATEGORY.put(Category.CLOTH, cloth);

        // --- HIVE ---
        addBlocks(Category.HIVE,
            "Soil_Hive", "Soil_Hive_Brick", "Soil_Hive_Brick_Beam",
            "Soil_Hive_Brick_Fence", "Soil_Hive_Brick_Half",
            "Soil_Hive_Brick_Smooth", "Soil_Hive_Brick_Stairs",
            "Soil_Hive_Corrupted", "Soil_Hive_Corrupted_Brick",
            "Soil_Hive_Corrupted_Brick_Beam", "Soil_Hive_Corrupted_Brick_Fence",
            "Soil_Hive_Corrupted_Brick_Half", "Soil_Hive_Corrupted_Brick_Smooth",
            "Soil_Hive_Corrupted_Brick_Stairs"
        );

        // --- RUNIC ---
        addBlocks(Category.RUNIC,
            "Rock_Runic_Brick", "Rock_Runic_Cobble",
            "Rock_Runic_Brick_Beam", "Rock_Runic_Brick_Half",
            "Rock_Runic_Brick_Ornate", "Rock_Runic_Brick_Pillar_Base", "Rock_Runic_Brick_Pillar_Middle",
            "Rock_Runic_Brick_Stairs", "Rock_Runic_Brick_Wall", "Rock_Runic_Pipe",
            "Rock_Runic_Cobble_Beam", "Rock_Runic_Cobble_Half", "Rock_Runic_Cobble_Stairs",
            "Rock_Runic_Blue_Brick", "Rock_Runic_Blue_Cobble",
            "Rock_Runic_Blue_Brick_Beam", "Rock_Runic_Blue_Brick_Half",
            "Rock_Runic_Blue_Brick_Ornate", "Rock_Runic_Blue_Brick_Stairs", "Rock_Runic_Blue_Brick_Wall",
            "Rock_Runic_Dark_Brick", "Rock_Runic_Dark_Cobble",
            "Rock_Runic_Dark_Brick_Beam", "Rock_Runic_Dark_Brick_Half",
            "Rock_Runic_Dark_Brick_Ornate", "Rock_Runic_Dark_Brick_Stairs", "Rock_Runic_Dark_Brick_Wall",
            "Rock_Runic_Teal_Brick", "Rock_Runic_Teal_Cobble",
            "Rock_Runic_Teal_Brick_Beam", "Rock_Runic_Teal_Brick_Half",
            "Rock_Runic_Teal_Brick_Ornate", "Rock_Runic_Teal_Brick_Stairs", "Rock_Runic_Teal_Brick_Wall",
            "LightStoneAlgiz", "LightStoneRuneDagaz", "LightStoneRuneFeho", "LightStoneRuneGebo"
        );

        // --- FLUIDS ---
        addBlocks(Category.FLUID,
            "Fluid_Water", "Fluid_Water_Source", "Fluid_Water_Finite",
            "Fluid_Lava", "Fluid_Lava_Source",
            "Fluid_Poison", "Fluid_Poison_Source",
            "Fluid_Slime", "Fluid_Slime_Source",
            "Fluid_Slime_Red", "Fluid_Slime_Red_Source",
            "Fluid_Tar", "Fluid_Tar_Source"
        );

        // --- SPECIAL ---
        addBlocks(Category.SPECIAL,
            "Empty",
            "Editor_Block", "Editor_Empty", "Editor_Anchor",
            "Portal_Void", "Portal_Device", "Portal_Return",
            "Rail", "Rail_Kart",
            "Trap_Ice", "Trap_Slate", "Trap_Ancient_Platform"
        );
    }

    private static void addBlocks(Category category, String... blockNames) {
        List<String> list = new ArrayList<>(Arrays.asList(blockNames));
        Collections.sort(list);
        BLOCKS_BY_CATEGORY.put(category, list);
    }

    private static void addRockVariants(List<String> list, String baseRock) {
        list.add(baseRock + "_Brick"); list.add(baseRock + "_Brick_Beam");
        list.add(baseRock + "_Brick_Decorative"); list.add(baseRock + "_Brick_Half");
        list.add(baseRock + "_Brick_Ornate");
        list.add(baseRock + "_Brick_Pillar_Base"); list.add(baseRock + "_Brick_Pillar_Middle");
        list.add(baseRock + "_Brick_Roof"); list.add(baseRock + "_Brick_Roof_Flat");
        list.add(baseRock + "_Brick_Roof_Flap"); list.add(baseRock + "_Brick_Roof_Vertical");
        list.add(baseRock + "_Brick_Smooth"); list.add(baseRock + "_Brick_Smooth_Half");
        list.add(baseRock + "_Brick_Stairs"); list.add(baseRock + "_Brick_Wall");
        list.add(baseRock + "_Cobble"); list.add(baseRock + "_Cobble_Beam");
        list.add(baseRock + "_Cobble_Half");
        list.add(baseRock + "_Cobble_Roof"); list.add(baseRock + "_Cobble_Roof_Flat");
        list.add(baseRock + "_Cobble_Roof_Flap"); list.add(baseRock + "_Cobble_Roof_Vertical");
        list.add(baseRock + "_Cobble_Stairs"); list.add(baseRock + "_Cobble_Wall");
        list.add(baseRock + "_Stalactite_Large"); list.add(baseRock + "_Stalactite_Small");
    }

    private static void addWoodVariants(List<String> list, String baseWood) {
        list.add(baseWood + "_Planks"); list.add(baseWood + "_Planks_Half");
        list.add(baseWood + "_Planks_Decorative"); list.add(baseWood + "_Planks_Ornate");
        list.add(baseWood + "_Planks_Beam"); list.add(baseWood + "_Planks_Stairs");
        list.add(baseWood + "_Planks_Roof"); list.add(baseWood + "_Planks_Roof_Flat");
        list.add(baseWood + "_Planks_Roof_Flap"); list.add(baseWood + "_Planks_Roof_Vertical");
        list.add(baseWood + "_Planks_Fence"); list.add(baseWood + "_Planks_Fence_Gate");
    }

    /**
     * Block definition data class (backward compatible).
     */
    public static class BlockDefinition implements Serializable {
        private static final long serialVersionUID = 1L;

        public String id;
        public String displayName;
        public String category;
        public String material;     // Solid, Fluid, Foliage, Empty
        public String drawType;     // Cube, Cross, Fluid, Custom
        public String opacity;      // Opaque, SemiTransparent, Transparent
        public String group;
        public float hardness;
        public int lightEmission;
        public boolean canRotate;

        public boolean isOpaque() { return "Opaque".equals(opacity); }
        public boolean isTransparent() { return "Transparent".equals(opacity); }
        public boolean isSolid() { return "Solid".equals(material); }
        public boolean isFluid() { return "Fluid".equals(material); }

        @Override
        public String toString() { return id + " [" + category + ", " + drawType + ", " + opacity + "]"; }
    }
}
