package org.pepsoft.worldpainter.hytale;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class HytaleTerrainPaletteTest {

    @Test
    public void currentPaletteIsAlignedWithLayerIndices() {
        List<String> palette = HytaleTerrain.currentTerrainPaletteBlockIds();
        assertFalse("palette must not be empty", palette.isEmpty());
        // Element (i-1) of the palette must be the block id resolved by layer index i.
        for (int i = 1; i <= palette.size(); i++) {
            HytaleTerrain t = HytaleTerrain.getByLayerIndex(i);
            assertNotNull("no terrain for index " + i, t);
            assertEquals("palette/index mismatch at " + i,
                    (t.getBlock() != null) ? t.getBlock().id : null,
                    palette.get(i - 1));
        }
    }

    @Test
    public void remapByBlockIdHandlesShiftAndRemoval() {
        // Simulated "old" palette: index 1 -> Rock_Stone, index 2 -> Soil_Clay,
        // index 3 -> a block id that no longer exists.
        java.util.Map<Integer, String> oldPalette = new java.util.HashMap<>();
        oldPalette.put(1, "Rock_Stone");
        oldPalette.put(2, "Soil_Clay");
        oldPalette.put(3, "Block_That_Was_Deleted");

        int stoneNow = HytaleTerrain.getByBlockId("Rock_Stone").getLayerIndex();
        int clayNow  = HytaleTerrain.getByBlockId("Soil_Clay").getLayerIndex();

        assertEquals(stoneNow, HytaleTerrainPalette.remapIndex(1, oldPalette));
        assertEquals(clayNow,  HytaleTerrainPalette.remapIndex(2, oldPalette));
        assertEquals("unknown block id clears the pixel",
                0, HytaleTerrainPalette.remapIndex(3, oldPalette));
        assertEquals("index 0 stays 0",
                0, HytaleTerrainPalette.remapIndex(0, oldPalette));
        assertEquals("index absent from palette is left unchanged",
                7, HytaleTerrainPalette.remapIndex(7, oldPalette));
    }

    @Test
    public void remapTilesRewritesStoredClayIndexToCurrentClayIndex() {
        org.pepsoft.worldpainter.Tile tile =
                new org.pepsoft.worldpainter.Tile(0, 0, 0, 320); // x, y, minHeight, maxHeight
        // Pretend this world was saved when "Soil_Clay" lived at stored index 5.
        HytaleTerrainLayer.setTerrainIndex(tile, 10, 10, 5);
        java.util.Map<Integer, String> oldPalette = new java.util.HashMap<>();
        oldPalette.put(5, "Soil_Clay");

        int rewritten = HytaleTerrainPalette.remapTiles(
                java.util.Collections.singletonList(tile), oldPalette);

        assertEquals(1, rewritten);
        int clayNow = HytaleTerrain.getByBlockId("Soil_Clay").getLayerIndex();
        assertEquals(clayNow, HytaleTerrainLayer.getTerrainIndex(tile, 10, 10));
    }
}
