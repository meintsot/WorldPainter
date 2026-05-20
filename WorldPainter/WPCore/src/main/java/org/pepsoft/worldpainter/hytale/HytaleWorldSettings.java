package org.pepsoft.worldpainter.hytale;

import org.pepsoft.worldpainter.hytale.chunk.HytaleChunk;

import java.util.LinkedHashMap;
import java.util.Map;

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
    public static final String DEFAULT_WORLD_GEN_TYPE = "Void";
    public static final String[] WORLD_GEN_TYPES = {"Void", "Standard", "Elevated"};

    public static final AttributeKey<String> ATTRIBUTE_GAMEPLAY_CONFIG =
            new AttributeKey<>("org.pepsoft.hytale.worldConfig.gameplayConfig", DEFAULT_GAMEPLAY_CONFIG);
    public static final AttributeKey<Boolean> ATTRIBUTE_IS_PVP_ENABLED =
            new AttributeKey<>("org.pepsoft.hytale.worldConfig.isPvpEnabled", false);
    public static final AttributeKey<Boolean> ATTRIBUTE_IS_FALL_DAMAGE_ENABLED =
            new AttributeKey<>("org.pepsoft.hytale.worldConfig.isFallDamageEnabled", true);
    public static final AttributeKey<Boolean> ATTRIBUTE_IS_SPAWNING_NPC =
            new AttributeKey<>("org.pepsoft.hytale.worldConfig.isSpawningNpc", true);
    public static final AttributeKey<String> ATTRIBUTE_WORLD_GEN_TYPE =
            new AttributeKey<>("org.pepsoft.hytale.worldConfig.worldGenType", DEFAULT_WORLD_GEN_TYPE);
    /**
     * When {@code true}, plants painted via {@link HytalePlantsLayer} are exported with support
     * value {@link HytaleChunk#SUPPORT_DECORATIVE} (= IS_DECO = 15) so Hytale's physics cascade
     * does not chain-break them when the player removes one. Tradeoff: IS_DECO blocks bypass the
     * gathering interaction, so the player gets the block itself instead of the configured drops
     * (e.g. berries from a bush). Default {@code false} — drops > chain-break for player UX. Users
     * who care about preserving large painted plant patches in-game can opt in at export time.
     */
    public static final AttributeKey<Boolean> ATTRIBUTE_PLANTS_PHYSICS_EXEMPT =
            new AttributeKey<>("org.pepsoft.hytale.worldConfig.plantsPhysicsExempt", false);

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

    /**
     * Build the {@code permissions.json} payload Hytale expects in a save's root directory.
     *
     * <p>TP-52: WorldPainter has no handle on the host's Hytale auth UUID at export time, so
     * the {@code users} map is always empty. Per-world admin perms ride entirely on group
     * membership: every export defines {@code OP = ["*"]}, and Creative exports additionally
     * define {@code Creative = ["*"]}. Hytale auto-places every joining player in the
     * gameplay-mode group named after {@code config.json::GameMode}, so granting {@code ["*"]}
     * to the {@code Creative} group is the Hytale-equivalent of Minecraft's
     * {@code allowCommands=true} Creative singleplayer default — anyone joining a Creative
     * export gets admin perms via group membership.
     *
     * <p>{@code Default = []} on purpose. Granting {@code Default = ["*"]} does NOT bypass
     * permission checks at runtime; Hytale's PermissionsModule special-cases the Default
     * group and ignores wildcards there (verified empirically — admin commands still report
     * "You do not have permission"). The {@code OP} group definition is retained so a player
     * who runs {@code /op self} after joining lands in a group that actually grants perms.
     *
     * <p>Adventure exports define no gameplay-mode wildcard, so normal multiplayer Adventure
     * permissions apply and the host must {@code /op self} once.
     *
     * @param gameType the world's game type; only {@link GameType#CREATIVE} triggers the
     *                 Creative-group wildcard
     */
    public static Map<String, Object> buildPermissionsJson(GameType gameType) {
        Map<String, Object> permissions = new LinkedHashMap<>();
        permissions.put("users", new LinkedHashMap<>());
        Map<String, Object> groups = new LinkedHashMap<>();
        groups.put("Default", new String[0]);
        groups.put("OP", new String[]{"*"});
        if (normalizeGameType(gameType) == CREATIVE) {
            groups.put("Creative", new String[]{"*"});
        }
        permissions.put("groups", groups);
        return permissions;
    }
}
