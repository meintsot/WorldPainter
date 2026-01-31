# WorldPainter Platform System

## Overview

WorldPainter supports multiple export targets through a platform abstraction layer. Each platform defines capabilities, constraints, and export behavior.

## Platform Class

**Location:** [WPCore/.../Platform.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/Platform.java)

```java
public final class Platform implements Serializable {
    public final String id;                      // Unique identifier
    public final String displayName;             // UI name
    public final int[] defaultHeights;           // Default world heights
    public final int standardMaxHeight;          // Standard max height
    public final int minMinHeight;               // Minimum allowed min
    public final int maxMaxHeight;               // Maximum allowed max
    public final int minX, maxX, minY, maxY;     // World bounds
    public final Set<Capability> capabilities;   
    public final List<GameType> supportedGameTypes;
    public final List<Generator> supportedGenerators;
    public final Set<Integer> supportedDimensions;
}
```

## Defined Platforms

### In DefaultPlugin

**Location:** [WPCore/.../DefaultPlugin.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/DefaultPlugin.java)

| Platform | ID | Height | Description |
|----------|----|----|-------------|
| `JAVA_MCREGION` | `org.pepsoft.mcregion` | 0-128 | MC Beta 1.3 to 1.1 |
| `JAVA_ANVIL` | `org.pepsoft.anvil` | 0-256 | MC 1.2 to 1.14 |
| `JAVA_ANVIL_1_15` | `org.pepsoft.anvil.1.15` | 0-256 | MC 1.15 (3D biomes) |
| `JAVA_ANVIL_1_17` | `org.pepsoft.anvil.1.17` | -64-320 | MC 1.17+ |
| `JAVA_ANVIL_1_18` | `org.pepsoft.anvil.1.18` | -64-320 | MC 1.18+ |
| `JAVA_ANVIL_1_19` | `org.pepsoft.anvil.1.19` | -64-320 | MC 1.19 |
| `JAVA_ANVIL_1_20_5` | `org.pepsoft.anvil.1.20.5` | -64-320 | MC 1.20.5+ |
| `HYTALE` | `org.pepsoft.hytale` | 0-320 | Hytale |

### Capabilities

```java
public enum Capability {
    BIOMES,                  // Biome support
    BIOMES_3D,               // 3D biome storage
    NAME_BASED_BIOMES,       // String biome IDs
    NAMED_BLOCKS,            // String block IDs (vs numeric)
    BLOCK_STATES,            // Block state properties
    PRECALCULATED_LIGHT,     // Light calculation required
    SET_SPAWN_POINT,         // Spawn point support
    POPULATE,                // Chunk population
    LEAF_DISTANCE,           // Leaf decay distance
    DATA_PACKS,              // Data pack support
    GRASS_MOISTURE,          // Grass moisture levels
    WATER_LEVEL,             // Per-column water level
}
```

## Platform Provider Interface

### PlatformProvider

**Location:** [WPCore/.../plugins/PlatformProvider.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/plugins/PlatformProvider.java)

Base interface for platform support.

```java
public interface PlatformProvider extends Provider<Platform> {
    List<Platform> getKeys();  // Supported platforms
    
    WorldExporter getExporter(World2 world, WorldExportSettings settings);
    
    File getDefaultExportDir(Platform platform);
    
    MapInfo identifyMap(File dir);  // Detect existing map format
    
    boolean isCompatible(Platform platform, Platform other);
}
```

### BlockBasedPlatformProvider

**Location:** [WPCore/.../plugins/BlockBasedPlatformProvider.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/plugins/BlockBasedPlatformProvider.java)

For Minecraft-like block-based platforms.

```java
public interface BlockBasedPlatformProvider extends PlatformProvider {
    Chunk createChunk(Platform platform, int x, int z, int minHeight, int maxHeight);
    
    ChunkStore getChunkStore(Platform platform, File worldDir, int dimension);
    
    PostProcessor getPostProcessor(Platform platform);
    
    MapImporter getMapImporter();
}
```

## Platform Implementations

### JavaPlatformProvider

**Location:** [WPCore/.../platforms/JavaPlatformProvider.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/platforms/JavaPlatformProvider.java)

Facade for all Java Edition variants.

