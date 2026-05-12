package org.pepsoft.worldpainter.hytale;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Modal Swing dialog that lets the user configure the
 * {@link HytaleAutoVegetationLayer} settings:
 * <ul>
 *   <li>Global on/off and seed</li>
 *   <li>Per-biome coverage percentage (0–100)</li>
 *   <li>Per-biome plant list with occurrence weights</li>
 * </ul>
 *
 * <p>The dialog works on an internal copy of the data.  The live
 * {@link HytaleAutoVegetationSettings} object is only mutated when the
 * user clicks OK; Cancel leaves it untouched.</p>
 */
public final class HytaleAutoVegetationDialog extends JDialog {

    // ── Public API ────────────────────────────────────────────────────

    private final HytaleAutoVegetationSettings settings;
    private boolean accepted;

    public HytaleAutoVegetationDialog(Window owner, HytaleAutoVegetationSettings settings) {
        super(owner, "Auto Vegetation Settings", ModalityType.APPLICATION_MODAL);
        this.settings = settings;
        buildUi();
        pack();
        setLocationRelativeTo(owner);
    }

    public boolean isAccepted() {
        return accepted;
    }

    // ── Working state (in-dialog copies) ─────────────────────────────

    /** JCheckBox for the global "Enabled" toggle. */
    private JCheckBox enabledCheckBox;

    /** JTextField for the seed (long). */
    private JTextField seedField;

    /** Panel that holds the per-biome rows (inside the scroll pane). */
    private JPanel biomeListPanel;

    /**
     * One mutable record per biome row, keeping slider state and plant list
     * so we can write back on OK without touching the real settings until then.
     */
    private final List<BiomeRowState> biomeRows = new ArrayList<>();

    // ── UI construction ───────────────────────────────────────────────

    private void buildUi() {
        setLayout(new BorderLayout(8, 8));
        ((JComponent) getContentPane()).setBorder(
                BorderFactory.createEmptyBorder(10, 10, 10, 10));

        add(buildTopBar(),    BorderLayout.NORTH);
        add(buildBiomeArea(), BorderLayout.CENTER);
        add(buildButtonBar(), BorderLayout.SOUTH);

        getRootPane().setDefaultButton(findDefaultButton());
    }

    /** Top bar: Enabled checkbox, Seed field, Reset-to-defaults button. */
    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        bar.setBorder(BorderFactory.createTitledBorder("Global"));

        enabledCheckBox = new JCheckBox("Enabled", settings.isEnabled());
        bar.add(enabledCheckBox);

        bar.add(new JLabel("Seed:"));
        seedField = new JTextField(Long.toString(settings.getSeed()), 18);
        seedField.setToolTipText("Random seed (64-bit integer) for vegetation placement");
        bar.add(seedField);

        JButton resetButton = new JButton("Reset all to defaults");
        resetButton.setToolTipText("Reload the shipped biome vegetation defaults into the editor");
        resetButton.addActionListener(e -> resetToDefaults());
        bar.add(resetButton);

