package org.pepsoft.worldpainter.layers.exporters;

import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.layers.Layer;
import org.pepsoft.worldpainter.layers.TownLayout;

import static org.pepsoft.minecraft.Constants.BLK_WOOL;

public class TownLayoutSettings implements ExporterSettings {
    @Override
    public boolean isApplyEverywhere() {
        return false;
    }

    @Override
    public Layer getLayer() {
        return TownLayout.INSTANCE;
    }

    public boolean isExport() {
        return export;
    }

    public void setExport(boolean export) {
        this.export = export;
    }

    public Material getBlock() {
        return block;
    }

    public void setBlock(Material block) {
        this.block = block;
    }

    public int getMarkerHeight() {
        return markerHeight;
    }

    public void setMarkerHeight(int markerHeight) {
        this.markerHeight = markerHeight;
    }

    @Override
    public ExporterSettings clone() {
        try {
            return (ExporterSettings) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError();
        }
    }

    private boolean export = true;
    private Material block = Material.get(BLK_WOOL, 15); // black wool
    private int markerHeight = 3;

    private static final long serialVersionUID = 1L;
}
