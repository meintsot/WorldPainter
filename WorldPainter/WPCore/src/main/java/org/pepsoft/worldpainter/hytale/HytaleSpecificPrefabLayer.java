package org.pepsoft.worldpainter.hytale;

import org.pepsoft.worldpainter.layers.CustomLayer;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A custom BIT layer that places specific Hytale prefab files.
 * Each instance stores a list of prefab entries and a user-chosen color.
 * When exported, each painted tile picks one entry from the list deterministically.
 */
public class HytaleSpecificPrefabLayer extends CustomLayer {
    private final List<PrefabFileEntry> prefabEntries;
    private final int colorRGB;

    public HytaleSpecificPrefabLayer(String name, List<PrefabFileEntry> entries, Color color) {
        super(name,
              "Specific prefabs: " + entries.size() + " variant(s)",
              DataSize.BIT, 91, color);
        this.prefabEntries = new ArrayList<>(entries);
        this.colorRGB = color.getRGB();
    }

    public List<PrefabFileEntry> getPrefabEntries() {
        return Collections.unmodifiableList(prefabEntries);
    }

    public Color getColor() {
        return new Color(colorRGB, true);
    }

    /**
     * Deterministically select a prefab entry for the given world coordinates.
     * Uses a simple hash to pick uniformly from the list.
     */
    public PrefabFileEntry selectPrefab(int worldX, int worldZ) {
        if (prefabEntries.size() == 1) {
            return prefabEntries.get(0);
        }
        // Deterministic hash from coordinates + layer identity
        long hash = worldX * 73856093L ^ worldZ * 19349669L ^ (long) getId().hashCode();
        int index = (int) ((hash & 0x7FFFFFFFL) % prefabEntries.size());
        return prefabEntries.get(index);
    }

    /**
     * Get the first entry's category (used in export for the PrefabMarker category field).
     */
    public String getPrimaryCategory() {
        return prefabEntries.isEmpty() ? "Unknown" : prefabEntries.get(0).getCategory();
    }

    @Override
    public BufferedImage getIcon() {
        if (icon == null) {
            icon = createColorIcon();
        }
        return icon;
    }

    private BufferedImage createColorIcon() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(colorRGB));
        g.fillRect(0, 0, 16, 16);
        g.setColor(Color.DARK_GRAY);
        g.drawRect(0, 0, 15, 15);
        g.dispose();
        return img;
    }

    private transient BufferedImage icon;
    private static final long serialVersionUID = 1L;
}
