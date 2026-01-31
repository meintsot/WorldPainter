# Hytale Lighting System

## Overview

Hytale uses a 4-channel lighting system stored in an octree structure:
- **Red, Green, Blue**: Block light emission (colored lighting)
- **Sky**: Sky exposure (sunlight)

## Light Channels

```java
public static final int CHANNEL_COUNT = 4;
public static final int BITS_PER_CHANNEL = 4;  // 0-15 per channel
public static final int MAX_VALUE = 15;

public static final int RED_CHANNEL = 0;      // Bits 0-3
public static final int GREEN_CHANNEL = 1;    // Bits 4-7
public static final int BLUE_CHANNEL = 2;     // Bits 8-11
public static final int SKY_CHANNEL = 3;      // Bits 12-15
```

## Light Value Packing

```java
// Combine 4 channels into 16-bit value
short combineLightValues(byte red, byte green, byte blue, byte sky) {
    return (short)(sky << 12 | blue << 8 | green << 4 | red);
}

// Extract channel from packed value
byte getLightValue(short value, int channel) {
    return (byte)(value >> (channel * 4) & 0xF);
}

// Full skylight (outdoor, noon)
short FULL_SKYLIGHT = combineLightValues(0, 0, 0, 15);  // 0xF000
```

## ChunkLightData Structure

```java
public class ChunkLightData {
    public static final ChunkLightData EMPTY = new ChunkLightData(null, 0);
    
    // Octree constants
    public static final int TREE_SIZE = 8;      // Children per node
    public static final int SIZE_MAGIC = 17;    // Bytes per node
    public static final int DEPTH_MAGIC = 12;   // Max depth bits
    
    protected final short changeId;
    ByteBuf light;  // Octree-compressed data
}
```

## Octree Format

### Node Structure

```
// 17 bytes per node:
[mask: byte]         // Bit i = 1 means child i is subdivided
[values: short[8]]   // Child light values OR child node pointers

// Mask interpretation:
// Bit 0 = child at (0,0,0)
// Bit 1 = child at (1,0,0)
// Bit 2 = child at (0,1,0)
// ...
// Bit 7 = child at (1,1,1)
```

### Octree Traversal

```java
short getTraverse(ByteBuf local, int index, int pointer, int depth) {
    int position = pointer * 17;
    byte mask = local.getByte(position);
    
    // Extract 3 bits of index at current depth
    int innerIndex = (index >> (12 - depth)) & 7;
    int loc = innerIndex * 2 + position + 1;
    int result = local.getUnsignedShort(loc);
    
    if ((mask >> innerIndex & 1) == 1) {
        // Recurse into child node
        return getTraverse(local, index, result, depth + 3);
    }
    return (short)result;  // Leaf value
}
```

### Block Index to Octree Index

```java
// Block coordinates (0-31 each) → octree index
int octreeIndex(int x, int y, int z) {
    // Interleave bits: xxxx,yyyy,zzzz → xyzxyzxyzxyz
    int index = 0;
    for (int i = 0; i < 5; i++) {  // 5 bits each
        index |= ((x >> i) & 1) << (i * 3);
        index |= ((y >> i) & 1) << (i * 3 + 1);
        index |= ((z >> i) & 1) << (i * 3 + 2);
    }
    return index;
}
```

## Light Calculation

### Two-Phase Process

1. **Local Light**: Per-section calculation
   - Block emitters (torches, glowstone equivalent)
   - Direct sky exposure (above heightmap)

2. **Global Light**: Cross-section propagation
   - Light from neighboring sections
   - Side/edge/corner propagation

### Flood Fill Algorithm

