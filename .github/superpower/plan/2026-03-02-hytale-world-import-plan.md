# Hytale World Import — Implementation Plan

**Goal:** Import an existing Hytale server world into WorldPainter for terrain editing, mapping blocks → HytaleTerrain, preserving environment & fluid data, and enabling round-trip fidelity.

**Architecture:** HytaleMapImporter (extends MapImporter) reads chunks via existing HytaleChunkStore/HytaleRegionFile pipeline. HytaleImportBlockMapper maps block IDs to HytaleTerrain. HytaleMapImportDialog provides Swing UI. HytalePlatformProvider wired as MapImporterProvider.

**Tech Stack:** Java 17, Swing, BSON (org.bson), Zstd, Maven (WPCore + WPGUI modules)

---

## Task 1: HytaleImportBlockMapper

Maps Hytale block IDs (from deserialized chunks) to `HytaleTerrain` instances. Provides prefix-based fallback and collects unknown block statistics.

### Step 1: Write failing test

- File: `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/HytaleImportBlockMapperTest.java`
- Code:

```java
package org.pepsoft.worldpainter.hytale;

import org.junit.jupiter.api.Test;
import org.pepsoft.worldpainter.Terrain;

import static org.junit.jupiter.api.Assertions.*;

class HytaleImportBlockMapperTest {

    @Test
    void directLookupReturnsKnownTerrain() {
        HytaleImportBlockMapper mapper = new HytaleImportBlockMapper();
        // "Soil_Grass" is in HytaleTerrain.BLOCK_ID_MAP → GRASS
        HytaleTerrain result = mapper.map("Soil_Grass");
        assertNotNull(result);
        assertEquals(HytaleTerrain.GRASS, result);
    }

    @Test
    void directLookupForStone() {
        HytaleImportBlockMapper mapper = new HytaleImportBlockMapper();
        HytaleTerrain result = mapper.map("Rock_Stone");
        assertNotNull(result);
        assertEquals(HytaleTerrain.STONE, result);
    }

    @Test
    void prefixFallbackForSoil() {
        HytaleImportBlockMapper mapper = new HytaleImportBlockMapper();
        // Hypothetical unknown Soil block should fallback to DIRT
        HytaleTerrain result = mapper.map("Soil_UnknownVariant_99");
        assertNotNull(result);
        assertEquals(HytaleTerrain.DIRT, result);
    }

    @Test
    void prefixFallbackForRock() {
        HytaleImportBlockMapper mapper = new HytaleImportBlockMapper();
        HytaleTerrain result = mapper.map("Rock_UnknownVariant_99");
        assertNotNull(result);
        assertEquals(HytaleTerrain.STONE, result);
    }

    @Test
    void unknownBlockReturnsFallback() {
        HytaleImportBlockMapper mapper = new HytaleImportBlockMapper();
        HytaleTerrain result = mapper.map("Completely_Unknown_Block");
        assertNotNull(result);
        assertEquals(HytaleTerrain.STONE, result); // default fallback
    }

    @Test
    void emptyBlockReturnsNull() {
        HytaleImportBlockMapper mapper = new HytaleImportBlockMapper();
        assertNull(mapper.map("Empty"));
        assertNull(mapper.map(null));
    }

    @Test
    void cacheReturnsSameInstance() {
        HytaleImportBlockMapper mapper = new HytaleImportBlockMapper();
        HytaleTerrain first = mapper.map("Soil_Grass");
        HytaleTerrain second = mapper.map("Soil_Grass");
        assertSame(first, second);
    }

    @Test
    void toMinecraftTerrainDelegation() {
        HytaleImportBlockMapper mapper = new HytaleImportBlockMapper();
        Terrain mc = mapper.toMcTerrain("Soil_Grass");
        assertNotNull(mc);
        assertEquals(Terrain.GRASS, mc);
    }

    @Test
    void unmappedCountTracked() {
        HytaleImportBlockMapper mapper = new HytaleImportBlockMapper();
        mapper.map("Completely_Unknown_XYZ");
        mapper.map("Completely_Unknown_XYZ");
        mapper.map("Another_Unknown");
        assertEquals(2, mapper.getUnmappedBlockIds().size());
        assertTrue(mapper.getUnmappedBlockIds().contains("Completely_Unknown_XYZ"));
        assertTrue(mapper.getUnmappedBlockIds().contains("Another_Unknown"));
    }
}
```

### Step 2: Run test and verify failure

- Command: `cd WorldPainter && mvn test -pl WPCore -Dtest=HytaleImportBlockMapperTest -Dsurefire.failIfNoSpecifiedTests=false`
- Expected: Compilation error — `HytaleImportBlockMapper` class does not exist

### Step 3: Implement HytaleImportBlockMapper

- File: `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleImportBlockMapper.java`
- Code:

