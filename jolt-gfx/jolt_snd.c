// jolt-snd: the synthesiser core and its AESND glue. See jolt_snd.h.
#include "jolt_snd.h"

// --- core -------------------------------------------------------------------
#include <math.h>
#include <stddef.h>

uint32_t snd_note_inc[128];

enum { ENV_OFF, ENV_ATTACK, ENV_DECAY, ENV_SUSTAIN, ENV_RELEASE };

#define ENV_ONE 65536          // envelope is Q16, 0..15 << 16
#define ENV_PEAK (15 * ENV_ONE)
#define OUT_FULL 2048          // one voice at full volume swings +-2048

struct voice {
    uint32_t phase, inc, target_inc;
    int32_t slide_step;
    int slide_left;
    uint16_t lfsr;
    uint8_t wave, duty, vol;
    int env_stage;
    int32_t env, s_level;
    int32_t a_step, d_step, r_step;
    int32_t r_ms;
};

static struct voice voices[SND_VOICES];
static int music_vol = 255;
static int sfx_vol = 255;
static int32_t test_left;      // samples left of the test tone, 0 when none

// Effect channels 0..2 drive voices 4..6.
static struct { const snd_effect *fx; int step; int32_t left; uint32_t age; } chans[3];
static uint32_t age_counter;
static const snd_effect *fx_tab;
static int fx_n;

// Sequencer state.
static const snd_instrument *inst_tab;
static int inst_n;
static const snd_song *song_tab;
static int song_n;
static const snd_song *cur_song;     // NULL when no song is set
static int mus_order, mus_row, mus_loop, mus_pct = 100, mus_paused;
static int32_t mus_left;             // samples left in the row, 0 = trigger the row next frame

static volatile int32_t peak;
static volatile uint32_t sfx_count;
static volatile uint32_t cb_count;

void snd_init(void)
{
    for (int n = 0; n < 128; n++)
        snd_note_inc[n] = (uint32_t)(440.0 * pow(2.0, (n - 69) / 12.0) * 4294967296.0 / SND_RATE + 0.5);
    for (int v = 0; v < SND_VOICES; v++) {
        struct voice zero = {0};
        zero.lfsr = 1;
        voices[v] = zero;
    }
    music_vol = 255;
    sfx_vol = 255;
    test_left = 0;
    for (int c = 0; c < 3; c++) {
        chans[c].fx = NULL;
        chans[c].step = 0;
        chans[c].left = 0;
        chans[c].age = 0;
    }
    age_counter = 0;
    fx_tab = NULL;
    fx_n = 0;
    inst_tab = NULL;
    inst_n = 0;
    song_tab = NULL;
    song_n = 0;
    cur_song = NULL;
    mus_order = mus_row = mus_loop = mus_paused = 0;
    mus_pct = 100;
    mus_left = 0;
    peak = 0;
    sfx_count = 0;
    cb_count = 0;
}

void snd_volume(int music, int sfx)
{
    music_vol = music < 0 ? 0 : music > 255 ? 255 : music;
    sfx_vol = sfx < 0 ? 0 : sfx > 255 ? 255 : sfx;
}

// Q16 step to cover `diff` in ms: diff / samples, at least 1 sample, and never
// 0 for a non-zero diff.
static int32_t env_step(int32_t diff, int ms)
{
    int32_t n = (int32_t)((int64_t)ms * SND_RATE / 1000);
    if (n < 1) n = 1;
    int32_t step = diff / n;
    if (step == 0) step = diff > 0 ? 1 : diff < 0 ? -1 : 0;
    return step;
}

void snd_voice_on(int v, const snd_instrument *inst, uint32_t inc)
{
    if (v < 0 || v >= SND_VOICES) return;
    struct voice *p = &voices[v];
    p->wave = inst->wave;
    p->duty = inst->duty;
    p->vol = inst->vol > 15 ? 15 : inst->vol;
    p->inc = inc;
    p->target_inc = inc;
    p->slide_left = 0;
    p->s_level = (int32_t)(inst->s_vol > 15 ? 15 : inst->s_vol) * ENV_ONE;
    p->r_ms = inst->r_ms;
    p->a_step = env_step(ENV_PEAK, inst->a_ms);
    p->d_step = env_step(p->s_level - ENV_PEAK, inst->d_ms);
    if (inst->a_ms == 0) {
        p->env = ENV_PEAK;
        p->env_stage = ENV_DECAY;
    } else {
        p->env_stage = ENV_ATTACK;
    }
    if (p->env_stage == ENV_DECAY && inst->d_ms == 0) {
        p->env = p->s_level;
        p->env_stage = ENV_SUSTAIN;
    }
}

