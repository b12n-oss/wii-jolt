/* Hand-written stand-in for the config.h Chez's configure generates.
   Boot files are embedded in the .dol and registered with
   Sregister_boot_file_bytes, so the heap path is never searched. */
#define DEFAULT_HEAP_PATH "/apps/chez/%m"
#define DISABLE_CURSES
#define DISABLE_X11
