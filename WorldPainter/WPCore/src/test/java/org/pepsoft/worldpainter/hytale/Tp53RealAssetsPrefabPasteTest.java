package org.pepsoft.worldpainter.hytale;

import org.junit.Assume;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

/**
 * End-to-end-ish regression test for TP-53 follow-up using a REAL bundled
 * Hytale prefab (rather than a synthetic fixture). Confirms that a real
 * {@code Plants/Seaweed/Normal/Seaweed_Normal_001.prefab.json} pasted by
 * {@link HytalePrefabPaster} into a flooded column survives the post-export
 * seal pass at {@link HytaleWorldExporter#sealAboveTerrainColumn}.
 *
 * <p>This test is opt-in: it skips if the user's HytaleAssets directory cannot
 * be located. To run it explicitly, set the system property
 * {@code worldpainter.test.hytaleAssets} to the absolute path of a directory
 * containing {@code Server/Prefabs/Plants/Seaweed/Normal/Seaweed_Normal_001.prefab.json}.
 * On the developer's local machine the test also probes a couple of conventional
 * sibling-of-worktree locations so it runs without configuration.
 */
public class Tp53RealAssetsPrefabPasteTest {

    private static final int TERRAIN_HEIGHT = 50;
    private static final int WATER_LEVEL = 64;
    private static final String FLUID_ID = HytaleBlockMapping.HY_WATER;
    private static final String SEAWEED_PREFAB_PATH =
            "Prefabs/Plants/Seaweed/Normal/Seaweed_Normal_001.prefab.json";

    /**
     * Attempts to locate a real HytaleAssets directory containing the Seaweed
     * prefab. Returns null if nothing is found — the caller {@code Assume}s.
     */
    private static File locateRealAssetsDir() {
        String prop = System.getProperty("worldpainter.test.hytaleAssets");
        if (prop != null) {
            File dir = new File(prop);
            if (new File(dir, "Server/" + SEAWEED_PREFAB_PATH).isFile()) {
                return dir;
            }
        }
        // Conventional sibling-of-project-root locations (developer's machine).
        String[] candidates = {
                "C:\\Users\\Sotirios\\Desktop\\WorldPainter\\HytaleAssets",
                "../../HytaleAssets",
                "../../../HytaleAssets",
                "../../../../HytaleAssets",
        };
        for (String c : candidates) {
            File dir = new File(c);
            if (new File(dir, "Server/" + SEAWEED_PREFAB_PATH).isFile()) {
                return dir;
            }
        }
        return null;
    }

    @Test
    public void realSeaweedPrefabSurvivesSealPassWhenSubmerged() {
        File assetsDir = locateRealAssetsDir();
        Assume.assumeTrue("Skipping: HytaleAssets directory with the Seaweed prefab not found. "
                + "Set -Dworldpainter.test.hytaleAssets=<path> to run this test.",
                assetsDir != null);

        HytalePrefabPaster paster = new HytalePrefabPaster(assetsDir);
        HytaleChunk chunk = new HytaleChunk(0, 0, 0, 320);

        boolean pasted = paster.paste(chunk, /*anchorX*/ 5, /*anchorY*/ TERRAIN_HEIGHT + 1, /*anchorZ*/ 5,
                /*worldX*/ 5, /*worldZ*/ 5, SEAWEED_PREFAB_PATH);
        assertTrue("Real seaweed prefab must paste successfully from " + assetsDir, pasted);

        int placedSeaweedCount = countSeaweedBlocks(chunk);
        assertTrue("At least one Plant_Seaweed_* block should be placed before the seal pass "
                        + "(found " + placedSeaweedCount + ")",
                placedSeaweedCount >= 1);

        HytaleWorldExporter.sealAboveTerrainColumn(
                chunk, 5, 5, TERRAIN_HEIGHT, WATER_LEVEL, FLUID_ID);

        int survivedSeaweedCount = countSeaweedBlocks(chunk);
        assertEquals("Every seaweed block placed before the seal pass must survive it "
                        + "(placed=" + placedSeaweedCount + ", survived=" + survivedSeaweedCount + ")",
                placedSeaweedCount, survivedSeaweedCount);
    }

    /**
     * Walks the chunk and counts blocks whose id starts with {@code Plant_Seaweed_}.
     * Iterates every voxel in the chunk to defend against the prefab being multi-column.
     */
    private static int countSeaweedBlocks(HytaleChunk chunk) {
        int count = 0;
        for (int x = 0; x < HytaleChunk.CHUNK_SIZE; x++) {
            for (int z = 0; z < HytaleChunk.CHUNK_SIZE; z++) {
                for (int y = 0; y < HytaleChunk.DEFAULT_MAX_HEIGHT; y++) {
                    HytaleBlock b = chunk.getHytaleBlock(x, y, z);
                    if (b != null && !b.isEmpty() && b.id != null
                            && b.id.startsWith("Plant_Seaweed_")) {
                        count++;
                    }
                }
            }
        }
        return count;
    }
}
