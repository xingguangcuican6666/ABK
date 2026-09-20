#!/usr/bin/env bash
set -euo pipefail

die() {
  echo "::error::$*" >&2
  exit 1
}

log() {
  echo "[zstdp] $*"
}

require_file() {
  local path="$1"
  [ -f "$path" ] || die "文件不存在: $path"
}

require_dir() {
  local path="$1"
  [ -d "$path" ] || die "目录不存在: $path"
}

copy_tree() {
  local src="$1"
  local dst="$2"

  require_dir "$src"
  mkdir -p "$dst"
  cp -a "$src"/. "$dst"/
}

copy_file() {
  local src="$1"
  local dst="$2"

  require_file "$src"
  mkdir -p "$(dirname "$dst")"
  cp -a "$src" "$dst"
}

patch_vendor_compat() {
  local common_root="$1"
  local vendor_root="$2"
  local mem_h="$vendor_root/lib/zstd/common/mem.h"
  local error_private_h="$vendor_root/lib/zstd/common/error_private.h"
  local vendor_zstd_h="$vendor_root/include/linux/zstd.h"
  local vendor_zstd_lib_h="$vendor_root/include/linux/zstd_lib.h"
  local vendor_zstd_errors_h="$vendor_root/include/linux/zstd_errors.h"

  require_file "$mem_h"
  require_file "$error_private_h"
  require_file "$vendor_zstd_h"
  require_file "$vendor_zstd_lib_h"
  require_file "$vendor_zstd_errors_h"

  if [ ! -f "$common_root/include/linux/unaligned.h" ]; then
    python3 - "$mem_h" <<'PY'
import pathlib
import sys

mem_h = pathlib.Path(sys.argv[1])
text = mem_h.read_text()
old = '#include <linux/unaligned.h>  /* get_unaligned, put_unaligned* */\n'
new = '#include <asm-generic/unaligned.h>  /* get_unaligned, put_unaligned* */\n'
if old in text and new not in text:
    text = text.replace(old, new, 1)
    mem_h.write_text(text)
PY
  fi

  python3 - "$error_private_h" <<'PY'
import pathlib
import sys

error_private_h = pathlib.Path(sys.argv[1])
text = error_private_h.read_text()
old = '#include <linux/zstd_errors.h>  /* enum list */\n'
new = '#include "vendor/include/linux/zstd_errors.h"  /* enum list */\n'
if old in text and new not in text:
    text = text.replace(old, new, 1)
    error_private_h.write_text(text)
PY

  python3 - "$vendor_zstd_h" "$vendor_zstd_lib_h" "$vendor_zstd_errors_h" <<'PY'
import pathlib
import sys

vendor_zstd_h = pathlib.Path(sys.argv[1])
vendor_zstd_lib_h = pathlib.Path(sys.argv[2])
vendor_zstd_errors_h = pathlib.Path(sys.argv[3])

def rewrite_once(path: pathlib.Path, old: str, new: str) -> None:
    text = path.read_text()
    if old in text and new not in text:
        text = text.replace(old, new, 1)
        path.write_text(text)

rewrite_once(
    vendor_zstd_h,
    '#include <linux/zstd_errors.h>\n',
    '#include "zstd_errors.h"\n',
)
rewrite_once(
    vendor_zstd_h,
    '#include <linux/zstd_lib.h>\n',
    '#include "zstd_lib.h"\n',
)
rewrite_once(
    vendor_zstd_lib_h,
    '#include <linux/zstd_errors.h> /* list of errors */\n',
    '#include "zstd_errors.h" /* list of errors */\n',
)
rewrite_once(
    vendor_zstd_lib_h,
    '#include <linux/zstd.h>\n',
    '#include "zstd.h"\n',
)
rewrite_once(
    vendor_zstd_errors_h,
    '#include <linux/types.h>   /* size_t */\n',
    '#include <linux/types.h>   /* size_t */\n',
)
PY

  python3 - "$vendor_root/lib/zstd" <<'PY'
import pathlib
import sys

vendor_lib = pathlib.Path(sys.argv[1])

for path in vendor_lib.rglob('*'):
    if not path.is_file():
        continue
    if path.suffix not in {'.c', '.h', '.S'}:
        continue

    text = path.read_text()
    new_text = text.replace(
        '#include <linux/zstd.h>',
        '#include "vendor/include/linux/zstd.h"',
    )
    if new_text != text:
        path.write_text(new_text)
PY
}

