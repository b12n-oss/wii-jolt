(ns mona.rules-test
  (:require [clojure.test :refer [deftest is run-tests]]
            [mona.rules :as r]))

(deftest ground-and-get-tile
  (let [lv (-> (r/make-level "T" 10 :day 300 false) (r/ground 0 9))]
    (is (= r/t-ground-top (r/get-tile lv 3 13)))
    (is (= r/t-ground (r/get-tile lv 3 14)))
    (is (= r/t-empty (r/get-tile lv 3 12)))
    (is (r/solid? lv 3 13))))

(deftest get-tile-outside-the-grid
  (let [lv (r/make-level "T" 10 :day 300 false)]
    (is (= r/t-hard (r/get-tile lv -1 5)))
    (is (= r/t-hard (r/get-tile lv 10 5)))
    (is (= r/t-empty (r/get-tile lv 5 -1)))
    (is (= r/t-empty (r/get-tile lv 5 15)))
    (is (identical? lv (r/set-tile! lv 10 5 r/t-brick)))))

(deftest row-letters-and-contents
  (let [lv (-> (r/make-level "T" 20 :day 300 false) (r/row 2 9 "BQ-H=o" :chip))]
    (is (= [r/t-brick r/t-question r/t-empty r/t-hard r/t-girder r/t-empty]
           (mapv (fn [i] (r/get-tile lv (+ 2 i) 9)) (range 6))))
    (is (= :chip (get (:contents lv) (r/index lv 3 9))))
    (is (contains? (:stars lv) [7 9]))))

(deftest cave-theme-uses-cave-tiles
  (let [lv (-> (r/make-level "T" 10 :cave 300 true) (r/ground 0 9) (r/row 1 9 "B"))]
    (is (= r/t-cave-ground (r/get-tile lv 1 13)))
    (is (= r/t-cave-brick (r/get-tile lv 1 9)))))

(deftest pipe-stairs-and-flag
  (let [lv (-> (r/make-level "T" 40 :day 300 false) (r/ground 0 39) (r/pipe 4 2) (r/stairs 10 3) (r/flag 30))]
    (is (= [r/t-pipe-tl r/t-pipe-tr] [(r/get-tile lv 4 11) (r/get-tile lv 5 11)]))
    (is (= [r/t-pipe-l r/t-pipe-r] [(r/get-tile lv 4 12) (r/get-tile lv 5 12)]))
    (is (= [r/t-hard r/t-empty] [(r/get-tile lv 10 12) (r/get-tile lv 10 11)]))
    (is (= [r/t-hard r/t-hard r/t-hard] [(r/get-tile lv 12 12) (r/get-tile lv 12 11) (r/get-tile lv 12 10)]))
    (is (= 30 (:flag-x lv)))
    (is (= r/t-pole-top (r/get-tile lv 30 3)))
    (is (= r/t-pole (r/get-tile lv 30 4)))
    (is (= r/t-hard (r/get-tile lv 30 12)))
    (is (= (+ (* 35 8) 18) (:castle-door lv)))
    (is (= r/t-castle (get (:decor lv) (r/index lv 35 8))))))

(deftest block-and-enemy
  (let [lv (-> (r/make-level "T" 10 :day 300 false) (r/block 3 5 [:multi 6] true) (r/enemy 7 :shellbot) (r/enemy 8))]
    (is (= r/t-brick (r/get-tile lv 3 5)))
    (is (= [:multi 6] (get (:contents lv) (r/index lv 3 5))))
    (is (= [[7 12 :shellbot] [8 12 :bug]] (:spawns lv)))))

(deftest move-x-stops-at-a-wall
  (let [lv (-> (r/make-level "T" 20 :day 300 false) (r/ground 0 19) (r/row 10 12 "H"))
        p (assoc (r/new-player 70.0 93.0 :mona false) :vx 60.0)
        [p2 hit?] (r/move-x p 11 lv 0.1)]
    (is hit?)
    (is (= 74.0 (:x p2)) "stops at tx*8 - w: 80 - 6"))
  (let [lv (-> (r/make-level "T" 20 :day 300 false) (r/ground 0 19) (r/row 5 12 "H"))
        p (assoc (r/new-player 50.0 93.0 :mona false) :vx -60.0)
        [p2 hit?] (r/move-x p 11 lv 0.1)]
    (is hit?)
    (is (= 48.0 (:x p2)) "stops at (tx+1)*8")))

