(ns balloons.snd-spike
  "AESND stream voice spike: the title tune plays and a jump blip fires each
   second while a text frame loop runs, with the shim's sound counters printed
   every 60 frames.
   Dolphin's audio dump (jolt run:jolt-snd-spike) shows whether the voice is
   audible."
  (:require [wii.gfx :as gfx]))

(defn- say [s] (println (str "jolt-snd-spike: " s)) (flush))

(defn -main [& _]
  (gfx/init! 640 480)
  (say (str "init=" (gfx/sound-init!)))
  ;; 0 is SONG_TITLE in mona_music.h; this app does not depend on jolt-mona.
  (gfx/music! 0 true)
  (loop [i 0]
    (when (< i 240)
      (gfx/begin! 0x202020FF)
      (gfx/text! "snd spike" 20 20 20 0xFFFFFFFF)
      (gfx/end!)
      (when (zero? (mod (inc i) 60))
        ;; 0 is SFX_JUMP.
        (gfx/sfx! 0)
        (let [{:keys [peak sfx callbacks]} (gfx/sound-stats)]
          (say (str "stats peak=" peak " sfx=" sfx " callbacks=" callbacks))))
      (recur (inc i))))
  (say "done")
  (gfx/close!))
