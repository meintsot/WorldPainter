package org.pepsoft.worldpainter.hytale;

import org.pepsoft.worldpainter.hytale.export.HytaleWorldExporter;

import java.util.Map;

import org.junit.Test;
import org.pepsoft.worldpainter.GameType;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.pepsoft.worldpainter.GameType.ADVENTURE;
import static org.pepsoft.worldpainter.GameType.CREATIVE;
import static org.pepsoft.worldpainter.GameType.HARDCORE;
import static org.pepsoft.worldpainter.GameType.SURVIVAL;

/**
 * Covers Hytale world configuration constants and game-type normalisation.
 *
 * <p>TP-50: configurable WorldGen type for areas outside the painted map.
 * The {@code WORLD_GEN_TYPES} list must contain exactly the three values the
 * export dialog combo box exposes, in the order users see them, and the
 * default must be {@code "Void"} so existing worlds retain pre-feature
 * behaviour. The {@code ATTRIBUTE_WORLD_GEN_TYPE} default must match
 * {@code DEFAULT_WORLD_GEN_TYPE} so {@code World.getAttribute(...)} returns
 * the right value when the attribute was never explicitly set.
 *
 * <p>TP-52: admin perms in Creative exports ride on the auto-injected
 * {@code Creative} gameplay-mode group via a wildcard. WorldPainter has no
 * handle on the host's auth UUID at export time, so the {@code users} map
 * is always empty and per-user OP entries are not written.
 */
public class HytaleWorldSettingsTest {

    @Test
    public void worldGenTypesExposesVoidStandardElevatedInOrder() {
        assertArrayEquals("Combo box order is user-visible — must not drift",
                new String[]{"Void", "Standard", "Elevated"},
                HytaleWorldSettings.WORLD_GEN_TYPES);
    }

    @Test
    public void defaultWorldGenTypeIsVoid() {
        assertEquals("Existing worlds must default to Void to preserve behaviour before TP-50",
                "Void", HytaleWorldSettings.DEFAULT_WORLD_GEN_TYPE);
    }

    @Test
    public void worldGenTypeAttributeDefaultMatchesConstant() {
        assertEquals("AttributeKey default must match DEFAULT_WORLD_GEN_TYPE",
                HytaleWorldSettings.DEFAULT_WORLD_GEN_TYPE,
                HytaleWorldSettings.ATTRIBUTE_WORLD_GEN_TYPE.defaultValue);
    }

    @Test
    public void defaultWorldGenTypeIsAValidChoice() {
        boolean defaultIsValid = false;
        for (String type : HytaleWorldSettings.WORLD_GEN_TYPES) {
            if (type.equals(HytaleWorldSettings.DEFAULT_WORLD_GEN_TYPE)) {
                defaultIsValid = true;
                break;
            }
        }
        assertTrue("DEFAULT_WORLD_GEN_TYPE must be one of WORLD_GEN_TYPES",
                defaultIsValid);
    }

    @Test
    public void normalizeGameTypeKeepsCreativeAsCreative() {
        assertEquals(CREATIVE, HytaleWorldSettings.normalizeGameType(CREATIVE));
    }

    @Test
    public void normalizeGameTypeKeepsAdventureAsAdventure() {
        assertEquals(ADVENTURE, HytaleWorldSettings.normalizeGameType(ADVENTURE));
    }

    @Test
    public void normalizeGameTypeFoldsSurvivalAndHardcoreToAdventure() {
        assertEquals("Survival is not a Hytale concept — must collapse to Adventure",
                ADVENTURE, HytaleWorldSettings.normalizeGameType(SURVIVAL));
        assertEquals("Hardcore is not a Hytale concept — must collapse to Adventure",
                ADVENTURE, HytaleWorldSettings.normalizeGameType(HARDCORE));
    }

    @Test
    public void normalizeGameTypeNullCollapsesToAdventure() {
        assertEquals("A null game type must collapse to Adventure",
                ADVENTURE, HytaleWorldSettings.normalizeGameType(null));
    }

