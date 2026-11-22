(ns balloons.rules-test
  (:require [clojure.test :refer [deftest is run-tests]]
            [balloons.rules :as r]))

(defn place
  "Add a balloon at (x, y) with radius 30, rising at 40 px/s, gold when :gold?."
  [g x y & [{:keys [gold?] :or {gold? false}}]]
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
  (let [g0 (-> (playing 1 [0]) (place 200.0 200.0) (place 400.0 200.0 {:gold? true}))
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
  (let [g0 (place (playing 3 [0]) 320.0 100.0)
        g1 (r/update-balloons g0 0.5 false)
        g2 (r/update-balloons (place (playing 3 [0]) 320.0 -200.0) (/ 1.0 60) false)]
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

(def dt (/ 1.0 60))

(defn step
  "Apply update with inputs at 1/60 s until seconds have passed (bounded)."
  [g inputs seconds]
  (let [limit (+ 3 (long (Math/ceil (* seconds 60))))]
    (loop [g g, t 0.0, i 0]
      (if (and (< t seconds) (< i limit))
        (recur (r/update g inputs dt) (+ t dt) (inc i))
        g))))

(defn press
  "Inputs where only player p is connected, aiming and firing at (x, y)."
  [p x y]
  (assoc r/no-input p {:connected true :aiming true :x x :y y :fire true}))

(defn alive-particles [g] (count (:particles g)))

(deftest title-to-play
  (let [g0 (r/init 42)
        g1 (r/update g0 (press 1 320.0 240.0) dt)
        g2 (r/update g1 (press 2 100.0 100.0) dt)
        g3 (step g2 r/no-input (+ r/countdown-seconds 0.1))]
    (is (= :title (:mode g0)) "starts on the title screen")
    (is (= :countdown (:mode g1)) "A on the title starts the countdown")
    (is (get-in g1 [:players 1 :joined]) "the player who pressed A joins")
    (is (not (get-in g1 [:players 0 :joined])) "players who didn't press stay out")
    (is (get-in g2 [:players 2 :joined]) "a second player can join during the countdown")
    (is (= :play (:mode g3)) "countdown ends in play")
    (is (= 2 (r/joined-count g3)) "two players joined")))

(deftest fire-through-update
  (let [g0 (place (playing 7 [3]) 320.0 300.0)
        g1 (r/update g0 (press 3 320.0 300.0) dt)]
    (is (= 1 (get-in g1 [:players 3 :score])) "fire on a balloon through update scores")
    (is (= 1 (:pops-this-round g1)) "pop counter increments")
    (is (> (alive-particles g1) 0) "a pop makes particles")))

(deftest unjoined-cannot-score
  (let [g0 (place (playing 7 [0]) 320.0 300.0)
        g1 (r/update g0 (press 2 320.0 300.0) dt)]
    (is (and (= 0 (get-in g1 [:players 2 :score])) (some (fn [b] (= 0 (:id b))) (:balloons g1)))
        "a player who didn't join can't pop")
    (is (= false (get-in g1 [:players 2 :joined])) "and doesn't join mid-round")))

(deftest disconnected-player-keeps-score
  (let [g0 (-> (playing 7 [1]) (assoc-in [:players 1 :score] 3) (place 320.0 300.0))
        in (assoc r/no-input 1 {:connected false :aiming true :x 320.0 :y 300.0 :fire true})
        g1 (r/update g0 in dt)]
    (is (= 3 (get-in g1 [:players 1 :score])) "score unchanged")
    (is (some (fn [b] (= 0 (:id b))) (:balloons g1)) "the balloon is still there")
    (is (get-in g1 [:players 1 :joined]) "still joined")
    (is (= false (get-in g1 [:players 1 :aiming])) "not aiming")))

(deftest simultaneous-pop-scores-once
  (let [g0 (place (playing 7 [0 2]) 320.0 300.0)
        fire {:connected true :aiming true :x 320.0 :y 300.0 :fire true}
        g1 (r/update g0 (assoc r/no-input 0 fire 2 fire) dt)]
    (is (= 1 (get-in g1 [:players 0 :score])) "the lower index scores")
    (is (= 0 (get-in g1 [:players 2 :score])) "the other player doesn't")
    (is (not-any? (fn [b] (= 0 (:id b))) (:balloons g1)) "the balloon is gone (play mode may spawn a new one below the screen)")
    (is (= 1 (:pops-this-round g1)) "one pop")))

(deftest effects-respect-their-caps
  (let [particle {:x 0.0 :y 0.0 :vx 0.0 :vy 0.0 :life 5.0 :color 0}
        popup {:x 0.0 :y 0.0 :life 5.0 :points 1 :player 0}
        g0 (-> (place (playing 7 [0]) 320.0 300.0)
               (assoc :particles (vec (repeat 256 particle))
                      :popups (vec (repeat 16 popup))))
        [g1 points] (r/try-pop g0 0 320.0 300.0)]
    (is (= 1 points) "the pop still scores")
    (is (= 1 (get-in g1 [:players 0 :score])))
    (is (= 256 (count (:particles g1))) "particles stay at the cap")
    (is (= 16 (count (:popups g1))) "popups stay at the cap")))

(deftest round-ends-with-winner
  (let [g1 (r/update (r/init 5) (press 0 10.0 10.0) dt)
        g2 (r/update g1 (press 1 10.0 10.0) dt)
        g3 (step g2 r/no-input (+ r/countdown-seconds 0.1))
        g4 (assoc-in g3 [:players 1 :score] 3)
        g5 (step g4 r/no-input (+ r/round-seconds 0.1))
        g6 (r/update g5 (press 0 10.0 10.0) dt)
        g7 (step g6 r/no-input r/results-lock-seconds)
        g8 (r/update g7 (press 0 10.0 10.0) dt)]
    (is (= :play (:mode g3)) "playing")
    (is (= :results (:mode g5)) "the round ends after round-seconds")
    (is (= 1 (r/winner g5)) "highest score wins")
    (is (not (r/tie? g5)) "not a tie")
    (is (= 1 (:rounds-played g5)) "round counted")
    (is (= :results (:mode g6)) "A is ignored right after the results appear")
    (is (= :title (:mode g8)) "A after the lock returns to the title")
    (is (and (not (get-in g8 [:players 0 :joined])) (= 0 (get-in g8 [:players 0 :score])))
        "players reset for the next round")))

(deftest tie
  (let [g (-> (r/init 5)
              (assoc-in [:players 0 :joined] true) (assoc-in [:players 2 :joined] true)
              (assoc-in [:players 0 :score] 4) (assoc-in [:players 2 :score] 4))]
    (is (r/tie? g) "equal top scores is a tie")
    (is (= 0 (r/winner g)) "winner on a tie is the lowest index")
    (is (not (r/tie? (assoc-in g [:players 2 :score] 5))) "no longer a tie")
    (is (nil? (r/winner (r/init 5))) "no one joined, no winner")))

(deftest demo-mode
  (let [g1 (step (r/init 11) r/no-input (+ r/attract-idle-seconds 0.1))
        g2 (step g1 r/no-input 10.0)
        total (reduce + (map :score (:players g2)))
        g3 (r/update g2 (press 0 320.0 240.0) dt)
        g4 (step (r/init 11) r/no-input (+ r/attract-idle-seconds r/demo-seconds 0.2))]
    (is (= :demo (:mode g1)) "idle on the title starts the demo")
    (is (> total 0) "demo bots pop balloons")
    (is (= :title (:mode g3)) "pressing A during the demo returns to the title")
    (doseq [p (range 4)]
      (is (and (not (get-in g3 [:players p :joined])) (= 0 (get-in g3 [:players p :score])))
          "demo scores don't leak into a real game"))
    (is (= :title (:mode g4)) "the demo ends by itself")))

(defn -main [& _]
  (let [res (run-tests 'balloons.rules-test)]
    (System/exit (if (zero? (+ (:fail res 0) (:error res 0))) 0 1))))
