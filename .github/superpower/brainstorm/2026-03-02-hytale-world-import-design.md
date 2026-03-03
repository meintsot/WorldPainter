# Hytale World Import — Design Document

**Date:** 2026-03-02  
**Goal:** Import an existing Hytale server world into WorldPainter for terrain editing, then re-export.

---

## Scope

- **Import terrain + blocks** (no entities/prefabs in v1)
- **Extract fluids** (water/lava with levels)
- **Extract environment data** (per-column environment IDs)
- **Single dimension** (overworld only)
- **Custom Hytale import dialog** with world stats and block mapping preview
- **Full pipeline**: new classes integrated into existing MapImporter framework

---

## Architecture — New & Modified Classes

### New Classes

| Class | Package | Role |
|-------|---------|------|
| `HytaleBsonChunkDeserializer` | `o.p.wp.hytale` | Reads BSON chunk bytes → extracts blocks, heightmaps, fluids, environments |
| `HytaleMapImporter` | `o.p.wp.hytale` | Extends `MapImporter`. Orchestrates import pipeline, builds `World2` |
| `HytaleMapImportDialog` | `o.p.wp.importing` (WPGUI) | Custom Swing dialog: folder picker, world stats, import options |
| `HytaleImportBlockMapper` | `o.p.wp.hytale` | Reverse-maps Hytale block ID → `HytaleTerrain` → WP `Terrain` |

### Modified Classes

| Class | Change |
|-------|--------|
| `HytalePlatformProvider` | Implement `MapImporterProvider` interface, return `HytaleMapImporter` |
| `App.java` | Wire Hytale import dialog into the import flow (detect Hytale platform, show custom dialog) |

---

## Data Flow

```
User selects Hytale world folder
         │
         ▼
HytaleMapImportDialog
  ├── HytalePlatformProvider.identifyMap() → confirms Hytale world
  ├── Scans region files → counts chunks, computes world bounds
  ├── Quick-samples chunks → shows block diversity stats
  └── User clicks Import
         │
         ▼
HytaleMapImporter.doImport(progressReceiver)
  │
  ├── 1. Create World2 (platform=HYTALE, minHeight=0, maxHeight=320)
  ├── 2. Iterate all region files in chunks/ directory
  │     └── For each chunk:
  │           ├── HytaleRegionFile.read(blobIndex) → compressed bytes
  │           ├── Zstd decompress → raw BSON
  │           └── HytaleBsonChunkDeserializer.deserialize(bsonBytes)
  │                 ├── Parse BlockChunk → heightmap[1024], tintmap[1024]
  │                 ├── Parse ChunkColumn → BlockSection[10] palettes
  │                 ├── Parse FluidSection[10] → fluid types + levels
  │                 └── Parse EnvironmentChunk → environment IDs
  │
  ├── 3. For each deserialized chunk:
  │     ├── HytaleImportBlockMapper: surface block → WP Terrain
  │     ├── Build WP Tile (128x128 = 4x4 Hytale chunks)
  │     ├── Set heightmap from BlockChunk heightmap
  │     ├── Set terrain types per column
  │     ├── Set water levels from fluid data
  │     ├── Set HytaleTerrainLayer for native terrain index
  │     └── Set HytaleEnvironmentLayer for environment data
  │
  ├── 4. Store importedFrom = worldDir on World2
  └── 5. Return World2
```

---

## BSON Chunk Deserializer

### Output Records

```java
record DeserializedChunk(
    int chunkX, int chunkZ,
    short[] heightmap,           // 1024 entries (32×32 columns)
    int[] tintmap,               // 1024 entries
    BlockSectionData[] sections, // 10 sections
    FluidSectionData[] fluids,   // 10 sections
    int[] environmentIds         // 1024 entries per column
)

record BlockSectionData(
    int[] blockIds,        // palette-resolved (32768 entries)
    String[] palette,      // block name strings
    int[] rotations        // 32768 rotation values
)

record FluidSectionData(
    int[] fluidTypes,      // palette-resolved (32768 entries)
    String[] palette,      // fluid name strings
    byte[] levels          // 16384 fluid levels
)
```

### Parsing Strategy

Mirror the serializer's BSON structure:
- `Components.BlockChunk` → heightmap + tintmap binary
- `Components.ChunkColumn.Sections[].Block` → palette type + palette entries + packed data
- `Components.ChunkColumn.Sections[].Fluid` → fluid palette + levels
- `Components.EnvironmentChunk` → environment column data

Palette unpacking uses the same bit-width logic as the serializer: empty (0), half-byte (4-bit), byte (8-bit), short (16-bit).

---

## Block → Terrain Mapping

`HytaleImportBlockMapper` handles reverse mapping:

1. Look up block by ID string via `HytaleBlockRegistry`
2. Map to `HytaleTerrain` via `HytaleTerrainHelper.fromBlockId()`
3. Fall back by block group prefix (`Rock_*` → stone, `Soil_*` → dirt/grass)
4. Unknown blocks → configurable fallback (default: `Terrain.STONE`)
5. Store native `HytaleTerrain` index on `HytaleTerrainLayer` for round-trip fidelity

---

## Import Dialog

```
┌──────────────────────────────────────────┐
│  Import Hytale World                     │
├──────────────────────────────────────────┤
│  World folder: [_______________] [Browse]│
│                                          │
│  ┌─ World Statistics ──────────────────┐ │
│  │ Chunks found: 1,024                 │ │
│  │ World bounds: -512,0 to 512,320     │ │
│  │ Region files: 4                     │ │
│  │ Unique blocks: 47                   │ │
│  │ Mapped blocks: 45/47 (96%)          │ │
│  └─────────────────────────────────────┘ │
│                                          │
│  ☐ Mark imported chunks as read-only     │
│                                          │
│           [Cancel]  [Import]             │
└──────────────────────────────────────────┘
```

- Folder selection triggers async scan
- Stats panel updates as scan completes
- Import button disabled until scan succeeds
- Progress shown via standard `ProgressDialog`

---

## Tile Aggregation

WP tiles are 128×128 blocks. Hytale chunks are 32×32. Each WP tile covers a 4×4 grid of Hytale chunks.

- Hytale chunk (cx, cz) maps to WP tile: `tileX = cx >> 2`, `tileZ = cz >> 2`
- Within the WP tile, the 32×32 chunk fills offset: `offX = (cx & 3) * 32`, `offZ = (cz & 3) * 32`

---

## Future Extensions (out of scope for v1)

- Entity import (mob positions, block entities)
- Embedded prefab data preservation
- Standalone `.prefab.json` / `.lpf` file import as custom objects
- Multi-zone / multi-dimension support
- Block mapping customization UI
