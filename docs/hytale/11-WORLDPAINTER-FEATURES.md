# WorldPainter Hytale Feature Proposals

This document outlines native Hytale features that WorldPainter could/should support beyond simple Minecraft→Hytale conversion.

## Currently Implemented

| Feature | Status | Notes |
|---------|--------|-------|
| Block export | ✅ | Minecraft blocks mapped to Hytale equivalents |
| Chunk format | ✅ | 32×32×320 with palette compression |
| Fluids | ✅ | Basic water/lava with levels |
| Lighting | ✅ | 4-channel RGBS octree |
| Environment | ✅ | Weekly weather forecast |
| World config | ✅ | config.json generation |

## Priority 1: Essential Features

### 1.1 Native Hytale Block Palette

**Current:** Hardcoded Minecraft→Hytale mapping  
**Proposed:** Load Hytale blocks from JSON asset files

```java
// Load from Server/Item/Items/*.json
HytaleBlockRegistry.loadFromAssets(hytaleAssetsPath);

// Support all native Hytale blocks
Dimension.setBlock(x, y, z, HytaleBlock.of("Soil_Grass_Lush_Zone1"));
```

**Benefits:**
- Support all 180+ Hytale block variants
- Proper texture metadata
- Material properties (hardness, sounds)

### 1.2 Zone Support

**Current:** All chunks go to "default" zone  
**Proposed:** Multiple zone support in export

```java
// Zone configuration
Zone zone = new Zone("Zone1_Forest");
zone.setBiome("Forest_Oak");
zone.setEnvironment("Forest");

// Zone-specific chunk storage
chunks/zone1/chunks.idb
chunks/zone2/chunks.idb
```

**Implementation:**
- Add Zone selector to export dialog
- Generate zone config.json
- Separate chunk storage per zone

### 1.3 Biome Export

**Current:** Biome data not exported  
**Proposed:** Support Hytale biome system

```java
// Per-chunk biome
chunk.setBiome("Forest_Birch");

// Or per-block (more granular)
chunk.setBiome(x, y, z, "Swamp_Mangrove");
```

**Implementation:**
- Map Minecraft biomes to Hytale biomes
- Store in chunk BSON
- Support custom biome assignment

## Priority 2: Enhanced Features

### 2.1 Prefab Integration

**Feature:** Place Hytale prefabs as custom objects

```java
// Load Hytale prefab
HytalePrefab dungeon = PrefabLoader.load("Prefabs/Dungeons/Cave.lpf");

// Place in world
world.placePrefab(dungeon, x, y, z, rotation);
```

**Requirements:**
- LPF v21 binary parser
- JSON v8 fallback parser
- Rotation/flip support
- ChildSpawner handling

### 2.2 Block Rotation Support

**Current:** All blocks exported with rotation 0  
**Proposed:** Support 0-63 rotation values

```java
// Block with rotation (X×Y×Z orientation)
HytaleBlock.of("Wood_Log_Oak", HytaleRotation.of(0, 3, 0));

// Stair orientations
HytaleBlock.of("Stone_Stairs", HytaleRotation.NORTH);
```

**Rotation Encoding:**
```
rotation = (rx * 16) + (ry * 4) + rz
where rx, ry, rz ∈ {0, 1, 2, 3}
```

### 2.3 Custom Fluid Types

**Current:** Only Water_Source, Lava_Source  
**Proposed:** Support custom fluids

```java
FluidSection.setFluid(x, y, z, "Acid", level);
FluidSection.setFluid(x, y, z, "Oil", level);
```

### 2.4 Environment Customization

**Current:** Fixed 1-week weather  
**Proposed:** User-configurable environment

```java
// Custom weather sequence
EnvironmentConfig.setWeather(DAY_0, "Clear");
EnvironmentConfig.setWeather(DAY_1, "Rain");
EnvironmentConfig.setWeather(DAY_2, "Thunderstorm");

// Time of day
EnvironmentConfig.setStartTime(0.25); // 6 AM
```

## Priority 3: Advanced Features

### 3.1 Entity Placement

**Feature:** Place Hytale entities in exported world

```java
// Spawn point marker
Entity spawn = new Entity("SpawnPoint");
spawn.setPosition(x, y, z);
world.addEntity(spawn);

// Creature spawner
Entity spawner = new Entity("CreatureSpawner");
spawner.setSpawnType("Creature_Kweebec");
spawner.setRadius(10);
world.addEntity(spawner);
```

**Entity Types to Support:**
- SpawnPoints
- Markers/Waypoints
- Item pickups
- NPCs (with data)

### 3.2 Native Terrain Generation

**Feature:** Use Hytale's terrain gen for exports

```java
// Apply Hytale biome terrain rules
TerrainGenerator gen = HytaleTerrainGenerator.forBiome("Forest_Oak");
gen.applyToChunk(chunk);

// Results in proper:
// - Surface blocks (Soil_Grass)
// - Rock layers
// - Cave systems
// - Ore distribution
```

### 3.3 Lighting Customization

**Feature:** Full RGBS lighting control

```java
// Custom colored light
chunk.setLight(x, y, z, 
    red: 15,    // R channel
    green: 8,   // G channel
    blue: 0,    // B channel
    sky: 15     // S (sky) channel
);
```

**Use Cases:**
- Magic glow effects
- Lava ambient lighting
- Underground atmospherics

### 3.4 ChildSpawner Support

**Feature:** Export structures with spawn points

```java
Prefab structure = new Prefab();
structure.addBlock(...);

// Add spawn points inside
structure.addChildSpawner("chest", 
    position: (3, 1, 5),
    type: "Container_Chest_Wooden"
);

structure.addChildSpawner("mob",
    position: (5, 0, 5),
    type: "Creature_Skeleton"
);
```

## Implementation Roadmap

### Phase 1 (Near-term)
1. ✅ Basic export working
2. ⬜ Load native Hytale block definitions
3. ⬜ Block rotation support
4. ⬜ Zone selector in export

### Phase 2 (Medium-term)
1. ⬜ Biome export
2. ⬜ Prefab placement tool
3. ⬜ Custom environment config
4. ⬜ Entity markers

### Phase 3 (Long-term)
1. ⬜ Native terrain generation
2. ⬜ Full RGBS lighting editor
3. ⬜ Two-way import/export
4. ⬜ Hytale world import

## Technical Requirements

### Asset Loading
```
HytaleAssets/
├── Server/
│   ├── Item/Items/          # Block definitions
│   ├── BlockTypeList/       # Block groups
│   ├── Environments/        # Environment configs
│   └── HytaleGenerator/     # Terrain gen data
```

### Configuration
```properties
# worldpainter.properties
hytale.assets.path=/path/to/HytaleAssets
hytale.default.zone=Zone1
hytale.block.rotation.enabled=true
```

## Open Questions

1. **Block ID Stability:** How stable are Hytale block IDs between versions?
2. **Prefab Compatibility:** Will LPF format be stable for tools?
3. **Server Validation:** What validation does Hytale server perform on chunks?
4. **Custom Content:** How will mod support affect WorldPainter exports?

## References

- [02-BLOCKS.md](02-BLOCKS.md) - Block system details
- [03-CHUNKS.md](03-CHUNKS.md) - Chunk format
- [04-TERRAIN.md](04-TERRAIN.md) - Terrain generation
- [08-PREFABS.md](08-PREFABS.md) - Prefab format
