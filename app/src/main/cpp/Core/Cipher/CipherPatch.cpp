#include "Cipher.h"

#include <mutex>

#include "../../include/misc/visibility.h"

PRIVATE_API std::mutex cipher_patch_mtx;

CipherPatch *CipherPatch::set_Opcode(std::string _hex) {
    artpatch_set_hex(this->patch, _hex.c_str());
    return this;
}

CipherPatch* CipherPatch::Fire() {
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
            return this;
        }
    }
        //this->p_Backup = (uintptr_t)(new MemoryBackup(this->get_address(), 8)); //backs up original bytes
    artpatch_set_addr(this->patch, (void *)this->get_address());
    artpatch_apply(this->patch);
    CipherPatch::s_InstanceVec.push_back((CipherBase *)this);
    this->m_fired = true;
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
    artpatch_die(this->patch);
}
