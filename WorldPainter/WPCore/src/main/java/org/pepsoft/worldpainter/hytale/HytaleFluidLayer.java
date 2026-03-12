package org.pepsoft.worldpainter.hytale;

import org.pepsoft.worldpainter.layers.Layer;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Layer for controlling fluid type in Hytale worlds. When painted, water in those
 * areas will use the specified tint color. The layer value (NIBBLE, 0-15) selects
 * a fluid preset:
 *
 * <table>
 * <tr><th>Value</th><th>Fluid Type</th><th>Description</th></tr>
 * <tr><td>0</td><td>(none)</td><td>Default / no override</td></tr>
 * <tr><td>1</td><td>Water (Zone 1)</td><td>Blue #1983d9</td></tr>
 * <tr><td>2</td><td>Water (Zone 2)</td><td>Bright blue #198dea</td></tr>
 * <tr><td>3</td><td>Water (Zone 3)</td><td>Deep blue #2076b5</td></tr>
 * <tr><td>4</td><td>Water (Zone 4)</td><td>Murky #66682b</td></tr>
 * <tr><td>5</td><td>Swamp Water</td><td>Greenish-brown #66682b</td></tr>
 * <tr><td>6</td><td>Oasis Water</td><td>Turquoise #30b8c0</td></tr>
 * <tr><td>7</td><td>Glacial Water</td><td>Icy blue #a0d8ef</td></tr>
 * <tr><td>8</td><td>Volcanic Water</td><td>Orange-red #c04020</td></tr>
 * <tr><td>9</td><td>Lava</td><td>Lava source</td></tr>
 * <tr><td>10</td><td>Azure Water</td><td>Bright azure #20a0ff</td></tr>
 * <tr><td>11</td><td>Poison</td><td>Sickly green #50c878</td></tr>
 * <tr><td>12</td><td>Slime</td><td>Bright green #7cfc00</td></tr>
 * <tr><td>13</td><td>Tar</td><td>Dark brown #2f1a0e</td></tr>
 * <tr><td>14-15</td><td>(reserved)</td><td>Future use</td></tr>
 * </table>
 */
public class HytaleFluidLayer extends Layer {

    private HytaleFluidLayer() {
        super("org.pepsoft.hytale.Fluid",
              "Hytale Fluid",
              "Set fluid type and water tint for Hytale worlds",
              DataSize.NIBBLE, true, 5, 'w');
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
        // Water blue gradient
        g.setColor(new Color(0x19, 0x83, 0xd9));
        g.fillRect(0, 0, 16, 10);
        // Lava red at bottom
        g.setColor(new Color(0xc0, 0x40, 0x20));
        g.fillRect(0, 10, 16, 6);
        // Wave highlight
        g.setColor(new Color(0x40, 0xa0, 0xff));
        g.drawLine(2, 3, 6, 3);
        g.drawLine(9, 5, 13, 5);
        g.dispose();
        return img;
    }

    // ─── Fluid Presets ─────────────────────────────────────────────────

    public static final int FLUID_NONE        = 0;
    public static final int FLUID_ZONE1_WATER = 1;
    public static final int FLUID_ZONE2_WATER = 2;
    public static final int FLUID_ZONE3_WATER = 3;
    public static final int FLUID_ZONE4_WATER = 4;
    public static final int FLUID_SWAMP       = 5;
    public static final int FLUID_OASIS       = 6;
    public static final int FLUID_GLACIAL     = 7;
    public static final int FLUID_VOLCANIC    = 8;
    public static final int FLUID_LAVA        = 9;
    public static final int FLUID_AZURE       = 10;
    public static final int FLUID_POISON      = 11;
    public static final int FLUID_SLIME       = 12;
    public static final int FLUID_TAR         = 13;

    public static final int FLUID_COUNT = 14;

    /** Display names for fluid presets, indexed by layer value. */
    public static final String[] FLUID_NAMES = {
        "(Default)",
        "Zone 1 Water",
        "Zone 2 Water",
        "Zone 3 Water",
        "Zone 4 Water",
        "Swamp Water",
        "Oasis Water",
        "Glacial Water",
        "Volcanic Water",
        "Lava",
        "Azure Water",
        "Poison",
        "Slime",
        "Tar"
    };

    /** Water tint hex colors for each preset (used in Hytale environment WaterTint). */
    public static final String[] FLUID_TINTS = {
        null,        // Default - use environment default
        "#1983d9",   // Zone 1
        "#198dea",   // Zone 2
        "#2076b5",   // Zone 3
        "#66682b",   // Zone 4
        "#66682b",   // Swamp
        "#30b8c0",   // Oasis
        "#a0d8ef",   // Glacial
        "#c04020",   // Volcanic
        null,        // Lava (not water)
        "#20a0ff",   // Azure
        "#50c878",   // Poison - sickly green
        "#7cfc00",   // Slime - bright green
        "#2f1a0e"    // Tar - dark brown
    };

    /** Display colors for the annotation-style renderer (ARGB). */
    public static final int[] FLUID_COLORS = {
        0x00000000,  // None - transparent
        0x801983d9,  // Zone 1
        0x80198dea,  // Zone 2
        0x802076b5,  // Zone 3
        0x8066682b,  // Zone 4
        0x8066682b,  // Swamp
        0x8030b8c0,  // Oasis
        0x80a0d8ef,  // Glacial
        0x80c04020,  // Volcanic
        0x80ff4400,  // Lava
        0x8020a0ff,  // Azure
        0x8050c878,  // Poison
        0x807cfc00,  // Slime
        0x802f1a0e   // Tar
    };

    /**
     * Returns whether the given fluid preset represents lava rather than water.
     */
    public static boolean isLava(int fluidValue) {
        return fluidValue == FLUID_LAVA;
    }

    /**
     * Returns whether the given fluid preset is a non-water, non-lava special fluid
     * (poison, slime, or tar).
     */
    public static boolean isSpecialFluid(int fluidValue) {
        return fluidValue == FLUID_POISON || fluidValue == FLUID_SLIME || fluidValue == FLUID_TAR;
    }

    /**
     * Returns the Hytale block ID for the given fluid preset.
     */
    public static String getFluidBlockId(int fluidValue) {
        switch (fluidValue) {
            case FLUID_LAVA:    return "Lava_Source";
            case FLUID_POISON:  return "Poison_Source";
            case FLUID_SLIME:   return "Slime_Source";
            case FLUID_TAR:     return "Tar_Source";
            default:            return "Water_Source";
        }
    }

    /**
     * Returns the water tint for the given fluid value, or null for default/lava.
     */
    public static String getWaterTint(int fluidValue) {
        if (fluidValue < 0 || fluidValue >= FLUID_TINTS.length) return null;
        return FLUID_TINTS[fluidValue];
    }

    public static final HytaleFluidLayer INSTANCE = new HytaleFluidLayer();

    private transient BufferedImage icon;
    private static final long serialVersionUID = 1L;
}