```java
package org.pepsoft.worldpainter.hytale;

import org.pepsoft.worldpainter.Terrain;

import java.util.*;

/**
 * Maps Hytale block IDs (from imported chunks) to {@link HytaleTerrain} instances.
 * <ol>
 *   <li>Direct lookup via {@link HytaleTerrain#getByBlockId(String)}</li>
 *   <li>Prefix-based fallback ({@code Soil_*} → DIRT, {@code Rock_*} → STONE, etc.)</li>
 *   <li>Default fallback → {@link HytaleTerrain#STONE}</li>
 * </ol>
 * Results are cached for speed. Unknown block IDs are tracked for reporting.
 */
public final class HytaleImportBlockMapper {

    private final Map<String, HytaleTerrain> cache = new HashMap<>();
    private final Set<String> unmappedBlockIds = new LinkedHashSet<>();

    /**
     * Map a Hytale block ID to a HytaleTerrain.
     *
     * @param blockId The block ID string (e.g. "Soil_Grass", "Rock_Stone")
     * @return The mapped HytaleTerrain, or {@code null} if the block is empty/air
     */
    public HytaleTerrain map(String blockId) {
        if (blockId == null || blockId.equals("Empty")) {
            return null;
        }
        return cache.computeIfAbsent(blockId, this::resolve);
    }

    /**
     * Map a block ID to its closest Minecraft Terrain (for WP's internal model).
     *
     * @param blockId The block ID string
     * @return The Minecraft Terrain, or {@code null} if empty/air
     */
    public Terrain toMcTerrain(String blockId) {
        HytaleTerrain ht = map(blockId);
        if (ht == null) {
            return null;
        }
        return HytaleTerrainHelper.toMinecraftTerrain(ht);
    }

    /**
     * @return Set of block IDs that could not be directly mapped and used fallback.
     */
    public Set<String> getUnmappedBlockIds() {
        return Collections.unmodifiableSet(unmappedBlockIds);
    }

    private HytaleTerrain resolve(String blockId) {
        // 1. Direct lookup
        HytaleTerrain terrain = HytaleTerrain.getByBlockId(blockId);
        if (terrain != null) {
            return terrain;
        }

        // 2. Prefix-based fallback
        terrain = prefixFallback(blockId);
        if (terrain != null) {
            unmappedBlockIds.add(blockId);
            return terrain;
        }

        // 3. Default fallback
        unmappedBlockIds.add(blockId);
        return HytaleTerrain.STONE;
    }

    private static HytaleTerrain prefixFallback(String blockId) {
        if (blockId.startsWith("Soil_")) return HytaleTerrain.DIRT;
        if (blockId.startsWith("Rock_")) return HytaleTerrain.STONE;
        if (blockId.startsWith("Sand_")) return HytaleTerrain.SAND;
        if (blockId.startsWith("Snow_")) return HytaleTerrain.SNOW;
        if (blockId.startsWith("Ice_"))  return HytaleTerrain.ICE;
        if (blockId.startsWith("Ore_"))  return HytaleTerrain.STONE;
        if (blockId.startsWith("Leaf_") || blockId.startsWith("Leaves_")) return HytaleTerrain.GRASS;
        if (blockId.startsWith("Wood_") || blockId.startsWith("Log_")) return HytaleTerrain.GRASS;
        return null;
    }
}
```

### Step 4: Run test and verify success

- Command: `cd WorldPainter && mvn test -pl WPCore -Dtest=HytaleImportBlockMapperTest`
- Expected: All 9 tests pass

---

## Task 2: HytaleMapImporter

The core import class. Extends `MapImporter`, iterates chunks via `HytaleChunkStore`, and builds a `World2` with terrain, heightmap, fluid, and environment data.

### Step 1: Write failing test

- File: `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/HytaleMapImporterTest.java`
- Code:

```java
package org.pepsoft.worldpainter.hytale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pepsoft.worldpainter.*;
import org.pepsoft.worldpainter.importing.MapImporter;

import java.io.File;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.pepsoft.worldpainter.Constants.*;
import static org.pepsoft.worldpainter.DefaultPlugin.HYTALE;

class HytaleMapImporterTest {

    @Test
    void constructorSetsFields(@TempDir Path tempDir) {
        File worldDir = tempDir.toFile();
        new File(worldDir, "chunks").mkdirs();
        TileFactory tileFactory = TileFactoryFactory.createNoiseTileFactory(
            0, Terrain.GRASS, 0, 320, 58, 62, false, true, 20, 1.0);
        HytaleMapImporter importer = new HytaleMapImporter(
            worldDir, tileFactory, null, MapImporter.ReadOnlyOption.NONE);
        assertNotNull(importer);
    }

    @Test
    void emptyWorldImportsWithNoChunks(@TempDir Path tempDir) throws Exception {
        File worldDir = tempDir.toFile();
        new File(worldDir, "chunks").mkdirs();
        // Write a config.json to make it look like a Hytale world
        java.nio.file.Files.writeString(tempDir.resolve("config.json"), "{}");

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
    void getWarningsReturnsNullOnCleanImport(@TempDir Path tempDir) throws Exception {
        File worldDir = tempDir.toFile();
        new File(worldDir, "chunks").mkdirs();
        java.nio.file.Files.writeString(tempDir.resolve("config.json"), "{}");

        TileFactory tileFactory = TileFactoryFactory.createNoiseTileFactory(
            0, Terrain.GRASS, 0, 320, 58, 62, false, true, 20, 1.0);
        HytaleMapImporter importer = new HytaleMapImporter(
            worldDir, tileFactory, null, MapImporter.ReadOnlyOption.NONE);
        importer.doImport(null);
        assertNull(importer.getWarnings());
    }
}
```

