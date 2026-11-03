(ns balloons.draw
  "Balloon Pop's drawing, a port of draw_* in examples/balloons/main.c: same
   colours, positions, sizes and blink timings. Calls only wii.gfx."
  (:require [wii.gfx :as gfx]
            [balloons.rules :as r]))

;; Every constant colour is made once (on the Wii most are bignums).
(def sky-top 0x6EBEFFFF)
(def sky-bottom 0xD2F0FFFF)
(def cloud 0xFFFFFFC8)
(def ground 0x5AB450FF)
(def string-grey 0x505050FF)
(def shine 0xFFFFFF8C)
(def white 0xFFFFFFFF)
(def red 0xE62937FF)
(def dark-blue 0x0052ACFF)
(def dark-gray 0x505050FF)
(def golden 0xFFCB00FF)
(def shadow 0x0000005A)
(def hud-bar 0x0000005A)
(def panel 0xFFFFFFC8)
(def results-panel 0xFFFFFFDC)
(def player-colors [0x2878FFFF 0xEB323CFF 0x28B446FF 0xFABE14FF])
(def balloon-colors [0xE62937FF 0xFF6DC2FF 0x009E2FFF 0x0079F1FF 0xC87AFFFF 0xFFA100FF])

(def ^:private screen-w r/screen-w)
(def ^:private screen-h r/screen-h)

(defn- centered
  "Shadowed text centred on the screen (ui_centered)."
  [s y size c]
  (let [x (- (quot screen-w 2) (quot (gfx/text-width s size) 2))]
    (gfx/text! s (+ x 3) (+ y 3) size shadow)
    (gfx/text! s x y size c)))

(defn draw-sky [t]
  (gfx/gradient-v! 0 0 screen-w screen-h sky-top sky-bottom)
  (doseq [i (range 4)]
    (let [x (- (mod (+ (* i 190.0) (* t (+ 8 (* i 3)))) (+ screen-w 200)) 100)
          y (+ 60 (* i 70))]
      (gfx/circle! x y 26 cloud)
      (gfx/circle! (+ x 28) (+ y 6) 22 cloud)
      (gfx/circle! (- x 26) (+ y 8) 18 cloud)))
  (gfx/rect! 0 (- screen-h 24) screen-w 24 ground))

(defn draw-balloon [b t]
  (let [gold (:gold b)
        c (if gold golden (nth balloon-colors (:color b)))
        x (:x b) y (:y b)
        ry (:r b)
        rx (* ry 0.85)
        wobble (:wobble b)
        yr (+ y ry)
        knot (+ yr 4)
        t3 (* t 3)]
    ;; string: six wavy segments hanging from the knot
    (loop [s 1 px x py knot]
      (when (<= s 6)
        (let [nx (+ x (* (Math/sin (+ t3 s wobble)) 3))
              ny (+ knot (* s 7))]
          (gfx/line! px py nx ny string-grey)
          (recur (inc s) nx ny))))
    (gfx/triangle! x (+ yr -2) (- x 5) (+ yr 5) (+ x 5) (+ yr 5) c)
    (gfx/ellipse! x y rx ry c)
    (gfx/ellipse! (- x (* rx 0.35)) (- y (* ry 0.4)) (* rx 0.22) (* ry 0.3) shine)
    (when gold
      (let [pulse (+ 0.5 (* 0.5 (Math/sin (* t 10))))]
        (gfx/poly! x y 5 (* ry 0.45) (* t 90)
                   (+ 0xFFFFFF00 (long (+ 120 (* pulse 120)))))))))

(defn draw-balloons [g t]
  (doseq [b (:balloons g)] (draw-balloon b t)))

(defn draw-cursor [p pl]
  (when (:aiming pl)
    (let [c (nth player-colors p)
          x (:x pl) y (:y pl)]
      (gfx/ring! x y 13 18 c)
      (gfx/ring! x y 11 13 white)
      (gfx/line-thick! (- x 24) y (- x 8) y 3 c)
      (gfx/line-thick! (+ x 8) y (+ x 24) y 3 c)
      (gfx/line-thick! x (- y 24) x (- y 8) 3 c)
      (gfx/line-thick! x (+ y 8) x (+ y 24) 3 c)
      (gfx/text! (str "P" (inc p)) (+ x 16) (+ y 12) 20 c))))