vendor_cache_dir() {
  local base tag

  base="${RUNNER_TEMP:-$ZZH_PATCHES/.cache}"
  tag="${ABK_ZSTDP_UPSTREAM_TAG:-v6.15}"
  printf '%s/abk-zstdp-%s\n' "$base" "$tag"
}

fetch_vendor_tree() {
  local repo tag cache_dir tmp_dir attempt

  repo="${ABK_ZSTDP_UPSTREAM_REPO:-https://github.com/torvalds/linux}"
  tag="${ABK_ZSTDP_UPSTREAM_TAG:-v6.15}"
  cache_dir="$(vendor_cache_dir)"

  if [ -n "${ABK_ZSTDP_VENDOR_DIR:-}" ]; then
    require_dir "$ABK_ZSTDP_VENDOR_DIR/lib/zstd"
    require_file "$ABK_ZSTDP_VENDOR_DIR/include/linux/zstd.h"
    printf '%s\n' "$ABK_ZSTDP_VENDOR_DIR"
    return 0
  fi

  if [ -d "$cache_dir/lib/zstd" ] && [ -f "$cache_dir/include/linux/zstd.h" ]; then
    printf '%s\n' "$cache_dir"
    return 0
  fi

  mkdir -p "$(dirname "$cache_dir")"
  tmp_dir="${cache_dir}.tmp.$$"
  rm -rf "$tmp_dir"

  for attempt in 1 2 3; do
    rm -rf "$tmp_dir"
    git init "$tmp_dir" >/dev/null
    git -C "$tmp_dir" remote add origin "$repo"
    git -C "$tmp_dir" sparse-checkout init --cone >/dev/null
    git -C "$tmp_dir" sparse-checkout set \
      lib/zstd \
      include/linux
    if git -C "$tmp_dir" -c protocol.version=2 fetch --depth 1 --filter=blob:none origin "refs/tags/$tag:refs/tags/$tag" \
      && git -C "$tmp_dir" checkout --detach "tags/$tag" >/dev/null; then
      rm -rf "$cache_dir"
      mv "$tmp_dir" "$cache_dir"
      printf '%s\n' "$cache_dir"
      return 0
    fi
    log "fetch $tag 失败，第 ${attempt} 次重试"
    sleep "$attempt"
  done

  rm -rf "$tmp_dir"
  die "无法获取 zstdp vendor tree: $repo@$tag"
}

patch_crypto_files() {
  local common_root="$1"
  local crypto_dir="$common_root/crypto"

  require_file "$crypto_dir/Kconfig"
  require_file "$crypto_dir/Makefile"

  python3 - "$crypto_dir/Kconfig" "$crypto_dir/Makefile" <<'PY'
import pathlib
import sys

kconfig_path = pathlib.Path(sys.argv[1])
makefile_path = pathlib.Path(sys.argv[2])

kconfig = kconfig_path.read_text()
source_line = 'source "crypto/abk_zstdp/Kconfig"\n'
if source_line not in kconfig:
    anchor = '\nendmenu\n\nmenu "Random number generation"\n'
    if anchor in kconfig:
        kconfig = kconfig.replace(anchor, '\n' + source_line + anchor, 1)
    else:
        marker = '\nendmenu\n'
        if marker in kconfig:
            kconfig = kconfig.replace(marker, '\n' + source_line + marker, 1)
        else:
            if not kconfig.endswith('\n'):
                kconfig += '\n'
            kconfig += source_line
    kconfig_path.write_text(kconfig)

makefile = makefile_path.read_text()
line = 'obj-$(CONFIG_CRYPTO_ZSTDP) += abk_zstdp/\n'
if line not in makefile:
    if not makefile.endswith('\n'):
        makefile += '\n'
    makefile += line
    makefile_path.write_text(makefile)
PY
}

