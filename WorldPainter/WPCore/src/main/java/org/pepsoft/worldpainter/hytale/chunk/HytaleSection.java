package org.pepsoft.worldpainter.hytale.chunk;

import org.pepsoft.worldpainter.hytale.HytaleBlock;
import org.pepsoft.worldpainter.hytale.HytaleBlockRegistry;

import org.pepsoft.minecraft.Material;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.pepsoft.worldpainter.hytale.chunk.HytaleChunk.CHUNK_SIZE;
import static org.pepsoft.worldpainter.hytale.chunk.HytaleChunk.SECTION_HEIGHT;
import static org.pepsoft.worldpainter.hytale.chunk.HytaleChunk.SUPPORT_NONE;

/**
 * A single 32x32x32 section within a Hytale chunk.
 */
public class HytaleSection {
    // Use palette-based storage like Hytale does
    // For simplicity, we start with a direct material array and can optimize later
    private Material[] blocks;
    private final HytaleBlock[] hytaleBlocks;
    private final byte[] rotations; // Hytale rotation: 0-63 (6 bits: rx*16 + ry*4 + rz)
    private final byte[] fluidIds; // Fluid type: 0=empty, 1=water, 2=lava, etc.
    // Fluid level: 0-15, interpreted relative to fluid MaxFluidLevel (source fluids are typically level 1).
    private final byte[] fluidLevels;
    private final byte[] blockLight;
    private final byte[] skyLight;
    private byte[] supportData;
    private int nonZeroSupportCount;
    private BitSet sealProtectedBlocks;
    
    // Palette for efficient storage
    private final List<Material> palette = new ArrayList<>();
    private final Map<Material, Integer> paletteIndex = new HashMap<>();
    
    // Fluid palette for name-based lookup
    private final List<String> fluidPalette = new ArrayList<>();
    private final Map<String, Integer> fluidPaletteIndex = new HashMap<>();
    
    private static final int SECTION_SIZE = CHUNK_SIZE * SECTION_HEIGHT * CHUNK_SIZE; // 32*32*32 = 32768
    
    public HytaleSection() {
        hytaleBlocks = new HytaleBlock[SECTION_SIZE];
        rotations = new byte[SECTION_SIZE];
        fluidIds = new byte[SECTION_SIZE];
        fluidLevels = new byte[SECTION_SIZE];
        blockLight = new byte[SECTION_SIZE];
        skyLight = new byte[SECTION_SIZE];
        
        // Initialize palette with air
        Material air = Material.AIR;
        palette.add(air);
        paletteIndex.put(air, 0);
        Arrays.fill(skyLight, (byte) 15); // Full sky light by default
        
        // Initialize fluid palette with empty entry
        fluidPalette.add("Empty");
        fluidPaletteIndex.put("Empty", 0);
    }
    
    public Material getMaterial(int x, int y, int z) {
        int index = getIndex(x, y, z);
        if (blocks != null) {
            Material material = blocks[index];
            if (material != null && material != Material.AIR) {
                return material;
            }
        }
        // Fall back to native Hytale block storage so that first-pass
        // exporters (e.g. ResourcesExporter) can see terrain placed
        // via setHytaleBlock() through the standard Chunk interface.
        HytaleBlock block = hytaleBlocks[index];
        if (block != null && !block.isEmpty()) {
            return Material.get(HytaleBlockRegistry.HYTALE_NAMESPACE + ":" + block.id);
        }
        return Material.AIR;
    }

    public HytaleBlock getHytaleBlock(int x, int y, int z) {
        return hytaleBlocks[getIndex(x, y, z)];
    }
    
    public void setMaterial(int x, int y, int z, Material material) {
        if (!paletteIndex.containsKey(material)) {
            paletteIndex.put(material, palette.size());
            palette.add(material);
        }
        if (material == Material.AIR && blocks == null) {
            return;
        }
        if (blocks == null) {
            blocks = new Material[SECTION_SIZE];
        }
        blocks[getIndex(x, y, z)] = material;
    }

    public void resetMaterialView() {
        blocks = null;
        palette.clear();
        paletteIndex.clear();
        palette.add(Material.AIR);
        paletteIndex.put(Material.AIR, 0);
    }

    public void setMaterialForLighting(int x, int y, int z, Material material) {
        if (material == Material.AIR && blocks == null) {
            return;
        }
        if (blocks == null) {
            resetMaterialView();
            blocks = new Material[SECTION_SIZE];
        }
        if (!paletteIndex.containsKey(material)) {
            paletteIndex.put(material, palette.size());
            palette.add(material);
        }
        blocks[getIndex(x, y, z)] = material;
    }

