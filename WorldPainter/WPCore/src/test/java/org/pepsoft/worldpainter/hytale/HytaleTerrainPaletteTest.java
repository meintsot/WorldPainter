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
}
