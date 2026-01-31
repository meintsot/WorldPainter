# WorldPainter Terrain System

## Terrain Enum

**Location:** [WPCore/.../Terrain.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/Terrain.java)

Terrain types define the surface and subsurface materials for each column.

### Built-in Terrain Types (~60)

| Category | Terrain Types |
|----------|--------------|
| **Grass/Dirt** | `GRASS`, `BARE_GRASS`, `DIRT`, `PERMADIRT`, `PODZOL`, `MYCELIUM` |
| **Sand** | `SAND`, `RED_SAND`, `GRAVEL`, `CLAY` |
| **Stone** | `STONE`, `ROCK`, `COBBLESTONE`, `MOSSY_COBBLESTONE`, `OBSIDIAN`, `BEDROCK` |
| **Stone Variants** | `GRANITE`, `DIORITE`, `ANDESITE`, `STONE_MIX`, `DEEPSLATE` |
| **Desert/Mesa** | `DESERT`, `RED_DESERT`, `MESA`, `SANDSTONE`, `RED_SANDSTONE` |
| **Terracotta** | `HARDENED_CLAY`, `WHITE_STAINED_CLAY` ... `BLACK_STAINED_CLAY` (16 colors) |
| **Nether** | `NETHERRACK`, `SOUL_SAND`, `NETHERLIKE`, `BASALT`, `BLACKSTONE` |
| **Nether 1.16** | `CRIMSON_NYLIUM`, `WARPED_NYLIUM`, `SOUL_SOIL` |
| **Other** | `WATER`, `LAVA`, `DEEP_SNOW`, `BEACHES`, `END_STONE`, `MAGMA` |
| **Custom** | `CUSTOM_1` through `CUSTOM_96` (96 slots) |

### Terrain Methods

```java
public enum Terrain {
    // Get block material at coordinates
    Material getMaterial(Platform platform, long seed, int x, int y, int z, int height);
    
    // Get surface decoration (flowers, grass, etc.)
    WPObject getSurfaceObject(Platform platform, long seed, int x, int y, int waterBlocksAbove);
    
    // Default Minecraft biome for this terrain
    int getDefaultBiome();
    
    // Query methods
    boolean isCustom();
    boolean isConfigured();
    int getCustomTerrainIndex();  // 0-95 for CUSTOM_1 through CUSTOM_96
}
```

### Material Generation

Each terrain type implements `getMaterial()` to return appropriate blocks:

```java
// Simple terrain (fixed material)
case STONE:
    return Material.STONE;

// Complex terrain (noise-based mixing)
case STONE_MIX:
    float noise = PerlinNoise.noise(seed, x / 16.0, y / 16.0, z / 16.0);
    if (noise < 0.25) return Material.GRANITE;
    if (noise < 0.50) return Material.DIORITE;
    if (noise < 0.75) return Material.ANDESITE;
    return Material.STONE;

// Mesa (layered with noise)
case MESA:
    int layer = (z + noiseOffset) % 8;
    return MESA_LAYERS[layer];
```

## Custom Terrain

**Helper:** [WPCore/.../CustomTerrainHelper.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/CustomTerrainHelper.java)

Custom terrains (`CUSTOM_1` to `CUSTOM_96`) delegate to `MixedMaterial` objects.

```java
// Register a custom terrain
MixedMaterial material = new MixedMaterial("My Terrain", ...);
Terrain.setCustomMaterial(0, material);  // CUSTOM_1

// Usage
Terrain.CUSTOM_1.getMaterial(platform, seed, x, y, z, height);
// Delegates to: Terrain.customMaterials[0].getMaterial(...)
```

### MixedMaterial

**Location:** [WPCore/.../MixedMaterial.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/MixedMaterial.java)

Defines complex block mixtures for custom terrain.

```java
public class MixedMaterial implements Serializable {
    private final String name;
    private final Row[] rows;           // Material entries
    private final Mode mode;
    private final Colour colour;        // Display color
    private final int biome;            // Optional biome association
}
```

### MixedMaterial Modes

| Mode | Description | Use Case |
|------|-------------|----------|
| `SIMPLE` | Single material | Stone floor |
| `NOISE` | Random selection by weight | Grass with patches of dirt |
| `BLOBS` | 3D Perlin noise blobs | Ore distribution |
| `LAYERED` | Horizontal layers | Sedimentary rock |

### Row Definition

