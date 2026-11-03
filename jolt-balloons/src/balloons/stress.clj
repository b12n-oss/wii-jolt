(ns balloons.stress
  "Speed measurement: a busy Balloon Pop frame (4 players aiming, 24 balloons,
   100 particles) updated by balloons.rules and drawn by balloons.draw, with
   update and draw timed every frame. Prints a frame summary every 120 frames."
  (:require [wii.gfx :as gfx]
            [wii.frame-stats :as stats]
            [balloons.rules :as r]
            [balloons.draw :as draw]))

(def ^:private app "jolt-stress")
(def ^:private target-balloons 24)
(def ^:private target-particles 100)
(def ^:private total-frames 1800)
(def ^:private summary-every 120)
(def ^:private aim-points [[160.0 160.0] [480.0 160.0] [160.0 320.0] [480.0 320.0]])

(defn- fixed-balloon [i]
  {:id i :x (double (+ 40 (mod (* i 53) 560))) :y (double (+ 60 (mod (* i 37) 380)))
   :r (double (+ 24 (mod i 12))) :vy -60.0 :wobble (* i 0.7)
   :color (mod i 6) :gold (zero? (mod i 8))})

(defn- fixed-particle [i]
  {:x (double (mod (* i 97) 640)) :y (double (+ 40 (mod (* i 53) 400)))
   :vx 20.0 :vy -20.0 :life 0.5 :color (mod i 6)})

(defn- aim [g]
  (assoc g :players
         (vec (map-indexed (fn [p pl]
                             (let [[x y] (nth aim-points p)]
                               (assoc pl :joined true :aiming true :x x :y y)))
                           (:players g)))))

(defn- initial-state []
  (-> (r/init (gfx/seed))
      (assoc :mode :play
             :balloons (mapv fixed-balloon (range target-balloons))
             :particles (mapv fixed-particle (range target-particles))
             :next-id target-balloons)
      aim))

(defn- top-up
  "Back to 24 balloons and 100 particles, players aiming at their fixed points."
  [g]
  (let [g (loop [g g] (if (< (count (:balloons g)) target-balloons) (recur (r/spawn-balloon g)) g))
        n (count (:particles g))]
    (aim (if (< n target-particles)
           (update g :particles into (map fixed-particle (range n target-particles)))
           g))))

(def ^:private counted
  '[gradient-v! rect! rect-rounded! circle! ellipse! triangle! line! line-thick!
    ring! poly! text! text-width])

(defn count-calls
  "The number of wii.gfx calls one draw of g makes. HOST ONLY: it swaps every
   drawing fn for a counting stub (no FFI), which works in the interpreter but
   not in a release build, where jolt inlines the small wii.gfx wrappers into
   balloons.draw. Run: jolt -A:test -e \"(require 'balloons.stress) (println (balloons.stress/count-busy-frame))\""
  [g t]
  (let [n (atom 0)
        stub (fn [& _] (swap! n inc) 0)
        vars (map (fn [nm] (resolve (symbol "wii.gfx" (str nm)))) counted)]
    (with-redefs-fn (zipmap vars (repeat stub))
      (fn [] (draw/draw g t)))
    (deref n)))

(defn count-busy-frame
  "count-calls for the busy play frame the stress loop draws."
  []
  (count-calls (aim (assoc (r/init 1) :mode :play
                           :balloons (mapv fixed-balloon (range target-balloons))
                           :particles (mapv fixed-particle (range target-particles))))
               1.0))

(defn- home? []
  (some (fn [ch] (and (gfx/connected? ch)
                      (pos? (bit-and (gfx/held ch) gfx/button-home))))
        (range 4)))

(defn -main [& _]
  (gfx/init! r/screen-w r/screen-h)
  (println (str app ": start"))
  (loop [frame 0 g (initial-state) t 0.0 samples []]
    (if (or (>= frame total-frames) (gfx/should-close?) (home?))
      (do (gfx/rumble-all-off!)
          (println (str app ": exit"))
          (gfx/close!))
      (let [fus (gfx/frame-us)
            dt (if (zero? fus) (/ 1.0 60) (min 0.05 (/ fus 1000000.0)))
            t (+ t dt)
            t0 (gfx/time-us)
            g (top-up (r/update g r/no-input dt))
            t1 (gfx/time-us)]
        (gfx/begin! draw/sky-top)
        (draw/draw g t)
        (let [t2 (gfx/time-us)]
          (gfx/circle! -50 240 20 draw/white)
          (gfx/end!)
          (when (zero? frame)
            (println (str app ": offscreen-circle x=-50 y=240 drawn")))
          (let [samples (conj samples {:update-us (stats/elapsed-us t0 t1)
                                       :draw-us (stats/elapsed-us t1 t2)
                                       :frame-us fus})
                done? (= (count samples) summary-every)]
            (when done? (println (stats/summary-line app (stats/summary samples))))
            (recur (inc frame) g t (if done? [] samples))))))))
