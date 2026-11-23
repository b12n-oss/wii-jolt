// snd_wii: the synthesiser (snd.h) on libogc's AESND stream voice. Wii only,
// and nothing here knows about jolt, so the C games link it directly.
//
// Every call from a game thread runs with interrupts off, because the stream
// callback runs from the audio DMA interrupt and shares the synth's state.
// Until snd_start has returned 1 the sndw_* calls are no-ops (stats return 0).
#ifndef SND_WII_H
#define SND_WII_H
#include <stdint.h>
#include "snd.h"

// Registers the tables and starts the stream voice. 1 on success, and 1 again
// on later calls without restarting anything.
int snd_start(const snd_effect *fx, int nfx, const snd_instrument *inst, int ninst,
              const snd_song *songs, int nsongs);
void sndw_sfx(int id);
void sndw_music(int song, int loop);
void sndw_music_stop(void);
void sndw_music_pause(int on);
void sndw_music_tempo(int pct);
void sndw_volume(int music, int sfx);
uint32_t sndw_stats(void);        // see snd_stats; resets on read
uint32_t sndw_music_peak(void);   // see snd_music_peak; resets on read
#endif
