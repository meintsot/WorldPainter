package org.pepsoft.worldpainter.hytale;

import java.awt.Rectangle;
import java.io.Serializable;
import java.util.*;

/**
 * Represents a Hytale zone - a geographic region with distinct environment settings.
 * 
 * <p>Zones in Hytale define:
 * <ul>
 *   <li>Environment type (determines sky, lighting, ambient effects)</li>
 *   <li>Biome within the zone</li>
 *   <li>Weather patterns</li>
 *   <li>Spawn settings</li>
 *   <li>Geographic bounds</li>
 * </ul>
 * 
 * <p>Each zone has a unique ID and can overlap with other zones. The zone system
 * is used in export to populate the EnvironmentChunk component.
 * 
 * @see HytaleWorldExporter
 */
public class HytaleZone implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /** Unique identifier for this zone. */
    private final UUID id;
    
    /** Display name for the zone. */
    private String name;
    
    /** Environment type (e.g., "Default", "Underwater", "Underground", "Void"). */
    private String environment;
    
    /** Biome within this zone (e.g., "Grassland", "Desert", "Forest"). */
    private String biome;
    
    /** Priority for overlapping zones (higher = takes precedence). */
    private int priority;
    
    /** Rectangular bounds in chunk coordinates (null = unlimited). */
    private Rectangle bounds;
    
    /** Minimum Y level for this zone (-1 = no minimum). */
    private int minY;
    
    /** Maximum Y level for this zone (-1 = no maximum). */
    private int maxY;
    
    /** Weather pattern for this zone. */
    private String weather;
    
    /** Custom properties for this zone. */
    private final Map<String, Object> properties;
    
    /**
     * Create a new zone with default settings.
     */
    public HytaleZone(String name) {
        this(name, "Default", null);
    }
    
    /**
     * Create a new zone with specified environment.
     */
    public HytaleZone(String name, String environment) {
        this(name, environment, null);
    }
    
    /**
     * Create a new zone with environment and biome.
     */
    public HytaleZone(String name, String environment, String biome) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.environment = environment;
        this.biome = biome;
        this.priority = 0;
        this.bounds = null;
        this.minY = -1;
        this.maxY = -1;
        this.weather = "Clear";
        this.properties = new HashMap<>();
    }
    
    // ----- Getters and Setters -----
    
    public UUID getId() {
        return id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getEnvironment() {
        return environment;
    }
    
    public void setEnvironment(String environment) {
        this.environment = environment;
    }
    
    public String getBiome() {
        return biome;
    }
    
    public void setBiome(String biome) {
        this.biome = biome;
    }
    
    public int getPriority() {
        return priority;
    }
    
    public void setPriority(int priority) {
        this.priority = priority;
    }
    
    public Rectangle getBounds() {
        return bounds;
    }
    
    public void setBounds(Rectangle bounds) {
        this.bounds = bounds;
    }
    
    /**
     * Set bounds from block coordinates.
     */
    public void setBoundsFromBlocks(int minX, int minZ, int maxX, int maxZ) {
        // Convert to chunk coordinates
        int minChunkX = minX >> 5; // divide by 32
        int minChunkZ = minZ >> 5;
        int maxChunkX = maxX >> 5;
        int maxChunkZ = maxZ >> 5;
        this.bounds = new Rectangle(minChunkX, minChunkZ, maxChunkX - minChunkX + 1, maxChunkZ - minChunkZ + 1);
    }
    
    public int getMinY() {
        return minY;
    }
    
    public void setMinY(int minY) {
        this.minY = minY;
    }
    
    public int getMaxY() {
        return maxY;
    }
    
    public void setMaxY(int maxY) {
        this.maxY = maxY;
    }
    
    public String getWeather() {
        return weather;
    }
    
    public void setWeather(String weather) {
        this.weather = weather;
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
    
    // ----- Query methods -----
    
    /**
     * Check if this zone contains a given chunk position.
     */
    public boolean containsChunk(int chunkX, int chunkZ) {
        if (bounds == null) {
            return true; // Unbounded zone covers everything
        }
        return bounds.contains(chunkX, chunkZ);
    }
    
    /**
     * Check if this zone applies to a given position.
     */
    public boolean contains(int blockX, int blockY, int blockZ) {
        // Check Y level
        if (minY >= 0 && blockY < minY) {
            return false;
        }
        if (maxY >= 0 && blockY > maxY) {
            return false;
        }
        
        // Check horizontal bounds
        if (bounds == null) {
            return true;
        }
        int chunkX = blockX >> 5;
        int chunkZ = blockZ >> 5;
        return bounds.contains(chunkX, chunkZ);
    }
    
    /**
     * Check if this zone has vertical bounds.
     */
    public boolean hasVerticalBounds() {
        return minY >= 0 || maxY >= 0;
    }
    
    /**
     * Check if this zone is global (no bounds).
     */
    public boolean isGlobal() {
        return bounds == null && minY < 0 && maxY < 0;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        HytaleZone that = (HytaleZone) obj;
        return id.equals(that.id);
    }
    
    @Override
    public int hashCode() {
        return id.hashCode();
    }
    
    @Override
    public String toString() {
        return name + " [" + environment + (biome != null ? "/" + biome : "") + "]";
    }
    
    // ----- Factory methods for common zones -----
    
    /**
     * Get a list of default zone templates.
     */
    public static List<HytaleZone> getDefaultZones() {
        List<HytaleZone> zones = new ArrayList<>();
        
        zones.add(createOverworld());
        zones.add(createUnderground());
        zones.add(createDesert());
        zones.add(createSnowy());
        zones.add(createTropical());
        zones.add(createOcean());
        
        return zones;
    }
    
    public static HytaleZone createOverworld() {
        HytaleZone zone = new HytaleZone("Overworld", "Default", "Grassland");
        zone.setWeather("Dynamic");
        return zone;
    }
    
    public static HytaleZone createUnderground() {
        HytaleZone zone = new HytaleZone("Underground", "Underground");
        zone.setMaxY(63);
        zone.setWeather("None");
        return zone;
    }
    
    public static HytaleZone createDesert() {
        HytaleZone zone = new HytaleZone("Desert", "Default", "Desert");
        zone.setWeather("Clear");
        return zone;
    }
    
    public static HytaleZone createSnowy() {
        HytaleZone zone = new HytaleZone("Snowy", "Default", "Tundra");
        zone.setWeather("Snowing");
        return zone;
    }
    
    public static HytaleZone createTropical() {
        HytaleZone zone = new HytaleZone("Tropical", "Default", "Tropical");
        zone.setWeather("Rainy");
        return zone;
    }
    
    public static HytaleZone createOcean() {
        HytaleZone zone = new HytaleZone("Ocean", "Underwater", "Ocean");
        zone.setWeather("Dynamic");
        return zone;
    }
    
    // ----- Comparator by priority -----
    
    /**
     * Comparator that sorts zones by priority (highest first).
     */
    public static final Comparator<HytaleZone> BY_PRIORITY = Comparator
        .comparingInt(HytaleZone::getPriority)
        .reversed()
        .thenComparing(HytaleZone::getName);
}
