package org.pepsoft.worldpainter.hytale.export;

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
import org.pepsoft.minecraft.MinecraftCoords;
import org.pepsoft.worldpainter.hytale.chunk.HytaleChunk;
import org.pepsoft.worldpainter.hytale.chunk.HytaleChunkStore;
import org.pepsoft.worldpainter.merging.InvalidMapException;
import org.pepsoft.worldpainter.merging.WorldMerger;
import org.pepsoft.worldpainter.util.FileInUseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Point;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
public class HytaleWorldMerger extends HytaleWorldExporter implements WorldMerger {

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

    /** Cooperatively-cancellable abort flag honoured by {@link #applyMergeOverrides}. */
    private volatile boolean aborted = false;

    /** Block offset resolved once per merge (before any rename) and reused for every chunk. */
    private Point resolvedBlockOffset;

    /** Collected merge warnings, surfaced via {@link #getWarnings()}. */
    private final List<String> warnings = new ArrayList<>();

    public HytaleWorldMerger(World2 world, WorldExportSettings exportSettings, File mapDir, Platform platform) {
        super(world, exportSettings);
        Objects.requireNonNull(mapDir, "mapDir");
        if (!HYTALE.equals(platform)) {
            throw new IllegalArgumentException("HytaleWorldMerger only supports the Hytale platform; got " + platform);
        }
        if (!mapDir.isDirectory()) {
            throw new IllegalArgumentException(mapDir + " does not exist or is not a directory");
        }
        // mapDir is the WORLD directory (the "default" folder containing config.json + chunks/),
        // matching what HytalePlatformProvider.identifyMap() returns. Verify the layout that
        // the importer/identifyMap also requires.
        if (!new File(mapDir, "config.json").isFile() || !new File(mapDir, CHUNKS_DIR).isDirectory()) {
            throw new IllegalArgumentException(mapDir + " is not a Hytale world directory "
                + "(must contain config.json and a chunks/ folder)");
        }
        // Also verify the parent chain is a Hytale save layout (.../universe/worlds/<name>);
        // we walk up to find the save root in merge().
        if (computeSaveRoot(mapDir) == null) {
            throw new IllegalArgumentException(mapDir + " is not nested in the expected Hytale "
                + "save layout (.../universe/worlds/<name>)");
        }
        this.mapDir = mapDir;
    }

    @Override
    public File getMapDir() {
        return mapDir;
    }

    /**
     * Plan (but do not create) a backup directory next to the SAVE ROOT. Hytale's save layout
     * places the world directory (the one the user picks) three levels under the save root
     * ({@code <saveRoot>/universe/worlds/default}); backing up the entire save root keeps the
     * config files, players directory, and any custom contents together with the chunks.
     *
     * <p>Overrides the {@link HytaleWorldExporter#selectBackupDir(File)} default of {@code null},
     * which would otherwise mean "no backup needed" and is wrong for a merge.
     */
    @Override
    public File selectBackupDir(File mapDir) throws IOException {
        File saveRoot = computeSaveRoot(mapDir);
        if (saveRoot == null) {
            throw new IOException(mapDir + " is not nested in a Hytale save layout");
        }
        return new File(saveRoot.getParentFile(), saveRoot.getName() + ".backup-" + System.currentTimeMillis());
    }

    /** Request that an in-progress merge stop at the next safe checkpoint. */
    public void abort() {
        this.aborted = true;
    }

    @Override
    public boolean isAborted() {
        return aborted;
    }

