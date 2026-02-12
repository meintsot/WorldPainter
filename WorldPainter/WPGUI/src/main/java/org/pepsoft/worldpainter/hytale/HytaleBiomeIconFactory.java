package org.pepsoft.worldpainter.hytale;

import org.pepsoft.util.GUIUtils;
import org.pepsoft.worldpainter.ColourScheme;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Creates richer Hytale biome icons using textured block imagery plus small
 * overlays, instead of plain colour swatches.
 */
public final class HytaleBiomeIconFactory {
    private HytaleBiomeIconFactory() {
    }

    public static Icon getIcon(int biomeId, ColourScheme colourScheme) {
        return getIcon(biomeId, colourScheme, DEFAULT_ICON_SIZE);
    }

    public static Icon getIcon(int biomeId, ColourScheme colourScheme, int size) {
        final int scaledSize = Math.max(10, (int) Math.round(size * GUIUtils.getUIScale()));
        final String key = biomeId + ":" + scaledSize + ":" + System.identityHashCode(colourScheme);
        return ICON_CACHE.computeIfAbsent(key, ignored -> new ImageIcon(createIconImage(biomeId, colourScheme, scaledSize)));
    }

    private static BufferedImage createIconImage(int biomeId, ColourScheme colourScheme, int size) {
        ensureAssetsConfigured();

        final BufferedImage icon;
        if (biomeId == HytaleBiome.BIOME_AUTO) {
            icon = HytaleTerrain.GRASS.getScaledIcon(size, colourScheme);
            final BufferedImage image = copy(icon, size);
            final Graphics2D g2 = image.createGraphics();
            try {
                g2.setComposite(AlphaComposite.SrcOver.derive(0.40f));
                g2.setColor(new Color(0x808080));
                g2.fillRect(0, 0, size, size);
                drawBadge(g2, "A", 1, 1, size, new Color(0x3a3a3a), Color.WHITE);
                drawBorder(g2, size, new Color(0x9a9a9a));
            } finally {
                g2.dispose();
            }
            return image;
        }

        final HytaleBiome biome = HytaleBiome.getById(biomeId);
        if (biome == null) {
            final BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            final Graphics2D g2 = image.createGraphics();
            try {
                g2.setColor(new Color(0x808080));
                g2.fillRect(0, 0, size, size);
                drawBorder(g2, size, Color.DARK_GRAY);
            } finally {
                g2.dispose();
            }
            return image;
        }

        icon = chooseBaseTerrain(biome).getScaledIcon(size, colourScheme);
        final BufferedImage image = copy(icon, size);
        final Graphics2D g2 = image.createGraphics();
        try {
            g2.setComposite(AlphaComposite.SrcOver.derive(TINT_OVERLAY_ALPHA));
            g2.setColor(new Color(biome.getTint() & 0x00ffffff));
            g2.fillRect(0, 0, size, size);

            g2.setComposite(AlphaComposite.SrcOver);
            final Color identityColour = getDistinctBorderColour(biomeId);
            drawIdentityAccent(g2, size, identityColour);
            drawBadge(g2, getZoneBadge(biome), 1, 1, size, new Color(0xb0202020, true), Color.WHITE);
            drawBadge(g2, getCategoryBadge(biome), size - Math.max(9, size / 3), size - Math.max(9, size / 3), size,
                    new Color(0xb0202020, true), Color.WHITE);
            drawMarker(g2, biome, size);
            drawBorder(g2, size, identityColour);
        } finally {
            g2.dispose();
        }
        return image;
    }

