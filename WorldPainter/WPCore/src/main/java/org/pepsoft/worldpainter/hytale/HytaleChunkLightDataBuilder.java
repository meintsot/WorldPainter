package org.pepsoft.worldpainter.hytale;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

import java.util.BitSet;

/**
 * Builds compressed octree light data for Hytale chunks.
 * 
 * <p>Hytale uses an octree structure for light data compression. Each 32³ section
 * has 8 child nodes that can either hold a uniform light value or be subdivided
 * into 8 more children. This allows efficient storage of large uniform areas
 * while supporting per-block detail where needed.
 * 
 * <p>Light values are stored as 16-bit shorts with the format:
 * <pre>
 * Bits 0-3:   Red channel   (0-15)
 * Bits 4-7:   Green channel (0-15)
 * Bits 8-11:  Blue channel  (0-15)
 * Bits 12-15: Sky channel   (0-15)
 * </pre>
 * 
 * <p>The octree node format is:
 * <pre>
 * [mask: byte]          - Bit i = 1 means child i is subdivided
 * [values: short[8]]    - Either light values (if mask bit=0) or child pointers (if mask bit=1)
 * </pre>
 * 
 * @see HytaleBsonChunkSerializer
 */
public class HytaleChunkLightDataBuilder {
    
    /** Number of children per octree node. */
    private static final int TREE_SIZE = 8;
    
    /** Size of one octree node in bytes (1 mask + 8 shorts). */
    private static final int NODE_SIZE = 17;
    
    /** Maximum octree depth (5 levels: 32 -> 16 -> 8 -> 4 -> 2 -> 1). */
    private static final int MAX_DEPTH = 15; // bits shift at deepest level
    
    /** Full sky light value (sky=15, rgb=0). */
    public static final short FULL_SKYLIGHT = (short) 0xF000;
    
    /** No light value (all channels = 0). */
    public static final short NO_LIGHT = 0;
    
    /** The octree data buffer. */
    private ByteBuf light;
    
    /** Tracks which segments are allocated in the octree. */
    private BitSet allocatedSegments;
    
    /** Change ID for versioning. */
    private final short changeId;
    
    /** Default value for unset blocks. */
    private final short defaultValue;
    
    /**
     * Create a new light data builder.
     * 
     * @param changeId Version/change tracking ID.
     */
    public HytaleChunkLightDataBuilder(short changeId) {
        this(changeId, NO_LIGHT);
    }
    
    /**
     * Create a new light data builder with a default value.
     * 
     * @param changeId Version/change tracking ID.
     * @param defaultValue Default light value for unset blocks.
     */
    public HytaleChunkLightDataBuilder(short changeId, short defaultValue) {
        this.changeId = changeId;
        this.defaultValue = defaultValue;
    }
    
    /**
     * Set sky light at block position within section (0-31 for each axis).
     * 
     * @param x Block X (0-31)
     * @param y Block Y (0-31)
     * @param z Block Z (0-31)
     * @param value Sky light value (0-15)
     */
    public void setSkyLight(int x, int y, int z, int value) {
        int index = indexBlock(x, y, z);
        short current = getLightRaw(index);
        // Clear sky bits and set new value
        current = (short) ((current & 0x0FFF) | ((value & 0xF) << 12));
        setLightRaw(index, current);
    }
    
    /**
     * Set combined RGB + sky light at block position.
     * 
     * @param x Block X (0-31)
     * @param y Block Y (0-31)
     * @param z Block Z (0-31)
     * @param red Red light (0-15)
     * @param green Green light (0-15)
     * @param blue Blue light (0-15)
     * @param sky Sky light (0-15)
     */
    public void setLight(int x, int y, int z, int red, int green, int blue, int sky) {
        int index = indexBlock(x, y, z);
        short value = combineLightValues(red, green, blue, sky);
        setLightRaw(index, value);
    }
    
    /**
     * Get raw light value at block index.
     * 
     * @param index Block index (computed from x,y,z)
     * @return Combined light value
     */
    public short getLightRaw(int index) {
        if (light == null) {
            return defaultValue;
        }
        return getTraverse(light, index, 0, 0);
    }
    
    /**
     * Set raw light value at block index, growing octree as needed.
     * 
     * @param index Block index (computed from x,y,z)
     * @param value Combined light value
     */
    public void setLightRaw(int index, short value) {
        ensureInitialized();
        setTraverse(light, allocatedSegments, index, 0, 0, value);
    }
    
