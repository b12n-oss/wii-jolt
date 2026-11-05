(ns mona.main
  "Super Mona on the Wii. For now a skeleton that proves the sprite sheets and
   the tile layers: it draws a test stage, a few sprites and the minimap for
   1200 frames (20 s, so the Dolphin screenshot at 20 s still finds the
   window) and exits."
  (:require [wii.gfx :as gfx]))

(def ^:private app "jolt-mona")
(def ^:private sky 0x5C94FCFF)

(def ^:private grid-width 30)

(defn- test-tiles []
  (let [a (byte-array (* grid-width 15))
        put! (fn [x y id] (aset a (+ (* y grid-width) x) (unchecked-byte id)))]
    (dotimes [x grid-width]
      (put! x 13 1)
      (put! x 14 2))
    (put! 10 9 3)
    (put! 12 9 4)
    (put! 20 11 7)
    (put! 21 11 8)
    (put! 20 12 9)
    (put! 21 12 10)
    a))

(defn- test-decor []
  (let [a (byte-array (* grid-width 15))]
    (dotimes [i 6]
      (aset a (+ (* (+ 11 (quot i 3)) grid-width) 2 (rem i 3)) (unchecked-byte (+ 16 i))))
    a))

(defn -main [& _]
  (gfx/init! 640 480)
  (gfx/scale! 4)
  (let [ok (count (filter gfx/sheet-load! [gfx/sheet-tiles gfx/sheet-players gfx/sheet-mobs]))]
    (println (str app ": sheets ok=" ok)))
  (println (str app ": tiles ok=" (gfx/tiles-load! (test-tiles) (test-decor) grid-width)))
  (loop [frame 0]
    (when (< frame 1200)
      (gfx/begin! sky)
      (gfx/tiles-draw! 0.0)
      (gfx/sprite! gfx/sheet-tiles 1 0 8 104 0)
      (gfx/sprite! gfx/sheet-tiles 1 0 16 104 0)
      (gfx/sprite! gfx/sheet-players 0 0 24 96 0)
      (gfx/sprite! gfx/sheet-players 0 0 48 96 gfx/flip-x)
      (gfx/sprite! gfx/sheet-mobs 0 0 72 104 gfx/flip-y)
      (gfx/tiles-map! 448 8 180 60 0xC8783CFF sky)
      (when (= frame 60) (gfx/tile-set! (+ (* 9 grid-width) 12) 5))
      (gfx/end!)
      (when (zero? (rem frame 60))
        (println (str app ": calls=" (gfx/calls))))
      (recur (inc frame))))
  (println (str app ": exit"))
  (gfx/close!))
