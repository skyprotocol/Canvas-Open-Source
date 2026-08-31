package com.tgc.sky;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.display.DisplayManager;
import android.hardware.input.InputManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import androidx.core.view.InputDeviceCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.tgc.sky.io.AudioDeviceType;
import com.tgc.sky.ui.panels.BasePanel;
import com.tgc.sky.ui.Utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;
import org.fmod.FMOD;
import org.json.JSONException;
import org.json.JSONObject;

import git.artdeell.skymodloader.BuildConfig;
import git.artdeell.skymodloader.DialogJNI;
import git.artdeell.skymodloader.FileSelector;
import git.artdeell.skymodloader.ImGUI;
import git.artdeell.skymodloader.R;
import git.artdeell.skymodloader.SMLApplication;
import git.artdeell.skymodloader.ImGUITextInput;
import kotlin.KotlinVersion;

public class GameActivity extends TGCNativeActivity implements View.OnCapturedPointerListener,
    SensorEventListener {

    static final boolean ENABLE_DISPLAY_CUTOUT_MODE = true;
    private static final String TAG = "GameActivity";
    public ArrayList<Integer> mGameControllerIds;
    private ArrayList<OnActivityIntentListener> mOnActivityIntentListeners;
    private ArrayList<OnActivityResultListener> mOnActivityResultListeners;
    private ArrayList<OnKeyboardListener> mOnKeyboardListeners;
    private PermissionCallback mPermissionCallback = null;
    private ArrayList<BasePanel> mActivePanels = new ArrayList<>();
    private ImageView logoView;
    private MediaPlayer m_mediaPlayer;
    private int m_nativeHeight;
    private int m_nativeWidth;
    private ImGUI imgui;
    private SurfaceView imguiView;
    private boolean imguiKeybaordShowing;
    private ImGUITextInput imguiInput;
    private boolean m_logoSoundReleased = false;
    public Rect mSafeAreaInsets = new Rect();
    private int m_keyboardHeight = 0;
    private boolean m_editTextFocused = false;
    private boolean m_isKeyboardShowing = false;
    private RelativeLayout m_relativeLayout;
    private final int[] m_imguiViewLocation = new int[2];
    SystemAccounts_android m_systemAccounts = null;
    private boolean m_lTriggerPressed = false;
    private boolean m_rTriggerPressed = false;
    private boolean m_motionEventsDisabled = false;
    private int m_lastDpadDirection = 23;

    // Device motion (accelerometer / gyroscope / rotation), forwarded to native.
    // Sky 0.34.0 (401861) calls setMotionEnabled(boolean) via JNI; without it the
    // native code aborts at startup with NoSuchMethodError setMotionEnabled(Z)V.
    static final float INV_GRAVITY = 0.10197162f; // 1 / SensorManager.GRAVITY_EARTH (m/s^2 -> g)
    private SensorManager m_sensorManager = null;
    private Sensor m_gravitySensor = null;
    private Sensor m_linearAccelSensor = null;
    private Sensor m_rotationVectorSensor = null;
    private Sensor m_gyroscopeSensor = null;
    private boolean m_motionAvailable = false;
    private boolean m_motionListening = false;
    private final float[] m_latestGravity = new float[3];
    private final float[] m_latestLinearAccel = new float[3];
    private final float[] m_latestRotationRate = new float[3];
    private final float[] m_quaternion = { 1.0f, 0.0f, 0.0f, 0.0f };
    private volatile MotionSample m_motionSample = null;
    private PointF m_lastMouseLocation = new PointF();
    SystemIO_android m_systemIO = null;
    SystemUI_android m_systemUI = null;
    public boolean portraitOnResume = false;

    public static class ActivityRequestCode {
        static final int ASK_PERMISSIONS = 100;
        public static final int DYNAMIC_FEATURE_DOWNLOAD_CONFIRM = 120;
        public static final int GOOGLE_SIGN_IN = 140;
        public static final int HUAWEI_SIGN_IN = 160;
        public static final int IMAGE_PICKER = 130;
        static final int SHARE_IMG = 111;
        static final int SHARE_URL = 110;
        static final int SHARE_VIDEO = 112;
        public static final int SURVEY_MONKEY_RESPONSE = 150;
    }

    public interface OnActivityIntentListener {
        boolean onNewIntent(Intent intent);
    }

    public interface OnActivityResultListener {
        void onActivityResult(int i, int i2, Intent intent);
    }

    public interface OnKeyboardListener {
        void onKeyboardChange(boolean z, int i);
    }

    public interface PermissionCallback {
        void onPermissionResult(String[] strArr, int[] iArr);
    }

    public static float AppleConvertAndroidScale(float f) {
        return f * 1.5f;
    }

    private native void onCreateNative();
    public native void onSafeAreaInsetsChanged(float[] fArr);
    private static native boolean onTouchNative(int i, int i2, float f, float f2);
    public native String ResolveTemplateArgsNative(String str);
    public native void onMouseMovedNative(int x, int y);
    public native void onMouseDeltaNative(int dx, int dy);
    public native void onMouseScrollingDeltaNative(float x, float y);
    public native boolean onButtonPressNative(int keyCode, int inputSource, boolean pressed);
    public native void setGamepadProductTypeNative(int vendorId, int productId);
    public native void onAudioDeviceTypeChangedNative(int i);
    public native void onBackPressedNative();
    public native void onCommerceUpdateNative(boolean z, boolean z2, boolean z3);
    public native void onDpadEventNative(float f, float f2, double d);
    public native void onGamepadConnectedNative();
    public native void onGamepadDisconnectedNative();
    public native void onInternetReachabilityNative(boolean z, boolean z2);
    public native void onKeyboardCompleteNative(String str, boolean z, boolean z2);
    public native void onNFCTagScannedNative(String str, int i, String str2, String str3);
    public native void onOpenedWithURLNative(String str, boolean z);
    public native void onStickEventNative(float f, float f2, float f3, float f4);
    public native void onSystemScreenshotTakenNative();
    public native void onVolumeChangeNative(float f, float f2);
    public native void onDisplayChangedNative();
    public native void onAccelerometerNative(float gravityX, float gravityY, float gravityZ, float accelX, float accelY, float accelZ);
    public native void onOrientationNative(int orientation, float quatX, float quatY, float quatZ, float quatW, float rotX, float rotY, float rotZ);
    public native void onMotionAvailabilityNative(boolean available);
    public native void setPendingDeeplinkNative(String str);
    public native void onTextFieldTextChanged(String str);
    public native void onTextFieldCursorPosChanged(int start, int end);

    public int getAppBuildVersion() { return com.tgc.sky.BuildConfig.VERSION_CODE; }
    public String getAppVersion() { return com.tgc.sky.BuildConfig.SKY_VERSION; }
    public String getPlatformName() { return "android"; }
    public float transformHeightToProgram(float f) { return f; }
    public float transformHeightToSystem(float f) { return f; }
    public float transformWidthToProgram(float f) { return f; }
    public float transformWidthToSystem(float f) { return f; }

    @Override
    public void attachBaseContext(Context context) {
        super.attachBaseContext(context);
    }

    private boolean isTextRenderingBrokenForDevice() {
        if (Build.VERSION.SDK_INT == 31 || Build.VERSION.SDK_INT == 32) {
            String[] strArr = {"OPD2102", "X21N2", "PFUM10", "TB128FU", "RMX3478", "RMX3471", "RMX3472", "2201116SC", "22101317C"};
            for (int i = 0; i < 9; i++) {
                if (Build.MODEL.compareToIgnoreCase(strArr[i]) == 0) return true;
            }
            return false;
        }
        return false;
    }

    @SuppressLint("WrongConstant")
    private void fixTextRenderingOnProblemDevices_HACK() {
        if (isTextRenderingBrokenForDevice()) {
            Log.i(TAG, "Detected problematic text rendering on this device - applying workaround");
            for (int i = 0; i < 29; i++) {
                View view = new View(this);
                WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
                layoutParams.width = 2;
                layoutParams.height = 2;
                layoutParams.flags = WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                layoutParams.format = PixelFormat.RGBA_8888;
                layoutParams.gravity = Gravity.BOTTOM;
                ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).addView(view, layoutParams);
            }
        }
    }

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        DialogJNI.setActivity(this);
        hideNavigationFullScreen(getWindow().getDecorView());
        getWindow().addFlags(2097280);
        tryEnablingDisplayCutoutMode();
        setContentView(R.layout.tgc_logo);
        this.m_relativeLayout = findViewById(R.id.sml_relLayout);
        this.m_relativeLayout.setOnCapturedPointerListener(this);
        ((SurfaceView) findViewById(R.id.surfaceView)).getHolder().addCallback(this);
        FileSelector.setActivity(this);
        if (imgui == null) imgui = new ImGUI();
        imguiView = findViewById(R.id.imguiView);
        imguiView.getHolder().setFormat(PixelFormat.TRANSLUCENT);
        imguiView.getHolder().addCallback(imgui);
        imguiView.setZOrderOnTop(true);
        ImGUI.setClipboardService((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE));
        imguiInput = findViewById(R.id.imguiInput);
        FMOD.init(this);
        new SystemCommerce_android(this);
        this.m_systemIO = new SystemIO_android(this);
        this.m_systemAccounts = new SystemAccounts_android(this);
        SystemRemoteConfig_android.getInstance().Initialize(this);
        SystemSupport_android.getInstance().Initialize(this);
        this.m_systemUI = new SystemUI_android(this);
        git.artdeell.skymodloader.MainActivity.getSysetemUI(this.m_systemUI);
        setupMotionSensors();
        onCreateNative();
        initGameController();
        logoView = findViewById(R.id.imageView);
        if (logoView != null) {
            logoView.setImageResource(git.artdeell.skymodloader.server.ServerManager.getActiveBootLogoRes(this));
        }
        Intent intent = getIntent();
        if (intent != null) HandleNewIntent(intent);
        getWindow().getDecorView().setOnApplyWindowInsetsListener((view, windowInsets) -> {
            try {
                int max = Integer.max(windowInsets.getStableInsetTop(), windowInsets.getStableInsetBottom());
                if (Build.VERSION.SDK_INT >= 27) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            if (windowInsets.getDisplayCutout() != null) {
                                max = Integer.max(max, Integer.max(
                                    windowInsets.getDisplayCutout().getSafeInsetLeft(),
                                    windowInsets.getDisplayCutout().getSafeInsetRight()));
                            }
                        }
                    } catch (NoSuchMethodError ignored) {}
                }
                GameActivity.this.mSafeAreaInsets.left = max;
                GameActivity.this.mSafeAreaInsets.top = 0;
                GameActivity.this.mSafeAreaInsets.right = max;
                GameActivity.this.mSafeAreaInsets.bottom = 0;
                float t = GameActivity.this.transformWidthToProgram(max);
                GameActivity.this.onSafeAreaInsetsChanged(new float[]{t, 0.0f, t, 0.0f});
                return view.onApplyWindowInsets(windowInsets);
            } catch (Exception | NoSuchMethodError unused) {
                return windowInsets;
            }
        });
        if (Build.VERSION.SDK_INT >= 30) setupDisplayListener();
    }

    // Parity with stock Sky 0.34.5 (410941): the captured-pointer callback
    // exists ONLY for Sony gamepad touchpads (button taps forwarded as key
    // 0x6d at inputSource 1; touchpad MOTION is dropped, as in stock). ANY
    // other captured device - a hardware mouse above all - releases capture
    // immediately, so a mouse can never get stuck in relative mode: captured
    // input bypasses the whole view tree, which would leave it click-dead
    // and delta-driven for as long as capture held.
    @Override
    public boolean onCapturedPointer(View view, MotionEvent event) {
        if (isGamepadWithTouchpadEvent(event)) {
            int vendorId = getDeviceVendorId(event);
            int productId = getDeviceProductId(event);
            int action = event.getAction();
            if (action == MotionEvent.ACTION_BUTTON_PRESS) {
                onButtonPress(0x6d, 1, true, vendorId, productId);
            } else if (action == MotionEvent.ACTION_BUTTON_RELEASE) {
                onButtonPress(0x6d, 1, false, vendorId, productId);
            }
        } else {
            setPointerCapture(false);
        }
        return true;
    }

    private void setupDisplayListener() {
        final DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        displayManager.registerDisplayListener(new DisplayManager.DisplayListener() {
            @Override public void onDisplayAdded(int i) {}
            @Override public void onDisplayRemoved(int i) {}
            @Override public void onDisplayChanged(int i) {
                if (displayManager.getDisplay(i) != null) GameActivity.this.onDisplayChangedNative();
            }
        }, null);
    }

    public String getDeviceBrand() { return Build.BRAND; }

    @Override
    public void onDestroy() {
        FMOD.close();
        FileSelector.unsetActivity();
        this.m_systemIO.onDestroy();
        super.onDestroy();
    }

    @Override
    public void onResume() {
        if (this.m_sensorManager != null && this.m_motionListening) {
            this.m_motionListening = registerMotionListeners();
        }
        if (this.portraitOnResume) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        this.m_systemIO.onResume();
        this.m_systemAccounts.onResume();
        super.onResume();
        if (this.portraitOnResume) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (GameActivity.this.portraitOnResume)
                    GameActivity.this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            }, 1000L);
        }
    }

    @Override
    public void onPause() {
        if (this.portraitOnResume) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        this.m_systemIO.onPause();
        if (this.m_sensorManager != null && this.m_motionListening) {
            this.m_sensorManager.unregisterListener(this);
        }
        super.onPause();
    }

    // ==== Device motion sensors (Sky 0.34.0 / 401861) ====================
    // Restores GameActivity.setMotionEnabled(boolean) and its sensor subsystem.
    // The game's native code calls setMotionEnabled(boolean) via JNI every frame;
    // without it startup aborts with:
    //   NoSuchMethodError: com.tgc.sky.GameActivity.setMotionEnabled(Z)V
    // Ported to match TGC's own GameActivity so native behavior is identical.

    private static final class MotionSample {
        final float gravityX, gravityY, gravityZ;
        final float accelX, accelY, accelZ;
        final float quatX, quatY, quatZ, quatW;
        final float rotX, rotY, rotZ;
        final int orientation;

        MotionSample(float gravityX, float gravityY, float gravityZ,
                     float accelX, float accelY, float accelZ,
                     float quatX, float quatY, float quatZ, float quatW,
                     float rotX, float rotY, float rotZ, int orientation) {
            this.gravityX = gravityX; this.gravityY = gravityY; this.gravityZ = gravityZ;
            this.accelX = accelX; this.accelY = accelY; this.accelZ = accelZ;
            this.quatX = quatX; this.quatY = quatY; this.quatZ = quatZ; this.quatW = quatW;
            this.rotX = rotX; this.rotY = rotY; this.rotZ = rotZ;
            this.orientation = orientation;
        }
    }

    private void setupMotionSensors() {
        this.m_sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (this.m_sensorManager != null) {
            this.m_gravitySensor     = this.m_sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
            this.m_linearAccelSensor = this.m_sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
            // NOTE: TGC registers GAME_ROTATION_VECTOR here but onSensorChanged
            // matches ROTATION_VECTOR (below), so in the official app the
            // quaternion stays identity. Kept identical to match native behavior.
            this.m_rotationVectorSensor = this.m_sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
            this.m_gyroscopeSensor   = this.m_sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        }
        this.m_motionAvailable = this.m_gravitySensor != null && this.m_linearAccelSensor != null
                && this.m_rotationVectorSensor != null && this.m_gyroscopeSensor != null;
    }

    // Called by native every frame to enable/disable device-motion streaming and
    // to pull the latest sample. Must match the JNI signature setMotionEnabled(Z)V.
    public void setMotionEnabled(boolean enabled) {
        onMotionAvailabilityNative(this.m_motionAvailable);
        if (!this.m_motionAvailable) return;
        if (enabled != this.m_motionListening) {
            if (enabled) {
                this.m_motionListening = registerMotionListeners();
            } else {
                this.m_sensorManager.unregisterListener(this);
                this.m_motionListening = false;
            }
        }
        MotionSample sample = this.m_motionSample;
        if (!this.m_motionListening || sample == null) return;
        onAccelerometerNative(sample.gravityX, sample.gravityY, sample.gravityZ,
                sample.accelX, sample.accelY, sample.accelZ);
        onOrientationNative(sample.orientation, sample.quatX, sample.quatY, sample.quatZ,
                sample.quatW, sample.rotX, sample.rotY, sample.rotZ);
    }

    private boolean registerMotionListeners() {
        boolean ok = this.m_gravitySensor == null
                || this.m_sensorManager.registerListener(this, this.m_gravitySensor, SensorManager.SENSOR_DELAY_GAME);
        if (this.m_linearAccelSensor != null)
            ok &= this.m_sensorManager.registerListener(this, this.m_linearAccelSensor, SensorManager.SENSOR_DELAY_GAME);
        if (this.m_rotationVectorSensor != null)
            ok &= this.m_sensorManager.registerListener(this, this.m_rotationVectorSensor, SensorManager.SENSOR_DELAY_GAME);
        if (this.m_gyroscopeSensor != null)
            ok &= this.m_sensorManager.registerListener(this, this.m_gyroscopeSensor, SensorManager.SENSOR_DELAY_GAME);
        if (!ok) {
            Log.w(TAG, "One or more motion sensors failed to register; disabling motion availability");
            this.m_sensorManager.unregisterListener(this);
            this.m_motionAvailable = false;
        }
        return ok;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_GRAVITY:
                this.m_latestGravity[0] = -event.values[0] * INV_GRAVITY;
                this.m_latestGravity[1] = -event.values[1] * INV_GRAVITY;
                this.m_latestGravity[2] = -event.values[2] * INV_GRAVITY;
                break;
            case Sensor.TYPE_LINEAR_ACCELERATION:
                this.m_latestLinearAccel[0] = event.values[0] * INV_GRAVITY;
                this.m_latestLinearAccel[1] = event.values[1] * INV_GRAVITY;
                this.m_latestLinearAccel[2] = event.values[2] * INV_GRAVITY;
                break;
            case Sensor.TYPE_GYROSCOPE:
                this.m_latestRotationRate[0] = event.values[0];
                this.m_latestRotationRate[1] = event.values[1];
                this.m_latestRotationRate[2] = event.values[2];
                break;
            case Sensor.TYPE_ROTATION_VECTOR:
                SensorManager.getQuaternionFromVector(this.m_quaternion, event.values);
                break;
            default:
                return;
        }
        publishMotionSample();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    private void publishMotionSample() {
        float gx = this.m_latestGravity[0], gy = this.m_latestGravity[1], gz = this.m_latestGravity[2];
        float ax = this.m_latestLinearAccel[0], ay = this.m_latestLinearAccel[1], az = this.m_latestLinearAccel[2];
        float rx = this.m_latestRotationRate[0], ry = this.m_latestRotationRate[1], rz = this.m_latestRotationRate[2];
        float qw = this.m_quaternion[0], qx = this.m_quaternion[1], qy = this.m_quaternion[2], qz = this.m_quaternion[3];

        float outGravityX, outGravityY, outAccelX, outAccelY, outRotX, outRotY;
        float outQuatX, outQuatY, outQuatZ, outQuatW;
        if (isNaturalLandscape()) {
            // Remap device frame -> game's natural-landscape frame: (x, y) -> (y, -x)
            // for the vectors, and rotate the orientation quaternion by 45 degrees
            // (0.70710677 = cos 45deg = sqrt(1/2)).
            final float c = 0.70710677f;
            outGravityX =  gy; outGravityY = -gx;
            outAccelX   =  ay; outAccelY   = -ax;
            outRotX     =  ry; outRotY     = -rx;
            outQuatX = c * (qx + qy);
            outQuatY = c * (qy - qx);
            outQuatZ = c * (qz - qw);
            outQuatW = c * (qw + qz);
        } else {
            outGravityX = gx; outGravityY = gy;
            outAccelX   = ax; outAccelY   = ay;
            outRotX     = rx; outRotY     = ry;
            outQuatX = qx; outQuatY = qy; outQuatZ = qz; outQuatW = qw;
        }
        this.m_motionSample = new MotionSample(
                outGravityX, outGravityY, gz,
                outAccelX, outAccelY, az,
                outQuatX, outQuatY, outQuatZ, outQuatW,
                outRotX, outRotY, rz,
                getMotionDeviceOrientation());
    }

    private int getDisplayRotation() {
        Display display = Build.VERSION.SDK_INT >= 30 ? getDisplay() : getWindowManager().getDefaultDisplay();
        return display != null ? display.getRotation() : Surface.ROTATION_0;
    }

    private boolean isNaturalLandscape() {
        int rotation = getDisplayRotation();
        int orientation = getResources().getConfiguration().orientation;
        return ((rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180)
                    && orientation == Configuration.ORIENTATION_LANDSCAPE)
            || ((rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270)
                    && orientation == Configuration.ORIENTATION_PORTRAIT);
    }

    private int getMotionDeviceOrientation() {
        int rotation = getDisplayRotation();
        if (isNaturalLandscape()) {
            switch (rotation) {
                case Surface.ROTATION_90:  return 2;
                case Surface.ROTATION_180: return 4;
                case Surface.ROTATION_270: return 1;
                default:                   return 3; // ROTATION_0
            }
        }
        switch (rotation) {
            case Surface.ROTATION_90:  return 3;
            case Surface.ROTATION_180: return 2;
            case Surface.ROTATION_270: return 4;
            default:                   return 1; // ROTATION_0
        }
    }

    @Override
    public void onWindowFocusChanged(boolean z) {
        RelativeLayout relativeLayout;
        super.onWindowFocusChanged(z);
        if (z && (relativeLayout = this.m_relativeLayout) != null) hideNavigationFullScreen(relativeLayout);
    }

    @Override
    public void onBackPressed() { onBackPressedNative(); }

    public void AddOnActivityIntentListener(OnActivityIntentListener l) {
        if (this.mOnActivityIntentListeners == null) this.mOnActivityIntentListeners = new ArrayList<>();
        if (!this.mOnActivityIntentListeners.contains(l)) this.mOnActivityIntentListeners.add(l);
    }

    public void RemoveOnActivityIntentListeners(OnActivityIntentListener l) {
        ArrayList<OnActivityIntentListener> a = this.mOnActivityIntentListeners;
        if (a != null) a.remove(l);
    }

    @Override
    public void onNewIntent(Intent intent) {
        ArrayList<OnActivityIntentListener> a = this.mOnActivityIntentListeners;
        if (a != null) {
            Iterator<OnActivityIntentListener> it = a.iterator();
            while (it.hasNext()) { if (it.next().onNewIntent(intent)) return; }
        }
        HandleNewIntent(intent);
    }

    public void HandleNewIntent(Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras != null) {
            JSONObject json = new JSONObject();
            for (String str : extras.keySet()) {
                try { json.put(str, JSONObject.wrap(extras.get(str))); } catch (JSONException ignored) {}
            }
            SystemIO_android.getInstance().OnAppLaunchNotificationMessage(json.toString());
        }
        String action = intent.getAction();
        Uri data = intent.getData();
        if ("android.intent.action.VIEW".equals(action) && data != null) {
            onOpenedWithURLNative(data.toString(), false);
        } else if ("android.nfc.action.NDEF_DISCOVERED".equals(action) && data != null) {
            onOpenedWithURLNative(data.toString(), true);
        }
    }

    public void AddOnActivityResultListener(OnActivityResultListener l) {
        if (this.mOnActivityResultListeners == null) this.mOnActivityResultListeners = new ArrayList<>();
        if (!this.mOnActivityResultListeners.contains(l)) this.mOnActivityResultListeners.add(l);
    }

    public void RemoveOnActivityResultListeners(OnActivityResultListener l) {
        ArrayList<OnActivityResultListener> a = this.mOnActivityResultListeners;
        if (a != null) a.remove(l);
    }

    @Override
    protected void onActivityResult(int i, int i2, Intent intent) {
        Log.i("Interlock", "GameActivity onActivityResult");
        super.onActivityResult(i, i2, intent);
        ArrayList<OnActivityResultListener> a = this.mOnActivityResultListeners;
        if (a != null) { for (OnActivityResultListener l : a) l.onActivityResult(i, i2, intent); }
    }

    public boolean checkSelfPermissions(String[] strArr) {
        boolean z = true;
        for (String str : strArr) z &= checkSelfPermission(str) == PackageManager.PERMISSION_GRANTED;
        return z;
    }

    public boolean checkResultPermissions(int[] iArr) {
        boolean z = iArr.length > 0;
        for (int i : iArr) z &= i == PackageManager.PERMISSION_GRANTED;
        return z;
    }

    public int[] getSelfPermissions(String[] strArr) {
        int[] iArr = new int[strArr.length];
        for (int i = 0; i < strArr.length; i++) iArr[i] = checkSelfPermission(strArr[i]);
        return iArr;
    }

    public boolean shouldShowRequestPermissionsRationale(String[] strArr) {
        boolean z = false;
        for (String str : strArr) z |= shouldShowRequestPermissionRationale(str);
        return z;
    }

    public void requestPermissions(String[] strArr, PermissionCallback permissionCallback) {
        if (checkSelfPermissions(strArr)) {
            permissionCallback.onPermissionResult(strArr, getSelfPermissions(strArr));
        } else if (this.mPermissionCallback != null) {
            permissionCallback.onPermissionResult(new String[0], new int[0]);
        } else {
            this.mPermissionCallback = permissionCallback;
            requestPermissions(strArr, 100);
        }
    }

    @Override
    public void onRequestPermissionsResult(int i, String[] strArr, int[] iArr) {
        PermissionCallback cb;
        super.onRequestPermissionsResult(i, strArr, iArr);
        if (i != 100 || (cb = this.mPermissionCallback) == null) return;
        cb.onPermissionResult(strArr, iArr);
        this.mPermissionCallback = null;
    }

    public void requestPermissionsThroughSettings(final String[] strArr, final PermissionCallback permissionCallback) {
        runOnUiThread(() -> {
            GameActivity.this.AddOnActivityResultListener(new OnActivityResultListener() {
                @Override
                public void onActivityResult(int i, int i2, Intent intent) {
                    if (i == 100) {
                        permissionCallback.onPermissionResult(strArr, GameActivity.this.getSelfPermissions(strArr));
                        GameActivity.this.RemoveOnActivityResultListeners(this);
                    }
                }
            });
            GameActivity.this.startActivityForResult(new Intent(
                "android.settings.APPLICATION_DETAILS_SETTINGS",
                Uri.fromParts("package", BuildConfig.APPLICATION_ID, null)), 100);
        });
    }

    @Override
    public boolean onTouchEvent(MotionEvent motionEvent) {
        int actionMasked = motionEvent.getActionMasked();
        if (actionMasked == MotionEvent.ACTION_UP || actionMasked == MotionEvent.ACTION_DOWN || actionMasked == MotionEvent.ACTION_MOVE) {
            float imguiX = motionEvent.getX();
            float imguiY = motionEvent.getY();
            if (imguiView != null) {
                imguiView.getLocationOnScreen(m_imguiViewLocation);
                imguiX = motionEvent.getRawX() - m_imguiViewLocation[0];
                imguiY = motionEvent.getRawY() - m_imguiViewLocation[1];
            }
            ImGUI.submitPositionEvent(imguiX, imguiY);
            if (actionMasked == MotionEvent.ACTION_DOWN) ImGUI.submitButtonEvent(0, true);
            if (actionMasked == MotionEvent.ACTION_UP) ImGUI.submitButtonEvent(0, false);
        }
        boolean wantsKeyboard = ImGUI.wantsKeyboard();
        if (wantsKeyboard && !imguiKeybaordShowing) { imguiInput.setKeyboardState(true); imguiKeybaordShowing = true; }
        if (!wantsKeyboard && imguiKeybaordShowing) { imguiInput.setKeyboardState(false); imguiKeybaordShowing = false; }
        if (ImGUI.wantsMouse()) return true;
        if (actionMasked == MotionEvent.ACTION_MOVE || actionMasked == MotionEvent.ACTION_CANCEL) {
            for (int i = 0; i < motionEvent.getPointerCount(); i++) {
                onTouchNative(motionEvent.getPointerId(i) + 1, actionMasked,
                    motionEvent.getX(i), motionEvent.getY(i));
            }
            return true;
        }
        int actionIndex = motionEvent.getActionIndex();
        return onTouchNative(motionEvent.getPointerId(actionIndex) + 1, actionMasked,
            motionEvent.getX(actionIndex), motionEvent.getY(actionIndex));
    }

    // Hardware-mouse input for the ImGui overlay (no stock counterpart -
    // stock has no overlay): feed the pointer exactly the way onTouchEvent
    // feeds touches - bridge-view-relative coordinates into the same
    // submitPositionEvent path, with ImGUI.wantsMouse() deciding whether the
    // game sees the event at all.
    private void submitImGuiMousePosition(MotionEvent motionEvent) {
        float x = motionEvent.getX();
        float y = motionEvent.getY();
        if (imguiView != null) {
            imguiView.getLocationOnScreen(m_imguiViewLocation);
            x = motionEvent.getRawX() - m_imguiViewLocation[0];
            y = motionEvent.getRawY() - m_imguiViewLocation[1];
        }
        ImGUI.submitPositionEvent(x, y);
    }

    private PointF transformPointToProgram(float x, float y) {
        PointF p = new PointF();
        p.x = transformWidthToProgram(x);
        p.y = transformHeightToProgram(y);
        return p;
    }

    private void onMouseMoved(int x, int y) {
        if (x != 0 || y != 0) setPointerCapture(false);
        onMouseMovedNative(x, y);
    }

    private void onMouseScrollingDelta(float x, float y) {
        if (x != 0.0f || y != 0.0f) setPointerCapture(false);
        onMouseScrollingDeltaNative(x, y);
    }

    private boolean isEventInTextField(MotionEvent motionEvent) {
        if (!m_systemUI.IsTextFieldShowing()) return false;
        Rect rect = m_systemUI.GetTextFieldHitRect();
        return rect.contains((int) motionEvent.getX(), (int) motionEvent.getY());
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent motionEvent) {
        if (isHardwareMouseEvent(motionEvent)) {
            if (isGamepadWithTouchpadEvent(motionEvent)) return true;
            PointF point = transformPointToProgram(motionEvent.getX(), motionEvent.getY());
            // Mouse-to-ImGui: primary-button gestures mirror the touch path
            // - position plus button 0 - and wantsMouse() then arbitrates
            // ownership just like onTouchEvent does for touches. The
            // backend's MouseDownOwned logic keeps a drag that began on the
            // game out of ImGui and vice versa.
            int actionMasked = motionEvent.getActionMasked();
            if (actionMasked == MotionEvent.ACTION_DOWN
                    || actionMasked == MotionEvent.ACTION_MOVE
                    || actionMasked == MotionEvent.ACTION_UP) {
                submitImGuiMousePosition(motionEvent);
                if (actionMasked == MotionEvent.ACTION_DOWN) ImGUI.submitButtonEvent(0, true);
                if (actionMasked == MotionEvent.ACTION_UP) ImGUI.submitButtonEvent(0, false);
            }
            if (ImGUI.wantsMouse()) {
                // ImGui owns this gesture: the game sees neither the camera
                // delta nor the cursor move. Still advance the delta anchor
                // so the first game-owned event afterwards computes no
                // spurious camera jump.
                m_lastMouseLocation = point;
                return true;
            }
            if (motionEvent.getButtonState() == 1) {
                float dx = (point.x - m_lastMouseLocation.x) * 3.0f;
                float dy = -(point.y - m_lastMouseLocation.y) * 3.0f;
                onMouseDeltaNative((int) dx, (int) dy);
            }
            onMouseMoved((int) point.x, (int) point.y);
            m_lastMouseLocation = point;
            if (isEventInTextField(motionEvent)) return super.dispatchTouchEvent(motionEvent);
            return true;
        }
        return super.dispatchTouchEvent(motionEvent);
    }

    public void notifyEditTextFocus(boolean z) {
        this.m_editTextFocused = z;
        if (Build.VERSION.SDK_INT < 30) {
            try {
                int iIntValue = ((Integer) InputMethodManager.class
                    .getMethod("getInputMethodWindowVisibleHeight", new Class[0])
                    .invoke((InputMethodManager) getSystemService("input_method"), new Object[0])).intValue();
                if (this.m_editTextFocused && iIntValue == 0) {
                    iIntValue = Utils.dp2px(30.0f);
                } else if (this.m_nativeWidth < this.m_nativeHeight) {
                    int id = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
                    if (id > 0) iIntValue += getResources().getDimensionPixelSize(id);
                }
                toggleKeyboard(this.m_editTextFocused, iIntValue);
                if (this.m_editTextFocused)
                    getBridgeView().postDelayed(() -> notifyEditTextFocus(m_editTextFocused), 100L);
            } catch (Exception unused) {}
        }
    }

    protected void handleKeyboardInsets(View view, WindowInsets windowInsets) {
        if (Build.VERSION.SDK_INT >= 30) {
            boolean visible = windowInsets.isVisible(WindowInsets.Type.ime());
            Insets insets = windowInsets.getInsets(WindowInsets.Type.ime());
            toggleKeyboard(visible, insets.bottom - insets.top);
        }
    }

    protected void toggleKeyboard(boolean z, int i) {
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        if (!z) {
            if (this.m_isKeyboardShowing) {
                this.m_isKeyboardShowing = false;
                this.m_keyboardHeight = 0;
                onHideKeyboard();
                lbm.sendBroadcast(new Intent("KeyboardWillHide"));
            }
            return;
        }
        if (this.m_isKeyboardShowing && i == this.m_keyboardHeight) return;
        this.m_isKeyboardShowing = true;
        this.m_keyboardHeight = i;
        onShowKeyboard(i);
        Intent intent = new Intent("KeyboardWillShow");
        intent.putExtra("KeyboardHeight", i);
        lbm.sendBroadcast(intent);
    }

    public void onGlobalLayout() {
        Rect rect = new Rect();
        this.m_relativeLayout.getWindowVisibleDisplayFrame(rect);
        int height = this.m_relativeLayout.getHeight() - rect.height();
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        if (height <= 0) {
            if (this.m_isKeyboardShowing) {
                this.m_isKeyboardShowing = false;
                onHideKeyboard();
                lbm.sendBroadcast(new Intent("KeyboardWillHide"));
            }
        } else if (!this.m_isKeyboardShowing) {
            this.m_isKeyboardShowing = true;
            onShowKeyboard(height);
            Intent intent = new Intent("KeyboardWillShow");
            intent.putExtra("KeyboardHeight", height);
            lbm.sendBroadcast(intent);
        }
    }

    protected void onShowKeyboard(int i) {
        ArrayList<OnKeyboardListener> a = this.mOnKeyboardListeners;
        if (a == null) return;
        for (OnKeyboardListener l : a) l.onKeyboardChange(true, i);
        getBridgeView().postDelayed(() -> GameActivity.hideNavigationFullScreen(GameActivity.this.getBridgeView()), 100L);
    }

    protected void onHideKeyboard() {
        ArrayList<OnKeyboardListener> a = this.mOnKeyboardListeners;
        if (a != null) { for (OnKeyboardListener l : a) l.onKeyboardChange(false, 0); }
    }

    public void addOnKeyboardListener(OnKeyboardListener l) {
        if (this.mOnKeyboardListeners == null) this.mOnKeyboardListeners = new ArrayList<>();
        if (!this.mOnKeyboardListeners.contains(l)) this.mOnKeyboardListeners.add(l);
    }

    public void RemoveOnKeyboardListener(OnKeyboardListener l) {
        ArrayList<OnKeyboardListener> a = this.mOnKeyboardListeners;
        if (a != null) a.remove(l);
    }

    private int getDeviceVendorId(InputEvent event) {
        InputDevice device = event.getDevice();
        if (device == null) return 0;
        return device.getVendorId();
    }

    private int getDeviceProductId(InputEvent event) {
        InputDevice device = event.getDevice();
        if (device == null) return 0;
        return device.getProductId();
    }

    private boolean isSony(int vendorId) { return vendorId == 0x54c; }
    private boolean isDualShock(int productId) { return productId == 0x5c4 || productId == 0x9cc; }
    private boolean isDualSense(int productId) { return productId == 0xce6 || productId == 0xdf2; }

    private boolean isHardwareKeyboardEvent(InputEvent event) {
        InputDevice device = event.getDevice();
        if (device == null) return false;
        int sources = device.getSources();
        return (sources & 0x101) == 0x101 && !device.isVirtual();
    }

    private boolean isHardwareMouseEvent(InputEvent event) {
        InputDevice device = event.getDevice();
        if (device == null) return false;
        return (event.getSource() & 0x2002) == 0x2002 && !device.isVirtual();
    }

    private boolean isGamepadEvent(InputEvent event) {
        if (!mGameControllerIds.contains(Integer.valueOf(event.getDeviceId()))) return false;
        return event.isFromSource(0x401) || event.isFromSource(0x1000010);
    }

    private boolean isGamepadWithTouchpadEvent(InputEvent event) {
        int vendorId = getDeviceVendorId(event);
        int productId = getDeviceProductId(event);
        return isSony(vendorId) && (isDualShock(productId) || isDualSense(productId));
    }

    private boolean onButtonPress(int keyCode, int inputSource, boolean pressed, int vendorId, int productId) {
        if (inputSource == 1) {
            setPointerCapture(true);
            setGamepadProductTypeNative(vendorId, productId);
        } else if (inputSource == 2 || inputSource == 3) {
            setPointerCapture(false);
        }
        return onButtonPressNative(keyCode, inputSource, pressed);
    }

    private void onGamepadConnected(int vendorId, int productId) {
        setPointerCapture(true);
        setGamepadProductTypeNative(vendorId, productId);
        onGamepadConnectedNative();
    }

    private void setPointerCapture(boolean capture) {
        if (capture) getBridgeView().requestPointerCapture();
        else getBridgeView().releasePointerCapture();
    }

    private int fixKeyCodeCompat(int inputSource, int keyCode, KeyEvent keyEvent) {
        if (Build.VERSION.SDK_INT >= 31) return keyCode;
        if (inputSource == 1) {
            int vendorId = getDeviceVendorId(keyEvent);
            int productId = getDeviceProductId(keyEvent);
            if (isSony(vendorId)) {
                if (keyCode == 4) return 2;
                if (keyCode == 125) return 1;
            }
        } else if (inputSource == 3) {
            if (keyCode == 4) return 2;
            if (keyCode == 125) return 1;
        }
        return keyCode;
    }

    public boolean isValidGameController(int i) {
        boolean z;
        InputDevice device = InputDevice.getDevice(i);
        if (device == null) return false;
        int sources = device.getSources();
        if ((sources & InputDeviceCompat.SOURCE_GAMEPAD) != 1025 ||
            (sources & InputDeviceCompat.SOURCE_JOYSTICK) != 16777232) return false;
        boolean[] hasKeys = device.hasKeys(new int[]{96, 97, 99, 100, 103});
        int i2 = 0;
        while (true) {
            if (i2 >= hasKeys.length) { z = true; break; }
            else if (!hasKeys[i2]) { z = false; break; }
            else i2++;
        }
        int i3 = 0;
        for (InputDevice.MotionRange next : device.getMotionRanges()) {
            if (next.getAxis() == 0 || next.getAxis() == 1 || next.getAxis() == 11 || next.getAxis() == 14) i3++;
        }
        return z && i3 >= 4;
    }

    private void initGameController() {
        this.mGameControllerIds = new ArrayList<>();
        for (int i : InputDevice.getDeviceIds()) {
            if (isValidGameController(i)) this.mGameControllerIds.add(i);
        }
        if (!this.mGameControllerIds.isEmpty()) {
            InputDevice device = InputDevice.getDevice(mGameControllerIds.get(0));
            if (device != null) onGamepadConnected(device.getVendorId(), device.getProductId());
        }
        InputManager inputManager = (InputManager) getBaseContext().getSystemService(Context.INPUT_SERVICE);
        if (inputManager != null) {
            inputManager.registerInputDeviceListener(new InputManager.InputDeviceListener() {
                @Override public void onInputDeviceChanged(int i2) {}
                @Override
                public void onInputDeviceAdded(int i2) {
                    if (GameActivity.this.isValidGameController(i2)) {
                        boolean isEmpty = GameActivity.this.mGameControllerIds.isEmpty();
                        GameActivity.this.mGameControllerIds.add(i2);
                        if (isEmpty) {
                            InputDevice d = InputDevice.getDevice(i2);
                            if (d != null) GameActivity.this.onGamepadConnected(d.getVendorId(), d.getProductId());
                        }
                    }
                }
                @Override
                public void onInputDeviceRemoved(int i2) {
                    if (GameActivity.this.mGameControllerIds.contains(i2)) {
                        GameActivity.this.mGameControllerIds.remove(Integer.valueOf(i2));
                        if (GameActivity.this.mGameControllerIds.isEmpty())
                            GameActivity.this.onGamepadDisconnectedNative();
                    }
                }
            }, null);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent keyEvent) {
        if (keyEvent.getRepeatCount() != 0) return super.onKeyDown(keyCode, keyEvent);
        int fixedKeyCode, inputSource;
        int vendorId = getDeviceVendorId(keyEvent);
        int productId = getDeviceProductId(keyEvent);
        if (isGamepadEvent(keyEvent)) {
            fixedKeyCode = fixKeyCodeCompat(1, keyCode, keyEvent);
            inputSource = 1;
        } else if (isHardwareMouseEvent(keyEvent)) {
            fixedKeyCode = fixKeyCodeCompat(3, keyCode, keyEvent);
            inputSource = 3;
        } else if (isHardwareKeyboardEvent(keyEvent)) {
            imgui.onKey(keyCode, true);
            fixedKeyCode = fixKeyCodeCompat(2, keyCode, keyEvent);
            inputSource = 2;
        } else {
            fixedKeyCode = keyCode;
            inputSource = 0;
        }
        if (onButtonPress(fixedKeyCode, inputSource, true, vendorId, productId)) return true;
        return super.onKeyDown(keyCode, keyEvent);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent keyEvent) {
        if (keyEvent.getRepeatCount() != 0) return super.onKeyUp(keyCode, keyEvent);
        int fixedKeyCode, inputSource;
        int vendorId = getDeviceVendorId(keyEvent);
        int productId = getDeviceProductId(keyEvent);
        if (isGamepadEvent(keyEvent)) {
            fixedKeyCode = fixKeyCodeCompat(1, keyCode, keyEvent);
            inputSource = 1;
        } else if (isHardwareMouseEvent(keyEvent)) {
            fixedKeyCode = fixKeyCodeCompat(3, keyCode, keyEvent);
            inputSource = 3;
        } else if (isHardwareKeyboardEvent(keyEvent)) {
            imgui.onKey(keyCode, false);
            fixedKeyCode = fixKeyCodeCompat(2, keyCode, keyEvent);
            inputSource = 2;
        } else {
            fixedKeyCode = keyCode;
            inputSource = 0;
        }
        if (onButtonPress(fixedKeyCode, inputSource, false, vendorId, productId)) return true;
        return super.onKeyUp(keyCode, keyEvent);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent motionEvent) {
        if (this.m_motionEventsDisabled) return true;
        if (isHardwareMouseEvent(motionEvent)) {
            // Hardware-mouse dispatch, parity with stock Sky 0.34.5
            // (410941): stock dispatches on the action and forwards mouse
            // BUTTONS to the game (onButtonPress at inputSource 3) and one
            // synthesized key tap per wheel notch, alongside hover and the
            // scroll delta. Dropping the button cases means no
            // hardware-mouse click ever reaches the game.
            int vendorId = getDeviceVendorId(motionEvent);
            int productId = getDeviceProductId(motionEvent);
            if (isGamepadWithTouchpadEvent(motionEvent)) {
                onGamepadConnected(vendorId, productId);
                if (Build.VERSION.SDK_INT < 31) {
                    onCapturedPointer(getBridgeView(), motionEvent);
                }
                return true;
            }
            // Mouse-to-ImGui: keep the overlay's pointer position fresh - it
            // powers ImGui hover and the wantsMouse() ownership checks
            // below. Button 0 itself is submitted from dispatchTouchEvent's
            // DOWN/UP, which Android delivers alongside
            // ACTION_BUTTON_PRESS/RELEASE for the primary button, so it is
            // not repeated here.
            submitImGuiMousePosition(motionEvent);
            switch (motionEvent.getAction()) {
                case MotionEvent.ACTION_HOVER_MOVE:
                case MotionEvent.ACTION_HOVER_ENTER:
                case MotionEvent.ACTION_HOVER_EXIT: {
                    PointF point = transformPointToProgram(motionEvent.getX(), motionEvent.getY());
                    onMouseMoved((int) point.x, (int) point.y);
                    m_lastMouseLocation = point;
                    break;
                }
                case MotionEvent.ACTION_SCROLL: {
                    float scrollX = motionEvent.getAxisValue(MotionEvent.AXIS_HSCROLL);
                    float scrollY = motionEvent.getAxisValue(MotionEvent.AXIS_VSCROLL);
                    if (ImGUI.wantsMouse()) {
                        // Over the overlay the wheel scrolls ImGui and never
                        // reaches the game - no camera zoom behind the menu.
                        ImGUI.submitScrollEvent(scrollX, scrollY);
                        break;
                    }
                    // One synthesized key tap per notch, exactly as stock:
                    // wheel down -> key 11, wheel up -> key 10, inputSource 3.
                    int key = scrollY < 0.0f ? 11 : (scrollY > 0.0f ? 10 : 0);
                    if (key != 0) {
                        onButtonPress(key, 3, true, vendorId, productId);
                        onButtonPress(key, 3, false, vendorId, productId);
                    }
                    onMouseScrollingDelta(scrollX, scrollY);
                    break;
                }
                case MotionEvent.ACTION_BUTTON_PRESS:
                    // wantsMouse() keeps clicks over the overlay away from
                    // the game; a press that began on the game keeps its
                    // release too (MouseDownOwned makes wantsMouse() stay
                    // false for that whole drag, even ending over ImGui).
                    if (!ImGUI.wantsMouse() && !isEventInTextField(motionEvent)) {
                        onButtonPress(motionEvent.getActionButton(), 3, true, vendorId, productId);
                    }
                    break;
                case MotionEvent.ACTION_BUTTON_RELEASE:
                    if (!ImGUI.wantsMouse() && !isEventInTextField(motionEvent)) {
                        onButtonPress(motionEvent.getActionButton(), 3, false, vendorId, productId);
                    }
                    break;
                default:
                    break;
            }
            return true;
        }
        if (isGamepadEvent(motionEvent)) {
            int vendorId = getDeviceVendorId(motionEvent);
            int productId = getDeviceProductId(motionEvent);
            float axisValue = motionEvent.getAxisValue(17);
            float axisValue2 = motionEvent.getAxisValue(18);
            if (Float.compare(axisValue, 0.0f) == 0) axisValue = motionEvent.getAxisValue(23);
            if (Float.compare(axisValue2, 0.0f) == 0) axisValue2 = motionEvent.getAxisValue(22);
            boolean z = Float.compare(axisValue, 1.0f) == 0;
            boolean z2 = Float.compare(axisValue2, 1.0f) == 0;
            if (z != this.m_lTriggerPressed) onButtonPress(104, 1, z, vendorId, productId);
            if (z2 != this.m_rTriggerPressed) onButtonPress(105, 1, z2, vendorId, productId);
            if (z != this.m_lTriggerPressed || z2 != this.m_rTriggerPressed) {
                this.m_lTriggerPressed = z;
                this.m_rTriggerPressed = z2;
                return true;
            }
            float axisValue3 = motionEvent.getAxisValue(15);
            float axisValue4 = motionEvent.getAxisValue(16);
            int i;
            if (Float.compare(axisValue3, -1.0f) == 0) i = 21;
            else if (Float.compare(axisValue3, 1.0f) == 0) i = 22;
            else if (Float.compare(axisValue4, -1.0f) == 0) i = 19;
            else i = Float.compare(axisValue4, 1.0f) == 0 ? 20 : 23;
            int i2 = this.m_lastDpadDirection;
            if (i != i2) {
                if (i2 != 23) onButtonPress(i2, 1, false, vendorId, productId);
                if (i != 23) onButtonPress(i, 1, true, vendorId, productId);
                this.m_lastDpadDirection = i;
                return true;
            }
            if ((motionEvent.getSource() & 16777232) == 16777232 &&
                (motionEvent.getAction() & KotlinVersion.MAX_COMPONENT_VALUE) == 2) {
                float lx = motionEvent.getAxisValue(0);
                float ly = motionEvent.getAxisValue(1);
                float rx = motionEvent.getAxisValue(11);
                float ry = motionEvent.getAxisValue(14);
                if (Math.abs(lx) > 0.02f || Math.abs(ly) > 0.02f ||
                    Math.abs(rx) > 0.02f || Math.abs(ry) > 0.02f) {
                    setPointerCapture(true);
                    setGamepadProductTypeNative(vendorId, productId);
                }
                onStickEventNative(lx, ly, rx, ry);
                return true;
            }
        }
        return super.onGenericMotionEvent(motionEvent);
    }

    public void addActivePanel(BasePanel basePanel) {
        if (this.mActivePanels == null) this.mActivePanels = new ArrayList<>();
        if (!this.mActivePanels.contains(basePanel)) this.mActivePanels.add(basePanel);
    }

    public void removeActivePanel(BasePanel basePanel) {
        ArrayList<BasePanel> a = this.mActivePanels;
        if (a != null) a.remove(basePanel);
    }

    public void dismissAllPanels() {
        ArrayList<BasePanel> a = this.mActivePanels;
        if (a != null) { for (BasePanel bp : a) bp.dismiss(); }
    }

    private void tryEnablingDisplayCutoutMode() {
        if (Build.VERSION.SDK_INT >= 28) {
            View decorView = getWindow().getDecorView();
            if (decorView == null) return;
            WindowManager.LayoutParams attrs = getWindow().getAttributes();
            attrs.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(attrs);
        }
    }

    public RelativeLayout getBridgeView() { return this.m_relativeLayout; }
    public Rect GetSafeAreaInsets() { return this.mSafeAreaInsets; }
    public static void hideNavigationFullScreen(View view) { view.setSystemUiVisibility(5894); }
    public static void showNavigationFullScreen(View view) { view.setSystemUiVisibility(1792); }
    public void onAudioDeviceTypeChanged(AudioDeviceType audioDeviceType) { onAudioDeviceTypeChangedNative(audioDeviceType.ordinal()); }
    public void onCommerceUpdate(boolean z, boolean z2, boolean z3) { onCommerceUpdateNative(z, z2, z3); }
    public String ResolveTemplateArgs(String str) { return ResolveTemplateArgsNative(str); }

    public void transformPointToSystem(float f, float f2, RectF rectF) {
        rectF.left += f; rectF.right += f;
        float h = ((float) getWindow().getDecorView().getHeight()) - f2;
        rectF.top += h; rectF.bottom += h;
    }

    public RectF transformRectToSystem(RectF rectF) { return new RectF(rectF); }
    public RectF transformRectToProgram(RectF rectF) { return new RectF(rectF); }
    public String getAppId() { return getApplicationInfo().packageName; }
    public String getAppName() { return "Sky"; }
    public String getAppProgramLibDir() { return getApplicationInfo().nativeLibraryDir; }

    public String getOpenedWithURL() {
        Intent intent = getIntent();
        if ("android.intent.action.VIEW".equals(intent.getAction())) return intent.getDataString();
        return null;
    }

    public String getOpenedWithNFC() {
        Intent intent = getIntent();
        if ("android.nfc.action.NDEF_DISCOVERED".equals(intent.getAction())) return intent.getDataString();
        return null;
    }

    public void setDisplayWidth(int i) { this.m_nativeWidth = i; }
    public int getDisplayWidth() { return this.m_nativeWidth; }
    public void setDisplayHeight(int i) { this.m_nativeHeight = i; }
    public int getDisplayHeight() { return this.m_nativeHeight; }

    public float getDisplaySizeInInches() {
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(dm);
        float h = ((float) dm.heightPixels) / dm.ydpi;
        float w = ((float) dm.widthPixels) / dm.xdpi;
        return (float) Math.sqrt((double) (w * w + h * h));
    }

    public float getDisplayXdpi() { return SMLApplication.skyRes.getDisplayMetrics().xdpi; }
    public float getDisplayYdpi() { return SMLApplication.skyRes.getDisplayMetrics().ydpi; }
    public float getDisplayDensity() { return SMLApplication.skyRes.getDisplayMetrics().density; }
    public boolean isScreenHdr() { return SMLApplication.skyRes.getConfiguration().isScreenHdr(); }
    public boolean isScreenWideColorGamut() { return SMLApplication.skyRes.getConfiguration().isScreenWideColorGamut(); }
    public float getDesiredMinLum() {
        Display display = getWindowManager().getDefaultDisplay();
        if (display != null && display.getHdrCapabilities() != null) {
            return display.getHdrCapabilities().getDesiredMinLuminance();
        }
        return 0.0f;
    }
    public float getDesiredMaxLum() {
        Display display = getWindowManager().getDefaultDisplay();
        if (display != null && display.getHdrCapabilities() != null) {
            return display.getHdrCapabilities().getDesiredMaxLuminance();
        }
        return 0.0f;
    }
    public float getDisplayRefreshRate() {
        Display display = getWindowManager().getDefaultDisplay();
        return display != null ? display.getRefreshRate() : 60.0f;
    }
    public float getDisplayMaxRefreshRate() {
        Display defaultDisplay = getWindowManager().getDefaultDisplay();
        if (defaultDisplay == null) return 60.0f;
        Display.Mode mode = defaultDisplay.getMode();
        float refreshRate = mode != null ? mode.getRefreshRate() : defaultDisplay.getRefreshRate();
        Display.Mode[] supportedModes = defaultDisplay.getSupportedModes();
        if (supportedModes != null && mode != null) {
            for (Display.Mode mode2 : supportedModes) {
                if (mode2 != null && mode2.getPhysicalWidth() == mode.getPhysicalWidth() && mode2.getPhysicalHeight() == mode.getPhysicalHeight()) {
                    refreshRate = Math.max(refreshRate, mode2.getRefreshRate());
                }
            }
        }
        return refreshRate;
    }

    public int getPhysicalMemorySize() {
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        ((ActivityManager) getBaseContext().getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(memoryInfo);
        return (int) (memoryInfo.totalMem / 1024);
    }

    public String getDeviceName() {
        String s = Settings.Global.getString(getContentResolver(), "device_name");
        if (s == null || s.isEmpty()) s = Settings.Secure.getString(getContentResolver(), "bluetooth_name");
        return (s == null || s.isEmpty()) ? "NO_DEVICE_NAME" : s;
    }

    public String getDeviceModel() {
        String str = Build.MANUFACTURER;
        String str2 = Build.MODEL;
        if (!str.isEmpty()) str = str + " ";
        return str + str2;
    }

    public String getDeviceDescriptionJson(String str) {
        try {
            JSONObject o = new JSONObject();
            o.put("brand", Build.MANUFACTURER);
            o.put("model", Build.MODEL);
            JSONObject o2 = new JSONObject();
            o2.put("build_brand", Build.BRAND);
            o2.put("build_device", Build.DEVICE);
            o2.put("build_product", Build.PRODUCT);
            o2.put("gpu", str);
            o.put("device_extra", o2);
            return o.toString();
        } catch (JSONException e) {
            Log.e(TAG, "Failed to generate deviceDescriptionJson", e);
            return "{}";
        }
    }

    public byte[] getDeviceUuid() {
        @SuppressLint("HardwareIds")
        String s = Settings.Secure.getString(getContentResolver(), "android_id");
        if (s.length() < 16) s = new String(new char[16 - s.length()]).replace('\0', '0') + s;
        byte[] bArr = new byte[s.length() / 2];
        for (int i = 0; i < s.length(); i += 2)
            bArr[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
        return bArr;
    }

    public void playLogoSound() {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am.isMusicActive()) return;
        MediaPlayer player = new MediaPlayer();
        try {
            player.setDataSource(SMLApplication.skyRes.openRawResourceFd(
                SMLApplication.skyRes.getIdentifier("tgc_logo", "raw", SMLApplication.skyPName)));
            player.prepare();
            (m_mediaPlayer = player).start();
        } catch (IOException e) { e.printStackTrace(); }
    }

    public boolean tryReleaseLogoSound() {
        if (m_mediaPlayer == null) { this.m_logoSoundReleased = true; return true; }
        if (!this.m_logoSoundReleased) {
            if (this.m_mediaPlayer.isPlaying()) return false;
            this.m_mediaPlayer.release();
            this.m_logoSoundReleased = true;
        }
        return true;
    }

    public void fadeoutLogos() {
        runOnUiThread(() -> {
            AlphaAnimation anim = new AlphaAnimation(1.0f, 0.0f);
            anim.setInterpolator(new AccelerateInterpolator());
            anim.setDuration(1000);
            anim.setAnimationListener(new Animation.AnimationListener() {
                @Override public void onAnimationStart(Animation a) {}
                @Override public void onAnimationEnd(Animation a) {
                    logoView.setVisibility(View.GONE);
                    git.artdeell.skymodloader.MainActivity.lateInitUserLibs();
                }
                @Override public void onAnimationRepeat(Animation a) {}
            });
            logoView.startAnimation(anim);
        });
    }

    public void pressBackButton() { moveTaskToBack(true); }

    public void finishActivity() {
        finishAndRemoveTask();
        new Timer().schedule(new TimerTask() {
            public void run() { System.exit(0); }
        }, 5000);
    }
}
