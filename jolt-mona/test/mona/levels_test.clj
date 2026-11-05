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

(def ^:private dt (/ 1.0 30))

(defn- first-diff
  "Index of the first tile where two tile vectors differ, or nil."
  [a b]
  (first (keep-indexed (fn [i [x y]] (when (not= x y) i)) (map vector a b))))

(deftest lcg-matches-the-badge
  (let [[vals _] (reduce (fn [[vs st] _] (let [[v st] (lv/rand-next st 10)] [(conj vs v) st]))
                         [[] (lv/lcg 7)] (range 6))]
    (is (= [4 6 8 4 1 5] vals))))

(deftest every-stage-matches-the-golden
  (let [golden (edn/read-string (slurp "test/mona/golden_levels.edn"))]
    (is (= 32 (count (remove nil? lv/levels))))
    (doseq [n (range 32)]
      (let [g (nth golden n)
            got (level->golden ((nth lv/levels n)))]
        (testing (:name g)
          (is (= (dissoc g :tiles) (dissoc got :tiles)))
          (is (= (:tiles g) (:tiles got))
              (str "first differing tile index " (first-diff (:tiles g) (:tiles got)))))))))

(deftest every-stage-can-be-finished-by-the-autopilot
  (doseq [n (range 32)]
    (let [w0 (assoc (r/new-world ((nth lv/levels n)) (r/new-session)) :demo? true)
          w (loop [w w0 c r/no-controls i 0]
              (if (or (= :clear (:state w)) (>= i 3600))
                w
                (let [c (r/autopilot w c)] (recur (r/step (r/clear-events w) c dt) c (inc i)))))]
      (testing (:name (:level w0))
        (is (= :clear (:state w)))
        (is (pos? (:score (:session w))))))))

(deftest levels-have-unique-names-and-known-themes
  (let [built (keep (fn [f] (when f (f))) lv/levels)]
    (is (= 32 (count lv/levels)))
    (is (= (count built) (count (set (map :name built)))))
    (is (every? #{:day :cave :dusk :cloud :castle} (map :theme built)))
    (is (every? (fn [l] (and (:flag-x l) (r/solid? l (first (:start l)) 13))) built))))

(defn -main [& _]
  (let [res (run-tests 'mona.levels-test)]
    (System/exit (if (zero? (+ (:fail res 0) (:error res 0))) 0 1))))