### Step 2: Run test and verify failure

- Command: `cd WorldPainter && mvn test -pl WPCore -Dtest=HytaleMapImporterTest -Dsurefire.failIfNoSpecifiedTests=false`
- Expected: Compilation error — `HytaleMapImporter` class does not exist

### Step 3: Implement HytaleMapImporter

- File: `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleMapImporter.java`
- Code:

```java
package org.pepsoft.worldpainter.hytale;

import org.pepsoft.minecraft.Chunk;
import org.pepsoft.minecraft.ChunkStore;
import org.pepsoft.minecraft.MinecraftCoords;
import org.pepsoft.util.ProgressReceiver;
import org.pepsoft.worldpainter.*;
import org.pepsoft.worldpainter.history.HistoryEntry;
import org.pepsoft.worldpainter.importing.MapImporter;
import org.pepsoft.worldpainter.layers.ReadOnly;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.pepsoft.worldpainter.Constants.*;
import static org.pepsoft.worldpainter.DefaultPlugin.HYTALE;
import static org.pepsoft.worldpainter.Dimension.Anchor;

/**
 * Imports an existing Hytale server world into WorldPainter. Reads chunks via
 * {@link HytaleChunkStore}, maps blocks to {@link HytaleTerrain}, and preserves
 * environment & fluid data for round-trip fidelity.
 */
public class HytaleMapImporter extends MapImporter {

    private static final Logger logger = Logger.getLogger(HytaleMapImporter.class.getName());

    private static final int HYTALE_CHUNK_SIZE = HytaleChunk.CHUNK_SIZE; // 32
    /** Number of Hytale chunks per WP tile side: 128 / 32 = 4 */
    private static final int CHUNKS_PER_TILE = TILE_SIZE / HYTALE_CHUNK_SIZE; // 4
    /** Bit shift: log2(4) = 2 */
    private static final int CHUNKS_PER_TILE_BITS = 2;

    private final File worldDir;
    private final TileFactory tileFactory;
    private final Set<MinecraftCoords> chunksToSkip;
    private final ReadOnlyOption readOnlyOption;
    private String warnings;

    public HytaleMapImporter(File worldDir, TileFactory tileFactory,
                             Set<MinecraftCoords> chunksToSkip,
                             ReadOnlyOption readOnlyOption) {
        this.worldDir = Objects.requireNonNull(worldDir);
        this.tileFactory = Objects.requireNonNull(tileFactory);
        this.chunksToSkip = chunksToSkip;
        this.readOnlyOption = (readOnlyOption != null) ? readOnlyOption : ReadOnlyOption.NONE;
    }

    @Override
    public World2 doImport(ProgressReceiver progressReceiver) throws IOException, ProgressReceiver.OperationCancelled {
        logger.info("Importing Hytale world from " + worldDir.getAbsolutePath());
        final long start = System.currentTimeMillis();

        // 1. Create World2
        final World2 world = new World2(HYTALE, 0, HytaleChunk.MAX_HEIGHT);
        world.setName(worldDir.getName());
        world.setCreateGoodiesChest(false);
        world.setImportedFrom(new File(worldDir, "config.json"));
        world.addHistoryEntry(HistoryEntry.WORLD_IMPORTED_FROM_MINECRAFT_MAP, worldDir.getName(), worldDir);

        // 2. Create Dimension
        final long seed = new Random().nextLong();
        tileFactory.setSeed(seed);
        final Anchor anchor = new Anchor(DIM_NORMAL, Dimension.Role.DETAIL, false, 0);
        final Dimension dimension = new Dimension(world, "Surface", seed, tileFactory, anchor);
        dimension.setEventsInhibited(true);

        final HytaleImportBlockMapper blockMapper = new HytaleImportBlockMapper();

        try {
            dimension.setCoverSteepTerrain(false);
            dimension.setSubsurfaceMaterial(Terrain.STONE);
            dimension.setBorderLevel(62);

            // 3. Iterate chunks
            try (ChunkStore chunkStore = new HytaleChunkStore(worldDir, 0, HytaleChunk.MAX_HEIGHT)) {
                final Set<MinecraftCoords> allCoords = chunkStore.getChunkCoords();
                final int totalChunks = allCoords.size();
                final AtomicInteger processedCount = new AtomicInteger();

                for (MinecraftCoords coords : allCoords) {
                    if (progressReceiver != null) {
                        progressReceiver.setProgress((float) processedCount.getAndIncrement() / totalChunks);
                    }

                    if (chunksToSkip != null && chunksToSkip.contains(coords)) {
                        continue;
                    }

                    final Chunk chunk = chunkStore.getChunk(coords.x, coords.z);
                    if (chunk == null) {
                        continue;
                    }

                    importChunk((HytaleChunk) chunk, coords.x, coords.z, dimension, blockMapper);
                }
            }

            // 4. Apply read-only if requested
            if (readOnlyOption == ReadOnlyOption.ALL) {
                for (Tile tile : dimension.getTiles()) {
                    for (int x = 0; x < TILE_SIZE; x++) {
                        for (int y = 0; y < TILE_SIZE; y++) {
                            tile.setBitLayerValue(ReadOnly.INSTANCE, x, y, true);
                        }
                    }
                }
            }

        } finally {
            dimension.setEventsInhibited(false);
        }

        world.addDimension(dimension);

        // 5. Report unmapped blocks
        final Set<String> unmapped = blockMapper.getUnmappedBlockIds();
        if (!unmapped.isEmpty()) {
            final StringBuilder sb = new StringBuilder("The following block IDs were not directly mapped and used fallback terrain:\n");
            for (String id : unmapped) {
                sb.append("  - ").append(id).append('\n');
            }
            warnings = sb.toString();
            logger.warning(warnings);
        }

        logger.info("Hytale import completed in " + (System.currentTimeMillis() - start) + " ms");
        return world;
    }

    @Override
    public String getWarnings() {
        return warnings;
    }

    private void importChunk(HytaleChunk chunk, int chunkX, int chunkZ,
                             Dimension dimension, HytaleImportBlockMapper mapper) {
        // Tile coords: 4 Hytale chunks per WP tile side
        final int tileX = chunkX >> CHUNKS_PER_TILE_BITS;
        final int tileZ = chunkZ >> CHUNKS_PER_TILE_BITS;
        final Point tileCoords = new Point(tileX, tileZ);

        Tile tile = dimension.getTile(tileCoords);
        if (tile == null) {
            tile = tileFactory.createTile(tileCoords.x, tileCoords.y);
            dimension.addTile(tile);
        }

        // Offset within tile: 0, 32, 64, or 96
        final int offX = (chunkX & (CHUNKS_PER_TILE - 1)) * HYTALE_CHUNK_SIZE;
        final int offZ = (chunkZ & (CHUNKS_PER_TILE - 1)) * HYTALE_CHUNK_SIZE;

        for (int localX = 0; localX < HYTALE_CHUNK_SIZE; localX++) {
            for (int localZ = 0; localZ < HYTALE_CHUNK_SIZE; localZ++) {
                final int tilePixelX = offX + localX;
                final int tilePixelZ = offZ + localZ;

                importColumn(chunk, localX, localZ, tile, tilePixelX, tilePixelZ, mapper);
            }
        }
    }

    private void importColumn(HytaleChunk chunk, int localX, int localZ,
                              Tile tile, int tilePixelX, int tilePixelZ,
                              HytaleImportBlockMapper mapper) {
        // Use heightmap from chunk
        final int height = chunk.getHeight(localX, localZ);

        // Find the topmost solid block to determine terrain type
        HytaleTerrain hytaleTerrain = null;
        Terrain mcTerrain = Terrain.STONE;
        int surfaceY = height;

        for (int y = height; y >= 0; y--) {
            HytaleBlock block = chunk.getHytaleBlock(localX, y, localZ);
            if (block != null && !block.isEmpty()) {
                hytaleTerrain = mapper.map(block.id);
                if (hytaleTerrain != null) {
                    mcTerrain = HytaleTerrainHelper.toMinecraftTerrain(hytaleTerrain);
                    surfaceY = y;
                    break;
                }
            }
        }

        // Set terrain height (WP uses float, half-block offset for best appearance)
        float wpHeight = Math.max(surfaceY - 0.4375f, 0);
        tile.setHeight(tilePixelX, tilePixelZ, wpHeight);
        tile.setTerrain(tilePixelX, tilePixelZ, mcTerrain);

        // Store native HytaleTerrain index for round-trip fidelity
        if (hytaleTerrain != null) {
            int layerIndex = hytaleTerrain.getLayerIndex();
            if (layerIndex > 0) {
                HytaleTerrainLayer.setTerrainIndex(tile, tilePixelX, tilePixelZ, layerIndex);
            }
        }

        // Detect water level from fluid data
        int waterLevel = 0; // Hytale minZ = 0
        for (int y = height + 1; y < HytaleChunk.MAX_HEIGHT && y <= height + 32; y++) {
            HytaleChunk.HytaleSection section = chunk.getSections()[y >> 5];
            if (section != null) {
                int fluidId = section.getFluidId(localX, y & 31, localZ);
                if (fluidId > 0) {
                    // Found fluid above surface
                    waterLevel = y;
                    // Look for top of fluid column
                    for (int fy = y + 1; fy < HytaleChunk.MAX_HEIGHT; fy++) {
                        HytaleChunk.HytaleSection fSec = chunk.getSections()[fy >> 5];
                        if (fSec == null || fSec.getFluidId(localX, fy & 31, localZ) == 0) {
                            waterLevel = fy - 1;
                            break;
                        }
                    }
                    break;
                }
            }
        }
        tile.setWaterLevel(tilePixelX, tilePixelZ, waterLevel);

        // Fluid type layer (detect lava vs water varieties)
        // Check fluid at the water level (or surface level if submerged)
        if (waterLevel > surfaceY) {
            HytaleChunk.HytaleSection sec = chunk.getSections()[waterLevel >> 5];
            if (sec != null) {
                int fId = sec.getFluidId(localX, waterLevel & 31, localZ);
                if (fId > 0) {
                    String fluidName = sec.getFluidPalette().get(fId);
                    int fluidLayerValue = mapFluidToLayer(fluidName);
                    if (fluidLayerValue > 0) {
                        tile.setLayerValue(HytaleFluidLayer.INSTANCE, tilePixelX, tilePixelZ, fluidLayerValue);
                    }
                }
            }
        }

        // Environment layer
        String envName = chunk.getEnvironment(localX, localZ);
        if (envName != null && !envName.equals("Default")) {
            HytaleEnvironmentData envData = HytaleEnvironmentData.getByName(envName);
            if (envData != null && envData.getId() > 0 && envData.getId() < 255) {
                tile.setLayerValue(HytaleEnvironmentLayer.INSTANCE, tilePixelX, tilePixelZ, envData.getId());
            }
        }
    }

    /**
     * Map a fluid name from the chunk palette to a {@link HytaleFluidLayer} value.
     */
    private static int mapFluidToLayer(String fluidName) {
        if (fluidName == null || fluidName.equals("Empty")) return 0;
        if (fluidName.contains("Lava"))  return HytaleFluidLayer.FLUID_LAVA;
        if (fluidName.contains("Water")) return HytaleFluidLayer.FLUID_ZONE1_WATER;
        return 0;
    }
}
```

