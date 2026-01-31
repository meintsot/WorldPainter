# Hytale System Overview

## High-Level Architecture

Hytale uses a modular, data-driven architecture where most game content is defined through JSON asset files and loaded at runtime through a codec-based system.

## Core Systems

### 1. World Structure
- **Universe**: Top-level container holding multiple worlds
- **World**: A single playable world with its own configuration
- **Zone**: Horizontal regions within a world (replaces Minecraft dimensions)
- **Chunk**: 32×32×320 block column divided into 10 sections
- **Section**: 32×32×32 block cube with palette-based storage

### 2. Data Storage
- **ChunkStore**: Manages chunk loading/saving with async I/O
- **EntityStore**: ECS-based entity management
- **IndexedStorageFile**: Region file format with Zstd compression

### 3. Asset System
- **AssetRegistry**: Central registry for all asset types
- **AssetStore**: Type-specific asset container with JSON loading
- **BuilderCodec**: Serialization system with inheritance support

## Key Classes by Module

### World (`server/core/universe/world/`)
```
World.java              - Main world class, runs on dedicated thread
WorldConfig.java        - World settings (spawn, worldgen, storage)
WorldChunk.java         - In-memory chunk representation
BlockChunk.java         - Block data container
```

### Chunk Storage (`server/core/universe/world/chunk/`)
```
BlockSection.java       - 32³ block section with palettes
FluidSection.java       - Fluid type + level storage
EnvironmentChunk.java   - Per-column environment IDs
ChunkLightData.java     - Octree-compressed lighting
```

### World Generation (`server/worldgen/`)
```
ChunkGenerator.java     - Main generation orchestrator
ZonePatternGenerator.java - Zone placement from mask images
BiomePatternGenerator.java - Biome distribution
BlockPopulator.java     - Terrain shape generation
CavePopulator.java      - Cave carving
PrefabPopulator.java    - Structure placement
WaterPopulator.java     - Fluid filling
```

### Entity System (`server/core/universe/world/storage/`)
```
Store.java              - ECS archetype-based storage
EntityStore.java        - World entity management
ComponentType.java      - Type-safe component registration
Holder.java            - Pre-spawn entity container
Ref.java               - Live entity reference
```

### Assets (`assetstore/`)
```
AssetRegistry.java      - Global asset registry
AssetStore.java         - Typed asset container
AssetBuilderCodec.java  - JSON serialization with inheritance
```

## Data Flow: World Loading

```
1. Universe.loadWorld(name)
        │
        ▼
2. WorldConfigProvider.load(path)
   └── config.json → WorldConfig
        │
        ▼
3. ChunkStorageProvider.create()
   └── IndexedStorageChunkStorageProvider
        │
        ▼
4. ChunkStore initialization
   ├── ChunkLoader (async read)
   ├── ChunkSaver (async write)
   └── WorldGen (lazy generation)
        │
        ▼
5. World.start() → dedicated thread
```

## Data Flow: Chunk Generation

```
1. ChunkGenerator.create(seed, x, z)
        │
        ▼
2. ZonePatternGenerator.generate(x, z)
   ├── Read mask color from image
   └── Map color → Zone
        │
        ▼
3. BiomePatternGenerator.generate(zone, x, z)
   ├── TileBiome selection (weighted)
   └── CustomBiome overlay (rivers, etc.)
        │
        ▼
4. ChunkGeneratorExecution.execute(seed)
   ├── generateTintMapping()
   ├── generateEnvironmentMapping()
   ├── BlockPopulator.populate() 
   ├── CavePopulator.populate()
   ├── PrefabPopulator.populate()
   └── WaterPopulator.populate()
        │
        ▼
5. Return populated Holder<ChunkStore>
```

## Data Flow: Chunk Serialization

```
Writing:
1. WorldChunk → ChunkStore.REGISTRY.serialize()
2. BsonDocument (nested structure)
3. BsonUtil.writeToBytes() → byte[]
4. Zstd.compress() → compressed bytes
5. IndexedStorageFile.writeBlob()

Reading:
1. IndexedStorageFile.readBlob()
2. Zstd.decompress()
3. BsonUtil.readFromBuffer()
4. ChunkStore.REGISTRY.deserialize()
5. WorldChunk with all components
```

## Threading Model

| Component | Thread | Purpose |
|-----------|--------|---------|
| Universe | Main | World management, player routing |
| World | Dedicated per-world | Chunk ticking, entity updates |
| ChunkLoader | Async pool | Disk I/O for chunk loading |
| ChunkSaver | Async pool | Disk I/O for chunk saving |
| WorldGen | World thread | Lazy chunk generation |
| LightCalculation | World thread | Light propagation |

## Configuration Hierarchy

```
Server/
├── config.json              # Server settings
└── worlds/
    └── {world}/
        ├── config.json      # World settings (WorldConfig)
        ├── chunks/          # Chunk region files
        │   └── {regionX}.{regionZ}.region.bin
        └── resources/       # World resources (time, markers, etc.)

HytaleAssets/Server/
├── World/Default/           # Default worldgen configuration
│   ├── World.json          # Worldgen settings
│   ├── Zones.json          # Zone color mappings
│   └── Zones/              # Zone-specific configs
├── Environments/           # Environment assets
├── Weathers/              # Weather assets
├── Item/Items/            # Item/block definitions
└── Prefabs/               # Structure templates
```

## Key Interfaces

### IWorldGenProvider
```java
public interface IWorldGenProvider {
    IWorldGen getGenerator() throws WorldGenLoadException;
}
// Implementations: Hytale, Flat, Void, Dummy
```

### IChunkStorageProvider
```java
public interface IChunkStorageProvider {
    IChunkLoader getLoader(ChunkStore store);
    IChunkSaver getSaver(ChunkStore store);
}
// Implementations: Hytale, IndexedStorage, Migration, Empty
```

### ISpawnProvider
```java
public interface ISpawnProvider {
    Transform getSpawnPoint(World world, UUID uuid);
    Transform[] getSpawnPoints();
}
// Implementations: Global, Individual, FitToHeightMap
```

## Version Numbers

| Component | Version | Field |
|-----------|---------|-------|
| WorldConfig | 4 | `config.json` Version field |
| BlockChunk | 3 | VERSION constant |
| BlockSection | 6 | VERSION constant |
| FluidSection | 0 | VERSION constant |
| Entity | 5 | Entity.VERSION |
| Prefab JSON | 8 | version field |
| Prefab Binary | 21 | LPF format |
