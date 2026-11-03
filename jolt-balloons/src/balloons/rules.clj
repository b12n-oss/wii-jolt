(ns balloons.rules
  "Balloon Pop rules, pure Clojure. A port of examples/balloons/game.c: every
   function takes a game map and returns a new one. Only live balloons,
   particles and popups are kept, in creation order."
  (:refer-clojure :exclude [update]))

(def screen-w 640)
(def screen-h 480)
(def max-players 4)
(def max-balloons 24)
(def max-particles 256)
(def max-popups 16)
(def round-seconds 60.0)
(def countdown-seconds 3.0)
(def results-lock-seconds 2.0)
(def attract-idle-seconds 8.0)
(def demo-seconds 20.0)
(def points-normal 1)
(def points-gold 5)
(def palette-size 6)

(def ^:private two-pi 6.2831853)

;; Random numbers. Wichmann-Hill (1982): three small LCGs whose products stay
;; below 172 * 30307, far under the 2^29 fixnum limit on the Wii. The state is
;; a vector [s1 s2 s3] with each component in [1, m-1].

(def ^:private m1 30269)
(def ^:private m2 30307)
(def ^:private m3 30323)

(defn rng
  "A generator state from any integer seed (negative and 0 included)."
  [seed]
  [(inc (mod (+ seed 11) (dec m1)))
   (inc (mod (+ seed 23) (dec m2)))
   (inc (mod (+ seed 37) (dec m3)))])

(defn rand-step
  "The next state. It is also the value to read with rand-unit."
  [[s1 s2 s3]]
  [(mod (* 171 s1) m1)
   (mod (* 172 s2) m2)
   (mod (* 170 s3) m3)])

(defn rand-unit
  "A double in [0, 1) from a state."
  [[s1 s2 s3]]
  (let [x (+ (/ (double s1) m1) (/ (double s2) m2) (/ (double s3) m3))]
    (- x (Math/floor x))))

(defn- draw
  "Step the game's generator. Returns [g' u] with u in [0, 1)."
  [g]
  (let [s (rand-step (:rng g))]
    [(assoc g :rng s) (rand-unit s)]))

