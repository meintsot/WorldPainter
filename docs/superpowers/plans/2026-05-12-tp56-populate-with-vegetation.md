# TP-56 — Populate with Vegetation (Hytale) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Hytale-only built-in `Auto Vegetation` layer that, at export, procedurally scatters plants based on the biome under each painted pixel, with curated defaults shipped out of the box and a per-biome Settings dialog.

**Architecture:** A `Layer` singleton (`HytaleAutoVegetationLayer`) for the bit-per-pixel paint, an `ExporterSettings` for the per-dimension biome → plant-mix table, a JSON resource for the curated defaults, a static algorithm helper for the per-pixel decision logic (so it's unit-testable in isolation), an integration site inside `HytaleWorldExporter.populateChunkFromTile`, and a Swing dialog for editing.

**Tech Stack:** Java 17, Maven multi-module, JUnit 4, GSON (already a dep), Swing/JIDE for UI.

**Companion spec:** [`docs/superpowers/specs/2026-05-12-tp56-populate-with-vegetation-design.md`](../specs/2026-05-12-tp56-populate-with-vegetation-design.md)

**Spec corrections incorporated here:**
- Renderer location is `hytale/renderers/HytaleAutoVegetationLayerRenderer.java` (auto-loaded by `Layer.init()` reflection at `Layer.java:248`), not `layers/renderers/`.
- The substrate-compatibility helper is **new code**, not an extraction from the painted-plant path (which has no substrate check today).

---

## Build / test commands (used throughout)

From the `WorldPainter/` directory (the Maven project root, **not** the repo root):

```bash
# Run a single test class
mvn -pl WPCore test -Dtest=HytaleAutoVegetationLayerTest

# Run all tests in WPCore
mvn -pl WPCore test

# Build (skip tests) — used to verify compilation
mvn -pl WPCore -DskipTests=true install
mvn -pl WPGUI -DskipTests=true install -am

# Run the app for manual UI checks
mvn -pl WPGUI exec:exec
```

`-pl <module>` = "this module only". `-am` = "also make dependencies".

---

## File structure

**New files (created by this plan):**

| File | Responsibility |
|---|---|
| `WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleAutoVegetationLayer.java` | Singleton bit-per-pixel `Layer`. |
| `WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleAutoVegetationSettings.java` | Per-dimension `ExporterSettings`. Contains the biome→plants map. |
| `WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleAutoVegetationDefaults.java` | Loads + applies the curated JSON. |
| `WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleAutoVegetationAlgorithm.java` | Pure per-pixel decision logic (no exporter dep), so it's unit-testable. |
| `WPCore/src/main/java/org/pepsoft/worldpainter/hytale/renderers/HytaleAutoVegetationLayerRenderer.java` | `BitLayerRenderer` subclass for the map view. |
| `WPCore/src/main/resources/hytale/auto-vegetation-defaults.json` | Curated biome → plant-mix data. |
| `WPGUI/src/main/java/org/pepsoft/worldpainter/hytale/HytaleAutoVegetationDialog.java` | Swing settings dialog. |
| `WPCore/src/test/java/org/pepsoft/worldpainter/hytale/HytaleAutoVegetationLayerTest.java` | Test. |
| `WPCore/src/test/java/org/pepsoft/worldpainter/hytale/HytaleAutoVegetationSettingsTest.java` | Test. |
| `WPCore/src/test/java/org/pepsoft/worldpainter/hytale/HytaleAutoVegetationDefaultsTest.java` | Test. |
| `WPCore/src/test/java/org/pepsoft/worldpainter/hytale/HytaleAutoVegetationAlgorithmTest.java` | Test. |
| `WPCore/src/test/java/org/pepsoft/worldpainter/hytale/Tp56AutoVegetationExportTest.java` | End-to-end exporter test. |

**Modified files:**

| File | Change |
|---|---|
| `WPCore/src/main/java/org/pepsoft/worldpainter/DefaultPlugin.java` | Append `HytaleAutoVegetationLayer.INSTANCE` to `getLayers()`. |
| `WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleWorldExporter.java` | Insert auto-veg per-pixel block after the painted plant overlay block (currently at lines 1632-1662 / `── Plant Overlay Layer ──`). |
| WPGUI dispatcher for layer right-click → Settings (file confirmed in Task 9 by grepping for `HytaleFluidLayer` dialog dispatch). | Route the new layer to `HytaleAutoVegetationDialog`. |

---

## Task 1: The Layer singleton

**Files:**
- Create: `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleAutoVegetationLayer.java`
- Test: `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/HytaleAutoVegetationLayerTest.java`

- [ ] **Step 1.1: Write the failing test**

Create `WPCore/src/test/java/org/pepsoft/worldpainter/hytale/HytaleAutoVegetationLayerTest.java`:

```java
package org.pepsoft.worldpainter.hytale;

import org.junit.Test;
import org.pepsoft.worldpainter.layers.Layer;

import java.io.*;

import static org.junit.Assert.*;

public class HytaleAutoVegetationLayerTest {

    @Test
    public void singletonInstanceIsBitPerPixelLayer() {
        Layer layer = HytaleAutoVegetationLayer.INSTANCE;
        assertNotNull(layer);
        assertEquals("HyAutoVeg", layer.getId());
        assertEquals("Auto Vegetation", layer.getName());
        assertEquals(Layer.DataSize.BIT, layer.getDataSize());
        assertEquals(0, layer.getDefaultValue());
    }

    @Test
    public void serializationRoundTripReturnsSameInstance() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bytes)) {
            oos.writeObject(HytaleAutoVegetationLayer.INSTANCE);
        }
        Object readBack;
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            readBack = ois.readObject();
        }
        assertSame("readResolve must return the singleton", HytaleAutoVegetationLayer.INSTANCE, readBack);
    }
}
```

- [ ] **Step 1.2: Run the test to verify it fails**

```bash
cd WorldPainter
mvn -pl WPCore test -Dtest=HytaleAutoVegetationLayerTest
```

Expected: compilation failure — `HytaleAutoVegetationLayer` does not exist.

- [ ] **Step 1.3: Write the minimal implementation**

Create `WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleAutoVegetationLayer.java`:

```java
package org.pepsoft.worldpainter.hytale;

import org.pepsoft.worldpainter.layers.Layer;

import java.io.ObjectStreamException;

/**
 * Built-in Hytale layer that procedurally scatters biome-appropriate plants
 * across painted pixels at export time. Bit-per-pixel: painted on/off. The
 * "which plants" decision lives in {@link HytaleAutoVegetationSettings},
 * attached to the dimension.
 */
public final class HytaleAutoVegetationLayer extends Layer {

    public static final HytaleAutoVegetationLayer INSTANCE = new HytaleAutoVegetationLayer();

    private HytaleAutoVegetationLayer() {
        super("HyAutoVeg", "Auto Vegetation",
                "Procedurally scatter plants based on the biome under each painted pixel (Hytale)",
                DataSize.BIT, false, 0);
    }

    private Object readResolve() throws ObjectStreamException {
        return INSTANCE;
    }

    private static final long serialVersionUID = 1L;
}
```

- [ ] **Step 1.4: Run the test to verify it passes**

```bash
mvn -pl WPCore test -Dtest=HytaleAutoVegetationLayerTest
```

Expected: `Tests run: 2, Failures: 0, Errors: 0`.

- [ ] **Step 1.5: Commit**

```bash
git add WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleAutoVegetationLayer.java \
        WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/HytaleAutoVegetationLayerTest.java
git commit -m "$(cat <<'EOF'
feat(hytale): add HytaleAutoVegetationLayer singleton (TP-56)

Bit-per-pixel Layer subclass used to mark regions where auto vegetation
should run. Configuration lives in HytaleAutoVegetationSettings (next
task); this is just the paint surface.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: The settings data model

**Files:**
- Create: `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleAutoVegetationSettings.java`
- Test: `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/HytaleAutoVegetationSettingsTest.java`

- [ ] **Step 2.1: Write the failing test**

Create `WPCore/src/test/java/org/pepsoft/worldpainter/hytale/HytaleAutoVegetationSettingsTest.java`:

```java
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
```

- [ ] **Step 2.2: Run the test to verify it fails**

```bash
mvn -pl WPCore test -Dtest=HytaleAutoVegetationSettingsTest
```

Expected: compilation failure — `HytaleAutoVegetationSettings` does not exist.

- [ ] **Step 2.3: Write the minimal implementation**

Create `WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleAutoVegetationSettings.java`:

```java
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

    public long getSeed() { return seed; }
    public void setSeed(long seed) { this.seed = seed; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    // ExporterSettings

    @Override
    public boolean isApplyEverywhere() { return false; }

    @Override
    public Layer getLayer() { return HytaleAutoVegetationLayer.INSTANCE; }

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

        public int getCoveragePercent() { return coveragePercent; }
        public List<PlantEntry> getPlants() { return Collections.unmodifiableList(plants); }

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

        public UUID getHytaleTerrainId() { return hytaleTerrainId; }
        public int getOccurrenceWeight() { return occurrenceWeight; }

        PlantEntry copy() { return new PlantEntry(hytaleTerrainId, occurrenceWeight); }

        private static final long serialVersionUID = 1L;
    }

    private static final long serialVersionUID = 1L;
}
```

- [ ] **Step 2.4: Run the test to verify it passes**

```bash
mvn -pl WPCore test -Dtest=HytaleAutoVegetationSettingsTest
```

Expected: `Tests run: 6, Failures: 0, Errors: 0`.

- [ ] **Step 2.5: Commit**

```bash
git add WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleAutoVegetationSettings.java \
        WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/HytaleAutoVegetationSettingsTest.java
