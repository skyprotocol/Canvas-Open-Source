package com.tgc.sky;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Looper;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import com.tgc.sky.ui.TextField;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import git.artdeell.skymodloader.ImGUI;
import git.artdeell.skymodloader.ImGUITextInput;
import git.artdeell.skymodloader.MainActivity;
import git.artdeell.skymodloader.R;
import git.artdeell.skymodloader.SMLApplication;

@RunWith(RobolectricTestRunner.class)
@Config(application = KeyboardLifecycleTest.AuditApplication.class,
    manifest = Config.NONE, sdk = {30, 34, 36},
    shadows = {KeyboardLifecycleTest.ImGuiShadow.class,
        KeyboardLifecycleTest.MainActivityShadow.class})
public class KeyboardLifecycleTest {
    private AuditActivity activity;
    private AuditBridge bridge;
    private FrameLayout parent;

    public static class AuditApplication extends SMLApplication {
        @Override public void onCreate() {
            ReflectionHelpers.setStaticField(SMLApplication.class, "smlApplication", this);
        }
    }

    public static class AuditActivity extends GameActivity {
        final List<String> completions = new ArrayList<>();
        Runnable onCompletion;
        @Override public void onSafeAreaInsetsChanged(float[] insets) {}
        @Override public void onKeyboardCompleteNative(String text, boolean callback, boolean cancel) {
            completions.add(text + ":" + callback + ":" + cancel);
            if (onCompletion != null) onCompletion.run();
        }
    }

    @Implements(ImGUI.class)
    public static class ImGuiShadow {
        static int focusClears;
        @Implementation protected static void __staticInitializer__() {}
        @Implementation protected static void clearTextFocus() { focusClears++; }
    }

    @Implements(MainActivity.class)
    public static class MainActivityShadow {
        static final List<String> submissions = new ArrayList<>();
        @Implementation protected static void onKeyboardCompleteNative(String text) {
            submissions.add(text);
        }
    }

    private static class AuditBridge extends RelativeLayout {
        final Rect frame = new Rect();
        AuditBridge(Context context) { super(context); }
        @Override public void getWindowVisibleDisplayFrame(Rect out) { out.set(frame); }
    }

    private static class QueuedOverlay extends ImGUITextInput {
        final List<Runnable> posted = new ArrayList<>();
        QueuedOverlay(Context context) { super(context); }
        @Override public boolean post(Runnable action) {
            if (posted == null) return super.post(action);
            posted.add(action);
            return true;
        }
    }

    @Before public void setUp() {
        ImGuiShadow.focusClears = 0;
        MainActivityShadow.submissions.clear();
        ReflectionHelpers.setStaticField(TextField.class, "sChatDraft", "");
        activity = Robolectric.buildActivity(AuditActivity.class).get();
        activity.setTheme(R.style.SkyFullscreenTheme);
        parent = new FrameLayout(activity);
        bridge = new AuditBridge(activity);
        parent.addView(bridge);
        activity.setContentView(parent);
        ReflectionHelpers.setField(activity, "m_relativeLayout", bridge);
        ReflectionHelpers.setField(activity, "m_destroyed", true);
        SMLApplication.skyRes = activity.getResources();
        SMLApplication.skyPName = activity.getPackageName();
        geometry(1080, 2376, 0, 120, 0, 0);
    }

    private void geometry(int width, int height, int left, int top, int right, int bottom) {
        activity.getWindow().getDecorView().layout(0, 0, width, height);
        bridge.layout(0, 0, width, height);
        bridge.frame.set(left, top, width - right, height - bottom);
    }

    private SystemUI_android systemUi() {
        SystemUI_android ui = new SystemUI_android(activity);
        activity.m_systemUI = ui;
        return ui;
    }

    private TextField textField(SystemUI_android ui) {
        return ReflectionHelpers.getField(ui, "m_textField");
    }

    private WindowInsets ime(boolean shown, int height) {
        return new WindowInsets.Builder()
            .setInsets(WindowInsets.Type.ime(), Insets.of(0, 0, 0, height))
            .setVisible(WindowInsets.Type.ime(), shown).build();
    }

    private void apply(boolean shown, int height) {
        activity.onApplyWindowInsets(parent, ime(shown, height));
    }

