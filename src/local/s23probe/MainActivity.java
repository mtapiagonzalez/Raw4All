package local.s23probe;

import android.app.Activity;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.hardware.camera2.*;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.camera2.params.SessionConfiguration;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.MeteringRectangle;
import android.media.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import android.util.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private static final long RESERVE = 96L * 1024 * 1024;
    private static final int ACCENT = 0xffa7f3d0, PANEL = 0xff171d23, WHITE = 0xfff1f5f9;
    private CameraManager manager;
    private CameraDevice camera;
    private CameraCaptureSession session;
    private MediaRecorder recorder;
    private ImageReader jpegReader;
    private boolean rawVendorSession;
    private boolean captureRawOnOpen;
    private Surface preview;
    private TextureView texture;
    private FrameLayout viewfinder;
    private TextView storage, timer, message, appTitle, photoCountdown;
    private CountdownDepthView countdownDepth;
    private Button shutter, mode, lens, format, gallery, fpsButton, stabilizationButton, infoButton, focusButton;
    private int selectedFps = 30;
    private boolean stabilizationEnabled = true;
    private boolean freeRotation, opticalOnly;
    private Button hideUiButton;
    private Button megapixelsButton;
    private Size selectedPhotoSize;
    private boolean audioEnabled=true, gridEnabled, countdownActive;
    private Button resetFocusButton;
    private int gridMode;
    private static final String[] GRID_NAMES={"Sin cuadrícula","Tercios","Proporción áurea","Diagonales","Centro","4 × 4"};
    private int photoDelay, countdownSequence, exposureCompensation, exposureAtTouch;
    private float targetZoom=1f, audioLevel;
    private final Map<String,Float> sessionZooms=new HashMap<>();
    private ScaleGestureDetector pinch;
    private boolean multiTouch;
    private long estimateMinutes;
    private final Runnable smoothZoom=new Runnable() {public void run() {
        if(!resumed || camera==null)return;
        zoom+=0.22f*(targetZoom-zoom);if(Math.abs(targetZoom-zoom)<.005f)zoom=targetZoom;
        updateZoomLabel();zoomSlider.setProgress(maxZoom<=1 ? 0 : Math.round((zoom-1)/(maxZoom-1)*1000));applyZoom();
        if(zoom!=targetZoom)handler.postDelayed(this,33);
        else sessionZooms.put(cameraId,zoom);
    }};
    private final Runnable audioMeter=new Runnable() {public void run() {
        if(!resumed)return;
        float level=0;try {if(recording && !recordingClock.isPaused() && audioEnabled && recorder!=null)level=(float)Math.max(0,(20*Math.log10(Math.max(1,recorder.getMaxAmplitude())/32767.0)+60)/60);}catch(IllegalStateException ignored) {}
        audioLevel=Math.max(level,audioLevel*.75f);if(focusOverlay!=null)focusOverlay.invalidate();handler.postDelayed(this,100);
    }};
    private boolean recordingUiHidden;
    private boolean horizonEnabled;
    private final HorizonFilter horizonFilter=new HorizonFilter();
    private float horizonDegrees;
    private float latestHorizonDegrees;
    private int horizonBase;
    private HorizonRecorder horizonRecorder;
    private android.hardware.SensorManager horizonSensors;
    private android.hardware.Sensor horizonSensor;
    private final android.hardware.SensorEventListener horizonListener=new android.hardware.SensorEventListener() {
        private final float[] rotationMatrix=new float[9];
        public void onSensorChanged(android.hardware.SensorEvent event) {
            android.hardware.SensorManager.getRotationMatrixFromVector(rotationMatrix,event.values);
            float x=rotationMatrix[6],y=rotationMatrix[7];
            // Roll is undefined when the phone lies flat; hold the previous correction.
            if(x*x+y*y<0.16f)return;
            latestHorizonDegrees=(float)((Math.toDegrees(Math.atan2(-x,y))+360)%360);
            if(horizonEnabled && !photoMode)updateHorizon(latestHorizonDegrees);
        }
        public void onAccuracyChanged(android.hardware.Sensor sensor,int accuracy) {}
    };
    private String cameraId = "56";
    private boolean portrait = true, photoMode, busy, recording, resumed, photoPending;
    private int generation, recordingSequence;
    private OrientationEventListener orientationListener;
    private int deviceDegrees, captureDegrees;
    private float zoom = 1f, maxZoom = 10f;
    private SeekBar zoomSlider;
    private TextView zoomLabel;
    private CaptureRequest.Builder repeatingRequest;
    private String stabilization = "";
    private int formatMode;
    private boolean orientationLocked;
    private LinearLayout rootLayout, landscapeBody, landscapeToolbar;
    private LinearLayout titleLayout, portraitHeader, portraitFooter;
    private FrameLayout contentRoot;
    private int insetTop, insetBottom, insetLeft, insetRight;
    private FocusOverlay focusOverlay;
    private GestureDetector focusGestures;
    private int focusSequence;
    private boolean focusPending, focusLocked, exposureLocked, focusHasAuto;
    private Rect lastSensorCrop;
    private MeteringRectangle[] focusRegions, exposureRegions;
    private Float lockedFocusDistance;
    private final CameraCaptureSession.CaptureCallback meteringCallback=new CameraCaptureSession.CaptureCallback() {
        @Override public void onCaptureCompleted(CameraCaptureSession s,CaptureRequest request,TotalCaptureResult result) {if(s==session)handleMeteringResult(result);}
    };
    private View[] uiBlocks;
    private final RecordingClock recordingClock=new RecordingClock();
    private long lastStorageCheck;
    private Uri videoUri, lastUri;
    private String lastMime = "video/mp4";
    private final ArrayList<SessionCapture> sessionCaptures=new ArrayList<>();
    private static final class SessionCapture {
        final Uri uri;final String mime;
        SessionCapture(Uri uri,String mime) {this.uri=uri;this.mime=mime;}
    }
    private ParcelFileDescriptor videoFd;

    private void report(String text) {
        if((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)==0)return;
        Log.i("S23Probe", text);
        File log=new File(getFilesDir(),"probe.log");
        try (FileWriter w = new FileWriter(log,log.length()<1024*1024)) { w.write(System.currentTimeMillis() + " " + text + "\n"); }
        catch (IOException ignored) { }
    }
    private void notice(String text) { report(text); message.setText(text); }
    @SuppressWarnings("deprecation")
    private void coloredToast(String text) {
        if(!resumed)return;
        TextView content=label("✓  "+text,14,0xff0b1015);content.setGravity(Gravity.CENTER);
        content.setPadding(dp(18),dp(12),dp(18),dp(12));content.setBackground(shape(ACCENT,20));
        Toast toast=new Toast(this);toast.setView(content);toast.setDuration(Toast.LENGTH_LONG);
        toast.setGravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL,0,dp(96));toast.show();
    }
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private GradientDrawable shape(int color, int radius) { GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d; }
    private Button button(String text,int color) {
        Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextColor(WHITE);b.setTextSize(14);b.setBackground(shape(color,28));b.setElevation(dp(5));
        b.setPadding(dp(12),0,dp(12),0);b.setMinWidth(0);b.setMinimumWidth(0);return b;
    }
    private TextView label(String text,int size,int color) { TextView v=new TextView(this);v.setText(text);v.setTextColor(color);v.setTextSize(size);return v; }
    private LinearLayout row() { LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);return r; }
    private void addControl(LinearLayout row,View control,int width,int height) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(width),dp(height));p.setMargins(dp(5),0,dp(5),0);row.addView(control,p);
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(state==null && Build.VERSION.SDK_INT>=31) {
            final long splashStart=SystemClock.uptimeMillis();
            getSplashScreen().setOnExitAnimationListener(splash -> {
                View brand=findSplashBrand(splash,splash.getIconView());
                report("SPLASH RGB branding="+(brand!=null)+" elapsed="+(SystemClock.uptimeMillis()-splashStart));
                if(brand!=null && splashBrandDrawable(brand)!=null && splashBrandDrawable(brand).getConstantState()!=null) {
                    SplashBrandGlitch glitch=new SplashBrandGlitch(brand,splashStart);
                    brand.getOverlay().add(glitch);
                    brand.post(new Runnable() {public void run() {
                        if(!brand.isAttachedToWindow() || SystemClock.uptimeMillis()-splashStart>=1200) {brand.getOverlay().remove(glitch);return;}
                        glitch.setBounds(0,0,brand.getWidth(),brand.getHeight());glitch.invalidateSelf();brand.postOnAnimation(this);
                    }});
                }
                splash.postDelayed(splash::remove,Math.max(0,splashStart+1200-SystemClock.uptimeMillis()));
            });
        }else if(state==null) {
            final View decor=getWindow().getDecorView();
            final long splashUntil=SystemClock.uptimeMillis()+1200;
            decor.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
                @Override public boolean onPreDraw() {
                    if(SystemClock.uptimeMillis()<splashUntil)return false;
                    decor.getViewTreeObserver().removeOnPreDrawListener(this);return true;
                }
            });
            decor.postDelayed(decor::invalidate,1200);
        }
        if(state!=null) {
            ArrayList<String> uris=state.getStringArrayList("session_uris"),mimes=state.getStringArrayList("session_mimes");
            if(uris!=null && mimes!=null && uris.size()==mimes.size())
                for(int i=0;i<uris.size();i++)sessionCaptures.add(new SessionCapture(Uri.parse(uris.get(i)),mimes.get(i)));
        }
        getWindow().setStatusBarColor(0xff0b1015);getWindow().setNavigationBarColor(0xff0b1015);manager=getSystemService(CameraManager.class);
        // The public launcher accepts no camera or automation commands from callers.
        cameraId=getPreferences(0).getString("lens","56");
        audioEnabled=getPreferences(0).getBoolean("audio",true);gridEnabled=getPreferences(0).getBoolean("grid",false);photoDelay=getPreferences(0).getInt("delay",0);
        gridMode=getPreferences(0).getInt("grid_mode",gridEnabled ? 1 : 0);if(gridMode<0 || gridMode>=GRID_NAMES.length)gridMode=0;gridEnabled=gridMode!=0;
        zoom=targetZoom=1f;
        selectedFps=getPreferences(0).getInt("fps",30);
        stabilizationEnabled=getPreferences(0).getBoolean("stabilization",true);
        horizonEnabled=getPreferences(0).getBoolean("horizon",false);freeRotation=getPreferences(0).getBoolean("free_rotation",false);opticalOnly=getPreferences(0).getBoolean("optical_only",false);
        formatMode=getPreferences(0).getInt("format",0);
        portrait=true;photoMode=getPreferences(0).getBoolean("photo_mode",false);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xff0b1015);root.setPadding(dp(12),dp(10),dp(12),dp(12));
        LinearLayout top=row(),title=new LinearLayout(this);titleLayout=title;title.setOrientation(LinearLayout.VERTICAL);
        TextView name=label("Raw4All",22,WHITE);appTitle=name;name.setLetterSpacing(.08f);name.setTypeface(null,1);title.addView(name);
        try {name.setContentDescription("Raw4All "+getPackageManager().getPackageInfo(getPackageName(),0).versionName);name.setOnLongClickListener(v -> {try {coloredToast("Raw4All "+getPackageManager().getPackageInfo(getPackageName(),0).versionName);}catch(Exception ignored) {}return true;});}catch(Exception ignored) {}
        storage=label("Calculando espacio…",12,0xff9caebc);title.addView(storage);top.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        format=button(portrait ? "9:16" : "16:9",PANEL);format.setContentDescription("Alternar formato horizontal y vertical");
        format.setText("Auto · 9:16");format.setTextSize(11);format.setContentDescription("Elegir Auto, 9:16 o 16:9");
        format.setOnLongClickListener(v -> {chooseRotation();return true;});format.setOnClickListener(v -> {if(locked())return;formatMode=(formatMode+1)%3;if(formatMode==0)freeRotation=false;getPreferences(0).edit().putInt("format",formatMode).putBoolean("free_rotation",freeRotation).apply();applyFormatPolicy();updateOrientation();});addControl(top,format,92,46);root.addView(top);
        timer=label("00:00.000",20,WHITE);timer.setGravity(Gravity.CENTER);timer.setTypeface(Typeface.MONOSPACE);root.addView(timer,new LinearLayout.LayoutParams(-1,dp(38)));
        photoCountdown=label("",22,Color.WHITE);photoCountdown.setGravity(Gravity.CENTER);photoCountdown.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);
        photoCountdown.setShadowLayer(dp(4),0,dp(1),Color.BLACK);photoCountdown.setVisibility(View.GONE);
        viewfinder=new FrameLayout(this);viewfinder.setBackground(shape(0xff020406,20));viewfinder.setClipToOutline(true);
        texture=new TextureView(this);viewfinder.addView(texture,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));root.addView(viewfinder,new LinearLayout.LayoutParams(-1,0,1));
        focusOverlay=new FocusOverlay();viewfinder.addView(focusOverlay,new FrameLayout.LayoutParams(-1,-1));
        countdownDepth=new CountdownDepthView();viewfinder.addView(countdownDepth,new FrameLayout.LayoutParams(-1,-1));
        focusGestures=new GestureDetector(this,new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent event) {return true;}
            @Override public boolean onSingleTapUp(MotionEvent event) {focusAt(event.getX(),event.getY(),false);return true;}
            @Override public void onLongPress(MotionEvent event) {focusAt(event.getX(),event.getY(),true);}
            @Override public boolean onDoubleTap(MotionEvent event) {restoreFocus();return true;}
            @Override public boolean onScroll(MotionEvent first,MotionEvent current,float dx,float dy) {
                if(!multiTouch && first!=null && Math.abs(current.getY()-first.getY())>dp(12))adjustExposure(first.getY()-current.getY());return true;
            }
        });
        pinch=new ScaleGestureDetector(this,new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            public boolean onScale(ScaleGestureDetector detector) {setTargetZoom(targetZoom*detector.getScaleFactor());return true;}
        });
        texture.setOnTouchListener((v,event) -> {if(event.getActionMasked()==MotionEvent.ACTION_DOWN) {multiTouch=false;exposureAtTouch=exposureCompensation;}if(event.getPointerCount()>1 && !multiTouch) {multiTouch=true;MotionEvent cancel=MotionEvent.obtain(event);cancel.setAction(MotionEvent.ACTION_CANCEL);focusGestures.onTouchEvent(cancel);cancel.recycle();}pinch.onTouchEvent(event);if(!multiTouch)focusGestures.onTouchEvent(event);return true;});
        texture.setContentDescription("Toca para enfocar; mantén pulsado para bloquear enfoque y exposición");
        viewfinder.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> layoutPreview());
        message=label("1080p · 30 fps · audio",12,0xff9caebc);message.setGravity(Gravity.CENTER);root.addView(message,new LinearLayout.LayoutParams(-1,dp(34)));
        LinearLayout cameras=row();cameras.setGravity(Gravity.CENTER);lens=button(cameraId.equals("54") ? "ST · 10×" : "W · 1×",PANEL);lens.setContentDescription("Alternar cámara W y ST");
        lens.setOnClickListener(v -> chooseLens());addControl(cameras,lens,110,42);
        megapixelsButton=button("MP",PANEL);megapixelsButton.setContentDescription("Elegir megapíxeles de fotografía");megapixelsButton.setOnClickListener(v -> chooseMegapixels());addControl(cameras,megapixelsButton,100,42);root.addView(cameras);
        LinearLayout settings=row();settings.setGravity(Gravity.CENTER);
        fpsButton=button(selectedFps+" fps",PANEL);fpsButton.setContentDescription("Elegir 30 o 60 fotogramas por segundo");
        fpsButton.setOnClickListener(v -> chooseFps());addControl(settings,fpsButton,90,38);
        stabilizationButton=button(stabilizationEnabled ? "Estab. ON" : "Estab. OFF",PANEL);stabilizationButton.setContentDescription("Activar o desactivar estabilización de la cámara");
        stabilizationButton.setOnClickListener(v -> chooseStabilization());addControl(settings,stabilizationButton,125,38);root.addView(settings);
        LinearLayout focusActions=row();addControl(settings,focusActions,76,38);
        focusButton=button("",PANEL);resetFocusButton=button("",PANEL);
        focusButton.setPadding(0,0,0,0);resetFocusButton.setPadding(0,0,0,0);
        LinearLayout.LayoutParams gearSlot=new LinearLayout.LayoutParams(0,-1,1);gearSlot.rightMargin=dp(2);focusActions.addView(focusButton,gearSlot);
        LinearLayout.LayoutParams resetSlot=new LinearLayout.LayoutParams(0,-1,1);resetSlot.leftMargin=dp(2);focusActions.addView(resetFocusButton,resetSlot);
        resetFocusButton.setForeground(new SettingsIcon(5,WHITE));resetFocusButton.setContentDescription("Restablecer enfoque automático y exposición a cero");resetFocusButton.setTooltipText("Enfoque automático · exposición 0 EV");resetFocusButton.setOnClickListener(v -> resetFocusAndLight());
        focusButton.setOnClickListener(v -> showTools());focusButton.setContentDescription("Ajustes de audio, temporizador, cuadrícula y enfoque");
        focusButton.setForeground(new SettingsIcon(0,WHITE));
        for(int i=0;i<settings.getChildCount();i++) {LinearLayout.LayoutParams slot=(LinearLayout.LayoutParams)settings.getChildAt(i).getLayoutParams();slot.width=0;slot.weight=1;settings.getChildAt(i).setLayoutParams(slot);}
        infoButton=button("ⓘ",0x50171d23);infoButton.setTextSize(24);infoButton.setContentDescription("Información, versión y créditos de Raw4All");infoButton.setOnClickListener(v -> showInfo());
        LinearLayout zoomRow=row();zoomLabel=label("",13,WHITE);zoomLabel.setGravity(Gravity.CENTER);zoomLabel.setIncludeFontPadding(false);zoomLabel.setShadowLayer(dp(3),0,dp(1),Color.BLACK);zoomRow.addView(zoomLabel,new LinearLayout.LayoutParams(dp(64),dp(32)));
        zoomSlider=new SeekBar(this);zoomSlider.setMax(1000);zoomRow.addView(zoomSlider,new LinearLayout.LayoutParams(0,dp(36),1));root.addView(zoomRow);updateZoomLabel();
        zoomLabel.setContentDescription("Accesos rápidos de zoom");zoomLabel.setOnClickListener(v -> {ArrayList<Float> values=new ArrayList<>();ArrayList<String> labels=new ArrayList<>();for(float z:new float[]{1,2,4,8})if(z<=maxZoom) {values.add(z);labels.add(String.format(Locale.US,"%.0f×",z*(cameraId.equals("54") ? 10 : 1)));}new android.app.AlertDialog.Builder(this).setTitle("Zoom").setItems(labels.toArray(new String[0]),(d,i) -> setTargetZoom(values.get(i))).show();});
        zoomSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s,int value,boolean user) {if(user)setTargetZoom(1f+value/1000f*(maxZoom-1f));}
            public void onStartTrackingTouch(SeekBar s) {} public void onStopTrackingTouch(SeekBar s) {}
        });
        LinearLayout controls=row();controls.setGravity(Gravity.CENTER);controls.setPadding(0,dp(16),0,dp(4));
        gallery=button("",PANEL);gallery.setForeground(new CaptureIcon(2,WHITE));gallery.setContentDescription("Abrir fotos y videos de esta sesión");gallery.setOnClickListener(v -> openGallery());
        shutter=button("●",ACCENT);shutter.setTextSize(36);shutter.setTextColor(0xff0b1015);shutter.setOnClickListener(v -> captureAction());
        mode=button(photoMode ? "Video" : "Foto",PANEL);mode.setContentDescription("Alternar foto y video");
        mode.setOnClickListener(v -> {if(recording) {toggleRecordingPause();return;}if(locked())return;photoMode=!photoMode;getPreferences(0).edit().putBoolean("photo_mode",photoMode).apply();updateControls();createSession(false);});
        addControl(controls,gallery,58,58);addControl(controls,shutter,84,84);addControl(controls,mode,70,58);root.addView(controls);
        rootLayout=root;uiBlocks=new View[]{top,timer,message,cameras,settings,zoomRow,controls};
        landscapeBody=new LinearLayout(this);landscapeBody.setOrientation(LinearLayout.HORIZONTAL);
        landscapeToolbar=row();landscapeToolbar.setPadding(0,dp(6),0,0);
        portraitHeader=new LinearLayout(this);portraitHeader.setOrientation(LinearLayout.VERTICAL);
        portraitHeader.setClipChildren(false);portraitHeader.setClipToPadding(false);
        portraitFooter=new LinearLayout(this);portraitFooter.setOrientation(LinearLayout.VERTICAL);
        contentRoot=new FrameLayout(this);contentRoot.setBackgroundColor(0xff0b1015);contentRoot.addView(root,new FrameLayout.LayoutParams(-1,-1));
        setContentView(contentRoot);updateControls();updateStorage();applyFormatPolicy();adaptLayout();
        texture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            public void onSurfaceTextureAvailable(SurfaceTexture s,int w,int h) {maybeOpen();}
            public void onSurfaceTextureSizeChanged(SurfaceTexture s,int w,int h) {s.setDefaultBufferSize(1920,1080);transformPreview();}
            public boolean onSurfaceTextureDestroyed(SurfaceTexture s) {closeCamera();return true;}
            public void onSurfaceTextureUpdated(SurfaceTexture s) { }
        });
        contentRoot.setOnApplyWindowInsetsListener((v,insets) -> {insetTop=insets.getSystemWindowInsetTop();insetBottom=insets.getSystemWindowInsetBottom();insetLeft=insets.getSystemWindowInsetLeft();insetRight=insets.getSystemWindowInsetRight();applyInsets();return insets;});
        contentRoot.requestApplyInsets();
        orientationListener=new OrientationEventListener(this,android.hardware.SensorManager.SENSOR_DELAY_GAME) {
            public void onOrientationChanged(int degrees) {
                if(degrees==ORIENTATION_UNKNOWN)return;
                if(horizonSensor==null) {latestHorizonDegrees=degrees;if(horizonEnabled && !photoMode)updateHorizon(degrees);}
                if(orientationLocked || recording || busy || photoPending)return;
                // A 15-degree hysteresis beyond quadrant boundaries prevents sensor jitter.
                int delta=((degrees-deviceDegrees+540)%360)-180;
                if(Math.abs(delta)<60)return;
                deviceDegrees=((degrees+45)/90*90)%360;
                updateOrientation();
            }
        };
        horizonSensors=(android.hardware.SensorManager)getSystemService(SENSOR_SERVICE);
        horizonSensor=horizonSensors.getDefaultSensor(android.hardware.Sensor.TYPE_GAME_ROTATION_VECTOR);
        if(checkSelfPermission(android.Manifest.permission.CAMERA)!=0)requestPermissions(new String[]{android.Manifest.permission.CAMERA},1);
    }
    private void updateHorizon(float degrees) {
        horizonDegrees=horizonFilter.update(degrees,SystemClock.elapsedRealtimeNanos());
        if(horizonRecorder!=null)horizonRecorder.setAngle(horizonBase-horizonDegrees);
        transformPreview();
    }
    private boolean locked() {if(recording || busy || photoPending || countdownActive) {notice("Espera o detén la captura para cambiar");return true;}return false;}
    private void setTargetZoom(float value) {
        if(busy || photoPending || camera==null)return;
        targetZoom=Math.max(1f,Math.min(maxZoom,value));handler.removeCallbacks(smoothZoom);handler.post(smoothZoom);
    }
    private void chooseLens() {
        if(locked())return;
        ArrayList<String> ids=new ArrayList<>(Arrays.asList("56","54"));ArrayList<String> names=new ArrayList<>(Arrays.asList("W · 1×","ST · 10×"));
        try {for(String id:manager.getCameraIdList())if(Integer.valueOf(CameraCharacteristics.LENS_FACING_FRONT).equals(manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING))) {ids.add(id);names.add("Frontal");break;}}catch(Exception e) {report("FRONT DISCOVERY "+e);}
        new android.app.AlertDialog.Builder(this).setTitle("Lente").setSingleChoiceItems(names.toArray(new String[0]),ids.indexOf(cameraId),(dialog,index) -> {
            dialog.dismiss();sessionZooms.put(cameraId,zoom);cameraId=ids.get(index);exposureCompensation=0;zoom=targetZoom=sessionZooms.getOrDefault(cameraId,1f);getPreferences(0).edit().putString("lens",cameraId).apply();openCamera();
        }).show();
    }
    private void showTools() {
        if(locked())return;
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(16),dp(12),dp(16),dp(16));
        LinearLayout heading=row();TextView title=label("Ajustes",20,WHITE);title.setTypeface(null,Typeface.BOLD);heading.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));title.setGravity(Gravity.CENTER_VERTICAL);
        Button close=button("×",0x0020272e);close.setTextSize(26);close.setContentDescription("Cerrar ajustes");heading.addView(close,new LinearLayout.LayoutParams(dp(48),dp(48)));panel.addView(heading);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.addView(panel);
        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setView(scroll).create();close.setOnClickListener(v -> dialog.dismiss());
        String[] labels={audioEnabled ? "Micrófono encendido" : "Sin audio","Temporizador","Cuadrícula"};
        for(int index=0;index<labels.length;index++) {
            final int action=index;LinearLayout item=row();item.setPadding(dp(12),dp(4),dp(12),dp(4));
            android.content.res.ColorStateList ripple=android.content.res.ColorStateList.valueOf(0x22a7f3d0);
            item.setBackground(new android.graphics.drawable.RippleDrawable(ripple,shape(0xff20272e,16),shape(Color.WHITE,16)));item.setClickable(true);item.setFocusable(true);
            ImageView icon=new ImageView(this);icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            if(index<3)icon.setImageDrawable(new SettingsIcon(index==0 ? (audioEnabled ? 1 : 2) : index==1 ? 3 : 4,index==0 && !audioEnabled ? 0xffffa8a8 : ACCENT));
            LinearLayout.LayoutParams iconSlot=new LinearLayout.LayoutParams(dp(32),dp(32));iconSlot.rightMargin=dp(12);item.addView(icon,iconSlot);
            TextView text=label(labels[index],15,WHITE);item.addView(text,new LinearLayout.LayoutParams(0,-2,1));
            TextView value=label(index==1 ? (photoDelay==0 ? "No" : photoDelay+" s") : index==2 ? "›" : "",13,ACCENT);value.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);item.addView(value,new LinearLayout.LayoutParams(dp(38),-2));
            LinearLayout.LayoutParams itemSlot=new LinearLayout.LayoutParams(-1,dp(60));itemSlot.topMargin=dp(8);panel.addView(item,itemSlot);
            item.setOnClickListener(v -> {
                if(action==0) {audioEnabled=!audioEnabled;text.setText(audioEnabled ? "Micrófono encendido" : "Sin audio");icon.setImageDrawable(new SettingsIcon(audioEnabled ? 1 : 2,audioEnabled ? ACCENT : 0xffffa8a8));getPreferences(0).edit().putBoolean("audio",audioEnabled).apply();updateStorage();}
                else if(action==1) {new android.app.AlertDialog.Builder(this).setTitle("Temporizador").setSingleChoiceItems(new String[]{"Sin espera","3 segundos","10 segundos"},photoDelay==0 ? 0 : photoDelay==3 ? 1 : 2,(d,i) -> {photoDelay=new int[]{0,3,10}[i];getPreferences(0).edit().putInt("delay",photoDelay).apply();value.setText(photoDelay==0 ? "No" : photoDelay+" s");updatePhotoCountdown();d.dismiss();}).show();}
                else if(action==2) {dialog.dismiss();chooseGrid();}
                focusOverlay.invalidate();
            });
        }
        dialog.show();Window window=dialog.getWindow();if(window!=null) {window.setBackgroundDrawable(shape(0xff141a20,24));window.setLayout(Math.min(dp(380),getResources().getDisplayMetrics().widthPixels-dp(32)),Math.min(dp(280),getResources().getDisplayMetrics().heightPixels-insetTop-insetBottom-dp(32)));}
    }
    private void resetFocusAndLight() {
        if(busy || photoPending || countdownActive)return;
        exposureCompensation=0;restoreFocus();applyZoom();
        Toast.makeText(this,"Enfoque automático · exposición 0 EV",Toast.LENGTH_SHORT).show();
    }
    private void chooseGrid() {
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,GRID_NAMES) {
            @Override public View getView(int position,View recycled,android.view.ViewGroup parent) {
                LinearLayout item=row();item.setPadding(dp(20),dp(10),dp(20),dp(10));
                ImageView preview=new ImageView(MainActivity.this);preview.setImageDrawable(new GridThumbnail(position));item.addView(preview,new LinearLayout.LayoutParams(dp(62),dp(42)));
                TextView title=label(GRID_NAMES[position],15,position==gridMode ? ACCENT : WHITE);title.setPadding(dp(16),0,0,0);item.addView(title,new LinearLayout.LayoutParams(0,-2,1));
                if(position==gridMode)item.addView(label("✓",18,ACCENT));return item;
            }
        };
        new android.app.AlertDialog.Builder(this).setTitle("Guías de composición").setAdapter(adapter,(dialog,index) -> {gridMode=index;gridEnabled=index!=0;getPreferences(0).edit().putInt("grid_mode",index).putBoolean("grid",gridEnabled).apply();focusOverlay.invalidate();}).setNegativeButton("Cerrar",null).show();
    }
    private static void drawGrid(Canvas canvas,Paint paint,int mode,float w,float h) {
        paint.setStyle(Paint.Style.STROKE);
        if(mode==1 || mode==2 || mode==5) {
            float[] positions=mode==2 ? new float[]{.381966f,.618034f} : mode==5 ? new float[]{.25f,.5f,.75f} : new float[]{1f/3,2f/3};
            for(float fraction:positions) {canvas.drawLine(w*fraction,0,w*fraction,h,paint);canvas.drawLine(0,h*fraction,w,h*fraction,paint);}
        }else if(mode==3) {canvas.drawLine(0,0,w,h,paint);canvas.drawLine(w,0,0,h,paint);}
        else if(mode==4) {canvas.drawLine(w/2,0,w/2,h,paint);canvas.drawLine(0,h/2,w,h/2,paint);canvas.drawCircle(w/2,h/2,Math.min(w,h)*.035f,paint);}
    }
    private final class GridThumbnail extends android.graphics.drawable.Drawable {
        private final int mode;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        GridThumbnail(int mode) {this.mode=mode;}
        @Override public void draw(Canvas canvas) {Rect b=getBounds();canvas.save();canvas.translate(b.left+dp(2),b.top+dp(2));float w=b.width()-dp(4),h=b.height()-dp(4);paint.setColor(0xff9caebc);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));canvas.drawRoundRect(0,0,w,h,dp(3),dp(3),paint);paint.setColor(ACCENT);drawGrid(canvas,paint,mode,w,h);canvas.restore();}
        @Override public void setAlpha(int alpha) {paint.setAlpha(alpha);invalidateSelf();}
        @Override public void setColorFilter(ColorFilter filter) {paint.setColorFilter(filter);invalidateSelf();}
        @Override public int getOpacity() {return PixelFormat.TRANSLUCENT;}
    }
    private void adjustExposure(float displacement) {
        if(busy || session==null || repeatingRequest==null)return;
        try {
            CameraCharacteristics c=manager.getCameraCharacteristics(cameraId);Range<Integer> range=c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
            if(range==null || range.getUpper().equals(range.getLower()))return;
            exposureCompensation=range.clamp(exposureAtTouch+Math.round(displacement/dp(24)));
            exposureLocked=false;repeatingRequest.set(CaptureRequest.CONTROL_AE_LOCK,false);repeatingRequest.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,exposureCompensation);
            session.setRepeatingRequest(repeatingRequest.build(),meteringCallback,handler);
            float ev=exposureCompensation*c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP).floatValue();
            focusOverlay.show(viewfinder.getWidth()/2f,viewfinder.getHeight()/2f,0xffffd27a,String.format(Locale.US,"☀ %+.1f",ev));
        }catch(Exception e) {report("EXPOSURE "+e);}
    }
    private void captureAction() {
        if(countdownActive) {countdownSequence++;countdownActive=false;updatePhotoCountdown();updateControls();return;}
        if(recording) {stopRecording(true);return;}
        if(!photoMode) {startRecording();return;}
        if(busy || photoPending || camera==null)return;
        if(photoDelay==0) {takePhoto();return;}
        countdownActive=true;updateControls();updatePhotoCountdown();final int sequence=++countdownSequence;final long end=SystemClock.elapsedRealtime()+photoDelay*1000L;
        handler.post(new Runnable() {public void run() {
            if(!resumed || sequence!=countdownSequence)return;
            long left=Math.max(0,end-SystemClock.elapsedRealtime());
            if(left<=0) {countdownActive=false;updatePhotoCountdown();takePhoto();return;}
            // Derive the animation from the deadline so delayed frames cannot drift.
            long seconds=(left+999)/1000;
            float phase=(1000-((left-1)%1000+1))/1000f;
            countdownDepth.show(seconds,phase);
            handler.postDelayed(this,Math.min(33,left));
        }});
    }
    private void updatePhotoCountdown() {
        if(photoCountdown==null)return;
        boolean visible=photoMode && photoDelay>0;
        photoCountdown.setVisibility(visible ? View.VISIBLE : View.GONE);
        photoCountdown.setText(photoDelay+" s");
        if(countdownDepth!=null && (!countdownActive || !visible))countdownDepth.hide();
    }
    /** Independent overlay: the selected delay label never changes or flashes. */
    private final class CountdownDepthView extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int[] labelLocation=new int[2],overlayLocation=new int[2];
        private String number;
        private float phase;
        CountdownDepthView() {
            super(MainActivity.this);setVisibility(View.GONE);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            paint.setTypeface(Typeface.create("sans-serif-black",Typeface.BOLD));paint.setTextAlign(Paint.Align.CENTER);
        }
        void show(long seconds,float progress) {number=Long.toString(seconds);phase=progress;setVisibility(View.VISIBLE);invalidate();}
        void hide() {number=null;setVisibility(View.GONE);}
        @Override protected void onDraw(Canvas canvas) {
            if(number==null || photoCountdown==null)return;
            photoCountdown.getLocationInWindow(labelLocation);getLocationInWindow(overlayLocation);
            float targetX=labelLocation[0]-overlayLocation[0]+photoCountdown.getWidth()/2f;
            float targetY=labelLocation[1]-overlayLocation[1]+photoCountdown.getHeight()+dp(18);
            // Perspective projection: moving away in depth makes the large foreground
            // numeral shrink rapidly, then settle at its small destination.
            float travel=Math.min(1f,phase/.86f);
            float depth=travel*travel*(3f-2f*travel);
            float finalSize=dp(28),nearSize=Math.min(getWidth()*.85f,getHeight()*.65f);
            float size=finalSize/(finalSize/nearSize+(1f-finalSize/nearSize)*depth);
            float distance=1f-(size-finalSize)/(nearSize-finalSize);
            float x=getWidth()/2f+(targetX-getWidth()/2f)*distance;
            float y=getHeight()/2f+(targetY-getHeight()/2f)*distance;
            // Strobe affects only the numeral, with a short bright pulse each cycle.
            float pulse=(phase%0.18f)/0.18f;
            float light=pulse<.42f ? 1f : .12f;
            float fade=phase>.86f ? Math.max(0,(1f-phase)/.14f) : 1f;
            paint.setTextSize(size);paint.setColor(Color.WHITE);paint.setAlpha(Math.round(255*light*fade));
            paint.setShadowLayer(dp(3)+dp(9)*light,0,0,0xddffffff);
            canvas.drawText(number,x,y-(paint.ascent()+paint.descent())/2f,paint);
        }
    }
    @Override public boolean onKeyDown(int key,android.view.KeyEvent event) {
        if(key==android.view.KeyEvent.KEYCODE_VOLUME_UP || key==android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {if(event.getRepeatCount()==0)captureAction();return true;}return super.onKeyDown(key,event);
    }
    @Override public boolean onKeyUp(int key,android.view.KeyEvent event) {
        if(key==android.view.KeyEvent.KEYCODE_VOLUME_UP || key==android.view.KeyEvent.KEYCODE_VOLUME_DOWN)return true;return super.onKeyUp(key,event);
    }
    private void updateControls() {
        updatePhotoCountdown();
        if(!recording)recordingUiHidden=false;
        updateRecordingUi();
        shutter.setText("");shutter.setForeground(null);shutter.setBackground(new ShutterDrawable(photoMode,recording));shutter.setElevation(0);
        timer.setVisibility(photoMode ? View.GONE : View.VISIBLE);
        shutter.setContentDescription(recording ? "Detener grabación" : photoMode ? "Tomar fotografía" : "Grabar video");mode.setText(recording ? "" : photoMode ? "Video" : "Foto");
        mode.setForeground(recording ? new CaptureIcon(recordingClock.isPaused() ? 4 : 3,WHITE) : null);
        mode.setContentDescription(recording ? (recordingClock.isPaused() ? "Continuar grabación" : "Pausar grabación") : "Alternar foto y video");
        mode.setEnabled(!busy && !photoPending && !countdownActive);lens.setEnabled(!recording && !busy && !photoPending && !countdownActive);format.setEnabled(!recording && !busy && !photoPending && !countdownActive);timer.setTextColor(recording ? 0xffff8790 : WHITE);
        fpsButton.setText(selectedFps+" fps");fpsButton.setEnabled(!recording && !busy && !photoPending && !photoMode);
        megapixelsButton.setEnabled(!recording && !busy && !photoPending);
        megapixelsButton.setText(raw50Selected() ? "50 MP (RAW)" : selectedPhotoSize==null ? "MP" : megapixelLabel(selectedPhotoSize));
        megapixelsButton.setTextSize(raw50Selected() ? 11 : 13);
        stabilizationButton.setText(!stabilizationEnabled ? "Estab. OFF" : opticalOnly ? "Estab. OIS" : "Estab. ON");stabilizationButton.setEnabled(!recording && !busy && !photoPending);
        focusButton.setText("");focusButton.setEnabled(!busy && !photoPending && !recording);lens.setText(cameraId.equals("56") ? "W · 1×" : cameraId.equals("54") ? "ST · 10×" : "↻ Frontal");
        resetFocusButton.setEnabled(!busy && !photoPending && !countdownActive);
    }
    private long freeBytes() {return new StatFs(Environment.getExternalStorageDirectory().getAbsolutePath()).getAvailableBytes();}
    private void updateStorage() {long free=freeBytes();estimateMinutes=Math.max(0,free-RESERVE)*8/((selectedFps==60 ? 20000000L : 12000000L)+(audioEnabled ? 128000 : 0))/60;storage.setText(String.format(Locale.US,"%.1f GB · ≈ %d min",free/1e9,estimateMinutes));}
    static String elapsedLabel(long seconds) {return seconds>=3600 ? String.format(Locale.US,"%02d:%02d:%02d",seconds/3600,(seconds/60)%60,seconds%60) : String.format(Locale.US,"%02d:%02d",seconds/60,seconds%60);}
    private final Runnable clock=new Runnable() {public void run() {
        if(recording) {String elapsed=RecordingClock.format(recordedMillis());timer.setText(elapsed);updateHideUiLabel();long now=SystemClock.elapsedRealtime();if(now-lastStorageCheck>=1000) {lastStorageCheck=now;if(freeBytes()<RESERVE) {notice("Poco espacio: finalizando video");stopRecording(true);}}}
        if(resumed)handler.postDelayed(this,recording && !recordingClock.isPaused() ? 33 : 250);
    }};
    private void layoutPreview() {
        int w=viewfinder.getWidth(),h=viewfinder.getHeight();if(w==0 || h==0)return;
        int pw=w,ph=h;FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)texture.getLayoutParams();
        if(p.width!=pw || p.height!=ph) {p.width=pw;p.height=ph;p.gravity=Gravity.CENTER;texture.setLayoutParams(p);}texture.post(this::transformPreview);
    }
    private void transformPreview() {
        int w=texture.getWidth(),h=texture.getHeight();if(w==0 || h==0)return;
        // CameraService already rotates the SurfaceTexture buffer into the sensor's
        // upright orientation. Undo its default stretch, then rotate for the grip.
        int sensor=90;try {sensor=manager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.SENSOR_ORIENTATION);}catch(Exception ignored) {}
        int display=displayDegrees();
        // Counter-rotate the preview against the physical phone roll.
        float rotation=horizonEnabled && !photoMode ? horizonDegrees : (360-display)%360;
        float bw=sensor%180==0 ? 1920 : 1080,bh=sensor%180==0 ? 1080 : 1920;
        double angle=Math.toRadians(rotation);
        float cosine=(float)Math.abs(Math.cos(angle)),sine=(float)Math.abs(Math.sin(angle));
        // Inverse-rotate the viewport to determine the uniform cover scale.
        // Sensor updates are frozen during recording, preserving orientation and scale.
        float scale=horizonEnabled && !photoMode
            ? 2.04f*Math.max(w/(display%180==0 ? bw : bh),h/(display%180==0 ? bh : bw))
            : Math.max((w*cosine+h*sine)/bw,(w*sine+h*cosine)/bh);
        float[] source={0,0,w,0,0,h},target=new float[6];
        float[] buffer={-bw/2,-bh/2,bw/2,-bh/2,-bw/2,bh/2};
        for(int i=0;i<3;i++) {float x=buffer[i*2],y=buffer[i*2+1];target[i*2]=w/2f+scale*(float)(x*Math.cos(angle)-y*Math.sin(angle));target[i*2+1]=h/2f+scale*(float)(x*Math.sin(angle)+y*Math.cos(angle));}
        Matrix m=new Matrix();m.setPolyToPoly(source,0,target,0,3);texture.setTransform(m);
    }
    private void updateOrientation() {
        if(orientationLocked)return;portrait=displayDegrees()%180==0;
        format.setText((formatMode==0 ? "Auto · "+(portrait ? "9:16" : "16:9") : formatMode==1 ? "Archivo 9:16" : "Archivo 16:9")+(horizonEnabled ? " · H" : ""));layoutPreview();
    }
    private int displayDegrees() {return getWindowManager().getDefaultDisplay().getRotation()*90;}
    private void applyFormatPolicy() {
        if(orientationLocked)return;
        // Manual formats never follow sensor rotation, including reverse orientations.
        // Physical movement remains in the camera frames rather than being counter-rotated.
        setRequestedOrientation(formatMode==1 ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            : formatMode==2 ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            : ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
    }
    private void chooseRotation() {
        if(locked())return;
        new android.app.AlertDialog.Builder(this).setTitle("Orientación de la imagen")
            .setSingleChoiceItems(new String[]{"Giro natural · modo actual","Horizonte fijo · video experimental"},horizonEnabled ? 1 : 0,(dialog,index) -> {
                dialog.dismiss();horizonEnabled=index==1;horizonFilter.reset();
                if(horizonEnabled)updateHorizon(latestHorizonDegrees);
                getPreferences(0).edit().putBoolean("horizon",horizonEnabled).apply();
                updateOrientation();createSession(false);
                notice(horizonEnabled ? "Horizonte fijo: recorte adicional 2,04× · solo video" : "Giro natural");
            }).setNegativeButton("Cerrar",null).show();
    }
    private void chooseStabilization() {
        if(locked())return;
        new android.app.AlertDialog.Builder(this).setTitle("Estabilización")
            .setSingleChoiceItems(new String[]{"Estabilización automática","Sin estabilización","Solo óptica (si disponible)"},opticalOnly ? 2 : stabilizationEnabled ? 0 : 1,(dialog,index) -> {
                dialog.dismiss();opticalOnly=index==2;stabilizationEnabled=index!=1;
                getPreferences(0).edit().putBoolean("optical_only",opticalOnly).putBoolean("stabilization",stabilizationEnabled).apply();
                applyFormatPolicy();updateControls();createSession(false);
            }).setNegativeButton("Cerrar",null).show();
    }
    private void adaptLayout() {
        if(rootLayout==null)return;
        for(View block:uiBlocks)detach(block);
        detach(storage);detach(infoButton);detach(format);detach(viewfinder);detach(rootLayout);
        if(hideUiButton!=null)detach(hideUiButton);
        detach(portraitHeader);detach(portraitFooter);portraitHeader.removeAllViews();portraitFooter.removeAllViews();
        rootLayout.removeView(landscapeBody);rootLayout.removeView(landscapeToolbar);landscapeBody.removeAllViews();landscapeToolbar.removeAllViews();
        boolean horizontal=getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE;
        portrait=!horizontal;
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);getWindow().setNavigationBarColor(Color.TRANSPARENT);
        viewfinder.setBackground(null);viewfinder.setClipToOutline(false);
        LinearLayout controls=(LinearLayout)uiBlocks[uiBlocks.length-1];
        controls.setOrientation(horizontal ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        controls.setPadding(dp(horizontal ? 10 : 0),dp(horizontal ? 4 : 16),0,dp(4));
        for(int i=0;i<controls.getChildCount();i++) {
            View child=controls.getChildAt(i);LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)child.getLayoutParams();
            p.width=dp(child==shutter ? (horizontal ? 80 : 84) : child==gallery ? (horizontal ? 52 : 58) : (horizontal ? 64 : 70));
            p.height=dp(child==shutter ? (horizontal ? 80 : 84) : (horizontal ? 48 : 58));
            p.setMargins(dp(horizontal ? 0 : 5),dp(horizontal ? 7 : 0),dp(horizontal ? 0 : 5),dp(horizontal ? 7 : 0));child.setLayoutParams(p);
        }
        rootLayout.setOrientation(LinearLayout.VERTICAL);appTitle.setVisibility(View.GONE);
        timer.setTextSize(horizontal ? 16 : 20);timer.setGravity(Gravity.CENTER);
        storage.setGravity(Gravity.CENTER);storage.setTextColor(WHITE);
        timer.setShadowLayer(dp(3),0,dp(1),Color.BLACK);storage.setShadowLayer(dp(3),0,dp(1),Color.BLACK);
        message.setTextSize(horizontal ? 11 : 12);message.setMaxLines(2);
        message.setTextColor(WHITE);message.setShadowLayer(dp(3),0,dp(1),Color.BLACK);
        timer.setBackground(null);
        timer.setPadding(dp(horizontal ? 14 : 0),0,dp(horizontal ? 14 : 0),0);
        message.setBackground(null);
        message.setPadding(dp(horizontal ? 12 : 0),dp(horizontal ? 6 : 0),dp(horizontal ? 12 : 0),dp(horizontal ? 6 : 0));
        for(Button chip:new Button[]{format,gallery,mode,lens,megapixelsButton,fpsButton,stabilizationButton,focusButton,resetFocusButton}) {
            chip.setBackground(shape(0x50171d23,28));chip.setElevation(0);
        }
        infoButton.setElevation(0);
        contentRoot.addView(viewfinder,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout headerActions=row();headerActions.addView(infoButton,new LinearLayout.LayoutParams(dp(48),dp(48)));
        Space gap=new Space(this);headerActions.addView(gap,new LinearLayout.LayoutParams(0,1,1));headerActions.addView(format,new LinearLayout.LayoutParams(dp(100),dp(44)));
        portraitHeader.addView(headerActions,new LinearLayout.LayoutParams(-1,dp(48)));
        portraitHeader.addView(storage,new LinearLayout.LayoutParams(-1,dp(24)));
        portraitHeader.addView(timer,new LinearLayout.LayoutParams(-1,dp(34)));
        portraitHeader.addView(photoCountdown,new LinearLayout.LayoutParams(-1,dp(horizontal ? 30 : 36)));
        contentRoot.addView(portraitHeader,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));
        portraitFooter.addView(message,new LinearLayout.LayoutParams(-1,dp(32)));
        if(horizontal) {
            landscapeToolbar.addView(uiBlocks[3],new LinearLayout.LayoutParams(dp(230),dp(44)));
            landscapeToolbar.addView(uiBlocks[4],new LinearLayout.LayoutParams(dp(322),dp(44)));
            landscapeToolbar.addView(uiBlocks[5],new LinearLayout.LayoutParams(0,dp(44),1));
            portraitFooter.addView(landscapeToolbar,new LinearLayout.LayoutParams(-1,dp(52)));
            FrameLayout.LayoutParams shutterRail=new FrameLayout.LayoutParams(dp(100),dp(235),Gravity.CENTER_VERTICAL|Gravity.RIGHT);shutterRail.rightMargin=dp(12)+insetRight;contentRoot.addView(controls,shutterRail);
        }else {
            for(int i=3;i<uiBlocks.length;i++) {
                LinearLayout.LayoutParams rowParams=new LinearLayout.LayoutParams(-1,-2);
                if(i==3)rowParams.bottomMargin=dp(14);
                portraitFooter.addView(uiBlocks[i],rowParams);
            }
        }
        contentRoot.addView(portraitFooter,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        if(hideUiButton==null) {
            hideUiButton=button("Ocultar UI",0x70171d23);hideUiButton.setTextSize(12);
            hideUiButton.setGravity(Gravity.CENTER);hideUiButton.setAllCaps(false);hideUiButton.setIncludeFontPadding(false);
            hideUiButton.setOnClickListener(v -> {if(!recording)return;recordingUiHidden=!recordingUiHidden;updateRecordingUi();});
        }
        FrameLayout.LayoutParams hideParams=new FrameLayout.LayoutParams(dp(104),dp(44),Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        hideParams.topMargin=dp(12)+insetTop;contentRoot.addView(hideUiButton,hideParams);
        updateRecordingUi();applyInsets();updateOrientation();viewfinder.post(this::layoutPreview);
    }
    private void updateRecordingUi() {
        if(focusOverlay!=null)focusOverlay.invalidate();
        if(portraitHeader==null || portraitFooter==null)return;
        int visibility=recording && recordingUiHidden ? View.INVISIBLE : View.VISIBLE;
        portraitHeader.setVisibility(visibility);portraitFooter.setVisibility(visibility);
        if(uiBlocks!=null)uiBlocks[uiBlocks.length-1].setVisibility(visibility);
        if(hideUiButton!=null) {
            hideUiButton.setVisibility(recording ? View.VISIBLE : View.GONE);
            updateHideUiLabel();
            FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)hideUiButton.getLayoutParams();
            p.height=dp(recordingUiHidden ? 66 : 44);hideUiButton.setLayoutParams(p);
            hideUiButton.setContentDescription(recordingUiHidden ? "Mostrar controles para detener grabación" : "Ocultar controles de grabación");
        }
    }
    private void updateHideUiLabel() {
        if(hideUiButton==null)return;
        if(!recordingUiHidden) {hideUiButton.setText("Ocultar UI");return;}
        String title="Mostrar UI\n",elapsed=RecordingClock.format(recordedMillis());
        android.text.SpannableString text=new android.text.SpannableString(title+elapsed);
        text.setSpan(new android.text.style.ForegroundColorSpan(0xffff8790),title.length(),text.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new android.text.style.RelativeSizeSpan(0.9f),title.length(),text.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new android.text.style.TypefaceSpan("monospace"),title.length(),text.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        hideUiButton.setText(text);
    }
    private void detach(View view) {android.view.ViewParent parent=view.getParent();if(parent instanceof android.view.ViewGroup)((android.view.ViewGroup)parent).removeView(view);}
    private void applyInsets() {
        rootLayout.setPadding(dp(12)+insetLeft,dp(10)+insetTop,dp(12)+insetRight,dp(12)+insetBottom);
        portraitHeader.setPadding(dp(16)+insetLeft,dp(10)+insetTop,dp(16)+insetRight,0);
        portraitFooter.setPadding(dp(16)+insetLeft,0,dp(16)+insetRight,dp(12)+insetBottom);
        if(hideUiButton!=null && hideUiButton.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)hideUiButton.getLayoutParams();p.topMargin=dp(12)+insetTop;hideUiButton.setLayoutParams(p);
        }
        if(hideUiButton!=null)hideUiButton.setTranslationX((insetLeft-insetRight)/2f);
    }
    private void showInfo() {
        String version="";try {version=getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception ignored) {}
        new android.app.AlertDialog.Builder(this).setTitle("Raw4All · "+version)
            .setMessage("MATH-AI\nMathias Tapia G.\n\nAsistencia de investigación y desarrollo: OpenAI Codex.\n\nCámara experimental W/ST, sin root, probada en S23 Ultra. Proyecto independiente, sin afiliación ni respaldo oficial de Samsung; valoramos sus tecnologías.\n\nCapturas locales en DCIM/Raw4All. La auditoría completa de seguridad está pendiente.\n\nToca la imagen para enfocar. Mantén pulsado para bloquear enfoque y exposición; AF Auto restablece el modo continuo.\n\nVista previa a pantalla completa, con recorte central proporcional. Mantén pulsado el formato para elegir orientación automática o fija.")
            .setPositiveButton("Entendido",null).show();
    }
    @Override public void onConfigurationChanged(Configuration config) {super.onConfigurationChanged(config);adaptLayout();}
    private void updateZoomLabel() {zoomLabel.setText(String.format(Locale.US,"%.1f×",zoom*(cameraId.equals("54") ? 10 : 1)));}
    private void setZoom(CaptureRequest.Builder request) throws CameraAccessException {
        CameraCharacteristics c=manager.getCameraCharacteristics(cameraId);Rect active=c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        Float maximum=c.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);float actual=Math.min(zoom,maximum==null ? 1f : maximum);
        int width=Math.round(active.width()/actual),height=Math.round(active.height()/actual);
        request.set(CaptureRequest.SCALER_CROP_REGION,new Rect(active.centerX()-width/2,active.centerY()-height/2,active.centerX()+width/2,active.centerY()+height/2));
    }
    private void applyZoom() {if(session==null || repeatingRequest==null || busy)return;try {setZoom(repeatingRequest);repeatingRequest.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,exposureCompensation);session.setRepeatingRequest(repeatingRequest.build(),meteringCallback,handler);}catch(Exception e) {report("ZOOM FAILED "+e);}}
    private void focusAt(float x,float y,boolean hold) {
        if(camera==null || session==null || repeatingRequest==null || busy || photoPending)return;
        try {
            CameraCharacteristics c=manager.getCameraCharacteristics(cameraId);
            Rect active=c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);if(active==null)return;
            Matrix inverse=new Matrix();if(!texture.getTransform(new Matrix()).invert(inverse))return;
            float[] point={x,y};inverse.mapPoints(point);
            float u=point[0]/texture.getWidth(),v=point[1]/texture.getHeight();
            if(u<0 || u>1 || v<0 || v>1)return;
            int sensor=c.get(CameraCharacteristics.SENSOR_ORIENTATION);float nativeU=u,nativeV=v;
            if(sensor==90) {nativeU=v;nativeV=1-u;}else if(sensor==180) {nativeU=1-u;nativeV=1-v;}else if(sensor==270) {nativeU=1-v;nativeV=u;}
            Rect crop=lastSensorCrop!=null ? new Rect(lastSensorCrop) : repeatingRequest.get(CaptureRequest.SCALER_CROP_REGION);
            if(crop==null)crop=new Rect(active);
            // 16:9 streams use a centered portion of the sensor crop, which can be 4:3.
            if(crop.width()/(float)crop.height()>16f/9f) {int width=Math.round(crop.height()*16f/9f);crop=new Rect(crop.centerX()-width/2,crop.top,crop.centerX()+width/2,crop.bottom);}
            else {int height=Math.round(crop.width()*9f/16f);crop=new Rect(crop.left,crop.centerY()-height/2,crop.right,crop.centerY()+height/2);}
            int size=Math.max(16,Math.min(crop.width(),crop.height())/8);
            int left=Math.max(crop.left,Math.min(crop.right-size,Math.round(crop.left+nativeU*crop.width())-size/2));
            int top=Math.max(crop.top,Math.min(crop.bottom-size,Math.round(crop.top+nativeV*crop.height())-size/2));
            MeteringRectangle region=new MeteringRectangle(new Rect(left,top,left+size,top+size),MeteringRectangle.METERING_WEIGHT_MAX);
            Integer afCount=c.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF),aeCount=c.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE);
            boolean auto=supports(c.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES),CaptureRequest.CONTROL_AF_MODE_AUTO);
            if(!auto && (aeCount==null || aeCount==0)) {notice("Esta cámara no admite enfoque puntual");return;}
            focusSequence++;final int sequence=focusSequence;focusPending=true;focusLocked=hold;focusHasAuto=auto;exposureLocked=false;lockedFocusDistance=null;
            focusRegions=afCount!=null && afCount>0 ? new MeteringRectangle[]{region} : null;
            exposureRegions=aeCount!=null && aeCount>0 ? new MeteringRectangle[]{region} : null;
            repeatingRequest.set(CaptureRequest.CONTROL_AE_LOCK,false);
            if(auto) {repeatingRequest.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_AUTO);repeatingRequest.set(CaptureRequest.CONTROL_AF_TRIGGER,CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);session.capture(repeatingRequest.build(),null,handler);}
            repeatingRequest.set(CaptureRequest.CONTROL_AF_REGIONS,focusRegions);repeatingRequest.set(CaptureRequest.CONTROL_AE_REGIONS,exposureRegions);
            repeatingRequest.set(CaptureRequest.CONTROL_AF_TRIGGER,CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
            session.setRepeatingRequest(repeatingRequest.build(),meteringCallback,handler);
            if(auto) {repeatingRequest.set(CaptureRequest.CONTROL_AF_TRIGGER,CaptureRequest.CONTROL_AF_TRIGGER_START);session.capture(repeatingRequest.build(),meteringCallback,handler);repeatingRequest.set(CaptureRequest.CONTROL_AF_TRIGGER,CaptureRequest.CONTROL_AF_TRIGGER_IDLE);}
            focusOverlay.show(x+texture.getLeft(),y+texture.getTop(),0xffffd27a,hold ? "◇" : "");updateControls();
            handler.postDelayed(() -> {if(sequence!=focusSequence)return;if(focusPending) {focusOverlay.status(0xffff8790,"Intenta de nuevo");restoreFocus();}else if(!focusLocked)restoreFocus();},6000);
        }catch(Exception e) {report("FOCUS FAILED "+e);restoreFocus();notice("No se pudo aplicar el enfoque");}
    }
    private void handleMeteringResult(TotalCaptureResult result) {
        Rect crop=result.get(CaptureResult.SCALER_CROP_REGION);if(crop!=null)lastSensorCrop=new Rect(crop);
        if(!focusPending)return;
        Integer af=result.get(CaptureResult.CONTROL_AF_STATE),ae=result.get(CaptureResult.CONTROL_AE_STATE);
        boolean focused=af!=null && af==CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED;
        boolean fixed=!focusHasAuto;
        boolean exposed=ae==null || ae==CaptureResult.CONTROL_AE_STATE_CONVERGED || ae==CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED || ae==CaptureResult.CONTROL_AE_STATE_LOCKED;
        if(af!=null && af==CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {focusPending=false;focusLocked=false;focusOverlay.status(0xffff8790,"Sin enfoque");updateControls();return;}
        if(!(focused || fixed) || (focusLocked && !exposed))return;
        focusPending=false;
        if(focusLocked) {
            lockedFocusDistance=result.get(CaptureResult.LENS_FOCUS_DISTANCE);
            try {
                if(Boolean.TRUE.equals(manager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.CONTROL_AE_LOCK_AVAILABLE))) {
                    exposureLocked=true;repeatingRequest.set(CaptureRequest.CONTROL_AE_LOCK,true);session.setRepeatingRequest(repeatingRequest.build(),meteringCallback,handler);
                }
            }catch(Exception e) {report("AE LOCK FAILED "+e);exposureLocked=false;}
        }
        focusOverlay.status(ACCENT,focusLocked ? "AF/AE · ●" : "✓");updateControls();
    }
    private void applyFocusSettings(CaptureRequest.Builder request) throws CameraAccessException {
        request.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,exposureCompensation);
        request.set(CaptureRequest.CONTROL_AF_REGIONS,focusRegions);request.set(CaptureRequest.CONTROL_AE_REGIONS,exposureRegions);
        if(exposureLocked)request.set(CaptureRequest.CONTROL_AE_LOCK,true);
        if(focusLocked) {
            int[] modes=manager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
            if(lockedFocusDistance!=null && supports(modes,CaptureRequest.CONTROL_AF_MODE_OFF)) {request.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_OFF);request.set(CaptureRequest.LENS_FOCUS_DISTANCE,lockedFocusDistance);}
            else if(supports(modes,CaptureRequest.CONTROL_AF_MODE_AUTO))request.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_AUTO);
        }
    }
    private void restoreFocus() {
        focusSequence++;focusPending=false;focusLocked=false;exposureLocked=false;focusRegions=null;exposureRegions=null;lockedFocusDistance=null;focusOverlay.hide();
        if(repeatingRequest!=null && session!=null && !busy)try {
            repeatingRequest.set(CaptureRequest.CONTROL_AF_TRIGGER,CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);session.capture(repeatingRequest.build(),null,handler);
            repeatingRequest.set(CaptureRequest.CONTROL_AF_TRIGGER,CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
            int desired=recording ? CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO : CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE;
            int[] modes=manager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
            if(supports(modes,desired))repeatingRequest.set(CaptureRequest.CONTROL_AF_MODE,desired);
            repeatingRequest.set(CaptureRequest.CONTROL_AF_REGIONS,null);repeatingRequest.set(CaptureRequest.CONTROL_AE_REGIONS,null);repeatingRequest.set(CaptureRequest.CONTROL_AE_LOCK,false);
            session.setRepeatingRequest(repeatingRequest.build(),meteringCallback,handler);
        }catch(Exception e) {report("RESTORE FOCUS FAILED "+e);}
        if(focusButton!=null)updateControls();
    }
    private final class FocusOverlay extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private float x,y;private int color;private String caption;private boolean visible;
        FocusOverlay() {super(MainActivity.this);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        void show(float px,float py,int tint,String text) {x=px;y=py;color=tint;caption=text;visible=true;invalidate();}
        void status(int tint,String text) {color=tint;caption=text;invalidate();}
        void hide() {visible=false;invalidate();}
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if(gridEnabled) {paint.setColor(0x55ffffff);paint.setStrokeWidth(dp(1));drawGrid(canvas,paint,gridMode,getWidth(),getHeight());}
            if(!photoMode) {
                boolean minimal=recording && recordingUiHidden;
                float center,baseline,step=dp(minimal ? 9 : 7),barWidth=dp(minimal ? 5 : 4);
                if(minimal) {
                    center=(getWidth()+insetLeft-insetRight)/2f;
                    baseline=getHeight()-insetBottom-dp(40);
                }else {
                    int[] anchor=new int[2],origin=new int[2];
                    message.getLocationOnScreen(anchor);getLocationOnScreen(origin);
                    center=anchor[0]-origin[0]+message.getWidth()/2f;
                    baseline=anchor[1]-origin[1]-dp(18);
                }
                float width=11*step+barWidth;
                paint.setStyle(Paint.Style.FILL);
                for(int i=0;i<12;i++) {
                    paint.setColor(!audioEnabled ? 0xff9caebc : recording && i/12f<audioLevel ? (i<8 ? ACCENT : i<11 ? 0xffffd27a : 0xffff8790) : 0x559caebc);
                    float left=center-width/2f+step*i;
                    canvas.drawRoundRect(left,baseline,left+barWidth,baseline+dp(minimal ? 8+i%3*4 : 6+i%3*3),dp(2),dp(2),paint);
                }
            }
            if(!visible)return;float radius=dp(30);
            paint.setColor(color);paint.setStrokeWidth(dp(2));paint.setStyle(Paint.Style.STROKE);
            canvas.drawRoundRect(x-radius,y-radius,x+radius,y+radius,dp(8),dp(8),paint);
            canvas.drawLine(x-dp(5),y,x+dp(5),y,paint);canvas.drawLine(x,y-dp(5),x,y+dp(5),paint);
            paint.setStyle(Paint.Style.FILL);paint.setTextSize(dp(12));paint.setTextAlign(Paint.Align.CENTER);paint.setShadowLayer(dp(2),0,dp(1),Color.BLACK);
            canvas.drawText(caption,x,Math.max(dp(18),y-radius-dp(9)),paint);paint.clearShadowLayer();
        }
    }
    private boolean supports(int[] values,int mode) {if(values!=null)for(int value:values)if(value==mode)return true;return false;}
    private boolean encoderSupports(int fps) {
        for(MediaCodecInfo info:new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
            if(!info.isEncoder() || info.isSoftwareOnly())continue;
            for(String type:info.getSupportedTypes())if(type.equalsIgnoreCase("video/avc")) {
                try {if(info.getCapabilitiesForType(type).getVideoCapabilities().areSizeAndRateSupported(1920,1080,fps))return true;}catch(Exception ignored) {}
            }
        }
        return false;
    }
    private Range<Integer> fpsRange(int fps) throws CameraAccessException {
        CameraCharacteristics c=manager.getCameraCharacteristics(cameraId);
        StreamConfigurationMap map=c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size size=new Size(1920,1080);
        if(map==null || !Arrays.asList(map.getOutputSizes(MediaRecorder.class)).contains(size)
                || !Arrays.asList(map.getOutputSizes(SurfaceTexture.class)).contains(size))return null;
        Range<Integer>[] ranges=c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
        if(ranges==null)return null;int[][] values=new int[ranges.length][2];
        for(int i=0;i<ranges.length;i++) {values[i][0]=ranges[i].getLower();values[i][1]=ranges[i].getUpper();}
        int[] chosen=FpsPolicy.selectRange(fps,values,map.getOutputMinFrameDuration(SurfaceTexture.class,size),map.getOutputMinFrameDuration(MediaRecorder.class,size),encoderSupports(fps));
        return chosen==null ? null : new Range<>(chosen[0],chosen[1]);
    }
    private void chooseFps() {
        if(locked())return;
        try {
            final boolean sixty=fpsRange(60)!=null;
            new android.app.AlertDialog.Builder(this).setTitle("Video · 1080p")
                .setSingleChoiceItems(new String[]{"30 fps",sixty ? "60 fps" : "60 fps · no disponible en esta cámara"},selectedFps==60 ? 1 : 0,(dialog,index) -> {
                    dialog.dismiss();if(index==1 && !sixty) {Toast.makeText(this,"Esta cámara no anuncia 1080p a 60 fps. Se mantienen 30 fps.",Toast.LENGTH_LONG).show();return;}
                    selectedFps=index==1 ? 60 : 30;updateStorage();getPreferences(0).edit().putInt("fps",selectedFps).apply();updateControls();createSession(false);
                }).setNegativeButton("Cerrar",null).show();
        }catch(Exception e) {notice("No se pudieron consultar los fps");report("FPS QUERY FAILED "+e);}
    }
    private void optimize(CaptureRequest.Builder request,boolean still) throws CameraAccessException {
        CameraCharacteristics c=manager.getCameraCharacteristics(cameraId);
        request.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_ON);
        request.set(CaptureRequest.CONTROL_AWB_MODE,CaptureRequest.CONTROL_AWB_MODE_AUTO);
        int[] antibanding=c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_ANTIBANDING_MODES);
        if(supports(antibanding,CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO))request.set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE,CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO);
        boolean electronic=stabilizationEnabled && !opticalOnly && !horizonEnabled && !still && supports(c.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES),CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON);
        boolean optical=supports(c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION),CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON);
        if((opticalOnly || horizonEnabled) && supports(c.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES),CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF))request.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF);
        if(!stabilizationEnabled) {
            if(supports(c.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES),CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF))request.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF);
            if(supports(c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION),CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF))request.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF);
            stabilization="Estabilización desactivada";
        }else if(electronic) {
            request.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON);
            if(optical)request.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF);
            stabilization="Estabilización electrónica";
        }else if(optical) {
            request.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF);
            request.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON);stabilization="Estabilización óptica";
        }else stabilization="Sin estabilización disponible";
        int distortion=still ? CaptureRequest.DISTORTION_CORRECTION_MODE_HIGH_QUALITY : CaptureRequest.DISTORTION_CORRECTION_MODE_FAST;
        if(supports(c.get(CameraCharacteristics.DISTORTION_CORRECTION_AVAILABLE_MODES),distortion))request.set(CaptureRequest.DISTORTION_CORRECTION_MODE,distortion);
        report("OPTIMIZE "+cameraId+" "+stabilization+" distortion="+supports(c.get(CameraCharacteristics.DISTORTION_CORRECTION_AVAILABLE_MODES),distortion));
    }
    private void maybeOpen() {if(resumed && texture.isAvailable() && camera==null && !busy && checkSelfPermission(android.Manifest.permission.CAMERA)==0)openCamera();}
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(code,permissions,results);if(results.length==0 || results[0]!=0) {notice("Permiso necesario para esta captura");return;}
        if(code==2) {maybeOpen();handler.postDelayed(() -> {if(camera!=null && !busy)startRecording();},700);}else maybeOpen();
    }
    private void openCamera() {
        if(!texture.isAvailable() || checkSelfPermission(android.Manifest.permission.CAMERA)!=0)return;
        closeCamera();busy=true;updateControls();final int token=generation;notice("Abriendo "+(cameraId.equals("54") ? "ST" : "W"));
        try {manager.openCamera(cameraId,new CameraDevice.StateCallback() {
            public void onOpened(CameraDevice d) {if(token!=generation) {d.close();return;}camera=d;report("OPENED "+d.getId());if(captureRawOnOpen) {captureRawOnOpen=false;captureRaw50();}else createSession(false);}
            public void onDisconnected(CameraDevice d) {d.close();if(token==generation || camera==d) {notice("Cámara desconectada");closeCamera();}}
            public void onError(CameraDevice d,int e) {d.close();if(token==generation || camera==d) {notice("Error de cámara "+e);closeCamera();}}
        },handler);}catch(Exception e) {notice("No se pudo abrir: "+e.getMessage());closeCamera();}
    }
    private void createSession(boolean video) {
        if(rawVendorSession) {openCamera();return;}
        if(camera==null || !texture.isAvailable())return;busy=true;updateControls();final int token=++generation;final CameraDevice device=camera;
        try {
            if(session!=null) {session.close();session=null;}if(jpegReader!=null) {jpegReader.close();jpegReader=null;}if(preview!=null)preview.release();
            CameraCharacteristics characteristics=manager.getCameraCharacteristics(cameraId);
            selectedPhotoSize=preferredPhotoSize();report("JPEG CAPABILITIES "+cameraId+" "+photoSizes());
            report("FPS CAPABILITIES "+cameraId+" ranges="+Arrays.toString(characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES))+" 1080p60="+(fpsRange(60)!=null));
            if(!photoMode && fpsRange(selectedFps)==null) {selectedFps=30;getPreferences(0).edit().putInt("fps",30).apply();Toast.makeText(this,"Esta cámara no admite los fps elegidos; usando 30 fps.",Toast.LENGTH_LONG).show();}
            Float supported=manager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
            maxZoom=photoMode && raw50Selected() ? 1f : Math.min(cameraId.equals("54") ? 2f : 10f,supported==null ? 1f : supported);zoom=targetZoom=Math.min(zoom,maxZoom);zoomSlider.setProgress(maxZoom<=1 ? 0 : Math.round((zoom-1)/(maxZoom-1)*1000));updateZoomLabel();report("ZOOM RANGE "+cameraId+" 1-"+maxZoom);
            texture.getSurfaceTexture().setDefaultBufferSize(1920,1080);preview=new Surface(texture.getSurfaceTexture());List<Surface> targets=new ArrayList<>();targets.add(preview);
            if(video)targets.add(horizonRecorder!=null ? horizonRecorder.getInputSurface() : recorder.getSurface());else if(photoMode) {
                Size chosen=selectedPhotoSize;
                jpegReader=ImageReader.newInstance(chosen.getWidth(),chosen.getHeight(),ImageFormat.JPEG,2);jpegReader.setOnImageAvailableListener(this::savePhoto,handler);targets.add(jpegReader.getSurface());
            }
            CaptureRequest.Builder request=device.createCaptureRequest(video ? CameraDevice.TEMPLATE_RECORD : CameraDevice.TEMPLATE_PREVIEW);request.addTarget(preview);if(video)request.addTarget(horizonRecorder!=null ? horizonRecorder.getInputSurface() : recorder.getSurface());
            request.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO);request.set(CaptureRequest.CONTROL_AF_MODE,video ? CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO : CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            Range<Integer> rate=photoMode && !video ? null : fpsRange(selectedFps);if(rate!=null)request.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,rate);
            setZoom(request);optimize(request,!video && photoMode);applyFocusSettings(request);repeatingRequest=request;transformPreview();
            CameraCaptureSession.StateCallback callback=new CameraCaptureSession.StateCallback() {
                public void onConfigured(CameraCaptureSession s) {
                    if(token!=generation || camera!=device) {s.close();return;}session=s;
                    try {
                        s.setRepeatingRequest(request.build(),new CameraCaptureSession.CaptureCallback() {
                            boolean first;
                            public void onCaptureCompleted(CameraCaptureSession ss,CaptureRequest r,TotalCaptureResult result) {
                                if(token==generation && ss==session)handleMeteringResult(result);
                                if(!first && token==generation) {
                                    first=true;report("FIRST FRAME ID "+device.getId()+" OIS="+result.get(CaptureResult.LENS_OPTICAL_STABILIZATION_MODE)+" EIS="+result.get(CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE)+" fps="+result.get(CaptureResult.CONTROL_AE_TARGET_FPS_RANGE));
                                }
                            }
                        },handler);busy=false;
                        if(video) {
                            recorder.start();recording=true;recordingClock.start(SystemClock.elapsedRealtime());handler.removeCallbacks(clock);handler.post(clock);if(horizonRecorder!=null)horizonRecorder.start();timer.setText("00:00.000");final int sequence=++recordingSequence;notice("Grabando · 1080p · "+selectedFps+" fps · "+(audioEnabled ? "audio" : "sin audio"));report("RECORDING "+videoUri);updateControls();
                        }else {updateOrientation();notice(photoMode ? "Foto · "+stabilization : "1080p · "+selectedFps+" fps · "+stabilization);updateControls();}
                    }catch(Exception e) {notice("Error de captura: "+e.getMessage());closeCamera();}
                }
                public void onConfigureFailed(CameraCaptureSession s) {
                    s.close();if(token!=generation)return;
                    Size fallback=new Size(1920,1080);
                    try {
                        if(photoMode && !video && !fallback.equals(selectedPhotoSize) && photoSizes().contains(fallback)) {
                            savePhotoSize(fallback);notice("Esta combinación no admite los MP elegidos; usando 2 MP");createSession(false);return;
                        }
                    }catch(Exception ignored) {}
                    notice("Configuración no compatible");closeCamera();
                }
            };
            List<OutputConfiguration> outputs=new ArrayList<>();for(Surface target:targets)outputs.add(new OutputConfiguration(target));
            SessionConfiguration configuration=new SessionConfiguration(SessionConfiguration.SESSION_REGULAR,outputs,command -> handler.post(command),callback);
            configuration.setSessionParameters(request.build());device.createCaptureSession(configuration);
        }catch(Exception e) {notice("Error de sesión: "+e.getMessage());closeCamera();}
    }
    private List<Size> photoSizes() throws CameraAccessException {
        StreamConfigurationMap map=manager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        ArrayList<Size> sizes=new ArrayList<>();Size[] normal=map.getOutputSizes(ImageFormat.JPEG);
        if(normal!=null)sizes.addAll(Arrays.asList(normal));Size[] high=map.getHighResolutionOutputSizes(ImageFormat.JPEG);
        if(high!=null)for(Size size:high)if(!sizes.contains(size))sizes.add(size);
        sizes.sort((a,b) -> Long.compare((long)a.getWidth()*a.getHeight(),(long)b.getWidth()*b.getHeight()));
        if(sizes.isEmpty())throw new IllegalStateException("Esta cámara no anuncia fotografías JPEG");return sizes;
    }
    // Samsung framework adapter used only for the tested high-resolution RAW route.
    private void setSamsungParameters(String parameters) throws Exception {
        ClassLoader loader=new dalvik.system.PathClassLoader("/system/framework/scamera_sdk_util.jar",android.util.Log.class.getClassLoader());
        Class.forName("com.samsung.android.sdk.camera.impl.internal.CustomInterfaceHelper",true,loader)
            .getMethod("setSamsungParameter",CameraDevice.class,String.class).invoke(null,camera,parameters);
    }
    private boolean supportsRaw50() {
        if(!cameraId.equals("56") || manager==null)return false;
        try {
            int[] modes=manager.getCameraCharacteristics(cameraId).get(new CameraCharacteristics.Key<int[]>("samsung.android.scaler.availableExpertRawHighresRawStreamConfigurations",int[].class));
            return modes!=null && modes.length>=5 && modes[0]==56 && modes[1]==8160 && modes[2]==6120 && modes[3]==0 && modes[4]==ImageFormat.RAW10;
        }catch(Exception e) {return false;}
    }
    private boolean raw50Selected() {return cameraId.equals("56") && getPreferences(0).getBoolean("raw50_56",false) && supportsRaw50();}
    private final class RawCapture {
        final int token=generation;
        final CameraCharacteristics characteristics;
        final int orientation;
        TotalCaptureResult result;
        byte[] pixels;
        int stride;
        long timestamp;
        boolean writing;
        RawCapture(CameraCharacteristics c,int degrees) {characteristics=c;orientation=degrees==90 ? 6 : degrees==180 ? 3 : degrees==270 ? 8 : 1;}
        void finishIfReady() {
            if(writing || result==null || pixels==null || token!=generation)return;
            Long sensorTime=result.get(CaptureResult.SENSOR_TIMESTAMP);
            if(sensorTime==null || sensorTime!=timestamp) {failRaw(token,"Los datos RAW no coinciden con la captura");return;}
            writing=true;notice("Guardando RAW de 50 MP…");
            final byte[] data=pixels;pixels=null;
            io.execute(() -> {
                Uri uri=null;
                try {
                    ContentValues values=new ContentValues();values.put(MediaStore.MediaColumns.DISPLAY_NAME,"Raw4All_"+System.currentTimeMillis()+".dng");
                    values.put(MediaStore.MediaColumns.MIME_TYPE,"image/x-adobe-dng");values.put(MediaStore.MediaColumns.RELATIVE_PATH,"DCIM/Raw4All");values.put(MediaStore.MediaColumns.IS_PENDING,1);
                    values.put(MediaStore.Images.Media.WIDTH,8160);values.put(MediaStore.Images.Media.HEIGHT,6120);
                    uri=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
                    if(uri==null)throw new IOException("No se pudo crear el DNG");
                    try(OutputStream out=new BufferedOutputStream(getContentResolver().openOutputStream(uri),128*1024)) {RawDng.write(out,data,8160,6120,stride,characteristics,result,orientation);}
                    final Uri saved=uri;
                    handler.post(() -> {
                        try {publish(saved,"image/x-adobe-dng");report("RAW50 DNG SAVED "+saved);updateStorage();coloredToast("RAW de 50 MP guardado en la galería");}
                        catch(Exception e) {removeMedia(saved);report("RAW50 PUBLISH ERROR "+e);}
                        if(token==generation && resumed)openCamera();
                    });
                }catch(Exception e) {removeMedia(uri);report("RAW50 SAVE ERROR "+e);handler.post(() -> failRaw(token,"No se pudo guardar el RAW"));}
            });
        }
    }
    private void failRaw(int token,String reason) {
        report("RAW50 FAILED "+reason);
        if(token!=generation)return;
        coloredToast(reason+". Puedes volver a JPEG.");if(resumed)openCamera();else closeCamera();
    }
    private void captureRaw50() {
        final int token=++generation;busy=true;photoPending=true;updateControls();notice("Preparando RAW · 50 MP…");
        try {
            if(!supportsRaw50())throw new IllegalStateException("50 MP RAW no disponible en esta cámara");
            CameraCharacteristics c=manager.getCameraCharacteristics(cameraId);
            final RawCapture capture=new RawCapture(c,outputOrientation());
            orientationLocked=true;setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
            rawVendorSession=true;
            setSamsungParameters("first-entrance=false;samsungcamera=true;factorytest=false;shootingmode=40;recording-fps=0;sw-vdis=false;video-beautyface=false;vtmode=0;operation_mode=none;stream_type=0;");
            report("RAW50 VENDOR mode=40");
            jpegReader=ImageReader.newInstance(8160,6120,ImageFormat.RAW10,2);
            jpegReader.setOnImageAvailableListener(reader -> {
                if(token!=generation)return;
                try(Image frame=reader.acquireNextImage()) {
                    if(frame==null)return;
                    if(frame.getWidth()!=8160 || frame.getHeight()!=6120 || frame.getFormat()!=ImageFormat.RAW10)throw new IOException("Formato RAW inesperado");
                    Image.Plane plane=frame.getPlanes()[0];capture.stride=plane.getRowStride();capture.timestamp=frame.getTimestamp();
                    capture.pixels=new byte[plane.getBuffer().remaining()];plane.getBuffer().get(capture.pixels);
                    report("RAW50 FRAME "+frame.getWidth()+"x"+frame.getHeight()+" stride="+capture.stride+" timestamp="+capture.timestamp);
                }catch(Exception e) {failRaw(token,"No se pudo recibir el RAW");return;}
                capture.finishIfReady();
            },handler);
            texture.getSurfaceTexture().setDefaultBufferSize(1920,1080);preview=new Surface(texture.getSurfaceTexture());
            CaptureRequest.Builder request=camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            request.addTarget(jpegReader.getSurface());request.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO);
            request.set(CaptureRequest.SENSOR_PIXEL_MODE,0);
            request.set(new CaptureRequest.Key<Integer>("samsung.android.control.shootingMode",Integer.class),40);
            CaptureRequest.Builder warmup=camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            warmup.addTarget(preview);warmup.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO);
            warmup.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            warmup.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,exposureCompensation);
            request.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION,exposureCompensation);
            warmup.set(new CaptureRequest.Key<Integer>("samsung.android.control.shootingMode",Integer.class),40);
            OutputConfiguration rawOutput=(OutputConfiguration)OutputConfiguration.class.getMethod("semCreateOutputConfiguration",int.class,Surface.class,int.class,int.class).invoke(null,-1,jpegReader.getSurface(),0,2);
            List<OutputConfiguration> outputs=Arrays.asList(new OutputConfiguration(preview),rawOutput);
            SessionConfiguration configuration=new SessionConfiguration(SessionConfiguration.SESSION_REGULAR,outputs,command -> handler.post(command),new CameraCaptureSession.StateCallback() {
                public void onConfigured(CameraCaptureSession s) {
                    if(token!=generation) {s.close();return;}session=s;report("RAW50 CONFIGURED");
                    try {s.setRepeatingRequest(warmup.build(),new CameraCaptureSession.CaptureCallback() {
                        int frames;boolean submitted;
                        public void onCaptureCompleted(CameraCaptureSession ss,CaptureRequest r,TotalCaptureResult result) {
                            if(++frames<30 || submitted || token!=generation)return;submitted=true;
                            try {ss.capture(request.build(),new CameraCaptureSession.CaptureCallback() {
                                public void onCaptureStarted(CameraCaptureSession s,CaptureRequest r,long timestamp,long number) {if(token==generation)photoStrobe();}
                                public void onCaptureCompleted(CameraCaptureSession s,CaptureRequest r,TotalCaptureResult result) {if(token!=generation)return;capture.result=result;capture.finishIfReady();}
                                public void onCaptureFailed(CameraCaptureSession s,CaptureRequest r,CaptureFailure failure) {failRaw(token,"El sensor rechazó la captura RAW");}
                            },handler);}catch(Exception e) {report("RAW50 REQUEST ERROR "+e);failRaw(token,"No se pudo disparar en RAW");}
                        }
                    },handler);}catch(Exception e) {report("RAW50 PREVIEW ERROR "+e);failRaw(token,"No se pudo preparar la captura RAW");}
                }
                public void onConfigureFailed(CameraCaptureSession s) {s.close();failRaw(token,"Esta configuración RAW no está disponible");}
            });
            configuration.setSessionParameters(warmup.build());camera.createCaptureSession(configuration);
            handler.postDelayed(() -> {if(token==generation && !capture.writing)failRaw(token,"La captura RAW tardó demasiado");},15000);
        }catch(Exception e) {report("RAW50 ERROR "+e);failRaw(token,"No se pudo abrir el modo RAW");}
    }
    private void inspectResolution() {
        for(String id:new String[]{"56","54"})try {
            CameraCharacteristics c=manager.getCameraCharacteristics(id);
            report("RESOLUTION PROBE "+id+" capabilities="+Arrays.toString(c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)));
            StreamConfigurationMap maximum=c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION);
            report("RESOLUTION PROBE "+id+" maximum="+(maximum==null ? "null" : Arrays.toString(maximum.getOutputSizes(ImageFormat.JPEG))));
            for(String suffix:new String[]{"availableFullPictureStreamConfigurations","availableFullTetraPictureStreamConfigurations","availableTetraPictureStreamConfigurations","availableExpertRawStreamConfigurations","availableExpertRawHighresRawStreamConfigurations","availableExpertRawHighresYuvStreamConfigurations"}) {
                try {int[] values=c.get(new CameraCharacteristics.Key<int[]>("samsung.android.scaler."+suffix,int[].class));report("RESOLUTION PROBE "+id+" "+suffix+"="+Arrays.toString(values));}
                catch(Exception e) {report("RESOLUTION PROBE "+id+" "+suffix+" unavailable: "+e.getClass().getSimpleName());}
            }
        }catch(Exception e) {report("RESOLUTION PROBE "+id+" failed "+e);}
    }
    private String megapixelLabel(Size size) {
        double mp=(long)size.getWidth()*size.getHeight()/1e6;
        for(int nominal:new int[]{12,50,100,200})if(Math.abs(mp-nominal)/nominal<0.15)return nominal+" MP";
        return String.format(Locale.US,"%.0f MP",mp);
    }
    private Size preferredPhotoSize() throws CameraAccessException {
        List<Size> sizes=photoSizes();String preference=getPreferences(0).getString("photo_size_"+cameraId,"");Size best=sizes.get(0);
        for(Size size:sizes) {
            if(size.toString().equals(preference))return size;
            if(Math.abs((long)size.getWidth()*size.getHeight()-12000000L)<Math.abs((long)best.getWidth()*best.getHeight()-12000000L))best=size;
        }
        return best;
    }
    private void savePhotoSize(Size size) {
        selectedPhotoSize=size;getPreferences(0).edit().putString("photo_size_"+cameraId,size.toString()).apply();updateControls();
    }
    private void chooseMegapixels() {
        if(locked())return;
        try {
            LinkedHashMap<String,Size> options=new LinkedHashMap<>();
            for(Size size:photoSizes())options.put(megapixelLabel(size),size);
            ArrayList<Size> sizes=new ArrayList<>(options.values());boolean rawAvailable=supportsRaw50();String[] labels=new String[sizes.size()+(rawAvailable ? 1 : 0)];int current=0;
            for(int i=0;i<sizes.size();i++) {
                Size size=sizes.get(i);labels[i]=megapixelLabel(size)+" · "+size.getWidth()+" × "+size.getHeight();
                if(size.equals(selectedPhotoSize))current=i;
            }
            if(rawAvailable) {labels[sizes.size()]="50 MP (RAW) · 8160 × 6120";if(raw50Selected())current=sizes.size();}
            new android.app.AlertDialog.Builder(this).setTitle("Foto · "+(cameraId.equals("56") ? "W" : cameraId.equals("54") ? "ST" : "Frontal"))
                .setSingleChoiceItems(labels,current,(dialog,index) -> {
                    dialog.dismiss();boolean raw=index==sizes.size();getPreferences(0).edit().putBoolean("raw50_"+cameraId,raw).apply();
                    if(!raw)savePhotoSize(sizes.get(index));else {zoom=targetZoom=1;zoomSlider.setProgress(0);updateZoomLabel();updateControls();}
                    if(photoMode)createSession(false);
                    else notice("Resolución elegida para fotos; el video sigue en 1080p");
                }).setNegativeButton("Cerrar",null).show();
        }catch(Exception e) {notice("No se pudieron consultar los megapíxeles");}
    }
    private Uri newMedia(boolean video) {
        ContentValues v=new ContentValues();v.put(MediaStore.MediaColumns.DISPLAY_NAME,"Raw4All_"+System.currentTimeMillis()+(video ? ".mp4" : ".jpg"));v.put(MediaStore.MediaColumns.MIME_TYPE,video ? "video/mp4" : "image/jpeg");
        v.put(MediaStore.MediaColumns.RELATIVE_PATH,"DCIM/Raw4All");v.put(MediaStore.MediaColumns.IS_PENDING,1);
        Uri result=getContentResolver().insert(video ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI : MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v);if(result==null)throw new IllegalStateException("No se pudo crear archivo en galería");return result;
    }
    private void publish(Uri uri,String mime) {
        ContentValues v=new ContentValues();v.put(MediaStore.MediaColumns.IS_PENDING,0);getContentResolver().update(uri,v,null,null);lastUri=uri;lastMime=mime;
        sessionCaptures.add(new SessionCapture(uri,mime));
        getPreferences(0).edit().putString("last_uri",uri.toString()).putString("last_mime",mime).apply();report("GALLERY SAVED "+uri);
    }
    private void removeMedia(Uri uri) {if(uri!=null)try {getContentResolver().delete(uri,null,null);}catch(Exception e) {report("DELETE FAILED "+e);}}
    private int outputOrientation() throws CameraAccessException {
        if(recording)return captureDegrees;
        int sensor=manager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.SENSOR_ORIENTATION);
        int target=formatMode==1 ? (displayDegrees()==180 ? 180 : 0)
            : formatMode==2 ? (displayDegrees()==270 ? 270 : 90) : displayDegrees();
        boolean front=Integer.valueOf(CameraCharacteristics.LENS_FACING_FRONT).equals(manager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.LENS_FACING));return (sensor+(front ? target : -target)+360)%360;
    }
    private void startRecording() {
        if(camera==null || busy || photoPending || recording)return;if(photoMode) {photoMode=false;createSession(false);return;}
        if(audioEnabled && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=0) {requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO},2);return;}
        if(freeBytes()<RESERVE+32L*1024*1024) {notice("No hay espacio suficiente para comenzar");return;}
        try {
            StreamConfigurationMap map=manager.getCameraCharacteristics(camera.getId()).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if(!Arrays.asList(map.getOutputSizes(MediaRecorder.class)).contains(new Size(1920,1080))) {notice("1080p no disponible en esta cámara");return;}
            if(fpsRange(selectedFps)==null) {notice("Los fps elegidos no están disponibles en 1080p");return;}
            videoUri=newMedia(true);videoFd=getContentResolver().openFileDescriptor(videoUri,"w");recorder=new MediaRecorder(this);
            if(audioEnabled)recorder.setAudioSource(MediaRecorder.AudioSource.MIC);recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);recorder.setOutputFile(videoFd.getFileDescriptor());
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);if(audioEnabled) {recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);recorder.setAudioSamplingRate(48000);recorder.setAudioEncodingBitRate(128000);}
            captureDegrees=outputOrientation();orientationLocked=true;setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
            recorder.setVideoSize(1920,1080);recorder.setVideoFrameRate(selectedFps);recorder.setVideoEncodingBitRate(selectedFps==60 ? 20000000 : 12000000);recorder.setOrientationHint(captureDegrees);recorder.setMaxFileSize(Math.max(1,freeBytes()-RESERVE));
            recorder.setOnInfoListener((r,what,extra) -> {if(what==MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {notice("Límite de almacenamiento alcanzado");stopRecording(true);}});
            recorder.setOnErrorListener((r,what,extra) -> {notice("La grabación se interrumpió");stopRecording(true);});recorder.prepare();
            if(horizonEnabled) {
                horizonBase=(360-displayDegrees())%360;horizonRecorder=new HorizonRecorder(recorder.getSurface(),() -> handler.post(() -> {notice("Error de horizonte: finalizando video");stopRecording(true);}));
                horizonRecorder.setAngle(horizonBase-horizonDegrees);
            }
            createSession(true);
        }catch(Exception e) {notice("No se pudo grabar: "+e.getMessage());closeCamera();}
    }
    private long recordedMillis() {
        return recordingClock.elapsed(SystemClock.elapsedRealtime());
    }
    private void toggleRecordingPause() {
        if(!recording || recorder==null || busy)return;
        try {
            if(recordingClock.isPaused()) {
                recorder.resume();recordingClock.resume(SystemClock.elapsedRealtime());
                notice("Grabando · 1080p · "+selectedFps+" fps · "+(audioEnabled ? "audio" : "sin audio"));report("RECORDING RESUMED pausedMs="+recordingClock.pausedMillis(SystemClock.elapsedRealtime()));
            }else {
                recorder.pause();recordingClock.pause(SystemClock.elapsedRealtime());audioLevel=0;
                notice("Grabación en pausa");report("RECORDING PAUSED durationMs="+recordedMillis());
            }
            timer.setText(RecordingClock.format(recordedMillis()));updateControls();
        }catch(IllegalStateException e) {report("PAUSE FAILED "+e);notice("No se pudo cambiar la pausa");}
    }
    private void stopRecording(boolean restart) {
        if(recorder==null)return;boolean valid=false;recordingSequence++;
        long elapsed=recording ? recordedMillis()/1000 : 0;
        try {if(session!=null) {session.stopRepeating();session.abortCaptures();}if(horizonRecorder!=null)horizonRecorder.pause();if(recording) {recorder.stop();valid=true;}}catch(Exception e) {report("STOP FAILED "+e);}
        recording=false;recordingClock.start(SystemClock.elapsedRealtime());if(horizonRecorder!=null) {horizonRecorder.close();horizonRecorder=null;}
        timer.setText("00:00.000");recorder.release();recorder=null;try {if(videoFd!=null)videoFd.close();}catch(IOException ignored) {}videoFd=null;
        report("RECORDING STOPPED duration="+elapsedLabel(elapsed));
        if(valid) {try {publish(videoUri,"video/mp4");notice("El video se guardó en la galería");coloredToast("El video se guardó en la galería");}catch(Exception e) {notice("No se pudo publicar el video en galería");report("PUBLISH FAILED "+e);}}else {removeMedia(videoUri);notice("No se pudo guardar este video");}
        videoUri=null;busy=false;orientationLocked=false;applyFormatPolicy();updateOrientation();updateControls();updateStorage();if(restart && camera!=null)createSession(false);
    }
    private void takePhoto() {
        if(camera==null || session==null || busy || photoPending || jpegReader==null)return;if(freeBytes()<RESERVE) {notice("No hay espacio suficiente");return;}
        if(raw50Selected()) {if(freeBytes()<RESERVE+110L*1024*1024) {notice("Se necesitan unos 100 MB libres para guardar el RAW");return;}captureRawOnOpen=true;openCamera();return;}
        try {
            photoPending=true;updateControls();notice("Capturando…");final int token=generation;
            CaptureRequest.Builder request=camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);request.addTarget(jpegReader.getSurface());request.set(CaptureRequest.JPEG_ORIENTATION,outputOrientation());request.set(CaptureRequest.JPEG_QUALITY,(byte)95);
            setZoom(request);optimize(request,true);applyFocusSettings(request);
            session.capture(request.build(),new CameraCaptureSession.CaptureCallback() {
                @Override public void onCaptureStarted(CameraCaptureSession s,CaptureRequest r,long timestamp,long frameNumber) {if(token==generation)photoStrobe();}
                public void onCaptureFailed(CameraCaptureSession s,CaptureRequest r,CaptureFailure f) {photoPending=false;updateControls();notice("No se pudo tomar la foto");}
            },handler);
            handler.postDelayed(() -> {if(token==generation && photoPending) {photoPending=false;updateControls();notice("La foto tardó demasiado; intenta de nuevo");}},8000);
        }catch(Exception e) {photoPending=false;updateControls();notice("Error de foto: "+e.getMessage());}
    }
    private void savePhoto(ImageReader reader) {
        byte[] bytes;try(Image image=reader.acquireLatestImage()) {if(image==null)return;ByteBuffer b=image.getPlanes()[0].getBuffer();bytes=new byte[b.remaining()];b.get(bytes);}
        catch(Exception e) {photoPending=false;updateControls();notice("Error al recibir foto");return;}
        io.execute(() -> {
            Uri uri=null;try {
                uri=newMedia(false);try(OutputStream out=getContentResolver().openOutputStream(uri)) {out.write(bytes);}final Uri saved=uri;
                handler.post(() -> {
                    try {publish(saved,"image/jpeg");notice("Foto guardada en Galería · Raw4All");}
                    catch(Exception e) {removeMedia(saved);report("JPEG PUBLISH ERROR "+e);notice("No se pudo publicar la foto en galería");}
                    finally {photoPending=false;updateControls();updateStorage();}
                });
            }catch(Exception e) {removeMedia(uri);handler.post(() -> {photoPending=false;updateControls();notice("No se pudo guardar la foto");});}
        });
    }
    private void openGallery() {
        if(recording || busy || photoPending)return;
        if(sessionCaptures.isEmpty()) {openPhoneGallery();return;}
        ArrayList<SessionCapture> captures=new ArrayList<>(sessionCaptures);Collections.reverse(captures);
        GridView grid=new GridView(this);grid.setNumColumns(portrait ? 3 : 5);
        grid.setHorizontalSpacing(dp(6));grid.setVerticalSpacing(dp(6));grid.setPadding(dp(12),dp(12),dp(12),dp(12));
        grid.setAdapter(new SessionGalleryAdapter(captures));
        grid.setOnItemClickListener((parent,view,index,id) -> {
            SessionCapture capture=captures.get(index);
            try {startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(capture.uri,capture.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}
            catch(Exception e) {notice("Esta captura ya no está disponible o no hay visor compatible");}
        });
        FrameLayout panel=new FrameLayout(this);panel.addView(grid,new FrameLayout.LayoutParams(-1,dp(portrait ? 380 : 220)));
        new android.app.AlertDialog.Builder(this).setTitle("Esta sesión · "+captures.size()+" capturas")
            .setView(panel).setNeutralButton("Ir a Galería",(dialog,which) -> openPhoneGallery())
            .setPositiveButton("Volver a cámara",null).show();
    }
    private void openPhoneGallery() {
        Intent samsungGallery=getPackageManager().getLaunchIntentForPackage("com.sec.android.gallery3d");
        if(samsungGallery!=null)try {startActivity(samsungGallery);return;}catch(ActivityNotFoundException ignored) {}
        try {startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY));}
        catch(ActivityNotFoundException e) {notice("No hay una aplicación de galería disponible");}
    }
    private final class SessionGalleryAdapter extends BaseAdapter {
        private final List<SessionCapture> captures;
        private final android.util.LruCache<String,Bitmap> thumbnails=new android.util.LruCache<>(24);
        private final Set<String> pending=new HashSet<>();
        private final Set<String> unavailable=new HashSet<>();
        SessionGalleryAdapter(List<SessionCapture> captures) {this.captures=captures;}
        public int getCount() {return captures.size();}
        public Object getItem(int position) {return captures.get(position);}
        public long getItemId(int position) {return position;}
        public View getView(int position,View reused,android.view.ViewGroup parent) {
            FrameLayout tile;
            if(reused==null) {
                tile=new FrameLayout(MainActivity.this);tile.setLayoutParams(new AbsListView.LayoutParams(-1,dp(104)));
                tile.setBackgroundColor(PANEL);
                ImageView image=new ImageView(MainActivity.this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);tile.addView(image,new FrameLayout.LayoutParams(-1,-1));
                TextView type=label("",11,WHITE);type.setGravity(Gravity.CENTER);type.setBackgroundColor(0x99171d23);
                tile.addView(type,new FrameLayout.LayoutParams(-1,dp(24),Gravity.BOTTOM));
            }else tile=(FrameLayout)reused;
            SessionCapture capture=captures.get(position);String key=capture.uri.toString();
            tile.setContentDescription((capture.mime.startsWith("video") ? "Video" : "Foto")+" de esta sesión, "+(position+1));
            ((TextView)tile.getChildAt(1)).setText(capture.mime.startsWith("video") ? "▶ Video" : capture.mime.equals("image/x-adobe-dng") ? "RAW · 50 MP" : "Foto");
            ImageView image=(ImageView)tile.getChildAt(0);Bitmap cached=thumbnails.get(key);image.setImageBitmap(cached);
            if(cached==null && !pending.contains(key) && !unavailable.contains(key) && !io.isShutdown()) {
                pending.add(key);io.execute(() -> {
                    Bitmap bitmap=null;try {bitmap=getContentResolver().loadThumbnail(capture.uri,new Size(256,256),null);}catch(Exception ignored) {}
                    final Bitmap result=bitmap;
                    handler.post(() -> {pending.remove(key);if(result!=null) {thumbnails.put(key,result);notifyDataSetChanged();}else unavailable.add(key);});
                });
            }
            return tile;
        }
    }
    private void photoStrobe() {
        View flash=new View(this);flash.setBackgroundColor(Color.WHITE);flash.setAlpha(0.7f);
        flash.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);flash.setClickable(false);
        viewfinder.addView(flash,new FrameLayout.LayoutParams(-1,-1));
        flash.animate().alpha(0f).setDuration(180).withEndAction(() -> viewfinder.removeView(flash)).start();
    }
    private android.graphics.drawable.Drawable splashBrandDrawable(View view) {
        return view instanceof ImageView ? ((ImageView)view).getDrawable() : view.getBackground();
    }
    private View findSplashBrand(View view,View icon) {
        if(view==icon)return null;
        android.graphics.drawable.Drawable drawable=splashBrandDrawable(view);
        if(drawable!=null && drawable.getIntrinsicHeight()>0
                && drawable.getIntrinsicWidth()>drawable.getIntrinsicHeight()*1.5f)return view;
        if(view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group=(android.view.ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++) {
                View found=findSplashBrand(group.getChildAt(i),icon);if(found!=null)return found;
            }
        }
        return null;
    }
    /** Only adds colored slices over the existing branding; native splash geometry stays intact. */
    private final class SplashBrandGlitch extends android.graphics.drawable.Drawable {
        private final long start;
        private final View target;
        private final android.graphics.drawable.Drawable copy;
        SplashBrandGlitch(View target,long start) {
            this.target=target;this.start=start;
            copy=splashBrandDrawable(target).getConstantState().newDrawable(getResources()).mutate();
        }
        @Override public void draw(Canvas canvas) {
            long elapsed=SystemClock.uptimeMillis()-start;
            if(elapsed>=1120)return;
            android.graphics.drawable.Drawable original=splashBrandDrawable(target);if(original==null)return;
            Rect bounds=original.getBounds();if(bounds.isEmpty())return;
            copy.setBounds(bounds);
            canvas.save();
            if(target instanceof ImageView) {canvas.translate(target.getPaddingLeft(),target.getPaddingTop());canvas.concat(((ImageView)target).getImageMatrix());}
            // Full-letter RGB separation lasts through the splash, not only an 80 ms slice.
            int beat=(int)(elapsed/65)%6;
            float strength=beat==1 || beat==4 ? 1f : .65f;
            float shift=bounds.width()*.027f*strength;
            drawChannel(canvas,0xffff3055,-shift,0,225);
            drawChannel(canvas,0xff38ff78,0,-bounds.height()*.016f,215);
            drawChannel(canvas,0xff397dff,shift,bounds.height()*.008f,240);
            drawChannel(canvas,0xffe4ecf4,0,0,155);
            // Short scanline displacements make the channel split visibly glitch.
            float line=.405f+(beat%3)*.056f;
            canvas.save();canvas.clipRect(bounds.left,bounds.top+bounds.height()*line,bounds.right,bounds.top+bounds.height()*(line+.038f));
            drawChannel(canvas,beat%2==0 ? 0xffff3055 : 0xff397dff,(beat%2==0 ? 1 : -1)*shift*1.8f,0,255);
            canvas.restore();copy.setAlpha(255);copy.clearColorFilter();canvas.restore();
        }
        private void drawChannel(Canvas canvas,int color,float x,float y,int alpha) {
            canvas.save();canvas.translate(x,y);copy.setAlpha(alpha);copy.setColorFilter(color,PorterDuff.Mode.SRC_IN);copy.draw(canvas);canvas.restore();
        }
        @Override public void setAlpha(int alpha) {copy.setAlpha(alpha);invalidateSelf();}
        @Override public void setColorFilter(ColorFilter filter) {copy.setColorFilter(filter);invalidateSelf();}
        @Override public int getOpacity() {return PixelFormat.TRANSLUCENT;}
    }
    /** Canvas icons stay sharp at every screen density, without relying on font glyphs. */
    private final class SettingsIcon extends android.graphics.drawable.Drawable {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int type;
        SettingsIcon(int type,int color) {this.type=type;paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.8f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);}
        @Override public void draw(Canvas canvas) {
            Rect b=getBounds();float size=Math.min(dp(26),Math.min(b.width(),b.height()));
            canvas.save();canvas.translate(b.exactCenterX()-size/2,b.exactCenterY()-size/2);canvas.scale(size/24,size/24);
            if(type==0) {
                canvas.drawCircle(12,12,6.5f,paint);canvas.drawCircle(12,12,2.7f,paint);
                for(int i=0;i<8;i++) {canvas.save();canvas.rotate(i*45,12,12);canvas.drawLine(12,2.5f,12,5.5f,paint);canvas.restore();}
            }else if(type==1 || type==2) {
                canvas.drawRoundRect(9,3,15,14,3,3,paint);canvas.drawArc(6,6,18,18,0,180,false,paint);canvas.drawLine(12,18,12,21,paint);canvas.drawLine(8,21,16,21,paint);
                if(type==2)canvas.drawLine(3,3,21,21,paint);
            }else if(type==3) {
                canvas.drawCircle(12,13,8,paint);canvas.drawLine(10,2,14,2,paint);canvas.drawLine(12,2,12,5,paint);canvas.drawLine(12,13,12,8,paint);canvas.drawLine(12,13,15,15,paint);
            }else if(type==5) {
                canvas.save();canvas.translate(1,3.5f);canvas.scale(.88f,.88f);
                // Leave the upper-right opening for the light bulb.
                canvas.drawArc(1.5f,4,19.5f,22,0,285,false,paint);
                canvas.drawLine(12.8f,4.3f,10,2.5f,paint);canvas.drawLine(12.8f,4.3f,10.5f,6.8f,paint);
                canvas.drawLine(7,12,7,9.5f,paint);canvas.drawLine(7,9.5f,9.5f,9.5f,paint);canvas.drawLine(11.5f,9.5f,14,9.5f,paint);canvas.drawLine(14,9.5f,14,12,paint);
                canvas.drawLine(7,14,7,16.5f,paint);canvas.drawLine(7,16.5f,9.5f,16.5f,paint);canvas.drawLine(11.5f,16.5f,14,16.5f,paint);canvas.drawLine(14,16.5f,14,14,paint);
                paint.setStrokeWidth(1.4f);
                Path bulb=new Path();bulb.moveTo(17.5f,8);bulb.lineTo(17.5f,6.5f);bulb.cubicTo(14.3f,3.4f,16.2f,1,19,1);bulb.cubicTo(21.8f,1,23.7f,3.4f,20.5f,6.5f);bulb.lineTo(20.5f,8);bulb.close();canvas.drawPath(bulb,paint);
                canvas.drawLine(18,10,20,10,paint);
                canvas.drawLine(19,-1,19,-3,paint);
                canvas.drawLine(15,-.2f,13.6f,-1.6f,paint);
                canvas.drawLine(23,-.2f,24.4f,-1.6f,paint);
                paint.setStrokeWidth(1.8f);canvas.restore();
            }else {
                canvas.drawRoundRect(3,3,21,21,2,2,paint);canvas.drawLine(9,3,9,21,paint);canvas.drawLine(15,3,15,21,paint);canvas.drawLine(3,9,21,9,paint);canvas.drawLine(3,15,21,15,paint);
            }
            canvas.restore();
        }
        @Override public void setAlpha(int alpha) {paint.setAlpha(alpha);invalidateSelf();}
        @Override public void setColorFilter(ColorFilter filter) {paint.setColorFilter(filter);invalidateSelf();}
        @Override public int getOpacity() {return PixelFormat.TRANSLUCENT;}
    }
    private final class ShutterDrawable extends android.graphics.drawable.Drawable {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.drawable.Drawable ring;
        private final boolean photo,stop;
        private boolean pressed;
        ShutterDrawable(boolean photo,boolean stop) {
            this.photo=photo;this.stop=stop;
            ring=getDrawable(getResources().getIdentifier("shutter_ring","drawable",getPackageName())).mutate();
        }
        @Override public boolean isStateful() {return true;}
        @Override protected boolean onStateChange(int[] states) {
            boolean next=false;for(int state:states)if(state==android.R.attr.state_pressed)next=true;
            if(next==pressed)return false;pressed=next;invalidateSelf();return true;
        }
        @Override public void draw(Canvas canvas) {
            Rect bounds=getBounds();float scale=Math.min(bounds.width(),bounds.height())/104f;
            canvas.save();canvas.translate(bounds.exactCenterX(),bounds.exactCenterY());canvas.scale(scale,scale);
            ring.setBounds(-52,-52,52,52);ring.draw(canvas);
            paint.setColor(photo && !stop ? (pressed ? 0xffe6e6e6 : Color.WHITE) : (pressed ? 0xffdc3028 : 0xffff3b30));
            if(stop) {float half=pressed ? 19 : 22;canvas.drawRoundRect(-half,-half,half,half,8,8,paint);}
            else canvas.drawCircle(0,0,pressed ? 30 : 34,paint);
            canvas.restore();
        }
        @Override public void setAlpha(int alpha) {paint.setAlpha(alpha);ring.setAlpha(alpha);invalidateSelf();}
        @Override public void setColorFilter(ColorFilter filter) {paint.setColorFilter(filter);ring.setColorFilter(filter);invalidateSelf();}
        @Override public int getOpacity() {return PixelFormat.TRANSLUCENT;}
    }
    private static final class CaptureIcon extends android.graphics.drawable.Drawable {
        private final int kind;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        CaptureIcon(int kind,int color) {this.kind=kind;paint.setColor(color);paint.setStrokeWidth(1.8f);paint.setStrokeJoin(Paint.Join.ROUND);paint.setStrokeCap(Paint.Cap.ROUND);}
        @Override public void draw(Canvas canvas) {
            Rect bounds=getBounds();float size=Math.min(bounds.width(),bounds.height())*0.43f;
            canvas.save();canvas.translate(bounds.exactCenterX()-size/2,bounds.exactCenterY()-size/2);canvas.scale(size/24,size/24);
            paint.setStyle(Paint.Style.STROKE);
            if(kind==0) {
                canvas.drawRoundRect(2,6,22,21,3,3,paint);canvas.drawCircle(12,13.5f,4,paint);
                Path top=new Path();top.moveTo(7,6);top.lineTo(9,3);top.lineTo(15,3);top.lineTo(17,6);canvas.drawPath(top,paint);
            }else if(kind==1) {
                canvas.drawRoundRect(2,5,16,19,2,2,paint);Path prism=new Path();prism.moveTo(16,10);prism.lineTo(22,6);prism.lineTo(22,18);prism.lineTo(16,14);canvas.drawPath(prism,paint);
            }else if(kind==2) {
                canvas.drawRoundRect(2,3,22,21,2,2,paint);canvas.drawCircle(8,8,1.5f,paint);
                Path mountains=new Path();mountains.moveTo(3,18);mountains.lineTo(9,12);mountains.lineTo(13,16);mountains.lineTo(17,11);mountains.lineTo(21,16);canvas.drawPath(mountains,paint);
            }else if(kind==3) {
                paint.setStyle(Paint.Style.FILL);canvas.drawRoundRect(4,3,9.5f,21,1.5f,1.5f,paint);canvas.drawRoundRect(14.5f,3,20,21,1.5f,1.5f,paint);
            }else if(kind==4) {
                paint.setStyle(Paint.Style.FILL);Path play=new Path();play.moveTo(6,3);play.lineTo(21,12);play.lineTo(6,21);play.close();canvas.drawPath(play,paint);
            }else {paint.setStyle(Paint.Style.FILL);canvas.drawRoundRect(5,5,19,19,2,2,paint);}
            canvas.restore();
        }
        @Override public void setAlpha(int alpha) {paint.setAlpha(alpha);invalidateSelf();}
        @Override public void setColorFilter(ColorFilter filter) {paint.setColorFilter(filter);invalidateSelf();}
        @Override public int getOpacity() {return PixelFormat.TRANSLUCENT;}
    }
    private void closeCamera() {countdownSequence++;countdownActive=false;handler.removeCallbacks(smoothZoom);
        if(rawVendorSession) {orientationLocked=false;applyFormatPolicy();}
        rawVendorSession=false;
        if(recorder!=null)stopRecording(false);generation++;if(session!=null) {session.close();session=null;}if(camera!=null) {camera.close();camera=null;}
        focusSequence++;focusPending=false;focusLocked=false;exposureLocked=false;focusRegions=null;exposureRegions=null;lockedFocusDistance=null;lastSensorCrop=null;repeatingRequest=null;if(focusOverlay!=null)focusOverlay.hide();
        if(jpegReader!=null) {jpegReader.close();jpegReader=null;}if(preview!=null) {preview.release();preview=null;}busy=false;photoPending=false;if(shutter!=null)updateControls();
    }
    @Override protected void onResume() {super.onResume();resumed=true;updateStorage();handler.removeCallbacks(audioMeter);handler.post(audioMeter);if(horizonSensor!=null)horizonSensors.registerListener(horizonListener,horizonSensor,android.hardware.SensorManager.SENSOR_DELAY_GAME);if(orientationListener!=null)orientationListener.enable();handler.removeCallbacks(clock);handler.post(clock);maybeOpen();}
    @Override protected void onPause() {resumed=false;handler.removeCallbacks(audioMeter);if(horizonSensors!=null)horizonSensors.unregisterListener(horizonListener);if(orientationListener!=null)orientationListener.disable();handler.removeCallbacks(clock);closeCamera();super.onPause();}
    @Override protected void onStop() {
        super.onStop();handler.removeCallbacks(smoothZoom);sessionZooms.clear();zoom=targetZoom=1f;
        if(zoomSlider!=null)zoomSlider.setProgress(0);if(zoomLabel!=null)updateZoomLabel();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);ArrayList<String> uris=new ArrayList<>(),mimes=new ArrayList<>();
        for(SessionCapture capture:sessionCaptures) {uris.add(capture.uri.toString());mimes.add(capture.mime);}
        state.putStringArrayList("session_uris",uris);state.putStringArrayList("session_mimes",mimes);
    }
    @Override protected void onDestroy() {io.shutdown();super.onDestroy();}
}