    /**
     * Fill the entire section with a uniform light value.
     * Very efficient - results in a single-node octree.
     * 
     * @param value Light value to fill with
     */
    public void fill(short value) {
        // Reset to single node with uniform value
        if (light != null) {
            light.release();
        }
        light = ByteBufAllocator.DEFAULT.buffer(NODE_SIZE);
        allocatedSegments = new BitSet();
        allocatedSegments.set(0);
        
        // Write root node with uniform value (no subdivisions)
        light.writeByte(0); // mask = 0, no children subdivided
        for (int i = 0; i < TREE_SIZE; i++) {
            light.writeShort(value);
        }
    }
    
    /**
     * Serialize the light data to BSON format.
     * 
     * @param buf Output buffer
     */
    public void serialize(ByteBuf buf) {
        buf.writeShort(changeId);
        
        boolean hasLight = light != null && allocatedSegments != null && hasNonDefaultLight();
        buf.writeBoolean(hasLight);
        
        if (hasLight) {
            // Compact the octree (remove unused segments, collapse uniform subtrees)
            ByteBuf compacted = compactOctree();
            buf.writeInt(compacted.readableBytes());
            buf.writeBytes(compacted);
            compacted.release();
        }
    }
    
    /**
     * Check if any blocks have non-default light values.
     */
    private boolean hasNonDefaultLight() {
        if (light == null) {
            return false;
        }
        // Check root node - if uniform and equals default, no light data needed
        byte mask = light.getByte(0);
        if (mask == 0) {
            // Check if all children are default
            for (int i = 0; i < TREE_SIZE; i++) {
                if (light.getShort(1 + i * 2) != defaultValue) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }
    
    /**
     * Initialize the octree buffer if not already done.
     */
    private void ensureInitialized() {
        if (light == null) {
            light = ByteBufAllocator.DEFAULT.buffer(NODE_SIZE * 64); // Start with reasonable capacity
            allocatedSegments = new BitSet();
            allocatedSegments.set(0);
            
            // Initialize root node with default value
            light.writeByte(0); // mask = 0
            for (int i = 0; i < TREE_SIZE; i++) {
                light.writeShort(defaultValue);
            }
        }
    }
    
    /**
     * Traverse octree to get light value at index.
     */
    private short getTraverse(ByteBuf local, int index, int pointer, int depth) {
        byte mask = local.getByte(pointer);
        int childIndex = (index >> (12 - depth)) & 7;
        int childOffset = pointer + 1 + childIndex * 2;
        
        if ((mask & (1 << childIndex)) == 0) {
            // Leaf node - return value directly
            return local.getShort(childOffset);
        } else {
            // Subdivided - follow pointer to child node
            int childPointer = local.getShort(childOffset) & 0xFFFF;
            return getTraverse(local, index, childPointer * NODE_SIZE, depth + 3);
        }
    }
    
    /**
     * Traverse octree to set light value at index, subdividing as needed.
     */
    private void setTraverse(ByteBuf local, BitSet segments, int index, int pointer, int depth, short value) {
        byte mask = local.getByte(pointer);
        int childIndex = (index >> (12 - depth)) & 7;
        int childOffset = pointer + 1 + childIndex * 2;
        
        if (depth >= 12) {
            // At maximum depth - just set the value
            local.setShort(childOffset, value);
            return;
        }
        
        if ((mask & (1 << childIndex)) == 0) {
            // Currently a leaf - need to check if we need to subdivide
            short existingValue = local.getShort(childOffset);
            if (existingValue == value) {
                // Same value, nothing to do
                return;
            }
            
            if (depth >= 12) {
                // At max depth, just update
                local.setShort(childOffset, value);
                return;
            }
            
            // Need to subdivide this node
            int newSegment = growSegment(local, segments, existingValue);
            
            // Update mask to mark this child as subdivided
            mask |= (1 << childIndex);
            local.setByte(pointer, mask);
            
            // Store pointer to new segment
            local.setShort(childOffset, (short) newSegment);
            
            // Continue traversal into new segment
            setTraverse(local, segments, index, newSegment * NODE_SIZE, depth + 3, value);
        } else {
            // Already subdivided - continue traversal
            int childPointer = local.getShort(childOffset) & 0xFFFF;
            setTraverse(local, segments, index, childPointer * NODE_SIZE, depth + 3, value);
        }
    }
    
    /**
     * Allocate a new segment in the octree, initialized with uniform value.
     */
    private int growSegment(ByteBuf local, BitSet segments, short fillValue) {
        int segment = segments.nextClearBit(0);
        segments.set(segment);
        
        int pointer = segment * NODE_SIZE;
        
        // Ensure buffer capacity
        while (local.capacity() <= pointer + NODE_SIZE) {
            local.capacity(local.capacity() * 2);
        }
        
        // Write at the correct position
        local.setByte(pointer, 0); // mask = 0 (no subdivisions)
        for (int i = 0; i < TREE_SIZE; i++) {
            local.setShort(pointer + 1 + i * 2, fillValue);
        }
        
        // Update writer index if needed
        if (local.writerIndex() < pointer + NODE_SIZE) {
            local.writerIndex(pointer + NODE_SIZE);
        }
        
        return segment;
    }
    
    /**
     * Compact the octree by removing unused segments and collapsing uniform subtrees.
     */
    private ByteBuf compactOctree() {
        if (light == null) {
            // Return minimal single-node tree with default value
            ByteBuf result = ByteBufAllocator.DEFAULT.buffer(NODE_SIZE);
            result.writeByte(0);
            for (int i = 0; i < TREE_SIZE; i++) {
                result.writeShort(defaultValue);
            }
            return result;
        }
        
        // For now, just copy used segments in order
        // A more sophisticated approach would reindex and collapse uniform subtrees
        ByteBuf result = ByteBufAllocator.DEFAULT.buffer(allocatedSegments.cardinality() * NODE_SIZE);
        
        // Simple copy - just write all allocated segments
        // TODO: Implement proper compaction with subtree collapsing
        int[] segmentMap = new int[allocatedSegments.length()];
        int newIndex = 0;
        
        for (int seg = allocatedSegments.nextSetBit(0); seg >= 0; seg = allocatedSegments.nextSetBit(seg + 1)) {
            segmentMap[seg] = newIndex++;
        }
        
        // Copy segments with updated pointers
        for (int seg = allocatedSegments.nextSetBit(0); seg >= 0; seg = allocatedSegments.nextSetBit(seg + 1)) {
            int srcPointer = seg * NODE_SIZE;
            byte mask = light.getByte(srcPointer);
            result.writeByte(mask);
            
            for (int i = 0; i < TREE_SIZE; i++) {
                short value = light.getShort(srcPointer + 1 + i * 2);
                if ((mask & (1 << i)) != 0) {
                    // This is a pointer - remap it
                    int oldSegment = value & 0xFFFF;
                    if (oldSegment < segmentMap.length) {
                        value = (short) segmentMap[oldSegment];
                    }
                }
                result.writeShort(value);
            }
        }
        
        return result;
    }
    
    /**
     * Release resources.
     */
    public void release() {
        if (light != null) {
            light.release();
            light = null;
        }
        allocatedSegments = null;
    }
    
    /**
     * Calculate block index from coordinates.
     * 
     * @param x Block X (0-31)
     * @param y Block Y (0-31)
     * @param z Block Z (0-31)
     * @return Block index for octree lookup
     */
    public static int indexBlock(int x, int y, int z) {
        return ((y & 31) << 10) | ((z & 31) << 5) | (x & 31);
    }
    
    /**
     * Combine individual light channels into a single value.
     * 
     * @param red Red light (0-15)
     * @param green Green light (0-15)
     * @param blue Blue light (0-15)
     * @param sky Sky light (0-15)
     * @return Combined 16-bit light value
     */
    public static short combineLightValues(int red, int green, int blue, int sky) {
        return (short) (((sky & 0xF) << 12) | ((blue & 0xF) << 8) | ((green & 0xF) << 4) | (red & 0xF));
    }
    
    /**
     * Extract sky light from combined value.
     */
    public static int getSkyLight(short value) {
        return (value >> 12) & 0xF;
    }
    
    /**
     * Extract red light from combined value.
     */
    public static int getRedLight(short value) {
        return value & 0xF;
    }
    
    /**
     * Extract green light from combined value.
     */
    public static int getGreenLight(short value) {
        return (value >> 4) & 0xF;
    }
    
    /**
     * Extract blue light from combined value.
     */
    public static int getBlueLight(short value) {
        return (value >> 8) & 0xF;
    }
}
