package org.pepsoft.worldpainter.merging;

import org.pepsoft.worldpainter.Configuration;
import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.UnloadableWorldException;
import org.pepsoft.worldpainter.World2;
import org.pepsoft.worldpainter.WorldIO;
import org.pepsoft.worldpainter.WorldPainterDialog;

import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import static javax.swing.BorderFactory.createEmptyBorder;
import static javax.swing.BorderFactory.createTitledBorder;

/**
 * Dialog for TP-46 "Import Map and Merge". Loads a source TalePainter
 * {@code .world} file and pastes its tiles into the currently-open dimension
 * at a user-chosen position. The cardinal-side radio sets the initial
 * placement; the preview panel below shows both maps and lets the user
 * click-drag the imported map to nudge it before committing.
 */
public class MergeMapDialog extends WorldPainterDialog {

    private final World2 currentWorld;
    private final Dimension targetDimension;

    private final JTextField sourcePathField = new JTextField(28);
    private final JLabel sourceSummary = new JLabel(" ");
    private final JLabel validationLabel = new JLabel(" ");

    private final ButtonGroup sideGroup = new ButtonGroup();
    private final JRadioButton sideEast = new JRadioButton("East", true);
    private final JRadioButton sideWest = new JRadioButton("West");
    private final JRadioButton sideNorth = new JRadioButton("North");
    private final JRadioButton sideSouth = new JRadioButton("South");

    private final ButtonGroup policyGroup = new ButtonGroup();
    private final JRadioButton policyReject = new JRadioButton("Reject if any tile overlaps (recommended)", true);
    private final JRadioButton policyReplace = new JRadioButton("Replace overlapping tiles");
    private final JRadioButton policyMerge = new JRadioButton("Merge overlapping tiles field-by-field");

    private final JCheckBox flagHeights = new JCheckBox("Use imported heights", true);
    private final JCheckBox flagTerrain = new JCheckBox("Use imported terrain", true);
    private final JCheckBox flagLayers = new JCheckBox("Use imported layers", true);
    private final JCheckBox flagBiomes = new JCheckBox("Use imported biomes", true);

    private final JSpinner offsetX = new JSpinner(new SpinnerNumberModel(0, -100000, 100000, 1));
    private final JSpinner offsetY = new JSpinner(new SpinnerNumberModel(0, -100000, 100000, 1));
    private final JButton snapToSideButton = new JButton("Snap to side");

    private final JButton okButton = new JButton("Merge");

    private MergePreviewPanel preview;
    private JPanel previewHost;

    private World2 sourceWorld;
    private Dimension sourceDimension;
    private TileMapMerger.Result lastResult;

    /** True while the dialog is updating spinners programmatically — used to
     * suppress the spinner's change listener so the preview's drag handler
     * doesn't loop with the spinner's setValue. */
    private boolean suppressSpinnerEvents = false;

    public MergeMapDialog(Frame parent, World2 currentWorld) {
        super(parent);
        this.currentWorld = currentWorld;
        this.targetDimension = currentWorld.getDimension(Dimension.Anchor.NORMAL_DETAIL);
        setTitle("Import Map and Merge");
        setModal(true);
        buildUI();
        pack();
        setLocationRelativeTo(parent);
        refreshControlState();
    }

