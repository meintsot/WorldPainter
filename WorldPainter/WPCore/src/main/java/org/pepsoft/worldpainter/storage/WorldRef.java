package org.pepsoft.worldpainter.storage;

import java.io.File;
import java.util.UUID;

/**
 * Identifies a world for {@link StorageBackend} dispatch. Either a local file path or a
 * cloud world UUID.
 */
public final class WorldRef {

    public enum Kind { LOCAL, CLOUD }

    private final Kind kind;
    private final File localFile;
    private final UUID cloudWorldId;

    private WorldRef(Kind kind, File localFile, UUID cloudWorldId) {
        this.kind = kind;
        this.localFile = localFile;
        this.cloudWorldId = cloudWorldId;
    }

    public static WorldRef local(File file) {
        if (file == null) {
            throw new IllegalArgumentException("file");
        }
        return new WorldRef(Kind.LOCAL, file, null);
    }

    public static WorldRef cloud(UUID worldId) {
        if (worldId == null) {
            throw new IllegalArgumentException("worldId");
        }
        return new WorldRef(Kind.CLOUD, null, worldId);
    }

    public Kind kind() { return kind; }
    public boolean isLocal() { return kind == Kind.LOCAL; }
    public boolean isCloud() { return kind == Kind.CLOUD; }

    public File localFile() {
        if (kind != Kind.LOCAL) {
            throw new IllegalStateException("Not a local WorldRef");
        }
        return localFile;
    }

    public UUID cloudWorldId() {
        if (kind != Kind.CLOUD) {
            throw new IllegalStateException("Not a cloud WorldRef");
        }
        return cloudWorldId;
    }

    @Override
    public String toString() {
        return kind == Kind.LOCAL ? "Local[" + localFile + "]" : "Cloud[" + cloudWorldId + "]";
    }
}
