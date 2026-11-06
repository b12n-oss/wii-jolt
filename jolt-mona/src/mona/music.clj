(ns mona.music
  "Super Mona's sound data: the rule-event effects, the instruments and the
   songs, plus the small note language the songs are written in. Plain
   Clojure with no FFI, so it loads under jolt and babashka alike. The C
   header that the Wii side reads is emitted from here by emit-header, which
   scripts/music-to-c writes to jolt-gfx/mona_music.h."
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
   from f0 to f1 Hz over ms milliseconds at volume vol (0..15). Tuned from the
   numbers, not yet by ear on the console: short pulse slides for the hero's
   moves, noise for the knocks, little arpeggios for the rewards."
  [{:wave :pulse :duty 1 :chan 0 :attack 0 :release 30
    :steps [[280 760 90 12]]}                                   ; :jump, an upward slide
   {:wave :pulse :duty 2 :chan 0 :attack 0 :release 30
    :steps [[700 350 40 13] [350 120 60 11]]}                   ; :stomp, a squash in two drops
   {:wave :pulse :duty 2 :chan 1 :attack 0 :release 20
    :steps [[220 110 50 14]]}                                   ; :kick, a short thud
   {:wave :noise :duty 0 :chan 2 :attack 0 :release 20
    :steps [[1800 300 50 12]]}                                  ; :bump
   {:wave :noise :duty 0 :chan 2 :attack 0 :release 40
    :steps [[1600 400 70 13] [700 150 120 11]]}                 ; :break, crack then rubble
   {:wave :pulse :duty 1 :chan 0 :attack 0 :release 20
    :steps [[440 440 10 0]]}                                    ; :die (silent, the jingle plays)
   {:wave :pulse :duty 1 :chan 0 :attack 0 :release 30
    :steps [[700 700 50 13] [450 450 70 12]]}                   ; :defeat, two falling blips
   {:wave :pulse :duty 1 :chan 1 :attack 0 :release 60
    :steps [[1200 300 600 12]]}                                 ; :flag, the slide down the pole
   {:wave :pulse :duty 2 :chan 0 :attack 0 :release 30
    :steps [[1047 1047 40 12] [1568 1568 110 11]]}              ; :pickup, C6 up a fifth
   {:wave :pulse :duty 1 :chan 0 :attack 0 :release 30
    :steps [[330 330 40 12] [440 440 40 12] [554 554 40 12]
            [659 659 40 12] [880 880 80 11]]}                   ; :item, an A major rise
   {:wave :pulse :duty 1 :chan 0 :attack 0 :release 30
    :steps [[392 523 70 12] [494 659 70 12] [587 784 70 12]
            [784 1047 120 12]]}                                 ; :grow, four rising scoops
   {:wave :pulse :duty 1 :chan 0 :attack 0 :release 30
    :steps [[1047 784 70 12] [784 587 70 12] [587 440 70 12]
            [440 294 120 11]]}                                  ; :shrink, the scoops falling
   {:wave :pulse :duty 1 :chan 0 :attack 0 :release 40
    :steps [[784 784 60 12] [988 988 60 12] [1175 1175 60 12]
            [1568 1568 60 12] [1319 1319 60 12] [1760 1760 120 12]]} ; :1up
   {:wave :pulse :duty 2 :chan 1 :attack 0 :release 15
    :steps [[2400 3200 35 10] [3200 2600 35 9]]}                ; :sparkle, 70 ms
   {:wave :pulse :duty 1 :chan 1 :attack 0 :release 20
    :steps [[1319 1319 40 12] [1047 1047 60 11]]}               ; :stored
   {:wave :pulse :duty 2 :chan 1 :attack 0 :release 15
    :steps [[3000 2000 30 12]]}])                               ; :switch, a 30 ms click

