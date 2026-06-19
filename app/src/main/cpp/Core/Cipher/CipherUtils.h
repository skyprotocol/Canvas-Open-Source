#pragma once

#include <vector>
#include <string>

/**
 * @brief Struct representing device information.
 */
struct DeviceInfo {
    float xdpi;                         /**< X-axis dots per inch (dpi) of the device screen. */
    float ydpi;                         /**< Y-axis dots per inch (dpi) of the device screen. */
    float density;                      /**< Pixel density of the device screen. */
    std::string deviceName;             /**< Name of the device. */
    std::string deviceManufacturer;     /**< Manufacturer of the device. */
    std::string deviceModel;            /**< Model of the device. */
};

/**
 * @brief Specifies the type of the game.
 */
enum GameType {
    Live = 0,   /**< Represents the live version of the game. */
    Beta,       /**< Represents the beta version of the game. */
    Huawei      /**< Represents the Huawei version of the game. */
};

/**
 * @brief Specifies memory segment permissions.
 */
enum Flags {
    ReadOnly = 10,      /**< Read-only segment (rodata). */
    ReadAndWrite,       /**< Read-write segment (bss). */
    ReadAndExecute,     /**< Read-execute segment (text). */
    Any                 /**< Any segment. */
};

enum HapticFeedbackType {
    TYPE_SUCCESS = 0,           /**< Success feedback. */
    TYPE_SUCCESS_STRONG = 1,    /**< Strong success feedback. */
    TYPE_WARNING = 2,           /**< Warning feedback. */
    TYPE_ERROR = 3,             /**< Error feedback. */
    TYPE_SELECTION = 4,         /**< Selection feedback. */
    TYPE_IMPACT_LIGHT = 5,      /**< Light impact feedback. */
    TYPE_IMPACT = 6,            /**< Impact feedback. */
    TYPE_IMPACT_HEAVY = 7       /**< Heavy impact feedback. */
};

/**
 * @brief Provides functionalities for interacting with game memory, scanning patterns, and accessing game information.
 */
class CipherUtils {
public:
    /**
     * @brief Retrieves the game version.
     * @return The game version as a 32-bit unsigned integer.
     */
    static std::uint32_t get_GameVersion();

    /**
     * @brief Retrieves the game type.
     * @return The game type.
     *
     * Possible return values:
     * - GameType::Live: Represents the live version of the game.
     * - GameType::Beta: Represents the beta version of the game.
     * - GameType::Huawei: Represents the Huawei version of the game.
     */
    static GameType get_GameType();

    /**
     * @brief Retrieves the name of the library.
     * @return The name of the library.
     */
    static const char *get_libName();

    /**
     * @brief Retrieves the base address of the library.
     * @return The base address of the library.
     */
    static std::uintptr_t get_libBase();

    /**
     * @brief Retrieves the path for mod configurations.
     * @return The path for mod configurations.
     */
    static const char *get_ConfigsPath();

    /**
     * @brief Reads an asset from the given asset path.
     * @param _assetPath The path of the asset to read.
     * @return A pointer to the buffer containing the asset data.
     */
    static char *readAsset(const char *_assetPath);

    /**
     * @brief Adds a listener for keyboard events.
     * @param _listener The listener function to be added.
     */
    static void addOnKeyboardCompleteListener(void (*_listener)(std::string _message));

    /**
    * @brief Adds a message to the user's chat.
    * @param _message The string to be added.
    */
    static DeviceInfo get_DeviceInfo();

    /**
     * @brief Perform haptic feedback of the specified type
     * @param type The type of haptic feedback
     */
    static void performHapticFeedback(HapticFeedbackType _type);

    /**
     * @brief Open the Android share sheet for a file via the app's FileProvider.
     * The chooser is launched on the UI thread.  `_path` must live under a
     * FileProvider-configured directory (e.g. a subdir of get_ConfigsPath()).
     * @param _path Absolute path of the file to share.
     * @param _mimeType MIME type, e.g. "text/plain"; empty or null falls back to
     *        a generic binary type.
     */
    static void shareFile(const char *_path, const char *_mimeType);

public:
    /**
     * @brief Converts a pattern string into bytes and a mask for memory scanning.
     * @param _pattern The pattern string to convert.
     * @param _bytesBuffer The buffer to store the converted bytes.
     * @param _maskBuffer The buffer to store the mask.
     */
    static void patternToBytes(const std::string _pattern, char *_bytesBuffer, std::string &_maskBuffer);