    @Test public void systemUiStaysClearAcrossPhoneTabletAndWindowGeometryMatrix() {
        SystemUI_android ui = systemUi();
        int[][] sizes = {{1080,2376},{2376,1080},{2560,1600},{1600,2560},
            {2048,1536},{1536,2048},{900,700}};
        int[][] gaps = {{0,0,0,0},{0,120,0,0},{0,0,0,160},
            {0,120,0,160},{80,0,0,0},{0,0,80,0}};
        for (int[] size : sizes) for (int[] gap : gaps) {
            geometry(size[0], size[1], gap[0], gap[1], gap[2], gap[3]);
            activity.onGlobalLayout();
            apply(false, 0);
            activity.onGlobalLayout();
            assertFalse(ui.IsKeyboardShowing());
            assertEquals(0f, ui.GetKeyboardHeight(), 0f);
        }
        assertTrue(activity.completions.isEmpty());
    }

    @Test public void chatShowResizeAndHideCompleteOnce() {
        SystemUI_android ui = systemUi();
        TextField field = textField(ui);
        int id = field.showTextFieldWithPromptAsync("Chat", "draft", 100, 100, false);
        assertNotEquals(-1, id);
        apply(true, 720);
        assertTrue(ui.IsTextFieldShowing());
        assertTrue(ui.IsKeyboardShowing());
        assertEquals(720f, ui.GetKeyboardHeight(), 0f);
        EditText edit = ReflectionHelpers.getField(field, "m_textField");
        int firstMargin = ((RelativeLayout.LayoutParams) edit.getLayoutParams()).bottomMargin;
        apply(true, 480);
        assertEquals(firstMargin - 240,
            ((RelativeLayout.LayoutParams) edit.getLayoutParams()).bottomMargin);
        apply(false, 0);
        assertFalse(ui.IsKeyboardShowing());
        assertEquals(0f, ui.GetKeyboardHeight(), 0f);
        assertFalse(ui.IsTextFieldShowing());
        assertFalse(ui.IsTextFieldIdActive(id));
        assertEquals(View.GONE, edit.getVisibility());
        assertEquals(1, activity.completions.size());
        assertEquals(":false:true", activity.completions.get(0));
        apply(false, 0);
        ui.HideTextField();
        field.hideTextField();
        assertEquals(1, activity.completions.size());
    }

    @Test public void foreignImeHideDoesNotCancelInactiveChat() {
        SystemUI_android ui = systemUi();
        assertFalse(ui.IsTextFieldShowing());
        apply(true, 480);
        apply(false, 0);
        textField(ui).hideTextField();
        assertTrue(activity.completions.isEmpty());
    }

    @Test public void hardwareChatSurvivesSoftImeHideButExplicitHideStillWorks() {
        Configuration config = new Configuration(activity.getResources().getConfiguration());
        config.keyboard = Configuration.KEYBOARD_QWERTY;
        activity.getResources().updateConfiguration(config, activity.getResources().getDisplayMetrics());
        SystemUI_android ui = systemUi();
        textField(ui).showTextFieldWithPromptAsync("Chat", "draft", 100, 100, false);
        EditText edit = ReflectionHelpers.getField(textField(ui), "m_textField");
        int closedMargin = ((RelativeLayout.LayoutParams) edit.getLayoutParams()).bottomMargin;
        apply(true, 480);
        apply(false, 0);
        assertTrue(ui.IsTextFieldShowing());
        assertFalse(ui.IsKeyboardShowing());
        assertEquals(0f, ui.GetKeyboardHeight(), 0f);
        assertEquals(closedMargin, ((RelativeLayout.LayoutParams) edit.getLayoutParams()).bottomMargin);
        assertTrue(activity.completions.isEmpty());
        ui.HideTextField();
        assertFalse(ui.IsTextFieldShowing());
        assertEquals(1, activity.completions.size());
    }

    @Test public void cancelledDraftSurvivesReopenAndForeignImeCycle() {
        SystemUI_android ui = systemUi();
        TextField field = textField(ui);
        field.showTextFieldWithPromptAsync("Chat", "", 100, 100, false);
        EditText edit = ReflectionHelpers.getField(field, "m_textField");
        edit.setText("unsent draft");
        apply(true, 480);
        apply(false, 0);
        apply(true, 480);
        apply(false, 0);
        assertEquals(1, activity.completions.size());
        assertNotEquals(-1, field.showTextFieldWithPromptAsync("Chat", "", 100, 100, false));
        assertEquals("unsent draft", edit.getText().toString());
        ui.HideTextField();
        assertEquals(2, activity.completions.size());
    }

