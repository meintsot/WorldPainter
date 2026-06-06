package org.pepsoft.worldpainter.layers;

/**
 * Marks the X-Z footprint of an imported town plan. On export, {@code TownLayoutExporter} replaces
 * the terrain surface block at each footprint column (flush with the ground, not a pillar above it).
 * A single on/off (BIT) layer.
 */
public class TownLayout extends Layer {
    private TownLayout() {
        super("org.pepsoft.TownLayout", "Town Layout",
                "Footprint of an imported town plan, exported as marker blocks", DataSize.BIT, true, 66);
    }

    public static final TownLayout INSTANCE = new TownLayout();

    private static final long serialVersionUID = 1L;
}
