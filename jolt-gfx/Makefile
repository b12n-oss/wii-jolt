# The C shim a jolt program links to draw with raylib and read Wiimotes.
# Run inside the devkitPPC image: scripts/dkp make -C jolt-gfx
include $(DEVKITPPC)/wii_rules

PORTS  := $(DEVKITPRO)/portlibs
# ../.work/pack-tpb32b/scheme.h: the Chez C API, for the heap report (jolt pack first).
CFLAGS := -O2 -Wall $(MACHDEP) -I../vendor/raylib -I../.work/pack-tpb32b -I../examples/common \
          -I$(PORTS)/wii/include -I$(PORTS)/ppc/include -I$(LIBOGC_INC)
CFLAGS += $(EXTRA_CFLAGS)

all: build/jolt_gfx.o build/settings.o build/snd.o build/snd_wii.o build/jolt_snd.o

build/snd.o: snd.c snd.h
	mkdir -p build
	$(CC) $(CFLAGS) -c $< -o $@

build/snd_wii.o: snd_wii.c snd_wii.h snd.h
	mkdir -p build
	$(CC) $(CFLAGS) -c $< -o $@

build/jolt_snd.o: jolt_snd.c jolt_snd.h snd.h snd_wii.h jolt_gfx.h mona_music.h
	mkdir -p build
	$(CC) $(CFLAGS) -c $< -o $@

build/settings.o: ../examples/common/settings.c ../examples/common/settings.h
	mkdir -p build
	$(CC) $(CFLAGS) -c $< -o $@

build/jolt_gfx_stub.o: jolt_gfx.c jolt_gfx.h
	mkdir -p build
	$(CC) $(CFLAGS) -DJG_STUB_DRAW -c $< -o $@

# Always recompiled: the build id (-DJG_BUILD_ID, from jolt build:jolt-gfx) is
# in CFLAGS, not in a source file, and the compile takes a second.
build/jolt_gfx.o: jolt_gfx.c jolt_gfx.h FORCE
	mkdir -p build
	$(CC) $(CFLAGS) -c $< -o $@

clean:
	rm -rf build

FORCE:

.PHONY: all clean FORCE
