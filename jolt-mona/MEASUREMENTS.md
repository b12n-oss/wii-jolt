# jolt-mona measurements

First frame-cost measurements of Super Mona (Clojure on jolt) for the Wii, taken in Dolphin. Not a console: nothing here has run on hardware.

## Setup

- Date: 2026-11-05.
- jolt: v0.8.23-429-ga65c59ed (`jolt --version`).
- Build: `jolt build:jolt-mona`, `jolt build --target tpb32b` with `--closed-world --boot small`; `jolt-mona/build/jolt-mona.dol` is 11,901,344 bytes (11.9 MB).
- Dolphin with MMU emulation on. The demo runs (`jolt run:jolt-mona`, 45 s) use the `idle` profile; the scripted runs (`jolt run:jolt-mona-wiimote`, 40 s) use the `platformer` profile.
- The game prints one summary line per 300 frames. Work is update plus draw, timed from the top of the frame to just before `EndDrawing`. Dropped counts frames whose interval exceeded 20 ms. Calls is shim crossings per frame.
- The saved measure logs keep only the `jolt-mona:` lines, so they show no `jolt_gfx:` heap line. The shim prints it to stderr at `jg_init`, and Dolphin's own log has it for a jolt-mona run: `jolt_gfx: heap at jg_init: malloc live 46987384, fallback used 0 + 0, free 0 + 0; chez bytes-allocated 39710984, collect-trip-bytes 2097152`.
- Each task ran twice. The two demo runs produced identical medians, worst-case and dropped counts to the digit; the two scripted runs differ by at most 1 microsecond in some medians and a few milliseconds in the worst cases (the second run's lines are in the task's measure logs). The tables below are the first run of each; the second run's lines agree.

## Demo run (`jolt run:jolt-mona`, 45 s)

| Window (frames) | Update median (ms) | Draw median (ms) | Work median (ms) | Work worst (ms) | Frame worst (ms) | Dropped | Calls |
|---|---|---|---|---|---|---|---|
| 1-300 | 0.9 | 5.1 | 6.1 | 257.0 | 812.4 | 1 | 27 |
| 301-600 | 0.9 | 5.2 | 6.1 | 331.2 | 331.3 | 2 | 27 |
| 601-900 | 12.9 | 5.3 | 18.2 | 21.5 | 77.0 | 53 | 32 |
| 901-1200 | 11.8 | 4.9 | 16.5 | 28.2 | 79.9 | 34 | 33 |
| 1201-1500 | 8.2 | 4.8 | 12.8 | 20.1 | 81.3 | 2 | 31 |
| 1501-1800 | 2.9 | 4.4 | 7.7 | 15.2 | 74.2 | 1 | 28 |
| 1801-2100 | 8.8 | 5.1 | 13.8 | 280.2 | 280.3 | 3 | 31 |

Window 1 is the title screen plus startup, window 2 the rest of the 10 s on the title and the first demo frames (the demo begins during window 2: `mode demo`, stage 1-1 HELLO WORLD). Windows 3 to 6 are the bot on stage 1-1: the update median goes 12.9, 11.8, 8.2, 2.9 ms as the stage goes on, with 53, 34, 2 and 1 frames dropped. The bot reached the flag (`event flag`) and the run moved to stage 2, 1-2 SERVER ROOM, during window 7, so window 7 covers the end of 1-1 and the start of 1-2. No `clear` line was printed. Calls per frame were 27 on the title and 31 to 33 in play.

## Scripted run (`jolt run:jolt-mona-wiimote`, 40 s)

| Window (frames) | Update median (ms) | Draw median (ms) | Work median (ms) | Work worst (ms) | Frame worst (ms) | Dropped | Calls |
|---|---|---|---|---|---|---|---|
| 1-300 | 2.4 | 4.2 | 6.6 | 340.4 | 812.4 | 2 | 29 |
| 301-600 | 2.4 | 4.2 | 6.6 | 330.5 | 330.6 | 2 | 29 |
| 601-900 | 2.4 | 4.2 | 6.6 | 330.5 | 330.5 | 2 | 29 |
| 901-1200 | 2.2 | 4.2 | 6.5 | 329.5 | 329.6 | 3 | 28 |
| 1201-1500 | 2.4 | 4.2 | 6.6 | 330.5 | 330.6 | 3 | 29 |
| 1501-1800 | 2.4 | 4.2 | 6.6 | 19.7 | 66.5 | 1 | 29 |

