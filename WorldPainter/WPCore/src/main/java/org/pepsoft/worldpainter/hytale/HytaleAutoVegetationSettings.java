package org.pepsoft.worldpainter.hytale;

import org.pepsoft.worldpainter.layers.Layer;
import org.pepsoft.worldpainter.layers.exporters.ExporterSettings;

import java.io.Serializable;
import java.util.*;

public final class HytaleAutoVegetationSettings implements ExporterSettings {

    private final Map<Integer, BiomeVegetationConfig> byBiome = new HashMap<>();
    private long seed = new Random().nextLong();
    private boolean enabled = true;

    public Map<Integer, BiomeVegetationConfig> getByBiome() {
        return Collections.unmodifiableMap(byBiome);
    }

    public void setBiomeConfig(int biomeId, BiomeVegetationConfig cfg) {
        byBiome.put(biomeId, cfg);
    }

    public void clearBiomeConfig(int biomeId) {
        byBiome.remove(biomeId);
    }

    public long getSeed() {
        return seed;
    }

    public void setSeed(long seed) {
        this.seed = seed;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    // ExporterSettings

    @Override
    public boolean isApplyEverywhere() {
        return false;
    }

    @Override
    public Layer getLayer() {
        return HytaleAutoVegetationLayer.INSTANCE;
    }

    @Override
    public HytaleAutoVegetationSettings clone() {
        HytaleAutoVegetationSettings copy = new HytaleAutoVegetationSettings();
        copy.seed = this.seed;
        copy.enabled = this.enabled;
        for (Map.Entry<Integer, BiomeVegetationConfig> e : byBiome.entrySet()) {
            copy.byBiome.put(e.getKey(), e.getValue().copy());
        }
        return copy;
    }

    public static final class BiomeVegetationConfig implements Serializable {
        private final int coveragePercent;
        private final List<PlantEntry> plants;

        public BiomeVegetationConfig(int coveragePercent, List<PlantEntry> plants) {
            this.coveragePercent = Math.max(0, Math.min(100, coveragePercent));
            this.plants = new ArrayList<>(plants);
        }

        public int getCoveragePercent() {
            return coveragePercent;
        }

        public List<PlantEntry> getPlants() {
            return Collections.unmodifiableList(plants);
        }

        BiomeVegetationConfig copy() {
            List<PlantEntry> copies = new ArrayList<>(plants.size());
            for (PlantEntry p : plants) {
                copies.add(p.copy());
            }
            return new BiomeVegetationConfig(coveragePercent, copies);
        }

        private static final long serialVersionUID = 1L;
    }

    public static final class PlantEntry implements Serializable {
        private final UUID hytaleTerrainId;
        private final int occurrenceWeight;

        public PlantEntry(UUID hytaleTerrainId, int occurrenceWeight) {
            this.hytaleTerrainId = hytaleTerrainId;
            this.occurrenceWeight = Math.max(1, Math.min(100, occurrenceWeight));
        }

        public UUID getHytaleTerrainId() {
            return hytaleTerrainId;
        }

        public int getOccurrenceWeight() {
            return occurrenceWeight;
        }

        PlantEntry copy() {
            return new PlantEntry(hytaleTerrainId, occurrenceWeight);
        }

        private static final long serialVersionUID = 1L;
    }

    private static final long serialVersionUID = 1L;
}
