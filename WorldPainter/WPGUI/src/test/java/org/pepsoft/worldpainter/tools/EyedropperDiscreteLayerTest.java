package org.pepsoft.worldpainter.tools;

import org.junit.Test;
import org.pepsoft.worldpainter.hytale.HytaleEnvironmentData;
import org.pepsoft.worldpainter.hytale.HytaleEnvironmentLayer;

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
}
