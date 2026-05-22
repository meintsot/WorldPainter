package org.pepsoft.worldpainter.cloud.adapt;

import org.junit.jupiter.api.Test;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.TileFactory;
import org.pepsoft.worldpainter.TileFactoryFactory;
import org.pepsoft.worldpainter.World2;
import org.pepsoft.worldpainter.layers.Layer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.pepsoft.worldpainter.Constants.DIM_NORMAL;
import static org.pepsoft.worldpainter.DefaultPlugin.JAVA_ANVIL_1_18;

class CloudDimensionTest {

    private static class StubLoader implements CloudTileLoader {
        private final MutationSink noopSink = new MutationSink() {
            @Override public void onTerrain(int tx, int ty, int x, int y, Terrain t) {}
            @Override public void onHeight(int tx, int ty, int x, int y, float h) {}
            @Override public void onRawHeight(int tx, int ty, int x, int y, int h) {}
            @Override public void onWaterLevel(int tx, int ty, int x, int y, int w) {}
            @Override public void onBitLayer(int tx, int ty, Layer l, int x, int y, boolean v) {}
            @Override public void onLayer(int tx, int ty, Layer l, int x, int y, int v) {}
        };
        int loadCount = 0;
        @Override
        public CloudTile load(int tileX, int tileY) {
            loadCount++;
            return new CloudTile(tileX, tileY, 0, 256, noopSink);
        }
    }

    @Test
    void getTile_fetches_lazily_and_caches() {
        StubLoader loader = new StubLoader();
        CloudDimension dim = newCloudDimension(loader);

        org.pepsoft.worldpainter.Tile t1 = dim.getTile(5, 7);
        assertThat(t1).isInstanceOf(CloudTile.class);
        assertThat(loader.loadCount).isEqualTo(1);

        org.pepsoft.worldpainter.Tile t2 = dim.getTile(5, 7);
        assertThat(t2).isSameAs(t1);
        assertThat(loader.loadCount).isEqualTo(1);  // not loaded again
    }

    @Test
    void getTileForEditing_returns_writable_cloud_tile() {
        StubLoader loader = new StubLoader();
        CloudDimension dim = newCloudDimension(loader);
        org.pepsoft.worldpainter.Tile t = dim.getTileForEditing(0, 0);
        assertThat(t).isInstanceOf(CloudTile.class);
    }

    private static CloudDimension newCloudDimension(CloudTileLoader loader) {
        World2 world = new World2(JAVA_ANVIL_1_18, 0, 256);
        world.setName("test-world");
        long seed = 0L;
        TileFactory factory = TileFactoryFactory.createFlatTileFactory(
                seed, Terrain.GRASS, 0, 256, 62, 62, false, false);
        Dimension.Anchor anchor = new Dimension.Anchor(DIM_NORMAL, Dimension.Role.DETAIL, false, 0);
        return new CloudDimension(world, "Surface", seed, factory, anchor, loader);
    }
}
