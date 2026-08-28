#include <android/log.h>
#include <dlfcn.h>
#include <pthread.h>
#include <thread>
#include <unistd.h>
#include <string>
#include "include/misc/Logger.h"
#include "main.h"
#include "Core/imgui/imgui.h"
#include "Utils/imgui_androidbk/androidbk.h"
#include "include/misc/Vector3.h"
#include <atomic>
#include <cmath>
#include <cstring>
#include <limits>
#include <vector>
#include "Canvas/Canvas.h"
#include "Cipher/CipherUtils.h"
#include "Cipher/Cipher.h"
#include "include/misc/visibility.h"
#include "Iconloader/IconLoader.h"
#include "fileselector/fileselector.h"
#include <android/asset_manager_jni.h>
#include "shadowhook.h"
#include "Core/starwatch/starwatch_block.h"

extern "C" {
#include "Core/ceserver/ceserver.h"
}

static std::string g_skyBuildKey;

void do_scroll();
void fsel_setup(JNIEnv*);

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    Canvas::javaVM = vm;
    Canvas::javaVM->GetEnv((void **) &Canvas::jniEnv, JNI_VERSION_1_6);
    return JNI_VERSION_1_6;
}


char test_file_buf[512] = {0};
int gfd = -2;
void file_selector_cb(int fd) {
    if(fd != -1) {
        if(gfd == -3) {
            const size_t len = strlen(test_file_buf);
            write(fd, test_file_buf, len);
        }else{
            gfd = fd;
        }
        close(fd);
    }
}


PRIVATE_API void SystemsTest() {
    ImGui::Begin("System Tests");

    ImGui::Text("is Game Beta? %s", Cipher::isGameBeta() ? "true" : "false");
    ImGui::Text("is Game Beta? %s", CipherUtils::get_GameType() == GameType::Beta ? "true" : "false");

    if (ImGui::Button("haptic success")) {
        CipherUtils::performHapticFeedback(HapticFeedbackType::TYPE_SUCCESS);
    }
    if (ImGui::Button("haptic success strong")) {
        CipherUtils::performHapticFeedback(HapticFeedbackType::TYPE_SUCCESS_STRONG);
    }
    if (ImGui::Button("haptic warning")) {
        CipherUtils::performHapticFeedback(HapticFeedbackType::TYPE_WARNING);
    }
    if (ImGui::Button("haptic error")) {
        CipherUtils::performHapticFeedback(HapticFeedbackType::TYPE_ERROR);
    }
    if (ImGui::Button("haptic selection")) {
        CipherUtils::performHapticFeedback(HapticFeedbackType::TYPE_SELECTION);
    }
    if (ImGui::Button("haptic impact light")) {
        CipherUtils::performHapticFeedback(HapticFeedbackType::TYPE_IMPACT_LIGHT);
    }
    if (ImGui::Button("haptic impact")) {
        CipherUtils::performHapticFeedback(HapticFeedbackType::TYPE_IMPACT);
    }
    
    ImGui::End();

}

PRIVATE_API static void HelpMarker(const Canvas::UserLib& userLib)
{
    ImGui::TextDisabled("(?)");
    if (ImGui::BeginItemTooltip())
    {
        ImGui::PushTextWrapPos(ImGui::GetFontSize() * 35.0f);
        ImGui::TextWrapped("%s: %s", userLib.Name.c_str(), userLib.Version.c_str());
        if (!userLib.Author.empty()) {
            ImGui::TextWrapped("Author: %s", userLib.Author.c_str());
        }

        if (!userLib.Description.empty()) {
            ImGui::Separator();
            ImGui::TextWrapped("%s", userLib.Description.c_str());
        }

        ImGui::PopTextWrapPos();
        ImGui::EndTooltip();
    }
}

PRIVATE_API void DrawMods() {
    // SystemsTest();
    for (auto &userLib: Canvas::userLibs) {
        if (userLib.UIEnabled) {
            if (userLib.UISelfManaged) {
                ImGui::PushID(userLib.Name.c_str());
            } else {
                ImGui::Begin(userLib.Name.c_str());
            }

            userLib.Draw(&userLib.UIEnabled);

            if (userLib.UISelfManaged) {
                ImGui::PopID();
            } else {
                ImGui::End();
            }
        }
    }
}

