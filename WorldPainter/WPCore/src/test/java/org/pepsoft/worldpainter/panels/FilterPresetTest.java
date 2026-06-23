package org.pepsoft.worldpainter.panels;

import org.junit.Test;
import org.pepsoft.worldpainter.Terrain;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FilterPresetTest {
    @Test
    public void capturesAndResolvesIntersectionFlag() {
        FilterPreset preset = new FilterPreset("test");
        preset.captureFrom(false, false, Integer.MIN_VALUE, Integer.MIN_VALUE, false,
                List.of(Terrain.GRASS, Terrain.SAND), null, -1, false, true);
        assertTrue(preset.isOnlyOnIntersection());
        Object onlyOn = preset.resolveOnlyOn();
        assertTrue(onlyOn instanceof List);
        assertEquals(2, ((List<?>) onlyOn).size());
    }

    @Test
    public void defaultsToUnion() {
        FilterPreset preset = new FilterPreset("test");
        assertFalse(preset.isOnlyOnIntersection());
    }

    @Test
    public void roundTripsThroughSerialization() throws Exception {
        FilterPreset preset = new FilterPreset("test");
        preset.captureFrom(false, false, Integer.MIN_VALUE, Integer.MIN_VALUE, false,
                List.of(Terrain.GRASS, Terrain.SAND), null, -1, false, true);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(preset);
        }
        FilterPreset restored;
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))) {
            restored = (FilterPreset) ois.readObject();
        }
        assertTrue(restored.isOnlyOnIntersection());
    }
}
