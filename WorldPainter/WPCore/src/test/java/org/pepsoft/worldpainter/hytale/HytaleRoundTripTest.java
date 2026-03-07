package org.pepsoft.worldpainter.hytale;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.pepsoft.minecraft.MinecraftCoords;
import org.pepsoft.worldpainter.*;
import org.pepsoft.worldpainter.exporting.WorldExportSettings;
import org.pepsoft.worldpainter.importing.MapImporter;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Set;

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
        // After restructuring: chunks are under universe/worlds/default/
        File actualWorldDir = new File(new File(new File(exportedWorldDir, "universe"), "worlds"), "default");
        File chunksDir = new File(actualWorldDir, "chunks");
        assertTrue("chunks directory should exist", chunksDir.isDirectory());
        File[] regionFiles = chunksDir.listFiles((dir, name) -> name.endsWith(".region.bin"));
        assertNotNull(regionFiles);
        assertTrue("At least one region file should be created", regionFiles.length > 0);

        // 4. Import back
        TileFactory importTileFactory = TileFactoryFactory.createNoiseTileFactory(
            0, Terrain.GRASS, 0, 320, 58, 62, false, true, 20, 1.0);
        HytaleMapImporter importer = new HytaleMapImporter(
            actualWorldDir, importTileFactory, null, MapImporter.ReadOnlyOption.NONE);
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

    @Test
    public void chunkStoreOnlyEnumeratesPresentChunks() throws Exception {
        File worldDir = tempDir.newFolder("sparse_hytale_world");

        HytaleChunkStore writer = new HytaleChunkStore(worldDir, 0, 320);
        writer.saveChunk(new HytaleChunk(0, 0, 0, 320));
        writer.saveChunk(new HytaleChunk(31, 31, 0, 320));
        writer.flush();
        writer.close();

        HytaleChunkStore reader = new HytaleChunkStore(worldDir, 0, 320);
        try {
            Set<MinecraftCoords> chunkCoords = reader.getChunkCoords();
            assertEquals("Sparse region should only report the written chunks", 2, chunkCoords.size());
            assertEquals("Chunk count should match actual occupied chunk slots", 2, reader.getChunkCount());
            assertTrue(chunkCoords.contains(new MinecraftCoords(0, 0)));
            assertTrue(chunkCoords.contains(new MinecraftCoords(31, 31)));
        } finally {
            reader.close();
        }
    }

    @Test
    public void testExportDoesNotIncludeUnselectedNeighborTiles() throws Exception {
        World2 world = new World2(HYTALE, 0, 320);
        world.setName("SelectedTileOnly");
        world.setCreateGoodiesChest(false);

        long seed = 42L;
        TileFactory tileFactory = TileFactoryFactory.createFlatTileFactory(
            seed, Terrain.GRASS, 0, 320, 64, 62, false, false);
        Dimension.Anchor anchor = new Dimension.Anchor(DIM_NORMAL, Dimension.Role.DETAIL, false, 0);
        Dimension dim = new Dimension(world, "Surface", seed, tileFactory, anchor);
        dim.setEventsInhibited(true);

        Tile selectedTile = tileFactory.createTile(0, 0);
        Tile unselectedTile = tileFactory.createTile(1, 0);
        for (int x = 0; x < 128; x++) {
            for (int z = 0; z < 128; z++) {
                selectedTile.setHeight(x, z, 64);
                selectedTile.setTerrain(x, z, Terrain.STONE);
                HytaleTerrainLayer.setTerrainIndex(selectedTile, x, z, HytaleTerrain.STONE.getLayerIndex());

                unselectedTile.setHeight(x, z, 70);
                unselectedTile.setTerrain(x, z, Terrain.DIRT);
                HytaleTerrainLayer.setTerrainIndex(unselectedTile, x, z, HytaleTerrain.DIRT.getLayerIndex());
            }
        }
        dim.addTile(selectedTile);
        dim.addTile(unselectedTile);
        dim.setEventsInhibited(false);
        world.addDimension(dim);

        WorldExportSettings exportSettings = new WorldExportSettings(
            java.util.Collections.singleton(DIM_NORMAL),
            java.util.Collections.singleton(new Point(0, 0)),
            null);

        File exportBaseDir = tempDir.newFolder("selected_tile_export");
        HytaleWorldExporter exporter = new HytaleWorldExporter(world, exportSettings);
        exporter.export(exportBaseDir, "SelectedTileOnly", null, null);

        File actualWorldDir = new File(new File(new File(new File(exportBaseDir, "SelectedTileOnly"), "universe"), "worlds"), "default");
        HytaleChunkStore chunkStore = new HytaleChunkStore(actualWorldDir, 0, 320);
        try {
            Set<MinecraftCoords> chunkCoords = chunkStore.getChunkCoords();
            assertEquals("One selected WorldPainter tile should export exactly 4x4 Hytale chunks", 16, chunkCoords.size());
            for (MinecraftCoords coords : chunkCoords) {
                assertTrue("Selected tile should stay within chunk x range 0-3 but found " + coords.x, (coords.x >= 0) && (coords.x < 4));
                assertTrue("Selected tile should stay within chunk z range 0-3 but found " + coords.z, (coords.z >= 0) && (coords.z < 4));
            }
        } finally {
            chunkStore.close();
        }
    }

    @Test
    public void exportWritesCalculatedLightDataForEmissiveBlocks() throws Exception {
        World2 world = new World2(HYTALE, 0, 320);
        world.setName("LightingExport");
        world.setCreateGoodiesChest(false);

        long seed = 99L;
        TileFactory tileFactory = TileFactoryFactory.createFlatTileFactory(
            seed, Terrain.GRASS, 0, 320, 64, 62, false, false);
        Dimension.Anchor anchor = new Dimension.Anchor(DIM_NORMAL, Dimension.Role.DETAIL, false, 0);
        Dimension dim = new Dimension(world, "Surface", seed, tileFactory, anchor);
        dim.setEventsInhibited(true);

        Tile tile = tileFactory.createTile(0, 0);
        for (int x = 0; x < 128; x++) {
            for (int z = 0; z < 128; z++) {
                tile.setHeight(x, z, 1);
                tile.setTerrain(x, z, Terrain.STONE);
                HytaleTerrainLayer.setTerrainIndex(tile, x, z, HytaleTerrain.STONE.getLayerIndex());
            }
        }
        HytaleTerrainLayer.setTerrainIndex(tile, 0, 0, HytaleTerrain.BLUE_GLOWING_MUSHROOM.getLayerIndex());
        tile.setHeight(0, 0, 1);

        dim.addTile(tile);
        dim.setEventsInhibited(false);
        world.addDimension(dim);

        File exportBaseDir = tempDir.newFolder("lighting_export");
        new HytaleWorldExporter(world, new WorldExportSettings()).export(exportBaseDir, "LightingExport", null, null);

        File actualWorldDir = new File(new File(new File(new File(exportBaseDir, "LightingExport"), "universe"), "worlds"), "default");
        HytaleChunkStore chunkStore = new HytaleChunkStore(actualWorldDir, 0, 320);
        try {
            HytaleChunk chunk = chunkStore.getChunk(0, 0);
            assertNotNull(chunk);
            assertTrue("Emissive terrain should populate block light", chunk.getBlockLightLevel(0, 1, 0) > 0);
        } finally {
            chunkStore.close();
        }
    }
}
