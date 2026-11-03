(ns hello.core
  "First Clojure program on a Wii: jolt cross-compiled to Chez tpb32b.")

(defn fib [n]
  (loop [a 0 b 1 i 0]
    (if (= i n) a (recur b (+ a b) (inc i)))))

(defn -main [& _]
  (println "Hello from Clojure (jolt) on the Wii!")
  (println "fib(30) =" (fib 30))
  (println "fib(90) =" (fib 90))
  (println "2^100 =" (reduce *' (repeat 100 2)))
  (println "sum 0..999 =" (reduce + (range 1000)))
  (println "map/filter:" (->> (range 20) (filter odd?) (map #(* % %)) vec))
  (println "a map:" {:console "Wii" :cpu "Broadway" :bits 32})
  (println "frequencies:" (frequencies "mississippi"))
  (println "set ops:" (clojure.set/union #{1 2 3} #{3 4 5}))
  (println "string:" (clojure.string/upper-case "jolt on wii"))
  (println "atom:" (let [a (atom 0)] (dotimes [_ 10] (swap! a inc)) @a))
  (println "hash of :wii" (hash :wii) " hash of \"wii\"" (hash "wii")))
