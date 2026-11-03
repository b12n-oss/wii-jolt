(ns wii.frame-stats-test
  (:require [clojure.test :refer [deftest is run-tests]]
            [wii.frame-stats :as fs]))

;; update-us 1000..1119 (120 values), draw-us 2000 each, frame-us 16667
;; except two of 33333.
(def samples
  (vec (for [i (range 120)]
         {:update-us (+ 1000 i)
          :draw-us 2000
          :frame-us (if (contains? #{10 70} i) 33333 16667)})))

(def expected
  {:n 120 :update-median-us 1059 :draw-median-us 2000 :work-median-us 3059
   :work-worst-us 3119 :frame-worst-us 33333 :dropped 2})

(deftest summary-of-known-samples
  (is (= expected (fs/summary samples)))
  (is (= expected (fs/summary (reverse samples))) "order does not matter"))

(deftest summary-line-format
  (is (= "jolt-stress: frames n=120 update-median-us=1059 draw-median-us=2000 work-median-us=3059 work-worst-us=3119 frame-worst-us=33333 dropped=2"
         (fs/summary-line "jolt-stress" expected))))

(deftest elapsed-wraps-at-2-to-the-28
  (is (= 250 (fs/elapsed-us 100 350)))
  (is (= 0x110 (fs/elapsed-us 0x0FFFFF00 0x10))))

(defn -main [& _]
  (let [res (run-tests 'wii.frame-stats-test)]
    (System/exit (if (zero? (+ (:fail res 0) (:error res 0))) 0 1))))
