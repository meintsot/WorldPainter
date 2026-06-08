package org.pepsoft.worldpainter.hytale;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.pepsoft.minecraft.MinecraftCoords;
import org.pepsoft.worldpainter.hytale.chunk.HytaleChunk;
import org.pepsoft.worldpainter.hytale.chunk.HytaleChunkStore;

import java.io.File;

import static org.junit.Assert.*;

/**
 * Verifies that the optional {@code rotation} field on {@link HytaleChunk.PrefabMarker}
 * survives a BSON round-trip and that the legacy 5-arg overload defaults to 0.0.
 */
public class PrefabMarkerRotationBsonTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    /**
     * Serialize a chunk with a 6-arg prefab marker (rotation = 137.5) via
     * {@link HytaleChunkStore}, read it back, and assert the rotation is preserved.
     */
    @Test
    public void rotationSurvivesBsonRoundTrip() throws Exception {
        File worldDir = tempDir.newFolder("rotation_round_trip");

        HytaleChunk chunk = new HytaleChunk(0, 0, 0, 320);
        chunk.addPrefabMarker(1, 64, 2, "Oak", "Prefabs/Trees/Oak.prefab.json", 137.5);

        HytaleChunkStore writer = new HytaleChunkStore(worldDir, 0, 320);
        writer.saveChunk(chunk);
        writer.flush();
        writer.close();

        HytaleChunkStore reader = new HytaleChunkStore(worldDir, 0, 320);
        try {
            HytaleChunk back = (HytaleChunk) reader.getChunk(0, 0);
            assertNotNull("Deserialized chunk must not be null", back);

            assertEquals("Exactly one prefab marker must survive the round-trip",
                    1, back.getPrefabMarkers().size());

            HytaleChunk.PrefabMarker m = back.getPrefabMarkers().get(0);
            assertEquals("prefabPath must survive round-trip",
                    "Prefabs/Trees/Oak.prefab.json", m.prefabPath);
            assertEquals("rotation must survive BSON round-trip",
                    137.5, m.rotation, 1.0e-9);
        } finally {
            reader.close();
        }
    }

    /**
     * The legacy 5-arg {@code addPrefabMarker} must default {@code rotation} to 0.0
     * without any BSON involvement.
     */
    @Test
    public void legacyMarkerWithoutRotationDefaultsToZero() {
        HytaleChunk chunk = new HytaleChunk(0, 0, 0, 320);
        chunk.addPrefabMarker(0, 0, 0, "X", "p.prefab.json"); // 5-arg, no rotation
        assertEquals("Legacy 5-arg addPrefabMarker must default rotation to 0.0",
                0.0, chunk.getPrefabMarkers().get(0).rotation, 1.0e-9);
    }
}
