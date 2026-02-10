package org.pepsoft.worldpainter.hytale;

import org.pepsoft.worldpainter.ColourScheme;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

/**
 * A terrain type for Hytale worlds. Each terrain represents a single Hytale block.
 * Builders can create their own mixed terrains via the custom terrain system.
 * 
 * <p>Grass colour variation is handled by the biome tinting system, not by
 * separate grass terrain types.
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
    
    /** The single block this terrain places. */
    private HytaleBlock block;
    
    /** Custom colour for display, or null to use block colour. */
    private Integer colour;
    
    /** Cached icon. */
    private transient BufferedImage icon;
    private transient Map<Integer, BufferedImage> scaledIconCache;
    
    /** Path to HytaleAssets for texture loading (set once at startup). */
    private static File hytaleAssetsDir;

    private static final int ICON_SIZE = 32;
    private static final float TOP_FACE_SHADE = 1.0f;
    private static final float LEFT_FACE_SHADE = 0.90f;
    private static final float RIGHT_FACE_SHADE = 0.74f;
    
    /**
     * Create a simple single-block terrain with a deterministic UUID based on the name.
     */
    public HytaleTerrain(String name, HytaleBlock block, Integer colour) {
        this.id = UUID.nameUUIDFromBytes(("HytaleTerrain:" + name).getBytes(StandardCharsets.UTF_8));
        this.name = name;
        this.block = block;
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
    
    public HytaleBlock getBlock() {
        return block;
    }
    
    public void setBlock(HytaleBlock block) {
        this.block = block;
        icon = null;
        scaledIconCache = null;
    }
    
    public Integer getColour() {
        return colour;
    }
    
    public void setColour(Integer colour) {
        this.colour = colour;
        icon = null;
        scaledIconCache = null;
    }
    
    /**
     * Get the primary block for this terrain.
     */
    public HytaleBlock getPrimaryBlock() {
        return block != null ? block : HytaleBlock.EMPTY;
    }
    
    /**
     * Get the block for any position. Always returns the single block.
     */
    public HytaleBlock getBlock(long seed, int x, int y, int z) {
        return block != null ? block : HytaleBlock.EMPTY;
    }
    
    /**
     * Set the directory where HytaleAssets are located, for loading block textures.
     */
    public static void setHytaleAssetsDir(File dir) {
        hytaleAssetsDir = dir;
    }
    
    /**
     * Get an icon for this terrain, preferring the actual block texture from HytaleAssets.
     */
    public BufferedImage getIcon(ColourScheme colourScheme) {
        if (icon == null) {
            icon = loadOrCreateIcon();
        }
        return icon;
    }

    /**
     * Get this icon scaled to a target size in pixels.
     */
    public BufferedImage getScaledIcon(int size, ColourScheme colourScheme) {
        if (size <= 0) {
            throw new IllegalArgumentException("size must be > 0");
        }
        BufferedImage base = getIcon(colourScheme);
        if ((base == null) || ((base.getWidth() == size) && (base.getHeight() == size))) {
            return base;
        }
        if (scaledIconCache == null) {
            scaledIconCache = new HashMap<>();
        }
            BufferedImage scaled = scaledIconCache.get(size);
            if (scaled == null) {
                scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = scaled.createGraphics();
                try {
                    // Keep block icons crisp at all sizes.
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                    g2.drawImage(base, 0, 0, size, size, null);
                } finally {
                    g2.dispose();
                }
                scaledIconCache.put(size, scaled);
        }
        return scaled;
    }
    
    private BufferedImage loadOrCreateIcon() {
        BlockFaceTextures textures = loadBlockFaceTextures();
        if (textures != null) {
            BufferedImage topTexture = (textures.top != null) ? textures.top : textures.side;
            BufferedImage sideTexture = (textures.side != null) ? textures.side : textures.top;
            if ((topTexture != null) && (sideTexture != null)) {
                return renderIsometricIcon(topTexture, sideTexture);
            }
        }

        // Keep a texture-based fallback when possible (instead of a flat colour swatch).
        BufferedImage fallbackTexture = loadFallbackTexture();
        if (fallbackTexture != null) {
            return renderIsometricIcon(fallbackTexture, fallbackTexture);
        }

        // Final fallback when no texture assets are available.
        return createColourIcon();
    }
    
    /**
     * Try to load top and side textures for this block from HytaleAssets.
     */
    private BlockFaceTextures loadBlockFaceTextures() {
        File texturesDir = getTexturesDir();
        if ((texturesDir == null) || (block == null)) {
            return null;
        }

        Map<String, File> textureIndex = indexTextureFiles(texturesDir);
        String blockId = block.id;
        List<String> topCandidates = new ArrayList<>();
        List<String> sideCandidates = new ArrayList<>();
        addTextureCandidates(blockId, topCandidates, sideCandidates);

        // Fluids are typically named Fluid_Water / Fluid_Lava in the assets.
        if (blockId.endsWith("_Source")) {
            String fluidName = "Fluid_" + blockId.substring(0, blockId.length() - "_Source".length());
            topCandidates.add(fluidName + ".png");
            sideCandidates.add(fluidName + ".png");
        }

        for (String prefix : new String[] { "Rock_", "Soil_" }) {
            if (blockId.startsWith(prefix)) {
                addTextureCandidates(blockId.substring(prefix.length()), topCandidates, sideCandidates);
            }
        }

        // Common spelling mismatch in assets (Poisoned vs Poisonned).
        if (blockId.contains("Poisoned")) {
            addTextureCandidates(blockId.replace("Poisoned", "Poisonned"), topCandidates, sideCandidates);
        }

        BufferedImage topTexture = loadTexture(textureIndex, topCandidates);
        BufferedImage sideTexture = loadTexture(textureIndex, sideCandidates);
        if ((topTexture == null) && (sideTexture == null)) {
            return null;
        }
        return new BlockFaceTextures(topTexture, sideTexture);
    }

    private BufferedImage loadFallbackTexture() {
        File texturesDir = getTexturesDir();
        if (texturesDir == null) {
            return null;
        }
        Map<String, File> textureIndex = indexTextureFiles(texturesDir);
        return loadTexture(textureIndex, Arrays.asList(
                "EditorBlock.png",
                "Rock_Stone.png",
                "Soil_Dirt.png",
                "Chalk.png",
                "Calcite.png"
        ));
    }

    private File getTexturesDir() {
        if ((hytaleAssetsDir == null) || (block == null)) {
            return null;
        }
        File texturesDir = new File(hytaleAssetsDir, "Common" + File.separator + "BlockTextures");
        return texturesDir.isDirectory() ? texturesDir : null;
    }

    private Map<String, File> indexTextureFiles(File texturesDir) {
        File[] files = texturesDir.listFiles();
        if (files == null) {
            return Collections.emptyMap();
        }
        Map<String, File> index = new HashMap<>(files.length * 2);
        for (File file : files) {
            if (file.isFile()) {
                index.putIfAbsent(file.getName().toLowerCase(Locale.ROOT), file);
            }
        }
        return index;
    }

    private void addTextureCandidates(String base, List<String> topCandidates, List<String> sideCandidates) {
        topCandidates.add(base + "_Top.png");
        topCandidates.add(base + ".png");
        topCandidates.add(base + "_Top_GS.png");
        topCandidates.add(base + "_GS.png");

        sideCandidates.add(base + "_Side.png");
        sideCandidates.add(base + ".png");
        sideCandidates.add(base + "_Side_GS.png");
        sideCandidates.add(base + "_Side_Full_GS.png");
        sideCandidates.add(base + "_GS.png");
    }

    private BufferedImage loadTexture(Map<String, File> textureIndex, List<String> candidates) {
        for (String candidate : candidates) {
            File textureFile = textureIndex.get(candidate.toLowerCase(Locale.ROOT));
            if ((textureFile != null) && textureFile.isFile()) {
                try {
                    BufferedImage image = ImageIO.read(textureFile);
                    if (image != null) {
                        if (candidate.toLowerCase(Locale.ROOT).contains("_gs")) {
                            image = tintGreyscale(image, getEffectiveColour());
                        }
                        return image;
                    }
                } catch (IOException e) {
                    // Try the next candidate.
                }
            }
        }
        return null;
    }

    private BufferedImage renderIsometricIcon(BufferedImage topTexture, BufferedImage sideTexture) {
        BufferedImage iconImage = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
        final int size = ICON_SIZE;
        final int margin = Math.max(1, Math.round(size * 0.04f));
        final int midX = size / 2;
        final int topY = margin;
        final int halfTopWidth = Math.max(1, Math.round(size * 0.44f));
        final int halfTopHeight = Math.max(1, Math.round(size * 0.22f));
        int faceHeight = Math.max(1, Math.round(size * 0.43f));
        int maxFaceHeight = size - margin - (topY + (halfTopHeight * 2));
        faceHeight = Math.max(1, Math.min(faceHeight, maxFaceHeight));

        final Point2D.Float top = new Point2D.Float(midX, topY);
        final Point2D.Float left = new Point2D.Float(midX - halfTopWidth, topY + halfTopHeight);
        final Point2D.Float right = new Point2D.Float(midX + halfTopWidth, topY + halfTopHeight);
        final Point2D.Float front = new Point2D.Float(midX, topY + (2.0f * halfTopHeight));
        final Point2D.Float leftBottom = new Point2D.Float(left.x, left.y + faceHeight);
        final Point2D.Float frontBottom = new Point2D.Float(front.x, front.y + faceHeight);
        final Point2D.Float rightBottom = new Point2D.Float(right.x, right.y + faceHeight);

        // Draw side faces first, then top face.
        drawTexturedParallelogram(iconImage, sideTexture, left, front, leftBottom, LEFT_FACE_SHADE);
        drawTexturedParallelogram(iconImage, sideTexture, front, right, frontBottom, RIGHT_FACE_SHADE);
        drawTexturedParallelogram(iconImage, topTexture, left, top, front, TOP_FACE_SHADE);

        // Add subtle outlines to improve readability at small sizes.
        Graphics2D g2 = iconImage.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g2.setColor(new Color(0, 0, 0, 110));
            drawEdge(g2, left, top);
            drawEdge(g2, top, right);
            drawEdge(g2, right, front);
            drawEdge(g2, front, left);
            drawEdge(g2, left, leftBottom);
            drawEdge(g2, front, frontBottom);
            drawEdge(g2, right, rightBottom);
            drawEdge(g2, leftBottom, frontBottom);
            drawEdge(g2, frontBottom, rightBottom);
        } finally {
            g2.dispose();
        }
        return iconImage;
    }

    private void drawTexturedParallelogram(BufferedImage target, BufferedImage texture, Point2D.Float p00, Point2D.Float p10, Point2D.Float p01, float shade) {
        Point2D.Float p11 = new Point2D.Float(p10.x + p01.x - p00.x, p10.y + p01.y - p00.y);
        float minX = Math.min(Math.min(p00.x, p10.x), Math.min(p01.x, p11.x));
        float minY = Math.min(Math.min(p00.y, p10.y), Math.min(p01.y, p11.y));
        float maxX = Math.max(Math.max(p00.x, p10.x), Math.max(p01.x, p11.x));
        float maxY = Math.max(Math.max(p00.y, p10.y), Math.max(p01.y, p11.y));

        float ax = p10.x - p00.x, ay = p10.y - p00.y;
        float bx = p01.x - p00.x, by = p01.y - p00.y;
        float det = (ax * by) - (ay * bx);
        if (Math.abs(det) < 0.00001f) {
            return;
        }

        int texW = texture.getWidth();
        int texH = texture.getHeight();
        for (int y = Math.max(0, (int) Math.floor(minY) - 1); y <= Math.min(target.getHeight() - 1, (int) Math.ceil(maxY) + 1); y++) {
            for (int x = Math.max(0, (int) Math.floor(minX) - 1); x <= Math.min(target.getWidth() - 1, (int) Math.ceil(maxX) + 1); x++) {
                float px = x + 0.5f - p00.x;
                float py = y + 0.5f - p00.y;

                float u = ((px * by) - (py * bx)) / det;
                float v = ((py * ax) - (px * ay)) / det;
                if ((u < -0.001f) || (u > 1.001f) || (v < -0.001f) || (v > 1.001f)) {
                    continue;
                }
                u = Math.max(0.0f, Math.min(1.0f, u));
                v = Math.max(0.0f, Math.min(1.0f, v));

                int sx = Math.min(texW - 1, Math.max(0, Math.round(u * (texW - 1))));
                int sy = Math.min(texH - 1, Math.max(0, Math.round(v * (texH - 1))));
                int argb = shade(texture.getRGB(sx, sy), shade);
                plotPixel(target, x, y, argb);
            }
        }
    }

    private void drawEdge(Graphics2D g2, Point2D.Float from, Point2D.Float to) {
        g2.drawLine(Math.round(from.x), Math.round(from.y), Math.round(to.x), Math.round(to.y));
    }

    private void plotPixel(BufferedImage target, int x, int y, int argb) {
        if (((argb >>> 24) == 0) || (x < 0) || (y < 0) || (x >= target.getWidth()) || (y >= target.getHeight())) {
            return;
        }
        target.setRGB(x, y, argb);
    }

    private int shade(int argb, float shade) {
        int alpha = (argb >>> 24) & 0xff;
        if (alpha == 0) {
            return 0;
        }
        int red = Math.min(255, Math.round(((argb >>> 16) & 0xff) * shade));
        int green = Math.min(255, Math.round(((argb >>> 8) & 0xff) * shade));
        int blue = Math.min(255, Math.round((argb & 0xff) * shade));
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static final class BlockFaceTextures {
        private BlockFaceTextures(BufferedImage top, BufferedImage side) {
            this.top = top;
            this.side = side;
        }

        private final BufferedImage top;
        private final BufferedImage side;
    }

    /**
     * Apply a colour tint to a greyscale texture by multiplying each pixel by the tint.
     */
    private BufferedImage tintGreyscale(BufferedImage gs, int tintRgb) {
        int w = gs.getWidth(), h = gs.getHeight();
        BufferedImage tinted = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        float tr = ((tintRgb >> 16) & 0xFF) / 255f;
        float tg = ((tintRgb >> 8) & 0xFF) / 255f;
        float tb = (tintRgb & 0xFF) / 255f;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int pixel = gs.getRGB(x, y);
                int a = (pixel >> 24) & 0xFF;
                int r = Math.min(255, (int)(((pixel >> 16) & 0xFF) * tr));
                int g = Math.min(255, (int)(((pixel >> 8) & 0xFF) * tg));
                int b = Math.min(255, (int)((pixel & 0xFF) * tb));
                tinted.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return tinted;
    }
    
    private BufferedImage createColourIcon() {
        int size = ICON_SIZE;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        int rgb = getEffectiveColour();
        g2d.setColor(new Color(rgb));
        g2d.fillRect(0, 0, size, size);
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
        return getBlockColour(getPrimaryBlock());
    }
    
    /**
     * Get a default colour for a Hytale block based on its ID.
     */
    static int getBlockColour(HytaleBlock block) {
        String id = block.id;
        
        // Rock colours
        if (id.startsWith("Rock_Stone_Cobble_Mossy")) return 0x607850;
        if (id.startsWith("Rock_Stone_Cobble")) return 0x787878;
        if (id.startsWith("Rock_Stone_Mossy")) return 0x607850;
        if (id.startsWith("Rock_Stone")) return 0x808080;
        if (id.startsWith("Rock_Bedrock")) return 0x2d2d2d;
        if (id.startsWith("Rock_Ice_Permafrost")) return 0x90b8c8;
        if (id.startsWith("Rock_Ice")) return 0xa0d0ff;
        if (id.startsWith("Rock_Sandstone_Red")) return 0xb45030;
        if (id.startsWith("Rock_Sandstone_White")) return 0xe8e0d0;
        if (id.startsWith("Rock_Sandstone")) return 0xd4c099;
        if (id.startsWith("Rock_Shale")) return 0x5a5a5a;
        if (id.startsWith("Rock_Slate")) return 0x4a4a4a;
        if (id.startsWith("Rock_Basalt")) return 0x3a3a3a;
        if (id.startsWith("Rock_Marble")) return 0xf0f0f0;
        if (id.startsWith("Rock_Quartzite")) return 0xe0e0e0;
        if (id.startsWith("Rock_Calcite")) return 0xdbd7ca;
        if (id.startsWith("Rock_Chalk")) return 0xfafafa;
        if (id.startsWith("Rock_Volcanic")) return 0x2a2a2a;
        if (id.startsWith("Rock_Magma_Cooled")) return 0x1a0a0a;
        if (id.startsWith("Rock_Aqua")) return 0x50a0b0;
        if (id.startsWith("Rock_Salt")) return 0xf0e8e0;
        if (id.startsWith("Rock_")) return 0x707070;
        
        // Soil colours
        if (id.equals("Soil_Grass") || id.equals("Soil_Grass_Full")) return 0x59a52c;
        if (id.startsWith("Soil_Leaves")) return 0x5a7a30;
        if (id.startsWith("Soil_Sand_Ashen") || id.startsWith("Soil_Ashes")) return 0x808070;
        if (id.startsWith("Soil_Dirt_Burnt")) return 0x5a3a1b;
        if (id.startsWith("Soil_Dirt_Cold")) return 0x6b5a4b;
        if (id.startsWith("Soil_Dirt_Dry")) return 0x9b6a3b;
        if (id.startsWith("Soil_Dirt_Poisoned") || id.startsWith("Soil_Dirt_Poisonned")) return 0x4a5a2b;
        if (id.startsWith("Soil_Dirt")) return 0x8b5a2b;
        if (id.startsWith("Soil_Sand_Red")) return 0xc4633c;
        if (id.startsWith("Soil_Sand_White")) return 0xf4e8c6;
        if (id.startsWith("Soil_Sand")) return 0xdbc497;
        if (id.startsWith("Soil_Gravel_Mossy")) return 0x708060;
        if (id.startsWith("Soil_Gravel")) return 0x909090;
        if (id.startsWith("Soil_Clay")) return 0x9ea4ae;
        if (id.startsWith("Soil_Mud_Dry")) return 0x7a6a4a;
        if (id.startsWith("Soil_Mud")) return 0x5a4a3a;
        if (id.startsWith("Soil_Snow")) return 0xfffafa;
        if (id.startsWith("Soil_")) return 0x8b5a2b;
        
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
        scaledIconCache = null;
    }
    
    /**
     * On deserialization, substitute the canonical static instance if one exists
     * with the same UUID, so that identity checks (==) work correctly.
     */
    private Object readResolve() throws ObjectStreamException {
        for (HytaleTerrain t : ALL_TERRAINS) {
            if (t.id.equals(this.id)) {
                return t;
            }
        }
        return this;
    }
    
    // ----- Static terrains: fundamental single-block terrains only -----
    // Builders create their own mixed/layered terrains via the custom terrain system.
    // Grass colour variation is per-biome (tinting), so only one grass terrain is needed.
    
    // ===== SOIL / SURFACE =====
    public static final HytaleTerrain GRASS = new HytaleTerrain("Grass",
        HytaleBlock.of("Soil_Grass"), 0x59a52c);
    
    public static final HytaleTerrain DIRT = new HytaleTerrain("Dirt",
        HytaleBlock.of("Soil_Dirt"), 0x8b5a2b);
    
    public static final HytaleTerrain DIRT_BURNT = new HytaleTerrain("Burnt Dirt",
        HytaleBlock.of("Soil_Dirt_Burnt"), 0x5a3a1b);
    
    public static final HytaleTerrain DIRT_COLD = new HytaleTerrain("Cold Dirt",
        HytaleBlock.of("Soil_Dirt_Cold"), 0x6b5a4b);
    
    public static final HytaleTerrain DIRT_DRY = new HytaleTerrain("Dry Dirt",
        HytaleBlock.of("Soil_Dirt_Dry"), 0x9b6a3b);
    
    public static final HytaleTerrain DIRT_POISONED = new HytaleTerrain("Poisoned Dirt",
        HytaleBlock.of("Soil_Dirt_Poisoned"), 0x4a5a2b);
    
    public static final HytaleTerrain SAND = new HytaleTerrain("Sand",
        HytaleBlock.of("Soil_Sand"), 0xdbc497);
    
    public static final HytaleTerrain SAND_RED = new HytaleTerrain("Red Sand",
        HytaleBlock.of("Soil_Sand_Red"), 0xc4633c);
    
    public static final HytaleTerrain SAND_WHITE = new HytaleTerrain("White Sand",
        HytaleBlock.of("Soil_Sand_White"), 0xf4e8c6);
    
    public static final HytaleTerrain SAND_ASHEN = new HytaleTerrain("Ashen Sand",
        HytaleBlock.of("Soil_Sand_Ashen"), 0x808070);
    
    public static final HytaleTerrain SNOW = new HytaleTerrain("Snow",
        HytaleBlock.of("Soil_Snow"), 0xfffafa);
    
    public static final HytaleTerrain GRAVEL = new HytaleTerrain("Gravel",
        HytaleBlock.of("Soil_Gravel"), 0x909090);
    
    public static final HytaleTerrain GRAVEL_MOSSY = new HytaleTerrain("Mossy Gravel",
        HytaleBlock.of("Soil_Gravel_Mossy"), 0x708060);
    
    public static final HytaleTerrain CLAY = new HytaleTerrain("Clay",
        HytaleBlock.of("Soil_Clay"), 0x9ea4ae);
    
    public static final HytaleTerrain MUD = new HytaleTerrain("Mud",
        HytaleBlock.of("Soil_Mud"), 0x5a4a3a);
    
    public static final HytaleTerrain MUD_DRY = new HytaleTerrain("Dry Mud",
        HytaleBlock.of("Soil_Mud_Dry"), 0x7a6a4a);
    
    public static final HytaleTerrain LEAVES_FLOOR = new HytaleTerrain("Leaf Litter",
        HytaleBlock.of("Soil_Leaves"), 0x5a7a30);
    
    // ===== ROCK =====
    public static final HytaleTerrain STONE = new HytaleTerrain("Stone",
        HytaleBlock.of("Rock_Stone"), 0x808080);
    
    public static final HytaleTerrain STONE_MOSSY = new HytaleTerrain("Mossy Stone",
        HytaleBlock.of("Rock_Stone_Mossy"), 0x607850);
    
    public static final HytaleTerrain COBBLESTONE = new HytaleTerrain("Cobblestone",
        HytaleBlock.of("Rock_Stone_Cobble"), 0x787878);
    
    public static final HytaleTerrain COBBLESTONE_MOSSY = new HytaleTerrain("Mossy Cobblestone",
        HytaleBlock.of("Rock_Stone_Cobble_Mossy"), 0x607850);
    
    public static final HytaleTerrain SANDSTONE = new HytaleTerrain("Sandstone",
        HytaleBlock.of("Rock_Sandstone"), 0xd4c099);
    
    public static final HytaleTerrain SANDSTONE_RED = new HytaleTerrain("Red Sandstone",
        HytaleBlock.of("Rock_Sandstone_Red"), 0xb45030);
    
    public static final HytaleTerrain SANDSTONE_WHITE = new HytaleTerrain("White Sandstone",
        HytaleBlock.of("Rock_Sandstone_White"), 0xe8e0d0);
    
    public static final HytaleTerrain SHALE = new HytaleTerrain("Shale",
        HytaleBlock.of("Rock_Shale"), 0x5a5a5a);
    
    public static final HytaleTerrain SLATE = new HytaleTerrain("Slate",
        HytaleBlock.of("Rock_Slate"), 0x4a4a4a);
    
    public static final HytaleTerrain BASALT = new HytaleTerrain("Basalt",
        HytaleBlock.of("Rock_Basalt"), 0x3a3a3a);
    
    public static final HytaleTerrain AQUA_ROCK = new HytaleTerrain("Aqua Rock",
        HytaleBlock.of("Rock_Aqua"), 0x50a0b0);
    
    public static final HytaleTerrain MARBLE = new HytaleTerrain("Marble",
        HytaleBlock.of("Rock_Marble"), 0xf0f0f0);
    
    public static final HytaleTerrain QUARTZITE = new HytaleTerrain("Quartzite",
        HytaleBlock.of("Rock_Quartzite"), 0xe0e0e0);
    
    public static final HytaleTerrain CALCITE = new HytaleTerrain("Calcite",
        HytaleBlock.of("Rock_Calcite"), 0xdbd7ca);
    
    public static final HytaleTerrain CHALK = new HytaleTerrain("Chalk",
        HytaleBlock.of("Rock_Chalk"), 0xfafafa);
    
    public static final HytaleTerrain SALT = new HytaleTerrain("Salt",
        HytaleBlock.of("Rock_Salt"), 0xf0e8e0);
    
    public static final HytaleTerrain VOLCANIC = new HytaleTerrain("Volcanic Rock",
        HytaleBlock.of("Rock_Volcanic"), 0x2a2a2a);
    
    public static final HytaleTerrain MAGMA_COOLED = new HytaleTerrain("Cooled Magma",
        HytaleBlock.of("Rock_Magma_Cooled"), 0x1a0a0a);
    
    public static final HytaleTerrain ICE = new HytaleTerrain("Ice",
        HytaleBlock.of("Rock_Ice"), 0xa0d0ff);
    
    public static final HytaleTerrain ICE_PERMAFROST = new HytaleTerrain("Permafrost",
        HytaleBlock.of("Rock_Ice_Permafrost"), 0x90b8c8);
    
    public static final HytaleTerrain BEDROCK = new HytaleTerrain("Bedrock",
        HytaleBlock.of("Rock_Bedrock"), 0x2d2d2d);
    
    // ===== FLUIDS =====
    public static final HytaleTerrain WATER = new HytaleTerrain("Water",
        HytaleBlock.of("Water_Source"), 0x3366ff);
    
    public static final HytaleTerrain LAVA = new HytaleTerrain("Lava",
        HytaleBlock.of("Lava_Source"), 0xff4500);
    
    // ===== PICK LIST (ordered for user-facing display) =====
    
    /** Array of fundamental single-block terrains for user selection. */
    public static final HytaleTerrain[] PICK_LIST = {
        // Soil / Surface
        GRASS, DIRT, SAND, SNOW,
        GRAVEL, CLAY, MUD,
        // Rock (matches Rock.json BlockTypeList)
        STONE, COBBLESTONE,
        SANDSTONE, SANDSTONE_RED, SANDSTONE_WHITE,
        SHALE, SLATE, BASALT, AQUA_ROCK,
        MARBLE, QUARTZITE, CALCITE, CHALK, SALT,
        VOLCANIC, ICE, BEDROCK,
        // Fluids
        WATER, LAVA
    };
    
    /** All defined terrains including variants (for lookup/backward compat). */
    private static final HytaleTerrain[] ALL_TERRAINS = {
        GRASS, DIRT, DIRT_BURNT, DIRT_COLD, DIRT_DRY, DIRT_POISONED,
        SAND, SAND_RED, SAND_WHITE, SAND_ASHEN,
        SNOW, GRAVEL, GRAVEL_MOSSY,
        CLAY, MUD, MUD_DRY, LEAVES_FLOOR,
        STONE, STONE_MOSSY, COBBLESTONE, COBBLESTONE_MOSSY,
        SANDSTONE, SANDSTONE_RED, SANDSTONE_WHITE,
        SHALE, SLATE, BASALT, AQUA_ROCK,
        MARBLE, QUARTZITE, CALCITE, CHALK, SALT,
        VOLCANIC, MAGMA_COOLED, ICE, ICE_PERMAFROST, BEDROCK,
        WATER, LAVA
    };
    
    /** Map from block ID to terrain for quick lookup (includes ALL terrains). */
    private static final Map<String, HytaleTerrain> BLOCK_ID_MAP;
    static {
        Map<String, HytaleTerrain> map = new HashMap<>();
        for (HytaleTerrain t : ALL_TERRAINS) {
            map.put(t.block.id, t);
        }
        BLOCK_ID_MAP = Collections.unmodifiableMap(map);
    }
    
    // ----- Static factory methods -----
    
    /**
     * Get all default Hytale terrains (the PICK_LIST as a modifiable list).
     */
    public static List<HytaleTerrain> getDefaultTerrains() {
        return new ArrayList<>(Arrays.asList(PICK_LIST));
    }
    
    /**
     * Look up a terrain by name (case-insensitive).
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
     * Look up a terrain by its block ID.
     */
    public static HytaleTerrain getByBlockId(String blockId) {
        return BLOCK_ID_MAP.get(blockId);
    }
    
    // ----- Inner classes -----
    // Row and Mode are kept for backward compatibility with serialized custom terrains
    
    /**
     * A row in a custom terrain definition (for builder-created mixed terrains).
     */
    public static final class Row implements Serializable {
        private static final long serialVersionUID = 1L;
        
        public final HytaleBlock block;
        public final int occurrence;
        
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
     * The mode of block selection for custom mixed terrains.
     */
    public enum Mode {
        SIMPLE,
        NOISE,
        BLOBS,
        LAYERED
    }
}
