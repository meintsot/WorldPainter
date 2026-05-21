package org.pepsoft.worldpainter.merging;

import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.TileFactory;
import org.pepsoft.worldpainter.TileFactoryFactory;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.World2;
import org.pepsoft.worldpainter.layers.Frost;

import org.junit.Test;

import java.awt.Point;

import static org.junit.Assert.*;
import static org.pepsoft.worldpainter.Constants.DIM_NORMAL;
import static org.pepsoft.worldpainter.DefaultPlugin.JAVA_ANVIL_1_20_5;

public class TileMapMergerTest {

    private static final int MIN_H = 0;
    private static final int MAX_H = 320;

    private static Dimension makeDimension(String name, int[][] tileCoords) {
        World2 world = new World2(JAVA_ANVIL_1_20_5, MIN_H, MAX_H);
        world.setName(name);
        TileFactory tileFactory = TileFactoryFactory.createFlatTileFactory(
            42L, Terrain.GRASS, MIN_H, MAX_H, 64, 62, false, false);
        Dimension.Anchor anchor = new Dimension.Anchor(DIM_NORMAL, Dimension.Role.DETAIL, false, 0);
        Dimension dim = new Dimension(world, name, 42L, tileFactory, anchor);
        dim.setEventsInhibited(true);
        for (int[] xy : tileCoords) {
            dim.addTile(tileFactory.createTile(xy[0], xy[1]));
        }
        dim.setEventsInhibited(false);
        world.addDimension(dim);
        return dim;
    }

    @Test
    public void offset_east_alignsSourceLeftEdgeNextToTargetRight() {
        Dimension t = makeDimension("t", new int[][] { {0, 0}, {1, 0} }); // 2 tiles wide
        Dimension s = makeDimension("s", new int[][] { {0, 0} });         // 1 tile wide at (0,0)
        Point off = TileMapMerger.computeOffset(t, s, TileMergeSettings.Side.EAST);
        // target maxX = 1; source must land at tileX = 2 → offset.x = 2
        assertEquals(2, off.x);
        assertEquals(0, off.y);
    }

    @Test
    public void offset_west_alignsSourceRightEdgeNextToTargetLeft() {
        Dimension t = makeDimension("t", new int[][] { {0, 0}, {1, 0} });
        Dimension s = makeDimension("s", new int[][] { {0, 0} });
        Point off = TileMapMerger.computeOffset(t, s, TileMergeSettings.Side.WEST);
        // target minX = 0; source max → tileX = -1 → offset = -1 - 0 = -1
        assertEquals(-1, off.x);
    }

    @Test
    public void offset_north_alignsSourceBottomNextToTargetTop() {
        Dimension t = makeDimension("t", new int[][] { {0, 0}, {0, 1} });
        Dimension s = makeDimension("s", new int[][] { {0, 0} });
        Point off = TileMapMerger.computeOffset(t, s, TileMergeSettings.Side.NORTH);
        assertEquals(-1, off.y);
        assertEquals(0, off.x);
    }

    @Test
    public void offset_south_alignsSourceTopNextToTargetBottom() {
        Dimension t = makeDimension("t", new int[][] { {0, 0}, {0, 1} });
        Dimension s = makeDimension("s", new int[][] { {0, 0} });
        Point off = TileMapMerger.computeOffset(t, s, TileMergeSettings.Side.SOUTH);
        assertEquals(2, off.y);
        assertEquals(0, off.x);
    }

    @Test
    public void merge_east_addsAllSourceTilesNoOverlap() {
        Dimension t = makeDimension("t", new int[][] { {0, 0} });
        Dimension s = makeDimension("s", new int[][] { {0, 0}, {0, 1} });
        TileMergeSettings settings = TileMergeSettings.builder()
            .side(TileMergeSettings.Side.EAST).build();

        TileMapMerger.Result r = TileMapMerger.merge(t, s, settings);

        assertEquals(2, r.tilesAdded);
        assertEquals(0, r.tilesReplaced);
        assertEquals(0, r.tilesMerged);
        assertEquals(3, t.getTileCount());
        assertNotNull(t.getTile(1, 0)); // pasted from source (0,0)
        assertNotNull(t.getTile(1, 1)); // pasted from source (0,1)
    }

