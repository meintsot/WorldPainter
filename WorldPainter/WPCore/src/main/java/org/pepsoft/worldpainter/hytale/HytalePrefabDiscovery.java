package org.pepsoft.worldpainter.hytale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Scans the HytaleAssets directory for .prefab.json files and returns
 * a sorted list of PrefabFileEntry instances.
 */
public final class HytalePrefabDiscovery {
    private HytalePrefabDiscovery() {} // utility class

    /**
     * Load the prefab catalog bundled with WorldPainter so the prefab tab remains
     * usable even when HytaleAssets is not available locally.
     */
    public static List<PrefabFileEntry> loadBundledPrefabs() {
        List<PrefabFileEntry> cached = bundledPrefabs;
        if (cached != null) {
            return cached;
        }

        try (InputStream inputStream = HytalePrefabDiscovery.class.getResourceAsStream(BUNDLED_PREFABS_RESOURCE)) {
            if (inputStream == null) {
                logger.warn("Bundled prefab catalog {} not found", BUNDLED_PREFABS_RESOURCE);
                bundledPrefabs = Collections.emptyList();
                return bundledPrefabs;
            }

            List<PrefabFileEntry> entries = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    String[] parts = line.split("\t", 4);
                    if (parts.length != 4) {
                        logger.warn("Ignoring malformed bundled prefab catalog line: {}", line);
                        continue;
                    }
                    entries.add(new PrefabFileEntry(parts[0], parts[1], parts[2], parts[3]));
                }
            }

            bundledPrefabs = Collections.unmodifiableList(entries);
            return bundledPrefabs;
        } catch (IOException e) {
            logger.warn("Could not read bundled prefab catalog", e);
            bundledPrefabs = Collections.emptyList();
            return bundledPrefabs;
        }
    }

    /**
     * Discover all .prefab.json files under {@code baseDir/Prefabs/}.
     *
     * @param baseDir the Hytale assets root (parent of "Prefabs" folder)
     * @return sorted list of PrefabFileEntry, empty if no prefabs found
     */
    public static List<PrefabFileEntry> discoverPrefabs(File baseDir) {
        File prefabsDir = new File(baseDir, "Prefabs");
        if (!prefabsDir.isDirectory()) {
            return Collections.emptyList();
        }
        List<PrefabFileEntry> results = new ArrayList<>();
        scanDirectory(prefabsDir, prefabsDir, results);
        results.sort((a, b) -> {
            int cmp = a.getCategory().compareToIgnoreCase(b.getCategory());
            if (cmp != 0) return cmp;
            cmp = a.getSubCategory().compareToIgnoreCase(b.getSubCategory());
            if (cmp != 0) return cmp;
            return a.getDisplayName().compareToIgnoreCase(b.getDisplayName());
        });
        return results;
    }

    private static void scanDirectory(File dir, File prefabsRoot, List<PrefabFileEntry> results) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                scanDirectory(child, prefabsRoot, results);
            } else if (child.getName().endsWith(".prefab.json")) {
                PrefabFileEntry entry = createEntry(child, prefabsRoot);
                if (entry != null) {
                    results.add(entry);
                }
            }
        }
    }

    private static PrefabFileEntry createEntry(File file, File prefabsRoot) {
        String pathFromPrefabs = getRelativePath(prefabsRoot, file);
        String relativePath = "Prefabs/" + pathFromPrefabs.replace('\\', '/');

        String[] parts = pathFromPrefabs.replace('\\', '/').split("/");
        if (parts.length < 2) {
            // File directly inside Prefabs/ with no category subfolder
            return null;
        }
        String category = parts[0];
        String subCategory = parts.length >= 3 ? parts[1] : parts[0];
        String fileName = file.getName();
        String displayName = fileName.substring(0, fileName.length() - ".prefab.json".length());

        return new PrefabFileEntry(displayName, category, subCategory, relativePath);
    }

    private static String getRelativePath(File base, File file) {
        return base.toPath().relativize(file.toPath()).toString();
    }

    private static volatile List<PrefabFileEntry> bundledPrefabs;

    private static final String BUNDLED_PREFABS_RESOURCE = "/org/pepsoft/worldpainter/hytale/bundled-prefabs.tsv";
    private static final Logger logger = LoggerFactory.getLogger(HytalePrefabDiscovery.class);
}
