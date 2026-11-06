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
(ffi/defcfn c-tiles-load "jg_tiles_load" [:uptr :uptr :uptr] :uptr)
(ffi/defcfn c-tiles-begin "jg_tiles_begin" [:uptr] :uptr)
(ffi/defcfn c-decor-set "jg_decor_set" [:uptr :uptr] :uptr)
(ffi/defcfn c-tile-set "jg_tile_set" [:uptr :uptr] :uptr)
(ffi/defcfn c-tiles-draw "jg_tiles_draw" [:uptr] :uptr)
(ffi/defcfn c-tiles-map "jg_tiles_map" [:uptr :uptr :uptr :uptr :uptr :uptr] :uptr)
(ffi/defcfn c-calls "jg_calls" [] :uptr)
(ffi/defcfn c-text "jg_text" [:uptr :uptr :uptr :uptr :uptr] :uptr)
(ffi/defcfn c-text-width "jg_text_width" [:uptr :uptr] :uptr)
(ffi/defcfn c-setting-get "jg_setting_get" [:uptr :uptr] :uptr)
(ffi/defcfn c-setting-put "jg_setting_put" [:uptr :uptr] :uptr)
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

;; Tile layers: 15 rows, width columns (at most 190), ids as in sheet 0.
(defn tiles-load!
  "Copy the tile and decor byte arrays (width * 15 each) into the shim. Returns
   true when the shim accepted them."
  [^bytes tiles ^bytes decor width]
  (let [n (* 15 (long width))]
    (when-not (= (alength tiles) (alength decor) n)
      (throw (ex-info "tiles-load!: layers must be width*15 bytes"
                      {:width width :tiles (alength tiles) :decor (alength decor)})))
    (let [tp (ffi/alloc n)]
      (try
        (let [dp (ffi/alloc n)]
          (try
            (ffi/write-bytes tp tiles)
            (ffi/write-bytes dp decor)
            (zero? (c-tiles-load tp dp (long width)))
            (finally (ffi/free dp))))
        (finally (ffi/free tp))))))
(defn tiles-begin!
  "Clear both layers and set the stage width (1..190); fill them with
   tile-set! and decor-set!. Returns true when the shim accepted the width."
  [width] (zero? (c-tiles-begin (long width))))
(defn decor-set! [index id] (c-decor-set (long index) (long id)) nil)
(defn tile-set! [index tile] (c-tile-set (long index) (long tile)) nil)
(defn tiles-draw! [^double cam-x] (c-tiles-draw (unchecked-long cam-x)) nil)
(defn tiles-map! [x y w h solid sky]
  (c-tiles-map (long x) (long y) (long w) (long h) solid sky) nil)
(defn calls
  "The number of counted shim calls since the last call to this."
  [] (c-calls))

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

;; An integer setting in settings.txt beside boot.dol.
(defn setting-get [k default]
  (let [p (ffi/string->ptr k)]
    (try
      (c-setting-get p default)
      (finally (ffi/free p)))))

(defn setting-put! [k value]
  (let [p (ffi/string->ptr k)]
    (try
      (= 1 (c-setting-put p value))
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

;; Sound.
(ffi/defcfn c-snd-init "jg_snd_init" [] :uptr)
(ffi/defcfn c-sfx "jg_sfx" [:uptr] :uptr)
(ffi/defcfn c-music "jg_music" [:uptr :uptr] :uptr)
(ffi/defcfn c-snd-stats "jg_snd_stats" [] :uptr)

(defn sound-init!
  "Start the audio voice. True when it runs, false when AESND refused."
  [] (= 1 (c-snd-init)))

(defn sfx!
  "Trigger sound effect id (an index into the effect table)."
  [id] (c-sfx (long id)) nil)

(defn music!
  "Start song from the first row, looping at its loop point when loop? is true."
  [song loop?] (c-music (long song) (if loop? 1 0)) nil)

(defn sound-stats
  "Counters since the last call: the largest sample rendered, effects
   triggered, stream callbacks run."
  []
  (let [s (c-snd-stats)]
    {:peak (bit-and s 0x7FFF)
     :sfx (bit-and (bit-shift-right s 15) 0xFF)
     :callbacks (bit-shift-right s 23)}))