    public TileMapMerger.Result getResult() {
        return lastResult;
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(createEmptyBorder(10, 10, 10, 10));

        // ── Top: source file picker + summary ───────────────────────────
        JPanel filePanel = new JPanel(new BorderLayout(5, 0));
        filePanel.setBorder(createTitledBorder("Source map"));
        sourcePathField.setEditable(false);
        filePanel.add(sourcePathField, BorderLayout.CENTER);
        JButton browseButton = new JButton("Browse...");
        browseButton.addActionListener(e -> chooseSource());
        filePanel.add(browseButton, BorderLayout.EAST);

        JPanel topPanel = new JPanel(new BorderLayout(0, 4));
        topPanel.add(filePanel, BorderLayout.NORTH);
        topPanel.add(sourceSummary, BorderLayout.CENTER);
        topPanel.add(validationLabel, BorderLayout.SOUTH);
        root.add(topPanel, BorderLayout.NORTH);

        // ── Middle: options on left, preview on right ───────────────────
        JPanel middle = new JPanel(new BorderLayout(10, 0));

        JPanel optionsCol = new JPanel();
        optionsCol.setLayout(new BoxLayout(optionsCol, BoxLayout.Y_AXIS));
        optionsCol.add(buildSidePanel());
        optionsCol.add(buildNudgePanel());
        optionsCol.add(buildPolicyPanel());
        middle.add(optionsCol, BorderLayout.WEST);

        previewHost = new JPanel(new BorderLayout());
        previewHost.setBorder(createTitledBorder("Placement preview (drag to move imported map)"));
        previewHost.add(new JLabel("  Load a source map to preview."), BorderLayout.CENTER);
        middle.add(previewHost, BorderLayout.CENTER);

        root.add(middle, BorderLayout.CENTER);

        // ── Footer: buttons ────────────────────────────────────────────
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        okButton.addActionListener(e -> ok());
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> cancel());
        buttons.add(okButton);
        buttons.add(cancelButton);
        root.add(buttons, BorderLayout.SOUTH);

        // ── Listeners that update preview/offsets ──────────────────────
        sideEast.addActionListener(e -> snapToSide());
        sideWest.addActionListener(e -> snapToSide());
        sideNorth.addActionListener(e -> snapToSide());
        sideSouth.addActionListener(e -> snapToSide());
        snapToSideButton.addActionListener(e -> snapToSide());
        policyReject.addActionListener(e -> refreshControlState());
        policyReplace.addActionListener(e -> refreshControlState());
        policyMerge.addActionListener(e -> refreshControlState());
        offsetX.addChangeListener(e -> { if (!suppressSpinnerEvents) syncPreviewFromSpinners(); });
        offsetY.addChangeListener(e -> { if (!suppressSpinnerEvents) syncPreviewFromSpinners(); });

