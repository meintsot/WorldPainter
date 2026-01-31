package org.pepsoft.worldpainter.hytale;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Registry of native Hytale blocks loaded from HytaleAssets JSON files.
 * 
 * <p>This registry loads block definitions from the Hytale asset files
 * (Server/Item/Items/*.json) and provides lookup functionality for:
 * - Block metadata (DrawType, Material, Opacity, etc.)
 * - Category organization (Rock, Soil, Wood, Plant, etc.)
 * - String ID to numeric index mapping for palette serialization
 * 
 * <p>The registry is loaded once at startup and cached. It supports both
 * runtime loading from HytaleAssets and a fallback embedded registry for
 * when assets are not available.
 * 
 * @see HytaleBlock
 */
public class HytaleBlockRegistry {
    
    private static final Logger logger = LoggerFactory.getLogger(HytaleBlockRegistry.class);
    private static final Gson GSON = new Gson();
    
    /** Singleton instance. */
    private static HytaleBlockRegistry instance;
    
    /** Path to HytaleAssets directory. */
    private Path assetsPath;
    
    /** All registered block definitions, keyed by ID. */
    private final Map<String, BlockDefinition> blocks = new ConcurrentHashMap<>();
    
    /** Block ID to numeric index mapping for palette serialization. */
    private final Map<String, Integer> idToIndex = new ConcurrentHashMap<>();
    
    /** Numeric index to block ID mapping. */
    private final Map<Integer, String> indexToId = new ConcurrentHashMap<>();
    
    /** Blocks organized by category. */
    private final Map<String, List<String>> blocksByCategory = new LinkedHashMap<>();
    
    /** Next available index for new blocks. */
    private int nextIndex = 0;
    
    /** Whether the registry has been loaded. */
    private boolean loaded = false;
    
    /**
     * Get the singleton instance, initializing with embedded defaults if needed.
     */
    public static synchronized HytaleBlockRegistry getInstance() {
        if (instance == null) {
            instance = new HytaleBlockRegistry();
            instance.loadEmbeddedDefaults();
        }
        return instance;
    }
    
    /**
     * Initialize the registry with a HytaleAssets path.
     * This loads all block definitions from the JSON files.
     * 
     * @param assetsPath Path to HytaleAssets directory
     * @return true if loading succeeded
     */
    public static synchronized boolean initialize(Path assetsPath) {
        if (instance == null) {
            instance = new HytaleBlockRegistry();
        }
        return instance.loadFromAssets(assetsPath);
    }
    
    /**
     * Check if assets have been loaded from disk.
     */
    public boolean isLoaded() {
        return loaded;
    }
    
    /**
     * Get the assets path, or null if using embedded defaults.
     */
    public Path getAssetsPath() {
        return assetsPath;
    }
    
    /**
     * Load block definitions from HytaleAssets directory.
     */
    public boolean loadFromAssets(Path assetsPath) {
        this.assetsPath = assetsPath;
        Path itemsDir = assetsPath.resolve("Server/Item/Items");
        
        if (!Files.isDirectory(itemsDir)) {
            logger.warn("HytaleAssets items directory not found: {}", itemsDir);
            return false;
        }
        
        logger.info("Loading Hytale blocks from: {}", itemsDir);
        int loadedCount = 0;
        
        try (Stream<Path> files = Files.walk(itemsDir)) {
            List<Path> jsonFiles = files
                .filter(p -> p.toString().endsWith(".json"))
                .toList();
            
            for (Path jsonFile : jsonFiles) {
                try {
                    if (loadBlockFromJson(jsonFile)) {
                        loadedCount++;
                    }
                } catch (Exception e) {
                    logger.debug("Skipping non-block file: {}", jsonFile.getFileName());
                }
            }
        } catch (IOException e) {
            logger.error("Failed to scan items directory", e);
            return false;
        }
        
        logger.info("Loaded {} Hytale block definitions", loadedCount);
        loaded = loadedCount > 0;
        return loaded;
    }
    
    /**
     * Load a single block from a JSON file.
     */
    private boolean loadBlockFromJson(Path jsonFile) throws IOException {
        String content = Files.readString(jsonFile, StandardCharsets.UTF_8);
        JsonObject json = JsonParser.parseString(content).getAsJsonObject();
        
        // Only process items that have a BlockType section
        if (!json.has("BlockType")) {
            return false;
        }
        
        // Extract block ID from filename or json
        String id = jsonFile.getFileName().toString().replace(".json", "");
        if (json.has("Id")) {
            id = json.get("Id").getAsString();
        }
        
        JsonObject blockType = json.getAsJsonObject("BlockType");
        
        // Parse block properties
        BlockDefinition def = new BlockDefinition();
        def.id = id;
        def.displayName = getStringOr(json, "TranslationProperties.Name", id);
        def.material = getStringOr(blockType, "Material", "Solid");
        def.drawType = getStringOr(blockType, "DrawType", "Cube");
        def.opacity = getStringOr(blockType, "Opacity", "Opaque");
        def.group = getStringOr(blockType, "Group", "Misc");
        def.hardness = getFloatOr(blockType, "Hardness", 1.0f);
        def.lightEmission = getIntOr(blockType, "LightEmission", 0);
        def.canRotate = getBoolOr(blockType, "CanRotate", false);
        
        // Parse category from ID prefix
        def.category = parseCategory(id);
        
        // Register the block
        registerBlock(def);
        
        return true;
    }
    
    /**
     * Register a block definition.
     */
    public void registerBlock(BlockDefinition def) {
        blocks.put(def.id, def);
        
        // Assign numeric index
        if (!idToIndex.containsKey(def.id)) {
            int index = nextIndex++;
            idToIndex.put(def.id, index);
            indexToId.put(index, def.id);
        }
        
        // Add to category
        blocksByCategory.computeIfAbsent(def.category, k -> new ArrayList<>()).add(def.id);
    }
    
    /**
     * Get block definition by ID.
     */
    public BlockDefinition getBlock(String id) {
        return blocks.get(id);
    }
    
    /**
     * Get numeric index for a block ID.
     */
    public int getIndex(String id) {
        Integer index = idToIndex.get(id);
        if (index == null) {
            // Auto-register unknown blocks
            index = nextIndex++;
            idToIndex.put(id, index);
            indexToId.put(index, id);
        }
        return index;
    }
    
    /**
     * Get block ID for a numeric index.
     */
    public String getId(int index) {
        return indexToId.getOrDefault(index, "Empty");
    }
    
    /**
     * Get all block IDs in a category.
     */
    public List<String> getBlocksInCategory(String category) {
        return blocksByCategory.getOrDefault(category, Collections.emptyList());
    }
    
    /**
     * Get all categories.
     */
    public Set<String> getCategories() {
        return Collections.unmodifiableSet(blocksByCategory.keySet());
    }
    
    /**
     * Get all registered block IDs.
     */
    public Set<String> getAllBlockIds() {
        return Collections.unmodifiableSet(blocks.keySet());
    }
    
    /**
     * Get total number of registered blocks.
     */
    public int size() {
        return blocks.size();
    }
    
    /**
     * Check if a block ID is registered.
     */
    public boolean contains(String id) {
        return blocks.containsKey(id);
    }
    
    /**
     * Parse category from block ID prefix.
     */
    private String parseCategory(String id) {
        if (id.startsWith("Rock_")) return "Rock";
        if (id.startsWith("Soil_")) return "Soil";
        if (id.startsWith("Wood_")) return "Wood";
        if (id.startsWith("Plant_")) return "Plant";
        if (id.startsWith("Ore_")) return "Ore";
        if (id.startsWith("Water_") || id.startsWith("Lava_")) return "Fluid";
        if (id.startsWith("Metal_")) return "Metal";
        if (id.startsWith("Glass_")) return "Glass";
        if (id.startsWith("Brick_")) return "Brick";
        if (id.startsWith("Cloth_")) return "Cloth";
        if (id.startsWith("Light_")) return "Light";
        if (id.startsWith("Decoration_")) return "Decoration";
        if (id.startsWith("Structure_")) return "Structure";
        return "Misc";
    }
    
    /**
     * Load embedded default block definitions.
     * Used when HytaleAssets are not available.
     */
    private void loadEmbeddedDefaults() {
        // Essential blocks - always available
        registerDefault("Empty", "Misc", "Empty", "Empty", "Transparent", false);
        
        // Rock types
        registerDefault("Rock_Stone", "Rock", "Solid", "Cube", "Opaque", false);
        registerDefault("Rock_Stone_Mossy", "Rock", "Solid", "Cube", "Opaque", false);
        registerDefault("Rock_Stone_Cobble", "Rock", "Solid", "Cube", "Opaque", false);
        registerDefault("Rock_Stone_Cobble_Mossy", "Rock", "Solid", "Cube", "Opaque", false);
        registerDefault("Rock_Bedrock", "Rock", "Solid", "Cube", "Opaque", false);
        registerDefault("Rock_Ice", "Rock", "Solid", "Cube", "Transparent", false);
        registerDefault("Rock_Sandstone", "Rock", "Solid", "Cube", "Opaque", false);
        registerDefault("Rock_Sandstone_Red", "Rock", "Solid", "Cube", "Opaque", false);
        registerDefault("Rock_Sandstone_White", "Rock", "Solid", "Cube", "Opaque", false);
        registerDefault("Rock_Shale", "Rock", "Solid", "Cube", "Opaque", false);
        registerDefault("Rock_Slate", "Rock", "Solid", "Cube", "Opaque", false);
        registerDefault("Rock_Basalt", "Rock", "Solid", "Cube", "Opaque", false);
        registerDefault("Rock_Marble", "Rock", "Solid", "Cube", "Opaque", false);
        registerDefault("Rock_Quartzite", "Rock", "Solid", "Cube", "Opaque", false);
        registerDefault("Rock_Calcite", "Rock", "Solid", "Cube", "Opaque", false);
        registerDefault("Rock_Chalk", "Rock", "Solid", "Cube", "Opaque", false);
        registerDefault("Rock_Volcanic", "Rock", "Solid", "Cube", "Opaque", false);
        registerDefault("Rock_Magma_Cooled", "Rock", "Solid", "Cube", "Opaque", false);
        
        // Soil types
        registerDefault("Soil_Dirt", "Soil", "Solid", "Cube", "Opaque", false);
        registerDefault("Soil_Grass", "Soil", "Solid", "Cube", "Opaque", false);
        registerDefault("Soil_Grass_Lush", "Soil", "Solid", "Cube", "Opaque", false);
        registerDefault("Soil_Sand", "Soil", "Solid", "Cube", "Opaque", false);
        registerDefault("Soil_Sand_Red", "Soil", "Solid", "Cube", "Opaque", false);
        registerDefault("Soil_Sand_White", "Soil", "Solid", "Cube", "Opaque", false);
        registerDefault("Soil_Gravel", "Soil", "Solid", "Cube", "Opaque", false);
        registerDefault("Soil_Gravel_Mossy", "Soil", "Solid", "Cube", "Opaque", false);
        registerDefault("Soil_Clay", "Soil", "Solid", "Cube", "Opaque", false);
        registerDefault("Soil_Mud", "Soil", "Solid", "Cube", "Opaque", false);
        registerDefault("Soil_Snow", "Soil", "Solid", "Cube", "Opaque", false);
        
        // Wood types
        registerDefault("Wood_Oak_Trunk", "Wood", "Solid", "Cube", "Opaque", true);
        registerDefault("Wood_Birch_Trunk", "Wood", "Solid", "Cube", "Opaque", true);
        registerDefault("Wood_Fir_Trunk", "Wood", "Solid", "Cube", "Opaque", true);
        registerDefault("Wood_Redwood_Trunk", "Wood", "Solid", "Cube", "Opaque", true);
        registerDefault("Wood_Palm_Trunk", "Wood", "Solid", "Cube", "Opaque", true);
        registerDefault("Wood_Maple_Trunk", "Wood", "Solid", "Cube", "Opaque", true);
        registerDefault("Wood_Cedar_Trunk", "Wood", "Solid", "Cube", "Opaque", true);
        registerDefault("Wood_Aspen_Trunk", "Wood", "Solid", "Cube", "Opaque", true);
        registerDefault("Wood_Ash_Trunk", "Wood", "Solid", "Cube", "Opaque", true);
        registerDefault("Wood_Beech_Trunk", "Wood", "Solid", "Cube", "Opaque", true);
        registerDefault("Wood_Jungle_Trunk", "Wood", "Solid", "Cube", "Opaque", true);
        registerDefault("Wood_Softwood_Planks", "Wood", "Solid", "Cube", "Opaque", true);
        registerDefault("Wood_Hardwood_Planks", "Wood", "Solid", "Cube", "Opaque", true);
        
        // Leaves
        registerDefault("Plant_Leaves_Oak", "Plant", "Foliage", "Cube", "SemiTransparent", false);
        registerDefault("Plant_Leaves_Birch", "Plant", "Foliage", "Cube", "SemiTransparent", false);
        registerDefault("Plant_Leaves_Fir", "Plant", "Foliage", "Cube", "SemiTransparent", false);
        registerDefault("Plant_Leaves_Redwood", "Plant", "Foliage", "Cube", "SemiTransparent", false);
        registerDefault("Plant_Leaves_Palm", "Plant", "Foliage", "Cube", "SemiTransparent", false);
        registerDefault("Plant_Leaves_Maple", "Plant", "Foliage", "Cube", "SemiTransparent", false);
        registerDefault("Plant_Leaves_Cedar", "Plant", "Foliage", "Cube", "SemiTransparent", false);
        registerDefault("Plant_Leaves_Jungle", "Plant", "Foliage", "Cube", "SemiTransparent", false);
        
        // Plants
        registerDefault("Plant_Grass_Lush", "Plant", "Foliage", "Cross", "Transparent", false);
        registerDefault("Plant_Fern", "Plant", "Foliage", "Cross", "Transparent", false);
        registerDefault("Plant_Flower_Common_Yellow", "Plant", "Foliage", "Cross", "Transparent", false);
        registerDefault("Plant_Cactus_1", "Plant", "Solid", "Cube", "Opaque", false);
        
        // Ores
        registerDefault("Ore_Iron_Stone", "Ore", "Solid", "Cube", "Opaque", false);
        registerDefault("Ore_Gold_Stone", "Ore", "Solid", "Cube", "Opaque", false);
        registerDefault("Ore_Copper_Stone", "Ore", "Solid", "Cube", "Opaque", false);
        registerDefault("Ore_Silver_Stone", "Ore", "Solid", "Cube", "Opaque", false);
        registerDefault("Ore_Cobalt_Stone", "Ore", "Solid", "Cube", "Opaque", false);
        registerDefault("Ore_Mithril_Stone", "Ore", "Solid", "Cube", "Opaque", false);
        registerDefault("Ore_Adamantite_Stone", "Ore", "Solid", "Cube", "Opaque", false);
        registerDefault("Ore_Thorium_Stone", "Ore", "Solid", "Cube", "Opaque", false);
        registerDefault("Ore_Onyxium_Stone", "Ore", "Solid", "Cube", "Opaque", false);
        
        // Fluids
        registerDefault("Water_Source", "Fluid", "Fluid", "Fluid", "Transparent", false);
        registerDefault("Lava_Source", "Fluid", "Fluid", "Fluid", "Transparent", false);
        
        logger.info("Loaded {} embedded Hytale block definitions", blocks.size());
    }
    
    /**
     * Helper to register a default block definition.
     */
    private void registerDefault(String id, String category, String material, String drawType, 
                                  String opacity, boolean canRotate) {
        BlockDefinition def = new BlockDefinition();
        def.id = id;
        def.displayName = id.replace("_", " ");
        def.category = category;
        def.material = material;
        def.drawType = drawType;
        def.opacity = opacity;
        def.group = category;
        def.hardness = 1.0f;
        def.lightEmission = 0;
        def.canRotate = canRotate;
        registerBlock(def);
    }
    
    // JSON helper methods
    private static String getStringOr(JsonObject json, String path, String defaultValue) {
        try {
            String[] parts = path.split("\\.");
            JsonObject current = json;
            for (int i = 0; i < parts.length - 1; i++) {
                if (current.has(parts[i])) {
                    current = current.getAsJsonObject(parts[i]);
                } else {
                    return defaultValue;
                }
            }
            String lastKey = parts[parts.length - 1];
            if (current.has(lastKey)) {
                return current.get(lastKey).getAsString();
            }
        } catch (Exception ignored) {}
        return defaultValue;
    }
    
    private static float getFloatOr(JsonObject json, String key, float defaultValue) {
        try {
            if (json.has(key)) {
                return json.get(key).getAsFloat();
            }
        } catch (Exception ignored) {}
        return defaultValue;
    }
    
    private static int getIntOr(JsonObject json, String key, int defaultValue) {
        try {
            if (json.has(key)) {
                return json.get(key).getAsInt();
            }
        } catch (Exception ignored) {}
        return defaultValue;
    }
    
    private static boolean getBoolOr(JsonObject json, String key, boolean defaultValue) {
        try {
            if (json.has(key)) {
                return json.get(key).getAsBoolean();
            }
        } catch (Exception ignored) {}
        return defaultValue;
    }
    
    /**
     * Block definition data class.
     */
    public static class BlockDefinition implements Serializable {
        private static final long serialVersionUID = 1L;
        
        public String id;
        public String displayName;
        public String category;
        public String material;     // Solid, Fluid, Foliage, Empty
        public String drawType;     // Cube, Cross, Fluid, Custom, etc.
        public String opacity;      // Opaque, SemiTransparent, Transparent
        public String group;        // Block group for palettes
        public float hardness;
        public int lightEmission;
        public boolean canRotate;
        
        public boolean isOpaque() {
            return "Opaque".equals(opacity);
        }
        
        public boolean isTransparent() {
            return "Transparent".equals(opacity);
        }
        
        public boolean isSolid() {
            return "Solid".equals(material);
        }
        
        public boolean isFluid() {
            return "Fluid".equals(material);
        }
        
        @Override
        public String toString() {
            return id + " [" + category + ", " + drawType + ", " + opacity + "]";
        }
    }
}
