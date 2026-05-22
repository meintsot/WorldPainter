package org.pepsoft.worldpainter.cloud.adapt;

/**
 * Loads {@link CloudTile} instances from the backend. Two paths:
 *
 * <ul>
 *   <li>{@link #load} — synchronous. Blocks until a SUBSCRIBE/TILE_SNAPSHOT round-trip
 *       completes. Used by the world-open preload and by getTile's background async path.
 *   <li>{@link #loadFast} — non-blocking. Returns a cached tile if available, otherwise
 *       creates an empty {@link CloudTile} instantly and dispatches the SUBSCRIBE in the
 *       background. Used by {@code getTileForEditing} so brushes never freeze the EDT on
 *       large brush strokes that touch many new tiles.
 * </ul>
 */
public interface CloudTileLoader {

    /**
     * Fetch and return the cloud tile at {@code (tileX, tileY)}. Returns the same instance
     * on repeated calls for the same coords (the loader maintains its own cache). Blocking.
     */
    CloudTile load(int tileX, int tileY);

    /**
     * Returns the cached cloud tile if present, otherwise creates an empty tile and starts a
     * background subscribe. When the snapshot arrives, its non-default cells are applied via
     * {@link CloudTile#applyRemoteOp} which honors HLC LWW — brush writes that happened in
     * the meantime keep their values.
     */
    CloudTile loadFast(int tileX, int tileY);
}
