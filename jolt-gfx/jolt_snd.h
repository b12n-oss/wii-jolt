// jolt-snd: Super Mona's jolt exports over the synthesiser. The core is snd.h
// and the AESND layer is snd_wii.h; this header re-exports snd.h so
// mona_music.h and the host tests still get the types from one include.
// The jg_* wrappers sit behind __wii__.
#ifndef JOLT_SND_H
#define JOLT_SND_H
#include "snd.h"

#ifdef __wii__
#include "jolt_gfx.h"
// Exports, registered by jg_snd_register. Every call counts as a jg_* call.
// Until jg_snd_init has returned 1 they are counted no-ops.
// jg_snd_stats packs the snd_stats word (see snd.h).
U jg_snd_init(void);
U jg_snd_stats(void);
U jg_snd_music_peak(void);
U jg_sfx(U id);
U jg_music(U song, U loop);
U jg_music_stop(void);
U jg_music_pause(U on);
U jg_music_tempo(U pct);
U jg_snd_volume(U music, U sfx);
void jg_snd_register(void);
#endif

#endif
