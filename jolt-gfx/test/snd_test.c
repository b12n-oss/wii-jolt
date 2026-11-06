// Host test for the jolt-snd core (voices, envelopes, mixer). Built by `bb test`.
#include <math.h>
#include <stdio.h>
#include <string.h>
#include "jolt_snd.h"

static int fails;
static int passes;
#define CHECK(c) do { if (!(c)) { fails++; printf("FAIL %s:%d: %s\n", __FILE__, __LINE__, #c); } } while (0)

#define MAX_FRAMES (SND_FRAMES * 64)
static int16_t out[MAX_FRAMES * 2];

// Render `frames` frames into out[] in SND_FRAMES-sized chunks.
static void render(int frames)
{
    for (int done = 0; done < frames;) {
        int n = frames - done < SND_FRAMES ? frames - done : SND_FRAMES;
        snd_render(out + 2 * done, n);
        done += n;
    }
}

static int peak_of(const int16_t *buf, int from, int to)
{
    int p = 0;
    for (int i = from; i < to; i++) {
        int a = buf[2 * i] < 0 ? -buf[2 * i] : buf[2 * i];
        if (a > p) p = a;
    }
    return p;
}

static int clipped_of(const int16_t *buf, int frames)
{
    int c = 0;
    for (int i = 0; i < 2 * frames; i++)
        if (buf[i] == 32767 || buf[i] == -32767) c++;
    return c;
}

static void begin(void)
{
    snd_init();
    snd_volume(255, 255);
}

static void note_table_is_tuned(void)
{
    begin();
    uint32_t a4 = (uint32_t)(440.0 * 4294967296.0 / 48000.0 + 0.5);
    int64_t d1 = (int64_t)snd_note_inc[69] - (int64_t)a4;
    int64_t d2 = (int64_t)snd_note_inc[81] - (int64_t)(2 * (uint64_t)a4);
    CHECK(d1 >= -1 && d1 <= 1);
    CHECK(d2 >= -2 && d2 <= 2);
    for (int n = 0; n < 128; n++)
        CHECK(snd_note_inc[n] == (uint32_t)(440.0 * pow(2.0, (n - 69) / 12.0) * 4294967296.0 / 48000.0 + 0.5));
}

static void pulse_voice_renders_a_square(void)
{
    begin();
    snd_instrument in = {SND_WAVE_PULSE, 2, 0, 0, 15, 15, 0};
    snd_voice_on(0, &in, snd_note_inc[69]);
    render(480);
    int p = peak_of(out, 0, 480);
    CHECK(p >= 1900 && p <= 2048);
    int same = 1, distinct = 0;
    int16_t seen[8];
    for (int i = 0; i < 480; i++) {
        if (out[2 * i] != out[2 * i + 1]) same = 0;
        int16_t s = out[2 * i];
        if (s == 0) continue;
        int found = 0;
        for (int k = 0; k < distinct; k++) if (seen[k] == s) found = 1;
        if (!found && distinct < 8) seen[distinct++] = s;
    }
    CHECK(same);
    CHECK(distinct == 2);
}

static double positive_fraction(int frames)
{
    int pos = 0;
    for (int i = 0; i < frames; i++) if (out[2 * i] > 0) pos++;
    return (double)pos / frames;
}

static void duty_changes_the_high_fraction(void)
{
    begin();
    snd_instrument in = {SND_WAVE_PULSE, 0, 0, 0, 15, 15, 0};
    snd_voice_on(0, &in, snd_note_inc[57]);
    render(4800);
    double f = positive_fraction(4800);
    CHECK(f >= 0.10 && f <= 0.15);
    begin();
    in.duty = 2;
    snd_voice_on(0, &in, snd_note_inc[57]);
    render(4800);
    f = positive_fraction(4800);
    CHECK(f >= 0.45 && f <= 0.55);
}

static void triangle_has_sixteen_levels(void)
{
    begin();
    snd_instrument in = {SND_WAVE_TRI, 0, 0, 0, 15, 15, 0};
    snd_voice_on(2, &in, snd_note_inc[45]);
    render(4800);
    int16_t seen[64];
    int n = 0;
    for (int i = 0; i < 4800; i++) {
        int found = 0;
        for (int k = 0; k < n; k++) if (seen[k] == out[2 * i]) found = 1;
        if (!found && n < 64) seen[n++] = out[2 * i];
    }
    CHECK(n == 16);
}

