package org.pepsoft.worldpainter.cloud;

import org.pepsoft.worldpainter.cloud.auth.CloudSession;
import org.pepsoft.worldpainter.cloud.auth.Session;
import org.pepsoft.worldpainter.cloud.tile.CloudTileProvider;
import org.pepsoft.worldpainter.cloud.tile.LocalTile;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URI;
import java.util.UUID;

/**
 * Dedicated editor window for a single cloud world. Phase 0c-2 is single-tile (0,0) only;
 * pan/zoom and multi-tile editing arrive in Phase 0c-3.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>Constructed with the world id + display name.</li>
 *   <li>{@link #connectAndShow} performs the WebSocket handshake (showing a "connecting…"
 *       splash), then displays the canvas.</li>
 *   <li>On window close, the {@link CloudTileProvider} is closed and resources released.</li>
 * </ol>
 */
public final class CloudEditorFrame extends JFrame {

    private static final URI DEFAULT_BACKEND_HTTP = URI.create(
            System.getProperty("worldpainter.cloud.backend", "http://localhost:8080"));

    private final UUID worldId;
    private final String worldName;
    private CloudTileProvider provider;
    private final JLabel statusLabel = new JLabel(" ");

    public CloudEditorFrame(UUID worldId, String worldName) {
        super(makeTitle(worldName));
        this.worldId = worldId;
        this.worldName = worldName;
        setLayout(new BorderLayout());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel splash = new JPanel(new GridBagLayout());
        splash.setPreferredSize(new Dimension(640, 480));
        splash.add(new JLabel("Connecting to " + worldName + "…"));
        add(splash, BorderLayout.CENTER);

        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        add(statusLabel, BorderLayout.SOUTH);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                if (provider != null) {
                    try { provider.close(); } catch (Exception ignored) {}
                }
            }
        });

        pack();
        setLocationRelativeTo(null);
    }

    private static String makeTitle(String worldName) {
        return "WorldPainter Cloud — " + worldName;
    }

    /**
     * Connect to the backend, fetch the initial tile, replace the splash with the paint canvas,
     * then show the frame. Returns immediately; the actual handshake runs on a SwingWorker.
     */
    public void connectAndShow() {
        Session session = CloudSession.getInstance().current()
                .orElseThrow(() -> new IllegalStateException("Not signed in"));
        URI wsUri = URI.create(DEFAULT_BACKEND_HTTP.toString().replaceFirst("^http", "ws") + "/ws");

        setVisible(true);
        statusLabel.setText("Connecting…");

        SwingWorker<LocalTile, Void> worker = new SwingWorker<>() {
            @Override
            protected LocalTile doInBackground() throws Exception {
                provider = new CloudTileProvider(wsUri, session, worldId);
                provider.connect();
                long deadline = System.currentTimeMillis() + 8_000;
                while (!provider.isOpen()) {
                    if (System.currentTimeMillis() > deadline) {
                        throw new RuntimeException("Connection handshake timed out");
                    }
                    Thread.sleep(50);
                }
                return provider.getTile(0, 0);
            }

            @Override
            protected void done() {
                try {
                    LocalTile tile = get();
                    CloudPaintCanvas canvas = new CloudPaintCanvas(tile, provider);

                    Container content = getContentPane();
                    content.removeAll();
                    content.add(canvas, BorderLayout.CENTER);
                    content.add(statusLabel, BorderLayout.SOUTH);
                    statusLabel.setText("Connected — left-click paints, right-click cycles brush");

                    pack();
                    setLocationRelativeTo(null);
                    repaint();
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    statusLabel.setForeground(new Color(180, 0, 0));
                    statusLabel.setText("Connect failed: " + cause.getMessage());
                }
            }
        };
        worker.execute();
    }
}