void snd_voice_off(int v)
{
    if (v < 0 || v >= SND_VOICES) return;
    struct voice *p = &voices[v];
    if (p->env_stage == ENV_OFF || p->env_stage == ENV_RELEASE) return;
    if (p->r_ms == 0 || p->env == 0) {
        p->env = 0;
        p->env_stage = ENV_OFF;
        return;
    }
    p->env_stage = ENV_RELEASE;
    p->r_step = env_step(-p->env, p->r_ms);
}

void snd_voice_slide(int v, uint32_t target_inc, int ms)
{
    if (v < 0 || v >= SND_VOICES) return;
    struct voice *p = &voices[v];
    int32_t n = (int32_t)((int64_t)ms * SND_RATE / 1000);
    if (n < 1) {
        p->inc = target_inc;
        p->slide_left = 0;
        return;
    }
    p->target_inc = target_inc;
    p->slide_step = (int32_t)(((int64_t)target_inc - (int64_t)p->inc) / n);
    p->slide_left = n;
}

int snd_voice_active(int v)
{
    return v >= 0 && v < SND_VOICES && voices[v].env_stage != ENV_OFF;
}

// The one float use besides snd_init, outside the callback.
void snd_test_tone(int hz, int ms)
{
    static const snd_instrument tone = {SND_WAVE_PULSE, 2, 0, 0, 15, 15, 0};
    if (hz < 20) hz = 20;
    if (hz > 20000) hz = 20000;
    snd_voice_on(4, &tone, (uint32_t)(hz * 4294967296.0 / SND_RATE));
    test_left = (int32_t)((int64_t)ms * SND_RATE / 1000);
    if (test_left < 1) test_left = 1;
}

// Advance one voice by a sample and return its output, +-OUT_FULL at most.
static int32_t voice_tick(struct voice *p)
{
    if (p->env_stage == ENV_OFF) return 0;

    int32_t w;    // wave, -15..15
    switch (p->wave) {
    case SND_WAVE_TRI: {
        int32_t q = (int32_t)(p->phase >> 27);                  // 0..31
        int32_t level = q < 16 ? q : 31 - q;                    // 0..15 up, 15..0 down
        w = 2 * level - 15;
        break;
    }
    case SND_WAVE_NOISE:
        w = (p->lfsr & 1) ? 15 : -15;
        break;
    default: {
        uint32_t high = p->duty == 0 ? (p->phase >> 29) == 0
                      : p->duty == 1 ? (p->phase >> 30) == 0
                      : (p->phase >> 31) == 0;
        w = high ? 15 : -15;
        break;
    }
    }
    // w * env * vol / (15 * 15 * 15) scaled to OUT_FULL; env in Q8 keeps this in int32.
    int32_t env8 = p->env >> 8;                                  // 0..3840
    int32_t s = w * env8 * p->vol;                               // |s| <= 864000
    s = s * OUT_FULL / (15 * 3840 * 15);

    uint32_t old = p->phase;
    p->phase += p->inc;
    if (p->wave == SND_WAVE_NOISE && p->phase < old) {
        uint16_t tap = p->duty ? (p->lfsr >> 6) : (p->lfsr >> 1);
        uint16_t fb = (p->lfsr ^ tap) & 1;
        p->lfsr = (uint16_t)((p->lfsr >> 1) | (fb << 14));
    }

    if (p->slide_left > 0) {
        p->slide_left--;
        p->inc = p->slide_left == 0 ? p->target_inc : (uint32_t)((int32_t)p->inc + p->slide_step);
    }

    switch (p->env_stage) {
    case ENV_ATTACK:
        p->env += p->a_step;
        if (p->env >= ENV_PEAK) {
            p->env = ENV_PEAK;
            p->env_stage = p->d_step == 0 ? ENV_SUSTAIN : ENV_DECAY;
        }
        break;
    case ENV_DECAY:
        p->env += p->d_step;
        if (p->env <= p->s_level) {
            p->env = p->s_level;
            p->env_stage = ENV_SUSTAIN;
        }
        break;
    case ENV_RELEASE:
        p->env += p->r_step;
        if (p->env <= 0) {
            p->env = 0;
            p->env_stage = ENV_OFF;
        }
        break;
    default:
        break;
    }
    return s;
}

