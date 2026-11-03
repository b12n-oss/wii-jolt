(ns balloons.draw-test
  "Golden test for balloons.draw: the full list of wii.gfx calls (name and
   arguments) that draw makes for a set of states must equal the list recorded
   in draw_golden.edn, which was generated from the code before the P2.T4
   optimisation pass. Host only (jolt -A:test); it never touches the FFI.
   Run: cd jolt-balloons && jolt -A:test -m balloons.draw-test
   Regenerate (only for a deliberate picture change):
     jolt -A:test -m balloons.draw-test gen"
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.edn :as edn]
            [balloons.draw :as draw]
            [balloons.rules :as r]
            [balloons.stress :as stress]))

(def ^:private golden-file "test/balloons/draw_golden.edn")

;; name -> [string-first? number-of-long-args]. Everything after the long args
;; is a colour, passed as given. wii.gfx passes numbers through long.
(def ^:private drawing-fns
  {'gradient-v! [false 4] 'rect! [false 4] 'rect-rounded! [false 5]
   'circle! [false 3] 'ellipse! [false 4] 'triangle! [false 6] 'line! [false 4]
   'line-thick! [false 5] 'ring! [false 4] 'poly! [false 5] 'text! [true 3]})

(defn- record-args [[str-first? nlong] args]
  (let [args (vec args)
        off (if str-first? 1 0)]
    (vec (concat (when str-first? [(nth args 0)])
                 (map (fn [i] (long (nth args (+ off i)))) (range nlong))
                 (drop (+ off nlong) args)))))

(defn- raw-longs
  "The arguments wii.gfx will cast with long, as draw passed them (before any cast)."
  [[str-first? nlong] args]
  (take nlong (drop (if str-first? 1 0) args)))

(def ^:private raw-log
  "Raw numeric arguments seen by the stubs of the current `calls` run."
  (atom []))

(defn calls
  "Every wii.gfx call draw makes for state g at time t, as [name & args]."
  [g t]
  (let [log (atom [])
        stub (fn [nm spec]
               (fn [& args]
                 (swap! raw-log into (raw-longs spec args))
                 (swap! log conj (into [(keyword nm)] (record-args spec args))) nil))
        width (fn [s size] (swap! raw-log conj size) (swap! log conj [:text-width s (long size)]) (* 10 (count s)))
        vars (assoc (into {} (map (fn [[nm spec]]
                                    [(resolve (symbol "wii.gfx" (str nm))) (stub nm spec)])
                                  drawing-fns))
                    (resolve 'wii.gfx/text-width) width)]
    (with-redefs-fn vars (fn [] (draw/draw g t)))
    @log))

(defn- busy-state []
  (stress/aim (assoc (r/init 1) :mode :play
                     :balloons (mapv stress/fixed-balloon (range 24))
                     :particles (mapv stress/fixed-particle (range 100)))))

(defn- joined [g & ps]
  (reduce (fn [g p] (assoc-in g [:players p :joined] true)) g ps))

(defn- scored [g & p+s]
  (reduce (fn [g [p s]] (assoc-in (assoc-in g [:players p :joined] true) [:players p :score] s))
          g (partition 2 p+s)))

(defn- results [mode-time & p+s]
  (apply scored (assoc (r/init 1) :mode :results :mode-time mode-time) p+s))

(defn states
  "[label g t] for every golden state."
  []
  (let [title (r/init 1)
        demo (-> (r/init 2)
                 (assoc :mode :demo :mode-time 3.0
                        :balloons (mapv stress/fixed-balloon (range 3))
                        :particles [{:x 100.0 :y 120.0 :vx 0.0 :vy 0.0 :life 0.5 :color -1}
                                    {:x 200.0 :y 220.0 :vx 0.0 :vy 0.0 :life 0.25 :color 3}
                                    {:x 300.0 :y 320.0 :vx 0.0 :vy 0.0 :life 0.75 :color 0}]
                        :popups [{:x 150.0 :y 150.0 :life 0.8 :points 5 :player 1}
                                 {:x 250.0 :y 250.0 :life 0.4 :points 1 :player 0}])
                 (scored 0 4 1 9)
                 (assoc-in [:players 0 :aiming] true))]
    [["busy t=1.0" (busy-state) 1.0]
     ["busy t=2.37" (busy-state) 2.37]
     ["title t=0.25" title 0.25]
     ["title t=0.75" title 0.75]
     ["countdown 0.5, players 0 and 2"
      (-> (assoc (r/init 1) :mode :countdown :mode-time 0.5)
          (joined 0 2) (assoc-in [:players 0 :aiming] true)) 0.5]
     ["countdown 2.9 -> n=1" (-> (assoc (r/init 1) :mode :countdown :mode-time 2.9) (joined 1)) 0.5]
     ["results winner of two" (results 2.25 0 5 1 3) 1.0]
     ["results winner of two, blink off" (results 2.75 0 5 1 3) 1.0]
     ["results tie" (results 2.25 0 2 1 2) 1.0]
     ["results single player" (results 2.25 0 7) 1.0]
     ["results before lock" (results 1.0 0 5 1 3) 1.0]
     ["demo t=0.25" demo 0.25]
     ["demo t=0.75" demo 0.75]]))

(defn- all-calls [] (mapv (fn [[label g t]] [label (calls g t)]) (states)))

(deftest golden-draw-calls
  (let [golden (edn/read-string (slurp golden-file))
        now (all-calls)]
    (is (= (count golden) (count now)) "same number of states")
    (doseq [[[gl gc] [nl nc]] (map vector golden now)]
      (is (= gl nl) "state order")
      (is (pos? (count nc)) (str nl " draws something"))
      (is (= gc nc) (str nl ": same wii.gfx calls")))))

(def ^:private fixnum-bound 536870912)

(deftest casts-agree-on-every-recorded-argument
  ;; wii.gfx casts drawing arguments with unchecked-long (the checked long
  ;; costs 135 us per double on the Wii). That is the same value only for
  ;; finite, in-range doubles, so pin it on everything the golden states draw.
  (reset! raw-log [])
  (all-calls)
  (let [vs @raw-log
        ds (filter double? vs)]
    (println (str "casts-agree: " (count vs) " numeric arguments, " (count ds) " doubles checked"))
    (is (pos? (count ds)))
    (doseq [v ds]
      (is (not (Double/isNaN v)) (str "NaN argument"))
      (is (not (Double/isInfinite v)) (str "infinite argument"))
      (is (< (- fixnum-bound) v fixnum-bound) (str "out of range: " v))
      (is (= (long v) (unchecked-long v)) (str "long and unchecked-long differ on " v)))))

(defn -main [& args]
  (if (= (first args) "gen")
    (let [cs (all-calls)]
      (spit golden-file (with-out-str (println "[") (doseq [c cs] (prn c)) (println "]")))
      (println (str "wrote " golden-file ": " (count cs) " states, "
                    (reduce + (map (fn [[_ c]] (count c)) cs)) " calls")))
    (let [res (run-tests 'balloons.draw-test)]
      (when (pos? (+ (:fail res 0) (:error res 0))) (System/exit 1)))))