```java
void propagateLight(BitSet queue, BlockSection section, ChunkLightDataBuilder light) {
    while ((blockIndex = queue.nextSetBit(0)) != -1) {
        queue.clear(blockIndex);
        
        // Get block opacity
        Opacity opacity = blockType.getOpacity();
        if (opacity == Opacity.Solid) continue;
        
        // Get current light values
        short lightValue = light.getLightRaw(blockIndex);
        byte red = getLightValue(lightValue, RED_CHANNEL);
        byte green = getLightValue(lightValue, GREEN_CHANNEL);
        byte blue = getLightValue(lightValue, BLUE_CHANNEL);
        byte sky = getLightValue(lightValue, SKY_CHANNEL);
        
        // Calculate propagated values (-1 attenuation)
        byte propRed = (byte)(red - 1);
        byte propGreen = (byte)(green - 1);
        byte propBlue = (byte)(blue - 1);
        byte propSky = (byte)(sky - 1);
        
        // Extra attenuation for semitransparent
        if (opacity == Opacity.Semitransparent || opacity == Opacity.Cutout) {
            propRed--; propGreen--; propBlue--; propSky--;
        }
        
        // Propagate to 6 neighbors
        for (Vector3i side : BLOCK_SIDES) {
            int nx = x + side.x, ny = y + side.y, nz = z + side.z;
            if (inBounds(nx, ny, nz)) {
                propagateToNeighbor(queue, propRed, propGreen, propBlue, propSky,
                                   section, light, indexBlock(nx, ny, nz));
            }
        }
    }
}
```

### Sky Light Calculation

```java
byte getSkyValue(WorldChunk chunk, int y, int height) {
    // Above heightmap = full sky
    return (byte)(y >= height ? 15 : 0);
}

// Sunlight factor (time of day)
byte sunlightValue = (byte)(skyLight * sunlightFactor);
```

## Block Light Emission

```java
// ColorLight definition
public class ColorLight {
    public byte radius;  // Reserved
    public byte red;     // 0-15
    public byte green;   // 0-15
    public byte blue;    // 0-15
}

// Blocks and fluids can emit light
ColorLight blockLight = blockType.getLight();
ColorLight fluidLight = fluid.getLight();

// Combine (take max per channel)
short combined = combineLightValues(
    Math.max(blockLight.red, fluidLight.red),
    Math.max(blockLight.green, fluidLight.green),
    Math.max(blockLight.blue, fluidLight.blue),
    skyValue
);
```

## Cross-Section Propagation

```java
// Attenuation by distance type
// Sides: -1
// Edges: -2
// Corners: -3

void propagateSide(BlockSection from, BlockSection to, ...) {
    for (int a = 0; a < 32; a++) {
        for (int b = 0; b < 32; b++) {
            byte propagated = (byte)(sourceLight - 1);
            if (fromOpacity == Opacity.Semitransparent) {
                propagated--;
            }
            propagateToNeighbor(...);
        }
    }
}
```

## WorldPainter Integration

### Full Skylight (Simplest)

For outdoor/surface terrain, set full skylight everywhere:

```java
void writeFullSkylight(ByteBuf buf) {
    // Single octree node with all children = 0xF000 (sky=15)
    buf.writeByte(0);  // No subdivisions
    for (int i = 0; i < 8; i++) {
        buf.writeShort(0xF000);  // Full skylight
    }
}
```

### Empty Light Data

```java
ChunkLightData.EMPTY = new ChunkLightData(null, 0);
// Serializes as minimal data
```

### Underground/Cave Light

For underground sections:
- Sky = 0 (no sky exposure)
- RGB from block emitters only

```java
short undergroundLight = combineLightValues(0, 0, 0, 0);  // Completely dark
```

### Serialization Format

```java
void serialize(ByteBuf buf) {
    if (light == null) {
        buf.writeShort(0);  // Size = 0
        buf.writeShort(changeId);
        return;
    }
    
    // Octree data
    buf.writeShort(light.readableBytes());
    buf.writeBytes(light);
    buf.writeShort(changeId);
}
```

## Key Constants

```java
// Light values
MAX_VALUE = 15              // Maximum light level
FULL_SKYLIGHT = 0xF000      // Sky channel = 15

// Octree
TREE_SIZE = 8               // Children per node
SIZE_MAGIC = 17             // Bytes per node
DEPTH_MAGIC = 12            // Max depth (covers 32³)

// Attenuation
BASE_ATTENUATION = 1        // Normal block
SEMITRANSPARENT_EXTRA = 1   // Extra for glass, etc.
```

## Common Issues

### Foggy/Dark World

- Cause: Missing or wrong skylight data
- Fix: Write full skylight (0xF000) for all outdoor blocks

### Light Data Size

- Empty octree: ~6 bytes
- Full octree: up to 17 × many nodes
- Most terrain: ~100-500 bytes compressed

### Change Counter

- `localChangeCounter` and `globalChangeCounter` track light updates
- For static export, use 0 or any consistent value
