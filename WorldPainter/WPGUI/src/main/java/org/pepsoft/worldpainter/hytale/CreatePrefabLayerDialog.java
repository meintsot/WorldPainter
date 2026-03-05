package org.pepsoft.worldpainter.hytale;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Dialog for creating a new {@link HytaleSpecificPrefabLayer} from a set of
 * selected prefab entries. Allows the user to set a name and pick a colour.
 */
public class CreatePrefabLayerDialog extends JDialog {
    private final List<PrefabFileEntry> selectedEntries;
    private final JTextField nameField;
    private final JButton colourButton;
    private Color chosenColour;
    private HytaleSpecificPrefabLayer result;

    public CreatePrefabLayerDialog(Window owner, List<PrefabFileEntry> selectedEntries) {
        super(owner, "Create Prefab Layer", ModalityType.APPLICATION_MODAL);
        this.selectedEntries = selectedEntries;
        this.chosenColour = generateDefaultColour(selectedEntries);

        setLayout(new BorderLayout(8, 8));
        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // --- Top: name + colour ---
        JPanel topPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        topPanel.add(new JLabel("Name:"), gbc);

        nameField = new JTextField(generateDefaultName(selectedEntries), 24);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        topPanel.add(nameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        topPanel.add(new JLabel("Colour:"), gbc);

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
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.WEST;
        topPanel.add(colourButton, gbc);

        add(topPanel, BorderLayout.NORTH);

        // --- Centre: selected prefabs list ---
        DefaultListModel<String> listModel = new DefaultListModel<>();
        for (PrefabFileEntry entry : selectedEntries) {
            listModel.addElement(entry.toString());
        }
        JList<String> prefabList = new JList<>(listModel);
        prefabList.setEnabled(false);
        JScrollPane scrollPane = new JScrollPane(prefabList);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Selected Prefabs (" + selectedEntries.size() + ")"));
        scrollPane.setPreferredSize(new Dimension(400, 200));
        add(scrollPane, BorderLayout.CENTER);

        // --- Bottom: OK / Cancel ---
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okButton = new JButton("OK");
        okButton.addActionListener(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter a layer name.", "Missing Name", JOptionPane.WARNING_MESSAGE);
                return;
            }
            result = new HytaleSpecificPrefabLayer(name, selectedEntries, chosenColour);
            dispose();
        });
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        add(buttonPanel, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(okButton);
        pack();
        setLocationRelativeTo(owner);
    }

    /**
     * Returns the created layer, or {@code null} if the dialog was cancelled.
     */
    public HytaleSpecificPrefabLayer getLayer() {
        return result;
    }

    private static String generateDefaultName(List<PrefabFileEntry> entries) {
        if (entries.isEmpty()) {
            return "Prefab Layer";
        }
        // Use first entry's category + subcategory if they're all the same
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
        // Deterministic colour based on first entry's name
        int hash = entries.get(0).getRelativePath().hashCode();
        float hue = (hash & 0x7FFFFFFF) / (float) Integer.MAX_VALUE;
        return Color.getHSBColor(hue, 0.7f, 0.9f);
    }
}
