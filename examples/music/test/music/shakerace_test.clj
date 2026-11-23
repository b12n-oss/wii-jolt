(ns music.shakerace-test
  (:require [chip.music :as chip]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]
            [music.shakerace :as sr]))

(deftest the-data-validates
  (is (nil? (chip/validate {:effect-ids sr/effect-ids :effects sr/effects
                            :instruments sr/instruments :songs sr/songs}))))

(deftest ids-fix-the-c-order
  (is (= ["beep" "go" "finish-1" "finish-2" "finish-3" "finish-4"] (map name sr/effect-ids)))
  (is (= [:title :race :results] sr/song-ids))
  (is (= (count sr/effect-ids) (count sr/effects))))

(defn- first-hz [k]
  (first (first (:steps (nth sr/effects (.indexOf sr/effect-ids k))))))

(defn- effect-ms [k]
  (reduce + (map #(nth % 2) (:steps (nth sr/effects (.indexOf sr/effect-ids k))))))

(deftest each-finish-starts-higher-than-the-last
  (let [hz (map first-hz [:finish-1 :finish-2 :finish-3 :finish-4])]
    (is (apply < hz) (str hz))))

(deftest effects-are-short
  (is (< (effect-ms :beep) 150))
  (is (< (effect-ms :go) 400))
  (doseq [k [:finish-1 :finish-2 :finish-3 :finish-4]]
    (is (< (effect-ms k) 300) (str k))))

(deftest song-lengths-and-loops
  (let [secs (fn [k] (chip/song-seconds sr/songs k))]
    (is (<= 2 (secs :results) 6) (str (secs :results)))
    (is (nil? (:loop (:results sr/songs))))
    (is (>= (secs :race) 8) (str (secs :race)))
    (is (some? (:loop (:race sr/songs))))
    (is (>= (secs :title) 8) (str (secs :title)))
    (is (some? (:loop (:title sr/songs))))))

(deftest header-names-the-ids
  (let [h (sr/emit-header)]
    (is (str/includes? h "examples/music/src/music/shakerace.clj"))
    (is (str/includes? h "#include \"snd.h\""))
    (is (str/includes? h "#define SFX_FINISH_4 5"))
    (is (str/includes? h "#define SONG_RESULTS 2"))
    (is (str/includes? h "#define SR_EFFECTS 6"))
    (is (str/includes? h "#define SR_SONGS 3"))
    (is (str/includes? h "sr_effects"))
    (is (str/includes? h "sr_instruments"))
    (is (str/includes? h "sr_songs"))
    (is (= h (sr/emit-header)))))

(defn -main [& _] (let [r (run-tests 'music.shakerace-test)] (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1))))
