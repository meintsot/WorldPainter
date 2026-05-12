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
                    HytaleTerrain terrain = resolveTerrainName(terrainName);
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
     * Resolves a terrain name from the JSON. Tries block-ID lookup first (since the
     * JSON typically uses Hytale block IDs), then falls back to display-name lookup
     * (case-insensitive) for human-readable names. Returns {@code null} if neither
     * lookup finds a match.
     */
    private static HytaleTerrain resolveTerrainName(String name) {
        if (name == null) {
            return null;
        }
        HytaleTerrain t = HytaleTerrain.getByBlockId(name);
        if (t != null) {
            return t;
        }
        return HytaleTerrain.getByName(name);
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
                if (!root.isJsonObject()) {
                    return unresolved;
                }
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject().entrySet()) {
                    if (!e.getValue().isJsonObject()) {
                        continue;
                    }
                    JsonObject cfg = e.getValue().getAsJsonObject();
                    if (!cfg.has("plants") || !cfg.get("plants").isJsonArray()) {
                        continue;
                    }
                    for (JsonElement plantEl : cfg.getAsJsonArray("plants")) {
                        if (!plantEl.isJsonObject()) {
                            continue;
                        }
                        String name = plantEl.getAsJsonObject().has("terrain")
                                ? plantEl.getAsJsonObject().get("terrain").getAsString() : null;
                        if (name != null && resolveTerrainName(name) == null) {
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
