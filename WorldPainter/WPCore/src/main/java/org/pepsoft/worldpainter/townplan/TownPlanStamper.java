package org.pepsoft.worldpainter.townplan;

import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.layers.TownLayout;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;

/**
 * Pure conversion from a placed town-plan image to the set of world columns that form its footprint,
 * plus a thin adapter to write those columns into a {@link Dimension}'s {@link TownLayout} layer.
 *
 * <p>Transform convention (must match the on-map rendering in the GUI): the image's top-left pixel
 * (0,0) sits at world block ({@code originX}, {@code originZ}); the image is scaled by
 * {@code blocksPerPixel} and rotated {@code rotationDeg} degrees clockwise about that origin.
 */
public final class TownPlanStamper {
    private TownPlanStamper() {
    }

    /**
     * Compute the world columns covered by the dark (or, if {@code invert}, light) parts of the image.
     *
     * @param image          the plan image (ARGB or RGB).
     * @param originX        world block X of image pixel (0,0).
     * @param originZ        world block Z of image pixel (0,0).
     * @param blocksPerPixel world blocks per image pixel (image scale). Must be &gt; 0.
     * @param rotationDeg    clockwise rotation of the image on the map, in degrees.
     * @param cropPx         optional crop rectangle in image-pixel space; only pixels inside it count. May be null.
     * @param threshold      brightness threshold 0..255, compared against per-pixel brightness {@code (r + g + b) / 3}.
     * @param invert         if false, pixels darker than the threshold are selected; if true, lighter.
     * @param worldArea      the world block area to scan.
     * @return the set of selected world columns (Point.x = world X, Point.y = world Z).
     */
    public static Set<Point> computeFootprint(BufferedImage image, double originX, double originZ,
                                              double blocksPerPixel, double rotationDeg, Rectangle cropPx,
                                              int threshold, boolean invert, Rectangle worldArea) {
        if (blocksPerPixel <= 0.0) {
            throw new IllegalArgumentException("blocksPerPixel must be > 0");
        }
        final Set<Point> result = new HashSet<>();
        final int imgW = image.getWidth(), imgH = image.getHeight();
        final double theta = Math.toRadians(rotationDeg);
        // Inverse rotation (by -theta): [ cos  sin ; -sin  cos ]
        final double cos = Math.cos(theta), sin = Math.sin(theta);
        for (int wx = worldArea.x; wx < worldArea.x + worldArea.width; wx++) {
            for (int wz = worldArea.y; wz < worldArea.y + worldArea.height; wz++) {
                final double dx = wx - originX, dz = wz - originZ;
                final double rx = cos * dx + sin * dz;
                final double rz = -sin * dx + cos * dz;
                final int u = (int) Math.floor(rx / blocksPerPixel);
                final int v = (int) Math.floor(rz / blocksPerPixel);
                if ((u < 0) || (u >= imgW) || (v < 0) || (v >= imgH)) {
                    continue;
                }
                if ((cropPx != null) && (! cropPx.contains(u, v))) {
                    continue;
                }
                final int argb = image.getRGB(u, v);
                final int alpha = (argb >>> 24) & 0xff;
                if (alpha == 0) {
                    continue; // fully transparent -> empty
                }
                final int r = (argb >> 16) & 0xff, g = (argb >> 8) & 0xff, b = argb & 0xff;
                final int brightness = (r + g + b) / 3;
                final boolean selected = invert ? (brightness > threshold) : (brightness < threshold);
                if (selected) {
                    result.add(new Point(wx, wz));
                }
            }
        }
        return result;
    }

    /**
     * Compute the footprint for the given parameters and write it into the dimension's
     * {@link TownLayout} layer (value 1). Columns outside the footprint are left untouched; footprint columns are set to 1.
     *
     * @return the number of columns set.
     */
    public static int stamp(Dimension dimension, BufferedImage image, double originX, double originZ,
                            double blocksPerPixel, double rotationDeg, Rectangle cropPx,
                            int threshold, boolean invert, Rectangle worldArea) {
        final Set<Point> columns = computeFootprint(image, originX, originZ, blocksPerPixel, rotationDeg,
                cropPx, threshold, invert, worldArea);
        for (Point p : columns) {
            dimension.setBitLayerValueAt(TownLayout.INSTANCE, p.x, p.y, true);
        }
        return columns.size();
    }
}
