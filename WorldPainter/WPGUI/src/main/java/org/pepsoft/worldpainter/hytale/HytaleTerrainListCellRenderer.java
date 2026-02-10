package org.pepsoft.worldpainter.hytale;

import org.pepsoft.worldpainter.ColourScheme;
import org.pepsoft.worldpainter.Terrain;
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
    private static final int HYTALE_ICON_SIZE = 28;

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
        setIconTextGap(6);
    }

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

        if (value == null) {
            setText(nullLabel != null ? nullLabel : "");
            setIcon(null);
        } else if (value instanceof HytaleTerrain) {
            HytaleTerrain terrain = (HytaleTerrain) value;
            setText(terrain.getName());
            setIcon(new ImageIcon(terrain.getScaledIcon(HYTALE_ICON_SIZE, colourScheme)));
        } else if (value instanceof Terrain) {
            // Map Minecraft Terrain to Hytale equivalent for display
            Terrain mcTerrain = (Terrain) value;
            HytaleTerrain ht = HytaleTerrainHelper.fromMinecraftTerrain(mcTerrain);
            setText(mcTerrain.isCustom() ? mcTerrain.getName() : ht.getName());
            setIcon(new ImageIcon(ht.getScaledIcon(HYTALE_ICON_SIZE, colourScheme)));
        } else {
            setText(value.toString());
            setIcon(null);
        }

        return this;
    }
}
