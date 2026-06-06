package org.pepsoft.worldpainter.layers.exporters;

import org.junit.Test;
import org.pepsoft.minecraft.Material;
import org.pepsoft.util.Box;
import org.pepsoft.worldpainter.layers.TownLayout;
import org.pepsoft.worldpainter.objects.MinecraftWorldObject;

import static org.junit.Assert.assertEquals;
import static org.pepsoft.minecraft.Constants.BLK_WOOL;
import static org.pepsoft.minecraft.Material.AIR;

public class TownLayoutExporterTest {
    @Test
    public void replacesSurfaceBlockFlush() {
        // Volume: x 0..16, y 0..16, vertical z 0..64. maxHeight must be a power of two.
        Box volume = new Box(0, 16, 0, 16, 0, 64);
        MinecraftWorldObject world = new MinecraftWorldObject("test", volume, 256, 32);
        Material blackWool = Material.get(BLK_WOOL, 15);

        // Surface height 10, depth 1 -> replace only the surface block at z = 10, flush.
        TownLayoutExporter.placeSurfaceColumn(world, 4, 4, 10, 1, blackWool);

        assertEquals(blackWool, world.getMaterialAt(4, 4, 10)); // surface replaced
        assertEquals(AIR, world.getMaterialAt(4, 4, 11));       // nothing added above the surface
    }

    @Test
    public void depthReplacesDownward() {
        Box volume = new Box(0, 16, 0, 16, 0, 64);
        MinecraftWorldObject world = new MinecraftWorldObject("test", volume, 256, 32);
        Material blackWool = Material.get(BLK_WOOL, 15);

        // Surface height 10, depth 3 -> replace z = 10, 9, 8.
        TownLayoutExporter.placeSurfaceColumn(world, 4, 4, 10, 3, blackWool);

        assertEquals(AIR, world.getMaterialAt(4, 4, 11));       // nothing above the surface
        assertEquals(blackWool, world.getMaterialAt(4, 4, 10));
        assertEquals(blackWool, world.getMaterialAt(4, 4, 9));
        assertEquals(blackWool, world.getMaterialAt(4, 4, 8));
        assertEquals(AIR, world.getMaterialAt(4, 4, 7));        // depth respected
    }

    @Test
    public void depthClampsAtWorldFloor() {
        Box volume = new Box(0, 16, 0, 16, 0, 64);
        MinecraftWorldObject world = new MinecraftWorldObject("test", volume, 256, 32);
        Material blackWool = Material.get(BLK_WOOL, 15);

        // Surface height 1 with depth 5 must not write below the world floor (z = 0) or throw.
        TownLayoutExporter.placeSurfaceColumn(world, 4, 4, 1, 5, blackWool);

        assertEquals(blackWool, world.getMaterialAt(4, 4, 1));
        assertEquals(blackWool, world.getMaterialAt(4, 4, 0)); // floor reached, no underflow
    }

    @Test
    public void layerResolvesExporterType() {
        assertEquals(TownLayoutExporter.class, TownLayout.INSTANCE.getExporterType());
    }
}