(def instruments
  "Melodic instruments by keyword. :a, :d and :r are milliseconds; :s is the
   sustain level 0..15; :vol is the peak level 0..15. Short decays and low
   sustains give the chip feel; :soft and :pad are the slow ones."
  {:lead {:wave :pulse :duty 1 :a 2 :d 40 :s 10 :r 50 :vol 15}
   :harm {:wave :pulse :duty 2 :a 2 :d 60 :s 7 :r 50 :vol 11}
   :bass {:wave :tri :duty 0 :a 1 :d 20 :s 15 :r 30 :vol 15}
   :brass {:wave :pulse :duty 2 :a 4 :d 60 :s 11 :r 50 :vol 15}
   :soft {:wave :pulse :duty 2 :a 10 :d 120 :s 6 :r 100 :vol 10}
   :bell {:wave :pulse :duty 0 :a 2 :d 160 :s 5 :r 180 :vol 15}
   :pad {:wave :pulse :duty 2 :a 40 :d 200 :s 7 :r 200 :vol 9}
   :hollow {:wave :pulse :duty 2 :a 2 :d 90 :s 6 :r 60 :vol 15}
   :stab {:wave :pulse :duty 0 :a 1 :d 30 :s 7 :r 30 :vol 15}})

(defn- pat
  "A pattern from its four tracks."
  [p1 p2 tri noise]
  {:p1 p1 :p2 p2 :tri tri :noise noise})

