/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.pepsoft.worldpainter.layers.bo2;

import org.pepsoft.minecraft.Material;
import org.pepsoft.util.DesktopUtils;
import org.pepsoft.worldpainter.App;
import org.pepsoft.worldpainter.ColourScheme;
import org.pepsoft.worldpainter.Configuration;
import org.pepsoft.worldpainter.DefaultPlugin;
import org.pepsoft.worldpainter.Platform;
import org.pepsoft.worldpainter.WorldPainterDialog;
import org.pepsoft.worldpainter.hytale.HytaleBlockPalette;
import org.pepsoft.worldpainter.hytale.HytaleBlockRegistry;
import org.pepsoft.worldpainter.layers.AbstractLayerEditor;
import org.pepsoft.worldpainter.layers.Bo2Layer;
import org.pepsoft.worldpainter.layers.exporters.ExporterSettings;
import org.pepsoft.worldpainter.objects.WPObject;
import org.pepsoft.worldpainter.plugins.CustomObjectManager;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.vecmath.Point3i;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.NumberFormat;
import java.util.List;
import java.util.*;

import static java.lang.Math.round;
import static java.lang.String.format;
import static org.pepsoft.minecraft.Material.PERSISTENT;
import static org.pepsoft.util.swing.MessageUtils.*;
import static org.pepsoft.worldpainter.ExceptionHandler.doWithoutExceptionReporting;
import static org.pepsoft.worldpainter.Platform.Capability.NAME_BASED;
import static org.pepsoft.worldpainter.objects.WPObject.*;

/**
 *
 * @author Pepijn Schmitz
 */
@SuppressWarnings({"unused", "FieldCanBeLocal"}) // Managed by NetBeans
public class Bo2LayerEditor extends AbstractLayerEditor<Bo2Layer> implements ListSelectionListener, DocumentListener {
    /**
     * Creates new form Bo2LayerEditor
     */
    public Bo2LayerEditor() {
        initComponents();
        
        listModel = new DefaultListModel<>();
        listObjects.setModel(listModel);
        listObjects.setCellRenderer(new WPObjectListCellRenderer());
        
        listObjects.getSelectionModel().addListSelectionListener(this);
        fieldName.getDocument().addDocumentListener(this);

        updateBlocksPerAttempt();
    }

    // LayerEditor
    
    @Override
    public Bo2Layer createLayer() {
        return new Bo2Layer(new Bo2ObjectTube("My Custom Objects", Collections.emptyList()), "Custom (e.g. bo2, bo3, nbt, schem and/or schematic) objects", Color.ORANGE);
    }

    @Override
    public void setLayer(Bo2Layer layer) {
        super.setLayer(layer);
        reset();
    }

    @Override
    public void commit() {
        if (! isCommitAvailable()) {
            throw new IllegalStateException("Settings invalid or incomplete");
        }
        saveSettings(layer);
    }
    
