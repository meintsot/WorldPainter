package org.pepsoft.worldpainter;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Regression tests for the WorldPainter grid scaling fix. The upstream
 * {@link org.pepsoft.util.swing.TiledImageViewer} auto-doubles the on-screen
 * grid spacing whenever the view is zoomed out, so a Hytale user who picks a
 * 32-block (chunk-aligned) grid sees 64/128/256/512-block spacing the moment
 * they zoom out. {@link WorldPainter#effectiveGridSize(int, int)} replaces
 * that policy with one that honors the user's choice and only hides the grid
 * when on-screen lines would be denser than 2 pixels apart.
 */
public class WorldPainterGridScalingTest {

    @Test
    public void honorsConfiguredSizeAtNativeZoom() {
        assertEquals(32, WorldPainter.effectiveGridSize(32, 0));
        assertEquals(128, WorldPainter.effectiveGridSize(128, 0));
    }

    @Test
    public void honorsConfiguredSizeWhenZoomedIn() {
        assertEquals(32, WorldPainter.effectiveGridSize(32, 4));
        assertEquals(128, WorldPainter.effectiveGridSize(128, 6));
    }

    @Test
    public void doesNotAutoDoubleAtZoomMinusTwo() {
        // Today the upstream auto-scaler would return 128 here. The fix must
        // honor the configured 32 instead so chunk borders stay visible.
        assertEquals(32, WorldPainter.effectiveGridSize(32, -2));
    }

    @Test
    public void doesNotAutoDoubleAtMaximumZoomOut() {
        // At the maximum WorldPainter zoom-out (-4) the upstream auto-scaler
        // would return 512. The fix must keep the user's 32 instead.
        // 32 * 2^-4 = 2.0 px on screen, which is exactly the visibility floor.
        assertEquals(32, WorldPainter.effectiveGridSize(32, -4));
    }

    @Test
    public void honorsLargeConfiguredSizeWhenZoomedOut() {
        // Default Minecraft grid size 128 stays 128 even at maximum zoom-out.
        assertEquals(128, WorldPainter.effectiveGridSize(128, -4));
    }

    @Test
    public void hidesGridWhenLinesWouldBeDenserThanTwoPixels() {
        // gridSize=2 at zoom=-1 → 1.0 px spacing → too dense, hide entirely.
        assertEquals(0, WorldPainter.effectiveGridSize(2, -1));
    }

    @Test
    public void hidesInvalidZeroGridSize() {
        assertEquals(0, WorldPainter.effectiveGridSize(0, 0));
    }

    @Test
    public void keepsGridAtTwoPixelBoundary() {
        // gridSize=4 at zoom=-1 → 2.0 px spacing exactly. Threshold is
        // strict-less-than, so the grid should still render.
        assertEquals(4, WorldPainter.effectiveGridSize(4, -1));
    }

    @Test
    public void hidesGridAtExtremeZoomOutWithSmallSize() {
        // gridSize=2 at zoom=-4 → 0.125 px spacing → hide.
        assertEquals(0, WorldPainter.effectiveGridSize(2, -4));
    }

    @Test
    public void setPaintGridTrueIsReflectedByIsPaintGrid() {
        WorldPainter wp = new WorldPainter(null, null);
        assertEquals(false, wp.isPaintGrid());
        wp.setPaintGrid(true);
        assertEquals(true, wp.isPaintGrid());
        wp.setPaintGrid(false);
        assertEquals(false, wp.isPaintGrid());
    }
}
