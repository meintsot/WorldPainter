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
        int loadFastCount = 0;
        @Override
        public CloudTile load(int tileX, int tileY) {
            loadCount++;
            return new CloudTile(tileX, tileY, 0, 256, noopSink);
        }
        @Override
        public CloudTile loadFast(int tileX, int tileY) {
            loadFastCount++;
            return new CloudTile(tileX, tileY, 0, 256, noopSink);
        }
    }

    @Test
    void getTile_returns_null_first_then_loads_async() throws Exception {
        StubLoader loader = new StubLoader();
        CloudDimension dim = newCloudDimension(loader);

        // getTile is gated: tiles not in the knownOccupied set return null instantly with
        // no async load. Mark (5,7) as occupied so the gate lets the fetch through.
        dim.markOccupied(5, 7);

        // First access kicks off an async load; returns null immediately to keep the EDT
        // responsive while the view paints uncached tiles.
        org.pepsoft.worldpainter.Tile t1 = dim.getTile(5, 7);
        assertThat(t1).isNull();

        // Wait for the async load to complete and the EDT-marshalled addTile to land.
        long deadline = System.currentTimeMillis() + 2000;
        org.pepsoft.worldpainter.Tile t2;
        while ((t2 = dim.getTile(5, 7)) == null && System.currentTimeMillis() < deadline) {
            javax.swing.SwingUtilities.invokeAndWait(() -> {});  // drain EDT queue
            Thread.sleep(20);
        }
        assertThat(t2).isInstanceOf(CloudTile.class);
        assertThat(loader.loadCount).isEqualTo(1);

        // Subsequent access returns the cached tile without re-loading.
        org.pepsoft.worldpainter.Tile t3 = dim.getTile(5, 7);
        assertThat(t3).isSameAs(t2);
        assertThat(loader.loadCount).isEqualTo(1);
    }

    @Test
    void getTileForEditing_returns_writable_cloud_tile_instantly() {
        StubLoader loader = new StubLoader();
        CloudDimension dim = newCloudDimension(loader);
        // getTileForEditing uses the fast path: never blocks on network. Returns an empty
        // CloudTile immediately so brushes don't freeze the EDT on large strokes.
        org.pepsoft.worldpainter.Tile t = dim.getTileForEditing(0, 0);
        assertThat(t).isInstanceOf(CloudTile.class);
        assertThat(loader.loadFastCount).isEqualTo(1);
        assertThat(loader.loadCount).isZero();  // no blocking load
    }

    @Test
    void getTile_skips_load_for_coords_not_in_known_occupied_set() throws Exception {
        StubLoader loader = new StubLoader();
        CloudDimension dim = newCloudDimension(loader);

        // No markOccupied call — (99, 99) is unknown. getTile must return null instantly
        // and trigger NO background load (avoids the unbounded-world fetch storm).
        org.pepsoft.worldpainter.Tile t = dim.getTile(99, 99);
        assertThat(t).isNull();

        // Give any background work a chance to (incorrectly) fire.
        Thread.sleep(150);
        assertThat(loader.loadCount).isZero();
    }

    @Test
    void getTileForEditing_marks_coord_occupied_for_future_getTile() throws Exception {
        StubLoader loader = new StubLoader();
        CloudDimension dim = newCloudDimension(loader);

        // Brush touches (3, 4) for the first time: fast-path creates an empty tile and marks
        // the coord occupied. After that, getTile is allowed to find it via super's cache.
        dim.getTileForEditing(3, 4);
        assertThat(loader.loadFastCount).isEqualTo(1);

        // Now getTile sees the tile is already cached and returns it directly.
        org.pepsoft.worldpainter.Tile t = dim.getTile(3, 4);
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