    @Override
    public void reset() {
        List<WPObject> objects = new ArrayList<>();
        fieldName.setText(layer.getName());
        paintPicker1.setPaint(layer.getPaint());
        paintPicker1.setOpacity(layer.getOpacity());
        List<File> files = layer.getFiles();
        if (files != null) {
            if (files.isEmpty()) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Existing layer contains new style objects");
                }
                // New layer; files stored in object attributes
                objects.addAll(layer.getObjectProvider().getAllObjects());
            } else {
                // Old layer; files stored separately
                int missingFiles = 0;
                CustomObjectManager customObjectManager = CustomObjectManager.getInstance();
                if ((files.size() == 1) && files.get(0).isDirectory()) {
                    logger.info("Existing custom object layer contains old style directory; migrating to new style");
                    File[] filesInDir = files.get(0).listFiles((FilenameFilter) CustomObjectManager.getInstance().getFileFilter());
                    //noinspection ConstantConditions // Cannot happen as we already checked that files.get(0) is an extant directory
                    for (File file: filesInDir) {
                        try {
                            objects.add(customObjectManager.loadObject(file));
                        } catch (IOException e) {
                            logger.error("I/O error while trying to load custom object " + file, e);
                            missingFiles++;
                        }
                    }
                } else {
                    logger.info("Existing custom object layer contains old style file list; migrating to new style");
                    for (File file: files) {
                        if (file.exists()) {
                            try {
                                objects.add(customObjectManager.loadObject(file));
                            } catch (IOException e) {
                                logger.error("I/O error while trying to load custom object " + file, e);
                                missingFiles++;
                            }
                        } else {
                            missingFiles++;
                        }
                    }
                }
                if (missingFiles > 0) {
                    showWarning(this, "This is an old custom object layer and " + missingFiles + " objects\ncould NOT be restored because they were missing or\nreading them resulted in an I/O error.\n\nYou will have to re-add these objects before\nsaving the settings, otherwise the existing object\ndata will be gone. You may also cancel the dialog\nwithout affecting the object data.", "Missing Files");
                }
            }
        } else {
            logger.info("Existing custom object layer contains very old style objects with no file information; migrating to new style");
            // Very old layer; no file information at all
            objects.addAll(layer.getObjectProvider().getAllObjects());
        }
        allObjects.clear();
        listModel.clear();
        for (WPObject object: objects) {
            WPObject clone = object.clone();
            allObjects.add(clone);
            listModel.addElement(clone);
        }
        spinnerBlocksPerAttempt.setValue(layer.getDensity());
        spinnerGrid.setValue(layer.getGridX());
        spinnerRandomOffset.setValue(layer.getRandomDisplacement());
        refreshLeafDecaySettings();
        updateCategories();
        restoreLayerBackedControls();
        
        settingsChanged();
    }

    @Override
    public ExporterSettings getSettings() {
        if (! isCommitAvailable()) {
            throw new IllegalStateException("Settings invalid or incomplete");
        }
        final Bo2Layer previewLayer = saveSettings(null);
        return new ExporterSettings() {
            @Override
            public boolean isApplyEverywhere() {
                return false;
            }

            @Override
            public Bo2Layer getLayer() {
                return previewLayer;
            }

            @Override
            public ExporterSettings clone() {
                throw new UnsupportedOperationException("Not supported");
            }
        };
    }

    @Override
    public boolean isCommitAvailable() {
        boolean filesSelected = !allObjects.isEmpty();
        boolean nameSpecified = fieldName.getText().trim().length() > 0;
        return filesSelected && nameSpecified;
    }

    @Override
    public void setContext(LayerEditorContext context) {
        super.setContext(context);
        colourScheme = context.getColourScheme();
        try {
            isHytaleWorld = context.getDimension().getWorld().getPlatform() == DefaultPlugin.HYTALE;
        } catch (Exception e) {
            isHytaleWorld = false;
        }
    }

    @Override
    public JComponent getComponent() {
        if (wrapperPanel == null) {
            wrapperPanel = new JPanel(new BorderLayout(0, 5));

            // Category filter at the top
            JPanel categoryPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
            labelCategory = new JLabel("Category:");
            comboBoxCategory = new JComboBox<>();
            comboBoxCategory.addItem(ALL_CATEGORIES);
            comboBoxCategory.addActionListener(e -> filterByCategory());
            categoryPanel.add(labelCategory);
            categoryPanel.add(comboBoxCategory);
            wrapperPanel.add(categoryPanel, BorderLayout.NORTH);

            wrapperPanel.add(this, BorderLayout.CENTER);

            // Bottom panel: no-physics checkbox + optional block mapping
            JPanel bottomPanel = new JPanel();
            bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));

            checkBoxNoPhysics = new JCheckBox("Disable physics (force-place all blocks regardless of terrain)");
            checkBoxNoPhysics.setToolTipText("When enabled, objects are placed at their calculated position without foundation or collision checks");
            checkBoxNoPhysics.addActionListener(e -> settingsChanged());
            JPanel checkPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
            checkPanel.add(checkBoxNoPhysics);
            bottomPanel.add(checkPanel);

            if (isHytaleWorld) {
                blockMappingPanel = createBlockMappingPanel();
                bottomPanel.add(blockMappingPanel);
            }

            wrapperPanel.add(bottomPanel, BorderLayout.SOUTH);
            restoreLayerBackedControls();
        }
        return wrapperPanel;
    }
        
    // ListSelectionListener
    
    @Override
    public void valueChanged(ListSelectionEvent e) {
        settingsChanged();
    }

    // DocumentListener
    
    @Override
    public void insertUpdate(DocumentEvent e) {
        settingsChanged();
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        settingsChanged();
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        settingsChanged();
    }

    private Bo2Layer saveSettings(Bo2Layer layer) {
        String name = fieldName.getText();
        // Use allObjects (unfiltered master list) for saving
        List<WPObject> objects = new ArrayList<>(allObjects);
        Bo2ObjectProvider objectProvider = new Bo2ObjectTube(name, objects);
        if (layer == null) {
            layer = new Bo2Layer(objectProvider, "Custom (e.g. bo2, bo3 and/or schematic) objects", paintPicker1.getPaint());
        } else {
            layer.setObjectProvider(objectProvider);
            layer.setPaint(paintPicker1.getPaint());
        }
        layer.setOpacity(paintPicker1.getOpacity());
        layer.setDensity((Integer) spinnerBlocksPerAttempt.getValue());
        layer.setGridX((Integer) spinnerGrid.getValue());
        layer.setGridY((Integer) spinnerGrid.getValue());
        layer.setRandomDisplacement((Integer) spinnerRandomOffset.getValue());
        if (checkBoxNoPhysics != null) {
            layer.setNoPhysics(checkBoxNoPhysics.isSelected());
        }
        if (isHytaleWorld && blockMappingTableModel != null) {
            layer.setHytaleBlockMappings(collectMappingsFromTable());
        }
        return layer;
    }

    private void settingsChanged() {
        setControlStates();
        context.settingsChanged();
    }

    private void restoreLayerBackedControls() {
        if (checkBoxNoPhysics != null) {
            checkBoxNoPhysics.setSelected((layer != null) && layer.isNoPhysics());
        }
        if (isHytaleWorld && (blockMappingTableModel != null)) {
            populateBlockMappings();
        }
    }
    
    private void setControlStates() {
        boolean filesSelected = !allObjects.isEmpty();
        boolean objectsSelected = listObjects.getSelectedIndex() != -1;
        buttonRemoveFile.setEnabled(objectsSelected);
        buttonReloadAll.setEnabled(filesSelected);
        buttonEdit.setEnabled(objectsSelected);
    }
    
    private void addFilesOrDirectory() {
        // Can't use FileUtils.selectFilesForOpen() because it doesn't support
        // selecting directories, or adding custom components to the dialog
        JFileChooser fileChooser = new JFileChooser();
        Configuration config = Configuration.getInstance();
        if ((config.getCustomObjectsDirectory() != null) && config.getCustomObjectsDirectory().isDirectory()) {
            fileChooser.setCurrentDirectory(config.getCustomObjectsDirectory());
        }
        fileChooser.setDialogTitle("Select File(s) or Directory");
        fileChooser.setMultiSelectionEnabled(true);
        fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        CustomObjectManager.UniversalFileFilter fileFilter = CustomObjectManager.getInstance().getFileFilter();
        fileChooser.setFileFilter(fileFilter);
        WPObjectPreviewer previewer = new WPObjectPreviewer();
        previewer.setDimension(App.getInstance().getDimension());
        fileChooser.addPropertyChangeListener(JFileChooser.SELECTED_FILE_CHANGED_PROPERTY, previewer);
        fileChooser.setAccessory(previewer);
        if (doWithoutExceptionReporting(() -> fileChooser.showOpenDialog(this)) == JFileChooser.APPROVE_OPTION) {
            File[] selectedFiles = fileChooser.getSelectedFiles();
            if (selectedFiles.length > 0) {
                Platform platform = context.getDimension().getWorld().getPlatform();
                boolean checkForNameOnlyMaterials = ! platform.capabilities.contains(NAME_BASED);
                Set<String> nameOnlyMaterialsNames = checkForNameOnlyMaterials ? new HashSet<>() : null;
                config.setCustomObjectsDirectory(selectedFiles[0].getParentFile());
                for (File selectedFile: selectedFiles) {
                    if (selectedFile.isDirectory()) {
                        if (fieldName.getText().isEmpty()) {
                            String name = selectedFiles[0].getName();
                            if (name.length() > 12) {
                                name = "..." + name.substring(name.length() - 10);
                            }
                            fieldName.setText(name);
                        }
                        File[] files = selectedFile.listFiles((FilenameFilter) fileFilter);
                        if (files == null) {
                            beepAndShowError(this, selectedFile.getName() + " is not a directory or it cannot be read.", "Not A Valid Directory");
                        } else if (files.length == 0) {
                            beepAndShowError(this, "Directory " + selectedFile.getName() + " does not contain any supported custom object files.", "No Custom Object Files");
                        } else {
                            for (File file: files) {
                                addFile(checkForNameOnlyMaterials, nameOnlyMaterialsNames, file);
                            }
                        }
                    } else {
                        if (fieldName.getText().isEmpty()) {
                            String name = selectedFile.getName();
                            int p = name.lastIndexOf('.');
                            if (p != -1) {
                                name = name.substring(0, p);
                            }
                            if (name.length() > 12) {
                                name = "..." + name.substring(name.length() - 10);
                            }
                            fieldName.setText(name);
                        }
                        addFile(checkForNameOnlyMaterials, nameOnlyMaterialsNames, selectedFile);
                    }
                }
                settingsChanged();
                refreshLeafDecaySettings();
                updateCategories();
                if (isHytaleWorld) {
                    populateBlockMappings();
                }
                if (checkForNameOnlyMaterials && (! nameOnlyMaterialsNames.isEmpty())) {
                    String message;
                    if (nameOnlyMaterialsNames.size() > 4) {
                        message = format("One or more added objects contain block types that are\n" +
                                "incompatible with the current map format (%s):\n" +
                                "%s and %d more\n" +
                                "You will not be able to export this world in this format if you use this layer.",
                                platform.displayName, String.join(", ", new ArrayList<>(nameOnlyMaterialsNames).subList(0, 3)),
                                nameOnlyMaterialsNames.size() - 3);
                    } else {
                        message = format("One or more added objects contain block types that are\n" +
                                "incompatible with the current map format (%s):\n" +
                                "%s\n" +
                                "You will not be able to export this world in this format if you use this layer.",
                                platform.displayName, String.join(", ", nameOnlyMaterialsNames));
                    }
                    beepAndShowWarning(this, message, "Map Format Not Compatible");
                }
            }
        }
    }

    private void addFile(boolean checkForNameOnlyMaterials, Set<String> nameOnlyMaterialsNames, File file) {
        try {
            WPObject object = CustomObjectManager.getInstance().loadObject(file);
            if (checkForNameOnlyMaterials) {
                Set<String> materialNamesEncountered = new HashSet<>();
                object.visitBlocks((o, x, y, z, material) -> {
                    if (! materialNamesEncountered.contains(material.name)) {
                        materialNamesEncountered.add(material.name);
                        if (material.blockType == -1) {
                            nameOnlyMaterialsNames.add(material.name);
                        }
                    }
                    return true;
                });
            }
            allObjects.add(object);
            listModel.addElement(object);
        } catch (IllegalArgumentException e) {
            logger.error("IllegalArgumentException while trying to load custom object " + file, e);
            JOptionPane.showMessageDialog(this, e.getMessage() + " while loading " + file.getName() + "; it was not added", "Illegal Argument", JOptionPane.ERROR_MESSAGE);
        } catch (IOException e) {
            logger.error("I/O error while trying to load custom object " + file, e);
            JOptionPane.showMessageDialog(this, "I/O error while loading " + file.getName() + "; it was not added", "I/O Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void removeFiles() {
        int[] selectedIndices = listObjects.getSelectedIndices();
        for (int i = selectedIndices.length - 1; i >= 0; i--) {
            WPObject removed = listModel.getElementAt(selectedIndices[i]);
            listModel.removeElementAt(selectedIndices[i]);
            allObjects.remove(removed);
        }
        settingsChanged();
        refreshLeafDecaySettings();
        updateCategories();
        if (isHytaleWorld) {
            populateBlockMappings();
        }
    }

    private void reloadObjects() {
        StringBuilder noFiles = new StringBuilder();
        StringBuilder notFound = new StringBuilder();
        StringBuilder errors = new StringBuilder();
        int[] indices;
        if (listObjects.getSelectedIndex() != -1) {
            indices = listObjects.getSelectedIndices();
        } else {
            indices = new int[listModel.getSize()];
            for (int i = 0; i < indices.length; i++) {
                indices[i] = i;
            }
        }
        CustomObjectManager customObjectManager = CustomObjectManager.getInstance();
        for (int index: indices) {
            WPObject object = listModel.getElementAt(index);
            File file = object.getAttribute(ATTRIBUTE_FILE);
            if (file != null) {
                if (file.isFile() && file.canRead()) {
                    try {
                        Map<String, Serializable> existingAttributes = object.getAttributes();
                        object = customObjectManager.loadObject(file);
                        if (existingAttributes != null) {
                            Map<String, Serializable> attributes = object.getAttributes();
                            if (attributes == null) {
                                attributes = new HashMap<>();
                            }
                            attributes.putAll(existingAttributes);
                            object.setAttributes(attributes);
                        }
                        WPObject oldObject = listModel.getElementAt(index);
                        int allIdx = allObjects.indexOf(oldObject);
                        listModel.setElementAt(object, index);
                        if (allIdx >= 0) {
                            allObjects.set(allIdx, object);
                        }
                    } catch (IOException e) {
                        logger.error("I/O error while reloading " + file, e);
                        errors.append(file.getPath()).append('\n');
                    }
                } else {
                    notFound.append(file.getPath()).append('\n');
                }
            } else {
                noFiles.append(object.getName()).append('\n');
            }
        }
        if ((noFiles.length() > 0) || (notFound.length() > 0) || (errors.length() > 0)) {
            StringBuilder message = new StringBuilder();
            message.append("Not all files could be reloaded!\n");
            if (noFiles.length() > 0) {
                message.append("\nThe following objects came from an old layer and have no filename stored:\n");
                message.append(noFiles);
            }
            if (notFound.length() > 0) {
                message.append("\nThe following files were missing or not accessible:\n");
                message.append(notFound);
            }
            if (errors.length() > 0) {
                message.append("\nThe following files experienced I/O errors while loading:\n");
                message.append(errors);
            }
            JOptionPane.showMessageDialog(this, message, "Not All Files Reloaded", JOptionPane.ERROR_MESSAGE);
        } else {
            showInfo(this, indices.length + " objects successfully reloaded", "Success");
        }
        refreshLeafDecaySettings();
    }
    
    private void editObjects() {
        List<WPObject> selectedObjects = new ArrayList<>(listObjects.getSelectedIndices().length);
        int[] selectedIndices = listObjects.getSelectedIndices();
        for (int i = selectedIndices.length - 1; i >= 0; i--) {
            selectedObjects.add(listModel.getElementAt(selectedIndices[i]));
        }
        EditObjectAttributes dialog = new EditObjectAttributes(SwingUtilities.getWindowAncestor(this), selectedObjects, colourScheme);
        dialog.setVisible(true);
        if (! dialog.isCancelled()) {
            settingsChanged();
            refreshLeafDecaySettings();
        }
    }

    private void refreshLeafDecaySettings() {
        if (listModel.isEmpty()) {
            labelLeafDecayTitle.setEnabled(false);
            labelEffectiveLeafDecaySetting.setEnabled(false);
            labelEffectiveLeafDecaySetting.setText("N/A");
            buttonSetDecay.setEnabled(false);
            buttonSetNoDecay.setEnabled(false);
            buttonReset.setEnabled(false);
            return;
        }
        boolean decayingLeavesFound = false;
        boolean nonDecayingLeavesFound = false;
        outer:
        for (Enumeration<WPObject> e = listModel.elements(); e.hasMoreElements(); ) {
            WPObject object = e.nextElement();
            int leafDecayMode = object.getAttribute(ATTRIBUTE_LEAF_DECAY_MODE);
            switch (leafDecayMode) {
                case LEAF_DECAY_NO_CHANGE:
                    // Leaf decay attribute not set (or set to "no change"); examine actual blocks
                    object.prepareForExport(context.getDimension());
                    Point3i dim = object.getDimensions();
                    for (int x = 0; x < dim.x; x++) {
                        for (int y = 0; y < dim.y; y++) {
                            for (int z = 0; z < dim.z; z++) {
                                if (object.getMask(x, y, z)) {
                                    final Material material = object.getMaterial(x, y, z);
                                    if (material.leafBlock) {
                                        if (material.is(PERSISTENT)) {
                                            // Non decaying leaf block
                                            nonDecayingLeavesFound = true;
                                            if (decayingLeavesFound) {
                                                // We have enough information; no reason to continue the examination
                                                break outer;
                                            }
                                        } else {
                                            // Decaying leaf block
                                            decayingLeavesFound = true;
                                            if (nonDecayingLeavesFound) {
                                                // We have enough information; no reason to continue the examination
                                                break outer;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    break;
                case LEAF_DECAY_OFF:
                    // Leaf decay attribute set to "off"; don't examine blocks for performance (even though this could
                    // lead to misleading information if the object doesn't contain any leaf blocks)
                    nonDecayingLeavesFound = true;
                    if (decayingLeavesFound) {
                        // We have enough information; no reason to continue the examination
                        break outer;
                    }
                    break;
                case LEAF_DECAY_ON:
                    // Leaf decay attribute set to "off"; don't examine blocks for performance (even though this could
                    // lead to misleading information if the object doesn't contain any leaf blocks)
                    decayingLeavesFound = true;
                    if (nonDecayingLeavesFound) {
                        // We have enough information; no reason to continue the examination
                        break outer;
                    }
                    break;
                default:
                    throw new InternalError();
            }
        }

        if (decayingLeavesFound) {
            labelLeafDecayTitle.setEnabled(true);
            labelEffectiveLeafDecaySetting.setEnabled(true);
            buttonSetNoDecay.setEnabled(true);
            buttonReset.setEnabled(true);
            if (nonDecayingLeavesFound) {
                // Both decaying and non decaying leaves found
                labelEffectiveLeafDecaySetting.setText("<html>Decaying <i>and</i> non decaying leaves.</html>");
                buttonSetDecay.setEnabled(true);
            } else {
                // Only decaying leaves found
                labelEffectiveLeafDecaySetting.setText("<html>Leaves <b>do</b> decay.</html>");
                buttonSetDecay.setEnabled(false);
            }
        } else {
            if (nonDecayingLeavesFound) {
                // Only non decaying leaves found
                labelLeafDecayTitle.setEnabled(true);
                labelEffectiveLeafDecaySetting.setEnabled(true);
                labelEffectiveLeafDecaySetting.setText("<html>Leaves do <b>not</b> decay.</html>");
                buttonSetDecay.setEnabled(true);
                buttonSetNoDecay.setEnabled(false);
                buttonReset.setEnabled(true);
            } else {
                // No leaf blocks encountered at all, so N/A
                labelLeafDecayTitle.setEnabled(false);
                labelEffectiveLeafDecaySetting.setEnabled(false);
                labelEffectiveLeafDecaySetting.setText("N/A");
                buttonSetDecay.setEnabled(false);
                buttonSetNoDecay.setEnabled(false);
                buttonReset.setEnabled(false);
            }
        }
    }

    private void setLeavesDecay() {
        for (Enumeration<WPObject> e = listModel.elements(); e.hasMoreElements(); ) {
            WPObject object = e.nextElement();
            object.setAttribute(ATTRIBUTE_LEAF_DECAY_MODE, LEAF_DECAY_ON);
        }
        refreshLeafDecaySettings();
    }

    private void setLeavesNoDecay() {
        for (Enumeration<WPObject> e = listModel.elements(); e.hasMoreElements(); ) {
            WPObject object = e.nextElement();
            object.setAttribute(ATTRIBUTE_LEAF_DECAY_MODE, LEAF_DECAY_OFF);
        }
        refreshLeafDecaySettings();
    }

    private void resetLeafDecay() {
        for (Enumeration<WPObject> e = listModel.elements(); e.hasMoreElements(); ) {
            WPObject object = e.nextElement();
            object.getAttributes().remove(ATTRIBUTE_LEAF_DECAY_MODE.key);
        }
        refreshLeafDecaySettings();
    }

    private void updateBlocksPerAttempt() {
        final int grid = (Integer) spinnerGrid.getValue();
        final float blocksAt50 = (float) ((Integer) spinnerBlocksPerAttempt.getValue()) * grid * grid;
        final float blocksAt1 = blocksAt50 * 64, blocksAt100 = round(blocksAt50 / 3.515625f);
        labelBlocksPerAttempt.setText(format("one per %d blocks at 1%%; %d blocks at 50%%; %d blocks at 100%%)",
                round(blocksAt1),
                round(blocksAt50),
                round((blocksAt100 <= 1) ? 1 : blocksAt100)));
    }

    // --- Category filter support ---

    /**
     * Extract a category string from a WPObject. Checks for "[Category]" in
     * the object name first, then falls back to the parent directory name of
     * the source file.
     */
    static String extractCategory(WPObject object) {
        String name = object.getName();
        if (name != null) {
            int open = name.indexOf('[');
            int close = name.indexOf(']', open + 1);
            if (open >= 0 && close > open) {
                return name.substring(open + 1, close);
            }
        }
        File file = object.getAttribute(ATTRIBUTE_FILE);
        if (file != null) {
            File parent = file.getParentFile();
            if (parent != null) {
                return parent.getName();
            }
        }
        return UNCATEGORIZED;
    }

    /**
     * Rebuild the category combo box from the current allObjects list.
     */
    private void updateCategories() {
        if (comboBoxCategory == null) {
            return;
        }
        String previousSelection = (String) comboBoxCategory.getSelectedItem();
        comboBoxCategory.removeAllItems();
        comboBoxCategory.addItem(ALL_CATEGORIES);
        Set<String> categories = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (WPObject obj : allObjects) {
            categories.add(extractCategory(obj));
        }
        for (String cat : categories) {
            comboBoxCategory.addItem(cat);
        }
        if (previousSelection != null) {
            comboBoxCategory.setSelectedItem(previousSelection);
            if (comboBoxCategory.getSelectedItem() == null || !comboBoxCategory.getSelectedItem().equals(previousSelection)) {
                comboBoxCategory.setSelectedItem(ALL_CATEGORIES);
            }
        }
    }

    /**
     * Filter the displayed list to only show objects matching the selected category.
     */
    private void filterByCategory() {
        if (comboBoxCategory == null) {
            return;
        }
        String selected = (String) comboBoxCategory.getSelectedItem();
        listModel.clear();
        for (WPObject obj : allObjects) {
            if (ALL_CATEGORIES.equals(selected) || extractCategory(obj).equalsIgnoreCase(selected)) {
                listModel.addElement(obj);
            }
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        buttonReloadAll = new javax.swing.JButton();
        jSeparator2 = new javax.swing.JSeparator();
        buttonEdit = new javax.swing.JButton();
        jPanel3 = new javax.swing.JPanel();
        labelLeafDecayTitle = new javax.swing.JLabel();
        labelEffectiveLeafDecaySetting = new javax.swing.JLabel();
        buttonSetDecay = new javax.swing.JButton();
        buttonSetNoDecay = new javax.swing.JButton();
        buttonReset = new javax.swing.JButton();
        jScrollPane1 = new javax.swing.JScrollPane();
        listObjects = new javax.swing.JList<>();
        jLabel6 = new javax.swing.JLabel();
        jLabel1 = new javax.swing.JLabel();
        jPanel2 = new javax.swing.JPanel();
        jLabel3 = new javax.swing.JLabel();
        fieldName = new javax.swing.JTextField();
        jLabel4 = new javax.swing.JLabel();
        paintPicker1 = new org.pepsoft.worldpainter.layers.renderers.PaintPicker();
        jLabel2 = new javax.swing.JLabel();
        buttonAddFile = new javax.swing.JButton();
        buttonRemoveFile = new javax.swing.JButton();
        jLabel7 = new javax.swing.JLabel();
        spinnerBlocksPerAttempt = new javax.swing.JSpinner();
        labelBlocksPerAttempt = new javax.swing.JLabel();
        jLabel10 = new javax.swing.JLabel();
        jLabel5 = new javax.swing.JLabel();
        spinnerGrid = new javax.swing.JSpinner();
        jLabel8 = new javax.swing.JLabel();
        jLabel9 = new javax.swing.JLabel();
        spinnerRandomOffset = new javax.swing.JSpinner();
        jLabel11 = new javax.swing.JLabel();
        jLabel12 = new javax.swing.JLabel();
        jLabel13 = new javax.swing.JLabel();
        jLabel14 = new javax.swing.JLabel();

        buttonReloadAll.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/pepsoft/worldpainter/icons/arrow_rotate_clockwise.png"))); // NOI18N
        buttonReloadAll.setToolTipText("Reload all or selected objects from disk");
        buttonReloadAll.setEnabled(false);
        buttonReloadAll.setMargin(new java.awt.Insets(2, 2, 2, 2));
        buttonReloadAll.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonReloadAllActionPerformed(evt);
            }
        });

        jSeparator2.setOrientation(javax.swing.SwingConstants.VERTICAL);

        buttonEdit.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/pepsoft/worldpainter/icons/brick_edit.png"))); // NOI18N
        buttonEdit.setToolTipText("Edit selected object(s) options");
        buttonEdit.setEnabled(false);
        buttonEdit.setMargin(new java.awt.Insets(2, 2, 2, 2));
        buttonEdit.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonEditActionPerformed(evt);
            }
        });

        labelLeafDecayTitle.setText("Leaf decay settings for these objects:");

        labelEffectiveLeafDecaySetting.setText("<html>Leaves do <b>not</b> decay.</html>");
        labelEffectiveLeafDecaySetting.setEnabled(false);

        buttonSetDecay.setText("Set all to decay");
        buttonSetDecay.setToolTipText("Set all objects to decaying leaves");
        buttonSetDecay.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonSetDecayActionPerformed(evt);
            }
        });

        buttonSetNoDecay.setText("<html>Set all to <b>not</b> decay</html>");
        buttonSetNoDecay.setToolTipText("Set all objects to non decaying leaves");
        buttonSetNoDecay.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonSetNoDecayActionPerformed(evt);
            }
        });

        buttonReset.setText("Reset");
        buttonReset.setToolTipText("Reset leaf decay to object defaults");
        buttonReset.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonResetActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(labelEffectiveLeafDecaySetting, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(labelLeafDecayTitle)
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addComponent(buttonSetDecay)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(buttonSetNoDecay, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(buttonReset)))
                .addGap(0, 0, Short.MAX_VALUE))
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addComponent(labelLeafDecayTitle)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(labelEffectiveLeafDecaySetting, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(buttonSetDecay)
                    .addComponent(buttonSetNoDecay, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(buttonReset)))
        );

        listObjects.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                listObjectsMouseClicked(evt);
            }
        });
        jScrollPane1.setViewportView(listObjects);

        jLabel6.setForeground(new java.awt.Color(0, 0, 255));
        jLabel6.setText("<html><u>Get custom objects</u></html>");
        jLabel6.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        jLabel6.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                jLabel6MouseClicked(evt);
            }
        });

        jLabel1.setText("Define your custom object layer on this screen.");

        jLabel3.setText("Name:");

        fieldName.setColumns(15);

        jLabel4.setText("Paint:");

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel3)
                    .addComponent(jLabel4))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(paintPicker1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(fieldName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel3)
                    .addComponent(fieldName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel4)
                    .addComponent(paintPicker1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
        );

        jLabel2.setText("Object(s):");

        buttonAddFile.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/pepsoft/worldpainter/icons/brick_add.png"))); // NOI18N
        buttonAddFile.setToolTipText("Add one or more objects");
        buttonAddFile.setMargin(new java.awt.Insets(2, 2, 2, 2));
        buttonAddFile.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonAddFileActionPerformed(evt);
            }
        });

        buttonRemoveFile.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/pepsoft/worldpainter/icons/brick_delete.png"))); // NOI18N
        buttonRemoveFile.setToolTipText("Remove selected object(s)");
        buttonRemoveFile.setEnabled(false);
        buttonRemoveFile.setMargin(new java.awt.Insets(2, 2, 2, 2));
        buttonRemoveFile.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                buttonRemoveFileActionPerformed(evt);
            }
        });

        jLabel7.setText("Spawn chance:");

        spinnerBlocksPerAttempt.setModel(new javax.swing.SpinnerNumberModel(20, 1, 99999, 1));
        spinnerBlocksPerAttempt.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                spinnerBlocksPerAttemptStateChanged(evt);
            }
        });

        labelBlocksPerAttempt.setText("one per x blocks at 1%; y blocks at 50%; z blocks at 100%)");

        jLabel10.setText("one in");

        jLabel5.setText("Grid:");

        spinnerGrid.setModel(new javax.swing.SpinnerNumberModel(1, 1, 999, 1));
        spinnerGrid.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                spinnerGridStateChanged(evt);
            }
        });

        jLabel8.setText("(at 50%)");

        jLabel9.setText("Random offset:");

        spinnerRandomOffset.setModel(new javax.swing.SpinnerNumberModel(0, 0, 999, 1));
        spinnerRandomOffset.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                spinnerRandomOffsetStateChanged(evt);
            }
        });

        jLabel11.setText("block(s)");

        jLabel12.setText("Effective density:");

        jLabel13.setText("(objects displaced in a random direction up to this distance)");

        jLabel14.setText("block(s)");

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jScrollPane1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(buttonAddFile, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(buttonRemoveFile, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(buttonEdit, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(buttonReloadAll, javax.swing.GroupLayout.Alignment.TRAILING)))
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel2)
                    .addComponent(jLabel1)
                    .addComponent(jLabel6, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jSeparator2, javax.swing.GroupLayout.PREFERRED_SIZE, 2, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel7)
                            .addComponent(jLabel5)
                            .addComponent(jLabel9)
                            .addComponent(jLabel12))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(spinnerRandomOffset, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jLabel11)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jLabel13))
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(spinnerGrid, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jLabel14))
                            .addComponent(labelBlocksPerAttempt)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(jLabel10)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(spinnerBlocksPerAttempt, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jLabel8)))))
                .addGap(0, 0, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel6, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(jLabel2)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(buttonAddFile)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(buttonRemoveFile)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(buttonEdit)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(buttonReloadAll)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE))
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel5)
                    .addComponent(spinnerGrid, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel14))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel7)
                    .addComponent(spinnerBlocksPerAttempt, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel10)
                    .addComponent(jLabel8))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(labelBlocksPerAttempt)
                    .addComponent(jLabel12))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel9)
                    .addComponent(spinnerRandomOffset, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel11)
                    .addComponent(jLabel13))
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jSeparator2)
                    .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void buttonReloadAllActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonReloadAllActionPerformed
        reloadObjects();
    }//GEN-LAST:event_buttonReloadAllActionPerformed

    private void buttonEditActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonEditActionPerformed
        editObjects();
    }//GEN-LAST:event_buttonEditActionPerformed

    private void buttonSetDecayActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonSetDecayActionPerformed
        setLeavesDecay();
    }//GEN-LAST:event_buttonSetDecayActionPerformed

    private void buttonSetNoDecayActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonSetNoDecayActionPerformed
        setLeavesNoDecay();
    }//GEN-LAST:event_buttonSetNoDecayActionPerformed

    private void buttonResetActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonResetActionPerformed
        resetLeafDecay();
    }//GEN-LAST:event_buttonResetActionPerformed

    private void listObjectsMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_listObjectsMouseClicked
        if (evt.getClickCount() == 2) {
            int row = listObjects.getSelectedIndex();
            if (row != -1) {
                WPObject object = listModel.getElementAt(row);
                EditObjectAttributes dialog = new EditObjectAttributes(SwingUtilities.getWindowAncestor(this), object, colourScheme);
                dialog.setVisible(true);
                if (! dialog.isCancelled()) {
                    refreshLeafDecaySettings();
                }
            }
        }
    }//GEN-LAST:event_listObjectsMouseClicked

    private void jLabel6MouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_jLabel6MouseClicked
        try {
            DesktopUtils.open(new URL("https://discord.gg/rNk5yN89"));
        } catch (MalformedURLException e) {
            throw new RuntimeException("Malformed URL exception while trying to open Discord", e);
        }
    }//GEN-LAST:event_jLabel6MouseClicked

    private void buttonAddFileActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonAddFileActionPerformed
        addFilesOrDirectory();
    }//GEN-LAST:event_buttonAddFileActionPerformed

    private void buttonRemoveFileActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_buttonRemoveFileActionPerformed
        removeFiles();
    }//GEN-LAST:event_buttonRemoveFileActionPerformed

    private void spinnerBlocksPerAttemptStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_spinnerBlocksPerAttemptStateChanged
        updateBlocksPerAttempt();
        settingsChanged();
    }//GEN-LAST:event_spinnerBlocksPerAttemptStateChanged

    private void spinnerGridStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_spinnerGridStateChanged
        updateBlocksPerAttempt();
        settingsChanged();
    }//GEN-LAST:event_spinnerGridStateChanged

    private void spinnerRandomOffsetStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_spinnerRandomOffsetStateChanged
        settingsChanged();
    }//GEN-LAST:event_spinnerRandomOffsetStateChanged


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton buttonAddFile;
    private javax.swing.JButton buttonEdit;
    private javax.swing.JButton buttonReloadAll;
    private javax.swing.JButton buttonRemoveFile;
    private javax.swing.JButton buttonReset;
    private javax.swing.JButton buttonSetDecay;
    private javax.swing.JButton buttonSetNoDecay;
    private javax.swing.JTextField fieldName;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabel14;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JLabel labelBlocksPerAttempt;
    private javax.swing.JLabel labelEffectiveLeafDecaySetting;
    private javax.swing.JLabel labelLeafDecayTitle;
    private javax.swing.JList<WPObject> listObjects;
    private org.pepsoft.worldpainter.layers.renderers.PaintPicker paintPicker1;
    private javax.swing.JSpinner spinnerBlocksPerAttempt;
    private javax.swing.JSpinner spinnerGrid;
    private javax.swing.JSpinner spinnerRandomOffset;
    // End of variables declaration//GEN-END:variables

    // --- Block Mapping Panel for Hytale worlds ---

    private JPanel createBlockMappingPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Block Mappings (Loaded Block \u2192 Hytale)"));

        JLabel hintLabel = new JLabel("Loaded blocks are read-only. Click the Hytale block column to choose replacements from a searchable picker.");
        panel.add(hintLabel, BorderLayout.NORTH);

        blockMappingTableModel = new javax.swing.table.DefaultTableModel(
            new String[]{"Loaded Block", "Hytale Block"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        blockMappingTable = new JTable(blockMappingTableModel);
        blockMappingTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        blockMappingTable.getTableHeader().setReorderingAllowed(false);
        blockMappingTable.setRowHeight(22);
        blockMappingTable.setDefaultRenderer(Object.class, new BlockMappingCellRenderer());
        blockMappingTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = blockMappingTable.rowAtPoint(e.getPoint());
                int column = blockMappingTable.columnAtPoint(e.getPoint());
                if ((row != -1) && (column == 1)) {
                    editBlockMapping(row, column);
                }
            }
        });
        blockMappingTable.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int row = blockMappingTable.rowAtPoint(e.getPoint());
                int column = blockMappingTable.columnAtPoint(e.getPoint());
                blockMappingTable.setCursor(((row != -1) && (column == 1)) ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
            }
        });

        JScrollPane scrollPane = new JScrollPane(blockMappingTable);
        scrollPane.setPreferredSize(new java.awt.Dimension(0, 180));
        panel.add(scrollPane, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        JButton scanButton = new JButton("Scan Objects");
        scanButton.setToolTipText("Re-scan all objects for Minecraft blocks and update mappings");
        scanButton.addActionListener(e -> populateBlockMappings());
        buttonPanel.add(scanButton);

        JButton resetButton = new JButton("Reset to Defaults");
        resetButton.setToolTipText("Reset all mappings to the default Minecraft \u2192 Hytale conversion");
        resetButton.addActionListener(e -> resetMappingsToDefaults());
        buttonPanel.add(resetButton);

        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void populateBlockMappings() {
        if (blockMappingTableModel == null) {
            return;
        }
        // Collect existing custom mappings from the table before clearing
        Map<String, String> existingCustom = collectMappingsFromTable();

        // Also load saved mappings from the layer
        Map<String, String> savedMappings = (layer != null) ? layer.getHytaleBlockMappings() : null;

        // Scan all objects for unique source materials so both schematics and Hytale prefabs can be remapped.
        java.util.TreeMap<String, String> mappings = new java.util.TreeMap<>();
        for (WPObject object : allObjects) {
            Point3i dim = object.getDimensions();
            for (int x = 0; x < dim.x; x++) {
                for (int y = 0; y < dim.y; y++) {
                    for (int z = 0; z < dim.z; z++) {
                        if (object.getMask(x, y, z)) {
                            Material material = object.getMaterial(x, y, z);
                            if (material != null && material != Material.AIR && material.name != null) {
                                String sourceBlock = material.name;
                                if (!mappings.containsKey(sourceBlock)) {
                                    // Priority: existing table edits > saved layer mappings > defaults
                                    String hytale = existingCustom.get(sourceBlock);
                                    if (hytale == null && savedMappings != null) {
                                        hytale = savedMappings.get(sourceBlock);
                                    }
                                    if (hytale == null) {
                                        hytale = getDefaultHytaleBlock(sourceBlock);
                                    }
                                    mappings.put(sourceBlock, hytale);
                                }
                            }
                        }
                    }
                }
            }
        }

        blockMappingTableModel.setRowCount(0);
        for (Map.Entry<String, String> entry : mappings.entrySet()) {
            blockMappingTableModel.addRow(new Object[]{entry.getKey(), entry.getValue()});
        }
    }

    private void resetMappingsToDefaults() {
        if (blockMappingTableModel == null) {
            return;
        }
        for (int row = 0; row < blockMappingTableModel.getRowCount(); row++) {
            String sourceBlock = (String) blockMappingTableModel.getValueAt(row, 0);
            String defaultHytale = getDefaultHytaleBlock(sourceBlock);
            blockMappingTableModel.setValueAt(defaultHytale, row, 1);
        }
    }

    private Map<String, String> collectMappingsFromTable() {
        Map<String, String> mappings = new java.util.LinkedHashMap<>();
        if (blockMappingTableModel == null) {
            return mappings;
        }
        for (int row = 0; row < blockMappingTableModel.getRowCount(); row++) {
            String mc = (String) blockMappingTableModel.getValueAt(row, 0);
            String hy = (String) blockMappingTableModel.getValueAt(row, 1);
            if (mc != null && hy != null && !mc.isEmpty() && !hy.isEmpty()) {
                String defaultHytale = getDefaultHytaleBlock(mc);
                if (! hy.equals(defaultHytale)) {
                    mappings.put(mc, hy);
                }
            }
        }
        return mappings;
    }

    private void editBlockMapping(int row, int column) {
        if ((row < 0) || (row >= blockMappingTableModel.getRowCount())) {
            return;
        }
        if (column == 1) {
            String currentHytale = (String) blockMappingTableModel.getValueAt(row, 1);
            String selectedHytale = chooseHytaleBlock(currentHytale);
            if (selectedHytale != null) {
                blockMappingTableModel.setValueAt(selectedHytale, row, 1);
            }
        }
    }

    private String chooseHytaleBlock(String currentHytale) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new WorldPainterDialog(owner);
        dialog.setTitle("Select Hytale Block");

        HytaleBlockRegistry.ensureMaterialsRegistered();
        HytaleBlockPalette palette = new HytaleBlockPalette(false);
        if (currentHytale != null) {
            palette.setSelectedBlock(currentHytale);
        }

        final String[] result = {null};
        JButton okButton = new JButton("OK");
        okButton.addActionListener(event -> {
            org.pepsoft.worldpainter.hytale.HytaleBlock selected = palette.getSelectedBlock();
            result[0] = (selected != null) ? selected.id : null;
            dialog.dispose();
        });

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(event -> dialog.dispose());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);

        dialog.getContentPane().add(palette, BorderLayout.CENTER);
        dialog.getContentPane().add(buttonPanel, BorderLayout.SOUTH);
        dialog.getRootPane().setDefaultButton(okButton);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);

        return result[0];
    }

    static String getDefaultHytaleBlock(String sourceBlock) {
        return org.pepsoft.worldpainter.hytale.HytaleBlockMapping.toHytale(Material.get(sourceBlock));
    }

    static String formatSourceBlockName(String blockName) {
        if (blockName == null) {
            return "";
        }
        if (blockName.startsWith(HytaleBlockRegistry.HYTALE_NAMESPACE + ":")) {
            return "Hytale: " + HytaleBlockRegistry.formatDisplayName(blockName.substring(HytaleBlockRegistry.HYTALE_NAMESPACE.length() + 1));
        }
        return formatMinecraftBlockName(blockName);
    }

    private static String formatMinecraftBlockName(String blockName) {
        if (blockName == null) {
            return "";
        }
        String display = blockName.startsWith("minecraft:") ? blockName.substring(10) : blockName;
        return display.replace('_', ' ');
    }

    private static class BlockMappingCellRenderer extends javax.swing.table.DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            String raw = (value instanceof String) ? (String) value : "";
            label.setToolTipText(raw);
            if (column == 0) {
                label.setText(formatSourceBlockName(raw));
            } else {
                label.setText(HytaleBlockRegistry.formatDisplayName(raw));
            }
            return label;
        }
    }

    private final DefaultListModel<WPObject> listModel;
    private final List<WPObject> allObjects = new ArrayList<>();
    private final NumberFormat numberFormat = NumberFormat.getInstance();
    private ColourScheme colourScheme;

    // Category filter and no-physics controls
    private JCheckBox checkBoxNoPhysics;
    private JComboBox<String> comboBoxCategory;
    private JLabel labelCategory;

    // Block mapping fields for Hytale worlds
    private boolean isHytaleWorld;
    private JPanel wrapperPanel;
    private JPanel blockMappingPanel;
    private javax.swing.table.DefaultTableModel blockMappingTableModel;
    private JTable blockMappingTable;

    private static final String ALL_CATEGORIES = "All";
    private static final String UNCATEGORIZED = "Uncategorized";
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Bo2LayerEditor.class);
    private static final long serialVersionUID = 1L;
}