git commit -m "$(cat <<'EOF'
feat(hytale): add HytaleAutoVegetationSettings (TP-56)

Per-dimension ExporterSettings holding the biome -> plant-mix table,
global RNG seed, and enabled flag. BiomeVegetationConfig + PlantEntry
nested types are immutable value objects with weight clamping and
defensive copies in clone().

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Curated defaults JSON

**Files:**
- Create: `WorldPainter/WPCore/src/main/resources/hytale/auto-vegetation-defaults.json`

No test in this task — the loader test (Task 4) parses this file as a smoke test.

- [ ] **Step 3.1: Create the resource directory and the JSON file**

Create `WPCore/src/main/resources/hytale/auto-vegetation-defaults.json` with a **first-pass curation** (a real curation pass on all 43 biomes is a follow-up):

```json
{
  "Zone1_Drifting_Plains": {
    "coverage": 12,
    "plants": [
      {"terrain": "Plant_Grass_Tall",    "weight": 60},
      {"terrain": "Plant_Flower_White",  "weight": 20},
      {"terrain": "Plant_Flower_Yellow", "weight": 20}
    ]
  },
  "Zone1_Seedling_Woods": {
    "coverage": 22,
    "plants": [
      {"terrain": "Sapling_Oak",       "weight": 50},
      {"terrain": "Plant_Grass_Tall",  "weight": 30},
      {"terrain": "Plant_Bush",        "weight": 20}
    ]
  },
  "Zone1_Swamps": {
    "coverage": 18,
    "plants": [
      {"terrain": "Plant_Grass_Tall", "weight": 60},
      {"terrain": "Plant_Bush",       "weight": 40}
    ]
  },
  "Zone2_Deserts": {
    "coverage": 1,
    "plants": [
      {"terrain": "Plant_Cactus",    "weight": 80},
      {"terrain": "Plant_Dead_Bush", "weight": 20}
    ]
  },
  "Zone2_Savannas": {
    "coverage": 8,
    "plants": [
      {"terrain": "Plant_Grass_Tall", "weight": 100}
    ]
  },
  "Zone3_Frostmarch_Tundra": {
    "coverage": 4,
    "plants": [
      {"terrain": "Plant_Grass_Frosted", "weight": 70},
      {"terrain": "Sapling_Pine",        "weight": 30}
    ]
  },
  "Zone3_Boreal_Forest": {
    "coverage": 25,
    "plants": [
      {"terrain": "Sapling_Pine",        "weight": 70},
      {"terrain": "Plant_Grass_Frosted", "weight": 30}
    ]
  },
  "Zone4_Charred_Woodlands": {
    "coverage": 6,
    "plants": [
      {"terrain": "Plant_Dead_Bush", "weight": 100}
    ]
  }
}
```

