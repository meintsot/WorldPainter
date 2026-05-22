package org.pepsoft.worldpainter.cloud.tile;

import com.google.protobuf.ByteString;
import com.talepainter.protocol.common.Common;
import com.talepainter.protocol.ops.Ops;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OpApplicatorTest {

    private final OpApplicator applicator = new OpApplicator();

    private static Common.Hlc hlc(long wall, int ctr, int node) {
        return Common.Hlc.newBuilder().setWallTimeMs(wall).setCounter(ctr).setNodeId(node).build();
    }

    private static Common.OpId opId() {
        byte[] b = new byte[16];
        UUID u = UUID.randomUUID();
        ByteBuffer bb = ByteBuffer.wrap(b);
        bb.putLong(u.getMostSignificantBits()); bb.putLong(u.getLeastSignificantBits());
        return Common.OpId.newBuilder().setUuid(ByteString.copyFrom(b)).build();
    }

    private static Ops.Op terrainCellOp(long wall, int ctr, int node, int x, int y, int value) {
        return Ops.Op.newBuilder()
                .setOpId(opId()).setHlc(hlc(wall, ctr, node))
                .setTileId(Common.TileId.newBuilder().setTileX(0).setTileY(0))
                .setType(Ops.OpType.OP_TYPE_TERRAIN)
                .setCell(Ops.CellOp.newBuilder().setX(x).setY(y).setValue(value))
                .build();
    }

    @Test
    void cell_op_with_higher_hlc_wins() {
        LocalTile t = new LocalTile(0, 0);
        applicator.apply(t, terrainCellOp(1000, 0, 1, 5, 5, 3));
        applicator.apply(t, terrainCellOp(1000, 0, 2, 5, 5, 7));
        assertThat(t.getTerrain(5, 5)).isEqualTo((byte) 7);
    }

    @Test
    void cell_op_with_lower_hlc_loses() {
        LocalTile t = new LocalTile(0, 0);
        applicator.apply(t, terrainCellOp(1000, 0, 2, 5, 5, 7));
        applicator.apply(t, terrainCellOp(1000, 0, 1, 5, 5, 3));
        assertThat(t.getTerrain(5, 5)).isEqualTo((byte) 7);
    }

    @Test
    void rect_op_paints_inclusive_region() {
        LocalTile t = new LocalTile(0, 0);
        Ops.Op rect = Ops.Op.newBuilder()
                .setOpId(opId()).setHlc(hlc(1000, 0, 1))
                .setTileId(Common.TileId.newBuilder().setTileX(0).setTileY(0))
                .setType(Ops.OpType.OP_TYPE_TERRAIN)
                .setRect(Ops.RectOp.newBuilder().setX1(10).setY1(10).setX2(12).setY2(12).setValue(5))
                .build();
        applicator.apply(t, rect);
        for (int x = 10; x <= 12; x++) for (int y = 10; y <= 12; y++) {
            assertThat(t.getTerrain(x, y)).isEqualTo((byte) 5);
        }
        assertThat(t.getTerrain(9, 10)).isEqualTo((byte) 0);
        assertThat(t.getTerrain(13, 12)).isEqualTo((byte) 0);
    }

    @Test
    void layer_add_then_remove_with_matching_tag_clears() {
        LocalTile t = new LocalTile(0, 0);
        byte[] tagBytes = new byte[16];
        new java.util.Random(11).nextBytes(tagBytes);

        Ops.Op add = Ops.Op.newBuilder()
                .setOpId(opId()).setHlc(hlc(1000, 0, 1))
                .setTileId(Common.TileId.newBuilder().setTileX(0).setTileY(0))
                .setType(Ops.OpType.OP_TYPE_LAYER_ADD)
                .setLayer(Ops.LayerOp.newBuilder().setLayerId(7)
                        .setUniqueTag(ByteString.copyFrom(tagBytes)))
                .build();
        Ops.Op remove = Ops.Op.newBuilder()
                .setOpId(opId()).setHlc(hlc(1001, 0, 1))
                .setTileId(Common.TileId.newBuilder().setTileX(0).setTileY(0))
                .setType(Ops.OpType.OP_TYPE_LAYER_REMOVE)
                .setLayer(Ops.LayerOp.newBuilder().setLayerId(7)
                        .setUniqueTag(ByteString.copyFrom(tagBytes)))
                .build();

        applicator.apply(t, add);
        assertThat(t.activeLayers()).contains(7);
        applicator.apply(t, remove);
        assertThat(t.activeLayers()).doesNotContain(7);
    }
}
