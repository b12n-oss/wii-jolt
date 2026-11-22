// See jolt_gfx.h. Chez finds these functions by name through its static
// foreign-symbol table. There is no dlsym on the Wii, so they are registered
// with Sforeign_symbol from inside Sbuild_heap's init callback (the pack links
// with -Wl,--wrap=Sbuild_heap).
#include "jolt_gfx.h"
#include "jolt_snd.h"
#include "settings.h"
#include "raylib.h"
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <gccore.h>
#include <ogc/lwp_watchdog.h>
#include <wiiuse/wpad.h>

#define NCHAN 4

// -DJG_STUB_DRAW (scripts/make-gfx-pack --stub-draw): every drawing function
// returns at once, so a frame's draw time is the Clojure side plus the FFI
// crossing, with no raylib work. Measurement builds only.
#ifdef JG_STUB_DRAW
#define JG_STUB() return 0
#else
#define JG_STUB() ((void)0)
#endif

static bool rumbling[NCHAN];

static Color rgba(U c)
{
    return (Color){ (unsigned char)(c >> 24), (unsigned char)(c >> 16),
                    (unsigned char)(c >> 8), (unsigned char)c };
}

static unsigned calls;

static bool chan_ok(U ch) { return (int)ch >= 0 && (int)ch < NCHAN; }

static void heap_report(void);
static const char *settings_file_path(void);

// --- console evidence ------------------------------------------------------
// A real Wii has no OSREPORT, so a run has to bring its own evidence back:
//  - a stamp drawn over every frame (build id, the interpreter's unaligned
//    double load/store counts, the frame number), so a photo of the TV says
//    which build ran and how far it got. settings.txt's `debug_hud=0` hides it.
//  - run.log beside boot.dol on the card: the build id, the heap report, a
//    line every 300 frames and one at exit, each fsynced so a crash keeps
//    the last one. Nothing is written when there is no card to write to.
// The build id comes from jolt build:jolt-gfx (-DJG_BUILD_ID=<git sha>).
#ifndef JG_BUILD_ID
#define JG_BUILD_ID dev
#endif
#define JG_STR2(x) #x
#define JG_STR(x) JG_STR2(x)
static const char build_id[] = JG_STR(JG_BUILD_ID);

extern unsigned pb_unaligned_fp_loads, pb_unaligned_fp_stores;   // the kernel's (c/pb.c)
static unsigned frames;
static bool hud = true;
static FILE *run_log;

// The card first, then stderr: libogc's UART write rewrites every '\n' to
// '\r' in the buffer it is handed, and newlib's vfprintf hands it the format
// string's own text, so a format written to stderr first reaches the log
// with its newlines gone (seen: a run.log of one long line).
static void jg_log(const char *fmt, ...)
{
    char line[256];
    va_list ap;
    va_start(ap, fmt); vsnprintf(line, sizeof line, fmt, ap); va_end(ap);
    if (run_log) {
        fputs(line, run_log);
        fflush(run_log);
        fsync(fileno(run_log));
    }
    fputs(line, stderr);
}

static void run_log_open(void)
{
    const char *settings = settings_file_path();
    size_t n = strlen(settings), tail = strlen("settings.txt");
    char path[256];
    if (n < tail || n >= sizeof path) return;
    memcpy(path, settings, n - tail);
    strcpy(path + n - tail, "run.log");
    run_log = fopen(path, "w");
    jg_log("jolt_gfx: build %s, run log %s\n", build_id, run_log ? path : "(not writable)");
}

static void hud_draw(void)
{
    char s[96];
    snprintf(s, sizeof s, "jolt %s  pb %u/%u  f%u", build_id,
             pb_unaligned_fp_loads, pb_unaligned_fp_stores, frames);
    int y = GetScreenHeight() - 36;   // above a TV's overscan crop
    DrawText(s, 9, y + 1, 10, BLACK);
    DrawText(s, 8, y, 10, RAYWHITE);
}

