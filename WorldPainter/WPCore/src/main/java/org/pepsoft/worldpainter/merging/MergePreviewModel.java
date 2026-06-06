package org.pepsoft.worldpainter.merging;

import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.layers.Void;

import java.awt.Rectangle;

import static org.pepsoft.worldpainter.Constants.TILE_SIZE;

/**
 * Pure (non-Swing) geometry helpers for the "Import Map and Merge" preview
 * (TP-46). The map importers create a {@link Tile} for every WP tile that
 * contains at least one stored chunk and mark every non-imported pixel with
 * the {@link Void} bit layer (see {@code HytaleMapImporter#importChunk}). The
 * editor canvas renders Void pixels as transparent, so an imported map looks
 * like the real shape of its data. This helper lets the merge preview apply
 * the same rule: a fully-Void tile is treated as absent, and a partially
 * imported tile reports the fraction of it that holds real data, so the
 * preview's bounding box and shading match what the user sees in the editor
 * rather than the importer's raw (rectangular, Void-padded) tile grid.
 */
public final class MergePreviewModel {

    /** Total pixels in a single tile. */
    public static final int PIXELS_PER_TILE = TILE_SIZE * TILE_SIZE;

    /**
     * The number of pixels in the tile that hold real (non-Void) data.
     * A tile that never received any Void data (e.g. one painted normally,
     * not imported) is fully content.
     */
    public static int contentPixelCount(Tile tile) {
        // Fast path: tiles that were never marked Void are entirely content.
        // Only the importers add the Void layer, so this skips the per-pixel
        // scan for every normally-painted tile.
        if (! tile.hasLayer(Void.INSTANCE)) {
            return PIXELS_PER_TILE;
        }
        int count = 0;
        for (int x = 0; x < TILE_SIZE; x++) {
            for (int y = 0; y < TILE_SIZE; y++) {
                if (! tile.getBitLayerValue(Void.INSTANCE, x, y)) {
                    count++;
                }
            }
        }
        return count;
    }

    /** Whether the tile holds any real (non-Void) data at all. */
    public static boolean hasContent(Tile tile) {
        // Fast path mirrors contentPixelCount: no Void layer ⇒ all content.
        if (! tile.hasLayer(Void.INSTANCE)) {
            return true;
        }
        for (int x = 0; x < TILE_SIZE; x++) {
            for (int y = 0; y < TILE_SIZE; y++) {
                if (! tile.getBitLayerValue(Void.INSTANCE, x, y)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Fraction (0..1) of the tile that holds real (non-Void) data. */
    public static float coverage(Tile tile) {
        return (float) contentPixelCount(tile) / PIXELS_PER_TILE;
    }

    /**
     * The bounding box, in tile coordinates, that spans only the dimension's
     * tiles which {@linkplain #hasContent(Tile) hold real data}. Fully-Void
     * tiles (created by the importer for non-imported chunks) are ignored so
     * the box matches the visible shape of the map.
     *
     * @return the content bounding box, or {@code null} if no tile has content.
     */
    public static Rectangle contentExtent(Dimension dimension) {
        Rectangle box = null;
        for (Tile tile : dimension.getTiles()) {
            if (! hasContent(tile)) {
                continue;
            }
            Rectangle tileBox = new Rectangle(tile.getX(), tile.getY(), 1, 1);
            box = (box == null) ? tileBox : box.union(tileBox);
        }
        return box;
    }

    private MergePreviewModel() {
        // Utility class; no instances.
    }
}
