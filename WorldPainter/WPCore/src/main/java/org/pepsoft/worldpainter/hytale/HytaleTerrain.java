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

        // No block-specific texture found. Generate a solid-colour isometric cube
        // from the terrain's effective colour so every terrain gets a unique 3D icon
        // instead of a generic grey fallback.
        int rgb = getEffectiveColour();
        BufferedImage solidTexture = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        int argb = 0xFF000000 | (rgb & 0xFFFFFF);
        for (int py = 0; py < 4; py++) {
            for (int px = 0; px < 4; px++) {
                solidTexture.setRGB(px, py, argb);
            }
        }
        return renderIsometricIcon(solidTexture, solidTexture);
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
            addTextureCandidates(blockId.replace("Poisoned", "Poison"), topCandidates, sideCandidates);
        }

        // Colour name mismatches between block IDs and texture filenames.
        if (blockId.contains("Cyan")) {
            addTextureCandidates(blockId.replace("Cyan", "SkyBlue"), topCandidates, sideCandidates);
        }
        if (blockId.contains("Gray")) {
            addTextureCandidates(blockId.replace("Gray", "Grey"), topCandidates, sideCandidates);
        }

        // Try stripping Plant_ prefix (e.g. Plant_Moss_Block_* → Moss_Block_*).
        if (blockId.startsWith("Plant_")) {
            addTextureCandidates(blockId.substring("Plant_".length()), topCandidates, sideCandidates);
        }

        // Try stripping Crop_ segment (e.g. Plant_Crop_Mushroom_Block_* → Plant_Mushroom_Block_*).
        if (blockId.contains("_Crop_")) {
            addTextureCandidates(blockId.replace("_Crop_", "_"), topCandidates, sideCandidates);
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

    /** Cached texture file index — built once, reused for all terrains. */
    private static volatile Map<String, File> cachedTextureIndex;
    private static volatile File cachedTextureIndexDir;

    private Map<String, File> indexTextureFiles(File texturesDir) {
        // Return cached index if it was built for the same directory.
        if (cachedTextureIndex != null && texturesDir.equals(cachedTextureIndexDir)) {
            return cachedTextureIndex;
        }
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
        cachedTextureIndex = index;
        cachedTextureIndexDir = texturesDir;
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
        if (id.startsWith("Rock_Crystal_White")) return 0xf2f6ff;
        if (id.startsWith("Rock_Crystal_Yellow")) return 0xf0d24a;
        if (id.startsWith("Rock_Crystal_Red")) return 0xc84545;
        if (id.startsWith("Rock_Crystal_Pink")) return 0xe28fc6;
        if (id.startsWith("Rock_Crystal_Purple")) return 0x8b63c7;
        if (id.startsWith("Rock_Crystal_Cyan")) return 0x4fc7cd;
        if (id.startsWith("Rock_Crystal_Blue")) return 0x4d7fd6;
        if (id.startsWith("Rock_Crystal_Green")) return 0x5aa964;
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
    
    // ----- Static terrains: builder-team approved blocks only -----

    // ===== GROUND BLOCKS =====
    public static final HytaleTerrain ASHEN_SAND = new HytaleTerrain("Ashen Sand",
        HytaleBlock.of("Soil_Sand_Ashen"), 0x908870);
    public static final HytaleTerrain AQUA_COBBLE = new HytaleTerrain("Aqua Cobble",
        HytaleBlock.of("Rock_Aqua_Cobble"), 0x4090a0);
    public static final HytaleTerrain AQUA_STONE = new HytaleTerrain("Aqua Stone",
        HytaleBlock.of("Rock_Aqua"), 0x50a0b0);
    public static final HytaleTerrain BASALT = new HytaleTerrain("Basalt",
        HytaleBlock.of("Rock_Basalt"), 0x3a3a3a);
    public static final HytaleTerrain BASALT_COBBLE = new HytaleTerrain("Basalt Cobble",
        HytaleBlock.of("Rock_Basalt_Cobble"), 0x4a4046);
    public static final HytaleTerrain BLUE_CRYSTAL = new HytaleTerrain("Blue Crystal",
        HytaleBlock.of("Rock_Crystal_Blue_Block"), 0x4d7fd6);
    public static final HytaleTerrain BLUE_ICE = new HytaleTerrain("Blue Ice",
        HytaleBlock.of("Rock_Ice"), 0xa0d0ff);
    public static final HytaleTerrain BURNED_GRASS = new HytaleTerrain("Burned Grass",
        HytaleBlock.of("Soil_Grass_Burnt"), 0x5c4020);
    public static final HytaleTerrain CALCITE = new HytaleTerrain("Calcite",
        HytaleBlock.of("Rock_Calcite"), 0xdbd7ca);
    public static final HytaleTerrain CALCITE_COBBLE = new HytaleTerrain("Calcite Cobble",
        HytaleBlock.of("Rock_Calcite_Cobble"), 0xcbc7ba);
    public static final HytaleTerrain CHALK = new HytaleTerrain("Chalk",
        HytaleBlock.of("Rock_Chalk"), 0xf8f8fa);
    public static final HytaleTerrain COLD_GRASS = new HytaleTerrain("Cold Grass",
        HytaleBlock.of("Soil_Grass_Cold"), 0x5e855a);
    public static final HytaleTerrain COLD_MAGMA = new HytaleTerrain("Cold Magma",
        HytaleBlock.of("Rock_Magma_Cooled"), 0x180808);
    public static final HytaleTerrain CRACKED_SLATE = new HytaleTerrain("Cracked Slate",
        HytaleBlock.of("Rock_Slate_Cobble"), 0x484848);
    public static final HytaleTerrain CRACKED_VOLCANIC_ROCK = new HytaleTerrain("Cracked Volcanic Rock",
        HytaleBlock.of("Rock_Volcanic_Cracked_Lava"), 0x3a2020);
    public static final HytaleTerrain CYAN_CRYSTAL = new HytaleTerrain("Cyan Crystal",
        HytaleBlock.of("Rock_Crystal_Cyan_Block"), 0x4fc7cd);
    public static final HytaleTerrain DEEP_GRASS = new HytaleTerrain("Deep Grass",
        HytaleBlock.of("Soil_Grass_Deep"), 0x1f6e12);
    public static final HytaleTerrain DRY_GRASS = new HytaleTerrain("Dry Grass",
        HytaleBlock.of("Soil_Grass_Dry"), 0xa89940);
    public static final HytaleTerrain FULL_GRASS = new HytaleTerrain("Full Grass",
        HytaleBlock.of("Soil_Grass_Full"), 0x3cb820);
    public static final HytaleTerrain GRASS = new HytaleTerrain("Grass",
        HytaleBlock.of("Soil_Grass"), 0x59a52c);
    public static final HytaleTerrain GREEN_CRYSTAL = new HytaleTerrain("Green Crystal",
        HytaleBlock.of("Rock_Crystal_Green_Block"), 0x5aa964);
    public static final HytaleTerrain ICE = new HytaleTerrain("Ice",
        HytaleBlock.of("Rock_Ice"), 0xc0e0f8);
    public static final HytaleTerrain MARBLE = new HytaleTerrain("Marble",
        HytaleBlock.of("Rock_Marble"), 0xeeeef0);
    public static final HytaleTerrain MOSSY_STONE = new HytaleTerrain("Mossy Stone",
        HytaleBlock.of("Rock_Stone_Mossy"), 0x607850);
    public static final HytaleTerrain PINK_CRYSTAL = new HytaleTerrain("Pink Crystal",
        HytaleBlock.of("Rock_Crystal_Pink_Block"), 0xe28fc6);
    public static final HytaleTerrain POISONED_VOLCANIC_ROCK = new HytaleTerrain("Poisoned Volcanic Rock",
        HytaleBlock.of("Rock_Volcanic_Cracked_Poisoned"), 0x3a4a2a);
    public static final HytaleTerrain PURPLE_CRYSTAL = new HytaleTerrain("Purple Crystal",
        HytaleBlock.of("Rock_Crystal_Purple_Block"), 0x8b63c7);
    public static final HytaleTerrain QUARTZITE = new HytaleTerrain("Quartzite",
        HytaleBlock.of("Rock_Quartzite"), 0xdcdce0);
    public static final HytaleTerrain RED_CRYSTAL = new HytaleTerrain("Red Crystal",
        HytaleBlock.of("Rock_Crystal_Red_Block"), 0xc84545);
    public static final HytaleTerrain RED_SAND = new HytaleTerrain("Red Sand",
        HytaleBlock.of("Soil_Sand_Red"), 0xc4633c);
    public static final HytaleTerrain RED_SANDSTONE = new HytaleTerrain("Red Sandstone",
        HytaleBlock.of("Rock_Sandstone_Red"), 0xb45030);
    public static final HytaleTerrain RED_SANDSTONE_BRICK_SMOOTH = new HytaleTerrain("Red Sandstone Brick Smooth",
        HytaleBlock.of("Rock_Sandstone_Red_Brick_Smooth"), 0xbc4c34);
    public static final HytaleTerrain SALT_BLOCK = new HytaleTerrain("Salt Block",
        HytaleBlock.of("Rock_Salt"), 0xf0e8e0);
    public static final HytaleTerrain SAND = new HytaleTerrain("Sand",
        HytaleBlock.of("Soil_Sand"), 0xdbc497);
    public static final HytaleTerrain SANDSTONE = new HytaleTerrain("Sandstone",
        HytaleBlock.of("Rock_Sandstone"), 0xd4c099);
    public static final HytaleTerrain SANDSTONE_BRICK_SMOOTH = new HytaleTerrain("Sandstone Brick Smooth",
        HytaleBlock.of("Rock_Sandstone_Brick_Smooth"), 0xdcbc9d);
    public static final HytaleTerrain SHALE = new HytaleTerrain("Shale",
        HytaleBlock.of("Rock_Shale"), 0x5a5a65);
    public static final HytaleTerrain SHALE_COBBLE = new HytaleTerrain("Shale Cobble",
        HytaleBlock.of("Rock_Shale_Cobble"), 0x686060);
    public static final HytaleTerrain SLATE = new HytaleTerrain("Slate",
        HytaleBlock.of("Rock_Slate"), 0x505058);
    public static final HytaleTerrain SLATE_COBBLE = new HytaleTerrain("Slate Cobble",
        HytaleBlock.of("Rock_Slate_Cobble"), 0x585c56);
    public static final HytaleTerrain STONE = new HytaleTerrain("Stone",
        HytaleBlock.of("Rock_Stone"), 0x808080);
    public static final HytaleTerrain SUMMER_GRASS = new HytaleTerrain("Summer Grass",
        HytaleBlock.of("Soil_Grass_Sunny"), 0x8abf30);
    public static final HytaleTerrain VOLCANIC_ROCK = new HytaleTerrain("Volcanic Rock",
        HytaleBlock.of("Rock_Volcanic"), 0x2e282c);
    public static final HytaleTerrain WET_GRASS = new HytaleTerrain("Wet Grass",
        HytaleBlock.of("Soil_Grass_Wet"), 0x38964a);
    public static final HytaleTerrain WHITE_CRYSTAL = new HytaleTerrain("White Crystal",
        HytaleBlock.of("Rock_Crystal_White_Block"), 0xf2f6ff);
    public static final HytaleTerrain WHITE_SAND = new HytaleTerrain("White Sand",
        HytaleBlock.of("Soil_Sand_White"), 0xf4e8c6);
    public static final HytaleTerrain WHITE_SANDSTONE = new HytaleTerrain("White Sandstone",
        HytaleBlock.of("Rock_Sandstone_White"), 0xe8e0d0);
    public static final HytaleTerrain WHITE_SANDSTONE_BRICK_SMOOTH = new HytaleTerrain("White Sandstone Brick Smooth",
        HytaleBlock.of("Rock_Sandstone_White_Brick_Smooth"), 0xf0dcd4);
    public static final HytaleTerrain YELLOW_CRYSTAL = new HytaleTerrain("Yellow Crystal",
        HytaleBlock.of("Rock_Crystal_Yellow_Block"), 0xf0d24a);

    // ===== LEAVES =====
    public static final HytaleTerrain AMBER_LEAVES = new HytaleTerrain("Amber Leaves",
        HytaleBlock.of("Plant_Leaves_Amber"), 0x994c4c);
    public static final HytaleTerrain ARID_PALM_LEAVES = new HytaleTerrain("Arid Palm Leaves",
        HytaleBlock.of("Plant_Leaves_Palm_Arid"), 0x506043);
    public static final HytaleTerrain ASH_LEAVES = new HytaleTerrain("Ash Leaves",
        HytaleBlock.of("Plant_Leaves_Ash"), 0x738059);
    public static final HytaleTerrain ASPEN_LEAVES = new HytaleTerrain("Aspen Leaves",
        HytaleBlock.of("Plant_Leaves_Aspen"), 0x619f50);
    public static final HytaleTerrain AUTUMN_LEAVES = new HytaleTerrain("Autumn Leaves",
        HytaleBlock.of("Plant_Leaves_Autumn"), 0xb26541);
    public static final HytaleTerrain AZURE_LEAVES = new HytaleTerrain("Azure Leaves",
        HytaleBlock.of("Plant_Leaves_Azure"), 0x4a6d9e);
    public static final HytaleTerrain BAMBOO_LEAVES = new HytaleTerrain("Bamboo Leaves",
        HytaleBlock.of("Plant_Leaves_Bamboo"), 0x39bf57);
    public static final HytaleTerrain BANYAN_LEAVES = new HytaleTerrain("Banyan Leaves",
        HytaleBlock.of("Plant_Leaves_Banyan"), 0x234006);
    public static final HytaleTerrain BEECH_LEAVES = new HytaleTerrain("Beech Leaves",
        HytaleBlock.of("Plant_Leaves_Beech"), 0x446043);
    public static final HytaleTerrain BIRCH_LEAVES = new HytaleTerrain("Birch Leaves",
        HytaleBlock.of("Plant_Leaves_Birch"), 0x408059);
    public static final HytaleTerrain BLUE_FIG_LEAVES = new HytaleTerrain("Blue Fig Leaves",
        HytaleBlock.of("Plant_Leaves_Fig_Blue"), 0x2a4185);
    public static final HytaleTerrain BOTTLE_TREE_LEAVES = new HytaleTerrain("Bottle Tree Leaves",
        HytaleBlock.of("Plant_Leaves_Bottle"), 0x549f30);
    public static final HytaleTerrain BRAMBLE_LEAVES = new HytaleTerrain("Bramble Leaves",
        HytaleBlock.of("Plant_Leaves_Bramble"), 0x13bf28);
    public static final HytaleTerrain BURNED_LEAVES = new HytaleTerrain("Burned Leaves",
        HytaleBlock.of("Plant_Leaves_Burnt"), 0xb24b2a);
    public static final HytaleTerrain CAMPHOR_LEAVES = new HytaleTerrain("Camphor Leaves",
        HytaleBlock.of("Plant_Leaves_Camphor"), 0x38402d);
    public static final HytaleTerrain CEDAR_LEAVES = new HytaleTerrain("Cedar Leaves",
        HytaleBlock.of("Plant_Leaves_Cedar"), 0x376030);
    public static final HytaleTerrain CRYSTAL_LEAVES = new HytaleTerrain("Crystal Leaves",
        HytaleBlock.of("Plant_Leaves_Crystal"), 0x729bb8);
    public static final HytaleTerrain DEAD_LEAVES = new HytaleTerrain("Dead Leaves",
        HytaleBlock.of("Plant_Leaves_Dead"), 0xcc7b14);
    public static final HytaleTerrain DRY_LEAVES = new HytaleTerrain("Dry Leaves",
        HytaleBlock.of("Plant_Leaves_Dry"), 0xcc9330);
    public static final HytaleTerrain FILTER_TREE_LEAVES = new HytaleTerrain("Filter Tree Leaves",
        HytaleBlock.of("Plant_Leaves_Sallow"), 0x268040);
    public static final HytaleTerrain FILTER_TREE_WOOD_AND_LEAVES = new HytaleTerrain("Filter Tree Wood and Leaves",
        HytaleBlock.of("Plant_Leaves_Rhododendron"), 0x4e9f10);
    public static final HytaleTerrain FIR_LEAVES = new HytaleTerrain("Fir Leaves",
        HytaleBlock.of("Plant_Leaves_Fir"), 0x86bf87);
    public static final HytaleTerrain FIR_LEAVES_TIP = new HytaleTerrain("Fir Leaves Tip",
        HytaleBlock.of("Plant_Leaves_Fir_Red"), 0x20402f);
    public static final HytaleTerrain FIRE_LEAVES = new HytaleTerrain("Fire Leaves",
        HytaleBlock.of("Plant_Leaves_Fire"), 0x66150a);
    public static final HytaleTerrain FOREST_FLOOR_LEAVES = new HytaleTerrain("Forest Floor Leaves",
        HytaleBlock.of("Plant_Leaves_Autumn_Floor"), 0x2e601d);
    public static final HytaleTerrain GIANT_PALM_LEAVES = new HytaleTerrain("Giant Palm Leaves",
        HytaleBlock.of("Plant_Leaves_Palm_Oasis"), 0x0d8022);
    public static final HytaleTerrain GUMBOAB_LEAVES = new HytaleTerrain("Gumboab Leaves",
        HytaleBlock.of("Plant_Leaves_Gumboab"), 0x899f70);
    public static final HytaleTerrain JUNGLE_FLOOR_LEAVES = new HytaleTerrain("Jungle Floor Leaves",
        HytaleBlock.of("Plant_Leaves_Jungle_Floor"), 0x68bf60);
    public static final HytaleTerrain MAPLE_LEAVES = new HytaleTerrain("Maple Leaves",
        HytaleBlock.of("Plant_Leaves_Maple"), 0x805c40);
    public static final HytaleTerrain OAK_LEAVES = new HytaleTerrain("Oak Leaves",
        HytaleBlock.of("Plant_Leaves_Oak"), 0x134023);
    public static final HytaleTerrain PALM_LEAVES = new HytaleTerrain("Palm Leaves",
        HytaleBlock.of("Plant_Leaves_Palm"), 0x29600a);
    public static final HytaleTerrain PALO_LEAVES = new HytaleTerrain("Palo Leaves",
        HytaleBlock.of("Plant_Leaves_Palo"), 0x59805c);
    public static final HytaleTerrain PETRIFIED_PINE_LEAVES = new HytaleTerrain("Petrified Pine Leaves",
        HytaleBlock.of("Plant_Leaves_Petrified"), 0x515247);
    public static final HytaleTerrain POISONED_LEAVES = new HytaleTerrain("Poisoned Leaves",
        HytaleBlock.of("Plant_Leaves_Poisoned"), 0x3d4532);
    public static final HytaleTerrain RED_FIR_LEAVES = new HytaleTerrain("Red Fir Leaves",
        HytaleBlock.of("Plant_Leaves_Fir_Red"), 0x9e3723);
    public static final HytaleTerrain REDWOOD_LEAVES = new HytaleTerrain("Redwood Leaves",
        HytaleBlock.of("Plant_Leaves_Redwood"), 0x9e2616);
    public static final HytaleTerrain SHALLOW_LEAVES = new HytaleTerrain("Shallow Leaves",
        HytaleBlock.of("Plant_Leaves_Goldentree"), 0x998238);
    public static final HytaleTerrain SNOWY_FIR_LEAVES = new HytaleTerrain("Snowy Fir Leaves",
        HytaleBlock.of("Plant_Leaves_Fir_Snow"), 0xb1c3cc);
    public static final HytaleTerrain SNOWY_FIR_LEAVES_TIP = new HytaleTerrain("Snowy Fir Leaves Tip",
        HytaleBlock.of("Plant_Leaves_Fir_Snow"), 0x95acbd);
    public static final HytaleTerrain SNOWY_LEAVES = new HytaleTerrain("Snowy Leaves",
        HytaleBlock.of("Plant_Leaves_Snow"), 0x7b94ad);
    public static final HytaleTerrain SPIRAL_LEAVES = new HytaleTerrain("Spiral Leaves",
        HytaleBlock.of("Plant_Leaves_Spiral"), 0x839f50);
    public static final HytaleTerrain STORM_BARK_LEAVES = new HytaleTerrain("Storm Bark Leaves",
        HytaleBlock.of("Plant_Leaves_Stormbark"), 0x6d7c9e);
    public static final HytaleTerrain TROPICAL_LEAVES = new HytaleTerrain("Tropical Leaves",
        HytaleBlock.of("Plant_Leaves_Jungle"), 0x54bf39);
    public static final HytaleTerrain WILD_WISTERIA_LEAVES = new HytaleTerrain("Wild Wisteria Leaves",
        HytaleBlock.of("Plant_Leaves_Wisteria_Wild"), 0x627db2);
    public static final HytaleTerrain WILLOW_LEAVES = new HytaleTerrain("Willow Leaves",
        HytaleBlock.of("Plant_Leaves_Windwillow"), 0x064015);

    // ===== BUSHES & BRAMBLES =====
    public static final HytaleTerrain ARID_BRAMBLE = new HytaleTerrain("Arid Bramble",
        HytaleBlock.of("Plant_Bramble_Dry_Sandthorn"), 0x59381f);
    public static final HytaleTerrain GREEN_BRAMBLE = new HytaleTerrain("Green Bramble",
        HytaleBlock.of("Plant_Bramble_Moss_Twisted"), 0x549632);
    public static final HytaleTerrain WINTER_BRAMBLE = new HytaleTerrain("Winter Bramble",
        HytaleBlock.of("Plant_Bramble_Winter"), 0x4c322e);
    public static final HytaleTerrain BUSH = new HytaleTerrain("Bush",
        HytaleBlock.of("Plant_Bush"), 0x6a7955);
    public static final HytaleTerrain ARID_BUSH = new HytaleTerrain("Arid Bush",
        HytaleBlock.of("Plant_Bush_Arid"), 0x806a60);
    public static final HytaleTerrain ARID_PALM_BUSH = new HytaleTerrain("Arid Palm Bush",
        HytaleBlock.of("Plant_Bush_Arid_Palm"), 0x937b5a);
    public static final HytaleTerrain RED_ARID_BUSH = new HytaleTerrain("Red Arid Bush",
        HytaleBlock.of("Plant_Bush_Arid_Red"), 0x807d4f);
    public static final HytaleTerrain BUSHY_ARID_GRASS = new HytaleTerrain("Bushy Arid Grass",
        HytaleBlock.of("Plant_Bush_Arid_Sharp"), 0xa69850);
    public static final HytaleTerrain DRY_BRAMBLE = new HytaleTerrain("Dry Bramble",
        HytaleBlock.of("Plant_Bramble_Dry_Twisted"), 0x6c6451);
    public static final HytaleTerrain BIG_CRYSTAL_BUSH = new HytaleTerrain("Big Crystal Bush",
        HytaleBlock.of("Plant_Bush_Crystal"), 0x79bdd4);
    public static final HytaleTerrain DEAD_BUSH = new HytaleTerrain("Dead Bush",
        HytaleBlock.of("Plant_Bush_Dead"), 0x6e5f54);
    public static final HytaleTerrain DEAD_HANGING_BUSH = new HytaleTerrain("Dead Hanging Bush",
        HytaleBlock.of("Plant_Bush_Dead_Hanging"), 0x5e5041);
    public static final HytaleTerrain SHRUB = new HytaleTerrain("Shrub",
        HytaleBlock.of("Plant_Bush_Dead_Twisted"), 0x8c7e7e);
    public static final HytaleTerrain LARGE_DEAD_BUSH = new HytaleTerrain("Large Dead Bush",
        HytaleBlock.of("Plant_Bush_Dead_Tall"), 0x4f4230);
    public static final HytaleTerrain GREEN_BUSH = new HytaleTerrain("Green Bush",
        HytaleBlock.of("Plant_Bush_Green"), 0x61964d);
    public static final HytaleTerrain EXOTIC_HANGING_BUSH = new HytaleTerrain("Exotic Hanging Bush",
        HytaleBlock.of("Plant_Bush_Hanging"), 0x3f7949);
    public static final HytaleTerrain JUNGLE_BUSH = new HytaleTerrain("Jungle Bush",
        HytaleBlock.of("Plant_Bush_Jungle"), 0x3cb242);
    public static final HytaleTerrain LUSH_BIG_BUSH = new HytaleTerrain("Lush Big Bush",
        HytaleBlock.of("Plant_Bush_Lush"), 0x23400a);
    public static final HytaleTerrain WET_BUSH = new HytaleTerrain("Wet Bush",
        HytaleBlock.of("Plant_Bush_Wet"), 0x455c41);
    public static final HytaleTerrain FROZEN_SHRUB = new HytaleTerrain("Frozen Shrub",
        HytaleBlock.of("Plant_Bush_Winter_Sharp"), 0xac978a);
    public static final HytaleTerrain BUSHY_WINTER_GRASS = new HytaleTerrain("Bushy Winter Grass",
        HytaleBlock.of("Plant_Bush_Winter"), 0xccbe8f);
    public static final HytaleTerrain SNOWY_WINTER_BUSH = new HytaleTerrain("Snowy Winter Bush",
        HytaleBlock.of("Plant_Bush_Winter_Snow"), 0xbee1e8);
    public static final HytaleTerrain RED_WINTER_BUSH = new HytaleTerrain("Red Winter Bush",
        HytaleBlock.of("Plant_Bush_Winter_Red"), 0xba2020);

    // ===== CACTI =====
    public static final HytaleTerrain CACTUS_BOTTOM = new HytaleTerrain("Cactus Bottom",
        HytaleBlock.of("Plant_Cactus_1"), 0x52794f);
    public static final HytaleTerrain CACTUS_MIDDLE = new HytaleTerrain("Cactus Middle",
        HytaleBlock.of("Plant_Cactus_2"), 0x488f4f);
    public static final HytaleTerrain CACTUS_TOP = new HytaleTerrain("Cactus Top",
        HytaleBlock.of("Plant_Cactus_3"), 0x3aa65a);
    public static final HytaleTerrain CACTUS_BALL = new HytaleTerrain("Cactus Ball",
        HytaleBlock.of("Plant_Cactus_Ball_1"), 0x104c0f);
    public static final HytaleTerrain CACTUS_FLOWER = new HytaleTerrain("Cactus Flower",
        HytaleBlock.of("Plant_Cactus_Flower"), 0xc43b80);
    public static final HytaleTerrain LARGE_FLAT_CACTUS = new HytaleTerrain("Large Flat Cactus",
        HytaleBlock.of("Plant_Cactus_Flat_1"), 0x406346);
    public static final HytaleTerrain FLAT_CACTUS = new HytaleTerrain("Flat Cactus",
        HytaleBlock.of("Plant_Cactus_Flat_2"), 0x3d7953);
    public static final HytaleTerrain SMALL_FLAT_CACTUS = new HytaleTerrain("Small Flat Cactus",
        HytaleBlock.of("Plant_Cactus_Flat_3"), 0x328f38);

    // ===== CORAL BLOCKS =====
    public static final HytaleTerrain BLUE_CORAL_BLOCK = new HytaleTerrain("Blue Coral Block",
        HytaleBlock.of("Plant_Coral_Block_Blue"), 0x2c4a94);
    public static final HytaleTerrain CYAN_CORAL_BLOCK = new HytaleTerrain("Cyan Coral Block",
        HytaleBlock.of("Plant_Coral_Block_Cyan"), 0x3893a1);
    public static final HytaleTerrain GREEN_CORAL_BLOCK = new HytaleTerrain("Green Coral Block",
        HytaleBlock.of("Plant_Coral_Block_Green"), 0x29873d);
    public static final HytaleTerrain GRAY_CORAL_BLOCK = new HytaleTerrain("Gray Coral Block",
        HytaleBlock.of("Plant_Coral_Block_Grey"), 0x878180);
    public static final HytaleTerrain LIME_GREEN_CORAL_BLOCK = new HytaleTerrain("Lime Green Coral Block",
        HytaleBlock.of("Plant_Coral_Block_Lime"), 0x63a130);
    public static final HytaleTerrain ORANGE_CORAL_BLOCK = new HytaleTerrain("Orange Coral Block",
        HytaleBlock.of("Plant_Coral_Block_Orange"), 0xc76d1e);
    public static final HytaleTerrain PINK_CORAL_BLOCK = new HytaleTerrain("Pink Coral Block",
        HytaleBlock.of("Plant_Coral_Block_Pink"), 0xc75a79);
    public static final HytaleTerrain PURPLE_CORAL_BLOCK = new HytaleTerrain("Purple Coral Block",
        HytaleBlock.of("Plant_Coral_Block_Purple"), 0x6e2c94);
    public static final HytaleTerrain POISONED_CORAL_BLOCK = new HytaleTerrain("Poisoned Coral Block",
        HytaleBlock.of("Plant_Coral_Block_Poison"), 0x4a543b);
    public static final HytaleTerrain RED_CORAL_BLOCK = new HytaleTerrain("Red Coral Block",
        HytaleBlock.of("Plant_Coral_Block_Red"), 0xad3523);
    public static final HytaleTerrain VIOLET_CORAL_BLOCK = new HytaleTerrain("Violet Coral Block",
        HytaleBlock.of("Plant_Coral_Block_Violet"), 0x5d3b94);
    public static final HytaleTerrain WHITE_CORAL_BLOCK = new HytaleTerrain("White Coral Block",
        HytaleBlock.of("Plant_Coral_Block_White"), 0xe8dddc);
    public static final HytaleTerrain YELLOW_CORAL_BLOCK = new HytaleTerrain("Yellow Coral Block",
        HytaleBlock.of("Plant_Coral_Block_Yellow"), 0xbaa925);

    // ===== CORAL BUSHES =====
    public static final HytaleTerrain BLUE_CORAL_BUSH = new HytaleTerrain("Blue Coral Bush",
        HytaleBlock.of("Plant_Coral_Bush_Blue"), 0x2f528a);
    public static final HytaleTerrain CYAN_CORAL_BUSH = new HytaleTerrain("Cyan Coral Bush",
        HytaleBlock.of("Plant_Coral_Bush_Cyan"), 0x3b9396);
    public static final HytaleTerrain GREEN_CORAL_BUSH = new HytaleTerrain("Green Coral Bush",
        HytaleBlock.of("Plant_Coral_Bush_Green"), 0x2a7d34);
    public static final HytaleTerrain GRAY_CORAL_BUSH = new HytaleTerrain("Gray Coral Bush",
        HytaleBlock.of("Plant_Coral_Bush_Grey"), 0x7d7777);
    public static final HytaleTerrain NEON_CORAL_BUSH = new HytaleTerrain("Neon Coral Bush",
        HytaleBlock.of("Plant_Coral_Bush_Lime"), 0x5fc91c);
    public static final HytaleTerrain ORANGE_CORAL_BUSH = new HytaleTerrain("Orange Coral Bush",
        HytaleBlock.of("Plant_Coral_Bush_Orange"), 0xbd5c24);
    public static final HytaleTerrain PINK_CORAL_BUSH = new HytaleTerrain("Pink Coral Bush",
        HytaleBlock.of("Plant_Coral_Bush_Pink"), 0xbd5c81);
    public static final HytaleTerrain POISONED_CORAL_BUSH = new HytaleTerrain("Poisoned Coral Bush",
        HytaleBlock.of("Plant_Coral_Bush_Poisoned"), 0x454a37);
    public static final HytaleTerrain PURPLE_CORAL_BUSH = new HytaleTerrain("Purple Coral Bush",
        HytaleBlock.of("Plant_Coral_Bush_Purple"), 0x5f2f8a);
    public static final HytaleTerrain RED_CORAL_BUSH = new HytaleTerrain("Red Coral Bush",
        HytaleBlock.of("Plant_Coral_Bush_Red"), 0xa32b27);
    public static final HytaleTerrain VIOLET_CORAL_BUSH = new HytaleTerrain("Violet Coral Bush",
        HytaleBlock.of("Plant_Coral_Bush_Violet"), 0x523d8a);
    public static final HytaleTerrain WHITE_CORAL_BUSH = new HytaleTerrain("White Coral Bush",
        HytaleBlock.of("Plant_Coral_Bush_White"), 0xded3d3);
    public static final HytaleTerrain YELLOW_CORAL_BUSH = new HytaleTerrain("Yellow Coral Bush",
        HytaleBlock.of("Plant_Coral_Bush_Yellow"), 0xb0932a);

    // ===== CORAL MODELS (Sponges, Tubes, Fans, Brackets) =====
    public static final HytaleTerrain BLUE_CORAL_SPONGE = new HytaleTerrain("Blue Coral Sponge",
        HytaleBlock.of("Plant_Coral_Model_Blue"), 0x2e4e8f);
    public static final HytaleTerrain CYAN_CORAL_SPONGE = new HytaleTerrain("Cyan Coral Sponge",
        HytaleBlock.of("Plant_Coral_Model_Cyan"), 0x3a939c);
    public static final HytaleTerrain GREEN_CORAL_TUBES = new HytaleTerrain("Green Coral Tubes",
        HytaleBlock.of("Plant_Coral_Model_Green"), 0x2a8238);
    public static final HytaleTerrain GRAY_BRACKET_CORAL = new HytaleTerrain("Gray Bracket Coral",
        HytaleBlock.of("Plant_Coral_Model_Grey"), 0x827c7c);
    public static final HytaleTerrain LIME_CORAL_SPONGE = new HytaleTerrain("Lime Coral Sponge",
        HytaleBlock.of("Plant_Coral_Model_Lime"), 0x639c2a);
    public static final HytaleTerrain ORANGE_BRACKET_CORAL = new HytaleTerrain("Orange Bracket Coral",
        HytaleBlock.of("Plant_Coral_Model_Orange"), 0xc26421);
    public static final HytaleTerrain PINK_FAN_CORAL = new HytaleTerrain("Pink Fan Coral",
        HytaleBlock.of("Plant_Coral_Model_Pink"), 0xc25b7d);
    public static final HytaleTerrain PURPLE_CORAL_TUBES = new HytaleTerrain("Purple Coral Tubes",
        HytaleBlock.of("Plant_Coral_Model_Purple"), 0x662e8f);
    public static final HytaleTerrain RED_CORAL_SPONGE = new HytaleTerrain("Red Coral Sponge",
        HytaleBlock.of("Plant_Coral_Model_Red"), 0xa83025);
    public static final HytaleTerrain SEA_ANEMONE = new HytaleTerrain("Sea Anemone",
        HytaleBlock.of("Plant_Coral_Model_Violet"), 0x9e3131);
    public static final HytaleTerrain WHITE_CORAL_SPONGE = new HytaleTerrain("White Coral Sponge",
        HytaleBlock.of("Plant_Coral_Model_White"), 0xe3d8d8);
    public static final HytaleTerrain YELLOW_CORAL_TUBES = new HytaleTerrain("Yellow Coral Tubes",
        HytaleBlock.of("Plant_Coral_Model_Yellow"), 0xb59e28);

    // ===== BERRY BUSHES =====
    public static final HytaleTerrain BERRY_BUSH = new HytaleTerrain("Berry Bush",
        HytaleBlock.of("Plant_Crop_Berry_Block"), 0x59664d);
    public static final HytaleTerrain WET_BERRY_BUSH = new HytaleTerrain("Wet Berry Bush",
        HytaleBlock.of("Plant_Crop_Berry_Wet_Block"), 0x4f734b);
    public static final HytaleTerrain WINTER_BERRY_BUSH = new HytaleTerrain("Winter Berry Bush",
        HytaleBlock.of("Plant_Crop_Berry_Winter_Block"), 0x468055);

    // ===== SPECIAL FLORA =====
    public static final HytaleTerrain BLOOD_ROSE = new HytaleTerrain("Blood Rose",
        HytaleBlock.of("Plant_Flower_Common_Red"), 0xa30508);
    public static final HytaleTerrain BLOOD_CAP_MUSHROOM = new HytaleTerrain("Blood Cap Mushroom",
        HytaleBlock.of("Plant_Crop_Mushroom_Cap_Red"), 0x94201c);
    public static final HytaleTerrain BLOOD_LEAF = new HytaleTerrain("Blood Leaf",
        HytaleBlock.of("Plant_Flower_Bushy_Red"), 0xb21424);
    public static final HytaleTerrain AZURE_FERN = new HytaleTerrain("Azure Fern",
        HytaleBlock.of("Plant_Fern_Forest"), 0x458ebf);
    public static final HytaleTerrain AZURE_CAP_MUSHROOM = new HytaleTerrain("Azure Cap Mushroom",
        HytaleBlock.of("Plant_Crop_Mushroom_Common_Blue"), 0x4771a1);
    public static final HytaleTerrain AZURE_KELP = new HytaleTerrain("Azure Kelp",
        HytaleBlock.of("Plant_Seaweed_Grass_Green"), 0x3173b0);
    public static final HytaleTerrain BROWN_MUSHROOM_MYCELIUM = new HytaleTerrain("Brown Mushroom Mycelium",
        HytaleBlock.of("Plant_Crop_Mushroom_Block_Brown_Mycelium"), 0x61462f);
    public static final HytaleTerrain LARGE_BOOMSHROOM = new HytaleTerrain("Large Boomshroom",
        HytaleBlock.of("Plant_Crop_Mushroom_Boomshroom_Large"), 0x5c9999);
    public static final HytaleTerrain SMALL_BOOMSHROOM = new HytaleTerrain("Small Boomshroom",
        HytaleBlock.of("Plant_Crop_Mushroom_Boomshroom_Small"), 0xbf509f);
    public static final HytaleTerrain BROWN_CAP_MUSHROOM = new HytaleTerrain("Brown Cap Mushroom",
        HytaleBlock.of("Plant_Crop_Mushroom_Cap_Brown"), 0x523b21);
    public static final HytaleTerrain SPOTTED_GREEN_CAP_MUSHROOM = new HytaleTerrain("Spotted Green Cap Mushroom",
        HytaleBlock.of("Plant_Crop_Mushroom_Cap_Green"), 0x467a30);
    public static final HytaleTerrain SPOTTED_ALLIUM_CAP_MUSHROOM = new HytaleTerrain("Spotted Allium Cap Mushroom",
        HytaleBlock.of("Plant_Crop_Mushroom_Cap_Poison"), 0x68416e);
    public static final HytaleTerrain RED_CAP_MUSHROOM = new HytaleTerrain("Red Cap Mushroom",
        HytaleBlock.of("Plant_Crop_Mushroom_Cap_Red"), 0xa12e1f);
    public static final HytaleTerrain WHITE_CAP_MUSHROOM = new HytaleTerrain("White Cap Mushroom",
        HytaleBlock.of("Plant_Crop_Mushroom_Cap_White"), 0xdbcfce);
    public static final HytaleTerrain BLUE_COMMON_MUSHROOM = new HytaleTerrain("Blue Common Mushroom",
        HytaleBlock.of("Plant_Crop_Mushroom_Common_Blue"), 0x274487);
    public static final HytaleTerrain BROWN_COMMON_MUSHROOM = new HytaleTerrain("Brown Common Mushroom",
        HytaleBlock.of("Plant_Crop_Mushroom_Common_Brown"), 0x423116);
    public static final HytaleTerrain PUFFY_GREEN_COMMON_MUSHROOM = new HytaleTerrain("Puffy Green Common Mushroom",
        HytaleBlock.of("Plant_Crop_Mushroom_Common_Lime"), 0x237a35);
    public static final HytaleTerrain BLUE_FLAT_CAP_MUSHROOM = new HytaleTerrain("Blue Flat Cap Mushroom",
        HytaleBlock.of("Plant_Crop_Mushroom_Flatcap_Blue"), 0x192e78);
    public static final HytaleTerrain GREEN_FLAT_CAP_MUSHROOM = new HytaleTerrain("Green Flat Cap Mushroom",
        HytaleBlock.of("Plant_Crop_Mushroom_Flatcap_Green"), 0x166b2e);
    public static final HytaleTerrain BLUE_GLOWING_MUSHROOM = new HytaleTerrain("Blue Glowing Mushroom",
        HytaleBlock.of("Plant_Crop_Mushroom_Glowing_Blue"), 0x0e1a69);
    public static final HytaleTerrain GREEN_GLOWING_MUSHROOM = new HytaleTerrain("Green Glowing Mushroom",
        HytaleBlock.of("Plant_Crop_Mushroom_Glowing_Green"), 0x0c5c29);
    public static final HytaleTerrain ORANGE_GLOWING_MUSHROOM = new HytaleTerrain("Orange Glowing Mushroom",
        HytaleBlock.of("Plant_Crop_Mushroom_Glowing_Orange"), 0xba621a);
    public static final HytaleTerrain PURPLE_GLOWING_MUSHROOM = new HytaleTerrain("Purple Glowing Mushroom",
        HytaleBlock.of("Plant_Crop_Mushroom_Glowing_Purple"), 0x622787);
    public static final HytaleTerrain RED_GLOWING_MUSHROOM = new HytaleTerrain("Red Glowing Mushroom",
        HytaleBlock.of("Plant_Crop_Mushroom_Glowing_Red"), 0x912a10);
    public static final HytaleTerrain VIOLET_GLOWING_MUSHROOM = new HytaleTerrain("Violet Glowing Mushroom",
        HytaleBlock.of("Plant_Crop_Mushroom_Glowing_Violet"), 0x533587);
    public static final HytaleTerrain BROWN_MUSHROOM_SHELF = new HytaleTerrain("Brown Mushroom Shelf",
        HytaleBlock.of("Plant_Crop_Mushroom_Shelve_Brown"), 0x33280d);
    public static final HytaleTerrain GREEN_MUSHROOM_SHELF = new HytaleTerrain("Green Mushroom Shelf",
        HytaleBlock.of("Plant_Crop_Mushroom_Shelve_Green"), 0x044c25);
    public static final HytaleTerrain YELLOW_MUSHROOM_SHELVES = new HytaleTerrain("Yellow Mushroom Shelves",
        HytaleBlock.of("Plant_Crop_Mushroom_Shelve_Yellow"), 0xad9b21);

    // ===== STORM / ZONE-SPECIFIC =====
    public static final HytaleTerrain STORM_THISTLE = new HytaleTerrain("Storm Thistle",
        HytaleBlock.of("Plant_Flower_Bushy_Purple"), 0x81b9b0);
    public static final HytaleTerrain STORM_CAP_MUSHROOM = new HytaleTerrain("Storm Cap Mushroom",
        HytaleBlock.of("Plant_Crop_Mushroom_Cap_White"), 0x7783a1);
    public static final HytaleTerrain STORM_SAPLING = new HytaleTerrain("Storm Sapling",
        HytaleBlock.of("Plant_Sapling_Crystal"), 0x78b8c9);

    // ===== FERNS =====
    public static final HytaleTerrain FERN = new HytaleTerrain("Fern",
        HytaleBlock.of("Plant_Fern"), 0x647350);
    public static final HytaleTerrain ARID_FERN = new HytaleTerrain("Arid Fern",
        HytaleBlock.of("Plant_Fern_Arid"), 0x568c48);
    public static final HytaleTerrain FOREST_FERN = new HytaleTerrain("Forest Fern",
        HytaleBlock.of("Plant_Fern_Forest"), 0x37a64b);
    public static final HytaleTerrain JUNGLE_FERN = new HytaleTerrain("Jungle Fern",
        HytaleBlock.of("Plant_Fern_Jungle"), 0x21400a);
    public static final HytaleTerrain TALL_FERN = new HytaleTerrain("Tall Fern",
        HytaleBlock.of("Plant_Fern_Tall"), 0x40593e);
    public static final HytaleTerrain WET_FERN = new HytaleTerrain("Wet Fern",
        HytaleBlock.of("Plant_Fern_Wet_Big"), 0x3b734d);
    public static final HytaleTerrain GIANT_WET_FERN = new HytaleTerrain("Giant Wet Fern",
        HytaleBlock.of("Plant_Fern_Wet_Giant"), 0x4a8c2f);
    public static final HytaleTerrain BLUE_NETTLE = new HytaleTerrain("Blue Nettle",
        HytaleBlock.of("Plant_Flower_Bushy_Blue"), 0x235ca6);
    public static final HytaleTerrain LARGE_FERN = new HytaleTerrain("Large Fern",
        HytaleBlock.of("Plant_Fern_Jungle_Trunk"), 0x19a626);
    public static final HytaleTerrain CYAN_FESTUCA = new HytaleTerrain("Cyan Festuca",
        HytaleBlock.of("Plant_Flower_Bushy_Cyan"), 0x2eb2b0);
    public static final HytaleTerrain NETTLE = new HytaleTerrain("Nettle",
        HytaleBlock.of("Plant_Flower_Bushy_Green"), 0x549999);
    public static final HytaleTerrain ASHY_BUSH = new HytaleTerrain("Ashy Bush",
        HytaleBlock.of("Plant_Flower_Bushy_Grey"), 0xb24a94);
    public static final HytaleTerrain BUSHY_ORANGE_FERN = new HytaleTerrain("Bushy Orange Fern",
        HytaleBlock.of("Plant_Flower_Bushy_Orange"), 0xd94e0d);
    public static final HytaleTerrain POISONED_NETTLE = new HytaleTerrain("Poisoned Nettle",
        HytaleBlock.of("Plant_Flower_Bushy_Poisoned"), 0x5d663e);
    public static final HytaleTerrain PURPLE_NETTLE = new HytaleTerrain("Purple Nettle",
        HytaleBlock.of("Plant_Flower_Bushy_Violet"), 0x6223a6);
    public static final HytaleTerrain RED_FEATHER_LEAF = new HytaleTerrain("Red Feather Leaf",
        HytaleBlock.of("Plant_Flower_Bushy_Red"), 0xbf1518);
    public static final HytaleTerrain PURPLE_FLOWERS = new HytaleTerrain("Purple Flowers",
        HytaleBlock.of("Plant_Flower_Bushy_Purple"), 0x5e1496);
    public static final HytaleTerrain FROST_LEAF = new HytaleTerrain("Frost Leaf",
        HytaleBlock.of("Plant_Fern_Winter"), 0x37402d);

    // ===== FLOWERS =====
    public static final HytaleTerrain YELLOW_ARID_FLOWER_BUSH = new HytaleTerrain("Yellow Arid Flower Bush",
        HytaleBlock.of("Plant_Flower_Bushy_Yellow"), 0xb0951c);
    public static final HytaleTerrain BLUE_HIBISCUS = new HytaleTerrain("Blue Hibiscus",
        HytaleBlock.of("Plant_Flower_Common_Blue"), 0x24478a);
    public static final HytaleTerrain BLUE_ALOE = new HytaleTerrain("Blue Aloe",
        HytaleBlock.of("Plant_Flower_Common_Blue2"), 0x16317a);
    public static final HytaleTerrain CYAN_ARID_FLOWER = new HytaleTerrain("Cyan Arid Flower",
        HytaleBlock.of("Plant_Flower_Common_Cyan"), 0x2f9096);
    public static final HytaleTerrain CYAN_HIBISCUS = new HytaleTerrain("Cyan Hibiscus",
        HytaleBlock.of("Plant_Flower_Common_Cyan2"), 0x1f7887);
    public static final HytaleTerrain LINEN_WEED = new HytaleTerrain("Linen Weed",
        HytaleBlock.of("Plant_Flower_Common_Grey"), 0x67acac);
    public static final HytaleTerrain SANDY_LION = new HytaleTerrain("Sandy Lion",
        HytaleBlock.of("Plant_Flower_Common_Grey2"), 0xc95aa9);
    public static final HytaleTerrain JUNGLE_FLOWER = new HytaleTerrain("Jungle Flower",
        HytaleBlock.of("Plant_Flower_Common_Lime"), 0x88e645);
    public static final HytaleTerrain LIME_SUCCULENT = new HytaleTerrain("Lime Succulent",
        HytaleBlock.of("Plant_Flower_Common_Lime2"), 0x5d9620);
    public static final HytaleTerrain CHRYSANTHEMUM = new HytaleTerrain("Chrysanthemum",
        HytaleBlock.of("Plant_Flower_Common_Orange"), 0x111d73);
    public static final HytaleTerrain COMMON_ORANGE_FLOWER = new HytaleTerrain("Common Orange Flower",
        HytaleBlock.of("Plant_Flower_Common_Orange2"), 0xbd5815);
    public static final HytaleTerrain COMMON_PINK_FLOWER = new HytaleTerrain("Common Pink Flower",
        HytaleBlock.of("Plant_Flower_Common_Pink"), 0xbd4d74);
    public static final HytaleTerrain ALLIUM = new HytaleTerrain("Allium",
        HytaleBlock.of("Plant_Flower_Common_Pink2"), 0x8f6056);
    public static final HytaleTerrain CARMINE_PATCHED_THORN = new HytaleTerrain("Carmine Patched Thorn",
        HytaleBlock.of("Plant_Flower_Common_Poisoned"), 0x4dac79);
    public static final HytaleTerrain COMMON_PINK_FLOWER_POISONED = new HytaleTerrain("Common Pink Flower Poisoned",
        HytaleBlock.of("Plant_Flower_Common_Poisoned2"), 0x424a31);
    public static final HytaleTerrain PURPLE_ARID_FLOWER = new HytaleTerrain("Purple Arid Flower",
        HytaleBlock.of("Plant_Flower_Common_Purple"), 0x5e248a);
    public static final HytaleTerrain LAVA_FLOWER = new HytaleTerrain("Lava Flower",
        HytaleBlock.of("Plant_Flower_Common_Purple2"), 0xa63cc9);
    public static final HytaleTerrain RED_ARID_FLOWER = new HytaleTerrain("Red Arid Flower",
        HytaleBlock.of("Plant_Flower_Common_Red2"), 0xa3231a);
    public static final HytaleTerrain POPPY = new HytaleTerrain("Poppy",
        HytaleBlock.of("Plant_Flower_Common_Red"), 0xdde622);
    public static final HytaleTerrain CAMPANULA_FLOWER = new HytaleTerrain("Campanula Flower",
        HytaleBlock.of("Plant_Flower_Common_Violet"), 0x456373);
    public static final HytaleTerrain VIOLETS = new HytaleTerrain("Violets",
        HytaleBlock.of("Plant_Flower_Common_Violet2"), 0x4d328a);
    public static final HytaleTerrain WHITE_HYDRANGEA = new HytaleTerrain("White Hydrangea",
        HytaleBlock.of("Plant_Flower_Common_White"), 0xdecaca);
    public static final HytaleTerrain DAISY = new HytaleTerrain("Daisy",
        HytaleBlock.of("Plant_Flower_Common_White2"), 0x8f415e);

    // ===== GRASS PLANTS =====
    public static final HytaleTerrain SHORT_CAVE_GRASS = new HytaleTerrain("Short Cave Grass",
        HytaleBlock.of("Plant_Grass_Cave_Short"), 0x6a7350);
    public static final HytaleTerrain DRY_GRASS_PLANT = new HytaleTerrain("Dry Grass",
        HytaleBlock.of("Plant_Grass_Dry"), 0x807359);
    public static final HytaleTerrain TALL_DRY_GRASS = new HytaleTerrain("Tall Dry Grass",
        HytaleBlock.of("Plant_Grass_Dry_Tall"), 0x938a53);
    public static final HytaleTerrain GNARLED_GRASS = new HytaleTerrain("Gnarled Grass",
        HytaleBlock.of("Plant_Grass_Gnarled"), 0x6d864e);
    public static final HytaleTerrain SHORT_GNARLED_GRASS = new HytaleTerrain("Short Gnarled Grass",
        HytaleBlock.of("Plant_Grass_Gnarled_Short"), 0x659947);
    public static final HytaleTerrain TALL_GNARLED_GRASS = new HytaleTerrain("Tall Gnarled Grass",
        HytaleBlock.of("Plant_Grass_Gnarled_Tall"), 0x3c4c1b);
    public static final HytaleTerrain JUNGLE_GRASS = new HytaleTerrain("Jungle Grass",
        HytaleBlock.of("Plant_Grass_Jungle"), 0x2d592d);
    public static final HytaleTerrain SHORT_JUNGLE_GRASS = new HytaleTerrain("Short Jungle Grass",
        HytaleBlock.of("Plant_Grass_Jungle_Short"), 0x286c31);
    public static final HytaleTerrain TALL_JUNGLE_GRASS = new HytaleTerrain("Tall Jungle Grass",
        HytaleBlock.of("Plant_Grass_Jungle_Tall"), 0x1f8038);
    public static final HytaleTerrain LUSH_GRASS_PLANT = new HytaleTerrain("Lush Grass Plant",
        HytaleBlock.of("Plant_Grass_Lush"), 0x4d8046);
    public static final HytaleTerrain SHORT_LUSH_GRASS_PLANT = new HytaleTerrain("Short Lush Grass Plant",
        HytaleBlock.of("Plant_Grass_Lush_Short"), 0x409343);
    public static final HytaleTerrain TALL_LUSH_GRASS_PLANT = new HytaleTerrain("Tall Lush Grass Plant",
        HytaleBlock.of("Plant_Grass_Lush_Tall"), 0x34a64a);
    public static final HytaleTerrain POISON_GRASS = new HytaleTerrain("Poison Grass",
        HytaleBlock.of("Plant_Grass_Poisoned"), 0x525346);
    public static final HytaleTerrain SHORT_POISON_GRASS = new HytaleTerrain("Short Poison Grass",
        HytaleBlock.of("Plant_Grass_Poisoned_Short"), 0x585c48);
    public static final HytaleTerrain ROCKY_GRASS = new HytaleTerrain("Rocky Grass",
        HytaleBlock.of("Plant_Grass_Rocky"), 0x516043);
    public static final HytaleTerrain SHORT_ROCKY_GRASS = new HytaleTerrain("Short Rocky Grass",
        HytaleBlock.of("Plant_Grass_Rocky_Short"), 0x517343);
    public static final HytaleTerrain TALL_ROCKY_GRASS = new HytaleTerrain("Tall Rocky Grass",
        HytaleBlock.of("Plant_Grass_Rocky_Tall"), 0x6a863e);
    public static final HytaleTerrain GRASS_PLANT = new HytaleTerrain("Grass",
        HytaleBlock.of("Plant_Grass_Sharp"), 0x4f7043);
    public static final HytaleTerrain OVERGROWN_SHARP_GRASS = new HytaleTerrain("Overgrown Sharp Grass",
        HytaleBlock.of("Plant_Grass_Sharp_Overgrown"), 0x42853e);
    public static final HytaleTerrain SHORT_SHARP_GRASS = new HytaleTerrain("Short Sharp Grass",
        HytaleBlock.of("Plant_Grass_Sharp_Short"), 0x339940);
    public static final HytaleTerrain TALL_GRASS = new HytaleTerrain("Tall Grass",
        HytaleBlock.of("Plant_Grass_Sharp_Tall"), 0x18470e);
    public static final HytaleTerrain WILD_SHARP_GRASS = new HytaleTerrain("Wild Sharp Grass",
        HytaleBlock.of("Plant_Grass_Sharp_Wild"), 0x375c38);
    public static final HytaleTerrain SNOWY_GRASS = new HytaleTerrain("Snowy Grass",
        HytaleBlock.of("Plant_Grass_Snowy"), 0xd0e2da);
    public static final HytaleTerrain SHORT_SNOWY_GRASS = new HytaleTerrain("Short Snowy Grass",
        HytaleBlock.of("Plant_Grass_Snowy_Short"), 0xcaeae3);
    public static final HytaleTerrain TALL_SNOWY_GRASS = new HytaleTerrain("Tall Snowy Grass",
        HytaleBlock.of("Plant_Grass_Snowy_Tall"), 0xc3f2f1);
    public static final HytaleTerrain WET_GRASS_PLANT = new HytaleTerrain("Wet Grass",
        HytaleBlock.of("Plant_Grass_Wet"), 0x4e733f);
    public static final HytaleTerrain OVERGROWN_WET_GRASS = new HytaleTerrain("Overgrown Wet Grass",
        HytaleBlock.of("Plant_Grass_Wet_Overgrown"), 0x44883a);
    public static final HytaleTerrain SHORT_WET_GRASS = new HytaleTerrain("Short Wet Grass",
        HytaleBlock.of("Plant_Grass_Wet_Short"), 0x309e32);
    public static final HytaleTerrain TALL_WET_GRASS = new HytaleTerrain("Tall Wet Grass",
        HytaleBlock.of("Plant_Grass_Wet_Tall"), 0x1a470d);
    public static final HytaleTerrain WILD_WET_GRASS = new HytaleTerrain("Wild Wet Grass",
        HytaleBlock.of("Plant_Grass_Wet_Wild"), 0x365d33);
    public static final HytaleTerrain WINTER_GRASS = new HytaleTerrain("Winter Grass",
        HytaleBlock.of("Plant_Grass_Winter"), 0x818c82);
    public static final HytaleTerrain SHORT_WINTER_GRASS = new HytaleTerrain("Short Winter Grass",
        HytaleBlock.of("Plant_Grass_Winter_Short"), 0x839588);
    public static final HytaleTerrain TALL_WINTER_GRASS = new HytaleTerrain("Tall Winter Grass",
        HytaleBlock.of("Plant_Grass_Winter_Tall"), 0x859e91);

    // ===== MOSS =====
    public static final HytaleTerrain BLUE_MOSS = new HytaleTerrain("Blue Moss",
        HytaleBlock.of("Plant_Moss_Blue"), 0x3c68d4);
    public static final HytaleTerrain MOSS = new HytaleTerrain("Moss",
        HytaleBlock.of("Plant_Moss_Green"), 0x428640);
    public static final HytaleTerrain DARK_GREEN_MOSS = new HytaleTerrain("Dark Green Moss",
        HytaleBlock.of("Plant_Moss_Green_Dark"), 0x2a5a20);
    public static final HytaleTerrain YELLOW_HIBISCUS = new HytaleTerrain("Yellow Hibiscus",
        HytaleBlock.of("Plant_Flower_Common_Yellow"), 0xbfa62a);
    public static final HytaleTerrain BLUE_FLAX = new HytaleTerrain("Blue Flax",
        HytaleBlock.of("Plant_Flower_Flax_Blue"), 0x315499);
    public static final HytaleTerrain FIRE_FLOWER = new HytaleTerrain("Fire Flower",
        HytaleBlock.of("Plant_Flower_Flax_Orange"), 0xcc3618);
    public static final HytaleTerrain DANDELION = new HytaleTerrain("Dandelion",
        HytaleBlock.of("Plant_Flower_Common_Yellow2"), 0x6ab0b0);
    public static final HytaleTerrain PINK_FLAX = new HytaleTerrain("Pink Flax",
        HytaleBlock.of("Plant_Flower_Flax_Pink"), 0xcc6084);
    public static final HytaleTerrain BERRY_FLAX = new HytaleTerrain("Berry Flax",
        HytaleBlock.of("Plant_Flower_Flax_Purple"), 0xc85da9);
    public static final HytaleTerrain SMALL_DAISIES = new HytaleTerrain("Small Daisies",
        HytaleBlock.of("Plant_Flower_Flax_White"), 0x89e04b);
    public static final HytaleTerrain LUCERNE = new HytaleTerrain("Lucerne",
        HytaleBlock.of("Plant_Flower_Flax_Yellow"), 0x192680);
    public static final HytaleTerrain HEMLOCK = new HytaleTerrain("Hemlock",
        HytaleBlock.of("Plant_Flower_Hemlock"), 0x98655b);
    public static final HytaleTerrain BLUE_CAVEWEED = new HytaleTerrain("Blue Caveweed",
        HytaleBlock.of("Plant_Flower_Tall_Blue"), 0x213b8a);
    public static final HytaleTerrain AZURE_FLOWER = new HytaleTerrain("Azure Flower",
        HytaleBlock.of("Plant_Flower_Tall_Cyan"), 0x5483b2);
    public static final HytaleTerrain ORANGE_ORCHID = new HytaleTerrain("Orange Orchid",
        HytaleBlock.of("Plant_Flower_Orchid_Orange"), 0xd66d1c);
    public static final HytaleTerrain PINK_ORCHID = new HytaleTerrain("Pink Orchid",
        HytaleBlock.of("Plant_Flower_Orchid_Pink"), 0xd65c83);
    public static final HytaleTerrain BLACK_ORCHID = new HytaleTerrain("Black Orchid",
        HytaleBlock.of("Plant_Flower_Orchid_Purple"), 0x302d2d);
    public static final HytaleTerrain PURPLE_ORCHID = new HytaleTerrain("Purple Orchid",
        HytaleBlock.of("Plant_Flower_Orchid_Cyan"), 0x742ea3);
    public static final HytaleTerrain RED_ORCHID = new HytaleTerrain("Red Orchid",
        HytaleBlock.of("Plant_Flower_Orchid_Red"), 0xbd3122);
    public static final HytaleTerrain WHITE_ORCHID = new HytaleTerrain("White Orchid",
        HytaleBlock.of("Plant_Flower_Orchid_White"), 0xf7e6e6);
    public static final HytaleTerrain YELLOW_ORCHID = new HytaleTerrain("Yellow Orchid",
        HytaleBlock.of("Plant_Flower_Orchid_Yellow"), 0xc9b124);
    public static final HytaleTerrain POISONED_FLOWER = new HytaleTerrain("Poisoned Flower",
        HytaleBlock.of("Plant_Flower_Poisoned_Orange"), 0x4c543e);
    public static final HytaleTerrain DELPHINIUM = new HytaleTerrain("Delphinium",
        HytaleBlock.of("Plant_Flower_Tall_Purple"), 0x5ba6a6);
    public static final HytaleTerrain BUSHY_CYAN_FERN = new HytaleTerrain("Bushy Cyan Fern",
        HytaleBlock.of("Plant_Flower_Tall_Cyan2"), 0x3f95a1);
    public static final HytaleTerrain CYAN_FLOWER = new HytaleTerrain("Cyan Flower",
        HytaleBlock.of("Plant_Flower_Tall_Pink"), 0x2d7d91);
    public static final HytaleTerrain PINK_CAMELLIA = new HytaleTerrain("Pink Camellia",
        HytaleBlock.of("Plant_Flower_Tall_Red"), 0xc76180);
    public static final HytaleTerrain LAVENDER = new HytaleTerrain("Lavender",
        HytaleBlock.of("Plant_Lavender_Stage_0"), 0xc2579a);
    public static final HytaleTerrain TALL_RED_RAFFLESIA = new HytaleTerrain("Tall Red Rafflesia",
        HytaleBlock.of("Plant_Flower_Tall_Violet"), 0xad392a);
    public static final HytaleTerrain LARKSPUR = new HytaleTerrain("Larkspur",
        HytaleBlock.of("Plant_Flower_Tall_Yellow"), 0x7ed93d);
    public static final HytaleTerrain SUNFLOWER = new HytaleTerrain("Sunflower",
        HytaleBlock.of("Plant_Sunflower_Stage_0"), 0x14246e);

    // ===== WATER PLANTS =====
    public static final HytaleTerrain BLUE_WATER_LILY = new HytaleTerrain("Blue Water Lily",
        HytaleBlock.of("Plant_Flower_Water_Blue"), 0x33538f);
    public static final HytaleTerrain DUCKWEED = new HytaleTerrain("Duckweed",
        HytaleBlock.of("Plant_Flower_Water_Duckweed"), 0x609f9f);
    public static final HytaleTerrain WATER_LILY = new HytaleTerrain("Water Lily",
        HytaleBlock.of("Plant_Flower_Water_Green"), 0xbc559e);
    public static final HytaleTerrain PURPLE_WATER_LILY = new HytaleTerrain("Purple Water Lily",
        HytaleBlock.of("Plant_Flower_Water_Purple"), 0x67338f);
    public static final HytaleTerrain RED_WATER_LILY = new HytaleTerrain("Red Water Lily",
        HytaleBlock.of("Plant_Flower_Water_Red"), 0xa8342c);
    public static final HytaleTerrain WHITE_WATER_LILY = new HytaleTerrain("White Water Lily",
        HytaleBlock.of("Plant_Flower_Water_White"), 0xe6dfd3);

    // ===== ARID GRASS =====
    public static final HytaleTerrain PLANT_GRASS_ARID = new HytaleTerrain("Plant Grass Arid",
        HytaleBlock.of("Plant_Grass_Arid"), 0xa1a648);
    public static final HytaleTerrain SHORT_DRY_GRASS = new HytaleTerrain("Short Dry Grass",
        HytaleBlock.of("Plant_Grass_Arid_Short"), 0x59491b);
    public static final HytaleTerrain BUSHY_SAVANNA_GRASS = new HytaleTerrain("Bushy Savanna Grass",
        HytaleBlock.of("Plant_Grass_Arid_Tall"), 0x6c6a4c);

    // ===== HANGING MOSS =====
    public static final HytaleTerrain BLUE_HANGING_MOSS = new HytaleTerrain("Blue Hanging Moss",
        HytaleBlock.of("Plant_Moss_Cave_Blue"), 0x223f87);
    public static final HytaleTerrain GREEN_HANGING_MOSS = new HytaleTerrain("Green Hanging Moss",
        HytaleBlock.of("Plant_Moss_Cave_Green"), 0x1f7a32);
    public static final HytaleTerrain DARK_GREEN_HANGING_MOSS = new HytaleTerrain("Dark Green Hanging Moss",
        HytaleBlock.of("Plant_Moss_Cave_Green_Dark"), 0x0e471f);
    public static final HytaleTerrain RED_HANGING_MOSS = new HytaleTerrain("Red Hanging Moss",
        HytaleBlock.of("Plant_Moss_Cave_Red"), 0xa12a18);
    public static final HytaleTerrain YELLOW_HANGING_MOSS = new HytaleTerrain("Yellow Hanging Moss",
        HytaleBlock.of("Plant_Moss_Cave_Yellow"), 0xad9c1a);
    public static final HytaleTerrain RED_MOSS = new HytaleTerrain("Red Moss",
        HytaleBlock.of("Plant_Moss_Red"), 0xcc3438);

    // ===== MOSS RUGS =====
    public static final HytaleTerrain BLUE_MOSS_RUG = new HytaleTerrain("Blue Moss Rug",
        HytaleBlock.of("Plant_Moss_Rug_Blue"), 0x29529e);
    public static final HytaleTerrain GREEN_MOSS_RUG = new HytaleTerrain("Green Moss Rug",
        HytaleBlock.of("Plant_Moss_Rug_Green"), 0x269136);
    public static final HytaleTerrain DARK_GREEN_MOSS_RUG = new HytaleTerrain("Dark Green Moss Rug",
        HytaleBlock.of("Plant_Moss_Rug_Green_Dark"), 0x145e25);
    public static final HytaleTerrain SORREL_RUG = new HytaleTerrain("Sorrel Rug",
        HytaleBlock.of("Plant_Moss_Rug_Lime"), 0x8c3838);
    public static final HytaleTerrain PINK_MOSS_RUG = new HytaleTerrain("Pink Moss Rug",
        HytaleBlock.of("Plant_Moss_Rug_Pink"), 0xd15681);
    public static final HytaleTerrain RED_MOSS_RUG = new HytaleTerrain("Red Moss Rug",
        HytaleBlock.of("Plant_Moss_Rug_Red"), 0xb8281d);
    public static final HytaleTerrain YELLOW_MOSS_RUG = new HytaleTerrain("Yellow Moss Rug",
        HytaleBlock.of("Plant_Moss_Rug_Yellow"), 0xc4a61f);

    // ===== SHORT MOSS =====
    public static final HytaleTerrain SHORT_BLUE_MOSS = new HytaleTerrain("Short Blue Moss",
        HytaleBlock.of("Plant_Moss_Short_Blue"), 0x2a5291);
    public static final HytaleTerrain SHORT_MOSS = new HytaleTerrain("Short Moss",
        HytaleBlock.of("Plant_Moss_Short_Green"), 0x802828);
    public static final HytaleTerrain SHORT_DARK_GREEN_MOSS = new HytaleTerrain("Short Dark Green Moss",
        HytaleBlock.of("Plant_Moss_Short_Green_Dark"), 0x145220);
    public static final HytaleTerrain SHORT_RED_MOSS = new HytaleTerrain("Short Red Moss",
        HytaleBlock.of("Plant_Moss_Short_Red"), 0xab2520);
    public static final HytaleTerrain SHORT_YELLOW_MOSS = new HytaleTerrain("Short Yellow Moss",
        HytaleBlock.of("Plant_Moss_Short_Yellow"), 0xb89723);
    public static final HytaleTerrain YELLOW_MOSS = new HytaleTerrain("Yellow Moss",
        HytaleBlock.of("Plant_Moss_Yellow"), 0xdcc438);

    // ===== REEDS =====
    public static final HytaleTerrain PAPYRUS_REEDS = new HytaleTerrain("Papyrus Reeds",
        HytaleBlock.of("Plant_Reeds_Arid"), 0x5b7979);
    public static final HytaleTerrain LAVA_REEDS = new HytaleTerrain("Lava Reeds",
        HytaleBlock.of("Plant_Reeds_Lava"), 0x8f567f);
    public static final HytaleTerrain RIVER_REEDS = new HytaleTerrain("River Reeds",
        HytaleBlock.of("Plant_Reeds_Marsh"), 0x71a64b);
    public static final HytaleTerrain POISON_REEDS = new HytaleTerrain("Poison Reeds",
        HytaleBlock.of("Plant_Reeds_Poison"), 0x626b48);
    public static final HytaleTerrain TALL_WATER_REEDS = new HytaleTerrain("Tall Water Reeds",
        HytaleBlock.of("Plant_Reeds_Water"), 0x171e4c);
    public static final HytaleTerrain WET_REEDS = new HytaleTerrain("Wet Reeds",
        HytaleBlock.of("Plant_Reeds_Wet"), 0x634e4a);
    public static final HytaleTerrain WINTER_REEDS = new HytaleTerrain("Winter Reeds",
        HytaleBlock.of("Plant_Reeds_Winter"), 0x49795f);

    // ===== CAVE ROOTS =====
    public static final HytaleTerrain LEAFY_CAVE_ROOTS = new HytaleTerrain("Leafy Cave Roots",
        HytaleBlock.of("Plant_Roots_Leafy"), 0x66624d);
    public static final HytaleTerrain CAVE_ROOTS = new HytaleTerrain("Cave Roots",
        HytaleBlock.of("Plant_Roots_Cave"), 0x6b734c);

    // ===== SEAWEED =====
    public static final HytaleTerrain RED_ARID_SEAWEED = new HytaleTerrain("Red Arid Seaweed",
        HytaleBlock.of("Plant_Seaweed_Arid_Red"), 0x806659);
    public static final HytaleTerrain ARID_SEAWEED_STACK = new HytaleTerrain("Arid Seaweed Stack",
        HytaleBlock.of("Plant_Seaweed_Arid_Stack"), 0x937853);
    public static final HytaleTerrain SHORT_ARID_SEAWEED = new HytaleTerrain("Short Arid Seaweed",
        HytaleBlock.of("Plant_Seaweed_Arid_Short"), 0xa69748);
    public static final HytaleTerrain TALL_ARID_SEAWEED = new HytaleTerrain("Tall Arid Seaweed",
        HytaleBlock.of("Plant_Seaweed_Arid_Tall"), 0x59361b);
    public static final HytaleTerrain YELLOW_ARID_SEAWEED = new HytaleTerrain("Yellow Arid Seaweed",
        HytaleBlock.of("Plant_Seaweed_Arid_Yellow"), 0x6c624c);
    public static final HytaleTerrain EERIE_DEAD_SEAWEED = new HytaleTerrain("Eerie Dead Seaweed",
        HytaleBlock.of("Plant_Seaweed_Dead_Eerie"), 0x6c6867);
    public static final HytaleTerrain GHOSTLY_DEAD_SEAWEED = new HytaleTerrain("Ghostly Dead Seaweed",
        HytaleBlock.of("Plant_Seaweed_Dead_Ghostly"), 0x837a73);
    public static final HytaleTerrain SHORT_DEAD_SEAWEED = new HytaleTerrain("Short Dead Seaweed",
        HytaleBlock.of("Plant_Seaweed_Dead_Short"), 0x99907d);
    public static final HytaleTerrain DEAD_SEAWEED_STACK = new HytaleTerrain("Dead Seaweed Stack",
        HytaleBlock.of("Plant_Seaweed_Dead_Stack"), 0x403430);
    public static final HytaleTerrain TALL_DEAD_SEAWEED = new HytaleTerrain("Tall Dead Seaweed",
        HytaleBlock.of("Plant_Seaweed_Dead_Tall"), 0x565452);
    public static final HytaleTerrain SHORT_SEAWEED = new HytaleTerrain("Short Seaweed",
        HytaleBlock.of("Plant_Seaweed_Grass_Short"), 0x39603b);
    public static final HytaleTerrain GREEN_SEAWEED_BULBS = new HytaleTerrain("Green Seaweed Bulbs",
        HytaleBlock.of("Plant_Seaweed_Grass_Bulbs"), 0x367042);
    public static final HytaleTerrain GREEN_SEAWEED = new HytaleTerrain("Green Seaweed",
        HytaleBlock.of("Plant_Seaweed_Grass_Green"), 0x2f804b);
    public static final HytaleTerrain SEAWEED_MIDDLE = new HytaleTerrain("Seaweed Middle",
        HytaleBlock.of("Plant_Seaweed_Grass_Stack"), 0x104015);
    public static final HytaleTerrain TALL_SEAWEED = new HytaleTerrain("Tall Seaweed",
        HytaleBlock.of("Plant_Seaweed_Grass_Tall"), 0x305038);
    public static final HytaleTerrain WET_SEAWEED = new HytaleTerrain("Wet Seaweed",
        HytaleBlock.of("Plant_Seaweed_Wet_Stack"), 0x2e6043);
    public static final HytaleTerrain AURORA_SEAWEED = new HytaleTerrain("Aurora Seaweed",
        HytaleBlock.of("Plant_Seaweed_Winter_Aurora"), 0x7d9393);
    public static final HytaleTerrain BLUE_WINTER_SEAWEED = new HytaleTerrain("Blue Winter Seaweed",
        HytaleBlock.of("Plant_Seaweed_Winter_Blue"), 0x7798a3);
    public static final HytaleTerrain SHORT_WINTER_SEAWEED = new HytaleTerrain("Short Winter Seaweed",
        HytaleBlock.of("Plant_Seaweed_Winter_Short"), 0x6e90b2);
    public static final HytaleTerrain WINTER_SEAWEED_STACK = new HytaleTerrain("Winter Seaweed Stack",
        HytaleBlock.of("Plant_Seaweed_Winter_Stack"), 0x396d73);
    public static final HytaleTerrain TALL_WINTER_SEAWEED = new HytaleTerrain("Tall Winter Seaweed",
        HytaleBlock.of("Plant_Seaweed_Winter_Tall"), 0x6f7c83);

    // ===== VINE RUG =====
    public static final HytaleTerrain VINE_RUG = new HytaleTerrain("Vine Rug",
        HytaleBlock.of("Plant_Vine_Rug"), 0x339133);

    // ===== MOSS BLOCKS =====
    public static final HytaleTerrain YELLOW_MOSS_BLOCK = new HytaleTerrain("Yellow Moss Block",
        HytaleBlock.of("Plant_Moss_Block_Yellow"), 0xc7b72a);
    public static final HytaleTerrain RED_MOSS_BLOCK = new HytaleTerrain("Red Moss Block",
        HytaleBlock.of("Plant_Moss_Block_Red"), 0xba3d27);
    public static final HytaleTerrain DARK_GREEN_MOSS_BLOCK = new HytaleTerrain("Dark Green Moss Block",
        HytaleBlock.of("Plant_Moss_Block_Green_Dark"), 0x196130);
    public static final HytaleTerrain BLUE_MOSS_BLOCK = new HytaleTerrain("Blue Moss Block",
        HytaleBlock.of("Plant_Moss_Block_Blue"), 0x324fa1);
    public static final HytaleTerrain GREEN_MOSS_BLOCK = new HytaleTerrain("Green Moss Block",
        HytaleBlock.of("Plant_Moss_Block_Green"), 0x2e9446);

    // ===== PICK LIST (all terrains in display order as requested by builder team) =====

    /** Array of terrains shown on the main Terrain tab in Hytale mode. */
    public static final HytaleTerrain[] PICK_LIST;

    /** All defined terrains, used for indexing, lookup and export. */
    private static final HytaleTerrain[] ALL_TERRAINS;

    static {
        List<HytaleTerrain> list = new ArrayList<>();
        // Ground blocks
        list.addAll(Arrays.asList(
            ASHEN_SAND, AQUA_COBBLE, AQUA_STONE, BASALT, BASALT_COBBLE,
            BLUE_CRYSTAL, BLUE_ICE, BURNED_GRASS, CALCITE, CALCITE_COBBLE,
            CHALK, COLD_GRASS, COLD_MAGMA, CRACKED_SLATE, CRACKED_VOLCANIC_ROCK,
            CYAN_CRYSTAL, DEEP_GRASS, DRY_GRASS, FULL_GRASS, GRASS,
            GREEN_CRYSTAL, ICE, MARBLE, MOSSY_STONE, PINK_CRYSTAL,
            POISONED_VOLCANIC_ROCK, PURPLE_CRYSTAL, QUARTZITE, RED_CRYSTAL,
            RED_SAND, RED_SANDSTONE, RED_SANDSTONE_BRICK_SMOOTH, SALT_BLOCK,
            SAND, SANDSTONE, SANDSTONE_BRICK_SMOOTH, SHALE, SHALE_COBBLE,
            SLATE, SLATE_COBBLE, STONE, SUMMER_GRASS, VOLCANIC_ROCK,
            WET_GRASS, WHITE_CRYSTAL, WHITE_SAND, WHITE_SANDSTONE,
            WHITE_SANDSTONE_BRICK_SMOOTH, YELLOW_CRYSTAL
        ));
        // Leaves
        list.addAll(Arrays.asList(
            AMBER_LEAVES, ARID_PALM_LEAVES, ASH_LEAVES, ASPEN_LEAVES,
            AUTUMN_LEAVES, AZURE_LEAVES, BAMBOO_LEAVES, BANYAN_LEAVES,
            BEECH_LEAVES, BIRCH_LEAVES, BLUE_FIG_LEAVES, BOTTLE_TREE_LEAVES,
            BRAMBLE_LEAVES, BURNED_LEAVES, CAMPHOR_LEAVES, CEDAR_LEAVES,
            CRYSTAL_LEAVES, DEAD_LEAVES, DRY_LEAVES, FILTER_TREE_LEAVES,
            FILTER_TREE_WOOD_AND_LEAVES, FIR_LEAVES, FIR_LEAVES_TIP,
            FIRE_LEAVES, FOREST_FLOOR_LEAVES, GIANT_PALM_LEAVES,
            GUMBOAB_LEAVES, JUNGLE_FLOOR_LEAVES, MAPLE_LEAVES, OAK_LEAVES,
            PALM_LEAVES, PALO_LEAVES, PETRIFIED_PINE_LEAVES, POISONED_LEAVES,
            RED_FIR_LEAVES, REDWOOD_LEAVES, SHALLOW_LEAVES, SNOWY_FIR_LEAVES,
            SNOWY_FIR_LEAVES_TIP, SNOWY_LEAVES, SPIRAL_LEAVES,
            STORM_BARK_LEAVES, TROPICAL_LEAVES, WILD_WISTERIA_LEAVES,
            WILLOW_LEAVES
        ));
        // Bushes & Brambles
        list.addAll(Arrays.asList(
            ARID_BRAMBLE, GREEN_BRAMBLE, WINTER_BRAMBLE, BUSH, ARID_BUSH,
            ARID_PALM_BUSH, RED_ARID_BUSH, BUSHY_ARID_GRASS, DRY_BRAMBLE,
            BIG_CRYSTAL_BUSH, DEAD_BUSH, DEAD_HANGING_BUSH, SHRUB,
            LARGE_DEAD_BUSH, GREEN_BUSH, EXOTIC_HANGING_BUSH, JUNGLE_BUSH,
            LUSH_BIG_BUSH, WET_BUSH, FROZEN_SHRUB, BUSHY_WINTER_GRASS,
            SNOWY_WINTER_BUSH, RED_WINTER_BUSH
        ));
        // Cacti
        list.addAll(Arrays.asList(
            CACTUS_BOTTOM, CACTUS_MIDDLE, CACTUS_TOP, CACTUS_BALL,
            CACTUS_FLOWER, LARGE_FLAT_CACTUS, FLAT_CACTUS, SMALL_FLAT_CACTUS
        ));
        // Coral Blocks
        list.addAll(Arrays.asList(
            BLUE_CORAL_BLOCK, CYAN_CORAL_BLOCK, GREEN_CORAL_BLOCK,
            GRAY_CORAL_BLOCK, LIME_GREEN_CORAL_BLOCK, ORANGE_CORAL_BLOCK,
            PINK_CORAL_BLOCK, PURPLE_CORAL_BLOCK, POISONED_CORAL_BLOCK,
            RED_CORAL_BLOCK, VIOLET_CORAL_BLOCK, WHITE_CORAL_BLOCK,
            YELLOW_CORAL_BLOCK
        ));
        // Coral Bushes
        list.addAll(Arrays.asList(
            BLUE_CORAL_BUSH, CYAN_CORAL_BUSH, GREEN_CORAL_BUSH,
            GRAY_CORAL_BUSH, NEON_CORAL_BUSH, ORANGE_CORAL_BUSH,
            PINK_CORAL_BUSH, POISONED_CORAL_BUSH, PURPLE_CORAL_BUSH,
            RED_CORAL_BUSH, VIOLET_CORAL_BUSH, WHITE_CORAL_BUSH,
            YELLOW_CORAL_BUSH
        ));
        // Coral Models
        list.addAll(Arrays.asList(
            BLUE_CORAL_SPONGE, CYAN_CORAL_SPONGE, GREEN_CORAL_TUBES,
            GRAY_BRACKET_CORAL, LIME_CORAL_SPONGE, ORANGE_BRACKET_CORAL,
            PINK_FAN_CORAL, PURPLE_CORAL_TUBES, RED_CORAL_SPONGE,
            SEA_ANEMONE, WHITE_CORAL_SPONGE, YELLOW_CORAL_TUBES
        ));
        // Berry Bushes
        list.addAll(Arrays.asList(
            BERRY_BUSH, WET_BERRY_BUSH, WINTER_BERRY_BUSH
        ));
        // Special Flora (Mushrooms, Blood, Azure, Storm)
        list.addAll(Arrays.asList(
            BLOOD_ROSE, BLOOD_CAP_MUSHROOM, BLOOD_LEAF,
            AZURE_FERN, AZURE_CAP_MUSHROOM, AZURE_KELP,
            BROWN_MUSHROOM_MYCELIUM, LARGE_BOOMSHROOM, SMALL_BOOMSHROOM,
            BROWN_CAP_MUSHROOM, SPOTTED_GREEN_CAP_MUSHROOM,
            SPOTTED_ALLIUM_CAP_MUSHROOM, RED_CAP_MUSHROOM, WHITE_CAP_MUSHROOM,
            BLUE_COMMON_MUSHROOM, BROWN_COMMON_MUSHROOM,
            PUFFY_GREEN_COMMON_MUSHROOM, BLUE_FLAT_CAP_MUSHROOM,
            GREEN_FLAT_CAP_MUSHROOM, BLUE_GLOWING_MUSHROOM,
            GREEN_GLOWING_MUSHROOM, ORANGE_GLOWING_MUSHROOM,
            PURPLE_GLOWING_MUSHROOM, RED_GLOWING_MUSHROOM,
            VIOLET_GLOWING_MUSHROOM, BROWN_MUSHROOM_SHELF,
            GREEN_MUSHROOM_SHELF, YELLOW_MUSHROOM_SHELVES,
            STORM_THISTLE, STORM_CAP_MUSHROOM, STORM_SAPLING
        ));
        // Ferns & Nettles
        list.addAll(Arrays.asList(
            FERN, ARID_FERN, FOREST_FERN, JUNGLE_FERN, TALL_FERN,
            WET_FERN, GIANT_WET_FERN, BLUE_NETTLE, LARGE_FERN,
            CYAN_FESTUCA, NETTLE, ASHY_BUSH, BUSHY_ORANGE_FERN,
            POISONED_NETTLE, PURPLE_NETTLE, RED_FEATHER_LEAF,
            PURPLE_FLOWERS, FROST_LEAF
        ));
        // Flowers
        list.addAll(Arrays.asList(
            YELLOW_ARID_FLOWER_BUSH, BLUE_HIBISCUS, BLUE_ALOE,
            CYAN_ARID_FLOWER, CYAN_HIBISCUS, LINEN_WEED, SANDY_LION,
            JUNGLE_FLOWER, LIME_SUCCULENT, CHRYSANTHEMUM,
            COMMON_ORANGE_FLOWER, COMMON_PINK_FLOWER, ALLIUM,
            CARMINE_PATCHED_THORN, COMMON_PINK_FLOWER_POISONED,
            PURPLE_ARID_FLOWER, LAVA_FLOWER, RED_ARID_FLOWER, POPPY,
            CAMPANULA_FLOWER, VIOLETS, WHITE_HYDRANGEA, DAISY
        ));
        // Grass Plants
        list.addAll(Arrays.asList(
            SHORT_CAVE_GRASS, DRY_GRASS_PLANT, TALL_DRY_GRASS,
            GNARLED_GRASS, SHORT_GNARLED_GRASS, TALL_GNARLED_GRASS,
            JUNGLE_GRASS, SHORT_JUNGLE_GRASS, TALL_JUNGLE_GRASS,
            LUSH_GRASS_PLANT, SHORT_LUSH_GRASS_PLANT, TALL_LUSH_GRASS_PLANT,
            POISON_GRASS, SHORT_POISON_GRASS, ROCKY_GRASS,
            SHORT_ROCKY_GRASS, TALL_ROCKY_GRASS, GRASS_PLANT,
            OVERGROWN_SHARP_GRASS, SHORT_SHARP_GRASS, TALL_GRASS,
            WILD_SHARP_GRASS, SNOWY_GRASS, SHORT_SNOWY_GRASS,
            TALL_SNOWY_GRASS, WET_GRASS_PLANT, OVERGROWN_WET_GRASS,
            SHORT_WET_GRASS, TALL_WET_GRASS, WILD_WET_GRASS,
            WINTER_GRASS, SHORT_WINTER_GRASS, TALL_WINTER_GRASS
        ));
        // Moss
        list.addAll(Arrays.asList(
            BLUE_MOSS, MOSS, DARK_GREEN_MOSS,
            YELLOW_HIBISCUS, BLUE_FLAX, FIRE_FLOWER, DANDELION,
            PINK_FLAX, BERRY_FLAX, SMALL_DAISIES, LUCERNE, HEMLOCK,
            BLUE_CAVEWEED, AZURE_FLOWER, ORANGE_ORCHID, PINK_ORCHID,
            BLACK_ORCHID, PURPLE_ORCHID, RED_ORCHID, WHITE_ORCHID,
            YELLOW_ORCHID, POISONED_FLOWER, DELPHINIUM, BUSHY_CYAN_FERN,
            CYAN_FLOWER, PINK_CAMELLIA, LAVENDER, TALL_RED_RAFFLESIA,
            LARKSPUR, SUNFLOWER
        ));
        // Water plants
        list.addAll(Arrays.asList(
            BLUE_WATER_LILY, DUCKWEED, WATER_LILY, PURPLE_WATER_LILY,
            RED_WATER_LILY, WHITE_WATER_LILY
        ));
        // Arid grass
        list.addAll(Arrays.asList(
            PLANT_GRASS_ARID, SHORT_DRY_GRASS, BUSHY_SAVANNA_GRASS
        ));
        // Hanging moss
        list.addAll(Arrays.asList(
            BLUE_HANGING_MOSS, GREEN_HANGING_MOSS, DARK_GREEN_HANGING_MOSS,
            RED_HANGING_MOSS, YELLOW_HANGING_MOSS, RED_MOSS
        ));
        // Moss rugs
        list.addAll(Arrays.asList(
            BLUE_MOSS_RUG, GREEN_MOSS_RUG, DARK_GREEN_MOSS_RUG,
            SORREL_RUG, PINK_MOSS_RUG, RED_MOSS_RUG, YELLOW_MOSS_RUG
        ));
        // Short moss
        list.addAll(Arrays.asList(
            SHORT_BLUE_MOSS, SHORT_MOSS, SHORT_DARK_GREEN_MOSS,
            SHORT_RED_MOSS, SHORT_YELLOW_MOSS, YELLOW_MOSS
        ));
        // Reeds
        list.addAll(Arrays.asList(
            PAPYRUS_REEDS, LAVA_REEDS, RIVER_REEDS, POISON_REEDS,
            TALL_WATER_REEDS, WET_REEDS, WINTER_REEDS
        ));
        // Cave roots
        list.addAll(Arrays.asList(LEAFY_CAVE_ROOTS, CAVE_ROOTS));
        // Seaweed
        list.addAll(Arrays.asList(
            RED_ARID_SEAWEED, ARID_SEAWEED_STACK, SHORT_ARID_SEAWEED,
            TALL_ARID_SEAWEED, YELLOW_ARID_SEAWEED, EERIE_DEAD_SEAWEED,
            GHOSTLY_DEAD_SEAWEED, SHORT_DEAD_SEAWEED, DEAD_SEAWEED_STACK,
            TALL_DEAD_SEAWEED, SHORT_SEAWEED, GREEN_SEAWEED_BULBS,
            GREEN_SEAWEED, SEAWEED_MIDDLE, TALL_SEAWEED, WET_SEAWEED,
            AURORA_SEAWEED, BLUE_WINTER_SEAWEED, SHORT_WINTER_SEAWEED,
            WINTER_SEAWEED_STACK, TALL_WINTER_SEAWEED
        ));
        // Vine & Moss blocks
        list.addAll(Arrays.asList(
            VINE_RUG, YELLOW_MOSS_BLOCK, RED_MOSS_BLOCK,
            DARK_GREEN_MOSS_BLOCK, BLUE_MOSS_BLOCK, GREEN_MOSS_BLOCK
        ));

        ALL_TERRAINS = list.toArray(new HytaleTerrain[0]);
        PICK_LIST = ALL_TERRAINS;
    }

    public static HytaleTerrain[] getAdditionalTerrainsForCustomPicker() {
        Set<HytaleTerrain> primary = new HashSet<>(Arrays.asList(PICK_LIST));
        List<HytaleTerrain> additional = new ArrayList<>();
        for (HytaleTerrain terrain : ALL_TERRAINS) {
            if (!primary.contains(terrain)) {
                additional.add(terrain);
            }
        }
        return additional.toArray(new HytaleTerrain[0]);
    }

    /** 1-based index assigned to each terrain for per-pixel storage. */
    private static final Map<HytaleTerrain, Integer> INDEX_BY_TERRAIN;
    /** Terrains by 0-based array index (layer value - 1). */
    private static final HytaleTerrain[] TERRAIN_BY_INDEX;
    static {
        // Assign 1-based indices for layer storage (0 = not set)
        INDEX_BY_TERRAIN = new HashMap<>();
        TERRAIN_BY_INDEX = new HytaleTerrain[ALL_TERRAINS.length];
        for (int i = 0; i < ALL_TERRAINS.length; i++) {
            INDEX_BY_TERRAIN.put(ALL_TERRAINS[i], i + 1);
            TERRAIN_BY_INDEX[i] = ALL_TERRAINS[i];
        }
    }

    /**
     * Get the 1-based index of this terrain for per-pixel layer storage.
     * Returns 0 if this terrain is not in the standard list.
     */
    public int getLayerIndex() {
        Integer idx = INDEX_BY_TERRAIN.get(this);
        return (idx != null) ? idx : 0;
    }

    /**
     * Look up a terrain by its 1-based layer index.
     * Returns null if the index is out of range or 0.
     */
    public static HytaleTerrain getByLayerIndex(int index) {
        if (index < 1 || index > TERRAIN_BY_INDEX.length) return null;
        return TERRAIN_BY_INDEX[index - 1];
    }

    /** Map from block ID to terrain for quick lookup (includes ALL terrains). */
    private static final Map<String, HytaleTerrain> BLOCK_ID_MAP;
    static {
        Map<String, HytaleTerrain> map = new HashMap<>();
        for (HytaleTerrain t : ALL_TERRAINS) {
            if (t.block != null) {
                map.putIfAbsent(t.block.id, t);
            }
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

    public static List<HytaleTerrain> getAllTerrains() {
        return new ArrayList<>(Arrays.asList(ALL_TERRAINS));
    }

    /**
     * Look up a terrain by name (case-insensitive).
     */
    public static HytaleTerrain getByName(String name) {
        if (name == null) return null;
        for (HytaleTerrain t : ALL_TERRAINS) {
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
