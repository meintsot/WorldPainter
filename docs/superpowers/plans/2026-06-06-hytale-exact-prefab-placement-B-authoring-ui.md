# Hytale Exact Prefab Placement — Plan B: Authoring UI (WPGUI)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax. **Prerequisite: Plan A is merged/working** (placements model + export realized). This plan adds the authoring UI on top of it.

**Goal:** A Hytale-only authoring UI: drag a prefab from a rendered-thumbnail palette onto the map to create a persistent placement, then see/move/rotate/delete it via on-map handles synced to a master "Placements" panel with exact numeric fields.

**Architecture:** A dockable `HytalePrefabPalette` (thumbnail grid + search, backed by `PrefabPaletteModel`) is a drag source emitting a `PrefabTransferable`. A `PrefabPlacementOperation` (modeled on `TownPlanOperation`) installs a `DropTarget` on the map canvas to create placements, and handles select/move/rotate gestures; placements are drawn as on-map footprints/handles by hooking `WorldPainter`'s paint, and mirrored in `HytalePrefabPlacementsPanel`. Thumbnails come from a new headless `HytalePrefabThumbnailRenderer`. All UI is gated to `DefaultPlugin.HYTALE`.

**Tech Stack:** Java 17, Swing, JIDE Docking Framework, AWT drag-and-drop (`DragSource`/`DropTarget`/`Transferable`), `BufferedImage`. Build needs the JIDE eval jars installed (see `BUILDING.md`).

**Spec:** `docs/superpowers/specs/2026-06-06-hytale-exact-prefab-placement-design.md`
**Depends on (Plan A):** `HytalePrefabPlacement`, `Dimension.getHytalePrefabPlacements()/add/remove/replaceHytalePrefabPlacement`.

**Commands (from worktree root):**
- Build GUI: `mvn -f WorldPainter/pom.xml -DskipTests=true -pl WPGUI -am install`
- Run one WPGUI test class: `mvn -f WorldPainter/pom.xml -pl WPGUI test -Dtest=<ClassName> -q`
- Run the app for manual testing: `mvn -f WorldPainter/pom.xml -pl WPGUI exec:exec`

**Manual-testing note:** several tasks below are Swing UI whose acceptance is visual. Each such task lists explicit manual steps; run the app, open/create a **Hytale** world, and follow them. Headless-testable logic (rendering, model, transferable) keeps the TDD loop.

---

## Task 8: `HytalePrefabThumbnailRenderer` (+ cache)

**Files:**
- Create: `WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/hytale/prefab/HytalePrefabThumbnailRenderer.java`
- Test: `WorldPainter/WPGUI/src/test/java/org/pepsoft/worldpainter/hytale/prefab/HytalePrefabThumbnailRendererTest.java`

**Context:** No prefab thumbnail renderer exists. Render a **top-down** image: for each `(x,z)` column take the topmost non-empty block, map block name → color, with light height shading. `BufferedImage` works headless. Reuse the prefab-JSON parse: the simplest dependency-free route is to parse the same `{blocks:[{x,y,z,name,rotation}]}` shape directly (the renderer only needs name + position). **Verify-first:** check whether a Hytale block→color source exists (e.g. used by terrain/`HytaleBlockPalette` rendering) and reuse it; otherwise use the deterministic hash fallback below.

- [ ] **Step 1: Write the failing test** (synthetic prefab on disk; assert a non-empty image with a colored pixel)

```java
package org.pepsoft.worldpainter.hytale.prefab;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.*;

public class HytalePrefabThumbnailRendererTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private File writePrefab() throws Exception {
        String json = "{ \"anchorX\":0,\"anchorY\":0,\"anchorZ\":0, \"blocks\":["
                + "{ \"x\":0,\"y\":0,\"z\":0,\"name\":\"Rock_Stone\",\"rotation\":0 },"
                + "{ \"x\":1,\"y\":0,\"z\":0,\"name\":\"Soil_Grass\",\"rotation\":0 },"
                + "{ \"x\":0,\"y\":1,\"z\":1,\"name\":\"Rock_Stone\",\"rotation\":0 } ] }";
        File assets = tmp.newFolder("HytaleAssets");
        File dir = new File(assets, "Server/Prefabs/Test");
        assertTrue(dir.mkdirs());
        File f = new File(dir, "thumb.prefab.json");
        Files.write(f.toPath(), json.getBytes(StandardCharsets.UTF_8));
        return assets;
    }

    @Test
    public void rendersNonEmptyImage() throws Exception {
        File assets = writePrefab();
        HytalePrefabThumbnailRenderer renderer = new HytalePrefabThumbnailRenderer(assets);
        BufferedImage img = renderer.render("Prefabs/Test/thumb.prefab.json", 64);
        assertNotNull(img);
        assertEquals(64, img.getWidth());
        assertEquals(64, img.getHeight());
        boolean anyOpaque = false;
        for (int y = 0; y < img.getHeight() && !anyOpaque; y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if (((img.getRGB(x, y) >>> 24) & 0xFF) != 0) { anyOpaque = true; break; }
            }
        }
        assertTrue("expected at least one opaque pixel", anyOpaque);
    }

    @Test
    public void cachesByPathAndSize() throws Exception {
        File assets = writePrefab();
        HytalePrefabThumbnailRenderer renderer = new HytalePrefabThumbnailRenderer(assets);
        BufferedImage a = renderer.render("Prefabs/Test/thumb.prefab.json", 48);
        BufferedImage b = renderer.render("Prefabs/Test/thumb.prefab.json", 48);
        assertSame(a, b);
    }

    @Test
    public void missingPrefabReturnsPlaceholderNotNull() {
        HytalePrefabThumbnailRenderer renderer = new HytalePrefabThumbnailRenderer(null);
        BufferedImage img = renderer.render("Prefabs/Nope/missing.prefab.json", 32);
        assertNotNull(img);
        assertEquals(32, img.getWidth());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -f WorldPainter/pom.xml -pl WPGUI test -Dtest=HytalePrefabThumbnailRendererTest -q`
