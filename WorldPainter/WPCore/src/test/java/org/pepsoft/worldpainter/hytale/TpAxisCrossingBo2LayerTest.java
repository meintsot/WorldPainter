package org.pepsoft.worldpainter.hytale;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.pepsoft.minecraft.Entity;
import org.pepsoft.minecraft.Material;
import org.pepsoft.minecraft.TileEntity;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.Tile;
import org.pepsoft.worldpainter.TileFactory;
import org.pepsoft.worldpainter.TileFactoryFactory;
import org.pepsoft.worldpainter.World2;
import org.pepsoft.worldpainter.exporting.WorldExportSettings;
import org.pepsoft.worldpainter.layers.Bo2Layer;
import org.pepsoft.worldpainter.layers.bo2.Bo2ObjectTube;
import org.pepsoft.worldpainter.objects.NamedObjectWithAttributes;
import org.pepsoft.worldpainter.objects.WPObject;

import javax.vecmath.Point3i;
import java.awt.Color;
import java.io.File;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.pepsoft.minecraft.Constants.MC_OAK_LEAVES;
import static org.pepsoft.worldpainter.Constants.DIM_NORMAL;
import static org.pepsoft.worldpainter.DefaultPlugin.HYTALE;
import static org.pepsoft.worldpainter.objects.WPObject.ATTRIBUTE_COLLISION_MODE;
import static org.pepsoft.worldpainter.objects.WPObject.ATTRIBUTE_RANDOM_ROTATION;

/**
 * Regression test for custom objects crossing the centred Hytale X/Z axes.
 * Those axes are also Hytale region boundaries for a world with tiles on both
 * sides of the origin. A Bo2 object anchored at (0,0) must be written by the
 * neighbouring region passes as one continuous object, not as four clipped
 * quadrants.
 */
public class TpAxisCrossingBo2LayerTest {
    private static final int TERRAIN_HEIGHT = 64;
    private static final int OBJECT_Y = TERRAIN_HEIGHT + 1;

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Test
    public void bo2ObjectCrossingZeroAxesIsNotClippedAtRegionBoundary() throws Exception {
        WPObject object = new FlatCanopyObject("axis-crossing-canopy", new Point3i(33, 33, 1),
                new Point3i(-16, -16, 0));
        object.setAttribute(ATTRIBUTE_RANDOM_ROTATION, Boolean.FALSE);
        object.setAttribute(ATTRIBUTE_COLLISION_MODE, WPObject.COLLISION_MODE_NONE);

        Bo2Layer layer = new Bo2Layer(new Bo2ObjectTube("axis-crossing", Collections.singletonList(object)),
                "Axis crossing object", new Color(64, 160, 64));
        layer.setDensity(1);
        layer.setGridX(64);
        layer.setGridY(64);
        layer.setNoPhysics(true);

        World2 world = new World2(HYTALE, 0, 320);
        world.setName("TpAxisCrossingBo2");
        world.setCreateGoodiesChest(false);

        long seed = 42L;
        TileFactory tileFactory = TileFactoryFactory.createFlatTileFactory(
                seed, Terrain.GRASS, 0, 320, TERRAIN_HEIGHT, 62, false, false);
        Dimension dimension = new Dimension(world, "Surface", seed, tileFactory,
                new Dimension.Anchor(DIM_NORMAL, Dimension.Role.DETAIL, false, 0));
        dimension.setEventsInhibited(true);

        for (int tileX = -1; tileX <= 0; tileX++) {
            for (int tileZ = -1; tileZ <= 0; tileZ++) {
                Tile tile = tileFactory.createTile(tileX, tileZ);
                for (int x = 0; x < 128; x++) {
                    for (int z = 0; z < 128; z++) {
                        tile.setHeight(x, z, TERRAIN_HEIGHT);
                        tile.setTerrain(x, z, Terrain.GRASS);
                        HytaleTerrainLayer.setTerrainIndex(tile, x, z, HytaleTerrain.GRASS.getLayerIndex());
                        tile.setLayerValue(layer, x, z, 15);
                    }
                }
                dimension.addTile(tile);
            }
        }

        dimension.setEventsInhibited(false);
        world.addDimension(dimension);

        File exportBaseDir = tempDir.newFolder("tp_axis_crossing_bo2_export");
        new HytaleWorldExporter(world, new WorldExportSettings())
                .export(exportBaseDir, "TpAxisCrossingBo2", null, null);

        File chunksDir = new File(new File(new File(new File(exportBaseDir, "TpAxisCrossingBo2"), "universe"),
                "worlds"), "default/chunks");
        assertObjectBlock(chunksDir, -16, 0);
        assertObjectBlock(chunksDir, -1, 0);
        assertObjectBlock(chunksDir, 0, 0);
        assertObjectBlock(chunksDir, 16, 0);
        assertObjectBlock(chunksDir, 0, -16);
        assertObjectBlock(chunksDir, 0, -1);
        assertObjectBlock(chunksDir, 0, 16);
        assertObjectBlock(chunksDir, -16, -16);
        assertObjectBlock(chunksDir, 16, 16);
    }

