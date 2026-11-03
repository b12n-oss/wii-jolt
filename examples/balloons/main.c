// Balloon Pop: point a Wiimote at the TV and press A (or B) to pop balloons.
// Up to four players, 60-second rounds, gold balloons are worth 5.
// The rules live in game.c; this file is input, drawing and rumble.
#include "raylib.h"
#include "game.h"
// SDL's Wii port runs its own main() first (SDL_wii_main.c): it calls
// WPAD_Init, hooks the power/reset buttons and mounts the SD card, then calls
// ours as SDL_main. Including SDL_main.h does that renaming. Without it the
// Wiimotes are never initialised (WPAD_Probe answers WPAD_ERR_NO_CONTROLLER).
#include <SDL2/SDL_main.h>
#include <gccore.h>
#include <wiiuse/wpad.h>
#include <ogc/lwp_watchdog.h>   // gettime(), for the RNG seed
#include <stdio.h>
#include <math.h>

static const Color PLAYER_COLORS[MAX_PLAYERS] = {
    { 40, 120, 255, 255 },   // P1 blue
    { 235, 50, 60, 255 },    // P2 red
    { 40, 180, 70, 255 },    // P3 green
    { 250, 190, 20, 255 },   // P4 yellow
};
static const Color BALLOON_COLORS[6] = {
    { 230, 41, 55, 255 }, { 255, 109, 194, 255 }, { 0, 158, 47, 255 },
    { 0, 121, 241, 255 }, { 200, 122, 255, 255 }, { 255, 161, 0, 255 },
};
static const Color GOLDEN = { 255, 203, 0, 255 };

// ---------------------------------------------------------------- input

static u32 prev_held[MAX_PLAYERS];
static bool rumbling[MAX_PLAYERS];

static void input_init(void)
{
    // SDL's main has already called WPAD_Init (see the SDL_main.h note at the
    // top). Ask every remote for buttons, accelerometer and IR, with IR in
    // screen pixels. SDL's main sets the same; repeating it keeps this file
    // correct on its own.
    WPAD_SetDataFormat(WPAD_CHAN_ALL, WPAD_FMT_BTNS_ACC_IR);
    WPAD_SetVRes(WPAD_CHAN_ALL, SCREEN_W, SCREEN_H);
}

// SDL reads the remotes (WPAD_ReadPending) while raylib polls events inside
// EndDrawing, so by the next frame WPAD_Data holds fresh state. We detect
// presses ourselves from btns_h, which can't miss a press between reads.
static bool read_inputs(PlayerInput in[MAX_PLAYERS])
{
    bool home = false;
    for (int p = 0; p < MAX_PLAYERS; p++) {
        u32 type;
        PlayerInput *pi = &in[p];
        pi->connected = WPAD_Probe(p, &type) == WPAD_ERR_NONE;
        if (!pi->connected) { pi->aiming = pi->fire = false; prev_held[p] = 0; continue; }
        WPADData *d = WPAD_Data(p);
        u32 held = d->btns_h;
        u32 down = held & ~prev_held[p];
        prev_held[p] = held;
        pi->fire = (down & (WPAD_BUTTON_A | WPAD_BUTTON_B)) != 0;
        pi->aiming = d->ir.valid;
        pi->x = d->ir.x;
        pi->y = d->ir.y;
        if (down & WPAD_BUTTON_HOME) home = true;
    }
    return home;
}

static void update_rumble(const Game *g)
{
    for (int p = 0; p < MAX_PLAYERS; p++) {
        bool want = g->players[p].rumble > 0 && g->mode != MODE_DEMO;
        if (want != rumbling[p]) { WPAD_Rumble(p, want); rumbling[p] = want; }
    }
}

// ---------------------------------------------------------------- drawing

static void draw_sky(float t)
{
    DrawRectangleGradientV(0, 0, SCREEN_W, SCREEN_H, (Color){ 110, 190, 255, 255 }, (Color){ 210, 240, 255, 255 });
    for (int i = 0; i < 4; i++) {
        float x = fmodf(i * 190.0f + t * (8 + i * 3), SCREEN_W + 200) - 100;
        float y = 60 + i * 70;
        DrawCircle(x, y, 26, (Color){ 255, 255, 255, 200 });
        DrawCircle(x + 28, y + 6, 22, (Color){ 255, 255, 255, 200 });
        DrawCircle(x - 26, y + 8, 18, (Color){ 255, 255, 255, 200 });
    }
    DrawRectangle(0, SCREEN_H - 24, SCREEN_W, 24, (Color){ 90, 180, 80, 255 });
}

