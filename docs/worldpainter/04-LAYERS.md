# WorldPainter Layer System

## Overview

Layers are overlay data painted on top of terrain. Each layer affects world export in a specific way (caves, forests, biomes, etc.).

**Base Class:** [WPCore/.../layers/Layer.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/layers/Layer.java)

## Layer Data Sizes

| DataSize | Bits | Values | Storage | Use Case |
|----------|------|--------|---------|----------|
| `BIT` | 1 | 0/1 | `BitSet` | On/off layers (Frost, Void) |
| `BIT_PER_CHUNK` | 1/256 | 0/1 | `BitSet` (64 bits) | Per-chunk flags (Populate) |
| `NIBBLE` | 4 | 0-15 | `byte[]` packed | Intensity layers (Caves, Resources) |
| `BYTE` | 8 | 0-255 | `byte[]` | High-precision (Biome) |
| `NONE` | 0 | N/A | No storage | Computed layers |

## Built-in Layers

### Terrain Modification

| Layer | DataSize | Description |
|-------|----------|-------------|
| `Caves` | NIBBLE | Small random caves (0-15 intensity) |
| `Caverns` | NIBBLE | Large cavern chambers |
| `Chasms` | NIBBLE | Deep vertical chasms |
| `Resources` | NIBBLE | Ore generation intensity |

### Surface Layers

| Layer | DataSize | Description |
|-------|----------|-------------|
| `Frost` | BIT | Snow/ice on terrain |
| `Void` | BIT | Remove all blocks |
| `Bedrock` | BIT | Force bedrock layer |
| `River` | BIT | River water flow |

### Vegetation (TreeLayer subclasses)

| Layer | DataSize | Description |
|-------|----------|-------------|
| `DeciduousForest` | NIBBLE | Oak/birch trees |
| `PineForest` | NIBBLE | Spruce trees |
| `Jungle` | NIBBLE | Jungle trees |
| `SwampLand` | NIBBLE | Swamp trees |

### Control Layers

| Layer | DataSize | Description |
|-------|----------|-------------|
| `Biome` | BYTE | Minecraft biome ID |
| `Annotations` | NIBBLE | Color markers (not exported) |
| `Populate` | BIT_PER_CHUNK | Enable chunk population |
| `ReadOnly` | BIT_PER_CHUNK | Preserve existing blocks |
| `Delete` | BIT | Delete chunks on export |

## Custom Layers

**Base Class:** [WPCore/.../layers/CustomLayer.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/layers/CustomLayer.java)

```java
public abstract class CustomLayer extends Layer {
    private Paint paint;           // Display color/pattern
    private float opacity = 0.5f;  // Display opacity
    private int biome = -1;        // Optional biome override
    private String palette;        // Group in layer palette
    private int exportIndex;       // Export order
}
```

### Custom Layer Types

| Type | Location | Description |
|------|----------|-------------|
| `GroundCoverLayer` | layers/groundcover/ | Surface material layer |
| `PlantLayer` | layers/plants/ | Plants with growth stages |
| `TunnelLayer` | layers/tunnel/ | Custom caves/tunnels |
| `Bo2Layer` | layers/ | Schematic/BO2 object placement |
| `CombinedLayer` | layers/ | Combines multiple layers |
| `UndergroundPocketsLayer` | layers/pockets/ | Underground material pockets |

### GroundCoverLayer

Places a material layer on the terrain surface.

```java
public class GroundCoverLayer extends CustomLayer {
    private MixedMaterial material;    // Surface material
    private int thickness;             // Layer depth
    private EdgeShape edgeShape;       // SMOOTH, ROUNDED, SHEER
    private NoiseSettings noiseSettings;  // Optional variation
}
```

### PlantLayer

Places plants with random selection and growth stages.

```java
public class PlantLayer extends CustomLayer {
    private List<PlantSettings> plants;  // Plant types + weights
    private int density;                  // Plants per chunk
    private boolean randomGrowth;         // Random growth stage
}
```

### TunnelLayer

