# Hytale Entity System

## Entity Hierarchy

```
Entity (abstract)
├── LivingEntity
│   ├── NPCEntity      - AI-controlled mobs/NPCs
│   └── Player         - Human players
├── BlockEntity        - Dropped blocks
├── SpawnBeacon        - Trigger-based spawner
└── ProjectileComponent - Arrows, spells
```

## Entity Component System (ECS)

Hytale uses an archetype-based ECS:

### Core Classes

```java
// Store - Central entity management
public class Store<T> {
    Ref<T> addEntity(Holder<T> holder, AddReason reason);
    void removeEntity(Ref<T> ref, RemoveReason reason);
    <C> C getComponent(Ref<T> ref, ComponentType<T, C> type);
    <C> void putComponent(Ref<T> ref, ComponentType<T, C> type, C component);
}

// Holder - Pre-spawn entity container
Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
holder.addComponent(componentType, component);

// Ref - Live entity reference
Ref<EntityStore> ref = store.addEntity(holder, reason);
```

### Common Components

| Component | Purpose |
|-----------|---------|
| TransformComponent | Position + Rotation |
| ModelComponent | Visual model |
| DisplayNameComponent | Entity name |
| BoundingBox | Collision |
| HeadRotation | Look direction |
| UUIDComponent | Unique ID |
| VelocityComponent | Movement |
| NetworkId | Network sync |

## NPCEntity

```java
public class NPCEntity extends LivingEntity {
    private Role role;               // AI behavior
    private int roleIndex;
    private String roleName;
    private long spawnInstant;
    private float initialModelScale;
    private Vector3d leashPosition;  // Tether point
    private Blackboard.Views blackboardViews;
    private TransientPathManager pathManager;
}
```

## Role System (AI)

```java
public class Role {
    // Support systems
    CombatSupport combatSupport;
    StateSupport stateSupport;
    MarkedEntitySupport markedEntitySupport;  // Targets
    WorldSupport worldSupport;
    
    // Motion
    Map<String, MotionController> motionControllers;
    Steering bodySteering, headSteering;
    
    // Behavior tree
    Instruction[] indexedInstructions;
    
    // Main tick
    void tick(Ref<EntityStore> ref, NPCEntity npc, float dt);
}
```

### Instruction (Behavior Tree Node)

```java
public class Instruction {
    Sensor sensor;           // Condition
    Action trueAction;       // If true
    Action falseAction;      // If false
    BodyMotion bodyMotion;   // Movement
    HeadMotion headMotion;   // Looking
}
```

### Sensor Types

- `BuilderSensorPlayer` - Detect players
- `BuilderSensorEntity` - Detect mobs
- `BuilderSensorDamage` - Damage events
- `BuilderSensorState` - State checks
- `BuilderSensorTimer` - Timers

### Action Types

- `BuilderActionAttack` - Combat
- `BuilderActionState` - State change
- `BuilderActionSpawn` - Spawn entities
- `BuilderActionPlayAnimation` - Animations
- `BuilderActionDespawn` - Removal

### BodyMotion Types

- `BuilderBodyMotionWander` - Random movement
- `BuilderBodyMotionFind` - Seek target
- `BuilderBodyMotionMoveAway` - Flee
- `BuilderBodyMotionPath` - Follow path

## Spawning Systems

### SpawnMarker (Prefab Placement)

```java
public class SpawnMarkerEntity implements Component<EntityStore> {
    String spawnMarkerId;
    SpawnMarker cachedMarker;
    double respawnCounter;
    int spawnCount;
    StoredFlock storedFlock;
    
    boolean spawnNPC(Ref<EntityStore> ref, SpawnMarker marker, Store<EntityStore> store);
}
```

### SpawnBeacon (Dynamic Triggers)

```java
public class SpawnBeacon extends Entity {
    BeaconSpawnWrapper spawnWrapper;
    IntSet unspawnableRoles;
    
    boolean manualTrigger(Ref<EntityStore> ref, ...);
}
```

### Direct API

```java
NPCPlugin.get().spawnNPC(
    store,
    "npcType",
    "groupType",
    position,
    rotation
);
```

## Entity Serialization

### BuilderCodec Pattern

```java
public static final BuilderCodec<NPCEntity> CODEC = BuilderCodec
    .builder(NPCEntity.class, NPCEntity::new, LivingEntity.CODEC)
    .append(new KeyedCodec<String>("Env", Codec.STRING), 
        (o, v) -> o.env = v, o -> o.env)
    .append(new KeyedCodec<Float>("HoverPhase", Codec.FLOAT), ...)
    .append(new KeyedCodec<String>("SpawnName", Codec.STRING), ...)
    .append(new KeyedCodec<Vector3d>("LeashPos", Vector3d.CODEC), ...)
    .build();
```

## Entity Storage

### EntityStore (Runtime)

```java
public class EntityStore {
    Store<EntityStore> store;
    Map<UUID, Ref<EntityStore>> entitiesByUuid;
    Int2ObjectMap<Ref<EntityStore>> networkIdToRef;
}
```

### Entity Chunk (Persistence)

```java
public class EntityChunk implements Component<ChunkStore> {
    List<Holder<EntityStore>> entityHolders;    // Serialized
    Set<Ref<EntityStore>> entityReferences;     // Active
}
```

## Spatial Indexing

KDTree for efficient entity queries:

```java
ResourceType<EntityStore, SpatialResource<Ref<EntityStore>, EntityStore>> npcSpatialResource;

SpatialResource spatial = store.getResource(npcSpatialResource);
spatial.getSpatialStructure().collect(position, radius, results);
```

## WorldPainter Integration

### Entity Placement

For basic entity placement, WorldPainter could:
1. Define spawn markers in exported worlds
2. Use prefabs with entity components
3. Export EntityChunk with serialized entity holders

### Limitations

Full entity AI/behavior requires runtime systems not available in static export. Focus on:
- Spawn markers (where entities can spawn)
- Basic entity placement (position, type)
- Leave AI to game runtime
