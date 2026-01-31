# WorldPainter Serialization

## World File Format (.world)

WorldPainter saves worlds to `.world` files using Java serialization.

### WorldIO

**Location:** [WPCore/.../WorldIO.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/WorldIO.java)

```java
public class WorldIO {
    public static final String WORLD_EXTENSION = ".world";
    
    // Save world
    public static void save(World2 world, File file);
    
    // Load world
    public static World2 load(File file);
    
    // Read metadata only (fast)
    public static Map<String, Object> getMetadata(File file);
}
```

## File Structure

### Simple Format (Legacy)

```
[GZIPOutputStream]
├── [ObjectOutputStream]
│   ├── Map<String, Object> metadata
│   └── World2 object
```

### Compartmentalized Format (Modern)

For large worlds, dimensions are stored separately for faster region access.

```
[ZipOutputStream]
├── metadata.json        // World metadata
├── world-data.bin       // World2 (without dimensions)
├── surface.bin          // Surface dimension
├── nether.bin           // Nether dimension
├── end.bin              // End dimension
└── ...                  // Additional dimensions
```

## Metadata

### Metadata Keys

```java
public static final String METADATA_KEY_NAME = "name";
public static final String METADATA_KEY_WP_VERSION = "wpVersion";
public static final String METADATA_KEY_WP_BUILD = "wpBuild";
public static final String METADATA_KEY_TIMESTAMP = "timestamp";
public static final String METADATA_KEY_PLUGINS = "plugins";
public static final String METADATA_KEY_PLATFORM = "platform";
```

### Example Metadata

```json
{
  "name": "My World",
  "wpVersion": "2.21.0",
  "wpBuild": "20240115",
  "timestamp": 1705334400000,
  "platform": "org.pepsoft.anvil.1.18",
  "plugins": ["DefaultPlugin", "JavaPlatformProvider"]
}
```

## Save Process

```java
public static void save(World2 world, File file) {
    Map<String, Object> metadata = createMetadata(world);
    
    try (OutputStream out = new FileOutputStream(file);
         GZIPOutputStream gzOut = new GZIPOutputStream(out);
         ObjectOutputStream objOut = new ObjectOutputStream(gzOut)) {
        
        objOut.writeObject(metadata);
        objOut.writeObject(world);
    }
}
```

## Load Process

```java
public static World2 load(File file) {
    try (InputStream in = new FileInputStream(file);
         GZIPInputStream gzIn = new GZIPInputStream(in);
         ObjectInputStream objIn = new ObjectInputStream(gzIn)) {
        
        Map<String, Object> metadata = (Map<String, Object>) objIn.readObject();
        World2 world = (World2) objIn.readObject();
        
        // Migration if needed
        return migrate(world, metadata);
    }
}
```

## Version Migration

### WorldIO.migrate()

Handles conversion from older file formats.

```java
private static World2 migrate(Object object, Map<String, Object> metadata) {
    if (object instanceof World2) {
        return (World2) object;
    } else if (object instanceof World) {
        // Convert legacy World to World2
        return WorldConverter.convert((World) object);
    }
    throw new IllegalArgumentException("Unknown world format");
}
```

### Migration Steps

1. **Pre-World2:** Convert `World` class to `World2`
2. **Anchor System:** Update dimension IDs to Anchor-based
3. **Platform Update:** Map old platform IDs to current
4. **Layer Migration:** Update layer serialization

## Serialization Helpers

### InstanceKeeper

**Location:** [WPCore/.../util/InstanceKeeper.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/util/InstanceKeeper.java)

Ensures singleton instances after deserialization.

```java
public abstract class InstanceKeeper implements Serializable {
    private static final Map<Class<?>, Object> instances = new WeakHashMap<>();
    
    protected Object readResolve() {
        // Return existing instance if available
        return instances.computeIfAbsent(getClass(), k -> this);
    }
}
```

### Serializable Fields

Classes implement `Serializable` with explicit fields:

```java
public class Tile implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final int x, y;
    private short[] heightMap;
    private byte[] terrain;
    // ...
    
    // Custom serialization
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        // Custom field handling
    }
    
    private void readObject(ObjectInputStream in) 
            throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        // Reconstruct transient fields
    }
}
```

## Export File Formats

### Minecraft Java Edition

| Version | Format | Files |
|---------|--------|-------|
| Beta-1.1 | MCRegion | `r.X.Z.mcr` |
| 1.2+ | Anvil | `r.X.Z.mca` |

**Structure:**
```
world/
├── level.dat           // NBT world settings
├── region/
│   └── r.X.Z.mca       // Chunk data
├── DIM-1/              // Nether
│   └── region/
└── DIM1/               // End
    └── region/
```

### Hytale

**Structure:**
```
world/
├── config.json         // World settings
└── chunks/
    └── chunks.idb      // IndexedStorageFile
```

**Chunk Storage:**
- BSON serialization
- Zstd compression (level 3)
- IndexedStorageFile with key `(x, z)`

## Autosave

```java
// In App.java
private void startAutosave() {
    Timer timer = new Timer(autosaveInterval * 60000, e -> {
        if (world != null && world.isDirty()) {
            File autosaveFile = getAutosaveFile();
            WorldIO.save(world, autosaveFile);
        }
    });
    timer.start();
}
```

## Backup

Before overwriting:

```java
public static void saveWithBackup(World2 world, File file) {
    if (file.exists()) {
        File backup = new File(file.getPath() + ".bak");
        Files.move(file.toPath(), backup.toPath(), REPLACE_EXISTING);
    }
    save(world, file);
}
```

## File Association

WorldPainter registers `.world` file extension:

```
HKEY_CLASSES_ROOT\.world
  (Default) = "WorldPainter.World"
  
HKEY_CLASSES_ROOT\WorldPainter.World
  (Default) = "WorldPainter World"
  shell\open\command = "...worldpainter.exe" "%1"
```

## See Also

- [01-ARCHITECTURE.md](01-ARCHITECTURE.md) — World2, Dimension, Tile classes
- [05-EXPORT.md](05-EXPORT.md) — Export file formats
