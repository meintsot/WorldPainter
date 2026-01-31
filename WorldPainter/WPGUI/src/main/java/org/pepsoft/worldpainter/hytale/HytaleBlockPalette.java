package org.pepsoft.worldpainter.hytale;

import org.pepsoft.worldpainter.ColourScheme;
import org.pepsoft.worldpainter.hytale.HytaleBlockRegistry.BlockDefinition;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/**
 * A palette panel for selecting native Hytale blocks and terrains.
 * 
 * <p>This panel provides:
 * <ul>
 *   <li>Categorized block list (Rock, Soil, Wood, Plant, etc.)</li>
 *   <li>Block icon preview with colour coding</li>
 *   <li>Search/filter functionality</li>
 *   <li>Terrain template selection</li>
 *   <li>Custom block mapping configuration</li>
 *   <li>Save/load palette configurations</li>
 * </ul>
 * 
 * <p>It integrates with the existing WorldPainter terrain painting system
 * when exporting to Hytale format.
 * 
 * @see HytaleBlockRegistry
 * @see HytaleTerrain
 */
public class HytaleBlockPalette extends JPanel {
    
    private final HytaleBlockRegistry registry;
    private final JComboBox<String> categoryCombo;
    private final JList<BlockItem> blockList;
    private final DefaultListModel<BlockItem> blockListModel;
    private final JTextField searchField;
    private final JLabel previewLabel;
    private final JLabel infoLabel;
    
    // Mapping configuration
    private final JList<MappingItem> mappingList;
    private final DefaultListModel<MappingItem> mappingListModel;
    private final JButton addMappingButton;
    private final JButton removeMappingButton;
    private final JButton saveMappingsButton;
    private final JButton loadMappingsButton;
    
    private BlockItem selectedBlock;
    private List<BlockSelectionListener> listeners = new ArrayList<>();
    private Map<String, String> customMappings = new LinkedHashMap<>(); // Minecraft block -> Hytale block
    
    /**
     * Create a new block palette panel.
     */
    public HytaleBlockPalette() {
        this(true);
    }
    
    /**
     * Create a new block palette panel.
     * @param showMappingPanel Whether to show the mapping configuration panel
     */
    public HytaleBlockPalette(boolean showMappingPanel) {
        this.registry = HytaleBlockRegistry.getInstance();
        
        setLayout(new BorderLayout(5, 5));
        setBorder(new EmptyBorder(5, 5, 5, 5));
        
        // Top panel: category selector and search
        JPanel topPanel = new JPanel(new BorderLayout(5, 0));
        
        // Category dropdown
        Set<String> categories = registry.getCategories();
        String[] categoryArray = new String[categories.size() + 1];
        categoryArray[0] = "All";
        int i = 1;
        for (String cat : categories) {
            categoryArray[i++] = cat;
        }
        categoryCombo = new JComboBox<>(categoryArray);
        categoryCombo.addActionListener(e -> refreshBlockList());
        topPanel.add(categoryCombo, BorderLayout.WEST);
        
        // Search field
        searchField = new JTextField();
        searchField.setToolTipText("Search blocks by name");
        searchField.addActionListener(e -> refreshBlockList());
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { refreshBlockList(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { refreshBlockList(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { refreshBlockList(); }
        });
        topPanel.add(searchField, BorderLayout.CENTER);
        
        add(topPanel, BorderLayout.NORTH);
        
        // Center: block list
        blockListModel = new DefaultListModel<>();
        blockList = new JList<>(blockListModel);
        blockList.setCellRenderer(new BlockCellRenderer());
        blockList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        blockList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                selectBlock(blockList.getSelectedValue());
            }
        });
        
        JScrollPane scrollPane = new JScrollPane(blockList);
        scrollPane.setPreferredSize(new Dimension(250, 300));
        
