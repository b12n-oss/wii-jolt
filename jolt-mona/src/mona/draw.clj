(ns mona.draw
  "Super Mona's drawing, a port of super_mona/render.py and the drawing parts
   of super_mona/__init__.py. Everything that calls wii.gfx lives here. The
   badge draws at 160x120; sprites and the tile layers are in game pixels
   (the shim scales them 4x), while rect!, text! and tiles-map! take screen
   pixels, so a badge position becomes `(* 4 x)` for those."
  (:require [mona.rules :as r]
            [wii.frame-stats :as stats]
            [wii.gfx :as gfx]))

(def sky {:day 0x5C94FCFF :cave 0x000000FF :dusk 0x342C6EFF :cloud 0x96C8FFFF :castle 0x280814FF})
(def black 0x000000FF)
(def white 0xFFFFFFFF)
(def gold 0xFFD64AFF)
(def dim 0x000000AA)
(def panel 0x100C28E1)
(def map-solid 0xC8783CFF)
(def map-player 0xFF50C8FF)
(def map-flag 0x50FF78FF)

(def ^:private px 4)
(def ^:private screen-w 640)
(def ^:private hud-size 32)
(def ^:private title-size 48)

;; Player sheet columns.
(def ^:private stand 0)
(def ^:private walk1 1)
(def ^:private walk2 2)
(def ^:private jump 3)
(def ^:private skid 4)
(def ^:private crouch 5)
(def ^:private dead 6)
(def ^:private climb 7)

(def ^:private item-cells {:chip 7 :sparkle 12 :1up 11})
(def ^:private pages ["PAUSED" "MAP" "ITEMS" "HERO"])

(defn- sky-of [level]
  (get sky (:theme level) (:day sky)))

;; Stage

(defn load-stage!
  "Hand a level's tile layer and its decor layer to the shim cell by cell,
   skipping empty cells (a block copy costs 245 ms on the Wii). Returns
   the microseconds the hand-off took (two clock reads)."
  [level]
  (let [t0 (gfx/time-us)
        tiles ^bytes (:tiles level)
        n (long (alength tiles))]
    (when-not (gfx/tiles-begin! (:width level))
      (throw (ex-info "load-stage!: the shim refused the stage width" {:width (:width level)})))
    (loop [i 0]
      (when (< i n)
        (let [id (bit-and (aget tiles i) 0xFF)]
          (when-not (zero? id) (gfx/tile-set! i id)))
        (recur (inc i))))
    (doseq [[i id] (:decor level)] (gfx/decor-set! i id))
    (stats/elapsed-us t0 (gfx/time-us))))

;; Text

(defn- pad
  "n as a string, at least `width` wide, padded on the left with `ch`."
  [n width ch]
  (let [s (str n)]
    (str (apply str (repeat (- (long width) (count s)) ch)) s)))

(defn- text-px!
  "Text with the badge's shadow (black, one game pixel down and right) at
   screen pixel (x, y)."
  [s x y size colour]
  (gfx/text! s (+ x px) (+ y px) size black)
  (gfx/text! s x y size colour))

(defn- text!
  "Text at game pixel (x, y) in the HUD size."
  [s x y colour]
  (text-px! s (* px x) (* px y) hud-size colour))

(defn- centre!
  "Text centred over the screen at game row y."
  ([s y colour] (centre! s y colour hud-size))
  ([s y colour size]
   (text-px! s (quot (- screen-w (long (gfx/text-width s size))) 2) (* px y) size colour)))

(defn- hero! [who big? x y]
  (gfx/sprite! gfx/sheet-players stand
               (+ (if (= who :ducky) 2 0) (if big? 1 0))
               x y 0))

(defn- mob! [index x y flags]
  (gfx/sprite! gfx/sheet-mobs (mod index 8) (quot index 8) x y flags))

(defn- panel! [y h]
  (gfx/rect! (* px 8) (* px y) (* px 144) (* px h) panel))

