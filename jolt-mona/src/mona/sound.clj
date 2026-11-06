(ns mona.sound
  "Super Mona's sound cues. Pure functions from the app map and its log lines
   to what the main loop should play: effects, the song, the tempo and the
   pause flag. Nothing here touches the shim."
  (:require [mona.music-ids :as ids]))

(def hurry-threshold
  "The time left, in game seconds, below which the music speeds up."
  100.0)

(def hurry-tempo
  "The tempo percent while hurrying. Normal tempo is 100."
  125)

(defn active-world
  "The world on screen: :title-world on :title, :demo-world on :demo, else
   :world. Nil when the app has none."
  [app]
  (case (:state app)
    :title (:title-world app)
    :demo (:demo-world app)
    (:world app)))

(defn- event-keyword
  "The keyword of an `event <kw> mode=<play|demo>` line, else nil."
  [line]
  (when-let [m (re-matches (re-pattern "event (\\S+) mode=(play|demo)") (str line))]
    (keyword (second m))))

(defn sfx-for
  "A vector of effect ids, one per `event <kw> mode=<play|demo>` line in
   log-lines, in order. Event keywords without an effect, and lines of any
   other shape, are skipped. :die is included; its recipe is silent."
  [log-lines]
  (into [] (keep (fn [line] (some-> (event-keyword line) ids/effect-id))) log-lines))

(defn- song
  [k loop?]
  {:song (ids/song-id k) :loop? loop?})

(defn- world-song
  "The song for a world on screen, by the world's state."
  [world]
  (case (:state world)
    :play (song (:theme (:level world)) true)
    :dying (song :die false)
    (:flag :castle :tally) (song :clear false)
    nil))

(defn music-for
  "The song cue {:song id :loop? bool} for the app, or nil for no music.
   Title, over and win have their own songs, the intro is silent, and play,
   pause and demo follow the active world's state."
  [app]
  (case (:state app)
    :title (song :title true)
    :over (song :over false)
    :win (song :win true)
    (:play :pause :demo) (world-song (active-world app))
    nil))

(defn tempo-for
  "hurry-tempo while playing (or in the demo) with the active world in :play
   and its time-left below hurry-threshold. 100 everywhere else."
  [app]
  (let [w (active-world app)]
    (if (and (contains? #{:play :demo} (:state app))
             (= :play (:state w))
             (< (:time-left w) hurry-threshold))
      hurry-tempo
      100)))

(defn paused?
  "True while the app is on its pause screen."
  [app]
  (= :pause (:state app)))

(defn sfx-muted?
  "True while the active world's state is :dying, when the die jingle owns
   the sound."
  [app]
  (= :dying (:state (active-world app))))

(defn cues
  "Everything the loop needs for one tick: {:sfx [...] :music m :tempo t
   :pause? p :sfx-muted? s}. :sfx is empty while effects are muted."
  [app log-lines]
  (let [muted (sfx-muted? app)]
    {:sfx (if muted [] (sfx-for log-lines))
     :music (music-for app)
     :tempo (tempo-for app)
     :pause? (paused? app)
     :sfx-muted? muted}))

(defn sound-on?
  "True when the sound setting value is not 0."
  [setting-value]
  (not= 0 (long setting-value)))
