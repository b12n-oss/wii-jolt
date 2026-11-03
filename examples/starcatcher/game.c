// Star Catcher rules. See game.h.
#include "game.h"
#include <math.h>
#include <string.h>

#define PALETTE_SIZE 5
#define DEMO_BOTS 2

uint32_t game_rand(Game *g)
{
    uint32_t x = g->rng;
    x ^= x << 13; x ^= x >> 17; x ^= x << 5;
    g->rng = x;
    return x;
}

float game_randf(Game *g, float lo, float hi)
{
    return lo + (hi - lo) * ((game_rand(g) >> 8) / 16777216.0f);
}

float game_start_x(int player)
{
    return SCREEN_W * (player + 1) / (float)(MAX_PLAYERS + 1);
}

static void reset_players(Game *g)
{
    for (int p = 0; p < MAX_PLAYERS; p++) {
        Player *pl = &g->players[p];
        memset(pl, 0, sizeof *pl);
        pl->x = game_start_x(p);
        pl->bot_target = pl->x;
    }
}

static void clear_board(Game *g)
{
    memset(g->items, 0, sizeof g->items);
    memset(g->popups, 0, sizeof g->popups);
    g->spawn_timer = 0;
    g->catches_this_round = 0;
}

static void set_mode(Game *g, Mode m)
{
    g->mode = m;
    g->mode_time = 0;
}

void game_init(Game *g, uint32_t seed)
{
    memset(g, 0, sizeof *g);
    g->rng = seed ? seed : 0x9E3779B9u;
    reset_players(g);
    set_mode(g, MODE_TITLE);
}

bool game_in_basket(float bx, float x, float y, float r)
{
    // the item's bottom has reached the basket's rim, and its centre is over
    // the basket (a little forgiveness at the edges for small hands)
    return y + r >= BASKET_Y && y - r <= BASKET_Y + BASKET_H
        && fabsf(x - bx) <= BASKET_W / 2 + r * 0.5f;
}

static void spawn_item(Game *g)
{
    for (int i = 0; i < MAX_ITEMS; i++) {
        Item *it = &g->items[i];
        if (it->alive) continue;
        uint32_t roll = game_rand(g) % 100;
        it->alive = true;
        it->kind = roll < 8 ? ITEM_RAINBOW : roll < 22 ? ITEM_CLOUD : ITEM_STAR;
        it->r = it->kind == ITEM_CLOUD ? 24.0f : it->kind == ITEM_RAINBOW ? 18.0f : 16.0f;
        it->x = game_randf(g, it->r + 10, SCREEN_W - it->r - 10);
        it->y = -it->r;
        it->vy = it->kind == ITEM_RAINBOW ? game_randf(g, 200, 250)
               : it->kind == ITEM_CLOUD ? game_randf(g, 90, 130)
               : game_randf(g, 110, 175);
        it->spin = game_randf(g, 0, 6.2831853f);
        it->color = (int)(game_rand(g) % PALETTE_SIZE);
        return;
    }
}

static void burst(Game *g, float x, float y, int color, int count)
{
    for (int i = 0; i < MAX_PARTICLES && count > 0; i++) {
        Particle *pt = &g->particles[i];
        if (pt->alive) continue;
        float a = game_randf(g, 3.4f, 6.0f), s = game_randf(g, 80, 240);   // mostly upward
        pt->alive = true;
        pt->x = x; pt->y = y;
        pt->vx = cosf(a) * s; pt->vy = sinf(a) * s;
        pt->life = game_randf(g, 0.4f, 0.8f);
        pt->color = color;
        count--;
    }
}

static void popup(Game *g, float x, float y, int points, int player)
{
    for (int i = 0; i < MAX_POPUPS; i++) {
        Popup *pu = &g->popups[i];
        if (pu->alive) continue;
        pu->alive = true; pu->x = x; pu->y = y; pu->life = 0.8f;
        pu->points = points; pu->player = player;
        return;
    }
}

