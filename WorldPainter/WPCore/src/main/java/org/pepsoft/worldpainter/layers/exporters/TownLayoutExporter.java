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
        final int depth = Math.max(1, settings.getSurfaceDepth());
        for (int x = area.x; x < area.x + area.width; x++) {
            for (int y = area.y; y < area.y + area.height; y++) {
                if (dimension.getBitLayerValueAt(TownLayout.INSTANCE, x, y)) {
                    placeSurfaceColumn(minecraftWorld, x, y, dimension.getIntHeightAt(x, y), depth, block);
                }
            }
        }
        return null;
    }

    /**
     * Replace the terrain surface block at {@code (x, y)} with {@code block}, flush with the ground,
     * continuing downward for {@code depth} blocks total (clamped at the world floor). Unlike a marker
     * pillar, this overwrites solid terrain — the footprint sits in the surface, not above it.
     */
    static void placeSurfaceColumn(MinecraftWorld world, int x, int y, int terrainHeight, int depth, Material block) {
        final int minZ = world.getMinHeight();
        for (int d = 0; d < depth; d++) {
            final int z = terrainHeight - d;
            if (z < minZ) {
                break;
            }
            world.setMaterialAt(x, y, z, block);
        }
    }
}
