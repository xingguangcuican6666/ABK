#!/usr/bin/env python3
"""Apply small ABI compatibility shims required by ABK's SUSFS integration."""
from pathlib import Path
import sys


def locate(root: Path) -> Path:
    for path in (root / "KernelSU/kernel", root / "common/drivers/kernelsu", root / "drivers/kernelsu", root / "kernel"):
        if (path / "Kbuild").is_file() or (path / "supercall.c").is_file() or (path / "feature/sucompat.c").is_file():
            return path
    raise SystemExit(f"KernelSU source directory not found below {root}")


def ensure_su_fd(ksu: Path) -> bool:
    source = next((path for path in (ksu / "supercall.c", ksu / "supercall/supercall.c") if path.is_file()), None)
    if source is None:
        return False
    header = source.with_suffix(".h")

    changed = False
    text = source.read_text()
    if "int ksu_install_su_fd(void)" not in text:
        marker = "\nvoid __init ksu_supercalls_init(void)"
        if marker not in text:
            raise SystemExit(f"missing supercall init anchor: {source}")
        if "ksu_install_fd_with_permissions(" in text:
            compat = "\nint ksu_install_su_fd(void)\n{\n    /* Install the scoped descriptor after exec enters ksud. */\n    return ksu_install_fd_with_permissions(O_CLOEXEC, KSU_DRIVER_PERMISSION_SU_SESSION);\n}\n"
        else:
            compat = "\n/* ABK: legacy SukiSU has no scoped driver contexts yet. */\nint ksu_install_su_fd(void)\n{\n    return ksu_install_fd();\n}\n"
        source.write_text(text.replace(marker, compat + marker, 1))
        changed = True

    if header.is_file():
        text = header.read_text()
        if "int ksu_install_su_fd(void);" not in text:
            marker = "int ksu_install_fd(void);"
            if marker not in text:
                raise SystemExit(f"missing ksu_install_fd declaration: {header}")
            header.write_text(text.replace(marker, marker + "\nint ksu_install_su_fd(void);", 1))
            changed = True
    return changed


def ensure_umount_set(ksu: Path) -> bool:
    path = ksu / "feature/kernel_umount.c"
    if not path.is_file():
        return False
    text = path.read_text()
    if "static int kernel_umount_feature_set(" in text or "int kernel_umount_feature_set(" in text:
        return False
    marker = "\nstatic const struct ksu_feature_handler kernel_umount_handler"
    if marker not in text:
        raise SystemExit(f"missing kernel umount handler anchor: {path}")
    block = """
static int kernel_umount_feature_set(u64 value)
{
    ksu_kernel_umount_enabled = value != 0;
    return 0;
}
"""
    path.write_text(text.replace(marker, block + marker, 1))
    return True


def main(root: str) -> None:
    ksu = locate(Path(root).resolve())
    changed_su_fd = ensure_su_fd(ksu)
    changed_umount_set = ensure_umount_set(ksu)
    changed = changed_su_fd or changed_umount_set
    print(f"KSU compatibility {'updated' if changed else 'already satisfied'}: {ksu}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("usage: ensure-ksu-compat.py <kernel-root>")
    main(sys.argv[1])
