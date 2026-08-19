//
// Created by maks on 11.12.2022.
//

#ifndef SKY_MODLOADER_FILESELECTOR_H
#define SKY_MODLOADER_FILESELECTOR_H

#include <cstdlib>

/**
 * Callback function for requestFile
 * fd - the file descriptor
 * You need to close it yourself
 */
typedef void (*callback_function)(int fd);

/**
 * Request a file from the user
 * mime_type - the MIME type that you want to select
 * callback - the callback function that will be called when a file is selected
 * save - whether you need to save (true) or load (false) a file with the selector
 * Returns:
 * true if the file selection was requested
 * false if the selector is already busy
 */
bool requestFile(const char* mime_type, callback_function callback, bool save);

/**
 * Request a file from the user, accepting any of multiple MIME types.
 * mime_types - array of MIME type strings (e.g. {"application/json", "text/plain"})
 * mime_type_count - number of entries; must be > 0
 * callback - called when a file is selected
 * save - whether you need to save (true) or load (false)
 * Returns:
 * true if the file selection was requested
 * false if the selector is busy or the input is invalid
 *
 * In load mode the picker filters via EXTRA_MIME_TYPES; some OEM pickers
 * (Huawei, MIUI) ignore the filter, so callers must validate contents.
 * In save mode only mime_types[0] is honored.
 */
bool requestFileMulti(const char* const* mime_types,
                      size_t mime_type_count,
                      callback_function callback,
                      bool save);

/**
 * requestFileMulti, plus a proposed file name for save mode.
 * suggested_name - initial name shown in the picker, or nullptr/"" for none.
 *                  Ignored when save is false; the user can always change it.
 *
 * Pass this whenever save is true. ACTION_CREATE_DOCUMENT takes its initial
 * name from EXTRA_TITLE, and with no name the picker's field opens empty - which
 * the platform does not leave empty: an empty display name comes back as the
 * literal "(invalid)" from FileUtils.buildValidFatFilename, so the file is
 * created as "(invalid).<ext>".
 *
 * Include the extension. FileUtils.splitFileName keeps it when it maps back to
 * mime_types[0] and swaps it for the type's own otherwise, so there is no
 * double-suffix case to avoid. Illegal characters, over-long names and
 * collisions are all handled by the provider - it appends "(1)" rather than
 * overwriting.
 */
bool requestFileMultiNamed(const char* const* mime_types,
                           size_t mime_type_count,
                           callback_function callback,
                           bool save,
                           const char* suggested_name);

/**
 * Callback for requestFiles.
 * count - number of file descriptors; 0 on cancel or all-failed
 * fds - valid only during the callback; callee owns the fds and must close
 *       them before returning, or copy them out for later use.
 */
typedef void (*callback_function_files)(size_t count, const int* fds);

/**
 * Request one or more files from the user via a multi-select picker.
 * mime_types - array of MIME type strings; must contain at least one entry
 * mime_type_count - number of entries; must be > 0
 * callback - invoked with the selected fds; count == 0 on cancel
 * Returns:
 * true if the file selection was requested
 * false if the selector is busy or the input is invalid
 *
 * Picking one file still fires the callback with count == 1.  Some OEM
 * pickers (Samsung, MIUI) ignore EXTRA_ALLOW_MULTIPLE and EXTRA_MIME_TYPES,
 * so callers must validate contents.
 */
bool requestFiles(const char* const* mime_types,
                  size_t mime_type_count,
                  callback_function_files callback);

#endif //SKY_MODLOADER_FILESELECTOR_H
