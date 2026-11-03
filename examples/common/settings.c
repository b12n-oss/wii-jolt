// See settings.h.
#include "settings.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

bool settings_path(char *out, size_t n, const char *argv0, const char *app)
{
    const char *slash = argv0 ? strrchr(argv0, '/') : NULL;
    int len;
    if (argv0 && strstr(argv0, ":/") && slash)
        len = snprintf(out, n, "%.*ssettings.txt", (int)(slash - argv0 + 1), argv0);
    else
        len = snprintf(out, n, "sd:/apps/%s/settings.txt", app);
    return len >= 0 && (size_t)len < n;
}

float settings_get(const char *text, const char *key, float fallback)
{
    size_t klen = strlen(key);
    for (const char *line = text; line && *line; ) {
        if (strncmp(line, key, klen) == 0 && line[klen] == '=') {
            char *end;
            float v = strtof(line + klen + 1, &end);
            if (end != line + klen + 1) return v;
        }
        line = strchr(line, '\n');
        if (line) line++;
    }
    return fallback;
}

float settings_load(const char *path, const char *key, float fallback)
{
    FILE *f = fopen(path, "r");
    if (!f) return fallback;
    char buf[512];
    size_t got = fread(buf, 1, sizeof buf - 1, f);
    fclose(f);
    buf[got] = '\0';
    return settings_get(buf, key, fallback);
}

bool settings_save(const char *path, const char *key, float value)
{
    FILE *f = fopen(path, "w");
    if (!f) return false;
    bool ok = fprintf(f, "%s=%g\n", key, value) > 0;
    return fclose(f) == 0 && ok;
}
