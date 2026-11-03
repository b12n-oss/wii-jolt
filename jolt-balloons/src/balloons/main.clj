(ns balloons.main
  "Balloon Pop in Clojure: the main loop. Reads the four Wiimotes, runs
   balloons.rules, draws with balloons.draw, logs events in the same format as
   examples/balloons/main.c (prefix 'jolt-balloons: ') and prints a frame
   summary every 120 frames. HOME on any connected remote ends the game."
  (:require [wii.gfx :as gfx]
            [wii.frame-stats :as stats]
            [balloons.rules :as r]
            [balloons.draw :as draw]))

(def ^:private app "jolt-balloons")
(def ^:private summary-every 120)
(def ^:private fire-mask (bit-or gfx/button-a gfx/button-b))

(defn- held-now
  "The held-button mask of each of the four remotes (0 when not connected)."
  []
  (mapv (fn [ch] (if (gfx/connected? ch) (gfx/held ch) 0)) (range r/max-players)))

(defn- home? [held]
  (some (fn [h] (pos? (bit-and h gfx/button-home))) held))

(defn- player-input
  "One player's input map for rules/update. :fire is a new press of A or B (per-button edge)."
  [ch now before]
  (let [connected (gfx/connected? ch)
        aiming (boolean (and connected (gfx/ir-valid? ch)))]
    {:connected connected
     :aiming aiming
     :x (if aiming (double (gfx/ir-x ch)) 0.0)
     :y (if aiming (double (gfx/ir-y ch)) 0.0)
     :fire (pos? (bit-and now (bit-not before) fire-mask))}))

(defn- log-events
  "Print a mode line when the mode changed and a pop line for each score that
   went up. Returns [mode scores] to remember for the next frame."
  [g last-mode last-scores]
  (let [mode (:mode g)
        scores (mapv :score (:players g))]
    (when (not= mode last-mode)
      (println (str app ": mode " (name mode))))
    (doseq [p (range r/max-players)]
      (when (> (nth scores p) (nth last-scores p))
        (println (str app ": pop player=" (inc p) " score=" (nth scores p)
                      " mode=" (name mode)))))
    [mode scores]))

(defn -main [& _]
  (gfx/init! r/screen-w r/screen-h)
  (let [g0 (r/init (gfx/seed))]
    (println (str app ": start"))
    (loop [g g0
           t 0.0
           last-held [0 0 0 0]
           last-mode (:mode g0)
           last-scores (mapv :score (:players g0))
           samples []]
      (let [t0 (gfx/time-us)
            fus (gfx/frame-us)
            dt (if (zero? fus) (/ 1.0 60) (min 0.05 (/ fus 1000000.0)))
            held (held-now)]
        (if (or (gfx/should-close?) (home? held))
          (do (gfx/rumble-all-off!)
              (println (str app ": exit"))
              (gfx/close!))
          (let [inputs (mapv (fn [ch] (player-input ch (nth held ch) (nth last-held ch)))
                             (range r/max-players))
                g (r/update g inputs dt)]
            (doseq [[ch pl] (map-indexed vector (:players g))]
              (gfx/rumble! ch (and (pos? (:rumble pl)) (not= (:mode g) :demo))))
            (let [[mode scores] (log-events g last-mode last-scores)
                  t (+ t dt)
                  t1 (gfx/time-us)]
              (gfx/begin! draw/sky-top)
              (draw/draw g t)
              (let [t2 (gfx/time-us)]
                (gfx/end!)
                (let [samples (conj samples {:update-us (stats/elapsed-us t0 t1)
                                             :draw-us (stats/elapsed-us t1 t2)
                                             :frame-us fus})
                      done? (= (count samples) summary-every)]
                  (when done? (println (stats/summary-line app (stats/summary samples))))
                  (recur g t held mode scores (if done? [] samples)))))))))))
