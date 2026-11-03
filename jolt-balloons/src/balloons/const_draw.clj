(ns balloons.const-draw
  "Split measurement (P2.T3): the same 368 wii.gfx calls as one busy Balloon
   Pop frame, but every argument is computed once at start-up and each frame
   only loops over the vector calling wii.gfx. Frame time is then the FFI
   crossing plus raylib, with almost no Clojure math. Update time is 0 (there
   is no update). Prints the stress frame-summary lines, prefix jolt-const-draw."
  (:require [wii.gfx :as gfx]
            [wii.frame-stats :as stats]
            [balloons.rules :as r]
            [balloons.draw :as draw]))

(def ^:private app "jolt-const-draw")
(def ^:private total-frames 1800)
(def ^:private summary-every 120)
(def ^:private t 1.0)
(def ^:private aim-points [[160 160] [480 160] [160 320] [480 320]])

;; Call kinds, small integers so the per-frame dispatch is a cheap case.
(def ^:private k-gradient 0)
(def ^:private k-rect 1)
(def ^:private k-circle 2)
(def ^:private k-ellipse 3)
(def ^:private k-triangle 4)
(def ^:private k-line 5)
(def ^:private k-line-thick 6)
(def ^:private k-ring 7)
(def ^:private k-poly 8)
(def ^:private k-text 9)
(def ^:private k-text-width 10)

(defn- sky-calls []
  (let [w r/screen-w h r/screen-h]
    (-> [[k-gradient 0 0 w h draw/sky-top draw/sky-bottom]]
        (into (mapcat (fn [i]
                        (let [x (long (- (mod (+ (* i 190.0) (* t (+ 8 (* i 3)))) (+ w 200)) 100))
                              y (+ 60 (* i 70))]
                          [[k-circle x y 26 draw/cloud]
                           [k-circle (+ x 28) (+ y 6) 22 draw/cloud]
                           [k-circle (- x 26) (+ y 8) 18 draw/cloud]]))
                      (range 4)))
        (conj [k-rect 0 (- h 24) w 24 draw/ground]))))

(defn- balloon-calls
  "The calls draw-balloon makes for the stress scene's balloon i."
  [i]
  (let [x (double (+ 40 (mod (* i 53) 560)))
        y (double (+ 60 (mod (* i 37) 380)))
        rr (double (+ 24 (mod i 12)))
        gold (zero? (mod i 8))
        c (if gold draw/golden (nth draw/balloon-colors (mod i 6)))
        rx (* rr 0.85)
        wobble (* i 0.7)
        string (loop [s 1 px x py (+ y rr 4) acc []]
                 (if (<= s 6)
                   (let [nx (+ x (* (Math/sin (+ (* t 3) s wobble)) 3))
                         ny (+ y rr 4 (* s 7))]
                     (recur (inc s) nx ny
                            (conj acc [k-line (long px) (long py) (long nx) (long ny) draw/string-grey])))
                   acc))
        body [[k-triangle (long x) (long (+ y rr -2)) (long (- x 5)) (long (+ y rr 5))
               (long (+ x 5)) (long (+ y rr 5)) c]
              [k-ellipse (long x) (long y) (long rx) (long rr) c]
              [k-ellipse (long (- x (* rx 0.35))) (long (- y (* rr 0.4)))
               (long (* rx 0.22)) (long (* rr 0.3)) draw/shine]]
        pulse (+ 0.5 (* 0.5 (Math/sin (* t 10))))]
    (cond-> (into string body)
      gold (conj [k-poly (long x) (long y) 5 (long (* rr 0.45)) (long (* t 90))
                  (+ 0xFFFFFF00 (long (+ 120 (* pulse 120))))]))))

(defn- particle-calls [i]
  (let [life 0.5]
    [[k-circle (mod (* i 97) 640) (+ 40 (mod (* i 53) 400)) (long (+ 3 (* life 3)))
      (nth draw/balloon-colors (mod i 6))]]))

