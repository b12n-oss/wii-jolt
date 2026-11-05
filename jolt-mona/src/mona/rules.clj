(ns mona.rules
  "Super Mona rules, pure Clojure. A port of super_mona/game.py from the badge
   game. It holds the constants, the records and the Level builders, the
   bodies and the world step. The only in-place structure is a level's tile
   byte array, which set-tile! mutates.")

;; Constants

(def tile 8)
(def rows 15)
(def view-w 160)
(def view-h 120)
(def max-fall 210)
(def small-h 11)
(def big-h 15)
(def stomp-bounce 120)
(def shell-speed 135)
(def inventory-size 3)
(def item-names {:chip "POWER CHIP" :sparkle "SPARKLE" :1up "1-UP"})

(def t-empty 0)
(def t-ground-top 1)
(def t-ground 2)
(def t-brick 3)
(def t-question 4)
(def t-used 5)
(def t-hard 6)
(def t-pipe-tl 7)
(def t-pipe-tr 8)
(def t-pipe-l 9)
(def t-pipe-r 10)
(def t-pole 11)
(def t-pole-top 12)
(def t-girder 13)
(def t-cave-ground 14)
(def t-cave-brick 15)
(def t-hill 16)
(def t-bush 22)
(def t-cloud 25)
(def t-castle 31)

(def dark-themes #{:cave :castle})

(def ^:private solid-tiles
  (let [ids [t-ground-top t-ground t-brick t-question t-used t-hard t-pipe-tl
             t-pipe-tr t-pipe-l t-pipe-r t-girder t-cave-ground t-cave-brick]]
    (vec (map (fn [i] (boolean (some (fn [t] (= t i)) ids))) (range 64)))))

(def breakable? #{t-brick t-cave-brick})

(def characters
  {:mona {:name "MONA" :walk 50 :run 82 :accel 190 :decel 170 :skid 420
          :jump 152 :run-jump 168 :grav-hold 300 :grav 900
          :about "Balanced grip"}
   :ducky {:name "DUCKY" :walk 50 :run 78 :accel 150 :decel 100 :skid 300
           :jump 166 :run-jump 180 :grav-hold 280 :grav 820
           :about "Floaty jumps, slippery"}})

(def character-order [:mona :ducky])

;; Records

(defrecord Level [name width theme time switch? ^bytes tiles decor contents stars spawns start flag-x castle-door])
;; decor {index tile-id}, contents {index :star|:chip|:sparkle|:1up|[:multi n]}, stars #{[tx ty]},
;; spawns [[tx ty kind]], start [tx ty], flag-x nil or column, castle-door nil or game px

(defrecord Player [^double x ^double y ^double vx ^double vy ^long w on-ground? character big? ^long facing crouch?
                   ^double jump-buffer ^double coyote ^double invulnerable ^double sparkle carrying ^long stomp-chain
                   ^double walk-phase skidding? dead?])

(defrecord Enemy [^long id ^double x ^double y ^double vx ^double vy ^long w ^long h kind state ^double timer active? ^double grace alive?])

(defrecord Item [^double x ^double y ^double vx ^double vy ^long w ^long h kind ^double emerge ^double target-y alive?])

(defrecord World [level session player enemies items particles bumps ^double cam-x ^double time-left ^double elapsed
                  state ^double state-time flag-y ^double flag-bonus ^double max-cam demo? events dirty controls-run? controls-jump?])

;; Level access

(defn make-level
  "An empty level of `width` columns."
  [name width theme time switch?]
  (->Level name width theme time switch? (byte-array (* width rows))
           {} {} #{} [] [2 12] nil nil))

(defn index
  "The tile array index of column tx, row ty."
  [level tx ty]
  (+ (* ty (long (:width level))) tx))

(defn get-tile
  "The tile id at (tx, ty): t-hard left or right of the level, t-empty above or below."
  [level tx ty]
  (cond
    (or (< tx 0) (>= tx (long (:width level)))) t-hard
    (or (< ty 0) (>= ty rows)) t-empty
    :else (bit-and (aget ^bytes (:tiles level) (index level tx ty)) 0xFF)))

(defn set-tile!
  "Write a tile in place and return the level. A no-op outside the grid or
   when the tile is unchanged."
  [level tx ty tile-id]
  (when (and (<= 0 tx) (< tx (long (:width level))) (<= 0 ty) (< ty rows))
    (let [i (index level tx ty)
          arr ^bytes (:tiles level)]
      (when (not= (bit-and (aget arr i) 0xFF) tile-id)
        (aset arr i (unchecked-byte tile-id)))))
  level)

(defn solid?
  "True when the tile at (tx, ty) blocks movement."
  [level tx ty]
  (nth solid-tiles (get-tile level tx ty)))

;; Builders

(defn- brick-tile [level]
  (if (contains? dark-themes (:theme level)) t-cave-brick t-brick))

(defn ground
  "Fill columns x0..x1 from row `top` (default 13) down with ground."
  ([level x0 x1] (ground level x0 x1 13))
  ([level x0 x1 top]
   (let [cave? (contains? dark-themes (:theme level))]
     (doseq [x (range x0 (inc x1))
             y (range top rows)]
       (set-tile! level x y (cond
                              cave? t-cave-ground
                              (= y top) t-ground-top
                              :else t-ground))))
   level))

(defn ceiling
  "Cave bricks along columns x0..x1 on each of `ys` (default row 2)."
  ([level x0 x1] (ceiling level x0 x1 [2]))
  ([level x0 x1 ys]
   (doseq [x (range x0 (inc x1))
           y ys]
     (set-tile! level x y t-cave-brick))
   level))

(defn row
  "Place blocks from a string at row y starting at column x: B brick, Q
   question (with `content`, default :star), H hard, = girder, o floating
   star, - nothing."
  ([level x y text] (row level x y text nil))
  ([level x y text content]
   (reduce
    (fn [lv [i ch]]
      (let [tx (+ x i)]
        (case ch
          \B (set-tile! lv tx y (brick-tile lv))
          \Q (-> (set-tile! lv tx y t-question)
                 (assoc :contents (assoc (:contents lv) (index lv tx y) (or content :star))))
          \H (set-tile! lv tx y t-hard)
          \= (set-tile! lv tx y t-girder)
          \o (assoc lv :stars (conj (:stars lv) [tx y]))
          lv)))
    level
    (map-indexed vector text))))

(defn block
  "A question block (or a brick when `brick?`) holding `content`."
  ([level x y content] (block level x y content false))
  ([level x y content brick?]
   (set-tile! level x y (if brick? (brick-tile level) t-question))
   (assoc level :contents (assoc (:contents level) (index level x y) content))))

(defn star-line
  "`n` floating stars in a row from (x, y)."
  [level x y n]
  (assoc level :stars (into (:stars level) (map (fn [i] [(+ x i) y]) (range n)))))

(defn pipe
  "A pipe two columns wide and `height` tall standing on row `base` (default 13)."
  ([level x height] (pipe level x height 13))
  ([level x height base]
   (let [top (- base height)]
     (set-tile! level x top t-pipe-tl)
     (set-tile! level (inc x) top t-pipe-tr)
     (doseq [y (range (inc top) base)]
       (set-tile! level x y t-pipe-l)
       (set-tile! level (inc x) y t-pipe-r))
     level)))

(defn stairs
  "Hard-block stairs `height` columns wide, rising to the right when `up?`."
  ([level x height] (stairs level x height true 13))
  ([level x height up? base]
   (doseq [i (range height)
           j (range (if up? (inc i) (- height i)))]
     (set-tile! level (+ x i) (- base 1 j) t-hard))
   level))

(defn enemy
  "Add a spawn at column x, row y (default 12) of kind :bug or :shellbot."
  ([level x] (enemy level x :bug 12))
  ([level x kind] (enemy level x kind 12))
  ([level x kind y]
   (assoc level :spawns (conj (:spawns level) [x y kind]))))

(defn- decor-block
  "Add `n` decor tiles starting at `first-id`, `cols` wide, from (x, y)."
  [level x y n cols first-id]
  (assoc level :decor
         (reduce (fn [d i]
                   (assoc d (index level (+ x (rem i cols)) (+ y (quot i cols)))
                          (+ first-id i)))
                 (:decor level)
                 (range n))))

(defn hill
  "A six-tile hill, three wide and two tall, from row 11."
  [level x]
  (decor-block level x 11 6 3 t-hill))

(defn bush
  "A three-tile bush on the row above ground."
  [level x]
  (decor-block level x 12 3 3 t-bush))

(defn cloud
  "A six-tile cloud with its top left corner at (x, y)."
  [level x y]
  (decor-block level x y 6 3 t-cloud))

(defn flag
  "A flag pole at column x and the castle five columns to its right."
  [level x]
  (set-tile! level x 12 t-hard)
  (set-tile! level x 3 t-pole-top)
  (doseq [y (range 4 12)]
    (set-tile! level x y t-pole))
  (let [castle-x (+ x 5)
        width (long (:width level))
        decor (reduce (fn [d i]
                        (let [tx (+ castle-x (rem i 5))
                              ty (+ 8 (quot i 5))]
                          (if (and (<= 0 tx) (< tx width))
                            (assoc d (index level tx ty) (+ t-castle i))
                            d)))
                      (:decor level)
                      (range 25))]
    (assoc level :flag-x x :decor decor :castle-door (+ (* castle-x tile) 18))))

;; Bodies

(defn tile-of
  "The tile column or row holding game pixel `v` (floor, so negatives round down)."
  [v]
  (long (Math/floor (/ (double v) 8.0))))

(defn overlap?
  "True when the boxes (ax, ay, aw, ah) and (bx, by, bw, bh) overlap."
  [ax ay aw ah bx by bw bh]
  (and (< ax (+ bx bw)) (< bx (+ ax aw))
       (< ay (+ by bh)) (< by (+ ay ah))))

(defn player-h
  "The player's height: big-h when big, else small-h."
  [player]
  (if (:big? player) big-h small-h))

(defn new-player
  "A player at (x, y) of `character`, small or `big?`."
  [x y character big?]
  (->Player (double x) (double y) 0.0 0.0 6 false character (boolean big?) 1 false
            0.0 0.0 0.0 0.0 nil 0
            0.0 false false))

(defn new-enemy
  "An enemy with id `id` at (x, y) of `kind`, walking left and not yet active."
  [id x y kind]
  (->Enemy id (double x) (double y) -18.0 0.0 6 7 kind :walk 0.0 false 0.0 true))

(defn new-item
  "An item at (x, y) of `kind`, about to rise one tile out of its block."
  [x y kind]
  (->Item (double x) (double y) 0.0 0.0 8 8 kind 0.5 (double (- y 8)) true))

(defn move-x
  "Move `body` (height `h`) along x by vx*dt and stop at a solid tile.
   Returns [body hit?]."
  [body h level dt]
  (let [h (double h)
        vx (double (:vx body))
        w (long (:w body))
        x (+ (double (:x body)) (* vx (double dt)))
        y (double (:y body))
        top (tile-of y)
        bottom (tile-of (- (+ y h) 0.01))]
    (cond
      (> vx 0.0)
      (let [tx (tile-of (- (+ x w) 0.01))]
        (if (some (fn [ty] (solid? level tx ty)) (range top (inc bottom)))
          [(assoc body :x (double (- (* tx tile) w))) true]
          [(assoc body :x x) false]))

      (< vx 0.0)
      (let [tx (tile-of x)]
        (if (some (fn [ty] (solid? level tx ty)) (range top (inc bottom)))
          [(assoc body :x (double (* (inc tx) tile))) true]
          [(assoc body :x x) false]))

      :else [(assoc body :x x) false])))

(defn move-y
  "Move `body` (height `h`) along y by vy*dt, landing on or bumping into solid
   tiles. Returns [body head] where head is nil or [hit-columns row]."
  [body h level dt]
  (let [h (double h)
        vy (double (:vy body))
        w (long (:w body))
        dy (* vy (double dt))
        y (+ (double (:y body)) dy)
        x (double (:x body))
        left (tile-of x)
        right (tile-of (- (+ x w) 0.01))
        moved (assoc body :y y :on-ground? false)]
    (if (>= vy 0.0)
      (let [ty (tile-of (- (+ y h) 0.01))
            land? (<= (- (+ y h) dy) (+ (* ty tile) 0.5 (Math/abs dy)))]
        (if (and land? (some (fn [tx] (solid? level tx ty)) (range left (inc right))))
          [(assoc moved :y (double (- (* ty tile) h)) :vy 0.0 :on-ground? true) nil]
          [moved nil]))
      (let [ty (tile-of y)
            hits (filterv (fn [tx] (solid? level tx ty)) (range left (inc right)))]
        (if (seq hits)
          [(assoc moved :y (double (* (inc ty) tile)) :vy 0.0) [hits ty]]
          [moved nil])))))

;; Session

(def no-controls
  {:left false :right false :up false :down false :jump false :jump-pressed false :run false})

(defn new-session
  "A fresh session: no score, three lives, Mona, small, empty inventory."
  []
  {:score 0 :stars 0 :lives 3 :level 0 :character :mona :big? false :inventory [] :high 0})

(defn add-score
  "Add `points`, raising the high score when passed."
  [session points]
  (let [score (+ (long (:score session)) (long points))]
    (assoc session :score score :high (max (long (:high session)) score))))

(defn add-star
  "Count a star worth 200 points; the 100th gives a life. Returns [session events]."
  [session]
  (let [s (-> session (add-score 200) (update :stars inc))]
    (if (>= (long (:stars s)) 100)
      [(-> s (update :stars - 100) (update :lives inc)) [:star :1up]]
      [s [:star]])))

(defn store
  "Put `kind` in the inventory when there is room. Returns [session stored?]."
  [session kind]
  (if (< (count (:inventory session)) inventory-size)
    [(update session :inventory conj kind) true]
    [session false]))

;; World

(defn new-world
  "A world at the start of `level` for `session`."
  [level session]
  (let [[sx sy] (:start level)
        big? (boolean (:big? session))
        player (new-player (+ (* sx tile) 1) 0 (:character session) big?)
        player (assoc player :y (double (- (* (inc sy) tile) (player-h player))))
        enemies (vec (map-indexed (fn [i [x y kind]]
                                    (new-enemy i (+ (* x tile) 1) (- (* (inc y) tile) 7) kind))
                                  (:spawns level)))]
    (->World level session player enemies [] [] [] 0.0 (double (:time level)) 0.0
             :play 0.0
             (when (:flag-x level) (double (* (- rows 12) tile)))
             0.0 (double (max 0 (- (* (long (:width level)) tile) view-w))) false [] [] false false)))

(defn clear-events
  "The world with its events and dirty lists emptied."
  [world]
  (assoc world :events [] :dirty []))

(defn- add-event [world event]
  (update world :events conj event))

(defn- add-points [world points]
  (update world :session add-score points))

(defn- add-world-star [world]
  (let [[session events] (add-star (:session world))]
    (-> world (assoc :session session) (update :events into events))))

(defn- set-tile-dirty
  "set-tile! on the world's level, noting the change in :dirty."
  [world tx ty tile-id]
  (let [level (:level world)]
    (set-tile! level tx ty tile-id)
    (update world :dirty conj [(index level tx ty) tile-id])))

(defn- update-enemy
  "Apply `f` to enemy `id`. An enemy's id is its index in :enemies for the
   stage's life (dead ones keep their slot, with :alive? false)."
  [world id f]
  (assoc-in world [:enemies id] (f (nth (:enemies world) id))))

(defn- find-enemy [world id]
  (when id
    (nth (:enemies world) id nil)))

;; Particles and camera

(defn- spawn-particle
  ([world kind x y vx vy] (spawn-particle world kind x y vx vy nil))
  ([world kind x y vx vy data]
   (update world :particles conj [kind (double x) (double y) (double vx) (double vy) 0.0 data])))

(defn- advance-particle [[kind x y vx vy age data] dt]
  (let [dt (double dt)
        vy (double (case kind :debris (+ (double vy) (* 500.0 dt)) :star (+ (double vy) (* 520.0 dt)) vy))]
    [kind (+ (double x) (* (double vx) dt)) (+ (double y) (* vy dt)) vx vy (+ (double age) dt) data]))

(defn- particle-life [kind]
  (case kind :star 0.6 :score 0.8 :sparkle 0.35 1.2))

(defn update-particles
  "Age and move particles and bumps, dropping the expired ones."
  [world dt]
  (let [dt (double dt)]
    (assoc world
           :particles (into [] (comp (map (fn [part] (advance-particle part dt)))
                                     (filter (fn [part] (< (double (nth part 5)) (double (particle-life (nth part 0)))))))
                            (:particles world))
           :bumps (into [] (comp (map (fn [[tx ty tl age]] [tx ty tl (+ (double age) dt)]))
                                 (filter (fn [bump] (< (double (nth bump 3)) 0.16))))
                        (:bumps world)))))

(defn update-camera
  "Scroll right to keep the player at x 64 on screen; never scroll back."
  [world]
  (let [target (- (double (:x (:player world))) 64.0)]
    (if (> target (double (:cam-x world)))
      (assoc world :cam-x (double (min target (double (:max-cam world)))))
      world)))

;; Player

(defn defeat-enemy
  "Knock enemy `id` out: dead, thrown up, `points` scored with a score particle."
  [world id points]
  (let [e (find-enemy world id)]
    (-> world
        (update-enemy id (fn [e] (assoc e :state :dead :vy -120.0
                                        :vx (if (>= (double (:vx e)) 0.0) 30.0 -30.0))))
        (add-points points)
        (spawn-particle :score (:x e) (- (double (:y e)) 4.0) 0 -30 points)
        (add-event :defeat))))

(defn grow
  "Make the player big, lifting it so the feet stay put."
  [world]
  (let [p (:player world)]
    (if (:big? p)
      world
      (-> world
          (assoc :player (assoc p :big? true :y (- (double (:y p)) (double (- big-h small-h)))))
          (assoc-in [:session :big?] true)
          (add-event :grow)))))

(defn apply-item
  "Apply an item. A chip grows a small player; a big one stores it (or, from
   the inventory, scores it)."
  ([world kind] (apply-item world kind false))
  ([world kind from-inventory?]
   (case kind
     :chip (if (:big? (:player world))
             (let [[session stored?] (if from-inventory?
                                       [(:session world) false]
                                       (store (:session world) :chip))]
               (if stored?
                 (-> world (assoc :session session) (add-event :stored))
                 (add-points world 1000)))
             (-> world grow (add-points 1000)))
     :sparkle (-> world (assoc-in [:player :sparkle] 8.0) (add-points 1000) (add-event :sparkle))
     :1up (-> world (update-in [:session :lives] inc) (add-event :1up))
     world)))

(defn kill-player
  "Start the death sequence unless already dying. `fell?` skips the hop."
  ([world] (kill-player world false))
  ([world fell?]
   (if (not= :play (:state world))
     world
     (-> world
         (update :player assoc :dead? true :big? false :vx 0.0 :vy (if fell? 0.0 -170.0) :carrying nil)
         (assoc-in [:session :big?] false)
         (assoc :state :dying :state-time 0.0)
         (add-event :die)))))

(defn hurt-player
  "A harmful touch: drop a carried shell, shrink a big player, else die."
  [world]
  (let [p (:player world)]
    (if (or (> (double (:invulnerable p)) 0.0) (> (double (:sparkle p)) 0.0)
            (:demo? world) (not= :play (:state world)))
      world
      (let [world (if-let [id (:carrying p)]
                    (-> world
                        (update-enemy id (fn [e] (assoc e :state :shell :timer 0.0)))
                        (assoc-in [:player :carrying] nil))
                    world)
            p (:player world)]
        (if (:big? p)
          (-> world
              (assoc :player (assoc p :big? false :y (+ (double (:y p)) (double (- big-h small-h))) :invulnerable 2.0))
              (assoc-in [:session :big?] false)
              (add-event :shrink))
          (kill-player world))))))

(defn use-item
  "Use inventory slot `slot` while playing. Returns [world used?]."
  [world slot]
  (let [inv (:inventory (:session world))]
    (if (or (>= slot (count inv)) (not= :play (:state world)))
      [world false]
      (let [kind (nth inv slot)]
        (if (and (= kind :chip) (:big? (:player world)))
          [world false]
          [(-> world
               (assoc-in [:session :inventory] (into (subvec inv 0 slot) (subvec inv (inc slot))))
               (apply-item kind true))
           true])))))

(defn switch-character
  "Cycle to the next character where the level allows it. Returns [world switched?]."
  [world]
  (if-not (:switch? (:level world))
    [world false]
    (let [i (.indexOf ^java.util.List character-order (:character (:player world)))
          nxt (nth character-order (rem (inc i) (count character-order)))]
      [(-> world
           (assoc-in [:player :character] nxt)
           (assoc-in [:session :character] nxt)
           (add-event :switch))
       true])))

;; Blocks

(defn- add-bump [world tx ty tile-id]
  (update world :bumps conj [tx ty tile-id 0.0]))

(defn- pop-star [world tx ty]
  (-> world
      add-world-star
      (spawn-particle :star (* tx tile) (- (* ty tile) tile) 0 -160)))

(defn- hit-from-below
  "Kick the enemies and items standing on the block at (tx, ty)."
  [world tx ty]
  (let [x0 (* tx tile)
        top (* ty tile)
        world (reduce
               (fn [w e]
                 (if (and (:alive? e) (contains? #{:walk :shell} (:state e))
                          (< (Math/abs (- (+ (double (:y e)) (long (:h e))) top)) 2.0)
                          (overlap? (:x e) 0 (:w e) 1 x0 0 tile 1))
                   (defeat-enemy w (:id e) 100)
                   w))
               world
               (:enemies world))]
    (update world :items
            (fn [items]
              (mapv (fn [item]
                      (if (and (<= (double (:emerge item)) 0.0)
                               (< (Math/abs (- (+ (double (:y item)) (long (:h item))) top)) 2.0)
                               (overlap? (:x item) 0 (:w item) 1 x0 0 tile 1))
                        (let [v (Math/abs (double (:vx item)))
                              v (if (zero? v) 40.0 v)]
                          (assoc item :vy -110.0
                                 :vx (if (> (+ (double (:x item)) 4.0) (+ x0 4.0)) v (- v))))
                        item))
                    items)))))

(defn bump
  "The player's head hits the block at (tx, ty)."
  [world tx ty]
  (let [level (:level world)
        tl (get-tile level tx ty)
        idx (index level tx ty)
        content (get (:contents level) idx)]
    (cond
      (vector? content)
      (let [remaining (dec (long (second content)))
            world (pop-star world tx ty)]
        (-> (if (pos? remaining)
              (-> world
                  (update :level assoc-in [:contents idx] [:multi remaining])
                  (add-bump tx ty tl))
              (-> world
                  (update :level update :contents dissoc idx)
                  (set-tile-dirty tx ty t-used)
                  (add-bump tx ty t-used)))
            (hit-from-below tx ty)))

      (some? content)
      (let [world (-> world
                      (update :level update :contents dissoc idx)
                      (set-tile-dirty tx ty t-used)
                      (add-bump tx ty t-used))]
        (-> (if (= content :star)
              (pop-star world tx ty)
              (-> world
                  (update :items conj (new-item (* tx tile) (* ty tile) content))
                  (add-event :item)))
            (hit-from-below tx ty)))

      (contains? breakable? tl)
      (-> (if (:big? (:player world))
            (-> (reduce (fn [w [vx vy]] (spawn-particle w :debris (+ (* tx tile) 2) (+ (* ty tile) 2) vx vy))
                        (set-tile-dirty world tx ty t-empty)
                        [[-40 -150] [40 -150] [-30 -90] [30 -90]])
                (add-points 50)
                (add-event :break))
            (-> world (add-bump tx ty tl) (add-event :bump)))
          (hit-from-below tx ty))

      :else (add-event world :bump))))

;; Items

(defn- update-item
  "One item's own motion. Returns the item (alive? false once gone)."
  [item level cam-x dt]
  (let [dt (double dt)
        emerge (double (:emerge item))]
    (if (> emerge 0.0)
      (let [emerge (- emerge dt)
            item (assoc item :emerge emerge
                        :y (+ (double (:target-y item)) (/ (* tile (max 0.0 emerge)) 0.5)))]
        (if (<= emerge 0.0)
          (let [sparkle? (= :sparkle (:kind item))]
            (cond-> (assoc item :y (double (:target-y item)) :vx (if sparkle? 55.0 40.0))
              sparkle? (assoc :vy -120.0)))
          item))
      (let [item (assoc item :vy (min (+ (double (:vy item)) (* 600.0 dt)) (double max-fall)))
            [item hit?] (move-x item (:h item) level dt)
            item (if hit? (assoc item :vx (- (double (:vx item)))) item)
            [item _] (move-y item (:h item) level dt)
            item (if (and (:on-ground? item) (= :sparkle (:kind item))) (assoc item :vy -130.0) item)]
        (if (or (> (double (:y item)) (+ view-h 16)) (< (double (:x item)) (- (double cam-x) 32.0)))
          (assoc item :alive? false)
          item)))))

(defn update-items
  "Move the items and let the player pick them up."
  [world dt]
  (let [dt (double dt)
        world (reduce
               (fn [w item]
                 (if (> (double (:emerge item)) 0.0)
                   (update w :items conj (update-item item (:level w) (:cam-x w) dt))
                   (let [item (update-item item (:level w) (:cam-x w) dt)
                         p (:player w)
                         touched? (overlap? (:x p) (:y p) (:w p) (player-h p)
                                            (:x item) (:y item) (:w item) (:h item))
                         item (if touched? (assoc item :alive? false) item)
                         w (update w :items conj item)]
                     (if touched? (apply-item w (:kind item)) w))))
               (assoc world :items [])
               (:items world))]
    (update world :items (fn [items] (filterv :alive? items)))))

;; Player update

(defn- body-in-wall? [level body]
  (let [x (double (:x body)) y (double (:y body))]
    (boolean (some (fn [tx] (some (fn [ty] (solid? level tx ty))
                                  (range (tile-of y) (inc (tile-of (- (+ y (long (:h body))) 0.01))))))
                   (range (tile-of x) (inc (tile-of (- (+ x (long (:w body))) 0.01))))))))

(defn throw-shell
  "Let go of the carried shell as a kicked one."
  [world]
  (let [p (:player world)
        id (:carrying p)
        world (assoc-in world [:player :carrying] nil)
        shell (find-enemy world id)]
    (if-not shell
      world
      (let [facing (long (:facing p))
            py (double (:y p)) px (double (:x p))
            sy (- (+ py (player-h p)) (long (:h shell)))
            sx (+ px (if (> facing 0) (long (:w p)) (- (long (:w shell)))))
            kicked (assoc shell :state :kicked :vx (double (* facing shell-speed)) :vy 0.0
                          :grace 0.25 :y sy :x sx)
            in-wall? (body-in-wall? (:level world) kicked)
            kicked (if in-wall? (assoc kicked :x px) kicked)
            stuck? (and in-wall? (body-in-wall? (:level world) kicked))]
        (cond-> (update-enemy world id (fn [_] kicked))
          stuck? (defeat-enemy id 200)
          true (add-event :kick))))))

(defn- update-carry [world c]
  (let [p (:player world)]
    (if-let [id (:carrying p)]
      (let [shell (find-enemy world id)]
        (if (or (nil? shell) (not (:alive? shell)) (not= :carried (:state shell)))
          (assoc-in world [:player :carrying] nil)
          (let [world (update-enemy world id
                                    (fn [e] (assoc e
                                                   :x (+ (double (:x p)) (* (long (:facing p)) 5))
                                                   :y (- (+ (double (:y p)) (player-h p)) (long (:h e)) (if (:big? p) 4 1)))))]
            (if (:run c) world (throw-shell world)))))
      world)))

(defn- collect-stars [world]
  (let [p (:player world)
        stars (:stars (:level world))]
    (if (empty? stars)
      world
      (let [x (double (:x p)) y (double (:y p))
            txs (range (tile-of x) (inc (tile-of (- (+ x (long (:w p))) 0.01))))
            tys (range (tile-of y) (inc (tile-of (- (+ y (player-h p)) 0.01))))]
        (reduce (fn [w tx]
                  (reduce (fn [w ty]
                            (let [cell [tx ty]]
                              (if (contains? (:stars (:level w)) cell)
                                (-> w
                                    (update :level update :stars disj cell)
                                    add-world-star
                                    (spawn-particle :sparkle (* tx tile) (* ty tile) 0 0))
                                w)))
                          w
                          tys))
                world
                txs)))))

(defn- steer
  "Timers, direction, acceleration, skid and facing for one frame."
  [p s c dt]
  (let [dt (double dt)
        dir (- (if (:right c) 1 0) (if (:left c) 1 0))
        crouch? (boolean (and (:big? p) (:down c) (:on-ground? p)))
        dir (if crouch? 0 dir)
        ground? (:on-ground? p)
        top (double (if (:run c) (:run s) (:walk s)))
        vx (double (:vx p))
        p (assoc p :crouch? crouch?
                 :invulnerable (if (> (double (:invulnerable p)) 0.0) (- (double (:invulnerable p)) dt) (:invulnerable p))
                 :sparkle (if (> (double (:sparkle p)) 0.0) (- (double (:sparkle p)) dt) (:sparkle p)))]
    (if (zero? dir)
      (let [slow (* (double (:decel s)) dt (if ground? 1.0 0.4))]
        (assoc p :skidding? false
               :vx (cond (<= (Math/abs vx) slow) 0.0 (> vx 0.0) (- vx slow) :else (+ vx slow))))
      (let [d (double dir)
            [nvx skid?] (cond
                          (< (* vx d) 0.0)
                          [(+ vx (* d (double (:skid s)) dt)) (boolean ground?)]

                          (< (Math/abs vx) top)
                          (let [v (+ vx (* d (double (:accel s)) dt))]
                            [(if (> (Math/abs v) top) (* d top) v) false])

                          :else
                          (let [v (- vx (* d (double (:decel s)) dt))]
                            [(if (< (Math/abs v) top) (* d top) v) false]))]
        (assoc p :vx (double nvx) :skidding? skid?
               :facing (if (or ground? (nil? (:carrying p))) dir (:facing p)))))))

(defn- jump-step
  "Jump buffer, coyote time and the jump itself. Returns [player jumped?]."
  [p s c dt]
  (let [dt (double dt)
        jb (double (:jump-buffer p))
        jb (cond (:jump-pressed c) 0.12 (> jb 0.0) (- jb dt) :else jb)
        coyote (double (:coyote p))
        ground? (:on-ground? p)
        coyote (cond ground? 0.08 (> coyote 0.0) (- coyote dt) :else coyote)
        p (assoc p :jump-buffer jb :coyote coyote :stomp-chain (if ground? 0 (:stomp-chain p)))]
    (if (and (> jb 0.0) (> coyote 0.0))
      [(assoc p :vy (double (- (if (> (Math/abs (double (:vx p))) (+ (long (:walk s)) 6)) (:run-jump s) (:jump s))))
              :jump-buffer 0.0 :coyote 0.0 :on-ground? false)
       true]
      [p false])))

(defn update-player
  "One frame of the player: input, physics, bumps, stars and the carried shell."
  [world c dt]
  (let [dt (double dt)
        level (:level world)
        s (get characters (:character (:player world)))
        p (steer (:player world) s c dt)
        [p jumped?] (jump-step p s c dt)
        gravity (double (if (and (:jump c) (< (double (:vy p)) 0.0)) (:grav-hold s) (:grav s)))
        p (assoc p :vy (double (min (+ (double (:vy p)) (* gravity dt)) (double max-fall))))
        [p hit?] (move-x p (player-h p) level dt)
        p (if hit? (assoc p :vx 0.0) p)
        p (if (< (double (:x p)) (double (:cam-x world)))
            (cond-> (assoc p :x (double (:cam-x world)))
              (< (double (:vx p)) 0.0) (assoc :vx 0.0))
            p)
        [p head] (move-y p (player-h p) level dt)
        world (cond-> (assoc world :player p)
                jumped? (add-event :jump))
        world (if head
                (let [[hits ty] head
                      centre (tile-of (+ (double (:x p)) (/ (long (:w p)) 2.0)))
                      tx (if (some (fn [h] (= h centre)) hits) centre (first hits))]
                  (bump world tx ty))
                world)
        p (:player world)
        p (if (and (:on-ground? p) (> (Math/abs (double (:vx p))) 1.0))
            (assoc p :walk-phase (+ (double (:walk-phase p)) (* (Math/abs (double (:vx p))) dt)))
            p)]
    (-> world (assoc :player p) collect-stars (update-carry c))))

;; Enemies

(defn- shell-hits
  "A moving or carried shell defeats the enemies it overlaps. A carried shell
   dies with the first one and is dropped."
  [world shell-id]
  (let [n (count (:enemies world))]
    (loop [world world
           id 0]
      (if (< id n)
        (let [shell (find-enemy world shell-id)
              other (find-enemy world id)]
          (if (or (= id shell-id) (not (:alive? other)) (not (:active? other))
                  (contains? #{:dead :squashed} (:state other))
                  (not (overlap? (:x shell) (:y shell) (:w shell) (:h shell)
                                 (:x other) (:y other) (:w other) (:h other))))
            (recur world (inc id))
            (let [world (defeat-enemy world id 200)]
              (if (= :carried (:state shell))
                (-> world
                    (defeat-enemy shell-id 200)
                    (assoc-in [:player :carrying] nil))
                (recur world (inc id))))))
        world))))

(defn- touch-player
  "What happens when enemy `id` meets the player: defeat, pick up, kick,
   stomp or hurt."
  [world id]
  (let [e (find-enemy world id)
        p (:player world)
        ph (player-h p)]
    (if (or (not= :play (:state world))
            (contains? #{:dead :squashed :carried} (:state e))
            (not (overlap? (:x p) (:y p) (:w p) ph (:x e) (:y e) (:w e) (:h e))))
      world
      (if (or (> (double (:sparkle p)) 0.0) (:demo? world))
        (defeat-enemy world id 200)
        (let [falling-onto? (and (> (double (:vy p)) 0.0)
                                 (<= (- (+ (double (:y p)) ph) (double (:y e))) 5.0))]
          (cond
            (= :shell (:state e))
            (if (and (not falling-onto?) (nil? (:carrying p)) (:controls-run? world))
              (-> world
                  (update-enemy id (fn [e] (assoc e :state :carried :timer 0.0)))
                  (assoc-in [:player :carrying] id)
                  (add-event :pickup))
              (let [right? (>= (+ (double (:x e)) (/ (long (:w e)) 2.0))
                               (+ (double (:x p)) (/ (long (:w p)) 2.0)))]
                (cond-> (-> world
                            (update-enemy id (fn [e] (assoc e :state :kicked
                                                            :vx (double (if right? shell-speed (- shell-speed)))
                                                            :grace 0.25)))
                            (add-points 400)
                            (add-event :kick))
                  falling-onto? (assoc-in [:player :vy] (double (- stomp-bounce))))))

            falling-onto?
            (let [chain (inc (long (:stomp-chain p)))
                  points (min (* 100 (bit-shift-left 1 (dec chain))) 8000)
                  jump (:jump (get characters (:character p)))]
              (-> world
                  (update :player assoc :stomp-chain chain
                          :vy (double (- (if (:controls-jump? world) jump stomp-bounce)))
                          :y (double (- (double (:y e)) ph)))
                  (add-points points)
                  (spawn-particle :score (:x e) (- (double (:y e)) 4.0) 0 -30 points)
                  (update-enemy id (fn [e] (if (= :bug (:kind e))
                                             (assoc e :state :squashed :timer 0.0)
                                             (assoc e :state :shell :timer 0.0 :vx 0.0))))
                  (add-event :stomp)))

            (and (= :kicked (:state e)) (> (double (:grace e)) 0.0))
            world

            :else (hurt-player world)))))))

(defn- walker?
  "A live, active enemy that is walking (the ones that bounce off each other)."
  [e]
  (and (:alive? e) (:active? e) (= :walk (:state e))))

(defn- walkers-bounce
  "Two overlapping walkers turn away from each other. Pairs go in slot order
   (i < j), each read from the vector as earlier pairs left it."
  [world]
  (let [n (count (:enemies world))]
    (loop [es (:enemies world) i 0 j 1]
      (cond
        (>= i n) (assoc world :enemies es)
        (>= j n) (recur es (inc i) (+ i 2))
        :else
        (let [a (nth es i)]
          (if-not (walker? a)
            (recur es (inc i) (+ i 2))
            (let [b (nth es j)]
              (if (and (walker? b)
                       (overlap? (:x a) (:y a) (:w a) (:h a) (:x b) (:y b) (:w b) (:h b)))
                (let [av (Math/abs (double (:vx a))) bv (Math/abs (double (:vx b)))
                      left? (< (double (:x a)) (double (:x b)))]
                  (recur (assoc es
                                i (assoc a :vx (if left? (- av) av))
                                j (assoc b :vx (if left? bv (- bv))))
                         i (inc j)))
                (recur es i (inc j))))))))))

(defn- move-enemy
  "Gravity, the walls and the floor for one live enemy. Returns the enemy;
   alive? is false once it left the screen."
  [e world dt]
  (let [dt (double dt)
        level (:level world)
        e (assoc e :vy (min (+ (double (:vy e)) (* 600.0 dt)) (double max-fall)))
        [e hit?] (move-x e (:h e) level dt)
        e (if hit? (assoc e :vx (- (double (:vx e)))) e)
        [e _] (move-y e (:h e) level dt)]
    (if (or (> (double (:y e)) (+ view-h 16)) (< (double (:x e)) (- (double (:cam-x world)) 64.0)))
      (assoc e :alive? false)
      e)))

(defn- update-enemy-at
  "One enemy's frame: the state timers and motion, then its contact effects.
   The enemy is re-read from the world after each effect, as the Python does."
  [world id dt]
  (let [e (find-enemy world id)
        dt (double dt)
        px (double (:x (:player world)))
        activate (+ (double (:cam-x world)) view-w 16.0)]
    (cond
      (not (:alive? e)) world
      (and (not (:active? e)) (>= (double (:x e)) activate)) world
      :else
      (let [e (assoc e :active? true)
            e (if (> (double (:grace e)) 0.0) (assoc e :grace (- (double (:grace e)) dt)) e)
            put (fn [w e] (update-enemy w id (fn [_] e)))]
        (case (:state e)
          :squashed
          (let [t (+ (double (:timer e)) dt)]
            (put world (assoc e :timer t :alive? (not (> t 0.5)))))

          :dead
          (let [vy (min (+ (double (:vy e)) (* 600.0 dt)) (double max-fall))
                y (+ (double (:y e)) (* vy dt))]
            (put world (assoc e :vy vy :x (+ (double (:x e)) (* (double (:vx e)) dt)) :y y
                              :alive? (not (> y (+ view-h 16))))))

          :carried (shell-hits (put world e) id)

          (let [e (if (= :shell (:state e))
                    (let [t (+ (double (:timer e)) dt)
                          e (assoc e :timer t :vx 0.0)]
                      (if (> t 6.0)
                        (assoc e :state :walk :vx (if (< px (double (:x e))) -18.0 18.0))
                        e))
                    e)
                e (move-enemy e world dt)
                world (put world e)]
            (if-not (:alive? e)
              world
              (cond-> world
                (= :kicked (:state e)) (shell-hits id)
                true (touch-player id)))))))))

(defn update-enemies
  "Activate, move and resolve every enemy in order, bounce overlapping
   walkers off each other. Dead enemies keep their slot."
  [world dt]
  (let [dt (double dt)
        n (count (:enemies world))
        world (loop [w world i 0]
                (if (< i n) (recur (update-enemy-at w i dt) (inc i)) w))
        es (:enemies world)
        walkers (loop [i 0 k 0]
                  (if (< i n)
                    (recur (inc i) (if (walker? (nth es i)) (inc k) k))
                    k))]
    (if (> walkers 1) (walkers-bounce world) world)))

;; The step

;; Flag, castle and tally

(def ^:private flag-bonuses [[64 5000] [48 2000] [32 800] [16 400]])

(defn- flag-bonus-for [height]
  (or (some (fn [[limit points]] (when (>= height limit) points)) flag-bonuses) 100))

(defn- check-flag
  "Grab the pole once the player's right edge reaches it: freeze, score the
   height bonus and drop a carried shell."
  [world]
  (let [fx (:flag-x (:level world))
        p (:player world)]
    (if (and fx
             (>= (+ (double (:x p)) (long (:w p))) (+ (* (long fx) tile) 3))
             (< (double (:y p)) (* (- rows 2) tile)))
      (let [carried (:carrying p)
            world (if carried
                    (update-enemy world carried (fn [e] (assoc e :state :shell)))
                    world)
            h (long (player-h p))
            x (double (- (+ (* (long fx) tile) 3) (long (:w p))))
            height (- (* (- rows 3) tile) (+ (double (:y p)) h))
            bonus (flag-bonus-for height)]
        (-> world
            (update :player assoc :carrying nil :x x :vx 0.0 :vy 0.0)
            (assoc :state :flag :state-time 0.0 :flag-bonus (double bonus))
            (add-points bonus)
            (spawn-particle :score (+ x 8) (:y p) 0 -20 bonus)
            (add-event :flag)))
      world)))

(defn- update-flag [world dt]
  (let [dt (double dt)
        p (:player world)
        h (long (player-h p))
        base (* (- rows 3) tile)
        py (min (+ (double (:y p)) (* 70.0 dt)) (double (- base h)))
        fy (when (:flag-y world)
             (min (+ (double (:flag-y world)) (* 70.0 dt)) (double (- base tile))))
        world (-> world (assoc-in [:player :y] py) (assoc :flag-y fy))]
    (if (and (>= py (- base h))
             (or (nil? fy) (>= fy (- base tile)))
             (> (double (:state-time world)) 0.4))
      (-> world
          (assoc :state :castle :state-time 0.0)
          (update :player assoc :x (+ (double (:x p)) (long (:w p)) 2.0) :facing 1))
      world)))

(defn- update-castle [world dt]
  (let [dt (double dt)
        level (:level world)
        p (:player world)
        h (player-h p)
        p (assoc p :vx 40.0 :vy (min (+ (double (:vy p)) (* 900.0 dt)) (double max-fall)))
        [p _] (move-x p h level dt)
        [p _] (move-y p h level dt)
        p (assoc p :walk-phase (+ (double (:walk-phase p)) (* 40.0 dt)))
        world (update-camera (assoc world :player p))
        door (or (:castle-door level) (+ (double (:x p)) 1.0))]
    (if (or (>= (double (:x p)) (- (double door) 3.0))
            (> (double (:state-time world)) 4.0))
      (-> world (assoc :state :tally :state-time 0.0) (assoc-in [:player :vx] 0.0))
      world)))

(defn- update-tally [world dt]
  (let [dt (double dt)
        left (double (:time-left world))]
    (cond
      (> left 0.0)
      (let [units (double (min left (double (max 1 (long (* dt 120.0))))))]
        (-> world
            (assoc :time-left (- left units))
            (add-points (* (long units) 50))))

      (> (double (:state-time world)) 1.0)
      (assoc world :state :clear)

      :else world)))

(defn- play-frame [world c dt]
  (let [dt (double dt)
        left (- (double (:time-left world)) (* dt 2.5))
        world (if (<= left 0.0)
                (kill-player (assoc world :time-left 0.0))
                (assoc world :time-left left))
        world (-> world (update-player c dt) (update-items dt) (update-enemies dt))
        world (if (= :play (:state world))
                (let [world (check-flag world)]
                  (if (> (double (:y (:player world))) (+ view-h 8))
                    (kill-player world true)
                    world))
                world)]
    (update-camera world)))

(defn- dying-frame [world dt]
  (let [dt (double dt)
        world (if (> (double (:state-time world)) 0.5)
                (let [p (:player world)
                      vy (min (+ (double (:vy p)) (* 600.0 dt)) (* max-fall 1.5))]
                  (assoc world :player (assoc p :vy (double vy) :y (+ (double (:y p)) (* vy dt)))))
                world)]
    (if (> (double (:state-time world)) 2.6)
      (assoc world :state :dead)
      world)))

(defn- update-world [world c dt]
  (let [dt (double (min dt 0.05))]
    (if (<= dt 0.0)
      world
      (let [world (-> world
                      (assoc :elapsed (+ (double (:elapsed world)) dt)
                             :state-time (+ (double (:state-time world)) dt))
                      (update-particles dt))]
        (case (:state world)
          :play (play-frame world c dt)
          :dying (dying-frame world dt)
          :flag (update-flag world dt)
          :castle (update-castle world dt)
          :tally (update-tally world dt)
          world)))))

(defn step
  "Advance the world one frame of `dt` seconds under `controls`."
  [world controls dt]
  (-> world
      (assoc :controls-run? (boolean (:run controls))
             :controls-jump? (boolean (:jump controls)))
      (update-world controls dt)))

;; Demo autopilot

(defn autopilot
  "The attract-mode driver: run right, hop walls and gaps. Takes and returns
   a controls map."
  [world c]
  (let [p (:player world)
        level (:level world)
        h (player-h p)
        py (double (:y p))
        edge (+ (double (:x p)) (long (:w p)))
        rs (range (tile-of py) (inc (tile-of (- (+ py h) 0.01))))
        wall? (boolean (some (fn [d] (some (fn [ty] (solid? level (tile-of (+ edge d)) ty)) rs)) [1 10]))
        front (tile-of (+ edge 10))
        feet (tile-of (+ py h 1))
        gap? (not (some (fn [k] (some (fn [y] (solid? level (+ front k) y)) (range feet rows))) [0 1]))
        want (boolean (and (or wall? gap?) (:on-ground? p)))]
    (assoc c :left false :up false :down false :right true :run true
           :jump-pressed (and want (not (:jump c)))
           :jump (boolean (or want (and (:jump c) (< (double (:vy p)) 0.0)))))))
