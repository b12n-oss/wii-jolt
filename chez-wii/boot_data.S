/* Embed the tpb32b boot files in the .dol. */
    .section .rodata
    .balign 32
    .global petite_boot, petite_boot_end, scheme_boot, scheme_boot_end
petite_boot:
    .incbin PETITE_BOOT
petite_boot_end:
    .balign 32
scheme_boot:
    .incbin SCHEME_BOOT
scheme_boot_end:
