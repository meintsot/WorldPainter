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
        String id = block.getId();
        
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
        if (id.startsWith("Rock_")) return 0x707070;
        
        // Soil colours
        if (id.startsWith("Soil_Grass_Lush")) return 0x00a000;
        if (id.startsWith("Soil_Grass")) return 0x59a52c;
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
    
    // ----- Static factory methods for common terrains -----
    
    /**
     * Get all default Hytale terrains.
     */
    public static List<HytaleTerrain> getDefaultTerrains() {
        List<HytaleTerrain> terrains = new ArrayList<>();
        
        // Soil terrains
        terrains.add(createSoilGrass());
        terrains.add(createSoilGrassLush());
        terrains.add(createSoilDirt());
        terrains.add(createSoilSand());
        terrains.add(createSoilSandRed());
        terrains.add(createSoilSnow());
        terrains.add(createSoilGravel());
        terrains.add(createSoilClay());
        terrains.add(createSoilMud());
        
        // Rock terrains
        terrains.add(createRockStone());
        terrains.add(createRockCobble());
        terrains.add(createRockSandstone());
        terrains.add(createRockBasalt());
        terrains.add(createRockBedrock());
        terrains.add(createRockIce());
        
        return terrains;
    }
    
    public static HytaleTerrain createSoilGrass() {
        return new HytaleTerrain("Grass", 
            new Row[] { new Row(HytaleBlock.of("Soil_Grass"), 1000) },
            Mode.SIMPLE, 1.0f, "Grassland", null);
    }
    
    public static HytaleTerrain createSoilGrassLush() {
        return new HytaleTerrain("Lush Grass", 
            new Row[] { new Row(HytaleBlock.of("Soil_Grass_Lush"), 1000) },
            Mode.SIMPLE, 1.0f, "Tropical", null);
    }
    
    public static HytaleTerrain createSoilDirt() {
        return new HytaleTerrain("Dirt", 
            new Row[] { new Row(HytaleBlock.of("Soil_Dirt"), 1000) },
            Mode.SIMPLE, 1.0f, null, null);
    }
    
    public static HytaleTerrain createSoilSand() {
        return new HytaleTerrain("Sand", 
            new Row[] { new Row(HytaleBlock.of("Soil_Sand"), 1000) },
            Mode.SIMPLE, 1.0f, "Desert", null);
    }
    
    public static HytaleTerrain createSoilSandRed() {
        return new HytaleTerrain("Red Sand", 
            new Row[] { new Row(HytaleBlock.of("Soil_Sand_Red"), 1000) },
            Mode.SIMPLE, 1.0f, "Desert", null);
    }
    
    public static HytaleTerrain createSoilSnow() {
        return new HytaleTerrain("Snow", 
            new Row[] { new Row(HytaleBlock.of("Soil_Snow"), 1000) },
            Mode.SIMPLE, 1.0f, "Tundra", null);
    }
    
    public static HytaleTerrain createSoilGravel() {
        return new HytaleTerrain("Gravel", 
            new Row[] { new Row(HytaleBlock.of("Soil_Gravel"), 1000) },
            Mode.SIMPLE, 1.0f, null, null);
    }
    
    public static HytaleTerrain createSoilClay() {
        return new HytaleTerrain("Clay", 
            new Row[] { new Row(HytaleBlock.of("Soil_Clay"), 1000) },
            Mode.SIMPLE, 1.0f, null, null);
    }
    
    public static HytaleTerrain createSoilMud() {
        return new HytaleTerrain("Mud", 
            new Row[] { new Row(HytaleBlock.of("Soil_Mud"), 1000) },
            Mode.SIMPLE, 1.0f, "Swamp", null);
    }
    
    public static HytaleTerrain createRockStone() {
        return new HytaleTerrain("Stone", 
            new Row[] { new Row(HytaleBlock.of("Rock_Stone"), 1000) },
            Mode.SIMPLE, 1.0f, null, null);
    }
    
    public static HytaleTerrain createRockCobble() {
        return new HytaleTerrain("Cobblestone", 
            new Row[] { new Row(HytaleBlock.of("Rock_Stone_Cobble"), 1000) },
            Mode.SIMPLE, 1.0f, null, null);
    }
    
    public static HytaleTerrain createRockSandstone() {
        return new HytaleTerrain("Sandstone", 
            new Row[] { new Row(HytaleBlock.of("Rock_Sandstone"), 1000) },
            Mode.SIMPLE, 1.0f, "Desert", null);
    }
    
    public static HytaleTerrain createRockBasalt() {
        return new HytaleTerrain("Basalt", 
            new Row[] { new Row(HytaleBlock.of("Rock_Basalt"), 1000) },
            Mode.SIMPLE, 1.0f, "Volcanic", null);
    }
    
    public static HytaleTerrain createRockBedrock() {
        return new HytaleTerrain("Bedrock", 
            new Row[] { new Row(HytaleBlock.of("Rock_Bedrock"), 1000) },
            Mode.SIMPLE, 1.0f, null, null);
    }
    
    public static HytaleTerrain createRockIce() {
        return new HytaleTerrain("Ice", 
            new Row[] { new Row(HytaleBlock.of("Rock_Ice"), 1000) },
            Mode.SIMPLE, 1.0f, "Tundra", null);
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
            return block.getId() + " (" + occurrence + ")";
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
