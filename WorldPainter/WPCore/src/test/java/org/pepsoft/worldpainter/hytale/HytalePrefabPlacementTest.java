package org.pepsoft.worldpainter.hytale;

import org.junit.Test;
import java.io.*;
import static org.junit.Assert.*;

public class HytalePrefabPlacementTest {
    @Test
    public void normalizesRotationIntoZeroTo360() {
        HytalePrefabPlacement p = new HytalePrefabPlacement(
                1L, "Prefabs/Trees/Oak.prefab.json", "Oak", 10, 20, 70, false, -90.0);
        assertEquals(270.0, p.getRotationDegrees(), 1.0e-9);
    }

    @Test
    public void withRotationKeepsIdentityAndPosition() {
        HytalePrefabPlacement p = new HytalePrefabPlacement(
                7L, "p.prefab.json", "P", 3, 4, null, true, 0.0);
        HytalePrefabPlacement r = p.withRotation(45.0);
        assertEquals(7L, r.getId());
        assertEquals(3, r.getX());
        assertEquals(4, r.getY());
        assertTrue(r.isSnapToSurface());
        assertEquals(45.0, r.getRotationDegrees(), 1.0e-9);
        assertEquals(p, r); // equality is by id
    }

    @Test
    public void serializesRoundTrip() throws Exception {
        HytalePrefabPlacement p = new HytalePrefabPlacement(
                42L, "Prefabs/Spawn/Camp.prefab.json", "Camp", -100, 250, 64, false, 137.5);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(p);
        }
        HytalePrefabPlacement back;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            back = (HytalePrefabPlacement) in.readObject();
        }
        assertEquals(42L, back.getId());
        assertEquals("Prefabs/Spawn/Camp.prefab.json", back.getPrefabPath());
        assertEquals("Camp", back.getPrefabName());
        assertEquals(-100, back.getX());
        assertEquals(250, back.getY());
        assertEquals(Integer.valueOf(64), back.getHeight());
        assertFalse(back.isSnapToSurface());
        assertEquals(137.5, back.getRotationDegrees(), 1.0e-9);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNaNRotation() {
        new HytalePrefabPlacement(1L, "p.prefab.json", "P", 0, 0, 0, false, Double.NaN);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInfiniteRotation() {
        new HytalePrefabPlacement(1L, "p.prefab.json", "P", 0, 0, 0, false, Double.POSITIVE_INFINITY);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNullHeightWithoutSnap() {
        new HytalePrefabPlacement(1L, "p.prefab.json", "P", 0, 0, null, false, 0.0);
    }

    @Test(expected = NullPointerException.class)
    public void rejectsNullPath() {
        new HytalePrefabPlacement(1L, null, "P", 0, 0, 0, false, 0.0);
    }

    @Test
    public void normalizesFullTurnAndLargeAngles() {
        assertEquals(0.0, new HytalePrefabPlacement(1L, "p", "P", 0, 0, 0, false, 360.0).getRotationDegrees(), 1.0e-9);
        assertEquals(5.0, new HytalePrefabPlacement(1L, "p", "P", 0, 0, 0, false, 725.0).getRotationDegrees(), 1.0e-9);
        // -360.0 normalizes to +0.0 (not -0.0)
        double zero = new HytalePrefabPlacement(1L, "p", "P", 0, 0, 0, false, -360.0).getRotationDegrees();
        assertEquals(0L, Double.doubleToRawLongBits(zero));
    }

    @Test
    public void withPositionAndWithHeightPreserveIdentity() {
        HytalePrefabPlacement p = new HytalePrefabPlacement(3L, "p", "P", 1, 2, 64, false, 0.0);
        HytalePrefabPlacement moved = p.withPosition(7, 8);
        assertEquals(7, moved.getX());
        assertEquals(8, moved.getY());
        assertEquals(3L, moved.getId());
        HytalePrefabPlacement snapped = p.withHeight(null, true);
        assertTrue(snapped.isSnapToSurface());
        assertNull(snapped.getHeight());
        assertEquals(3L, snapped.getId());
    }
}
