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

// Phase increment per sample for MIDI notes 0..127, built by snd_init and
// rounded to nearest. The 20..20000 Hz clamp applies to runtime Hz inputs only;
// the note table is exempt (notes 0..15 are below 20 Hz).
extern uint32_t snd_note_inc[128];

// Voice API. Callers clamp a frequency in Hz to 20..20000 before turning it
// into an increment.
void snd_voice_on(int v, const snd_instrument *inst, uint32_t inc);   // retrigger: phase kept, envelope to attack
void snd_voice_off(int v);                                            // release
void snd_voice_slide(int v, uint32_t target_inc, int ms);             // linear slide of the increment over ms
int snd_voice_active(int v);                                          // 1 until the release has ended
// Music (voices 0..3) and effects (4..6) levels, 0..255 each, default 255.
void snd_volume(int music, int sfx);

// Effects: recipes of frequency slides on voices 4, 5 (pulse) and 6 (noise).
#define SND_STEPS 6
typedef struct { uint16_t f0, f1, ms; uint8_t vol; } snd_step;   // slide f0 -> f1 Hz over ms at volume 0..15 (0 = a gap)
typedef struct { uint8_t wave, duty, chan, attack_ms, release_ms, nsteps; snd_step steps[SND_STEPS]; } snd_effect;
// chan: the preferred effect channel, 0 or 1 for a pulse effect, 2 for noise.

// Songs: patterns of rows, four tracks (0 pulse 1, 1 pulse 2, 2 triangle, 3 noise
// drums). note: MIDI 0..127 retriggers the voice with instrument `inst`, -1
// releases, -2 holds; on track 3 the note is a drum id 0..2 and inst is ignored.
// A NULL track is all rests. rpm is rows per minute; order indexes patterns;
// loop_at is the order index to return to, -1 plays once.
typedef struct { int8_t note; uint8_t inst; } snd_cell;
typedef struct { uint8_t rows; const snd_cell *track[4]; } snd_pattern;
typedef struct { uint16_t rpm; uint8_t npatterns, norder; int8_t loop_at; const snd_pattern *patterns; const uint8_t *order; } snd_song;
// The tables are borrowed, not copied; snd_init clears them.
void snd_tables(const snd_effect *fx, int nfx, const snd_instrument *inst, int ninst, const snd_song *songs, int nsongs);
void snd_sfx(int id);              // trigger; out-of-range ids are ignored
int snd_sfx_active(int chan);      // 1 while effect channel chan (0..2) plays, release included

// Music on voices 0..3. Out-of-range songs are ignored.
void snd_music(int song, int loop);   // from the first row; loop 0 forces play-once
void snd_music_stop(void);            // release the four music voices, clear the song
void snd_music_pause(int on);         // 1 holds the row and releases the voices, 0 resumes at that row
void snd_music_tempo(int pct);        // 50..200, applied from the next row; 100 = rpm
int snd_music_playing(void);          // 1 while a song is set and has not ended
int snd_music_row(void);              // order index * 256 + row, for tests

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
U jg_sfx(U id);
U jg_music(U song, U loop);
U jg_music_stop(void);
U jg_music_pause(U on);
U jg_music_tempo(U pct);
U jg_snd_volume(U music, U sfx);
void jg_snd_register(void);
#endif

#endif
