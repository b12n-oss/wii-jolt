// Host-side tests for the Star Catcher rules (bb test builds and runs them).
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

static void clear_items(Game *g)
{
    for (int i = 0; i < MAX_ITEMS; i++) g->items[i].alive = false;
}

static Item *drop(Game *g, int i, ItemKind kind, float x, float y)
{
    Item *it = &g->items[i];
    memset(it, 0, sizeof *it);
    it->alive = true; it->kind = kind; it->x = x; it->y = y; it->r = 16; it->vy = 120;
    return it;
}

static Game playing(int players)
{
    Game g; game_init(&g, 3);
    for (int p = 0; p < players; p++) g.players[p].joined = true;
    g.mode = MODE_PLAY; g.mode_time = 0; g.spawn_timer = 1000;   // no random spawns
    clear_items(&g);
    return g;
}

static void test_join_and_start(void)
{
    Game g; game_init(&g, 42);
    CHECK(g.mode == MODE_TITLE, "starts on the title screen");
    PlayerInput in[MAX_PLAYERS];
    join(in, 2);
    game_update(&g, in, 1.0f / 60);
    CHECK(g.mode == MODE_COUNTDOWN, "a join press starts the countdown");
    CHECK(g.players[2].joined && !g.players[0].joined, "only the player who pressed joins");
    join(in, 0);
    game_update(&g, in, 1.0f / 60);
    CHECK(g.players[0].joined, "another player can join during the countdown");
    step(&g, none, COUNTDOWN_SECONDS + 0.1f);
    CHECK(g.mode == MODE_PLAY, "the countdown ends in play");
    CHECK(game_joined_count(&g) == 2, "two players joined");
}

static void test_steering(void)
{
    Game g = playing(1);
    PlayerInput in[MAX_PLAYERS] = { 0 };
    in[0].connected = true;
    float x0 = g.players[0].x;
    in[0].steer = 1;
    step(&g, in, 0.5f);
    CHECK(g.players[0].x > x0 + BASKET_SPEED * 0.4f, "full right tilt moves the basket right at full speed");
    in[0].steer = -1;
    step(&g, in, 5);
    CHECK(fabsf(g.players[0].x - BASKET_W / 2) < 0.01f, "the basket stops at the left wall");
    in[0].steer = 5;
    float before = g.players[0].x;
    game_update(&g, in, 0.1f);
    CHECK(g.players[0].x - before <= BASKET_SPEED * 0.1f + 0.01f, "steer beyond 1 is clamped");
    in[0].connected = false; in[0].steer = 1;
    before = g.players[0].x;
    game_update(&g, in, 0.1f);
    CHECK(g.players[0].x == before, "a disconnected remote doesn't move its basket");
}

static void test_tilt_steer(void)
{
    CHECK(game_tilt_steer(0.05f, -1) == 0, "small wobbles are ignored");
    CHECK(game_tilt_steer(-TILT_FULL, -1) == 1, "with the default sign, gy = -0.5 is full right");
    CHECK(game_tilt_steer(-1.0f, -1) == 1, "steering is capped at 1");
    CHECK(game_tilt_steer(-TILT_FULL, 1) == -1, "flipping the sign reverses the steering");
    float half = game_tilt_steer(-(TILT_DEADZONE + TILT_FULL) / 2, -1);
    CHECK(half > 0.45f && half < 0.55f, "halfway between the dead zone and full tip is half speed");
}

static void test_basket_geometry(void)
{
    CHECK(game_in_basket(300, 300, BASKET_Y, 16), "an item at the rim, centred, is caught");
    CHECK(!game_in_basket(300, 300, BASKET_Y - 40, 16), "an item still above the rim is not");
    CHECK(!game_in_basket(300, 300 + BASKET_W, BASKET_Y, 16), "an item a basket-width away is not");
    CHECK(game_in_basket(300, 300 + BASKET_W / 2 + 6, BASKET_Y, 16), "the edges are a little forgiving");
}

static void test_catch_scores(void)
{
    Game g = playing(2);
    float x = g.players[0].x;
    drop(&g, 0, ITEM_STAR, x, BASKET_Y - 17);
    drop(&g, 1, ITEM_RAINBOW, x, BASKET_Y - 60);
    step(&g, none, 0.6f);
    CHECK(!g.items[0].alive && !g.items[1].alive, "both items were caught");
    CHECK(g.players[0].score == POINTS_STAR + POINTS_RAINBOW, "star 1 + rainbow 5");
    CHECK(g.players[1].score == 0, "the other player scored nothing");
    CHECK(g.players[0].rumble > 0, "a catch rumbles");
    CHECK(g.catches_this_round == 2, "catches are counted");
}

