package org.pepsoft.worldpainter.hytale;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.pepsoft.minecraft.Chunk;
import org.pepsoft.minecraft.ChunkFactory;
import org.pepsoft.minecraft.Entity;
import org.pepsoft.minecraft.Material;
import org.pepsoft.minecraft.MinecraftCoords;
import org.pepsoft.minecraft.TileEntity;
import org.pepsoft.util.FileUtils;
import org.pepsoft.util.ParallelProgressManager;
import org.pepsoft.util.ProgressReceiver;
import org.pepsoft.util.SubProgressReceiver;
import org.pepsoft.util.Box;
import org.pepsoft.util.mdc.MDCCapturingRuntimeException;
import org.pepsoft.util.mdc.MDCThreadPoolExecutor;
import org.pepsoft.worldpainter.*;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Dimension.Anchor;
import org.pepsoft.worldpainter.exporting.*;
import org.pepsoft.worldpainter.history.HistoryEntry;
import org.pepsoft.worldpainter.layers.Bo2Layer;
import org.pepsoft.worldpainter.layers.Layer;
import org.pepsoft.worldpainter.layers.Biome;
import org.pepsoft.worldpainter.layers.Frost;
import org.pepsoft.worldpainter.layers.FloodWithLava;
import org.pepsoft.worldpainter.layers.bo2.Bo2LayerExporter;
import org.pepsoft.worldpainter.layers.exporters.FrostExporter;
import org.pepsoft.worldpainter.exporting.LayerExporter;
import org.pepsoft.worldpainter.exporting.FirstPassLayerExporter;
import org.pepsoft.worldpainter.exporting.SecondPassLayerExporter;
import org.pepsoft.worldpainter.util.FileInUseException;
import org.pepsoft.worldpainter.vo.AttributeKeyVO;
import org.pepsoft.worldpainter.vo.EventVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.pepsoft.util.ExceptionUtils.chainContains;
import static org.pepsoft.util.mdc.MDCUtils.doWithMdcContext;
import static org.pepsoft.minecraft.Constants.MC_PICKLES;
import static org.pepsoft.minecraft.Constants.MC_SEA_PICKLE;
import static org.pepsoft.minecraft.Constants.MC_LAVA;
import static org.pepsoft.minecraft.Constants.MC_WATER;
import static org.pepsoft.minecraft.Constants.MC_WATERLOGGED;
import static org.pepsoft.worldpainter.Constants.*;
import static org.pepsoft.worldpainter.DefaultPlugin.HYTALE;
import static org.pepsoft.worldpainter.Dimension.Anchor.NORMAL_DETAIL;
import static org.pepsoft.worldpainter.Dimension.Anchor.NORMAL_DETAIL_CEILING;
import static org.pepsoft.worldpainter.Dimension.Role.DETAIL;
import static org.pepsoft.worldpainter.util.ThreadUtils.chooseThreadCountForExport;

/**
 * World exporter for Hytale format.
 * 
 * Hytale uses 32x32 chunks instead of Minecraft's 16x16, and stores them in IndexedStorageFile format with Zstd compression.
 */
public class HytaleWorldExporter implements WorldExporter {
    
    private static final Logger logger = LoggerFactory.getLogger(HytaleWorldExporter.class);
    
    private final World2 world;
    private final WorldExportSettings worldExportSettings;
    private final Platform platform;
    private final Semaphore performingFixups = new Semaphore(1);
    private static final BlockBasedExportSettings HYTALE_LIGHTING_SETTINGS = new BlockBasedExportSettings() {
        @Override
        public boolean isCalculateSkyLight() {
            return true;
        }

        @Override
        public boolean isCalculateBlockLight() {
            return true;
        }

        @Override
        public boolean isCalculateLeafDistance() {
            return false;
        }

        @Override
        public boolean isRemoveFloatingLeaves() {
            return false;
        }
    };
    
    // Offset to center terrain at world origin (computed during export)
    private int blockOffsetX = 0;
    private int blockOffsetZ = 0;
    
    // Prefab paster for inlining prefab blocks during export (initialized at export time)
    private HytalePrefabPaster prefabPaster;

    // Original chunk store for merging imported data (entities, health, metadata) on re-export
    private HytaleChunkStore originalChunkStore;
    
    public HytaleWorldExporter(World2 world, WorldExportSettings exportSettings) {
        this.world = world;
        this.platform = HYTALE;
        this.worldExportSettings = (exportSettings != null) 
            ? exportSettings 
            : (world.getExportSettings() != null ? world.getExportSettings() : new WorldExportSettings());
    }
    
    @Override
    public World2 getWorld() {
        return world;
    }
    
    @Override
    public File selectBackupDir(File worldDir) throws IOException {
        return null;
    }
    
    @Override
    public Map<Integer, ChunkFactory.Stats> export(File baseDir, String name, File backupDir, ProgressReceiver progressReceiver) 
            throws IOException, ProgressReceiver.OperationCancelled {
        return doWithMdcContext(() -> {
            // Sanity checks
            final Set<Point> selectedTiles = worldExportSettings.getTilesToExport();
            final Set<Integer> selectedDimensions = worldExportSettings.getDimensionsToExport();
            if ((selectedTiles != null) && ((selectedDimensions == null) || (selectedDimensions.size() != 1))) {
                throw new IllegalArgumentException("If a tile selection is active then exactly one dimension must be selected");
            }
            
            // Create save directory (full Hytale save structure)
            File saveDir = new File(baseDir, FileUtils.sanitiseName(name));
            logger.info("Exporting world {} to Hytale save at {}", world.getName(), saveDir);
            
            if (saveDir.isDirectory()) {
                logger.info("Directory already exists; deleting previous Hytale export at {}", saveDir);
                deleteRecursive(saveDir.toPath());
            } else if (saveDir.exists()) {
                throw new IllegalStateException("Target path exists but is not a directory: " + saveDir);
            }

            if ((backupDir != null) && backupDir.exists()) {
                try {
                    deleteRecursive(backupDir.toPath());
                } catch (IOException e) {
                    logger.debug("Could not remove unused backup path {}", backupDir, e);
                }
            }
            
            // Record start time
            long start = System.currentTimeMillis();
            
            // Determine if the target directory is on a different (potentially slower/problematic) drive.
            // When it is, export to a temp directory on the same drive as the target, then move the result.
            // This avoids OutOfMemoryErrors caused by slow I/O holding region data in RAM too long.
            Path systemTempRoot = Path.of(System.getProperty("java.io.tmpdir"));
            boolean useTempDir = false;
            try {
                java.nio.file.FileStore targetStore = Files.getFileStore(baseDir.toPath());
                java.nio.file.FileStore tempStore = Files.getFileStore(systemTempRoot);
                useTempDir = !targetStore.equals(tempStore);
                if (useTempDir) {
                    logger.info("Target directory is on a different drive ({}), will export to temp dir on target drive",
                        targetStore);
                }
            } catch (IOException e) {
                logger.warn("Could not determine file stores, exporting directly to target", e);
            }
            
            File effectiveSaveDir;
            Path tempDir = null;
            if (useTempDir) {
                tempDir = Files.createTempDirectory(baseDir.toPath(), "wp-hytale-export-");
                effectiveSaveDir = tempDir.resolve(FileUtils.sanitiseName(name)).toFile();
            } else {
                effectiveSaveDir = saveDir;
            }
            
            try {
                // Create full Hytale save directory structure:
                // saveDir/config.json              (server config)
                // saveDir/bans.json                (empty bans list)
                // saveDir/permissions.json          (default permissions)
                // saveDir/whitelist.json            (disabled whitelist)
                // saveDir/universe/memories.json    (empty memories)
                // saveDir/universe/players/         (empty players dir)
                // saveDir/universe/worlds/default/  (the actual world)
                //   config.json, chunks/, resources/
                if (!effectiveSaveDir.mkdirs()) {
                    throw new IOException("Could not create directory: " + effectiveSaveDir);
                }
                
                File actualWorldDir = new File(new File(new File(effectiveSaveDir, "universe"), "worlds"), "default");
                
                File chunksDir = new File(actualWorldDir, "chunks");
                if (!chunksDir.mkdirs()) {
                    throw new IOException("Could not create chunks directory");
                }
                
                File resourcesDir = new File(actualWorldDir, "resources");
                if (!resourcesDir.mkdirs()) {
                    throw new IOException("Could not create resources directory");
                }
                
                File playersDir = new File(new File(effectiveSaveDir, "universe"), "players");
                if (!playersDir.mkdirs()) {
                    throw new IOException("Could not create players directory");
                }
                
                // Initialize prefab paster for inlining prefab blocks into chunk data
                prefabPaster = new HytalePrefabPaster(HytaleTerrain.getHytaleAssetsDir());
                
                // Export dimensions (must come before writeWorldConfig so blockOffsetX/Z are set)
                Map<Integer, ChunkFactory.Stats> stats = new HashMap<>();
                Dimension dim0 = world.getDimension(NORMAL_DETAIL);
                if (dim0 != null) {
                    if (progressReceiver != null) {
                        progressReceiver.setMessage("Exporting Overworld to Hytale format");
                    }
                    stats.put(DIM_NORMAL, exportDimension(actualWorldDir, dim0, selectedTiles, progressReceiver));
                }
                
                // Write world-level config.json (after exportDimension so blockOffsetX/Z are set for SpawnProvider)
                writeWorldConfig(actualWorldDir, name);
                
                // Write world resource files
                writeResourceFiles(actualWorldDir);
                
                // Write server-level config and boilerplate files
                writeServerConfig(effectiveSaveDir);
                writeServerBoilerplate(effectiveSaveDir);
                
                // If we used a temp directory, move the result to the target location
                if (useTempDir) {
                    if (progressReceiver != null) {
                        progressReceiver.setMessage("Moving exported world to target directory...");
                    }
                    logger.info("Moving exported world from {} to {}", effectiveSaveDir, saveDir);
                    // Delete any existing target directory first so the move can succeed
                    if (saveDir.exists()) {
                        deleteRecursive(saveDir.toPath());
                    }
                    // Both paths are on the same drive, so this is a fast rename (no data copy)
                    Files.move(effectiveSaveDir.toPath(), saveDir.toPath());
                }
                
                // Record the export in the world history
                world.addHistoryEntry(HistoryEntry.WORLD_EXPORTED_FULL, name, saveDir);
                
                // Log event
                Configuration config = Configuration.getInstance();
                if (config != null) {
                    EventVO event = new EventVO(EVENT_KEY_ACTION_EXPORT_WORLD).duration(System.currentTimeMillis() - start);
                    event.setAttribute(EventVO.ATTRIBUTE_TIMESTAMP, new Date(start));
                    event.setAttribute(ATTRIBUTE_KEY_MAX_HEIGHT, world.getMaxHeight());
                    event.setAttribute(ATTRIBUTE_KEY_PLATFORM, platform.displayName);
                    event.setAttribute(ATTRIBUTE_KEY_PLATFORM_ID, platform.id);
                    config.logEvent(event);
                }
                
                logger.info("Export completed in {} ms", System.currentTimeMillis() - start);
                return stats;
            } finally {
                // Clean up temp directory
                if (tempDir != null) {
                    try {
                        deleteRecursive(tempDir);
                    } catch (IOException e) {
                        logger.warn("Could not fully clean up temp directory: {}", tempDir, e);
                    }
                }
            }
        }, "world.name", world.getName(), "platform.id", platform.id);
    }
    