patch_legacy_zram_files() {
  local common_root="$1"
  local zram_dir="$common_root/drivers/block/zram"

  require_file "$zram_dir/Kconfig"
  require_file "$zram_dir/zcomp.c"

  python3 - "$zram_dir/Kconfig" "$zram_dir/zcomp.c" "$zram_dir/zram_drv.c" <<'PY'
import pathlib
import sys

kconfig_path = pathlib.Path(sys.argv[1])
zcomp_path = pathlib.Path(sys.argv[2])
zram_drv_path = pathlib.Path(sys.argv[3])

kconfig = kconfig_path.read_text()
if 'config ZRAM_DEF_COMP_ZSTD\n' in kconfig:
    depends_old = "depends on CRYPTO_LZO || CRYPTO_ZSTD || CRYPTO_LZ4 || CRYPTO_LZ4HC || CRYPTO_842"
    depends_new = "depends on CRYPTO_LZO || CRYPTO_ZSTD || CRYPTO_ZSTDP || CRYPTO_LZ4 || CRYPTO_LZ4HC || CRYPTO_842"
    if depends_old in kconfig and depends_new not in kconfig:
        kconfig = kconfig.replace(depends_old, depends_new, 1)

    choice_block = (
        'config ZRAM_DEF_COMP_ZSTDP\n'
        '\tbool "zstdp"\n'
        '\tdepends on CRYPTO_ZSTDP\n\n'
    )
    if "config ZRAM_DEF_COMP_ZSTDP\n" not in kconfig:
        anchor = (
            'config ZRAM_DEF_COMP_ZSTD\n'
            '\tbool "zstd"\n'
            '\tdepends on CRYPTO_ZSTD\n\n'
        )
        if anchor not in kconfig:
            raise SystemExit(f"{kconfig_path} 缺少 ZRAM_DEF_COMP_ZSTD 锚点")
        kconfig = kconfig.replace(anchor, anchor + choice_block, 1)

    default_line = '\tdefault "zstdp" if ZRAM_DEF_COMP_ZSTDP\n'
    if default_line not in kconfig:
        anchor = '\tdefault "zstd" if ZRAM_DEF_COMP_ZSTD\n'
        if anchor not in kconfig:
            raise SystemExit(f"{kconfig_path} 缺少 ZRAM_DEF_COMP 默认值锚点")
        kconfig = kconfig.replace(anchor, anchor + default_line, 1)

    kconfig_path.write_text(kconfig)
elif zram_drv_path.exists():
    zram_drv = zram_drv_path.read_text()
    old = 'static const char *default_compressor = "lzo-rle";\n'
    new = 'static const char *default_compressor = "zstdp";\n'
    if old in zram_drv and new not in zram_drv:
        zram_drv = zram_drv.replace(old, new, 1)
        zram_drv_path.write_text(zram_drv)

zcomp = zcomp_path.read_text()
zstdp_block = '#if IS_ENABLED(CONFIG_CRYPTO_ZSTDP)\n\t"zstdp",\n#endif\n'
if zstdp_block not in zcomp:
    anchor = '#if IS_ENABLED(CONFIG_CRYPTO_ZSTD)\n\t"zstd",\n#endif\n'
    if anchor not in zcomp:
        raise SystemExit(f"{zcomp_path} 缺少 CONFIG_CRYPTO_ZSTD backend 锚点")
    zcomp = zcomp.replace(anchor, anchor + zstdp_block, 1)
    zcomp_path.write_text(zcomp)
PY
}

