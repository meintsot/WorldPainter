package org.pepsoft.worldpainter.hytale;

import org.pepsoft.worldpainter.layers.Layer;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Layer for controlling fluid type in Hytale worlds. The layer value (NIBBLE, 0-15)
 * selects a fluid type. Fluids are placed using the Flood tool (click-to-fill),
 * not brush-painted.
 *
 * <table>
 * <tr><th>Value</th><th>Fluid Type</th><th>Description</th></tr>
 * <tr><td>0</td><td>(none)</td><td>Default water (no override)</td></tr>
 * <tr><td>1</td><td>Lava</td><td>Lava source</td></tr>
 * <tr><td>2</td><td>Poison</td><td>Sickly green #50c878</td></tr>
 * <tr><td>3</td><td>Slime</td><td>Bright green #7cfc00</td></tr>
 * <tr><td>4</td><td>Tar</td><td>Dark brown #2f1a0e</td></tr>
 * <tr><td>5-15</td><td>(reserved)</td><td>Future use</td></tr>
 * </table>
 *
 * <p>Water tinting is handled by the environment layer, not by this layer.</p>
 */
public class HytaleFluidLayer extends Layer {

    private HytaleFluidLayer() {
        super("org.pepsoft.hytale.Fluid",
              "Hytale Fluid",
              "Fluid type for Hytale worlds (set via Flood tools)",
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

    // ─── Fluid Types ───────────────────────────────────────────────────

    public static final int FLUID_NONE   = 0;
    public static final int FLUID_LAVA   = 1;
    public static final int FLUID_POISON = 2;
    public static final int FLUID_SLIME  = 3;
    public static final int FLUID_TAR    = 4;

    public static final int FLUID_COUNT = 5;

    /** Display names for fluid types, indexed by layer value. */
    public static final String[] FLUID_NAMES = {
        "Water",
        "Lava",
        "Poison",
        "Slime",
        "Tar"
    };

    /** Display colors for fluid rendering (ARGB). */
    public static final int[] FLUID_COLORS = {
        0x00000000,  // None/Water - transparent (uses default waterColour)
        0x80ff4400,  // Lava
        0x8050c878,  // Poison
        0x807cfc00,  // Slime
        0x802f1a0e   // Tar
    };

    /** Opaque RGB colors for fluid rendering in TileRenderer. */
    public static final int[] FLUID_RENDER_COLORS = {
        0x000000,    // None/Water - not used (uses default waterColour)
        0xff4400,    // Lava - not used (uses default lavaColour)
        0x50c878,    // Poison
        0x7cfc00,    // Slime
        0x2f1a0e     // Tar
    };

    /**
     * Returns whether the given fluid type represents lava.
     */
    public static boolean isLava(int fluidValue) {
        return fluidValue == FLUID_LAVA;
    }

    /**
     * Returns whether the given fluid type is a non-water, non-lava special fluid
     * (poison, slime, or tar).
     */
    public static boolean isSpecialFluid(int fluidValue) {
        return fluidValue == FLUID_POISON || fluidValue == FLUID_SLIME || fluidValue == FLUID_TAR;
    }

    /**
     * Returns the Hytale block ID for the given fluid type.
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
     * Maps legacy fluid layer values (from old saved worlds) to the new compact values.
     * Old values 1-8 and 10 (water variants) map to FLUID_NONE (water),
     * old value 9 maps to FLUID_LAVA, etc.
     */
    public static int migrateLegacyValue(int oldValue) {
        switch (oldValue) {
            case 9:  return FLUID_LAVA;
            case 11: return FLUID_POISON;
            case 12: return FLUID_SLIME;
            case 13: return FLUID_TAR;
            default: return FLUID_NONE;
        }
    }

    /**
     * Normalize a fluid layer value, transparently migrating legacy values
     * (≥ {@link #FLUID_COUNT}) while leaving current compact values (0-4)
     * untouched.  Safe to call on both old and new values.
     */
    public static int normalizeFluidValue(int value) {
        return (value >= FLUID_COUNT) ? migrateLegacyValue(value) : value;
    }

    public static final HytaleFluidLayer INSTANCE = new HytaleFluidLayer();

    private transient BufferedImage icon;
    private static final long serialVersionUID = 1L;
}
