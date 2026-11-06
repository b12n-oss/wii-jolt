// jolt-snd: the synthesiser core and its AESND glue. See jolt_snd.h.
#include "jolt_snd.h"

// --- core -------------------------------------------------------------------

static int32_t test_left;    // samples left of the test tone
static uint32_t test_inc;    // phase step per sample
static uint32_t phase;
static int32_t peak;
static uint32_t sfx_count;
static uint32_t cb_count;

void snd_init(void)
{
    test_left = 0;
    test_inc = 0;
    phase = 0;
    peak = 0;
    sfx_count = 0;
    cb_count = 0;
}

// The one float use, outside the callback.
void snd_test_tone(int hz, int ms)
{
    test_inc = (uint32_t)(hz * 4294967296.0 / SND_RATE);
    test_left = (int32_t)((int64_t)ms * SND_RATE / 1000);
}

void snd_render(int16_t *out, int frames)
{
    for (int i = 0; i < frames; i++) {
        int16_t s = 0;
        if (test_left > 0) {
            s = (phase >> 31) ? -2048 : 2048;
            phase += test_inc;
            test_left--;
        }
        int32_t a = s < 0 ? -s : s;
        if (a > peak) peak = a;
        out[2 * i] = s;
        out[2 * i + 1] = s;
    }
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
#include <aesndlib.h>
#include "jolt_gfx.h"

void Sforeign_symbol(const char *s, void *v);

static int16_t bufs[2][SND_FRAMES * 2] __attribute__((aligned(32)));
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
    if (ready) snd_test_tone((int)hz, (int)ms);
    return 0;
}

U jg_snd_stats(void)
{
    jg_count();
    return ready ? snd_stats() : 0;
}

void jg_snd_register(void)
{
#define R(f) Sforeign_symbol(#f, (void *)f)
    R(jg_snd_init); R(jg_snd_test); R(jg_snd_stats);
#undef R
}
#endif
