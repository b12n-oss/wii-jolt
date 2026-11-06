// Host test for the jolt-snd core (voices, envelopes, mixer). Built by `bb test`.
#include <math.h>
#include <stdio.h>
#include <string.h>
#include "jolt_snd.h"
#include "../mona_music.h"

static int fails;
static int passes;
#define CHECK(c) do { if (!(c)) { fails++; printf("FAIL %s:%d: %s\n", __FILE__, __LINE__, #c); } } while (0)

#define MAX_FRAMES (SND_RATE * 4)
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

// --- effects ------------------------------------------------------------------

// fx_blip has a 5 ms attack so the steal test sees a measurable dip.
static const snd_effect fx_table[3] = {
    {SND_WAVE_PULSE, 2, 0, 5, 0, 1, {{880, 880, 50, 15}}},
    {SND_WAVE_PULSE, 2, 1, 0, 20, 1, {{300, 900, 80, 15}}},
    {SND_WAVE_NOISE, 0, 2, 0, 0, 2, {{2000, 2000, 30, 15}, {200, 200, 60, 10}}},
};

static void begin_fx(void)
{
    begin();
    snd_tables(fx_table, 3, NULL, 0, NULL, 0);
}

static int sfx_of(uint32_t stats) { return (int)((stats >> 15) & 255); }

static int crossings(int from, int to)
{
    int c = 0;
    for (int i = from + 1; i < to; i++)
        if ((out[2 * i] > 0) != (out[2 * (i - 1)] > 0)) c++;
    return c;
}

static void effect_renders_and_ends(void)
{
    begin_fx();
    snd_sfx(0);
    render(1920);
    CHECK(snd_sfx_active(0));
    CHECK(peak_of(out, 0, 1920) > 1500);
    render(3840);
    CHECK(!snd_sfx_active(0));
    render(480);
    CHECK(peak_of(out, 0, 480) == 0);
}

static void sweep_rises(void)
{
    begin_fx();
    snd_sfx(1);
    render(3840);
    CHECK(crossings(0, 960) < crossings(2880, 3840));
}

static void noise_effect_steps(void)
{
    begin_fx();
    snd_sfx(2);
    render(960);
    CHECK(snd_sfx_active(2));
    render(1920);
    CHECK(snd_sfx_active(2));
    render(4320);
    CHECK(!snd_sfx_active(2));
}

static void trigger_steals_the_oldest_channel(void)
{
    begin_fx();
    snd_sfx(0);
    render(480);
    snd_sfx(0);
    CHECK(snd_sfx_active(0));
    CHECK(snd_sfx_active(1));
    render(480);
    int steady = peak_of(out, 0, 480);
    CHECK(steady > 1500);
    snd_sfx(0);                  /* both busy: channel 0 is the oldest */
    render(96);
    CHECK(peak_of(out, 0, 96) < steady);
    CHECK(snd_sfx_active(0));
    CHECK(snd_sfx_active(1));
    begin_fx();
    snd_stats();
    int finite = 1;
    for (int i = 0; i < 16; i++) {
        snd_sfx(i % 2);
        render(800);
        for (int k = 0; k < 800 * 2; k++)
            if (out[k] > 32767 || out[k] < -32767) finite = 0;
    }
    CHECK(finite);
    CHECK(sfx_of(snd_stats()) == 16);
}

static void out_of_range_effect_is_ignored(void)
{
    begin_fx();
    snd_sfx(3);
    snd_sfx(-1);
    for (int c = 0; c < 3; c++) CHECK(!snd_sfx_active(c));
    CHECK(sfx_of(snd_stats()) == 0);
}

static void calls_before_tables_are_noops(void)
{
    begin();
    snd_sfx(0);
    for (int c = 0; c < 3; c++) CHECK(!snd_sfx_active(c));
    render(480);
    CHECK(peak_of(out, 0, 480) == 0);
    CHECK(snd_stats() == 0);
}


// --- sequencer ----------------------------------------------------------------