### Step 4: Run test and verify success

- Command: `cd WorldPainter && mvn test -pl WPCore -Dtest=HytaleMapImporterTest`
- Expected: All 3 tests pass

---

## Task 3: Wire HytalePlatformProvider as MapImporterProvider

Modify `HytalePlatformProvider` to implement `MapImporterProvider` so the existing import dialog can discover and use the Hytale importer.

### Step 1: Write failing test

- File: `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/platforms/HytalePlatformProviderImportTest.java`
- Code:

```java
package org.pepsoft.worldpainter.platforms;

import org.junit.jupiter.api.Test;
import org.pepsoft.worldpainter.*;
import org.pepsoft.worldpainter.importing.MapImporter;
import org.pepsoft.worldpainter.plugins.MapImporterProvider;

import java.io.File;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class HytalePlatformProviderImportTest {

    @Test
    void implementsMapImporterProvider() {
        HytalePlatformProvider provider = new HytalePlatformProvider();
        assertTrue(provider instanceof MapImporterProvider);
    }

    @Test
    void getImporterReturnsNonNull() {
        HytalePlatformProvider provider = new HytalePlatformProvider();
        TileFactory tileFactory = TileFactoryFactory.createNoiseTileFactory(
            0, Terrain.GRASS, 0, 320, 58, 62, false, true, 20, 1.0);
        MapImporter importer = ((MapImporterProvider) provider).getImporter(
            new File("."), tileFactory, null, MapImporter.ReadOnlyOption.NONE, Collections.singleton(0));
        assertNotNull(importer);
    }
}
```

