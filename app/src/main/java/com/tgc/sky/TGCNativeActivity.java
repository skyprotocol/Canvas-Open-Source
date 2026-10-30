package com.tgc.sky;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewTreeObserver;
import java.io.File;

import git.artdeell.skymodloader.SMLApplication;
import git.artdeell.skymodloader.GameRuntime;

/* loaded from: classes2.dex */
public abstract class TGCNativeActivity extends Activity implements SurfaceHolder.Callback2, ViewTreeObserver.OnGlobalLayoutListener {
    private static final String KEY_NATIVE_SAVED_STATE = "android:native_state";
    private SurfaceHolder m_currentSurfaceHolder;
    int m_lastContentHeight;
    int m_lastContentWidth;
    int m_lastContentX;
    int m_lastContentY;
    private boolean m_destroyed = false;
    private boolean m_nativeCreated = false;
    private long m_handle = 0;
    final int[] m_location = new int[2];

    private native void onConfigurationChangedNative(long j);

    private native void onContentRectChangedNative(long j, int i, int i2, int i3, int i4);

    private native long onCreateNative(String str, String str2, String str3, int i, Object obj, byte[] bArr);

    private native void onDestroyNative(long j);

    private native void onLowMemoryNative(long j);

    private native void onPauseNative(long j);

    private native void onResumeNative(long j);

    private native byte[] onSaveInstanceStateNative(long j);

    private native void onStartNative(long j);

    private native void onStopNative(long j);

    private native void onSurfaceChangedNative(long j, Surface surface, int i, int i2, int i3);

    private native void onSurfaceCreatedNative(long j, Surface surface);

    private native void onSurfaceDestroyedNative(long j, Surface surface);

    private native void onSurfaceRedrawNeededNative(long j, Surface surface);

    private native void onWindowFocusChangedNative(long j, boolean z);

    @Override // android.app.Activity
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        if (!GameRuntime.isReady()) {
            GameRuntime.returnToManager(this);
            return;
        }
        findViewById(android.R.id.content).getRootView().getViewTreeObserver().addOnGlobalLayoutListener(this);
        this.m_handle = onCreateNative(getAbsolutePath(getFilesDir()), getAbsolutePath(getObbDir()), getAbsolutePath(getExternalFilesDir(null)), Build.VERSION.SDK_INT, SMLApplication.skyRes.getAssets(), bundle != null ? bundle.getByteArray(KEY_NATIVE_SAVED_STATE) : null);
        m_nativeCreated = true;
    }

    protected final boolean hasNativeActivity() {
        return m_nativeCreated && !m_destroyed;
    }

    @Override
    public void onDestroy() {
        if (!hasNativeActivity()) {
            m_destroyed = true;
            super.onDestroy();
            return;
        }
        findViewById(android.R.id.content).getRootView().getViewTreeObserver().removeOnGlobalLayoutListener(this);
        git.artdeell.skymodloader.AccountStorage.saveActive(this);
        this.m_destroyed = true;
        SurfaceHolder surfaceHolder = this.m_currentSurfaceHolder;
        if (surfaceHolder != null) {
            onSurfaceDestroyedNative(this.m_handle, surfaceHolder.getSurface());
            this.m_currentSurfaceHolder = null;
        }
        onDestroyNative(this.m_handle);
        m_nativeCreated = false;
        m_handle = 0;
        super.onDestroy();
    }

    @Override
    public void onPause() {
        if (hasNativeActivity()) git.artdeell.skymodloader.AccountStorage.saveActive(this);
        super.onPause();
        if (hasNativeActivity()) onPauseNative(this.m_handle);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (hasNativeActivity()) onResumeNative(this.m_handle);
    }

    @Override
    protected void onSaveInstanceState(Bundle bundle) {
        super.onSaveInstanceState(bundle);
        if (!hasNativeActivity()) return;
        byte[] onSaveInstanceStateNative = onSaveInstanceStateNative(this.m_handle);
        if (onSaveInstanceStateNative != null) {
            bundle.putByteArray(KEY_NATIVE_SAVED_STATE, onSaveInstanceStateNative);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (hasNativeActivity()) onStartNative(this.m_handle);
    }

    @Override
    protected void onStop() {
        if (hasNativeActivity()) git.artdeell.skymodloader.AccountStorage.saveActive(this);
        super.onStop();
        if (hasNativeActivity()) onStopNative(this.m_handle);
    }

    @Override // android.app.Activity, android.content.ComponentCallbacks
    public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        if (!hasNativeActivity()) {
            return;
        }
        onConfigurationChangedNative(this.m_handle);
    }

    @Override // android.app.Activity, android.content.ComponentCallbacks
    public void onLowMemory() {
        super.onLowMemory();
        if (!hasNativeActivity()) {
            return;
        }
        onLowMemoryNative(this.m_handle);
    }

    @Override // android.app.Activity, android.view.Window.Callback
    public void onWindowFocusChanged(boolean z) {
        super.onWindowFocusChanged(z);
        if (!hasNativeActivity()) {
            return;
        }
        onWindowFocusChangedNative(this.m_handle, z);
    }

    @Override // android.view.SurfaceHolder.Callback
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        if (!hasNativeActivity()) {
            return;
        }
        this.m_currentSurfaceHolder = surfaceHolder;
        onSurfaceCreatedNative(this.m_handle, surfaceHolder.getSurface());
    }

    @Override // android.view.SurfaceHolder.Callback
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i2, int i3) {
        if (!hasNativeActivity()) {
            return;
        }
        this.m_currentSurfaceHolder = surfaceHolder;
        onSurfaceChangedNative(this.m_handle, surfaceHolder.getSurface(), i, i2, i3);
    }

    @Override // android.view.SurfaceHolder.Callback2
    public void surfaceRedrawNeeded(SurfaceHolder surfaceHolder) {
        if (!hasNativeActivity()) {
            return;
        }
        this.m_currentSurfaceHolder = surfaceHolder;
        onSurfaceRedrawNeededNative(this.m_handle, surfaceHolder.getSurface());
    }

    @Override // android.view.SurfaceHolder.Callback
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        this.m_currentSurfaceHolder = null;
        if (!hasNativeActivity()) {
            return;
        }
        onSurfaceDestroyedNative(this.m_handle, surfaceHolder.getSurface());
    }

    public void onGlobalLayout() {
        View rootView = findViewById(android.R.id.content).getRootView();
        rootView.getLocationInWindow(this.m_location);
        int width = rootView.getWidth();
        int height = rootView.getHeight();
        int[] iArr = this.m_location;
        if (iArr[0] != this.m_lastContentX || iArr[1] != this.m_lastContentY || width != this.m_lastContentWidth || height != this.m_lastContentHeight) {
            int[] iArr2 = this.m_location;
            int i = iArr2[0];
            this.m_lastContentX = i;
            int i2 = iArr2[1];
            this.m_lastContentY = i2;
            this.m_lastContentWidth = width;
            this.m_lastContentHeight = height;
            if (hasNativeActivity()) {
                onContentRectChangedNative(this.m_handle, i, i2, width, height);
            }
        }
    }

    private static String getAbsolutePath(File file) {
        if (file != null) {
            return file.getAbsolutePath();
        }
        return null;
    }
}
