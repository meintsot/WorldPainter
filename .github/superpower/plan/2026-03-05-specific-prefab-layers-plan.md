# Specific Prefab Layers — Implementation Plan

**Goal:** Replace the fixed 11-category prefab grid with a searchable list of all individual `.prefab.json` files from Hytale assets. Users multi-select prefabs, pick a custom color, and create persistent BIT layers that appear in the Layers panel.

**Architecture:** New `CustomLayer` subclass (`HytaleSpecificPrefabLayer`) with BIT data size. Discovery class scans asset folders. New Prefabs tab UI with `JTextField` search + `JList` multi-select + "Create Layer" dialog with `JColorChooser`. Export writes exact file paths to `PrefabMarker`.

**Tech Stack:** Java 8+, Swing (JList, JColorChooser, GridBagLayout), JUnit 4, WorldPainter Layer/CustomLayer framework, JIDE Docking.

---

## Task 1: Create `PrefabFileEntry` data class

**Step 1: Write the failing test**
- File: `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/PrefabFileEntryTest.java`
- Code:
  ```java
  package org.pepsoft.worldpainter.hytale;

  import org.junit.Test;
  import static org.junit.Assert.*;

  public class PrefabFileEntryTest {
      @Test
      public void testFieldsAndToString() {
          PrefabFileEntry entry = new PrefabFileEntry(
              "Oak_Stage5_003", "Trees", "Oak",
              "Prefabs/Trees/Oak/Stage_5/Oak_Stage5_003.prefab.json");
          assertEquals("Oak_Stage5_003", entry.getDisplayName());
          assertEquals("Trees", entry.getCategory());
          assertEquals("Oak", entry.getSubCategory());
          assertEquals("Prefabs/Trees/Oak/Stage_5/Oak_Stage5_003.prefab.json", entry.getRelativePath());
          assertEquals("[Trees] Oak / Oak_Stage5_003", entry.toString());
      }

      @Test
      public void testEqualsAndHashCode() {
          PrefabFileEntry a = new PrefabFileEntry("Oak1", "Trees", "Oak", "Prefabs/Trees/Oak/Oak1.prefab.json");
          PrefabFileEntry b = new PrefabFileEntry("Oak1", "Trees", "Oak", "Prefabs/Trees/Oak/Oak1.prefab.json");
          PrefabFileEntry c = new PrefabFileEntry("Birch1", "Trees", "Birch", "Prefabs/Trees/Birch/Birch1.prefab.json");
          assertEquals(a, b);
          assertEquals(a.hashCode(), b.hashCode());
          assertNotEquals(a, c);
      }

      @Test
      public void testSerializable() throws Exception {
          PrefabFileEntry entry = new PrefabFileEntry("Oak1", "Trees", "Oak", "Prefabs/Trees/Oak/Oak1.prefab.json");
          java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
          java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(baos);
          oos.writeObject(entry);
          oos.close();
          java.io.ObjectInputStream ois = new java.io.ObjectInputStream(
              new java.io.ByteArrayInputStream(baos.toByteArray()));
          PrefabFileEntry deserialized = (PrefabFileEntry) ois.readObject();
          assertEquals(entry, deserialized);
          assertEquals(entry.getRelativePath(), deserialized.getRelativePath());
      }
  }
  ```

**Step 2: Run test and verify failure**
- Command: `cd WorldPainter && mvn test -pl WPCore -Dtest=org.pepsoft.worldpainter.hytale.PrefabFileEntryTest -Dsurefire.useFile=false`
- Expected: Compilation failure — `PrefabFileEntry` class does not exist

**Step 3: Implement `PrefabFileEntry`**
- File: `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/PrefabFileEntry.java`
- Code:
  ```java
  package org.pepsoft.worldpainter.hytale;

  import java.io.Serializable;
  import java.util.Objects;

  /**
   * Represents a single discovered Hytale .prefab.json file from the assets directory.
   * Stored in HytaleSpecificPrefabLayer instances and serialized with the world.
   */
  public final class PrefabFileEntry implements Serializable {
      private final String displayName;
      private final String category;
      private final String subCategory;
      private final String relativePath;

      public PrefabFileEntry(String displayName, String category, String subCategory, String relativePath) {
          this.displayName = Objects.requireNonNull(displayName);
          this.category = Objects.requireNonNull(category);
          this.subCategory = Objects.requireNonNull(subCategory);
          this.relativePath = Objects.requireNonNull(relativePath);
      }

      public String getDisplayName() { return displayName; }
      public String getCategory() { return category; }
      public String getSubCategory() { return subCategory; }
      public String getRelativePath() { return relativePath; }

      /**
       * Matches against a search query (case-insensitive).
       * Checks displayName, category, and subCategory.
       */
      public boolean matchesSearch(String query) {
          String lower = query.toLowerCase();
          return displayName.toLowerCase().contains(lower)
              || category.toLowerCase().contains(lower)
              || subCategory.toLowerCase().contains(lower);
      }

      @Override
      public String toString() {
          return "[" + category + "] " + subCategory + " / " + displayName;
      }

      @Override
      public boolean equals(Object o) {
          if (this == o) return true;
          if (!(o instanceof PrefabFileEntry)) return false;
          PrefabFileEntry that = (PrefabFileEntry) o;
          return relativePath.equals(that.relativePath);
      }

      @Override
      public int hashCode() {
          return relativePath.hashCode();
      }

      private static final long serialVersionUID = 1L;
  }
  ```