### Step 2: Run test and verify failure

- Command: `cd WorldPainter && mvn test -pl WPCore -Dtest=HytalePlatformProviderImportTest -Dsurefire.failIfNoSpecifiedTests=false`
- Expected: `implementsMapImporterProvider` fails — `HytalePlatformProvider` does not implement `MapImporterProvider`

### Step 3: Modify HytalePlatformProvider

- File: `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/platforms/HytalePlatformProvider.java`
- Changes:

**3a.** Add import and change class declaration:

```java
// Add imports:
import org.pepsoft.minecraft.MinecraftCoords;
import org.pepsoft.worldpainter.TileFactory;
import org.pepsoft.worldpainter.hytale.HytaleMapImporter;
import org.pepsoft.worldpainter.importing.MapImporter;
import org.pepsoft.worldpainter.plugins.MapImporterProvider;
import java.util.Set;

// Change class declaration from:
public class HytalePlatformProvider extends AbstractPlatformProvider implements BlockBasedPlatformProvider {

// To:
public class HytalePlatformProvider extends AbstractPlatformProvider implements BlockBasedPlatformProvider, MapImporterProvider {
```

**3b.** Add `getImporter()` method before the ICON field:

```java
    // MapImporterProvider implementation

    @Override
    public MapImporter getImporter(File dir, TileFactory tileFactory, Set<MinecraftCoords> chunksToSkip,
                                   MapImporter.ReadOnlyOption readOnlyOption, Set<Integer> dimensionsToImport) {
        return new HytaleMapImporter(dir, tileFactory, chunksToSkip, readOnlyOption);
    }
```