The scripted profile jumps twice and runs into a bump, then the player dies (`event die`), the intro and play repeat on stage 1-1, and after the last life the game shows `over` and returns to the title. Every window is stage 1-1 early in the stage or the intro, so these are light frames: the update median is 2.4 ms. The scripted run never moves past the first screen, so it does not exercise the heavy part of the stage the demo does.

## The bar

The spec asks for a work median of at most 12 ms and no frame's work over 16.7 ms after the first two seconds, judged from the second `frames` line on.

- Demo: missed. Work medians from window 3 on are 18.2, 16.5, 12.8, 7.7 and 13.8 ms; windows 3, 4, 5 and 7 are above 12 ms. From window 2 on the work worst is 331.2, 21.5, 28.2, 20.1, 15.2 and 280.2 ms, so frames above 16.7 ms occur in every window except window 6, and windows 2 and 7 each have a stall of 280 ms or more.
- Scripted: the median passes (6.5 ms in every window) but the work worst fails in all 5 windows after the first: 330.5, 330.5, 329.5, 330.5 and 19.7 ms. Four of them hold a stall of about 330 ms, the same size as the stall in demo window 2 (331.2 ms). I did not investigate the cause here.

## Reading

The update dominates the heavy demo frames: 12.9 ms at the worst against 5.3 ms of drawing, and it falls to 2.9 ms where fewer things are live. The drawing is a flat 4.2 to 5.3 ms in every window, including the title screen, and 27 to 33 shim crossings per frame cannot account for it, so it is likely the shim's tile window (the stub-draw pack would split it; a 23 by 15 cell window, up to two quads per non-empty cell, on OpenGX) rather than crossings. The demo's busiest frames need the update to fall by about a third to fit 12 ms of work, and the stalls of 280 to 330 ms need their own look.

## Rung 0: the enemy update by index (2026-11-05)

The enemy update now works by index: an enemy's `:id` is its slot in `:enemies`, dead enemies keep their slot with `:alive? false` instead of being pruned each frame, `find-enemy` and `update-enemy` are `nth` and `assoc-in`, and `walkers-bounce` and `shell-hits` are index loops. The main loop also reads `connected?` once per channel per frame, calls `rumble!` only when a channel's state changes, and prints `stall` lines for frames over 100 ms. Build: `jolt build:jolt-mona`, same jolt as above. Two `jolt run:jolt-mona` runs agreed on dropped counts and worst-case work; the medians differed by at most 0.2 ms (update 6758 vs 6572 us in the last window) and frame-worst by at most 0.7 ms (77394 vs 76756 us). The table is the first run. A `stall` line pairs the previous frame's interval with the current frame's work, so it is one frame off.

| Window (frames) | Update median (ms) | Draw median (ms) | Work median (ms) | Work worst (ms) | Frame worst (ms) | Dropped | Calls |
|---|---|---|---|---|---|---|---|
| 1-300 | 1.2 | 5.2 | 6.5 | 257.5 | 846.7 | 1 | 19 |
| 301-600 | 1.2 | 5.2 | 6.5 | 331.5 | 331.6 | 2 | 19 |
| 601-900 | 9.2 | 5.8 | 15.0 | 17.9 | 73.1 | 1 | 24 |
| 901-1200 | 9.3 | 4.9 | 14.1 | 18.1 | 76.9 | 1 | 25 |
| 1201-1500 | 7.9 | 4.8 | 12.8 | 24.2 | 77.4 | 2 | 23 |
| 1501-1800 | 7.2 | 4.5 | 11.9 | 17.9 | 74.9 | 1 | 21 |
| 1801-2100 | 6.8 | 4.7 | 11.0 | 280.6 | 280.7 | 2 | 22 |

Against the first section's demo run: the busiest window (601-900) went from update 12.9 ms, work 18.2 ms and 53 dropped frames to update 9.2 ms, work 15.0 ms and 1 dropped. Window 901-1200 went from 11.8 / 16.5 ms and 34 dropped to 9.3 / 14.1 ms and 1 dropped. Calls per frame fell by 8 on the title (27 to 19) and by 8 in play (32 to 24, 33 to 25), which is the saved `connected?` reads (4 of the 8 per frame, read twice before) plus the `rumble!` calls (4 per frame, now only on a change). The windows do not line up one to one after 900, because the faster frames let the bot reach the flag earlier: stage 1-2 now starts in window 1501-1800 (before, in 1801-2100). Draw is unchanged at 4.5 to 5.8 ms. The `stall` lines (frame-us 846,680, 331,556 and 280,684) match the old worst frames: they are the frame before the printing one, the title and stage loads, and the printing frame's own work is 5.5, 13.2 and 15.6 ms. The stall at the 1-2 load is not an enemy cost.

