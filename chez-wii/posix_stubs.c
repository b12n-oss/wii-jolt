// POSIX calls the Chez kernel links against that newlib-on-libogc lacks.
// Each fails with ENOSYS (or the API's own failure value), so Scheme code
// that reaches them gets an ordinary error instead of a link failure.
// None are reached by booting, evaluating, threading or collecting.
#include <errno.h>
#include <signal.h>
#include <stddef.h>
#include <sys/types.h>
#include <sys/resource.h>
#include <pthread.h>
#include <stdio.h>
#include <sys/time.h>
#include <string.h>

#define STUB(name) fprintf(stderr, "[posix_stubs] %s\n", name)

struct passwd;

int getrusage(int who, struct rusage *u)
{
    // Chez reads cpu time at startup. There's no per-process CPU accounting
    // on libogc, so report wall-clock time since boot as user time.
    (void)who;
    struct timeval tv; gettimeofday(&tv, NULL);
    memset(u, 0, sizeof *u);
    u->ru_utime = tv;
    return 0;
}
int sigaction(int s, const struct sigaction *a, struct sigaction *o) { (void)s; (void)a; (void)o; return 0; }
int sigprocmask(int h, const sigset_t *s, sigset_t *o) { (void)h; (void)s; if (o) *o = 0; return 0; }
int pthread_kill(pthread_t t, int s) { STUB("pthread_kill"); (void)t; (void)s; return ENOSYS; }
int pipe(int fds[2]) { STUB("pipe"); (void)fds; errno = ENOSYS; return -1; }
int execl(const char *p, const char *a, ...) { STUB("execl"); (void)p; (void)a; errno = ENOSYS; return -1; }
pid_t waitpid(pid_t p, int *st, int o) { STUB("waitpid"); (void)p; (void)st; (void)o; errno = ECHILD; return -1; }
struct passwd *getpwnam(const char *n) { STUB("getpwnam"); (void)n; return NULL; }
struct passwd *getpwuid(uid_t u) { STUB("getpwuid"); (void)u; return NULL; }

typedef void *iconv_t;
iconv_t iconv_open(const char *to, const char *from) { STUB("iconv_open"); (void)to; (void)from; errno = EINVAL; return (iconv_t)-1; }
size_t iconv(iconv_t cd, char **in, size_t *inl, char **out, size_t *outl)
{ (void)cd; (void)in; (void)inl; (void)out; (void)outl; errno = EBADF; return (size_t)-1; }
int iconv_close(iconv_t cd) { (void)cd; return 0; }
uid_t getuid(void) { return 0; }
