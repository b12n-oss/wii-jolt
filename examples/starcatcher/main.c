// Star Catcher: hold the Wiimote sideways, tip it to slide your basket, catch
// falling stars. Up to four players. The rules live in game.c; this file is
// input, drawing and rumble.
#include "raylib.h"
#include "game.h"
#include "wiimote.h"
#include "ui.h"
#include <SDL2/SDL_main.h>     // SDL's main runs first and calls WPAD_Init (see wiimote.h)
#include <gccore.h>
#include <wiiuse/wpad.h>
#include <ogc/lwp_watchdog.h>  // gettime(), for the RNG seed
#include <stdio.h>
#include <math.h>

// ---------------------------------------------------------------- input
//
// Held sideways (D-pad on the left), tipping the remote like a steering wheel
// moves gravity along its long axis, the accelerometer's y. The sign comes
// from Dolphin: with "Sideways Wiimote" on, its "tilt right" reads gy = -1.
// Dolphin's emulation is what Wii games are tested against, but this hasn't
// been checked with a real remote yet. If the basket goes the wrong way on the
// console, flip TILT_SIGN.
#define TILT_SIGN (-1.0f)
#define TILT_FULL 0.5f        // gy at full steer: about 30 degrees of tip
#define TILT_DEADZONE 0.1f    // ignore small wobbles from holding it still

static float tilt_steer(float gy)
{
    float s = TILT_SIGN * gy;
    float m = fabsf(s);
    if (m < TILT_DEADZONE) return 0;
    float v = (m - TILT_DEADZONE) / (TILT_FULL - TILT_DEADZONE);
    if (v > 1) v = 1;
    return s < 0 ? -v : v;
}

static void to_player_inputs(const Remote r[WIIMOTE_MAX], PlayerInput in[MAX_PLAYERS])
{
    for (int p = 0; p < MAX_PLAYERS; p++) {
        in[p].connected = r[p].connected;
        in[p].join = (r[p].pressed & (WPAD_BUTTON_A | WPAD_BUTTON_1 | WPAD_BUTTON_2)) != 0;
        // D-pad: held sideways, "up" points left and "down" points right;
        // held upright, left and right do what they say.
        float pad = 0;
        if (r[p].held & (WPAD_BUTTON_UP | WPAD_BUTTON_LEFT)) pad -= 1;
        if (r[p].held & (WPAD_BUTTON_DOWN | WPAD_BUTTON_RIGHT)) pad += 1;
        in[p].steer = pad != 0 ? pad : tilt_steer(r[p].gy);
    }
}

// ---------------------------------------------------------------- drawing

static const Color STAR_COLORS[5] = {
    { 255, 236, 120, 255 }, { 255, 190, 240, 255 }, { 160, 230, 255, 255 },
    { 190, 255, 170, 255 }, { 255, 200, 140, 255 },
};

// A five-pointed star as ten triangles from the centre. Both windings are
// drawn, so the star shows whichever way the GL backend culls.
static void draw_star(float cx, float cy, float r, float rot, Color c)
{
    Vector2 pts[10];
    for (int i = 0; i < 10; i++) {
        float a = rot + i * 3.14159265f / 5 - 3.14159265f / 2;
        float rr = (i % 2 == 0) ? r : r * 0.45f;
        pts[i] = (Vector2){ cx + cosf(a) * rr, cy + sinf(a) * rr };
    }
    Vector2 ctr = { cx, cy };
    for (int i = 0; i < 10; i++) {
        Vector2 a = pts[i], b = pts[(i + 1) % 10];
        DrawTriangle(ctr, a, b, c);
        DrawTriangle(ctr, b, a, c);
    }
}

static void draw_sky(float t)
{
    DrawRectangleGradientV(0, 0, SCREEN_W, SCREEN_H, (Color){ 12, 16, 60, 255 }, (Color){ 60, 40, 120, 255 });
    for (int i = 0; i < 40; i++) {   // twinkling background stars, fixed spots
        float x = (float)((i * 97) % SCREEN_W), y = (float)((i * 53) % 380);
        unsigned char a = (unsigned char)(120 + 120 * sinf(t * 2 + i));
        DrawCircle(x, y, 1.5f, (Color){ 255, 255, 255, a });
    }
    DrawCircle(560, 70, 34, (Color){ 250, 245, 210, 255 });   // moon
    DrawCircle(574, 60, 30, (Color){ 19, 19, 69, 255 });   // sky colour at y=60, cuts the crescent
    DrawRectangle(0, (int)(BASKET_Y + BASKET_H), SCREEN_W, SCREEN_H, (Color){ 40, 90, 60, 255 });
}

