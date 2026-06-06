package org.pepsoft.worldpainter.hytale.prefab;

import org.junit.Test;
import org.pepsoft.worldpainter.hytale.prefab.HytalePrefabPaster.PrefabBlock;
import org.pepsoft.worldpainter.hytale.prefab.HytalePrefabPaster.PrefabBlockData;
import org.pepsoft.worldpainter.hytale.prefab.HytalePrefabPaster.PrefabFluid;

import java.util.*;

import static org.junit.Assert.*;

public class PrefabRotatorTest {

    private static PrefabBlockData data(List<PrefabBlock> blocks) {
        return new PrefabBlockData(0, 0, 0, blocks, Collections.emptyList());
    }

    private static Map<Long, PrefabBlock> index(PrefabBlockData d) {
        Map<Long, PrefabBlock> m = new HashMap<>();
        for (PrefabBlock b : d.blocks) {
            m.put((((long) b.x) << 32) ^ (b.z & 0xFFFFFFFFL), b);
        }
        return m;
    }

    @Test
    public void zeroDegreesIsNoOp() {
        PrefabBlockData d = data(Collections.singletonList(new PrefabBlock(2, 5, 3, "Rock_Stone", 1)));
        assertSame(d, PrefabRotator.rotate(d, 0.0));
        assertSame(d, PrefabRotator.rotate(d, 360.0));
    }

    @Test
    public void cardinal90MovesPositionsAndYaw() {
        PrefabBlockData d = data(Collections.singletonList(new PrefabBlock(2, 5, 0, "Rock_Stone", 0)));
        PrefabBlockData r = PrefabRotator.rotate(d, 90.0);
        PrefabBlock b = r.blocks.get(0);
        assertEquals(0, b.x);
        assertEquals(2, b.z);
        assertEquals(5, b.y);
        assertEquals(3, b.rotation);
    }

    @Test
    public void fourCardinalStepsRestoreOriginal() {
        PrefabBlockData d = data(Arrays.asList(
                new PrefabBlock(2, 0, 0, "A", 1),
                new PrefabBlock(0, 0, 3, "B", 2),
                new PrefabBlock(-1, 1, 2, "C", 0)));
        PrefabBlockData r = PrefabRotator.rotate(
                PrefabRotator.rotate(PrefabRotator.rotate(PrefabRotator.rotate(d, 90), 90), 90), 90);
        Map<Long, PrefabBlock> before = index(d), after = index(r);
        assertEquals(before.keySet(), after.keySet());
        for (Long k : before.keySet()) {
            assertEquals(before.get(k).blockName, after.get(k).blockName);
            assertEquals(before.get(k).rotation, after.get(k).rotation);
        }
    }

