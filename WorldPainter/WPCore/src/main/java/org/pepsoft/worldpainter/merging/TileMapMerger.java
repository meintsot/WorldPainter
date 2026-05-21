package org.pepsoft.worldpainter.merging;

import org.pepsoft.worldpainter.CoordinateTransform;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Tile;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.pepsoft.worldpainter.Constants.TILE_SIZE;

/**
 * Tile-level merger for TP-46 ("Import Map and Merge"). Takes a source TalePainter
 * dimension and pastes its tiles into a target dimension on a chosen side, with a
 * configurable policy for what happens at tiles that would overlap.
 *
 * <p>Pure logic, no UI, no I/O. The caller is responsible for reading the source
 * world from disk and obtaining its surface dimension before calling
 * {@link #merge(Dimension, Dimension, TileMergeSettings)}.</p>
 */
public final class TileMapMerger {

    private TileMapMerger() {
        // Static-only utility class.
    }

    public static final class Result {
        public final int tilesAdded;
        public final int tilesReplaced;
        public final int tilesMerged;
        public final int tilesSkipped;

        private Result(int added, int replaced, int merged, int skipped) {
            this.tilesAdded = added;
            this.tilesReplaced = replaced;
            this.tilesMerged = merged;
            this.tilesSkipped = skipped;
        }

        @Override
        public String toString() {
            return "Result{added=" + tilesAdded + ", replaced=" + tilesReplaced
                + ", merged=" + tilesMerged + ", skipped=" + tilesSkipped + "}";
        }
    }

    /**
     * Compute where the source dimension's tile region ends up in the target's
     * tile coordinate space when placed on the requested side. Exposed so the
     * dialog can pre-compute the overlap count and show it to the user before
     * the merge actually runs.
     */
    public static Point computeOffset(Dimension target, Dimension source, TileMergeSettings.Side side) {
        Rectangle t = target.getExtent();
        Rectangle s = source.getExtent();
        int tMaxX = t.x + t.width - 1;
        int tMaxY = t.y + t.height - 1;
        int sMaxX = s.x + s.width - 1;
        int sMaxY = s.y + s.height - 1;
        switch (side) {
            case EAST:
                return new Point((tMaxX + 1) - s.x, t.y - s.y);
            case WEST:
                return new Point((t.x - 1) - sMaxX, t.y - s.y);
            case NORTH:
                return new Point(t.x - s.x, (t.y - 1) - sMaxY);
            case SOUTH:
                return new Point(t.x - s.x, (tMaxY + 1) - s.y);
            default:
                throw new IllegalArgumentException("Unknown side: " + side);
        }
    }

    /**
     * Tile coordinates (in the target's space) where an imported tile would
     * collide with a tile that already exists on the target.
     */
    public static Set<Point> findOverlappingCoords(Dimension target, Dimension source, Point offset) {
        Set<Point> overlaps = new HashSet<>();
        for (Tile srcTile : source.getTiles()) {
            Point candidate = new Point(srcTile.getX() + offset.x, srcTile.getY() + offset.y);
            if (target.getTile(candidate) != null) {
                overlaps.add(candidate);
            }
        }
        return Collections.unmodifiableSet(overlaps);
    }

    public static Result merge(Dimension target, Dimension source, TileMergeSettings settings) {
        if ((target.getMinHeight() != source.getMinHeight())
                || (target.getMaxHeight() != source.getMaxHeight())) {
            throw new IllegalArgumentException("Cannot merge: height bounds differ (target ["
                + target.getMinHeight() + ", " + target.getMaxHeight() + "), source ["
                + source.getMinHeight() + ", " + source.getMaxHeight() + "))");
        }
        return mergeAt(target, source, settings, computeOffset(target, source, settings.side));
    }

    /**
     * Same as {@link #merge(Dimension, Dimension, TileMergeSettings)} but uses an
     * explicit tile-coordinate offset instead of deriving one from the chosen side.
     * Exposed so tests can exercise the overlap-policy code paths directly — the
     * auto-computed side offset by construction never produces overlaps, so the
     * policy branches are otherwise unreachable from the public {@code merge}.
     */
    public static Result mergeAt(Dimension target, Dimension source, TileMergeSettings settings, Point offset) {
        if ((target.getMinHeight() != source.getMinHeight())
                || (target.getMaxHeight() != source.getMaxHeight())) {
            throw new IllegalArgumentException("Cannot merge: height bounds differ (target ["
                + target.getMinHeight() + ", " + target.getMaxHeight() + "), source ["
                + source.getMinHeight() + ", " + source.getMaxHeight() + "))");
        }

        Set<Point> overlaps = findOverlappingCoords(target, source, offset);

        if (settings.overlapPolicy == TileMergeSettings.OverlapPolicy.REJECT && !overlaps.isEmpty()) {
            throw new IllegalStateException(overlaps.size()
                + " imported tile(s) would overlap existing tiles; aborting per REJECT policy.");
        }

        int added = 0, replaced = 0, merged = 0, skipped = 0;
        // Snapshot the source tiles to avoid concurrent-modification issues if the
        // source dimension somehow shares ownership with the target.
        List<Tile> sourceTiles = new ArrayList<>(source.getTiles());

        boolean wasInhibited = target.isEventsInhibited();
        target.setEventsInhibited(true);
        try {
            for (Tile srcTile : sourceTiles) {
                Point dest = new Point(srcTile.getX() + offset.x, srcTile.getY() + offset.y);
                boolean isOverlap = overlaps.contains(dest);
                if (isOverlap) {
                    switch (settings.overlapPolicy) {
                        case REPLACE: {
                            target.removeTile(dest);
                            target.addTile(translatedClone(srcTile, offset));
                            replaced++;
                            break;
                        }
                        case MERGE: {
                            Tile existing = target.getTileForEditing(dest);
                            Tile translated = translatedClone(srcTile, offset);
                            existing.absorbFrom(translated,
                                settings.useImportedHeights,
                                settings.useImportedTerrain,
                                settings.useImportedLayers,
                                settings.useImportedBiomes);
                            merged++;
                            break;
                        }
                        default:
                            // REJECT was handled above; anything else here means we somehow
                            // ended up with an overlap under a policy that shouldn't have
                            // allowed it — skip defensively.
                            skipped++;
                            break;
                    }
                } else {
                    target.addTile(translatedClone(srcTile, offset));
                    added++;
                }
            }
        } finally {
            target.setEventsInhibited(wasInhibited);
        }

        return new Result(added, replaced, merged, skipped);
    }

    private static Tile translatedClone(Tile sourceTile, Point offset) {
        CoordinateTransform translate = CoordinateTransform.getTranslatingInstance(
            offset.x * TILE_SIZE, offset.y * TILE_SIZE);
        return sourceTile.transform(translate);
    }
}
