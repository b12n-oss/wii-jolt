// Chez Scheme (tpb32b) booting on a Wii from boot files embedded in the .dol.
// Output goes to the TV through libogc's text console and to Dolphin's log
// (or a USB Gecko) through SYS_STDIO_Report.
#include <gccore.h>
#include <wiiuse/wpad.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdarg.h>
#include "scheme.h"

// Print to the TV console (stdout) and to the debug UART (stderr), which
// Dolphin logs as OSREPORT and a USB Gecko receives on real hardware.
static void say(const char *fmt, ...)
{
    char buf[1024];
    va_list ap; va_start(ap, fmt); vsnprintf(buf, sizeof buf, fmt, ap); va_end(ap);
    fputs(buf, stdout); fflush(stdout);
    fputs(buf, stderr); fflush(stderr);
}

extern char petite_boot[], petite_boot_end[];
extern char scheme_boot[], scheme_boot_end[];

static void *xfb;
static GXRModeObj *rmode;

static void console_setup(void)
{
    VIDEO_Init();
    WPAD_Init();
    rmode = VIDEO_GetPreferredMode(NULL);
    xfb = MEM_K0_TO_K1(SYS_AllocateFramebuffer(rmode));
    console_init(xfb, 20, 20, rmode->fbWidth, rmode->xfbHeight,
                 rmode->fbWidth * VI_DISPLAY_PIX_SZ);
    VIDEO_Configure(rmode);
    VIDEO_SetNextFramebuffer(xfb);
    VIDEO_SetBlack(false);
    VIDEO_Flush();
    VIDEO_WaitVSync();
    if (rmode->viTVMode & VI_NON_INTERLACE) VIDEO_WaitVSync();
    SYS_STDIO_Report(false);   // stderr -> UART; stdout stays on the TV
}

// Evaluate one expression given as source text, via Scheme's own reader.
static ptr eval_string(const char *src)
{
    ptr port = Scall1(Stop_level_value(Sstring_to_symbol("open-input-string")),
                      Sstring_utf8(src, -1));
    ptr form = Scall1(Stop_level_value(Sstring_to_symbol("read")), port);
    return Scall1(Stop_level_value(Sstring_to_symbol("eval")), form);
}

static void run(const char *label, const char *src)
{
    say("%s\n  %s\n  => ", label, src);
    ptr v = eval_string(src);
    // Format with Scheme's own writer, then print the string from C.
    ptr str = Scall2(Stop_level_value(Sstring_to_symbol("format")),
                     Sstring_utf8("~s", -1), v);
    ptr bv = Scall1(Stop_level_value(Sstring_to_symbol("string->utf8")), str);
    say("%.*s\n", (int)Sbytevector_length(bv), (char *)Sbytevector_data(bv));
}

int main(int argc, char **argv)
{
    console_setup();
    say("\n chez-wii: Chez Scheme on tpb32b\n");
    say(" petite.boot %d bytes, scheme.boot %d bytes\n",
           (int)(petite_boot_end - petite_boot), (int)(scheme_boot_end - scheme_boot));

    Sscheme_init(NULL);
    Sregister_boot_file_bytes("petite", petite_boot, petite_boot_end - petite_boot);
    Sregister_boot_file_bytes("scheme", scheme_boot, scheme_boot_end - scheme_boot);
    Sbuild_heap(NULL, NULL);
    say(" heap built\n\n");

    run("version", "(scheme-version)");
    run("machine", "(machine-type)");
    run("arith", "(list (+ 1 2) (* 1.5 4) (expt 2 100) (/ 1 3))");
    run("fixnum range", "(list (most-positive-fixnum) (fixnum? (expt 2 29)))");
    run("bytevector order", "(bytevector-u32-native-ref (bytevector 1 2 3 4) 0)");
    run("closure+recursion",
        "(let fib ([n 22]) (if (< n 2) n (+ (fib (- n 1)) (fib (- n 2)))))");
    run("gc", "(begin (collect) (bytes-allocated))");
    run("speed: fib 25, ms",
        "(let ([t0 (real-time)])"
        " (let fib ([n 25]) (if (< n 2) n (+ (fib (- n 1)) (fib (- n 2)))))"
        " (- (real-time) t0))");
    run("threads", "(let ([m (make-mutex)] [c (make-condition)] [r #f])"
                   " (fork-thread (lambda () (with-mutex m (set! r 'from-thread)"
                   " (condition-signal c))))"
                   " (with-mutex m (let loop () (unless r (condition-wait c m) (loop))))"
                   " r)");
    run("compile at runtime", "((eval '(lambda (x) (* x x))) 12)");

    say("\n done. Press HOME to exit.\n");
    while (1) {
        WPAD_ScanPads();
        if (WPAD_ButtonsDown(0) & WPAD_BUTTON_HOME) exit(0);
        VIDEO_WaitVSync();
    }
    return 0;
}
