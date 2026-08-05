//
// Created by maks on 28.06.2022.
//
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <android/input.h>
#include <android/keycodes.h>
#include <android/log.h>
#include <android/asset_manager_jni.h>
#include <jni.h>
#include <ctime>
#include "imgui/imgui.h"
#include "imgui/imgui_internal.h"
#include "imgui/backends/imgui_impl_opengl3.h"
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <unistd.h>
#include <atomic>
#include <cfloat>
#include <mutex>
#include <vector>
#include "Canvas/Canvas.h"
#include "../../include/misc/visibility.h"

#define g_LogTag "imgui4ca"

static EGLDisplay           g_EglDisplay = EGL_NO_DISPLAY;
static EGLSurface           g_EglSurface = EGL_NO_SURFACE;
static EGLContext           g_EglContext = EGL_NO_CONTEXT;
static EGLConfig egl_config;
static EGLint egl_format;
static bool run = true;
static ANativeWindow *androidWindow;
static double g_time = 0;

PRIVATE_API static ImGuiKey ImGui_ImplAndroid_KeyCodeToImGuiKey(int32_t key_code)
{
    switch (key_code)
    {
        case AKEYCODE_TAB:                  return ImGuiKey_Tab;
        case AKEYCODE_DPAD_LEFT:            return ImGuiKey_LeftArrow;
        case AKEYCODE_DPAD_RIGHT:           return ImGuiKey_RightArrow;
        case AKEYCODE_DPAD_UP:              return ImGuiKey_UpArrow;
        case AKEYCODE_DPAD_DOWN:            return ImGuiKey_DownArrow;
        case AKEYCODE_PAGE_UP:              return ImGuiKey_PageUp;
        case AKEYCODE_PAGE_DOWN:            return ImGuiKey_PageDown;
        case AKEYCODE_MOVE_HOME:            return ImGuiKey_Home;
        case AKEYCODE_MOVE_END:             return ImGuiKey_End;
        case AKEYCODE_INSERT:               return ImGuiKey_Insert;
        case AKEYCODE_FORWARD_DEL:          return ImGuiKey_Delete;
        case AKEYCODE_DEL:                  return ImGuiKey_Backspace;
        case AKEYCODE_SPACE:                return ImGuiKey_Space;
        case AKEYCODE_ENTER:                return ImGuiKey_Enter;
        case AKEYCODE_ESCAPE:               return ImGuiKey_Escape;
        case AKEYCODE_APOSTROPHE:           return ImGuiKey_Apostrophe;
        case AKEYCODE_COMMA:                return ImGuiKey_Comma;
        case AKEYCODE_MINUS:                return ImGuiKey_Minus;
        case AKEYCODE_PERIOD:               return ImGuiKey_Period;
        case AKEYCODE_SLASH:                return ImGuiKey_Slash;
        case AKEYCODE_SEMICOLON:            return ImGuiKey_Semicolon;
        case AKEYCODE_EQUALS:               return ImGuiKey_Equal;
        case AKEYCODE_LEFT_BRACKET:         return ImGuiKey_LeftBracket;
        case AKEYCODE_BACKSLASH:            return ImGuiKey_Backslash;
        case AKEYCODE_RIGHT_BRACKET:        return ImGuiKey_RightBracket;
        case AKEYCODE_GRAVE:                return ImGuiKey_GraveAccent;
        case AKEYCODE_CAPS_LOCK:            return ImGuiKey_CapsLock;
        case AKEYCODE_SCROLL_LOCK:          return ImGuiKey_ScrollLock;
        case AKEYCODE_NUM_LOCK:             return ImGuiKey_NumLock;
        case AKEYCODE_SYSRQ:                return ImGuiKey_PrintScreen;
        case AKEYCODE_BREAK:                return ImGuiKey_Pause;
        case AKEYCODE_NUMPAD_0:             return ImGuiKey_Keypad0;
        case AKEYCODE_NUMPAD_1:             return ImGuiKey_Keypad1;
        case AKEYCODE_NUMPAD_2:             return ImGuiKey_Keypad2;
        case AKEYCODE_NUMPAD_3:             return ImGuiKey_Keypad3;
        case AKEYCODE_NUMPAD_4:             return ImGuiKey_Keypad4;
        case AKEYCODE_NUMPAD_5:             return ImGuiKey_Keypad5;
        case AKEYCODE_NUMPAD_6:             return ImGuiKey_Keypad6;
        case AKEYCODE_NUMPAD_7:             return ImGuiKey_Keypad7;
        case AKEYCODE_NUMPAD_8:             return ImGuiKey_Keypad8;
        case AKEYCODE_NUMPAD_9:             return ImGuiKey_Keypad9;
        case AKEYCODE_NUMPAD_DOT:           return ImGuiKey_KeypadDecimal;
        case AKEYCODE_NUMPAD_DIVIDE:        return ImGuiKey_KeypadDivide;
        case AKEYCODE_NUMPAD_MULTIPLY:      return ImGuiKey_KeypadMultiply;
        case AKEYCODE_NUMPAD_SUBTRACT:      return ImGuiKey_KeypadSubtract;
        case AKEYCODE_NUMPAD_ADD:           return ImGuiKey_KeypadAdd;
        case AKEYCODE_NUMPAD_ENTER:         return ImGuiKey_KeypadEnter;
        case AKEYCODE_NUMPAD_EQUALS:        return ImGuiKey_KeypadEqual;
        case AKEYCODE_CTRL_LEFT:            return ImGuiKey_LeftCtrl;
        case AKEYCODE_SHIFT_LEFT:           return ImGuiKey_LeftShift;
        case AKEYCODE_ALT_LEFT:             return ImGuiKey_LeftAlt;
        case AKEYCODE_META_LEFT:            return ImGuiKey_LeftSuper;
        case AKEYCODE_CTRL_RIGHT:           return ImGuiKey_RightCtrl;
        case AKEYCODE_SHIFT_RIGHT:          return ImGuiKey_RightShift;
        case AKEYCODE_ALT_RIGHT:            return ImGuiKey_RightAlt;
        case AKEYCODE_META_RIGHT:           return ImGuiKey_RightSuper;
        case AKEYCODE_MENU:                 return ImGuiKey_Menu;
        case AKEYCODE_0:                    return ImGuiKey_0;
        case AKEYCODE_1:                    return ImGuiKey_1;
        case AKEYCODE_2:                    return ImGuiKey_2;
        case AKEYCODE_3:                    return ImGuiKey_3;
        case AKEYCODE_4:                    return ImGuiKey_4;
        case AKEYCODE_5:                    return ImGuiKey_5;
        case AKEYCODE_6:                    return ImGuiKey_6;
        case AKEYCODE_7:                    return ImGuiKey_7;
        case AKEYCODE_8:                    return ImGuiKey_8;
        case AKEYCODE_9:                    return ImGuiKey_9;
        case AKEYCODE_A:                    return ImGuiKey_A;
        case AKEYCODE_B:                    return ImGuiKey_B;
        case AKEYCODE_C:                    return ImGuiKey_C;
        case AKEYCODE_D:                    return ImGuiKey_D;
        case AKEYCODE_E:                    return ImGuiKey_E;
        case AKEYCODE_F:                    return ImGuiKey_F;
        case AKEYCODE_G:                    return ImGuiKey_G;
        case AKEYCODE_H:                    return ImGuiKey_H;
        case AKEYCODE_I:                    return ImGuiKey_I;
        case AKEYCODE_J:                    return ImGuiKey_J;
        case AKEYCODE_K:                    return ImGuiKey_K;
        case AKEYCODE_L:                    return ImGuiKey_L;
        case AKEYCODE_M:                    return ImGuiKey_M;
        case AKEYCODE_N:                    return ImGuiKey_N;
        case AKEYCODE_O:                    return ImGuiKey_O;
        case AKEYCODE_P:                    return ImGuiKey_P;
        case AKEYCODE_Q:                    return ImGuiKey_Q;
        case AKEYCODE_R:                    return ImGuiKey_R;
        case AKEYCODE_S:                    return ImGuiKey_S;
        case AKEYCODE_T:                    return ImGuiKey_T;
        case AKEYCODE_U:                    return ImGuiKey_U;
        case AKEYCODE_V:                    return ImGuiKey_V;
        case AKEYCODE_W:                    return ImGuiKey_W;
        case AKEYCODE_X:                    return ImGuiKey_X;
        case AKEYCODE_Y:                    return ImGuiKey_Y;
        case AKEYCODE_Z:                    return ImGuiKey_Z;
        case AKEYCODE_F1:                   return ImGuiKey_F1;
        case AKEYCODE_F2:                   return ImGuiKey_F2;
        case AKEYCODE_F3:                   return ImGuiKey_F3;
        case AKEYCODE_F4:                   return ImGuiKey_F4;
        case AKEYCODE_F5:                   return ImGuiKey_F5;
        case AKEYCODE_F6:                   return ImGuiKey_F6;
        case AKEYCODE_F7:                   return ImGuiKey_F7;
        case AKEYCODE_F8:                   return ImGuiKey_F8;
        case AKEYCODE_F9:                   return ImGuiKey_F9;
        case AKEYCODE_F10:                  return ImGuiKey_F10;
        case AKEYCODE_F11:                  return ImGuiKey_F11;
        case AKEYCODE_F12:                  return ImGuiKey_F12;
        default:                            return ImGuiKey_None;
    }
}



