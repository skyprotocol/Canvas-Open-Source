//
// Created by artDev on 04.09.2022.
//
#include <stdlib.h>
#include <stddef.h>
#include <string.h>
#include <android/log.h>
#include <stdbool.h>
#include <sys/mman.h>
#include <unistd.h>
// PAGE_SIZE was removed from NDK 29 public headers; derive it at runtime.
#ifndef PAGE_SIZE
#  define PAGE_SIZE ((size_t)sysconf(_SC_PAGESIZE))
#endif
#include "procutils.h"


#define REDEFINE_PATCHT;
typedef struct {
    unsigned char* patchDestination;
    unsigned char* patch;
    unsigned char* backup;
    unsigned int patchLength;
    bool patchApplied;
} real_patch_t;
typedef real_patch_t* patch_t;
#include "artpatch.h"

__attribute__((visibility ("hidden"))) unsigned char artpatch_decode_hex_digit(char hexa) {
    switch(hexa) {
        case '0': return 0;
        case '1': return 1;
        case '2': return 2;
        case '3': return 3;
        case '4': return 4;
        case '5': return 5;
        case '6': return 6;
        case '7': return 7;
        case '8': return 8;
        case '9': return 9;
        case 'A': return 10;
        case 'B': return 11;
        case 'C': return 12;
        case 'D': return 13;
        case 'E': return 14;
        case 'F': return 15;
        default: return 255;
    }
}

__attribute__((visibility ("hidden"))) void artpatch_set_addr(patch_t patch, void* addr) {
    patch->patchDestination = addr;
}

__attribute__((visibility ("hidden"))) void artpatch_strip_nonnumbers(char* buffer) {
    const char* match_table = "0123456789ABCDEFabcdef";
    char* d = buffer;
    do {
        while (!strchr(match_table, *d)) {
            ++d;
        }
    } while (*buffer++ = *d++);
}

__attribute__((visibility ("hidden"))) void artpatch_set_hex(patch_t patch, const char* hexa_orig) {
    size_t length = strlen(hexa_orig);
    char* hexa = malloc(length+1);
    memcpy(hexa, hexa_orig, length+1);
    artpatch_strip_nonnumbers(hexa);
    length = strlen(hexa);
    patch->patchLength = length / 2;
    if(!patch->patch) patch->patch = malloc(patch->patchLength);
    else patch->patch = realloc(patch->patch, patch->patchLength);
    if(!patch->backup) patch->backup = malloc(patch->patchLength);
    else patch->backup = realloc(patch->backup, patch->patchLength);
    for(size_t i = 0; i < patch->patchLength; i++) {
        unsigned char decode_hi = artpatch_decode_hex_digit(hexa[i*2]);
        unsigned char decode_lo = artpatch_decode_hex_digit(hexa[i*2+1]);
        if(decode_hi > 15 || decode_lo > 15) {
            __android_log_print(ANDROID_LOG_DEBUG, "artpatch", "invalid character during decode: %c %c", hexa[i*2], hexa[i*2+1]);
            free(hexa);
            return;
        }
        patch->patch[i] = (decode_hi << 4) | (decode_lo & 0xF);
    }
    free(hexa);
}

__attribute__((visibility ("hidden"))) void artpatch_apply(patch_t patch) {
    const void* pagestart = (void*)((uintptr_t)patch->patchDestination & -PAGE_SIZE);
    int prot_flags = procutils_read_mprotection(pagestart);
    if(!mprotect(pagestart, PAGE_SIZE, PROT_READ | PROT_WRITE | PROT_EXEC)) {
        if(!patch->patchApplied) memcpy(patch->backup, patch->patchDestination, patch->patchLength);
        memcpy(patch->patchDestination, patch->patch, patch->patchLength);
        mprotect(pagestart, PAGE_SIZE, prot_flags);
        patch->patchApplied = true;
    }
}
__attribute__((visibility ("hidden"))) void artpatch_restore(patch_t patch) {
    if(!patch->patchApplied) return;
    const void* pagestart = (void*)((uintptr_t)patch->patchDestination & -PAGE_SIZE);
    int prot_flags = procutils_read_mprotection(pagestart);
    if(!mprotect(pagestart, PAGE_SIZE, PROT_READ | PROT_WRITE | PROT_EXEC)) {
        memcpy(patch->patchDestination, patch->backup, patch->patchLength);
        mprotect(pagestart, PAGE_SIZE, prot_flags);
        patch->patchApplied = false;
    }else{
        __android_log_print(ANDROID_LOG_FATAL,"artPatch","Unable to change memory protection to restore backup!");
        abort();
    }
}
__attribute__((visibility ("hidden"))) patch_t artpatch_new() {
    patch_t patch = malloc(sizeof(real_patch_t));
    memset(patch, 0 ,sizeof(real_patch_t));
    return patch;
}
__attribute__((visibility ("hidden"))) void artpatch_die(patch_t patch) {
    if(patch->patchApplied) artpatch_restore(patch);
    if(patch->patch) free(patch->patch);
    if(patch->backup) free(patch->backup);
    free(patch);
}


