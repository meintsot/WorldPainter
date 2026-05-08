package org.pepsoft.minecraft;

import org.junit.Test;
import org.pepsoft.worldpainter.DefaultPlugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * TP-49: Hytale prefab branch blocks didn't rotate/mirror with the prefab.
 *
 * <p>{@code HytalePrefabJsonObject.placeBlocks} stores a Hytale-native
 * {@code hytale_rotation} integer property (0–63) on each block. The
 * Minecraft-style {@code Material.rotate}/{@code Material.mirror} only knew
 * about Minecraft orientation schemes (FACING, AXIS, etc.), so a rotated or
 * mirrored prefab kept its branches pointing in the original direction.
 *
 * <p>The fix transforms the {@code yaw} component of {@code hytale_rotation}
 * while leaving pitch and roll untouched, since branch directionality is
 * encoded entirely in yaw. Hytale encodes {@code RotationTuple.index()} as
 * {@code rotation = roll * 16 + pitch * 4 + yaw}. Yaw values:
 * {@code 0=north, 1=west, 2=south, 3=east}.
 *
 * <p>Tests use unique block names per case to avoid Material cache contamination
 * across tests.
 */
public class MaterialHytaleRotationTest {

    private static final String HYTALE_ROTATION = "hytale_rotation";

    @Test
    public void rotateOneStepShiftsYawClockwise() {
        // yaw=3 (east) -> rotate 1 step (90 degrees CW) -> yaw=2 (south).
        Material east = Material.get(uniqueName("east_yaw"), HYTALE_ROTATION, "3");
        Material rotated = east.rotate(1, DefaultPlugin.HYTALE);

        assertEquals("Rotating east by 90° must produce south",
                "2", rotated.getProperty(HYTALE_ROTATION));
    }

    @Test
    public void rotateTwoStepsMovesEastToWest() {
        // yaw=3 (east) -> rotate 2 steps (180 degrees) -> yaw=1 (west).
        Material east = Material.get(uniqueName("east_to_west"), HYTALE_ROTATION, "3");
        Material rotated = east.rotate(2, DefaultPlugin.HYTALE);

        assertEquals("1", rotated.getProperty(HYTALE_ROTATION));
    }

    @Test
    public void rotateFourStepsIsIdentity() {
        // (steps % 4) == 0 short-circuits before reaching the hytale path.
        Material east = Material.get(uniqueName("identity_full_turn"), HYTALE_ROTATION, "3");
        Material rotated = east.rotate(4, DefaultPlugin.HYTALE);

        // 4 full quarter-turns must be a no-op.
        assertSame("rotate(4) must be a no-op (identity)", east, rotated);
    }

    @Test
    public void rotateNorthByOneStepProducesEast() {
        // yaw=0 (north) -> rotate 1 step clockwise -> yaw=3 (east).
        Material north = Material.get(uniqueName("north_to_east"), HYTALE_ROTATION, "0");
        Material rotated = north.rotate(1, DefaultPlugin.HYTALE);

        assertEquals("3", rotated.getProperty(HYTALE_ROTATION));
    }

    @Test
    public void rotatePreservesPitchAndRoll() {
        // roll=2, pitch=1, yaw=3 (east) -> 2*16 + 1*4 + 3 = 39.
        // Rotate 1 step: yaw becomes 2, roll and pitch unchanged -> 38.
        Material twisted = Material.get(uniqueName("twisted"), HYTALE_ROTATION, "39");
        Material rotated = twisted.rotate(1, DefaultPlugin.HYTALE);

        assertEquals("38", rotated.getProperty(HYTALE_ROTATION));
    }

    @Test
    public void rotateLeavesNonHytaleMaterialsAlone() {
        // No hytale_rotation property — must return the receiver unchanged.
        Material plain = Material.get(uniqueName("plain"));
        Material rotated = plain.rotate(1, DefaultPlugin.HYTALE);

        assertSame("rotate must be a no-op when hytale_rotation is absent",
                plain, rotated);
    }

