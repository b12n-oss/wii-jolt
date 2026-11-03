# Roadmap

## Done (2026-11-03)

- Stock raylib 6.0 on the Wii through wii-sdl2 and OpenGX, with a link-time
  guard for the libwiikeyboard null-queue crash.
- Chez Scheme 10.4.1 on `tpb32b`: kernel patch, TLS hooks, POSIX stubs, boot
  files embedded in the `.dol`.
- Clojure through `jolt build --target tpb32b`, with output matching the Mac
  build. jolt-side fix on a local branch, `feat/wii-tpb32b`.
- `bb verify` builds all three and checks each one's output in Dolphin with MMU
  emulation on.

## Done: Balloon Pop (2026-11-03)

A Wiimote pointer game for up to four players, with rumble and a robot demo.
45 host-side checks on its rules. `bb verify` plays it in Dolphin twice: the
robot demo, and a round driven by a scripted emulated Wiimote (A to join, IR
pointer pops). Building that second test found that raylib apps never
initialised the Wiimotes at all (SDL's `main` wrapper wasn't linked), now
fixed in `balloons` and `hello`. Real remotes, rumble and the sensor bar are
untested until the console.

Open: one boot hang in about ten runs right after the SDL `main` change, not
reproduced since. See [Balloon Pop](docs/guide/balloon-pop.md).

## Done: Star Catcher and Shake Race (2026-11-03)

Two more games, each using the Wiimote a new way: Star Catcher steers by
tilting a sideways remote, and Shake Race runs on shaking. 38 and 35
host-side checks. The Wiimote code moved into `examples/common/wiimote.c`,
which all three games now share. `bb verify` plays every game twice, with
the robot demo and with a scripted remote (pointer, tilt or shake).

While testing these, we found that Dolphin ignores controller input whenever
its window isn't focused, which made scripted runs flaky. `dolphin-shot` now
turns on background input. See [Wiimote input](docs/guide/wiimote-input.md).

## Done: couch settings (2026-11-03)

Star Catcher's steering direction and Shake Race's shake power can be changed
from the remote and are saved to `settings.txt` beside each game's `boot.dol`
(`examples/common/settings.c`). `bb verify` checks the save and reload
through Dolphin's emulated SD card.

## Done: Balloon Pop in Clojure (2026-11-03)

Balloon Pop is also written in Clojure (`jolt-balloons/`) with pure rules, drawing
through a C shim (`jolt-gfx/`) that registers raylib and the Wiimote functions
with Chez at link time. It plays in Dolphin but misses the 60 fps budget on most
frames: about 23 to 24 ms of work per frame against 3 to 4 ms in C, measured in
Dolphin, not on a console. A checked `(long x)` on 32-bit Chez was the big cost
(draw went from 252 ms to 60 ms once it became `unchecked-long`).
[Balloon Pop in Clojure](docs/guide/balloon-pop-in-clojure.md) has the
comparison and what it taught us about jolt.

## Next: the real console

1. Copy `bb sd`'s output to an SD card and run every app on the family Wii.
2. Play a round of Balloon Pop with real Wiimotes: does every remote get its own
   pointer, do A and B pop, does the rumble fire, and is aiming comfortable from
   the couch?
3. Star Catcher: does tipping right move the basket right? The default came
   from Dolphin. If it's backwards, press − on the title screen (saved to the
   SD card), then make the right direction the default
   (`TILT_SIGN_DEFAULT` in `examples/starcatcher/game.h`). Is 30 degrees of
   tip for full speed comfortable?
4. Shake Race: which shake power suits the kids? Set it with + and − and note
   the level, then make it the default (`SHAKE_LEVEL_DEFAULT`). Also check
   that a remote held still doesn't creep forward (the dead zone, in
   `examples/shakerace/game.h`).
5. Answer the questions Dolphin can't (listed in
   [Running on your Wii](docs/guide/running-on-your-wii.md)): `KEYBOARD_Init`
   without a keyboard, the Wiimote through SDL, real Chez speed, and whether the
   10.9 MB jolt `.dol` loads.
6. Get `wiiload` working from the Docker image, so a build goes to the console
   without swapping SD cards.

## Then: more for the kids

- **More Wiimote.** The shared module has buttons, IR, the accelerometer and
  rumble. The Nunchuk is next: two-handed games, and a stick for the kids who
  find tilting hard.
- **raylib from Clojure.** Done for Balloon Pop, through link-time
  registration of the shim's functions (see
  [Balloon Pop in Clojure](docs/guide/balloon-pop-in-clojure.md)). The games
  stay in C: the Clojure one plays but drops most frames. Two jolt-side
  changes could alter that, in this order. First, a cheaper checked `long`
  cast on 32-bit (4x on this game's drawing; useful on any 32-bit target).
  Second, a native PowerPC backend: Chez ships `s/ppc32.ss` with big-endian
  support, and the Wii's 750CL is the G3 family it targeted, so a `tppc32wii`
  machine type over the existing libogc runtime patch could replace the
  bytecode interpreter. That is the only change with a chance of 60 fps. It
  needs a spike: the EABI foreign-call convention and the executable-memory
  path are the unknowns. Also still open: keep the link-time glue, or teach
  `jolt build --target` about `:jolt/native` static archives.
- **The next game** built with the kids: their ideas, their drawings.
- **Sound.** raylib's `raudio` is left out today. Try miniaudio against libogc,
  or use libogc's own ASND/AESND.

## Upstream, when you say so

Nothing goes upstream until it's explicitly asked for. Candidates:

- **jolt:** the `feat/wii-tpb32b` branch (32-bit cross targets get the narrow
  hash arm and generic fx ops). A longer-term fix would replace the checked fx
  ops at each int-width site.
- **Chez Scheme:** the `__wii__` kernel branch, and adding `__PPC__` to the
  big-endian test in `version.h`, which helps any PowerPC toolchain that doesn't
  define `__powerpc__`.
- **libogc:** `KEYBOARD_GetEvent` before a successful init; `WriteUARTN`
  writing into a `const` buffer; the missing `__syscall_tls_*` and
  `__syscall_thread_detach` hooks.
- **devkitPro SDL:** check `KEYBOARD_Init`'s result in `OGC_VideoInit`. Maybe
  also a note in their README that apps with their own `main` must include
  `SDL_main.h` to get Wiimotes.

Before filing any of them, re-check against the current upstream heads.

## Known limits

- `ogc_tls.c` has fixed tables (64 threads, 32 keys), never frees a row, and
  never runs destructors.
- The POSIX stubs fail rather than emulate: no subprocesses, no signals, no
  `iconv`.
- `cpu-time` in Chez is wall-clock time, since libogc has no per-process CPU
  accounting.
