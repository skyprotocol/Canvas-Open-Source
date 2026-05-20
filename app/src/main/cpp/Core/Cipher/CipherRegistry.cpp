#include "Cipher.h"
#include "CipherRegistry.h"
#include "CipherUtils.h"

#include <algorithm>
#include <atomic>
#include <cstring>
#include <dlfcn.h>
#include <functional>
#include <mutex>
#include <shared_mutex>
#include <unordered_map>
#include <utility>
#include <vector>

#include <Canvas/Canvas.h>
#include <KittyMemory/KittyInclude.hpp>

#include "../../include/misc/visibility.h"

namespace {

// Lazy-initialised state. Plain namespace-scope statics race with
// main.cpp's __attribute__((constructor)) main(), which fires Canvas's
// own ImGui hook at .so-load time — calling RecordInstall before this
// TU's dynamic init has run. Function-local statics avoid the race
// (C++11 guarantees thread-safe lazy init on first call).
struct RegistryState {
    std::shared_mutex                                                     mtx;
    std::unordered_map<CipherBase*, CipherInstallInfo>                    byInstance;
    std::unordered_map<CipherBase*, std::size_t>                          patchBytes;
    std::atomic<std::uint64_t>                                            seq{0};
    std::vector<std::function<void(const CipherInstallInfo&)>>            listeners;
};

RegistryState& State() {
    static RegistryState inst;
    return inst;
}

std::string ResolveOwnerImpl(void* callerPC) {
    if (!callerPC) return "<unknown>";
    Dl_info info{};
    if (!dladdr(callerPC, &info) || !info.dli_fname) return "<unknown>";
    const char* slash = std::strrchr(info.dli_fname, '/');
    return slash ? slash + 1 : info.dli_fname;
}

bool SegmentMatches(const KittyMemory::ProcMap& seg, const Flags& f) {
    switch (f) {
        case Flags::ReadOnly:        return seg.is_ro;
        case Flags::ReadAndWrite:    return seg.is_rw;
        case Flags::ReadAndExecute:  return seg.is_rx;
        case Flags::Any:             return seg.is_ro || seg.is_rw || seg.is_rx;
        default:                     return false;
    }
}

// Mirrors the segment-derivation in CipherUtils::CipherScan(bytes,mask,flag,…).
// Returns empty if Canvas::libElfScanner isn't ready yet — i.e. mod fired
// before the Java settle() callback completed.
std::vector<std::pair<std::uintptr_t, std::uintptr_t>>
ResolveScanRanges(const char* libName, const Flags& flag) {
    std::vector<std::pair<std::uintptr_t, std::uintptr_t>> out;
    const char* eff = (libName && libName[0]) ? libName : Canvas::libName;
    if (!eff) return out;
    const auto scanner = (std::strcmp(eff, Canvas::libName)
        ? KittyScanner::ElfScanner::createWithPath(eff)
        : Canvas::libElfScanner);
    if (!scanner.isValid()) return out;
    for (const auto& seg : scanner.segments()) {
        if (SegmentMatches(seg, flag)) {
            out.emplace_back(seg.startAddress, seg.endAddress);
        }
    }
    return out;
}

} // anonymous namespace

// ---------------- internal hooks (called from Fire/dtor) -----------------

