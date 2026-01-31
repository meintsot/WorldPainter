# Hytale Environment and Weather System

## Environment Assets

Environment assets control per-biome/zone visual settings:

```java
public class Environment {
    public static final int HOURS_PER_DAY = 24;
    
    private String id;                // e.g., "Default"
    private Color waterTint;          // Water color
    private Map<String, FluidParticle> fluidParticles;
    private Int2ObjectMap<IWeightedMap<WeatherForecast>> weatherForecasts; // By hour
    private float spawnDensity;
    private boolean blockModificationAllowed;
}
```

## Weather System

### Weather Properties

```java
public class Weather {
    // Sky colors (interpolated by time of day)
    private TimeColorAlpha[] skyTopColors;
    private TimeColorAlpha[] skyBottomColors;
    private TimeColorAlpha[] skySunsetColors;
    private String stars;  // Star texture
    
    // Sun/Moon
    private TimeColor[] sunColors;
    private TimeFloat[] sunScales;
    private TimeColorAlpha[] sunGlowColors;
    private TimeColorAlpha[] moonColors;
    private TimeFloat[] moonScales;
    private DayTexture[] moons;  // Moon phases
    
    // Fog
    private TimeColor[] fogColors;
    private TimeFloat[] fogDensities;
    private TimeFloat[] fogHeightFalloffs;
    private float[] fogDistance;  // [near, far]
    private FogOptions fogOptions;
    
    // Effects
    private Cloud[] clouds;
    private WeatherParticle particle;
    private String screenEffect;
    private TimeColorAlpha[] screenEffectColors;
    private TimeColor[] colorFilters;
    private TimeColor[] waterTints;
    private TimeFloat[] sunlightDampingMultiplier;
}
```

### Time Interpolation Types

```java
public class TimeColor {
    float hour;    // 0.0-24.0
    Color color;   // RGB
}

public class TimeColorAlpha {
    float hour;
    ColorAlpha color;  // RGBA
}

public class TimeFloat {
    float hour;
    float value;
}

public class DayTexture {
    int day;           // Day in lunar cycle
    String texture;    // Moon texture path
}
```

## Weather Forecasts

Probabilistic weather selection per hour:

```java
public class WeatherForecast implements IWeightedElement {
    private String weatherId;      // Weather asset reference
    private int weatherIndex;      // Runtime index
    private double weight;         // Probability weight
}
```

## Environment Chunk Storage

### EnvironmentChunk

```java
public class EnvironmentChunk implements Component<ChunkStore> {
    public static final int COLUMNS = 1024;  // 32×32
    
    private EnvironmentColumn[] columns;
    
    public int get(int x, int y, int z) {
        return columns[x + z * 32].get(y);
    }
    
    public void setColumn(int x, int z, int environmentId) {
        columns[x + z * 32].set(environmentId);
    }
}
```

### EnvironmentColumn (Run-Length Encoded)

```java
public class EnvironmentColumn {
    private IntArrayList maxYs;    // Range end Y values
    private IntArrayList values;   // Environment IDs
    
    // Example: maxYs=[63, 127], values=[1, 2, 3]
    // y ∈ (-∞, 63] = env 1
    // y ∈ [64, 127] = env 2
    // y ∈ [128, +∞) = env 3
}
```

### Serialization

```java
void serialize(ByteBuf buf) {
    // Environment ID mapping
    buf.writeInt(counts.size());
    for (Entry entry : counts) {
        buf.writeInt(environmentId);
        writeUTF(buf, environmentKey);  // e.g., "Default"
    }
    
    // Column data
    for (EnvironmentColumn column : columns) {
        column.serialize(buf, (id, b) -> b.writeInt(id));
    }
}
```

## Zone Discovery

```java
public record ZoneDiscoveryConfig(
    boolean display,         // Show notification
    String zone,             // Display name
    String soundEventId,     // Sound
    String icon,             // Icon
    boolean major,           // Major discovery
    float duration,          // Display time
    float fadeInDuration,
    float fadeOutDuration
)
```

## Climate System

### Climate Noise

