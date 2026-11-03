// Host-side tests for settings.c (bb test). argv[1] is a scratch file path.
#include "settings.h"
#include <math.h>
#include <stdio.h>
#include <string.h>

static int failures = 0, checks = 0;
#define CHECK(cond, msg) do { checks++; if (!(cond)) { failures++; \
    printf("FAIL %s:%d %s\n", __FILE__, __LINE__, msg); } } while (0)

int main(int argc, char *argv[])
{
    char p[128];
    CHECK(settings_path(p, sizeof p, "usb:/apps/starcatcher/boot.dol", "starcatcher")
          && strcmp(p, "usb:/apps/starcatcher/settings.txt") == 0, "next to the launched boot.dol");
    CHECK(settings_path(p, sizeof p, "sd:/apps/x/boot.dol", "x") && strcmp(p, "sd:/apps/x/settings.txt") == 0,
          "works for the SD card too");
    CHECK(settings_path(p, sizeof p, "app", "shakerace") && strcmp(p, "sd:/apps/shakerace/settings.txt") == 0,
          "no usable argv[0]: falls back to the SD card");
    CHECK(settings_path(p, sizeof p, NULL, "shakerace") && strcmp(p, "sd:/apps/shakerace/settings.txt") == 0,
          "NULL argv[0] falls back too");
    char tiny[8];
    CHECK(!settings_path(tiny, sizeof tiny, "usb:/apps/starcatcher/boot.dol", "s"), "too small a buffer says so");

    CHECK(settings_get("tilt_sign=1\n", "tilt_sign", -1) == 1, "reads a value");
    CHECK(settings_get("a=2\nshake_level=7\n", "shake_level", 3) == 7, "reads a later line");
    CHECK(settings_get("shake_level=7", "shake", 3) == 3, "a key that's only a prefix doesn't match");
    CHECK(settings_get("shake_level=oops\n", "shake_level", 3) == 3, "a bad number gives the fallback");
    CHECK(settings_get("", "shake_level", 3) == 3, "an empty file gives the fallback");

    const char *file = argc > 1 ? argv[1] : "test_settings.tmp";
    remove(file);
    CHECK(settings_load(file, "tilt_sign", -1) == -1, "a missing file gives the fallback");
    CHECK(settings_save(file, "tilt_sign", 1), "saves");
    CHECK(settings_load(file, "tilt_sign", -1) == 1, "and reads it back");
    CHECK(settings_save(file, "shake_level", 0.5f) && fabsf(settings_load(file, "shake_level", 3) - 0.5f) < 1e-6f,
          "a save replaces the file");
    remove(file);
    CHECK(!settings_save("/no/such/dir/settings.txt", "k", 1), "an unwritable path reports failure");

    printf("settings: %d checks, %d failed\n", checks, failures);
    return failures ? 1 : 0;
}
