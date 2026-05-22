package org.pepsoft.worldpainter.storage;

import org.pepsoft.util.ProgressReceiver;
import org.pepsoft.worldpainter.UnloadableWorldException;
import org.pepsoft.worldpainter.World2;
import org.pepsoft.worldpainter.WorldIO;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Wraps WorldPainter's existing local {@code .world} file load and save code behind the
 * {@link StorageBackend} interface. Pure refactor — no behavior change.
 *
 * <p>The {@code progress} parameter is accepted for interface compatibility but not threaded through
 * to {@link WorldIO}, which does not expose progress reporting on its load/save methods.
 */
public final class LocalFileStorageBackend implements StorageBackend {

    @Override
    public WorldRef.Kind kind() {
        return WorldRef.Kind.LOCAL;
    }

    @Override
    public World2 open(WorldRef ref, ProgressReceiver progress) throws IOException, UnloadableWorldException {
        File file = ref.localFile();
        WorldIO worldIO = new WorldIO();
        worldIO.load(new FileInputStream(file));
        return worldIO.getWorld();
    }

    @Override
    public void save(World2 world, WorldRef ref, ProgressReceiver progress) throws IOException {
        File file = ref.localFile();
        WorldIO worldIO = new WorldIO(world);
        worldIO.save(new FileOutputStream(file));
    }

    @Override
    public void close(World2 world) {
        // Local-file worlds have no persistent resources to release.
    }
}
