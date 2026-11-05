(ns mona.main
  "Super Mona on the Wii: the main loop. Reads the four Wiimotes, maps the
   remote held sideways to the badge's pad, ticks mona.app, hands stage tiles
   and tile changes to the shim, rumbles on a few events, prints the app's log
   lines (prefix 'jolt-mona: ') and a frame summary every 300 frames. HOME on
   any connected remote ends the game."
  (:require [mona.app :as app]
            [mona.draw :as draw]
            [mona.rules :as rules]
            [wii.frame-stats :as stats]
            [wii.gfx :as gfx]))

(def ^:private name* "jolt-mona")
(def ^:private summary-every 300)
(def ^:private channels (range 4))
(def ^:private stall-us 100000)
;; estimate: a short pulse, as the C games use
(def ^:private rumble-secs 0.1)
(def ^:private rumble-events #{"stomp" "kick" "die" "break"})

(def ^:private pad-buttons
  "Sideways remote to badge pad."
  [[gfx/button-up :left]
   [gfx/button-down :right]
   [gfx/button-right :up]
   [gfx/button-left :down]
   [gfx/button-2 :select]
   [gfx/button-1 :back]
   [gfx/button-plus :menu]])

(defn- pad-set
  "The badge pad buttons in a Wiimote button mask."
  [mask]
  (set (keep (fn [[bit k]] (when (pos? (bit-and mask bit)) k)) pad-buttons)))

(defn- connected-now
  "Whether each of the four remotes is connected, read once per frame."
  []
  (mapv (fn [ch] (boolean (gfx/connected? ch))) channels))

(defn- held-now
  "The held-button mask of each of the four remotes (0 when not connected)."
  [connected]
  (mapv (fn [ch] (if (nth connected ch) (gfx/held ch) 0)) channels))

(defn- pressed-mask [now before]
  (bit-and now (bit-not before)))

(defn- channel-input
  "The app's input map from this frame's masks, last frame's masks and the app
   (see app/choose-channel for how the reading channel is picked)."
  [a now before connected-v]
  (let [connected (set (filter (fn [ch] (nth connected-v ch)) channels))
        ch (app/choose-channel (:player-ch a) now before)]
    {:connected connected
     :any-pressed? (boolean (some (fn [c] (pos? (pressed-mask (nth now c) (nth before c)))) channels))
     :held (if ch (pad-set (nth now ch)) #{})
     :pressed (if ch (pad-set (pressed-mask (nth now ch) (nth before ch))) #{})
     :ch ch}))

(defn- world-key
  "Where the app keeps the world that is on screen."
  [state]
  (case state
    :title :title-world
    :demo :demo-world
    :world))

(defn- sync-world
  "Hand a new stage to the shim when the title, intro or demo began (or its
   level changed), forward the world's tile changes and clear them. Returns
   [app stage-name load], the level name to remember and, on a load,
   [hand-off-us calls-before calls-after] (the shim call count read before and
   after draw/load-stage!, else nil)."
  [a last-state last-name]
  (let [state (:state a)
        k (world-key state)
        world (get a k)]
    (if-not world
      [a last-name nil]
      (let [lvl (:level world)
            load? (and (#{:title :intro :demo} state)
                       (or (not= state last-state) (not= (:name lvl) last-name)))]
        (let [c0 (when load? (long (gfx/calls)))
              us (when load? (draw/load-stage! lvl))
              load (when load? [us c0 (long (gfx/calls))])]
          (doseq [[i tile] (:dirty world)] (gfx/tile-set! i tile))
          [(assoc a k (rules/clear-events world))
           (if (#{:title :intro :demo} state) (:name lvl) last-name)
           load])))))

(defn- rumble-event?
  "True when a log line is a play-mode event worth a pulse."
  [line]
  (and (.startsWith ^String line "event ")
       (.endsWith ^String line "mode=play")
       (let [kw (subs line 6 (- (count line) 10))]
         (contains? rumble-events kw))))

(defn- print-log [a]
  (doseq [l (:log a)] (println (str name* ": " l))))

(defn- active-sky [a]
  (let [world (get a (world-key (:state a)))]
    (get draw/sky (:theme (:level world)) (:day draw/sky))))

(defn -main [& _]
  (gfx/init! 640 480)
  (gfx/scale! 4)
  (println (str name* ": sheets ok="
                (count (filter true? (map gfx/sheet-load! [gfx/sheet-tiles gfx/sheet-players gfx/sheet-mobs])))))
  (let [high0 (gfx/setting-get "mona_high" 0)
        a0 (app/init high0)]
    (println (str name* ": start"))
    (loop [a a0
           t 0.0
           last-held [0 0 0 0]
           samples []
           calls-total 0
           written high0
           last-state nil
           stage-key nil
           rumble-ch nil
           rumble-left 0.0
           last-rumble [false false false false]]
      (let [t0 (gfx/time-us)
            fus (gfx/frame-us)
            dt (if (zero? fus) (/ 1.0 60) (min 0.05 (/ fus 1000000.0)))
            conn (connected-now)
            held (held-now conn)]
        (if (or (gfx/should-close?)
                (some (fn [h] (pos? (bit-and h gfx/button-home))) held))
          (do (gfx/rumble-all-off!)
              (when (> (long (:high a)) (long written))
                (gfx/setting-put! "mona_high" (:high a)))
              (println (str name* ": exit"))
              (gfx/close!))
          (let [a (app/tick a (channel-input a held last-held conn) dt)
                t-tick (gfx/time-us)
                [a stage-key load] (sync-world a last-state stage-key)
                calls-total (if load (+ calls-total (long (nth load 1)) (long (nth load 2))) calls-total)
                log-lines (:log a)
                _ (print-log a)
                _ (when load
                    (println (str name* ": stage-load tick-us=" (stats/elapsed-us t0 t-tick)
                                  " hand-off-us=" (first load) " calls=" (nth load 2))))
                ch (:player-ch a)
                pulse? (and ch (boolean (some rumble-event? log-lines)))
                a (app/clear-log a)
                state (:state a)
                written (if (and (not= state last-state) (#{:over :win} state)
                                 (> (long (:high a)) (long written)))
                          (if (gfx/setting-put! "mona_high" (:high a)) (:high a) written)
                          written)
                [rumble-ch rumble-left] (cond
                                          pulse? [ch rumble-secs]
                                          (and rumble-ch (pos? rumble-left)) [rumble-ch (- rumble-left dt)]
                                          :else [nil 0.0])
                want-rumble (mapv (fn [c] (boolean (and (= c rumble-ch) (pos? rumble-left)))) channels)
                _ (doseq [c channels]
                    (when (not= (nth want-rumble c) (nth last-rumble c))
                      (gfx/rumble! c (nth want-rumble c))))
                t (+ t dt)
                t1 (gfx/time-us)]
            (gfx/begin! (active-sky a))
            (draw/draw-app a t)
            (let [t2 (gfx/time-us)]
              (gfx/end!)
              (let [calls-total (+ calls-total (long (gfx/calls)))
                    sample {:update-us (stats/elapsed-us t0 t1)
                            :draw-us (stats/elapsed-us t1 t2)
                            :frame-us fus}
                    samples (conj samples sample)
                    done? (= (count samples) summary-every)]
                (when (> fus stall-us)
                  (println (str name* ": stall frame-us=" fus
                                " work-us=" (+ (:update-us sample) (:draw-us sample)))))
                (when done?
                  (println (str (stats/summary-line name* (stats/summary samples))
                                " calls=" (quot calls-total summary-every))))
                (recur a t held (if done? [] samples) (if done? 0 calls-total)
                       written state stage-key rumble-ch rumble-left want-rumble)))))))))
