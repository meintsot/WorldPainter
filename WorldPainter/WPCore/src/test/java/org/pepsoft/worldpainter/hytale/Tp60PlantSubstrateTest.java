package org.pepsoft.worldpainter.hytale;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.TileFactory;
import org.pepsoft.worldpainter.TileFactoryFactory;
import org.pepsoft.worldpainter.World2;
import org.pepsoft.worldpainter.exporting.WorldExportSettings;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.pepsoft.worldpainter.Constants.DIM_NORMAL;
import static org.pepsoft.worldpainter.DefaultPlugin.HYTALE;

/**
 * Regression test for TP-60: painting a surface-only HytaleTerrain (e.g. a Bush) on top of a
 * solid HytaleTerrain (e.g. Stone) must NOT replace the substrate. The bush is a decoration
 * placed at {@code height + 1}; the block at {@code height} stays whatever the user painted
 * as substrate.
 *
 * <p>The fix routes surface-only HytaleTerrain selections into a new {@link HytalePlantsLayer}
 * sibling of {@link HytaleTerrainLayer} so the substrate index is preserved. This test sets
 * the layers directly (bypassing the painter UI which lives in WPGUI) and exercises the
 * exporter end-to-end.
 */
public class Tp60PlantSubstrateTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Test
    public void plantPaintedOverStoneKeepsStoneSubstrateAndPlacesPlantOnTop() throws Exception {
        World2 world = new World2(HYTALE, 0, 320);
        world.setName("Tp60Substrate");
        world.setCreateGoodiesChest(false);

        long seed = 42L;
        TileFactory tileFactory = TileFactoryFactory.createFlatTileFactory(
                seed, Terrain.GRASS, 0, 320, 64, 62, false, false);
        Dimension.Anchor anchor = new Dimension.Anchor(DIM_NORMAL, Dimension.Role.DETAIL, false, 0);
        Dimension dim = new Dimension(world, "Surface", seed, tileFactory, anchor);
        dim.setEventsInhibited(true);

        Tile tile = tileFactory.createTile(0, 0);
        for (int x = 0; x < 128; x++) {
            for (int z = 0; z < 128; z++) {
                tile.setHeight(x, z, 64);
                tile.setTerrain(x, z, Terrain.GRASS);
                HytaleTerrainLayer.setTerrainIndex(tile, x, z, HytaleTerrain.STONE.getLayerIndex());
            }
        }
        // Paint a Bush plant on a single pixel; the substrate (Stone) at this pixel must
        // survive and the bush must end up at height+1.
        HytalePlantsLayer.setPlantIndex(tile, 5, 7, HytaleTerrain.BUSH.getLayerIndex());
        dim.addTile(tile);
        dim.setEventsInhibited(false);
        world.addDimension(dim);

        File exportBaseDir = tempDir.newFolder("tp60_export");
        new HytaleWorldExporter(world, new WorldExportSettings()).export(exportBaseDir, "Tp60Substrate", null, null);

        File chunksDir = new File(new File(new File(new File(exportBaseDir, "Tp60Substrate"), "universe"), "worlds"),
                "default/chunks");
        File[] regionFiles = chunksDir.listFiles((d, n) -> n.endsWith(".region.bin"));
        assertNotNull(regionFiles);
        assertTrue("export should produce at least one region file", regionFiles.length > 0);

        // Scan every chunk in every region for the Plant_Bush block; assert that wherever
        // a bush appears, the block directly below it is the painted substrate (Rock_Stone),
        // not a synthesised Soil_Grass. (Support-value verification lives in
        // PlantOverlayDecorativeTest because HytaleRegionFile's read path doesn't restore
        // BlockPhysics support data — it's a write-only diagnostic on disk readback.)
        int bushCount = 0;
        int wrongSubstrateCount = 0;
        String firstWrongSubstrate = null;
        for (File rfile : regionFiles) {
            HytaleRegionFile rf = new HytaleRegionFile(rfile.toPath());
            try {
                rf.open();
                for (int cx = 0; cx < HytaleChunk.CHUNK_SIZE; cx++) {
                    for (int cz = 0; cz < HytaleChunk.CHUNK_SIZE; cz++) {
                        if (! rf.hasChunk(cx, cz)) {
                            continue;
                        }
                        HytaleChunk chunk = rf.readChunk(cx, cz, 0, HytaleChunk.DEFAULT_MAX_HEIGHT);
                        if (chunk == null) {
                            continue;
                        }
                        for (int x = 0; x < HytaleChunk.CHUNK_SIZE; x++) {
                            for (int z = 0; z < HytaleChunk.CHUNK_SIZE; z++) {
                                for (int y = 1; y < HytaleChunk.DEFAULT_MAX_HEIGHT; y++) {
                                    HytaleBlock b = chunk.getHytaleBlock(x, y, z);
                                    if ((b == null) || b.isEmpty() || ! "Plant_Bush".equals(b.id)) {
                                        continue;
                                    }
                                    bushCount++;
                                    HytaleBlock below = chunk.getHytaleBlock(x, y - 1, z);
                                    if ((below == null) || ! "Rock_Stone".equals(below.id)) {
                                        wrongSubstrateCount++;
                                        if (firstWrongSubstrate == null) {
                                            firstWrongSubstrate = (below == null) ? "null" : below.id;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } finally {
                rf.close();
            }
        }

        assertTrue("Expected at least one Plant_Bush in the export (the painted overlay at tile pixel 5,7)",
                bushCount >= 1);
        assertEquals("All Plant_Bush blocks must sit on Rock_Stone substrate (first mismatch: "
                        + firstWrongSubstrate + ")",
                0, wrongSubstrateCount);
    }
}
