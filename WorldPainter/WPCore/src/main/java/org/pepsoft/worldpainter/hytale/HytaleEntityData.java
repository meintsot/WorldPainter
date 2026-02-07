package org.pepsoft.worldpainter.hytale;

import java.util.*;

/**
 * Registry of all known Hytale entity/NPC types that can spawn in the world.
 * Organized by category (mammals, livestock, aquatic, hostile, etc.).
 *
 * <p>Data sourced from HytaleAssets/Server/Entity/ and Server/NPC/ directories.</p>
 *
 * <p>This registry is used by the {@link HytaleEntityLayer} to provide spawn
 * configuration metadata during export.</p>
 */
public final class HytaleEntityData {

    private final String id;
    private final String displayName;
    private final Category category;
    private final SpawnType spawnType;

    private HytaleEntityData(String id, String displayName, Category category, SpawnType spawnType) {
        this.id = id;
        this.displayName = displayName;
        this.category = category;
        this.spawnType = spawnType;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public Category getCategory() { return category; }
    public SpawnType getSpawnType() { return spawnType; }

    @Override
    public String toString() {
        return displayName + " (" + id + ")";
    }

    // ─── Categories ────────────────────────────────────────────────────

    public enum Category {
        MAMMALS("Mammals"),
        LIVESTOCK("Livestock"),
        REPTILES("Reptiles"),
        CRITTERS("Critters"),
        MYTHIC("Mythic Creatures"),
        VERMIN("Vermin"),
        AQUATIC_FRESH("Freshwater"),
        AQUATIC_MARINE("Marine"),
        AVIAN("Birds"),
        HOSTILE("Hostile/Aggressive"),
        NEUTRAL_NPC("Neutral NPCs"),
        UNDEAD("Undead"),
        VOID("Void Creatures"),
        BOSS("Bosses"),
        ELEMENTAL("Elementals");

        private final String displayName;
        Category(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }

    public enum SpawnType {
        PASSIVE,    // Won't attack players
        HOSTILE,    // Attacks players on sight
        NEUTRAL,    // Attacks only when provoked
        AQUATIC,    // Spawns in water
        BOSS        // Special boss entity
    }

    // ─── Static Registry ───────────────────────────────────────────────

    private static final List<HytaleEntityData> ALL = new ArrayList<>();
    private static final Map<String, HytaleEntityData> BY_ID = new HashMap<>();

    private static void reg(String id, String displayName, Category cat, SpawnType type) {
        HytaleEntityData e = new HytaleEntityData(id, displayName, cat, type);
        ALL.add(e);
        BY_ID.put(id, e);
    }

    static {
        // ── Mammals ──
        reg("Antelope",         "Antelope",         Category.MAMMALS, SpawnType.PASSIVE);
        reg("Armadillo",        "Armadillo",        Category.MAMMALS, SpawnType.PASSIVE);
        reg("Bear_Grizzly",     "Grizzly Bear",     Category.MAMMALS, SpawnType.NEUTRAL);
        reg("Bear_Polar",       "Polar Bear",       Category.MAMMALS, SpawnType.NEUTRAL);
        reg("Deer_Doe",         "Deer (Doe)",       Category.MAMMALS, SpawnType.PASSIVE);
        reg("Deer_Stag",        "Deer (Stag)",      Category.MAMMALS, SpawnType.PASSIVE);
        reg("Fox",              "Fox",              Category.MAMMALS, SpawnType.PASSIVE);
        reg("Hyena",            "Hyena",            Category.MAMMALS, SpawnType.HOSTILE);
        reg("Leopard_Snow",     "Snow Leopard",     Category.MAMMALS, SpawnType.NEUTRAL);
        reg("Moose_Bull",       "Moose (Bull)",     Category.MAMMALS, SpawnType.NEUTRAL);
        reg("Moose_Cow",        "Moose (Cow)",      Category.MAMMALS, SpawnType.PASSIVE);
        reg("Mosshorn",         "Mosshorn",         Category.MAMMALS, SpawnType.PASSIVE);
        reg("Tiger_Sabertooth", "Sabertooth Tiger",  Category.MAMMALS, SpawnType.HOSTILE);
        reg("Wolf_Black",       "Black Wolf",       Category.MAMMALS, SpawnType.HOSTILE);
        reg("Wolf_White",       "White Wolf",       Category.MAMMALS, SpawnType.HOSTILE);

        // ── Livestock ──
        reg("Bison",            "Bison",            Category.LIVESTOCK, SpawnType.PASSIVE);
        reg("Boar",             "Boar",             Category.LIVESTOCK, SpawnType.PASSIVE);
        reg("Bunny",            "Bunny",            Category.LIVESTOCK, SpawnType.PASSIVE);
        reg("Camel",            "Camel",            Category.LIVESTOCK, SpawnType.PASSIVE);
        reg("Chicken",          "Chicken",          Category.LIVESTOCK, SpawnType.PASSIVE);
        reg("Chicken_Desert",   "Desert Chicken",   Category.LIVESTOCK, SpawnType.PASSIVE);
        reg("Cow",              "Cow",              Category.LIVESTOCK, SpawnType.PASSIVE);
        reg("Goat",             "Goat",             Category.LIVESTOCK, SpawnType.PASSIVE);
        reg("Horse",            "Horse",            Category.LIVESTOCK, SpawnType.PASSIVE);
        reg("Mouflon",          "Mouflon",          Category.LIVESTOCK, SpawnType.PASSIVE);
        reg("Pig",              "Pig",              Category.LIVESTOCK, SpawnType.PASSIVE);
        reg("Pig_Wild",         "Wild Pig",         Category.LIVESTOCK, SpawnType.PASSIVE);
        reg("Rabbit",           "Rabbit",           Category.LIVESTOCK, SpawnType.PASSIVE);
        reg("Ram",              "Ram",              Category.LIVESTOCK, SpawnType.PASSIVE);
        reg("Sheep",            "Sheep",            Category.LIVESTOCK, SpawnType.PASSIVE);
        reg("Skrill",           "Skrill",           Category.LIVESTOCK, SpawnType.PASSIVE);
        reg("Turkey",           "Turkey",           Category.LIVESTOCK, SpawnType.PASSIVE);
        reg("Warthog",          "Warthog",          Category.LIVESTOCK, SpawnType.PASSIVE);

        // ── Reptiles ──
        reg("Crocodile",        "Crocodile",        Category.REPTILES, SpawnType.HOSTILE);
        reg("Lizard_Sand",      "Sand Lizard",      Category.REPTILES, SpawnType.PASSIVE);
        reg("Raptor_Cave",      "Cave Raptor",      Category.REPTILES, SpawnType.HOSTILE);
        reg("Rex_Cave",         "Cave Rex",         Category.REPTILES, SpawnType.HOSTILE);
        reg("Toad_Rhino",       "Rhino Toad",       Category.REPTILES, SpawnType.NEUTRAL);
        reg("Toad_Rhino_Magma", "Magma Toad",       Category.REPTILES, SpawnType.HOSTILE);
        reg("Tortoise",         "Tortoise",         Category.REPTILES, SpawnType.PASSIVE);

        // ── Critters ──
        reg("Frog_Blue",        "Blue Frog",        Category.CRITTERS, SpawnType.PASSIVE);
        reg("Frog_Green",       "Green Frog",       Category.CRITTERS, SpawnType.PASSIVE);
        reg("Frog_Orange",      "Orange Frog",      Category.CRITTERS, SpawnType.PASSIVE);
        reg("Gecko",            "Gecko",            Category.CRITTERS, SpawnType.PASSIVE);
        reg("Meerkat",          "Meerkat",          Category.CRITTERS, SpawnType.PASSIVE);
        reg("Mouse",            "Mouse",            Category.CRITTERS, SpawnType.PASSIVE);
        reg("Squirrel",         "Squirrel",         Category.CRITTERS, SpawnType.PASSIVE);

        // ── Mythic ──
        reg("Cactee",           "Cactee",           Category.MYTHIC, SpawnType.NEUTRAL);
        reg("Emberwulf",        "Emberwulf",        Category.MYTHIC, SpawnType.HOSTILE);
        reg("Fen_Stalker",      "Fen Stalker",      Category.MYTHIC, SpawnType.HOSTILE);
        reg("Hatworm",          "Hatworm",          Category.MYTHIC, SpawnType.PASSIVE);
        reg("Snapdragon",       "Snapdragon",       Category.MYTHIC, SpawnType.HOSTILE);
        reg("Spark_Living",     "Living Spark",      Category.MYTHIC, SpawnType.NEUTRAL);
        reg("Trillodon",        "Trillodon",        Category.MYTHIC, SpawnType.NEUTRAL);
        reg("Yeti",             "Yeti",             Category.MYTHIC, SpawnType.HOSTILE);

        // ── Vermin ──
        reg("Larva_Silk",       "Silk Larva",       Category.VERMIN, SpawnType.PASSIVE);
        reg("Molerat",          "Molerat",          Category.VERMIN, SpawnType.HOSTILE);
        reg("Rat",              "Rat",              Category.VERMIN, SpawnType.HOSTILE);
        reg("Scorpion",         "Scorpion",         Category.VERMIN, SpawnType.HOSTILE);
        reg("Slug_Magma",       "Magma Slug",       Category.VERMIN, SpawnType.HOSTILE);
        reg("Snail_Frost",      "Frost Snail",      Category.VERMIN, SpawnType.PASSIVE);
        reg("Snail_Magma",      "Magma Snail",      Category.VERMIN, SpawnType.HOSTILE);
        reg("Snake_Cobra",      "Cobra",            Category.VERMIN, SpawnType.HOSTILE);
        reg("Snake_Marsh",      "Marsh Snake",      Category.VERMIN, SpawnType.HOSTILE);
        reg("Snake_Rattle",     "Rattlesnake",      Category.VERMIN, SpawnType.HOSTILE);
        reg("Spider",           "Spider",           Category.VERMIN, SpawnType.HOSTILE);
        reg("Spider_Cave",      "Cave Spider",      Category.VERMIN, SpawnType.HOSTILE);

        // ── Aquatic (Freshwater) ──
        reg("Bluegill",         "Bluegill",         Category.AQUATIC_FRESH, SpawnType.AQUATIC);
        reg("Catfish",          "Catfish",          Category.AQUATIC_FRESH, SpawnType.AQUATIC);
        reg("Frostgill",        "Frostgill",        Category.AQUATIC_FRESH, SpawnType.AQUATIC);
        reg("Minnow",           "Minnow",           Category.AQUATIC_FRESH, SpawnType.AQUATIC);
        reg("Pike",             "Pike",             Category.AQUATIC_FRESH, SpawnType.AQUATIC);
        reg("Piranha",          "Piranha",          Category.AQUATIC_FRESH, SpawnType.AQUATIC);
        reg("Piranha_Black",    "Black Piranha",    Category.AQUATIC_FRESH, SpawnType.AQUATIC);
        reg("Salmon",           "Salmon",           Category.AQUATIC_FRESH, SpawnType.AQUATIC);
        reg("Snapjaw",          "Snapjaw",          Category.AQUATIC_FRESH, SpawnType.AQUATIC);
        reg("Trout_Rainbow",    "Rainbow Trout",    Category.AQUATIC_FRESH, SpawnType.AQUATIC);

        // ── Aquatic (Marine) ──
        reg("Clownfish",        "Clownfish",        Category.AQUATIC_MARINE, SpawnType.AQUATIC);
        reg("Crab",             "Crab",             Category.AQUATIC_MARINE, SpawnType.AQUATIC);
        reg("Jellyfish_Blue",   "Blue Jellyfish",   Category.AQUATIC_MARINE, SpawnType.AQUATIC);
        reg("Jellyfish_Cyan",   "Cyan Jellyfish",   Category.AQUATIC_MARINE, SpawnType.AQUATIC);
        reg("Jellyfish_Green",  "Green Jellyfish",  Category.AQUATIC_MARINE, SpawnType.AQUATIC);
        reg("Jellyfish_Red",    "Red Jellyfish",    Category.AQUATIC_MARINE, SpawnType.AQUATIC);
        reg("Jellyfish_Yellow", "Yellow Jellyfish",  Category.AQUATIC_MARINE, SpawnType.AQUATIC);
        reg("Jellyfish_Man_Of_War", "Man-of-War",   Category.AQUATIC_MARINE, SpawnType.AQUATIC);
        reg("Lobster",          "Lobster",          Category.AQUATIC_MARINE, SpawnType.AQUATIC);
        reg("Pufferfish",       "Pufferfish",       Category.AQUATIC_MARINE, SpawnType.AQUATIC);
        reg("Tang_Blue",        "Blue Tang",        Category.AQUATIC_MARINE, SpawnType.AQUATIC);
        reg("Tang_Chevron",     "Chevron Tang",     Category.AQUATIC_MARINE, SpawnType.AQUATIC);
        reg("Tang_Lemon_Peel",  "Lemon Peel Tang",  Category.AQUATIC_MARINE, SpawnType.AQUATIC);
        reg("Tang_Sailfin",     "Sailfin Tang",     Category.AQUATIC_MARINE, SpawnType.AQUATIC);

        // ── Avian ──
        reg("Bat",              "Bat",              Category.AVIAN, SpawnType.PASSIVE);
        reg("Bat_Ice",          "Ice Bat",          Category.AVIAN, SpawnType.PASSIVE);
        reg("Bluebird",         "Bluebird",         Category.AVIAN, SpawnType.PASSIVE);
        reg("Crow",             "Crow",             Category.AVIAN, SpawnType.PASSIVE);
        reg("Duck",             "Duck",             Category.AVIAN, SpawnType.PASSIVE);
        reg("Finch_Green",      "Green Finch",      Category.AVIAN, SpawnType.PASSIVE);
        reg("Flamingo",         "Flamingo",         Category.AVIAN, SpawnType.PASSIVE);
        reg("Hawk",             "Hawk",             Category.AVIAN, SpawnType.NEUTRAL);
        reg("Owl_Brown",        "Brown Owl",        Category.AVIAN, SpawnType.PASSIVE);
        reg("Owl_Snow",         "Snow Owl",         Category.AVIAN, SpawnType.PASSIVE);
        reg("Parrot",           "Parrot",           Category.AVIAN, SpawnType.PASSIVE);
        reg("Penguin",          "Penguin",           Category.AVIAN, SpawnType.PASSIVE);
        reg("Pigeon",           "Pigeon",           Category.AVIAN, SpawnType.PASSIVE);
        reg("Pterodactyl",      "Pterodactyl",      Category.AVIAN, SpawnType.NEUTRAL);
        reg("Raven",            "Raven",            Category.AVIAN, SpawnType.PASSIVE);
        reg("Sparrow",          "Sparrow",          Category.AVIAN, SpawnType.PASSIVE);
        reg("Tetrabird",        "Tetrabird",        Category.AVIAN, SpawnType.NEUTRAL);
        reg("Vulture",          "Vulture",          Category.AVIAN, SpawnType.NEUTRAL);
        reg("Woodpecker",       "Woodpecker",       Category.AVIAN, SpawnType.PASSIVE);

        // ── Hostile / Aggressive (Intelligent) ──
        reg("Goblin_Duke",      "Goblin Duke",      Category.HOSTILE, SpawnType.HOSTILE);
        reg("Goblin_Hermit",    "Goblin Hermit",    Category.HOSTILE, SpawnType.HOSTILE);
        reg("Goblin_Lobber",    "Goblin Lobber",    Category.HOSTILE, SpawnType.HOSTILE);
        reg("Goblin_Miner",     "Goblin Miner",     Category.HOSTILE, SpawnType.HOSTILE);
        reg("Goblin_Ogre",      "Goblin Ogre",      Category.HOSTILE, SpawnType.HOSTILE);
        reg("Goblin_Scavenger", "Goblin Scavenger",  Category.HOSTILE, SpawnType.HOSTILE);
        reg("Goblin_Scrapper",  "Goblin Scrapper",   Category.HOSTILE, SpawnType.HOSTILE);
        reg("Goblin_Thief",     "Goblin Thief",     Category.HOSTILE, SpawnType.HOSTILE);
        reg("Scarak_Broodmother", "Scarak Broodmother", Category.HOSTILE, SpawnType.HOSTILE);
        reg("Scarak_Defender",  "Scarak Defender",  Category.HOSTILE, SpawnType.HOSTILE);
        reg("Scarak_Fighter",   "Scarak Fighter",   Category.HOSTILE, SpawnType.HOSTILE);
        reg("Scarak_Seeker",    "Scarak Seeker",    Category.HOSTILE, SpawnType.HOSTILE);
        reg("Scarak_Louse",     "Scarak Louse",     Category.HOSTILE, SpawnType.HOSTILE);

        // ── Neutral NPCs ──
        reg("Bramblekin",       "Bramblekin",       Category.NEUTRAL_NPC, SpawnType.NEUTRAL);
        reg("Bramblekin_Shaman","Bramblekin Shaman", Category.NEUTRAL_NPC, SpawnType.NEUTRAL);
        reg("Feran",            "Feran",            Category.NEUTRAL_NPC, SpawnType.NEUTRAL);
        reg("Kweebec",          "Kweebec",          Category.NEUTRAL_NPC, SpawnType.NEUTRAL);

        // ── Undead ──
        reg("Ghoul",            "Ghoul",            Category.UNDEAD, SpawnType.HOSTILE);
        reg("Hound_Bleached",   "Bleached Hound",   Category.UNDEAD, SpawnType.HOSTILE);
        reg("Shadow_Knight",    "Shadow Knight",    Category.UNDEAD, SpawnType.HOSTILE);
        reg("Werewolf",         "Werewolf",         Category.UNDEAD, SpawnType.HOSTILE);
        reg("Wraith",           "Wraith",           Category.UNDEAD, SpawnType.HOSTILE);
        reg("Wraith_Lantern",   "Lantern Wraith",   Category.UNDEAD, SpawnType.HOSTILE);

        // ── Void ──
        reg("Crawler_Void",     "Void Crawler",     Category.VOID, SpawnType.HOSTILE);
        reg("Eye_Void",         "Void Eye",         Category.VOID, SpawnType.HOSTILE);
        reg("Larva_Void",       "Void Larva",       Category.VOID, SpawnType.HOSTILE);
        reg("Spawn_Void",       "Void Spawn",       Category.VOID, SpawnType.HOSTILE);
        reg("Spectre_Void",     "Void Spectre",     Category.VOID, SpawnType.HOSTILE);

        // ── Bosses ──
        reg("Dragon_Fire",      "Fire Dragon",      Category.BOSS, SpawnType.BOSS);
        reg("Dragon_Frost",     "Frost Dragon",     Category.BOSS, SpawnType.BOSS);

        // ── Elementals ──
        reg("Golem_Crystal_Earth",   "Earth Crystal Golem",   Category.ELEMENTAL, SpawnType.HOSTILE);
        reg("Golem_Crystal_Flame",   "Flame Crystal Golem",   Category.ELEMENTAL, SpawnType.HOSTILE);
        reg("Golem_Crystal_Frost",   "Frost Crystal Golem",   Category.ELEMENTAL, SpawnType.HOSTILE);
        reg("Golem_Crystal_Sand",    "Sand Crystal Golem",    Category.ELEMENTAL, SpawnType.HOSTILE);
        reg("Golem_Crystal_Thunder", "Thunder Crystal Golem", Category.ELEMENTAL, SpawnType.HOSTILE);
        reg("Golem_Firesteel",       "Firesteel Golem",       Category.ELEMENTAL, SpawnType.HOSTILE);
        reg("Golem_Guardian_Void",   "Void Guardian Golem",   Category.ELEMENTAL, SpawnType.HOSTILE);
        reg("Spirit_Ember",          "Ember Spirit",          Category.ELEMENTAL, SpawnType.NEUTRAL);
        reg("Spirit_Frost",          "Frost Spirit",          Category.ELEMENTAL, SpawnType.NEUTRAL);
        reg("Spirit_Root",           "Root Spirit",           Category.ELEMENTAL, SpawnType.NEUTRAL);
        reg("Spirit_Thunder",        "Thunder Spirit",        Category.ELEMENTAL, SpawnType.NEUTRAL);
    }

    // ─── Lookup ────────────────────────────────────────────────────────

    /** Get entity by ID (e.g. "Wolf_Black"). Returns null if not found. */
    public static HytaleEntityData getById(String id) {
        return BY_ID.get(id);
    }

    /** Get all registered entities. */
    public static List<HytaleEntityData> getAll() {
        return Collections.unmodifiableList(ALL);
    }

    /** Get all entities in a category. */
    public static List<HytaleEntityData> getByCategory(Category category) {
        List<HytaleEntityData> result = new ArrayList<>();
        for (HytaleEntityData e : ALL) {
            if (e.category == category) {
                result.add(e);
            }
        }
        return result;
    }

    /** Get all entities of a spawn type. */
    public static List<HytaleEntityData> getBySpawnType(SpawnType spawnType) {
        List<HytaleEntityData> result = new ArrayList<>();
        for (HytaleEntityData e : ALL) {
            if (e.spawnType == spawnType) {
                result.add(e);
            }
        }
        return result;
    }

    /** Total number of registered entities. */
    public static int getCount() {
        return ALL.size();
    }
}
