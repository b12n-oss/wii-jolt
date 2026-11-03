(ns balloons.ffi-bench
  "Where does the FFI time go? Times 2000 iterations of: an empty loop, a bare
   defcfn call, a 5-argument defcfn call (fixnum and bignum colour), the
   wii.gfx wrapper, ffi/write, a bulk copy and a vector conj. Prints one
   'ffi-bench: <case> n=<N> total-us=<t> per-op-ns=<ns>' line per case."
  (:require [jolt.ffi :as ffi]
            [wii.gfx :as gfx]
            [wii.frame-stats :as stats]))

(ffi/defcfn c-time-us "jg_time_us" [] :uptr)
(ffi/defcfn c-rect "jg_rect" [:uptr :uptr :uptr :uptr :uptr] :uptr)

(def ^:private n 2000)

(defn- report [case-name total-us]
  (println (str "ffi-bench: " case-name " n=" n " total-us=" total-us
                " per-op-ns=" (quot (* total-us 1000) n))))

(defmacro ^:private timed
  "Run body once, timed with jg_time_us; print the line, or the error."
  [case-name & body]
  `(try
     (let [t0# (c-time-us)
           _# (do ~@body)
           t1# (c-time-us)]
       (report ~case-name (stats/elapsed-us t0# t1#)))
     (catch Exception e#
       (println (str "ffi-bench: " ~case-name " ERROR " (pr-str e#))))))

(defn- bench-draw []
  (gfx/begin! 0)
  (timed "defcfn-5arg-fixnum"
         (loop [i 0] (when (< i n) (c-rect 0 0 4 4 0x00FF00FF) (recur (inc i)))))
  (timed "defcfn-5arg-bignum-colour"
         (loop [i 0] (when (< i n) (c-rect 0 0 4 4 0xFF0000FF) (recur (inc i)))))
  (timed "wiigfx-rect"
         (loop [i 0] (when (< i n) (gfx/rect! 0 0 4 4 0xFF0000FF) (recur (inc i)))))
  (gfx/end!))

(defn- bench-memory []
  (let [p (ffi/alloc (* n 4))]
    (timed "ffi-write-int32"
           (loop [i 0] (when (< i n) (ffi/write p :int32 i (* i 4)) (recur (inc i)))))
    (ffi/free p))
  (let [p (ffi/alloc (* n 4))
        arr (int-array n)]
    (timed "bulk-fill"
           (loop [i 0] (when (< i n) (aset arr i (int i)) (recur (inc i)))))
    (timed "bulk-copy"
           (ffi/write-array p :int32 arr))
    (ffi/free p)))

(defn -main [& _]
  (gfx/init! 640 480)
  (println "ffi-bench: start")
  (timed "empty-loop"
         (loop [i 0] (when (< i n) (recur (inc i)))))
  (timed "defcfn-noarg"
         (loop [i 0] (when (< i n) (c-time-us) (recur (inc i)))))
  (try (bench-draw)
       (catch Exception e (println (str "ffi-bench: draw ERROR " (pr-str e)))))
  (try (bench-memory)
       (catch Exception e (println (str "ffi-bench: memory ERROR " (pr-str e)))))
  (timed "vector-conj"
         (loop [i 0 v []] (if (< i n) (recur (inc i) (conj v i)) (count v))))
  (println "ffi-bench: done")
  (gfx/close!))