#define R_ (-1)
#define H_ (-2)
#define CELL(n) {(n), 0}
static const snd_cell t0a[8] = {CELL(60), CELL(H_), CELL(64), CELL(H_), CELL(67), CELL(R_), CELL(72), CELL(H_)};
static const snd_cell t1a[8] = {CELL(R_), CELL(R_), CELL(R_), CELL(R_), CELL(R_), CELL(R_), CELL(R_), CELL(R_)};
static const snd_cell t2a[8] = {CELL(36), CELL(H_), CELL(36), CELL(H_), CELL(36), CELL(H_), CELL(36), CELL(H_)};
static const snd_cell t3a[8] = {CELL(0), CELL(2), CELL(1), CELL(2), CELL(0), CELL(2), CELL(1), CELL(2)};
static const snd_pattern song_patterns[2] = {
    {8, {t0a, t1a, t2a, t3a}},
    {8, {t0a, t1a, t2a, t3a}},
};
static const uint8_t song_order[2] = {0, 1};
static const snd_cell hold_t0[8] = {{60, 1}, CELL(H_), CELL(H_), CELL(H_), CELL(H_), CELL(H_), CELL(H_), CELL(H_)};
static const snd_pattern hold_patterns[1] = {{8, {hold_t0, NULL, NULL, NULL}}};
static const uint8_t hold_order[1] = {0};
static const snd_song song_table[2] = {
    {480, 2, 2, 0, song_patterns, song_order},
    {480, 1, 1, 0, hold_patterns, hold_order},
};
static const snd_instrument song_inst[2] = {
    {SND_WAVE_PULSE, 2, 0, 0, 15, 15, 5},
    {SND_WAVE_PULSE, 2, 200, 0, 15, 15, 5},
};

static void begin_song(void)
{
    begin();
    snd_tables(NULL, 0, song_inst, 2, song_table, 2);
}

static void song_advances_rows(void)
{
    begin_song();
    snd_music(0, 1);
    render(48000);
    int r = snd_music_row();
    CHECK((r >= 7 && r <= 8) || (r >= 256 && r <= 257));
    render(48000);
    CHECK(snd_music_row() <= 1);
}

static void song_loops_at_its_order_point(void)
{
    begin_song();
    snd_music(0, 1);
    render(48000 * 3 + 24000);
    CHECK(snd_music_playing());
    CHECK(snd_music_row() >= 256 && snd_music_row() < 256 + 8);
}

static void play_once_ends(void)
{
    begin_song();
    snd_music(0, 0);
    render(48000 * 2 + 24000);
    CHECK(!snd_music_playing());
    render(480);
    CHECK(peak_of(out, 0, 480) == 0);
}

static void pause_holds_the_row(void)
{
    begin_song();
    snd_music(0, 1);
    render(28800);
    int row = snd_music_row();
    snd_music_pause(1);
    render(48000);
    CHECK(snd_music_row() == row);
    CHECK(peak_of(out, 48000 - 240, 48000) == 0);
    snd_music_pause(0);
    render(6000);
    CHECK(snd_music_row() == row + 1);
}

static void tempo_changes_from_the_next_row(void)
{
    begin_song();
    snd_music_tempo(125);
    snd_music(0, 1);
    render(48000);
    int r = snd_music_row();      /* 10 rows in: order 1, row 2 */
    CHECK(r >= 256 + 1 && r <= 256 + 3);
    begin_song();
    snd_music_tempo(125);
    snd_music_tempo(100);
    snd_music(0, 1);
    render(48000);
    r = snd_music_row();
    CHECK(r >= 255 && r <= 257);
}

static void stop_silences(void)
{
    begin_song();
    snd_music(0, 1);
    render(3000);
    CHECK(peak_of(out, 0, 3000) > 0);
    snd_music_stop();
    render(480);
    CHECK(peak_of(out, 240, 480) < 300);
    CHECK(!snd_music_playing());
}

static void hold_does_not_retrigger(void)
{
    begin_song();
    snd_music(1, 1);
    render(12000);
    int row0 = peak_of(out, 0, 6000);
    int row1 = peak_of(out, 6000, 12000);
    CHECK(row0 > 0);
    CHECK(row1 > row0);
}

static void music_before_tables_is_a_noop(void)
{
    snd_init();
    snd_tables(NULL, 0, NULL, 0, NULL, 0);
    snd_music(0, 1);
    CHECK(!snd_music_playing());
    render(480);
    CHECK(peak_of(out, 0, 480) == 0);
}

// --- the real tables (mona_music.h) ------------------------------------------

static void begin_real(void)
{
    begin();
    snd_tables(mona_effects, MONA_EFFECTS, mona_instruments,
               (int)(sizeof mona_instruments / sizeof mona_instruments[0]), mona_songs, MONA_SONGS);
}

// Render `frames` frames in out[]-sized pieces, keeping only the last piece.
static void render_long(int frames)
{
    while (frames > 0) {
        int n = frames < MAX_FRAMES ? frames : MAX_FRAMES;
        render(n);
        frames -= n;
    }
}

static void every_effect_sounds_and_ends(void)
{
    for (int id = 0; id < MONA_EFFECTS; id++) {
        begin_real();
        snd_sfx(id);
        render(SND_RATE / 10);
        int p = peak_of(out, 0, SND_RATE / 10);
        if (id == SFX_DIE) CHECK(p == 0);
        else CHECK(p > 500);
        render_long(SND_RATE * 3 / 2 - SND_RATE / 10);
        for (int c = 0; c < 3; c++) CHECK(!snd_sfx_active(c));
    }
}

