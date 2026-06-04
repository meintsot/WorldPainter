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
    public void placesPillarFromSurfaceUp() {
        // Volume: x 0..16, y 0..16, vertical z 0..64. maxHeight must be a power of two.
        Box volume = new Box(0, 16, 0, 16, 0, 64);
        MinecraftWorldObject world = new MinecraftWorldObject("test", volume, 256, 32);
        Material blackWool = Material.get(BLK_WOOL, 15);

        // Surface height 10, marker height 3 -> place at z = 11, 12, 13.
        TownLayoutExporter.placeMarkerColumn(world, 4, 4, 10, 3, blackWool);

        assertEquals(AIR, world.getMaterialAt(4, 4, 10));   // surface untouched
        assertEquals(blackWool, world.getMaterialAt(4, 4, 11));
        assertEquals(blackWool, world.getMaterialAt(4, 4, 12));
        assertEquals(blackWool, world.getMaterialAt(4, 4, 13));
        assertEquals(AIR, world.getMaterialAt(4, 4, 14));   // above the pillar
    }

    @Test
    public void stopsAtSolidBlock() {
        Box volume = new Box(0, 16, 0, 16, 0, 64);
        MinecraftWorldObject world = new MinecraftWorldObject("test", volume, 256, 32);
        Material blackWool = Material.get(BLK_WOOL, 15);
        Material stone = Material.get(1); // minecraft:stone, solid

        world.setMaterialAt(4, 4, 12, stone); // obstruction within the pillar
        TownLayoutExporter.placeMarkerColumn(world, 4, 4, 10, 3, blackWool);

        assertEquals(blackWool, world.getMaterialAt(4, 4, 11)); // placed below obstruction
        assertEquals(stone, world.getMaterialAt(4, 4, 12));     // obstruction preserved
        assertEquals(AIR, world.getMaterialAt(4, 4, 13));       // stopped, not placed
    }

    @Test
    public void layerResolvesExporterType() {
        assertEquals(TownLayoutExporter.class, TownLayout.INSTANCE.getExporterType());
    }
}
