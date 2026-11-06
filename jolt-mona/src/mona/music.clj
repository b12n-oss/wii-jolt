(ns mona.music
  "Super Mona's sound data: the rule-event effects, the instruments and the
   songs, plus the small note language the songs are written in. Plain
   Clojure with no FFI, so it loads under jolt and babashka alike. The C
   header that the Wii side reads is emitted from here (emit-header is still
   a stub until the emitter lands)."
  (:require [clojure.string :as str]))

(def effect-ids
  "The rule events that have a sound effect, in the order the C tables use.
   :die has an effect entry that is silent, because the die jingle is music."
  [:jump :stomp :kick :bump :break :die :defeat :flag :pickup :item :grow
   :shrink :1up :sparkle :stored :switch])

(def song-ids
  "Every song id, in the order the C tables use."
  [:title :day :dusk :cloud :cave :castle :clear :die :over :win])

(def effects
  "One entry per effect-ids id. A step is [f0 f1 ms vol]: the frequency slides
   from f0 to f1 Hz over ms milliseconds at volume vol (0..15). These are
   starting points, tuned later."
  [{:wave :pulse :duty 1 :chan 0 :attack 0 :release 30
    :steps [[300 900 80 12]]}                                   ; :jump
   {:wave :pulse :duty 2 :chan 0 :attack 0 :release 30
    :steps [[600 150 90 13]]}                                   ; :stomp
   {:wave :pulse :duty 2 :chan 1 :attack 0 :release 20
    :steps [[200 200 60 14]]}                                   ; :kick
   {:wave :noise :duty 0 :chan 2 :attack 0 :release 20
    :steps [[2000 400 40 12]]}                                  ; :bump
   {:wave :noise :duty 0 :chan 2 :attack 0 :release 40
    :steps [[1500 300 160 13]]}                                 ; :break
   {:wave :pulse :duty 1 :chan 0 :attack 0 :release 20
    :steps [[440 440 10 0]]}                                    ; :die (silent)
   {:wave :pulse :duty 1 :chan 0 :attack 0 :release 30
    :steps [[600 600 60 13] [400 400 60 13]]}                   ; :defeat
   {:wave :pulse :duty 1 :chan 1 :attack 0 :release 60
    :steps [[1200 300 600 12]]}                                 ; :flag
   {:wave :pulse :duty 2 :chan 0 :attack 0 :release 30
    :steps [[900 900 50 12] [1300 1300 50 12]]}                 ; :pickup
   {:wave :pulse :duty 1 :chan 0 :attack 0 :release 30
    :steps [[400 400 50 12] [500 500 50 12] [600 600 50 12]
            [800 800 50 12]]}                                   ; :item
   {:wave :pulse :duty 1 :chan 0 :attack 0 :release 30
    :steps [[523 523 75 12] [659 659 75 12] [784 784 75 12]
            [1047 1047 75 12]]}                                 ; :grow
   {:wave :pulse :duty 1 :chan 0 :attack 0 :release 30
    :steps [[1047 1047 75 12] [784 784 75 12] [659 659 75 12]
            [523 523 75 12]]}                                   ; :shrink
   {:wave :pulse :duty 1 :chan 0 :attack 0 :release 40
    :steps [[659 659 60 12] [784 784 60 12] [988 988 60 12]
            [1319 1319 60 12] [1568 1568 60 12]]}               ; :1up
   {:wave :pulse :duty 2 :chan 1 :attack 0 :release 20
    :steps [[2400 2400 50 10]]}                                 ; :sparkle
   {:wave :pulse :duty 1 :chan 1 :attack 0 :release 20
    :steps [[1600 1600 40 12]]}                                 ; :stored
   {:wave :pulse :duty 2 :chan 1 :attack 0 :release 20
    :steps [[3000 3000 30 12]]}])                               ; :switch

(def instruments
  "Melodic instruments by keyword. :a, :d and :r are milliseconds; :s is the
   sustain level 0..15; :vol is the peak level 0..15."
  {:lead {:wave :pulse :duty 1 :a 2 :d 40 :s 10 :r 60 :vol 15}
   :harm {:wave :pulse :duty 2 :a 2 :d 60 :s 8 :r 80 :vol 12}
   :bass {:wave :tri :duty 0 :a 1 :d 20 :s 15 :r 40 :vol 15}})

