// snd_wii: the AESND stream voice that plays the synthesiser. See snd_wii.h.
#include <gccore.h>
#include <ogc/machine/processor.h>
#include <aesndlib.h>
#include "snd_wii.h"

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

int snd_start(const snd_effect *fx, int nfx, const snd_instrument *inst, int ninst,
              const snd_song *songs, int nsongs)
{
    if (ready) return 1;
    snd_init();
    snd_tables(fx, nfx, inst, ninst, songs, nsongs);
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

uint32_t sndw_stats(void)
{
    if (!ready) return 0;
    u32 level;
    _CPU_ISR_Disable(level);
    uint32_t word = snd_stats();
    _CPU_ISR_Restore(level);
    return word;
}

uint32_t sndw_music_peak(void)
{
    if (!ready) return 0;
    u32 level;
    _CPU_ISR_Disable(level);
    uint32_t p = snd_music_peak();
    _CPU_ISR_Restore(level);
    return p;
}

void sndw_sfx(int id)
{
    if (!ready) return;
    u32 level;
    _CPU_ISR_Disable(level);
    snd_sfx(id);
    _CPU_ISR_Restore(level);
}

void sndw_music(int song, int loop)
{
    if (!ready) return;
    u32 level;
    _CPU_ISR_Disable(level);
    snd_music(song, loop);
    _CPU_ISR_Restore(level);
}

void sndw_music_stop(void)
{
    if (!ready) return;
    u32 level;
    _CPU_ISR_Disable(level);
    snd_music_stop();
    _CPU_ISR_Restore(level);
}

void sndw_music_pause(int on)
{
    if (!ready) return;
    u32 level;
    _CPU_ISR_Disable(level);
    snd_music_pause(on);
    _CPU_ISR_Restore(level);
}

void sndw_music_tempo(int pct)
{
    if (!ready) return;
    u32 level;
    _CPU_ISR_Disable(level);
    snd_music_tempo(pct);
    _CPU_ISR_Restore(level);
}

void sndw_volume(int music, int sfx)
{
    if (!ready) return;
    u32 level;
    _CPU_ISR_Disable(level);
    snd_volume(music, sfx);
    _CPU_ISR_Restore(level);
}
