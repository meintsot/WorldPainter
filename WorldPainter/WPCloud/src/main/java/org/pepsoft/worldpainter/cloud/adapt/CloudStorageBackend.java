package org.pepsoft.worldpainter.cloud.adapt;

import org.pepsoft.util.ProgressReceiver;
import org.pepsoft.worldpainter.DefaultPlugin;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Platform;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.TileFactory;
import org.pepsoft.worldpainter.TileFactoryFactory;
import org.pepsoft.worldpainter.World2;
import org.pepsoft.worldpainter.cloud.auth.CloudSession;
import org.pepsoft.worldpainter.cloud.auth.Session;
import org.pepsoft.worldpainter.cloud.tile.CloudTileProvider;
import org.pepsoft.worldpainter.storage.StorageBackend;
import org.pepsoft.worldpainter.storage.WorldRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.IdentityHashMap;
import java.util.Map;

import static org.pepsoft.worldpainter.Constants.DIM_NORMAL;

/**
 * {@link StorageBackend} that opens cloud worlds. Builds the full client-side machinery on
 * {@link #open}: a {@link CloudTileProvider} connection, a {@link MultiTileCloudProvider}
 * adapter, a {@link CloudDimension}, and a {@link CloudWorld2}.
 */
public final class CloudStorageBackend implements StorageBackend {

    private static final Logger LOG = LoggerFactory.getLogger(CloudStorageBackend.class);

    private static final URI DEFAULT_BACKEND_HTTP = URI.create(
            System.getProperty("worldpainter.cloud.backend", "http://localhost:8080"));

    /** Track open providers per world so {@link #close} can release them. */
    private final Map<World2, CloudTileProvider> openProviders = new IdentityHashMap<>();

    @Override
    public WorldRef.Kind kind() { return WorldRef.Kind.CLOUD; }

    @Override
    public World2 open(WorldRef ref, ProgressReceiver progress) throws Exception {
        Session session = CloudSession.getInstance().current()
                .orElseThrow(() -> new IllegalStateException("Not signed in to TalePainter Cloud"));

        URI wsUri = URI.create(
                DEFAULT_BACKEND_HTTP.toString().replaceFirst("^http", "ws") + "/ws");

        // 1. Open the WebSocket and complete the handshake.
        if (progress != null) progress.setMessage("Connecting to cloud…");
        CloudTileProvider provider = new CloudTileProvider(wsUri, session, ref.cloudWorldId());
        provider.connect();
        long deadline = System.currentTimeMillis() + 10_000;
        while (!provider.isOpen()) {
            if (System.currentTimeMillis() > deadline) {
                provider.close();
                throw new RuntimeException("Cloud handshake timed out after 10 s");
            }
            Thread.sleep(50);
        }

        // 2. Build the multi-tile adapter.
        int minHeight = 0;
        int maxHeight = 256;  // Phase 0c-3 assumes standard-height worlds (TD-038)
        MultiTileCloudProvider multi = new MultiTileCloudProvider(provider, minHeight, maxHeight);

        // 2b. List tiles known to have content (used to gate CloudDimension.getTile so we
        // don't fetch the hundreds of empty tiles a viewport repaint would otherwise ask for).
        java.util.List<org.pepsoft.worldpainter.cloud.api.CloudWorldsClient.TileCoord> occupied;
        try {
            if (progress != null) progress.setMessage("Listing tiles…");
            org.pepsoft.worldpainter.cloud.api.CloudWorldsClient worldsClient =
                    new org.pepsoft.worldpainter.cloud.api.CloudWorldsClient(
                            DEFAULT_BACKEND_HTTP, session.token());
            occupied = worldsClient.listTiles(ref.cloudWorldId());
        } catch (Exception e) {
            LOG.warn("listTiles failed (tiles will load on demand): {}", e.getMessage());
            occupied = java.util.List.of();
        }

        // 3. Build CloudWorld2 + CloudDimension BEFORE the preload, so we can mark known-occupied
        // coords on the dimension as each tile loads. CloudDimension.getTile is gated by that set;
        // gating off until preload completes would cause the renderer to see an empty world.
        Platform platform = DefaultPlugin.JAVA_ANVIL;
        CloudWorld2 world = new CloudWorld2(platform, minHeight, maxHeight,
                ref.cloudWorldId(), multi);

        long seed = 0L;
        Terrain defaultTerrain = Terrain.GRASS;
        int defaultHeight = 62;
        int defaultWaterLevel = 62;
        boolean floodWithLava = false;
        boolean beaches = false;
        TileFactory tileFactory = TileFactoryFactory.createFlatTileFactory(
                seed, defaultTerrain, minHeight, maxHeight,
                defaultHeight, defaultWaterLevel, floodWithLava, beaches);

        Dimension.Anchor anchor = new Dimension.Anchor(
                DIM_NORMAL, Dimension.Role.DETAIL, false, 0);
        CloudDimension surface = new CloudDimension(world, "Surface", seed, tileFactory,
                anchor, multi);

        // Mark every occupied coord on the dimension so getTile is allowed to fetch them.
        // (Coords NOT in this set return null instantly without a network round-trip —
        // the unbounded-world fix.)
        for (var coord : occupied) {
            surface.markOccupied(coord.x(), coord.y());
        }

        // 4. Preload occupied tiles in parallel via the multi adapter. The cache lives in
        // MultiTileCloudProvider; subsequent surface.getTile() async-loads hit the cache.
        int total = occupied.size();
        if (total > 0) {
            if (progress != null) {
                try { progress.setMessage("Loading " + total + " tile(s)…"); }
                catch (Exception ignored) {}
            }
            int parallelism = Math.min(32, total);
            java.util.concurrent.ExecutorService pool =
                    java.util.concurrent.Executors.newFixedThreadPool(parallelism, r -> {
                        Thread t = new Thread(r, "cloud-preload");
                        t.setDaemon(true);
                        return t;
                    });
            java.util.concurrent.atomic.AtomicInteger done = new java.util.concurrent.atomic.AtomicInteger();
            for (var coord : occupied) {
                pool.submit(() -> {
                    try { multi.load(coord.x(), coord.y()); }
                    catch (Exception ignored) {}
                    int d = done.incrementAndGet();
                    if (progress != null) {
                        try { progress.setProgress((float) d / total); }
                        catch (org.pepsoft.util.ProgressReceiver.OperationCancelled ignored2) {}
                    }
                });
            }
            pool.shutdown();
            try { pool.awaitTermination(60, java.util.concurrent.TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            LOG.info("Preloaded {} cloud tile(s) for world {}", total, ref.cloudWorldId());
        }

        world.addDimension(surface);

        synchronized (openProviders) { openProviders.put(world, provider); }

        if (progress != null) progress.setProgress(1f);
        LOG.info("Opened cloud world {}", ref.cloudWorldId());
        return world;
    }

    @Override
    public void save(World2 world, WorldRef ref, ProgressReceiver progress) {
        // Cloud worlds are auto-persisted by the op stream; no explicit save is needed.
        // Future: could force a snapshot via a backend API (TD-039).
        LOG.debug("save() called on cloud world; no-op (auto-persisted by op stream)");
    }

    @Override
    public void close(World2 world) {
        CloudTileProvider provider;
        synchronized (openProviders) { provider = openProviders.remove(world); }
        if (provider != null) {
            try { provider.close(); }
            catch (Exception e) { LOG.warn("Failed to close CloudTileProvider", e); }
        }
    }
}
