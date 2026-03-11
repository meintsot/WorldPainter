package org.pepsoft.worldpainter.layers.exporters;

import org.junit.Test;
import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.DefaultPlugin;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.hytale.HytaleBlockRegistry;
import org.pepsoft.worldpainter.layers.Frost;

import java.awt.*;

import static org.junit.Assert.assertEquals;
import static org.pepsoft.minecraft.Material.AIR;
import static org.pepsoft.minecraft.Material.GRASS_BLOCK;
import static org.pepsoft.worldpainter.TestData.createDimension;
import static org.pepsoft.worldpainter.TestData.createMinecraftWorld;

public class FrostExporterTest {
    @Test
    public void testHytaleFrostDoesNotStackExistingSnow() {
        HytaleBlockRegistry.ensureMaterialsRegistered();

        final Rectangle area = new Rectangle(0, 0, 1, 1);
        final Dimension dimension = createDimension(area, 62);
        dimension.setBitLayerValueAt(Frost.INSTANCE, 0, 0, true);

        final Material hytaleSnow = Material.get("hytale:Soil_Snow");
        try (var minecraftWorld = createMinecraftWorld(area, 62, GRASS_BLOCK)) {
            minecraftWorld.setMaterialAt(0, 0, 63, hytaleSnow);

            final FrostExporter exporter = new FrostExporter(dimension, DefaultPlugin.HYTALE, new FrostExporter.FrostSettings());
            exporter.addFeatures(area, area, minecraftWorld);

            assertEquals(hytaleSnow, minecraftWorld.getMaterialAt(0, 0, 63));
            assertEquals(AIR, minecraftWorld.getMaterialAt(0, 0, 64));
        }
    }

    @Test
    public void testHytaleFrostReplacesSurfaceBlock() {
        HytaleBlockRegistry.ensureMaterialsRegistered();

        final Rectangle area = new Rectangle(0, 0, 1, 1);
        final Dimension dimension = createDimension(area, 62);
        dimension.setBitLayerValueAt(Frost.INSTANCE, 0, 0, true);

        final Material hytaleSnow = Material.get("hytale:Soil_Snow");
        try (var minecraftWorld = createMinecraftWorld(area, 62, GRASS_BLOCK)) {
            final FrostExporter exporter = new FrostExporter(dimension, DefaultPlugin.HYTALE, new FrostExporter.FrostSettings());
            exporter.addFeatures(area, area, minecraftWorld);

            // Snow should replace the surface block, not be added on top
            assertEquals(hytaleSnow, minecraftWorld.getMaterialAt(0, 0, 62));
            assertEquals(AIR, minecraftWorld.getMaterialAt(0, 0, 63));
        }
    }
}
