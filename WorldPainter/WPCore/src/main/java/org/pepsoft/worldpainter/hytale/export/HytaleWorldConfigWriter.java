package org.pepsoft.worldpainter.hytale.export;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.World2;
import org.pepsoft.worldpainter.hytale.HytaleWorldSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Point;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.pepsoft.worldpainter.Dimension.Anchor.NORMAL_DETAIL;

/**
 * Emits the JSON configuration files for a Hytale save (world-level config.json,
 * resource files, server config, and server boilerplate). Extracted from
 * {@link HytaleWorldExporter} so the exporter can focus on chunk population.
 *
 * <p>The writer needs the {@code blockOffsetX/Z} computed by the exporter (the
 * centering offset applied to terrain so the Hytale spawn does not fall into the
 * void); construct after those offsets are known.
 */
class HytaleWorldConfigWriter {
    private static final Logger logger = LoggerFactory.getLogger(HytaleWorldConfigWriter.class);

    private final World2 world;
    private final int blockOffsetX;
    private final int blockOffsetZ;

    HytaleWorldConfigWriter(World2 world, int blockOffsetX, int blockOffsetZ) {
        this.world = world;
        this.blockOffsetX = blockOffsetX;
        this.blockOffsetZ = blockOffsetZ;
    }

    void writeWorldConfig(File worldDir, String displayName) throws IOException {
        Dimension dim0 = world.getDimension(NORMAL_DETAIL);

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("Version", 4);

        // Generate UUID
        UUID uuid = UUID.randomUUID();
        Map<String, String> uuidMap = new LinkedHashMap<>();
        uuidMap.put("$binary", Base64.getEncoder().encodeToString(uuidToBytes(uuid)));
        uuidMap.put("$type", "04");
        config.put("UUID", uuidMap);

        config.put("DisplayName", displayName);
        config.put("Seed", dim0 != null ? dim0.getMinecraftSeed() : System.currentTimeMillis());

        Map<String, String> worldGen = new LinkedHashMap<>();
        worldGen.put("Type", world.getAttribute(HytaleWorldSettings.ATTRIBUTE_WORLD_GEN_TYPE)
                .orElse(HytaleWorldSettings.DEFAULT_WORLD_GEN_TYPE));
        config.put("WorldGen", worldGen);

        Map<String, String> worldMap = new LinkedHashMap<>();
        worldMap.put("Type", "WorldGen");
        config.put("WorldMap", worldMap);

        Map<String, String> chunkStorage = new LinkedHashMap<>();
        chunkStorage.put("Type", "Hytale");
        config.put("ChunkStorage", chunkStorage);

        final boolean pvpEnabled = world.getAttribute(HytaleWorldSettings.ATTRIBUTE_IS_PVP_ENABLED).orElse(false);
        final boolean fallDamageEnabled = world.getAttribute(HytaleWorldSettings.ATTRIBUTE_IS_FALL_DAMAGE_ENABLED).orElse(true);
        final boolean spawningNpcEnabled = world.getAttribute(HytaleWorldSettings.ATTRIBUTE_IS_SPAWNING_NPC).orElse(true);
        final String gameplayConfig = world.getAttribute(HytaleWorldSettings.ATTRIBUTE_GAMEPLAY_CONFIG)
                .orElse(HytaleWorldSettings.DEFAULT_GAMEPLAY_CONFIG);

        // SpawnProvider - tells Hytale where players spawn
        Point spawnPoint = world.getSpawnPoint();
        if (spawnPoint != null) {
            int spawnX = spawnPoint.x + blockOffsetX;
            int spawnZ = spawnPoint.y + blockOffsetZ;
            int spawnY = 0;
            if (dim0 != null) {
                int height = dim0.getIntHeightAt(spawnPoint.x, spawnPoint.y);
                if (height >= 0) {
                    spawnY = height + 1;
                }
            }
            Map<String, Object> spawnProvider = new LinkedHashMap<>();
            spawnProvider.put("Type", "Global");
            Map<String, Object> spawnTransform = new LinkedHashMap<>();
            spawnTransform.put("X", (double) spawnX);
            spawnTransform.put("Y", (double) spawnY);
            spawnTransform.put("Z", (double) spawnZ);
            spawnProvider.put("SpawnPoint", spawnTransform);
            config.put("SpawnProvider", spawnProvider);
            logger.info("Set SpawnProvider in config.json at ({}, {}, {})", spawnX, spawnY, spawnZ);
        }

        config.put("ChunkConfig", new LinkedHashMap<>());
        config.put("IsTicking", true);
        config.put("IsBlockTicking", true);
        config.put("IsPvpEnabled", pvpEnabled);
        config.put("IsFallDamageEnabled", fallDamageEnabled);
        config.put("IsGameTimePaused", false);
        config.put("GameTime", "0001-01-01T05:30:00.000000000Z");

        Map<String, Object> clientEffects = new LinkedHashMap<>();
        clientEffects.put("SunHeightPercent", 100.0);
        clientEffects.put("SunAngleDegrees", 0.0);
        clientEffects.put("BloomIntensity", 0.3);
        clientEffects.put("BloomPower", 8.0);
        clientEffects.put("SunIntensity", 0.25);
        clientEffects.put("SunshaftIntensity", 0.3);
        clientEffects.put("SunshaftScaleFactor", 4.0);
        config.put("ClientEffects", clientEffects);

        config.put("RequiredPlugins", new LinkedHashMap<>());
        config.put("GameMode", HytaleWorldSettings.toHytaleGameModeName(world.getGameType()));
        config.put("IsSpawningNPC", spawningNpcEnabled);
        config.put("IsSpawnMarkersEnabled", true);
        config.put("IsAllNPCFrozen", false);
        config.put("GameplayConfig", gameplayConfig);
        config.put("IsCompassUpdating", true);
        config.put("IsSavingPlayers", true);
        config.put("IsSavingChunks", true);
        config.put("SaveNewChunks", true);
        config.put("IsUnloadingChunks", true);
        config.put("IsObjectiveMarkersEnabled", true);
        config.put("DeleteOnUniverseStart", false);
        config.put("DeleteOnRemove", false);

        Map<String, String> resourceStorage = new LinkedHashMap<>();
        resourceStorage.put("Type", "Hytale");
        config.put("ResourceStorage", resourceStorage);

        config.put("Plugin", new LinkedHashMap<>());

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(config);

        File configFile = new File(worldDir, "config.json");
        Files.write(configFile.toPath(), json.getBytes(StandardCharsets.UTF_8));

        logger.debug("Wrote config.json to {}", configFile);
    }