(defn randf
  "Returns [g' x] with x in [lo, hi)."
  [g lo hi]
  (let [[g' u] (draw g)]
    [g' (+ lo (* (- hi lo) u))]))

(defn init [seed]
  {:mode :title
   :mode-time 0.0
   :spawn-timer 0.0
   :rng (rng seed)
   :balloons []
   :particles []
   :popups []
   :players (vec (repeat max-players
                         {:joined false :score 0 :x 320.0 :y 240.0 :aiming false
                          :rumble 0.0 :target nil :aim-delay 0.0}))
   :pops-this-round 0
   :rounds-played 0
   :next-id 0})

(defn spawn-balloon
  "Add one balloon below the screen, unless 24 are already up."
  [g]
  (if (>= (count (:balloons g)) max-balloons)
    g
    (let [[g u] (draw g)
          gold (< u (/ 1.0 12))
          [g r] (if gold [g 22.0] (randf g 24.0 36.0))
          [g x] (randf g (+ r 8) (- screen-w r 8))
          [g speed] (if gold (randf g 110.0 150.0) (randf g 50.0 105.0))
          [g wobble] (randf g 0.0 two-pi)
          [g cu] (draw g)
          b {:id (:next-id g) :x x :y (+ screen-h r) :r r :vy (- speed)
             :wobble wobble :color (int (* cu palette-size)) :gold gold}]
      (-> g
          (clojure.core/update :balloons conj b)
          (clojure.core/update :next-id inc)))))

(defn- burst
  "Up to count particles, fewer if the pool is full."
  [g x y color count]
  (loop [g g, i 0]
    (if (or (>= i count) (>= (clojure.core/count (:particles g)) max-particles))
      g
      (let [[g a] (randf g 0.0 two-pi)
            [g s] (randf g 60.0 260.0)
            [g life] (randf g 0.4 0.8)]
        (recur (clojure.core/update g :particles conj {:x x :y y
                                          :vx (* (Math/cos a) s) :vy (* (Math/sin a) s)
                                          :life life :color color})
               (inc i))))))

(defn- popup [g x y points player]
  (if (>= (count (:popups g)) max-popups)
    g
    (clojure.core/update g :popups conj {:x x :y y :life 0.8 :points points :player player})))

(defn try-pop
  "Pop the first live balloon under (x, y) for player. Returns [g' points],
   with points 0 and g unchanged when nothing is hit."
  [g player x y]
  (if-let [b (first (filter (fn [b]
                              (let [dx (- x (:x b)) dy (- y (:y b))]
                                (<= (+ (* dx dx) (* dy dy)) (* (:r b) (:r b)))))
                            (:balloons g)))]
    (let [gold (:gold b)
          points (if gold points-gold points-normal)]
      [(-> g
           (clojure.core/update :balloons (fn [bs] (filterv (fn [o] (not= (:id o) (:id b))) bs)))
           (update-in [:players player :score] + points)
           (assoc-in [:players player :rumble] (if gold 0.25 0.12))
           (clojure.core/update :pops-this-round inc)
           (burst (:x b) (:y b) (if gold -1 (:color b)) (if gold 28 16))
           (popup (:x b) (:y b) points player))
       points])
    [g 0]))

(defn- move-balloon [dt b]
  (let [wobble (+ (:wobble b) (* dt 2.0))
        y (+ (:y b) (* (:vy b) dt))
        x (+ (:x b) (* (Math/sin wobble) 18.0 dt))
        x (max (:r b) (min x (- screen-w (:r b))))]
    (assoc b :wobble wobble :y y :x x)))

(defn update-balloons
  "Spawn on the timer when spawn? is true, then move every balloon and drop
   the ones that have left through the top."
  [g dt spawn?]
  (let [g (if spawn?
            (let [t (- (:spawn-timer g) dt)]
              (if (<= t 0)
                (let [[g' next-t] (randf (spawn-balloon g) 0.35 0.8)]
                  (assoc g' :spawn-timer next-t))
                (assoc g :spawn-timer t)))
            g)]
    (clojure.core/update g :balloons
            (fn [bs]
              (into [] (comp (map (fn [b] (move-balloon dt b)))
                             (remove (fn [b] (< (+ (:y b) (:r b)) 0))))
                    bs)))))

(defn update-effects
  "Age particles and popups, and run down each player's rumble."
  [g dt]
  (-> g
      (clojure.core/update :particles
              (fn [ps]
                (into [] (comp (map (fn [p] (assoc p :life (- (:life p) dt))))
                               (filter (fn [p] (> (:life p) 0)))
                               (map (fn [p]
                                      (let [vy (+ (:vy p) (* 400.0 dt))]
                                        (assoc p :vy vy
                                               :x (+ (:x p) (* (:vx p) dt))
                                               :y (+ (:y p) (* vy dt)))))))
                      ps)))
      (clojure.core/update :popups
              (fn [us]
                (into [] (comp (map (fn [u] (assoc u :life (- (:life u) dt)
                                                   :y (- (:y u) (* 40.0 dt)))))
                               (filter (fn [u] (> (:life u) 0))))
                      us)))
      (clojure.core/update :players
              (fn [ps]
                (mapv (fn [p] (if (> (:rumble p) 0)
                                (assoc p :rumble (max 0.0 (- (:rumble p) dt)))
                                p))
                      ps)))))

;; Modes, demo bots and results. Mirrors game_update in game.c: the same
;; transitions and timers, with update-effects last.

(def demo-bots 2)
(def ^:private bot-speed 260.0)

(def no-input
  "Four players, none connected, nothing pressed."
  (vec (repeat max-players {:connected false :aiming false :x 0.0 :y 0.0 :fire false})))

(defn- reset-players [g]
  (assoc g :players
         (mapv (fn [p] (assoc p :joined false :score 0 :rumble 0.0 :target nil
                              :aim-delay 0.0 :x (/ screen-w 2.0) :y (/ screen-h 2.0)))
               (:players g))))

(defn- clear-board [g]
  (assoc g :balloons [] :popups [] :spawn-timer 0.0 :pops-this-round 0))

(defn- set-mode [g m]
  (assoc g :mode m :mode-time 0.0))

(defn- any-fire? [inputs]
  (boolean (some (fn [i] (and (:connected i) (:fire i))) inputs)))

(defn- track-pointers [g inputs]
  (assoc g :players
         (mapv (fn [p i]
                 (let [aiming (boolean (and (:connected i) (:aiming i)))]
                   (if aiming
                     (assoc p :aiming true :x (:x i) :y (:y i))
                     (assoc p :aiming false))))
               (:players g) inputs)))

(defn- join-on-fire [g inputs]
  (assoc g :players
         (mapv (fn [p i] (if (and (:connected i) (:fire i)) (assoc p :joined true) p))
               (:players g) inputs)))

(defn- find-balloon [g id]
  (first (filter (fn [b] (= (:id b) id)) (:balloons g))))

(defn- nearest-balloon
  "Id of the closest balloon that is fully on screen, or nil."
  [g x y]
  (let [cands (filter (fn [b] (not (or (> (:y b) (* screen-h 0.66)) (< (:y b) (:r b)))))
                      (:balloons g))
        d2 (fn [b] (let [dx (- (:x b) x) dy (- (:y b) y)] (+ (* dx dx) (* dy dy))))]
    (when (seq cands)
      (:id (reduce (fn [best b] (if (< (d2 b) (d2 best)) b best)) cands)))))

(defn- update-bot [g p dt]
  (let [g (assoc-in g [:players p :aiming] true)
        pl (get-in g [:players p])
        dead? (or (nil? (:target pl)) (nil? (find-balloon g (:target pl))))
        [g target delay] (if dead?
                           (let [[g' d] (randf g 0.6 1.4)]
                             [g' (nearest-balloon g (:x pl) (:y pl)) d])
                           [g (:target pl) (:aim-delay pl)])
        b (when target (find-balloon g target))]
    (if (nil? b)
      (assoc-in (assoc-in g [:players p :target] nil) [:players p :aim-delay] delay)
      (let [dx (- (:x b) (:x pl)) dy (- (:y b) (:y pl))
            d (Math/sqrt (+ (* dx dx) (* dy dy)))
            step (* bot-speed dt)
            [x y] (if (> d step)
                    [(+ (:x pl) (* (/ dx d) step)) (+ (:y pl) (* (/ dy d) step))]
                    [(:x b) (:y b)])
            delay (- delay dt)
            g (update-in g [:players p] assoc :target target :aim-delay delay :x x :y y)]
        (if (and (< d (* (:r b) 0.6)) (<= delay 0))
          (-> (first (try-pop g p x y))
              (assoc-in [:players p :target] nil))
          g)))))

(defn- update-bots [g dt]
  (reduce (fn [g p] (update-bot g p dt)) g (range demo-bots)))

(defn update
  "Advance the game by dt seconds. inputs is a vector of 4 maps
   {:connected :aiming :x :y :fire}. In :demo the bots play and any fire
   returns to the title."
  [g inputs dt]
  (let [g (clojure.core/update g :mode-time + dt)
        g (case (:mode g)
            :title
            (let [g (-> g (track-pointers inputs) (update-balloons dt true))]
              (cond
                (any-fire? inputs)
                (-> g reset-players (join-on-fire inputs) (set-mode :countdown))

                (>= (:mode-time g) attract-idle-seconds)
                (let [g (-> g reset-players clear-board)]
                  (-> g
                      (assoc :players (vec (map-indexed
                                            (fn [i p] (if (< i demo-bots) (assoc p :joined true) p))
                                            (:players g))))
                      (set-mode :demo)))

                :else g))

            :countdown
            (let [g (-> g (track-pointers inputs) (join-on-fire inputs) (update-balloons dt false))]
              (if (>= (:mode-time g) countdown-seconds)
                (-> g clear-board (set-mode :play))
                g))

            :play
            (let [g (track-pointers g inputs)
                  g (reduce (fn [g p]
                              (let [i (nth inputs p)]
                                (if (and (get-in g [:players p :joined])
                                         (:connected i) (:aiming i) (:fire i))
                                  (first (try-pop g p (:x i) (:y i)))
                                  g)))
                            g (range max-players))
                  g (update-balloons g dt true)]
              (if (>= (:mode-time g) round-seconds)
                (-> g (clojure.core/update :rounds-played inc) (set-mode :results))
                g))

            :results
            (let [g (-> g (track-pointers inputs) (update-balloons dt false))]
              (if (and (>= (:mode-time g) results-lock-seconds) (any-fire? inputs))
                (-> g reset-players clear-board (set-mode :title))
                g))

            :demo
            (let [g (-> g (update-bots dt) (update-balloons dt true))]
              (if (or (any-fire? inputs) (>= (:mode-time g) demo-seconds))
                (-> g reset-players clear-board (set-mode :title))
                g)))]
    (update-effects g dt)))

(defn joined-count [g]
  (count (filter :joined (:players g))))

(defn winner
  "Index of the joined player with the top score (lowest index on a tie), or nil."
  [g]
  (reduce (fn [best p]
            (cond
              (not (get-in g [:players p :joined])) best
              (or (nil? best) (> (get-in g [:players p :score]) (get-in g [:players best :score]))) p
              :else best))
          nil (range max-players)))

(defn tie? [g]
  (if-let [w (winner g)]
    (let [ps (:players g)]
      (boolean (some (fn [p] (and (not= p w) (:joined (nth ps p))
                                  (= (:score (nth ps p)) (:score (nth ps w)))))
                     (range max-players))))
    false))

(defn time-left [g]
  (case (:mode g)
    :play (- round-seconds (:mode-time g))
    :demo (- demo-seconds (:mode-time g))
    :countdown (- countdown-seconds (:mode-time g))
    0.0))
