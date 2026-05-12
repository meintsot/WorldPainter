package org.pepsoft.worldpainter.hytale;

import org.junit.Test;
import org.pepsoft.worldpainter.layers.exporters.ExporterSettings;

import java.io.*;
import java.util.*;

import static org.junit.Assert.*;

public class HytaleAutoVegetationSettingsTest {

    @Test
    public void linksToTheCorrectLayer() {
        HytaleAutoVegetationSettings s = new HytaleAutoVegetationSettings();
        assertSame(HytaleAutoVegetationLayer.INSTANCE, s.getLayer());
    }

    @Test
    public void applyEverywhereIsFalseByDefault() {
        HytaleAutoVegetationSettings s = new HytaleAutoVegetationSettings();
        assertFalse(s.isApplyEverywhere());
    }

    @Test
    public void enabledIsTrueByDefault() {
        HytaleAutoVegetationSettings s = new HytaleAutoVegetationSettings();
        assertTrue(s.isEnabled());
    }

    @Test
    public void biomeMapStartsEmpty() {
        HytaleAutoVegetationSettings s = new HytaleAutoVegetationSettings();
        assertTrue(s.getByBiome().isEmpty());
    }

    @Test
    public void canStoreAndRetrieveABiomeConfig() {
        HytaleAutoVegetationSettings s = new HytaleAutoVegetationSettings();
        HytaleAutoVegetationSettings.BiomeVegetationConfig cfg =
                new HytaleAutoVegetationSettings.BiomeVegetationConfig(
                        12, Arrays.asList(
                                new HytaleAutoVegetationSettings.PlantEntry(UUID.randomUUID(), 60),
                                new HytaleAutoVegetationSettings.PlantEntry(UUID.randomUUID(), 40)));
        s.setBiomeConfig(7, cfg);

        assertSame(cfg, s.getByBiome().get(7));
        assertEquals(12, cfg.getCoveragePercent());
        assertEquals(2, cfg.getPlants().size());
    }

    @Test
    public void cloneIsDeep() {
        HytaleAutoVegetationSettings original = new HytaleAutoVegetationSettings();
        UUID terrainId = UUID.randomUUID();
        original.setBiomeConfig(7, new HytaleAutoVegetationSettings.BiomeVegetationConfig(
                12, new ArrayList<>(Collections.singletonList(
                        new HytaleAutoVegetationSettings.PlantEntry(terrainId, 100)))));

        HytaleAutoVegetationSettings copy = (HytaleAutoVegetationSettings) ((ExporterSettings) original).clone();
        copy.setBiomeConfig(7, new HytaleAutoVegetationSettings.BiomeVegetationConfig(99, Collections.emptyList()));

        assertEquals("original must not be mutated", 12,
                original.getByBiome().get(7).getCoveragePercent());
    }

    @Test
    public void roundTripsThroughJavaSerialization() throws Exception {
        HytaleAutoVegetationSettings original = new HytaleAutoVegetationSettings();
        original.setSeed(42L);
        original.setBiomeConfig(3, new HytaleAutoVegetationSettings.BiomeVegetationConfig(
                17, Collections.singletonList(
                        new HytaleAutoVegetationSettings.PlantEntry(
                                UUID.fromString("00000000-0000-0000-0000-000000000001"), 80))));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bytes)) {
            oos.writeObject(original);
        }
        HytaleAutoVegetationSettings readBack;
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            readBack = (HytaleAutoVegetationSettings) ois.readObject();
        }
        assertEquals(42L, readBack.getSeed());
        assertEquals(17, readBack.getByBiome().get(3).getCoveragePercent());
        assertEquals(80, readBack.getByBiome().get(3).getPlants().get(0).getOccurrenceWeight());
    }
}