    @Test
    public void bo2ObjectWhoseOriginIsFarFromRegionEdgeStillContinuesIntoNextRegion() throws Exception {
        WPObject object = new FlatCanopyObject("wide-axis-crossing-canopy", new Point3i(400, 1, 1),
                new Point3i(0, 0, 0));
        object.setAttribute(ATTRIBUTE_RANDOM_ROTATION, Boolean.FALSE);
        object.setAttribute(ATTRIBUTE_COLLISION_MODE, WPObject.COLLISION_MODE_NONE);

        Bo2Layer layer = new Bo2Layer(new Bo2ObjectTube("wide-axis-crossing", Collections.singletonList(object)),
                "Wide axis crossing object", new Color(64, 160, 64));
        layer.setDensity(1);
        layer.setGridX(704);
        layer.setGridY(1);
        layer.setNoPhysics(true);

        World2 world = new World2(HYTALE, 0, 320);
        world.setName("TpWideAxisCrossingBo2");
        world.setCreateGoodiesChest(false);

        long seed = 42L;
        TileFactory tileFactory = TileFactoryFactory.createFlatTileFactory(
                seed, Terrain.GRASS, 0, 320, TERRAIN_HEIGHT, 62, false, false);
        Dimension dimension = new Dimension(world, "Surface", seed, tileFactory,
                new Dimension.Anchor(DIM_NORMAL, Dimension.Role.DETAIL, false, 0));
        dimension.setEventsInhibited(true);

        for (int tileX = -8; tileX <= 8; tileX++) {
            Tile tile = tileFactory.createTile(tileX, 0);
            for (int x = 0; x < 128; x++) {
                for (int z = 0; z < 128; z++) {
                    tile.setHeight(x, z, TERRAIN_HEIGHT);
                    tile.setTerrain(x, z, Terrain.GRASS);
                    HytaleTerrainLayer.setTerrainIndex(tile, x, z, HytaleTerrain.GRASS.getLayerIndex());
                    tile.setLayerValue(layer, x, z, 15);
                }
            }
            dimension.addTile(tile);
        }

        dimension.setEventsInhibited(false);
        world.addDimension(dimension);

        File exportBaseDir = tempDir.newFolder("tp_wide_axis_crossing_bo2_export");
        new HytaleWorldExporter(world, new WorldExportSettings())
                .export(exportBaseDir, "TpWideAxisCrossingBo2", null, null);

        File chunksDir = new File(new File(new File(new File(exportBaseDir, "TpWideAxisCrossingBo2"), "universe"),
                "worlds"), "default/chunks");
        assertObjectBlock(chunksDir, 704, 0);
        assertObjectBlock(chunksDir, 1023, 0);
        assertObjectBlock(chunksDir, 1024, 0);
        assertObjectBlock(chunksDir, 1103, 0);
    }

    private static void assertObjectBlock(File chunksDir, int worldX, int worldZ) throws Exception {
        int chunkX = Math.floorDiv(worldX, HytaleChunk.CHUNK_SIZE);
        int chunkZ = Math.floorDiv(worldZ, HytaleChunk.CHUNK_SIZE);
        int regionX = Math.floorDiv(chunkX, 32);
        int regionZ = Math.floorDiv(chunkZ, 32);
        int localChunkX = Math.floorMod(chunkX, 32);
        int localChunkZ = Math.floorMod(chunkZ, 32);
        int localX = Math.floorMod(worldX, HytaleChunk.CHUNK_SIZE);
        int localZ = Math.floorMod(worldZ, HytaleChunk.CHUNK_SIZE);

        File regionFile = new File(chunksDir, HytaleRegionFile.getRegionFileName(regionX, regionZ));
        assertTrue("Expected region file for " + worldX + "," + worldZ + ": " + regionFile, regionFile.isFile());

        HytaleRegionFile region = new HytaleRegionFile(regionFile.toPath());
        try {
            region.open();
            assertTrue("Expected chunk for " + worldX + "," + worldZ,
                    region.hasChunk(localChunkX, localChunkZ));
            HytaleChunk chunk = region.readChunk(localChunkX, localChunkZ, 0, HytaleChunk.DEFAULT_MAX_HEIGHT);
            assertNotNull("Expected readable chunk for " + worldX + "," + worldZ, chunk);
            HytaleBlock block = chunk.getHytaleBlock(localX, OBJECT_Y, localZ);
            assertNotNull("Missing object block at " + worldX + "," + worldZ, block);
            assertEquals("Unexpected object block at " + worldX + "," + worldZ,
                    HytaleBlockMapping.HY_OAK_LEAVES, block.id);
        } finally {
            region.close();
        }
    }

    private static final class FlatCanopyObject extends NamedObjectWithAttributes {
        private FlatCanopyObject(String name, Point3i dimensions, Point3i offset) {
            super(name);
            this.dimensions = dimensions;
            setAttribute(WPObject.ATTRIBUTE_OFFSET, offset);
        }

        @Override
        public Point3i getDimensions() {
            return new Point3i(dimensions);
        }

        @Override
        public Material getMaterial(int x, int y, int z) {
            return Material.get(MC_OAK_LEAVES);
        }

        @Override
        public boolean getMask(int x, int y, int z) {
            return true;
        }

        @Override
        public List<Entity> getEntities() {
            return null;
        }

        @Override
        public List<TileEntity> getTileEntities() {
            return null;
        }

        @Override
        public FlatCanopyObject clone() {
            return (FlatCanopyObject) super.clone();
        }

        private final Point3i dimensions;

        private static final long serialVersionUID = 1L;
    }
}
