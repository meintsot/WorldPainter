/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.pepsoft.worldpainter.layers;

import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.Platform;
import org.pepsoft.worldpainter.exporting.LayerExporter;
import org.pepsoft.worldpainter.layers.bo2.Bo2LayerExporter;
import org.pepsoft.worldpainter.layers.bo2.Bo2ObjectProvider;
import org.pepsoft.worldpainter.layers.exporters.ExporterSettings;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author pepijn
 */
public class Bo2Layer extends CustomLayer {
    public Bo2Layer(Bo2ObjectProvider objectProvider, String description, Object paint) {
        super(objectProvider.getName(), description, DataSize.NIBBLE, 50, paint);
        this.objectProvider = objectProvider;
    }

    public Bo2ObjectProvider getObjectProvider() {
        return objectProvider;
    }

    public void setObjectProvider(Bo2ObjectProvider objectProvider) {
        this.objectProvider = objectProvider;
        setName(objectProvider.getName());
        setDescription("Custom " + objectProvider.getName() + " objects");
        
        // Legacy
        files = Collections.emptyList();
    }

    public List<File> getFiles() {
        return files;
    }

    @Override
    public Class<? extends LayerExporter> getExporterType() {
        return Bo2LayerExporter.class;
    }

    @Override
    public Bo2LayerExporter getExporter(Dimension dimension, Platform platform, ExporterSettings settings) {
        return new Bo2LayerExporter(dimension, platform, this);
    }

    public int getDensity() {
        return density;
    }

    public void setDensity(int density) {
        this.density = density;
    }

    public int getGridX() {
        return gridX;
    }

    public void setGridX(int gridX) {
        this.gridX = gridX;
    }

    public int getGridY() {
        return gridY;
    }

    public void setGridY(int gridY) {
        this.gridY = gridY;
    }

    public int getRandomDisplacement() {
        return randomDisplacement;
    }

    public void setRandomDisplacement(int randomDisplacement) {
        this.randomDisplacement = randomDisplacement;
    }

    /**
     * Whether physics checks (foundation requirements, collision detection)
     * should be disabled when placing objects from this layer. When {@code true},
     * objects are force-placed at their calculated position regardless of what
     * blocks are already there. Useful for importing Minecraft tree schematics
     * where physics cause floating or missing blocks.
     */
    public boolean isNoPhysics() {
        return noPhysics;
    }

    public void setNoPhysics(boolean noPhysics) {
        this.noPhysics = noPhysics;
    }

    /**
     * Get custom source-block→Hytale block mappings for this layer.
     * Used during Hytale export to override default block conversions.
     *
     * @return Map of source block ID to Hytale block ID, or {@code null} if using defaults
     */
    public Map<String, String> getHytaleBlockMappings() {
        return hytaleBlockMappings;
    }

    /**
     * Set custom source-block→Hytale block mappings for this layer.
     *
     * @param hytaleBlockMappings Map of source block ID to Hytale block ID, or {@code null} to use defaults
     */
    public void setHytaleBlockMappings(Map<String, String> hytaleBlockMappings) {
        this.hytaleBlockMappings = hytaleBlockMappings;
    }

    @Override
    public String getType() {
        return "Custom Objects";
    }

    // Cloneable

    @Override
    public Bo2Layer clone() {
        Bo2Layer clone = (Bo2Layer) super.clone();
        clone.objectProvider = objectProvider.clone();
        if (hytaleBlockMappings != null) {
            clone.hytaleBlockMappings = new HashMap<>(hytaleBlockMappings);
        }
        return clone;
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();

        // Legacy support
        if (colour != 0) {
            setPaint(new Color(colour));
            colour = 0;
        }
        if (density == 0) {
            density = 20;
        }
        if (gridX == 0) {
            gridX = 1;
            gridY = 1;
        }
    }
    
    private Bo2ObjectProvider objectProvider;
    @Deprecated
    private int colour;
    @Deprecated
    private List<File> files = Collections.emptyList();
    private int density = 20;
    private int gridX = 1, gridY = 1, randomDisplacement = 0;
    private Map<String, String> hytaleBlockMappings;
    private boolean noPhysics = false;

    private static final long serialVersionUID = 1L;
}