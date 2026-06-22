# SPDX-License-Identifier: GPL-2.0-only

subdir-ccflags-y += -I$(srctree)/$(src)
subdir-ccflags-y += -I$(objtree)/$(obj)
subdir-ccflags-y += -I$(srctree)/$(src)/vendor/include
subdir-ccflags-y += -I$(objtree)/$(obj)/vendor/include
subdir-ccflags-y += -Wno-deprecated-declarations

obj-$(CONFIG_CRYPTO_ZSTDP) += zstdp.o

zstdp-y := zstdp_wrapper.o zstdp_vendor_prefixed.o

zstdp_vendor_relobjs := \
	vendor/lib/zstd/common/debug.o \
	vendor/lib/zstd/common/entropy_common.o \
	vendor/lib/zstd/common/error_private.o \
	vendor/lib/zstd/common/fse_decompress.o \
	vendor/lib/zstd/common/zstd_common.o \
	vendor/lib/zstd/compress/fse_compress.o \
	vendor/lib/zstd/compress/hist.o \
	vendor/lib/zstd/compress/huf_compress.o \
	vendor/lib/zstd/compress/zstd_compress.o \
	vendor/lib/zstd/compress/zstd_compress_literals.o \
	vendor/lib/zstd/compress/zstd_compress_sequences.o \
	vendor/lib/zstd/compress/zstd_compress_superblock.o \
	vendor/lib/zstd/compress/zstd_double_fast.o \
	vendor/lib/zstd/compress/zstd_fast.o \
	vendor/lib/zstd/compress/zstd_lazy.o \
	vendor/lib/zstd/compress/zstd_ldm.o \
	vendor/lib/zstd/compress/zstd_opt.o \
	vendor/lib/zstd/compress/zstd_preSplit.o \
	vendor/lib/zstd/decompress/huf_decompress.o \
	vendor/lib/zstd/decompress/zstd_ddict.o \
	vendor/lib/zstd/decompress/zstd_decompress.o \
	vendor/lib/zstd/decompress/zstd_decompress_block.o

zstdp_vendor_objs := $(addprefix $(obj)/,$(zstdp_vendor_relobjs))

targets += $(zstdp_vendor_relobjs) zstdp_vendor_unprefixed.o zstdp_vendor_rename.syms zstdp_vendor_prefixed.o
clean-files += zstdp_vendor_rename.syms zstdp_vendor_prefixed.o

quiet_cmd_zstdp_syms = GEN     $@
      cmd_zstdp_syms = $(NM) -g --defined-only $< | awk 'NF == 3 { print $$3 " abk_zstdp_" $$3 }' > $@

quiet_cmd_zstdp_ld = LD      $@
      cmd_zstdp_ld = $(LD) $(ld_flags) -r -o $@ $(filter %.o,$^)

$(obj)/zstdp_vendor_unprefixed.o: $(zstdp_vendor_objs) FORCE
	$(call if_changed,zstdp_ld)

$(obj)/zstdp_vendor_rename.syms: $(obj)/zstdp_vendor_unprefixed.o FORCE
	$(call if_changed,zstdp_syms)

quiet_cmd_zstdp_prefix = OBJCOPY $@
      cmd_zstdp_prefix = cp $< $@ && $(OBJCOPY) --redefine-syms=$(word 2,$^) $@

$(obj)/zstdp_vendor_prefixed.o: $(obj)/zstdp_vendor_unprefixed.o $(obj)/zstdp_vendor_rename.syms FORCE
	$(call if_changed,zstdp_prefix)
