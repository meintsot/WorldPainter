package org.pepsoft.worldpainter.hytale.export;

import org.pepsoft.minecraft.ChunkFactory;
import org.pepsoft.util.FileUtils;
import org.pepsoft.util.ProgressReceiver;
import org.pepsoft.util.mdc.MDCCapturingRuntimeException;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Platform;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.World2;
import org.pepsoft.worldpainter.exporting.WorldExportSettings;
import org.pepsoft.worldpainter.hytale.HytaleBlock;
import org.pepsoft.worldpainter.hytale.HytaleBlockRegistry;
import org.pepsoft.worldpainter.hytale.chunk.HytaleChunk;
import org.pepsoft.worldpainter.hytale.chunk.HytaleChunkStore;
import org.pepsoft.worldpainter.merging.InvalidMapException;
import org.pepsoft.worldpainter.util.FileInUseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.pepsoft.worldpainter.Constants.DIM_NORMAL;
import static org.pepsoft.worldpainter.DefaultPlugin.HYTALE;
import static org.pepsoft.worldpainter.Dimension.Anchor.NORMAL_DETAIL;

/**
 * Merger for the Hytale platform. Mirrors {@link org.pepsoft.worldpainter.merging.JavaWorldMerger}
 * in structure: a re-export of a previously imported world that backs up the original map directory
 * and regenerates chunks where TalePainter has tile coverage, preserving entities / block health /
 * metadata for the regenerated chunks via the inherited
 * {@link HytaleWorldExporter#mergeOriginalChunkData} hook.
 *
 * <p>Phase 1 covers the skeleton: every chunk that TalePainter has data for is regenerated
 * wholesale, while entities and other non-block metadata from the original world are merged
 * back in. Block-level merge options ({@link #setMergeBlocksAboveGround above-ground} /
 * {@link #setMergeBlocksUnderground underground} / {@link #setSurfaceMergeDepth surface depth} /
 * {@link #setMergeBiomes biomes}) are exposed as getter/setter pairs but are not yet wired
 * into the chunk-generation path. Phase 2 will wire them.
 *
 * <p>The {@code mapDir} passed to the constructor is the top of the Hytale save (the directory
 * directly containing {@code universe/worlds/default/chunks/}, {@code config.json}, etc.) — the
 * same level that {@link HytaleWorldExporter#export} writes when called with
 * {@code baseDir = saveDir.getParentFile()} and {@code name = saveDir.getName()}.
 */
public class HytaleWorldMerger extends HytaleWorldExporter {

    private static final Logger logger = LoggerFactory.getLogger(HytaleWorldMerger.class);

    // Standard Hytale world structure inside a save dir.
    private static final String UNIVERSE_DIR = "universe";
    private static final String WORLDS_DIR = "worlds";
    private static final String DEFAULT_WORLD = "default";
    private static final String CHUNKS_DIR = "chunks";

    private final File mapDir;

    // Merge settings — mirrors JavaWorldMerger. Defaults chosen to be sensible for a
    // "just regenerate everything I painted" workflow. Block-level flags default to true
    // so that wiring them through in Phase 2 doesn't require a UI change.
    private boolean replaceChunks = false;
    private boolean mergeBlocksAboveGround = true;
    private boolean mergeBlocksUnderground = true;
    /** Single biome merge flag — Hytale biomes are per-column, not per 3D cell. */
    private boolean mergeBiomes = true;
    private int surfaceMergeDepth = 1;
    private boolean clearTrees = false;
    private boolean clearVegetation = false;
    private boolean clearManMadeAboveGround = false;
    private boolean clearManMadeBelowGround = false;

