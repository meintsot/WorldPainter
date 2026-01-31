# Hytale Terrain Generation

## Generation Pipeline

```java
public void execute(int seed) {
    // Phase 1: Biome-based tint colors
    this.generateTintMapping(seed);
    
    // Phase 2: Environment IDs per column
    this.generateEnvironmentMapping(seed);
    
    // Phase 3: Terrain shape
    BlockPopulator.populate(seed, this);
    
    // Phase 4: Cave carving
    CavePopulator.populate(seed, this);
    
    // Phase 5: Structures and trees
    PrefabPopulator.populate(seed, this);
    
    // Phase 6: Water filling
    WaterPopulator.populate(seed, this);
}
```

## Zone System

### Zone vs Dimension
- Minecraft: Separate dimensions (Overworld, Nether, End)
- Hytale: Horizontal zones within same world

### Zone Definition

```java
public record Zone(
    int id,
    String name,
    ZoneDiscoveryConfig discoveryConfig,   // UI notification
    CaveGenerator caveGenerator,           // Zone caves
    BiomePatternGenerator biomePatternGenerator,
    UniquePrefabContainer uniquePrefabContainer
)
```

### Zone Color Mapping

Zones are mapped from mask images using RGB colors:

```json
{
  "MaskMapping": {
    "#ff0000": ["Zone1_Spawn"],
    "#78ff27": ["Zone1_Tier1"],
    "#5bbf1d": ["Zone1_Tier2"],
    "#ffb835": ["Zone2_Tier1"],
    "#008479": ["Oceans"],
    "#ffffff": ["Zone1_Tier1", "Zone2_Tier1", "Oceans"]
  }
}
```

### Zone Pattern Generation

```java
public ZoneGeneratorResult generate(int seed, double x, double z) {
    // 1. Get mask color at position
    int mask = maskProvider.get(seed, rx, rz) & 0xFFFFFF;
    
    // 2. Map color to zone(s)
    Zone[] zones = zoneColorMapping.get(mask);
    
    // 3. If multiple zones, use point generator for selection
    if (zones.length > 1) {
        getZone(seed, x, z, result, zones);
    }
    
    // 4. Calculate border distance
    result.borderDistance = calculateBorderDistance(x, z);
    
    return result;
}
```

## Biome System

### Biome Hierarchy

```
Biome (abstract)
├── TileBiome      - Primary zone biomes with weight
└── CustomBiome    - Overlay biomes (rivers, mountains)
```

### Biome Properties

```java
public abstract class Biome {
    protected final int id;
    protected final String name;
    protected final int mapColor;                // Map display color
    protected final BiomeInterpolation interpolation;  // Blend radius
    
    // Containers
    protected final IHeightThresholdInterpreter heightmapInterpreter;
    protected final CoverContainer coverContainer;     // Surface plants
    protected final LayerContainer layerContainer;     // Block layers
    protected final PrefabContainer prefabContainer;   // Structures
    protected final TintContainer tintContainer;       // Grass color
    protected final EnvironmentContainer environmentContainer;
    protected final WaterContainer waterContainer;     // Water level
    protected final FadeContainer fadeContainer;       // Border fading
    protected final NoiseProperty heightmapNoise;
}
```

### TileBiome (Main Biome Type)

```java
public class TileBiome extends Biome {
    protected final double weight;       // Spawn probability
    protected final double sizeModifier; // Size scaling
}
```

### CustomBiome (Overlay)

```java
public class CustomBiome extends Biome {
    protected final CustomBiomeGenerator generator;
    
    // Triggers when noise > threshold
    boolean shouldGenerateAt(seed, x, z, zoneResult);
}
```

## Height Generation

### Threshold-Based Terrain

Hytale uses a threshold comparison system:

```java
// For each Y level:
float threshold = biome.getThreshold(y);
float noise = biome.getHeightmapNoise(x, z);

if (threshold > noise || threshold == 1.0f) {
    placeBlock(x, y, z, fillingBlock);
}
```

### HeightThresholdInterpreter

```java
public class BasicHeightThresholdInterpreter {
    // Key positions and values
    private final int[] positions;    // Y levels
    private final float[] thresholds; // Threshold at each Y
    
    // Interpolated array for all Y
    private final float[] interpolatedThresholds;
    
    public float getThreshold(int y) {
        return interpolatedThresholds[y];
    }
}
```

