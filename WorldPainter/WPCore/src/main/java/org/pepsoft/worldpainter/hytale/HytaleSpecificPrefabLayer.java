package org.pepsoft.worldpainter.hytale;

import org.pepsoft.worldpainter.layers.CustomLayer;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * A custom NIBBLE layer that places specific Hytale prefab files.
 * Each instance stores a list of prefab entries and configuration for
 * density, grid spacing, random displacement, and per-prefab frequency weights.
 *
 * <p>Similar to {@link org.pepsoft.worldpainter.layers.Bo2Layer} but for
 * Hytale prefabs discovered from the assets directory.
 *
 * @deprecated New prefab layers should use {@link org.pepsoft.worldpainter.layers.Bo2Layer}
 *     with WPObjects loaded via {@link HytalePrefabJsonObject#load(java.io.File)}.
 *     This class is retained for backward compatibility with saved worlds.
 */
@Deprecated
public class HytaleSpecificPrefabLayer extends CustomLayer {
    private final List<PrefabFileEntry> prefabEntries;
    private final int colorRGB;
    private int density = DEFAULT_DENSITY;
    private int gridX = 1;
    private int gridZ = 1;
    private int randomDisplacement = 0;

    public HytaleSpecificPrefabLayer(String name, List<PrefabFileEntry> entries, Color color) {
        super(name,
              "Specific prefabs: " + entries.size() + " variant(s)",
              DataSize.NIBBLE, 91, color);
        this.prefabEntries = new ArrayList<>(entries);
        this.colorRGB = color.getRGB();
    }

    public List<PrefabFileEntry> getPrefabEntries() {
        return Collections.unmodifiableList(prefabEntries);
    }

    /**
     * Replace the prefab entries in this layer with a new list.
     */
    public void setPrefabEntries(List<PrefabFileEntry> entries) {
        prefabEntries.clear();
        prefabEntries.addAll(entries);
        setDescription("Specific prefabs: " + entries.size() + " variant(s)");
    }

    public Color getColor() {
        return new Color(colorRGB, true);
    }

    /**
     * Blocks per attempt — higher values mean sparser placement.
     * Default is {@value #DEFAULT_DENSITY}. The probability of placing a prefab
     * at a given position is {@code (strength^2) / (density * 64)}.
     */
    public int getDensity() {
        return density;
    }

    public void setDensity(int density) {
        this.density = Math.max(1, density);
    }

    public int getGridX() {
        return gridX;
    }

    public void setGridX(int gridX) {
        this.gridX = Math.max(1, gridX);
    }

    public int getGridZ() {
        return gridZ;
    }

    public void setGridZ(int gridZ) {
        this.gridZ = Math.max(1, gridZ);
    }

    public int getRandomDisplacement() {
        return randomDisplacement;
    }

    public void setRandomDisplacement(int randomDisplacement) {
        this.randomDisplacement = Math.max(0, randomDisplacement);
    }

    /**
     * Select a prefab entry for the given world coordinates, taking into
     * account each entry's frequency weight. Uses a deterministic seed
     * so the same location always yields the same prefab.
     */
    public PrefabFileEntry selectPrefab(int worldX, int worldZ) {
        if (prefabEntries.size() == 1) {
            return prefabEntries.get(0);
        }
        // Deterministic seed from coordinates + layer identity
        long seed = worldX * 73856093L ^ worldZ * 19349669L ^ (long) getId().hashCode();
        Random rng = new Random(seed);

        // Weighted selection
        int totalWeight = 0;
        for (PrefabFileEntry entry : prefabEntries) {
            totalWeight += entry.getFrequency();
        }
        int roll = rng.nextInt(totalWeight);
        int cumulative = 0;
        for (PrefabFileEntry entry : prefabEntries) {
            cumulative += entry.getFrequency();
            if (roll < cumulative) {
                return entry;
            }
        }
        // Fallback (should not happen)
        return prefabEntries.get(prefabEntries.size() - 1);
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

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        // Legacy support: files saved with version 1 (BIT layer) will have density == 0
        if (density == 0) {
            density = DEFAULT_DENSITY;
        }
        if (gridX == 0) {
            gridX = 1;
        }
        if (gridZ == 0) {
            gridZ = 1;
        }
    }

    private transient BufferedImage icon;

    public static final int DEFAULT_DENSITY = 20;
    private static final long serialVersionUID = 2L;
}
