#!/usr/bin/env python3
"""ABK: never key sus_path uid entries on inode number 0.

Usage:
    patch_susfs_ino_zero_guard.py <kernel_root>

Genshin Impact (com.miHoYo.Yuanshen) could not download its resources after the
hide list started using hide_from on /storage/emulated/0/Android/data/<pkg>:

    susfs_is_inode_sus_path] hiding path with ino '0' from uid '10274'

FUSE does not instantiate the child inode for those paths, so inode->i_ino is 0
and the uid entry becomes (ino 0, dev <fuse>, uid, mode 1) - a wildcard that
matches *every* FUSE inode whose i_ino is 0, including the app's own directories.
An app that is itself a target (18 rows here) therefore loses access to its own
FUSE paths.

Skip ino 0 when writing the table and when querying it.  Per-target hiding of
FUSE paths stops working (a FUSE path has no stable inode to key on), which is
expected - only real filesystems such as /data keep per-uid hiding.

Run after patch 60 and patch_susfs_hide_from_uid.py.
"""
import os
import sys

MARKER = "SUS_PATH_INO0_GUARD_ABK"

REL_SRC = os.path.join("fs", "susfs.c")

UPSERT_OLD = "\tif (!inode || uid < 0)\n\t\treturn;\n"
UPSERT_NEW = (
    "\t/* ABK " + MARKER + ": FUSE reports i_ino 0, which would turn the entry\n"
    "\t * into a wildcard matching every unresolved inode. */\n"
    "\tif (!inode || inode->i_ino == 0 || uid < 0)\n"
    "\t\treturn;\n"
)

DENIED_OLD = (
    "static bool susfs_sus_path_uid_is_denied(struct inode *inode) {\n"
    "\tstruct st_susfs_sus_path_hlist *entry = NULL;\n"
    "\tbool denied = false;\n"
    "\n"
    "\tif (!inode)\n"
    "\t\treturn false;\n"
)
DENIED_NEW = (
    "static bool susfs_sus_path_uid_is_denied(struct inode *inode) {\n"
    "\tstruct st_susfs_sus_path_hlist *entry = NULL;\n"
    "\tbool denied = false;\n"
    "\n"
    "\tif (!inode || inode->i_ino == 0)\n"
    "\t\treturn false;\n"
)

ALLOWED_OLD = (
    "static bool susfs_sus_path_uid_is_allowed(struct inode *inode) {\n"
    "\tstruct st_susfs_sus_path_hlist *entry = NULL;\n"
    "\tbool allowed = false;\n"
    "\n"
    "\tif (!inode)\n"
    "\t\treturn false;\n"
)
ALLOWED_NEW = (
    "static bool susfs_sus_path_uid_is_allowed(struct inode *inode) {\n"
    "\tstruct st_susfs_sus_path_hlist *entry = NULL;\n"
    "\tbool allowed = false;\n"
    "\n"
    "\tif (!inode || inode->i_ino == 0)\n"
    "\t\treturn false;\n"
)


def die(msg):
    sys.stderr.write("::error::" + msg + "\n")
    sys.exit(2)


def load(path):
    if not os.path.isfile(path):
        die("expected file not found: " + path)
    with open(path, "r", encoding="utf-8", newline="") as fh:
        return fh.read()


def save(path, text):
    with open(path, "w", encoding="utf-8", newline="") as fh:
        fh.write(text)


def main():
    if len(sys.argv) != 2:
        die("usage: patch_susfs_ino_zero_guard.py <kernel_root>")
    root = sys.argv[1]
    base = None
    for candidate in (os.path.join(root, "common"), root):
        if os.path.isfile(os.path.join(candidate, REL_SRC)):
            base = candidate
            break
    if base is None:
        die("kernel sources not found under " + root)

    src = os.path.join(base, REL_SRC)
    s = load(src)

    if MARKER in s:
        print("[ABK] skip: " + src + " already carries " + MARKER)
        return 0

    if UPSERT_OLD not in s:
        die("anchor 'upsert guard (!inode || uid < 0)' missing from " + src +
            "; run patch 60 and patch_susfs_hide_from_uid.py first")
    s = s.replace(UPSERT_OLD, UPSERT_NEW, 1)

    if DENIED_OLD not in s:
        die("anchor 'susfs_sus_path_uid_is_denied' missing from " + src)
    s = s.replace(DENIED_OLD, DENIED_NEW, 1)

    if ALLOWED_OLD not in s:
        die("anchor 'susfs_sus_path_uid_is_allowed' missing from " + src)
    s = s.replace(ALLOWED_OLD, ALLOWED_NEW, 1)

    save(src, s)

    if load(src).count(MARKER) < 1:
        die("verification failed: " + MARKER + " missing")
    print("[ABK] sus_path ino-0 guard OK: " + MARKER +
          " (FUSE i_ino 0 no longer becomes a wildcard uid entry)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
