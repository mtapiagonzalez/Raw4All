package local.s23probe;

public final class RecordingClockCheck {
    public static void main(String[] args) {
        RecordingClock clock=new RecordingClock();clock.start(1000);
        clock.pause(2347);if(clock.elapsed(9999)!=1347)throw new AssertionError("Paused clock advanced");
        clock.pause(3000);clock.resume(4347);clock.resume(5000);
        if(clock.elapsed(5000)!=2000)throw new AssertionError("Pause included or duplicate resume counted");
        clock.pause(6000);clock.resume(7000);
        if(clock.elapsed(8000)!=4000)throw new AssertionError("Multiple pauses");
        clock.start(10000);if(clock.elapsed(10000)!=0 || clock.isPaused())throw new AssertionError("Restart");
        long[] times={0,9,999,1000,59999,60000,3599999,3600000,360000999};
        String[] expected={"00:00.000","00:00.009","00:00.999","00:01.000","00:59.999","01:00.000","59:59.999","01:00:00.000","100:00:00.999"};
        for(int i=0;i<times.length;i++)if(!RecordingClock.format(times[i]).equals(expected[i]))throw new AssertionError("Format "+times[i]);
        System.out.println("Recording clock: milliseconds, boundaries, repeated pauses and restart passed");
    }
}