PRIVATE_API void initContext() {
    g_EglDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (g_EglDisplay == EGL_NO_DISPLAY)
        __android_log_print(ANDROID_LOG_ERROR, g_LogTag, "%s", "eglGetDisplay(EGL_DEFAULT_DISPLAY) returned EGL_NO_DISPLAY");
    if (eglInitialize(g_EglDisplay, nullptr, nullptr) != EGL_TRUE)
        __android_log_print(ANDROID_LOG_ERROR, g_LogTag, "%s", "eglInitialize() returned with an error");
    eglSwapInterval(g_EglDisplay, 1);
    const EGLint egl_attributes[] = { EGL_BLUE_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_RED_SIZE, 8, EGL_ALPHA_SIZE, 8, EGL_DEPTH_SIZE, 24, EGL_SURFACE_TYPE, EGL_WINDOW_BIT, EGL_NONE };
    EGLint num_configs = 0;
    if (eglChooseConfig(g_EglDisplay, egl_attributes, nullptr, 0, &num_configs) != EGL_TRUE)
        __android_log_print(ANDROID_LOG_ERROR, g_LogTag, "%s", "eglChooseConfig() returned with an error");
    if (num_configs == 0)
        __android_log_print(ANDROID_LOG_ERROR, g_LogTag, "%s", "eglChooseConfig() returned 0 matching config");

    // Get the first matching config
    eglChooseConfig(g_EglDisplay, egl_attributes, &egl_config, 1, &num_configs);
    eglGetConfigAttrib(g_EglDisplay, egl_config, EGL_NATIVE_VISUAL_ID, &egl_format);

    const EGLint egl_context_attributes[] = { EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE };
    g_EglContext = eglCreateContext(g_EglDisplay, egl_config, EGL_NO_CONTEXT, egl_context_attributes);

    if (g_EglContext == EGL_NO_CONTEXT)
        __android_log_print(ANDROID_LOG_ERROR, g_LogTag, "%s", "eglCreateContext() returned EGL_NO_CONTEXT");

}

