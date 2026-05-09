package org.pepsoft.worldpainter.hytale;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

import static org.junit.Assert.*;

/**
 * Regression test for TP-53 follow-up: blocks pasted by {@link HytalePrefabPaster}
 * (i.e. bundled Hytale prefabs from the catalog) must be marked seal-protected so
 * the post-export seal pass does NOT wipe them when they land in a flooded column.
 *
 * <p>Prior to the fix, {@code paste()} called {@code chunk.setHytaleBlock(...)}
 * with no follow-up {@code setSealProtected(...)} call, so each pasted block
 * defaulted to {@code SUPPORT_NONE} with {@code isSealProtected() == false},
 * and {@link HytaleWorldExporter#sealAboveTerrainColumn} cleared it.
 *
 * <p>The fix adds {@code chunk.setSealProtected(bx, by, bz, true)} immediately
 * after the per-block {@code setHytaleBlock} call in {@link HytalePrefabPaster#paste}.
 */
public class Tp53UnderwaterPrefabPasterTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    private static final int TERRAIN_HEIGHT = 50;
    private static final int WATER_LEVEL = 64;
    private static final String FLUID_ID = HytaleBlockMapping.HY_WATER;
    private static final String SEAWEED_ID = "Plant_Seaweed_Arid_Stack";

    /**
     * Single-block prefab pasted at terrain+1 in a flooded column must
     * survive the seal pass. Without the fix this test fails because the
     * seaweed block has SUPPORT_NONE + !isSealProtected, so the seal pass
     * clears it.
     */
    @Test
    public void singleBlockPrefabSurvivesSealPassWhenSubmerged() throws Exception {
        File assetsDir = writeFixturePrefab("Prefabs/Test/SingleSeaweed.prefab.json",
                /*anchorX*/ 0, /*anchorY*/ 0, /*anchorZ*/ 0,
                new FixtureBlock(0, 0, 0, SEAWEED_ID, 0));

        HytalePrefabPaster paster = new HytalePrefabPaster(assetsDir);
        HytaleChunk chunk = new HytaleChunk(0, 0, 0, 320);

        boolean pasted = paster.paste(chunk, /*anchorX*/ 5, /*anchorY*/ TERRAIN_HEIGHT + 1, /*anchorZ*/ 5,
                /*worldX*/ 5, /*worldZ*/ 5, "Prefabs/Test/SingleSeaweed.prefab.json");
        assertTrue("Fixture prefab must paste successfully", pasted);

        // Sanity: the block IS placed before the seal pass runs.
        HytaleBlock placed = chunk.getHytaleBlock(5, TERRAIN_HEIGHT + 1, 5);
        assertNotNull("Prefab block should be placed before seal pass", placed);
        assertEquals(SEAWEED_ID, placed.id);

        // Run the seal pass over the flooded column.
        HytaleWorldExporter.sealAboveTerrainColumn(
                chunk, 5, 5, TERRAIN_HEIGHT, WATER_LEVEL, FLUID_ID);

        HytaleBlock survived = chunk.getHytaleBlock(5, TERRAIN_HEIGHT + 1, 5);
        assertNotNull("Submerged prefab block must survive the seal pass", survived);
        assertEquals("Submerged prefab block must still be the seaweed block",
                SEAWEED_ID, survived.id);

        // The fluid layer must still be filled around the surviving block.
        int fluidId = chunk.getSections()[(TERRAIN_HEIGHT + 1) >> 5]
                .getFluidId(5, (TERRAIN_HEIGHT + 1) & 31, 5);
        assertTrue("Fluid must be present around the surviving prefab block", fluidId > 0);
    }

    /**
     * Multi-block prefab spanning the water surface: blocks below
     * {@code waterLevel} are inside the seal range; blocks above are not.
     * All must survive — submerged blocks because they are seal-protected,
     * above-water blocks because they are outside the seal pass range.
     */
    @Test
    public void tallPrefabSpanningWaterSurfaceSurvivesEntirely() throws Exception {
        // 10-block-tall stack at the same column, anchored at terrain+1.
        FixtureBlock[] tallStack = new FixtureBlock[10];
        for (int i = 0; i < 10; i++) {
            tallStack[i] = new FixtureBlock(0, i, 0, SEAWEED_ID, 0);
        }
        File assetsDir = writeFixturePrefab("Prefabs/Test/TallSeaweed.prefab.json",
                0, 0, 0, tallStack);

        HytalePrefabPaster paster = new HytalePrefabPaster(assetsDir);
        HytaleChunk chunk = new HytaleChunk(0, 0, 0, 320);

        // anchorY = TERRAIN_HEIGHT + 1 = 51. Plant occupies y=51..60. Water
        // level is 64, so y=51..64 is in the seal range, y=65+ is not.
        boolean pasted = paster.paste(chunk, 5, TERRAIN_HEIGHT + 1, 5,
                5, 5, "Prefabs/Test/TallSeaweed.prefab.json");
        assertTrue(pasted);

        HytaleWorldExporter.sealAboveTerrainColumn(
                chunk, 5, 5, TERRAIN_HEIGHT, WATER_LEVEL, FLUID_ID);

        // Every plant block must remain. y=51..60 (10 blocks).
        for (int dy = 0; dy < 10; dy++) {
            int y = TERRAIN_HEIGHT + 1 + dy;
            HytaleBlock survived = chunk.getHytaleBlock(5, y, 5);
            assertNotNull("Block at y=" + y + " must survive seal pass", survived);
            assertEquals("Block at y=" + y + " must still be seaweed",
                    SEAWEED_ID, survived.id);
        }
    }

    /**
     * Regression: prefab pasted on a non-flooded column (water level <= terrain
     * height) must continue to work as it does today. The seal pass has a
     * zero-length iteration on dry columns so this is a no-op, but we verify
     * explicitly.
     */
    @Test
    public void prefabOnDryColumnUnaffectedByFix() throws Exception {
        File assetsDir = writeFixturePrefab("Prefabs/Test/DryBlock.prefab.json",
                0, 0, 0, new FixtureBlock(0, 0, 0, SEAWEED_ID, 0));

        HytalePrefabPaster paster = new HytalePrefabPaster(assetsDir);
        HytaleChunk chunk = new HytaleChunk(0, 0, 0, 320);

        paster.paste(chunk, 5, TERRAIN_HEIGHT + 1, 5, 5, 5, "Prefabs/Test/DryBlock.prefab.json");

        // No flooding: waterLevel == terrainHeight, so seal pass loop is empty.
        HytaleWorldExporter.sealAboveTerrainColumn(
                chunk, 5, 5, TERRAIN_HEIGHT, /*waterLevel*/ TERRAIN_HEIGHT, FLUID_ID);

        HytaleBlock placed = chunk.getHytaleBlock(5, TERRAIN_HEIGHT + 1, 5);
        assertNotNull(placed);
        assertEquals(SEAWEED_ID, placed.id);
    }

    // ── Test helpers ──────────────────────────────────────────────────────

    /**
     * Writes a minimal prefab JSON fixture under {@code <tempDir>/Server/<relativePath>}
     * and returns the assets directory ({@code <tempDir>}) for {@link HytalePrefabPaster}'s
     * constructor. The paster expects assetsDir/Server/ as its prefab root.
     */
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
                .append("\",\"rotation\":").append(b.rotation)
                .append('}');
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
        final int rotation;

        FixtureBlock(int x, int y, int z, String name, int rotation) {
            this.x = x; this.y = y; this.z = z; this.name = name; this.rotation = rotation;
        }
    }
}