patch_modern_zram_files() {
  local common_root="$1"
  local zram_dir="$common_root/drivers/block/zram"

  require_file "$zram_dir/Kconfig"
  require_file "$zram_dir/Makefile"
  require_file "$zram_dir/zcomp.c"

  python3 - "$zram_dir/Kconfig" "$zram_dir/Makefile" "$zram_dir/zcomp.c" <<'PY'
import pathlib
import sys

kconfig_path = pathlib.Path(sys.argv[1])
makefile_path = pathlib.Path(sys.argv[2])
zcomp_path = pathlib.Path(sys.argv[3])

kconfig = kconfig_path.read_text()
backend_block = (
    'config ZRAM_BACKEND_ZSTDP\n'
    '\tbool "zstdp compression support"\n'
    '\tdepends on ZRAM\n'
    '\tselect CRYPTO_ZSTDP\n\n'
)
if 'config ZRAM_BACKEND_ZSTDP\n' not in kconfig:
    anchor = (
        'config ZRAM_BACKEND_ZSTD\n'
        '\tbool "zstd compression support"\n'
        '\tdepends on ZRAM\n'
        '\tselect ZSTD_COMPRESS\n'
        '\tselect ZSTD_DECOMPRESS\n\n'
    )
    if anchor not in kconfig:
        raise SystemExit(f"{kconfig_path} 缺少 ZRAM_BACKEND_ZSTD 锚点")
    kconfig = kconfig.replace(anchor, anchor + backend_block, 1)

force_old = '!ZRAM_BACKEND_ZSTD && !ZRAM_BACKEND_DEFLATE && \\'
force_new = '!ZRAM_BACKEND_ZSTD && !ZRAM_BACKEND_ZSTDP && !ZRAM_BACKEND_DEFLATE && \\'
if force_old in kconfig and force_new not in kconfig:
    kconfig = kconfig.replace(force_old, force_new, 1)

def_block = (
    'config ZRAM_DEF_COMP_ZSTDP\n'
    '\tbool "zstdp"\n'
    '\tdepends on ZRAM_BACKEND_ZSTDP\n\n'
)
if 'config ZRAM_DEF_COMP_ZSTDP\n' not in kconfig:
    anchor = (
        'config ZRAM_DEF_COMP_ZSTD\n'
        '\tbool "zstd"\n'
        '\tdepends on ZRAM_BACKEND_ZSTD\n\n'
    )
    if anchor not in kconfig:
        raise SystemExit(f"{kconfig_path} 缺少 ZRAM_DEF_COMP_ZSTD 锚点")
    kconfig = kconfig.replace(anchor, anchor + def_block, 1)

default_line = '\tdefault "zstdp" if ZRAM_DEF_COMP_ZSTDP\n'
if default_line not in kconfig:
    anchor = '\tdefault "zstd" if ZRAM_DEF_COMP_ZSTD\n'
    if anchor not in kconfig:
        raise SystemExit(f"{kconfig_path} 缺少 ZRAM_DEF_COMP 默认值锚点")
    kconfig = kconfig.replace(anchor, anchor + default_line, 1)

kconfig_path.write_text(kconfig)

makefile = makefile_path.read_text()
line = 'zram-$(CONFIG_ZRAM_BACKEND_ZSTDP)\t+= backend_zstdp.o\n'
if line not in makefile:
    anchor = 'zram-$(CONFIG_ZRAM_BACKEND_ZSTD)\t+= backend_zstd.o\n'
    if anchor not in makefile:
        raise SystemExit(f"{makefile_path} 缺少 backend_zstd 锚点")
    makefile = makefile.replace(anchor, anchor + line, 1)
    makefile_path.write_text(makefile)

zcomp = zcomp_path.read_text()
include_line = '#include "backend_zstdp.h"\n'
if include_line not in zcomp:
    anchor = '#include "backend_zstd.h"\n'
    if anchor not in zcomp:
        raise SystemExit(f"{zcomp_path} 缺少 backend_zstd.h include 锚点")
    zcomp = zcomp.replace(anchor, anchor + include_line, 1)

backend_line = '#if IS_ENABLED(CONFIG_ZRAM_BACKEND_ZSTDP)\n\t&backend_zstdp,\n#endif\n'
if backend_line not in zcomp:
    anchor = '#if IS_ENABLED(CONFIG_ZRAM_BACKEND_ZSTD)\n\t&backend_zstd,\n#endif\n'
    if anchor not in zcomp:
        raise SystemExit(f"{zcomp_path} 缺少 ZRAM_BACKEND_ZSTD backend 锚点")
    zcomp = zcomp.replace(anchor, anchor + backend_line, 1)

zcomp_path.write_text(zcomp)
PY
}

patch_zram_files() {
  local common_root="$1"
  local zram_dir="$common_root/drivers/block/zram"

  require_file "$zram_dir/Kconfig"
  require_file "$zram_dir/zcomp.c"

  if grep -Fq 'backend_zstd.h' "$zram_dir/zcomp.c"; then
    patch_modern_zram_files "$common_root"
  else
    patch_legacy_zram_files "$common_root"
  fi
}

