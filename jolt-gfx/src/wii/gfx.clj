(ns wii.gfx
  "raylib and the Wiimotes for a jolt program on the Wii. One function per
   jg_* shim function (see jolt-gfx/jolt_gfx.h for units). Every C function
   takes and returns :uptr; coordinates are truncated with long here, as C
   truncates a float passed as int. Colours are 0xRRGGBBAA."
  (:require [jolt.ffi :as ffi]))

(ffi/defcfn c-init "jg_init" [:uptr :uptr] :uptr)
(ffi/defcfn c-should-close "jg_should_close" [] :uptr)
(ffi/defcfn c-close "jg_close" [] :uptr)
(ffi/defcfn c-begin "jg_begin" [:uptr] :uptr)
(ffi/defcfn c-end "jg_end" [] :uptr)
(ffi/defcfn c-frame-us "jg_frame_us" [] :uptr)
(ffi/defcfn c-time-us "jg_time_us" [] :uptr)
(ffi/defcfn c-seed "jg_seed" [] :uptr)
(ffi/defcfn c-gradient-v "jg_gradient_v" [:uptr :uptr :uptr :uptr :uptr :uptr] :uptr)
(ffi/defcfn c-rect "jg_rect" [:uptr :uptr :uptr :uptr :uptr] :uptr)
(ffi/defcfn c-rect-rounded "jg_rect_rounded" [:uptr :uptr :uptr :uptr :uptr :uptr] :uptr)
(ffi/defcfn c-circle "jg_circle" [:uptr :uptr :uptr :uptr] :uptr)
(ffi/defcfn c-ellipse "jg_ellipse" [:uptr :uptr :uptr :uptr :uptr] :uptr)
(ffi/defcfn c-triangle "jg_triangle" [:uptr :uptr :uptr :uptr :uptr :uptr :uptr] :uptr)
(ffi/defcfn c-line "jg_line" [:uptr :uptr :uptr :uptr :uptr] :uptr)
(ffi/defcfn c-line-thick "jg_line_thick" [:uptr :uptr :uptr :uptr :uptr :uptr] :uptr)
(ffi/defcfn c-ring "jg_ring" [:uptr :uptr :uptr :uptr :uptr] :uptr)
(ffi/defcfn c-poly "jg_poly" [:uptr :uptr :uptr :uptr :uptr :uptr] :uptr)
(ffi/defcfn c-text "jg_text" [:uptr :uptr :uptr :uptr :uptr] :uptr)
(ffi/defcfn c-text-width "jg_text_width" [:uptr :uptr] :uptr)
(ffi/defcfn c-connected "jg_connected" [:uptr] :uptr)
(ffi/defcfn c-held "jg_held" [:uptr] :uptr)
(ffi/defcfn c-ir-valid "jg_ir_valid" [:uptr] :uptr)
(ffi/defcfn c-ir-x "jg_ir_x" [:uptr] :uptr)
(ffi/defcfn c-ir-y "jg_ir_y" [:uptr] :uptr)
(ffi/defcfn c-rumble "jg_rumble" [:uptr :uptr] :uptr)
(ffi/defcfn c-rumble-all-off "jg_rumble_all_off" [] :uptr)

(def ir-bias 4096)
(def button-a 0x0008)
(def button-b 0x0004)
(def button-home 0x0080)

(defn color
  "0xRRGGBBAA from four 0..255 values."
  [r g b a]
  (+ (* (long r) 16777216) (* (long g) 65536) (* (long b) 256) (long a)))

;; Window and frame.
(defn init! [w h] (c-init (long w) (long h)) nil)
(defn should-close? [] (not= 0 (c-should-close)))
(defn close! [] (c-close) nil)
(defn begin! [clear] (c-begin clear) nil)
(defn end! [] (c-end) nil)
(defn frame-us [] (c-frame-us))
(defn time-us [] (c-time-us))
(defn seed [] (c-seed))

;; Drawing.
(defn gradient-v! [x y w h top bottom]
  (c-gradient-v (long x) (long y) (long w) (long h) top bottom) nil)
(defn rect! [x y w h c] (c-rect (long x) (long y) (long w) (long h) c) nil)
(defn rect-rounded! [x y w h roundness c]
  (c-rect-rounded (long x) (long y) (long w) (long h) (long roundness) c) nil)
(defn circle! [x y r c] (c-circle (long x) (long y) (long r) c) nil)
(defn ellipse! [x y rx ry c] (c-ellipse (long x) (long y) (long rx) (long ry) c) nil)
(defn triangle! [x1 y1 x2 y2 x3 y3 c]
  (c-triangle (long x1) (long y1) (long x2) (long y2) (long x3) (long y3) c) nil)
(defn line! [x1 y1 x2 y2 c] (c-line (long x1) (long y1) (long x2) (long y2) c) nil)
(defn line-thick! [x1 y1 x2 y2 thick c]
  (c-line-thick (long x1) (long y1) (long x2) (long y2) (long thick) c) nil)
(defn ring! [x y inner outer c] (c-ring (long x) (long y) (long inner) (long outer) c) nil)
(defn poly! [x y sides radius rot c]
  (c-poly (long x) (long y) (long sides) (long radius) (long rot) c) nil)

(defn text!
  "Draw s at (x, y). The string goes over as a C string's address, freed after."
  [s x y size c]
  (let [p (ffi/string->ptr s)]
    (try
      (c-text p (long x) (long y) (long size) c)
      (finally (ffi/free p))))
  nil)

(defn text-width [s size]
  (let [p (ffi/string->ptr s)]
    (try
      (c-text-width p (long size))
      (finally (ffi/free p)))))

;; Wiimotes, by channel 0..3.
(defn connected? [ch] (not= 0 (c-connected ch)))
(defn held [ch] (c-held ch))
(defn ir-valid? [ch] (not= 0 (c-ir-valid ch)))
(defn ir-x
  "Call only after ir-valid? is true."
  [ch] (- (c-ir-x ch) ir-bias))
(defn ir-y
  "Call only after ir-valid? is true."
  [ch] (- (c-ir-y ch) ir-bias))
(defn rumble! [ch on?] (c-rumble ch (if on? 1 0)) nil)
(defn rumble-all-off! [] (c-rumble-all-off) nil)
