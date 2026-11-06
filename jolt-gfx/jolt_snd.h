// jolt-snd: a small chip synthesiser for Super Mona, rendered on libogc's
// AESND stream voice. The core (snd_*) is plain C99 with no libogc, so a host
// cc builds and tests it; the Wii glue (jg_snd_*) sits behind __wii__.
//
// jg_snd_stats packs three counters and resets them on each call:
//   bits 0..14   peak       the largest |sample| rendered, 0..32767
//   bits 15..22  sfx        effects triggered, saturating at 255
//   bits 23..28  callbacks  stream callbacks run, saturating at 63
// The word stays below 2^29, a 32-bit Chez fixnum.
//
// The stream callback runs from the audio DMA interrupt. It never allocates,
// calls into Chez, prints or uses floats: bounded integer work on static state.
#ifndef JOLT_SND_H
#define JOLT_SND_H
#include <stdint.h>

#define SND_RATE 48000
#define SND_FRAMES 288

// Core.
void snd_init(void);
// Interleaved stereo, both channels equal.
void snd_render(int16_t *out, int frames);
void snd_test_tone(int hz, int ms);
uint32_t snd_stats(void);
void snd_count_callback(void);

#ifdef __wii__
#include "jolt_gfx.h"
// Exports, registered by jg_snd_register. Every call counts as a jg_* call.
// Until jg_snd_init has returned 1 they are counted no-ops.
U jg_snd_init(void);
U jg_snd_test(U hz, U ms);
U jg_snd_stats(void);
void jg_snd_register(void);
#endif

#endif