// --- thread-safe input bridge ----------------------------------------------
//
// renderloop() owns the ImGui context on the "imgui dispatch thread", and a
// context may not be used from multiple threads in parallel.  Every JNI entry
// point below is called from the Android UI thread, so none of them may touch
// the context.
//
// Note that swapping "io.MousePos = ..." for io.AddMousePosEvent() would NOT be
// sufficient: AddXXXEvent() pushes onto g.InputEventsQueue (an ImVector) and
// reads live g.IO state to filter duplicates, so it is equally unsafe from a
// foreign thread.  Instead the UI thread appends to our own locked queue and
// the render thread replays it into ImGui immediately before NewFrame().
//
// The UI thread still has to answer wantsMouse()/wantsKeyboard() synchronously
// (onTouchEvent must decide there and then whether to forward the touch to the
// game).  It does that against a snapshot of window rects published by the
// render thread at the end of each frame, so the decision stays position-aware
// - which is what the old UpdateHoveredWindowAndCaptureFlags() call was for -
// without reading live context state.
namespace CanvasInput {

struct Event {
    enum Kind { MousePos, MouseButton, Key, Char };
    Kind     kind;
    float    x = 0.0f, y = 0.0f;
    int      code = 0;      // mouse button index, or Android keycode
    bool     down = false;
    unsigned codepoint = 0;
};

static std::mutex         s_queueMutex;
static std::vector<Event> s_queue;
static float              s_lastX = -FLT_MAX;   // guarded by s_queueMutex
static float              s_lastY = -FLT_MAX;

struct Snapshot {
    std::vector<ImVec4> hoverRects;             // min.xy, max.zw - padding applied
    bool wantCaptureMouse = false;
    bool wantTextInput    = false;
    bool mouseDown        = false;              // io.MouseDown[0]
    bool mouseDownOwned   = false;              // io.MouseDownOwned[0]
};
static std::mutex s_snapMutex;
static Snapshot   s_snapshot;

// Raised from the UI thread when the user dismisses the IME behind ImGui's back
// (BACK key, window focus loss).  Applied on the render thread inside the frame.
static std::atomic<bool> s_clearTextFocus{false};

// ---- UI thread -------------------------------------------------------------

static void PushMousePos(float x, float y) {
    std::lock_guard<std::mutex> lk(s_queueMutex);
    Event e; e.kind = Event::MousePos; e.x = x; e.y = y;
    s_queue.push_back(e);
    s_lastX = x; s_lastY = y;
}

static void PushMouseButton(int button, bool down) {
    std::lock_guard<std::mutex> lk(s_queueMutex);
    Event e; e.kind = Event::MouseButton; e.code = button; e.down = down;
    s_queue.push_back(e);

    // On release, tell ImGui the pointer left the screen - nothing else does.
    // Otherwise io.MousePos keeps the lift-off coordinates indefinitely, so
    // ImGui goes on reporting whatever sat under the finger as hovered (tooltips
    // that outlive the touch that raised them), and the first frame of the NEXT
    // touch computes an io.MouseDelta spanning both touches.  NewFrame() zeroes
    // MouseDelta when either position fails IsMousePosValid, which is what this
    // makes true.
    //
    // This is a pointer-lifecycle correctness fix, NOT a guard for scroll.cpp's
    // drag-to-scroll: do_scroll() only reaches SetScrollY when hovered && !held,
    // and neither branch can satisfy that on the press frame.  Over empty window
    // space ButtonBehavior has just taken ActiveId, so held is true; over a
    // widget HoveredId != 0, so ButtonBehavior is never called at all and
    // hovered stays false.  The cross-touch delta is unreachable either way.
    //
    // Known limit: if a lift and the next touch-down fall inside one frame, the
    // invalidation and the new position are applied in the same UpdateInputEvents
    // pass (position events do not break each other) and only the new position
    // survives to the frame boundary, so the delta is not zeroed for that
    // gesture.  Left as-is deliberately - splitting the drain across frames to
    // cover it would buy nothing given the paragraph above.
    if (!down && button == 0) {
        Event inv; inv.kind = Event::MousePos; inv.x = -FLT_MAX; inv.y = -FLT_MAX;
        s_queue.push_back(inv);
        // s_lastX/s_lastY deliberately keep the real lift-off coordinates, so
        // the wantsMouse() hit test still resolves this ACTION_UP correctly.
    }
}

static void PushKey(int keycode, bool down) {
    std::lock_guard<std::mutex> lk(s_queueMutex);
    Event e; e.kind = Event::Key; e.code = keycode; e.down = down;
    s_queue.push_back(e);
}

static void PushChar(unsigned codepoint) {
    std::lock_guard<std::mutex> lk(s_queueMutex);
    Event e; e.kind = Event::Char; e.codepoint = codepoint;
    s_queue.push_back(e);
}

static bool WantsMouse() {
    float x, y;
    {
        std::lock_guard<std::mutex> lk(s_queueMutex);
        x = s_lastX; y = s_lastY;
    }
    std::lock_guard<std::mutex> lk(s_snapMutex);
    // An in-progress drag keeps capture even once the finger leaves the window,
    // matching io.WantCaptureMouse's behaviour with an active item.
    if (s_snapshot.wantCaptureMouse)
        return true;
    // Click ownership: a drag that began outside ImGui belongs to the game for
    // its whole duration, even if it passes over a window.  Without this the
    // hit test below would hijack a camera drag mid-stroke - ImGui itself
    // enforces this via io.MouseDownOwned in UpdateHoveredWindowAndCaptureFlags.
    if (s_snapshot.mouseDown && !s_snapshot.mouseDownOwned)
        return false;
    for (const ImVec4 &r : s_snapshot.hoverRects)
        if (x >= r.x && y >= r.y && x < r.z && y < r.w)
            return true;
    return false;
}

static bool WantsKeyboard() {
    std::lock_guard<std::mutex> lk(s_snapMutex);
    return s_snapshot.wantTextInput;
}

// The IME can be dismissed without ImGui ever hearing about it (BACK key,
// app switch), which leaves the InputText active and io.WantTextInput stuck
// true.  GameActivity's imguiKeybaordShowing latch then believes the keyboard
// is up while it is not, and never re-shows it - tapping a text field moves the
// caret but raises no keyboard.  Ending the ImGui editing session drops
// WantTextInput, which lets that latch settle by itself on the next touch, the
// same way the IME "done" action already behaves.
static void RequestClearTextFocus() {
    s_clearTextFocus.store(true, std::memory_order_relaxed);
}

// ---- render thread ---------------------------------------------------------

// Call immediately before ImGui::NewFrame().
static void Drain() {
    std::vector<Event> local;
    {
        std::lock_guard<std::mutex> lk(s_queueMutex);
        local.swap(s_queue);
    }
    // Deliberately NOT tagged via AddMouseSourceEvent(ImGuiMouseSource_TouchScreen),
    // even though these events genuinely are touches.  That tag activates ImGui's
    // trickling rule for touch presses ("#2702: TouchScreen have no initial
    // hover"), which defers a button-down to the frame AFTER the pointer move.
    // Canvas cannot absorb that delay: wantsKeyboard() is only ever polled from
    // GameActivity.onTouchEvent, so a quick tap at 60 fps finishes before the
    // deferred press is applied and the soft keyboard does not open until the
    // next touch.  Note the tag is sticky state, so setting it for position
    // events alone would still leak onto the following button event.
    // The only thing given up is ImGui's 3 px (rather than 2 px) stationary
    // threshold for ImGuiHoveredFlags_Stationary - which is what this backend
    // had before, since it never set a mouse source at all.
    ImGuiIO &io = ImGui::GetIO();
    for (const Event &e : local) {
        switch (e.kind) {
        case Event::MousePos:
            io.AddMousePosEvent(e.x, e.y);
            break;
        case Event::MouseButton:
            io.AddMouseButtonEvent(e.code, e.down);
            break;
        case Event::Key:
            io.AddKeyEvent(ImGui_ImplAndroid_KeyCodeToImGuiKey(e.code), e.down);
            break;
        case Event::Char:
            io.AddInputCharacter(e.codepoint);
            break;
        }
    }
}

// Call immediately after ImGui::NewFrame(), so ActiveId is only ever touched
// inside frame scope.  ClearActiveID() deactivates the InputText without
// reverting it - unlike feeding Escape, which sets revert_edit and would throw
// away whatever the user had typed.
static void ApplyPendingFocusClear() {
    if (!s_clearTextFocus.exchange(false, std::memory_order_relaxed))
        return;
    // Only ever end a TEXT session.  ClearActiveID() clears whatever currently
    // owns ActiveId - and additionally drops g.MovingWindow when the id is a
    // window's MoveId - so an unconditional call would cancel a slider drag or
    // a window move if the window happened to change focus mid-gesture.
    ImGuiContext *g = ImGui::GetCurrentContext();
    if (g != nullptr && g->ActiveId != 0 && g->ActiveId == g->InputTextState.ID)
        ImGui::ClearActiveID();
}

// Call after ImGui::Render(), when the frame is closed and g.Windows is stable.
// Mirrors FindHoveredWindow()'s filters so the UI-thread hit test agrees with
// what ImGui itself would decide for the same position.
static void PublishSnapshot() {
    ImGuiContext *g = ImGui::GetCurrentContext();
    if (g == nullptr)
        return;

    Snapshot snap;
    snap.wantCaptureMouse = g->IO.WantCaptureMouse;
    snap.mouseDown        = g->IO.MouseDown[0];
    snap.mouseDownOwned   = g->IO.MouseDownOwned[0];

    // io.WantTextInput is written by NewFrame() from g.WantTextInputNextFrame,
    // which InputTextEx sets during the frame BODY - so by the time we get here
    // io.WantTextInput still describes the previous frame, and publishing it
    // alone would cost a second frame of latency on top of the snapshot's own.
    // Since wantsKeyboard() is polled only from onTouchEvent, that lost frame
    // shows up as a short tap failing to raise the soft keyboard.  Folding in
    // the pending request recovers the frame the old synchronous
    // UpdateHoveredWindowAndCaptureFlags() call used to catch.
    snap.wantTextInput    = g->IO.WantTextInput || (g->WantTextInputNextFrame == 1);

    const ImVec2 paddingRegular = g->Style.TouchExtraPadding;
    const ImVec2 paddingResize  = g->IO.ConfigWindowsResizeFromEdges
                                      ? g->WindowsHoverPadding : paddingRegular;
    snap.hoverRects.reserve((std::size_t)g->Windows.Size);
    for (int i = 0; i < g->Windows.Size; i++) {
        ImGuiWindow *w = g->Windows[i];
        if (w == nullptr || !w->Active || w->Hidden)
            continue;
        if (w->Flags & ImGuiWindowFlags_NoMouseInputs)
            continue;
        const ImVec2 pad = (w->Flags & (ImGuiWindowFlags_NoResize | ImGuiWindowFlags_AlwaysAutoResize))
                               ? paddingRegular : paddingResize;
        const ImRect &r = w->OuterRectClipped;
        snap.hoverRects.push_back(ImVec4(r.Min.x - pad.x, r.Min.y - pad.y,
                                         r.Max.x + pad.x, r.Max.y + pad.y));
    }

    std::lock_guard<std::mutex> lk(s_snapMutex);
    s_snapshot = std::move(snap);
}

// Call when the render loop stops (surface torn down).  Drops any backlog that
// would otherwise replay on resurface(), and leaves ImGui with the pointer
// released.
static void Reset() {
    s_clearTextFocus.store(false, std::memory_order_relaxed);
    {
        std::lock_guard<std::mutex> lk(s_queueMutex);
        s_queue.clear();
        s_lastX = -FLT_MAX;
        s_lastY = -FLT_MAX;
    }
    {
        std::lock_guard<std::mutex> lk(s_snapMutex);
        s_snapshot = Snapshot();
    }

    // Leave the context with nothing pending and nothing held.  Both matter,
    // because the ImGuiContext outlives the surface (CreateContext runs once;
    // resurface() reuses it):
    //
    //  - ClearEventsQueue(): ImGui's OWN queue is not necessarily empty.
    //    UpdateInputEvents processes events until a trickling rule breaks, then
    //    keeps the remainder "for the next frame".  If the loop exits there, a
    //    trailing button-down replays on resurface and latches a press at a
    //    stale position with no finger on the screen.
    //  - ClearInputKeys(): releases mouse buttons (plus MouseDown/Duration),
    //    keyboard keys and the character queue.  Dropping our own queue above
    //    can discard a pending ACTION_UP - or the up half of a key pair, since
    //    ImGUITextInput.submitBackspace sends down and up separately.
    //
    // A latched io.MouseDown[0] would otherwise make the first published
    // snapshot claim every touch after resurface - or, if the press was not
    // ImGui's, refuse all of them - and AddMouseButtonEvent's duplicate filter
    // would additionally swallow the next real press, since latest_button_down
    // would already match.
    //
    // Written directly rather than queued: no further frame will run to drain a
    // queue, and the render thread owns the context at this point.  ActiveId is
    // deliberately NOT cleared - with the pointer released and MousePos
    // invalid, any mouse-driven ActiveId self-releases on the first frame after
    // resurface without firing, whereas an explicit ClearActiveID() would run
    // InputTextDeactivateHook and discard an in-progress text edit.
    if (ImGuiContext *g = ImGui::GetCurrentContext()) {
        g->IO.ClearEventsQueue();
        g->IO.ClearInputKeys();
        // Not covered by ClearInputKeys(), which sets MousePos but not Prev.
        g->IO.MousePosPrev = ImVec2(-FLT_MAX, -FLT_MAX);
    }
}

} // namespace CanvasInput

