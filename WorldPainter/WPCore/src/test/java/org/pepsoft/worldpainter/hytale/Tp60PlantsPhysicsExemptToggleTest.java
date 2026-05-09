package org.pepsoft.worldpainter.hytale;

import org.junit.Test;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * Verifies the user-facing "Plants physics-exempt" toggle reaches the BlockPhysics payload
 * exactly when enabled and not when disabled.
 *
 * <p>The chunk-API path is what the toggle ultimately drives: when the export flag is on the
 * exporter calls {@code chunk.setDecorative(...)}; when off, it doesn't. This test exercises
 * that contract directly on a {@link HytaleChunk} and runs it through
 * {@link HytaleBsonChunkSerializer#serializeChunk}, comparing the raw (uncompressed) BSON output.
 *
 * <p>This avoids the on-disk format, which Zstd-compresses 16384 bytes of all-15 nibbles down to
 * a handful of bytes — making file-size comparison too noisy. Comparing the pre-compression BSON
 * is unambiguous: the support array is either present (≈16 KB heavier) or absent.
 */
public class Tp60PlantsPhysicsExemptToggleTest {

    /** Bigger than incidental BSON encoding overhead, smaller than the 16384-byte support array. */
    private static final int EXPECTED_MIN_BSON_DELTA = 8 * 1024;

    @Test
    public void setDecorativeEnlargesSerializedBsonByTheSupportPayload() {
        byte[] withoutDeco = serializeBushChunk(false);
        byte[] withDeco = serializeBushChunk(true);
        int delta = withDeco.length - withoutDeco.length;
        assertNotEquals("Toggle should change the serialized BSON output",
                withoutDeco.length, withDeco.length);
        assertTrue("Toggle on must add the per-section nibble-packed support array (~16 KB) to "
                        + "the BlockPhysics component. without=" + withoutDeco.length
                        + " with=" + withDeco.length + " delta=" + delta,
                delta >= EXPECTED_MIN_BSON_DELTA);
    }

    private static byte[] serializeBushChunk(boolean decorative) {
        // Force registry init so block IDs resolve.
        HytaleBlockRegistry.ensureMaterialsRegistered();
        HytaleChunk chunk = new HytaleChunk(0, 0, 0, 320);
        // Place a Stone substrate at y=0..63 and a Plant_Bush overlay at y=64 across the chunk.
        for (int x = 0; x < HytaleChunk.CHUNK_SIZE; x++) {
            for (int z = 0; z < HytaleChunk.CHUNK_SIZE; z++) {
                for (int y = 1; y <= 63; y++) {
                    chunk.setHytaleBlock(x, y, z, HytaleBlock.STONE);
                }
                chunk.setHytaleBlock(x, 64, z, HytaleBlock.of("Plant_Bush"));
                if (decorative) {
                    chunk.setDecorative(x, 64, z, true);
                }
            }
        }
        return HytaleBsonChunkSerializer.serializeChunk(chunk);
    }
}