        if (showMappingPanel) {
            // Split pane with block list and mapping list
            JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
            
            JPanel blockPanel = new JPanel(new BorderLayout());
            blockPanel.setBorder(new TitledBorder("Hytale Blocks"));
            blockPanel.add(scrollPane, BorderLayout.CENTER);
            splitPane.setTopComponent(blockPanel);
            
            // Mapping panel
            JPanel mappingPanel = new JPanel(new BorderLayout(5, 5));
            mappingPanel.setBorder(new TitledBorder("Block Mappings (Minecraft → Hytale)"));
            
            mappingListModel = new DefaultListModel<>();
            mappingList = new JList<>(mappingListModel);
            mappingList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            JScrollPane mappingScroll = new JScrollPane(mappingList);
            mappingScroll.setPreferredSize(new Dimension(250, 150));
            mappingPanel.add(mappingScroll, BorderLayout.CENTER);
            
            JPanel mappingButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
            addMappingButton = new JButton("Add");
            addMappingButton.addActionListener(e -> addMapping());
            removeMappingButton = new JButton("Remove");
            removeMappingButton.addActionListener(e -> removeMapping());
            saveMappingsButton = new JButton("Save");
            saveMappingsButton.addActionListener(e -> saveMappings());
            loadMappingsButton = new JButton("Load");
            loadMappingsButton.addActionListener(e -> loadMappings());
            
            mappingButtons.add(addMappingButton);
            mappingButtons.add(removeMappingButton);
            mappingButtons.add(Box.createHorizontalStrut(10));
            mappingButtons.add(saveMappingsButton);
            mappingButtons.add(loadMappingsButton);
            mappingPanel.add(mappingButtons, BorderLayout.SOUTH);
            
            splitPane.setBottomComponent(mappingPanel);
            splitPane.setDividerLocation(300);
            
            add(splitPane, BorderLayout.CENTER);
        } else {
            add(scrollPane, BorderLayout.CENTER);
            mappingListModel = null;
            mappingList = null;
            addMappingButton = null;
            removeMappingButton = null;
            saveMappingsButton = null;
            loadMappingsButton = null;
        }
        
        // Bottom: preview and info
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        
        previewLabel = new JLabel();
        previewLabel.setPreferredSize(new Dimension(64, 64));
        previewLabel.setHorizontalAlignment(SwingConstants.CENTER);
        previewLabel.setBorder(BorderFactory.createEtchedBorder());
        bottomPanel.add(previewLabel, BorderLayout.WEST);
        
        infoLabel = new JLabel("<html>Select a block<br>from the list above</html>");
        infoLabel.setVerticalAlignment(SwingConstants.TOP);
        bottomPanel.add(infoLabel, BorderLayout.CENTER);
        
        add(bottomPanel, BorderLayout.SOUTH);
        