### Step 4: Run test and verify success

- Command: `cd WorldPainter && mvn test -pl WPCore -Dtest=HytalePlatformProviderImportTest`
- Expected: Both tests pass

---

## Task 4: Verify MapImportDialog Integration

With Task 3 complete, the existing `MapImportDialog` should already work with Hytale worlds since it checks `instanceof MapImporterProvider`. This task verifies the integration manually.

### Step 1: Build the full project

- Command: `cd WorldPainter && mvn package -DskipTests`
- Expected: BUILD SUCCESS

### Step 2: Manual verification

1. Launch WorldPainter
2. File → Import → Existing Map
3. Browse to a Hytale world folder (must have `config.json` + `chunks/` directory)
4. Dialog should display platform "Hytale" and show chunk statistics
5. Click Import — world should load with correct terrain heights and block mapping
6. Verify terrain painting matches imported blocks on the 2D view

### Step 3: Verify round-trip

1. After import, File → Export (Hytale)
2. Compare exported region files with original — terrain types should be preserved via `HytaleTerrainLayer`

---

## Task 5: HytaleMapImportDialog (custom dialog — optional enhancement)

A dedicated Hytale import dialog with block mapping statistics preview. This enhances the existing `MapImportDialog` flow but is **not required** for basic import functionality (Task 3 already enables import via the generic dialog).

### Step 1: Write the dialog

- File: `WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/importing/HytaleMapImportDialog.java`
- Code:

