(ns mona.app-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is run-tests]]
            [mona.app :as app]))

(def dt (/ 1.0 30))

(def no-input {:ch nil :held #{} :pressed #{} :any-pressed? false :connected #{}})

(deftest title-idles-into-the-demo-and-back
  (let [a (reduce (fn [a _] (app/tick a no-input dt)) (app/init 0) (range (int (* 10.5 30))))]
    (is (= :demo (:state a)))
    (is (some (fn [l] (= l "mode demo")) (:log a)))
    (let [a (app/tick a (assoc no-input :any-pressed? true :pressed #{:back}) dt)]
      (is (= :title (:state a)) "any other button returns to the title"))))

(deftest title-takes-the-first-remote
  (let [a (app/tick (app/init 0) (assoc no-input :ch 2 :pressed #{:select} :any-pressed? true :connected #{0 2}) dt)]
    (is (= :intro (:state a)))
    (is (= 2 (:player-ch a)))
    (is (= 3 (:lives (:session a))))
    (is (some (fn [l] (= l "stage 1 1-1 HELLO WORLD")) (:log a)))))

(deftest intro-then-play-then-pause-pages
  (let [a (-> (app/init 0) (app/tick (assoc no-input :pressed #{:select} :any-pressed? true :connected #{0} :ch 0) dt))
        a (reduce (fn [a _] (app/tick a no-input dt)) a (range 70))]
    (is (= :play (:state a)))
    (let [a (app/tick a (assoc no-input :pressed #{:menu}) dt)]
      (is (= :pause (:state a)))
      (is (= 1 (:page (app/tick a (assoc no-input :pressed #{:right}) dt))))
      (is (= 3 (:page (app/tick a (assoc no-input :pressed #{:left}) dt))))
      (is (= :play (:state (app/tick (app/tick a no-input dt) (assoc no-input :pressed #{:back}) dt)))))))

(deftest death-costs-a-life-and-restarts-the-stage
  (let [a (-> (app/init 0) (app/tick (assoc no-input :pressed #{:select} :any-pressed? true :connected #{0} :ch 0) dt))
        a (reduce (fn [a _] (app/tick a no-input dt)) a (range 70))
        a (assoc-in a [:world :state] :dead)
        a (app/tick a no-input dt)]
    (is (= 2 (:lives (:session a))))
    (is (= :intro (:state a)))
    (let [a (app/tick (assoc-in (assoc-in a [:session :lives] 1) [:world :state] :dead) no-input dt)]
      (is (= :over (:state a))))))

(deftest clear-advances-and-win-after-the-last
  (let [a (-> (app/init 0) (app/tick (assoc no-input :pressed #{:select} :any-pressed? true :connected #{0} :ch 0) dt))
        a (reduce (fn [a _] (app/tick a no-input dt)) a (range 70))
        a (app/tick (assoc-in a [:world :state] :clear) no-input dt)]
    (is (= 1 (:level (:session a))))
    (is (= :intro (:state a)))
    (is (some (fn [l] (str/starts-with? l "clear 1-1 HELLO WORLD score=")) (:log a)))
    (let [a (app/tick (assoc-in (assoc-in a [:session :level] 31) [:world :state] :clear) no-input dt)]
      (is (= :win (:state a))))))

(deftest demo-bot-clears-the-first-pipe
  (let [dt60 (/ 1.0 60)
        a (loop [a (app/init 0) i 0]
            (if (or (>= i 2400) (and (= :demo (:state a)) (>= (double (:state-time a)) 20.0)))
              a
              (recur (app/tick a no-input dt60) (inc i))))]
    (is (= :demo (:state a)))
    (is (< 400.0 (double (:x (:player (:demo-world a))))) "stage 1-1's first pipe is at x 298")))

(defn -main [& _]
  (let [res (run-tests 'mona.app-test)]
    (System/exit (if (zero? (+ (:fail res 0) (:error res 0))) 0 1))))
