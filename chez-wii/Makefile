# Chez Scheme kernel (tpb32b: threaded portable bytecode, 32-bit big-endian)
# cross-compiled for the Wii with devkitPPC, plus a tiny boot-and-eval main.
# Inputs come from .work/ChezScheme after `make bootquick XM=tpb32b` there
# and applying patches/chez-*.patch (see docs/guide/chez-scheme-on-the-wii.md).
# Usage: scripts/dkp make -C chez-wii

include $(DEVKITPPC)/wii_rules

CHEZ    := ../.work/ChezScheme
BOOT    := $(CHEZ)/boot/tpb32b
OUT     := build
PORTS   := $(DEVKITPRO)/portlibs

KSRCS   := statics segment alloc symbol intern gcwrapper gc-011 gc-par gc-ocd \
           gc-oce number schsig io new-io print fasl vfasl stats foreign prim \
           prim5 flushcache schlib thread expeditor scheme compress-io random \
           ffi self-exe pb
KOBJS   := $(addprefix $(OUT)/k/,$(addsuffix .o,$(KSRCS)))

CFLAGS  := -O2 $(MACHDEP) -DPORTABLE_BYTECODE -D_REENTRANT \
           -Wno-unused -Wno-implicit-fallthrough \
           -I. -I$(BOOT) -I$(CHEZ)/c \
           -I$(PORTS)/ppc/include -I$(LIBOGC_INC)

LIBS    := -L$(PORTS)/ppc/lib -L$(LIBOGC_LIB) -llz4 -lz -lfat -lwiiuse -lbte -lpthread -logc -lm

RUNTIME := $(OUT)/wii_runtime.o $(OUT)/posix_stubs.o $(OUT)/ogc_tls.o

all: $(OUT)/chez.dol runtime

# Objects a `jolt build --target tpb32b` executable links in (see
# scripts/make-pack): console + tee + exit wait, POSIX stubs, TLS hooks.
runtime: $(OUT)/libkernel.a $(RUNTIME)

$(OUT)/%.o: %.c | $(OUT)/k
	$(CC) $(CFLAGS) -c $< -o $@

$(OUT)/k:
	mkdir -p $(OUT)/k

$(OUT)/k/%.o: $(CHEZ)/c/%.c | $(OUT)/k
	$(CC) $(CFLAGS) -c $< -o $@

$(OUT)/libkernel.a: $(KOBJS)
	$(AR) rcs $@ $^

$(OUT)/boot_data.o: boot_data.S $(BOOT)/petite.boot $(BOOT)/scheme.boot | $(OUT)/k
	$(CC) $(MACHDEP) -DPETITE_BOOT=\"$(BOOT)/petite.boot\" -DSCHEME_BOOT=\"$(BOOT)/scheme.boot\" -c $< -o $@

$(OUT)/chez.elf: main.c posix_stubs.c ogc_tls.c $(OUT)/boot_data.o $(OUT)/libkernel.a
	$(CC) $(CFLAGS) main.c posix_stubs.c ogc_tls.c $(OUT)/boot_data.o $(OUT)/libkernel.a $(MACHDEP) \
	  -Wl,-Map,$(OUT)/chez.map $(LIBS) -o $@

$(OUT)/chez.dol: $(OUT)/chez.elf
	elf2dol $< $@

clean:
	rm -rf $(OUT)

.PHONY: all clean runtime
