package com.tgc.sky;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.hardware.HardwareBuffer;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Debug;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;
import com.tgc.sky.io.AudioDeviceType;
import com.tgc.sky.io.DeviceKey;
import com.tgc.sky.io.NFCSessionManager;
import com.tgc.sky.ui.NtVideoRecorder;

import git.artdeell.skymodloader.server.ServerManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.nio.ByteBuffer;



public class SystemIO_android {
    private static final String TAG = "SystemIO_android";
    private static volatile SystemIO_android sInstance;
    private ArrayList<Object> mAppLaunchNotificationMessages;
    private NFCSessionManager mNFCSessionManager;
    private ArrayList<Object> mNotificationMessages;
    private Lock mNotificationMessagesLock;
    public GameActivity m_activity;
    private boolean m_batteryCharging;
    private ExoplayerService mExoplayer;
    private int mExoplayerVideoCounter = 0;
    float mExoplayerPauseVolume = 1.0f;
    boolean mIsPaused = false;
    private Future<String> mSubtitleRequest;
    public float m_batteryLevel;
    private boolean m_isOtherAudioPlaying;
    private boolean m_isPhonecallActive;
    private boolean m_networkReachableByCellular;
    private boolean m_networkReachableByWifi;
    private String m_pushNotificationToken;
    private SystemIOBroadcastReceiver m_receiver = new SystemIOBroadcastReceiver();
    private SharedPreferences mUserPrefs;
    private NtVideoRecorder m_videoRecorder;

    private boolean m_isRecording = false;
    private boolean m_isGameRecorder = false;
    VideoFrameResultRef m_VFRRef = new VideoFrameResultRef();
    AudioFrameResultRef m_AFRRef = new AudioFrameResultRef();

    enum VideoCodec {
        kVideoCodecH264,
        kVideoCodecHEVC
    }


    enum UserPreferenceBoolKey {
        kUserPreference_WideScreenHint,
        kUserPreference_PortraitModeAllowed,
        kUserPreference_AskedToEnableHighResScreenshots,
        kUserPreference_EnableHighResScreenshots,
        kUserPreference_ParentalDisableChat,
        kUserPreference_ParentalDisableShop,
        kUserPreference_ProControls,
        kUserPreference_LeftHanded,
        kUserPreference_MixedCameraMode,
        kUserPreference_InvertCamera,
        kUserPreference_InvertFlight,
        kUserPreference_EnableHaptics,
        kUserPreference_InvertCameraGamepad,
        kUserPreference_InvertFlightGamepad,
        kUserPreference_EnableHapticsGamepad,
        kUserPreference_InvertCameraDesktop,
        kUserPreference_InvertFlightDesktop,
        kUserPreference_ReturningPlayer,
        kUserPreference_Instrument_RemoteNotes_Freeplay,
        kUserPreference_Instrument_RemoteNotes_JoinedSheet,
        kUserPreference_DisableCrossPlay,
        kUserPreference_EnableDarkMode,
        kUserPreference_Fullscreen,
        kUserPreference_AutoSkipCutscenes,
        kUserPreference_VideoSubtitles

    }

    enum UserPreferenceFloatKey {
        kUserPreference_MasterVolume,
        kUserPreference_MusicVolume,
        kUserPreference_SfxVolume,
        kUserPreference_InstrumentVolume,
        kUserPreference_VideoVolume,
        kUserPreference_HdrBrightness,
        kUserPreference_HdrUIBrightness,
        kUserPreference_HdrBlackLevel,
        kUserPreference_MotionBlurScalar,
        kUserPreference_CameraRotationSpeed,
        kUserPreference_CameraZoomSpeed

    }

    enum UserPreferenceIntKey {
        kUserPreference_TimeOffset,
        kUserPreference_AgreedTOSVersion,
        kUserPreference_InstrumentLayout,
        kUserPreference_AltInstrumentLayout,
        kUserPreference_QualityFps

    }

    public enum UserPreferenceStringKey {
        kUserPreference_Version,
        kUserPreference_UserId,
        kUserPreference_LastSessionId,
        kUserPreference_QualityOption,
        kUserPreference_AccountLinkPromptDismissedEpoch,
        kUserPreference_DeviceCapability_HashId,
        kUserPreference_DeviceCapability_GpuRating,
        kUserPreference_LastPlayedAccountType,
        kUserPreference_AutomatedTestData,
        kUserPreference_DevicePublicKey,
        kUserPreference_DevicePrivateKey,
        kUserPreference_DisplayPreferenceData,
        kUserPreference_FirstOpenTimestamp,
        kUserPreference_ButtonMapping,
        kUserPreference_StartLevel

        }


    public boolean GetResourceBundlesEnabled() {
        return true;
    }

    public native void OnPushNotificationToken(String str);

    public native void SetAssetManager(AssetManager assetManager);


    public static SystemIO_android getInstance() {
        return sInstance;
    }

