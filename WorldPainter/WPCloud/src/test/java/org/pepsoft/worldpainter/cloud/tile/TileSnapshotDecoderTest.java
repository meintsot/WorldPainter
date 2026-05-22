package org.pepsoft.worldpainter.cloud.tile;

import com.google.protobuf.ByteString;
import com.talepainter.protocol.common.Common;
import com.talepainter.protocol.tile.TileSnapshotProto;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.assertj.core.api.Assertions.assertThat;

class TileSnapshotDecoderTest {

    private static final int CELLS = 128 * 128;

    @Test
    void empty_snapshot_decodes_to_empty_tile() {
        TileSnapshotProto.TileSnapshot snap = TileSnapshotProto.TileSnapshot.newBuilder()
                .setSnapshotHlc(Common.Hlc.newBuilder().setWallTimeMs(1000).setCounter(0).setNodeId(1))
                .build();
        LocalTile t = TileSnapshotDecoder.decode(5, 7, snap.toByteArray());
        assertThat(t.tileX()).isEqualTo(5);
        assertThat(t.tileY()).isEqualTo(7);
        assertThat(t.getTerrain(0, 0)).isEqualTo((byte) 0);
        assertThat(t.activeLayers()).isEmpty();
    }

    @Test
    void terrain_height_water_decode_correctly() {
        byte[] terrain = new byte[CELLS];
        terrain[22 * 128 + 15] = 3;          // (x=15, y=22) -> idx = y*128+x

        byte[] heightBytes = new byte[CELLS * 4];
        ByteBuffer.wrap(heightBytes).order(ByteOrder.LITTLE_ENDIAN).putInt((22 * 128 + 15) * 4, 250);

        byte[] waterBytes = new byte[CELLS * 2];
        ByteBuffer.wrap(waterBytes).order(ByteOrder.LITTLE_ENDIAN).putShort((22 * 128 + 15) * 2, (short) 64);

        TileSnapshotProto.TileSnapshot snap = TileSnapshotProto.TileSnapshot.newBuilder()
                .setSnapshotHlc(Common.Hlc.newBuilder().setWallTimeMs(1).setCounter(0).setNodeId(1))
                .setTerrain(ByteString.copyFrom(terrain))
                .setHeight(ByteString.copyFrom(heightBytes))
                .setWaterLevel(ByteString.copyFrom(waterBytes))
                .build();

        LocalTile t = TileSnapshotDecoder.decode(0, 0, snap.toByteArray());
        assertThat(t.getTerrain(15, 22)).isEqualTo((byte) 3);
        assertThat(t.getHeight(15, 22)).isEqualTo(250);
        assertThat(t.getWaterLevel(15, 22)).isEqualTo((short) 64);
    }

    @Test
    void layer_presence_with_tags_decoded() {
        byte[] tagBytes = new byte[16];
        new java.util.Random(99).nextBytes(tagBytes);
        TileSnapshotProto.TileSnapshot snap = TileSnapshotProto.TileSnapshot.newBuilder()
                .setSnapshotHlc(Common.Hlc.newBuilder().setWallTimeMs(1).setCounter(0).setNodeId(1))
                .addActiveLayers(TileSnapshotProto.LayerPresence.newBuilder()
                        .setLayerId(7).addTags(ByteString.copyFrom(tagBytes)))
                .build();
        LocalTile t = TileSnapshotDecoder.decode(0, 0, snap.toByteArray());
        assertThat(t.activeLayers()).contains(7);
        assertThat(t.layerTagsFor(7)).hasSize(1);
    }

    @Test
    void snapshot_hlc_is_readable_independently() {
        TileSnapshotProto.TileSnapshot snap = TileSnapshotProto.TileSnapshot.newBuilder()
                .setSnapshotHlc(Common.Hlc.newBuilder().setWallTimeMs(5000).setCounter(17).setNodeId(3))
                .build();
        Common.Hlc decoded = TileSnapshotDecoder.readSnapshotHlc(snap.toByteArray());
        assertThat(decoded.getWallTimeMs()).isEqualTo(5000);
        assertThat(decoded.getCounter()).isEqualTo(17);
        assertThat(decoded.getNodeId()).isEqualTo(3);
    }
}