    /**
     * Copy a directory tree from source to target.
     */
    private static void copyDirectory(Path source, Path target) throws IOException {
        Files.createDirectories(target);
        try (java.util.stream.Stream<Path> stream = Files.walk(source)) {
            stream.forEach(src -> {
                Path dst = target.resolve(source.relativize(src));
                try {
                    if (Files.isDirectory(src)) {
                        Files.createDirectories(dst);
                    } else {
                        Files.copy(src, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (java.io.UncheckedIOException e) {
            throw e.getCause();
        }
    }
    
    /**
     * Recursively delete a directory tree.
     */
    private static void deleteRecursive(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (java.util.stream.Stream<Path> stream = Files.list(path)) {
                for (Path child : stream.collect(java.util.stream.Collectors.toList())) {
                    deleteRecursive(child);
                }
            }
        }
        Files.deleteIfExists(path);
    }
    
    /**
     * Write the Hytale world config.json file.
     */
    private void writeWorldConfig(File worldDir, String displayName) throws IOException {
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
        
        // Use Void world gen so Hytale doesn't generate its own terrain
        Map<String, String> worldGen = new LinkedHashMap<>();
        worldGen.put("Type", "Void");
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
    private void writeResourceFiles(File worldDir) throws IOException {
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
    private void writeServerConfig(File saveDir) throws IOException {
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
    private void writeServerBoilerplate(File saveDir) throws IOException {
        // bans.json - empty list
        Files.write(new File(saveDir, "bans.json").toPath(),
                "[]".getBytes(StandardCharsets.UTF_8));
        
        // permissions.json - default with OP group
        Map<String, Object> permissions = new LinkedHashMap<>();
        permissions.put("users", new LinkedHashMap<>());
        Map<String, Object> groups = new LinkedHashMap<>();
        groups.put("Default", new String[0]);
        groups.put("OP", new String[]{"*"});
        permissions.put("groups", groups);
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
    
    private byte[] uuidToBytes(UUID uuid) {
        byte[] bytes = new byte[16];
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        for (int i = 0; i < 8; i++) {
            bytes[i] = (byte) (msb >>> (8 * (7 - i)));
            bytes[8 + i] = (byte) (lsb >>> (8 * (7 - i)));
        }
        return bytes;
    }
    
    /**
     * Export a dimension by exporting each region in parallel.
     */
    private ChunkFactory.Stats exportDimension(File worldDir, Dimension dimension, Set<Point> selectedTiles, ProgressReceiver progressReceiver) 
            throws ProgressReceiver.OperationCancelled {
        return doWithMdcContext(() -> {
            if (progressReceiver != null) {
                progressReceiver.setMessage("Exporting " + dimension.getName() + " dimension to Hytale format");
            }
            
            long start = System.currentTimeMillis();
            ChunkFactory.Stats collectedStats = new ChunkFactory.Stats();
            
            // Determine regions to export
            // In Hytale: region = 32x32 chunks, chunk = 32x32 blocks
            // WorldPainter tile = 128x128 blocks = 4x4 Hytale chunks
            Set<Point> regions = new HashSet<>();
            Set<Point> allTileCoords = dimension.getTileCoords();
            // If the user made a tile selection, restrict to those tiles only
            Set<Point> tileCoords = (selectedTiles != null)
                ? allTileCoords.stream().filter(selectedTiles::contains).collect(java.util.stream.Collectors.toSet())
                : allTileCoords;
            
            // Calculate the center offset to ensure terrain is centered at world origin (0,0)
            // This way players spawn on the WorldPainter terrain instead of Hytale-generated void
            int minTileX = Integer.MAX_VALUE, maxTileX = Integer.MIN_VALUE;
            int minTileY = Integer.MAX_VALUE, maxTileY = Integer.MIN_VALUE;
            for (Point tile : tileCoords) {
                minTileX = Math.min(minTileX, tile.x);
                maxTileX = Math.max(maxTileX, tile.x);
                minTileY = Math.min(minTileY, tile.y);
                maxTileY = Math.max(maxTileY, tile.y);
            }
            // Center offset in tiles (WorldPainter tiles are 128x128 blocks)
            int centerTileX = (minTileX + maxTileX) / 2;
            int centerTileY = (minTileY + maxTileY) / 2;
            // Convert to block offset (we want to shift the entire world so center is at 0,0)
            // Store in instance fields so exportRegion can use them
            this.blockOffsetX = -centerTileX * 128;
            this.blockOffsetZ = -centerTileY * 128;
            
            logger.info("Centering terrain: tile center ({},{}), block offset ({},{})",
                centerTileX, centerTileY, blockOffsetX, blockOffsetZ);

            // Open the original imported world (if any) for merging entities, block health,
            // and metadata back into the exported chunks for round-trip fidelity
            openOriginalChunkStore(dimension);

            for (Point tile : tileCoords) {
                // Apply offset when calculating Hytale chunk coords
                int worldBlockX = tile.x * 128 + blockOffsetX;
                int worldBlockZ = tile.y * 128 + blockOffsetZ;
                int hyChunkX = worldBlockX >> 5; // / 32
                int hyChunkZ = worldBlockZ >> 5;
                // Also include chunks for the far edge of the tile
                int hyChunkX2 = (worldBlockX + 127) >> 5;
                int hyChunkZ2 = (worldBlockZ + 127) >> 5;
                
                for (int cx = hyChunkX; cx <= hyChunkX2; cx++) {
                    for (int cz = hyChunkZ; cz <= hyChunkZ2; cz++) {
                        int regionX = cx >> 5; // Hytale chunk to region (32 chunks per region)
                        int regionZ = cz >> 5;
                        regions.add(new Point(regionX, regionZ));
                    }
                }
            }
            
            if (regions.isEmpty()) {
                logger.warn("No regions to export for dimension {}", dimension.getName());
                return collectedStats;
            }
            
            logger.info("Processing {} regions for dimension {}", regions.size(), dimension.getName());
            final boolean hasCustomObjects = hasCustomObjectLayers(dimension);
            final boolean hasSecondPass = hasSecondPassLayers(dimension);
            final boolean needsFullRegionRetention = hasCustomObjects || hasSecondPass;
            logger.info("Hytale custom object layers present: {}, second-pass layers: {}", hasCustomObjects, hasSecondPass);

            final Dimension ceilingDimension = world.getDimension(NORMAL_DETAIL_CEILING);
            if (ceilingDimension != null) {
                logger.info("Ceiling dimension found; will export ceiling terrain at ceilingHeight={}",
                    dimension.getCeilingHeight());
            }

            // Export region files with BSON-serialized chunk data
            File chunksDir = new File(worldDir, "chunks");
            
            List<Point> sortedRegions = new ArrayList<>(regions);
            ParallelProgressManager parallelProgressManager = (progressReceiver != null) 
                ? new ParallelProgressManager(progressReceiver, regions.size()) : null;
            AtomicBoolean abort = new AtomicBoolean(false);
            RuntimeException[] exception = new RuntimeException[1];
            
            // Limit concurrent regions in memory to avoid OutOfMemoryError.
            // For Hytale exports each region can be very memory heavy, especially when writing to slow drives
            // where generated data can stay in memory longer while waiting for I/O.
            //
            // Default is adaptive to target drive throughput and can be overridden with
            // -Dorg.pepsoft.worldpainter.hytale.maxConcurrentRegions=N.
            final Runtime runtime = Runtime.getRuntime();
            final long maxMem = runtime.maxMemory();
            Integer configured = Integer.getInteger("org.pepsoft.worldpainter.hytale.maxConcurrentRegions");
            final long writeSpeedMBps = estimateDriveWriteSpeedMBps(worldDir);
            final int adaptiveDefaultConcurrentRegions;
            if (writeSpeedMBps >= 300L) {
                adaptiveDefaultConcurrentRegions = 4;
            } else if (writeSpeedMBps >= 150L) {
                adaptiveDefaultConcurrentRegions = 3;
            } else {
                adaptiveDefaultConcurrentRegions = 2;
            }
            final int configuredMaxConcurrentRegions = (configured != null)
                    ? Math.max(1, configured)
                    : adaptiveDefaultConcurrentRegions;
            final int maxByMemory = Math.max(1, (int) (maxMem / (1536L * 1024 * 1024)));
            final int maxByContent = needsFullRegionRetention ? 1 : configuredMaxConcurrentRegions;
            final int maxConcurrentRegions = Math.max(1,
                Math.min(Math.min(maxByContent, maxByMemory), sortedRegions.size()));
            final Semaphore regionMemorySemaphore = new Semaphore(maxConcurrentRegions);
            final ExecutorService executor = createExecutorService("hytale-export", maxConcurrentRegions);
            logger.info("Limiting concurrent region exports to {} (configured: {}, adaptive default: {}, drive write: {} MB/s, memory cap: {}, max memory: {} MB)",
                maxConcurrentRegions, configuredMaxConcurrentRegions, adaptiveDefaultConcurrentRegions, writeSpeedMBps, maxByMemory, maxMem / (1024 * 1024));
            
            try {
                for (Point region : sortedRegions) {
                    executor.execute(() -> {
                        if (abort.get()) return;
                        
                        try {
                            regionMemorySemaphore.acquire();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        
                        ProgressReceiver regionProgress = (parallelProgressManager != null) 
                            ? parallelProgressManager.createProgressReceiver() : null;
                        
                        if (regionProgress != null) {
                            try {
                                regionProgress.checkForCancellation();
                            } catch (ProgressReceiver.OperationCancelled e) {
                                abort.set(true);
                                regionMemorySemaphore.release();
                                return;
                            }
                        }
                        
                        try {
                            exportRegion(chunksDir, dimension, ceilingDimension, tileCoords, region, collectedStats, regionProgress, needsFullRegionRetention);
                        } catch (Throwable t) {
                            if (chainContains(t, ProgressReceiver.OperationCancelled.class)) {
                                logger.debug("Operation cancelled on thread {}", Thread.currentThread().getName());
                            } else {
                                logger.error("Error exporting region {},{}: {}", region.x, region.y, t.getMessage(), t);
                            }
                            abort.set(true);
                            if (regionProgress != null) {
                                regionProgress.exceptionThrown(t);
                            } else if (exception[0] == null) {
                                exception[0] = new RuntimeException(t.getClass().getSimpleName() + " while exporting region " + region.x + "," + region.y, t);
                            }
                        } finally {
                            regionMemorySemaphore.release();
                        }
                    });
                }
            } finally {
                executor.shutdown();
                try {
                    executor.awaitTermination(366, TimeUnit.DAYS);
                } catch (InterruptedException e) {
                    throw new MDCCapturingRuntimeException("Thread interrupted while waiting for export to complete", e);
                }
            }
            
            // Check for errors
            if (exception[0] != null) {
                throw exception[0];
            }
            
            logger.info("Exported {} regions with BSON chunk data", sortedRegions.size());

            // Close original chunk store after all regions are exported
            closeOriginalChunkStore();

            collectedStats.time = System.currentTimeMillis() - start;
            
            if (progressReceiver != null) {
                progressReceiver.setProgress(1.0f);
            }
            
            return collectedStats;
        }, "dimension.name", dimension.getName());
    }
    
    /**
     * Export terrain data as JSON for use with custom Hytale plugins/mods.
     * This includes heightmap, block types, and coordinates translated to Hytale coordinate system.
     */
    private void exportTerrainDataAsJson(File worldDir, Dimension dimension, Set<Point> tileCoords, 
            ProgressReceiver progressReceiver) throws IOException {
        
        Map<String, Object> terrainData = new LinkedHashMap<>();
        terrainData.put("format", "TalePainter Hytale Terrain Export");
        terrainData.put("version", 1);
        terrainData.put("blockOffsetX", blockOffsetX);
        terrainData.put("blockOffsetZ", blockOffsetZ);
        terrainData.put("tileCount", tileCoords.size());
        
        List<Map<String, Object>> tiles = new ArrayList<>();
        
        int tileIndex = 0;
        int totalTiles = tileCoords.size();
        
        for (Point tileCoord : tileCoords) {
            Tile tile = dimension.getTile(tileCoord.x, tileCoord.y);
            if (tile == null) continue;
            
            Map<String, Object> tileData = new LinkedHashMap<>();
            
            // Original WorldPainter coordinates
            tileData.put("originalTileX", tileCoord.x);
            tileData.put("originalTileZ", tileCoord.y);
            
            // Hytale world coordinates (with offset applied)
            int hytaleBlockX = tileCoord.x * 128 + blockOffsetX;
            int hytaleBlockZ = tileCoord.y * 128 + blockOffsetZ;
            tileData.put("hytaleBlockX", hytaleBlockX);
            tileData.put("hytaleBlockZ", hytaleBlockZ);
            
            // Heightmap data (128x128 = 16384 values per tile)
            int[] heightmap = new int[128 * 128];
            for (int z = 0; z < 128; z++) {
                for (int x = 0; x < 128; x++) {
                    heightmap[z * 128 + x] = tile.getIntHeight(x, z);
                }
            }
            tileData.put("heightmap", heightmap);
            
            // Water level data
            int[] waterLevels = new int[128 * 128];
            for (int z = 0; z < 128; z++) {
                for (int x = 0; x < 128; x++) {
                    waterLevels[z * 128 + x] = tile.getWaterLevel(x, z);
                }
            }
            tileData.put("waterLevels", waterLevels);
            
            tiles.add(tileData);
            
            tileIndex++;
            if (progressReceiver != null && tileIndex % 10 == 0) {
                try {
                    progressReceiver.setProgress((float) tileIndex / totalTiles * 0.5f);
                } catch (ProgressReceiver.OperationCancelled e) {
                    return;
                }
            }
        }
        
        terrainData.put("tiles", tiles);
        
        // Write terrain data to JSON file
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(terrainData);
        
        File terrainFile = new File(worldDir, "terrain_data.json");
        Files.write(terrainFile.toPath(), json.getBytes(StandardCharsets.UTF_8));
        
        logger.info("Exported terrain data for {} tiles to {}", tiles.size(), terrainFile);
    }
    
    /**
     * Export a single region.
     */
        private void exportRegion(File chunksDir, Dimension dimension, Dimension ceilingDimension, Set<Point> tileCoords, Point regionCoords,
            ChunkFactory.Stats stats, ProgressReceiver progressReceiver, boolean retainChunksForCustomObjects)
            throws IOException, ProgressReceiver.OperationCancelled {
        
        Path regionPath = chunksDir.toPath().resolve(HytaleRegionFile.getRegionFileName(regionCoords.x, regionCoords.y));
        
        try (HytaleRegionFile regionFile = new HytaleRegionFile(regionPath)) {
            regionFile.create();
            
            int minHeight = dimension.getMinHeight();
            int maxHeight = dimension.getMaxHeight();
            
            // Each region contains 32x32 Hytale chunks
            int chunksExported = 0;
            int totalChunks = 32 * 32;
            Map<Long, HytaleChunk> chunksByCoords = new HashMap<>();
            
            for (int localZ = 0; localZ < 32; localZ++) {
                for (int localX = 0; localX < 32; localX++) {
                    // Convert local chunk coords to world Hytale chunk coords
                    int hyChunkX = (regionCoords.x << 5) + localX;
                    int hyChunkZ = (regionCoords.y << 5) + localZ;
                    
                    // Convert Hytale chunk coords to block coords (in centered coordinate system)
                    int blockX = hyChunkX << 5; // * 32
                    int blockZ = hyChunkZ << 5;
                    
                    // Convert back to original WorldPainter coordinates by removing offset
                    // This is the inverse of the centering operation done in exportDimension
                    int originalBlockX = blockX - blockOffsetX;
                    int originalBlockZ = blockZ - blockOffsetZ;
                    
                    // Check if we have tile data for this area
                    // WorldPainter tiles are 128x128 blocks
                    int tileX = originalBlockX >> 7; // / 128
                    int tileZ = originalBlockZ >> 7;
                    
                    if (!tileCoords.contains(new Point(tileX, tileZ))) {
                        chunksExported++;
                        continue;
                    }

                    Tile tile = dimension.getTile(tileX, tileZ);
                    if (tile == null) {
                        // No data for this chunk
                        chunksExported++;
                        continue;
                    }
                    
                    // Create and populate the Hytale chunk
                    HytaleChunk chunk = new HytaleChunk(hyChunkX, hyChunkZ, minHeight, maxHeight);
                    
                    // Fill chunk with terrain data from WorldPainter
                    // Pass original block coordinates so tile lookups work correctly
                    populateChunkFromTile(chunk, dimension, tile, originalBlockX, originalBlockZ);

                    // Paint ceiling terrain hanging from above, if a ceiling dimension exists
                    if (ceilingDimension != null) {
                        int ceilingTileX = originalBlockX >> 7;
                        int ceilingTileZ = originalBlockZ >> 7;
                        Tile ceilingTile = ceilingDimension.getTile(ceilingTileX, ceilingTileZ);
                        if (ceilingTile != null) {
                            populateCeilingIntoChunk(chunk, ceilingDimension, ceilingTile,
                                originalBlockX, originalBlockZ, dimension.getCeilingHeight(),
                                ceilingDimension.isBottomless());
                        }
                    }

                    // Add entities (spawn markers, etc.)
                    addEntitiesToChunk(chunk, dimension, hyChunkX, hyChunkZ);

                    // Merge entities, block health, and metadata from the original
                    // imported world so that re-exporting preserves all original data
                    mergeOriginalChunkData(chunk, originalBlockX, originalBlockZ);

                    chunksByCoords.put(chunkKey(hyChunkX, hyChunkZ), chunk);
                    
                    chunksExported++;
                    if (progressReceiver != null && chunksExported % 32 == 0) {
                        progressReceiver.setProgress((float) chunksExported / totalChunks);
                    }
                }
            }

            // Apply first-pass layers (ground cover, resources) to chunks
            applyFirstPassLayers(dimension, tileCoords, chunksByCoords);

            // Apply second-pass layers (caves, caverns, chasms) - CARVE then ADD_FEATURES
            applySecondPassLayers(dimension, regionCoords, chunksByCoords);

            if (retainChunksForCustomObjects) {
                // Apply custom object layers after terrain generation so placement/collision checks can use the final surface.
                applyCustomObjectLayers(dimension, regionCoords, chunksByCoords);
            }

            // Re-seal fluid bodies: restore any fluid blocks that were cleared
            // by layer exporters (caves, chasms, custom objects) during
            // post-processing. Hytale has no runtime water flow, so every fluid
            // block must be explicitly present in the exported data.
            sealFluidBodies(dimension, chunksByCoords);

            // Apply frost AFTER sealing fluid bodies so that ice placed on
            // water surfaces is not overwritten by the fluid restoration pass.
            applyFrostLayer(dimension, regionCoords, chunksByCoords);

            convertCoveredGrass(chunksByCoords);

            // Skip pre-baked lighting: Hytale recalculates light at runtime
            // when players interact with blocks, which overwrites our values
            // and causes visual artefacts. Writing empty light data lets
            // Hytale compute lighting natively with consistent results.
            // calculateLighting(regionCoords, chunksByCoords, progressReceiver);

            // Final pass: enforce void columns by clearing any blocks/fluids
            // that may have been placed by second-pass layers, custom objects,
            // frost, or lighting. This guarantees void areas are truly empty.
            enforceVoidColumns(dimension, chunksByCoords);

            for (int localZ = 0; localZ < 32; localZ++) {
                for (int localX = 0; localX < 32; localX++) {
                    int hyChunkX = (regionCoords.x << 5) + localX;
                    int hyChunkZ = (regionCoords.y << 5) + localZ;
                    HytaleChunk chunk = chunksByCoords.get(chunkKey(hyChunkX, hyChunkZ));
                    if (chunk == null) {
                        continue;
                    }
                    regionFile.writeChunk(localX, localZ, chunk);
                    synchronized (stats) {
                        stats.surfaceArea += HytaleChunk.CHUNK_SIZE * HytaleChunk.CHUNK_SIZE;
                    }
                }
            }
            
            regionFile.flush();
        }
        
        logger.debug("Exported region {},{} to {}", regionCoords.x, regionCoords.y, regionPath);
    }

    /**
     * Open the original imported world's chunk store so that entities, block health,
     * and metadata can be merged back into re-exported chunks.
     */
    private void openOriginalChunkStore(Dimension dimension) {
        File importedFrom = world.getImportedFrom();
        if (importedFrom == null || !importedFrom.exists()) {
            return;
        }
        // importedFrom points to config.json; parent is the world directory
        File importedWorldDir = importedFrom.getParentFile();
        if (importedWorldDir == null) {
            return;
        }
        File importedChunksDir = new File(importedWorldDir, "chunks");
        if (importedChunksDir.isDirectory()) {
            try {
                originalChunkStore = new HytaleChunkStore(importedWorldDir,
                    dimension.getMinHeight(), dimension.getMaxHeight());
                logger.info("Opened original chunk store at {} for round-trip merge",
                    importedWorldDir.getAbsolutePath());
            } catch (Exception e) {
                logger.warn("Could not open original chunk store for merging: {}", e.getMessage());
                originalChunkStore = null;
            }
        }
    }

    /**
     * Close the original chunk store after export is complete.
     */
    private void closeOriginalChunkStore() {
        if (originalChunkStore != null) {
            try {
                originalChunkStore.close();
            } catch (Exception e) {
                logger.warn("Error closing original chunk store: {}", e.getMessage());
            }
            originalChunkStore = null;
        }
    }

    /**
     * Merge entities, block health, water tints, spawn configuration, and prefab
     * markers from the original imported chunk into the newly generated chunk.
     * This enables round-trip fidelity: import a server world, edit terrain in
     * WorldPainter, and re-export without losing entities, schematics, or metadata.
     *
     * @param newChunk The newly generated chunk to merge data into
     * @param originalBlockX The original (pre-offset) block X coordinate of this chunk's origin
     * @param originalBlockZ The original (pre-offset) block Z coordinate of this chunk's origin
     */
    private void mergeOriginalChunkData(HytaleChunk newChunk, int originalBlockX, int originalBlockZ) {
        if (originalChunkStore == null) {
            return;
        }

        // Calculate the original chunk coordinates (before centering offset was applied).
        // originalBlockX/Z are in WorldPainter tile space; the original Hytale chunk
        // coordinates are simply these divided by 32 (Hytale chunk size).
        int origChunkX = originalBlockX >> 5;
        int origChunkZ = originalBlockZ >> 5;

        HytaleChunk originalChunk;
        try {
            originalChunk = (HytaleChunk) originalChunkStore.getChunk(origChunkX, origChunkZ);
        } catch (Exception e) {
            logger.debug("Could not read original chunk at {},{}: {}", origChunkX, origChunkZ, e.getMessage());
            return;
        }
        if (originalChunk == null) {
            return;
        }

        // 1. Entities: copy all original entities with position adjusted for centering offset.
        //    This includes NPCs, creature spawn markers, and player spawn markers from the
        //    original world. A duplicate player spawn marker may occur (one from addEntitiesToChunk,
        //    one from the original) but this is harmless — Hytale uses whichever is closer.
        for (HytaleEntity entity : originalChunk.getHytaleEntities()) {
            HytaleEntity adjusted = entity.clone();
            adjusted.setPosition(
                entity.getX() + blockOffsetX,
                entity.getY(),
                entity.getZ() + blockOffsetZ
            );
            newChunk.addHytaleEntity(adjusted);
        }

        // 2. Block health: copy all damaged block entries
        for (Map.Entry<Integer, HytaleChunk.BlockHealthData> entry : originalChunk.getBlockHealthMap().entrySet()) {
            int key = entry.getKey();
            int bx = HytaleChunk.unpackX(key);
            int by = HytaleChunk.unpackY(key);
            int bz = HytaleChunk.unpackZ(key);
            HytaleChunk.BlockHealthData data = entry.getValue();
            newChunk.setBlockHealth(bx, by, bz, data.health, data.lastDamageTime);
        }

        // 3. Water tints: preserve original values where the new chunk has no override
        for (int lz = 0; lz < HytaleChunk.CHUNK_SIZE; lz++) {
            for (int lx = 0; lx < HytaleChunk.CHUNK_SIZE; lx++) {
                String origTint = originalChunk.getWaterTint(lx, lz);
                if (origTint != null && newChunk.getWaterTint(lx, lz) == null) {
                    newChunk.setWaterTint(lx, lz, origTint);
                }
            }
        }

        // 4. Spawn density and tags: preserve where new chunk has defaults
        for (int lz = 0; lz < HytaleChunk.CHUNK_SIZE; lz++) {
            for (int lx = 0; lx < HytaleChunk.CHUNK_SIZE; lx++) {
                float origDensity = originalChunk.getSpawnDensity(lx, lz);
                if (origDensity >= 0.0f && newChunk.getSpawnDensity(lx, lz) < 0.0f) {
                    newChunk.setSpawnDensity(lx, lz, origDensity);
                }
                String origTag = originalChunk.getSpawnTag(lx, lz);
                if (origTag != null && newChunk.getSpawnTag(lx, lz) == null) {
                    newChunk.setSpawnTag(lx, lz, origTag);
                }
            }
        }

        // 5. Prefab markers: copy all from original
        for (HytaleChunk.PrefabMarker pm : originalChunk.getPrefabMarkers()) {
            newChunk.addPrefabMarker(pm.x, pm.y, pm.z, pm.category, pm.prefabPath);
        }
    }

    private boolean hasCustomObjectLayers(Dimension dimension) {
        Set<Layer> layers = dimension.getAllLayers(false);
        if (layers.isEmpty()) {
            return false;
        }
        for (Layer layer : layers) {
            if (layer instanceof Bo2Layer) {
                return true;
            }
        }
        return false;
    }

    private long estimateDriveWriteSpeedMBps(File baseDir) {
        final long probeSizeBytes = 8L * 1024L * 1024L; // 8 MB
        byte[] buffer = new byte[64 * 1024];
        Path probeFile = null;
        long startNanos;
        long durationNanos;
        try {
            probeFile = Files.createTempFile(baseDir.toPath(), "wp-hytale-speed-", ".tmp");
            startNanos = System.nanoTime();
            try (java.io.OutputStream out = Files.newOutputStream(probeFile, java.nio.file.StandardOpenOption.WRITE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
                long written = 0;
                while (written < probeSizeBytes) {
                    int toWrite = (int) Math.min(buffer.length, probeSizeBytes - written);
                    out.write(buffer, 0, toWrite);
                    written += toWrite;
                }
                out.flush();
            }
            durationNanos = System.nanoTime() - startNanos;
            if (durationNanos <= 0) {
                return 100;
            }
            long bytesPerSecond = (probeSizeBytes * 1_000_000_000L) / durationNanos;
            long mbps = Math.max(1, bytesPerSecond / (1024L * 1024L));
            return mbps;
        } catch (IOException e) {
            logger.debug("Could not estimate drive write speed for {}", baseDir, e);
            return 100;
        } finally {
            if (probeFile != null) {
                try {
                    Files.deleteIfExists(probeFile);
                } catch (IOException ignored) {
                    // ignore probe cleanup failure
                }
            }
        }
    }
    
    /**
     * Post-processing pass that ensures fluid integrity without carving away
     * dry shoreline terrain.
     * <p>
     * Hytale has no runtime water flow, so every fluid block must be explicitly
     * present in the exported chunk data. This pass restores missing fluid in
     * underwater voids and overwrites any above-terrain blocks that intrude into
     * the intended water column. Sea-level shoreline columns remain dry land; the
     * exporter should not replace valid beach blocks simply because adjacent
     * columns contain water.
     * <p>
     * Must be called after all layer processing (except frost) and before
     * frost/lighting/void passes. Frost runs after this so that ice placed on
     * water surfaces is not overwritten.
     */
    private void sealFluidBodies(Dimension dimension, Map<Long, HytaleChunk> chunksByCoords) {
        int sealed = 0;

        // Restore fluid in all columns that have a water level.
        // Above terrain height: forcefully replace any block (including
        // solid blocks placed by layer exporters like ground cover) with
        // fluid. Below terrain height: only fill empty blocks (caves,
        // chasms carved by exporters).
        for (HytaleChunk chunk : chunksByCoords.values()) {
            int chunkBlockX = chunk.getxPos() << 5;
            int chunkBlockZ = chunk.getzPos() << 5;

            for (int localX = 0; localX < HytaleChunk.CHUNK_SIZE; localX++) {
                for (int localZ = 0; localZ < HytaleChunk.CHUNK_SIZE; localZ++) {
                    int worldX = chunkBlockX + localX - blockOffsetX;
                    int worldZ = chunkBlockZ + localZ - blockOffsetZ;

                    int waterLevel = dimension.getWaterLevelAt(worldX, worldZ);
                    if (waterLevel == Integer.MIN_VALUE || waterLevel <= 0) {
                        continue;
                    }

                    int terrainHeight = dimension.getIntHeightAt(worldX, worldZ);
                    if (waterLevel <= terrainHeight) {
                        continue; // Terrain is at or above water level; no water column
                    }

                    String fluidId = resolveFluidId(dimension, worldX, worldZ);

                    // Below terrain: fill only empty blocks (underground voids)
                    for (int y = 1; y <= terrainHeight; y++) {
                        HytaleBlock block = chunk.getHytaleBlock(localX, y, localZ);
                        if (block != null && !block.isEmpty()) {
                            continue;
                        }
                        HytaleChunk.HytaleSection section = chunk.getSections()[y >> 5];
                        if (section.getFluidId(localX, y & 31, localZ) == 0) {
                            chunk.setHytaleBlock(localX, y, localZ, HytaleBlock.EMPTY);
                            section.setFluid(localX, y & 31, localZ, fluidId, 1);
                            sealed++;
                        }
                    }

                    // Above terrain: forcefully restore fluid, overwriting any
                    // blocks placed by layer exporters (ground cover, etc.)
                    for (int y = terrainHeight + 1; y <= waterLevel; y++) {
                        chunk.setHytaleBlock(localX, y, localZ, HytaleBlock.EMPTY);
                        chunk.getSections()[y >> 5].setFluid(localX, y & 31, localZ, fluidId, 1);
                        sealed++;
                    }
                }
            }
        }

        if (sealed > 0) {
            logger.info("Fluid seal pass: restored {} missing fluid blocks", sealed);
        }
    }

    /**
     * Resolve the fluid type for a column from dimension layer data.
     */
    private String resolveFluidId(Dimension dimension, int worldX, int worldZ) {
        int fluidLayerValue = HytaleFluidLayer.normalizeFluidValue(
            dimension.getLayerValueAt(HytaleFluidLayer.INSTANCE, worldX, worldZ));
        if (fluidLayerValue > 0) {
            return HytaleFluidLayer.getFluidBlockId(fluidLayerValue);
        }
        if (dimension.getBitLayerValueAt(FloodWithLava.INSTANCE, worldX, worldZ)) {
            return HytaleBlockMapping.HY_LAVA;
        }
        return HytaleBlockMapping.HY_WATER;
    }

    /**
     * Post-process grass blocks: convert grass to dirt when covered by a
     * solid/opaque block, but preserve grass when the block above is a plant,
     * decoration, fluid, or other non-solid block. This pre-applies Hytale's
     * in-game grass-to-dirt conversion, avoiding unnecessary runtime work.
     * <p>
     * Must be called after all block-placement passes (terrain, layers,
     * custom objects, frost) but before lighting calculation.
     */
    private void convertCoveredGrass(Map<Long, HytaleChunk> chunksByCoords) {
        int converted = 0;
        for (HytaleChunk chunk : chunksByCoords.values()) {
            for (int x = 0; x < HytaleChunk.CHUNK_SIZE; x++) {
                for (int z = 0; z < HytaleChunk.CHUNK_SIZE; z++) {
                    int height = chunk.getHeight(x, z);
                    for (int y = 0; y <= height; y++) {
                        HytaleBlock block = chunk.getHytaleBlock(x, y, z);
                        if (block != null && block.isGrass()) {
                            HytaleBlock above = chunk.getHytaleBlock(x, y + 1, z);
                            if (above != null && !above.isEmpty()
                                    && !HytaleBlockRegistry.preservesGrassBelow(above.id)) {
                                chunk.setHytaleBlock(x, y, z, HytaleBlock.DIRT);
                                converted++;
                            }
                        }
                    }
                }
            }
        }
        if (converted > 0) {
            logger.debug("Converted {} covered grass blocks to dirt", converted);
        }
    }

    /**
     * Final pass: enforce void columns by clearing any blocks, fluids, and
     * support data that may have been placed in void-marked columns by
     * first-pass layers, second-pass layers, custom objects, frost, or other
     * processing steps. This guarantees void areas are truly empty in the
     * exported chunk data.
     */
    private void enforceVoidColumns(Dimension dimension, Map<Long, HytaleChunk> chunksByCoords) {
        int cleared = 0;
        for (Map.Entry<Long, HytaleChunk> entry : chunksByCoords.entrySet()) {
            HytaleChunk chunk = entry.getValue();
            int hyChunkX = chunk.getxPos();
            int hyChunkZ = chunk.getzPos();

            // Convert to world block coords and then to tile coords
            int blockX = (hyChunkX << 5) - blockOffsetX;
            int blockZ = (hyChunkZ << 5) - blockOffsetZ;
            int tileX = blockX >> 7;
            int tileZ = blockZ >> 7;

            Tile tile = dimension.getTile(tileX, tileZ);
            if (tile == null) {
                continue;
            }

            int maxHeight = chunk.getMaxHeight();
            for (int localX = 0; localX < HytaleChunk.CHUNK_SIZE; localX++) {
                for (int localZ = 0; localZ < HytaleChunk.CHUNK_SIZE; localZ++) {
                    int worldX = blockX + localX;
                    int worldZ = blockZ + localZ;
                    int tileLocalX = worldX & 0x7F;
                    int tileLocalZ = worldZ & 0x7F;

                    if (!tile.getBitLayerValue(org.pepsoft.worldpainter.layers.Void.INSTANCE, tileLocalX, tileLocalZ)) {
                        continue;
                    }

                    // This column is void — clear everything
                    chunk.setHeight(localX, localZ, 0);
                    for (int y = 0; y < maxHeight; y++) {
                        HytaleBlock existing = chunk.getHytaleBlock(localX, y, localZ);
                        if (existing != null && !existing.isEmpty()) {
                            chunk.setHytaleBlock(localX, y, localZ, HytaleBlock.EMPTY);
                            cleared++;
                        }
                        HytaleChunk.HytaleSection section = chunk.getSections()[y >> 5];
                        int localY = y & 31;
                        if (section.getFluidId(localX, localY, localZ) > 0) {
                            section.clearFluid(localX, localY, localZ);
                            cleared++;
                        }
                    }
                }
            }
        }
        if (cleared > 0) {
            logger.info("Void enforcement pass cleared {} blocks/fluids", cleared);
        }
    }

    /**
     * Populate a Hytale chunk with terrain data from WorldPainter dimension.
     * Uses HytaleBlockMapping for proper block conversion and sets biomes.
     */
    private void populateChunkFromTile(HytaleChunk chunk, Dimension dimension, Tile tile, int worldBlockX, int worldBlockZ) {
        int waterLevel = tile.getWaterLevel(0, 0);
        Terrain terrain = tile.getTerrain(0, 0);
        long seed = dimension.getMinecraftSeed();

        // Collect specific prefab layers for this dimension
        List<HytaleSpecificPrefabLayer> specificPrefabLayers = new ArrayList<>();
        for (Layer layer : dimension.getAllLayers(false)) {
            if (layer instanceof HytaleSpecificPrefabLayer) {
                specificPrefabLayers.add((HytaleSpecificPrefabLayer) layer);
            }
        }
        
        // Track water placement for debugging (log first occurrence)
        boolean waterLogged = false;
        int minHeight = Integer.MAX_VALUE;
        int maxHeight = Integer.MIN_VALUE;
        int minWaterLevel = Integer.MAX_VALUE;
        int maxWaterLevel = Integer.MIN_VALUE;
        int waterColumns = 0;
        int specialFluidColumns = 0;
        Map<String, Integer> fluidTypeCounts = new HashMap<>();
        
        // Hytale chunk is 32x32 blocks
        for (int localX = 0; localX < HytaleChunk.CHUNK_SIZE; localX++) {
            for (int localZ = 0; localZ < HytaleChunk.CHUNK_SIZE; localZ++) {
                int worldX = worldBlockX + localX;
                int worldZ = worldBlockZ + localZ;
                
                // Get coordinates within the tile
                int tileLocalX = worldX & 0x7F; // % 128
                int tileLocalZ = worldZ & 0x7F;
                
                // Check if this column is marked as Void — skip all terrain generation
                if (tile.getBitLayerValue(org.pepsoft.worldpainter.layers.Void.INSTANCE, tileLocalX, tileLocalZ)) {
                    // Leave the column completely empty (no bedrock, no terrain, no fluids)
                    chunk.setHeight(localX, localZ, 0);
                    continue;
                }
                
                // Get terrain height and terrain type
                int height = tile.getIntHeight(tileLocalX, tileLocalZ);
                int localWaterLevel = tile.getWaterLevel(tileLocalX, tileLocalZ);
                if (height < minHeight) {
                    minHeight = height;
                }
                if (height > maxHeight) {
                    maxHeight = height;
                }
                if (localWaterLevel < minWaterLevel) {
                    minWaterLevel = localWaterLevel;
                }
                if (localWaterLevel > maxWaterLevel) {
                    maxWaterLevel = localWaterLevel;
                }
                Terrain localTerrain = tile.getTerrain(tileLocalX, tileLocalZ);
                // Check if this is a custom terrain (backed by MixedMaterial)
                boolean isCustomTerrain = localTerrain.isCustom();
                MixedMaterial customMaterial = isCustomTerrain
                    ? Terrain.getCustomMaterial(localTerrain.getCustomTerrainIndex())
                    : null;
                // Read per-pixel HytaleTerrain layer first, fall back to Terrain-based lookup
                int htIndex = HytaleTerrainLayer.getTerrainIndex(tile, tileLocalX, tileLocalZ);
                HytaleTerrain hytaleTerrain;
                if (htIndex > 0) {
                    hytaleTerrain = HytaleTerrain.getByLayerIndex(htIndex);
                } else {
                    hytaleTerrain = isCustomTerrain ? null : HytaleTerrainHelper.fromMinecraftTerrain(localTerrain);
                }

                // Resolve biome: check if user painted a biome via the Biome layer
                int paintedBiomeId = tile.getLayerValue(Biome.INSTANCE, tileLocalX, tileLocalZ);
                String biome;
                String environment;
                int tint;
                if (paintedBiomeId != HytaleBiome.BIOME_AUTO) {
                    // User explicitly painted a Hytale biome
                    HytaleBiome hb = HytaleBiome.getById(paintedBiomeId);
                    if (hb != null) {
                        biome = hb.getName();
                        environment = hb.getEnvironment();
                        tint = hb.getTint();
                    } else {
                        // Unknown biome ID, fall back to auto
                        biome = mapTerrainToBiome(localTerrain);
                        HytaleBiome fallback = HytaleBiome.fromTerrainBiomeName(biome);
                        environment = fallback.getEnvironment();
                        tint = fallback.getTint();
                    }
                } else {
                    // Auto biome: derive from terrain
                    String terrainBiomeName = mapTerrainToBiome(localTerrain);
                    HytaleBiome autoBiome = HytaleBiome.fromTerrainBiomeName(terrainBiomeName);
                    biome = autoBiome.getName();
                    environment = autoBiome.getEnvironment();
                    tint = autoBiome.getTint();
                }
                
                // Set biome, environment and tint
                chunk.setBiomeName(localX, localZ, biome);
                chunk.setEnvironment(localX, localZ, environment);
                chunk.setTint(localX, localZ, tint);
                
                // Bottom layer - bedrock (unless the dimension is bottomless)
                if (!dimension.isBottomless()) {
                    chunk.setHytaleBlock(localX, 0, localZ, HytaleBlock.BEDROCK);
                }
                
                if (isCustomTerrain && customMaterial != null) {
                    // Custom terrain: resolve blocks through MixedMaterial → Material → HytaleBlock.
                    // Surface-only blocks (vegetation, decorations) must only appear on top;
                    // subsurface is filled with dirt/stone just like the non-custom path.
                    HytaleBlock surfacePlant = null;
                    for (int y = 1; y <= height; y++) {
                        int depth = height - y;
                        Material mat = customMaterial.getMaterial(seed, worldX, worldZ, y);
                        HytaleBlock block = HytaleBlockMapping.toHytaleBlock(mat);
                        if (HytaleBlockRegistry.isSurfaceOnlyBlock(block.id)) {
                            // Remember the surface-only block for placement on top
                            if (depth == 0) {
                                surfacePlant = block;
                            }
                            // Subsurface gets dirt/stone; surface gets grass
                            block = (depth > 0)
                                    ? ((depth <= 4) ? HytaleBlock.DIRT : HytaleBlock.STONE)
                                    : HytaleBlock.GRASS;
                        } else if (block.isGrass() && depth > 0) {
                            // Grass only belongs on the surface
                            block = (depth <= 4) ? HytaleBlock.DIRT : HytaleBlock.STONE;
                        }
                        if (block.isFluid()) {
                            chunk.setHytaleBlock(localX, y, localZ, HytaleBlock.EMPTY);
                            chunk.getSections()[y >> 5].setFluid(localX, y & 31, localZ, block.id, 1);
                        } else {
                            chunk.setHytaleBlock(localX, y, localZ, block);
                        }
                    }
                    // Place the vegetation/decoration block on top of the terrain
                    if (surfacePlant != null) {
                        chunk.setHytaleBlock(localX, height + 1, localZ, surfacePlant);
                    }
                } else if (hytaleTerrain != null) {
                    HytaleBlock terrainBlock = hytaleTerrain.getPrimaryBlock();
                    boolean surfaceOnly = HytaleBlockRegistry.isSurfaceOnlyBlock(terrainBlock.id);
                    boolean grassTerrain = terrainBlock.isGrass();
                    for (int y = 1; y <= height; y++) {
                        int depth = height - y;
                        HytaleBlock block;
                        if (surfaceOnly) {
                            if (depth > 0) {
                                // Fill subsurface with dirt (or stone below depth 4)
                                block = (depth <= 4) ? HytaleBlock.DIRT : HytaleBlock.STONE;
                            } else {
                                // Surface: place grass; the plant goes on top at height+1
                                block = HytaleBlock.GRASS;
                            }
                        } else if (grassTerrain && depth > 0) {
                            // Grass blocks only belong on the surface; Hytale converts
                            // subsurface grass to dirt at runtime which hurts performance,
                            // so export dirt directly below the top grass block
                            block = (depth <= 4) ? HytaleBlock.DIRT : HytaleBlock.STONE;
                        } else {
                            block = hytaleTerrain.getBlock(seed, worldX, worldZ, depth);
                        }
                        if (block.isFluid()) {
                            chunk.setHytaleBlock(localX, y, localZ, HytaleBlock.EMPTY);
                            chunk.getSections()[y >> 5].setFluid(localX, y & 31, localZ, block.id, 1);
                        } else {
                            chunk.setHytaleBlock(localX, y, localZ, block);
                        }
                    }
                    // Place the vegetation/decoration block on top of the grass surface
                    if (surfaceOnly) {
                        HytaleBlock plantBlock = hytaleTerrain.getBlock(seed, worldX, worldZ, 0);
                        chunk.setHytaleBlock(localX, height + 1, localZ, plantBlock);
                    }
                }
                
                // ── Fluid Layer ──────────────────────────────────────
                // Check HytaleFluidLayer first, then fall back to FloodWithLava
                int fluidLayerValue = HytaleFluidLayer.normalizeFluidValue(
                    tile.getLayerValue(HytaleFluidLayer.INSTANCE, tileLocalX, tileLocalZ));
                boolean hasFluidOverride = fluidLayerValue > 0;
                boolean isLavaFluid = hasFluidOverride 
                    ? HytaleFluidLayer.isLava(fluidLayerValue)
                    : tile.getBitLayerValue(FloodWithLava.INSTANCE, tileLocalX, tileLocalZ);

                // Fill fluid (water/lava/poison/slime/tar) if below water level
                // Surface block is at height, so fluid starts at height+1
                // waterLevel is the TOP surface of fluid (inclusive)
                if (localWaterLevel > height) {
                    waterColumns++;
                    String fluidId;
                    if (hasFluidOverride) {
                        fluidId = HytaleFluidLayer.getFluidBlockId(fluidLayerValue);
                    } else if (isLavaFluid) {
                        fluidId = HytaleBlockMapping.HY_LAVA;
                    } else {
                        fluidId = HytaleBlockMapping.HY_WATER;
                    }
                    fluidTypeCounts.merge(fluidId, 1, Integer::sum);
                    for (int y = height + 1; y <= localWaterLevel; y++) {
                        chunk.setHytaleBlock(localX, y, localZ, HytaleBlock.EMPTY);
                        chunk.getSections()[y >> 5].setFluid(localX, y & 31, localZ,
                            fluidId, 1); // Source fluids: all have MaxFluidLevel=1 per Hytale assets
                    }
                }

                // ── Environment Layer ────────────────────────────────
                // Water tinting is now solely environment-driven
                int envLayerValue = tile.getLayerValue(HytaleEnvironmentLayer.INSTANCE, tileLocalX, tileLocalZ);
                if (envLayerValue != HytaleEnvironmentLayer.ENV_AUTO) {
                    HytaleEnvironmentData envData = HytaleEnvironmentData.getById(envLayerValue);
                    if (envData != null) {
                        environment = envData.getName();
                        chunk.setEnvironment(localX, localZ, environment);
                        if (envData.getWaterTint() != null) {
                            chunk.setWaterTint(localX, localZ, envData.getWaterTint());
                        }
                    }
                }

                // ── Entity Layer ─────────────────────────────────────
                int entityLayerValue = tile.getLayerValue(HytaleEntityLayer.INSTANCE, tileLocalX, tileLocalZ);
                if (entityLayerValue > 0) {
                    float spawnDensity = HytaleEntityLayer.getSpawnDensity(entityLayerValue);
                    chunk.setSpawnDensity(localX, localZ, spawnDensity);
                    if (entityLayerValue < HytaleEntityLayer.SPAWN_TAGS.length 
                            && HytaleEntityLayer.SPAWN_TAGS[entityLayerValue] != null) {
                        chunk.setSpawnTag(localX, localZ, HytaleEntityLayer.SPAWN_TAGS[entityLayerValue]);
                    }
                }

                // ── Prefab Layer ─────────────────────────────────────
                int prefabLayerValue = tile.getLayerValue(HytalePrefabLayer.INSTANCE, tileLocalX, tileLocalZ);
                if (prefabLayerValue > 0 && prefabLayerValue < HytalePrefabLayer.PREFAB_PATHS.length) {
                    String prefabPath = HytalePrefabLayer.PREFAB_PATHS[prefabLayerValue];
                    if (prefabPath != null) {
                        // Paste prefab blocks inline (Hytale reads blocks, not markers)
                        if (!prefabPaster.paste(chunk, localX, height + 1, localZ, worldX, worldZ, prefabPath)) {
                            // Fallback: keep marker for debugging if paste failed
                            chunk.addPrefabMarker(localX, height + 1, localZ,
                                HytalePrefabLayer.PREFAB_NAMES[prefabLayerValue], prefabPath);
                        }
                    }
                }

                // ── Specific Prefab Layers ───────────────────────────
                for (HytaleSpecificPrefabLayer spLayer : specificPrefabLayers) {
                    int gridX = spLayer.getGridX();
                    int gridZ = spLayer.getGridZ();
                    // Skip positions that don't fall on the grid
                    if (((worldX % gridX) != 0) || ((worldZ % gridZ) != 0)) {
                        continue;
                    }
                    int strength = tile.getLayerValue(spLayer, tileLocalX, tileLocalZ);
                    if (strength <= 0) {
                        continue;
                    }
                    // Probability-based placement matching Bo2LayerExporter approach
                    int densityFactor = spLayer.getDensity() * 64;
                    long placementSeed = seed + worldX * 65537L + worldZ * 4099L + (long) spLayer.getId().hashCode();
                    java.util.Random rng = new java.util.Random(placementSeed);
                    if (rng.nextInt(densityFactor) > strength * strength) {
                        continue;
                    }
                    // Apply random displacement
                    int placeX = worldX;
                    int placeZ = worldZ;
                    int placeLocalX = localX;
                    int placeLocalZ = localZ;
                    int displacement = spLayer.getRandomDisplacement();
                    if (displacement > 0) {
                        double angle = rng.nextDouble() * Math.PI * 2;
                        double distance = rng.nextDouble() * displacement;
                        placeX = worldX + (int) Math.round(Math.sin(angle) * distance);
                        placeZ = worldZ + (int) Math.round(Math.cos(angle) * distance);
                        // Recalculate local coordinates within this chunk
                        placeLocalX = placeX - worldBlockX;
                        placeLocalZ = placeZ - worldBlockZ;
                        // Skip if displaced outside this chunk
                        if (placeLocalX < 0 || placeLocalX >= HytaleChunk.CHUNK_SIZE
                                || placeLocalZ < 0 || placeLocalZ >= HytaleChunk.CHUNK_SIZE) {
                            continue;
                        }
                    }
                    PrefabFileEntry selected = spLayer.selectPrefab(placeX, placeZ);
                    int placeHeight = tile.getIntHeight(placeX & 0x7F, placeZ & 0x7F);
                    // Paste prefab blocks inline (Hytale reads blocks, not markers)
                    if (!prefabPaster.paste(chunk, placeLocalX, placeHeight + 1, placeLocalZ, placeX, placeZ, selected.getRelativePath())) {
                        // Fallback: keep marker for debugging if paste failed
                        chunk.addPrefabMarker(placeLocalX, placeHeight + 1, placeLocalZ,
                            selected.getDisplayName(), selected.getRelativePath());
                    }
                }
                
                // Update heightmap - WorldPainter height is the Y coordinate of the surface block
                // Hytale heightmap also stores Y coordinate of topmost solid block
                chunk.setHeight(localX, localZ, height);
            }
        }
        
        // Log summary for this chunk area
        if (waterColumns > 0 || specialFluidColumns > 0) {
            logger.warn("Chunk at ({}, {}): {} water-body columns, {} surface-fluid columns, height {}-{}, waterLevel {}-{}, fluids: {}",
                worldBlockX, worldBlockZ, waterColumns, specialFluidColumns, minHeight, maxHeight, minWaterLevel, maxWaterLevel, fluidTypeCounts);
        }
    }
    
    /**
     * Populate a chunk with ceiling terrain, inverted from the ceiling dimension.
     * Blocks hang downward from {@code ceilingHeight - 1} (bedrock lid) based on
     * the painted height in the ceiling tile. The gap between the surface and the
     * ceiling is left as {@link HytaleBlock#EMPTY} (void/air).
     */
    private void populateCeilingIntoChunk(HytaleChunk chunk, Dimension ceilingDimension, Tile ceilingTile,
            int worldBlockX, int worldBlockZ, int ceilingHeight, boolean bottomless) {
        long seed = ceilingDimension.getMinecraftSeed();
        int chunkMaxHeight = chunk.getMaxHeight();

        for (int localX = 0; localX < HytaleChunk.CHUNK_SIZE; localX++) {
            for (int localZ = 0; localZ < HytaleChunk.CHUNK_SIZE; localZ++) {
                int worldX = worldBlockX + localX;
                int worldZ = worldBlockZ + localZ;
                int tileLocalX = worldX & 0x7F;
                int tileLocalZ = worldZ & 0x7F;

                // Check if this column is marked as Void in the ceiling dimension — skip it
                if (ceilingTile.getBitLayerValue(org.pepsoft.worldpainter.layers.Void.INSTANCE, tileLocalX, tileLocalZ)) {
                    continue;
                }

                // Bedrock lid at the very top of the ceiling (unless bottomless)
                if (!bottomless) {
                    int topY = ceilingHeight - 1;
                    if (topY >= 0 && topY < chunkMaxHeight) {
                        chunk.setHytaleBlock(localX, topY, localZ, HytaleBlock.BEDROCK);
                    }
                }

                int hangDepth = ceilingTile.getIntHeight(tileLocalX, tileLocalZ);
                if (hangDepth <= 0) continue;

                Terrain localTerrain = ceilingTile.getTerrain(tileLocalX, tileLocalZ);
                boolean isCustomTerrain = localTerrain.isCustom();
                MixedMaterial customMaterial = isCustomTerrain
                    ? Terrain.getCustomMaterial(localTerrain.getCustomTerrainIndex())
                    : null;
                int htIndex = HytaleTerrainLayer.getTerrainIndex(ceilingTile, tileLocalX, tileLocalZ);
                HytaleTerrain hytaleTerrain;
                if (htIndex > 0) {
                    hytaleTerrain = HytaleTerrain.getByLayerIndex(htIndex);
                } else {
                    hytaleTerrain = isCustomTerrain ? null : HytaleTerrainHelper.fromMinecraftTerrain(localTerrain);
                }

                // depth 0 = bottom-most (visible) hanging block; increases toward the bedrock lid
                // worldY = ceilingHeight - 1 - hangDepth + depth
                for (int depth = 0; depth < hangDepth; depth++) {
                    int y = ceilingHeight - 1 - hangDepth + depth;
                    if (y < 0 || y >= chunkMaxHeight) continue;

                    HytaleBlock block;
                    if (isCustomTerrain && customMaterial != null) {
                        Material mat = customMaterial.getMaterial(seed, worldX, worldZ, depth);
                        block = HytaleBlockMapping.toHytaleBlock(mat);
                    } else if (hytaleTerrain != null) {
                        block = hytaleTerrain.getBlock(seed, worldX, worldZ, depth);
                    } else {
                        block = HytaleBlock.STONE;
                    }

                    if (!block.isEmpty() && !block.isFluid()) {
                        chunk.setHytaleBlock(localX, y, localZ, block);
                    }
                }
            }
        }
    }

    /**
     * Add entities to a chunk, including player spawn markers.
     * 
     * @param chunk The Hytale chunk to add entities to.
     * @param dimension The WorldPainter dimension being exported.
     * @param chunkX The chunk X coordinate.
     * @param chunkZ The chunk Z coordinate.
     */
    private void addEntitiesToChunk(HytaleChunk chunk, Dimension dimension, int chunkX, int chunkZ) {
        // Check if this chunk contains the world spawn point
        Point spawnPoint = world.getSpawnPoint();
        if (spawnPoint != null && dimension.getAnchor().equals(NORMAL_DETAIL)) {
            // Apply the centering offset to the spawn point
            int adjustedSpawnX = spawnPoint.x + blockOffsetX;
            int adjustedSpawnZ = spawnPoint.y + blockOffsetZ;
            
            // Calculate which chunk the spawn point falls in
            int spawnChunkX = adjustedSpawnX >> 5; // / 32
            int spawnChunkZ = adjustedSpawnZ >> 5;
            
            if (spawnChunkX == chunkX && spawnChunkZ == chunkZ) {
                // This chunk contains the spawn point - add player spawn marker
                // Calculate local position within the chunk
                int localX = adjustedSpawnX & 0x1F; // % 32
                int localZ = adjustedSpawnZ & 0x1F;
                
                // Get terrain height at spawn point
                int height = chunk.getHeight(localX, localZ);
                
                // Create spawn marker at terrain height + 1 (player stands on block)
                double y = height + 1.0;
                HytaleSpawnMarker spawnMarker = HytaleSpawnMarker.forPlayerSpawn(
                    adjustedSpawnX + 0.5, // Center on block
                    y,
                    adjustedSpawnZ + 0.5
                );
                
                chunk.addHytaleEntity(spawnMarker);
                logger.debug("Added player spawn marker at ({}, {}, {}) in chunk ({}, {})",
                    adjustedSpawnX + 0.5, y, adjustedSpawnZ + 0.5, chunkX, chunkZ);
            }
        }
    }

    private void applyCustomObjectLayers(Dimension dimension, Point regionCoords, Map<Long, HytaleChunk> chunksByCoords) {
        Set<Layer> layers = dimension.getAllLayers(false);
        if (layers.isEmpty()) {
            return;
        }

        int regionSize = HytaleChunk.CHUNK_SIZE * 32;
        Rectangle exportedArea = new Rectangle(
                (regionCoords.x << 10) - blockOffsetX,
                (regionCoords.y << 10) - blockOffsetZ,
                regionSize,
                regionSize);

        HytaleRegionMinecraftWorld regionWorld = new HytaleRegionMinecraftWorld(chunksByCoords, blockOffsetX, blockOffsetZ,
                dimension.getMinHeight(), dimension.getMaxHeight());

        for (Layer layer: layers) {
            if (! (layer instanceof Bo2Layer)) {
                continue;
            }
            Bo2Layer bo2Layer = (Bo2Layer) layer;
            regionWorld.setActiveBlockMappings(bo2Layer.getHytaleBlockMappings());
            regionWorld.setDecoratePlacedBlocks(bo2Layer.isNoPhysics());
            Bo2LayerExporter exporter = bo2Layer.getExporter(dimension, platform, dimension.getLayerSettings(bo2Layer));
            if (exporter == null) {
                regionWorld.setDecoratePlacedBlocks(false);
                continue;
            }
            try {
                List<Fixup> fixups = exporter.addFeatures(exportedArea, exportedArea, regionWorld);
                if ((fixups != null) && (! fixups.isEmpty())) {
                    logger.debug("Skipped {} border fixups for custom object layer '{}' in region {},{}",
                            fixups.size(), bo2Layer.getName(), regionCoords.x, regionCoords.y);
                }
            } catch (RuntimeException e) {
                logger.error("Error applying custom object layer '{}' in region {},{}",
                        bo2Layer.getName(), regionCoords.x, regionCoords.y, e);
            } finally {
                regionWorld.setDecoratePlacedBlocks(false);
            }
        }
    }

    private void applyFrostLayer(Dimension dimension, Point regionCoords, Map<Long, HytaleChunk> chunksByCoords) {
        final FrostExporter.FrostSettings frostSettings = (FrostExporter.FrostSettings) dimension.getLayerSettings(Frost.INSTANCE);
        final boolean frostPainted = dimension.getAllLayers(false).contains(Frost.INSTANCE);
        if ((! frostPainted) && ((frostSettings == null) || (! frostSettings.isApplyEverywhere()))) {
            return;
        }

        final int regionSize = HytaleChunk.CHUNK_SIZE * 32;
        final Rectangle exportedArea = new Rectangle(
                (regionCoords.x << 10) - blockOffsetX,
                (regionCoords.y << 10) - blockOffsetZ,
                regionSize,
                regionSize);

        final HytaleRegionMinecraftWorld regionWorld = new HytaleRegionMinecraftWorld(chunksByCoords, blockOffsetX, blockOffsetZ,
                dimension.getMinHeight(), dimension.getMaxHeight());
        final FrostExporter exporter = new FrostExporter(dimension, platform, frostSettings);
        try {
            exporter.addFeatures(exportedArea, exportedArea, regionWorld);
        } catch (RuntimeException e) {
            logger.error("Error applying frost layer in region {},{}", regionCoords.x, regionCoords.y, e);
        }
    }

    /**
     * Apply all second-pass layer exporters (caves, caverns, chasms, etc.) to the region.
     * Follows the same two-stage pattern as AbstractWorldExporter: CARVE first, then ADD_FEATURES.
     */
    private void applySecondPassLayers(Dimension dimension, Point regionCoords, Map<Long, HytaleChunk> chunksByCoords) {
        Set<Layer> layers = dimension.getAllLayers(false);
        if (layers.isEmpty()) {
            return;
        }

        // Collect second-pass layers (excluding Bo2Layer which is handled separately, and Frost which has its own method)
        List<Layer> secondPassLayers = new ArrayList<>();
        for (Layer layer : layers) {
            if (layer instanceof Bo2Layer || layer == Frost.INSTANCE) {
                continue;
            }
            Class<? extends LayerExporter> exporterType = layer.getExporterType();
            if (exporterType != null && SecondPassLayerExporter.class.isAssignableFrom(exporterType)) {
                secondPassLayers.add(layer);
            }
        }
        if (secondPassLayers.isEmpty()) {
            return;
        }
        Collections.sort(secondPassLayers);

        final int regionSize = HytaleChunk.CHUNK_SIZE * 32;
        final Rectangle exportedArea = new Rectangle(
                (regionCoords.x << 10) - blockOffsetX,
                (regionCoords.y << 10) - blockOffsetZ,
                regionSize,
                regionSize);

        final HytaleRegionMinecraftWorld regionWorld = new HytaleRegionMinecraftWorld(chunksByCoords, blockOffsetX, blockOffsetZ,
                dimension.getMinHeight(), dimension.getMaxHeight());

        // Instantiate all exporters
        Map<Layer, SecondPassLayerExporter> exporters = new LinkedHashMap<>();
        for (Layer layer : secondPassLayers) {
            LayerExporter exporter = layer.getExporter(dimension, platform, dimension.getLayerSettings(layer));
            if (exporter instanceof SecondPassLayerExporter) {
                exporters.put(layer, (SecondPassLayerExporter) exporter);
            }
        }

        // Stage 1: CARVE - remove blocks (caves, tunnels, etc.)
        for (Map.Entry<Layer, SecondPassLayerExporter> entry : exporters.entrySet()) {
            SecondPassLayerExporter exporter = entry.getValue();
            if (!exporter.getStages().contains(SecondPassLayerExporter.Stage.CARVE)) {
                continue;
            }
            try {
                List<Fixup> fixups = exporter.carve(exportedArea, exportedArea, regionWorld);
                if (fixups != null && !fixups.isEmpty()) {
                    logger.debug("Skipped {} border fixups for layer '{}' CARVE in region {},{}",
                            fixups.size(), entry.getKey().getName(), regionCoords.x, regionCoords.y);
                }
            } catch (RuntimeException e) {
                logger.error("Error carving layer '{}' in region {},{}", entry.getKey().getName(), regionCoords.x, regionCoords.y, e);
            }
        }

        // Stage 2: ADD_FEATURES - add decorations (stalactites, mushrooms, etc.)
        for (Map.Entry<Layer, SecondPassLayerExporter> entry : exporters.entrySet()) {
            SecondPassLayerExporter exporter = entry.getValue();
            if (!exporter.getStages().contains(SecondPassLayerExporter.Stage.ADD_FEATURES)) {
                continue;
            }
            try {
                List<Fixup> fixups = exporter.addFeatures(exportedArea, exportedArea, regionWorld);
                if (fixups != null && !fixups.isEmpty()) {
                    logger.debug("Skipped {} border fixups for layer '{}' ADD_FEATURES in region {},{}",
                            fixups.size(), entry.getKey().getName(), regionCoords.x, regionCoords.y);
                }
            } catch (RuntimeException e) {
                logger.error("Error adding features for layer '{}' in region {},{}", entry.getKey().getName(), regionCoords.x, regionCoords.y, e);
            }
        }
    }

    /**
     * Apply first-pass layer exporters (ground cover, resources) to the region.
     * These need a Chunk interface, which we provide via HytaleChunkView.
     */
    private void applyFirstPassLayers(Dimension dimension, Set<Point> tileCoords, Map<Long, HytaleChunk> chunksByCoords) {
        Set<Layer> layers = dimension.getAllLayers(false);
        if (layers.isEmpty()) {
            return;
        }

        // Collect first-pass layers
        List<Layer> firstPassLayers = new ArrayList<>();
        for (Layer layer : layers) {
            Class<? extends LayerExporter> exporterType = layer.getExporterType();
            if (exporterType != null && FirstPassLayerExporter.class.isAssignableFrom(exporterType)) {
                firstPassLayers.add(layer);
            }
        }
        if (firstPassLayers.isEmpty()) {
            return;
        }

        // Instantiate all exporters
        List<FirstPassLayerExporter> exporters = new ArrayList<>();
        for (Layer layer : firstPassLayers) {
            LayerExporter exporter = layer.getExporter(dimension, platform, dimension.getLayerSettings(layer));
            if (exporter instanceof FirstPassLayerExporter) {
                exporters.add((FirstPassLayerExporter) exporter);
            }
        }
        if (exporters.isEmpty()) {
            return;
        }

        // Apply each first-pass exporter to each chunk
        // First-pass exporters work per-tile, per-chunk via render(Tile, Chunk)
        for (Map.Entry<Long, HytaleChunk> entry : chunksByCoords.entrySet()) {
            HytaleChunk chunk = entry.getValue();
            int hyChunkX = chunk.getxPos();
            int hyChunkZ = chunk.getzPos();

            // Convert to world block coords and then to tile coords
            int blockX = (hyChunkX << 5) - blockOffsetX;
            int blockZ = (hyChunkZ << 5) - blockOffsetZ;
            int tileX = blockX >> 7;
            int tileZ = blockZ >> 7;

            if (!tileCoords.contains(new Point(tileX, tileZ))) {
                continue;
            }

            Tile tile = dimension.getTile(tileX, tileZ);
            if (tile == null) {
                continue;
            }

            // Each Hytale chunk (32x32) corresponds to 4 MC chunks (16x16)
            // Create MC-style chunk views for each quadrant
            for (int qx = 0; qx < 2; qx++) {
                for (int qz = 0; qz < 2; qz++) {
                    int mcChunkX = hyChunkX * 2 + qx;
                    int mcChunkZ = hyChunkZ * 2 + qz;
                    int xOffset = qx << 4;
                    int zOffset = qz << 4;
                    HytaleChunkView chunkView = new HytaleChunkView(chunk, mcChunkX, mcChunkZ, xOffset, zOffset);

                    for (FirstPassLayerExporter exporter : exporters) {
                        try {
                            exporter.render(tile, chunkView);
                        } catch (RuntimeException e) {
                            logger.error("Error applying first-pass layer in chunk ({}, {})", mcChunkX, mcChunkZ, e);
                        }
                    }
                }
            }
        }
    }

    private boolean hasSecondPassLayers(Dimension dimension) {
        Set<Layer> layers = dimension.getAllLayers(false);
        for (Layer layer : layers) {
            if (layer instanceof Bo2Layer || layer == Frost.INSTANCE) {
                continue;
            }
            Class<? extends LayerExporter> exporterType = layer.getExporterType();
            if (exporterType != null && SecondPassLayerExporter.class.isAssignableFrom(exporterType)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasFirstPassLayers(Dimension dimension) {
        Set<Layer> layers = dimension.getAllLayers(false);
        for (Layer layer : layers) {
            Class<? extends LayerExporter> exporterType = layer.getExporterType();
            if (exporterType != null && FirstPassLayerExporter.class.isAssignableFrom(exporterType)) {
                return true;
            }
        }
        return false;
    }

    private void calculateLighting(Point regionCoords, Map<Long, HytaleChunk> chunksByCoords, ProgressReceiver progressReceiver)
            throws ProgressReceiver.OperationCancelled {
        if (chunksByCoords.isEmpty()) {
            return;
        }
        if (! BlockPropertiesCalculator.isBlockPropertiesPassNeeded(platform, worldExportSettings, HYTALE_LIGHTING_SETTINGS)) {
            return;
        }
        if (progressReceiver != null) {
            progressReceiver.setMessage("Calculating Hytale lighting");
        }
        prepareLightingMaterialViews(chunksByCoords.values());
        HytaleRegionMinecraftWorld regionWorld = new HytaleRegionMinecraftWorld(chunksByCoords, blockOffsetX, blockOffsetZ,
                world.getMinHeight(), world.getMaxHeight());
        BlockPropertiesCalculator calculator = new BlockPropertiesCalculator(regionWorld, platform, worldExportSettings, HYTALE_LIGHTING_SETTINGS);
        int minBlockX = regionCoords.x << 10;
        int minBlockZ = regionCoords.y << 10;
        calculator.setDirtyArea(new Box(minBlockX, minBlockX + 1024, world.getMinHeight(), world.getMaxHeight(), minBlockZ, minBlockZ + 1024));
        calculator.firstPass();

        int maxIterations = 16;
        int iteration = 0;
        while (calculator.secondPass() && iteration < maxIterations) {
            iteration++;
            if (progressReceiver != null) {
                progressReceiver.setProgress(Math.min(1.0f, 0.35f + (0.5f * iteration / maxIterations)));
            }
        }
        calculator.finalise();
        for (HytaleChunk chunk : chunksByCoords.values()) {
            chunk.setLightPopulated(true);
        }
    }

    private void prepareLightingMaterialViews(Collection<HytaleChunk> chunks) {
        HytaleBlockRegistry registry = HytaleBlockRegistry.getInstance();
        for (HytaleChunk chunk : chunks) {
            for (HytaleChunk.HytaleSection section : chunk.getSections()) {
                section.resetMaterialView();
            }
            for (int y = chunk.getMinHeight(); y < chunk.getMaxHeight(); y++) {
                HytaleChunk.HytaleSection section = chunk.getSections()[y >> 5];
                int localY = y & 31;
                for (int z = 0; z < HytaleChunk.CHUNK_SIZE; z++) {
                    for (int x = 0; x < HytaleChunk.CHUNK_SIZE; x++) {
                        HytaleBlock block = chunk.getHytaleBlock(x, y, z);
                        String fluidName = null;
                        int fluidId = section.getFluidId(x, localY, z);
                        if (fluidId > 0 && fluidId < section.getFluidPalette().size()) {
                            fluidName = section.getFluidPalette().get(fluidId);
                        }
                        section.setMaterialForLighting(x, localY, z, getLightingMaterial(registry, block, fluidName));
                    }
                }
            }
        }
    }

    private Material getLightingMaterial(HytaleBlockRegistry registry, HytaleBlock block, String fluidName) {
        if (fluidName != null && !fluidName.equals("Empty")) {
            if (fluidName.contains("Lava")) {
                return Material.LAVA;
            }
            if (fluidName.contains("Water")) {
                return Material.WATER;
            }
            return Material.GLASS;
        }
        if (block == null || block.isEmpty()) {
            return Material.AIR;
        }
        HytaleBlockRegistry.BlockDefinition definition = registry.getBlock(block.id);
        int emission = registry.getLightEmission(block.id);
        String opacity = (definition != null && definition.opacity != null) ? definition.opacity : "Opaque";
        boolean opaque = "Opaque".equals(opacity);
        boolean semiTransparent = "SemiTransparent".equals(opacity);

        if (emission > 0) {
            if (opaque) {
                return (emission <= 3) ? Material.get("minecraft:magma_block") : Material.GLOWSTONE;
            }
            if (emission >= 14) {
                return Material.TORCH;
            }
            if (emission >= 12) {
                return seaPickleMaterial(3);
            }
            if (emission >= 9) {
                return seaPickleMaterial(2);
            }
            if (emission >= 6) {
                return Material.SEA_PICKLE_1;
            }
            return semiTransparent ? Material.LEAVES_OAK : Material.GLASS;
        }

        if (opaque) {
            return Material.STONE;
        }
        if (semiTransparent) {
            return Material.LEAVES_OAK;
        }
        return Material.GLASS;
    }

    private Material seaPickleMaterial(int pickleCount) {
        return Material.get(MC_SEA_PICKLE, MC_WATERLOGGED, true, MC_PICKLES, pickleCount);
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }

    private static final class HytaleRegionMinecraftWorld implements MinecraftWorld {
        private HytaleRegionMinecraftWorld(Map<Long, HytaleChunk> chunksByCoords, int blockOffsetX, int blockOffsetZ,
                                           int minHeight, int maxHeight) {
            this.chunksByCoords = chunksByCoords;
            this.blockOffsetX = blockOffsetX;
            this.blockOffsetZ = blockOffsetZ;
            this.minHeight = minHeight;
            this.maxHeight = maxHeight;
        }

        void setActiveBlockMappings(java.util.Map<String, String> mappings) {
            this.activeBlockMappings = mappings;
        }

        void setDecoratePlacedBlocks(boolean decoratePlacedBlocks) {
            this.decoratePlacedBlocks = decoratePlacedBlocks;
        }

        @Override
        public int getBlockTypeAt(int x, int y, int height) {
            return getMaterialAt(x, y, height).blockType;
        }

        @Override
        public int getDataAt(int x, int y, int height) {
            Material material = getMaterialAt(x, y, height);
            return (material.data >= 0) ? material.data : 0;
        }

        @Override
        public Material getMaterialAt(int x, int y, int height) {
            if ((height < minHeight) || (height >= maxHeight)) {
                return Material.AIR;
            }
            Location location = toLocation(x, y);
            if (location == null) {
                return Material.AIR;
            }
            HytaleBlock block = location.chunk.getHytaleBlock(location.localX, height, location.localZ);
            if ((block != null) && (! block.isEmpty())) {
                Material material = Material.get(HytaleBlockRegistry.HYTALE_NAMESPACE + ":" + block.id);
                if (block.rotation != 0) {
                    material = material.withProperty(HytalePrefabJsonObject.HYTALE_ROTATION_PROPERTY, Integer.toString(block.rotation & 0x3F));
                }
                return material;
            }

            HytaleChunk.HytaleSection section = location.chunk.getSections()[height >> 5];
            int localY = height & 31;
            int fluidId = section.getFluidId(location.localX, localY, location.localZ);
            if (fluidId > 0) {
                List<String> fluidPalette = section.getFluidPalette();
                if (fluidId < fluidPalette.size()) {
                    String fluidName = fluidPalette.get(fluidId);
                    if (fluidName.contains("Lava")) {
                        return Material.LAVA;
                    } else if (fluidName.contains("Water")) {
                        return Material.WATER;
                    } else if (! fluidName.equals("Empty")) {
                        return Material.get(HytaleBlockRegistry.HYTALE_NAMESPACE + ":" + fluidName);
                    }
                }
                return Material.WATER;
            }
            return Material.AIR;
        }

        @Override
        public void setBlockTypeAt(int x, int y, int height, int blockType) {
            setMaterialAt(x, y, height, Material.get(blockType));
        }

        @Override
        public void setDataAt(int x, int y, int height, int data) {
            Material existing = getMaterialAt(x, y, height);
            if (existing.blockType >= 0) {
                setMaterialAt(x, y, height, Material.get(existing.blockType, data));
            }
        }

        @Override
        public void setMaterialAt(int x, int y, int height, Material material) {
            if ((height < minHeight) || (height >= maxHeight)) {
                return;
            }
            Location location = toLocation(x, y);
            if (location == null) {
                return;
            }
            HytaleChunk.HytaleSection section = location.chunk.getSections()[height >> 5];
            int localY = height & 31;

            if ((material == null) || (material == Material.AIR)) {
                // Don't clear fluid when setting AIR — Hytale has no runtime water flow,
                // so fluid placed by the main pass must survive cave/layer carving.
                // If a cave carves through underwater terrain, the fluid should remain.
                location.chunk.setHytaleBlock(location.localX, height, location.localZ, HytaleBlock.EMPTY);
                return;
            }

            if (material.isNamed(MC_WATER)) {
                location.chunk.setHytaleBlock(location.localX, height, location.localZ, HytaleBlock.EMPTY);
                section.setFluid(location.localX, localY, location.localZ, HytaleBlockMapping.HY_WATER, 1);
                location.chunk.setDecorative(location.localX, height, location.localZ, false);
                return;
            } else if (material.isNamed(MC_LAVA)) {
                location.chunk.setHytaleBlock(location.localX, height, location.localZ, HytaleBlock.EMPTY);
                section.setFluid(location.localX, localY, location.localZ, HytaleBlockMapping.HY_LAVA, 1);
                location.chunk.setDecorative(location.localX, height, location.localZ, false);
                return;
            }

            HytaleBlock block = HytaleBlockMapping.toHytaleBlock(material, activeBlockMappings);
            if (block.isFluid()) {
                location.chunk.setHytaleBlock(location.localX, height, location.localZ, HytaleBlock.EMPTY);
                section.setFluid(location.localX, localY, location.localZ, block.id, 1);
                location.chunk.setDecorative(location.localX, height, location.localZ, false);
            } else {
                section.clearFluid(location.localX, localY, location.localZ);
                location.chunk.setHytaleBlock(location.localX, height, location.localZ, block);
                location.chunk.setDecorative(location.localX, height, location.localZ, decoratePlacedBlocks);
            }
        }

        @Override
        public int getMinHeight() {
            return minHeight;
        }

        @Override
        public int getMaxHeight() {
            return maxHeight;
        }

        @Override
        public void addEntity(double x, double y, double height, Entity entity) {
            Location location = toLocation((int) Math.floor(x), (int) Math.floor(y));
            if (location == null) {
                return;
            }
            String entityType = (entity != null) ? entity.getId() : "hytale:prefab_entity";
            HytaleEntity hytaleEntity = HytaleEntity.of(entityType, x + blockOffsetX, height, y + blockOffsetZ);
            if (entity != null) {
                float[] rotation = entity.getRot();
                hytaleEntity.setRotation(rotation[0], rotation[1], 0.0f);
            }
            location.chunk.addHytaleEntity(hytaleEntity);
        }

        @Override
        public void addTileEntity(int x, int y, int height, TileEntity tileEntity) {
            // Hytale has no direct equivalent for Minecraft tile entities in this exporter path.
        }

        @Override
        public int getBlockLightLevel(int x, int y, int height) {
            Location location = toLocation(x, y);
            return (location != null) ? location.chunk.getBlockLightLevel(location.localX, height, location.localZ) : 0;
        }

        @Override
        public void setBlockLightLevel(int x, int y, int height, int blockLightLevel) {
            if ((height < minHeight) || (height >= maxHeight)) {
                return;
            }
            Location location = toLocation(x, y);
            if (location != null) {
                location.chunk.setBlockLightLevel(location.localX, height, location.localZ, blockLightLevel);
            }
        }

        @Override
        public int getSkyLightLevel(int x, int y, int height) {
            Location location = toLocation(x, y);
            return (location != null) ? location.chunk.getSkyLightLevel(location.localX, height, location.localZ) : 15;
        }

        @Override
        public void setSkyLightLevel(int x, int y, int height, int skyLightLevel) {
            if ((height < minHeight) || (height >= maxHeight)) {
                return;
            }
            Location location = toLocation(x, y);
            if (location != null) {
                location.chunk.setSkyLightLevel(location.localX, height, location.localZ, skyLightLevel);
            }
        }

        @Override
        public boolean isChunkPresent(int x, int y) {
            int centeredBlockX = x << 4;
            int centeredBlockZ = y << 4;
            int hChunkX = Math.floorDiv(centeredBlockX, HytaleChunk.CHUNK_SIZE);
            int hChunkZ = Math.floorDiv(centeredBlockZ, HytaleChunk.CHUNK_SIZE);
            return chunksByCoords.containsKey(chunkKey(hChunkX, hChunkZ));
        }

        @Override
        public void addChunk(Chunk chunk) {
            // Not needed for this in-memory region view.
        }

        @Override
        public int getHighestNonAirBlock(int x, int y) {
            for (int z = maxHeight - 1; z >= minHeight; z--) {
                if (getMaterialAt(x, y, z) != Material.AIR) {
                    return z;
                }
            }
            return Integer.MIN_VALUE;
        }

        @Override
        public Chunk getChunk(int x, int z) {
            return chunkViews.computeIfAbsent(chunkKey(x, z), key -> {
                int hChunkX = Math.floorDiv(x, 2);
                int hChunkZ = Math.floorDiv(z, 2);
                HytaleChunk chunk = chunksByCoords.get(chunkKey(hChunkX, hChunkZ));
                if (chunk == null) {
                    return null;
                }
                int xOffset = Math.floorMod(x, 2) << 4;
                int zOffset = Math.floorMod(z, 2) << 4;
                return new HytaleChunkView(chunk, x, z, xOffset, zOffset);
            });
        }

        @Override
        public Chunk getChunkForEditing(int x, int z) {
            return getChunk(x, z);
        }

        @Override
        public void close() {
            // No resources to close.
        }

        private Location toLocation(int originalX, int originalZ) {
            int centeredX = originalX + blockOffsetX;
            int centeredZ = originalZ + blockOffsetZ;
            int hChunkX = Math.floorDiv(centeredX, HytaleChunk.CHUNK_SIZE);
            int hChunkZ = Math.floorDiv(centeredZ, HytaleChunk.CHUNK_SIZE);
            HytaleChunk chunk = chunksByCoords.get(chunkKey(hChunkX, hChunkZ));
            if (chunk == null) {
                return null;
            }
            int localX = Math.floorMod(centeredX, HytaleChunk.CHUNK_SIZE);
            int localZ = Math.floorMod(centeredZ, HytaleChunk.CHUNK_SIZE);
            return new Location(chunk, localX, localZ);
        }

        private final Map<Long, HytaleChunk> chunksByCoords;
        private final Map<Long, Chunk> chunkViews = new HashMap<>();
        private final int blockOffsetX, blockOffsetZ;
        private final int minHeight, maxHeight;
        private java.util.Map<String, String> activeBlockMappings;
        private boolean decoratePlacedBlocks;

        private static final class Location {
            private Location(HytaleChunk chunk, int localX, int localZ) {
                this.chunk = chunk;
                this.localX = localX;
                this.localZ = localZ;
            }

            private final HytaleChunk chunk;
            private final int localX, localZ;
        }
    }

    private static final class HytaleChunkView implements Chunk {
        private HytaleChunkView(HytaleChunk delegate, int mcChunkX, int mcChunkZ, int xOffset, int zOffset) {
            this.delegate = delegate;
            this.mcChunkX = mcChunkX;
            this.mcChunkZ = mcChunkZ;
            this.xOffset = xOffset;
            this.zOffset = zOffset;
        }

        @Override
        public int getBlockLightLevel(int x, int y, int z) {
            return delegate.getBlockLightLevel(x + xOffset, y, z + zOffset);
        }

        @Override
        public void setBlockLightLevel(int x, int y, int z, int blockLightLevel) {
            delegate.setBlockLightLevel(x + xOffset, y, z + zOffset, blockLightLevel);
        }

        @Override
        @Deprecated
        public int getBlockType(int x, int y, int z) {
            return delegate.getBlockType(x + xOffset, y, z + zOffset);
        }

        @Override
        @Deprecated
        public void setBlockType(int x, int y, int z, int blockType) {
            delegate.setBlockType(x + xOffset, y, z + zOffset, blockType);
        }

        @Override
        @Deprecated
        public int getDataValue(int x, int y, int z) {
            return delegate.getDataValue(x + xOffset, y, z + zOffset);
        }

        @Override
        @Deprecated
        public void setDataValue(int x, int y, int z, int dataValue) {
            delegate.setDataValue(x + xOffset, y, z + zOffset, dataValue);
        }

        @Override
        public int getHeight(int x, int z) {
            return delegate.getHeight(x + xOffset, z + zOffset);
        }

        @Override
        public void setHeight(int x, int z, int height) {
            delegate.setHeight(x + xOffset, z + zOffset, height);
        }

        @Override
        public int getSkyLightLevel(int x, int y, int z) {
            return delegate.getSkyLightLevel(x + xOffset, y, z + zOffset);
        }

        @Override
        public void setSkyLightLevel(int x, int y, int z, int skyLightLevel) {
            delegate.setSkyLightLevel(x + xOffset, y, z + zOffset, skyLightLevel);
        }

        @Override
        public int getxPos() {
            return mcChunkX;
        }

        @Override
        public int getzPos() {
            return mcChunkZ;
        }

        @Override
        public MinecraftCoords getCoords() {
            return new MinecraftCoords(mcChunkX, mcChunkZ);
        }

        @Override
        public boolean isTerrainPopulated() {
            return delegate.isTerrainPopulated();
        }

        @Override
        public void setTerrainPopulated(boolean terrainPopulated) {
            delegate.setTerrainPopulated(terrainPopulated);
        }

        @Override
        public Material getMaterial(int x, int y, int z) {
            return delegate.getMaterial(x + xOffset, y, z + zOffset);
        }

        @Override
        public void setMaterial(int x, int y, int z, Material material) {
            int dx = x + xOffset;
            int dz = z + zOffset;
            if (y < delegate.getMinHeight() || y >= delegate.getMaxHeight()) {
                return;
            }
            if ((material == null) || (material == Material.AIR)) {
                delegate.setHytaleBlock(dx, y, dz, HytaleBlock.EMPTY);
                // Don't clear fluid when setting AIR — Hytale has no runtime water flow,
                // so fluid placed by the main pass must survive cave/layer carving.
                // If a cave carves through underwater terrain, the fluid should remain.
                return;
            }
            HytaleBlock block = HytaleBlockMapping.toHytaleBlock(material);
            if (block.isFluid()) {
                delegate.setHytaleBlock(dx, y, dz, HytaleBlock.EMPTY);
                delegate.getSections()[y >> 5].setFluid(dx, y & 31, dz, block.id, 1);
            } else if (material.isNamed(MC_WATER)) {
                delegate.setHytaleBlock(dx, y, dz, HytaleBlock.EMPTY);
                delegate.getSections()[y >> 5].setFluid(dx, y & 31, dz, HytaleBlockMapping.HY_WATER, 1);
            } else if (material.isNamed(MC_LAVA)) {
                delegate.setHytaleBlock(dx, y, dz, HytaleBlock.EMPTY);
                delegate.getSections()[y >> 5].setFluid(dx, y & 31, dz, HytaleBlockMapping.HY_LAVA, 1);
            } else {
                delegate.getSections()[y >> 5].clearFluid(dx, y & 31, dz);
                delegate.setHytaleBlock(dx, y, dz, block);
            }
        }

        @Override
        public List<Entity> getEntities() {
            return delegate.getEntities();
        }

        @Override
        public List<TileEntity> getTileEntities() {
            return delegate.getTileEntities();
        }

        @Override
        public int getMinHeight() {
            return delegate.getMinHeight();
        }

        @Override
        public int getMaxHeight() {
            return delegate.getMaxHeight();
        }

        @Override
        public boolean isReadOnly() {
            return delegate.isReadOnly();
        }

        @Override
        public boolean isLightPopulated() {
            return delegate.isLightPopulated();
        }

        @Override
        public void setLightPopulated(boolean lightPopulated) {
            delegate.setLightPopulated(lightPopulated);
        }

        @Override
        public long getInhabitedTime() {
            return delegate.getInhabitedTime();
        }

        @Override
        public void setInhabitedTime(long inhabitedTime) {
            delegate.setInhabitedTime(inhabitedTime);
        }

        @Override
        public int getHighestNonAirBlock(int x, int z) {
            return delegate.getHighestNonAirBlock(x + xOffset, z + zOffset);
        }

        @Override
        public int getHighestNonAirBlock() {
            int highest = Integer.MIN_VALUE;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    highest = Math.max(highest, getHighestNonAirBlock(x, z));
                }
            }
            return highest;
        }

        private final HytaleChunk delegate;
        private final int mcChunkX, mcChunkZ;
        private final int xOffset, zOffset;
    }
    
    /**
     * Map WorldPainter terrain type to Hytale biome name.
     */
    private String mapTerrainToBiome(Terrain terrain) {
        if (terrain == null) {
            return "Grassland";
        }
        
        String name = terrain.getName().toLowerCase();
        
        // Desert terrains (but not "soul sand" which is a Nether block)
        if ((name.contains("sand") || name.contains("desert") || name.contains("red sand"))
                && !name.contains("soul")) {
            return "Desert";
        }
        // Snow/ice terrains
        if (name.contains("snow") || name.contains("ice") || name.contains("frozen") || name.contains("tundra")) {
            return "Tundra";
        }
        // Tropical terrains
        if (name.contains("jungle") || name.contains("tropical") || name.contains("swamp")) {
            return "Tropical";
        }
        // Forest terrains
        if (name.contains("forest") || name.contains("taiga") || name.contains("birch") || name.contains("dark oak")) {
            return "Forest";
        }
        // Ocean terrains (deep ocean only — NOT beaches or rivers, which are
        // land-adjacent and should keep a green vegetation tint)
        if (name.contains("ocean")) {
            return "Ocean";
        }
        // Mountain terrains
        if (name.contains("mountain") || name.contains("extreme") || name.contains("peak")) {
            return "Mountain";
        }
        // Underground
        if (name.contains("deep") || name.contains("cave")) {
            return "Underground";
        }
        
        // Beaches, rivers, and everything else default to grassland so that
        // vegetation near sand/water edges keeps a natural green tint.
        return "Grassland";
    }
    
    /**
     * Get the surface material for a terrain type.
     */
    private Material getSurfaceMaterial(Terrain terrain) {
        if (terrain == null) {
            return Material.GRASS_BLOCK;
        }
        
        String name = terrain.getName().toLowerCase();
        
        if (name.contains("sand") || name.contains("desert")) {
            return name.contains("red") ? Material.RED_SAND : Material.SAND;
        }
        if (name.contains("snow")) {
            return Material.SNOW_BLOCK;
        }
        if (name.contains("stone") || name.contains("rock")) {
            return Material.STONE;
        }
        if (name.contains("gravel")) {
            return Material.GRAVEL;
        }
        if (name.contains("clay")) {
            return Material.CLAY;
        }
        if (name.contains("dirt")) {
            return Material.DIRT;
        }
        
        return Material.GRASS_BLOCK;
    }
    
    /**
     * Get the subsurface material for a terrain type.
     */
    private Material getSubsurfaceMaterial(Terrain terrain) {
        if (terrain == null) {
            return Material.DIRT;
        }
        
        String name = terrain.getName().toLowerCase();
        
        if (name.contains("sand") || name.contains("desert")) {
            return name.contains("red") ? Material.RED_SAND : Material.SAND;
        }
        if (name.contains("snow")) {
            return Material.DIRT;
        }
        if (name.contains("stone") || name.contains("rock")) {
            return Material.STONE;
        }
        if (name.contains("gravel")) {
            return Material.GRAVEL;
        }
        
        return Material.DIRT;
    }
    
    private ExecutorService createExecutorService(String operation, int jobCount) {
        return MDCThreadPoolExecutor.newFixedThreadPool(chooseThreadCountForExport(operation, jobCount), new ThreadFactory() {
            @Override
            public synchronized Thread newThread(Runnable r) {
                Thread thread = new Thread(threadGroup, r, operation.toLowerCase().replaceAll("\\s+", "-") + "-" + nextID++);
                thread.setPriority(Thread.MIN_PRIORITY);
                return thread;
            }

            private final ThreadGroup threadGroup = new ThreadGroup(operation);
            private int nextID = 1;
        });
    }
    
    private static final String EVENT_KEY_ACTION_EXPORT_WORLD = "action.exportWorld";
    private static final AttributeKeyVO<Integer> ATTRIBUTE_KEY_MAX_HEIGHT = new AttributeKeyVO<>("maxHeight");
    private static final AttributeKeyVO<String> ATTRIBUTE_KEY_PLATFORM = new AttributeKeyVO<>("platform");
    private static final AttributeKeyVO<String> ATTRIBUTE_KEY_PLATFORM_ID = new AttributeKeyVO<>("platformId");
}
