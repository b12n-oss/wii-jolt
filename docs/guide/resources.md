# Resources and links

Everything we read while building this, grouped by layer, with a note on what
each one told us. Started 2026-11-03. Every external link was checked that day.
dolphin-emu.org refuses scripted requests, but it opens fine in a browser.

## Wii homebrew in general

- [WiiBrew](https://wiibrew.org): the community wiki. Start here for anything
  about the console, its hardware, or homebrew tools.
- [Homebrew Channel](https://wiibrew.org/wiki/Homebrew_Channel): the launcher
  homebrew runs from, and the `apps/<name>/boot.dol` + `meta.xml` layout it
  reads from SD or USB.
- [wii.hacks.guide](https://wii.hacks.guide): the softmod guide most people
  follow to install the Homebrew Channel.
- [wiiload](https://wiibrew.org/wiki/Wiiload): sends a `.dol` to the Homebrew
  Channel over the network. It ships in the devkitPPC image at
  `/opt/devkitpro/tools/bin/wiiload`.
- [Running GameCube homebrew on the Wii](https://wiibrew.org/wiki/Running_GameCube_homebrew_on_the_Wii)
  and [Gcube](https://wiibrew.org/wiki/Gcube): the Wii-mode versus
  GameCube-mode split. We target Wii mode, which is what gives us the Wiimote
  and MEM2.

## Toolchain

- [devkitPro: Getting Started](https://devkitpro.org/wiki/Getting_Started):
  the official install route. We use the Docker image instead.
- [devkitpro/devkitppc on Docker Hub](https://hub.docker.com/r/devkitpro/devkitppc):
  the image `scripts/dkp` runs. As pulled on 2026-11-03 it carries libogc 3.2.1,
  wii-sdl2 2.28.5, wii-opengx 0.17.0, newlib with pthreads, and portlibs
  including lz4 and zlib.
- [libogc](https://github.com/devkitPro/libogc) and its
  [API docs](https://libogc.devkitpro.org): the Wii's system library.
- [devkitPro's SDL fork, `ogc-sdl-2.28` branch](https://github.com/devkitPro/SDL/tree/ogc-sdl-2.28):
  the SDL2 the image ships.
- [OpenGX](https://github.com/devkitPro/opengx): OpenGL 1.x over the GX GPU.
- The image's own examples, at `/opt/devkitpro/examples/wii`.

Two toolchain facts that each cost a probe:

- `powerpc-eabi-gcc -mrvl` defines `__wii__`, `HW_RVL`, `__PPC__`, `PPC` and
  `__BIG_ENDIAN__`, but not `__powerpc__`.
- The image only puts devkitPPC on `PATH` for a login shell. `docker run image
  cmd` doesn't get it, so our scripts pass `PATH` explicitly.

## Dolphin

- [Dolphin](https://dolphin-emu.org): installed with `brew install --cask
  dolphin` (build 2609a here). `Dolphin --help` lists the command-line options,
  and `-C System.Section.Key=Value` sets any config value for one run, which is
  how `scripts/dolphin-shot` turns on MMU emulation and logging without
  touching ini files.

## Scripting Dolphin's emulated Wiimote

- [Dolphin `Core.cpp`](https://github.com/dolphin-emu/dolphin/blob/master/Source/Core/Core/Core.cpp),
  `UpdateInputGate`: every controller expression reads 0 while the game window
  isn't focused, unless background input is on.
- [Dolphin `MainSettings.cpp`](https://github.com/dolphin-emu/dolphin/blob/master/Source/Core/Core/Config/MainSettings.cpp):
  `MAIN_INPUT_BACKGROUND_INPUT`, `Input.BackgroundInput`, default false. We
  set it with `-C Dolphin.Input.BackgroundInput=True`.
- [Dolphin `WiimoteEmu.cpp`](https://github.com/dolphin-emu/dolphin/blob/master/Source/Core/Core/HW/WiimoteEmu/WiimoteEmu.cpp),
  `Wiimote::LoadDefaults`: the Mac's default mapping (left click A, right
  click B, arrows for the D-pad, Return for HOME, middle click to shake, no
  tilt keys).
- [Dolphin `QuartzKeyboardAndMouse.mm`](https://github.com/dolphin-emu/dolphin/blob/master/Source/Core/InputCommon/ControllerInterface/Quartz/QuartzKeyboardAndMouse.mm):
  the Mac key names an expression can use, such as `Space` and `Return`.

- [Dolphin `FunctionExpression.cpp`](https://github.com/dolphin-emu/dolphin/blob/master/Source/Core/InputCommon/ControlReference/FunctionExpression.cpp):
  the functions a controller mapping can use (`timer`, `sin`, `toggle`,
  `pulse`, ...). `timer(s)` is a 0-to-1 sawtooth every `s` seconds.
- [Dolphin `WiimoteSettings.cpp`](https://github.com/dolphin-emu/dolphin/blob/master/Source/Core/Core/Config/WiimoteSettings.cpp):
  Wiimote 1 defaults to the emulated source.

## raylib

- [raylib](https://github.com/raysan5/raylib): we vendor the 6.0 release source
  unmodified.
- [raylib #1732](https://github.com/raysan5/raylib/issues/1732): an older GL 1.1
  build regression. Not a problem in 6.0, but it explains why GL 1.1 builds are
  rare.
- [raylib-extras game-premake](https://github.com/raylib-extras/game-premake/blob/main/raylib_premake5.lua):
  the one place we found pairing raylib's SDL backend with GL 1.1.
- No earlier raylib port to the Wii turned up on GitHub or WiiBrew.

The keyboard crash, source by source:

- [libwiikeyboard `keyboard.c`](https://github.com/devkitPro/libogc/blob/master/libwiikeyboard/keyboard.c):
  `KEYBOARD_Init` returns before `__lwp_queue_init_empty(&_queue)` when USB
  init fails, and `KEYBOARD_GetEvent` doesn't check.
- [SDL `SDL_ogcvideo.c`](https://github.com/devkitPro/SDL/blob/ogc-sdl-2.28/src/video/ogc/SDL_ogcvideo.c):
  calls `KEYBOARD_Init(NULL)` and ignores the result.
- [SDL `SDL_wii_main.c`](https://github.com/devkitPro/SDL/blob/ogc-sdl-2.28/src/main/wii/SDL_wii_main.c):
  SDL's own `main` for the Wii, which calls `WPAD_Init()`. A raylib app has to
  include `SDL_main.h` to get it.
- [SDL `SDL_ogcevents.c`](https://github.com/devkitPro/SDL/blob/ogc-sdl-2.28/src/video/ogc/SDL_ogcevents.c):
  every remote's IR pointer is sent as the same SDL mouse.
- [SDL `SDL_ogckeyboard.c`](https://github.com/devkitPro/SDL/blob/ogc-sdl-2.28/src/video/ogc/SDL_ogckeyboard.c):
  calls `KEYBOARD_GetEvent` every frame.
- The only open libogc issue mentioning keyboards, #256, is about something
  else.

## Chez Scheme

- [Chez Scheme](https://github.com/cisco/ChezScheme): we build tag `v10.4.1`
  from a local clone.
- `ChezScheme/BUILDING`: the `--pb`, `--pbarch` and Emscripten sections, which
  describe portable bytecode and booting from embedded boot files.
- `ChezScheme/s/cmacros.ss`: the machine type list where `pb32b` / `tpb32b`
  live.
- `ChezScheme/c/version.h`: per-OS kernel configuration. The `__EMSCRIPTEN__`
  branch is what our `__wii__` branch is modelled on.
- devkitPro newlib's `sys/iosupport.h` (in the image, and in
  [devkitPro/newlib](https://github.com/devkitPro/newlib) on the `devkitPro`
  branch): the `__syscall_tls_*` and `__syscall_thread_detach` hooks libogc
  leaves unimplemented.

## jolt

- [jolt](https://github.com/jolt-lang/jolt), and
  [#375](https://github.com/jolt-lang/jolt/issues/375), the cross-compilation
  issue that produced `jolt build --target`.
- `jolt/tools/cross-compile/README.md`: target packs, `make-pack.sh`, and the
  cross builds that were verified before this one.
- `jolt/host/chez/hasheq.ss`: `define-width-op` and its comment about cross
  builds to narrower targets.
- jolt's `CHANGELOG.md`, the #801 entry: the 32-bit hash engine fix, and the
  note that other host code still uses checked fx ops on int-width values.
- `jolt/host/chez/build.ss`: the generated `compile.ss` and `main.c`, and
  where `$JOLT_TARGET_CC`, `link-libs` and the xpatch come in.

## Prior art in our own trees

- `~/dev/jlt-commons/raylib-ios`: jolt on `tpb64l` with raylib and SDL2
  statically linked, and the "who owns thread 0" design notes. The closest
  relative of this project.
- [jasalt/jolt-wasm-raylib-experiment](https://github.com/jasalt/jolt-wasm-raylib-experiment):
  jolt on `tpb32l` under Emscripten. It found that jolt needs the threaded
  machine type (the non-threaded `pb` aborts at `make-mutex`), and it's where
  the #801 hash fix came from.

## libogc source we read for the console bug

- [`libogc/exi.c`](https://github.com/devkitPro/libogc/blob/v3.2.1/libogc/exi.c),
  `WriteUARTN`: rewrites `\n` to `\r` in the caller's buffer.
- [`libogc/console.c`](https://github.com/devkitPro/libogc/blob/master/libogc/console.c),
  `consolePrintChar`: `\n` starts a new row, `\r` only returns to column 1.
