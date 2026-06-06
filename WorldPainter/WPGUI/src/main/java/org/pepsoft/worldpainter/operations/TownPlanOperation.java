package org.pepsoft.worldpainter.operations;

import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.MaterialSelector;
import org.pepsoft.worldpainter.Overlay;
import org.pepsoft.worldpainter.Platform;
import org.pepsoft.worldpainter.WorldPainter;
import org.pepsoft.worldpainter.layers.TownLayout;
import org.pepsoft.worldpainter.layers.exporters.TownLayoutSettings;
import org.pepsoft.worldpainter.townplan.TownPlanPlacement;
import org.pepsoft.worldpainter.townplan.TownPlanStamper;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.beans.PropertyVetoException;
import java.io.File;

/**
 * Palette tool for placing a town-plan image on the map (drag to move, corner handles to scale,
 * cyan knob to rotate) and stamping its footprint into the {@link TownLayout} layer.
 */
public class TownPlanOperation extends AbstractOperation {
    public TownPlanOperation(WorldPainter view) {
        super("Town Plan", "Place a town-plan image and stamp its footprint", "townplan");
        setView(view);
    }

    @Override
    public JPanel getOptionsPanel() {
        if (optionsPanel == null) {
            optionsPanel = buildOptionsPanel();
        }
        return optionsPanel;
    }

    @Override
    public void interrupt() {
        // No continuous operation in progress; nothing to interrupt.
        activeHandle = TownPlanPlacement.Handle.NONE;
    }

    @Override
    protected void activate() throws PropertyVetoException {
        final WorldPainter view = (WorldPainter) getView();
        view.addMouseListener(mouseHandler);
        view.addMouseMotionListener(mouseHandler);
        if (overlay != null) {
            // Re-show the placement image (it is hidden while another tool is active).
            overlay.setEnabled(true);
            view.setPlacementOverlay(overlay);
            view.repaint();
        }
        refreshBlockButtonLabel();
    }

    @Override
    protected void deactivate() {
        final WorldPainter view = (WorldPainter) getView();
        view.removeMouseListener(mouseHandler);
        view.removeMouseMotionListener(mouseHandler);
        // Hide the placement image and its handles while another tool is active. The stamped Town Layout
        // footprint stays visible, since it is a layer rather than the overlay.
        if (overlay != null) {
            overlay.setEnabled(false);
        }
        view.setPlacementOverlay(null);
        view.repaint();
    }

    private JPanel buildOptionsPanel() {
        final JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        final JButton selectButton = new JButton("Select image…");
        selectButton.addActionListener(e -> selectImage());
        thresholdSlider = new JSlider(0, 255, 128);
        invertCheckBox = new JCheckBox("Invert (light = footprint)");
        // Exact 90-degree (N/E/S/W) rotation. Free rotation is still available via the cyan knob (hold Shift
        // while dragging the knob to snap to 90 degrees).
        final JButton rotateCcwButton = new JButton("CCW 90°");
        rotateCcwButton.setToolTipText("Rotate 90° counter-clockwise (snaps to N/E/S/W)");
        rotateCcwButton.addActionListener(e -> rotateBy90(-1));
        final JButton rotateCwButton = new JButton("CW 90°");
        rotateCwButton.setToolTipText("Rotate 90° clockwise (snaps to N/E/S/W)");
        rotateCwButton.addActionListener(e -> rotateBy90(1));
        final JButton snapButton = new JButton("Snap 90°");
        snapButton.setToolTipText("Snap the current rotation to the nearest N/E/S/W");
        snapButton.addActionListener(e -> rotateBy90(0));
        final JPanel rotatePanel = new JPanel();
        rotatePanel.add(rotateCcwButton);
        rotatePanel.add(rotateCwButton);
        rotatePanel.add(snapButton);
        final JButton stampButton = new JButton("Stamp");
        stampButton.addActionListener(e -> stamp());
        panel.add(selectButton);
        panel.add(new JLabel("Brightness threshold"));
        panel.add(thresholdSlider);
        panel.add(invertCheckBox);
        panel.add(new JLabel("Rotation (free-drag the knob; buttons snap to 90°)"));
        panel.add(rotatePanel);
        blockButton = new JButton();
        blockButton.setToolTipText("Choose the block the footprint exports as");
        blockButton.addActionListener(e -> chooseBlock());
        refreshBlockButtonLabel();
        panel.add(blockButton);
        panel.add(stampButton);
        eraseToggle = new JToggleButton("Erase footprint");
        eraseToggle.setToolTipText("When on, drag on the map to erase the stamped footprint under the brush");
        eraseRadiusSlider = new JSlider(1, 64, 8);
        eraseRadiusSlider.setToolTipText("Erase brush radius, in blocks");
        panel.add(new JLabel("Erase brush radius"));
        panel.add(eraseRadiusSlider);
        panel.add(eraseToggle);
        return panel;
    }

