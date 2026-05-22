package org.pepsoft.worldpainter.cloud;

import org.pepsoft.worldpainter.cloud.tile.CloudTileProvider;
import org.pepsoft.worldpainter.cloud.tile.LocalTile;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

/**
 * Reusable 128×128 paint canvas widget. Renders a {@link LocalTile} as a grid of colored cells
 * (terrain value modulo palette length → color). Left-click paints with the current brush;
 * right-click cycles to the next brush color.
 */
public final class CloudPaintCanvas extends JPanel {

    private static final int CELL_PX = 6;
    private static final int SIZE = 128;
    private static final Color[] PALETTE = {
            Color.WHITE, Color.RED, Color.ORANGE, Color.YELLOW,
            Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA
    };

    private final LocalTile tile;
    private final CloudTileProvider provider;
    private byte currentBrush = 1;

    public CloudPaintCanvas(LocalTile tile, CloudTileProvider provider) {
        this.tile = tile;
        this.provider = provider;
        setPreferredSize(new Dimension(SIZE * CELL_PX, SIZE * CELL_PX));

        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { paintAt(e); }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseDragged(MouseEvent e) { paintAt(e); }
        });

        provider.addListener((t, cx, cy) -> SwingUtilities.invokeLater(this::repaint));
    }

    public byte getCurrentBrush() { return currentBrush; }
    public void setCurrentBrush(byte b) {
        this.currentBrush = (byte) (((b % PALETTE.length) + PALETTE.length) % PALETTE.length);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                byte v = tile.getTerrain(x, y);
                g.setColor(PALETTE[Math.abs(v) % PALETTE.length]);
                g.fillRect(x * CELL_PX, y * CELL_PX, CELL_PX, CELL_PX);
            }
        }
        g.setColor(Color.GRAY);
        for (int i = 0; i <= SIZE; i += 16) {
            g.drawLine(i * CELL_PX, 0, i * CELL_PX, SIZE * CELL_PX);
            g.drawLine(0, i * CELL_PX, SIZE * CELL_PX, i * CELL_PX);
        }
    }

    private void paintAt(MouseEvent e) {
        int cx = e.getX() / CELL_PX, cy = e.getY() / CELL_PX;
        if (cx < 0 || cx >= SIZE || cy < 0 || cy >= SIZE) return;
        if (e.getButton() == MouseEvent.BUTTON3) {
            setCurrentBrush((byte) (currentBrush + 1));
            return;
        }
        provider.setTerrain(0, 0, cx, cy, currentBrush);
        repaint();
    }
}
