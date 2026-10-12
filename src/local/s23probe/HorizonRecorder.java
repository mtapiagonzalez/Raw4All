package local.s23probe;

import android.graphics.SurfaceTexture;
import android.opengl.*;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;
import java.nio.*;
import java.util.concurrent.*;

/** Rotates camera frames on the GPU before they reach MediaRecorder. */
final class HorizonRecorder implements AutoCloseable {
    private final HandlerThread thread=new HandlerThread("Raw4All-Horizon");
    private final Handler handler;
    private final Runnable onError;
    private EGLDisplay display;
    private EGLContext context;
    private EGLSurface window;
    private SurfaceTexture texture;
    private Surface input;
    private int program,textureId;
    private volatile float degrees;
    private volatile boolean active;
    private long firstTimestamp=-1;
    private long firstOutputTimestamp;
    private final float[] matrix=new float[16];
    private final FloatBuffer vertices=ByteBuffer.allocateDirect(8*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    HorizonRecorder(Surface output,Runnable onError) throws Exception {
        this.onError=onError;
        thread.start();handler=new Handler(thread.getLooper());
        FutureTask<Void> init=new FutureTask<>(() -> {initialize(output);return null;});
        handler.post(init);
        try {init.get(5,TimeUnit.SECONDS);}catch(Exception error) {close();throw error;}
    }
    Surface getInputSurface() {return input;}
    void setAngle(float angle) {degrees=angle;}
    void start() {active=true;}
    void pause() {active=false;}
    private void initialize(Surface output) {
        display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        int[] version=new int[2];check(EGL14.eglInitialize(display,version,0,version,1),"EGL initialize");
        int[] attributes={EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,
            EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,0x3142,1,EGL14.EGL_NONE};
        EGLConfig[] configs=new EGLConfig[1];int[] count=new int[1];
        check(EGL14.eglChooseConfig(display,attributes,0,configs,0,1,count,0) && count[0]>0,"EGL config");
        context=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE},0);
        window=EGL14.eglCreateWindowSurface(display,configs[0],output,new int[]{EGL14.EGL_NONE},0);
        check(EGL14.eglMakeCurrent(display,window,window,context),"EGL current");
        int[] ids=new int[1];GLES20.glGenTextures(1,ids,0);textureId=ids[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,textureId);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
        int vertex=shader(GLES20.GL_VERTEX_SHADER,"attribute vec2 position; varying vec2 uv; void main(){gl_Position=vec4(position,0.,1.);uv=position;}");
        int fragment=shader(GLES20.GL_FRAGMENT_SHADER,"#extension GL_OES_EGL_image_external : require\nprecision highp float; varying vec2 uv; uniform samplerExternalOES camera; uniform mat4 transform; uniform float angle; void main(){vec2 p=uv*vec2(1920.,1080.)/2.;float c=cos(angle),s=sin(angle);p=vec2(c*p.x+s*p.y,-s*p.x+c*p.y);p=p/(2.04*vec2(1920.,1080.))+vec2(.5);vec2 t=(transform*vec4(p,0.,1.)).xy;gl_FragColor=texture2D(camera,t);}");
        program=GLES20.glCreateProgram();GLES20.glAttachShader(program,vertex);GLES20.glAttachShader(program,fragment);GLES20.glLinkProgram(program);
        int[] linked=new int[1];GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,linked,0);
        if(linked[0]==0)throw new IllegalStateException(GLES20.glGetProgramInfoLog(program));
        GLES20.glDeleteShader(vertex);GLES20.glDeleteShader(fragment);
        vertices.put(new float[]{-1,-1,1,-1,-1,1,1,1}).position(0);
        texture=new SurfaceTexture(textureId);texture.setDefaultBufferSize(1920,1080);
        texture.setOnFrameAvailableListener(t -> render(),handler);input=new Surface(texture);
    }
    private int shader(int type,String source) {
        int id=GLES20.glCreateShader(type);GLES20.glShaderSource(id,source);GLES20.glCompileShader(id);
        int[] status=new int[1];GLES20.glGetShaderiv(id,GLES20.GL_COMPILE_STATUS,status,0);
        if(status[0]==0)throw new IllegalStateException(GLES20.glGetShaderInfoLog(id));return id;
    }
    private void render() {
        if(texture==null)return;
        try {
            texture.updateTexImage();if(!active)return;texture.getTransformMatrix(matrix);
            GLES20.glViewport(0,0,1920,1080);GLES20.glUseProgram(program);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,textureId);
            GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"camera"),0);
            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program,"transform"),1,false,matrix,0);
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"angle"),(float)Math.toRadians(degrees));
            int position=GLES20.glGetAttribLocation(program,"position");GLES20.glEnableVertexAttribArray(position);
            vertices.position(0);GLES20.glVertexAttribPointer(position,2,GLES20.GL_FLOAT,false,0,vertices);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
            long timestamp=texture.getTimestamp();
            if(firstTimestamp<0) {firstTimestamp=timestamp;firstOutputTimestamp=System.nanoTime();}
            // Camera timestamps may include suspend time; align them to the recorder's
            // monotonic clock while preserving the intervals between captured frames.
            EGLExt.eglPresentationTimeANDROID(display,window,firstOutputTimestamp+timestamp-firstTimestamp);
            check(EGL14.eglSwapBuffers(display,window),"EGL frame");
        }catch(RuntimeException error) {active=false;android.util.Log.e("Raw4All","HORIZON RENDER FAILED",error);onError.run();}
    }
    private static void check(boolean ok,String operation) {if(!ok)throw new IllegalStateException(operation);}
    @Override public void close() {
        active=false;
        FutureTask<Void> release=new FutureTask<>(() -> {
            if(input!=null)input.release();if(texture!=null) {texture.release();texture=null;}
            if(display!=null && display!=EGL14.EGL_NO_DISPLAY) {
                if(program!=0)GLES20.glDeleteProgram(program);
                if(textureId!=0)GLES20.glDeleteTextures(1,new int[]{textureId},0);
                EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
                if(window!=null)EGL14.eglDestroySurface(display,window);
                if(context!=null)EGL14.eglDestroyContext(display,context);EGL14.eglTerminate(display);
            }
            return null;
        });
        handler.post(release);try {release.get(5,TimeUnit.SECONDS);}catch(Exception ignored) {}thread.quitSafely();
    }
}
