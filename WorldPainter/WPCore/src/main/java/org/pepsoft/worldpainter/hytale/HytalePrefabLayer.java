package org.pepsoft.worldpainter.hytale;

import org.pepsoft.worldpainter.layers.Layer;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.Serializable;
import java.util.*;
import java.util.List;

/**
 * Layer for placing Hytale prefabs on the world. Users can select from built-in
 * Hytale prefabs discovered from the assets directory and paint them into the world.
 * The layer value (NIBBLE, 0-15) selects a prefab category. Specific prefab selection
 * is handled through the layer settings.
 *
 * <p>During export, prefab placement markers are written into chunk metadata,
 * which a Hytale server uses to instantiate actual prefab structures.</p>
 */
public class HytalePrefabLayer extends Layer {

    private HytalePrefabLayer() {
        super("org.pepsoft.hytale.Prefab",
              "Hytale Prefabs",
              "Place Hytale prefab structures (trees, monuments, dungeons, etc.)",
              DataSize.NIBBLE, true, 90, 'f');
    }

    @Override
    public BufferedImage getIcon() {
        if (icon == null) {
            icon = createIcon();
        }
        return icon;
    }

    private static BufferedImage createIcon() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        // Building silhouette (structure icon)
        g.setColor(new Color(0x8B, 0x45, 0x13)); // Brown
        g.fillRect(2, 6, 12, 10);
        // Roof
        g.setColor(new Color(0xA0, 0x52, 0x2D));
        int[] xPoints = {1, 8, 15};
        int[] yPoints = {6, 1, 6};
        g.fillPolygon(xPoints, yPoints, 3);
        // Door
        g.setColor(new Color(0x5C, 0x2E, 0x0A));
        g.fillRect(6, 10, 4, 6);
        // Window
        g.setColor(new Color(0xFF, 0xE0, 0x80));
        g.fillRect(4, 8, 2, 2);
        g.fillRect(10, 8, 2, 2);
        g.dispose();
        return img;
    }

    // ─── Built-in Prefab Registry ───────────────────────────────────────

    /**
     * A single prefab entry representing a built-in Hytale prefab.
     */
    public static class PrefabEntry implements Serializable {
        public final String category;
        public final String name;
        public final String path;

        public PrefabEntry(String category, String name, String path) {
            this.category = category;
            this.name = name;
            this.path = path;
        }

        @Override
        public String toString() {
            return category + "/" + name;
        }

        private static final long serialVersionUID = 1L;
    }

    /** All built-in Hytale prefabs discovered from the assets directory. */
    public static final PrefabEntry[] BUILT_IN_PREFABS = {
        new PrefabEntry("Cave", "Formations", "Prefabs/Cave/Formations"),
        new PrefabEntry("Cave", "Geysers", "Prefabs/Cave/Geysers"),
        new PrefabEntry("Cave", "Hive", "Prefabs/Cave/Hive"),
        new PrefabEntry("Cave", "Klops", "Prefabs/Cave/Klops"),
        new PrefabEntry("Cave", "Nodes", "Prefabs/Cave/Nodes"),
        new PrefabEntry("Cave", "Organics", "Prefabs/Cave/Organics"),
        new PrefabEntry("Cave", "Stalagmites", "Prefabs/Cave/Stalagmites"),
        new PrefabEntry("Dungeon", "Challenge_Gate", "Prefabs/Dungeon/Challenge_Gate"),
        new PrefabEntry("Dungeon", "Cursed_Crypt", "Prefabs/Dungeon/Cursed_Crypt"),
        new PrefabEntry("Dungeon", "Goblin_Lair", "Prefabs/Dungeon/Goblin_Lair"),
        new PrefabEntry("Dungeon", "Labyrinth", "Prefabs/Dungeon/Labyrinth"),
        new PrefabEntry("Dungeon", "Magic_Ruins", "Prefabs/Dungeon/Magic_Ruins"),
        new PrefabEntry("Dungeon", "Outlander_Temple", "Prefabs/Dungeon/Outlander_Temple"),
        new PrefabEntry("Dungeon", "Rift", "Prefabs/Dungeon/Rift"),
        new PrefabEntry("Dungeon", "Sandstone", "Prefabs/Dungeon/Sandstone"),
        new PrefabEntry("Dungeon", "Sewer", "Prefabs/Dungeon/Sewer"),
        new PrefabEntry("Dungeon", "Shale", "Prefabs/Dungeon/Shale"),
        new PrefabEntry("Dungeon", "Slate", "Prefabs/Dungeon/Slate"),
        new PrefabEntry("Dungeon", "Stone", "Prefabs/Dungeon/Stone"),
        new PrefabEntry("Mineshaft", "Dry", "Prefabs/Mineshaft/Dry"),
        new PrefabEntry("Mineshaft", "Fir", "Prefabs/Mineshaft/Fir"),
        new PrefabEntry("Mineshaft", "Shaft", "Prefabs/Mineshaft/Shaft"),
        new PrefabEntry("Mineshaft", "Slope", "Prefabs/Mineshaft/Slope"),
        new PrefabEntry("Mineshaft", "Surface", "Prefabs/Mineshaft/Surface"),
        new PrefabEntry("Mineshaft_Drift", "Stage1_Generic", "Prefabs/Mineshaft_Drift/Stage1_Generic"),
        new PrefabEntry("Mineshaft_Drift", "Stage2_Generic", "Prefabs/Mineshaft_Drift/Stage2_Generic"),
        new PrefabEntry("Mineshaft_Drift", "Stage3_Generic", "Prefabs/Mineshaft_Drift/Stage3_Generic"),
        new PrefabEntry("Monuments", "Challenge", "Prefabs/Monuments/Challenge"),
        new PrefabEntry("Monuments", "Encounter", "Prefabs/Monuments/Encounter"),
        new PrefabEntry("Monuments", "Incidental", "Prefabs/Monuments/Incidental"),
        new PrefabEntry("Monuments", "Story", "Prefabs/Monuments/Story"),
        new PrefabEntry("Monuments", "Unique", "Prefabs/Monuments/Unique"),
        new PrefabEntry("Npc", "Dragons", "Prefabs/Npc/Dragons"),
        new PrefabEntry("Npc", "Feran", "Prefabs/Npc/Feran"),
        new PrefabEntry("Npc", "Hedera", "Prefabs/Npc/Hedera"),
        new PrefabEntry("Npc", "Kweebec", "Prefabs/Npc/Kweebec"),
        new PrefabEntry("Npc", "Outlander", "Prefabs/Npc/Outlander"),
        new PrefabEntry("Npc", "Scarak", "Prefabs/Npc/Scarak"),
        new PrefabEntry("Npc", "Slothian", "Prefabs/Npc/Slothian"),
        new PrefabEntry("Npc", "Trork", "Prefabs/Npc/Trork"),
        new PrefabEntry("Npc", "Yeti", "Prefabs/Npc/Yeti"),
        new PrefabEntry("Plants", "Bush", "Prefabs/Plants/Bush"),
        new PrefabEntry("Plants", "Cacti", "Prefabs/Plants/Cacti"),
        new PrefabEntry("Plants", "Coral", "Prefabs/Plants/Coral"),
        new PrefabEntry("Plants", "Driftwood", "Prefabs/Plants/Driftwood"),
        new PrefabEntry("Plants", "Jungle", "Prefabs/Plants/Jungle"),
        new PrefabEntry("Plants", "Mushroom_Large", "Prefabs/Plants/Mushroom_Large"),
        new PrefabEntry("Plants", "Mushroom_Rings", "Prefabs/Plants/Mushroom_Rings"),
        new PrefabEntry("Plants", "Seaweed", "Prefabs/Plants/Seaweed"),
        new PrefabEntry("Plants", "Twisted_Wood", "Prefabs/Plants/Twisted_Wood"),
        new PrefabEntry("Plants", "Vines", "Prefabs/Plants/Vines"),
        new PrefabEntry("Rock_Formations", "Arches", "Prefabs/Rock_Formations/Arches"),
        new PrefabEntry("Rock_Formations", "Crystal_Floating", "Prefabs/Rock_Formations/Crystal_Floating"),
        new PrefabEntry("Rock_Formations", "Crystal_Pattern", "Prefabs/Rock_Formations/Crystal_Pattern"),
        new PrefabEntry("Rock_Formations", "Crystal_Pits", "Prefabs/Rock_Formations/Crystal_Pits"),
        new PrefabEntry("Rock_Formations", "Crystals", "Prefabs/Rock_Formations/Crystals"),
        new PrefabEntry("Rock_Formations", "Dolmen", "Prefabs/Rock_Formations/Dolmen"),
        new PrefabEntry("Rock_Formations", "Fossils", "Prefabs/Rock_Formations/Fossils"),
        new PrefabEntry("Rock_Formations", "Geode_Floating", "Prefabs/Rock_Formations/Geode_Floating"),
        new PrefabEntry("Rock_Formations", "Hotsprings", "Prefabs/Rock_Formations/Hotsprings"),
        new PrefabEntry("Rock_Formations", "Ice_Formations", "Prefabs/Rock_Formations/Ice_Formations"),
        new PrefabEntry("Rock_Formations", "Mushrooms", "Prefabs/Rock_Formations/Mushrooms"),
        new PrefabEntry("Rock_Formations", "Pillars", "Prefabs/Rock_Formations/Pillars"),
        new PrefabEntry("Rock_Formations", "Rocks", "Prefabs/Rock_Formations/Rocks"),
        new PrefabEntry("Rock_Formations", "Stalactites", "Prefabs/Rock_Formations/Stalactites"),
        new PrefabEntry("Spawn", "Layouts", "Prefabs/Spawn/Layouts"),
        new PrefabEntry("Spawn", "Pathways", "Prefabs/Spawn/Pathways"),
        new PrefabEntry("Spawn", "Room", "Prefabs/Spawn/Room"),
        new PrefabEntry("Spawn", "Room_Goblin", "Prefabs/Spawn/Room_Goblin"),
        new PrefabEntry("Spawn", "Spawners_Rocks_Stone", "Prefabs/Spawn/Spawners_Rocks_Stone"),
        new PrefabEntry("Spawn", "Spawners_Trees_Birch", "Prefabs/Spawn/Spawners_Trees_Birch"),
        new PrefabEntry("Spawn", "Spawners_Trees_Oak", "Prefabs/Spawn/Spawners_Trees_Oak"),
        new PrefabEntry("Standalone", "Example_Portal1", "Prefabs/Standalone/Example_Portal1"),
        new PrefabEntry("Standalone", "Example_Portal_Base", "Prefabs/Standalone/Example_Portal_Base"),
        new PrefabEntry("Standalone", "Goblin_Thief_Chest", "Prefabs/Standalone/Goblin_Thief_Chest"),
        new PrefabEntry("Standalone", "default.minigame", "Prefabs/Standalone/default.minigame"),
        new PrefabEntry("Standalone", "pregame.minigame", "Prefabs/Standalone/pregame.minigame"),
        new PrefabEntry("TestTree", "Stage_0", "Prefabs/TestTree/Stage_0"),
        new PrefabEntry("TestTree", "Stage_1", "Prefabs/TestTree/Stage_1"),
        new PrefabEntry("Testing", "Block_Migrations", "Prefabs/Testing/Block_Migrations"),
        new PrefabEntry("Testing", "Npc", "Prefabs/Testing/Npc"),
        new PrefabEntry("Testing", "Prefab_Editor", "Prefabs/Testing/Prefab_Editor"),
        new PrefabEntry("Testing", "Prototype_Mushroom", "Prefabs/Testing/Prototype_Mushroom"),
        new PrefabEntry("Testing", "Prototype_Poison", "Prefabs/Testing/Prototype_Poison"),
        new PrefabEntry("Testing", "Prototype_Structures", "Prefabs/Testing/Prototype_Structures"),
        new PrefabEntry("Testing", "Prototype_Structures_Zone2", "Prefabs/Testing/Prototype_Structures_Zone2"),
        new PrefabEntry("Testing", "Sewer", "Prefabs/Testing/Sewer"),
        new PrefabEntry("Trees", "Amber", "Prefabs/Trees/Amber"),
        new PrefabEntry("Trees", "Amber_Trunk", "Prefabs/Trees/Amber_Trunk"),
        new PrefabEntry("Trees", "Ash", "Prefabs/Trees/Ash"),
        new PrefabEntry("Trees", "Ash_Dead", "Prefabs/Trees/Ash_Dead"),
        new PrefabEntry("Trees", "Ash_Moss", "Prefabs/Trees/Ash_Moss"),
        new PrefabEntry("Trees", "Ash_swamp", "Prefabs/Trees/Ash_swamp"),
        new PrefabEntry("Trees", "Ash_swamp_dead", "Prefabs/Trees/Ash_swamp_dead"),
        new PrefabEntry("Trees", "Ash_twisted", "Prefabs/Trees/Ash_twisted"),
        new PrefabEntry("Trees", "Ash_twisted_Giant", "Prefabs/Trees/Ash_twisted_Giant"),
        new PrefabEntry("Trees", "Ash_twisted_Large", "Prefabs/Trees/Ash_twisted_Large"),
        new PrefabEntry("Trees", "Aspen", "Prefabs/Trees/Aspen"),
        new PrefabEntry("Trees", "Autumn", "Prefabs/Trees/Autumn"),
        new PrefabEntry("Trees", "Autumn_Stumps", "Prefabs/Trees/Autumn_Stumps"),
        new PrefabEntry("Trees", "Azure", "Prefabs/Trees/Azure"),
        new PrefabEntry("Trees", "Bamboo", "Prefabs/Trees/Bamboo"),
        new PrefabEntry("Trees", "Banyan", "Prefabs/Trees/Banyan"),
        new PrefabEntry("Trees", "Beech", "Prefabs/Trees/Beech"),
        new PrefabEntry("Trees", "Beech_Dry", "Prefabs/Trees/Beech_Dry"),
        new PrefabEntry("Trees", "Beech_Mountain", "Prefabs/Trees/Beech_Mountain"),
        new PrefabEntry("Trees", "Birch", "Prefabs/Trees/Birch"),
        new PrefabEntry("Trees", "Boab", "Prefabs/Trees/Boab"),
        new PrefabEntry("Trees", "Burnt", "Prefabs/Trees/Burnt"),
        new PrefabEntry("Trees", "Burnt_Roots", "Prefabs/Trees/Burnt_Roots"),
        new PrefabEntry("Trees", "Burnt_dead", "Prefabs/Trees/Burnt_dead"),
        new PrefabEntry("Trees", "Cedar", "Prefabs/Trees/Cedar"),
        new PrefabEntry("Trees", "Cedar_Burnt", "Prefabs/Trees/Cedar_Burnt"),
        new PrefabEntry("Trees", "Cedar_Logs", "Prefabs/Trees/Cedar_Logs"),
        new PrefabEntry("Trees", "Crystal", "Prefabs/Trees/Crystal"),
        new PrefabEntry("Trees", "Dry", "Prefabs/Trees/Dry"),
        new PrefabEntry("Trees", "Dry_Dead", "Prefabs/Trees/Dry_Dead"),
        new PrefabEntry("Trees", "Fig_Blue", "Prefabs/Trees/Fig_Blue"),
        new PrefabEntry("Trees", "Fir", "Prefabs/Trees/Fir"),
        new PrefabEntry("Trees", "Fir_Autumn", "Prefabs/Trees/Fir_Autumn"),
        new PrefabEntry("Trees", "Fir_Dead", "Prefabs/Trees/Fir_Dead"),
        new PrefabEntry("Trees", "Fir_Dead_Large", "Prefabs/Trees/Fir_Dead_Large"),
        new PrefabEntry("Trees", "Fir_Logs", "Prefabs/Trees/Fir_Logs"),
        new PrefabEntry("Trees", "Fir_Snow", "Prefabs/Trees/Fir_Snow"),
        new PrefabEntry("Trees", "Fir_red", "Prefabs/Trees/Fir_red"),
        new PrefabEntry("Trees", "Fire_Burning", "Prefabs/Trees/Fire_Burning"),
        new PrefabEntry("Trees", "Fruit", "Prefabs/Trees/Fruit"),
        new PrefabEntry("Trees", "Gum", "Prefabs/Trees/Gum"),
        new PrefabEntry("Trees", "Jungle", "Prefabs/Trees/Jungle"),
        new PrefabEntry("Trees", "Jungle1", "Prefabs/Trees/Jungle1"),
        new PrefabEntry("Trees", "Jungle2", "Prefabs/Trees/Jungle2"),
        new PrefabEntry("Trees", "Jungle3", "Prefabs/Trees/Jungle3"),
        new PrefabEntry("Trees", "Jungle_Crystal", "Prefabs/Trees/Jungle_Crystal"),
        new PrefabEntry("Trees", "Jungle_Crystal_Red", "Prefabs/Trees/Jungle_Crystal_Red"),
        new PrefabEntry("Trees", "Jungle_Ferns", "Prefabs/Trees/Jungle_Ferns"),
        new PrefabEntry("Trees", "Jungle_Island1", "Prefabs/Trees/Jungle_Island1"),
        new PrefabEntry("Trees", "Jungle_Island2", "Prefabs/Trees/Jungle_Island2"),
        new PrefabEntry("Trees", "Jungle_Mushroom", "Prefabs/Trees/Jungle_Mushroom"),
        new PrefabEntry("Trees", "Logs", "Prefabs/Trees/Logs"),
        new PrefabEntry("Trees", "Maple", "Prefabs/Trees/Maple"),
        new PrefabEntry("Trees", "Maple_Stumps", "Prefabs/Trees/Maple_Stumps"),
        new PrefabEntry("Trees", "Mushroom", "Prefabs/Trees/Mushroom"),
        new PrefabEntry("Trees", "Oak", "Prefabs/Trees/Oak"),
        new PrefabEntry("Trees", "Oak_Moss", "Prefabs/Trees/Oak_Moss"),
        new PrefabEntry("Trees", "Oak_Stumps", "Prefabs/Trees/Oak_Stumps"),
        new PrefabEntry("Trees", "Palm", "Prefabs/Trees/Palm"),
        new PrefabEntry("Trees", "Palm_Green", "Prefabs/Trees/Palm_Green"),
        new PrefabEntry("Trees", "Palo", "Prefabs/Trees/Palo"),
        new PrefabEntry("Trees", "Petrified", "Prefabs/Trees/Petrified"),
        new PrefabEntry("Trees", "Petrified_Coral_Bulbs", "Prefabs/Trees/Petrified_Coral_Bulbs"),
        new PrefabEntry("Trees", "Petrified_Dead", "Prefabs/Trees/Petrified_Dead"),
        new PrefabEntry("Trees", "Petrified_Logs", "Prefabs/Trees/Petrified_Logs"),
        new PrefabEntry("Trees", "Poisoned", "Prefabs/Trees/Poisoned"),
        new PrefabEntry("Trees", "Redwood", "Prefabs/Trees/Redwood"),
        new PrefabEntry("Trees", "Redwood_Logs", "Prefabs/Trees/Redwood_Logs"),
        new PrefabEntry("Trees", "Redwood_Stumps", "Prefabs/Trees/Redwood_Stumps"),
        new PrefabEntry("Trees", "Rocks", "Prefabs/Trees/Rocks"),
        new PrefabEntry("Trees", "Sallow", "Prefabs/Trees/Sallow"),
        new PrefabEntry("Trees", "Silver", "Prefabs/Trees/Silver"),
        new PrefabEntry("Trees", "Stormbark_Stumps", "Prefabs/Trees/Stormbark_Stumps"),
        new PrefabEntry("Trees", "Willow", "Prefabs/Trees/Willow"),
        new PrefabEntry("Trees", "Wisteria", "Prefabs/Trees/Wisteria"),
        new PrefabEntry("Trees", "Zone5", "Prefabs/Trees/Zone5")
    };

    /** Prefab category names. */
    public static final String[] CATEGORY_NAMES = {
        "Cave", "Dungeon", "Mineshaft", "Mineshaft_Drift", "Monuments", "Npc", "Plants", "Rock_Formations", "Spawn", "Standalone", "TestTree", "Testing", "Trees"
    };

    /**
     * Get all prefabs in a given category.
     */
    public static List<PrefabEntry> getPrefabsInCategory(String category) {
        List<PrefabEntry> result = new ArrayList<>();
        for (PrefabEntry entry : BUILT_IN_PREFABS) {
            if (entry.category.equals(category)) {
                result.add(entry);
            }
        }
        return result;
    }

    /**
     * Get all unique category names.
     */
    public static String[] getCategories() {
        return CATEGORY_NAMES;
    }

    // ─── Old Prefab Category Constants (kept for layer value compatibility) ──

    public static final int PREFAB_NONE                = 0;
    public static final int PREFAB_TREES               = 1;
    public static final int PREFAB_ROCKS               = 2;
    public static final int PREFAB_PLANTS              = 3;
    public static final int PREFAB_CAVE                = 4;
    public static final int PREFAB_MONUMENT_INCIDENTAL = 5;
    public static final int PREFAB_MONUMENT_ENCOUNTER  = 6;
    public static final int PREFAB_DUNGEON             = 7;
    public static final int PREFAB_NPC_SETTLEMENT      = 8;
    public static final int PREFAB_MINESHAFT           = 9;
    public static final int PREFAB_MONUMENT_STORY      = 10;
    public static final int PREFAB_MONUMENT_UNIQUE     = 11;

    public static final int PREFAB_COUNT = 12;

    /** Display names for prefab categories. */
    public static final String[] PREFAB_NAMES = {
        "(None)",
        "Trees",
        "Rock Formations",
        "Plants",
        "Cave Formations",
        "Incidental Monument",
        "Encounter Monument",
        "Dungeon",
        "NPC Settlement",
        "Mineshaft",
        "Story Monument",
        "Unique Monument"
    };

    /** Prefab directory paths indexed by layer value. */
    public static final String[] PREFAB_PATHS = {
        null,                              // None
        "Prefabs/Trees/",                  // Trees
        "Prefabs/Rock_Formations/",        // Rocks
        "Prefabs/Plants/",                 // Plants
        "Prefabs/Cave/",                   // Cave formations
        "Prefabs/Monuments/Incidental/",   // Incidental monuments
        "Prefabs/Monuments/Encounter/",    // Encounter monuments
        "Prefabs/Dungeon/",               // Dungeons
        "Prefabs/Npc/",                    // NPC settlements
        "Prefabs/Mineshaft/",             // Mineshafts
        "Prefabs/Monuments/Story/",        // Story monuments
        "Prefabs/Monuments/Unique/"         // Unique monuments
    };

    /** Display colors for the renderer (ARGB, semi-transparent). */
    public static final int[] PREFAB_COLORS = {
        0x00000000,  // None - transparent
        0x80228B22,  // Trees - forest green
        0x80808080,  // Rocks - gray
        0x8032CD32,  // Plants - lime green
        0x80696969,  // Cave - dim gray
        0x80DAA520,  // Incidental - goldenrod
        0x80FF4500,  // Encounter - orange-red
        0x80800000,  // Dungeon - maroon
        0x80FFD700,  // NPC Settlement - gold
        0x808B4513,  // Mineshaft - saddle brown
        0x809932CC,  // Story - dark orchid
        0x80FF1493   // Unique - deep pink
    };

    public static final HytalePrefabLayer INSTANCE = new HytalePrefabLayer();

    private transient BufferedImage icon;
    private static final long serialVersionUID = 1L;
}
