package org.pepsoft.worldpainter.importing;

import org.pepsoft.minecraft.ChunkStore;
import org.pepsoft.minecraft.MinecraftCoords;
import org.pepsoft.util.ProgressReceiver;
import org.pepsoft.util.swing.ProgressDialog;
import org.pepsoft.util.swing.ProgressTask;
import org.pepsoft.worldpainter.*;
import org.pepsoft.worldpainter.hytale.*;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.Set;

/**
 * Custom import dialog for Hytale worlds. Shows block mapping statistics
 * and environment/fluid preview before importing.
 */
public class HytaleMapImportDialog extends WorldPainterDialog {

    private final App app;
    private File worldDir;
    private World2 importedWorld;

    // UI Components
    private JTextField fieldFolder;
    private JLabel labelChunkCount, labelWorldBounds, labelRegionFiles;
    private JRadioButton radioReadOnlyNone, radioReadOnlyManMadeAboveGround, radioReadOnlyManMade, radioReadOnlyAll;
    private ButtonGroup readOnlyGroup;
    private JButton buttonImport;

    public HytaleMapImportDialog(App app) {
        super(app);
        this.app = app;
        initUI();
        setLocationRelativeTo(app);
    }

    public World2 getImportedWorld() {
        return importedWorld;
    }

    private void initUI() {
        setTitle("Import Hytale World");
        setModal(true);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Top: folder selection
        JPanel folderPanel = new JPanel(new BorderLayout(5, 0));
        folderPanel.add(new JLabel("World folder:"), BorderLayout.WEST);
        fieldFolder = new JTextField(30);
        fieldFolder.setEditable(false);
        folderPanel.add(fieldFolder, BorderLayout.CENTER);
        JButton btnBrowse = new JButton("Browse...");
        btnBrowse.addActionListener(e -> selectFolder());
        folderPanel.add(btnBrowse, BorderLayout.EAST);
        mainPanel.add(folderPanel, BorderLayout.NORTH);

        // Center: stats panel
        JPanel statsPanel = new JPanel(new GridBagLayout());
        statsPanel.setBorder(BorderFactory.createTitledBorder("World Statistics"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(2, 4, 2, 4);

        int row = 0;
        labelChunkCount = addStatRow(statsPanel, gbc, row++, "Chunks found:", "\u2014");
        labelWorldBounds = addStatRow(statsPanel, gbc, row++, "World bounds:", "\u2014");
        labelRegionFiles = addStatRow(statsPanel, gbc, row++, "Region files:", "\u2014");

        mainPanel.add(statsPanel, BorderLayout.CENTER);

        // Bottom: options + buttons
        JPanel bottomPanel = new JPanel(new BorderLayout());

        // Read-only radio buttons (matching Minecraft import dialog)
        JPanel readOnlyPanel = new JPanel();
        readOnlyPanel.setLayout(new BoxLayout(readOnlyPanel, BoxLayout.Y_AXIS));
        readOnlyPanel.setBorder(BorderFactory.createTitledBorder("Read-only options:"));
        readOnlyGroup = new ButtonGroup();

        radioReadOnlyNone = new JRadioButton("Do not mark any chunks read-only");
        radioReadOnlyManMadeAboveGround = new JRadioButton(
            "<html>Mark chunks containing man-made blocks <i>above ground</i> read-only</html>");
        radioReadOnlyManMadeAboveGround.setSelected(true);
        radioReadOnlyManMade = new JRadioButton("Mark chunks containing man-made blocks read-only");
        radioReadOnlyAll = new JRadioButton("Mark all chunks read-only");

        readOnlyGroup.add(radioReadOnlyNone);
        readOnlyGroup.add(radioReadOnlyManMadeAboveGround);
        readOnlyGroup.add(radioReadOnlyManMade);
        readOnlyGroup.add(radioReadOnlyAll);

        readOnlyPanel.add(radioReadOnlyNone);
        readOnlyPanel.add(radioReadOnlyManMadeAboveGround);
        readOnlyPanel.add(radioReadOnlyManMade);
        readOnlyPanel.add(radioReadOnlyAll);

        bottomPanel.add(readOnlyPanel, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnCancel = new JButton("Cancel");
        btnCancel.addActionListener(e -> cancel());
        buttonPanel.add(btnCancel);
        buttonImport = new JButton("Import");
        buttonImport.setEnabled(false);
        buttonImport.addActionListener(e -> doImport());
        buttonPanel.add(buttonImport);
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);

        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
        pack();
        setMinimumSize(new java.awt.Dimension(520, 450));
    }

    private JLabel addStatRow(JPanel panel, GridBagConstraints gbc, int row, String label, String value) {
        gbc.gridx = 0; gbc.gridy = row;
        panel.add(new JLabel(label), gbc);
        JLabel valueLabel = new JLabel(value);
        gbc.gridx = 1;
        panel.add(valueLabel, gbc);
        return valueLabel;
    }

    private void selectFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Select Hytale World Folder");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            worldDir = chooser.getSelectedFile();
            fieldFolder.setText(worldDir.getAbsolutePath());
            analyzeWorld();
        }
    }

