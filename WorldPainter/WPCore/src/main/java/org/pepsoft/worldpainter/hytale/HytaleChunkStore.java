package org.pepsoft.worldpainter.hytale;

import org.pepsoft.minecraft.Chunk;
import org.pepsoft.minecraft.ChunkStore;
import org.pepsoft.minecraft.MinecraftCoords;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

/**
 * ChunkStore implementation for reading/writing Hytale world chunks.
 */
public class HytaleChunkStore implements ChunkStore {
    
    private static final Logger logger = Logger.getLogger(HytaleChunkStore.class.getName());
    
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
        for (Point regionCoords : getRegionCoords()) {
            try {
                HytaleRegionFile region = getRegionFile(regionCoords.x, regionCoords.y, false);
                if (region == null) {
                    continue;
                }
                for (int localZ = 0; localZ < 32; localZ++) {
                    for (int localX = 0; localX < 32; localX++) {
                        if (region.hasChunk(localX, localZ)) {
                            count++;
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("Error counting chunks in region " + regionCoords.x + "," + regionCoords.y, e);
            }
        }
        return count;
    }
    
    @Override
    public Set<MinecraftCoords> getChunkCoords() {
        Set<MinecraftCoords> coords = new HashSet<>();
        for (Point regionCoords : getRegionCoords()) {
            int regionChunkCount = 0;
            try {
                HytaleRegionFile region = getRegionFile(regionCoords.x, regionCoords.y, false);
                if (region == null) {
                    logger.warning("Region " + regionCoords.x + "," + regionCoords.y + " file not found");
                    continue;
                }
                for (int localZ = 0; localZ < 32; localZ++) {
                    for (int localX = 0; localX < 32; localX++) {
                        if (region.hasChunk(localX, localZ)) {
                            int chunkX = (regionCoords.x << 5) + localX;
                            int chunkZ = (regionCoords.y << 5) + localZ;
                            coords.add(new MinecraftCoords(chunkX, chunkZ));
                            regionChunkCount++;
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("Error enumerating chunks in region " + regionCoords.x + "," + regionCoords.y, e);
            }
            logger.info("Region " + regionCoords.x + "," + regionCoords.y + ": " + regionChunkCount + " chunks found");
        }
        logger.info("Total: " + coords.size() + " chunks across " + getRegionCoords().size() + " regions");
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

    private Set<Point> getRegionCoords() {
        Set<Point> regionCoords = new HashSet<>();
        if (!chunksDir.isDirectory()) {
            return regionCoords;
        }

        File[] regionFiles = chunksDir.listFiles((dir, name) -> name.endsWith(".region.bin"));
        if (regionFiles == null) {
            return regionCoords;
        }

        for (File regionFile : regionFiles) {
            String[] parts = regionFile.getName().split("\\.");
            if (parts.length < 3) {
                continue;
            }
            try {
                regionCoords.add(new Point(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
            } catch (NumberFormatException e) {
                // Skip invalid filename
            }
        }

        return regionCoords;
    }
}
