package org.pepsoft.worldpainter.layers.exporters;

import org.junit.Test;
import org.pepsoft.minecraft.Material;

import static org.junit.Assert.*;
import static org.pepsoft.minecraft.Constants.BLK_WOOL;

public class TownLayoutSettingsTest {
    @Test
    public void equalsAndHashCodeReflectFields() {
        TownLayoutSettings a = new TownLayoutSettings();
        TownLayoutSettings b = new TownLayoutSettings();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        b.setSurfaceDepth(7);
        assertNotEquals(a, b);
        b.setSurfaceDepth(a.getSurfaceDepth());
        assertEquals(a, b);

        b.setExport(! a.isExport());
        assertNotEquals(a, b);
        b.setExport(a.isExport());

        b.setBlock(Material.get(BLK_WOOL, 0)); // white wool, differs from the stone default
        assertNotEquals(a, b);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setBlockRejectsNull() {
        new TownLayoutSettings().setBlock(null);
    }

    @Test
    public void defaultBlockIsStone() {
        assertEquals(Material.STONE, new TownLayoutSettings().getBlock());
    }

    @Test
    public void defaultSurfaceDepthIsOne() {
        assertEquals(1, new TownLayoutSettings().getSurfaceDepth());
    }
}
