package org.pepsoft.worldpainter.hytale.prefab;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Renders a top-down thumbnail of a Hytale prefab: for each (x,z) column, the
 * topmost non-empty block's colour, with light height shading. Results are cached
 * by (path, size). Headless-safe (pure {@link BufferedImage}).
 */
public final class HytalePrefabThumbnailRenderer {
    private final File serverDir;
    private final Map<String, BufferedImage> cache = new HashMap<>();

    public HytalePrefabThumbnailRenderer(File hytaleAssetsDir) {
        this.serverDir = (hytaleAssetsDir != null) ? new File(hytaleAssetsDir, "Server") : null;
    }

    public synchronized BufferedImage render(String relativePath, int size) {
        final String cacheKey = relativePath + "@" + size;
        BufferedImage cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        BufferedImage img = renderUncached(relativePath, size);
        cache.put(cacheKey, img);
        return img;
    }

    private BufferedImage renderUncached(String relativePath, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        TopColumn[][] grid = loadTopColumns(relativePath);
        if (grid == null) {
            return img; // transparent placeholder for missing/invalid prefab
        }
        int w = grid.length, d = (w > 0) ? grid[0].length : 0;
        if ((w == 0) || (d == 0)) {
            return img;
        }
        double scale = Math.min((double) size / w, (double) size / d);
        int drawW = Math.max(1, (int) Math.round(w * scale));
        int drawD = Math.max(1, (int) Math.round(d * scale));
        int offX = (size - drawW) / 2, offY = (size - drawD) / 2;
        for (int px = 0; px < drawW; px++) {
            for (int pz = 0; pz < drawD; pz++) {
                int gx = (int) (px / scale), gz = (int) (pz / scale);
                if ((gx >= w) || (gz >= d)) {
                    continue;
                }
                TopColumn c = grid[gx][gz];
                if (c == null) {
                    continue;
                }
                img.setRGB(offX + px, offY + pz, c.argb);
            }
        }
        return img;
    }

    private TopColumn[][] loadTopColumns(String relativePath) {
        if (serverDir == null) {
            return null;
        }
        File file = new File(serverDir, relativePath.replace('/', File.separatorChar));
        if (!file.isFile()) {
            return null;
        }
        try (Reader r = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            JsonElement root = JsonParser.parseReader(r);
            if ((root == null) || !root.isJsonObject()) {
                return null;
            }
            JsonElement blocksEl = root.getAsJsonObject().get("blocks");
            if ((blocksEl == null) || !blocksEl.isJsonArray()) {
                return null;
            }
            JsonArray blocks = blocksEl.getAsJsonArray();
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE,
                    minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
            for (JsonElement e : blocks) {
                JsonObject b = e.getAsJsonObject();
                if (isEmpty(b)) {
                    continue;
                }
                int x = b.get("x").getAsInt(), y = b.get("y").getAsInt(), z = b.get("z").getAsInt();
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
            }
            if (minX > maxX) {
                return null;
            }
            int w = (maxX - minX) + 1, d = (maxZ - minZ) + 1;
            TopColumn[][] grid = new TopColumn[w][d];
            for (JsonElement e : blocks) {
                JsonObject b = e.getAsJsonObject();
                if (isEmpty(b)) {
                    continue;
                }
                int x = b.get("x").getAsInt() - minX, z = b.get("z").getAsInt() - minZ, y = b.get("y").getAsInt();
                String name = b.get("name").getAsString().trim();
                TopColumn cur = grid[x][z];
                if ((cur == null) || (y > cur.topY)) {
                    grid[x][z] = new TopColumn(y, shade(colorFor(name), y, minY, maxY));
                }
            }
            return grid;
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    private static boolean isEmpty(JsonObject b) {
        JsonElement n = b.get("name");
        if ((n == null) || !n.isJsonPrimitive()) {
            return true;
        }
        String s = n.getAsString().trim();
        return s.isEmpty() || s.equals("Empty") || s.equals("Editor_Empty");
    }

    /**
     * Deterministic block-to-colour fallback. (If a Hytale block/terrain colour source
     * is later wired in for previews, it can replace this.)
     */
    private static int colorFor(String blockName) {
        int h = blockName.hashCode();
        int r = 80 + ((h >> 16) & 0x7F);
        int g = 80 + ((h >> 8) & 0x7F);
        int b = 80 + (h & 0x7F);
        return new Color(r, g, b).getRGB();
    }

    private static int shade(int argb, int y, int minY, int maxY) {
        double t = (maxY > minY) ? ((double) (y - minY) / (maxY - minY)) : 1.0;
        double f = 0.6 + (0.4 * t);
        Color c = new Color(argb);
        int r = clamp((int) (c.getRed() * f)), g = clamp((int) (c.getGreen() * f)), b = clamp((int) (c.getBlue() * f));
        return new Color(r, g, b, 255).getRGB();
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static final class TopColumn {
        final int topY;
        final int argb;

        TopColumn(int topY, int argb) {
            this.topY = topY;
            this.argb = argb;
        }
    }
}
