package local.s23probe;

public class TimerCheck {
    public static void main(String[] args) {
        long[] seconds = {0, 59, 60, 3599, 3600, 3661, 360000};
        String[] expected = {"00:00", "00:59", "01:00", "59:59", "01:00:00", "01:01:01", "100:00:00"};
        for (int i = 0; i < seconds.length; i++) {
            String actual = MainActivity.elapsedLabel(seconds[i]);
            if (!actual.equals(expected[i])) throw new AssertionError(seconds[i] + ": " + actual);
        }
        System.out.println("Timer boundaries passed: seconds, minutes, hours, 100 hours.");
    }
}
