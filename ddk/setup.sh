#!/bin/sh
set -eu

GKI_ROOT="$(pwd)"
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
SRC_DIR="$SCRIPT_DIR/xingguang-ddk"
PATCH_DIR="$SCRIPT_DIR/patches/xingguang-ddk"
DDK_DIR="$GKI_ROOT/Xingguang-DDK"

if [ -d "$GKI_ROOT/security" ]; then
	COMMON_ROOT="$GKI_ROOT"
	SECURITY_DIR="$GKI_ROOT/security"
elif [ -d "$GKI_ROOT/common/security" ]; then
	COMMON_ROOT="$GKI_ROOT/common"
	SECURITY_DIR="$GKI_ROOT/common/security"
else
	echo '[ERROR] security directory not found.'
	exit 127
fi

SECURITY_MAKEFILE="$SECURITY_DIR/Makefile"
SECURITY_KCONFIG="$SECURITY_DIR/Kconfig"
DDK_SYMLINK="$SECURITY_DIR/xingguang-ddk"

if [ ! -d "$SRC_DIR" ]; then
	echo "[ERROR] DDK source directory not found: $SRC_DIR"
	exit 127
fi

function_has_call() {
	file="$1"
	signature="$2"
	marker="$3"
	awk -v signature="$signature" -v marker="$marker" '
		$0 ~ "^[[:space:]]*" signature "\\(" { in_func=1 }
		in_func && index($0, marker) { found=1; exit }
		in_func && /^}/ { exit }
		END { exit found ? 0 : 1 }
	' "$file"
}

function_has_call_name() {
	file="$1"
	name="$2"
	marker="$3"
	awk -v name="$name" -v marker="$marker" '
		$0 ~ "^[[:space:]]*([_[:alnum:]]+[[:space:]*]+)+" name "[[:space:]]*\\(" { in_func=1 }
		in_func && index($0, marker) { found=1; exit }
		in_func && /^}/ { exit }
		END { exit found ? 0 : 1 }
	' "$file"
}

ensure_ddk_include() {
	file="$1"
	include='#include <linux/xingguang_ddk.h>'

	if [ ! -f "$file" ]; then
		echo "[ERROR] DDK target file not found: $file"
		return 1
	fi

	if grep -qF "$include" "$file"; then
		return 0
	fi

	if ! grep -q '^#include "blk.h"$' "$file"; then
		echo "[ERROR] DDK include anchor not found in $file"
		return 1
	fi

	sed -i '/^#include "blk.h"$/a\
#include <linux/xingguang_ddk.h>' "$file"
}

inject_blkdev_ioctl_compat() {
	file="$1"

	perl -0pi -e 's/(long blkdev_ioctl\s*\([^)]*\)\s*\{.*?\n\s*int ret;\n)/$1\n\tret = xg_ddk_blkdev_ioctl(bdev, cmd);\n\tif (ret)\n\t\treturn ret;\n/s or die "long blkdev_ioctl anchor not found\n";' "$file" 2>/dev/null && return 0
	perl -0pi -e 's/(int blkdev_ioctl\s*\([^)]*\)\s*\{.*?\n\s*int ret;\n)/$1\n\tret = xg_ddk_blkdev_ioctl(bdev, cmd);\n\tif (ret)\n\t\treturn ret;\n/s or die "int blkdev_ioctl anchor not found\n";' "$file" 2>/dev/null && return 0

	echo "blkdev_ioctl anchor not found"
	return 1
}

inject_compat_blkdev_ioctl_compat() {
	file="$1"

	perl -0pi -e 's/(long compat_blkdev_ioctl\s*\([^)]*\)\s*\{.*?\n\s*fmode_t mode = [^\n]+;\n)/$1\n\tret = xg_ddk_blkdev_ioctl(bdev, cmd);\n\tif (ret)\n\t\treturn ret;\n/s or die "compat_blkdev_ioctl anchor not found\n";' "$file" 2>/dev/null && return 0

	echo "compat_blkdev_ioctl anchor not found"
	return 1
}