(deftest move-y-lands-and-bumps
  (let [lv (-> (r/make-level "T" 20 :day 300 false) (r/ground 0 19) (r/row 4 8 "B"))
        p (assoc (r/new-player 33.0 80.0 :mona false) :vy 100.0)
        [p2 head] (r/move-y p 11 lv 0.2)]
    (is (nil? head))
    (is (= 93.0 (:y p2)) "lands with feet on row 13: 104 - 11")
    (is (:on-ground? p2))
    (is (= 0.0 (:vy p2))))
  (let [lv (-> (r/make-level "T" 20 :day 300 false) (r/ground 0 19) (r/row 4 8 "B"))
        p (assoc (r/new-player 33.0 76.0 :mona false) :vy -100.0)
        [p2 head] (r/move-y p 11 lv 0.1)]
    (is (= [[4] 8] head))
    (is (= 72.0 (:y p2)) "pushed back under the block: (ty+1)*8")))

(def dt (/ 1.0 30))

(defn- blank-level []
  (-> (r/make-level "TEST" 60 :day 300 false)
      (r/ground 0 59)
      (assoc :start [4 12])))

(defn- right [] (assoc r/no-controls :right true))

(defn- settle
  ([w n] (settle w n r/no-controls))
  ([w n controls] (reduce (fn [w _] (r/step w controls dt)) w (range n))))

(deftest held-jump-goes-higher
  (let [height (fn [hold?]
                 (let [w (settle (r/new-world (blank-level) (r/new-session)) 10)
                       ground (:y (:player w))
                       w (r/step w (assoc r/no-controls :jump true :jump-pressed true) dt)
                       c (assoc r/no-controls :jump hold?)
                       peak (loop [w w i 0 peak ground]
                              (if (= i 60) peak (let [w (r/step w c dt)] (recur w (inc i) (min peak (:y (:player w)))))))]
                   (- ground peak)))]
    (is (> (height true) (+ (height false) 8)))))

(deftest question-block-releases-chip-that-grows
  (let [lv (-> (blank-level) (r/block 5 9 :chip))
        w (settle (r/new-world lv (r/new-session)) 10)
        w (assoc-in w [:player :x] 41.0)
        w (r/step w (assoc r/no-controls :jump true :jump-pressed true) dt)
        w (settle w 30 (assoc r/no-controls :jump true))]
    (is (= r/t-used (r/get-tile (:level w) 5 9)))
    (is (some (fn [c] (= c [(r/index lv 5 9) r/t-used])) (:dirty w)) "the used block is in the dirty list")
    (is (= 1 (count (:items w))))
    (let [w (settle w 30)
          item (first (:items w))
          w (-> w (assoc-in [:player :x] (:x item)) (assoc-in [:player :y] (- (+ (:y item) (:h item)) 11)))
          w (r/step w r/no-controls dt)]
      (is (:big? (:player w)))
      (is (:big? (:session w))))))

(deftest brick-breaks-only-when-big
  (doseq [big? [false true]]
    (let [lv (-> (blank-level) (r/row 5 9 "B"))
          w (settle (r/new-world lv (assoc (r/new-session) :big? big?)) 10)
          w (assoc-in w [:player :x] 41.0)
          w (r/step w (assoc r/no-controls :jump true :jump-pressed true) dt)
          w (settle w 30 (assoc r/no-controls :jump true))]
      (is (= (if big? r/t-empty r/t-brick) (r/get-tile (:level w) 5 9)) (str "big? " big?)))))

(deftest inventory-and-character-switch
  (let [lv (assoc (blank-level) :switch? true)
        w (r/apply-item (r/new-world lv (assoc (r/new-session) :big? true)) :chip)
        _ (is (= [:chip] (:inventory (:session w))))
        [w used?] (r/use-item w 0)
        _ (is (false? used?) "a chip is not used while big")
        [w used?] (r/use-item (assoc-in w [:player :big?] false) 0)
        _ (is (true? used?))
        _ (is (:big? (:player w)))
        [w switched?] (r/switch-character w)]
    (is (true? switched?))
    (is (= :ducky (:character (:session w))))
    (is (false? (second (r/switch-character (assoc-in w [:level :switch?] false)))))))

