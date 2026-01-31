# Hytale Chunk Storage System

## Chunk Dimensions

```java
// From ChunkUtil.java
BITS = 5                     // Shift bits for chunk coordinates
SIZE = 32                    // Blocks per axis per section
SIZE_2 = 1024                // 32×32 columns per chunk
SIZE_BLOCKS = 32768          // 32×32×32 blocks per section
HEIGHT_SECTIONS = 10         // Vertical sections
HEIGHT = 320                 // Total world height (10 × 32)
MIN_Y = 0                    // Minimum Y coordinate
SIZE_BLOCKS_COLUMN = 327680  // 32×32×320 blocks per column
```

## Index Calculations

```java
// Column index within chunk (XZ plane)
indexColumn(x, z) = (z & 0x1F) << 5 | (x & 0x1F)

// Block index within section (XYZ)
indexBlock(x, y, z) = (y & 0x1F) << 10 | (z & 0x1F) << 5 | (x & 0x1F)

// Section index from Y coordinate
indexSection(y) = y >> 5

// Chunk index (64-bit: high 32 = X, low 32 = Z)
indexChunk(x, z) = (long)x << 32 | (long)z & 0xFFFFFFFFL
```

## Chunk Hierarchy

```
WorldChunk
├── BlockChunk
│   ├── ShortBytePalette (heightmap, 1024 shorts)
│   ├── IntBytePalette (tintmap, 1024 ints)
│   ├── BlockSection[10]
│   │   ├── ISectionPalette (block IDs)
│   │   ├── ISectionPalette (filler data)
│   │   ├── ISectionPalette (rotation indices)
│   │   ├── ChunkLightData (local light)
│   │   ├── ChunkLightData (global light)
│   │   └── BitSet (ticking blocks)
│   └── EnvironmentChunk
│       └── EnvironmentColumn[1024]
├── FluidSection[10]
│   ├── ISectionPalette (fluid types)
│   └── byte[16384] (fluid levels)
├── BlockComponentChunk (block entities)
└── EntityChunk (world entities)
```

## BlockChunk

```java
public class BlockChunk implements Component<ChunkStore> {
    public static final int VERSION = 3;
    
    private long index;                     // Packed chunk coordinates
    private int x, z;                       // Chunk coordinates
    
    private final ShortBytePalette height;  // Heightmap (1024 shorts)
    private final IntBytePalette tint;      // Tintmap (1024 ints)
    private BlockSection[] chunkSections;   // 10 sections
    private EnvironmentChunk environments;  // Environment data
}
```

## BlockSection

```java
public class BlockSection implements Component<ChunkStore> {
    public static final int VERSION = 6;
    
    // Block data palettes
    private ISectionPalette chunkSection;    // Block IDs
    private ISectionPalette fillerSection;   // Filler block data
    private ISectionPalette rotationSection; // Block rotations (0-63)
    
    // Lighting
    private ChunkLightData localLight;       // Emitter + direct sky light
    private short localChangeCounter;
    private ChunkLightData globalLight;      // Propagated light
    private short globalChangeCounter;
    
    // Block ticking
    private BitSet tickingBlocks;
    private int tickingBlocksCount;
}
```

## Palette System

### Palette Types

| Type | Ordinal | Max Values | Storage |
|------|---------|------------|---------|
| Empty | 0 | 1 | 0 bytes (single value) |
| HalfByte | 1 | 16 | 4 bits/block = 16KB |
| Byte | 2 | 256 | 8 bits/block = 32KB |
| Short | 3 | 65536 | 16 bits/block = 64KB |

### Palette Interface

```java
public interface ISectionPalette {
    SetResult set(int index, int value);
    int get(int index);
    boolean contains(int id);
    int count();                    // Unique values
    ISectionPalette promote();      // Upgrade capacity
    ISectionPalette demote();       // Downgrade if possible
    PaletteType getPaletteType();
    void serialize(KeySerializer serializer, ByteBuf buf);
}
```

