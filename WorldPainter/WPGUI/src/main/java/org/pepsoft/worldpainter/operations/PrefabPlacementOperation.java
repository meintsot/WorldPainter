package org.pepsoft.worldpainter.operations;

import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.WorldPainter;
import org.pepsoft.worldpainter.hytale.HytalePrefabPlacement;
import org.pepsoft.worldpainter.hytale.HytaleTerrain;
import org.pepsoft.worldpainter.hytale.prefab.HytalePrefabThumbnailRenderer;
import org.pepsoft.worldpainter.hytale.prefab.PrefabPaletteModel;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tool for authoring exact Hytale prefab placements. When active, its Tool Settings
 * panel shows a searchable thumbnail list of prefabs; pick one, then click the map to
 * place it (snap-to-surface, 0° by default). Clicking an existing marker selects it.
 * Created placements are managed/edited in the "Prefab Placements" panel.
 */
public final class PrefabPlacementOperation extends MouseOrTabletOperation {
    private static final int THUMB = 48;

    private final WorldPainter view;
    private final AtomicLong idSeq = new AtomicLong(System.nanoTime());
    private long selectedId = -1;
    private Runnable selectionListener;

    // Tool Settings options panel (built lazily on first show)
    private JPanel optionsPanel;
    private PrefabPaletteModel model;
    private HytalePrefabThumbnailRenderer renderer;
    private DefaultListModel<PrefabPaletteModel.PrefabItem> prefabListModel;
    private JList<PrefabPaletteModel.PrefabItem> prefabList;
    private JLabel selectedLabel;
    private PrefabPaletteModel.PrefabItem currentPrefab;

    public PrefabPlacementOperation(WorldPainter view) {
        super("Place Prefab", "Place a Hytale prefab at an exact location", "prefabPlacement");
        this.view = view;
        setView(view);
    }

    @Override
    protected void deactivate() {
        view.setPrefabPlacementSelectionId(null);
        super.deactivate();
    }

    @Override
    public JPanel getOptionsPanel() {
        if (optionsPanel == null) {
            optionsPanel = buildOptionsPanel();
        }
        return optionsPanel;
    }

    private JPanel buildOptionsPanel() {
        final java.io.File assetsDir = HytaleTerrain.getHytaleAssetsDir();
        renderer = new HytalePrefabThumbnailRenderer(assetsDir);
        model = PrefabPaletteModel.withDiscovered(assetsDir);

        final JPanel panel = new JPanel(new BorderLayout(2, 2));
        panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        final JLabel hint = new JLabel("<html>Pick a prefab, then click the map to place it.</html>");
        hint.setFont(hint.getFont().deriveFont(Font.ITALIC, 11f));
        final JTextField search = new JTextField();
        final JPanel north = new JPanel(new BorderLayout(2, 2));
        north.add(hint, BorderLayout.NORTH);
        north.add(search, BorderLayout.SOUTH);
        panel.add(north, BorderLayout.NORTH);

        prefabListModel = new DefaultListModel<>();
        prefabList = new JList<>(prefabListModel);
        prefabList.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        prefabList.setVisibleRowCount(-1);
        prefabList.setFixedCellWidth(THUMB + 20);
        prefabList.setFixedCellHeight(THUMB + 26);
        prefabList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        prefabList.setCellRenderer(new ThumbCellRenderer(renderer, THUMB));
        prefabList.addListSelectionListener(e -> {
            if (! e.getValueIsAdjusting()) {
                currentPrefab = prefabList.getSelectedValue();
                updateSelectedLabel();
            }
        });
        panel.add(new JScrollPane(prefabList), BorderLayout.CENTER);

        selectedLabel = new JLabel(" ");
        selectedLabel.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
        panel.add(selectedLabel, BorderLayout.SOUTH);

        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { refilter(search.getText()); }
            @Override public void removeUpdate(DocumentEvent e) { refilter(search.getText()); }
            @Override public void changedUpdate(DocumentEvent e) { refilter(search.getText()); }
        });
        refilter("");
        updateSelectedLabel();
        return panel;
    }

    private void refilter(String query) {
        prefabListModel.clear();
        for (PrefabPaletteModel.PrefabItem item : model.search(query)) {
            prefabListModel.addElement(item);
        }
    }

    private void updateSelectedLabel() {
        selectedLabel.setText((currentPrefab != null) ? ("Selected: " + currentPrefab.name) : "No prefab selected");
    }

    @Override
    protected void tick(int centreX, int centreY, boolean inverse, boolean first, float dynamicLevel) {
        if (! first) {
            return;
        }
        final Dimension dim = view.getDimension();
        if (dim == null) {
            return;
        }
        // Click an existing marker to select it; otherwise place the currently-picked prefab.
        final HytalePrefabPlacement hit = hitTest(dim, centreX, centreY);
        if (hit != null) {
            selectedId = hit.getId();
        } else if (currentPrefab != null) {
            final HytalePrefabPlacement placement = new HytalePrefabPlacement(
                    idSeq.incrementAndGet(), currentPrefab.path, currentPrefab.name,
                    centreX, centreY, null, true, 0.0);
            dim.addHytalePrefabPlacement(placement);
            selectedId = placement.getId();
        } else {
            return; // nothing picked and not on an existing marker
        }
        notifySelection();
        view.setPrefabPlacementSelectionId(selectedId);
    }

    private HytalePrefabPlacement hitTest(Dimension dim, int worldX, int worldY) {
        HytalePrefabPlacement best = null;
        int bestDist = Integer.MAX_VALUE;
        for (HytalePrefabPlacement p : dim.getHytalePrefabPlacements()) {
            final int dx = p.getX() - worldX, dy = p.getY() - worldY;
            final int dist = (dx * dx) + (dy * dy);
            if ((dist < bestDist) && (dist <= 64)) {
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
        view.setPrefabPlacementSelectionId(id);
    }

    public void setSelectionListener(Runnable listener) {
        this.selectionListener = listener;
    }

    private void notifySelection() {
        if (selectionListener != null) {
            selectionListener.run();
        }
    }

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
            } else {
                icon.setIcon(new ImageIcon(renderer.render(value.path, thumb)));
                label.setText(value.name);
            }
            setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            setOpaque(true);
            return this;
        }
    }
}
