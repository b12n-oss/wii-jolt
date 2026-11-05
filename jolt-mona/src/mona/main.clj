(ns mona.main
  "Super Mona on the Wii. For now a skeleton that proves the sprite sheets:
   it draws a few sprites for 1200 frames (20 s, so the Dolphin screenshot
   at 20 s still finds the window) and exits."
  (:require [wii.gfx :as gfx]))

(def ^:private app "jolt-mona")
(def ^:private sky 0x5C94FCFF)

(defn -main [& _]
  (gfx/init! 640 480)
  (gfx/scale! 4)
  (let [ok (count (filter gfx/sheet-load! [gfx/sheet-tiles gfx/sheet-players gfx/sheet-mobs]))]
    (println (str app ": sheets ok=" ok)))
  (loop [frame 0]
    (when (< frame 1200)
      (gfx/begin! sky)
      (gfx/sprite! gfx/sheet-tiles 1 0 8 104 0)
      (gfx/sprite! gfx/sheet-tiles 1 0 16 104 0)
      (gfx/sprite! gfx/sheet-players 0 0 24 96 0)
      (gfx/sprite! gfx/sheet-players 0 0 48 96 gfx/flip-x)
      (gfx/sprite! gfx/sheet-mobs 0 0 72 104 gfx/flip-y)
      (gfx/end!)
      (recur (inc frame))))
  (println (str app ": exit"))
  (gfx/close!))
