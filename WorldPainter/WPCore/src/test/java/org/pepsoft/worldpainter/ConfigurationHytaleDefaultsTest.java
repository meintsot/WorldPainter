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
}
