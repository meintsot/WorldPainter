package org.pepsoft.worldpainter.hytale.prefab;

import javax.swing.*;
import java.awt.*;
import java.awt.dnd.*;
import java.io.File;

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
                    int idx = list.locationToIndex(dge.getDragOrigin());
                    if (idx < 0) {
                        return;
                    }
                    Rectangle cellBounds = list.getCellBounds(idx, idx);
                    if ((cellBounds == null) || (! cellBounds.contains(dge.getDragOrigin()))) {
                        return;
                    }
                    PrefabPaletteModel.PrefabItem item = listModel.getElementAt(idx);
                    dge.startDrag(DragSource.DefaultCopyDrop,
                            new PrefabTransferable(item.path, item.name));
                });

        refilter("");
    }

    private void refilter(String query) {
        listModel.clear();
        listModel.addAll(model.search(query));
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
            if (value == null) {
                icon.setIcon(null);
                label.setText("");
                setBackground(list.getBackground());
                setOpaque(true);
                return this;
            }
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
