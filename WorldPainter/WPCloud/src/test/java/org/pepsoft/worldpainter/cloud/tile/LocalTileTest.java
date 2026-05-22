package org.pepsoft.worldpainter.cloud.tile;

import com.talepainter.protocol.common.Common;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocalTileTest {

    @Test
    void fresh_tile_has_zero_terrain() {
        LocalTile t = new LocalTile(5, 7);
        assertThat(t.tileX()).isEqualTo(5);
        assertThat(t.tileY()).isEqualTo(7);
        assertThat(t.getTerrain(0, 0)).isEqualTo((byte) 0);
        assertThat(t.getCellHlc(LocalTile.Field.TERRAIN, 0, 0, 0)).isNull();
    }

    @Test
    void set_terrain_records_value_and_hlc() {
        LocalTile t = new LocalTile(0, 0);
        Common.Hlc h = Common.Hlc.newBuilder().setWallTimeMs(1000).setCounter(0).setNodeId(1).build();
        t.setTerrain(15, 22, (byte) 3, h);
        assertThat(t.getTerrain(15, 22)).isEqualTo((byte) 3);
        assertThat(t.getCellHlc(LocalTile.Field.TERRAIN, 0, 15, 22)).isEqualTo(h);
    }

    @Test
    void rejects_out_of_range_coordinates() {
        LocalTile t = new LocalTile(0, 0);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> t.getTerrain(-1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> t.getTerrain(128, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
