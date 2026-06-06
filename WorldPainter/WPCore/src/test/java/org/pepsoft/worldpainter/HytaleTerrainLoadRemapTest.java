package org.pepsoft.worldpainter;

import org.junit.Test;
import org.pepsoft.worldpainter.hytale.HytaleTerrain;
import org.pepsoft.worldpainter.hytale.HytaleTerrainLayer;
import java.util.HashMap;
import java.util.Map;
import static org.pepsoft.worldpainter.DefaultPlugin.HYTALE;
import static org.pepsoft.worldpainter.Constants.DIM_NORMAL;
import static org.junit.Assert.*;

public class HytaleTerrainLoadRemapTest {

    private static Dimension buildHytaleDimension() {
        World2 world = new World2(HYTALE, 0, 320);
        long seed = 1L;
        TileFactory tileFactory = TileFactoryFactory.createFlatTileFactory(
                seed, Terrain.GRASS, 0, 320, 64, 62, false, false);
        Dimension.Anchor anchor = new Dimension.Anchor(DIM_NORMAL, Dimension.Role.DETAIL, false, 0);
        Dimension dim = new Dimension(world, "Surface", seed, tileFactory, anchor);
        dim.setEventsInhibited(true);
        dim.addTile(tileFactory.createTile(0, 0));
        dim.setEventsInhibited(false);
        world.addDimension(dim);
        return dim;
    }

    @Test
    public void loadRemapsStoredIndexToCurrentIndexOfSameBlock() {
        Dimension dim = buildHytaleDimension();
        Tile tile = dim.getTile(0, 0);
        int clayNow = HytaleTerrain.getByBlockId("Soil_Clay").getLayerIndex();
        int stoneNow = HytaleTerrain.getByBlockId("Rock_Stone").getLayerIndex();
        assertNotEquals("precondition: clay and stone differ", clayNow, stoneNow);

        // Simulate an older save: a pixel stored at stoneNow, with a palette saying
        // that index meant "Soil_Clay" (the ordering before the registry grew).
        HytaleTerrainLayer.setTerrainIndex(tile, 2, 2, stoneNow);
        Map<Integer, String> lyingPalette = new HashMap<>();
        lyingPalette.put(stoneNow, "Soil_Clay");
        dim.putManagedAttributeForTests("hytaleTerrainVersion", 3);
        dim.putManagedAttributeForTests("hytaleTerrainPalette", lyingPalette);

        dim.migrateHytaleTerrainPaletteOnLoad();

        assertEquals("stored index must be remapped to clay's CURRENT index",
                clayNow, HytaleTerrainLayer.getTerrainIndex(dim.getTile(0, 0), 2, 2));
    }

    @Test
    public void loadIsNoOpWithoutPalette() {
        Dimension dim = buildHytaleDimension();
        Tile tile = dim.getTile(0, 0);
        int stoneNow = HytaleTerrain.getByBlockId("Rock_Stone").getLayerIndex();
        HytaleTerrainLayer.setTerrainIndex(tile, 3, 3, stoneNow);
        // No palette, version < 3 -> palette branch must NOT touch anything here.
        dim.putManagedAttributeForTests("hytaleTerrainVersion", 2);

        dim.migrateHytaleTerrainPaletteOnLoad();

        assertEquals(stoneNow, HytaleTerrainLayer.getTerrainIndex(dim.getTile(0, 0), 3, 3));
    }
}
