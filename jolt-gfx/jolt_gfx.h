// jolt-gfx: raylib and the Wiimotes for a jolt program on the Wii.
//
// Call-shape rule: Chez on the Wii runs portable bytecode with no libffi, so
// it can only call C functions whose shape is in its fixed table. Every
// function here takes and returns uintptr_t, with at most 7 arguments. Clojure
// declares each as (ffi/defcfn name "jg_xxx" [:uptr ...] :uptr). Strings go
// over as the address from ffi/string->ptr. Signed values arrive as their
// two's complement bits and are cast back with (int).
//
// Units: colours are 0xRRGGBBAA. Coordinates, sizes and radii are whole
// pixels. Angles are whole degrees. Roundness is in hundredths (50 = 0.50).
//
// IR pointer: libogc reports it as floats that can drift a little below 0 or
// past the screen edge while valid is set. A negative value cast to uintptr_t
// would be a bignum on the Wii, so the IR getters return the position plus
// JG_IR_BIAS, clamped to [0, 0x0FFFFFFF] after biasing, and 0 when the pointer
// is not valid. Callers subtract JG_IR_BIAS (after checking the valid flag).
#define JG_IR_BIAS 4096
//
// Masks: values returned to Clojure fit a 32-bit Chez fixnum (below 2^29).
// Buttons are btns_h & 0xFFFF, times are masked with 0x0FFFFFFF.
//
// Every prototype below is registered by register_all in jolt_gfx.c, and
// nothing else is.
#ifndef JOLT_GFX_H
#define JOLT_GFX_H
#include <stdint.h>

typedef uintptr_t U;

// Window and frame.
U jg_init(U w, U h);
U jg_should_close(void);
U jg_close(void);
U jg_begin(U clear);
U jg_end(void);
U jg_frame_us(void);
U jg_time_us(void);
U jg_seed(void);

// Drawing.
U jg_gradient_v(U x, U y, U w, U h, U top, U bottom);
U jg_rect(U x, U y, U w, U h, U c);
U jg_rect_rounded(U x, U y, U w, U h, U roundness, U c);
U jg_circle(U x, U y, U r, U c);
U jg_ellipse(U x, U y, U rx, U ry, U c);
U jg_triangle(U x1, U y1, U x2, U y2, U x3, U y3, U c);
U jg_line(U x1, U y1, U x2, U y2, U c);
U jg_line_thick(U x1, U y1, U x2, U y2, U thick, U c);
U jg_ring(U x, U y, U inner, U outer, U c);
U jg_poly(U x, U y, U sides, U radius, U rot, U c);
U jg_text(U s, U x, U y, U size, U c);
U jg_text_width(U s, U size);

// Wiimotes, by channel 0..3. The IR position is biased (see JG_IR_BIAS) and 0
// when the pointer is not valid, so check jg_ir_valid first.
U jg_connected(U ch);
U jg_held(U ch);
U jg_ir_valid(U ch);
U jg_ir_x(U ch);
U jg_ir_y(U ch);
U jg_rumble(U ch, U on);
U jg_rumble_all_off(void);

#endif
