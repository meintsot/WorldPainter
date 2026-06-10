/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.pepsoft.worldpainter.operations;

import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.WorldPainter;
import org.pepsoft.worldpainter.layers.ReadOnly;

import javax.swing.*;

/**
 *
 * @author pepijn
 */
public class RaisePyramid extends MouseOrTabletOperation {
    public RaisePyramid(WorldPainter worldPainter) {
        super("Raise Pyramid", "Raises a square pyramid out of the ground", worldPainter, 100, "operation.raisePyramid", "pyramid");
    }

    @Override
    public JPanel getOptionsPanel() {
        return OPTIONS_PANEL;
    }

    @Override
    protected void tick(int centreX, int centreY, boolean inverse, boolean first, float dynamicLevel) {
        final Dimension dimension = getDimension();
        if (dimension == null) {
            // Probably some kind of race condition
            return;
        }
        float height = dimension.getHeightAt(centreX, centreY);
        dimension.setEventsInhibited(true);
        try {
            // TP-58: don't modify read-only (imported) chunks
            if (! dimension.getBitLayerValueAt(ReadOnly.INSTANCE, centreX, centreY)) {
                if (height < (dimension.getMaxHeight() - 1.5f)) {
                    dimension.setHeightAt(centreX, centreY, height + 1);
                }
                dimension.setTerrainAt(centreX, centreY, Terrain.SANDSTONE);
            }
            int maxR = dimension.getMaxHeight() - dimension.getMinHeight();
            for (int r = 1; r < maxR; r++) {
                if (! raiseRing(dimension, centreX, centreY, r, height--)) {
                    break;
                }
            }
        } finally {
            dimension.setEventsInhibited(false);
        }
    }

    private boolean raiseRing(Dimension dimension, int x, int y, int r, float desiredHeight) {
        boolean raised = false;
        for (int i = -r; i <= r; i++) {
            raised |= raiseColumn(dimension, x + i, y - r, desiredHeight);
            raised |= raiseColumn(dimension, x + i, y + r, desiredHeight);
        }
        for (int i = -r + 1; i < r; i++) {
            raised |= raiseColumn(dimension, x - r, y + i, desiredHeight);
            raised |= raiseColumn(dimension, x + r, y + i, desiredHeight);
        }
        return raised;
    }

    private boolean raiseColumn(Dimension dimension, int x, int y, float desiredHeight) {
        if (dimension.getHeightAt(x, y) < desiredHeight) {
            // TP-58: don't modify read-only (imported) chunks, but do keep expanding the pyramid past them
            if (! dimension.getBitLayerValueAt(ReadOnly.INSTANCE, x, y)) {
                dimension.setHeightAt(x, y, desiredHeight);
                dimension.setTerrainAt(x, y, Terrain.SANDSTONE);
            }
            return true;
        }
        return false;
    }

    private static final StandardOptionsPanel OPTIONS_PANEL = new StandardOptionsPanel("Raise Pyramid", "<p>Click to raise a four-sided sandstone pyramid from the ground");
}