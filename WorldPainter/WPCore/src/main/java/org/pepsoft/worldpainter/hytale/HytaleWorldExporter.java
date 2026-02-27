package org.pepsoft.worldpainter.hytale;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.pepsoft.minecraft.Chunk;
import org.pepsoft.minecraft.ChunkFactory;
import org.pepsoft.minecraft.Entity;
import org.pepsoft.minecraft.Material;
import org.pepsoft.minecraft.TileEntity;
import org.pepsoft.util.FileUtils;
import org.pepsoft.util.ParallelProgressManager;
import org.pepsoft.util.ProgressReceiver;
import org.pepsoft.util.SubProgressReceiver;
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
import org.pepsoft.worldpainter.layers.FloodWithLava;
import org.pepsoft.worldpainter.layers.bo2.Bo2LayerExporter;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.pepsoft.util.ExceptionUtils.chainContains;
import static org.pepsoft.util.mdc.MDCUtils.doWithMdcContext;
import static org.pepsoft.minecraft.Constants.MC_LAVA;
import static org.pepsoft.minecraft.Constants.MC_WATER;
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
    
    // Offset to center terrain at world origin (computed during export)
    private int blockOffsetX = 0;
    private int blockOffsetZ = 0;
    
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
        File baseDir = worldDir.getParentFile();
        File backupsDir = new File(baseDir, "backups");
        if ((!backupsDir.isDirectory()) && (!backupsDir.mkdirs())) {
            backupsDir = new File(System.getProperty("user.home"), "WorldPainter Backups");
            if ((!backupsDir.isDirectory()) && (!backupsDir.mkdirs())) {
                throw new IOException("Could not create " + backupsDir);
            }
        }
        String timestamp = new java.text.SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        return new File(backupsDir, worldDir.getName() + "." + timestamp);
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
            
            // Create world directory
            File worldDir = new File(baseDir, FileUtils.sanitiseName(name));
            logger.info("Exporting world {} to Hytale map at {}", world.getName(), worldDir);
            
            if (worldDir.isDirectory()) {
                if (backupDir != null) {
                    logger.info("Directory already exists; backing up to {}", backupDir);
                    if (!worldDir.renameTo(backupDir)) {
                        throw new FileInUseException("Could not move " + worldDir + " to " + backupDir);
                    }
                } else {
                    throw new IllegalStateException("Directory already exists and no backup directory specified");
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
            
            File effectiveWorldDir;
            Path tempDir = null;
            if (useTempDir) {
                tempDir = Files.createTempDirectory(baseDir.toPath(), "wp-hytale-export-");
                effectiveWorldDir = tempDir.resolve(FileUtils.sanitiseName(name)).toFile();
            } else {
                effectiveWorldDir = worldDir;
            }
            
            try {
                // Create directory structure
                if (!effectiveWorldDir.mkdirs()) {
                    throw new IOException("Could not create directory: " + effectiveWorldDir);
                }
                
                File chunksDir = new File(effectiveWorldDir, "chunks");
                if (!chunksDir.mkdirs()) {
                    throw new IOException("Could not create chunks directory");
                }
                
                File resourcesDir = new File(effectiveWorldDir, "resources");
                if (!resourcesDir.mkdirs()) {
                    throw new IOException("Could not create resources directory");
                }
                
                // Export dimensions (must come before writeWorldConfig so blockOffsetX/Z are set)
                Map<Integer, ChunkFactory.Stats> stats = new HashMap<>();
                Dimension dim0 = world.getDimension(NORMAL_DETAIL);
                if (dim0 != null) {
                    if (progressReceiver != null) {
                        progressReceiver.setMessage("Exporting Overworld to Hytale format");
                    }
                    stats.put(DIM_NORMAL, exportDimension(effectiveWorldDir, dim0, selectedTiles, progressReceiver));
                }
                
                // Write config.json (after exportDimension so blockOffsetX/Z are set for SpawnProvider)
                writeWorldConfig(effectiveWorldDir);
                
                // Write resource files
                writeResourceFiles(effectiveWorldDir);
                
                // If we used a temp directory, move the result to the target location
                if (useTempDir) {
                    if (progressReceiver != null) {
                        progressReceiver.setMessage("Moving exported world to target directory...");
                    }
                    logger.info("Moving exported world from {} to {}", effectiveWorldDir, worldDir);
                    // Delete any existing target directory first so the move can succeed
                    if (worldDir.exists()) {
                        deleteRecursive(worldDir.toPath());
                    }
                    // Both paths are on the same drive, so this is a fast rename (no data copy)
                    Files.move(effectiveWorldDir.toPath(), worldDir.toPath());
                }
                
                // Record the export in the world history
                world.addHistoryEntry(HistoryEntry.WORLD_EXPORTED_FULL, name, worldDir);
                
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
    private void writeWorldConfig(File worldDir) throws IOException {
        Dimension dim0 = world.getDimension(NORMAL_DETAIL);
        
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("Version", 4);
        
        // Generate UUID
        UUID uuid = UUID.randomUUID();
        Map<String, String> uuidMap = new LinkedHashMap<>();
        uuidMap.put("$binary", Base64.getEncoder().encodeToString(uuidToBytes(uuid)));
        uuidMap.put("$type", "04");
        config.put("UUID", uuidMap);
        
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
            logger.info("Hytale custom object layers present: {}", hasCustomObjects);

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
            final int maxByContent = hasCustomObjects ? 1 : configuredMaxConcurrentRegions;
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
                            exportRegion(chunksDir, dimension, ceilingDimension, region, collectedStats, regionProgress, hasCustomObjects);
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
        terrainData.put("format", "WorldPainter Hytale Terrain Export");
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
        private void exportRegion(File chunksDir, Dimension dimension, Dimension ceilingDimension, Point regionCoords,
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
            Map<Long, HytaleChunk> chunksByCoords = retainChunksForCustomObjects ? new HashMap<>() : null;
            
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
                                originalBlockX, originalBlockZ, dimension.getCeilingHeight());
                        }
                    }

                    // Add entities (spawn markers, etc.)
                    addEntitiesToChunk(chunk, dimension, hyChunkX, hyChunkZ);
                    if (retainChunksForCustomObjects) {
                        chunksByCoords.put(chunkKey(hyChunkX, hyChunkZ), chunk);
                    } else {
                        // Streaming path: write chunk immediately to keep memory footprint low.
                        regionFile.writeChunk(localX, localZ, chunk);
                        synchronized (stats) {
                            stats.surfaceArea += HytaleChunk.CHUNK_SIZE * HytaleChunk.CHUNK_SIZE;
                        }
                    }
                    
                    chunksExported++;
                    if (progressReceiver != null && chunksExported % 32 == 0) {
                        progressReceiver.setProgress((float) chunksExported / totalChunks);
                    }
                }
            }

            if (retainChunksForCustomObjects) {
                // Apply custom object layers after terrain generation so placement/collision checks can use the final surface.
                applyCustomObjectLayers(dimension, regionCoords, chunksByCoords);

                // Write retained chunks to disk.
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
            }
            
            regionFile.flush();
        }
        
        logger.debug("Exported region {},{} to {}", regionCoords.x, regionCoords.y, regionPath);
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
     * Populate a Hytale chunk with terrain data from WorldPainter dimension.
     * Uses HytaleBlockMapping for proper block conversion and sets biomes.
     */
    private void populateChunkFromTile(HytaleChunk chunk, Dimension dimension, Tile tile, int worldBlockX, int worldBlockZ) {
        int waterLevel = tile.getWaterLevel(0, 0);
        Terrain terrain = tile.getTerrain(0, 0);
        long seed = dimension.getMinecraftSeed();
        
        // Track water placement for debugging (log first occurrence)
        boolean waterLogged = false;
        int minHeight = Integer.MAX_VALUE;
        int maxHeight = Integer.MIN_VALUE;
        int minWaterLevel = Integer.MAX_VALUE;
        int maxWaterLevel = Integer.MIN_VALUE;
        int waterColumns = 0;
        
        // Hytale chunk is 32x32 blocks
        for (int localX = 0; localX < HytaleChunk.CHUNK_SIZE; localX++) {
            for (int localZ = 0; localZ < HytaleChunk.CHUNK_SIZE; localZ++) {
                int worldX = worldBlockX + localX;
                int worldZ = worldBlockZ + localZ;
                
                // Get coordinates within the tile
                int tileLocalX = worldX & 0x7F; // % 128
                int tileLocalZ = worldZ & 0x7F;
                
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
                
                // Bottom layer - bedrock
                chunk.setHytaleBlock(localX, 0, localZ, HytaleBlock.BEDROCK);
                
                if (isCustomTerrain && customMaterial != null) {
                    // Custom terrain: resolve blocks through MixedMaterial → Material → HytaleBlock
                    for (int y = 1; y <= height; y++) {
                        Material mat = customMaterial.getMaterial(seed, worldX, worldZ, y);
                        HytaleBlock block = HytaleBlockMapping.toHytaleBlock(mat);
                        if (block.isFluid()) {
                            chunk.setHytaleBlock(localX, y, localZ, HytaleBlock.EMPTY);
                            chunk.getSections()[y >> 5].setFluid(localX, y & 31, localZ, block.id, 1);
                        } else {
                            chunk.setHytaleBlock(localX, y, localZ, block);
                        }
                    }
                } else if (hytaleTerrain != null) {
                    for (int y = 1; y <= height; y++) {
                        int depth = height - y;
                        HytaleBlock block = hytaleTerrain.getBlock(seed, worldX, worldZ, depth);
                        if (block.isFluid()) {
                            chunk.setHytaleBlock(localX, y, localZ, HytaleBlock.EMPTY);
                            chunk.getSections()[y >> 5].setFluid(localX, y & 31, localZ, block.id, 1);
                        } else {
                            chunk.setHytaleBlock(localX, y, localZ, block);
                        }
                    }
                }
                
                // ── Fluid Layer ──────────────────────────────────────
                // Check HytaleFluidLayer first, then fall back to FloodWithLava
                int fluidLayerValue = tile.getLayerValue(HytaleFluidLayer.INSTANCE, tileLocalX, tileLocalZ);
                boolean hasFluidOverride = fluidLayerValue > 0;
                boolean isLavaFluid = hasFluidOverride 
                    ? HytaleFluidLayer.isLava(fluidLayerValue)
                    : tile.getBitLayerValue(FloodWithLava.INSTANCE, tileLocalX, tileLocalZ);
                
                // Apply water tint from fluid layer if present
                if (hasFluidOverride) {
                    String waterTint = HytaleFluidLayer.getWaterTint(fluidLayerValue);
                    if (waterTint != null) {
                        chunk.setWaterTint(localX, localZ, waterTint);
                    }
                }

                // Fill water or lava if below water level
                // Surface block is at height, so water starts at height+1
                // waterLevel is the TOP surface of water (inclusive)
                if (localWaterLevel > height) {
                    waterColumns++;
                    String fluidId = isLavaFluid ? HytaleBlockMapping.HY_LAVA : HytaleBlockMapping.HY_WATER;
                    // Debug logging for first fluid placement in this chunk
                    if (!waterLogged) {
                        logger.info("{} at world ({}, {}) chunk block ({}, {}) - Terrain Y: {}, Fluid Y: {}, Placing Y: {} to {}",
                            isLavaFluid ? "Lava" : "Water", worldX, worldZ, localX, localZ, height, localWaterLevel, height + 1, localWaterLevel);
                        waterLogged = true;
                    }
                    for (int y = height + 1; y <= localWaterLevel; y++) {
                        chunk.setHytaleBlock(localX, y, localZ, HytaleBlock.EMPTY);
                        chunk.getSections()[y >> 5].setFluid(localX, y & 31, localZ, 
                            fluidId, 1); // Source fluids use max level (1 for water/lava)
                    }
                }

                // ── Environment Layer ────────────────────────────────
                int envLayerValue = tile.getLayerValue(HytaleEnvironmentLayer.INSTANCE, tileLocalX, tileLocalZ);
                if (envLayerValue != HytaleEnvironmentLayer.ENV_AUTO) {
                    HytaleEnvironmentData envData = HytaleEnvironmentData.getById(envLayerValue);
                    if (envData != null) {
                        environment = envData.getName();
                        chunk.setEnvironment(localX, localZ, environment);
                        // Apply water tint from environment if no fluid layer override
                        if (!hasFluidOverride && envData.getWaterTint() != null) {
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
                        chunk.addPrefabMarker(localX, height + 1, localZ,
                            HytalePrefabLayer.PREFAB_NAMES[prefabLayerValue], prefabPath);
                    }
                }
                
                // Update heightmap - WorldPainter height is the Y coordinate of the surface block
                // Hytale heightmap also stores Y coordinate of topmost solid block
                chunk.setHeight(localX, localZ, height);
            }
        }
        
        // Log summary for this chunk area
        logger.info("Chunk area at world ({}, {}) - height min/max: {}/{} waterLevel min/max: {}/{} water columns: {}",
            worldBlockX, worldBlockZ, minHeight, maxHeight, minWaterLevel, maxWaterLevel, waterColumns);
    }
    
    /**
     * Populate a chunk with ceiling terrain, inverted from the ceiling dimension.
     * Blocks hang downward from {@code ceilingHeight - 1} (bedrock lid) based on
     * the painted height in the ceiling tile. The gap between the surface and the
     * ceiling is left as {@link HytaleBlock#EMPTY} (void/air).
     */
    private void populateCeilingIntoChunk(HytaleChunk chunk, Dimension ceilingDimension, Tile ceilingTile,
            int worldBlockX, int worldBlockZ, int ceilingHeight) {
        long seed = ceilingDimension.getMinecraftSeed();
        int chunkMaxHeight = chunk.getMaxHeight();

        for (int localX = 0; localX < HytaleChunk.CHUNK_SIZE; localX++) {
            for (int localZ = 0; localZ < HytaleChunk.CHUNK_SIZE; localZ++) {
                int worldX = worldBlockX + localX;
                int worldZ = worldBlockZ + localZ;
                int tileLocalX = worldX & 0x7F;
                int tileLocalZ = worldZ & 0x7F;

                // Bedrock lid at the very top of the ceiling
                int topY = ceilingHeight - 1;
                if (topY >= 0 && topY < chunkMaxHeight) {
                    chunk.setHytaleBlock(localX, topY, localZ, HytaleBlock.BEDROCK);
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
                logger.info("Added player spawn marker at ({}, {}, {}) in chunk ({}, {})",
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
            Bo2LayerExporter exporter = bo2Layer.getExporter(dimension, platform, dimension.getLayerSettings(bo2Layer));
            if (exporter == null) {
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
            }
        }
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
                section.clearFluid(location.localX, localY, location.localZ);
                location.chunk.setHytaleBlock(location.localX, height, location.localZ, HytaleBlock.EMPTY);
                return;
            }

            if (material.isNamed(MC_WATER)) {
                location.chunk.setHytaleBlock(location.localX, height, location.localZ, HytaleBlock.EMPTY);
                section.setFluid(location.localX, localY, location.localZ, HytaleBlockMapping.HY_WATER, 1);
                return;
            } else if (material.isNamed(MC_LAVA)) {
                location.chunk.setHytaleBlock(location.localX, height, location.localZ, HytaleBlock.EMPTY);
                section.setFluid(location.localX, localY, location.localZ, HytaleBlockMapping.HY_LAVA, 1);
                return;
            }

            HytaleBlock block = HytaleBlockMapping.toHytaleBlock(material);
            if (block.isFluid()) {
                location.chunk.setHytaleBlock(location.localX, height, location.localZ, HytaleBlock.EMPTY);
                section.setFluid(location.localX, localY, location.localZ, block.id, 1);
            } else {
                section.clearFluid(location.localX, localY, location.localZ);
                location.chunk.setHytaleBlock(location.localX, height, location.localZ, block);
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
            return 0;
        }

        @Override
        public void setBlockLightLevel(int x, int y, int height, int blockLightLevel) {
            // Not managed here; chunk serializer calculates lighting.
        }

        @Override
        public int getSkyLightLevel(int x, int y, int height) {
            return 15;
        }

        @Override
        public void setSkyLightLevel(int x, int y, int height, int skyLightLevel) {
            // Not managed here; chunk serializer calculates lighting.
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
            int centeredBlockX = x << 4;
            int centeredBlockZ = z << 4;
            int hChunkX = Math.floorDiv(centeredBlockX, HytaleChunk.CHUNK_SIZE);
            int hChunkZ = Math.floorDiv(centeredBlockZ, HytaleChunk.CHUNK_SIZE);
            return chunksByCoords.get(chunkKey(hChunkX, hChunkZ));
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
        private final int blockOffsetX, blockOffsetZ;
        private final int minHeight, maxHeight;

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
    
    /**
     * Map WorldPainter terrain type to Hytale biome name.
     */
    private String mapTerrainToBiome(Terrain terrain) {
        if (terrain == null) {
            return "Grassland";
        }
        
        String name = terrain.getName().toLowerCase();
        
        // Desert terrains
        if (name.contains("sand") || name.contains("desert") || name.contains("red sand")) {
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
        // Ocean/water terrains
        if (name.contains("ocean") || name.contains("beach") || name.contains("river")) {
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
        
        // Default to grassland
        return "Grassland";
    }
    
    /**
     * Map biome name to Hytale environment name.
     * Environments control weather, lighting, and other effects in Hytale.
     */
    private String mapBiomeToEnvironment(String biome) {
        if (biome == null) {
            return "Default";
        }
        
        switch (biome) {
            case "Desert":
                return "Desert";
            case "Tundra":
                return "Tundra";
            case "Tropical":
                return "Tropical";
            case "Forest":
                return "Forest";
            case "Ocean":
                return "Ocean";
            case "Mountain":
                return "Mountain";
            case "Underground":
                return "Underground";
            case "Grassland":
            default:
                return "Default";
        }
    }
    
    /**
     * Map biome name to tint color (ARGB format).
     * Tints affect grass, leaves, and other vegetation colors.
     * Format: 0xAARRGGBB.
     */
    private int mapBiomeToTint(String biome) {
        if (biome == null) {
            return 0xFF79C05A; // Natural grass green - RGB(121,192,90)
        }
        
        switch (biome) {
            case "Desert":
                return 0xFFBDB76B; // DarkKhaki - dry, sparse vegetation
            case "Tundra":
                return 0xFF80B497; // Muted teal - cold vegetation
            case "Tropical":
                return 0xFF1A8C4B; // Deep tropical green
            case "Forest":
                return 0xFF59A52C; // Dark forest green
            case "Ocean":
                return 0xFF4682B4; // SteelBlue - water tint
            case "Mountain":
                return 0xFF808080; // Gray - rocky, sparse vegetation
            case "Underground":
                return 0xFF4B0082; // Indigo - dark, underground tint
            case "Grassland":
            default:
                return 0xFF79C05A; // Natural grass green (similar to Minecraft plains)
        }
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