(deftest hundred-stars-gives-life
  (let [[s _] (reduce (fn [[s _] _] (r/add-star s)) [(r/new-session) nil] (range 100))]
    (is (= [0 4] [(:stars s) (:lives s)]))))

(deftest camera-follows-right-only
  (let [w (settle (r/new-world (blank-level) (r/new-session)) 120 (assoc r/no-controls :right true :run true))
        ;; coast to a stop first: a skid out of run speed still carries the camera a few px
        w (settle w 30)
        cam (:cam-x w)]
    (is (> cam 0.0))
    (is (<= cam (:max-cam w)))
    (let [w2 (settle w 60 (assoc r/no-controls :left true :run true))]
      (is (= cam (:cam-x w2)) "walking back never scrolls back")
      (is (>= (:x (:player w2)) cam) "the player cannot leave the camera's left edge"))))

(deftest opposed-directions-cancel
  (let [w (settle (r/new-world (blank-level) (r/new-session)) 30 (assoc r/no-controls :left true :right true))]
    (is (= 0.0 (:vx (:player w))))))

(deftest held-jump-does-not-repeat
  (let [w (settle (r/new-world (blank-level) (r/new-session)) 10)
        w (r/step w (assoc r/no-controls :jump true :jump-pressed true) dt)
        w (settle (r/clear-events w) 120 (assoc r/no-controls :jump true))]
    (is (= 0 (count (filter (fn [e] (= e :jump)) (:events w)))) "no second jump while the button stays held")))

(deftest time-runs-out
  (let [lv (assoc (blank-level) :time 1)
        w (settle (r/new-world lv (r/new-session)) 30)]
    (is (= :dying (:state w)))
    (is (some (fn [e] (= e :die)) (:events w)))))

(deftest stomp-squashes-bug
  (let [lv (-> (blank-level) (r/enemy 8))
        w (r/new-world lv (r/new-session))
        e (first (:enemies w))
        w (-> w (assoc-in [:player :x] (:x e)) (assoc-in [:player :y] (- (:y e) 20)) (assoc-in [:player :vy] 60.0))
        w (settle w 8)]
    (is (= :squashed (:state (first (:enemies w)))))
    (is (pos? (:score (:session w))))
    (is (some (fn [e] (= e :stomp)) (:events w)))))

(deftest side-hit-shrinks-big-player-then-kills
  (let [lv (-> (blank-level) (r/enemy 9))
        w (settle (r/new-world lv (assoc (r/new-session) :big? true)) 2)
        w (settle w 40 (right))]
    (is (not (:big? (:player w))))
    (is (pos? (:invulnerable (:player w))))
    (is (= :play (:state w)))
    (let [p (:player w)
          e (first (:enemies w))
          w (-> w (assoc-in [:player :invulnerable] 0.0)
                (assoc-in [:enemies 0 :x] (+ (:x p) 2))
                (assoc-in [:enemies 0 :y] (- (+ (:y p) (r/player-h p)) (:h e))))
          w (r/step w r/no-controls dt)]
      (is (= :dying (:state w))))))

(deftest shell-pickup-and-throw
  (let [lv (-> (blank-level) (r/enemy 10 :shellbot))
        w (settle (r/new-world lv (r/new-session)) 2)
        w (-> w (assoc-in [:enemies 0 :state] :shell) (assoc-in [:enemies 0 :vx] 0.0))
        w (assoc-in w [:player :x] (- (:x (first (:enemies w))) 6 2))
        c (assoc r/no-controls :right true :run true)
        w (loop [w w i 0] (if (or (:carrying (:player w)) (= i 10)) w (recur (r/step w c dt) (inc i))))]
    (is (= 0 (:carrying (:player w))))
    (is (= :carried (:state (first (:enemies w)))))
    (let [w (r/step w r/no-controls dt)]
      (is (nil? (:carrying (:player w))))
      (is (= :kicked (:state (first (:enemies w)))))
      (is (pos? (:vx (first (:enemies w))))))))