int game_joined_count(const Game *g)
{
    int n = 0;
    for (int p = 0; p < MAX_PLAYERS; p++) n += g->players[p].joined;
    return n;
}

int game_winner(const Game *g)
{
    int best = -1;
    for (int p = 0; p < MAX_PLAYERS; p++) {
        if (!g->players[p].joined) continue;
        if (best < 0 || g->players[p].score > g->players[best].score) best = p;
    }
    return best;
}

bool game_is_tie(const Game *g)
{
    int w = game_winner(g);
    if (w < 0) return false;
    for (int p = 0; p < MAX_PLAYERS; p++)
        if (p != w && g->players[p].joined && g->players[p].score == g->players[w].score) return true;
    return false;
}

float game_time_left(const Game *g)
{
    float total = g->mode == MODE_DEMO ? DEMO_SECONDS : ROUND_SECONDS;
    if (g->mode != MODE_PLAY && g->mode != MODE_DEMO) return g->mode == MODE_RESULTS ? 0 : total;
    float left = total - g->mode_time;
    return left > 0 ? left : 0;
}

static bool active(const Game *g, int p)
{
    if (g->mode == MODE_DEMO) return p < DEMO_BOTS;
    return g->players[p].joined;
}

static float bot_steer(Game *g, int p, float dt)
{
    Player *pl = &g->players[p];
    pl->bot_think -= dt;
    if (pl->bot_think <= 0) {
        // chase the lowest star that's still above the basket; ignore clouds
        float best_y = -1e9f;
        for (int i = 0; i < MAX_ITEMS; i++) {
            const Item *it = &g->items[i];
            if (!it->alive || it->kind == ITEM_CLOUD || it->y > BASKET_Y) continue;
            if (it->y > best_y) { best_y = it->y; pl->bot_target = it->x; }
        }
        pl->bot_think = game_randf(g, 0.25f, 0.6f);
    }
    float s = (pl->bot_target - pl->x) / 60.0f;
    return s > 1 ? 1 : s < -1 ? -1 : s;
}

static void move_baskets(Game *g, const PlayerInput in[MAX_PLAYERS], float dt)
{
    for (int p = 0; p < MAX_PLAYERS; p++) {
        Player *pl = &g->players[p];
        if (pl->rumble > 0) pl->rumble -= dt;
        if (pl->dizzy > 0) pl->dizzy -= dt;
        if (!active(g, p)) continue;
        float steer = g->mode == MODE_DEMO ? bot_steer(g, p, dt) : in[p].connected ? in[p].steer : 0;
        if (steer > 1) steer = 1;
        if (steer < -1) steer = -1;
        float speed = BASKET_SPEED * (pl->dizzy > 0 ? DIZZY_SPEED_SCALE : 1.0f);
        pl->x += steer * speed * dt;
        if (pl->x < BASKET_W / 2) pl->x = BASKET_W / 2;
        if (pl->x > SCREEN_W - BASKET_W / 2) pl->x = SCREEN_W - BASKET_W / 2;
    }
}

static void catch_items(Game *g)
{
    for (int i = 0; i < MAX_ITEMS; i++) {
        Item *it = &g->items[i];
        if (!it->alive) continue;
        for (int p = 0; p < MAX_PLAYERS; p++) {
            if (!active(g, p)) continue;
            Player *pl = &g->players[p];
            if (!game_in_basket(pl->x, it->x, it->y, it->r)) continue;
            it->alive = false;
            if (it->kind == ITEM_CLOUD) {
                pl->dizzy = DIZZY_SECONDS;
                pl->rumble = 0.4f;
                burst(g, it->x, it->y, -2, 14);
                popup(g, it->x, it->y, 0, p);
            } else {
                int pts = it->kind == ITEM_RAINBOW ? POINTS_RAINBOW : POINTS_STAR;
                pl->score += pts;
                pl->rumble = it->kind == ITEM_RAINBOW ? 0.25f : 0.1f;
                g->catches_this_round++;
                burst(g, it->x, it->y, it->kind == ITEM_RAINBOW ? -1 : it->color, it->kind == ITEM_RAINBOW ? 26 : 12);
                popup(g, it->x, it->y, pts, p);
            }
            break;
        }
    }
}

