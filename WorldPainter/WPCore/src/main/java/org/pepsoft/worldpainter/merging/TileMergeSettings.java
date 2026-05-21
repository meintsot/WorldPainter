package org.pepsoft.worldpainter.merging;

/**
 * Settings for a tile-level merge of one TalePainter map into another
 * (TP-46 "Import Map and Merge"). Independent of {@link JavaWorldMerger} /
 * {@link org.pepsoft.worldpainter.hytale.export.HytaleWorldMerger} which
 * operate on game-save chunks, not WorldPainter tiles.
 */
public final class TileMergeSettings {

    public enum Side { NORTH, SOUTH, EAST, WEST }

    public enum OverlapPolicy {
        /** Abort the merge if any imported tile would land on an existing one. */
        REJECT,
        /** Imported tile wholesale replaces the existing tile at conflict points. */
        REPLACE,
        /** Combine the two tiles field-by-field per the {@code useImported*} flags. */
        MERGE
    }

    public final Side side;
    public final OverlapPolicy overlapPolicy;
    public final boolean useImportedHeights;
    public final boolean useImportedTerrain;
    public final boolean useImportedLayers;
    public final boolean useImportedBiomes;

    private TileMergeSettings(Side side, OverlapPolicy overlapPolicy,
                              boolean useImportedHeights, boolean useImportedTerrain,
                              boolean useImportedLayers, boolean useImportedBiomes) {
        this.side = side;
        this.overlapPolicy = overlapPolicy;
        this.useImportedHeights = useImportedHeights;
        this.useImportedTerrain = useImportedTerrain;
        this.useImportedLayers = useImportedLayers;
        this.useImportedBiomes = useImportedBiomes;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Side side = Side.EAST;
        private OverlapPolicy overlapPolicy = OverlapPolicy.REJECT;
        private boolean useImportedHeights = true;
        private boolean useImportedTerrain = true;
        private boolean useImportedLayers = true;
        private boolean useImportedBiomes = true;

        public Builder side(Side side) { this.side = side; return this; }
        public Builder overlapPolicy(OverlapPolicy p) { this.overlapPolicy = p; return this; }
        public Builder useImportedHeights(boolean v) { this.useImportedHeights = v; return this; }
        public Builder useImportedTerrain(boolean v) { this.useImportedTerrain = v; return this; }
        public Builder useImportedLayers(boolean v) { this.useImportedLayers = v; return this; }
        public Builder useImportedBiomes(boolean v) { this.useImportedBiomes = v; return this; }

        public TileMergeSettings build() {
            return new TileMergeSettings(side, overlapPolicy,
                useImportedHeights, useImportedTerrain, useImportedLayers, useImportedBiomes);
        }
    }
}
