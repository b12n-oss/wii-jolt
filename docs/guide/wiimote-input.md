# Wiimote input

All three games read the remotes through one small module,
`examples/common/wiimote.{h,c}`. This page covers what it gives a game, the
numbers we measured for tilt and shake, and how `bb verify` plays each game
with a scripted remote.

## The module

SDL's Wii port folds every remote's IR pointer into a single mouse, which is
no good for four players. So the games read libogc's WPAD directly:

```c
wiimote_init(SCREEN_W, SCREEN_H);   // buttons + accelerometer + IR, IR in screen pixels

Remote r[WIIMOTE_MAX];
if (wiimote_read(r)) break;         // once a frame; true when anyone pressed HOME
// r[p].connected, .held, .pressed, .ir_valid, .ir_x, .ir_y, .gx, .gy, .gz, .shake

wiimote_rumble(p, on);              // only talks to the remote when it changes
```

`pressed` is "held now, not held last frame", worked out from `btns_h`, so a
press that lands between two reads isn't lost. `shake` is how far the
acceleration moved since the last frame, `|g_now - g_before|` in g, and it's
zero on the first frame after a remote connects.

Each game turns a `Remote` into its own small input struct (`PlayerInput`),
so the rules in `game.c` never see a Wii header and the tests can feed them
anything.

One thing every app needs, module or not: `#include <SDL2/SDL_main.h>` in
`main.c`. SDL's own `main` is what calls `WPAD_Init`, and without it every
remote reads as disconnected. [Balloon Pop](balloon-pop.md) tells how we found
that.

## Tilt: what Dolphin reports

We booted a throwaway probe in Dolphin that printed `gforce` for each
scripted pose:

| Pose in Dolphin | gx | gy | gz |
|---|---|---|---|
| at rest, flat | 0.00 | 0.00 | 1.00 |
| upright, "tilt right" | 1.00 | 0.00 | 0.08 |
| sideways, "tilt right" | 0.00 | -1.00 | 0.08 |

So for a sideways remote, steering lives on `gy`, and in Dolphin tipping
right makes it negative. Star Catcher steers with `-gy` (`TILT_SIGN` in its
`main.c`), full speed at 0.5 g (about 30 degrees), with a 0.1 g dead zone.

Our own reasoning about the accelerometer's axes said the opposite sign.
Dolphin wins for now, because Wii games are tested against it. But this is
the first thing to try on the console: if the basket goes the wrong way, flip
`TILT_SIGN`.

## Shake: what Dolphin reports

The same probe, with Dolphin shaking the remote on all three axes for 1 s in
every 2 s, averaged over 2-second windows:

| | per-frame `shake` |
|---|---|
| held still (tilt runs) | 0.04 to 0.09 |
| shaking, peak | 2.45 |
| shaking, mean over 2 s | 0.85, with 62 of 120 frames over 0.25 |

That's where Shake Race's 0.25 g dead zone comes from: well above a
still remote, well below a shake. A real remote's noise, and how hard a
child really shakes, are both still unmeasured.

## Scripted remotes for testing

`scripts/dolphin-auto-wiimote <profile>` writes a Dolphin user directory under
`.dolphin-auto/<profile>/` whose Wiimote 1 plays by itself, through Dolphin's
input expressions:

| Profile | Used by | What the remote does |
|---|---|---|
| `pointer` | Balloon Pop | A four times a second; IR sweeps left and right every 5 s |
| `tilt` | Star Catcher | A four times a second; held sideways, tipped about 70 degrees each way every 4 s |
| `shake` | Shake Race | A four times a second; shaken 1 s in every 2 |
| `play` | `bb play:*` | nothing scripted: Dolphin's mouse and keyboard defaults, plus Space to shake |

`bb verify` boots each game twice, once to watch the robot demo and once with
its scripted remote, and requires the remote to join, the round to start, and
player 1 to score during it.

Two traps from writing these:

- **A bare number is a key name.** `Tilt/Right = 1` does nothing, because
  Dolphin reads `1` as the 1 key. A constant has to be an expression, such as
  `sin(1.5708)`.
- **Dolphin ignores input when its window isn't focused.** Every control
  expression, timer-based or not, reads 0 unless the game window has focus
  (`UpdateInputGate` in Dolphin's `Core.cpp`). Whether a test run got focus
  depended on what else was on the Mac's screen, so a scripted remote would
  sometimes never join, or join and then stop. `scripts/dolphin-shot` now
  passes `-C Dolphin.Input.BackgroundInput=True`. Shake Race failed 2 of its
  first 4 scripted runs before that and passed 3 of 3 after.
