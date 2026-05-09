package org.pepsoft.worldpainter.painting;

import org.junit.Test;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.TileFactory;
import org.pepsoft.worldpainter.TileFactoryFactory;
import org.pepsoft.worldpainter.World2;
import org.pepsoft.worldpainter.hytale.HytalePlantsLayer;
import org.pepsoft.worldpainter.hytale.HytaleTerrain;
import org.pepsoft.worldpainter.hytale.HytaleTerrainLayer;

import static org.junit.Assert.assertEquals;
import static org.pepsoft.worldpainter.Constants.DIM_NORMAL;
import static org.pepsoft.worldpainter.DefaultPlugin.HYTALE;

/**
 * Regression test for TP-60: {@link HytaleTerrainPaint} must route surface-only
 * HytaleTerrains (plants/decorations) into {@link HytalePlantsLayer} so they sit on
 * top of whatever substrate {@link HytaleTerrainLayer} already holds, instead of
 * overwriting that substrate.
 */
public class HytaleTerrainPaintRoutingTest {

    @Test
    public void surfaceOnlyHytaleTerrainPaintsIntoPlantsLayerAndLeavesSubstrateAlone() {
        Tile tile = setUpStonePaintedTile();

        HytaleTerrainPaint bushPaint = new HytaleTerrainPaint(Terrain.GRASS, HytaleTerrain.BUSH);
        bushPaint.applyPixel(getDimension(tile), worldX(tile, 5), worldZ(tile, 7));

        assertEquals("Substrate must remain HytaleTerrain.STONE — painting a plant must not overwrite it",
                HytaleTerrain.STONE.getLayerIndex(),
                HytaleTerrainLayer.getTerrainIndex(tile, 5, 7));
        assertEquals("Painted bush must land in HytalePlantsLayer at the same pixel",
                HytaleTerrain.BUSH.getLayerIndex(),
                HytalePlantsLayer.getPlantIndex(tile, 5, 7));
    }

    @Test
    public void solidHytaleTerrainPaintsIntoTerrainLayerAndLeavesPlantsAlone() {
        Tile tile = setUpFreshTile();
        // Pre-existing plant at this pixel (e.g., user previously painted a bush here).
        HytalePlantsLayer.setPlantIndex(tile, 5, 7, HytaleTerrain.BUSH.getLayerIndex());

        HytaleTerrainPaint stonePaint = new HytaleTerrainPaint(Terrain.STONE, HytaleTerrain.STONE);
        stonePaint.applyPixel(getDimension(tile), worldX(tile, 5), worldZ(tile, 7));

        assertEquals("Painting a solid terrain must store its index in HytaleTerrainLayer",
                HytaleTerrain.STONE.getLayerIndex(),
                HytaleTerrainLayer.getTerrainIndex(tile, 5, 7));
        assertEquals("Painting a solid terrain must not erase a previously-painted plant overlay",
                HytaleTerrain.BUSH.getLayerIndex(),
                HytalePlantsLayer.getPlantIndex(tile, 5, 7));
    }

    // -------- test helpers --------

    private Dimension dimensionRef;

    private Tile setUpStonePaintedTile() {
        Tile tile = setUpFreshTile();
        for (int x = 0; x < 128; x++) {
            for (int z = 0; z < 128; z++) {
                HytaleTerrainLayer.setTerrainIndex(tile, x, z, HytaleTerrain.STONE.getLayerIndex());
            }
        }
        return tile;
    }

    private Tile setUpFreshTile() {
        World2 world = new World2(HYTALE, 0, 320);
        long seed = 1L;
        TileFactory tileFactory = TileFactoryFactory.createFlatTileFactory(
                seed, Terrain.GRASS, 0, 320, 64, 62, false, false);
        Dimension.Anchor anchor = new Dimension.Anchor(DIM_NORMAL, Dimension.Role.DETAIL, false, 0);
        Dimension dim = new Dimension(world, "Surface", seed, tileFactory, anchor);
        dim.setEventsInhibited(true);
        Tile tile = tileFactory.createTile(0, 0);
        dim.addTile(tile);
        dim.setEventsInhibited(false);
        world.addDimension(dim);
        dimensionRef = dim;
        return tile;
    }

    private Dimension getDimension(Tile tile) {
        return dimensionRef;
    }

    private static int worldX(Tile tile, int localX) {
        return (tile.getX() << 7) | localX;
    }

    private static int worldZ(Tile tile, int localZ) {
        return (tile.getY() << 7) | localZ;
    }
}