apply_ddk_0030_compat() {
	ioctl_file="$COMMON_ROOT/block/ioctl.c"
	blk_lib_file="$COMMON_ROOT/block/blk-lib.c"

	ensure_ddk_include "$ioctl_file" || return 1
	ensure_ddk_include "$blk_lib_file" || return 1

	if ! function_has_call_name "$ioctl_file" "blkdev_ioctl" "xg_ddk_blkdev_ioctl(bdev, cmd)"; then
		inject_blkdev_ioctl_compat "$ioctl_file" || return 1
	fi

	if ! function_has_call_name "$ioctl_file" "compat_blkdev_ioctl" "xg_ddk_blkdev_ioctl(bdev, cmd)"; then
		inject_compat_blkdev_ioctl_compat "$ioctl_file" || return 1
	fi

	if ! function_has_call "$blk_lib_file" "int __blkdev_issue_discard" "xg_ddk_blkdev_issue_discard(bdev, sector, nr_sects)"; then
		if function_has_call "$blk_lib_file" "int __blkdev_issue_discard" "int ret;"; then
			perl -0pi -e 's/(int __blkdev_issue_discard\s*\([^)]*\)\s*\{.*?\n)(\s*if \(bdev_read_only\(bdev\)\))/$1\tret = xg_ddk_blkdev_issue_discard(bdev, sector, nr_sects);\n\tif (ret)\n\t\treturn ret;\n\n$2/s or die "__blkdev_issue_discard anchor not found\n";' "$blk_lib_file" || return 1
		else
			perl -0pi -e 's/(int __blkdev_issue_discard\s*\([^)]*\)\s*\{.*?\n\s*sector_t bs_mask;\n)(\s*if \(bdev_read_only\(bdev\)\))/$1\tint ret;\n\n\tret = xg_ddk_blkdev_issue_discard(bdev, sector, nr_sects);\n\tif (ret)\n\t\treturn ret;\n\n$2/s or die "__blkdev_issue_discard anchor not found\n";' "$blk_lib_file" || return 1
		fi
	fi

	if ! function_has_call "$blk_lib_file" "int __blkdev_issue_zeroout" "xg_ddk_blkdev_issue_zeroout(bdev, sector, nr_sects)"; then
		perl -0pi -e 's/(int __blkdev_issue_zeroout\s*\([^)]*\)\s*\{.*?\n\s*sector_t bs_mask;\n)(\s*bs_mask = )/$1\n\tret = xg_ddk_blkdev_issue_zeroout(bdev, sector, nr_sects);\n\tif (ret)\n\t\treturn ret;\n\n$2/s or die "__blkdev_issue_zeroout anchor not found\n";' "$blk_lib_file" || return 1
	fi

	if ! function_has_call "$blk_lib_file" "int blkdev_issue_zeroout" "xg_ddk_blkdev_issue_zeroout(bdev, sector, nr_sects)"; then
		perl -0pi -e 's/(int blkdev_issue_zeroout\s*\([^)]*\)\s*\{.*?\n\s*bool try_write_zeroes = !!bdev_write_zeroes_sectors\(bdev\);\n)(\s*bs_mask = )/$1\n\tret = xg_ddk_blkdev_issue_zeroout(bdev, sector, nr_sects);\n\tif (ret)\n\t\treturn ret;\n\n$2/s or die "blkdev_issue_zeroout anchor not found\n";' "$blk_lib_file" || return 1
	fi

	function_has_call_name "$ioctl_file" "blkdev_ioctl" "xg_ddk_blkdev_ioctl(bdev, cmd)" || return 1
	function_has_call_name "$ioctl_file" "compat_blkdev_ioctl" "xg_ddk_blkdev_ioctl(bdev, cmd)" || return 1
	function_has_call "$blk_lib_file" "int __blkdev_issue_discard" "xg_ddk_blkdev_issue_discard(bdev, sector, nr_sects)" || return 1
	function_has_call "$blk_lib_file" "int __blkdev_issue_zeroout" "xg_ddk_blkdev_issue_zeroout(bdev, sector, nr_sects)" || return 1
	function_has_call "$blk_lib_file" "int blkdev_issue_zeroout" "xg_ddk_blkdev_issue_zeroout(bdev, sector, nr_sects)" || return 1
}

echo "[+] Setting up Xingguang DDK LSM"

if [ -d "$PATCH_DIR" ]; then
	echo "[+] Applying Xingguang DDK patch stack"
	for patch in "$PATCH_DIR"/*.patch; do
		[ -e "$patch" ] || continue
		name="$(basename "$patch")"
		optional=false
		case "$name" in
			*.optional.patch) optional=true ;;
		esac
		if git -C "$COMMON_ROOT" apply --check "$patch" >/dev/null 2>&1; then
			git -C "$COMMON_ROOT" apply "$patch"
			echo " - applied $name"
		elif git -C "$COMMON_ROOT" apply --reverse --check "$patch" >/dev/null 2>&1; then
			echo " - already applied $name"
		elif [ "$name" = "0030-block-ioctl-erase-callsite.patch" ] && apply_ddk_0030_compat; then
			echo " - applied $name (compat)"
		elif [ "$optional" = true ]; then
			echo " - skipped optional $name"
		else
			echo "[ERROR] failed to apply DDK patch: $patch"
			git -C "$COMMON_ROOT" apply --check "$patch"
			exit 1
		fi
	done
fi

rm -rf "$DDK_DIR"
mkdir -p "$DDK_DIR"
cp -a "$SRC_DIR/." "$DDK_DIR/"

cd "$SECURITY_DIR"
if command -v realpath >/dev/null 2>&1; then
	rel="$(realpath --relative-to="$SECURITY_DIR" "$DDK_DIR" 2>/dev/null || true)"
else
	rel="$DDK_DIR"
fi
[ -n "$rel" ] || rel="$DDK_DIR"
ln -sfn "$rel" "$DDK_SYMLINK"

if ! grep -q 'xingguang-ddk' "$SECURITY_MAKEFILE"; then
	printf '\nobj-$(CONFIG_XINGGUANG_DDK) += xingguang-ddk/\n' >> "$SECURITY_MAKEFILE"
	echo " - Makefile updated"
fi

if ! grep -q 'security/xingguang-ddk/Kconfig' "$SECURITY_KCONFIG"; then
	if grep -n '^endmenu[[:space:]]*$' "$SECURITY_KCONFIG" >/dev/null 2>&1; then
		awk '
			{ a[NR]=$0 }
			END {
				last=0
				for (i=1; i<=NR; i++) if (a[i] ~ /^endmenu[[:space:]]*$/) last=i
				for (i=1; i<=NR; i++) {
					if (i==last) print "source \"security/xingguang-ddk/Kconfig\""
					print a[i]
				}
			}
		' "$SECURITY_KCONFIG" > "$SECURITY_KCONFIG.tmp" && mv "$SECURITY_KCONFIG.tmp" "$SECURITY_KCONFIG"
	else
		printf '\nsource "security/xingguang-ddk/Kconfig"\n' >> "$SECURITY_KCONFIG"
	fi
	echo " - Kconfig updated"
fi

sed -i '/^config LSM$/,/^help$/{ /^[[:space:]]*default/ { /xingguang_ddk/! s/selinux/selinux,xingguang_ddk/ } }' "$SECURITY_KCONFIG"

echo "[+] Xingguang DDK LSM ready."