U jg_init(U w, U h)
{
    calls++;
    run_log_open();
    heap_report();
    hud = settings_load(settings_file_path(), "debug_hud", 1.0f) != 0.0f;
    InitWindow((int)w, (int)h, "jolt");
    SetTargetFPS(60);
    WPAD_SetDataFormat(WPAD_CHAN_ALL, WPAD_FMT_BTNS_ACC_IR);
    WPAD_SetVRes(WPAD_CHAN_ALL, (int)w, (int)h);
    return 0;
}
U jg_should_close(void) { return WindowShouldClose(); }
U jg_close(void)
{
    calls++;
    jg_log("jolt_gfx: exit at frame %u, pb unaligned double loads %u stores %u\n",
           frames, pb_unaligned_fp_loads, pb_unaligned_fp_stores);
    if (run_log) { fclose(run_log); run_log = NULL; }
    CloseWindow();
    return 0;
}
U jg_begin(U clear) { calls++; BeginDrawing(); ClearBackground(rgba(clear)); return 0; }
U jg_end(void)
{
    calls++;
    frames++;
#ifndef JG_STUB_DRAW
    if (hud) hud_draw();
#endif
    if (run_log && frames % 300 == 0)
        jg_log("jolt_gfx: frame %u, pb unaligned double loads %u stores %u\n",
               frames, pb_unaligned_fp_loads, pb_unaligned_fp_stores);
    EndDrawing();
    return 0;
}
U jg_frame_us(void) { return (U)(GetFrameTime() * 1000000.0f); }
U jg_time_us(void) { return (U)(ticks_to_microsecs(gettime()) & 0x0FFFFFFF); }
U jg_seed(void) { calls++; return (U)(gettime() & 0x0FFFFFFF); }

U jg_gradient_v(U x, U y, U w, U h, U top, U bottom)
{
    calls++;
    JG_STUB();
    DrawRectangleGradientV((int)x, (int)y, (int)w, (int)h, rgba(top), rgba(bottom));
    return 0;
}
U jg_rect(U x, U y, U w, U h, U c)
{
    calls++;
    JG_STUB();
    DrawRectangle((int)x, (int)y, (int)w, (int)h, rgba(c));
    return 0;
}
U jg_rect_rounded(U x, U y, U w, U h, U roundness, U c)
{
    calls++;
    JG_STUB();
    Rectangle r = { (float)(int)x, (float)(int)y, (float)(int)w, (float)(int)h };
    DrawRectangleRounded(r, (float)(int)roundness / 100.0f, 8, rgba(c));
    return 0;
}
U jg_circle(U x, U y, U r, U c)
{
    calls++;
    JG_STUB();
    DrawCircle((int)x, (int)y, (float)(int)r, rgba(c));
    return 0;
}
U jg_ellipse(U x, U y, U rx, U ry, U c)
{
    calls++;
    JG_STUB();
    DrawEllipse((int)x, (int)y, (float)(int)rx, (float)(int)ry, rgba(c));
    return 0;
}
U jg_triangle(U x1, U y1, U x2, U y2, U x3, U y3, U c)
{
    calls++;
    JG_STUB();
    DrawTriangle((Vector2){ (float)(int)x1, (float)(int)y1 },
                 (Vector2){ (float)(int)x2, (float)(int)y2 },
                 (Vector2){ (float)(int)x3, (float)(int)y3 }, rgba(c));
    return 0;
}
U jg_line(U x1, U y1, U x2, U y2, U c)
{
    calls++;
    JG_STUB();
    DrawLine((int)x1, (int)y1, (int)x2, (int)y2, rgba(c));
    return 0;
}
U jg_line_thick(U x1, U y1, U x2, U y2, U thick, U c)
{
    calls++;
    JG_STUB();
    DrawLineEx((Vector2){ (float)(int)x1, (float)(int)y1 },
               (Vector2){ (float)(int)x2, (float)(int)y2 }, (float)(int)thick, rgba(c));
    return 0;
}
U jg_ring(U x, U y, U inner, U outer, U c)
{
    calls++;
    JG_STUB();
    DrawRing((Vector2){ (float)(int)x, (float)(int)y }, (float)(int)inner,
             (float)(int)outer, 0.0f, 360.0f, 24, rgba(c));
    return 0;
}
U jg_poly(U x, U y, U sides, U radius, U rot, U c)
{
    calls++;
    JG_STUB();
    DrawPoly((Vector2){ (float)(int)x, (float)(int)y }, (int)sides,
             (float)(int)radius, (float)(int)rot, rgba(c));
    return 0;
}
// --- sprite sheets ---
#include "mona_assets.h"

