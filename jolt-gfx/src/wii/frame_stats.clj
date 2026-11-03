(ns wii.frame-stats
  "Frame timing summaries for the Wii games. Pure Clojure, no FFI, so it runs
   in host tests.")

(def ^:private frame-budget-us 20000)
(def ^:private time-mask 0x0FFFFFFF)

(defn- median
  "The lower middle of the sorted values."
  [xs]
  (nth (sort xs) (quot (dec (count xs)) 2)))

(defn summary
  "samples: a seq of {:update-us :draw-us :frame-us}. Work is update + draw."
  [samples]
  (let [work (map (fn [s] (+ (:update-us s) (:draw-us s))) samples)]
    {:n (count samples)
     :update-median-us (median (map :update-us samples))
     :draw-median-us (median (map :draw-us samples))
     :work-median-us (median work)
     :work-worst-us (apply max work)
     :frame-worst-us (apply max (map :frame-us samples))
     :dropped (count (filter (fn [s] (> (:frame-us s) frame-budget-us)) samples))}))

(defn summary-line [app s]
  (str app ": frames n=" (:n s)
       " update-median-us=" (:update-median-us s)
       " draw-median-us=" (:draw-median-us s)
       " work-median-us=" (:work-median-us s)
       " work-worst-us=" (:work-worst-us s)
       " frame-worst-us=" (:frame-worst-us s)
       " dropped=" (:dropped s)))

(defn elapsed-us
  "t1 - t0 for times that wrap at 2^28 (the shim masks with 0x0FFFFFFF)."
  [t0 t1]
  (bit-and (- t1 t0) time-mask))
