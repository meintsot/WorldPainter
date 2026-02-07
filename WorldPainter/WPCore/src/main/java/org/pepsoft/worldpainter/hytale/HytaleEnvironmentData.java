package org.pepsoft.worldpainter.hytale;

import java.util.*;

/**
 * Registry of all known Hytale environments. Each environment defines weather
 * forecasts, water tint, spawn density, and visual settings for an area of the
 * world. Environments are referenced by biomes and can be overridden per-column
 * using the {@link HytaleEnvironmentLayer}.
 *
 * <p>Data sourced from HytaleAssets/Server/Environments/ JSON files.</p>
 */
public final class HytaleEnvironmentData {

    private final int id;
    private final String name;
    private final String displayName;
    private final String parent;
    private final String waterTint;
    private final float spawnDensity;
    private final Category category;

    private HytaleEnvironmentData(int id, String name, String displayName, String parent,
                                   String waterTint, float spawnDensity, Category category) {
        this.id = id;
        this.name = name;
        this.displayName = displayName;
        this.parent = parent;
        this.waterTint = waterTint;
        this.spawnDensity = spawnDensity;
        this.category = category;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getDisplayName() { return displayName; }
    public String getParent() { return parent; }
    public String getWaterTint() { return waterTint; }
    public float getSpawnDensity() { return spawnDensity; }
    public Category getCategory() { return category; }

    @Override
    public String toString() {
        return displayName + " (" + name + ")";
    }

    // ─── Category ──────────────────────────────────────────────────────

    public enum Category {
        ZONE0("Zone 0 — Ocean"),
        ZONE1("Zone 1 — Emerald Grove"),
        ZONE2("Zone 2 — Howling Sands"),
        ZONE3("Zone 3 — Borea"),
        ZONE4("Zone 4 — Devastated Lands"),
        SPECIAL("Special"),
        LEGACY("Legacy");

        private final String displayName;
        Category(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }

    // ─── Static Registry ───────────────────────────────────────────────

    private static final List<HytaleEnvironmentData> ALL = new ArrayList<>();
    private static final Map<Integer, HytaleEnvironmentData> BY_ID = new HashMap<>();
    private static final Map<String, HytaleEnvironmentData> BY_NAME = new HashMap<>();

    private static int nextId = 1;

    private static HytaleEnvironmentData reg(String name, String displayName, String parent,
                                              String waterTint, float spawnDensity, Category category) {
        int id = nextId++;
        HytaleEnvironmentData env = new HytaleEnvironmentData(id, name, displayName, parent, waterTint, spawnDensity, category);
        ALL.add(env);
        BY_ID.put(id, env);
        BY_NAME.put(name, env);
        return env;
    }

    static {
        // ── Zone 0 (Ocean) ──
        reg("Env_Zone0",           "Ocean Deep",          null,         "#1070b0", 0.3f, Category.ZONE0);
        reg("Env_Zone0_Cold",      "Ocean Cold",          "Env_Zone0",  "#2076b5", 0.3f, Category.ZONE0);
        reg("Env_Zone0_Temperate", "Ocean Temperate",     "Env_Zone0",  "#1983d9", 0.4f, Category.ZONE0);
        reg("Env_Zone0_Warm",      "Ocean Warm",          "Env_Zone0",  "#198dea", 0.4f, Category.ZONE0);

        // ── Zone 1 (Emerald Grove) ──
        reg("Env_Zone1",           "Zone 1 Base",         null,         "#1983d9", 0.5f, Category.ZONE1);
        reg("Env_Zone1_Plains",    "Plains",              "Env_Zone1",  "#1983d9", 0.5f, Category.ZONE1);
        reg("Env_Zone1_Forests",   "Forests",             "Env_Zone1",  "#1983d9", 0.6f, Category.ZONE1);
        reg("Env_Zone1_Autumn",    "Autumn Forest",       "Env_Zone1",  "#1983d9", 0.5f, Category.ZONE1);
        reg("Env_Zone1_Azure",     "Azure Forest",        "Env_Zone1",  "#20a0ff", 0.5f, Category.ZONE1);
        reg("Env_Zone1_Mountains", "Mountains",           "Env_Zone1",  "#1983d9", 0.4f, Category.ZONE1);
        reg("Env_Zone1_Swamps",    "Swamps",              "Env_Zone1",  "#66682b", 0.7f, Category.ZONE1);
        reg("Env_Zone1_Shores",    "Shores",              "Env_Zone1",  "#1983d9", 0.3f, Category.ZONE1);
        reg("Env_Zone1_Kweebec",   "Kweebec Village",     "Env_Zone1",  "#1983d9", 0.4f, Category.ZONE1);
        reg("Env_Zone1_Trork",     "Trork Camp",          "Env_Zone1",  "#1983d9", 0.6f, Category.ZONE1);
        reg("Env_Zone1_Caves",     "Caves",               "Env_Zone1",  "#1983d9", 0.4f, Category.ZONE1);
        reg("Env_Zone1_Caves_Forests",   "Cave — Forests",   "Env_Zone1_Caves", "#1983d9", 0.5f, Category.ZONE1);
        reg("Env_Zone1_Caves_Goblins",   "Cave — Goblins",   "Env_Zone1_Caves", "#1983d9", 0.6f, Category.ZONE1);
        reg("Env_Zone1_Caves_Mountains", "Cave — Mountains", "Env_Zone1_Caves", "#1983d9", 0.4f, Category.ZONE1);
        reg("Env_Zone1_Caves_Plains",    "Cave — Plains",    "Env_Zone1_Caves", "#1983d9", 0.4f, Category.ZONE1);
        reg("Env_Zone1_Caves_Rats",      "Cave — Rats",      "Env_Zone1_Caves", "#1983d9", 0.5f, Category.ZONE1);
        reg("Env_Zone1_Caves_Spiders",   "Cave — Spiders",   "Env_Zone1_Caves", "#1983d9", 0.6f, Category.ZONE1);
        reg("Env_Zone1_Caves_Swamps",    "Cave — Swamps",    "Env_Zone1_Caves", "#66682b", 0.6f, Category.ZONE1);
        reg("Env_Zone1_Caves_Volcanic_T1", "Cave — Volcanic T1", "Env_Zone1_Caves", "#1983d9", 0.3f, Category.ZONE1);
        reg("Env_Zone1_Caves_Volcanic_T2", "Cave — Volcanic T2", "Env_Zone1_Caves", "#1983d9", 0.4f, Category.ZONE1);
        reg("Env_Zone1_Caves_Volcanic_T3", "Cave — Volcanic T3", "Env_Zone1_Caves", "#1983d9", 0.5f, Category.ZONE1);
        reg("Env_Zone1_Dungeons",    "Dungeons",           "Env_Zone1",  "#1983d9", 0.7f, Category.ZONE1);
        reg("Env_Zone1_Encounters",  "Encounters",         "Env_Zone1",  "#1983d9", 0.6f, Category.ZONE1);
        reg("Env_Zone1_Mage_Towers", "Mage Towers",        "Env_Zone1",  "#1983d9", 0.5f, Category.ZONE1);
        reg("Env_Zone1_Mineshafts",  "Mineshafts",         "Env_Zone1",  "#1983d9", 0.4f, Category.ZONE1);

        // ── Zone 2 (Howling Sands) ──
        reg("Env_Zone2",            "Zone 2 Base",         null,         "#198dea", 0.4f, Category.ZONE2);
        reg("Env_Zone2_Deserts",    "Deserts",             "Env_Zone2",  "#198dea", 0.3f, Category.ZONE2);
        reg("Env_Zone2_Savanna",    "Savanna",             "Env_Zone2",  "#198dea", 0.5f, Category.ZONE2);
        reg("Env_Zone2_Scrub",      "Scrub",               "Env_Zone2",  "#198dea", 0.4f, Category.ZONE2);
        reg("Env_Zone2_Plateaus",   "Plateaus",            "Env_Zone2",  "#198dea", 0.4f, Category.ZONE2);
        reg("Env_Zone2_Oasis",      "Oasis",               "Env_Zone2",  "#30b8c0", 0.5f, Category.ZONE2);
        reg("Env_Zone2_Scarak",     "Scarak Territory",    "Env_Zone2",  "#198dea", 0.7f, Category.ZONE2);
        reg("Env_Zone2_Feran",      "Feran Territory",     "Env_Zone2",  "#198dea", 0.5f, Category.ZONE2);
        reg("Env_Zone2_Shores",     "Shores",              "Env_Zone2",  "#198dea", 0.3f, Category.ZONE2);
        reg("Env_Zone2_Caves",      "Caves",               "Env_Zone2",  "#198dea", 0.4f, Category.ZONE2);
        reg("Env_Zone2_Caves_Deserts",   "Cave — Deserts",   "Env_Zone2_Caves", "#198dea", 0.3f, Category.ZONE2);
        reg("Env_Zone2_Caves_Goblins",   "Cave — Goblins",   "Env_Zone2_Caves", "#198dea", 0.5f, Category.ZONE2);
        reg("Env_Zone2_Caves_Plateaus",  "Cave — Plateaus",  "Env_Zone2_Caves", "#198dea", 0.4f, Category.ZONE2);
        reg("Env_Zone2_Caves_Rats",      "Cave — Rats",      "Env_Zone2_Caves", "#198dea", 0.5f, Category.ZONE2);
        reg("Env_Zone2_Caves_Savanna",   "Cave — Savanna",   "Env_Zone2_Caves", "#198dea", 0.4f, Category.ZONE2);
        reg("Env_Zone2_Caves_Scarak",    "Cave — Scarak",    "Env_Zone2_Caves", "#198dea", 0.6f, Category.ZONE2);
        reg("Env_Zone2_Caves_Scrub",     "Cave — Scrub",     "Env_Zone2_Caves", "#198dea", 0.4f, Category.ZONE2);
        reg("Env_Zone2_Caves_Volcanic_T1", "Cave — Volcanic T1", "Env_Zone2_Caves", "#198dea", 0.3f, Category.ZONE2);
        reg("Env_Zone2_Caves_Volcanic_T2", "Cave — Volcanic T2", "Env_Zone2_Caves", "#198dea", 0.4f, Category.ZONE2);
        reg("Env_Zone2_Caves_Volcanic_T3", "Cave — Volcanic T3", "Env_Zone2_Caves", "#198dea", 0.5f, Category.ZONE2);
        reg("Env_Zone2_Dungeons",    "Dungeons",           "Env_Zone2",  "#198dea", 0.7f, Category.ZONE2);
        reg("Env_Zone2_Encounters",  "Encounters",         "Env_Zone2",  "#198dea", 0.6f, Category.ZONE2);
        reg("Env_Zone2_Mage_Towers", "Mage Towers",        "Env_Zone2",  "#198dea", 0.5f, Category.ZONE2);
        reg("Env_Zone2_Mineshafts",  "Mineshafts",         "Env_Zone2",  "#198dea", 0.4f, Category.ZONE2);

        // ── Zone 3 (Borea) ──
        reg("Env_Zone3",            "Zone 3 Base",         null,         "#2076b5", 0.4f, Category.ZONE3);
        reg("Env_Zone3_Forests",    "Forests",             "Env_Zone3",  "#2076b5", 0.5f, Category.ZONE3);
        reg("Env_Zone3_Tundra",     "Tundra",              "Env_Zone3",  "#2076b5", 0.3f, Category.ZONE3);
        reg("Env_Zone3_Mountains",  "Mountains",           "Env_Zone3",  "#2076b5", 0.3f, Category.ZONE3);
        reg("Env_Zone3_Glacial",    "Glacial",             "Env_Zone3",  "#a0d8ef", 0.2f, Category.ZONE3);
        reg("Env_Zone3_Glacial_Henges", "Glacial Henges",  "Env_Zone3_Glacial", "#a0d8ef", 0.3f, Category.ZONE3);
        reg("Env_Zone3_Hedera",     "Hedera Village",      "Env_Zone3",  "#2076b5", 0.4f, Category.ZONE3);
        reg("Env_Zone3_Outlander",  "Outlander Camp",      "Env_Zone3",  "#2076b5", 0.6f, Category.ZONE3);
        reg("Env_Zone3_Shores",     "Shores",              "Env_Zone3",  "#2076b5", 0.3f, Category.ZONE3);
        reg("Env_Zone3_Caves",      "Caves",               "Env_Zone3",  "#2076b5", 0.4f, Category.ZONE3);
        reg("Env_Zone3_Caves_Forests",  "Cave — Forests",   "Env_Zone3_Caves", "#2076b5", 0.5f, Category.ZONE3);
        reg("Env_Zone3_Caves_Glacial",  "Cave — Glacial",   "Env_Zone3_Caves", "#a0d8ef", 0.3f, Category.ZONE3);
        reg("Env_Zone3_Caves_Mountains", "Cave — Mountains","Env_Zone3_Caves", "#2076b5", 0.3f, Category.ZONE3);
        reg("Env_Zone3_Caves_Spider",   "Cave — Spider",    "Env_Zone3_Caves", "#2076b5", 0.6f, Category.ZONE3);
        reg("Env_Zone3_Caves_Tundra",   "Cave — Tundra",    "Env_Zone3_Caves", "#2076b5", 0.3f, Category.ZONE3);
        reg("Env_Zone3_Caves_Volcanic_T1", "Cave — Volcanic T1", "Env_Zone3_Caves", "#2076b5", 0.3f, Category.ZONE3);
        reg("Env_Zone3_Caves_Volcanic_T2", "Cave — Volcanic T2", "Env_Zone3_Caves", "#2076b5", 0.4f, Category.ZONE3);
        reg("Env_Zone3_Caves_Volcanic_T3", "Cave — Volcanic T3", "Env_Zone3_Caves", "#2076b5", 0.5f, Category.ZONE3);
        reg("Env_Zone3_Dungeons",    "Dungeons",           "Env_Zone3",  "#2076b5", 0.7f, Category.ZONE3);
        reg("Env_Zone3_Encounters",  "Encounters",         "Env_Zone3",  "#2076b5", 0.6f, Category.ZONE3);
        reg("Env_Zone3_Mage_Towers", "Mage Towers",        "Env_Zone3",  "#2076b5", 0.5f, Category.ZONE3);
        reg("Env_Zone3_Mineshafts",  "Mineshafts",         "Env_Zone3",  "#2076b5", 0.4f, Category.ZONE3);

        // ── Zone 4 (Devastated Lands) ──
        reg("Env_Zone4",            "Zone 4 Base",         null,         "#667030", 0.4f, Category.ZONE4);
        reg("Env_Zone4_Forests",    "Forests",             "Env_Zone4",  "#667030", 0.5f, Category.ZONE4);
        reg("Env_Zone4_Jungles",    "Jungles",             "Env_Zone4",  "#4a6020", 0.6f, Category.ZONE4);
        reg("Env_Zone4_Wastes",     "Wastes",              "Env_Zone4",  "#667030", 0.3f, Category.ZONE4);
        reg("Env_Zone4_Volcanoes",  "Volcanoes",           "Env_Zone4",  "#c04020", 0.3f, Category.ZONE4);
        reg("Env_Zone4_Crucible",   "Crucible",            "Env_Zone4",  "#667030", 0.5f, Category.ZONE4);
        reg("Env_Zone4_Shores",     "Shores",              "Env_Zone4",  "#667030", 0.3f, Category.ZONE4);
        reg("Env_Zone4_Sewers",     "Sewers",              "Env_Zone4",  "#667030", 0.5f, Category.ZONE4);
        reg("Env_Zone4_Caves",      "Caves",               "Env_Zone4",  "#667030", 0.4f, Category.ZONE4);
        reg("Env_Zone4_Caves_Volcanic", "Cave — Volcanic",  "Env_Zone4_Caves", "#c04020", 0.4f, Category.ZONE4);
        reg("Env_Zone4_Dungeons",    "Dungeons",           "Env_Zone4",  "#667030", 0.7f, Category.ZONE4);
        reg("Env_Zone4_Encounters",  "Encounters",         "Env_Zone4",  "#667030", 0.6f, Category.ZONE4);
        reg("Env_Zone4_Mage_Towers", "Mage Towers",        "Env_Zone4",  "#667030", 0.5f, Category.ZONE4);

        // ── Special / Unique ──
        reg("Default",                         "Default",                 null, "#1983d9", 0.5f, Category.SPECIAL);
        reg("Env_Void",                        "Void",                    null, null,      0.0f, Category.SPECIAL);
        reg("Env_Creative_Hub",                "Creative Hub",            null, "#1983d9", 0.0f, Category.SPECIAL);
        reg("Env_Temple_of_Gaia",              "Temple of Gaia",          null, "#1983d9", 0.3f, Category.SPECIAL);
        reg("Env_Forgotten_Temple_Base",       "Forgotten Temple — Base",     null, "#1983d9", 0.3f, Category.SPECIAL);
        reg("Env_Forgotten_Temple_Exterior",   "Forgotten Temple — Exterior", null, "#1983d9", 0.3f, Category.SPECIAL);
        reg("Env_Forgotten_Temple_Heart",      "Forgotten Temple — Heart",    null, "#1983d9", 0.4f, Category.SPECIAL);
        reg("Env_Forgotten_Temple_Interior_Grand", "Forgotten Temple — Grand Interior", null, "#1983d9", 0.3f, Category.SPECIAL);
        reg("Env_Forgotten_Temple_Interior_Small", "Forgotten Temple — Small Interior", null, "#1983d9", 0.3f, Category.SPECIAL);
        reg("Env_Forgotten_Temple_Interior_Tent",  "Forgotten Temple — Tent", null, "#1983d9", 0.2f, Category.SPECIAL);
        reg("Env_Portals_Hedera",              "Portal — Hedera",         null, "#2076b5", 0.3f, Category.SPECIAL);
        reg("Env_Portals_Oasis",               "Portal — Oasis",          null, "#30b8c0", 0.3f, Category.SPECIAL);
    }

    // ─── Lookup ────────────────────────────────────────────────────────

    /** Get environment by sequential ID (1-based). Returns null if not found. */
    public static HytaleEnvironmentData getById(int id) {
        return BY_ID.get(id);
    }

    /** Get environment by name (e.g. "Env_Zone1_Forests"). Returns null if not found. */
    public static HytaleEnvironmentData getByName(String name) {
        return BY_NAME.get(name);
    }

    /** Get all environments. */
    public static List<HytaleEnvironmentData> getAll() {
        return Collections.unmodifiableList(ALL);
    }

    /** Get all environments in a category. */
    public static List<HytaleEnvironmentData> getByCategory(Category category) {
        List<HytaleEnvironmentData> result = new ArrayList<>();
        for (HytaleEnvironmentData env : ALL) {
            if (env.category == category) {
                result.add(env);
            }
        }
        return result;
    }

    /** Total number of registered environments. */
    public static int getCount() {
        return ALL.size();
    }
}
