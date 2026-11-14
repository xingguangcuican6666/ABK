#!/usr/bin/env python3
"""ABK: filter ABK sus_path entries out of directory listings (readdir/getdents).

Usage:
    patch_susfs_readdir_filter.py <kernel_root>

Background: the SUSFS "50_add_susfs_in_gki-*" patch already filters sus_path
entries inside fs/readdir.c, but it can only do so when the entry's inode is still
in the inode cache:

    inode = ilookup(buf->sb, ino);
    if (!inode) {
            goto orig_flow;          <-- uncached inode: entry is emitted anyway
    }

For a plain directory listing the children are usually NOT cached (nothing looked
them up yet), so ilookup() returns NULL and the hidden name still shows up. The
ABK per-UID (patches/60_add_per_uid_sus_path.patch) and per-target
(patch_susfs_hide_from_uid.py) sus_path entries are affected the same way.

This patcher adds the missing fallback to the ALREADY SUSFS-patched kernel tree
(paths relative to <kernel_root>/common):

    fs/susfs.c              susfs_is_ino_sus_path_hidden(u64 ino, u64 dev)
    include/linux/susfs.h   prototype of that predicate
    fs/readdir.c            consult the predicate inside the existing
                            "if (!inode) { goto orig_flow; }" block of every
                            dir_context actor (filldir / filldir64 /
                            compat_filldir, plus the legacy old-readdir ones)

The predicate reuses the SUS_PATH_UID_HLIST table that patch 60 / hide-from
already maintain (no second table) and mirrors the per-uid decision of
susfs_is_inode_sus_path():

    mode == 1 entry with allowed_uid == current uid -> hidden (per-target deny)
    mode == 0 entry with allowed_uid != current uid -> hidden (global sus_path,
                                                        visible to allowed_uid only)

The umounted-app check runs first and cheapest, and readdir.c passes the real
device of the listed superblock (buf->sb->s_dev), so no cross-filesystem inode
number collisions can occur. Every anchor is asserted; a missing or ambiguous
anchor aborts with exit code 2 and an ::error:: line, so CI never silently ships
an unpatched kernel. Re-running is a no-op.

Known limits:
    * the fallback only sees entries that exist in SUS_PATH_UID_HLIST, i.e. a
      global sus_path added WITHOUT a visible uid (ksu_susfs add_sus_path with
      info.uid == -1) is not filtered here;
    * readdir only carries the inode number, so the predicate cannot verify the
      inode owner uid: when an entry is hidden from everyone but allowed_uid, an
      uncached entry owned by the current uid is hidden as well.
"""
import os
import re
import sys

MARKER = "SUS_PATH_READDIR_FILTER_ABK"
HIDE_FROM_MARKER = "SUS_PATH_UID_HIDE_FROM_ABK"

REL_HDR = os.path.join("include", "linux", "susfs.h")
REL_SRC = os.path.join("fs", "susfs.c")
REL_READDIR = os.path.join("fs", "readdir.c")

HDR_ANCHOR = "void susfs_add_sus_path_loop(void __user **user_info);\n"
SRC_ANCHOR = "bool susfs_is_inode_sus_path("
SRC_GUARD_ANCHOR = "#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 6, 0)\n"

READDIR_INCLUDE_ANCHOR = ("#ifdef CONFIG_KSU_SUSFS_SUS_PATH\n"
                          "#include <linux/susfs_def.h>\n")
READDIR_SUSFS_INCLUDE = "#include <linux/susfs.h> /* ABK " + MARKER + " */"
READDIR_ILOOKUP = ("\tinode = ilookup(buf->sb, ino);\n"
                    "\tif (!inode) {\n"
                    "\t\tgoto orig_flow;\n"
                    "\t}\n")
READDIR_CALL = "susfs_is_ino_sus_path_hidden((u64)ino, (u64)buf->sb->s_dev)"

HDR_BLOCK = (
    "/* ABK " + MARKER + ": readdir/getdents-level sus_path filter */\n"
    "bool susfs_is_ino_sus_path_hidden(u64 ino, u64 dev);\n"
)

