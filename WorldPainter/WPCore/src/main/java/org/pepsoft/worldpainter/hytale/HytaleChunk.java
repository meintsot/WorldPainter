package org.pepsoft.worldpainter.hytale;

import org.pepsoft.minecraft.Chunk;
import org.pepsoft.minecraft.Entity;
import org.pepsoft.minecraft.Material;
import org.pepsoft.minecraft.MinecraftCoords;
import org.pepsoft.minecraft.TileEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A Hytale chunk implementation. Hytale chunks are 32x32x320 blocks, organized into 10 sections of 32x32x32 blocks each.
 */
public class HytaleChunk implements Chunk {
    
    public static final int CHUNK_SIZE = 32;
    public static final int SECTION_HEIGHT = 32;
    public static final int SECTION_COUNT = 10;
    public static final int MAX_HEIGHT = 320;
    
    private final int x, z;
    private final int minHeight, maxHeight;
    private final HytaleSection[] sections;
    private final short[] heightmap;
    private final List<Entity> entities = new ArrayList<>();
    private final List<TileEntity> tileEntities = new ArrayList<>();
    private boolean terrainPopulated = false;
    private boolean lightPopulated = false;
    private long inhabitedTime = 0;
    
    public HytaleChunk(int x, int z, int minHeight, int maxHeight) {
        this.x = x;
        this.z = z;
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
        this.sections = new HytaleSection[SECTION_COUNT];
        for (int i = 0; i < SECTION_COUNT; i++) {
            sections[i] = new HytaleSection();
        }
        this.heightmap = new short[CHUNK_SIZE * CHUNK_SIZE];
    }
    
    @Override
    public int getBlockLightLevel(int x, int y, int z) {
        if (y < 0 || y >= MAX_HEIGHT) return 0;
        return getSection(y).getBlockLight(x, y & 31, z);
    }
    
    @Override
    public void setBlockLightLevel(int x, int y, int z, int blockLightLevel) {
        if (y < 0 || y >= MAX_HEIGHT) return;
        getSection(y).setBlockLight(x, y & 31, z, blockLightLevel);
    }
    
    @Override
    @Deprecated
    public int getBlockType(int x, int y, int z) {
        Material material = getMaterial(x, y, z);
        return material != null ? material.blockType : 0;
    }
    
    @Override
    @Deprecated
    public void setBlockType(int x, int y, int z, int blockType) {
        // Not directly supported for Hytale
    }
    
    @Override
    @Deprecated
    public int getDataValue(int x, int y, int z) {
        return 0;
    }
    
    @Override
    @Deprecated
    public void setDataValue(int x, int y, int z, int dataValue) {
        // Not directly supported for Hytale
    }
    
    @Override
    public int getHeight(int x, int z) {
        return heightmap[z * CHUNK_SIZE + x];
    }
    
    @Override
    public void setHeight(int x, int z, int height) {
        heightmap[z * CHUNK_SIZE + x] = (short) height;
    }
    
    @Override
    public int getSkyLightLevel(int x, int y, int z) {
        if (y < 0 || y >= MAX_HEIGHT) return 15;
        return getSection(y).getSkyLight(x, y & 31, z);
    }
    
    @Override
    public void setSkyLightLevel(int x, int y, int z, int skyLightLevel) {
        if (y < 0 || y >= MAX_HEIGHT) return;
        getSection(y).setSkyLight(x, y & 31, z, skyLightLevel);
    }
    
    @Override
    public int getxPos() {
        return x;
    }
    
    @Override
    public int getzPos() {
        return z;
    }
    
    @Override
    public MinecraftCoords getCoords() {
        return new MinecraftCoords(x, z);
    }
    
    @Override
    public boolean isTerrainPopulated() {
        return terrainPopulated;
    }
    
    @Override
    public void setTerrainPopulated(boolean terrainPopulated) {
        this.terrainPopulated = terrainPopulated;
    }
    
    @Override
    public Material getMaterial(int x, int y, int z) {
        if (y < 0 || y >= MAX_HEIGHT) return Material.AIR;
        return getSection(y).getMaterial(x, y & 31, z);
    }
    
    @Override
    public void setMaterial(int x, int y, int z, Material material) {
        if (y < 0 || y >= MAX_HEIGHT) return;
        getSection(y).setMaterial(x, y & 31, z, material);
        // Update heightmap
        if (material != Material.AIR && y > getHeight(x, z)) {
            setHeight(x, z, y);
        } else if (material == Material.AIR && y == getHeight(x, z)) {
            // Recalculate height
            int newHeight = 0;
            for (int h = y - 1; h >= 0; h--) {
                if (getMaterial(x, h, z) != Material.AIR) {
                    newHeight = h;
                    break;
                }
            }
            setHeight(x, z, newHeight);
        }
    }
    
