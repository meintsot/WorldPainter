# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

WorldPainter (rebranded as **TalePainter** for the Hytale edition) is a Java Swing desktop application for interactive map generation. Originally built for Minecraft, it now also supports Hytale world formats. Licensed under GPL v3.

## Build & Run

Requires **Java 17** and **Maven** with a configured JDK 17 toolchain. JIDE Docking Framework evaluation jars must be installed in the local Maven repo first (see BUILDING.md).

```bash
# Build (from WorldPainter/ directory containing pom.xml)
mvn -DskipTests=true -pl WPGUI -am install

# Run
mvn -pl WPGUI exec:exec

# Package fat JAR for distribution
mvn -DskipTests=true package -pl WPGUI -am
# Output: WPGUI/target/WPGUI-*-full.jar

# Run tests (JUnit 4, slow)
mvn test

# Integration tests
mvn verify -P integration-tests
```

Windows convenience scripts: `build-and-run-worldpainter.bat`, `package-worldpainter.bat`.

## Module Structure

Three Maven modules under `WorldPainter/`:

- **WPCore** — Core library (no GUI). World representation, export/import, Minecraft NBT and Hytale BSON format handling, layers, brushes, biomes, platform abstraction.
- **WPGUI** — Swing UI application. Main class: `org.pepsoft.worldpainter.Main`. Uses JIDE Docking Framework, JPen for tablet support. Packaged as a fat JAR via maven-shade-plugin.
- **WPDynmapPreviewer** — Dynmap integration for map preview.

`PluginParent/` is a separate Maven parent for third-party plugin development.

## Architecture

**Platform abstraction:** `Platform` + platform providers (`JavaPlatformProvider` for Minecraft, `HytalePlatformProvider` for Hytale) allow the same world model to export to different game formats.

**World model:** `Dimension` (a world dimension) contains `Tile` objects (128x128 block areas). Layers are applied via first-pass, second-pass, and incidental exporters.

**Plugin system:** Plugins implement `org.pepsoft.worldpainter.plugins.Plugin`, registered via JSON descriptors in `org.pepsoft.worldpainter.plugins` resource files, managed by `WPPluginManager`.

**Hytale support:** `HytaleBlock`, `HytaleChunk`, `HytaleChunkStore`, `HytaleBiome`, `HytaleBlockRegistry`, BSON serialization via `HytaleBsonChunkSerializer`. Assets are auto-located or user-specified via `HytaleAssetsLocator`.

**Key large classes:** `App.java` (main window, ~378KB), `Dimension.java` (~137KB), `DimensionPropertiesEditor.java` (~260KB), `AbstractWorldExporter.java` (~71KB).

## Code Style (from CODESTYLE.md)

- Sun/Oracle Java conventions as baseline
- 4 spaces indentation, no tabs. ~120 char line length (soft limit)
- Always use curly braces, even for single statements
- Use redundant parentheses for clarity in expressions
- Member order: constructors, instance methods, static methods, instance fields, static fields, inner classes — each group ordered public > protected > package > private
- Exceptions: use `MDCWrappingRuntimeException` / `MDCCapturingRuntimeException` variants to capture logging context
- Don't reformat existing code unless changing a significant portion of the file
- UTF-8 encoding, no BOM

## Key System Properties

- `org.pepsoft.worldpainter.devMode` — enables development mode
- `org.pepsoft.worldpainter.safeMode` — disables scaling and certain features
- `org.pepsoft.worldpainter.configDir` — override config directory
- `org.pepsoft.worldpainter.logLevel` — set logging level

## Dependencies of Note

- **JIDE Docking Framework 3.7.13** — commercial, requires manual local Maven install (evaluation jars)
- **Private Maven repo:** `https://www.worldpainter.net/maven-repo/` for non-public deps
- Logging: SLF4J + Logback
- Serialization: GSON, MongoDB BSON driver
- Custom libraries: `pepsoft.util`, `pepsoft.swingutils`, `pepsoft.jnbt`
