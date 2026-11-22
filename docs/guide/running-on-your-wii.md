# Running on your Wii

**Status: two console runs (2026-11-06).** The C games boot from the Homebrew
Channel and draw on the TV. On the first card the jolt builds died at boot
with an alignment exception, the console's 750 trapping a double load at an
odd address that Dolphin had always accepted (see [Chez Scheme on the
Wii](chez-scheme-on-the-wii.md), the kernel patch). On the second card, with
the fix, jolt-hello printed all twelve lines and `pb: unaligned double loads 4
stores 0`, the same count Dolphin reports, and Balloon Pop (Clojure) and Super
Mona were played with a real Wiimote. The `chez` app ran its whole list too,
threads and runtime eval included, with `fib 25` in 109 ms against Dolphin's
139. The couch settings and the rest of the questions at the end of this page
are still unreported, so treat each first run of anything else as a test and
keep an eye on it.

## The Homebrew Channel

A Wii only runs Nintendo-signed discs and channels out of the box. Programs you
build yourself are called homebrew, and they launch from the **Homebrew
Channel**, which gets installed through a software exploit (a "softmod"). No
opening the case.

If your Wii already runs games from a USB hard drive through a loader such as
USB Loader GX or WiiFlow, it's softmodded and the Homebrew Channel is almost
certainly there. Look for it on the Wii Menu.

If it isn't installed, [wii.hacks.guide](https://wii.hacks.guide) is the guide
most people follow. It changes the console's system software, and a wrong step
can in rare cases leave the Wii unbootable, so follow it carefully and in
order.

## Putting apps on an SD card

```sh
jolt build
jolt sd
```

Then copy `build/sd/apps` to the root of an SD card (FAT32), so you end up with:

```
SD:/apps/hello/boot.dol
SD:/apps/hello/meta.xml
SD:/apps/chez/boot.dol
SD:/apps/jolt-hello/boot.dol
SD:/apps/jolt-hello/meta.xml
```

`meta.xml` holds the name and description the Homebrew Channel shows. A 128x48
`icon.png` next to it adds an icon, which none of ours have yet. The Homebrew
Channel also reads `apps/` from a USB drive, so the hard drive you already use
for games works too.

## Sending over Wi-Fi with wiiload

Swapping the SD card for every build gets old quickly. While the Homebrew
Channel is open it listens on the network, and `wiiload` sends it a `.dol`:

```sh
scripts/dkp bash -c 'WIILOAD=tcp:192.168.1.50 wiiload jolt-hello/build/jolt-hello.dol'
```

Use the Wii's own address, which the Homebrew Channel shows in its corner once
it has a network connection. We haven't tried this from inside Docker yet, so the
container may need `--network host` if the plain command can't reach the Wii.

## What to expect on screen

- **hello**: a blue screen with a bouncing ball. The bottom lines report the
  frame rate and which controller raylib sees. In Dolphin that's a GameCube
  pad; on the console the Wiimote should show up as an SDL joystick, which is
  one of the things to check.
- **chez**: white text listing Scheme expressions and their results, ending with
  "done. Press HOME to exit."
- **jolt-hello**: twelve lines of Clojure output, then a line
  `pb: unaligned double loads N stores M` (the interpreter's count of double
  reads it had to do in two halves; 4 and 0 in Dolphin), then "[program
  finished: press HOME or START to leave]".
- **jolt-balloons** ("Balloon Pop (Clojure)" in the Homebrew Channel) and
  **jolt-mona** ("Super Mona"): the games, with a small stamp along the bottom
  edge, `jolt <sha> pb <loads>/<stores> f<frame>`. The C Balloon Pop has no
  stamp, which is how the two are told apart on the TV.

HOME on a Wiimote, or START on a GameCube pad, returns to the Homebrew Channel
from the chez and jolt apps. The raylib app's exit path on hardware isn't
checked yet.

## Bringing evidence back from the console

A real Wii has no debug port that Dolphin's log stands in for, so each run has
to leave its evidence on the TV or on the card.

**The stamp.** Every frame a jolt game draws carries the git sha the graphics
shim was built from (`-dirty` when the tree had uncommitted changes), the
interpreter's unaligned double load and store counts, and the frame number. A
photo of the TV then says which build ran and how far it got. Writing
`debug_hud=0` into the game's `settings.txt` hides it.

**run.log.** The same games write `run.log` next to their `boot.dol`: the build
id, the heap report, a line every 300 frames and one at exit, each flushed to
the card as it is written, so a crash keeps the last line. Nothing is written
when there is no card to write to.

**Several keys in settings.txt.** The shared settings writer keeps the keys it
does not touch, so `debug_hud=0` and `mona_sound=0` can sit beside the high
score (`mona_high`) in one file.

**Clean captures.** For talk screenshots in Dolphin, write `debug_hud=0` into
`.work/sd-mona/apps/jolt-mona/settings.txt` before `jolt run:jolt-mona`, and the
stamp stays off the picture. `jolt demo:jolt-mona-video` does the same on its
own card folder.

**Sound evidence.** The 300-frame line in `run.log` does not carry the sound
stats yet (`snd-peak`, `sfx`, `snd-cb`, `snd-music` appear only in the console
output Dolphin shows), so a console run's evidence for sound is the ear until
that is added.

**Checking the card.** The card should carry the same files as `build/sd/apps`:

```sh
shasum -a 256 build/sd/apps/*/boot.dol /Volumes/<card>/apps/*/boot.dol
```

**Reading a crash.** The red screen names the exception and prints the
registers, with the faulting address in `PC` (SRR0) and the return addresses
in `STACK DUMP`. Each build keeps its `.elf` beside the `.dol`, and the
toolchain image has `addr2line`, so the screen maps to functions in one
command:

```sh
scripts/dkp powerpc-eabi-addr2line -f -e jolt-hello/build/jolt-hello.elf 0x800090c0 0x800a7914 0x80006860
```

On the first console run that gave `S_pb_interp`, `S_call_help`, `boot_call`:
the bytecode interpreter, called while the kernel loaded its boot files. The
elf has to be the one that produced the dol, which the hash check above
settles.

## Things to check on the first hardware run

These are the open questions that Dolphin can't answer:

1. Does `KEYBOARD_Init` succeed on a real Wii with no USB keyboard? The shim in
   `raylib-wii/wii_shim.c` covers both cases, but it would be good to know.
2. Does the Wiimote reach raylib's gamepad API through SDL?
3. ~~How fast is Chez really?~~ Answered 2026-11-06: `fib 25` took 109 ms on
   the console against 139 ms in Dolphin, so Dolphin's timing is a touch
   pessimistic, and every Dolphin number in this repo is an upper bound.
4. ~~Does the 10.9 MB jolt-hello `.dol` load?~~ Yes, and the 14.8 MB
   jolt-balloons and 11.9 MB jolt-mona with it (2026-11-06).