PRIVATE_API void newframe() {
    ImGuiIO& io = ImGui::GetIO();
    io.DisplaySize = ImVec2((float)ANativeWindow_getWidth(androidWindow), (float)ANativeWindow_getHeight(androidWindow));
    struct timespec current_timespec{};
    clock_gettime(CLOCK_MONOTONIC, &current_timespec);
    double current_time = (double)(current_timespec.tv_sec) + ((double)current_timespec.tv_nsec / 1000000000.0);
    io.DeltaTime = g_time > 0.0 ? (float)(current_time - g_time) : (float)(1.0f / 60.0f);
    g_time = current_time;
}

PRIVATE_API void renderloop()
{
    ImGuiIO &io = ImGui::GetIO();
    while (run) {
        if(Canvas::frameRateLimited) usleep(41000);
        ImGui_ImplOpenGL3_NewFrame();
        newframe();
        CanvasInput::Drain();          // replay UI-thread input into ImGui
        ImGui::NewFrame();
        CanvasInput::ApplyPendingFocusClear();
        Canvas::CanvasMenu();
        ImGui::Render();
        CanvasInput::PublishSnapshot(); // serves next frame's UI-thread queries
        glViewport(0, 0, (int)io.DisplaySize.x, (int)io.DisplaySize.y);
        glClearColor(0, 0, 0, 0);
        glClear(GL_COLOR_BUFFER_BIT);
        ImGui_ImplOpenGL3_RenderDrawData(ImGui::GetDrawData());
        eglSwapBuffers(g_EglDisplay, g_EglSurface);
    }
    CanvasInput::Reset();
    eglMakeCurrent(g_EglDisplay, nullptr, nullptr, nullptr);
    eglDestroySurface(g_EglDisplay, g_EglSurface);
    g_EglSurface = nullptr;
}