    @Test
    public void freeAngleProducesNoHolesOverFootprint() {
        List<PrefabBlock> blocks = new ArrayList<>();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                blocks.add(new PrefabBlock(x, 0, z, "Rock_Stone", 0));
            }
        }
        PrefabBlockData r = PrefabRotator.rotate(data(blocks), 37.0);
        assertFalse(r.blocks.isEmpty());
        Set<Long> filled = new HashSet<>();
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (PrefabBlock b : r.blocks) {
            filled.add((((long) b.x) << 32) ^ (b.z & 0xFFFFFFFFL));
            minX = Math.min(minX, b.x); maxX = Math.max(maxX, b.x);
            minZ = Math.min(minZ, b.z); maxZ = Math.max(maxZ, b.z);
        }
        for (int x = minX + 1; x < maxX; x++) {
            for (int z = minZ + 1; z < maxZ; z++) {
                long k = (((long) x) << 32) ^ (z & 0xFFFFFFFFL);
                boolean hasLeft = filled.contains((((long) (x - 1)) << 32) ^ (z & 0xFFFFFFFFL));
                boolean hasRight = filled.contains((((long) (x + 1)) << 32) ^ (z & 0xFFFFFFFFL));
                if (hasLeft && hasRight) {
                    assertTrue("interior hole at " + x + "," + z, filled.contains(k));
                }
            }
        }
    }

    @Test
    public void freeAngleSnapsYawToNearestCardinal() {
        PrefabBlockData d = data(Collections.singletonList(new PrefabBlock(0, 0, 0, "A", 0)));
        PrefabBlockData r = PrefabRotator.rotate(d, 80.0);
        assertEquals(3, r.blocks.get(0).rotation);
    }

    @Test
    public void cardinal180And270ExactPositionsAndYaw() {
        PrefabBlockData d = data(Collections.singletonList(new PrefabBlock(2, 0, 3, "A", 1)));

        PrefabBlock b180 = PrefabRotator.rotate(d, 180.0).blocks.get(0);
        assertEquals(-2, b180.x);
        assertEquals(-3, b180.z);
        assertEquals(3, b180.rotation); // rotateRaw(1,2) = (1-2+4)%4 = 3

        PrefabBlock b270 = PrefabRotator.rotate(d, 270.0).blocks.get(0);
        assertEquals(3, b270.x);   // steps=3: (x,z)->(z,-x) => (3,-2)
        assertEquals(-2, b270.z);
        assertEquals(2, b270.rotation); // rotateRaw(1,3) = (1-3+4)%4 = 2
    }

    @Test
    public void cardinalRotationAboutNonZeroAnchor() {
        // anchor (10,0,20); block (12,5,20) -> offset (2,0) -> 90° -> (0,2) -> world (10,5,22)
        PrefabBlockData d = new PrefabBlockData(10, 0, 20,
                Collections.singletonList(new PrefabBlock(12, 5, 20, "A", 0)),
                Collections.emptyList());
        PrefabBlock b = PrefabRotator.rotate(d, 90.0).blocks.get(0);
        assertEquals(10, b.x);
        assertEquals(22, b.z);
        assertEquals(5, b.y);
    }

    @Test
    public void freeAngleAboutNonZeroAnchorStaysBracketed() {
        PrefabBlockData d = new PrefabBlockData(100, 0, 200,
                Arrays.asList(new PrefabBlock(102, 0, 200, "A", 0),
                              new PrefabBlock(98, 0, 200, "B", 0)),
                Collections.emptyList());
        PrefabBlockData r = PrefabRotator.rotate(d, 37.0);
        assertFalse(r.blocks.isEmpty());
        for (PrefabBlock b : r.blocks) {
            assertTrue("x within anchor bracket", Math.abs(b.x - 100) <= 3);
            assertTrue("z within anchor bracket", Math.abs(b.z - 200) <= 3);
        }
    }

    @Test
    public void freeAngleResamplesLayersIndependently() {
        PrefabBlockData d = data(Arrays.asList(
                new PrefabBlock(2, 0, 0, "low", 0),
                new PrefabBlock(0, 1, 2, "high", 0)));
        PrefabBlockData r = PrefabRotator.rotate(d, 90.0001); // off-cardinal -> free path
        Map<Integer, List<PrefabBlock>> byY = new HashMap<>();
        for (PrefabBlock b : r.blocks) {
            byY.computeIfAbsent(b.y, k -> new ArrayList<>()).add(b);
        }
        assertTrue("layer y=0 present", byY.containsKey(0));
        assertTrue("layer y=1 present", byY.containsKey(1));
        for (PrefabBlock b : byY.get(0)) {
            assertEquals("low", b.blockName);
        }
        for (PrefabBlock b : byY.get(1)) {
            assertEquals("high", b.blockName);
        }
    }

    @Test
    public void fluidsRotateCardinalAndFree() {
        PrefabBlockData d = new PrefabBlockData(0, 0, 0,
                Collections.emptyList(),
                Collections.singletonList(new PrefabFluid(2, 5, 0, "Water", 1)));

        PrefabFluid c = PrefabRotator.rotate(d, 90.0).fluids.get(0);
        assertEquals(0, c.x);
        assertEquals(2, c.z);
        assertEquals(5, c.y);
        assertEquals("Water", c.fluidName);
        assertEquals(1, c.level);

        PrefabBlockData rf = PrefabRotator.rotate(d, 37.0);
        assertEquals(1, rf.fluids.size());
        assertEquals("Water", rf.fluids.get(0).fluidName);
    }
}
