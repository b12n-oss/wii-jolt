# Build vendor/raylib as libraylib.a for the Wii.
# raylib's SDL2 platform + its OpenGL 1.1 renderer, on top of devkitPro's
# wii-sdl2 and wii-opengx (OpenGL 1.x implemented over the Wii's GX).
# Run inside the devkitPPC image: scripts/dkp make -C raylib-wii

include $(DEVKITPPC)/wii_rules

RAYLIB  := ../vendor/raylib
OUT     := build
PORTS   := $(DEVKITPRO)/portlibs

MODULES := rcore rshapes rtextures rtext rmodels
OBJS    := $(addprefix $(OUT)/,$(addsuffix .o,$(MODULES))) $(OUT)/wii_shim.o

CFLAGS  := -O2 -Wall -Wno-unused-function -Wno-missing-braces \
           $(MACHDEP) \
           -DPLATFORM_DESKTOP_SDL -DGRAPHICS_API_OPENGL_11 \
           -DUSING_SDL2_PACKAGE -DGEKKO_WII \
           -I$(RAYLIB) \
           -I$(PORTS)/wii/include -I$(PORTS)/wii/include/SDL2 \
           -I$(PORTS)/ppc/include -I$(LIBOGC_INC)

all: $(OUT)/libraylib.a

$(OUT):
	mkdir -p $(OUT)

$(OUT)/%.o: $(RAYLIB)/%.c | $(OUT)
	$(CC) $(CFLAGS) -c $< -o $@

$(OUT)/wii_shim.o: wii_shim.c | $(OUT)
	$(CC) $(CFLAGS) -c $< -o $@

$(OUT)/libraylib.a: $(OBJS)
	$(AR) rcs $@ $^

clean:
	rm -rf $(OUT)

.PHONY: all clean