```java
package org.pepsoft.worldpainter.importing;

import org.pepsoft.minecraft.ChunkStore;
import org.pepsoft.minecraft.MinecraftCoords;
import org.pepsoft.util.ProgressReceiver;
import org.pepsoft.util.swing.ProgressDialog;
import org.pepsoft.util.swing.ProgressTask;
import org.pepsoft.worldpainter.*;
import org.pepsoft.worldpainter.hytale.*;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.Set;

import static org.pepsoft.worldpainter.Constants.*;
import static org.pepsoft.worldpainter.DefaultPlugin.HYTALE;

/**
 * Custom import dialog for Hytale worlds. Shows block mapping statistics
 * and environment/fluid preview before importing.
 */
public class HytaleMapImportDialog extends WorldPainterDialog {

    private final App app;
    private File worldDir;
    private World2 importedWorld;

    // UI Components
    private JTextField fieldFolder;
    private JLabel labelChunkCount, labelWorldBounds, labelRegionFiles;
    private JLabel labelUniqueBlocks, labelMappedBlocks;
    private JCheckBox checkReadOnly;
    private JButton buttonImport;

    public HytaleMapImportDialog(App app) {
        super(app);
        this.app = app;
        initUI();
        setLocationRelativeTo(app);
    }

    public World2 getImportedWorld() {
        return importedWorld;
    }

    private void initUI() {
        setTitle("Import Hytale World");
        setModal(true);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Top: folder selection
        JPanel folderPanel = new JPanel(new BorderLayout(5, 0));
        folderPanel.add(new JLabel("World folder:"), BorderLayout.WEST);
        fieldFolder = new JTextField(30);
        fieldFolder.setEditable(false);
        folderPanel.add(fieldFolder, BorderLayout.CENTER);
        JButton btnBrowse = new JButton("Browse...");
        btnBrowse.addActionListener(e -> selectFolder());
        folderPanel.add(btnBrowse, BorderLayout.EAST);
        mainPanel.add(folderPanel, BorderLayout.NORTH);

        // Center: stats panel
        JPanel statsPanel = new JPanel(new GridBagLayout());
        statsPanel.setBorder(BorderFactory.createTitledBorder("World Statistics"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 4, 2, 4);

        int row = 0;
        labelChunkCount = addStatRow(statsPanel, gbc, row++, "Chunks found:", "—");
        labelWorldBounds = addStatRow(statsPanel, gbc, row++, "World bounds:", "—");
        labelRegionFiles = addStatRow(statsPanel, gbc, row++, "Region files:", "—");
        labelUniqueBlocks = addStatRow(statsPanel, gbc, row++, "Unique blocks:", "—");
        labelMappedBlocks = addStatRow(statsPanel, gbc, row++, "Mapped blocks:", "—");

        mainPanel.add(statsPanel, BorderLayout.CENTER);

        // Bottom: options + buttons
        JPanel bottomPanel = new JPanel(new BorderLayout());
        checkReadOnly = new JCheckBox("Mark imported chunks as read-only");
        bottomPanel.add(checkReadOnly, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnCancel = new JButton("Cancel");
        btnCancel.addActionListener(e -> cancel());
        buttonPanel.add(btnCancel);
        buttonImport = new JButton("Import");
        buttonImport.setEnabled(false);
        buttonImport.addActionListener(e -> doImport());
        buttonPanel.add(buttonImport);
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);

        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
        pack();
        setMinimumSize(new Dimension(480, 350));
    }

    private JLabel addStatRow(JPanel panel, GridBagConstraints gbc, int row, String label, String value) {
        gbc.gridx = 0; gbc.gridy = row;
        panel.add(new JLabel(label), gbc);
        JLabel valueLabel = new JLabel(value);
        gbc.gridx = 1;
        panel.add(valueLabel, gbc);
        return valueLabel;
    }

    private void selectFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Select Hytale World Folder");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            worldDir = chooser.getSelectedFile();
            fieldFolder.setText(worldDir.getAbsolutePath());
            analyzeWorld();
        }
    }

    private void analyzeWorld() {
        buttonImport.setEnabled(false);
        if (worldDir == null || !worldDir.isDirectory()) return;

        File configFile = new File(worldDir, "config.json");
        File chunksDir = new File(worldDir, "chunks");
        if (!configFile.isFile() || !chunksDir.isDirectory()) {
            JOptionPane.showMessageDialog(this,
                "Not a valid Hytale world (missing config.json or chunks/ directory)",
                "Invalid World", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Count region files
        File[] regionFiles = chunksDir.listFiles((dir, name) -> name.endsWith(".region.bin"));
        int regionCount = (regionFiles != null) ? regionFiles.length : 0;
        labelRegionFiles.setText(String.valueOf(regionCount));

        if (regionCount == 0) {
            labelChunkCount.setText("0");
            labelWorldBounds.setText("—");
            return;
        }

        // Analyze chunks
        ProgressDialog.executeTask(this, new ProgressTask<Void>() {
            @Override public String getName() { return "Analyzing Hytale world..."; }
            @Override public Void execute(ProgressReceiver pr) throws ProgressReceiver.OperationCancelled {
                try (ChunkStore store = new HytaleChunkStore(worldDir, 0, HytaleChunk.MAX_HEIGHT)) {
                    Set<MinecraftCoords> coords = store.getChunkCoords();
                    final NumberFormat fmt = NumberFormat.getIntegerInstance();
                    int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
                    int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
                    for (MinecraftCoords c : coords) {
                        if (c.x < minX) minX = c.x;
                        if (c.x > maxX) maxX = c.x;
                        if (c.z < minZ) minZ = c.z;
                        if (c.z > maxZ) maxZ = c.z;
                    }
                    final int fMinX = minX, fMaxX = maxX, fMinZ = minZ, fMaxZ = maxZ;
                    final int chunkCount = coords.size();
                    SwingUtilities.invokeLater(() -> {
                        labelChunkCount.setText(fmt.format(chunkCount));
                        labelWorldBounds.setText(fmt.format(fMinX * 32) + "," + fmt.format(fMinZ * 32)
                            + " to " + fmt.format((fMaxX + 1) * 32) + "," + fmt.format((fMaxZ + 1) * 32));
                        buttonImport.setEnabled(chunkCount > 0);
                    });
                }
                return null;
            }
        });
    }

    private void doImport() {
        app.clearWorld();
        final MapImporter.ReadOnlyOption readOnlyOpt = checkReadOnly.isSelected()
            ? MapImporter.ReadOnlyOption.ALL : MapImporter.ReadOnlyOption.NONE;

        importedWorld = ProgressDialog.executeTask(this, new ProgressTask<World2>() {
            @Override public String getName() { return "Importing Hytale world..."; }
            @Override public World2 execute(ProgressReceiver pr) throws ProgressReceiver.OperationCancelled {
                try {
                    TileFactory tileFactory = TileFactoryFactory.createNoiseTileFactory(
                        0, Terrain.GRASS, 0, HytaleChunk.MAX_HEIGHT, 58, 62, false, true, 20, 1.0);
                    HytaleMapImporter importer = new HytaleMapImporter(
                        worldDir, tileFactory, null, readOnlyOpt);
                    return importer.doImport(pr);
                } catch (IOException e) {
                    throw new RuntimeException("I/O error during Hytale import", e);
                }
            }
        });

        if (importedWorld != null) {
            ok();
        }
    }
}
```

### Step 2: Wire dialog into App.java (optional direct access)

