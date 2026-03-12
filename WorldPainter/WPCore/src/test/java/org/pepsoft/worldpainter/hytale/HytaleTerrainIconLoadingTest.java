package org.pepsoft.worldpainter.hytale;

import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HytaleTerrainIconLoadingTest {

    @Test
    public void testExplicitIconPathFromBlockAssetIsLoaded() throws Exception {
        Path assetsDir = Files.createTempDirectory("hytale-icon-assets");
        File previousAssetsDir = HytaleTerrain.getHytaleAssetsDir();
        try {
            Files.createDirectories(assetsDir.resolve("Server").resolve("BlockTypeList"));
            Files.createDirectories(assetsDir.resolve("Server").resolve("Item").resolve("Items").resolve("Test"));
            Files.createDirectories(assetsDir.resolve("Common").resolve("Icons").resolve("ItemsGenerated"));

            writeJson(assetsDir.resolve("Server").resolve("Item").resolve("Items").resolve("Test").resolve("Test_Metadata_Icon.json"),
                    "{\n" +
                            "  \"Icon\": \"Icons/ItemsGenerated/ExplicitIcon.png\"\n" +
                            "}\n");
            writeSolidPng(assetsDir.resolve("Common").resolve("Icons").resolve("ItemsGenerated").resolve("ExplicitIcon.png"), 0xFFFF0000);

            HytaleTerrain.setHytaleAssetsDir(assetsDir.toFile());
            HytaleTerrain terrain = new HytaleTerrain("Test Metadata Icon", HytaleBlock.of("Test_Metadata_Icon"), 0x0000FF);

            BufferedImage icon = terrain.getIcon(null);

            assertEquals(0xFFFF0000, icon.getRGB(0, 0));
        } finally {
            HytaleTerrain.setHytaleAssetsDir(previousAssetsDir);
        }
    }

    @Test
    public void testExplicitTexturePathFromBlockAssetIsLoaded() throws Exception {
        Path assetsDir = Files.createTempDirectory("hytale-texture-assets");
        File previousAssetsDir = HytaleTerrain.getHytaleAssetsDir();
        try {
            Files.createDirectories(assetsDir.resolve("Server").resolve("BlockTypeList"));
            Files.createDirectories(assetsDir.resolve("Server").resolve("Item").resolve("Items").resolve("Test"));
            Files.createDirectories(assetsDir.resolve("Common").resolve("BlockTextures"));

            writeJson(assetsDir.resolve("Server").resolve("Item").resolve("Items").resolve("Test").resolve("Test_Metadata_Texture.json"),
                    "{\n" +
                            "  \"BlockType\": {\n" +
                            "    \"Textures\": [\n" +
                            "      {\n" +
                            "        \"All\": \"BlockTextures/ExplicitTexture.png\"\n" +
                            "      }\n" +
                            "    ]\n" +
                            "  }\n" +
                            "}\n");
            writeSolidPng(assetsDir.resolve("Common").resolve("BlockTextures").resolve("ExplicitTexture.png"), 0xFFFF0000);

            HytaleTerrain.setHytaleAssetsDir(assetsDir.toFile());
            HytaleTerrain terrain = new HytaleTerrain("Test Metadata Texture", HytaleBlock.of("Test_Metadata_Texture"), 0x0000FF);

            BufferedImage icon = terrain.getIcon(null);

            assertTrue(hasStrongRedPixel(icon));
        } finally {
            HytaleTerrain.setHytaleAssetsDir(previousAssetsDir);
        }
    }

    @Test
    public void testParentBlockAssetMetadataProvidesFallbackIcon() throws Exception {
        Path assetsDir = Files.createTempDirectory("hytale-parent-icon-assets");
        File previousAssetsDir = HytaleTerrain.getHytaleAssetsDir();
        try {
            Files.createDirectories(assetsDir.resolve("Server").resolve("BlockTypeList"));
            Files.createDirectories(assetsDir.resolve("Server").resolve("Item").resolve("Items").resolve("Test"));
            Files.createDirectories(assetsDir.resolve("Common").resolve("Icons").resolve("ItemsGenerated"));

            writeJson(assetsDir.resolve("Server").resolve("Item").resolve("Items").resolve("Test").resolve("Parent_Block.json"),
                    "{\n" +
                            "  \"Icon\": \"Icons/ItemsGenerated/ParentIcon.png\"\n" +
                            "}\n");
            writeJson(assetsDir.resolve("Server").resolve("Item").resolve("Items").resolve("Test").resolve("Child_Block.json"),
                    "{\n" +
                            "  \"Parent\": \"Parent_Block\"\n" +
                            "}\n");
            writeSolidPng(assetsDir.resolve("Common").resolve("Icons").resolve("ItemsGenerated").resolve("ParentIcon.png"), 0xFF00FF00);

            HytaleTerrain.setHytaleAssetsDir(assetsDir.toFile());
            HytaleTerrain terrain = new HytaleTerrain("Child Block", HytaleBlock.of("Child_Block"), 0xFF0000);

            BufferedImage icon = terrain.getIcon(null);

            assertEquals(0xFF00FF00, icon.getRGB(0, 0));
        } finally {
            HytaleTerrain.setHytaleAssetsDir(previousAssetsDir);
        }
    }

    private void writeJson(Path path, String content) throws Exception {
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }

    private void writeSolidPng(Path path, int argb) throws Exception {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, argb);
            }
        }
        ImageIO.write(image, "png", path.toFile());
    }

    private boolean hasStrongRedPixel(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                int red = (argb >>> 16) & 0xFF;
                int green = (argb >>> 8) & 0xFF;
                int blue = argb & 0xFF;
                if ((alpha > 0) && (red > 180) && (green < 100) && (blue < 100)) {
                    return true;
                }
            }
        }
        return false;
    }
}
