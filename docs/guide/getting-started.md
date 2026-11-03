# Getting started

Everything builds on a Mac. The Wii compiler runs inside Docker, so the only
things installed on the Mac itself are Docker, the Dolphin emulator, babashka,
and jolt.

## What you need

| Tool | Why | Install |
|---|---|---|
| Docker | runs devkitPro's devkitPPC toolchain | Docker Desktop |
| `devkitpro/devkitppc` image | the Wii compiler, libogc, SDL2, OpenGX | `docker pull devkitpro/devkitppc:latest` |
| Dolphin | the Wii emulator we test in | `brew install --cask dolphin` |
| babashka | the task runner (`bb`) | `brew install borkdude/brew/babashka` |
| jolt | compiles the Clojure app | see [jolt](https://github.com/jolt-lang/jolt) |
| a Chez Scheme checkout | source for the Wii build of Chez | `~/dev/ChezScheme` with submodules, or set `CHEZ_SRC` |

`bb doctor` checks each of these and says what the missing ones block:

```
$ bb doctor
toolchain (blocks every build):
  ok    docker on PATH
  ok    docker daemon running
  ok    devkitpro/devkitppc:latest pulled
testing (blocks run:* and verify):
  ok    Dolphin emulator
Chez Scheme (blocks build:chez, pack, build:jolt-hello):
  ok    ~/dev/ChezScheme checkout (source for chez:setup)
  ok    tpb32b boot files built
jolt (blocks pack users: build:jolt-hello):
  ok    jolt on PATH
  ok    jolt target pack assembled
docs (blocks site:*):
  ok    docs-engine checkout

all good
```

## Building

The raylib app needs nothing beyond Docker:

```sh
bb build:hello      # builds raylib for the Wii, then examples/hello
```

Chez needs a one-time setup. `bb chez:setup` clones your local Chez checkout
into `.work/ChezScheme` at tag `v10.4.1` (the version jolt is built on),
applies `patches/chez-wii.patch`, builds a Mac Chez, and builds the `tpb32b`
boot files. That takes about five minutes. After it:

```sh
bb build:chez       # the Chez kernel for the Wii and the chez test app
bb pack             # the jolt target pack in .work/pack-tpb32b
bb build:jolt-hello # jolt build --target tpb32b, then elf2dol
```

`bb build` does all of it in order.

Each build leaves a `.dol` (the Wii's executable format) and an
`sd/apps/<name>/` folder laid out for the Homebrew Channel. `bb sd` gathers every
app into one `build/sd/apps` tree.

### What `scripts/dkp` does

Every Wii compile goes through `scripts/dkp`, which runs a command inside the
devkitPPC image with the repo mounted at `/work`. It also sets `PATH`
explicitly, since the image only puts devkitPPC on `PATH` for a login shell, not
for `docker run image cmd`.

`scripts/wii-cc` is the same idea shaped as a C compiler for jolt: it mounts the
repo at its own absolute path, so the paths jolt writes into its link command
resolve unchanged inside the container.

## Testing in Dolphin

```sh
bb run:hello        # screenshot in .work/shots/hello.png
bb run:chez         # prints the Scheme results
bb run:jolt-hello   # prints the Clojure program's output
bb verify           # builds everything, runs all three, checks the output
```

These go through `scripts/dolphin-shot`, which boots a `.dol` with a separate
Dolphin user directory (`.dolphin-user/`, so your own Dolphin settings stay
untouched), takes a screenshot, and pulls the program's output out of Dolphin's
log.

Three Dolphin settings matter, and the script sets all of them:

- **MMU emulation is on.** By default Dolphin notices a bad memory read, shows a
  "would have crashed on real hardware" dialog, and carries on. With MMU
  emulation you get libogc's red crash screen instead, the same as on the
  console. The first raylib run only showed its bug this way. Turn it off with
  `DOLPHIN_MMU=0` if you ever need to.
- **Program output goes to the log.** The apps send stdout through libogc's
  debug UART, which Dolphin records as `OSREPORT` lines. On a real Wii the same
  bytes go to a USB Gecko adapter if one is plugged in.
- **The text-console apps turn off "XFB to texture".** The chez and jolt apps
  print with libogc's console, which writes pixels straight into the
  framebuffer from the CPU. Dolphin only shows those writes with
  `GFX.Hacks.XFBToTextureEnable=False`. Without it the screen stays black even
  though the program is fine.

### When something crashes

The red libogc screen lists the program counter and a stack of return
addresses. Turn them into function names with the matching `.elf`:

```sh
scripts/dkp powerpc-eabi-addr2line -f -p -e examples/build/hello/hello.elf 0x80098ffc 0x80036cd0
```

That's how we found the keyboard crash described in
[raylib on the Wii](raylib-on-the-wii.md).
