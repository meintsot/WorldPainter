package org.pepsoft.worldpainter.platforms;

import org.junit.Test;
import org.pepsoft.worldpainter.*;
import org.pepsoft.worldpainter.importing.MapImporter;
import org.pepsoft.worldpainter.plugins.MapImporterProvider;

import java.io.File;
import java.util.Collections;

import static org.junit.Assert.*;

public class HytalePlatformProviderImportTest {

    @Test
    public void implementsMapImporterProvider() {
        HytalePlatformProvider provider = new HytalePlatformProvider();
        assertTrue(provider instanceof MapImporterProvider);
    }

    @Test
    public void getImporterReturnsNonNull() {
        HytalePlatformProvider provider = new HytalePlatformProvider();
        TileFactory tileFactory = TileFactoryFactory.createNoiseTileFactory(
            0, Terrain.GRASS, 0, 320, 58, 62, false, true, 20, 1.0);
        MapImporter importer = ((MapImporterProvider) provider).getImporter(
            new File("."), tileFactory, null, MapImporter.ReadOnlyOption.NONE, Collections.singleton(0));
        assertNotNull(importer);
    }
}