**Step 4: Run test and verify success**
- Command: `cd WorldPainter && mvn test -pl WPCore -Dtest=org.pepsoft.worldpainter.hytale.PrefabFileEntryTest -Dsurefire.useFile=false`
- Expected:
  ```
  Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
  BUILD SUCCESS
  ```

---

## Task 2: Create `HytalePrefabDiscovery` scanner

**Step 1: Write the failing test**
- File: `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/HytalePrefabDiscoveryTest.java`
- Code:
  ```java
  package org.pepsoft.worldpainter.hytale;

  import org.junit.Rule;
  import org.junit.Test;
  import org.junit.rules.TemporaryFolder;

  import java.io.File;
  import java.io.FileWriter;
  import java.util.List;

  import static org.junit.Assert.*;

  public class HytalePrefabDiscoveryTest {
      @Rule
      public TemporaryFolder tempFolder = new TemporaryFolder();

      @Test
      public void testDiscoversPrefabFiles() throws Exception {
          // Create: Prefabs/Trees/Oak/Stage_5/Oak_Stage5_003.prefab.json
          File prefabs = tempFolder.newFolder("Prefabs");
          File trees = new File(prefabs, "Trees");
          File oak = new File(trees, "Oak");
          File stage5 = new File(oak, "Stage_5");
          stage5.mkdirs();
          File prefabFile = new File(stage5, "Oak_Stage5_003.prefab.json");
          try (FileWriter w = new FileWriter(prefabFile)) { w.write("{}"); }

          // Create: Prefabs/Npc/Kweebec/Oak/Kweebec_Village_01.prefab.json
          File npc = new File(prefabs, "Npc");
          File kweebec = new File(npc, "Kweebec");
          File kweebecOak = new File(kweebec, "Oak");
          kweebecOak.mkdirs();
          File npcFile = new File(kweebecOak, "Kweebec_Village_01.prefab.json");
          try (FileWriter w = new FileWriter(npcFile)) { w.write("{}"); }

          List<PrefabFileEntry> entries = HytalePrefabDiscovery.discoverPrefabs(tempFolder.getRoot());

          assertEquals(2, entries.size());

          // Verify Oak tree entry
          PrefabFileEntry oakEntry = entries.stream()
              .filter(e -> e.getDisplayName().equals("Oak_Stage5_003"))
              .findFirst().orElse(null);
          assertNotNull(oakEntry);
          assertEquals("Trees", oakEntry.getCategory());
          assertEquals("Oak", oakEntry.getSubCategory());
          assertEquals("Prefabs/Trees/Oak/Stage_5/Oak_Stage5_003.prefab.json", oakEntry.getRelativePath());

          // Verify NPC entry
          PrefabFileEntry npcEntry = entries.stream()
              .filter(e -> e.getDisplayName().equals("Kweebec_Village_01"))
              .findFirst().orElse(null);
          assertNotNull(npcEntry);
          assertEquals("Npc", npcEntry.getCategory());
          assertEquals("Kweebec", npcEntry.getSubCategory());
      }

      @Test
      public void testEmptyDirectoryReturnsEmptyList() throws Exception {
          File prefabs = tempFolder.newFolder("Prefabs");
          List<PrefabFileEntry> entries = HytalePrefabDiscovery.discoverPrefabs(tempFolder.getRoot());
          assertTrue(entries.isEmpty());
      }

      @Test
      public void testNoPrefabsDirectoryReturnsEmptyList() throws Exception {
          List<PrefabFileEntry> entries = HytalePrefabDiscovery.discoverPrefabs(tempFolder.getRoot());
          assertTrue(entries.isEmpty());
      }

      @Test
      public void testIgnoresNonPrefabJsonFiles() throws Exception {
          File prefabs = tempFolder.newFolder("Prefabs");
          File trees = new File(prefabs, "Trees");
          File oak = new File(trees, "Oak");
          oak.mkdirs();
          // .json but not .prefab.json
          File notPrefab = new File(oak, "readme.json");
          try (FileWriter w = new FileWriter(notPrefab)) { w.write("{}"); }

          List<PrefabFileEntry> entries = HytalePrefabDiscovery.discoverPrefabs(tempFolder.getRoot());
          assertTrue(entries.isEmpty());
      }

      @Test
      public void testSearchFiltering() throws Exception {
          File prefabs = tempFolder.newFolder("Prefabs");
          File trees = new File(prefabs, "Trees");
          File oak = new File(trees, "Oak");
          File stage = new File(oak, "Stage_5");
          stage.mkdirs();
          File f1 = new File(stage, "Oak_Stage5_001.prefab.json");
          try (FileWriter w = new FileWriter(f1)) { w.write("{}"); }
          File birch = new File(trees, "Birch");
          File bStage = new File(birch, "Stage_1");
          bStage.mkdirs();
          File f2 = new File(bStage, "Birch_Stage1_001.prefab.json");
          try (FileWriter w = new FileWriter(f2)) { w.write("{}"); }

          List<PrefabFileEntry> all = HytalePrefabDiscovery.discoverPrefabs(tempFolder.getRoot());
          assertEquals(2, all.size());

          // Filter by "birch"
          long birchCount = all.stream().filter(e -> e.matchesSearch("birch")).count();
          assertEquals(1, birchCount);
      }
  }
  ```