```java
public static class Row {
    public final Material material;   // Block type
    public final int count;           // Occurrence weight (NOISE mode)
    public final float scale;         // Noise scale (BLOBS mode)
}
```

### Example: Mixed Stone

```java
MixedMaterial stoneMix = new MixedMaterial(
    "Stone Mix",
    new Row[] {
        new Row(Material.STONE, 50),     // 50% stone
        new Row(Material.GRANITE, 20),   // 20% granite
        new Row(Material.DIORITE, 15),   // 15% diorite
        new Row(Material.ANDESITE, 15)   // 15% andesite
    },
    Mode.NOISE,
    null,  // No custom color
    -1     // No biome
);
```

## Theme System

**Interface:** [WPCore/.../themes/Theme.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/themes/Theme.java)

Themes automatically assign terrain types and layers based on height.

```java
public interface Theme extends Serializable, Cloneable {
    void apply(Tile tile, int x, int y);  // Apply theme to tile point
    int getWaterHeight();
    void setWaterHeight(int height);
    long getSeed();
    void setSeed(long seed);
    int getMinHeight(), getMaxHeight();
}
```

### SimpleTheme

**Location:** [WPCore/.../themes/SimpleTheme.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/themes/SimpleTheme.java)

Height-based terrain assignment with optional randomization.

```java
public class SimpleTheme implements Theme {
    private SortedMap<Integer, Terrain> terrainRanges;  // Height → Terrain
    private Map<Filter, Layer> layerMap;                // Filter → Layer
    private boolean randomise;                          // Perlin noise offset
    private boolean beaches;                            // Auto beach terrain
}
```

### Default Theme Configuration

```
Height Range                Terrain
──────────────────────────────────────
waterHeight → +32          GRASS
waterHeight + 32 → +48     STONE_MIX (or PERMADIRT)
waterHeight + 48 → +80     STONE_MIX
waterHeight + 80 → ∞       DEEP_SNOW
waterHeight + 64 → ∞       Frost layer applied
```

### FancyTheme

**Location:** [WPCore/.../themes/impl/fancy/FancyTheme.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/themes/impl/fancy/FancyTheme.java)

Climate-based terrain selection using temperature and humidity.

```java
public class FancyTheme implements Theme {
    private HeightMap temperatureMap;
    private HeightMap humidityMap;
    // Climate → Terrain mapping
    // Slope-based rocky terrain
    // Forest layers based on climate
}
```

## Terrain Export Flow

In [WorldPainterChunkFactory.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/exporting/WorldPainterChunkFactory.java):

```java
for each column (x, y) in chunk:
    Tile tile = dimension.getTile(tileX, tileY);
    int height = tile.getHeight(localX, localY);
    Terrain terrain = tile.getTerrain(localX, localY);
    int waterLevel = tile.getWaterLevel(localX, localY);
    
    for z from minHeight to height:
        if (z < height - topLayerDepth) {
            // Subsurface material
            block = dimension.getSubsurfaceMaterial().getMaterial(...);
        } else {
            // Surface terrain
            block = terrain.getMaterial(platform, seed, x, y, z, height);
        }
        chunk.setBlock(x, z, y, block);
    
    // Water/lava fill
    if (height < waterLevel) {
        Material fluid = tile.isBitLayerValue(FloodWithLava, x, y) 
            ? Material.LAVA : Material.WATER;
        for z from height + 1 to waterLevel:
            chunk.setBlock(x, z, y, fluid);
    
    // Surface objects (flowers, grass, etc.)
    WPObject object = terrain.getSurfaceObject(...);
    if (object != null) place(object);
```

## Hytale Integration Notes

| WorldPainter | Hytale Equivalent |
|--------------|-------------------|
| `Terrain.GRASS` | `"Soil_Grass"` |
| `Terrain.STONE` | `"Rock_Stone"` |
| `Terrain.SAND` | `"Sand"` |
| `Terrain.WATER` | `FluidSection` with `"Water_Source"` |
| `MixedMaterial` | Could map to Hytale terrain gen noise |
| Custom terrain (96 slots) | Map to any Hytale block ID |

## See Also

- [02-DATA-MODEL.md](02-DATA-MODEL.md) — Tile storage details
- [04-LAYERS.md](04-LAYERS.md) — Layer system
- [../hytale/02-BLOCKS.md](../hytale/02-BLOCKS.md) — Hytale block system
