package org.pepsoft.worldpainter.selection;

import org.junit.Test;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.TileFactory;
import org.pepsoft.worldpainter.TileFactoryFactory;
import org.pepsoft.worldpainter.World2;
import org.pepsoft.worldpainter.layers.ReadOnly;

import static org.junit.Assert.assertEquals;
import static org.pepsoft.worldpainter.Constants.DIM_NORMAL;
import static org.pepsoft.worldpainter.DefaultPlugin.HYTALE;

/**
 * TP-58: pasting a copied selection must not modify columns marked {@link ReadOnly} (imported chunks the user opted
 * to protect from editing).
 */
public class Tp58SelectionCopyReadOnlyTest {

    private static final int BASE_HEIGHT = 50;
    private static final int SOURCE_HEIGHT = 60;

    @Test
    public void copySelectionSkipsReadOnlyDestination() throws Exception {
        final World2 world = new World2(HYTALE, 0, 320);
        final long seed = 42L;
        final TileFactory tileFactory = TileFactoryFactory.createFlatTileFactory(seed, Terrain.GRASS, 0, 320, BASE_HEIGHT, 0, false, false);
        final Dimension.Anchor anchor = new Dimension.Anchor(DIM_NORMAL, Dimension.Role.DETAIL, false, 0);
        final Dimension dim = new Dimension(world, "Surface", seed, tileFactory, anchor);
        dim.setEventsInhibited(true);
        final Tile tile = tileFactory.createTile(0, 0);
        dim.addTile(tile);

        // Source: chunk (0, 0), distinctive height and terrain, selected per-block
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                dim.setHeightAt(x, y, SOURCE_HEIGHT);
                dim.setTerrainAt(x, y, Terrain.SAND);
                dim.setBitLayerValueAt(SelectionBlock.INSTANCE, x, y, true);
            }
        }

        // Destination chunk (blocks 64..79, 0..15) marked read-only
        dim.setBitLayerValueAt(ReadOnly.INSTANCE, 64, 0, true);

        final SelectionHelper helper = new SelectionHelper(dim);
        helper.setOptions(new SelectionOptions());
        helper.copySelection(64, 0, null);

        assertEquals("Height of read-only destination must be unchanged",
                BASE_HEIGHT, dim.getIntHeightAt(72, 8));
        assertEquals("Terrain of read-only destination must be unchanged",
                Terrain.GRASS, dim.getTerrainAt(72, 8));

        // Copying to a normal chunk must still work
        helper.copySelection(96, 0, null);

        assertEquals("Height of normal destination must be copied",
                SOURCE_HEIGHT, dim.getIntHeightAt(104, 8));
        assertEquals("Terrain of normal destination must be copied",
                Terrain.SAND, dim.getTerrainAt(104, 8));
    }
}
