// Host-side tests for the Shake Race rules (bb test builds and runs them).
#include "game.h"
#include <math.h>
#include <stdio.h>
#include <string.h>

static int failures = 0, checks = 0;
#define CHECK(cond, msg) do { checks++; if (!(cond)) { failures++; \
    printf("FAIL %s:%d %s\n", __FILE__, __LINE__, msg); } } while (0)

static PlayerInput none[MAX_PLAYERS];

static void step(Game *g, const PlayerInput in[MAX_PLAYERS], float seconds)
{
    for (float t = 0; t < seconds; t += 1.0f / 60) game_update(g, in, 1.0f / 60);
}

static void join(PlayerInput in[MAX_PLAYERS], int p)
{
    memset(in, 0, sizeof(PlayerInput) * MAX_PLAYERS);
    in[p].connected = in[p].join = true;
}

static Game racing(int players)
{
    Game g; game_init(&g, 3);
    for (int p = 0; p < players; p++) g.players[p].joined = true;
    g.mode = MODE_RACE; g.mode_time = 0;
    return g;
}

static void shaking(PlayerInput in[MAX_PLAYERS], float s0, float s1, float s2, float s3)
{
    float s[4] = { s0, s1, s2, s3 };
    for (int p = 0; p < MAX_PLAYERS; p++) { in[p].connected = true; in[p].join = false; in[p].shake = s[p]; }
}

static void test_join_and_countdown(void)
{
    Game g; game_init(&g, 1);
    CHECK(g.mode == MODE_TITLE, "starts on the title screen");
    PlayerInput in[MAX_PLAYERS];
    join(in, 1);
    game_update(&g, in, 1.0f / 60);
    CHECK(g.mode == MODE_COUNTDOWN && g.players[1].joined, "A on the title joins and starts the countdown");
    join(in, 3);
    game_update(&g, in, 1.0f / 60);
    CHECK(g.players[3].joined, "another player can join during the countdown");
    shaking(in, 3, 3, 3, 3);
    step(&g, in, 1);
    CHECK(g.players[1].dist == 0 && g.players[1].speed == 0, "shaking during the countdown doesn't move anyone");
    step(&g, none, COUNTDOWN_SECONDS);
    CHECK(g.mode == MODE_RACE, "the countdown ends in the race");
    CHECK(game_joined_count(&g) == 2, "two players are racing");
}

static void test_shake_speed(void)
{
    Game g = racing(2);
    PlayerInput in[MAX_PLAYERS] = { 0 };
    shaking(in, 0.1f, 0.6f, 0, 0);
    step(&g, in, 2);
    CHECK(g.players[0].speed == 0 && g.players[0].dist == 0, "a still remote (under the dead zone) doesn't run");
    CHECK(g.players[1].speed > 5, "a steady shake builds speed");
    float expected = SHAKE_GAIN * (0.6f - SHAKE_DEADZONE) * 60 / FRICTION;
    step(&g, in, 5);
    CHECK(fabsf(g.players[1].speed - expected) < 0.6f, "a steady shake settles near the documented speed");
    shaking(in, 5, 5, 0, 0);
    step(&g, in, 1);
    CHECK(g.players[0].speed <= MAX_SPEED, "speed is capped");
    float v = g.players[1].speed;
    step(&g, none, 1);
    CHECK(g.players[1].speed < v * 0.3f, "stopping shaking slows the runner quickly");
    g.players[2].joined = false;
    shaking(in, 0, 0, 5, 0);
    step(&g, in, 1);
    CHECK(g.players[2].dist == 0, "a remote that didn't join doesn't run");
}

static void test_finish_order(void)
{
    Game g = racing(3);
    PlayerInput in[MAX_PLAYERS] = { 0 };
    shaking(in, 3, 0.7f, 0, 0);
    for (int i = 0; i < 60 * 30 && !g.players[0].finished; i++) game_update(&g, in, 1.0f / 60);
    CHECK(g.players[0].rumble > 0, "crossing the line rumbles");
    CHECK(g.first_finish_time == g.players[0].finish_time, "the first finish time is recorded");
    for (int i = 0; i < 60 * 40 && g.mode == MODE_RACE; i++) game_update(&g, in, 1.0f / 60);
    CHECK(g.players[0].finished && g.players[0].place == 1, "the hardest shaker wins");
    CHECK(g.players[1].finished && g.players[1].place == 2, "the steady shaker is second");
    CHECK(!g.players[2].finished, "a player who never shakes doesn't finish");
    CHECK(g.players[0].finish_time < g.players[1].finish_time, "finish times follow places");
    CHECK(g.mode == MODE_RESULTS, "the race ends once the grace time is up");
    int order[MAX_PLAYERS];
    int n = game_standings(&g, order);
    CHECK(n == 3 && order[0] == 0 && order[1] == 1 && order[2] == 2, "standings: finishers, then the rest");
}