        return bar;
    }

    /** Scroll pane containing one row per biome, with separators between zones. */
    private JScrollPane buildBiomeArea() {
        biomeListPanel = new JPanel();
        biomeListPanel.setLayout(new BoxLayout(biomeListPanel, BoxLayout.Y_AXIS));

        populateBiomeRows(settings);

        JScrollPane scroll = new JScrollPane(biomeListPanel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setPreferredSize(new Dimension(780, 420));
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        return scroll;
    }

    /**
     * Clear and rebuild all biome rows from the given settings snapshot.
     * Called once during construction and again after "Reset to defaults".
     */
    private void populateBiomeRows(HytaleAutoVegetationSettings source) {
        biomeListPanel.removeAll();
        biomeRows.clear();

        int[] order = HytaleBiome.getBiomeOrder();
        for (int biomeId : order) {
            if (biomeId == -1) {
                // Spacer between zones
                biomeListPanel.add(buildZoneSeparator());
                continue;
            }
            HytaleBiome biome = HytaleBiome.getById(biomeId);
            if (biome == null) {
                continue;
            }
            HytaleAutoVegetationSettings.BiomeVegetationConfig cfg =
                    source.getByBiome().get(biomeId);
            int initCoverage = (cfg != null) ? cfg.getCoveragePercent() : 0;
            List<HytaleAutoVegetationSettings.PlantEntry> initPlants =
                    (cfg != null)
                            ? new ArrayList<>(cfg.getPlants())
                            : new ArrayList<>();

            BiomeRowState state = new BiomeRowState(biome, initCoverage, initPlants);
            biomeRows.add(state);
            biomeListPanel.add(buildBiomeRow(state));
        }

        biomeListPanel.revalidate();
        biomeListPanel.repaint();
    }

    /** Thin horizontal separator shown between zone groups. */
    private static Component buildZoneSeparator() {
        JSeparator sep = new JSeparator(JSeparator.HORIZONTAL);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 8));
        return sep;
    }

    /** Build the full row panel for a single biome. */
    private JPanel buildBiomeRow(BiomeRowState state) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getMinimumSize().height + 12));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Color swatch
        JPanel swatch = new JPanel();
        swatch.setBackground(new Color(state.biome.getDisplayColor()));
        swatch.setPreferredSize(new Dimension(14, 14));
        swatch.setMinimumSize(new Dimension(14, 14));
        swatch.setMaximumSize(new Dimension(14, 14));
        swatch.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY, 1));
        row.add(swatch);
        row.add(Box.createHorizontalStrut(6));

        // Biome display name (fixed width so sliders align)
        JLabel nameLabel = new JLabel(state.biome.getDisplayName());
        nameLabel.setPreferredSize(new Dimension(180, 20));
        nameLabel.setMinimumSize(new Dimension(180, 20));
        nameLabel.setMaximumSize(new Dimension(180, 20));
        row.add(nameLabel);
        row.add(Box.createHorizontalStrut(8));

        // Coverage label + slider
        JLabel coverageLabel = new JLabel(String.format("%3d%%", state.coveragePercent));
        coverageLabel.setPreferredSize(new Dimension(38, 20));
        coverageLabel.setMinimumSize(new Dimension(38, 20));
        coverageLabel.setMaximumSize(new Dimension(38, 20));

        JSlider slider = new JSlider(0, 100, state.coveragePercent);
        slider.setPreferredSize(new Dimension(140, 24));
        slider.setMinimumSize(new Dimension(80, 24));
        slider.setMaximumSize(new Dimension(180, 24));
        slider.setToolTipText("Coverage percentage for " + state.biome.getDisplayName());
        slider.addChangeListener(e -> {
            state.coveragePercent = slider.getValue();
            coverageLabel.setText(String.format("%3d%%", state.coveragePercent));
        });
        state.slider = slider;

        row.add(new JLabel("Coverage:"));
        row.add(Box.createHorizontalStrut(4));
        row.add(slider);
        row.add(Box.createHorizontalStrut(4));
        row.add(coverageLabel);
        row.add(Box.createHorizontalStrut(10));

        // Plant list panel (grows horizontally)
        JPanel plantArea = buildPlantArea(state);
        row.add(plantArea);

        return row;
    }

    /**
     * Panel showing the current plant entries for one biome, with an
     * "Add plant" button.
     * Uses a simple list model + JList (simpler than chips) plus an
     * "Add plant" and "Remove selected" button.
     */
    private JPanel buildPlantArea(BiomeRowState state) {
        JPanel panel = new JPanel(new BorderLayout(4, 0));

        // List model backed by state.plants
        DefaultListModel<String> listModel = new DefaultListModel<>();
        refreshListModel(listModel, state);
        state.listModel = listModel;

        JList<String> list = new JList<>(listModel);
        list.setVisibleRowCount(3);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setToolTipText("Plants for this biome (name · weight)");
        JScrollPane listScroll = new JScrollPane(list);
        listScroll.setPreferredSize(new Dimension(200, 60));
        panel.add(listScroll, BorderLayout.CENTER);

        // Buttons: Add / Remove / Edit Weight
        JPanel btnPanel = new JPanel(new GridLayout(0, 1, 0, 2));

        JButton addBtn = new JButton("+ Add plant");
        addBtn.setToolTipText("Add a surface-only Hytale terrain as a plant");
        addBtn.addActionListener(e -> showAddPlantPicker(state, listModel, list));
        btnPanel.add(addBtn);

        JButton removeBtn = new JButton("Remove");
        removeBtn.setToolTipText("Remove the selected plant entry");
        removeBtn.addActionListener(e -> {
            int idx = list.getSelectedIndex();
            if (idx >= 0 && idx < state.plants.size()) {
                state.plants.remove(idx);
                refreshListModel(listModel, state);
            }
        });
        btnPanel.add(removeBtn);

        JButton editWeightBtn = new JButton("Edit weight");
        editWeightBtn.setToolTipText("Change the occurrence weight of the selected plant");
        editWeightBtn.addActionListener(e -> {
            int idx = list.getSelectedIndex();
            if (idx < 0 || idx >= state.plants.size()) {
                return;
            }
            HytaleAutoVegetationSettings.PlantEntry existing = state.plants.get(idx);
            String input = JOptionPane.showInputDialog(
                    this,
                    "Enter new weight (1–100):",
                    existing.getOccurrenceWeight());
            if (input == null) {
                return;
            }
            try {
                int w = Integer.parseInt(input.trim());
                if (w < 1 || w > 100) {
                    JOptionPane.showMessageDialog(this, "Weight must be between 1 and 100.",
                            "Invalid Weight", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                state.plants.set(idx,
                        new HytaleAutoVegetationSettings.PlantEntry(
                                existing.getHytaleTerrainId(), w));
                refreshListModel(listModel, state);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Please enter a valid integer.",
                        "Invalid Input", JOptionPane.WARNING_MESSAGE);
            }
        });
        btnPanel.add(editWeightBtn);

        panel.add(btnPanel, BorderLayout.EAST);
        return panel;
    }

    /** (Re)populate a list model from the current plants in a BiomeRowState. */
    private static void refreshListModel(DefaultListModel<String> model, BiomeRowState state) {
        model.clear();
        for (HytaleAutoVegetationSettings.PlantEntry entry : state.plants) {
            model.addElement(plantEntryLabel(entry));
        }
    }

    /** Human-readable label for a plant entry: "Name · weight". */
    private static String plantEntryLabel(HytaleAutoVegetationSettings.PlantEntry entry) {
        HytaleTerrain terrain = HytaleTerrain.getById(entry.getHytaleTerrainId());
        String name = (terrain != null) ? terrain.getName() : entry.getHytaleTerrainId().toString();
        return name + " · " + entry.getOccurrenceWeight();
    }

    /**
     * Show the "add plant" picker as a small dialog listing surface-only terrains
     * that are not already in the biome's plant list.
     */
    private void showAddPlantPicker(BiomeRowState state, DefaultListModel<String> listModel,
                                    JList<String> list) {
        // Collect surface-only terrains not yet in this biome
        Set<UUID> alreadyAdded = new HashSet<>();
        for (HytaleAutoVegetationSettings.PlantEntry pe : state.plants) {
            alreadyAdded.add(pe.getHytaleTerrainId());
        }

        List<HytaleTerrain> surfaceTerrains = new ArrayList<>();
        for (HytaleTerrain t : HytaleTerrain.getAllTerrains()) {
            HytaleBlock primary = t.getPrimaryBlock();
            if (primary != null
                    && HytaleBlockRegistry.isSurfaceOnlyBlock(primary.id)
                    && !alreadyAdded.contains(t.getId())) {
                surfaceTerrains.add(t);
            }
        }

        if (surfaceTerrains.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "All surface-only terrains are already in the list.",
                    "No terrains to add", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Sort alphabetically by name for readability
        surfaceTerrains.sort(Comparator.comparing(HytaleTerrain::getName));

        // Build picker dialog
        JDialog picker = new JDialog(this, "Add Plant — " + state.biome.getDisplayName(),
                ModalityType.APPLICATION_MODAL);
        picker.setLayout(new BorderLayout(8, 8));
        ((JComponent) picker.getContentPane()).setBorder(
                BorderFactory.createEmptyBorder(8, 8, 8, 8));

        DefaultListModel<HytaleTerrain> pickerModel = new DefaultListModel<>();
        for (HytaleTerrain t : surfaceTerrains) {
            pickerModel.addElement(t);
        }
        JList<HytaleTerrain> pickerList = new JList<>(pickerModel);
        pickerList.setCellRenderer(new TerrainListCellRenderer());
        pickerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        pickerList.setSelectedIndex(0);

        JScrollPane pickerScroll = new JScrollPane(pickerList);
        pickerScroll.setPreferredSize(new Dimension(260, 300));
        picker.add(pickerScroll, BorderLayout.CENTER);

        // Weight spinner
        JPanel weightPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        weightPanel.add(new JLabel("Occurrence weight (1–100):"));
        JSpinner weightSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 100, 1));
        weightPanel.add(weightSpinner);
        picker.add(weightPanel, BorderLayout.NORTH);

        // OK / Cancel
        JPanel pickerButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okBtn = new JButton("Add");
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e2 -> picker.dispose());
        okBtn.addActionListener(e2 -> {
            HytaleTerrain selected = pickerList.getSelectedValue();
            if (selected == null) {
                picker.dispose();
                return;
            }
            int weight = (int) weightSpinner.getValue();
            state.plants.add(
                    new HytaleAutoVegetationSettings.PlantEntry(selected.getId(), weight));
            refreshListModel(listModel, state);
            picker.dispose();
        });
        pickerButtons.add(okBtn);
        pickerButtons.add(cancelBtn);
        picker.add(pickerButtons, BorderLayout.SOUTH);

        picker.getRootPane().setDefaultButton(okBtn);
        picker.pack();
        picker.setLocationRelativeTo(this);
        picker.setVisible(true);
    }

    /** OK / Cancel button bar. */
    private JPanel buildButtonBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());

        JButton okButton = new JButton("OK");
        okButton.addActionListener(e -> onOk());

        bar.add(cancelButton);
        bar.add(okButton);
        return bar;
    }

    // ── Actions ───────────────────────────────────────────────────────

    /**
     * Commit the in-dialog working state back to the live settings, then close.
     * Only this path mutates the settings object.
     */
    private void onOk() {
        // Validate seed
        long seed;
        try {
            seed = Long.parseLong(seedField.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this,
                    "Seed must be a valid 64-bit integer.",
                    "Invalid Seed", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Write global settings
        settings.setEnabled(enabledCheckBox.isSelected());
        settings.setSeed(seed);

        // Write per-biome configs
        for (BiomeRowState state : biomeRows) {
            HytaleAutoVegetationSettings.BiomeVegetationConfig cfg =
                    new HytaleAutoVegetationSettings.BiomeVegetationConfig(
                            state.coveragePercent,
                            new ArrayList<>(state.plants));
            settings.setBiomeConfig(state.biome.getId(), cfg);
        }

        accepted = true;
        dispose();
    }

    /**
     * Reload shipped defaults into the in-dialog working state without touching
     * the live settings object (that mutation happens only on OK).
     */
    private void resetToDefaults() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Reset all biome vegetation settings to the shipped defaults?\n"
                + "Your current changes in this dialog will be lost.",
                "Reset to Defaults",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.OK_OPTION) {
            return;
        }

        // Build a temporary settings object populated with defaults
        HytaleAutoVegetationSettings temp = new HytaleAutoVegetationSettings();
        HytaleAutoVegetationDefaults.applyShippedDefaultsTo(temp);

        // Repopulate the dialog rows from the temporary settings
        populateBiomeRows(temp);

        // Also reset coverage default — leave seed and enabled as-is
        // (reset affects biome data only; the user keeps their seed/enabled choice)
    }

    /** Find the OK button to set as the default. */
    private JButton findDefaultButton() {
        // Traverse to find the OK button in the button bar
        Container content = getContentPane();
        for (Component comp : ((JPanel) content).getComponents()) {
            if (comp instanceof JPanel) {
                for (Component btn : ((JPanel) comp).getComponents()) {
                    if (btn instanceof JButton && "OK".equals(((JButton) btn).getText())) {
                        return (JButton) btn;
                    }
                }
            }
        }
        return null;
    }

    // ── Inner helpers ─────────────────────────────────────────────────

    /**
     * Mutable per-biome row state.  The dialog works exclusively on these
     * copies until OK is pressed.
     */
    private static final class BiomeRowState {
        final HytaleBiome biome;
        int coveragePercent;
        final List<HytaleAutoVegetationSettings.PlantEntry> plants;
        // References to Swing widgets so resetToDefaults can refresh them
        JSlider slider;
        DefaultListModel<String> listModel;

        BiomeRowState(HytaleBiome biome, int coveragePercent,
                      List<HytaleAutoVegetationSettings.PlantEntry> plants) {
            this.biome = biome;
            this.coveragePercent = coveragePercent;
            this.plants = new ArrayList<>(plants);
        }
    }

    /** Cell renderer for the terrain picker list. */
    private static final class TerrainListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof HytaleTerrain) {
                HytaleTerrain t = (HytaleTerrain) value;
                setText(t.getName());
                HytaleBlock block = t.getPrimaryBlock();
                if (block != null) {
                    setToolTipText("Block: " + block.id);
                }
            }
            return this;
        }
    }

    private static final long serialVersionUID = 1L;
}
