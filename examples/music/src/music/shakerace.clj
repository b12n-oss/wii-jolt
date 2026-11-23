(ns music.shakerace
  "Shake Race's sound data: effects, instruments and songs in the note
   language of chip.music. Plain Clojure with no FFI. The C header the game
   reads is emitted from here by scripts/music-to-c shakerace, which writes
   examples/shakerace/shakerace_music.h. All tunes are original."
  (:require [chip.music :as chip]))

(def effect-ids
  "The effects in C table order: the countdown beep, the go cue and one finish
   sting per place."
  [:beep :go :finish-1 :finish-2 :finish-3 :finish-4])

(def song-ids
  "Every song id, in the C table order."
  [:title :race :results])

(def effects
  "One entry per effect-ids id. A step is [f0 f1 ms vol]: the frequency slides
   from f0 to f1 Hz over ms milliseconds at volume vol (0..15). The finish
   stings start higher for each place, so a couch can hear who got in first
   to last, and they alternate between the two tonal channels, so two close finishes both
   sound and a third at once takes the older one."
  [{:wave :pulse :duty 2 :chan 0 :attack 0 :release 20
    :steps [[1000 1000 100 12]]}                                ; :beep, under 150 ms
   {:wave :pulse :duty 1 :chan 0 :attack 0 :release 30
    :steps [[392 392 70 12] [587 587 70 12] [784 1175 200 13]]} ; :go, 340 ms rise
   {:wave :pulse :duty 1 :chan 0 :attack 0 :release 30
    :steps [[523 659 80 13] [659 784 80 13] [784 1047 100 12]]} ; :finish-1, 260 ms
   {:wave :pulse :duty 2 :chan 1 :attack 0 :release 30
    :steps [[659 784 80 12] [784 988 80 12] [988 1319 100 11]]} ; :finish-2
   {:wave :pulse :duty 1 :chan 0 :attack 0 :release 30
    :steps [[784 988 80 12] [988 1175 80 12] [1175 1568 100 11]]} ; :finish-3
   {:wave :pulse :duty 2 :chan 1 :attack 0 :release 30
    :steps [[988 1175 80 12] [1175 1480 80 12] [1480 1976 100 11]]}]) ; :finish-4

(def instruments
  "Melodic instruments by keyword. :a, :d and :r are milliseconds; :s is the
   sustain level 0..15; :vol is the peak level 0..15."
  {:lead {:wave :pulse :duty 1 :a 2 :d 40 :s 10 :r 50 :vol 15}
   :harm {:wave :pulse :duty 2 :a 2 :d 60 :s 7 :r 50 :vol 11}
   :bass {:wave :tri :duty 0 :a 1 :d 20 :s 15 :r 30 :vol 15}
   :brass {:wave :pulse :duty 2 :a 4 :d 60 :s 11 :r 50 :vol 15}
   :bell {:wave :pulse :duty 0 :a 2 :d 160 :s 5 :r 180 :vol 15}
   :pad {:wave :pulse :duty 2 :a 40 :d 200 :s 7 :r 200 :vol 9}})

