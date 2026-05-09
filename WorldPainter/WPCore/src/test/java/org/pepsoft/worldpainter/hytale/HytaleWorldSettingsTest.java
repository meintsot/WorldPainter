package org.pepsoft.worldpainter.hytale;

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
 * <p>TP-52: auto-OP for Creative exports. {@link HytaleWorldSettings#normalizeGameType}
 * is the predicate that drives whether {@code permissions.json} marks
 * {@code Player} as {@code OP}; only {@code CREATIVE} should normalise to
 * {@code CREATIVE}, everything else (Adventure, Survival, Hardcore) must
 * collapse to {@code ADVENTURE} so non-Creative exports do not auto-OP.
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
        assertEquals("A null game type must not auto-OP the player; collapse to Adventure",
                ADVENTURE, HytaleWorldSettings.normalizeGameType(null));
    }

    @Test
    public void toHytaleGameModeNameProducesUserVisibleStrings() {
        assertEquals("Creative", HytaleWorldSettings.toHytaleGameModeName(CREATIVE));
        assertEquals("Adventure", HytaleWorldSettings.toHytaleGameModeName(ADVENTURE));
        assertEquals("Adventure", HytaleWorldSettings.toHytaleGameModeName(SURVIVAL));
    }

    @Test
    public void onlyCreativeQualifiesForAutoOpInPermissionsJson() {
        // TP-52: this is the exact predicate HytaleWorldExporter uses to decide
        // whether to grant the Default group full permissions in permissions.json.
        for (GameType gt : GameType.values()) {
            boolean shouldAutoOp = HytaleWorldSettings.normalizeGameType(gt) == CREATIVE;
            assertEquals("Only CREATIVE should auto-OP players; got " + gt,
                    gt == CREATIVE, shouldAutoOp);
        }
    }

    /**
     * TP-52 (revised): Hytale's {@code permissions.json} keys users by player UUID,
     * not by display name — the original {@code "Player": "OP"} entry the exporter
     * wrote was format-invalid and silently ignored by Hytale, leaving the player
     * unprivileged. WorldPainter cannot know the player's auth UUID at export time,
     * so the fix grants the {@code Default} group {@code ["*"]} (all permissions)
     * for Creative exports. Every user joining a Creative-mode export is therefore
     * effectively OP without typing {@code /op} themselves.
     */
    @Test
    public void permissionsJsonGrantsDefaultGroupWildcardForCreative() {
        Map<String, Object> permissions = HytaleWorldSettings.buildPermissionsJson(CREATIVE);
        @SuppressWarnings("unchecked")
        Map<String, Object> groups = (Map<String, Object>) permissions.get("groups");
        assertArrayEquals("Creative export must grant Default group all permissions so the first joiner is OP-equivalent without typing /op",
                new String[]{"*"}, (String[]) groups.get("Default"));
    }

    @Test
    public void permissionsJsonLeavesDefaultGroupEmptyForAdventure() {
        Map<String, Object> permissions = HytaleWorldSettings.buildPermissionsJson(ADVENTURE);
        @SuppressWarnings("unchecked")
        Map<String, Object> groups = (Map<String, Object>) permissions.get("groups");
        assertArrayEquals("Non-Creative exports must keep Default empty so players are not silently OPed",
                new String[]{}, (String[]) groups.get("Default"));
    }

    @Test
    public void permissionsJsonAlwaysDefinesOpGroupWithWildcard() {
        for (GameType gt : GameType.values()) {
            Map<String, Object> permissions = HytaleWorldSettings.buildPermissionsJson(gt);
            @SuppressWarnings("unchecked")
            Map<String, Object> groups = (Map<String, Object>) permissions.get("groups");
            assertArrayEquals("OP group must always exist with wildcard for " + gt,
                    new String[]{"*"}, (String[]) groups.get("OP"));
        }
    }

    @Test
    public void permissionsJsonHasEmptyUsersMap() {
        // The exporter cannot know the singleplayer player's auth UUID, so users
        // must be empty — privileges are granted via the Default group instead.
        Map<String, Object> permissions = HytaleWorldSettings.buildPermissionsJson(CREATIVE);
        @SuppressWarnings("unchecked")
        Map<String, Object> users = (Map<String, Object>) permissions.get("users");
        assertTrue("users map must be empty — Hytale keys users by UUID which WP cannot know at export",
                users.isEmpty());
    }
}
