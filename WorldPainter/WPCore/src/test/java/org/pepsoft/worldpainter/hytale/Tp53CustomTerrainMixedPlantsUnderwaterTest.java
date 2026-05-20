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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.pepsoft.worldpainter.Constants.DIM_NORMAL;
import static org.pepsoft.worldpainter.DefaultPlugin.HYTALE;

/**
 * Regression test (TP-53 follow-up): a Custom Terrain whose MixedMaterial mixes
 * multiple surface-only (plant) blocks must place plants on flooded columns just
 * like the {@link HytalePlantsLayer} overlay does for single-plant paints.
 *
 * <p>Prior to this fix, the {@code populateChunkFromTile} per-pixel loop in
 * {@link HytaleWorldExporter} wrote the custom-terrain {@code surfacePlant} at
 * {@code height + 1} <em>before</em> the inline fluid loop and did not call
 * {@code setSealProtected}. The fluid loop's
 * {@code chunk.setHytaleBlock(localX, y, localZ, HytaleBlock.EMPTY)} on flooded
 * columns wiped the plant; the post-export
 * {@link HytaleWorldExporter#sealAboveTerrainColumn} pass wiped it again. Result:
 * zero plant blocks at terrain+1 underwater for custom-terrain MixedMaterials,
 * even though the sibling {@link HytalePlantsLayer} overlay placed plants
 * correctly (the original {@code a2358135} fix covered only that overlay path).
 *
 * <p>Single-plant paints (e.g. an Anemone selected from the Hytale terrain
 * palette) are unaffected: TP-60's {@link HytalePlantsLayer#routePaint} routes
 * surface-only terrains into the overlay layer instead of the main terrain
 * layer, so they exit through the already-fixed overlay path.
 */
public class Tp53CustomTerrainMixedPlantsUnderwaterTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    private static final int CUSTOM_INDEX = 0;
    private static final int TERRAIN_HEIGHT = 50;
    private static final int WATER_LEVEL = 64;

    private MixedMaterial savedCustomMaterial;

    @After
    public void restoreCustomMaterial() {
        Terrain.setCustomMaterial(CUSTOM_INDEX, savedCustomMaterial);
    }

    @Test
    public void customTerrainMixOfPlantsSurvivesFloodedColumnExport() throws Exception {
        savedCustomMaterial = Terrain.getCustomMaterial(CUSTOM_INDEX);

        MixedMaterial plantsMix = new MixedMaterial("MixedPlants",
                new Row[] {
                        new Row(Material.GRASS, 1, 1.0f),
                        new Row(Material.FERN, 1, 1.0f),
                },
                -1, null);
        Terrain.setCustomMaterial(CUSTOM_INDEX, plantsMix);

        World2 world = buildFloodedWorldWithCustomMixedPlantsTerrain();

        File exportBaseDir = tempDir.newFolder("tp53_custom_mix_export");
        new HytaleWorldExporter(world, new WorldExportSettings())
                .export(exportBaseDir, "Tp53CustomMixUnderwater", null, null);

        int plantCount = countSurfaceOnlyBlocksAtY(exportBaseDir,
                "Tp53CustomMixUnderwater", TERRAIN_HEIGHT + 1);

        assertTrue("Custom-terrain mixed-plants at terrain+1 must survive the seal pass on flooded columns "
                        + "(found " + plantCount + " surface-only blocks at y=" + (TERRAIN_HEIGHT + 1) + ")",
                plantCount >= 1);
    }

    private World2 buildFloodedWorldWithCustomMixedPlantsTerrain() {
        World2 world = new World2(HYTALE, 0, 320);
        world.setName("Tp53CustomMixUnderwater");
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
            }
        }
        dim.addTile(tile);
        dim.setEventsInhibited(false);
        world.addDimension(dim);
        return world;
    }

    /**
     * Walks every region file in the exported world and counts surface-only
     * Hytale blocks at world Y == {@code worldY}. Counts the union of plant ids
     * (Plant_Grass_Lush, Plant_Fern) the MixedMaterial can produce so the
     * assertion does not depend on which row the sampler picks per pixel.
     */
    private int countSurfaceOnlyBlocksAtY(File exportBaseDir, String worldName, int worldY) throws Exception {
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
                                if (b != null && ! b.isEmpty()
                                        && HytaleBlockRegistry.isSurfaceOnlyBlock(b.id)) {
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
