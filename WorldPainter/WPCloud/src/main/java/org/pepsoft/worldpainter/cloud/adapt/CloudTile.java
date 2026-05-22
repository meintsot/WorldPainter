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

    /**
     * Defensive override: WorldPainter's {@link Tile#ensureReadable} reassigns the protected
     * {@code bitLayerData} / {@code layerData} fields from the UndoManager when their buffer
     * key isn't in {@code readableBuffers} (e.g., after an undo step fires
     * {@code bufferChanged}). If the UndoManager has no prior buffer for those fields, the
     * assignment yields {@code null} and the next read NPEs.
     *
     * <p>Cloud tiles often hit this path because brush usage on a fresh tile may write only
     * terrain and never touch bit-layer data — yet a mouse-hover that reads bit-layer state
     * still happens. We restore empty-map defaults after delegating to super.
     */
    @Override
    protected synchronized void ensureReadable(TileBuffer buffer) {
        super.ensureReadable(buffer);
        if (bitLayerData == null) {
            bitLayerData = new java.util.HashMap<>();
        }
        if (layerData == null) {
            layerData = new java.util.HashMap<>();
        }
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

    /**
     * Apply an inbound CRDT op (from the cloud backend) to this tile. Sets the
     * {@link RemoteOpContext} flag so the resulting setter calls do NOT emit new outbound ops.
     *
     * <p>HLC ordering / LWW resolution is handled by the caller (the
     * {@code MultiTileCloudProvider}'s op-applicator pipeline) before this method is invoked.
     */
    public void applyRemoteOp(com.talepainter.protocol.ops.Ops.Op op) {
        RemoteOpContext.runApplyingRemote(() -> dispatch(op));
    }

    private void dispatch(com.talepainter.protocol.ops.Ops.Op op) {
        switch (op.getBodyCase()) {
            case CELL  -> applyCell(op);
            case RECT  -> applyRect(op);
            case LAYER -> applyLayerOp(op);
            default    -> throw new IllegalArgumentException("Op has no supported body for CloudTile: " + op.getBodyCase());
        }
    }

    private void applyCell(com.talepainter.protocol.ops.Ops.Op op) {
        com.talepainter.protocol.ops.Ops.CellOp c = op.getCell();
        int x = c.getX(), y = c.getY();
        switch (op.getType()) {
            case OP_TYPE_TERRAIN -> setTerrain(x, y, TerrainRegistry.fromByte((byte) c.getValue()));
            case OP_TYPE_HEIGHT  -> setRawHeight(x, y, c.getValue());
            case OP_TYPE_WATER   -> setWaterLevel(x, y, c.getValue());
            case OP_TYPE_LAYER   -> {
                Layer layer = LayerRegistry.layerFor(c.getLayerId());
                if (layer != null) {
                    setLayerValue(layer, x, y, c.getValue());
                }
            }
            case OP_TYPE_BIT_LAYER -> {
                Layer layer = LayerRegistry.layerFor(c.getLayerId());
                if (layer != null) {
                    setBitLayerValue(layer, x, y, c.getValue() != 0);
                }
            }
            default -> { /* ignore unsupported op types in Phase 0c-3 */ }
        }
    }

    private void applyRect(com.talepainter.protocol.ops.Ops.Op op) {
        com.talepainter.protocol.ops.Ops.RectOp r = op.getRect();
        for (int x = r.getX1(); x <= r.getX2(); x++) {
            for (int y = r.getY1(); y <= r.getY2(); y++) {
                switch (op.getType()) {
                    case OP_TYPE_TERRAIN -> setTerrain(x, y, TerrainRegistry.fromByte((byte) r.getValue()));
                    case OP_TYPE_HEIGHT  -> setRawHeight(x, y, r.getValue());
                    case OP_TYPE_WATER   -> setWaterLevel(x, y, r.getValue());
                    case OP_TYPE_LAYER   -> {
                        Layer layer = LayerRegistry.layerFor(r.getLayerId());
                        if (layer != null) {
                            setLayerValue(layer, x, y, r.getValue());
                        }
                    }
                    case OP_TYPE_BIT_LAYER -> {
                        Layer layer = LayerRegistry.layerFor(r.getLayerId());
                        if (layer != null) {
                            setBitLayerValue(layer, x, y, r.getValue() != 0);
                        }
                    }
                    default -> { }
                }
            }
        }
    }

    private void applyLayerOp(com.talepainter.protocol.ops.Ops.Op op) {
        // OR-Set layer presence ops (LAYER_ADD / LAYER_REMOVE) — Phase 0c-3 does not yet track
        // tile-level layer presence in the editor (existing WorldPainter doesn't expose this
        // concept); ignore for now. TD-036 logs this.
    }
}
