// Star Catcher: game rules, with no raylib or Wii calls, so they build and get
// tested on the Mac (test/test_game.c) as well as on the Wii.
//
// Hold the Wiimote sideways and tip it like a steering wheel to slide your
// basket. Catch falling stars; rainbow stars are worth 5; a storm cloud makes
// your basket slow and wobbly for a moment.
#ifndef STARCATCHER_GAME_H
#define STARCATCHER_GAME_H

#include <stdbool.h>
#include <stdint.h>

#define SCREEN_W 640
#define SCREEN_H 480
#define MAX_PLAYERS 4
#define MAX_ITEMS 32
#define MAX_PARTICLES 256
#define MAX_POPUPS 16

#define ROUND_SECONDS 60.0f
#define COUNTDOWN_SECONDS 3.0f
#define RESULTS_LOCK_SECONDS 2.0f
#define ATTRACT_IDLE_SECONDS 8.0f
#define DEMO_SECONDS 20.0f

#define BASKET_Y 420.0f        // top edge of every basket
#define BASKET_W 84.0f
#define BASKET_H 34.0f
#define BASKET_SPEED 430.0f    // pixels per second at full tilt
#define DIZZY_SECONDS 1.5f
#define DIZZY_SPEED_SCALE 0.35f

#define POINTS_STAR 1
#define POINTS_RAINBOW 5

typedef enum { MODE_TITLE, MODE_COUNTDOWN, MODE_PLAY, MODE_RESULTS, MODE_DEMO } Mode;
typedef enum { ITEM_STAR, ITEM_RAINBOW, ITEM_CLOUD } ItemKind;

// What one Wiimote did this frame. `steer` is -1 (full left) to 1 (full
// right), already worked out from tilt or the D-pad; `join` is true only on
// the frame a join button (A, 1 or 2) went down.
typedef struct {
    bool connected;
    float steer;
    bool join;
} PlayerInput;

typedef struct {
    bool alive;
    ItemKind kind;
    float x, y;       // centre
    float r;
    float vy;         // pixels per second, positive = falling
    float spin;       // phase for drawing
    int color;        // palette index for stars
} Item;

typedef struct {
    bool alive;
    float x, y, vx, vy, life;
    int color;        // -1 = rainbow, -2 = cloud grey
} Particle;

typedef struct {
    bool alive;
    float x, y, life;
    int points;       // 0 = "dizzy!" label
    int player;
} Popup;

typedef struct {
    bool joined;
    int score;
    float x;          // basket centre
    float dizzy;      // seconds of slow-and-wobbly left
    float rumble;     // seconds of rumble left
    float bot_think;  // demo bot: seconds until it picks a new target
    float bot_target;
} Player;

typedef struct {
    Mode mode;
    float mode_time;
    float spawn_timer;
    uint32_t rng;
    Item items[MAX_ITEMS];
    Particle particles[MAX_PARTICLES];
    Popup popups[MAX_POPUPS];
    Player players[MAX_PLAYERS];
    int catches_this_round;
} Game;

void game_init(Game *g, uint32_t seed);
// Advance by dt seconds. In MODE_DEMO bots steer and any `join` returns to the
// title.
void game_update(Game *g, const PlayerInput in[MAX_PLAYERS], float dt);

// Where each basket starts: spread evenly across the screen.
float game_start_x(int player);

// Does an item at (x, y) with radius r land in the basket centred at bx?
// Exposed for tests.
bool game_in_basket(float bx, float x, float y, float r);

int game_winner(const Game *g);   // top score, lowest index on a tie; -1 if no one joined
bool game_is_tie(const Game *g);
int game_joined_count(const Game *g);
float game_time_left(const Game *g);

uint32_t game_rand(Game *g);
float game_randf(Game *g, float lo, float hi);

#endif
