package org.pepsoft.worldpainter.hytale;

import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class HytaleAutoVegetationAlgorithmTest {

    /** Stub substrate that just reports an id; used for isValidSubstrateFor tests. */
    private static HytaleBlock blockOf(String id) {
        return HytaleBlock.of(id);   // HytaleBlock constructor is private; use factory method
    }

    @Test
    public void substrateAllowsSolidGround() {
        assertTrue(HytaleAutoVegetationAlgorithm.isValidSubstrateFor(
                blockOf("Plant_Grass_Lush_Tall"), blockOf("Block_Grass")));
        assertTrue(HytaleAutoVegetationAlgorithm.isValidSubstrateFor(
                blockOf("Plant_Grass_Lush_Tall"), blockOf("Block_Dirt")));
        assertTrue(HytaleAutoVegetationAlgorithm.isValidSubstrateFor(
                blockOf("Plant_Cactus_1"),       blockOf("Block_Sand")));
    }

    @Test
    public void substrateRejectsLavaAndWater() {
        assertFalse(HytaleAutoVegetationAlgorithm.isValidSubstrateFor(
                blockOf("Plant_Grass_Lush_Tall"), blockOf("Liquid_Water_Source")));
        assertFalse(HytaleAutoVegetationAlgorithm.isValidSubstrateFor(
                blockOf("Plant_Grass_Lush_Tall"), blockOf("Liquid_Lava_Source")));
    }

    @Test
    public void substrateRejectsMagmaIceSnow() {
        assertFalse(HytaleAutoVegetationAlgorithm.isValidSubstrateFor(
                blockOf("Plant_Grass_Lush_Tall"), blockOf("Block_Magma")));
        assertFalse(HytaleAutoVegetationAlgorithm.isValidSubstrateFor(
                blockOf("Plant_Grass_Lush_Tall"), blockOf("Block_Ice")));
        assertFalse(HytaleAutoVegetationAlgorithm.isValidSubstrateFor(
                blockOf("Plant_Grass_Lush_Tall"), blockOf("Block_Snow")));
    }

    @Test
    public void coverageZeroNeverPlaces() {
        HytaleAutoVegetationSettings.BiomeVegetationConfig cfg =
                new HytaleAutoVegetationSettings.BiomeVegetationConfig(0,
                        Collections.singletonList(new HytaleAutoVegetationSettings.PlantEntry(
                                someTerrainId(), 100)));
        int placed = 0;
        for (int i = 0; i < 1000; i++) {
            if (HytaleAutoVegetationAlgorithm.pick(cfg, new Random(i)) != null) placed++;
        }
        assertEquals(0, placed);
    }

    @Test
    public void coverageHundredAlwaysPlaces() {
        HytaleAutoVegetationSettings.BiomeVegetationConfig cfg =
                new HytaleAutoVegetationSettings.BiomeVegetationConfig(100,
                        Collections.singletonList(new HytaleAutoVegetationSettings.PlantEntry(
                                someTerrainId(), 100)));
        for (int i = 0; i < 100; i++) {
            assertNotNull(HytaleAutoVegetationAlgorithm.pick(cfg, new Random(i)));
        }
    }

    @Test
    public void coverageFiftyIsApproximatelyFiftyPercent() {
        HytaleAutoVegetationSettings.BiomeVegetationConfig cfg =
                new HytaleAutoVegetationSettings.BiomeVegetationConfig(50,
                        Collections.singletonList(new HytaleAutoVegetationSettings.PlantEntry(
                                someTerrainId(), 100)));
        int placed = 0;
        int samples = 10_000;
        Random rng = new Random(123);
        for (int i = 0; i < samples; i++) {
            if (HytaleAutoVegetationAlgorithm.pick(cfg, rng) != null) placed++;
        }
        double ratio = placed / (double) samples;
        assertTrue("expected ~0.50, got " + ratio, ratio > 0.47 && ratio < 0.53);
    }

    @Test
    public void weightsProduceApproximateProportions() {
        UUID a = new UUID(0, 1);
        UUID b = new UUID(0, 2);
        UUID c = new UUID(0, 3);
        HytaleAutoVegetationSettings.BiomeVegetationConfig cfg =
                new HytaleAutoVegetationSettings.BiomeVegetationConfig(100, Arrays.asList(
                        new HytaleAutoVegetationSettings.PlantEntry(a, 60),
                        new HytaleAutoVegetationSettings.PlantEntry(b, 30),
                        new HytaleAutoVegetationSettings.PlantEntry(c, 10)));
        Map<UUID, Integer> counts = new HashMap<>();
        Random rng = new Random(7);
        int samples = 10_000;
        for (int i = 0; i < samples; i++) {
            UUID picked = HytaleAutoVegetationAlgorithm.pick(cfg, rng);
            counts.merge(picked, 1, Integer::sum);
        }
        assertEquals(0.60, counts.get(a) / (double) samples, 0.02);
        assertEquals(0.30, counts.get(b) / (double) samples, 0.02);
        assertEquals(0.10, counts.get(c) / (double) samples, 0.02);
    }

    @Test
    public void emptyPlantListReturnsNullEvenAtFullCoverage() {
        HytaleAutoVegetationSettings.BiomeVegetationConfig cfg =
                new HytaleAutoVegetationSettings.BiomeVegetationConfig(100, Collections.emptyList());
        assertNull(HytaleAutoVegetationAlgorithm.pick(cfg, new Random()));
    }

    @Test
    public void seedMixIsDeterministic() {
        long s1 = HytaleAutoVegetationAlgorithm.seedFor(42L, 1, 1, 5, 7);
        long s2 = HytaleAutoVegetationAlgorithm.seedFor(42L, 1, 1, 5, 7);
        assertEquals(s1, s2);
    }

    @Test
    public void seedMixDiffersByCoordinates() {
        long s1 = HytaleAutoVegetationAlgorithm.seedFor(42L, 1, 1, 5, 7);
        long s2 = HytaleAutoVegetationAlgorithm.seedFor(42L, 1, 1, 5, 8);
        assertNotEquals(s1, s2);
    }

    private static UUID someTerrainId() {
        return UUID.fromString("00000000-0000-0000-0000-000000000001");
    }
}
