package org.pepsoft.worldpainter.hytale;

import org.junit.After;
import org.junit.Test;
import org.pepsoft.minecraft.Material;
import org.pepsoft.worldpainter.MixedMaterial;
import org.pepsoft.worldpainter.MixedMaterial.Row;
import org.pepsoft.worldpainter.Terrain;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Regression test (TP-129): the Global Operations "Fill with terrain type" combo
 * on Hytale worlds was populated solely from {@link HytaleTerrain#getPickListWithIcons()},
 * which omits configured Custom Terrains. As a result custom terrains were absent
 * from the dropdown, and the eyedropper — which calls
 * {@code comboBox.setSelectedItem(customTerrain)} — was silently rejected by the
 * non-editable combo (leaving the default Ashen Sand selected).
 *
 * <p>{@link HytaleTerrainHelper#getAllHytaleTerrainsWithCustomTerrains()} must return
 * every built-in {@link HytaleTerrain} <em>plus</em> every configured custom
 * {@link Terrain} slot, so both the dropdown and the eyedropper can resolve them.
 */
public class HytaleTerrainHelperCustomTerrainPickListTest {

    private static final int CONFIGURED_INDEX = 0;
    private static final int UNCONFIGURED_INDEX = 1;

    private MixedMaterial savedConfigured;
    private MixedMaterial savedUnconfigured;

    @After
    public void restoreCustomMaterials() {
        Terrain.setCustomMaterial(CONFIGURED_INDEX, savedConfigured);
        Terrain.setCustomMaterial(UNCONFIGURED_INDEX, savedUnconfigured);
    }

    @Test
    public void includesBuiltInHytaleTerrainsAndConfiguredCustomTerrains() {
        savedConfigured = Terrain.getCustomMaterial(CONFIGURED_INDEX);
        savedUnconfigured = Terrain.getCustomMaterial(UNCONFIGURED_INDEX);

        final MixedMaterial customMix = new MixedMaterial("Quartzite + Quartzite Gravel (TP-129)",
                new Row[] {
                        new Row(Material.STONE, 1, 1.0f),
                        new Row(Material.GRAVEL, 1, 1.0f),
                },
                -1, null);
        Terrain.setCustomMaterial(CONFIGURED_INDEX, customMix);
        Terrain.setCustomMaterial(UNCONFIGURED_INDEX, null);

        final Set<Object> items = new HashSet<>(Arrays.asList(
                HytaleTerrainHelper.getAllHytaleTerrainsWithCustomTerrains()));

        for (HytaleTerrain builtIn : HytaleTerrain.getPickListWithIcons()) {
            assertTrue("Built-in Hytale terrain " + builtIn.getName() + " must remain in the list",
                    items.contains(builtIn));
        }

        assertTrue("A configured Custom Terrain must appear in the Hytale terrain list",
                items.contains(Terrain.getCustomTerrain(CONFIGURED_INDEX)));

        assertFalse("An unconfigured Custom Terrain must not appear in the Hytale terrain list",
                items.contains(Terrain.getCustomTerrain(UNCONFIGURED_INDEX)));
    }
}
