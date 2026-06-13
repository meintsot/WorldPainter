package org.pepsoft.worldpainter;

import org.junit.Test;
import org.pepsoft.minecraft.Material;

import static org.junit.Assert.assertEquals;
import static org.pepsoft.worldpainter.DefaultPlugin.HYTALE;
import static org.pepsoft.worldpainter.hytale.HytaleBlockRegistry.HYTALE_NAMESPACE;

/**
 * Regression test (TP-130): a custom material built on a <em>modded</em> Hytale block — i.e. a
 * {@code hytale:}-namespaced block that is NOT in the built-in {@link org.pepsoft.worldpainter.hytale.HytaleBlockRegistry}
 * — must survive a round-trip through {@link MaterialSelector} (the editor reopen path).
 *
 * <p>Community report (aziraphale5547, 2026-06-06): "I can't get modded blocks to persist in custom
 * materials … this part keeps resetting … update does persist but only if you do it in one go and
 * does not persist in editor window."
 *
 * <p>Root cause: {@link MaterialSelector#setMaterial(Material)} routed every block whose namespace
 * equals the primary namespace ({@code hytale} in Hytale mode) to the <em>non-editable</em> primary
 * combo box, whose model only contains registry block names. Swing's
 * {@code JComboBox.setSelectedItem()} silently rejects values absent from a non-editable model, so
 * the combo kept a registry block and {@link MaterialSelector#getMaterial()} read that back —
 * overwriting the modded block on save.
 */
public class MaterialSelectorModdedBlockTest {

    /** A clearly fake Hytale block name that is not part of the built-in registry. */
    private static final String MODDED_BLOCK = "ModdedBlock_TP130_NotInRegistry";

    @Test
    public void moddedHytaleBlockSurvivesRoundTripThroughMaterialSelector() {
        final String moddedName = HYTALE_NAMESPACE + ":" + MODDED_BLOCK;
        final Material moddedMaterial = Material.get(moddedName);

        MaterialSelector selector = new MaterialSelector();
        selector.setPlatform(HYTALE);
        selector.setMaterial(moddedMaterial);

        assertEquals("A modded (non-registry) Hytale block loaded into the editor must be read back "
                        + "unchanged, otherwise the custom material is silently overwritten with a "
                        + "registry block on save (TP-130)",
                moddedName, selector.getMaterial().name);
    }

    @Test
    public void builtInRegistryHytaleBlockStillSurvivesRoundTrip() {
        // Guard the TP-130 fix: a normal built-in registry block must continue to round-trip via the
        // primary ("Hytale:") radio path and not be affected by the new modded-block routing.
        final String registryName = HYTALE_NAMESPACE + ":Soil_Dirt";
        final Material registryMaterial = Material.get(registryName);

        MaterialSelector selector = new MaterialSelector();
        selector.setPlatform(HYTALE);
        selector.setMaterial(registryMaterial);

        assertEquals("A built-in registry Hytale block must round-trip unchanged through the editor",
                registryName, selector.getMaterial().name);
    }
}
