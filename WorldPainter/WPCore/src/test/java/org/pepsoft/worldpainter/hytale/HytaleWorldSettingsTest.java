package org.pepsoft.worldpainter.hytale;

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
        // whether to add Player → OP to permissions.json.
        for (GameType gt : GameType.values()) {
            boolean shouldAutoOp = HytaleWorldSettings.normalizeGameType(gt) == CREATIVE;
            assertEquals("Only CREATIVE should auto-OP Player; got " + gt,
                    gt == CREATIVE, shouldAutoOp);
        }
    }
}