**Note:** Plant names are placeholders against the `HytaleTerrain` registry. Before merging, verify each plant name resolves by running:

```bash
mvn -pl WPCore test -Dtest=HytaleAutoVegetationDefaultsTest#allShippedPlantNamesResolveInRegistry
```

(That test is written in Task 4. If any name fails to resolve, edit the JSON to use a valid name from the registry.)

- [ ] **Step 3.2: Commit**

```bash
git add WorldPainter/WPCore/src/main/resources/hytale/auto-vegetation-defaults.json
git commit -m "$(cat <<'EOF'
feat(hytale): add curated auto-vegetation defaults JSON (TP-56)

First-pass biome -> plant-mix table covering ~8 representative biomes
across the 4 zones. Remaining biomes default to empty (coverage 0) and
can be filled in by the user via the settings dialog or by extending
this file. A full 43-biome curation pass is a follow-up.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Defaults loader

**Files:**
- Create: `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleAutoVegetationDefaults.java`
- Test: `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/HytaleAutoVegetationDefaultsTest.java`

- [ ] **Step 4.1: Write the failing test**

Create `WPCore/src/test/java/org/pepsoft/worldpainter/hytale/HytaleAutoVegetationDefaultsTest.java`:

```java
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
                "\"plants\": [ {\"terrain\": \"Plant_Grass_Tall\", \"weight\": 60} ] } }";

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
                "{\"terrain\": \"Plant_Grass_Tall\",   \"weight\": 50} " +
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
```

- [ ] **Step 4.2: Run the test to verify it fails**

```bash
mvn -pl WPCore test -Dtest=HytaleAutoVegetationDefaultsTest
```

Expected: compilation failure — `HytaleAutoVegetationDefaults` does not exist.

- [ ] **Step 4.3: Write the minimal implementation**

Create `WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleAutoVegetationDefaults.java`:

```java
package org.pepsoft.worldpainter.hytale;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Loads and applies the curated biome -> plant-mix defaults shipped at
 * {@code hytale/auto-vegetation-defaults.json}. Unknown biomes or plants
 * are logged and skipped — the file is data, not code, so registry drift
 * never crashes the app.
 */
public final class HytaleAutoVegetationDefaults {

    private static final Logger logger = LoggerFactory.getLogger(HytaleAutoVegetationDefaults.class);
    private static final String RESOURCE_PATH = "/hytale/auto-vegetation-defaults.json";

    private HytaleAutoVegetationDefaults() {}

