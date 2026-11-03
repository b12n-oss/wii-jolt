// Balloon Pop: game rules, with no raylib or Wii calls, so they build and
// get tested on the Mac (test/test_game.c) as well as on the Wii.
#ifndef BALLOONS_GAME_H
#define BALLOONS_GAME_H

#include <stdbool.h>
#include <stdint.h>

#define SCREEN_W 640
#define SCREEN_H 480
#define MAX_PLAYERS 4
#define MAX_BALLOONS 24
#define MAX_PARTICLES 256
#define MAX_POPUPS 16

#define ROUND_SECONDS 60.0f
#define COUNTDOWN_SECONDS 3.0f
#define RESULTS_LOCK_SECONDS 2.0f   // ignore A on the results screen this long
#define ATTRACT_IDLE_SECONDS 8.0f   // title idle time before the demo starts
#define DEMO_SECONDS 20.0f

#define POINTS_NORMAL 1
#define POINTS_GOLD 5

typedef enum { MODE_TITLE, MODE_COUNTDOWN, MODE_PLAY, MODE_RESULTS, MODE_DEMO } Mode;

// What one Wiimote did this frame. `aiming` is false when the pointer is off
// screen (or the remote isn't connected); `fire` is true only on the frame A
// or B went down.
typedef struct {
    bool connected;
    bool aiming;
    float x, y;
    bool fire;
} PlayerInput;

typedef struct {
    bool alive;
    float x, y;        // centre
    float r;           // radius
    float vy;          // pixels per second, negative = rising
    float wobble;      // phase for the sideways drift
    int color;         // index into the palette, chosen by the renderer
    bool gold;
} Balloon;

typedef struct {
    bool alive;
    float x, y, vx, vy, life;
    int color;
} Particle;

typedef struct {
    bool alive;
    float x, y, life;
    int points;
    int player;
} Popup;

typedef struct {
    bool joined;
    int score;
    float x, y;        // last pointer position (for drawing the cursor)
    bool aiming;
    float rumble;      // seconds of rumble left
    // demo bot state
    int target;        // balloon index the bot is chasing, -1 = none
    float aim_delay;
} Player;

typedef struct {
    Mode mode;
    float mode_time;   // seconds spent in the current mode
    float spawn_timer;
    uint32_t rng;
    Balloon balloons[MAX_BALLOONS];
    Particle particles[MAX_PARTICLES];
    Popup popups[MAX_POPUPS];
    Player players[MAX_PLAYERS];
    int pops_this_round;
    int rounds_played;
} Game;

void game_init(Game *g, uint32_t seed);
// Advance by dt seconds with this frame's inputs. In MODE_DEMO the inputs are
// ignored for aiming (bots play) but any `fire` returns to the title.
void game_update(Game *g, const PlayerInput in[MAX_PLAYERS], float dt);

// Pop the first live balloon under (x, y) for `player`. Returns points scored,
// 0 if nothing was hit. Exposed for tests.
int game_try_pop(Game *g, int player, float x, float y);

// Index of the player with the top score (lowest index on a tie), -1 if no
// one joined.
int game_winner(const Game *g);
bool game_is_tie(const Game *g);
int game_joined_count(const Game *g);
float game_time_left(const Game *g);

// Small deterministic RNG (xorshift32), exposed for tests.
uint32_t game_rand(Game *g);
float game_randf(Game *g, float lo, float hi);

#endif
