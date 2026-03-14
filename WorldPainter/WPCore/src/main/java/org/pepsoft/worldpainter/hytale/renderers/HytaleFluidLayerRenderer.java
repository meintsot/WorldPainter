package org.pepsoft.worldpainter.hytale.renderers;

import org.pepsoft.util.ColourUtils;
import org.pepsoft.worldpainter.hytale.HytaleFluidLayer;
import org.pepsoft.worldpainter.layers.renderers.NibbleLayerRenderer;

/**
 * Renderer for the {@link HytaleFluidLayer} in the 2D editor view.
 * Blends the fluid preset colour over the underlying terrain colour.
 */
public class HytaleFluidLayerRenderer implements NibbleLayerRenderer {
    @Override
    public int getPixelColour(int x, int y, int underlyingColour, int value) {
        if (value > 0 && value < HytaleFluidLayer.FLUID_COLORS.length) {
            int argb = HytaleFluidLayer.FLUID_COLORS[value];
            int alpha = (argb >>> 24) & 0xFF;
            int rgb = argb & 0x00FFFFFF;
            if (alpha == 0) {
                return underlyingColour;
            }
            return ColourUtils.mix(rgb, underlyingColour, alpha);
        }
        return underlyingColour;
    }
}
