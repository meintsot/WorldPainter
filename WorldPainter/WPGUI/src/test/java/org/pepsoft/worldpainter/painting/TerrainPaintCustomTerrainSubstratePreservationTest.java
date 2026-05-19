package org.pepsoft.worldpainter.painting;

import org.junit.Test;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.TileFactory;
import org.pepsoft.worldpainter.TileFactoryFactory;
import org.pepsoft.worldpainter.World2;
import org.pepsoft.worldpainter.hytale.HytaleTerrain;
import org.pepsoft.worldpainter.hytale.HytaleTerrainLayer;

import static org.junit.Assert.assertEquals;
import static org.pepsoft.worldpainter.Constants.DIM_NORMAL;
import static org.pepsoft.worldpainter.DefaultPlugin.HYTALE;

/**
 * Regression test (TP-53 follow-up): painting a Custom Terrain on top of a tile
 * whose {@link HytaleTerrainLayer} already holds a substrate (e.g. the user just
 * painted Hytale Sand via the Hytale palette) must NOT clear that substrate.
 *
 * <p>Prior to this fix, {@link TerrainPaint#applyPixel} unconditionally cleared
 * both {@link HytaleTerrainLayer#LO} and {@link HytaleTerrainLayer#HI} to 0 every
 * time it ran — including when the target was a {@code Terrain.CUSTOM_N}. The
 * user's workflow "paint Sand via Hytale palette, then paint a Custom Terrain
 * plant-mix on top" therefore lost the Sand the instant the Custom Terrain paint
 * landed: tile.terrain became CUSTOM_1 and HytaleTerrainLayer was wiped to 0, so
 * the exporter's {@code getSurfaceOnlySubstrate} HytaleTerrain fallback never
 * fired and the surface defaulted to DIRT.
 *
 * <p>The fix: skip the HytaleTerrainLayer clear when the target Terrain is
 * custom ({@link Terrain#isCustom()}). Non-custom terrain paints continue to
 * clear HytaleTerrainLayer as before.
 */
public class TerrainPaintCustomTerrainSubstratePreservationTest {

    @Test
    public void paintingCustomTerrainPreservesHytaleTerrainLayerSubstrate() {
        Tile tile = setUpTile();
        // Simulate the user having just painted Hytale Sand via the Hytale palette:
        // HytalePlantsLayer.routePaint for a non-surface-only terrain writes
        // HytaleTerrainLayer with the painted terrain's index.
        HytaleTerrainLayer.setTerrainIndex(tile, 5, 7, HytaleTerrain.SAND.getLayerIndex());

        TerrainPaint customPaint = new TerrainPaint(Terrain.CUSTOM_1);
        customPaint.applyPixel(dimensionRef, worldX(tile, 5), worldZ(tile, 7));

        assertEquals("Painting a Custom Terrain must preserve the Hytale substrate the user "
                        + "set via the Hytale palette so the exporter can use it as the surface "
                        + "block under plant-mix custom terrains",
                HytaleTerrain.SAND.getLayerIndex(),
                HytaleTerrainLayer.getTerrainIndex(tile, 5, 7));
        assertEquals("The standard Terrain must be set to the painted Custom Terrain",
                Terrain.CUSTOM_1, tile.getTerrain(5, 7));
    }

    @Test
    public void paintingNonCustomTerrainStillClearsHytaleTerrainLayer() {
        Tile tile = setUpTile();
        HytaleTerrainLayer.setTerrainIndex(tile, 5, 7, HytaleTerrain.SAND.getLayerIndex());

        TerrainPaint sandPaint = new TerrainPaint(Terrain.SAND);
        sandPaint.applyPixel(dimensionRef, worldX(tile, 5), worldZ(tile, 7));

        assertEquals("Painting a non-Custom Terrain must clear HytaleTerrainLayer (existing TP-60 behavior — "
                        + "the user is replacing the Hytale substrate with a plain WP terrain)",
                0, HytaleTerrainLayer.getTerrainIndex(tile, 5, 7));
        assertEquals(Terrain.SAND, tile.getTerrain(5, 7));
    }

    private Dimension dimensionRef;

    private Tile setUpTile() {
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

    private int worldX(Tile tile, int xInTile) {
        return (tile.getX() << 7) + xInTile;
    }

    private int worldZ(Tile tile, int zInTile) {
        return (tile.getY() << 7) + zInTile;
    }
}
