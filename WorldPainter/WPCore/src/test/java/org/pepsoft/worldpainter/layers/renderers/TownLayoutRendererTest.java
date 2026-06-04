package org.pepsoft.worldpainter.layers.renderers;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TownLayoutRendererTest {
    @Test
    public void setColumnsRenderMagenta() {
        TownLayoutRenderer renderer = new TownLayoutRenderer();
        assertEquals(0xFF00FF, renderer.getPixelColour(0, 0, 0x123456, true));
    }

    @Test
    public void unsetColumnsKeepUnderlyingColour() {
        TownLayoutRenderer renderer = new TownLayoutRenderer();
        assertEquals(0x123456, renderer.getPixelColour(0, 0, 0x123456, false));
    }
}