(deftest kicked-shell-defeats-neighbours
  ;; three enemies: a bug behind, a shellbot, a bug ahead; stomp the shellbot into a
  ;; shell, kick it right, and the bug ahead dies; the ids and slots stay stable
  (let [lv (-> (blank-level) (r/enemy 12) (r/enemy 16 :shellbot) (r/enemy 20))
        w (settle (r/new-world lv (r/new-session)) 2)
        w (-> w (assoc-in [:enemies 1 :state] :shell) (assoc-in [:enemies 1 :vx] 0.0))
        w (assoc-in w [:player :x] (- (:x (get-in w [:enemies 1])) 6 2))
        w (settle w 30 (right))]
    (is (= :kicked (:state (get-in w [:enemies 1]))) "walking into a shell kicks it")
    (let [w (settle w 60)]
      (is (= [0 1 2] (mapv :id (:enemies w))) "ids are the slots")
      (is (= :dead (:state (get-in w [:enemies 2]))) "the bug ahead was hit by the shell"))))

(deftest dead-enemies-stay-out-of-play
  (let [lv (-> (blank-level) (r/enemy 8) (r/enemy 9))
        w (r/new-world lv (r/new-session))
        w (-> w (assoc-in [:enemies 0 :alive? ] false) (assoc-in [:enemies 0 :state] :dead))
        w (settle w 30)]
    (is (= 2 (count (:enemies w))) "the dead slot stays")
    (is (false? (:alive? (get-in w [:enemies 0]))))
    (is (= :walk (:state (get-in w [:enemies 1]))) "the live one walks on")
    (is (= :play (:state w)) "a dead slot cannot touch the player")))

(deftest falling-off-the-stage-kills
  (let [lv (-> (r/make-level "T" 40 :day 300 false) (r/ground 0 10))
        w (loop [w (r/new-world lv (r/new-session)) i 0]
            (if (or (= :dying (:state w)) (= i 300)) w (recur (r/step w (assoc r/no-controls :right true :run true) dt) (inc i))))]
    (is (= :dying (:state w)))
    (is (>= (:vy (:player w)) 0.0) "a fall death starts with vy 0, not the -170 hop")))

(deftest flag-ends-the-stage
  (let [lv (-> (blank-level) (r/flag 20))
        ;; the pole's base is a hard tile at row 12, so a walker stops at x 154 and the
        ;; flag is only reached airborne (as the badge's stages and autopilot do)
        w (-> (r/new-world lv (r/new-session)) (assoc-in [:player :x] 154.0) (settle 1))
        w (r/step (r/clear-events w) (assoc (right) :jump true :jump-pressed true) dt)
        w (settle w 20 (assoc (right) :jump true))]
    (is (= :flag (:state w)))
    (is (some (fn [e] (= e :flag)) (:events w)))
    (is (contains? #{100.0 400.0 800.0 2000.0 5000.0} (:flag-bonus w)) "a bonus from the height table")
    (let [w (loop [w w i 0] (if (or (= :clear (:state w)) (= i 900)) w (recur (r/step w r/no-controls dt) (inc i))))]
      (is (= :clear (:state w)))
      (is (= 0.0 (:time-left w)) "the tally spent the clock")
      (is (> (:score (:session w)) 100) "the tally added time points"))))

(deftest autopilot-clears-walls-and-gaps
  ;; stairs at 13, not 14: with 14 the badge's own bot lands 3 px short of the far edge and dies in the gap
  (let [lv (-> (r/make-level "T" 60 :day 300 false) (r/ground 0 20) (r/stairs 13 3) (r/ground 24 59) (r/row 40 12 "H") (r/flag 52))
        w (assoc (r/new-world lv (r/new-session)) :demo? true)
        w (loop [w w c r/no-controls i 0]
            (if (or (= :clear (:state w)) (= i 3600))
              w
              (let [c (r/autopilot w c)] (recur (r/step w c dt) c (inc i)))))]
    (is (= :clear (:state w)))))

(defn -main [& _]
  (let [res (run-tests 'mona.rules-test)]
    (System/exit (if (zero? (+ (:fail res 0) (:error res 0))) 0 1))))
