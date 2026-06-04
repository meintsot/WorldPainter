package org.pepsoft.worldpainter.layers;

/**
 * Marks the X-Z footprint of an imported town plan. Exported as marker blocks at the terrain
 * surface by {@code TownLayoutExporter}. A single on/off (BIT) layer.
 */
public class TownLayout extends Layer {
    private TownLayout() {
        super("org.pepsoft.TownLayout", "Town Layout",
                "Footprint of an imported town plan, exported as marker blocks", DataSize.BIT, true, 66);
    }

    public static final TownLayout INSTANCE = new TownLayout();

    private static final long serialVersionUID = 1L;
}
