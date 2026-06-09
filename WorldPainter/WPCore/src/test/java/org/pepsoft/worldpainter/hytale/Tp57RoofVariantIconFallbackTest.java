package org.pepsoft.worldpainter.hytale;

import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * TP-57: roof sub-variants ending in {@code _Hollow}, {@code _Shallow} or
 * {@code _Steep} that have no icon of their own must fall back to the base
 * {@code ..._Roof} icon, like the pre-existing {@code _Flap}/{@code _Flat}/
 * {@code _Vertical} variants do.  Update 4 registered
 * {@code Rock_Slate_Brick_Roof_Hollow}, for which the game assets ship no
 * dedicated icon.
 */
public class Tp57RoofVariantIconFallbackTest {

    @Test
    public void testRoofSubVariantsFallBackToBaseRoofIcon() throws Exception {
        java.io.File previousAssetsDir = HytaleTerrain.getHytaleAssetsDir();
        Path tempDir = Files.createTempDirectory("hytale-roof-icons-test");
        Path iconDir = tempDir.resolve("Common").resolve("Icons").resolve("ItemsGenerated");
        Files.createDirectories(iconDir);

        BufferedImage slateRoofIcon = createSolidIcon(0xFF4A4A4A);
        BufferedImage metalRoofIcon = createSolidIcon(0xFFCD7F32);
        ImageIO.write(slateRoofIcon, "png", iconDir.resolve("Rock_Slate_Brick_Roof.png").toFile());
        ImageIO.write(metalRoofIcon, "png", iconDir.resolve("Metal_Bronze_Roof.png").toFile());

        try {
            HytaleTerrain.setHytaleAssetsDir(tempDir.toFile());
            // The real-world case: no Rock_Slate_Brick_Roof_Hollow.png exists in the game assets
            assertIconColour(new HytaleTerrain("Slate Brick Roof (Hollow)",
                    HytaleBlock.of("Rock_Slate_Brick_Roof_Hollow"), null), 0xFF4A4A4A);
            // Same fallback must hold for the _Shallow and _Steep sub-variants
            assertIconColour(new HytaleTerrain("Bronze Roof (Shallow)",
                    HytaleBlock.of("Metal_Bronze_Roof_Shallow"), null), 0xFFCD7F32);
            assertIconColour(new HytaleTerrain("Bronze Roof (Steep)",
                    HytaleBlock.of("Metal_Bronze_Roof_Steep"), null), 0xFFCD7F32);
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
