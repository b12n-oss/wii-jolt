// Wii runtime for programs whose main() someone else writes (a `jolt build`
// executable: Chez's own main boots the embedded image and runs -main).
//
// - A constructor brings up video and the libogc text console before main.
// - stdout and stderr are teed: every write goes to the TV console AND to the
//   debug UART (Dolphin logs it as OSREPORT; a USB Gecko gets it on hardware).
//   SYS_STDIO_Report on its own would replace the console, leaving the TV
//   black.
// - At exit it waits for HOME (Wiimote) or START (GameCube pad), so output
//   stays on screen instead of dropping straight back to the loader.
#include <gccore.h>
#include <wiiuse/wpad.h>
#include <sys/iosupport.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <malloc.h>

static void *xfb;
static const devoptab_t *console_dev, *uart_dev;

static ssize_t tee_write(struct _reent *r, void *fd, const char *ptr, size_t len)
{
    // The UART gets a private copy: libogc 3.2.1's WriteUARTN (libogc/exi.c:1038)
    // rewrites every '\n' to '\r' IN the caller's buffer, despite the const
    // char* devoptab signature. Handing it our caller's bytes first left the
    // console only '\r's, so every line overwrote the last on the TV.
    ssize_t n = console_dev->write_r(r, fd, ptr, len);
    if (uart_dev && uart_dev->write_r) {
        char copy[256];
        for (size_t off = 0; off < len; off += sizeof copy) {
            size_t k = len - off < sizeof copy ? len - off : sizeof copy;
            memcpy(copy, ptr + off, k);
            uart_dev->write_r(r, fd, copy, k);
        }
    }
    return n;
}

static devoptab_t tee_dev;

// How often the bytecode interpreter took its unaligned double path (pb.h);
// a non-zero count on the console is a double read at an odd address that
// the 750 would otherwise have thrown an alignment exception on.
extern unsigned pb_unaligned_fp_loads, pb_unaligned_fp_stores;

static void wait_for_home(void)
{
    fflush(stdout);
    printf("pb: unaligned double loads %u stores %u\n", pb_unaligned_fp_loads, pb_unaligned_fp_stores);
    printf("\n [program finished: press HOME or START to leave]\n");
    fflush(stdout);
    for (;;) {
        WPAD_ScanPads();
        PAD_ScanPads();
        if ((WPAD_ButtonsDown(0) & WPAD_BUTTON_HOME) || (PAD_ButtonsDown(0) & PAD_BUTTON_START))
            break;
        VIDEO_WaitVSync();
    }
}

__attribute__((constructor))
static void wii_runtime_init(void)
{
    VIDEO_Init();
    WPAD_Init();
    PAD_Init();
    GXRModeObj *rmode = VIDEO_GetPreferredMode(NULL);
    // Framebuffer from the malloc heap. (SYS_AllocateFramebuffer works too;
    // this was swapped while chasing a console bug that turned out to be the
    // UART writer above, and kept because it's equally valid.)
    xfb = MEM_K0_TO_K1(memalign(32, VIDEO_GetFrameBufferSize(rmode)));
    console_init(xfb, 20, 20, rmode->fbWidth, rmode->xfbHeight,
                 rmode->fbWidth * VI_DISPLAY_PIX_SZ);
    VIDEO_Configure(rmode);
    VIDEO_SetNextFramebuffer(xfb);
    VIDEO_SetBlack(false);
    VIDEO_Flush();
    VIDEO_WaitVSync();
    if (rmode->viTVMode & VI_NON_INTERLACE) VIDEO_WaitVSync();

    console_dev = devoptab_list[STD_OUT];
    SYS_STDIO_Report(true);
    uart_dev = devoptab_list[STD_OUT];
    memcpy(&tee_dev, console_dev, sizeof tee_dev);
    tee_dev.write_r = tee_write;
    devoptab_list[STD_OUT] = &tee_dev;
    devoptab_list[STD_ERR] = &tee_dev;
    setvbuf(stdout, NULL, _IONBF, 0);

    atexit(wait_for_home);
}
