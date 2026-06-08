package org.pepsoft.worldpainter.hytale.export;

import org.junit.Test;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.TestData;
import org.pepsoft.worldpainter.hytale.HytalePrefabPlacement;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.pepsoft.worldpainter.Constants.TILE_SIZE;

public class ExactPlacementExportTest {

    @Test
    public void enqueuesPlacementInsideRegionBoundsWithRotation() {
        Dimension dim = TestData.createDimension(new java.awt.Rectangle(0, 0, TILE_SIZE, TILE_SIZE), 64);
        dim.addHytalePrefabPlacement(
                new HytalePrefabPlacement(1L, "Prefabs/x.prefab.json", "X", 10, 20, null, true, 90.0));
        dim.addHytalePrefabPlacement(
                new HytalePrefabPlacement(2L, "Prefabs/y.prefab.json", "Y", 99999, 99999, 70, false, 0.0));

        List<HytaleWorldExporter.PendingPrefabPaste> out = new ArrayList<>();
        HytaleWorldExporter.enqueueExactPlacements(dim, 0, 0, 128, 128, out);

        assertEquals(1, out.size());
        HytaleWorldExporter.PendingPrefabPaste p = out.get(0);
        assertEquals(10, p.worldX);
        assertEquals(20, p.worldZ);
        assertEquals(90.0, p.rotationDegrees, 1.0e-9);
        assertEquals(65, p.anchorY);  // snapToSurface -> terrain height (64) + 1
        assertEquals("Prefabs/x.prefab.json", p.prefabPath);
    }
}
