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

    @Test
    public void snapTo90RoundsToNearestCardinal() {
        assertEquals(0.0, TownPlanPlacement.snapTo90(37.0), EPS);
        assertEquals(90.0, TownPlanPlacement.snapTo90(46.0), EPS);
        assertEquals(90.0, TownPlanPlacement.snapTo90(134.0), EPS);
        assertEquals(180.0, TownPlanPlacement.snapTo90(135.0), EPS);
        assertEquals(0.0, TownPlanPlacement.snapTo90(-10.0), EPS);
    }

    @Test
    public void rotateToSetsAngleAndKeepsCenterFixed() {
        int W = 100, H = 80;
        double originX = 5, originZ = -3, scale = 1.5, theta = 20.0;
        Point2D.Double before = TownPlanPlacement.centerWorld(originX, originZ, scale, theta, W, H);
        double[] s = TownPlanPlacement.rotateTo(originX, originZ, scale, theta, 90.0, W, H);
        assertEquals(90.0, s[3], EPS);  // exact target angle
        assertEquals(scale, s[2], EPS); // scale unchanged
        Point2D.Double after = TownPlanPlacement.centerWorld(s[0], s[1], s[2], s[3], W, H);
        assertEquals(before.x, after.x, 1e-6);
        assertEquals(before.y, after.y, 1e-6);
    }

    @Test
    public void applyRotateWithSnapLandsOnCardinalAndKeepsCenter() {
        int W = 100, H = 80;
        double originX = 0, originZ = 0, scale = 1.0, theta = 0.0;
        Point2D.Double center = TownPlanPlacement.centerWorld(originX, originZ, scale, theta, W, H);
        // Mouse slightly off the +90 direction => free angle near 90 but not exact; snap should land it on 90.
        double[] s = TownPlanPlacement.applyRotate(center.x + 50, center.y + 4, originX, originZ, scale, theta, W, H, true);
        assertEquals(0.0, normalize(s[3]) % 90.0, 1e-6); // snapped to a multiple of 90
        Point2D.Double after = TownPlanPlacement.centerWorld(s[0], s[1], s[2], s[3], W, H);
        assertEquals(center.x, after.x, 1e-6);
        assertEquals(center.y, after.y, 1e-6);
    }

    private static double normalize(double deg) {
        double d = deg % 360.0;
        if (d < 0) {
            d += 360.0;
        }
        return d;
    }
}
