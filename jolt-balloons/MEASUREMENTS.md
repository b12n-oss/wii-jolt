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

## Where the draw time goes (split measurement)

Three builds of the same 368-call busy frame, each run in Dolphin 2609a (idle
profile, MMU on) for 170 s. Steady state is summaries 2 to 5 (summary 1 holds
the start-up frames). All numbers are microseconds, medians of 120 frames.

- **stress**: `balloons.stress`, the real thing (update, Clojure draw, raylib).
- **stress-stub**: the same program linked against a shim built with
  `-DJG_STUB_DRAW` (`bb gfx-pack-stub`), where every drawing function returns at
  once. Draw time is Clojure compute plus the FFI crossing.
- **const-draw**: `balloons.const-draw`, the same 368 calls with arguments
  computed once at start-up. Per frame it only loops over a vector. Draw time is
  the crossing plus raylib. No update, so update is 0.

| run | summaries 2 to 5 draw | steady draw | update | work |
|---|---|---|---|---|
| stress | 251611, 252227, 252678, 251758 | 252000 | 15483 | 267900 |
| stress-stub | 238032, 239531, 240086, 239727 | 239700 | 15483 | 255400 |
| const-draw | 31619, 31618, 31619, 31619 | 31619 | 0 | 31619 |
| crossing, 368 x 19.9 us (bench, `wiigfx-rect`) | | 7323 | | |
| Clojure compute, stub draw minus crossing | | 232400 | | |
| raylib, const-draw minus crossing | | 24300 | | |

The crossing figure uses the P2.T3 bench's 19.9 us for a 5-argument call for
all 368 calls. Calls take 1 to 7 arguments, and text calls also allocate and
free a C string, so it is an estimate to within a few ms.

Reading: about 232 ms of the 252 ms draw time is Clojure running balloons.draw
(boxed float math, `Math/sin`, the lazy `range`/`doseq`, map lookups, string
building for text, and the GC they cause), 7 ms is the crossing and about 24 ms
is raylib and opengx. The earlier bench report guessed that the missing 245 ms
sat inside the C draw functions. That guess is wrong: with every C draw
function stubbed out, the draw time only falls from 252 ms to 240 ms. The
pieces do not add up exactly (240 + 24 = 264 against a measured 252, 12 ms or
5% over), probably because GC timing differs between builds, but that is small
next to the 232 ms. The numbers support the conclusion that **the Clojure side
of drawing is the bottleneck, not the FFI crossing and not raylib**. A command
buffer that merely batches the 368 calls would save at most about 7 ms. Even a
free raylib leaves 240 ms. The work to do is on the Clojure code that computes
the shapes (the update at 15.5 ms is a second, smaller, cost of the same kind).
Nothing was optimised in this task.

The Review Focus circle (x = -10, y = 240, r = 20) shows as a white sliver at
the left edge of the screenshot (`.work/shots/jolt-stress.png`, about y = 820 of
the 1586-pixel-tall view). `top-up` now pins `:mode :play` and `:mode-time 0.0`,
so a run no longer leaves the round after 60 s.