static void test_one_basket_per_item(void)
{
    Game g = playing(2);
    g.players[1].x = g.players[0].x;   // baskets on top of each other
    drop(&g, 0, ITEM_STAR, g.players[0].x, BASKET_Y - 17);
    step(&g, none, 0.3f);
    CHECK(g.players[0].score + g.players[1].score == POINTS_STAR, "an item scores for one basket only");
}

static void test_cloud_makes_dizzy(void)
{
    Game g = playing(1);
    drop(&g, 0, ITEM_CLOUD, g.players[0].x, BASKET_Y - 17);
    step(&g, none, 0.2f);
    CHECK(g.players[0].dizzy > 0, "a cloud makes the basket dizzy");
    CHECK(g.players[0].score == 0, "a cloud doesn't take points away");
    PlayerInput in[MAX_PLAYERS] = { 0 };
    in[0].connected = true; in[0].steer = 1;
    float x0 = g.players[0].x;
    game_update(&g, in, 0.1f);
    CHECK(fabsf((g.players[0].x - x0) - BASKET_SPEED * DIZZY_SPEED_SCALE * 0.1f) < 0.5f, "dizzy baskets move slower");
    step(&g, none, DIZZY_SECONDS);
    CHECK(g.players[0].dizzy <= 0, "dizziness wears off");
}

static void test_missed_items_fall_away(void)
{
    Game g = playing(1);
    drop(&g, 0, ITEM_STAR, g.players[0].x + 200, BASKET_Y);
    step(&g, none, 2);
    CHECK(!g.items[0].alive, "a missed star falls off the bottom");
    CHECK(g.players[0].score == 0, "and scores nothing");
}

static void test_round_and_results(void)
{
    Game g = playing(2);
    g.players[0].score = 4; g.players[1].score = 9;
    step(&g, none, ROUND_SECONDS + 0.1f);
    CHECK(g.mode == MODE_RESULTS, "the round ends after 60 seconds");
    CHECK(game_winner(&g) == 1 && !game_is_tie(&g), "the top score wins");
    PlayerInput in[MAX_PLAYERS];
    join(in, 0);
    game_update(&g, in, 1.0f / 60);
    CHECK(g.mode == MODE_RESULTS, "A right away doesn't skip the results");
    step(&g, none, RESULTS_LOCK_SECONDS);
    game_update(&g, in, 1.0f / 60);
    CHECK(g.mode == MODE_TITLE, "A after the lock goes back to the title");
    g.players[0].joined = g.players[1].joined = true;
    g.players[0].score = g.players[1].score = 3;
    CHECK(game_is_tie(&g), "equal scores are a tie");
}

static void test_spawning(void)
{
    Game g = playing(1);
    g.spawn_timer = 0;
    step(&g, none, 10);
    int alive = 0;
    for (int i = 0; i < MAX_ITEMS; i++) alive += g.items[i].alive;
    CHECK(alive > 3, "items keep falling during play");
    Game h; game_init(&h, 99);
    h.mode = MODE_PLAY; h.spawn_timer = 0;
    int seen[3] = { 0 };
    for (int n = 0; n < 2000; n++) { clear_items(&h); h.spawn_timer = 0; game_update(&h, none, 0.001f);
        for (int i = 0; i < MAX_ITEMS; i++) if (h.items[i].alive) seen[h.items[i].kind]++; }
    CHECK(seen[ITEM_STAR] > seen[ITEM_CLOUD] && seen[ITEM_CLOUD] > 0 && seen[ITEM_RAINBOW] > 0,
          "stars are commonest; clouds and rainbows both turn up");
}

static void test_demo(void)
{
    Game g; game_init(&g, 5);
    step(&g, none, ATTRACT_IDLE_SECONDS + 0.1f);
    CHECK(g.mode == MODE_DEMO, "an idle title screen starts the demo");
    step(&g, none, DEMO_SECONDS - 1);
    int total = 0;
    for (int p = 0; p < MAX_PLAYERS; p++) total += g.players[p].score;
    CHECK(total >= 5, "the demo bots catch stars");
    CHECK(g.players[2].score == 0 && g.players[3].score == 0, "only two bots play");
    PlayerInput in[MAX_PLAYERS];
    join(in, 3);
    game_update(&g, in, 1.0f / 60);
    CHECK(g.mode == MODE_TITLE, "a press during the demo returns to the title");
    step(&g, none, ATTRACT_IDLE_SECONDS + DEMO_SECONDS + 0.5f);
    CHECK(g.mode == MODE_TITLE, "the demo ends by itself and goes back to the title");
}

int main(void)
{
    test_join_and_start();
    test_steering();
    test_tilt_steer();
    test_basket_geometry();
    test_catch_scores();
    test_one_basket_per_item();
    test_cloud_makes_dizzy();
    test_missed_items_fall_away();
    test_round_and_results();
    test_spawning();
    test_demo();
    printf("starcatcher: %d checks, %d failed\n", checks, failures);
    return failures ? 1 : 0;
}
