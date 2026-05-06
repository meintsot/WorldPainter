package org.pepsoft.worldpainter.hytale;

import org.junit.Test;
import org.pepsoft.worldpainter.Terrain;
import org.pepsoft.worldpainter.layers.Bo2Layer;
import org.pepsoft.worldpainter.layers.Layer;
import org.pepsoft.worldpainter.layers.bo2.Bo2ObjectProvider;
import org.pepsoft.worldpainter.layers.pockets.UndergroundPocketsLayer;
import org.pepsoft.worldpainter.objects.WPObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 * Front-line proof for TP-55: Hytale export must respect the user's explicit
 * Custom Layer order from Export → Custom Layers tab.
 *
 * <p>Before the fix, {@code applyFirstPassLayers} and
 * {@code applyCustomObjectLayers} iterated the {@code HashSet} returned by
 * {@code Dimension.getAllLayers(false)} directly, rendering layers in hash
 * order and ignoring {@code exportIndex}. The fix routes both call sites
 * through the {@link HytaleWorldExporter#sortFirstPassLayers} and
 * {@link HytaleWorldExporter#sortBo2Layers} helpers, which collect, filter and
 * sort via {@link Layer#compareTo} (which honours {@code exportIndex} as the
 * primary key). These tests construct realistic custom layers with explicit
 * non-natural {@code exportIndex} values, place them in a {@code HashSet}, and
 * assert the helper output is sorted regardless of input order.
 */
public class HytaleWorldExporterLayerOrderingTest {

    @Test
    public void sortFirstPassLayersHonoursExplicitExportIndex() {
        UndergroundPocketsLayer caves = newPocketsLayer("Underground: Caves");
        UndergroundPocketsLayer mixA = newPocketsLayer("Underground: Mix A");
        UndergroundPocketsLayer mixB = newPocketsLayer("Underground: Mix B");
        UndergroundPocketsLayer mixC = newPocketsLayer("Underground: Mix C");

        // Non-natural order: alphabetical/insertion order would not produce
        // the right answer, so any drift back to those fallbacks fails here.
        caves.setExportIndex(5);
        mixA.setExportIndex(1);
        mixB.setExportIndex(4);
        mixC.setExportIndex(2);

        Set<Layer> input = new HashSet<>(Arrays.asList(caves, mixA, mixB, mixC));

        List<Layer> sorted = HytaleWorldExporter.sortFirstPassLayers(input);

        assertEquals("All four first-pass layers must be retained",
                4, sorted.size());
        assertEquals("Output must be ordered by ascending exportIndex",
                Arrays.asList(mixA, mixC, mixB, caves), sorted);
    }

    @Test
    public void sortFirstPassLayersFiltersNonFirstPassLayers() {
        UndergroundPocketsLayer pockets = newPocketsLayer("Pockets");
        Bo2Layer bo2 = newBo2Layer("Trees");
        pockets.setExportIndex(0);
        bo2.setExportIndex(0);

        Set<Layer> input = new HashSet<>(Arrays.asList(pockets, bo2));

        List<Layer> sorted = HytaleWorldExporter.sortFirstPassLayers(input);

        assertEquals("Bo2Layer is SecondPassLayerExporter and must be filtered out",
                Collections.singletonList(pockets), sorted);
    }

    @Test
    public void sortBo2LayersHonoursExplicitExportIndex() {
        Bo2Layer trees = newBo2Layer("Trees");
        Bo2Layer rocks = newBo2Layer("Rocks");
        Bo2Layer plants = newBo2Layer("Plants");

        trees.setExportIndex(2);
        rocks.setExportIndex(0);
        plants.setExportIndex(1);

        Set<Layer> input = new HashSet<>(Arrays.asList(trees, rocks, plants));

        List<Bo2Layer> sorted = HytaleWorldExporter.sortBo2Layers(input);

        assertEquals("All three Bo2 layers must be retained",
                3, sorted.size());
        assertEquals("Output must be ordered by ascending exportIndex",
                Arrays.asList(rocks, plants, trees), sorted);
    }

    @Test
    public void sortBo2LayersFiltersNonBo2Layers() {
        Bo2Layer bo2 = newBo2Layer("Trees");
        UndergroundPocketsLayer pockets = newPocketsLayer("Pockets");
        bo2.setExportIndex(0);
        pockets.setExportIndex(0);

        Set<Layer> input = new HashSet<>(Arrays.asList(bo2, pockets));

        List<Bo2Layer> sorted = HytaleWorldExporter.sortBo2Layers(input);

        assertEquals("Non-Bo2 layers must be filtered out",
                Collections.singletonList(bo2), sorted);
    }

    @Test
    public void sortFirstPassLayersIsDeterministicAcrossManyInputOrderings() {
        // The pre-fix bug surfaced as "random" ordering because the source
        // collection is a HashSet whose iteration depends on hash codes.
        // Build the layer set from many shuffled insertions to defeat any
        // accidental hash-coincidence and confirm the helper output is
        // always exportIndex-sorted.
        List<Integer> indices = Arrays.asList(7, 3, 9, 1, 5, 8, 2, 6, 4, 0);
        List<UndergroundPocketsLayer> layers = new ArrayList<>();
        for (int i = 0; i < indices.size(); i++) {
            UndergroundPocketsLayer layer = newPocketsLayer("Layer-" + i);
            layer.setExportIndex(indices.get(i));
            layers.add(layer);
        }

        List<UndergroundPocketsLayer> expected = new ArrayList<>(layers);
        expected.sort(Comparator.comparingInt(UndergroundPocketsLayer::getExportIndex));

        for (int trial = 0; trial < 20; trial++) {
            List<UndergroundPocketsLayer> shuffled = new ArrayList<>(layers);
            Collections.shuffle(shuffled, new Random(trial));
            Set<Layer> input = new LinkedHashSet<>(shuffled);

            List<Layer> sorted = HytaleWorldExporter.sortFirstPassLayers(input);

            assertEquals("Trial " + trial + ": output must always be exportIndex-sorted",
                    expected, sorted);
        }
    }

    private static UndergroundPocketsLayer newPocketsLayer(String name) {
        return new UndergroundPocketsLayer(name, null, Terrain.STONE, 50, 0, 256, 50, null);
    }

    private static Bo2Layer newBo2Layer(String name) {
        return new Bo2Layer(new StubBo2ObjectProvider(name), "test " + name, null);
    }

    private static class StubBo2ObjectProvider implements Bo2ObjectProvider {
        private static final long serialVersionUID = 1L;
        private final String name;

        StubBo2ObjectProvider(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public WPObject getObject() {
            return null;
        }

        @Override
        public List<WPObject> getAllObjects() {
            return Collections.emptyList();
        }

        @Override
        public void setSeed(long seed) {
        }

        @Override
        public Bo2ObjectProvider clone() {
            return this;
        }
    }
}
