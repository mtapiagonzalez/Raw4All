package local.s23probe;

/** Circular One Euro filter: Casiez, Roussel and Vogel, CHI 2012.
 * Original implementation of the published algorithm, with degrees and seconds.
 */
final class HorizonFilter {
    private static final double MIN_CUTOFF=1.5, SPEED_GAIN=0.045, DERIVATIVE_CUTOFF=1.0;
    private boolean initialized;
    private double lastRaw, unwrapped, filtered, velocity;
    private long lastTime;

    float update(float degrees,long timeNanos) {
        double dt=(timeNanos-lastTime)*1e-9;
        if(!initialized || dt>0.5) {
            initialized=true;lastRaw=unwrapped=filtered=degrees;velocity=0;lastTime=timeNanos;
            return normalize(filtered);
        }
        if(dt<=0)return normalize(filtered);
        double change=((degrees-lastRaw+540)%360)-180;
        lastRaw=degrees;lastTime=timeNanos;unwrapped+=change;
        velocity+=alpha(DERIVATIVE_CUTOFF,dt)*(change/dt-velocity);
        double cutoff=MIN_CUTOFF+SPEED_GAIN*Math.abs(velocity);
        filtered+=alpha(cutoff,dt)*(unwrapped-filtered);
        return normalize(filtered);
    }
    void reset() {initialized=false;}
    private static double alpha(double cutoff,double dt) {
        return 1.0/(1.0+1.0/(2.0*Math.PI*cutoff*dt));
    }
    private static float normalize(double angle) {return (float)((angle%360+360)%360);}
}
