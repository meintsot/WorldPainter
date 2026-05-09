package org.pepsoft.worldpainter.hytale;

import org.junit.Test;
import org.pepsoft.worldpainter.Tile;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

/**
 * Regression test for the TP-60 save migration: legacy worlds painted before the plants/terrain
 * split stored surface-only HytaleTerrain indices (Bush, Mushroom, Flower …) directly in
 * {@link HytaleTerrainLayer}, overwriting the substrate. On load, those indices must be moved
 * to {@link HytalePlantsLayer} so the substrate (currently 0 = "use Minecraft fallback") is
 * preserved and the plant becomes an overlay at {@code height + 1}.
 */
public class HytalePlantsLayerMigrationTest {

    @Test
    public void surfaceOnlyTerrainIndicesAreMovedFromTerrainLayerToPlantsLayer() {
        Tile tile = new Tile(0, 0, 0, 320);
        HytaleTerrainLayer.setTerrainIndex(tile, 5, 7, HytaleTerrain.BUSH.getLayerIndex());
        HytaleTerrainLayer.setTerrainIndex(tile, 9, 9, HytaleTerrain.STONE.getLayerIndex());

        HytalePlantsLayer.migrateSurfaceOnlyFromTerrainLayer(Collections.singletonList(tile));

        assertEquals("Surface-only Bush index must move to HytalePlantsLayer",
                HytaleTerrain.BUSH.getLayerIndex(),
                HytalePlantsLayer.getPlantIndex(tile, 5, 7));
        assertEquals("HytaleTerrainLayer must be cleared at the migrated pixel",
                0,
                HytaleTerrainLayer.getTerrainIndex(tile, 5, 7));
        assertEquals("Solid Stone index must remain in HytaleTerrainLayer",
                HytaleTerrain.STONE.getLayerIndex(),
                HytaleTerrainLayer.getTerrainIndex(tile, 9, 9));
        assertEquals("Solid Stone pixel must not get a plants overlay",
                0,
                HytalePlantsLayer.getPlantIndex(tile, 9, 9));
    }

    @Test
    public void emptyTilesAreSafeNoOps() {
        Tile tile = new Tile(0, 0, 0, 320);
        // No terrain data at all — migration must not throw or set anything.

        HytalePlantsLayer.migrateSurfaceOnlyFromTerrainLayer(Collections.singletonList(tile));

        assertEquals(0, HytaleTerrainLayer.getTerrainIndex(tile, 0, 0));
        assertEquals(0, HytalePlantsLayer.getPlantIndex(tile, 0, 0));
    }
}
