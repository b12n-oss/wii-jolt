# jolt-mona measurements

First frame-cost measurements of Super Mona (Clojure on jolt) for the Wii, taken in Dolphin. Not a console: nothing here has run on hardware.

## Setup

- Date: 2026-11-05.
- jolt: v0.8.23-429-ga65c59ed (`jolt --version`).
- Build: `bb build:jolt-mona`, `jolt build --target tpb32b` with `--closed-world --boot small`; `jolt-mona/build/jolt-mona.dol` is 11,901,344 bytes (11.9 MB).
- Dolphin with MMU emulation on. The demo runs (`bb run:jolt-mona`, 45 s) use the `idle` profile; the scripted runs (`bb run:jolt-mona-wiimote`, 40 s) use the `platformer` profile.
- The game prints one summary line per 300 frames. Work is update plus draw, timed from the top of the frame to just before `EndDrawing`. Dropped counts frames whose interval exceeded 20 ms. Calls is shim crossings per frame.
- The saved measure logs keep only the `jolt-mona:` lines, so they show no `jolt_gfx:` heap line. The shim prints it to stderr at `jg_init`, and Dolphin's own log has it for a jolt-mona run: `jolt_gfx: heap at jg_init: malloc live 46987384, fallback used 0 + 0, free 0 + 0; chez bytes-allocated 39710984, collect-trip-bytes 2097152`.
- Each task ran twice. The two demo runs produced identical medians, worst-case and dropped counts to the digit; the two scripted runs differ by at most 1 microsecond in some medians and a few milliseconds in the worst cases (the second run's lines are in the task's measure logs). The tables below are the first run of each; the second run's lines agree.

## Demo run (`bb run:jolt-mona`, 45 s)

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

## Scripted run (`bb run:jolt-mona-wiimote`, 40 s)

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

The enemy update now works by index: an enemy's `:id` is its slot in `:enemies`, dead enemies keep their slot with `:alive? false` instead of being pruned each frame, `find-enemy` and `update-enemy` are `nth` and `assoc-in`, and `walkers-bounce` and `shell-hits` are index loops. The main loop also reads `connected?` once per channel per frame, calls `rumble!` only when a channel's state changes, and prints `stall` lines for frames over 100 ms. Build: `bb build:jolt-mona`, same jolt as above. Two `bb run:jolt-mona` runs agreed on dropped counts and worst-case work; the medians differed by at most 0.2 ms (update 6758 vs 6572 us in the last window) and frame-worst by at most 0.7 ms (77394 vs 76756 us). The table is the first run. A `stall` line pairs the previous frame's interval with the current frame's work, so it is one frame off.

| Window (frames) | Update median (ms) | Draw median (ms) | Work median (ms) | Work worst (ms) | Frame worst (ms) | Dropped | Calls |
|---|---|---|---|---|---|---|---|
| 1-300 | 1.2 | 5.2 | 6.5 | 257.5 | 846.7 | 1 | 19 |
| 301-600 | 1.2 | 5.2 | 6.5 | 331.5 | 331.6 | 2 | 19 |
| 601-900 | 9.2 | 5.8 | 15.0 | 17.9 | 73.1 | 1 | 24 |
| 901-1200 | 9.3 | 4.9 | 14.1 | 18.1 | 76.9 | 1 | 25 |
| 1201-1500 | 7.9 | 4.8 | 12.8 | 24.2 | 77.4 | 2 | 23 |
| 1501-1800 | 7.2 | 4.5 | 11.9 | 17.9 | 74.9 | 1 | 21 |
| 1801-2100 | 6.8 | 4.7 | 11.0 | 280.6 | 280.7 | 2 | 22 |

Against the first section's demo run: the busiest window (601-900) went from update 12.9 ms, work 18.2 ms and 53 dropped frames to update 9.2 ms, work 15.0 ms and 1 dropped. Window 901-1200 went from 11.8 / 16.5 ms and 34 dropped to 9.3 / 14.1 ms and 1 dropped. Calls per frame fell by 8 on the title (27 to 19) and by 8 in play (32 to 24, 33 to 25), which is the saved `connected?` reads (4 of the 8 per frame, read twice before) plus the `rumble!` calls (4 per frame, now only on a change). The windows do not line up one to one after 900, because the faster frames let the bot reach the flag earlier: stage 1-2 now starts in window 1501-1800 (before, in 1801-2100). Draw is unchanged at 4.5 to 5.8 ms. The `stall` lines (frame-us 846,680, 331,556 and 280,684) match the old worst frames: they are the frame before the printing one, the title and stage loads, and the printing frame's own work is 5.5, 13.2 and 15.7 ms. The stall at the 1-2 load is not an enemy cost.

