package org.pepsoft.worldpainter.hytale;

import org.junit.Test;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.TestData;

import java.awt.Rectangle;
import java.io.*;

import static org.junit.Assert.*;
import static org.pepsoft.worldpainter.Constants.TILE_SIZE;

public class DimensionPrefabPlacementsTest {

    private static Dimension roundTrip(Dimension dim) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(dim);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (Dimension) in.readObject();
        }
    }

    @Test
    public void emptyByDefault() {
        Dimension dim = TestData.createDimension(new Rectangle(0, 0, TILE_SIZE, TILE_SIZE), 64);
        assertNotNull(dim.getHytalePrefabPlacements());
        assertTrue(dim.getHytalePrefabPlacements().isEmpty());
    }

    @Test
    public void addRemoveReplace() {
        Dimension dim = TestData.createDimension(new Rectangle(0, 0, TILE_SIZE, TILE_SIZE), 64);
        HytalePrefabPlacement p = new HytalePrefabPlacement(1L, "a.prefab.json", "A", 5, 6, 70, false, 0.0);
        dim.addHytalePrefabPlacement(p);
        assertEquals(1, dim.getHytalePrefabPlacements().size());

        HytalePrefabPlacement moved = p.withPosition(9, 9);
        dim.replaceHytalePrefabPlacement(p, moved);   // replace matches by id (equals)
        assertEquals(9, dim.getHytalePrefabPlacements().get(0).getX());

        assertTrue(dim.removeHytalePrefabPlacement(moved));
        assertTrue(dim.getHytalePrefabPlacements().isEmpty());
    }

    @Test
    public void survivesSerialization() throws Exception {
        Dimension dim = TestData.createDimension(new Rectangle(0, 0, TILE_SIZE, TILE_SIZE), 64);
        dim.addHytalePrefabPlacement(
                new HytalePrefabPlacement(11L, "Prefabs/x.prefab.json", "X", 12, 34, null, true, 90.0));
        Dimension back = roundTrip(dim);
        assertEquals(1, back.getHytalePrefabPlacements().size());
        HytalePrefabPlacement p = back.getHytalePrefabPlacements().get(0);
        assertEquals("Prefabs/x.prefab.json", p.getPrefabPath());
        assertEquals(12, p.getX());
        assertTrue(p.isSnapToSurface());
        assertEquals(90.0, p.getRotationDegrees(), 1.0e-9);
    }

    @Test
    public void returnedListIsUnmodifiable() {
        Dimension dim = TestData.createDimension(new Rectangle(0, 0, TILE_SIZE, TILE_SIZE), 64);
        try {
            dim.getHytalePrefabPlacements().add(
                    new HytalePrefabPlacement(2L, "b.prefab.json", "B", 0, 0, 0, false, 0.0));
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // good
        }
    }
}
