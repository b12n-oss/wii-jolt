// Host-side test for race_tempo_pct (jolt test builds and runs it).
#include "tempo.h"
#include <stdio.h>

static int failures = 0, checks = 0;
#define CHECK(cond, msg) do { checks++; if (!(cond)) { failures++; \
    printf("FAIL %s:%d %s\n", __FILE__, __LINE__, msg); } } while (0)

int main(void)
{
    const float m = 15.0f;
    CHECK(race_tempo_pct(0, m) == 100, "speed 0 is 100");
    CHECK(race_tempo_pct(m, m) == 160, "max speed is 160");
    CHECK(race_tempo_pct(2 * m, m) == 160, "above max is capped at 160");
    CHECK(race_tempo_pct(-1, m) == 100, "negative speed is 100");
    CHECK(race_tempo_pct(m / 2, m) == 130, "half of max is 130");
    CHECK(race_tempo_pct(1, 0) == 100, "no max speed gives 100");
    for (int i = 0; i <= 300; i++) {
        int t = race_tempo_pct(m * (float)i / 200.0f, m);
        CHECK(t % 5 == 0, "a multiple of 5");
        CHECK(t >= 100 && t <= 160, "within 100..160");
    }
    printf("test_tempo: %d checks, %d failed\n", checks, failures);
    return failures ? 1 : 0;
}