**Step 2: Run test and verify failure**
- Command: `cd WorldPainter && mvn test -pl WPCore -Dtest=org.pepsoft.worldpainter.hytale.HytalePrefabDiscoveryTest -Dsurefire.useFile=false`
- Expected: Compilation failure — `HytalePrefabDiscovery` class does not exist

**Step 3: Implement `HytalePrefabDiscovery`**
- File: `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytalePrefabDiscovery.java`
- Code:
  ```java
  package org.pepsoft.worldpainter.hytale;

  import java.io.File;
  import java.util.ArrayList;
  import java.util.Collections;
  import java.util.List;

  /**
   * Scans the HytaleAssets directory for .prefab.json files and returns
   * a sorted list of PrefabFileEntry instances.
   */
  public final class HytalePrefabDiscovery {
      private HytalePrefabDiscovery() {} // utility class

      /**
       * Discover all .prefab.json files under {@code baseDir/Prefabs/}.
       *
       * @param baseDir the Hytale assets root (parent of "Prefabs" folder)
       * @return sorted list of PrefabFileEntry, empty if no prefabs found
       */
      public static List<PrefabFileEntry> discoverPrefabs(File baseDir) {
          File prefabsDir = new File(baseDir, "Prefabs");
          if (!prefabsDir.isDirectory()) {
              return Collections.emptyList();
          }
          List<PrefabFileEntry> results = new ArrayList<>();
          scanDirectory(prefabsDir, prefabsDir, results);
          results.sort((a, b) -> {
              int cmp = a.getCategory().compareToIgnoreCase(b.getCategory());
              if (cmp != 0) return cmp;
              cmp = a.getSubCategory().compareToIgnoreCase(b.getSubCategory());
              if (cmp != 0) return cmp;
              return a.getDisplayName().compareToIgnoreCase(b.getDisplayName());
          });
          return results;
      }

      private static void scanDirectory(File dir, File prefabsRoot, List<PrefabFileEntry> results) {
          File[] children = dir.listFiles();
          if (children == null) return;
          for (File child : children) {
              if (child.isDirectory()) {
                  scanDirectory(child, prefabsRoot, results);
              } else if (child.getName().endsWith(".prefab.json")) {
                  PrefabFileEntry entry = createEntry(child, prefabsRoot);
                  if (entry != null) {
                      results.add(entry);
                  }
              }
          }
      }

      private static PrefabFileEntry createEntry(File file, File prefabsRoot) {
          // Build relative path from the assets root (parent of Prefabs/)
          String relativePath = "Prefabs/" + getRelativePath(prefabsRoot, file);

          // Extract category (first folder under Prefabs/)
          // and sub-category (second folder under Prefabs/)
          String pathFromPrefabs = getRelativePath(prefabsRoot, file);
          String[] parts = pathFromPrefabs.replace('\\', '/').split("/");
          if (parts.length < 2) {
              // File directly inside Prefabs/ with no category subfolder
              return null;
          }
          String category = parts[0];
          String subCategory = parts.length >= 3 ? parts[1] : parts[0];
          String fileName = file.getName();
          String displayName = fileName.substring(0, fileName.length() - ".prefab.json".length());

          return new PrefabFileEntry(displayName, category, subCategory,
              relativePath.replace('\\', '/'));
      }

      private static String getRelativePath(File base, File file) {
          return base.toPath().relativize(file.toPath()).toString();
      }
  }
  ```

**Step 4: Run test and verify success**
- Command: `cd WorldPainter && mvn test -pl WPCore -Dtest=org.pepsoft.worldpainter.hytale.HytalePrefabDiscoveryTest -Dsurefire.useFile=false`
- Expected:
  ```
  Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
  BUILD SUCCESS
  ```

---

## Task 3: Create `HytaleSpecificPrefabLayer` (CustomLayer subclass)

