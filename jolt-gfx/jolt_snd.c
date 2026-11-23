// jolt-snd: the jg_* exports that give jolt programs Super Mona's sound. See jolt_snd.h.
#ifdef __wii__
#include "jolt_snd.h"
#include "snd_wii.h"
#include "mona_music.h"

void Sforeign_symbol(const char *s, void *v);

U jg_snd_init(void)
{
    jg_count();
    return (U)snd_start(mona_effects, MONA_EFFECTS, mona_instruments,
                        (int)(sizeof mona_instruments / sizeof mona_instruments[0]),
                        mona_songs, MONA_SONGS);
}

U jg_snd_stats(void)
{
    jg_count();
    return sndw_stats();
}

U jg_snd_music_peak(void)
{
    jg_count();
    return sndw_music_peak();
}

U jg_sfx(U id)
{
    jg_count();
    sndw_sfx((int)id);
    return 0;
}

U jg_music(U song, U loop)
{
    jg_count();
    sndw_music((int)song, (int)loop);
    return 0;
}

U jg_music_stop(void)
{
    jg_count();
    sndw_music_stop();
    return 0;
}

U jg_music_pause(U on)
{
    jg_count();
    sndw_music_pause((int)on);
    return 0;
}

U jg_music_tempo(U pct)
{
    jg_count();
    sndw_music_tempo((int)pct);
    return 0;
}

U jg_snd_volume(U music, U sfx)
{
    jg_count();
    sndw_volume((int)music, (int)sfx);
    return 0;
}

void jg_snd_register(void)
{
#define R(f) Sforeign_symbol(#f, (void *)f)
    R(jg_snd_init); R(jg_snd_stats); R(jg_snd_music_peak);
    R(jg_sfx); R(jg_music); R(jg_music_stop); R(jg_music_pause); R(jg_music_tempo); R(jg_snd_volume);
#undef R
}
#endif
