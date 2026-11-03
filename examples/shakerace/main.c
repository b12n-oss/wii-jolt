// Shake Race: shake the Wiimote to make your runner go; first to the finish
// line wins. Up to four players. The rules live in game.c; this file is input,
// drawing and rumble.
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

#define TRACK_X0 50.0f         // start line
#define TRACK_X1 560.0f        // finish line
#define LANE_Y0 150.0f
#define LANE_H 70.0f

static void to_player_inputs(const Remote r[WIIMOTE_MAX], PlayerInput in[MAX_PLAYERS])
{
    for (int p = 0; p < MAX_PLAYERS; p++) {
        in[p].connected = r[p].connected;
        in[p].shake = r[p].shake;
        in[p].join = (r[p].pressed & (WPAD_BUTTON_A | WPAD_BUTTON_B)) != 0;
    }
}

// ---------------------------------------------------------------- drawing

static bool shown(const Game *g, int p)
{
    return g->mode == MODE_DEMO || (g->mode != MODE_TITLE && g->players[p].joined);
}

static float runner_x(const Player *pl)
{
    float x = TRACK_X0 + pl->dist / TRACK_METERS * (TRACK_X1 - TRACK_X0);
    return x > SCREEN_W - 30 ? SCREEN_W - 30 : x;
}

static void draw_track(void)
{
    DrawRectangleGradientV(0, 0, SCREEN_W, (int)LANE_Y0, (Color){ 120, 200, 255, 255 }, (Color){ 200, 235, 255, 255 });
    for (int i = 0; i < 7; i++)   // hills
        DrawCircle(i * 110 - 20, (int)LANE_Y0 + 20, 70, (Color){ 110, 190, 100, 255 });
    DrawRectangle(0, (int)LANE_Y0, SCREEN_W, SCREEN_H, (Color){ 200, 100, 70, 255 });
    for (int l = 0; l <= MAX_PLAYERS; l++)
        DrawRectangle(0, (int)(LANE_Y0 + l * LANE_H) - 1, SCREEN_W, 3, (Color){ 255, 255, 255, 200 });
    DrawRectangle((int)TRACK_X0 - 2, (int)LANE_Y0, 4, (int)(LANE_H * MAX_PLAYERS), WHITE);
    for (int y = 0; y < (int)(LANE_H * MAX_PLAYERS) / 10; y++)   // chequered finish
        for (int x = 0; x < 2; x++)
            DrawRectangle((int)TRACK_X1 + x * 10, (int)LANE_Y0 + y * 10, 10, 10, (x + y) % 2 ? BLACK : WHITE);
}

// A round little runner: body, head, ears, and legs that swing with distance.
static void draw_runner(int p, const Player *pl, float t)
{
    Color c = UI_PLAYER_COLORS[p];
    float x = runner_x(pl);
    float ground = LANE_Y0 + p * LANE_H + LANE_H - 12;
    float swing = sinf(pl->stride * 2.2f) * (pl->speed > 0.2f ? 9 : 0);
    float bob = fabsf(sinf(pl->stride * 2.2f)) * (pl->speed > 0.2f ? 5 : 0);
    if (pl->finished && pl->speed < 0.2f) bob = fabsf(sinf(t * 6)) * 8;   // a happy hop
    float by = ground - 22 - bob;
    DrawLineEx((Vector2){ x - 4, by + 10 }, (Vector2){ x - 4 + swing, ground }, 5, DARKGRAY);
    DrawLineEx((Vector2){ x + 4, by + 10 }, (Vector2){ x + 4 - swing, ground }, 5, DARKGRAY);
    DrawCircle(x, by, 15, c);
    DrawCircle(x + 12, by - 14, 10, c);
    DrawEllipse(x + 8, by - 28, 4, 9, c);     // ears
    DrawEllipse(x + 16, by - 27, 4, 9, c);
    DrawCircle(x + 16, by - 15, 2.5f, BLACK); // eye
    DrawText(TextFormat("%d", p + 1), (int)x - 5, (int)by - 9, 18, WHITE);
    if (pl->finished) {
        const char *pl_txt = pl->place == 1 ? "1st" : pl->place == 2 ? "2nd" : pl->place == 3 ? "3rd" : "4th";
        DrawText(pl_txt, (int)TRACK_X1 + 26, (int)(LANE_Y0 + p * LANE_H + 20), 26, WHITE);
    } else {
        // speed meter at the left of the lane
        float f = pl->speed / MAX_SPEED;
        DrawRectangle(6, (int)(LANE_Y0 + p * LANE_H + 10), 10, (int)(LANE_H - 20), (Color){ 0, 0, 0, 70 });
        int h = (int)((LANE_H - 20) * f);
        DrawRectangle(6, (int)(LANE_Y0 + p * LANE_H + LANE_H - 10) - h, 10, h, c);
    }
}

static const char *place_name(int n)
{
    return n == 1 ? "1st" : n == 2 ? "2nd" : n == 3 ? "3rd" : "4th";
}

