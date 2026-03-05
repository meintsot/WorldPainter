package org.pepsoft.worldpainter.hytale.renderers;

import org.pepsoft.util.ColourUtils;
import org.pepsoft.worldpainter.hytale.HytalePrefabLayer;
import org.pepsoft.worldpainter.layers.renderers.NibbleLayerRenderer;

/**
 * Renderer for the {@link HytalePrefabLayer} in the 2D editor view.
 * Blends the prefab category colour over the underlying terrain colour.
 */
public class HytalePrefabLayerRenderer implements NibbleLayerRenderer {
    @Override
    public int getPixelColour(int x, int y, int underlyingColour, int value) {
        if (value > 0 && value < HytalePrefabLayer.PREFAB_COLORS.length) {
            int argb = HytalePrefabLayer.PREFAB_COLORS[value];
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
