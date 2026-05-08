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
import org.pepsoft.worldpainter.layers.Bo2Layer;
import org.pepsoft.worldpainter.layers.bo2.Bo2ObjectTube;
import org.pepsoft.worldpainter.objects.WPObject;

import java.awt.Color;
import java.io.File;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.pepsoft.worldpainter.Constants.DIM_NORMAL;
import static org.pepsoft.worldpainter.DefaultPlugin.HYTALE;
import static org.pepsoft.worldpainter.objects.WPObject.ATTRIBUTE_RANDOM_ROTATION;

/**
 * Regression test for TP-49: a Hytale tree prefab placed via a {@link Bo2Layer} with random
 * rotation enabled must export with the rotated branch rotations preserved end-to-end through
 * the BSON write and read paths.
 *
 * <p>Two bugs interact here:</p>
 * <ul>
 *   <li>{@link org.pepsoft.minecraft.Material#rotate(int, org.pepsoft.worldpainter.Platform)}
 *       must transform the {@code hytale_rotation} property of branch materials so that
 *       rotated WPObjects produce branches at new orientations (covered by
 *       {@code MaterialHytaleRotationTest} and {@code HytalePrefabJsonObjectTest}).</li>
 *   <li>The deserializer in {@link HytaleRegionFile} populates {@code hytaleBlocks[]} with
 *       default-rotation blocks before reading the rotation section. Until commit ___, the
 *       rotation section's values were stored only in the parallel {@code rotations[]} array,
 *       so {@link HytaleChunk#getHytaleBlock(int, int, int)} returned blocks with
 *       {@code rotation = 0} regardless of what was actually in the file. The fix syncs the
 *       HytaleBlock object whenever {@code setRotation} is called, so the two arrays stay
 *       consistent and round-trips preserve branch orientation.</li>
 * </ul>
 */
public class Tp49EndToEndExportTest {

    private static final String PREFAB =
            "C:/Users/Sotirios/Desktop/WorldPainter/HytaleAssets/Server/Prefabs/Trees/Ash/Stage_2/Ash_Stage2_001.prefab.json";

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Test
    public void rotatedTreeExportPreservesBranchRotationsThroughBsonRoundTrip() throws Exception {
        File prefab = new File(PREFAB);
        if (! prefab.isFile()) {
            System.out.println("[TP-49] HytaleAssets prefab missing: " + prefab + " — skipping");
            return;
        }

        WPObject tree = HytalePrefabJsonObject.load(prefab);
        tree.setAttribute(ATTRIBUTE_RANDOM_ROTATION, Boolean.TRUE);

        Bo2ObjectTube provider = new Bo2ObjectTube("ash-tree", Collections.singletonList(tree));
        Bo2Layer treeLayer = new Bo2Layer(provider, "Ash trees with random rotation", new Color(160, 80, 0));
        treeLayer.setDensity(1);
        treeLayer.setGridX(1);
        treeLayer.setGridY(1);

        World2 world = new World2(HYTALE, 0, 320);
        world.setName("Tp49EndToEnd");
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
                HytaleTerrainLayer.setTerrainIndex(tile, x, z, HytaleTerrain.GRASS.getLayerIndex());
                tile.setLayerValue(treeLayer, x, z, 15);
            }
        }
        dim.addTile(tile);
        dim.setEventsInhibited(false);
        world.addDimension(dim);

        File exportBaseDir = tempDir.newFolder("tp49_export");
        new HytaleWorldExporter(world, new WorldExportSettings()).export(exportBaseDir, "Tp49EndToEnd", null, null);

        File chunksDir = new File(new File(new File(new File(exportBaseDir, "Tp49EndToEnd"), "universe"), "worlds"),
                "default/chunks");
        File[] regionFiles = chunksDir.listFiles((d, n) -> n.endsWith(".region.bin"));
        assertNotNull(regionFiles);
        assertTrue("export should produce at least one region file", regionFiles.length > 0);

        // Sum rotation counts across all chunks of all regions, looking specifically at
        // Branch_Long blocks. These come in three families in the source prefab:
        //   - 9 with rotation=4 (Pitch=90, Yaw=0)
        //   - 17 with rotation=5 (Pitch=90, Yaw=1)
        //   - 11 with no explicit rotation (rotation=0, vertical Y-axis pipe)
        //
        // After random rotation by Bo2LayerExporter the horizontal pipes are spread across
        // rotations {4, 5, 6, 7} and the vertical pipes stay at 0. We assert every horizontal
        // family is represented to ensure rotation bytes survive the BSON round-trip.
        boolean[] horizontalSeen = new boolean[4];   // index = rotation - 4
        long longBranchTotal = 0;
        long cornerBranchTotal = 0;
        java.util.Map<Integer, Integer> cornerHist = new java.util.TreeMap<>();
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
                            for (int y = 0; y < HytaleChunk.DEFAULT_MAX_HEIGHT; y++) {
                                for (int z = 0; z < HytaleChunk.CHUNK_SIZE; z++) {
                                    HytaleBlock b = chunk.getHytaleBlock(x, y, z);
                                    if (b == null || b.isEmpty()) {
                                        continue;
                                    }
                                    if (b.id == null) {
                                        continue;
                                    }
                                    if (b.id.endsWith("_Branch_Corner")) {
                                        cornerBranchTotal++;
                                        cornerHist.merge(b.rotation & 0x3F, 1, Integer::sum);
                                        continue;
                                    }
                                    if (! b.id.endsWith("_Branch_Long")) {
                                        continue;
                                    }
                                    longBranchTotal++;
                                    int rot = b.rotation & 0x3F;
                                    if (rot >= 4 && rot <= 7) {
                                        horizontalSeen[rot - 4] = true;
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
        assertTrue("Test expected the export to actually place Branch_Long blocks", longBranchTotal > 100);
        for (int r = 4; r <= 7; r++) {
            assertTrue("Branch_Long rotation " + r + " missing from BSON round-trip; rotations are being dropped (TP-49 regression)",
                    horizontalSeen[r - 4]);
        }
        System.out.println("[TP-49] Branch_Corner total=" + cornerBranchTotal + " hist=" + cornerHist);
        assertTrue("Branch_Corner blocks missing from export — source has 7 per tree, tree-corner connectivity is broken",
                cornerBranchTotal > 100);
    }
}
