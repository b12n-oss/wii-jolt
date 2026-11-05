(ns mona.app
  "Super Mona's app state machine, pure. A port of the state logic in the
   badge's super_mona/__init__.py (everything but drawing and the badge API).
   `tick` takes the app map, one frame of input and dt, and returns the next
   app map. The main loop prints and then empties `:log` each frame.
   `:session` is the authority for score, lives and level: it is pushed into
   the world before each step and pulled back after, so a 1-up in the world
   and a death handled here both stick.
   Extra keys beyond the draw contract: `:last-mode` (the state last logged)
   and `:high-before` (`:high` at the start of the tick, for `save-high?`)."
  (:require [mona.levels :as levels]
            [mona.rules :as rules]))

(def demo-idle 10.0)
(def demo-stages 2)
(def demo-seconds 60.0)
(def pages ["PAUSED" "MAP" "ITEMS" "HERO"])
(def max-dt 0.05)

;; Input

(defn world-controls
  "Rules controls from badge pad names."
  [input]
  (let [held (:held input)
        pressed (:pressed input)]
    {:left (boolean (held :left))
     :right (boolean (held :right))
     :up (boolean (held :up))
     :down (boolean (held :down))
     :jump (boolean (held :select))
     :jump-pressed (boolean (pressed :select))
     :run (boolean (held :back))}))

(defn- pressed? [input button]
  (boolean ((:pressed input) button)))

;; Helpers

(defn- log [app line]
  (update app :log conj line))

(defn- set-state [app state]
  (assoc app :state state :state-time 0.0))

(defn- level-count [] (count levels/levels))

(defn- stage-line [n level]
  (str "stage " (inc n) " " (:name level)))

(defn- title-world []
  (assoc (rules/new-world (levels/level-at 0) (rules/new-session)) :demo? true))

(defn- log-events
  "Log the world's events as `event <kw> mode=<mode>` and empty them. :dirty
   stays for the main loop, which forwards it to the shim and clears it."
  [app world mode]
  (let [app (reduce (fn [a e] (log a (str "event " (name e) " mode=" mode))) app (:events world))]
    [app (assoc world :events [])]))

(defn- raise-high [app]
  (assoc app :high (max (long (:high app)) (long (:high (:session app))))))

(defn- pull-session
  "Take the stepped world's session back as the app's."
  [app world]
  (assoc app :session (:session world) :world world))

;; Starters

(defn- start-level [app]
  (let [n (:level (:session app))
        level (levels/level-at n)]
    (-> app
        (assoc :world (rules/new-world level (:session app)))
        (log (stage-line n level))
        (set-state :intro))))

(defn- new-game [app ch]
  (-> app
      (assoc :player-ch ch
             :session (assoc (rules/new-session) :high (:high app)))
      start-level))

(defn- start-demo [app]
  (let [n (mod (long (:demo-next app)) (level-count))
        level (levels/level-at n)]
    (-> app
        (assoc :demo-world (assoc (rules/new-world level (rules/new-session)) :demo? true)
               :demo-next (inc (long (:demo-next app))))
        (log (stage-line n level))
        (set-state :demo))))

(defn- stop-demo [app]
  (assoc app :demo-world nil))

;; States

(defn- title-tick [app input]
  (cond
    (pressed? input :select) (new-game app (:ch input))
    (:any-pressed? input) (set-state app :title)
    (> (double (:state-time app)) demo-idle) (start-demo (assoc app :demo-played 0))
    :else app))