    @Override
    public List<Entity> getEntities() {
        return entities;
    }
    
    @Override
    public List<TileEntity> getTileEntities() {
        return tileEntities;
    }
    
    @Override
    public int getMinHeight() {
        return minHeight;
    }
    
    @Override
    public int getMaxHeight() {
        return maxHeight;
    }
    
    @Override
    public boolean isReadOnly() {
        return false;
    }
    
    @Override
    public boolean isLightPopulated() {
        return lightPopulated;
    }
    
    @Override
    public void setLightPopulated(boolean lightPopulated) {
        this.lightPopulated = lightPopulated;
    }
    
    @Override
    public long getInhabitedTime() {
        return inhabitedTime;
    }
    
    @Override
    public void setInhabitedTime(long inhabitedTime) {
        this.inhabitedTime = inhabitedTime;
    }
    
    @Override
    public int getHighestNonAirBlock(int x, int z) {
        for (int y = MAX_HEIGHT - 1; y >= 0; y--) {
            if (getMaterial(x, y, z) != Material.AIR) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }
    
    @Override
    public int getHighestNonAirBlock() {
        int highest = Integer.MIN_VALUE;
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                int h = getHighestNonAirBlock(x, z);
                if (h > highest) {
                    highest = h;
                }
            }
        }
        return highest;
    }
    
    private HytaleSection getSection(int y) {
        return sections[y >> 5]; // divide by 32
    }
    
    /**
     * Get all sections for serialization.
     */
    public HytaleSection[] getSections() {
        return sections;
    }
    
    /**
     * Get the heightmap for serialization.
     */
    public short[] getHeightmap() {
        return heightmap;
    }
    
    /**
     * A single 32x32x32 section within a Hytale chunk.
     */
    public static class HytaleSection {
        // Use palette-based storage like Hytale does
        // For simplicity, we start with a direct material array and can optimize later
        private final Material[] blocks;
        private final byte[] blockLight;
        private final byte[] skyLight;
        
        // Palette for efficient storage
        private final List<Material> palette = new ArrayList<>();
        private final Map<Material, Integer> paletteIndex = new HashMap<>();
        
        private static final int SECTION_SIZE = CHUNK_SIZE * SECTION_HEIGHT * CHUNK_SIZE; // 32*32*32 = 32768
        
        public HytaleSection() {
            blocks = new Material[SECTION_SIZE];
            blockLight = new byte[SECTION_SIZE];
            skyLight = new byte[SECTION_SIZE];
            
            // Initialize with air
            Material air = Material.AIR;
            palette.add(air);
            paletteIndex.put(air, 0);
            for (int i = 0; i < SECTION_SIZE; i++) {
                blocks[i] = air;
                skyLight[i] = 15; // Full sky light by default
            }
        }
        
        public Material getMaterial(int x, int y, int z) {
            return blocks[getIndex(x, y, z)];
        }
        
        public void setMaterial(int x, int y, int z, Material material) {
            if (!paletteIndex.containsKey(material)) {
                paletteIndex.put(material, palette.size());
                palette.add(material);
            }
            blocks[getIndex(x, y, z)] = material;
        }
        
        public int getBlockLight(int x, int y, int z) {
            return blockLight[getIndex(x, y, z)] & 0xF;
        }
        
        public void setBlockLight(int x, int y, int z, int level) {
            blockLight[getIndex(x, y, z)] = (byte) (level & 0xF);
        }
        
        public int getSkyLight(int x, int y, int z) {
            return skyLight[getIndex(x, y, z)] & 0xF;
        }
        
        public void setSkyLight(int x, int y, int z, int level) {
            skyLight[getIndex(x, y, z)] = (byte) (level & 0xF);
        }
        
        /**
         * Get the palette for serialization.
         */
        public List<Material> getPalette() {
            return palette;
        }
        
        /**
         * Get all blocks for serialization.
         */
        public Material[] getBlocks() {
            return blocks;
        }
        
        /**
         * Check if all blocks are air (for optimization).
         */
        public boolean isEmpty() {
            for (Material block : blocks) {
                if (block != Material.AIR) {
                    return false;
                }
            }
            return true;
        }
        
        // Hytale index formula: (y & 31) << 10 | (z & 31) << 5 | (x & 31)
        private int getIndex(int x, int y, int z) {
            return (y & 31) << 10 | (z & 31) << 5 | (x & 31);
        }
    }
}
