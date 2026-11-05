# The C shim a jolt program links to draw with raylib and read Wiimotes.
# Run inside the devkitPPC image: scripts/dkp make -C jolt-gfx
include $(DEVKITPPC)/wii_rules

PORTS  := $(DEVKITPRO)/portlibs
# ../.work/pack-tpb32b/scheme.h: the Chez C API, for the heap report (bb pack first).
CFLAGS := -O2 -Wall $(MACHDEP) -I../vendor/raylib -I../.work/pack-tpb32b -I../examples/common \
          -I$(PORTS)/wii/include -I$(PORTS)/ppc/include -I$(LIBOGC_INC)
CFLAGS += $(EXTRA_CFLAGS)

all: build/jolt_gfx.o build/settings.o

build/settings.o: ../examples/common/settings.c ../examples/common/settings.h
	mkdir -p build
	$(CC) $(CFLAGS) -c $< -o $@

build/jolt_gfx_stub.o: jolt_gfx.c jolt_gfx.h
	mkdir -p build
	$(CC) $(CFLAGS) -DJG_STUB_DRAW -c $< -o $@

build/jolt_gfx.o: jolt_gfx.c jolt_gfx.h
	mkdir -p build
	$(CC) $(CFLAGS) -c $< -o $@

clean:
	rm -rf build

.PHONY: all clean
