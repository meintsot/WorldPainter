package org.pepsoft.worldpainter.hytale;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
     * <p>TP-52: Hytale's PermissionsModule passes every key in the {@code users} map through
     * {@link UUID#fromString} during {@code syncLoad()}. The original exporter wrote
     * {@code "Player": "OP"} — a non-UUID key with a string value — which crashed parsing
     * with {@code IllegalArgumentException: Invalid UUID string: Player} and disabled the
     * entire module, leaving Creative worlds unjoinable. The corrected payload writes the
     * player's discovered UUID into the {@code users} map with groups {@code ["OP"]} so
     * Hytale spawns them already opped on first join. Empirically, granting
     * {@code Default = ["*"]} does NOT bypass permission checks — admin commands still
     * report "You do not have permission" — so we keep {@code Default} empty and rely on
     * explicit group membership instead.
     *
     * <p>TP-52 follow-up: Hytale's join logic differs by version. Pre-release builds
     * (developer's local install) APPEND the gameplay-mode groups to the user's existing
     * {@code groups} list, preserving {@code OP}; release builds (Ferstborn's report)
     * REPLACE the list with the gameplay-mode defaults, stripping {@code OP}. To survive
     * both, Creative exports also grant {@code ["*"]} to the runtime-injected
     * {@code Creative} gameplay-mode group itself. The user is always in that group after
     * Hytale's sync regardless of append-vs-replace semantics, so admin permissions are
     * retained via Creative-group membership even when {@code OP} is removed from the
     * user's groups field. In multiplayer Creative WP exports this also gives every
     * joining player admin permissions, which matches the "creative build server" use case
     * these exports are intended for. Adventure exports remain unchanged — no wildcard on
     * any gameplay-mode group, so normal multiplayer adventure permissions apply.
     *
     * @param gameType   the world's game type; only {@link GameType#CREATIVE} triggers
     *                   auto-OP and the Creative-group wildcard
     * @param playerUuid the user's persistent Hytale client UUID, or {@code null} if unknown.
     *                   When {@code null} the {@code users} map is left empty so the file
     *                   still parses cleanly; the player can {@code /op self} once. (The
     *                   Creative-group wildcard still applies if the export is Creative.)
     */
    public static Map<String, Object> buildPermissionsJson(GameType gameType, UUID playerUuid) {
        Map<String, Object> permissions = new LinkedHashMap<>();
        Map<String, Object> users = new LinkedHashMap<>();
        final boolean creative = (normalizeGameType(gameType) == CREATIVE);
        if (creative && (playerUuid != null)) {
            Map<String, Object> userEntry = new LinkedHashMap<>();
            userEntry.put("groups", new String[]{"OP"});
            users.put(playerUuid.toString(), userEntry);
        }
        permissions.put("users", users);
        Map<String, Object> groups = new LinkedHashMap<>();
        groups.put("Default", new String[0]);
        groups.put("OP", new String[]{"*"});
        if (creative) {
            groups.put("Creative", new String[]{"*"});
        }
        permissions.put("groups", groups);
        return permissions;
    }

    /**
     * Default Hytale singleplayer saves directory: {@code %APPDATA%/Hytale/UserData/Saves}.
     * Returns {@code null} on platforms or installs where this directory cannot be located
     * (e.g. {@code APPDATA} unset, or Hytale never installed).
     */
    public static File defaultHytaleSavesDir() {
        String appdata = System.getenv("APPDATA");
        if ((appdata == null) || appdata.isEmpty()) {
            return null;
        }
        return new File(appdata, "Hytale/UserData/Saves");
    }

    /**
     * Discover the user's persistent Hytale client UUID by scanning their existing saves.
     *
     * <p>Hytale stores each player's per-world state at
     * {@code <saveDir>/universe/players/<uuid>.json}. The UUID embedded in those filenames
     * is the user's local auth UUID — stable across saves on the same Hytale install. The
     * exporter uses this to pre-OP the user in newly exported Creative worlds, since
     * WorldPainter has no other handle on the user's client identity.
     *
     * <p>Returns the UUID from the most recently modified player file across all saves so
     * that on a shared install the most recently active user wins. Returns {@link
     * Optional#empty()} when the directory is missing, has no saves, or no filename parses
     * as a UUID (the typical first-time-user case) — callers must fall back to writing a
     * permissions.json without auto-OP.
     */
    public static Optional<UUID> detectHytalePlayerUuid(File savesDir) {
        if ((savesDir == null) || !savesDir.isDirectory()) {
            return Optional.empty();
        }
        FileFilter dirsOnly = File::isDirectory;
        FilenameFilter jsonFiles = (d, name) -> name.endsWith(".json");

        File mostRecent = null;
        File[] worldDirs = savesDir.listFiles(dirsOnly);
        if (worldDirs == null) {
            return Optional.empty();
        }
        for (File worldDir : worldDirs) {
            File playersDir = new File(worldDir, "universe/players");
            if (!playersDir.isDirectory()) {
                continue;
            }
            File[] playerFiles = playersDir.listFiles(jsonFiles);
            if (playerFiles == null) {
                continue;
            }
            for (File playerFile : playerFiles) {
                String name = playerFile.getName();
                String stem = name.substring(0, name.length() - ".json".length());
                try {
                    UUID.fromString(stem);
                } catch (IllegalArgumentException notAUuid) {
                    continue;
                }
                if ((mostRecent == null) || (playerFile.lastModified() > mostRecent.lastModified())) {
                    mostRecent = playerFile;
                }
            }
        }
        if (mostRecent == null) {
            return Optional.empty();
        }
        String stem = mostRecent.getName().substring(0, mostRecent.getName().length() - ".json".length());
        return Optional.of(UUID.fromString(stem));
    }
}