    public HytaleWorldMerger(World2 world, WorldExportSettings exportSettings, File mapDir, Platform platform) {
        super(world, exportSettings);
        Objects.requireNonNull(mapDir, "mapDir");
        if (!HYTALE.equals(platform)) {
            throw new IllegalArgumentException("HytaleWorldMerger only supports the Hytale platform; got " + platform);
        }
        if (!mapDir.isDirectory()) {
            throw new IllegalArgumentException(mapDir + " does not exist or is not a directory");
        }
        // A valid Hytale save has the inner world dir at universe/worlds/default
        File innerWorldDir = getInnerWorldDir(mapDir);
        if (!innerWorldDir.isDirectory()) {
            throw new IllegalArgumentException(mapDir + " does not contain a Hytale world at "
                + UNIVERSE_DIR + "/" + WORLDS_DIR + "/" + DEFAULT_WORLD);
        }
        this.mapDir = mapDir;
    }

    public File getMapDir() {
        return mapDir;
    }

    // ── Settings (Phase 1 surface; only wired through inherited mergeOriginalChunkData) ──

    public boolean isReplaceChunks() {
        return replaceChunks;
    }

    public void setReplaceChunks(boolean replaceChunks) {
        this.replaceChunks = replaceChunks;
    }

    public boolean isMergeBlocksAboveGround() {
        return mergeBlocksAboveGround;
    }

    public void setMergeBlocksAboveGround(boolean mergeBlocksAboveGround) {
        this.mergeBlocksAboveGround = mergeBlocksAboveGround;
    }

    public boolean isMergeBlocksUnderground() {
        return mergeBlocksUnderground;
    }

    public void setMergeBlocksUnderground(boolean mergeBlocksUnderground) {
        this.mergeBlocksUnderground = mergeBlocksUnderground;
    }

    /**
     * Whether to preserve biomes from the original map. Single flag for Hytale (the
     * Minecraft "above-ground / underground" split does not apply because Hytale biomes
     * are per-column, not per 3D cell).
     */
    public boolean isMergeBiomes() {
        return mergeBiomes;
    }

    public void setMergeBiomes(boolean mergeBiomes) {
        this.mergeBiomes = mergeBiomes;
    }

    public int getSurfaceMergeDepth() {
        return surfaceMergeDepth;
    }

    public void setSurfaceMergeDepth(int surfaceMergeDepth) {
        this.surfaceMergeDepth = surfaceMergeDepth;
    }

    public boolean isClearTrees() {
        return clearTrees;
    }

    public void setClearTrees(boolean clearTrees) {
        this.clearTrees = clearTrees;
    }

    public boolean isClearVegetation() {
        return clearVegetation;
    }

    public void setClearVegetation(boolean clearVegetation) {
        this.clearVegetation = clearVegetation;
    }

    public boolean isClearManMadeAboveGround() {
        return clearManMadeAboveGround;
    }

    public void setClearManMadeAboveGround(boolean clearManMadeAboveGround) {
        this.clearManMadeAboveGround = clearManMadeAboveGround;
    }

    public boolean isClearManMadeBelowGround() {
        return clearManMadeBelowGround;
    }

    public void setClearManMadeBelowGround(boolean clearManMadeBelowGround) {
        this.clearManMadeBelowGround = clearManMadeBelowGround;
    }

    // ── Sanity checks ─────────────────────────────────────────────────────────────────

    /**
     * Validate that the merge can proceed. Throws {@link InvalidMapException} or
     * {@link IllegalArgumentException} on failure. Analogous to
     * {@link org.pepsoft.worldpainter.merging.JavaWorldMerger#performSanityChecks()}.
     */
    public void performSanityChecks() {
        if (world.getImportedFrom() == null) {
            throw new InvalidMapException("World was not imported from an existing Hytale map; nothing to merge with");
        }
        if (!mapDir.isDirectory()) {
            throw new InvalidMapException(mapDir + " does not exist or is not a directory");
        }

        // Hytale supports only the Overworld for now
        Dimension surface = world.getDimension(NORMAL_DETAIL);
        if (surface == null) {
            throw new InvalidMapException("TalePainter world has no surface dimension to merge");
        }

        // Dimension height sanity check — re-exporting at a different height would corrupt
        // the original chunks we want to preserve metadata from
        if (surface.getMinHeight() != platform.minZ) {
            throw new InvalidMapException("Dimension " + surface.getName() + " has min height "
                + surface.getMinHeight() + " but Hytale platform expects " + platform.minZ);
        }
        if (surface.getMaxHeight() != platform.standardMaxHeight) {
            throw new InvalidMapException("Dimension " + surface.getName() + " has max height "
                + surface.getMaxHeight() + " but Hytale platform expects " + platform.standardMaxHeight);
        }
    }

