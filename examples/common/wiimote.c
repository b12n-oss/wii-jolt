// See wiimote.h.
#include "wiimote.h"
#include <gccore.h>
#include <wiiuse/wpad.h>
#include <math.h>

static uint32_t prev_held[WIIMOTE_MAX];
static bool was_connected[WIIMOTE_MAX];
static float prev_g[WIIMOTE_MAX][3];
static bool rumbling[WIIMOTE_MAX];

void wiimote_init(int screen_w, int screen_h)
{
    // SDL's main has already called WPAD_Init. SDL sets the same format;
    // repeating it keeps this file correct on its own.
    WPAD_SetDataFormat(WPAD_CHAN_ALL, WPAD_FMT_BTNS_ACC_IR);
    WPAD_SetVRes(WPAD_CHAN_ALL, screen_w, screen_h);
}

bool wiimote_read(Remote r[WIIMOTE_MAX])
{
    bool home = false;
    for (int c = 0; c < WIIMOTE_MAX; c++) {
        Remote *rm = &r[c];
        u32 type;
        rm->connected = WPAD_Probe(c, &type) == WPAD_ERR_NONE;
        if (!rm->connected) {
            rm->held = rm->pressed = 0;
            rm->ir_valid = false;
            rm->shake = 0;
            prev_held[c] = 0;
            was_connected[c] = false;
            continue;
        }
        WPADData *d = WPAD_Data(c);
        // A press is "held now, not held last frame": btns_d can miss a press
        // that lands between two reads, this can't.
        rm->held = d->btns_h;
        rm->pressed = d->btns_h & ~prev_held[c];
        prev_held[c] = d->btns_h;
        rm->ir_valid = d->ir.valid;
        rm->ir_x = d->ir.x;
        rm->ir_y = d->ir.y;
        rm->gx = d->gforce.x;
        rm->gy = d->gforce.y;
        rm->gz = d->gforce.z;
        if (was_connected[c]) {
            float dx = rm->gx - prev_g[c][0], dy = rm->gy - prev_g[c][1], dz = rm->gz - prev_g[c][2];
            rm->shake = sqrtf(dx * dx + dy * dy + dz * dz);
        } else {
            rm->shake = 0;   // first frame: no previous reading to compare with
        }
        prev_g[c][0] = rm->gx; prev_g[c][1] = rm->gy; prev_g[c][2] = rm->gz;
        was_connected[c] = true;
        if (rm->pressed & WPAD_BUTTON_HOME) home = true;
    }
    return home;
}

void wiimote_rumble(int chan, bool on)
{
    if (chan < 0 || chan >= WIIMOTE_MAX || rumbling[chan] == on) return;
    WPAD_Rumble(chan, on);
    rumbling[chan] = on;
}

void wiimote_rumble_all_off(void)
{
    for (int c = 0; c < WIIMOTE_MAX; c++) { WPAD_Rumble(c, 0); rumbling[c] = false; }
}
