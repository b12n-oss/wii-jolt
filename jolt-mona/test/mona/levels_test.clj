(ns mona.levels-test
  (:require [clojure.edn :as edn]
            [clojure.test :refer [deftest is run-tests testing]]
            [mona.levels :as lv]
            [mona.rules :as r]))

(defn- level->golden
  "A Level as the golden's map shape."
  [level]
  (let [tiles ^bytes (:tiles level)]
    {:name (:name level)
     :width (:width level)
     :theme (:theme level)
     :time (:time level)
     :switch (:switch? level)
     :start (:start level)
     :flag-x (:flag-x level)
     :castle-door (:castle-door level)
     :tiles (mapv (fn [i] (bit-and (aget tiles i) 0xFF)) (range (alength tiles)))
     :decor (:decor level)
     :contents (:contents level)
     :stars (:stars level)
     :spawns (:spawns level)}))

(deftest hand-built-stages-match-the-golden
  (let [golden (edn/read-string (slurp "test/mona/golden_levels.edn"))]
    (is (= 32 (count golden)))
    (doseq [n (range 8)]
      (let [g (nth golden n)
            level ((nth lv/levels n))]
        (testing (:name g)
          (is (= (dissoc g :tiles) (dissoc (level->golden level) :tiles)))
          (is (= (:tiles g) (:tiles (level->golden level)))))))))

(deftest levels-have-unique-names-and-known-themes
  (let [built (keep (fn [f] (when f (f))) lv/levels)]
    (is (= 32 (count lv/levels)))
    (is (= (count built) (count (set (map :name built)))))
    (is (every? #{:day :cave :dusk :cloud :castle} (map :theme built)))
    (is (every? (fn [l] (and (:flag-x l) (r/solid? l (first (:start l)) 13))) built))))

(defn -main [& _]
  (let [res (run-tests 'mona.levels-test)]
    (System/exit (if (zero? (+ (:fail res 0) (:error res 0))) 0 1))))
