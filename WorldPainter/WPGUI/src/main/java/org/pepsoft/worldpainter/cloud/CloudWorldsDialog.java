package org.pepsoft.worldpainter.cloud;

import org.pepsoft.worldpainter.cloud.api.CloudWorldsClient;
import org.pepsoft.worldpainter.cloud.auth.CloudSession;
import org.pepsoft.worldpainter.cloud.auth.Session;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Modal world-picker. Shows the user's cloud worlds in a table; user picks one or creates new.
 *
 * <p>Returns the selected world (id + name) via {@link #showAndPick(Frame)} or empty
 * if the user cancelled.
 */
public final class CloudWorldsDialog extends JDialog {

    private static final URI DEFAULT_BACKEND = URI.create(
            System.getProperty("worldpainter.cloud.backend", "http://localhost:8080"));

    private final WorldsTableModel model = new WorldsTableModel();
    private final JTable table = new JTable(model);
    private final JButton openButton = new JButton("Open");
    private final JButton newWorldButton = new JButton("New World…");
    private final JButton deleteButton = new JButton("Delete");
    private final JButton cancelButton = new JButton("Cancel");
    private final JLabel statusLabel = new JLabel(" ");

    private Selection selection;

    private CloudWorldsDialog(Frame owner) {
        super(owner, "Open Cloud World", true);
        setLayout(new BorderLayout(8, 8));
        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(520, 280));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(newWorldButton);
        buttons.add(deleteButton);
        buttons.add(cancelButton);
        buttons.add(openButton);

        statusLabel.setForeground(new Color(120, 120, 120));

        add(scroll, BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout());
        south.add(statusLabel, BorderLayout.WEST);
        south.add(buttons, BorderLayout.EAST);
        add(south, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(openButton);

        openButton.addActionListener(e -> onOpen());
        newWorldButton.addActionListener(e -> onNewWorld());
        deleteButton.addActionListener(e -> onDelete());
        cancelButton.addActionListener(e -> setVisible(false));

        openButton.setEnabled(false);
        deleteButton.setEnabled(false);
        table.getSelectionModel().addListSelectionListener(e -> {
            boolean rowSelected = table.getSelectedRow() >= 0;
            openButton.setEnabled(rowSelected);
            deleteButton.setEnabled(rowSelected);
        });

        pack();
        setLocationRelativeTo(owner);

        loadWorldsAsync();
    }

    private void loadWorldsAsync() {
        Session session = CloudSession.getInstance().current()
                .orElseThrow(() -> new IllegalStateException("Not signed in"));
        CloudWorldsClient client = new CloudWorldsClient(DEFAULT_BACKEND, session.token());

        statusLabel.setText("Loading worlds…");
        SwingWorker<List<CloudWorldsClient.WorldSummary>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<CloudWorldsClient.WorldSummary> doInBackground() {
                return client.listWorlds();
            }
            @Override
            protected void done() {
                try {
                    List<CloudWorldsClient.WorldSummary> worlds = get();
                    model.setWorlds(worlds);
                    statusLabel.setText(worlds.isEmpty()
                            ? "No worlds yet. Click 'New World…' to create one."
                            : worlds.size() + " world(s)");
                } catch (Exception ex) {
                    statusLabel.setForeground(new Color(180, 0, 0));
                    statusLabel.setText("Failed to load worlds: " + ex.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void onOpen() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        CloudWorldsClient.WorldSummary chosen = model.row(row);
        selection = new Selection(chosen.id(), chosen.name());
        setVisible(false);
    }

    private void onDelete() {
        int row = table.getSelectedRow();
        if (row < 0) return;
        CloudWorldsClient.WorldSummary chosen = model.row(row);
        int choice = JOptionPane.showConfirmDialog(this,
                "Permanently delete cloud world \"" + chosen.name() + "\"?\n" +
                "All painted tiles and ops will be lost. This cannot be undone.",
                "Delete cloud world",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) return;

        Session session = CloudSession.getInstance().current().orElseThrow();
        CloudWorldsClient client = new CloudWorldsClient(DEFAULT_BACKEND, session.token());
        statusLabel.setForeground(new Color(120, 120, 120));
        statusLabel.setText("Deleting…");

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                client.deleteWorld(chosen.id());
                return null;
            }
            @Override
            protected void done() {
                try {
                    get();
                    statusLabel.setText("Deleted \"" + chosen.name() + "\"");
                    loadWorldsAsync();   // refresh the table
                } catch (Exception ex) {
                    statusLabel.setForeground(new Color(180, 0, 0));
                    statusLabel.setText("Delete failed: " + ex.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void onNewWorld() {
        String name = JOptionPane.showInputDialog(this, "World name:",
                "New cloud world", JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.isBlank()) return;
        Session session = CloudSession.getInstance().current().orElseThrow();
        CloudWorldsClient client = new CloudWorldsClient(DEFAULT_BACKEND, session.token());

        statusLabel.setText("Creating world…");
        SwingWorker<UUID, Void> worker = new SwingWorker<>() {
            @Override
            protected UUID doInBackground() { return client.createWorld(name, "hytale"); }
            @Override
            protected void done() {
                try {
                    UUID newId = get();
                    selection = new Selection(newId, name);
                    setVisible(false);
                } catch (Exception ex) {
                    statusLabel.setForeground(new Color(180, 0, 0));
                    statusLabel.setText("Create failed: " + ex.getMessage());
                }
            }
        };
        worker.execute();
    }

    /** Show the dialog modally and return the chosen world, or empty if cancelled. */
    public static java.util.Optional<Selection> showAndPick(Frame owner) {
        if (!CloudSession.getInstance().isSignedIn()) {
            JOptionPane.showMessageDialog(owner,
                    "Please sign in to TalePainter Cloud first.",
                    "Not signed in", JOptionPane.WARNING_MESSAGE);
            return java.util.Optional.empty();
        }
        CloudWorldsDialog dialog = new CloudWorldsDialog(owner);
        dialog.setVisible(true);
        return java.util.Optional.ofNullable(dialog.selection);
    }

    public record Selection(UUID id, String name) {}

    private static final class WorldsTableModel extends AbstractTableModel {
        private List<CloudWorldsClient.WorldSummary> worlds = List.of();
        private static final String[] COLUMNS = { "Name", "Platform", "Storage" };

        void setWorlds(List<CloudWorldsClient.WorldSummary> worlds) {
            this.worlds = worlds;
            fireTableDataChanged();
        }
        CloudWorldsClient.WorldSummary row(int i) { return worlds.get(i); }

        @Override public int getRowCount() { return worlds.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }
        @Override
        public Object getValueAt(int row, int col) {
            CloudWorldsClient.WorldSummary w = worlds.get(row);
            return switch (col) {
                case 0 -> w.name();
                case 1 -> w.platform();
                case 2 -> w.storageMode();
                default -> "";
            };
        }
    }
}