static void draw_balloon(const Balloon *b, float t)
{
    Color c = b->gold ? GOLDEN : BALLOON_COLORS[b->color];
    float rx = b->r * 0.85f, ry = b->r;
    // string: a few wavy segments hanging from the knot
    Vector2 prev = { b->x, b->y + ry + 4 };
    for (int s = 1; s <= 6; s++) {
        Vector2 nxt = { b->x + sinf(t * 3 + s + b->wobble) * 3, b->y + ry + 4 + s * 7 };
        DrawLineV(prev, nxt, (Color){ 80, 80, 80, 255 });
        prev = nxt;
    }
    DrawTriangle((Vector2){ b->x, b->y + ry - 2 }, (Vector2){ b->x - 5, b->y + ry + 5 },
                 (Vector2){ b->x + 5, b->y + ry + 5 }, c);
    DrawEllipse(b->x, b->y, rx, ry, c);
    DrawEllipse(b->x - rx * 0.35f, b->y - ry * 0.4f, rx * 0.22f, ry * 0.3f, (Color){ 255, 255, 255, 140 });
    if (b->gold) {
        float pulse = 0.5f + 0.5f * sinf(t * 10);
        DrawPoly((Vector2){ b->x, b->y }, 5, ry * 0.45f, t * 90, (Color){ 255, 255, 255, (unsigned char)(120 + pulse * 120) });
    }
}

static void draw_cursor(int p, const Player *pl)
{
    if (!pl->aiming) return;
    Color c = PLAYER_COLORS[p];
    DrawRing((Vector2){ pl->x, pl->y }, 13, 18, 0, 360, 24, c);
    DrawRing((Vector2){ pl->x, pl->y }, 11, 13, 0, 360, 24, WHITE);
    DrawLineEx((Vector2){ pl->x - 24, pl->y }, (Vector2){ pl->x - 8, pl->y }, 3, c);
    DrawLineEx((Vector2){ pl->x + 8, pl->y }, (Vector2){ pl->x + 24, pl->y }, 3, c);
    DrawLineEx((Vector2){ pl->x, pl->y - 24 }, (Vector2){ pl->x, pl->y - 8 }, 3, c);
    DrawLineEx((Vector2){ pl->x, pl->y + 8 }, (Vector2){ pl->x, pl->y + 24 }, 3, c);
    DrawText(TextFormat("P%d", p + 1), pl->x + 16, pl->y + 12, 20, c);
}

static void draw_centered(const char *text, int y, int size, Color c)
{
    int w = MeasureText(text, size);
    DrawText(text, SCREEN_W / 2 - w / 2 + 3, y + 3, size, (Color){ 0, 0, 0, 90 });
    DrawText(text, SCREEN_W / 2 - w / 2, y, size, c);
}

static void draw_hud(const Game *g)
{
    DrawRectangle(0, 0, SCREEN_W, 36, (Color){ 0, 0, 0, 90 });
    int x = 12;
    for (int p = 0; p < MAX_PLAYERS; p++) {
        if (!g->players[p].joined) continue;
        DrawText(TextFormat("P%d %d", p + 1, g->players[p].score), x, 8, 24, PLAYER_COLORS[p]);
        x += 120;
    }
    int secs = (int)ceilf(game_time_left(g));
    const char *tl = TextFormat("%d", secs < 0 ? 0 : secs);
    DrawText(tl, SCREEN_W - 16 - MeasureText(tl, 28), 5, 28, secs <= 10 ? RED : WHITE);
}

static void draw_effects(const Game *g)
{
    for (int i = 0; i < MAX_PARTICLES; i++) {
        const Particle *pt = &g->particles[i];
        if (!pt->alive) continue;
        Color c = pt->color < 0 ? GOLDEN : BALLOON_COLORS[pt->color];
        DrawCircle(pt->x, pt->y, 3 + pt->life * 3, c);
    }
    for (int i = 0; i < MAX_POPUPS; i++) {
        const Popup *pu = &g->popups[i];
        if (!pu->alive) continue;
        DrawText(TextFormat("+%d", pu->points), pu->x - 10, pu->y - 10, pu->points > 1 ? 32 : 24,
                 PLAYER_COLORS[pu->player]);
    }
}

static void draw_results(const Game *g)
{
    DrawRectangle(80, 70, SCREEN_W - 160, SCREEN_H - 140, (Color){ 255, 255, 255, 220 });
    draw_centered("Time's up!", 90, 44, DARKBLUE);
    int w = game_winner(g);
    if (w >= 0) {
        if (game_is_tie(g)) draw_centered("It's a tie!", 145, 32, DARKGRAY);
        else if (game_joined_count(g) > 1) draw_centered(TextFormat("Player %d wins!", w + 1), 145, 32, PLAYER_COLORS[w]);
        else draw_centered("Great popping!", 145, 32, PLAYER_COLORS[w]);
    }
    int y = 200;
    for (int p = 0; p < MAX_PLAYERS; p++) {
        if (!g->players[p].joined) continue;
        draw_centered(TextFormat("Player %d:  %d", p + 1, g->players[p].score), y, 30, PLAYER_COLORS[p]);
        y += 40;
    }
    if (g->mode_time >= RESULTS_LOCK_SECONDS && ((int)(g->mode_time * 2) % 2 == 0))
        draw_centered("Press A to play again", SCREEN_H - 110, 24, DARKGRAY);
}