    public static Map<Integer, HytaleAutoVegetationSettings.BiomeVegetationConfig> parse(Reader json) {
        JsonElement root = JsonParser.parseReader(json);
        if (!root.isJsonObject()) {
            logger.warn("auto-vegetation defaults: root is not a JSON object; ignored");
            return Collections.emptyMap();
        }
        Map<Integer, HytaleAutoVegetationSettings.BiomeVegetationConfig> out = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject().entrySet()) {
            String biomeName = entry.getKey();
            HytaleBiome biome = HytaleBiome.getByName(biomeName);
            if (biome == null) {
                logger.warn("auto-vegetation defaults: unknown biome '{}' — skipped", biomeName);
                continue;
            }
            if (!entry.getValue().isJsonObject()) {
                logger.warn("auto-vegetation defaults: biome '{}' value is not an object — skipped", biomeName);
                continue;
            }
            JsonObject cfgJson = entry.getValue().getAsJsonObject();
            int coverage = cfgJson.has("coverage") ? cfgJson.get("coverage").getAsInt() : 0;
            List<HytaleAutoVegetationSettings.PlantEntry> plants = new ArrayList<>();
            if (cfgJson.has("plants") && cfgJson.get("plants").isJsonArray()) {
                for (JsonElement plantEl : cfgJson.getAsJsonArray("plants")) {
                    if (!plantEl.isJsonObject()) {
                        continue;
                    }
                    JsonObject p = plantEl.getAsJsonObject();
                    String terrainName = p.has("terrain") ? p.get("terrain").getAsString() : null;
                    int weight = p.has("weight") ? p.get("weight").getAsInt() : 1;
                    if (terrainName == null) {
                        continue;
                    }
                    HytaleTerrain terrain = HytaleTerrain.getByName(terrainName);
                    if (terrain == null) {
                        logger.warn("auto-vegetation defaults: biome '{}' references unknown terrain '{}' — dropped",
                                biomeName, terrainName);
                        continue;
                    }
                    plants.add(new HytaleAutoVegetationSettings.PlantEntry(terrain.getId(), weight));
                }
            }
            out.put(biome.getId(),
                    new HytaleAutoVegetationSettings.BiomeVegetationConfig(coverage, plants));
        }
        return out;
    }

    public static void applyTo(HytaleAutoVegetationSettings settings, Reader json) {
        Map<Integer, HytaleAutoVegetationSettings.BiomeVegetationConfig> parsed = parse(json);
        for (Map.Entry<Integer, HytaleAutoVegetationSettings.BiomeVegetationConfig> e : parsed.entrySet()) {
            settings.setBiomeConfig(e.getKey(), e.getValue());
        }
    }

    public static void applyShippedDefaultsTo(HytaleAutoVegetationSettings settings) {
        try (InputStream in = HytaleAutoVegetationDefaults.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                logger.warn("auto-vegetation defaults: shipped resource {} not found", RESOURCE_PATH);
                return;
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                applyTo(settings, reader);
            }
        } catch (IOException e) {
            logger.warn("auto-vegetation defaults: failed to load shipped resource", e);
        }
    }

    /**
     * Returns the list of plant terrain names referenced by the shipped JSON
     * that do not currently resolve via {@link HytaleTerrain#getByName(String)}.
     * Used by the registry-drift guard test.
     */
    public static List<String> findUnresolvedShippedPlantNames() {
        List<String> unresolved = new ArrayList<>();
        try (InputStream in = HytaleAutoVegetationDefaults.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                return unresolved;
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonElement root = JsonParser.parseReader(reader);
                if (!root.isJsonObject()) return unresolved;
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject().entrySet()) {
                    if (!e.getValue().isJsonObject()) continue;
                    JsonObject cfg = e.getValue().getAsJsonObject();
                    if (!cfg.has("plants") || !cfg.get("plants").isJsonArray()) continue;
                    for (JsonElement plantEl : cfg.getAsJsonArray("plants")) {
                        if (!plantEl.isJsonObject()) continue;
                        String name = plantEl.getAsJsonObject().has("terrain")
                                ? plantEl.getAsJsonObject().get("terrain").getAsString() : null;
                        if (name != null && HytaleTerrain.getByName(name) == null) {
                            unresolved.add(name);
                        }
                    }
                }
            }
        } catch (IOException e) {
            logger.warn("auto-vegetation defaults: unresolved-names scan failed", e);
        }
        return unresolved;
    }
}
```

**Important: `HytaleTerrain.getByName(String)` is assumed to exist.** Confirm before running tests:

```bash
grep -n "public static HytaleTerrain getByName" WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleTerrain.java
```

If it does not exist, add this method to `HytaleTerrain.java` next to `getById`:

```java
public static HytaleTerrain getByName(String name) {
    if (name == null) return null;
    for (HytaleTerrain t : getAllTerrains()) {        // method name may differ — match existing iteration API
        if (name.equals(t.getName())) return t;
    }
    return null;
}
```

(`HytaleTerrain.getAllTerrains()` or the equivalent iteration accessor must already exist — find it with `grep -n "public static.*getAll" HytaleTerrain.java`.)

- [ ] **Step 4.4: Run the test to verify it passes**

```bash
mvn -pl WPCore test -Dtest=HytaleAutoVegetationDefaultsTest
```

Expected: `Tests run: 7, Failures: 0, Errors: 0`. If `allShippedPlantNamesResolveInRegistry` fails, the JSON has names that don't exist in the registry — edit the JSON in Task 3 to use real names.

- [ ] **Step 4.5: Commit**

```bash
git add WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleAutoVegetationDefaults.java \
        WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/HytaleAutoVegetationDefaultsTest.java
# only if you added the getByName method:
git add WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleTerrain.java
git commit -m "$(cat <<'EOF'
feat(hytale): add auto-vegetation defaults loader (TP-56)

Parses the shipped biome -> plant-mix JSON. Unknown biomes and plants
are warned-and-skipped, so registry drift never crashes the loader. A
findUnresolvedShippedPlantNames() helper backs the CI guard test that
catches stale plant names in the shipped JSON.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Substrate-validity helper + per-pixel algorithm

We combine these because the helper is private to the algorithm class and tested through it.

**Files:**
- Create: `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleAutoVegetationAlgorithm.java`
- Test: `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/HytaleAutoVegetationAlgorithmTest.java`

The algorithm class exists so that the per-pixel decision is unit-testable in isolation, without standing up the full `HytaleWorldExporter`.

- [ ] **Step 5.1: Write the failing test**

Create `WPCore/src/test/java/org/pepsoft/worldpainter/hytale/HytaleAutoVegetationAlgorithmTest.java`:

```java
package org.pepsoft.worldpainter.hytale;

import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class HytaleAutoVegetationAlgorithmTest {

    /** Stub substrate that just reports an id; used for isValidSubstrateFor tests. */
    private static HytaleBlock blockOf(String id) {
        return new HytaleBlock(id);   // confirm constructor signature; see note below
    }

    @Test
    public void substrateAllowsSolidGround() {
        assertTrue(HytaleAutoVegetationAlgorithm.isValidSubstrateFor(
                blockOf("Plant_Grass_Tall"), blockOf("Block_Grass")));
        assertTrue(HytaleAutoVegetationAlgorithm.isValidSubstrateFor(
                blockOf("Plant_Grass_Tall"), blockOf("Block_Dirt")));
        assertTrue(HytaleAutoVegetationAlgorithm.isValidSubstrateFor(
                blockOf("Plant_Cactus"),     blockOf("Block_Sand")));
    }

    @Test
    public void substrateRejectsLavaAndWater() {
        assertFalse(HytaleAutoVegetationAlgorithm.isValidSubstrateFor(
                blockOf("Plant_Grass_Tall"), blockOf("Liquid_Water_Source")));
        assertFalse(HytaleAutoVegetationAlgorithm.isValidSubstrateFor(
                blockOf("Plant_Grass_Tall"), blockOf("Liquid_Lava_Source")));
    }

    @Test
    public void substrateRejectsMagmaIceSnow() {
        assertFalse(HytaleAutoVegetationAlgorithm.isValidSubstrateFor(
                blockOf("Plant_Grass_Tall"), blockOf("Block_Magma")));
        assertFalse(HytaleAutoVegetationAlgorithm.isValidSubstrateFor(
                blockOf("Plant_Grass_Tall"), blockOf("Block_Ice")));
        assertFalse(HytaleAutoVegetationAlgorithm.isValidSubstrateFor(
                blockOf("Plant_Grass_Tall"), blockOf("Block_Snow")));
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
```