**Step 1: Write the failing test**
- File: `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/HytaleSpecificPrefabLayerTest.java`
- Code:
  ```java
  package org.pepsoft.worldpainter.hytale;

  import org.junit.Test;
  import org.pepsoft.worldpainter.layers.Layer;

  import java.awt.*;
  import java.io.*;
  import java.util.Arrays;
  import java.util.List;

  import static org.junit.Assert.*;

  public class HytaleSpecificPrefabLayerTest {
      @Test
      public void testConstructionAndGetters() {
          List<PrefabFileEntry> entries = Arrays.asList(
              new PrefabFileEntry("Oak1", "Trees", "Oak", "Prefabs/Trees/Oak/Oak1.prefab.json"),
              new PrefabFileEntry("Oak2", "Trees", "Oak", "Prefabs/Trees/Oak/Oak2.prefab.json")
          );
          HytaleSpecificPrefabLayer layer = new HytaleSpecificPrefabLayer(
              "My Oak Trees", entries, new Color(0x228B22));

          assertEquals("My Oak Trees", layer.getName());
          assertEquals(Layer.DataSize.BIT, layer.getDataSize());
          assertEquals(2, layer.getPrefabEntries().size());
          assertEquals(new Color(0x228B22), layer.getColor());
      }

      @Test
      public void testDeterministicPrefabSelection() {
          List<PrefabFileEntry> entries = Arrays.asList(
              new PrefabFileEntry("A", "Trees", "Oak", "Prefabs/Trees/Oak/A.prefab.json"),
              new PrefabFileEntry("B", "Trees", "Oak", "Prefabs/Trees/Oak/B.prefab.json"),
              new PrefabFileEntry("C", "Trees", "Oak", "Prefabs/Trees/Oak/C.prefab.json")
          );
          HytaleSpecificPrefabLayer layer = new HytaleSpecificPrefabLayer(
              "Test", entries, Color.GREEN);

          // Same coordinates should always produce same result
          PrefabFileEntry first = layer.selectPrefab(100, 200);
          PrefabFileEntry second = layer.selectPrefab(100, 200);
          assertEquals(first, second);

          // Single-entry layer always returns that entry
          HytaleSpecificPrefabLayer single = new HytaleSpecificPrefabLayer(
              "Single", entries.subList(0, 1), Color.RED);
          assertEquals(entries.get(0), single.selectPrefab(999, 888));
      }

      @Test
      public void testSerialization() throws Exception {
          List<PrefabFileEntry> entries = Arrays.asList(
              new PrefabFileEntry("Oak1", "Trees", "Oak", "Prefabs/Trees/Oak/Oak1.prefab.json"),
              new PrefabFileEntry("Birch1", "Trees", "Birch", "Prefabs/Trees/Birch/Birch1.prefab.json")
          );
          HytaleSpecificPrefabLayer layer = new HytaleSpecificPrefabLayer(
              "Mixed Trees", entries, new Color(0x44, 0x88, 0x22));

          ByteArrayOutputStream baos = new ByteArrayOutputStream();
          ObjectOutputStream oos = new ObjectOutputStream(baos);
          oos.writeObject(layer);
          oos.close();

          ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()));
          HytaleSpecificPrefabLayer deserialized = (HytaleSpecificPrefabLayer) ois.readObject();

          assertEquals("Mixed Trees", deserialized.getName());
          assertEquals(2, deserialized.getPrefabEntries().size());
          assertEquals(new Color(0x44, 0x88, 0x22), deserialized.getColor());
          assertEquals(layer.getPrefabEntries().get(0), deserialized.getPrefabEntries().get(0));
      }

      @Test
      public void testIconIsColoredSquare() {
          HytaleSpecificPrefabLayer layer = new HytaleSpecificPrefabLayer(
              "Test", Arrays.asList(
                  new PrefabFileEntry("A", "T", "S", "Prefabs/T/S/A.prefab.json")),
              new Color(0xFF, 0x00, 0x00));
          assertNotNull(layer.getIcon());
          assertEquals(16, layer.getIcon().getWidth());
          assertEquals(16, layer.getIcon().getHeight());
          // Center pixel should be red
          int centerRGB = layer.getIcon().getRGB(8, 8) & 0x00FFFFFF;
          assertEquals(0xFF0000, centerRGB);
      }
  }
  ```

**Step 2: Run test and verify failure**
- Command: `cd WorldPainter && mvn test -pl WPCore -Dtest=org.pepsoft.worldpainter.hytale.HytaleSpecificPrefabLayerTest -Dsurefire.useFile=false`
- Expected: Compilation failure — `HytaleSpecificPrefabLayer` class does not exist

**Step 3: Implement `HytaleSpecificPrefabLayer`**
- File: `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleSpecificPrefabLayer.java`
- Code:
  ```java
  package org.pepsoft.worldpainter.hytale;

  import org.pepsoft.worldpainter.layers.CustomLayer;

  import java.awt.*;
  import java.awt.image.BufferedImage;
  import java.io.Serializable;
  import java.util.ArrayList;
  import java.util.Collections;
  import java.util.List;

  /**
   * A custom BIT layer that places specific Hytale prefab files.
   * Each instance stores a list of prefab entries and a user-chosen color.
   * When exported, each painted tile picks one entry from the list deterministically.
   */
  public class HytaleSpecificPrefabLayer extends CustomLayer {
      private final List<PrefabFileEntry> prefabEntries;
      private final int colorRGB;

      public HytaleSpecificPrefabLayer(String name, List<PrefabFileEntry> entries, Color color) {
          super(name,
                "Specific prefabs: " + entries.size() + " variant(s)",
                DataSize.BIT, 91, color);
          this.prefabEntries = new ArrayList<>(entries);
          this.colorRGB = color.getRGB();
      }

      public List<PrefabFileEntry> getPrefabEntries() {
          return Collections.unmodifiableList(prefabEntries);
      }

      public Color getColor() {
          return new Color(colorRGB, true);
      }

      /**
       * Deterministically select a prefab entry for the given world coordinates.
       * Uses a simple hash to pick uniformly from the list.
       */
      public PrefabFileEntry selectPrefab(int worldX, int worldZ) {
          if (prefabEntries.size() == 1) {
              return prefabEntries.get(0);
          }
          // Deterministic hash from coordinates + layer identity
          long hash = worldX * 73856093L ^ worldZ * 19349669L ^ (long) getId().hashCode();
          int index = (int) ((hash & 0x7FFFFFFFL) % prefabEntries.size());
          return prefabEntries.get(index);
      }

      /**
       * Get the first entry's category (used in export for the PrefabMarker category field).
       */
      public String getPrimaryCategory() {
          return prefabEntries.isEmpty() ? "Unknown" : prefabEntries.get(0).getCategory();
      }

      @Override
      public BufferedImage getIcon() {
          if (icon == null) {
              icon = createColorIcon();
          }
          return icon;
      }

      private BufferedImage createColorIcon() {
          BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
          Graphics2D g = img.createGraphics();
          g.setColor(new Color(colorRGB));
          g.fillRect(0, 0, 16, 16);
          g.setColor(Color.DARK_GRAY);
          g.drawRect(0, 0, 15, 15);
          g.dispose();
          return img;
      }

      private transient BufferedImage icon;
      private static final long serialVersionUID = 1L;
  }
  ```

