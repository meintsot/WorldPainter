package org.pepsoft.worldpainter.townplan;

import org.junit.Test;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.TileFactory;
import org.pepsoft.worldpainter.TileFactoryFactory;
import org.pepsoft.worldpainter.World2;
import org.pepsoft.worldpainter.layers.ReadOnly;
import org.pepsoft.worldpainter.layers.TownLayout;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.pepsoft.worldpainter.Constants.DIM_NORMAL;
import static org.pepsoft.worldpainter.DefaultPlugin.HYTALE;

/**
 * TP-58: the town-plan stamper must not modify columns marked {@link ReadOnly} (imported chunks the user opted to
 * protect from editing).
 */
public class Tp58TownPlanReadOnlyTest {

    /**
     * Stamping a footprint that spans a read-only chunk and a normal chunk must only set {@link TownLayout} in the
     * normal chunk.
     */
    @Test
    public void stampSkipsReadOnlyColumns() {
        final Dimension dim = buildDimension();
        // Mark chunk (0, 0) (blocks 0..15, 0..15) read-only
        dim.setBitLayerValueAt(ReadOnly.INSTANCE, 0, 0, true);

        // All-black 2x2 image at 16 blocks per pixel: footprint covers blocks (0..31, 0..31)
        final BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        TownPlanStamper.stamp(dim, image, 0, 0, 16, 0, null, 128, false, new Rectangle(0, 0, 32, 32));

        assertFalse("Read-only column must not be stamped", dim.getBitLayerValueAt(TownLayout.INSTANCE, 8, 8));
        assertTrue("Normal column must be stamped", dim.getBitLayerValueAt(TownLayout.INSTANCE, 24, 24));
    }

    /**
     * Erasing a disc that spans a read-only chunk and a normal chunk must only clear {@link TownLayout} in the
     * normal chunk.
     */
    @Test
    public void eraseSkipsReadOnlyColumns() {
        final Dimension dim = buildDimension();
        dim.setBitLayerValueAt(TownLayout.INSTANCE, 8, 8, true);
        dim.setBitLayerValueAt(TownLayout.INSTANCE, 24, 24, true);
        // Mark chunk (0, 0) read-only AFTER stamping, as if the footprint came from an import
        dim.setBitLayerValueAt(ReadOnly.INSTANCE, 0, 0, true);

        // Disc around (16, 16) with radius 20 covers both (8, 8) and (24, 24)
        TownPlanStamper.erase(dim, 16, 16, 20);

        assertTrue("Read-only column must not be erased", dim.getBitLayerValueAt(TownLayout.INSTANCE, 8, 8));
        assertFalse("Normal column must be erased", dim.getBitLayerValueAt(TownLayout.INSTANCE, 24, 24));
    }

    private static Dimension buildDimension() {
        final World2 world = new World2(HYTALE, 0, 320);
        final long seed = 42L;
        final TileFactory tileFactory = TileFactoryFactory.createFlatTileFactory(seed, Terrain.GRASS, 0, 320, 50, 0, false, false);
        final Dimension.Anchor anchor = new Dimension.Anchor(DIM_NORMAL, Dimension.Role.DETAIL, false, 0);
        final Dimension dim = new Dimension(world, "Surface", seed, tileFactory, anchor);
        dim.setEventsInhibited(true);
        final Tile tile = tileFactory.createTile(0, 0);
        dim.addTile(tile);
        return dim;
    }
}
