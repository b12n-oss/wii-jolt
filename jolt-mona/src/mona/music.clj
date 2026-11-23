(ns mona.music
  "Super Mona's sound data: the rule-event effects, the instruments and the
   songs, written in the note language of chip.music, which this namespace
   re-exports for the tests. Plain Clojure with no FFI, so it loads under
   jolt and babashka alike. The C header that the Wii side reads is emitted
   from here by emit-header, which scripts/music-to-c writes to
   jolt-gfx/mona_music.h."
  (:require [chip.music :as chip]
            [mona.music-ids :as ids]))

(def effect-ids
  "The rule events that have a sound effect. Lives in mona.music-ids so the Wii build can take the ids without the data."
  ids/effect-ids)

(def song-ids
  "Every song id, in the C table order. Lives in mona.music-ids so the Wii build can take the ids without the data."
  ids/song-ids)

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
    :steps [[1047 1047 40 12]]}                                 ; :pickup, a short single-step grab
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
    :steps [[3000 2000 30 12]]}                                 ; :switch, a 30 ms click
   {:wave :pulse :duty 2 :chan 0 :attack 0 :release 30
    :steps [[1047 1047 50 13] [1568 1568 50 13]]}])             ; :star, C6 then G6, coin-like

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

(def songs
  "Songs by id. :loop is the order index to loop back to, or nil. :order lists
   pattern indexes. Every pattern below is two bars of 4/4 (32 rows), except
   the die jingle's one bar. All melodies are original to this game."
  {;; Title: G major, 120 bpm. Day's hook (d b d g, then the fall to f#)
   ;; broadened into brass leaps over an oom-pah bass.
   :title
   {:rpm 480 :loop 0 :order [0 1 0 2]
    :patterns
    [{:p1 [:brass "d4:2 g4:2 d5:4 b4:2 d5:2 g5:4 f#5:3 e5 d5:4 b4:2 e5:2 d5:4"]
      :p2 [:harm "b3:8 d4:8 c4:8 a3:4 f#3:4"]
      :tri [:bass "g2:2 -:2 g2:2 d2:2 g2:2 -:2 g2:2 d2:2 d2:2 -:2 d2:2 a2:2 d2:2 -:2 d2:2 f#2:2"]
      :noise "k - - - s - h - k - k - s - h h k - - - s - h - k - k - s - h h"}
     {:p1 [:brass "e5:2 g5:2 b5:4 a5:3 g5 e5:4 c5:2 e5:2 a5:4 f#5:2 a5:2 d6:4"]
      :p2 [:harm "g3:8 b3:8 e4:8 f#4:8"]
      :tri [:bass "e2:2 -:2 e2:2 b2:2 e2:2 -:2 e2:2 b2:2 a2:2 -:2 a2:2 e2:2 d2:2 -:2 d2:2 a2:2"]
      :noise "k - - - s - h - k - k - s - h h k - - - s - h - k - k - s s s s"}
     {:p1 [:brass "b5:3 a5 g5:4 e5:2 c5:2 a4:4 d5:2 f#5:2 a5:2 c6:2 b5:8"]
      :p2 [:harm "d4:8 c4:8 c4:8 b3:8"]
      :tri [:bass "g2:2 -:2 g2:2 d3:2 a2:2 -:2 a2:2 e2:2 d2:2 -:2 d2:2 a2:2 g2:4 d2:4"]
      :noise "k - - - s - h - k - k - s - h h k - s - k - s - k:8"}]}

   ;; Day: G major, 132 bpm, bright. Off-beat harmony and a walking bass.
   :day
   {:rpm 528 :loop 0 :order [0 1 0 2 0 1]
    :patterns
    [{:p1 [:lead "d5:2 b4 d5 g5:3 f#5 e5:2 d5:2 b4:2 c5:2 a4:2 c5 e5 d5:4 -:2 c5 a4 f#4:4"]
      :p2 [:harm "-:2 b3:2 -:2 d4:2 -:2 b3:2 -:2 d4:2 -:2 c4:2 -:2 e4:2 -:2 d4:2 -:2 f#4:2"]
      :tri [:bass "g2:4 a2:4 b2:4 d3:4 a2:4 c3:4 d3:4 f#2:4"]
      :noise "k - h - s - h - k - h - s h s - k - h - s - h - k - h - s h s -"}
     {:p1 [:lead "e5:2 g5:2 b5:3 a5 g5:2 e5:2 d5:2 e5:2 c5:3 d5 e5:2 g5:2 a5:4 d5:2 f#5:2"]
      :p2 [:harm "-:2 g3:2 -:2 b3:2 -:2 g3:2 -:2 e4:2 -:2 g3:2 -:2 e4:2 -:2 a3:2 -:2 f#4:2"]
      :tri [:bass "e2:4 g2:4 c3:4 e3:4 g2:4 e2:4 d2:4 f#2:4"]
      :noise "k - h - s - h - k - h - s h s - k - h - s - h - k - h - s h s -"}
     {:p1 [:lead "g5:2 e5:2 c5 e5 g5:2 f#5:2 a5:2 d5:4 b4:2 e5:2 g5:3 e5 f#5:2 a5:2 d5:2 c5:2"]
      :p2 [:harm "-:2 e3:2 -:2 g3:2 -:2 f#3:2 -:2 a3:2 -:2 g3:2 -:2 b3:2 -:2 f#3:2 -:2 a3:2"]
      :tri [:bass "c3:4 b2:4 a2:4 d3:4 e2:4 g2:4 d2:4 f#2:4"]
      :noise "k - h k s - h - k - h k s - s s k - h k s - h - k - h k s - s s"}]}

   ;; Dusk: D minor, 100 bpm. A slow soft pad under a sighing tune.
   :dusk
   {:rpm 400 :loop 0 :order [0 1 0 2]
    :patterns
    [{:p1 [:lead "a4:4 f4:2 g4:2 a4:3 c5 d5:4 c5:2 bb4:2 a4:2 f4:2 g4:6 -:2"]
      :p2 [:soft "f3:8 a3:8 d3:8 bb3:8"]
      :tri [:bass "d2:4 -:2 a2:2 d3:4 a2:4 bb2:4 -:2 f2:2 g2:4 d2:4"]
      :noise "k - - - s - - h - - k - s - - - k - - - s - - h - - k - s - - -"}
     {:p1 [:lead "bb4:2 d5:2 g5:4 f5:2 e5:2 c#5:4 e5:2 g5:2 f5:3 e5 d5:8"]
      :p2 [:soft "d3:8 c#3:8 e3:8 f3:8"]
      :tri [:bass "g2:4 -:2 d2:2 a2:4 e2:4 a2:4 -:2 c#3:2 d3:4 a2:4"]
      :noise "k - - - s - - h - - k - s - - - k - - - s - - h - - k - s - h -"}
     {:p1 [:lead "d5:3 c5 bb4:2 c5:2 f5:4 e5:2 c5:2 bb4:2 a4:2 g4:2 bb4:2 a4:8"]
      :p2 [:soft "f3:8 c3:8 d3:8 c#3:8"]
      :tri [:bass "bb2:4 -:2 f2:2 a2:4 f2:4 g2:4 -:2 d2:2 a2:4 e2:4"]
      :noise "k - - - s - - h - - k - s - - - k - - - s - - h - - k - s - h -"}]}

   ;; Cloud: F lydian, 112 bpm. A thin bell high up, a slow pad, hats and one low drum.
   :cloud
   {:rpm 448 :loop 0 :order [0 1 0 2]
    :patterns
    [{:p1 [:bell "c6:2 -:2 a5:2 -:2 f5:4 -:2 b5:2 c6:4 e6:4 d6:2 -:2 c6:2 -:2"]
      :p2 [:pad "f4:16 e4:8 g4:8"]
      :tri [:bass "f3:6 -:2 c4:4 -:4 c3:6 -:2 g3:4 -:4"]
      :noise "h - - - - - - - h - - - - - - - h - - - - - - - h - - - h - - -"}
     {:p1 [:bell "d6:2 -:2 f6:4 -:2 e6:2 c6:4 a5:4 -:4 g5:2 a5:2 c6:4"]
      :p2 [:pad "d4:16 c4:8 e4:8"]
      :tri [:bass "bb2:6 -:2 f3:4 -:4 a2:6 -:2 c3:4 -:4"]
      :noise "h - - - - - - - h - - - - - - - h - - - - - - - h - - - h - - -"}
     {:p1 [:bell "a5:2 -:2 d6:2 -:2 e6:2 -:2 f6:4 -:4 g6:2 e6:2 c6:8"]
      :p2 [:pad "f4:16 e4:16"]
      :tri [:bass "d3:6 -:2 a3:4 -:4 c3:6 -:2 g3:4 -:4"]
      :noise "h - - - - - - - h - - - - - - - h - - - - - - - k - - - h - - -"}]}

   ;; Cave: A minor, 96 bpm, low and hollow. Low drum and hat only, no snare.
   :cave
   {:rpm 384 :loop 0 :order [0 1 0 2]
    :patterns
    [{:p1 [:hollow "a3:4 e4:2 c4:2 b3:4 -:2 d4 c4 a3:2 e3:2 g#3:4 a3:6 -:2"]
      :p2 [:harm "-:4 e3:4 -:4 c3:4 -:4 c3:4 -:4 b2:4"]
      :tri [:bass "a2:4 -:4 a2:2 e2:2 -:4 f2:4 -:4 e2:4 -:4"]
      :noise "k - - - h - - - k - - k h - - - k - - - h - - - k - - k h - - -"}
     {:p1 [:hollow "f4:4 e4:2 d4:2 a4:4 g4:2 f4:2 e4:2 c4:2 d4:2 b3:2 g#3:4 b3:4"]
      :p2 [:harm "-:4 a3:4 -:4 f3:4 -:4 g#3:4 -:4 e3:4"]
      :tri [:bass "d2:4 -:4 d2:2 a2:2 -:4 e2:4 -:4 e2:2 b2:2 -:4"]
      :noise "k - - - h - - - k - - k h - - - k - - - h - - - k - - k h - h -"}
     {:p1 [:hollow "-:4 e4 -:3 c4 -:3 a3:4 -:4 d4 -:3 b3 -:3 g#3:4"]
      :p2 [:harm "c3:16 c3:8 b2:8"]
      :tri [:bass "a2:8 -:8 f2:8 e2:8"]
      :noise "k - - - - - - - h - - - - - - - k - - - - - - - h - - - - - - -"}]}

   ;; Castle: C minor, 152 bpm. A pedal C in the bass with Db and B leaning
   ;; on it, clipped thin stabs, a busy kit.
   :castle
   {:rpm 608 :loop 0 :order [0 1 0 2 1 2]
    :patterns
    [{:p1 [:stab "g4:2 ab4:2 g4:2 -:2 eb5:3 d5 c5:2 b4:2 g4:2 -:2 ab4:2 f#4:2 g4:4 -:4"]
      :p2 [:harm "c4:2 -:2 c4:2 -:2 eb4:2 -:2 d4:2 -:2 eb4:2 -:2 c4:2 -:2 b3:4 -:4"]
      :tri [:bass "c2:2 c2:2 c2:2 db2:2 c2:2 c2:2 b1:2 c2:2 c2:2 c2:2 c2:2 db2:2 c2:2 c2:2 b1:2 c2:2"]
      :noise "k h s h k h s h k h s h k k s h k h s h k h s h k h s h k k s h"}
     {:p1 [:stab "c5:2 eb5:2 g5:2 f#5:2 g5:4 ab5:2 g5:2 f5:2 eb5:2 d5:2 eb5:2 b4:4 -:4"]
      :p2 [:harm "-:2 g4:2 -:2 g4:2 -:2 ab4:2 -:2 g4:2 -:2 ab4:2 -:2 g4:2 -:2 f4:2 -:2 d4:2"]
      :tri [:bass "c2:2 c2:2 db2:2 c2:2 c2:2 c2:2 b1:2 c2:2 c2:2 c2:2 db2:2 db2:2 c2:2 b1:2 b1:2 c2:2"]
      :noise "k h s h k h s h k h s h k k s h k h s h k h s h k h s h s s s s"}
     {:p1 [:stab "g4 ab4 g4 ab4 b4:4 c5 db5 c5 db5 d5:4 eb5:4 d5:2 db5:2 c5:4 b4:4"]
      :p2 [:harm "eb4:8 f4:8 g4:8 g3:8"]
      :tri [:bass "c2:2 c2:2 c2:2 db2:2 c2:2 c2:2 b1:2 c2:2 c2:2 c2:2 c2:2 db2:2 c2:2 c2:2 b1:2 c2:2"]
      :noise "k h s h k h s h k h s h k k s h k h s h k h s h k k s s k s s s"}]}

   ;; Clear: G major, 150 bpm, once. A climb that lands on the tonic.
   :clear
   {:rpm 600 :loop nil :order [0]
    :patterns
    [{:p1 [:brass "g4:2 a4:2 b4:2 d5:2 c5:2 e5:2 f#5:4 g5:2 a5:2 b5:3 a5 g5:8"]
      :p2 [:harm "-:4 d4:4 e4:4 c4:4 d4:8 b4:8"]
      :tri [:bass "g2:4 b2:4 c3:4 d3:4 d2:8 g2:8"]
      :noise "k - - - k - - - k - - - s - s s s - s - s - s - k:8"}]}

   ;; Die: E minor, 120 bpm, once, 2 s. A drop by thirds to a low E.
   :die
   {:rpm 480 :loop nil :order [0]
    :patterns
    [{:p1 [:lead "e5:2 b4:2 g4:2 d#4:2 e4:2 b3:2 e3:4"]
      :p2 [:harm "g4:2 d#4:2 b3:2 a3:2 g3:2 f#3:2 -:4"]
      :tri [:bass "e3:4 b2:4 g2:4 e2:4"]
      :noise "-:16"}]}

   ;; Over: A minor, 90 bpm, once, 5.3 s. Slow falling lines to a low A.
   :over
   {:rpm 360 :loop nil :order [0]
    :patterns
    [{:p1 [:lead "e5:4 c5:2 a4:2 d5:4 b4:2 g#4:2 f4:3 e4 d4:2 b3:2 a3:8"]
      :p2 [:soft "a3:8 g#3:8 d3:8 c3:8"]
      :tri [:bass "a2:8 e2:8 d2:8 a1:8"]
      :noise "-:16 -:8 k:8"}]}

   ;; Win: C major, 116 bpm, a march. The bass repeats one figure (root,
   ;; fifth, third, root, fifth) moved to each chord; a marching snare.
   :win
   {:rpm 464 :loop 0 :order [0 1 0 2]
    :patterns
    [{:p1 [:brass "e5:3 d5 c5:2 g4:2 c5:4 e5:2 g5:2 f5:3 e5 d5:2 b4:2 g4:8"]
      :p2 [:harm "e4:2 -:2 e4:2 -:2 e4:2 -:2 g4:2 -:2 d4:2 -:2 f4:2 -:2 d4:2 -:2 b3:2 -:2"]
      :tri [:bass "c2:2 -:2 g2:2 e2:2 c2:2 -:2 g2:2 -:2 g2:2 -:2 d3:2 b2:2 g2:2 -:2 d3:2 -:2"]
      :noise "s - s s k - s - s - s s k - s s s - s s k - s - s - s s k - s s"}
     {:p1 [:brass "a5:3 g5 f5:2 a5:2 c6:4 a5:4 g5:3 e5 c5:2 e5:2 d5:4 -:4"]
      :p2 [:harm "c4:2 -:2 f4:2 -:2 c4:2 -:2 f4:2 -:2 e4:2 -:2 g4:2 -:2 d4:2 -:2 b3:2 -:2"]
      :tri [:bass "f2:2 -:2 c3:2 a2:2 f2:2 -:2 c3:2 -:2 c2:2 -:2 g2:2 e2:2 g2:2 -:2 d3:2 -:2"]
      :noise "s - s s k - s - s - s s k - s s s - s s k - s - s - s s k - s s"}
     {:p1 [:brass "e5:2 g5:2 c6:4 b5:2 a5:2 g5:4 e5:2 d5:2 b4:2 d5:2 c5:8"]
      :p2 [:harm "g4:8 e4:8 d4:8 e4:8"]
      :tri [:bass "c2:2 -:2 g2:2 e2:2 a2:2 -:2 e2:2 -:2 g2:2 -:2 d3:2 b2:2 c2:2 -:2 g2:2 -:2"]
      :noise "s - s s k - s - s - s s k - s s s - s s k - s - k - k - k:4"}]}})


