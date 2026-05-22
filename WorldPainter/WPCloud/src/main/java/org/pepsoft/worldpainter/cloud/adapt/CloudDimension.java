package org.pepsoft.worldpainter.cloud.adapt;

import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.TileFactory;
import org.pepsoft.worldpainter.World2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;
import java.awt.Point;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cloud-backed {@link Dimension}. Overrides {@link #getTile(int, int)} and
 * {@link #getTileForEditing(int, int)} to load cloud tiles via the injected
 * {@link CloudTileLoader}, gated by a {@link #knownOccupied} set that lists the tile
 * coordinates known to have backend content.
 *
 * <p><strong>{@link #getTile(int, int)} is asynchronous AND gated</strong> — for a tile
 * coordinate NOT in {@code knownOccupied}, returns {@code null} immediately with NO network
 * activity. The view sees "no tile here" and shows the background color. This avoids the
 * unbounded-world problem where the renderer asks for hundreds of empty-tile coordinates per
 * viewport repaint.
 *
 * <p>For coordinates that ARE in {@code knownOccupied} but not yet cached, schedules a
 * background fetch; when it completes the tile is installed on the EDT via {@code addTile},
 * firing {@code tilesAdded} which triggers a view repaint.
 *
 * <p><strong>{@link #getTileForEditing(int, int)} is synchronous and NOT gated</strong> —
 * brushes can create new tiles in previously-empty coordinates. The new coordinate is added
 * to {@code knownOccupied} so subsequent {@code getTile} calls find it.
 */
public final class CloudDimension extends Dimension {

    private static final Logger LOG = LoggerFactory.getLogger(CloudDimension.class);

    private final transient CloudTileLoader loader;
    private final transient ExecutorService loadExecutor = Executors.newFixedThreadPool(32, r -> {
        Thread t = new Thread(r, "wpcloud-tile-loader");
        t.setDaemon(true);
        return t;
    });
    private final transient Set<Long> pendingLoads = ConcurrentHashMap.newKeySet();
    private final transient Set<Long> knownOccupied = ConcurrentHashMap.newKeySet();

    public CloudDimension(World2 world, String name, long minecraftSeed, TileFactory tileFactory,
                          Anchor anchor, CloudTileLoader loader) {
        super(world, name, minecraftSeed, tileFactory, anchor);
        this.loader = loader;
    }

    /**
     * Mark a tile coordinate as known to have content. Called by {@link CloudStorageBackend}
     * after the initial {@code listTiles} preload, and again whenever a remote op arrives for
     * a previously-unknown coord.
     */
    public void markOccupied(int tileX, int tileY) {
        knownOccupied.add(packKey(tileX, tileY));
    }

    @Override
    public Tile getTile(int x, int y) {
        Tile cached = super.getTile(x, y);
        if (cached != null) {
            return cached;
        }
        long key = packKey(x, y);
        // Gate: only fetch tiles known to have content. Empty coords return null
        // immediately so the view shows background without any network round-trip.
        if (!knownOccupied.contains(key)) {
            return null;
        }
        if (pendingLoads.add(key)) {
            loadExecutor.execute(() -> {
                try {
                    Tile loaded = loader.load(x, y);
                    if (loaded != null) {
                        SwingUtilities.invokeLater(() -> {
                            try {
                                if (super.getTile(x, y) == null) {
                                    addTile(loaded);
                                }
                            } finally {
                                pendingLoads.remove(key);
                            }
                        });
                    } else {
                        pendingLoads.remove(key);
                    }
                } catch (Exception e) {
                    LOG.warn("Failed to load cloud tile ({},{}): {}", x, y, e.getMessage());
                    pendingLoads.remove(key);
                }
            });
        }
        return null;
    }

    @Override
    public Tile getTile(Point coords) {
        return getTile(coords.x, coords.y);
    }

    @Override
    public Tile getTileForEditing(int x, int y) {
        Tile cached = super.getTile(x, y);
        if (cached != null) {
            return cached;
        }
        // Fast path: never blocks on the network. Returns an empty CloudTile instantly and
        // subscribes to the backend in the background. Brushes can paint on it immediately;
        // any pre-existing backend content arrives later via the listener pipeline (HLC LWW
        // preserves the brush's fresh writes).
        Tile loaded = loader.loadFast(x, y);
        if (loaded != null) {
            knownOccupied.add(packKey(x, y));  // future getTile will return this tile
            if (SwingUtilities.isEventDispatchThread()) {
                if (super.getTile(x, y) == null) {
                    addTile(loaded);
                }
            } else {
                try {
                    SwingUtilities.invokeAndWait(() -> {
                        if (super.getTile(x, y) == null) {
                            addTile(loaded);
                        }
                    });
                } catch (Exception e) {
                    LOG.warn("getTileForEditing EDT marshal failed: {}", e.getMessage());
                }
            }
        }
        return loaded;
    }

    @Override
    public Tile getTileForEditing(Point coords) {
        return getTileForEditing(coords.x, coords.y);
    }

    private static long packKey(int x, int y) {
        return (((long) x) << 32) | (y & 0xFFFFFFFFL);
    }
}
