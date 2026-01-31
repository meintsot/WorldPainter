# Hytale Technical Documentation

This documentation covers the core technical systems of Hytale extracted from decompiled source analysis. The goal is to enable WorldPainter to support native Hytale world editing, not just Minecraft-to-Hytale conversion.

## Documentation Index

| Document | Description |
|----------|-------------|
| [01-OVERVIEW.md](01-OVERVIEW.md) | High-level architecture overview |
| [02-BLOCKS.md](02-BLOCKS.md) | Block system, properties, materials, and palettes |
| [03-CHUNKS.md](03-CHUNKS.md) | Chunk storage, sections, serialization format |
| [04-TERRAIN.md](04-TERRAIN.md) | Terrain generation, zones, biomes, noise |
| [05-FLUIDS.md](05-FLUIDS.md) | Fluid system, water, lava, flow physics |
| [06-LIGHTING.md](06-LIGHTING.md) | RGB + skylight octree system |
| [07-ENVIRONMENT.md](07-ENVIRONMENT.md) | Weather, environment, time-of-day |
| [08-PREFABS.md](08-PREFABS.md) | Prefab/structure format and placement |
| [09-ENTITIES.md](09-ENTITIES.md) | Entity system, NPCs, spawning |
| [10-ASSETS.md](10-ASSETS.md) | Asset registry and JSON format |
| [11-WORLDPAINTER-FEATURES.md](11-WORLDPAINTER-FEATURES.md) | Proposed WorldPainter features |

## Key Differences from Minecraft

| Feature | Minecraft | Hytale |
|---------|-----------|--------|
| World Height | 320 blocks (-64 to 256) | 320 blocks (0 to 320), 10 sections × 32 |
| Chunk Size | 16×256×16 (16×16 sections) | 32×320×32 (10 sections of 32³) |
| Horizontal Regions | Dimensions (Overworld, Nether, End) | Zones (horizontal regions in same world) |
| Block Storage | Palette-based (4-15 bits) | Palette-based (Empty/4/8/16 bits) |
| Lighting | Separate block + sky light | 4-channel RGBS octree |
| Fluids | Part of block state | Separate FluidSection |
| File Format | Anvil (.mca) with NBT | IndexedStorage with BSON+Zstd |

## Core Constants

```java
// Chunk dimensions
CHUNK_SIZE = 32           // Blocks per axis per section
SECTION_COUNT = 10        // Vertical sections per chunk
WORLD_HEIGHT = 320        // Total world height (10 × 32)
COLUMNS_PER_CHUNK = 1024  // 32 × 32 columns

// File system
REGION_SIZE = 32          // Chunks per region axis (32×32)
COMPRESSION = Zstd        // Level 3 default

// Special block IDs
EMPTY_ID = 0              // Air/"Empty"
UNKNOWN_ID = 1            // Unknown/missing block
```

## Architecture Layers

```
┌─────────────────────────────────────────────────┐
│                    Assets                        │
│  JSON configs: Blocks, Items, Biomes, Weather   │
└───────────────────────┬─────────────────────────┘
                        │
┌───────────────────────▼─────────────────────────┐
│                  Asset Registry                  │
│  Runtime loading + tag indexing + inheritance   │
└───────────────────────┬─────────────────────────┘
                        │
┌───────────────────────▼─────────────────────────┐
│              World Generation                    │
│  Zones → Biomes → Terrain → Prefabs → Water    │
└───────────────────────┬─────────────────────────┘
                        │
┌───────────────────────▼─────────────────────────┐
│               Chunk Storage                      │
│  BlockSection + FluidSection + EnvironmentChunk │
└───────────────────────┬─────────────────────────┘
                        │
┌───────────────────────▼─────────────────────────┐
│              File Serialization                  │
│  BSON → Zstd compression → IndexedStorageFile   │
└─────────────────────────────────────────────────┘
```

## Quick Reference: Palette Types

| Type | Ordinal | Max Values | Bits/Block | Use Case |
|------|---------|------------|------------|----------|
| Empty | 0 | 1 | 0 | All same block (usually air) |
| HalfByte | 1 | 16 | 4 | Simple terrain |
| Byte | 2 | 256 | 8 | Complex terrain |
| Short | 3 | 65536 | 16 | Very diverse |

## Quick Reference: Light Channels

| Channel | Index | Bits | Description |
|---------|-------|------|-------------|
| Red | 0 | 0-3 | Block light red |
| Green | 1 | 4-7 | Block light green |
| Blue | 2 | 8-11 | Block light blue |
| Sky | 3 | 12-15 | Sky exposure |

## Source References

All information extracted from:
- `decompiled-src/com/hypixel/hytale/` - Decompiled game code
- `HytaleAssets/Server/` - Asset JSON files
- `universe/worlds/default/config.json` - World configuration

---
*Last updated: January 2026*
