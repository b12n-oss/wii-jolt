# Roadmap

## Done (2026-11-03)

- Stock raylib 6.0 on the Wii through wii-sdl2 and OpenGX, with a link-time
  guard for the libwiikeyboard null-queue crash.
- Chez Scheme 10.4.1 on `tpb32b`: kernel patch, TLS hooks, POSIX stubs, boot
  files embedded in the `.dol`.
- Clojure through `jolt build --target tpb32b`, with output matching the Mac
  build. The jolt-side fix is upstream (jolt-lang/jolt#1514, 2026-11-05).
- `jolt verify` builds all three and checks each one's output in Dolphin with MMU
  emulation on.

## Done: Balloon Pop (2026-11-03)

A Wiimote pointer game for up to four players, with rumble and a robot demo.
45 host-side checks on its rules. `jolt verify` plays it in Dolphin twice: the
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
which all three games now share. `jolt verify` plays every game twice, with
the robot demo and with a scripted remote (pointer, tilt or shake).

While testing these, we found that Dolphin ignores controller input whenever
its window isn't focused, which made scripted runs flaky. `dolphin-shot` now
turns on background input. See [Wiimote input](docs/guide/wiimote-input.md).

## Done: couch settings (2026-11-03)

Star Catcher's steering direction and Shake Race's shake power can be changed
from the remote and are saved to `settings.txt` beside each game's `boot.dol`
(`examples/common/settings.c`). `jolt verify` checks the save and reload
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

## Done: Super Mona in jolt (2026-11-05)

Super Mona, Martin Woodward's 32-stage MicroPython platformer for the GitHub
Universe 2026 badge, is ported to Clojure (`jolt-mona/`) as the second Clojure
game, played with the remote held sideways. The stages match the Python byte
for byte and the demo bot finishes every one at 30 and 60 Hz. In Dolphin, play
on the first screen takes 6.5 ms of work per frame, but the demo deep in stage
1-1 takes 12.8 to 18.2 ms with up to 53 frames dropped per 300, and every run
stalls for 280 to 340 ms every few seconds. The spec's bar (12 ms median, no
work frame over 16.7 ms) is missed on busy frames. The fallback ladder is
untried and the next rung is the user's call: an algorithmic fix to the enemy
update, then the collector, then batched sprite calls, enemy physics in C, and
the renderer in C. [Super Mona in jolt](docs/guide/super-mona-in-jolt.md) has
the numbers.

## Next: the real console

1. ~~Copy `jolt sd`'s output to an SD card and run every app on the family Wii.~~
   Done 2026-11-06, in two rounds. The first card ran the C games and Hello
   raylib and stopped every jolt build at boot with an alignment exception
   (the kernel patch fixed it, see the guide). The second card ran
   jolt-hello (all twelve lines on the TV, `pb: unaligned double loads 4
   stores 0`, the same count as Dolphin), Balloon Pop (Clojure) and Super Mona,
   both played with a real Wiimote, and `chez` through its whole list (`fib 25`
   in 109 ms, Dolphin says 139). The couch settings are still unreported.
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
   without a keyboard, the Wiimote through SDL. Answered 2026-11-06: Chez's
   speed (`fib 25` in 109 ms on the console, 139 in Dolphin) and the `.dol`
   sizes (10.9 MB jolt-hello, 14.8 MB jolt-balloons, 11.9 MB jolt-mona all
   load).
6. Get `wiiload` working from the Docker image, so a build goes to the console
   without swapping SD cards.

## Then: more for the kids

- **More Wiimote.** The shared module has buttons, IR, the accelerometer and
  rumble. The Nunchuk is next: two-handed games, and a stick for the kids who
  find tilting hard.
- **raylib from Clojure.** Done for Balloon Pop, through link-time
  registration of the shim's functions (see
  [Balloon Pop in Clojure](docs/guide/balloon-pop-in-clojure.md)). The cast
  work merged upstream (jolt-lang/jolt#1491, #1493, #1494) and Super Mona is
  the second Clojure game: it plays at 6.5 ms of work a frame on the first
  screen, but its busy frames reach 18.2 ms in Dolphin. The cheaper
  checked `long` cast on 32-bit is in (4x on this game's drawing). One
  jolt-side change could still go further: a native PowerPC backend: Chez ships `s/ppc32.ss` with big-endian
  support, and the Wii's 750CL is the G3 family it targeted, so a `tppc32wii`
  machine type over the existing libogc runtime patch could replace the
  bytecode interpreter. That is the change with the best chance of 60 fps. It
  needs a spike: the EABI foreign-call convention and the executable-memory
  path are the unknowns. Also still open: keep the link-time glue, or teach
  `jolt build --target` about `:jolt/native` static archives.
- **The next game** built with the kids: their ideas, their drawings.
- ~~**Sound.**~~ Done for Super Mona, 2026-11-07: a chip synthesiser in the
  shim on libogc's AESND (seven voices, an effect player, a four-track
  sequencer), seventeen effects and ten original tunes written as note data in
  `jolt-mona/src/mona/music.clj`, cues from the app's state, and a `-` mute on
  the title saved as `mona_sound`. `jolt verify` checks it in Dolphin through the
  shim's stats (three new gates). Dolphin only; see
  [Super Mona in jolt](docs/guide/super-mona-in-jolt.md#sound).
- **Sound for Balloon Pop (Clojure).** The next sound arc: recipes for pop,
  miss and the round, cues from its events and a round tune, all on the same
  synthesiser. After that the C games, through the C core without the shim.
- **The first console run of the sound.** Nothing on the sound branch has run
  on the real Wii. It must check: the `jolt_gfx:` heap line in `run.log` with
  sound in (a console boot is the real heap test, since the .dol is within
  about 240 KB of the size that ran out of heap); music audible through the
  title, a stage, pause and resume, a death, and a full stage clear through the
  castle walk and the tally; the star sound; that `-` mutes and the mute
  survives a reboot; that HOME returns to the Homebrew Channel cleanly (the
  AESND voice is never stopped at exit, so watch for a hang or a buzz); and no
  new hitches with audio running.

## Upstream, when you say so

Nothing goes upstream until it's explicitly asked for. Candidates:

- **jolt:** done. The 32-bit cross-target fix (narrow hash arm and generic fx
  ops in the generated compile script) merged as jolt-lang/jolt#1514 on
  2026-11-05, after the cast work (#1491, #1493, #1494). A longer-term fix
  would replace the checked fx ops at each int-width site.
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
