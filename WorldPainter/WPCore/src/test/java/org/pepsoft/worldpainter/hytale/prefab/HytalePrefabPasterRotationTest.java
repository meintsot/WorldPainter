package org.pepsoft.worldpainter.hytale.prefab;

import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.Rule;
import org.pepsoft.worldpainter.hytale.HytaleBlock;
import org.pepsoft.worldpainter.hytale.chunk.HytaleChunk;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class HytalePrefabPasterRotationTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private File writeSinglePrefab() throws Exception {
        String json = "{ \"anchorX\":0, \"anchorY\":0, \"anchorZ\":0, "
                + "\"blocks\": [ { \"x\":2, \"y\":0, \"z\":0, \"name\":\"Rock_Stone\", \"rotation\":0 } ] }";
        File assets = tmp.newFolder("HytaleAssets");
        File dir = new File(assets, "Server/Prefabs/Test");
        assertTrue(dir.mkdirs());
        File file = new File(dir, "single.prefab.json");
        Files.write(file.toPath(), json.getBytes(StandardCharsets.UTF_8));
        return assets;
    }

    private static long chunkKey(int cx, int cz) {
        return (((long) cx) << 32) ^ (cz & 0xFFFFFFFFL);
    }

    @Test
    public void rotates90AboutAnchor() throws Exception {
        File assets = writeSinglePrefab();
        HytalePrefabPaster paster = new HytalePrefabPaster(assets);

        HytaleChunk chunk = new HytaleChunk(0, 0, 0, 320);
        Map<Long, HytaleChunk> chunks = new HashMap<>();
        chunks.put(chunkKey(0, 0), chunk);

        boolean ok = paster.paste(chunks, 0, 64, 0, 0, 0,
                "Prefabs/Test/single.prefab.json", 90.0);
        assertTrue(ok);

        HytaleBlock atRotated = chunk.getHytaleBlock(0, 64, 2);
        assertNotNull(atRotated);
        assertEquals("Rock_Stone", atRotated.id);
        HytaleBlock atOriginal = chunk.getHytaleBlock(2, 64, 0);
        assertTrue(atOriginal == null || "Empty".equals(atOriginal.id));
    }

    @Test
    public void zeroRotationStillPastes() throws Exception {
        File assets = writeSinglePrefab();
        HytalePrefabPaster paster = new HytalePrefabPaster(assets);
        HytaleChunk chunk = new HytaleChunk(0, 0, 0, 320);
        Map<Long, HytaleChunk> chunks = new HashMap<>();
        chunks.put(chunkKey(0, 0), chunk);
        assertTrue(paster.paste(chunks, 0, 64, 0, 0, 0, "Prefabs/Test/single.prefab.json", 0.0));
        HytaleBlock at = chunk.getHytaleBlock(2, 64, 0);
        assertNotNull(at);
        assertEquals("Rock_Stone", at.id);
    }
}
