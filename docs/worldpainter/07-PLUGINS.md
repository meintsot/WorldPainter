# WorldPainter Plugin System

## Overview

WorldPainter uses a plugin system for extending functionality. Plugins can add platforms, layers, custom objects, operations, and more.

## Plugin Architecture

### Plugin Manager

**Location:** [WPCore/.../plugins/WPPluginManager.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/plugins/WPPluginManager.java)

Central registry for all plugins.

```java
public class WPPluginManager {
    private static WPPluginManager instance;
    
    public static void initialise(UUID uuid, WPContext context);
    public static WPPluginManager getInstance();
    
    public <T extends Plugin> List<T> getPlugins(Class<T> type);
    public <T extends Plugin> T getPlugin(Class<T> type, String name);
}
```

### Plugin Interface

**Location:** [WPCore/.../plugins/Plugin.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/plugins/Plugin.java)

```java
public interface Plugin {
    String getName();
    String getVersion();
    void init(WPContext context);
}
```

### AbstractPlugin

**Location:** [WPCore/.../plugins/AbstractPlugin.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/plugins/AbstractPlugin.java)

```java
public abstract class AbstractPlugin implements Plugin {
    private final String name;
    private final String version;
    
    @Override
    public void init(WPContext context) { 
        // Default: no-op 
    }
}
```

## Plugin Discovery

### Descriptor Format

Plugins are discovered via descriptor files at:
- `META-INF/org.pepsoft.worldpainter.plugins` (in JARs)
- Direct resource path `org.pepsoft.worldpainter.plugins`

**Format (JSON):**

```json
{
  "name": "My Plugin",
  "version": "1.0.0",
  "classes": [
    "com.example.MyPlatformProvider",
    "com.example.MyLayerProvider",
    "com.example.MyOperationProvider"
  ]
}
```

### Loading Process

```java
// In Main.java
public static void main(String[] args) {
    // 1. Load external JAR plugins
    PluginManager.loadPlugins(pluginsDir, publicKey, DESCRIPTOR_PATH, version, true);
    
    // 2. Initialize WPPluginManager
    WPPluginManager.initialise(uuid, new WPContext() { ... });
    
    // 3. All plugin init() methods called
}
```

### Core Plugin Descriptors

**WPCore:**
```
org.pepsoft.worldpainter.DefaultPlugin
org.pepsoft.worldpainter.platforms.JavaPlatformProvider
org.pepsoft.worldpainter.platforms.HytalePlatformProvider
```

**WPGUI:**
```
org.pepsoft.worldpainter.GUIDefaultPlugin
```

## Extension Points

### Provider Interface

**Location:** [WPCore/.../plugins/Provider.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/plugins/Provider.java)

Base interface for keyed providers.

```java
public interface Provider<K> extends Plugin {
    List<K> getKeys();  // Keys this provider handles
}
```

### AbstractProviderManager

**Location:** [WPCore/.../plugins/AbstractProviderManager.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/plugins/AbstractProviderManager.java)

Generic manager for provider collections.

```java
public abstract class AbstractProviderManager<K, P extends Provider<K>> {
    private final Map<K, P> providersByKey = new HashMap<>();
    
    protected AbstractProviderManager(Class<P> providerClass) {
        for (P provider : WPPluginManager.getInstance().getPlugins(providerClass)) {
            for (K key : provider.getKeys()) {
                providersByKey.put(key, provider);
            }
        }
    }
    
    public P getProvider(K key);
    public List<P> getAllProviders();
}
```

## Provider Types

### PlatformProvider

**Location:** [WPCore/.../plugins/PlatformProvider.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/plugins/PlatformProvider.java)

Adds support for export platforms (Minecraft versions, Hytale).

```java
public interface PlatformProvider extends Provider<Platform> {
    WorldExporter getExporter(World2 world, WorldExportSettings settings);
    File getDefaultExportDir(Platform platform);
}
```

**Manager:** `PlatformManager`

### LayerProvider

**Location:** [WPCore/.../plugins/LayerProvider.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/plugins/LayerProvider.java)

Provides built-in layers.

```java
public interface LayerProvider extends Plugin {
    List<Layer> getLayers();
}
```

### CustomLayerProvider

