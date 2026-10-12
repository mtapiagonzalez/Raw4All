package local.s23probe;

/** Android RAW10: four high bytes followed by four pairs of low bits. */
final class Raw10 {
    static void unpackRow(byte[] packed, int offset, int width, byte[] littleEndian16) {
        if(width<=0 || width%4!=0 || offset<0 || (long)offset+width*5L/4>packed.length || littleEndian16.length<width*2)
            throw new IllegalArgumentException("Invalid RAW10 row");
        for(int x=0,p=offset;x<width;x+=4,p+=5) {
            int low=packed[p+4]&255;
            for(int i=0;i<4;i++) {
                int pixel=((packed[p+i]&255)<<2)|((low>>(2*i))&3);
                littleEndian16[(x+i)*2]=(byte)pixel;
                littleEndian16[(x+i)*2+1]=(byte)(pixel>>8);
            }
        }
    }
}
