# Hytale Prefab System

## File Formats

### JSON Format (Version 8)

Extension: `.prefab.json`

```json
{
  "version": 8,
  "blockIdVersion": 10,
  "anchorX": 0, "anchorY": 0, "anchorZ": 0,
  "blocks": [
    {
      "x": 0, "y": 0, "z": 0,
      "name": "Block_Name",
      "rotation": 1,
      "support": 5,
      "filler": 0,
      "components": { "prefabspawner": {...} }
    }
  ],
  "fluids": [
    { "x": 0, "y": 1, "z": 0, "name": "Fluid_Water", "level": 8 }
  ],
  "entities": [...]
}
```

### Binary Format (Version 21)

Extension: `.lpf` (Lightweight Prefab File)

```
Header:
  [version: short (21)]
  [blockIdVersion: short]
  [anchor: packed long]

Block Name Table:
  [count: varint]
  [entries: (id: varint, name: string)[]]

Fluid Name Table:
  [count: varint]
  [entries: (id: varint, name: string)[]]

Columns:
  [count: int]
  per column:
    [columnIndex: int]
    [blockCount: varint]
    [blocks: PrefabBufferBlockEntry[]]
    [entityCount: varint]
    [entities: EntityStore[]]
```

## BlockSelection (In-Memory)

```java
public class BlockSelection {
    Long2ObjectMap<BlockHolder> blocks;  // Position → block
    Long2ObjectMap<FluidHolder> fluids;  // Position → fluid
    List<Holder<EntityStore>> entities;
    
    // BlockHolder
    record BlockHolder(
        int blockId,
        int rotation,       // 0-23
        int filler,
        int supportValue,
        Holder<ChunkStore> holder  // Block state
    ) {}
    
    // Methods
    void place(World, x, y, z, rotation);
    BlockSelection rotate(PrefabRotation rotation);
    BlockSelection flip(FlipAxis axis);
    BlockSelection relativize();
    BlockSelection cloneSelection();
}
```

## Prefab Rotation

```java
public enum PrefabRotation {
    ROTATION_0(0),    // 0°
    ROTATION_90(1),   // 90°
    ROTATION_180(2),  // 180°
    ROTATION_270(3);  // 270°
    
    int transformX(int x, int z);
    int transformZ(int x, int z);
}
```

## Block Rotation Index

```java
// 24 possible rotations (not 64 - only valid combinations)
// Rotation index stored per block (0-23)

// RotationTuple combines yaw, pitch, roll
int index = yaw + pitch * 4 + roll * 16;  // For 64 rotations
// But only 24 are valid block orientations
```

## Child Prefab Spawning

### PrefabSpawner Component

```json
{
  "x": 5, "y": 0, "z": 3,
  "name": "Block_PrefabSpawner",
  "components": {
    "prefabspawner": {
      "PrefabPath": "Trees.Oak.Stage_5.Oak_Stage5_001",
      "FitHeightmap": true,
      "InheritSeed": false,
      "PrefabWeights": {
        "Small_Rock_001": 10,
        "Small_Rock_002": 5
      }
    }
  }
}
```

### Child Prefab Config

```java
record ChildPrefab(
    int x, int y, int z,
    String path,                    // Dot-notation path
    boolean fitHeightmap,
    boolean inheritSeed,
    boolean inheritHeightCondition,
    PrefabWeights weights,
    PrefabRotation rotation
)
```

## PrefabStore (Singleton)

```java
public class PrefabStore {
    static final String PREFAB_PATH = "prefabs/";
    static final String ASSET_PREFAB_PATH = "Server/Prefabs/";
    static final String WORLD_GEN_PATH = "Prefabs/";
    
    BlockSelection getPrefab(String path);
    BlockSelection getAssetPrefab(String path);
    void savePrefab(String path, BlockSelection);
}
```

## World Generation Placement

### PrefabPatternGenerator

```java
public class PrefabPatternGenerator {
    long seedOffset;
    PrefabCategory category;
    GridGenerator gridGenerator;
    HeightCondition heightCondition;
    MapCondition mapCondition;
    BlockCondition parentCondition;
    PrefabRotation[] rotations;
    Vector3i displacement;
    boolean fitHeightmap;
    boolean onWater;
    boolean deepSearch;
    boolean submerge;
    Vector3i maxSize;
    int exclusionRadius;
}
```

### PrefabPasteUtil

```java
public class PrefabPasteUtil {
    static final int MAX_RECURSION_DEPTH = 10;
    
    void paste(
        IPrefabBuffer buffer,
        WorldGenerator generator,
        int x, int y, int z,
        PrefabRotation rotation,
        long seed,
        HeightCondition heightCondition,
        BlockPlacementMask mask,
        int environmentId,
        int recursionDepth
    );
}
```

## Block Placement Masks

```java
public class BlockPlacementMask {
    BlockMask mask;              // Block filter
    BlockMask.FilterType type;   // WHITELIST or BLACKLIST
    boolean inverted;
}

// Mask string: "Block_Stone,Block_Dirt" or "!Block_Air"
```

## Asset Prefab Examples

### Loot Chest

```json
{
  "version": 8,
  "blocks": [
    {
      "x": 0, "y": 0, "z": 0,
      "name": "Container_Chest",
      "components": {
        "container": {
          "Drops": "Loot_Dungeon_Crate__High"
        }
      }
    }
  ]
}
```

### Tree Structure

```json
{
  "version": 8,
  "anchorX": 0, "anchorY": 0, "anchorZ": 0,
  "blocks": [
    { "x": 0, "y": -5, "z": 0, "name": "Wood_Oak_Trunk_Full" },
    { "x": 0, "y": 0, "z": 0, "name": "Wood_Oak_Trunk_Full" },
    { "x": 1, "y": 5, "z": 0, "name": "Wood_Oak_Branch_Long", "rotation": 1 },
    { "x": 3, "y": 8, "z": 2, "name": "Plant_Leaves_Oak" }
  ]
}
```

## File Locations

| Category | Path |
|----------|------|
| Asset Prefabs | `HytaleAssets/Server/Prefabs/**/*.prefab.json` |
| User Prefabs | `prefabs/**/*.prefab.json` |
| WorldGen Prefabs | `Prefabs/**/*.prefab.json` |
| Binary Cache | `*.lpf` |

## WorldPainter Integration

### Prefab Export

WorldPainter could export prefab files:
1. Select blocks in editor
2. Calculate anchor point
3. Write JSON format with block positions and types
4. Include rotations and components if needed

### Prefab Import

1. Parse JSON/LPF file
2. Create custom brush from block positions
3. Allow placement with rotation

### BO3 to Prefab Conversion

Map Minecraft BO3 structures to Hytale prefabs:
- Block mapping (minecraft:stone → Rock_Stone)
- Rotation conversion
- Entity conversion (limited)
