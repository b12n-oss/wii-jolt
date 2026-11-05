(ns mona.rules-test
  (:require [clojure.test :refer [deftest is run-tests]]
            [mona.rules :as r]))

(deftest ground-and-get-tile
  (let [lv (-> (r/make-level "T" 10 :day 300 false) (r/ground 0 9))]
    (is (= r/t-ground-top (r/get-tile lv 3 13)))
    (is (= r/t-ground (r/get-tile lv 3 14)))
    (is (= r/t-empty (r/get-tile lv 3 12)))
    (is (r/solid? lv 3 13))))

(deftest get-tile-outside-the-grid
  (let [lv (r/make-level "T" 10 :day 300 false)]
    (is (= r/t-hard (r/get-tile lv -1 5)))
    (is (= r/t-hard (r/get-tile lv 10 5)))
    (is (= r/t-empty (r/get-tile lv 5 -1)))
    (is (= r/t-empty (r/get-tile lv 5 15)))
    (is (identical? lv (r/set-tile! lv 10 5 r/t-brick)))))

(deftest row-letters-and-contents
  (let [lv (-> (r/make-level "T" 20 :day 300 false) (r/row 2 9 "BQ-H=o" :chip))]
    (is (= [r/t-brick r/t-question r/t-empty r/t-hard r/t-girder r/t-empty]
           (mapv (fn [i] (r/get-tile lv (+ 2 i) 9)) (range 6))))
    (is (= :chip (get (:contents lv) (r/index lv 3 9))))
    (is (contains? (:stars lv) [7 9]))))

(deftest cave-theme-uses-cave-tiles
  (let [lv (-> (r/make-level "T" 10 :cave 300 true) (r/ground 0 9) (r/row 1 9 "B"))]
    (is (= r/t-cave-ground (r/get-tile lv 1 13)))
    (is (= r/t-cave-brick (r/get-tile lv 1 9)))))

(deftest pipe-stairs-and-flag
  (let [lv (-> (r/make-level "T" 40 :day 300 false) (r/ground 0 39) (r/pipe 4 2) (r/stairs 10 3) (r/flag 30))]
    (is (= [r/t-pipe-tl r/t-pipe-tr] [(r/get-tile lv 4 11) (r/get-tile lv 5 11)]))
    (is (= [r/t-pipe-l r/t-pipe-r] [(r/get-tile lv 4 12) (r/get-tile lv 5 12)]))
    (is (= [r/t-hard r/t-empty] [(r/get-tile lv 10 12) (r/get-tile lv 10 11)]))
    (is (= [r/t-hard r/t-hard r/t-hard] [(r/get-tile lv 12 12) (r/get-tile lv 12 11) (r/get-tile lv 12 10)]))
    (is (= 30 (:flag-x lv)))
    (is (= r/t-pole-top (r/get-tile lv 30 3)))
    (is (= r/t-pole (r/get-tile lv 30 4)))
    (is (= r/t-hard (r/get-tile lv 30 12)))
    (is (= (+ (* 35 8) 18) (:castle-door lv)))
    (is (= r/t-castle (get (:decor lv) (r/index lv 35 8))))))

(deftest block-and-enemy
  (let [lv (-> (r/make-level "T" 10 :day 300 false) (r/block 3 5 [:multi 6] true) (r/enemy 7 :shellbot) (r/enemy 8))]
    (is (= r/t-brick (r/get-tile lv 3 5)))
    (is (= [:multi 6] (get (:contents lv) (r/index lv 3 5))))
    (is (= [[7 12 :shellbot] [8 12 :bug]] (:spawns lv)))))

(defn -main [& _]
  (let [res (run-tests 'mona.rules-test)]
    (System/exit (if (zero? (+ (:fail res 0) (:error res 0))) 0 1))))
