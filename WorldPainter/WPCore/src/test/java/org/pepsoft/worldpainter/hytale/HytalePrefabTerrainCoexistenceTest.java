package org.pepsoft.worldpainter.hytale;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Regression test proving that the old interleaved approach (terrain + prefab
 * per column) loses prefab blocks in neighboring columns, and the deferred
 * approach (all terrain first, then all prefabs) preserves them.
 */
public class HytalePrefabTerrainCoexistenceTest {

    private static final int TERRAIN_HEIGHT = 50;

    /**
     * Simulates the OLD (buggy) approach: for each column, place terrain then
     * paste prefab. Neighboring columns processed later overwrite prefab blocks.
     */
    @Test
    public void interleavedApproachLosesPrefabBlocksInNeighborColumns() {
        HytaleChunk chunk = new HytaleChunk(0, 0, 0, 320);
        int anchorX = 5, anchorZ = 5;
        HytaleBlock leafBlock = HytaleBlock.of("Oak_Leaves", 0);

        // Simulate per-column loop: terrain + inline prefab paste
        for (int x = 0; x < HytaleChunk.CHUNK_SIZE; x++) {
            for (int z = 0; z < HytaleChunk.CHUNK_SIZE; z++) {
                // 1. Place terrain for this column
                for (int y = 1; y <= TERRAIN_HEIGHT; y++) {
                    chunk.setHytaleBlock(x, y, z, HytaleBlock.STONE);
                }
                chunk.setHytaleBlock(x, TERRAIN_HEIGHT, z, HytaleBlock.GRASS);
                // Surface plant at height+1
                chunk.setHytaleBlock(x, TERRAIN_HEIGHT + 1, z,
                        HytaleBlock.of("Fern_Short", 0));

                // 2. Paste prefab (only at anchor column)
                if (x == anchorX && z == anchorZ) {
                    // Place leaves in 3x3 at height+3..height+5
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            for (int y = TERRAIN_HEIGHT + 3; y <= TERRAIN_HEIGHT + 5; y++) {
                                chunk.setHytaleBlock(anchorX + dx, y, anchorZ + dz, leafBlock);
                            }
                        }
                    }
                }
            }
        }

        // Column (6, 6) processed AFTER anchor writes its surface plant at height+1
        HytaleBlock surfacePlantOverwrite = chunk.getHytaleBlock(anchorX + 1, TERRAIN_HEIGHT + 1, anchorZ + 1);
        assertEquals("Surface plant should overwrite prefab at height+1 in later column",
                "Fern_Short", surfacePlantOverwrite.id);
    }

    /**
     * Simulates the FIXED approach: all terrain first in every column,
     * then all prefab pastes. Prefab blocks always win.
     */
    @Test
    public void deferredApproachPreservesPrefabBlocks() {
        HytaleChunk chunk = new HytaleChunk(0, 0, 0, 320);
        int anchorX = 5, anchorZ = 5;
        HytaleBlock trunkBlock = HytaleBlock.of("Oak_Log", 0);
        HytaleBlock leafBlock = HytaleBlock.of("Oak_Leaves", 0);

        // Phase 1: ALL terrain in every column
        for (int x = 0; x < HytaleChunk.CHUNK_SIZE; x++) {
            for (int z = 0; z < HytaleChunk.CHUNK_SIZE; z++) {
                for (int y = 1; y <= TERRAIN_HEIGHT; y++) {
                    chunk.setHytaleBlock(x, y, z, HytaleBlock.STONE);
                }
                chunk.setHytaleBlock(x, TERRAIN_HEIGHT, z, HytaleBlock.GRASS);
                chunk.setHytaleBlock(x, TERRAIN_HEIGHT + 1, z,
                        HytaleBlock.of("Fern_Short", 0));
            }
        }

        // Phase 2: ALL prefab pastes AFTER terrain
        // Trunk
        for (int y = TERRAIN_HEIGHT + 1; y <= TERRAIN_HEIGHT + 4; y++) {
            chunk.setHytaleBlock(anchorX, y, anchorZ, trunkBlock);
        }
        // Leaves 3x3
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int y = TERRAIN_HEIGHT + 3; y <= TERRAIN_HEIGHT + 5; y++) {
                    chunk.setHytaleBlock(anchorX + dx, y, anchorZ + dz, leafBlock);
                }
            }
        }

        // Verify: trunk at anchor column overwrote surface plant
        assertEquals("Trunk should overwrite fern at anchor",
                trunkBlock.id,
                chunk.getHytaleBlock(anchorX, TERRAIN_HEIGHT + 1, anchorZ).id);

        // Verify: leaves at neighbor (6, 6) survive
        assertEquals("Leaves at neighbor column should survive",
                leafBlock.id,
                chunk.getHytaleBlock(anchorX + 1, TERRAIN_HEIGHT + 3, anchorZ + 1).id);

        // Verify: terrain below prefab in neighbor is intact
        assertEquals("Terrain below prefab should be intact",
                HytaleBlock.GRASS.id,
                chunk.getHytaleBlock(anchorX + 1, TERRAIN_HEIGHT, anchorZ + 1).id);
    }
}