static Texture2D sheets[3];
static int cell_w[3] = {8, 16, 8};
static float scale = 1.0f;

U jg_scale(U n)
{
    calls++;
    scale = (float)(int)n;
    return 0;
}

// Sheet id 0 tiles (8x8 cells), 1 players (16x16), 2 mobs (8x8). Returns 0 on
// success, 1 when the image or texture failed to load.
U jg_sheet_load(U id)
{
    calls++;
    static const unsigned char *const data[3] = { mona_tiles_png, mona_players_png, mona_mobs_png };
    static const unsigned int *const len[3] = { &mona_tiles_png_len, &mona_players_png_len, &mona_mobs_png_len };
    if (id > 2) return 1;
    Image img = LoadImageFromMemory(".png", data[id], (int)*len[id]);
    Texture2D tex = LoadTextureFromImage(img);
    UnloadImage(img);
    if (tex.id == 0) return 1;
    SetTextureFilter(tex, TEXTURE_FILTER_POINT);
    sheets[id] = tex;
    return 0;
}

// Draw cell (col, row) of a sheet at game pixel (x, y), scaled by jg_scale.
// flags bit 0 flips x, bit 1 flips y, both in place.
static void draw_cell(U sheet, int col, int row, int x, int y, U flags)
{
    float cw = (float)cell_w[sheet];
    Rectangle src = { (float)col * cw, (float)row * cw,
                      (flags & 1) ? -cw : cw, (flags & 2) ? -cw : cw };
    Rectangle dst = { (float)x * scale, (float)y * scale, cw * scale, cw * scale };
    DrawTexturePro(sheets[sheet], src, dst, (Vector2){ 0, 0 }, 0.0f, WHITE);
}

U jg_sprite(U sheet, U col, U row, U x, U y, U flags)
{
    calls++;
    JG_STUB();
    if (sheet > 2) return 1;
    draw_cell(sheet, (int)col, (int)row, (int)x, (int)y, flags);
    return 0;
}

// --- tile layers ---
// Two 15-row layers, column-major per row: index = ty * tile_width + tx. The
// decor layer draws first, then the tile layer. Ids are cells of sheet 0 at
// (id % 16, id / 16); 0 is empty and 11 is the flagpole, which the minimap
// does not treat as solid.
static unsigned char tile_layer[190 * 15], decor_layer[190 * 15];
static int tile_width;

U jg_tiles_load(U tiles_ptr, U decor_ptr, U width)
{
    calls++;
    if (!tiles_ptr || !decor_ptr || width == 0 || width > 190) return 1;
    tile_width = (int)width;
    memcpy(tile_layer, (const void *)tiles_ptr, (size_t)width * 15);
    memcpy(decor_layer, (const void *)decor_ptr, (size_t)width * 15);
    return 0;
}

// The per-cell loading path: jg_tiles_begin clears both layers and sets the
// width, then the caller sets only the non-empty cells with jg_tile_set and
// jg_decor_set.
U jg_tiles_begin(U width)
{
    calls++;
    if (width == 0 || width > 190) return 1;
    tile_width = (int)width;
    memset(tile_layer, 0, sizeof tile_layer);
    memset(decor_layer, 0, sizeof decor_layer);
    return 0;
}