    @Test public void submittedChatDoesNotCancelOrResurrectDraftOnHide() {
        SystemUI_android ui = systemUi();
        TextField field = textField(ui);
        field.showTextFieldWithPromptAsync("Chat", "send me", 100, 100, false);
        EditText edit = ReflectionHelpers.getField(field, "m_textField");
        apply(true, 480);
        edit.onEditorAction(EditorInfo.IME_ACTION_SEND);
        assertEquals("send me:false:false", activity.completions.get(0));
        assertEquals(1, MainActivityShadow.submissions.size());
        assertEquals("send me", MainActivityShadow.submissions.get(0));
        apply(false, 0);
        ui.HideTextField();
        assertEquals(1, activity.completions.size());
        field.showTextFieldWithPromptAsync("Chat", "", 100, 100, false);
        assertEquals("", edit.getText().toString());
    }

    @Test public void callbackFieldKeepsItsCompletionFlagsAndCancelsOnlyOnce() {
        SystemUI_android ui = systemUi();
        TextField field = textField(ui);
        field.showTextFieldWithPromptAsync("Callback", "", 100, 100, true);
        apply(true, 480);
        apply(false, 0);
        ui.HideTextField();
        assertEquals(1, activity.completions.size());
        assertEquals(":true:true", activity.completions.get(0));
        field.showTextFieldWithPromptAsync("Callback", "answer", 100, 100, true);
        EditText edit = ReflectionHelpers.getField(field, "m_textField");
        edit.onEditorAction(EditorInfo.IME_ACTION_SEND);
        ui.HideTextField();
        assertEquals(2, activity.completions.size());
        assertEquals("answer:true:true", activity.completions.get(1));
    }

    @Test @Config(sdk = {26, 29, 30, 34, 36})
    public void reentrantHideCannotDuplicateCancellationOrOpenDuringCleanup() {
        SystemUI_android ui = systemUi();
        TextField field = textField(ui);
        int id = field.showTextFieldWithPromptAsync("Chat", "draft", 100, 100, false);
        activity.onCompletion = () -> {
            ui.HideTextField();
            field.hideTextField();
            assertEquals(-1, field.showTextFieldWithPromptAsync("Chat", "new", 100, 100, false));
        };
        field.hideTextField();
        activity.onCompletion = null;
        assertEquals(1, activity.completions.size());
        assertFalse(ui.IsTextFieldShowing());
        assertFalse(ui.IsTextFieldIdActive(id));
        int nextId = field.showTextFieldWithPromptAsync("Chat", "new", 100, 100, false);
        assertNotEquals(-1, nextId);
        assertNotEquals(id, nextId);
        assertTrue(ui.IsTextFieldIdActive(nextId));
    }

    @Test public void unrelatedImeHideDoesNotCancelQueuedShow() throws InterruptedException {
        SystemUI_android ui = systemUi();
        TextField field = textField(ui);
        AtomicInteger id = new AtomicInteger(-1);
        Thread nativeCaller = new Thread(() -> id.set(
            field.showTextFieldWithPromptAsync("Chat", "queued", 100, 100, false)));
        nativeCaller.start();
        nativeCaller.join();
        assertEquals(TextField.State.kTextFieldState_RequestShow, field.getState());
        apply(true, 480);
        apply(false, 0);
        assertTrue(activity.completions.isEmpty());
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(ui.IsTextFieldShowing());
        assertTrue(ui.IsTextFieldIdActive(id.get()));
        ui.HideTextField();
        assertEquals(1, activity.completions.size());
    }

    @Test @Config(sdk = {26, 29, 30, 34, 36})
    public void queuedShowThenExplicitHideCompletesOnce() throws InterruptedException {
        SystemUI_android ui = systemUi();
        TextField field = textField(ui);
        AtomicInteger id = new AtomicInteger(-1);
        Thread nativeCaller = new Thread(() -> {
            id.set(field.showTextFieldWithPromptAsync("Chat", "queued", 100, 100, false));
            ui.HideTextField();
        });
        nativeCaller.start();
        nativeCaller.join();
        assertEquals(TextField.State.kTextFieldState_RequestHide, field.getState());
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(TextField.State.kTextFieldState_Hidden, field.getState());
        assertEquals(1, activity.completions.size());
        assertFalse(ui.IsTextFieldIdActive(id.get()));
        assertNotEquals(-1, field.showTextFieldWithPromptAsync("Chat", "new", 100, 100, false));
    }

