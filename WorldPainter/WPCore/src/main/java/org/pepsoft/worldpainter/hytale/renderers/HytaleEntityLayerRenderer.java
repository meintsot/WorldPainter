package org.pepsoft.worldpainter.hytale.renderers;

import org.pepsoft.util.ColourUtils;
import org.pepsoft.worldpainter.hytale.HytaleEntityLayer;
import org.pepsoft.worldpainter.layers.renderers.NibbleLayerRenderer;

/**
 * Renderer for the {@link HytaleEntityLayer} in the 2D editor view.
 * Blends the entity density colour over the underlying terrain colour
 * with intensity proportional to the nibble value.
 */
public class HytaleEntityLayerRenderer implements NibbleLayerRenderer {
    @Override
    public int getPixelColour(int x, int y, int underlyingColour, int value) {
        if (value > 0 && value < HytaleEntityLayer.DENSITY_COLORS.length) {
            int argb = HytaleEntityLayer.DENSITY_COLORS[value];
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
