package org.pepsoft.worldpainter.hytale;

import org.pepsoft.util.IconUtils;
import org.pepsoft.worldpainter.ColourScheme;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.*;
import java.util.List;

/**
 * A terrain type for Hytale worlds, analogous to Terrain/MixedMaterial for Minecraft.
 * 
 * <p>HytaleTerrain represents what block(s) should be placed at a location during
 * Hytale export. It supports:
 * <ul>
 *   <li>Simple single-block terrain</li>
 *   <li>Noisy multi-block mixing</li>
 *   <li>Layered terrain (different blocks at different depths)</li>
 * </ul>
 * 
 * <p>The actual block placement is determined by the export layer in collaboration
 * with {@link HytaleBlockRegistry} and {@link HytaleBlockMapping}.
 * 
 * @see HytaleBlock
 * @see HytaleBlockRegistry
 */
public final class HytaleTerrain implements Serializable, Comparable<HytaleTerrain> {
    
    private static final long serialVersionUID = 1L;
    
    /** Unique identifier for this terrain. */
    private final UUID id;
    
    /** Display name for the GUI. */
    private String name;
    
    /** Block entries for this terrain. */
    private Row[] rows;
    
    /** Mode of block selection. */
    private Mode mode;
    
    /** Scale factor for noise/blob modes. */
    private float scale;
    
    /** Hytale biome name associated with this terrain. */
    private String biome;
    
    /** Custom colour for display, or null to use block colour. */
    private Integer colour;
    
    /** Cached icon. */
    private transient BufferedImage icon;
    
    /**
     * Create a simple single-block terrain.
     */
    public HytaleTerrain(String name, HytaleBlock block) {
        this(name, new Row[] { new Row(block, 1000) }, Mode.SIMPLE, 1.0f, null, null);
    }
    
    /**
     * Create a simple single-block terrain with biome.
     */
    public HytaleTerrain(String name, HytaleBlock block, String biome) {
        this(name, new Row[] { new Row(block, 1000) }, Mode.SIMPLE, 1.0f, biome, null);
    }
    
    /**
     * Create a noisy multi-block terrain.
     */
    public HytaleTerrain(String name, Row[] rows, String biome) {
        this(name, rows, Mode.NOISE, 1.0f, biome, null);
    }
    
