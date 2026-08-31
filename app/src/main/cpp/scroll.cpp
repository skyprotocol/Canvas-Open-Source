//
// Created by maks on 06.12.2022.
//
#include "Core/imgui/imgui_internal.h"
#include "include/misc/visibility.h"

// Drag-to-scroll, run once per frame as an EndFramePre context hook
// (registered from androidbk's ImGUI_init, right after CreateContext).
//
// Deliberately NOT a runtime hook on ImGui::End: inline-patching a function
// in our own statically linked binary is silently defeated by emulator ARM
// translation caches (observed on MuMu - shadowhook reported success and the
// hook body never executed, killing drag-to-scroll and letting scroll
// gestures drag windows around instead). A context hook is plain compiled
// code going through ImGui's own extension point, works under any
// translation layer, and keeps the vendored imgui tree pristine.
//
// One call per frame on ctx.HoveredWindow covers every window:
// ButtonBehavior's ItemHoverable requires g.HoveredWindow == window, so no
// other window could ever act anyway. EndFramePre fires before
// UpdateMouseMovingWindowEndFrame (see ImGui::EndFrame), so a press-frame
// ActiveId claim here still suppresses ImGui's window-move for that click.
static void do_scroll(ImGuiContext *ctx_, ImGuiContextHook *) {
    ImGuiContext &ctx = *ctx_;
    ImGuiWindow *window = ctx.HoveredWindow;
    if (window == NULL || window->ScrollMax.y == 0 || ctx.HoveredId != 0)
        return;
    // ButtonBehavior reads GetCurrentWindow() for SetActiveID's owner window
    // and the flatten-children check; point it at the window being scrolled
    // for the duration. Plain assignment on purpose: SetCurrentWindow() is
    // file-static inside imgui.cpp, and its font bookkeeping is not needed
    // here - nothing is rendered.
    ImGuiWindow *backup = ctx.CurrentWindow;
    ctx.CurrentWindow = window;
    bool hovered = false, held = false;
    // InnerClipRect, NOT Rect(): the claim must exclude the title bar and
    // scrollbars, or title-bar presses get claimed as scrolls and the window
    // can never be moved by drag.
    ImGui::ButtonBehavior(window->InnerClipRect, window->GetID("###Canvas_scroll"),
                          &hovered, &held, ImGuiButtonFlags_MouseButtonLeft);
    // Scroll only while the primary button is held. A no-op for touch (a
    // valid position implies a finger down), but a hardware mouse hovers
    // with no button held, and without the gate every motion frame over
    // claim-eligible space would scroll the content.
    if (hovered && !held && ImGui::GetIO().MouseDown[0]) {
        ImVec2 &mouseDelta = ImGui::GetIO().MouseDelta;
        ImGui::SetScrollY(window, window->Scroll.y - mouseDelta.y);
    }
    ctx.CurrentWindow = backup;
}

PRIVATE_API void register_do_scroll(ImGuiContext *ctx) {
    ImGuiContextHook hook;
    hook.Type = ImGuiContextHookType_EndFramePre;
    hook.Callback = do_scroll;
    ImGui::AddContextHook(ctx, &hook);
}
