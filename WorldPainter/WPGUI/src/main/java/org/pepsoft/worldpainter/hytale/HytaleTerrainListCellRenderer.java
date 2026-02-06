package org.pepsoft.worldpainter.hytale;

import org.pepsoft.worldpainter.ColourScheme;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.themes.TerrainListCellRenderer;

import javax.swing.*;
import java.awt.*;

/**
 * A combo box cell renderer for {@link HytaleTerrain} items. Can also handle
 * standard {@link Terrain} items (delegates to {@link TerrainListCellRenderer})
 * making it suitable for platform-aware combo boxes that may contain either type.
 *
 * <p>Each item is rendered with a small coloured square showing the terrain colour,
 * followed by the terrain name and optional biome label.
 */
public class HytaleTerrainListCellRenderer extends DefaultListCellRenderer {

    private final ColourScheme colourScheme;
    private final String nullLabel;

    /**
     * Create a renderer with no null label.
     *
     * @param colourScheme Colour scheme for Minecraft terrain icons
     */
    public HytaleTerrainListCellRenderer(ColourScheme colourScheme) {
        this(colourScheme, null);
    }

    /**
     * Create a renderer with a custom null label.
     *
     * @param colourScheme Colour scheme for Minecraft terrain icons
     * @param nullLabel Label to display for null items (e.g. "-all-", "none")
     */
    public HytaleTerrainListCellRenderer(ColourScheme colourScheme, String nullLabel) {
        this.colourScheme = colourScheme;
        this.nullLabel = nullLabel;
    }

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

        if (value == null) {
            setText(nullLabel != null ? nullLabel : "");
            setIcon(null);
        } else if (value instanceof HytaleTerrain) {
            HytaleTerrain terrain = (HytaleTerrain) value;
            String label = terrain.getName();
            if (terrain.getBiome() != null) {
                label = label + " (" + terrain.getBiome() + ")";
            }
            setText(label);
            setIcon(createColourIcon(terrain.getEffectiveColour()));
        } else if (value instanceof Terrain) {
            // Map Minecraft Terrain to Hytale equivalent for display
            Terrain mcTerrain = (Terrain) value;
            HytaleTerrain ht = HytaleTerrainHelper.fromMinecraftTerrain(mcTerrain);
            String label = ht.getName();
            if (ht.getBiome() != null) {
                label = label + " (" + ht.getBiome() + ")";
            }
            setText(label);
            setIcon(createColourIcon(ht.getEffectiveColour()));
        } else {
            setText(value.toString());
            setIcon(null);
        }

        return this;
    }

    /**
     * Create a small 16x16 coloured icon.
     */
    private static Icon createColourIcon(int rgb) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                g.setColor(new Color(rgb));
                g.fillRect(x, y, 16, 16);
                g.setColor(Color.DARK_GRAY);
                g.drawRect(x, y, 15, 15);
            }

            @Override
            public int getIconWidth() {
                return 16;
            }

            @Override
            public int getIconHeight() {
                return 16;
            }
        };
    }
}
