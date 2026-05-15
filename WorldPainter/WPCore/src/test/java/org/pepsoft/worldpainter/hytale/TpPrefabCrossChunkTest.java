package org.pepsoft.worldpainter.hytale;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Regression for {@link HytalePrefabPaster}'s multi-chunk paste path. Before the
 * cross-chunk fix, {@link HytalePrefabPaster#paste(HytaleChunk, int, int, int, int, int, String)}
 * dropped any block whose footprint extended past the 32x32 column bounds of the
 * single chunk being pasted into, so prefabs anchored near a chunk boundary lost
 * their overhanging columns. The new
 * {@link HytalePrefabPaster#paste(Map, int, int, int, int, int, String)} overload
 * routes each block to the correct chunk in a {@code chunksByCoords} map, so a
 * prefab whose footprint spans two chunks lands intact in both.
 */
public class TpPrefabCrossChunkTest {

    private static final int MIN_HEIGHT = 0;
    private static final int MAX_HEIGHT = 320;
    private static final int ANCHOR_Y = 64;

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Test
    public void prefabSpanningTwoChunksLandsInBothChunks() throws Exception {
        // 5-block-wide-in-X prefab, mask everywhere, anchor at the prefab's own (0,0,0).
        // We'll anchor it at world X=30 inside Hytale chunk 0 — its blocks at prefab
        // x=0..4 (offset relative to anchor) span world X=30..34, which crosses the
        // chunk-0 / chunk-1 boundary at world X=32.
        File assetsDir = writeFixturePrefab("Prefabs/Test/HorizontalBar.prefab.json",
                /*anchorX*/ 0, /*anchorY*/ 0, /*anchorZ*/ 0,
                new FixtureBlock(0, 0, 0, "Rock_Stone"),
                new FixtureBlock(1, 0, 0, "Rock_Stone"),
                new FixtureBlock(2, 0, 0, "Rock_Stone"),
                new FixtureBlock(3, 0, 0, "Rock_Stone"),
                new FixtureBlock(4, 0, 0, "Rock_Stone"));

        HytalePrefabPaster paster = new HytalePrefabPaster(assetsDir);

        // Set up a two-chunk region. blockOffset = 0 so WP X == Hytale X.
        HytaleChunk chunk0 = new HytaleChunk(0, 0, MIN_HEIGHT, MAX_HEIGHT);
        HytaleChunk chunk1 = new HytaleChunk(1, 0, MIN_HEIGHT, MAX_HEIGHT);
        Map<Long, HytaleChunk> chunksByCoords = new HashMap<>();
        chunksByCoords.put(HytalePrefabPaster.chunkKey(0, 0), chunk0);
        chunksByCoords.put(HytalePrefabPaster.chunkKey(1, 0), chunk1);

        boolean pasted = paster.paste(chunksByCoords,
                /*anchorWorldX*/ 30, /*anchorY*/ ANCHOR_Y, /*anchorWorldZ*/ 5,
                /*blockOffsetX*/ 0, /*blockOffsetZ*/ 0,
                "Prefabs/Test/HorizontalBar.prefab.json");
        org.junit.Assert.assertTrue("paste must succeed", pasted);

        // Blocks at WP X=30..31 land in chunk (0, 0) at local X=30..31.
        assertNotNull("WP X=30 should land in chunk 0", blockAt(chunk0, 30, ANCHOR_Y, 5));
        assertNotNull("WP X=31 should land in chunk 0", blockAt(chunk0, 31, ANCHOR_Y, 5));
        // Blocks at WP X=32..34 land in chunk (1, 0) at local X=0..2 — the cross-chunk
        // continuation that the old per-chunk paste used to silently drop.
        assertNotNull("WP X=32 should land in chunk 1", blockAt(chunk1, 0, ANCHOR_Y, 5));
        assertNotNull("WP X=33 should land in chunk 1", blockAt(chunk1, 1, ANCHOR_Y, 5));
        assertNotNull("WP X=34 should land in chunk 1", blockAt(chunk1, 2, ANCHOR_Y, 5));
    }

    @Test
    public void prefabBlocksOutsideRegionAreDroppedNotErrored() throws Exception {
        // Same prefab but place it anchored near the right edge of chunk 1 — its right
        // half would extend into chunk 2 which isn't in the chunksByCoords map. Those
        // blocks must be silently dropped (the adjacent region writes them).
        File assetsDir = writeFixturePrefab("Prefabs/Test/HorizontalBar.prefab.json",
                /*anchorX*/ 0, /*anchorY*/ 0, /*anchorZ*/ 0,
                new FixtureBlock(0, 0, 0, "Rock_Stone"),
                new FixtureBlock(1, 0, 0, "Rock_Stone"),
                new FixtureBlock(2, 0, 0, "Rock_Stone"),
                new FixtureBlock(3, 0, 0, "Rock_Stone"),
                new FixtureBlock(4, 0, 0, "Rock_Stone"));

        HytalePrefabPaster paster = new HytalePrefabPaster(assetsDir);
        HytaleChunk chunk1 = new HytaleChunk(1, 0, MIN_HEIGHT, MAX_HEIGHT);
        Map<Long, HytaleChunk> chunksByCoords = new HashMap<>();
        chunksByCoords.put(HytalePrefabPaster.chunkKey(1, 0), chunk1);

        boolean pasted = paster.paste(chunksByCoords,
                /*anchorWorldX*/ 62, /*anchorY*/ ANCHOR_Y, /*anchorWorldZ*/ 5,
                /*blockOffsetX*/ 0, /*blockOffsetZ*/ 0,
                "Prefabs/Test/HorizontalBar.prefab.json");
        org.junit.Assert.assertTrue("paste must succeed", pasted);

        // WP X=62..63 land in chunk 1 at local X=30..31.
        assertNotNull("WP X=62 should land in chunk 1", blockAt(chunk1, 30, ANCHOR_Y, 5));
        assertNotNull("WP X=63 should land in chunk 1", blockAt(chunk1, 31, ANCHOR_Y, 5));
        // WP X=64..66 land in chunk 2 which is NOT in the map — must be silently dropped.
        // (No exception, no error; chunk 1 is unaffected outside its own bounds.)
    }

    private static HytaleBlock blockAt(HytaleChunk chunk, int localX, int y, int localZ) {
        HytaleBlock b = chunk.getHytaleBlock(localX, y, localZ);
        if (b == null || b.isEmpty()) {
            return null;
        }
        return b;
    }

    private File writeFixturePrefab(String relativePath, int anchorX, int anchorY, int anchorZ,
                                    FixtureBlock... blocks) throws IOException {
        File assetsDir = tempDir.newFolder("HytaleAssets_" + System.nanoTime());
        File serverDir = new File(assetsDir, "Server");
        File prefabFile = new File(serverDir, relativePath.replace('/', File.separatorChar));
        if (!prefabFile.getParentFile().mkdirs() && !prefabFile.getParentFile().isDirectory()) {
            throw new IOException("Could not create parent dir: " + prefabFile.getParentFile());
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