static void draw_results(const Game *g)
{
    DrawRectangleRounded((Rectangle){ 80, 40, SCREEN_W - 160, SCREEN_H - 80 }, 0.1f, 8, (Color){ 255, 255, 255, 230 });
    int order[MAX_PLAYERS];
    int n = game_standings(g, order);
    if (n > 0) {
        const Player *w = &g->players[order[0]];
        if (!w->finished) ui_centered("Nobody made it this time!", SCREEN_W, 60, 32, DARKGRAY);
        else if (n > 1) ui_centered(TextFormat("Player %d wins!", order[0] + 1), SCREEN_W, 60, 44, UI_PLAYER_COLORS[order[0]]);
        else ui_centered("You made it!", SCREEN_W, 60, 44, UI_PLAYER_COLORS[order[0]]);
    }
    int y = 140, place = 0;
    for (int i = 0; i < n; i++) {
        int p = order[i];
        const Player *pl = &g->players[p];
        // runners who tie on distance share a place
        if (i == 0 || !(game_tied(g, p) && game_tied(g, order[i - 1]) && pl->dist == g->players[order[i - 1]].dist))
            place = i + 1;
        const char *line = pl->finished
            ? TextFormat("%s  Player %d   %.1f s", place_name(place), p + 1, pl->finish_time)
            : TextFormat("%s  Player %d   %d m", place_name(place), p + 1, (int)pl->dist);
        ui_centered(line, SCREEN_W, y, 30, UI_PLAYER_COLORS[p]);
        y += 44;
    }
    if (g->mode_time >= RESULTS_LOCK_SECONDS && ((int)(g->mode_time * 2) % 2 == 0))
        ui_centered("Press A for a rematch", SCREEN_W, SCREEN_H - 100, 24, DARKGRAY);
}

static void draw(const Game *g, float t)
{
    draw_track();
    for (int p = 0; p < MAX_PLAYERS; p++) if (shown(g, p)) draw_runner(p, &g->players[p], t);

    switch (g->mode) {
    case MODE_TITLE:
        DrawRectangleRounded((Rectangle){ 40, 30, SCREEN_W - 80, 300 }, 0.15f, 8, (Color){ 255, 255, 255, 220 });
        ui_centered("SHAKE RACE", SCREEN_W, 50, 64, (Color){ 230, 90, 20, 255 });
        ui_centered("Shake your Wiimote to make your runner go!", SCREEN_W, 140, 22, DARKBLUE);
        ui_centered("Stop shaking and you slow down.", SCREEN_W, 170, 22, DARKBLUE);
        ui_centered("Hold on tight and wear the wrist strap!", SCREEN_W, 210, 20, MAROON);
        if ((int)(t * 2) % 2 == 0) ui_centered("Press A to join", SCREEN_W, 260, 32, DARKBLUE);
        break;
    case MODE_COUNTDOWN: {
        int n = (int)ceilf(COUNTDOWN_SECONDS - g->mode_time);
        ui_centered(n > 0 ? TextFormat("%d", n) : "GO!", SCREEN_W, 10, 110, WHITE);
        ui_centered("Others: press A to join", SCREEN_W, 120, 20, DARKBLUE);
        break;
    }
    case MODE_RACE:
        if (g->mode_time < 0.8f) ui_centered("GO! SHAKE!", SCREEN_W, 40, 64, WHITE);
        else {
            int secs = (int)ceilf(RACE_MAX_SECONDS - g->mode_time);
            ui_centered(TextFormat("%d", secs), SCREEN_W, 20, 40, secs <= 10 ? RED : WHITE);
        }
        break;
    case MODE_RESULTS:
        draw_results(g);
        break;
    case MODE_DEMO:
        if ((int)(t * 2) % 2 == 0) ui_centered("DEMO - press A to play!", SCREEN_W, 50, 32, WHITE);
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
    case MODE_RACE: return "race";
    case MODE_RESULTS: return "results";
    case MODE_DEMO: return "demo";
    }
    return "?";
}

static void log_events(const Game *g, Mode *last_mode, bool last_finished[MAX_PLAYERS])
{
    for (int p = 0; p < MAX_PLAYERS; p++) {
        const Player *pl = &g->players[p];
        if (pl->finished && !last_finished[p])
            // logged before the mode change below, so a last finisher that
            // ends the race reports the mode it finished in
            printf("shakerace: finish player=%d place=%d time=%.1f mode=%s\n", p + 1, pl->place, pl->finish_time,
                   mode_name(*last_mode));
        last_finished[p] = pl->finished;
    }
    if (g->mode != *last_mode) {
        printf("shakerace: mode %s\n", mode_name(g->mode));
        *last_mode = g->mode;
    }
}

int main(int argc, char *argv[])
{
    (void)argc; (void)argv;
    SYS_STDIO_Report(true);
    InitWindow(SCREEN_W, SCREEN_H, "Shake Race");
    SetTargetFPS(60);
    wiimote_init(SCREEN_W, SCREEN_H);

    Game g;
    game_init(&g, (uint32_t)gettime());
    printf("shakerace: start\n");

    Remote remotes[WIIMOTE_MAX] = { 0 };
    PlayerInput in[MAX_PLAYERS] = { 0 };
    Mode last_mode = g.mode;
    bool last_finished[MAX_PLAYERS] = { 0 };
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
        log_events(&g, &last_mode, last_finished);

        BeginDrawing();
        draw(&g, t);
        EndDrawing();
    }

    wiimote_rumble_all_off();
    printf("shakerace: exit\n");
    CloseWindow();
    return 0;
}
