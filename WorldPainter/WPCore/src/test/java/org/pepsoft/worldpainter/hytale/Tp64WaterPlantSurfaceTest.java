package org.pepsoft.worldpainter.hytale;

import org.pepsoft.worldpainter.hytale.chunk.HytaleChunk;
import org.pepsoft.worldpainter.hytale.chunk.HytaleRegionFile;
import org.pepsoft.worldpainter.hytale.export.HytaleWorldExporter;

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
 * Regression test for TP-64: water lilies and duckweed painted over water must
 * float on the water <em>surface</em>, not sink to the water floor.
 *
 * <p>Floating water plants ({@code Plant_Flower_Water_*}) are surface-only
 * vegetation blocks, so before the fix every plant-placement path in
 * {@link HytaleWorldExporter} placed them at {@code terrainHeight + 1}. On a
 * flooded column that voxel is the <em>bottom</em> of the water body, so the
 * lily appeared submerged at the floor instead of resting on the surface.
 *
 * <p>The fix routes these plants to {@code waterLevel + 1} — the air cell
 * directly above the topmost fluid block — when the column is flooded.
 */
public class Tp64WaterPlantSurfaceTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    private static final int TERRAIN_HEIGHT = 50;
    private static final int WATER_LEVEL = 64;
    private static final int PLANT_TILE_X = 5;
    private static final int PLANT_TILE_Z = 7;

    /** Block id behind {@link HytaleTerrain#WATER_LILY}. */
    private static final String WATER_LILY_BLOCK = "Plant_Flower_Water_Green";

    @Test
    public void waterLilyPaintedOnFloodedColumnFloatsAtSurface() throws Exception {
        World2 world = buildWorldWithWaterLilyOnFloodedTile();

        File exportBaseDir = tempDir.newFolder("tp64_water_plant_export");
        new HytaleWorldExporter(world, new WorldExportSettings())
                .export(exportBaseDir, "Tp64WaterPlant", null, null);

        int atSurface = countBlocksAtY(exportBaseDir, "Tp64WaterPlant", WATER_LILY_BLOCK, WATER_LEVEL + 1);
        int atFloor = countBlocksAtY(exportBaseDir, "Tp64WaterPlant", WATER_LILY_BLOCK, TERRAIN_HEIGHT + 1);

        assertEquals("Water lily must rest on the water surface (Y=" + (WATER_LEVEL + 1) + ")",
                1, atSurface);
        assertEquals("Water lily must not be left at the water floor (Y=" + (TERRAIN_HEIGHT + 1) + ")",
                0, atFloor);
    }

    private World2 buildWorldWithWaterLilyOnFloodedTile() {
        World2 world = new World2(HYTALE, 0, 320);
        world.setName("Tp64WaterPlant");
        world.setCreateGoodiesChest(false);

        long seed = 42L;
        // Flat tile factory: terrain at TERRAIN_HEIGHT, water at WATER_LEVEL.
        // Every column is flooded — water is above terrain everywhere.
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
        // Paint a Water Lily at one pixel via the plants overlay layer.
        HytalePlantsLayer.setPlantIndex(tile, PLANT_TILE_X, PLANT_TILE_Z,
                HytaleTerrain.WATER_LILY.getLayerIndex());
        dim.addTile(tile);
        dim.setEventsInhibited(false);
        world.addDimension(dim);
        return world;
    }

    /**
     * Counts how many blocks with id {@code blockId} sit at world Y ==
     * {@code worldY}, across every column in every exported chunk.
     */
    private int countBlocksAtY(File exportBaseDir, String worldName, String blockId, int worldY) throws Exception {
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
                                if (b != null && ! b.isEmpty() && blockId.equals(b.id)) {
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
