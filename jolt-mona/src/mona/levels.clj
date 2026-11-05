(ns mona.levels
  "Super Mona's stages. The eight hand-built stages are transcribed from the
   badge's levels.py call for call, so a diff against the Python stays
   readable. Each builder makes a fresh Level, so restarting a stage resets
   its blocks, stars and enemies."
  (:require [mona.rules :as r]))

(defn- scenery
  "Hills, bushes and clouds (a seq of [x y]) as plain decor."
  [level hills bushes clouds]
  (as-> level lv
    (reduce r/hill lv hills)
    (reduce r/bush lv bushes)
    (reduce (fn [l [x y]] (r/cloud l x y)) lv clouds)))

(defn- hello-world []
  (-> (r/make-level "1-1 HELLO WORLD" 190 :day 300 false)
      (r/ground 0 68)
      (r/ground 71 85)
      (r/ground 89 150)
      (r/ground 153 189)
      (scenery [0 40 92 140 178]
               [9 33 60 104 130 157]
               [[6 3] [24 2] [47 4] [70 3] [96 2] [118 4] [146 3] [166 2]])

      (r/row 12 9 "Q")
      (r/row 18 9 "B-B")
      (r/block 19 9 :chip)
      (r/star-line 18 5 3)
      (r/enemy 24)
      (r/pipe 28 2)
      (r/enemy 33)
      (r/pipe 38 3)
      (r/enemy 44)
      (r/enemy 46)
      (r/pipe 52 4)
      (r/star-line 57 10 4)
      (r/enemy 63 :shellbot)

      (r/star-line 68 9 4)
      (r/row 74 9 "BB-BB")
      (r/block 76 9 [:multi 6] true)
      (r/block 78 5 :1up true)
      (r/enemy 81 :shellbot)

      (r/star-line 86 8 3)
      (r/row 92 9 "QQQ")
      (r/enemy 96)
      (r/enemy 98)
      (r/row 100 9 "BBBBBB")
      (r/block 103 5 :sparkle)
      (r/star-line 100 4 3)
      (r/enemy 108 :shellbot)
      (r/enemy 112)
      (r/enemy 114)

      (r/stairs 120 4)
      (r/stairs 124 4 false 13)
      (r/enemy 132)
      (r/pipe 138 2)
      (r/row 144 9 "Q" :chip)
      (r/star-line 150 8 4)
      (r/enemy 157)
      (r/enemy 159)
      (r/stairs 161 7)
      (r/flag 176)))

(defn- server-room []
  (-> (r/make-level "1-2 SERVER ROOM" 165 :cave 300 true)
      (r/ground 0 39)
      (r/ground 43 77)
      (r/ground 81 103)
      (r/ground 107 164)
      (r/ceiling 0 150)

      (r/row 10 9 "QQQ")
      (r/block 13 9 :chip)
      (r/enemy 18)
      (r/enemy 20)
      (r/row 26 10 "BB")
      (r/row 26 11 "BB")
      (r/row 26 12 "BB")
      (r/row 32 8 "BBBBBB")
      (r/star-line 33 7 4)
      (r/star-line 40 9 3)

      (r/enemy 46 :shellbot)
      (r/enemy 50)
      (r/enemy 52)
      (r/row 56 9 "BB-BB")
      (r/block 58 9 :sparkle)
      (r/row 56 5 "BBBBB")
      (r/pipe 66 3)
      (r/enemy 70)
      (r/pipe 72 2)
      (r/star-line 78 9 3)

      (r/row 84 9 "BBBBBBBB")
      (r/row 84 5 "BBBBBBBB")
      (r/block 88 9 [:multi 8] true)
      (r/star-line 85 7 6)
      (r/enemy 94)
      (r/enemy 96)
      (r/enemy 99 :shellbot)
      (r/star-line 104 9 3)

      (r/stairs 112 4)
      (r/row 122 9 "Q" :1up)
      (r/enemy 128)
      (r/enemy 130)
      (r/row 134 9 "Q" :chip)
      (r/stairs 140 6)
      (r/flag 152)))

