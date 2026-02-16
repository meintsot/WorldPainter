package org.pepsoft.worldpainter.painting;

import org.pepsoft.worldpainter.ColourScheme;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.hytale.HytaleTerrain;
import org.pepsoft.worldpainter.hytale.HytaleTerrainLayer;

import java.awt.*;
import java.awt.image.BufferedImage;

import static org.pepsoft.worldpainter.Constants.TILE_SIZE_BITS;
import static org.pepsoft.worldpainter.Constants.TILE_SIZE_MASK;

/**
 * A Paint for Hytale terrains that stores BOTH a fallback Minecraft Terrain
 * and the exact HytaleTerrain index in a per-pixel layer.
 */
public final class HytaleTerrainPaint extends AbstractPaint {

    private final Terrain fallbackTerrain;
    private final HytaleTerrain hytaleTerrain;
    private final int layerIndex;

    public HytaleTerrainPaint(Terrain fallbackTerrain, HytaleTerrain hytaleTerrain) {
        this.fallbackTerrain = fallbackTerrain;
        this.hytaleTerrain = hytaleTerrain;
        this.layerIndex = hytaleTerrain.getLayerIndex();
    }

    @Override
    public String getId() {
        return "HytaleTerrain/" + layerIndex;
    }

    public int getLayerIndex() {
        return layerIndex;
    }

    public HytaleTerrain getHytaleTerrain() {
        return hytaleTerrain;
    }

    @Override
    public void apply(Dimension dimension, int centreX, int centreY, float dynamicLevel) {
        if (brush.getRadius() == 0) {
            applyPixel(dimension, centreX, centreY);
            return;
        }
        final Rectangle boundingBox = brush.getBoundingBox();
        final int x1 = centreX + boundingBox.x, y1 = centreY + boundingBox.y,
                  x2 = x1 + boundingBox.width - 1, y2 = y1 + boundingBox.height - 1;
        final int tileX1 = x1 >> TILE_SIZE_BITS, tileY1 = y1 >> TILE_SIZE_BITS,
                  tileX2 = x2 >> TILE_SIZE_BITS, tileY2 = y2 >> TILE_SIZE_BITS;
        if ((tileX1 == tileX2) && (tileY1 == tileY2)) {
            final Tile tile = dimension.getTileForEditing(tileX1, tileY1);
            if (tile == null) return;
            final int x1InTile = x1 & TILE_SIZE_MASK, y1InTile = y1 & TILE_SIZE_MASK,
                      x2InTile = x2 & TILE_SIZE_MASK, y2InTile = y2 & TILE_SIZE_MASK;
            final int tileXInWorld = tileX1 << TILE_SIZE_BITS, tileYInWorld = tileY1 << TILE_SIZE_BITS;
            if (dither) {
                for (int y = y1InTile; y <= y2InTile; y++) {
                    for (int x = x1InTile; x <= x2InTile; x++) {
                        final float strength = dynamicLevel * getStrength(centreX, centreY, tileXInWorld + x, tileYInWorld + y);
                        if ((strength > 0.95f) || (Math.random() < strength)) {
                            applyToTile(tile, x, y);
                        }
                    }
                }
            } else {
                for (int y = y1InTile; y <= y2InTile; y++) {
                    for (int x = x1InTile; x <= x2InTile; x++) {
                        final float strength = dynamicLevel * getFullStrength(centreX, centreY, tileXInWorld + x, tileYInWorld + y);
                        if (strength > 0.75f) {
                            applyToTile(tile, x, y);
                        }
                    }
                }
            }
        } else {
            if (dither) {
                for (int y = y1; y <= y2; y++) {
                    for (int x = x1; x <= x2; x++) {
                        final float strength = dynamicLevel * getStrength(centreX, centreY, x, y);
                        if ((strength > 0.95f) || (Math.random() < strength)) {
                            applyPixel(dimension, x, y);
                        }
                    }
                }
            } else {
                for (int y = y1; y <= y2; y++) {
                    for (int x = x1; x <= x2; x++) {
                        final float strength = dynamicLevel * getFullStrength(centreX, centreY, x, y);
                        if (strength > 0.75f) {
                            applyPixel(dimension, x, y);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void remove(Dimension dimension, int centreX, int centreY, float dynamicLevel) {
        if (brush.getRadius() == 0) {
            removePixel(dimension, centreX, centreY);
            return;
        }
        final Rectangle boundingBox = brush.getBoundingBox();
        final int x1 = centreX + boundingBox.x, y1 = centreY + boundingBox.y,
                  x2 = x1 + boundingBox.width - 1, y2 = y1 + boundingBox.height - 1;
        if (dither) {
            for (int y = y1; y <= y2; y++) {
                for (int x = x1; x <= x2; x++) {
                    final float strength = dynamicLevel * getFullStrength(centreX, centreY, x, y);
                    if ((strength > 0.95f) || (Math.random() < strength)) {
                        dimension.applyTheme(x, y);
                    }
                }
            }
        } else {
            for (int y = y1; y <= y2; y++) {
                for (int x = x1; x <= x2; x++) {
                    final float strength = dynamicLevel * getFullStrength(centreX, centreY, x, y);
                    if (strength > 0.75f) {
                        dimension.applyTheme(x, y);
                    }
                }
            }
        }
    }

    @Override
    public void applyPixel(Dimension dimension, int x, int y) {
        dimension.setTerrainAt(x, y, fallbackTerrain);
        final int tileX = x >> TILE_SIZE_BITS, tileY = y >> TILE_SIZE_BITS;
        final Tile tile = dimension.getTileForEditing(tileX, tileY);
        if (tile != null) {
            HytaleTerrainLayer.setTerrainIndex(tile, x & TILE_SIZE_MASK, y & TILE_SIZE_MASK, layerIndex);
        }
    }

    @Override
    public void removePixel(Dimension dimension, int x, int y) {
        dimension.applyTheme(x, y);
    }

    @Override
    public BufferedImage getIcon(ColourScheme colourScheme) {
        return hytaleTerrain.getScaledIcon(16, colourScheme);
    }

    private void applyToTile(Tile tile, int x, int y) {
        tile.setTerrain(x, y, fallbackTerrain);
        HytaleTerrainLayer.setTerrainIndex(tile, x, y, layerIndex);
    }
}
