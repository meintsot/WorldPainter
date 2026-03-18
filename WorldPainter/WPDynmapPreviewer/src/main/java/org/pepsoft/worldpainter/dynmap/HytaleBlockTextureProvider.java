package org.pepsoft.worldpainter.dynmap;

import org.pepsoft.worldpainter.hytale.HytaleBlockRegistry;
import org.pepsoft.worldpainter.hytale.HytaleTerrain;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides and caches Hytale block face textures for the 3D isometric renderer.
 * Loads the actual PNG textures from the HytaleAssets directory and stores
 * top and side face images per block ID.
 */
class HytaleBlockTextureProvider {

    /**
     * Get the face textures for a Hytale block. Returns a {@link FaceTextures}
     * instance (which may have null top/side if textures couldn't be loaded), or
     * {@code null} if the block ID is unknown.
     */
    static FaceTextures getTextures(String blockId) {
        return CACHE.computeIfAbsent(blockId, HytaleBlockTextureProvider::loadTextures);
    }

    private static FaceTextures loadTextures(String blockId) {
        BufferedImage[] images = HytaleTerrain.loadBlockFaceTexturesForId(blockId);
        if (images != null) {
            BufferedImage top = images[0];
            BufferedImage side = images[1];
            // If one is missing, use the other for both faces
            if (top == null) {
                top = side;
            }
            if (side == null) {
                side = top;
            }
            if (top != null) {
                return new FaceTextures(top, side);
            }
        }
        // No textures found — create a solid-colour fallback from block registry
        int colour = getFallbackColour(blockId);
        BufferedImage solid = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        solid.setRGB(0, 0, colour);
        return new FaceTextures(solid, solid);
    }

    private static int getFallbackColour(String blockId) {
        // Use semi-transparent alpha for vegetation/surface-only blocks
        // (leaves, flowers, ferns, bushes, etc.) so they don't render as
        // opaque cubes when actual textures are unavailable.
        HytaleBlockRegistry.Category category = HytaleBlockRegistry.getCategoryForBlock(blockId);
        int alpha = (category != null && category.isSurfaceOnly()) ? VEGETATION_ALPHA : 0xFF;
        Integer colour = HytaleBlockRegistry.getBlockColour(blockId);
        if (colour != null) {
            return (alpha << 24) | (colour & 0xFFFFFF);
        }
        if (category != null) {
            return (alpha << 24) | (getCategoryColour(category) & 0xFFFFFF);
        }
        return 0xFFA0A0A0;
    }

    private static int getCategoryColour(HytaleBlockRegistry.Category category) {
        switch (category) {
            case SOIL:              return 0xFF8B6914;
            case SAND:              return 0xFFE8D5A3;
            case CLAY:              return 0xFFC4A882;
            case SNOW_ICE:          return 0xFFEEF0F0;
            case GRAVEL:            return 0xFF9E9E9E;
            case ROCK:              return 0xFF808080;
            case ROCK_CONSTRUCTION: return 0xFFAAAAAA;
            case ORE:               return 0xFF8A7A6A;
            case CRYSTAL_GEM:       return 0xFF9ABDE0;
            case WOOD_NATURAL:      return 0xFF8B6B3A;
            case WOOD_PLANKS:       return 0xFFB89560;
            case LEAVES:            return 0xFF3A7A28;
            case GRASS_PLANTS:      return 0xFF4C9A2A;
            case FLOWERS:           return 0xFFD25B9A;
            case FERNS:             return 0xFF3E8A30;
            case BUSHES:            return 0xFF2E6A20;
            case CACTUS:            return 0xFF5A8A40;
            case MOSS_VINES:        return 0xFF4A7A30;
            case MUSHROOMS:         return 0xFFA06040;
            case CROPS:             return 0xFF8AAA40;
            case CORAL:             return 0xFFE08060;
            case SEAWEED:           return 0xFF2A7A50;
            case SAPLINGS_FRUITS:   return 0xFF6AAA30;
            case RUBBLE:            return 0xFF7A7A6A;
            case DECORATION:        return 0xFFAA8A60;
            case CLOTH:             return 0xFFD0C0B0;
            case HIVE:              return 0xFFC8A840;
            case RUNIC:             return 0xFF6060AA;
            case FLUID:             return 0xFF3070D0;
            case SPECIAL:           return 0xFF505050;
            default:                return 0xFFA0A0A0;
        }
    }

    static final class FaceTextures {
        final BufferedImage top;
        final BufferedImage side;

        FaceTextures(BufferedImage top, BufferedImage side) {
            this.top = top;
            this.side = side;
        }

        /**
         * Sample the texture at the given UV coordinates (0..1) for the specified face.
         * Returns an ARGB colour value.
         */
        int sample(boolean isTopOrBottom, double u, double v) {
            BufferedImage tex = isTopOrBottom ? top : side;
            int texW = tex.getWidth();
            int texH = tex.getHeight();
            int sx = Math.min(texW - 1, Math.max(0, (int) (u * texW)));
            int sy = Math.min(texH - 1, Math.max(0, (int) (v * texH)));
            return tex.getRGB(sx, sy);
        }
    }

    /** Alpha for vegetation blocks when no real texture is available. Matches HytaleTerrain.LEAF_ALPHA. */
    private static final int VEGETATION_ALPHA = 0xD0;

    private static final Map<String, FaceTextures> CACHE = new ConcurrentHashMap<>();
}