(defn- sky-bridge []
  (-> (r/make-level "1-3 SKY BRIDGE" 175 :dusk 300 true)
      (r/ground 0 20)
      (r/ground 33 50)
      (r/ground 72 95)
      (r/ground 100 120)
      (r/ground 127 174)
      (scenery [2 36 76 130 165]
               [12 45 88 110 140]
               [[8 3] [28 2] [52 4] [80 2] [108 3] [136 2] [156 4]])

      (r/row 10 9 "QBQ")
      (r/enemy 15)
      (r/row 24 10 "======")
      (r/star-line 24 9 6)

      (r/pipe 38 3)
      (r/enemy 42 :shellbot)
      (r/row 44 9 "BQB" :chip)
      (r/enemy 47)

      (r/row 54 10 "====")
      (r/row 60 8 "====")
      (r/row 66 10 "====")
      (r/star-line 60 7 4)
      (r/enemy 62 :bug 7)

      (r/enemy 78)
      (r/enemy 80)
      (r/enemy 83 :shellbot)
      (r/row 86 9 "Q" :sparkle)
      (r/stairs 90 3)
      (r/row 97 11 "==")

      (r/row 104 9 "BB-BB")
      (r/block 106 9 [:multi 6] true)
      (r/enemy 108)
      (r/enemy 110)
      (r/pipe 114 4)
      (r/row 122 9 "====")
      (r/star-line 122 8 4)

      (r/enemy 132 :shellbot)
      (r/enemy 134)
      (r/enemy 136)
      (r/block 140 9 :1up)
      (r/enemy 146)
      (r/stairs 150 6)
      (r/flag 162)))

(defn- firewall []
  ;; World 1 castle: low ceiling, pits bridged by girders, dense blocks.
  (-> (r/make-level "1-4 FIREWALL" 150 :castle 300 true)
      (r/ground 0 14 10)
      (r/ground 15 30)
      (r/ground 34 52)
      (r/ground 57 80)
      (r/ground 84 110)
      (r/ground 114 149)
      (r/ceiling 0 134 [1 2])
      (assoc :start [2 9])

      (r/row 17 9 "Q" :chip)
      (r/enemy 22)
      (r/row 24 6 "HHHHHH")
      (r/star-line 31 10 3)

      (r/enemy 38)
      (r/enemy 40)
      (r/row 42 9 "BQB" :sparkle)
      (r/stairs 48 3)
      (r/row 53 11 "===")

      (r/row 60 6 "HHHHHHHHHHHH")
      (r/row 62 9 "BBBB")
      (r/block 64 9 [:multi 8] true)
      (r/enemy 68 :shellbot)
      (r/enemy 74)
      (r/enemy 76)
      (r/star-line 81 10 3)

      (r/pipe 88 2)
      (r/enemy 93)
      (r/enemy 95)
      (r/row 98 9 "Q" :1up)
      (r/stairs 104 4)
      (r/row 111 11 "==")

      (r/enemy 118 :shellbot)
      (r/enemy 122)
      (r/stairs 126 5)
      (r/flag 138)))

(defn- pull-request []
  ;; World 2 overworld: pipes in pairs, long block rows and stair pits.
  (-> (r/make-level "2-1 PULL REQUEST" 185 :day 300 true)
      (r/ground 0 45)
      (r/ground 48 98)
      (r/ground 102 132)
      (r/ground 135 184)
      (scenery [0 50 104 150]
               [14 38 66 90 120 168]
               [[8 2] [30 4] [58 2] [84 3] [112 2] [140 4] [164 3]])

      (r/row 8 9 "BQB" :chip)
      (r/row 14 9 "QQ")
      (r/enemy 18)
      (r/enemy 20)
      (r/pipe 24 2)
      (r/pipe 30 3)
      (r/enemy 34 :shellbot)
      (r/row 38 5 "BBQBB" :sparkle)
      (r/star-line 38 4 5)
      (r/star-line 45 9 3)

      (r/stairs 52 3)
      (r/stairs 55 3 false 13)
      (r/enemy 60)
      (r/enemy 62)
      (r/enemy 64)
      (r/row 68 9 "BBBBBBBB")
      (r/block 71 9 [:multi 6] true)
      (r/row 70 5 "Q" :1up)
      (r/pipe 80 4)
      (r/enemy 85 :shellbot)
      (r/pipe 90 2)
      (r/star-line 98 9 4)

      (r/row 104 9 "QBQBQ")
      (r/enemy 108)
      (r/enemy 110)
      (r/stairs 116 4)
      (r/row 124 9 "Q" :chip)
      (r/star-line 131 8 4)

      (r/enemy 140 :shellbot)
      (r/enemy 144)
      (r/enemy 146)
      (r/pipe 150 3)
      (r/stairs 158 8)
      (r/flag 172)))

(defn- brick-towers
  "Four brick towers one to four tiles tall, three columns apart from x 18."
  [level]
  (reduce (fn [lv [i h]]
            (reduce (fn [l y] (r/row l (+ 18 (* i 3)) y "B")) lv (range (- 13 h) 13)))
          level
          (map-indexed vector [1 2 3 4])))

