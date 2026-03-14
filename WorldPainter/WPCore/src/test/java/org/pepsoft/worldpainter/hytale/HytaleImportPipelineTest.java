package org.pepsoft.worldpainter.hytale;

import org.junit.Test;
import org.pepsoft.minecraft.ChunkStore;
import org.pepsoft.minecraft.Chunk;
import org.pepsoft.minecraft.MinecraftCoords;

import java.io.File;
import java.util.*;

/**
 * Comprehensive diagnostic test that traces the EXACT same code path as the importer.
 * Uses HytaleChunkStore (not HytaleRegionFile directly) to find the root cause
 * of missing tiles during import.
 * 
 * Run this from the IDE to see diagnostic output.
 */
public class HytaleImportPipelineTest {

    private static final String WORLD_DIR = "C:\\Users\\Sotirios\\Desktop\\WorldPainter\\New World\\universe\\worlds\\default";

    @Test
    public void traceFullImportPipeline() throws Exception {
        File worldDir = new File(WORLD_DIR);
        File chunksDir = new File(worldDir, "chunks");
        if (!chunksDir.isDirectory()) {
            System.out.println("Chunks directory not found: " + chunksDir);
            return;
        }

        System.out.println("=== Hytale Import Pipeline Diagnostic ===");
        System.out.println("World dir: " + worldDir);
        System.out.println("Chunks dir: " + chunksDir);

        // List region files
        File[] regionFiles = chunksDir.listFiles((dir, name) -> name.endsWith(".region.bin"));
        System.out.println("\nRegion files found: " + (regionFiles != null ? regionFiles.length : 0));
        if (regionFiles != null) {
            for (File f : regionFiles) {
                System.out.println("  " + f.getName() + " (" + f.length() + " bytes)");
            }
        }

        // Phase 1: Enumerate chunks via HytaleChunkStore (same as importer)
        System.out.println("\n=== Phase 1: Chunk Enumeration (via HytaleChunkStore) ===");
        int totalEnumerated;
        Set<MinecraftCoords> allCoords;
        try (ChunkStore chunkStore = new HytaleChunkStore(worldDir, 0, HytaleChunk.DEFAULT_MAX_HEIGHT)) {
            allCoords = chunkStore.getChunkCoords();
            totalEnumerated = allCoords.size();
            System.out.println("Total chunks enumerated: " + totalEnumerated);

            // Group by region for summary
            Map<String, Integer> regionCounts = new TreeMap<>();
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
            for (MinecraftCoords c : allCoords) {
                String regionKey = (c.x >> 5) + "," + (c.z >> 5);
                regionCounts.merge(regionKey, 1, Integer::sum);
                if (c.x < minX) minX = c.x;
                if (c.x > maxX) maxX = c.x;
                if (c.z < minZ) minZ = c.z;
                if (c.z > maxZ) maxZ = c.z;
            }
            System.out.println("Chunk coordinate range: X=[" + minX + ".." + maxX + "] Z=[" + minZ + ".." + maxZ + "]");
            System.out.println("Per-region chunk counts:");
            for (Map.Entry<String, Integer> entry : regionCounts.entrySet()) {
                System.out.println("  Region " + entry.getKey() + ": " + entry.getValue() + " chunks");
            }
        }

        // Phase 2: Read every chunk via HytaleChunkStore.getChunk() (same as importer)
        System.out.println("\n=== Phase 2: Chunk Reading (via HytaleChunkStore.getChunk) ===");
        int nullCount = 0;
        int successCount = 0;
        int exceptionCount = 0;
        int emptyHeightmapCount = 0;
        int noBlocksCount = 0;
        Map<String, Integer> nullPerRegion = new TreeMap<>();
        Map<String, Integer> successPerRegion = new TreeMap<>();

        try (ChunkStore chunkStore = new HytaleChunkStore(worldDir, 0, HytaleChunk.DEFAULT_MAX_HEIGHT)) {
            // Re-enumerate to ensure same state
            allCoords = chunkStore.getChunkCoords();

            for (MinecraftCoords coords : allCoords) {
                String regionKey = (coords.x >> 5) + "," + (coords.z >> 5);

                Chunk chunk;
                try {
                    chunk = chunkStore.getChunk(coords.x, coords.z);
                } catch (Exception e) {
                    exceptionCount++;
                    if (exceptionCount <= 10) {
                        System.out.println("EXCEPTION reading chunk " + coords.x + "," + coords.z + ": " + e.getMessage());
                    }
                    nullPerRegion.merge(regionKey, 1, Integer::sum);
                    continue;
                }

                if (chunk == null) {
                    nullCount++;
                    if (nullCount <= 10) {
                        System.out.println("NULL chunk at " + coords.x + "," + coords.z);
                    }
                    nullPerRegion.merge(regionKey, 1, Integer::sum);
                    continue;
                }

                successCount++;
                successPerRegion.merge(regionKey, 1, Integer::sum);

                // Check heightmap
                HytaleChunk hc = (HytaleChunk) chunk;
                int maxH = 0;
                for (int lx = 0; lx < 32; lx++) {
                    for (int lz = 0; lz < 32; lz++) {
                        int h = hc.getHeight(lx, lz);
                        if (h > maxH) maxH = h;
                    }
                }
                if (maxH == 0) {
                    emptyHeightmapCount++;
                }

                // Check if any blocks exist
                boolean hasBlocks = false;
                for (int sy = 0; sy < hc.getSectionCount(); sy++) {
                    HytaleChunk.HytaleSection sec = hc.getSections()[sy];
                    if (sec != null) {
                        for (HytaleBlock b : sec.getHytaleBlocks()) {
                            if (b != null && !b.isEmpty()) {
                                hasBlocks = true;
                                break;
                            }
                        }
                    }
                    if (hasBlocks) break;
                }
                if (!hasBlocks) {
                    noBlocksCount++;
                }

                // Print first few chunks' details
                if (successCount <= 3) {
                    System.out.printf("Chunk(%d,%d): maxHeight=%d, hasBlocks=%b%n",
                        coords.x, coords.z, maxH, hasBlocks);
                }
            }
        }

        // Phase 3: Tile mapping summary
        System.out.println("\n=== Phase 3: Tile Mapping ===");
        Set<String> tileCoords = new TreeSet<>();
        for (MinecraftCoords c : allCoords) {
            int tileX = c.x >> 2; // 4 chunks per tile side
            int tileZ = c.z >> 2;
            tileCoords.add(tileX + "," + tileZ);
        }
        System.out.println("Expected tiles (from chunk coords): " + tileCoords.size());

        // Summary
        System.out.println("\n=== Summary ===");
        System.out.println("Enumerated:         " + totalEnumerated);
        System.out.println("Successfully read:  " + successCount);
        System.out.println("Returned null:      " + nullCount);
        System.out.println("Threw exception:    " + exceptionCount);
        System.out.println("Empty heightmap:    " + emptyHeightmapCount + " (of " + successCount + " success)");
        System.out.println("No blocks at all:   " + noBlocksCount + " (of " + successCount + " success)");
        System.out.println("Expected tiles:     " + tileCoords.size());

        System.out.println("\nPer-region success counts:");
        for (Map.Entry<String, Integer> entry : successPerRegion.entrySet()) {
            System.out.println("  Region " + entry.getKey() + ": " + entry.getValue() + " success");
        }

        if (!nullPerRegion.isEmpty()) {
            System.out.println("\nPer-region null/exception counts:");
            for (Map.Entry<String, Integer> entry : nullPerRegion.entrySet()) {
                System.out.println("  Region " + entry.getKey() + ": " + entry.getValue() + " null/exception");
            }
        }

        // CRITICAL: If null count matches the observed gap pattern, we've found the issue
        if (nullCount + exceptionCount > 0) {
            System.out.println("\n*** ISSUE FOUND: " + (nullCount + exceptionCount) + " chunks failed out of " + totalEnumerated + " ***");
            System.out.println("This likely explains the missing tiles in the import!");
        } else if (emptyHeightmapCount > 0) {
            System.out.println("\n*** ISSUE FOUND: " + emptyHeightmapCount + " chunks have empty heightmaps ***");
            System.out.println("These chunks may produce flat/invisible tiles!");
        } else {
            System.out.println("\n*** All chunks read successfully with data. Issue may be in tile creation/rendering. ***");
        }
    }
}