PRIVATE_API void Canvas::CanvasMenu() {
    if(Canvas::hideCanvasMenu) {
        DrawMods();
        return;
    }

    ImGui::Begin("Canvas Menu");
    if (!Canvas::userLibs.empty() && ImGui::BeginTable(
            "Mods##canvas_mods_table",
            2,
            ImGuiTableFlags_Borders
               | ImGuiTableFlags_RowBg
               | ImGuiTableFlags_BordersH
               | ImGuiTableFlags_BordersOuterH,
            ImVec2(-1.0f, 0.0f))
    ) {
        ImGui::TableSetupColumn("Mod", ImGuiTableColumnFlags_WidthStretch);
        ImGui::TableSetupColumn(
                "Info",
                ImGuiTableColumnFlags_WidthFixed,
                ImGui::CalcTextSize("Info").x + (20.0f / (24 / ImGui::GetFont()->FontSize))
        );
        ImGui::TableHeadersRow();
        for (auto &userLib: Canvas::userLibs) {
            ImGui::TableNextRow();
            ImGui::TableSetColumnIndex(0);
            ImGui::Checkbox(userLib.Name.c_str(), &userLib.UIEnabled);

            ImGui::TableSetColumnIndex(1);
            HelpMarker(userLib);

        }
        ImGui::EndTable();
    }

    DrawMods();
    ImGui::Text(
            "Application average %.3f ms/frame (%.1f FPS)",
            1000.0f / ImGui::GetIO().Framerate,
            ImGui::GetIO().Framerate
    );
    ImGui::Checkbox("Limit FPS", &Canvas::frameRateLimited);
    ImGui::End();

    //SystemsTest();
}

void (*ImGuiEnd)();
void ImGuiEndHook() {
    do_scroll();
    return ImGuiEnd();
}

__unused __attribute__((constructor))
int main() {
    LOGI("Starting Sky ModMenu.. Build time: " __DATE__ " " __TIME__);
    shadowhook_init(SHADOWHOOK_MODE_UNIQUE, false);
    starwatch_block_install();
    Canvas::libName = "libBootloader.so";
    do {
        sleep(1);
    } while (!Canvas::isLibLoaded(Canvas::libName));
    (new CipherHook)->set_Hook((std::uintptr_t)ImGuiEndHook)
    ->set_Callback((std::uintptr_t)&ImGuiEnd)
    ->set_Address("_ZN5ImGui3EndEv")
    ->Fire();

    return 0;
}

extern "C"
JNIEXPORT void JNICALL
Java_git_artdeell_skymodloader_MainActivity_settle(
        JNIEnv *env,
        jclass clazz,
        jint _gameVersion,
        jint _gameType,
        jstring _hostName,
        jstring _configDir,
        jobject _gameAssets,
        jboolean _ceserver_enabled,
        jboolean _hideCanvasMenu
        ) {
    fsel_setup(env);
    Canvas::userLibs.clear();
    Canvas::MainActivity = clazz;
    Canvas::CeserverEnabled = _ceserver_enabled;
    Canvas::hideCanvasMenu = _hideCanvasMenu;
    Canvas::gameVersion = _gameVersion;
    Canvas::gameType = _gameType;
    Canvas::configsPath = (*env).GetStringUTFChars(_configDir, NULL);
    Canvas::aAssetManager = AAssetManager_fromJava(env, _gameAssets);

    Canvas::libElfScanner = ElfScanner::createWithPath(Canvas::libName);
    Canvas::libBase = Canvas::libElfScanner.baseSegment().startAddress;

    if (Canvas::CeserverEnabled) {
        LOGI("Starting Cheat Engine Server");
        start_ceserver();
    }

}


typedef void (*func)();
PRIVATE_API void *UserThread(Canvas::UserLib *pUserLib){
    func (*Start)() = (func(*)())pUserLib->Start;
    pUserLib->Draw = (void (*)(bool *))Start();
    if(Canvas::hideCanvasMenu) {
        pUserLib->UIEnabled = true;
    }
    if (!pUserLib->Name.empty() && pUserLib->Draw && pUserLib->DisplaysUI) {
        Canvas::pushUserLib(*pUserLib);
    }
    delete pUserLib;
    return nullptr;
}

