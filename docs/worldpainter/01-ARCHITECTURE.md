# WorldPainter Architecture

## Core Domain Model

```
World2
  └── Dimension (0..n, keyed by Anchor)
        └── Tile (0..n, keyed by Point coordinates)
              ├── heightMap (terrain elevations)
              ├── terrain (surface material types)
              ├── waterLevel (water/lava levels)
              ├── layerData (byte/nibble layers)
              └── bitLayerData (bit layers)
```

## Key Classes

### World2

**Location:** [WPCore/.../World2.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/World2.java)

The root container for an entire WorldPainter project.

```java
public class World2 extends InstanceKeeper implements Serializable {
    private String name;
    private Platform platform;           // Target export format
    private GameType gameType;           // Survival, Creative, etc.
    private Point spawnPoint;
    private Map<Anchor, Dimension> dimensions;
    private boolean allowCheats;
    private Difficulty difficulty;
    // ... export settings, history, etc.
}
```

**Key Responsibilities:**
- Holds all dimensions (surfaces, nether, end, caves)
- Stores global settings (spawn, game mode, difficulty)
- Tracks target platform for export
- Manages undo/redo history

### Dimension

**Location:** [WPCore/.../Dimension.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/Dimension.java)

Represents a game dimension or sub-dimension (ceiling, cave floor).

```java
public class Dimension extends InstanceKeeper implements TileProvider, Serializable {
    private final Anchor anchor;
    private final Map<Point, Tile> tiles = new HashMap<>();
    private TileFactory tileFactory;
    private int minHeight, maxHeight;
    private int contourSeparation;
    private Border border;
    private MixedMaterial subsurfaceMaterial;
    // ... layers, overlays, settings
}
```

**Key Responsibilities:**
- Contains all tiles for a dimension
- Provides tile factory for new tile generation
- Manages dimension-specific settings (borders, overlays)
- Tracks enabled layers

### Dimension.Anchor

**Location:** [WPCore/.../Dimension.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/Dimension.java) (inner class)

Uniquely identifies a dimension within a world.

```java
public static final class Anchor implements Serializable {
    public final int dim;        // 0=Surface, 1=Nether, 2=End
    public final Role role;      // DETAIL, MASTER, CAVE_FLOOR, FLOATING_FLOOR
    public final boolean invert; // Ceiling vs floor
    public final int id;         // Distinguishes multiple same-type dims
}
```

**Role Types:**
| Role | Description |
|------|-------------|
| `DETAIL` | Primary editing surface (default) |
| `MASTER` | Master dimension for multi-resolution |
| `CAVE_FLOOR` | Cave floor dimension |
| `FLOATING_FLOOR` | Floating islands floor |

### Tile

**Location:** [WPCore/.../Tile.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/Tile.java)

A 128×128 block area storing all terrain data.

```java
public class Tile extends InstanceKeeper implements Serializable {
    private final int x, y;                    // Tile coordinates
    private short[] heightMap;                 // 16-bit heights (0-65535)
    private int[] tallHeightMap;               // 32-bit heights (extended)
    private byte[] terrain;                    // Terrain type per column
    private short[] waterLevel;                // Water level per column
    private Map<Layer, byte[]> layerData;      // NIBBLE/BYTE layer storage
    private Map<Layer, BitSet> bitLayerData;   // BIT layer storage
}
```

**Storage Layout:**
- `heightMap[y * 128 + x]` — Height at (x, y) within tile
- `terrain[y * 128 + x]` — Terrain enum ordinal
- `layerData.get(layer)[index]` — Layer value at index

## Platform

**Location:** [WPCore/.../Platform.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/Platform.java)

Describes a target export format with its capabilities.

```java
public final class Platform implements Serializable {
    public final String id;                    // Unique identifier
    public final String displayName;
    public final int minMinHeight, maxMaxHeight;  // Build limits
    public final Set<Capability> capabilities;
    public final List<GameType> supportedGameTypes;
}
```

**Registered Platforms:**
| Platform ID | Display Name | Height Range |
|-------------|--------------|--------------|
| `org.pepsoft.mcregion` | Minecraft 1.1 | 0-128 |
| `org.pepsoft.anvil` | Minecraft 1.2-1.14 | 0-256 |
| `org.pepsoft.anvil.1.15` | Minecraft 1.15 | 0-256 |
| `org.pepsoft.anvil.1.17` | Minecraft 1.17 | -64 to 320 |
| `org.pepsoft.anvil.1.18` | Minecraft 1.18+ | -64 to 320 |
| `org.pepsoft.hytale` | Hytale | 0-320 |

## Constants

**Location:** [WPCore/.../Constants.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/Constants.java)

```java
public static final int TILE_SIZE = 128;
public static final int TILE_SIZE_BITS = 7;
public static final int TILE_SIZE_MASK = 0x7f;

public static final int DIM_NORMAL = 0;
public static final int DIM_NETHER = 1;
public static final int DIM_END = 2;
public static final int DIM_NORMAL_CEILING = 3;
public static final int DIM_NETHER_CEILING = 4;
public static final int DIM_END_CEILING = 5;
```

## Module Structure

```
WorldPainter/
├── WPCore/              # Core library (no GUI dependencies)
│   ├── Domain objects (World2, Dimension, Tile)
│   ├── Layers and exporters
│   ├── Platform providers
│   └── Plugin system
├── WPGUI/               # Swing GUI application
│   ├── Main application (App.java)
│   ├── Operations/tools
│   ├── Dialogs
│   └── Views (2D, 3D)
└── WPDynmapPreviewer/   # Dynmap integration
```

## Class Diagram

```
                    ┌─────────────┐
                    │   World2    │
                    │─────────────│
                    │ name        │
                    │ platform    │
                    │ spawnPoint  │
                    └──────┬──────┘
                           │ 1:n
                           ▼
┌──────────┐       ┌─────────────┐
│  Anchor  │◄──────│  Dimension  │
│──────────│       │─────────────│
│ dim      │       │ tiles       │
│ role     │       │ tileFactory │
│ invert   │       │ minHeight   │
│ id       │       │ maxHeight   │
└──────────┘       └──────┬──────┘
                          │ 1:n
                          ▼
                   ┌─────────────┐
                   │    Tile     │
                   │─────────────│
                   │ x, y        │
                   │ heightMap   │
                   │ terrain     │
                   │ waterLevel  │
                   │ layerData   │
                   └─────────────┘
```

## See Also

- [02-DATA-MODEL.md](02-DATA-MODEL.md) — Detailed tile structure
- [06-PLATFORMS.md](06-PLATFORMS.md) — Platform provider system
