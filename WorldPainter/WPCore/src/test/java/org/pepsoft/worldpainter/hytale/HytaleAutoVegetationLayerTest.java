package org.pepsoft.worldpainter.hytale;

import org.junit.Test;
import org.pepsoft.worldpainter.layers.Layer;

import java.io.*;

import static org.junit.Assert.*;

public class HytaleAutoVegetationLayerTest {

    @Test
    public void singletonInstanceIsBitPerPixelLayer() {
        Layer layer = HytaleAutoVegetationLayer.INSTANCE;
        assertNotNull(layer);
        assertEquals("HyAutoVeg", layer.getId());
        assertEquals("Auto Vegetation", layer.getName());
        assertEquals(Layer.DataSize.BIT, layer.getDataSize());
        assertEquals(0, layer.getDefaultValue());
    }

    @Test
    public void serializationRoundTripReturnsSameInstance() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bytes)) {
            oos.writeObject(HytaleAutoVegetationLayer.INSTANCE);
        }
        Object readBack;
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            readBack = ois.readObject();
        }
        assertSame("readResolve must return the singleton", HytaleAutoVegetationLayer.INSTANCE, readBack);
    }
}
