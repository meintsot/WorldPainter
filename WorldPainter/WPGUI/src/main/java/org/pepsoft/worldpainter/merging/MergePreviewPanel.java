package org.pepsoft.worldpainter.merging;

import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Tile;

import javax.swing.*;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Interactive preview for TP-46 "Import Map and Merge". Draws the target
 * dimension's tile bounding boxes and the source dimension's tile bounding
 * boxes (offset by the user's chosen placement). Lets the user click-drag
 * the imported map to nudge its position; the result snaps to the tile grid.
 *
 * <p>Tile colour is sampled from each tile's average heightmap value to give
 * a rough sense of terrain shape; this is not the full WP terrain render, but
 * it's enough to align maps by eye.</p>
 */
public class MergePreviewPanel extends JPanel {

    private static final int MIN_PIXELS_PER_TILE = 6;
    private static final int MAX_PIXELS_PER_TILE = 40;
    private static final Color TARGET_FILL = new Color(70, 130, 180, 170);   // steel blue
    private static final Color SOURCE_FILL = new Color(60, 160, 90, 170);    // green
    private static final Color OVERLAP_BORDER = new Color(220, 40, 40);
    private static final Color GRID = new Color(255, 255, 255, 40);
    private static final Color TEXT_BG = new Color(0, 0, 0, 140);

    private final Dimension target;
    private final Dimension source;
    private final Consumer<Point> offsetListener;
    private Point offset;

    // Drag state
    private boolean dragging = false;
    private Point dragStartScreen;
    private Point offsetAtDragStart;

    public MergePreviewPanel(Dimension target, Dimension source, Point initialOffset,
                             Consumer<Point> offsetListener) {
        this.target = target;
        this.source = source;
        this.offset = new Point(initialOffset);
        this.offsetListener = offsetListener;
        setBackground(new Color(20, 20, 28));
        setPreferredSize(new java.awt.Dimension(420, 280));
        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1) return;
                dragging = true;
                dragStartScreen = e.getPoint();
                offsetAtDragStart = new Point(offset);
            }
            @Override public void mouseDragged(MouseEvent e) {
                if (!dragging) return;
                int pxPerTile = pixelsPerTile();
                if (pxPerTile <= 0) return;
                int dxTiles = Math.round((float) (e.getX() - dragStartScreen.x) / pxPerTile);
                int dyTiles = Math.round((float) (e.getY() - dragStartScreen.y) / pxPerTile);
                Point newOffset = new Point(offsetAtDragStart.x + dxTiles, offsetAtDragStart.y + dyTiles);
                if (!newOffset.equals(offset)) {
                    offset = newOffset;
                    repaint();
                    if (offsetListener != null) offsetListener.accept(new Point(offset));
                }
            }
            @Override public void mouseReleased(MouseEvent e) {
                dragging = false;
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    public Point getOffset() {
        return new Point(offset);
    }

    public void setOffset(Point newOffset) {
        if (!newOffset.equals(offset)) {
            offset = new Point(newOffset);
            repaint();
            if (offsetListener != null) offsetListener.accept(new Point(offset));
        }
    }

    private Rectangle combinedTileExtent() {
        Rectangle t = target.getExtent();
        Rectangle s = source.getExtent();
        Rectangle sShifted = new Rectangle(s.x + offset.x, s.y + offset.y, s.width, s.height);
        return t.union(sShifted);
    }

    private int pixelsPerTile() {
        Rectangle ext = combinedTileExtent();
        if (ext.width == 0 || ext.height == 0) return MAX_PIXELS_PER_TILE;
        int avail_w = Math.max(20, getWidth() - 16);
        int avail_h = Math.max(20, getHeight() - 36);
        int px = Math.min(avail_w / ext.width, avail_h / ext.height);
        return Math.max(MIN_PIXELS_PER_TILE, Math.min(MAX_PIXELS_PER_TILE, px));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        try {
            if (target.getTileCount() == 0 && source.getTileCount() == 0) {
                g2.setColor(Color.LIGHT_GRAY);
                g2.drawString("No tiles to preview", 10, 20);
                return;
            }
            Rectangle ext = combinedTileExtent();
            int pxPerTile = pixelsPerTile();
            int totalW = ext.width * pxPerTile;
            int totalH = ext.height * pxPerTile;
            int originX = (getWidth() - totalW) / 2;
            int originY = 8;

            // Compute overlap set (target coords where source tiles land)
            Set<Point> overlapCoords = new HashSet<>();
            for (Tile s : source.getTiles()) {
                Point shifted = new Point(s.getX() + offset.x, s.getY() + offset.y);
                if (target.getTile(shifted) != null) overlapCoords.add(shifted);
            }

            // Target tiles
            for (Tile t : target.getTiles()) {
                int px = originX + (t.getX() - ext.x) * pxPerTile;
                int py = originY + (t.getY() - ext.y) * pxPerTile;
                Color fill = sampleTileColour(t, TARGET_FILL);
                g2.setColor(fill);
                g2.fillRect(px, py, pxPerTile, pxPerTile);
            }

            // Source tiles (translucent overlay; red border on overlap)
            Composite prev = g2.getComposite();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.85f));
            for (Tile s : source.getTiles()) {
                int sx = s.getX() + offset.x, sy = s.getY() + offset.y;
                int px = originX + (sx - ext.x) * pxPerTile;
                int py = originY + (sy - ext.y) * pxPerTile;
                Color fill = sampleTileColour(s, SOURCE_FILL);
                g2.setColor(fill);
                g2.fillRect(px, py, pxPerTile, pxPerTile);
                if (overlapCoords.contains(new Point(sx, sy))) {
                    g2.setComposite(AlphaComposite.SrcOver);
                    g2.setColor(OVERLAP_BORDER);
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawRect(px + 1, py + 1, pxPerTile - 2, pxPerTile - 2);
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.85f));
                }
            }
            g2.setComposite(prev);

            // Grid
            g2.setColor(GRID);
            for (int tx = 0; tx <= ext.width; tx++) {
                g2.drawLine(originX + tx * pxPerTile, originY, originX + tx * pxPerTile, originY + totalH);
            }
            for (int ty = 0; ty <= ext.height; ty++) {
                g2.drawLine(originX, originY + ty * pxPerTile, originX + totalW, originY + ty * pxPerTile);
            }

            // Footer status
            String info = String.format("Offset: (%d, %d)  •  Target: %d tiles  •  Source: %d tiles  •  Overlap: %d",
                offset.x, offset.y, target.getTileCount(), source.getTileCount(), overlapCoords.size());
            g2.setColor(TEXT_BG);
            g2.fillRect(0, getHeight() - 22, getWidth(), 22);
            g2.setColor(Color.WHITE);
            g2.drawString(info, 8, getHeight() - 7);

            // Legend
            int lx = getWidth() - 180, ly = 6;
            g2.setColor(TARGET_FILL); g2.fillRect(lx, ly, 12, 12);
            g2.setColor(Color.WHITE); g2.drawString("current", lx + 16, ly + 11);
            g2.setColor(SOURCE_FILL); g2.fillRect(lx + 70, ly, 12, 12);
            g2.setColor(Color.WHITE); g2.drawString("imported", lx + 86, ly + 11);
        } finally {
            g2.dispose();
        }
    }

    /**
     * Pick a single representative colour for a tile by sampling its heightmap.
     * Cheap stand-in for proper terrain rendering: blueish at low elevations,
     * greenish-brown for mid, off-white at high elevations.
     */
    private static Color sampleTileColour(Tile tile, Color tint) {
        // Sample a 4x4 grid of the 128x128 tile to estimate average height.
        float sum = 0;
        int samples = 16;
        for (int sx = 0; sx < 4; sx++) {
            for (int sy = 0; sy < 4; sy++) {
                int x = 16 + sx * 32, y = 16 + sy * 32;
                sum += tile.getHeight(x, y);
            }
        }
        float avg = sum / samples;
        float range = Math.max(1, tile.getMaxHeight() - tile.getMinHeight());
        float norm = Math.max(0f, Math.min(1f, (avg - tile.getMinHeight()) / range));
        // Mix tint with brightness based on normalised height.
        int r = (int) (tint.getRed()   * (0.5f + 0.5f * norm));
        int g = (int) (tint.getGreen() * (0.5f + 0.5f * norm));
        int b = (int) (tint.getBlue()  * (0.5f + 0.5f * norm));
        return new Color(Math.min(255, r), Math.min(255, g), Math.min(255, b), tint.getAlpha());
    }
}
