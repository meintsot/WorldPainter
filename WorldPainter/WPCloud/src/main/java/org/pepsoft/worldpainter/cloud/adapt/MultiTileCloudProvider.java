package org.pepsoft.worldpainter.cloud.adapt;

import com.google.protobuf.ByteString;
import com.talepainter.protocol.common.Common;
import com.talepainter.protocol.ops.Ops;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.cloud.crdt.ClientHlcClock;
import org.pepsoft.worldpainter.cloud.tile.CloudTileProvider;
import org.pepsoft.worldpainter.layers.Layer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Glue between the per-tile {@link CloudTile} mutation sink + the connection-level
 * {@link CloudTileProvider}. Owns:
 *
 * <ul>
 *   <li>A {@code Map<(tileX,tileY), CloudTile>} of loaded cloud tiles for this world.</li>
 *   <li>A {@link MutationSink} that converts CloudTile setter callbacks into protobuf ops
 *       and enqueues them on the provider's outbound queue.</li>
 *   <li>A {@link CloudTileProvider.RemoteOpsListener} that receives inbound TILE_OPS and
 *       dispatches them to the right {@link CloudTile}'s {@code applyRemoteOp}.</li>
 *   <li>A {@link CloudTileLoader} that lazily fetches tile snapshots via the provider's
 *       SUBSCRIBE/TILE_SNAPSHOT path.</li>
 * </ul>
 */
