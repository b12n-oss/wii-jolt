(ns mona.music-test
  (:require [clojure.test :refer [deftest is run-tests]]
            [mona.music :as music]))

(deftest notes-parse-to-midi
  (is (= [{:note 60} {:note 64} {:note 67}] (music/parse-track "c4 e4 g4")))
  (is (= [{:note 61} {:note 58} {:note 108}] (music/parse-track "c#4 bb3 c8")))
  (is (= [{:note 60} {:note :hold} {:note :rest} {:note :rest} {:note :hold}]
         (music/parse-track "c4:2 -:2 ="))))

(deftest bad-tokens-throw
  (is (thrown? clojure.lang.ExceptionInfo (music/parse-track "h4")))
  (is (thrown? clojure.lang.ExceptionInfo (music/parse-track "c4:0")))
  (is (thrown? clojure.lang.ExceptionInfo (music/parse-track "c9"))))

(deftest drums-parse
  (is (= [{:drum 0} {:drum :rest} {:drum 1} {:drum 2} {:drum :rest}]
         (music/parse-drums "k - s h:2"))))

(deftest pattern-rows-must-agree
  (is (= 4 (music/pattern-rows {:p1 [:lead "c4 e4 g4 c5"] :p2 [:harm "-:4"] :tri [:bass "c2:4"] :noise "k - s -"})))
  (is (thrown? clojure.lang.ExceptionInfo
               (music/pattern-rows {:p1 [:lead "c4 e4 g4"] :p2 [:harm "-:4"] :tri [:bass "c2:4"] :noise "k - s -"}))))

(deftest the-data-validates
  (is (nil? (music/validate)))
  (is (= 16 (count music/effects)))
  (is (= (count music/effect-ids) (count music/effects)))
  (is (every? (set (keys music/songs)) [:title])))

(defn -main [& _]
  (let [res (run-tests 'mona.music-test)]
    (System/exit (if (zero? (+ (:fail res 0) (:error res 0))) 0 1))))
