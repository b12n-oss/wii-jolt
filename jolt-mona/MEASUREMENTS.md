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
