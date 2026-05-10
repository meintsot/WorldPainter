package org.pepsoft.worldpainter.hytale;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.pepsoft.worldpainter.GameType;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
 * <p>TP-52: auto-OP for Creative exports. Hytale's {@code permissions.json}
 * keys the {@code users} map by player auth UUID; the original {@code "Player": "OP"}
 * string-value entry the exporter wrote made Hytale's PermissionsModule throw
 * {@code IllegalArgumentException: Invalid UUID string: Player} from
 * {@code UUID.fromString} during {@code syncLoad()}, which crashed the entire
 * module and left Creative worlds unjoinable. The corrected exporter discovers
 * the user's persistent client UUID by scanning their existing Hytale saves at
 * {@code %APPDATA%/Hytale/UserData/Saves/&#42;/universe/players/&#42;.json} and writes
 * that UUID into the {@code users} map with groups {@code ["OP"]} for Creative
 * exports. Hytale merges in the gameplay-mode group on first join. If no
 * existing UUID is discoverable (player has never launched Hytale), the
 * {@code users} map is empty — the file still parses and the player can
 * {@code /op self} once.
 */
public class HytaleWorldSettingsTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private static final UUID TEST_UUID = UUID.fromString("1c3e2764-8430-4ee7-aafe-abe7a320b3bf");

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
        // whether to add the player's UUID to the OP group in permissions.json.
        for (GameType gt : GameType.values()) {
            boolean shouldAutoOp = HytaleWorldSettings.normalizeGameType(gt) == CREATIVE;
            assertEquals("Only CREATIVE should auto-OP players; got " + gt,
                    gt == CREATIVE, shouldAutoOp);
        }
    }

    @Test
    public void permissionsJsonAddsUserUuidToOpGroupForCreative() {
        Map<String, Object> permissions = HytaleWorldSettings.buildPermissionsJson(CREATIVE, TEST_UUID);
        @SuppressWarnings("unchecked")
        Map<String, Object> users = (Map<String, Object>) permissions.get("users");
        assertTrue("Player's UUID must appear as a key in the users map",
                users.containsKey(TEST_UUID.toString()));
        @SuppressWarnings("unchecked")
        Map<String, Object> userEntry = (Map<String, Object>) users.get(TEST_UUID.toString());
        assertArrayEquals("Creative export must place the player in the OP group so they spawn already opped",
                new String[]{"OP"}, (String[]) userEntry.get("groups"));
    }

    @Test
    public void permissionsJsonOmitsUuidWhenUnknownInCreative() {
        Map<String, Object> permissions = HytaleWorldSettings.buildPermissionsJson(CREATIVE, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> users = (Map<String, Object>) permissions.get("users");
        assertTrue("Without a discoverable UUID we cannot pre-OP; users must be empty so the file still parses",
                users.isEmpty());
    }

    @Test
    public void permissionsJsonOmitsUuidForAdventureEvenWhenKnown() {
        Map<String, Object> permissions = HytaleWorldSettings.buildPermissionsJson(ADVENTURE, TEST_UUID);
        @SuppressWarnings("unchecked")
        Map<String, Object> users = (Map<String, Object>) permissions.get("users");
        assertTrue("Adventure exports must not auto-OP the player", users.isEmpty());
    }

    @Test
    public void permissionsJsonAlwaysHasEmptyDefaultGroup() {
        // Hytale's working permissions.json shape uses Default = []; ["*"] in Default
        // does NOT actually grant any permissions at runtime (verified empirically —
        // players still got "You do not have permission to use this command").
        for (GameType gt : GameType.values()) {
            Map<String, Object> permissions = HytaleWorldSettings.buildPermissionsJson(gt, null);
            @SuppressWarnings("unchecked")
            Map<String, Object> groups = (Map<String, Object>) permissions.get("groups");
            assertArrayEquals("Default group must be empty for " + gt + "; the OP group does the OPing",
                    new String[]{}, (String[]) groups.get("Default"));
        }
    }

    @Test
    public void permissionsJsonAlwaysDefinesOpGroupWithWildcard() {
        for (GameType gt : GameType.values()) {
            Map<String, Object> permissions = HytaleWorldSettings.buildPermissionsJson(gt, TEST_UUID);
            @SuppressWarnings("unchecked")
            Map<String, Object> groups = (Map<String, Object>) permissions.get("groups");
            assertArrayEquals("OP group must always exist with wildcard for " + gt,
                    new String[]{"*"}, (String[]) groups.get("OP"));
        }
    }

    @Test
    public void detectHytalePlayerUuidFindsUuidFromExistingSave() throws Exception {
        // A save the user has played has universe/players/<uuid>.json
        File savesDir = temp.newFolder("Saves");
        File playersDir = new File(savesDir, "Some Played World/universe/players");
        assertTrue("Failed to create test players dir", playersDir.mkdirs());
        new File(playersDir, TEST_UUID + ".json").createNewFile();

        Optional<UUID> uuid = HytaleWorldSettings.detectHytalePlayerUuid(savesDir);
        assertTrue("Should discover the UUID from the existing save", uuid.isPresent());
        assertEquals(TEST_UUID, uuid.get());
    }

    @Test
    public void detectHytalePlayerUuidReturnsEmptyWhenNoSavesExist() throws Exception {
        File emptySavesDir = temp.newFolder("EmptySaves");
        Optional<UUID> uuid = HytaleWorldSettings.detectHytalePlayerUuid(emptySavesDir);
        assertFalse("No saves means no discoverable UUID — must not throw", uuid.isPresent());
    }

    @Test
    public void detectHytalePlayerUuidReturnsEmptyWhenSavesDirMissing() {
        File missing = new File(temp.getRoot(), "DoesNotExist");
        Optional<UUID> uuid = HytaleWorldSettings.detectHytalePlayerUuid(missing);
        assertFalse("Missing saves dir means no UUID — must not throw", uuid.isPresent());
    }

    @Test
    public void detectHytalePlayerUuidIgnoresNonUuidFilenames() throws Exception {
        // memories.json sits alongside player files in some universe dirs; must be ignored.
        File savesDir = temp.newFolder("Saves");
        File playersDir = new File(savesDir, "World/universe/players");
        assertTrue(playersDir.mkdirs());
        new File(playersDir, "memories.json").createNewFile();
        new File(playersDir, "not-a-uuid.json").createNewFile();

        Optional<UUID> uuid = HytaleWorldSettings.detectHytalePlayerUuid(savesDir);
        assertFalse("Filenames that don't parse as UUIDs must not be returned", uuid.isPresent());
    }

    @Test
    public void detectHytalePlayerUuidPicksMostRecentlyModified() throws Exception {
        // If multiple saves exist (e.g. a shared install), the UUID from the most
        // recently played save is the user we should auto-OP.
        File savesDir = temp.newFolder("Saves");
        UUID older = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID newer = UUID.fromString("22222222-2222-2222-2222-222222222222");

        File p1 = new File(savesDir, "OldWorld/universe/players");
        assertTrue(p1.mkdirs());
        Path olderFile = new File(p1, older + ".json").toPath();
        Files.createFile(olderFile);
        Files.setLastModifiedTime(olderFile, java.nio.file.attribute.FileTime.fromMillis(1_000L));

        File p2 = new File(savesDir, "NewWorld/universe/players");
        assertTrue(p2.mkdirs());
        Path newerFile = new File(p2, newer + ".json").toPath();
        Files.createFile(newerFile);
        Files.setLastModifiedTime(newerFile, java.nio.file.attribute.FileTime.fromMillis(2_000_000L));

        Optional<UUID> uuid = HytaleWorldSettings.detectHytalePlayerUuid(savesDir);
        assertTrue(uuid.isPresent());
        assertEquals("Most recently modified player file wins", newer, uuid.get());
    }
}
