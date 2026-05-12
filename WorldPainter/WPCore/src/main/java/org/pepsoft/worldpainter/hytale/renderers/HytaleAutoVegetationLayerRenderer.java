package org.pepsoft.worldpainter.hytale.renderers;

import org.pepsoft.worldpainter.layers.renderers.BitLayerRenderer;

/**
 * Renders {@link org.pepsoft.worldpainter.hytale.HytaleAutoVegetationLayer}
 * in the map view: a translucent leafy-green tint over painted pixels,
 * blended at ~40% with the underlying terrain colour.
 */
public final class HytaleAutoVegetationLayerRenderer implements BitLayerRenderer {

    /** Leafy-green tint colour, ARGB-style components. */
    private static final int TINT_R = 0x4F;
    private static final int TINT_G = 0xAE;
    private static final int TINT_B = 0x3C;
    /** Tint strength out of 255 (~40%). */
    private static final int ALPHA = 102;

    @Override
    public int getPixelColour(int x, int y, int underlyingColour, boolean value) {
        if (!value) {
            return underlyingColour;
        }
        int red   = (underlyingColour & 0xFF0000) >> 16;
        int green = (underlyingColour & 0x00FF00) >>  8;
        int blue  =  underlyingColour & 0x0000FF;
        red   = (red   * (255 - ALPHA) + TINT_R * ALPHA) / 255;
        green = (green * (255 - ALPHA) + TINT_G * ALPHA) / 255;
        blue  = (blue  * (255 - ALPHA) + TINT_B * ALPHA) / 255;
        return (red << 16) | (green << 8) | blue;
    }
}
