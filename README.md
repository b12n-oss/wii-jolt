# wii-jolt

Programs for the Nintendo Wii we have at home, built from the tools we already
use: stock [raylib](https://www.raylib.com/) 6.0, [Chez Scheme](https://github.com/cisco/ChezScheme)
10.4.1 and Clojure through [Jolt](https://github.com/jolt-lang/jolt). Everything
compiles on a Mac with devkitPro's toolchain in Docker, and we test it in the
Dolphin emulator with its strict memory checking turned on.

<p>
  <img src="docs/images/hello-dolphin.png" width="260" alt="raylib bouncing ball on the Wii">
  <img src="docs/images/chez-dolphin.png" width="260" alt="Chez Scheme evaluating on the Wii">
  <img src="docs/images/jolt-hello-dolphin.png" width="260" alt="Clojure via jolt on the Wii">
</p>

*Left to right: raylib's SDL backend drawing through OpenGX, Chez Scheme's
portable bytecode running on the Wii's PowerPC, and a Clojure program compiled by
jolt whose output matches the Mac build line for line. All three are Dolphin
screenshots.*

**On the real Wii:** the C games and the jolt builds all run from the Homebrew
Channel on our family Wii (2026-11-06). Clojure's hello program printed its
twelve lines on the TV, and Balloon Pop (Clojure) and Super Mona were played
with a real Wiimote. The first console run had stopped every jolt build at
boot with an alignment exception, because the Wii's CPU traps a misaligned
double load that Dolphin lets through; the kernel fix went on the next card
and the console counted the same four unaligned reads Dolphin does. [Running on your
Wii](docs/guide/running-on-your-wii.md) has the details.

## Games

<p>
  <img src="docs/images/balloons-demo.png" width="260" alt="Balloon Pop robot demo">
  <img src="docs/images/starcatcher-wiimote.png" width="260" alt="Star Catcher, a basket steered by tilting">
  <img src="docs/images/shakerace-demo.png" width="260" alt="Shake Race robot demo">
</p>

Three games for the kids, each for up to four players and each using the
Wiimote a different way, plus [Super Mona in jolt](docs/guide/super-mona-in-jolt.md),
a Clojure platformer played with the remote held sideways:

- **[Balloon Pop](docs/guide/balloon-pop.md)**: point at the TV and press A to
  pop balloons. Gold ones are worth 5.
- **[Star Catcher](docs/guide/star-catcher.md)**: hold the remote sideways and
  tip it like a steering wheel to catch falling stars, and steer clear of the
  storm clouds.
- **[Shake Race](docs/guide/shake-race.md)**: shake the remote as hard as you
  can to make your runner go. Wrist straps on.

Each one has a robot demo, rumble, and rules that are tested on the Mac. The
two numbers we could only guess at in the emulator can be fixed from the
couch: **−** flips Star Catcher's steering, and **+**/**−** set Shake Race's
shake power. Both are saved on the SD card.
`bb play:<game>` opens any of them in Dolphin to try with the mouse and
keyboard, and `bb verify` plays each one with a scripted remote.

## What's here

| App | Size | What it shows |
|---|---|---|
| `examples/balloons` | 1.3 MB | Balloon Pop: point and press A |
| `examples/starcatcher` | 1.3 MB | Star Catcher: tilt to steer |
| `examples/shakerace` | 1.3 MB | Shake Race: shake to run |
| `examples/hello` | 1.3 MB | raylib 6.0, unmodified, at 60 fps over SDL2 and OpenGX |
| `chez-wii` | 4.5 MB | Chez Scheme booting from boot files baked into the program, with bignums, threads, runtime `eval` and a full collect |
| `jolt-balloons` | 14.5 MB | Balloon Pop in Clojure on jolt: plays, but misses 60 fps (see the comparison page) |
| `jolt-mona` | 11.9 MB | Super Mona in Clojure on jolt: a 32-stage platformer, remote held sideways (see its page) |
| `jolt-hello` | 10.9 MB | Clojure: maps, sets, lazy seqs, atoms, bignums and Java-compatible hashes |

The interesting part is the bottom row. Chez has a big-endian 32-bit
portable-bytecode target (`tpb32b`) that had never been pointed at a real
machine, and jolt had never produced code for a 32-bit or big-endian one. Getting
there took a short patch to the Chez kernel, two small shims for gaps in libogc,
and a fix to how `jolt build` cross-compiles for 32-bit targets. The
[Jolt on the Wii](docs/guide/jolt-on-the-wii.md) page walks through each one.

## How it fits together

```mermaid
flowchart LR
  subgraph mac["On the Mac"]
    games["Games and hello<br/>(C + raylib 6.0)"]
    clj["Clojure program"]
    jolt["jolt build<br/>--target tpb32b"]
    clj --> jolt
  end
  subgraph wii["On the Wii (PowerPC 750CL, big-endian, 32-bit)"]
    raylib["raylib"] --> sdl["SDL2 (wii-sdl2)"] --> gx["OpenGX: GL 1.x on the GX GPU"]
    chez["Chez Scheme kernel<br/>running tpb32b bytecode"]
    libogc["libogc: video, Wiimotes (WPAD), SD/USB, threads"]
    sdl --> libogc
    gx --> libogc
    chez --> libogc
  end
  games --> raylib
  jolt --> chez
```

The games are plain C on raylib. Clojure takes the other road: jolt compiles
it to Chez Scheme's portable bytecode, and a small Chez kernel runs that
bytecode on the Wii. Everything bottoms out in libogc, devkitPro's library
that stands in for an operating system.

## Quick start

```sh
bb doctor        # Docker, the devkitPPC image, Dolphin, jolt, Chez: what's missing
bb chez:setup    # one time: Chez 10.4.1 + the Wii patch, about 5 minutes
bb test          # every game's rules, compiled and tested on the Mac
bb verify        # build everything, boot each app in Dolphin, check the output
bb sd            # gather everything into build/sd/apps for an SD card
```

`bb info` prints every task, grouped. `jolt info` works too, since jolt reads
the same `bb.edn`.

Copy `build/sd/apps` to the root of an SD card or USB drive and the apps show up
in the Homebrew Channel. [Running on your Wii](docs/guide/running-on-your-wii.md)
covers the console side, including what to check before trying it on the family
Wii.

## Documentation

- [Balloon Pop in Clojure](docs/guide/balloon-pop-in-clojure.md): the same
  game on jolt, measured against the C version
- [Super Mona in jolt](docs/guide/super-mona-in-jolt.md): a badge platformer ported to Clojure on the Wii, with its frame cost
- [Balloon Pop](docs/guide/balloon-pop.md), [Star Catcher](docs/guide/star-catcher.md)
  and [Shake Race](docs/guide/shake-race.md): how to play, and how each is built
- [Wiimote input](docs/guide/wiimote-input.md): the shared input module, tilt
  and shake measurements, and testing with a scripted remote
- [Getting started](docs/guide/getting-started.md): the toolchain, the build,
  and testing in Dolphin
- [Running on your Wii](docs/guide/running-on-your-wii.md): the Homebrew
  Channel, SD card layout, `wiiload`
- [raylib on the Wii](docs/guide/raylib-on-the-wii.md): the SDL2 + OpenGX route
  and the keyboard crash it hit
- [Chez Scheme on the Wii](docs/guide/chez-scheme-on-the-wii.md): the kernel
  patch, thread-local storage, the POSIX stubs
- [Jolt on the Wii](docs/guide/jolt-on-the-wii.md): the target pack, the 32-bit
  fixes, the console tee
- [Resources and links](docs/guide/resources.md): everything we read, with
  links
- [Roadmap](ROADMAP.md): what's next, starting with real hardware

`bb site:serve` builds the docs into a local site through
[docs-engine](https://github.com/jlt-commons/docs-engine) and serves it on
localhost.

## Layout

```
raylib-wii/     builds vendor/raylib as libraylib.a for the Wii, plus wii_shim.c
examples/       raylib apps: the games (balloons, starcatcher, shakerace, each
                with host tests), hello, and common/ (Wiimote input, UI bits)
chez-wii/       Chez kernel build, the chez test app, and the runtime objects
                that jolt-built programs link (console, TLS hooks, POSIX stubs)
jolt-hello/     a Clojure program, with its expected output
jolt-balloons/  Balloon Pop in Clojure, its tests and MEASUREMENTS.md
jolt-mona/      Super Mona in Clojure, its tests and MEASUREMENTS.md
jolt-gfx/       the C shim and Clojure wrappers that give jolt raylib and Wiimotes
patches/        chez-wii.patch, applied to Chez v10.4.1 by scripts/setup-chez
scripts/        dkp (run in the devkitPPC image), wii-cc, dolphin-shot,
                dolphin-auto-wiimote, setup-chez, make-pack, jolt-wii-build
vendor/raylib/  raylib 6.0 source, unmodified
```

## License

EPL-2.0, see [LICENSE](LICENSE). raylib is under its own zlib/libpng license,
and the full attribution list is in [NOTICE](NOTICE).