// The song's length once through its order, in frames.
static int song_frames(int id)
{
    const snd_song *s = &mona_songs[id];
    int rows = 0;
    for (int i = 0; i < s->norder; i++) rows += s->patterns[s->order[i]].rows;
    return (int)((int64_t)rows * 60 * SND_RATE / s->rpm);
}

// Render `frames` frames in SND_FRAMES pieces into out[] (wrapping when it
// is full), counting each time the order index arrives at loop_at. *last is
// the order index before the first piece, -1 before the song starts.
static int arrivals_at(int frames, int loop_at, int *last)
{
    int n = 0, pos = 0;
    for (int done = 0; done < frames;) {
        int k = frames - done < SND_FRAMES ? frames - done : SND_FRAMES;
        if (pos + k > MAX_FRAMES) pos = 0;
        if (*last == -1 && snd_music_playing() && (snd_music_row() >> 8) == loop_at) n++;
        if (*last == -1) *last = snd_music_row() >> 8;
        snd_render(out + 2 * pos, k);
        pos += k;
        done += k;
        int o = snd_music_playing() ? snd_music_row() >> 8 : -2;
        if (o == loop_at && *last != loop_at) n++;
        *last = o;
    }
    return n;
}

static void every_song_renders(void)
{
    for (int id = 0; id < MONA_SONGS; id++) {
        const snd_song *s = &mona_songs[id];
        begin_real();
        snd_music(id, 1);
        int frames = SND_RATE * 4;
        int last = -1;
        int seen = arrivals_at(frames, s->loop_at, &last);
        CHECK(peak_of(out, 0, frames) > 1500);
        CHECK(clipped_of(out, frames) * 200 < 2 * frames);
        if (id == SONG_CLEAR || id == SONG_DIE || id == SONG_OVER) {
            CHECK(s->loop_at < 0);
            // Played again from the start and checked at its own bound.
            int bound = id == SONG_DIE ? SND_RATE * 26 / 10 : SND_RATE * 6;
            begin_real();
            snd_music(id, 1);
            render_long(bound);
            CHECK(!snd_music_playing());
            continue;
        }
        CHECK(s->loop_at >= 0);
        // The start is the first arrival at loop_at. Playing at 12 s, then on
        // past one pass of the order (16 to 22 s here) for the second arrival.
        seen += arrivals_at(SND_RATE * 8, s->loop_at, &last);
        CHECK(snd_music_playing());
        int more = song_frames(id) + SND_RATE * 2 - SND_RATE * 12;
        if (more > 0) seen += arrivals_at(more, s->loop_at, &last);
        CHECK(snd_music_playing());
        CHECK(seen >= 2);
    }
}

static void hurry_tempo_renders(void)
{
    begin_real();
    snd_music_tempo(125);
    snd_music(SONG_DAY, 1);
    int frames = SND_RATE * 4;
    render(frames);
    CHECK(peak_of(out, 0, frames) > 1500);
    CHECK(clipped_of(out, frames) * 200 < 2 * frames);
    snd_music_tempo(100);
}

static void music_peak_tracks_the_music_voices(void)
{
    begin_song();
    snd_music_peak();
    snd_music(0, 1);
    render(48000);
    CHECK(snd_music_peak() > 1500);
    CHECK(snd_music_peak() == 0);   // reset on read
    snd_music_stop();
    render(24000);                  // past every release
    snd_music_peak();
    render(4800);
    CHECK(snd_music_peak() == 0);
    begin_fx();                     // an effect alone is not music
    snd_music_peak();
    snd_sfx(0);
    render(1920);
    CHECK(peak_of(out, 0, 1920) > 1500);
    CHECK(snd_music_peak() == 0);
}

int main(void)
{
    void (*tests[])(void) = {
        note_table_is_tuned, pulse_voice_renders_a_square, duty_changes_the_high_fraction,
        triangle_has_sixteen_levels, noise_is_not_periodic_short_term,
        envelope_attacks_decays_sustains_releases, mix_of_all_voices_does_not_wrap,
        volume_scales_the_sum, effect_renders_and_ends, sweep_rises, noise_effect_steps,
        trigger_steals_the_oldest_channel, out_of_range_effect_is_ignored,
        calls_before_tables_are_noops, song_advances_rows, song_loops_at_its_order_point,
        play_once_ends, pause_holds_the_row, tempo_changes_from_the_next_row, stop_silences,
        hold_does_not_retrigger, music_before_tables_is_a_noop,
        every_effect_sounds_and_ends, every_song_renders, hurry_tempo_renders,
        music_peak_tracks_the_music_voices,
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
