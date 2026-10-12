package local.s23probe;

import java.util.Locale;

/** Elapsed time from monotonic timestamps, excluding every paused interval. */
final class RecordingClock {
    private long start, pausedAt, pausedTotal;
    private boolean paused;
    void start(long now) {start=now;pausedAt=pausedTotal=0;paused=false;}
    void pause(long now) {if(!paused) {pausedAt=now;paused=true;}}
    void resume(long now) {if(paused) {pausedTotal+=now-pausedAt;paused=false;}}
    long elapsed(long now) {return Math.max(0,(paused ? pausedAt : now)-start-pausedTotal);}
    long pausedMillis(long now) {return pausedTotal+(paused ? now-pausedAt : 0);}
    boolean isPaused() {return paused;}
    static String format(long millis) {
        millis=Math.max(0,millis);long seconds=millis/1000;
        return seconds>=3600 ? String.format(Locale.US,"%02d:%02d:%02d.%03d",seconds/3600,(seconds/60)%60,seconds%60,millis%1000)
            : String.format(Locale.US,"%02d:%02d.%03d",seconds/60,seconds%60,millis%1000);
    }
}