    /**
     * Create a terrain with full control over parameters.
     */
    public HytaleTerrain(String name, Row[] rows, Mode mode, float scale, String biome, Integer colour) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.rows = rows;
        this.mode = mode;
        this.scale = scale;
        this.biome = biome;
        this.colour = colour;
    }
    
    // Getters
    
    public UUID getId() {
        return id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public Row[] getRows() {
        return rows;
    }
    
    public void setRows(Row[] rows) {
        this.rows = rows;
        icon = null; // Invalidate cached icon
    }
    
    public Mode getMode() {
        return mode;
    }
    
    public void setMode(Mode mode) {
        this.mode = mode;
    }
    
    public float getScale() {
        return scale;
    }
    
    public void setScale(float scale) {
        this.scale = scale;
    }
    
    public String getBiome() {
        return biome;
    }
    
    public void setBiome(String biome) {
        this.biome = biome;
    }
    
    public Integer getColour() {
        return colour;
    }
    
    public void setColour(Integer colour) {
        this.colour = colour;
        icon = null;
    }
    
    /**
     * Get the primary block for this terrain (first row or highest occurrence).
     */
    public HytaleBlock getPrimaryBlock() {
        if (rows == null || rows.length == 0) {
            return HytaleBlock.EMPTY;
        }
        return rows[0].block;
    }
    
    /**
     * Get a block for the given position using the terrain's mode.
     * 
     * @param seed World seed for noise
     * @param x Block X coordinate
     * @param y Block Y coordinate
     * @param z Block Z coordinate (depth below surface)
     * @return The HytaleBlock to place
     */
    public HytaleBlock getBlock(long seed, int x, int y, int z) {
        if (rows == null || rows.length == 0) {
            return HytaleBlock.EMPTY;
        }
        if (rows.length == 1) {
            return rows[0].block;
        }
        
        switch (mode) {
            case SIMPLE:
                return rows[0].block;
                
            case NOISE:
                return getNoiseBlock(seed, x, y, z);
                
            case BLOBS:
                return getBlobBlock(seed, x, y, z);
                
            case LAYERED:
                return getLayeredBlock(z);
                
            default:
                return rows[0].block;
        }
    }
    
    private HytaleBlock getNoiseBlock(long seed, int x, int y, int z) {
        // Simple random selection based on occurrence weights
        Random rnd = new Random(seed + (x * 65537L) + (y * 4099L) + (z * 257L));
        int total = Arrays.stream(rows).mapToInt(r -> r.occurrence).sum();
        int roll = rnd.nextInt(total);
        int cumulative = 0;
        for (Row row : rows) {
            cumulative += row.occurrence;
            if (roll < cumulative) {
                return row.block;
            }
        }
        return rows[0].block;
    }
    
    private HytaleBlock getBlobBlock(long seed, int x, int y, int z) {
        // For blobs, use 3D noise to create regions
        // Simplified version - full implementation would use Perlin noise
        double scaledX = x / (16.0 * scale);
        double scaledY = y / (16.0 * scale);
        double scaledZ = z / (8.0 * scale);
        
        // Simple hash-based "noise" for blob selection
        long hash = seed + (long)(scaledX * 314159) + (long)(scaledY * 271828) + (long)(scaledZ * 141421);
        Random rnd = new Random(hash);
        
        int total = Arrays.stream(rows).mapToInt(r -> r.occurrence).sum();
        int roll = rnd.nextInt(total);
        int cumulative = 0;
        for (Row row : rows) {
            cumulative += row.occurrence;
            if (roll < cumulative) {
                return row.block;
            }
        }
        return rows[0].block;
    }
    
    private HytaleBlock getLayeredBlock(int depth) {
        // For layered mode, occurrence represents layer height
        int currentDepth = 0;
        for (Row row : rows) {
            currentDepth += row.occurrence;
            if (depth < currentDepth) {
                return row.block;
            }
        }
        // Beyond all layers, use the last one
        return rows[rows.length - 1].block;
    }
    
    /**
     * Get an icon for this terrain.
     */
    public BufferedImage getIcon(ColourScheme colourScheme) {
        if (icon == null) {
            icon = createIcon();
        }
        return icon;
    }
    
    private BufferedImage createIcon() {
        int size = 16;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        
        // Fill with terrain colour
        int rgb = getEffectiveColour();
        g2d.setColor(new Color(rgb));
        g2d.fillRect(0, 0, size, size);
        
        // Draw a border
        g2d.setColor(Color.DARK_GRAY);
        g2d.drawRect(0, 0, size - 1, size - 1);
        
        g2d.dispose();
        return img;
    }
    
    /**
     * Get the effective display colour for this terrain.
     */
    public int getEffectiveColour() {
        if (colour != null) {
            return colour;
        }
        // Derive from primary block
        HytaleBlock primary = getPrimaryBlock();
        return getBlockColour(primary);
    }
    
    /**
     * Get a default colour for a Hytale block based on its ID.
     */
    private static int getBlockColour(HytaleBlock block) {
        String id = block.id;
        
        // Rock colours
        if (id.startsWith("Rock_Stone")) return 0x808080;
        if (id.startsWith("Rock_Bedrock")) return 0x2d2d2d;
        if (id.startsWith("Rock_Ice")) return 0xa0d0ff;
        if (id.startsWith("Rock_Sandstone")) return 0xd4c099;
        if (id.startsWith("Rock_Shale")) return 0x5a5a5a;
        if (id.startsWith("Rock_Slate")) return 0x4a4a4a;
        if (id.startsWith("Rock_Basalt")) return 0x3a3a3a;
        if (id.startsWith("Rock_Marble")) return 0xf0f0f0;
        if (id.startsWith("Rock_Quartzite")) return 0xe0e0e0;
        if (id.startsWith("Rock_Calcite")) return 0xdbd7ca;
        if (id.startsWith("Rock_Chalk")) return 0xffffff;
        if (id.startsWith("Rock_Volcanic")) return 0x2a2a2a;
        if (id.startsWith("Rock_Aqua")) return 0x50a0b0;
        if (id.startsWith("Rock_Salt")) return 0xf0e8e0;
        if (id.startsWith("Rock_")) return 0x707070;
        
        // Soil colours
        if (id.startsWith("Soil_Grass_Full")) return 0x00a000;
        if (id.startsWith("Soil_Grass_Cold")) return 0x80c0a0;
        if (id.startsWith("Soil_Grass_Sunny")) return 0x70b830;
        if (id.startsWith("Soil_Grass_Burnt")) return 0x6a5a20;
        if (id.startsWith("Soil_Grass_Deep")) return 0x2a7a1a;
        if (id.startsWith("Soil_Grass_Wet")) return 0x4a8a3a;
        if (id.startsWith("Soil_Grass")) return 0x59a52c;
        if (id.startsWith("Soil_Leaves")) return 0x5a7a30;
        if (id.startsWith("Soil_Sand_Ashen")) return 0x808070;
        if (id.startsWith("Soil_Dirt")) return 0x8b5a2b;
        if (id.startsWith("Soil_Sand_Red")) return 0xc4633c;
        if (id.startsWith("Soil_Sand_White")) return 0xf4e8c6;
        if (id.startsWith("Soil_Sand")) return 0xdbc497;
        if (id.startsWith("Soil_Gravel")) return 0x909090;
        if (id.startsWith("Soil_Clay")) return 0x9ea4ae;
        if (id.startsWith("Soil_Mud")) return 0x5a4a3a;
        if (id.startsWith("Soil_Snow")) return 0xfffafa;
        if (id.startsWith("Soil_")) return 0x8b5a2b;
        
        // Wood colours
        if (id.contains("Oak")) return 0x8b7355;
        if (id.contains("Birch")) return 0xd5c9a6;
        if (id.contains("Fir") || id.contains("Cedar")) return 0x5d4e37;
        if (id.contains("Redwood")) return 0x8b4513;
        if (id.contains("Palm")) return 0x9e8b61;
        if (id.contains("Jungle")) return 0x6b4423;
        if (id.startsWith("Wood_")) return 0x8b7355;
        
        // Plant colours
        if (id.contains("Leaves")) return 0x228b22;
        if (id.contains("Grass") || id.contains("Fern")) return 0x32cd32;
        if (id.contains("Flower")) return 0xffff00;
        if (id.contains("Cactus")) return 0x2e8b57;
        if (id.startsWith("Plant_")) return 0x228b22;
        
        // Ore colours
        if (id.contains("Iron")) return 0xd4a574;
        if (id.contains("Gold")) return 0xffd700;
        if (id.contains("Copper")) return 0xb87333;
        if (id.contains("Silver")) return 0xc0c0c0;
        if (id.contains("Cobalt")) return 0x0047ab;
        if (id.contains("Mithril")) return 0x4682b4;
        if (id.contains("Adamantite")) return 0x006400;
        if (id.contains("Thorium")) return 0x98fb98;
        if (id.contains("Onyxium")) return 0x0a0a0a;
        if (id.startsWith("Ore_")) return 0x808080;
        
        // Fluid colours
        if (id.contains("Water")) return 0x3366ff;
        if (id.contains("Lava")) return 0xff4500;
        
        // Default
        if (id.equals("Empty")) return 0x000000;
        return 0xa0a0a0;
    }
    
    @Override
    public int compareTo(HytaleTerrain other) {
        return name.compareToIgnoreCase(other.name);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        HytaleTerrain that = (HytaleTerrain) obj;
        return id.equals(that.id);
    }
    
    @Override
    public int hashCode() {
        return id.hashCode();
    }
    
    @Override
    public String toString() {
        return name;
    }
    
    // Serialization
    
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        // Icon will be recreated on demand
    }
    
    // ----- Static terrains and PICK_LIST -----
    
    // ===== SIMPLE SOIL TERRAINS =====
    public static final HytaleTerrain GRASS = new HytaleTerrain("Grass",
        new Row[] { new Row(HytaleBlock.of("Soil_Grass"), 1000) },
        Mode.SIMPLE, 1.0f, "Grassland", 0x59a52c);
    
    public static final HytaleTerrain GRASS_FULL = new HytaleTerrain("Full Grass",
        new Row[] { new Row(HytaleBlock.of("Soil_Grass_Full"), 1000) },
        Mode.SIMPLE, 1.0f, "Tropical", 0x00a000);
    
    public static final HytaleTerrain GRASS_DRY = new HytaleTerrain("Dry Grass",
        new Row[] { new Row(HytaleBlock.of("Soil_Grass_Dry"), 1000) },
        Mode.SIMPLE, 1.0f, "Savanna", 0xa0a020);
    
    public static final HytaleTerrain GRASS_COLD = new HytaleTerrain("Cold Grass",
        new Row[] { new Row(HytaleBlock.of("Soil_Grass_Cold"), 1000) },
        Mode.SIMPLE, 1.0f, "Tundra", 0x80c0a0);
    
    public static final HytaleTerrain DIRT = new HytaleTerrain("Dirt",
        new Row[] { new Row(HytaleBlock.of("Soil_Dirt"), 1000) },
        Mode.SIMPLE, 1.0f, null, 0x8b5a2b);
    
    public static final HytaleTerrain GRASS_SUNNY = new HytaleTerrain("Sunny Grass",
        new Row[] { new Row(HytaleBlock.of("Soil_Grass_Sunny"), 1000) },
        Mode.SIMPLE, 1.0f, "Grassland", 0x70b830);
    
    public static final HytaleTerrain SAND = new HytaleTerrain("Sand",
        new Row[] { new Row(HytaleBlock.of("Soil_Sand"), 1000) },
        Mode.SIMPLE, 1.0f, "Desert", 0xdbc497);
    
    public static final HytaleTerrain SAND_RED = new HytaleTerrain("Red Sand",
        new Row[] { new Row(HytaleBlock.of("Soil_Sand_Red"), 1000) },
        Mode.SIMPLE, 1.0f, "Desert", 0xc4633c);
    
    public static final HytaleTerrain SAND_WHITE = new HytaleTerrain("White Sand",
        new Row[] { new Row(HytaleBlock.of("Soil_Sand_White"), 1000) },
        Mode.SIMPLE, 1.0f, "Ocean", 0xf4e8c6);
    
    public static final HytaleTerrain SNOW = new HytaleTerrain("Snow",
        new Row[] { new Row(HytaleBlock.of("Soil_Snow"), 1000) },
        Mode.SIMPLE, 1.0f, "Tundra", 0xfffafa);
    
    public static final HytaleTerrain GRAVEL = new HytaleTerrain("Gravel",
        new Row[] { new Row(HytaleBlock.of("Soil_Gravel"), 1000) },
        Mode.SIMPLE, 1.0f, null, 0x909090);
    
    public static final HytaleTerrain GRAVEL_MOSSY = new HytaleTerrain("Mossy Gravel",
        new Row[] { new Row(HytaleBlock.of("Soil_Gravel_Mossy"), 1000) },
        Mode.SIMPLE, 1.0f, "Forest", 0x708060);
    
    public static final HytaleTerrain CLAY = new HytaleTerrain("Clay",
        new Row[] { new Row(HytaleBlock.of("Soil_Clay"), 1000) },
        Mode.SIMPLE, 1.0f, null, 0x9ea4ae);
    
    public static final HytaleTerrain MUD = new HytaleTerrain("Mud",
        new Row[] { new Row(HytaleBlock.of("Soil_Mud"), 1000) },
        Mode.SIMPLE, 1.0f, "Swamp", 0x5a4a3a);
    
    // ===== SIMPLE ROCK TERRAINS =====
    public static final HytaleTerrain STONE = new HytaleTerrain("Stone",
        new Row[] { new Row(HytaleBlock.of("Rock_Stone"), 1000) },
        Mode.SIMPLE, 1.0f, null, 0x808080);
    
    public static final HytaleTerrain COBBLESTONE = new HytaleTerrain("Cobblestone",
        new Row[] { new Row(HytaleBlock.of("Rock_Stone_Cobble"), 1000) },
        Mode.SIMPLE, 1.0f, null, 0x787878);
    
    public static final HytaleTerrain COBBLESTONE_MOSSY = new HytaleTerrain("Mossy Cobblestone",
        new Row[] { new Row(HytaleBlock.of("Rock_Stone_Cobble_Mossy"), 1000) },
        Mode.SIMPLE, 1.0f, null, 0x607850);
    
    public static final HytaleTerrain SANDSTONE = new HytaleTerrain("Sandstone",
        new Row[] { new Row(HytaleBlock.of("Rock_Sandstone"), 1000) },
        Mode.SIMPLE, 1.0f, "Desert", 0xd4c099);
    
    public static final HytaleTerrain SANDSTONE_RED = new HytaleTerrain("Red Sandstone",
        new Row[] { new Row(HytaleBlock.of("Rock_Sandstone_Red"), 1000) },
        Mode.SIMPLE, 1.0f, "Desert", 0xb45030);
    
    public static final HytaleTerrain SHALE = new HytaleTerrain("Shale",
        new Row[] { new Row(HytaleBlock.of("Rock_Shale"), 1000) },
        Mode.SIMPLE, 1.0f, null, 0x5a5a5a);
    
    public static final HytaleTerrain SLATE = new HytaleTerrain("Slate",
        new Row[] { new Row(HytaleBlock.of("Rock_Slate"), 1000) },
        Mode.SIMPLE, 1.0f, null, 0x4a4a4a);
    
    public static final HytaleTerrain BASALT = new HytaleTerrain("Basalt",
        new Row[] { new Row(HytaleBlock.of("Rock_Basalt"), 1000) },
        Mode.SIMPLE, 1.0f, "Volcanic", 0x3a3a3a);
    
    public static final HytaleTerrain MARBLE = new HytaleTerrain("Marble",
        new Row[] { new Row(HytaleBlock.of("Rock_Marble"), 1000) },
        Mode.SIMPLE, 1.0f, null, 0xf0f0f0);
    
    public static final HytaleTerrain QUARTZITE = new HytaleTerrain("Quartzite",
        new Row[] { new Row(HytaleBlock.of("Rock_Quartzite"), 1000) },
        Mode.SIMPLE, 1.0f, null, 0xe0e0e0);
    
    public static final HytaleTerrain CALCITE = new HytaleTerrain("Calcite",
        new Row[] { new Row(HytaleBlock.of("Rock_Calcite"), 1000) },
        Mode.SIMPLE, 1.0f, null, 0xdbd7ca);
    
    public static final HytaleTerrain CHALK = new HytaleTerrain("Chalk",
        new Row[] { new Row(HytaleBlock.of("Rock_Chalk"), 1000) },
        Mode.SIMPLE, 1.0f, null, 0xfafafa);
    
    public static final HytaleTerrain VOLCANIC = new HytaleTerrain("Volcanic Rock",
        new Row[] { new Row(HytaleBlock.of("Rock_Volcanic"), 1000) },
        Mode.SIMPLE, 1.0f, "Volcanic", 0x2a2a2a);
    
    public static final HytaleTerrain MAGMA_COOLED = new HytaleTerrain("Cooled Magma",
        new Row[] { new Row(HytaleBlock.of("Rock_Magma_Cooled"), 1000) },
        Mode.SIMPLE, 1.0f, "Volcanic", 0x1a0a0a);
    
    public static final HytaleTerrain ICE = new HytaleTerrain("Ice",
        new Row[] { new Row(HytaleBlock.of("Rock_Ice"), 1000) },
        Mode.SIMPLE, 1.0f, "Tundra", 0xa0d0ff);
    
    public static final HytaleTerrain BEDROCK = new HytaleTerrain("Bedrock",
        new Row[] { new Row(HytaleBlock.of("Rock_Bedrock"), 1000) },
        Mode.SIMPLE, 1.0f, null, 0x2d2d2d);
    
    // ===== ZONE-SPECIFIC MIXED TERRAINS =====
    
    // Zone 1 - Temperate
    public static final HytaleTerrain ZONE1_GRASSLAND = new HytaleTerrain("Z1: Temperate Grassland",
        new Row[] {
            new Row(HytaleBlock.of("Soil_Grass"), 800),
            new Row(HytaleBlock.of("Soil_Dirt"), 200)
        }, Mode.NOISE, 1.0f, "Grassland", 0x59a52c);
    
    public static final HytaleTerrain ZONE1_FOREST_FLOOR = new HytaleTerrain("Z1: Forest Floor",
        new Row[] {
            new Row(HytaleBlock.of("Soil_Grass"), 400),
            new Row(HytaleBlock.of("Soil_Dirt"), 400),
            new Row(HytaleBlock.of("Soil_Gravel_Mossy"), 200)
        }, Mode.BLOBS, 1.5f, "Forest", 0x4a7a2a);
    
    public static final HytaleTerrain ZONE1_MEADOW = new HytaleTerrain("Z1: Meadow",
        new Row[] {
            new Row(HytaleBlock.of("Soil_Grass_Full"), 700),
            new Row(HytaleBlock.of("Soil_Grass"), 300)
        }, Mode.NOISE, 1.0f, "Grassland", 0x40b030);
    
    public static final HytaleTerrain ZONE1_BEACH = new HytaleTerrain("Z1: Beach",
        new Row[] {
            new Row(HytaleBlock.of("Soil_Sand"), 700),
            new Row(HytaleBlock.of("Soil_Sand_White"), 200),
            new Row(HytaleBlock.of("Soil_Gravel"), 100)
        }, Mode.NOISE, 1.0f, "Ocean", 0xdbc497);
    
    public static final HytaleTerrain ZONE1_RIVERBED = new HytaleTerrain("Z1: River Bed",
        new Row[] {
            new Row(HytaleBlock.of("Soil_Gravel"), 500),
            new Row(HytaleBlock.of("Soil_Sand"), 300),
            new Row(HytaleBlock.of("Soil_Clay"), 200)
        }, Mode.BLOBS, 1.0f, "Grassland", 0x90a080);
    
    // Zone 2 - Arid / Desert
    public static final HytaleTerrain ZONE2_DESERT = new HytaleTerrain("Z2: Desert",
        new Row[] {
            new Row(HytaleBlock.of("Soil_Sand"), 800),
            new Row(HytaleBlock.of("Soil_Sand_Red"), 200)
        }, Mode.BLOBS, 2.0f, "Desert", 0xd4b070);
    
    public static final HytaleTerrain ZONE2_RED_DESERT = new HytaleTerrain("Z2: Red Desert",
        new Row[] {
            new Row(HytaleBlock.of("Soil_Sand_Red"), 700),
            new Row(HytaleBlock.of("Soil_Sand"), 200),
            new Row(HytaleBlock.of("Rock_Sandstone_Red"), 100)
        }, Mode.BLOBS, 2.0f, "Desert", 0xc46040);
    
    public static final HytaleTerrain ZONE2_MESA = new HytaleTerrain("Z2: Mesa",
        new Row[] {
            new Row(HytaleBlock.of("Rock_Sandstone_Red"), 500),
            new Row(HytaleBlock.of("Soil_Sand_Red"), 300),
            new Row(HytaleBlock.of("Rock_Sandstone"), 200)
        }, Mode.LAYERED, 1.0f, "Desert", 0xb05030);
    
    public static final HytaleTerrain ZONE2_OASIS = new HytaleTerrain("Z2: Oasis",
        new Row[] {
            new Row(HytaleBlock.of("Soil_Grass"), 500),
            new Row(HytaleBlock.of("Soil_Sand"), 300),
            new Row(HytaleBlock.of("Soil_Clay"), 200)
        }, Mode.BLOBS, 1.0f, "Desert", 0x80a050);
    
    public static final HytaleTerrain ZONE2_SAVANNA = new HytaleTerrain("Z2: Savanna",
        new Row[] {
            new Row(HytaleBlock.of("Soil_Grass_Dry"), 600),
            new Row(HytaleBlock.of("Soil_Dirt"), 300),
            new Row(HytaleBlock.of("Soil_Sand"), 100)
        }, Mode.NOISE, 1.5f, "Savanna", 0xb0a030);
    
    // Zone 3 - Boreal / Cold
    public static final HytaleTerrain ZONE3_TUNDRA = new HytaleTerrain("Z3: Tundra",
        new Row[] {
            new Row(HytaleBlock.of("Soil_Snow"), 600),
            new Row(HytaleBlock.of("Soil_Grass_Cold"), 300),
            new Row(HytaleBlock.of("Soil_Gravel"), 100)
        }, Mode.BLOBS, 2.0f, "Tundra", 0xe0e8f0);
    
    public static final HytaleTerrain ZONE3_TAIGA = new HytaleTerrain("Z3: Taiga",
        new Row[] {
            new Row(HytaleBlock.of("Soil_Grass_Cold"), 500),
            new Row(HytaleBlock.of("Soil_Snow"), 300),
            new Row(HytaleBlock.of("Soil_Dirt"), 200)
        }, Mode.BLOBS, 1.5f, "Tundra", 0x607860);
    
    public static final HytaleTerrain ZONE3_FROZEN_LAKE = new HytaleTerrain("Z3: Frozen Lake",
        new Row[] {
            new Row(HytaleBlock.of("Rock_Ice"), 800),
            new Row(HytaleBlock.of("Soil_Snow"), 200)
        }, Mode.NOISE, 1.0f, "Tundra", 0xa0d0ff);
    
    public static final HytaleTerrain ZONE3_SNOWY_PEAKS = new HytaleTerrain("Z3: Snowy Peaks",
        new Row[] {
            new Row(HytaleBlock.of("Soil_Snow"), 500),
            new Row(HytaleBlock.of("Rock_Stone"), 400),
            new Row(HytaleBlock.of("Rock_Ice"), 100)
        }, Mode.LAYERED, 1.0f, "Mountain", 0xd0d8e0);
    
    // Zone 4 - Volcanic
    public static final HytaleTerrain ZONE4_VOLCANIC_PLAINS = new HytaleTerrain("Z4: Volcanic Plains",
        new Row[] {
            new Row(HytaleBlock.of("Rock_Volcanic"), 500),
            new Row(HytaleBlock.of("Rock_Basalt"), 300),
            new Row(HytaleBlock.of("Rock_Magma_Cooled"), 200)
        }, Mode.BLOBS, 2.0f, "Volcanic", 0x2a2a2a);
    
    public static final HytaleTerrain ZONE4_LAVA_FIELDS = new HytaleTerrain("Z4: Lava Fields",
        new Row[] {
            new Row(HytaleBlock.of("Rock_Magma_Cooled"), 600),
            new Row(HytaleBlock.of("Rock_Basalt"), 300),
            new Row(HytaleBlock.of("Rock_Volcanic"), 100)
        }, Mode.BLOBS, 1.5f, "Volcanic", 0x1a0a0a);
    
    public static final HytaleTerrain ZONE4_ASH_WASTE = new HytaleTerrain("Z4: Ash Waste",
        new Row[] {
            new Row(HytaleBlock.of("Soil_Sand"), 400),
            new Row(HytaleBlock.of("Rock_Volcanic"), 400),
            new Row(HytaleBlock.of("Rock_Basalt"), 200)
        }, Mode.NOISE, 1.5f, "Volcanic", 0x404040);
    
    // ===== MIXED NATURAL TERRAINS =====
    public static final HytaleTerrain STONE_MIX = new HytaleTerrain("Stone Mix",
        new Row[] {
            new Row(HytaleBlock.of("Rock_Stone"), 500),
            new Row(HytaleBlock.of("Rock_Shale"), 200),
            new Row(HytaleBlock.of("Rock_Slate"), 200),
            new Row(HytaleBlock.of("Rock_Quartzite"), 100)
        }, Mode.BLOBS, 2.0f, null, 0x707070);
    
    public static final HytaleTerrain MOUNTAIN_ROCK = new HytaleTerrain("Mountain Rock",
        new Row[] {
            new Row(HytaleBlock.of("Rock_Stone"), 400),
            new Row(HytaleBlock.of("Rock_Stone_Cobble"), 300),
            new Row(HytaleBlock.of("Soil_Gravel"), 200),
            new Row(HytaleBlock.of("Rock_Slate"), 100)
        }, Mode.BLOBS, 1.5f, "Mountain", 0x808080);
    
    public static final HytaleTerrain SWAMP = new HytaleTerrain("Swamp",
        new Row[] {
            new Row(HytaleBlock.of("Soil_Mud"), 400),
            new Row(HytaleBlock.of("Soil_Grass"), 300),
            new Row(HytaleBlock.of("Soil_Clay"), 200),
            new Row(HytaleBlock.of("Soil_Dirt"), 100)
        }, Mode.BLOBS, 1.5f, "Swamp", 0x4a5a3a);
    
    public static final HytaleTerrain TROPICAL_BEACH = new HytaleTerrain("Tropical Beach",
        new Row[] {
            new Row(HytaleBlock.of("Soil_Sand_White"), 600),
            new Row(HytaleBlock.of("Soil_Sand"), 300),
            new Row(HytaleBlock.of("Soil_Gravel"), 100)
        }, Mode.NOISE, 1.0f, "Tropical", 0xf0e0c0);
    
    public static final HytaleTerrain OCEAN_FLOOR = new HytaleTerrain("Ocean Floor",
        new Row[] {
            new Row(HytaleBlock.of("Soil_Sand"), 400),
            new Row(HytaleBlock.of("Soil_Clay"), 300),
            new Row(HytaleBlock.of("Soil_Gravel"), 200),
            new Row(HytaleBlock.of("Rock_Stone"), 100)
        }, Mode.BLOBS, 2.0f, "Ocean", 0x506070);
    
    // ===== LAYERED TERRAINS =====
    public static final HytaleTerrain GRASSLAND_LAYERED = new HytaleTerrain("Grassland (Layered)",
        new Row[] {
            new Row(HytaleBlock.of("Soil_Grass"), 1),      // Top: grass
            new Row(HytaleBlock.of("Soil_Dirt"), 3),       // 3 blocks of dirt
            new Row(HytaleBlock.of("Rock_Stone"), 1000)    // Then stone
        }, Mode.LAYERED, 1.0f, "Grassland", 0x59a52c);
    
    public static final HytaleTerrain DESERT_LAYERED = new HytaleTerrain("Desert (Layered)",
        new Row[] {
            new Row(HytaleBlock.of("Soil_Sand"), 4),        // Top: 4 layers of sand
            new Row(HytaleBlock.of("Rock_Sandstone"), 1000) // Then sandstone
        }, Mode.LAYERED, 1.0f, "Desert", 0xdbc497);
    
    public static final HytaleTerrain TUNDRA_LAYERED = new HytaleTerrain("Tundra (Layered)",
        new Row[] {
            new Row(HytaleBlock.of("Soil_Snow"), 2),       // Top: 2 blocks of snow
            new Row(HytaleBlock.of("Soil_Dirt"), 3),       // Then dirt
            new Row(HytaleBlock.of("Rock_Stone"), 1000)    // Then stone
        }, Mode.LAYERED, 1.0f, "Tundra", 0xe0e8f0);
    
    // ===== FLUID TERRAINS =====
    public static final HytaleTerrain WATER = new HytaleTerrain("Water",
        new Row[] { new Row(HytaleBlock.of("Water_Source"), 1000) },
        Mode.SIMPLE, 1.0f, "Ocean", 0x3366ff);
    
    public static final HytaleTerrain LAVA = new HytaleTerrain("Lava",
        new Row[] { new Row(HytaleBlock.of("Lava_Source"), 1000) },
        Mode.SIMPLE, 1.0f, "Volcanic", 0xff4500);
    
    // ===== ADDITIONAL SOIL TERRAINS (official blocks) =====
    public static final HytaleTerrain GRASS_BURNT = new HytaleTerrain("Burnt Grass",
        new Row[] { new Row(HytaleBlock.of("Soil_Grass_Burnt"), 1000) },
        Mode.SIMPLE, 1.0f, "Volcanic", 0x6a5a20);
    
    public static final HytaleTerrain GRASS_DEEP = new HytaleTerrain("Deep Grass",
        new Row[] { new Row(HytaleBlock.of("Soil_Grass_Deep"), 1000) },
        Mode.SIMPLE, 1.0f, "Forest", 0x2a7a1a);
    
    public static final HytaleTerrain GRASS_WET = new HytaleTerrain("Wet Grass",
        new Row[] { new Row(HytaleBlock.of("Soil_Grass_Wet"), 1000) },
        Mode.SIMPLE, 1.0f, "Swamp", 0x4a8a3a);
    
    public static final HytaleTerrain DIRT_BURNT = new HytaleTerrain("Burnt Dirt",
        new Row[] { new Row(HytaleBlock.of("Soil_Dirt_Burnt"), 1000) },
        Mode.SIMPLE, 1.0f, "Volcanic", 0x5a3a1b);
    
    public static final HytaleTerrain DIRT_COLD = new HytaleTerrain("Cold Dirt",
        new Row[] { new Row(HytaleBlock.of("Soil_Dirt_Cold"), 1000) },
        Mode.SIMPLE, 1.0f, "Tundra", 0x6b5a4b);
    
    public static final HytaleTerrain DIRT_DRY = new HytaleTerrain("Dry Dirt",
        new Row[] { new Row(HytaleBlock.of("Soil_Dirt_Dry"), 1000) },
        Mode.SIMPLE, 1.0f, "Desert", 0x9b6a3b);
    
    public static final HytaleTerrain DIRT_POISONED = new HytaleTerrain("Poisoned Dirt",
        new Row[] { new Row(HytaleBlock.of("Soil_Dirt_Poisoned"), 1000) },
        Mode.SIMPLE, 1.0f, "Swamp", 0x4a5a2b);
    
    public static final HytaleTerrain SAND_ASHEN = new HytaleTerrain("Ashen Sand",
        new Row[] { new Row(HytaleBlock.of("Soil_Sand_Ashen"), 1000) },
        Mode.SIMPLE, 1.0f, "Volcanic", 0x808070);
    
    public static final HytaleTerrain LEAVES_FLOOR = new HytaleTerrain("Leaf Litter",
        new Row[] { new Row(HytaleBlock.of("Soil_Leaves"), 1000) },
        Mode.SIMPLE, 1.0f, "Forest", 0x5a7a30);
    
    public static final HytaleTerrain MUD_DRY = new HytaleTerrain("Dry Mud",
        new Row[] { new Row(HytaleBlock.of("Soil_Mud_Dry"), 1000) },
        Mode.SIMPLE, 1.0f, "Desert", 0x7a6a4a);
    
    // ===== ADDITIONAL ROCK TERRAINS (official blocks) =====
    public static final HytaleTerrain STONE_MOSSY = new HytaleTerrain("Mossy Stone",
        new Row[] { new Row(HytaleBlock.of("Rock_Stone_Mossy"), 1000) },
        Mode.SIMPLE, 1.0f, "Forest", 0x607850);
    
    public static final HytaleTerrain SANDSTONE_WHITE = new HytaleTerrain("White Sandstone",
        new Row[] { new Row(HytaleBlock.of("Rock_Sandstone_White"), 1000) },
        Mode.SIMPLE, 1.0f, "Ocean", 0xe8e0d0);
    
    public static final HytaleTerrain AQUA_ROCK = new HytaleTerrain("Aqua Rock",
        new Row[] { new Row(HytaleBlock.of("Rock_Aqua"), 1000) },
        Mode.SIMPLE, 1.0f, "Ocean", 0x50a0b0);
    
    public static final HytaleTerrain SALT = new HytaleTerrain("Salt",
        new Row[] { new Row(HytaleBlock.of("Rock_Salt"), 1000) },
        Mode.SIMPLE, 1.0f, null, 0xf0e8e0);
    
    public static final HytaleTerrain ICE_PERMAFROST = new HytaleTerrain("Permafrost",
        new Row[] { new Row(HytaleBlock.of("Rock_Ice_Permafrost"), 1000) },
        Mode.SIMPLE, 1.0f, "Tundra", 0x90b8c8);
    
    // ===== ADDITIONAL MIXED TERRAINS =====
    public static final HytaleTerrain ZONE1_AUTUMN = new HytaleTerrain("Z1: Autumn Forest",
        new Row[] {
            new Row(HytaleBlock.of("Soil_Grass"), 400),
            new Row(HytaleBlock.of("Soil_Leaves"), 400),
            new Row(HytaleBlock.of("Soil_Dirt"), 200)
        }, Mode.BLOBS, 1.5f, "Forest", 0x8a6a2a);
    
    public static final HytaleTerrain ZONE2_PLATEAU = new HytaleTerrain("Z2: Plateau",
        new Row[] {
            new Row(HytaleBlock.of("Rock_Sandstone"), 500),
            new Row(HytaleBlock.of("Rock_Sandstone_Red"), 300),
            new Row(HytaleBlock.of("Soil_Sand"), 200)
        }, Mode.BLOBS, 2.0f, "Desert", 0xc4a060);
    
    public static final HytaleTerrain ZONE3_BOREAL_FOREST = new HytaleTerrain("Z3: Boreal Forest",
        new Row[] {
            new Row(HytaleBlock.of("Soil_Grass_Cold"), 400),
            new Row(HytaleBlock.of("Soil_Dirt_Cold"), 300),
            new Row(HytaleBlock.of("Soil_Snow"), 200),
            new Row(HytaleBlock.of("Soil_Gravel"), 100)
        }, Mode.BLOBS, 1.5f, "Tundra", 0x507050);
    
    public static final HytaleTerrain ZONE4_JUNGLE = new HytaleTerrain("Z4: Jungle",
        new Row[] {
            new Row(HytaleBlock.of("Soil_Grass_Deep"), 500),
            new Row(HytaleBlock.of("Soil_Mud"), 300),
            new Row(HytaleBlock.of("Soil_Dirt"), 200)
        }, Mode.BLOBS, 1.5f, "Tropical", 0x1a6030);
    
    public static final HytaleTerrain ZONE4_ASH_DESERT = new HytaleTerrain("Z4: Ash Desert",
        new Row[] {
            new Row(HytaleBlock.of("Soil_Sand_Ashen"), 500),
            new Row(HytaleBlock.of("Rock_Volcanic"), 300),
            new Row(HytaleBlock.of("Soil_Dirt_Burnt"), 200)
        }, Mode.BLOBS, 2.0f, "Volcanic", 0x505040);
    
    public static final HytaleTerrain OCEAN_AQUA = new HytaleTerrain("Ocean Reef",
        new Row[] {
            new Row(HytaleBlock.of("Rock_Aqua"), 500),
            new Row(HytaleBlock.of("Soil_Sand"), 300),
            new Row(HytaleBlock.of("Soil_Clay"), 200)
        }, Mode.BLOBS, 2.0f, "Ocean", 0x50a0b0);
    
    // ===== PICK LIST (ordered for user-facing display) =====
    
    /** Array of terrains available for user selection. Ordered logically by category. */
    public static final HytaleTerrain[] PICK_LIST = {
        // Soil / Surface — Grass variants
        GRASS, GRASS_FULL, GRASS_SUNNY, GRASS_DRY, GRASS_COLD,
        GRASS_BURNT, GRASS_DEEP, GRASS_WET,
        // Soil / Surface — Dirt variants
        DIRT, DIRT_BURNT, DIRT_COLD, DIRT_DRY, DIRT_POISONED,
        // Sand, Snow, Gravel, Clay, Mud
        SAND, SAND_RED, SAND_WHITE, SAND_ASHEN,
        SNOW, GRAVEL, GRAVEL_MOSSY,
        CLAY, MUD, MUD_DRY, LEAVES_FLOOR,
        // Rock
        STONE, STONE_MOSSY, COBBLESTONE, COBBLESTONE_MOSSY,
        SANDSTONE, SANDSTONE_RED, SANDSTONE_WHITE,
        SHALE, SLATE, BASALT, AQUA_ROCK,
        MARBLE, QUARTZITE, CALCITE, CHALK, SALT,
        VOLCANIC, MAGMA_COOLED, ICE, ICE_PERMAFROST, BEDROCK,
        // Zone 1 — Temperate
        ZONE1_GRASSLAND, ZONE1_FOREST_FLOOR, ZONE1_MEADOW, ZONE1_AUTUMN,
        ZONE1_BEACH, ZONE1_RIVERBED,
        // Zone 2 — Arid
        ZONE2_DESERT, ZONE2_RED_DESERT, ZONE2_MESA, ZONE2_PLATEAU,
        ZONE2_OASIS, ZONE2_SAVANNA,
        // Zone 3 — Boreal
        ZONE3_TUNDRA, ZONE3_TAIGA, ZONE3_BOREAL_FOREST,
        ZONE3_FROZEN_LAKE, ZONE3_SNOWY_PEAKS,
        // Zone 4 — Volcanic
        ZONE4_VOLCANIC_PLAINS, ZONE4_LAVA_FIELDS, ZONE4_ASH_WASTE,
        ZONE4_ASH_DESERT, ZONE4_JUNGLE,
        // Mixed natural
        STONE_MIX, MOUNTAIN_ROCK, SWAMP, TROPICAL_BEACH, OCEAN_FLOOR, OCEAN_AQUA,
        // Layered
        GRASSLAND_LAYERED, DESERT_LAYERED, TUNDRA_LAYERED,
        // Fluids
        WATER, LAVA
    };
    
    // ----- Static factory methods -----
    
    /**
     * Get all default Hytale terrains (the PICK_LIST as a modifiable list).
     */
    public static List<HytaleTerrain> getDefaultTerrains() {
        return new ArrayList<>(Arrays.asList(PICK_LIST));
    }
    
    /**
     * Look up a terrain by name (case-insensitive).
     * 
     * @param name The display name to search for
     * @return The matching terrain, or null if not found
     */
    public static HytaleTerrain getByName(String name) {
        if (name == null) return null;
        for (HytaleTerrain t : PICK_LIST) {
            if (t.getName().equalsIgnoreCase(name)) {
                return t;
            }
        }
        return null;
    }
    
    /**
     * Get terrains filtered by biome.
     * 
     * @param biome The biome to filter by (e.g. "Desert", "Tundra")
     * @return List of terrains associated with that biome
     */
    public static List<HytaleTerrain> getTerrainsByBiome(String biome) {
        List<HytaleTerrain> result = new ArrayList<>();
        for (HytaleTerrain t : PICK_LIST) {
            if (biome.equals(t.getBiome())) {
                result.add(t);
            }
        }
        return result;
    }
    
    // ----- Inner classes -----
    
    /**
     * A row in the terrain definition, containing a block and its occurrence/height.
     */
    public static final class Row implements Serializable {
        private static final long serialVersionUID = 1L;
        
        public final HytaleBlock block;
        public final int occurrence; // Parts per 1000 for NOISE/BLOBS, layer height for LAYERED
        
        public Row(HytaleBlock block, int occurrence) {
            this.block = Objects.requireNonNull(block);
            this.occurrence = occurrence;
        }
        
        @Override
        public String toString() {
            return block.id + " (" + occurrence + ")";
        }
    }
    
    /**
     * The mode of block selection for mixed terrains.
     */
    public enum Mode {
        /** Single block, or always use the first row. */
        SIMPLE,
        /** Random selection based on occurrence weights. */
        NOISE,
        /** 3D blob regions of each block type. */
        BLOBS,
        /** Vertical layers, occurrence = layer thickness. */
        LAYERED
    }
}
