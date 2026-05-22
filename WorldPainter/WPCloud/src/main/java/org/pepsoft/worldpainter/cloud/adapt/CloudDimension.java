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
 * {@link CloudTileLoader}.
 *
 * <p><strong>{@link #getTile(int, int)} is asynchronous</strong> — it returns {@code null}
 * immediately if the tile isn't yet cached, and kicks off a background fetch. When the fetch
 * completes the tile is installed on the EDT via {@code addTile}, which fires the existing
 * {@code Dimension} tilesAdded listener event so the view repaints. This keeps the view
 * panel responsive while panning across uncached tiles.
 *
 * <p><strong>{@link #getTileForEditing(int, int)} is synchronous</strong> — brushes need the
 * tile right now to mutate it, so we block until the fetch completes. UI callers wrap brush
 * operations on background threads already, so this doesn't freeze the EDT in normal use.
 */
public final class CloudDimension extends Dimension {

    private static final Logger LOG = LoggerFactory.getLogger(CloudDimension.class);

    private final transient CloudTileLoader loader;
    private final transient ExecutorService loadExecutor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "wpcloud-tile-loader");
        t.setDaemon(true);
        return t;
    });
    private final transient Set<Long> pendingLoads = ConcurrentHashMap.newKeySet();

    public CloudDimension(World2 world, String name, long minecraftSeed, TileFactory tileFactory,
                          Anchor anchor, CloudTileLoader loader) {
        super(world, name, minecraftSeed, tileFactory, anchor);
        this.loader = loader;
    }

    @Override
    public Tile getTile(int x, int y) {
        Tile cached = super.getTile(x, y);
        if (cached != null) {
            return cached;
        }
        // Non-blocking: kick off async load if not already in flight. View will refresh
        // automatically when addTile fires tilesAdded.
        long key = packKey(x, y);
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
        // Synchronous load for brushes: they need the tile to mutate.
        Tile loaded = loader.load(x, y);
        if (loaded != null) {
            // Install on EDT if we're not already there; brushes typically run off-EDT.
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
