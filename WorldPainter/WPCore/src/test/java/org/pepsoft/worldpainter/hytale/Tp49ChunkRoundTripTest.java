package org.pepsoft.worldpainter.hytale;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Unit-level regression for TP-49: exporting and re-reading a single chunk preserves the
 * rotation byte on the {@link HytaleBlock} returned by {@link HytaleChunk#getHytaleBlock(int, int, int)}.
 *
 * <p>Before the fix in {@code HytaleChunk.HytaleSection.setRotation(...)}, the deserializer
 * populated {@code hytaleBlocks[]} with default-rotation blocks first (via
 * {@link HytaleBlock#of(String)}), then read the rotation section into a parallel
 * {@code rotations[]} array. {@code getHytaleBlock} returned the original block, so the
 * rotation byte was effectively dropped on read. This test would have failed before the fix.
 */
public class Tp49ChunkRoundTripTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Test
    public void hytaleBlockRotationSurvivesBsonRoundTrip() throws Exception {
        File worldDir = tempDir.newFolder("tp49-roundtrip");
        Path region = new File(worldDir, "0.0.region.bin").toPath();

        HytaleChunk chunk = new HytaleChunk(0, 0, 0, HytaleChunk.DEFAULT_MAX_HEIGHT);
        chunk.setHytaleBlock(5, 64, 7, HytaleBlock.of("Wood_Oak_Branch_Long", 4));    // Z-axis pipe
        chunk.setHytaleBlock(6, 64, 7, HytaleBlock.of("Wood_Oak_Branch_Long", 5));    // X-axis pipe
        chunk.setHytaleBlock(5, 65, 7, HytaleBlock.of("Wood_Oak_Branch_Corner", 9));  // pitch=2, yaw=1
        chunk.setHytaleBlock(6, 65, 7, HytaleBlock.of("Wood_Oak_Branch_Long", 0));    // vertical Y-axis (default)

        HytaleRegionFile writer = new HytaleRegionFile(region);
        try {
            writer.create();
            writer.writeChunk(0, 0, chunk);
            writer.flush();
        } finally {
            writer.close();
        }

        HytaleRegionFile reader = new HytaleRegionFile(region);
        HytaleChunk readBack;
        try {
            reader.open();
            readBack = reader.readChunk(0, 0, 0, HytaleChunk.DEFAULT_MAX_HEIGHT);
        } finally {
            reader.close();
        }
        assertNotNull(readBack);

        // Each rotation must come back unchanged on the HytaleBlock object — both the parallel
        // rotations[] array AND the block instance itself, since callers consume getHytaleBlock.
        assertEquals(4, readBack.getHytaleBlock(5, 64, 7).rotation & 0x3F);
        assertEquals(5, readBack.getHytaleBlock(6, 64, 7).rotation & 0x3F);
        assertEquals(9, readBack.getHytaleBlock(5, 65, 7).rotation & 0x3F);
        assertEquals(0, readBack.getHytaleBlock(6, 65, 7).rotation & 0x3F);
    }
}