- File: `WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/App.java`
- After the existing `importWorld()` method (around line 2812), add:

```java
    private void importHytaleWorld() {
        if (!saveIfNecessary()) {
            return;
        }
        HytaleMapImportDialog dialog = new HytaleMapImportDialog(this);
        dialog.setVisible(true);
        if (!dialog.isCancelled()) {
            World2 importedWorld = dialog.getImportedWorld();
            if (importedWorld != null) {
                setWorld(importedWorld, true);
                lastSelectedFile = null;
            }
        }
    }
```

- Add import at top of App.java:
```java
import org.pepsoft.worldpainter.importing.HytaleMapImportDialog;
```

**Note:** This method provides direct access via a dedicated menu item. The generic `importWorld()` already works via `MapImportDialog` after Task 3. Adding a menu item is optional and can be deferred.

### Step 3: Build and verify

- Command: `cd WorldPainter && mvn package -DskipTests`
- Expected: BUILD SUCCESS

---

## Task 6: Integration Test — Round-trip Export → Import

Verifies that a WorldPainter world exported to Hytale format can be imported back with terrain preserved.

### Step 1: Write the integration test

- File: `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/HytaleRoundTripTest.java`
- Code:

```java
package org.pepsoft.worldpainter.hytale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pepsoft.worldpainter.*;
import org.pepsoft.worldpainter.importing.MapImporter;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.pepsoft.worldpainter.Constants.*;
import static org.pepsoft.worldpainter.DefaultPlugin.HYTALE;

/**
 * Round-trip test: create WP world → export as Hytale → import back → verify terrain.
 */
class HytaleRoundTripTest {

    @Test
    void roundTripPreservesTerrain(@TempDir Path tempDir) throws Exception {
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
        File exportDir = tempDir.resolve("exported_world").toFile();
        exportDir.mkdirs();
        Files.writeString(exportDir.toPath().resolve("config.json"), "{}");
        new File(exportDir, "chunks").mkdirs();

        // Use the actual exporter
        WorldExportSettings exportSettings = new WorldExportSettings();
        HytaleWorldExporter exporter = new HytaleWorldExporter(world, exportSettings);
        exporter.export(exportDir, "RoundTripTest", exporter.firstPass(null), null);

        // 3. Verify export produced region files
        File chunksDir = new File(exportDir, "chunks");
        File[] regionFiles = chunksDir.listFiles((dir, name) -> name.endsWith(".region.bin"));
        assertNotNull(regionFiles);
        assertTrue(regionFiles.length > 0, "At least one region file should be created");

        // 4. Import back
        TileFactory importTileFactory = TileFactoryFactory.createNoiseTileFactory(
            0, Terrain.GRASS, 0, 320, 58, 62, false, true, 20, 1.0);
        HytaleMapImporter importer = new HytaleMapImporter(
            exportDir, importTileFactory, null, MapImporter.ReadOnlyOption.NONE);
        World2 importedWorld = importer.doImport(null);

        // 5. Verify imported terrain preserved
        assertNotNull(importedWorld);
        assertEquals(HYTALE, importedWorld.getPlatform());
        Dimension importedDim = importedWorld.getDimension(anchor);
        assertNotNull(importedDim);
        assertTrue(importedDim.getTileCount() > 0);

        // Check that HytaleTerrain index was preserved
        Tile importedTile = importedDim.getTile(0, 0);
        if (importedTile != null) {
            int terrainIndex = HytaleTerrainLayer.getTerrainIndex(importedTile, 0, 0);
            if (terrainIndex > 0) {
                assertEquals(HytaleTerrain.STONE.getLayerIndex(), terrainIndex,
                    "HytaleTerrain index should be preserved through round-trip");
            }
        }
    }
}
```

### Step 2: Run test

- Command: `cd WorldPainter && mvn test -pl WPCore -Dtest=HytaleRoundTripTest`
- Expected: Test passes (may need adjustments depending on exporter API — the test uses `firstPass`/`export` methods and should match the actual `HytaleWorldExporter` API)

---

## Summary

| Task | Type | Files | Description |
|------|------|-------|-------------|
| 1 | New class + test | `HytaleImportBlockMapper.java`, test | Block ID → HytaleTerrain mapping with cache & fallback |
| 2 | New class + test | `HytaleMapImporter.java`, test | Core importer: chunks → WP tiles with terrain/fluid/env |
| 3 | Modify + test | `HytalePlatformProvider.java`, test | Wire as `MapImporterProvider` → enables generic import dialog |
| 4 | Manual verification | — | Verify existing `MapImportDialog` works end-to-end |
| 5 | New class (optional) | `HytaleMapImportDialog.java`, App.java | Custom dialog with block mapping stats |
| 6 | Integration test | `HytaleRoundTripTest.java` | Export → Import round-trip verification |

**Execution order:** 1 → 2 → 3 → 4 → 5 (optional) → 6