(defn draw-hud [g]
  (gfx/rect! 0 0 screen-w 36 hud-bar)
  (loop [p 0 x 12]
    (when (< p r/max-players)
      (let [pl (nth (:players g) p)]
        (if (:joined pl)
          (do (gfx/text! (str "P" (inc p) " " (:score pl)) x 8 24 (nth player-colors p))
              (recur (inc p) (+ x 120)))
          (recur (inc p) x)))))
  (let [secs (long (Math/ceil (r/time-left g)))
        tl (str (if (neg? secs) 0 secs))]
    (gfx/text! tl (- screen-w 16 (gfx/text-width tl 28)) 5 28
               (if (<= secs 10) red white))))

(defn draw-particles [g]
  (doseq [pt (:particles g)]
    (let [c (if (neg? (:color pt)) golden (nth balloon-colors (:color pt)))]
      (gfx/circle! (:x pt) (:y pt) (+ 3 (* (:life pt) 3)) c))))

(defn draw-popups [g]
  (doseq [pu (:popups g)]
    (let [pts (:points pu)]
      (gfx/text! (str "+" pts) (- (:x pu) 10) (- (:y pu) 10) (if (> pts 1) 32 24)
                 (nth player-colors (:player pu))))))

(defn draw-effects [g]
  (draw-particles g)
  (draw-popups g))

(defn draw-results [g]
  (gfx/rect! 80 70 (- screen-w 160) (- screen-h 140) results-panel)
  (centered "Time's up!" 90 44 dark-blue)
  (when-let [w (r/winner g)]
    (cond
      (r/tie? g) (centered "It's a tie!" 145 32 dark-gray)
      (> (r/joined-count g) 1) (centered (str "Player " (inc w) " wins!") 145 32 (nth player-colors w))
      :else (centered "Great popping!" 145 32 (nth player-colors w))))
  (loop [p 0 y 200]
    (when (< p r/max-players)
      (let [pl (nth (:players g) p)]
        (if (:joined pl)
          (do (centered (str "Player " (inc p) ":  " (:score pl)) y 30 (nth player-colors p))
              (recur (inc p) (+ y 40)))
          (recur (inc p) y)))))
  (when (and (>= (:mode-time g) r/results-lock-seconds)
             (even? (long (* (:mode-time g) 2))))
    (centered "Press A to play again" (- screen-h 110) 24 dark-gray)))

(defn draw-cursors [g]
  (doseq [p (range r/max-players)]
    (let [pl (nth (:players g) p)]
      ;; pointers show whenever a remote is aimed; in a round only for joined players
      (when-not (and (= (:mode g) :play) (not (:joined pl)))
        (draw-cursor p pl)))))

(defn- blink? [t] (even? (long (* t 2))))

(defn draw
  "Draw one frame's contents for game state g at time t (seconds). The caller
   does begin! and end!."
  [g t]
  (draw-sky t)
  (draw-balloons g t)
  (draw-effects g)
  (case (:mode g)
    :title
    (do (gfx/rect-rounded! 40 105 (- screen-w 80) 250 15 panel)
        (centered "BALLOON POP" 120 64 red)
        (centered "Point at the TV and press A to join!" 220 24 dark-blue)
        (centered "Up to 4 players. Gold balloons are worth 5!" 255 20 dark-gray)
        (when (blink? t) (centered "Press A" 320 32 dark-blue)))
    :countdown
    (let [n (long (Math/ceil (- r/countdown-seconds (:mode-time g))))]
      (centered (if (> n 0) (str n) "GO!") 170 120 dark-blue)
      (loop [p 0 y 320]
        (when (< p r/max-players)
          (if (:joined (nth (:players g) p))
            (do (centered (str "Player " (inc p) " is in!") y 22 (nth player-colors p))
                (recur (inc p) (+ y 28)))
            (recur (inc p) y))))
      (centered "Others: press A to join" (- screen-h 50) 20 dark-gray))
    :play (draw-hud g)
    :results (do (draw-hud g) (draw-results g))
    :demo (do (draw-hud g)
              (when (blink? t) (centered "DEMO - press A to play!" (- screen-h 70) 28 dark-blue))))
  (draw-cursors g))