namespace CipherInternal {

PRIVATE_API void RecordInstall(CipherBase* base, void* callerPC,
                                Types kind, std::uintptr_t address,
                                std::size_t bytes, const char* targetLib)
{
    CipherInstallInfo info{
        ResolveOwnerImpl(callerPC),
        targetLib ? std::string(targetLib) : std::string{},
        kind,
        address,
        bytes,
        State().seq.fetch_add(1, std::memory_order_relaxed)
    };

    {
        std::unique_lock<std::shared_mutex> lock(State().mtx);
        State().byInstance[base] = info;
    }

    // Snapshot listeners under shared lock; invoke outside to avoid
    // re-entrancy deadlocks if a listener queries the registry.
    std::vector<std::function<void(const CipherInstallInfo&)>> snap;
    {
        std::shared_lock<std::shared_mutex> lock(State().mtx);
        snap = State().listeners;
    }
    for (const auto& cb : snap) cb(info);
}

PRIVATE_API void ForgetInstall(CipherBase* base) {
    std::unique_lock<std::shared_mutex> lock(State().mtx);
    State().byInstance.erase(base);
    State().patchBytes.erase(base);  // no-op for hooks
}

PRIVATE_API void NotePatchBytes(CipherBase* patch, std::size_t bytes) {
    std::unique_lock<std::shared_mutex> lock(State().mtx);
    State().patchBytes[patch] = bytes;
}

PRIVATE_API std::size_t LookupPatchBytes(CipherBase* patch) {
    std::shared_lock<std::shared_mutex> lock(State().mtx);
    auto it = State().patchBytes.find(patch);
    return it != State().patchBytes.end() ? it->second : 0;
}

PRIVATE_API std::string ResolveOwnerForLog(void* callerPC) {
    return ResolveOwnerImpl(callerPC);
}

} // namespace CipherInternal

// ---------------- public Cipher:: query surface --------------------------

bool Cipher::IsAddressPatched(std::uintptr_t addr) {
    std::shared_lock<std::shared_mutex> lock(State().mtx);
    for (const auto& kv : State().byInstance) {
        const auto& i = kv.second;
        if (addr >= i.address && addr < i.address + i.bytes) return true;
    }
    return false;
}

std::vector<CipherInstallInfo> Cipher::DescribePatchesAt(std::uintptr_t addr) {
    std::vector<CipherInstallInfo> out;
    {
        std::shared_lock<std::shared_mutex> lock(State().mtx);
        for (const auto& kv : State().byInstance) {
            const auto& i = kv.second;
            if (addr >= i.address && addr < i.address + i.bytes) out.push_back(i);
        }
    }
    std::sort(out.begin(), out.end(),
              [](const CipherInstallInfo& a, const CipherInstallInfo& b){
                  return a.installSeq < b.installSeq;
              });
    return out;
}

std::vector<CipherInstallInfo>
Cipher::DescribePatchesIn(std::uintptr_t start, std::size_t len) {
    const std::uintptr_t end = start + len;
    std::vector<CipherInstallInfo> out;
    {
        std::shared_lock<std::shared_mutex> lock(State().mtx);
        for (const auto& kv : State().byInstance) {
            const auto& i = kv.second;
            if (i.address < end && (i.address + i.bytes) > start) out.push_back(i);
        }
    }
    std::sort(out.begin(), out.end(),
              [](const CipherInstallInfo& a, const CipherInstallInfo& b){
                  return a.installSeq < b.installSeq;
              });
    return out;
}

std::vector<CipherInstallInfo>
Cipher::ExplainScanFailure(const char* /*bytes*/, const char* mask, const char* libName)
{
    if (!mask) return {};
    const auto ranges = ResolveScanRanges(libName, Flags::Any);
    if (ranges.empty()) return {};

    std::vector<CipherInstallInfo> hits;
    {
        std::shared_lock<std::shared_mutex> lock(State().mtx);
        for (const auto& kv : State().byInstance) {
            const auto& i = kv.second;
            for (const auto& range : ranges) {
                if (i.address < range.second && (i.address + i.bytes) > range.first) {
                    hits.push_back(i);
                    break;
                }
            }
        }
    }
    std::sort(hits.begin(), hits.end(),
              [](const CipherInstallInfo& a, const CipherInstallInfo& b){
                  return a.installSeq < b.installSeq;
              });
    return hits;
}

std::vector<CipherInstallInfo>
Cipher::ExplainScanFailure(const std::string& idaPattern, const char* libName)
{
    char bytesBuf[256] = {0};
    std::string maskBuf;
    CipherUtils::patternToBytes(idaPattern, bytesBuf, maskBuf);
    return ExplainScanFailure(bytesBuf, maskBuf.c_str(), libName);
}

void Cipher::OnInstallEvent(std::function<void(const CipherInstallInfo&)> cb) {
    if (!cb) return;
    std::unique_lock<std::shared_mutex> lock(State().mtx);
    State().listeners.push_back(std::move(cb));
}
