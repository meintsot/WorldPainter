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

import static org.junit.Assert.*;
import static org.pepsoft.worldpainter.Constants.DIM_NORMAL;
import static org.pepsoft.worldpainter.DefaultPlugin.HYTALE;

/**
 * Regression test for TP-53 follow-up: a plant painted on a flooded column
 * via {@link HytalePlantsLayer} must survive the post-export seal pass.
 *
 * <p>Prior to the fix, the plant-export inner loop in
 * {@link HytaleWorldExporter} called {@code chunk.setHytaleBlock} with no
 * unconditional {@code setSealProtected} call. When the column was flooded
 * (water level above terrain height), the seal pass cleared the plant.
 *
 * <p>The fix adds an unconditional {@code chunk.setSealProtected(localX,
 * height + 1, localZ, true)} call after the existing {@code setHytaleBlock}
 * placement, independent of the {@code plantsPhysicsExempt} flag (which
 * gates a different concern: runtime physics + gathering interaction).
 */
public class Tp53UnderwaterPlantTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    private static final int TERRAIN_HEIGHT = 50;
    private static final int WATER_LEVEL = 64;
    private static final int PLANT_TILE_X = 5;
    private static final int PLANT_TILE_Z = 7;

    @Test
    public void plantPaintedOnFloodedColumnSurvivesExportSealPass() throws Exception {
        World2 world = buildWorldWithPlantOnFloodedTile();

        File exportBaseDir = tempDir.newFolder("tp53_underwater_plant_export");
        new HytaleWorldExporter(world, new WorldExportSettings())
                .export(exportBaseDir, "Tp53UnderwaterPlant", null, null);

        int bushCount = countPlantBushBlocksAtY(exportBaseDir, "Tp53UnderwaterPlant", TERRAIN_HEIGHT + 1);

        assertTrue("Plant_Bush at terrain+1 must survive the seal pass on a flooded column "
                        + "(found " + bushCount + " matching blocks)",
                bushCount >= 1);
    }

    private World2 buildWorldWithPlantOnFloodedTile() {
        World2 world = new World2(HYTALE, 0, 320);
        world.setName("Tp53UnderwaterPlant");
        world.setCreateGoodiesChest(false);

        long seed = 42L;
        // Flat tile factory: terrain at TERRAIN_HEIGHT (50), water at WATER_LEVEL (64).
        // Result: every column is flooded — water is above terrain everywhere.
        TileFactory tileFactory = TileFactoryFactory.createFlatTileFactory(
                seed, Terrain.GRASS, 0, 320, TERRAIN_HEIGHT, WATER_LEVEL, false, false);
        Dimension.Anchor anchor = new Dimension.Anchor(DIM_NORMAL, Dimension.Role.DETAIL, false, 0);
        Dimension dim = new Dimension(world, "Surface", seed, tileFactory, anchor);
        dim.setEventsInhibited(true);

        Tile tile = tileFactory.createTile(0, 0);
        for (int x = 0; x < 128; x++) {
            for (int z = 0; z < 128; z++) {
                tile.setHeight(x, z, TERRAIN_HEIGHT);
                tile.setWaterLevel(x, z, WATER_LEVEL);
                tile.setTerrain(x, z, Terrain.GRASS);
                HytaleTerrainLayer.setTerrainIndex(tile, x, z, HytaleTerrain.STONE.getLayerIndex());
            }
        }
        // Paint a Bush plant at one pixel.
        HytalePlantsLayer.setPlantIndex(tile, PLANT_TILE_X, PLANT_TILE_Z,
                HytaleTerrain.BUSH.getLayerIndex());
        dim.addTile(tile);
        dim.setEventsInhibited(false);
        world.addDimension(dim);
        return world;
    }

    /**
     * Walks every region file in the exported world and counts how many
     * Plant_Bush blocks sit at world Y == {@code worldY}, across every column
     * in every chunk. Used by the underwater-survival assertion: the test
     * paints exactly one Plant_Bush, so a count of >= 1 means the plant
     * survived the export pipeline.
     */
    private int countPlantBushBlocksAtY(File exportBaseDir, String worldName, int worldY) throws Exception {
        File chunksDir = new File(new File(new File(new File(exportBaseDir, worldName), "universe"),
                "worlds"), "default/chunks");
        File[] regionFiles = chunksDir.listFiles((d, n) -> n.endsWith(".region.bin"));
        assertNotNull("Export should produce a chunks directory", regionFiles);
        assertTrue("Export should produce at least one region file", regionFiles.length > 0);

        int matchCount = 0;
        for (File rfile : regionFiles) {
            HytaleRegionFile rf = new HytaleRegionFile(rfile.toPath());
            try {
                rf.open();
                for (int cx = 0; cx < HytaleChunk.CHUNK_SIZE; cx++) {
                    for (int cz = 0; cz < HytaleChunk.CHUNK_SIZE; cz++) {
                        if (! rf.hasChunk(cx, cz)) continue;
                        HytaleChunk chunk = rf.readChunk(cx, cz, 0, HytaleChunk.DEFAULT_MAX_HEIGHT);
                        if (chunk == null) continue;
                        for (int x = 0; x < HytaleChunk.CHUNK_SIZE; x++) {
                            for (int z = 0; z < HytaleChunk.CHUNK_SIZE; z++) {
                                HytaleBlock b = chunk.getHytaleBlock(x, worldY, z);
                                if (b != null && ! b.isEmpty() && "Plant_Bush".equals(b.id)) {
                                    matchCount++;
                                }
                            }
                        }
                    }
                }
            } finally {
                rf.close();
            }
        }
        return matchCount;
    }
}
