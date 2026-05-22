package org.pepsoft.worldpainter.cloud.tile;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TileCacheTest {

    private static final UUID WORLD = UUID.randomUUID();

    @Test
    void put_then_get_round_trips() {
        TileCache cache = new TileCache(10);
        LocalTile t = new LocalTile(5, 7);
        cache.put(WORLD, t);
        assertThat(cache.get(WORLD, 5, 7)).isSameAs(t);
    }

    @Test
    void missing_tile_returns_null() {
        TileCache cache = new TileCache(10);
        assertThat(cache.get(WORLD, 99, 99)).isNull();
    }

    @Test
    void capacity_eviction_drops_oldest_clean_tile() {
        TileCache cache = new TileCache(2);
        cache.put(WORLD, new LocalTile(0, 0));
        cache.put(WORLD, new LocalTile(1, 0));
        cache.put(WORLD, new LocalTile(2, 0));    // triggers eviction of (0,0)
        assertThat(cache.get(WORLD, 0, 0)).isNull();
        assertThat(cache.get(WORLD, 1, 0)).isNotNull();
        assertThat(cache.get(WORLD, 2, 0)).isNotNull();
    }

    @Test
    void dirty_tile_is_protected_from_eviction() {
        TileCache cache = new TileCache(2);
        cache.put(WORLD, new LocalTile(0, 0));
        cache.markDirty(WORLD, 0, 0);
        cache.put(WORLD, new LocalTile(1, 0));
        cache.put(WORLD, new LocalTile(2, 0));    // (0,0) is dirty → evict (1,0) instead
        assertThat(cache.get(WORLD, 0, 0)).isNotNull();
        assertThat(cache.get(WORLD, 1, 0)).isNull();
        assertThat(cache.get(WORLD, 2, 0)).isNotNull();
    }

    @Test
    void marking_clean_re_enables_eviction() {
        TileCache cache = new TileCache(2);
        cache.put(WORLD, new LocalTile(0, 0));
        cache.markDirty(WORLD, 0, 0);
        cache.put(WORLD, new LocalTile(1, 0));
        cache.markClean(WORLD, 0, 0);
        cache.put(WORLD, new LocalTile(2, 0));    // now (0,0) is evictable; it's older → goes
        assertThat(cache.get(WORLD, 0, 0)).isNull();
    }
}