    public void setHytaleBlock(int x, int y, int z, HytaleBlock block) {
        int index = getIndex(x, y, z);
        HytaleBlock effective = (block != null) ? block : HytaleBlock.EMPTY;
        hytaleBlocks[index] = effective;
        rotations[index] = (byte) (effective.rotation & 0x3F);
    }

    public HytaleBlock[] getHytaleBlocks() {
        return hytaleBlocks;
    }

    public boolean hasHytaleBlocks() {
        for (HytaleBlock block : hytaleBlocks) {
            if (block != null && !block.isEmpty()) {
                return true;
            }
        }
        return false;
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
     * Keeps the HytaleBlock in {@code hytaleBlocks} in sync so that
     * {@link #getHytaleBlock(int, int, int)} reflects the new rotation.
     *
     * @param rotation Rotation value 0-63 (rx*16 + ry*4 + rz where each is 0-3)
     */
    public void setRotation(int x, int y, int z, int rotation) {
        int index = getIndex(x, y, z);
        byte rotByte = (byte) (rotation & 0x3F);
        rotations[index] = rotByte;
        HytaleBlock current = hytaleBlocks[index];
        if (current != null && !current.isEmpty() && (current.rotation & 0x3F) != (rotByte & 0x3F)) {
            hytaleBlocks[index] = current.withRotation(rotByte & 0xFF);
        }
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
     * @return Fluid level 0-15, interpreted relative to fluid MaxFluidLevel.
     */
    public int getFluidLevel(int x, int y, int z) {
        return fluidLevels[getIndex(x, y, z)] & 0xF;
    }
    
    /**
     * Set fluid at position by name.
     * @param fluidName Fluid name (e.g., "Water_Source", "Lava_Source")
     * @param level Fluid level 0-15, interpreted relative to fluid MaxFluidLevel
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

    public int getSupportValue(int x, int y, int z) {
        if (supportData == null) {
            return SUPPORT_NONE;
        }
        int index = getIndex(x, y, z);
        int packed = supportData[index >> 1] & 0xFF;
        return ((index & 1) == 0) ? ((packed >> 4) & 0xF) : (packed & 0xF);
    }

    public void setSupportValue(int x, int y, int z, int supportValue) {
        int value = supportValue & 0xF;
        if ((supportData == null) && (value == SUPPORT_NONE)) {
            return;
        }
        if (supportData == null) {
            supportData = new byte[SECTION_SIZE >> 1];
        }
        int index = getIndex(x, y, z);
        int byteIndex = index >> 1;
        int packed = supportData[byteIndex] & 0xFF;
        int oldValue = ((index & 1) == 0) ? ((packed >> 4) & 0xF) : (packed & 0xF);
        if (oldValue == value) {
            return;
        }
        if ((index & 1) == 0) {
            packed = (packed & 0x0F) | (value << 4);
        } else {
            packed = (packed & 0xF0) | value;
        }
        supportData[byteIndex] = (byte) packed;
        if ((oldValue == SUPPORT_NONE) && (value != SUPPORT_NONE)) {
            nonZeroSupportCount++;
        } else if ((oldValue != SUPPORT_NONE) && (value == SUPPORT_NONE)) {
            nonZeroSupportCount--;
        }
    }

    public byte[] getSupportData() {
        return (nonZeroSupportCount > 0) ? supportData : null;
    }

    boolean isSealProtected(int x, int y, int z) {
        return (sealProtectedBlocks != null) && sealProtectedBlocks.get(getIndex(x, y, z));
    }

    void setSealProtected(int x, int y, int z, boolean sealProtected) {
        if ((sealProtectedBlocks == null) && (! sealProtected)) {
            return;
        }
        int index = getIndex(x, y, z);
        if (sealProtected) {
            if (sealProtectedBlocks == null) {
                sealProtectedBlocks = new BitSet(SECTION_SIZE);
            }
            sealProtectedBlocks.set(index);
        } else {
            sealProtectedBlocks.clear(index);
            if (sealProtectedBlocks.isEmpty()) {
                sealProtectedBlocks = null;
            }
        }
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
        if (blocks != null) {
            for (Material block : blocks) {
                if ((block != null) && (block != Material.AIR)) {
                    return false;
                }
            }
            return true;
        }
        for (HytaleBlock block : hytaleBlocks) {
            if (block != null && !block.isEmpty()) {
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