// --- effects ----------------------------------------------------------------
// An effect is a voice instrument built at trigger time (attack_ms, no decay,
// sustain at full envelope, release_ms) whose volume field carries the current
// step's vol, so a step change is a plain vol write and vol 0 is a silent gap.

static uint32_t hz_to_inc(int hz)
{
    if (hz < 20) hz = 20;
    if (hz > 20000) hz = 20000;
    return (uint32_t)(((uint64_t)hz << 32) / SND_RATE);
}

void snd_tables(const snd_effect *fx, int nfx, const snd_instrument *inst, int ninst, const snd_song *songs, int nsongs)
{
    fx_tab = fx;
    fx_n = fx ? nfx : 0;
    inst_tab = inst;
    inst_n = inst ? ninst : 0;
    song_tab = songs;
    song_n = songs ? nsongs : 0;
    cur_song = NULL;
}

// Start step k of channel c; the voice is already on.
static void chan_step(int c, int k)
{
    const snd_step *st = &chans[c].fx->steps[k];
    struct voice *p = &voices[4 + c];
    p->vol = st->vol > 15 ? 15 : st->vol;
    p->inc = hz_to_inc(st->f0);
    p->target_inc = p->inc;
    p->slide_left = 0;
    if (st->f1 != st->f0) snd_voice_slide(4 + c, hz_to_inc(st->f1), st->ms);
    chans[c].step = k;
    chans[c].left = (int32_t)((int64_t)st->ms * SND_RATE / 1000);
}

static int nsteps_of(const snd_effect *fx)
{
    return fx->nsteps > SND_STEPS ? SND_STEPS : fx->nsteps;
}

void snd_sfx(int id)
{
    if (!fx_tab || id < 0 || id >= fx_n) return;
    const snd_effect *fx = &fx_tab[id];
    if (nsteps_of(fx) < 1) return;
    int c;
    if (fx->wave == SND_WAVE_NOISE) {
        c = 2;
    } else {
        int pref = fx->chan == 1 ? 1 : 0;
        if (!snd_voice_active(4 + pref)) c = pref;
        else if (!snd_voice_active(4 + (1 - pref))) c = 1 - pref;
        else c = chans[0].age <= chans[1].age ? 0 : 1;
    }
    snd_instrument inst = {fx->wave, fx->duty, fx->attack_ms, 0, 15, 15, fx->release_ms};
    voices[4 + c].phase = 0;      // a trigger restarts the voice, a stolen one included
    voices[4 + c].env = 0;
    snd_voice_on(4 + c, &inst, hz_to_inc(fx->steps[0].f0));
    chans[c].fx = fx;
    chans[c].age = ++age_counter;
    chan_step(c, 0);
    if (sfx_count < 255) sfx_count++;
}

int snd_sfx_active(int chan)
{
    return chan >= 0 && chan < 3 && snd_voice_active(4 + chan);
}

// Once per frame, before the voices render it: count the step down and advance.
static void chans_tick(void)
{
    for (int c = 0; c < 3; c++) {
        if (!chans[c].fx) continue;
        if (--chans[c].left > 0) continue;
        int k = chans[c].step + 1;
        while (k < nsteps_of(chans[c].fx) && chans[c].fx->steps[k].ms == 0) k++;
        if (k < nsteps_of(chans[c].fx)) {
            chan_step(c, k);
        } else {
            snd_voice_off(4 + c);
            chans[c].fx = NULL;
        }
    }
}

// --- sequencer --------------------------------------------------------------
// Music voices 0..3. The row counter advances when a row's samples run out and
// the next row's notes trigger on the following frame.

static const snd_instrument drums[3] = {
    {SND_WAVE_NOISE, 0, 0, 80, 0, 15, 0},     // low noise
    {SND_WAVE_NOISE, 0, 0, 120, 0, 15, 0},    // snare
    {SND_WAVE_NOISE, 1, 0, 30, 0, 15, 0},     // hat
};
static const uint8_t drum_note[3] = {60, 84, 100};

