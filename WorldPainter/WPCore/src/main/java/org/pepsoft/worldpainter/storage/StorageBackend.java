package org.pepsoft.worldpainter.storage;

import org.pepsoft.util.ProgressReceiver;
import org.pepsoft.worldpainter.World2;

/**
 * Pluggable backend that opens, saves, and closes worlds. Implementations are registered with
 * {@link StorageBackendRegistry} at app startup; {@code App.openWorld(WorldRef)} dispatches by
 * {@link WorldRef#kind()}.
 *
 * <p>{@code LocalFileStorageBackend} wraps WorldPainter's existing local {@code .world} file
 * load/save code. {@code CloudStorageBackend} (in WPCloud) opens a cloud world by id.
 */
public interface StorageBackend {

    WorldRef.Kind kind();

    /**
     * Open the world identified by {@code ref}. Returns a fully-loaded {@link World2}.
     * May block on disk or network I/O; {@code progress} receives progress updates.
     */
    World2 open(WorldRef ref, ProgressReceiver progress) throws Exception;

    /**
     * Persist the world to its backend. For local files this writes the {@code .world} file;
     * for cloud worlds this may be a no-op (the op stream auto-persists) or it may force a
     * snapshot.
     */
    void save(World2 world, WorldRef ref, ProgressReceiver progress) throws Exception;

    /**
     * Release any backend-side resources (close WebSocket, flush op queue, etc.). Called when
     * the user closes the world in the editor.
     */
    void close(World2 world);
}
