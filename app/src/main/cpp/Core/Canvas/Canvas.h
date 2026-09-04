#pragma once

#include <vector>
#include <string>
#include <jni.h>
#include <android/asset_manager_jni.h>

#include <KittyMemory/KittyInclude.hpp>
#include "imgui/imgui.h"

namespace Canvas {
    struct UserLib {
        bool UIEnabled = false;
        bool UISelfManaged;
        bool DisplaysUI;
        std::string Name;
        std::string Author;
        std::string Description;
        std::string Version;
        void (*InitLate)();
        void (*Draw)(bool*);
        void (*Start)();
        ImGuiStyle Style;
        bool StyleSeeded = false;
    };

    struct DeviceInfo {
        float xdpi;
        float ydpi;
        float density;
        std::string deviceName;
        std::string deviceManufacturer;
        std::string deviceModel;
    };

    extern DeviceInfo deviceInfo;
    extern const char* gameHost;

    extern const char* libName;
    extern std::uintptr_t libBase;
    extern KittyScanner::ElfScanner libElfScanner;

    extern std::vector<UserLib> userLibs;
    extern void pushUserLib(UserLib& _userLib);

    extern AAssetManager *aAssetManager;
    extern bool dev;

    extern bool isLibLoaded(const char* _elfName);
    extern std::uintptr_t findLib(const char* _elfName);
    extern void CanvasMenu();


    extern _Atomic std::uint32_t gameVersion;
    extern int gameType;
    extern bool frameRateLimited;
    extern JavaVM *javaVM;
    extern JNIEnv *jniEnv;
    extern jobject systemUI;
    extern jclass MainActivity;
    extern bool CeserverEnabled;
    extern bool hideCanvasMenu;
    extern const char *configsPath;

    extern std::vector<void (*)(std::string)> onKeyboardCompleteListeners;
}
