package org.pepsoft.worldpainter.hytale;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Dialog for creating or editing a {@link HytaleSpecificPrefabLayer} from a set of
 * selected prefab entries. Allows the user to configure name, colour, density,
 * grid spacing, random displacement, and per-prefab frequency weights.
 *
 * @deprecated New prefab layers should use {@link org.pepsoft.worldpainter.layers.Bo2Layer}
 *     with the standard EditLayerDialog / Bo2LayerEditor. This dialog is retained for
 *     editing legacy {@link HytaleSpecificPrefabLayer} instances from older saved worlds.
 */
@Deprecated
public class CreatePrefabLayerDialog extends JDialog {
    private final List<PrefabFileEntry> entries;
    private final JTextField nameField;
    private final JButton colourButton;
    private final JSpinner densitySpinner;
    private final JSpinner gridXSpinner;
    private final JSpinner gridZSpinner;
    private final JSpinner displacementSpinner;
    private final PrefabTableModel tableModel;
    private Color chosenColour;
    private HytaleSpecificPrefabLayer result;
    private HytaleSpecificPrefabLayer editingLayer;

    /**
     * Create dialog for a new layer from the given selected entries.
     */
    public CreatePrefabLayerDialog(Window owner, List<PrefabFileEntry> selectedEntries) {
        this(owner, selectedEntries, null);
    }

    /**
     * Create dialog pre-populated from an existing layer (edit mode).
     */
    public CreatePrefabLayerDialog(Window owner, HytaleSpecificPrefabLayer existingLayer) {
        this(owner, new ArrayList<>(existingLayer.getPrefabEntries()), existingLayer);
    }

