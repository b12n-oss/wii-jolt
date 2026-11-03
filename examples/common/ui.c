// See ui.h.
#include "ui.h"

const Color UI_PLAYER_COLORS[4] = {
    { 40, 120, 255, 255 },
    { 235, 50, 60, 255 },
    { 40, 180, 70, 255 },
    { 250, 190, 20, 255 },
};

void ui_centered(const char *text, int screen_w, int y, int size, Color c)
{
    int w = MeasureText(text, size);
    DrawText(text, screen_w / 2 - w / 2 + 3, y + 3, size, (Color){ 0, 0, 0, 90 });
    DrawText(text, screen_w / 2 - w / 2, y, size, c);
}
