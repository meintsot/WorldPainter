package org.pepsoft.worldpainter.hytale.renderers;

import org.pepsoft.util.ColourUtils;
import org.pepsoft.worldpainter.layers.renderers.ByteLayerRenderer;

/**
 * Renderer for the {@link org.pepsoft.worldpainter.hytale.HytaleEnvironmentLayer}
 * in the 2D editor view. Renders a subtle tint to indicate non-default environments.
 */
public class HytaleEnvironmentLayerRenderer implements ByteLayerRenderer {

    private static final int OVERLAY_ALPHA = 48;
    private static final int OVERLAY_RGB = 0x60B0FF; // light sky blue

    @Override
    public int getPixelColour(int x, int y, int underlyingColour, int value) {
        if (value > 0 && value < 255) {
            return ColourUtils.mix(OVERLAY_RGB, underlyingColour, OVERLAY_ALPHA);
        }
        return underlyingColour;
    }
}