    // ── Merge entry point ─────────────────────────────────────────────────────────────

    /**
     * Merge the TalePainter world into the existing Hytale map at {@link #getMapDir()}, backing
     * up the original to {@code backupDir}. After a successful call:
     * <ul>
     *   <li>{@code backupDir} contains the original save (rename, not copy).</li>
     *   <li>{@code mapDir} contains a freshly exported save with chunks regenerated from
     *   TalePainter tiles. Entities, block health, water tints, spawn metadata and prefab
     *   markers from the original chunks are merged in via the inherited
     *   {@link #mergeOriginalChunkData} hook.</li>
     *   <li>Any non-chunk files present in the backup but not regenerated by the exporter
     *   (player data, custom universe entries, etc.) are copied back to {@code mapDir}.</li>
     * </ul>
     */
    public Map<Integer, ChunkFactory.Stats> merge(File backupDir, ProgressReceiver progressReceiver)
            throws IOException, ProgressReceiver.OperationCancelled {
        logger.info("Merging world {} with Hytale map at {}", world.getName(), mapDir);

        performSanityChecks();

        Objects.requireNonNull(backupDir, "backupDir");
        if (backupDir.exists()) {
            throw new IllegalArgumentException("Backup directory already exists: " + backupDir);
        }

        // 1. Rename the existing map dir to backup. We do this BEFORE invoking the exporter so
        //    that the exporter's own "delete existing save dir" guard doesn't wipe the original
        //    chunks we still want to read from.
        if (!mapDir.renameTo(backupDir)) {
            throw new FileInUseException("Could not move " + mapDir + " to " + backupDir);
        }

        try {
            // 2. Wire the inherited original-chunk-store to the backup. mergeOriginalChunkData
            //    will then read entities / health / tints / spawn metadata / prefab markers from
            //    the backup chunks while writing new chunks into the fresh save.
            File backupInnerWorldDir = getInnerWorldDir(backupDir);
            if (new File(backupInnerWorldDir, CHUNKS_DIR).isDirectory()) {
                Dimension surface = world.getDimension(NORMAL_DETAIL);
                originalChunkStore = new HytaleChunkStore(
                        backupInnerWorldDir,
                        surface.getMinHeight(),
                        surface.getMaxHeight());
                logger.info("Opened backup chunk store at {} for round-trip merge", backupInnerWorldDir);
            } else {
                logger.warn("Backup at {} contains no chunks directory; merge will regenerate everything from scratch",
                        backupInnerWorldDir);
            }

            // 3. Run the inherited export against the freed mapDir slot. The exporter will:
            //    - mkdirs the save structure at mapDir
            //    - generate chunks from TalePainter tiles
            //    - call mergeOriginalChunkData per-chunk against our pre-set originalChunkStore
            //    - close the chunk store when it's done
            Map<Integer, ChunkFactory.Stats> stats = super.export(
                    mapDir.getParentFile(), mapDir.getName(), null, progressReceiver);

            // 4. Copy non-chunk files from the backup to the fresh save where the exporter
            //    didn't already write them. This preserves player data, custom universe
            //    contents, and any other user customisations that the exporter doesn't know
            //    how to regenerate. The chunks/ directories are deliberately skipped.
            copyPreservedFiles(backupDir, mapDir);

            return stats;
        } catch (RuntimeException | IOException | ProgressReceiver.OperationCancelled e) {
            // If anything fails, attempt to restore the original map dir from the backup so the
            // user isn't left with a half-written save. We swallow restore failures so the
            // original exception isn't masked.
            if (mapDir.exists()) {
                try {
                    // Move freshly-written (partial) save out of the way so the rename below succeeds.
                    File aborted = new File(mapDir.getParentFile(), mapDir.getName() + ".aborted-" + System.currentTimeMillis());
                    if (!mapDir.renameTo(aborted)) {
                        logger.warn("Could not move partial save aside to {}", aborted);
                    }
                } catch (RuntimeException restoreEx) {
                    logger.warn("Could not move partial save aside: {}", restoreEx.getMessage());
                }
            }
            if (backupDir.exists() && !mapDir.exists()) {
                if (!backupDir.renameTo(mapDir)) {
                    logger.warn("Could not restore backup at {} to original location {}", backupDir, mapDir);
                }
            }
            throw e;
        }
    }