PRIVATE_API void init_sfc(JNIEnv *env, jobject surface) {
    if(androidWindow != nullptr) {
        ANativeWindow_release(androidWindow);
    }
    androidWindow = ANativeWindow_fromSurface(env, surface);
    ANativeWindow_acquire(androidWindow);
    ANativeWindow_setBuffersGeometry(androidWindow, 0, 0, egl_format);
    g_EglSurface = eglCreateWindowSurface(g_EglDisplay, egl_config, androidWindow, nullptr);
    eglMakeCurrent(g_EglDisplay, g_EglSurface, g_EglSurface, g_EglContext);
}

static jclass class_ImGUI;
static jmethodID method_getClipboard;
static jmethodID method_setClipboard;
static JavaVM *jvm;
static char* clipboard_buffer = nullptr;

#define CHECK_ENV JNIEnv *env; bool detach = false; if(jvm->GetEnv((void**)&env, JNI_VERSION_1_6) == JNI_EDETACHED) {detach = true; jvm->AttachCurrentThread(&env, nullptr);}
#define CHECK_END if(detach) jvm->DetachCurrentThread();

PRIVATE_API static const char* androidbk_get_clipboard(void* user_data) {
    CHECK_ENV
    auto clipboard = (jstring)env->CallStaticObjectMethod(class_ImGUI, method_getClipboard);
    jsize strb_length =  env->GetStringUTFLength(clipboard)+1;
    const char* clipboard_chars = env->GetStringUTFChars(clipboard, nullptr);
    clipboard_buffer = (char *) realloc(clipboard_buffer, strb_length);
    if(clipboard_buffer == nullptr) abort();
    snprintf(clipboard_buffer, strb_length, "%s", clipboard_chars);
    env->ReleaseStringUTFChars(clipboard, clipboard_chars);
    env->DeleteLocalRef(clipboard);
    CHECK_END
    return clipboard_buffer;
}

