// A tiny settings file for the games: "key=value" lines in settings.txt, kept
// next to the game's boot.dol, so a setting changed on the couch survives the
// next launch. Plain C and stdio only, so it's tested on the Mac too
// (test/test_settings.c).
#ifndef WII_JOLT_SETTINGS_H
#define WII_JOLT_SETTINGS_H

#include <stdbool.h>
#include <stddef.h>

// Where settings.txt goes. The Homebrew Channel launches a game with argv[0]
// set to its path, such as "usb:/apps/starcatcher/boot.dol", and the file goes
// in that folder. Without a usable path (Dolphin passes "app"), it falls back
// to "sd:/apps/<app>/settings.txt". Returns false if `out` is too small.
bool settings_path(char *out, size_t n, const char *argv0, const char *app);

// The number stored under `key` in `text`, or `fallback` if it isn't there or
// doesn't parse.
float settings_get(const char *text, const char *key, float fallback);

// Read the file at `path` and return the value under `key`, or `fallback` if
// the file or the key is missing.
float settings_load(const char *path, const char *key, float fallback);

// Set `key` in the file, keeping the other keys: the key's line is replaced in
// place, or added at the end, and the file is created if it is missing. Lines
// that aren't `key=value` are kept as they are. Returns false if it couldn't be
// written (no SD card, read-only, or the folder isn't there).
bool settings_save(const char *path, const char *key, float value);

#endif
