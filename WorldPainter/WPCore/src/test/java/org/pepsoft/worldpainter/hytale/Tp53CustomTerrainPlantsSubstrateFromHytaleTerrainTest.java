package org.pepsoft.worldpainter.hytale;

import org.pepsoft.worldpainter.hytale.chunk.HytaleChunk;
import org.pepsoft.worldpainter.hytale.chunk.HytaleRegionFile;
import org.pepsoft.worldpainter.hytale.export.HytaleWorldExporter;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.MixedMaterial;
import org.pepsoft.worldpainter.MixedMaterial.Row;
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
 * Regression test (TP-53 follow-up): when a Custom Terrain's MixedMaterial has
 * no solid block in its mix (only surface-only plants), the substrate written
 * at {@code y == height} must fall back to the HytaleTerrain painted at the
 * pixel via {@link HytaleTerrainLayer}, not to a hard-coded
 * {@link HytaleBlock#DIRT}. The user's intent: "I painted Sand here, then
 * painted my plant-mix Custom Terrain on top — the Sand should survive."
 *
 * <p>Prior to this fix, {@code getSurfaceOnlySubstrate} returned
 * {@code HytaleBlock.DIRT} as the unconditional fallback when the
 * MixedMaterial scan found no solid block. The fix lets a caller-provided
 * solid {@link HytaleTerrain} act as the fallback before DIRT.
 */
public class Tp53CustomTerrainPlantsSubstrateFromHytaleTerrainTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    private static final int CUSTOM_INDEX = 0;
    private static final int TERRAIN_HEIGHT = 50;
    private static final int WATER_LEVEL = 0;

    private MixedMaterial savedCustomMaterial;

    @After
    public void restoreCustomMaterial() {
        Terrain.setCustomMaterial(CUSTOM_INDEX, savedCustomMaterial);
    }

    @Test
    public void customTerrainPlantMixUsesHytaleTerrainLayerAsSubstrate() throws Exception {
        savedCustomMaterial = Terrain.getCustomMaterial(CUSTOM_INDEX);

        MixedMaterial plantsMix = new MixedMaterial("MixedPlantsForSubstrateTest",
                new Row[] {
                        new Row(Material.GRASS, 1, 1.0f),
                        new Row(Material.FERN, 1, 1.0f),
                },
                -1, null);
        Terrain.setCustomMaterial(CUSTOM_INDEX, plantsMix);

        World2 world = buildWorldWithSandHytaleSubstrateAndCustomPlantMixTerrain();

        File exportBaseDir = tempDir.newFolder("tp53_custom_substrate_export");
        new HytaleWorldExporter(world, new WorldExportSettings())
                .export(exportBaseDir, "Tp53CustomSubstrate", null, null);

        int sandCount = countBlocksAtYWithId(exportBaseDir, "Tp53CustomSubstrate",
                TERRAIN_HEIGHT, "Soil_Sand");
        int dirtCount = countBlocksAtYWithId(exportBaseDir, "Tp53CustomSubstrate",
                TERRAIN_HEIGHT, "Soil_Dirt");

        assertTrue("Custom-terrain plant-mix substrate must honour HytaleTerrainLayer "
                        + "(found " + sandCount + " Soil_Sand at y=" + TERRAIN_HEIGHT
                        + ", expected > 0)",
                sandCount > 0);
        assertEquals("No Soil_Dirt should be written at the surface when HytaleTerrainLayer "
                        + "supplies a solid substrate (found " + dirtCount + ")",
                0, dirtCount);
    }

    private World2 buildWorldWithSandHytaleSubstrateAndCustomPlantMixTerrain() {
        World2 world = new World2(HYTALE, 0, 320);
        world.setName("Tp53CustomSubstrate");
        world.setCreateGoodiesChest(false);

        long seed = 42L;
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
                tile.setTerrain(x, z, Terrain.CUSTOM_1);
                HytaleTerrainLayer.setTerrainIndex(tile, x, z, HytaleTerrain.SAND.getLayerIndex());
            }
        }
        dim.addTile(tile);
        dim.setEventsInhibited(false);
        world.addDimension(dim);
        return world;
    }

    private int countBlocksAtYWithId(File exportBaseDir, String worldName, int worldY, String expectedId)
            throws Exception {
        File chunksDir = new File(new File(new File(new File(exportBaseDir, worldName), "universe"),
                "worlds"), "default/chunks");
        File[] regionFiles = chunksDir.listFiles((d, n) -> n.endsWith(".region.bin"));
        assertNotNull("Export should produce a chunks directory", regionFiles);
        assertTrue("Export should produce at least one region file", regionFiles.length > 0);

        int count = 0;
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
                                if (b != null && expectedId.equals(b.id)) {
                                    count++;
                                }
                            }
                        }
                    }
                }
            } finally {
                rf.close();
            }
        }
        return count;
    }
}
