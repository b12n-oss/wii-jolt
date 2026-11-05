(ns wii.gfx
  "raylib and the Wiimotes for a jolt program on the Wii. One function per
   jg_* shim function (see jolt-gfx/jolt_gfx.h for units). Every C function
   takes and returns :uptr; coordinates are truncated with unchecked-long here,
   as C truncates a float passed as int. The drawing wrappers hint their
   geometry parameters ^double so jolt lowers that cast to an inline
   flonum->fixnum (a fixnum argument widens on entry, which costs less than the
   generic cast did). Colours are 0xRRGGBBAA and go through untouched."
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
(ffi/defcfn c-scale "jg_scale" [:uptr] :uptr)
(ffi/defcfn c-sheet-load "jg_sheet_load" [:uptr] :uptr)
(ffi/defcfn c-sprite "jg_sprite" [:uptr :uptr :uptr :uptr :uptr :uptr] :uptr)
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
(def button-2 0x0001)
(def button-1 0x0002)
(def button-minus 0x0010)
(def button-plus 0x1000)
(def button-left 0x0100)
(def button-right 0x0200)
(def button-down 0x0400)
(def button-up 0x0800)

(def sheet-tiles 0)
(def sheet-players 1)
(def sheet-mobs 2)
(def flip-x 1)
(def flip-y 2)

;; Window and frame.
(defn init! [w h] (c-init (long w) (long h)) nil)
(defn should-close? [] (not= 0 (c-should-close)))
(defn close! [] (c-close) nil)
(defn begin! [clear] (c-begin clear) nil)
(defn end! [] (c-end) nil)
(defn frame-us [] (c-frame-us))
(defn time-us [] (c-time-us))
(defn seed [] (c-seed))

;; Drawing. Geometry is ^double (see the ns docstring); colours pass through.
(defn gradient-v! [^double x ^double y ^double w ^double h top bottom]
  (c-gradient-v (unchecked-long x) (unchecked-long y) (unchecked-long w) (unchecked-long h) top bottom) nil)
(defn rect! [^double x ^double y ^double w ^double h c]
  (c-rect (unchecked-long x) (unchecked-long y) (unchecked-long w) (unchecked-long h) c) nil)
(defn rect-rounded! [^double x ^double y ^double w ^double h ^double roundness c]
  (c-rect-rounded (unchecked-long x) (unchecked-long y) (unchecked-long w) (unchecked-long h) (unchecked-long roundness) c) nil)
(defn circle! [^double x ^double y ^double r c]
  (c-circle (unchecked-long x) (unchecked-long y) (unchecked-long r) c) nil)
(defn ellipse! [^double x ^double y ^double rx ^double ry c]
  (c-ellipse (unchecked-long x) (unchecked-long y) (unchecked-long rx) (unchecked-long ry) c) nil)
(defn triangle! [^double x1 ^double y1 ^double x2 ^double y2 ^double x3 ^double y3 c]
  (c-triangle (unchecked-long x1) (unchecked-long y1) (unchecked-long x2) (unchecked-long y2) (unchecked-long x3) (unchecked-long y3) c) nil)
(defn line! [^double x1 ^double y1 ^double x2 ^double y2 c]
  (c-line (unchecked-long x1) (unchecked-long y1) (unchecked-long x2) (unchecked-long y2) c) nil)
(defn line-thick! [^double x1 ^double y1 ^double x2 ^double y2 ^double thick c]
  (c-line-thick (unchecked-long x1) (unchecked-long y1) (unchecked-long x2) (unchecked-long y2) (unchecked-long thick) c) nil)
(defn ring! [^double x ^double y ^double inner ^double outer c]
  (c-ring (unchecked-long x) (unchecked-long y) (unchecked-long inner) (unchecked-long outer) c) nil)
(defn poly! [^double x ^double y ^double sides ^double radius ^double rot c]
  (c-poly (unchecked-long x) (unchecked-long y) (unchecked-long sides) (unchecked-long radius) (unchecked-long rot) c) nil)

;; Sprite sheets. Positions are in game pixels, scaled by scale!.
(defn scale! [n] (c-scale (long n)) nil)
(defn sheet-load! [id] (zero? (c-sheet-load (long id))))
(defn sprite! [sheet col row ^double x ^double y flags]
  (c-sprite sheet col row (unchecked-long x) (unchecked-long y) flags) nil)

(defn text!
  "Draw s at (x, y). The string goes over as a C string's address, freed after."
  [s ^double x ^double y ^double size c]
  (let [p (ffi/string->ptr s)]
    (try
      (c-text p (unchecked-long x) (unchecked-long y) (unchecked-long size) c)
      (finally (ffi/free p))))
  nil)

(defn text-width [s ^double size]
  (let [p (ffi/string->ptr s)]
    (try
      (c-text-width p (unchecked-long size))
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
