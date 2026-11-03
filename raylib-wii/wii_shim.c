// Link-time guards for devkitPro wii-sdl2 2.28.5 + libogc 3.2.1.
//
// SDL's OGC video driver calls KEYBOARD_Init(NULL) and then, every frame,
// KEYBOARD_GetEvent(). When KEYBOARD_Init fails early (USB_Initialize or
// USBKeyboard_Initialize not IPC_OK; this is what Dolphin does), libwiikeyboard
// never runs __lwp_queue_init_empty(&_queue), so GetEvent dereferences a
// zeroed queue: a DSI on real hardware, a null read in Dolphin.
// Stack: KEYBOARD_GetEvent <- OGC_PumpKeyboardEvents <- SDL_PumpEventsInternal
//        <- PollInputEvents <- EndDrawing.
//
// Linked with -Wl,--wrap=KEYBOARD_Init -Wl,--wrap=KEYBOARD_GetEvent.
#include <gctypes.h>
#include <wiikeyboard/keyboard.h>

s32 __real_KEYBOARD_Init(keyPressCallback keypress_cb);
s32 __real_KEYBOARD_GetEvent(keyboard_event *event);

static int kbd_ready = 0;

s32 __wrap_KEYBOARD_Init(keyPressCallback keypress_cb)
{
    s32 r = __real_KEYBOARD_Init(keypress_cb);
    kbd_ready = (r >= 0);
    return r;
}

s32 __wrap_KEYBOARD_GetEvent(keyboard_event *event)
{
    if (!kbd_ready) return 0;
    return __real_KEYBOARD_GetEvent(event);
}
