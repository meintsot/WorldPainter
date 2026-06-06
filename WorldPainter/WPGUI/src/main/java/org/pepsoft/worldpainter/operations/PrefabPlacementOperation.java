package org.pepsoft.worldpainter.operations;

import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.WorldPainter;
import org.pepsoft.worldpainter.hytale.HytalePrefabPlacement;
import org.pepsoft.worldpainter.hytale.prefab.PrefabTransferable;

import java.awt.Point;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.beans.PropertyVetoException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tool for authoring exact Hytale prefab placements: accepts prefab drops from the
 * palette (creating a placement at the drop's world coords), and selects existing
 * placements on click. Move/rotate gestures and on-map drawing are added in later tasks.
 */
public final class PrefabPlacementOperation extends MouseOrTabletOperation {
    private final WorldPainter view;
    private final AtomicLong idSeq = new AtomicLong(System.nanoTime());
    private DropTarget dropTarget;
    private long selectedId = -1;
    private Runnable selectionListener;

    public PrefabPlacementOperation(WorldPainter view) {
        super("Place Prefab", "Place a Hytale prefab at an exact location", "prefabPlacement");
        this.view = view;
        setView(view);
    }

    /**
     * Installs mouse/tablet listeners (via super) and registers the DnD drop target on the canvas.
     * Signature matches AbstractOperation: protected void activate() throws PropertyVetoException.
     */
    @Override
    protected void activate() throws PropertyVetoException {
        super.activate();
        dropTarget = new DropTarget(view, DnDConstants.ACTION_COPY, new DropHandler(), true);
    }

    /**
     * Removes the drop target and deregisters mouse/tablet listeners (via super).
     * Signature matches AbstractOperation/MouseOrTabletOperation: protected void deactivate() — no throws clause.
     */
    @Override
    protected void deactivate() {
        if (dropTarget != null) {
            view.setDropTarget(null);
            dropTarget = null;
        }
        selectedId = -1;
        super.deactivate();
    }

    @Override
    protected void tick(int centreX, int centreY, boolean inverse, boolean first, float dynamicLevel) {
        if (!first) {
            return;
        }
        HytalePrefabPlacement hit = hitTest(centreX, centreY);
        selectedId = (hit != null) ? hit.getId() : -1;
        notifySelection();
        view.setPrefabPlacementSelectionId(selectedId);
    }

    private HytalePrefabPlacement hitTest(int worldX, int worldY) {
        Dimension dim = view.getDimension();
        if (dim == null) {
            return null;
        }
        HytalePrefabPlacement best = null;
        int bestDist = Integer.MAX_VALUE;
        for (HytalePrefabPlacement p : dim.getHytalePrefabPlacements()) {
            int dx = p.getX() - worldX, dy = p.getY() - worldY;
            int dist = (dx * dx) + (dy * dy);
            if ((dist < bestDist) && (dist <= 64)) {
                best = p;
                bestDist = dist;
            }
        }
        return best;
    }

    public long getSelectedId() {
        return selectedId;
    }

    public void setSelectedId(long id) {
        this.selectedId = id;
        view.setPrefabPlacementSelectionId(selectedId);
    }

    public void setSelectionListener(Runnable listener) {
        this.selectionListener = listener;
    }

    private void notifySelection() {
        if (selectionListener != null) {
            selectionListener.run();
        }
    }

    private boolean addPlacementAt(int worldX, int worldY, PrefabTransferable.Payload payload) {
        Dimension dim = view.getDimension();
        if (dim == null) {
            return false;
        }
        HytalePrefabPlacement placement = new HytalePrefabPlacement(
                idSeq.incrementAndGet(), payload.path, payload.name,
                worldX, worldY, null, true, 0.0);
        dim.addHytalePrefabPlacement(placement);
        selectedId = placement.getId();
        notifySelection();
        view.setPrefabPlacementSelectionId(selectedId);
        return true;
    }

    private final class DropHandler extends DropTargetAdapter {
        @Override
        public void drop(DropTargetDropEvent dtde) {
            try {
                if (!dtde.isDataFlavorSupported(PrefabTransferable.PREFAB_FLAVOR)) {
                    dtde.rejectDrop();
                    return;
                }
                dtde.acceptDrop(DnDConstants.ACTION_COPY);
                PrefabTransferable.Payload payload = (PrefabTransferable.Payload)
                        dtde.getTransferable().getTransferData(PrefabTransferable.PREFAB_FLAVOR);
                Point world = view.viewToWorld(dtde.getLocation());
                boolean placed = (world != null) && addPlacementAt(world.x, world.y, payload);
                dtde.dropComplete(placed);
            } catch (Exception e) {
                dtde.dropComplete(false);
            }
        }
    }
}