validate_integration() {
  local common_root="$1"
  local target_dir="$common_root/crypto/abk_zstdp"
  local zram_dir="$common_root/drivers/block/zram"

  require_dir "$target_dir/vendor/lib/zstd"
  require_file "$target_dir/Kconfig"
  require_file "$target_dir/Makefile"
  require_file "$common_root/crypto/Kconfig"
  require_file "$common_root/crypto/Makefile"
  require_file "$zram_dir/Kconfig"
  require_file "$zram_dir/zcomp.c"

  grep -Fq 'source "crypto/abk_zstdp/Kconfig"' "$common_root/crypto/Kconfig" \
    || die "crypto/Kconfig 缺少 zstdp source 行"
  grep -Fq 'obj-$(CONFIG_CRYPTO_ZSTDP) += abk_zstdp/' "$common_root/crypto/Makefile" \
    || die "crypto/Makefile 缺少 zstdp object 行"

  if grep -Fq 'backend_zstd.h' "$zram_dir/zcomp.c"; then
    require_file "$zram_dir/backend_zstdp.c"
    require_file "$zram_dir/backend_zstdp.h"
    require_file "$zram_dir/Makefile"
    grep -Fq 'config ZRAM_BACKEND_ZSTDP' "$zram_dir/Kconfig" \
      || die "6.12 zram Kconfig 缺少 ZRAM_BACKEND_ZSTDP"
    grep -Fq '&backend_zstdp,' "$zram_dir/zcomp.c" \
      || die "6.12 zcomp backend 列表缺少 zstdp"
  else
    grep -Fq '"zstdp",' "$zram_dir/zcomp.c" \
      || die "zcomp backend 列表缺少 zstdp"
    if grep -Fq 'config ZRAM_DEF_COMP_ZSTD' "$zram_dir/Kconfig"; then
      grep -Fq 'config ZRAM_DEF_COMP_ZSTDP' "$zram_dir/Kconfig" \
        || die "zram Kconfig 缺少 zstdp 默认选项"
    elif [ -f "$zram_dir/zram_drv.c" ]; then
      grep -Fq 'default_compressor = "zstdp"' "$zram_dir/zram_drv.c" \
        || die "5.10 zram_drv 缺少 zstdp 默认值"
    fi
  fi
}

integrate() {
  local common_root repo_root cache_dir target_dir vendor_root

  common_root="${1:-$PWD}"
  repo_root="${ZZH_PATCHES:-$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)}"
  cache_dir="$(fetch_vendor_tree)"
  target_dir="$common_root/crypto/abk_zstdp"
  vendor_root="$target_dir/vendor"

  require_dir "$common_root/crypto"
  require_dir "$common_root/drivers/block/zram"
  require_dir "$repo_root/zram/zstdp"

  mkdir -p "$target_dir"
  copy_file "$repo_root/zram/zstdp/Kconfig" "$target_dir/Kconfig"
  copy_file "$repo_root/zram/zstdp/Makefile" "$target_dir/Makefile"
  copy_file "$repo_root/zram/zstdp/zstdp_namespace.h" "$target_dir/zstdp_namespace.h"
  copy_file "$repo_root/zram/zstdp/zstdp_wrapper.c" "$target_dir/zstdp_wrapper.c"
  if [ -f "$common_root/drivers/block/zram/backend_zstd.h" ]; then
    copy_file "$repo_root/zram/zstdp/backend_zstdp.c" "$common_root/drivers/block/zram/backend_zstdp.c"
    copy_file "$repo_root/zram/zstdp/backend_zstdp.h" "$common_root/drivers/block/zram/backend_zstdp.h"
  fi

  rm -rf "$vendor_root"
  mkdir -p "$vendor_root/lib" "$vendor_root/include/linux"
  copy_tree "$cache_dir/lib/zstd" "$vendor_root/lib/zstd"
  copy_file "$cache_dir/include/linux/zstd.h" "$vendor_root/include/linux/zstd.h"
  copy_file "$cache_dir/include/linux/zstd_lib.h" "$vendor_root/include/linux/zstd_lib.h"
  copy_file "$cache_dir/include/linux/zstd_errors.h" "$vendor_root/include/linux/zstd_errors.h"
  copy_file "$repo_root/zram/zstdp/vendor_include_linux_unaligned.h" \
    "$vendor_root/include/linux/unaligned.h"
  patch_vendor_compat "$common_root" "$vendor_root"

  patch_crypto_files "$common_root"
  patch_zram_files "$common_root"
  validate_integration "$common_root"
}

main() {
  local action="${1:-integrate}"

  case "$action" in
    integrate)
      integrate "${2:-$PWD}"
      ;;
    *)
      die "不支持的动作: $action"
      ;;
  esac

  log "done"
}

main "$@"
