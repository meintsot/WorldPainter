package org.pepsoft.worldpainter.hytale;

import org.junit.Test;
import org.pepsoft.worldpainter.DefaultPlugin;
import org.pepsoft.worldpainter.Terrain;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HytaleTerrainHelperTest {

    @Test
    public void testGetPickListReturnsAllHytaleTerrains() {
        final HytaleTerrain[] allTerrains = HytaleTerrainHelper.getAllHytaleTerrains();

        assertTrue(allTerrains.length >= HytaleTerrain.PICK_LIST.length);
        assertEquals(HytaleTerrain.getAllTerrains().size(), allTerrains.length);
        assertArrayEquals(allTerrains, HytaleTerrainHelper.getPickList(DefaultPlugin.HYTALE));
    }

    @Test
    public void testGetPickListPreservesMinecraftTerrainChoices() {
        assertArrayEquals(Terrain.PICK_LIST, HytaleTerrainHelper.getPickList(DefaultPlugin.JAVA_ANVIL));
    }
}
