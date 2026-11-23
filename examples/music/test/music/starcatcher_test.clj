(ns music.starcatcher-test
  (:require [chip.music :as chip]
            [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]
            [music.starcatcher :as sc]))

(deftest the-data-validates
  (is (nil? (chip/validate {:effect-ids sc/effect-ids :effects sc/effects
                            :instruments sc/instruments :songs sc/songs}))))

(deftest ids-fix-the-c-order
  (is (= ["beep" "go" "catch-1" "catch-2" "catch-3" "catch-4"] (map name sc/effect-ids)))
  (is (= [:title :play :results] sc/song-ids))
  (is (= (count sc/effect-ids) (count sc/effects))))

(defn- first-hz [k]
  (first (first (:steps (nth sc/effects (.indexOf sc/effect-ids k))))))

(defn- effect-ms [k]
  (reduce + (map #(nth % 2) (:steps (nth sc/effects (.indexOf sc/effect-ids k))))))

(deftest each-catch-starts-higher-than-the-last
  (let [hz (map first-hz [:catch-1 :catch-2 :catch-3 :catch-4])]
    (is (apply < hz) (str hz))))

(deftest effects-are-short
  (is (< (effect-ms :beep) 150))
  (is (< (effect-ms :go) 400))
  (doseq [k [:catch-1 :catch-2 :catch-3 :catch-4]]
    (is (< (effect-ms k) 250) (str k))))

(deftest song-lengths-and-loops
  (let [secs (fn [k] (chip/song-seconds sc/songs k))]
    (is (<= 2 (secs :results) 6) (str (secs :results)))
    (is (nil? (:loop (:results sc/songs))))
    (doseq [k [:title :play]]
      (is (>= (secs k) 8) (str k " " (secs k)))
      (is (some? (:loop (get sc/songs k))) (str k " loops")))))

(deftest header-names-the-ids
  (let [h (sc/emit-header)]
    (is (str/includes? h "examples/music/src/music/starcatcher.clj"))
    (is (str/includes? h "#include \"snd.h\""))
    (is (str/includes? h "#define SFX_CATCH_4 5"))
    (is (str/includes? h "#define SONG_RESULTS 2"))
    (is (str/includes? h "#define SC_EFFECTS 6"))
    (is (str/includes? h "#define SC_SONGS 3"))
    (is (str/includes? h "sc_effects"))
    (is (str/includes? h "sc_instruments"))
    (is (str/includes? h "sc_songs"))
    (is (= h (sc/emit-header)))))

(defn -main [& _] (let [r (run-tests 'music.starcatcher-test)] (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1))))
