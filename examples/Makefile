# Build one example as a Wii .dol plus a Homebrew Channel app folder.
# Usage (inside the image): scripts/dkp make -C examples APP=hello

include $(DEVKITPPC)/wii_rules

APP     ?= hello
OUT     := build/$(APP)
PORTS   := $(DEVKITPRO)/portlibs
RAYLIB  := ../vendor/raylib
RLWII   := ../raylib-wii/build

CFLAGS  := -O2 -Wall $(MACHDEP) -I$(RAYLIB) \
           -I$(PORTS)/wii/include -I$(PORTS)/ppc/include -I$(LIBOGC_INC)
LDFLAGS := $(MACHDEP) -Wl,-Map,$(OUT)/$(APP).map \
           -Wl,--wrap=KEYBOARD_Init -Wl,--wrap=KEYBOARD_GetEvent
LIBS    := $(RLWII)/wii_shim.o -L$(RLWII) -lraylib \
           -L$(PORTS)/wii/lib -L$(PORTS)/ppc/lib -L$(LIBOGC_LIB) \
           -lSDL2main -lSDL2 -lpthread -laesnd -lopengx -lfat -lwiiuse -lbte \
           -lwiikeyboard -logc -lstdc++ -lm

SRCS    := $(wildcard $(APP)/*.c)

all: $(OUT)/$(APP).dol

$(OUT):
	mkdir -p $(OUT)

$(OUT)/$(APP).elf: $(SRCS) $(RLWII)/libraylib.a | $(OUT)
	$(CC) $(CFLAGS) $(SRCS) $(LDFLAGS) $(LIBS) -o $@

$(OUT)/$(APP).dol: $(OUT)/$(APP).elf
	elf2dol $< $@
	mkdir -p $(OUT)/sd/apps/$(APP)
	cp $@ $(OUT)/sd/apps/$(APP)/boot.dol
	[ -f $(APP)/meta.xml ] && cp $(APP)/meta.xml $(OUT)/sd/apps/$(APP)/ || true

clean:
	rm -rf build

.PHONY: all clean
