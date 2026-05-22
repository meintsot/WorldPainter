package org.pepsoft.worldpainter.cloud.crdt;

import com.google.protobuf.ByteString;
import com.talepainter.protocol.common.Common;
import com.talepainter.protocol.ops.Ops;

import java.nio.ByteBuffer;
import java.util.UUID;

public final class OpGenerator {

    private final ClientHlcClock clock;

    public OpGenerator(ClientHlcClock clock) { this.clock = clock; }

    public Ops.Op terrainWrite(int tileX, int tileY, int cellX, int cellY, byte value) {
        return baseCell(tileX, tileY, cellX, cellY, value, Ops.OpType.OP_TYPE_TERRAIN, 0);
    }

    public Ops.Op heightWrite(int tileX, int tileY, int cellX, int cellY, int value) {
        return baseCell(tileX, tileY, cellX, cellY, value, Ops.OpType.OP_TYPE_HEIGHT, 0);
    }

    public Ops.Op waterWrite(int tileX, int tileY, int cellX, int cellY, short value) {
        return baseCell(tileX, tileY, cellX, cellY, value, Ops.OpType.OP_TYPE_WATER, 0);
    }

    public Ops.Op layerWrite(int tileX, int tileY, int layerId, int cellX, int cellY, byte value) {
        return baseCell(tileX, tileY, cellX, cellY, value, Ops.OpType.OP_TYPE_LAYER, layerId);
    }

    public Ops.Op layerAdd(int tileX, int tileY, int layerId) {
        UUID tag = UUID.randomUUID();
        return Ops.Op.newBuilder()
                .setOpId(opId())
                .setHlc(clock.next())
                .setTileId(Common.TileId.newBuilder().setTileX(tileX).setTileY(tileY))
                .setType(Ops.OpType.OP_TYPE_LAYER_ADD)
                .setLayer(Ops.LayerOp.newBuilder().setLayerId(layerId).setUniqueTag(uuidToBs(tag)))
                .build();
    }

    private Ops.Op baseCell(int tileX, int tileY, int cellX, int cellY, int value,
                            Ops.OpType type, int layerId) {
        return Ops.Op.newBuilder()
                .setOpId(opId())
                .setHlc(clock.next())
                .setTileId(Common.TileId.newBuilder().setTileX(tileX).setTileY(tileY))
                .setType(type)
                .setCell(Ops.CellOp.newBuilder()
                        .setX(cellX).setY(cellY).setValue(value).setLayerId(layerId))
                .build();
    }

    private static Common.OpId opId() {
        return Common.OpId.newBuilder().setUuid(uuidToBs(UUID.randomUUID())).build();
    }

    private static ByteString uuidToBs(UUID u) {
        byte[] b = new byte[16];
        ByteBuffer bb = ByteBuffer.wrap(b);
        bb.putLong(u.getMostSignificantBits());
        bb.putLong(u.getLeastSignificantBits());
        return ByteString.copyFrom(b);
    }
}
