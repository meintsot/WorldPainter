package org.pepsoft.worldpainter.hytale;

import org.junit.Test;

import java.io.StringReader;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

public class HytaleAutoVegetationDefaultsTest {

    @Test
    public void parsesWellFormedJson() {
        String json = "{ \"Zone1_Drifting_Plains\": { \"coverage\": 12, " +
                "\"plants\": [ {\"terrain\": \"Plant_Grass_Lush_Tall\", \"weight\": 60} ] } }";

        Map<Integer, HytaleAutoVegetationSettings.BiomeVegetationConfig> parsed =
                HytaleAutoVegetationDefaults.parse(new StringReader(json));

        int driftingPlainsId = HytaleBiome.DRIFTING_PLAINS.getId();
        assertTrue(parsed.containsKey(driftingPlainsId));
        assertEquals(12, parsed.get(driftingPlainsId).getCoveragePercent());
        assertEquals(1, parsed.get(driftingPlainsId).getPlants().size());
    }

    @Test
    public void unknownBiomeKeyIsSkipped() {
        String json = "{ \"Zone99_Imaginary\": { \"coverage\": 5, \"plants\": [] }, " +
                "\"Zone1_Drifting_Plains\": { \"coverage\": 12, \"plants\": [] } }";

        Map<Integer, HytaleAutoVegetationSettings.BiomeVegetationConfig> parsed =
                HytaleAutoVegetationDefaults.parse(new StringReader(json));

        assertEquals(1, parsed.size());
        assertTrue(parsed.containsKey(HytaleBiome.DRIFTING_PLAINS.getId()));
    }

    @Test
    public void unknownPlantTerrainIsDropped() {
        String json = "{ \"Zone1_Drifting_Plains\": { \"coverage\": 12, \"plants\": [ " +
                "{\"terrain\": \"Plant_DoesNotExist\", \"weight\": 50}, " +
                "{\"terrain\": \"Plant_Grass_Lush_Tall\",   \"weight\": 50} " +
                "] } }";

        Map<Integer, HytaleAutoVegetationSettings.BiomeVegetationConfig> parsed =
                HytaleAutoVegetationDefaults.parse(new StringReader(json));

        HytaleAutoVegetationSettings.BiomeVegetationConfig cfg =
                parsed.get(HytaleBiome.DRIFTING_PLAINS.getId());
        assertNotNull(cfg);
        // Only the resolvable plant survives
        assertEquals(1, cfg.getPlants().size());
    }

    @Test
    public void emptyJsonReturnsEmptyMap() {
        Map<Integer, HytaleAutoVegetationSettings.BiomeVegetationConfig> parsed =
                HytaleAutoVegetationDefaults.parse(new StringReader("{}"));
        assertTrue(parsed.isEmpty());
    }

    @Test
    public void applyToSeedsSettings() {
        String json = "{ \"Zone1_Drifting_Plains\": { \"coverage\": 12, \"plants\": [] } }";
        HytaleAutoVegetationSettings settings = new HytaleAutoVegetationSettings();

        HytaleAutoVegetationDefaults.applyTo(settings, new StringReader(json));

        assertEquals(12,
                settings.getByBiome().get(HytaleBiome.DRIFTING_PLAINS.getId()).getCoveragePercent());
    }

    @Test
    public void shippedDefaultsJsonLoadsWithoutError() {
        // Reads the actual resource shipped in src/main/resources.
        HytaleAutoVegetationSettings settings = new HytaleAutoVegetationSettings();
        HytaleAutoVegetationDefaults.applyShippedDefaultsTo(settings);
        assertFalse("shipped JSON should populate at least one biome",
                settings.getByBiome().isEmpty());
    }

    /**
     * Guard against drift between the shipped JSON and the HytaleTerrain registry.
     * If any plant name in the JSON does not resolve, this test fails.
     */
    @Test
    public void allShippedPlantNamesResolveInRegistry() {
        java.util.List<String> unresolved = HytaleAutoVegetationDefaults.findUnresolvedShippedPlantNames();
        assertTrue("Unresolved plant names in shipped defaults: " + unresolved,
                unresolved.isEmpty());
    }
}
