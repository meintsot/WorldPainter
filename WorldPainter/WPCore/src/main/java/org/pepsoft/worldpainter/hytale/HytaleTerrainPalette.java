package org.pepsoft.worldpainter.hytale;

import org.pepsoft.worldpainter.Tile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helpers for the self-describing Hytale terrain palette.
 *
 * <p>Per-pixel Hytale terrain is stored as a 1-based ordinal into
 * {@link HytaleTerrain#currentTerrainPaletteBlockIds()}. That ordinal's meaning
 * shifts whenever the terrain list changes (notably when the block registry
 * grows), so a world that stored "clay" can read back as a different block. To
 * keep saves stable we persist the ordinal-&gt;block-id palette at save time and
 * remap stored ordinals back to current ordinals by block id at load time.</p>
 */
public final class HytaleTerrainPalette {

    private static final Logger logger = LoggerFactory.getLogger(HytaleTerrainPalette.class);

    private HytaleTerrainPalette() {}

    /**
     * Build the current palette as a {@code stored-index -> block-id} map for
     * every 1-based index in {@link HytaleTerrain#currentTerrainPaletteBlockIds()}.
     */
    public static Map<Integer, String> currentPalette() {
        List<String> ids = HytaleTerrain.currentTerrainPaletteBlockIds();
        Map<Integer, String> palette = new HashMap<>(ids.size() * 2);
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            if (id != null) {
                palette.put(i + 1, id);
            }
        }
        return palette;
    }

    /**
     * Remap a single stored 1-based index using {@code oldPalette}
     * ({@code stored-index -> block-id} as it was when the world was saved).
     * Returns the current layer index for that block id, {@code 0} if the block
     * id no longer resolves to a terrain, or the original index unchanged if it
     * is {@code 0} or not present in the palette.
     */
    public static int remapIndex(int oldIndex, Map<Integer, String> oldPalette) {
        if (oldIndex <= 0) {
            return 0;
        }
        String blockId = oldPalette.get(oldIndex);
        if (blockId == null) {
            // Index not described by the palette — cannot safely remap; leave as-is.
            return oldIndex;
        }
        HytaleTerrain current = HytaleTerrain.getByBlockId(blockId);
        return (current != null) ? current.getLayerIndex() : 0;
    }

    /**
     * Remap every pixel of every tile that carries Hytale terrain data using
     * {@code oldPalette}. Pixels whose index is unchanged are skipped. Returns
     * the number of pixels actually rewritten.
     */
    public static int remapTiles(Collection<? extends Tile> tiles, Map<Integer, String> oldPalette) {
        int changed = 0;
        for (Tile tile : tiles) {
            if (!HytaleTerrainLayer.hasTerrainData(tile)) {
                continue;
            }
            for (int y = 0; y < 128; y++) {
                for (int x = 0; x < 128; x++) {
                    int oldIndex = HytaleTerrainLayer.getTerrainIndex(tile, x, y);
                    if (oldIndex <= 0) {
                        continue;
                    }
                    int newIndex = remapIndex(oldIndex, oldPalette);
                    if (newIndex != oldIndex) {
                        HytaleTerrainLayer.setTerrainIndex(tile, x, y, newIndex);
                        changed++;
                    }
                }
            }
        }
        if (changed > 0) {
            logger.info("Hytale terrain palette remap: rewrote {} pixels", changed);
        }
        return changed;
    }
}
