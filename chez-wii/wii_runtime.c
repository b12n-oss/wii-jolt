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

static void *xfb;
static const devoptab_t *console_dev, *uart_dev;

static ssize_t tee_write(struct _reent *r, void *fd, const char *ptr, size_t len)
{
    if (uart_dev && uart_dev->write_r) uart_dev->write_r(r, fd, ptr, len);
    return console_dev->write_r(r, fd, ptr, len);
}

static devoptab_t tee_dev;

static void wait_for_home(void)
{
    fflush(stdout);
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
    xfb = MEM_K0_TO_K1(SYS_AllocateFramebuffer(rmode));
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