(def ^:private placeholder-pattern
  {:p1 [:lead "c4 e4 g4 c5 e5 c5 g4 e4 c4:2 e4:2 g4:2 c5:2"]
   :p2 [:harm "c3:4 g3:4 a3:4 e3:4"]
   :tri [:bass "c2:4 g2:4 a2:4 e2:4"]
   :noise "k - s - k - s - k - s - k s h -"})

(def ^:private placeholder-pattern-2
  {:p1 [:lead "a4:2 g4:2 e4:2 c4:2 e4 g4 a4 c5 e5:2 c5:2"]
   :p2 [:harm "c3:4 g3:4 a3:4 e3:4"]
   :tri [:bass "c2:4 g2:4 a2:4 e2:4"]
   :noise "k - s - k - s - k - s - k s h -"})

(def songs
  "Songs by id. :loop is the order index to loop back to, or nil. :order lists
   pattern indexes. Only :title is written so far; it holds placeholder
   patterns that P2.T3 replaces."
  {:title {:rpm 480 :loop 0
           :patterns [placeholder-pattern placeholder-pattern-2]
           :order [0 1]}})

(def ^:private note-re #"([a-g])([#b]?)([0-8])(?::(\d+))?")
(def ^:private hold-re #"(-|=)(?::(\d+))?")
(def ^:private drum-re #"([ksh-])(?::(\d+))?")

(def ^:private digit-values (zipmap (map str "0123456789") (range)))
(def ^:private octave-values (zipmap (map str "012345678") (range)))
(def ^:private semitones {"c" 0 "d" 2 "e" 4 "f" 5 "g" 7 "a" 9 "b" 11})
(def ^:private accidentals {"#" 1 "b" -1 "" 0})
(def ^:private drum-ids {"k" 0 "s" 1 "h" 2})

(defn- fail
  "Throws ex-info with the message and data."
  [msg data]
  (throw (ex-info msg data)))

(defn- digits
  "The integer value of a string of decimal digits."
  [s]
  (reduce (fn [acc ch] (+ (* 10 acc) (get digit-values (str ch)))) 0 s))

(defn- suffix-count
  "The :n suffix of token t as a row count 1..64. A missing suffix is 1."
  [t sfx]
  (if (nil? sfx)
    1
    (let [n (digits sfx)]
      (if (and (>= n 1) (<= n 64))
        n
        (fail (str "row count out of range 1..64 in " t) {:token t})))))

(defn- occupy
  "A token's cells: the first is first-cell, the rest are rest-cell, n in all."
  [first-cell rest-cell n]
  (into [first-cell] (repeat (dec n) rest-cell)))

(defn- track-cells
  "The cells one track token occupies. A note is {:note midi}; a rest or a
   hold fills its own rows with the same kind of cell."
  [t]
  (let [m (re-matches note-re t)
        h (re-matches hold-re t)]
    (cond
      m (let [[_ letter acc octave sfx] m
              midi (+ (* 12 (inc (get octave-values octave)))
                      (get semitones letter)
                      (get accidentals acc))]
          (occupy {:note midi} {:note :hold} (suffix-count t sfx)))
      h (let [[_ mark sfx] h
              kind (if (= "-" mark) :rest :hold)]
          (occupy {:note kind} {:note kind} (suffix-count t sfx)))
      :else (fail (str "bad note token: " t) {:token t}))))

(defn- drum-cells
  "The cells one drum token occupies. A hit is {:drum 0..2}; a rest is
   {:drum :rest}, and a hit's extra rows are rests."
  [t]
  (let [m (re-matches drum-re t)]
    (if (nil? m)
      (fail (str "bad drum token: " t) {:token t})
      (let [[_ d sfx] m
            n (suffix-count t sfx)]
        (if (= "-" d)
          (occupy {:drum :rest} {:drum :rest} n)
          (occupy {:drum (get drum-ids d)} {:drum :rest} n))))))

(defn- tokens
  "The whitespace-separated tokens of s, with empty ones dropped."
  [s]
  (remove empty? (str/split s #"\s+")))

(defn parse-track
  "Parses a note string into one cell per row. Returns a vector of
   {:note midi}, {:note :rest} or {:note :hold}. Throws ex-info with
   {:token t} on a token it does not know."
  [s]
  (vec (mapcat track-cells (tokens s))))

(defn parse-drums
  "Parses a drum string into one cell per row. Returns a vector of
   {:drum 0}, {:drum 1}, {:drum 2} (k, s, h) or {:drum :rest} (-). Throws
   ex-info with {:token t} on a token it does not know."
  [s]
  (vec (mapcat drum-cells (tokens s))))

(defn pattern-rows
  "Returns the row count of pattern p when its four tracks agree. Throws
   ex-info with {:rows {:p1 n :p2 n :tri n :noise n}} when they differ."
  [p]
  (let [rows {:p1 (count (parse-track (second (:p1 p))))
              :p2 (count (parse-track (second (:p2 p))))
              :tri (count (parse-track (second (:tri p))))
              :noise (count (parse-drums (:noise p)))}]
    (if (apply = (vals rows))
      (:p1 rows)
      (fail "pattern tracks have different row counts" {:rows rows}))))

(defn- in-range?
  "True when x is a number from lo to hi inclusive."
  [lo hi x]
  (and (number? x) (>= x lo) (<= x hi)))

(defn- check-effect
  "Throws when an effect has no steps, too many steps, or a bad step."
  [id e]
  (let [steps (:steps e)]
    (when (or (empty? steps) (> (count steps) 6))
      (fail (str "effect " id " has " (count steps) " steps, want 1 to 6")
            {:effect id}))
    (doseq [step steps]
      (let [[f0 f1 _ vol] step]
        (when-not (in-range? 0 15 vol)
          (fail (str "effect " id " step volume outside 0..15") {:effect id :step step}))
        (when-not (and (in-range? 20 20000 f0) (in-range? 20 20000 f1))
          (fail (str "effect " id " frequency outside 20..20000 Hz") {:effect id :step step}))))))

(defn- check-instrument
  "Throws when an instrument field is out of range."
  [id i]
  (let [bad (fn [field] (fail (str "instrument " id " field " field " out of range")
                              {:instrument id :field field}))]
    (when-not (contains? #{:pulse :tri :noise} (:wave i)) (bad :wave))
    (when-not (in-range? 0 2 (:duty i)) (bad :duty))
    (when-not (in-range? 0 15 (:s i)) (bad :s))
    (when-not (in-range? 0 15 (:vol i)) (bad :vol))
    (doseq [field [:a :d :r]]
      (when-not (and (number? (get i field)) (>= (get i field) 0)) (bad field)))))

(defn- check-song
  "Throws when a song's loop or order is out of bounds, a track names an
   unknown instrument, or a pattern's rows disagree."
  [id s]
  (let [lp (:loop s)
        order (:order s)
        patterns (:patterns s)]
    (when (and (some? lp) (or (< lp 0) (>= lp (count order))))
      (fail (str "song " id " loop " lp " is outside its order") {:song id :loop lp}))
    (doseq [pi order]
      (when-not (and (>= pi 0) (< pi (count patterns)))
        (fail (str "song " id " order index " pi " is outside its patterns")
              {:song id :index pi})))
    (doseq [p patterns]
      (doseq [k [:p1 :p2 :tri]]
        (when-not (contains? instruments (first (k p)))
          (fail (str "song " id " track " k " names an unknown instrument")
                {:song id :track k :instrument (first (k p))})))
      (pattern-rows p))))

(defn validate
  "Checks the sound data: effects, instruments, then songs, throwing ex-info
   on the first problem. Returns nil when all is well."
  []
  (when-not (= (count effect-ids) (count effects))
    (fail "effects are not aligned with effect-ids" {:ids (count effect-ids) :effects (count effects)}))
  (doseq [[id e] (map vector effect-ids effects)]
    (check-effect id e))
  (doseq [[id i] instruments]
    (check-instrument id i))
  (doseq [[id s] songs]
    (check-song id s))
  nil)

(defn emit-header
  "The C header for the sound data as a string. Returns \"\" until the
   emitter lands (P2.T2)."
  []
  "")
