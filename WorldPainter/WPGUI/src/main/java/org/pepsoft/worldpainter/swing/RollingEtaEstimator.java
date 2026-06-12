package org.pepsoft.worldpainter.swing;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Estimates remaining time for a long-running task from the rate of progress over a recent sliding window, rather
 * than extrapolating linearly over the whole run (TP-128).
 *
 * <p>Whole-run extrapolation ({@code remaining = (1 - progress) × elapsed / progress}) hovers at a near-constant
 * value for hours when a task enters a slow tail phase, because elapsed time grows with wall time while the remaining
 * fraction shrinks only slowly. A windowed rate reflects the current phase, and additionally allows a complete stall
 * to be detected and reported instead of displaying a stale estimate.
 *
 * <p>All methods are thread safe. Timestamps are in milliseconds on a single monotonic-enough clock (such as
 * {@link System#currentTimeMillis()}).
 */
public class RollingEtaEstimator {
    /**
     * @param windowMillis  The length of the sliding window over which the rate of progress is measured. Also used as
     *                      the stall timeout: if no progress has been reported for this long, or progress was flat
     *                      across an entire window, the task is considered {@link #isStalled stalled}.
     * @param minSpanMillis The minimum time span the recorded samples must cover before an estimate is produced.
     */
    public RollingEtaEstimator(long windowMillis, long minSpanMillis) {
        this.windowMillis = windowMillis;
        this.minSpanMillis = minSpanMillis;
    }

    /**
     * Record a progress report.
     *
     * @param progress        The reported overall progress, from 0.0 to 1.0.
     * @param timestampMillis The time of the report.
     */
    public synchronized void progressReported(float progress, long timestampMillis) {
        samples.addLast(new Sample(timestampMillis, progress));
        // Keep only the samples within the window, relative to the newest sample
        while ((samples.size() > 1) && (samples.getFirst().timestamp < (timestampMillis - windowMillis))) {
            samples.removeFirst();
        }
    }

    /**
     * Estimate the remaining time in milliseconds, based on the rate of progress across the recorded window and
     * counting down the time elapsed since the last report. Returns {@link #UNKNOWN} if there is not yet enough data
     * for an estimate, or if the task is {@link #isStalled stalled}.
     */
    public synchronized long remainingMillis(long nowMillis) {
        if (samples.isEmpty() || isStalled(nowMillis)) {
            return UNKNOWN;
        }
        final Sample oldest = samples.getFirst(), newest = samples.getLast();
        final long span = newest.timestamp - oldest.timestamp;
        if (span < minSpanMillis) {
            return UNKNOWN;
        }
        final double rate = (newest.progress - oldest.progress) / (double) span;
        if (rate <= 0.0) {
            return UNKNOWN;
        }
        final long atLastReport = (long) ((1.0 - newest.progress) / rate);
        return Math.max(0L, atLastReport - (nowMillis - newest.timestamp));
    }

    /**
     * Indicates whether the task appears stalled: no progress reports for a full window, or progress flat across an
     * entire window of reports. Always {@code false} before the first report.
     */
    public synchronized boolean isStalled(long nowMillis) {
        if (samples.isEmpty()) {
            return false;
        }
        final Sample oldest = samples.getFirst(), newest = samples.getLast();
        if ((nowMillis - newest.timestamp) >= windowMillis) {
            return true;
        }
        return ((newest.timestamp - oldest.timestamp) >= windowMillis) && (newest.progress <= oldest.progress);
    }

    /**
     * Discard all recorded samples, for when the underlying task restarts its progress reporting from scratch.
     */
    public synchronized void reset() {
        samples.clear();
    }

    private final Deque<Sample> samples = new ArrayDeque<>();
    private final long windowMillis, minSpanMillis;

    /**
     * Returned by {@link #remainingMillis} when no estimate is available.
     */
    public static final long UNKNOWN = -1L;

    private static final class Sample {
        Sample(long timestamp, float progress) {
            this.timestamp = timestamp;
            this.progress = progress;
        }

        final long timestamp;
        final float progress;
    }
}
