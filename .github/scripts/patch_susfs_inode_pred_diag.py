#!/usr/bin/env python3
"""ABK (temporary): log the hidden-inode predicate inputs.

Usage:
    patch_susfs_inode_pred_diag.py <kernel_root>

Diagnostic only - it prints why susfs_is_inode_sus_path() does or does not deny a
lookup: the caller uid, the inode number/device, whether AS_FLAGS_SUS_PATH_HIDE_FROM
is set on the susfs inode and whether the uid table holds a deny entry for this uid.

Run after patch_susfs_hide_from_uid.py (it anchors on the fast-path comment that
patcher inserts).  Remove this step once the enforcement question is answered.
"""
import os
import sys

MARKER = "DIAG_INODE_PRED_ABK"
REL_SRC = os.path.join("fs", "susfs.c")

FAST_PATH = "\t/* ABK SUS_PATH_UID_HIDE_FROM_ABK: per-target hide-from-uid deny fast path */\n"

DIAG = (
    "\t/* ABK " + MARKER + ": temporary - remove after diagnosis */\n"
    "\tSUSFS_LOGI(\"inode_pred diag: uid='%u' ino='%lu' dev='%u' flag='%d' denied='%d'\\n\",\n"
    "\t\t\tcurrent_uid().val, inode->i_ino, inode->i_sb->s_dev,\n"
    "\t\t\t(int)test_bit(AS_FLAGS_SUS_PATH_HIDE_FROM, &sus_inode->i_mapping->flags),\n"
    "\t\t\t(int)susfs_sus_path_uid_is_denied(sus_inode));\n"
)


def die(msg):
    sys.stderr.write("::error::" + msg + "\n")
    sys.exit(2)


def main():
    if len(sys.argv) != 2:
        die("usage: patch_susfs_inode_pred_diag.py <kernel_root>")
    root = sys.argv[1]
    path = None
    for base in (os.path.join(root, "common"), root):
        candidate = os.path.join(base, REL_SRC)
        if os.path.isfile(candidate):
            path = candidate
            break
    if path is None:
        die("expected file not found: " + REL_SRC)

    with open(path, "r", encoding="utf-8", newline="") as fh:
        text = fh.read()
    if MARKER in text:
        print("skip (already patched): " + REL_SRC)
        return
    if text.count(FAST_PATH) != 1:
        die("fast-path anchor matched %d times (expected 1) in " + REL_SRC)
    text = text.replace(FAST_PATH, DIAG + FAST_PATH, 1)
    with open(path, "w", encoding="utf-8", newline="") as fh:
        fh.write(text)
    print("patch: inode predicate diagnostic (" + MARKER + ")")


if __name__ == "__main__":
    main()