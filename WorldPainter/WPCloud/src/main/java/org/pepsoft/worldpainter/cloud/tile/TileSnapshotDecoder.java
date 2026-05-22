package org.pepsoft.worldpainter.cloud.tile;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.talepainter.protocol.common.Common;
import com.talepainter.protocol.tile.TileSnapshotProto;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

/**
 * Decodes the {@code bytes blob} payload of a {@code Messages.TileSnapshot}
 * into a {@link LocalTile}. Per-cell HLCs are set to the snapshot's baseline HLC, matching the
 * encoder's contract (server-side {@code TileSnapshotCodec}).
 */
public final class TileSnapshotDecoder {

    private TileSnapshotDecoder() {}

    private static final int SIZE = LocalTile.SIZE;

    public static LocalTile decode(int tileX, int tileY, byte[] blob) {
        TileSnapshotProto.TileSnapshot p;
        try { p = TileSnapshotProto.TileSnapshot.parseFrom(blob); }
        catch (InvalidProtocolBufferException e) { throw new IllegalArgumentException("malformed tile snapshot", e); }

        Common.Hlc snapshotHlc = p.getSnapshotHlc();
        LocalTile tile = new LocalTile(tileX, tileY);

        byte[] terrain = p.getTerrain().toByteArray();
        if (terrain.length > 0) {
            for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
                int i = y * SIZE + x;
                if (terrain[i] != 0) tile.setTerrain(x, y, terrain[i], snapshotHlc);
            }
        }
        if (p.getHeight().size() > 0) {
            ByteBuffer hb = p.getHeight().asReadOnlyByteBuffer().order(ByteOrder.LITTLE_ENDIAN);
            for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
                int v = hb.getInt();
                if (v != 0) tile.setHeight(x, y, v, snapshotHlc);
            }
        }
        if (p.getWaterLevel().size() > 0) {
            ByteBuffer wb = p.getWaterLevel().asReadOnlyByteBuffer().order(ByteOrder.LITTLE_ENDIAN);
            for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
                short v = wb.getShort();
                if (v != 0) tile.setWaterLevel(x, y, v, snapshotHlc);
            }
        }
        for (var e : p.getLayerDataMap().entrySet()) {
            byte[] data = e.getValue().toByteArray();
            int layerId = e.getKey();
            for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
                int i = y * SIZE + x;
                if (data[i] != 0) tile.setLayerCell(layerId, x, y, data[i], snapshotHlc);
            }
        }
        for (TileSnapshotProto.LayerPresence lp : p.getActiveLayersList()) {
            for (ByteString tag : lp.getTagsList()) {
                tile.addLayer(lp.getLayerId(), bsToUuid(tag));
            }
        }
        if (!p.getMetadataBlob().isEmpty()) {
            tile.setMetadata(p.getMetadataBlob().toByteArray(), snapshotHlc);
        }
        return tile;
    }

    public static Common.Hlc readSnapshotHlc(byte[] blob) {
        try {
            return TileSnapshotProto.TileSnapshot.parseFrom(blob).getSnapshotHlc();
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("malformed tile snapshot", e);
        }
    }

    private static UUID bsToUuid(ByteString bs) {
        if (bs.size() != 16) throw new IllegalArgumentException("uuid not 16 bytes");
        ByteBuffer bb = bs.asReadOnlyByteBuffer();
        return new UUID(bb.getLong(), bb.getLong());
    }
}
