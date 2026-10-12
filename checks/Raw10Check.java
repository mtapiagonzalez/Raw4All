package local.s23probe;

import java.util.Arrays;

public final class Raw10Check {
    public static void main(String[] args) {
        byte[] packed=new byte[1284],row=new byte[2048];Arrays.fill(packed,(byte)0x7f);
        for(int p=0;p<1024;p+=4) {
            int offset=2+p*5/4,low=0;
            for(int i=0;i<4;i++) {packed[offset+i]=(byte)((p+i)>>2);low|=((p+i)&3)<<(2*i);}
            packed[offset+4]=(byte)low;
        }
        Raw10.unpackRow(packed,2,1024,row);
        for(int i=0;i<1024;i++)if(((row[i*2]&255)|((row[i*2+1]&255)<<8))!=i)throw new AssertionError("Pixel "+i);
        boolean rejected=false;try {Raw10.unpackRow(new byte[4],0,4,new byte[8]);}catch(IllegalArgumentException e) {rejected=true;}
        if(!rejected)throw new AssertionError("Truncated input accepted");
        rejected=false;try {Raw10.unpackRow(packed,0,3,row);}catch(IllegalArgumentException e) {rejected=true;}
        if(!rejected)throw new AssertionError("Invalid width accepted");
        System.out.println("RAW10: all 1024 values, padded offset and malformed rows passed");
    }
}