(def songs
  "Songs by id. :loop is the order index to loop back to, or nil. :order lists
   pattern indexes. Every pattern below is two bars of 4/4 (32 rows), except
   the die jingle's one bar. All melodies are original to this game."
  {;; Title: G major, 120 bpm. Day's hook (d b d g, then the fall to f#)
   ;; broadened into brass leaps over an oom-pah bass.
   :title
   {:rpm 480 :loop 0 :order [0 1 0 2]
    :patterns
    [(pat [:brass "d4:2 g4:2 d5:4 b4:2 d5:2 g5:4 f#5:3 e5 d5:4 c5:2 e5:2 d5:4"]
          [:harm "b3:8 d4:8 c4:8 a3:4 f#3:4"]
          [:bass "g2:2 -:2 g2:2 d2:2 g2:2 -:2 g2:2 d2:2 d2:2 -:2 d2:2 a2:2 d2:2 -:2 d2:2 f#2:2"]
          "k - - - s - h - k - k - s - h h k - - - s - h - k - k - s - h h")
     (pat [:brass "e5:2 g5:2 b5:4 a5:3 g5 e5:4 c5:2 e5:2 a5:4 f#5:2 a5:2 d6:4"]
          [:harm "g3:8 b3:8 e4:8 f#4:8"]
          [:bass "e2:2 -:2 e2:2 b2:2 e2:2 -:2 e2:2 b2:2 a2:2 -:2 a2:2 e2:2 d2:2 -:2 d2:2 a2:2"]
          "k - - - s - h - k - k - s - h h k - - - s - h - k - k - s s s s")
     (pat [:brass "b5:3 a5 g5:4 e5:2 c5:2 a4:4 d5:2 f#5:2 a5:2 c6:2 b5:8"]
          [:harm "d4:8 c4:8 c4:8 b3:8"]
          [:bass "g2:2 -:2 g2:2 d3:2 a2:2 -:2 a2:2 e2:2 d2:2 -:2 d2:2 a2:2 g2:4 d2:4"]
          "k - - - s - h - k - k - s - h h k - s - k - s - k:8")]}

   ;; Day: G major, 132 bpm, bright. Off-beat harmony and a walking bass.
   :day
   {:rpm 528 :loop 0 :order [0 1 0 2 0 1]
    :patterns
    [(pat [:lead "d5:2 b4 d5 g5:3 f#5 e5:2 d5:2 b4:2 c5:2 a4:2 c5 e5 d5:4 -:2 c5 a4 f#4:4"]
          [:harm "-:2 b3:2 -:2 d4:2 -:2 b3:2 -:2 d4:2 -:2 c4:2 -:2 e4:2 -:2 d4:2 -:2 f#4:2"]
          [:bass "g2:4 a2:4 b2:4 d3:4 a2:4 c3:4 d3:4 f#2:4"]
          "k - h - s - h - k - h - s h s - k - h - s - h - k - h - s h s -")
     (pat [:lead "e5:2 g5:2 b5:3 a5 g5:2 e5:2 d5:2 e5:2 c5:3 d5 e5:2 g5:2 a5:4 d5:2 f#5:2"]
          [:harm "-:2 g3:2 -:2 b3:2 -:2 g3:2 -:2 e4:2 -:2 g3:2 -:2 e4:2 -:2 a3:2 -:2 f#4:2"]
          [:bass "e2:4 g2:4 c3:4 e3:4 g2:4 e2:4 d2:4 f#2:4"]
          "k - h - s - h - k - h - s h s - k - h - s - h - k - h - s h s -")
     (pat [:lead "g5:2 e5:2 c5 e5 g5:2 f#5:2 a5:2 d5:4 b4:2 e5:2 g5:3 e5 f#5:2 a5:2 d5:2 c5:2"]
          [:harm "-:2 e3:2 -:2 g3:2 -:2 f#3:2 -:2 a3:2 -:2 g3:2 -:2 b3:2 -:2 f#3:2 -:2 a3:2"]
          [:bass "c3:4 b2:4 a2:4 d3:4 e2:4 g2:4 d2:4 f#2:4"]
          "k - h k s - h - k - h k s - s s k - h k s - h - k - h k s - s s")]}

   ;; Dusk: D minor, 100 bpm. A slow soft pad under a sighing tune.
   :dusk
   {:rpm 400 :loop 0 :order [0 1 0 2]
    :patterns
    [(pat [:lead "a4:4 f4:2 g4:2 a4:3 c5 d5:4 c5:2 bb4:2 a4:2 f4:2 g4:6 -:2"]
          [:soft "f3:8 a3:8 d3:8 bb3:8"]
          [:bass "d2:4 -:2 a2:2 d3:4 a2:4 bb2:4 -:2 f2:2 g2:4 d2:4"]
          "k - - - s - - h - - k - s - - - k - - - s - - h - - k - s - - -")
     (pat [:lead "bb4:2 d5:2 g5:4 f5:2 e5:2 c#5:4 e5:2 g5:2 f5:3 e5 d5:8"]
          [:soft "d3:8 c#3:8 e3:8 f3:8"]
          [:bass "g2:4 -:2 d2:2 a2:4 e2:4 a2:4 -:2 c#3:2 d3:4 a2:4"]
          "k - - - s - - h - - k - s - - - k - - - s - - h - - k - s - h -")
     (pat [:lead "d5:3 c5 bb4:2 c5:2 f5:4 e5:2 c5:2 bb4:2 a4:2 g4:2 bb4:2 a4:8"]
          [:soft "f3:8 c3:8 d3:8 c#3:8"]
          [:bass "bb2:4 -:2 f2:2 a2:4 f2:4 g2:4 -:2 d2:2 a2:4 e2:4"]
          "k - - - s - - h - - k - s - - - k - - - s - - h - - k - s - h -")]}

   ;; Cloud: F lydian, 112 bpm. A thin bell high up, a slow pad, hats only.
   :cloud
   {:rpm 448 :loop 0 :order [0 1 0 2]
    :patterns
    [(pat [:bell "c6:2 -:2 a5:2 -:2 f5:4 -:2 b5:2 c6:4 e6:4 d6:2 -:2 c6:2 -:2"]
          [:pad "f4:16 e4:8 g4:8"]
          [:bass "f3:6 -:2 c4:4 -:4 c3:6 -:2 g3:4 -:4"]
          "h - - - - - - - h - - - - - - - h - - - - - - - h - - - h - - -")
     (pat [:bell "d6:2 -:2 f6:4 -:2 e6:2 c6:4 a5:4 -:4 g5:2 a5:2 c6:4"]
          [:pad "d4:16 c4:8 e4:8"]
          [:bass "bb2:6 -:2 f3:4 -:4 a2:6 -:2 c3:4 -:4"]
          "h - - - - - - - h - - - - - - - h - - - - - - - h - - - h - - -")
     (pat [:bell "a5:2 -:2 d6:2 -:2 e6:2 -:2 f6:4 -:4 g6:2 e6:2 c6:8"]
          [:pad "f4:16 e4:16"]
          [:bass "d3:6 -:2 a3:4 -:4 c3:6 -:2 g3:4 -:4"]
          "h - - - - - - - h - - - - - - - h - - - - - - - k - - - h - - -")]}

   ;; Cave: A minor, 96 bpm, low and hollow. Low drum and hat only, no snare.
   :cave
   {:rpm 384 :loop 0 :order [0 1 0 2]
    :patterns
    [(pat [:hollow "a3:4 e4:2 c4:2 b3:4 -:2 d4 c4 a3:2 e3:2 g#3:4 a3:6 -:2"]
          [:harm "-:4 e3:4 -:4 c3:4 -:4 c3:4 -:4 b2:4"]
          [:bass "a2:4 -:4 a2:2 e2:2 -:4 f2:4 -:4 e2:4 -:4"]
          "k - - - h - - - k - - k h - - - k - - - h - - - k - - k h - - -")
     (pat [:hollow "f4:4 e4:2 d4:2 a4:4 g4:2 f4:2 e4:2 c4:2 d4:2 b3:2 g#3:4 b3:4"]
          [:harm "-:4 a3:4 -:4 f3:4 -:4 g#3:4 -:4 e3:4"]
          [:bass "d2:4 -:4 d2:2 a2:2 -:4 e2:4 -:4 e2:2 b2:2 -:4"]
          "k - - - h - - - k - - k h - - - k - - - h - - - k - - k h - h -")
     (pat [:hollow "-:4 e4 -:3 c4 -:3 a3:4 -:4 d4 -:3 b3 -:3 g#3:4"]
          [:harm "c3:16 c3:8 b2:8"]
          [:bass "a2:8 -:8 f2:8 e2:8"]
          "k - - - - - - - h - - - - - - - k - - - - - - - h - - - - - - -")]}

   ;; Castle: C minor, 152 bpm. A pedal C in the bass with Db and B leaning
   ;; on it, clipped thin stabs, a busy kit.
   :castle
   {:rpm 608 :loop 0 :order [0 1 0 2 1 2]
    :patterns
    [(pat [:stab "g4:2 ab4:2 g4:2 -:2 eb5:3 d5 c5:2 b4:2 c5:2 -:2 g4:2 f#4:2 g4:4 -:4"]
          [:harm "c4:2 -:2 c4:2 -:2 eb4:2 -:2 d4:2 -:2 eb4:2 -:2 c4:2 -:2 b3:4 -:4"]
          [:bass "c2:2 c2:2 c2:2 db2:2 c2:2 c2:2 b1:2 c2:2 c2:2 c2:2 c2:2 db2:2 c2:2 c2:2 b1:2 c2:2"]
          "k h s h k h s h k h s h k k s h k h s h k h s h k h s h k k s h")
     (pat [:stab "c5:2 eb5:2 g5:2 f#5:2 g5:4 ab5:2 g5:2 f5:2 eb5:2 d5:2 eb5:2 b4:4 -:4"]
          [:harm "-:2 g4:2 -:2 g4:2 -:2 ab4:2 -:2 g4:2 -:2 ab4:2 -:2 g4:2 -:2 f4:2 -:2 d4:2"]
          [:bass "c2:2 c2:2 db2:2 c2:2 c2:2 c2:2 b1:2 c2:2 c2:2 c2:2 db2:2 db2:2 c2:2 b1:2 b1:2 c2:2"]
          "k h s h k h s h k h s h k k s h k h s h k h s h k h s h s s s s")
     (pat [:stab "g4 ab4 g4 ab4 b4:4 c5 db5 c5 db5 d5:4 eb5:4 d5:2 db5:2 c5:4 b4:4"]
          [:harm "eb4:8 f4:8 g4:8 g3:8"]
          [:bass "c2:2 c2:2 c2:2 db2:2 c2:2 c2:2 b1:2 c2:2 c2:2 c2:2 c2:2 db2:2 c2:2 c2:2 b1:2 c2:2"]
          "k h s h k h s h k h s h k k s h k h s h k h s h k k s s k s s s")]}

   ;; Clear: G major, 150 bpm, once. A climb that lands on the tonic.
   :clear
   {:rpm 600 :loop nil :order [0]
    :patterns
    [(pat [:brass "g4:2 a4:2 b4:2 d5:2 c5:2 e5:2 f#5:4 g5:2 a5:2 b5:3 a5 g5:8"]
          [:harm "-:4 d4:4 e4:4 c4:4 d4:8 b4:8"]
          [:bass "g2:4 b2:4 c3:4 d3:4 d2:8 g2:8"]
          "k - - - k - - - k - - - s - s s s - s - s - s - k:8")]}

   ;; Die: E minor, 120 bpm, once, 2 s. A drop by thirds to a low E.
   :die
   {:rpm 480 :loop nil :order [0]
    :patterns
    [(pat [:lead "e5:2 b4:2 g4:2 d#4:2 e4:2 b3:2 e3:4"]
          [:harm "g4:2 d#4:2 b3:2 a3:2 g3:2 f#3:2 -:4"]
          [:bass "e3:4 b2:4 g2:4 e2:4"]
          "-:16")]}

   ;; Over: A minor, 90 bpm, once, 5.3 s. Slow falling lines to a low A.
   :over
   {:rpm 360 :loop nil :order [0]
    :patterns
    [(pat [:lead "e5:4 c5:2 a4:2 d5:4 b4:2 g#4:2 f4:3 e4 d4:2 b3:2 a3:8"]
          [:soft "a3:8 g#3:8 d3:8 c3:8"]
          [:bass "a2:8 e2:8 d2:8 a1:8"]
          "-:16 -:8 k:8")]}

   ;; Win: C major, 116 bpm, a march. The bass repeats one figure (root,
   ;; fifth, third, root, fifth) moved to each chord; a marching snare.
   :win
   {:rpm 464 :loop 0 :order [0 1 0 2]
    :patterns
    [(pat [:brass "e5:3 d5 c5:2 g4:2 c5:4 e5:2 g5:2 f5:3 e5 d5:2 b4:2 g4:8"]
          [:harm "e4:2 -:2 e4:2 -:2 e4:2 -:2 g4:2 -:2 d4:2 -:2 f4:2 -:2 d4:2 -:2 b3:2 -:2"]
          [:bass "c2:2 -:2 g2:2 e2:2 c2:2 -:2 g2:2 -:2 g2:2 -:2 d3:2 b2:2 g2:2 -:2 d3:2 -:2"]
          "s - s s k - s - s - s s k - s s s - s s k - s - s - s s k - s s")
     (pat [:brass "a5:3 g5 f5:2 a5:2 c6:4 a5:4 g5:3 e5 c5:2 e5:2 d5:4 -:4"]
          [:harm "c4:2 -:2 f4:2 -:2 c4:2 -:2 f4:2 -:2 e4:2 -:2 g4:2 -:2 d4:2 -:2 b3:2 -:2"]
          [:bass "f2:2 -:2 c3:2 a2:2 f2:2 -:2 c3:2 -:2 c2:2 -:2 g2:2 e2:2 g2:2 -:2 d3:2 -:2"]
          "s - s s k - s - s - s s k - s s s - s s k - s - s - s s k - s s")
     (pat [:brass "e5:2 g5:2 c6:4 b5:2 a5:2 g5:4 f5:2 d5:2 b4:2 d5:2 c5:8"]
          [:harm "g4:8 e4:8 d4:8 e4:8"]
          [:bass "c2:2 -:2 g2:2 e2:2 a2:2 -:2 e2:2 -:2 g2:2 -:2 d3:2 b2:2 c2:2 -:2 g2:2 -:2"]
          "s - s s k - s - s - s s k - s s s - s s k - s - k - k - k:4")]}})

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
  "The rows song k plays over its whole order, or 0 when k has no song."
  [k]
  (let [s (get songs k)]
    (reduce + 0 (map (fn [i] (pattern-rows (nth (:patterns s) i))) (:order s)))))

