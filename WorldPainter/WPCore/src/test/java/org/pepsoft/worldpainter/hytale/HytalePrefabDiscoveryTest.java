package org.pepsoft.worldpainter.hytale;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.util.List;

import static org.junit.Assert.*;

public class HytalePrefabDiscoveryTest {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testDiscoversPrefabFiles() throws Exception {
        // Create: Prefabs/Trees/Oak/Stage_5/Oak_Stage5_003.prefab.json
        File prefabs = tempFolder.newFolder("Prefabs");
        File trees = new File(prefabs, "Trees");
        File oak = new File(trees, "Oak");
        File stage5 = new File(oak, "Stage_5");
        stage5.mkdirs();
        File prefabFile = new File(stage5, "Oak_Stage5_003.prefab.json");
        try (FileWriter w = new FileWriter(prefabFile)) { w.write("{}"); }

        // Create: Prefabs/Npc/Kweebec/Oak/Kweebec_Village_01.prefab.json
        File npc = new File(prefabs, "Npc");
        File kweebec = new File(npc, "Kweebec");
        File kweebecOak = new File(kweebec, "Oak");
        kweebecOak.mkdirs();
        File npcFile = new File(kweebecOak, "Kweebec_Village_01.prefab.json");
        try (FileWriter w = new FileWriter(npcFile)) { w.write("{}"); }

        List<PrefabFileEntry> entries = HytalePrefabDiscovery.discoverPrefabs(tempFolder.getRoot());

        assertEquals(2, entries.size());

        // Verify Oak tree entry
        PrefabFileEntry oakEntry = entries.stream()
            .filter(e -> e.getDisplayName().equals("Oak_Stage5_003"))
            .findFirst().orElse(null);
        assertNotNull(oakEntry);
        assertEquals("Trees", oakEntry.getCategory());
        assertEquals("Oak", oakEntry.getSubCategory());
        assertEquals("Prefabs/Trees/Oak/Stage_5/Oak_Stage5_003.prefab.json", oakEntry.getRelativePath());

        // Verify NPC entry
        PrefabFileEntry npcEntry = entries.stream()
            .filter(e -> e.getDisplayName().equals("Kweebec_Village_01"))
            .findFirst().orElse(null);
        assertNotNull(npcEntry);
        assertEquals("Npc", npcEntry.getCategory());
        assertEquals("Kweebec", npcEntry.getSubCategory());
    }

    @Test
    public void testEmptyDirectoryReturnsEmptyList() throws Exception {
        File prefabs = tempFolder.newFolder("Prefabs");
        List<PrefabFileEntry> entries = HytalePrefabDiscovery.discoverPrefabs(tempFolder.getRoot());
        assertTrue(entries.isEmpty());
    }

    @Test
    public void testNoPrefabsDirectoryReturnsEmptyList() throws Exception {
        List<PrefabFileEntry> entries = HytalePrefabDiscovery.discoverPrefabs(tempFolder.getRoot());
        assertTrue(entries.isEmpty());
    }

    @Test
    public void testIgnoresNonPrefabJsonFiles() throws Exception {
        File prefabs = tempFolder.newFolder("Prefabs");
        File trees = new File(prefabs, "Trees");
        File oak = new File(trees, "Oak");
        oak.mkdirs();
        // .json but not .prefab.json
        File notPrefab = new File(oak, "readme.json");
        try (FileWriter w = new FileWriter(notPrefab)) { w.write("{}"); }

        List<PrefabFileEntry> entries = HytalePrefabDiscovery.discoverPrefabs(tempFolder.getRoot());
        assertTrue(entries.isEmpty());
    }

    @Test
    public void testSearchFiltering() throws Exception {
        File prefabs = tempFolder.newFolder("Prefabs");
        File trees = new File(prefabs, "Trees");
        File oak = new File(trees, "Oak");
        File stage = new File(oak, "Stage_5");
        stage.mkdirs();
        File f1 = new File(stage, "Oak_Stage5_001.prefab.json");
        try (FileWriter w = new FileWriter(f1)) { w.write("{}"); }
        File birch = new File(trees, "Birch");
        File bStage = new File(birch, "Stage_1");
        bStage.mkdirs();
        File f2 = new File(bStage, "Birch_Stage1_001.prefab.json");
        try (FileWriter w = new FileWriter(f2)) { w.write("{}"); }

        List<PrefabFileEntry> all = HytalePrefabDiscovery.discoverPrefabs(tempFolder.getRoot());
        assertEquals(2, all.size());

        // Filter by "birch"
        long birchCount = all.stream().filter(e -> e.matchesSearch("birch")).count();
        assertEquals(1, birchCount);
    }
}
