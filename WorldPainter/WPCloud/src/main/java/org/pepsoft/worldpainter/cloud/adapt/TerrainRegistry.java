package org.pepsoft.worldpainter.cloud.adapt;

import org.pepsoft.worldpainter.Terrain;

/**
 * Stable bidirectional mapping between WorldPainter's {@link Terrain} enum and the byte ids
 * used in CRDT ops. Uses ordinal-based encoding (matches how {@code Tile} already stores
 * terrain internally — see {@code Tile.setTerrain}).
 */
public final class TerrainRegistry {

    private static final Terrain[] VALUES = Terrain.values();

    private TerrainRegistry() {}

    public static byte toByte(Terrain terrain) {
        if (terrain == null) throw new IllegalArgumentException("terrain");
        return (byte) terrain.ordinal();
    }

    public static Terrain fromByte(byte b) {
        int id = b & 0xFF;
        if (id < 0 || id >= VALUES.length) {
            throw new IllegalArgumentException("terrain id out of range: " + id
                    + " (max " + (VALUES.length - 1) + ")");
        }
        return VALUES[id];
    }
}
