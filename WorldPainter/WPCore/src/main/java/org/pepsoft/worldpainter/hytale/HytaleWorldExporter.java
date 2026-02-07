package org.pepsoft.worldpainter.hytale;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.pepsoft.minecraft.ChunkFactory;
import org.pepsoft.minecraft.Material;
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
import org.pepsoft.worldpainter.layers.Layer;
import org.pepsoft.worldpainter.layers.Biome;
import org.pepsoft.worldpainter.layers.FloodWithLava;
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
import static org.pepsoft.worldpainter.Constants.*;
import static org.pepsoft.worldpainter.DefaultPlugin.HYTALE;
import static org.pepsoft.worldpainter.Dimension.Anchor.NORMAL_DETAIL;
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
            
            // Create directory structure
            if (!worldDir.mkdirs()) {
                throw new IOException("Could not create directory: " + worldDir);
            }
            
            File chunksDir = new File(worldDir, "chunks");
            if (!chunksDir.mkdirs()) {
                throw new IOException("Could not create chunks directory");
            }
            
            File resourcesDir = new File(worldDir, "resources");
            if (!resourcesDir.mkdirs()) {
                throw new IOException("Could not create resources directory");
            }
            
            // Write config.json
            writeWorldConfig(worldDir);
            
            // Export dimensions
            Map<Integer, ChunkFactory.Stats> stats = new HashMap<>();
            Dimension dim0 = world.getDimension(NORMAL_DETAIL);
            if (dim0 != null) {
                if (progressReceiver != null) {
                    progressReceiver.setMessage("Exporting Overworld to Hytale format");
                }
                stats.put(DIM_NORMAL, exportDimension(worldDir, dim0, progressReceiver));
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
        }, "world.name", world.getName(), "platform.id", platform.id);
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
        
        config.put("ChunkConfig", new LinkedHashMap<>());
        config.put("IsTicking", true);
        config.put("IsBlockTicking", true);
        config.put("IsPvpEnabled", false);
        config.put("IsFallDamageEnabled", true);
        config.put("IsGameTimePaused", false);
        config.put("GameTime", "0001-01-01T00:00:00.000000000Z");
        
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
        config.put("IsSpawningNPC", true);
        config.put("IsSpawnMarkersEnabled", true);
        config.put("IsAllNPCFrozen", false);
        config.put("GameplayConfig", "Default");
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
    private ChunkFactory.Stats exportDimension(File worldDir, Dimension dimension, ProgressReceiver progressReceiver) 
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
            Set<Point> tileCoords = dimension.getTileCoords();
            
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
            
            // Export region files with BSON-serialized chunk data
            File chunksDir = new File(worldDir, "chunks");
            
            List<Point> sortedRegions = new ArrayList<>(regions);
            ExecutorService executor = createExecutorService("hytale-export", sortedRegions.size());
            ParallelProgressManager parallelProgressManager = (progressReceiver != null) 
                ? new ParallelProgressManager(progressReceiver, regions.size()) : null;
            AtomicBoolean abort = new AtomicBoolean(false);
            RuntimeException[] exception = new RuntimeException[1];
            
            try {
                for (Point region : sortedRegions) {
                    executor.execute(() -> {
                        if (abort.get()) return;
                        
                        ProgressReceiver regionProgress = (parallelProgressManager != null) 
                            ? parallelProgressManager.createProgressReceiver() : null;
                        
                        if (regionProgress != null) {
                            try {
                                regionProgress.checkForCancellation();
                            } catch (ProgressReceiver.OperationCancelled e) {
                                abort.set(true);
                                return;
                            }
                        }
                        
                        try {
                            exportRegion(chunksDir, dimension, region, collectedStats, regionProgress);
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
     * Export a single region (currently disabled - kept for future BSON implementation).
     */
    private void exportRegion(File chunksDir, Dimension dimension, Point regionCoords, 
            ChunkFactory.Stats stats, ProgressReceiver progressReceiver) throws IOException, ProgressReceiver.OperationCancelled {
        
        Path regionPath = chunksDir.toPath().resolve(HytaleRegionFile.getRegionFileName(regionCoords.x, regionCoords.y));
        
        try (HytaleRegionFile regionFile = new HytaleRegionFile(regionPath)) {
            regionFile.create();
            
            int minHeight = dimension.getMinHeight();
            int maxHeight = dimension.getMaxHeight();
            
            // Each region contains 32x32 Hytale chunks
            int chunksExported = 0;
            int totalChunks = 32 * 32;
            
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
                    
                    // Add entities (spawn markers, etc.)
                    addEntitiesToChunk(chunk, dimension, hyChunkX, hyChunkZ);
                    
                    // Write to region file
                    regionFile.writeChunk(localX, localZ, chunk);
                    
                    synchronized (stats) {
                        stats.surfaceArea += HytaleChunk.CHUNK_SIZE * HytaleChunk.CHUNK_SIZE;
                    }
                    
                    chunksExported++;
                    if (progressReceiver != null && chunksExported % 32 == 0) {
                        progressReceiver.setProgress((float) chunksExported / totalChunks);
                    }
                }
            }
            
            regionFile.flush();
        }
        
        logger.debug("Exported region {},{} to {}", regionCoords.x, regionCoords.y, regionPath);
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
                // Map the Minecraft terrain to the best Hytale equivalent
                HytaleTerrain hytaleTerrain = isCustomTerrain ? null : HytaleTerrainHelper.fromMinecraftTerrain(localTerrain);

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
                        biome = (hytaleTerrain != null && hytaleTerrain.getBiome() != null)
                            ? hytaleTerrain.getBiome()
                            : mapTerrainToBiome(localTerrain);
                        HytaleBiome fallback = HytaleBiome.fromTerrainBiomeName(biome);
                        environment = fallback.getEnvironment();
                        tint = fallback.getTint();
                    }
                } else {
                    // Auto biome: derive from terrain
                    String terrainBiomeName = (hytaleTerrain != null && hytaleTerrain.getBiome() != null)
                        ? hytaleTerrain.getBiome()
                        : mapTerrainToBiome(localTerrain);
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
                
                // Fill water or lava if below water level
                // Surface block is at height, so water starts at height+1
                // waterLevel is the TOP surface of water (inclusive)
                if (localWaterLevel > height) {
                    waterColumns++;
                    // Check if this column is marked as lava instead of water
                    boolean isLava = tile.getBitLayerValue(FloodWithLava.INSTANCE, tileLocalX, tileLocalZ);
                    String fluidId = isLava ? HytaleBlockMapping.HY_LAVA : HytaleBlockMapping.HY_WATER;
                    // Debug logging for first fluid placement in this chunk
                    if (!waterLogged) {
                        logger.info("{} at world ({}, {}) chunk block ({}, {}) - Terrain Y: {}, Fluid Y: {}, Placing Y: {} to {}",
                            isLava ? "Lava" : "Water", worldX, worldZ, localX, localZ, height, localWaterLevel, height + 1, localWaterLevel);
                        waterLogged = true;
                    }
                    for (int y = height + 1; y <= localWaterLevel; y++) {
                        chunk.setHytaleBlock(localX, y, localZ, HytaleBlock.EMPTY);
                        chunk.getSections()[y >> 5].setFluid(localX, y & 31, localZ, 
                            fluidId, 1); // Source fluids use max level (1 for water/lava)
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