;; World layers

(defn- draw-tiles-window [world]
  (gfx/tiles-draw! (double (long (:cam-x world)))))

(defn- draw-bumps [world cam]
  (let [bg (sky-of (:level world))]
    (doseq [[tx ty tile age] (:bumps world)]
      (let [x (- (* (long tx) r/tile) cam)]
        (when-not (or (< x (- r/tile)) (> x r/view-w))
          (let [y (* (long ty) r/tile)
                lift (long (* 4 (Math/sin (* Math/PI (min (/ (double age) 0.16) 1.0)))))]
            (gfx/rect! (* px x) (* px y) (* px r/tile) (* px r/tile) bg)
            (gfx/sprite! gfx/sheet-tiles (mod tile 16) (quot tile 16) x (- y lift) 0)))))))

(defn- draw-stars [world cam t]
  (let [cell (+ 8 (mod (long (* (double t) 6)) 3))
        left (dec (Math/floorDiv (long cam) (long r/tile)))
        right (+ left (quot r/view-w r/tile) 2)]
    (doseq [[tx ty] (:stars (:level world))]
      (when (<= left (long tx) right)
        (mob! cell (- (* (long tx) r/tile) cam) (* (long ty) r/tile) 0)))))

(defn- draw-items [world cam]
  (doseq [item (:items world)]
    (let [x (- (long (:x item)) cam)]
      (when-not (or (< x (- r/tile)) (> x r/view-w))
        (mob! (get item-cells (:kind item) 7) x (long (:y item)) 0)
        (when (pos? (double (:emerge item)))
          ;; Keep the item behind its block while it rises out.
          (let [by (+ (long (:target-y item)) r/tile)
                tile (r/get-tile (:level world) (r/tile-of (:x item)) (r/tile-of by))]
            (gfx/sprite! gfx/sheet-tiles (mod tile 16) (quot tile 16) x by 0)))))))

(defn- draw-flag [world cam]
  (let [fx (:flag-x (:level world))
        fy (:flag-y world)]
    (when (and fx fy)
      (let [x (- (* (long fx) r/tile) 4 cam)]
        (when (< (- r/tile) x r/view-w)
          (mob! 14 x (long fy) 0))))))

(defn- enemy-cell
  "[cell flags] for an enemy at animation step `step`."
  [e step]
  (if (= :bug (:kind e))
    [(if (= :squashed (:state e)) 2 step) 0]
    (case (:state e)
      :walk [(+ 3 step) (if (pos? (double (:vx e))) gfx/flip-x 0)]
      :shell [(if (> (double (:timer e)) 4.0) (+ 5 step) 5) 0]
      [5 0])))

(defn- draw-enemies [world cam t]
  (let [step (mod (long (* (double t) 6)) 2)]
    (doseq [e (:enemies world)]
      (when (and (:alive? e) (:active? e))
        (let [x (- (long (:x e)) 1 cam)]
          (when-not (or (< x (- r/tile)) (> x r/view-w))
            (let [y (- (long (+ (double (:y e)) (double (:h e)))) r/tile)
                  [cell flags] (enemy-cell e step)]
              (mob! cell x y (if (= :dead (:state e)) gfx/flip-y flags)))))))))

(defn- player-col [world p]
  (let [state (:state world)]
    (cond
      (:dead? p) dead
      (= :flag state) climb
      (:crouch? p) crouch
      (and (not (:on-ground? p)) (= :play state)) jump
      (:skidding? p) skid
      (or (> (Math/abs (double (:vx p))) 1.0) (= :castle state))
      (if (odd? (quot (long (:walk-phase p)) 6)) walk1 walk2)
      :else stand)))

