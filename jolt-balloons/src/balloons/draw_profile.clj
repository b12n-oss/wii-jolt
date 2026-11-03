(ns balloons.draw-profile
  "Where does balloons.draw spend its time? Times, on the busy stress frame,
   the whole draw and each section (median of 120 frames), then the cost of
   single operations (N iterations, the empty loop subtracted). Build it
   against the stub-draw pack so the numbers are Clojure plus the crossing
   (bb build:jolt-draw-profile). Prints 'draw-profile: ...' lines."
  (:require [jolt.ffi :as ffi]
            [wii.gfx :as gfx]
            [wii.frame-stats :as stats]
            [balloons.rules :as r]
            [balloons.stress :as stress]
            [balloons.draw :as draw]))

(def ^:private frames 120)
(def ^:private t 1.0)

(defn- median [xs] (nth (sort xs) (quot (dec (count xs)) 2)))

(defn- time-section
  "Median microseconds of (f) over 120 frames, each inside begin!/end!."
  [nm f]
  (let [us (loop [i 0 acc []]
             (if (< i frames)
               (do (gfx/begin! draw/sky-top)
                   (let [t0 (gfx/time-us)
                         _ (f)
                         t1 (gfx/time-us)]
                     (gfx/end!)
                     (recur (inc i) (conj acc (stats/elapsed-us t0 t1)))))
               acc))]
    (println (str "draw-profile: section " nm " n=" frames " median-us=" (median us)))))

(defn- with-popups [g]
  (assoc g :popups (mapv (fn [i] {:x (double (* i 60)) :y 100.0 :life 0.5
                                  :points (if (even? i) 1 5) :player (mod i 4)})
                         (range 8))))

;; ---- single operations ----------------------------------------------------

(def ^:private sink (atom nil))
(def ^:private reps 5000)
(def ^:private vec100 (vec (range 100)))
(def ^:private vec6 [10 20 30 40 50 60])
(def ^:private a-balloon (stress/fixed-balloon 3))
(def ^:private a-double 123.7)

(defn mul-boxed [a b] (* a b))
(defn mul-hinted ^double [^double a ^double b] (* a b))

(defn cast-hinted [^double x] (long x))
(def ^:private an-int 123)
(def ^:private integral-double 124.0)

(def ^:private empty-ns-per-iter (atom 0))

(defn- measure
  "Time (f), which loops reps times; ops is the operations per run. Prints
   total microseconds and the net nanoseconds per operation (empty loop's cost
   per iteration subtracted)."
  [nm iters ops f]
  (let [t0 (gfx/time-us)
        _ (reset! sink (f))
        t1 (gfx/time-us)
        total (stats/elapsed-us t0 t1)
        net (quot (- (* total 1000) (* (deref empty-ns-per-iter) iters)) ops)]
    (println (str "draw-profile: micro " nm " iters=" iters " ops=" ops
                  " total-us=" total " net-ns-per-op=" net))
    (flush)))

(defmacro ^:private micro
  "Run body (which may use i, the iteration number) reps times."
  [nm ops body]
  `(measure ~nm reps ~ops
            (fn [] (loop [~'i 0 ~'acc nil]
                     (if (< ~'i reps) (recur (inc ~'i) ~body) ~'acc)))))

(defn- micro-ops []
  (let [t0 (gfx/time-us)
        _ (reset! sink (loop [i 0] (if (< i reps) (recur (inc i)) i)))
        t1 (gfx/time-us)
        total (stats/elapsed-us t0 t1)]
    (reset! empty-ns-per-iter (quot (* total 1000) reps))
    (println (str "draw-profile: micro empty-loop iters=" reps " ops=" reps
                  " total-us=" total " net-ns-per-op=" (deref empty-ns-per-iter))))
  (micro "kw-lookup" reps (:x a-balloon))
  (micro "nth-6-vector" reps (nth vec6 3))
  (micro "mul-boxed" reps (mul-boxed (if (zero? i) 1.5 a-double) 1.0001))
  (micro "mul-double-hinted-fn" reps (mul-hinted (if (zero? i) 1.5 a-double) 1.0001))
  (micro "long-cast" reps (long a-double))
  (micro "long-cast-hinted-fn" reps (cast-hinted a-double))
  (micro "unchecked-long" reps (unchecked-long a-double))
  (micro "long-cast-integral-double" reps (long integral-double))
  (micro "Math-floor" reps (Math/floor a-double))
  (micro "Math-sin" reps (Math/sin a-double))
  (micro "str-P-i" reps (str "P" i))
  (micro "string->ptr+free" reps (let [p (ffi/string->ptr "P1")] (ffi/free p) 0))
  ;; 50 runs over 100 elements: 5000 element visits
  (measure "doseq-empty-100-vector" 50 5000
           (fn [] (loop [k 0] (if (< k 50) (do (doseq [x vec100] x) (recur (inc k))) k))))
  (measure "loop-nth-100-vector" 50 5000
           (fn [] (loop [k 0]
                    (if (< k 50)
                      (do (loop [j 0] (when (< j 100) (nth vec100 j) (recur (inc j))))
                          (recur (inc k)))
                      k)))))

(defn -main [& _]
  (gfx/init! r/screen-w r/screen-h)
  (println "draw-profile: start")
  (let [g (stress/initial-state)
        gp (with-popups g)]
    (time-section "whole-draw" (fn [] (draw/draw g t)))
    (time-section "sky" (fn [] (draw/draw-sky t)))
    (time-section "balloons" (fn [] (draw/draw-balloons g t)))
    (time-section "particles" (fn [] (draw/draw-particles g)))
    (time-section "popups-0" (fn [] (draw/draw-popups g)))
    (time-section "popups-8" (fn [] (draw/draw-popups gp)))
    (time-section "hud" (fn [] (draw/draw-hud g)))
    (time-section "cursors" (fn [] (draw/draw-cursors g))))
  (micro-ops)
  (println "draw-profile: done")
  (gfx/close!))
