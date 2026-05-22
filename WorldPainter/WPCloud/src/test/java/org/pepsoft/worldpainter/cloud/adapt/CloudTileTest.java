package org.pepsoft.worldpainter.cloud.adapt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.layers.Frost;
import org.pepsoft.worldpainter.layers.Layer;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CloudTileTest {

    private RecordingMutationSink sink;
    private CloudTile tile;

    @BeforeEach
    void setUp() {
        sink = new RecordingMutationSink();
        tile = new CloudTile(5, 7, 0, 256, sink);
    }

    @Test
    void setTerrain_emits_op_and_updates_state() {
        tile.setTerrain(10, 20, Terrain.GRASS);
        assertThat(tile.getTerrain(10, 20)).isEqualTo(Terrain.GRASS);
        assertThat(sink.terrain).hasSize(1);
        RecordingMutationSink.TerrainEvent e = sink.terrain.get(0);
        assertThat(e.tileX).isEqualTo(5);
        assertThat(e.tileY).isEqualTo(7);
        assertThat(e.x).isEqualTo(10);
        assertThat(e.y).isEqualTo(20);
        assertThat(e.terrain).isEqualTo(Terrain.GRASS);
    }

    @Test
    void setTerrain_during_remote_apply_does_not_emit() {
        RemoteOpContext.runApplyingRemote(() -> tile.setTerrain(10, 20, Terrain.SAND));
        assertThat(tile.getTerrain(10, 20)).isEqualTo(Terrain.SAND);
        assertThat(sink.terrain).isEmpty();
    }

    @Test
    void setWaterLevel_emits_op() {
        tile.setWaterLevel(15, 25, 64);
        assertThat(sink.water).hasSize(1);
        assertThat(sink.water.get(0).x).isEqualTo(15);
        assertThat(sink.water.get(0).waterLevel).isEqualTo(64);
    }

    @Test
    void setBitLayerValue_emits_op() {
        tile.setBitLayerValue(Frost.INSTANCE, 10, 10, true);
        assertThat(sink.bitLayer).hasSize(1);
        assertThat(sink.bitLayer.get(0).layer).isSameAs(Frost.INSTANCE);
        assertThat(sink.bitLayer.get(0).value).isTrue();
    }

    /** Records every callback for assertion. */
    private static class RecordingMutationSink implements MutationSink {
        static class TerrainEvent {
            final int tileX, tileY, x, y; final Terrain terrain;
            TerrainEvent(int tx, int ty, int x, int y, Terrain t) {
                this.tileX = tx; this.tileY = ty; this.x = x; this.y = y; this.terrain = t;
            }
        }
        static class WaterEvent {
            final int tileX, tileY, x, y, waterLevel;
            WaterEvent(int tx, int ty, int x, int y, int w) {
                this.tileX = tx; this.tileY = ty; this.x = x; this.y = y; this.waterLevel = w;
            }
        }
        static class BitLayerEvent {
            final int tileX, tileY, x, y; final Layer layer; final boolean value;
            BitLayerEvent(int tx, int ty, Layer l, int x, int y, boolean v) {
                this.tileX = tx; this.tileY = ty; this.layer = l; this.x = x; this.y = y; this.value = v;
            }
        }

        final List<TerrainEvent> terrain = new ArrayList<>();
        final List<WaterEvent> water = new ArrayList<>();
        final List<BitLayerEvent> bitLayer = new ArrayList<>();

        @Override public void onTerrain(int tx, int ty, int x, int y, Terrain t) { terrain.add(new TerrainEvent(tx, ty, x, y, t)); }
        @Override public void onHeight(int tx, int ty, int x, int y, float h) {}
        @Override public void onRawHeight(int tx, int ty, int x, int y, int h) {}
        @Override public void onWaterLevel(int tx, int ty, int x, int y, int w) { water.add(new WaterEvent(tx, ty, x, y, w)); }
        @Override public void onBitLayer(int tx, int ty, Layer l, int x, int y, boolean v) { bitLayer.add(new BitLayerEvent(tx, ty, l, x, y, v)); }
        @Override public void onLayer(int tx, int ty, Layer l, int x, int y, int v) {}
    }
}