static void draw_item(const Item *it, float t)
{
    switch (it->kind) {
    case ITEM_STAR:
        draw_star(it->x, it->y, it->r, it->spin, STAR_COLORS[it->color]);
        break;
    case ITEM_RAINBOW: {
        Color c = ColorFromHSV(fmodf(t * 360, 360), 0.8f, 1.0f);
        DrawCircle(it->x, it->y, it->r * 1.5f, (Color){ 255, 255, 255, 55 });
        draw_star(it->x, it->y, it->r, it->spin, c);
        break;
    }
    case ITEM_CLOUD: {
        Color grey = { 120, 125, 140, 255 };
        DrawCircle(it->x - 14, it->y + 4, 13, grey);
        DrawCircle(it->x + 14, it->y + 4, 13, grey);
        DrawCircle(it->x, it->y - 4, 17, grey);
        Color bolt = { 255, 230, 60, 255 };
        Vector2 a = { it->x - 2, it->y + 10 }, b = { it->x + 6, it->y + 10 }, c = { it->x - 4, it->y + 26 };
        DrawTriangle(a, c, b, bolt);
        DrawTriangle(a, b, c, bolt);
        break;
    }
    }
}

static void draw_basket(int p, const Player *pl, float t)
{
    Color c = UI_PLAYER_COLORS[p];
    float wob = pl->dizzy > 0 ? sinf(t * 25) * 6 : 0;
    Rectangle body = { pl->x - BASKET_W / 2 + wob, BASKET_Y, BASKET_W, BASKET_H };
    DrawRectangleRounded(body, 0.35f, 6, c);
    for (int i = 1; i < 4; i++)   // the weave
        DrawLine((int)(body.x + i * BASKET_W / 4), (int)BASKET_Y + 4, (int)(body.x + i * BASKET_W / 4),
                 (int)(BASKET_Y + BASKET_H - 4), (Color){ 255, 255, 255, 90 });
    DrawRectangle((int)body.x - 3, (int)BASKET_Y - 4, (int)BASKET_W + 6, 7, (Color){ 255, 255, 255, 200 });
    DrawText(TextFormat("P%d", p + 1), (int)(pl->x - 11 + wob), (int)BASKET_Y + 9, 18, WHITE);
    if (pl->dizzy > 0)
        for (int i = 0; i < 3; i++) {
            float a = t * 6 + i * 2.094f;
            draw_star(pl->x + cosf(a) * 26, BASKET_Y - 16 + sinf(a) * 6, 6, a, (Color){ 255, 240, 120, 255 });
        }
}

static void draw_hud(const Game *g)
{
    DrawRectangle(0, 0, SCREEN_W, 36, (Color){ 0, 0, 0, 110 });
    int x = 12;
    for (int p = 0; p < MAX_PLAYERS; p++) {
        if (g->mode != MODE_DEMO && !g->players[p].joined) continue;
        if (g->mode == MODE_DEMO && p >= 2) continue;
        DrawText(TextFormat("P%d %d", p + 1, g->players[p].score), x, 8, 24, UI_PLAYER_COLORS[p]);
        x += 120;
    }
    int secs = (int)ceilf(game_time_left(g));
    const char *tl = TextFormat("%d", secs);
    DrawText(tl, SCREEN_W - 16 - MeasureText(tl, 28), 5, 28, secs <= 10 ? RED : WHITE);
}

static void draw_effects(const Game *g, float t)
{
    for (int i = 0; i < MAX_PARTICLES; i++) {
        const Particle *pt = &g->particles[i];
        if (!pt->alive) continue;
        Color c = pt->color == -1 ? ColorFromHSV(fmodf(t * 360 + i * 30, 360), 0.8f, 1.0f)
                : pt->color == -2 ? (Color){ 150, 150, 165, 255 } : STAR_COLORS[pt->color];
        DrawCircle(pt->x, pt->y, 2 + pt->life * 3, c);
    }
    for (int i = 0; i < MAX_POPUPS; i++) {
        const Popup *pu = &g->popups[i];
        if (!pu->alive) continue;
        if (pu->points == 0) DrawText("dizzy!", pu->x - 28, pu->y - 20, 22, (Color){ 200, 200, 215, 255 });
        else DrawText(TextFormat("+%d", pu->points), pu->x - 10, pu->y - 20, pu->points > 1 ? 32 : 24,
                      UI_PLAYER_COLORS[pu->player]);
    }
}

static void draw_results(const Game *g)
{
    DrawRectangleRounded((Rectangle){ 80, 70, SCREEN_W - 160, SCREEN_H - 140 }, 0.1f, 8, (Color){ 255, 255, 255, 225 });
    ui_centered("Time's up!", SCREEN_W, 90, 44, DARKBLUE);
    int w = game_winner(g);
    if (w >= 0) {
        if (game_is_tie(g)) ui_centered("It's a tie!", SCREEN_W, 145, 32, DARKGRAY);
        else if (game_joined_count(g) > 1) ui_centered(TextFormat("Player %d wins!", w + 1), SCREEN_W, 145, 32, UI_PLAYER_COLORS[w]);
        else ui_centered("Great catching!", SCREEN_W, 145, 32, UI_PLAYER_COLORS[w]);
    }
    int y = 200;
    for (int p = 0; p < MAX_PLAYERS; p++) {
        if (!g->players[p].joined) continue;
        ui_centered(TextFormat("Player %d:  %d", p + 1, g->players[p].score), SCREEN_W, y, 30, UI_PLAYER_COLORS[p]);
        y += 40;
    }
    if (g->mode_time >= RESULTS_LOCK_SECONDS && ((int)(g->mode_time * 2) % 2 == 0))
        ui_centered("Press A to play again", SCREEN_W, SCREEN_H - 110, 24, DARKGRAY);
}

