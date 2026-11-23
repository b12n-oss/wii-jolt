// Host test: Shake Race's four finish stings triggered back to back, then its
// race song, must render and be audible. Only two channels are tonal, so this
// shows the synth neither stalls nor goes quiet when four arrive at once, not
// that four play together. Separate from snd_test.c because the
// generated headers define the same SFX_ names. Built by `jolt test`.
#include <stdio.h>
#include "snd.h"
#include "../../examples/shakerace/shakerace_music.h"

#define FRAMES SND_RATE
static int16_t out[FRAMES * 2];

static int render_peak(void)
{
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
    return peak;
}

int main(void)
{
    int fails = 0;
    snd_init();
    snd_volume(255, 255);
    snd_tables(sr_effects, SR_EFFECTS, sr_instruments, (int)(sizeof sr_instruments / sizeof sr_instruments[0]), sr_songs, SR_SONGS);
    snd_sfx(SFX_FINISH_1);
    snd_sfx(SFX_FINISH_2);
    snd_sfx(SFX_FINISH_3);
    snd_sfx(SFX_FINISH_4);
    int peak_sfx = render_peak();
    for (int c = 0; c < 3; c++)
        if (snd_sfx_active(c)) { printf("FAIL: effect channel %d still active after 1 s\n", c); fails++; }
    if (peak_sfx <= 1000) { printf("FAIL: finish peak %d is not above 1000\n", peak_sfx); fails++; }
    snd_music(SONG_RACE, 1);
    snd_music_tempo(160);
    int peak_song = render_peak();
    if (peak_song <= 1000) { printf("FAIL: race peak %d (tempo 160) is not above 1000\n", peak_song); fails++; }
    if (!snd_music_playing()) { printf("FAIL: the race song stopped\n"); fails++; }
    printf("snd_sr_test: finish peak %d, race peak %d, %s\n", peak_sfx, peak_song, fails ? "FAILED" : "ok");
    return fails ? 1 : 0;
}