(def songs
  "Songs by id. :loop is the order index to loop back to, or nil. :order lists
   pattern indexes. Title and race patterns are two bars of 4/4 (32 rows), the
   results fanfare is 24 rows. The race song is steady eighths with the beat on
   every other row, so it still sounds right when the game plays it faster."
  {;; Title: G major, 110 bpm, 17.5 s a pass. A bouncy bell over a loose pad.
   :title
   {:rpm 440 :loop 0 :order [0 1 0 2]
    :patterns
    [{:p1 [:bell "g5:4 b5:2 d6:2 b5:4 g5:4 a5:4 c6:2 e6:2 c6:4 a5:4"]
      :p2 [:pad "g3:16 e3:16"]
      :tri [:bass "g2:4 d3:4 g2:4 d3:4 e2:4 b2:4 e2:4 b2:4"]
      :noise "k:4 h:4 s:4 h:4 k:4 h:4 s:4 h:4"}
     {:p1 [:bell "d6:4 b5:2 g5:2 a5:4 e5:4 g5:4 b5:2 d6:2 c6:8"]
      :p2 [:pad "c4:16 d4:16"]
      :tri [:bass "c3:4 g3:4 c3:4 g3:4 d3:4 a3:4 d3:4 a3:4"]
      :noise "k:4 h:4 s:4 h:4 k:4 h:4 s:4 h:4"}
     {:p1 [:bell "g5:2 a5:2 b5:2 d6:2 g6:8 e6:2 d6:2 c6:2 a5:2 g5:8"]
      :p2 [:pad "g3:16 d4:16"]
      :tri [:bass "g2:4 d3:4 g2:4 d3:4 c3:4 g3:4 d3:4 d3:4"]
      :noise "k:4 h:4 s:4 h:4 k:4 h:4 s:4 h:4"}]}

   ;; Race: E minor and C, 130 bpm, 14.8 s a pass. Running eighths, kick on
   ;; every other row, off-beat harmony.
   :race
   {:rpm 520 :loop 0 :order [0 1 0 2]
    :patterns
    [{:p1 [:lead "e5:2 g5:2 b5:2 g5:2 e6:2 b5:2 g5:2 b5:2 c5:2 e5:2 g5:2 e5:2 c6:2 g5:2 e5:2 g5:2"]
      :p2 [:harm "-:2 b4:2 -:2 b4:2 -:2 b4:2 -:2 b4:2 -:2 g4:2 -:2 g4:2 -:2 g4:2 -:2 g4:2"]
      :tri [:bass "e3:4 b3:4 e3:4 b3:4 c3:4 g3:4 c3:4 g3:4"]
      :noise "k - h - s - h - k - h - s - h - k - h - s - h - k - h - s h s -"}
     {:p1 [:lead "b5:2 g5:2 e5:2 g5:2 b5:2 e6:2 b5:2 g5:2 a5:2 c6:2 e6:2 c6:2 a5:2 e5:2 c5:2 e5:2"]
      :p2 [:harm "-:2 g4:2 -:2 g4:2 -:2 g4:2 -:2 g4:2 -:2 e4:2 -:2 e4:2 -:2 e4:2 -:2 e4:2"]
      :tri [:bass "e3:4 b3:4 e3:4 b3:4 a2:4 e3:4 a2:4 e3:4"]
      :noise "k - h - s - h - k - h - s - h - k - h - s - h - k - h - s h s -"}
     {:p1 [:lead "g5:2 a5:2 b5:2 d6:2 e6:2 d6:2 b5:2 a5:2 g5:2 a5:2 b5:2 g5:2 e5:8"]
      :p2 [:harm "-:2 b4:2 -:2 b4:2 -:2 b4:2 -:2 b4:2 -:2 g4:2 -:2 g4:2 -:2 b4:2 -:2 b4:2"]
      :tri [:bass "e3:4 b3:4 e3:4 b3:4 c3:4 d3:4 b2:4 b2:4"]
      :noise "k - h - s - h - k - h - s - h - k - h - s - h - k - h - s h s -"}]}

   ;; Results: G major, 120 bpm, once, 3 s. A rising run to a held high G.
   :results
   {:rpm 480 :loop nil :order [0]
    :patterns
    [{:p1 [:brass "g5:2 b5:2 d6:2 g6:2 b6:4 a6:2 b6:2 g6:8"]
      :p2 [:harm "g4:8 b4:8 d5:8"]
      :tri [:bass "g3:8 d3:8 g3:8"]
      :noise "k - - - s - - - k - - - s - s s k:8"}]}})

(defn emit-header
  "The C header for the sound data as a string. The output depends only on
   the data, so two calls return the same text."
  []
  (chip/emit-header {:prefix "sr"
                     :source "examples/music/src/music/shakerace.clj"
                     :include "snd.h"
                     :regen "scripts/music-to-c shakerace"
                     :effect-ids effect-ids :song-ids song-ids
                     :effects effects :instruments instruments :songs songs}))

(defn -main
  "Validates the data, then writes the header to out (default
   examples/shakerace/shakerace_music.h): to out.tmp first, then renamed."
  [& [out]]
  (chip/validate {:effect-ids effect-ids :effects effects
                  :instruments instruments :songs songs})
  (let [out (or out "examples/shakerace/shakerace_music.h")
        tmp (str out ".tmp")]
    (spit tmp (emit-header))
    (when-not (.renameTo (java.io.File. tmp) (java.io.File. out))
      (throw (ex-info (str "could not rename " tmp " over " out) {:out out})))))