    private static BufferedImage copy(BufferedImage source, int size) {
        final BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g2 = image.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g2.drawImage(source, 0, 0, size, size, null);
        } finally {
            g2.dispose();
        }
        return image;
    }

    private static void drawBadge(Graphics2D g2, String text, int x, int y, int size, Color background, Color foreground) {
        final int badgeSize = Math.max(9, size / 3);
        g2.setColor(background);
        g2.fillRoundRect(x, y, badgeSize, badgeSize, Math.max(4, badgeSize / 2), Math.max(4, badgeSize / 2));
        g2.setColor(new Color(0xe0ffffff, true));
        g2.drawRoundRect(x, y, badgeSize - 1, badgeSize - 1, Math.max(4, badgeSize / 2), Math.max(4, badgeSize / 2));

        final Font font = new Font("Dialog", Font.BOLD, Math.max(7, badgeSize - 3));
        g2.setFont(font);
        final FontMetrics fm = g2.getFontMetrics(font);
        final int textX = x + (badgeSize - fm.stringWidth(text)) / 2;
        final int textY = y + (badgeSize + fm.getAscent() - fm.getDescent()) / 2 - 1;
        g2.setColor(foreground);
        g2.drawString(text, textX, textY);
    }

    private static void drawIdentityAccent(Graphics2D g2, int size, Color colour) {
        final int accentWidth = Math.max(2, size / 8);
        g2.setComposite(AlphaComposite.SrcOver.derive(0.70f));
        g2.setColor(colour);
        g2.fillRect(size - accentWidth - 1, 1, accentWidth, size - 2);
        g2.setComposite(AlphaComposite.SrcOver);
    }

    private static void drawMarker(Graphics2D g2, HytaleBiome biome, int size) {
        final BufferedImage marker = getMarkerForBiome(biome);
        if (marker == null) {
            return;
        }
        final int markerSize = Math.max(8, size / 2);
        final int x = size - markerSize - 1;
        final int y = 1;
        g2.drawImage(marker, x, y, markerSize, markerSize, null);
    }

    private static BufferedImage getMarkerForBiome(HytaleBiome biome) {
        final String displayName = biome.getDisplayName().toLowerCase(Locale.ROOT);
        if (displayName.contains("portal")) {
            return loadMapMarker("Portal");
        }
        if (displayName.contains("temple")) {
            return loadMapMarker("Temple_Gateway");
        }
        if (displayName.contains("dungeon")) {
            return loadMapMarker("Prefab");
        }
        if (displayName.contains("mineshaft")) {
            return loadMapMarker("Campfire");
        }

        switch (biome.getCategory()) {
            case MISC:
                return loadMapMarker("Prefab");
            case ENCOUNTERS:
                return loadMapMarker("Warp");
            default:
                return null;
        }
    }

    private static BufferedImage loadMapMarker(String markerName) {
        ensureAssetsConfigured();
        if (assetsRoot == null) {
            return null;
        }
        return MARKER_CACHE.computeIfAbsent(markerName, name -> {
            final File markerFile = new File(assetsRoot, "Common" + File.separator + "UI" + File.separator + "WorldMap"
                    + File.separator + "MapMarkers" + File.separator + name + ".png");
            if (! markerFile.isFile()) {
                return null;
            }
            try {
                return ImageIO.read(markerFile);
            } catch (IOException e) {
                return null;
            }
        });
    }

    private static void drawBorder(Graphics2D g2, int size, Color colour) {
        g2.setColor(colour);
        g2.drawRect(0, 0, size - 1, size - 1);
    }

    private static Color getDistinctBorderColour(int biomeId) {
        final float hue = ((biomeId * 41) % 360) / 360.0f;
        return Color.getHSBColor(hue, 0.40f, 1.00f);
    }

    private static String getZoneBadge(HytaleBiome biome) {
        switch (biome.getCategory()) {
            case ZONE1:
                return "1";
            case ZONE2:
                return "2";
            case ZONE3:
                return "3";
            case ZONE4:
                return "4";
            case OCEAN:
                return "0";
            case MISC:
                return "M";
            case ENCOUNTERS:
                return "E";
            default:
                return "?";
        }
    }

    private static String getCategoryBadge(HytaleBiome biome) {
        switch (biome.getCategory()) {
            case ZONE1:
            case ZONE2:
            case ZONE3:
            case ZONE4:
                return "Z";
            case OCEAN:
                return "W";
            case MISC:
                return "M";
            case ENCOUNTERS:
                return "E";
            default:
                return "?";
        }
    }

    private static HytaleTerrain chooseBaseTerrain(HytaleBiome biome) {
        final String name = (biome.getName() + " " + biome.getDisplayName()).toLowerCase(Locale.ROOT);
        if (name.contains("ocean") || name.contains("river") || name.contains("shore")) {
            return HytaleTerrain.SAND;
        }
        if (name.contains("desert") || name.contains("savanna") || name.contains("oasis") || name.contains("scrub")) {
            return HytaleTerrain.SAND;
        }
        if (name.contains("swamp") || name.contains("fens")) {
            return HytaleTerrain.WET_GRASS;
        }
        if (name.contains("glacial") || name.contains("glacier") || name.contains("tundra") || name.contains("frozen") || name.contains("everfrost") || name.contains("iceberg")) {
            return HytaleTerrain.ICE;
        }
        if (name.contains("volcanic") || name.contains("volcano") || name.contains("magma") || name.contains("cinder") || name.contains("charred") || name.contains("burning") || name.contains("ashen")) {
            return HytaleTerrain.VOLCANIC_ROCK;
        }
        if (name.contains("cave") || name.contains("cavern") || name.contains("tunnel")) {
            return HytaleTerrain.SLATE;
        }
        if (name.contains("mountain") || name.contains("boulder")) {
            return HytaleTerrain.STONE;
        }
        if (name.contains("mushroom")) {
            return HytaleTerrain.DEEP_GRASS;
        }

        switch (biome.getCategory()) {
            case ZONE2:
                return HytaleTerrain.SAND;
            case ZONE3:
                return HytaleTerrain.ICE;
            case ZONE4:
                return HytaleTerrain.VOLCANIC_ROCK;
            case OCEAN:
                return HytaleTerrain.SAND;
            case MISC:
                return HytaleTerrain.CALCITE;
            case ENCOUNTERS:
                return HytaleTerrain.BASALT;
            case ZONE1:
            default:
                return HytaleTerrain.GRASS;
        }
    }

    private static void ensureAssetsConfigured() {
        if (assetsSearchDone) {
            return;
        }
        synchronized (ASSETS_LOCK) {
            if (assetsSearchDone) {
                return;
            }
            final File[] candidates = new File[] {
                    new File("HytaleAssets"),
                    new File("..", "HytaleAssets"),
                    new File(System.getProperty("user.dir"), "HytaleAssets"),
                    new File(System.getProperty("user.dir"), ".." + File.separator + "HytaleAssets"),
                    new File(System.getProperty("user.home"), "Desktop" + File.separator + "WorldPainter" + File.separator + "HytaleAssets")
            };
            for (File candidate : candidates) {
                final File blockTextures = new File(candidate, "Common" + File.separator + "BlockTextures");
                if (candidate.isDirectory() && blockTextures.isDirectory()) {
                    assetsRoot = candidate;
                    HytaleTerrain.setHytaleAssetsDir(candidate);
                    break;
                }
            }
            if (assetsRoot == null) {
                final String sysProp = System.getProperty("org.pepsoft.worldpainter.hytaleAssetsDir");
                if (sysProp != null) {
                    final File candidate = new File(sysProp);
                    final File blockTextures = new File(candidate, "Common" + File.separator + "BlockTextures");
                    if (candidate.isDirectory() && blockTextures.isDirectory()) {
                        assetsRoot = candidate;
                        HytaleTerrain.setHytaleAssetsDir(candidate);
                    }
                }
            }
            assetsSearchDone = true;
        }
    }

    private static final int DEFAULT_ICON_SIZE = 16;
    private static final float TINT_OVERLAY_ALPHA = 0.28f;

    private static final Object ASSETS_LOCK = new Object();
    private static volatile boolean assetsSearchDone;
    private static volatile File assetsRoot;

    private static final Map<String, Icon> ICON_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, BufferedImage> MARKER_CACHE = new ConcurrentHashMap<>();
}
