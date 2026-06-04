package org.pepsoft.worldpainter.layers.exporters;

import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Platform;
import org.pepsoft.worldpainter.exporting.AbstractLayerExporter;
import org.pepsoft.worldpainter.exporting.Fixup;
import org.pepsoft.worldpainter.exporting.MinecraftWorld;
import org.pepsoft.worldpainter.exporting.SecondPassLayerExporter;
import org.pepsoft.worldpainter.layers.TownLayout;

import java.awt.Rectangle;
import java.util.List;
import java.util.Set;

import static java.util.Collections.singleton;
import static org.pepsoft.worldpainter.exporting.SecondPassLayerExporter.Stage.ADD_FEATURES;

public class TownLayoutExporter extends AbstractLayerExporter<TownLayout> implements SecondPassLayerExporter {
    public TownLayoutExporter(Dimension dimension, Platform platform, ExporterSettings settings) {
        super(dimension, platform, (settings != null) ? settings : new TownLayoutSettings(), TownLayout.INSTANCE);
    }

    @Override
    public Set<Stage> getStages() {
        return singleton(ADD_FEATURES);
    }

    @Override
    public List<Fixup> addFeatures(Rectangle area, Rectangle exportedArea, MinecraftWorld minecraftWorld) {
        final TownLayoutSettings settings = (TownLayoutSettings) super.settings;
        if (! settings.isExport()) {
            return null;
        }
        final Material block = settings.getBlock();
        final int markerHeight = Math.max(1, settings.getMarkerHeight());
        for (int x = area.x; x < area.x + area.width; x++) {
            for (int y = area.y; y < area.y + area.height; y++) {
                if (dimension.getBitLayerValueAt(TownLayout.INSTANCE, x, y)) {
                    placeMarkerColumn(minecraftWorld, x, y, dimension.getIntHeightAt(x, y), markerHeight, block);
                }
            }
        }
        return null;
    }

    /**
     * Place a marker pillar of {@code block} starting one block above {@code terrainHeight}, up to
     * {@code markerHeight} blocks tall. Only fills insubstantial space; stops at the first solid block
     * and never exceeds the world's maximum height.
     */
    static void placeMarkerColumn(MinecraftWorld world, int x, int y, int terrainHeight, int markerHeight, Material block) {
        final int maxZ = world.getMaxHeight() - 1;
        for (int dz = 1; dz <= markerHeight; dz++) {
            final int z = terrainHeight + dz;
            if (z > maxZ) {
                break;
            }
            final Material existing = world.getMaterialAt(x, y, z);
            if (existing.veryInsubstantial || (existing == Material.ICE)) {
                world.setMaterialAt(x, y, z, block);
            } else {
                break;
            }
        }
    }
}
