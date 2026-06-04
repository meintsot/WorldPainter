package org.pepsoft.worldpainter.layers.renderers;

/**
 * Renders the {@code TownLayout} footprint as a high-visibility magenta on the map. This is an
 * editor-only colour and is independent of the block the layer exports.
 */
public class TownLayoutRenderer implements BitLayerRenderer {
    @Override
    public int getPixelColour(int x, int y, int underlyingColour, boolean value) {
        return value ? 0xFF00FF : underlyingColour;
    }
}
