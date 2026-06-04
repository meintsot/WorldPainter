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
        if (block == null) {
            throw new IllegalArgumentException("block");
        }
        this.block = block;
    }

    public int getMarkerHeight() {
        return markerHeight;
    }

    public void setMarkerHeight(int markerHeight) {
        this.markerHeight = markerHeight;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 41 * hash + (this.export ? 1 : 0);
        hash = 41 * hash + ((this.block != null) ? this.block.hashCode() : 0);
        hash = 41 * hash + this.markerHeight;
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final TownLayoutSettings other = (TownLayoutSettings) obj;
        if (this.export != other.export) {
            return false;
        }
        if ((this.block == null) ? (other.block != null) : (! this.block.equals(other.block))) {
            return false;
        }
        return this.markerHeight == other.markerHeight;
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