        setContentPane(root);
    }

    private JPanel buildSidePanel() {
        sideGroup.add(sideEast);
        sideGroup.add(sideWest);
        sideGroup.add(sideNorth);
        sideGroup.add(sideSouth);
        JPanel sidePanel = new JPanel(new GridBagLayout());
        sidePanel.setBorder(createTitledBorder("Initial side"));
        GridBagConstraints sgc = new GridBagConstraints();
        sgc.insets = new Insets(2, 8, 2, 8);
        sgc.gridx = 1; sgc.gridy = 0; sidePanel.add(sideNorth, sgc);
        sgc.gridx = 0; sgc.gridy = 1; sidePanel.add(sideWest, sgc);
        sgc.gridx = 2; sgc.gridy = 1; sidePanel.add(sideEast, sgc);
        sgc.gridx = 1; sgc.gridy = 2; sidePanel.add(sideSouth, sgc);
        sidePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return sidePanel;
    }

    private JPanel buildNudgePanel() {
        JPanel nudge = new JPanel(new GridBagLayout());
        nudge.setBorder(createTitledBorder("Fine offset (tiles)"));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(2, 4, 2, 4);

        JButton up = new JButton("▲"); up.setMargin(new Insets(2, 6, 2, 6));
        JButton down = new JButton("▼"); down.setMargin(new Insets(2, 6, 2, 6));
        JButton left = new JButton("◀"); left.setMargin(new Insets(2, 6, 2, 6));
        JButton right = new JButton("▶"); right.setMargin(new Insets(2, 6, 2, 6));
        up.addActionListener(e -> nudgeOffset(0, -1));
        down.addActionListener(e -> nudgeOffset(0, 1));
        left.addActionListener(e -> nudgeOffset(-1, 0));
        right.addActionListener(e -> nudgeOffset(1, 0));

        g.gridx = 1; g.gridy = 0; nudge.add(up, g);
        g.gridx = 0; g.gridy = 1; nudge.add(left, g);
        g.gridx = 1; g.gridy = 1; nudge.add(snapToSideButton, g);
        g.gridx = 2; g.gridy = 1; nudge.add(right, g);
        g.gridx = 1; g.gridy = 2; nudge.add(down, g);

        JPanel xyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        xyPanel.add(new JLabel("X:"));
        xyPanel.add(offsetX);
        xyPanel.add(new JLabel("Y:"));
        xyPanel.add(offsetY);
        g.gridx = 0; g.gridy = 3; g.gridwidth = 3;
        nudge.add(xyPanel, g);

        nudge.setAlignmentX(Component.LEFT_ALIGNMENT);
        return nudge;
    }

    private JPanel buildPolicyPanel() {
        policyGroup.add(policyReject);
        policyGroup.add(policyReplace);
        policyGroup.add(policyMerge);
        JPanel policyPanel = new JPanel();
        policyPanel.setLayout(new BoxLayout(policyPanel, BoxLayout.Y_AXIS));
        policyPanel.setBorder(createTitledBorder("If tiles overlap"));
        policyPanel.add(policyReject);
        policyPanel.add(policyReplace);
        policyPanel.add(policyMerge);
        JPanel mergeFlagsPanel = new JPanel();
        mergeFlagsPanel.setLayout(new BoxLayout(mergeFlagsPanel, BoxLayout.Y_AXIS));
        mergeFlagsPanel.setBorder(createEmptyBorder(0, 22, 0, 0));
        mergeFlagsPanel.add(flagHeights);
        mergeFlagsPanel.add(flagTerrain);
        mergeFlagsPanel.add(flagLayers);
        mergeFlagsPanel.add(flagBiomes);
        policyPanel.add(mergeFlagsPanel);
        policyPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return policyPanel;
    }

    private void chooseSource() {
        JFileChooser chooser = new JFileChooser();
        Configuration cfg = Configuration.getInstance();
        if ((cfg != null) && (cfg.getWorldDirectory() != null)) {
            chooser.setCurrentDirectory(cfg.getWorldDirectory());
        }
        chooser.setFileFilter(new FileFilter() {
            @Override public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".world");
            }
            @Override public String getDescription() {
                return "TalePainter worlds (*.world)";
            }
        });
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File chosen = chooser.getSelectedFile();
        sourcePathField.setText(chosen.getAbsolutePath());
        loadSource(chosen);
    }

    private void loadSource(File file) {
        sourceWorld = null;
        sourceDimension = null;
        sourceSummary.setText("Loading…");
        try {
            WorldIO worldIO = new WorldIO();
            worldIO.load(new FileInputStream(file));
            sourceWorld = worldIO.getWorld();
        } catch (UnloadableWorldException e) {
            sourceSummary.setText("Could not load: " + e.getMessage());
            rebuildPreview();
            refreshControlState();
            return;
        } catch (IOException e) {
            sourceSummary.setText("I/O error: " + e.getMessage());
            rebuildPreview();
            refreshControlState();
            return;
        }
        sourceDimension = sourceWorld.getDimension(Dimension.Anchor.NORMAL_DETAIL);
        if (sourceDimension == null) {
            sourceSummary.setText("Source has no Surface dimension.");
        } else {
            sourceSummary.setText(String.format(
                "Source: %s — %d tiles, height %d–%d, platform %s",
                sourceWorld.getName(),
                sourceDimension.getTileCount(),
                sourceDimension.getMinHeight(),
                sourceDimension.getMaxHeight(),
                sourceWorld.getPlatform().displayName));
        }
        rebuildPreview();
        snapToSide();
        refreshControlState();
    }

    private void rebuildPreview() {
        previewHost.removeAll();
        if (sourceDimension != null && targetDimension != null && validationPasses()) {
            Point initial = TileMapMerger.computeOffset(targetDimension, sourceDimension, getSelectedSide());
            preview = new MergePreviewPanel(targetDimension, sourceDimension, initial, this::onPreviewOffsetChanged);
            previewHost.add(preview, BorderLayout.CENTER);
            updateSpinnersFromOffset(initial);
        } else {
            preview = null;
            previewHost.add(new JLabel("  Load a compatible source map to preview."), BorderLayout.CENTER);
        }
        previewHost.revalidate();
        previewHost.repaint();
    }

    private void onPreviewOffsetChanged(Point newOffset) {
        updateSpinnersFromOffset(newOffset);
    }

    private void updateSpinnersFromOffset(Point offset) {
        suppressSpinnerEvents = true;
        try {
            offsetX.setValue(offset.x);
            offsetY.setValue(offset.y);
        } finally {
            suppressSpinnerEvents = false;
        }
    }

    private void syncPreviewFromSpinners() {
        if (preview == null) return;
        int x = (Integer) offsetX.getValue();
        int y = (Integer) offsetY.getValue();
        preview.setOffset(new Point(x, y));
    }

    private void nudgeOffset(int dx, int dy) {
        if (preview == null) return;
        Point cur = preview.getOffset();
        preview.setOffset(new Point(cur.x + dx, cur.y + dy));
    }

    private void snapToSide() {
        if (sourceDimension == null || targetDimension == null) return;
        Point sideOffset = TileMapMerger.computeOffset(targetDimension, sourceDimension, getSelectedSide());
        if (preview != null) {
            preview.setOffset(sideOffset);
        } else {
            updateSpinnersFromOffset(sideOffset);
        }
    }

    private boolean validationPasses() {
        if (sourceWorld == null || sourceDimension == null || targetDimension == null) return false;
        if (!sourceWorld.getPlatform().equals(currentWorld.getPlatform())) return false;
        return (sourceDimension.getMinHeight() == targetDimension.getMinHeight())
            && (sourceDimension.getMaxHeight() == targetDimension.getMaxHeight());
    }

    private void refreshControlState() {
        boolean mergeMode = policyMerge.isSelected();
        flagHeights.setEnabled(mergeMode);
        flagTerrain.setEnabled(mergeMode);
        flagLayers.setEnabled(mergeMode);
        flagBiomes.setEnabled(mergeMode);

        if (sourceWorld == null || sourceDimension == null || targetDimension == null) {
            validationLabel.setText(" ");
            okButton.setEnabled(false);
            return;
        }
        if (!sourceWorld.getPlatform().equals(currentWorld.getPlatform())) {
            validationLabel.setText("Platform mismatch: source is " + sourceWorld.getPlatform().displayName
                + ", current is " + currentWorld.getPlatform().displayName);
            okButton.setEnabled(false);
            return;
        }
        if ((sourceDimension.getMinHeight() != targetDimension.getMinHeight())
                || (sourceDimension.getMaxHeight() != targetDimension.getMaxHeight())) {
            validationLabel.setText("Height range mismatch: source " + sourceDimension.getMinHeight()
                + "–" + sourceDimension.getMaxHeight() + ", current " + targetDimension.getMinHeight()
                + "–" + targetDimension.getMaxHeight());
            okButton.setEnabled(false);
            return;
        }
        validationLabel.setText(" ");
        okButton.setEnabled(true);
    }

    private TileMergeSettings.Side getSelectedSide() {
        if (sideNorth.isSelected()) return TileMergeSettings.Side.NORTH;
        if (sideSouth.isSelected()) return TileMergeSettings.Side.SOUTH;
        if (sideWest.isSelected()) return TileMergeSettings.Side.WEST;
        return TileMergeSettings.Side.EAST;
    }

    private TileMergeSettings.OverlapPolicy getSelectedPolicy() {
        if (policyReplace.isSelected()) return TileMergeSettings.OverlapPolicy.REPLACE;
        if (policyMerge.isSelected()) return TileMergeSettings.OverlapPolicy.MERGE;
        return TileMergeSettings.OverlapPolicy.REJECT;
    }

    @Override
    protected void ok() {
        if (preview == null) {
            JOptionPane.showMessageDialog(this, "No source map loaded.", "Cannot merge", JOptionPane.ERROR_MESSAGE);
            return;
        }
        TileMergeSettings settings = TileMergeSettings.builder()
            .side(getSelectedSide())
            .overlapPolicy(getSelectedPolicy())
            .useImportedHeights(flagHeights.isSelected())
            .useImportedTerrain(flagTerrain.isSelected())
            .useImportedLayers(flagLayers.isSelected())
            .useImportedBiomes(flagBiomes.isSelected())
            .build();
        Point finalOffset = preview.getOffset();
        try {
            lastResult = TileMapMerger.mergeAt(targetDimension, sourceDimension, settings, finalOffset);
        } catch (IllegalStateException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Merge rejected", JOptionPane.ERROR_MESSAGE);
            return;
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Cannot merge", JOptionPane.ERROR_MESSAGE);
            return;
        }
        super.ok();
    }
}