public final class MultiTileCloudProvider implements CloudTileLoader, MutationSink, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(MultiTileCloudProvider.class);

    private final CloudTileProvider provider;
    private final int defaultMinHeight;
    private final int defaultMaxHeight;
    private final Map<Long, CloudTile> tilesByKey = new ConcurrentHashMap<>();
    private final CloudTileProvider.RemoteOpsListener listener = this::onRemoteOpsHandler;

    public MultiTileCloudProvider(CloudTileProvider provider, int minHeight, int maxHeight) {
        this.provider = provider;
        this.defaultMinHeight = minHeight;
        this.defaultMaxHeight = maxHeight;
        provider.addRemoteOpsListener(listener);
    }

    // ─── CloudTileLoader ────────────────────────────────────────────

    @Override
    public CloudTile load(int tileX, int tileY) {
        long k = key(tileX, tileY);
        return tilesByKey.computeIfAbsent(k, kk -> {
            // Fetch via provider (blocking SUBSCRIBE + TILE_SNAPSHOT)
            var localTile = provider.getTile(tileX, tileY);
            CloudTile cloudTile = new CloudTile(tileX, tileY, defaultMinHeight, defaultMaxHeight, this);
            // Bulk-copy the snapshot terrain bytes directly into the Tile's internal terrain[]
            // via reflection — avoids the 16K per-cell setTerrain() calls (each of which would
            // synchronize, run undo bookkeeping, and fire a Tile.Listener event).
            // Falls back to per-cell loop if reflection fails (e.g., JVM module restrictions).
            byte[] sourceTerrain = localTile.terrainArrayUnsafe();
            if (!bulkCopyTerrain(cloudTile, sourceTerrain)) {
                RemoteOpContext.runApplyingRemote(() -> {
                    for (int y = 0; y < 128; y++) {
                        for (int x = 0; x < 128; x++) {
                            byte terrainByte = sourceTerrain[y * 128 + x];
                            if (terrainByte != 0) {
                                cloudTile.setTerrain(x, y, TerrainRegistry.fromByte(terrainByte));
                            }
                        }
                    }
                });
            }
            // (Heights/water/layers not yet materialized — TD-037.)
            return cloudTile;
        });
    }

    /**
     * Bulk-copy {@code source} into the Tile.terrain field of {@code dest} via reflection.
     * Returns true on success, false on failure (caller falls back to per-cell setTerrain).
     */
    private static boolean bulkCopyTerrain(CloudTile dest, byte[] source) {
        try {
            java.lang.reflect.Field terrainField =
                    org.pepsoft.worldpainter.Tile.class.getDeclaredField("terrain");
            terrainField.setAccessible(true);
            byte[] destArray = (byte[]) terrainField.get(dest);
            if (destArray == null || destArray.length != source.length) {
                return false;
            }
            System.arraycopy(source, 0, destArray, 0, source.length);
            return true;
        } catch (Exception e) {
            LOG.warn("Bulk terrain copy via reflection failed; falling back to per-cell: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Fast-path tile load for brushes: returns the cached {@link CloudTile} if present,
     * otherwise creates an empty one instantly and returns it.
     *
     * <p>We deliberately do NOT subscribe to the backend here. The use case is "brush touches
     * a brand-new tile coordinate that no one has painted before" — there's no content on
     * the server to fetch, and subscribing eagerly would queue ~N WebSocket round-trips for
     * a single brush stroke that touches N tiles, with no payoff. For tiles that DO have
     * server-side content, {@link CloudStorageBackend#open} preloads them via the synchronous
     * {@link #load} path before the editor ever renders.
     *
     * <p>Trade-off (TD-042): if another client paints on this tile in the future, we won't
     * receive their ops because we never subscribed. Acceptable for Phase 0 single-user use;
     * needs a "subscribe on first write" hook before multi-user collab works on new tiles.
     */
    public CloudTile loadFast(int tileX, int tileY) {
        long k = key(tileX, tileY);
        CloudTile existing = tilesByKey.get(k);
        if (existing != null) return existing;
        CloudTile fresh = new CloudTile(tileX, tileY, defaultMinHeight, defaultMaxHeight, this);
        CloudTile prior = tilesByKey.putIfAbsent(k, fresh);
        return prior != null ? prior : fresh;
    }

    public CloudTile getCachedCloudTile(int tileX, int tileY) {
        return tilesByKey.get(key(tileX, tileY));
    }

    // ─── MutationSink (outbound from CloudTile setters) ─────────────

    @Override
    public void onTerrain(int tileX, int tileY, int x, int y, Terrain terrain) {
        emitCell(tileX, tileY, x, y, TerrainRegistry.toByte(terrain), 0, Ops.OpType.OP_TYPE_TERRAIN);
    }

    @Override
    public void onHeight(int tileX, int tileY, int x, int y, float height) {
        emitCell(tileX, tileY, x, y, Math.round(height * 256f), 0, Ops.OpType.OP_TYPE_HEIGHT);
    }

    @Override
    public void onRawHeight(int tileX, int tileY, int x, int y, int rawHeight) {
        emitCell(tileX, tileY, x, y, rawHeight, 0, Ops.OpType.OP_TYPE_HEIGHT);
    }

    @Override
    public void onWaterLevel(int tileX, int tileY, int x, int y, int waterLevel) {
        emitCell(tileX, tileY, x, y, waterLevel, 0, Ops.OpType.OP_TYPE_WATER);
    }

    @Override
    public void onBitLayer(int tileX, int tileY, Layer layer, int x, int y, boolean value) {
        int layerId = LayerRegistry.idOf(layer);
        emitCell(tileX, tileY, x, y, value ? 1 : 0, layerId, Ops.OpType.OP_TYPE_BIT_LAYER);
    }

    @Override
    public void onLayer(int tileX, int tileY, Layer layer, int x, int y, int value) {
        int layerId = LayerRegistry.idOf(layer);
        emitCell(tileX, tileY, x, y, value, layerId, Ops.OpType.OP_TYPE_LAYER);
    }

    private void emitCell(int tileX, int tileY, int x, int y, int value, int layerId, Ops.OpType type) {
        ClientHlcClock clock = provider.clock();
        Ops.Op op = Ops.Op.newBuilder()
                .setOpId(opId())
                .setHlc(clock.next())
                .setTileId(Common.TileId.newBuilder().setTileX(tileX).setTileY(tileY))
                .setType(type)
                .setCell(Ops.CellOp.newBuilder()
                        .setX(x).setY(y).setValue(value).setLayerId(layerId))
                .build();
        provider.submitOp(op);
    }

    private static Common.OpId opId() {
        byte[] b = new byte[16];
        UUID u = UUID.randomUUID();
        ByteBuffer bb = ByteBuffer.wrap(b);
        bb.putLong(u.getMostSignificantBits());
        bb.putLong(u.getLeastSignificantBits());
        return Common.OpId.newBuilder().setUuid(ByteString.copyFrom(b)).build();
    }

    // ─── Inbound op routing ─────────────────────────────────────────

    private void onRemoteOpsHandler(int tileX, int tileY, List<Ops.Op> ops) {
        CloudTile tile = tilesByKey.get(key(tileX, tileY));
        if (tile == null) {
            return;
        }
        for (Ops.Op op : ops) {
            try { tile.applyRemoteOp(op); }
            catch (Exception e) { LOG.warn("Failed to apply remote op to ({},{}): {}", tileX, tileY, e.getMessage()); }
        }
    }

    // ─── Lifecycle ──────────────────────────────────────────────────

    @Override
    public void close() {
        provider.removeRemoteOpsListener(listener);
    }

    private static long key(int x, int y) {
        return (((long) x) << 32) | (y & 0xFFFFFFFFL);
    }
}
