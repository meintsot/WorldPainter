# Fix: Hytale prefab branch rotation/mirroring

## Problem

When a Hytale prefab with directional blocks (e.g. tree branches) is placed with random rotation or random mirroring enabled, the block positions are correctly transformed but the block orientations are not. Branches keep pointing in their original direction regardless of the prefab's rotation/mirror state.

## Root cause

1. `HytalePrefabJsonObject.placeBlocks()` stores each block's Hytale rotation as a `hytale_rotation` material property (0-63).
2. `RotatedObject.getMaterial()` calls `material.rotate(steps, platform)`.
3. `MirroredObject.getMaterial()` calls `material.mirror(axis, platform)`.
4. `Material.rotate()` and `Material.mirror()` only handle Minecraft `HorizontalOrientationScheme`-based properties (facing, axis, etc.). They have no awareness of the `hytale_rotation` property.
5. The untransformed `hytale_rotation` value is read verbatim by `HytaleBlockMapping.getExplicitRotation()` at export time.

## Hytale rotation format

Encoded as a single integer: `rotation = rx * 16 + ry * 4 + rz` (range 0-63).

- `rx` (bits 4-5): pitch, rotation around X-axis, 0-3 steps of 90 degrees
- `ry` (bits 2-3): yaw, rotation around Y-axis (vertical), 0-3 steps of 90 degrees
- `rz` (bits 0-1): roll, rotation around Z-axis, 0-3 steps of 90 degrees

Yaw values: 0=north, 1=east, 2=south, 3=west.

## Fix

Modify `Material.java` to transform the `hytale_rotation` property in `rotate()` and `mirror()`.

### Material.rotate(int steps, Platform platform)

After the existing orientation scheme handling (which does not trigger for Hytale materials), add a fallback check for the `hytale_rotation` property. Transform the ry (yaw) component:

```
new_ry = (old_ry + steps) % 4
```

rx and rz are unchanged (body-relative pitch/roll unaffected by horizontal rotation).

Return `withProperty("hytale_rotation", String.valueOf(newRotation))`.

### Material.mirror(Direction axis, Platform platform)

After the existing orientation scheme handling, add a fallback check. Transform ry based on the mirror axis:

- `Direction.SOUTH` (flip east/west, used when `mirrorYAxis=false`):
  ```
  new_ry = (4 - old_ry) % 4
  ```
  Swaps east(1) and west(3). North(0) and south(2) unchanged.

- `Direction.EAST` (flip north/south, used when `mirrorYAxis=true`):
  ```
  new_ry = (6 - old_ry) % 4
  ```
  Swaps north(0) and south(2). East(1) and west(3) unchanged.

rx and rz are unchanged.

### Why only ry

Branch blocks use ry (yaw) to point in cardinal directions. The rx (pitch) and rz (roll) components represent body-relative tilt that is unaffected by horizontal rotation or mirroring of the prefab. Transforming ry alone fixes the reported bug and covers all common directional blocks.

## Files changed

- `WPCore/src/main/java/org/pepsoft/minecraft/Material.java` - add hytale_rotation handling to `rotate()` and `mirror()`

## Verification

| Scenario | Input | Expected | Formula |
|----------|-------|----------|---------|
| Branch east, rotate 1 step | ry=1 | ry=2 (south) | (1+1)%4=2 |
| Branch east, rotate 2 steps | ry=1 | ry=3 (west) | (1+2)%4=3 |
| Branch east, mirror SOUTH | ry=1 | ry=3 (west) | (4-1)%4=3 |
| Branch north, mirror EAST | ry=0 | ry=2 (south) | (6-0)%4=2 |
| Branch east, mirror EAST | ry=1 | ry=1 (east) | (6-1)%4=1 |
| No rotation, rotate 1 step | ry=0 | ry=1 (east) | (0+1)%4=1 |
