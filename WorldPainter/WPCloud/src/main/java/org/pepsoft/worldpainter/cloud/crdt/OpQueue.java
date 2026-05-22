package org.pepsoft.worldpainter.cloud.crdt;

import com.talepainter.protocol.common.Common;
import com.talepainter.protocol.ops.Ops;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Buffers outbound {@link Ops.Op}s and flushes them as {@link Ops.OpBatch} on a fixed interval.
 *
 * <p>Within a single flush window, ops targeting the same (tile, kind, layerId, cellX, cellY)
 * are coalesced to the latest by HLC — matches the backend {@code OpCoalescer} behavior.
 */
public final class OpQueue {

    private static final Logger LOG = LoggerFactory.getLogger(OpQueue.class);

    private final long batchIntervalMs;
    private final Consumer<Ops.OpBatch> sink;

    private final List<Ops.Op> pending = new ArrayList<>();
    private final ScheduledExecutorService scheduler;

    public OpQueue(long batchIntervalMs, Consumer<Ops.OpBatch> sink) {
        this.batchIntervalMs = batchIntervalMs;
        this.sink = sink;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "wpcloud-opqueue-flush");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::flushSafely, batchIntervalMs, batchIntervalMs,
                TimeUnit.MILLISECONDS);
    }

    public void stop() {
        scheduler.shutdownNow();
    }

    public synchronized void enqueue(Ops.Op op) {
        pending.add(op);
    }

    /** Force an immediate flush; useful for tests and for graceful shutdown. */
    public void flushNow() {
        flushSafely();
    }

    private void flushSafely() {
        try { flush(); } catch (Throwable t) { LOG.error("OpQueue flush failed", t); }
    }

    private void flush() {
        List<Ops.Op> snapshot;
        synchronized (this) {
            if (pending.isEmpty()) return;
            snapshot = new ArrayList<>(pending);
            pending.clear();
        }
        List<Ops.Op> coalesced = coalesce(snapshot);
        Ops.OpBatch batch = Ops.OpBatch.newBuilder().addAllOps(coalesced).build();
        sink.accept(batch);
    }

    private static List<Ops.Op> coalesce(List<Ops.Op> ops) {
        Map<CellKey, Ops.Op> latest = new LinkedHashMap<>();
        List<Ops.Op> nonCell = new ArrayList<>();
        for (Ops.Op op : ops) {
            if (op.hasCell()) {
                CellKey k = new CellKey(packTile(op.getTileId()), op.getType(),
                        op.getCell().getLayerId(), op.getCell().getX(), op.getCell().getY());
                Ops.Op existing = latest.get(k);
                if (existing == null || compareHlc(op.getHlc(), existing.getHlc()) > 0) {
                    latest.put(k, op);
                }
            } else {
                nonCell.add(op);
            }
        }
        List<Ops.Op> out = new ArrayList<>(latest.size() + nonCell.size());
        out.addAll(latest.values());
        out.addAll(nonCell);
        return out;
    }

    private static long packTile(Common.TileId t) {
        return (((long) t.getTileX()) << 32) | (t.getTileY() & 0xFFFFFFFFL);
    }

    private static int compareHlc(Common.Hlc a, Common.Hlc b) {
        int c = Long.compare(a.getWallTimeMs(), b.getWallTimeMs());
        if (c != 0) return c;
        c = Integer.compare(a.getCounter(), b.getCounter());
        if (c != 0) return c;
        return Integer.compare(a.getNodeId(), b.getNodeId());
    }

    private record CellKey(long tilePacked, Ops.OpType type, int layerId, int x, int y) { }
}
