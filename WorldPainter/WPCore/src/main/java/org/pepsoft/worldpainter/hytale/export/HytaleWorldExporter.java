package org.pepsoft.worldpainter.hytale.export;

import org.pepsoft.worldpainter.hytale.*;

import org.pepsoft.worldpainter.hytale.chunk.HytaleChunk;
import org.pepsoft.worldpainter.hytale.chunk.HytaleSection;
import org.pepsoft.worldpainter.hytale.chunk.HytaleChunkStore;
import org.pepsoft.worldpainter.hytale.chunk.HytaleRegionFile;
import org.pepsoft.worldpainter.hytale.prefab.HytalePrefabPaster;
import org.pepsoft.worldpainter.hytale.prefab.HytalePrefabJsonObject;
import org.pepsoft.worldpainter.hytale.vegetation.HytaleAutoVegetationAlgorithm;
import org.pepsoft.worldpainter.hytale.vegetation.HytaleAutoVegetationDefaults;

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
import org.pepsoft.worldpainter.objects.WPObject;
import org.pepsoft.worldpainter.exporting.LayerExporter;
import org.pepsoft.worldpainter.exporting.FirstPassLayerExporter;
import org.pepsoft.worldpainter.exporting.SecondPassLayerExporter;
import org.pepsoft.worldpainter.util.FileInUseException;
import org.pepsoft.worldpainter.vo.AttributeKeyVO;
import org.pepsoft.worldpainter.vo.EventVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.vecmath.Point3i;
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

    // Extra margin (in blocks) added to the placement bounds check so that
    // custom objects near region edges are placed instead of deferred as
    // fixups. 256 blocks covers the largest typical tree or structure.
    private static final int OBJECT_BORDER_MARGIN = 256;

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
            // Ensure Hytale block Materials have correct properties (veryInsubstantial
            // for surface-only blocks) BEFORE any Material.get() call during export.
            // Without this, custom object layers cannot place on top of custom terrain.
            HytaleBlockRegistry.ensureMaterialsRegistered();

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
                HytaleWorldConfigWriter configWriter = new HytaleWorldConfigWriter(world, blockOffsetX, blockOffsetZ);
                configWriter.writeWorldConfig(actualWorldDir, name);
                configWriter.writeResourceFiles(actualWorldDir);
                configWriter.writeServerConfig(effectiveSaveDir);
                configWriter.writeServerBoilerplate(effectiveSaveDir);
                
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
            // Region-level pending prefab pastes — populated per-chunk inside
            // populateChunkFromTile, executed AFTER the chunk loop so multi-chunk prefabs
            // can be routed to the right chunk instead of being clipped at 32-block
            // chunk boundaries.
            List<PendingPrefabPaste> regionPrefabPastes = new ArrayList<>();
            
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
                    populateChunkFromTile(chunk, dimension, tile, originalBlockX, originalBlockZ, regionPrefabPastes);

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

            // ── Region-level prefab pastes ─────────────────────────────
            // Execute all prefab pastes AFTER every chunk in the region is populated, so
            // multi-chunk prefabs (HytalePrefabLayer + HytaleSpecificPrefabLayer) can be
            // routed to the correct chunk in chunksByCoords. Blocks that land in chunks
            // outside this region are dropped silently; the adjacent region's iteration
            // will write them. This is what fixes the per-chunk 32-block clipping that
            // turned big prefabs into grids of chopped quadrants.
            for (PendingPrefabPaste pending : regionPrefabPastes) {
                boolean pasted = prefabPaster.paste(chunksByCoords,
                        pending.worldX, pending.anchorY, pending.worldZ,
                        blockOffsetX, blockOffsetZ, pending.prefabPath);
                if (!pasted && pending.prefabName != null) {
                    // Fallback: keep a marker on the chunk containing the anchor so the
                    // missing prefab is visible during debugging.
                    HytaleChunk anchorChunk = lookupAnchorChunk(chunksByCoords,
                            pending.worldX, pending.worldZ);
                    if (anchorChunk != null) {
                        int aLocalX = Math.floorMod(pending.worldX + blockOffsetX, HytaleChunk.CHUNK_SIZE);
                        int aLocalZ = Math.floorMod(pending.worldZ + blockOffsetZ, HytaleChunk.CHUNK_SIZE);
                        anchorChunk.addPrefabMarker(aLocalX, pending.anchorY, aLocalZ,
                                pending.prefabName, pending.prefabPath);
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

            HytaleChunkPostProcessor postProcessor = new HytaleChunkPostProcessor(blockOffsetX, blockOffsetZ);

            // Re-seal fluid bodies: restore any fluid blocks that were cleared
            // by layer exporters (caves, chasms, custom objects) during
            // post-processing. Hytale has no runtime water flow, so every fluid
            // block must be explicitly present in the exported data.
            postProcessor.sealFluidBodies(dimension, chunksByCoords);

            // Apply frost AFTER sealing fluid bodies so that ice placed on
            // water surfaces is not overwritten by the fluid restoration pass.
            applyFrostLayer(dimension, regionCoords, chunksByCoords);

            postProcessor.convertCoveredGrass(chunksByCoords);

            // Skip pre-baked lighting: Hytale recalculates light at runtime
            // when players interact with blocks, which overwrites our values
            // and causes visual artefacts. Writing empty light data lets
            // Hytale compute lighting natively with consistent results.
            // calculateLighting(regionCoords, chunksByCoords, progressReceiver);

            // Final pass: enforce void columns by clearing any blocks/fluids
            // that may have been placed by second-pass layers, custom objects,
            // frost, or lighting. This guarantees void areas are truly empty.
            postProcessor.enforceVoidColumns(dimension, chunksByCoords);

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

    static List<Layer> sortFirstPassLayers(Set<Layer> layers) {
        List<Layer> firstPassLayers = new ArrayList<>();
        for (Layer layer : layers) {
            Class<? extends LayerExporter> exporterType = layer.getExporterType();
            if (exporterType != null && FirstPassLayerExporter.class.isAssignableFrom(exporterType)) {
                firstPassLayers.add(layer);
            }
        }
        Collections.sort(firstPassLayers);
        return firstPassLayers;
    }

    static List<Bo2Layer> sortBo2Layers(Set<Layer> layers) {
        List<Bo2Layer> bo2Layers = new ArrayList<>();
        for (Layer layer : layers) {
            if (layer instanceof Bo2Layer) {
                bo2Layers.add((Bo2Layer) layer);
            }
        }
        Collections.sort(bo2Layers);
        return bo2Layers;
    }

    static HytaleBlock getSurfaceOnlySubstrate(Terrain terrain, MixedMaterial customMaterial,
                                               HytaleTerrain hytaleSubstrate, long seed, int x, int z,
                                               int y) {
        if (customMaterial != null) {
            for (int scanY = y; scanY >= Math.max(0, y - 8); scanY--) {
                HytaleBlock candidate = HytaleBlockMapping.toHytaleBlock(customMaterial.getMaterial(seed, x, z, scanY));
                if ((candidate != null) && (! candidate.isEmpty()) && (! candidate.isFluid())
                        && (! HytaleBlockRegistry.isSurfaceOnlyBlock(candidate.id))) {
                    return candidate;
                }
            }
            // Mix has no solid block (e.g. all plants). Honour the HytaleTerrain painted
            // at this pixel via HytaleTerrainLayer before falling back to DIRT, so a
            // user who paints Sand and then a plant-mix Custom Terrain on top gets
            // Sand at the surface instead of a synthesised DIRT.
            if (hytaleSubstrate != null) {
                HytaleBlock fromLayer = hytaleSubstrate.getBlock(seed, x, z, 0);
                if ((fromLayer != null) && (! fromLayer.isEmpty()) && (! fromLayer.isFluid())
                        && (! HytaleBlockRegistry.isSurfaceOnlyBlock(fromLayer.id))) {
                    return fromLayer;
                }
            }
            return HytaleBlock.DIRT;
        }

        HytaleTerrain baseTerrain = HytaleTerrainHelper.fromMinecraftTerrain(terrain);
        HytaleBlock candidate = (baseTerrain != null) ? baseTerrain.getBlock(seed, x, z, 0) : HytaleBlock.GRASS;
        if ((candidate == null) || candidate.isEmpty() || candidate.isFluid()
                || HytaleBlockRegistry.isSurfaceOnlyBlock(candidate.id)) {
            return HytaleBlock.DIRT;
        }
        return candidate;
    }

    /** Deferred prefab paste — collected during column loop, executed after all terrain is placed. */
    private static final class PendingPrefabPaste {
        final int localX, anchorY, localZ, worldX, worldZ;
        final String prefabPath;
        final String prefabName; // for fallback marker (display name for both layer types)

        PendingPrefabPaste(int localX, int anchorY, int localZ,
                           int worldX, int worldZ, String prefabPath, String prefabName) {
            this.localX = localX;
            this.anchorY = anchorY;
            this.localZ = localZ;
            this.worldX = worldX;
            this.worldZ = worldZ;
            this.prefabPath = prefabPath;
            this.prefabName = prefabName;
        }
    }

    /**
     * Populate a Hytale chunk with terrain data from WorldPainter dimension.
     * Uses HytaleBlockMapping for proper block conversion and sets biomes.
     */
    private void populateChunkFromTile(HytaleChunk chunk, Dimension dimension, Tile tile, int worldBlockX, int worldBlockZ,
                                       List<PendingPrefabPaste> regionPrefabPastes) {
        int waterLevel = tile.getWaterLevel(0, 0);
        Terrain terrain = tile.getTerrain(0, 0);
        long seed = dimension.getMinecraftSeed();
        // Read per-export Hytale flag once per chunk: if true, plants painted via
        // HytalePlantsLayer are written with SUPPORT_DECORATIVE so Hytale's physics cascade
        // skips them (no chain-break, but the gathering system also skips them so broken
        // plants drop themselves instead of resources). User-controlled via the export dialog.
        final boolean plantsPhysicsExempt = world.getAttribute(HytaleWorldSettings.ATTRIBUTE_PLANTS_PHYSICS_EXEMPT)
                .orElse(false);

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
        // Prefab pastes are appended to the region-level list and executed AFTER all
        // chunks in the region are populated, so multi-chunk prefabs can be routed to
        // the correct chunk (see exportRegion's post-loop paste pass).
        final List<PendingPrefabPaste> pendingPrefabPastes = regionPrefabPastes;

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

                resolveAndSetBiome(chunk, tile, localTerrain, tileLocalX, tileLocalZ, localX, localZ);
                
                // Bottom layer - bedrock (unless the dimension is bottomless)
                if (!dimension.isBottomless()) {
                    chunk.setHytaleBlock(localX, 0, localZ, HytaleBlock.BEDROCK);
                }
                
                // Surface-only block sampled from a custom-terrain MixedMaterial at depth 0.
                // Placement is deferred to after the inline fluid loop below so that the
                // loop's setHytaleBlock(EMPTY) over [height+1, waterLevel] does not wipe
                // the plant on flooded columns. The post-export sealAboveTerrainColumn pass
                // also skips this voxel once it's seal-protected. Mirrors the fix a2358135
                // applied to the HytalePlantsLayer overlay path.
                HytaleBlock pendingCustomTerrainSurfacePlant = null;
                if (isCustomTerrain && customMaterial != null) {
                    // Custom terrain: resolve blocks through MixedMaterial → Material → HytaleBlock.
                    // Surface-only blocks (vegetation, decorations) must only appear on top;
                    // subsurface is filled with dirt/stone just like the non-custom path.
                    HytaleBlock substrateBlock = getSurfaceOnlySubstrate(localTerrain, customMaterial, hytaleTerrain, seed, worldX, worldZ, height);
                    HytaleBlock subsurfaceFallback = substrateBlock.isGrass() ? HytaleBlock.DIRT : substrateBlock;
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
                            // Subsurface gets the resolved substrate; surface keeps the
                            // terrain substrate instead of synthesising grass.
                            block = (depth > 0)
                                    ? ((depth <= 4) ? subsurfaceFallback : HytaleBlock.STONE)
                                    : substrateBlock;
                        } else if (block.isGrass() && depth > 0) {
                            // Grass only belongs on the surface
                            block = (depth <= 4) ? subsurfaceFallback : HytaleBlock.STONE;
                        }
                        if (block.isFluid()) {
                            chunk.setHytaleBlock(localX, y, localZ, HytaleBlock.EMPTY);
                            chunk.getSections()[y >> 5].setFluid(localX, y & 31, localZ, block.id, 1);
                        } else {
                            chunk.setHytaleBlock(localX, y, localZ, block);
                        }
                    }
                    // Capture the vegetation/decoration block for placement AFTER the inline
                    // fluid loop below. Placing it now would let the fluid loop overwrite it
                    // with EMPTY on flooded columns.
                    pendingCustomTerrainSurfacePlant = surfacePlant;
                } else if (hytaleTerrain != null) {
                    HytaleBlock terrainBlock = hytaleTerrain.getPrimaryBlock();
                    boolean surfaceOnly = HytaleBlockRegistry.isSurfaceOnlyBlock(terrainBlock.id);
                    boolean grassTerrain = terrainBlock.isGrass();
                    HytaleBlock substrateBlock = surfaceOnly
                            ? getSurfaceOnlySubstrate(localTerrain, customMaterial, null, seed, worldX, worldZ, height)
                            : terrainBlock;
                    HytaleBlock subsurfaceFallback = substrateBlock.isGrass() ? HytaleBlock.DIRT : substrateBlock;
                    for (int y = 1; y <= height; y++) {
                        int depth = height - y;
                        HytaleBlock block;
                        if (surfaceOnly) {
                            if (depth > 0) {
                                // Fill subsurface with substrate (or stone below depth 4)
                                block = (depth <= 4) ? subsurfaceFallback : HytaleBlock.STONE;
                            } else {
                                // Surface: preserve the underlying terrain
                                // substrate; the plant goes on top at height+1.
                                block = substrateBlock;
                            }
                        } else if (grassTerrain && depth > 0) {
                            // Grass blocks only belong on the surface; Hytale converts
                            // subsurface grass to dirt at runtime which hurts performance,
                            // so export substrate directly below the top grass block
                            block = (depth <= 4) ? subsurfaceFallback : HytaleBlock.STONE;
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

                // ── Custom-Terrain Surface Plant (deferred from above) ───
                // Place the plant captured from the custom-terrain MixedMaterial
                // AFTER the fluid loop so a flooded column does not wipe it, and
                // seal-protect so the post-export seal pass leaves it alone.
                // HytalePlantsLayer paints below override this default, so this
                // runs before the overlay block.
                if ((pendingCustomTerrainSurfacePlant != null)
                        && (! pendingCustomTerrainSurfacePlant.isEmpty())
                        && (! pendingCustomTerrainSurfacePlant.isFluid())
                        && ((height + 1) < dimension.getMaxHeight())) {
                    chunk.setHytaleBlock(localX, height + 1, localZ, pendingCustomTerrainSurfacePlant);
                    chunk.setSealProtected(localX, height + 1, localZ, true);
                    if (plantsPhysicsExempt) {
                        chunk.setDecorative(localX, height + 1, localZ, true);
                    }
                }

                // ── Plant Overlay Layer ──────────────────────────────
                // Surface-only HytaleTerrains (plants, decorations) painted on
                // top of a substrate land here. Place the plant block at
                // height + 1 without touching the substrate at height — this
                // is what makes "paint Stone, paint Bush" produce stone-with-
                // bush-on-top instead of overwriting stone with grass.
                //
                // Optionally mark the placed block IS_DECO (support value 15) so
                // Hytale's physics cascade does not chain-break it. Tradeoff:
                // IS_DECO also bypasses the gathering interaction, so the
                // player gets the block itself (e.g. a Plant_Bush item) instead
                // of the configured drops (e.g. berries). Gated on the
                // {@link HytaleWorldSettings#ATTRIBUTE_PLANTS_PHYSICS_EXEMPT}
                // export attribute, which the user toggles in the export dialog.
                int plantIndex = HytalePlantsLayer.getPlantIndex(tile, tileLocalX, tileLocalZ);
                if (plantIndex > 0) {
                    HytaleTerrain plantTerrain = HytaleTerrain.getByLayerIndex(plantIndex);
                    if (plantTerrain != null) {
                        HytaleBlock plantBlock = plantTerrain.getBlock(seed, worldX, worldZ, 0);
                        if ((plantBlock != null) && (! plantBlock.isEmpty()) && (! plantBlock.isFluid())
                                && ((height + 1) < dimension.getMaxHeight())) {
                            chunk.setHytaleBlock(localX, height + 1, localZ, plantBlock);
                            // Seal-protect so the post-export sealAboveTerrainColumn pass
                            // does not clear this plant on flooded columns.
                            chunk.setSealProtected(localX, height + 1, localZ, true);
                            if (plantsPhysicsExempt) {
                                chunk.setDecorative(localX, height + 1, localZ, true);
                            }
                        }
                    }
                }

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
                        int biomeId = tile.getLayerValue(Biome.INSTANCE, tileLocalX, tileLocalZ);
                        if (biomeId == HytaleBiome.BIOME_AUTO) {
                            // Reuse the exporter's existing terrain-derived biome
                            // mapping for auto-biome cells.
                            biomeId = HytaleBiome.fromTerrainBiomeName(
                                    mapTerrainToBiome(localTerrain)).getId();
                        }
                        HytaleAutoVegetationSettings.BiomeVegetationConfig cfg =
                                autoVegSettings.getByBiome().get(biomeId);
                        if (cfg != null) {
                            long pixelSeed = HytaleAutoVegetationAlgorithm.seedFor(
                                    autoVegSettings.getSeed(),
                                    tile.getX(), tile.getY(),
                                    tileLocalX, tileLocalZ);
                            Random rng = new Random(pixelSeed);
                            UUID pickedTerrainId = HytaleAutoVegetationAlgorithm.pick(cfg, rng);
                            if (pickedTerrainId != null) {
                                HytaleTerrain pickedTerrain = HytaleTerrain.getById(pickedTerrainId);
                                if (pickedTerrain != null) {
                                    HytaleBlock plantBlock = pickedTerrain.getBlock(seed, worldX, worldZ, 0);
                                    HytaleBlock substrate = chunk.getHytaleBlock(localX, height, localZ);
                                    if ((plantBlock != null) && (! plantBlock.isEmpty()) && (! plantBlock.isFluid())
                                            && ((height + 1) < dimension.getMaxHeight())
                                            && HytaleAutoVegetationAlgorithm.isValidSubstrateFor(plantBlock, substrate)) {
                                        chunk.setHytaleBlock(localX, height + 1, localZ, plantBlock);
                                        chunk.setSealProtected(localX, height + 1, localZ, true);
                                        if (plantsPhysicsExempt) {
                                            chunk.setDecorative(localX, height + 1, localZ, true);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                applyEnvironmentLayer(chunk, tile, tileLocalX, tileLocalZ, localX, localZ);
                applyEntityLayer(chunk, tile, tileLocalX, tileLocalZ, localX, localZ);
                enqueuePrefabLayerPaste(tile, tileLocalX, tileLocalZ, localX, localZ, worldX, worldZ, height, pendingPrefabPastes);
                enqueueSpecificPrefabPastes(tile, specificPrefabLayers, localX, localZ, tileLocalX, tileLocalZ,
                        worldX, worldZ, worldBlockX, worldBlockZ, seed, pendingPrefabPastes);
                
                // Update heightmap - WorldPainter height is the Y coordinate of the surface block
                // Hytale heightmap also stores Y coordinate of topmost solid block
                chunk.setHeight(localX, localZ, height);
            }
        }

        // (Prefab pastes are now executed at the region level — see exportRegion —
        // so multi-chunk prefabs can be routed to the right chunk rather than being
        // clipped at this chunk's 32-block bounds.)

        // Log summary for this chunk area
        if (waterColumns > 0 || specialFluidColumns > 0) {
            logger.warn("Chunk at ({}, {}): {} water-body columns, {} surface-fluid columns, height {}-{}, waterLevel {}-{}, fluids: {}",
                worldBlockX, worldBlockZ, waterColumns, specialFluidColumns, minHeight, maxHeight, minWaterLevel, maxWaterLevel, fluidTypeCounts);
        }
    }
    
    /**
     * Resolve the Hytale biome for a column and write biome name, environment, and
     * grass tint into the chunk. If the user painted a Hytale biome via the {@link
     * Biome} layer, that wins; otherwise the biome is derived from the column's
     * Minecraft terrain via {@link #mapTerrainToBiome}.
     */
    private void resolveAndSetBiome(HytaleChunk chunk, Tile tile, Terrain localTerrain,
                                    int tileLocalX, int tileLocalZ, int localX, int localZ) {
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
        chunk.setBiomeName(localX, localZ, biome);
        chunk.setEnvironment(localX, localZ, environment);
        chunk.setTint(localX, localZ, tint);
    }

    /**
     * Apply a user-painted environment override to a column (weather, sky, water tint).
     * If the layer value is {@link HytaleEnvironmentLayer#ENV_AUTO}, the biome's
     * default environment (already set by {@link #resolveAndSetBiome}) stays in place.
     */
    private void applyEnvironmentLayer(HytaleChunk chunk, Tile tile,
                                       int tileLocalX, int tileLocalZ, int localX, int localZ) {
        int envLayerValue = tile.getLayerValue(HytaleEnvironmentLayer.INSTANCE, tileLocalX, tileLocalZ);
        if (envLayerValue == HytaleEnvironmentLayer.ENV_AUTO) {
            return;
        }
        HytaleEnvironmentData envData = HytaleEnvironmentData.getById(envLayerValue);
        if (envData == null) {
            return;
        }
        chunk.setEnvironment(localX, localZ, envData.getName());
        if (envData.getWaterTint() != null) {
            chunk.setWaterTint(localX, localZ, envData.getWaterTint());
        }
    }

    /**
     * Apply a user-painted spawn-density preset to a column (NPC spawn frequency
     * and optional filter tag).
     */
    private void applyEntityLayer(HytaleChunk chunk, Tile tile,
                                  int tileLocalX, int tileLocalZ, int localX, int localZ) {
        int entityLayerValue = tile.getLayerValue(HytaleEntityLayer.INSTANCE, tileLocalX, tileLocalZ);
        if (entityLayerValue <= 0) {
            return;
        }
        float spawnDensity = HytaleEntityLayer.getSpawnDensity(entityLayerValue);
        chunk.setSpawnDensity(localX, localZ, spawnDensity);
        if (entityLayerValue < HytaleEntityLayer.SPAWN_TAGS.length
                && HytaleEntityLayer.SPAWN_TAGS[entityLayerValue] != null) {
            chunk.setSpawnTag(localX, localZ, HytaleEntityLayer.SPAWN_TAGS[entityLayerValue]);
        }
    }

    /**
     * Queue a prefab paste for the {@link HytalePrefabLayer} value at this column.
     * Pastes are executed at the region level after all chunks are populated, so
     * multi-chunk prefabs can span chunk boundaries.
     */
    private void enqueuePrefabLayerPaste(Tile tile, int tileLocalX, int tileLocalZ,
                                         int localX, int localZ, int worldX, int worldZ, int height,
                                         List<PendingPrefabPaste> pendingPrefabPastes) {
        int prefabLayerValue = tile.getLayerValue(HytalePrefabLayer.INSTANCE, tileLocalX, tileLocalZ);
        if (prefabLayerValue <= 0 || prefabLayerValue >= HytalePrefabLayer.PREFAB_PATHS.length) {
            return;
        }
        String prefabPath = HytalePrefabLayer.PREFAB_PATHS[prefabLayerValue];
        if (prefabPath != null) {
            pendingPrefabPastes.add(new PendingPrefabPaste(
                    localX, height + 1, localZ, worldX, worldZ, prefabPath,
                    HytalePrefabLayer.PREFAB_NAMES[prefabLayerValue]));
        }
    }

    /**
     * Queue prefab pastes for all {@link HytaleSpecificPrefabLayer}s at this column.
     * Grid alignment, density-based probability roll, and random displacement match
     * the {@link Bo2LayerExporter} approach.
     */
    private void enqueueSpecificPrefabPastes(Tile tile, List<HytaleSpecificPrefabLayer> specificPrefabLayers,
                                             int localX, int localZ, int tileLocalX, int tileLocalZ,
                                             int worldX, int worldZ, int worldBlockX, int worldBlockZ,
                                             long seed,
                                             List<PendingPrefabPaste> pendingPrefabPastes) {
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
            pendingPrefabPastes.add(new PendingPrefabPaste(
                    placeLocalX, placeHeight + 1, placeLocalZ,
                    placeX, placeZ, selected.getRelativePath(),
                    selected.getDisplayName()));
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
        // Expand both the iteration area and the bounds-fit check for custom object footprints.
        // The chunk-position-seeded random in Bo2LayerExporter makes tree placement
        // deterministic per-chunk, so each region also iterating the overlap strip into
        // its neighbours generates the same trees the neighbouring region would generate
        // for those chunks. HytaleRegionMinecraftWorld silently drops writes to chunks not
        // in this region's map (and getMaterialAt falls back to dimension terrain there),
        // so each region writes only its own portion of any boundary-straddling tree —
        // combined, the two regions cover the full footprint without the cross-shaped
        // seams that the strict-region iteration produced at WP X=0 / Z=0. The margin is
        // computed per layer below; large custom objects can extend farther than the old
        // fixed margin.

        HytaleRegionMinecraftWorld regionWorld = new HytaleRegionMinecraftWorld(chunksByCoords, blockOffsetX, blockOffsetZ,
                dimension.getMinHeight(), dimension.getMaxHeight(), dimension);

        for (Bo2Layer bo2Layer : sortBo2Layers(layers)) {
            regionWorld.setActiveBlockMappings(bo2Layer.getHytaleBlockMappings());
            // Hytale stores blocks and fluids separately, so custom-object
            // blocks always coexist with surrounding water. Track them for
            // the seal-above-terrain pass without abusing BlockPhysics support
            // distances. noPhysics layers still get DECORATIVE
            // (physics-exempt); other layers keep normal support data so
            // Hytale can compute structural support on demand.
            regionWorld.setPlacedBlockSupportValue(bo2Layer.isNoPhysics()
                    ? HytaleChunk.SUPPORT_DECORATIVE
                    : HytaleChunk.SUPPORT_NONE);
            regionWorld.setProtectPlacedBlocksFromFluidSeal(true);
            Bo2LayerExporter exporter = bo2Layer.getExporter(dimension, platform, dimension.getLayerSettings(bo2Layer));
            if (exporter == null) {
                regionWorld.setPlacedBlockSupportValue(HytaleChunk.SUPPORT_NONE);
                regionWorld.setProtectPlacedBlocksFromFluidSeal(false);
                continue;
            }
            final int objectBorderMargin = getObjectBorderMargin(bo2Layer);
            final Rectangle placementBounds = expandToBo2ChunkAlignedBounds(exportedArea, objectBorderMargin);
            try {
                exporter.addFeatures(placementBounds, placementBounds, regionWorld);
            } catch (RuntimeException e) {
                logger.error("Error applying custom object layer '{}' in region {},{}",
                        bo2Layer.getName(), regionCoords.x, regionCoords.y, e);
            } finally {
                regionWorld.setPlacedBlockSupportValue(HytaleChunk.SUPPORT_NONE);
                regionWorld.setProtectPlacedBlocksFromFluidSeal(false);
            }
        }
    }

    private static int getObjectBorderMargin(Bo2Layer layer) {
        int margin = OBJECT_BORDER_MARGIN;
        try {
            for (WPObject object : layer.getObjectProvider().getAllObjects()) {
                final Point3i dimensions = object.getDimensions();
                final Point3i offset = object.getOffset();
                margin = Math.max(margin, Math.max(
                        Math.max(Math.abs(offset.x), Math.abs(offset.x + dimensions.x - 1)),
                        Math.max(Math.abs(offset.y), Math.abs(offset.y + dimensions.y - 1))));
            }
        } catch (UnsupportedOperationException e) {
            logger.debug("Could not enumerate custom objects for layer {}; using default border margin",
                    layer.getName(), e);
        }
        return margin + layer.getRandomDisplacement() + 16;
    }

    private static Rectangle expandToBo2ChunkAlignedBounds(Rectangle area, int margin) {
        final int x1 = (area.x - margin) & ~0xf;
        final int y1 = (area.y - margin) & ~0xf;
        final int x2 = (area.x + area.width + margin + 15) & ~0xf;
        final int y2 = (area.y + area.height + margin + 15) & ~0xf;
        return new Rectangle(x1, y1, x2 - x1, y2 - y1);
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
        final Rectangle placementBounds = new Rectangle(
                exportedArea.x - OBJECT_BORDER_MARGIN,
                exportedArea.y - OBJECT_BORDER_MARGIN,
                exportedArea.width + OBJECT_BORDER_MARGIN * 2,
                exportedArea.height + OBJECT_BORDER_MARGIN * 2);

        // Use the dimension-aware regionWorld so substrate / material lookups for
        // chunks in adjacent regions fall back to dimension terrain (STONE below
        // surface, AIR above) rather than always returning AIR, keeping placement
        // decisions consistent between regions iterating the overlap strip.
        final HytaleRegionMinecraftWorld regionWorld = new HytaleRegionMinecraftWorld(chunksByCoords, blockOffsetX, blockOffsetZ,
                dimension.getMinHeight(), dimension.getMaxHeight(), dimension);

        // Instantiate all exporters
        Map<Layer, SecondPassLayerExporter> exporters = new LinkedHashMap<>();
        for (Layer layer : secondPassLayers) {
            LayerExporter exporter = layer.getExporter(dimension, platform, dimension.getLayerSettings(layer));
            if (exporter instanceof SecondPassLayerExporter) {
                exporters.put(layer, (SecondPassLayerExporter) exporter);
            }
        }

        // Stage 1: CARVE - remove blocks (caves, tunnels, etc.)
        // Pass placementBounds as both iteration area and fit-check bounds (matching
        // the Bo2 fix). Each region also iterates the OBJECT_BORDER_MARGIN overlap
        // strip into its neighbours, so caves/chasms/decorations that straddle a
        // region boundary are carved/added by both regions (each writes only the
        // chunks it owns; the rest is silently dropped by HytaleRegionMinecraftWorld).
        for (Map.Entry<Layer, SecondPassLayerExporter> entry : exporters.entrySet()) {
            SecondPassLayerExporter exporter = entry.getValue();
            if (!exporter.getStages().contains(SecondPassLayerExporter.Stage.CARVE)) {
                continue;
            }
            try {
                exporter.carve(placementBounds, placementBounds, regionWorld);
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
                exporter.addFeatures(placementBounds, placementBounds, regionWorld);
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

        List<Layer> firstPassLayers = sortFirstPassLayers(layers);
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
            for (HytaleSection section : chunk.getSections()) {
                section.resetMaterialView();
            }
            for (int y = chunk.getMinHeight(); y < chunk.getMaxHeight(); y++) {
                HytaleSection section = chunk.getSections()[y >> 5];
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

    private HytaleChunk lookupAnchorChunk(Map<Long, HytaleChunk> chunksByCoords, int wpBlockX, int wpBlockZ) {
        int centredX = wpBlockX + blockOffsetX;
        int centredZ = wpBlockZ + blockOffsetZ;
        int hChunkX = Math.floorDiv(centredX, HytaleChunk.CHUNK_SIZE);
        int hChunkZ = Math.floorDiv(centredZ, HytaleChunk.CHUNK_SIZE);
        return chunksByCoords.get(chunkKey(hChunkX, hChunkZ));
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
