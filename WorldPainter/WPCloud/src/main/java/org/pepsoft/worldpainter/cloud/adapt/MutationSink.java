package org.pepsoft.worldpainter.cloud.adapt;

import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.layers.Layer;

/**
 * Callback hook that {@link CloudTile} invokes whenever a local mutation occurs (i.e. NOT
 * during {@link RemoteOpContext#runApplyingRemote} scope). The implementation generates the
 * appropriate CRDT op and enqueues it on the outbound stream.
 *
 * <p>One {@code MutationSink} per cloud world. Wired by {@code MultiTileCloudProvider} (Task 10).
 */
public interface MutationSink {

    void onTerrain(int tileX, int tileY, int cellX, int cellY, Terrain terrain);

    void onHeight(int tileX, int tileY, int cellX, int cellY, float height);

    /** Raw height: stored as int directly. Used for tall worlds. */
    void onRawHeight(int tileX, int tileY, int cellX, int cellY, int rawHeight);

    void onWaterLevel(int tileX, int tileY, int cellX, int cellY, int waterLevel);

    void onBitLayer(int tileX, int tileY, Layer layer, int cellX, int cellY, boolean value);

    void onLayer(int tileX, int tileY, Layer layer, int cellX, int cellY, int value);
}
