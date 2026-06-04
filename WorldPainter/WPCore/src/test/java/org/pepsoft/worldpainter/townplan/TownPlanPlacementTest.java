package org.pepsoft.worldpainter.townplan;

import org.junit.Test;

import java.awt.geom.Point2D;

import static org.junit.Assert.*;
import static org.pepsoft.worldpainter.townplan.TownPlanPlacement.Handle;

public class TownPlanPlacementTest {
    private static final double EPS = 1e-6;

    @Test
    public void pixelToWorldNoRotation() {
        Point2D.Double p = TownPlanPlacement.pixelToWorld(10, 4, 100, 200, 2.0, 0.0);
        assertEquals(120.0, p.x, EPS); // 100 + 2*10
        assertEquals(208.0, p.y, EPS); // 200 + 2*4
    }

    @Test
    public void pixelToWorld90Clockwise() {
        // theta=90: R=[0 -1; 1 0]; world = origin + scale*( -v, u )
        Point2D.Double p = TownPlanPlacement.pixelToWorld(10, 0, 0, 0, 1.0, 90.0);
        assertEquals(0.0, p.x, EPS);   // -v*scale = 0
        assertEquals(10.0, p.y, EPS);  //  u*scale = 10
    }

    @Test
    public void hitTestCornersBodyAndOutside() {
        // 100x80 image, origin (0,0), scale 1, no rotation: corners at (0,0),(100,0),(0,80),(100,80).
        double handleR = 5.0;
        assertEquals(Handle.NW, TownPlanPlacement.hitTest(0, 0, 0, 0, 1.0, 0.0, 100, 80, handleR));
        assertEquals(Handle.SE, TownPlanPlacement.hitTest(100, 80, 0, 0, 1.0, 0.0, 100, 80, handleR));
        assertEquals(Handle.BODY, TownPlanPlacement.hitTest(50, 40, 0, 0, 1.0, 0.0, 100, 80, handleR));
        assertEquals(Handle.NONE, TownPlanPlacement.hitTest(500, 500, 0, 0, 1.0, 0.0, 100, 80, handleR));
    }

    @Test
    public void hitTestRotateHandleAboveTopEdge() {
        // Rotate handle sits 'rotateOffset' world units beyond the top-edge midpoint (50,0),
        // in the outward (negative v) direction => (50, -rotateOffset).
        double handleR = 5.0;
        double off = TownPlanPlacement.ROTATE_HANDLE_WORLD_OFFSET;
        assertEquals(Handle.ROTATE, TownPlanPlacement.hitTest(50, -off, 0, 0, 1.0, 0.0, 100, 80, handleR));
    }

    @Test
    public void moveTranslatesOrigin() {
        double[] s = TownPlanPlacement.applyMove(100, 200, 5, -3); // dx=5, dz=-3
        assertEquals(105.0, s[0], EPS); // originX
        assertEquals(197.0, s[1], EPS); // originZ
    }

    @Test
    public void rotateKeepsCenterFixed() {
        int W = 100, H = 80;
        double originX = 0, originZ = 0, scale = 1.0, theta = 0.0;
        Point2D.Double centerBefore = TownPlanPlacement.centerWorld(originX, originZ, scale, theta, W, H);
        // Mouse straight to the right of the center => up-vector points right => theta = +90 (deg).
        double[] s = TownPlanPlacement.applyRotate(centerBefore.x + 50, centerBefore.y, originX, originZ, scale, theta, W, H);
        assertEquals(90.0, normalize(s[3]), 1e-6);
        Point2D.Double centerAfter = TownPlanPlacement.centerWorld(s[0], s[1], s[2], s[3], W, H);
        assertEquals(centerBefore.x, centerAfter.x, 1e-6);
        assertEquals(centerBefore.y, centerAfter.y, 1e-6);
    }

    @Test
    public void scaleKeepsCenterFixedAndMatchesCornerDistance() {
        int W = 100, H = 80;
        double originX = 0, originZ = 0, scale = 1.0, theta = 0.0;
        Point2D.Double center = TownPlanPlacement.centerWorld(originX, originZ, scale, theta, W, H); // (50,40)
        double halfDiagPixels = Math.hypot(W / 2.0, H / 2.0);
        // Put the mouse at twice the current corner distance => scale doubles.
        double targetDist = 2.0 * scale * halfDiagPixels;
        double[] s = TownPlanPlacement.applyScale(center.x + targetDist, center.y, originX, originZ, scale, theta, W, H);
        assertEquals(2.0, s[2], 1e-6); // new scale
        Point2D.Double centerAfter = TownPlanPlacement.centerWorld(s[0], s[1], s[2], theta, W, H);
        assertEquals(center.x, centerAfter.x, 1e-6);
        assertEquals(center.y, centerAfter.y, 1e-6);
    }

    private static double normalize(double deg) {
        double d = deg % 360.0;
        if (d < 0) {
            d += 360.0;
        }
        return d;
    }
}