    private CreatePrefabLayerDialog(Window owner, List<PrefabFileEntry> selectedEntries,
                                     HytaleSpecificPrefabLayer existingLayer) {
        super(owner, existingLayer != null ? "Edit Prefab Layer" : "Create Prefab Layer",
              ModalityType.APPLICATION_MODAL);
        this.entries = new ArrayList<>(selectedEntries);
        this.editingLayer = existingLayer;
        this.chosenColour = existingLayer != null
                ? existingLayer.getColor()
                : generateDefaultColour(selectedEntries);

        setLayout(new BorderLayout(8, 8));
        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // ── Top panel: name, colour, placement settings ──────────────
        JPanel settingsPanel = new JPanel(new GridBagLayout());
        settingsPanel.setBorder(BorderFactory.createTitledBorder("Layer Settings"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(3, 6, 3, 6);
        gbc.anchor = GridBagConstraints.WEST;

        // Row 0: Name
        gbc.gridx = 0; gbc.gridy = 0;
        settingsPanel.add(new JLabel("Name:"), gbc);

        String defaultName = existingLayer != null ? existingLayer.getName() : generateDefaultName(selectedEntries);
        nameField = new JTextField(defaultName, 20);
        gbc.gridx = 1; gbc.gridwidth = 3; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        settingsPanel.add(nameField, gbc);
        gbc.gridwidth = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;

        // Row 1: Colour
        gbc.gridx = 0; gbc.gridy = 1;
        settingsPanel.add(new JLabel("Colour:"), gbc);

        colourButton = new JButton();
        colourButton.setPreferredSize(new Dimension(48, 24));
        colourButton.setBackground(chosenColour);
        colourButton.setOpaque(true);
        colourButton.addActionListener(e -> {
            Color picked = JColorChooser.showDialog(this, "Pick Layer Colour", chosenColour);
            if (picked != null) {
                chosenColour = picked;
                colourButton.setBackground(chosenColour);
            }
        });
        gbc.gridx = 1;
        settingsPanel.add(colourButton, gbc);

        // Row 2: Density
        gbc.gridx = 0; gbc.gridy = 2;
        settingsPanel.add(new JLabel("Density (blocks per attempt):"), gbc);

        int initDensity = existingLayer != null ? existingLayer.getDensity() : HytaleSpecificPrefabLayer.DEFAULT_DENSITY;
        densitySpinner = new JSpinner(new SpinnerNumberModel(initDensity, 1, 1000, 1));
        densitySpinner.setToolTipText("<html>Higher values = sparser placement.<br>Default: "
                + HytaleSpecificPrefabLayer.DEFAULT_DENSITY + "</html>");
        gbc.gridx = 1;
        settingsPanel.add(densitySpinner, gbc);

        // Row 3: Grid spacing
        gbc.gridx = 0; gbc.gridy = 3;
        settingsPanel.add(new JLabel("Grid spacing (X \u00d7 Z):"), gbc);

        int initGridX = existingLayer != null ? existingLayer.getGridX() : 1;
        int initGridZ = existingLayer != null ? existingLayer.getGridZ() : 1;
        JPanel gridPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        gridXSpinner = new JSpinner(new SpinnerNumberModel(initGridX, 1, 256, 1));
        gridXSpinner.setToolTipText("Horizontal grid spacing in blocks (X axis)");
        gridZSpinner = new JSpinner(new SpinnerNumberModel(initGridZ, 1, 256, 1));
        gridZSpinner.setToolTipText("Horizontal grid spacing in blocks (Z axis)");
        gridPanel.add(gridXSpinner);
        gridPanel.add(new JLabel("\u00d7"));
        gridPanel.add(gridZSpinner);
        gbc.gridx = 1; gbc.gridwidth = 3;
        settingsPanel.add(gridPanel, gbc);
        gbc.gridwidth = 1;

        // Row 4: Random displacement
        gbc.gridx = 0; gbc.gridy = 4;
        settingsPanel.add(new JLabel("Random displacement:"), gbc);

        int initDisplacement = existingLayer != null ? existingLayer.getRandomDisplacement() : 0;
        displacementSpinner = new JSpinner(new SpinnerNumberModel(initDisplacement, 0, 128, 1));
        displacementSpinner.setToolTipText("<html>Maximum random offset from grid position in blocks.<br>"
                + "0 = no randomness, objects align to grid.</html>");
        gbc.gridx = 1;
        settingsPanel.add(displacementSpinner, gbc);

        add(settingsPanel, BorderLayout.NORTH);

        // ── Centre: prefab table with frequency column ───────────────
        tableModel = new PrefabTableModel(entries);
        JTable table = new JTable(tableModel);
        table.getColumnModel().getColumn(0).setPreferredWidth(350);
        table.getColumnModel().getColumn(1).setPreferredWidth(80);
        table.getColumnModel().getColumn(2).setPreferredWidth(80);
        table.setRowHeight(22);

        // Add/Remove buttons
        JPanel tableButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton removeButton = new JButton("Remove Selected");
        removeButton.addActionListener(e -> {
            int[] rows = table.getSelectedRows();
            if (rows.length == 0) {
                return;
            }
            // Remove from bottom to top to preserve indices
            for (int i = rows.length - 1; i >= 0; i--) {
                entries.remove(rows[i]);
            }
            tableModel.fireTableDataChanged();
        });
        tableButtonPanel.add(removeButton);

        JPanel tablePanel = new JPanel(new BorderLayout(4, 4));
        tablePanel.setBorder(BorderFactory.createTitledBorder("Prefabs (" + entries.size() + ")"));
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(520, 200));
        tablePanel.add(scrollPane, BorderLayout.CENTER);
        tablePanel.add(tableButtonPanel, BorderLayout.SOUTH);

        add(tablePanel, BorderLayout.CENTER);

        // ── Bottom: OK / Cancel ──────────────────────────────────────
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okButton = new JButton("OK");
        okButton.addActionListener(e -> onOk());
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        add(buttonPanel, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(okButton);
        pack();
        setLocationRelativeTo(owner);
    }

    private void onOk() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a layer name.",
                    "Missing Name", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (entries.isEmpty()) {
            JOptionPane.showMessageDialog(this, "At least one prefab is required.",
                    "No Prefabs", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (editingLayer != null) {
            // Update existing layer in place
            editingLayer.setName(name);
            editingLayer.setPrefabEntries(entries);
            editingLayer.setDensity((int) densitySpinner.getValue());
            editingLayer.setGridX((int) gridXSpinner.getValue());
            editingLayer.setGridZ((int) gridZSpinner.getValue());
            editingLayer.setRandomDisplacement((int) displacementSpinner.getValue());
            result = editingLayer;
        } else {
            // Create new layer
            result = new HytaleSpecificPrefabLayer(name, entries, chosenColour);
            result.setDensity((int) densitySpinner.getValue());
            result.setGridX((int) gridXSpinner.getValue());
            result.setGridZ((int) gridZSpinner.getValue());
            result.setRandomDisplacement((int) displacementSpinner.getValue());
        }
        dispose();
    }

    /**
     * Returns the created/edited layer, or {@code null} if the dialog was cancelled.
     */
    public HytaleSpecificPrefabLayer getLayer() {
        return result;
    }

    // ── Table model for prefab entries with frequency editing ─────────

    private static class PrefabTableModel extends AbstractTableModel {
        private final List<PrefabFileEntry> entries;
        private static final String[] COLUMNS = {"Prefab", "Category", "Frequency"};

        PrefabTableModel(List<PrefabFileEntry> entries) {
            this.entries = entries;
        }

        @Override
        public int getRowCount() {
            return entries.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 2 ? Integer.class : String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 2; // Only frequency is editable
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            PrefabFileEntry entry = entries.get(rowIndex);
            switch (columnIndex) {
                case 0: return entry.getDisplayName();
                case 1: return entry.getCategory() + " / " + entry.getSubCategory();
                case 2: return entry.getFrequency();
                default: return null;
            }
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if (columnIndex == 2 && aValue instanceof Integer) {
                entries.get(rowIndex).setFrequency((Integer) aValue);
                fireTableCellUpdated(rowIndex, columnIndex);
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private static String generateDefaultName(List<PrefabFileEntry> entries) {
        if (entries.isEmpty()) {
            return "Prefab Layer";
        }
        String cat = entries.get(0).getCategory();
        String sub = entries.get(0).getSubCategory();
        boolean sameCategory = entries.stream().allMatch(e -> e.getCategory().equals(cat));
        boolean sameSubCategory = sub != null && entries.stream().allMatch(e -> sub.equals(e.getSubCategory()));
        if (sameSubCategory) {
            return sub;
        } else if (sameCategory) {
            return cat;
        } else {
            return "Mixed Prefabs";
        }
    }

    private static Color generateDefaultColour(List<PrefabFileEntry> entries) {
        if (entries.isEmpty()) {
            return Color.GREEN;
        }
        int hash = entries.get(0).getRelativePath().hashCode();
        float hue = (hash & 0x7FFFFFFF) / (float) Integer.MAX_VALUE;
        return Color.getHSBColor(hue, 0.7f, 0.9f);
    }
}