PRIVATE_API static void androidbk_set_clipboard(void* user_data, const char* text) {
    CHECK_ENV
    jstring new_clipboard = env->NewStringUTF(text);
    env->CallStaticVoidMethod(class_ImGUI, method_setClipboard, new_clipboard);
    env->DeleteLocalRef(new_clipboard);
    CHECK_END
}
PRIVATE_API static void loadFonts(ImGuiIO& io, jfloat fontsize, AAssetManager *mgr, bool loadFontDroidSans) {
    /* EUROPEAN GLYPH LOADER */
    ImVector<ImWchar> rangesEuropean;
    ImFontGlyphRangesBuilder builderEuropean;
    builderEuropean.AddRanges(io.Fonts->GetGlyphRangesDefault());
    builderEuropean.AddRanges(io.Fonts->GetGlyphRangesCyrillic());
    builderEuropean.BuildRanges(&rangesEuropean);
    io.Fonts->AddFontFromFileTTF("/system/fonts/Roboto-Regular.ttf", fontsize, nullptr,
                                 rangesEuropean.Data);
    /* END */

    /* GEORGIAN GLYPH MERGE — merged into the Roboto atlas above.
     * Roboto does not ship Georgian glyphs; we pull them from whichever
     * NotoSansGeorgian variant is present on the device (path varies by
     * Android version and OEM). First successful load wins; missing files
     * are silently skipped by AddFontFromFileTTF returning nullptr. */
    {
        static const ImWchar kGeorgianRanges[] = {
            0x10A0, 0x10FF, // Georgian (Mkhedruli)
            0,
        };
        static const char* kGeorgianFontCandidates[] = {
            "/system/fonts/NotoSansGeorgian-VF.ttf",           // OEM / Android 11+ (variable)
            "/system/fonts/NotoSansGeorgian-Regular.ttf",      // Android <= 11 (static)
            "/system/fonts/NotoSansGeorgian[wdth,wght].ttf",   // alternative variable naming
            "/system/fonts/NotoSans-Regular.ttf",               // broad fallback
            nullptr,
        };
        ImFontConfig fcGeo;
        fcGeo.MergeMode = true;
        for (int gi = 0; kGeorgianFontCandidates[gi]; ++gi) {
            if (io.Fonts->AddFontFromFileTTF(kGeorgianFontCandidates[gi], fontsize,
                                             &fcGeo, kGeorgianRanges)) {
                break;
            }
        }
    }
    /* END GEORGIAN */

    void* fontBufferDroidSans = nullptr;

    /* DROID SANS */
    if(loadFontDroidSans) {
        AAsset *font_droidsans = AAssetManager_open(mgr, "DroidSansFallback.ttf",
                                                    AASSET_MODE_STREAMING);
        if (font_droidsans == nullptr) return;
        size_t size = AAsset_getLength64(font_droidsans);
        fontBufferDroidSans = malloc(size);
        ImFontConfig fc;
        fc.MergeMode = true;
        if (AAsset_read(font_droidsans, fontBufferDroidSans , size) != size) {
            __android_log_print(ANDROID_LOG_ERROR, g_LogTag, "Unable to fully read font");
            free(fontBufferDroidSans);
            fontBufferDroidSans = nullptr;
            goto ds_fini;
        }
        io.Fonts->AddFontFromMemoryTTF(fontBufferDroidSans, (int) size, fontsize,
                                       &fc, io.Fonts->GetGlyphRangesChineseSimplifiedCommon());
        ds_fini:
        AAsset_close(font_droidsans);
    }
    io.Fonts->Build();
    if(fontBufferDroidSans != nullptr) free(fontBufferDroidSans);
}
extern "C"
JNIEXPORT void JNICALL
Java_git_artdeell_skymodloader_ImGUI_init(JNIEnv *env, jclass clazz, jobject surface, jfloat fontsize, jfloat scale, jobject assetManager, jboolean enable_droid_sans) {
    initContext();
    init_sfc(env, surface);
    env->GetJavaVM(&jvm);
    class_ImGUI = (jclass)env->NewGlobalRef(clazz);
    method_setClipboard = env->GetStaticMethodID(clazz, "setClipboard", "(Ljava/lang/String;)V");
    method_getClipboard = env->GetStaticMethodID(clazz, "getClipboard", "()Ljava/lang/String;");
    ImGui::CreateContext();
    ImGuiIO& io = ImGui::GetIO();
    io.IniFilename = nullptr;
    io.BackendPlatformName = "imgui4canvas";
    io.DisplayFramebufferScale = ImVec2(1, 1);
    io.GetClipboardTextFn = &androidbk_get_clipboard;
    io.SetClipboardTextFn = &androidbk_set_clipboard;
    //ImFontConfig font_cfg;
    //font_cfg.SizePixels = 22.0f;
    //io.Fonts->AddFontDefault(&font_cfg);
    loadFonts(io, fontsize, AAssetManager_fromJava(env, assetManager), enable_droid_sans);

    g_time = 0;
    ImGui::StyleColorsDark();
    ImGui_ImplOpenGL3_Init("#version 300 es");
    ImGui::GetStyle().ScaleAllSizes(scale);
    renderloop();
}