**Confirm `HytaleBlock` constructor before running.** Look at existing code: at `HytalePlantsLayer.java:83` we see `terrain.getPrimaryBlock().id` — so `HytaleBlock.id` is a public field. Check the constructor:

```bash
grep -n "public HytaleBlock" WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleBlock.java
```

If the test stub `new HytaleBlock(id)` doesn't compile, replace the helper with whatever factory the class exposes (e.g. `HytaleBlock.of(id)` or `new HytaleBlock(id, ...)` with the right extra args). The substrate-validity logic only inspects `.id`, so any constructor that sets `id` will work.

- [ ] **Step 5.2: Run the test to verify it fails**

```bash
mvn -pl WPCore test -Dtest=HytaleAutoVegetationAlgorithmTest
```

Expected: compilation failure — `HytaleAutoVegetationAlgorithm` does not exist.

- [ ] **Step 5.3: Write the minimal implementation**

Create `WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleAutoVegetationAlgorithm.java`:

```java
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
        if (substrate == null || substrate.id == null) return false;
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
```

- [ ] **Step 5.4: Run the test to verify it passes**

```bash
mvn -pl WPCore test -Dtest=HytaleAutoVegetationAlgorithmTest
```

Expected: all 10 tests pass.

- [ ] **Step 5.5: Commit**

```bash
git add WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleAutoVegetationAlgorithm.java \
        WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/HytaleAutoVegetationAlgorithmTest.java
git commit -m "$(cat <<'EOF'
feat(hytale): add auto-vegetation per-pixel algorithm helper (TP-56)

Pure decision logic separated from the exporter so it can be unit-tested
in isolation: substrate-validity rejection list, coverage gate, weighted
plant pick, and a deterministic per-pixel seed mix (splitmix64
finalizer) using global block coordinates so plants don't reshuffle or
seam at tile boundaries.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: Register the layer in `DefaultPlugin`

**Files:**
- Modify: `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/DefaultPlugin.java:38-43`

Registering before integration so we can paint the layer in a manual smoke test if needed.

- [ ] **Step 6.1: Edit `DefaultPlugin.getLayers()` to include the new layer**

Locate the `getLayers()` method (around line 37-44). It currently returns:

```java
return Arrays.asList(Frost.INSTANCE, Caves.INSTANCE, Caverns.INSTANCE, Chasms.INSTANCE, DeciduousForest.INSTANCE, PineForest.INSTANCE, SwampLand.INSTANCE, Jungle.INSTANCE, org.pepsoft.worldpainter.layers.Void.INSTANCE, Resources.INSTANCE/*, River.INSTANCE*/,
    // Hytale-specific layers
    org.pepsoft.worldpainter.hytale.HytaleEntityLayer.INSTANCE,
    org.pepsoft.worldpainter.hytale.HytalePrefabLayer.INSTANCE,
    org.pepsoft.worldpainter.hytale.HytaleFluidLayer.INSTANCE,
    org.pepsoft.worldpainter.hytale.HytaleEnvironmentLayer.INSTANCE);
```

Add the auto-veg layer at the end of the Hytale block:

```java
return Arrays.asList(Frost.INSTANCE, Caves.INSTANCE, Caverns.INSTANCE, Chasms.INSTANCE, DeciduousForest.INSTANCE, PineForest.INSTANCE, SwampLand.INSTANCE, Jungle.INSTANCE, org.pepsoft.worldpainter.layers.Void.INSTANCE, Resources.INSTANCE/*, River.INSTANCE*/,
    // Hytale-specific layers
    org.pepsoft.worldpainter.hytale.HytaleEntityLayer.INSTANCE,
    org.pepsoft.worldpainter.hytale.HytalePrefabLayer.INSTANCE,
    org.pepsoft.worldpainter.hytale.HytaleFluidLayer.INSTANCE,
    org.pepsoft.worldpainter.hytale.HytaleEnvironmentLayer.INSTANCE,
    org.pepsoft.worldpainter.hytale.HytaleAutoVegetationLayer.INSTANCE);
```

- [ ] **Step 6.2: Verify the build compiles**

```bash
mvn -pl WPCore -DskipTests=true install
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 6.3: Commit**

```bash
git add WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/DefaultPlugin.java
git commit -m "$(cat <<'EOF'
feat(hytale): register HytaleAutoVegetationLayer in DefaultPlugin (TP-56)

Adds the new layer to the Hytale section of the layer provider list so
it shows up in the Layers tab.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: Layer renderer

**Files:**
- Create: `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/renderers/HytaleAutoVegetationLayerRenderer.java`

`Layer.init()` (at `Layer.java:248`) auto-loads `<package>.renderers.<ClassName>Renderer`. The package is `org.pepsoft.worldpainter.hytale`, the class is `HytaleAutoVegetationLayer`, so the renderer must be at `hytale/renderers/HytaleAutoVegetationLayerRenderer.java`.

No test in this task — visual correctness is checked in the manual smoke test.

- [ ] **Step 7.1: Look at how `BitLayerRenderer` is used**

```bash
grep -rn "extends BitLayerRenderer" WorldPainter/WPCore/src/main/java
```

Find an existing subclass (e.g. `FrostRenderer.java`) and use it as a template.

- [ ] **Step 7.2: Create the renderer**

Create `WPCore/src/main/java/org/pepsoft/worldpainter/hytale/renderers/HytaleAutoVegetationLayerRenderer.java`:

```java
package org.pepsoft.worldpainter.hytale.renderers;

import org.pepsoft.worldpainter.layers.renderers.BitLayerRenderer;

/**
 * Renders {@link org.pepsoft.worldpainter.hytale.HytaleAutoVegetationLayer}
 * in the map view as a translucent leafy-green overlay where the bit is set.
 */
public final class HytaleAutoVegetationLayerRenderer extends BitLayerRenderer {

