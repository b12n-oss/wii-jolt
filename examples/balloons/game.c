// Balloon Pop rules. See game.h.
#include "game.h"
#include <math.h>
#include <string.h>

#define PALETTE_SIZE 6
#define DEMO_BOTS 2
#define BOT_SPEED 260.0f     // pixels per second the demo cursor moves

uint32_t game_rand(Game *g)
{
    uint32_t x = g->rng;
    x ^= x << 13; x ^= x >> 17; x ^= x << 5;
    g->rng = x;
    return x;
}

float game_randf(Game *g, float lo, float hi)
{
    // 24 random bits -> [0, 1), so the result never reaches hi
    return lo + (hi - lo) * ((game_rand(g) >> 8) / 16777216.0f);
}

static void reset_players(Game *g)
{
    for (int p = 0; p < MAX_PLAYERS; p++) {
        Player *pl = &g->players[p];
        pl->joined = false;
        pl->score = 0;
        pl->rumble = 0;
        pl->target = -1;
        pl->aim_delay = 0;
        pl->x = SCREEN_W / 2.0f;
        pl->y = SCREEN_H / 2.0f;
    }
}

static void clear_board(Game *g)
{
    memset(g->balloons, 0, sizeof g->balloons);
    memset(g->popups, 0, sizeof g->popups);
    g->spawn_timer = 0;
    g->pops_this_round = 0;
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

static void spawn_balloon(Game *g)
{
    for (int i = 0; i < MAX_BALLOONS; i++) {
        Balloon *b = &g->balloons[i];
        if (b->alive) continue;
        b->alive = true;
        b->gold = (game_rand(g) % 12) == 0;
        b->r = b->gold ? 22.0f : game_randf(g, 24.0f, 36.0f);
        b->x = game_randf(g, b->r + 8, SCREEN_W - b->r - 8);
        b->y = SCREEN_H + b->r;
        b->vy = -(b->gold ? game_randf(g, 110.0f, 150.0f) : game_randf(g, 50.0f, 105.0f));
        b->wobble = game_randf(g, 0, 6.2831853f);
        b->color = (int)(game_rand(g) % PALETTE_SIZE);
        return;
    }
}

static void burst(Game *g, float x, float y, int color, int count)
{
    for (int i = 0; i < MAX_PARTICLES && count > 0; i++) {
        Particle *pt = &g->particles[i];
        if (pt->alive) continue;
        float a = game_randf(g, 0, 6.2831853f), s = game_randf(g, 60, 260);
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

int game_try_pop(Game *g, int player, float x, float y)
{
    for (int i = 0; i < MAX_BALLOONS; i++) {
        Balloon *b = &g->balloons[i];
        if (!b->alive) continue;
        float dx = x - b->x, dy = y - b->y;
        if (dx * dx + dy * dy > b->r * b->r) continue;
        int points = b->gold ? POINTS_GOLD : POINTS_NORMAL;
        b->alive = false;
        g->players[player].score += points;
        g->players[player].rumble = b->gold ? 0.25f : 0.12f;
        g->pops_this_round++;
        burst(g, b->x, b->y, b->gold ? -1 : b->color, b->gold ? 28 : 16);
        popup(g, b->x, b->y, points, player);
        return points;
    }
    return 0;
}

static void update_balloons(Game *g, float dt, bool spawn)
{
    if (spawn) {
        g->spawn_timer -= dt;
        if (g->spawn_timer <= 0) {
            spawn_balloon(g);
            g->spawn_timer = game_randf(g, 0.35f, 0.8f);
        }
    }
    for (int i = 0; i < MAX_BALLOONS; i++) {
        Balloon *b = &g->balloons[i];
        if (!b->alive) continue;
        b->wobble += dt * 2.0f;
        b->y += b->vy * dt;
        b->x += sinf(b->wobble) * 18.0f * dt;
        if (b->x < b->r) b->x = b->r;
        if (b->x > SCREEN_W - b->r) b->x = SCREEN_W - b->r;
        if (b->y + b->r < 0) b->alive = false;
    }
}

static void update_effects(Game *g, float dt)
{
    for (int i = 0; i < MAX_PARTICLES; i++) {
        Particle *pt = &g->particles[i];
        if (!pt->alive) continue;
        pt->life -= dt;
        if (pt->life <= 0) { pt->alive = false; continue; }
        pt->vy += 400.0f * dt;   // a little gravity on the confetti
        pt->x += pt->vx * dt;
        pt->y += pt->vy * dt;
    }
    for (int i = 0; i < MAX_POPUPS; i++) {
        Popup *pu = &g->popups[i];
        if (!pu->alive) continue;
        pu->life -= dt;
        pu->y -= 40.0f * dt;
        if (pu->life <= 0) pu->alive = false;
    }
    for (int p = 0; p < MAX_PLAYERS; p++) {
        Player *pl = &g->players[p];
        if (pl->rumble > 0) { pl->rumble -= dt; if (pl->rumble < 0) pl->rumble = 0; }
    }
}

static int nearest_balloon(const Game *g, float x, float y)
{
    int best = -1;
    float best_d = 1e30f;
    for (int i = 0; i < MAX_BALLOONS; i++) {
        const Balloon *b = &g->balloons[i];
        // only chase balloons that are fully on screen
        if (!b->alive || b->y > SCREEN_H * 0.66f || b->y < b->r) continue;
        float dx = b->x - x, dy = b->y - y, d = dx * dx + dy * dy;
        if (d < best_d) { best_d = d; best = i; }
    }
    return best;
}

static void update_bots(Game *g, float dt)
{
    for (int p = 0; p < DEMO_BOTS; p++) {
        Player *pl = &g->players[p];
        pl->aiming = true;
        if (pl->target < 0 || !g->balloons[pl->target].alive) {
            pl->target = nearest_balloon(g, pl->x, pl->y);
            pl->aim_delay = game_randf(g, 0.6f, 1.4f);
            if (pl->target < 0) continue;
        }
        Balloon *b = &g->balloons[pl->target];
        float dx = b->x - pl->x, dy = b->y - pl->y;
        float d = sqrtf(dx * dx + dy * dy), step = BOT_SPEED * dt;
        if (d > step) { pl->x += dx / d * step; pl->y += dy / d * step; }
        else { pl->x = b->x; pl->y = b->y; }
        pl->aim_delay -= dt;
        if (d < b->r * 0.6f && pl->aim_delay <= 0) {
            game_try_pop(g, p, pl->x, pl->y);
            pl->target = -1;
        }
    }
}

static bool any_fire(const PlayerInput in[MAX_PLAYERS])
{
    for (int p = 0; p < MAX_PLAYERS; p++) if (in[p].connected && in[p].fire) return true;
    return false;
}

static void track_pointers(Game *g, const PlayerInput in[MAX_PLAYERS])
{
    for (int p = 0; p < MAX_PLAYERS; p++) {
        Player *pl = &g->players[p];
        pl->aiming = in[p].connected && in[p].aiming;
        if (pl->aiming) { pl->x = in[p].x; pl->y = in[p].y; }
    }
}

static void join_on_fire(Game *g, const PlayerInput in[MAX_PLAYERS])
{
    for (int p = 0; p < MAX_PLAYERS; p++)
        if (in[p].connected && in[p].fire) g->players[p].joined = true;
}

void game_update(Game *g, const PlayerInput in[MAX_PLAYERS], float dt)
{
    g->mode_time += dt;
    switch (g->mode) {
    case MODE_TITLE:
        track_pointers(g, in);
        update_balloons(g, dt, true);   // balloons drift past behind the title
        if (any_fire(in)) {
            reset_players(g);
            join_on_fire(g, in);
            set_mode(g, MODE_COUNTDOWN);
        } else if (g->mode_time >= ATTRACT_IDLE_SECONDS) {
            reset_players(g);
            clear_board(g);
            for (int p = 0; p < DEMO_BOTS; p++) g->players[p].joined = true;
            set_mode(g, MODE_DEMO);
        }
        break;
    case MODE_COUNTDOWN:
        track_pointers(g, in);
        join_on_fire(g, in);
        update_balloons(g, dt, false);
        if (g->mode_time >= COUNTDOWN_SECONDS) {
            clear_board(g);
            set_mode(g, MODE_PLAY);
        }
        break;
    case MODE_PLAY:
        track_pointers(g, in);
        for (int p = 0; p < MAX_PLAYERS; p++) {
            const PlayerInput *pi = &in[p];
            if (g->players[p].joined && pi->connected && pi->aiming && pi->fire)
                game_try_pop(g, p, pi->x, pi->y);
        }
        update_balloons(g, dt, true);
        if (g->mode_time >= ROUND_SECONDS) {
            g->rounds_played++;
            set_mode(g, MODE_RESULTS);
        }
        break;
    case MODE_RESULTS:
        track_pointers(g, in);
        update_balloons(g, dt, false);
        if (g->mode_time >= RESULTS_LOCK_SECONDS && any_fire(in)) {
            reset_players(g);
            clear_board(g);
            set_mode(g, MODE_TITLE);
        }
        break;
    case MODE_DEMO:
        update_bots(g, dt);
        update_balloons(g, dt, true);
        if (any_fire(in) || g->mode_time >= DEMO_SECONDS) {
            reset_players(g);
            clear_board(g);
            set_mode(g, MODE_TITLE);
        }
        break;
    }
    update_effects(g, dt);
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
    if (g->mode == MODE_PLAY) return ROUND_SECONDS - g->mode_time;
    if (g->mode == MODE_DEMO) return DEMO_SECONDS - g->mode_time;
    if (g->mode == MODE_COUNTDOWN) return COUNTDOWN_SECONDS - g->mode_time;
    return 0;
}
