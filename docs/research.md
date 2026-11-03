# Wii homebrew research notes

Started 2026-11-03. This is the running log of what we looked at, what we
found, and the links behind each finding. Newest results are at the bottom of
each section.

## Getting programs onto the family Wii

A stock Wii only runs Nintendo-signed discs and channels. Anything we build is
"homebrew", and it gets launched from the **Homebrew Channel** (HBC). HBC is
installed through a softmod (a software exploit, no case opening). If the Wii
already runs games off a USB hard drive through a loader like USB Loader GX or
WiiFlow, it's already softmodded and HBC is almost certainly installed.

HBC reads apps from `apps/<name>/` on the SD card or a USB drive:

```
apps/hello/boot.dol     the program
apps/hello/meta.xml     name/description shown in the channel
apps/hello/icon.png     optional, 128x48
```

`make -C examples APP=hello` produces exactly that tree under
`examples/build/hello/sd/`, so copying `sd/apps` onto the card is the whole
install.

There's also `wiiload`, which sends a `.dol` over Wi-Fi to HBC while HBC is open,
and that's much quicker than swapping SD cards during development.

Links:

- WiiBrew wiki, the community hub: <https://wiibrew.org>
- Homebrew Channel: <https://wiibrew.org/wiki/Homebrew_Channel>
- Softmod guide most people follow today: <https://wii.hacks.guide>
- Running GameCube homebrew on a Wii (for context only, we target Wii mode):
  <https://wiibrew.org/wiki/Running_GameCube_homebrew_on_the_Wii>
- Gcube, about the Wii-vs-GameCube library split:
  <https://wiibrew.org/wiki/Gcube>
- wiiload: <https://wiibrew.org/wiki/Wiiload>

## Toolchain

devkitPro's **devkitPPC** (a GCC for the Wii's PowerPC 750CL "Broadway" CPU)
plus **libogc**, the Wii/GameCube system library. We use the official Docker
image rather than installing it on the Mac:

```sh
docker pull devkitpro/devkitppc:latest
scripts/dkp <command>        # runs <command> in that image with the repo at /work
```

What the image ships, measured 2026-11-03:

| package | version | why it matters |
|---|---|---|
| libogc | 3.2.1 | video, Wiimote (WPAD via wiiuse), threads (LWP), SD/USB (libfat) |
| wii-sdl2 | 2.28.5 | SDL2 on libogc, so raylib's SDL platform has something to sit on |
| wii-opengx | 0.17.0 | OpenGL 1.x implemented over the Wii's GX GPU |
| newlib `pthread.h` | in libc | POSIX threads mapped onto libogc's LWP threads |
| ppc portlibs | `liblz4`, `libz` (via SDL deps), lua 5.1, box2d, freetype, png… | lz4 and zlib are what the Chez kernel links |

Two toolchain facts that cost a probe each:

