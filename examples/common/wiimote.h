// Shared Wiimote input for the games: buttons with press detection, the IR
// pointer, the accelerometer, a shake measure, and rumble. Reads libogc's WPAD
// directly, because SDL folds every remote's pointer into one mouse.
//
// Apps must #include <SDL2/SDL_main.h> in their main.c: SDL's own main() is
// what calls WPAD_Init. Without it every remote reads as disconnected.
#ifndef WII_JOLT_WIIMOTE_H
#define WII_JOLT_WIIMOTE_H

#include <stdbool.h>
#include <stdint.h>

#define WIIMOTE_MAX 4

typedef struct {
    bool connected;
    uint32_t held;      // WPAD_BUTTON_* bits held now
    uint32_t pressed;   // bits that went down since the last read
    bool ir_valid;      // pointer is on screen
    float ir_x, ir_y;   // pointer in screen pixels
    float gx, gy, gz;   // accelerometer, in g (about 1.0 total at rest)
    float shake;        // |change in acceleration| since the last read, in g
} Remote;

// Ask every remote for buttons, accelerometer and IR, with IR in screen pixels.
void wiimote_init(int screen_w, int screen_h);

// Fill r[0..3] from the latest WPAD state. SDL does the actual reading
// (WPAD_ReadPending) while raylib polls events in EndDrawing, so call this
// once per frame. Returns true if any remote pressed HOME this frame.
bool wiimote_read(Remote r[WIIMOTE_MAX]);

// Rumble on or off. Only talks to the remote when the state changes.
void wiimote_rumble(int chan, bool on);
void wiimote_rumble_all_off(void);

#endif
