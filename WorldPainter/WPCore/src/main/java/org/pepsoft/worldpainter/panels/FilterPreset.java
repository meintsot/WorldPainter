package org.pepsoft.worldpainter.panels;

import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.hytale.HytaleTerrain;
import org.pepsoft.worldpainter.layers.Annotations;
import org.pepsoft.worldpainter.layers.Biome;
import org.pepsoft.worldpainter.layers.Layer;
import org.pepsoft.worldpainter.layers.LayerManager;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import static org.pepsoft.worldpainter.panels.DefaultFilter.LayerValue;

/**
 * A named, serializable snapshot of a filter configuration that can be saved and loaded
 * from {@link org.pepsoft.worldpainter.Configuration}.
 */
public final class FilterPreset implements Serializable {
    private static final long serialVersionUID = 1L;

    private String name;
    private boolean inSelection;
    private boolean outsideSelection;
    private int aboveLevel = Integer.MIN_VALUE;
    private int belowLevel = Integer.MIN_VALUE;
    private boolean feather;
    private List<PaintRef> onlyOnRefs;
    private List<PaintRef> exceptOnRefs;
    private int slopeDegrees = -1;
    private boolean slopeIsAbove;

    public FilterPreset() {}

    public FilterPreset(String name) {
        this.name = name;
    }

    // ---- Getters / Setters ----

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isInSelection() { return inSelection; }
    public void setInSelection(boolean inSelection) { this.inSelection = inSelection; }

    public boolean isOutsideSelection() { return outsideSelection; }
    public void setOutsideSelection(boolean outsideSelection) { this.outsideSelection = outsideSelection; }

    public int getAboveLevel() { return aboveLevel; }
    public void setAboveLevel(int aboveLevel) { this.aboveLevel = aboveLevel; }

    public int getBelowLevel() { return belowLevel; }
    public void setBelowLevel(int belowLevel) { this.belowLevel = belowLevel; }

    public boolean isFeather() { return feather; }
    public void setFeather(boolean feather) { this.feather = feather; }

    public List<PaintRef> getOnlyOnRefs() { return onlyOnRefs; }
    public void setOnlyOnRefs(List<PaintRef> onlyOnRefs) { this.onlyOnRefs = onlyOnRefs; }

    public List<PaintRef> getExceptOnRefs() { return exceptOnRefs; }
    public void setExceptOnRefs(List<PaintRef> exceptOnRefs) { this.exceptOnRefs = exceptOnRefs; }

    public int getSlopeDegrees() { return slopeDegrees; }
    public void setSlopeDegrees(int slopeDegrees) { this.slopeDegrees = slopeDegrees; }

    public boolean isSlopeIsAbove() { return slopeIsAbove; }
    public void setSlopeIsAbove(boolean slopeIsAbove) { this.slopeIsAbove = slopeIsAbove; }

    // ---- Snapshot from live paint objects ----

    /**
     * Capture a snapshot of the current BrushOptions state into this preset.
     */
    @SuppressWarnings("unchecked")
    public void captureFrom(boolean inSelection, boolean outsideSelection,
                            int aboveLevel, int belowLevel, boolean feather,
                            Object onlyOn, Object exceptOn,
                            int slopeDegrees, boolean slopeIsAbove) {
        this.inSelection = inSelection;
        this.outsideSelection = outsideSelection;
        this.aboveLevel = aboveLevel;
        this.belowLevel = belowLevel;
        this.feather = feather;
        this.slopeDegrees = slopeDegrees;
        this.slopeIsAbove = slopeIsAbove;
        this.onlyOnRefs = toRefs(onlyOn);
        this.exceptOnRefs = toRefs(exceptOn);
    }

    /**
     * Resolve the onlyOn paint objects from saved refs.
     * Returns a single Object or a List, matching BrushOptions convention.
     * Returns null if no refs are stored.
     */
    public Object resolveOnlyOn() {
        return resolveRefs(onlyOnRefs);
    }

    /**
     * Resolve the exceptOn paint objects from saved refs.
     */
    public Object resolveExceptOn() {
        return resolveRefs(exceptOnRefs);
    }

    // ---- Conversion helpers ----

    @SuppressWarnings("unchecked")
    private static List<PaintRef> toRefs(Object paint) {
        if (paint == null) return null;
        if (paint instanceof List) {
            List<PaintRef> refs = new ArrayList<>();
            for (Object item : (List<Object>) paint) {
                PaintRef ref = toRef(item);
                if (ref != null) refs.add(ref);
            }
            return refs.isEmpty() ? null : refs;
        } else {
            PaintRef ref = toRef(paint);
            if (ref == null) return null;
            List<PaintRef> refs = new ArrayList<>();
            refs.add(ref);
            return refs;
        }
    }

