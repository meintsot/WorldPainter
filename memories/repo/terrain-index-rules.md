# WorldPainter Hytale Terrain Index Rules

## Critical: ALL_TERRAINS ordering
- Per-pixel terrain data is stored as numeric indices into `HytaleTerrain.ALL_TERRAINS`
- **New terrains MUST be appended at the END of the list, NEVER inserted in the middle**
- Inserting in the middle shifts all subsequent indices, corrupting saved maps
- This was the root cause of the Calandor map bug (uniform dark brown)

## Build command
```
cd WorldPainter && /c/Users/Sotirios/Downloads/apache-maven-3.9.6/bin/mvn.cmd compile -pl WPCore,WPGUI -am -q
```

## Key files
- `HytaleTerrain.java` - terrain definitions, ALL_TERRAINS list, PICK_LIST
- `HytaleTerrainHelper.java` - Minecraft↔Hytale terrain mapping
- `HytaleBlockRegistry.java` - block name registry, formatDisplayName()
- `HytaleWorldExporter.java` - export logic, ceiling dimension support
- `App.java` - GUI, autosave, custom terrain popup

## Current terrain append order (after GREEN_MOSS_BLOCK)
1. DIRT, BURNT_DIRT, COLD_DIRT, DRY_DIRT, POISONED_DIRT
2. GRAVEL, MOSSY_GRAVEL, SAND_GRAVEL, RED_SAND_GRAVEL, WHITE_SAND_GRAVEL
3. SNOW
