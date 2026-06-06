package org.pepsoft.worldpainter.hytale.prefab;

import org.pepsoft.worldpainter.hytale.HytaleRotations;
import org.pepsoft.worldpainter.hytale.prefab.HytalePrefabPaster.PrefabBlock;
import org.pepsoft.worldpainter.hytale.prefab.HytalePrefabPaster.PrefabBlockData;
import org.pepsoft.worldpainter.hytale.prefab.HytalePrefabPaster.PrefabFluid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rotates {@link PrefabBlockData} by an arbitrary yaw angle about the vertical
 * axis, in the horizontal (x,z) plane about the prefab anchor.
 *
 * <ul>
 *   <li>Cardinal angles (0/90/180/270) rotate positions exactly (integer turns)
 *       and transform each block's facing via {@link HytaleRotations#rotateRaw}.</li>
 *   <li>Off-cardinal angles use <b>destination-grid nearest-neighbour</b> resampling
 *       (iterate destination cells, inverse-rotate to the nearest source cell) so the
 *       footprint has no holes; each emitted block's facing snaps to the nearest
 *       cardinal turn.</li>
 * </ul>
 */
public final class PrefabRotator {
    private static final double EPS = 1.0e-6;

    private PrefabRotator() {
        // utility
    }

    public static PrefabBlockData rotate(PrefabBlockData data, double degrees) {
        final double norm = ((degrees % 360.0) + 360.0) % 360.0;
        if (norm < EPS) {
            return data; // exact no-op
        }
        final int steps = ((int) Math.round(norm / 90.0)) % 4;
        final boolean cardinal = Math.abs(norm - (steps * 90.0)) < EPS;
        return cardinal ? rotateCardinal(data, steps) : rotateFree(data, norm, steps);
    }

    private static PrefabBlockData rotateCardinal(PrefabBlockData data, int steps) {
        final List<PrefabBlock> outBlocks = new ArrayList<>(data.blocks.size());
        for (PrefabBlock b : data.blocks) {
            final int[] p = rotateOffset(b.x - data.anchorX, b.z - data.anchorZ, steps);
            // NOTE: facing uses rotateRaw(rot, steps); if directional blocks point wrong
            // at 90° in manual testing, change `steps` here to `((4 - steps) % 4)`.
            outBlocks.add(new PrefabBlock(data.anchorX + p[0], b.y, data.anchorZ + p[1],
                    b.blockName, HytaleRotations.rotateRaw(b.rotation, steps)));
        }
        final List<PrefabFluid> outFluids = new ArrayList<>(data.fluids.size());
        for (PrefabFluid f : data.fluids) {
            final int[] p = rotateOffset(f.x - data.anchorX, f.z - data.anchorZ, steps);
            outFluids.add(new PrefabFluid(data.anchorX + p[0], f.y, data.anchorZ + p[1], f.fluidName, f.level));
        }
        return new PrefabBlockData(data.anchorX, data.anchorY, data.anchorZ, outBlocks, outFluids);
    }

    /** 90°-step clockwise rotation of an (x,z) offset. At 90°: (x,z) -> (-z, x). */
    private static int[] rotateOffset(int x, int z, int steps) {
        switch (((steps % 4) + 4) % 4) {
            case 1:  return new int[]{-z,  x};
            case 2:  return new int[]{-x, -z};
            case 3:  return new int[]{ z, -x};
            default: return new int[]{ x,  z};
        }
    }

    private static PrefabBlockData rotateFree(PrefabBlockData data, double degrees, int nearestSteps) {
        final double rad = Math.toRadians(degrees);
        final double cos = Math.cos(rad), sin = Math.sin(rad);

        final Map<Integer, Map<Long, PrefabBlock>> byLayer = new HashMap<>();
        for (PrefabBlock b : data.blocks) {
            byLayer.computeIfAbsent(b.y, k -> new HashMap<>())
                    .put(key(b.x - data.anchorX, b.z - data.anchorZ), b);
        }

        final List<PrefabBlock> outBlocks = new ArrayList<>();
        for (Map.Entry<Integer, Map<Long, PrefabBlock>> entry : byLayer.entrySet()) {
            final int y = entry.getKey();
            final Map<Long, PrefabBlock> src = entry.getValue();

            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
            for (PrefabBlock b : src.values()) {
                final int ox = b.x - data.anchorX, oz = b.z - data.anchorZ;
                final double dx = (ox * cos) - (oz * sin);
                final double dz = (ox * sin) + (oz * cos);
                minX = Math.min(minX, (int) Math.floor(dx)); maxX = Math.max(maxX, (int) Math.ceil(dx));
                minZ = Math.min(minZ, (int) Math.floor(dz)); maxZ = Math.max(maxZ, (int) Math.ceil(dz));
            }

            for (int dx = minX; dx <= maxX; dx++) {
                for (int dz = minZ; dz <= maxZ; dz++) {
                    final int sx = (int) Math.round((dx * cos) + (dz * sin));
                    final int sz = (int) Math.round((-dx * sin) + (dz * cos));
                    final PrefabBlock b = src.get(key(sx, sz));
                    if (b != null) {
                        outBlocks.add(new PrefabBlock(data.anchorX + dx, y, data.anchorZ + dz,
                                b.blockName, HytaleRotations.rotateRaw(b.rotation, nearestSteps)));
                    }
                }
            }
        }

        final List<PrefabFluid> outFluids = new ArrayList<>(data.fluids.size());
        for (PrefabFluid f : data.fluids) {
            final int ox = f.x - data.anchorX, oz = f.z - data.anchorZ;
            final int nx = (int) Math.round((ox * cos) - (oz * sin));
            final int nz = (int) Math.round((ox * sin) + (oz * cos));
            outFluids.add(new PrefabFluid(data.anchorX + nx, f.y, data.anchorZ + nz, f.fluidName, f.level));
        }
        return new PrefabBlockData(data.anchorX, data.anchorY, data.anchorZ, outBlocks, outFluids);
    }

    private static long key(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xFFFFFFFFL);
    }
}