(defn- draw-player [world cam t]
  (let [p (:player world)
        level (:level world)
        door (:castle-door level)
        hidden? (or (and (contains? #{:tally :clear} (:state world))
                         door
                         (>= (double (:x p)) (- (long door) 4)))
                    (and (pos? (double (:invulnerable p)))
                         (odd? (long (* (double t) 20)))))]
    (when-not hidden?
      (let [glow? (and (pos? (double (:sparkle p))) (odd? (long (* (double t) 12))))
            row (+ (if (= :ducky (:character p)) 2 0) (if (:big? p) 1 0) (if glow? 4 0))
            x (- (long (:x p)) 5 cam)
            y (- (long (+ (double (:y p)) (double (r/player-h p)))) 16)]
        (gfx/sprite! gfx/sheet-players (player-col world p) row x y
                     (if (neg? (long (:facing p))) gfx/flip-x 0))))))

(defn- draw-particles [world cam t]
  (doseq [[kind x y _vx _vy _age data] (:particles world)]
    (let [sx (- (long x) cam)
          sy (long y)]
      (case kind
        :score (text! (str data) sx sy white)
        :debris (mob! 13 sx sy 0)
        :star (mob! (+ 8 (mod (long (* (double t) 12)) 3)) sx sy 0)
        :sparkle (mob! 12 sx sy 0)
        nil))))

(defn draw-world
  "The stage window and everything that moves on it, in render.py's order."
  [world t]
  (let [cam (long (:cam-x world))]
    (draw-tiles-window world)
    (draw-bumps world cam)
    (draw-stars world cam t)
    (draw-items world cam)
    (draw-flag world cam)
    (draw-enemies world cam t)
    (draw-player world cam t)
    (draw-particles world cam t)))

;; HUD

(defn draw-hud
  "Name and lives, stars, score and the clock along the top."
  [world]
  (let [s (:session world)
        who (:name (get r/characters (:character (:player world))))]
    (text! (str who " x" (:lives s)) 2 0 white)
    (mob! 8 52 2 0)
    (text! (str "x" (pad (:stars s) 2 \0)) 61 0 gold)
    (text! (pad (:score s) 6 \0) 84 0 white)
    (text! (pad (long (+ (double (:time-left world)) 0.99)) 3 \space) 138 0 white)))

;; Map page

(defn- draw-map [world x y w h]
  (let [level (:level world)
        width (long (:width level))
        sx (/ (double w) width)
        sy (/ (double h) r/rows)
        cam-x (double (:cam-x world))
        p (:player world)]
    (gfx/tiles-map! (* px x) (* px y) (* px w) (* px h) map-solid (sky-of level))
    (gfx/rect! (* px (+ x (long (* (/ cam-x r/tile) sx)))) (* px y) px (* px h) white)
    (gfx/rect! (* px (+ x (long (* (/ (+ cam-x r/view-w) r/tile) sx)))) (* px y) px (* px h) white)
    (when-let [fx (:flag-x level)]
      (gfx/rect! (* px (+ x (long (* (long fx) sx)))) (* px (+ y (long (* 3 sy))))
                 (* px 2) (* px (long (* 9 sy))) map-flag))
    (gfx/rect! (* px (dec (+ x (long (* (/ (double (:x p)) r/tile) sx)))))
               (* px (dec (+ y (long (* (/ (double (:y p)) r/tile) sy)))))
               (* px 3) (* px 3) map-player)))

;; Overlays

(defn- hints! [s]
  (centre! s 108 gold))

(defn- draw-title [app t world]
  (draw-world world t)
  (centre! "SUPER MONA" 22 gold title-size)
  (centre! (str "HIGH " (pad (:high app) 6 \0)) 44 white)
  (when (odd? (long (* (double t) 2)))
    (centre! "PRESS 2" 84 white))
  (when (:sound-off? app)
    (centre! "SOUND OFF" 100 white)))

(defn- draw-intro [app t]
  (let [world (:world app)
        level (:level world)
        session (:session app)
        switch? (:switch? level)]
    (draw-world world t)
    (panel! 26 (if switch? 76 56))
    (centre! (:name level) 34 white title-size)
    (hero! (:character session) (:big? session) 62 58)
    (text! (str "x " (:lives session)) 84 62 white)
    (when switch?
      (centre! "SWAP HERO IN MENU" 92 gold))))

(defn- draw-pause-page [app world page]
  (let [session (:session app)
        level (:level world)]
    (panel! 30 70)
    (case page
      0 (do (centre! (:name level) 36 white)
            (centre! (str "SCORE " (pad (:score session) 6 \0)) 50 white)
            (centre! (str "HIGH  " (pad (max (long (:high session)) (long (:high app))) 6 \0)) 62 white)
            (centre! "HOME TO QUIT" 80 gold))
      1 (do (centre! (:name level) 34 white)
            (draw-map world 12 50 136 45))
      2 (let [inv (:inventory session)]
          (if (empty? inv)
            (do (centre! "NO ITEMS YET" 50 white)
                (centre! "SPARE CHIPS GO HERE" 66 white))
            (let [cursor (min (long (:cursor app)) (dec (count inv)))]
              (doseq [[i kind] (map-indexed vector inv)]
                (let [y (+ 36 (* i 16))
                      pen (if (= i cursor) gold white)]
                  (mob! (get item-cells kind 7) 30 (+ y 2) 0)
                  (text! (get r/item-names kind (name kind)) 44 y pen)
                  (when (= i cursor)
                    (text! ">" 20 y gold)))))))
      (do (doseq [[i who] (map-indexed vector r/character-order)]
            (let [x (+ 34 (* i 60))]
              (hero! who (:big? session) (+ x 8) 38)
              (text! (:name (get r/characters who)) (+ x 4) 56
                     (if (= who (:character (:player world))) gold white))))
          (centre! (:about (get r/characters (:character (:player world)))) 70 white)
          (if (:switch? level)
            (centre! "2: SWAP HERO" 84 gold)
            (centre! "NO SWAP ON THIS STAGE" 84 white))))))

(defn- draw-pause [app t]
  (let [world (:world app)
        page (long (:page app))]
    (draw-world world t)
    (draw-hud world)
    (gfx/rect! 0 (* px 10) screen-w (* px 110) dim)
    (loop [i 0 x (* px 6)]
      (when (< i (count pages))
        (let [name (nth pages i)]
          (text-px! name x (* px 14) hud-size (if (= i page) gold white))
          (recur (inc i) (+ x (long (gfx/text-width name hud-size)) (* px 10))))))
    (draw-pause-page app world page)
    (hints! "< > PAGE   1 RESUME")))

(defn- draw-end [app title]
  (let [session (:session app)
        t (double (:state-time app))]
    (gfx/rect! 0 0 screen-w 480 black)
    (centre! title 30 gold title-size)
    (centre! (str "SCORE " (pad (:score session) 6 \0)) 56 white)
    (centre! (str "HIGH  " (pad (:high app) 6 \0)) 68 white)
    (when (and (> t 1.0) (odd? (long (* t 2))))
      (centre! "PRESS 2" 92 white))))

(defn- draw-demo [app t]
  (let [world (:demo-world app)]
    (draw-world world t)
    (draw-hud world)
    (when (< (double (:state-time app)) 2.0)
      (centre! (:name (:level world)) 30 white title-size))
    (when (odd? (long (* (double t) 2)))
      (centre! "PRESS 2" 84 white))))

(defn draw-app
  "The frame for the app map's state: the backdrop (world, and the HUD in play
   states) and the state's overlay. Call once per frame after begin!."
  [app t]
  (case (:state app)
    :title (draw-title app t (:title-world app))
    :intro (draw-intro app (double (:state-time app)))
    :play (do (draw-world (:world app) t) (draw-hud (:world app)))
    :pause (draw-pause app t)
    :over (draw-end app "GAME OVER")
    :win (draw-end app "YOU WIN!")
    :demo (draw-demo app t)))
