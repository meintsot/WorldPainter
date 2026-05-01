package org.pepsoft.worldpainter.tools;

import org.junit.Test;
import org.pepsoft.worldpainter.hytale.HytaleEnvironmentData;
import org.pepsoft.worldpainter.hytale.HytaleEnvironmentLayer;
import org.pepsoft.worldpainter.hytale.HytaleEntityLayer;
import org.pepsoft.worldpainter.hytale.HytaleFluidLayer;
import org.pepsoft.worldpainter.hytale.HytalePrefabLayer;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class EyedropperDiscreteLayerTest {

    @Test
    public void environment_validId_returnsDisplayName() {
        int id = HytaleEnvironmentData.getByName("Env_Zone1_Forests").getId();

        Eyedropper.DiscreteEntry entry = Eyedropper.discreteLayerEntry(
                HytaleEnvironmentLayer.INSTANCE, id);

        assertNotNull("entry must not be null", entry);
        assertNotNull("icon must not be null", entry.icon);
        assertTrue(
                "name should contain the display name 'Forests', got: " + entry.name,
                entry.name.contains("Forests"));
        assertTrue(
                "name should be prefixed with the layer name, got: " + entry.name,
                entry.name.startsWith("Hytale Environment"));
    }

    @Test
    public void fluid_lava_returnsLavaName() {
        Eyedropper.DiscreteEntry entry = Eyedropper.discreteLayerEntry(
                HytaleFluidLayer.INSTANCE, HytaleFluidLayer.FLUID_LAVA);

        assertNotNull(entry);
        assertNotNull(entry.icon);
        assertTrue("expected name to contain 'Lava', got: " + entry.name,
                entry.name.contains("Lava"));
        assertTrue("expected layer-name prefix, got: " + entry.name,
                entry.name.startsWith("Hytale Fluid"));
    }

    @Test
    public void fluid_legacyValue9_migratesToLava() {
        // Old saves stored fluid value 9 to mean lava; the eyedropper must
        // resolve it through HytaleFluidLayer.normalizeFluidValue rather
        // than indexing FLUID_NAMES with the raw 9.
        Eyedropper.DiscreteEntry entry = Eyedropper.discreteLayerEntry(
                HytaleFluidLayer.INSTANCE, 9);

        assertNotNull(entry);
        assertTrue("expected legacy 9 to migrate to 'Lava', got: " + entry.name,
                entry.name.contains("Lava"));
    }

    @Test
    public void entity_dense_returnsDenseName() {
        Eyedropper.DiscreteEntry entry = Eyedropper.discreteLayerEntry(
                HytaleEntityLayer.INSTANCE, HytaleEntityLayer.DENSITY_DENSE);

        assertNotNull(entry);
        assertNotNull(entry.icon);
        assertTrue("expected name to contain 'Dense', got: " + entry.name,
                entry.name.contains("Dense"));
        assertTrue("expected layer-name prefix 'Entities', got: " + entry.name,
                entry.name.startsWith("Entities"));
    }

    @Test
    public void prefab_dungeon_returnsDungeonName() {
        Eyedropper.DiscreteEntry entry = Eyedropper.discreteLayerEntry(
                HytalePrefabLayer.INSTANCE, HytalePrefabLayer.PREFAB_DUNGEON);

        assertNotNull(entry);
        assertNotNull(entry.icon);
        assertTrue("expected name to contain 'Dungeon', got: " + entry.name,
                entry.name.contains("Dungeon"));
        assertTrue("expected layer-name prefix 'Prefabs', got: " + entry.name,
                entry.name.startsWith("Prefabs"));
    }
}