U jg_decor_set(U index, U id)
{
    calls++;
    if ((int)index < 0 || (int)index >= tile_width * 15) return 1;
    decor_layer[(int)index] = (unsigned char)id;
    return 0;
}

U jg_tile_set(U index, U tile)
{
    calls++;
    if ((int)index < 0 || (int)index >= tile_width * 15) return 1;
    tile_layer[(int)index] = (unsigned char)tile;
    return 0;
}

U jg_tiles_draw(U cam_x)
{
    calls++;
    JG_STUB();
    int cx = (int)cam_x;
    int first = (cx >= 0 ? cx / 8 : -((-cx + 7) / 8)) - 1;
    int last = first + 22;
    if (first < 0) first = 0;
    if (last > tile_width - 1) last = tile_width - 1;
    for (int ty = 0; ty < 15; ty++)
        for (int tx = first; tx <= last; tx++) {
            int i = ty * tile_width + tx;
            int layers[2] = { decor_layer[i], tile_layer[i] };
            for (int l = 0; l < 2; l++)
                if (layers[l])
                    draw_cell(0, layers[l] % 16, layers[l] / 16, tx * 8 - cx, ty * 8, 0);
        }
    return 0;
}

// The whole stage as a box in screen pixels: sky, then a rectangle per solid
// cell (tile id non-zero and not 11).
U jg_tiles_map(U x, U y, U w, U h, U solid, U sky)
{
    calls++;
    JG_STUB();
    int bx = (int)x, by = (int)y, bw = (int)w, bh = (int)h;
    DrawRectangle(bx, by, bw, bh, rgba(sky));
    if (tile_width == 0) return 0;
    for (int ty = 0; ty < 15; ty++)
        for (int tx = 0; tx < tile_width; tx++) {
            int id = tile_layer[ty * tile_width + tx];
            if (id == 0 || id == 11) continue;
            int x0 = bx + tx * bw / tile_width, x1 = bx + (tx + 1) * bw / tile_width;
            int y0 = by + ty * bh / 15, y1 = by + (ty + 1) * bh / 15;
            DrawRectangle(x0, y0, x1 - x0 > 0 ? x1 - x0 : 1, y1 - y0 > 0 ? y1 - y0 : 1, rgba(solid));
        }
    return 0;
}

// Calls to counted jg_* functions since the last jg_calls; each counted
// function adds exactly 1. Masked to stay a fixnum.
U jg_calls(void)
{
    unsigned n = calls & 0x0FFFFFFF;
    calls = 0;
    return (U)n;
}

// Counts one call for the sound exports in jolt_snd.c, whose counter is this one.
void jg_count(void) { calls++; }

U jg_text(U s, U x, U y, U size, U c)
{
    calls++;
    JG_STUB();
    DrawText((const char *)s, (int)x, (int)y, (int)size, rgba(c));
    return 0;
}
U jg_text_width(U s, U size)
{
    calls++;
    JG_STUB();
    return (U)MeasureText((const char *)s, (int)size);
}

// --- settings ---
// An integer setting in settings.txt beside boot.dol (examples/common/settings.c).
// The path is worked out once, on first use.
static char settings_file[256];
static bool settings_ready;

static const char *settings_file_path(void)
{
    if (!settings_ready) {
        const char *argv0 = (__system_argv && __system_argv->argc > 0) ? __system_argv->argv[0] : NULL;
        if (!settings_path(settings_file, sizeof settings_file, argv0, "jolt-mona"))
            settings_file[0] = 0;
        settings_ready = true;
    }
    return settings_file;
}

