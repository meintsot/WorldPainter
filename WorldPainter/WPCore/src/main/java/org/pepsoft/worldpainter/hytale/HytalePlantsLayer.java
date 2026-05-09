package org.pepsoft.worldpainter.hytale;

import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.layers.Layer;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.util.Collection;

import static org.pepsoft.worldpainter.Constants.TILE_SIZE;

/**
 * Per-pixel layer storing the {@link HytaleTerrain} index for surface-only blocks
 * (plants/decorations) painted on top of the substrate held by {@link HytaleTerrainLayer}.
 *
 * <p>Mirrors the two-byte 16-bit-index pattern of {@link HytaleTerrainLayer}. A non-zero
 * value means a plant overlay is present at this pixel; the exporter places it at
 * {@code height + 1} on top of whatever the substrate produced.
 *
 * <p>Value 0 = no plant overlay.
 * Values 1..N = HytaleTerrain index (1-based, same encoding as {@link HytaleTerrainLayer}).
 */
public final class HytalePlantsLayer {

    private HytalePlantsLayer() {}

    /** Low 8 bits of the plant index. */
    public static final Layer LO = new ByteLayer("HyPlantLo", "Hytale plant index (low byte)");

    /** High 8 bits of the plant index. */
    public static final Layer HI = new ByteLayer("HyPlantHi", "Hytale plant index (high byte)");

    /**
     * Store a 1-based HytaleTerrain index at a tile pixel as a plant overlay.
     * Use index 0 to clear the overlay.
     */
    public static void setPlantIndex(Tile tile, int x, int y, int index) {
        tile.setLayerValue(LO, x, y, index & 0xFF);
        tile.setLayerValue(HI, x, y, (index >> 8) & 0xFF);
    }

    /**
     * Read the 1-based HytaleTerrain index from a tile pixel.
     * Returns 0 if no plant overlay is set at this pixel.
     */
    public static int getPlantIndex(Tile tile, int x, int y) {
        int lo = tile.getLayerValue(LO, x, y);
        int hi = tile.getLayerValue(HI, x, y);
        return (hi << 8) | lo;
    }

    /**
     * Check whether a tile has any plant-overlay data.
     */
    public static boolean hasPlantData(Tile tile) {
        return tile.hasLayer(LO);
    }

    /**
     * Walk every pixel of the given tiles and move surface-only HytaleTerrain indices from
     * {@link HytaleTerrainLayer} into {@link HytalePlantsLayer}, clearing the substrate slot.
     * Solid HytaleTerrains are left in place. Used as a one-time save migration when loading
     * worlds painted before the plants/terrain split.
     */
    public static void migrateSurfaceOnlyFromTerrainLayer(Collection<? extends Tile> tiles) {
        if (tiles == null) {
            return;
        }
        for (Tile tile : tiles) {
            if (! HytaleTerrainLayer.hasTerrainData(tile)) {
                continue;
            }
            for (int x = 0; x < TILE_SIZE; x++) {
                for (int y = 0; y < TILE_SIZE; y++) {
                    int idx = HytaleTerrainLayer.getTerrainIndex(tile, x, y);
                    if (idx <= 0) {
                        continue;
                    }
                    HytaleTerrain terrain = HytaleTerrain.getByLayerIndex(idx);
                    if (terrain == null) {
                        continue;
                    }
                    if (HytaleBlockRegistry.isSurfaceOnlyBlock(terrain.getPrimaryBlock().id)) {
                        setPlantIndex(tile, x, y, idx);
                        HytaleTerrainLayer.setTerrainIndex(tile, x, y, 0);
                    }
                }
            }
        }
    }

    /**
     * Route a HytaleTerrain paint click at a single tile pixel to the right layer:
     * surface-only blocks (plants, decorations) go into {@link HytalePlantsLayer} so the
     * substrate held by {@link HytaleTerrainLayer} is preserved; solid terrains overwrite
     * the substrate as before.
     *
     * @return {@code true} if the paint went to the plants layer (surface-only),
     *         {@code false} if it went to the terrain layer (solid substrate).
     */
    public static boolean routePaint(Tile tile, int x, int y, HytaleTerrain hytaleTerrain) {
        if (HytaleBlockRegistry.isSurfaceOnlyBlock(hytaleTerrain.getPrimaryBlock().id)) {
            setPlantIndex(tile, x, y, hytaleTerrain.getLayerIndex());
            return true;
        }
        HytaleTerrainLayer.setTerrainIndex(tile, x, y, hytaleTerrain.getLayerIndex());
        return false;
    }

    private static final class ByteLayer extends Layer {
        ByteLayer(String id, String description) {
            super(id, description, DataSize.BYTE, true, 0);
        }

        @Override
        public int getDefaultValue() {
            return 0;
        }

        private Object readResolve() throws ObjectStreamException {
            if ("HyPlantLo".equals(getId())) {
                return LO;
            } else if ("HyPlantHi".equals(getId())) {
                return HI;
            }
            throw new InvalidObjectException("Unknown ByteLayer id: " + getId());
        }

        private static final long serialVersionUID = 1L;
    }
}
