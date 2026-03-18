/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.pepsoft.worldpainter.operations;

import org.pepsoft.worldpainter.DefaultPlugin;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.WorldPainter;
import org.pepsoft.worldpainter.hytale.HytaleFluidLayer;
import org.pepsoft.worldpainter.layers.FloodWithLava;
import org.pepsoft.worldpainter.painting.GeneralQueueLinearFloodFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;

import static org.pepsoft.util.swing.MessageUtils.showWarning;
import static org.pepsoft.worldpainter.Constants.TILE_SIZE;

/**
 *
 * @author pepijn
 */
public class Flood extends MouseOrTabletOperation {

    /** Fluid type constants matching HytaleFluidLayer values. */
    public static final int FLUID_WATER  = 0;
    public static final int FLUID_LAVA   = 1;
    public static final int FLUID_POISON = 2;
    public static final int FLUID_SLIME  = 3;
    public static final int FLUID_TAR    = 4;

    private static final String[] FLUID_NAMES = { "Water", "Lava", "Poison", "Slime", "Tar" };
    private static final String[] ICON_NAMES  = { "flood", "flood_with_lava", "flood_with_poison", "flood_with_slime", "flood_with_tar" };

    /**
     * Legacy constructor for water/lava (used by existing tool panel code).
     */
    public Flood(WorldPainter view, boolean floodWithLava) {
        this(view, floodWithLava ? FLUID_LAVA : FLUID_WATER);
    }

    public Flood(WorldPainter view, int fluidType) {
        super(fluidType == FLUID_WATER ? "Flood" : FLUID_NAMES[fluidType],
                "Flood an area with " + FLUID_NAMES[fluidType].toLowerCase(),
                view,
                "operation.flood." + FLUID_NAMES[fluidType].toLowerCase(),
                ICON_NAMES[fluidType]);
        this.fluidType = fluidType;
        this.floodWithLava = (fluidType == FLUID_LAVA);
        String fluidName = FLUID_NAMES[fluidType].toLowerCase();
        optionsPanel = new StandardOptionsPanel("Flood with " + FLUID_NAMES[fluidType],
                "<ul><li>Left-click on dry land to flood with " + fluidName + "\n" +
                "<li>Left-click on " + fluidName + " to raise it by one\n" +
                "<li>Right-click on " + fluidName + " to lower it by one\n" +
                "<li>Click on a different fluid to convert it\n" +
                "</ul>");
    }

    public int getFluidType() {
        return fluidType;
    }
    
