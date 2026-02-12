package org.pepsoft.worldpainter.hytale;

import java.util.*;

/**
 * Registry of Hytale biomes with their associated environment keys, tint colors,
 * and display colors for the biome panel. Biomes in Hytale are closely tied to
 * environments and tints; each biome maps to a specific environment string and
 * vegetation tint color.
 *
 * <p>In Hytale, biomes control terrain generation while environments control
 * weather, sky, fog, and water tint. The tint controls grass/vegetation coloring.
 * This class models all three concepts together for WorldPainter integration.</p>
 */
public final class HytaleBiome {

    private final int id;
    private final String name;
    private final String displayName;
    private final String environment;
    private final int tint;          // ARGB vegetation tint
    private final int displayColor;  // RGB color for the biome panel button
    private final Category category;

    private HytaleBiome(int id, String name, String displayName, String environment, int tint, int displayColor, Category category) {
        this.id = id;
        this.name = name;
        this.displayName = displayName;
        this.environment = environment;
        this.tint = tint;
        this.displayColor = displayColor;
        this.category = category;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getDisplayName() { return displayName; }
    public String getEnvironment() { return environment; }
    public int getTint() { return tint; }
    public int getDisplayColor() { return displayColor; }
    public Category getCategory() { return category; }

    @Override
    public String toString() {
        return displayName + " (" + name + ")";
    }

    // ─── Categories ────────────────────────────────────────────────────

    public enum Category {
        ZONE1("Zone 1 — Emerald Wilds"),
        ZONE2("Zone 2 — Howling Sands"),
        ZONE3("Zone 3 — Whispering Frost Frontiers"),
        ZONE4("Zone 4 — Devastated Lands"),
        OCEAN("Ocean"),
        MISC("Unknown / Miscellaneous"),
        ENCOUNTERS("Encounters");

        private final String displayName;

        Category(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }

    // ─── All biome definitions ─────────────────────────────────────────
    // Builder-team approved biomes only.

    // Auto-biome (special value 255 — let exporter decide from terrain)
    public static final int BIOME_AUTO = 255;
    public static final String BIOME_AUTO_NAME = "Automatic";

    // ID counter
    private static int nextId = 0;
    private static final List<HytaleBiome> ALL_BIOMES = new ArrayList<>();
    private static final Map<Integer, HytaleBiome> BY_ID = new HashMap<>();
    private static final Map<String, HytaleBiome> BY_NAME = new HashMap<>();
    private static final Map<Category, List<HytaleBiome>> BY_CATEGORY = new LinkedHashMap<>();

    // ─── Zone 1: Emerald Wilds ─────────────────────────────────────────

    public static final HytaleBiome DRIFTING_PLAINS = register("Zone1_Drifting_Plains", "Drifting Plains",
            "Env_Zone1_Plains", 0xFF5B9E28, 0x5B9E28, Category.ZONE1);

    public static final HytaleBiome SEEDLING_WOODS = register("Zone1_Seedling_Woods", "Seedling Woods",
            "Env_Zone1_Forests", 0xFF4A8A22, 0x4A8A22, Category.ZONE1);

    public static final HytaleBiome THE_FENS = register("Zone1_The_Fens", "The Fens",
            "Env_Zone1_Fens", 0xFF5A7A20, 0x6A8A30, Category.ZONE1);

    public static final HytaleBiome SWAMPS = register("Zone1_Swamps", "Swamps",
            "Env_Zone1_Swamps", 0xFF5A7A20, 0x5A7A20, Category.ZONE1);

    public static final HytaleBiome AZURE_FOREST = register("Zone1_Azure_Forest", "Azure Forest",
            "Env_Zone1_Azure", 0xFF4080C0, 0x4080C0, Category.ZONE1);

    public static final HytaleBiome AUTUMN_FOREST = register("Zone1_Autumn_Forest", "Autumn Forest",
            "Env_Zone1_Autumn", 0xFFC87420, 0xC87420, Category.ZONE1);

    public static final HytaleBiome BOULDER_FIELDS = register("Zone1_Boulder_Fields", "Boulder Fields",
            "Env_Zone1_Mountains", 0xFF6CA229, 0x808080, Category.ZONE1);

    public static final HytaleBiome CRYSTAL_CAVES = register("Zone1_Crystal_Caves", "Crystal Caves",
            "Env_Zone1_Crystal_Caves", 0xFF80C0E0, 0x80C0E0, Category.ZONE1);

    public static final HytaleBiome BOOM_CAVES = register("Zone1_Boom_Caves", "Boom Caves",
            "Env_Zone1_Boom_Caves", 0xFFA08040, 0xA08040, Category.ZONE1);

    public static final HytaleBiome DEEP_LAVA_CAVES = register("Zone1_Deep_Lava_Caves", "Deep Lava Caves",
            "Env_Zone1_Caves_Volcanic", 0xFF8B4513, 0x7B1F08, Category.ZONE1);

    // ─── Zone 2: Howling Sands ─────────────────────────────────────────

    public static final HytaleBiome BADLANDS = register("Zone2_Badlands", "Badlands",
            "Env_Zone2_Badlands", 0xFFC0A060, 0xC0A060, Category.ZONE2);

    public static final HytaleBiome DESOLATE_BASIN = register("Zone2_Desolate_Basin", "Desolate Basin",
            "Env_Zone2_Desolate_Basin", 0xFF908040, 0x908040, Category.ZONE2);

    public static final HytaleBiome GOLDEN_STEPPES = register("Zone2_Golden_Steppes", "Golden Steppes",
            "Env_Zone2_Steppes", 0xFFBDB76B, 0xBDB76B, Category.ZONE2);

    public static final HytaleBiome DESERTS = register("Zone2_Deserts", "Deserts",
            "Env_Zone2_Deserts", 0xFFBDB76B, 0xDBC497, Category.ZONE2);

    public static final HytaleBiome OASES = register("Zone2_Oases", "Oases",
            "Env_Zone2_Oasis", 0xFF60B040, 0x60B040, Category.ZONE2);

    public static final HytaleBiome SAVANNAS = register("Zone2_Savannas", "Savannas",
            "Env_Zone2_Savanna", 0xFFA0A020, 0xA0A020, Category.ZONE2);

    public static final HytaleBiome HOT_SPRINGS = register("Zone2_Hot_Springs", "Hot Springs",
            "Env_Zone2_Hot_Springs", 0xFF60B0C0, 0x60B0C0, Category.ZONE2);

    public static final HytaleBiome SCARAK_HIVE_TUNNELS = register("Zone2_Scarak_Hive_Tunnels", "Scarak Hive Tunnels",
            "Env_Zone2_Scarak", 0xFF806020, 0x806020, Category.ZONE2);

    // ─── Zone 3: Whispering Frost Frontiers ────────────────────────────

    public static final HytaleBiome FROSTMARCH_TUNDRA = register("Zone3_Frostmarch_Tundra", "Frostmarch Tundra",
            "Env_Zone3_Tundra", 0xFF80B497, 0x80B497, Category.ZONE3);

    public static final HytaleBiome BOREAL_REACH = register("Zone3_Boreal_Reach", "Boreal Reach",
            "Env_Zone3_Boreal_Reach", 0xFF2A6A3A, 0x2A6A3A, Category.ZONE3);

    public static final HytaleBiome THE_EVERFROST = register("Zone3_The_Everfrost", "The Everfrost",
            "Env_Zone3_Everfrost", 0xFFA0C8D0, 0xB0D0E0, Category.ZONE3);

    public static final HytaleBiome GLACIERS = register("Zone3_Glaciers", "Glaciers",
            "Env_Zone3_Glacial", 0xFFA0C8D0, 0xA0C8D0, Category.ZONE3);

    public static final HytaleBiome ICEBERGS = register("Zone3_Icebergs", "Icebergs",
            "Env_Zone3_Icebergs", 0xFFB0D8E0, 0xC0E0F0, Category.ZONE3);

    public static final HytaleBiome MOUNTAINS = register("Zone3_Mountains", "Mountains",
            "Env_Zone3_Mountains", 0xFF607060, 0x607060, Category.ZONE3);

    public static final HytaleBiome REDWOOD_FOREST = register("Zone3_Redwood_Forest", "Redwood Forest",
            "Env_Zone3_Redwood", 0xFF3A6A30, 0x3A6A30, Category.ZONE3);

    public static final HytaleBiome BOREAL_FOREST = register("Zone3_Boreal_Forest", "Boreal Forest",
            "Env_Zone3_Forests", 0xFF2A6A3A, 0x1A5A2A, Category.ZONE3);

    public static final HytaleBiome CEDAR_FOREST = register("Zone3_Cedar_Forest", "Cedar Forest",
            "Env_Zone3_Cedar", 0xFF3A6A30, 0x2A5A20, Category.ZONE3);

    public static final HytaleBiome ICY_CAVES = register("Zone3_Icy_Caves", "Icy Caves",
            "Env_Zone3_Caves_Glacial", 0xFF80A0B0, 0x80A0B0, Category.ZONE3);

    public static final HytaleBiome SUBMERGED_CAVES = register("Zone3_Submerged_Caves", "Submerged Caves",
            "Env_Zone3_Caves_Submerged", 0xFF405060, 0x405060, Category.ZONE3);

    // ─── Zone 4: Devastated Lands ──────────────────────────────────────

    public static final HytaleBiome CHARRED_WOODLANDS = register("Zone4_Charred_Woodlands", "Charred Woodlands",
            "Env_Zone4_Charred", 0xFF3A2A1A, 0x3A2A1A, Category.ZONE4);

    public static final HytaleBiome CINDER_WASTES = register("Zone4_Cinder_Wastes", "Cinder Wastes",
            "Env_Zone4_Wastes", 0xFF505020, 0x505020, Category.ZONE4);

    public static final HytaleBiome VOLCANOES = register("Zone4_Volcanoes", "Volcanoes",
            "Env_Zone4_Volcanoes", 0xFF8B2500, 0x8B2500, Category.ZONE4);

    public static final HytaleBiome MAGMA_MOUNTAINS = register("Zone4_Magma_Mountains", "Magma Mountains",
            "Env_Zone4_Magma_Mountains", 0xFFA03000, 0xA03000, Category.ZONE4);

    public static final HytaleBiome VOLCANIC_SHORES = register("Zone4_Volcanic_Shores", "Volcanic Shores",
            "Env_Zone4_Shores", 0xFF706040, 0x706040, Category.ZONE4);

    public static final HytaleBiome EVER_BURNING_WOODS = register("Zone4_Ever_Burning_Woods", "Ever-Burning Woods",
            "Env_Zone4_Ever_Burning", 0xFFC04020, 0xC04020, Category.ZONE4);

    public static final HytaleBiome ASHEN_FLATS = register("Zone4_Ashen_Flats", "Ashen Flats",
            "Env_Zone4_Ashen", 0xFF808070, 0x808070, Category.ZONE4);

    public static final HytaleBiome DESOLATED_WOODLANDS = register("Zone4_Desolated_Woodlands", "Desolated Woodlands",
            "Env_Zone4_Forests", 0xFF304020, 0x304020, Category.ZONE4);

    public static final HytaleBiome MUSHROOM_FORESTS = register("Zone4_Mushroom_Forests", "Mushroom Forests",
            "Env_Zone4_Mushroom", 0xFF8060A0, 0x8060A0, Category.ZONE4);

    public static final HytaleBiome TROPICAL_JUNGLE = register("Zone4_Tropical_Jungle", "Tropical Jungle",
            "Env_Zone4_Jungles", 0xFF1A6030, 0x1A6030, Category.ZONE4);

    public static final HytaleBiome VOLCANIC_CAVERNS = register("Zone4_Volcanic_Caverns", "Volcanic Caverns",
            "Env_Zone4_Caves_Volcanic", 0xFF8B2500, 0x6B1500, Category.ZONE4);

    // ─── Ocean ─────────────────────────────────────────────────────────

    public static final HytaleBiome DEEP_OCEAN = register("Deep_Ocean", "Deep Ocean",
            "Env_Zone0", 0xFF2076B5, 0x1060A5, Category.OCEAN);

    public static final HytaleBiome OCEAN_SHELF = register("Ocean_Shelf", "Ocean Shelf",
            "Env_Zone0_Temperate", 0xFF2076B5, 0x3090C0, Category.OCEAN);

    public static final HytaleBiome CRYSTALLINE_DEPTHS = register("Crystalline_Depths", "Crystalline Depths",
            "Env_Crystalline_Depths", 0xFF80C0E0, 0x60A0D0, Category.OCEAN);

    // ─── Unknown / Miscellaneous ───────────────────────────────────────

    public static final HytaleBiome GHOST_FOREST = register("Ghost_Forest", "Ghost Forest",
            "Env_Ghost_Forest", 0xFF8A8A8A, 0x8A8A8A, Category.MISC);

    // ─── Encounters ────────────────────────────────────────────────────

    public static final HytaleBiome ENCOUNTERS = register("Encounters", "Encounters",
            "Env_Encounters", 0xFF5B9E28, 0x5A4A3A, Category.ENCOUNTERS);

    // ─── Registration ──────────────────────────────────────────────────

    private static HytaleBiome register(String name, String displayName, String environment, int tint, int displayColor, Category category) {
        int id = nextId++;
        HytaleBiome biome = new HytaleBiome(id, name, displayName, environment, tint, displayColor, category);
        ALL_BIOMES.add(biome);
        BY_ID.put(id, biome);
        BY_NAME.put(name, biome);
        BY_CATEGORY.computeIfAbsent(category, k -> new ArrayList<>()).add(biome);
        return biome;
    }

    // ─── Public API ────────────────────────────────────────────────────

    public static HytaleBiome getById(int id) {
        return BY_ID.get(id);
    }

    public static HytaleBiome getByName(String name) {
        return BY_NAME.get(name);
    }

    public static List<HytaleBiome> getAllBiomes() {
        return Collections.unmodifiableList(ALL_BIOMES);
    }

    public static List<HytaleBiome> getBiomesByCategory(Category category) {
        return Collections.unmodifiableList(BY_CATEGORY.getOrDefault(category, Collections.emptyList()));
    }

    public static int getBiomeCount() {
        return ALL_BIOMES.size();
    }

    /**
     * The display names array, indexed by biome ID.
     * Used by BiomesPanel/BiomeScheme-compatible code.
     * Index 255 is reserved for "Automatic".
     */
    public static String[] getDisplayNames() {
        String[] names = new String[256];
        for (HytaleBiome biome : ALL_BIOMES) {
            names[biome.id] = biome.displayName;
        }
        names[BIOME_AUTO] = BIOME_AUTO_NAME;
        return names;
    }

    /**
     * Get all biome IDs for the button order in the biomes panel.
     * Returns the base biome IDs (one per button) in display order.
     */
    public static int[] getBiomeOrder() {
        List<Integer> order = new ArrayList<>();

        // Zone 1 — Emerald Wilds
        order.add(DRIFTING_PLAINS.id);
        order.add(SEEDLING_WOODS.id);
        order.add(THE_FENS.id);
        order.add(SWAMPS.id);
        order.add(AZURE_FOREST.id);
        order.add(AUTUMN_FOREST.id);
        order.add(BOULDER_FIELDS.id);
        order.add(CRYSTAL_CAVES.id);
        order.add(BOOM_CAVES.id);
        order.add(DEEP_LAVA_CAVES.id);
        order.add(-1); // spacer

        // Zone 2 — Howling Sands
        order.add(BADLANDS.id);
        order.add(DESOLATE_BASIN.id);
        order.add(GOLDEN_STEPPES.id);
        order.add(DESERTS.id);
        order.add(OASES.id);
        order.add(SAVANNAS.id);
        order.add(HOT_SPRINGS.id);
        order.add(SCARAK_HIVE_TUNNELS.id);
        order.add(-1); // spacer

        // Zone 3 — Whispering Frost Frontiers
        order.add(FROSTMARCH_TUNDRA.id);
        order.add(BOREAL_REACH.id);
        order.add(THE_EVERFROST.id);
        order.add(GLACIERS.id);
        order.add(ICEBERGS.id);
        order.add(MOUNTAINS.id);
        order.add(REDWOOD_FOREST.id);
        order.add(BOREAL_FOREST.id);
        order.add(CEDAR_FOREST.id);
        order.add(ICY_CAVES.id);
        order.add(SUBMERGED_CAVES.id);
        order.add(-1); // spacer

        // Zone 4 — Devastated Lands
        order.add(CHARRED_WOODLANDS.id);
        order.add(CINDER_WASTES.id);
        order.add(VOLCANOES.id);
        order.add(MAGMA_MOUNTAINS.id);
        order.add(VOLCANIC_SHORES.id);
        order.add(EVER_BURNING_WOODS.id);
        order.add(ASHEN_FLATS.id);
        order.add(DESOLATED_WOODLANDS.id);
        order.add(MUSHROOM_FORESTS.id);
        order.add(TROPICAL_JUNGLE.id);
        order.add(VOLCANIC_CAVERNS.id);
        order.add(-1); // spacer

        // Ocean
        order.add(DEEP_OCEAN.id);
        order.add(OCEAN_SHELF.id);
        order.add(CRYSTALLINE_DEPTHS.id);
        order.add(-1); // spacer

        // Unknown / Miscellaneous
        order.add(GHOST_FOREST.id);
        order.add(-1); // spacer

        // Encounters
        order.add(ENCOUNTERS.id);

        return order.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Find a biome that best matches a terrain-derived biome name string
     * (used for auto-biome assignment from terrain in the exporter).
     */
    public static HytaleBiome fromTerrainBiomeName(String terrainBiome) {
        if (terrainBiome == null) return DRIFTING_PLAINS;

        switch (terrainBiome) {
            case "Grassland":   return DRIFTING_PLAINS;
            case "Forest":      return SEEDLING_WOODS;
            case "Tropical":    return TROPICAL_JUNGLE;
            case "Swamp":       return SWAMPS;
            case "Savanna":     return SAVANNAS;
            case "Desert":      return DESERTS;
            case "Tundra":      return FROSTMARCH_TUNDRA;
            case "Mountain":    return MOUNTAINS;
            case "Volcanic":    return VOLCANOES;
            case "Ocean":       return DEEP_OCEAN;
            case "Underground": return CRYSTAL_CAVES;
            default:            return DRIFTING_PLAINS;
        }
    }
}