(def parse-track chip/parse-track)
(def parse-drums chip/parse-drums)
(def pattern-rows chip/pattern-rows)

(defn song-rows
  "The rows song k plays over its whole order, or 0 when k has no song."
  [k]
  (chip/song-rows songs k))

(defn song-seconds
  "How long song k plays once through its order, in seconds: rows * 60 / rpm."
  [k]
  (chip/song-seconds songs k))

(defn melody
  "The :p1 cells of song k's patterns concatenated in order, as parse-track
   returns them. Empty when k has no song."
  [k]
  (chip/melody songs k))

(defn validate
  "Checks the sound data: effects, instruments, then songs, throwing ex-info
   on the first problem. Returns nil when all is well."
  []
  (chip/validate {:effect-ids effect-ids :effects effects :instruments instruments :songs songs}))

(def effect-id
  "The C index of effect keyword k, or nil. Lives in mona.music-ids so the Wii build can take the ids without the data."
  ids/effect-id)

(def song-id
  "The C index of song keyword k, or nil. Lives in mona.music-ids so the Wii build can take the ids without the data."
  ids/song-id)

(defn emit-header
  "The C header for the sound data as a string. The output depends only on
   the data, so two calls return the same text."
  []
  (chip/emit-header {:prefix "mona"
                     :source "jolt-mona/src/mona/music.clj"
                     :include "jolt_snd.h"
                     :effect-ids effect-ids :song-ids song-ids
                     :effects effects :instruments instruments :songs songs}))

(defn -main
  "Validates the data, then writes the header to the path in out (default
   jolt-gfx/mona_music.h): to out.tmp first, then renamed over out."
  [& [out]]
  (validate)
  (let [out (or out "jolt-gfx/mona_music.h")
        tmp (str out ".tmp")]
    (spit tmp (emit-header))
    (when-not (.renameTo (java.io.File. tmp) (java.io.File. out))
      (throw (ex-info (str "could not rename " tmp " over " out) {:out out})))))