**Step 4: Run test and verify success**
- Command: `cd WorldPainter && mvn test -pl WPCore -Dtest=org.pepsoft.worldpainter.hytale.HytaleSpecificPrefabLayerTest -Dsurefire.useFile=false`
- Expected:
  ```
  Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
  BUILD SUCCESS
  ```

---

## Task 4: Create layer renderer

The renderer class must be named `HytaleSpecificPrefabLayerRenderer` and placed in the `renderers` subpackage so `Layer.init()` auto-discovers it.

**Step 1: Create the renderer**
- File: `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/renderers/HytaleSpecificPrefabLayerRenderer.java`
- Code:
  ```java
  package org.pepsoft.worldpainter.hytale.renderers;

  import org.pepsoft.util.ColourUtils;
  import org.pepsoft.worldpainter.layers.renderers.TransparentColourRenderer;

  /**
   * Renderer for HytaleSpecificPrefabLayer.
   * Since it extends CustomLayer which uses TransparentColourRenderer based on
   * the paint Color, this renderer is auto-discovered via reflection but the
   * actual rendering is handled by the CustomLayer's built-in paint mechanism.
   *
   * This class exists so Layer.init() finds a renderer and the layer
   * renders correctly in the 2D view.
   */
  public class HytaleSpecificPrefabLayerRenderer extends TransparentColourRenderer {
      public HytaleSpecificPrefabLayerRenderer() {
          super(0x228B22); // Default green; actual color comes from CustomLayer paint
      }
  }
  ```

**Step 2: Verify renderer is discovered**
- No separate test needed — the `CustomLayer` base class already handles renderer assignment via its `setPaint(Color)` call in the constructor. The `Layer.init()` mechanism auto-discovers this class by naming convention. Verify by running:
- Command: `cd WorldPainter && mvn compile -pl WPCore`
- Expected: `BUILD SUCCESS`

---

## Task 5: Add export logic for `HytaleSpecificPrefabLayer`

**Step 1: Write the failing test**
- File: `WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/hytale/HytaleSpecificPrefabExportTest.java`
- Code:
  ```java
  package org.pepsoft.worldpainter.hytale;

  import org.junit.Test;

  import java.awt.*;
  import java.util.Arrays;
  import java.util.List;

  import static org.junit.Assert.*;

  public class HytaleSpecificPrefabExportTest {
      @Test
      public void testSelectPrefabIsDeterministic() {
          List<PrefabFileEntry> entries = Arrays.asList(
              new PrefabFileEntry("Oak1", "Trees", "Oak", "Prefabs/Trees/Oak/Stage_5/Oak1.prefab.json"),
              new PrefabFileEntry("Oak2", "Trees", "Oak", "Prefabs/Trees/Oak/Stage_5/Oak2.prefab.json"),
              new PrefabFileEntry("Oak3", "Trees", "Oak", "Prefabs/Trees/Oak/Stage_5/Oak3.prefab.json")
          );
          HytaleSpecificPrefabLayer layer = new HytaleSpecificPrefabLayer("Oaks", entries, Color.GREEN);

          // Verify determinism: same coords → same result
          for (int x = -100; x < 100; x += 13) {
              for (int z = -100; z < 100; z += 17) {
                  PrefabFileEntry a = layer.selectPrefab(x, z);
                  PrefabFileEntry b = layer.selectPrefab(x, z);
                  assertSame("Deterministic at (" + x + "," + z + ")", a, b);
                  assertTrue(entries.contains(a));
              }
          }
      }

      @Test
      public void testSelectPrefabDistribution() {
          List<PrefabFileEntry> entries = Arrays.asList(
              new PrefabFileEntry("A", "T", "S", "Prefabs/T/S/A.prefab.json"),
              new PrefabFileEntry("B", "T", "S", "Prefabs/T/S/B.prefab.json"),
              new PrefabFileEntry("C", "T", "S", "Prefabs/T/S/C.prefab.json")
          );
          HytaleSpecificPrefabLayer layer = new HytaleSpecificPrefabLayer("Test", entries, Color.RED);

          int[] counts = new int[3];
          for (int x = 0; x < 300; x++) {
              for (int z = 0; z < 300; z++) {
                  PrefabFileEntry selected = layer.selectPrefab(x, z);
                  counts[entries.indexOf(selected)]++;
              }
          }

          // Each should get roughly 30000 (90000/3). Allow ±30% tolerance.
          for (int i = 0; i < 3; i++) {
              assertTrue("Entry " + i + " count " + counts[i] + " should be between 21000 and 39000",
                  counts[i] > 21000 && counts[i] < 39000);
          }
      }
  }
  ```