    @Test
    public void toHytaleGameModeNameProducesUserVisibleStrings() {
        assertEquals("Creative", HytaleWorldSettings.toHytaleGameModeName(CREATIVE));
        assertEquals("Adventure", HytaleWorldSettings.toHytaleGameModeName(ADVENTURE));
        assertEquals("Adventure", HytaleWorldSettings.toHytaleGameModeName(SURVIVAL));
    }

    @Test
    public void permissionsJsonAlwaysHasEmptyUsersMap() {
        // TP-52: WorldPainter has no auth handle on the host, so we never write per-user OP
        // entries. Admin perms ride entirely on the Creative-group wildcard for Creative
        // exports; Adventure exports require manual /op self.
        for (GameType gt : GameType.values()) {
            Map<String, Object> permissions = HytaleWorldSettings.buildPermissionsJson(gt);
            @SuppressWarnings("unchecked")
            Map<String, Object> users = (Map<String, Object>) permissions.get("users");
            assertTrue("users map must be empty for " + gt + " — TP-52 grants admin via group, not per-user",
                    users.isEmpty());
        }
    }

    @Test
    public void permissionsJsonAlwaysHasEmptyDefaultGroup() {
        // Granting Default = ["*"] does NOT actually grant any permissions at runtime
        // (Hytale's PermissionsModule special-cases the Default group — verified
        // empirically when "You do not have permission" persisted with the wildcard).
        for (GameType gt : GameType.values()) {
            Map<String, Object> permissions = HytaleWorldSettings.buildPermissionsJson(gt);
            @SuppressWarnings("unchecked")
            Map<String, Object> groups = (Map<String, Object>) permissions.get("groups");
            assertArrayEquals("Default group must be empty for " + gt + "; the Creative/OP groups do the OPing",
                    new String[]{}, (String[]) groups.get("Default"));
        }
    }

    @Test
    public void permissionsJsonAlwaysDefinesOpGroupWithWildcard() {
        // OP group is retained as the manual-/op-self target — a player who types /op self
        // after joining lands in this group and gets admin perms.
        for (GameType gt : GameType.values()) {
            Map<String, Object> permissions = HytaleWorldSettings.buildPermissionsJson(gt);
            @SuppressWarnings("unchecked")
            Map<String, Object> groups = (Map<String, Object>) permissions.get("groups");
            assertArrayEquals("OP group must always exist with wildcard for " + gt,
                    new String[]{"*"}, (String[]) groups.get("OP"));
        }
    }

    @Test
    public void creativeExportDefinesCreativeGroupWithWildcard() {
        // TP-52: Hytale auto-places every joining player in the gameplay-mode group named
        // after config.json::GameMode. Granting Creative = ["*"] is therefore equivalent to
        // Minecraft's allowCommands=true Creative singleplayer default — anyone joining gets
        // admin perms via group membership without needing /op. Side effect: multiplayer
        // Creative exports give every joining player admin perms; this matches the typical
        // "creative build server" use case these exports are intended for.
        Map<String, Object> permissions = HytaleWorldSettings.buildPermissionsJson(CREATIVE);
        @SuppressWarnings("unchecked")
        Map<String, Object> groups = (Map<String, Object>) permissions.get("groups");
        assertArrayEquals("Creative group must grant [*] for Creative exports",
                new String[]{"*"}, (String[]) groups.get("Creative"));
    }

    @Test
    public void adventureExportDoesNotDefineCreativeGroupWildcard() {
        // Symmetry: only Creative-mode exports should grant Creative=[*].
        // Adventure exports must not silently turn the world into an admin-for-all
        // server.
        Map<String, Object> permissions = HytaleWorldSettings.buildPermissionsJson(ADVENTURE);
        @SuppressWarnings("unchecked")
        Map<String, Object> groups = (Map<String, Object>) permissions.get("groups");
        Object creativeGroup = groups.get("Creative");
        assertTrue("Adventure exports must not define a wildcard Creative group; "
                        + "found " + creativeGroup,
                (creativeGroup == null)
                        || (((String[]) creativeGroup).length == 0));
    }
}
