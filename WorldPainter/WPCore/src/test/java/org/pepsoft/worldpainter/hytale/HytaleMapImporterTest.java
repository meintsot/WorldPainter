package org.pepsoft.worldpainter.hytale;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.pepsoft.worldpainter.*;
import org.pepsoft.worldpainter.importing.MapImporter;

import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.*;
import static org.pepsoft.worldpainter.Constants.*;
import static org.pepsoft.worldpainter.DefaultPlugin.HYTALE;

public class HytaleMapImporterTest {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Test
    public void constructorSetsFields() throws Exception {
        File worldDir = tempDir.newFolder("world");
        new File(worldDir, "chunks").mkdirs();
        TileFactory tileFactory = TileFactoryFactory.createNoiseTileFactory(
            0, Terrain.GRASS, 0, 320, 58, 62, false, true, 20, 1.0);
        HytaleMapImporter importer = new HytaleMapImporter(
            worldDir, tileFactory, null, MapImporter.ReadOnlyOption.NONE);
        assertNotNull(importer);
    }

    @Test
    public void emptyWorldImportsWithNoChunks() throws Exception {
        File worldDir = tempDir.newFolder("world2");
        new File(worldDir, "chunks").mkdirs();
        Files.writeString(worldDir.toPath().resolve("config.json"), "{}");

        TileFactory tileFactory = TileFactoryFactory.createNoiseTileFactory(
            0, Terrain.GRASS, 0, 320, 58, 62, false, true, 20, 1.0);
        HytaleMapImporter importer = new HytaleMapImporter(
            worldDir, tileFactory, null, MapImporter.ReadOnlyOption.NONE);
        World2 world = importer.doImport(null);
        assertNotNull(world);
        assertEquals(HYTALE, world.getPlatform());
        // Empty world should have zero tiles
        Dimension dim = world.getDimension(new Dimension.Anchor(DIM_NORMAL, Dimension.Role.DETAIL, false, 0));
        assertNotNull(dim);
        assertEquals(0, dim.getTileCount());
    }

    @Test
    public void getWarningsReturnsNullOnCleanImport() throws Exception {
        File worldDir = tempDir.newFolder("world3");
        new File(worldDir, "chunks").mkdirs();
        Files.writeString(worldDir.toPath().resolve("config.json"), "{}");

        TileFactory tileFactory = TileFactoryFactory.createNoiseTileFactory(
            0, Terrain.GRASS, 0, 320, 58, 62, false, true, 20, 1.0);
        HytaleMapImporter importer = new HytaleMapImporter(
            worldDir, tileFactory, null, MapImporter.ReadOnlyOption.NONE);
        importer.doImport(null);
        assertNull(importer.getWarnings());
    }
}
