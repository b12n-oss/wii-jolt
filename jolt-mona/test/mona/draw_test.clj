(ns mona.draw-test
  "Golden test for mona.draw: the list of wii.gfx calls (name and arguments)
   for four scenes must equal the list in draw_golden.edn. Host only; it never
   touches the FFI.
   Run: cd jolt-mona && jolt -A:test -m mona.draw-test
   Regenerate (only for a deliberate picture change):
     jolt -A:test -m mona.draw-test gen"
  (:require [clojure.edn :as edn]
            [clojure.test :refer [deftest is run-tests]]
            [mona.draw :as draw]
            [mona.rules :as r]
            [wii.gfx :as gfx]))

(def ^:private golden-file "test/mona/draw_golden.edn")

(defn- num-args [args]
  (mapv (fn [a] (if (number? a) (long a) a)) args))

(defn- record
  "Every drawing call f makes through wii.gfx, as [:name arg ...]."
  [f]
  (let [log (atom [])
        stub (fn [nm] (fn [& args] (swap! log conj (into [nm] (num-args args))) nil))]
    (with-redefs [gfx/sprite! (stub :sprite!)
                  gfx/tiles-draw! (stub :tiles-draw!)
                  gfx/tiles-map! (stub :tiles-map!)
                  gfx/tiles-begin! (fn [& args] (apply (stub :tiles-begin!) args) true)
                  gfx/tile-set! (stub :tile-set!)
                  gfx/decor-set! (stub :decor-set!)
                  gfx/time-us (fn [] 0)
                  gfx/rect! (stub :rect!)
                  gfx/text! (stub :text!)
                  gfx/text-width (fn [s _size] (* 16 (count s)))]
      (f))
    @log))

(defn- blank-level []
  (-> (r/make-level "BLANK" 30 :day 300 false) (r/ground 0 29)))

(defn- fresh-world []
  (r/new-world (blank-level) (r/new-session)))

(defn- facing-left-world []
  (let [w (r/new-world (blank-level) (assoc (r/new-session) :big? true))]
    (update w :player assoc :facing -1 :sparkle 1.0)))

(defn- enemy-world []
  (let [w (fresh-world)
        shell (assoc (r/new-enemy 0 60 88 :shellbot) :active? true :vx 18.0)
        bug (assoc (r/new-enemy 1 100 88 :bug) :active? true :state :squashed)]
    (assoc w :enemies [shell bug])))

(defn- app [state world & {:as more}]
  (merge {:state state :state-time 0.0 :session (r/new-session) :world world
          :title-world world :demo-world nil :page 0 :cursor 0 :high 0}
         more))

(defn- scenes []
  [["fresh world t=0" (fn [] (draw/draw-world (fresh-world) 0.0))]
   ["facing left, big, sparkle t=0.1" (fn [] (draw/draw-world (facing-left-world) 0.1))]
   ["shellbot and squashed bug t=0" (fn [] (draw/draw-world (enemy-world) 0.0))]
   ["pause MAP page" (fn [] (draw/draw-app (app :pause (enemy-world) :page 1) 0.0))]
   ["title" (fn [] (draw/draw-app (app :title (fresh-world)) 0.0))]
   ["title, sound off" (fn [] (draw/draw-app (app :title (fresh-world) :sound-off? true) 0.0))]])

(defn- all-calls [] (mapv (fn [[label f]] [label (record f)]) (scenes)))

(deftest player-facing-left-is-flipped-in-place
  (let [calls (record (fn [] (draw/draw-world (facing-left-world) 0.0)))
        p (first (filter (fn [c] (and (= :sprite! (first c)) (= gfx/sheet-players (second c)))) calls))]
    (is (= gfx/flip-x (last p)))
    (is (= (- (long (:x (:player (facing-left-world)))) 5) (nth p 4)) "x is not shifted by the width when flipping")))

(deftest load-stage-goes-cell-by-cell
  (let [lvl (-> (r/make-level "TEN" 10 :day 300 false)
                (r/set-tile! 1 2 7) (r/set-tile! 4 5 9) (r/set-tile! 9 14 3)
                (assoc :decor {12 4}))
        calls (record (fn [] (draw/load-stage! lvl)))]
    (is (= [[:tiles-begin! 10]
            [:tile-set! 21 7] [:tile-set! 54 9] [:tile-set! 149 3]
            [:decor-set! 12 4]]
           calls))))

(deftest tiles-window-uses-the-camera
  (let [w (assoc (fresh-world) :cam-x 37.9)
        calls (record (fn [] (draw/draw-world w 0.0)))]
    (is (= [:tiles-draw! 37] (first (filter (fn [c] (= :tiles-draw! (first c))) calls))))))

(deftest title-shows-sound-off-only-when-muted
  (let [text (fn [calls] (set (map second (filter (fn [c] (= :text! (first c))) calls))))]
    (is (contains? (text (record (fn [] (draw/draw-app (app :title (fresh-world) :sound-off? true) 0.0)))) "SOUND OFF"))
    (is (not (contains? (text (record (fn [] (draw/draw-app (app :title (fresh-world)) 0.0)))) "SOUND OFF")))))

(deftest golden-draw-calls
  (let [golden (edn/read-string (slurp golden-file))
        now (all-calls)]
    (is (= (count golden) (count now)) "same number of scenes")
    (doseq [[[gl gc] [nl nc]] (map vector golden now)]
      (is (= gl nl) "scene order")
      (is (pos? (count nc)) (str nl " draws something"))
      (is (= gc nc) (str nl ": same wii.gfx calls")))))

(defn -main [& args]
  (if (= (first args) "gen")
    (let [cs (all-calls)]
      (spit golden-file (with-out-str (println "[") (doseq [c cs] (prn c)) (println "]")))
      (println (str "wrote " golden-file ": " (count cs) " scenes, "
                    (reduce + (map (fn [[_ c]] (count c)) cs)) " calls")))
    (let [res (run-tests 'mona.draw-test)]
      (System/exit (if (zero? (+ (:fail res 0) (:error res 0))) 0 1)))))
