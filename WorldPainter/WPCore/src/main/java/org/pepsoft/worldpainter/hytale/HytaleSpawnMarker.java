package org.pepsoft.worldpainter.hytale;

import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonDouble;
import org.bson.BsonInt32;
import org.bson.BsonString;

/**
 * A Hytale spawn marker entity for defining creature/NPC spawn points.
 * 
 * <p>Spawn markers define locations where entities can spawn in the world.
 * They include configuration for respawn timing, spawn limits, and the
 * type of entity to spawn.
 * 
 * <p>From Hytale's SpawnMarkerEntity and SpawnMarkerComponent:
 * <ul>
 *   <li>SpawnMarker - ID referencing the spawn marker definition</li>
 *   <li>RespawnTime - Time in seconds between spawns</li>
 *   <li>SpawnCount - Maximum number of active spawns</li>
 *   <li>SpawnRadius - Radius within which entities can spawn</li>
 *   <li>Active - Whether the spawn marker is currently active</li>
 * </ul>
 * 
 * @see HytaleEntity
 */
public class HytaleSpawnMarker extends HytaleEntity {
    
    private static final long serialVersionUID = 1L;
    
    /** Spawn marker definition ID (references spawn marker asset). */
    private String spawnMarkerId;
    
    /** Time in seconds between respawns. */
    private double respawnTime;
    
    /** Maximum number of entities that can be spawned at once. */
    private int spawnCount;
    
    /** Radius within which entities can spawn. */
    private double spawnRadius;
    
    /** Whether this spawn marker is active. */
    private boolean active;
    
    /** Whether spawned entities should despawn when far from players. */
    private boolean despawnWhenFar;
    
    /**
     * Create a spawn marker with default settings.
     * 
     * @param spawnMarkerId The spawn marker definition ID
     * @param x World X coordinate
     * @param y World Y coordinate
     * @param z World Z coordinate
     */
    public HytaleSpawnMarker(String spawnMarkerId, double x, double y, double z) {
        super("SpawnMarker", x, y, z);
        this.spawnMarkerId = spawnMarkerId;
        this.respawnTime = 60.0; // Default 60 seconds
        this.spawnCount = 1;
        this.spawnRadius = 2.0;
        this.active = true;
        this.despawnWhenFar = true;
    }
    
    /**
     * Create a spawn marker with custom settings.
     * 
     * @param spawnMarkerId The spawn marker definition ID
     * @param x World X coordinate
     * @param y World Y coordinate
     * @param z World Z coordinate
     * @param respawnTime Time in seconds between respawns
     * @param spawnCount Maximum concurrent spawns
     */
    public HytaleSpawnMarker(String spawnMarkerId, double x, double y, double z,
                             double respawnTime, int spawnCount) {
        this(spawnMarkerId, x, y, z);
        this.respawnTime = respawnTime;
        this.spawnCount = spawnCount;
    }
    
    // ----- Getters and setters -----
    
    public String getSpawnMarkerId() {
        return spawnMarkerId;
    }
    
    public void setSpawnMarkerId(String spawnMarkerId) {
        this.spawnMarkerId = spawnMarkerId;
    }
    
    public double getRespawnTime() {
        return respawnTime;
    }
    
    public void setRespawnTime(double respawnTime) {
        this.respawnTime = respawnTime;
    }
    
    public int getSpawnCount() {
        return spawnCount;
    }
    
    public void setSpawnCount(int spawnCount) {
        this.spawnCount = spawnCount;
    }
    
    public double getSpawnRadius() {
        return spawnRadius;
    }
    
    public void setSpawnRadius(double spawnRadius) {
        this.spawnRadius = spawnRadius;
    }
    
    public boolean isActive() {
        return active;
    }
    
    public void setActive(boolean active) {
        this.active = active;
    }
    
    public boolean isDespawnWhenFar() {
        return despawnWhenFar;
    }
    
    public void setDespawnWhenFar(boolean despawnWhenFar) {
        this.despawnWhenFar = despawnWhenFar;
    }
    
    // ----- BSON Serialization -----
    
    @Override
    protected void addEntityComponents(BsonDocument doc) {
        super.addEntityComponents(doc);
        
        // SpawnMarker component
        BsonDocument spawnMarkerDoc = new BsonDocument();
        spawnMarkerDoc.put("SpawnMarker", new BsonString(spawnMarkerId));
        spawnMarkerDoc.put("RespawnTime", new BsonDouble(respawnTime));
        spawnMarkerDoc.put("SpawnCount", new BsonInt32(spawnCount));
        spawnMarkerDoc.put("SpawnRadius", new BsonDouble(spawnRadius));
        spawnMarkerDoc.put("Active", new BsonBoolean(active));
        spawnMarkerDoc.put("DespawnWhenFar", new BsonBoolean(despawnWhenFar));
        
        doc.put("SpawnMarker", spawnMarkerDoc);
    }
    
    @Override
    public HytaleSpawnMarker clone() {
        HytaleSpawnMarker copy = (HytaleSpawnMarker) super.clone();
        return copy;
    }
    
    // ----- Factory methods -----
    
    /**
     * Create a spawn marker for a creature type.
     * 
     * @param creatureId Creature type ID (e.g., "Trork_Warrior")
     * @param x World X coordinate
     * @param y World Y coordinate
     * @param z World Z coordinate
     * @return Configured spawn marker
     */
    public static HytaleSpawnMarker forCreature(String creatureId, double x, double y, double z) {
        return new HytaleSpawnMarker("Creature_" + creatureId, x, y, z);
    }
    
    /**
     * Create a spawn marker for an NPC.
     * 
     * @param npcId NPC type ID
     * @param x World X coordinate
     * @param y World Y coordinate
     * @param z World Z coordinate
     * @return Configured spawn marker (non-despawning)
     */
    public static HytaleSpawnMarker forNPC(String npcId, double x, double y, double z) {
        HytaleSpawnMarker marker = new HytaleSpawnMarker("NPC_" + npcId, x, y, z);
        marker.setDespawnWhenFar(false);
        marker.setRespawnTime(0); // Instant respawn
        return marker;
    }
    
    /**
     * Create a spawn marker for a player spawn point.
     * 
     * @param x World X coordinate
     * @param y World Y coordinate
     * @param z World Z coordinate
     * @return Configured player spawn marker
     */
    public static HytaleSpawnMarker forPlayerSpawn(double x, double y, double z) {
        HytaleSpawnMarker marker = new HytaleSpawnMarker("PlayerSpawn", x, y, z);
        marker.setSpawnCount(0); // Unlimited
        marker.setRespawnTime(0);
        marker.setDespawnWhenFar(false);
        return marker;
    }
    
    @Override
    public String toString() {
        return String.format("HytaleSpawnMarker{id=%s, pos=(%.2f, %.2f, %.2f), count=%d, respawn=%.1fs}",
            spawnMarkerId, x, y, z, spawnCount, respawnTime);
    }
}