U jg_setting_get(U key_ptr, U fallback)
{
    calls++;
    const char *path = settings_file_path();
    if (!key_ptr || !path[0]) return fallback;
    return (U)(int)settings_load(path, (const char *)key_ptr, (float)(int)fallback);
}
U jg_setting_put(U key_ptr, U value)
{
    calls++;
    const char *path = settings_file_path();
    if (!key_ptr || !path[0]) return 0;
    return settings_save(path, (const char *)key_ptr, (float)(int)value) ? 1 : 0;
}

// Wiimote channel ch. Each call probes the channel itself.
static bool connected(U ch)
{
    u32 t;
    return chan_ok(ch) && WPAD_Probe((int)ch, &t) == WPAD_ERR_NONE;
}
static bool ir_valid(U ch) { return connected(ch) && WPAD_Data((int)ch)->ir.valid; }
U jg_connected(U ch) { calls++; return connected(ch); }
U jg_held(U ch) { calls++; return connected(ch) ? (U)(WPAD_Data((int)ch)->btns_h & 0xFFFF) : 0; }
U jg_ir_valid(U ch) { calls++; return ir_valid(ch); }

// An IR coordinate as the biased, fixnum-safe value Clojure gets (see JG_IR_BIAS).
static U ir_biased(float v)
{
    long n = (long)(int)v + JG_IR_BIAS;
    if (n < 0) n = 0;
    if (n > 0x0FFFFFFF) n = 0x0FFFFFFF;
    return (U)n;
}
U jg_ir_x(U ch) { calls++; return ir_valid(ch) ? ir_biased(WPAD_Data((int)ch)->ir.x) : 0; }
U jg_ir_y(U ch) { calls++; return ir_valid(ch) ? ir_biased(WPAD_Data((int)ch)->ir.y) : 0; }

// Like wiimote_rumble in examples/common: only talk to WPAD on a change.
U jg_rumble(U ch, U on)
{
    calls++;
    if (!chan_ok(ch) || rumbling[ch] == (on != 0)) return 0;
    WPAD_Rumble((int)ch, on != 0);
    rumbling[ch] = on != 0;
    return 0;
}
U jg_rumble_all_off(void)
{
    calls++;
    for (int c = 0; c < NCHAN; c++) { WPAD_Rumble(c, 0); rumbling[c] = false; }
    return 0;
}

// stdout/stderr to Dolphin's log (OSREPORT) or a USB Gecko, before jolt's main
// runs, as the C games do with SYS_STDIO_Report(true). raylib owns the screen.
__attribute__((constructor))
static void jg_stdio(void)
{
    SYS_STDIO_Report(true);
    // jolt sizes its allocation window (the "nursery") at 16MB. The runtime
    // holds about 41MB live on this 32-bit target in a heap of about 60MB
    // (see the fallback heap below), so a 16MB window runs it out of memory
    // before the first collection. Pin it small; jolt reads this at startup.
    setenv("JOLT_GC_TRIP_BYTES", "2097152", 0);
#ifdef JG_GC_LOG
    // Measurement build: jolt prints one line per collection on stderr.
    // Build with: scripts/dkp make -C jolt-gfx EXTRA_CFLAGS=-DJG_GC_LOG
    setenv("JOLT_GC_LOG", "1", 0);
#endif
}

// --- registration ---------------------------------------------------------
void Sforeign_symbol(const char *s, void *v);
void __real_Sbuild_heap(const char *exe, void (*init)(void));
static void (*jolt_init)(void);

