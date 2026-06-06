package org.pepsoft.worldpainter.hytale;

/**
 * Shared Hytale block-rotation math. The 0-63 rotation value encodes three
 * axes (2 bits each); {@link #rotateRaw(int, int)} transforms only the yaw
 * component, matching {@code Material.rotate(steps, …)}'s {@code hytale_rotation}
 * handling — the convention already used by prefab-layer rotation. Reused by
 * {@code PrefabRotator} so exact-placement rotation behaves identically.
 */
public final class HytaleRotations {
    private HytaleRotations() {
        // utility class
    }

    /**
     * Rotate a Hytale 0-63 rotation value by {@code steps} 90° turns, transforming
     * only the yaw component. Verbatim port of {@code Material.rotateHytaleRotation}.
     * Values outside 0-63 are returned unchanged.
     */
    public static int rotateRaw(int rotation, int steps) {
        if ((rotation < 0) || (rotation > 63)) {
            return rotation;
        }
        final int roll = rotation >> 4;
        final int pitch = (rotation >> 2) & 3;
        int yaw = rotation & 3;
        yaw = ((yaw - (steps % 4)) + 4) % 4;
        return (roll << 4) | (pitch << 2) | yaw;
    }
}
