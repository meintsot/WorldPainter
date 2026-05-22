package org.pepsoft.worldpainter.cloud.adapt;

/**
 * Synchronous interface for loading a {@link CloudTile} from the backend. Implementations
 * (Task 10's {@code MultiTileCloudProvider}) block while the WebSocket subscribe + snapshot
 * round-trip completes; UI callers should call this from a SwingWorker, not the EDT.
 */
public interface CloudTileLoader {

    /**
     * Fetch and return the cloud tile at {@code (tileX, tileY)}. Returns the same instance
     * on repeated calls for the same coords (the loader maintains its own cache).
     */
    CloudTile load(int tileX, int tileY);
}
