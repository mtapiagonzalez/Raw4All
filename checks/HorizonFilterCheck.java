package local.s23probe;

public final class HorizonFilterCheck {
    private static double delta(double a,double b) {return (a-b+540)%360-180;}
    public static void main(String[] args) {
        for(int hz:new int[]{50,100}) {
            HorizonFilter filter=new HorizonFilter();double energy=0;
            for(int i=0;i<hz*4;i++) {
                float input=(float)(180+0.6*Math.sin(2*Math.PI*12*i/hz));
                float output=filter.update(input,1_000_000_000L+i*(1_000_000_000L/hz));
                if(i>=hz)energy+=Math.pow(delta(output,180),2);
            }
            double rms=Math.sqrt(energy/(hz*3));
            if(rms>0.1)throw new AssertionError("Stationary jitter: "+rms);
            filter.reset();float output=0;
            for(int i=0;i<=hz;i++)output=filter.update((float)((350+180.0*i/hz)%360),1_000_000_000L+i*(1_000_000_000L/hz));
            double lag=Math.abs(delta(output,170));
            if(lag>8)throw new AssertionError("Fast turn lag: "+lag);
            float resumed=filter.update(45,4_000_000_000L);
            if(resumed!=45)throw new AssertionError("Resume reset");
            System.out.printf("%d Hz: jitter RMS %.3f degrees, fast-turn lag %.2f degrees; wrap/resume OK%n",hz,rms,lag);
        }
    }
}
