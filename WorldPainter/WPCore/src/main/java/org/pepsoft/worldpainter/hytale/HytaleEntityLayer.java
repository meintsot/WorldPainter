package org.pepsoft.worldpainter.hytale;

import org.pepsoft.worldpainter.layers.Layer;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Layer for controlling NPC/entity spawn density and type in Hytale worlds.
 * The layer value (NIBBLE, 0-15) sets the spawn density category:
 *
 * <table>
 * <tr><th>Value</th><th>Density</th><th>Description</th></tr>
 * <tr><td>0</td><td>Default</td><td>Use environment's SpawnDensity</td></tr>
 * <tr><td>1</td><td>None</td><td>No spawning (safe zones)</td></tr>
 * <tr><td>2</td><td>Minimal</td><td>0.1 density — very rare spawns</td></tr>
 * <tr><td>3</td><td>Sparse</td><td>0.2 density — occasional spawns</td></tr>
 * <tr><td>4</td><td>Low</td><td>0.3 density — light spawning</td></tr>
 * <tr><td>5</td><td>Normal</td><td>0.5 density — standard spawning</td></tr>
 * <tr><td>6</td><td>Dense</td><td>0.7 density — frequent spawns</td></tr>
 * <tr><td>7</td><td>Heavy</td><td>0.9 density — very frequent spawns</td></tr>
 * <tr><td>8</td><td>Maximum</td><td>1.0 density — maximum spawning</td></tr>
 * <tr><td>9</td><td>Passive Only</td><td>0.5 density, only passive mobs</td></tr>
 * <tr><td>10</td><td>Hostile Only</td><td>0.5 density, only hostile mobs</td></tr>
 * <tr><td>11</td><td>Aquatic</td><td>0.5 density, only aquatic mobs</td></tr>
 * <tr><td>12-15</td><td>(reserved)</td><td>Future use</td></tr>
 * </table>
 */
public class HytaleEntityLayer extends Layer {

    private HytaleEntityLayer() {
        super("org.pepsoft.hytale.Entity",
              "Hytale Entities",
              "Control NPC/entity spawn density for Hytale worlds",
              DataSize.NIBBLE, true, 80, 'n');
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
        // Body (green creature silhouette)
        g.setColor(new Color(0x40, 0x80, 0x40));
        g.fillRect(5, 4, 6, 8);
        // Head
        g.fillRect(6, 1, 4, 4);
        // Eyes
        g.setColor(new Color(0xff, 0xff, 0xff));
        g.fillRect(7, 2, 1, 1);
        g.fillRect(9, 2, 1, 1);
        // Legs
        g.setColor(new Color(0x40, 0x80, 0x40));
        g.fillRect(5, 12, 2, 3);
        g.fillRect(9, 12, 2, 3);
        g.dispose();
        return img;
    }

    // ─── Spawn Density Presets ──────────────────────────────────────────

    public static final int DENSITY_DEFAULT      = 0;
    public static final int DENSITY_NONE         = 1;
    public static final int DENSITY_MINIMAL      = 2;
    public static final int DENSITY_SPARSE       = 3;
    public static final int DENSITY_LOW          = 4;
    public static final int DENSITY_NORMAL       = 5;
    public static final int DENSITY_DENSE        = 6;
    public static final int DENSITY_HEAVY        = 7;
    public static final int DENSITY_MAXIMUM      = 8;
    public static final int DENSITY_PASSIVE_ONLY = 9;
    public static final int DENSITY_HOSTILE_ONLY = 10;
    public static final int DENSITY_AQUATIC      = 11;

    public static final int DENSITY_COUNT = 12;

    /** Display names for spawn density presets. */
    public static final String[] DENSITY_NAMES = {
        "(Default)",
        "No Spawning",
        "Minimal (0.1)",
        "Sparse (0.2)",
        "Low (0.3)",
        "Normal (0.5)",
        "Dense (0.7)",
        "Heavy (0.9)",
        "Maximum (1.0)",
        "Passive Only",
        "Hostile Only",
        "Aquatic Only"
    };

    /** Actual spawn density values (0.0 - 1.0). */
    public static final float[] DENSITY_VALUES = {
        -1.0f, // Default (use environment)
        0.0f,  // None
        0.1f,  // Minimal
        0.2f,  // Sparse
        0.3f,  // Low
        0.5f,  // Normal
        0.7f,  // Dense
        0.9f,  // Heavy
        1.0f,  // Maximum
        0.5f,  // Passive Only
        0.5f,  // Hostile Only
        0.5f   // Aquatic
    };

    /** Spawn filter tags for specialized presets. */
    public static final String[] SPAWN_TAGS = {
        null,           // Default
        null,           // None 
        null,           // Minimal
        null,           // Sparse
        null,           // Low
        null,           // Normal
        null,           // Dense
        null,           // Heavy
        null,           // Maximum
        "Passive",      // Passive Only
        "Hostile",      // Hostile Only
        "Aquatic"       // Aquatic Only
    };

    /** Display colors for the renderer (ARGB, semi-transparent). */
    public static final int[] DENSITY_COLORS = {
        0x00000000,  // Default - transparent
        0x80ff0000,  // None - red (no spawn zone)
        0x40ffaa00,  // Minimal - faint orange
        0x60ffaa00,  // Sparse - light orange
        0x60ffcc00,  // Low - light yellow
        0x6000cc00,  // Normal - green
        0x8000aa00,  // Dense - darker green
        0x80008800,  // Heavy - deep green
        0x80006600,  // Maximum - dark green
        0x8080c0ff,  // Passive - light blue
        0x80ff4040,  // Hostile - red
        0x804080ff   // Aquatic - blue
    };

    /**
     * Returns the spawn density value for a given preset index.
     * Returns -1.0f for "default" (meaning use the environment's SpawnDensity).
     */
    public static float getSpawnDensity(int densityValue) {
        if (densityValue < 0 || densityValue >= DENSITY_VALUES.length) return -1.0f;
        return DENSITY_VALUES[densityValue];
    }

    public static final HytaleEntityLayer INSTANCE = new HytaleEntityLayer();

    private transient BufferedImage icon;
    private static final long serialVersionUID = 1L;
}
