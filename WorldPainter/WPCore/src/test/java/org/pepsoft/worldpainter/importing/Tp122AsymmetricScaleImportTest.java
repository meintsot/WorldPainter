package org.pepsoft.worldpainter.importing;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.pepsoft.worldpainter.Configuration;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.TileFactory;
import org.pepsoft.worldpainter.TileFactoryFactory;
import org.pepsoft.worldpainter.World2;
import org.pepsoft.worldpainter.heightMaps.BitmapHeightMap;
import org.pepsoft.worldpainter.heightMaps.TransformingHeightMap;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.pepsoft.worldpainter.Constants.DIM_NORMAL;
import static org.pepsoft.worldpainter.DefaultPlugin.HYTALE;

/**
 * TP-122: heightmap import must support independent X and Y scale factors. The dialog historically coupled both axes
 * to a single scale spinner; these tests pin the engine behaviour ({@link TransformingHeightMap} +
 * {@link HeightMapImporter}) that the new per-axis UI relies on.
 *
 * <p>The test image is an 8x8 grayscale gradient where pixel (x, y) has value {@code y * 8 + x}, so every pixel is
 * uniquely identifiable in the imported heights.
 */
public class Tp122AsymmetricScaleImportTest {

    private static final float SCALE_X = 16.0f, SCALE_Y = 8.0f;

    private Configuration previousConfiguration;

    @Before
    public void setUp() {
        previousConfiguration = Configuration.getInstance();
        Configuration.setInstance(new Configuration());
    }

    @After
    public void tearDown() {
        Configuration.setInstance(previousConfiguration);
    }

    @Test
    public void transformingHeightMapAppliesIndependentScales() {
        final TransformingHeightMap scaled = buildScaledHeightMap();

        final Rectangle extent = scaled.getExtent();
        assertEquals("Width must be scaled by the X factor", Math.round(8 * SCALE_X), extent.width);
        assertEquals("Height must be scaled by the Y factor", Math.round(8 * SCALE_Y), extent.height);

        // (33, 17) and (47, 23) both map to source pixel (2, 2)
        assertEquals("Coordinates in the same scaled cell must sample the same source pixel",
                scaled.getHeight(33, 17), scaled.getHeight(47, 23), 0.0);
        // (17, 33) maps to source pixel (1, 4) (value 33), not (2, 2) (value 18) — X and Y scales must not be swapped
        assertNotEquals("Different scaled cells must sample different source pixels",
                scaled.getHeight(33, 17), scaled.getHeight(17, 33), 0.0);
        assertEquals(18.0, scaled.getHeight(33, 17), 0.0);
        assertEquals(33.0, scaled.getHeight(17, 33), 0.0);
    }

    @Test
    public void importToDimensionHonoursIndependentXYScales() throws Exception {
        final TransformingHeightMap scaled = buildScaledHeightMap();

        final World2 world = new World2(HYTALE, 0, 320);
        final long seed = 42L;
        final TileFactory tileFactory = TileFactoryFactory.createFlatTileFactory(seed, Terrain.GRASS, 0, 320, 0, 0, false, false);
        final Dimension.Anchor anchor = new Dimension.Anchor(DIM_NORMAL, Dimension.Role.DETAIL, false, 0);
        final Dimension dim = new Dimension(world, "Surface", seed, tileFactory, anchor);
        dim.setEventsInhibited(true);

        final HeightMapImporter importer = new HeightMapImporter();
        importer.setPlatform(HYTALE);
        importer.setHeightMap(scaled);
        importer.setName("tp122");
        importer.setTileFactory(tileFactory);
        importer.setMinHeight(0);
        importer.setMaxHeight(320);
        importer.setImageLowLevel(0);
        importer.setImageHighLevel(255);
        importer.setWorldLowLevel(0);
        importer.setWorldWaterLevel(0);
        importer.setWorldHighLevel(255);
        importer.importToDimension(dim, true, null);

        // One source pixel covers SCALE_X x SCALE_Y blocks
        assertEquals("Heights must be constant within one scaled cell along X",
                dim.getIntHeightAt(0, 0), dim.getIntHeightAt(15, 0));
        assertNotEquals("Height must change at the next scaled cell along X (16 blocks per pixel)",
                dim.getIntHeightAt(0, 0), dim.getIntHeightAt(16, 0));
        assertEquals("Heights must be constant within one scaled cell along Y",
                dim.getIntHeightAt(0, 0), dim.getIntHeightAt(0, 7));
        assertNotEquals("Height must change at the next scaled cell along Y (8 blocks per pixel)",
                dim.getIntHeightAt(0, 0), dim.getIntHeightAt(0, 8));

        // Absolute values: image 0..255 mapped 1:1 to world 0..255, pixel value = y * 8 + x
        assertEquals(18, dim.getIntHeightAt(33, 17));
        assertEquals(33, dim.getIntHeightAt(17, 33));
    }

    private static TransformingHeightMap buildScaledHeightMap() {
        final BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_BYTE_GRAY);
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                image.getRaster().setSample(x, y, 0, y * 8 + x);
            }
        }
        final BitmapHeightMap base = BitmapHeightMap.build().withName("tp122").withImage(image).now();
        return new TransformingHeightMap("tp122 transformed", base, SCALE_X, SCALE_Y, 0, 0, 0.0f);
    }
}
