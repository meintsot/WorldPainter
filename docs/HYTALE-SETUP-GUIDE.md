# Hytale WorldPainter — Team Setup & Distribution Guide

This guide covers how to build, distribute, and maintain the Hytale-modified WorldPainter fork for your team.

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Option A: Build From Source (Developers)](#2-option-a-build-from-source)
3. [Option B: Pre-Built Distribution (Non-Developers)](#3-option-b-pre-built-distribution)
4. [Keeping In Sync With Upstream WorldPainter](#4-keeping-in-sync-with-upstream)
5. [Recommended Git Workflow](#5-recommended-git-workflow)
6. [Troubleshooting](#6-troubleshooting)

---

## 1. Architecture Overview

### What We Changed

Our fork (`github.com/meintsot/WorldPainter`) adds Hytale game support on top of the open-source WorldPainter 2.26.0. The changes live in:

| Area | Files | Purpose |
|------|-------|---------|
| **Platform** | `HytalePlatformProvider`, `HytalePlatform` | Registers Hytale as an export target |
| **Terrains** | `HytaleTerrainHelper`, 78 terrain presets | Maps Hytale blocks to WorldPainter terrains |
| **Blocks** | `HytaleBlockTypes`, 1612 blocks | Full Hytale block registry |
| **Biomes** | `HytaleBiomeHelper`, 101 biomes | Hytale biome system |
| **Layers** | `HytaleFluidLayer`, `HytaleEnvironmentLayer`, `HytaleEntityLayer`, `HytalePrefabLayer` | Water tints, environments, entity spawns, prefabs |
| **Data** | `HytaleEnvironmentData` (89 envs), `HytaleEntityData` (150+ entities) | Hytale asset registries |
| **Export** | `HytaleWorldExporter`, `HytaleChunk`, `HytaleBsonChunkSerializer` | Exports worlds to Hytale's BSON format |

All changes are in **WPCore** (the engine JAR). The GUI module (WPGUI) picks them up automatically through the plugin system.

### How WorldPainter Distributes

The open-source repo produces **thin JAR files** (not installers). The official `.exe`/`.dmg`/`.deb` installers at worldpainter.net are built with a separate private pipeline. We don't have access to that, so we use alternative distribution methods described below.

---

## 2. Option A: Build From Source

**Best for:** Developers on your team, or anyone comfortable with command-line tools.

### Prerequisites

Each team member needs:

1. **JDK 17** — Download from [Microsoft OpenJDK](https://learn.microsoft.com/en-us/java/openjdk/download) or [Adoptium](https://adoptium.net/)
2. **Apache Maven 3.9+** — Download from [maven.apache.org](https://maven.apache.org/download.cgi)
3. **Git** — [git-scm.com](https://git-scm.com/downloads)

### Step-by-Step Setup

#### 2.1 Install JDK 17

```bash
# Verify after installation
java -version
# Should show: openjdk version "17.x.x"
```

#### 2.2 Install Maven

Extract Maven to a folder (e.g., `C:\tools\apache-maven-3.9.5`). Add `C:\tools\apache-maven-3.9.5\bin` to your system `PATH`.

```bash
mvn -version
# Should show Maven 3.9.x with Java 17
```

#### 2.3 Configure Maven Toolchains

Create (or edit) the file `%USERPROFILE%\.m2\toolchains.xml` (Windows) or `~/.m2/toolchains.xml` (macOS/Linux):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<toolchains>
  <toolchain>
    <type>jdk</type>
    <provides>
      <version>17</version>
    </provides>
    <configuration>
      <jdkHome>C:\Program Files\Microsoft\jdk-17.0.15.6-hotspot</jdkHome>
    </configuration>
  </toolchain>
</toolchains>
```

> **Important:** Replace the `<jdkHome>` path with your actual JDK 17 installation directory.
>
> - Windows typical: `C:\Program Files\Microsoft\jdk-17.x.x-hotspot` or `C:\Program Files\Eclipse Adoptium\jdk-17.x.x-hotspot`
> - macOS typical: `/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home`
> - Linux typical: `/usr/lib/jvm/java-17-openjdk-amd64`

#### 2.4 Install JIDE Docking Framework

WorldPainter uses the JIDE Docking Framework (commercial library, evaluation available). Download from [jidesoft.com](https://www.jidesoft.com/) and install the JARs into your local Maven repo:

```bash
mvn install:install-file -DgroupId=com.jidesoft -DartifactId=jide-common -Dversion=3.7.15 -Dpackaging=jar -Dfile=jide-common.jar
mvn install:install-file -DgroupId=com.jidesoft -DartifactId=jide-dock -Dversion=3.7.15 -Dpackaging=jar -Dfile=jide-dock.jar
mvn install:install-file -DgroupId=com.jidesoft -DartifactId=jide-plaf-jdk7 -Dversion=3.7.15 -Dpackaging=jar -Dfile=jide-plaf-jdk7.jar
```

> **Shortcut:** If one team member has built successfully, they can share their `~/.m2/repository/com/jidesoft/` folder so others can just copy it.

#### 2.5 Clone and Build

```bash
git clone git@github.com:meintsot/WorldPainter.git
cd WorldPainter/WorldPainter
mvn -DskipTests=true install
```

#### 2.6 Run

```bash
# Option 1: Use the batch script (Windows)
.\build-and-run-worldpainter.bat

# Option 2: Maven exec (any OS, after build)
cd WorldPainter
mvn -pl WPGUI exec:exec

# Option 3: Direct java command (after build)
java --add-opens java.desktop/sun.swing=ALL-UNNAMED \
     --add-opens java.desktop/javax.swing.plaf.basic=ALL-UNNAMED \
     --add-opens java.desktop/java.awt=ALL-UNNAMED \
     --add-opens java.desktop/sun.awt=ALL-UNNAMED \
     --add-opens java.base/java.lang=ALL-UNNAMED \
     -cp "WPCore/target/classes;WPGUI/target/classes;WPDynmapPreviewer/target/classes;<dependency-jars>" \
     org.pepsoft.worldpainter.Main
```

#### 2.7 Updating

When new Hytale features are pushed:

```bash
cd WorldPainter
git pull origin master
cd WorldPainter
mvn -DskipTests=true install
# Then run as above
```

---

## 3. Option B: Pre-Built Distribution

**Best for:** Non-developer team members who just need to run it.

### 3.1 Create a Fat JAR (Recommended)

Add the Maven Shade Plugin to the WPGUI build to produce a single self-contained JAR. Add this to `WorldPainter/WPGUI/pom.xml` inside `<build><plugins>`:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <version>3.5.1</version>
    <executions>
        <execution>
            <phase>package</phase>
            <goals>
                <goal>shade</goal>
            </goals>
            <configuration>
                <transformers>
                    <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                        <mainClass>org.pepsoft.worldpainter.Main</mainClass>
                    </transformer>
                    <transformer implementation="org.apache.maven.plugins.shade.resource.ServicesResourceTransformer"/>
                </transformers>
                <filters>
                    <filter>
                        <artifact>*:*</artifact>
                        <excludes>
                            <exclude>META-INF/*.SF</exclude>
                            <exclude>META-INF/*.DSA</exclude>
                            <exclude>META-INF/*.RSA</exclude>
                        </excludes>
                    </filter>
                </filters>
                <createDependencyReducedPom>false</createDependencyReducedPom>
                <shadedArtifactAttached>true</shadedArtifactAttached>
                <shadedClassifierName>full</shadedClassifierName>
            </configuration>
        </execution>
    </executions>
</plugin>
```

Then build:

```bash
cd WorldPainter/WorldPainter
mvn -DskipTests=true package
```

This produces: `WPGUI/target/WPGUI-2.26.1-SNAPSHOT-full.jar`

### 3.2 Create a Launch Script

Distribute the fat JAR with a launcher script.

**Windows — `run-worldpainter.bat`:**

```batch
@echo off
java --add-opens java.desktop/sun.swing=ALL-UNNAMED ^
     --add-opens java.desktop/javax.swing.plaf.basic=ALL-UNNAMED ^
     --add-opens java.desktop/java.awt=ALL-UNNAMED ^
     --add-opens java.desktop/sun.awt=ALL-UNNAMED ^
     --add-opens java.base/java.lang=ALL-UNNAMED ^
     -Xmx2G ^
     -jar WPGUI-2.26.1-SNAPSHOT-full.jar
pause
```

**macOS/Linux — `run-worldpainter.sh`:**

```bash
#!/bin/bash
java --add-opens java.desktop/sun.swing=ALL-UNNAMED \
     --add-opens java.desktop/javax.swing.plaf.basic=ALL-UNNAMED \
     --add-opens java.desktop/java.awt=ALL-UNNAMED \
     --add-opens java.desktop/sun.awt=ALL-UNNAMED \
     --add-opens java.base/java.lang=ALL-UNNAMED \
     -Xmx2G \
     -jar WPGUI-2.26.1-SNAPSHOT-full.jar
```

### 3.3 Distribution Package

Create a zip/folder to share:

```
WorldPainter-Hytale/
├── WPGUI-2.26.1-SNAPSHOT-full.jar
├── run-worldpainter.bat          # Windows
├── run-worldpainter.sh           # macOS/Linux
└── README.txt                    # Brief instructions
```

**Team members only need JDK 17 installed** — no Maven, no Git, no build tools. They just:

1. Install JDK 17
2. Unzip the package
3. Double-click `run-worldpainter.bat` (or run the `.sh` script)

### 3.4 Automate Builds with GitHub Actions (Optional)

Add `.github/workflows/build.yml` to auto-build on every push and publish the fat JAR as a GitHub Release artifact:

```yaml
name: Build WorldPainter Hytale

on:
  push:
    branches: [master]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Configure Maven Toolchains
        run: |
          mkdir -p ~/.m2
          cat > ~/.m2/toolchains.xml << 'EOF'
          <?xml version="1.0" encoding="UTF-8"?>
          <toolchains>
            <toolchain>
              <type>jdk</type>
              <provides><version>17</version></provides>
              <configuration>
                <jdkHome>${JAVA_HOME}</jdkHome>
              </configuration>
            </toolchain>
          </toolchains>
          EOF

      - name: Install JIDE Dependencies
        run: |
          # You'll need to host these JARs somewhere accessible
          # or check them into a lib/ directory in the repo
          # Example with local lib/ folder:
          mvn install:install-file -DgroupId=com.jidesoft -DartifactId=jide-common -Dversion=3.7.15 -Dpackaging=jar -Dfile=lib/jide-common.jar
          mvn install:install-file -DgroupId=com.jidesoft -DartifactId=jide-dock -Dversion=3.7.15 -Dpackaging=jar -Dfile=lib/jide-dock.jar
          mvn install:install-file -DgroupId=com.jidesoft -DartifactId=jide-plaf-jdk7 -Dversion=3.7.15 -Dpackaging=jar -Dfile=lib/jide-plaf-jdk7.jar

      - name: Build
        run: |
          cd WorldPainter
          mvn -DskipTests=true package

      - name: Upload Artifact
        uses: actions/upload-artifact@v4
        with:
          name: WorldPainter-Hytale
          path: WorldPainter/WPGUI/target/WPGUI-*-full.jar
```

> **Note on JIDE:** Since JIDE is commercial, you can't host the JARs publicly. Either: commit them to a private `lib/` folder in your repo, or use a private Maven repository (Nexus, Artifactory, GitHub Packages).

---

## 4. Keeping In Sync With Upstream

The upstream WorldPainter is at **`github.com/Captain-Chaos/WorldPainter`**. As the upstream project releases new versions, you'll want to pull in their improvements.

### 4.1 Add the Upstream Remote (One-Time)

```bash
cd WorldPainter
git remote add upstream https://github.com/Captain-Chaos/WorldPainter.git
git fetch upstream
```

Verify:

```bash
git remote -v
# origin    git@github.com:meintsot/WorldPainter.git (fetch)
# origin    git@github.com:meintsot/WorldPainter.git (push)
# upstream  https://github.com/Captain-Chaos/WorldPainter.git (fetch)
# upstream  https://github.com/Captain-Chaos/WorldPainter.git (push)
```

### 4.2 Merging Upstream Updates

When upstream releases a new version:

```bash
# Fetch latest from upstream
git fetch upstream

# See what's new
git log --oneline master..upstream/master | head -20

# Merge upstream into your branch
git merge upstream/master
```

### 4.3 Handling Merge Conflicts

Our Hytale code is mostly in **new files** that don't exist upstream, so conflicts should be rare. The likely conflict points are:

| File | Risk | Why |
|------|------|-----|
| `DefaultPlugin.java` | **Medium** | We added Hytale layers to `getLayers()`. If upstream adds new layers, both sides edit this file. |
| `pom.xml` files | **Medium** | Version bumps, new dependencies. Usually straightforward. |
| Layer base classes | **Low** | If upstream modifies `Layer.java`, `DataSize`, or `Tile` storage. |
| Platform registry | **Low** | If upstream changes how platforms are registered. |
| GUI classes | **Low** | Our terrain panel changes. |

**Conflict resolution strategy:**

1. **New files** (all `Hytale*.java`): No conflict possible — these don't exist upstream.
2. **DefaultPlugin.java**: Keep both sides — our Hytale layers and any new upstream layers.
3. **Version numbers in POMs**: Accept upstream's version, then bump if needed.
4. **API changes**: If upstream changes a method signature we use, update our code to match.

### 4.4 Rebase vs. Merge

**Merge** (recommended for teams):

```bash
git merge upstream/master
# Creates a merge commit, preserves full history
# Easier for multiple team members
```

**Rebase** (cleaner history, solo developer):

```bash
git rebase upstream/master
# Replays your commits on top of upstream
# Cleaner but rewrites history — only do this if you haven't pushed yet
# NEVER rebase if other team members have pulled your commits
```

### 4.5 Sync Schedule

Recommended cadence:

- **Check upstream monthly**: `git fetch upstream && git log --oneline master..upstream/master`
- **Merge when upstream releases a new version** (e.g., 2.27.0)
- **Don't merge every single commit** — wait for stable release tags
- Use `git log upstream/master --oneline --since="2026-01-01"` to see recent activity

### 4.6 Tagging Your Releases

Tag your own releases so team members can check out known-good versions:

```bash
git tag -a v2.26.1-hytale-1 -m "First Hytale release: terrains, biomes, blocks, layers"
git push origin v2.26.1-hytale-1
```

---

## 5. Recommended Git Workflow

### Branch Strategy

```
master (upstream tracking)
  └── hytale-dev (your main development branch)
        ├── feature/fluid-layer
        ├── feature/prefab-layer
        └── feature/entity-spawns
```

The current setup has everything on `master`. For better organization going forward:

```bash
# Create a development branch (one-time setup)
git checkout -b hytale-dev
git push -u origin hytale-dev

# For new features
git checkout -b feature/my-feature hytale-dev
# ... develop ...
git checkout hytale-dev
git merge feature/my-feature

# When upstream updates
git checkout master
git merge upstream/master
git checkout hytale-dev
git merge master
```

### If You Want to Stay on `master` (Simpler)

That's fine too. Just be careful when merging upstream:

```bash
git fetch upstream
git merge upstream/master   # Resolve conflicts if any
git push origin master
```

---

## 6. Troubleshooting

### "Cannot find toolchain for type jdk"

You're missing `~/.m2/toolchains.xml`. Create it as shown in [section 2.3](#23-configure-maven-toolchains).

### "Cannot resolve com.jidesoft:jide-common"

JIDE JARs aren't installed in local Maven repo. Follow [section 2.4](#24-install-jide-docking-framework).

### WorldPainter starts but no Hytale option

- Verify WPCore compiled with your changes: check timestamp of `WPCore/target/classes/org/pepsoft/worldpainter/hytale/`
- Make sure you ran `mvn install` (not just `mvn compile`) — WPGUI needs to pick up the installed WPCore JAR

### "Module not found" or classpath errors

The `--add-opens` flags are required for Java 17. Make sure you're using the launch script, not just `java -jar`.

### Fat JAR won't start

If you get `SecurityException` about signed JARs, ensure the shade plugin `<filters>` section excludes `*.SF`, `*.DSA`, `*.RSA` files (shown in [section 3.1](#31-create-a-fat-jar-recommended)).

### Merge conflicts with upstream

See [section 4.3](#43-handling-merge-conflicts). Most Hytale code is in new files and won't conflict. Focus on `DefaultPlugin.java` and POM files.

---

## Quick Reference

| Task | Command |
|------|---------|
| Build | `cd WorldPainter && mvn -DskipTests=true install` |
| Run | `mvn -pl WPGUI exec:exec` or `.\build-and-run-worldpainter.bat` |
| Update from team | `git pull origin master` then rebuild |
| Check upstream | `git fetch upstream && git log --oneline master..upstream/master` |
| Merge upstream | `git merge upstream/master` then rebuild |
| Tag a release | `git tag -a v2.26.1-hytale-N -m "description"` |
| Build fat JAR | `mvn -DskipTests=true package` (after adding shade plugin) |

---

## Summary of Distribution Options

| Method | Who | Effort | Maintenance |
|--------|-----|--------|-------------|
| **Source build** | Developers | Medium (one-time setup) | `git pull` + rebuild |
| **Fat JAR + script** | Anyone with JDK 17 | Low (unzip + run) | Re-download JAR when updated |
| **GitHub Actions + Releases** | Anyone with JDK 17 | Zero (download from GitHub) | Automatic on push |
