package org.pepsoft.worldpainter.cloud.adapt;

import org.pepsoft.worldpainter.Platform;
import org.pepsoft.worldpainter.World2;

import java.util.UUID;

/**
 * Cloud-backed {@link World2}. Carries the cloud world id and a reference to the
 * {@link MultiTileCloudProvider} so the App layer can identify cloud worlds and route save/close
 * actions appropriately.
 *
 * <p>Marked {@code transient} fields are NOT serialized — cloud worlds are never written to
 * a local {@code .world} file. If the user accidentally invokes a serializing save action, the
 * file would contain only the inherited {@link World2} state minus cloud-specific fields,
 * which is by design (Task 17 disables the save action for cloud worlds anyway).
 */
public final class CloudWorld2 extends World2 {

    private static final long serialVersionUID = 1L;

    private final transient UUID cloudWorldId;
    private final transient MultiTileCloudProvider multiTileProvider;

    public CloudWorld2(Platform platform, int minHeight, int maxHeight,
                       UUID cloudWorldId, MultiTileCloudProvider multiTileProvider) {
        super(platform, minHeight, maxHeight);
        this.cloudWorldId = cloudWorldId;
        this.multiTileProvider = multiTileProvider;
    }

    public UUID cloudWorldId() { return cloudWorldId; }
    public MultiTileCloudProvider multiTileProvider() { return multiTileProvider; }
}
