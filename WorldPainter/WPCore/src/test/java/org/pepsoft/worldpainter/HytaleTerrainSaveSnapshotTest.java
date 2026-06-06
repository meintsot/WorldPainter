package org.pepsoft.worldpainter;

import org.junit.Test;
import org.pepsoft.worldpainter.hytale.HytaleTerrain;
import org.pepsoft.worldpainter.hytale.HytaleTerrainLayer;
import java.util.Map;
import static org.pepsoft.worldpainter.DefaultPlugin.HYTALE;
import static org.pepsoft.worldpainter.Constants.DIM_NORMAL;
import static org.junit.Assert.*;

public class HytaleTerrainSaveSnapshotTest {

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
    public void snapshotStoresPaletteAndVersionWhenTerrainPainted() {
        Dimension dim = buildHytaleDimension();
        Tile tile = dim.getTile(0, 0);
        int clayIdx = HytaleTerrain.getByBlockId("Soil_Clay").getLayerIndex();
        HytaleTerrainLayer.setTerrainIndex(tile, 1, 1, clayIdx);

        dim.snapshotHytaleTerrainPalette();

        @SuppressWarnings("unchecked")
        Map<Integer, String> palette =
                (Map<Integer, String>) dim.getManagedAttributeForTests("hytaleTerrainPalette");
        assertNotNull("palette must be snapshotted when terrain present", palette);
        assertEquals("Soil_Clay", palette.get(clayIdx));
        assertEquals(Integer.valueOf(3), dim.getManagedAttributeForTests("hytaleTerrainVersion"));
    }

    @Test
    public void snapshotNoOpWhenNoTerrainData() {
        Dimension dim = buildHytaleDimension();
        dim.snapshotHytaleTerrainPalette();
        assertNull("no palette when there is no Hytale terrain data",
                dim.getManagedAttributeForTests("hytaleTerrainPalette"));
    }
}
