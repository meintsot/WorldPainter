package org.pepsoft.worldpainter.cloud.tile;

import com.google.protobuf.ByteString;
import com.talepainter.protocol.common.Common;
import com.talepainter.protocol.messages.Messages;
import com.talepainter.protocol.ops.Ops;
import org.pepsoft.worldpainter.cloud.auth.Session;
import org.pepsoft.worldpainter.cloud.crdt.ClientHlcClock;
import org.pepsoft.worldpainter.cloud.crdt.OpGenerator;
import org.pepsoft.worldpainter.cloud.crdt.OpQueue;
import org.pepsoft.worldpainter.cloud.transport.TyrusWebSocketClient;
import org.pepsoft.worldpainter.cloud.transport.WebSocketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Top-level integration class for the cloud client. Wires WebSocket transport, CRDT op
 * generation, local cache, and op application into a coherent API.
 *
 * <p>Lifecycle: construct, {@link #connect()}, then {@link #getTile(int, int)} and
 * {@link #setTerrain(int, int, int, int, byte)} freely, finally {@link #close()}.
 *
 * <p>Thread safety: {@link #setTerrain} and {@link #getTile} are safe to call from the UI
 * thread. Inbound WS messages are processed on a Tyrus container thread; cache updates from
 * those messages are visible via {@link TileChangeListener}.
 */
public final class CloudTileProvider implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(CloudTileProvider.class);

    private final URI backendUri;
    private final Session session;
    private final UUID worldId;
    private final WebSocketClient ws;
    private final TileCache cache = new TileCache(256);
    private final ClientHlcClock clock;
    private final OpGenerator ops;
    private final OpQueue queue;
    private final OpApplicator applicator = new OpApplicator();

    private final ConcurrentHashMap<Long, CompletableFuture<LocalTile>> pendingSubscribes = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<TileChangeListener> listeners = new CopyOnWriteArrayList<>();
    private volatile boolean authOk = false;
    private volatile boolean worldOpened = false;

    public interface TileChangeListener {
        void onTileChanged(LocalTile tile, int cellX, int cellY);
    }

    public interface RemoteOpsListener {
        /** Called when an inbound TILE_OPS arrives for the given tile coordinates. */
        void onRemoteOps(int tileX, int tileY, java.util.List<com.talepainter.protocol.ops.Ops.Op> ops);
    }

    private final java.util.concurrent.CopyOnWriteArrayList<RemoteOpsListener> remoteOpsListeners
            = new java.util.concurrent.CopyOnWriteArrayList<>();

    public void addRemoteOpsListener(RemoteOpsListener l) { remoteOpsListeners.add(l); }
    public void removeRemoteOpsListener(RemoteOpsListener l) { remoteOpsListeners.remove(l); }

    public CloudTileProvider(URI backendUri, Session session, UUID worldId) {
        this(backendUri, session, worldId, new TyrusWebSocketClient());
    }

    /** Test constructor — inject a fake {@link WebSocketClient}. */
    public CloudTileProvider(URI backendUri, Session session, UUID worldId, WebSocketClient ws) {
        this.backendUri = backendUri;
        this.session = session;
        this.worldId = worldId;
        this.ws = ws;
        int nodeId = Math.abs(session.token().hashCode()) % 65535;
        this.clock = new ClientHlcClock(Clock.systemUTC(), nodeId);
        this.ops = new OpGenerator(clock);
        this.queue = new OpQueue(50, this::sendBatch);
    }

    public void connect() {
        ws.connect(backendUri, this::onServerMessage);
        ws.send(Messages.ClientMessage.newBuilder()
                .setAuth(Messages.Auth.newBuilder().setJwt(session.token()))
                .build());
        queue.start();
    }

    public boolean isOpen() { return authOk && worldOpened && ws.isOpen(); }

    public LocalTile getTile(int tileX, int tileY) {
        LocalTile cached = cache.get(worldId, tileX, tileY);
        if (cached != null) return cached;

        long key = (((long) tileX) << 32) | (tileY & 0xFFFFFFFFL);
        CompletableFuture<LocalTile> future = pendingSubscribes.computeIfAbsent(key, k -> new CompletableFuture<>());
        ws.send(Messages.ClientMessage.newBuilder()
                .setSubscribe(Messages.Subscribe.newBuilder()
                        .addTileIds(Common.TileId.newBuilder().setTileX(tileX).setTileY(tileY))
                        .setLod(0))
                .build());
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("getTile timed out for (" + tileX + "," + tileY + ")", e);
        }
    }

    public LocalTile getCachedTile(int tileX, int tileY) {
        return cache.get(worldId, tileX, tileY);
    }

    public void setTerrain(int tileX, int tileY, int cellX, int cellY, byte value) {
        Ops.Op op = ops.terrainWrite(tileX, tileY, cellX, cellY, value);
        LocalTile tile = cache.get(worldId, tileX, tileY);
        if (tile == null) {
            throw new IllegalStateException("Tile (" + tileX + "," + tileY + ") not in cache; call getTile first");
        }
        applicator.apply(tile, op);
        cache.markDirty(worldId, tileX, tileY);
        queue.enqueue(op);
        notifyChanged(tile, cellX, cellY);
    }

    public void addListener(TileChangeListener l) { listeners.add(l); }
    public void removeListener(TileChangeListener l) { listeners.remove(l); }

    /** Test helper — pre-populate the cache without subscribing. */
    public void injectTileForTest(LocalTile tile) {
        cache.put(worldId, tile);
    }

    /** Enqueue an outbound op (used by external mutation pipelines). */
    public void submitOp(Ops.Op op) {
        queue.enqueue(op);
    }

    /** Expose the HLC clock for callers that generate their own ops. */
    public ClientHlcClock clock() { return clock; }

    @Override
    public void close() {
        queue.flushNow();
        queue.stop();
        ws.close();
    }

    // ─── Inbound message handling ──────────────────────────────────

    private void onServerMessage(Messages.ServerMessage msg) {
        try {
            switch (msg.getBodyCase()) {
                case AUTH_OK -> {
                    authOk = true;
                    clock.observe(msg.getAuthOk().getServerHlc());
                    ws.send(Messages.ClientMessage.newBuilder()
                            .setOpenWorld(Messages.OpenWorld.newBuilder()
                                    .setWorldId(Common.WorldId.newBuilder()
                                            .setUuid(uuidToBs(worldId))))
                            .build());
                }
                case WORLD_OPENED -> worldOpened = true;
                case TILE_SNAPSHOT -> handleSnapshot(msg.getTileSnapshot());
                case TILE_OPS      -> handleTileOps(msg.getTileOps());
                case OPS_ACK       -> handleAck(msg.getOpsAck());
                case ERROR         -> LOG.error("Server error: {} — {}",
                        msg.getError().getCode(), msg.getError().getMessage());
                default -> LOG.debug("Ignoring server message: {}", msg.getBodyCase());
            }
        } catch (Exception e) {
            LOG.error("onServerMessage handling failed", e);
        }
    }

    private void handleSnapshot(Messages.TileSnapshot snap) {
        int tileX = snap.getTileId().getTileX();
        int tileY = snap.getTileId().getTileY();
        LocalTile tile = TileSnapshotDecoder.decode(tileX, tileY, snap.getBlob().toByteArray());
        cache.put(worldId, tile);
        long key = (((long) tileX) << 32) | (tileY & 0xFFFFFFFFL);
        CompletableFuture<LocalTile> pending = pendingSubscribes.remove(key);
        if (pending != null) pending.complete(tile);
    }

    private void handleTileOps(Messages.TileOps tileOps) {
        int tileX = tileOps.getTileId().getTileX();
        int tileY = tileOps.getTileId().getTileY();
        LocalTile tile = cache.get(worldId, tileX, tileY);
        if (tile == null) {
            LOG.debug("Discarding TILE_OPS for un-cached tile ({},{})", tileX, tileY);
            return;
        }
        for (Ops.Op op : tileOps.getOpsList()) {
            applicator.apply(tile, op);
            clock.observe(op.getHlc());
            if (op.hasCell()) notifyChanged(tile, op.getCell().getX(), op.getCell().getY());
        }
        // Fan out to external listeners (used by MultiTileCloudProvider to route to CloudTile)
        for (RemoteOpsListener l : remoteOpsListeners) {
            try { l.onRemoteOps(tileX, tileY, tileOps.getOpsList()); }
            catch (Exception e) { LOG.warn("RemoteOpsListener threw", e); }
        }
    }

    private void handleAck(Messages.OpsAck ack) {
        LOG.debug("OPS_ACK with {} op IDs", ack.getOpIdsCount());
    }

    private void sendBatch(Ops.OpBatch batch) {
        if (!ws.isOpen()) {
            LOG.warn("Discarding batch of {} ops: WS closed", batch.getOpsCount());
            return;
        }
        ws.send(Messages.ClientMessage.newBuilder()
                .setSubmitOps(Messages.SubmitOps.newBuilder().setBatch(batch))
                .build());
    }

    private void notifyChanged(LocalTile tile, int cellX, int cellY) {
        for (TileChangeListener l : listeners) {
            try { l.onTileChanged(tile, cellX, cellY); }
            catch (Exception e) { LOG.warn("Listener threw", e); }
        }
    }

    private static ByteString uuidToBs(UUID u) {
        byte[] b = new byte[16];
        ByteBuffer bb = ByteBuffer.wrap(b);
        bb.putLong(u.getMostSignificantBits()); bb.putLong(u.getLeastSignificantBits());
        return ByteString.copyFrom(b);
    }
}