static void music_release(void)
{
    for (int v = 0; v < 4; v++) snd_voice_off(v);
}

static void music_end(void)
{
    music_release();
    cur_song = NULL;
}

void snd_music(int song, int loop)
{
    if (!song_tab || song < 0 || song >= song_n) return;
    music_release();
    cur_song = &song_tab[song];
    mus_order = mus_row = 0;
    mus_loop = loop != 0;
    mus_paused = 0;
    mus_left = 0;
}

void snd_music_stop(void)
{
    music_end();
    mus_paused = 0;
}

void snd_music_pause(int on)
{
    if (!cur_song) return;
    if (on && !mus_paused) music_release();
    mus_paused = on != 0;
}

void snd_music_tempo(int pct)
{
    mus_pct = pct < 50 ? 50 : pct > 200 ? 200 : pct;
}

int snd_music_playing(void)
{
    return cur_song != NULL;
}

int snd_music_row(void)
{
    return cur_song ? mus_order * 256 + mus_row : 0;
}

static void music_play_row(void)
{
    const snd_song *s = cur_song;
    if (mus_order >= s->norder || s->order[mus_order] >= s->npatterns) return;
    const snd_pattern *pat = &s->patterns[s->order[mus_order]];
    if (mus_row >= pat->rows) return;
    for (int t = 0; t < 4; t++) {
        if (!pat->track[t]) continue;
        snd_cell c = pat->track[t][mus_row];
        if (c.note == -2) continue;
        if (c.note < 0) { snd_voice_off(t); continue; }
        if (t == 3) {
            if (c.note > 2) continue;
            snd_voice_on(3, &drums[(int)c.note], snd_note_inc[drum_note[(int)c.note]]);
        } else {
            if (!inst_tab || c.inst >= inst_n || c.note > 127) continue;
            snd_voice_on(t, &inst_tab[c.inst], snd_note_inc[(int)c.note]);
        }
    }
}

static void music_advance(void)
{
    const snd_song *s = cur_song;
    const snd_pattern *pat = (mus_order < s->norder && s->order[mus_order] < s->npatterns)
                           ? &s->patterns[s->order[mus_order]] : NULL;
    if (pat && ++mus_row < pat->rows) return;
    mus_row = 0;
    if (++mus_order < s->norder) return;
    if (mus_loop && s->loop_at >= 0 && s->loop_at < s->norder) mus_order = s->loop_at;
    else music_end();
}

static void music_tick(void)
{
    if (!cur_song || mus_paused) return;
    if (mus_left <= 0) {
        music_play_row();
        int64_t rpm = cur_song->rpm ? cur_song->rpm : 1;
        int64_t n = (int64_t)SND_RATE * 60 * 100 / (rpm * mus_pct);
        mus_left = n < 1 ? 1 : (int32_t)n;
    }
    if (--mus_left <= 0) music_advance();
}

void snd_render(int16_t *out, int frames)
{
    int32_t pk = 0;
    for (int i = 0; i < frames; i++) {
        int32_t music = 0, sfx = 0;
        music_tick();
        chans_tick();
        for (int v = 0; v < 4; v++) music += voice_tick(&voices[v]);
        for (int v = 4; v < SND_VOICES; v++) sfx += voice_tick(&voices[v]);
        if (test_left > 0 && --test_left == 0) snd_voice_off(4);
        int32_t mix = music * music_vol / 255 + sfx * sfx_vol / 255;
        if (mix > 32767) mix = 32767;
        if (mix < -32767) mix = -32767;
        int32_t a = mix < 0 ? -mix : mix;
        if (a > pk) pk = a;
        out[2 * i] = (int16_t)mix;
        out[2 * i + 1] = (int16_t)mix;
    }
    if (pk > peak) peak = pk;
}

void snd_count_callback(void)
{
    if (cb_count < 63) cb_count++;
}

uint32_t snd_stats(void)
{
    uint32_t p = (uint32_t)peak & 0x7FFF;
    uint32_t sfx = sfx_count > 255 ? 255 : sfx_count;
    uint32_t cb = cb_count > 63 ? 63 : cb_count;
    peak = 0;
    sfx_count = 0;
    cb_count = 0;
    return p | sfx << 15 | cb << 23;
}