**Location:** [WPCore/.../plugins/CustomLayerProvider.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/plugins/CustomLayerProvider.java)

Provides custom layer types for user creation.

```java
public interface CustomLayerProvider extends Plugin {
    List<Class<? extends CustomLayer>> getCustomLayers();
}
```

### CustomObjectProvider

**Location:** [WPCore/.../plugins/CustomObjectProvider.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/plugins/CustomObjectProvider.java)

Handles custom object formats (BO2, schematics, etc.).

```java
public interface CustomObjectProvider extends Provider<String> {
    List<String> getKeys();  // File extensions
    
    WPObject loadObject(File file) throws IOException;
    
    default List<WPObject> loadObjects(File file) throws IOException {
        return Collections.singletonList(loadObject(file));
    }
}
```

**Manager:** `CustomObjectManager`

### OperationProvider

**Location:** [WPCore/.../plugins/OperationProvider.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/plugins/OperationProvider.java)

Provides painting operations/tools.

```java
public interface OperationProvider extends Plugin {
    List<Operation> getOperations(WorldPainterView view);
}
```

### LayerEditorProvider

**Location:** [WPCore/.../plugins/LayerEditorProvider.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/plugins/LayerEditorProvider.java)

Provides UI editors for layer types.

```java
public interface LayerEditorProvider extends Provider<Class<? extends Layer>> {
    LayerEditor createLayerEditor(Platform platform, Class<? extends Layer> layerClass);
}
```

### MapImporterProvider

**Location:** [WPCore/.../plugins/MapImporterProvider.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/plugins/MapImporterProvider.java)

Handles world/map import.

```java
public interface MapImporterProvider extends Provider<Platform> {
    MapImporter getMapImporter(Platform platform);
}
```

## Creating a Plugin

### Step 1: Implement Plugin Classes

```java
public class MyPlugin extends AbstractPlugin implements PlatformProvider {
    
    public MyPlugin() {
        super("My Plugin", "1.0.0");
    }
    
    @Override
    public List<Platform> getKeys() {
        return Collections.singletonList(MY_PLATFORM);
    }
    
    @Override
    public WorldExporter getExporter(World2 world, WorldExportSettings settings) {
        return new MyWorldExporter(world, settings);
    }
}
```

### Step 2: Create Descriptor

Create `org.pepsoft.worldpainter.plugins` in resources:

```json
{
  "name": "My Plugin",
  "version": "1.0.0",
  "classes": [
    "com.example.myplugin.MyPlugin"
  ]
}
```

### Step 3: Package and Deploy

```
my-plugin.jar
├── META-INF/
│   └── org.pepsoft.worldpainter.plugins
└── com/
    └── example/
        └── myplugin/
            └── MyPlugin.class
```

Deploy to WorldPainter plugins directory.

## Plugin Context

**Location:** [WPCore/.../plugins/WPContext.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/plugins/WPContext.java)

Provides access to WorldPainter internals for plugins.

```java
public interface WPContext {
    EventLogger getEventLogger();
    ColourScheme getColourScheme();
    Object getContext(String key);
}
```

## DefaultPlugin

**Location:** [WPCore/.../DefaultPlugin.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/DefaultPlugin.java)

The core plugin defining all built-in functionality.

```java
public class DefaultPlugin extends AbstractPlugin 
    implements LayerProvider, CustomLayerProvider {
    
    // Defines all Platform constants
    public static final Platform JAVA_MCREGION = ...;
    public static final Platform JAVA_ANVIL = ...;
    public static final Platform HYTALE = ...;
    
    @Override
    public List<Layer> getLayers() {
        return Arrays.asList(
            Frost.INSTANCE,
            Caves.INSTANCE,
            Resources.INSTANCE,
            // ... all built-in layers
        );
    }
    
    @Override
    public List<Class<? extends CustomLayer>> getCustomLayers() {
        return Arrays.asList(
            GroundCoverLayer.class,
            PlantLayer.class,
            TunnelLayer.class,
            Bo2Layer.class,
            // ... all custom layer types
        );
    }
}
```

## See Also

- [06-PLATFORMS.md](06-PLATFORMS.md) — Platform provider details
- [04-LAYERS.md](04-LAYERS.md) — Layer system
