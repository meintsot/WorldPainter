package org.pepsoft.worldpainter.hytale;

import org.junit.Test;
import org.pepsoft.worldpainter.layers.Layer;

import java.awt.*;
import java.io.*;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class HytaleSpecificPrefabLayerTest {
    @Test
    public void testConstructionAndGetters() {
        List<PrefabFileEntry> entries = Arrays.asList(
            new PrefabFileEntry("Oak1", "Trees", "Oak", "Prefabs/Trees/Oak/Oak1.prefab.json"),
            new PrefabFileEntry("Oak2", "Trees", "Oak", "Prefabs/Trees/Oak/Oak2.prefab.json")
        );
        HytaleSpecificPrefabLayer layer = new HytaleSpecificPrefabLayer(
            "My Oak Trees", entries, new Color(0x228B22));

        assertEquals("My Oak Trees", layer.getName());
        assertEquals(Layer.DataSize.BIT, layer.getDataSize());
        assertEquals(2, layer.getPrefabEntries().size());
        assertEquals(new Color(0x228B22), layer.getColor());
    }

    @Test
    public void testDeterministicPrefabSelection() {
        List<PrefabFileEntry> entries = Arrays.asList(
            new PrefabFileEntry("A", "Trees", "Oak", "Prefabs/Trees/Oak/A.prefab.json"),
            new PrefabFileEntry("B", "Trees", "Oak", "Prefabs/Trees/Oak/B.prefab.json"),
            new PrefabFileEntry("C", "Trees", "Oak", "Prefabs/Trees/Oak/C.prefab.json")
        );
        HytaleSpecificPrefabLayer layer = new HytaleSpecificPrefabLayer(
            "Test", entries, Color.GREEN);

        // Same coordinates should always produce same result
        PrefabFileEntry first = layer.selectPrefab(100, 200);
        PrefabFileEntry second = layer.selectPrefab(100, 200);
        assertEquals(first, second);

        // Single-entry layer always returns that entry
        HytaleSpecificPrefabLayer single = new HytaleSpecificPrefabLayer(
            "Single", entries.subList(0, 1), Color.RED);
        assertEquals(entries.get(0), single.selectPrefab(999, 888));
    }

    @Test
    public void testSerialization() throws Exception {
        List<PrefabFileEntry> entries = Arrays.asList(
            new PrefabFileEntry("Oak1", "Trees", "Oak", "Prefabs/Trees/Oak/Oak1.prefab.json"),
            new PrefabFileEntry("Birch1", "Trees", "Birch", "Prefabs/Trees/Birch/Birch1.prefab.json")
        );
        HytaleSpecificPrefabLayer layer = new HytaleSpecificPrefabLayer(
            "Mixed Trees", entries, new Color(0x44, 0x88, 0x22));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(layer);
        oos.close();

        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()));
        HytaleSpecificPrefabLayer deserialized = (HytaleSpecificPrefabLayer) ois.readObject();

        assertEquals("Mixed Trees", deserialized.getName());
        assertEquals(2, deserialized.getPrefabEntries().size());
        assertEquals(new Color(0x44, 0x88, 0x22), deserialized.getColor());
        assertEquals(layer.getPrefabEntries().get(0), deserialized.getPrefabEntries().get(0));
    }

    @Test
    public void testIconIsColoredSquare() {
        HytaleSpecificPrefabLayer layer = new HytaleSpecificPrefabLayer(
            "Test", Arrays.asList(
                new PrefabFileEntry("A", "T", "S", "Prefabs/T/S/A.prefab.json")),
            new Color(0xFF, 0x00, 0x00));
        assertNotNull(layer.getIcon());
        assertEquals(16, layer.getIcon().getWidth());
        assertEquals(16, layer.getIcon().getHeight());
        // Center pixel should be red
        int centerRGB = layer.getIcon().getRGB(8, 8) & 0x00FFFFFF;
        assertEquals(0xFF0000, centerRGB);
    }
}
