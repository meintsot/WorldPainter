package org.pepsoft.worldpainter.cloud.tile;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bounded LRU cache for {@link LocalTile} instances keyed by {@code (worldId, tileX, tileY)}.
 *
 * <p>Dirty tiles (those with un-ACKed outbound ops) are pinned against eviction. When the cache
 * is at capacity and a new put arrives, the oldest non-dirty tile is evicted. If every tile is
 * dirty, the cache grows past its target capacity rather than dropping in-flight work.
 */
public final class TileCache {

    private final int maxTiles;
    private final LinkedHashMap<Key, LocalTile> store;
    private final Set<Key> dirty = ConcurrentHashMap.newKeySet();

    public TileCache(int maxTiles) {
        this.maxTiles = maxTiles;
        this.store = new LinkedHashMap<>(maxTiles, 0.75f, true);  // accessOrder=true → LRU
    }

    public synchronized LocalTile get(UUID worldId, int tileX, int tileY) {
        return store.get(new Key(worldId, tileX, tileY));
    }

    public synchronized void put(UUID worldId, LocalTile tile) {
        Key k = new Key(worldId, tile.tileX(), tile.tileY());
        store.put(k, tile);
        evictIfNeeded();
    }

    public void markDirty(UUID worldId, int tileX, int tileY) {
        dirty.add(new Key(worldId, tileX, tileY));
    }

    public void markClean(UUID worldId, int tileX, int tileY) {
        dirty.remove(new Key(worldId, tileX, tileY));
    }

    public synchronized int size() { return store.size(); }

    private void evictIfNeeded() {
        if (store.size() <= maxTiles) return;
        Key victim = null;
        for (Key k : store.keySet()) {
            if (!dirty.contains(k)) { victim = k; break; }
        }
        if (victim != null) {
            store.remove(victim);
        }
        // else: every tile is dirty; refuse to evict. Cache grows.
    }

    private record Key(UUID worldId, int tileX, int tileY) {
        Key {
            Objects.requireNonNull(worldId);
        }
    }
}
