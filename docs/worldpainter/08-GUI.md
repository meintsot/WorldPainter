# WorldPainter GUI Architecture

## Application Structure

### Entry Point

**Location:** [WPGUI/.../Main.java](../../WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/Main.java)

```java
public final class Main {
    public static void main(String[] args) {
        // 1. Configure logging
        configureLogging();
        
        // 2. Load plugins
        PluginManager.loadPlugins(pluginsDir, publicKey, DESCRIPTOR_PATH, version, true);
        WPPluginManager.initialise(uuid, context);
        
        // 3. Load configuration
        Configuration config = Configuration.load();
        
        // 4. Create main window on EDT
        SwingUtilities.invokeLater(() -> {
            App app = App.getInstance();
            app.setVisible(true);
        });
    }
}
```

### Main Application Frame

**Location:** [WPGUI/.../App.java](../../WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/App.java)

The main window (~7000 lines). Extends `JFrame`.

```java
public class App extends JFrame implements BrushControl, DockableHolder {
    private static App instance;
    
    // Core state
    private World2 world;
    private Dimension dimension;
    private final UndoManager undoManager;
    
    // UI components
    private WorldPainter worldPainter;        // Main 2D view
    private DockingManager dockingManager;    // JIDE docking
    private JToolBar toolBar;
    private JMenuBar menuBar;
    
    // Current tool state
    private Operation activeOperation;
    private Brush brush;
    private float brushLevel = 1.0f;
    private int brushRadius = 50;
}
```

### Docking Framework

WorldPainter uses **JIDE Docking Framework** for panel management.

**Panels:**
- Tools Panel — Operations/tools
- Tool Settings — Current tool options
- Layers Panel — Layer selection
- Terrain Panel — Terrain type selection
- Biomes Panel — Biome painting
- Brushes Panel — Brush selection
- Info Panel — Cursor info, statistics

## Main View

### WorldPainter (2D View)

**Location:** [WPGUI/.../WorldPainter.java](../../WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/WorldPainter.java)

The main editing surface. Extends `WorldPainterView` → `TiledImageViewer`.

```java
public class WorldPainter extends WorldPainterView {
    private Dimension dimension;
    private boolean showGrid, showContours, showBiomes;
    private int viewDistance;
    
    // Rendering
    @Override
    protected void paintTile(Graphics2D g2d, int tileX, int tileY) {
        Tile tile = dimension.getTile(tileX, tileY);
        if (tile != null) {
            renderTile(g2d, tile);
        }
    }
    
    // Mouse handling
    @Override
    public void mousePressed(MouseEvent e) {
        // Delegate to active operation
        if (activeOperation != null) {
            activeOperation.mousePressed(e);
        }
    }
}
```

### 3D View

**Location:** [WPGUI/.../threedeeview/](../../WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/threedeeview/)

| Class | Description |
|-------|-------------|
| `ThreeDeeFrame` | 3D preview window |
| `ThreeDeeView` | OpenGL rendering canvas |
| `Tile3DRenderer` | Renders tiles in 3D |
| `ThreeDeeRenderManager` | Background rendering |

## Operations (Tools)

### Operation Interface

**Location:** [WPCore/.../operations/Operation.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/operations/Operation.java)

```java
public interface Operation {
    String getName();
    String getDescription();
    Icon getIcon();
    
    void setActive(boolean active);
    boolean isActive();
    
    JPanel getOptionsPanel();  // Tool settings UI
    
    void interrupt();
}
```

### Operation Hierarchy

```
Operation (interface)
  └── AbstractOperation
        ├── AbstractGlobalOperation
        │     └── FillOperation, etc.
        └── MouseOrTabletOperation
              └── AbstractBrushOperation
                    ├── RadiusOperation
                    └── AbstractPaintOperation
                          └── Height, Flatten, Smooth, etc.
```

### Built-in Operations

| Operation | Description |
|-----------|-------------|
| `Height` | Raise/lower terrain |
| `Flatten` | Flatten to level |
| `Smooth` | Smooth terrain |
| `Fill` | Fill enclosed areas |
| `Flood` | Flood with water |
| `Sponge` | Remove water |
| `Pencil` | Direct drawing |
| `SprayPaint` | Spray-paint |
| `Erode` | Apply erosion |
| `RaiseMountain` | Create peaks |
| `RaisePyramid` | Create pyramids |
| `SetSpawnPoint` | Set spawn |
| `Text` | Paint text |

### Paint System

**Location:** [WPGUI/.../painting/](../../WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/painting/)

