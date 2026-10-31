//
// Created by maks on 05.09.2022.
//

#include <sys/mman.h>
#include <stdio.h>
#include <errno.h>
#include <malloc.h>
//#define PAGE_SIZE 4096
#include <stdlib.h>
#include <sys/mman.h>
#include <android/log.h>
#include "procutils.h"

struct buffer {
    int pos;
    int size;
    char* mem;
};

__attribute__((visibility ("hidden"))) char* procutils_buf_reset(struct buffer*b) {
    b->mem[b->pos] = 0;
    b->pos = 0;
    return b->mem;
}

__attribute__((visibility ("hidden"))) struct buffer* procutils_new_buffer(int length) {
    struct buffer* res = malloc(sizeof(struct buffer)+length+4);
    res->pos = 0;
    res->size = length;
    res->mem = (void*)(res+1);
    return res;
}

__attribute__((visibility ("hidden"))) int procutils_buf_putchar(struct buffer*b, int c) {
    b->mem[b->pos++] = c;
    return b->pos >= b->size;
}

__attribute__((visibility ("hidden"))) int procutils_read_mprotection(void* addr) {
    int a;
    unsigned int res = PROT_NONE;
    FILE *f = fopen("/proc/self/maps", "r");
    struct buffer* b = procutils_new_buffer(1024);
    while ((a = fgetc(f)) >= 0) {
        if (procutils_buf_putchar(b, a) || a == '\n') {
            char*end0 = (void*)0;
            unsigned long addr0 = strtoul(b->mem, &end0, 0x10);
            char*end1 = (void*)0;
            unsigned long addr1 = strtoul(end0+1, &end1, 0x10);
            if ((void*)addr0 <= addr && addr < (void*)addr1) {
                res |= (end1+1)[0] == 'r' ? PROT_READ : 0;
                res |= (end1+1)[1] == 'w' ? PROT_WRITE : 0;
                res |= (end1+1)[2] == 'x' ? PROT_EXEC : 0;
                break;
            }
            procutils_buf_reset(b);
        }
    }
    free(b);
    fclose(f);
    return res;
}