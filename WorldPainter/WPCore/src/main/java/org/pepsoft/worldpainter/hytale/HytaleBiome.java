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
        ZONE1_SURFACE("Zone 1 — Emerald Grove"),
        ZONE1_CAVES("Zone 1 — Caves"),
        ZONE2_SURFACE("Zone 2 — Howling Sands"),
        ZONE2_CAVES("Zone 2 — Caves"),
        ZONE3_SURFACE("Zone 3 — Borea"),
        ZONE3_CAVES("Zone 3 — Caves"),
        ZONE4_SURFACE("Zone 4 — Devastated Lands"),
        ZONE4_CAVES("Zone 4 — Caves"),
        OCEAN("Ocean"),
        SPECIAL("Special");

        private final String displayName;

        Category(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }

    // ─── All biome definitions ─────────────────────────────────────────
    // Tints come from the actual Hytale biome JSONs (TintProvider colors)
    // Environments come from EnvironmentProvider in the biome JSONs
    // Display colors are chosen to be recognizable on the biome panel

    // Auto-biome (special value 255 — let exporter decide from terrain)
    public static final int BIOME_AUTO = 255;
    public static final String BIOME_AUTO_NAME = "Automatic";

    // ID counter
    private static int nextId = 0;
    private static final List<HytaleBiome> ALL_BIOMES = new ArrayList<>();
    private static final Map<Integer, HytaleBiome> BY_ID = new HashMap<>();
    private static final Map<String, HytaleBiome> BY_NAME = new HashMap<>();
    private static final Map<Category, List<HytaleBiome>> BY_CATEGORY = new LinkedHashMap<>();

    // ─── Zone 1: Emerald Grove (Temperate) ─────────────────────────────

    public static final HytaleBiome ZONE1_PLAINS = register("Zone1_Plains", "Plains",
            "Env_Zone1_Plains", 0xFF5B9E28, 0x5B9E28, Category.ZONE1_SURFACE);

    public static final HytaleBiome ZONE1_FORESTS = register("Zone1_Forests", "Forests",
            "Env_Zone1_Forests", 0xFF4A8A22, 0x4A8A22, Category.ZONE1_SURFACE);

    public static final HytaleBiome ZONE1_MOUNTAINS = register("Zone1_Mountains", "Mountains",
            "Env_Zone1_Mountains", 0xFF6CA229, 0x808080, Category.ZONE1_SURFACE);

    public static final HytaleBiome ZONE1_SWAMPS = register("Zone1_Swamps", "Swamps",
            "Env_Zone1_Swamps", 0xFF5A7A20, 0x5A7A20, Category.ZONE1_SURFACE);

    public static final HytaleBiome ZONE1_SHORES = register("Zone1_Shores", "Shores",
            "Env_Zone1_Shores", 0xFF7EC850, 0xC2B280, Category.ZONE1_SURFACE);

    public static final HytaleBiome ZONE1_AUTUMN = register("Zone1_Autumn", "Autumn Forest",
            "Env_Zone1_Autumn", 0xFFC87420, 0xC87420, Category.ZONE1_SURFACE);

    public static final HytaleBiome ZONE1_AZURE = register("Zone1_Azure", "Azure Forest",
            "Env_Zone1_Azure", 0xFF4080C0, 0x4080C0, Category.ZONE1_SURFACE);

    public static final HytaleBiome ZONE1_KWEEBEC = register("Zone1_Kweebec", "Kweebec Village",
            "Env_Zone1_Kweebec", 0xFF5B9E28, 0x3B7E18, Category.ZONE1_SURFACE);

    public static final HytaleBiome ZONE1_TRORK = register("Zone1_Trork", "Trork Camp",
            "Env_Zone1_Trork", 0xFF5B9E28, 0x6B4E37, Category.ZONE1_SURFACE);

    // Zone 1 Caves
    public static final HytaleBiome ZONE1_CAVES = register("Zone1_Caves", "Caves",
            "Env_Zone1_Caves", 0xFF4A7A32, 0x555555, Category.ZONE1_CAVES);

    public static final HytaleBiome ZONE1_CAVES_FORESTS = register("Zone1_Caves_Forests", "Forest Caves",
            "Env_Zone1_Caves_Forests", 0xFF3A6A22, 0x3A6A22, Category.ZONE1_CAVES);

    public static final HytaleBiome ZONE1_CAVES_MOUNTAINS = register("Zone1_Caves_Mountains", "Mountain Caves",
            "Env_Zone1_Caves_Mountains", 0xFF505050, 0x707070, Category.ZONE1_CAVES);

    public static final HytaleBiome ZONE1_CAVES_PLAINS = register("Zone1_Caves_Plains", "Plains Caves",
            "Env_Zone1_Caves_Plains", 0xFF5B9E28, 0x808060, Category.ZONE1_CAVES);

    public static final HytaleBiome ZONE1_CAVES_SWAMPS = register("Zone1_Caves_Swamps", "Swamp Caves",
            "Env_Zone1_Caves_Swamps", 0xFF4A6A10, 0x4A6A10, Category.ZONE1_CAVES);

    public static final HytaleBiome ZONE1_CAVES_GOBLINS = register("Zone1_Caves_Goblins", "Goblin Caves",
            "Env_Zone1_Caves_Goblins", 0xFF3A5A12, 0x8B4513, Category.ZONE1_CAVES);

    public static final HytaleBiome ZONE1_CAVES_SPIDERS = register("Zone1_Caves_Spiders", "Spider Caves",
            "Env_Zone1_Caves_Spiders", 0xFF3A5A12, 0x4A3A4A, Category.ZONE1_CAVES);

    public static final HytaleBiome ZONE1_CAVES_RATS = register("Zone1_Caves_Rats", "Rat Caves",
            "Env_Zone1_Caves_Rats", 0xFF3A5A12, 0x6A5A4A, Category.ZONE1_CAVES);

    public static final HytaleBiome ZONE1_CAVES_VOLCANIC_T1 = register("Zone1_Caves_Volcanic_T1", "Volcanic Caves T1",
            "Env_Zone1_Caves_Volcanic_T1", 0xFF8B4513, 0x8B2500, Category.ZONE1_CAVES);

    public static final HytaleBiome ZONE1_CAVES_VOLCANIC_T2 = register("Zone1_Caves_Volcanic_T2", "Volcanic Caves T2",
            "Env_Zone1_Caves_Volcanic_T2", 0xFF8B4513, 0xA03000, Category.ZONE1_CAVES);

    public static final HytaleBiome ZONE1_CAVES_VOLCANIC_T3 = register("Zone1_Caves_Volcanic_T3", "Volcanic Caves T3",
            "Env_Zone1_Caves_Volcanic_T3", 0xFF8B4513, 0xC04000, Category.ZONE1_CAVES);

    // ─── Zone 2: Howling Sands (Desert/Arid) ───────────────────────────

    public static final HytaleBiome ZONE2_DESERTS = register("Zone2_Deserts", "Deserts",
            "Env_Zone2_Deserts", 0xFFBDB76B, 0xBDB76B, Category.ZONE2_SURFACE);

    public static final HytaleBiome ZONE2_SAVANNA = register("Zone2_Savanna", "Savanna",
            "Env_Zone2_Savanna", 0xFFA0A020, 0xA0A020, Category.ZONE2_SURFACE);

    public static final HytaleBiome ZONE2_OASIS = register("Zone2_Oasis", "Oasis",
            "Env_Zone2_Oasis", 0xFF60B040, 0x60B040, Category.ZONE2_SURFACE);

    public static final HytaleBiome ZONE2_PLATEAUS = register("Zone2_Plateaus", "Plateaus",
            "Env_Zone2_Plateaus", 0xFFC0A060, 0xC0A060, Category.ZONE2_SURFACE);

    public static final HytaleBiome ZONE2_SCRUB = register("Zone2_Scrub", "Scrubland",
            "Env_Zone2_Scrub", 0xFF908040, 0x908040, Category.ZONE2_SURFACE);

    public static final HytaleBiome ZONE2_SHORES = register("Zone2_Shores", "Shores",
            "Env_Zone2_Shores", 0xFFD0C080, 0xD0C080, Category.ZONE2_SURFACE);

    public static final HytaleBiome ZONE2_SCARAK = register("Zone2_Scarak", "Scarak Nest",
            "Env_Zone2_Scarak", 0xFF806020, 0x806020, Category.ZONE2_SURFACE);

    public static final HytaleBiome ZONE2_FERAN = register("Zone2_Feran", "Feran Territory",
            "Env_Zone2_Feran", 0xFFA08050, 0xA08050, Category.ZONE2_SURFACE);

    // Zone 2 Caves
    public static final HytaleBiome ZONE2_CAVES = register("Zone2_Caves", "Caves",
            "Env_Zone2_Caves", 0xFF605030, 0x555555, Category.ZONE2_CAVES);

    public static final HytaleBiome ZONE2_CAVES_DESERTS = register("Zone2_Caves_Deserts", "Desert Caves",
            "Env_Zone2_Caves_Deserts", 0xFFA09050, 0xA09050, Category.ZONE2_CAVES);

    public static final HytaleBiome ZONE2_CAVES_SAVANNA = register("Zone2_Caves_Savanna", "Savanna Caves",
            "Env_Zone2_Caves_Savanna", 0xFF808020, 0x808020, Category.ZONE2_CAVES);

    public static final HytaleBiome ZONE2_CAVES_PLATEAUS = register("Zone2_Caves_Plateaus", "Plateau Caves",
            "Env_Zone2_Caves_Plateaus", 0xFFA08050, 0xA08050, Category.ZONE2_CAVES);

    public static final HytaleBiome ZONE2_CAVES_SCRUB = register("Zone2_Caves_Scrub", "Scrub Caves",
            "Env_Zone2_Caves_Scrub", 0xFF706030, 0x706030, Category.ZONE2_CAVES);

    public static final HytaleBiome ZONE2_CAVES_SCARAK = register("Zone2_Caves_Scarak", "Scarak Caves",
            "Env_Zone2_Caves_Scarak", 0xFF605020, 0x605020, Category.ZONE2_CAVES);

    public static final HytaleBiome ZONE2_CAVES_GOBLINS = register("Zone2_Caves_Goblins", "Goblin Caves",
            "Env_Zone2_Caves_Goblins", 0xFF504020, 0x8B4513, Category.ZONE2_CAVES);

    public static final HytaleBiome ZONE2_CAVES_RATS = register("Zone2_Caves_Rats", "Rat Caves",
            "Env_Zone2_Caves_Rats", 0xFF504020, 0x6A5A4A, Category.ZONE2_CAVES);

    public static final HytaleBiome ZONE2_CAVES_VOLCANIC_T1 = register("Zone2_Caves_Volcanic_T1", "Volcanic Caves T1",
            "Env_Zone2_Caves_Volcanic_T1", 0xFF8B4513, 0x8B2500, Category.ZONE2_CAVES);

    public static final HytaleBiome ZONE2_CAVES_VOLCANIC_T2 = register("Zone2_Caves_Volcanic_T2", "Volcanic Caves T2",
            "Env_Zone2_Caves_Volcanic_T2", 0xFF8B4513, 0xA03000, Category.ZONE2_CAVES);

    public static final HytaleBiome ZONE2_CAVES_VOLCANIC_T3 = register("Zone2_Caves_Volcanic_T3", "Volcanic Caves T3",
            "Env_Zone2_Caves_Volcanic_T3", 0xFF8B4513, 0xC04000, Category.ZONE2_CAVES);

    // ─── Zone 3: Borea (Boreal/Cold) ───────────────────────────────────

    public static final HytaleBiome ZONE3_FORESTS = register("Zone3_Forests", "Boreal Forests",
            "Env_Zone3_Forests", 0xFF2A6A3A, 0x2A6A3A, Category.ZONE3_SURFACE);

    public static final HytaleBiome ZONE3_TUNDRA = register("Zone3_Tundra", "Tundra",
            "Env_Zone3_Tundra", 0xFF80B497, 0x80B497, Category.ZONE3_SURFACE);

    public static final HytaleBiome ZONE3_GLACIAL = register("Zone3_Glacial", "Glacial",
            "Env_Zone3_Glacial", 0xFFA0C8D0, 0xA0C8D0, Category.ZONE3_SURFACE);

    public static final HytaleBiome ZONE3_MOUNTAINS = register("Zone3_Mountains", "Mountains",
            "Env_Zone3_Mountains", 0xFF607060, 0x607060, Category.ZONE3_SURFACE);

    public static final HytaleBiome ZONE3_SHORES = register("Zone3_Shores", "Shores",
            "Env_Zone3_Shores", 0xFF90A890, 0x90A890, Category.ZONE3_SURFACE);

    public static final HytaleBiome ZONE3_GLACIAL_HENGES = register("Zone3_Glacial_Henges", "Glacial Henges",
            "Env_Zone3_Glacial_Henges", 0xFFB0D0E0, 0xB0D0E0, Category.ZONE3_SURFACE);

    public static final HytaleBiome ZONE3_HEDERA = register("Zone3_Hedera", "Hedera",
            "Env_Zone3_Hedera", 0xFF40A060, 0x40A060, Category.ZONE3_SURFACE);

    public static final HytaleBiome ZONE3_OUTLANDER = register("Zone3_Outlander", "Outlander Camp",
            "Env_Zone3_Outlander", 0xFF506050, 0x506050, Category.ZONE3_SURFACE);

    // Zone 3 Caves
    public static final HytaleBiome ZONE3_CAVES = register("Zone3_Caves", "Caves",
            "Env_Zone3_Caves", 0xFF405050, 0x555555, Category.ZONE3_CAVES);

    public static final HytaleBiome ZONE3_CAVES_FORESTS = register("Zone3_Caves_Forests", "Forest Caves",
            "Env_Zone3_Caves_Forests", 0xFF2A5A2A, 0x2A5A2A, Category.ZONE3_CAVES);

    public static final HytaleBiome ZONE3_CAVES_GLACIAL = register("Zone3_Caves_Glacial", "Glacial Caves",
            "Env_Zone3_Caves_Glacial", 0xFF80A0B0, 0x80A0B0, Category.ZONE3_CAVES);

    public static final HytaleBiome ZONE3_CAVES_MOUNTAINS = register("Zone3_Caves_Mountains", "Mountain Caves",
            "Env_Zone3_Caves_Mountains", 0xFF506060, 0x506060, Category.ZONE3_CAVES);

    public static final HytaleBiome ZONE3_CAVES_TUNDRA = register("Zone3_Caves_Tundra", "Tundra Caves",
            "Env_Zone3_Caves_Tundra", 0xFF609080, 0x609080, Category.ZONE3_CAVES);

    public static final HytaleBiome ZONE3_CAVES_SPIDER = register("Zone3_Caves_Spider", "Spider Caves",
            "Env_Zone3_Caves_Spider", 0xFF405040, 0x4A3A4A, Category.ZONE3_CAVES);

    public static final HytaleBiome ZONE3_CAVES_VOLCANIC_T1 = register("Zone3_Caves_Volcanic_T1", "Volcanic Caves T1",
            "Env_Zone3_Caves_Volcanic_T1", 0xFF8B4513, 0x8B2500, Category.ZONE3_CAVES);

    public static final HytaleBiome ZONE3_CAVES_VOLCANIC_T2 = register("Zone3_Caves_Volcanic_T2", "Volcanic Caves T2",
            "Env_Zone3_Caves_Volcanic_T2", 0xFF8B4513, 0xA03000, Category.ZONE3_CAVES);

    public static final HytaleBiome ZONE3_CAVES_VOLCANIC_T3 = register("Zone3_Caves_Volcanic_T3", "Volcanic Caves T3",
            "Env_Zone3_Caves_Volcanic_T3", 0xFF8B4513, 0xC04000, Category.ZONE3_CAVES);

    // ─── Zone 4: Devastated Lands (Volcanic) ──────────────────────────

    public static final HytaleBiome ZONE4_WASTES = register("Zone4_Wastes", "Wastes",
            "Env_Zone4_Wastes", 0xFF505020, 0x505020, Category.ZONE4_SURFACE);

    public static final HytaleBiome ZONE4_FORESTS = register("Zone4_Forests", "Dark Forests",
            "Env_Zone4_Forests", 0xFF304020, 0x304020, Category.ZONE4_SURFACE);

    public static final HytaleBiome ZONE4_JUNGLES = register("Zone4_Jungles", "Jungles",
            "Env_Zone4_Jungles", 0xFF1A6030, 0x1A6030, Category.ZONE4_SURFACE);

    public static final HytaleBiome ZONE4_VOLCANOES = register("Zone4_Volcanoes", "Volcanoes",
            "Env_Zone4_Volcanoes", 0xFF8B2500, 0x8B2500, Category.ZONE4_SURFACE);

    public static final HytaleBiome ZONE4_SHORES = register("Zone4_Shores", "Shores",
            "Env_Zone4_Shores", 0xFF706040, 0x706040, Category.ZONE4_SURFACE);

    public static final HytaleBiome ZONE4_CRUCIBLE = register("Zone4_Crucible", "Crucible",
            "Env_Zone4_Crucible", 0xFFA03000, 0xA03000, Category.ZONE4_SURFACE);

    // Zone 4 Caves
    public static final HytaleBiome ZONE4_CAVES = register("Zone4_Caves", "Caves",
            "Env_Zone4_Caves", 0xFF303020, 0x555555, Category.ZONE4_CAVES);

    public static final HytaleBiome ZONE4_CAVES_VOLCANIC = register("Zone4_Caves_Volcanic", "Volcanic Caves",
            "Env_Zone4_Caves_Volcanic", 0xFF8B2500, 0x8B2500, Category.ZONE4_CAVES);

    // ─── Ocean ─────────────────────────────────────────────────────────

    public static final HytaleBiome OCEAN = register("Ocean", "Ocean",
            "Env_Zone1_Shores", 0xFF4682B4, 0x4682B4, Category.OCEAN);

    // ─── Special ───────────────────────────────────────────────────────

    public static final HytaleBiome VOID = register("Void", "Void",
            "Env_Void", 0xFF202020, 0x202020, Category.SPECIAL);

    public static final HytaleBiome CREATIVE_HUB = register("Creative_Hub", "Creative Hub",
            "Env_Creative_Hub", 0xFF5B9E28, 0x5B9EFF, Category.SPECIAL);

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
        // Show surface biomes as main buttons, grouped by zone
        // Cave biomes will be accessible as "variations" of their zone
        List<Integer> order = new ArrayList<>();

        // Zone 1 surface biomes
        order.add(ZONE1_PLAINS.id);
        order.add(ZONE1_FORESTS.id);
        order.add(ZONE1_MOUNTAINS.id);
        order.add(ZONE1_SWAMPS.id);
        order.add(ZONE1_SHORES.id);
        order.add(ZONE1_AUTUMN.id);
        order.add(ZONE1_AZURE.id);
        order.add(ZONE1_KWEEBEC.id);
        order.add(ZONE1_TRORK.id);
        order.add(ZONE1_CAVES.id);

        // Zone 2 surface biomes
        order.add(ZONE2_DESERTS.id);
        order.add(ZONE2_SAVANNA.id);
        order.add(ZONE2_OASIS.id);
        order.add(ZONE2_PLATEAUS.id);
        order.add(ZONE2_SCRUB.id);
        order.add(ZONE2_SHORES.id);
        order.add(ZONE2_SCARAK.id);
        order.add(ZONE2_FERAN.id);
        order.add(ZONE2_CAVES.id);
        order.add(-1); // spacer

        // Zone 3 surface biomes
        order.add(ZONE3_FORESTS.id);
        order.add(ZONE3_TUNDRA.id);
        order.add(ZONE3_GLACIAL.id);
        order.add(ZONE3_MOUNTAINS.id);
        order.add(ZONE3_SHORES.id);
        order.add(ZONE3_GLACIAL_HENGES.id);
        order.add(ZONE3_HEDERA.id);
        order.add(ZONE3_OUTLANDER.id);
        order.add(ZONE3_CAVES.id);
        order.add(-1); // spacer

        // Zone 4 surface biomes
        order.add(ZONE4_WASTES.id);
        order.add(ZONE4_FORESTS.id);
        order.add(ZONE4_JUNGLES.id);
        order.add(ZONE4_VOLCANOES.id);
        order.add(ZONE4_SHORES.id);
        order.add(ZONE4_CRUCIBLE.id);
        order.add(ZONE4_CAVES.id);
        order.add(OCEAN.id);
        order.add(VOID.id);
        order.add(CREATIVE_HUB.id);

        return order.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Find a biome that best matches a terrain-derived biome name string
     * (used for auto-biome assignment from terrain in the exporter).
     */
    public static HytaleBiome fromTerrainBiomeName(String terrainBiome) {
        if (terrainBiome == null) return ZONE1_PLAINS;

        switch (terrainBiome) {
            case "Grassland":   return ZONE1_PLAINS;
            case "Forest":      return ZONE1_FORESTS;
            case "Tropical":    return ZONE4_JUNGLES;
            case "Swamp":       return ZONE1_SWAMPS;
            case "Savanna":     return ZONE2_SAVANNA;
            case "Desert":      return ZONE2_DESERTS;
            case "Tundra":      return ZONE3_TUNDRA;
            case "Mountain":    return ZONE1_MOUNTAINS;
            case "Volcanic":    return ZONE4_VOLCANOES;
            case "Ocean":       return OCEAN;
            case "Underground": return ZONE1_CAVES;
            default:            return ZONE1_PLAINS;
        }
    }
}