    /**
     * Write resource files to the world's resources directory, including
     * PrefabEditSession.json with the spawn point coordinates.
     */
    void writeResourceFiles(File worldDir) throws IOException {
        File resourcesDir = new File(worldDir, "resources");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        // PrefabEditSession.json - sets the world spawn point
        Point spawnPoint = world.getSpawnPoint();
        int spawnX = 0, spawnY = 0, spawnZ = 0;
        if (spawnPoint != null) {
            spawnX = spawnPoint.x + blockOffsetX;
            spawnZ = spawnPoint.y + blockOffsetZ;
            // Get terrain height at spawn point from dimension
            Dimension dim0 = world.getDimension(NORMAL_DETAIL);
            if (dim0 != null) {
                int height = dim0.getIntHeightAt(spawnPoint.x, spawnPoint.y);
                if (height >= 0) {
                    spawnY = height + 1;
                }
            }
        }
        Map<String, Object> prefabEditSession = new LinkedHashMap<>();
        prefabEditSession.put("SpawnPoint", new int[]{spawnX, spawnY, spawnZ});
        prefabEditSession.put("LoadedPrefabMetadata", new Object[0]);
        Files.write(new File(resourcesDir, "PrefabEditSession.json").toPath(),
                gson.toJson(prefabEditSession).getBytes(StandardCharsets.UTF_8));

        // InstanceData.json
        Map<String, Object> instanceData = new LinkedHashMap<>();
        instanceData.put("HadPlayer", false);
        Files.write(new File(resourcesDir, "InstanceData.json").toPath(),
                gson.toJson(instanceData).getBytes(StandardCharsets.UTF_8));

        logger.info("Wrote resource files with spawn point ({}, {}, {})", spawnX, spawnY, spawnZ);
    }

    /**
     * Write the server-level config.json for the Hytale save.
     */
    void writeServerConfig(File saveDir) throws IOException {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("SkipModValidationForVersion", null);

        Map<String, Object> backup = new LinkedHashMap<>();
        backup.put("Enabled", false);
        config.put("Backup", backup);

        config.put("Version", 4);
        config.put("Mods", new LinkedHashMap<>());

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Files.write(new File(saveDir, "config.json").toPath(),
                gson.toJson(config).getBytes(StandardCharsets.UTF_8));
        logger.debug("Wrote server config.json to {}", saveDir);
    }

    /**
     * Write boilerplate files for the Hytale save (bans, permissions, whitelist, memories).
     */
    void writeServerBoilerplate(File saveDir) throws IOException {
        // bans.json - empty list
        Files.write(new File(saveDir, "bans.json").toPath(),
                "[]".getBytes(StandardCharsets.UTF_8));

        // permissions.json - Creative exports grant groups.Creative=["*"] so every joining
        // player gets admin perms via the auto-injected gameplay-mode group; see TP-52.
        Map<String, Object> permissions = HytaleWorldSettings.buildPermissionsJson(world.getGameType());
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Files.write(new File(saveDir, "permissions.json").toPath(),
                gson.toJson(permissions).getBytes(StandardCharsets.UTF_8));

        // whitelist.json - disabled
        Map<String, Object> whitelist = new LinkedHashMap<>();
        whitelist.put("enabled", false);
        whitelist.put("list", new String[0]);
        Files.write(new File(saveDir, "whitelist.json").toPath(),
                gson.toJson(whitelist).getBytes(StandardCharsets.UTF_8));

        // universe/memories.json - empty memories
        File universeDir = new File(saveDir, "universe");
        Map<String, Object> memories = new LinkedHashMap<>();
        memories.put("Memories", new Object[0]);
        Files.write(new File(universeDir, "memories.json").toPath(),
                gson.toJson(memories).getBytes(StandardCharsets.UTF_8));

        logger.debug("Wrote server boilerplate files to {}", saveDir);
    }

    private static byte[] uuidToBytes(UUID uuid) {
        byte[] bytes = new byte[16];
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        for (int i = 0; i < 8; i++) {
            bytes[i] = (byte) (msb >>> (8 * (7 - i)));
            bytes[8 + i] = (byte) (lsb >>> (8 * (7 - i)));
        }
        return bytes;
    }
}