PREDICATE = (
    "/* ABK " + MARKER + ": (ino, dev) predicate usable from the VFS readdir path.\n"
    " * A directory entry only carries the inode number, so the per-uid decision is\n"
    " * taken from the SUS_PATH_UID_HLIST table instead of from the inode itself.\n"
    " */\n"
    "bool susfs_is_ino_sus_path_hidden(u64 ino, u64 dev) {\n"
    "\tstruct st_susfs_sus_path_hlist *entry = NULL;\n"
    "\tuid_t uid;\n"
    "\tbool denied = false, allowed = false, other = false;\n"
    "\n"
    "\t/* cheapest check first: only umounted app processes are affected */\n"
    "\tif (!susfs_is_current_proc_umounted_app())\n"
    "\t\treturn false;\n"
    "\n"
    "\tuid = current_uid().val;\n"
    "\n"
    "\trcu_read_lock();\n"
    "\thash_for_each_possible_rcu(SUS_PATH_UID_HLIST, entry, node, (unsigned long)ino) {\n"
    "\t\tif ((u64)entry->target_ino != ino)\n"
    "\t\t\tcontinue;\n"
    "\t\tif (dev && (u64)entry->target_dev != dev)\n"
    "\t\t\tcontinue;\n"
    "\t\tif (entry->allowed_uid < 0)\n"
    "\t\t\tcontinue;\n"
    "\t\tif ((uid_t)entry->allowed_uid == uid) {\n"
    "\t\t\tif (entry->mode == 1) {\n"
    "\t\t\t\t/* per-target deny: hidden from this uid only */\n"
    "\t\t\t\tdenied = true;\n"
    "\t\t\t\tbreak;\n"
    "\t\t\t}\n"
    "\t\t\t/* global sus_path naming this uid as the visible one */\n"
    "\t\t\tallowed = true;\n"
    "\t\t} else if (entry->mode == 0) {\n"
    "\t\t\t/* global sus_path: hidden from every uid but the allowed one */\n"
    "\t\t\tother = true;\n"
    "\t\t}\n"
    "\t}\n"
    "\trcu_read_unlock();\n"
    "\n"
    "\tif (denied || (other && !allowed)) {\n"
    "\t\tSUSFS_LOGI(\"readdir: hiding entry with ino '%lu' from uid '%u'\\n\",\n"
    "\t\t\t\t(unsigned long)ino, uid);\n"
    "\t\treturn true;\n"
    "\t}\n"
    "\treturn false;\n"
    "}\n"
    "\n"
)

READDIR_HIT_OLD = "\tif (susfs_is_inode_sus_path(inode)) {\n"
READDIR_HIT_NEW = (
    "\t/* ABK " + MARKER + ": a recycled inode carries no AS_FLAGS_SUS_PATH, so the\n"
    "\t * cache-hit path must consult the uid table as well.\n"
    "\t */\n"
    "\tif (susfs_is_inode_sus_path(inode) ||\n"
    "\t    susfs_is_ino_sus_path_hidden((u64)ino, (u64)buf->sb->s_dev)) {\n"
)

READDIR_FALLBACK = (
    "\tinode = ilookup(buf->sb, ino);\n"
    "\tif (!inode) {\n"
    "\t\t/* ABK " + MARKER + ": the inode is not in the cache, so\n"
    "\t\t * susfs_is_inode_sus_path() cannot see its AS_FLAGS_SUS_PATH flags;\n"
    "\t\t * fall back to the sus_path hlist so the entry is still filtered out.\n"
    "\t\t */\n"
    "\t\tif (unlikely(" + READDIR_CALL + "))\n"
    "\t\t\treturn %s;\n"
    "\t\tgoto orig_flow;\n"
    "\t}\n"
)

ACTOR_RE = re.compile(
    r'^static\s+(int|bool)\s+\w+\s*[(]struct dir_context\s*[*]', re.M)


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


def sub_one(text, needle, repl, path, label):
    count = text.count(needle)
    if count != 1:
        die("anchor '" + label + "' matched " + str(count) + " times (expected 1) in " + path)
    return text.replace(needle, repl, 1)


def patch_hdr(hdr):
    text = load(hdr)
    if MARKER in text:
        return "skip"
    text = sub_one(text, HDR_ANCHOR, HDR_ANCHOR + HDR_BLOCK, hdr, "susfs_add_sus_path_loop prototype")
    save(hdr, text)
    return "patch"


