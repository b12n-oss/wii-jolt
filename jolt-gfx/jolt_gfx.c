// See jolt_gfx.h. Chez finds these functions by name through its static
// foreign-symbol table. There is no dlsym on the Wii, so they are registered
// with Sforeign_symbol from inside Sbuild_heap's init callback (the pack links
// with -Wl,--wrap=Sbuild_heap).
#include "jolt_gfx.h"
#include "raylib.h"
#include <stdlib.h>
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

static bool chan_ok(U ch) { return (int)ch >= 0 && (int)ch < NCHAN; }

U jg_init(U w, U h)
{
    InitWindow((int)w, (int)h, "jolt");
    SetTargetFPS(60);
    WPAD_SetDataFormat(WPAD_CHAN_ALL, WPAD_FMT_BTNS_ACC_IR);
    WPAD_SetVRes(WPAD_CHAN_ALL, (int)w, (int)h);
    return 0;
}
U jg_should_close(void) { return WindowShouldClose(); }
U jg_close(void) { CloseWindow(); return 0; }
U jg_begin(U clear) { BeginDrawing(); ClearBackground(rgba(clear)); return 0; }
U jg_end(void) { EndDrawing(); return 0; }
U jg_frame_us(void) { return (U)(GetFrameTime() * 1000000.0f); }
U jg_time_us(void) { return (U)(ticks_to_microsecs(gettime()) & 0x0FFFFFFF); }
U jg_seed(void) { return (U)(gettime() & 0x0FFFFFFF); }

U jg_gradient_v(U x, U y, U w, U h, U top, U bottom)
{
    JG_STUB();
    DrawRectangleGradientV((int)x, (int)y, (int)w, (int)h, rgba(top), rgba(bottom));
    return 0;
}
U jg_rect(U x, U y, U w, U h, U c)
{
    JG_STUB();
    DrawRectangle((int)x, (int)y, (int)w, (int)h, rgba(c));
    return 0;
}
U jg_rect_rounded(U x, U y, U w, U h, U roundness, U c)
{
    JG_STUB();
    Rectangle r = { (float)(int)x, (float)(int)y, (float)(int)w, (float)(int)h };
    DrawRectangleRounded(r, (float)(int)roundness / 100.0f, 8, rgba(c));
    return 0;
}
U jg_circle(U x, U y, U r, U c)
{
    JG_STUB();
    DrawCircle((int)x, (int)y, (float)(int)r, rgba(c));
    return 0;
}
U jg_ellipse(U x, U y, U rx, U ry, U c)
{
    JG_STUB();
    DrawEllipse((int)x, (int)y, (float)(int)rx, (float)(int)ry, rgba(c));
    return 0;
}
U jg_triangle(U x1, U y1, U x2, U y2, U x3, U y3, U c)
{
    JG_STUB();
    DrawTriangle((Vector2){ (float)(int)x1, (float)(int)y1 },
                 (Vector2){ (float)(int)x2, (float)(int)y2 },
                 (Vector2){ (float)(int)x3, (float)(int)y3 }, rgba(c));
    return 0;
}
U jg_line(U x1, U y1, U x2, U y2, U c)
{
    JG_STUB();
    DrawLine((int)x1, (int)y1, (int)x2, (int)y2, rgba(c));
    return 0;
}
U jg_line_thick(U x1, U y1, U x2, U y2, U thick, U c)
{
    JG_STUB();
    DrawLineEx((Vector2){ (float)(int)x1, (float)(int)y1 },
               (Vector2){ (float)(int)x2, (float)(int)y2 }, (float)(int)thick, rgba(c));
    return 0;
}
U jg_ring(U x, U y, U inner, U outer, U c)
{
    JG_STUB();
    DrawRing((Vector2){ (float)(int)x, (float)(int)y }, (float)(int)inner,
             (float)(int)outer, 0.0f, 360.0f, 24, rgba(c));
    return 0;
}
U jg_poly(U x, U y, U sides, U radius, U rot, U c)
{
    JG_STUB();
    DrawPoly((Vector2){ (float)(int)x, (float)(int)y }, (int)sides,
             (float)(int)radius, (float)(int)rot, rgba(c));
    return 0;
}
U jg_text(U s, U x, U y, U size, U c)
{
    JG_STUB();
    DrawText((const char *)s, (int)x, (int)y, (int)size, rgba(c));
    return 0;
}
U jg_text_width(U s, U size)
{
    JG_STUB();
    return (U)MeasureText((const char *)s, (int)size);
}

// Wiimote channel ch. Each call probes the channel itself.
U jg_connected(U ch)
{
    u32 t;
    return chan_ok(ch) && WPAD_Probe((int)ch, &t) == WPAD_ERR_NONE;
}
U jg_held(U ch) { return jg_connected(ch) ? (U)(WPAD_Data((int)ch)->btns_h & 0xFFFF) : 0; }
U jg_ir_valid(U ch) { return jg_connected(ch) && WPAD_Data((int)ch)->ir.valid; }

// An IR coordinate as the biased, fixnum-safe value Clojure gets (see JG_IR_BIAS).
static U ir_biased(float v)
{
    long n = (long)(int)v + JG_IR_BIAS;
    if (n < 0) n = 0;
    if (n > 0x0FFFFFFF) n = 0x0FFFFFFF;
    return (U)n;
}
U jg_ir_x(U ch) { return jg_ir_valid(ch) ? ir_biased(WPAD_Data((int)ch)->ir.x) : 0; }
U jg_ir_y(U ch) { return jg_ir_valid(ch) ? ir_biased(WPAD_Data((int)ch)->ir.y) : 0; }

// Like wiimote_rumble in examples/common: only talk to WPAD on a change.
U jg_rumble(U ch, U on)
{
    if (!chan_ok(ch) || rumbling[ch] == (on != 0)) return 0;
    WPAD_Rumble((int)ch, on != 0);
    rumbling[ch] = on != 0;
    return 0;
}
U jg_rumble_all_off(void)
{
    for (int c = 0; c < NCHAN; c++) { WPAD_Rumble(c, 0); rumbling[c] = false; }
    return 0;
}

// stdout/stderr to Dolphin's log (OSREPORT) or a USB Gecko, before jolt's main
// runs, as the C games do with SYS_STDIO_Report(true). raylib owns the screen.
__attribute__((constructor))
static void jg_stdio(void)
{
    SYS_STDIO_Report(true);
    // jolt sizes its allocation window (the "nursery") at 16MB, which is more
    // than the malloc heap has left after a 14MB .dol loads into MEM1, so a
    // program that allocates dies with "out of memory" before the first
    // collection. Pin it small; jolt reads this at startup.
    setenv("JOLT_GC_TRIP_BYTES", "2097152", 0);
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
    R(jg_text); R(jg_text_width);
    R(jg_connected); R(jg_held); R(jg_ir_valid); R(jg_ir_x); R(jg_ir_y);
    R(jg_rumble); R(jg_rumble_all_off);
#undef R
}

void __wrap_Sbuild_heap(const char *exe, void (*init)(void))
{
    jolt_init = init;
    __real_Sbuild_heap(exe, register_all);
}
