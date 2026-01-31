# Hytale Block System

## Block Type Definition

BlockType is the central class defining all block properties. Each block is identified by a string ID (e.g., "Rock_Stone", "Soil_Grass").

### Core Properties

```java
public class BlockType {
    // Identity
    protected String id;                    // Block ID (e.g., "Rock_Stone")
    protected boolean unknown;              // Unknown/missing block flag
    protected String group;                 // Block group (e.g., "Air", "@Tech")
    protected String[] aliases;             // Alternative names for commands
    
    // Built-in IDs
    public static final int EMPTY_ID = 0;   // Air/"Empty"
    public static final int UNKNOWN_ID = 1;
    public static final int DEBUG_CUBE_ID = 2;
    public static final int DEBUG_MODEL_ID = 3;
}
```

### Rendering Properties

```java
// Draw type determines rendering method
protected DrawType drawType;  // Empty, GizmoCube, Cube, Model, CubeWithModel

// Material determines solidity
protected BlockMaterial material;  // Empty or Solid

// Opacity determines light transmission
protected Opacity opacity;  // Solid, Semitransparent, Cutout, Transparent
```

### Visual Properties

```java
// Textures (per face or all)
protected BlockTypeTextures[] textures;
protected CustomModelTexture[] customModelTexture;
protected String customModel;               // .blockymodel path
protected float customModelScale;
protected String customModelAnimation;

// Light emission
protected ColorLight light;  // { radius, red, green, blue }

// Tinting (per face, can be biome-dependent)
protected Color[] tintUp, tintDown, tintNorth, tintSouth, tintWest, tintEast;
protected int biomeTintUp, biomeTintDown, /* etc. */
```

### Rotation Properties

```java
// Random rotation for terrain variation
protected RandomRotation randomRotation;  // None, YawPitchRollStep1, YawStep1, etc.

// Variant rotation for block variants
protected VariantRotation variantRotation;

// Placement offset (rotation when placed)
protected Rotation rotationYawPlacementOffset;

// Rotation index: 0-63 (4 yaw × 4 pitch × 4 roll)
```

### Physics Properties

```java
// Hitbox configuration
protected String hitboxType = "Full";       // Hitbox asset ID
protected String interactionHitboxType;     // Separate interaction hitbox

// Movement settings
protected BlockMovementSettings movementSettings;
// - isClimbable, isBouncy, bounceVelocity
// - drag, friction
// - climbUpSpeedMultiplier, climbDownSpeedMultiplier
// - terminalVelocityModifier, jumpForceMultiplier

// Block support system
protected SupportDropType supportDropType;   // What happens when support lost
protected int maxSupportDistance;
protected Map<BlockFace, RequiredBlockFaceSupport[]> support;
protected Map<BlockFace, BlockFaceSupport[]> supporting;
```

### State & Interaction

```java
// State machine for multi-state blocks (doors, etc.)
protected StateData state;

// Block entity component
protected Holder<ChunkStore> blockEntity;

// Interaction handlers
protected BlockFlags flags;  // isUsable, isStackable
protected Map<InteractionType, String> interactions;

// Crafting/gathering
protected Bench bench;
protected BlockGathering gathering;
protected FarmingData farming;
```

## Enumerations

### DrawType
```java
public enum DrawType {
    Empty(0),         // No rendering (air)
    GizmoCube(1),     // Debug cube
    Cube(2),          // Standard cube
    Model(3),         // Custom 3D model
    CubeWithModel(4)  // Cube with model overlay
}
```

### BlockMaterial
```java
public enum BlockMaterial {
    Empty(0),    // Non-solid (air, water)
    Solid(1)     // Blocks movement
}
```

### Opacity
```java
public enum Opacity {
    Solid(0),          // Fully opaque, blocks light
    Semitransparent(1), // Partial transparency (glass)
    Cutout(2),         // Binary alpha (leaves)
    Transparent(3)     // Fully transparent (air)
}
```

### RandomRotation
```java
public enum RandomRotation {
    None(0),               // No random rotation
    YawPitchRollStep1(1),  // Random on all axes
    YawStep1(2),           // Random yaw only
    YawStep1XZ(3),         // Random yaw, mirrored X/Z
    YawStep90(4)           // Random 90° yaw increments
}
```

### Rotation (for block orientation)
```java
public enum Rotation {
    None(0),        // 0°
    Ninety(90),     // 90°
    OneEighty(180), // 180°
    TwoSeventy(270) // 270°
}
// Rotation index = yaw + pitch*4 + roll*16 (0-63)
```

### BlockFace
```java
public enum BlockFace {
    // Primary faces
    UP, DOWN, NORTH, EAST, SOUTH, WEST,
    
    // Edge faces (12)
    UP_NORTH, UP_SOUTH, UP_EAST, UP_WEST,
    DOWN_NORTH, DOWN_SOUTH, DOWN_EAST, DOWN_WEST,
    NORTH_EAST, SOUTH_EAST, SOUTH_WEST, NORTH_WEST,
    
    // Corner faces (8)
    UP_NORTH_EAST, UP_SOUTH_EAST, UP_SOUTH_WEST, UP_NORTH_WEST,
    DOWN_NORTH_EAST, DOWN_SOUTH_EAST, DOWN_SOUTH_WEST, DOWN_NORTH_WEST
}
```