    SystemIO_android(GameActivity gameActivity) {
        this.m_activity = gameActivity;
		DeviceKey.setContext(gameActivity.getApplicationContext());
        boolean z = false;
        this.m_isOtherAudioPlaying = false;
        this.m_isPhonecallActive = false;
        this.m_batteryCharging = false;
        this.m_batteryLevel = 1.0f;
        m_initializeAudioSystem();
        Intent registerReceiver = this.m_activity.getApplicationContext().registerReceiver(new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                SystemIO_android.notificationHandler(context, SystemIO_android.getInstance(), intent, "Battery");
            }
        }, new IntentFilter("android.intent.action.BATTERY_CHANGED"));
        this.m_batteryLevel = ((float) registerReceiver.getIntExtra("level", 0)) / ((float) registerReceiver.getIntExtra("scale", 100));
        int intExtra = registerReceiver.getIntExtra("status", -1);
        this.m_batteryCharging = (intExtra == 2 || intExtra == 5) ? true : z;
        this.mNotificationMessages = new ArrayList<>();
        this.mAppLaunchNotificationMessages = new ArrayList<>();
        this.mNotificationMessagesLock = new ReentrantLock();
        sInstance = this;
        this.mNFCSessionManager = new NFCSessionManager(gameActivity);
        this.m_videoRecorder = new NtVideoRecorder(this.m_activity);
        this.mUserPrefs = m_activity.getSharedPreferences("user", Context.MODE_PRIVATE);
    }

    /* access modifiers changed from: package-private */
    public void onResume() {
        this.mNFCSessionManager.onResume();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.net.wifi.WIFI_STATE_CHANGED");
        intentFilter.addAction("android.net.wifi.STATE_CHANGE");
        intentFilter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
        this.m_activity.registerReceiver(this.m_receiver, intentFilter);
        m_onAudioDeviceChanged((AudioManager) this.m_activity.getSystemService(Context.AUDIO_SERVICE), (Intent) null);
        ExoplayerService exoplayerService = this.mExoplayer;
        if (exoplayerService != null) {
            exoplayerService.SetVolume(this.mExoplayerPauseVolume);
        }
        this.mIsPaused = false;

    }

    /* access modifiers changed from: package-private */
    public void onPause() {
        this.m_activity.unregisterReceiver(this.m_receiver);
        this.mIsPaused = true;
        ExoplayerService exoplayerService = this.mExoplayer;
        if (exoplayerService != null) {
            exoplayerService.SetVolume(0.0f);
        }

    }

    /* access modifiers changed from: package-private */
    public void onDestroy() {
    }

    public int GetPlatformInt() {
        return 0;
    }

    public class SystemIOBroadcastReceiver extends BroadcastReceiver {
        public SystemIOBroadcastReceiver() {
        }

        public void onReceive(Context context, Intent intent) {
            SystemIO_android.this.m_updateReachabilityFlags(context, intent);
        }
    }

    /* access modifiers changed from: package-private */
    public void m_updateReachabilityFlags(Context context, Intent intent) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public void RequestResourceBundle(String str) {
    }

    public boolean ResourceBundleShouldDownload(String str) {
        return false;
    }

    public int GetResourceBundleState(String str) {
        return 4; //ResourceBundleState.READY
    }

    public long[] ResourceBundleDownloadProgress(String str) {
        return new long[]{0, 0};
    }

    public String GetDirectFileAccessPath(String str) {
        return null;
    }

    public String GetPushNotificationToken() {
        return this.m_pushNotificationToken;
    }

    public void RetrievePushNotificationToken() {
    }

    public static void OnPushNotificationTokenJava(String str) {
        SystemIO_android instance = getInstance();
        if (instance != null) {
            instance.m_pushNotificationToken = str;
            instance.OnPushNotificationToken(str);
        }
    }

    public void OnNotificationMessage(String str) {
        this.mNotificationMessagesLock.lock();
        this.mNotificationMessages.add(str);
        this.mNotificationMessagesLock.unlock();
    }

    public void OnAppLaunchNotificationMessage(String str) {
        this.mNotificationMessagesLock.lock();
        this.mAppLaunchNotificationMessages.add(str);
        this.mNotificationMessagesLock.unlock();
    }

    /* access modifiers changed from: package-private */
    public String GetNotificationMessage() throws UnsupportedEncodingException {
        if (this.mNotificationMessages.size() <= 0) {
            return null;
        }
        this.mNotificationMessagesLock.lock();
        String str = (String) this.mNotificationMessages.get(0);
        this.mNotificationMessages.remove(0);
        this.mNotificationMessagesLock.unlock();
        return str;
    }

    /* access modifiers changed from: package-private */
    public String GetAppLaunchNotificationMessage() {
        if (this.mAppLaunchNotificationMessages.size() <= 0) {
            return null;
        }
        this.mNotificationMessagesLock.lock();
        String str = (String) this.mAppLaunchNotificationMessages.get(0);
        this.mAppLaunchNotificationMessages.remove(0);
        this.mNotificationMessagesLock.unlock();
        return str;
    }

    public boolean IsOtherAudioPlaying() {
        return this.m_isOtherAudioPlaying;
    }

    public boolean IsPhonecallActive() {
        return this.m_isPhonecallActive;
    }

    public boolean IsNetworkReachableByWifi() {
        return this.m_networkReachableByWifi;
    }

    public boolean IsNetworkReachableByCellular() {
        return this.m_networkReachableByCellular;
    }

    /* access modifiers changed from: package-private */
    public boolean DeleteDeviceKey() {
        return DeviceKey.Delete();
    }

    /* access modifiers changed from: package-private */
    public String GetDeviceKey() {
        return DeviceKey.GetPublicKeyAsBase64();
    }

    /* access modifiers changed from: package-private */
    public String SignWithDeviceKey(String str) {
        return DeviceKey.Sign(str);
    }

    /* access modifiers changed from: package-private */
    public boolean VerifyWithDeviceKey(String str, String str2) {
        return DeviceKey.VerifySignature(str, str2);
    }

    /* access modifiers changed from: package-private */
    public boolean VerifyWithPublicKey(String str, String str2, String str3) {
        return DeviceKey.VerifyWithPublicKeyAndSignature(str, str2, str3);
    }


    public boolean StartRecording() {
        return false;
    }

    public static /* synthetic */ class C11334 {
        static final /* synthetic */ int[] $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceBoolKey;
        static final /* synthetic */ int[] $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceFloatKey;
        static final /* synthetic */ int[] $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceIntKey;
        static final /* synthetic */ int[] $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceStringKey;

        static {
            int[] iArr = new int[UserPreferenceFloatKey.values().length];
            $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceFloatKey = iArr;
            try {
                iArr[UserPreferenceFloatKey.kUserPreference_MasterVolume.ordinal()] = 1;
            } catch (NoSuchFieldError unused) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceFloatKey[UserPreferenceFloatKey.kUserPreference_MusicVolume.ordinal()] = 2;
            } catch (NoSuchFieldError unused2) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceFloatKey[UserPreferenceFloatKey.kUserPreference_SfxVolume.ordinal()] = 3;
            } catch (NoSuchFieldError unused3) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceFloatKey[UserPreferenceFloatKey.kUserPreference_InstrumentVolume.ordinal()] = 4;
            } catch (NoSuchFieldError unused4) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceFloatKey[UserPreferenceFloatKey.kUserPreference_VideoVolume.ordinal()] = 5;
            } catch (NoSuchFieldError unused5) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceFloatKey[UserPreferenceFloatKey.kUserPreference_HdrBrightness.ordinal()] = 6;
            } catch (NoSuchFieldError unused6) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceFloatKey[UserPreferenceFloatKey.kUserPreference_HdrUIBrightness.ordinal()] = 7;
            } catch (NoSuchFieldError unused7) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceFloatKey[UserPreferenceFloatKey.kUserPreference_HdrBlackLevel.ordinal()] = 8;
            } catch (NoSuchFieldError unused8) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceFloatKey[UserPreferenceFloatKey.kUserPreference_MotionBlurScalar.ordinal()] = 9;
            } catch (NoSuchFieldError unused9) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceFloatKey[UserPreferenceFloatKey.kUserPreference_CameraRotationSpeed.ordinal()] = 10;
            } catch (NoSuchFieldError unused10) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceFloatKey[UserPreferenceFloatKey.kUserPreference_CameraZoomSpeed.ordinal()] = 11;
            } catch (NoSuchFieldError unused11) {
            }
            int[] iArr2 = new int[UserPreferenceStringKey.values().length];
            $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceStringKey = iArr2;
            try {
                iArr2[UserPreferenceStringKey.kUserPreference_Version.ordinal()] = 1;
            } catch (NoSuchFieldError unused12) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceStringKey[UserPreferenceStringKey.kUserPreference_UserId.ordinal()] = 2;
            } catch (NoSuchFieldError unused13) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceStringKey[UserPreferenceStringKey.kUserPreference_LastSessionId.ordinal()] = 3;
            } catch (NoSuchFieldError unused14) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceStringKey[UserPreferenceStringKey.kUserPreference_QualityOption.ordinal()] = 4;
            } catch (NoSuchFieldError unused15) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceStringKey[UserPreferenceStringKey.kUserPreference_AccountLinkPromptDismissedEpoch.ordinal()] = 5;
            } catch (NoSuchFieldError unused16) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceStringKey[UserPreferenceStringKey.kUserPreference_DeviceCapability_HashId.ordinal()] = 6;
            } catch (NoSuchFieldError unused17) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceStringKey[UserPreferenceStringKey.kUserPreference_DeviceCapability_GpuRating.ordinal()] = 7;
            } catch (NoSuchFieldError unused18) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceStringKey[UserPreferenceStringKey.kUserPreference_LastPlayedAccountType.ordinal()] = 8;
            } catch (NoSuchFieldError unused19) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceStringKey[UserPreferenceStringKey.kUserPreference_AutomatedTestData.ordinal()] = 9;
            } catch (NoSuchFieldError unused20) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceStringKey[UserPreferenceStringKey.kUserPreference_DevicePublicKey.ordinal()] = 10;
            } catch (NoSuchFieldError unused21) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceStringKey[UserPreferenceStringKey.kUserPreference_DevicePrivateKey.ordinal()] = 11;
            } catch (NoSuchFieldError unused22) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceStringKey[UserPreferenceStringKey.kUserPreference_DisplayPreferenceData.ordinal()] = 12;
            } catch (NoSuchFieldError unused23) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceStringKey[UserPreferenceStringKey.kUserPreference_FirstOpenTimestamp.ordinal()] = 13;
            } catch (NoSuchFieldError unused24) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceStringKey[UserPreferenceStringKey.kUserPreference_ButtonMapping.ordinal()] = 14;
            } catch (NoSuchFieldError unused25) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceStringKey[UserPreferenceStringKey.kUserPreference_StartLevel.ordinal()] = 15;
            } catch (NoSuchFieldError unused26) {
            }
            int[] iArr3 = new int[UserPreferenceIntKey.values().length];
            $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceIntKey = iArr3;
            try {
                iArr3[UserPreferenceIntKey.kUserPreference_TimeOffset.ordinal()] = 1;
            } catch (NoSuchFieldError unused27) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceIntKey[UserPreferenceIntKey.kUserPreference_AgreedTOSVersion.ordinal()] = 2;
            } catch (NoSuchFieldError unused28) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceIntKey[UserPreferenceIntKey.kUserPreference_InstrumentLayout.ordinal()] = 3;
            } catch (NoSuchFieldError unused29) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceIntKey[UserPreferenceIntKey.kUserPreference_AltInstrumentLayout.ordinal()] = 4;
            } catch (NoSuchFieldError unused30) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceIntKey[UserPreferenceIntKey.kUserPreference_QualityFps.ordinal()] = 5;
            } catch (NoSuchFieldError unused31) {
            }
            int[] iArr4 = new int[UserPreferenceBoolKey.values().length];
            $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceBoolKey = iArr4;
            try {
                iArr4[UserPreferenceBoolKey.kUserPreference_WideScreenHint.ordinal()] = 1;
            } catch (NoSuchFieldError unused32) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceBoolKey[UserPreferenceBoolKey.kUserPreference_PortraitModeAllowed.ordinal()] = 2;
            } catch (NoSuchFieldError unused33) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceBoolKey[UserPreferenceBoolKey.kUserPreference_AskedToEnableHighResScreenshots.ordinal()] = 3;
            } catch (NoSuchFieldError unused34) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceBoolKey[UserPreferenceBoolKey.kUserPreference_EnableHighResScreenshots.ordinal()] = 4;
            } catch (NoSuchFieldError unused35) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceBoolKey[UserPreferenceBoolKey.kUserPreference_ParentalDisableChat.ordinal()] = 5;
            } catch (NoSuchFieldError unused36) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceBoolKey[UserPreferenceBoolKey.kUserPreference_ParentalDisableShop.ordinal()] = 6;
            } catch (NoSuchFieldError unused37) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceBoolKey[UserPreferenceBoolKey.kUserPreference_ProControls.ordinal()] = 7;
            } catch (NoSuchFieldError unused38) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceBoolKey[UserPreferenceBoolKey.kUserPreference_LeftHanded.ordinal()] = 8;
            } catch (NoSuchFieldError unused39) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceBoolKey[UserPreferenceBoolKey.kUserPreference_MixedCameraMode.ordinal()] = 9;
            } catch (NoSuchFieldError unused40) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceBoolKey[UserPreferenceBoolKey.kUserPreference_InvertCamera.ordinal()] = 10;
            } catch (NoSuchFieldError unused41) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceBoolKey[UserPreferenceBoolKey.kUserPreference_InvertFlight.ordinal()] = 11;
            } catch (NoSuchFieldError unused42) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceBoolKey[UserPreferenceBoolKey.kUserPreference_EnableHaptics.ordinal()] = 12;
            } catch (NoSuchFieldError unused43) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceBoolKey[UserPreferenceBoolKey.kUserPreference_InvertCameraGamepad.ordinal()] = 13;
            } catch (NoSuchFieldError unused44) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceBoolKey[UserPreferenceBoolKey.kUserPreference_InvertFlightGamepad.ordinal()] = 14;
            } catch (NoSuchFieldError unused45) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceBoolKey[UserPreferenceBoolKey.kUserPreference_EnableHapticsGamepad.ordinal()] = 15;
            } catch (NoSuchFieldError unused46) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceBoolKey[UserPreferenceBoolKey.kUserPreference_InvertCameraDesktop.ordinal()] = 16;
            } catch (NoSuchFieldError unused47) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceBoolKey[UserPreferenceBoolKey.kUserPreference_InvertFlightDesktop.ordinal()] = 17;
            } catch (NoSuchFieldError unused48) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceBoolKey[UserPreferenceBoolKey.kUserPreference_ReturningPlayer.ordinal()] = 18;
            } catch (NoSuchFieldError unused49) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceBoolKey[UserPreferenceBoolKey.kUserPreference_Instrument_RemoteNotes_Freeplay.ordinal()] = 19;
            } catch (NoSuchFieldError unused50) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceBoolKey[UserPreferenceBoolKey.kUserPreference_Instrument_RemoteNotes_JoinedSheet.ordinal()] = 20;
            } catch (NoSuchFieldError unused51) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceBoolKey[UserPreferenceBoolKey.kUserPreference_DisableCrossPlay.ordinal()] = 21;
            } catch (NoSuchFieldError unused52) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceBoolKey[UserPreferenceBoolKey.kUserPreference_EnableDarkMode.ordinal()] = 22;
            } catch (NoSuchFieldError unused53) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceBoolKey[UserPreferenceBoolKey.kUserPreference_Fullscreen.ordinal()] = 23;
            } catch (NoSuchFieldError unused54) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceBoolKey[UserPreferenceBoolKey.kUserPreference_AutoSkipCutscenes.ordinal()] = 24;
            } catch (NoSuchFieldError unused55) {
            }
            try {
                $SwitchMap$com$tgc$sky$LocalPreferences$UserPreferenceBoolKey[UserPreferenceBoolKey.kUserPreference_VideoSubtitles.ordinal()] = 25;
            } catch (NoSuchFieldError unused56) {
            }

        }
    }

    boolean StartRecording(String str, int i, int i2, int i3, boolean z, int i4, boolean z2, int i5, boolean z3) {
    this.m_isGameRecorder = true;
    String str2 = C115312.$SwitchMap$com$tgc$sky$SystemIO_android$VideoCodec[VideoCodec.values()[i3].ordinal()] != 2 ? "video/avc" : "video/hevc";
    this.m_isRecording = true;
    return this.m_videoRecorder.startRecordingWithFilename(str, i, i2, str2, i4, z2, i5, z3);
	}


    static /* synthetic */ class C115312 {
        static final /* synthetic */ int[] $SwitchMap$com$tgc$sky$SystemIO_android$VideoCodec;

        static {
            int[] iArr = new int[VideoCodec.values().length];
            $SwitchMap$com$tgc$sky$SystemIO_android$VideoCodec = iArr;
            try {
                iArr[VideoCodec.kVideoCodecH264.ordinal()] = 1;
            } catch (NoSuchFieldError unused) {
            }
            try {
                $SwitchMap$com$tgc$sky$SystemIO_android$VideoCodec[VideoCodec.kVideoCodecHEVC.ordinal()] = 2;
            } catch (NoSuchFieldError unused2) {
            }
        }
    }

    boolean StopRecording() {
        return this.m_videoRecorder.stopRecording();
    }

    boolean IsRecording() {
        return this.m_videoRecorder.isRecording();
    }

    // TODO: Implement speech recognition
    boolean SupportsSpeechRecognition() {
        return false; //this.m_speechRecognizer.supportsRecognition();
    }

    int StartSpeechRecognition(int i, int i2) {
        return 0; //this.m_speechRecognizer.startRecognition(i, i2);
    }

    boolean StopSpeechRecognition() {
        return false;//this.m_speechRecognizer.stopRecognition();
    }

    boolean IsSpeechRecognitionIdActive(int i) {
        return false;// this.m_speechRecognizer.isIdActive(i);
    }

    boolean IsSpeechRecognitionActive() {
        return false;// this.m_speechRecognizer.isActive();
    }

    boolean IsSpeechRecognitionRecording() {
        return false; // this.m_speechRecognizer.isRecording();
    }

    String GetSpeechRecognitionTranscription() {
        return "[[[not supported]]]";// this.m_speechRecognizer.getTranscription();
    }


    /* access modifiers changed from: package-private */
    public float BatteryLevel() {
        return this.m_batteryLevel;
    }

    /* access modifiers changed from: package-private */
    public boolean IsBatteryCharging() {
        return this.m_batteryCharging;
    }

    static void notificationHandler(Context context, Object obj, Intent intent, String str) {
        SystemIO_android systemIO_android = (SystemIO_android) obj;
        if ("Battery".equals(str)) {
            boolean z = false;
            systemIO_android.m_batteryLevel = ((float) intent.getIntExtra("level", 0)) / ((float) intent.getIntExtra("scale", 100));
            int intExtra = intent.getIntExtra("status", -1);
            if (intExtra == 2 || intExtra == 5) {
                z = true;
            }
            systemIO_android.m_batteryCharging = z;
        }
    }

    private void m_initializeAudioSystem() {
        final AudioManager audioManager = (AudioManager) this.m_activity.getSystemService(Context.AUDIO_SERVICE);
        this.m_isOtherAudioPlaying = audioManager.isMusicActive();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.media.AUDIO_BECOMING_NOISY");
        intentFilter.addAction("android.intent.action.HEADSET_PLUG");
        intentFilter.addAction("android.media.action.HDMI_AUDIO_PLUG");
        m_onAudioDeviceChanged(audioManager, this.m_activity.getApplicationContext().registerReceiver(new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                SystemIO_android.this.m_onAudioDeviceChanged(audioManager, intent);
            }
        }, intentFilter));
    }

    /* access modifiers changed from: private */
    public void m_onAudioDeviceChanged(AudioManager audioManager, Intent intent) {
        if (intent != null) {
            Log.d(TAG, "DeviceBasedMixing - Intent received: " + intent.getAction());
            if (intent.getAction().equals("android.media.AUDIO_BECOMING_NOISY")) {
                this.m_activity.onAudioDeviceTypeChanged(AudioDeviceType.kAudioDeviceType_BuiltInSpeaker);
                return;
            }
        }
        if (audioManager == null) {
            this.m_activity.onAudioDeviceTypeChanged(AudioDeviceType.kAudioDeviceType_Unknown);
            return;
        }
        AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        for (int i = 0; i < devices.length; i++) {
            AudioDeviceInfo audioDeviceInfo = devices[i];
            Log.d(TAG, "DeviceBasedMixing - [" + i + "]: " + audioDeviceInfo.getProductName() + " -> " + audioDeviceInfo.getType());
        }
        AudioDeviceType audioDeviceType = AudioDeviceType.kAudioDeviceType_Unknown;
        for (AudioDeviceInfo type : devices) {
            int type2 = type.getType();
            if (type2 != 0) {
                if (type2 != 1) {
                    if (type2 == 2) {
                        audioDeviceType = AudioDeviceType.kAudioDeviceType_BuiltInSpeaker;
                    } else if (!(type2 == 3 || type2 == 4 || type2 == 22)) {
                        audioDeviceType = AudioDeviceType.kAudioDeviceType_ExternalDevice;
                    }
                }
                audioDeviceType = AudioDeviceType.kAudioDeviceType_Headphones;
            }
            if (audioDeviceType != AudioDeviceType.kAudioDeviceType_Unknown) {
                break;
            }
        }
        this.m_activity.onAudioDeviceTypeChanged(audioDeviceType);
    }

    private static final byte KEY_TYPE_STRING = 0;
    private static final byte KEY_TYPE_BOOLEAN = 1;
    private static final byte KEY_TYPE_INT = 2;
    private static final byte KEY_TYPE_FLOAT = 3;

    static String getKeyName(byte keyType, int preference) {
        switch (keyType) {
            case KEY_TYPE_STRING:
                switch (preference + 1) {
                    case 1:
                        return "version_preference";
                    case 2:
                        return "user_id_preference";
                    case 3:
                        return "last_session_id_preference";
                    case 4:
                        return "quality_mode_preference";
                    case 5:
                        return "account_link_prompt_dismissed_epoch_preference";
                    case 6:
                        return "device_capability_hash_id";
                    case 7:
                        return "device_capability_gpu_rating";
                    case 8:
                        return "last_played_account_type";
                    case 9:
                        return "automated_test_data";
                    case 10:
                        return "device_public_key";
                    case 11:
                        return "device_private_key";
                    case 12:
                        return "display_preference_data";
                    case 13:
                        return "first_open_ts";
                    case 14:
                        return "button_mapping";
                    case 15:
                        return "start_level";

                    default:
                        return null;
                }
            case KEY_TYPE_BOOLEAN:
                switch (preference + 1) {
                    case 1:
                        return "widescreen_hint";
                    case 2:
                        return "portrait_mode_allowed";
                    case 3:
                        return "asked_enable_highresscreenshot_hidden";
                    case 4:
                        return "enable_highresscreenshot_preference";
                    case 5:
                        return "disabled_chat_preference";
                    case 6:
                        return "disabled_shop_preference";
                    case 7:
                        return "kUserPreference_ProControls";
                    case 8:
                        return "kUserPreference_LeftHanded";
                    case 9:
                        return "kUserPreference_MixedCameraMode";
                    case 10:
                        return "kUserPreference_InvertCamera";
                    case 11:
                        return "kUserPreference_InvertFlight";
                    case 12:
                        return "kUserPreference_EnableHaptics";
                    case 13:
                        return "kUserPreference_InvertCameraGamepad";
                    case 14:
                        return "kUserPreference_InvertFlightGamepad";
                    case 15:
                        return "kUserPreference_EnableHapticsGamepad";
                    case 16:
                        return "kUserPreference_InvertCameraDesktop";
                    case 17:
                        return "kUserPreference_InvertFlightDesktop";
                    case 18:
                        return "kUserPreference_ReturningPlayer";
                    case 19:
                        return "kUserPreference_Instr_RemoteNotes_Freeplay";
                    case 20:
                        return "kUserPreference_Instr_RemoteNotes_Sheet";
                    case 21:
                        return "kUserPreference_DisableCrossPlay";
                    case 22:
                        return "kUserPreference_EnableDarkMode";
                    case 23:
                        return "kUserPreference_Fullscreen";
                    case 24:
                        return "kUserPreference_AutoSkipCutscenes";
                    case 25:
                        return "kUserPreference_VideoSubtitles";
                    default:
                        return null;
                }
            case KEY_TYPE_FLOAT:
                switch (preference + 1) {
                    case 1:
                        return "kUserPreference_MasterVolume";
                    case 2:
                        return "kUserPreference_MusicVolume";
                    case 3:
                        return "kUserPreference_SfxVolume";
                    case 4:
                        return "kUserPreference_InstrumentVolume";
                    case 5:
                        return "kUserPreference_VideoVolume";
                    case 6:
                        return "kUserPreference_HdrBrightness";
                    case 7:
                        return "kUserPreference_HdrUIBrightness";
                    case 8:
                        return "kUserPreference_HdrBlackLevel";
                    case 9:
                        return "kUserPreference_MotionBlurScalar";
                    case 10:
                        return "kUserPreference_CameraRotationSpeed";
                    case 11:
                        return "kUserPreference_CameraZoomSpeed";
                    default:
                        return null;

                }
            case KEY_TYPE_INT:
                switch (preference + 1) {
                    case 1:
                        return "time_offset";
                    case 2:
                        return "agreed_tos_version";
                    case 3:
                        return "instrument_layout";
                    case 4:
                        return "alt_instrument_layout";
                    case 5:
                        return "quality_fps";
                    default:
                        return null;
                }
            default:
                return null;
        }
    }


    public String GetUserPreferenceBool(int i) {
        String _default = "false";
        if (i == 6) _default = "true"; //kUserPreference_ProControls
        return mUserPrefs.getString(getKeyName(KEY_TYPE_BOOLEAN, i), _default);
    }

    public String GetUserPreferenceInt(int i) {
        return mUserPrefs.getString(getKeyName(KEY_TYPE_INT, i), null);
    }

    public String GetUserPreferenceFloat(int i) {
        return mUserPrefs.getString(getKeyName(KEY_TYPE_FLOAT, i), null);
    }

    public String GetUserPreferenceString(int i) {
        if (i == UserPreferenceStringKey.kUserPreference_LastPlayedAccountType.ordinal()
                && ServerManager.isFreeSkyActive(m_activity)) {
            return "Local";
        }
        return mUserPrefs.getString(getKeyName(KEY_TYPE_STRING, i), null);
    }

    public boolean SetUserPreferenceBool(int i, boolean z) {
        return mUserPrefs.edit().putString(getKeyName(KEY_TYPE_BOOLEAN, i), Boolean.toString(z)).commit();
    }

    public boolean SetUserPreferenceInt(int i, int i2) {
        return mUserPrefs.edit().putString(getKeyName(KEY_TYPE_INT, i), Integer.toString(i2)).commit();
    }

    public boolean SetUserPreferenceFloat(int i, float f) {
        return mUserPrefs.edit().putString(getKeyName(KEY_TYPE_FLOAT, i), Float.toString(f)).commit();
    }

    public boolean SetUserPreferenceString(int i, String str) {
        return mUserPrefs.edit().putString(getKeyName(KEY_TYPE_STRING, i), str).commit();
    }

    public void RemoveUserPreferenceBool(int i) {
        mUserPrefs.edit().remove(getKeyName(KEY_TYPE_BOOLEAN, i)).commit();
    }

    public void RemoveUserPreferenceInt(int i) {
        mUserPrefs.edit().remove(getKeyName(KEY_TYPE_INT, i)).commit();
    }

    public void RemoveUserPreferenceFloat(int i) {
        mUserPrefs.edit().remove(getKeyName(KEY_TYPE_FLOAT, i)).commit();
    }

    public void RemoveUserPreferenceString(int i) {
        mUserPrefs.edit().remove(getKeyName(KEY_TYPE_STRING, i)).commit();
    }

    public void SetAppIconBadgeNumber(int i) {

    }

    public long GetFreeDiskSpace() {
        long j = 0;
        if ("mounted".equals(Environment.getExternalStorageState())) {
            StatFs statFs = new StatFs(Environment.getExternalStorageDirectory().getPath());
            j = 0 + (statFs.getBlockCountLong() * statFs.getBlockSizeLong());
        }
        StatFs statFs2 = new StatFs(Environment.getRootDirectory().getPath());
        return j + (statFs2.getBlockCountLong() * statFs2.getBlockSizeLong());
    }

    public String CreateSessionId() {
        return UUID.randomUUID().toString();
    }

    public String GetInstallSource() {
        return "com.android.vending"; //we do trace amnounts of trolling
    }

    public void RequestAppInstallLicense() {

    }

    public boolean IsAppInstallLicenseAvailable() {
        return true;
    }

    public String GetAppInstallLicense() {
        return null;
    }

    public boolean SupportsForegroundScanning() {
        return this.mNFCSessionManager.SupportsForegroundScanning();
    }

    public boolean SupportsBackgroundScanning() {
        return this.mNFCSessionManager.SupportsBackgroundScanning();
    }

    public boolean ScanNFCTag() {
        return this.mNFCSessionManager.ScanNFCTag();
    }

    public boolean ReadNFCTag() {
        return this.mNFCSessionManager.ReadNFCTag();
    }

    public boolean WriteNFCTag(String str) {
        return this.mNFCSessionManager.WriteNFCTag(str);
    }

    VideoFrameResultRef BeginWriteVideoFrame() {
        this.m_VFRRef.outBytesPerRow = 0;
        ByteBuffer beginWriteVideoFrame = this.m_videoRecorder.beginWriteVideoFrame();
        VideoFrameResultRef videoFrameResultRef = this.m_VFRRef;
        videoFrameResultRef.isBufferChange = videoFrameResultRef.returnByteBuffer != beginWriteVideoFrame;
        this.m_VFRRef.returnByteBuffer = beginWriteVideoFrame;
        return this.m_VFRRef;
    }

    void EndWriteVideoFrame() {
        this.m_videoRecorder.endWriteVideoFrame();
    }

    AudioFrameResultRef BeginWriteAudioFrame() {
        this.m_AFRRef.outBytesPerRow = 0;
        ByteBuffer beginWriteAudioFrame = this.m_videoRecorder.beginWriteAudioFrame();
        AudioFrameResultRef audioFrameResultRef = this.m_AFRRef;
        audioFrameResultRef.isBufferChange = audioFrameResultRef.returnByteBuffer != beginWriteAudioFrame;
        this.m_AFRRef.returnByteBuffer = beginWriteAudioFrame;
        return this.m_AFRRef;
    }

    void EndWriteAudioFrame() {
        this.m_videoRecorder.endWriteAudioFrame();
    }

    boolean SupportsVideoPlayback() {
        return Build.VERSION.SDK_INT >= 28;
    }

    int RequestNewStreamId() {
        int i = this.mExoplayerVideoCounter + 1;
        this.mExoplayerVideoCounter = i;
        return i;
    }

    public native void ReceiveVideoBufferNative(HardwareBuffer hardwareBuffer, double d);

    public native void ReceiveVideoMetadataNative(int i, int i2, double d);

    public native void ReceiveVideoSubtitlesNative(String str);

    public native void nativeSetIsGameRecorder(boolean z);

    int CreateMediaPlayerForUrl(final String str, final String str2) {
        this.m_activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (SystemIO_android.this.mExoplayer == null) {
                    SystemIO_android.this.mExoplayer = new ExoplayerService();
                }
                SystemIO_android.this.mExoplayer.Initialize(SystemIO_android.this.m_activity.getApplicationContext());
                SystemIO_android.this.mExoplayer.LoadUrl(str);
                SystemIO_android.this.mExoplayer.Play();
                SystemIO_android.this.mExoplayer.Update();
            }
        });
        Future<String> future = this.mSubtitleRequest;
        if (future != null) {
            future.cancel(true);
            this.mSubtitleRequest = null;
        }
        if (str2 != null && str2.length() > 0) {
            ExecutorService newSingleThreadExecutor = Executors.newSingleThreadExecutor();
            this.mSubtitleRequest = newSingleThreadExecutor.submit(new Callable<String>() {
                @Override
                public String call() throws IOException {
                    HttpURLConnection httpURLConnection = (HttpURLConnection) new URL(str2).openConnection();
                    httpURLConnection.setRequestMethod("GET");
                    try {
                        if (httpURLConnection.getResponseCode() != 200) {
                            return "";
                        }
                        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(httpURLConnection.getInputStream()));
                        StringBuffer stringBuffer = new StringBuffer();
                        while (true) {
                            String readLine = bufferedReader.readLine();
                            if (readLine != null) {
                                stringBuffer.append(readLine + "\n");
                            } else {
                                bufferedReader.close();
                                return stringBuffer.toString();
                            }
                        }
                    } finally {
                        httpURLConnection.disconnect();
                    }
                }
            });
            newSingleThreadExecutor.shutdown();
        }
        return RequestNewStreamId();
    }

    void UpdateMediaPlayer() {
        this.m_activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                SystemIO_android.this.mExoplayer.Update();
                ExoplayerVideoMetadata GetMetadata = SystemIO_android.this.mExoplayer.GetMetadata();
                if (GetMetadata != null) {
                    SystemIO_android.this.ReceiveVideoMetadataNative(GetMetadata.width, GetMetadata.height, GetMetadata.framesPerSecond);
                }
                double GetPlaybackPositionMs = SystemIO_android.this.mExoplayer.GetPlaybackPositionMs() / 1000.0d;
                HardwareBuffer GetNextHardwareBuffer = SystemIO_android.this.mExoplayer.GetNextHardwareBuffer();
                if (GetNextHardwareBuffer != null) {
                    SystemIO_android.this.ReceiveVideoBufferNative(GetNextHardwareBuffer, GetPlaybackPositionMs);
                }
            }
        });
        Future<String> future = this.mSubtitleRequest;
        if (future == null || !future.isDone()) {
            return;
        }
        try {
            ReceiveVideoSubtitlesNative(this.mSubtitleRequest.get());
        } catch (InterruptedException | ExecutionException unused) {
        }
        this.mSubtitleRequest = null;
    }


    void PauseMediaPlayer() {
        this.m_activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                SystemIO_android.this.mExoplayer.Pause();
            }
        });
    }

    void UnpauseMediaPlayer() {
        this.m_activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                SystemIO_android.this.mExoplayer.Play();
            }
        });
    }

    void SeekMediaPlayer(final float f) {
        this.m_activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                SystemIO_android.this.mExoplayer.Seek(Math.round(f * 1000.0d));
            }
        });
    }

    void DestroyMediaPlayer() {
        this.m_activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                SystemIO_android.this.mExoplayer.EndVideo();
            }
        });
    }

    void MediaPlayerSetVolume(final float f) {
        this.mExoplayerPauseVolume = f;
        this.m_activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                SystemIO_android.this.mExoplayer.SetVolume(f);
            }
        });
    }

    public boolean IsDebuggerConnected() {
        return Debug.isDebuggerConnected();
    }

    public void WaitForDebugger() {
        Debug.waitForDebugger();
    }
}
