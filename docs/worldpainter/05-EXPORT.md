# WorldPainter Export System

## Overview

The export system transforms WorldPainter's internal representation (World2 → Dimension → Tile) into platform-specific world formats (Minecraft regions, Hytale chunks).

## Core Interfaces

### WorldExporter

**Location:** [WPCore/.../exporting/WorldExporter.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/exporting/WorldExporter.java)

```java
public interface WorldExporter {
    Map<Platform, File> selectBackupDir(File worldDir);
    Map<Integer, Stats> export(File worldDir, boolean inhibitWarnings, 
                               ProgressReceiver progressReceiver);
}
```

### AbstractWorldExporter

**Location:** [WPCore/.../exporting/AbstractWorldExporter.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/exporting/AbstractWorldExporter.java)

Base implementation for block-based platforms. Handles:
- Region parallelization
- First/second pass layer export
- Post-processing coordination

## Export Pipeline

```
World2
  │
  ├─► createWorld(worldDir)
  │     └─► Create directory structure
  │     └─► Write level.dat / config.json
  │
  └─► parallelExportRegions(dimension)
        │
        ├─► For each region (32×32 chunks, parallelized):
        │     │
        │     ├─► FIRST PASS
        │     │     └─► WorldPainterChunkFactory.createChunk()
        │     │           ├─► Generate terrain from Tile
        │     │           ├─► Apply FirstPassLayerExporters
        │     │           └─► Store in WorldRegion buffer
        │     │
        │     ├─► SECOND PASS - Stage.CARVE
        │     │     └─► Apply SecondPassLayerExporters
        │     │           └─► Caves, Chasms, Tunnels
        │     │
        │     ├─► SECOND PASS - Stage.ADD_FEATURES
        │     │     └─► Apply SecondPassLayerExporters
        │     │           └─► Trees, Objects, Structures
        │     │
        │     ├─► POST-PROCESSING
        │     │     └─► PostProcessor.postProcess()
        │     │           └─► Block rule fixes (water flow, etc.)
        │     │
        │     ├─► BLOCK PROPERTIES
        │     │     └─► BlockPropertiesCalculator
        │     │           └─► Lighting, leaf distance
        │     │
        │     └─► SAVE
        │           └─► WorldRegion.save()
        │                 └─► ChunkStore.saveChunk()
        │
        └─► Return export stats
```

## Key Export Classes

### WorldPainterChunkFactory

**Location:** [WPCore/.../exporting/WorldPainterChunkFactory.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/exporting/WorldPainterChunkFactory.java)

Creates chunks from WorldPainter tiles.

```java
public class WorldPainterChunkFactory {
    private final Dimension dimension;
    private final Platform platform;
    private final Map<Layer, LayerExporter> exporters;
    
    public ChunkCreationResult createChunk(int chunkX, int chunkZ) {
        Chunk chunk = platformProvider.createChunk(platform, chunkX, chunkZ);
        
        for each column (x, z) in chunk:
            // Get tile data
            Tile tile = dimension.getTileForCoords(x, z);
            int height = tile.getHeight(x, z);
            Terrain terrain = tile.getTerrain(x, z);
            int waterLevel = tile.getWaterLevel(x, z);
            
            // Generate blocks
            for y from minHeight to height:
                Material material = getMaterial(terrain, x, y, z);
                chunk.setMaterial(x, y, z, material);
            
            // Fill water
            for y from height + 1 to waterLevel:
                chunk.setMaterial(x, y, z, Material.WATER);
        
        // Apply first-pass layer exporters
        for (FirstPassLayerExporter exporter : firstPassExporters):
            exporter.render(dimension, tile, chunk, platform);
        
        return new ChunkCreationResult(chunk, stats);
    }
}
```

### WorldRegion

**Location:** [WPCore/.../exporting/WorldRegion.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/exporting/WorldRegion.java)

In-memory buffer for a 32×32 chunk region during export.

```java
public class WorldRegion implements MinecraftWorld {
    private final Chunk[][] chunks = new Chunk[32][32];
    
    public void setChunk(int x, int z, Chunk chunk);
    public Chunk getChunk(int x, int z);
    public void save(File worldDir, int dimension);
}
```

### MinecraftWorld

**Location:** [WPCore/.../exporting/MinecraftWorld.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/exporting/MinecraftWorld.java)

