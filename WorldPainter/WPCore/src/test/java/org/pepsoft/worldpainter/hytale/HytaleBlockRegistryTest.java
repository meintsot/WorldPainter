package org.pepsoft.worldpainter.hytale;

import org.junit.Test;
import org.pepsoft.minecraft.Material;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HytaleBlockRegistryTest {

    @Test
    public void testInitializeLoadsBlockTypesFromAssets() throws Exception {
        Path tempDir = Files.createTempDirectory("hytale-assets-test");
        Path blockTypeListDir = tempDir.resolve("Server").resolve("BlockTypeList");
        Files.createDirectories(blockTypeListDir);
        Files.write(blockTypeListDir.resolve("Custom.json"),
                ("{\n" +
                        "  \"Blocks\": [\n" +
                        "    \"Wood_Test_Trunk\",\n" +
                        "    \"Rock_Test_Brick\",\n" +
                        "    \"Plant_Flower_Test_Blue\"\n" +
                        "  ]\n" +
                        "}\n").getBytes(StandardCharsets.UTF_8));

        HytaleBlockRegistry.initialize(tempDir);

        assertTrue(HytaleBlockRegistry.getAllBlockNames().contains("Wood_Test_Trunk"));
        assertTrue(HytaleBlockRegistry.getAllBlockNames().contains("Rock_Test_Brick"));
        assertTrue(HytaleBlockRegistry.getAllBlockNames().contains("Plant_Flower_Test_Blue"));
        assertEquals(HytaleBlockRegistry.Category.WOOD_NATURAL, HytaleBlockRegistry.getCategoryForBlock("Wood_Test_Trunk"));
        assertEquals(HytaleBlockRegistry.Category.ROCK_CONSTRUCTION, HytaleBlockRegistry.getCategoryForBlock("Rock_Test_Brick"));
        assertEquals(HytaleBlockRegistry.Category.FLOWERS, HytaleBlockRegistry.getCategoryForBlock("Plant_Flower_Test_Blue"));

        HytaleBlockRegistry.ensureMaterialsRegistered();
        assertEquals("hytale:Wood_Test_Trunk", Material.get("hytale:Wood_Test_Trunk").name);
    }

    @Test
    public void testMossBlockVariantsAreNotSurfaceOnly() {
        assertEquals(HytaleBlockRegistry.Category.MOSS_BLOCKS,
            HytaleBlockRegistry.getCategoryForBlock("Plant_Moss_Block_Green"));
        assertFalse(HytaleBlockRegistry.isSurfaceOnlyBlock("Plant_Moss_Block_Green"));

        assertEquals(HytaleBlockRegistry.Category.MOSS_VINES,
            HytaleBlockRegistry.getCategoryForBlock("Plant_Moss_Green"));
        assertTrue(HytaleBlockRegistry.isSurfaceOnlyBlock("Plant_Moss_Green"));
    }
}
