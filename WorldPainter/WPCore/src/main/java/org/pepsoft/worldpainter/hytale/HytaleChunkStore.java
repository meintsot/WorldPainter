package org.pepsoft.worldpainter.hytale;

import org.pepsoft.minecraft.Chunk;
import org.pepsoft.minecraft.ChunkStore;
import org.pepsoft.minecraft.MinecraftCoords;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * ChunkStore implementation for reading/writing Hytale world chunks.
 */
public class HytaleChunkStore implements ChunkStore {
    
    private final File chunksDir;
    private final int minHeight;
    private final int maxHeight;
    private final Map<Point, HytaleRegionFile> openRegions = new HashMap<>();
    
    public HytaleChunkStore(File worldDir, int minHeight, int maxHeight) {
        this.chunksDir = new File(worldDir, "chunks");
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
    }
    
    @Override
    public int getChunkCount() {
        int count = 0;
        if (chunksDir.isDirectory()) {
            File[] regionFiles = chunksDir.listFiles((dir, name) -> name.endsWith(".region.bin"));
            if (regionFiles != null) {
                // Each region can have up to 32x32 = 1024 chunks
                // This is an estimate - actual count would require opening each file
                count = regionFiles.length * 1024;
            }
        }
        return count;
    }
    
    @Override
    public Set<MinecraftCoords> getChunkCoords() {
        Set<MinecraftCoords> coords = new HashSet<>();
        if (!chunksDir.isDirectory()) {
            return coords;
        }
        
        File[] regionFiles = chunksDir.listFiles((dir, name) -> name.endsWith(".region.bin"));
        if (regionFiles == null) {
            return coords;
        }
        
        for (File regionFile : regionFiles) {
            String name = regionFile.getName();
            // Parse region coordinates from filename: x.z.region.bin
            String[] parts = name.split("\\.");
            if (parts.length >= 3) {
                try {
                    int regionX = Integer.parseInt(parts[0]);
                    int regionZ = Integer.parseInt(parts[1]);
                    
                    // Add all potential chunk coordinates in this region
                    for (int localZ = 0; localZ < 32; localZ++) {
                        for (int localX = 0; localX < 32; localX++) {
                            int chunkX = (regionX << 5) + localX;
                            int chunkZ = (regionZ << 5) + localZ;
                            coords.add(new MinecraftCoords(chunkX, chunkZ));
                        }
                    }
                } catch (NumberFormatException e) {
                    // Skip invalid filename
                }
            }
        }
        
        return coords;
    }
    
    @Override
    public boolean visitChunks(ChunkVisitor visitor) {
        for (MinecraftCoords coords : getChunkCoords()) {
            Chunk chunk = getChunk(coords.x, coords.z);
            if (chunk != null) {
                try {
                    if (!visitor.visitChunk(chunk)) {
                        return false;
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Error visiting chunk at " + coords.x + "," + coords.z, e);
                }
            }
        }
        return true;
    }
    
    @Override
    public boolean visitChunksForEditing(ChunkVisitor visitor) {
        for (MinecraftCoords coords : getChunkCoords()) {
            Chunk chunk = getChunkForEditing(coords.x, coords.z);
            if (chunk != null) {
                try {
                    if (visitor.visitChunk(chunk)) {
                        saveChunk(chunk);
                    } else {
                        return false;
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Error visiting chunk at " + coords.x + "," + coords.z, e);
                }
            }
        }
        return true;
    }
    
    @Override
    public Chunk getChunk(int x, int z) {
        try {
            HytaleRegionFile region = getRegionFile(x >> 5, z >> 5, false);
            if (region == null) {
                return null;
            }
            return region.readChunk(x & 31, z & 31, minHeight, maxHeight);
        } catch (IOException e) {
            throw new RuntimeException("Error reading chunk at " + x + "," + z, e);
        }
    }
    
    @Override
    public Chunk getChunkForEditing(int x, int z) {
        return getChunk(x, z);
    }
    
    @Override
    public void saveChunk(Chunk chunk) {
        if (!(chunk instanceof HytaleChunk)) {
            throw new IllegalArgumentException("Expected HytaleChunk but got " + chunk.getClass().getName());
        }
        
        HytaleChunk hytaleChunk = (HytaleChunk) chunk;
        int x = chunk.getxPos();
        int z = chunk.getzPos();
        
        try {
            HytaleRegionFile region = getRegionFile(x >> 5, z >> 5, true);
            region.writeChunk(x & 31, z & 31, hytaleChunk);
        } catch (IOException e) {
            throw new RuntimeException("Error saving chunk at " + x + "," + z, e);
        }
    }
    
    @Override
    public void doInTransaction(Runnable task) {
        // Hytale format doesn't have transactions, just execute directly
        task.run();
    }
    
    @Override
    public void flush() {
        for (HytaleRegionFile region : openRegions.values()) {
            try {
                region.flush();
            } catch (IOException e) {
                throw new RuntimeException("Error flushing region file", e);
            }
        }
    }
    
    @Override
    public boolean isChunkPresent(int x, int z) {
        try {
            HytaleRegionFile region = getRegionFile(x >> 5, z >> 5, false);
            if (region == null) {
                return false;
            }
            return region.hasChunk(x & 31, z & 31);
        } catch (IOException e) {
            return false;
        }
    }
    
    @Override
    public void close() {
        for (HytaleRegionFile region : openRegions.values()) {
            try {
                region.close();
            } catch (IOException e) {
                throw new RuntimeException("Error closing region file", e);
            }
        }
        openRegions.clear();
    }
    
    private HytaleRegionFile getRegionFile(int regionX, int regionZ, boolean create) throws IOException {
        Point key = new Point(regionX, regionZ);
        HytaleRegionFile region = openRegions.get(key);
        
        if (region == null) {
            Path regionPath = chunksDir.toPath().resolve(HytaleRegionFile.getRegionFileName(regionX, regionZ));
            
            if (!create && !regionPath.toFile().exists()) {
                return null;
            }
            
            if (!chunksDir.exists()) {
                chunksDir.mkdirs();
            }
            
            region = new HytaleRegionFile(regionPath);
            region.openOrCreate();
            openRegions.put(key, region);
        }
        
        return region;
    }
}
