# Running on your Wii

**Status: one console run so far (2026-11-06).** The C games boot from the
Homebrew Channel and draw on the TV. The jolt builds died at boot with an
alignment exception, the console's 750 trapping a double load at an odd
address that Dolphin had always accepted (see [Chez Scheme on the
Wii](chez-scheme-on-the-wii.md), the kernel patch); the fix is in, and the
next card is its test. Everything else below is still only proven in Dolphin
with MMU emulation on, so treat each first run on the console as a test and
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
bb build
bb sd
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
- **jolt-hello**: twelve lines of Clojure output, then "[program finished: press
  HOME or START to leave]".

HOME on a Wiimote, or START on a GameCube pad, returns to the Homebrew Channel
from the chez and jolt apps. The raylib app's exit path on hardware isn't
checked yet.

## Things to check on the first hardware run

These are the open questions that Dolphin can't answer:

1. Does `KEYBOARD_Init` succeed on a real Wii with no USB keyboard? The shim in
   `raylib-wii/wii_shim.c` covers both cases, but it would be good to know.
2. Does the Wiimote reach raylib's gamepad API through SDL?
3. How fast is Chez really? Dolphin keeps the Wii's timing, so `fib 25` in
   139 ms should be close, but only the console can confirm it.
4. Does the 10.9 MB jolt-hello `.dol` load? It fits the Wii's 24 MB MEM1, but
   loaders differ in what they accept.
