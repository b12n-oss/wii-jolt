// Host-side tests for the Balloon Pop rules. Build and run on the Mac:
//   cc -std=c99 -Wall -I.. ../game.c test_game.c -lm -o test_game && ./test_game
// (jolt test:balloons does exactly that.)
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

static void press(PlayerInput in[MAX_PLAYERS], int p, float x, float y)
{
    memset(in, 0, sizeof(PlayerInput) * MAX_PLAYERS);
    in[p].connected = in[p].aiming = in[p].fire = true;
    in[p].x = x; in[p].y = y;
}

static Balloon *place(Game *g, int i, float x, float y, bool gold)
{
    Balloon *b = &g->balloons[i];
    memset(b, 0, sizeof *b);
    b->alive = true; b->x = x; b->y = y; b->r = 30; b->vy = -40; b->gold = gold;
    return b;
}

static void clear_balloons(Game *g)
{
    for (int i = 0; i < MAX_BALLOONS; i++) g->balloons[i].alive = false;
}

static void test_title_to_play(void)
{
    Game g; game_init(&g, 42);
    CHECK(g.mode == MODE_TITLE, "starts on the title screen");
    PlayerInput in[MAX_PLAYERS];
    press(in, 1, 320, 240);
    game_update(&g, in, 1.0f / 60);
    CHECK(g.mode == MODE_COUNTDOWN, "A on the title starts the countdown");
    CHECK(g.players[1].joined, "the player who pressed A joins");
    CHECK(!g.players[0].joined, "players who didn't press stay out");
    press(in, 2, 100, 100);
    game_update(&g, in, 1.0f / 60);
    CHECK(g.players[2].joined, "a second player can join during the countdown");
    step(&g, none, COUNTDOWN_SECONDS + 0.1f);
    CHECK(g.mode == MODE_PLAY, "countdown ends in play");
    CHECK(game_joined_count(&g) == 2, "two players joined");
}

static void test_pop_scores(void)
{
    Game g; game_init(&g, 1);
    g.mode = MODE_PLAY; g.players[0].joined = true;
    clear_balloons(&g);
    place(&g, 0, 200, 200, false);
    place(&g, 1, 400, 200, true);
    CHECK(game_try_pop(&g, 0, 205, 195) == POINTS_NORMAL, "hit inside the radius scores 1");
    CHECK(!g.balloons[0].alive, "a popped balloon is gone");
    CHECK(game_try_pop(&g, 0, 205, 195) == 0, "same spot again hits nothing");
    CHECK(game_try_pop(&g, 0, 400 + 31, 200) == 0, "just outside the radius misses");
    CHECK(game_try_pop(&g, 0, 400, 200) == POINTS_GOLD, "gold balloon scores 5");
    CHECK(g.players[0].score == POINTS_NORMAL + POINTS_GOLD, "score accumulates");
    CHECK(g.players[0].rumble > 0, "a pop starts the rumble");
}

static void test_fire_through_update(void)
{
    Game g; game_init(&g, 7);
    g.mode = MODE_PLAY; g.players[3].joined = true;
    clear_balloons(&g);
    place(&g, 5, 320, 300, false);
    PlayerInput in[MAX_PLAYERS];
    press(in, 3, 320, 300);
    game_update(&g, in, 1.0f / 60);
    CHECK(g.players[3].score == 1, "fire on a balloon through game_update scores");
    CHECK(g.pops_this_round == 1, "pop counter increments");
    int alive_particles = 0;
    for (int i = 0; i < MAX_PARTICLES; i++) alive_particles += g.particles[i].alive;
    CHECK(alive_particles > 0, "a pop makes particles");
}

static void test_unjoined_cannot_score(void)
{
    Game g; game_init(&g, 7);
    g.mode = MODE_PLAY; g.players[0].joined = true;
    clear_balloons(&g);
    place(&g, 0, 320, 300, false);
    PlayerInput in[MAX_PLAYERS];
    press(in, 2, 320, 300);
    game_update(&g, in, 1.0f / 60);
    CHECK(g.players[2].score == 0 && g.balloons[0].alive, "a player who didn't join can't pop");
    CHECK(g.players[2].joined == false, "and doesn't join mid-round");
}

static void test_balloons_rise_and_leave(void)
{
    Game g; game_init(&g, 3);
    g.mode = MODE_PLAY; g.players[0].joined = true;
    clear_balloons(&g);
    Balloon *b = place(&g, 0, 320, 100, false);
    float y0 = b->y;
    game_update(&g, none, 0.5f);
    CHECK(b->y < y0, "balloons rise");
    // a balloon well above the top gets recycled
    clear_balloons(&g);
    place(&g, 0, 320, -200, false);
    game_update(&g, none, 1.0f / 60);
    CHECK(!g.balloons[0].alive, "a balloon that floats off the top is removed");
}

