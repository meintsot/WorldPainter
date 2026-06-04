# Town Plan Export-Block Picker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the user pick which block the Town Layout footprint exports as (default stone, overridable per world via a picker in the Town Plan tool panel).

**Architecture:** Change the `TownLayoutSettings` default block to stone (WPCore, unit-tested), then add an "Export block" button to `TownPlanOperation`'s options panel that opens the existing `MaterialSelector` and stores the choice in the dimension's `TownLayout` layer settings, which `TownLayoutExporter` already reads at export. No exporter changes.

**Tech Stack:** Java 17, Maven, JUnit 4, Swing (`MaterialSelector`, `JOptionPane`), `org.pepsoft.minecraft.Material`.

**Build/test commands** (from `C:/Users/Sotirios/Desktop/WorldPainter/WorldPainter`):
- WPCore test: `mvn -pl WPCore -am test -Dtest=<Class>`
- Compile WPGUI: `mvn -DskipTests=true -pl WPGUI -am install` (needs JIDE eval jars; already installed on this machine)
- Run the app **on JDK 17**: prepend `C:\Program Files\Microsoft\jdk-17.0.15.6-hotspot\bin` to PATH, then `mvn -pl WPGUI exec:exec` (plain `exec:exec` uses JDK 25 and crashes on JIDE).

**Verification note:** Task 1 is unit-tested (WPCore). Task 2 is Swing glue, build- and run-verified (the picker dialog can't be meaningfully unit-tested).

---

## File Structure

Paths under `C:/Users/Sotirios/Desktop/WorldPainter/WorldPainter/`.

**Modify:**
- `WPCore/src/main/java/org/pepsoft/worldpainter/layers/exporters/TownLayoutSettings.java` — default block → stone.
- `WPCore/src/test/java/org/pepsoft/worldpainter/layers/exporters/TownLayoutSettingsTest.java` — assert the stone default.
- `WPGUI/src/main/java/org/pepsoft/worldpainter/operations/TownPlanOperation.java` — "Export block" button + chooser dialog + storage.

---

## Task 1: Default export block → stone (WPCore)

**Files:**
- Modify: `WPCore/src/main/java/org/pepsoft/worldpainter/layers/exporters/TownLayoutSettings.java`
- Test: `WPCore/src/test/java/org/pepsoft/worldpainter/layers/exporters/TownLayoutSettingsTest.java`

`TownLayoutSettings` currently initializes `block = Material.get(BLK_WOOL, 15)` (black wool). Change it to `Material.STONE`. The exporter and integration tests set the block explicitly via `setBlock(...)`, so they are unaffected.

- [ ] **Step 1: Write the failing test**

In `WPCore/src/test/java/org/pepsoft/worldpainter/layers/exporters/TownLayoutSettingsTest.java`, add this test method (the class already imports `org.pepsoft.minecraft.Material` and `org.junit.Test`):

```java
    @Test
    public void defaultBlockIsStone() {
        assertEquals(Material.STONE, new TownLayoutSettings().getBlock());
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl WPCore -am test -Dtest=TownLayoutSettingsTest`
Expected: FAIL — `defaultBlockIsStone` fails because the current default is black wool, not stone.

- [ ] **Step 3: Change the default block to stone**

In `TownLayoutSettings.java`, change the field initializer. It currently reads:

```java
    private Material block = Material.get(BLK_WOOL, 15); // black wool
```

Change it to:

```java
    private Material block = Material.STONE;
```

Then remove the now-unused `import static org.pepsoft.minecraft.Constants.BLK_WOOL;` if (and only if) `BLK_WOOL` is no longer referenced anywhere else in the file (it should not be). `Material` is already imported.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl WPCore -am test -Dtest=TownLayoutSettingsTest`
Expected: PASS (all tests, including `defaultBlockIsStone`).

- [ ] **Step 5: Run the exporter + integration tests to confirm no regression**

Run: `mvn -pl WPCore -am test -Dtest=TownLayoutExporterTest,TownPlanStampIntegrationTest`
Expected: PASS — these set the block explicitly, so the default change does not affect them.

- [ ] **Step 6: Commit**

```bash
git add WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/layers/exporters/TownLayoutSettings.java WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/layers/exporters/TownLayoutSettingsTest.java
git commit -m "feat(town-plan): default export block is now stone"
```
End the commit message with a blank line then:
`Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`

---

## Task 2: Export-block picker in the Town Plan tool panel (WPGUI)

**Files:**
- Modify: `WPGUI/src/main/java/org/pepsoft/worldpainter/operations/TownPlanOperation.java`

Add an "Export block: `<name>`" button to the options panel that opens the existing `MaterialSelector` (it shows both Minecraft and Hytale blocks, with search) and stores the chosen block in the dimension's `TownLayout` layer settings — which `TownLayoutExporter` already reads at export. Build- and run-verified.

> **Implementer — confirm these against the real API before/while coding (expected to match; adapt and note if not):**
> 1. `dimension.getLayerSettings(TownLayout.INSTANCE)` returns `ExporterSettings` (cast to `TownLayoutSettings`); `dimension.setLayerSettings(TownLayout.INSTANCE, settings)` stores it. (Used by `DimensionPropertiesEditor` for `Annotations`.)
> 2. The world's platform for the picker: `dimension.getWorld().getPlatform()` (search `getPlatform` on `World2`). `MaterialSelector.setPlatform(Platform)` exists (used in `CustomMaterialDialog`).
> 3. `MaterialSelector` no-arg constructor, `setMaterial(Material)`, `getMaterial()` (confirmed at MaterialSelector.java:49/95/128). If `MaterialSelector` requires `setExtendedBlockIds(boolean)` to render (as in `CustomMaterialDialog`), call `selector.setExtendedBlockIds(false)`.
> 4. A readable label for a `Material` — use `String.valueOf(block)`; if that is verbose, use the material's simple-name accessor.

- [ ] **Step 1: Add imports**

In `TownPlanOperation.java`, ensure these imports are present (add any missing):

```java
import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.MaterialSelector;
import org.pepsoft.worldpainter.Platform;
import org.pepsoft.worldpainter.layers.exporters.TownLayoutSettings;
```

- [ ] **Step 2: Add the "Export block" button to `buildOptionsPanel()`**

`buildOptionsPanel()` currently builds: Select image, threshold, invert, a Rotation label + `rotatePanel`, and Stamp. Add the block button between the rotation panel and Stamp. Locate this block:

```java
        panel.add(invertCheckBox);
        panel.add(new JLabel("Rotation (free-drag the knob; buttons snap to 90°)"));
        panel.add(rotatePanel);
        panel.add(stampButton);
        return panel;
    }
