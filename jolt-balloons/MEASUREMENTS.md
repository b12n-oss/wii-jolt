# Clojure drawing on the Wii: first measurement (P2.T2)

Program: `balloons.stress` (4 players aiming, 24 balloons, 100 particles, mode
`:play`), built with `bb build:jolt-stress`, run in Dolphin 2609a (idle profile,
MMU on) for 170 s. Per-shape drawing: one FFI call per raylib shape.

Calls per frame: 368 (`balloons.stress/count-busy-frame`, run on the Mac with
counting stubs; a release build inlines the wii.gfx wrappers, so the count
cannot be taken on the Wii). Analytic check: 14 sky + 219 balloons + 100
particles + 7 HUD + 28 cursors = 368.

| summary | update median us | draw median us | work median us | work worst us | frame worst us | dropped |
|---|---|---|---|---|---|---|
| 1 (start-up, ignored) | 15459 | 206655 | 221760 | 253792 | 697385 | 119 |
| 2 | 15459 | 250967 | 266711 | 273878 | 274578 | 120 |
| 3 | 15459 | 253134 | 268770 | 276330 | 298410 | 120 |
| 4 | 15459 | 253233 | 269052 | 276071 | 309319 | 120 |
| 5 | 15460 | 253136 | 268586 | 274084 | 300399 | 120 |

Steady state: work median about 269000 us (about 16 frames of budget), draw about
253000 us, so about 0.69 ms per wii.gfx call.

Decision rule: per-shape stays only if work-median <= 12000 and work-worst <=
16700. Neither holds. Draw is 94% of work (>= half), so **P2.T3 (the command
buffer) is needed next.** Update (15.5 ms) is also over a 16.7 ms frame once
draw is fixed, so the rules need a look afterwards.

Memory finding: jolt sizes its allocation window at 16 MB, more than the malloc
heap left after the 14 MB .dol loads, so the first run died with "out of
memory" after about 14 frames. `jolt-gfx/jolt_gfx.c` now sets
`JOLT_GC_TRIP_BYTES=2097152` in its constructor. With it the runs are stable.