### Promotion/Demotion Rules

```java
// HalfByteSectionPalette
shouldDemote() = isSolid(0);      // All air → Empty
promote() = new ByteSectionPalette();

// ByteSectionPalette
DEMOTE_SIZE = 14;
shouldDemote() = count() <= 14;   // → HalfByte
promote() = new ShortSectionPalette();

// ShortSectionPalette
DEMOTE_SIZE = 251;
shouldDemote() = count() <= 251;  // → Byte
// Cannot promote further
```

### Palette Serialization Format

```
// Disk format (uses string block IDs):
[entry_count: short]
for each entry:
  [internal_id: byte]
  [external_key: UTF string]  // e.g., "Rock_Stone"
  [count: short]
[block_data: byte[]]

// Network format (uses numeric IDs):
[entry_count: short (LE)]
for each entry:
  [internal_id: byte]
  [external_id: int (LE)]
  [count: short (LE)]
[block_data: byte[]]
```

## Heightmap and Tintmap

### ShortBytePalette (Heightmap)
```java
// 1024 entries (32×32 columns)
// Each entry: max Y with solid block
// Storage: BitFieldArr format (10 bits per entry)

// Serialization:
buf.writeByte(paletteType.ordinal());
// Palette entries...
// BitFieldArr: 10 bits × 1024 = 1280 bytes
```

### IntBytePalette (Tintmap)
```java
// 1024 entries (32×32 columns)
// Each entry: packed RGBA tint color
// Storage: similar to heightmap
```

## FluidSection

```java
public class FluidSection implements Component<ChunkStore> {
    public static final int LEVEL_DATA_SIZE = 16384;  // 32768 / 2
    public static final int VERSION = 0;
    
    private ISectionPalette typePalette;   // Fluid type IDs
    @Nullable private byte[] levelData;    // 4 bits per block
    private int nonZeroLevels = 0;
}

// Fluid levels: 0-15 (4 bits)
// Level 0 = no fluid
// Level 8 = full source block (FluidState.FULL_LEVEL)
// Levels 1-7 = flowing (decreasing depth)
```

### FluidSection Serialization

```
[palette_type: byte]
[palette_data: ...]
[has_levels: boolean]
if has_levels:
  [level_data: byte[16384]]  // 4 bits × 32768 blocks
```

## EnvironmentChunk

```java
public class EnvironmentChunk implements Component<ChunkStore> {
    private final EnvironmentColumn[] columns = new EnvironmentColumn[1024];
    private final Int2LongMap counts;  // Env ID → block count
}
```

### EnvironmentColumn (Run-Length Encoded)

```java
public class EnvironmentColumn {
    private final IntArrayList maxYs;   // Range end Y values
    private final IntArrayList values;  // Environment IDs
    
    // Example: maxYs=[63, 127], values=[1, 2, 3]
    // y ∈ (-∞, 63] = env 1
    // y ∈ [64, 127] = env 2
    // y ∈ [128, +∞) = env 3
}
```

### EnvironmentChunk Serialization

```
[env_mapping_count: int]
for each mapping:
  [env_id: int]
  [env_key: UTF string]  // e.g., "Default"
for each column (1024):
  [column serialization...]
```

## ChunkLightData (Octree)

```java
public class ChunkLightData {
    public static final ChunkLightData EMPTY = new ChunkLightData(null, 0);
    
    // Octree constants
    public static final int TREE_SIZE = 8;     // Children per node
    public static final int SIZE_MAGIC = 17;   // Bytes per node
    
    // Light channels (4 bits each = 16 bits total)
    public static final int RED_CHANNEL = 0;    // Bits 0-3
    public static final int GREEN_CHANNEL = 1;  // Bits 4-7
    public static final int BLUE_CHANNEL = 2;   // Bits 8-11
    public static final int SKY_CHANNEL = 3;    // Bits 12-15
    
    protected final short changeId;
    ByteBuf light;  // Octree-compressed data
}
```

### Octree Node Format

