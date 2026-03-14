package org.pepsoft.worldpainter.hytale;

import org.junit.Test;

import java.io.File;
import java.nio.file.Path;

/**
 * Diagnostic test that reads real Hytale region files and dumps what it finds.
 * Run this test to debug import issues.
 */
public class HytaleImportDiagnosticTest {

    private static final String WORLD_DIR = "C:\\Users\\Sotirios\\Desktop\\WorldPainter\\New World";

    @Test
    public void diagnoseSingleChunk() throws Exception {
        File chunksDir = new File(WORLD_DIR, "universe/worlds/default/chunks");
        if (!chunksDir.isDirectory()) {
            System.out.println("Chunks directory not found: " + chunksDir);
            return;
        }

        // Read the 0.0 region file and look at the first available chunk
        Path regionPath = chunksDir.toPath().resolve("0.0.region.bin");
        if (!regionPath.toFile().exists()) {
            System.out.println("Region file not found: " + regionPath);
            return;
        }

        HytaleRegionFile region = new HytaleRegionFile(regionPath);
        region.open();

        int chunksFound = 0;
        int chunksWithBlocks = 0;
        int totalNonAir = 0;

        for (int lz = 0; lz < 32; lz++) {
            for (int lx = 0; lx < 32; lx++) {
                if (!region.hasChunk(lx, lz)) continue;
                chunksFound++;

                HytaleChunk chunk = region.readChunk(lx, lz, 0, HytaleChunk.DEFAULT_MAX_HEIGHT);
                if (chunk == null) {
                    System.out.println("Chunk " + lx + "," + lz + " returned null!");
                    continue;
                }

                int nonAir = 0;
                int minHeight = Integer.MAX_VALUE, maxHeight = Integer.MIN_VALUE;
                for (int x = 0; x < 32; x++) {
                    for (int z = 0; z < 32; z++) {
                        int h = chunk.getHeight(x, z);
                        if (h < minHeight) minHeight = h;
                        if (h > maxHeight) maxHeight = h;
                    }
                }

                for (int sy = 0; sy < chunk.getSectionCount(); sy++) {
                    HytaleChunk.HytaleSection sec = chunk.getSections()[sy];
                    for (HytaleBlock b : sec.getHytaleBlocks()) {
                        if (b != null && !b.isEmpty()) nonAir++;
                    }
                }

                if (nonAir > 0) chunksWithBlocks++;
                totalNonAir += nonAir;

                if (chunksFound <= 5 || nonAir == 0) {
                    System.out.printf("Chunk local(%d,%d): heightmap=[%d..%d], nonAirBlocks=%d%n",
                            lx, lz, minHeight, maxHeight, nonAir);

                    // Sample a few blocks at the surface
                    if (maxHeight > 0) {
                        for (int sx = 0; sx < 32; sx += 8) {
                            int h = chunk.getHeight(sx, 0);
                            HytaleBlock block = chunk.getHytaleBlock(sx, h, 0);
                            String blockId = (block != null) ? block.id : "null";
                            System.out.printf("  Column (%d,0) height=%d block='%s'%n", sx, h, blockId);
                            // Check a few blocks below
                            for (int dy = 0; dy >= -3 && (h + dy) >= 0; dy--) {
                                HytaleBlock below = chunk.getHytaleBlock(sx, h + dy, 0);
                                String belowId = (below != null) ? below.id : "null";
                                System.out.printf("    y=%d block='%s'%n", h + dy, belowId);
                            }
                        }
                    }
                }
            }
        }

        System.out.printf("%nSummary: %d chunks found, %d with blocks, %d total non-air blocks%n",
                chunksFound, chunksWithBlocks, totalNonAir);

        region.close();
    }
}

