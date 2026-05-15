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
import static org.pepsoft.minecraft.Constants.MC_OAK_LOG;

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

    /**
     * Regression for the dense-tree boundary scar: under high-density Bo2 placement
     * (gridX=1, density=100), trees centred near a region boundary used to receive
     * asymmetric {@code isRoom} verdicts (one region's chunks had prior trunks; the
     * other side fell back to AIR), which left a 1-block-wide deficit at the region
     * boundary column. After the fix, both regions should skip {@code isRoom} for
     * any tree whose footprint straddles a chunk they don't own, so the
     * boundary column has roughly the same tree-block density as its neighbours.
     */
    @Test
    public void denseBo2NearBoundaryHasSymmetricColumnDensity() throws Exception {
        WPObject trunk = new SolidTrunkObject("dense-trunk", new Point3i(3, 3, 5),
                new Point3i(-1, -1, 0));
        trunk.setAttribute(ATTRIBUTE_RANDOM_ROTATION, Boolean.FALSE);
        // Default collision mode (SOLID) so isRoom does collision checks.

        Bo2Layer layer = new Bo2Layer(new Bo2ObjectTube("dense-trunks", Collections.singletonList(trunk)),
                "Dense trunks across boundary", new Color(64, 160, 64));
        layer.setDensity(100);
        layer.setGridX(1);
        layer.setGridY(1);
        layer.setNoPhysics(false);

        World2 world = new World2(HYTALE, 0, 320);
        world.setName("TpDenseBo2");
        world.setCreateGoodiesChest(false);

        long seed = 1234L;
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

        File exportBaseDir = tempDir.newFolder("tp_dense_bo2_export");
        new HytaleWorldExporter(world, new WorldExportSettings())
                .export(exportBaseDir, "TpDenseBo2", null, null);

        File chunksDir = new File(new File(new File(new File(exportBaseDir, "TpDenseBo2"), "universe"),
                "worlds"), "default/chunks");

        // Count trunk blocks on each column near the X=0 boundary across a chunkZ slice.
        // Pick chunkZ=-1 so we look at Hytale Z in [-32..-1] which is in regionZ=-1 — but the
        // result needs to be readable regardless of which regionZ; just collect what's present.
        int[] cnts = new int[5];
        int[] wpX = {-3, -2, -1, 0, 1};
        for (int i = 0; i < wpX.length; i++) {
            cnts[i] = countTrunkBlocksAt(chunksDir, wpX[i]);
        }
        System.out.println("Trunk counts across boundary (WP X=-3,-2,-1,0,1): "
                + cnts[0] + ", " + cnts[1] + ", " + cnts[2] + ", " + cnts[3] + ", " + cnts[4]);

        // Boundary columns at WP X=-1 (region -1's last) and X=0 (region 0's first) must be
        // similar in density to their non-boundary neighbours. Without the fix the boundary
        // column was on the order of 10x sparser than its neighbours.
        int neighbourAvg = (cnts[0] + cnts[4]) / 2;
        assertTrue("WP X=-1 should have similar density to neighbours: got " + cnts[2]
                        + " vs neighbour avg " + neighbourAvg,
                cnts[2] >= neighbourAvg / 2);
        assertTrue("WP X=0 should have similar density to neighbours: got " + cnts[3]
                        + " vs neighbour avg " + neighbourAvg,
                cnts[3] >= neighbourAvg / 2);
    }

    private static int countTrunkBlocksAt(File chunksDir, int worldX) throws Exception {
        int total = 0;
        for (int worldZ = -128; worldZ < 128; worldZ++) {
            int chunkX = Math.floorDiv(worldX, HytaleChunk.CHUNK_SIZE);
            int chunkZ = Math.floorDiv(worldZ, HytaleChunk.CHUNK_SIZE);
            int regionX = Math.floorDiv(chunkX, 32);
            int regionZ = Math.floorDiv(chunkZ, 32);
            int localChunkX = Math.floorMod(chunkX, 32);
            int localChunkZ = Math.floorMod(chunkZ, 32);
            int localX = Math.floorMod(worldX, HytaleChunk.CHUNK_SIZE);
            int localZ = Math.floorMod(worldZ, HytaleChunk.CHUNK_SIZE);

            File regionFile = new File(chunksDir, HytaleRegionFile.getRegionFileName(regionX, regionZ));
            if (!regionFile.isFile()) continue;
            HytaleRegionFile region = new HytaleRegionFile(regionFile.toPath());
            try {
                region.open();
                if (!region.hasChunk(localChunkX, localChunkZ)) continue;
                HytaleChunk chunk = region.readChunk(localChunkX, localChunkZ, 0, HytaleChunk.DEFAULT_MAX_HEIGHT);
                for (int y = TERRAIN_HEIGHT + 1; y < TERRAIN_HEIGHT + 6; y++) {
                    HytaleBlock b = chunk.getHytaleBlock(localX, y, localZ);
                    if (b != null && !b.isEmpty() && b.id != null
                            && b.id.equals(HytaleBlockMapping.HY_OAK_LOG)) {
                        total++;
                    }
                }
            } finally {
                region.close();
            }
        }
        return total;
    }

    private static final class SolidTrunkObject extends NamedObjectWithAttributes {
        private SolidTrunkObject(String name, Point3i dimensions, Point3i offset) {
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
            return Material.get(MC_OAK_LOG);
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
        public SolidTrunkObject clone() {
            return (SolidTrunkObject) super.clone();
        }

        private final Point3i dimensions;

        private static final long serialVersionUID = 1L;
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