## Block Hitboxes

### BlockBoundingBoxes
```java
public class BlockBoundingBoxes {
    protected Box[] baseDetailBoxes;        // Array of AABB boxes
    private transient RotatedVariantBoxes[] variants;  // Pre-computed for 64 rotations
    protected transient boolean protrudesUnitBox;      // Extends beyond 1×1×1
    
    public static final String DEFAULT = "Full";
    public static final BlockBoundingBoxes UNIT_BOX;   // Standard 1×1×1
}
```

### JSON Hitbox Definition
```json
{
  "Boxes": [
    {
      "Min": { "X": 0, "Y": 0, "Z": 0 },
      "Max": { "X": 1, "Y": 0.5, "Z": 1 }
    }
  ]
}
```

## Block JSON Definition

Blocks are typically defined as part of Item definitions:

```json
{
  "TranslationProperties": { "Name": "server.items.Rock_Stone.name" },
  "ItemLevel": 9,
  "Icon": "Icons/ItemsGenerated/Rock_Stone.png",
  "Categories": ["Blocks.Rock"],
  "PlayerAnimationsId": "Block",
  "BlockType": {
    "Material": "Solid",
    "DrawType": "Cube",
    "Opacity": "Solid",
    "Group": "Stone",
    "Textures": [
      {
        "All": "BlockTextures/Rock/Stone.png",
        "Weight": 1
      }
    ],
    "Light": {
      "Radius": 0,
      "Red": 0,
      "Green": 0,
      "Blue": 0
    },
    "HitboxType": "Full",
    "BlockSoundSetId": "Stone",
    "RandomRotation": "YawStep90"
  },
  "Tags": { "Type": ["Rock"] }
}
```

## Block Registry

### BlockTypeAssetMap
```java
public class BlockTypeAssetMap<K, T> extends AssetMapWithIndexes<K, T> {
    private Object2IntMap<K> keyToIndex;  // Name → numeric ID
    private T[] array;                    // ID → BlockType
    private Map<String, Integer> groupMap; // Group → group ID
    
    public int getIndex(K key);           // Get ID by name
    public T getAsset(int index);         // Get BlockType by ID
    public int getGroupId(String group);
}
```

## Known Block Categories

From decompiled source and assets:

### Soils
- `Soil_Dirt`, `Soil_Dirt_Burnt`, `Soil_Dirt_Cold`, `Soil_Dirt_Dry`, `Soil_Dirt_Poisoned`
- `Soil_Grass`, `Soil_Grass_Burnt`, `Soil_Grass_Cold`, `Soil_Grass_Deep`, `Soil_Grass_Dry`, `Soil_Grass_Full`, `Soil_Grass_Sunny`, `Soil_Grass_Wet`
- `Soil_Mud`, `Soil_Sand`, `Soil_Ice`, `Soil_Snow`

### Rock
- `Rock_Stone`, `Rock_Stone_Mossy`
- `Rock_Shale`, `Rock_Slate`, `Rock_Quartzite`
- `Rock_Sandstone`, `Rock_Sandstone_Red`, `Rock_Sandstone_White`
- `Rock_Basalt`, `Rock_Volcanic`, `Rock_Marble`
- `Rock_Calcite`, `Rock_Aqua`, `Rock_Chalk`
- `Rock_Bedrock`, `Rock_Salt`

### Wood
- `Wood_Oak_*`, `Wood_Birch_*`, `Wood_Pine_*`
- Variants: `_Trunk_Full`, `_Trunk_Half`, `_Branch_*`, `_Plank`

### Plants
- `Plant_Leaves_*` (various tree types)
- `Plant_Flower_*`, `Plant_Mushroom_*`
- `Plant_Grass_*`, `Plant_Fern_*`

### Technical
- `Empty` (Air, ID 0)
- `@Tech` group blocks (no physics)
- `Block_PrefabSpawner` (for nested prefab placement)

## Block State System

### StateData
```java
public class StateData {
    private String id;                           // State type ID
    private Map<String, String> stateToBlock;    // State name → block ID
    private Map<String, String> blockToState;    // Block ID → state name
    
    public String getBlockForState(String state);
    public String getStateForBlock(String blockTypeKey);
}
```

Example: Door states
```json
{
  "State": {
    "Id": "Door",
    "StateToBlock": {
      "Open": "Door_Oak_Open",
      "Closed": "Door_Oak_Closed"
    }
  }
}
```

## WorldPainter Integration Notes

### Block ID Mapping
WorldPainter needs to maintain a mapping between:
- Hytale string IDs (e.g., "Rock_Stone")
- Internal numeric IDs (assigned at runtime)

### Required Properties for Export
- Block string ID (required)
- Rotation index (0-63, default 0)
- Filler value (for multi-block structures, usually 0)
- Support value (for physics, usually 0)

### Block Palette Usage
When writing BlockSection:
1. Collect unique block IDs in section
2. Choose palette type based on count (≤1: Empty, ≤16: HalfByte, ≤256: Byte, else: Short)
3. Write palette header with ID→name mappings
4. Write block data array