```java
public interface Paint {
    void apply(Dimension dimension, int x, int y, float strength);
    void remove(Dimension dimension, int x, int y, float strength);
}

// Implementations
TerrainPaint       // Paint terrain types
LayerPaint         // Paint layers
BitLayerPaint      // Paint bit layers
NibbleLayerPaint   // Paint nibble layers
```

## Brushes

### Brush Interface

**Location:** [WPCore/.../brushes/Brush.java](../../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/brushes/Brush.java)

```java
public interface Brush {
    String getName();
    float getStrength(int dx, int dy);      // Strength at offset
    float getFullStrength(int dx, int dy);  // Ignoring level
    int getRadius();
    void setRadius(int radius);
    float getLevel();
    void setLevel(float level);
    BrushShape getBrushShape();
}
```

### Brush Types

| Type | Description |
|------|-------------|
| **Symmetric** | Mathematically symmetric (circle, square) |
| **Constant** | Uniform strength |
| **Linear** | Linear falloff |
| **Cosine** | Smooth sine falloff |
| **Plateau** | Flat center with falloff edge |
| **Spike** | Quadratic falloff (sharp) |
| **Dome** | Spherical falloff |
| **Bitmap** | Custom image-based |

### Brush Shapes

```java
public enum BrushShape {
    CIRCLE,
    SQUARE
}
```

## Dialogs

### Base Classes

| Class | Description |
|-------|-------------|
| `WorldPainterDialog` | Standard dialog base |
| `WorldPainterModalFrame` | Modal frame alternative |

### Key Dialogs

| Dialog | Purpose |
|--------|---------|
| `NewWorldDialog` | Create new world |
| `ExportWorldDialog` | Export to Minecraft/Hytale |
| `ImportHeightMapDialog` | Import height maps |
| `MergeWorldDialog` | Merge existing map |
| `DimensionPropertiesDialog` | Edit dimension settings |
| `PreferencesDialog` | Application preferences |
| `FillDialog` | Global fill options |
| `ScaleWorldDialog` | Scale world |
| `RotateWorldDialog` | Rotate world |
| `ShiftWorldDialog` | Shift world |
| `AboutDialog` | About information |

### Layer Editors

**Location:** [WPGUI/.../layers/](../../WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/layers/)

| Editor | Layer Type |
|--------|------------|
| `EditLayerDialog` | Generic layer editor |
| `TunnelLayerDialog` | Tunnel/cave layers |
| `UndergroundPocketsDialog` | Underground pockets |
| `PlantLayerDialog` | Plant layers |
| `GroundCoverDialog` | Ground cover layers |

## Menu Structure

```
File
├── New World
├── Open World
├── Save / Save As
├── Export → (Platform submenu)
├── Import → Height Map, World
└── Exit

Edit
├── Undo / Redo
├── Cut / Copy / Paste
├── Fill
├── Global Operations
└── Preferences

View
├── Zoom In/Out
├── Grid / Contours
├── Biomes / Layers
└── 3D View

Dimension
├── Properties
├── Add Dimension
├── Delete Dimension
└── Go to (dimension)

Tools
├── (All operations)
└── Scripting Console

Help
├── Documentation
├── Check for Updates
└── About
```

## Event Handling

### Brush Events

```java
// In AbstractBrushOperation
@Override
public void tick(int x, int y, boolean undo, boolean first, float strength) {
    // Get brush strength at each point
    for (int dx = -radius; dx <= radius; dx++) {
        for (int dy = -radius; dy <= radius; dy++) {
            float brushStrength = brush.getStrength(dx, dy);
            if (brushStrength > 0) {
                applyEffect(x + dx, y + dy, brushStrength * strength);
            }
        }
    }
}
```

### Undo/Redo

```java
// In App
private final UndoManager undoManager = new UndoManager();

public void undo() {
    if (undoManager.canUndo()) {
        undoManager.undo();
        refresh();
    }
}

public void redo() {
    if (undoManager.canRedo()) {
        undoManager.redo();
        refresh();
    }
}
```

## Configuration

**Location:** [WPGUI/.../Configuration.java](../../WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/Configuration.java)

Persistent application settings.

```java
public class Configuration implements Serializable {
    private UUID uuid;
    private File customObjectsDirectory;
    private LookAndFeel lookAndFeel;
    private int maximumBrushSize;
    private boolean autosave;
    private int autosaveInterval;
    // ... many more settings
}
```

## See Also

- [01-ARCHITECTURE.md](01-ARCHITECTURE.md) — Core architecture
- [07-PLUGINS.md](07-PLUGINS.md) — Plugin system for operations
