package org.pepsoft.worldpainter.townplan;

import java.awt.geom.Point2D;

/**
 * Pure geometry for placing/transforming a town-plan image on the map. State is
 * {@code (originX, originZ, scale, rotationDeg)} where {@code origin} is the world position of
 * image pixel (0,0), {@code scale} is world blocks per image pixel, and rotation is clockwise
 * degrees. The convention matches {@link TownPlanStamper}: world = origin + scale·R(θ)·pixel,
 * R(θ) = [cos −sin; sin cos].
 *
 * <p>Returned state arrays are {@code [originX, originZ, scale, rotationDeg]} (rotate/scale also
 * return the recomputed origin so the image center stays fixed). Move returns {@code [originX, originZ]}.
 */
public final class TownPlanPlacement {
    private TownPlanPlacement() {
    }

    public enum Handle { BODY, NW, NE, SW, SE, ROTATE, NONE }

    /** World-unit distance the rotate handle sits beyond the top-edge midpoint. */
    public static final double ROTATE_HANDLE_WORLD_OFFSET = 24.0;

    public static Point2D.Double pixelToWorld(double u, double v, double originX, double originZ,
                                              double scale, double thetaDeg) {
        final double rad = Math.toRadians(thetaDeg);
        final double cos = Math.cos(rad), sin = Math.sin(rad);
        return new Point2D.Double(
                originX + scale * (cos * u - sin * v),
                originZ + scale * (sin * u + cos * v));
    }

    /** Inverse of {@link #pixelToWorld}: world point -> image pixel coordinates. */
    public static Point2D.Double worldToPixel(double wx, double wz, double originX, double originZ,
                                              double scale, double thetaDeg) {
        final double rad = Math.toRadians(thetaDeg);
        final double cos = Math.cos(rad), sin = Math.sin(rad);
        final double dx = wx - originX, dz = wz - originZ;
        return new Point2D.Double((cos * dx + sin * dz) / scale, (-sin * dx + cos * dz) / scale);
    }

    public static Point2D.Double centerWorld(double originX, double originZ, double scale,
                                             double thetaDeg, int imgW, int imgH) {
        return pixelToWorld(imgW / 2.0, imgH / 2.0, originX, originZ, scale, thetaDeg);
    }

    /** World position of the rotate handle (beyond the top-edge midpoint, outward). */
    public static Point2D.Double rotateHandleWorld(double originX, double originZ, double scale,
                                                   double thetaDeg, int imgW, int imgH) {
        final Point2D.Double topMid = pixelToWorld(imgW / 2.0, 0, originX, originZ, scale, thetaDeg);
        final Point2D.Double center = centerWorld(originX, originZ, scale, thetaDeg, imgW, imgH);
        double dirX = topMid.x - center.x, dirZ = topMid.y - center.y;
        final double len = Math.hypot(dirX, dirZ);
        if (len < 1e-9) {
            return topMid;
        }
        dirX /= len;
        dirZ /= len;
        return new Point2D.Double(topMid.x + dirX * ROTATE_HANDLE_WORLD_OFFSET,
                topMid.y + dirZ * ROTATE_HANDLE_WORLD_OFFSET);
    }

