package org.pepsoft.worldpainter.cloud.tile;

import com.talepainter.protocol.common.Common;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Client-side mirror of the tile state. 128x128 cells.
 *
 * <p>Per-cell HLCs are tracked for LWW resolution. {@code null} means the cell has never
 * been written by an op the client has seen (its value is whatever the snapshot baseline says,
 * which the client treats as HLC = snapshot HLC after decoding).
 */
public final class LocalTile {

    public static final int SIZE = 128;
    private static final int CELLS = SIZE * SIZE;

    public enum Field { TERRAIN, HEIGHT, WATER, LAYER, BIT_LAYER }

    private final int tileX, tileY;
    private final byte[]  terrain     = new byte[CELLS];
    private final int[]   height      = new int[CELLS];
    private final short[] waterLevel  = new short[CELLS];
    private final Common.Hlc[] terrainHlc = new Common.Hlc[CELLS];
    private final Common.Hlc[] heightHlc  = new Common.Hlc[CELLS];
    private final Common.Hlc[] waterHlc   = new Common.Hlc[CELLS];

    private final Map<Integer, byte[]> layerData = new HashMap<>();
    private final Map<Integer, Common.Hlc[]> layerHlc = new HashMap<>();

    private final Map<Integer, Set<UUID>> layerTags = new HashMap<>();
    private byte[] metadataBlob = new byte[0];
    private Common.Hlc metadataHlc = null;

    public LocalTile(int tileX, int tileY) {
        this.tileX = tileX;
        this.tileY = tileY;
    }

    public int tileX() { return tileX; }
    public int tileY() { return tileY; }

    private static int idx(int x, int y) {
        if (x < 0 || x >= SIZE || y < 0 || y >= SIZE) {
            throw new IllegalArgumentException("(x,y) out of range: " + x + "," + y);
        }
        return y * SIZE + x;
    }

    public byte getTerrain(int x, int y) { return terrain[idx(x, y)]; }

    /**
     * Returns a read-only view of the raw 128*128 terrain byte array (row-major, index = y*128+x).
     * Used by {@code MultiTileCloudProvider} to bulk-copy snapshot bytes into a {@code CloudTile}
     * via reflection on {@code Tile.terrain}, bypassing the 16K-cell setTerrain loop.
     *
     * <p>Caller MUST NOT mutate the returned array.
     */
    public byte[] terrainArrayUnsafe() { return terrain; }
    public void setTerrain(int x, int y, byte value, Common.Hlc hlc) {
        int i = idx(x, y); terrain[i] = value; terrainHlc[i] = hlc;
    }

    public int  getHeight(int x, int y) { return height[idx(x, y)]; }
    public void setHeight(int x, int y, int value, Common.Hlc hlc) {
        int i = idx(x, y); height[i] = value; heightHlc[i] = hlc;
    }

    public short getWaterLevel(int x, int y) { return waterLevel[idx(x, y)]; }
    public void setWaterLevel(int x, int y, short value, Common.Hlc hlc) {
        int i = idx(x, y); waterLevel[i] = value; waterHlc[i] = hlc;
    }

    public byte getLayerCell(int layerId, int x, int y) {
        byte[] data = layerData.get(layerId);
        return data == null ? 0 : data[idx(x, y)];
    }

    public void setLayerCell(int layerId, int x, int y, byte value, Common.Hlc hlc) {
        byte[] data = layerData.computeIfAbsent(layerId, k -> new byte[CELLS]);
        Common.Hlc[] hlcs = layerHlc.computeIfAbsent(layerId, k -> new Common.Hlc[CELLS]);
        int i = idx(x, y); data[i] = value; hlcs[i] = hlc;
    }

    public Set<Integer> activeLayers() {
        Set<Integer> out = new HashSet<>();
        for (var e : layerTags.entrySet()) if (!e.getValue().isEmpty()) out.add(e.getKey());
        return out;
    }
    public void addLayer(int layerId, UUID tag) {
        layerTags.computeIfAbsent(layerId, k -> new HashSet<>()).add(tag);
    }
    public void removeLayerTags(int layerId, Set<UUID> tags) {
        Set<UUID> existing = layerTags.get(layerId);
        if (existing != null) existing.removeAll(tags);
    }
    public Set<UUID> layerTagsFor(int layerId) {
        return layerTags.getOrDefault(layerId, Set.of());
    }

    public Common.Hlc getCellHlc(Field field, int layerId, int x, int y) {
        int i = idx(x, y);
        return switch (field) {
            case TERRAIN -> terrainHlc[i];
            case HEIGHT  -> heightHlc[i];
            case WATER   -> waterHlc[i];
            case LAYER, BIT_LAYER -> {
                Common.Hlc[] hlcs = layerHlc.get(layerId);
                yield hlcs == null ? null : hlcs[i];
            }
        };
    }

    public byte[] getMetadataBlob() { return metadataBlob.clone(); }
    public Common.Hlc getMetadataHlc() { return metadataHlc; }
    public void setMetadata(byte[] blob, Common.Hlc hlc) {
        this.metadataBlob = blob.clone(); this.metadataHlc = hlc;
    }
}