### Biome Interpolation

When near biome borders, thresholds are blended:

```java
float generateInterpolatedThreshold(int y, InterpolatedBiomeCountList biomeCounts) {
    float threshold = 0;
    int total = 0;
    
    for (BiomeCountResult r : biomeCounts) {
        threshold += r.biome.getThreshold(y) * r.count;
        total += r.count;
    }
    
    return threshold / total;
}
```

## Noise System

### Noise Types

```java
public enum NoiseType {
    SIMPLEX,      // Primary noise
    OLD_SIMPLEX,
    VALUE,
    PERLIN,
    CELL,         // Voronoi/cellular
    DISTANCE,
    CONSTANT,
    GRID,
    MESH,
    BRANCH,
    POINT
}
```

### Noise Property Types

| Type | Description |
|------|-------------|
| `SingleNoiseProperty` | Basic noise with scale/offset |
| `FractalNoiseProperty` | Multi-octave fractal |
| `BlendNoiseProperty` | Blend between two noises |
| `CurveNoiseProperty` | Apply curve transformation |
| `DistortedNoiseProperty` | Domain warping |
| `SumNoiseProperty` | Add noises |
| `MultiplyNoiseProperty` | Multiply noises |
| `MinNoiseProperty` | Minimum of noises |
| `MaxNoiseProperty` | Maximum of noises |

### Fractal Modes

```java
public enum FractalMode {
    FBM,           // Fractional Brownian Motion
    BILLOW,        // Absolute value turbulence
    MULTI_RIGID,   // Rigid multi-fractal
    OLDSCHOOL,     // Classic octave stacking
    MIN,           // Minimum of octaves
    MAX            // Maximum of octaves
}
```

### FBM Implementation

```java
public double get(int seed, double x, double y) {
    double sum = noise.get(seed, x, y);
    double amp = 1.0;
    
    for (int i = 1; i < octaves; ++i) {
        x *= lacunarity;
        y *= lacunarity;
        amp *= persistence;
        sum += noise.get(seed + i, x, y) * amp;
    }
    
    return sum * 0.5 + 0.5;  // Normalize to 0-1
}
```

## Layer System

### LayerContainer

```java
public class LayerContainer {
    BlockFluidEntry filling;        // Default underground block
    StaticLayer[] staticLayers;     // Fixed depth layers
    DynamicLayer[] dynamicLayers;   // Noise-based layers
}
```

### StaticLayer

```java
public class StaticLayer {
    int depth;                      // Blocks from surface
    BlockFluidEntry block;          // Block to place
    IExposureCondition exposure;    // Surface/underground check
}
```

### DynamicLayer

```java
public class DynamicLayer {
    NoiseProperty depthNoise;       // Variable depth
    int minDepth, maxDepth;
    BlockFluidEntry block;
}
```

### Layer Examples

```json
{
  "Layers": {
    "Filling": "Rock_Stone",
    "StaticLayers": [
      { "Depth": 0, "Block": "Soil_Grass" },
      { "Depth": 3, "Block": "Soil_Dirt" }
    ],
    "DynamicLayers": [
      {
        "DepthNoise": { "Type": "SimplexNoise2D", "Scale": 50 },
        "MinDepth": 200,
        "Block": "Rock_Bedrock"
      }
    ]
  }
}
```

## Cover System

### CoverContainer

```java
public class CoverContainer {
    CoverEntry[] entries;
}

public class CoverEntry {
    BlockFluidEntry block;          // What to place
    IPointGenerator pointGenerator; // Where to place
    NoiseProperty densityNoise;     // Spawn probability
    BlockMask parentMask;           // Required block below
}
```

### Cover JSON

```json
{
  "Covers": [
    {
      "Block": "Plant_Grass_Tall",
      "PointGenerator": { "Type": "Grid", "Scale": 2 },
      "Density": { "Type": "SimplexNoise2D", "Scale": 100 },
      "ParentMask": ["Soil_Grass"]
    }
  ]
}
```

## Cave Generation

### Cave Generator

```java
public class CaveGenerator {
    CaveType[] caveTypes;
    
    public Cave generate(int seed, int x, int y, int z) {
        // Generate cave node tree
        Cave cave = new Cave(caveType);
        startCave(seed, cave, origin, random);
        cave.compile();
        return cave;
    }
}
```

