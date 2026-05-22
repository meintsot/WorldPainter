package org.pepsoft.worldpainter.cloud.adapt;

import com.google.protobuf.ByteString;
import com.talepainter.protocol.common.Common;
import com.talepainter.protocol.ops.Ops;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pepsoft.worldpainter.Terrain;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CloudTileRemoteOpTest {

    private RecordingSink sink;
    private CloudTile tile;

    @BeforeEach
    void setUp() {
        sink = new RecordingSink();
        tile = new CloudTile(0, 0, 0, 256, sink);
    }

    @Test
    void applies_terrain_cell_op_without_emitting() {
        Ops.Op op = Ops.Op.newBuilder()
                .setOpId(opId())
                .setHlc(hlc(1000, 0, 1))
                .setTileId(Common.TileId.newBuilder().setTileX(0).setTileY(0))
                .setType(Ops.OpType.OP_TYPE_TERRAIN)
                .setCell(Ops.CellOp.newBuilder()
                        .setX(10).setY(20).setValue(TerrainRegistry.toByte(Terrain.GRASS)))
                .build();

        tile.applyRemoteOp(op);

        assertThat(tile.getTerrain(10, 20)).isEqualTo(Terrain.GRASS);
        assertThat(sink.terrainCount).isZero();
    }

    @Test
    void applies_water_cell_op() {
        Ops.Op op = Ops.Op.newBuilder()
                .setOpId(opId()).setHlc(hlc(1000, 0, 1))
                .setTileId(Common.TileId.newBuilder().setTileX(0).setTileY(0))
                .setType(Ops.OpType.OP_TYPE_WATER)
                .setCell(Ops.CellOp.newBuilder().setX(5).setY(5).setValue(64))
                .build();
        tile.applyRemoteOp(op);
        assertThat(tile.getWaterLevel(5, 5)).isEqualTo(64);
        assertThat(sink.waterCount).isZero();
    }

    @Test
    void applies_rect_op() {
        Ops.Op op = Ops.Op.newBuilder()
                .setOpId(opId()).setHlc(hlc(1000, 0, 1))
                .setTileId(Common.TileId.newBuilder().setTileX(0).setTileY(0))
                .setType(Ops.OpType.OP_TYPE_TERRAIN)
                .setRect(Ops.RectOp.newBuilder()
                        .setX1(10).setY1(10).setX2(12).setY2(12)
                        .setValue(TerrainRegistry.toByte(Terrain.SAND)))
                .build();
        tile.applyRemoteOp(op);
        for (int x = 10; x <= 12; x++) for (int y = 10; y <= 12; y++) {
            assertThat(tile.getTerrain(x, y)).isEqualTo(Terrain.SAND);
        }
        // (9, 10) is outside the rect — its initial terrain should remain unchanged
        assertThat(tile.getTerrain(9, 10)).isNotEqualTo(Terrain.SAND);
        assertThat(sink.terrainCount).isZero();
    }

    private static Common.Hlc hlc(long wall, int ctr, int node) {
        return Common.Hlc.newBuilder().setWallTimeMs(wall).setCounter(ctr).setNodeId(node).build();
    }

    private static Common.OpId opId() {
        byte[] b = new byte[16];
        UUID u = UUID.randomUUID();
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(b);
        bb.putLong(u.getMostSignificantBits()); bb.putLong(u.getLeastSignificantBits());
        return Common.OpId.newBuilder().setUuid(ByteString.copyFrom(b)).build();
    }

    private static class RecordingSink implements MutationSink {
        int terrainCount = 0, waterCount = 0;
        @Override public void onTerrain(int tx, int ty, int x, int y, Terrain t) { terrainCount++; }
        @Override public void onHeight(int tx, int ty, int x, int y, float h) {}
        @Override public void onRawHeight(int tx, int ty, int x, int y, int h) {}
        @Override public void onWaterLevel(int tx, int ty, int x, int y, int w) { waterCount++; }
        @Override public void onBitLayer(int tx, int ty, org.pepsoft.worldpainter.layers.Layer l, int x, int y, boolean v) {}
        @Override public void onLayer(int tx, int ty, org.pepsoft.worldpainter.layers.Layer l, int x, int y, int v) {}
    }
}