    private void analyzeWorld() {
        buttonImport.setEnabled(false);
        if (worldDir == null || !worldDir.isDirectory()) return;

        File configFile = new File(worldDir, "config.json");
        File chunksDir = new File(worldDir, "chunks");
        if (!configFile.isFile() || !chunksDir.isDirectory()) {
            JOptionPane.showMessageDialog(this,
                "Not a valid Hytale world (missing config.json or chunks/ directory)",
                "Invalid World", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Count region files
        File[] regionFiles = chunksDir.listFiles((dir, name) -> name.endsWith(".region.bin"));
        int regionCount = (regionFiles != null) ? regionFiles.length : 0;
        labelRegionFiles.setText(String.valueOf(regionCount));

        if (regionCount == 0) {
            labelChunkCount.setText("0");
            labelWorldBounds.setText("\u2014");
            return;
        }

        // Analyze chunks
        ProgressDialog.executeTask(this, new ProgressTask<Void>() {
            @Override public String getName() { return "Analyzing Hytale world..."; }
            @Override public Void execute(ProgressReceiver pr) throws ProgressReceiver.OperationCancelled {
                try (ChunkStore store = new HytaleChunkStore(worldDir, 0, HytaleChunk.DEFAULT_MAX_HEIGHT)) {
                    Set<MinecraftCoords> coords = store.getChunkCoords();
                    final NumberFormat fmt = NumberFormat.getIntegerInstance();
                    int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
                    int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
                    for (MinecraftCoords c : coords) {
                        if (c.x < minX) minX = c.x;
                        if (c.x > maxX) maxX = c.x;
                        if (c.z < minZ) minZ = c.z;
                        if (c.z > maxZ) maxZ = c.z;
                    }
                    final int fMinX = minX, fMaxX = maxX, fMinZ = minZ, fMaxZ = maxZ;
                    final int chunkCount = coords.size();
                    SwingUtilities.invokeLater(() -> {
                        labelChunkCount.setText(fmt.format(chunkCount));
                        labelWorldBounds.setText(fmt.format(fMinX * 32) + "," + fmt.format(fMinZ * 32)
                            + " to " + fmt.format((fMaxX + 1) * 32) + "," + fmt.format((fMaxZ + 1) * 32));
                        buttonImport.setEnabled(chunkCount > 0);
                    });
                }
                return null;
            }
        });
    }

    private void doImport() {
        app.clearWorld();
        final MapImporter.ReadOnlyOption readOnlyOpt;
        if (radioReadOnlyAll.isSelected()) {
            readOnlyOpt = MapImporter.ReadOnlyOption.ALL;
        } else if (radioReadOnlyManMade.isSelected()) {
            readOnlyOpt = MapImporter.ReadOnlyOption.MAN_MADE;
        } else if (radioReadOnlyManMadeAboveGround.isSelected()) {
            readOnlyOpt = MapImporter.ReadOnlyOption.MAN_MADE_ABOVE_GROUND;
        } else {
            readOnlyOpt = MapImporter.ReadOnlyOption.NONE;
        }

        importedWorld = ProgressDialog.executeTask(this, new ProgressTask<World2>() {
            @Override public String getName() { return "Importing Hytale world..."; }
            @Override public World2 execute(ProgressReceiver pr) throws ProgressReceiver.OperationCancelled {
                try {
                    TileFactory tileFactory = TileFactoryFactory.createNoiseTileFactory(
                        0, Terrain.GRASS, 0, HytaleChunk.DEFAULT_MAX_HEIGHT, 58, 62, false, true, 20, 1.0);
                    HytaleMapImporter importer = new HytaleMapImporter(
                        worldDir, tileFactory, null, readOnlyOpt);
                    World2 world = importer.doImport(pr);
                    if (importer.getWarnings() != null) {
                        try {
                            SwingUtilities.invokeAndWait(() -> {
                                java.awt.Toolkit.getDefaultToolkit().beep();
                                ImportWarningsDialog warningsDialog = new ImportWarningsDialog(
                                    HytaleMapImportDialog.this,
                                    "Hytale Import Warnings",
                                    "The import process generated warnings. Some chunks may not have been imported correctly.");
                                warningsDialog.setWarnings(importer.getWarnings());
                                warningsDialog.setVisible(true);
                            });
                        } catch (java.lang.reflect.InvocationTargetException | InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    return world;
                } catch (IOException e) {
                    throw new RuntimeException("I/O error during Hytale import", e);
                }
            }
        });

        if (importedWorld != null) {
            ok();
        }
    }
}
