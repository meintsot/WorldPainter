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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.pepsoft.worldpainter.Constants.TILE_SIZE;

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

    // Precomputed, offset-independent: only the tiles that hold real (non-Void)
    // data, plus the content bounding box of each map. Computed once because the
    // tiles don't change while the dialog is open, so drag repaints stay cheap.
    private final List<TileCell> targetCells;
    private final List<TileCell> sourceCells;
    private final Rectangle targetContentExtent;
    private final Rectangle sourceContentExtent;

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
        this.targetCells = buildCells(target, TARGET_FILL);
        this.sourceCells = buildCells(source, SOURCE_FILL);
        this.targetContentExtent = MergePreviewModel.contentExtent(target);
        this.sourceContentExtent = MergePreviewModel.contentExtent(source);
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
        Rectangle t = targetContentExtent;
        Rectangle sShifted = (sourceContentExtent == null)
            ? null
            : new Rectangle(sourceContentExtent.x + offset.x, sourceContentExtent.y + offset.y,
                            sourceContentExtent.width, sourceContentExtent.height);
        if ((t == null) && (sShifted == null)) {
            return new Rectangle(0, 0, 0, 0);
        }
        if (t == null) {
            return sShifted;
        }
        if (sShifted == null) {
            return t;
        }
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
            if (targetCells.isEmpty() && sourceCells.isEmpty()) {
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

            // Compute overlap set (target coords where source tiles land). Uses
            // tile existence — matching the merge's own collision rule — so the
            // count stays truthful even where Void-padded tiles overlap.
            Set<Point> overlapCoords = new HashSet<>();
            for (Tile s : source.getTiles()) {
                Point shifted = new Point(s.getX() + offset.x, s.getY() + offset.y);
                if (target.getTile(shifted) != null) overlapCoords.add(shifted);
            }

            // Target tiles (only those with real data; fully-Void tiles skipped)
            for (TileCell t : targetCells) {
                int px = originX + (t.tileX - ext.x) * pxPerTile;
                int py = originY + (t.tileY - ext.y) * pxPerTile;
                g2.setColor(t.colour);
                g2.fillRect(px, py, pxPerTile, pxPerTile);
            }

            // Source tiles (translucent overlay; red border on overlap)
            Composite prev = g2.getComposite();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.85f));
            for (TileCell s : sourceCells) {
                int sx = s.tileX + offset.x, sy = s.tileY + offset.y;
                int px = originX + (sx - ext.x) * pxPerTile;
                int py = originY + (sy - ext.y) * pxPerTile;
                g2.setColor(s.colour);
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
                offset.x, offset.y, targetCells.size(), sourceCells.size(), overlapCoords.size());
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
     * Build the drawable cells for one map: one {@link TileCell} per tile that
     * holds real (non-Void) data. Fully-Void tiles — which the importer creates
     * for non-imported chunks — are dropped so the preview shows the same shape
     * the editor canvas does. Done once per dialog; tiles don't change while it
     * is open.
     */
    private static List<TileCell> buildCells(Dimension dimension, Color tint) {
        List<TileCell> cells = new ArrayList<>();
        for (Tile tile : dimension.getTiles()) {
            float coverage = MergePreviewModel.coverage(tile);
            if (coverage <= 0f) {
                continue; // fully Void → not part of the visible map
            }
            cells.add(new TileCell(tile.getX(), tile.getY(), sampleTileColour(tile, tint, coverage)));
        }
        return cells;
    }

    /**
     * Pick a single representative colour for a tile by sampling the heights of
     * its non-Void pixels, then fade it by how much of the tile holds real data.
     * Cheap stand-in for proper terrain rendering: darker at low elevations,
     * brighter at high; fully-imported tiles are solid, sparse edge tiles faint.
     * Void pixels are skipped so non-imported areas neither tint the colour nor
     * make a barely-imported tile look full.
     */
    private static Color sampleTileColour(Tile tile, Color tint, float coverage) {
        // Sample a 16x16 grid across the 128x128 tile, ignoring Void pixels.
        float sum = 0;
        int samples = 0;
        for (int x = 0; x < TILE_SIZE; x += 8) {
            for (int y = 0; y < TILE_SIZE; y += 8) {
                if (tile.getBitLayerValue(org.pepsoft.worldpainter.layers.Void.INSTANCE, x, y)) {
                    continue;
                }
                sum += tile.getHeight(x, y);
                samples++;
            }
        }
        float norm = 0.5f;
        if (samples > 0) {
            float avg = sum / samples;
            float range = Math.max(1, tile.getMaxHeight() - tile.getMinHeight());
            norm = Math.max(0f, Math.min(1f, (avg - tile.getMinHeight()) / range));
        }
        // Mix tint with brightness based on normalised height.
        int r = (int) (tint.getRed()   * (0.5f + 0.5f * norm));
        int g = (int) (tint.getGreen() * (0.5f + 0.5f * norm));
        int b = (int) (tint.getBlue()  * (0.5f + 0.5f * norm));
        // Fade by coverage so partly-imported edge tiles read as lighter, with a
        // floor so a sparsely-imported tile is still visible.
        int a = (int) (tint.getAlpha() * Math.max(0.4f, coverage));
        return new Color(Math.min(255, r), Math.min(255, g), Math.min(255, b), Math.min(255, a));
    }

    /** A precomputed, offset-independent drawable cell for one content tile. */
    private static final class TileCell {
        final int tileX;
        final int tileY;
        final Color colour;

        TileCell(int tileX, int tileY, Color colour) {
            this.tileX = tileX;
            this.tileY = tileY;
            this.colour = colour;
        }
    }
}
