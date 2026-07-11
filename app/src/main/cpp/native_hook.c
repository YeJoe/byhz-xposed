#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <dlfcn.h>
#include <sys/mman.h>
#include <android/log.h>

#define TAG     "BYHZ_Native"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

#define NEW_TOKEN   "eb8fbd7caca26d2b8147e59340b78d60"
#define TOKEN_VALUE "bearer " NEW_TOKEN
#define PAGE_SIZE   4096
#define HOOK_LEN    16   /* ARM64: LDR X17,#8 + BR X17 + .quad addr = 16 bytes */

/* ---- typedefs ---- */
typedef ssize_t (*write_t)(int fd, const void *buf, size_t count);
typedef int     (*SSL_write_t)(void *ssl, const void *buf, int num);

static write_t    real_write    = NULL;   /* trampoline */
static SSL_write_t real_SSL_write = NULL; /* trampoline */

static int g_hook_ok = 0;

/* ================================================================
 *  ARM64 inline hook
 *  Trampoline (16 bytes):  LDR X17, #8  |  BR X17  |  target_addr(8)
 * ================================================================ */
static void build_jmp(uint8_t *code, void *target)
{
    uint32_t *insn = (uint32_t *)code;
    insn[0] = 0x58000051;          /* LDR X17, #8   (literal load from PC+8) */
    insn[1] = 0xD61F0220;          /* BR  X17                      */
    *(uint64_t *)(code + 8) = (uint64_t)(uintptr_t)target;
}