static void update_world(Game *g, float dt, bool spawn)
{
    if (spawn) {
        // a little faster as the round goes on: every 0.75 s down to 0.45 s
        float every = 0.75f - 0.3f * (g->mode_time / ROUND_SECONDS);
        if (every < 0.45f) every = 0.45f;
        g->spawn_timer -= dt;
        if (g->spawn_timer <= 0) { spawn_item(g); g->spawn_timer = every; }
    }
    for (int i = 0; i < MAX_ITEMS; i++) {
        Item *it = &g->items[i];
        if (!it->alive) continue;
        it->y += it->vy * dt;
        it->spin += dt * 3;
        if (it->y - it->r > SCREEN_H) it->alive = false;
    }
    for (int i = 0; i < MAX_PARTICLES; i++) {
        Particle *pt = &g->particles[i];
        if (!pt->alive) continue;
        pt->x += pt->vx * dt; pt->y += pt->vy * dt; pt->vy += 400 * dt;
        pt->life -= dt;
        if (pt->life <= 0) pt->alive = false;
    }
    for (int i = 0; i < MAX_POPUPS; i++) {
        Popup *pu = &g->popups[i];
        if (!pu->alive) continue;
        pu->y -= 50 * dt; pu->life -= dt;
        if (pu->life <= 0) pu->alive = false;
    }
}

static bool any_join(const PlayerInput in[MAX_PLAYERS])
{
    for (int p = 0; p < MAX_PLAYERS; p++) if (in[p].connected && in[p].join) return true;
    return false;
}

static void join_presses(Game *g, const PlayerInput in[MAX_PLAYERS])
{
    for (int p = 0; p < MAX_PLAYERS; p++)
        if (in[p].connected && in[p].join) g->players[p].joined = true;
}

void game_update(Game *g, const PlayerInput in[MAX_PLAYERS], float dt)
{
    g->mode_time += dt;
    switch (g->mode) {
    case MODE_TITLE:
        update_world(g, dt, true);   // stars fall behind the title too
        if (any_join(in)) {
            reset_players(g);
            clear_board(g);
            join_presses(g, in);
            set_mode(g, MODE_COUNTDOWN);
        } else if (g->mode_time >= ATTRACT_IDLE_SECONDS) {
            reset_players(g);
            clear_board(g);
            set_mode(g, MODE_DEMO);
        }
        break;
    case MODE_COUNTDOWN:
        join_presses(g, in);
        move_baskets(g, in, dt);     // practise steering while you wait
        update_world(g, dt, false);
        if (g->mode_time >= COUNTDOWN_SECONDS) { clear_board(g); set_mode(g, MODE_PLAY); }
        break;
    case MODE_PLAY:
        move_baskets(g, in, dt);
        update_world(g, dt, true);
        catch_items(g);
        if (g->mode_time >= ROUND_SECONDS) set_mode(g, MODE_RESULTS);
        break;
    case MODE_RESULTS:
        update_world(g, dt, false);
        if (g->mode_time >= RESULTS_LOCK_SECONDS && any_join(in)) {
            clear_board(g);
            set_mode(g, MODE_TITLE);
        }
        break;
    case MODE_DEMO:
        move_baskets(g, in, dt);
        update_world(g, dt, true);
        catch_items(g);
        if (any_join(in) || g->mode_time >= DEMO_SECONDS) {
            reset_players(g);
            clear_board(g);
            set_mode(g, MODE_TITLE);
        }
        break;
    }
}
