package org.pepsoft.worldpainter.hytale;

import org.junit.Test;

import java.awt.*;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class HytaleSpecificPrefabExportTest {
    @Test
    public void testSelectPrefabIsDeterministic() {
        List<PrefabFileEntry> entries = Arrays.asList(
            new PrefabFileEntry("Oak1", "Trees", "Oak", "Prefabs/Trees/Oak/Stage_5/Oak1.prefab.json"),
            new PrefabFileEntry("Oak2", "Trees", "Oak", "Prefabs/Trees/Oak/Stage_5/Oak2.prefab.json"),
            new PrefabFileEntry("Oak3", "Trees", "Oak", "Prefabs/Trees/Oak/Stage_5/Oak3.prefab.json")
        );
        HytaleSpecificPrefabLayer layer = new HytaleSpecificPrefabLayer("Oaks", entries, Color.GREEN);

        // Verify determinism: same coords -> same result
        for (int x = -100; x < 100; x += 13) {
            for (int z = -100; z < 100; z += 17) {
                PrefabFileEntry a = layer.selectPrefab(x, z);
                PrefabFileEntry b = layer.selectPrefab(x, z);
                assertSame("Deterministic at (" + x + "," + z + ")", a, b);
                assertTrue(entries.contains(a));
            }
        }
    }

    @Test
    public void testSelectPrefabDistribution() {
        List<PrefabFileEntry> entries = Arrays.asList(
            new PrefabFileEntry("A", "T", "S", "Prefabs/T/S/A.prefab.json"),
            new PrefabFileEntry("B", "T", "S", "Prefabs/T/S/B.prefab.json"),
            new PrefabFileEntry("C", "T", "S", "Prefabs/T/S/C.prefab.json")
        );
        HytaleSpecificPrefabLayer layer = new HytaleSpecificPrefabLayer("Test", entries, Color.RED);

        int[] counts = new int[3];
        for (int x = 0; x < 300; x++) {
            for (int z = 0; z < 300; z++) {
                PrefabFileEntry selected = layer.selectPrefab(x, z);
                counts[entries.indexOf(selected)]++;
            }
        }

        // Each should get roughly 30000 (90000/3). Allow +/- 30% tolerance.
        for (int i = 0; i < 3; i++) {
            assertTrue("Entry " + i + " count " + counts[i] + " should be between 21000 and 39000",
                counts[i] > 21000 && counts[i] < 39000);
        }
    }
}
