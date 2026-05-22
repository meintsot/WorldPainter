package org.pepsoft.worldpainter.cloud.adapt;

/**
 * Thread-local marker indicating that the current thread is applying a CRDT op received from
 * the cloud backend, and therefore {@link CloudTile} setter mutations triggered from within
 * the marked scope must NOT emit a new outbound op.
 *
 * <p>Usage:
 * <pre>
 *   RemoteOpContext.runApplyingRemote(() -&gt; cloudTile.setTerrain(x, y, decodedTerrain));
 * </pre>
 *
 * <p>Nested {@code runApplyingRemote} calls preserve the flag across nesting and restore it
 * on exit. The flag is per-thread; concurrent threads do not affect each other.
 */
public final class RemoteOpContext {

    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private RemoteOpContext() {}

    public static boolean isApplyingRemote() {
        return DEPTH.get() > 0;
    }

    public static void runApplyingRemote(Runnable action) {
        DEPTH.set(DEPTH.get() + 1);
        try {
            action.run();
        } finally {
            int d = DEPTH.get() - 1;
            if (d <= 0) DEPTH.remove();
            else DEPTH.set(d);
        }
    }
}
