// Shake Race rules. See game.h.
#include "game.h"
#include <math.h>
#include <string.h>

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

static void reset_runners(Game *g, bool keep_joined)
{
    for (int p = 0; p < MAX_PLAYERS; p++) {
        bool j = keep_joined && g->players[p].joined;
        memset(&g->players[p], 0, sizeof g->players[p]);
        g->players[p].joined = j;
    }
    g->first_finish_time = -1;
    g->finishers = 0;
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
    g->shake_scale = 1;
    reset_runners(g, false);
    set_mode(g, MODE_TITLE);
}

float game_shake_scale(int level)
{
    if (level < SHAKE_LEVEL_MIN) level = SHAKE_LEVEL_MIN;
    if (level > SHAKE_LEVEL_MAX) level = SHAKE_LEVEL_MAX;
    return powf(2.0f, (level - SHAKE_LEVEL_DEFAULT) / 2.0f);
}

int game_joined_count(const Game *g)
{
    int n = 0;
    for (int p = 0; p < MAX_PLAYERS; p++) n += g->players[p].joined;
    return n;
}

static bool racing(const Game *g, int p)
{
    return g->mode == MODE_DEMO || g->players[p].joined;
}

// true if a ranks ahead of b
static bool ahead(const Player *a, const Player *b)
{
    if (a->finished != b->finished) return a->finished;
    if (a->finished) return a->place < b->place;
    return a->dist > b->dist;
}

int game_standings(const Game *g, int order[MAX_PLAYERS])
{
    int n = 0;
    for (int p = 0; p < MAX_PLAYERS; p++) if (racing(g, p)) order[n++] = p;
    for (int i = 1; i < n; i++)   // insertion sort, stable: lower index wins exact ties
        for (int j = i; j > 0 && ahead(&g->players[order[j]], &g->players[order[j - 1]]); j--) {
            int t = order[j]; order[j] = order[j - 1]; order[j - 1] = t;
        }
    return n;
}

bool game_tied(const Game *g, int player)
{
    const Player *a = &g->players[player];
    if (a->finished) return false;   // finish places are always distinct
    for (int p = 0; p < MAX_PLAYERS; p++)
        if (p != player && racing(g, p) && !g->players[p].finished && g->players[p].dist == a->dist) return true;
    return false;
}

static float bot_shake(Game *g, Player *pl, float dt)
{
    pl->bot_think -= dt;
    if (pl->bot_think <= 0) {
        pl->bot_effort = game_randf(g, 0.5f, 1.1f);   // a kid speeding up and flagging
        pl->bot_think = game_randf(g, 0.5f, 1.5f);
    }
    return SHAKE_DEADZONE + pl->bot_effort;
}

// `live` is false once the race is over: finishers coast to a stop, everyone
// else stays where they were, so the standings can't change on the results
// screen.
static void run(Game *g, const PlayerInput in[MAX_PLAYERS], float dt, bool live)
{
    for (int p = 0; p < MAX_PLAYERS; p++) {
        Player *pl = &g->players[p];
        if (pl->rumble > 0) pl->rumble -= dt;
        if (!racing(g, p) || (!live && !pl->finished)) continue;
        float s = g->mode == MODE_DEMO ? bot_shake(g, pl, dt) : in[p].connected ? in[p].shake : 0;
        if (pl->finished || !live) s = 0;   // coast to a stop past the line
        // the demo bots ignore shake power, so the demo looks the same at any level
        float gain = SHAKE_GAIN * (g->mode == MODE_DEMO ? 1.0f : g->shake_scale);
        if (s > SHAKE_DEADZONE) pl->speed += gain * (s - SHAKE_DEADZONE);
        pl->speed -= pl->speed * FRICTION * dt;
        if (pl->speed > MAX_SPEED) pl->speed = MAX_SPEED;
        if (pl->speed < 0.01f) pl->speed = 0;
        pl->dist += pl->speed * dt;
        pl->stride += pl->speed * dt;
        if (!pl->finished && pl->dist >= TRACK_METERS) {
            pl->finished = true;
            pl->place = ++g->finishers;
            pl->finish_time = g->mode_time;
            pl->rumble = pl->place == 1 ? 0.6f : 0.3f;
            if (g->first_finish_time < 0) g->first_finish_time = g->mode_time;
        }
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

static bool race_over(const Game *g, float limit)
{
    int racers = 0;
    for (int p = 0; p < MAX_PLAYERS; p++) racers += racing(g, p);
    if (g->finishers >= racers) return true;
    if (g->first_finish_time >= 0 && g->mode_time - g->first_finish_time >= FINISH_GRACE_SECONDS) return true;
    return g->mode_time >= limit;
}

void game_update(Game *g, const PlayerInput in[MAX_PLAYERS], float dt)
{
    g->mode_time += dt;
    switch (g->mode) {
    case MODE_TITLE:
        if (any_join(in)) {
            reset_runners(g, false);
            join_presses(g, in);
            set_mode(g, MODE_COUNTDOWN);
        } else if (g->mode_time >= ATTRACT_IDLE_SECONDS) {
            reset_runners(g, false);
            set_mode(g, MODE_DEMO);
        }
        break;
    case MODE_COUNTDOWN:
        join_presses(g, in);   // no running yet: shaking early does nothing
        if (g->mode_time >= COUNTDOWN_SECONDS) set_mode(g, MODE_RACE);
        break;
    case MODE_RACE:
        run(g, in, dt, true);
        if (race_over(g, RACE_MAX_SECONDS)) set_mode(g, MODE_RESULTS);
        break;
    case MODE_RESULTS:
        run(g, in, dt, false);
        if (g->mode_time >= RESULTS_LOCK_SECONDS && any_join(in)) {
            reset_runners(g, true);   // same players, straight into a rematch
            set_mode(g, MODE_COUNTDOWN);
        }
        break;
    case MODE_DEMO:
        run(g, in, dt, true);
        if (any_join(in) || (race_over(g, DEMO_SECONDS) && g->mode_time > 2)) {
            reset_runners(g, false);
            set_mode(g, MODE_TITLE);
        }
        break;
    }
}
