package com.tgc.sky;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.SurfaceView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import git.artdeell.skymodloader.SMLApplication;
import git.artdeell.skymodloader.elfmod.ModManagerActivity;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, manifest = Config.NONE, sdk = {26, 34, 36})
public class GameActivityRestorationTest {
    @Test
    public void restoredGameRedirectsBeforeResourcesOrJniAndSafelyFinishes() {
        SMLApplication.skyRes = null;
        Bundle saved = new Bundle();
        saved.putByteArray("android:native_state", new byte[]{1, 2});
        var controller = Robolectric.buildActivity(GameActivity.class).create(saved);
        GameActivity activity = controller.get();
        Intent destination = shadowOf(activity).getNextStartedActivity();
        assertEquals(ModManagerActivity.class.getName(), destination.getComponent().getClassName());
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK, destination.getFlags());
        assertTrue(activity.isFinishing());
        assertFalse(activity.hasNativeActivity());

        // JNI is deliberately absent in this test. Any unguarded native call fails it.
        controller.start().resume();
        activity.onWindowFocusChanged(true);
        activity.onConfigurationChanged(new Configuration());
        activity.onLowMemory();
        activity.onGlobalLayout();
        activity.onNewIntent(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("sky://test")));
        var holder = new SurfaceView(activity).getHolder();
        activity.surfaceCreated(holder);
        activity.surfaceChanged(holder, android.graphics.PixelFormat.RGBA_8888, 100, 100);
        activity.surfaceRedrawNeeded(holder);
        activity.surfaceDestroyed(holder);
        Bundle output = new Bundle();
        controller.saveInstanceState(output);
        assertFalse(output.containsKey("android:native_state"));
        controller.pause().stop().destroy();
        assertFalse(activity.hasNativeActivity());
        assertNull(SMLApplication.skyRes);
    }
}
