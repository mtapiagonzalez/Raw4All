package local.s23probe;

import android.hardware.camera2.*;
import android.hardware.camera2.params.*;
import android.os.Build;
import android.util.Rational;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/** Uncompressed CFA DNG. Keeps all RAW10 samples; no demosaic, resizing or tone curve.
 * The vendor stream has its own dimensions, unlike the standard 12 MP Camera2 map.
 * Tags follow the Adobe Digital Negative specification and TIFF 6.0.
 */
final class RawDng {
    private static final class Tag {
        final int id,type,count; final byte[] data;
        Tag(int id,int type,int count,byte[] data) {this.id=id;this.type=type;this.count=count;this.data=data;}
    }
    private static ByteBuffer buffer(int bytes) {return ByteBuffer.allocate(bytes).order(ByteOrder.LITTLE_ENDIAN);}
    private static Tag longs(int id,int... values) {
        ByteBuffer b=buffer(values.length*4);for(int v:values)b.putInt(v);return new Tag(id,4,values.length,b.array());
    }
    private static Tag shorts(int id,int... values) {
        ByteBuffer b=buffer(values.length*2);for(int v:values)b.putShort((short)v);return new Tag(id,3,values.length,b.array());
    }
    private static Tag bytes(int id,int... values) {
        byte[] b=new byte[values.length];for(int i=0;i<b.length;i++)b[i]=(byte)values[i];return new Tag(id,1,b.length,b);
    }
    private static Tag text(int id,String value) {
        byte[] b=(value+"\0").getBytes(StandardCharsets.US_ASCII);return new Tag(id,2,b.length,b);
    }
    private static Tag rational(int id,boolean signed,Rational... values) {
        ByteBuffer b=buffer(values.length*8);
        for(Rational v:values) {if(v==null || !v.isFinite())throw new IllegalArgumentException("Missing DNG calibration");b.putInt(v.getNumerator());b.putInt(v.getDenominator());}
        return new Tag(id,signed ? 10 : 5,values.length,b.array());
    }
    private static void matrix(List<Tag> tags,int id,ColorSpaceTransform matrix) {
        if(matrix==null)return;Rational[] values=new Rational[9];matrix.copyElements(values,0);tags.add(rational(id,true,values));
    }
    private static byte[] ifd(List<Tag> tags,int base) {
        tags.sort(Comparator.comparingInt(t -> t.id));int length=2+tags.size()*12+4;
        for(Tag t:tags)if(t.data.length>4)length+=(t.data.length+1)&~1;
        ByteBuffer b=buffer(length);b.putShort((short)tags.size());int extra=2+tags.size()*12+4;
        for(Tag t:tags) {
            b.putShort((short)t.id).putShort((short)t.type).putInt(t.count);
            if(t.data.length<=4) {b.put(t.data);for(int i=t.data.length;i<4;i++)b.put((byte)0);}
            else {b.putInt(base+extra);System.arraycopy(t.data,0,b.array(),extra,t.data.length);extra+=(t.data.length+1)&~1;}
        }
        b.putInt(0);return b.array();
    }
    static void write(OutputStream output,byte[] raw,int width,int height,int stride,
            CameraCharacteristics c,TotalCaptureResult result,int orientation) throws IOException {
        if(width%4!=0 || height<=0 || stride<width*5/4 || (long)stride*(height-1)+width*5L/4>raw.length)
            throw new IOException("Incomplete RAW10 image");
        Integer arrangement=c.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT);
        int[][] patterns={{0,1,1,2},{1,0,2,1},{1,2,0,1},{2,1,1,0}};
        Rational[] neutral=result.get(CaptureResult.SENSOR_NEUTRAL_COLOR_POINT);
        Integer white=result.get(CaptureResult.SENSOR_DYNAMIC_WHITE_LEVEL);
        if(white==null)white=c.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL);
        if(arrangement==null || arrangement<0 || arrangement>3 || neutral==null || neutral.length!=3 || white==null || white!=1023
                || c.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM1)==null || c.get(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT1)==null)
            throw new IOException("Missing or incompatible RAW10 color calibration");
        float[] black=result.get(CaptureResult.SENSOR_DYNAMIC_BLACK_LEVEL);
        if(black==null) {
            BlackLevelPattern pattern=c.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN);
            if(pattern==null)throw new IOException("Missing black level");
            black=new float[]{pattern.getOffsetForIndex(0,0),pattern.getOffsetForIndex(1,0),pattern.getOffsetForIndex(0,1),pattern.getOffsetForIndex(1,1)};
        }
        Rational[] blackR=new Rational[4];for(int i=0;i<4;i++)blackR[i]=new Rational(Math.round(black[i]*10000),10000);
        List<Tag> tags=new ArrayList<>(),exif=new ArrayList<>();
        tags.add(longs(254,0));tags.add(longs(256,width));tags.add(longs(257,height));
        tags.add(shorts(258,16));tags.add(shorts(259,1));tags.add(shorts(262,32803));
        tags.add(text(270,"Raw4All single-frame RAW10; unpacked losslessly to 16-bit storage."));
        tags.add(text(271,Build.MANUFACTURER));tags.add(text(272,Build.MODEL));
        Tag strip=longs(273,0);tags.add(strip);
        tags.add(shorts(274,orientation));tags.add(shorts(277,1));tags.add(longs(278,height));tags.add(longs(279,width*height*2));
        tags.add(shorts(284,1));tags.add(text(305,"Raw4All"));
        String date=new SimpleDateFormat("yyyy:MM:dd HH:mm:ss",Locale.US).format(new Date());tags.add(text(306,date));
        tags.add(shorts(33421,2,2));tags.add(bytes(33422,patterns[arrangement]));
        Tag exifOffset=longs(34665,0);tags.add(exifOffset);
        tags.add(bytes(50706,1,4,0,0));tags.add(bytes(50707,1,3,0,0));
        tags.add(text(50708,Build.MANUFACTURER+" "+Build.MODEL+" Raw4All W 50MP"));
        tags.add(bytes(50710,0,1,2));tags.add(shorts(50711,1));tags.add(shorts(50713,2,2));tags.add(rational(50714,false,blackR));
        tags.add(longs(50717,white));tags.add(rational(50718,false,new Rational(1,1),new Rational(1,1)));
        tags.add(longs(50719,0,0));tags.add(longs(50720,width,height));tags.add(longs(50829,0,0,height,width));
        matrix(tags,50721,c.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM1));
        matrix(tags,50722,c.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM2));
        matrix(tags,50723,c.get(CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM1));
        matrix(tags,50724,c.get(CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM2));
        matrix(tags,50964,c.get(CameraCharacteristics.SENSOR_FORWARD_MATRIX1));
        matrix(tags,50965,c.get(CameraCharacteristics.SENSOR_FORWARD_MATRIX2));
        tags.add(rational(50728,false,neutral));tags.add(shorts(50778,c.get(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT1)));
        Byte light2=c.get(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT2);if(light2!=null)tags.add(shorts(50779,light2&255));
        Long exposure=result.get(CaptureResult.SENSOR_EXPOSURE_TIME);if(exposure!=null)exif.add(rational(33434,false,new Rational((int)(exposure/1000),1000000)));
        Integer iso=result.get(CaptureResult.SENSOR_SENSITIVITY);if(iso!=null)exif.add(shorts(34855,Math.min(65535,iso)));
        Float aperture=result.get(CaptureResult.LENS_APERTURE);if(aperture!=null)exif.add(rational(33437,false,new Rational(Math.round(aperture*100),100)));
        Float focal=result.get(CaptureResult.LENS_FOCAL_LENGTH);if(focal!=null)exif.add(rational(37386,false,new Rational(Math.round(focal*1000),1000)));
        exif.add(new Tag(36864,7,4,new byte[]{'0','2','3','2'}));exif.add(text(36867,date));
        int exifBase=8+ifd(tags,8).length;byte[] exifData=ifd(exif,exifBase);
        ByteBuffer.wrap(exifOffset.data).order(ByteOrder.LITTLE_ENDIAN).putInt(exifBase);
        ByteBuffer.wrap(strip.data).order(ByteOrder.LITTLE_ENDIAN).putInt(exifBase+exifData.length);
        output.write(new byte[]{'I','I',42,0,8,0,0,0});output.write(ifd(tags,8));output.write(exifData);
        byte[] row=new byte[width*2];
        for(int y=0;y<height;y++) {Raw10.unpackRow(raw,y*stride,width,row);output.write(row);}
        output.flush();
    }
}
