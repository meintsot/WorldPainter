package org.pepsoft.worldpainter.merging;

import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.TileFactory;
import org.pepsoft.worldpainter.TileFactoryFactory;
import org.pepsoft.worldpainter.World2;
import org.pepsoft.worldpainter.layers.Void;

import org.junit.Test;

import java.awt.Rectangle;

import static org.junit.Assert.*;
import static org.pepsoft.worldpainter.Constants.DIM_NORMAL;
import static org.pepsoft.worldpainter.Constants.TILE_SIZE;
import static org.pepsoft.worldpainter.DefaultPlugin.JAVA_ANVIL_1_20_5;

/**
 * Tests for {@link MergePreviewModel}, the Void-aware geometry the merge
 * preview uses so it matches what the editor canvas shows. The map importer
 * creates a Tile for every WP tile that contains at least one stored chunk and
 * marks non-imported pixels as {@link Void}; the canvas renders Void as
 * transparent. These tests pin down that fully-Void tiles are treated as
 * absent (no content) and excluded from the bounding box, while partially
 * imported tiles report their real coverage fraction.
 */
public class MergePreviewModelTest {

    private static final int MIN_H = 0;
    private static final int MAX_H = 320;

    private static TileFactory flatFactory() {
        return TileFactoryFactory.createFlatTileFactory(
            42L, Terrain.GRASS, MIN_H, MAX_H, 64, 62, false, false);
    }

    private static Dimension dimensionWith(Tile... tiles) {
        World2 world = new World2(JAVA_ANVIL_1_20_5, MIN_H, MAX_H);
        world.setName("t");
        Dimension.Anchor anchor = new Dimension.Anchor(DIM_NORMAL, Dimension.Role.DETAIL, false, 0);
        Dimension dim = new Dimension(world, "t", 42L, flatFactory(), anchor);
        dim.setEventsInhibited(true);
        for (Tile tile : tiles) {
            dim.addTile(tile);
        }
        dim.setEventsInhibited(false);
        world.addDimension(dim);
        return dim;
    }

    /** A normally-painted tile: no Void layer at all, so 100% content. */
    private static Tile contentTile(int x, int y) {
        return flatFactory().createTile(x, y);
    }

    /** A tile the importer created but populated with no real data. */
    private static Tile fullyVoidTile(int x, int y) {
        Tile tile = flatFactory().createTile(x, y);
        for (int px = 0; px < TILE_SIZE; px++) {
            for (int pz = 0; pz < TILE_SIZE; pz++) {
                tile.setBitLayerValue(Void.INSTANCE, px, pz, true);
            }
        }
        return tile;
    }

    /** A tile imported along an edge: left half Void, right half real data. */
    private static Tile halfVoidTile(int x, int y) {
        Tile tile = flatFactory().createTile(x, y);
        for (int px = 0; px < TILE_SIZE / 2; px++) {
            for (int pz = 0; pz < TILE_SIZE; pz++) {
                tile.setBitLayerValue(Void.INSTANCE, px, pz, true);
            }
        }
        return tile;
    }

    @Test
    public void hasContent_falseWhenEveryPixelIsVoid() {
        assertFalse(MergePreviewModel.hasContent(fullyVoidTile(0, 0)));
    }

    @Test
    public void hasContent_trueWhenSomePixelsAreImported() {
        assertTrue(MergePreviewModel.hasContent(halfVoidTile(0, 0)));
        assertTrue(MergePreviewModel.hasContent(contentTile(0, 0)));
    }

    @Test
    public void coverage_reflectsNonVoidFraction() {
        assertEquals(0f, MergePreviewModel.coverage(fullyVoidTile(0, 0)), 0.001f);
        assertEquals(1f, MergePreviewModel.coverage(contentTile(0, 0)), 0.001f);
        assertEquals(0.5f, MergePreviewModel.coverage(halfVoidTile(0, 0)), 0.001f);
    }

    @Test
    public void contentExtent_excludesFullyVoidTiles() {
        // Real square data at (2,3); a stray fully-Void tile far away at (9,9) —
        // the kind the importer creates for non-imported chunks — must not
        // inflate the preview's bounding box.
        Dimension dim = dimensionWith(contentTile(2, 3), fullyVoidTile(9, 9));
        assertEquals(new Rectangle(2, 3, 1, 1), MergePreviewModel.contentExtent(dim));
    }

    @Test
    public void contentExtent_spansAllContentTiles() {
        Dimension dim = dimensionWith(contentTile(2, 3), halfVoidTile(4, 3), fullyVoidTile(9, 9));
        // Bounding box over the two content tiles (2,3) and (4,3); the void tile is ignored.
        assertEquals(new Rectangle(2, 3, 3, 1), MergePreviewModel.contentExtent(dim));
    }

    @Test
    public void contentExtent_nullWhenNothingHasContent() {
        Dimension dim = dimensionWith(fullyVoidTile(0, 0), fullyVoidTile(1, 1));
        assertNull(MergePreviewModel.contentExtent(dim));
    }
}
