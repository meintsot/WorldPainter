package org.pepsoft.worldpainter.cloud.tile;

import com.google.protobuf.ByteString;
import com.talepainter.protocol.common.Common;
import com.talepainter.protocol.ops.Ops;

import java.nio.ByteBuffer;
import java.util.Set;
import java.util.UUID;

/**
 * Applies a single {@link Ops.Op} to a {@link LocalTile} in place. LWW resolution for cell ops;
 * OR-Set semantics for layer add/remove; HLC-gated for metadata.
 *
 * <p>Pure function (no side effects beyond the {@code state} argument).
 */
public final class OpApplicator {

    public void apply(LocalTile state, Ops.Op op) {
        switch (op.getBodyCase()) {
            case CELL  -> applyCell(state, op);
            case RECT  -> applyRect(state, op);
            case LAYER -> applyLayer(state, op);
            case META  -> applyMeta(state, op);
            default    -> throw new IllegalArgumentException("Op has no body: " + op);
        }
    }

    private void applyCell(LocalTile state, Ops.Op op) {
        Ops.CellOp c = op.getCell();
        LocalTile.Field field = fieldFor(op.getType());
        Common.Hlc existing = state.getCellHlc(field, c.getLayerId(), c.getX(), c.getY());
        if (existing != null && compareHlc(op.getHlc(), existing) <= 0) return;
        writeCell(state, field, c.getLayerId(), c.getX(), c.getY(), c.getValue(), op.getHlc());
    }

    private void applyRect(LocalTile state, Ops.Op op) {
        Ops.RectOp r = op.getRect();
        LocalTile.Field field = fieldFor(op.getType());
        for (int x = r.getX1(); x <= r.getX2(); x++) {
            for (int y = r.getY1(); y <= r.getY2(); y++) {
                Common.Hlc existing = state.getCellHlc(field, r.getLayerId(), x, y);
                if (existing != null && compareHlc(op.getHlc(), existing) <= 0) continue;
                writeCell(state, field, r.getLayerId(), x, y, r.getValue(), op.getHlc());
            }
        }
    }

    private void applyLayer(LocalTile state, Ops.Op op) {
        Ops.LayerOp l = op.getLayer();
        UUID tag = bsToUuid(l.getUniqueTag());
        switch (op.getType()) {
            case OP_TYPE_LAYER_ADD    -> state.addLayer(l.getLayerId(), tag);
            case OP_TYPE_LAYER_REMOVE -> state.removeLayerTags(l.getLayerId(), Set.of(tag));
            default -> throw new IllegalArgumentException("LayerOp must be LAYER_ADD or LAYER_REMOVE");
        }
    }

    private void applyMeta(LocalTile state, Ops.Op op) {
        Common.Hlc existing = state.getMetadataHlc();
        if (existing != null && compareHlc(op.getHlc(), existing) <= 0) return;
        state.setMetadata(op.getMeta().getMetadataBlob().toByteArray(), op.getHlc());
    }

    private static void writeCell(LocalTile state, LocalTile.Field field, int layerId,
                                  int x, int y, int value, Common.Hlc hlc) {
        switch (field) {
            case TERRAIN -> state.setTerrain(x, y, (byte) value, hlc);
            case HEIGHT  -> state.setHeight(x, y, value, hlc);
            case WATER   -> state.setWaterLevel(x, y, (short) value, hlc);
            case LAYER, BIT_LAYER -> state.setLayerCell(layerId, x, y, (byte) value, hlc);
        }
    }

    private static LocalTile.Field fieldFor(Ops.OpType type) {
        return switch (type) {
            case OP_TYPE_TERRAIN   -> LocalTile.Field.TERRAIN;
            case OP_TYPE_HEIGHT    -> LocalTile.Field.HEIGHT;
            case OP_TYPE_WATER     -> LocalTile.Field.WATER;
            case OP_TYPE_LAYER     -> LocalTile.Field.LAYER;
            case OP_TYPE_BIT_LAYER -> LocalTile.Field.BIT_LAYER;
            default -> throw new IllegalArgumentException("not a cell-field op type: " + type);
        };
    }

    private static int compareHlc(Common.Hlc a, Common.Hlc b) {
        int c = Long.compare(a.getWallTimeMs(), b.getWallTimeMs());
        if (c != 0) return c;
        c = Integer.compare(a.getCounter(), b.getCounter());
        if (c != 0) return c;
        return Integer.compare(a.getNodeId(), b.getNodeId());
    }

    private static UUID bsToUuid(ByteString bs) {
        if (bs.size() != 16) throw new IllegalArgumentException("uuid not 16 bytes");
        ByteBuffer bb = bs.asReadOnlyByteBuffer();
        return new UUID(bb.getLong(), bb.getLong());
    }
}
