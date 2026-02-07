package org.pepsoft.worldpainter.hytale;

import org.pepsoft.worldpainter.BiomeScheme;
import org.pepsoft.worldpainter.ColourScheme;

/**
 * {@link BiomeScheme} implementation for Hytale biomes.
 * Provides biome metadata (names, colors, patterns) needed by the
 * BiomesPanel and related UI components.
 */
public class HytaleBiomeScheme implements BiomeScheme {

    public static final HytaleBiomeScheme INSTANCE = new HytaleBiomeScheme();

    private HytaleBiomeScheme() {}

    @Override
    public void setSeed(long seed) {
        // Not used for Hytale — biome generation is not seed-based in the panel
    }

    @Override
    public int getBiomeCount() {
        return 256; // We use IDs 0–254 for biomes, 255 for Auto
    }

    @Override
    public int[] getBiomes(int x, int y, int width, int height) {
        // Not used — Hytale does not support biome preview generation
        int[] buffer = new int[width * height];
        getBiomes(x, y, width, height, buffer);
        return buffer;
    }

    @Override
    public void getBiomes(int x, int y, int width, int height, int[] buffer) {
        // Fill with default biome (Zone1 Plains)
        java.util.Arrays.fill(buffer, HytaleBiome.ZONE1_PLAINS.getId());
    }

    @Override
    public int getColour(int biome, ColourScheme colourScheme) {
        if (biome == HytaleBiome.BIOME_AUTO) {
            return 0x808080; // Gray for "Automatic"
        }
        HytaleBiome hb = HytaleBiome.getById(biome);
        return (hb != null) ? hb.getDisplayColor() : 0x808080;
    }

    @Override
    public boolean[][] getPattern(int biome) {
        if (biome == HytaleBiome.BIOME_AUTO) {
            return null;
        }
        HytaleBiome hb = HytaleBiome.getById(biome);
        if (hb == null) return null;

        // Create a distinctive pattern based on category
        HytaleBiome.Category cat = hb.getCategory();
        switch (cat) {
            case ZONE1_CAVES:
            case ZONE2_CAVES:
            case ZONE3_CAVES:
            case ZONE4_CAVES:
                return PATTERN_CAVES;
            case OCEAN:
                return PATTERN_OCEAN;
            case SPECIAL:
                return PATTERN_SPECIAL;
            default:
                return null; // No pattern for surface biomes — solid color
        }
    }

    @Override
    public String getBiomeName(int biome) {
        if (biome == HytaleBiome.BIOME_AUTO) {
            return HytaleBiome.BIOME_AUTO_NAME;
        }
        HytaleBiome hb = HytaleBiome.getById(biome);
        return (hb != null) ? hb.getDisplayName() : "Unknown";
    }

    @Override
    public String getStringId(int biome) {
        if (biome == HytaleBiome.BIOME_AUTO) {
            return "hytale:auto";
        }
        HytaleBiome hb = HytaleBiome.getById(biome);
        if (hb != null) {
            return "hytale:" + hb.getName();
        }
        throw new IllegalArgumentException("Unknown Hytale biome ID: " + biome);
    }

    @Override
    public boolean isBiomePresent(int biome) {
        return (biome == HytaleBiome.BIOME_AUTO) || (HytaleBiome.getById(biome) != null);
    }

    // ─── Patterns ──────────────────────────────────────────────────────

    /** Dotted pattern for caves */
    private static final boolean[][] PATTERN_CAVES = createDotsPattern();
    /** Wave pattern for ocean */
    private static final boolean[][] PATTERN_OCEAN = createWavePattern();
    /** Diamond pattern for special biomes */
    private static final boolean[][] PATTERN_SPECIAL = createDiamondPattern();

    private static boolean[][] createDotsPattern() {
        boolean[][] pattern = new boolean[16][16];
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                pattern[x][y] = (x % 4 == 0) && (y % 4 == 0);
            }
        }
        return pattern;
    }

    private static boolean[][] createWavePattern() {
        boolean[][] pattern = new boolean[16][16];
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                int wave = (int) (Math.sin(x * 0.4) * 2) + 4;
                pattern[x][y] = (y % 8 == wave) || (y % 8 == wave + 1);
            }
        }
        return pattern;
    }

    private static boolean[][] createDiamondPattern() {
        boolean[][] pattern = new boolean[16][16];
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                int cx = (x % 8) - 4;
                int cy = (y % 8) - 4;
                pattern[x][y] = (Math.abs(cx) + Math.abs(cy) == 3);
            }
        }
        return pattern;
    }
}
