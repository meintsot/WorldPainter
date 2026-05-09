package org.pepsoft.worldpainter.hytale;

import org.junit.Assume;
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
import org.pepsoft.worldpainter.layers.Bo2Layer;
import org.pepsoft.worldpainter.layers.bo2.Bo2ObjectTube;
import org.pepsoft.worldpainter.objects.WPObject;

import java.awt.Color;
import java.io.File;
import java.util.Collections;

import static org.junit.Assert.*;
import static org.pepsoft.worldpainter.Constants.DIM_NORMAL;
import static org.pepsoft.worldpainter.DefaultPlugin.HYTALE;
import static org.pepsoft.worldpainter.objects.WPObject.ATTRIBUTE_SPAWN_IN_LAVA;
import static org.pepsoft.worldpainter.objects.WPObject.ATTRIBUTE_SPAWN_IN_WATER;
import static org.pepsoft.worldpainter.objects.WPObject.ATTRIBUTE_SPAWN_ON_LAND;
import static org.pepsoft.worldpainter.objects.WPObject.ATTRIBUTE_SPAWN_ON_LAVA;
import static org.pepsoft.worldpainter.objects.WPObject.ATTRIBUTE_SPAWN_ON_WATER;

/**
 * Replicates the user's exact GUI setup from TP-53 follow-up smoke testing:
 * a real bundled {@code Seaweed_Arid_002} prefab loaded as a WPObject and
 * placed via a {@link Bo2Layer} with ONLY {@code SPAWN_IN_WATER} checked
 * (no SPAWN_ON_LAND, no SPAWN_ON_WATER) on a fully-flooded tile. Exports,
 * reads back the region files, and counts Plant_Seaweed_Arid_Stack blocks.
 *
 * <p>Pass: at least one seaweed block lands in the export.
 * Fail: zero blocks → real export-pipeline bug for the user's flow.
 */
public class Tp53Bo2UnderwaterOnlyTest {

    private static final String PREFAB =
            "C:/Users/Sotirios/Desktop/WorldPainter/HytaleAssets/Server/Prefabs/Plants/Seaweed/Arid/Seaweed_Arid_002.prefab.json";

    private static final int TERRAIN_HEIGHT = 50;
    private static final int WATER_LEVEL = 64;

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Test
    public void seaweedBo2WithOnlyUnderwaterFlagSpawnsOnFloodedTile() throws Exception {
        File prefab = new File(PREFAB);
        Assume.assumeTrue("Skipping: Seaweed_Arid_002 prefab not present at " + PREFAB, prefab.isFile());

        // 1. Load the real prefab as a WPObject.
        WPObject seaweed = HytalePrefabJsonObject.load(prefab);

        // 2. Set spawn attributes to exactly what the user has checked: only "under water".
        seaweed.setAttribute(ATTRIBUTE_SPAWN_IN_WATER, Boolean.TRUE);
        seaweed.setAttribute(ATTRIBUTE_SPAWN_ON_LAND, Boolean.FALSE);
        seaweed.setAttribute(ATTRIBUTE_SPAWN_ON_WATER, Boolean.FALSE);
        seaweed.setAttribute(ATTRIBUTE_SPAWN_IN_LAVA, Boolean.FALSE);
        seaweed.setAttribute(ATTRIBUTE_SPAWN_ON_LAVA, Boolean.FALSE);

        // 3. Wrap in a Bo2 Custom Layer.
        Bo2ObjectTube provider = new Bo2ObjectTube("seaweed-arid-002", Collections.singletonList(seaweed));
        Bo2Layer seaweedLayer = new Bo2Layer(provider, "Seaweed underwater-only", new Color(0, 200, 0));
        seaweedLayer.setDensity(1);
        seaweedLayer.setGridX(1);
        seaweedLayer.setGridY(1);

        // 4. Build a Hytale world with a fully-flooded tile: terrain at 50, water at 64.
        World2 world = new World2(HYTALE, 0, 320);
        world.setName("Tp53Bo2Underwater");
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
                tile.setTerrain(x, z, Terrain.GRASS);
                HytaleTerrainLayer.setTerrainIndex(tile, x, z, HytaleTerrain.STONE.getLayerIndex());
                // Paint the Bo2 layer at maximum value across the whole tile.
                tile.setLayerValue(seaweedLayer, x, z, 15);
            }
        }
        dim.addTile(tile);
        dim.setEventsInhibited(false);
        world.addDimension(dim);

        // 5. Export.
        File exportBaseDir = tempDir.newFolder("tp53_bo2_underwater_export");
        new HytaleWorldExporter(world, new WorldExportSettings())
                .export(exportBaseDir, "Tp53Bo2Underwater", null, null);

        // 6. Walk regions and count Plant_Seaweed_* blocks at any Y.
        File chunksDir = new File(new File(new File(new File(exportBaseDir, "Tp53Bo2Underwater"), "universe"),
                "worlds"), "default/chunks");
        File[] regionFiles = chunksDir.listFiles((d, n) -> n.endsWith(".region.bin"));
        assertNotNull("Export should produce a chunks directory", regionFiles);
        assertTrue("Export should produce at least one region file", regionFiles.length > 0);

        int seaweedTotal = 0;
        int firstSeaweedY = -1;
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
                                for (int y = 1; y < HytaleChunk.DEFAULT_MAX_HEIGHT; y++) {
                                    HytaleBlock b = chunk.getHytaleBlock(x, y, z);
                                    if (b != null && !b.isEmpty() && b.id != null
                                            && b.id.startsWith("Plant_Seaweed_")) {
                                        seaweedTotal++;
                                        if (firstSeaweedY < 0) {
                                            firstSeaweedY = y;
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

        System.out.println("[Tp53Bo2UnderwaterOnlyTest] Plant_Seaweed_* count = " + seaweedTotal
                + ", first y = " + firstSeaweedY
                + " (expected near terrainHeight+1 = " + (TERRAIN_HEIGHT + 1) + ")");

        assertTrue("Expected at least one Plant_Seaweed_* block in the export, got " + seaweedTotal
                        + ". This proves whether the Bo2 underwater-only path actually lands blocks.",
                seaweedTotal >= 1);

        // If we got blocks, also assert they're at the expected Y range.
        // Seaweed_Arid_002 is 5 blocks (y=0..4). Anchor at terrain+1=51 means blocks at 51..55.
        assertTrue("First seaweed Y (" + firstSeaweedY + ") should be at or above terrainHeight+1 ("
                        + (TERRAIN_HEIGHT + 1) + ")",
                firstSeaweedY >= TERRAIN_HEIGHT + 1);
        assertTrue("First seaweed Y (" + firstSeaweedY + ") should be at or below terrainHeight+5 ("
                        + (TERRAIN_HEIGHT + 5) + ")",
                firstSeaweedY <= TERRAIN_HEIGHT + 5);
    }
}
