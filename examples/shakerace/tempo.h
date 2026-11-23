// The race tune's tempo as a percentage of its written speed: 100 when the
// leader stands still, 160 at top speed, linear between, rounded down to a
// multiple of 5. Pure, so it builds on the Mac for the host test.
#ifndef SHAKERACE_TEMPO_H
#define SHAKERACE_TEMPO_H

static inline int race_tempo_pct(float leader_speed, float max_speed)
{
    if (max_speed <= 0 || leader_speed <= 0) return 100;
    if (leader_speed >= max_speed) return 160;
    int pct = 100 + (int)(60.0f * leader_speed / max_speed);
    return pct - pct % 5;
}

#endif
