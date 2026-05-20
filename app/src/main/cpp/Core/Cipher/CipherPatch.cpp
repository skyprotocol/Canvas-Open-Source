#include "Cipher.h"
#include "CipherRegistry.h"

#include <mutex>

#include "../include/misc/Logger.h"
#include "../../include/misc/visibility.h"

PRIVATE_API std::mutex cipher_patch_mtx;

CipherPatch *CipherPatch::set_Opcode(std::string _hex) {
    artpatch_set_hex(this->patch, _hex.c_str());
    CipherInternal::NotePatchBytes(this, _hex.length() / 2);
    return this;
}

CipherPatch* CipherPatch::Fire() {
    void* callerPC = __builtin_return_address(0);

    if (m_fired) {
        artpatch_apply(this->patch);
        return this;
    }

    if (this->get_address() == 0) {
        return this;
    }

    std::lock_guard<std::mutex> lock(cipher_patch_mtx);

    for (auto& instance : CipherPatch::s_InstanceVec) {
        CipherPatch* pInstance = (CipherPatch *)instance;
        if (pInstance->get_address() == get_address() && pInstance->get_Lock()) {
            const auto prior = Cipher::DescribePatchesAt(get_address());
            LOGW("Cipher: patch at %p rejected - locked by %s",
                 (void*)get_address(),
                 prior.empty() ? "<unknown>" : prior.front().ownerModule.c_str());
            return this;
        }
    }
        //this->p_Backup = (uintptr_t)(new MemoryBackup(this->get_address(), 8)); //backs up original bytes
    artpatch_set_addr(this->patch, (void *)this->get_address());
    artpatch_apply(this->patch);
    CipherPatch::s_InstanceVec.push_back((CipherBase *)this);
    this->m_fired = true;

    CipherInternal::RecordInstall(this, callerPC, Types::e_patch,
                                  this->get_address(),
                                  CipherInternal::LookupPatchBytes(this),
                                  this->get_libName());
    return this;
}

void CipherPatch::Restore() {
    artpatch_restore(this->patch);
}

CipherPatch::CipherPatch() {
    this->patch = artpatch_new();
    this->m_type = Types::e_patch;
}

CipherPatch::~CipherPatch() {
    artpatch_restore(this->patch);

    std::lock_guard<std::mutex> lock(cipher_patch_mtx);

    CipherBase::s_InstanceVec.erase(
        std::find(
            CipherBase::s_InstanceVec.begin(),
            CipherBase::s_InstanceVec.end(),
            (CipherBase *)this
        )
    );
    CipherInternal::ForgetInstall(this);
    artpatch_die(this->patch);
}