    private void chooseBlock() {
        final WorldPainter view = (WorldPainter) getView();
        final Dimension dimension = getDimension();
        if (dimension == null) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        final MaterialSelector selector = new MaterialSelector();
        final Platform platform = dimension.getWorld().getPlatform();
        if (platform != null) {
            selector.setPlatform(platform);
        }
        selector.setMaterial(currentExportBlock(dimension));
        final int result = JOptionPane.showConfirmDialog(view, selector, "Select export block",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            final Material chosen = selector.getMaterial();
            if (chosen != null) {
                final TownLayoutSettings settings = currentOrNewSettings(dimension);
                settings.setBlock(chosen);
                dimension.setLayerSettings(TownLayout.INSTANCE, settings);
                refreshBlockButtonLabel();
            }
        }
    }

    /** The export block currently configured for the dimension, or the (stone) default if none is stored. */
    private Material currentExportBlock(Dimension dimension) {
        final TownLayoutSettings settings = (dimension != null)
                ? (TownLayoutSettings) dimension.getLayerSettings(TownLayout.INSTANCE) : null;
        return (settings != null) ? settings.getBlock() : new TownLayoutSettings().getBlock();
    }

    /** The dimension's existing TownLayout settings, or a fresh default instance if none is stored. */
    private TownLayoutSettings currentOrNewSettings(Dimension dimension) {
        final TownLayoutSettings settings = (TownLayoutSettings) dimension.getLayerSettings(TownLayout.INSTANCE);
        return (settings != null) ? settings : new TownLayoutSettings();
    }

    private void refreshBlockButtonLabel() {
        if (blockButton == null) {
            return;
        }
        final Material block = currentExportBlock(getDimension());
        final String name = (block == null) ? "(none)"
                : ((block.simpleName != null) ? block.simpleName : String.valueOf(block));
        blockButton.setText("Export block: " + name);
    }

    /**
     * Rotate the placed image by an exact 90-degree step, snapping to the nearest cardinal first so the result
     * is always a clean N/E/S/W angle. {@code direction}: -1 = counter-clockwise, +1 = clockwise, 0 = snap only.
     */
    private void rotateBy90(int direction) {
        final WorldPainter view = (WorldPainter) getView();
        if ((overlay == null) || (overlay.getImage() == null)) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        final int imgW = overlay.getImage().getWidth(), imgH = overlay.getImage().getHeight();
        final double target = TownPlanPlacement.snapTo90(overlay.getRotation()) + direction * 90.0;
        final double[] s = TownPlanPlacement.rotateTo(overlay.getOffsetX(), overlay.getOffsetY(),
                overlay.getScale(), overlay.getRotation(), target, imgW, imgH);
        overlay.setOffsetX((int) Math.round(s[0]));
        overlay.setOffsetY((int) Math.round(s[1]));
        overlay.setRotation((float) s[3]);
        view.repaint();
    }

