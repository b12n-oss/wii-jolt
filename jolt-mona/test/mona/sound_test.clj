(ns mona.sound-test
  (:require [clojure.test :refer [deftest is run-tests]]
            [mona.app :as app]
            [mona.music :as music]
            [mona.sound :as sound]))

(def dt (/ 1.0 30))

(def no-input {:ch nil :held #{} :pressed #{} :any-pressed? false :connected #{}})

(def start (assoc no-input :pressed #{:select} :any-pressed? true :connected #{0} :ch 0))

(def playing
  (reduce (fn [a _] (app/tick a no-input dt)) (app/tick (app/init 0) start dt) (range 70)))

(def rule-events
  "Every event the rules emit. Must match
   `grep -o 'add-event [^)]*' jolt-mona/src/mona/rules.clj` plus :star from add-star."
  [:jump :stomp :kick :bump :break :die :defeat :flag :pickup :item :grow :shrink :1up :sparkle :stored :switch :star])

(deftest every-rule-event-has-an-effect
  (is (= 17 (count rule-events)))
  (doseq [k rule-events]
    (is (some? (music/effect-id k)) (str k " has no effect id"))
    (is (= [(music/effect-id k)] (sound/sfx-for [(str "event " (name k) " mode=play")])) (str k " is dropped by sfx-for")))
  (is (= [] (sound/sfx-for ["mode play" "stage 1 1-1 HELLO WORLD" "event nope mode=play"]))))

(def world-states
  "The states update-world in rules.clj has a case for."
  [:play :dying :dead :flag :castle :tally :clear])

(deftest every-world-state-has-its-cue
  (let [cue (fn [st] (sound/music-for (assoc-in playing [:world :state] st)))
        expected {:play {:song (music/song-id :day) :loop? true}
                  :dying {:song (music/song-id :die) :loop? false}
                  :flag {:song (music/song-id :clear) :loop? false}
                  :castle {:song (music/song-id :clear) :loop? false}
                  :tally {:song (music/song-id :clear) :loop? false}
                  :dead nil
                  :clear nil}]
    (is (= (set world-states) (set (keys expected))))
    (doseq [st world-states]
      (is (= (get expected st) (cue st)) (str "world state " st)))))

(deftest title-plays-the-title-tune-once
  (let [a (app/init 0)]
    (is (= {:song (music/song-id :title) :loop? true} (sound/music-for a)))
    (is (= (sound/music-for a) (sound/music-for (app/tick a no-input dt))) "the same cue, so the main loop does not restart it")))

(deftest intro-is-silent-then-the-theme-plays
  (let [a (-> (app/init 0) (app/tick start dt))]
    (is (nil? (sound/music-for a)))
    (let [a (reduce (fn [a _] (app/tick a no-input dt)) a (range 70))]
      (is (= :play (:state a)))
      (is (= :play (:state (:world a))))
      (is (= {:song (music/song-id :day) :loop? true} (sound/music-for a))))))

(deftest themes-map-to-songs
  (doseq [theme [:day :dusk :cloud :cave :castle]]
    (let [a (-> playing (assoc-in [:world :level :theme] theme))]
      (is (= (music/song-id theme) (:song (sound/music-for a)))))))

(deftest hurry-under-100
  (is (= 100 (sound/tempo-for (assoc-in playing [:world :time-left] 100.0))))
  (is (= 125 (sound/tempo-for (assoc-in playing [:world :time-left] 99.9)))))

(deftest jingles-ignore-the-hurry-tempo
  (let [a (-> playing (assoc-in [:world :time-left] 50.0) (assoc-in [:world :state] :dying))]
    (is (= {:song (music/song-id :die) :loop? false} (sound/music-for a)))
    (is (= 100 (sound/tempo-for a)))))

(deftest death-mutes-effects-and-restarts-the-tune
  (let [dying (assoc-in playing [:world :state] :dying)]
    (is (sound/sfx-muted? dying))
    (is (= [] (:sfx (sound/cues dying ["event stomp mode=play"]))))
    (let [a (app/tick (assoc-in playing [:world :state] :dead) no-input dt)]
      (is (= :intro (:state a)))
      (is (nil? (sound/music-for a)) "the intro card is silent; the theme restarts on play"))))

(deftest flag-starts-the-fanfare
  (is (= {:song (music/song-id :clear) :loop? false} (sound/music-for (assoc-in playing [:world :state] :flag))))
  (is (= {:song (music/song-id :clear) :loop? false} (sound/music-for (assoc-in playing [:world :state] :tally)))))

(deftest pause-and-resume
  (let [p (app/tick playing (assoc no-input :pressed #{:menu}) dt)]
    (is (sound/paused? p))
    (is (= (sound/music-for playing) (sound/music-for p)) "the song cue holds through a pause")
    (is (not (sound/paused? (app/tick (app/tick p no-input dt) (assoc no-input :pressed #{:back}) dt))))))

(deftest over-and-win
  (is (= {:song (music/song-id :over) :loop? false} (sound/music-for (assoc (app/init 0) :state :over))))
  (is (= {:song (music/song-id :win) :loop? true} (sound/music-for (assoc (app/init 0) :state :win)))))

(deftest demo-plays-like-play
  (let [a (reduce (fn [a _] (app/tick a no-input dt)) (app/init 0) (range (int (* 10.5 30))))]
    (is (= :demo (:state a)))
    (is (= (music/song-id :day) (:song (sound/music-for a))))))

(deftest sound-setting-reads
  (is (sound/sound-on? 1)) (is (sound/sound-on? 7)) (is (not (sound/sound-on? 0))))

(defn -main [& _] (let [r (run-tests 'mona.sound-test)] (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1))))