static void draw(const Game *g, float t)
{
    draw_sky(t);
    for (int i = 0; i < MAX_ITEMS; i++) if (g->items[i].alive) draw_item(&g->items[i], t);
    for (int p = 0; p < MAX_PLAYERS; p++) {
        bool show = g->mode == MODE_DEMO ? p < 2
                  : (g->mode == MODE_COUNTDOWN || g->mode == MODE_PLAY || g->mode == MODE_RESULTS) && g->players[p].joined;
        if (show) draw_basket(p, &g->players[p], t);
    }
    draw_effects(g, t);

    switch (g->mode) {
    case MODE_TITLE:
        DrawRectangleRounded((Rectangle){ 40, 95, SCREEN_W - 80, 270 }, 0.15f, 8, (Color){ 255, 255, 255, 215 });
        ui_centered("STAR CATCHER", SCREEN_W, 110, 60, (Color){ 120, 60, 200, 255 });
        ui_centered("Hold your Wiimote sideways and tip it", SCREEN_W, 195, 22, DARKBLUE);
        ui_centered("like a steering wheel to slide your basket.", SCREEN_W, 222, 22, DARKBLUE);
        ui_centered("Rainbow stars are worth 5. Dodge the storm clouds!", SCREEN_W, 260, 18, DARKGRAY);
        if ((int)(t * 2) % 2 == 0) ui_centered("Press 2 (or A) to join", SCREEN_W, 310, 30, DARKBLUE);
        break;
    case MODE_COUNTDOWN: {
        int n = (int)ceilf(COUNTDOWN_SECONDS - g->mode_time);
        ui_centered(n > 0 ? TextFormat("%d", n) : "GO!", SCREEN_W, 120, 120, WHITE);
        int y = 260;
        for (int p = 0; p < MAX_PLAYERS; p++) if (g->players[p].joined) {
            ui_centered(TextFormat("Player %d is in! Try tipping.", p + 1), SCREEN_W, y, 22, UI_PLAYER_COLORS[p]);
            y += 28;
        }
        ui_centered("Others: press 2 to join", SCREEN_W, 380, 20, LIGHTGRAY);
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
        if ((int)(t * 2) % 2 == 0) ui_centered("DEMO - press 2 to play!", SCREEN_W, 200, 28, WHITE);
        break;
    }
}

// ---------------------------------------------------------------- log
// One line per event on stdout (Dolphin's log, or a USB Gecko). bb verify
// reads these.

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
        printf("starcatcher: mode %s\n", mode_name(g->mode));
        *last_mode = g->mode;
    }
    for (int p = 0; p < MAX_PLAYERS; p++) {
        if (g->players[p].score > last_score[p])
            printf("starcatcher: catch player=%d score=%d mode=%s\n", p + 1, g->players[p].score, mode_name(g->mode));
        last_score[p] = g->players[p].score;
    }
}

int main(int argc, char *argv[])
{
    (void)argc; (void)argv;
    SYS_STDIO_Report(true);
    InitWindow(SCREEN_W, SCREEN_H, "Star Catcher");
    SetTargetFPS(60);
    wiimote_init(SCREEN_W, SCREEN_H);

    Game g;
    game_init(&g, (uint32_t)gettime());
    printf("starcatcher: start\n");

    Remote remotes[WIIMOTE_MAX] = { 0 };
    PlayerInput in[MAX_PLAYERS] = { 0 };
    Mode last_mode = g.mode;
    int last_score[MAX_PLAYERS] = { 0 };
    float t = 0;

    while (!WindowShouldClose()) {
        float dt = GetFrameTime();
        if (dt > 0.05f) dt = 0.05f;
        t += dt;

        if (wiimote_read(remotes)) break;   // HOME: back to the Homebrew Channel
        to_player_inputs(remotes, in);
        game_update(&g, in, dt);
        for (int p = 0; p < MAX_PLAYERS; p++)
            wiimote_rumble(p, g.players[p].rumble > 0 && g.mode != MODE_DEMO);
        log_events(&g, &last_mode, last_score);

        BeginDrawing();
        draw(&g, t);
        EndDrawing();
    }

    wiimote_rumble_all_off();
    printf("starcatcher: exit\n");
    CloseWindow();
    return 0;
}