```
// 17 bytes per node:
[mask: byte]              // Bit i = 1 means child i is pointer
[values: short[8]]        // Either light value or child pointer

// Light value (16 bits):
bits 0-3:   red (0-15)
bits 4-7:   green (0-15)
bits 8-11:  blue (0-15)
bits 12-15: sky (0-15)
```

## File Storage (IndexedStorageFile)

### Region File Format

```
// Header (28 bytes):
[magic: "HytaleIndexedStorage" (20 bytes)]
[version: int (4 bytes)]              // = 1
[blob_count: int (4 bytes)]           // = 1024 (32×32 chunks)
[segment_size: int (4 bytes)]         // = 4096

// Blob Index Table (4096 bytes):
[segment_index: int][1024]  // 0 = unassigned

// Data Segments:
[segment data...]
```

### Blob Header

```
// Each chunk blob starts with:
[uncompressed_size: int (4 bytes)]
[compressed_size: int (4 bytes)]
[compressed_data: byte[compressed_size]]
```

### Compression

```java
// Zstd compression, level 3 (default)
compressionLevel = 3;

// Compress
ByteBuffer compressed = Zstd.compress(data, compressionLevel);

// Decompress
ByteBuffer decompressed = Zstd.decompress(compressed, uncompressedSize);
```

### File Naming

```
// Pattern: {regionX}.{regionZ}.region.bin
// Examples: 0.0.region.bin, -1.2.region.bin

// Region coordinates:
regionX = chunkX >> 5;  // 32 chunks per region
regionZ = chunkZ >> 5;

// Local index within region:
localX = chunkX & 0x1F;
localZ = chunkZ & 0x1F;
blobIndex = indexColumn(localX, localZ);  // 0-1023
```

## BSON Serialization

### Chunk Components

| Component | BSON Key | Data |
|-----------|----------|------|
| BlockChunk | `"Data"` | Serialized binary |
| BlockSection | `"Data"` | Serialized binary |
| FluidSection | `"Data"` | Serialized binary |
| EnvironmentChunk | `"Data"` | Serialized binary |
| ChunkColumn | `"Sections"` | Section references |
| BlockComponentChunk | `"BlockComponents"` | Block entities |
| EntityChunk | `"Entities"` | Entity holders |

### BlockSection Serialization Order

```
1. [block_migration_version: int]
2. Block palette:
   [palette_type: byte]
   [palette_data: ...]
3. Ticking blocks (if not Empty):
   [count: short]
   [long_array_length: short]
   [long[]]
4. Filler palette:
   [palette_type: byte]
   [palette_data: ...]  // uses writeShort for values
5. Rotation palette:
   [palette_type: byte]
   [palette_data: ...]  // uses writeByte for values
6. Local light:
   [octree_data: ...]
7. Global light:
   [octree_data: ...]
8. [local_change_counter: short]
9. [global_change_counter: short]
```

## WorldPainter Integration Notes

### Chunk Writing Checklist

1. **Calculate section range** - Which Y sections have blocks
2. **Per section:**
   - Build block palette (collect unique IDs)
   - Choose palette type (Empty/HalfByte/Byte/Short)
   - Serialize blocks with little-endian encoding
   - Build rotation palette (usually Empty if no rotations)
   - Build filler palette (usually Empty)
   - Generate light data (full skylight for surface)
3. **Build heightmap** - Max solid Y per column
4. **Build tintmap** - Biome colors per column
5. **Build environment chunks** - Environment IDs per column
6. **Build fluid sections** - Water/lava placement
7. **Wrap in BSON document**
8. **Compress with Zstd**
9. **Write to region file**

### Key Gotchas

1. **Little-endian encoding** for ShortBytePalette network format
2. **BitFieldArr format** for heightmap/tintmap (10 bits × 1024)
3. **Palette count** includes empty placeholder at index 0
4. **Fluid level 0** clears the fluid, level 8 is full source
5. **Environment ID** must match asset registry ("Default" not "default")