static void draw(const Game *g, float t)
{
    draw_sky(t);
    for (int i = 0; i < MAX_BALLOONS; i++) if (g->balloons[i].alive) draw_balloon(&g->balloons[i], t);
    draw_effects(g);

    switch (g->mode) {
    case MODE_TITLE:
        DrawRectangleRounded((Rectangle){ 40, 105, SCREEN_W - 80, 250 }, 0.15f, 8, (Color){ 255, 255, 255, 200 });
        draw_centered("BALLOON POP", 120, 64, (Color){ 230, 41, 55, 255 });
        draw_centered("Point at the TV and press A to join!", 220, 24, DARKBLUE);
        draw_centered("Up to 4 players. Gold balloons are worth 5!", 255, 20, DARKGRAY);
        if ((int)(t * 2) % 2 == 0) draw_centered("Press A", 320, 32, DARKBLUE);
        break;
    case MODE_COUNTDOWN: {
        int n = (int)ceilf(COUNTDOWN_SECONDS - g->mode_time);
        draw_centered(n > 0 ? TextFormat("%d", n) : "GO!", 170, 120, DARKBLUE);
        int y = 320;
        for (int p = 0; p < MAX_PLAYERS; p++) if (g->players[p].joined) {
            draw_centered(TextFormat("Player %d is in!", p + 1), y, 22, PLAYER_COLORS[p]);
            y += 28;
        }
        draw_centered("Others: press A to join", SCREEN_H - 50, 20, DARKGRAY);
        break;
    }
    case MODE_PLAY:
        draw_hud(g);
        break;
    case MODE_RESULTS:
        draw_hud(g);
        draw_results(g);
        break;
    case MODE_DEMO:
        draw_hud(g);
        if ((int)(t * 2) % 2 == 0) draw_centered("DEMO - press A to play!", SCREEN_H - 70, 28, DARKBLUE);
        break;
    }

    for (int p = 0; p < MAX_PLAYERS; p++) {
        // pointers show whenever a remote is aimed; in a round only for players who joined
        if (g->mode == MODE_PLAY && !g->players[p].joined) continue;
        draw_cursor(p, &g->players[p]);
    }
}

// ---------------------------------------------------------------- log
// One line per event on stdout, which goes to Dolphin's log (OSREPORT) and to
// a USB Gecko on hardware. bb verify reads these.

static const char *mode_name(Mode m)
{
    switch (m) {
    case MODE_TITLE: return "title";
    case MODE_COUNTDOWN: return "countdown";
    case MODE_PLAY: return "play";
    case MODE_RESULTS: return "results";
    case MODE_DEMO: return "demo";
    }
    return "?";
}

static void log_events(const Game *g, Mode *last_mode, int last_score[MAX_PLAYERS])
{
    if (g->mode != *last_mode) {
        printf("balloons: mode %s\n", mode_name(g->mode));
        *last_mode = g->mode;
    }
    for (int p = 0; p < MAX_PLAYERS; p++) {
        if (g->players[p].score != last_score[p]) {
            if (g->players[p].score > last_score[p])
                printf("balloons: pop player=%d score=%d mode=%s\n", p + 1, g->players[p].score, mode_name(g->mode));
            last_score[p] = g->players[p].score;
        }
    }
}

int main(int argc, char *argv[])
{
    (void)argc; (void)argv;
    SYS_STDIO_Report(true);   // stdout -> Dolphin log / USB Gecko; raylib draws with the GPU
    InitWindow(SCREEN_W, SCREEN_H, "Balloon Pop");
    SetTargetFPS(60);
    input_init();

    Game g;
    game_init(&g, (uint32_t)gettime());
    printf("balloons: start\n");

    PlayerInput in[MAX_PLAYERS] = { 0 };
    Mode last_mode = g.mode;
    int last_score[MAX_PLAYERS] = { 0 };
    float t = 0;

    while (!WindowShouldClose()) {
        float dt = GetFrameTime();
        if (dt > 0.05f) dt = 0.05f;   // a long frame shouldn't teleport balloons
        t += dt;

        if (read_inputs(in)) break;    // HOME: back to the Homebrew Channel
        game_update(&g, in, dt);
        update_rumble(&g);
        log_events(&g, &last_mode, last_score);

        BeginDrawing();
        draw(&g, t);
        EndDrawing();
    }

    for (int p = 0; p < MAX_PLAYERS; p++) WPAD_Rumble(p, 0);
    printf("balloons: exit\n");
    CloseWindow();
    return 0;
}
