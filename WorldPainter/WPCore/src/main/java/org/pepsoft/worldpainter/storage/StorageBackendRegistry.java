package org.pepsoft.worldpainter.storage;

import java.util.EnumMap;
import java.util.Map;

/**
 * Process-wide registry of {@link StorageBackend} implementations keyed by {@link WorldRef.Kind}.
 */
public final class StorageBackendRegistry {

    private static final StorageBackendRegistry INSTANCE = new StorageBackendRegistry();

    public static StorageBackendRegistry getInstance() { return INSTANCE; }

    private final Map<WorldRef.Kind, StorageBackend> backends = new EnumMap<>(WorldRef.Kind.class);

    public synchronized void register(StorageBackend backend) {
        backends.put(backend.kind(), backend);
    }

    public synchronized StorageBackend forRef(WorldRef ref) {
        StorageBackend b = backends.get(ref.kind());
        if (b == null) {
            throw new IllegalStateException("No StorageBackend registered for kind " + ref.kind());
        }
        return b;
    }

    public synchronized boolean isRegistered(WorldRef.Kind kind) {
        return backends.containsKey(kind);
    }
}
