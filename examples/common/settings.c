// See settings.h.
#include "settings.h"
#include <errno.h>
#include <math.h>
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
    char old[4096];
    size_t got = 0;
    FILE *in = fopen(path, "r");
    if (in) {
        got = fread(old, 1, sizeof old - 1, in);
        fclose(in);
    } else if (errno != ENOENT) {
        return false;   // unreadable is not absent: do not truncate what we cannot read
    }
    old[got] = '\0';

    FILE *f = fopen(path, "w");
    if (!f) return false;
    size_t klen = strlen(key);
    bool ok = true, written = false;
    // %g keeps 6 significant digits, so an integer from 1000000 up would be
    // saved as 1.23457e+06. Whole values print exactly.
    #define PUT_KEY() (value == floorf(value) ? fprintf(f, "%s=%.0f\n", key, value) \
                                              : fprintf(f, "%s=%g\n", key, value))
    for (const char *line = old; *line; ) {
        const char *nl = strchr(line, '\n');
        size_t len = nl ? (size_t)(nl - line) : strlen(line);
        const char *eq = memchr(line, '=', len);
        if (eq && (size_t)(eq - line) == klen && strncmp(line, key, klen) == 0) {
            if (!written) ok = PUT_KEY() > 0 && ok;
            written = true;
        } else if (len > 0) {
            ok = (fwrite(line, 1, len, f) == len && fputc('\n', f) != EOF) && ok;
        }
        line += len + (nl ? 1 : 0);
    }
    if (!written) ok = PUT_KEY() > 0 && ok;
    #undef PUT_KEY
    return fclose(f) == 0 && ok;
}