```

and change it to (creates the field-backed `blockButton`, wires it, and inserts it before Stamp):

```java
        panel.add(invertCheckBox);
        panel.add(new JLabel("Rotation (free-drag the knob; buttons snap to 90°)"));
        panel.add(rotatePanel);
        blockButton = new JButton();
        blockButton.setToolTipText("Choose the block the footprint exports as");
        blockButton.addActionListener(e -> chooseBlock());
        refreshBlockButtonLabel();
        panel.add(blockButton);
        panel.add(stampButton);
        return panel;
    }
```

- [ ] **Step 3: Add the `chooseBlock`, settings-helper, and label methods**

Add these methods to the class (e.g. directly after `buildOptionsPanel()`):

```java
    private void chooseBlock() {
        final WorldPainter view = (WorldPainter) getView();
        final Dimension dimension = getDimension();
        if (dimension == null) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        final MaterialSelector selector = new MaterialSelector();
        final Platform platform = dimension.getWorld().getPlatform();
        if (platform != null) {
            selector.setPlatform(platform);
        }
        selector.setMaterial(currentExportBlock(dimension));
        final int result = JOptionPane.showConfirmDialog(view, selector, "Select export block",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            final Material chosen = selector.getMaterial();
            if (chosen != null) {
                final TownLayoutSettings settings = currentOrNewSettings(dimension);
                settings.setBlock(chosen);
                dimension.setLayerSettings(TownLayout.INSTANCE, settings);
                refreshBlockButtonLabel();
            }
        }
    }

    /** The export block currently configured for the dimension, or the (stone) default if none is stored. */
    private Material currentExportBlock(Dimension dimension) {
        final TownLayoutSettings settings = (dimension != null)
                ? (TownLayoutSettings) dimension.getLayerSettings(TownLayout.INSTANCE) : null;
        return (settings != null) ? settings.getBlock() : new TownLayoutSettings().getBlock();
    }

    /** The dimension's existing TownLayout settings, or a fresh default instance if none is stored. */
    private TownLayoutSettings currentOrNewSettings(Dimension dimension) {
        final TownLayoutSettings settings = (TownLayoutSettings) dimension.getLayerSettings(TownLayout.INSTANCE);
        return (settings != null) ? settings : new TownLayoutSettings();
    }

    private void refreshBlockButtonLabel() {
        if (blockButton == null) {
            return;
        }
        final Material block = currentExportBlock(getDimension());
        blockButton.setText("Export block: " + String.valueOf(block));
    }
