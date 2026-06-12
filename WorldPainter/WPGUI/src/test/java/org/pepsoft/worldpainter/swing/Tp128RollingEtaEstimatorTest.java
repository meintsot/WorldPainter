package org.pepsoft.worldpainter.swing;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.pepsoft.worldpainter.swing.RollingEtaEstimator.UNKNOWN;

/**
 * TP-128: the export progress dialog's "time remaining" estimate must be based on the recent rate of progress, not on
 * a linear extrapolation over the whole run. The whole-run extrapolation hovers at a near-constant value for hours
 * when an export enters a slow tail phase, making a crawling export indistinguishable from a healthy one.
 */
public class Tp128RollingEtaEstimatorTest {
    @Test
    public void unknownWithoutSamples() {
        final RollingEtaEstimator estimator = newEstimator();
        assertEquals(UNKNOWN, estimator.remainingMillis(0L));
        assertFalse(estimator.isStalled(0L));
    }

    @Test
    public void unknownWhileSampleSpanIsBelowMinimum() {
        final RollingEtaEstimator estimator = newEstimator();
        estimator.progressReported(0.01f, 0L);
        estimator.progressReported(0.02f, 10_000L);
        assertEquals(UNKNOWN, estimator.remainingMillis(10_000L));
    }

    @Test
    public void extrapolatesFromSteadyRate() {
        final RollingEtaEstimator estimator = newEstimator();
        // 10% progress over 60 seconds, reported every 10 seconds
        for (long t = 0; t <= 60_000L; t += 10_000L) {
            estimator.progressReported(t / 600_000.0f, t);
        }
        // Remaining 90% at the observed rate of 10% per minute → nine minutes
        assertEquals(540_000.0, estimator.remainingMillis(60_000L), 1_500.0);
    }

    @Test
    public void usesOnlyTheRecentWindowNotTheWholeRun() {
        final RollingEtaEstimator estimator = newEstimator();
        // Fast phase: 0 → 50% in the first 60 seconds
        for (long t = 0; t <= 60_000L; t += 10_000L) {
            estimator.progressReported(t / 120_000.0f, t);
        }
        // Slow tail: 0.01% per second for the next two minutes
        for (long t = 70_000L; t <= 180_000L; t += 10_000L) {
            estimator.progressReported(0.5f + ((t - 60_000L) / 1_000L) * 0.0001f, t);
        }
        // At t=180s progress is 51.2%. Whole-run extrapolation would claim ~172 s remaining; the recent rate
        // (0.01%/s) actually implies 48.8% / 0.0001 = 4880 s.
        assertEquals(4_880_000.0, estimator.remainingMillis(180_000L), 30_000.0);
    }

    @Test
    public void countsDownBetweenReports() {
        final RollingEtaEstimator estimator = newEstimator();
        for (long t = 0; t <= 60_000L; t += 10_000L) {
            estimator.progressReported(t / 600_000.0f, t);
        }
        final long atLastReport = estimator.remainingMillis(60_000L);
        assertEquals((double) (atLastReport - 10_000L), estimator.remainingMillis(70_000L), 1_500.0);
    }

    @Test
    public void neverReturnsNegativeRemainingTime() {
        final RollingEtaEstimator estimator = newEstimator();
        for (long t = 0; t <= 60_000L; t += 10_000L) {
            estimator.progressReported(0.9f + t / 600_000.0f, t);
        }
        // Estimate at the last report is small; just under the stall timeout later it must clamp to zero
        assertEquals(0L, estimator.remainingMillis(60_000L + WINDOW - 1));
    }

    @Test
    public void stalledWhenNoReportsForAFullWindow() {
        final RollingEtaEstimator estimator = newEstimator();
        estimator.progressReported(0.1f, 0L);
        assertFalse(estimator.isStalled(WINDOW - 1));
        assertTrue(estimator.isStalled(WINDOW + 1_000L));
        assertEquals(UNKNOWN, estimator.remainingMillis(WINDOW + 1_000L));
    }

    @Test
    public void stalledWhenProgressIsFlatAcrossTheWindow() {
        final RollingEtaEstimator estimator = newEstimator();
        for (long t = 0; t <= WINDOW + 10_000L; t += 10_000L) {
            estimator.progressReported(0.4f, t);
        }
        assertTrue(estimator.isStalled(WINDOW + 10_000L));
        assertEquals(UNKNOWN, estimator.remainingMillis(WINDOW + 10_000L));
    }

    private static RollingEtaEstimator newEstimator() {
        return new RollingEtaEstimator(WINDOW, MIN_SPAN);
    }

    private static final long WINDOW = 120_000L;
    private static final long MIN_SPAN = 30_000L;
}
