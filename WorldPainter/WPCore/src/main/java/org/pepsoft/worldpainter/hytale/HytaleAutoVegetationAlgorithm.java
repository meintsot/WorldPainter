package org.pepsoft.worldpainter.hytale;

import java.util.*;

/**
 * Pure per-pixel decision logic for {@link HytaleAutoVegetationLayer}. No
 * dependency on the exporter or the tile grid — exists so the algorithm can
 * be unit-tested in isolation.
 */
public final class HytaleAutoVegetationAlgorithm {

    private static final Set<String> REJECTED_SUBSTRATE_IDS = new HashSet<>(Arrays.asList(
            "Liquid_Water_Source", "Liquid_Lava_Source",
            "Block_Magma", "Block_Ice", "Block_Snow"
    ));

    private HytaleAutoVegetationAlgorithm() {}

    /**
     * Whether {@code plant} is allowed to be placed at {@code height + 1}
     * given that {@code substrate} is the block at {@code height}.
     * Default: any solid non-fluid block is acceptable. Liquids, magma, ice,
     * and snow are rejected — see {@link #REJECTED_SUBSTRATE_IDS}.
     */
    public static boolean isValidSubstrateFor(HytaleBlock plant, HytaleBlock substrate) {
        if (substrate == null || substrate.id == null) {
            return false;
        }
        return !REJECTED_SUBSTRATE_IDS.contains(substrate.id);
    }

    /**
     * Decide whether to place a plant at this pixel and which one. Returns
     * the chosen plant's {@code HytaleTerrain} UUID, or {@code null} for
     * "no plant".
     *
     * <p>Two stages: a coverage gate (RNG vs coverage %), then a weighted
     * pick over the plant list.
     */
    public static UUID pick(HytaleAutoVegetationSettings.BiomeVegetationConfig cfg, Random rng) {
        if (cfg == null || cfg.getCoveragePercent() == 0 || cfg.getPlants().isEmpty()) {
            return null;
        }
        if (rng.nextInt(100) >= cfg.getCoveragePercent()) {
            return null;
        }
        int total = 0;
        for (HytaleAutoVegetationSettings.PlantEntry e : cfg.getPlants()) {
            total += e.getOccurrenceWeight();
        }
        if (total == 0) {
            return null;
        }
        int roll = rng.nextInt(total);
        int acc = 0;
        for (HytaleAutoVegetationSettings.PlantEntry e : cfg.getPlants()) {
            acc += e.getOccurrenceWeight();
            if (roll < acc) {
                return e.getHytaleTerrainId();
            }
        }
        // unreachable
        return cfg.getPlants().get(cfg.getPlants().size() - 1).getHytaleTerrainId();
    }

    /**
     * Deterministic per-pixel RNG seed. Combines the user-visible global seed
     * with global block coordinates so plants don't reshuffle across exports
     * and don't seam at tile boundaries.
     *
     * <p>Mixing uses the splitmix64 finalizer constants to achieve good
     * avalanche, ensuring that adjacent pixels receive statistically
     * independent sequences.
     */
    public static long seedFor(long globalSeed, int tileX, int tileZ, int xInTile, int zInTile) {
        long blockX = ((long) tileX) * 128L + xInTile;
        long blockZ = ((long) tileZ) * 128L + zInTile;
        long h = globalSeed;
        h = h * 0x9E3779B97F4A7C15L + blockX;
        h = h * 0x9E3779B97F4A7C15L + blockZ;
        // Final mix (splitmix64 finalizer)
        h ^= (h >>> 30);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 27);
        h *= 0x94D049BB133111EBL;
        h ^= (h >>> 31);
        return h;
    }
}
