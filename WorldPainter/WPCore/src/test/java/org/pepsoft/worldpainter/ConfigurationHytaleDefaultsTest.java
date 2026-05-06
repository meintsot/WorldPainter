package org.pepsoft.worldpainter;

import org.junit.Test;
import org.pepsoft.worldpainter.hytale.HytaleWorldSettings;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.pepsoft.worldpainter.GameType.ADVENTURE;
import static org.pepsoft.worldpainter.GameType.CREATIVE;

public class ConfigurationHytaleDefaultsTest {

    @Test
    public void appliesMinecraftDefaultsToNonHytaleWorlds() {
        Configuration config = new Configuration();
        config.setDefaultCreateGoodiesChest(false);
        config.setDefaultMapFeatures(true);
        config.setDefaultGameType(CREATIVE);
        config.setDefaultAllowCheats(true);
        config.setDefaultHytaleFallDamageEnabled(false);
        config.setDefaultHytaleNpcSpawningEnabled(false);
        config.setDefaultHytaleGameType(ADVENTURE);
        config.setDefaultHytalePvpEnabled(false);

        World2 world = new World2(DefaultPlugin.JAVA_ANVIL_1_20_5, DefaultPlugin.JAVA_ANVIL_1_20_5.minZ,
                DefaultPlugin.JAVA_ANVIL_1_20_5.standardMaxHeight);
        config.applyDefaultGameSettings(world);

        assertFalse(world.isCreateGoodiesChest());
        assertTrue(world.isMapFeatures());
        assertEquals(CREATIVE, world.getGameType());
        assertTrue(world.isAllowCheats());
    }

    @Test
    public void appliesHytaleDefaultsToHytaleWorlds() {
        Configuration config = new Configuration();
        config.setDefaultCreateGoodiesChest(true);
        config.setDefaultMapFeatures(true);
        config.setDefaultGameType(CREATIVE);
        config.setDefaultAllowCheats(true);
        config.setDefaultHytaleFallDamageEnabled(false);
        config.setDefaultHytaleNpcSpawningEnabled(false);
        config.setDefaultHytaleGameType(CREATIVE);
        config.setDefaultHytalePvpEnabled(true);

        World2 world = new World2(DefaultPlugin.HYTALE, DefaultPlugin.HYTALE.minZ, DefaultPlugin.HYTALE.standardMaxHeight);
        config.applyDefaultGameSettings(world);

        assertEquals(CREATIVE, world.getGameType());
        assertEquals(Boolean.FALSE, world.getAttribute(HytaleWorldSettings.ATTRIBUTE_IS_FALL_DAMAGE_ENABLED).orElse(null));
        assertEquals(Boolean.FALSE, world.getAttribute(HytaleWorldSettings.ATTRIBUTE_IS_SPAWNING_NPC).orElse(null));
        assertEquals(Boolean.TRUE, world.getAttribute(HytaleWorldSettings.ATTRIBUTE_IS_PVP_ENABLED).orElse(null));
    }

    @Test
    public void hytalePlatformOnlyAdvertisesSupportedGameTypes() {
        assertEquals(Arrays.asList(ADVENTURE, CREATIVE), DefaultPlugin.HYTALE.supportedGameTypes);
    }

    /**
     * TP-38: Hytale grid/contour defaults must be platform-appropriate, not
     * the Minecraft-oriented {@code 128} block grid. Hytale chunks are 32
     * blocks across so the grid default is 32. These getters back the
     * "Edit > Preferences > Defaults" platform-specific defaults that
     * {@code WorldFactory} consults at world creation.
     */
    @Test
    public void freshConfigurationExposesHytaleGridDefaultsAt32Enabled() {
        Configuration config = new Configuration();

        assertTrue("Hytale grid is enabled by default — chunks-aligned grid is useful",
                config.isDefaultHytaleGridEnabled());
        assertEquals("Hytale grid default size matches chunk size (32)",
                32, config.getDefaultHytaleGridSize());
    }

    @Test
    public void freshConfigurationExposesHytaleContourDefaults() {
        Configuration config = new Configuration();

        assertTrue("Hytale contour overlay enabled by default",
                config.isDefaultHytaleContoursEnabled());
        assertEquals("Hytale contour separation default is 10 blocks",
                10, config.getDefaultHytaleContourSeparation());
    }

    @Test
    public void hytaleGridSettersAreIndependentFromMinecraftSettings() {
        // The bug TP-38 fixed was a single shared default. Verify the per-platform
        // setters do not bleed into each other.
        Configuration config = new Configuration();
        config.setDefaultGridEnabled(false);
        config.setDefaultGridSize(128);
        config.setDefaultHytaleGridEnabled(true);
        config.setDefaultHytaleGridSize(32);

        assertFalse("Minecraft default unaffected by Hytale setter", config.isDefaultGridEnabled());
        assertEquals(128, config.getDefaultGridSize());
        assertTrue("Hytale default unaffected by Minecraft setter", config.isDefaultHytaleGridEnabled());
        assertEquals(32, config.getDefaultHytaleGridSize());
    }
}