```java
public class JavaPlatformProvider extends AbstractPlatformProvider 
    implements BlockBasedPlatformProvider {
    
    public JavaPlatformProvider() {
        super(Version.VERSION, Arrays.asList(
            JAVA_MCREGION, JAVA_ANVIL, JAVA_ANVIL_1_15, 
            JAVA_ANVIL_1_17, JAVA_ANVIL_1_18, JAVA_ANVIL_1_19, 
            JAVA_ANVIL_1_20_5
        ), "JavaPlatformProvider");
    }
    
    @Override
    public WorldExporter getExporter(World2 world, ...) {
        return new JavaWorldExporter(world, ...);
    }
    
    @Override
    public ChunkStore getChunkStore(Platform platform, File worldDir, int dim) {
        return new JavaChunkStore(platform, worldDir, dim);
    }
}
```

### HytalePlatformProvider

**Location:** [WPCore/.../platforms/HytalePlatformProvider.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/platforms/HytalePlatformProvider.java)

Hytale export support.

```java
public class HytalePlatformProvider extends AbstractPlatformProvider 
    implements BlockBasedPlatformProvider {
    
    public HytalePlatformProvider() {
        super(Version.VERSION, Collections.singletonList(HYTALE), 
              "HytalePlatformProvider");
    }
    
    @Override
    public WorldExporter getExporter(World2 world, ...) {
        return new HytaleWorldExporter(world, ...);
    }
    
    @Override
    public Chunk createChunk(...) {
        return new HytaleChunk(x, z, minHeight, maxHeight);
    }
}
```

## Platform Manager

**Location:** [WPCore/.../plugins/PlatformManager.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/plugins/PlatformManager.java)

Central registry for all platform providers.

```java
public class PlatformManager extends AbstractProviderManager<Platform, PlatformProvider> {
    private static PlatformManager instance;
    
    public static PlatformManager getInstance() { ... }
    
    public PlatformProvider getProviderForPlatform(Platform platform);
    
    public List<Platform> getAllPlatforms();
    
    public Platform getPlatformById(String id);
}
```

### Usage

```java
// Get provider for a platform
PlatformProvider provider = PlatformManager.getInstance()
    .getProviderForPlatform(JAVA_ANVIL_1_18);

// Get exporter
WorldExporter exporter = provider.getExporter(world, settings);

// Export world
exporter.export(outputDir, false, progressReceiver);
```

## Adding a New Platform

### Step 1: Define Platform

```java
public static final Platform MY_PLATFORM = new Platform(
    "com.example.myplatform",     // Unique ID
    "My Platform",                // Display name
    new int[] { 256 },           // Default heights
    256,                          // Standard max height
    0,                            // Min min height
    256,                          // Max max height
    -30000000, 30000000,          // X bounds
    -30000000, 30000000,          // Y bounds
    EnumSet.of(Capability.BIOMES, Capability.NAMED_BLOCKS),
    Arrays.asList(GameType.SURVIVAL, GameType.CREATIVE),
    Arrays.asList(Generator.DEFAULT),
    Collections.singleton(DIM_NORMAL)
);
```

### Step 2: Implement Provider

```java
public class MyPlatformProvider extends AbstractPlatformProvider 
    implements BlockBasedPlatformProvider {
    
    public MyPlatformProvider() {
        super(Version.VERSION, 
              Collections.singletonList(MY_PLATFORM), 
              "MyPlatformProvider");
    }
    
    @Override
    public WorldExporter getExporter(World2 world, WorldExportSettings settings) {
        return new MyWorldExporter(world, settings);
    }
    
    @Override
    public Chunk createChunk(Platform platform, int x, int z, int min, int max) {
        return new MyChunk(x, z, min, max);
    }
    
    @Override
    public ChunkStore getChunkStore(Platform platform, File worldDir, int dim) {
        return new MyChunkStore(worldDir, dim);
    }
    
    @Override
    public PostProcessor getPostProcessor(Platform platform) {
        return new MyPostProcessor();
    }
}
```

### Step 3: Register in Plugin Descriptor

In `org.pepsoft.worldpainter.plugins`:

```json
{
  "name": "My Platform Plugin",
  "version": "1.0.0",
  "classes": [
    "com.example.MyPlatformProvider"
  ]
}
```

## Platform Selection UI

The export dialog shows platforms from `PlatformManager.getAllPlatforms()`:

```
┌─ Export World ────────────────────────────┐
│                                           │
│  Platform: [Minecraft 1.18+          ▼]   │
│                                           │
│  World height: [-64 to 320]               │
│                                           │
│  [Export]  [Cancel]                       │
└───────────────────────────────────────────┘
```

## See Also

- [05-EXPORT.md](05-EXPORT.md) — Export pipeline
- [07-PLUGINS.md](07-PLUGINS.md) — Plugin system
- [../hytale/README.md](../hytale/README.md) — Hytale platform details
