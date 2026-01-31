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
    private final String[] biomes; // Biome name for each column (32x32 = 1024)
    private final String[] environments; // Environment name for each column (32x32 = 1024)
    private final int[] tints; // Tint color (ARGB) for each column (32x32 = 1024)
    private final List<Entity> entities = new ArrayList<>();
    private final List<TileEntity> tileEntities = new ArrayList<>();
    private final List<HytaleEntity> hytaleEntities = new ArrayList<>(); // Native Hytale entities
    private final Map<Integer, BlockHealthData> blockHealthMap = new HashMap<>(); // Damaged blocks only
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
        this.biomes = new String[CHUNK_SIZE * CHUNK_SIZE];
        this.environments = new String[CHUNK_SIZE * CHUNK_SIZE];
        this.tints = new int[CHUNK_SIZE * CHUNK_SIZE];
        // Initialize biomes to "Grassland", environments to "Default", tints to default green (grass tint)
        for (int i = 0; i < biomes.length; i++) {
            biomes[i] = "Grassland";
            environments[i] = "Default";
            tints[i] = 0xFF7CFC00; // LawnGreen default tint (ARGB)
        }
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
    
    /**
     * Gets the list of Hytale-native entities in this chunk.
     *
     * @return The list of Hytale entities. May be empty but never null.
     */
    public List<HytaleEntity> getHytaleEntities() {
        return hytaleEntities;
    }
    
    /**
     * Adds a Hytale-native entity to this chunk.
     *
     * @param entity The entity to add.
     */
    public void addHytaleEntity(HytaleEntity entity) {
        hytaleEntities.add(entity);
    }
    
    /**
     * Removes all Hytale-native entities from this chunk.
     */
    public void clearHytaleEntities() {
        hytaleEntities.clear();
    }
    
    // ----- Block Health -----
    
    /**
     * Set the health of a block. Health is 0.0-1.0 where 1.0 is full health.
     * Only damaged blocks (health < 1.0) are stored.
     * 
     * @param x Block X coordinate (0-31)
     * @param y Block Y coordinate (0-319)
     * @param z Block Z coordinate (0-31)
     * @param health Health value (0.0-1.0)
     */
    public void setBlockHealth(int x, int y, int z, float health) {
        int key = packBlockCoords(x, y, z);
        if (health >= 1.0f) {
            // Full health - remove from map
            blockHealthMap.remove(key);
        } else {
            blockHealthMap.put(key, new BlockHealthData(health));
        }
    }
    
    /**
     * Get the health of a block. Returns 1.0 for undamaged blocks.
     * 
     * @param x Block X coordinate (0-31)
     * @param y Block Y coordinate (0-319)
     * @param z Block Z coordinate (0-31)
     * @return Health value (0.0-1.0)
     */
    public float getBlockHealth(int x, int y, int z) {
        BlockHealthData data = blockHealthMap.get(packBlockCoords(x, y, z));
        return data != null ? data.health : 1.0f;
    }
    
    /**
     * Get the block health map for serialization.
     * Keys are packed coordinates (x | y << 5 | z << 14).
     */
    public Map<Integer, BlockHealthData> getBlockHealthMap() {
        return blockHealthMap;
    }
    
    /**
     * Pack block coordinates into a single integer key.
     */
    private static int packBlockCoords(int x, int y, int z) {
        return (x & 0x1F) | ((y & 0x1FF) << 5) | ((z & 0x1F) << 14);
    }
    
    /**
     * Unpack X coordinate from packed key.
     */
    public static int unpackX(int key) {
        return key & 0x1F;
    }
    
    /**
     * Unpack Y coordinate from packed key.
     */
    public static int unpackY(int key) {
        return (key >> 5) & 0x1FF;
    }
    
    /**
     * Unpack Z coordinate from packed key.
     */
    public static int unpackZ(int key) {
        return (key >> 14) & 0x1F;
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
     * Get the biome at a specific column.
     * @param x Local X coordinate (0-31)
     * @param z Local Z coordinate (0-31)
     * @return The biome name for that column
     */
    public String getBiome(int x, int z) {
        return biomes[z * CHUNK_SIZE + x];
    }
    
    /**
     * Set the biome at a specific column.
     * @param x Local X coordinate (0-31)
     * @param z Local Z coordinate (0-31)
     * @param biome The biome name to set
     */
    public void setBiome(int x, int z, String biome) {
        biomes[z * CHUNK_SIZE + x] = biome != null ? biome : "Grassland";
    }
    
    /**
     * Set the same biome for all columns in the chunk.
     * @param biome The biome name to set
     */
    public void fillBiome(String biome) {
        String b = biome != null ? biome : "Grassland";
        for (int i = 0; i < biomes.length; i++) {
            biomes[i] = b;
        }
    }
    
    /**
     * Get all biomes for serialization.
     * @return Array of 1024 biome names (32x32)
     */
    public String[] getBiomes() {
        return biomes;
    }
    
    /**
     * Get the environment for a specific column.
     * @param x Local x coordinate (0-31)
     * @param z Local z coordinate (0-31)
     * @return The environment name for this column
     */
    public String getEnvironment(int x, int z) {
        if (x < 0 || x >= CHUNK_SIZE || z < 0 || z >= CHUNK_SIZE) {
            return "Default";
        }
        return environments[z * CHUNK_SIZE + x];
    }
    
    /**
     * Set the environment for a specific column.
     * @param x Local x coordinate (0-31)
     * @param z Local z coordinate (0-31)
     * @param environment The environment name (e.g., "Forest", "Desert")
     */
    public void setEnvironment(int x, int z, String environment) {
        if (x < 0 || x >= CHUNK_SIZE || z < 0 || z >= CHUNK_SIZE) {
            return;
        }
        environments[z * CHUNK_SIZE + x] = environment != null ? environment : "Default";
    }
    
    /**
     * Get all environments for serialization.
     * @return Array of 1024 environment names (32x32)
     */
    public String[] getEnvironments() {
        return environments;
    }
    
    /**
     * Get the tint color for a specific column.
     * @param x Local x coordinate (0-31)
     * @param z Local z coordinate (0-31)
     * @return The tint color in ARGB format
     */
    public int getTint(int x, int z) {
        if (x < 0 || x >= CHUNK_SIZE || z < 0 || z >= CHUNK_SIZE) {
            return 0xFF7CFC00;
        }
        return tints[z * CHUNK_SIZE + x];
    }
    
    /**
     * Set the tint color for a specific column.
     * @param x Local x coordinate (0-31)
     * @param z Local z coordinate (0-31)
     * @param tint The tint color in ARGB format
     */
    public void setTint(int x, int z, int tint) {
        if (x < 0 || x >= CHUNK_SIZE || z < 0 || z >= CHUNK_SIZE) {
            return;
        }
        tints[z * CHUNK_SIZE + x] = tint;
    }
    
    /**
     * Get all tints for serialization.
     * @return Array of 1024 tint colors in ARGB format (32x32)
     */
    public int[] getTints() {
        return tints;
    }
    
    // ----- Inner classes -----
    
    /**
     * Data for a damaged block.
     */
    public static class BlockHealthData {
        /** Health value (0.0-1.0, where 1.0 is full health). */
        public final float health;
        /** Game time when block was last damaged (0 for pre-placed terrain). */
        public final long lastDamageTime;
        
        public BlockHealthData(float health) {
            this(health, 0L);
        }
        
        public BlockHealthData(float health, long lastDamageTime) {
            this.health = Math.max(0.0f, Math.min(1.0f, health));
            this.lastDamageTime = lastDamageTime;
        }
    }
    
    /**
     * A single 32x32x32 section within a Hytale chunk.
     */
    public static class HytaleSection {
        // Use palette-based storage like Hytale does
        // For simplicity, we start with a direct material array and can optimize later
        private final Material[] blocks;
        private final byte[] rotations; // Hytale rotation: 0-63 (6 bits: rx*16 + ry*4 + rz)
        private final byte[] fluidIds; // Fluid type: 0=empty, 1=water, 2=lava, etc.
        private final byte[] fluidLevels; // Fluid level: 0-15 (8 = full source)
        private final byte[] blockLight;
        private final byte[] skyLight;
        
        // Palette for efficient storage
        private final List<Material> palette = new ArrayList<>();
        private final Map<Material, Integer> paletteIndex = new HashMap<>();
        
        // Fluid palette for name-based lookup
        private final List<String> fluidPalette = new ArrayList<>();
        private final Map<String, Integer> fluidPaletteIndex = new HashMap<>();
        
        private static final int SECTION_SIZE = CHUNK_SIZE * SECTION_HEIGHT * CHUNK_SIZE; // 32*32*32 = 32768
        
        public HytaleSection() {
            blocks = new Material[SECTION_SIZE];
            rotations = new byte[SECTION_SIZE];
            fluidIds = new byte[SECTION_SIZE];
            fluidLevels = new byte[SECTION_SIZE];
            blockLight = new byte[SECTION_SIZE];
            skyLight = new byte[SECTION_SIZE];
            
            // Initialize with air
            Material air = Material.AIR;
            palette.add(air);
            paletteIndex.put(air, 0);
            for (int i = 0; i < SECTION_SIZE; i++) {
                blocks[i] = air;
                rotations[i] = 0; // No rotation by default
                fluidIds[i] = 0; // No fluid by default
                fluidLevels[i] = 0;
                skyLight[i] = 15; // Full sky light by default
            }
            
            // Initialize fluid palette with empty entry
            fluidPalette.add("Empty");
            fluidPaletteIndex.put("Empty", 0);
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
        
        /**
         * Get rotation at the given position.
         * @return Rotation value 0-63 (rx*16 + ry*4 + rz where each is 0-3)
         */
        public int getRotation(int x, int y, int z) {
            return rotations[getIndex(x, y, z)] & 0x3F;
        }
        
        /**
         * Set rotation at the given position.
         * @param rotation Rotation value 0-63 (rx*16 + ry*4 + rz where each is 0-3)
         */
        public void setRotation(int x, int y, int z, int rotation) {
            rotations[getIndex(x, y, z)] = (byte) (rotation & 0x3F);
        }
        
        /**
         * Get all rotations for serialization.
         */
        public byte[] getRotations() {
            return rotations;
        }
        
        /**
         * Check if any block has a non-zero rotation.
         */
        public boolean hasRotations() {
            for (byte r : rotations) {
                if (r != 0) {
                    return true;
                }
            }
            return false;
        }
        
        /**
         * Get fluid ID at position.
         * @return Fluid palette index (0 = empty)
         */
        public int getFluidId(int x, int y, int z) {
            return fluidIds[getIndex(x, y, z)] & 0xFF;
        }
        
        /**
         * Get fluid level at position.
         * @return Fluid level 0-15 (8 = full source block)
         */
        public int getFluidLevel(int x, int y, int z) {
            return fluidLevels[getIndex(x, y, z)] & 0xF;
        }
        
        /**
         * Set fluid at position by name.
         * @param fluidName Fluid name (e.g., "Water_Source", "Lava_Source")
         * @param level Fluid level 0-15 (8 = full source)
         */
        public void setFluid(int x, int y, int z, String fluidName, int level) {
            int idx = getIndex(x, y, z);
            if (fluidName == null || fluidName.isEmpty() || fluidName.equals("Empty")) {
                fluidIds[idx] = 0;
                fluidLevels[idx] = 0;
            } else {
                if (!fluidPaletteIndex.containsKey(fluidName)) {
                    fluidPaletteIndex.put(fluidName, fluidPalette.size());
                    fluidPalette.add(fluidName);
                }
                fluidIds[idx] = (byte) (fluidPaletteIndex.get(fluidName) & 0xFF);
                fluidLevels[idx] = (byte) (level & 0xF);
            }
        }
        
        /**
         * Clear fluid at position.
         */
        public void clearFluid(int x, int y, int z) {
            int idx = getIndex(x, y, z);
            fluidIds[idx] = 0;
            fluidLevels[idx] = 0;
        }
        
        /**
         * Get fluid palette for serialization.
         */
        public List<String> getFluidPalette() {
            return fluidPalette;
        }
        
        /**
         * Get raw fluid ID array for serialization.
         */
        public byte[] getFluidIds() {
            return fluidIds;
        }
        
        /**
         * Get raw fluid level array for serialization.
         */
        public byte[] getFluidLevels() {
            return fluidLevels;
        }
        
        /**
         * Check if section has any fluids.
         */
        public boolean hasFluids() {
            for (byte id : fluidIds) {
                if (id != 0) {
                    return true;
                }
            }
            return false;
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
