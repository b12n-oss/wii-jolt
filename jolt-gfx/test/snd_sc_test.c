// Host test: Star Catcher's four catch chimes triggered back to back must all
// finish within a second and be audible. Separate from snd_test.c because the
// two generated headers define the same SFX_ names. Built by `jolt test`.
#include <stdio.h>
#include "snd.h"
#include "../../examples/starcatcher/starcatcher_music.h"

#define FRAMES SND_RATE
static int16_t out[FRAMES * 2];

int main(void)
{
    int fails = 0;
    snd_init();
    snd_volume(255, 255);
    snd_tables(sc_effects, SC_EFFECTS, sc_instruments, (int)(sizeof sc_instruments / sizeof sc_instruments[0]), sc_songs, SC_SONGS);
    snd_sfx(SFX_CATCH_1);
    snd_sfx(SFX_CATCH_2);
    snd_sfx(SFX_CATCH_3);
    snd_sfx(SFX_CATCH_4);
    for (int done = 0; done < FRAMES;) {
        int n = FRAMES - done < SND_FRAMES ? FRAMES - done : SND_FRAMES;
        snd_render(out + 2 * done, n);
        done += n;
    }
    int peak = 0;
    for (int i = 0; i < FRAMES * 2; i++) {
        int a = out[i] < 0 ? -out[i] : out[i];
        if (a > peak) peak = a;
    }
    for (int c = 0; c < 3; c++)
        if (snd_sfx_active(c)) { printf("FAIL: effect channel %d still active after 1 s\n", c); fails++; }
    if (peak <= 1000) { printf("FAIL: peak %d is not above 1000\n", peak); fails++; }
    printf("snd_sc_test: peak %d, %s\n", peak, fails ? "FAILED" : "ok");
    return fails ? 1 : 0;
}
