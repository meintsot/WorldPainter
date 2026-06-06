package org.pepsoft.worldpainter.townplan;

import org.junit.Test;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.TestData;
import org.pepsoft.worldpainter.layers.TownLayout;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Set;

import static org.junit.Assert.*;

public class TownPlanStamperTest {
    /** 2x2 image: only the top-left pixel (0,0) is black; the rest white. */
    private static BufferedImage topLeftBlack() {
        BufferedImage img = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, 0xFF000000); // opaque black
        img.setRGB(1, 0, 0xFFFFFFFF);
        img.setRGB(0, 1, 0xFFFFFFFF);
        img.setRGB(1, 1, 0xFFFFFFFF);
        return img;
    }

    @Test
    public void darkPixelMapsToColumnAtUnitScale() {
        Set<Point> cols = TownPlanStamper.computeFootprint(
                topLeftBlack(), 0, 0, 1.0, 0.0, null, 128, false,
                new Rectangle(0, 0, 2, 2));
        assertEquals(1, cols.size());
        assertTrue(cols.contains(new Point(0, 0)));
    }

    @Test
    public void scaleExpandsOnePixelToBlockBlock() {
        // blocksPerPixel = 2 -> the single black pixel covers a 2x2 block area at world origin.
        Set<Point> cols = TownPlanStamper.computeFootprint(
                topLeftBlack(), 0, 0, 2.0, 0.0, null, 128, false,
                new Rectangle(0, 0, 4, 4));
        assertEquals(4, cols.size());
        assertTrue(cols.contains(new Point(0, 0)));
        assertTrue(cols.contains(new Point(1, 0)));
        assertTrue(cols.contains(new Point(0, 1)));
        assertTrue(cols.contains(new Point(1, 1)));
    }

    @Test
    public void invertSelectsLightPixels() {
        Set<Point> cols = TownPlanStamper.computeFootprint(
                topLeftBlack(), 0, 0, 1.0, 0.0, null, 128, true,
                new Rectangle(0, 0, 2, 2));
        assertEquals(3, cols.size());               // the three white pixels
        assertFalse(cols.contains(new Point(0, 0))); // not the black one
    }

    @Test
    public void cropRectExcludesOutsidePixels() {
        Set<Point> cols = TownPlanStamper.computeFootprint(
                topLeftBlack(), 0, 0, 1.0, 0.0, new Rectangle(1, 0, 1, 2), 128, false,
                new Rectangle(0, 0, 2, 2));
        assertTrue(cols.isEmpty());
    }

    @Test
    public void fullyTransparentPixelsAreIgnored() {
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, 0x00000000); // transparent black
        Set<Point> cols = TownPlanStamper.computeFootprint(
                img, 0, 0, 1.0, 0.0, null, 128, false, new Rectangle(0, 0, 1, 1));
        assertTrue(cols.isEmpty());
    }

    @Test
    public void rotation90IsClockwise() {
        // Black pixel at image (1,0), OFF the rotation pivot. At 90 deg clockwise about the origin,
        // image (1,0) maps to world column (0,1); counter-clockwise would instead give (0,-1).
        BufferedImage img = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, 0xFFFFFFFF);
        img.setRGB(1, 0, 0xFF000000); // opaque black, off the pivot
        img.setRGB(0, 1, 0xFFFFFFFF);
        img.setRGB(1, 1, 0xFFFFFFFF);

        Set<Point> cols = TownPlanStamper.computeFootprint(
                img, 0, 0, 1.0, 90.0, null, 128, false,
                new Rectangle(-3, -3, 6, 6));

        assertEquals(1, cols.size());
        assertTrue(cols.contains(new Point(0, 1)));    // clockwise result
        assertFalse(cols.contains(new Point(0, -1)));  // would be counter-clockwise
    }

    @Test
    public void discRadiusZeroIsJustCentre() {
        Set<Point> cols = TownPlanStamper.computeDisc(5, 7, 0.0);
        assertEquals(1, cols.size());
        assertTrue(cols.contains(new Point(5, 7)));
    }

    @Test
    public void discRadiusOneIsPlusShape() {
        // radius 1: centre + 4 orthogonal neighbours; diagonals (dx^2 + dz^2 = 2) fall outside.
        Set<Point> cols = TownPlanStamper.computeDisc(0, 0, 1.0);
        assertEquals(5, cols.size());
        assertTrue(cols.contains(new Point(0, 0)));
        assertTrue(cols.contains(new Point(1, 0)));
        assertTrue(cols.contains(new Point(-1, 0)));
        assertTrue(cols.contains(new Point(0, 1)));
        assertTrue(cols.contains(new Point(0, -1)));
        assertFalse(cols.contains(new Point(1, 1)));   // diagonal excluded
    }

    @Test
    public void discIsCentredOnGivenColumn() {
        Set<Point> cols = TownPlanStamper.computeDisc(10, -4, 2.0);
        assertTrue(cols.contains(new Point(10, -4)));   // centre
        assertTrue(cols.contains(new Point(12, -4)));   // +2 in x, on the rim (4 <= 4)
        assertTrue(cols.contains(new Point(10, -2)));   // +2 in z, on the rim
        assertFalse(cols.contains(new Point(12, -2)));  // (2,2): 8 > 4, outside
        assertFalse(cols.contains(new Point(13, -4)));  // +3 in x: 9 > 4, outside
    }

    @Test
    public void eraseClearsDiscAndLeavesRest() {
        final Rectangle area = new Rectangle(0, 0, 128, 128);
        final Dimension dimension = TestData.createDimension(area, 64);
        // Paint a 3x3 block of footprint around (10,10).
        for (int x = 9; x <= 11; x++) {
            for (int z = 9; z <= 11; z++) {
                dimension.setBitLayerValueAt(TownLayout.INSTANCE, x, z, true);
            }
        }
        // Erase radius 1 at the centre clears (10,10) and its 4 orthogonal neighbours only.
        int cleared = TownPlanStamper.erase(dimension, 10, 10, 1.0);
        assertEquals(5, cleared);
        assertFalse(dimension.getBitLayerValueAt(TownLayout.INSTANCE, 10, 10));
        assertFalse(dimension.getBitLayerValueAt(TownLayout.INSTANCE, 11, 10));
        assertTrue(dimension.getBitLayerValueAt(TownLayout.INSTANCE, 9, 9)); // diagonal corner survives
    }
}