    /**
     * Returns a newline-separated summary of any warnings recorded during the most recent
     * {@link #merge}, or {@code null} when there are none. Matches the contract of
     * {@link org.pepsoft.worldpainter.merging.JavaWorldMerger#getWarnings()}.
     */
    @Override
    public String getWarnings() {
        if (warnings.isEmpty()) {
            return null;
        }
        return String.join("\n", warnings);
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
        // Note: we do NOT require world.getImportedFrom() != null. App.merge() already
        // surfaces a "world was not imported, are you sure?" confirmation before opening
        // the merge dialog, mirroring how Minecraft's merge works — Talepainter users can
        // merge an arbitrary painted world into any existing Hytale save.
        if (!mapDir.isDirectory()) {
            throw new InvalidMapException(mapDir + " does not exist or is not a directory");
        }

        // Hytale supports only the Overworld for now
        Dimension surface = world.getDimension(NORMAL_DETAIL);
        if (surface == null) {
            throw new InvalidMapException("TalePainter world has no surface dimension to merge");
        }

        // Vanilla Hytale's BlockChunk hard-codes `new BlockSection[10]`, so the dimension
        // height must be exactly 320 (= 10 * SECTION_HEIGHT). An earlier version of this
        // check accepted any positive multiple of 32 based on the optimistic "modded servers
        // support taller worlds" comment in HytaleChunk.java, but vanilla Hytale rejects
        // anything other than 10 sections at chunk-load time:
        //
        //   java.lang.ArrayIndexOutOfBoundsException: Index 10 out of bounds for length 10
        //     at BlockChunk.loadFromHolder(BlockChunk.java:290)
        //
        // The crash happens silently per chunk during preload, so worlds with a 1024-height
        // dimension look like they exported fine until a player tries to join. Fail loudly
        // here instead of letting the export produce 32-section chunks that won't load.
        if (surface.getMinHeight() != 0) {
            throw new InvalidMapException("Dimension " + surface.getName() + " has min height "
                + surface.getMinHeight() + " but Hytale requires 0");
        }
        if (surface.getMaxHeight() != HytaleChunk.DEFAULT_MAX_HEIGHT) {
            throw new InvalidMapException("Dimension " + surface.getName() + " has max height "
                + surface.getMaxHeight() + " but Hytale requires " + HytaleChunk.DEFAULT_MAX_HEIGHT
                + ". Change it in Dimensions → Properties → " + surface.getName()
                + " → Maximum height.");
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
    @Override
    public void merge(File backupDir, ProgressReceiver progressReceiver)
            throws IOException, ProgressReceiver.OperationCancelled {
        logger.info("Merging world {} with Hytale map at {}", world.getName(), mapDir);

        // Reset transient run state in case the same merger instance is reused.
        aborted = false;
        resolvedBlockOffset = null;
        warnings.clear();

        performSanityChecks();

        // Resolve the block offset ONCE, now, while mapDir still holds the existing chunks and
        // config.json (the full-merge path renames mapDir to a backup further down). Both the
        // in-place and full-merge paths reach determineBlockOffset, which returns this value.
        // Throws InvalidMapException (aborting before any modification) if alignment can't be found.
        this.resolvedBlockOffset = resolveBlockOffset();

        // Fast path: a tile selection means "apply only these tiles to the already-exported
        // world". Patch them in place — overwrite just the selected tiles' chunks and touch
        // nothing else — instead of renaming the whole save to a backup and rebuilding it. Cost
        // is proportional to the selection, not the world size. No backup is made; the merge
        // dialog warns the user first.
        if (worldExportSettings.getTilesToExport() != null) {
            logger.info("Fast in-place selective merge of {} tile(s) into {}",
                    worldExportSettings.getTilesToExport().size(), mapDir);
            mergeSelectedTilesInPlace(progressReceiver);
            return;
        }

        Objects.requireNonNull(backupDir, "backupDir");
        if (backupDir.exists()) {
            throw new IllegalArgumentException("Backup directory already exists: " + backupDir);
        }
        // The user picked the inner world dir (the "default" folder); the merger operates at
        // the save-root level so config.json, players/, etc. are backed up alongside chunks.
        File saveRoot = computeSaveRoot(mapDir);
        if (saveRoot == null) {
            throw new InvalidMapException(mapDir + " is not nested in a Hytale save layout");
        }

        // 1. Rename the SAVE ROOT to backup. We do this BEFORE invoking the exporter so the
        //    exporter's own "delete existing save dir" guard doesn't wipe the original chunks
        //    we still want to read from.
        if (!saveRoot.renameTo(backupDir)) {
            throw new FileInUseException("Could not move " + saveRoot + " to " + backupDir);
        }

        try {
            // 2. Wire the inherited original-chunk-store to the backup's inner world dir.
            //    mergeOriginalChunkData will then read entities / health / tints / spawn
            //    metadata / prefab markers from the backup chunks while writing new chunks
            //    into the fresh save.
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

            // 3. Run the inherited export against the freed save-root slot. The exporter will:
            //    - mkdirs the save structure at saveRoot
            //    - generate chunks from TalePainter tiles
            //    - call mergeOriginalChunkData per-chunk against our pre-set originalChunkStore
            //    - close the chunk store when it's done
            super.export(saveRoot.getParentFile(), saveRoot.getName(), null, progressReceiver);

            // 4. Copy any original chunks that lived outside TalePainter's tile coverage from
            //    the backup into the fresh save. The exporter only writes regions/chunks that
            //    overlap TalePainter tiles, so without this step any chunks the original save
            //    had farther out would simply be lost. The merger reproduces the original map's
            //    block offset (see determineBlockOffset()) so the original chunk coordinates
            //    line up exactly with the fresh save.
            preserveUntouchedOriginalChunks(saveRoot, backupDir);

            // 5. Copy non-chunk files from the backup to the fresh save where the exporter
            //    didn't already write them. This preserves player data, custom universe
            //    contents, and any other user customisations that the exporter doesn't know
            //    how to regenerate. The chunks/ directories are deliberately skipped.
            copyPreservedFiles(backupDir, saveRoot);
        } catch (RuntimeException | IOException | ProgressReceiver.OperationCancelled e) {
            // If anything fails, attempt to restore the original save root from the backup so
            // the user isn't left with a half-written save. We swallow restore failures so the
            // original exception isn't masked.
            if (saveRoot.exists()) {
                try {
                    // Move freshly-written (partial) save out of the way so the rename below succeeds.
                    File aborted = new File(saveRoot.getParentFile(), saveRoot.getName() + ".aborted-" + System.currentTimeMillis());
                    if (!saveRoot.renameTo(aborted)) {
                        logger.warn("Could not move partial save aside to {}", aborted);
                    }
                } catch (RuntimeException restoreEx) {
                    logger.warn("Could not move partial save aside: {}", restoreEx.getMessage());
                }
            }
            if (backupDir.exists() && !saveRoot.exists()) {
                if (!backupDir.renameTo(saveRoot)) {
                    logger.warn("Could not restore backup at {} to original location {}", backupDir, saveRoot);
                }
            }
            throw e;
        }
    }

    /**
     * Apply only the active tile selection to the existing world in place. Overwrites just the
     * selected tiles' chunks and leaves every other chunk and region file untouched on disk; no
     * rename, no backup, no save-structure rebuild. Reads each original chunk from the live world
     * (for entity / block-health / biome / block merge) before overwriting it.
     */
    private void mergeSelectedTilesInPlace(ProgressReceiver progressReceiver)
            throws IOException, ProgressReceiver.OperationCancelled {
        Dimension surface = world.getDimension(NORMAL_DETAIL);
        // Read originals from the LIVE world. exportDimension() leaves a pre-set originalChunkStore
        // untouched (see HytaleWorldExporter.openOriginalChunkStore) and closes it when done.
        originalChunkStore = new HytaleChunkStore(mapDir, surface.getMinHeight(), surface.getMaxHeight());
        try {
            exportSelectedTilesInPlace(mapDir, progressReceiver);
        } finally {
            // Defensive: exportDimension() closes the store on success, but if it throws
            // (cancellation, OOM, region failure) the open read handles on the live region
            // files would leak. closeOriginalChunkStore() is null-safe and idempotent.
            closeOriginalChunkStore();
        }
    }

    /**
     * Walk up from a world dir to find the save root. Returns {@code null} if the parent
     * chain doesn't match the Hytale layout {@code <saveRoot>/universe/worlds/<world>}.
     */
    private static File computeSaveRoot(File worldDir) {
        File worldsDir = worldDir.getParentFile();
        if (worldsDir == null || !WORLDS_DIR.equals(worldsDir.getName())) {
            return null;
        }
        File universeDir = worldsDir.getParentFile();
        if (universeDir == null || !UNIVERSE_DIR.equals(universeDir.getName())) {
            return null;
        }
        return universeDir.getParentFile();
    }

    /**
     * Reproduce the block offset the <em>existing</em> map was written with, so regenerated
     * chunks land on top of the chunks preserved from the backup (via
     * {@link #preserveUntouchedOriginalChunks}) instead of in a separate, shifted copy of the
     * world.
     *
     * <ul>
     *   <li><b>Imported map</b> ({@code world.getImportedFrom() != null}):
     *       {@link org.pepsoft.worldpainter.hytale.imports.HytaleMapImporter} maps native Hytale
     *       chunk coordinates onto WorldPainter tiles 1:1 with no offset, so the round-trip merge
     *       must not shift anything — return {@code (0,0)}, exactly the original behaviour.</li>
     *   <li><b>Painted-from-scratch map</b>: the original full export centered the terrain around
     *       the origin ({@link HytaleWorldExporter#isCenteringTerrain()} {@code == true}),
     *       computing the offset over <em>all</em> tiles. Reproduce that same offset here so the
     *       regenerated tiles align with the preserved chunks. (Assumes the world's tile bounds
     *       are unchanged since the export, which holds when only existing tiles were repainted.)</li>
     * </ul>
     *
     * <p>This replaces the earlier {@code isCenteringTerrain() == false} override, which
     * incorrectly assumed the existing map was always written without centering — true for
     * imported maps but not for maps produced by a fresh (centered) export, where it left the
     * regenerated tiles offset from the rest of the world.
     */
    /** Today's offset behavior: (0,0) for imported maps, else center the current tile set. */
    private Point heuristicOffset() {
        if (world.getImportedFrom() != null) {
            return new Point(0, 0);
        }
        return HytaleWorldExporter.centeringOffset(world.getDimension(NORMAL_DETAIL).getTileCoords());
    }

    /**
     * Resolve the block offset to write the merge with, reading the existing map at {@link #mapDir}.
     * MUST be called before any backup rename (while mapDir still holds the chunks and config.json).
     * Order: stored sidecar (authoritative) -> best of {spawn recovery, centering heuristic} validated
     * by region coverage -> abort if nothing clears {@link #COVERAGE_THRESHOLD}.
     */
    Point resolveBlockOffset() {
        final Point sidecar = HytaleExportMetadata.readBlockOffset(mapDir);
        if (sidecar != null) {
            logger.info("Using stored export offset {} from sidecar in {}", sidecar, mapDir);
            return sidecar;
        }
        final Set<Point> existingRegions = readExistingRegionCoords(mapDir);
        if (existingRegions.isEmpty()) {
            return heuristicOffset();   // nothing to align to (degenerate / empty map)
        }
        final Set<Point> allTiles = world.getDimension(NORMAL_DETAIL).getTileCoords();
        final Point oSpawn = recoverOffsetFromSpawn(mapDir);
        final Point oHeuristic = heuristicOffset();
        // Evaluate spawn recovery first so it wins ties (it is exact when the spawn is unchanged).
        final java.util.List<Point> candidates = (oSpawn != null)
                ? java.util.Arrays.asList(oSpawn, oHeuristic)
                : java.util.Collections.singletonList(oHeuristic);
        Point best = null;
        double bestCoverage = -1.0;
        for (Point candidate : candidates) {
            final double cov = coverage(allTiles, candidate, existingRegions);
            if (cov > bestCoverage) {
                bestCoverage = cov;
                best = candidate;
            }
        }
        if (bestCoverage >= COVERAGE_THRESHOLD) {
            logger.info("Resolved export offset {} (matches {}% of existing regions) for merge into {}",
                    best, Math.round(bestCoverage * 100), mapDir);
            return best;
        }
        throw new InvalidMapException("Could not determine the original block alignment of the existing "
                + "Hytale map (best match " + Math.round(bestCoverage * 100) + "% of existing regions). "
                + "Aborting so tiles are not misplaced. This map was exported before TalePainter stored its "
                + "export offset, and spawn-based recovery did not line up — re-check the world's spawn point, "
                + "or do a full export to reset alignment.");
    }

    @Override
    protected Point determineBlockOffset(Dimension dimension, Set<Point> exportedTileCoords) {
        // merge() resolves the offset once (before any rename) and caches it here. When that hasn't
        // run (e.g. determineBlockOffset called directly in a unit test), fall back to the heuristic.
        return (resolvedBlockOffset != null) ? resolvedBlockOffset : heuristicOffset();
    }

    /**
     * Copy any chunks that existed in the original save (now living in the backup) but
     * that the fresh export did NOT write into the new save. These are chunks outside
     * TalePainter's tile coverage — the exporter only emits regions/chunks that overlap
     * a painted tile, so without this step the merger would silently lose every chunk
     * that lived beyond TalePainter's bounds (e.g. an imported 14×14 tile world that
     * originally covered a much larger Hytale save).
     *
     * <p>The merger reproduces the original map's block offset (see
     * {@link #determineBlockOffset(Dimension, Set)}), so the original chunk's {@code (x, z)}
     * maps 1:1 onto the new save — copying the chunk verbatim is correct.
     */
    private void preserveUntouchedOriginalChunks(File saveRoot, File backupDir) throws IOException {
        File backupInnerWorldDir = getInnerWorldDir(backupDir);
        if (!new File(backupInnerWorldDir, CHUNKS_DIR).isDirectory()) {
            return; // No backup chunks to preserve (e.g. brand-new save).
        }
        Dimension surface = world.getDimension(NORMAL_DETAIL);
        if (surface == null) {
            return;
        }
        File freshInnerWorldDir = getInnerWorldDir(saveRoot);
        int minHeight = surface.getMinHeight();
        int maxHeight = surface.getMaxHeight();
        try (HytaleChunkStore backupStore = new HytaleChunkStore(backupInnerWorldDir, minHeight, maxHeight);
             HytaleChunkStore freshStore = new HytaleChunkStore(freshInnerWorldDir, minHeight, maxHeight)) {
            int copied = 0;
            for (MinecraftCoords coords : backupStore.getChunkCoords()) {
                if (freshStore.isChunkPresent(coords.x, coords.z)) {
                    continue; // TalePainter wrote a chunk here; don't clobber.
                }
                HytaleChunk originalChunk;
                try {
                    originalChunk = (HytaleChunk) backupStore.getChunk(coords.x, coords.z);
                } catch (RuntimeException e) {
                    logger.warn("Could not read backup chunk at ({},{}) during merge preservation: {}",
                            coords.x, coords.z, e.getMessage());
                    continue;
                }
                if (originalChunk == null) {
                    continue;
                }
                freshStore.saveChunk(originalChunk);
                copied++;
            }
            freshStore.flush();
            if (copied > 0) {
                logger.info("Preserved {} untouched original chunks from backup", copied);
            }
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
        if (aborted) {
            // Cooperative cancellation: bail without touching this chunk. The chunk
            // TalePainter generated will still be written (which is fine; the user is
            // tearing things down anyway and the backup is intact).
            return;
        }
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

    // ── Offset-recovery helpers (Task 2) ─────────────────────────────────────────────

    /** Coverage threshold below which the resolved offset is rejected (abort rather than misplace). */
    private static final double COVERAGE_THRESHOLD = 0.9;

    /**
     * Read the region coordinates present in {@code <worldDir>/chunks} (files named
     * {@code "<x>.<z>.region.bin"}). Used to validate a candidate block offset against the chunks
     * already in the existing map.
     */
    static Set<Point> readExistingRegionCoords(File worldDir) {
        final Set<Point> coords = new HashSet<>();
        final File chunksDir = new File(worldDir, CHUNKS_DIR);
        final File[] files = chunksDir.listFiles((d, n) -> n.endsWith(".region.bin"));
        if (files == null) {
            return coords;
        }
        for (File f : files) {
            final String[] parts = f.getName().split("\\.");
            if (parts.length >= 3) {
                try {
                    coords.add(new Point(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
                } catch (NumberFormatException ignored) {
                    // skip non-coordinate filenames
                }
            }
        }
        return coords;
    }

    /**
     * Recover the offset the existing map was exported with from its spawn point. The export wrote
     * {@code saveSpawn = world.getSpawnPoint() + blockOffset} (HytaleWorldConfigWriter), so
     * {@code blockOffset = saveSpawn - world.getSpawnPoint()}. Returns {@code null} when the world
     * has no spawn point or the existing {@code config.json} has no usable {@code SpawnProvider.SpawnPoint}.
     */
    Point recoverOffsetFromSpawn(File worldDir) {
        final Point wpSpawn = world.getSpawnPoint();
        if (wpSpawn == null) {
            return null;
        }
        final File configFile = new File(worldDir, "config.json");
        if (!configFile.isFile()) {
            return null;
        }
        try {
            final String text = new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8);
            final JsonObject root = JsonParser.parseString(text).getAsJsonObject();
            if (!root.has("SpawnProvider")) {
                return null;
            }
            final JsonObject sp = root.getAsJsonObject("SpawnProvider");
            if (!sp.has("SpawnPoint")) {
                return null;
            }
            final JsonObject pt = sp.getAsJsonObject("SpawnPoint");
            if ((!pt.has("X")) || (!pt.has("Z"))) {
                return null;
            }
            final int saveX = (int) Math.round(pt.get("X").getAsDouble());
            final int saveZ = (int) Math.round(pt.get("Z").getAsDouble());
            return new Point(saveX - wpSpawn.x, saveZ - wpSpawn.y);
        } catch (Exception e) {
            logger.warn("Could not recover offset from spawn in {}: {}", configFile, e.getMessage());
            return null;
        }
    }

    /**
     * Fraction of {@code existingRegions} that are "explained" by some tile in {@code tileCoords}
     * under {@code offset}: i.e. the tile, once offset, falls in that region. A correct offset lands
     * the unchanged tiles back on the regions the export wrote (coverage near 1.0); a drifted offset
     * scores lower. Returns 1.0 for an empty {@code existingRegions} (nothing to validate against).
     */
    static double coverage(Set<Point> tileCoords, Point offset, Set<Point> existingRegions) {
        if (existingRegions.isEmpty()) {
            return 1.0;
        }
        final Set<Point> hit = new HashSet<>();
        for (Point t : tileCoords) {
            final int bx0 = (t.x * 128) + offset.x;
            final int bz0 = (t.y * 128) + offset.y;
            // region = chunk >> 5 = block >> 10. A 128-block tile may straddle a 1024-block boundary.
            final int rx0 = bx0 >> 10, rx1 = (bx0 + 127) >> 10;
            final int rz0 = bz0 >> 10, rz1 = (bz0 + 127) >> 10;
            for (int rx = rx0; rx <= rx1; rx++) {
                for (int rz = rz0; rz <= rz1; rz++) {
                    final Point r = new Point(rx, rz);
                    if (existingRegions.contains(r)) {
                        hit.add(r);
                    }
                }
            }
        }
        return (double) hit.size() / existingRegions.size();
    }
}
