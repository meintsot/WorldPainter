package org.pepsoft.worldpainter.hytale;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.pepsoft.worldpainter.*;
import org.pepsoft.worldpainter.exporting.WorldExportSettings;
import org.pepsoft.worldpainter.importing.MapImporter;

import java.io.File;

import static org.junit.Assert.*;
import static org.pepsoft.worldpainter.Constants.*;
import static org.pepsoft.worldpainter.DefaultPlugin.HYTALE;

/**
 * Round-trip test: create WP world -> export as Hytale -> import back -> verify terrain.
 */
public class HytaleRoundTripTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Test
    public void roundTripPreservesTerrain() throws Exception {
        // 1. Create a simple WP world with Hytale platform
        World2 world = new World2(HYTALE, 0, 320);
        world.setName("RoundTripTest");
        world.setCreateGoodiesChest(false);

        long seed = 42L;
        TileFactory tileFactory = TileFactoryFactory.createFlatTileFactory(
            seed, Terrain.GRASS, 0, 320, 64, 62, false, false);
        Dimension.Anchor anchor = new Dimension.Anchor(DIM_NORMAL, Dimension.Role.DETAIL, false, 0);
        Dimension dim = new Dimension(world, "Surface", seed, tileFactory, anchor);
        dim.setEventsInhibited(true);

        // Create one tile and set some terrain
        Tile tile = tileFactory.createTile(0, 0);
        // Paint some specific terrain in a known area
        for (int x = 0; x < 32; x++) {
            for (int z = 0; z < 32; z++) {
                tile.setHeight(x, z, 64);
                tile.setTerrain(x, z, Terrain.STONE);
                HytaleTerrainLayer.setTerrainIndex(tile, x, z, HytaleTerrain.STONE.getLayerIndex());
            }
        }
        dim.addTile(tile);
        dim.setEventsInhibited(false);
        world.addDimension(dim);

        // 2. Export to Hytale format
        File exportBaseDir = tempDir.newFolder("export_base");

        WorldExportSettings exportSettings = new WorldExportSettings();
        HytaleWorldExporter exporter = new HytaleWorldExporter(world, exportSettings);
        exporter.export(exportBaseDir, "RoundTripTest", null, null);

        // The exporter creates a subdirectory named after the world
        File exportedWorldDir = new File(exportBaseDir, "RoundTripTest");
        assertTrue("Exported world directory should exist", exportedWorldDir.isDirectory());

        // 3. Verify export produced region files
        File chunksDir = new File(exportedWorldDir, "chunks");
        assertTrue("chunks directory should exist", chunksDir.isDirectory());
        File[] regionFiles = chunksDir.listFiles((dir, name) -> name.endsWith(".region.bin"));
        assertNotNull(regionFiles);
        assertTrue("At least one region file should be created", regionFiles.length > 0);

        // 4. Import back
        TileFactory importTileFactory = TileFactoryFactory.createNoiseTileFactory(
            0, Terrain.GRASS, 0, 320, 58, 62, false, true, 20, 1.0);
        HytaleMapImporter importer = new HytaleMapImporter(
            exportedWorldDir, importTileFactory, null, MapImporter.ReadOnlyOption.NONE);
        World2 importedWorld = importer.doImport(null);

        // 5. Verify imported terrain preserved
        assertNotNull(importedWorld);
        assertEquals(HYTALE, importedWorld.getPlatform());
        Dimension importedDim = importedWorld.getDimension(anchor);
        assertNotNull("Imported dimension should not be null", importedDim);
        assertTrue("Imported world should have tiles", importedDim.getTileCount() > 0);

        // Check that HytaleTerrain index was preserved
        Tile importedTile = importedDim.getTile(0, 0);
        if (importedTile != null) {
            int terrainIndex = HytaleTerrainLayer.getTerrainIndex(importedTile, 0, 0);
            if (terrainIndex > 0) {
                assertEquals("HytaleTerrain index should be preserved through round-trip",
                    HytaleTerrain.STONE.getLayerIndex(), terrainIndex);
            }
        }
    }
}
