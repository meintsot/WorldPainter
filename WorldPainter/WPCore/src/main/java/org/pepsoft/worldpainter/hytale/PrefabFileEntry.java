package org.pepsoft.worldpainter.hytale;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents a single discovered Hytale .prefab.json file from the assets directory.
 * Stored in HytaleSpecificPrefabLayer instances and serialized with the world.
 */
public final class PrefabFileEntry implements Serializable {
    private final String displayName;
    private final String category;
    private final String subCategory;
    private final String relativePath;
    private int frequency = DEFAULT_FREQUENCY;

    public PrefabFileEntry(String displayName, String category, String subCategory, String relativePath) {
        this.displayName = Objects.requireNonNull(displayName);
        this.category = Objects.requireNonNull(category);
        this.subCategory = Objects.requireNonNull(subCategory);
        this.relativePath = Objects.requireNonNull(relativePath);
    }

    public String getDisplayName() { return displayName; }
    public String getCategory() { return category; }
    public String getSubCategory() { return subCategory; }
    public String getRelativePath() { return relativePath; }

    /**
     * Get the frequency weight for this prefab entry (1-1000).
     * Higher values mean this prefab is chosen more often relative to others in the same layer.
     */
    public int getFrequency() { return frequency; }

    /**
     * Set the frequency weight for this prefab entry (1-1000).
     */
    public void setFrequency(int frequency) {
        this.frequency = Math.max(1, Math.min(1000, frequency));
    }

    /**
     * Matches against a search query (case-insensitive).
     * Checks displayName, category, and subCategory.
     */
    public boolean matchesSearch(String query) {
        String lower = query.toLowerCase();
        return displayName.toLowerCase().contains(lower)
            || category.toLowerCase().contains(lower)
            || subCategory.toLowerCase().contains(lower);
    }

    @Override
    public String toString() {
        return "[" + category + "] " + subCategory + " / " + displayName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PrefabFileEntry)) return false;
        PrefabFileEntry that = (PrefabFileEntry) o;
        return relativePath.equals(that.relativePath);
    }

    @Override
    public int hashCode() {
        return relativePath.hashCode();
    }

    public static final int DEFAULT_FREQUENCY = 100;

    private static final long serialVersionUID = 2L;
}
