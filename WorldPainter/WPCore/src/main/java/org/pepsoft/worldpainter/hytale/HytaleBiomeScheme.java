package org.pepsoft.worldpainter.hytale;

import org.pepsoft.worldpainter.BiomeScheme;
import org.pepsoft.worldpainter.ColourScheme;

/**
 * {@link BiomeScheme} implementation for Hytale biomes.
 * Provides biome metadata (names and tint-based colours) for the UI.
 */
public class HytaleBiomeScheme implements BiomeScheme {

    public static final HytaleBiomeScheme INSTANCE = new HytaleBiomeScheme();

    private HytaleBiomeScheme() {
    }

    @Override
    public void setSeed(long seed) {
        // Not used for Hytale.
    }

    @Override
    public int getBiomeCount() {
        // IDs 0-254 are biomes, 255 is Automatic.
        return 256;
    }

    @Override
    public int[] getBiomes(int x, int y, int width, int height) {
        int[] buffer = new int[width * height];
        getBiomes(x, y, width, height, buffer);
        return buffer;
    }

    @Override
    public void getBiomes(int x, int y, int width, int height, int[] buffer) {
        // Placeholder for non-generation contexts.
        java.util.Arrays.fill(buffer, HytaleBiome.ZONE1_PLAINS.getId());
    }

    @Override
    public int getColour(int biome, ColourScheme colourScheme) {
        if (biome == HytaleBiome.BIOME_AUTO) {
            return 0x808080;
        }
        HytaleBiome hytaleBiome = HytaleBiome.getById(biome);
        // Match UI colour to exported chunk tint.
        return (hytaleBiome != null) ? (hytaleBiome.getTint() & 0x00ffffff) : 0x808080;
    }

    @Override
    public boolean[][] getPattern(int biome) {
        // Use unpatterned swatches so icons/palette reflect actual tint values.
        return null;
    }

    @Override
    public String getBiomeName(int biome) {
        if (biome == HytaleBiome.BIOME_AUTO) {
            return HytaleBiome.BIOME_AUTO_NAME;
        }
        HytaleBiome hytaleBiome = HytaleBiome.getById(biome);
        return (hytaleBiome != null) ? hytaleBiome.getDisplayName() : "Unknown";
    }

    @Override
    public String getStringId(int biome) {
        if (biome == HytaleBiome.BIOME_AUTO) {
            return "hytale:auto";
        }
        HytaleBiome hytaleBiome = HytaleBiome.getById(biome);
        if (hytaleBiome != null) {
            return "hytale:" + hytaleBiome.getName();
        }
        throw new IllegalArgumentException("Unknown Hytale biome ID: " + biome);
    }

    @Override
    public boolean isBiomePresent(int biome) {
        return (biome == HytaleBiome.BIOME_AUTO) || (HytaleBiome.getById(biome) != null);
    }
}
