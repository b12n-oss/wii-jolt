(ns mona.rules
  "Super Mona rules, pure Clojure. A port of super_mona/game.py from the badge
   game. This first slice holds the constants, the records and the Level
   builders. The only in-place structure is a level's tile byte array, which
   set-tile! mutates.")

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
  "A six-tile hill with its base on row `base` (default 13)."
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
