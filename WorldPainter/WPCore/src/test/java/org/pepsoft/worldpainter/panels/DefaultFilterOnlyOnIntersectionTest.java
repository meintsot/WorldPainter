package org.pepsoft.worldpainter.panels;

import org.junit.Test;
import org.pepsoft.worldpainter.Terrain;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DefaultFilterOnlyOnIntersectionTest {
    @Test
    public void intersectionBuildsAllOfFilter() {
        DefaultFilter f = new DefaultFilter(null, false, false,
                Integer.MIN_VALUE, Integer.MIN_VALUE, false,
                true, List.of(Terrain.GRASS, Terrain.SAND), true,
                false, null, -1, false);
        assertTrue(f.onlyOnFilter instanceof AllOfFilter);
        assertTrue(f.isOnlyOnIntersection());
    }

    @Test
    public void unionBuildsAnyOfFilter() {
        DefaultFilter f = new DefaultFilter(null, false, false,
                Integer.MIN_VALUE, Integer.MIN_VALUE, false,
                true, List.of(Terrain.GRASS, Terrain.SAND), false,
                false, null, -1, false);
        assertTrue(f.onlyOnFilter instanceof AnyOfFilter);
        assertFalse(f.isOnlyOnIntersection());
    }

    @Test
    public void legacyConstructorDefaultsToUnion() {
        DefaultFilter f = new DefaultFilter(null, false, false,
                Integer.MIN_VALUE, Integer.MIN_VALUE, false,
                true, List.of(Terrain.GRASS, Terrain.SAND),
                false, null, -1, false);
        assertTrue(f.onlyOnFilter instanceof AnyOfFilter);
        assertFalse(f.isOnlyOnIntersection());
    }

    @Test
    public void builderSupportsIntersection() {
        DefaultFilter f = DefaultFilter.buildForDimension(null)
                .onlyOn(List.of(Terrain.GRASS, Terrain.SAND))
                .onlyOnIntersection(true)
                .build();
        assertTrue(f.onlyOnFilter instanceof AllOfFilter);
    }
}