PRIVATE_API void crash(JNIEnv *env, char* crashReason) {
    jclass exception =  env->FindClass("java/lang/Exception");
    if(exception != nullptr) {
        env->ThrowNew(exception, crashReason);
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_git_artdeell_skymodloader_LibrarySelectorListener_onModLibrary(
        JNIEnv *env,
        jclass clazz,
        jstring _path,
        jboolean _isDraw,
        jstring _displayName,
        jstring _author,
        jstring _description,
        jstring _version,
        jboolean _selfManagedUI
) {

    const char *temp = env->GetStringUTFChars(_path, 0);
    void *dl_entry = dlopen(temp, RTLD_LOCAL);
    if(dl_entry == nullptr) {
        crash(env, dlerror());
        return;
    }

    env->ReleaseStringUTFChars(_path, temp);

    func (*Start)() = (func (*)()) dlsym( dl_entry, "Start");
    if (Start == nullptr) {
        crash(env, dlerror());
        return;
    }

    Canvas::UserLib* pUserLib = new Canvas::UserLib;
    pUserLib->DisplaysUI = _isDraw;
    pUserLib->UISelfManaged = _selfManagedUI;
    pUserLib->Name = env->GetStringUTFChars(_displayName, 0);
    pUserLib->Author = env->GetStringUTFChars(_author, 0);
    pUserLib->Description = env->GetStringUTFChars(_description, 0);
    pUserLib->Version = env->GetStringUTFChars(_version, 0);
    pUserLib->Start = (void (*)(void))(Start);

    func (*InitLate)() = (func (*)()) dlsym( dl_entry, "InitLate");
    pUserLib->InitLate = (void (*)(void))InitLate;

    UserThread(pUserLib);
}

extern "C"
JNIEXPORT void JNICALL
Java_git_artdeell_skymodloader_MainActivity_onKeyboardCompleteNative(
        JNIEnv *env,
        jclass clazz,
        jstring message
) {
    std::string msg = env->GetStringUTFChars(message, nullptr);
    for (auto& listener : Canvas::onKeyboardCompleteListeners) {
        listener(msg);
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_git_artdeell_skymodloader_MainActivity_setDeviceInfoNative(
        JNIEnv *env, jclass clazz,
        jfloat _xdpi,
        jfloat _ydpi,
        jfloat _density,
        jstring _deviceName,
        jstring _manufacturer,
        jstring _model
) {
    Canvas::deviceInfo.xdpi = _xdpi;
    Canvas::deviceInfo.ydpi = _ydpi;
    Canvas::deviceInfo.density = _density;
    Canvas::deviceInfo.deviceName = env->GetStringUTFChars(_deviceName, nullptr);
    Canvas::deviceInfo.deviceManufacturer = env->GetStringUTFChars(_manufacturer, nullptr);
    Canvas::deviceInfo.deviceModel = env->GetStringUTFChars(_model, nullptr);
}

extern "C"
JNIEXPORT void JNICALL
Java_git_artdeell_skymodloader_MainActivity_lateInitUserLibs(JNIEnv *env, jclass clazz) {
    std::thread lateInitThread([]() -> void {
        for (auto& userlib : Canvas::userLibs) {
            if (userlib.InitLate != nullptr) {
                userlib.InitLate();
                LOGI("InitLate() called for %s", userlib.Name.c_str());
            } else {
                LOGI("%s does not adopt a InitLate() function", userlib.Name.c_str());
            }
        }
    });
    lateInitThread.detach();
}

extern "C"
JNIEXPORT void JNICALL
Java_git_artdeell_skymodloader_MainActivity_customServer(JNIEnv *env, jclass clazz, jstring url) {
    uintptr_t ssl = (new CipherUtils())->CipherScan("\\x00\\x00\\x00\\x52\\xE2\\x03\\x1F\\xAA\\x00\\x00\\x00\\x94\\x00\\x00\\x00\\xF9\\x00\\x00\\x00\\x52", "???xxxxx???x???x???x");
    LOGD("scanner %p", ssl);
}

extern "C"
JNIEXPORT void JNICALL
Java_git_artdeell_skymodloader_MainActivity_getSysetemUI(
        JNIEnv *env,
        jclass clazz,
        jobject systemUI
) {
    Canvas::systemUI = env->NewGlobalRef(systemUI);
}

extern "C"
JNIEXPORT void JNICALL
Java_git_artdeell_skymodloader_MainActivity_nativeSetSkyBuildKey(
        JNIEnv *env,
        jclass clazz,
        jstring key
) {
    const char* keyStr = env->GetStringUTFChars(key, nullptr);
    if (keyStr) {
        g_skyBuildKey = std::string(keyStr);
        env->ReleaseStringUTFChars(key, keyStr);
        LOGI("SkyBuildKey set: %s", g_skyBuildKey.c_str());
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_git_artdeell_skymodloader_MainActivity_nativeSetStarwatchAllowed(
        JNIEnv *env,
        jclass clazz,
        jboolean allowed
) {
    starwatch_block_set_allowed(allowed == JNI_TRUE);
}

// ---------------------------------------------------------------------------
// Host value channel for user libs.
//
// One weak-linked entry point instead of one exported symbol per capability:
// a mod gates on this ONCE and every future key costs a string rather than an
// ABI addition and a Canvas release cycle.
//
// extern "C" on purpose.  A C++ static would tie both sides to an exact mangled
// name, and any signature drift would make the mod's weak symbol resolve null -
// which is indistinguishable from "this Canvas is too old", permanently.
//
// CONTRACT (mods rely on all three):
//   - callable from ANY thread, including a mod's ImGui thread
//   - never blocks: no JNI, no I/O, no lock.  Values are snapshots Canvas
//     already sampled on the thread that owns them.  ExoPlayer in particular
//     must only be touched on its application looper, so the video keys are
//     pushed down from UpdateMediaPlayer rather than pulled on demand.
//   - cheap enough to call per frame
//
// KEY REGISTRY - this comment is the source of truth, and the table in
// CanvasQueryHostNumber must match it exactly:
//
//   video.duration_ms    total length of the playing video.
//                        Absent for live streams, before the player reaches
//                        STATE_READY, and for containers that carry no
//                        duration at all (MPEG-TS).  "absent" therefore does
//                        NOT imply live - query video.is_live for that.
//   video.position_ms    current playback position.  For live content this is
//                        relative to the start of the live window, which moves.
//   video.is_live        1 / 0.  From Player.isCurrentMediaItemLive().
//   video.is_seekable    1 / 0.  From Player.isCurrentMediaItemSeekable().
//                        Having a duration does NOT imply being seekable.
//   video.is_buffering   1 while the player is stalled fetching data, else 0.
//                        Distinct from is_ended and from position simply not
//                        advancing, so a mod can show a spinner without guessing.
//   video.is_ended       1 when the player has reached the end of the video,
//                        else 0.  A looping mod should watch this rather than
//                        compare position against duration, which need not land
//                        exactly on it.  Kept a boolean, not the raw
//                        Player.STATE_* enum, so the channel does not marry a
//                        backend's state numbering.
//
// A key prefixed `is_` is a boolean: 1 or 0, never any other value.
// Read those through the typed wrappers rather than as raw numbers, and
// do not introduce an unprefixed boolean key alongside them.
// A key with no value right now reports false and leaves the caller's out
// parameter untouched - never a sentinel a caller might render.  See kAbsent
// below for how absence is stored.
namespace {
    // NaN, not a negative number, means "no value right now".  The query cannot
    // know a given key's legal range, so a negative sentinel would silently
    // swallow any future signed value - an audio balance, a live-edge offset -
    // at exactly the values it most needed to report, and would do it quietly:
    // the caller just sees "unavailable", which is a legal answer.  Deciding
    // absence belongs to each push site, which does know the range.
    constexpr double kAbsent = std::numeric_limits<double>::quiet_NaN();
    std::atomic<double> g_videoDurationMs{kAbsent};
    std::atomic<double> g_videoPositionMs{kAbsent};
    std::atomic<double> g_videoIsLive{kAbsent};
    std::atomic<double> g_videoIsSeekable{kAbsent};
    std::atomic<double> g_videoIsBuffering{kAbsent};
    std::atomic<double> g_videoIsEnded{kAbsent};
}

extern "C"
JNIEXPORT void JNICALL
Java_git_artdeell_skymodloader_MainActivity_nativeSetVideoStats(
        JNIEnv *env,
        jclass clazz,
        jlong durationMs,
        jlong positionMs,
        jint isLive,
        jint isSeekable,
        jint playbackState
) {
    // Java signals absence with -1 for all of these, none of which can
    // legitimately be negative.  Translate here, at the push site, so the stored
    // form stays range-agnostic.
    const auto put = [](std::atomic<double> &cell, long long v) {
        cell.store(v < 0 ? kAbsent : static_cast<double>(v),
                   std::memory_order_relaxed);
    };
    put(g_videoDurationMs, durationMs);
    put(g_videoPositionMs, positionMs);
    put(g_videoIsLive, isLive);
    put(g_videoIsSeekable, isSeekable);
    // Player.STATE_BUFFERING == 2, STATE_ENDED == 4.  A negative state means no
    // player, which stays absent; otherwise each collapses to its one bit.
    put(g_videoIsBuffering, playbackState < 0 ? -1 : (playbackState == 2));
    put(g_videoIsEnded, playbackState < 0 ? -1 : (playbackState == 4));
}

extern "C"
bool CanvasQueryHostNumber(const char *_key, double *_out) {
    if (_key == nullptr || _out == nullptr) return false;
    const struct { const char *key; std::atomic<double> *cell; } table[] = {
        {"video.duration_ms", &g_videoDurationMs},
        {"video.position_ms", &g_videoPositionMs},
        {"video.is_live",     &g_videoIsLive},
        {"video.is_seekable", &g_videoIsSeekable},
        {"video.is_buffering", &g_videoIsBuffering},
        {"video.is_ended",    &g_videoIsEnded},
    };
    for (const auto &e : table) {
        if (std::strcmp(_key, e.key) != 0) continue;
        const double v = e.cell->load(std::memory_order_relaxed);
        if (std::isnan(v)) return false;  // known key, no value right now
        *_out = v;
        return true;
    }
    return false;                    // unknown key: this Canvas is older
}
