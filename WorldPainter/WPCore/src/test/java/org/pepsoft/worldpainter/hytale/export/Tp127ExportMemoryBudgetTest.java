package org.pepsoft.worldpainter.hytale.export;

import org.junit.Test;
import org.pepsoft.worldpainter.hytale.chunk.HytaleChunk;

import static org.junit.Assert.assertEquals;
import static org.pepsoft.worldpainter.hytale.export.HytaleWorldExporter.maxConcurrentRegionsByMemory;
import static org.pepsoft.worldpainter.hytale.export.HytaleWorldExporter.perRegionMemoryBudgetBytes;

/**
 * TP-127: the concurrent region cap for Hytale exports must be computed from the memory actually available for the
 * export, not from the total heap size. A 20k×20k world's own tiles occupy multiple GB of heap; sizing region
 * concurrency against the full heap caused GC thrash and multi-hour stalls on the default 4 GB heap.
 */
public class Tp127ExportMemoryBudgetTest {
    @Test
    public void budgetIsBaselineForDefaultHeightPlainExport() {
        assertEquals(BASELINE_BUDGET, perRegionMemoryBudgetBytes(HytaleChunk.DEFAULT_MAX_HEIGHT, false));
    }

    @Test
    public void budgetScalesUpWithWorldHeight() {
        assertEquals(2 * BASELINE_BUDGET, perRegionMemoryBudgetBytes(HytaleChunk.DEFAULT_MAX_HEIGHT * 2, false));
    }

    @Test
    public void budgetDoesNotShrinkBelowBaselineForShortWorlds() {
        assertEquals(BASELINE_BUDGET, perRegionMemoryBudgetBytes(HytaleChunk.DEFAULT_MAX_HEIGHT / 2, false));
    }

    @Test
    public void budgetDoublesWhenMergeRetainsOriginalChunks() {
        assertEquals(2 * BASELINE_BUDGET, perRegionMemoryBudgetBytes(HytaleChunk.DEFAULT_MAX_HEIGHT, true));
    }

    @Test
    public void capIsComputedFromAvailableMemory() {
        assertEquals(2, maxConcurrentRegionsByMemory(2 * BASELINE_BUDGET + (100L * 1024 * 1024), BASELINE_BUDGET));
    }

    @Test
    public void capIsAtLeastOneEvenWhenMemoryIsExhausted() {
        assertEquals(1, maxConcurrentRegionsByMemory(0L, BASELINE_BUDGET));
        // memoryInUse can momentarily exceed maxMemory between GC cycles, making "available" negative
        assertEquals(1, maxConcurrentRegionsByMemory(-2L * 1024 * 1024 * 1024, BASELINE_BUDGET));
    }

    @Test
    public void capAccountsForMemoryAlreadyInUse() {
        // The TP-127 regression scenario: 4 GB heap, ~2.5 GB already taken by the loaded world. The old computation
        // (total heap / budget) yielded 2 concurrent regions; only 1 actually fits.
        final long maxMemory = 4L * 1024 * 1024 * 1024;
        final long memoryInUse = 5L * 1024 * 1024 * 1024 / 2;
        assertEquals(1, maxConcurrentRegionsByMemory(maxMemory - memoryInUse, BASELINE_BUDGET));
    }

    private static final long BASELINE_BUDGET = 1536L * 1024 * 1024;
}
