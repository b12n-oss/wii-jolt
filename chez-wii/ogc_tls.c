// Thread-local storage for newlib's pthread_key_* on libogc.
//
// devkitPro's libsysbase implements pthread_key_create/getspecific/... on top
// of weak __syscall_tls_* hooks (newlib sys/iosupport.h). libogc 3.2.1 doesn't
// define them, so pthread_key_create returns ENOSYS, and Chez's threaded
// kernel aborts in Sscheme_init ("Function not implemented"). This supplies
// the hooks: a fixed table of per-thread value rows keyed by LWP_GetSelf().
//
// Spike-grade limits, documented in docs/chez-on-wii.md:
// - at most TLS_THREADS live threads and TLS_KEYS keys;
// - a row is never freed when its thread exits, and libogc reuses lwp_t
//   handles, so a new thread can see stale values until it sets its own.
//   Chez sets its thread-context key first thing in every thread it starts.
// - destructors are recorded but never run.
#include <errno.h>
#include <stdint.h>
#include <string.h>
#include <ogc/lwp.h>
#include <ogc/irq.h>

#define TLS_KEYS    32
#define TLS_THREADS 64

static uint8_t key_used[TLS_KEYS];
static struct row { lwp_t tid; int used; const void *val[TLS_KEYS]; } rows[TLS_THREADS];

static struct row *row_for_self(int create)
{
    lwp_t me = LWP_GetSelf();
    struct row *free_row = NULL;
    for (int i = 0; i < TLS_THREADS; i++) {
        if (rows[i].used && rows[i].tid == me) return &rows[i];
        if (!rows[i].used && !free_row) free_row = &rows[i];
    }
    if (!create || !free_row) return NULL;
    memset(free_row, 0, sizeof *free_row);
    free_row->tid = me;
    free_row->used = 1;
    return free_row;
}

int __syscall_tls_create(uint32_t *key, void (*destructor)(void *))
{
    (void)destructor;
    u32 level = IRQ_Disable();
    for (uint32_t k = 0; k < TLS_KEYS; k++) {
        if (!key_used[k]) {
            key_used[k] = 1;
            for (int i = 0; i < TLS_THREADS; i++) rows[i].val[k] = NULL;
            IRQ_Restore(level);
            *key = k;
            return 0;
        }
    }
    IRQ_Restore(level);
    return EAGAIN;
}

int __syscall_tls_set(uint32_t key, const void *value)
{
    if (key >= TLS_KEYS || !key_used[key]) return EINVAL;
    u32 level = IRQ_Disable();
    struct row *r = row_for_self(1);
    if (r) r->val[key] = value;
    IRQ_Restore(level);
    return r ? 0 : ENOMEM;
}

void *__syscall_tls_get(uint32_t key)
{
    if (key >= TLS_KEYS) return NULL;
    u32 level = IRQ_Disable();
    struct row *r = row_for_self(0);
    const void *v = r ? r->val[key] : NULL;
    IRQ_Restore(level);
    return (void *)v;
}

int __syscall_tls_delete(uint32_t key)
{
    if (key >= TLS_KEYS) return EINVAL;
    key_used[key] = 0;
    return 0;
}

// libogc also leaves thread_detach unimplemented; Chez detaches every
// thread it forks. Treat detach as a no-op (the LWP is reclaimed when it
// exits regardless).
struct __pthread_t;
int __syscall_thread_detach(struct __pthread_t *thread)
{
    (void)thread;
    return 0;
}
