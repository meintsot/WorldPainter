package org.pepsoft.worldpainter.hytale;

import org.junit.Test;
import static org.junit.Assert.*;

public class PrefabFileEntryTest {
    @Test
    public void testFieldsAndToString() {
        PrefabFileEntry entry = new PrefabFileEntry(
            "Oak_Stage5_003", "Trees", "Oak",
            "Prefabs/Trees/Oak/Stage_5/Oak_Stage5_003.prefab.json");
        assertEquals("Oak_Stage5_003", entry.getDisplayName());
        assertEquals("Trees", entry.getCategory());
        assertEquals("Oak", entry.getSubCategory());
        assertEquals("Prefabs/Trees/Oak/Stage_5/Oak_Stage5_003.prefab.json", entry.getRelativePath());
        assertEquals("[Trees] Oak / Oak_Stage5_003", entry.toString());
    }

    @Test
    public void testEqualsAndHashCode() {
        PrefabFileEntry a = new PrefabFileEntry("Oak1", "Trees", "Oak", "Prefabs/Trees/Oak/Oak1.prefab.json");
        PrefabFileEntry b = new PrefabFileEntry("Oak1", "Trees", "Oak", "Prefabs/Trees/Oak/Oak1.prefab.json");
        PrefabFileEntry c = new PrefabFileEntry("Birch1", "Trees", "Birch", "Prefabs/Trees/Birch/Birch1.prefab.json");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    public void testSerializable() throws Exception {
        PrefabFileEntry entry = new PrefabFileEntry("Oak1", "Trees", "Oak", "Prefabs/Trees/Oak/Oak1.prefab.json");
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(baos);
        oos.writeObject(entry);
        oos.close();
        java.io.ObjectInputStream ois = new java.io.ObjectInputStream(
            new java.io.ByteArrayInputStream(baos.toByteArray()));
        PrefabFileEntry deserialized = (PrefabFileEntry) ois.readObject();
        assertEquals(entry, deserialized);
        assertEquals(entry.getRelativePath(), deserialized.getRelativePath());
    }
}
