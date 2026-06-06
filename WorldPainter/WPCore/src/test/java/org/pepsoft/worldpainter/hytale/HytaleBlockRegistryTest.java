package org.pepsoft.worldpainter.hytale;

import org.junit.Test;
import org.pepsoft.minecraft.Material;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
    public void testRockStoneMossyConstructionVariantsUseCorrectHytaleNames() {
        List<String> rockConstruction =
                HytaleBlockRegistry.getBlockNames(HytaleBlockRegistry.Category.ROCK_CONSTRUCTION);

        // Hytale's mossy construction blocks put "_Mossy" last
        // (Rock_Stone_Cobble_Mossy / Rock_Stone_Brick_Mossy).
        assertTrue("ROCK_CONSTRUCTION should contain Rock_Stone_Cobble_Mossy",
                rockConstruction.contains("Rock_Stone_Cobble_Mossy"));
        assertTrue("ROCK_CONSTRUCTION should contain Rock_Stone_Brick_Mossy",
                rockConstruction.contains("Rock_Stone_Brick_Mossy"));

        // TP-65: generating the variant sub-tree from the "Rock_Stone_Mossy" base
        // produced wrong-order names that don't exist in Hytale and export as the
        // magenta error block. None of those may be offered.
        for (String id : rockConstruction) {
            assertFalse("Wrong-order block leaked into the picker (TP-65): " + id,
                    id.startsWith("Rock_Stone_Mossy_"));
        }

        // The corrected blocks must still resolve to the ROCK_CONSTRUCTION category.
        assertEquals(HytaleBlockRegistry.Category.ROCK_CONSTRUCTION,
                HytaleBlockRegistry.getCategoryForBlock("Rock_Stone_Cobble_Mossy"));
        assertEquals(HytaleBlockRegistry.Category.ROCK_CONSTRUCTION,
                HytaleBlockRegistry.getCategoryForBlock("Rock_Stone_Brick_Mossy"));
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

    @Test
    public void testWaterFlowersAreFloatingWaterPlants() {
        assertTrue(HytaleBlockRegistry.isFloatingWaterPlant("Plant_Flower_Water_Green"));
        assertTrue(HytaleBlockRegistry.isFloatingWaterPlant("Plant_Flower_Water_Duckweed"));
        assertTrue(HytaleBlockRegistry.isFloatingWaterPlant("Plant_Flower_Water_Blue"));

        assertFalse(HytaleBlockRegistry.isFloatingWaterPlant("Plant_Bush"));
        assertFalse(HytaleBlockRegistry.isFloatingWaterPlant("Plant_Flower_Common_Blue"));
        assertFalse(HytaleBlockRegistry.isFloatingWaterPlant(null));
    }

    @Test
    public void testFloatingWaterPlantOnFloodedColumnGoesToSurface() {
        // Flooded: waterLevel (64) above terrain (50) -> rest on the air cell above the
        // topmost fluid block (65), not the floor (51).
        assertEquals(65, HytaleBlockRegistry.surfacePlantY("Plant_Flower_Water_Green", 50, 64));
    }

    @Test
    public void testFloatingWaterPlantOnDryColumnStaysOnSurface() {
        // No water above terrain -> fall back to terrain+1, like any other plant.
        assertEquals(51, HytaleBlockRegistry.surfacePlantY("Plant_Flower_Water_Green", 50, 40));
    }

    @Test
    public void testNonFloatingPlantIgnoresWaterLevel() {
        // Ordinary plants always sit on the terrain surface even when flooded.
        assertEquals(51, HytaleBlockRegistry.surfacePlantY("Plant_Bush", 50, 64));
    }
}