// --- Wii glue ---------------------------------------------------------------

#ifdef __wii__
#include <gccore.h>
#include <ogc/machine/processor.h>
#include <aesndlib.h>
#include "jolt_gfx.h"

void Sforeign_symbol(const char *s, void *v);

static int16_t bufs[2][SND_FRAMES * 2] __attribute__((aligned(32)));
_Static_assert(sizeof bufs[0] == DSP_STREAMBUFFER_SIZE, "one stream buffer");
static int cur;
static AESNDPB *voice;
static int ready;

static void stream_cb(AESNDPB *pb, u32 state)
{
    if (state == VOICE_STATE_STREAM) {
        snd_render(bufs[cur], SND_FRAMES);
        DCFlushRange(bufs[cur], sizeof bufs[cur]);
        AESND_SetVoiceBuffer(pb, bufs[cur], sizeof bufs[cur]);
        cur ^= 1;
        snd_count_callback();
    }
}

// Until the generated tables arrive: effect 0 is a 440 Hz pulse blip.
static const snd_effect spike_fx[1] = {
    {SND_WAVE_PULSE, 2, 0, 0, 10, 1, {{440, 440, 50, 15}}},
};

U jg_snd_init(void)
{
    jg_count();
    if (ready) return 1;
    snd_init();
    snd_tables(spike_fx, 1, NULL, 0, NULL, 0);
    AESND_Init();
    voice = AESND_AllocateVoice(stream_cb);
    if (!voice) return 0;
    AESND_SetVoiceFormat(voice, VOICE_STEREO16);
    AESND_SetVoiceFrequency(voice, 48000.0f);
    AESND_SetVoiceVolume(voice, 255, 255);
    AESND_SetVoiceStream(voice, true);
    AESND_SetVoiceStop(voice, false);
    ready = 1;
    return 1;
}

U jg_snd_test(U hz, U ms)
{
    jg_count();
    if (ready) {
        u32 level;
        _CPU_ISR_Disable(level);
        snd_test_tone((int)hz, (int)ms);
        _CPU_ISR_Restore(level);
    }
    return 0;
}

U jg_snd_stats(void)
{
    jg_count();
    if (!ready) return 0;
    u32 level;
    _CPU_ISR_Disable(level);
    U word = snd_stats();
    _CPU_ISR_Restore(level);
    return word;
}

U jg_sfx(U id)
{
    jg_count();
    if (!ready) return 0;
    u32 level;
    _CPU_ISR_Disable(level);
    snd_sfx((int)id);
    _CPU_ISR_Restore(level);
    return 0;
}

U jg_music(U song, U loop)
{
    jg_count();
    if (!ready) return 0;
    u32 level;
    _CPU_ISR_Disable(level);
    snd_music((int)song, (int)loop);
    _CPU_ISR_Restore(level);
    return 0;
}

U jg_music_stop(void)
{
    jg_count();
    if (!ready) return 0;
    u32 level;
    _CPU_ISR_Disable(level);
    snd_music_stop();
    _CPU_ISR_Restore(level);
    return 0;
}

U jg_music_pause(U on)
{
    jg_count();
    if (!ready) return 0;
    u32 level;
    _CPU_ISR_Disable(level);
    snd_music_pause((int)on);
    _CPU_ISR_Restore(level);
    return 0;
}

U jg_music_tempo(U pct)
{
    jg_count();
    if (!ready) return 0;
    u32 level;
    _CPU_ISR_Disable(level);
    snd_music_tempo((int)pct);
    _CPU_ISR_Restore(level);
    return 0;
}

U jg_snd_volume(U music, U sfx)
{
    jg_count();
    if (!ready) return 0;
    u32 level;
    _CPU_ISR_Disable(level);
    snd_volume((int)music, (int)sfx);
    _CPU_ISR_Restore(level);
    return 0;
}

void jg_snd_register(void)
{
#define R(f) Sforeign_symbol(#f, (void *)f)
    R(jg_snd_init); R(jg_snd_test); R(jg_snd_stats);
    R(jg_sfx); R(jg_music); R(jg_music_stop); R(jg_music_pause); R(jg_music_tempo); R(jg_snd_volume);
#undef R
}
#endif
