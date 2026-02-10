/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.pepsoft.worldpainter.layers.renderers;

import org.pepsoft.util.ColourUtils;
import org.pepsoft.worldpainter.BiomeScheme;
import org.pepsoft.worldpainter.ColourScheme;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.biomeschemes.CustomBiome;
import org.pepsoft.worldpainter.biomeschemes.CustomBiomeManager;
import org.pepsoft.worldpainter.biomeschemes.StaticBiomeInfo;
import org.pepsoft.worldpainter.util.BiomeUtils;

import java.awt.image.BufferedImage;
import java.util.List;

import static java.awt.image.BufferedImage.TYPE_INT_RGB;

/**
 *
 * @author pepijn
 */
public class BiomeRenderer implements ByteLayerRenderer, DimensionAwareRenderer {
    public BiomeRenderer(CustomBiomeManager customBiomeManager, ColourScheme colourScheme) {
        this.customBiomeManager = customBiomeManager;
        this.colourScheme = colourScheme;
        reloadPatterns(StaticBiomeInfo.INSTANCE);
    }
    
    @Override
    public int getPixelColour(int x, int y, int underlyingColour, int value) {
        if ((value != 255) && (patterns[value] != null)) {
            final int rgb = patterns[value].getRGB(x & 0xf, y & 0xf);
            if ((rgb & 0xff000000) != 0) {
                return ColourUtils.mix(underlyingColour, rgb);
            }
        }
        return underlyingColour;
    }

    @Override
    public void setDimension(Dimension dimension) {
        final BiomeScheme desiredScheme = (dimension != null)
                ? BiomeUtils.getBiomeScheme(dimension.getWorld().getPlatform())
                : StaticBiomeInfo.INSTANCE;
        if (desiredScheme != biomeInfo) {
            reloadPatterns(desiredScheme);
        }
    }

    private void reloadPatterns(BiomeScheme scheme) {
        biomeInfo = scheme;
        patterns = new BufferedImage[255];
        for (int i = 0; i < 255; i++) {
            if (biomeInfo.isBiomePresent(i)) {
                patterns[i] = createPattern(i);
            }
        }
        if (customBiomeManager != null) {
            final List<CustomBiome> customBiomes = customBiomeManager.getCustomBiomes();
            for (CustomBiome customBiome : customBiomes) {
                final int id = customBiome.getId();
                if ((id >= 0) && (id < patterns.length) && (patterns[id] == null)) {
                    final BufferedImage pattern = customBiome.getPattern();
                    patterns[id] = (pattern != null) ? pattern : createSolidPattern(customBiome.getColour());
                }
            }
        }
    }

    private BufferedImage createPattern(int biomeId) {
        final boolean[][] pattern = biomeInfo.getPattern(biomeId);
        final int colour = biomeInfo.getColour(biomeId, colourScheme);
        final BufferedImage image = new BufferedImage(16, 16, TYPE_INT_RGB);
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                if ((pattern != null) && pattern[x][y]) {
                    image.setRGB(x, y, BLACK);
                } else {
                    image.setRGB(x, y, colour);
                }
            }
        }
        return image;
    }

    private BufferedImage createSolidPattern(int colour) {
        final BufferedImage image = new BufferedImage(16, 16, TYPE_INT_RGB);
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                image.setRGB(x, y, colour);
            }
        }
        return image;
    }

    private BufferedImage[] patterns;
    private final CustomBiomeManager customBiomeManager;
    private final ColourScheme colourScheme;
    private BiomeScheme biomeInfo;

    private static final int BLACK = 0;
}