static void register_all(void)
{
    if (jolt_init) jolt_init();
#define R(f) Sforeign_symbol(#f, (void *)f)
    R(jg_init); R(jg_should_close); R(jg_close); R(jg_begin); R(jg_end);
    R(jg_frame_us); R(jg_time_us); R(jg_seed);
    R(jg_gradient_v); R(jg_rect); R(jg_rect_rounded); R(jg_circle); R(jg_ellipse);
    R(jg_triangle); R(jg_line); R(jg_line_thick); R(jg_ring); R(jg_poly);
    R(jg_scale); R(jg_sheet_load); R(jg_sprite);
    R(jg_tiles_load); R(jg_tiles_begin); R(jg_decor_set); R(jg_tile_set); R(jg_tiles_draw); R(jg_tiles_map); R(jg_calls);
    R(jg_text); R(jg_text_width);
    R(jg_setting_get); R(jg_setting_put);
    R(jg_connected); R(jg_held); R(jg_ir_valid); R(jg_ir_x); R(jg_ir_y);
    R(jg_rumble); R(jg_rumble_all_off);
    jg_snd_register();
#undef R
}

void __wrap_Sbuild_heap(const char *exe, void (*init)(void))
{
    jolt_init = init;
    __real_Sbuild_heap(exe, register_all);
}

// --- the heap: a fallback over the stranded arenas, and a report ------------
// A jolt program's runtime holds about 41MB live on this target (58MB at the
// boot peak, while collections copy the runtime's data) in a heap of about
// 60MB: libogc's sbrk serves malloc from MEM1's arena (7MB after a 14.5MB
// .dol loads) and then, once a request does not fit there, from MEM2's
// 52MB. It never returns to MEM1, so what MEM1 had left (3.8MB on the game)
// is stranded, and a build 100KB bigger than the game died at boot with
// "out of memory" while 5MB sat unreachable. The pack links with
// --wrap=malloc/calloc/realloc/free (scripts/make-gfx-pack): a request goes
// to the real heap first and, when that fails, to lwp heaps carved from what
// both arenas still hold (MEM1 minus a reserve for the video framebuffers,
// which SDL takes from that arena in InitWindow, after the boot). Blocks
// served this way carry a 32-byte size header so free and realloc route by
// address. Not locked: the game runs Chez single-threaded. jg_init prints
// the heap's state to Dolphin's log ("jolt_gfx: ..."), which is how the
// numbers above were taken.
#include <ogc/lwp_heap.h>
#include <malloc.h>
#include <stdio.h>
#include <string.h>
#include "scheme.h"

#define FB_ARENA1_RESERVE (1536u * 1024)   // two 640x480 XFBs and change
#define FB_HDR 32

void *__real_malloc(size_t n);
void *__real_calloc(size_t a, size_t b);
void *__real_realloc(void *p, size_t n);
void __real_free(void *p);

static heap_cntrl fb_heap[2];
static u8 *fb_lo[2], *fb_hi[2];
static int fb_tried;
static size_t heap_live, heap_big;
static unsigned heap_calls, heap_fails;

static void fb_init_one(int i, void *lo_v, void *hi_v, u32 reserve, void (*set_lo)(void *))
{
    u8 *lo = (u8 *)(((u32)lo_v + 31) & ~31u), *hi = (u8 *)hi_v;
    if (hi <= lo || (u32)(hi - lo) <= reserve + 65536) return;
    u32 size = ((u32)(hi - lo) - reserve) & ~31u;
    set_lo(lo + size);
    if (!__lwp_heap_init(&fb_heap[i], lo, size, 32)) return;
    fb_lo[i] = lo; fb_hi[i] = lo + size;
    fprintf(stderr, "jolt_gfx: fallback heap %d: %u bytes at %p\n", i, (unsigned)size, (void *)lo);
}

// stderr, not stdout: stdout's first write allocates its buffer, and this
// runs when malloc has just failed
static void fb_init(void)
{
    fb_tried = 1;
    fprintf(stderr, "jolt_gfx: real heap exhausted: live %u largest %u calls %u; arena1 %p..%p arena2 %p..%p\n",
            (unsigned)heap_live, (unsigned)heap_big, heap_calls,
            SYS_GetArena1Lo(), SYS_GetArena1Hi(), SYS_GetArena2Lo(), SYS_GetArena2Hi());
    fb_init_one(0, SYS_GetArena1Lo(), SYS_GetArena1Hi(), FB_ARENA1_RESERVE, SYS_SetArena1Lo);
    fb_init_one(1, SYS_GetArena2Lo(), SYS_GetArena2Hi(), 0, SYS_SetArena2Lo);
}

