package org.pepsoft.worldpainter.hytale;

import org.bson.BsonArray;
import org.bson.BsonBinary;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonString;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Base class for Hytale entities that can be placed in chunks.
 * 
 * <p>Hytale entities use an ECS (Entity Component System) architecture where each
 * entity is a holder containing multiple components. Common components include:
 * <ul>
 *   <li>TransformComponent - Position and rotation</li>
 *   <li>UUIDComponent - Unique identifier</li>
 *   <li>Entity-specific components (SpawnMarker, NPC, etc.)</li>
 * </ul>
 * 
 * <p>This class provides the base serialization to BSON format matching Hytale's
 * EntityStore registry patterns.
 * 
 * @see HytaleSpawnMarker
 */
public class HytaleEntity implements Serializable, Cloneable {
    
    private static final long serialVersionUID = 1L;
    
    /** Entity type identifier (e.g., "SpawnMarker", "NPC"). */
    protected String entityType;
    
    /** World position (x, y, z). */
    protected double x, y, z;
    
    /** Rotation angles in degrees (yaw, pitch, roll). */
    protected float yaw, pitch, roll;
    
    /** Unique entity identifier. */
    protected UUID uuid;
    
    /** Additional custom properties. */
    protected Map<String, Object> properties;
    
    /**
     * Create a new entity.
     * 
     * @param entityType The entity type identifier
     * @param x World X coordinate
     * @param y World Y coordinate
     * @param z World Z coordinate
     */
    public HytaleEntity(String entityType, double x, double y, double z) {
        this.entityType = entityType;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = 0;
        this.pitch = 0;
        this.roll = 0;
        this.uuid = UUID.randomUUID();
        this.properties = new HashMap<>();
    }
    
    /**
     * Create a new entity with rotation.
     * 
     * @param entityType The entity type identifier
     * @param x World X coordinate
     * @param y World Y coordinate
     * @param z World Z coordinate
     * @param yaw Yaw rotation in degrees
     * @param pitch Pitch rotation in degrees
     * @param roll Roll rotation in degrees
     */
    public HytaleEntity(String entityType, double x, double y, double z, 
                        float yaw, float pitch, float roll) {
        this(entityType, x, y, z);
        this.yaw = yaw;
        this.pitch = pitch;
        this.roll = roll;
    }
    
    // ----- Getters and setters -----
    
