package org.pepsoft.worldpainter.layers.exporters;

import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.layers.Layer;
import org.pepsoft.worldpainter.layers.TownLayout;

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

    public int getSurfaceDepth() {
        return surfaceDepth;
    }

    public void setSurfaceDepth(int surfaceDepth) {
        this.surfaceDepth = surfaceDepth;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 41 * hash + (this.export ? 1 : 0);
        hash = 41 * hash + ((this.block != null) ? this.block.hashCode() : 0);
        hash = 41 * hash + this.surfaceDepth;
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
        return this.surfaceDepth == other.surfaceDepth;
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
    private Material block = Material.STONE;
    private int surfaceDepth = 1;

    private static final long serialVersionUID = 1L;
}