    private static PaintRef toRef(Object paint) {
        if (paint instanceof Terrain) {
            return new PaintRef(PaintRef.Type.TERRAIN, ((Terrain) paint).name(), -1);
        } else if (paint instanceof HytaleTerrain) {
            HytaleTerrain ht = (HytaleTerrain) paint;
            String id = (ht.getBlock() != null) ? ht.getBlock().id : ht.getName();
            return new PaintRef(PaintRef.Type.HYTALE_TERRAIN, id, -1);
        } else if (paint instanceof Layer) {
            return new PaintRef(PaintRef.Type.LAYER, ((Layer) paint).getId(), -1);
        } else if (paint instanceof LayerValue) {
            LayerValue lv = (LayerValue) paint;
            if (lv.layer instanceof Biome) {
                return new PaintRef(lv.value < 0 ? PaintRef.Type.AUTO_BIOME : PaintRef.Type.BIOME,
                        null, Math.abs(lv.value));
            } else if (lv.layer instanceof Annotations) {
                return new PaintRef(lv.value == -1 ? PaintRef.Type.ANNOTATION_ANY : PaintRef.Type.ANNOTATION,
                        null, lv.value);
            } else {
                return new PaintRef(PaintRef.Type.LAYER_VALUE, lv.layer.getId(), lv.value);
            }
        } else if (paint instanceof String) {
            switch ((String) paint) {
                case TerrainOrLayerFilter.WATER:      return new PaintRef(PaintRef.Type.WATER, null, -1);
                case TerrainOrLayerFilter.LAVA:       return new PaintRef(PaintRef.Type.LAVA, null, -1);
                case TerrainOrLayerFilter.LAND:       return new PaintRef(PaintRef.Type.LAND, null, -1);
                case TerrainOrLayerFilter.AUTO_BIOMES: return new PaintRef(PaintRef.Type.AUTO_BIOMES, null, -1);
                default: return null;
            }
        }
        return null;
    }

    private static Object resolveRefs(List<PaintRef> refs) {
        if (refs == null || refs.isEmpty()) return null;
        if (refs.size() == 1) return resolveRef(refs.get(0));
        List<Object> result = new ArrayList<>();
        for (PaintRef ref : refs) {
            Object obj = resolveRef(ref);
            if (obj != null) result.add(obj);
        }
        if (result.isEmpty()) return null;
        if (result.size() == 1) return result.get(0);
        return result;
    }

    private static Object resolveRef(PaintRef ref) {
        if (ref == null) return null;
        switch (ref.type) {
            case TERRAIN:
                try { return Terrain.valueOf(ref.id); }
                catch (IllegalArgumentException e) { return null; }
            case HYTALE_TERRAIN:
                HytaleTerrain ht = HytaleTerrain.getByBlockId(ref.id);
                return (ht != null) ? ht : HytaleTerrain.getByName(ref.id);
            case LAYER: {
                // Try built-in layers first, then search all
                Layer layer = LayerManager.getInstance().getLayer(ref.id);
                if (layer != null) return layer;
                for (Layer l : LayerManager.getInstance().getLayers()) {
                    if (l.getId().equals(ref.id)) return l;
                }
                return null;
            }
            case LAYER_VALUE: {
                Layer layer = null;
                for (Layer l : LayerManager.getInstance().getLayers()) {
                    if (l.getId().equals(ref.id)) { layer = l; break; }
                }
                return (layer != null) ? new LayerValue(layer, ref.value) : null;
            }
            case BIOME:
                return new LayerValue(Biome.INSTANCE, ref.value);
            case AUTO_BIOME:
                return new LayerValue(Biome.INSTANCE, -ref.value);
            case ANNOTATION:
                return new LayerValue(Annotations.INSTANCE, ref.value);
            case ANNOTATION_ANY:
                return new LayerValue(Annotations.INSTANCE);
            case WATER:      return TerrainOrLayerFilter.WATER;
            case LAVA:       return TerrainOrLayerFilter.LAVA;
            case LAND:       return TerrainOrLayerFilter.LAND;
            case AUTO_BIOMES: return TerrainOrLayerFilter.AUTO_BIOMES;
            default: return null;
        }
    }

    @Override
    public String toString() {
        return name;
    }

    // ---- Inner class ----

    /**
     * A serializable reference to a paint object (terrain, layer, biome, etc.).
     */
    public static final class PaintRef implements Serializable {
        private static final long serialVersionUID = 1L;

        public enum Type {
            TERRAIN, HYTALE_TERRAIN, LAYER, LAYER_VALUE, BIOME, AUTO_BIOME,
            ANNOTATION, ANNOTATION_ANY, WATER, LAVA, LAND, AUTO_BIOMES
        }

        public final Type type;
        public final String id;
        public final int value;

        public PaintRef(Type type, String id, int value) {
            this.type = type;
            this.id = id;
            this.value = value;
        }
    }
}
