package local.s23probe;

/** Select only advertised AE ranges that the configured streams can sustain. */
final class FpsPolicy {
    static int[] selectRange(int fps, int[][] ranges, long previewDuration,
            long recorderDuration, boolean encoderSupported) {
        if ((fps != 30 && fps != 60) || !encoderSupported || ranges == null) return null;
        long duration = Math.max(previewDuration, recorderDuration);
        // One nanosecond of rounding is allowed in camera metadata.
        if (duration > 0 && duration > 1_000_000_000L / fps + 1) return null;
        int[] best = null;
        for (int[] range : ranges) {
            if (range == null || range.length != 2 || range[0] > fps || range[1] != fps) continue;
            if (best == null || range[0] > best[0]) best = range;
        }
        return best;
    }
}
