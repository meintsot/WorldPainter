package org.pepsoft.worldpainter.hytale;

import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.layers.Layer;

/**
 * Per-pixel layer for storing HytaleTerrain indices.
 * Uses two BYTE layers to form a 16-bit index (supports up to 65535 terrains).
 * Value 0 = no terrain set (fall back to Terrain-based lookup).
 * Values 1..N = HytaleTerrain index + 1.
 */
public final class HytaleTerrainLayer {

    private HytaleTerrainLayer() {}

    /** Low 8 bits of the terrain index. */
    public static final Layer LO = new ByteLayer("HyTerLo", "Hytale terrain index (low byte)");

    /** High 8 bits of the terrain index. */
    public static final Layer HI = new ByteLayer("HyTerHi", "Hytale terrain index (high byte)");

    /**
     * Store a 1-based HytaleTerrain index on a tile pixel.
     * Use index 0 to clear.
     */
    public static void setTerrainIndex(Tile tile, int x, int y, int index) {
        tile.setLayerValue(LO, x, y, index & 0xFF);
        tile.setLayerValue(HI, x, y, (index >> 8) & 0xFF);
    }

    /**
     * Read the 1-based HytaleTerrain index from a tile pixel.
     * Returns 0 if no Hytale terrain is set.
     */
    public static int getTerrainIndex(Tile tile, int x, int y) {
        int lo = tile.getLayerValue(LO, x, y);
        int hi = tile.getLayerValue(HI, x, y);
        return (hi << 8) | lo;
    }

    /**
     * Check whether a tile has any Hytale terrain data.
     */
    public static boolean hasTerrainData(Tile tile) {
        return tile.hasLayer(LO);
    }

    /**
     * A simple BYTE-sized layer with default value 0.
     */
    private static final class ByteLayer extends Layer {
        ByteLayer(String id, String description) {
            super(id, description, DataSize.BYTE, true, 0);
        }

        @Override
        public int getDefaultValue() {
            return 0;
        }

        private static final long serialVersionUID = 1L;
    }
}
