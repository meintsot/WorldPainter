# Hytale Asset Registry System

## Asset Registry Architecture

```
AssetRegistryLoader.loadAssets()
        │
        ▼
AssetRegistry.register(AssetStore)
        │
        ▼
AssetStore.load() ──→ JSON files ──→ JsonAssetWithMap
        │
        ▼
Tag indexing + dependency resolution
        │
        ▼
Assets available via AssetRegistry.getAssetStore(id).get(key)
```

## Asset Identification

Assets use string identifiers:

```java
// Simple ID
"Rock_Stone"
"Soil_Grass"

// Namespaced (rare)
"Hytale:Hytale"
```

## Asset Store Types

| Category | Asset Types |
|----------|-------------|
| **Blocks** | BlockType, BlockGroup, BlockSet, BlockBoundingBoxes, Fluid |
| **Items** | Item, ItemCategory, ItemSoundSet, ItemToolSpec, ItemQuality |
| **Audio** | SoundEvent, SoundSet, AudioCategory, AmbienceFX |
| **World** | Environment, Weather, GameplayConfig, Zone, PortalType |
| **Entities** | EntityEffect, Projectile, DamageCause |
| **Visuals** | ModelAsset, ParticleSpawner, ParticleSystem, Trail |
| **Crafting** | CraftingRecipe, ResourceType |

## AssetStore Class

```java
public class AssetStore<K, T extends JsonAssetWithMap, M> {
    private final Map<K, T> assets = new ConcurrentHashMap<>();
    private final AssetBuilderCodec<T> codec;
    
    public T get(K key);
    public int getIndex(K key);      // Numeric ID
    public T getAsset(int index);    // By numeric ID
}
```

## JSON Format

### Basic Item (Block)

```json
{
  "TranslationProperties": { "Name": "server.items.Rock_Stone.name" },
  "ItemLevel": 9,
  "Icon": "Icons/ItemsGenerated/Rock_Stone.png",
  "Categories": ["Blocks.Rock"],
  "BlockType": {
    "Material": "Solid",
    "DrawType": "Cube",
    "Group": "Stone",
    "Textures": [{ "All": "BlockTextures/Rock/Stone.png" }]
  },
  "Tags": { "Type": ["Rock"] }
}
```

### Inherited Asset

```json
{
  "Parent": "Template_Weapon_Sword",
  "TranslationProperties": { "Name": "server.items.Weapon_Sword_Iron.name" },
  "Model": "Items/Weapons/Sword/Iron.blockymodel",
  "Quality": "Uncommon",
  "ItemLevel": 20
}
```

## Tag System

```json
{
  "Tags": {
    "Type": ["Soil", "Natural"],
    "Family": ["Sword", "Weapon"]
  }
}
```

## Codec System

### AssetBuilderCodec

```java
public class AssetBuilderCodec<T> {
    // Supports inheritance via Parent field
    // Type-safe serialization/deserialization
    // Versioning and migration
}
```

### BuilderCodec Example

```java
public static final BuilderCodec<BlockType> CODEC = BuilderCodec
    .builder(BlockType.class, BlockType::new)
    .versioned()
    .codecVersion(3)
    .append(new KeyedCodec<String>("Id", Codec.STRING), ...)
    .append(new KeyedCodec<String>("Material", Codec.STRING), ...)
    .build();
```

## File Locations

| Content | Path |
|---------|------|
| Items/Blocks | `Server/Item/Items/` |
| Block Lists | `Server/BlockTypeList/` |
| Environments | `Server/Environments/` |
| Weathers | `Server/Weathers/` |
| Prefabs | `Server/Prefabs/` |
| Biomes | `Server/HytaleGenerator/Biomes/` |

## Biome JSON Structure

### TileBiome

```json
{
  "Name": "Forest_Birch",
  "Weight": 9,
  "SizeModifier": 0.5,
  "MapColor": "#9ec752",
  "Covers": [...],
  "Prefabs": {...},
  "Layers": {...},
  "TerrainHeightThreshold": {...},
  "HeightmapNoise": {...},
  "Environment": {...},
  "Water": {
    "Entries": [{ "Fluid": "Water_Source", "Min": 0, "Max": 114 }]
  }
}
```

### Terrain Density Nodes

| Type | Description |
|------|-------------|
| `Constant` | Fixed value |
| `SimplexNoise2D` | 2D Perlin noise |
| `SimplexNoise3D` | 3D noise |
| `CurveMapper` | Curve transformation |
| `Sum`, `Multiplier` | Math ops |
| `Mix` | Blend inputs |
| `BaseHeight` | Height reference |
| `Normalizer` | Range normalization |

### Material Provider Types

| Type | Description |
|------|-------------|
| `Constant` | Single block |
| `Queue` | Fallback chain |
| `Solidity` | By solid/empty |
| `SimpleHorizontal` | By Y-range |
| `FieldFunction` | Noise-based |

## WorldPainter Integration

### Block ID Resolution

```java
// Map Hytale block name to numeric ID
int blockId = assetStore.getIndex("Rock_Stone");

// Map numeric ID back to name
String blockName = assetStore.getAsset(blockId).getId();
```

### Custom Block Support

For WorldPainter to support custom Hytale blocks:
1. Load block definitions from JSON
2. Build ID→name mapping
3. Use in palette serialization

### Asset Loading

```java
// Load block types from JSON directory
BlockTypeAssetMap blockTypes = new BlockTypeAssetMap();
for (File json : jsonFiles) {
    BlockType block = BlockType.CODEC.decode(json);
    blockTypes.register(block.getId(), block);
}
```
