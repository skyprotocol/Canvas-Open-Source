#include "Canvas.h"

#include "../../include/misc/Utils.h"
#include "../../include/misc/visibility.h"


namespace Canvas {

    PRIVATE_API DeviceInfo deviceInfo;
    PRIVATE_API const char* gameHost;
    PRIVATE_API const char* libName;
    PRIVATE_API std::uintptr_t libBase;
    PRIVATE_API KittyScanner::ElfScanner libElfScanner;

    PRIVATE_API std::vector<UserLib> userLibs;
    PRIVATE_API void pushUserLib(UserLib& _userLib) {
        Canvas::userLibs.push_back(_userLib);
    }

    PRIVATE_API bool isLibLoaded(const char* _elfName) {
        return isLibraryLoaded(_elfName);
    }

    PRIVATE_API std::uintptr_t findLib(const char* _elfName) {
        return findLibrary(_elfName);
    }

    PRIVATE_API void CanvasMenu();

    PRIVATE_API AAssetManager* aAssetManager;
    PRIVATE_API bool dev = false;
    PRIVATE_API _Atomic std::uint32_t gameVersion;
    PRIVATE_API int gameType;
    PRIVATE_API bool frameRateLimited;
    PRIVATE_API JavaVM *javaVM;
    PRIVATE_API JNIEnv *jniEnv;
    PRIVATE_API jobject systemUI;
    PRIVATE_API jclass MainActivity;
    PRIVATE_API bool CeserverEnabled;
    PRIVATE_API bool hideCanvasMenu;
    PRIVATE_API const char *configsPath;

    PRIVATE_API std::vector<void (*)(std::string)> onKeyboardCompleteListeners;
    PRIVATE_API std::mutex onKeyboardCompleteListenersMutex;
}