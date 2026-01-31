# WorldPainter Agent Guide
#
# This file is for coding agents working in this repo.
# It summarizes build/test commands and the local code style.

## Sources of truth
- Build setup: `BUILDING.md`
- Code style: `CODESTYLE.md`
- No Cursor/Copilot rules found in `.cursor/rules/`, `.cursorrules`, or `.github/copilot-instructions.md`.

## Project layout
- `WorldPainter/` is the main multi-module Maven project.
- Modules: `WPCore`, `WPGUI`, `WPDynmapPreviewer`.
- `PluginParent/` is a separate parent POM for plugins.
- Source roots are standard Maven (`src/main/java`, `src/test/java`).
- Resources in `WPCore` and `WPGUI` use filtering for a few plugin/property files.

## Prerequisites (required before builds)
- JDK 17 is required. The build uses the Maven toolchains plugin.
- Configure a Maven toolchain for `jdk` version `17`.
- Install non-public dependencies locally:
  - JIDE Docking Framework JARs (commercial eval):
    `jide-common.jar`, `jide-dock.jar`, `jide-plaf-jdk7.jar`
  - If you use a different JIDE version, update `WorldPainter/WPGUI/pom.xml`.
- Maven Central does not host all artifacts; private repo is declared in `WorldPainter/pom.xml`.

## Build commands (from repo root)
- Full build (skip tests):
  - `mvn -f WorldPainter/pom.xml install -DskipTests=true`
- Full build (with tests):
  - `mvn -f WorldPainter/pom.xml install`
- Build a single module and its deps:
  - `mvn -f WorldPainter/pom.xml -pl WPGUI -am install -DskipTests=true`
- Build just a module without deps (use with care):
  - `mvn -f WorldPainter/pom.xml -pl WPCore install -DskipTests=true`

## Run the app
- Dev run via Maven exec plugin:
  - `mvn -f WorldPainter/pom.xml -pl WPGUI exec:exec`
- Main class (IDE):
  - `org.pepsoft.worldpainter.Main`
- The exec plugin sets `-Dorg.pepsoft.worldpainter.devMode=true`.

## Tests
- Unit tests (all modules):
  - `mvn -f WorldPainter/pom.xml test`
- Unit tests (single module):
  - `mvn -f WorldPainter/pom.xml -pl WPCore test`
- Single test class (JUnit 4):
  - `mvn -f WorldPainter/pom.xml -pl WPGUI -Dtest=MyTest test`
- Single test method:
  - `mvn -f WorldPainter/pom.xml -pl WPGUI -Dtest=MyTest#myMethod test`
- Integration tests (failsafe profile):
  - `mvn -f WorldPainter/pom.xml -Pintegration-tests verify`
- Single integration test class:
  - `mvn -f WorldPainter/pom.xml -Pintegration-tests -Dit.test=RegressionIT verify`
- Tests are JUnit 4; use `@Test` from `org.junit`.
- Integration tests live in WPGUI and run via Failsafe.

## Lint/format
- No dedicated lint/format task found in the POMs.
- Follow `CODESTYLE.md` and existing file formatting.
- Avoid introducing auto-formatter churn.

## Code style (Java)
### General conventions
- Follow Sun/Oracle Java Code Conventions unless overridden in `CODESTYLE.md`.
- Indentation: 4 spaces, no tabs.
- Line length: roughly 120 chars; prefer readability over strict limits.
- Always use braces for control blocks, even single statements.
- Opening brace goes on the same line as the statement.
- Source files are UTF-8 without BOM.
- Java target/source is 17 per the Maven compiler plugin.

### Naming
- Classes/interfaces: `UpperCamelCase`.
- Methods/fields: `lowerCamelCase`.
- Constants: `UPPER_SNAKE_CASE`.
- Prefer descriptive names; self-documenting code is expected.
- Avoid unnecessary abbreviations; clarity beats brevity.

### Imports
- Package declaration, blank line, then imports.
- Non-static imports first, static imports last, with a blank line between.
- Import groups are separated by blank lines (e.g., org.*, javax.*, java.*).
- Static imports are used when it improves readability; follow local patterns.
- Avoid reorganizing imports unless you are editing the file anyway.
- Wildcard imports exist in the codebase; keep the local convention of the file you touch.

### Formatting details
- Redundant parentheses are encouraged to avoid ambiguity.
- Switch cases may be indented one extra level.
- Do not add or remove formatting-only changes unless you touch a large portion
  of the file; then normalize the whole file to the style rules.
- Prefer explicit braces and clear multi-line conditionals for readability.

### Members order inside classes
- Constructors
- Instance methods
- Static methods
- Instance fields
- Static fields
- Inner classes/interfaces

Within each category, order by scope:
- Public
- Protected
- Package-private
- Private

### Comments and Javadoc
- Public methods/fields and methods intended for override must have Javadoc.
- Comments should explain why, not what. Prefer self-documenting code.

### Error handling and logging
- Do not swallow exceptions or continue after fatal errors.
- Provide informative exception messages with context.
- Prefer MDC-aware exception wrappers:
  - `MDCWrappingRuntimeException`
  - `MDCCapturingRuntimeException`
  - `MDCWrappingException`
  - `MDCCapturingException`
- Logging uses SLF4J (`org.slf4j`).
- Log with context; avoid noisy debug output in production paths.

## Testing notes
- Tests are JUnit 4 (`junit:junit:4.13.2`).
- Failsafe-based integration tests are in the `integration-tests` profile.

## Practical editing guidance
- Keep changes minimal and consistent with nearby code.
- Use existing patterns in the module you touch (WPCore vs WPGUI).
- When adding new files, match package structure and naming conventions.
- Do not rearrange members unless needed; preserve the class ordering rules.

## Module notes
- Use `-pl <module> -am` to build a module with its dependencies.
- `WPCore` holds core logic and data structures.
- `WPGUI` holds the GUI, tools, and integration tests.
- `WPDynmapPreviewer` holds the Dynmap previewer integration.
- `PluginParent` is a parent POM for plugin projects, not part of the main build.

## References
- Build instructions: `BUILDING.md`
- Code style: `CODESTYLE.md`
