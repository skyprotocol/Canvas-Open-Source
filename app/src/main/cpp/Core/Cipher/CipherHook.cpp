#include "Cipher.h"
#include "CipherRegistry.h"

#include <mutex>

#include "Canvas/Canvas.h"

#include "../include/misc/Logger.h"
#include <shadowhook.h>

#include "../../include/misc/visibility.h"

PRIVATE_API std::recursive_mutex cipher_hook_mtx;

CipherHook::CipherHook()
        : p_Hook(0),
        p_Callback(0),
        stub(nullptr) {
    this->m_type = Types::e_hook;
    this->set_libName(Canvas::libName);
}

CipherHook::~CipherHook() {
    this->m_Restore();
    return;
}

//sets detour function
CipherHook* CipherHook::set_Hook(std::uintptr_t _hook) {
    this->p_Hook = _hook;
    LOGI("set_Hook: %lx", _hook);
    return this;
}

//sets callback to original function
CipherHook* CipherHook::set_Callback(std::uintptr_t _callback) {
    this->p_Callback = _callback;
    LOGI("set_Callback: %lx", _callback);
    return this;
}

CipherHook* CipherHook::Fire() {
    void* callerPC = __builtin_return_address(0);

    //check if fields are set
    const bool invalidHook = this->get_address() == 0 || this->p_Hook == 0;
    const bool invalidCallback = !this->get_Lock() && this->p_Callback == 0;
    const bool already = stub != nullptr;
    if (invalidHook || invalidCallback || already) {
        return this;
    }

    std::lock_guard<std::recursive_mutex> lock(cipher_hook_mtx);

    if (!this->get_Lock()) {
        for (auto& instance : CipherBase::s_InstanceVec) {
            CipherHook* pInstance = (CipherHook *)instance;
            if (pInstance->get_address() == this->get_address() && pInstance->get_Lock()) {
                const auto prior = Cipher::DescribePatchesAt(get_address());
                LOGW("Cipher: hook at %p rejected - locked by %s",
                     (void*)get_address(),
                     prior.empty() ? "<unknown>" : prior.front().ownerModule.c_str());
                return this;
            } else if (pInstance->get_address() == this->get_address()
                       && pInstance->m_type == Types::e_hook) {
                const auto prior = Cipher::DescribePatchesAt(get_address());
                LOGI("Cipher: hook chain at %p - prior=%s, new=%s",
                     (void*)get_address(),
                     prior.empty() ? "<unknown>" : prior.front().ownerModule.c_str(),
                     CipherInternal::ResolveOwnerForLog(callerPC).c_str());
                this->set_Address(pInstance->p_Hook, false); //hooks the hooked function instead
            }
        }
    }

    LOGD(
        "address: %p detour: %p callback: %p",
        this->get_address(),
        this->p_Hook,
        this->p_Callback
    );

    this->stub = shadowhook_hook_func_addr(
        (void *)this->get_address(),
        (void *)this->p_Hook,
        (void **)this->p_Callback
    );

    if (this->stub == nullptr) {
        int error_num = shadowhook_get_errno();
        const char *error_msg = shadowhook_to_errmsg(error_num);
        LOGE("hook failed: %d - %s", error_num, error_msg);
        return this;
    }

    CipherBase::s_InstanceVec.push_back((CipherBase *)this);
    // 16-byte conservative ARM64 prologue size; shadowhook doesn't expose
    // the exact rewrite count.
    CipherInternal::RecordInstall(this, callerPC, Types::e_hook,
                                  this->get_address(), 16, this->get_libName());
    return this;
}

void CipherHook::m_Restore() {
    if (this->stub == nullptr) {
        return;
    }

    std::lock_guard<std::recursive_mutex> lock(cipher_hook_mtx);

    shadowhook_unhook(this->stub);
    this->stub = nullptr;
    CipherBase::s_InstanceVec.erase(
        std::find(
            CipherBase::s_InstanceVec.begin(),
            CipherBase::s_InstanceVec.end(),
            (CipherBase *)this
        )
    );
    CipherInternal::ForgetInstall(this);

    // Recursive Restore mutates s_InstanceVec — return after the first match.
    for (auto& instance : CipherBase::s_InstanceVec) {
        CipherHook* pInstance = (CipherHook *)instance;
        if (pInstance->get_address() == this->p_Hook && pInstance->m_type == Types::e_hook) {
            pInstance->Restore();
            return;
        }
    }
}

void CipherHook::Restore() {
    this->m_Restore();
}

