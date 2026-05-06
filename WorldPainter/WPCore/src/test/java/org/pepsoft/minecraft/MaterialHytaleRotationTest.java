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
 * <p>The fix transforms the {@code ry} (yaw) component of {@code hytale_rotation}
 * while leaving {@code rx} (pitch) and {@code rz} (roll) untouched, since
 * branch directionality is encoded entirely in yaw. Encoding:
 * {@code rotation = rx * 16 + ry * 4 + rz}. Yaw values:
 * {@code 0=north, 1=east, 2=south, 3=west}.
 *
 * <p>Tests use unique block names per case to avoid Material cache contamination
 * across tests.
 */
public class MaterialHytaleRotationTest {

    private static final String HYTALE_ROTATION = "hytale_rotation";

    @Test
    public void rotateOneStepShiftsYawClockwise() {
        // ry=1 (east) → rotate 1 step (90° CW) → ry=2 (south)
        // rotation 4 == rx=0, ry=1, rz=0 → expected 8 == rx=0, ry=2, rz=0
        Material east = Material.get(uniqueName("east_yaw"), HYTALE_ROTATION, "4");
        Material rotated = east.rotate(1, DefaultPlugin.HYTALE);

        assertEquals("Rotating east by 90° must produce south",
                "8", rotated.getProperty(HYTALE_ROTATION));
    }

    @Test
    public void rotateTwoStepsMovesEastToWest() {
        // ry=1 (east) → rotate 2 steps (180°) → ry=3 (west)
        // 4 → 12 (rx=0, ry=3, rz=0)
        Material east = Material.get(uniqueName("east_to_west"), HYTALE_ROTATION, "4");
        Material rotated = east.rotate(2, DefaultPlugin.HYTALE);

        assertEquals("12", rotated.getProperty(HYTALE_ROTATION));
    }

    @Test
    public void rotateFourStepsIsIdentity() {
        // (steps % 4) == 0 short-circuits before reaching the hytale path.
        Material east = Material.get(uniqueName("identity_full_turn"), HYTALE_ROTATION, "4");
        Material rotated = east.rotate(4, DefaultPlugin.HYTALE);

        // 4 full quarter-turns must be a no-op.
        assertSame("rotate(4) must be a no-op (identity)", east, rotated);
    }

    @Test
    public void rotateNorthByOneStepProducesEast() {
        // ry=0 (north) → rotate 1 step → ry=1 (east). 0 → 4.
        Material north = Material.get(uniqueName("north_to_east"), HYTALE_ROTATION, "0");
        Material rotated = north.rotate(1, DefaultPlugin.HYTALE);

        assertEquals("4", rotated.getProperty(HYTALE_ROTATION));
    }

    @Test
    public void rotatePreservesPitchAndRoll() {
        // rx=2 (180° pitch), ry=1 (east), rz=3 (270° roll) → 2*16 + 1*4 + 3 = 39
        // Rotate 1 step: ry becomes 2, rx and rz unchanged → 2*16 + 2*4 + 3 = 43
        Material twisted = Material.get(uniqueName("twisted"), HYTALE_ROTATION, "39");
        Material rotated = twisted.rotate(1, DefaultPlugin.HYTALE);

        assertEquals("43", rotated.getProperty(HYTALE_ROTATION));
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
        // north/south alone. ry=1 (east) → ry=3 (west). 4 → 12.
        Material east = Material.get(uniqueName("mirror_s_east"), HYTALE_ROTATION, "4");
        Material mirrored = east.mirror(Direction.SOUTH, DefaultPlugin.HYTALE);

        assertEquals("12", mirrored.getProperty(HYTALE_ROTATION));
    }

    @Test
    public void mirrorSouthLeavesNorthAndSouthUnchanged() {
        Material north = Material.get(uniqueName("mirror_s_north"), HYTALE_ROTATION, "0");
        Material mirroredNorth = north.mirror(Direction.SOUTH, DefaultPlugin.HYTALE);
        // ry=0 → (4-0)%4=0 → unchanged → fast path returns receiver.
        assertSame("North block mirrored across X-axis stays north",
                north, mirroredNorth);

        Material south = Material.get(uniqueName("mirror_s_south"), HYTALE_ROTATION, "8");
        Material mirroredSouth = south.mirror(Direction.SOUTH, DefaultPlugin.HYTALE);
        // ry=2 → (4-2)%4=2 → unchanged.
        assertSame(south, mirroredSouth);
    }

    @Test
    public void mirrorEastFlipsNorthAndSouth() {
        // Direction.EAST mirrors across the Z-axis (flips north/south), leaves
        // east/west alone. ry=0 (north) → ry=2 (south). 0 → 8.
        Material north = Material.get(uniqueName("mirror_e_north"), HYTALE_ROTATION, "0");
        Material mirrored = north.mirror(Direction.EAST, DefaultPlugin.HYTALE);

        assertEquals("8", mirrored.getProperty(HYTALE_ROTATION));
    }

    @Test
    public void mirrorEastLeavesEastAndWestUnchanged() {
        // ry=1 (east) → (6-1)%4=1 → unchanged.
        Material east = Material.get(uniqueName("mirror_e_east"), HYTALE_ROTATION, "4");
        Material mirroredEast = east.mirror(Direction.EAST, DefaultPlugin.HYTALE);
        assertSame(east, mirroredEast);

        // ry=3 (west) → (6-3)%4=3 → unchanged.
        Material west = Material.get(uniqueName("mirror_e_west"), HYTALE_ROTATION, "12");
        Material mirroredWest = west.mirror(Direction.EAST, DefaultPlugin.HYTALE);
        assertSame(west, mirroredWest);
    }

    @Test
    public void mirrorPreservesPitchAndRoll() {
        // rx=1 (90° pitch), ry=1 (east), rz=2 (180° roll) → 16 + 4 + 2 = 22.
        // Mirror across X-axis (Direction.SOUTH): ry 1 → 3, rx/rz unchanged → 16 + 12 + 2 = 30.
        Material twisted = Material.get(uniqueName("mirror_twisted"), HYTALE_ROTATION, "22");
        Material mirrored = twisted.mirror(Direction.SOUTH, DefaultPlugin.HYTALE);

        assertEquals("30", mirrored.getProperty(HYTALE_ROTATION));
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