(defn song-seconds
  "How long song k plays once through its order, in seconds: rows * 60 / rpm."
  [k]
  (/ (* (song-rows k) 60.0) (:rpm (get songs k))))

(defn melody
  "The :p1 cells of song k's patterns concatenated in order, as parse-track
   returns them. Empty when k has no song."
  [k]
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

(def ^:private effect-index
  (zipmap effect-ids (range)))

(def ^:private song-index
  (zipmap song-ids (range)))

(defn effect-id
  "The C index of effect keyword k, or nil when k is not an effect id."
  [k]
  (get effect-index k))

(defn song-id
  "The C index of song keyword k, or nil when k is not a song id."
  [k]
  (get song-index k))

(def ^:private wave-values {:pulse 0 :tri 1 :noise 2})

(def ^:private instrument-keys
  "Instrument keywords in the sorted order the C table uses."
  (vec (sort (keys instruments))))

(def ^:private instrument-index
  (zipmap instrument-keys (range)))

(defn- c-name
  "The upper-case C macro suffix of keyword k, as in :1up to 1UP."
  [k]
  (str/upper-case (name k)))

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
  [k]
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
  [[inst-key text]]
  (let [inst (get instrument-index inst-key)]
    (map (fn [c] [(note-value c) inst]) (parse-track text))))

