package org.pepsoft.worldpainter.hytale;

import org.pepsoft.util.AttributeKey;
import org.pepsoft.worldpainter.GameType;

import static org.pepsoft.worldpainter.GameType.ADVENTURE;
import static org.pepsoft.worldpainter.GameType.CREATIVE;

/**
 * Shared Hytale world configuration keys and defaults used by both UI and exporter.
 */
public final class HytaleWorldSettings {
    private HytaleWorldSettings() {
        // Constants class
    }

    public static final String DEFAULT_GAMEPLAY_CONFIG = "Default";

    public static final AttributeKey<String> ATTRIBUTE_GAMEPLAY_CONFIG =
            new AttributeKey<>("org.pepsoft.hytale.worldConfig.gameplayConfig", DEFAULT_GAMEPLAY_CONFIG);
    public static final AttributeKey<Boolean> ATTRIBUTE_IS_PVP_ENABLED =
            new AttributeKey<>("org.pepsoft.hytale.worldConfig.isPvpEnabled", false);
    public static final AttributeKey<Boolean> ATTRIBUTE_IS_FALL_DAMAGE_ENABLED =
            new AttributeKey<>("org.pepsoft.hytale.worldConfig.isFallDamageEnabled", true);
    public static final AttributeKey<Boolean> ATTRIBUTE_IS_SPAWNING_NPC =
            new AttributeKey<>("org.pepsoft.hytale.worldConfig.isSpawningNpc", true);

    /**
     * Hytale supports Adventure and Creative modes.
     */
    public static GameType normalizeGameType(GameType gameType) {
        return (gameType == CREATIVE) ? CREATIVE : ADVENTURE;
    }

    /**
     * WorldConfig.GameMode value expected by Hytale config.json.
     */
    public static String toHytaleGameModeName(GameType gameType) {
        return (normalizeGameType(gameType) == CREATIVE) ? "Creative" : "Adventure";
    }
}
