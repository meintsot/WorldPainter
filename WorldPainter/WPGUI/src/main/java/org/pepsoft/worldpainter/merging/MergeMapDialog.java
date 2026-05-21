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
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Set;

import static javax.swing.BorderFactory.createEmptyBorder;
import static javax.swing.BorderFactory.createTitledBorder;

/**
 * Dialog for TP-46 "Import Map and Merge". Loads a source TalePainter
 * {@code .world} file and pastes its tiles next to the currently-open
 * dimension's tiles, on a chosen side. Surfaces validation (platform and
 * height compatibility, overlap detection) before the user commits.
 */
public class MergeMapDialog extends WorldPainterDialog {

    private final World2 currentWorld;
    private final Dimension targetDimension;

    private final JTextField sourcePathField = new JTextField(28);
    private final JLabel sourceSummary = new JLabel(" ");
    private final JLabel validationLabel = new JLabel(" ");
    private final JLabel overlapPreviewLabel = new JLabel(" ");

    private final ButtonGroup sideGroup = new ButtonGroup();
    private final JRadioButton sideEast = new JRadioButton("East", true);
    private final JRadioButton sideWest = new JRadioButton("West");
    private final JRadioButton sideNorth = new JRadioButton("North");
    private final JRadioButton sideSouth = new JRadioButton("South");

    private final ButtonGroup policyGroup = new ButtonGroup();
    private final JRadioButton policyReject = new JRadioButton("Reject if any tile overlaps (recommended)", true);
    private final JRadioButton policyReplace = new JRadioButton("Replace overlapping tiles with imported");
    private final JRadioButton policyMerge = new JRadioButton("Merge overlapping tiles field-by-field");

    private final JCheckBox flagHeights = new JCheckBox("Use imported heights", true);
    private final JCheckBox flagTerrain = new JCheckBox("Use imported terrain", true);
    private final JCheckBox flagLayers = new JCheckBox("Use imported layers", true);
    private final JCheckBox flagBiomes = new JCheckBox("Use imported biomes", true);

    private final JButton okButton = new JButton("Merge");

    private World2 sourceWorld;
    private Dimension sourceDimension;
    private TileMapMerger.Result lastResult;

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

        JPanel filePanel = new JPanel(new BorderLayout(5, 0));
        filePanel.setBorder(createTitledBorder("Source map"));
        sourcePathField.setEditable(false);
        filePanel.add(sourcePathField, BorderLayout.CENTER);
        JButton browseButton = new JButton("Browse...");
        browseButton.addActionListener(e -> chooseSource());
        filePanel.add(browseButton, BorderLayout.EAST);

        JPanel filePanelOuter = new JPanel(new BorderLayout(0, 4));
        filePanelOuter.add(filePanel, BorderLayout.NORTH);
        filePanelOuter.add(sourceSummary, BorderLayout.CENTER);
        filePanelOuter.add(validationLabel, BorderLayout.SOUTH);

        root.add(filePanelOuter, BorderLayout.NORTH);

        JPanel options = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.insets = new Insets(0, 0, 6, 6);

        sideGroup.add(sideEast);
        sideGroup.add(sideWest);
        sideGroup.add(sideNorth);
        sideGroup.add(sideSouth);
        JPanel sidePanel = new JPanel(new GridBagLayout());
        sidePanel.setBorder(createTitledBorder("Place imported map on"));
        GridBagConstraints sgc = new GridBagConstraints();
        sgc.insets = new Insets(2, 8, 2, 8);
        sgc.gridx = 1; sgc.gridy = 0; sidePanel.add(sideNorth, sgc);
        sgc.gridx = 0; sgc.gridy = 1; sidePanel.add(sideWest, sgc);
        sgc.gridx = 2; sgc.gridy = 1; sidePanel.add(sideEast, sgc);
        sgc.gridx = 1; sgc.gridy = 2; sidePanel.add(sideSouth, sgc);
        options.add(sidePanel, gbc);

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
        gbc.gridx = 1;
        options.add(policyPanel, gbc);

        root.add(options, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(overlapPreviewLabel, BorderLayout.WEST);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        okButton.addActionListener(e -> ok());
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> cancel());
        buttons.add(okButton);
        buttons.add(cancelButton);
        bottom.add(buttons, BorderLayout.EAST);
        root.add(bottom, BorderLayout.SOUTH);

        sideEast.addActionListener(e -> refreshOverlapPreview());
        sideWest.addActionListener(e -> refreshOverlapPreview());
        sideNorth.addActionListener(e -> refreshOverlapPreview());
        sideSouth.addActionListener(e -> refreshOverlapPreview());
        policyReject.addActionListener(e -> refreshControlState());
        policyReplace.addActionListener(e -> refreshControlState());
        policyMerge.addActionListener(e -> refreshControlState());

        setContentPane(root);
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
            refreshControlState();
            return;
        } catch (IOException e) {
            sourceSummary.setText("I/O error: " + e.getMessage());
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
        refreshControlState();
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
            overlapPreviewLabel.setText(" ");
            return;
        }
        if (!sourceWorld.getPlatform().equals(currentWorld.getPlatform())) {
            validationLabel.setText("Platform mismatch: source is " + sourceWorld.getPlatform().displayName
                + ", current is " + currentWorld.getPlatform().displayName);
            okButton.setEnabled(false);
            overlapPreviewLabel.setText(" ");
            return;
        }
        if ((sourceDimension.getMinHeight() != targetDimension.getMinHeight())
                || (sourceDimension.getMaxHeight() != targetDimension.getMaxHeight())) {
            validationLabel.setText("Height range mismatch: source " + sourceDimension.getMinHeight()
                + "–" + sourceDimension.getMaxHeight() + ", current " + targetDimension.getMinHeight()
                + "–" + targetDimension.getMaxHeight());
            okButton.setEnabled(false);
            overlapPreviewLabel.setText(" ");
            return;
        }
        validationLabel.setText(" ");
        okButton.setEnabled(true);
        refreshOverlapPreview();
    }

    private void refreshOverlapPreview() {
        if (sourceDimension == null || targetDimension == null) {
            overlapPreviewLabel.setText(" ");
            return;
        }
        Point offset = TileMapMerger.computeOffset(targetDimension, sourceDimension, getSelectedSide());
        Set<Point> overlaps = TileMapMerger.findOverlappingCoords(targetDimension, sourceDimension, offset);
        int newTiles = sourceDimension.getTileCount() - overlaps.size();
        overlapPreviewLabel.setText(String.format(
            "Will add %d new tile(s) at offset (%d, %d); %d overlap(s) detected.",
            newTiles, offset.x, offset.y, overlaps.size()));
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
        TileMergeSettings settings = TileMergeSettings.builder()
            .side(getSelectedSide())
            .overlapPolicy(getSelectedPolicy())
            .useImportedHeights(flagHeights.isSelected())
            .useImportedTerrain(flagTerrain.isSelected())
            .useImportedLayers(flagLayers.isSelected())
            .useImportedBiomes(flagBiomes.isSelected())
            .build();
        try {
            lastResult = TileMapMerger.merge(targetDimension, sourceDimension, settings);
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
