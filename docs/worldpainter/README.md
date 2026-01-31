# WorldPainter Technical Documentation

This documentation covers WorldPainter's internal architecture, data models, and extension points. The goal is to understand the codebase for implementing native Hytale support.

## Documentation Index

| Document | Description |
|----------|-------------|
| [01-ARCHITECTURE.md](01-ARCHITECTURE.md) | Core domain model and class hierarchy |
| [02-DATA-MODEL.md](02-DATA-MODEL.md) | Tile structure, height maps, layer storage |
| [03-TERRAIN.md](03-TERRAIN.md) | Terrain types, themes, custom terrain |
| [04-LAYERS.md](04-LAYERS.md) | Layer system, data sizes, exporters |
| [05-EXPORT.md](05-EXPORT.md) | Export pipeline and chunk generation |
| [06-PLATFORMS.md](06-PLATFORMS.md) | Platform abstraction and providers |
| [07-PLUGINS.md](07-PLUGINS.md) | Plugin system and extension points |
| [08-GUI.md](08-GUI.md) | Application structure, tools, views |
| [09-SERIALIZATION.md](09-SERIALIZATION.md) | World file format (.world) |

## Key Constants

```java
// Tile dimensions
TILE_SIZE = 128           // Blocks per tile edge
TILE_SIZE_BITS = 7        // For bit shifting

// Dimension IDs
DIM_NORMAL = 0            // Overworld/Surface
DIM_NETHER = 1            // Nether
DIM_END = 2               // The End

// Layer data sizes
BIT = 1 bit per block
NIBBLE = 4 bits per block (0-15)
BYTE = 8 bits per block (0-255)
BIT_PER_CHUNK = 1 bit per 16×16 chunk
```

## WorldPainter vs Hytale Comparison

| Concept | WorldPainter | Hytale |
|---------|--------------|--------|
| **Tile/Chunk** | 128×128 tiles | 32×32×32 sections |
| **Height** | Platform-dependent (up to 4096) | 320 blocks (0-320) |
| **Storage** | Java serialization + GZIP | BSON + Zstd |
| **Dimensions** | Anchor-based (dim, role, invert) | Zones (horizontal regions) |
| **Blocks** | `Material` class | String IDs + rotation |
| **Layers** | Overlay data (Caves, Biome, etc.) | N/A (different approach) |
| **Terrain** | Enum + custom slots | JSON asset definitions |
| **Platforms** | Java MC versions, Hytale | Single target |

## Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│                        World2                            │
│  name, platform, gameType, spawnPoint, history          │
└────────────────────────┬────────────────────────────────┘
                         │ Map<Anchor, Dimension>
                         ▼
┌─────────────────────────────────────────────────────────┐
│                      Dimension                           │
│  anchor, tileFactory, border, contourSeparation         │
└────────────────────────┬────────────────────────────────┘
                         │ Map<Point, Tile>
                         ▼
┌─────────────────────────────────────────────────────────┐
│                        Tile                              │
│  heightMap, terrain, waterLevel, layerData              │
└─────────────────────────────────────────────────────────┘
```

## Key Source Locations

| Component | Path |
|-----------|------|
| Core classes | `WPCore/src/main/java/org/pepsoft/worldpainter/` |
| Layers | `WPCore/.../worldpainter/layers/` |
| Exporters | `WPCore/.../worldpainter/exporting/` |
| Platforms | `WPCore/.../worldpainter/platforms/` |
| Plugins | `WPCore/.../worldpainter/plugins/` |
| GUI | `WPGUI/src/main/java/org/pepsoft/worldpainter/` |
| Operations | `WPGUI/.../worldpainter/operations/` |
| Hytale | `WPCore/.../worldpainter/hytale/` |

## Related Documentation

- [Hytale Documentation](../hytale/README.md) — Hytale system internals
- [BUILDING.md](../../BUILDING.md) — Build instructions
- [CODESTYLE.md](../../CODESTYLE.md) — Code conventions

---
*Last updated: January 2026*
