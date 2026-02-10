package org.pepsoft.worldpainter.hytale;

import org.pepsoft.worldpainter.ColourScheme;
import org.pepsoft.worldpainter.Terrain;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Table cell renderer that always uses Hytale terrain textures for icons.
 */
public class HytaleTerrainTableCellRenderer extends DefaultTableCellRenderer {
    private static final int HYTALE_ICON_SIZE = 28;

    public HytaleTerrainTableCellRenderer(ColourScheme colourScheme) {
        this.colourScheme = colourScheme;
        setIconTextGap(6);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        if (value == null) {
            setText("");
            setIcon(null);
        } else if (value instanceof HytaleTerrain) {
            HytaleTerrain terrain = (HytaleTerrain) value;
            setText(terrain.getName());
            setIcon(getOrCreateIcon(terrain));
        } else if (value instanceof Terrain) {
            Terrain minecraftTerrain = (Terrain) value;
            HytaleTerrain terrain = HytaleTerrainHelper.fromMinecraftTerrain(minecraftTerrain);
            setText(minecraftTerrain.isCustom() ? minecraftTerrain.getName() : terrain.getName());
            setIcon(getOrCreateIcon(terrain));
        } else {
            setText(value.toString());
            setIcon(null);
        }

        return this;
    }

    private Icon getOrCreateIcon(HytaleTerrain terrain) {
        Icon icon = iconCache.get(terrain);
        if (icon == null) {
            icon = new ImageIcon(terrain.getScaledIcon(HYTALE_ICON_SIZE, colourScheme));
            iconCache.put(terrain, icon);
        }
        return icon;
    }

    private final ColourScheme colourScheme;
    private final Map<HytaleTerrain, Icon> iconCache = new HashMap<>();

    private static final long serialVersionUID = 1L;
}