static void test_everyone_finishes_ends_race(void)
{
    Game g = racing(2);
    PlayerInput in[MAX_PLAYERS] = { 0 };
    shaking(in, 3, 3, 0, 0);
    step(&g, in, 12);
    CHECK(g.mode == MODE_RESULTS, "the race ends when everyone has finished");
    CHECK(g.players[0].place != g.players[1].place, "two finishers get different places");
}

static void test_time_limit(void)
{
    Game g = racing(2);
    step(&g, none, RACE_MAX_SECONDS + 0.1f);
    CHECK(g.mode == MODE_RESULTS, "nobody shaking: the race still ends at the time limit");
    CHECK(game_tied(&g, 0) && game_tied(&g, 1), "two runners who never moved are tied");
    PlayerInput in[MAX_PLAYERS] = { 0 };
    shaking(in, 5, 5, 0, 0);
    step(&g, in, 1);
    CHECK(g.players[0].dist == 0, "shaking on the results screen doesn't move an unfinished runner");
}

static void test_unfinished_ranked_by_distance(void)
{
    Game g = racing(3);
    g.players[0].dist = 20; g.players[1].dist = 70; g.players[2].dist = 45;
    int order[MAX_PLAYERS];
    game_standings(&g, order);
    CHECK(order[0] == 1 && order[1] == 2 && order[2] == 0, "non-finishers rank by distance");
    CHECK(!game_tied(&g, 0), "different distances aren't a tie");
}

static void test_rematch(void)
{
    Game g = racing(2);
    PlayerInput in[MAX_PLAYERS] = { 0 };
    shaking(in, 3, 3, 0, 0);
    for (int i = 0; i < 60 * 90 && g.mode == MODE_RACE; i++) game_update(&g, in, 1.0f / 60);
    join(in, 0);
    game_update(&g, in, 1.0f / 60);
    CHECK(g.mode == MODE_RESULTS, "A right away doesn't skip the results");
    step(&g, none, RESULTS_LOCK_SECONDS);
    game_update(&g, in, 1.0f / 60);
    CHECK(g.mode == MODE_COUNTDOWN, "A after the lock starts a rematch");
    CHECK(g.players[0].joined && g.players[1].joined, "the same players are still in");
    CHECK(g.players[0].dist == 0 && !g.players[0].finished && g.finishers == 0, "the runners are back at the start");
}

static void test_demo(void)
{
    Game g; game_init(&g, 9);
    step(&g, none, ATTRACT_IDLE_SECONDS + 0.1f);
    CHECK(g.mode == MODE_DEMO, "an idle title starts the demo");
    float best = 0;
    for (int i = 0; i < 60 * 6; i++) {
        game_update(&g, none, 1.0f / 60);
        for (int p = 0; p < MAX_PLAYERS; p++) if (g.players[p].dist > best) best = g.players[p].dist;
    }
    CHECK(best > 30, "the demo runners run");
    step(&g, none, DEMO_SECONDS);
    CHECK(g.mode == MODE_TITLE, "the demo ends by itself");
    step(&g, none, ATTRACT_IDLE_SECONDS + 1);
    PlayerInput in[MAX_PLAYERS];
    join(in, 2);
    game_update(&g, in, 1.0f / 60);
    CHECK(g.mode == MODE_TITLE, "a press during the demo returns to the title");
}

int main(void)
{
    test_join_and_countdown();
    test_shake_speed();
    test_finish_order();
    test_everyone_finishes_ends_race();
    test_time_limit();
    test_unfinished_ranked_by_distance();
    test_rematch();
    test_demo();
    printf("shakerace: %d checks, %d failed\n", checks, failures);
    return failures ? 1 : 0;
}