extern "C"
JNIEXPORT void JNICALL
Java_git_artdeell_skymodloader_ImGUI_shutdown(JNIEnv *env, jclass clazz) {
    run = false;
}
extern "C"
JNIEXPORT void JNICALL
Java_git_artdeell_skymodloader_ImGUI_submitPositionEvent(JNIEnv *env, jclass clazz, jfloat x, jfloat y) {
    CanvasInput::PushMousePos(x, y);
}
extern "C"
JNIEXPORT void JNICALL
Java_git_artdeell_skymodloader_ImGUI_submitButtonEvent(JNIEnv *env, jclass clazz, jint btn,
                                                       jboolean pressed) {
    CanvasInput::PushMouseButton(btn, pressed);
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_git_artdeell_skymodloader_ImGUI_wantsKeyboard(JNIEnv *env, jclass clazz) {
    // Previously called ImGui::UpdateHoveredWindowAndCaptureFlags() here, which
    // mutates g.HoveredWindow from the UI thread mid-frame.  Served from the
    // render thread's published snapshot now.
    return CanvasInput::WantsKeyboard();
}
extern "C"
JNIEXPORT void JNICALL
Java_git_artdeell_skymodloader_ImGUI_submitUnicodeEvent(JNIEnv *env, jclass clazz,
                                                        jchar codepoint) {
    CanvasInput::PushChar((unsigned)codepoint);
}
extern "C"
JNIEXPORT void JNICALL
Java_git_artdeell_skymodloader_ImGUI_clearTextFocus(JNIEnv *env, jclass clazz) {
    CanvasInput::RequestClearTextFocus();
}
extern "C"
JNIEXPORT void JNICALL
Java_git_artdeell_skymodloader_ImGUI_submitKeyEvent(JNIEnv *env, jclass clazz, jint key,
                                                    jboolean down) {
    // Called from ImGUITextInput on the UI thread; the keycode is mapped on the
    // render thread inside Drain() so nothing touches the context here.
    CanvasInput::PushKey(key, down);
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_git_artdeell_skymodloader_ImGUI_wantsMouse(JNIEnv *env, jclass clazz) {
    // Position-aware hit test against the render thread's published rects, so a
    // touch landing on a window is still claimed on its very first event (what
    // the old mid-frame UpdateHoveredWindowAndCaptureFlags() call achieved),
    // without reading live context state.
    return CanvasInput::WantsMouse();
}
extern "C"
JNIEXPORT void JNICALL
Java_git_artdeell_skymodloader_ImGUI_resurface(JNIEnv *env, jclass clazz, jobject surface) {
    init_sfc(env, surface);
    eglMakeCurrent(g_EglDisplay, g_EglSurface, g_EglSurface, g_EglContext);
    run = true;
    renderloop();
}