(defn- hud-calls []
  (-> [[k-rect 0 0 r/screen-w 36 draw/hud-bar]]
      (into (map (fn [p] [k-text (str "P" (inc p) " 0") (+ 12 (* p 120)) 8 24
                          (nth draw/player-colors p)])
                 (range 4)))
      (conj [k-text-width "60" 28])
      (conj [k-text "60" (- r/screen-w 16 40) 5 28 draw/white])))

(defn- cursor-calls [p]
  (let [[x y] (nth aim-points p)
        c (nth draw/player-colors p)]
    [[k-ring x y 13 18 c]
     [k-ring x y 11 13 draw/white]
     [k-line-thick (- x 24) y (- x 8) y 3 c]
     [k-line-thick (+ x 8) y (+ x 24) y 3 c]
     [k-line-thick x (- y 24) x (- y 8) 3 c]
     [k-line-thick x (+ y 8) x (+ y 24) 3 c]
     [k-text (str "P" (inc p)) (+ x 16) (+ y 12) 20 c]]))

(defn calls
  "The wii.gfx calls of the busy stress frame as [kind arg...], every number a
   long. 14 sky + 219 balloon + 100 particle + 7 HUD + 28 cursor = 368."
  []
  (-> (vec (sky-calls))
      (into (mapcat balloon-calls (range 24)))
      (into (mapcat particle-calls (range 100)))
      (into (hud-calls))
      (into (mapcat cursor-calls (range 4)))))

(defn- run-call [c]
  (case (nth c 0)
    0 (gfx/gradient-v! (nth c 1) (nth c 2) (nth c 3) (nth c 4) (nth c 5) (nth c 6))
    1 (gfx/rect! (nth c 1) (nth c 2) (nth c 3) (nth c 4) (nth c 5))
    2 (gfx/circle! (nth c 1) (nth c 2) (nth c 3) (nth c 4))
    3 (gfx/ellipse! (nth c 1) (nth c 2) (nth c 3) (nth c 4) (nth c 5))
    4 (gfx/triangle! (nth c 1) (nth c 2) (nth c 3) (nth c 4) (nth c 5) (nth c 6) (nth c 7))
    5 (gfx/line! (nth c 1) (nth c 2) (nth c 3) (nth c 4) (nth c 5))
    6 (gfx/line-thick! (nth c 1) (nth c 2) (nth c 3) (nth c 4) (nth c 5) (nth c 6))
    7 (gfx/ring! (nth c 1) (nth c 2) (nth c 3) (nth c 4) (nth c 5))
    8 (gfx/poly! (nth c 1) (nth c 2) (nth c 3) (nth c 4) (nth c 5) (nth c 6))
    9 (gfx/text! (nth c 1) (nth c 2) (nth c 3) (nth c 4) (nth c 5))
    10 (gfx/text-width (nth c 1) (nth c 2))))

(defn- draw-all [cs]
  (let [n (count cs)]
    (loop [i 0]
      (when (< i n)
        (run-call (nth cs i))
        (recur (inc i))))))

(defn- home? []
  (some (fn [ch] (and (gfx/connected? ch)
                      (pos? (bit-and (gfx/held ch) gfx/button-home))))
        (range 4)))

(defn -main [& _]
  (gfx/init! r/screen-w r/screen-h)
  (let [cs (calls)]
    (println (str app ": start calls=" (count cs)))
    (loop [frame 0 samples []]
      (if (or (>= frame total-frames) (gfx/should-close?) (home?))
        (do (gfx/rumble-all-off!)
            (println (str app ": exit"))
            (gfx/close!))
        (let [fus (gfx/frame-us)]
          (gfx/begin! draw/sky-top)
          (let [t1 (gfx/time-us)]
            (draw-all cs)
            (let [t2 (gfx/time-us)]
              (gfx/end!)
              (let [samples (conj samples {:update-us 0
                                           :draw-us (stats/elapsed-us t1 t2)
                                           :frame-us fus})
                    done? (= (count samples) summary-every)]
                (when done? (println (stats/summary-line app (stats/summary samples))))
                (recur (inc frame) (if done? [] samples))))))))))