    public HytaleAutoVegetationLayerRenderer() {
        // ARGB: ~40% alpha, green leaning towards yellow-green for "vegetation"
        super(0x66, 0x66, 0x4FAE3C);
    }
}
```

**Confirm the `BitLayerRenderer` constructor signature.** Open `WPCore/src/main/java/org/pepsoft/worldpainter/layers/renderers/BitLayerRenderer.java`, find its constructors, and adjust the arguments. Existing subclasses (e.g. `FrostRenderer`, `CavesRenderer`) are good templates.

- [ ] **Step 7.3: Verify the build**

```bash
mvn -pl WPCore -DskipTests=true install
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 7.4: Commit**

```bash
git add WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/renderers/HytaleAutoVegetationLayerRenderer.java
git commit -m "$(cat <<'EOF'
feat(hytale): add map renderer for HytaleAutoVegetationLayer (TP-56)

Translucent leafy-green overlay subclass of BitLayerRenderer. Discovered
automatically by Layer.init() reflection via the package convention.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: Hook the algorithm into `HytaleWorldExporter`

**Files:**
- Modify: `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleWorldExporter.java` (insert after the `── Plant Overlay Layer ──` block at lines 1632-1662)
- Test: `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/Tp56AutoVegetationExportTest.java`

This is the largest task. Write the integration test first, see it fail, then add the integration block.

- [ ] **Step 8.1: Look at the existing per-pixel block to anchor the insertion**

```bash
sed -n '1630,1665p' WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleWorldExporter.java
```

You should see the `// ── Plant Overlay Layer ──` block ending with the `setDecorative(...)` call inside `if (plantsPhysicsExempt)`. Your new block goes **immediately after the closing brace** of that `if (plantIndex > 0)` block.

- [ ] **Step 8.2: Write the integration test (initially expected to fail)**

Look at `Tp53UnderwaterPlantTest.java` as the template for end-to-end exporter tests:

```bash
find WorldPainter/WPCore/src/test -name "Tp53UnderwaterPlantTest.java"
```

Open it and copy its scaffolding into `WPCore/src/test/java/org/pepsoft/worldpainter/hytale/Tp56AutoVegetationExportTest.java`. Adapt:

- Build a tiny Hytale world.
- Paint a single biome (e.g. `DRIFTING_PLAINS`) on a small region.
- Set the Auto Vegetation layer bit on that region: `tile.setBitLayerValue(HytaleAutoVegetationLayer.INSTANCE, x, z, true)`.
- Attach `HytaleAutoVegetationSettings` to the dimension with a single biome config: coverage 100, one plant with weight 100.
- Run the export.
- Read back the chunks. Assert: at every cell of the painted region, `chunk.getHytaleBlock(localX, height+1, localZ)` equals the configured plant block, and `chunk.isSealProtected(localX, height+1, localZ)` is true.
- A second test: re-export the same world (don't touch anything), read back, assert byte-identical block placement (determinism).
- A third test: lazy defaults. Don't set any settings on the dimension; paint the layer; export. After export, `dimension.getLayerSettings(HytaleAutoVegetationLayer.INSTANCE)` is non-null and matches the curated JSON.

Use `Tp53UnderwaterPlantTest`'s exact patterns for world construction and BSON readback — do not invent new patterns.

- [ ] **Step 8.3: Run the test to verify it fails**

```bash
mvn -pl WPCore test -Dtest=Tp56AutoVegetationExportTest
```

Expected: compilation passes (we have all the types), but the test fails because the exporter ignores the layer. The block at `height+1` will be empty/air.

- [ ] **Step 8.4: Modify the exporter — add the auto-veg block**

Open `HytaleWorldExporter.java`. Inside `populateChunkFromTile`, immediately after the `── Plant Overlay Layer ──` block (the existing `if (plantIndex > 0) { ... }` block ending around line 1662), insert:

```java
                // ── Auto Vegetation Layer ─────────────────────────────
                // Biome-driven procedural plant placement. Yields to any
                // user-painted plant at this pixel. Curated defaults are
                // lazily seeded the first time the layer is exported on a
                // dimension that has no settings yet.
                if (tile.getBitLayerValue(HytaleAutoVegetationLayer.INSTANCE, tileLocalX, tileLocalZ)
                        && (plantIndex == 0)) {
                    HytaleAutoVegetationSettings autoVegSettings = (HytaleAutoVegetationSettings)
                            dimension.getLayerSettings(HytaleAutoVegetationLayer.INSTANCE);
                    if (autoVegSettings == null) {
                        autoVegSettings = new HytaleAutoVegetationSettings();
                        HytaleAutoVegetationDefaults.applyShippedDefaultsTo(autoVegSettings);
                        dimension.setLayerSettings(HytaleAutoVegetationLayer.INSTANCE, autoVegSettings);
                    }
                    if (autoVegSettings.isEnabled()) {
                        int biomeId = tile.getLayerValue(org.pepsoft.worldpainter.layers.Biome.INSTANCE,
                                tileLocalX, tileLocalZ);
                        if (biomeId == HytaleBiome.BIOME_AUTO) {
                            // Reuse the exporter's existing terrain-derived biome
                            // mapping for auto-biome cells.
                            biomeId = HytaleBiome.fromTerrainBiomeName(
                                    hytaleTerrain != null ? hytaleTerrain.getName() : null).getId();
                        }
                        HytaleAutoVegetationSettings.BiomeVegetationConfig cfg =
                                autoVegSettings.getByBiome().get(biomeId);
                        if (cfg != null) {
                            long pixelSeed = HytaleAutoVegetationAlgorithm.seedFor(
                                    autoVegSettings.getSeed(),
                                    tile.getX(), tile.getY(),
                                    tileLocalX, tileLocalZ);
                            Random rng = new Random(pixelSeed);
                            java.util.UUID pickedTerrainId = HytaleAutoVegetationAlgorithm.pick(cfg, rng);
                            if (pickedTerrainId != null) {
                                HytaleTerrain pickedTerrain = HytaleTerrain.getById(pickedTerrainId);
                                if (pickedTerrain != null) {
                                    HytaleBlock plantBlock = pickedTerrain.getBlock(seed, worldX, worldZ, 0);
                                    HytaleBlock substrate = chunk.getHytaleBlock(localX, height, localZ).orElse(null);
                                    if (plantBlock != null && !plantBlock.isEmpty() && !plantBlock.isFluid()
                                            && ((height + 1) < dimension.getMaxHeight())
                                            && HytaleAutoVegetationAlgorithm.isValidSubstrateFor(plantBlock, substrate)) {
                                        chunk.setHytaleBlock(localX, height + 1, localZ, plantBlock);
                                        chunk.setSealProtected(localX, height + 1, localZ, true);
                                    }
                                }
                            }
                        }
                    }
                }
```

**Confirm these API names against the existing painted-plant block** (lines 1646-1662 of `HytaleWorldExporter.java`):
- `tile.getX()`, `tile.getY()` for tile coordinates (Z is called `Y` in WorldPainter's 2D-on-a-plane convention — verify by checking how the painted-plant block uses `worldX`, `worldZ`).
- `chunk.getHytaleBlock(localX, y, localZ)` — verify this returns an `Optional<HytaleBlock>` or a direct `HytaleBlock`. Adapt the `.orElse(null)` line accordingly.
- `hytaleTerrain` variable — that's the surface terrain already in scope from the earlier `─ Terrain ─` block in this loop. Verify by grepping nearby code.
- `seed`, `worldX`, `worldZ`, `localX`, `localZ`, `tileLocalX`, `tileLocalZ`, `height` — all already in scope from the surrounding loop. Verify by reading the painted-plant block above.

If any name differs, adjust — the existing painted-plant block at lines 1646-1662 is the authoritative naming reference; mirror it.

Add the imports at the top of `HytaleWorldExporter.java`:

```java
import java.util.Random;
import java.util.UUID;
```

(`UUID` is already used in `HytaleTerrain` — there's likely already an import. Check before adding to avoid duplicates.)

- [ ] **Step 8.5: Run the integration test — verify it now passes**

```bash
mvn -pl WPCore test -Dtest=Tp56AutoVegetationExportTest
```

Expected: all three tests pass.

- [ ] **Step 8.6: Run the regression smokes — verify nothing else broke**

```bash
mvn -pl WPCore test -Dtest='Tp53UnderwaterPlantTest,Tp60PlantSubstrateTest,Tp60PlantsPhysicsExemptToggleTest'
```

Expected: all pass.

- [ ] **Step 8.7: Commit**

```bash
git add WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleWorldExporter.java \
        WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/Tp56AutoVegetationExportTest.java
git commit -m "$(cat <<'EOF'
feat(hytale): integrate auto-vegetation into export pipeline (TP-56)

Inserts a per-pixel auto-veg block in populateChunkFromTile immediately
after the painted plant overlay. Yields to user-painted plants, routes
Auto-biome (255) through fromTerrainBiomeName, lazy-seeds curated
defaults on first export, and seal-protects every placed plant so it
survives the post-export water-sealing pass (TP-53 pattern).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: The Settings dialog

**Files:**
- Create: `WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/hytale/HytaleAutoVegetationDialog.java`

UI code that doesn't have meaningful unit tests — verified manually in Task 11. Keep it focused: the goal is the layout from the spec's UI mockup, no flourishes.

- [ ] **Step 9.1: Look at an existing Hytale settings dialog for a template**

```bash
ls WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/hytale/
```

`CreatePrefabLayerDialog.java` is a good template — same module, same package, same Swing style. Open it and copy the import block + the dialog skeleton (constructor with `Window owner`, `JDialog`, `setLayout`, `pack`, `setLocationRelativeTo`).

- [ ] **Step 9.2: Create the dialog**

Create `WPGUI/src/main/java/org/pepsoft/worldpainter/hytale/HytaleAutoVegetationDialog.java`. Required components (matching the UI mockup):

- Modal `JDialog` with title "Auto Vegetation Settings".
- Top bar: `JCheckBox` "Enabled" (bound to `settings.isEnabled`), `JTextField` "Seed" (bound to `settings.getSeed`), `JButton` "Reset all to defaults".
- Main: a `JScrollPane` wrapping a `JPanel` with a row per biome from `HytaleBiome.getAllBiomes()` in the order returned by `HytaleBiome.getBiomeOrder()` (skipping the `-1` spacers but adding a thin separator between zones).
- Each row: biome color swatch (small `JPanel` with `setBackground(new Color(biome.getDisplayColor()))`), biome display name (`JLabel`), `JSlider(0, 100)` for coverage, a `FlowLayout` panel of plant chips. Each chip: a small panel with `terrain.getName() · weight` + an `✕` button. A `+ add plant` button at the end opens a `JPopupMenu` or a small `JDialog` listing surface-only terrains (`HytaleBlockRegistry.isSurfaceOnlyBlock(terrain.getPrimaryBlock().id)` filter).
- Bottom: `JButton` "Cancel", `JButton` "OK". OK writes back to the settings object the dialog was constructed with.

Public API:

```java
public final class HytaleAutoVegetationDialog extends JDialog {

    private final HytaleAutoVegetationSettings settings;
    private boolean accepted;

    public HytaleAutoVegetationDialog(Window owner, HytaleAutoVegetationSettings settings) {
        super(owner, "Auto Vegetation Settings", ModalityType.APPLICATION_MODAL);
        this.settings = settings;
        buildUi();
        pack();
        setLocationRelativeTo(owner);
    }

    public boolean isAccepted() {
        return accepted;
    }

    private void buildUi() {
        // ... assemble the components per the layout above.
    }
}
```

Keep the implementation straightforward. Use `BoxLayout`, `BorderLayout`, and `GridBagLayout` — do **not** pull in third-party form builders.

- [ ] **Step 9.3: Verify the build**

```bash
mvn -pl WPGUI -DskipTests=true install -am
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 9.4: Commit**

```bash
git add WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/hytale/HytaleAutoVegetationDialog.java
git commit -m "$(cat <<'EOF'
feat(hytale): add Settings dialog for auto-vegetation layer (TP-56)

Modal Swing dialog with a per-biome table: coverage slider, plant chips
with occurrence weights, and an "add plant" picker filtered to
surface-only Hytale terrains. Reset-to-defaults reloads the shipped JSON.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 10: Wire right-click → Settings dispatch

**Files:**
- Modify: the existing WPGUI dispatcher used by `HytaleFluidLayer` / `HytalePrefabLayer` settings dialogs.

The exact file isn't documented in the spec — locate it.

- [ ] **Step 10.1: Find the dispatcher**

```bash
grep -rn "HytaleFluidLayer\|HytalePrefabLayer\|HytaleEnvironmentLayer" WorldPainter/WPGUI/src/main/java | grep -i "settings\|dialog\|edit" | head -20
```

Look for a switch / chain of `instanceof` checks that maps a `Layer` to its settings dialog. That's the dispatcher.

- [ ] **Step 10.2: Add a case for the new layer**

Following the existing pattern — if it's a `switch` on layer ID, add a `case "HyAutoVeg":` that opens `HytaleAutoVegetationDialog`. If it's an `instanceof` chain, add `else if (layer instanceof HytaleAutoVegetationLayer) { … }`. In both shapes:

```java
HytaleAutoVegetationSettings settings = (HytaleAutoVegetationSettings)
        dimension.getLayerSettings(HytaleAutoVegetationLayer.INSTANCE);
if (settings == null) {
    settings = new HytaleAutoVegetationSettings();
    HytaleAutoVegetationDefaults.applyShippedDefaultsTo(settings);
    dimension.setLayerSettings(HytaleAutoVegetationLayer.INSTANCE, settings);
}
HytaleAutoVegetationDialog dialog = new HytaleAutoVegetationDialog(parentWindow, settings);
dialog.setVisible(true);
// dialog mutates settings in place on OK
```

Mirror the surrounding code style — variable names, parent-window resolution, repaint-after-edit calls — exactly as the existing Hytale-layer dispatch does.

- [ ] **Step 10.3: Verify the build**

```bash
mvn -pl WPGUI -DskipTests=true install -am
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 10.4: Commit**

```bash
# the file path depends on what Step 10.1 found:
git add <the dispatcher file>
git commit -m "$(cat <<'EOF'
feat(hytale): route auto-veg layer Settings menu to its dialog (TP-56)

Hooks HytaleAutoVegetationLayer into the existing Hytale layer-settings
dispatcher so right-click -> Settings opens HytaleAutoVegetationDialog.
Lazy-seeds curated defaults if the dimension has no settings yet.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 11: Manual smoke test

No code changes — verify the feature works end-to-end in the running app.

- [ ] **Step 11.1: Start the app**

```bash
cd WorldPainter
mvn -pl WPGUI exec:exec
```

- [ ] **Step 11.2: Walk the checklist**

Run each step and record the result:

1. **New world → paint auto-veg over Drifting Plains.** Create a new Hytale dimension. Use the biome paint tool to paint a region as Drifting Plains. Switch to the Layers tab → click **Auto Vegetation** → paint over the same region. Export. Open the export in Hytale: expect grass and flowers scattered across the painted region. ✅ / ❌
2. **Tundra mix.** Repeat for Frostmarch Tundra. Expect sparser frosted grass / pine saplings. ✅ / ❌
3. **Manual override.** Paint a single plant with the plant brush inside an auto-veg region. Export. That pixel keeps the manual plant; neighboring pixels get curated plants. ✅ / ❌
4. **Underwater.** Paint auto-veg on a flooded column. Export. Plant block on the seabed, water fills the column above. ✅ / ❌
5. **Settings — Tundra coverage 0.** Right-click Auto Vegetation → Settings → set Tundra coverage to 0 → OK → re-export. Tundra is bare; Drifting Plains unchanged. ✅ / ❌
6. **Settings — Reset.** Click Reset to defaults → values revert to the shipped JSON. ✅ / ❌
7. **Old save.** Open a pre-feature `.world` file. No errors. Auto Vegetation appears in the Layers tab unpainted. ✅ / ❌

If any step fails, file a follow-up task with the exact reproduction.

- [ ] **Step 11.3: Once all green, push the branch and open a PR**

```bash
git push -u origin <branch-name>
gh pr create --title "feat(hytale): auto-vegetation layer (TP-56)" --body "$(cat <<'EOF'
## Summary
- Adds a Hytale-only built-in "Auto Vegetation" layer that procedurally scatters biome-appropriate plants at export.
- Curated biome -> plant-mix defaults shipped in `WPCore/src/main/resources/hytale/auto-vegetation-defaults.json`.
- Per-dimension settings editable via right-click → Settings.

Spec: docs/superpowers/specs/2026-05-12-tp56-populate-with-vegetation-design.md
Plan: docs/superpowers/plans/2026-05-12-tp56-populate-with-vegetation.md
YouTrack: TP-56

## Test plan
- [x] Unit: HytaleAutoVegetationLayerTest
- [x] Unit: HytaleAutoVegetationSettingsTest
- [x] Unit: HytaleAutoVegetationDefaultsTest (incl. shipped-JSON resolution guard)
- [x] Unit: HytaleAutoVegetationAlgorithmTest
- [x] Integration: Tp56AutoVegetationExportTest
- [x] Regression: Tp53UnderwaterPlantTest, Tp60PlantSubstrateTest, Tp60PlantsPhysicsExemptToggleTest
- [x] Manual smoke checklist passed (Task 11)

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

When the PR merges, follow your standard YouTrack workflow to move TP-56 to **Review**.