    /**
     * Walk {@code backupDir} and copy any file or directory that the freshly exported
     * {@code mapDir} doesn't already contain. The {@code chunks/} directory inside
     * {@code universe/worlds/default/} is always skipped — the exporter is authoritative
     * for chunk content during a merge.
     */
    private void copyPreservedFiles(File backupDir, File mapDir) throws IOException {
        copyMissingRecursive(backupDir, mapDir);
    }

    private void copyMissingRecursive(File srcDir, File destDir) throws IOException {
        File[] children = srcDir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            // Skip the chunks/ directory inside universe/worlds/default; the exporter
            // is authoritative for chunk data on merge.
            if (isChunksDir(child)) {
                continue;
            }
            File dest = new File(destDir, child.getName());
            if (child.isFile()) {
                if (!dest.exists()) {
                    FileUtils.copyFileToDir(child, destDir);
                }
            } else if (child.isDirectory()) {
                if (!dest.exists()) {
                    FileUtils.copyDir(child, dest);
                } else {
                    // Dest dir already exists (the exporter created it); recurse so we copy
                    // any preserved files inside without clobbering exporter-written ones.
                    copyMissingRecursive(child, dest);
                }
            } else {
                logger.warn("Not copying {} from backup; not a regular file or directory", child);
            }
        }
    }

    private boolean isChunksDir(File candidate) {
        if (!candidate.isDirectory() || !candidate.getName().equals(CHUNKS_DIR)) {
            return false;
        }
        // Only treat as the chunks dir if it sits at <something>/universe/worlds/default/chunks
        File parent = candidate.getParentFile();
        if (parent == null || !parent.getName().equals(DEFAULT_WORLD)) {
            return false;
        }
        File worlds = parent.getParentFile();
        if (worlds == null || !worlds.getName().equals(WORLDS_DIR)) {
            return false;
        }
        File universe = worlds.getParentFile();
        return universe != null && universe.getName().equals(UNIVERSE_DIR);
    }

    private static File getInnerWorldDir(File saveDir) {
        return new File(new File(new File(saveDir, UNIVERSE_DIR), WORLDS_DIR), DEFAULT_WORLD);
    }

    // ── Per-chunk merge engine ────────────────────────────────────────────────────────

    /**
     * The default biome assigned to every column by {@link HytaleChunk}'s constructor.
     * Used to decide whether TalePainter actually painted a biome at a given column or
     * left the column at its default — only the latter case allows the original biome
     * to be preserved when {@link #isMergeBiomes() mergeBiomes} is true.
     */
    private static final String DEFAULT_BIOME = "Grassland";

    /**
     * Apply per-block merge overrides on top of the chunk TalePainter just generated.
     * Reads the matching chunk from {@link #originalChunkStore} (set up by
     * {@link #merge(File, ProgressReceiver)} to point at the backup) and replays the
     * decisions described in TP-59 Phase 2 per column:
     *
     * <ul>
     *   <li>If {@link #isReplaceChunks() replaceChunks}: ignore the original entirely.</li>
     *   <li>Above-ground band (y &gt; terrainHeight): if
     *       {@link #isMergeBlocksAboveGround() mergeBlocksAboveGround}, copy non-empty
     *       original blocks back in (skipping tree-related / vegetation / man-made blocks
     *       per the {@code clearTrees / clearVegetation / clearManMadeAboveGround}
     *       flags).</li>
     *   <li>Surface band (terrainHeight - surfaceMergeDepth + 1 .. terrainHeight): always
     *       keep TalePainter's blocks (authoritative).</li>
     *   <li>Below-ground band (y &lt; terrainHeight - surfaceMergeDepth + 1): if
     *       {@link #isMergeBlocksUnderground() mergeBlocksUnderground}, copy non-empty
     *       original blocks back in (skipping man-made blocks per
     *       {@code clearManMadeBelowGround}).</li>
     *   <li>Biome: if {@link #isMergeBiomes() mergeBiomes} and TalePainter left the
     *       column at the default biome, copy the original biome.</li>
     * </ul>
     *
     * <p>{@link HytaleBlockRegistry} does not currently expose an {@code isNatural(...)}
     * predicate (unlike {@code Material.natural} on the Minecraft side). The closest
     * existing classification is {@link HytaleBlockRegistry#isSurfaceOnlyBlock(String)},
     * which flags vegetation / leaves / decorations / saplings / corals / rubble /
     * crops as surface-only. We treat surface-only blocks as <em>natural</em> overlay
     * material, and use the {@link HytaleBlockRegistry.Category} of each block to
     * decide naturalness: SOIL / SAND / CLAY / SNOW_ICE / GRAVEL / ROCK / ORE /
     * CRYSTAL_GEM / WOOD_NATURAL / LEAVES / MOSS_BLOCKS / MOSS_VINES / FLUID and the
     * surface-only categories are natural; everything else (WOOD_PLANKS,
     * ROCK_CONSTRUCTION, CLOTH, HIVE, RUNIC, SPECIAL) is treated as man-made.
     */
    @Override
    protected void applyMergeOverrides(HytaleChunk chunk, int worldBlockX, int worldBlockZ, Tile tile) {
        if (replaceChunks) {
            // Wholesale replacement — TalePainter wins for every block & biome in the chunk.
            return;
        }
        if (originalChunkStore == null) {
            // Nothing to merge against (e.g. backup had no chunks dir). Treat as replace.
            return;
        }

        // Bail out fast if nothing the hook can do would change the chunk.
        if (!mergeBlocksAboveGround && !mergeBlocksUnderground && !mergeBiomes) {
            return;
        }

        // Look up the original chunk at this (pre-centering) world position. Same math
        // as mergeOriginalChunkData(): original Hytale chunks are 32 blocks wide and
        // worldBlockX/Z are pre-offset WorldPainter coordinates.
        final int origChunkX = worldBlockX >> 5;
        final int origChunkZ = worldBlockZ >> 5;
        final HytaleChunk originalChunk;
        try {
            originalChunk = (HytaleChunk) originalChunkStore.getChunk(origChunkX, origChunkZ);
        } catch (Exception e) {
            logger.debug("Could not read original chunk at {},{} for merge overrides: {}",
                    origChunkX, origChunkZ, e.getMessage());
            return;
        }
        if (originalChunk == null) {
            return;
        }

        final int chunkMaxHeight = chunk.getMaxHeight();
        final int chunkMinHeight = chunk.getMinHeight();
        final int depth = Math.max(1, surfaceMergeDepth);

        for (int localX = 0; localX < HytaleChunk.CHUNK_SIZE; localX++) {
            for (int localZ = 0; localZ < HytaleChunk.CHUNK_SIZE; localZ++) {
                final int worldX = worldBlockX + localX;
                final int worldZ = worldBlockZ + localZ;
                final int tileLocalX = worldX & 0x7F;
                final int tileLocalZ = worldZ & 0x7F;

                // Honor Void columns: TalePainter left the column empty on purpose;
                // don't reintroduce original blocks.
                if (tile.getBitLayerValue(org.pepsoft.worldpainter.layers.Void.INSTANCE, tileLocalX, tileLocalZ)) {
                    continue;
                }

                final int terrainHeight = tile.getIntHeight(tileLocalX, tileLocalZ);
                // Boundary between the surface band (kept as TalePainter wrote) and
                // the underground band: blocks at y >= surfaceBottom are surface,
                // blocks below are underground.
                final int surfaceBottom = terrainHeight - depth + 1;

                // ── Above-ground band: y > terrainHeight ──────────────────────
                if (mergeBlocksAboveGround) {
                    for (int y = terrainHeight + 1; y < chunkMaxHeight; y++) {
                        HytaleBlock orig = originalChunk.getHytaleBlock(localX, y, localZ);
                        if (orig == null || orig.isEmpty()) {
                            continue;
                        }
                        if (shouldSkipForAboveGroundClear(orig)) {
                            continue;
                        }
                        chunk.setHytaleBlock(localX, y, localZ, orig);
                    }
                }

                // ── Below-ground band: y < surfaceBottom ──────────────────────
                if (mergeBlocksUnderground) {
                    final int undergroundTop = Math.min(surfaceBottom - 1, chunkMaxHeight - 1);
                    for (int y = undergroundTop; y >= chunkMinHeight; y--) {
                        HytaleBlock orig = originalChunk.getHytaleBlock(localX, y, localZ);
                        if (orig == null || orig.isEmpty()) {
                            continue;
                        }
                        if (clearManMadeBelowGround && !isNatural(orig)) {
                            continue;
                        }
                        chunk.setHytaleBlock(localX, y, localZ, orig);
                    }
                }

                // ── Surface band (terrainHeight - depth + 1 .. terrainHeight) ──
                // Always authoritative — keep whatever TalePainter wrote. No-op.

                // ── Biome ─────────────────────────────────────────────────────
                if (mergeBiomes) {
                    String origBiome = originalChunk.getBiomeName(localX, localZ);
                    String newBiome = chunk.getBiomeName(localX, localZ);
                    if (origBiome != null && !origBiome.isEmpty()
                            && !DEFAULT_BIOME.equals(origBiome)
                            && (newBiome == null || DEFAULT_BIOME.equals(newBiome))) {
                        chunk.setBiomeName(localX, localZ, origBiome);
                    }
                }
            }
        }
    }

    /**
     * Returns true when an original above-ground block should be filtered out instead
     * of being copied back into the regenerated chunk. Mirrors the AND-of-flags
     * structure in {@link org.pepsoft.worldpainter.merging.JavaWorldMerger#processExistingChunk}:
     * {@code clearTrees} drops leaves/logs, {@code clearVegetation} drops surface-only
     * plants/flowers/decorations, {@code clearManMadeAboveGround} drops anything not
     * natural.
     */
    private boolean shouldSkipForAboveGroundClear(HytaleBlock block) {
        if (clearTrees && isTreeRelated(block)) {
            return true;
        }
        if (clearVegetation && HytaleBlockRegistry.isSurfaceOnlyBlock(block.id)) {
            return true;
        }
        if (clearManMadeAboveGround && !isNatural(block)) {
            return true;
        }
        return false;
    }

    /** Tree leaves and wood-natural (logs) form the "tree" group. */
    private static boolean isTreeRelated(HytaleBlock block) {
        HytaleBlockRegistry.Category cat = HytaleBlockRegistry.getCategoryForBlock(block.id);
        return cat == HytaleBlockRegistry.Category.LEAVES
                || cat == HytaleBlockRegistry.Category.WOOD_NATURAL;
    }

    /**
     * Classification predicate used in lieu of {@code Material.natural}. See
     * {@link #applyMergeOverrides} for the category mapping rationale. Unknown
     * blocks (category == null) are treated as natural — a conservative choice
     * that avoids accidentally clearing blocks the registry hasn't indexed.
     */
    private static boolean isNatural(HytaleBlock block) {
        if (block == null || block.isEmpty()) {
            return true;
        }
        HytaleBlockRegistry.Category cat = HytaleBlockRegistry.getCategoryForBlock(block.id);
        if (cat == null) {
            return true;
        }
        switch (cat) {
            // Man-made construction / fabricated:
            case ROCK_CONSTRUCTION:
            case WOOD_PLANKS:
            case CLOTH:
            case HIVE:
            case RUNIC:
            case SPECIAL:
                return false;
            // Everything else (raw rock, soil, ores, gems, natural wood, leaves,
            // vegetation, moss, fluids, etc.) is natural.
            default:
                return true;
        }
    }
}