def patch_src(src):
    text = load(src)
    if MARKER in text:
        return "skip"
    if HIDE_FROM_MARKER not in text or "entry->mode" not in text:
        die("the ABK per-UID / hide-from-uid sus_path patches are missing from " + src +
            "; run patch_susfs_hide_from_uid.py before this patcher")
    hits = [m.start() for m in re.finditer(re.escape(SRC_ANCHOR), text)]
    if len(hits) != 2:
        die("anchor '" + SRC_ANCHOR + "' matched " + str(len(hits)) +
            " times (expected 2: the 6.6+ and the pre-6.6 definition) in " + src)
    pos = text.rfind(SRC_GUARD_ANCHOR, 0, hits[0])
    if pos == -1:
        die("anchor '" + SRC_GUARD_ANCHOR.strip() + "' not found before the sus_path definition in " + src)
    save(src, text[:pos] + PREDICATE + text[pos:])
    return "patch"


def patch_readdir(path):
    text = load(path)
    if MARKER in text:
        return "skip"
    text = sub_one(text, READDIR_INCLUDE_ANCHOR,
                   READDIR_INCLUDE_ANCHOR + READDIR_SUSFS_INCLUDE + "\n",
                   path, "readdir.c SUSFS include block")
    spots = [m.start() for m in re.finditer(re.escape(READDIR_ILOOKUP), text)]
    if len(spots) < 3:
        die("expected at least 3 SUSFS ilookup blocks (filldir / filldir64 / compat_filldir) in "
            + path + ", found " + str(len(spots)) +
            "; the kernel tree does not carry the upstream SUSFS fs/readdir.c hook")
    for pos in reversed(spots):
        actors = list(ACTOR_RE.finditer(text, 0, pos))
        if not actors:
            die("no enclosing dir_context actor found for the ilookup block at offset " +
                str(pos) + " in " + path)
        value = "true" if actors[-1].group(1) == "bool" else "0"
        text = text[:pos] + (READDIR_FALLBACK % value) + text[pos + len(READDIR_ILOOKUP):]

    # Cache-hit path: the upstream hook only tests the inode flags, and an inode
    # that was evicted and recreated carries none, so the name would reappear in
    # the same process until the next loop replay.  Consult the uid table here too.
    spent = text.count(READDIR_HIT_OLD)
    if spent < 3:
        die("cache-hit anchor matched " + str(spent) +
            " times (expected at least 3: filldir / filldir64 / compat_filldir) in " + path)
    text = text.replace(READDIR_HIT_OLD, READDIR_HIT_NEW)

    save(path, text)
    return "patch (" + str(len(spots)) + " actors)"


def main():
    if len(sys.argv) != 2:
        die("usage: patch_susfs_readdir_filter.py <kernel_root>")
    root = sys.argv[1]
    base = None
    for candidate in (os.path.join(root, "common"), root):
        if all(os.path.isfile(os.path.join(candidate, rel))
               for rel in (REL_HDR, REL_SRC, REL_READDIR)):
            base = candidate
            break
    if base is None:
        die("SUSFS kernel sources not found under " + root +
            " (need fs/susfs.c, fs/readdir.c and include/linux/susfs.h, directly or in common/)")

    hdr = os.path.join(base, REL_HDR)
    src = os.path.join(base, REL_SRC)
    readdir = os.path.join(base, REL_READDIR)

    print("[ABK] kernel tree: " + base)
    print("[ABK] " + patch_hdr(hdr) + ": " + hdr)
    print("[ABK] " + patch_src(src) + ": " + src)
    print("[ABK] " + patch_readdir(readdir) + ": " + readdir)

    rd = load(readdir)
    if READDIR_SUSFS_INCLUDE not in rd:
        die("verification failed: the susfs.h include is missing from " + readdir)
    for path, needle in ((hdr, "susfs_is_ino_sus_path_hidden(u64 ino, u64 dev);"),
                         (src, "bool susfs_is_ino_sus_path_hidden(u64 ino, u64 dev) {")):
        if needle not in load(path):
            die("verification failed: '" + needle + "' missing from " + path)
    # Every actor now consults the uid table twice: once in the !inode fallback and
    # once on the cache-hit path, so the expected count is twice the block count.
    guards = rd.count(READDIR_CALL)
    orig = rd.count("goto orig_flow;")
    if guards < 6 or guards != 2 * orig:
        die("verification failed: " + str(guards) + " readdir uid-table checks for " +
            str(orig) + " ilookup blocks (expected >= 6 and exactly twice the blocks)")

    print("[ABK] readdir sus_path filter OK: " + MARKER + " in fs/susfs.c, fs/readdir.c, "
          "include/linux/susfs.h (" + str(guards) + " uid-table checks, " + str(orig) +
          " actors)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
