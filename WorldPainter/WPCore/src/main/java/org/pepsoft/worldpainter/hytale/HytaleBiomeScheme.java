package org.pepsoft.worldpainter.hytale;

import org.pepsoft.worldpainter.BiomeScheme;
import org.pepsoft.worldpainter.ColourScheme;

/**
 * {@link BiomeScheme} implementation for Hytale biomes.
 * Provides biome metadata (names and tint-based colours) for the UI.
 *
 * <p>Each biome category gets a distinctive 16×16 pattern so that painted
 * biomes are clearly visible on the 2D map, even when the biome colour is
 * similar to the underlying terrain colour. This mirrors how Minecraft biomes
 * use patterns (tree shapes, mountain shapes, etc.) to stay visually distinct.</p>
 */
public class HytaleBiomeScheme implements BiomeScheme {

    public static final HytaleBiomeScheme INSTANCE = new HytaleBiomeScheme();

    /** Lazily populated patterns array, indexed by biome ID. */
    private final boolean[][][] patterns = new boolean[256][][];
    private boolean patternsInitialised;

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
        java.util.Arrays.fill(buffer, HytaleBiome.DRIFTING_PLAINS.getId());
    }

    @Override
    public int getColour(int biome, ColourScheme colourScheme) {
        if (biome == HytaleBiome.BIOME_AUTO) {
            return 0x808080;
        }
        HytaleBiome hytaleBiome = HytaleBiome.getById(biome);
        // Use the displayColor for the map overlay — it is chosen to contrast
        // with the terrain, unlike the tint which often matches the terrain.
        return (hytaleBiome != null) ? hytaleBiome.getDisplayColor() : 0x808080;
    }

    @Override
    public boolean[][] getPattern(int biome) {
        if (biome == HytaleBiome.BIOME_AUTO) {
            return null;
        }
        ensurePatternsInitialised();
        return (biome >= 0 && biome < patterns.length) ? patterns[biome] : null;
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

    // ─── Pattern generation ────────────────────────────────────────────

    private synchronized void ensurePatternsInitialised() {
        if (patternsInitialised) {
            return;
        }
        for (HytaleBiome hb : HytaleBiome.getAllBiomes()) {
            patterns[hb.getId()] = generatePattern(hb);
        }
        patternsInitialised = true;
    }

    /**
     * Generate a distinctive 16×16 boolean pattern for a biome based on its
     * category. The pattern determines where black foreground pixels are drawn;
     * {@code true} = foreground dot, {@code false} = biome colour.
     */
    private static boolean[][] generatePattern(HytaleBiome biome) {
        switch (biome.getCategory()) {
            case ZONE1:
                // Emerald Wilds — sparse leaf / tree dots
                return patternLeaves(biome.getId());
            case ZONE2:
                // Howling Sands — scattered sand grain dots
                return patternDots(biome.getId());
            case ZONE3:
                // Frost Frontiers — diagonal frost lines
                return patternFrostLines(biome.getId());
            case ZONE4:
                // Devastated Lands — cross-hatch ash/fire
                return patternCrossHatch(biome.getId());
            case OCEAN:
                // Ocean — wavy horizontal lines
                return patternWaves();
            case ENCOUNTERS:
                // Encounters — diamond markers
                return patternDiamonds();
            case MISC:
            default:
                // Miscellaneous — sparse diagonal dots
                return patternDiagonalDots();
        }
    }

    /** Zone 1: small leaf-like cluster pattern, offset per biome. */
    private static boolean[][] patternLeaves(int seed) {
        boolean[][] p = new boolean[16][16];
        int off = (seed * 7) & 0xF;
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                int px = (x + off) & 0xF;
                int py = (y + off) & 0xF;
                // Place small 3-pixel leaf marks at staggered intervals
                if ((px % 6 == 0) && (py % 5 == 0)) {
                    p[x][y] = true;
                } else if ((px % 6 == 1) && (py % 5 == 1)) {
                    p[x][y] = true;
                } else if (((px + 3) % 6 == 0) && ((py + 2) % 5 == 0)) {
                    p[x][y] = true;
                }
            }
        }
        return p;
    }

    /** Zone 2: scattered grain/dot pattern, offset per biome. */
    private static boolean[][] patternDots(int seed) {
        boolean[][] p = new boolean[16][16];
        int off = (seed * 11) & 0xF;
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                int px = (x + off) & 0xF;
                int py = (y + off) & 0xF;
                if (((px * 5 + py * 3) & 0xF) < 2) {
                    p[x][y] = true;
                }
            }
        }
        return p;
    }

    /** Zone 3: diagonal frost / slash lines. */
    private static boolean[][] patternFrostLines(int seed) {
        boolean[][] p = new boolean[16][16];
        int off = (seed * 3) & 0xF;
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                int px = (x + off) & 0xF;
                int py = (y + off) & 0xF;
                if (((px + py) & 7) == 0) {
                    p[x][y] = true;
                }
            }
        }
        return p;
    }

    /** Zone 4: cross-hatch / ash pattern. */
    private static boolean[][] patternCrossHatch(int seed) {
        boolean[][] p = new boolean[16][16];
        int off = (seed * 5) & 0xF;
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                int px = (x + off) & 0xF;
                int py = (y + off) & 0xF;
                if ((px % 4 == 0) || (py % 4 == 0)) {
                    if (((px + py) & 1) == 0) {
                        p[x][y] = true;
                    }
                }
            }
        }
        return p;
    }

    /** Ocean: wavy horizontal lines. */
    private static boolean[][] patternWaves() {
        boolean[][] p = new boolean[16][16];
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                int wave = (int) (Math.sin(x * 0.8) * 1.5);
                if (((y + wave) & 7) == 0) {
                    p[x][y] = true;
                }
            }
        }
        return p;
    }

    /** Encounters: small diamond markers. */
    private static boolean[][] patternDiamonds() {
        boolean[][] p = new boolean[16][16];
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                int cx = (x % 8) - 4;
                int cy = (y % 8) - 4;
                if ((Math.abs(cx) + Math.abs(cy)) == 3) {
                    p[x][y] = true;
                }
            }
        }
        return p;
    }

    /** Misc: sparse diagonal dots. */
    private static boolean[][] patternDiagonalDots() {
        boolean[][] p = new boolean[16][16];
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                if (((x + y) % 5 == 0) && ((x - y + 16) % 7 == 0)) {
                    p[x][y] = true;
                }
            }
        }
        return p;
    }
}
