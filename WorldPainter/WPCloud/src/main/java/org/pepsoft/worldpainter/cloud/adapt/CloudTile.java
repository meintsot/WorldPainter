package org.pepsoft.worldpainter.cloud.adapt;

import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.layers.Layer;

/**
 * Cloud-backed tile. Subclasses {@link Tile} and overrides each public mutator to emit a CRDT
 * op (via {@link MutationSink}) after the underlying mutation. Op emission is suppressed when
 * the current thread is inside {@link RemoteOpContext#runApplyingRemote} (so applying inbound
 * ops doesn't generate outbound ops).
 *
 * <p>Existing {@code Tile.Listener} callbacks (used by {@link org.pepsoft.worldpainter.Dimension},
 * the view repaint logic, and plugins) keep firing exactly as before — we never bypass {@code super}.
 */
public class CloudTile extends Tile {

    private final transient MutationSink sink;

    public CloudTile(int x, int y, int minHeight, int maxHeight, MutationSink sink) {
        super(x, y, minHeight, maxHeight);
        this.sink = sink;
    }

    @Override
    public void setTerrain(int x, int y, Terrain terrain) {
        super.setTerrain(x, y, terrain);
        if (!RemoteOpContext.isApplyingRemote()) {
            sink.onTerrain(getX(), getY(), x, y, terrain);
        }
    }

    @Override
    public void setHeight(int x, int y, float height) {
        super.setHeight(x, y, height);
        if (!RemoteOpContext.isApplyingRemote()) {
            sink.onHeight(getX(), getY(), x, y, height);
        }
    }

    @Override
    public void setRawHeight(int x, int y, int rawHeight) {
        super.setRawHeight(x, y, rawHeight);
        if (!RemoteOpContext.isApplyingRemote()) {
            sink.onRawHeight(getX(), getY(), x, y, rawHeight);
        }
    }

    @Override
    public void setWaterLevel(int x, int y, int waterLevel) {
        super.setWaterLevel(x, y, waterLevel);
        if (!RemoteOpContext.isApplyingRemote()) {
            sink.onWaterLevel(getX(), getY(), x, y, waterLevel);
        }
    }

    @Override
    public void setBitLayerValue(Layer layer, int x, int y, boolean value) {
        super.setBitLayerValue(layer, x, y, value);
        if (!RemoteOpContext.isApplyingRemote()) {
            sink.onBitLayer(getX(), getY(), layer, x, y, value);
        }
    }

    @Override
    public void setLayerValue(Layer layer, int x, int y, int value) {
        super.setLayerValue(layer, x, y, value);
        if (!RemoteOpContext.isApplyingRemote()) {
            sink.onLayer(getX(), getY(), layer, x, y, value);
        }
    }
}