**Step 2: Run test and verify success** (class already exists from Task 3)
- Command: `cd WorldPainter && mvn test -pl WPCore -Dtest=org.pepsoft.worldpainter.hytale.HytaleSpecificPrefabExportTest -Dsurefire.useFile=false`
- Expected:
  ```
  Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
  BUILD SUCCESS
  ```

**Step 3: Modify `HytaleWorldExporter.java` — add specific prefab layer export**
- File: `WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/hytale/HytaleWorldExporter.java`
- Find the existing prefab export section (around line 1116-1126) and add the new logic **after** it:
  ```java
  // ── Specific Prefab Layers ─────────────────────────────
  for (Layer layer : dimension.getAllLayers(false)) {
      if (layer instanceof HytaleSpecificPrefabLayer) {
          HytaleSpecificPrefabLayer specificLayer = (HytaleSpecificPrefabLayer) layer;
          if (tile.getBitLayerValue(specificLayer, tileLocalX, tileLocalZ)) {
              PrefabFileEntry selected = specificLayer.selectPrefab(worldX, worldZ);
              chunk.addPrefabMarker(localX, height + 1, localZ,
                  selected.getCategory(), selected.getRelativePath());
          }
      }
  }
  ```
- Insert this block right after the existing `// ── Prefab Layer ──` block and before `// Update heightmap`.

**Step 4: Verify compilation**
- Command: `cd WorldPainter && mvn compile -pl WPCore`
- Expected: `BUILD SUCCESS`

---

## Task 6: Replace Prefabs tab UI — searchable list with "Create Layer" button

**Step 1: Create `CreatePrefabLayerDialog`**
- File: `WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/hytale/CreatePrefabLayerDialog.java`
- Code:
  ```java
  package org.pepsoft.worldpainter.hytale;

  import javax.swing.*;
  import java.awt.*;
  import java.util.ArrayList;
  import java.util.List;

  /**
   * Dialog for creating a new HytaleSpecificPrefabLayer.
   * Shows the selected prefabs, lets user edit the name and pick a color.
   */
  public class CreatePrefabLayerDialog extends JDialog {
      private final List<PrefabFileEntry> selectedEntries;
      private final JTextField nameField;
      private final JButton colorButton;
      private Color chosenColor = new Color(0x22, 0x8B, 0x22);
      private boolean approved = false;

      public CreatePrefabLayerDialog(Window owner, List<PrefabFileEntry> entries) {
          super(owner, "Create Prefab Layer", ModalityType.APPLICATION_MODAL);
          this.selectedEntries = new ArrayList<>(entries);

          setLayout(new BorderLayout(8, 8));
          JPanel content = new JPanel(new BorderLayout(8, 8));
          content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

          // Top: Name
          JPanel namePanel = new JPanel(new BorderLayout(4, 0));
          namePanel.add(new JLabel("Layer name:"), BorderLayout.WEST);
          String autoName = generateAutoName(entries);
          nameField = new JTextField(autoName, 30);
          namePanel.add(nameField, BorderLayout.CENTER);
          content.add(namePanel, BorderLayout.NORTH);

          // Center: Selected prefabs list (read-only)
          DefaultListModel<String> listModel = new DefaultListModel<>();
          for (PrefabFileEntry e : entries) {
              listModel.addElement(e.toString());
          }
          JList<String> list = new JList<>(listModel);
          list.setEnabled(false);
          JScrollPane scrollPane = new JScrollPane(list);
          scrollPane.setPreferredSize(new Dimension(400, 200));
          scrollPane.setBorder(BorderFactory.createTitledBorder(
              "Selected prefabs (" + entries.size() + ")"));
          content.add(scrollPane, BorderLayout.CENTER);

          // Bottom: Color picker + OK/Cancel
          JPanel bottomPanel = new JPanel(new BorderLayout(8, 0));

          JPanel colorPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
          colorPanel.add(new JLabel("Layer color:"));
          colorButton = new JButton("    ");
          colorButton.setBackground(chosenColor);
          colorButton.setOpaque(true);
          colorButton.setPreferredSize(new Dimension(50, 25));
          colorButton.addActionListener(e -> {
              Color c = JColorChooser.showDialog(this, "Choose Layer Color", chosenColor);
              if (c != null) {
                  chosenColor = c;
                  colorButton.setBackground(c);
              }
          });
          colorPanel.add(colorButton);
          bottomPanel.add(colorPanel, BorderLayout.WEST);

          JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
          JButton okButton = new JButton("OK");
          okButton.addActionListener(e -> {
              if (nameField.getText().trim().isEmpty()) {
                  JOptionPane.showMessageDialog(this, "Please enter a layer name.",
                      "Missing Name", JOptionPane.WARNING_MESSAGE);
                  return;
              }
              approved = true;
              dispose();
          });
          JButton cancelButton = new JButton("Cancel");
          cancelButton.addActionListener(e -> dispose());
          buttonPanel.add(okButton);
          buttonPanel.add(cancelButton);
          bottomPanel.add(buttonPanel, BorderLayout.EAST);

          content.add(bottomPanel, BorderLayout.SOUTH);
          add(content);

          pack();
          setLocationRelativeTo(owner);
          getRootPane().setDefaultButton(okButton);
      }

      public boolean isApproved() { return approved; }

      public HytaleSpecificPrefabLayer createLayer() {
          return new HytaleSpecificPrefabLayer(
              nameField.getText().trim(), selectedEntries, chosenColor);
      }

      private static String generateAutoName(List<PrefabFileEntry> entries) {
          if (entries.isEmpty()) return "Prefab Layer";
          if (entries.size() == 1) return entries.get(0).getDisplayName();
          // Use the most common sub-category
          String subCat = entries.get(0).getSubCategory();
          long sameCount = entries.stream()
              .filter(e -> e.getSubCategory().equals(subCat)).count();
          if (sameCount == entries.size()) {
              return subCat + " (" + entries.size() + " variants)";
          }
          String cat = entries.get(0).getCategory();
          long catCount = entries.stream()
              .filter(e -> e.getCategory().equals(cat)).count();
          if (catCount == entries.size()) {
              return cat + " (" + entries.size() + " variants)";
          }
          return "Prefabs (" + entries.size() + " variants)";
      }
  }
  ```

