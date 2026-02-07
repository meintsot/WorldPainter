package org.pepsoft.worldpainter.hytale;

import org.pepsoft.worldpainter.layers.Layer;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Layer for placing Hytale prefab markers on the world. When painted, areas
 * marked with this layer will have prefab references written into chunk metadata
 * during export. The layer value (NIBBLE, 0-15) selects a prefab category:
 *
 * <table>
 * <tr><th>Value</th><th>Category</th><th>Description</th></tr>
 * <tr><td>0</td><td>(none)</td><td>No prefab</td></tr>
 * <tr><td>1</td><td>Trees</td><td>Random tree from zone-appropriate species</td></tr>
 * <tr><td>2</td><td>Rocks</td><td>Rock formation prefabs</td></tr>
 * <tr><td>3</td><td>Plants</td><td>Plant/flower clusters</td></tr>
 * <tr><td>4</td><td>Cave</td><td>Cave formation prefabs</td></tr>
 * <tr><td>5</td><td>Monument (Incidental)</td><td>Small incidental structures</td></tr>
 * <tr><td>6</td><td>Monument (Encounter)</td><td>Encounter/combat structures per zone</td></tr>
 * <tr><td>7</td><td>Dungeon</td><td>Dungeon entrance/structure</td></tr>
 * <tr><td>8</td><td>NPC Settlement</td><td>NPC village/camp</td></tr>
 * <tr><td>9</td><td>Mineshaft</td><td>Mineshaft entrance</td></tr>
 * <tr><td>10</td><td>Monument (Story)</td><td>Story-related structures</td></tr>
 * <tr><td>11</td><td>Monument (Unique)</td><td>Unique one-off structures</td></tr>
 * <tr><td>12-15</td><td>(reserved)</td><td>Future use</td></tr>
 * </table>
 *
 * <p>During export, the chunk metadata will include a {@code prefab_markers}
 * array listing the category and position, which a Hytale server plugin can
 * use to place actual prefabs.</p>
 */
public class HytalePrefabLayer extends Layer {

    private HytalePrefabLayer() {
        super("org.pepsoft.hytale.Prefab",
              "Hytale Prefabs",
              "Mark areas for Hytale prefab placement (structures, trees, dungeons)",
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

    // ─── Prefab Categories ──────────────────────────────────────────────

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

    /** 
     * Hytale prefab root paths for each category.
     * These correspond to directories under Server/Prefabs/.
     */
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
