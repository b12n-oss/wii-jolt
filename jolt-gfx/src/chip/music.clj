(ns chip.music
  "The note language shared by the chip-tune sound data: the parser for note
   and drum strings, the validators, and the C header emitter. A game keeps
   its own effects, instruments and songs and passes them in. Plain Clojure
   with no FFI, so it loads under jolt and babashka alike."
  (:require [clojure.string :as str]))

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

(defn song-rows
  "The rows song k of the songs map plays over its whole order, or 0 when k
   has no song."
  [songs k]
  (let [s (get songs k)]
    (reduce + 0 (map (fn [i] (pattern-rows (nth (:patterns s) i))) (:order s)))))

(defn song-seconds
  "How long song k of the songs map plays once through its order, in
   seconds: rows * 60 / rpm."
  [songs k]
  (/ (* (song-rows songs k) 60.0) (:rpm (get songs k))))

(defn melody
  "The :p1 cells of song k's patterns in the songs map, concatenated in
   order, as parse-track returns them. Empty when k has no song."
  [songs k]
  (let [s (get songs k)]
    (vec (mapcat (fn [i] (parse-track (second (:p1 (nth (:patterns s) i))))) (:order s)))))

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
  [instruments id s]
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
   on the first problem. Takes {:effect-ids :effects :instruments :songs}.
   Returns nil when all is well."
  [{:keys [effect-ids effects instruments songs]}]
  (when-not (= (count effect-ids) (count effects))
    (fail "effects are not aligned with effect-ids" {:ids (count effect-ids) :effects (count effects)}))
  (doseq [[id e] (map vector effect-ids effects)]
    (check-effect id e))
  (doseq [[id i] instruments]
    (check-instrument id i))
  (doseq [[id s] songs]
    (check-song instruments id s))
  nil)

(def ^:private wave-values {:pulse 0 :tri 1 :noise 2})

(defn- c-name
  "The upper-case C macro suffix of keyword k, as in :1up to 1UP. A hyphen
   becomes an underscore, so :catch-1 is CATCH_1."
  [k]
  (str/replace (str/upper-case (name k)) "-" "_"))

(defn- join
  "Joins the strings in coll with sep."
  [sep coll]
  (str/join sep coll))

(defn- emit-step
  "One snd_step initialiser."
  [[f0 f1 ms vol]]
  (str "{" f0 ", " f1 ", " ms ", " vol "}"))

(defn- emit-effect
  "One snd_effect initialiser line."
  [e]
  (str "    {" (get wave-values (:wave e)) ", " (:duty e) ", " (:chan e) ", "
       (:attack e) ", " (:release e) ", " (count (:steps e)) ", {"
       (join ", " (map emit-step (:steps e))) "}},"))

(defn- emit-instrument
  "One snd_instrument initialiser line, led by a comment naming it."
  [instruments k]
  (let [i (get instruments k)]
    (str "    /* " (name k) " */ {" (get wave-values (:wave i)) ", " (:duty i) ", "
         (:a i) ", " (:d i) ", " (:s i) ", " (:vol i) ", " (:r i) "},")))

(defn- note-value
  "The C note of a melodic cell: the MIDI number, -1 for a rest, -2 for a hold."
  [cell]
  (case (:note cell)
    :rest -1
    :hold -2
    (:note cell)))

(defn- drum-value
  "The C note of a drum cell: the drum id, or -1 for a rest."
  [cell]
  (let [d (:drum cell)]
    (if (= d :rest) -1 d)))

(defn- melodic-cells
  "The snd_cell values of one melodic track, as [note inst] pairs."
  [instrument-index [inst-key text]]
  (let [inst (get instrument-index inst-key)]
    (map (fn [c] [(note-value c) inst]) (parse-track text))))

(defn- drum-pairs
  "The snd_cell values of the drum track, as [id 0] pairs."
  [text]
  (map (fn [c] [(drum-value c) 0]) (parse-drums text)))

(defn- pattern-tracks
  "The four tracks of pattern p as vectors of [note inst] pairs."
  [instrument-index p]
  [(melodic-cells instrument-index (:p1 p)) (melodic-cells instrument-index (:p2 p))
   (melodic-cells instrument-index (:tri p)) (drum-pairs (:noise p))])

(def ^:private empty-pattern
  "One row of rests on all four tracks."
  [[[-1 0]] [[-1 0]] [[-1 0]] [[-1 0]]])

