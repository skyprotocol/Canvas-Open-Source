#include "starwatch_block.h"

#include <netdb.h>
#include <cstring>
#include <cstdlib>
#include <strings.h>
#include <arpa/inet.h>
#include <sys/socket.h>
#include <unistd.h>
#include <set>
#include <mutex>
#include <android/log.h>
#include "shadowhook.h"
#include "../../include/misc/visibility.h"

#define SW_TAG "StarwatchBlock"

static int (*orig_getaddrinfo)(const char *, const char *,
                               const struct addrinfo *,
                               struct addrinfo **) = nullptr;

static int (*orig_connect)(int, const struct sockaddr *, socklen_t) = nullptr;

static ssize_t (*orig_sendto)(int, const void *, size_t, int,
                               const struct sockaddr *, socklen_t) = nullptr;

static std::mutex g_mtx;
static std::set<int> g_blocked_fds;

PRIVATE_API static bool contains_starwatch(const char *host) {
    if (!host) return false;
    const char *p = host;
    while (*p) {
        if ((*p == 's' || *p == 'S') &&
            strncasecmp(p, "starwatch", 9) == 0) {
            return true;
        }
        ++p;
    }
    return false;
}

PRIVATE_API static bool is_loopback(const struct sockaddr *addr) {
    if (!addr || addr->sa_family != AF_INET) return false;
    auto *sin = (const struct sockaddr_in *)addr;
    return ntohl(sin->sin_addr.s_addr) == INADDR_LOOPBACK;
}

PRIVATE_API static int hooked_getaddrinfo(const char *node, const char *service,
                                           const struct addrinfo *hints,
                                           struct addrinfo **res) {
    if (contains_starwatch(node)) {
        __android_log_print(ANDROID_LOG_DEBUG, SW_TAG,
                            "Blocked DNS: %s", node);

        auto *info = (struct addrinfo *)calloc(1, sizeof(struct addrinfo));
        auto *addr = (struct sockaddr_in *)calloc(1, sizeof(struct sockaddr_in));

        addr->sin_family = AF_INET;
        addr->sin_addr.s_addr = htonl(INADDR_LOOPBACK);

        info->ai_family = AF_INET;
        info->ai_socktype = hints ? hints->ai_socktype : SOCK_STREAM;
        info->ai_protocol = hints ? hints->ai_protocol : 0;
        info->ai_addrlen = sizeof(struct sockaddr_in);
        info->ai_addr = (struct sockaddr *)addr;
        info->ai_next = nullptr;

        *res = info;
        return 0;
    }
    return orig_getaddrinfo(node, service, hints, res);
}

PRIVATE_API static int hooked_connect(int fd, const struct sockaddr *addr,
                                       socklen_t addrlen) {
    if (is_loopback(addr)) {
        auto *sin = (const struct sockaddr_in *)addr;
        int port = ntohs(sin->sin_port);
        if (port > 1024) {
            std::lock_guard<std::mutex> lock(g_mtx);
            g_blocked_fds.insert(fd);
            __android_log_print(ANDROID_LOG_DEBUG, SW_TAG,
                                "Sinkholed connect fd=%d port=%d", fd, port);
            return 0;
        }
    }
    return orig_connect(fd, addr, addrlen);
}

PRIVATE_API static ssize_t hooked_sendto(int fd, const void *buf, size_t len,
                                          int flags, const struct sockaddr *addr,
                                          socklen_t addrlen) {
    if (addr && is_loopback(addr)) {
        auto *sin = (const struct sockaddr_in *)addr;
        int port = ntohs(sin->sin_port);
        if (port > 1024) {
            return (ssize_t)len;
        }
    }

    {
        std::lock_guard<std::mutex> lock(g_mtx);
        if (g_blocked_fds.count(fd)) {
            return (ssize_t)len;
        }
    }

    return orig_sendto(fd, buf, len, flags, addr, addrlen);
}

void starwatch_block_install() {
    void *s1 = shadowhook_hook_sym_name(
        "libc.so", "getaddrinfo",
        (void *)hooked_getaddrinfo,
        (void **)&orig_getaddrinfo
    );

    void *s2 = shadowhook_hook_sym_name(
        "libc.so", "connect",
        (void *)hooked_connect,
        (void **)&orig_connect
    );

    void *s3 = shadowhook_hook_sym_name(
        "libc.so", "sendto",
        (void *)hooked_sendto,
        (void **)&orig_sendto
    );

    __android_log_print(ANDROID_LOG_INFO, SW_TAG,
                        "Hooks: dns=%s connect=%s sendto=%s",
                        s1 ? "OK" : "FAIL",
                        s2 ? "OK" : "FAIL",
                        s3 ? "OK" : "FAIL");
}