static void test_spawning_keeps_screen_busy(void)
{
    Game g; game_init(&g, 99);
    g.mode = MODE_PLAY; g.players[0].joined = true;
    clear_balloons(&g);
    step(&g, none, 5.0f);
    int alive = 0;
    for (int i = 0; i < MAX_BALLOONS; i++) alive += g.balloons[i].alive;
    CHECK(alive >= 4, "after 5 seconds there are several balloons up");
    bool any_onscreen = false;
    for (int i = 0; i < MAX_BALLOONS; i++) {
        Balloon *b = &g.balloons[i];
        if (b->alive && b->x - b->r >= 0 && b->x + b->r <= SCREEN_W) any_onscreen = true;
    }
    CHECK(any_onscreen, "spawned balloons fit horizontally on screen");
}

static void test_round_ends_with_winner(void)
{
    Game g; game_init(&g, 5);
    PlayerInput in[MAX_PLAYERS];
    press(in, 0, 10, 10);
    game_update(&g, in, 1.0f / 60);
    press(in, 1, 10, 10);
    game_update(&g, in, 1.0f / 60);
    step(&g, none, COUNTDOWN_SECONDS + 0.1f);
    CHECK(g.mode == MODE_PLAY, "playing");
    g.players[1].score = 3;
    step(&g, none, ROUND_SECONDS + 0.1f);
    CHECK(g.mode == MODE_RESULTS, "the round ends after ROUND_SECONDS");
    CHECK(game_winner(&g) == 1, "highest score wins");
    CHECK(!game_is_tie(&g), "not a tie");
    CHECK(g.rounds_played == 1, "round counted");
    press(in, 0, 10, 10);
    game_update(&g, in, 1.0f / 60);
    CHECK(g.mode == MODE_RESULTS, "A is ignored right after the results appear");
    step(&g, none, RESULTS_LOCK_SECONDS);
    game_update(&g, in, 1.0f / 60);
    CHECK(g.mode == MODE_TITLE, "A after the lock returns to the title");
    CHECK(!g.players[0].joined && g.players[0].score == 0, "players reset for the next round");
}

static void test_tie(void)
{
    Game g; game_init(&g, 5);
    g.players[0].joined = g.players[2].joined = true;
    g.players[0].score = g.players[2].score = 4;
    CHECK(game_is_tie(&g), "equal top scores is a tie");
    CHECK(game_winner(&g) == 0, "winner on a tie is the lowest index");
    g.players[2].score = 5;
    CHECK(!game_is_tie(&g), "no longer a tie");
}

static void test_demo_mode(void)
{
    Game g; game_init(&g, 11);
    step(&g, none, ATTRACT_IDLE_SECONDS + 0.1f);
    CHECK(g.mode == MODE_DEMO, "idle on the title starts the demo");
    step(&g, none, 10.0f);
    int total = 0;
    for (int p = 0; p < MAX_PLAYERS; p++) total += g.players[p].score;
    CHECK(total > 0, "demo bots pop balloons");
    PlayerInput in[MAX_PLAYERS];
    press(in, 0, 320, 240);
    game_update(&g, in, 1.0f / 60);
    CHECK(g.mode == MODE_TITLE, "pressing A during the demo returns to the title");
    for (int p = 0; p < MAX_PLAYERS; p++)
        CHECK(!g.players[p].joined && g.players[p].score == 0, "demo scores don't leak into a real game");
    game_init(&g, 11);
    step(&g, none, ATTRACT_IDLE_SECONDS + DEMO_SECONDS + 0.2f);
    CHECK(g.mode == MODE_TITLE, "the demo ends by itself");
}

static void test_rng(void)
{
    Game a, b; game_init(&a, 1234); game_init(&b, 1234);
    bool same = true;
    for (int i = 0; i < 100; i++) same = same && game_rand(&a) == game_rand(&b);
    CHECK(same, "same seed, same sequence");
    bool in_range = true;
    for (int i = 0; i < 1000; i++) { float f = game_randf(&a, -2, 3); in_range = in_range && f >= -2 && f < 3; }
    CHECK(in_range, "randf stays in [lo, hi)");
    Game z; game_init(&z, 0);
    CHECK(game_rand(&z) != 0, "seed 0 doesn't lock xorshift at zero");
}

int main(void)
{
    test_title_to_play();
    test_pop_scores();
    test_fire_through_update();
    test_unjoined_cannot_score();
    test_balloons_rise_and_leave();
    test_spawning_keeps_screen_busy();
    test_round_ends_with_winner();
    test_tie();
    test_demo_mode();
    test_rng();
    printf("%d checks, %d failures\n", checks, failures);
    return failures ? 1 : 0;
}