    @Test(expected = IllegalStateException.class)
    public void merge_reject_throwsIfAnyOverlap() {
        Dimension t = makeDimension("t", new int[][] { {0, 0} });
        Dimension s = makeDimension("s", new int[][] { {0, 0} });
        TileMergeSettings settings = TileMergeSettings.builder()
            .side(TileMergeSettings.Side.EAST)
            .overlapPolicy(TileMergeSettings.OverlapPolicy.REJECT)
            .build();
        // Explicit zero offset forces source (0,0) to land on target (0,0) — overlap.
        TileMapMerger.mergeAt(t, s, settings, new Point(0, 0));
    }

    @Test
    public void merge_replace_overwritesOverlappingTile() {
        Dimension t = makeDimension("t", new int[][] { {0, 0} });
        Tile tTile = t.getTile(0, 0);
        tTile.setHeight(64, 64, 200f);

        Dimension s = makeDimension("s", new int[][] { {0, 0} });
        Tile sTile = s.getTile(0, 0);
        sTile.setHeight(64, 64, 50f);

        TileMergeSettings settings = TileMergeSettings.builder()
            .side(TileMergeSettings.Side.EAST)
            .overlapPolicy(TileMergeSettings.OverlapPolicy.REPLACE)
            .build();
        TileMapMerger.Result r = TileMapMerger.mergeAt(t, s, settings, new Point(0, 0));

        assertEquals(0, r.tilesAdded);
        assertEquals(1, r.tilesReplaced);
        Tile replaced = t.getTile(0, 0);
        assertEquals(50f, replaced.getHeight(64, 64), 0.001f);
    }

    @Test
    public void merge_mergePolicy_takesImportedHeightsAndLayersOnlyWhenFlagged() {
        Dimension t = makeDimension("t", new int[][] { {0, 0} });
        Tile tTile = t.getTile(0, 0);
        tTile.setHeight(64, 64, 200f);
        tTile.setBitLayerValue(Frost.INSTANCE, 64, 64, true);

        Dimension s = makeDimension("s", new int[][] { {0, 0} });
        Tile sTile = s.getTile(0, 0);
        sTile.setHeight(64, 64, 50f);
        // Source has no Frost — leaving useImportedLayers=false should keep target's Frost.

        TileMergeSettings settings = TileMergeSettings.builder()
            .side(TileMergeSettings.Side.EAST)
            .overlapPolicy(TileMergeSettings.OverlapPolicy.MERGE)
            .useImportedHeights(true)
            .useImportedTerrain(false)
            .useImportedLayers(false)
            .useImportedBiomes(false)
            .build();
        TileMapMerger.Result r = TileMapMerger.mergeAt(t, s, settings, new Point(0, 0));

        assertEquals(1, r.tilesMerged);
        Tile merged = t.getTile(0, 0);
        assertEquals(50f, merged.getHeight(64, 64), 0.001f);
        assertTrue("Frost bit should be preserved from target when useImportedLayers=false",
            merged.getBitLayerValue(Frost.INSTANCE, 64, 64));
    }

    @Test(expected = IllegalArgumentException.class)
    public void merge_heightMismatch_throws() {
        Dimension t = makeDimension("t", new int[][] { {0, 0} });
        // Source with different max height.
        World2 world = new World2(JAVA_ANVIL_1_20_5, MIN_H, 512);
        world.setName("s512");
        TileFactory tf = TileFactoryFactory.createFlatTileFactory(
            42L, Terrain.GRASS, MIN_H, 512, 64, 62, false, false);
        Dimension.Anchor anchor = new Dimension.Anchor(DIM_NORMAL, Dimension.Role.DETAIL, false, 0);
        Dimension s = new Dimension(world, "s512", 42L, tf, anchor);
        s.setEventsInhibited(true);
        s.addTile(tf.createTile(0, 0));
        s.setEventsInhibited(false);
        world.addDimension(s);

        TileMergeSettings settings = TileMergeSettings.builder()
            .side(TileMergeSettings.Side.EAST).build();
        TileMapMerger.merge(t, s, settings);
    }
}
