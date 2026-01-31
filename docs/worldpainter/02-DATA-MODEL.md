# WorldPainter Data Model

## Tile Structure

A **Tile** is a 128×128 block column storing all per-block data.

**Location:** [WPCore/.../Tile.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/Tile.java)

### Storage Arrays

| Field | Type | Size | Description |
|-------|------|------|-------------|
| `heightMap` | `short[]` | 16,384 | Height per column (0-65535) |
| `tallHeightMap` | `int[]` | 16,384 | Extended height (when >65535) |
| `terrain` | `byte[]` | 16,384 | Terrain enum ordinal |
| `waterLevel` | `short[]` | 16,384 | Water/lava level per column |
| `tallWaterLevel` | `int[]` | 16,384 | Extended water level |
| `layerData` | `Map<Layer, byte[]>` | varies | NIBBLE/BYTE layer storage |
| `bitLayerData` | `Map<Layer, BitSet>` | varies | BIT layer storage |

### Coordinate Indexing

```java
// Convert (x, y) to array index
int index = (y & TILE_SIZE_MASK) << TILE_SIZE_BITS | (x & TILE_SIZE_MASK);
// Simplified: index = y * 128 + x (within tile)

// Get world coordinates from tile coordinates
int worldX = tile.getX() * TILE_SIZE + localX;
int worldY = tile.getY() * TILE_SIZE + localY;
```

### Key Methods

```java
// Height access
int getHeight(int x, int y);
void setHeight(int x, int y, int height);
float getRawHeight(int x, int y);  // Floating-point precision

// Terrain access
Terrain getTerrain(int x, int y);
void setTerrain(int x, int y, Terrain terrain);

// Water access
int getWaterLevel(int x, int y);
void setWaterLevel(int x, int y, int level);
boolean isBitLayerValue(Layer layer, int x, int y);  // FloodWithLava check

// Layer access
int getLayerValue(Layer layer, int x, int y);       // NIBBLE/BYTE
void setLayerValue(Layer layer, int x, int y, int value);
boolean getBitLayerValue(Layer layer, int x, int y); // BIT
void setBitLayerValue(Layer layer, int x, int y, boolean value);

// Layer queries
Set<Layer> getLayers();
boolean hasLayer(Layer layer);
void clearLayerData(Layer layer);
```

## Height Map System

**Interface:** [WPCore/.../HeightMap.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/HeightMap.java)

Height maps define terrain elevation procedurally or from images.

```java
public interface HeightMap extends Cloneable, Serializable {
    double getHeight(int x, int y);
    double getHeight(float x, float y);  // Interpolated
    long getSeed();
    double getBaseHeight();
    double getRange();
    Rectangle getExtent();  // null = unbounded
    boolean isConstant();
}
```

### Height Map Implementations

| Type | Location | Description |
|------|----------|-------------|
| `ConstantHeightMap` | heightMaps/ | Single fixed value |
| `NoiseHeightMap` | heightMaps/ | Perlin/simplex noise |
| `BitmapHeightMap` | heightMaps/ | From grayscale image |
| `SumHeightMap` | heightMaps/ | A + B |
| `ProductHeightMap` | heightMaps/ | A × B |
| `DifferenceHeightMap` | heightMaps/ | A - B |
| `MaximisingHeightMap` | heightMaps/ | max(A, B) |
| `MinimisingHeightMap` | heightMaps/ | min(A, B) |
| `TransformingHeightMap` | heightMaps/ | Scale/rotate/translate |
| `DisplacementHeightMap` | heightMaps/ | Displacement mapping |
| `SlopeHeightMap` | heightMaps/ | Slope from another map |
| `BicubicHeightMap` | heightMaps/ | Smooth interpolation |

### Chaining API

```java
HeightMap combined = noise
    .plus(constant)         // Add
    .times(scale)           // Multiply
    .smoothed(3)            // Bicubic smoothing
    .clamped(0, 255);       // Clamp range
```

## Tile Factory

**Interface:** [WPCore/.../TileFactory.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/TileFactory.java)