    /**
     * Determine which handle (or the body, or nothing) the given world point hits. Corner and rotate
     * handles win over the body; {@code handleRadius} is the pick tolerance in world units.
     */
    public static Handle hitTest(double wx, double wz, double originX, double originZ, double scale,
                                 double thetaDeg, int imgW, int imgH, double handleRadius) {
        final double r2 = handleRadius * handleRadius;
        if (near(wx, wz, rotateHandleWorld(originX, originZ, scale, thetaDeg, imgW, imgH), r2)) {
            return Handle.ROTATE;
        }
        if (near(wx, wz, pixelToWorld(0, 0, originX, originZ, scale, thetaDeg), r2)) {
            return Handle.NW;
        }
        if (near(wx, wz, pixelToWorld(imgW, 0, originX, originZ, scale, thetaDeg), r2)) {
            return Handle.NE;
        }
        if (near(wx, wz, pixelToWorld(0, imgH, originX, originZ, scale, thetaDeg), r2)) {
            return Handle.SW;
        }
        if (near(wx, wz, pixelToWorld(imgW, imgH, originX, originZ, scale, thetaDeg), r2)) {
            return Handle.SE;
        }
        final Point2D.Double px = worldToPixel(wx, wz, originX, originZ, scale, thetaDeg);
        if ((px.x >= 0) && (px.x <= imgW) && (px.y >= 0) && (px.y <= imgH)) {
            return Handle.BODY;
        }
        return Handle.NONE;
    }

    /** Move gesture: translate the origin by the world drag delta. Returns {@code [originX, originZ]}. */
    public static double[] applyMove(double originX, double originZ, double dWorldX, double dWorldZ) {
        return new double[] { originX + dWorldX, originZ + dWorldZ };
    }

    /**
     * Rotate gesture: set rotation so the image's 'up' (center -> top-edge) points at the mouse, keeping
     * the center fixed. Returns {@code [originX, originZ, rotationDeg]}.
     */
    public static double[] applyRotate(double mouseWx, double mouseWz, double originX, double originZ,
                                       double scale, double thetaDeg, int imgW, int imgH) {
        final Point2D.Double center = centerWorld(originX, originZ, scale, thetaDeg, imgW, imgH);
        final double phiDeg = Math.toDegrees(Math.atan2(mouseWz - center.y, mouseWx - center.x));
        final double newTheta = phiDeg + 90.0; // 'up' direction angle equals theta - 90
        final double[] s = originForFixedCenter(center.x, center.y, scale, newTheta, imgW, imgH);
        return new double[] { s[0], s[1], newTheta };
    }

    /**
     * Scale gesture: set scale so the dragged corner sits at the mouse distance from the center, keeping
     * the center fixed. Returns {@code [originX, originZ, scale, rotationDeg]}.
     */
    public static double[] applyScale(double mouseWx, double mouseWz, double originX, double originZ,
                                      double scale, double thetaDeg, int imgW, int imgH) {
        final Point2D.Double center = centerWorld(originX, originZ, scale, thetaDeg, imgW, imgH);
        final double halfDiagPixels = Math.hypot(imgW / 2.0, imgH / 2.0);
        final double dist = Math.hypot(mouseWx - center.x, mouseWz - center.y);
        double newScale = (halfDiagPixels > 1e-9) ? (dist / halfDiagPixels) : scale;
        if (newScale < MIN_SCALE) {
            newScale = MIN_SCALE;
        }
        final double[] s = originForFixedCenter(center.x, center.y, newScale, thetaDeg, imgW, imgH);
        return new double[] { s[0], s[1], newScale, thetaDeg };
    }

    /** Origin such that the image center lands on {@code (centerX, centerZ)} for the given scale/rotation. */
    private static double[] originForFixedCenter(double centerX, double centerZ, double scale,
                                                 double thetaDeg, int imgW, int imgH) {
        // center = origin + scale·R(θ)·(W/2, H/2)  =>  origin = center − scale·R(θ)·(W/2, H/2)
        final double rad = Math.toRadians(thetaDeg);
        final double cos = Math.cos(rad), sin = Math.sin(rad);
        final double cu = imgW / 2.0, cv = imgH / 2.0;
        final double offX = scale * (cos * cu - sin * cv);
        final double offZ = scale * (sin * cu + cos * cv);
        return new double[] { centerX - offX, centerZ - offZ };
    }

    private static boolean near(double wx, double wz, Point2D.Double p, double r2) {
        final double dx = wx - p.x, dz = wz - p.y;
        return (dx * dx + dz * dz) <= r2;
    }

    private static final double MIN_SCALE = 0.01;
}