(defn- demo-tick [app input dt]
  (if (:any-pressed? input)
    (let [select? (pressed? input :select)
          app (stop-demo app)]
      (if select?
        (new-game app (:ch input))
        (set-state app :title)))
    (let [w (rules/step (:demo-world app) (rules/autopilot (:demo-world app) rules/no-controls) dt)
          [app w] (log-events app w "demo")
          app (assoc app :demo-world w)]
      (if (or (#{:clear :dead} (:state w)) (> (double (:state-time app)) demo-seconds))
        (let [played (inc (long (:demo-played app)))
              app (assoc app :demo-played played)]
          (if (< played demo-stages)
            (start-demo app)
            (set-state (stop-demo app) :title)))
        app))))

(defn- resolve-world
  "Handle a finished world: a death costs a life and restarts the stage or
   ends the game, a clear advances the level or wins. Other states pass."
  [app]
  (let [world (:world app)
        session (:session app)]
    (case (:state world)
      :dead (let [session (update session :lives dec)
                  app (assoc app :session session)]
              (if (pos? (long (:lives session)))
                (start-level app)
                (set-state app :over)))
      :clear (let [app (log app (str "clear " (:name (:level world)) " score=" (:score session)))
                   session (update session :level inc)
                   app (assoc app :session session)]
               (if (>= (long (:level session)) (level-count))
                 (set-state app :win)
                 (start-level app)))
      app)))

(defn- intro-tick
  "The badge never has a finished world here; the check keeps a stage that was
   forced to :dead or :clear during the intro from being lost."
  [app input]
  (let [t (double (:state-time app))]
    (cond
      (#{:dead :clear} (:state (:world app))) (resolve-world (raise-high app))
      (or (> t 2.0) (and (> t 0.4) (pressed? input :select))) (set-state app :play)
      :else app)))

(defn- play-tick [app input dt]
  (if (and (pressed? input :menu) (= :play (:state (:world app))))
    (-> app (assoc :page 0 :cursor 0) (set-state :pause))
    (let [world (assoc (:world app) :session (:session app))
          world (rules/step world (world-controls input) dt)
          [app world] (log-events app world "play")]
      (resolve-world (raise-high (pull-session app world))))))

(defn- pause-tick [app input]
  (let [input-page (long (:page app))
        n (count pages)
        page (cond
               (pressed? input :left) (mod (dec input-page) n)
               (pressed? input :right) (mod (inc input-page) n)
               :else input-page)
        app (assoc app :page page)
        resume (fn [a]
                 (if (and (> (double (:state-time a)) 0.0)
                          (or (pressed? input :menu) (pressed? input :back)))
                   (set-state a :play)
                   a))]
    (case (nth pages page)
      "ITEMS"
      (let [inv (:inventory (:session app))]
        (if (empty? inv)
          (resume app)
          (let [c (long (:cursor app))
                c (cond (pressed? input :up) (mod (dec c) (count inv))
                        (pressed? input :down) (mod (inc c) (count inv))
                        :else c)
                c (min c (dec (count inv)))
                app (assoc app :cursor c)]
            (if (pressed? input :select)
              (let [world (assoc (:world app) :session (:session app))
                    [world used?] (rules/use-item world c)
                    [app world] (log-events app world "play")
                    app (pull-session app world)]
                (if used? (set-state app :play) (resume app)))
              (resume app)))))

      "HERO"
      (if (and (pressed? input :select) (:switch? (:level (:world app))))
        (let [world (assoc (:world app) :session (:session app))
              [world _] (rules/switch-character world)
              [app world] (log-events app world "play")]
          (resume (pull-session app world)))
        (resume app))

      (resume app))))

(defn- end-tick [app input]
  (if (and (> (double (:state-time app)) 1.0) (pressed? input :select))
    (set-state app :title)
    app))

;; Public

(defn init
  "A fresh app on the title with `high` as the saved high score."
  [high]
  {:state :title :state-time 0.0 :session nil :world nil
   :title-world (title-world) :demo-world nil :demo-next 0 :demo-played 0
   :page 0 :cursor 0 :high high :player-ch nil :log []
   :last-mode nil :high-before high})

(defn tick
  "Advance the app one frame."
  [app input dt]
  (let [dt (min (double dt) max-dt)
        app (if (nil? (:last-mode app))
              (-> app (log (str "mode " (name (:state app)))) (assoc :last-mode (:state app)))
              app)
        app (assoc app :high-before (:high app)
                       :state-time (+ (double (:state-time app)) dt))
        app (case (:state app)
              :title (title-tick app input)
              :intro (intro-tick app input)
              :play (play-tick app input dt)
              :pause (pause-tick app input)
              :demo (demo-tick app input dt)
              (:over :win) (end-tick app input))]
    (if (= (:state app) (:last-mode app))
      app
      (-> app (log (str "mode " (name (:state app)))) (assoc :last-mode (:state app))))))

(defn clear-log
  "The app with its log emptied."
  [app]
  (assoc app :log []))

(defn save-high?
  "True when `:high` rose during the last tick."
  [app]
  (> (long (:high app)) (long (:high-before app))))
