package org.pepsoft.worldpainter.hytale;

import org.junit.Test;
import org.pepsoft.worldpainter.DefaultPlugin;
import org.pepsoft.worldpainter.Terrain;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HytaleTerrainHelperTest {

    @Test
    public void testGetPickListReturnsFilteredHytaleTerrains() {
        final HytaleTerrain[] uiTerrains = HytaleTerrainHelper.getAllHytaleTerrains();

        // UI list should be a subset of the full pick list (blocks without icons are excluded)
        assertTrue(uiTerrains.length <= HytaleTerrain.PICK_LIST.length);
        assertTrue(uiTerrains.length > 0);
        assertArrayEquals(uiTerrains, HytaleTerrainHelper.getPickList(DefaultPlugin.HYTALE));
    }

    @Test
    public void testGetPickListPreservesMinecraftTerrainChoices() {
        assertArrayEquals(Terrain.PICK_LIST, HytaleTerrainHelper.getPickList(DefaultPlugin.JAVA_ANVIL));
    }
}
