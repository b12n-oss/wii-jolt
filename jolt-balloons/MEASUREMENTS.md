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

## Profile of balloons.draw

Program: `balloons.draw-profile`, built against the stub-draw pack
(`bb build:jolt-draw-profile`, `bb run:jolt-draw-profile`), Dolphin 2609a, idle
profile, MMU on. State: the stress start-up state (24 balloons, 100 particles, 4
players aiming, mode `:play`) at t = 1.0. Each section is timed 120 times
inside `begin!`/`end!` and the median taken; microseconds. The whole draw here
(151 ms) is lower than the stress program's 240 ms because the profile does no
update between frames, so the heap holds less garbage (less GC work per draw).
Treat the sections as shares of a draw, not as an absolute frame.

| section | median us | share of whole draw |
|---|---|---|
| whole-draw | 151182 | 100% |
| sky (gradient, 4 clouds x 3 circles, ground) | 1112 | 1% |
| balloons (24, 8 of them gold) | 115819 | 77% |
| particles (100 circles) | 25208 | 17% |
| popups-0 (the stress frame has none) | 13 | 0% |
| popups-8 (eight popups, for scale) | 2780 | |
| hud | 1491 | 1% |
| cursors (4) | 7572 | 5% |

Single operations, 5000 iterations each, the empty loop (530 ns per iteration)
subtracted. `net-ns-per-op` is copied from the log:

| operation | total us | net ns per op |
|---|---|---|
| keyword lookup on a balloon map | 14651 | 2400 |
| `nth` on a 6-vector | 21627 | 3795 |
| boxed double multiply (`mul-boxed`) | 16361 | 2742 |
| same multiply in a `^double`-hinted fn | 15560 | 2582 |
| `(long 123.7)` | 680078 | 135485 |
| `(long x)` inside a `^double`-hinted fn | 680681 | 135606 |
| `(unchecked-long 123.7)` | 134235 | 26317 |
| `(long 124.0)` (integral double) | 360526 | 71575 |
| `Math/floor` | 60333 | 11536 |
| `Math/sin` | 60909 | 11651 |
| `(str "P" i)` | 150424 | 29554 |
| `ffi/string->ptr` + `ffi/free` | 719005 | 143271 |
| `doseq` over a 100-vector, empty body (per element) | 17180 | 3430 |
| `loop`/`nth` over the same vector (per element) | 45533 | 9101 |

Reading:

- `(long x)` on a double costs 135 us, 50 times a map lookup and 11 times
  `Math/sin`. wii.gfx casts every coordinate argument of every call with
  `long`, and a busy frame makes 1414 such casts, 1324 of them on doubles (counted on the
  Mac with stubs over the golden test's busy state), which is where the 150 ms
  goes (an integral double costs 72 us, a fractional one 135 us). A `^double` hint changes nothing (135606 against
  135485): the cast is the checked `jolt-long-cast`, which compares the double
  with +-2^63, and on 32-bit Chez those bounds are bignums. `unchecked-long`
  skips the compare and costs 26 us.
- Hints on arithmetic buy little here: the hinted multiply is 160 ns faster
  than the boxed one (6%), and whole-program inference probably unboxes both.
- `doseq` over a vector is cheaper than `loop`/`nth` (3.4 us against 9.1 us per
  element), so rewriting `doseq` as `loop`/`nth` would make things worse.
- Text costs a `string->ptr` + `free` (143 us) per call; the stress frame has
  at most 7 text calls, so under 1 ms.

## After the draw optimisation (P2.T4)

Same builds and settings as above (Dolphin 2609a, idle profile, MMU on). Steady
state is every summary after the first. This pass ran longer than the earlier
ones because 1800 frames now take less wall time, so there are 14 steady
summaries instead of 4.

| build | draw median before | draw median after | work median before | work median after |
|---|---|---|---|---|
| stress (real) | 251271 to 252930 us | 59404 to 60097 us | 266752 to 268575 us | 74887 to 75580 us |
| stress-stub (Clojure + crossing) | 239068 to 240199 us | 46630 to 47275 us | 254695 to 255988 us | 62114 to 62769 us |

Final summary lines, verbatim (last one of each run):

```
jolt-stress: frames n=120 update-median-us=15481 draw-median-us=59782 work-median-us=75290 work-worst-us=78052 frame-worst-us=107728 dropped=120
jolt-stress: frames n=120 update-median-us=15482 draw-median-us=46993 work-median-us=62485 work-worst-us=64469 frame-worst-us=94653 dropped=120
```

The draw is 4.2 times faster (252 ms to 60 ms). The profile's whole-draw median
went from 151182 to 47767 us.

Levers (profile medians, microseconds; the golden test was green after each):

| lever | before | after | verdict |
|---|---|---|---|
| 1. wii.gfx: `unchecked-long` instead of `long` for every coordinate argument of the drawing calls. `long` on a double is the checked cast, which compares with +-2^63 bignums on 32-bit (135 us); `unchecked-long` is 26 us. Same value for every in-range double; a checked cast would throw out of range, this one saturates. | whole-draw 151182 | 48384 | kept |
| 2. `draw-balloon`: read `:r` once, hoist `y + r`, `y + r + 4` and `t * 3` out of the loop (same operation order, so the same doubles) | balloons 32045 | 31425 (whole 47767) | kept (small: 1.3%) |
| `^double`/`^long` hints | | | not tried in draw: the profile shows a hinted multiply is 6% cheaper and a hinted `long` cast costs the same as an unhinted one |
| `loop`/`nth` instead of `doseq` | | | not tried: `doseq` is 3.4 us per element, `loop`/`nth` 9.1 us |

Levers left, each estimated from the profile (`estimate:` figures):

- A cheaper double-to-fixnum truncation than `unchecked-long`. 1324 of the busy
  frame's casts are on doubles at 26 us, about 34 ms of the 48 ms draw. The
  remaining cost is Chez's `exact` of a truncated flonum. No built-in is
  cheaper (`.longValue` 72 us, `Math/round` 73 us, `unchecked-int` 26 us, `int`
  135 us; scratch run, not part of the committed profile). A hand-written
  truncation from flonum comparisons might reach 10 to 15 us per cast, so
  estimate: 15 to 20 ms. It is not one of the allowed levers (it is new code in
  wii.gfx), so it is reported, not done.
- Caching C strings for static text: at most 7 text calls in a busy frame at
  143 us each, estimate: about 1 ms (2%), under the 5% bar.
- Six `Math/sin` per balloon string (11.6 us each, 24 balloons, 147 calls, about 1.7 ms
  including the 3 gold pulses); only a lookup table would remove it, which moves
  arguments and is out of scope.
- The crossing (368 calls, about 7 ms) and raylib (about 24 ms) are untouched.

Frame budget: update (15.5 ms) plus draw (60 ms) is 75 ms of work against a
16.7 ms frame at 60 fps, about 4.5 times too slow. Even with a free draw, the
update alone is under budget only barely (15.5 of 16.7 ms).

Screenshot check: the run tasks' screenshots did not capture the stress frame
this time. The "before" shot (`.work/shots/jolt-stress-before.png`) shows the
stress frame; the "after" run left `jolt-stress.png` unwritten after 21:42,
and the file it holds is the C game's title screen, from another Dolphin
window. The picture is guarded by the golden test instead (13 states, 1129
recorded wii.gfx calls, all equal), plus a host check that `long` and
`unchecked-long` agree on all 3108 double arguments in those states.
