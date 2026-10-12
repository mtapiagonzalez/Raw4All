package local.s23probe;

public class FpsPolicyCheck {
    public static void main(String[] args) {
        int[][] ranges = {{7, 30}, {30, 30}, {30, 60}, {60, 60}};
        require(FpsPolicy.selectRange(30, ranges, 33333333, 33333333, true)[0] == 30, "Prefer fixed 30 fps");
        require(FpsPolicy.selectRange(60, ranges, 16666667, 16666667, true)[0] == 60, "Accept rounded 60 fps duration");
        require(FpsPolicy.selectRange(60, ranges, 33333333, 16666667, true) == null, "Slow preview limits recording");
        require(FpsPolicy.selectRange(60, ranges, 16666667, 33333333, true) == null, "Slow recording stream limits recording");
        require(FpsPolicy.selectRange(60, new int[][]{{7,30},{30,30}}, 0, 0, true) == null, "Do not invent AE ranges");
        require(FpsPolicy.selectRange(60, ranges, 0, 0, false) == null, "Require encoder support");
        require(FpsPolicy.selectRange(60, new int[][]{{30,60}}, 0, 0, true)[0] == 30, "Accept variable advertised range");
        require(FpsPolicy.selectRange(60, new int[][]{{60,120}}, 0, 0, true) == null, "Do not request a higher unselected rate");
        require(FpsPolicy.selectRange(24, ranges, 0, 0, true) == null, "Reject unsupported UI rate");
        System.out.println("FPS policy passed: AE ranges, stream limits, metadata rounding, encoder limits.");
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
