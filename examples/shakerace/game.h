// Shake Race: game rules, with no raylib or Wii calls, so they build and get
// tested on the Mac (test/test_game.c) as well as on the Wii.
//
// Everyone gets a lane. Shake your Wiimote and your runner speeds up; stop
// shaking and it slows down. First to the finish line wins.
#ifndef SHAKERACE_GAME_H
#define SHAKERACE_GAME_H

#include <stdbool.h>
#include <stdint.h>

#define SCREEN_W 640
#define SCREEN_H 480
#define MAX_PLAYERS 4

#define TRACK_METERS 100.0f
#define COUNTDOWN_SECONDS 3.0f
#define RACE_MAX_SECONDS 60.0f      // the race ends by itself after this
#define FINISH_GRACE_SECONDS 8.0f   // after the winner crosses, others get this long
#define RESULTS_LOCK_SECONDS 2.0f
#define ATTRACT_IDLE_SECONDS 8.0f
#define DEMO_SECONDS 20.0f

// Speed model, per reading (one per frame at 60 fps): a shake above the dead
// zone adds speed, and speed drains away by FRICTION per second. Steady
// shaking of `s` g per frame settles at about
//   SHAKE_GAIN * (s - SHAKE_DEADZONE) * 60 / FRICTION   metres per second,
// capped at MAX_SPEED. Dolphin's scripted shake reaches 2-2.5 g per frame;
// a held-still remote reads under 0.1.
#define SHAKE_DEADZONE 0.25f
#define SHAKE_GAIN 0.5f
#define FRICTION 1.6f
#define MAX_SPEED 15.0f

typedef enum { MODE_TITLE, MODE_COUNTDOWN, MODE_RACE, MODE_RESULTS, MODE_DEMO } Mode;

// What one Wiimote did this frame. `shake` is how much its acceleration
// changed since the last frame, in g; `join` is true only on the frame A or B
// went down.
typedef struct {
    bool connected;
    float shake;
    bool join;
} PlayerInput;

typedef struct {
    bool joined;
    float dist;          // metres run
    float speed;         // metres per second
    float stride;        // animation phase, advances with distance
    bool finished;
    int place;           // 1 = first; 0 = not placed yet
    float finish_time;   // seconds into the race
    float rumble;        // seconds of rumble left
    float bot_effort;    // demo bot: how hard it's shaking right now
    float bot_think;
} Player;

typedef struct {
    Mode mode;
    float mode_time;
    float first_finish_time;   // < 0 until someone crosses
    int finishers;
    uint32_t rng;
    Player players[MAX_PLAYERS];
} Game;

void game_init(Game *g, uint32_t seed);
void game_update(Game *g, const PlayerInput in[MAX_PLAYERS], float dt);

// Places for everyone who raced: finishers by time, then the rest by distance.
// Fills order[] with player indexes, best first; returns how many.
int game_standings(const Game *g, int order[MAX_PLAYERS]);
int game_joined_count(const Game *g);
// Does `player` share their place in the standings with someone else? Two
// runners who didn't finish and covered the same distance do.
bool game_tied(const Game *g, int player);

uint32_t game_rand(Game *g);
float game_randf(Game *g, float lo, float hi);

#endif
