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

#define SND_VOICES 7            /* 0 pulse1, 1 pulse2, 2 triangle, 3 noise, 4 sfx pulse A, 5 sfx pulse B, 6 sfx noise */
#define SND_WAVE_PULSE 0
#define SND_WAVE_TRI 1
#define SND_WAVE_NOISE 2

// duty: pulse 0 = 12.5 %, 1 = 25 %, 2 = 50 %; noise 0 = long (bit 0 ^ bit 1),
// 1 = short (bit 0 ^ bit 6). ADSR in ms (attack, decay, release); s_vol is the
// sustain level 0..15 and vol the peak 0..15.
typedef struct { uint8_t wave, duty, a_ms, d_ms, s_vol, vol; uint16_t r_ms; } snd_instrument;

// Phase increment per sample for MIDI notes 0..127, built by snd_init.
extern uint32_t snd_note_inc[128];

// Voice API. Callers clamp a frequency in Hz to 20..20000 before turning it
// into an increment.
void snd_voice_on(int v, const snd_instrument *inst, uint32_t inc);   // retrigger: phase kept, envelope to attack
void snd_voice_off(int v);                                            // release
void snd_voice_slide(int v, uint32_t target_inc, int ms);             // linear slide of the increment over ms
int snd_voice_active(int v);                                          // 1 until the release has ended
// Music (voices 0..3) and effects (4..6) levels, 0..255 each, default 255.
void snd_volume(int music, int sfx);

// Core.
void snd_init(void);
// Interleaved stereo, both channels equal.
void snd_render(int16_t *out, int frames);
// Plays voice 4 as a 50 % pulse at full volume for ms, then releases it.
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
