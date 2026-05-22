package org.pepsoft.worldpainter.cloud.examples;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.pepsoft.worldpainter.cloud.auth.AuthClient;
import org.pepsoft.worldpainter.cloud.auth.Session;
import org.pepsoft.worldpainter.cloud.tile.CloudTileProvider;
import org.pepsoft.worldpainter.cloud.tile.LocalTile;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.util.UUID;

/**
 * Standalone Swing demo for WPCloud. One window per JVM. Run two instances pointing at the same
 * world to demonstrate real-time collaborative painting.
 *
 * <p>Usage:
 * <pre>
 *   java -jar WPCloud-*-with-deps.jar [http-backend-url] [world-name]
 *   # Defaults:  http://localhost:8080  "Demo World"
 * </pre>
 */
public final class WPCloudDemo {

    private static final int CELL_PX = 6;          // 128 * 6 = 768 px canvas
    private static final int SIZE = 128;
    private static final Color[] PALETTE = {
            Color.WHITE, Color.RED, Color.ORANGE, Color.YELLOW,
            Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA
    };

    public static void main(String[] args) throws Exception {
        URI httpBase = URI.create(args.length > 0 ? args[0] : "http://localhost:8080");
        URI wsBase   = URI.create(httpBase.toString().replaceFirst("^http", "ws") + "/ws");

        String displayName = JOptionPane.showInputDialog(
                null, "Display name:", "WPCloud Demo Login", JOptionPane.PLAIN_MESSAGE);
        if (displayName == null || displayName.isBlank()) System.exit(0);

        AuthClient auth = new AuthClient(httpBase);
        Session session = auth.login(displayName);

        // List existing worlds; let user pick or create new.
        java.util.List<String[]> existing = listWorlds(httpBase, session.token());
        String[] choices = new String[existing.size() + 1];
        for (int i = 0; i < existing.size(); i++) {
            choices[i] = existing.get(i)[1];  // name
        }
        choices[existing.size()] = "+ New World...";

        String picked = (String) JOptionPane.showInputDialog(
                null, "Pick a world:", "WPCloud Demo",
                JOptionPane.PLAIN_MESSAGE, null, choices, choices[0]);
        if (picked == null) System.exit(0);

        UUID worldId;
        String worldName;
        if (picked.equals("+ New World...")) {
            worldName = JOptionPane.showInputDialog(
                    null, "New world name:", "Demo World " + System.currentTimeMillis());
            if (worldName == null || worldName.isBlank()) System.exit(0);
            worldId = findOrCreateWorld(httpBase, session.token(), worldName);
        } else {
            worldName = picked;
            UUID resolved = null;
            for (String[] w : existing) {
                if (w[1].equals(picked)) { resolved = UUID.fromString(w[0]); break; }
            }
            if (resolved == null) throw new IllegalStateException("World " + picked + " disappeared");
            worldId = resolved;
        }

        CloudTileProvider provider = new CloudTileProvider(wsBase, session, worldId);
        provider.connect();
        waitUntil(provider::isOpen, 5_000);

        LocalTile tile = provider.getTile(0, 0);

        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("WPCloud Demo — " + displayName + " — " + worldName);
            Canvas canvas = new Canvas(tile, provider);
            canvas.setPreferredSize(new Dimension(SIZE * CELL_PX, SIZE * CELL_PX));

            canvas.addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { canvas.paintAt(e); }
            });
            canvas.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                @Override public void mouseDragged(MouseEvent e) { canvas.paintAt(e); }
            });

            provider.addListener((t, cx, cy) -> SwingUtilities.invokeLater(canvas::repaint));

            frame.setContentPane(canvas);
            frame.pack();
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setVisible(true);
        });

        Runtime.getRuntime().addShutdownHook(new Thread(provider::close));
    }

    private static class Canvas extends JPanel {
        final LocalTile tile;
        final CloudTileProvider provider;
        byte currentBrush = 1;

        Canvas(LocalTile tile, CloudTileProvider provider) {
            this.tile = tile;
            this.provider = provider;
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

        void paintAt(MouseEvent e) {
            int cx = e.getX() / CELL_PX, cy = e.getY() / CELL_PX;
            if (cx < 0 || cx >= SIZE || cy < 0 || cy >= SIZE) return;
            if (e.getButton() == MouseEvent.BUTTON3) {
                currentBrush = (byte) ((currentBrush + 1) % PALETTE.length);
                return;
            }
            provider.setTerrain(0, 0, cx, cy, currentBrush);
            repaint();
        }
    }

    private static UUID findOrCreateWorld(URI baseUri, String token, String name) throws Exception {
        try (CloseableHttpClient http = HttpClients.createDefault()) {
            HttpGet list = new HttpGet(baseUri.resolve("/v1/worlds"));
            list.setHeader("Authorization", "Bearer " + token);
            UUID found = http.execute(list, response -> {
                JsonNode body = new ObjectMapper().readTree(EntityUtils.toString(response.getEntity()));
                for (JsonNode w : body.get("worlds")) {
                    if (name.equals(w.get("name").asText())) {
                        return UUID.fromString(w.get("id").asText());
                    }
                }
                return null;
            });
            if (found != null) return found;

            HttpPost post = new HttpPost(baseUri.resolve("/v1/worlds"));
            post.setHeader("Authorization", "Bearer " + token);
            post.setEntity(new StringEntity(
                    "{\"name\":\"" + name.replace("\"", "\\\"") + "\",\"platform\":\"hytale\"}",
                    ContentType.APPLICATION_JSON));
            return http.execute(post, response -> {
                JsonNode body = new ObjectMapper().readTree(EntityUtils.toString(response.getEntity()));
                return UUID.fromString(body.get("id").asText());
            });
        }
    }

    private static void waitUntil(java.util.function.BooleanSupplier cond, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!cond.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new RuntimeException("WS handshake timed out after " + timeoutMs + "ms");
            }
            Thread.sleep(50);
        }
    }

    private static java.util.List<String[]> listWorlds(URI baseUri, String token) throws Exception {
        try (CloseableHttpClient http = HttpClients.createDefault()) {
            HttpGet get = new HttpGet(baseUri.resolve("/v1/worlds"));
            get.setHeader("Authorization", "Bearer " + token);
            return http.execute(get, response -> {
                JsonNode body = new ObjectMapper().readTree(EntityUtils.toString(response.getEntity()));
                java.util.List<String[]> out = new java.util.ArrayList<>();
                for (JsonNode w : body.get("worlds")) {
                    out.add(new String[]{ w.get("id").asText(), w.get("name").asText() });
                }
                return out;
            });
        }
    }
}
