package org.pepsoft.worldpainter.hytale;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a native Hytale block with ID and rotation.
 * 
 * Unlike Minecraft's Material class, this is Hytale-specific and supports:
 * - String-based block IDs (e.g., "Rock_Stone", "Soil_Grass")
 * - Rotation values 0-63 (6 bits: 2 bits each for X, Y, Z axes)
 * - Optional properties map for future extensibility
 * 
 * <p>Rotation encoding: rotation = (rx * 16) + (ry * 4) + rz
 * where rx, ry, rz ∈ {0, 1, 2, 3} representing 0°, 90°, 180°, 270°.
 * 
 * <p>This class is immutable and uses interning for common blocks.
 */
public final class HytaleBlock implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /** Interned block instances for memory efficiency. */
    private static final Map<HytaleBlock, HytaleBlock> INTERN_POOL = new HashMap<>();
    
    /** The block ID string (e.g., "Rock_Stone"). */
    public final String id;
    
    /** The rotation value 0-63. */
    public final byte rotation;
    
    /** Optional properties for future use. */
    private final Map<String, String> properties;
    
    // Common block constants
    public static final HytaleBlock EMPTY = of("Empty");
    public static final HytaleBlock STONE = of("Rock_Stone");
    public static final HytaleBlock DIRT = of("Soil_Dirt");
    public static final HytaleBlock GRASS = of("Soil_Grass");
    public static final HytaleBlock SAND = of("Soil_Sand");
    public static final HytaleBlock GRAVEL = of("Soil_Gravel");
    public static final HytaleBlock BEDROCK = of("Rock_Bedrock");
    public static final HytaleBlock WATER = of("Water_Source");
    public static final HytaleBlock LAVA = of("Lava_Source");
    
    /**
     * Private constructor - use factory methods.
     */
    private HytaleBlock(String id, int rotation, Map<String, String> properties) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("Block ID cannot be null or empty");
        }
        if (rotation < 0 || rotation > 63) {
            throw new IllegalArgumentException("Rotation must be 0-63, got: " + rotation);
        }
        this.id = id.intern();  // Intern for memory efficiency
        this.rotation = (byte) rotation;
        this.properties = properties != null ? Map.copyOf(properties) : null;
    }
    
    /**
     * Create a block with default rotation (0).
     */
    public static HytaleBlock of(String id) {
        return of(id, 0);
    }
    
    /**
     * Create a block with specified rotation.
     */
    public static HytaleBlock of(String id, int rotation) {
        return of(id, rotation, null);
    }
    
    /**
     * Create a block with rotation and properties.
     */
    public static HytaleBlock of(String id, int rotation, Map<String, String> properties) {
        HytaleBlock block = new HytaleBlock(id, rotation, properties);
        // Intern common blocks (no properties, common rotations)
        if (properties == null && rotation == 0) {
            return INTERN_POOL.computeIfAbsent(block, k -> k);
        }
        return block;
    }
    
    /**
     * Create a block with rotation from axis values.
     * 
     * @param id Block ID
     * @param rx X-axis rotation (0-3 for 0°, 90°, 180°, 270°)
     * @param ry Y-axis rotation (0-3)
     * @param rz Z-axis rotation (0-3)
     */
    public static HytaleBlock of(String id, int rx, int ry, int rz) {
        int rotation = (rx & 3) * 16 + (ry & 3) * 4 + (rz & 3);
        return of(id, rotation);
    }
    
    /**
     * Create a copy with different rotation.
     */
    public HytaleBlock withRotation(int newRotation) {
        if (newRotation == this.rotation) {
            return this;
        }
        return of(this.id, newRotation, this.properties);
    }
    
    /**
     * Create a copy with different properties.
     */
    public HytaleBlock withProperties(Map<String, String> newProperties) {
        return of(this.id, this.rotation, newProperties);
    }
    
    /**
     * Get X-axis rotation component (0-3).
     */
    public int getRotationX() {
        return (rotation >> 4) & 3;
    }
    
    /**
     * Get Y-axis rotation component (0-3).
     */
    public int getRotationY() {
        return (rotation >> 2) & 3;
    }
    
    /**
     * Get Z-axis rotation component (0-3).
     */
    public int getRotationZ() {
        return rotation & 3;
    }
    
    /**
     * Check if this is the empty/air block.
     */
    public boolean isEmpty() {
        return "Empty".equals(id);
    }
    
    /**
     * Check if this is a fluid block.
     */
    public boolean isFluid() {
        return id.contains("_Source") || id.equals("Water_Source") || id.equals("Lava_Source");
    }
    
    /**
     * Get property value, or null if not present.
     */
    public String getProperty(String key) {
        return properties != null ? properties.get(key) : null;
    }
    
    /**
     * Get all properties (may be null).
     */
    public Map<String, String> getProperties() {
        return properties;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HytaleBlock that = (HytaleBlock) o;
        return rotation == that.rotation && 
               id.equals(that.id) && 
               Objects.equals(properties, that.properties);
    }
    
    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + rotation;
        if (properties != null) {
            result = 31 * result + properties.hashCode();
        }
        return result;
    }
    
    @Override
    public String toString() {
        if (rotation == 0 && properties == null) {
            return id;
        } else if (properties == null) {
            return id + "[rot=" + rotation + "]";
        } else {
            return id + "[rot=" + rotation + ", props=" + properties + "]";
        }
    }
}