Expected: FAIL — class missing. (First WPGUI build pulls JIDE eval jars per BUILDING.md; if that's not set up, install them before this task.)

- [ ] **Step 3: Implement the renderer**

```java
package org.pepsoft.worldpainter.hytale.prefab;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Renders a top-down thumbnail of a Hytale prefab: for each (x,z) column, the
 * topmost non-empty block's colour, with light height shading. Results are cached
 * by (path, size). Headless-safe (pure {@link BufferedImage}).
 */
public final class HytalePrefabThumbnailRenderer {
    private final File serverDir;
    private final Map<String, BufferedImage> cache = new HashMap<>();

    public HytalePrefabThumbnailRenderer(File hytaleAssetsDir) {
        this.serverDir = (hytaleAssetsDir != null) ? new File(hytaleAssetsDir, "Server") : null;
    }

    public synchronized BufferedImage render(String relativePath, int size) {
        final String cacheKey = relativePath + "@" + size;
        BufferedImage cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        BufferedImage img = renderUncached(relativePath, size);
        cache.put(cacheKey, img);
        return img;
    }

    private BufferedImage renderUncached(String relativePath, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        TopColumn[][] grid = loadTopColumns(relativePath);
        if (grid == null) {
            return img; // transparent placeholder for missing/invalid prefab
        }
        int w = grid.length, d = (w > 0) ? grid[0].length : 0;
        if ((w == 0) || (d == 0)) {
            return img;
        }
        // scale prefab footprint into the square thumbnail, preserving aspect
        double scale = Math.min((double) size / w, (double) size / d);
        int drawW = Math.max(1, (int) Math.round(w * scale));
        int drawD = Math.max(1, (int) Math.round(d * scale));
        int offX = (size - drawW) / 2, offY = (size - drawD) / 2;
        for (int px = 0; px < drawW; px++) {
            for (int pz = 0; pz < drawD; pz++) {
                int gx = (int) (px / scale), gz = (int) (pz / scale);
                if ((gx >= w) || (gz >= d)) {
                    continue;
                }
                TopColumn c = grid[gx][gz];
                if (c == null) {
                    continue;
                }
                img.setRGB(offX + px, offY + pz, c.argb);
            }
        }
        return img;
    }

    private TopColumn[][] loadTopColumns(String relativePath) {
        if (serverDir == null) {
            return null;
        }
        File file = new File(serverDir, relativePath.replace('/', File.separatorChar));
        if (!file.isFile()) {
            return null;
        }
        try (Reader r = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            JsonElement root = JsonParser.parseReader(r);
            if ((root == null) || !root.isJsonObject()) {
                return null;
            }
            JsonElement blocksEl = root.getAsJsonObject().get("blocks");
            if ((blocksEl == null) || !blocksEl.isJsonArray()) {
                return null;
            }
            JsonArray blocks = blocksEl.getAsJsonArray();
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE,
                    minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
            for (JsonElement e : blocks) {
                JsonObject b = e.getAsJsonObject();
                if (isEmpty(b)) {
                    continue;
                }
                int x = b.get("x").getAsInt(), y = b.get("y").getAsInt(), z = b.get("z").getAsInt();
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
            }
            if (minX > maxX) {
                return null;
            }
            int w = (maxX - minX) + 1, d = (maxZ - minZ) + 1;
            TopColumn[][] grid = new TopColumn[w][d];
            for (JsonElement e : blocks) {
                JsonObject b = e.getAsJsonObject();
                if (isEmpty(b)) {
                    continue;
                }
                int x = b.get("x").getAsInt() - minX, z = b.get("z").getAsInt() - minZ, y = b.get("y").getAsInt();
                String name = b.get("name").getAsString().trim();
                TopColumn cur = grid[x][z];
                if ((cur == null) || (y > cur.topY)) {
                    grid[x][z] = new TopColumn(y, shade(colorFor(name), y, minY, maxY));
                }
            }
            return grid;
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    private static boolean isEmpty(JsonObject b) {
        JsonElement n = b.get("name");
        if ((n == null) || !n.isJsonPrimitive()) {
            return true;
        }
        String s = n.getAsString().trim();
        return s.isEmpty() || s.equals("Empty") || s.equals("Editor_Empty");
    }

    /**
     * Deterministic block→colour. VERIFY-FIRST: if a Hytale block/terrain colour
     * source exists (used elsewhere for previews), call it here instead of this
     * hash fallback.
     */
    private static int colorFor(String blockName) {
        int h = blockName.hashCode();
        int r = 80 + (((h >> 16) & 0x7F));
        int g = 80 + (((h >> 8) & 0x7F));
        int b = 80 + ((h & 0x7F));
        return new Color(r, g, b).getRGB();
    }

    private static int shade(int argb, int y, int minY, int maxY) {
        double t = (maxY > minY) ? ((double) (y - minY) / (maxY - minY)) : 1.0;
        double f = 0.6 + (0.4 * t); // lower = darker
        Color c = new Color(argb);
        int r = clamp((int) (c.getRed() * f)), g = clamp((int) (c.getGreen() * f)), b = clamp((int) (c.getBlue() * f));
        return new Color(r, g, b, 255).getRGB();
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static final class TopColumn {
        final int topY;
        final int argb;

        TopColumn(int topY, int argb) {
            this.topY = topY;
            this.argb = argb;
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -f WorldPainter/pom.xml -pl WPGUI test -Dtest=HytalePrefabThumbnailRendererTest -q`
Expected: PASS, 3 tests, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/hytale/prefab/HytalePrefabThumbnailRenderer.java \
        WorldPainter/WPGUI/src/test/java/org/pepsoft/worldpainter/hytale/prefab/HytalePrefabThumbnailRendererTest.java
git commit -m "feat(hytale): top-down prefab thumbnail renderer with cache"
```

---

## Task 9: `PrefabPaletteModel` — unified prefab list (built-in + discovered, searchable)

**Files:**
- Create: `WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/hytale/prefab/PrefabPaletteModel.java`
- Test: `WorldPainter/WPGUI/src/test/java/org/pepsoft/worldpainter/hytale/prefab/PrefabPaletteModelTest.java`

**Context:** built-ins come from `HytalePrefabLayer.getCategories()` + `getPrefabsInCategory(cat)` returning `PrefabEntry{category,name,path}`; user files from `HytalePrefabDiscovery.discoverPrefabs(File)` returning `PrefabFileEntry{getDisplayName,getCategory,getRelativePath,matchesSearch}`. Normalize both into one `PrefabItem{category, name, path}` and provide search + grouping for the palette.

- [ ] **Step 1: Write the failing test**

```java
package org.pepsoft.worldpainter.hytale.prefab;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class PrefabPaletteModelTest {
    @Test
    public void includesBuiltInsGroupedByCategory() {
        PrefabPaletteModel model = PrefabPaletteModel.builtInsOnly();
        assertFalse(model.getCategories().isEmpty());
        String cat = model.getCategories().get(0);
        List<PrefabPaletteModel.PrefabItem> items = model.itemsInCategory(cat);
        assertFalse(items.isEmpty());
        assertNotNull(items.get(0).path);
        assertEquals(cat, items.get(0).category);
    }

    @Test
    public void searchFiltersByNameCategoryOrPath() {
        PrefabPaletteModel model = PrefabPaletteModel.builtInsOnly();
        // empty query returns everything
        assertEquals(model.allItems().size(), model.search("").size());
        // a query that cannot match anything returns empty
        assertTrue(model.search("zzzz_no_such_prefab_zzzz").isEmpty());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -f WorldPainter/pom.xml -pl WPGUI test -Dtest=PrefabPaletteModelTest -q`
Expected: FAIL — class missing.

- [ ] **Step 3: Implement the model**

```java
package org.pepsoft.worldpainter.hytale.prefab;

import org.pepsoft.worldpainter.hytale.HytalePrefabLayer;
import org.pepsoft.worldpainter.hytale.PrefabFileEntry;

import java.io.File;
import java.util.*;

/** Unified, searchable catalogue of placeable Hytale prefabs for the palette. */
public final class PrefabPaletteModel {

    public static final class PrefabItem {
        public final String category;
        public final String name;
        public final String path; // relative path, e.g. Prefabs/Trees/Oak.prefab.json

        public PrefabItem(String category, String name, String path) {
            this.category = category;
            this.name = name;
            this.path = path;
        }
    }

    private final List<PrefabItem> items = new ArrayList<>();

    public static PrefabPaletteModel builtInsOnly() {
        PrefabPaletteModel m = new PrefabPaletteModel();
        m.addBuiltIns();
        return m;
    }

    public static PrefabPaletteModel withDiscovered(File hytaleAssetsDir) {
        PrefabPaletteModel m = builtInsOnly();
        if (hytaleAssetsDir != null) {
            for (PrefabFileEntry e : HytalePrefabDiscovery.discoverPrefabs(hytaleAssetsDir)) {
                m.items.add(new PrefabItem(e.getCategory(), e.getDisplayName(), e.getRelativePath()));
            }
        }
        return m;
    }

    private void addBuiltIns() {
        for (String cat : HytalePrefabLayer.getCategories()) {
            for (HytalePrefabLayer.PrefabEntry e : HytalePrefabLayer.getPrefabsInCategory(cat)) {
                items.add(new PrefabItem(e.category, e.name, e.path));
            }
        }
    }

    public List<PrefabItem> allItems() {
        return Collections.unmodifiableList(items);
    }

    public List<String> getCategories() {
        LinkedHashSet<String> cats = new LinkedHashSet<>();
        for (PrefabItem i : items) {
            cats.add(i.category);
        }
        return new ArrayList<>(cats);
    }

    public List<PrefabItem> itemsInCategory(String category) {
        List<PrefabItem> out = new ArrayList<>();
        for (PrefabItem i : items) {
            if (i.category.equals(category)) {
                out.add(i);
            }
        }
        return out;
    }

    public List<PrefabItem> search(String query) {
        if ((query == null) || query.trim().isEmpty()) {
            return allItems();
        }
        String q = query.toLowerCase();
        List<PrefabItem> out = new ArrayList<>();
        for (PrefabItem i : items) {
            if (i.name.toLowerCase().contains(q) || i.category.toLowerCase().contains(q)
                    || i.path.toLowerCase().contains(q)) {
                out.add(i);
            }
        }
        return out;
    }
}
```

> `HytalePrefabDiscovery` is in `org.pepsoft.worldpainter.hytale.prefab` (per Plan A's paster). If it lives in a different package, fix the import. `HytalePrefabLayer.PrefabEntry` fields are public (`category`,`name`,`path`).

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -f WorldPainter/pom.xml -pl WPGUI test -Dtest=PrefabPaletteModelTest -q`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/hytale/prefab/PrefabPaletteModel.java \
        WorldPainter/WPGUI/src/test/java/org/pepsoft/worldpainter/hytale/prefab/PrefabPaletteModelTest.java
git commit -m "feat(hytale): unified searchable prefab palette model"
```

---

## Task 10: `PrefabTransferable` — drag payload

**Files:**
- Create: `WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/hytale/prefab/PrefabTransferable.java`
- Test: `WorldPainter/WPGUI/src/test/java/org/pepsoft/worldpainter/hytale/prefab/PrefabTransferableTest.java`

**Context:** AWT DnD needs a `Transferable` with a custom local-JVM `DataFlavor` carrying `{path, name}`. This is headless-testable.

- [ ] **Step 1: Write the failing test**

```java
package org.pepsoft.worldpainter.hytale.prefab;

import org.junit.Test;
import static org.junit.Assert.*;

public class PrefabTransferableTest {
    @Test
    public void carriesPathAndName() throws Exception {
        PrefabTransferable t = new PrefabTransferable("Prefabs/Trees/Oak.prefab.json", "Oak");
        assertTrue(t.isDataFlavorSupported(PrefabTransferable.PREFAB_FLAVOR));
        Object data = t.getTransferData(PrefabTransferable.PREFAB_FLAVOR);
        assertTrue(data instanceof PrefabTransferable.Payload);
        PrefabTransferable.Payload p = (PrefabTransferable.Payload) data;
        assertEquals("Prefabs/Trees/Oak.prefab.json", p.path);
        assertEquals("Oak", p.name);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -f WorldPainter/pom.xml -pl WPGUI test -Dtest=PrefabTransferableTest -q`
Expected: FAIL — class missing.

- [ ] **Step 3: Implement**

```java
package org.pepsoft.worldpainter.hytale.prefab;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;

/** Drag payload for a prefab dragged from the palette onto the map. */
public final class PrefabTransferable implements Transferable {

    public static final DataFlavor PREFAB_FLAVOR =
            new DataFlavor(Payload.class, "Hytale prefab placement");

    public static final class Payload {
        public final String path;
        public final String name;

        public Payload(String path, String name) {
            this.path = path;
            this.name = name;
        }
    }

    private final Payload payload;

    public PrefabTransferable(String path, String name) {
        this.payload = new Payload(path, name);
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[]{PREFAB_FLAVOR};
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return PREFAB_FLAVOR.equals(flavor);
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
        if (!PREFAB_FLAVOR.equals(flavor)) {
            throw new UnsupportedFlavorException(flavor);
        }
        return payload;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -f WorldPainter/pom.xml -pl WPGUI test -Dtest=PrefabTransferableTest -q`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/hytale/prefab/PrefabTransferable.java \
        WorldPainter/WPGUI/src/test/java/org/pepsoft/worldpainter/hytale/prefab/PrefabTransferableTest.java
git commit -m "feat(hytale): PrefabTransferable drag payload"
```

---

## Task 11: `HytalePrefabPalette` dockable (thumbnail grid + search + drag source)

**Files:**
- Create: `WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/hytale/prefab/HytalePrefabPalette.java`

**Context:** A `JPanel` with a search `JTextField` and a `JList<PrefabItem>` in `HORIZONTAL_WRAP` layout (a thumbnail grid) using a custom `ListCellRenderer` that draws `HytalePrefabThumbnailRenderer.render(item.path, THUMB)` + name. A `DragGestureRecognizer` starts a drag emitting `new PrefabTransferable(item.path, item.name)`. Thumbnails render lazily off the EDT (a tiny worker that repaints the cell when ready) to avoid blocking the UI. This is GUI; acceptance is manual.

- [ ] **Step 1: Implement the palette panel**

```java
package org.pepsoft.worldpainter.hytale.prefab;

import javax.swing.*;
import java.awt.*;
import java.awt.dnd.*;
import java.io.File;
import java.util.List;

/** Dockable palette of placeable Hytale prefabs; drag a thumbnail onto the map. */
public final class HytalePrefabPalette extends JPanel {
    private static final int THUMB = 56;

    private final HytalePrefabThumbnailRenderer renderer;
    private final PrefabPaletteModel model;
    private final DefaultListModel<PrefabPaletteModel.PrefabItem> listModel = new DefaultListModel<>();
    private final JList<PrefabPaletteModel.PrefabItem> list = new JList<>(listModel);

    public HytalePrefabPalette(File hytaleAssetsDir) {
        super(new BorderLayout());
        this.renderer = new HytalePrefabThumbnailRenderer(hytaleAssetsDir);
        this.model = PrefabPaletteModel.withDiscovered(hytaleAssetsDir);

        JTextField search = new JTextField();
        search.getDocument().addDocumentListener(new SimpleDocListener(() -> refilter(search.getText())));
        add(search, BorderLayout.NORTH);

        list.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        list.setVisibleRowCount(-1);
        list.setFixedCellWidth(THUMB + 24);
        list.setFixedCellHeight(THUMB + 28);
        list.setCellRenderer(new ThumbCellRenderer(renderer, THUMB));
        add(new JScrollPane(list), BorderLayout.CENTER);

        DragSource.getDefaultDragSource().createDefaultDragGestureRecognizer(
                list, DnDConstants.ACTION_COPY, dge -> {
                    PrefabPaletteModel.PrefabItem item = list.getSelectedValue();
                    if (item != null) {
                        dge.startDrag(DragSource.DefaultCopyDrop,
                                new PrefabTransferable(item.path, item.name));
                    }
                });

        refilter("");
    }

    private void refilter(String query) {
        listModel.clear();
        List<PrefabPaletteModel.PrefabItem> items = model.search(query);
        for (PrefabPaletteModel.PrefabItem i : items) {
            listModel.addElement(i);
        }
    }

    /** Cell renderer drawing the prefab thumbnail + name. */
    private static final class ThumbCellRenderer extends JPanel
            implements ListCellRenderer<PrefabPaletteModel.PrefabItem> {
        private final HytalePrefabThumbnailRenderer renderer;
        private final int thumb;
        private final JLabel icon = new JLabel();
        private final JLabel label = new JLabel();

        ThumbCellRenderer(HytalePrefabThumbnailRenderer renderer, int thumb) {
            super(new BorderLayout());
            this.renderer = renderer;
            this.thumb = thumb;
            icon.setHorizontalAlignment(SwingConstants.CENTER);
            label.setHorizontalAlignment(SwingConstants.CENTER);
            label.setFont(label.getFont().deriveFont(10f));
            add(icon, BorderLayout.CENTER);
            add(label, BorderLayout.SOUTH);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends PrefabPaletteModel.PrefabItem> list,
                PrefabPaletteModel.PrefabItem value, int index, boolean selected, boolean focused) {
            icon.setIcon(new ImageIcon(renderer.render(value.path, thumb)));
            label.setText(value.name);
            setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            setOpaque(true);
            return this;
        }
    }

    /** Minimal DocumentListener adapter. */
    private static final class SimpleDocListener implements javax.swing.event.DocumentListener {
        private final Runnable onChange;
        SimpleDocListener(Runnable onChange) { this.onChange = onChange; }
        public void insertUpdate(javax.swing.event.DocumentEvent e) { onChange.run(); }
        public void removeUpdate(javax.swing.event.DocumentEvent e) { onChange.run(); }
        public void changedUpdate(javax.swing.event.DocumentEvent e) { onChange.run(); }
    }
}
```

> `HytalePrefabThumbnailRenderer.render` already caches, so calling it from the cell renderer is acceptable for a first version; if scrolling stutters on large catalogues, move rendering to a `SwingWorker` that fills the cache and repaints. The assets dir comes from `HytaleTerrain.getHytaleAssetsDir()` at the call site (Task 15).

- [ ] **Step 2: Build the GUI to confirm it compiles**

Run: `mvn -f WorldPainter/pom.xml -DskipTests=true -pl WPGUI -am install`
Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit**

```bash
git add WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/hytale/prefab/HytalePrefabPalette.java
git commit -m "feat(hytale): prefab palette panel with thumbnail grid + drag source"
```

(Visual acceptance happens in Task 15's manual test once the palette is docked.)

---

## Task 12: `PrefabPlacementOperation` — tool, canvas DropTarget, select/move/rotate

**Files:**
- Create: `WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/operations/PrefabPlacementOperation.java`

**Context:** Model on `TownPlanOperation` (`public TownPlanOperation(WorldPainter view){ super("Town Plan","…","townplan"); setView(view); }`, registered in `App.java` via `createButtonForOperation`). `MouseOrTabletOperation` provides `tick(int centreX,int centreY,boolean inverse,boolean first,float dynamicLevel)` with **world** coords. The operation:
- on `activate()`: builds/binds a `DropTarget` on the canvas component accepting `PrefabTransferable.PREFAB_FLAVOR`; on drop, converts the drop's **component pixel** to world coords and adds a placement.
- on map clicks (via `tick`): hit-tests placement footprints to select; drag = move; with a held modifier or near the rotate handle = rotate.
- holds a reference to the `Dimension`, the `HytalePrefabPlacementsPanel` (Task 14), and a selection (`long selectedId`), and after any mutation calls `view.repaint()` + `panel.refresh()`.

**Pixel→world for the drop:** `tick` gives world coords for clicks, but a `DropTargetDropEvent` gives a component `Point`. Use the **same** conversion the view/status-bar uses (the inverse of the transform applied in `WorldPainter.drawOverlays`, i.e. `TiledImageViewer` view↔world). **Verify-first:** find the view method that converts a component point to world coords (used by the status bar / `MouseMotionListener` in `WorldPainter`); call it. If only a `worldToView`/transform is exposed, invert it.

- [ ] **Step 1: Implement the operation (skeleton with real wiring)**

```java
package org.pepsoft.worldpainter.operations;

import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.WorldPainter;
import org.pepsoft.worldpainter.hytale.HytalePrefabPlacement;
import org.pepsoft.worldpainter.hytale.prefab.PrefabTransferable;

import java.awt.Point;
import java.awt.dnd.*;
import java.awt.geom.Point2D;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tool for authoring exact Hytale prefab placements: accepts prefab drops from the
 * palette, and selects/moves/rotates existing placements. One-shot tick (placement
 * happens on drop; clicks select/drag).
 */
public final class PrefabPlacementOperation extends MouseOrTabletOperation {
    private final WorldPainter view;
    private final AtomicLong idSeq = new AtomicLong(System.nanoTime());
    private DropTarget dropTarget;
    private long selectedId = -1;
    private Runnable selectionListener; // set by the panel to mirror selection

    public PrefabPlacementOperation(WorldPainter view) {
        super("Place Prefab", "Place a Hytale prefab at an exact location", "prefabPlacement");
        this.view = view;
        setView(view);
    }

    @Override
    protected void activate() throws java.beans.PropertyVetoException {
        super.activate();
        dropTarget = new DropTarget(view, DnDConstants.ACTION_COPY, new DropHandler(), true);
    }

    @Override
    protected void deactivate() throws java.beans.PropertyVetoException {
        if (dropTarget != null) {
            dropTarget.setActive(false);
            dropTarget = null;
        }
        super.deactivate();
    }

    @Override
    protected void tick(int centreX, int centreY, boolean inverse, boolean first, float dynamicLevel) {
        if (!first) {
            return; // movement handled by the move/rotate drag logic below
        }
        // Select the topmost placement whose footprint contains (centreX, centreY).
        HytalePrefabPlacement hit = hitTest(centreX, centreY);
        selectedId = (hit != null) ? hit.getId() : -1;
        notifySelection();
        view.repaint();
    }

    private HytalePrefabPlacement hitTest(int worldX, int worldY) {
        Dimension dim = view.getDimension();
        if (dim == null) {
            return null;
        }
        // Simple proximity hit-test; a footprint-rectangle test can refine this.
        HytalePrefabPlacement best = null;
        int bestDist = Integer.MAX_VALUE;
        for (HytalePrefabPlacement p : dim.getHytalePrefabPlacements()) {
            int dx = p.getX() - worldX, dy = p.getY() - worldY;
            int dist = (dx * dx) + (dy * dy);
            if (dist < bestDist && dist <= 64) { // within ~8 blocks
                best = p;
                bestDist = dist;
            }
        }
        return best;
    }

    public long getSelectedId() {
        return selectedId;
    }

    public void setSelectedId(long id) {
        this.selectedId = id;
        view.repaint();
    }

    public void setSelectionListener(Runnable listener) {
        this.selectionListener = listener;
    }

    private void notifySelection() {
        if (selectionListener != null) {
            selectionListener.run();
        }
    }

    private void addPlacementAt(int worldX, int worldY, PrefabTransferable.Payload payload) {
        Dimension dim = view.getDimension();
        if (dim == null) {
            return;
        }
        HytalePrefabPlacement placement = new HytalePrefabPlacement(
                idSeq.incrementAndGet(), payload.path, payload.name,
                worldX, worldY, null, true, 0.0);
        dim.addHytalePrefabPlacement(placement);
        selectedId = placement.getId();
        notifySelection();
        view.repaint();
    }

    /** Convert a component pixel point to world (x, y). VERIFY-FIRST: use the view's
     *  own conversion (the one the status bar uses). Replace the body accordingly. */
    private Point worldFromComponentPoint(Point componentPoint) {
        Point2D worldF = view.viewToWorld(componentPoint); // <-- confirm method name on WorldPainter/TiledImageViewer
        return new Point((int) Math.floor(worldF.getX()), (int) Math.floor(worldF.getY()));
    }

    private final class DropHandler extends DropTargetAdapter {
        @Override
        public void drop(DropTargetDropEvent dtde) {
            try {
                if (!dtde.isDataFlavorSupported(PrefabTransferable.PREFAB_FLAVOR)) {
                    dtde.rejectDrop();
                    return;
                }
                dtde.acceptDrop(DnDConstants.ACTION_COPY);
                PrefabTransferable.Payload payload = (PrefabTransferable.Payload)
                        dtde.getTransferable().getTransferData(PrefabTransferable.PREFAB_FLAVOR);
                Point world = worldFromComponentPoint(dtde.getLocation());
                addPlacementAt(world.x, world.y, payload);
                dtde.dropComplete(true);
            } catch (Exception e) {
                dtde.dropComplete(false);
            }
        }
    }
}
```

> **Verify-first items in this task:** (a) `MouseOrTabletOperation` constructor `("name","desc","statsKey")` and `tick(...)` signature (confirmed in Plan A research); (b) the view's component-pixel→world method (`viewToWorld` is a placeholder — replace with the real method on `WorldPainter`/`TiledImageViewer`); (c) move/rotate drag gestures: a first pass can ship **select + drop-create + delete + (numeric move/rotate via the panel)**, with on-map drag-move/rotate added once selection works. Keep that reduced first pass if drag math risks blocking progress — numeric editing in Task 14 still gives full precision.

- [ ] **Step 2: Build to confirm compilation** (after fixing the `viewToWorld` call to the real method)

Run: `mvn -f WorldPainter/pom.xml -DskipTests=true -pl WPGUI -am install`
Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit**

```bash
git add WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/operations/PrefabPlacementOperation.java
git commit -m "feat(hytale): prefab placement operation with canvas drop target + selection"
```

---

## Task 13: Draw placements on the map (`WorldPainter` paint hook)

**Files:**
- Modify: `WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/WorldPainter.java`

**Context:** `drawOverlays(Graphics2D g2)` (lines ~1176-1235) is called during paint and iterates `dimension.getOverlays()` applying view↔world transforms. Add a sibling `drawPrefabPlacements(Graphics2D g2)` invoked from the same paint method, iterating `dimension.getHytalePrefabPlacements()` and drawing each as a small marker + label (and a highlight for the selected one). Use the **same** world→view transform the overlay code uses so markers track zoom/scroll.

- [ ] **Step 1: Add the draw method and call it.** Near `drawOverlays`, add:

```java
private void drawPrefabPlacements(Graphics2D g2) {
    if ((dimension == null) || dimension.getHytalePrefabPlacements().isEmpty()) {
        return;
    }
    final long selectedId = (prefabPlacementSelectionId != null) ? prefabPlacementSelectionId : -1L;
    final Color marker = new Color(255, 220, 0);
    final Color selected = new Color(255, 80, 80);
    for (org.pepsoft.worldpainter.hytale.HytalePrefabPlacement p : dimension.getHytalePrefabPlacements()) {
        // world (x, y) -> view pixel using the same conversion as overlays / status bar
        Point viewPt = worldToViewPoint(p.getX(), p.getY());   // <-- confirm/inverse of the overlay transform
        if (viewPt == null) {
            continue;
        }
        boolean isSel = (p.getId() == selectedId);
        g2.setColor(isSel ? selected : marker);
        int s = 6;
        g2.fillOval(viewPt.x - s, viewPt.y - s, s * 2, s * 2);
        g2.drawString(p.getPrefabName(), viewPt.x + 8, viewPt.y);
        if (isSel) {
            g2.drawOval(viewPt.x - 12, viewPt.y - 12, 24, 24); // selection ring; rotate-handle hint
        }
    }
}
```

Add the field and the call:
```java
private Long prefabPlacementSelectionId; // set by PrefabPlacementOperation to highlight selection
```
In the paint method body where `drawOverlays(g2)` is invoked, add right after it:
```java
drawPrefabPlacements(g2);
```
Add a setter the operation can call to drive the highlight:
```java
public void setPrefabPlacementSelectionId(Long id) {
    this.prefabPlacementSelectionId = id;
    repaint();
}
```

> **Verify-first:** `worldToViewPoint(int worldX, int worldY)` is a placeholder for the view's existing world→component conversion. Find the method `drawOverlays` (or the status-bar/`MouseMotionListener`) uses and call it; if only a `Graphics2D` transform is applied (no point helper), apply the same transform to compute the pixel, or draw in world space within the same transformed `g2`. Wire `PrefabPlacementOperation` to call `view.setPrefabPlacementSelectionId(selectedId)` wherever it currently calls `view.repaint()` (update Task 12's `notifySelection`/`addPlacementAt`/`tick`).

- [ ] **Step 2: Build to confirm compilation**

Run: `mvn -f WorldPainter/pom.xml -DskipTests=true -pl WPGUI -am install`
Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit**

```bash
git add WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/WorldPainter.java \
        WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/operations/PrefabPlacementOperation.java
git commit -m "feat(hytale): draw exact prefab placements + selection on the map"
```

---

## Task 14: `HytalePrefabPlacementsPanel` — master list + numeric edit fields

**Files:**
- Create: `WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/hytale/prefab/HytalePrefabPlacementsPanel.java`

**Context:** A dockable panel with a `JList` of placements (name + `(x,y)`), and an edit form (`X`, `Y`, `height` spinners, `snap-to-surface` checkbox, `rotation` degrees spinner) for the selected one, plus Delete / Duplicate. Edits call `Dimension.replaceHytalePrefabPlacement(old, old.withX(...))` and then `operation`-driven `view.repaint()`. Selection is two-way synced with `PrefabPlacementOperation`. GUI; acceptance is manual.

- [ ] **Step 1: Implement the panel**

```java
package org.pepsoft.worldpainter.hytale.prefab;

import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.WorldPainter;
import org.pepsoft.worldpainter.hytale.HytalePrefabPlacement;
import org.pepsoft.worldpainter.operations.PrefabPlacementOperation;

import javax.swing.*;
import java.awt.*;

/** Dockable master list + numeric editor for exact prefab placements. */
public final class HytalePrefabPlacementsPanel extends JPanel {
    private final WorldPainter view;
    private final PrefabPlacementOperation operation;

    private final DefaultListModel<HytalePrefabPlacement> listModel = new DefaultListModel<>();
    private final JList<HytalePrefabPlacement> list = new JList<>(listModel);
    private final JSpinner xSpin = new JSpinner(new SpinnerNumberModel(0, -30_000_000, 30_000_000, 1));
    private final JSpinner ySpin = new JSpinner(new SpinnerNumberModel(0, -30_000_000, 30_000_000, 1));
    private final JSpinner heightSpin = new JSpinner(new SpinnerNumberModel(64, -512, 4096, 1));
    private final JCheckBox snapBox = new JCheckBox("Snap to surface");
    private final JSpinner rotSpin = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 359.999, 1.0));

    private boolean syncing; // guard to avoid feedback loops while populating fields

    public HytalePrefabPlacementsPanel(WorldPainter view, PrefabPlacementOperation operation) {
        super(new BorderLayout());
        this.view = view;
        this.operation = operation;

        list.setCellRenderer(new PlacementCellRenderer());
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !syncing) {
                HytalePrefabPlacement sel = list.getSelectedValue();
                operation.setSelectedId(sel != null ? sel.getId() : -1);
                populateForm(sel);
            }
        });
        add(new JScrollPane(list), BorderLayout.CENTER);
        add(buildForm(), BorderLayout.SOUTH);

        // mirror operation -> panel selection
        operation.setSelectionListener(() -> SwingUtilities.invokeLater(this::syncFromOperation));

        refresh();
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new GridLayout(0, 2, 4, 2));
        form.add(new JLabel("X (E-W):")); form.add(xSpin);
        form.add(new JLabel("Y (N-S):")); form.add(ySpin);
        form.add(new JLabel("Height:")); form.add(heightSpin);
        form.add(new JLabel("")); form.add(snapBox);
        form.add(new JLabel("Rotation (°):")); form.add(rotSpin);

        javax.swing.event.ChangeListener onEdit = e -> { if (!syncing) { applyForm(); } };
        xSpin.addChangeListener(onEdit);
        ySpin.addChangeListener(onEdit);
        heightSpin.addChangeListener(onEdit);
        rotSpin.addChangeListener(onEdit);
        snapBox.addActionListener(e -> { if (!syncing) { applyForm(); } });

        JButton delete = new JButton("Delete");
        delete.addActionListener(e -> deleteSelected());
        JButton duplicate = new JButton("Duplicate");
        duplicate.addActionListener(e -> duplicateSelected());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(delete);
        buttons.add(duplicate);

        JPanel south = new JPanel(new BorderLayout());
        south.add(form, BorderLayout.CENTER);
        south.add(buttons, BorderLayout.SOUTH);
        return south;
    }

    /** Rebuild the list from the dimension (call after any external change). */
    public void refresh() {
        Dimension dim = view.getDimension();
        long sel = operation.getSelectedId();
        syncing = true;
        try {
            listModel.clear();
            if (dim != null) {
                for (HytalePrefabPlacement p : dim.getHytalePrefabPlacements()) {
                    listModel.addElement(p);
                    if (p.getId() == sel) {
                        list.setSelectedValue(p, true);
                    }
                }
            }
        } finally {
            syncing = false;
        }
        populateForm(currentSelection());
    }

    private void syncFromOperation() {
        refresh();
    }

    private HytalePrefabPlacement currentSelection() {
        Dimension dim = view.getDimension();
        if (dim == null) {
            return null;
        }
        for (HytalePrefabPlacement p : dim.getHytalePrefabPlacements()) {
            if (p.getId() == operation.getSelectedId()) {
                return p;
            }
        }
        return null;
    }

    private void populateForm(HytalePrefabPlacement p) {
        syncing = true;
        try {
            boolean enabled = (p != null);
            xSpin.setEnabled(enabled);
            ySpin.setEnabled(enabled);
            heightSpin.setEnabled(enabled && !p.isSnapToSurface());
            snapBox.setEnabled(enabled);
            rotSpin.setEnabled(enabled);
            if (p != null) {
                xSpin.setValue(p.getX());
                ySpin.setValue(p.getY());
                heightSpin.setValue(p.getHeight() != null ? p.getHeight() : 64);
                snapBox.setSelected(p.isSnapToSurface());
                rotSpin.setValue(p.getRotationDegrees());
            }
        } finally {
            syncing = false;
        }
    }

    private void applyForm() {
        HytalePrefabPlacement old = currentSelection();
        if (old == null) {
            return;
        }
        boolean snap = snapBox.isSelected();
        Integer height = snap ? null : ((Number) heightSpin.getValue()).intValue();
        HytalePrefabPlacement updated = old
                .withPosition(((Number) xSpin.getValue()).intValue(), ((Number) ySpin.getValue()).intValue())
                .withHeight(height, snap)
                .withRotation(((Number) rotSpin.getValue()).doubleValue());
        view.getDimension().replaceHytalePrefabPlacement(old, updated);
        view.setPrefabPlacementSelectionId(updated.getId());
        heightSpin.setEnabled(!snap);
        refresh();
    }

    private void deleteSelected() {
        HytalePrefabPlacement old = currentSelection();
        if (old == null) {
            return;
        }
        view.getDimension().removeHytalePrefabPlacement(old);
        operation.setSelectedId(-1);
        view.repaint();
        refresh();
    }

    private void duplicateSelected() {
        HytalePrefabPlacement old = currentSelection();
        if (old == null) {
            return;
        }
        HytalePrefabPlacement copy = new HytalePrefabPlacement(
                System.nanoTime(), old.getPrefabPath(), old.getPrefabName(),
                old.getX() + 4, old.getY() + 4, old.getHeight(), old.isSnapToSurface(), old.getRotationDegrees());
        view.getDimension().addHytalePrefabPlacement(copy);
        operation.setSelectedId(copy.getId());
        view.repaint();
        refresh();
    }

    private static final class PlacementCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                boolean selected, boolean focused) {
            super.getListCellRendererComponent(list, value, index, selected, focused);
            if (value instanceof HytalePrefabPlacement) {
                HytalePrefabPlacement p = (HytalePrefabPlacement) value;
                setText(p.getPrefabName() + "  (" + p.getX() + ", " + p.getY() + ")");
            }
            return this;
        }
    }
}
```

- [ ] **Step 2: Build to confirm compilation**

Run: `mvn -f WorldPainter/pom.xml -DskipTests=true -pl WPGUI -am install`
Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit**

```bash
git add WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/hytale/prefab/HytalePrefabPlacementsPanel.java
git commit -m "feat(hytale): placements master list + numeric editor panel"
```

---

## Task 15: Wire into `App` (tool button + docked panels, gated to Hytale) + manual acceptance

**Files:**
- Modify: `WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/App.java`

**Context:** Tools are added in `App.java` (~line 3526) via `toolPanel.add(createButtonForOperation(new …Operation(view), 'x'));`. The Hytale gate is `DefaultPlugin.HYTALE.equals(dimension.getWorld().getPlatform())` (id `"org.pepsoft.hytale"`). The assets dir is `HytaleTerrain.getHytaleAssetsDir()`. Dock the palette and placements panel using the same JIDE docking mechanism the app already uses for its tool/option panels (find an existing `DockableFrame`/dock registration in `App.java` and mirror it).

- [ ] **Step 1: Instantiate the operation + panels and add the tool button.** Near the other `createButtonForOperation` calls:

```java
final org.pepsoft.worldpainter.operations.PrefabPlacementOperation prefabPlacementOp =
        new org.pepsoft.worldpainter.operations.PrefabPlacementOperation(view);
toolPanel.add(createButtonForOperation(prefabPlacementOp, 'k'));
```

- [ ] **Step 2: Create and dock the palette + placements panels.** Where the app builds its dockable frames, add (mirroring an existing dock registration):

```java
final java.io.File hytaleAssets = org.pepsoft.worldpainter.hytale.HytaleTerrain.getHytaleAssetsDir();
final org.pepsoft.worldpainter.hytale.prefab.HytalePrefabPalette prefabPalette =
        new org.pepsoft.worldpainter.hytale.prefab.HytalePrefabPalette(hytaleAssets);
final org.pepsoft.worldpainter.hytale.prefab.HytalePrefabPlacementsPanel placementsPanel =
        new org.pepsoft.worldpainter.hytale.prefab.HytalePrefabPlacementsPanel(view, prefabPlacementOp);
// register both as DockableFrames using the same API as the existing tool panels
```

- [ ] **Step 3: Gate visibility to Hytale.** Add a helper and apply it when the world/platform loads (mirror how other platform-specific UI is shown/hidden):

```java
private boolean isHytale() {
    return (dimension != null) && org.pepsoft.worldpainter.DefaultPlugin.HYTALE.equals(dimension.getWorld().getPlatform());
}
```
Show/enable the tool button + both docked panels only when `isHytale()` is true; hide/disable otherwise. Refresh `placementsPanel` (`placementsPanel.refresh()`) when a world is opened so existing placements appear.

> **Verify-first:** the exact JIDE docking registration call and the place where platform-specific UI is toggled — find an existing example in `App.java` (e.g. how a Hytale-only or Minecraft-only panel is shown) and mirror it precisely. Tool mnemonic `'k'` is a suggestion; pick any unused one.

- [ ] **Step 4: Build the full app**

Run: `mvn -f WorldPainter/pom.xml -DskipTests=true -pl WPGUI -am install`
Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Manual acceptance — run the app and verify the whole feature**

Run: `mvn -f WorldPainter/pom.xml -pl WPGUI exec:exec`

Then verify:
- [ ] On a **Minecraft** world: the Place-Prefab tool button and the palette/placements panels are **hidden/disabled**.
- [ ] Create/open a **Hytale** world: the tool, palette, and placements panel appear; palette shows thumbnails grouped/searchable.
- [ ] Activate the tool, **drag** a prefab thumbnail onto the map → a marker appears at the drop point; it's auto-selected; the placements panel shows it with `(x, y)` and editable fields; height defaults to snap-to-surface.
- [ ] Type new **X/Y/height/rotation** in the panel → marker moves/updates on the map immediately.
- [ ] **Click** a marker on the map → it selects in the panel (two-way sync). **Delete** removes it; **Duplicate** adds an offset copy.
- [ ] **Save** the world, close, reopen → placements persist and redraw.
- [ ] **Export** the Hytale world → the prefab's blocks appear baked at the exact location; non-cardinal rotation looks rotated (resampled); a deliberately-missing prefab path leaves a marker (check logs).
- [ ] **Rotation direction check:** place a prefab with a clearly directional feature (branches/stairs), set rotation 90° → confirm the feature points consistently with the rotated footprint. If it points wrong, apply the one-line fix from Plan A's `PrefabRotator.rotateCardinal` (flip `steps` to `((4 - steps) % 4)`), rebuild, re-verify.

- [ ] **Step 6: Commit**

```bash
git add WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/App.java
git commit -m "feat(hytale): wire prefab placement tool + palette + panel into App (Hytale-gated)"
```

---

## Plan B self-review checklist

- [ ] Spec coverage: thumbnail renderer (T8), palette model (T9), transferable (T10), palette panel + drag source (T11), operation + canvas DropTarget + selection (T12), on-map drawing (T13), placements panel + numeric edit (T14), App wiring + Hytale gate + manual acceptance (T15). ✔
- [ ] Headless-testable logic (T8/T9/T10) has real TDD tests; GUI tasks have explicit manual acceptance (T15 Step 5).
- [ ] `verify-first` notes (view pixel↔world method name, JIDE dock registration, block→colour source, platform-toggle location) are concrete lookups against named existing code — resolve each by reading the cited class before writing the line.
- [ ] Reduced-scope fallback noted (T12): if on-map drag-move/rotate math risks stalling, ship select + drop-create + delete + numeric move/rotate (Task 14) first; on-map drag is an enhancement.

## Finishing

When both plans are complete and the manual acceptance passes, use the **superpowers:finishing-a-development-branch** skill to decide merge/PR/cleanup for `worktree-hytale-prefab-exact-placement`. Consider opening a `TP` YouTrack issue (set to **Review** on completion, per project workflow) to track the feature if one is wanted.