static void noise_is_not_periodic_short_term(void)
{
    begin();
    snd_instrument in = {SND_WAVE_NOISE, 0, 0, 0, 15, 15, 0};
    snd_voice_on(3, &in, snd_note_inc[100]);
    render(2400);
    int same = 1;
    for (int i = 0; i < 512; i++)
        if (out[2 * i] != out[2 * (i + 512)]) same = 0;
    CHECK(!same);
    char lens[2400];
    memset(lens, 0, sizeof lens);
    int distinct = 0, run = 1;
    for (int i = 1; i < 2400; i++) {
        if (out[2 * i] == out[2 * (i - 1)]) { run++; continue; }
        if (!lens[run]) { lens[run] = 1; distinct++; }
        run = 1;
    }
    CHECK(distinct >= 10);
}

static void envelope_attacks_decays_sustains_releases(void)
{
    begin();
    snd_instrument in = {SND_WAVE_PULSE, 2, 20, 30, 8, 15, 40};
    snd_voice_on(0, &in, snd_note_inc[69]);
    render(4800);
    int early = peak_of(out, 0, 480);
    int mid = peak_of(out, 960, 1440);
    int sus = peak_of(out, 2880, 4800);
    CHECK(early < mid);
    CHECK(sus >= (int)(0.45 * 2048) && sus <= (int)(0.6 * 2048));
    CHECK(snd_voice_active(0));
    snd_voice_off(0);
    render(2160);
    CHECK(!snd_voice_active(0));
    render(480);
    CHECK(peak_of(out, 0, 480) == 0);
}

static void mix_of_all_voices_does_not_wrap(void)
{
    begin();
    snd_instrument ins[3] = {
        {SND_WAVE_PULSE, 2, 0, 0, 15, 15, 0},
        {SND_WAVE_TRI, 0, 0, 0, 15, 15, 0},
        {SND_WAVE_NOISE, 0, 0, 0, 15, 15, 0},
    };
    snd_voice_on(0, &ins[0], snd_note_inc[60]);
    snd_voice_on(1, &ins[0], snd_note_inc[64]);
    snd_voice_on(2, &ins[1], snd_note_inc[48]);
    snd_voice_on(3, &ins[2], snd_note_inc[90]);
    snd_voice_on(4, &ins[0], snd_note_inc[72]);
    snd_voice_on(5, &ins[0], snd_note_inc[76]);
    snd_voice_on(6, &ins[2], snd_note_inc[95]);
    render(4800);
    CHECK(clipped_of(out, 4800) * 100 < 4800 * 2);
    int big = 0;
    for (int i = 1; i < 4800 * 2; i++) {
        int d = out[i] - out[i - 1];
        if (d < 0) d = -d;
        if (d > 30000) big = 1;
    }
    CHECK(!big);
}

static void volume_scales_the_sum(void)
{
    begin();
    snd_instrument in = {SND_WAVE_PULSE, 2, 0, 0, 15, 15, 0};
    snd_volume(128, 255);
    snd_voice_on(0, &in, snd_note_inc[69]);
    render(480);
    int p = peak_of(out, 0, 480);
    CHECK(p >= 1000 && p <= 1040);
    begin();
    snd_volume(255, 0);
    snd_voice_on(4, &in, snd_note_inc[69]);
    render(480);
    CHECK(peak_of(out, 0, 480) == 0);
}

int main(void)
{
    void (*tests[])(void) = {
        note_table_is_tuned, pulse_voice_renders_a_square, duty_changes_the_high_fraction,
        triangle_has_sixteen_levels, noise_is_not_periodic_short_term,
        envelope_attacks_decays_sustains_releases, mix_of_all_voices_does_not_wrap,
        volume_scales_the_sum,
    };
    int n = (int)(sizeof tests / sizeof tests[0]);
    for (int i = 0; i < n; i++) {
        int before = fails;
        tests[i]();
        if (fails == before) passes++;
    }
    printf("snd_test: %d passed, %d failed\n", passes, n - passes);
    return fails != 0;
}