    @Override
    protected void tick(int centreX, int centreY, boolean inverse, boolean first, float dynamicLevel) {
        final Dimension dimension = getDimension();
        if (dimension == null) {
            // Probably some kind of race condition
            return;
        }

        // We have seen in the wild that this sometimes gets called recursively (perhaps someone clicks to flood more
        // than once and then it takes more than two seconds so it is continued in the background and event queue
        // processing is resumed?), which causes errors, so just ignore it if we are already flooding.
        if (alreadyFlooding) {
            logger.debug("Flood operation already in progress; ignoring repeated invocation");
            return;
        }
        alreadyFlooding = true;
        try {
            final Rectangle dimensionBounds = new Rectangle(dimension.getLowestX() * TILE_SIZE, dimension.getLowestY() * TILE_SIZE, dimension.getWidth() * TILE_SIZE, dimension.getHeight() * TILE_SIZE);
            final int terrainHeight = dimension.getIntHeightAt(centreX, centreY);
            if (terrainHeight == Integer.MIN_VALUE) {
                // Not on a tile
                return;
            }
            final int waterLevel = dimension.getWaterLevelAt(centreX, centreY);
            final boolean fluidPresent = waterLevel > terrainHeight;
            if (inverse && (! fluidPresent)) {
                // No point lowering the water level if there is no water...
                return;
            }
        // Determine whether the platform is Hytale (for HytaleFluidLayer support)
            final boolean isHytale = (dimension.getWorld() != null)
                    && DefaultPlugin.HYTALE.id.equals(dimension.getWorld().getPlatform().id);

            final GeneralQueueLinearFloodFiller.FillMethod fillMethod;
            if (fluidPresent && (floodWithLava != dimension.getBitLayerValueAt(FloodWithLava.INSTANCE, centreX, centreY))) {
                // There is fluid present of a different type; don't change the
                // height, just change the type
                if (floodWithLava) {
                    fillMethod = new FloodFillMethod("Changing water to lava", dimensionBounds) {
                        @Override public boolean isBoundary(int x, int y) {
                            final int height = dimension.getIntHeightAt(x, y);
                            return (height == Integer.MIN_VALUE) // Not on a tile
                                    || (dimension.getWaterLevelAt(x, y) <= height) // Not flooded
                                    || dimension.getBitLayerValueAt(FloodWithLava.INSTANCE, x, y); // Not water
                        }

                        @Override public void fill(int x, int y) {
                            dimension.setBitLayerValueAt(FloodWithLava.INSTANCE, x, y, true);
                            if (isHytale) {
                                dimension.setLayerValueAt(HytaleFluidLayer.INSTANCE, x, y, fluidType);
                            }
                        }
                    };
                } else {
                    fillMethod = new FloodFillMethod("Changing fluid to " + FLUID_NAMES[fluidType].toLowerCase(), dimensionBounds) {
                        @Override public boolean isBoundary(int x, int y) {
                            final int height = dimension.getIntHeightAt(x, y);
                            return (height == Integer.MIN_VALUE) // Not on a tile
                                    || (dimension.getWaterLevelAt(x, y) <= height) // Not flooded
                                    || (! dimension.getBitLayerValueAt(FloodWithLava.INSTANCE, x, y)); // Not lava
                        }

                        @Override public void fill(int x, int y) {
                            dimension.setBitLayerValueAt(FloodWithLava.INSTANCE, x, y, false);
                            if (isHytale) {
                                dimension.setLayerValueAt(HytaleFluidLayer.INSTANCE, x, y, fluidType);
                            }
                        }
                    };
                }
            } else {
                final int height = Math.max(terrainHeight, waterLevel);
                if (inverse ? (height <= dimension.getMinHeight()) : (height >= (dimension.getMaxHeight() - 1))) {
                    // Already at the lowest or highest possible point
                    return;
                }
                final int floodToHeight = inverse ? (height - 1): (height + 1);
                if (inverse) {
                    fillMethod = new FloodFillMethod("Lowering " + FLUID_NAMES[fluidType].toLowerCase() + " level", dimensionBounds) {
                        @Override public boolean isBoundary(int x, int y) {
                            final int height = dimension.getIntHeightAt(x, y);
                            return (height == Integer.MIN_VALUE) // Not on a tile
                                    || (dimension.getWaterLevelAt(x, y) <= height) // Not flooded
                                    || (dimension.getWaterLevelAt(x, y) <= floodToHeight); // Already at the required level or lower
                        }

                        @Override public void fill(int x, int y) {
                            dimension.setWaterLevelAt(x, y, floodToHeight);
                            dimension.setBitLayerValueAt(FloodWithLava.INSTANCE, x, y, floodWithLava);
                            if (isHytale) {
                                dimension.setLayerValueAt(HytaleFluidLayer.INSTANCE, x, y, fluidType);
                            }
                        }
                    };
                } else {
                    String desc = fluidPresent
                            ? "Raising " + FLUID_NAMES[fluidType].toLowerCase() + " level"
                            : "Flooding with " + FLUID_NAMES[fluidType].toLowerCase();
                    fillMethod = new FloodFillMethod(desc, dimensionBounds) {
                        @Override public boolean isBoundary(int x, int y) {
                            final int height = dimension.getIntHeightAt(x, y), waterLevel = dimension.getWaterLevelAt(x, y);
                            return (height == Integer.MIN_VALUE) // Not on a tile
                                    || (height >= floodToHeight) // Higher land encountered
                                    || (waterLevel >= floodToHeight); // Already at the required level or higher
                        }

                        @Override public void fill(int x, int y) {
                            dimension.setWaterLevelAt(x, y, floodToHeight);
                            dimension.setBitLayerValueAt(FloodWithLava.INSTANCE, x, y, floodWithLava);
                            if (isHytale) {
                                dimension.setLayerValueAt(HytaleFluidLayer.INSTANCE, x, y, fluidType);
                            }
                        }
                    };
                }
            }
            synchronized (dimension) {
                dimension.setEventsInhibited(true);
            }
            try {
                synchronized (dimension) {
                    dimension.rememberChanges();
                }
                final GeneralQueueLinearFloodFiller flooder = new GeneralQueueLinearFloodFiller(fillMethod);
                try {
                    if (! flooder.floodFill(centreX, centreY, SwingUtilities.getWindowAncestor(getView()))) {
                        // Cancelled by user
                        synchronized (dimension) {
                            if (dimension.undoChanges()) {
                                dimension.clearRedo();
                                dimension.armSavePoint();
                            }
                        }
                        return;
                    }
                    if (flooder.isBoundsHit()) {
                        showWarning(getView(), "The area to be flooded was too large and may not have been completely flooded.", "Area Too Large");
                    }
                } catch (IndexOutOfBoundsException e) {
                    // This most likely indicates that the area being flooded was too large
                    synchronized (dimension) {
                        if (dimension.undoChanges()) {
                            dimension.clearRedo();
                            dimension.armSavePoint();
                        }
                    }
                    JOptionPane.showMessageDialog(getView(), "The area to be flooded is too large or complex; please retry with a smaller area", "Area Too Large", JOptionPane.ERROR_MESSAGE);
                }
            } finally {
                synchronized (dimension) {
                    dimension.setEventsInhibited(false);
                }
            }
        } finally {
            alreadyFlooding = false;
        }
    }

    @Override
    public JPanel getOptionsPanel() {
        return optionsPanel;
    }

    private boolean alreadyFlooding;

    private final int fluidType;
    private final boolean floodWithLava;
    private final StandardOptionsPanel optionsPanel;

    private static final Logger logger = LoggerFactory.getLogger(Flood.class);

    static abstract class FloodFillMethod implements GeneralQueueLinearFloodFiller.FillMethod {
        protected FloodFillMethod(String description, Rectangle bounds) {
            this.description = description;
            this.bounds = bounds;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public Rectangle getBounds() {
            return bounds;
        }

        private final String description;
        private final Rectangle bounds;
    }
}