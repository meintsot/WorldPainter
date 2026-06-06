package org.pepsoft.worldpainter.hytale;

import org.junit.Test;
import static org.junit.Assert.*;

public class HytaleRotationsTest {
    @Test
    public void rotateRawMatchesMaterialFormula() {
        // yaw lives in bits 0-1; transform is (yaw - steps + 4) % 4, roll/pitch unchanged.
        assertEquals(0, HytaleRotations.rotateRaw(1, 1));   // rot=1 (yaw=1), 1 step -> yaw=0
        assertEquals(3, HytaleRotations.rotateRaw(1, 2));   // yaw=1, 2 steps -> yaw=3
        assertEquals(3, HytaleRotations.rotateRaw(0, 1));   // yaw=0, 1 step -> yaw=3
        assertEquals(53, HytaleRotations.rotateRaw(54, 1)); // 0b110110 (roll3,pitch1,yaw2),1 step->yaw1 =>0b110101
        for (int r = 0; r <= 63; r++) {
            assertEquals(r, HytaleRotations.rotateRaw(r, 4)); // four steps == identity
        }
        assertEquals(-5, HytaleRotations.rotateRaw(-5, 1));  // out-of-range passthrough
        assertEquals(99, HytaleRotations.rotateRaw(99, 1));
    }
}
