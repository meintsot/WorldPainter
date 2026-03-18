/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.pepsoft.worldpainter.operations;

import org.pepsoft.worldpainter.DefaultPlugin;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.HeightMapTileFactory;
import org.pepsoft.worldpainter.TileFactory;
import org.pepsoft.worldpainter.WorldPainterView;
import org.pepsoft.worldpainter.hytale.HytaleFluidLayer;
import org.pepsoft.worldpainter.layers.FloodWithLava;

import javax.swing.*;

/**
 *
 * @author pepijn
 */
public class Sponge extends AbstractBrushOperation {
    public Sponge(WorldPainterView view) {
        super("Sponge", "Dry up or reset water, lava and other fluids", view, 100, "operation.sponge");
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
        final int waterHeight, minHeight = dimension.getMinHeight();
        final TileFactory tileFactory = dimension.getTileFactory();
        if (tileFactory instanceof HeightMapTileFactory) {
            waterHeight = ((HeightMapTileFactory) tileFactory).getWaterHeight();
        } else {
            // If we can't determine the water height disable the inverse
            // functionality, which resets to the default water height
            waterHeight = -1;
        }
        final boolean isHytale = (dimension.getWorld() != null)
                && DefaultPlugin.HYTALE.id.equals(dimension.getWorld().getPlatform().id);
        dimension.setEventsInhibited(true);
        try {
            final int radius = getEffectiveRadius();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    if (getStrength(centreX, centreY, centreX + dx, centreY + dy) != 0f) {
                        final int px = centreX + dx, py = centreY + dy;
                        if (inverse) {
                            if (waterHeight != -1) {
                                dimension.setWaterLevelAt(px, py, waterHeight);
                                dimension.setBitLayerValueAt(FloodWithLava.INSTANCE, px, py, false);
                                if (isHytale) {
                                    dimension.setLayerValueAt(HytaleFluidLayer.INSTANCE, px, py, HytaleFluidLayer.FLUID_NONE);
                                }
                            }
                        } else {
                            dimension.setWaterLevelAt(px, py, minHeight);
                            if (isHytale) {
                                dimension.setLayerValueAt(HytaleFluidLayer.INSTANCE, px, py, HytaleFluidLayer.FLUID_NONE);
                            }
                        }
                    }
                }
            }
        } finally {
            dimension.setEventsInhibited(false);
        }
    }

    private static final JPanel OPTIONS_PANEL = new StandardOptionsPanel("Sponge", "<ul><li>Left-click to remove all fluids<li>Right-click to reset to the default fluid type and height</ul>");
}