    @Test public void overlayHideCallbackDisablesEditingWhenDrained() {
        QueuedOverlay overlay = queuedOverlayAfterHide();
        assertTrue(overlay.isEnabled());
        int clears = ImGuiShadow.focusClears;
        overlay.posted.remove(0).run();
        assertFalse(overlay.isEnabled());
        assertEquals(View.GONE, overlay.getVisibility());
        assertEquals(clears + 1, ImGuiShadow.focusClears);
    }

    @Test public void oldOverlayHideCannotCloseANewerEditingSession() {
        QueuedOverlay overlay = queuedOverlayAfterHide();
        overlay.setKeyboardState(true);
        parent.dispatchApplyWindowInsets(ime(true, 480));
        assertTrue(ReflectionHelpers.<Boolean>getField(overlay, "imeWasVisible"));
        int clears = ImGuiShadow.focusClears;
        overlay.posted.remove(0).run();
        assertTrue(overlay.isEnabled());
        assertEquals(View.VISIBLE, overlay.getVisibility());
        assertEquals(clears, ImGuiShadow.focusClears);
    }

    @Test public void oldOverlayHideCannotCloseNewSessionBeforeImeShows() {
        QueuedOverlay overlay = queuedOverlayAfterHide();
        overlay.setKeyboardState(true);
        int clears = ImGuiShadow.focusClears;
        overlay.posted.remove(0).run();
        assertTrue(overlay.isEnabled());
        assertEquals(View.VISIBLE, overlay.getVisibility());
        assertEquals(clears, ImGuiShadow.focusClears);
    }

    @Test public void reshownImeInvalidatesHideWithoutExplicitEnable() {
        QueuedOverlay overlay = queuedOverlayAfterHide();
        parent.dispatchApplyWindowInsets(ime(true, 480));
        int clears = ImGuiShadow.focusClears;
        overlay.posted.remove(0).run();
        assertTrue(overlay.isEnabled());
        assertEquals(clears, ImGuiShadow.focusClears);
    }

    @Test public void onlyLatestHideCanDisableOverlay() {
        QueuedOverlay overlay = queuedOverlayAfterHide();
        parent.dispatchApplyWindowInsets(ime(true, 480));
        parent.dispatchApplyWindowInsets(ime(false, 0));
        assertEquals(2, overlay.posted.size());
        int clears = ImGuiShadow.focusClears;
        overlay.posted.remove(0).run();
        assertTrue(overlay.isEnabled());
        assertEquals(clears, ImGuiShadow.focusClears);
        overlay.posted.remove(0).run();
        assertFalse(overlay.isEnabled());
        assertEquals(clears + 1, ImGuiShadow.focusClears);
    }

    @Test public void explicitDisableInvalidatesPendingNativeFocusClear() {
        QueuedOverlay overlay = queuedOverlayAfterHide();
        overlay.disable();
        int clears = ImGuiShadow.focusClears;
        overlay.posted.remove(0).run();
        assertFalse(overlay.isEnabled());
        assertEquals(clears, ImGuiShadow.focusClears);

        parent.dispatchApplyWindowInsets(ime(true, 480));
        parent.dispatchApplyWindowInsets(ime(false, 0));
        assertTrue(overlay.posted.isEmpty());
    }

    private QueuedOverlay queuedOverlayAfterHide() {
        SystemUI_android ui = systemUi();
        QueuedOverlay overlay = new QueuedOverlay(activity);
        parent.addView(overlay, 0);
        overlay.setKeyboardState(true);
        parent.setOnApplyWindowInsetsListener(activity::onApplyWindowInsets);
        parent.dispatchApplyWindowInsets(ime(true, 480));
        assertTrue(ui.IsKeyboardShowing());
        parent.dispatchApplyWindowInsets(ime(false, 0));
        assertFalse(ui.IsKeyboardShowing());
        assertEquals(1, overlay.posted.size());
        return overlay;
    }
}
