package org.pepsoft.worldpainter.hytale;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads Hytale prefab JSON files and pastes their blocks directly into
 * {@link HytaleChunk} data. This is the correct approach for exporting
 * prefabs — Hytale's server expects prefab blocks to be baked inline
 * into chunk data, not stored as metadata markers.
 *
 * <p>Prefab files are cached after first load to avoid repeated disk I/O
 * during export of large worlds.</p>
 */
public final class HytalePrefabPaster {

    private static final Logger logger = LoggerFactory.getLogger(HytalePrefabPaster.class);

    /** Cache of parsed prefab data keyed by relative path. */
    private final Map<String, PrefabBlockData> cache = new ConcurrentHashMap<>();

    /** Root directory for resolving prefab file paths (HytaleAssets/Server/). */
    private final File serverDir;

    /**
     * @param hytaleAssetsDir the HytaleAssets directory (parent of Server/)
     */
    public HytalePrefabPaster(File hytaleAssetsDir) {
        this.serverDir = (hytaleAssetsDir != null)
                ? new File(hytaleAssetsDir, "Server")
                : null;
    }

    /**
     * Paste a prefab's blocks into a chunk at the given anchor position.
     *
     * @param chunk      the chunk to paste into
     * @param anchorX    chunk-local X of the anchor column (0-31)
     * @param anchorY    world Y of the anchor (typically surface height + 1)
     * @param anchorZ    chunk-local Z of the anchor column (0-31)
     * @param worldX     absolute world X (for logging)
     * @param worldZ     absolute world Z (for logging)
     * @param prefabPath relative path like {@code Prefabs/Trees/Oak/Stage_5/Oak_Stage5_003.prefab.json}
     * @return true if the prefab was loaded and pasted, false if it could not be resolved
     */
    public boolean paste(HytaleChunk chunk, int anchorX, int anchorY, int anchorZ,
                         int worldX, int worldZ, String prefabPath) {
        PrefabBlockData data = loadPrefab(prefabPath);
        if (data == null || data.blocks.isEmpty()) {
            return false;
        }

        for (PrefabBlock block : data.blocks) {
            int bx = anchorX + block.x - data.anchorX;
            int by = anchorY + block.y - data.anchorY;
            int bz = anchorZ + block.z - data.anchorZ;

            // Only place blocks that fall within this chunk's column bounds
            if (bx < 0 || bx >= HytaleChunk.CHUNK_SIZE || bz < 0 || bz >= HytaleChunk.CHUNK_SIZE) {
                continue;
            }
            if (by < 0 || by >= chunk.getMaxHeight()) {
                continue;
            }

            HytaleBlock hBlock = HytaleBlock.of(block.blockName, block.rotation);
            chunk.setHytaleBlock(bx, by, bz, hBlock);
        }

        // Also paste fluids
        for (PrefabFluid fluid : data.fluids) {
            int fx = anchorX + fluid.x - data.anchorX;
            int fy = anchorY + fluid.y - data.anchorY;
            int fz = anchorZ + fluid.z - data.anchorZ;

            if (fx < 0 || fx >= HytaleChunk.CHUNK_SIZE || fz < 0 || fz >= HytaleChunk.CHUNK_SIZE) {
                continue;
            }
            if (fy < 0 || fy >= chunk.getMaxHeight()) {
                continue;
            }

            int sectionIndex = fy >> 5;
            if (sectionIndex >= 0 && sectionIndex < chunk.getSections().length) {
                chunk.getSections()[sectionIndex].setFluid(fx, fy & 31, fz, fluid.fluidName, fluid.level);
            }
        }

        return true;
    }

    /**
     * Load and cache a prefab's block data.
     *
     * @param relativePath path like {@code Prefabs/Cave/Formations/Rock_Stone/...prefab.json}
     * @return parsed block data, or null if the file cannot be found/parsed
     */
    private PrefabBlockData loadPrefab(String relativePath) {
        PrefabBlockData cached = cache.get(relativePath);
        if (cached != null) {
            return cached == EMPTY_PREFAB ? null : cached;
        }

        if (serverDir == null) {
            logger.debug("No HytaleAssets directory configured; cannot load prefab {}", relativePath);
            cache.put(relativePath, EMPTY_PREFAB);
            return null;
        }

        File prefabFile = new File(serverDir, relativePath.replace('/', File.separatorChar));
        if (!prefabFile.isFile()) {
            logger.warn("Prefab file not found: {} (resolved to {})", relativePath, prefabFile.getAbsolutePath());
            cache.put(relativePath, EMPTY_PREFAB);
            return null;
        }

        try {
            PrefabBlockData data = parsePrefabJson(prefabFile);
            cache.put(relativePath, data);
            return data;
        } catch (IOException e) {
            logger.warn("Failed to parse prefab file {}: {}", relativePath, e.getMessage());
            cache.put(relativePath, EMPTY_PREFAB);
            return null;
        }
    }

