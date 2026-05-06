package org.pepsoft.worldpainter.hytale;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Regression test for {@link HytaleWorldExporter#sealAboveTerrainColumn}, the
 * helper that restores fluid above terrain in the water column at the end of a
 * region export.
 *
 * <p>Previously this loop unconditionally cleared every block above terrain up
 * to the water level, which wiped out underwater prefabs/plants placed by Bo2
 * custom-object layers. The fix preserves Bo2-placed blocks with a transient
 * seal-protection marker, and still preserves decorative physics-exempt
 * blocks, so they survive the seal pass while the surrounding water is
 * restored.
 */
public class HytaleSealAboveTerrainColumnTest {

    private static final int TERRAIN_HEIGHT = 30;
    private static final int WATER_LEVEL = 64;
    private static final String FLUID_ID = HytaleBlockMapping.HY_WATER;

    @Test
    public void sealClearsNonDecorativeBlocksAboveTerrainAndFillsWithFluid() {
        HytaleChunk chunk = new HytaleChunk(0, 0, 0, 320);

        // Simulate stray ground-cover/terrain plant left at terrain+1 in a
        // column whose water level is well above the terrain. The seal pass
        // should clear it and replace with fluid.
        HytaleBlock strayPlant = HytaleBlock.of("Fern_Short", 0);
        chunk.setHytaleBlock(5, TERRAIN_HEIGHT + 1, 5, strayPlant);
        // Note: setDecorative is *not* called, so support remains SUPPORT_NONE.

        int sealed = HytaleWorldExporter.sealAboveTerrainColumn(
                chunk, 5, 5, TERRAIN_HEIGHT, WATER_LEVEL, FLUID_ID);

        assertEquals("Seal pass should touch every cell from terrain+1 to waterLevel",
                WATER_LEVEL - TERRAIN_HEIGHT, sealed);

        HytaleBlock cleared = chunk.getHytaleBlock(5, TERRAIN_HEIGHT + 1, 5);
        assertTrue("Non-decorative block above terrain should be cleared",
                cleared == null || cleared.isEmpty());

        int fluidId = chunk.getSections()[(TERRAIN_HEIGHT + 1) >> 5]
                .getFluidId(5, (TERRAIN_HEIGHT + 1) & 31, 5);
        assertTrue("Cleared cell should be filled with fluid", fluidId > 0);
    }

    @Test
    public void sealPreservesDecorativeBlocksAndStillFillsFluidAroundThem() {
        HytaleChunk chunk = new HytaleChunk(0, 0, 0, 320);

        // Simulate a Bo2 layer with "Disable physics" placing a tall seaweed
        // prefab at terrain+1 .. terrain+5 in an underwater column. Each cell
        // is marked SUPPORT_DECORATIVE; the per-cell fluid clear that
        // HytaleRegionMinecraftWorld.setMaterialAt performs during placement
        // is intentionally omitted from this test so we exercise the seal
        // pass against a "block + fluid already cleared" state.
        HytaleBlock seaweed = HytaleBlock.of("Plant_Seaweed_Arid_Stack", 0);
        for (int y = TERRAIN_HEIGHT + 1; y <= TERRAIN_HEIGHT + 5; y++) {
            chunk.setHytaleBlock(5, y, 5, seaweed);
            chunk.setDecorative(5, y, 5, true);
            chunk.getSections()[y >> 5].clearFluid(5, y & 31, 5);
        }

        HytaleWorldExporter.sealAboveTerrainColumn(
                chunk, 5, 5, TERRAIN_HEIGHT, WATER_LEVEL, FLUID_ID);

        // Seaweed blocks must survive the seal pass.
        for (int y = TERRAIN_HEIGHT + 1; y <= TERRAIN_HEIGHT + 5; y++) {
            HytaleBlock survived = chunk.getHytaleBlock(5, y, 5);
            assertNotNull("Decorative block at y=" + y + " must survive seal pass", survived);
            assertEquals("Seaweed block at y=" + y + " must survive seal pass",
                    seaweed.id, survived.id);
            assertEquals("Decorative support must persist at y=" + y,
                    HytaleChunk.SUPPORT_DECORATIVE, chunk.getSupportValue(5, y, 5));

            int fluidId = chunk.getSections()[y >> 5].getFluidId(5, y & 31, 5);
            assertTrue("Water fluid must be restored around seaweed at y=" + y, fluidId > 0);
        }

        // Cells above the seaweed but still in the water column should have
        // their fluid restored too.
        for (int y = TERRAIN_HEIGHT + 6; y <= WATER_LEVEL; y++) {
            int fluidId = chunk.getSections()[y >> 5].getFluidId(5, y & 31, 5);
            assertTrue("Empty cells in water column should be filled with fluid at y=" + y,
                    fluidId > 0);
        }
    }

    @Test
    public void sealPreservesProtectedNormalBlocksWithoutChangingSupport() {
        HytaleChunk chunk = new HytaleChunk(0, 0, 0, 320);

        // Simulate a Bo2 layer WITHOUT "Disable physics" placing a tree log
        // underwater. Normal blocks must keep SUPPORT_NONE so Hytale computes
        // real structural support on demand; the transient seal marker is what
        // keeps the block from being mistaken for a stray terrain plant.
        HytaleBlock log = HytaleBlock.of("Log_Oak", 0);
        for (int y = TERRAIN_HEIGHT + 1; y <= TERRAIN_HEIGHT + 3; y++) {
            chunk.setHytaleBlock(5, y, 5, log);
            chunk.setSealProtected(5, y, 5, true);
        }

        HytaleWorldExporter.sealAboveTerrainColumn(
                chunk, 5, 5, TERRAIN_HEIGHT, WATER_LEVEL, FLUID_ID);

        for (int y = TERRAIN_HEIGHT + 1; y <= TERRAIN_HEIGHT + 3; y++) {
            HytaleBlock survived = chunk.getHytaleBlock(5, y, 5);
            assertNotNull("Structural block at y=" + y + " must survive seal pass", survived);
            assertEquals("Log block at y=" + y + " must survive seal pass",
                    log.id, survived.id);
            assertEquals("Normal object blocks should not get synthetic support at y=" + y,
                    HytaleChunk.SUPPORT_NONE, chunk.getSupportValue(5, y, 5));

            int fluidId = chunk.getSections()[y >> 5].getFluidId(5, y & 31, 5);
            assertTrue("Water fluid must be restored around log at y=" + y, fluidId > 0);
        }
    }

    @Test
    public void sealHandlesMixedDecorativeAndNonDecorativeBlocksInSameColumn() {
        HytaleChunk chunk = new HytaleChunk(0, 0, 0, 320);

        // Decorative seaweed at terrain+1, stray fern at terrain+2 (no
        // decorative flag), empty above. Only the fern should be cleared.
        HytaleBlock seaweed = HytaleBlock.of("Plant_Seaweed_Arid_Stack", 0);
        HytaleBlock stray = HytaleBlock.of("Fern_Short", 0);

        chunk.setHytaleBlock(5, TERRAIN_HEIGHT + 1, 5, seaweed);
        chunk.setDecorative(5, TERRAIN_HEIGHT + 1, 5, true);

        chunk.setHytaleBlock(5, TERRAIN_HEIGHT + 2, 5, stray);
        // No setDecorative -> SUPPORT_NONE

        HytaleWorldExporter.sealAboveTerrainColumn(
                chunk, 5, 5, TERRAIN_HEIGHT, WATER_LEVEL, FLUID_ID);

        HytaleBlock atOne = chunk.getHytaleBlock(5, TERRAIN_HEIGHT + 1, 5);
        assertNotNull(atOne);
        assertEquals("Decorative seaweed at terrain+1 must survive",
                seaweed.id, atOne.id);

        HytaleBlock atTwo = chunk.getHytaleBlock(5, TERRAIN_HEIGHT + 2, 5);
        assertTrue("Non-decorative stray block at terrain+2 must be cleared",
                atTwo == null || atTwo.isEmpty());

        int fluidAtOne = chunk.getSections()[(TERRAIN_HEIGHT + 1) >> 5]
                .getFluidId(5, (TERRAIN_HEIGHT + 1) & 31, 5);
        assertTrue("Fluid must be set around the surviving decorative block", fluidAtOne > 0);

        int fluidAtTwo = chunk.getSections()[(TERRAIN_HEIGHT + 2) >> 5]
                .getFluidId(5, (TERRAIN_HEIGHT + 2) & 31, 5);
        assertTrue("Fluid must be set in the cleared cell", fluidAtTwo > 0);
    }
}
