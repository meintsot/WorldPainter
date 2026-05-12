package org.pepsoft.worldpainter.hytale;

import org.pepsoft.worldpainter.layers.Layer;

import java.io.ObjectStreamException;

/**
 * Built-in Hytale layer that procedurally scatters biome-appropriate plants
 * across painted pixels at export time. Bit-per-pixel: painted on/off. The
 * "which plants" decision lives in {@link HytaleAutoVegetationSettings},
 * attached to the dimension.
 */
public final class HytaleAutoVegetationLayer extends Layer {

    public static final HytaleAutoVegetationLayer INSTANCE = new HytaleAutoVegetationLayer();

    private HytaleAutoVegetationLayer() {
        super("HyAutoVeg", "Auto Vegetation",
                "Procedurally scatter plants based on the biome under each painted pixel (Hytale)",
                DataSize.BIT, false, 0);
    }

    private Object readResolve() throws ObjectStreamException {
        return INSTANCE;
    }

    private static final long serialVersionUID = 1L;
}