**Step 2: Verify compilation**
- Command: `cd WorldPainter && mvn compile -pl WPGUI`
- Expected: `BUILD SUCCESS`

---

## Task 7: Rewrite `createPrefabsPanel()` in `App.java`

**Step 1: Modify `App.java` — replace the `createPrefabsPanel()` method**
- File: `WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/App.java`
- Replace the entire `createPrefabsPanel()` method body (from `private JPanel createPrefabsPanel()` through its closing brace before `private JPanel createEntitiesPanel()`) with:

  ```java
  private JPanel createPrefabsPanel() {
      final JPanel panel = new JPanel();
      panel.setLayout(new BorderLayout(4, 4));

      // ─── Top: Show/Solo checkboxes ───
      JPanel topRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
      JCheckBox checkBox = new JCheckBox("Show:");
      checkBox.setHorizontalTextPosition(SwingConstants.LEADING);
      checkBox.setSelected(true);
      checkBox.setToolTipText("Uncheck to hide prefab markers from view");
      checkBox.addActionListener(e -> {
          if (checkBox.isSelected()) {
              hiddenLayers.remove(org.pepsoft.worldpainter.hytale.HytalePrefabLayer.INSTANCE);
          } else {
              hiddenLayers.add(org.pepsoft.worldpainter.hytale.HytalePrefabLayer.INSTANCE);
          }
          updateLayerVisibility();
      });
      topRow.add(checkBox);

      JCheckBox soloCheckBox = new JCheckBox("Solo:");
      soloCheckBox.setHorizontalTextPosition(SwingConstants.LEADING);
      soloCheckBox.setToolTipText("<html>Check to show <em>only</em> prefab markers</html>");
      soloCheckBox.addActionListener(new SoloCheckboxHandler(soloCheckBox, org.pepsoft.worldpainter.hytale.HytalePrefabLayer.INSTANCE));
      layerSoloCheckBoxes.put(org.pepsoft.worldpainter.hytale.HytalePrefabLayer.INSTANCE, soloCheckBox);
      topRow.add(soloCheckBox);
      panel.add(topRow, BorderLayout.NORTH);

      // ─── Center: Search field + scrollable prefab list ───
      JPanel centerPanel = new JPanel(new BorderLayout(2, 2));
      JTextField searchField = new JTextField();
      searchField.setToolTipText("Type to filter prefabs by name, category, or sub-category");

      // Discover all prefab files
      final java.util.List<org.pepsoft.worldpainter.hytale.PrefabFileEntry> allPrefabs =
          org.pepsoft.worldpainter.hytale.HytalePrefabDiscovery.discoverPrefabs(
              getHytaleAssetsDir());

      DefaultListModel<org.pepsoft.worldpainter.hytale.PrefabFileEntry> listModel = new DefaultListModel<>();
      for (org.pepsoft.worldpainter.hytale.PrefabFileEntry entry : allPrefabs) {
          listModel.addElement(entry);
      }
      JList<org.pepsoft.worldpainter.hytale.PrefabFileEntry> prefabList = new JList<>(listModel);
      prefabList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
      prefabList.setVisibleRowCount(15);
      prefabList.setToolTipText("Select one or more prefabs, then click 'Create Layer'");

      // Search filter
      searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
          private void filter() {
              String query = searchField.getText().trim();
              listModel.clear();
              for (org.pepsoft.worldpainter.hytale.PrefabFileEntry entry : allPrefabs) {
                  if (query.isEmpty() || entry.matchesSearch(query)) {
                      listModel.addElement(entry);
                  }
              }
          }
          @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { filter(); }
          @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { filter(); }
          @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { filter(); }
      });

      centerPanel.add(searchField, BorderLayout.NORTH);
      centerPanel.add(new JScrollPane(prefabList), BorderLayout.CENTER);
      panel.add(centerPanel, BorderLayout.CENTER);

      // ─── Bottom: Create Layer button ───
      JButton createLayerButton = new JButton("Create Layer...");
      createLayerButton.setToolTipText("Create a new layer from selected prefabs");
      createLayerButton.addActionListener(e -> {
          java.util.List<org.pepsoft.worldpainter.hytale.PrefabFileEntry> selected =
              prefabList.getSelectedValuesList();
          if (selected.isEmpty()) {
              JOptionPane.showMessageDialog(this,
                  "Select one or more prefabs from the list first.",
                  "No Selection", JOptionPane.INFORMATION_MESSAGE);
              return;
          }
          org.pepsoft.worldpainter.hytale.CreatePrefabLayerDialog dialog =
              new org.pepsoft.worldpainter.hytale.CreatePrefabLayerDialog(this, selected);
          dialog.setVisible(true);
          if (dialog.isApproved()) {
              org.pepsoft.worldpainter.hytale.HytaleSpecificPrefabLayer newLayer = dialog.createLayer();
              // Register the layer with the dimension and UI
              customLayerController.registerCustomLayer(newLayer, true);
              dimension.setCustomLayers(
                  new java.util.ArrayList<>(dimension.getCustomLayers()) {{
                      add(newLayer);
                  }}
              );
          }
      });
      JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
      bottomPanel.add(createLayerButton);
      panel.add(bottomPanel, BorderLayout.SOUTH);

      // ─── Store controls ───
      layerControls.put(org.pepsoft.worldpainter.hytale.HytalePrefabLayer.INSTANCE,
          new LayerControls(org.pepsoft.worldpainter.hytale.HytalePrefabLayer.INSTANCE, checkBox, soloCheckBox));

      panel.putClientProperty(KEY_ICON, new ImageIcon(org.pepsoft.worldpainter.hytale.HytalePrefabLayer.INSTANCE.getIcon()));

      return panel;
  }
  ```