Abstract interface for world access during export.

```java
public interface MinecraftWorld {
    int getMinHeight();
    int getMaxHeight();
    
    Material getMaterialAt(int x, int y, int z);
    void setMaterialAt(int x, int y, int z, Material material);
    
    Chunk getChunk(int chunkX, int chunkZ);
    Chunk getChunkForEditing(int chunkX, int chunkZ);
}
```

### ChunkStore

**Location:** [WPCore/.../minecraft/ChunkStore.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/minecraft/ChunkStore.java)

Persists chunks to disk.

```java
public interface ChunkStore extends AutoCloseable {
    void saveChunk(Chunk chunk);
    Chunk getChunk(int x, int z);
    boolean containsChunk(int x, int z);
    void doInTransaction(Runnable operation);
    void flush();
}
```

## Platform-Specific Exporters

### JavaWorldExporter

**Location:** [WPCore/.../exporting/JavaWorldExporter.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/exporting/JavaWorldExporter.java)

Exports to Minecraft Java Edition format.

- Creates `level.dat` with NBT world settings
- Uses Anvil region files (`.mca`)
- Handles dimension folders (`DIM-1`, `DIM1`)

### HytaleWorldExporter

**Location:** [WPCore/.../hytale/HytaleWorldExporter.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleWorldExporter.java)

Exports to Hytale format.

- Creates `config.json` with world settings
- Uses IndexedStorageFile with BSON + Zstd
- Generates environment/weather data

```java
public class HytaleWorldExporter implements WorldExporter {
    @Override
    public Map<Integer, Stats> export(File worldDir, ...) {
        createWorldDirectory(worldDir);
        writeConfigJson(worldDir);
        
        for each dimension:
            exportDimension(dimension, worldDir);
        
        return stats;
    }
    
    private void exportDimension(Dimension dimension, File worldDir) {
        IndexedStorageFile storage = new IndexedStorageFile(chunksDir);
        
        for each tile:
            HytaleChunk chunk = createHytaleChunk(tile);
            byte[] bson = HytaleBsonChunkSerializer.serialize(chunk);
            storage.write(chunkKey, bson);
        
        storage.close();
    }
}
```

## Post-Processing

### PostProcessor

**Location:** [WPCore/.../exporting/PostProcessor.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/exporting/PostProcessor.java)

Fixes block rules after export.

```java
public interface PostProcessor {
    void postProcess(MinecraftWorld world, Rectangle area, 
                     ProgressReceiver progressReceiver);
}
```

**Tasks:**
- Water flow simulation
- Support block placement (torches, signs)
- Block state corrections

### BlockPropertiesCalculator

**Location:** [WPCore/.../exporting/BlockPropertiesCalculator.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/exporting/BlockPropertiesCalculator.java)

Calculates lighting and other properties.

```java
public class BlockPropertiesCalculator {
    void calculate(MinecraftWorld world, Chunk chunk);
    
    // Calculates:
    // - Sky light propagation
    // - Block light emission
    // - Leaf distance (for decay)
}
```

## Export Statistics

```java
public static class Stats {
    public long time;           // Export time (ms)
    public int landArea;        // Land blocks
    public int waterArea;       // Water blocks
    public int surfaceArea;     // Total surface area
    
    // Layer stats
    public Map<Layer, Long> layerTimes;
    public Map<Layer, Long> layerBlockCounts;
}
```

## Parallelization

Export is parallelized by region:

```java
// In AbstractWorldExporter
ExecutorService executor = Executors.newFixedThreadPool(
    Runtime.getRuntime().availableProcessors());

for (Point regionCoords : regionCoordinates) {
    futures.add(executor.submit(() -> {
        return exportRegion(dimension, regionCoords);
    }));
}

for (Future<Stats> future : futures) {
    stats.merge(future.get());
}
```

## Extension Points

To add a new export target:

1. Implement `WorldExporter` or extend `AbstractWorldExporter`
2. Create platform-specific `ChunkStore`
3. Implement `PostProcessor` if needed
4. Register via `PlatformProvider`

## See Also

- [06-PLATFORMS.md](06-PLATFORMS.md) — Platform provider system
- [04-LAYERS.md](04-LAYERS.md) — Layer exporters
- [../hytale/03-CHUNKS.md](../hytale/03-CHUNKS.md) — Hytale chunk format
