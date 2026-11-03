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

## Next: the real console

1. Copy `bb sd`'s output to an SD card and run all three apps on the family Wii.
2. Answer the questions Dolphin can't (listed in
   [Running on your Wii](docs/guide/running-on-your-wii.md)): `KEYBOARD_Init`
   without a keyboard, the Wiimote through SDL, real Chez speed, and whether the
   10.9 MB jolt `.dol` loads.
3. Get `wiiload` working from the Docker image, so a build goes to the console
   without swapping SD cards.

## Then: something the kids can play

- **Wiimote input in raylib apps.** Check what SDL exposes (buttons, IR
  pointer, accelerometer) and wrap the bits raylib's gamepad API doesn't cover.
- **raylib from Clojure.** Bind raylib to jolt on the Wii the way raylib-ios
  does on iOS, so a game can be written in Clojure. That needs
  `:jolt/native` static archives in cross builds (not supported by
  `jolt build --target` yet), or registering the raylib entry points with Chez
  by hand from the C side.
- **A first small game** built with the kids, in whichever of C or Clojure
  works best on the console at that point.
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
- **devkitPro SDL:** check `KEYBOARD_Init`'s result in `OGC_VideoInit`.

Before filing any of them, re-check against the current upstream heads.

## Known limits

- `ogc_tls.c` has fixed tables (64 threads, 32 keys), never frees a row, and
  never runs destructors.
- The POSIX stubs fail rather than emulate: no subprocesses, no signals, no
  `iconv`.
- `cpu-time` in Chez is wall-clock time, since libogc has no per-process CPU
  accounting.
