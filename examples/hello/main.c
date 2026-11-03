// Hello Wii: stock raylib 6.0 drawing on a Wii through SDL2 + OpenGX.
// A ball bounces around, and the screen reports what raylib sees of the
// Wiimote so we can tell whether input reaches raylib's gamepad API.
#include "raylib.h"
// SDL's Wii port runs its own main() first (SDL_wii_main.c): it calls
// WPAD_Init, hooks the power/reset buttons and mounts the SD card, then calls
// ours as SDL_main. Including SDL_main.h does that renaming. Without it the
// Wiimotes are never initialised (WPAD_Probe answers WPAD_ERR_NO_CONTROLLER).
#include <SDL2/SDL_main.h>
#include <stdio.h>
#include <ogc/system.h>

int main(int argc, char *argv[])
{
    (void)argc; (void)argv;
    SYS_STDIO_Report(true);   // stdout/stderr -> Dolphin log / USB Gecko
    printf("hello: start\n");
    InitWindow(640, 480, "hello wii");
    SetTargetFPS(60);

    Vector2 pos = { 320, 240 };
    Vector2 vel = { 4, 3 };
    const float r = 30;
    int frames = 0;
    Color colors[] = { RED, ORANGE, GOLD, LIME, SKYBLUE, VIOLET };
    int ci = 0;

    while (!WindowShouldClose())
    {
        pos.x += vel.x;
        pos.y += vel.y;
        if (pos.x < r || pos.x > 640 - r) { vel.x = -vel.x; ci = (ci + 1) % 6; }
        if (pos.y < r || pos.y > 480 - r) { vel.y = -vel.y; ci = (ci + 1) % 6; }

        // Any gamepad button changes the colour, so the kids can poke it.
        if (IsGamepadAvailable(0) && GetGamepadButtonPressed() != 0) ci = (ci + 1) % 6;

        BeginDrawing();
        ClearBackground(DARKBLUE);
        DrawText("Hello from raylib on the Wii!", 110, 40, 30, RAYWHITE);
        DrawCircleV(pos, r, colors[ci]);
        DrawRectangleLines(10, 10, 620, 460, RAYWHITE);
        DrawText(TextFormat("frame %d   fps %d", frames, GetFPS()), 20, 430, 20, LIGHTGRAY);
        DrawText(TextFormat("gamepad0: %s  %s", IsGamepadAvailable(0) ? "yes" : "no",
                            IsGamepadAvailable(0) ? GetGamepadName(0) : ""),
                 20, 400, 20, LIGHTGRAY);
        EndDrawing();
        frames++;
    }

    CloseWindow();
    return 0;
}
