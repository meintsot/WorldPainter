package org.pepsoft.worldpainter.hytale;

import org.pepsoft.worldpainter.layers.Layer;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Layer for overriding the Hytale environment per-column. The layer value
 * (BYTE, 0-255) selects an environment preset from {@link HytaleEnvironmentData}.
 * Value 0 (or 255) means "auto" (use the biome's default environment).
 *
 * <p>Hytale environments control weather forecasts, water tint, spawn density,
 * sky rendering, and fog. Painting this layer lets users override the default
 * environment derived from the biome.</p>
 *
 * <p>This pairs naturally with the weather system: each environment defines
 * weighted weather forecasts per hour of day.</p>
 */
public class HytaleEnvironmentLayer extends Layer {

    private HytaleEnvironmentLayer() {
        super("org.pepsoft.hytale.Environment",
              "Hytale Environment",
              "Override the Hytale environment (weather, fog, water tint) for an area",
              DataSize.BYTE, true, 75, 'e');
    }

    @Override
    public int getDefaultValue() {
        return ENV_AUTO;
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
        // Sky gradient
        g.setColor(new Color(0x60, 0xb0, 0xff));
        g.fillRect(0, 0, 16, 6);
        // Cloud
        g.setColor(new Color(0xff, 0xff, 0xff));
        g.fillRect(3, 2, 5, 2);
        g.fillRect(9, 3, 4, 2);
        // Sun
        g.setColor(new Color(0xff, 0xd7, 0x00));
        g.fillOval(11, 0, 4, 4);
        // Ground
        g.setColor(new Color(0x4a, 0x8c, 0x2a));
        g.fillRect(0, 6, 16, 4);
        // Rain drops
        g.setColor(new Color(0x30, 0x60, 0xc0));
        g.fillRect(0, 10, 16, 6);
        g.setColor(new Color(0x80, 0xb0, 0xff));
        g.drawLine(2, 11, 2, 13);
        g.drawLine(7, 12, 7, 14);
        g.drawLine(12, 11, 12, 13);
        g.dispose();
        return img;
    }

    /** Auto environment — use biome's default. */
    public static final int ENV_AUTO = 255;

    public static final HytaleEnvironmentLayer INSTANCE = new HytaleEnvironmentLayer();

    private transient BufferedImage icon;
    private static final long serialVersionUID = 1L;
}