(defn- drum-pairs
  "The snd_cell values of the drum track, as [id 0] pairs."
  [text]
  (map (fn [c] [(drum-value c) 0]) (parse-drums text)))

(defn- pattern-tracks
  "The four tracks of pattern p as vectors of [note inst] pairs."
  [p]
  [(melodic-cells (:p1 p)) (melodic-cells (:p2 p)) (melodic-cells (:tri p))
   (drum-pairs (:noise p))])

(def ^:private empty-pattern
  "One row of rests on all four tracks."
  [[[-1 0]] [[-1 0]] [[-1 0]] [[-1 0]]])

(defn- song-parts
  "A song as {:rpm :loop :order :patterns}, where each pattern is a vector of
   four tracks of [note inst] pairs. A song missing from songs is one pattern
   of one row of rests, played once at 480 rpm."
  [k]
  (if-let [s (get songs k)]
    {:rpm (:rpm s) :loop (:loop s) :order (:order s)
     :patterns (mapv pattern-tracks (:patterns s))}
    {:rpm 480 :loop nil :order [0] :patterns [empty-pattern]}))

(defn- cells-name
  "The C array name of one track: song index i, pattern j, track k."
  [i j k]
  (str "mona_s" i "_p" j "_t" k))

(defn- emit-cells
  "One snd_cell array."
  [i j k cells]
  (str "static const snd_cell " (cells-name i j k) "[] = {"
       (join ", " (map (fn [[n inst]] (str "{" n ", " inst "}")) cells)) "};"))