    private void selectImage() {
        final WorldPainter view = (WorldPainter) getView();
        final Dimension dimension = getDimension();
        if (dimension == null) {
            return;
        }
        final JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(view) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        final File file = chooser.getSelectedFile();
        final BufferedImage image;
        try {
            image = ImageIO.read(file);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(view, "Could not read image:\n" + ex.getMessage(), "Town Plan", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (image == null) {
            JOptionPane.showMessageDialog(view, "Not a supported image file.", "Town Plan", JOptionPane.ERROR_MESSAGE);
            return;
        }
        // Replace any previously-placed town-plan overlay rather than stacking them.
        if (overlay != null) {
            final int oldIndex = dimension.getOverlays().indexOf(overlay);
            if (oldIndex >= 0) {
                dimension.removeOverlay(oldIndex);
            }
        }
        overlay = new Overlay(file);
        overlay.setImage(image);
        overlay.setTransparency(0.5f);
        // Scale the image to fit comfortably within the current view (~60% of the more constraining view
        // dimension) so the whole plan and its handles are visible immediately, instead of filling the screen
        // at 1 block per pixel.
        final double pixelsPerBlock = Math.pow(2.0, view.getZoom());
        final double viewBlocksWide = view.getWidth() / pixelsPerBlock;
        final double viewBlocksHigh = view.getHeight() / pixelsPerBlock;
        final double fitScale = 0.6 * Math.min(viewBlocksWide / image.getWidth(), viewBlocksHigh / image.getHeight());
        final float scale = (float) Math.max(fitScale, 0.01);
        overlay.setScale(scale);
        // Centre the image (in its scaled, world-block size) on the current view centre.
        final Point center = view.getViewCentreInWorldCoords();
        overlay.setOffsetX(center.x - Math.round(image.getWidth() * scale / 2.0f));
        overlay.setOffsetY(center.y - Math.round(image.getHeight() * scale / 2.0f));
        overlay.setEnabled(true);
        dimension.addOverlay(overlay);
        // Turn on overlay drawing for this dimension, otherwise the placed image (and its placement handles)
        // would be invisible, since overlays are off by default.
        dimension.setOverlaysEnabled(true);
        view.setPlacementOverlay(overlay);
        view.repaint();
    }

    private void stamp() {
        final WorldPainter view = (WorldPainter) getView();
        final Dimension dimension = getDimension();
        if ((overlay == null) || (overlay.getImage() == null) || (dimension == null)) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        final BufferedImage image = overlay.getImage();
        final double scale = overlay.getScale();
        final double rot = overlay.getRotation();
        final int ox = overlay.getOffsetX(), oz = overlay.getOffsetY();
        final java.awt.geom.Point2D.Double[] corners = {
                TownPlanPlacement.pixelToWorld(0, 0, ox, oz, scale, rot),
                TownPlanPlacement.pixelToWorld(image.getWidth(), 0, ox, oz, scale, rot),
                TownPlanPlacement.pixelToWorld(image.getWidth(), image.getHeight(), ox, oz, scale, rot),
                TownPlanPlacement.pixelToWorld(0, image.getHeight(), ox, oz, scale, rot)
        };
        double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (java.awt.geom.Point2D.Double c : corners) {
            minX = Math.min(minX, c.x);
            minZ = Math.min(minZ, c.y);
            maxX = Math.max(maxX, c.x);
            maxZ = Math.max(maxZ, c.y);
        }
        final Rectangle area = new Rectangle((int) Math.floor(minX), (int) Math.floor(minZ),
                (int) Math.ceil(maxX - minX) + 1, (int) Math.ceil(maxZ - minZ) + 1);
        final int count = TownPlanStamper.stamp(dimension, image, ox, oz, scale, rot, null,
                thresholdSlider.getValue(), invertCheckBox.isSelected(), area);
        dimension.armSavePoint();
        view.repaint();
        JOptionPane.showMessageDialog(view, "Stamped " + count + " columns into the Town Layout layer.", "Town Plan", JOptionPane.INFORMATION_MESSAGE);
    }

    private boolean isErasing() {
        return (eraseToggle != null) && eraseToggle.isSelected();
    }

    /** Erase the stamped footprint under {@code world} using the current brush radius, and repaint. */
    private void eraseAt(Point world) {
        final Dimension dimension = getDimension();
        if (dimension == null) {
            return;
        }
        TownPlanStamper.erase(dimension, world.x, world.y, eraseRadiusSlider.getValue());
        ((WorldPainter) getView()).repaint();
    }

    private final MouseAdapter mouseHandler = new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
            final WorldPainter view = (WorldPainter) getView();
            if (isErasing()) {
                eraseAt(view.viewToWorld(e.getPoint()));
                return;
            }
            if ((overlay == null) || (overlay.getImage() == null)) {
                return;
            }
            final Point w = view.viewToWorld(e.getPoint());
            final int imgW = overlay.getImage().getWidth(), imgH = overlay.getImage().getHeight();
            final double handleR = 6.0 / Math.pow(2.0, view.getZoom());
            activeHandle = TownPlanPlacement.hitTest(w.x, w.y, overlay.getOffsetX(), overlay.getOffsetY(),
                    overlay.getScale(), overlay.getRotation(), imgW, imgH, handleR);
            lastWorld = w;
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            final WorldPainter view = (WorldPainter) getView();
            if (isErasing()) {
                eraseAt(view.viewToWorld(e.getPoint()));
                return;
            }
            if ((overlay == null) || (overlay.getImage() == null)
                    || (activeHandle == TownPlanPlacement.Handle.NONE) || (lastWorld == null)) {
                return;
            }
            final Point w = view.viewToWorld(e.getPoint());
            final int imgW = overlay.getImage().getWidth(), imgH = overlay.getImage().getHeight();
            switch (activeHandle) {
                case BODY: {
                    final double[] s = TownPlanPlacement.applyMove(overlay.getOffsetX(), overlay.getOffsetY(),
                            w.x - lastWorld.x, w.y - lastWorld.y);
                    overlay.setOffsetX((int) Math.round(s[0]));
                    overlay.setOffsetY((int) Math.round(s[1]));
                    break;
                }
                case ROTATE: {
                    // Hold Shift while dragging to snap the rotation to the nearest 90 degrees (N/E/S/W).
                    final double[] s = TownPlanPlacement.applyRotate(w.x, w.y, overlay.getOffsetX(),
                            overlay.getOffsetY(), overlay.getScale(), overlay.getRotation(), imgW, imgH, e.isShiftDown());
                    overlay.setOffsetX((int) Math.round(s[0]));
                    overlay.setOffsetY((int) Math.round(s[1]));
                    overlay.setRotation((float) s[3]);
                    break;
                }
                case NW: case NE: case SW: case SE: {
                    final double[] s = TownPlanPlacement.applyScale(w.x, w.y, overlay.getOffsetX(),
                            overlay.getOffsetY(), overlay.getScale(), overlay.getRotation(), imgW, imgH);
                    overlay.setOffsetX((int) Math.round(s[0]));
                    overlay.setOffsetY((int) Math.round(s[1]));
                    overlay.setScale((float) s[2]);
                    break;
                }
                default:
                    break;
            }
            lastWorld = w;
            view.repaint();
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (isErasing()) {
                final Dimension dimension = getDimension();
                if (dimension != null) {
                    dimension.armSavePoint();
                }
            }
            activeHandle = TownPlanPlacement.Handle.NONE;
        }
    };

    private JPanel optionsPanel;
    private JSlider thresholdSlider;
    private JCheckBox invertCheckBox;
    private JToggleButton eraseToggle;
    private JSlider eraseRadiusSlider;
    private JButton blockButton;
    private Overlay overlay;
    private Point lastWorld;
    private TownPlanPlacement.Handle activeHandle = TownPlanPlacement.Handle.NONE;
}
