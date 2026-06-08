package org.pepsoft.worldpainter.hytale.prefab;

import org.pepsoft.worldpainter.hytale.HytalePrefabLayer;
import org.pepsoft.worldpainter.hytale.PrefabFileEntry;

import java.io.File;
import java.util.*;

/** Unified, searchable catalogue of placeable Hytale prefabs for the palette. */
public final class PrefabPaletteModel {

    public static final class PrefabItem {
        public final String category;
        public final String name;
        public final String path; // relative path, e.g. Prefabs/Trees/Oak.prefab.json

        public PrefabItem(String category, String name, String path) {
            this.category = category;
            this.name = name;
            this.path = path;
        }
    }

    private final List<PrefabItem> items = new ArrayList<>();

    public static PrefabPaletteModel builtInsOnly() {
        PrefabPaletteModel m = new PrefabPaletteModel();
        m.addBuiltIns();
        return m;
    }

    public static PrefabPaletteModel withDiscovered(File hytaleAssetsDir) {
        PrefabPaletteModel m = builtInsOnly();
        if (hytaleAssetsDir != null) {
            for (PrefabFileEntry e : HytalePrefabDiscovery.discoverPrefabs(hytaleAssetsDir)) {
                m.items.add(new PrefabItem(e.getCategory(), e.getDisplayName(), e.getRelativePath()));
            }
        }
        return m;
    }

    private void addBuiltIns() {
        for (String cat : HytalePrefabLayer.getCategories()) {
            for (HytalePrefabLayer.PrefabEntry e : HytalePrefabLayer.getPrefabsInCategory(cat)) {
                items.add(new PrefabItem(e.category, e.name, e.path));
            }
        }
    }

    public List<PrefabItem> allItems() {
        return Collections.unmodifiableList(items);
    }

    public List<String> getCategories() {
        LinkedHashSet<String> cats = new LinkedHashSet<>();
        for (PrefabItem i : items) {
            cats.add(i.category);
        }
        return new ArrayList<>(cats);
    }

    public List<PrefabItem> itemsInCategory(String category) {
        List<PrefabItem> out = new ArrayList<>();
        for (PrefabItem i : items) {
            if (i.category.equals(category)) {
                out.add(i);
            }
        }
        return out;
    }

    public List<PrefabItem> search(String query) {
        if ((query == null) || query.trim().isEmpty()) {
            return allItems();
        }
        String q = query.toLowerCase();
        List<PrefabItem> out = new ArrayList<>();
        for (PrefabItem i : items) {
            if (i.name.toLowerCase().contains(q) || i.category.toLowerCase().contains(q)
                    || i.path.toLowerCase().contains(q)) {
                out.add(i);
            }
        }
        return out;
    }
}