    public String getEntityType() {
        return entityType;
    }
    
    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }
    
    public double getX() {
        return x;
    }
    
    public void setX(double x) {
        this.x = x;
    }
    
    public double getY() {
        return y;
    }
    
    public void setY(double y) {
        this.y = y;
    }
    
    public double getZ() {
        return z;
    }
    
    public void setZ(double z) {
        this.z = z;
    }
    
    public void setPosition(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
    
    public float getYaw() {
        return yaw;
    }
    
    public void setYaw(float yaw) {
        this.yaw = yaw;
    }
    
    public float getPitch() {
        return pitch;
    }
    
    public void setPitch(float pitch) {
        this.pitch = pitch;
    }
    
    public float getRoll() {
        return roll;
    }
    
    public void setRoll(float roll) {
        this.roll = roll;
    }
    
    public void setRotation(float yaw, float pitch, float roll) {
        this.yaw = yaw;
        this.pitch = pitch;
        this.roll = roll;
    }
    
    public UUID getUuid() {
        return uuid;
    }
    
    public void setUuid(UUID uuid) {
        this.uuid = uuid != null ? uuid : UUID.randomUUID();
    }
    
    public Map<String, Object> getProperties() {
        return properties;
    }
    
    public void setProperty(String key, Object value) {
        properties.put(key, value);
    }
    
    public Object getProperty(String key) {
        return properties.get(key);
    }
    
    // ----- BSON Serialization -----
    
    /**
     * Serialize this entity to a BSON document for the EntityChunk component.
     * 
     * <p>The format matches Hytale's EntityStore.REGISTRY serialization:
     * <pre>
     * {
     *   "EntityType": "...",
     *   "Transform": { "Position": {...}, "Rotation": {...} },
     *   "UUID": { "UUID": binary },
     *   ... entity-specific components ...
     * }
     * </pre>
     * 
     * @return BSON document representing this entity
     */
    public BsonDocument toBson() {
        BsonDocument doc = new BsonDocument();
        
        // Entity type identifier
        doc.put("EntityType", new BsonString(entityType));
        
        // Transform component with position and rotation
        doc.put("Transform", serializeTransformComponent());
        
        // UUID component
        doc.put("UUID", serializeUUIDComponent());
        
        // Allow subclasses to add their specific components
        addEntityComponents(doc);
        
        return doc;
    }
    
    /**
     * Serialize the TransformComponent.
     * 
     * <p>Format from TransformComponent.CODEC:
     * <pre>
     * {
     *   "Position": { "X": double, "Y": double, "Z": double },
     *   "Rotation": { "X": float, "Y": float, "Z": float }
     * }
     * </pre>
     */
    protected BsonDocument serializeTransformComponent() {
        BsonDocument transform = new BsonDocument();
        
        // Position (Vector3d)
        BsonDocument position = new BsonDocument();
        position.put("X", new BsonDouble(x));
        position.put("Y", new BsonDouble(y));
        position.put("Z", new BsonDouble(z));
        transform.put("Position", position);
        
        // Rotation (Vector3f) - yaw, pitch, roll as X, Y, Z
        BsonDocument rotation = new BsonDocument();
        rotation.put("X", new BsonDouble(yaw));
        rotation.put("Y", new BsonDouble(pitch));
        rotation.put("Z", new BsonDouble(roll));
        transform.put("Rotation", rotation);
        
        return transform;
    }
    
    /**
     * Serialize the UUIDComponent.
     * 
     * <p>Format: { "UUID": binary(16 bytes) }
     */
    protected BsonDocument serializeUUIDComponent() {
        BsonDocument uuidDoc = new BsonDocument();
        
        // Convert UUID to binary (16 bytes: 8 for most significant, 8 for least)
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        
        uuidDoc.put("UUID", new BsonBinary(buffer.array()));
        
        return uuidDoc;
    }
    
    /**
     * Override in subclasses to add entity-specific components.
     * 
     * @param doc The BSON document to add components to
     */
    protected void addEntityComponents(BsonDocument doc) {
        // Base implementation adds no additional components
    }
    
    // ----- Cloning -----
    
    @Override
    public HytaleEntity clone() {
        try {
            HytaleEntity copy = (HytaleEntity) super.clone();
            copy.uuid = UUID.randomUUID(); // New UUID for clone
            copy.properties = new HashMap<>(this.properties);
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }
    
    // ----- Factory methods -----
    
    /**
     * Create a generic entity at the specified position.
     * 
     * @param entityType Entity type identifier
     * @param x World X coordinate
     * @param y World Y coordinate
     * @param z World Z coordinate
     * @return New HytaleEntity instance
     */
    public static HytaleEntity of(String entityType, double x, double y, double z) {
        return new HytaleEntity(entityType, x, y, z);
    }
    
    /**
     * Create a spawn marker entity.
     * 
     * @param spawnMarkerId The spawn marker identifier
     * @param x World X coordinate
     * @param y World Y coordinate
     * @param z World Z coordinate
     * @return New HytaleSpawnMarker instance
     */
    public static HytaleSpawnMarker spawnMarker(String spawnMarkerId, double x, double y, double z) {
        return new HytaleSpawnMarker(spawnMarkerId, x, y, z);
    }
    
    @Override
    public String toString() {
        return String.format("HytaleEntity{type=%s, pos=(%.2f, %.2f, %.2f), uuid=%s}",
            entityType, x, y, z, uuid);
    }
}