    /**
     * @brief Scans memory for a specific pattern within a given range.
     * @param _start The start address of the memory range to scan.
     * @param _end The end address of the memory range to scan.
     * @param _bytes The pattern bytes to search for.
     * @param _mask The mask corresponding to the pattern bytes.
     * @return The address of the found pattern, or 0 if not found.
     */
    static std::uintptr_t CipherScan(const std::uintptr_t _start, const std::uintptr_t _end, const char *_bytes, const char *_mask);

    /**
     * @brief Scans memory for a specific pattern within a specific memory segment in the lib range.
     * @param _bytes The pattern bytes to search for.
     * @param _mask The mask corresponding to the pattern bytes.
     * @param _flag The flag indicating the memory segment type to search in.
     * @param _start The start address of the scan in the specified memory segment.
     * @param _libName The name of the library to scan.
     * @return The address of the found pattern, or 0 if not found.
     */
    static std::uintptr_t CipherScan(const char *_bytes, const char *_mask, const Flags &_flag = Flags::Any, const std::uintptr_t &_start = 0x0, const char *_libName = nullptr);

    /**
     * @brief Scans memory for all occurrences of a specific pattern within a given range.
     * @param _start The start address of the memory range to scan.
     * @param _end The end address of the memory range to scan.
     * @param _bytes The pattern bytes to search for.
     * @param _mask The mask corresponding to the pattern bytes.
     * @return A vector containing the addresses of all found patterns.
     */
    static std::vector<std::uintptr_t> CipherScanAll(const std::uintptr_t _start, const std::uintptr_t _end, const char *_bytes, const char *_mask
    );

    /**
     * @brief Scans memory for all occurrences of a specific pattern within a specific memory segment.
     * @param _bytes The pattern bytes to search for.
     * @param _mask The mask corresponding to the pattern bytes.
     * @param _flag The flag indicating the memory segment type to search in.
     * @param _start The start address of the memory segment to scan.
     * @param _libName The name of the library to scan.
     * @return A vector containing the addresses of all found patterns.
     */
    static std::vector<std::uintptr_t> CipherScanAll(const char *_bytes, const char *_mask, const Flags &_flag = Flags::Any, const std::uintptr_t &_start = 0x0, const char *_libName = nullptr);

    /**
     * @brief Scans memory for a pattern defined as a string within a given range or within a specific memory segment.
     * @param _start The start address of the memory range to scan.
     * @param _end The end address of the memory range to scan.
     * @param _pattern The pattern string to search for.
     * @return The address of the found pattern, or 0 if not found.
     */
    static std::uintptr_t CipherScanPattern(const std::uintptr_t _start, const std::uintptr_t _end, const char *_pattern);

    /**
     * @brief Scans memory for a pattern defined as a string within a specific memory segment.
     * @param _pattern The pattern string to search for.
     * @param _flag The flag indicating the memory segment type to search in.
     * @param _start The start address of the memory segment to scan.
     * @param _libName The name of the library to scan.
     * @return The address of the found pattern, or 0 if not found.
     */
    static std::uintptr_t CipherScanPattern(const char *_pattern, const Flags &_flag = Flags::Any, const std::uintptr_t &_start = 0x0, const char *_libName = nullptr);

    /**
     * @brief Scans memory for all occurrences of a pattern defined as a string within a given range or within a specific memory segment.
     * @param _start The start address of the memory range to scan.
     * @param _end The end address of the memory range to scan.
     * @param _pattern The pattern string to search for.
     * @return A vector containing the addresses of all found patterns.
     */
    static std::vector<std::uintptr_t> CipherScanPatternAll(const std::uintptr_t _start, const std::uintptr_t _end, const char *_pattern);

    /**
     * @brief Scans memory for all occurrences of a pattern defined as a string within a specific memory segment.
     * @param _pattern The pattern string to search for.
     * @param _flag The flag indicating the memory segment type to search in.
     * @param _start The start address of the memory segment to scan.
     * @param _libName The name of the library to scan.
     * @return A vector containing the addresses of all found patterns.
     */
    static std::vector<std::uintptr_t> CipherScanPatternAll(const char *_pattern, const Flags &_flag = Flags::Any, const std::uintptr_t &_start = 0x0, const char *_libName = nullptr);
};
