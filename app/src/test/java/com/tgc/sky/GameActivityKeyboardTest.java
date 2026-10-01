package com.tgc.sky;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Looper;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class, manifest = Config.NONE, sdk = {30, 34})
public class GameActivityKeyboardTest {
    private TestActivity activity;
    private FrameView bridge;
    private final List<String> changes = new ArrayList<>();

    public static class TestActivity extends GameActivity {
        int safeAreaFailure;

        @Override
        public void onSafeAreaInsetsChanged(float[] insets) {
            if (safeAreaFailure == 1) throw new IllegalStateException("test safe-area failure");
            if (safeAreaFailure == 2) throw new NoSuchMethodError("test safe-area failure");
        }
    }

    private static class FrameView extends RelativeLayout {
        final Rect visibleFrame = new Rect();

        FrameView(Context context) { super(context); }

        @Override
        public void getWindowVisibleDisplayFrame(Rect out) { out.set(visibleFrame); }
    }

    @Before
    public void setUp() {

        activity = Robolectric.buildActivity(TestActivity.class).get();
        bridge = new FrameView(activity);
        activity.setContentView(bridge);
        ReflectionHelpers.setField(activity, "m_relativeLayout", bridge);

        ReflectionHelpers.setField(activity, "m_destroyed", true);
        activity.addOnKeyboardListener((visible, height) -> changes.add(visible + ":" + height));
    }

    private void frame(int width, int height, int left, int top) {
        activity.getWindow().getDecorView().layout(0, 0, width, height);
        bridge.layout(0, 0, width, height);
        bridge.visibleFrame.set(left, top, width, height);
    }

    private WindowInsets ime(boolean visible, int height) {
        return new WindowInsets.Builder()
            .setInsets(WindowInsets.Type.systemBars(), Insets.of(0, 120, 0, 0))
            .setInsets(WindowInsets.Type.ime(), Insets.of(0, 0, 0, height))
            .setVisible(WindowInsets.Type.ime(), visible)
            .build();
    }

    private void applyIme(boolean visible, int height) {
        activity.onApplyWindowInsets(bridge, ime(visible, height));
    }

    private boolean showing() {
        return ReflectionHelpers.getField(activity, "m_isKeyboardShowing");
    }

    private int keyboardHeight() {
        return ReflectionHelpers.getField(activity, "m_keyboardHeight");
    }

    @Test
    public void capturedRotationSequenceNeverAnnouncesKeyboard() {

        for (int repeat = 0; repeat < 3; repeat++) {
            frame(2376, 1080, 120, 0);
            applyIme(false, 0);
            activity.onGlobalLayout();
            frame(1080, 2376, 0, 120);
            activity.onGlobalLayout();
            applyIme(false, 0);
            activity.onGlobalLayout();
            assertFalse(showing());
            assertEquals(0, keyboardHeight());
        }
        assertTrue(changes.isEmpty());
    }

    @Test
    public void realKeyboardShowResizeAndHideReachListenersAndBroadcastsOnce() {
        List<String> broadcasts = new ArrayList<>();
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(activity);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                broadcasts.add(intent.getAction() + ":" + intent.getIntExtra("KeyboardHeight", 0));
            }
        };
        IntentFilter filter = new IntentFilter("KeyboardWillShow");
        filter.addAction("KeyboardWillHide");
        manager.registerReceiver(receiver, filter);
        try {
            applyIme(true, 720);
            applyIme(true, 720);
            assertTrue(showing());
            assertEquals(720, keyboardHeight());
            applyIme(true, 480);
            assertEquals(480, keyboardHeight());
            applyIme(false, 0);
            applyIme(false, 0);
            assertFalse(showing());
            assertEquals(0, keyboardHeight());
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(Arrays.asList("true:720", "true:480", "false:0"), changes);
            assertEquals(Arrays.asList("KeyboardWillShow:720", "KeyboardWillShow:480",
                "KeyboardWillHide:0"), broadcasts);
        } finally {
            manager.unregisterReceiver(receiver);
        }
    }

    @Test
    public void floatingKeyboardVisibilityDoesNotDependOnHeightOrEditFocus() {
        frame(1080, 2376, 0, 120);
        applyIme(true, 0);
        activity.notifyEditTextFocus(false);
        activity.onGlobalLayout();
        assertTrue(showing());
        assertEquals(Arrays.asList("true:0"), changes);
        applyIme(false, 0);
        assertFalse(showing());
    }

    @Test
    public void rotationLayoutCannotHideAnActuallyOpenKeyboard() {
        applyIme(true, 720);
        frame(2376, 1080, 120, 0);
        activity.onGlobalLayout();
        frame(1080, 2376, 0, 120);
        activity.onGlobalLayout();
        assertTrue(showing());
        assertEquals(720, keyboardHeight());
        assertEquals(Arrays.asList("true:720"), changes);
    }

    @Test
    public void insetsAreForwardedToChildrenEvenWhenSafeAreaCallbackFails() {
        FrameLayout parent = new FrameLayout(activity);
        View child = new View(activity);
        parent.addView(child);
        parent.setOnApplyWindowInsetsListener(activity::onApplyWindowInsets);
        List<WindowInsets> received = new ArrayList<>();
        child.setOnApplyWindowInsetsListener((view, insets) -> {
            received.add(insets);
            return insets;
        });
        for (int failure = 0; failure <= 2; failure++) {
            activity.safeAreaFailure = failure;
            WindowInsets shown = ime(true, 720);
            parent.dispatchApplyWindowInsets(shown);
            assertTrue(showing());
            assertEquals(720, keyboardHeight());
            assertEquals(shown, received.get(received.size() - 1));
            parent.dispatchApplyWindowInsets(ime(false, 0));
            assertFalse(showing());
        }
        assertEquals(6, received.size());
        assertEquals(6, changes.size());
    }

    @Test
    public void callbackPreservesTheViewsInsetsReturnValue() {
        View consumer = new View(activity) {
            @Override public WindowInsets onApplyWindowInsets(WindowInsets insets) {
                return WindowInsets.CONSUMED;
            }
        };
        assertSame(WindowInsets.CONSUMED, activity.onApplyWindowInsets(consumer, ime(false, 0)));
    }

    @Test
    public void baseContentRectCacheTracksRotation() {
        frame(2376, 1080, 120, 0);
        activity.onGlobalLayout();
        assertEquals(2376, activity.m_lastContentWidth);
        assertEquals(1080, activity.m_lastContentHeight);
        frame(1080, 2376, 0, 120);
        activity.onGlobalLayout();
        assertEquals(1080, activity.m_lastContentWidth);
        assertEquals(2376, activity.m_lastContentHeight);
    }

    @Test
    @Config(sdk = {26, 29})
    public void legacyKeyboardLayoutNotificationsArePreserved() {
        frame(1080, 2376, 0, 720);
        activity.onGlobalLayout();
        activity.onGlobalLayout();
        assertTrue(showing());
        frame(1080, 2376, 0, 0);
        activity.onGlobalLayout();
        activity.onGlobalLayout();
        assertFalse(showing());
        assertEquals(Arrays.asList("true:720", "false:0"), changes);
        assertEquals(2376, activity.m_lastContentHeight);
    }

    @Test
    @Config(sdk = {26, 29, 30, 34})
    public void layoutBeforeBridgeIsReadyStillUpdatesBaseContent() {
        frame(1080, 2376, 0, 0);
        ReflectionHelpers.setField(activity, "m_relativeLayout", null);
        activity.onGlobalLayout();
        assertEquals(2376, activity.m_lastContentHeight);
        assertTrue(changes.isEmpty());
    }
}