(defn- stack-overflow []
  ;; World 2 underground: brick towers, tight corridors and a pipe maze.
  (-> (r/make-level "2-2 OVERFLOW" 160 :cave 300 true)
      (r/ground 0 34)
      (r/ground 38 70)
      (r/ground 74 112)
      (r/ground 116 159)
      (r/ceiling 0 145)

      (r/row 8 9 "QQQQ")
      (r/block 9 9 :chip)
      (r/enemy 14)
      (brick-towers)
      (r/star-line 18 7 10)
      (r/enemy 30)
      (r/star-line 35 9 3)

      (r/row 42 6 "BBBBBBBBBB")
      (r/row 42 9 "B-B-BQB" :sparkle)
      (r/enemy 46)
      (r/enemy 48)
      (r/enemy 52 :shellbot)
      (r/pipe 58 2)
      (r/pipe 62 3)
      (r/pipe 66 4)
      (r/star-line 71 8 3)

      (r/row 78 9 "BBBBBB")
      (r/row 78 5 "BBBBBB")
      (r/block 80 5 :1up true)
      (r/star-line 78 7 6)
      (r/enemy 88)
      (r/enemy 90)
      (r/enemy 92)
      (r/stairs 98 4)
      (r/stairs 102 4 false 13)
      (r/star-line 113 9 3)

      (r/row 120 9 "Q" :chip)
      (r/enemy 124 :shellbot)
      (r/enemy 128)
      (r/stairs 134 6)
      (r/flag 148)))

(defn- cloud-deploy []
  ;; World 2 athletic stage: girder hops high above the clouds.
  (-> (r/make-level "2-3 CLOUD DEPLOY" 175 :cloud 300 true)
      (r/ground 0 14)
      (r/ground 46 56)
      (r/ground 98 108)
      (r/ground 135 174)
      (scenery [2 144]
               [8 50 102 156]
               [[10 8] [22 3] [36 10] [60 2] [76 9] [92 3] [116 10] [130 4]])

      (r/row 8 9 "Q" :chip)
      (r/row 17 11 "=========")
      (r/row 29 10 "========")
      (r/star-line 30 9 6)
      (r/enemy 33 :bug 9)
      (r/row 40 11 "======")

      (r/row 48 9 "BQB" :sparkle)
      (r/enemy 52)
      (r/row 59 11 "=========")
      (r/row 71 10 "========")
      (r/star-line 72 9 6)
      (r/enemy 75 :bug 9)
      (r/row 82 8 "Q" :1up)
      (r/row 83 11 "============")

      (r/enemy 102 :shellbot)
      (r/row 111 11 "=========")
      (r/row 123 10 "=======")
      (r/star-line 124 9 5)
      (r/enemy 127 :bug 9)
      (r/star-line 133 7 3)

      (r/row 144 9 "QBQ" :chip)
      (r/enemy 148)
      (r/enemy 150)
      (r/stairs 152 6)
      (r/flag 164)))

(defn- merge-conflict []
  ;; World 2 castle: the final stage, longer pits and more shellbots.
  (-> (r/make-level "2-4 CONFLICT" 165 :castle 300 true)
      (r/ground 0 14 10)
      (r/ground 15 28)
      (r/ground 32 46)
      (r/ground 51 66)
      (r/ground 71 92)
      (r/ground 96 120)
      (r/ground 125 164)
      (r/ceiling 0 150 [1 2])
      (assoc :start [2 9])

      (r/row 18 9 "Q" :chip)
      (r/enemy 22 :shellbot)
      (r/row 24 6 "HHHH")
      (r/star-line 29 10 3)

      (r/enemy 35)
      (r/enemy 37)
      (r/row 38 9 "BQB" :sparkle)
      (r/stairs 42 3)
      (r/row 47 11 "===")

      (r/row 54 6 "HHHHHHHHHH")
      (r/row 56 9 "BBBB")
      (r/block 57 9 [:multi 8] true)
      (r/enemy 60 :shellbot)
      (r/enemy 63)
      (r/row 67 11 "===")

      (r/pipe 74 3)
      (r/enemy 79)
      (r/enemy 81)
      (r/enemy 83 :shellbot)
      (r/row 86 9 "Q" :1up)
      (r/stairs 88 4)
      (r/star-line 93 9 3)

      (r/enemy 100)
      (r/enemy 102)
      (r/row 104 6 "HHHHHHHH")
      (r/enemy 110 :shellbot)
      (r/stairs 114 4)
      (r/row 121 11 "===")

      (r/enemy 130)
      (r/enemy 132)
      (r/enemy 134 :shellbot)
      (r/stairs 138 6)
      (r/flag 152)))