static int arm64_hook(void *target, void *hook, void **trampoline_out)
{
    uintptr_t addr = (uintptr_t)target;
    uintptr_t page = addr & ~(PAGE_SIZE - 1);

    /* --- make target page writable --- */
    if (mprotect((void *)page, PAGE_SIZE, PROT_READ | PROT_WRITE | PROT_EXEC) != 0) {
        LOGE("mprotect(RWX) failed on %p", target);
        return -1;
    }

    /* --- allocate trampoline page --- */
    uint8_t *tramp = mmap(NULL, PAGE_SIZE,
                           PROT_READ | PROT_WRITE | PROT_EXEC,
                           MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (tramp == MAP_FAILED) {
        /* fallback: RW then mprotect to RX */
        tramp = mmap(NULL, PAGE_SIZE,
                      PROT_READ | PROT_WRITE,
                      MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
        if (tramp == MAP_FAILED) {
            LOGE("mmap trampoline failed");
            mprotect((void *)page, PAGE_SIZE, PROT_READ | PROT_EXEC);
            return -1;
        }
    }

    /* --- trampoline = original_HOOK_LEN bytes + jump to original+HOOK_LEN --- */
    memcpy(tramp, target, HOOK_LEN);
    build_jmp(tramp + HOOK_LEN, (void *)(addr + HOOK_LEN));

    /* make trampoline RX only */
    mprotect(tramp, PAGE_SIZE, PROT_READ | PROT_EXEC);

    /* --- overwrite target with jump to hook --- */
    build_jmp((uint8_t *)target, hook);

    /* restore target page to RX */
    mprotect((void *)page, PAGE_SIZE, PROT_READ | PROT_EXEC);

    /* flush caches */
    __builtin___clear_cache((char *)target, (char *)target + HOOK_LEN);
    __builtin___clear_cache((char *)tramp,  (char *)tramp  + HOOK_LEN * 2);

    *trampoline_out = tramp;
    return 0;
}

/* ================================================================
 *  URL pattern matching
 * ================================================================ */
static int url_matches(const char *url)
{
    if (strstr(url, "/api/video/report_item"))  return 1;
    if (strstr(url, "/api/live/room/detail"))   return 1;
    if (strstr(url, "/api/video/related"))      return 1;
    if (strstr(url, "/api/video/detail"))       return 1;
    if (strstr(url, "/api/socialposts_info"))    return 1;
    if (strstr(url, "/api/my/profile"))         return 1;
    return 0;
}

/* case-insensitive string compare */
static int str_starts_with_nocase(const char *s, const char *prefix)
{
    while (*prefix) {
        char a = *s++;
        char b = *prefix++;
        if (a >= 'A' && a <= 'Z') a += 32;
        if (b >= 'A' && b <= 'Z') b += 32;
        if (a != b) return 0;
    }
    return 1;
}

/* ================================================================
 *  HTTP header parser & token replacer
 *  Returns new length, or -1 if no change needed.
 *  data is mutable, max_len is available buffer size.
 * ================================================================ */
static int process_http_headers(char *data, int len, int max_len)
{
    /* find header terminator \r\n\r\n */
    int headers_len = -1;
    for (int i = 0; i < len - 3; i++) {
        if (data[i] == '\r' && data[i+1] == '\n' &&
            data[i+2] == '\r' && data[i+3] == '\n') {
            headers_len = i;
            break;
        }
    }
    if (headers_len < 0) return -1;

    /* parse request line: GET /path HTTP/1.1 */
    int line1_end = 0;
    while (line1_end < headers_len && data[line1_end] != '\r') line1_end++;
    if (line1_end < 4) return -1;

    char req_line[2048];
    int rl_len = line1_end < (int)sizeof(req_line)-1 ? line1_end : (int)sizeof(req_line)-1;
    memcpy(req_line, data, rl_len);
    req_line[rl_len] = '\0';

    /* split method and path */
    char *path = strchr(req_line, ' ');
    if (!path) return -1;
    path++;
    char *proto = strchr(path, ' ');
    int path_len = proto ? (int)(proto - path) : (int)strlen(path);

    /* find Host header */
    char host[256] = {0};
    int pos = line1_end + 2; /* skip \r\n */
    while (pos < headers_len) {
        int le = pos;
        while (le < headers_len && data[le] != '\r') le++;

        if (le - pos > 5 && str_starts_with_nocase(data + pos, "host:")) {
            int hs = pos + 5;
            while (hs < le && data[hs] == ' ') hs++;
            int hl = le - hs;
            if (hl > 255) hl = 255;
            memcpy(host, data + hs, hl);
            break;
        }
        pos = le + 2;
    }
    if (host[0] == '\0') return -1;

    /* build full URL */
    char full_url[2560];
    snprintf(full_url, sizeof(full_url), "https://%s/%.*s",
             host, path_len, path);

    LOGD("[SSL] %s", full_url);

    if (!url_matches(full_url)) return -1;

    /* locate Authorization header */
    int auth_start = -1, auth_end = -1;
    pos = line1_end + 2;
    while (pos < headers_len) {
        int le = pos;
        while (le < headers_len && data[le] != '\r') le++;

        if (le - pos > 14 && (memcmp(data + pos, "authorization:", 14) == 0 ||
                               memcmp(data + pos, "Authorization:", 14) == 0)) {
            auth_start = pos;
            auth_end = le;
            break;
        }
        pos = le + 2;
    }

    const char *new_auth = "authorization: " TOKEN_VALUE;
    int new_auth_len = (int)strlen(new_auth);
    int body_start    = headers_len + 4;
    int body_len      = len - body_start;

    if (auth_start >= 0) {
        /* replace existing Authorization */
        int old_line_len = auth_end - auth_start;
        int new_hdr_len  = headers_len - old_line_len + new_auth_len;
        int total_new    = new_hdr_len + 4 + body_len;

        if (total_new > max_len) return -1;

        char tmp[32768];
        int w = 0;

        /* before auth line */
        memcpy(tmp + w, data, auth_start);
        w += auth_start;
        /* new auth */
        memcpy(tmp + w, new_auth, new_auth_len);
        w += new_auth_len;
        tmp[w++] = '\r'; tmp[w++] = '\n';
        /* after auth line */
        int after = auth_end + 2;
        if (headers_len > after) {
            memcpy(tmp + w, data + after, headers_len - after);
            w += headers_len - after;
        }
        /* separator */
        tmp[w++] = '\r'; tmp[w++] = '\n';
        tmp[w++] = '\r'; tmp[w++] = '\n';
        /* body */
        if (body_len > 0) {
            memcpy(tmp + w, data + body_start, body_len);
            w += body_len;
        }
        memcpy(data, tmp, total_new);

        LOGD("[SSL-Token] Replaced: %s", new_auth);
        return total_new;
    } else {
        /* no Authorization header → try to insert one after Host */
        int host_pos = -1, host_end = -1;
        pos = line1_end + 2;
        while (pos < headers_len) {
            int le = pos;
            while (le < headers_len && data[le] != '\r') le++;

            if (le - pos > 5 && str_starts_with_nocase(data + pos, "host:")) {
                host_pos = pos;
                host_end = le;
                break;
            }
            pos = le + 2;
        }
        if (host_pos < 0) return -1;

        /* insert after host line */
        int insert_at = host_end + 2; /* after host's \r\n */
        int new_auth_line_len = new_auth_len + 2; /* + \r\n */
        int new_hdr_len  = headers_len + new_auth_line_len;
        int total_new    = new_hdr_len + 4 + body_len;

        if (total_new > max_len) return -1;

        char tmp[32768];
        memcpy(tmp, data, insert_at);
        int w = insert_at;
        memcpy(tmp + w, new_auth, new_auth_len);
        w += new_auth_len;
        tmp[w++] = '\r'; tmp[w++] = '\n';
        int remaining = headers_len - insert_at;
        memcpy(tmp + w, data + insert_at, remaining);
        w += remaining;
        tmp[w++] = '\r'; tmp[w++] = '\n';
        tmp[w++] = '\r'; tmp[w++] = '\n';
        if (body_len > 0) {
            memcpy(tmp + w, data + body_start, body_len);
            w += body_len;
        }
        memcpy(data, tmp, total_new);

        LOGD("[SSL-Token] Inserted: %s", new_auth);
        return total_new;
    }
}

/* ================================================================
 *  Hooked SSL_write  (gets plaintext before encryption)
 * ================================================================ */
static int my_SSL_write(void *ssl, const void *buf, int num)
{
    if (num <= 0 || buf == NULL || real_SSL_write == NULL)
        return -1;

    const char *data = (const char *)buf;

    /* only process HTTP-like requests */
    if (num < 16) goto passthru;
    if (memcmp(data, "GET ",  4) != 0 &&
        memcmp(data, "POST ", 5) != 0 &&
        memcmp(data, "PUT ",  4) != 0 &&
        memcmp(data, "HEAD ", 5) != 0)
        goto passthru;

    /* make a mutable copy with extra room for token */
    char *modified = (char *)malloc(num + 512);
    if (!modified) goto passthru;
    memcpy(modified, data, num);

    int new_len = process_http_headers(modified, num, num + 512);
    if (new_len > 0) {
        int ret = real_SSL_write(ssl, modified, new_len);
        free(modified);
        return ret;
    }
    free(modified);

passthru:
    return real_SSL_write(ssl, buf, num);
}

/* ================================================================
 *  Hooked write()  (diagnostic + plain-HTTP fallback)
 * ================================================================ */
static ssize_t my_write(int fd, const void *buf, size_t count)
{
    /* Only log first few calls to confirm it's reached */
    static int call_count = 0;
    if (call_count < 5 && count > 4) {
        const char *data = (const char *)buf;
        if (memcmp(data, "GET ", 4) == 0 || memcmp(data, "POST ", 5) == 0) {
            LOGD("[write] HTTP detected, fd=%d len=%zu", fd, count);
            /* TODO: could do same token replacement here for plain HTTP */
        }
        call_count++;
    }
    return real_write(fd, buf, count);
}

/* ================================================================
 *  Try to find & hook SSL_write
 * ================================================================ */
static int hook_ssl_write(void)
{
    /* try RTLD_DEFAULT first (works if any loaded lib exports it) */
    void *sym = dlsym(RTLD_DEFAULT, "SSL_write");
    if (!sym) {
        /* try libflutter.so explicitly */
        void *h = dlopen("libflutter.so", RTLD_NOLOAD);
        if (h) sym = dlsym(h, "SSL_write");
    }
    if (!sym) {
        LOGE("SSL_write not found");
        return -1;
    }
    LOGD("[OK] SSL_write found at %p", sym);

    if (arm64_hook(sym, (void *)my_SSL_write, (void **)&real_SSL_write) != 0) {
        LOGE("[FAIL] hook SSL_write");
        return -1;
    }
    LOGD("[OK] SSL_write hooked");
    return 0;
}

/* ================================================================
 *  Hook libc write() as diagnostic + HTTP fallback
 * ================================================================ */
static int hook_write(void)
{
    void *sym = dlsym(RTLD_DEFAULT, "write");
    if (!sym) {
        LOGE("write not found");
        return -1;
    }
    LOGD("[OK] write found at %p", sym);

    if (arm64_hook(sym, (void *)my_write, (void **)&real_write) != 0) {
        LOGE("[FAIL] hook write");
        return -1;
    }
    LOGD("[OK] write hooked");
    return 0;
}

/* ================================================================
 *  Init: called when libbyhz_hook.so is loaded
 * ================================================================ */
__attribute__((constructor))
static void init_native_hook(void)
{
    LOGD("=== Native hook init ===");

    if (hook_ssl_write() == 0) {
        g_hook_ok = 1;
    }

    hook_write(); /* diagnostic, may also catch plain-HTTP */
}