### Cave Node Shapes

```java
public enum CaveNodeShapeEnum {
    PIPE,        // Cylindrical tube
    CYLINDER,    // Vertical cylinder
    PREFAB,      // Prefab-based room
    EMPTY_LINE,  // Connector without carving
    ELLIPSOID,   // Ellipsoidal cavern
    DISTORTED    // Warped shape
}
```

### Cave Type Configuration

```java
public class CaveType {
    String name;
    CaveNodeType entryNodeType;            // Starting node
    IFloatRange yaw, pitch, depth;         // Start orientation
    IHeightThresholdInterpreter heightFactors;  // Radius by Y
    IPointGenerator pointGenerator;        // Entry point distribution
    Int2FlagsCondition biomeMask;          // Biome restrictions
    BlockMaskCondition blockMask;          // Block replacement
    FluidLevel fluidLevel;                 // Underground water
    boolean submerge;                      // Fill with water
    double maximumSize;                    // Max extent
}
```

## Climate System

### Climate Noise

```java
public class ClimateNoise {
    Grid grid;                    // Cell-based grid
    NoiseProperty continent;      // Land/ocean
    NoiseProperty temperature;    // Temperature gradient
    NoiseProperty intensity;      // Weather intensity
    Thresholds thresholds;        // Land/island/beach cutoffs
}
```

### Climate Graph

512×512 lookup table mapping temperature/intensity to biomes:

```java
public class ClimateGraph {
    public static final int RESOLUTION = 512;
    
    int[] data;          // Climate type IDs
    int[] distanceData;  // Distance to boundary
}
```

## Block Placement Priority

| Priority | Phase |
|----------|-------|
| 1 | Base terrain filling |
| 2 | Layer blocks |
| 3 | Cover blocks |
| 4 | Water submerge |
| 7 | Cave carving |
| 9 | Surface prefabs |
| 39 | Submerged cave prefabs |
| 41 | Submerged surface prefabs |

## JSON Configuration Examples

### World.json

```json
{
  "Width": 1024,
  "Height": 1024,
  "OffsetX": 0,
  "OffsetY": 0,
  "Masks": "Mask.png",
  "PrefabStore": "DEFAULT"
}
```

### Zone WorldStructure

```json
{
  "Type": "NoiseRange",
  "Biomes": [
    { "Biome": "Plains1_Oak", "Min": -1, "Max": -0.82 },
    { "Biome": "Plains1_River", "Min": -0.5, "Max": 0 }
  ],
  "DefaultBiome": "Basic",
  "DefaultTransitionDistance": 32,
  "ContentFields": [
    { "Type": "BaseHeight", "Name": "Base", "Y": 100 }
  ]
}
```

### Biome Terrain (DAOTerrain)

```json
{
  "Terrain": {
    "Type": "DAOTerrain",
    "Density": {
      "Type": "Multiplier",
      "Inputs": [
        { "Type": "Constant", "Value": 2 },
        { "Type": "SimplexNoise2D", "Scale": 400, "Octaves": 3 }
      ]
    }
  }
}
```

## File Structure

```
Server/World/Default/
├── World.json              # Worldgen root config
├── Zones.json              # Zone color mappings
├── Mask.png                # Zone mask image
└── Zones/
    ├── Zone1_Tier1/
    │   ├── Zone.json       # Zone discovery config
    │   ├── Tile.*.json     # TileBiome definitions
    │   ├── Custom.*.json   # CustomBiome overlays
    │   ├── Cave/           # Cave configurations
    │   └── PrefabPatterns/ # Structure patterns
    └── ...
```

## WorldPainter Integration Notes

### Terrain Approach Options

1. **Direct Block Placement** - Bypass generation, write blocks directly
2. **Height-Based Generation** - Use WorldPainter heightmaps with Hytale layers
3. **Biome Mapping** - Map WorldPainter biomes to Hytale zones/biomes

### Recommended Approach

For v1, use direct block placement:
- Skip zone/biome system
- Place blocks directly per column
- Fill environment chunks with "Default"
- Add water using FluidSection

### Future Enhancement

Support native Hytale world editing:
- Zone mask painting
- Biome assignment per column
- Layer configuration
- Prefab placement rules