;; Worlds 3 to 8
;;
;; Built from small segments, as the badge does. The generator is pure: its
;; state is a vector [s], and every segment takes and returns it, drawing in
;; exactly the order levels.py calls r(...).

(def ^:private items [:chip :sparkle :chip :1up [:multi 6]])

(defn lcg
  "A generator state seeded with `seed`: the vector [s]."
  [seed]
  [seed])

(defn rand-next
  "Draw from [0, n): returns [value state']."
  [[s] n]
  (let [s' (bit-and (+ (* s 1103515245) 12345) 0x7FFFFFFF)]
    [(mod (bit-shift-right s' 16) n) [s']]))

(defn- flat [lv x k rng]
  (let [[d rng] (rand-next rng 3)
        lv (-> lv
               (r/ground x (+ x 9))
               (r/star-line (+ x 2) 9 3)
               (r/enemy (+ x 4 d)))]
    [(if (> k 2) (r/enemy lv (+ x 8)) lv) (+ x 10) rng]))

(defn- pipes [lv x k rng]
  (let [lv (-> lv (r/ground x (+ x 15)))
        [a rng] (rand-next rng 2)
        lv (-> lv (r/pipe (+ x 2) (+ 2 a)) (r/enemy (+ x 6)))
        [b rng] (rand-next rng 2)
        lv (r/pipe lv (+ x 9) (+ 3 b))]
    [(if (> k 1) (r/enemy lv (+ x 13) :shellbot) lv) (+ x 16) rng]))

(defn- blocks [lv x _k rng]
  (let [[i rng] (rand-next rng (count items))
        lv (-> lv
               (r/ground x (+ x 13))
               (r/row (+ x 2) 9 "BQBBB")
               (r/block (+ x 5) 9 (nth items i)))
        [c rng] (rand-next rng 2)
        lv (if (zero? c)
             (r/star-line lv (+ x 3) 5 3)
             (r/block lv (+ x 4) 5 [:multi 6] true))]
    [(-> lv (r/enemy (+ x 9)) (r/enemy (+ x 11))) (+ x 14) rng]))

(defn- pit [lv x k rng]
  (let [[d rng] (rand-next rng 2)
        w (+ 2 (min 2 (quot (+ k d) 2)))
        lv (-> lv (r/star-line x 9 w) (r/ground (+ x w) (+ x w 3)))]
    [lv (+ x w 4) rng]))

(defn- stairs-seg [lv x k rng]
  (let [h (+ 3 (min 1 (quot k 2)))
        lv (-> lv (r/ground x (+ x h)) (r/stairs (+ x 1) h))
        x2 (+ x h 1 (if (>= k 2) 2 0))
        lv (-> lv
               (r/ground x2 (+ x2 h 2))
               (r/stairs x2 h false 13)
               (r/enemy (+ x2 h 1)))]
    [lv (+ x2 h 3) rng]))

(defn- swarm [lv x k rng]
  (let [[d rng] (rand-next rng 4)
        lv (-> lv
               (r/ground x (+ x 13))
               (r/row (+ x 4) 8 "Q" (if (zero? d) :1up :chip))
               (r/enemy (+ x 3))
               (r/enemy (+ x 5))
               (r/enemy (+ x 8) :shellbot))]
    [(if (>= k 2) (r/enemy lv (+ x 11)) lv) (+ x 14) rng]))

(defn- towers [lv x _k rng]
  (let [lv (r/ground lv x (+ x 15))
        lv (reduce (fn [l [i y]] (r/row l (+ x 1 (* i 3)) y "B"))
                   lv
                   (for [i (range 4) y (range (- 12 i) 13)] [i y]))]
    [(-> lv (r/star-line (+ x 1) 7 10) (r/enemy (+ x 14))) (+ x 16) rng]))

(defn- corridor [lv x _k rng]
  (let [lv (-> lv
               (r/ground x (+ x 11))
               (r/row (+ x 1) 9 "BBBBBBBB")
               (r/row (+ x 1) 5 "BBBBBBBB"))
        [d rng] (rand-next rng 4)
        [i rng] (rand-next rng (count items))
        lv (r/block lv (+ x 3 d) 9 (nth items i) true)]
    [(-> lv (r/star-line (+ x 2) 7 6) (r/enemy (+ x 10))) (+ x 12) rng]))

(defn- hops [lv x _k rng]
  [(-> lv
       (r/row (+ x 2) 11 "=========")
       (r/row (+ x 14) 10 "========")
       (r/star-line (+ x 15) 9 6)
       (r/enemy (+ x 18) :bug 9)
       (r/row (+ x 25) 11 "======"))
   (+ x 34) rng])

(defn- bridge [lv x _k rng]
  [(-> lv
       (r/row (+ x 1) 11 "===")
       (r/star-line (+ x 1) 8 3)
       (r/ground (+ x 5) (+ x 10))
       (r/enemy (+ x 8)))
   (+ x 11) rng])

(defn- roof [lv x _k rng]
  (let [lv (-> lv
               (r/ground x (+ x 15))
               (r/row (+ x 2) 6 "HHHHHHHHHH")
               (r/row (+ x 4) 9 "BBBB"))
        [c rng] (rand-next rng 2)
        lv (r/block lv (+ x 5) 9 (if (zero? c) :sparkle [:multi 8]) true)]
    [(-> lv (r/enemy (+ x 8) :shellbot) (r/enemy (+ x 12))) (+ x 16) rng]))

(def ^:private segments
  {\f flat \p pipes \b blocks \g pit \s stairs-seg \w swarm \t towers
   \c corridor \h hops \x bridge \r roof})

(defn- build
  "A stage from its segment `plan` at `width` columns: [level end]."
  [name theme plan k seed width]
  (let [[lv x rng] (reduce (fn [[lv x rng] code] ((segments code) lv x k rng))
                           [(-> (r/make-level name width theme 300 true)
                                (r/ground 0 9)
                                (r/row 5 9 "Q" :chip))
                            10 (lcg seed)]
                           plan)
        lv (-> lv (r/ground x (+ x 23)) (r/stairs (+ x 2) 6) (r/flag (+ x 14)))
        end (+ x 24)
        lv (case theme
             :cave (r/ceiling lv 0 (+ x 13))
             :castle (r/ceiling lv 0 (+ x 13) [1 2])
             (as-> lv lv
               (reduce (fn [l hx] (if (and (r/solid? l hx 13) (r/solid? l (+ hx 2) 13))
                                    (r/hill l hx)
                                    l))
                       lv (range 0 (- end 3) 37))
               (reduce (fn [l bx] (if (and (r/solid? l bx 13) (r/solid? l (+ bx 2) 13)
                                           (not (r/solid? l (+ bx 1) 12)))
                                    (r/bush l bx)
                                    l))
                       lv (range 9 (- end 3) 23))
               (reduce (fn [l [i cx]] (r/cloud l cx (+ 2 (mod i 3))))
                       lv (map-indexed vector (range 6 (- end 3) 21)))))]
    [lv end]))

(defn- stage
  "A thunk for a generated stage. Builds once at width 200 to learn the end,
   then again at exactly that width."
  [name theme plan k seed]
  (fn []
    (let [[_ width] (build name theme plan k seed 200)]
      (first (build name theme plan k seed width)))))

(def ^:private worlds
  [[["CLONE" "FORK" "PUSH" "PULL"]
    [:day :cave :cloud :castle]
    ["bpgwsbgp" "cgtwcgs" "bhwh" "rxwrxs"]]
   [["ACTION" "TRIGGER" "PRE-PROD" "PROD"]
    [:day :cave :dusk :castle]
    ["pgpbwgsp" "tcgwtcs" "fhbhw" "xrwxrs"]]
   [["BUG BASH" "STACK TRACE" "BREAKPOINT" "SEGFAULT"]
    [:dusk :cave :cloud :castle]
    ["wbgpswgb" "ctgwcts" "hwbh" "rxwxrws"]]
   [["CONTAINER" "KERNEL PANIC" "EDGE CACHE" "DEADLOCK"]
    [:day :cave :dusk :castle]
    ["pbgwpgsw" "tcwgtcgs" "bhwhf" "xrxwrxs"]]
   [["CLONE" "STASH" "PR" "MERGE"]
    [:day :cave :cloud :castle]
    ["bgpwgsbpg" "cgtwcgts" "hbhw" "rxwrxws"]]
   [["PLAN" "BUILD" "TEST" "SHIP-IT"]
    [:dusk :cave :dusk :castle]
    ["wpgbsgwpg" "tcgwctgs" "hwhbh" "xrwxrwxs"]]])

(def levels
  "The 32 stages in play order, each a thunk returning a fresh Level."
  (into [hello-world server-room sky-bridge firewall
         pull-request stack-overflow cloud-deploy merge-conflict]
        (for [[w [names themes plans]] (map-indexed vector worlds)
              s (range 4)]
          (stage (str (+ w 3) "-" (inc s) " " (nth names s))
                 (nth themes s) (nth plans s) w (+ (* w 4) s 7)))))

(defn level-at
  "A fresh Level for stage index n."
  [n]
  ((nth levels n)))
