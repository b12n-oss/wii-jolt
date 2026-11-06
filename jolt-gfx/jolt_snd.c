// jolt-snd: the synthesiser core and its AESND glue. See jolt_snd.h.
#include "jolt_snd.h"

// --- core -------------------------------------------------------------------
#include <math.h>

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

void snd_render(int16_t *out, int frames)
{
    int32_t pk = 0;
    for (int i = 0; i < frames; i++) {
        int32_t music = 0, sfx = 0;
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

U jg_snd_init(void)
{
    jg_count();
    if (ready) return 1;
    snd_init();
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

void jg_snd_register(void)
{
#define R(f) Sforeign_symbol(#f, (void *)f)
    R(jg_snd_init); R(jg_snd_test); R(jg_snd_stats);
#undef R
}
#endif