(defn- emit-song
  "The C text of song index i: its cell arrays, patterns, order and nothing
   else. The snd_song row itself goes in the mona_songs table."
  [i parts]
  (let [patterns (:patterns parts)]
    (join "\n"
          (concat
           (for [[j tracks] (map-indexed vector patterns)
                 [k cells] (map-indexed vector tracks)]
             (emit-cells i j k cells))
           [(str "static const snd_pattern mona_s" i "_patterns[] = {")]
           (for [[j tracks] (map-indexed vector patterns)]
             (str "    {" (count (first tracks)) ", {"
                  (join ", " (map (fn [k] (cells-name i j k)) (range 4))) "}},"))
           ["};"
            (str "static const uint8_t mona_s" i "_order[] = {"
                 (join ", " (:order parts)) "};")]))))

(defn- emit-song-row
  "The snd_song initialiser for song index i."
  [i parts]
  (str "    {" (:rpm parts) ", " (count (:patterns parts)) ", " (count (:order parts)) ", "
       (if (nil? (:loop parts)) -1 (:loop parts)) ", mona_s" i "_patterns, mona_s" i "_order},"))

(defn emit-header
  "The C header for the sound data as a string. The output depends only on
   the data, so two calls return the same text."
  []
  (let [parts (mapv song-parts song-ids)]
    (str "// Generated by scripts/music-to-c from jolt-mona/src/mona/music.clj. Do not edit.\n"
         "// Regenerate: scripts/music-to-c\n"
         "#include \"jolt_snd.h\"\n"
         (join "" (map-indexed (fn [i k] (str "#define SFX_" (c-name k) " " i "\n")) effect-ids))
         (join "" (map-indexed (fn [i k] (str "#define SONG_" (c-name k) " " i "\n")) song-ids))
         "#define MONA_EFFECTS " (count effect-ids) "\n"
         "#define MONA_SONGS " (count song-ids) "\n"
         "static const snd_effect mona_effects[MONA_EFFECTS] = {\n"
         (join "\n" (map emit-effect effects)) "\n};\n"
         "static const snd_instrument mona_instruments[" (count instrument-keys) "] = {\n"
         (join "\n" (map emit-instrument instrument-keys)) "\n};\n"
         (join "" (map-indexed (fn [i p] (str (emit-song i p) "\n")) parts))
         "static const snd_song mona_songs[MONA_SONGS] = {\n"
         (join "\n" (map-indexed emit-song-row parts)) "\n};\n")))

(defn -main
  "Validates the data, then writes the header to the path in out (default
   jolt-gfx/mona_music.h): to out.tmp first, then renamed over out."
  [& [out]]
  (validate)
  (let [out (or out "jolt-gfx/mona_music.h")
        tmp (str out ".tmp")]
    (spit tmp (emit-header))
    (when-not (.renameTo (java.io.File. tmp) (java.io.File. out))
      (fail (str "could not rename " tmp " over " out) {:out out}))))