        // Initial population
        refreshBlockList();
        loadDefaultMappings();
    }
    
    /**
     * Refresh the block list based on category and search filter.
     */
    private void refreshBlockList() {
        blockListModel.clear();
        
        String category = (String) categoryCombo.getSelectedItem();
        String search = searchField.getText().toLowerCase().trim();
        
        Set<String> blockIds;
        if ("All".equals(category)) {
            blockIds = registry.getAllBlockIds();
        } else {
            blockIds = new LinkedHashSet<>(registry.getBlocksInCategory(category));
        }
        
        for (String id : blockIds) {
            if (search.isEmpty() || id.toLowerCase().contains(search)) {
                BlockDefinition def = registry.getBlock(id);
                if (def != null) {
                    blockListModel.addElement(new BlockItem(id, def));
                }
            }
        }
    }
    
    /**
     * Handle block selection.
     */
    private void selectBlock(BlockItem item) {
        selectedBlock = item;
        
        if (item != null) {
            // Update preview
            BufferedImage icon = createBlockIcon(item.definition, 64);
            previewLabel.setIcon(new ImageIcon(icon));
            
            // Update info
            BlockDefinition def = item.definition;
            String info = String.format(
                "<html><b>%s</b><br>" +
                "Category: %s<br>" +
                "Material: %s<br>" +
                "DrawType: %s<br>" +
                "Opacity: %s<br>" +
                "Rotatable: %s</html>",
                def.displayName,
                def.category,
                def.material,
                def.drawType,
                def.opacity,
                def.canRotate ? "Yes" : "No"
            );
            infoLabel.setText(info);
            
            // Notify listeners
            for (BlockSelectionListener listener : listeners) {
                listener.blockSelected(HytaleBlock.of(item.id));
            }
        } else {
            previewLabel.setIcon(null);
            infoLabel.setText("<html>Select a block<br>from the list above</html>");
        }
    }
    
    /**
     * Get the currently selected block.
     */
    public HytaleBlock getSelectedBlock() {
        return selectedBlock != null ? HytaleBlock.of(selectedBlock.id) : null;
    }
    
    /**
     * Set the selected block by ID.
     */
    public void setSelectedBlock(String blockId) {
        for (int i = 0; i < blockListModel.size(); i++) {
            BlockItem item = blockListModel.get(i);
            if (item.id.equals(blockId)) {
                blockList.setSelectedIndex(i);
                blockList.ensureIndexIsVisible(i);
                return;
            }
        }
    }
    
    /**
     * Add a selection listener.
     */
    public void addSelectionListener(BlockSelectionListener listener) {
        listeners.add(listener);
    }
    
    /**
     * Remove a selection listener.
     */
    public void removeSelectionListener(BlockSelectionListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * Create an icon for a block definition.
     */
    private BufferedImage createBlockIcon(BlockDefinition def, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = img.createGraphics();
        
        // Get colour based on category/name
        int rgb = getBlockColour(def);
        g2d.setColor(new Color(rgb));
        g2d.fillRect(2, 2, size - 4, size - 4);
        
        // Draw border
        g2d.setColor(Color.DARK_GRAY);
        g2d.drawRect(2, 2, size - 5, size - 5);
        
        // Draw diagonal pattern for transparent blocks
        if ("Transparent".equals(def.opacity) || "SemiTransparent".equals(def.opacity)) {
            g2d.setColor(new Color(0, 0, 0, 40));
            for (int i = -size; i < size; i += 4) {
                g2d.drawLine(i, 0, i + size, size);
            }
        }
        
        // Draw X pattern for foliage/cross draw type
        if ("Cross".equals(def.drawType) || "Foliage".equals(def.material)) {
            g2d.setColor(new Color(0, 0, 0, 60));
            g2d.drawLine(4, 4, size - 5, size - 5);
            g2d.drawLine(size - 5, 4, 4, size - 5);
        }
        
        g2d.dispose();
        return img;
    }
    
    /**
     * Get a colour for a block based on its definition.
     */
    private int getBlockColour(BlockDefinition def) {
        String id = def.id;
        
        // Rock colours
        if (id.startsWith("Rock_Stone")) return 0x808080;
        if (id.startsWith("Rock_Bedrock")) return 0x2d2d2d;
        if (id.startsWith("Rock_Ice")) return 0xa0d0ff;
        if (id.startsWith("Rock_Sandstone")) return 0xd4c099;
        if (id.startsWith("Rock_Basalt")) return 0x3a3a3a;
        if (id.startsWith("Rock_Marble")) return 0xf0f0f0;
        if (id.startsWith("Rock_")) return 0x707070;
        
        // Soil colours
        if (id.startsWith("Soil_Grass")) return 0x59a52c;
        if (id.startsWith("Soil_Dirt")) return 0x8b5a2b;
        if (id.startsWith("Soil_Sand_Red")) return 0xc4633c;
        if (id.startsWith("Soil_Sand")) return 0xdbc497;
        if (id.startsWith("Soil_Snow")) return 0xfffafa;
        if (id.startsWith("Soil_")) return 0x8b5a2b;
        
        // Wood colours
        if (id.startsWith("Wood_")) return 0x8b7355;
        
        // Plant colours
        if (id.contains("Leaves")) return 0x228b22;
        if (id.startsWith("Plant_")) return 0x32cd32;
        
        // Ore colours
        if (id.contains("Iron")) return 0xd4a574;
        if (id.contains("Gold")) return 0xffd700;
        if (id.contains("Copper")) return 0xb87333;
        if (id.startsWith("Ore_")) return 0x808080;
        
        // Fluid colours
        if (id.contains("Water")) return 0x3366ff;
        if (id.contains("Lava")) return 0xff4500;
        
        // Default
        if (id.equals("Empty")) return 0x000000;
        return 0xa0a0a0;
    }
    
    // ----- Inner classes -----
    
    /**
     * List item wrapper for blocks.
     */
    private static class BlockItem {
        final String id;
        final BlockDefinition definition;
        
        BlockItem(String id, BlockDefinition definition) {
            this.id = id;
            this.definition = definition;
        }
        
        @Override
        public String toString() {
            return definition.displayName;
        }
    }
    
    /**
     * Cell renderer for block list.
     */
    private class BlockCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            
            if (value instanceof BlockItem) {
                BlockItem item = (BlockItem) value;
                label.setText(item.definition.displayName);
                
                // Create small icon
                BufferedImage icon = createBlockIcon(item.definition, 16);
                label.setIcon(new ImageIcon(icon));
            }
            
            return label;
        }
    }
    
    /**
     * Listener interface for block selection events.
     */
    public interface BlockSelectionListener {
        void blockSelected(HytaleBlock block);
    }
    
    // ----- Mapping management methods -----
    
    /**
     * Load default mappings from HytaleBlockMapping.
     */
    private void loadDefaultMappings() {
        customMappings.clear();
        // Load some common default mappings
        customMappings.put("minecraft:stone", "Rock_Stone");
        customMappings.put("minecraft:grass_block", "Soil_Grass");
        customMappings.put("minecraft:dirt", "Soil_Dirt");
        customMappings.put("minecraft:sand", "Soil_Sand");
        customMappings.put("minecraft:red_sand", "Soil_Sand_Red");
        customMappings.put("minecraft:gravel", "Soil_Gravel");
        customMappings.put("minecraft:bedrock", "Rock_Bedrock");
        customMappings.put("minecraft:oak_log", "Wood_Oak_Log");
        customMappings.put("minecraft:oak_planks", "Wood_Oak_Planks");
        customMappings.put("minecraft:oak_leaves", "Leaves_Oak");
        customMappings.put("minecraft:water", "Water_Source");
        customMappings.put("minecraft:lava", "Lava_Source");
        
        refreshMappingList();
    }
    
    /**
     * Refresh the mapping list display.
     */
    private void refreshMappingList() {
        if (mappingListModel == null) return;
        
        mappingListModel.clear();
        for (Map.Entry<String, String> entry : customMappings.entrySet()) {
            mappingListModel.addElement(new MappingItem(entry.getKey(), entry.getValue()));
        }
    }
    
    /**
     * Add a new mapping.
     */
    private void addMapping() {
        HytaleBlock selected = getSelectedBlock();
        String hytaleBlock = selected != null ? selected.getId() : "Rock_Stone";
        
        String minecraftBlock = JOptionPane.showInputDialog(this,
            "Enter Minecraft block ID (e.g., minecraft:stone):",
            "Add Block Mapping",
            JOptionPane.PLAIN_MESSAGE);
        
        if (minecraftBlock != null && !minecraftBlock.trim().isEmpty()) {
            minecraftBlock = minecraftBlock.trim();
            if (!minecraftBlock.contains(":")) {
                minecraftBlock = "minecraft:" + minecraftBlock;
            }
            
            // Ask for Hytale block if none selected
            if (selected == null) {
                hytaleBlock = JOptionPane.showInputDialog(this,
                    "Enter Hytale block ID (e.g., Rock_Stone):",
                    "Add Block Mapping",
                    JOptionPane.PLAIN_MESSAGE);
                if (hytaleBlock == null || hytaleBlock.trim().isEmpty()) {
                    return;
                }
                hytaleBlock = hytaleBlock.trim();
            }
            
            customMappings.put(minecraftBlock, hytaleBlock);
            refreshMappingList();
        }
    }
    
    /**
     * Remove the selected mapping.
     */
    private void removeMapping() {
        if (mappingList == null) return;
        
        MappingItem selected = mappingList.getSelectedValue();
        if (selected != null) {
            customMappings.remove(selected.minecraftBlock);
            refreshMappingList();
        }
    }
    
    /**
     * Save mappings to a file.
     */
    private void saveMappings() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save Block Mappings");
        chooser.setFileFilter(new FileNameExtensionFilter("Hytale Mappings (*.hymap)", "hymap"));
        
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (!file.getName().endsWith(".hymap")) {
                file = new File(file.getAbsolutePath() + ".hymap");
            }
            
            try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
                writer.println("# Hytale Block Mappings");
                writer.println("# Format: minecraft_block=hytale_block");
                writer.println();
                
                for (Map.Entry<String, String> entry : customMappings.entrySet()) {
                    writer.println(entry.getKey() + "=" + entry.getValue());
                }
                
                JOptionPane.showMessageDialog(this,
                    "Mappings saved successfully.",
                    "Save Mappings",
                    JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this,
                    "Error saving mappings: " + e.getMessage(),
                    "Save Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    /**
     * Load mappings from a file.
     */
    private void loadMappings() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Load Block Mappings");
        chooser.setFileFilter(new FileNameExtensionFilter("Hytale Mappings (*.hymap)", "hymap"));
        
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                customMappings.clear();
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    
                    int eq = line.indexOf('=');
                    if (eq > 0) {
                        String mc = line.substring(0, eq).trim();
                        String hy = line.substring(eq + 1).trim();
                        if (!mc.isEmpty() && !hy.isEmpty()) {
                            customMappings.put(mc, hy);
                        }
                    }
                }
                
                refreshMappingList();
                JOptionPane.showMessageDialog(this,
                    "Mappings loaded successfully.",
                    "Load Mappings",
                    JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this,
                    "Error loading mappings: " + e.getMessage(),
                    "Load Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    /**
     * Get the custom block mappings.
     * @return Map of Minecraft block ID to Hytale block ID
     */
    public Map<String, String> getCustomMappings() {
        return new LinkedHashMap<>(customMappings);
    }
    
    /**
     * Set custom block mappings.
     * @param mappings Map of Minecraft block ID to Hytale block ID
     */
    public void setCustomMappings(Map<String, String> mappings) {
        customMappings.clear();
        if (mappings != null) {
            customMappings.putAll(mappings);
        }
        refreshMappingList();
    }
    
    /**
     * Mapping item for display in the list.
     */
    private static class MappingItem {
        final String minecraftBlock;
        final String hytaleBlock;
        
        MappingItem(String minecraftBlock, String hytaleBlock) {
            this.minecraftBlock = minecraftBlock;
            this.hytaleBlock = hytaleBlock;
        }
        
        @Override
        public String toString() {
            return minecraftBlock + " → " + hytaleBlock;
        }
    }
}
