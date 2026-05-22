package org.pepsoft.worldpainter.storage;

import org.junit.Before;
import org.junit.Test;
import org.pepsoft.util.ProgressReceiver;
import org.pepsoft.worldpainter.World2;

import java.io.File;
import java.util.UUID;

import static org.junit.Assert.*;

public class StorageBackendRegistryTest {

    private StorageBackendRegistry registry;

    @Before
    public void setUp() {
        registry = new StorageBackendRegistry();
    }

    @Test
    public void register_and_lookup_by_kind() {
        StubBackend localBackend = new StubBackend(WorldRef.Kind.LOCAL);
        registry.register(localBackend);
        assertSame(localBackend, registry.forRef(WorldRef.local(new File("/x.world"))));
    }

    @Test
    public void forRef_throws_when_no_backend_registered() {
        try {
            registry.forRef(WorldRef.cloud(UUID.randomUUID()));
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("No StorageBackend"));
        }
    }

    private static class StubBackend implements StorageBackend {
        private final WorldRef.Kind kind;

        StubBackend(WorldRef.Kind kind) { this.kind = kind; }

        @Override
        public WorldRef.Kind kind() { return kind; }

        @Override
        public World2 open(WorldRef ref, ProgressReceiver progress) { return null; }

        @Override
        public void save(World2 world, WorldRef ref, ProgressReceiver progress) {}

        @Override
        public void close(World2 world) {}
    }
}