**Step 2: Verify `getHytaleAssetsDir()` exists**
- Search `App.java` for `getHytaleAssetsDir`. If it does not exist, add a helper method that resolves the Hytale assets directory from the world's platform configuration. The discovery should point to the `HytaleAssets/Server/` directory (or whichever location contains the `Prefabs/` folder).
- If the method does not exist, add this to `App.java`:
  ```java
  private File getHytaleAssetsDir() {
      // Resolve from the platform's configured assets path, or fall back to the
      // HytaleAssets/Server directory relative to the WorldPainter installation
      File configuredDir = org.pepsoft.worldpainter.hytale.HytalePlatformProvider.getAssetsDir();
      if (configuredDir != null && configuredDir.isDirectory()) {
          return configuredDir;
      }
      // Fallback: look next to the application
      File fallback = new File("HytaleAssets/Server");
      if (fallback.isDirectory()) {
          return fallback;
      }
      return new File(".");
  }
  ```

**Step 3: Verify compilation**
- Command: `cd WorldPainter && mvn compile -pl WPGUI`
- Expected: `BUILD SUCCESS`

---

## Task 8: Verify full build and run all tests

**Step 1: Run full build**
- Command: `cd WorldPainter && mvn clean install -DskipTests`
- Expected: `BUILD SUCCESS`

**Step 2: Run all Hytale tests**
- Command: `cd WorldPainter && mvn test -pl WPCore -Dtest="org.pepsoft.worldpainter.hytale.*" -Dsurefire.useFile=false`
- Expected:
  ```
  Tests run: ~15+, Failures: 0, Errors: 0, Skipped: 0
  BUILD SUCCESS
  ```

**Step 3: Run WPGUI compilation check**
- Command: `cd WorldPainter && mvn compile -pl WPGUI`
- Expected: `BUILD SUCCESS`

---

## Summary of Files

### New Files (5)
| # | File | Purpose |
|---|------|---------|
| 1 | `WPCore/.../hytale/PrefabFileEntry.java` | Discovered prefab data class |
| 2 | `WPCore/.../hytale/HytalePrefabDiscovery.java` | Asset directory scanner |
| 3 | `WPCore/.../hytale/HytaleSpecificPrefabLayer.java` | CustomLayer subclass (BIT, multi-prefab) |
| 4 | `WPCore/.../hytale/renderers/HytaleSpecificPrefabLayerRenderer.java` | Auto-discovered renderer |
| 5 | `WPGUI/.../hytale/CreatePrefabLayerDialog.java` | Layer creation dialog with color picker |

### New Test Files (4)
| # | File | Tests |
|---|------|-------|
| 1 | `WPCore/.../hytale/PrefabFileEntryTest.java` | Fields, equals, serialization |
| 2 | `WPCore/.../hytale/HytalePrefabDiscoveryTest.java` | Discovery, empty dir, filtering |
| 3 | `WPCore/.../hytale/HytaleSpecificPrefabLayerTest.java` | Construction, serialization, icon |
| 4 | `WPCore/.../hytale/HytaleSpecificPrefabExportTest.java` | Determinism, distribution |

### Modified Files (2)
| # | File | Change |
|---|------|--------|
| 1 | `WPGUI/.../App.java` `createPrefabsPanel()` | Replace category grid with search + list + create button |
| 2 | `WPCore/.../hytale/HytaleWorldExporter.java` | Add specific prefab layer export after existing prefab export |
