package org.pepsoft.worldpainter.hytale.prefab;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.*;

public class HytalePrefabThumbnailRendererTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private File writePrefab() throws Exception {
        String json = "{ \"anchorX\":0,\"anchorY\":0,\"anchorZ\":0, \"blocks\":["
                + "{ \"x\":0,\"y\":0,\"z\":0,\"name\":\"Rock_Stone\",\"rotation\":0 },"
                + "{ \"x\":1,\"y\":0,\"z\":0,\"name\":\"Soil_Grass\",\"rotation\":0 },"
                + "{ \"x\":0,\"y\":1,\"z\":1,\"name\":\"Rock_Stone\",\"rotation\":0 } ] }";
        File assets = tmp.newFolder("HytaleAssets");
        File dir = new File(assets, "Server/Prefabs/Test");
        assertTrue(dir.mkdirs());
        File f = new File(dir, "thumb.prefab.json");
        Files.write(f.toPath(), json.getBytes(StandardCharsets.UTF_8));
        return assets;
    }

    @Test
    public void rendersNonEmptyImage() throws Exception {
        File assets = writePrefab();
        HytalePrefabThumbnailRenderer renderer = new HytalePrefabThumbnailRenderer(assets);
        BufferedImage img = renderer.render("Prefabs/Test/thumb.prefab.json", 64);
        assertNotNull(img);
        assertEquals(64, img.getWidth());
        assertEquals(64, img.getHeight());
        boolean anyOpaque = false;
        for (int y = 0; y < img.getHeight() && !anyOpaque; y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if (((img.getRGB(x, y) >>> 24) & 0xFF) != 0) { anyOpaque = true; break; }
            }
        }
        assertTrue("expected at least one opaque pixel", anyOpaque);
    }

    @Test
    public void cachesByPathAndSize() throws Exception {
        File assets = writePrefab();
        HytalePrefabThumbnailRenderer renderer = new HytalePrefabThumbnailRenderer(assets);
        BufferedImage a = renderer.render("Prefabs/Test/thumb.prefab.json", 48);
        BufferedImage b = renderer.render("Prefabs/Test/thumb.prefab.json", 48);
        assertSame(a, b);
    }

    @Test
    public void missingPrefabReturnsPlaceholderNotNull() {
        HytalePrefabThumbnailRenderer renderer = new HytalePrefabThumbnailRenderer(null);
        BufferedImage img = renderer.render("Prefabs/Nope/missing.prefab.json", 32);
        assertNotNull(img);
        assertEquals(32, img.getWidth());
    }
}
