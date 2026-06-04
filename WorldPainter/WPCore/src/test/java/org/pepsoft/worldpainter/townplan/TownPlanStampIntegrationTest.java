package org.pepsoft.worldpainter.townplan;

import org.junit.Test;
import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.TestData;
import org.pepsoft.worldpainter.exporting.MinecraftWorld;
import org.pepsoft.worldpainter.layers.TownLayout;
import org.pepsoft.worldpainter.layers.exporters.TownLayoutExporter;
import org.pepsoft.worldpainter.layers.exporters.TownLayoutSettings;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import static org.junit.Assert.*;
import static org.pepsoft.minecraft.Constants.BLK_WOOL;
import static org.pepsoft.minecraft.Material.AIR;

public class TownPlanStampIntegrationTest {
    @Test
    public void stampWritesBitLayerThenExporterPlacesMarker() {
        final int terrainHeight = 64;
        final Rectangle area = new Rectangle(0, 0, 128, 128);
        final Dimension dimension = TestData.createDimension(area, terrainHeight);

        // 1x1 opaque-black image at origin, unit scale -> stamps exactly world column (0,0).
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, 0xFF000000);

        // stamp() must use the BIT-layer write API; the int-value API would throw on a BIT layer.
        int stamped = TownPlanStamper.stamp(dimension, img, 0, 0, 1.0, 0.0, null, 128, false, area);
        assertEquals(1, stamped);
        assertTrue(dimension.getBitLayerValueAt(TownLayout.INSTANCE, 0, 0));
        assertFalse(dimension.getBitLayerValueAt(TownLayout.INSTANCE, 5, 5));

        // Export: the exporter must READ the BIT layer (would throw with the int-value API) and
        // place the marker pillar at the surface for the stamped column.
        final Material blackWool = Material.get(BLK_WOOL, 15);
        TownLayoutSettings settings = new TownLayoutSettings();
        settings.setBlock(blackWool);
        settings.setMarkerHeight(3);
        MinecraftWorld world = TestData.createMinecraftWorld(area, terrainHeight, Material.STONE);

        TownLayoutExporter exporter = new TownLayoutExporter(dimension, TestData.PLATFORM, settings);
        exporter.addFeatures(area, area, world);

        assertEquals(blackWool, world.getMaterialAt(0, 0, terrainHeight + 1));
        assertEquals(blackWool, world.getMaterialAt(0, 0, terrainHeight + 3));
        assertEquals(AIR, world.getMaterialAt(0, 0, terrainHeight + 4));     // pillar height respected
        assertEquals(AIR, world.getMaterialAt(5, 5, terrainHeight + 1));     // unstamped column untouched
    }
}
