package org.pepsoft.worldpainter.hytale;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

import static org.junit.Assert.*;

/**
 * TP-49 follow-up: Hytale tree prefabs author {@code anchorY} at the trunk's
 * planting block, with trunk blocks extending DOWN to {@code bounds.minY}.
 * The previous placement code interpreted {@code anchorY} as "place this Y
 * at the world placement Y", which put the bottom trunk blocks
 * {@code (anchorY - bounds.minY)} levels below terrain — the "buried trunk"
 * visual Ferstborn reported.
 *
 * <p>This test exercises {@link HytalePrefabPaster}. The companion test for
 * the Bo2-layer placement path lives in {@code HytalePrefabJsonObjectTest}.
 *
 * <p>74% of the 1,059 bundled Hytale tree prefabs have {@code anchorY > bounds.minY},
 * so the visual regression is widespread, not localised to one tree variant.
 */
public class Tp49TreeNotBuriedTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    private static final int TERRAIN_HEIGHT = 50;
    private static final int PLACE_Y = TERRAIN_HEIGHT + 1; // exporter passes terrain+1 as the anchor world-Y
    private static final String TRUNK_ID = "Wood_Ash_Trunk";

    /**
     * A prefab with {@code anchorY=3} and trunk blocks at y=0..4 (so three
     * blocks below the anchor and one above) must, when pasted at world
     * Y={@code TERRAIN_HEIGHT+1}, place its <em>lowest</em> trunk block at
     * {@code TERRAIN_HEIGHT+1} — not at {@code TERRAIN_HEIGHT-2}.
     */
    @Test
    public void treeWithTrunkBelowAnchorPastesLowestBlockAtPlacementY() throws Exception {
        File assetsDir = writeFixturePrefab("Prefabs/Test/BuriedTrunk.prefab.json",
                /*anchorX*/ 0, /*anchorY*/ 3, /*anchorZ*/ 0,
                new FixtureBlock(0, 0, 0, TRUNK_ID),
                new FixtureBlock(0, 1, 0, TRUNK_ID),
                new FixtureBlock(0, 2, 0, TRUNK_ID),
                new FixtureBlock(0, 3, 0, TRUNK_ID),
                new FixtureBlock(0, 4, 0, TRUNK_ID));

        HytalePrefabPaster paster = new HytalePrefabPaster(assetsDir);
        HytaleChunk chunk = new HytaleChunk(0, 0, 0, 320);

        boolean pasted = paster.paste(chunk, /*anchorX*/ 5, /*anchorY*/ PLACE_Y, /*anchorZ*/ 5,
                /*worldX*/ 5, /*worldZ*/ 5, "Prefabs/Test/BuriedTrunk.prefab.json");
        assertTrue("Fixture prefab must paste successfully", pasted);

        assertNotNull("Lowest trunk block must land AT terrain+1 (not buried below)",
                trunkBlockAt(chunk, 5, PLACE_Y, 5));
        assertNotNull("Highest trunk block must land 4 above lowest: prefab's y=4 block lands at terrain+5",
                trunkBlockAt(chunk, 5, PLACE_Y + 4, 5));

        for (int dy = 1; dy <= 3; dy++) {
            assertNull("No trunk block should land BELOW terrain+1 (dy=-" + dy + ")",
                    trunkBlockAt(chunk, 5, PLACE_Y - dy, 5));
        }
    }

    /**
     * Real-assets opt-in regression: paste a real bundled {@code Ash_Stage1_001}
     * tree (burial=3 per the JSON) and assert no trunk block ends up below the
     * placement Y. Skips when the developer's HytaleAssets directory is not
     * present (e.g. CI without assets); set
     * {@code -Dworldpainter.test.hytaleAssets=<path>} to opt in elsewhere.
     */
    @Test
    public void realAshStage1TreeIsNotBuriedAfterPaste() {
        File assetsDir = locateRealAssetsDir();
        Assume.assumeTrue("Skipping: HytaleAssets/Trees/Ash/Stage_1/Ash_Stage1_001.prefab.json not found. "
                        + "Set -Dworldpainter.test.hytaleAssets=<path> to run this test.",
                assetsDir != null);

        HytalePrefabPaster paster = new HytalePrefabPaster(assetsDir);
        HytaleChunk chunk = new HytaleChunk(0, 0, 0, 320);

        boolean pasted = paster.paste(chunk, /*anchorX*/ 16, /*anchorY*/ PLACE_Y, /*anchorZ*/ 16,
                /*worldX*/ 16, /*worldZ*/ 16,
                "Prefabs/Trees/Ash/Stage_1/Ash_Stage1_001.prefab.json");
        assertTrue("Real Ash_Stage1_001 prefab must paste successfully", pasted);

        int lowestPlacedY = lowestNonEmptyY(chunk);
        assertEquals("Lowest prefab block must land at the placement Y (terrain+1); buried trunk regression",
                PLACE_Y, lowestPlacedY);
    }

    private static HytaleBlock trunkBlockAt(HytaleChunk chunk, int x, int y, int z) {
        HytaleBlock b = chunk.getHytaleBlock(x, y, z);
        if (b == null || b.isEmpty() || b.id == null || !TRUNK_ID.equals(b.id)) {
            return null;
        }
        return b;
    }

    private static int lowestNonEmptyY(HytaleChunk chunk) {
        for (int y = 0; y < HytaleChunk.DEFAULT_MAX_HEIGHT; y++) {
            for (int x = 0; x < HytaleChunk.CHUNK_SIZE; x++) {
                for (int z = 0; z < HytaleChunk.CHUNK_SIZE; z++) {
                    HytaleBlock b = chunk.getHytaleBlock(x, y, z);
                    if (b != null && !b.isEmpty()) {
                        return y;
                    }
                }
            }
        }
        return -1;
    }

    private static File locateRealAssetsDir() {
        String prop = System.getProperty("worldpainter.test.hytaleAssets");
        String marker = "Server/Prefabs/Trees/Ash/Stage_1/Ash_Stage1_001.prefab.json";
        if (prop != null) {
            File dir = new File(prop);
            if (new File(dir, marker).isFile()) {
                return dir;
            }
        }
        String[] candidates = {
                "C:\\Users\\Sotirios\\Desktop\\WorldPainter\\HytaleAssets",
                "../../HytaleAssets",
                "../../../HytaleAssets",
                "../../../../HytaleAssets",
        };
        for (String c : candidates) {
            File dir = new File(c);
            if (new File(dir, marker).isFile()) {
                return dir;
            }
        }
        return null;
    }

    private File writeFixturePrefab(String relativePath, int anchorX, int anchorY, int anchorZ,
                                    FixtureBlock... blocks) throws IOException {
        File assetsDir = tempDir.newFolder("HytaleAssets_" + System.nanoTime());
        File serverDir = new File(assetsDir, "Server");
        File prefabFile = new File(serverDir, relativePath.replace('/', File.separatorChar));
        if (! prefabFile.getParentFile().mkdirs() && ! prefabFile.getParentFile().isDirectory()) {
            throw new IOException("Failed to create fixture parent dir: " + prefabFile.getParentFile());
        }

        StringBuilder json = new StringBuilder();
        json.append("{\"anchorX\":").append(anchorX)
            .append(",\"anchorY\":").append(anchorY)
            .append(",\"anchorZ\":").append(anchorZ)
            .append(",\"blocks\":[");
        for (int i = 0; i < blocks.length; i++) {
            if (i > 0) json.append(',');
            FixtureBlock b = blocks[i];
            json.append("{\"x\":").append(b.x)
                .append(",\"y\":").append(b.y)
                .append(",\"z\":").append(b.z)
                .append(",\"name\":\"").append(b.name)
                .append("\",\"rotation\":0}");
        }
        json.append("],\"fluids\":[]}");

        try (Writer w = new FileWriter(prefabFile)) {
            w.write(json.toString());
        }
        return assetsDir;
    }

    private static final class FixtureBlock {
        final int x, y, z;
        final String name;

        FixtureBlock(int x, int y, int z, String name) {
            this.x = x; this.y = y; this.z = z; this.name = name;
        }
    }
}