The bar (work median at most 12 ms, no frame's work over 16.7 ms after the first two seconds) is not met yet. Work medians from window 3 are 15.0, 14.1, 12.8, 11.9 and 11.0 ms, so three windows are above 12 ms. The work worst is 17.9, 18.1, 24.2, 17.9 and 280.6 ms, so every window has a frame over 16.7 ms. What moved: the update fell by about 3 ms (a quarter) where enemies are many, and the dropped frames nearly vanished, because the frames are now mostly inside the 20 ms budget. What did not: draw, the load stalls, and an update of 7 to 9 ms that remains while enemies are live.

## The stalls and the collector (2026-11-05)

The question: are the 280 to 850 ms stalls jolt's older-generation collections? Reproduce with the logging shim, which sets `JOLT_GC_LOG=1` at startup:

```
scripts/dkp make -C jolt-gfx EXTRA_CFLAGS=-DJG_GC_LOG
bb gfx-pack && bb build:jolt-mona && bb run:jolt-mona
grep -E 'gc:|jolt-mona: (stall|frames|mode|stage)|jolt_gfx: heap' .dolphin-auto/idle/Logs/dolphin.log
```

Rebuild without `EXTRA_CFLAGS` afterwards (`rm -rf jolt-gfx/build` first, since make does not see the flag change) to get the normal shim back. One 45 s demo run, Dolphin, MMU emulation on. The timestamps below are the log's `SS.mmm` within one minute.

The run had three stalls. Each is listed with the nearest `gc:` line and with what the game was doing:

| Stall logged at | Stall interval (ms) | Work of the next frame (ms) | Nearest gc line | gc ms | `full:` | What the game did |
|---|---|---|---|---|---|---|
| 12.911 | 846.7 | 5.5 | 13.559 (0.65 s after), 12.103 (0.81 s before) | 10, 6 | none | first frame of the title screen, 0.04 s after `mode title` |
| 23.180 | 331.6 | 13.2 | 23.320 (0.14 s after) | 0 | none | `stage 1 1-1` and `mode demo` logged at 23.153/23.154 |
| 46.218 | 280.7 | 15.6 | 46.407 (0.19 s after) | 0 | none | `stage 2 1-2 SERVER ROOM` logged at 46.193 |

The first stall has no collection within 0.5 s. For the other two, the gc line comes after the stall, and it reports 0 ms. The stall line prints after the long frame ends, so the long frame began 0.28 to 0.33 s before its stall line, and the nearest collection in each case sits outside that span.

Collections: 28 in the 45 s run, none of them full (no `full:` marker on any line), the longest 10 ms, the sum 29 ms. Every line reads `heap 36 or 37MB total 44MB peak 58MB live-after-full 36MB trip 2MB`, so the heap sits flat. On the title screen they are 3.4 to 3.5 s apart (17.104, 20.482), from 24.5 s on 1.0 to 1.5 s apart, and 2.2 s apart at 44.237 and 46.407 (`of 2217ms`, `of 2168ms`, around the stage 2 load). Per `frames` window (about 5.1 s each), the collections cost:

| Window ends at (s) | Collections | Sum (ms) | Share of the window |
|---|---|---|---|
| 23.287 | 1 | 0 | 0.0% |
| 28.366 | 5 | 0 | 0.0% |
| 33.436 | 4 | 0 | 0.0% |
| 38.556 | 5 | 9 | 0.18% |
| 43.614 | 3 | 0 | 0.0% |
| 48.929 | 4 | 0 | 0.0% |

The title screen window (ends 17.922, started at `mode title`, 12.871) holds two collections, 10 and 4 ms, 14 ms of 5.05 s, 0.28%. The whole run: 29 ms of collection in 45 s.

Conclusion: the stalls are not the collector. No full collection happened in the run, the collections that did happen took at most 10 ms, and none lies inside a stall. The two stalls in the demo coincide with a stage load (`stage 1 1-1` at 23.153, `stage 2 1-2` at 46.193), and the 846.7 ms stall is the first frame of the title screen, so they look like one-off work on first use or on a stage change (building the level, first sprite and tile uploads, first calls into code the runtime has not run yet). That is a reading of the timing, not something this run isolates. The hypothesis in the earlier sections (a periodic older-generation collection) is rejected. One caveat: the `ms` in the `gc:` line comes from jolt's own clock under Dolphin, so a collection that takes 0 ms there is a 0 ms collection on Dolphin's clock. The `of <elapsed>ms` figures agree with the log's wall time, which is why we trust it here.

What this suggests for rung 0b, as options for the user and not decisions:

- Static boot data, less per-frame allocation and a forced collection during the 2 s intro panel would all target the collector, which costs 29 ms in 45 s here. None of them would remove a stall of the size seen.
- If the stalls matter, the next measurement is the stage load: time `build-stage` and the first draw of a stage with `jg_time_us` around them, and see whether doing that work during the intro panel (or spreading it over frames) removes the 280 to 330 ms. The 846.7 ms first frame wants the same split between runtime start-up and the first draw.
- The recurring "every few seconds" in the earlier sections matches the three stalls in this run only as "once per stage load, plus the first frame". A longer run that crosses more stages would show whether more appear.