```

- [ ] **Step 4: Refresh the label when the tool is activated**

So the button shows the right block when the user switches worlds, add a `refreshBlockButtonLabel()` call at the end of `activate()`. Locate `activate()`:

```java
    @Override
    protected void activate() throws PropertyVetoException {
        final WorldPainter view = (WorldPainter) getView();
        view.addMouseListener(mouseHandler);
        view.addMouseMotionListener(mouseHandler);
        if (overlay != null) {
            // Re-show the placement image (it is hidden while another tool is active).
            overlay.setEnabled(true);
            view.setPlacementOverlay(overlay);
            view.repaint();
        }
    }
```

and add the refresh call just before the closing brace:

```java
    @Override
    protected void activate() throws PropertyVetoException {
        final WorldPainter view = (WorldPainter) getView();
        view.addMouseListener(mouseHandler);
        view.addMouseMotionListener(mouseHandler);
        if (overlay != null) {
            // Re-show the placement image (it is hidden while another tool is active).
            overlay.setEnabled(true);
            view.setPlacementOverlay(overlay);
            view.repaint();
        }
        refreshBlockButtonLabel();
    }
```

- [ ] **Step 5: Add the `blockButton` field**

In the field declarations at the bottom of the class (next to `private JSlider thresholdSlider;` etc.), add:

```java
    private JButton blockButton;
```

- [ ] **Step 6: Compile WPGUI**

Run: `mvn -DskipTests=true -pl WPGUI -am install`
Expected: BUILD SUCCESS. Resolve any signature mismatches using the implementer notes above (real names for `getWorld().getPlatform()`, `setExtendedBlockIds`, the material label) while keeping behavior identical.

- [ ] **Step 7: Manual verification (run the app on JDK 17)**

```powershell
$env:PATH = "C:\Program Files\Microsoft\jdk-17.0.15.6-hotspot\bin;$env:PATH"
mvn -pl WPGUI exec:exec
```
1. Open a world; click the **Town Plan** tool. The options panel shows **Export block: …** (default reads "stone"-ish).
2. Click it → the `MaterialSelector` dialog opens with the current block selected; pick a different block (try the search; for a Hytale world, a Hytale block) → **OK**. The button label updates.
3. Stamp a footprint and **export** the world; confirm the exported marker blocks are the chosen block (not stone). Re-open the world / re-activate the tool and confirm the choice persisted (it's saved in the layer settings).

- [ ] **Step 8: Commit**

```bash
git add WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/operations/TownPlanOperation.java
git commit -m "feat(town-plan): export-block picker in the Town Plan tool panel"
```
End with the `Co-Authored-By` trailer.

---

## Self-Review

- **Spec coverage:** §3.1 default → stone → Task 1. §3.2 "Export block" button + `MaterialSelector` dialog + store in dimension layer settings → Task 2 (Steps 2–5). §3.2 button reflects current block → `refreshBlockButtonLabel` (Task 2 Steps 2/4). §4 data flow (stored per-world, read by exporter) → Task 2 Step 3 (`setLayerSettings`) + no exporter change. §5 testing: backend default unit test → Task 1; picker build/run-verified → Task 2 Steps 6–7. Out-of-scope items (global default, marker-height/export-toggle UI) correctly absent.
- **Placeholder scan:** No TBD/TODO. The "Implementer — confirm" notes point to exact named methods (`getWorld().getPlatform()`, `setExtendedBlockIds`, material label), grounded confirmations for GUI glue that compiles only with the JIDE-dependent WPGUI module — not vague guidance.
- **Type consistency:** `TownLayoutSettings` (`getBlock`/`setBlock`), `TownLayout.INSTANCE`, `MaterialSelector` (`setMaterial`/`getMaterial`/`setPlatform`), `dimension.getLayerSettings`/`setLayerSettings`, and the `blockButton`/`refreshBlockButtonLabel`/`currentExportBlock`/`currentOrNewSettings`/`chooseBlock` members are used consistently across steps. `Material.STONE` is the default referenced in both Task 1 and Task 2's `currentExportBlock` fallback (`new TownLayoutSettings().getBlock()`).