    private static PrefabBlockData parsePrefabJson(File file) throws IOException {
        JsonObject root;
        try (Reader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            JsonElement element = JsonParser.parseReader(reader);
            if (element == null || !element.isJsonObject()) {
                throw new IOException("Invalid prefab JSON: root must be an object");
            }
            root = element.getAsJsonObject();
        }

        int anchorX = getInt(root, "anchorX", 0);
        int anchorY = getInt(root, "anchorY", 0);
        int anchorZ = getInt(root, "anchorZ", 0);

        List<PrefabBlock> blocks = new ArrayList<>();
        JsonArray blocksArray = getArray(root, "blocks");
        if (blocksArray != null) {
            for (JsonElement elem : blocksArray) {
                if (!elem.isJsonObject()) continue;
                JsonObject entry = elem.getAsJsonObject();
                Integer x = getIntObj(entry, "x");
                Integer y = getIntObj(entry, "y");
                Integer z = getIntObj(entry, "z");
                String name = getString(entry, "name");
                if (x == null || y == null || z == null || name == null) continue;

                // Skip air/empty blocks
                String trimmed = name.trim();
                if (trimmed.isEmpty() || trimmed.equals("Empty") || trimmed.equals("Editor_Empty")) continue;

                int rotation = getInt(entry, "rotation", 0);
                blocks.add(new PrefabBlock(x, y, z, trimmed, rotation));
            }
        }

        List<PrefabFluid> fluids = new ArrayList<>();
        JsonArray fluidsArray = getArray(root, "fluids");
        if (fluidsArray != null) {
            for (JsonElement elem : fluidsArray) {
                if (!elem.isJsonObject()) continue;
                JsonObject entry = elem.getAsJsonObject();
                Integer x = getIntObj(entry, "x");
                Integer y = getIntObj(entry, "y");
                Integer z = getIntObj(entry, "z");
                String name = getString(entry, "name");
                if (x == null || y == null || z == null || name == null) continue;
                int level = getInt(entry, "level", 1);
                fluids.add(new PrefabFluid(x, y, z, name.trim(), level));
            }
        }

        return new PrefabBlockData(anchorX, anchorY, anchorZ, blocks, fluids);
    }

    // ── JSON helpers ──────────────────────────────────────────────────

    private static JsonArray getArray(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        return (e != null && e.isJsonArray()) ? e.getAsJsonArray() : null;
    }

    private static Integer getIntObj(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        if (e == null || !e.isJsonPrimitive()) return null;
        try { return e.getAsInt(); } catch (NumberFormatException ex) { return null; }
    }

    private static int getInt(JsonObject obj, String key, int def) {
        Integer v = getIntObj(obj, key);
        return v != null ? v : def;
    }

    private static String getString(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        if (e == null || !e.isJsonPrimitive()) return null;
        try { return e.getAsString(); } catch (UnsupportedOperationException ex) { return null; }
    }

    // ── Data classes ──────────────────────────────────────────────────

    private static final PrefabBlockData EMPTY_PREFAB = new PrefabBlockData(0, 0, 0,
            Collections.emptyList(), Collections.emptyList());

    static final class PrefabBlockData {
        final int anchorX, anchorY, anchorZ;
        final List<PrefabBlock> blocks;
        final List<PrefabFluid> fluids;

        PrefabBlockData(int anchorX, int anchorY, int anchorZ,
                        List<PrefabBlock> blocks, List<PrefabFluid> fluids) {
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.anchorZ = anchorZ;
            this.blocks = blocks;
            this.fluids = fluids;
        }
    }

    static final class PrefabBlock {
        final int x, y, z;
        final String blockName;
        final int rotation;

        PrefabBlock(int x, int y, int z, String blockName, int rotation) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.blockName = blockName;
            this.rotation = rotation;
        }
    }

    static final class PrefabFluid {
        final int x, y, z;
        final String fluidName;
        final int level;

        PrefabFluid(int x, int y, int z, String fluidName, int level) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.fluidName = fluidName;
            this.level = level;
        }
    }
}
