(ns balloons.rules-test
  (:require [clojure.test :refer [deftest is run-tests]]
            [balloons.rules :as r]))

(defn place
  "Add a balloon at (x, y) with radius 30, rising at 40 px/s."
  [g x y gold?]
  (-> g
      (update :balloons conj {:id (:next-id g) :x x :y y :r 30.0 :vy -40.0
                              :wobble 0.0 :color 0 :gold gold?})
      (update :next-id inc)))

(defn playing
  "A game in play mode with no balloons and the given players joined."
  [seed joined-players]
  (reduce (fn [g p] (assoc-in g [:players p :joined] true))
          (assoc (r/init seed) :mode :play :balloons [])
          joined-players))

(defn run-spawning
  "Apply update-balloons with spawning n times at 1/60 s. Returns the list of
   states after each step."
  [g n]
  (take n (rest (iterate (fn [s] (r/update-balloons s (/ 1.0 60) true)) g))))

(deftest pop-scores
  (let [g0 (-> (playing 1 [0]) (place 200.0 200.0 false) (place 400.0 200.0 true))
        [g1 p1] (r/try-pop g0 0 205.0 195.0)
        [g2 p2] (r/try-pop g1 0 205.0 195.0)
        [g3 p3] (r/try-pop g2 0 (+ 400.0 31.0) 200.0)
        [g4 p4] (r/try-pop g3 0 400.0 200.0)]
    (is (= 1 p1) "hit inside the radius scores 1")
    (is (not-any? (fn [b] (= 0 (:id b))) (:balloons g1)) "a popped balloon is gone")
    (is (= 0 p2) "same spot again hits nothing")
    (is (= 0 p3) "just outside the radius misses")
    (is (= 5 p4) "gold balloon scores 5")
    (is (= 6 (get-in g4 [:players 0 :score])) "score accumulates")
    (is (> (get-in g4 [:players 0 :rumble]) 0) "a pop starts the rumble")
    (is (= 0 (count (:balloons g4))) "both balloons are gone")))

(deftest balloons-rise-and-leave
  (let [g0 (place (playing 3 [0]) 320.0 100.0 false)
        g1 (r/update-balloons g0 0.5 false)
        g2 (r/update-balloons (place (playing 3 [0]) 320.0 -200.0 false) (/ 1.0 60) false)]
    (is (< (:y (first (:balloons g1))) 100.0) "balloons rise")
    (is (empty? (:balloons g2)) "a balloon that floats off the top is removed")))

(deftest spawning-keeps-screen-busy
  (let [g (last (run-spawning (playing 99 [0]) 300))
        fits? (fn [b] (and (>= (- (:x b) (:r b)) 0) (<= (+ (:x b) (:r b)) r/screen-w)))]
    (is (>= (count (:balloons g)) 4) "after 5 seconds there are several balloons up")
    (is (some fits? (:balloons g)) "spawned balloons fit horizontally on screen")))

(deftest spawning-respects-the-cap
  (let [states (run-spawning (playing 99 [0]) 1200)]
    (is (= 1200 (count states)) "all 20 seconds ran")
    (is (every? (fn [s] (<= (count (:balloons s)) 24)) states)
        "never more than 24 live balloons")
    (is (every? (fn [s] (<= (count (:balloons s)) r/max-balloons)) states))))

(deftest rng-determinism-and-range
  (let [stream (fn [seed n] (take n (map r/rand-unit (rest (iterate r/rand-step (r/rng seed))))))
        g (r/init 1234)
        xs (take 1000 (rest (iterate (fn [[g' _]] (r/randf g' -2.0 3.0)) [g nil])))]
    (is (= (stream 1234 100) (stream 1234 100)) "same seed, same sequence")
    (is (not= (stream 1234 20) (stream 1235 20)) "different seeds differ")
    (is (every? (fn [[_ x]] (and (>= x -2.0) (< x 3.0))) xs) "randf stays in [lo, hi)")
    (is (every? (fn [u] (and (>= u 0.0) (< u 1.0))) (stream 7 1000)) "rand-unit stays in [0, 1)")
    (let [z (r/rng 0)]
      (is (not= z (r/rand-step z)) "seed 0 is not stuck")
      (is (not= (r/rand-step z) (r/rand-step (r/rand-step z))) "seed 0 keeps moving"))))

(deftest rng-stays-fixnum-safe
  (doseq [seed [0 1 -7 123456789]]
    (let [states (take 10001 (iterate r/rand-step (r/rng seed)))]
      (is (= 10001 (count states)))
      (is (every? (fn [s] (every? (fn [c] (and (integer? c) (>= c 0) (< c 536870912))) s)) states)
          (str "seed " seed " stays in [0, 2^29)")))))

(defn -main [& _]
  (let [res (run-tests 'balloons.rules-test)]
    (System/exit (if (zero? (+ (:fail res 0) (:error res 0))) 0 1))))