static int fb_of(const void *p)
{
    for (int i = 0; i < 2; i++)
        if (fb_lo[i] && (const u8 *)p >= fb_lo[i] && (const u8 *)p < fb_hi[i]) return i;
    return -1;
}

static void *fb_alloc(size_t n)
{
    if (!fb_tried) fb_init();
    for (int i = 0; i < 2; i++) {
        if (!fb_lo[i]) continue;
        u8 *b = __lwp_heap_allocate(&fb_heap[i], (u32)(n + FB_HDR));
        if (!b) continue;
        *(size_t *)b = n;
        return b + FB_HDR;
    }
    if (!heap_fails++) fprintf(stderr, "jolt_gfx: fallback heaps exhausted at a request of %u\n", (unsigned)n);
    return NULL;
}

static size_t fb_size(const void *p) { return *(const size_t *)((const u8 *)p - FB_HDR); }
static void fb_free(int i, void *p) { __lwp_heap_free(&fb_heap[i], (u8 *)p - FB_HDR); }

static void heap_account(void *p, size_t n)
{
    heap_live += malloc_usable_size(p);
    if (n > heap_big) heap_big = n;
    heap_calls++;
}

static uptr chez_uint(const char *fn)
{
    return Sunsigned_value(Scall0(Stop_level_value(Sstring_to_symbol(fn))));
}

static void heap_report(void)
{
    heap_iblock info[2] = {{0}, {0}};
    for (int i = 0; i < 2; i++) if (fb_lo[i]) __lwp_heap_getinfo(&fb_heap[i], &info[i]);
    jg_log("jolt_gfx: heap at jg_init: malloc live %u, fallback used %u + %u, free %u + %u; chez bytes-allocated %u, collect-trip-bytes %u\n",
           (unsigned)heap_live, (unsigned)info[0].used_size, (unsigned)info[1].used_size,
           (unsigned)info[0].free_size, (unsigned)info[1].free_size,
           (unsigned)chez_uint("bytes-allocated"), (unsigned)chez_uint("collect-trip-bytes"));
    jg_log("jolt_gfx: arena1 %p..%p arena2 %p..%p\n",
           SYS_GetArena1Lo(), SYS_GetArena1Hi(), SYS_GetArena2Lo(), SYS_GetArena2Hi());
}

void *__wrap_malloc(size_t n)
{
    void *p = __real_malloc(n);
    if (p) { heap_account(p, n); return p; }
    return fb_alloc(n);
}

void *__wrap_calloc(size_t a, size_t b)
{
    void *p = __real_calloc(a, b);
    if (p) { heap_account(p, a * b); return p; }
    p = fb_alloc(a * b);
    if (p) memset(p, 0, a * b);
    return p;
}

void __wrap_free(void *p)
{
    if (!p) return;
    int i = fb_of(p);
    if (i >= 0) { fb_free(i, p); return; }
    heap_live -= malloc_usable_size(p);
    __real_free(p);
}

void *__wrap_realloc(void *p, size_t n)
{
    if (!p) return __wrap_malloc(n);
    int i = fb_of(p);
    if (i < 0) {
        size_t old = malloc_usable_size(p);
        void *q = __real_realloc(p, n);
        if (q) { heap_live -= old; heap_account(q, n); return q; }
        q = fb_alloc(n);
        if (!q) return NULL;
        memcpy(q, p, old < n ? old : n);
        heap_live -= old;
        __real_free(p);
        return q;
    }
    size_t old = fb_size(p);
    if (n <= old) return p;
    void *q = __wrap_malloc(n);
    if (!q) return NULL;
    memcpy(q, p, old);
    fb_free(i, p);
    return q;
}
