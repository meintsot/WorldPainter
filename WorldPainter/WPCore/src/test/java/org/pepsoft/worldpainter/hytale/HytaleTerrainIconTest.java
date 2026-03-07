package org.pepsoft.worldpainter.hytale;

import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class HytaleTerrainIconTest {

    @Test
    public void testGeneratedItemIconsAreUsedWhenAvailable() throws Exception {
        java.io.File previousAssetsDir = HytaleTerrain.getHytaleAssetsDir();
        Path tempDir = Files.createTempDirectory("hytale-icons-test");
        Path iconDir = tempDir.resolve("Common").resolve("Icons").resolve("ItemsGenerated");
        Files.createDirectories(iconDir);

        BufferedImage dirtIcon = createSolidIcon(0xFF12AB34);
        BufferedImage aspenLeavesIcon = createSolidIcon(0xFF55AA11);
        BufferedImage crystalSaplingIcon = createSolidIcon(0xFF2299CC);
        BufferedImage marshReedsIcon = createSolidIcon(0xFF6BAA44);
        BufferedImage wetReedsIcon = createSolidIcon(0xFF7A5A4A);
        BufferedImage greenSeaweedIcon = createSolidIcon(0xFF2F8F55);
        BufferedImage seaweedStackIcon = createSolidIcon(0xFF1B5A33);
        BufferedImage aridPalmLeavesIcon = createSolidIcon(0xFF8C7A45);
        BufferedImage tropicalLeavesIcon = createSolidIcon(0xFF2A7D39);
        BufferedImage winterBerryIcon = createSolidIcon(0xFF9A2D4E);
        BufferedImage glowcapOrangeIcon = createSolidIcon(0xFFE07A22);
        BufferedImage aridGrassIcon = createSolidIcon(0xFFB28A43);
        BufferedImage caveBlueMossIcon = createSolidIcon(0xFF3C71A8);
        BufferedImage seaweedBulbsIcon = createSolidIcon(0xFF4FAE67);
        ImageIO.write(dirtIcon, "png", iconDir.resolve("Soil_Dirt.png").toFile());
        ImageIO.write(aspenLeavesIcon, "png", iconDir.resolve("Plant_Aspen_Leaves.png").toFile());
        ImageIO.write(crystalSaplingIcon, "png", iconDir.resolve("Plant_Crystal_Sapling.png").toFile());
        ImageIO.write(marshReedsIcon, "png", iconDir.resolve("Plant_Reeds.png").toFile());
        ImageIO.write(wetReedsIcon, "png", iconDir.resolve("Plants_Reeds_Wet.png").toFile());
        ImageIO.write(greenSeaweedIcon, "png", iconDir.resolve("Plant_Seaweed_Glow_Green.png").toFile());
        ImageIO.write(seaweedStackIcon, "png", iconDir.resolve("Plant_Seaweed_Stack.png").toFile());
        ImageIO.write(aridPalmLeavesIcon, "png", iconDir.resolve("Plant_Bush_Arid_Palm.png").toFile());
        ImageIO.write(tropicalLeavesIcon, "png", iconDir.resolve("Plant_Tropical_Leaves.png").toFile());
        ImageIO.write(winterBerryIcon, "png", iconDir.resolve("Plant_Bush_Winter_Berry.png").toFile());
        ImageIO.write(glowcapOrangeIcon, "png", iconDir.resolve("Plant_Crop_Mushroom_Glowcap_Orange.png").toFile());
        ImageIO.write(aridGrassIcon, "png", iconDir.resolve("Plant_Grass_Dry.png").toFile());
        ImageIO.write(caveBlueMossIcon, "png", iconDir.resolve("Plant_Moss_Blue_Cave.png").toFile());
        ImageIO.write(seaweedBulbsIcon, "png", iconDir.resolve("Plant_Seaweed_Glow_Green_Bulbs.png").toFile());

        try {
            HytaleTerrain.setHytaleAssetsDir(tempDir.toFile());
            assertIconColour(new HytaleTerrain("Test Dirt", HytaleBlock.of("Soil_Dirt"), null), 0xFF12AB34);
            assertIconColour(new HytaleTerrain("Aspen Leaves", HytaleBlock.of("Plant_Leaves_Aspen"), null), 0xFF55AA11);
            assertIconColour(new HytaleTerrain("Crystal Sapling", HytaleBlock.of("Plant_Sapling_Crystal"), null), 0xFF2299CC);
            assertIconColour(new HytaleTerrain("River Reeds", HytaleBlock.of("Plant_Reeds_Marsh"), null), 0xFF6BAA44);
            assertIconColour(new HytaleTerrain("Wet Reeds", HytaleBlock.of("Plant_Reeds_Wet"), null), 0xFF7A5A4A);
            assertIconColour(new HytaleTerrain("Green Seaweed", HytaleBlock.of("Plant_Seaweed_Grass_Green"), null), 0xFF2F8F55);
            assertIconColour(new HytaleTerrain("Seaweed Middle", HytaleBlock.of("Plant_Seaweed_Grass_Stack"), null), 0xFF1B5A33);
            assertIconColour(new HytaleTerrain("Arid Palm Leaves", HytaleBlock.of("Plant_Leaves_Palm_Arid"), null), 0xFF8C7A45);
            assertIconColour(new HytaleTerrain("Tropical Leaves", HytaleBlock.of("Plant_Leaves_Jungle"), null), 0xFF2A7D39);
            assertIconColour(new HytaleTerrain("Winter Berry Bush", HytaleBlock.of("Plant_Crop_Berry_Winter_Block"), null), 0xFF9A2D4E);
            assertIconColour(new HytaleTerrain("Orange Glowing Mushroom", HytaleBlock.of("Plant_Crop_Mushroom_Glowing_Orange"), null), 0xFFE07A22);
            assertIconColour(new HytaleTerrain("Arid Grass", HytaleBlock.of("Plant_Grass_Arid"), null), 0xFFB28A43);
            assertIconColour(new HytaleTerrain("Blue Hanging Moss", HytaleBlock.of("Plant_Moss_Cave_Blue"), null), 0xFF3C71A8);
            assertIconColour(new HytaleTerrain("Green Seaweed Bulbs", HytaleBlock.of("Plant_Seaweed_Grass_Bulbs"), null), 0xFF4FAE67);
        } finally {
            HytaleTerrain.setHytaleAssetsDir(previousAssetsDir);
        }
    }

    private void assertIconColour(HytaleTerrain terrain, int expectedArgb) {
        BufferedImage icon = terrain.getIcon(null);
        assertNotNull(icon);
        assertEquals(8, icon.getWidth());
        assertEquals(8, icon.getHeight());
        assertEquals(expectedArgb, icon.getRGB(4, 4));
    }

    private BufferedImage createSolidIcon(int argb) {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, argb);
            }
        }
        return image;
    }
}