(defn- song-parts
  "A song as {:rpm :loop :order :patterns}, where each pattern is a vector of
   four tracks of [note inst] pairs. A song missing from songs is one pattern
   of one row of rests, played once at 480 rpm."
  [songs instrument-index k]
  (if-let [s (get songs k)]
    {:rpm (:rpm s) :loop (:loop s) :order (:order s)
     :patterns (mapv #(pattern-tracks instrument-index %) (:patterns s))}
    {:rpm 480 :loop nil :order [0] :patterns [empty-pattern]}))

(defn- cells-name
  "The C array name of one track: song index i, pattern j, track k."
  [prefix i j k]
  (str prefix "_s" i "_p" j "_t" k))

(defn- emit-cells
  "One snd_cell array: track :track of pattern :pattern of song :song."
  [{:keys [prefix song pattern track cells]}]
  (str "static const snd_cell " (cells-name prefix song pattern track) "[] = {"
       (join ", " (map (fn [[n inst]] (str "{" n ", " inst "}")) cells)) "};"))

(defn- emit-song
  "The C text of song index i: its cell arrays, patterns, order and nothing
   else. The snd_song row itself goes in the <prefix>_songs table."
  [prefix i parts]
  (let [patterns (:patterns parts)]
    (join "\n"
          (concat
           (for [[j tracks] (map-indexed vector patterns)
                 [k cells] (map-indexed vector tracks)]
             (emit-cells {:prefix prefix :song i :pattern j :track k :cells cells}))
           [(str "static const snd_pattern " prefix "_s" i "_patterns[] = {")]
           (for [[j tracks] (map-indexed vector patterns)]
             (str "    {" (count (first tracks)) ", {"
                  (join ", " (map (fn [k] (cells-name prefix i j k)) (range 4))) "}},"))
           ["};"
            (str "static const uint8_t " prefix "_s" i "_order[] = {"
                 (join ", " (:order parts)) "};")]))))

(defn- emit-song-row
  "The snd_song initialiser for song index i."
  [prefix i parts]
  (str "    {" (:rpm parts) ", " (count (:patterns parts)) ", " (count (:order parts)) ", "
       (if (nil? (:loop parts)) -1 (:loop parts)) ", " prefix "_s" i "_patterns, " prefix "_s" i "_order},"))

(defn emit-header
  "The C header for a game's sound data as a string. Takes {:prefix :source
   :include :effect-ids :song-ids :effects :instruments :songs}. prefix names
   the tables (\"mona\" gives mona_effects, MONA_EFFECTS, mona_s0_p0_t0),
   source is the path named in the Generated line, and include is the header
   file the generated #include line names. An optional :regen is the command
   text on the Regenerate line (default \"scripts/music-to-c\"). The output depends only on the
   data, so two calls return the same text."
  [{:keys [prefix source include effect-ids song-ids effects instruments songs regen]}]
  (let [instrument-keys (vec (sort (keys instruments)))
        instrument-index (zipmap instrument-keys (range))
        parts (mapv #(song-parts songs instrument-index %) song-ids)
        upper (str/upper-case prefix)]
    (str "// Generated by scripts/music-to-c from " source ". Do not edit.\n"
         "// Regenerate: " (or regen "scripts/music-to-c") "\n"
         "#include \"" include "\"\n"
         (join "" (map-indexed (fn [i k] (str "#define SFX_" (c-name k) " " i "\n")) effect-ids))
         (join "" (map-indexed (fn [i k] (str "#define SONG_" (c-name k) " " i "\n")) song-ids))
         "#define " upper "_EFFECTS " (count effect-ids) "\n"
         "#define " upper "_SONGS " (count song-ids) "\n"
         "static const snd_effect " prefix "_effects[" upper "_EFFECTS] = {\n"
         (join "\n" (map emit-effect effects)) "\n};\n"
         "static const snd_instrument " prefix "_instruments[" (count instrument-keys) "] = {\n"
         (join "\n" (map #(emit-instrument instruments %) instrument-keys)) "\n};\n"
         (join "" (map-indexed (fn [i p] (str (emit-song prefix i p) "\n")) parts))
         "static const snd_song " prefix "_songs[" upper "_SONGS] = {\n"
         (join "\n" (map-indexed (fn [i p] (emit-song-row prefix i p)) parts)) "\n};\n")))
