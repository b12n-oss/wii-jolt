(ns balloons.cast-bench
  "The double-to-integer casts alone, timed on the Wii: balloons.draw-profile's
   cast micro-ops in a program small enough to boot (the profile sits at the
   .dol memory cliff and no longer prints). Prints one
   'cast-bench: micro <name> iters=<n> ops=<n> total-us=<t> net-ns-per-op=<ns>'
   line per case, the empty loop's cost per iteration subtracted, in the same
   format as draw-profile so the two can be read side by side."
  (:require [wii.gfx :as gfx]
            [wii.frame-stats :as stats]))

(def ^:private app "cast-bench")
(def ^:private sink (atom nil))
(def ^:private reps 5000)
(def ^:private a-double 123.7)
(def ^:private integral-double 124.0)
(def ^:private a-fixnum 123)

(defn cast-hinted [^double x] (long x))
(defn mul-boxed [a b] (* a b))

(def ^:private empty-ns-per-iter (atom 0))

(defn- measure
  [nm iters ops f]
  (let [t0 (gfx/time-us)
        _ (reset! sink (f))
        t1 (gfx/time-us)
        total (stats/elapsed-us t0 t1)
        net (quot (- (* total 1000) (* (deref empty-ns-per-iter) iters)) ops)]
    (println (str app ": micro " nm " iters=" iters " ops=" ops
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
    (println (str app ": micro empty-loop iters=" reps " ops=" reps
                  " total-us=" total " net-ns-per-op=" (deref empty-ns-per-iter))))
  (micro "mul-boxed" reps (mul-boxed (if (zero? i) 1.5 a-double) 1.0001))
  (micro "long-cast" reps (long a-double))
  (micro "long-cast-hinted-fn" reps (cast-hinted a-double))
  (micro "long-cast-integral-double" reps (long integral-double))
  (micro "long-cast-fixnum" reps (long a-fixnum))
  (micro "int-cast" reps (int a-double))
  (micro "unchecked-long" reps (unchecked-long a-double))
  (micro "unchecked-int" reps (unchecked-int a-double)))

(defn -main [& _]
  (gfx/init! 640 480)
  (println (str app ": start"))
  (println (str app ": edges " [(long 536870912.0) (long 536870911.9) (long -536870912.0) (long -536870912.5) (unchecked-int 536870911.9) (long -123.7) (long 1.152921504606847E18) (cast-hinted 536870911.9) (cast-hinted 536870656.5) (cast-hinted -123.7)]))
  ;; two passes: the first warms the paths, the second is the one to read
  (dotimes [pass 2]
    (println (str app ": pass " (inc pass)))
    (micro-ops))
  (println (str app ": done"))
  (gfx/close!))
