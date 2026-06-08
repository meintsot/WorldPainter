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

    private boolean syncing;

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

        operation.setSelectionListener(() -> SwingUtilities.invokeLater(this::syncFromOperation));

        refresh();
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new GridLayout(0, 2, 4, 2));
        form.add(new JLabel("X (E-W):")); form.add(xSpin);
        form.add(new JLabel("Y (N-S):")); form.add(ySpin);
        form.add(new JLabel("Height:")); form.add(heightSpin);
        form.add(new JLabel("")); form.add(snapBox);
        form.add(new JLabel("Rotation (deg):")); form.add(rotSpin);

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
            heightSpin.setEnabled(enabled && (p != null) && !p.isSnapToSurface());
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