```java
public class ClimateNoise {
    Grid grid;                    // Cell grid
    NoiseProperty continent;      // Land/ocean
    NoiseProperty temperature;    // Temperature
    NoiseProperty intensity;      // Weather intensity
    Thresholds thresholds;
    
    static class Thresholds {
        float land = 0.5f;
        float island = 0.8f;
        float beachSize = 0.05f;
        float shallowOceanSize = 0.15f;
    }
}
```

### Climate Graph

```java
public class ClimateGraph {
    public static final int RESOLUTION = 512;
    
    int[] data;          // Climate type IDs
    int[] distanceData;  // Distance to boundary
}
```

## JSON Examples

### Environment Configuration

```json
{
  "Id": "zone1:env_zone1",
  "Parent": "default",
  "WaterTint": { "R": 64, "G": 180, "B": 216 },
  "WeatherForecasts": {
    "0":  [{ "WeatherId": "zone1:zone1_sunny", "Weight": 1.0 }],
    "6":  [{ "WeatherId": "zone1:zone1_sunny", "Weight": 0.8 },
           { "WeatherId": "zone1:zone1_cloudy", "Weight": 0.2 }],
    "12": [{ "WeatherId": "zone1:zone1_sunny", "Weight": 0.6 },
           { "WeatherId": "zone1:zone1_storm", "Weight": 0.4 }]
  }
}
```

### Weather Configuration

```json
{
  "Id": "zone1:zone1_sunny",
  "SkyTopColors": [
    { "Hour": 0,  "Color": { "R": 10, "G": 15, "B": 40, "A": 255 } },
    { "Hour": 6,  "Color": { "R": 135, "G": 180, "B": 220, "A": 255 } },
    { "Hour": 12, "Color": { "R": 100, "G": 170, "B": 255, "A": 255 } }
  ],
  "FogColors": [
    { "Hour": 0,  "Color": { "R": 20, "G": 25, "B": 50 } },
    { "Hour": 12, "Color": { "R": 180, "G": 200, "B": 230 } }
  ],
  "FogDensities": [
    { "Hour": 0, "Value": 0.002 },
    { "Hour": 12, "Value": 0.001 }
  ],
  "FogDistance": [10.0, 500.0],
  "Clouds": [
    {
      "Texture": "Common/Sky/Clouds/Cloud_Layer_1.png",
      "Colors": [{ "Hour": 12, "Color": { "R": 255, "G": 255, "B": 255, "A": 200 } }],
      "Speeds": [{ "Hour": 12, "Value": 0.01 }]
    }
  ]
}
```

## WorldConfig Settings

```java
public class WorldConfig {
    private boolean isGameTimePaused = false;
    private Instant gameTime = parseTime("05:30");  // 5:30 AM
    private String forcedWeather = null;  // Override weather
    private ClientEffectWorldSettings clientEffects;
}

public class ClientEffectWorldSettings {
    float sunHeightPercent = 100.0f;
    float sunAngleDegrees = 0.0f;
    float bloomIntensity = 0.3f;
    float bloomPower = 8.0f;
    float sunIntensity = 0.25f;
    float sunshaftIntensity = 0.3f;
    float sunshaftScaleFactor = 4.0f;
}
```

## WorldPainter Integration

### Setting Environment

For export, use "Default" environment for all columns:

```java
void writeEnvironmentChunk(ByteBuf buf) {
    // Single environment mapping
    buf.writeInt(1);  // Count
    buf.writeInt(1);  // Environment ID
    writeUTF(buf, "Default");  // Environment key (case-sensitive!)
    
    // All columns use same environment
    for (int i = 0; i < 1024; i++) {
        // Single range covering all Y
        buf.writeInt(1);  // Range count
        buf.writeInt(Integer.MAX_VALUE);  // Max Y
        buf.writeInt(1);  // Environment ID
    }
}
```

### Known Environment IDs

- `"Default"` - Standard environment
- `"Zone1_Emerald_Wilds"` - Zone 1 environment
- (Environment names are case-sensitive!)

### Forcing Weather

In world config.json:
```json
{
  "ForcedWeather": "Zone1_Sunny"
}
```

### Time Settings

```json
{
  "IsGameTimePaused": false,
  "GameTime": "0001-01-01T12:00:00Z"
}
```
