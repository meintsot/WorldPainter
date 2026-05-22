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
    private final java.util.concurrent.ExecutorService backgroundSubscribeExecutor =
            java.util.concurrent.Executors.newFixedThreadPool(16, r -> {
                Thread t = new Thread(r, "cloud-bg-subscribe");
                t.setDaemon(true);
                return t;
            });

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
     * otherwise creates an empty one instantly and dispatches a background SUBSCRIBE. The
     * brush paints on the empty tile immediately; when the snapshot eventually arrives, its
     * non-default cells are applied via {@link CloudTile#applyRemoteOp} which honors HLC LWW
     * (so the brush's fresh writes win over an older baseline).
     *
     * <p>This avoids freezing the EDT on the synchronous SUBSCRIBE+TILE_SNAPSHOT round-trip
     * for every new tile a large brush stroke touches.
     */
    public CloudTile loadFast(int tileX, int tileY) {
        long k = key(tileX, tileY);
        CloudTile existing = tilesByKey.get(k);
        if (existing != null) return existing;
        CloudTile fresh = new CloudTile(tileX, tileY, defaultMinHeight, defaultMaxHeight, this);
        CloudTile prior = tilesByKey.putIfAbsent(k, fresh);
        if (prior != null) return prior;   // another thread won the race; use theirs
        // Background subscribe so we (a) get a snapshot if the server has content for this
        // tile and (b) start receiving live TILE_OPS broadcasts from collaborators.
        backgroundSubscribeExecutor.execute(() -> {
            try {
                var localTile = provider.getTile(tileX, tileY);
                byte[] srcTerrain = localTile.terrainArrayUnsafe();
                // Apply only non-default cells, via setTerrain so the LWW path runs (preserves
                // any cells the user already painted with newer HLCs).
                RemoteOpContext.runApplyingRemote(() -> {
                    for (int i = 0; i < srcTerrain.length; i++) {
                        byte b = srcTerrain[i];
                        if (b != 0) {
                            int x = i % 128, y = i / 128;
                            fresh.setTerrain(x, y, TerrainRegistry.fromByte(b));
                        }
                    }
                });
            } catch (Exception e) {
                LOG.warn("Background subscribe failed for ({},{}): {}", tileX, tileY, e.getMessage());
            }
        });
        return fresh;
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
