// Drawing helpers the games share: player colours and centred, shadowed text.
#ifndef WII_JOLT_UI_H
#define WII_JOLT_UI_H

#include "raylib.h"

// P1 blue, P2 red, P3 green, P4 yellow: the order the Wii's player LEDs use.
extern const Color UI_PLAYER_COLORS[4];

// Text centred on the screen's width at y, with a soft drop shadow.
void ui_centered(const char *text, int screen_w, int y, int size, Color c);

#endif
