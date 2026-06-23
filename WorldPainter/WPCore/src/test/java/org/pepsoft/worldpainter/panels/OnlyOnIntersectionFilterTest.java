package org.pepsoft.worldpainter.panels;

import org.junit.Test;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.operations.Filter;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OnlyOnIntersectionFilterTest {
    @Test
    public void createWithListAndIntersectionReturnsAllOfFilter() {
        Filter f = OnlyOnTerrainOrLayerFilter.create(null, List.of(Terrain.GRASS, Terrain.SAND), true);
        assertTrue("expected AllOfFilter, got " + f.getClass(), f instanceof AllOfFilter);
        assertEquals(2, ((AllOfFilter) f).getFilters().size());
    }

    @Test
    public void createWithListAndUnionReturnsAnyOfFilter() {
        Filter f = OnlyOnTerrainOrLayerFilter.create(null, List.of(Terrain.GRASS, Terrain.SAND), false);
        assertTrue("expected AnyOfFilter, got " + f.getClass(), f instanceof AnyOfFilter);
        assertEquals(2, ((AnyOfFilter) f).getFilters().size());
    }

    @Test
    public void createWithSingleItemIgnoresIntersection() {
        Filter f = OnlyOnTerrainOrLayerFilter.create(null, Terrain.GRASS, true);
        assertTrue(f instanceof OnlyOnTerrainOrLayerFilter);
    }

    @Test
    public void twoArgCreateStillDefaultsToUnion() {
        Filter f = OnlyOnTerrainOrLayerFilter.create(null, List.of(Terrain.GRASS, Terrain.SAND));
        assertTrue(f instanceof AnyOfFilter);
    }

    // Lock the union (max) vs intersection (min) semantics of the combiners themselves,
    // using stub sub-filters so no Dimension is needed.
    @Test
    public void anyOfIsUnion() {
        // List.<Filter>of(...) gives the lambdas their target functional-interface type.
        Filter union = new AnyOfFilter(List.<Filter>of(
                (x, y, s) -> (x == 1) ? s : 0.0f,
                (x, y, s) -> (x == 2) ? s : 0.0f));
        assertEquals(1.0f, union.modifyStrength(1, 0, 1.0f), 0.0f);
        assertEquals(1.0f, union.modifyStrength(2, 0, 1.0f), 0.0f);
        assertEquals(0.0f, union.modifyStrength(3, 0, 1.0f), 0.0f);
    }

    @Test
    public void allOfIsIntersection() {
        Filter intersection = new AllOfFilter(List.<Filter>of(
                (x, y, s) -> (x >= 1) ? s : 0.0f,
                (x, y, s) -> (x <= 1) ? s : 0.0f));
        assertEquals(1.0f, intersection.modifyStrength(1, 0, 1.0f), 0.0f); // both pass only at x==1
        assertEquals(0.0f, intersection.modifyStrength(0, 0, 1.0f), 0.0f);
        assertEquals(0.0f, intersection.modifyStrength(2, 0, 1.0f), 0.0f);
    }
}