Creates new tiles with initial terrain data.

```java
public interface TileFactory extends Serializable {
    Tile createTile(int x, int y);
    int getMinHeight();
    int getMaxHeight();
    void setMinMaxHeight(int min, int max, HeightTransform transform);
}
```

### HeightMapTileFactory

**Location:** [WPCore/.../HeightMapTileFactory.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/HeightMapTileFactory.java)

The standard tile factory using height maps and themes.

```java
public class HeightMapTileFactory extends AbstractTileFactory {
    private HeightMap heightMap;
    private Theme theme;
    private int waterHeight;
    private boolean floodWithLava;
    private boolean beaches;
    
    @Override
    public Tile createTile(int x, int y) {
        Tile tile = new Tile(x, y, minHeight, maxHeight);
        for (int dx = 0; dx < TILE_SIZE; dx++) {
            for (int dy = 0; dy < TILE_SIZE; dy++) {
                int worldX = x * TILE_SIZE + dx;
                int worldY = y * TILE_SIZE + dy;
                int height = (int) heightMap.getHeight(worldX, worldY);
                tile.setHeight(dx, dy, height);
                theme.apply(tile, dx, dy);  // Sets terrain + layers
            }
        }
        return tile;
    }
}
```

## Layer Data Storage

### Data Sizes

| DataSize | Bits/Block | Values | Storage |
|----------|------------|--------|---------|
| `BIT` | 1 | 0-1 | `BitSet` |
| `BIT_PER_CHUNK` | 1/256 | 0-1 | `BitSet` (64 bits) |
| `NIBBLE` | 4 | 0-15 | `byte[]` (packed) |
| `BYTE` | 8 | 0-255 | `byte[]` |
| `NONE` | 0 | N/A | No storage |

### NIBBLE Packing

```java
// Two values per byte
int index = (y * 128 + x) / 2;
int shift = ((y * 128 + x) % 2) * 4;

// Read
int value = (layerData[index] >> shift) & 0x0F;

// Write
layerData[index] = (byte) ((layerData[index] & ~(0x0F << shift)) | (value << shift));
```

### BIT Storage

```java
// Using BitSet
BitSet bits = bitLayerData.get(layer);

// Read
boolean value = bits.get(y * 128 + x);

// Write
bits.set(y * 128 + x, value);
```

## Material

**Location:** [WPCore/.../Material.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/Material.java)

Represents a Minecraft block with properties.

```java
public final class Material implements Serializable {
    private final String name;        // e.g., "minecraft:stone"
    private final Map<String, String> properties;  // Block state
    
    // Predefined materials
    public static final Material AIR = get("minecraft:air");
    public static final Material STONE = get("minecraft:stone");
    public static final Material GRASS_BLOCK = get("minecraft:grass_block");
    // ... hundreds more
}
```

### Material Properties

```java
// Get material with properties
Material oakLog = Material.get("minecraft:oak_log", "axis", "y");
Material waterLevel5 = Material.get("minecraft:water", "level", "5");

// Query properties
boolean isOpaque = material.opaque;
boolean isTransparent = material.transparent;
boolean isFluid = material.isNamed("minecraft:water") || material.isNamed("minecraft:lava");
```

## World Coordinates

```
World Coordinates (blocks):
  X: -30,000,000 to +30,000,000
  Y: -30,000,000 to +30,000,000  
  Z: minHeight to maxHeight (vertical)

Tile Coordinates:
  tileX = worldX >> 7  (divide by 128)
  tileY = worldY >> 7

Local Coordinates (within tile):
  localX = worldX & 0x7F  (0-127)
  localY = worldY & 0x7F  (0-127)

Chunk Coordinates (for export):
  chunkX = worldX >> 4   (divide by 16)
  chunkZ = worldY >> 4   (WP uses Y for horizontal)
```

## See Also

- [03-TERRAIN.md](03-TERRAIN.md) — Terrain types and themes
- [04-LAYERS.md](04-LAYERS.md) — Layer system details