The bar (work median at most 12 ms, no frame's work over 16.7 ms after the first two seconds) is not met yet. Work medians from window 3 are 15.0, 14.1, 12.8, 11.9 and 11.0 ms, so three windows are above 12 ms. The work worst is 17.9, 18.1, 24.2, 17.9 and 280.6 ms, so every window has a frame over 16.7 ms. What moved: the update fell by about 3 ms (a quarter) where enemies are many, and the dropped frames nearly vanished, because the frames are now mostly inside the 20 ms budget. What did not: draw, the load stalls, and an update of 7 to 9 ms that remains while enemies are live.

## The stalls and the collector (2026-11-05)

The question: are the 280 to 850 ms stalls jolt's older-generation collections? Reproduce with the logging shim, which sets `JOLT_GC_LOG=1` at startup:

```
scripts/dkp make -C jolt-gfx EXTRA_CFLAGS=-DJG_GC_LOG
jolt gfx-pack && jolt build:jolt-mona && jolt run:jolt-mona
grep -E 'gc:|jolt-mona: (stall|frames|mode|stage)|jolt_gfx: heap' .dolphin-auto/idle/Logs/dolphin.log
```

Rebuild without `EXTRA_CFLAGS` afterwards (`rm -rf jolt-gfx/build` first, since make does not see the flag change) to get the normal shim back. One 45 s demo run, Dolphin, MMU emulation on. The timestamps below are the log's `SS.mmm` within one minute.

The run had three stalls. Each is listed with the nearest `gc:` line and with what the game was doing:

| Stall logged at | Stall interval (ms) | Work of the next frame (ms) | Nearest gc line | gc ms | `full:` | What the game did |
|---|---|---|---|---|---|---|
| 12.911 | 846.7 | 5.5 | 13.559 (0.65 s after), 12.103 (0.81 s before) | 10, 6 | none | the interval spans runtime start-up up to `jg_init` (12.520) and `mode title` (12.871); "first title frame" is an inference about where the time goes, not a measurement |
| 23.180 | 331.6 | 13.2 | 23.320 (0.14 s after) | 0 | none | `stage 1 1-1` and `mode demo` logged at 23.153/23.154 (a) |
| 46.218 | 280.7 | 15.6 | 46.407 (0.19 s after) | 0 | none | `stage 2 1-2 SERVER ROOM` logged at 46.193 (a) |

(a) The two stage times are from the run's log but are not kept in `gclog.txt`, which has no `stage` lines. The confirm run (normal shim, same build) logged `stage 1 1-1` 25 ms and `stage 2 1-2` 24 ms before the matching stall line, which corroborates them.

The first stall has one 6 ms collection inside it (12.103, in the interval that began about 12.06, under 1% of the 846.7 ms) and no other within 0.5 s. For the other two, the gc line comes after the stall, and it reports 0 ms. The stall line prints after the long frame ends, so the long frame began 0.28 to 0.33 s before its stall line, and the nearest collection in each case sits outside that span.

Collections: 28 in the 45 s run (two of them, at 11.366 and 12.103, are boot collections before `jg_init`), none of them full (no `full:` marker on any line), the longest 10 ms, the sum 29 ms. Every line reads `heap 36 or 37MB total 44MB peak 58MB live-after-full 36MB trip 2MB`, so the heap sits flat. On the title screen they are 3.4 to 3.5 s apart (17.104, 20.482), from 24.5 s on 1.0 to 1.5 s apart, and 2.2 s apart at 44.237 and 46.407 (`of 2217ms`, `of 2168ms`, around the stage 2 load). Per `frames` window (about 5.1 s each), the collections cost:

| Window ends at (s) | Collections | Sum (ms) | Share of the window |
|---|---|---|---|
| 23.287 | 1 | 0 | 0.0% |
| 28.366 | 5 | 0 | 0.0% |
| 33.436 | 4 | 0 | 0.0% |
| 38.556 | 5 | 9 | 0.18% |
| 43.614 | 3 | 0 | 0.0% |
| 48.929 | 4 | 0 | 0.0% |

The title screen window (ends 17.922, started at `mode title`, 12.871) holds two collections, 10 and 4 ms, 14 ms of 5.05 s, 0.28%. The whole run: 29 ms of collection in 45 s.

Conclusion: the stalls are not the collector. No full collection happened in the run, the collections that did happen took at most 10 ms, and one 6 ms collection sits inside the first stall (under 1% of it) and none inside the other two. The two stalls in the demo coincide with a stage load (`stage 1 1-1` at 23.153, `stage 2 1-2` at 46.193, from the run's log and not kept in the extract), and the 846.7 ms stall spans runtime start-up up to the first title frames (an inference about where its time goes), so they look like one-off work on first use or on a stage change (building the level, first sprite and tile uploads, first calls into code the runtime has not run yet). That is a reading of the timing, not something this run isolates. The hypothesis in the earlier sections (a periodic older-generation collection) is rejected. One caveat: the `ms` in the `gc:` line comes from jolt's own clock under Dolphin, so a collection that takes 0 ms there is a 0 ms collection on Dolphin's clock. The `of <elapsed>ms` figures agree with the log's wall time, which is why we trust it here.

What this suggests for rung 0b, as options for the user and not decisions:

- Static boot data, less per-frame allocation and a forced collection during the 2 s intro panel would all target the collector, which costs 29 ms in 45 s here. None of them would remove a stall of the size seen.
- If the stalls matter, the next measurement is the stage load: time `build-stage` and the first draw of a stage with `jg_time_us` around them, and see whether doing that work during the intro panel (or spreading it over frames) removes the 280 to 330 ms. The 846.7 ms first frame wants the same split between runtime start-up and the first draw.
- The recurring "every few seconds" in the earlier sections matches the three stalls in this run only as "once per stage load, plus the first frame". A longer run that crosses more stages would show whether more appear.

## The stage-change stall, located (2026-11-05)

A probe in `mona.main` timed the part of the frame after t2: `end-us` (`gfx/end!`, raylib's EndDrawing) and `post-us` (the `calls` read, the sample `conj`), and the stall line now carries the previous frame's two values (`prev-end-us`, `prev-post-us`). One 45 s demo run (idle profile, Dolphin's VIDEO and HOST_GPU logs on) and one 40 s scripted run. Dolphin only, no console.

- The stall is not in the tail. `prev-end-us` was 96 to 510 us and `prev-post-us` 25 to 44 us at every stall, and no frame had an `end-us` over 100 ms (no `slow-end` line).
- It is in the previous frame's update-plus-draw window. Each stage-load window's `work-worst-us` is within 100 us of its `frame-worst-us` (not the start-up window, 257 ms vs 847 ms, nor the first scripted window, 340767 vs 846684 us): stage 1-1 load 331499 vs 331578, stage 2 load 280614 vs 280691, scripted restarts 330800 vs 330876 and its siblings. The `work-us` printed on the stall line belongs to the frame after the long one, so it reads 5 to 16 ms. The earlier note that work never passed 28 ms compared the wrong frame.
- Sizes: stage 1-1 load 330 to 341 ms, stage 2 load 281 ms, title after game over 256 ms, start-up interval 847 ms of which about 257 ms is the first frame's work (the rest lies before its t0, inferred).
- It recurs: all six loads of stage 1-1 in the scripted run (the initial one plus five restarts) stalled 330 to 341 ms, so it is not a one-time compilation. Dolphin logged no (non-OSREPORT) lines near any stall (raylib's own "Texture loaded" lines sit in the start-up interval; the one Dolphin pipeline line is a startup read of 3 pipeline UIDs, six seconds before the title).
- Not split yet: update half (`app/tick`, `sync-world`) against draw half. The log order (the `stage` line, printed after the tick, comes about 20 ms of host time before the stall line) points at the update half. That is an inference.

## The stage load, split (2026-11-05)

`mona.main` now prints one `stage-load` line per load frame, and `draw/load-stage!` returns `[decor-us copy-us]`. `level-us` and `world-us` are duplicate builds made only for the timing (the app already built the level inside `tick`), so they cost an extra ~75 ms on each load frame in this probe; `tick-us` is the real `app/tick` on that frame, and it contains one genuine `level-at` plus `new-world` on intro and demo starts. One 45 s demo run and one 40 s scripted run, Dolphin only, in ms. The stall rows are the following frame's `frame-us`, which includes the probe's duplicate builds.

| Run | Load | tick | level | world | decor | copy | print | frame after |
|---|---|---|---|---|---|---|---|---|
| demo | title (start-up) | 1.1 | 73.0 | 1.6 | 4.1 | 244.8 | 0.3 | 921.9 |
| demo | stage 1-1 | 75.8 | 73.6 | 1.6 | 4.1 | 244.7 | 0.6 | 407.4 |
| demo | stage 1-2 | 59.8 | 56.2 | 1.3 | 0.9 | 213.3 | 0.3 | 339.5 |
| scripted | title (start-up) | 1.1 | 73.0 | 1.6 | 4.1 | 244.8 | 0.3 | 921.9 (the two runs' start-up rows agree to 0.1 ms; they are separate measurements, 921924 and 921902 us) |
| scripted | stage 1-1, initial load | 76.3 | 73.0 | 1.6 | 4.1 | 244.7 | 0.6 | 405.4 |
| scripted | stage 1-1, restarts 1 to 5 | 77.1 to 77.2 | 73.0 to 73.6 | 1.6 | 4.1 | 244.7 to 245.3 | 0.6 | 405.8 to 406.7 |
| scripted | title after game over | 1.5 | 73.0 | 1.6 | 4.1 | 244.7 | 0.3 | 330.6 |

The shim copy dominates: `copy-us` is 213 to 245 ms of a load frame, about 75% of the roughly 325 ms the stall measured before the probe (tick 76 + decor 4 + copy 245). The second piece is the level build, about 73 ms (`level-us`, matching the `tick-us` of 76 ms on intro and demo loads, so it is real, not probe cost). `world-us`, `decor-us` and `print-us` are each under 5 ms. So the stall is mostly the call that hands the tile arrays to the shim: the two `ffi/write-bytes` block copies of up to 2,850 bytes each plus the C copy, which suggests copying through `jg_tile_set` only for non-empty cells (measured later: 589 calls for 1-1) or finding why a 2.8 KB write costs 120 ms, and then the builders' 2,850 `set-tile!` calls and row/decor maps as the second target. That reading is inferred from the split; the cost inside `tiles-load!` is not itself broken down.

## The stage load, fixed (2026-11-05)

`draw/load-stage!` now calls `jg_tiles_begin` (width), scans the tile array and calls `jg_tile_set` for each non-zero cell, then `jg_decor_set` for each decor entry. The two `ffi/write-bytes` block copies are gone, as are the duplicate level and world builds of the probe. `stage-load` now prints `tick-us`, `hand-off-us` (`load-stage!`'s own time) and `print-us`. One 45 s demo run and one 40 s scripted run, Dolphin only. Hand-off in ms, before (the previous section's `decor + copy`) and after:

| Load | hand-off before | hand-off after | tick (real, after) | stall interval before | stall interval after |
|---|---|---|---|---|---|
| title (start-up) | 248.9 | 32.2 | 1.1 | 847 (922 with probe) | 614 |
| stage 1-1, demo | 248.8 | 32.2 | 75.8 | 332 | 115 |
| stage 1-2, demo | 214.2 | 25.9 | 59.8 | 281 | 93.5 (work-worst; no stall line) |
| stage 1-1 loads, scripted (initial plus five restarts) | 248.8 | 32.2 (42.8 on the first) | 76.3 to 77.2 | 330 to 341 | 113.8 to 124.6 |
| title after game over, scripted | 248.8 | 32.2 | 1.5 | 256 | no stall line (work-worst 39 ms) |

The cost of the hand-off fell by about 215 ms, not to the ~12 ms that was estimated: 32 ms for stage 1-1 (the 2,850-byte scan in Clojure plus 589 shim calls, measured, so roughly 54 us per call including the scan, not 20). The remaining stall is the level build itself: `tick-us` of 76 ms on an intro or demo start (`levels/level-at` plus `rules/new-world`, 73 ms of it the builders' `set-tile!` calls and the row and decor maps) plus the 32 ms hand-off, about 108 ms, then 6 ms of drawing. Stall lines remain for the stage 1-1 loads (113.8 to 124.6 ms intervals, over the 100 ms threshold) and the start-up (614 ms, of which about 41 ms is the first frame's work and the rest lies before its t0, inferred). The title after game over and stage 1-2 no longer print one. The next target is the level build (73 ms), then the scan loop.

### Fix round 1 (2026-11-05)

`load-stage!` now throws when the shim refuses the width, and the probe reads are gone from the loop except one after `app/tick`. The `stage-load` line is `tick-us hand-off-us calls`, with `calls` the shim call count read around `load-stage!`. Measured calls per stage load: 589 for stage 1-1 (title, demo and every scripted load), 584 for stage 1-2. Hand-off after the change: 31.6 to 31.7 ms for 1-1 (title and stage), 25.4 ms for 1-2. Stall intervals: 613 ms at start-up, 113 to 115 ms at each 1-1 load, none at 1-2 (work-worst 93 ms). The `tiles-load!` figure of 245 ms covers allocs, two `write-bytes`, the C call and the frees together, so the cost of the block copy alone is not established, and the per-cell path costs about 54 us per call; both look per-element on this target.

Steady-state frames, same build flags as the Rung 0 section: the demo's windows 601 to 2100 read work medians 15.0, 14.0, 12.8, 11.8 and 10.9 ms and `calls` 24, 25, 23, 21, 24, against Rung 0's 15.0, 14.1, 12.8, 11.9 and 11.0 ms and 24, 25, 23, 21, 22, so the per-frame cost did not change.

## Sound (2026-11-07)

Super Mona now plays ten chip tunes and seventeen effects through the shim's synthesiser (`jolt-gfx/jolt_snd.c`). One 45 s demo run from `jolt run:jolt-mona` on the sound build (branch `feat/mona-sound`, shim build `69c095a-dirty`), Dolphin only, the same build flags as the Rung 0 section. The `jolt verify` run that gated the sound passed all 18 checks, but its demo log was overwritten by later runs, so these figures come from the separate `jolt run:jolt-mona` made afterwards.

Work medians of the windows 601 to 2100, against the previous section's:

| Window | 601-900 | 901-1200 | 1201-1500 | 1501-1800 | 1801-2100 |
|---|---|---|---|---|---|
| before sound (ms) | 15.0 | 14.0 | 12.8 | 11.8 | 10.9 |
| with sound (ms) | 14.8 | 14.8 | 13.2 | 12.2 | 11.3 |

The per-frame cost grew by 0.4 to 0.8 ms in four of the five windows and fell by 0.2 ms in the first. The sound `calls` per window are 24, 25, 23, 21 and 24, the same as before. Different runs vary by a few tenths of a millisecond, so this is a single-run comparison, not an established cost. Every window printed `snd-cb=63`. The `snd-peak` and `sfx` values in that first run (1638 to 1774, 3 to 6) predate the `snd-music` counter and the star effect; the figures below come from a later run.

Heap, from the same run's `jg_init` line: `malloc live 48330676, fallback used 0 + 0, free 0 + 0; chez bytes-allocated 40190608, collect-trip-bytes 2097152`. (The pre-sound heap line is not in this repo's records; estimate: about 0.2 MB less live malloc, from the .dol delta below. Not measured.)

The shim's static footprint (`scripts/dkp powerpc-eabi-size jolt-gfx/build/jolt_snd.o`): text 10388, data 12, bss 3332 bytes, so 3,344 bytes of data plus bss.

The .dol grew from 11,872,224 bytes without sound to 12,017,920 bytes with it (+145,696). Putting all of `mona.music` on the Wii path gave 12.26 MB and ran out of heap at boot; these two .dol figures and that one were measured in the previous task, not here.

The audio dump (`jolt run:jolt-mona-audio`, `-C Dolphin.DSP.DumpAudio=True`): the WAV with the highest peak was `dspdump1.wav` (the 48 kHz AESND voice), peak 7718 of 32767. The frame log's `snd-peak` is a running maximum of the rendered samples since the previous summary (`snd_render` keeps it), so it is not a sample taken at one moment. The dump's 7718 and the log's figures sit in the same range once the stage music plays.

The log now also carries `snd-music`, the largest |sum of the four music voices| since the last summary, before the volume scaling (`jg_snd_music_peak`, effects excluded), and `jolt verify` requires it above 3000 in some window. One `jolt run:jolt-mona` on the build with the star effect (.dol 12,040,896 bytes) printed, for the five demo windows in order: `snd-music` 7470, 7357, 7479, 7485, 7304; `snd-peak` 7470, 9756, 8922, 9177, 7304; `sfx` 4, 10, 6, 9, 3. The two title windows before them read `snd-music` 7578 and 7546 with `sfx=0`. So the stage music does render in the demo (about 7300 to 7500 per window, where a song measured 6461 or more per window on the host), and the windows where `snd-peak` exceeds `snd-music` are the ones with several effects on top. An earlier run's 1638 to 1774 was not seen again; the cause was not investigated.