- `powerpc-eabi-gcc -mrvl` defines `__wii__`, `HW_RVL`, `__PPC__`, `PPC` and
  `__BIG_ENDIAN__`, but **not** `__powerpc__`. Code that detects PowerPC with
  `__powerpc__` alone (Chez's `version.h` does) silently takes the
  little-endian path.
- The image's login shell sets `PATH`, but `docker run image cmd` does not, so
  `scripts/dkp` passes `PATH` explicitly.

Links:

- devkitPro getting started: <https://devkitpro.org/wiki/Getting_Started>
- libogc source: <https://github.com/devkitPro/libogc>
- libogc API docs: <https://libogc.devkitpro.org>
- devkitPro's SDL fork (the `ogc-sdl-2.28` branch is what the image ships):
  <https://github.com/devkitPro/SDL/tree/ogc-sdl-2.28>
- OpenGX: <https://github.com/devkitPro/opengx>
- devkitPro Docker images: <https://hub.docker.com/r/devkitpro/devkitppc>
- Wii example programs ship inside the image at `/opt/devkitpro/examples/wii`.

## Testing without the console: Dolphin

Dolphin emulates the Wii well enough to run libogc programs directly from a
`.dol`. Installed with `brew install --cask dolphin` (2609a).

`scripts/dolphin-shot app.dol out.png [seconds]` boots a `.dol` with an
isolated user dir (`.dolphin-user/`, gitignored, so your normal Dolphin
settings are untouched), screenshots the window, and prints the tail of
Dolphin's log.

Two settings matter:

- **stdout goes to Dolphin's log.** Call `SYS_STDIO_Report(true)` at the top of
  `main`; libogc then writes `printf` output to the EXI debug UART, which
  Dolphin logs as `OSREPORT`. On real hardware the same output goes to a USB
  Gecko if one's plugged in, and nowhere otherwise.
- **Turn on MMU emulation to see real crashes.** By default Dolphin pops a
  "would have crashed on real hardware" dialog and carries on. With
  `DOLPHIN_EXTRA="-C Dolphin.Core.MMU=True"` you get libogc's red exception
  screen with a stack dump, and `powerpc-eabi-addr2line -f -p -e app.elf
  <addrs>` turns that into function names.

Links:

- Dolphin: <https://dolphin-emu.org>
- Dolphin command-line options: `Dolphin --help` (the `-C System.Section.Key=Value`
  form is how the script sets config without touching ini files).

## raylib on the Wii

**Result: stock raylib 6.0 runs unmodified at 60 fps in Dolphin** (commit
`ab21dcc`), screenshot in `docs/images/hello-dolphin.png`.

How: raylib's `PLATFORM_DESKTOP_SDL` backend with `GRAPHICS_API_OPENGL_11`,
compiled with devkitPPC against wii-sdl2 and wii-opengx. raylib reports
`Vendor: opengx, Renderer: libogc, Version: 1.5`. The only build change was
dropping `-I vendor/raylib/external`, which made raylib's Windows `dirent.h`
shadow newlib's.

I found no prior raylib Wii port on GitHub or WiiBrew. Searches for raylib +
opengx / libogc / Wii turned up only general GL 1.1 notes:

- raylib GL 1.1 build regression, 3.7 era:
  <https://github.com/raysan5/raylib/issues/1732>
- raylib-extras premake pairing SDL2 with GL 1.1:
  <https://github.com/raylib-extras/game-premake/blob/main/raylib_premake5.lua>

### The keyboard crash (and the shim)

The first run crashed on a null read inside `__lwp_queue_get`. The stack, from
libogc's exception screen under MMU emulation:

```
KEYBOARD_GetEvent        libwiikeyboard/keyboard.c:637
OGC_PumpKeyboardEvents   SDL2-2.28.5/src/video/ogc/SDL_ogckeyboard.c:32
SDL_PumpEventsInternal   SDL_events.c:927
SDL_WaitEventTimeout     SDL_events.c:1097
PollInputEvents / EndDrawing / main
```

SDL's video init calls `KEYBOARD_Init(NULL)` and ignores the result. When that
init fails early (`USB_Initialize` or `USBKeyboard_Initialize` not `IPC_OK`,
which is what happens in Dolphin), libwiikeyboard never runs
`__lwp_queue_init_empty(&_queue)`. SDL still calls `KEYBOARD_GetEvent` every
frame, and that walks a zeroed queue. libogc master has the same code
(checked 2026-11-03). The only open libogc issue mentioning keyboards is #256,
which is unrelated.

`raylib-wii/wii_shim.c` wraps both functions with `ld --wrap`, so `GetEvent`
returns "no event" when `Init` failed. Whether `KEYBOARD_Init` also fails on a
real Wii with no USB keyboard is untested. The shim is harmless either way.

- libwiikeyboard source:
  <https://github.com/devkitPro/libogc/blob/master/libwiikeyboard/keyboard.c>
- SDL's OGC keyboard pump:
  <https://github.com/devkitPro/SDL/blob/ogc-sdl-2.28/src/video/ogc/SDL_ogckeyboard.c>
- SDL's OGC video init calling `KEYBOARD_Init`:
  <https://github.com/devkitPro/SDL/blob/ogc-sdl-2.28/src/video/ogc/SDL_ogcvideo.c>

## Jolt on the Wii: what has to be true

Jolt runs on Chez Scheme. The Wii is a 32-bit **big-endian** PowerPC with
88 MB of RAM (24 MB MEM1 + 64 MB MEM2) and no OS beyond libogc. Here's what
we found in our own trees:

- **Chez has a portable-bytecode target for exactly this shape:** `pb32b` /
  `tpb32b` (32-bit, big-endian, `t` = threaded), listed in
  `ChezScheme/s/cmacros.ss`. Bytecode means no JIT and no native PowerPC code
  generator needed. raylib-ios already runs Jolt this way (`tpb64l`) because
  iOS forbids JIT.
- **Jolt needs the threaded variant.** jasalt's WASM experiment found the
  non-threaded `pb` build aborts at `make-mutex`, and Jolt runs on `tpb32l`.
  So we need `tpb32b`, which needs pthreads, and newlib-on-libogc provides
  them.
- **Jolt's 32-bit support is partial.** CHANGELOG entry for #801: the hash
  engine was fixed for 32-bit Chez, but "it is not full `tpb32l` support.
  Other host code still applies checked `fx` ops to int-width values, which
  raise rather than corrupt." On 32-bit Chez the fixnum ceiling is 2^29-1, so
  int-width values above that become bignums.
- **Nobody has run Jolt (or any Chez) big-endian.** Expect byte-order bugs in
  any host C glue that reads multi-byte values.
- **Jolt cross-compiles already.** `jolt build --target <machine> --target-pack
  <dir>` (`jolt/tools/cross-compile/README.md`) retargets the last build step
  using a pack of the target's boot files, kernel and `xpatch`.
- **Boot files can be embedded.** Chez has `Sregister_boot_file_bytes`, so
  the `.dol` can carry them with no filesystem.
- **Size is the big risk.** The release `jolt.boot` on the Mac is 27.5 MB
  (64-bit), and the raylib-ios executable is 18.7 MB. A 32-bit build should be
  smaller, but the heap has to fit next to it in 88 MB.

Local references:

- `~/dev/jlt-commons/raylib-ios/docs/guide/index.md`: Jolt on `tpb64l`, who owns
  thread 0, static raylib + SDL2
- `~/dev/github--jasalt--jolt-wasm-raylib-experiment/README.md` and
  `REPORT.md`: Jolt on Chez `tpb32l` under Emscripten, the closest prior art
- `~/dev/jolt/tools/cross-compile/README.md`: target packs
- `~/dev/ChezScheme/BUILDING`: `--pb`, `--pbarch`, Emscripten section
- `~/dev/ChezScheme/c/version.h`: per-OS kernel config; the
  `__EMSCRIPTEN__` branch is the model for a Wii branch

Upstream:

- Chez Scheme: <https://github.com/cisco/ChezScheme>
- Jolt: <https://github.com/jolt-lang/jolt>, cross-compile issue
  <https://github.com/jolt-lang/jolt/issues/375>
- jasalt's WASM experiment:
  <https://github.com/jasalt/jolt-wasm-raylib-experiment>