Creates caves, tunnels, or floating dimensions.

```java
public class TunnelLayer extends CustomLayer {
    private int minLevel, maxLevel;       // Vertical range
    private int floorDepth, roofDepth;    // Floor/roof thickness
    private MixedMaterial floorMaterial;
    private MixedMaterial roofMaterial;
    private MixedMaterial wallMaterial;
    private boolean removeWater;
    private boolean floodWithLava;
}
```

### Bo2Layer

Places BO2/BO3/schematic objects.

```java
public class Bo2Layer extends CustomLayer {
    private List<WPObject> objects;       // Objects to place
    private int density;                   // Objects per chunk
    private WPObjectPlacement placement;  // Placement mode
}
```

## Layer Exporters

### Exporter Interface Hierarchy

```
LayerExporter (marker interface)
├── FirstPassLayerExporter      # Called per-chunk with chunk creation
├── SecondPassLayerExporter     # Called after all chunks exist
└── IncidentalLayerExporter     # Point-by-point during terrain
```

### FirstPassLayerExporter

**Location:** [WPCore/.../exporting/FirstPassLayerExporter.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/exporting/FirstPassLayerExporter.java)

Called during initial chunk generation. Cannot access neighboring chunks.

```java
public interface FirstPassLayerExporter<L extends Layer> extends LayerExporter<L> {
    void render(Dimension dimension, Tile tile, Chunk chunk, Platform platform);
}
```

**Examples:** Frost, Biome, Resources

### SecondPassLayerExporter

**Location:** [WPCore/.../exporting/SecondPassLayerExporter.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/exporting/SecondPassLayerExporter.java)

Called after first pass. Can access all chunks for cross-chunk operations.

```java
public interface SecondPassLayerExporter<L extends Layer> extends LayerExporter<L> {
    enum Stage { CARVE, ADD_FEATURES }
    
    Set<Stage> getStages();
    
    void render(Dimension dimension, Tile tile, Chunk chunk, 
                MinecraftWorld world, Platform platform, Stage stage);
}
```

**Stages:**
- `CARVE` — Remove blocks (caves, tunnels)
- `ADD_FEATURES` — Add blocks (trees, structures)

**Examples:** Caves, Trees, Bo2Layer

### IncidentalLayerExporter

**Location:** [WPCore/.../exporting/IncidentalLayerExporter.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/exporting/IncidentalLayerExporter.java)

Applied block-by-block during terrain export.

```java
public interface IncidentalLayerExporter<L extends Layer> extends LayerExporter<L> {
    Material getMaterial(Dimension dimension, int x, int y, int z);
}
```

**Examples:** GroundCoverLayer

## Layer Export Order

1. **First Pass:** Terrain + FirstPassLayerExporters
2. **Second Pass CARVE:** Caves, Chasms, Tunnels
3. **Second Pass ADD_FEATURES:** Trees, Objects, Structures
4. **Post-Processing:** Block rule fixes, lighting

Layers are sorted by `priority` field and `exportIndex` for custom layers.

## Layer Rendering (Display)

**Interface:** [WPCore/.../layers/LayerRenderer.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/layers/LayerRenderer.java)

```java
public interface LayerRenderer {
    int getPixelColour(int x, int y, int underlyingColour, 
                       Dimension dimension, ColourScheme colourScheme);
    
    int getPixelColour(int x, int y, int underlyingColour, 
                       boolean withAlpha);  // Fast path
}
```

## Hytale Integration Notes

| WP Layer | Hytale Mapping |
|----------|----------------|
| `Biome` | Zone biome assignment |
| `Resources` | Block type variations |
| `Caves` | Terrain gen (threshold) |
| `Frost` | Could map to snow blocks |
| `Bo2Layer` | Prefab placement |

Layers without direct Hytale equivalents:
- `Populate` (MC-specific chunk population)
- `ReadOnly` (MC merge feature)

## See Also

- [05-EXPORT.md](05-EXPORT.md) — Export pipeline details
- [03-TERRAIN.md](03-TERRAIN.md) — Terrain system