    @Test
    public void rotateIgnoresMalformedHytaleRotation() {
        // Out-of-range value: must be left untouched (the hytale handler
        // validates 0–63 and falls through otherwise).
        Material malformed = Material.get(uniqueName("malformed_oor"), HYTALE_ROTATION, "9999");
        Material rotated = malformed.rotate(1, DefaultPlugin.HYTALE);

        assertSame(malformed, rotated);
    }

    @Test
    public void mirrorSouthFlipsEastAndWest() {
        // Direction.SOUTH mirrors across the X-axis (flips east/west), leaves
        // north/south alone. yaw=3 (east) -> yaw=1 (west).
        Material east = Material.get(uniqueName("mirror_s_east"), HYTALE_ROTATION, "3");
        Material mirrored = east.mirror(Direction.SOUTH, DefaultPlugin.HYTALE);

        assertEquals("1", mirrored.getProperty(HYTALE_ROTATION));
    }

    @Test
    public void mirrorSouthLeavesNorthAndSouthUnchanged() {
        Material north = Material.get(uniqueName("mirror_s_north"), HYTALE_ROTATION, "0");
        Material mirroredNorth = north.mirror(Direction.SOUTH, DefaultPlugin.HYTALE);
        // yaw=0 -> unchanged -> fast path returns receiver.
        assertSame("North block mirrored across X-axis stays north",
                north, mirroredNorth);

        Material south = Material.get(uniqueName("mirror_s_south"), HYTALE_ROTATION, "2");
        Material mirroredSouth = south.mirror(Direction.SOUTH, DefaultPlugin.HYTALE);
        // yaw=2 -> unchanged.
        assertSame(south, mirroredSouth);
    }

    @Test
    public void mirrorEastFlipsNorthAndSouth() {
        // Direction.EAST mirrors across the Z-axis (flips north/south), leaves
        // east/west alone. yaw=0 (north) -> yaw=2 (south).
        Material north = Material.get(uniqueName("mirror_e_north"), HYTALE_ROTATION, "0");
        Material mirrored = north.mirror(Direction.EAST, DefaultPlugin.HYTALE);

        assertEquals("2", mirrored.getProperty(HYTALE_ROTATION));
    }

    @Test
    public void mirrorEastLeavesEastAndWestUnchanged() {
        // yaw=3 (east) -> unchanged.
        Material east = Material.get(uniqueName("mirror_e_east"), HYTALE_ROTATION, "3");
        Material mirroredEast = east.mirror(Direction.EAST, DefaultPlugin.HYTALE);
        assertSame(east, mirroredEast);

        // yaw=1 (west) -> unchanged.
        Material west = Material.get(uniqueName("mirror_e_west"), HYTALE_ROTATION, "1");
        Material mirroredWest = west.mirror(Direction.EAST, DefaultPlugin.HYTALE);
        assertSame(west, mirroredWest);
    }

    @Test
    public void mirrorPreservesPitchAndRoll() {
        // roll=1, pitch=1, yaw=3 (east) -> 16 + 4 + 3 = 23.
        // Mirror across X-axis (Direction.SOUTH): yaw 3 -> 1, roll/pitch unchanged -> 21.
        Material twisted = Material.get(uniqueName("mirror_twisted"), HYTALE_ROTATION, "23");
        Material mirrored = twisted.mirror(Direction.SOUTH, DefaultPlugin.HYTALE);

        assertEquals("21", mirrored.getProperty(HYTALE_ROTATION));
    }

    @Test
    public void mirrorLeavesNonHytaleMaterialsAlone() {
        Material plain = Material.get(uniqueName("plain_mirror"));
        Material mirrored = plain.mirror(Direction.SOUTH, DefaultPlugin.HYTALE);

        assertSame("mirror must be a no-op when hytale_rotation is absent",
                plain, mirrored);
    }

    private static String uniqueName(String slot) {
        // Each test gets a fresh material name so the global Material cache
        // can't leak state from one assertion into another.
        return "test_hytale_rot:" + slot + "_" + System.nanoTime();
    }
}
