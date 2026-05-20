#pragma once

#include <cstdint>
#include <cstddef>
#include <string>

#include "Cipher.h"

// Internal helpers consumed by CipherHook::Fire / CipherPatch::Fire and
// their destructors. Mods include Cipher.h and see the public query
// surface there; they never need this header.
namespace CipherInternal {
    void RecordInstall(CipherBase* base, void* callerPC,
                       Types kind, std::uintptr_t address,
                       std::size_t bytes, const char* targetLib);
    void ForgetInstall(CipherBase* base);

    // Patch byte count side-table — kept off CipherPatch's class layout to
    // avoid an ABI bump. set_Opcode records, Fire reads.
    void NotePatchBytes(CipherBase* patch, std::size_t bytes);
    std::size_t LookupPatchBytes(CipherBase* patch);

    // Owner-string helper for the in-loop logs.
    std::string ResolveOwnerForLog(void* callerPC);
}
