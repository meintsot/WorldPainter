# Hytale Fluid System

## Overview

Hytale handles fluids separately from blocks:
- **BlockSection**: Solid blocks only (water/lava blocks map to air)
- **FluidSection**: Fluid type and level per block

## Fluid Types

```java
// Known fluid types from legacy conversion
"Water_Source"   // Full water block
"Water"          // Flowing water
"Water_Finite"   // Conservation-based water
"Lava_Source"    // Full lava block
"Lava"           // Flowing lava
"Tar_Source"     // Full tar block
"Tar"            // Flowing tar
"Slime_Source"   // Full slime
"Slime"          // Flowing slime
"Poison_Source"  // Full poison
"Poison"         // Flowing poison
```

## Fluid Asset Definition

```java
public class Fluid {
    protected String id;                    // e.g., "Water_Source"
    private int maxFluidLevel = 8;          // Max level (0-15, default 8)
    private BlockTypeTextures[] textures;   // Visual textures
    private ShaderType[] effect;            // Shader effects
    private Opacity opacity = Opacity.Solid; // Light blocking
    private boolean requiresAlphaBlending = true;
    private String fluidFXId = "Empty";     // Visual FX
    private FluidTicker ticker;             // Flow physics
    protected int damageToEntities;         // Damage (lava)
    protected ColorLight light;             // Light emission
    protected Color particleColor;          // Particle tinting
    protected String blockSoundSetId;       // Sound effects
}
```

## FluidSection Storage

```java
public class FluidSection implements Component<ChunkStore> {
    public static final int LEVEL_DATA_SIZE = 16384;  // 32768 / 2 (4 bits each)
    public static final int VERSION = 0;
    
    private ISectionPalette typePalette;    // Fluid type IDs (palette)
    @Nullable private byte[] levelData;     // Fluid levels (nibbles)
    private int nonZeroLevels = 0;          // Optimization counter
}
```

## Fluid Levels

```java
// Level range: 0-15 (4 bits)
public static final int FULL_LEVEL = 8;  // FluidState.FULL_LEVEL

// Level meanings:
// 0     = No fluid (clears fluid)
// 1-7   = Flowing fluid (decreasing depth)
// 8     = Full source block
// 9-15  = Reserved/unused
```

## Level Storage Format

```java
// Nibble-packed array: 2 blocks per byte
// levelData[blockIndex >> 1] contains 2 levels

byte getFluidLevel(int blockIndex) {
    int byteIndex = blockIndex >> 1;
    int shift = (blockIndex & 1) * 4;
    return (byte)((levelData[byteIndex] >> shift) & 0xF);
}

void setFluidLevel(int blockIndex, byte level) {
    int byteIndex = blockIndex >> 1;
    int shift = (blockIndex & 1) * 4;
    int mask = 0xF << shift;
    levelData[byteIndex] = (byte)((levelData[byteIndex] & ~mask) | ((level & 0xF) << shift));
}
```

## FluidSection Serialization

```
[palette_type: byte]           // 0=Empty, 1=HalfByte, 2=Byte, 3=Short
[palette_data: ...]            // Type palette (same format as blocks)
[has_level_data: boolean]
if has_level_data:
  [level_data: byte[16384]]    // 4 bits × 32768 blocks
```

## Fluid Physics (Runtime)

### DefaultFluidTicker (Infinite Flow)

```java
protected BlockTickStrategy spread(...) {
    // 1. Check block below
    if (!isSolid(blockBelow)) {
        // Flow down at max level
        setFluid(x, y-1, z, spreadFluidId, maxLevel);
    } else {
        // 2. Find shortest path to drop (up to 5 blocks)
        int offsets = getSpreadOffsets(..., MAX_DROP_DISTANCE=5);
        
        // 3. Spread horizontally with decreasing level
        int childLevel = fluidLevel - 1;
        for each orthogonal direction {
            if (canSpread && childLevel > 0) {
                setFluid(bx, by, bz, spreadFluidId, childLevel);
            }
        }
    }
}
```

### FiniteFluidTicker (Conservation)

```java
// Spreads diagonally and orthogonally
// Level is conserved when spreading
private boolean spreadDownwards(...) {
    int topY = getTopY(...);
    int transferLevel = min(topBlockLevel, maxLevel - bottomLevel);
    
    belowSection.setFluid(x, y-1, z, fluidId, newBottomLevel);
    topSection.setFluid(x, topY, z, fluidId, topBlockLevel - transferLevel);
}
```

### Fluid Collisions

```java
// Example: water + lava = stone
public class FluidCollisionConfig {
    String blockToPlace;    // Block created
    String soundEvent;      // Sound effect
    boolean placeFluid;     // Keep original fluid
}
```

## WorldPainter Integration

### Placing Water

```java
// 1. In BlockSection: place "Empty" (air) where water should be
blockPalette.set(index, EMPTY_ID);

// 2. In FluidSection: set water type and level
fluidTypePalette.set(index, getFluidId("Water_Source"));
setFluidLevel(index, 8);  // Full source level
```

### Clearing Water

```java
// Set level to 0 (clears fluid)
setFluidLevel(index, 0);
// OR set fluid type to 0
fluidTypePalette.set(index, 0);
```

### Serialization Example

```java
void createFluidSectionBson(int sectionY, Map<Vector3i, Material> fluids) {
    // Build fluid type palette
    ISectionPalette typePalette = new EmptySectionPalette();
    byte[] levelData = new byte[16384];
    
    for (var entry : fluids.entrySet()) {
        int blockIndex = indexBlock(x, y, z);
        
        // Set fluid type
        String fluidId = mapMaterialToFluid(entry.getValue());
        int typeId = getOrAddToPalette(typePalette, fluidId);
        typePalette = typePalette.set(blockIndex, typeId);
        
        // Set fluid level
        byte level = 8;  // Full source
        setFluidLevel(levelData, blockIndex, level);
    }
    
    // Serialize
    buf.writeByte(typePalette.getPaletteType().ordinal());
    typePalette.serialize(Fluid.KEY_SERIALIZER, buf);
    buf.writeBoolean(true);  // Has level data
    buf.writeBytes(levelData);
}
```

## Key Constants

```java
// FluidSection
LEVEL_DATA_SIZE = 16384    // Bytes for level storage
VERSION = 0                 // Section version

// FluidState
FULL_LEVEL = 8             // Max source level
MAX_LEVEL = 15             // Absolute max (4 bits)

// Flow physics
MAX_DROP_DISTANCE = 5      // Horizontal pathfinding
FLUID_BLOCK_DISTANCE = 5   // Max horizontal search
```

## Common Issues

### Water Not Rendering

1. **Wrong fluid level**: Use level 8 (FULL_LEVEL), not 15
2. **Zero fluid ID**: Level 0 or type 0 clears the fluid
3. **Palette encoding**: Use same format as block palettes

### Lava/Special Fluids

Same process as water, just different fluid type ID:
- `"Lava_Source"` for full lava
- `"Lava"` for flowing lava

### Fluid in Air vs Solid

Fluids should only exist where blocks are air/empty:
- BlockSection → "Empty" (ID 0)
- FluidSection → fluid type + level 8
