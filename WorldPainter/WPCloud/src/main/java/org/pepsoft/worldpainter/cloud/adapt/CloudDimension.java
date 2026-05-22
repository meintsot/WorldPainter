package org.pepsoft.worldpainter.cloud.adapt;

import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.TileFactory;
import org.pepsoft.worldpainter.World2;

import java.awt.Point;

/**
 * Cloud-backed {@link Dimension}. Overrides {@link #getTile(int, int)} and
 * {@link #getTileForEditing(int, int)} to lazy-load cloud tiles via the injected
 * {@link CloudTileLoader} on first access. Loaded tiles are added to the parent class's tile
 * map so subsequent accesses go through {@code Dimension}'s normal cache path.
 */
public final class CloudDimension extends Dimension {

    private final transient CloudTileLoader loader;

    public CloudDimension(World2 world, String name, long minecraftSeed, TileFactory tileFactory,
                          Anchor anchor, CloudTileLoader loader) {
        super(world, name, minecraftSeed, tileFactory, anchor);
        this.loader = loader;
    }

    @Override
    public Tile getTile(int x, int y) {
        Tile cached = super.getTile(x, y);
        if (cached != null) {
            return cached;
        }
        Tile loaded = loader.load(x, y);
        if (loaded != null) {
            addTile(loaded);
        }
        return loaded;
    }

    @Override
    public Tile getTile(Point coords) {
        return getTile(coords.x, coords.y);
    }

    @Override
    public Tile getTileForEditing(int x, int y) {
        return getTile(x, y);
    }

    @Override
    public Tile getTileForEditing(Point coords) {
        return getTileForEditing(coords.x, coords.y);
    }
}
