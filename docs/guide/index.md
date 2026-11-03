# Guide

wii-jolt puts three things on a Nintendo Wii: raylib, Chez Scheme, and Clojure
compiled by jolt. Each one builds on the one before it, so the pages read best
in order, but each also stands on its own if you only care about one layer.

Three games for the kids, each for up to four players, each using the Wiimote
a different way:

- [Balloon Pop](balloon-pop.md): point at the TV and press A to pop balloons.
- [Star Catcher](star-catcher.md): hold the remote sideways and tip it to
  catch falling stars.
- [Shake Race](shake-race.md): shake the remote to make your runner go.
- [Balloon Pop in Clojure](balloon-pop-in-clojure.md): the same game written
  in Clojure on jolt, and measured against the C version.
- [Wiimote input](wiimote-input.md): the input module the games share, what
  Dolphin reports for tilt and shake, and how `bb verify` plays each game with
  a scripted remote.

Then the layers underneath:

- [Getting started](getting-started.md): install the toolchain, build
  everything, and test it in the Dolphin emulator.
- [Running on your Wii](running-on-your-wii.md): getting a build onto the
  console through the Homebrew Channel.
- [raylib on the Wii](raylib-on-the-wii.md): how stock raylib 6.0 runs through
  SDL2 and OpenGX, and the libogc keyboard crash we had to guard against.
- [Chez Scheme on the Wii](chez-scheme-on-the-wii.md): the big-endian 32-bit
  portable-bytecode target, the kernel patch, and the thread-local storage hooks
  libogc leaves out.
- [Jolt on the Wii](jolt-on-the-wii.md): cross-compiling a Clojure program with
  `jolt build --target tpb32b`, and the two 32-bit assumptions in jolt it ran
  into.
- [Resources and links](resources.md): every source we used, with notes on
  what each one told us.

## The short version

The Wii is a 729 MHz PowerPC 750CL with 88 MB of RAM, big-endian and 32-bit.
devkitPro's devkitPPC compiles C for it, and libogc stands in for an operating
system. On top of that:

1. **raylib** needed no source changes. devkitPro ships SDL2 and an OpenGL 1.x
   layer over the Wii's GPU, and raylib already has an SDL backend and an
   OpenGL 1.1 renderer.
2. **Chez Scheme** has a portable-bytecode machine type for exactly this kind of
   CPU, `tpb32b`, so no PowerPC code generator is needed. Its C kernel compiles
   with a short patch, and booting it needed libogc's missing thread-local
   storage hooks.
3. **jolt** cross-compiles through Chez, so once Chez ran, a Clojure program
   needed a target pack, a link step in Docker, and two fixes for places where
   jolt assumed the machine compiling the code had the same fixnum width as the
   machine running it.

Everything so far runs in Dolphin with its MMU emulation on, which makes bad
memory accesses crash the way they would on hardware. The physical console is
next, and `ROADMAP.md` at the repo root lists what comes after.
