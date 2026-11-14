#!/usr/bin/env python3
"""ABK: give every sus_path entry a matching sus_kstat registration.

Usage:
    patch_susfs_kstat_autoreg.py <kernel_root>

Background (measured against susfs4ksu gki-android14-6.1, SUSFS v2.3.0):

  * fs/stat.c already spoofs getattr for app-uid processes:
        if (susfs_is_current_app_uid()) { ... susfs_is_inode_sus_kstat() ... }
    so a registered SUS_KSTAT entry is honoured for ordinary and isolated app
    processes, no umounted flag required.
  * fs/statfs.c routes a sus_kstat inode to the NON-SUS mount root
    (susfs_statfs_by_dentry -> statfs_by_dentry(no_sus_vfsmnt->mnt_root)).
  * But the sus_path handlers never registered a kstat entry.  A path that is
    hidden via sus_path therefore has no pre-hide (dev, ino) record: an fd that
    was opened before the hide, or a shadow mount that is still walked, can
    report numbers that do not exist in the visible tree.  That is the
    st_dev/st_ino consistency class of check.

This patcher registers a static SUS_KSTAT entry for every path passed to
add_sus_path / add_sus_path_loop, BEFORE the path is flagged:

    target_ino/target_dev  = the real inode numbers of the path
    spoofed_ino/spoofed_dev= the same real numbers (captured pre-hide)
    spoofed_nlink/size/times/blocks/blksize = the real inode metadata
    spoofed_mnt_id/kstatfs = resolved from the non-sus mount by the existing
                             susfs_mark_inode_sus_kstat() helper

so stat()/fstat()/statx()/statfs() on anything that still reaches the inode
report the non-sus filesystem view, identical to the mountinfo view that SUSFS
exports to app-uid processes.  su / ksu tooling is untouched.

Kill switch: plain bool, default on:
    abk_sus_path_kstat=0

Touched file (relative to <kernel_root>, directly or under common/):
    fs/susfs.c

Every missing or ambiguous anchor aborts with exit code 2 and an ::error::
line.  Re-running is a no-op.
"""
import os
import re
import sys

MARKER = "SUS_PATH_KSTAT_AUTOREG_ABK"
HELPER = "susfs_abk_register_path_kstat"
REL = os.path.join("fs", "susfs.c")

PROTO = (
    "\n"
    "/* ABK " + MARKER + ": register a static sus_kstat entry for a sus_path target.\n"
    " * Defined after susfs_mark_inode_sus_kstat() below. */\n"
    "static void " + HELPER + "(struct vfsmount *mnt, struct inode *inode,\n"
    "\t\t\t\tconst char *pathname);\n"
)

DEF_ANCHOR = "void susfs_add_sus_kstat(void __user **user_info) {\n"

HELPER_DEF = (
    "/* ABK " + MARKER + ": keep stat()/fstat()/statfs() on a hidden path in\n"
    " * sync with the non-sus filesystem view.  Called before the path is\n"
    " * flagged, so every value below is the real, pre-hide value.\n"
    " * Boot-time override: abk_sus_path_kstat=0\n"
    " */\n"
    "static bool abk_sus_path_kstat __read_mostly = true;\n"
    "core_param(abk_sus_path_kstat, abk_sus_path_kstat, bool, 0644);\n"
    "\n"
    "static void " + HELPER + "(struct vfsmount *mnt, struct inode *inode,\n"
    "\t\t\t\tconst char *pathname)\n"
    "{\n"
    "\tstruct st_susfs_sus_kstat_hlist *entry = NULL, *tmp = NULL;\n"
    "\tunsigned long tgt_ino;\n"
    "\tbool is_fuse = false;\n"
    "\n"
    "\tif (!READ_ONCE(abk_sus_path_kstat) || !inode || !inode->i_mapping || !mnt)\n"
    "\t\treturn;\n"
    "\n"
    "\tif (inode->i_sb->s_magic == FUSE_SUPER_MAGIC) {\n"
    "\t\tstruct fuse_inode *fi = get_fuse_inode(inode);\n"
    "\n"
    "\t\tif (!fi || !fi->inode.i_mapping)\n"
    "\t\t\treturn;\n"
    "\t\tis_fuse = true;\n"
    "\t\ttgt_ino = fi->inode.i_ino;\n"
    "\t} else {\n"
    "\t\ttgt_ino = inode->i_ino;\n"
    "\t}\n"
    "\n"
    "\tentry = kzalloc(sizeof(*entry), GFP_KERNEL);\n"
    "\tif (!entry)\n"
    "\t\treturn;\n"
    "\tentry->target_ino = tgt_ino;\n"
    "\tentry->target_dev = inode->i_sb->s_dev;\n"
    "\tentry->is_fuse = is_fuse;\n"
    "\tentry->info.is_statically = true;\n"
    "\t/* ABK review fix: spoof only the identity fields.  Baking size, times and\n"
    "\t * blocks in at registration time would make fstat() of a growing file (even\n"
    "\t * for its owner) keep returning the size it had when the rule was added. */\n"
    "\tentry->info.flags = KSTAT_SPOOF_INO | KSTAT_SPOOF_DEV;\n"
    "\tentry->info.spoofed_ino = inode->i_ino;\n"
    "\tentry->info.spoofed_dev = inode->i_sb->s_dev;\n"
    "\tstrscpy(entry->info.target_pathname, pathname, SUSFS_MAX_LEN_PATHNAME - 1);\n"
    "\n"
    "\t/* fills spoofed_mnt_id + spoofed_kstatfs from the non-sus mount and sets\n"
    "\t * AS_FLAGS_SUS_KSTAT on the inode; it re-resolves the path by name. */\n"
    "\tif (susfs_mark_inode_sus_kstat(entry->info.target_pathname, entry, false)) {\n"
    "\t\tkfree(entry);\n"
    "\t\treturn;\n"
    "\t}\n"
    "\n"
    "\tmutex_lock(&susfs_mutex_lock_sus_kstat);\n"
    "\thash_for_each_possible(SUS_KSTAT_HLIST, tmp, node, tgt_ino) {\n"
    "\t\tif (tmp->target_ino == tgt_ino &&\n"
    "\t\t    tmp->target_dev == entry->target_dev &&\n"
    "\t\t    tmp->is_fuse == is_fuse) {\n"
    "\t\t\tkfree(entry);\n"
    "\t\t\tmutex_unlock(&susfs_mutex_lock_sus_kstat);\n"
    "\t\t\treturn;\n"
    "\t\t}\n"
    "\t}\n"
    "\thash_add_rcu(SUS_KSTAT_HLIST, &entry->node, tgt_ino);\n"
    "\tmutex_unlock(&susfs_mutex_lock_sus_kstat);\n"
    "\n"
    "\tSUSFS_LOGI(\"ABK auto sus_kstat for '%s': ino '%lu', dev '%u', fuse '%d'\\n\",\n"
    "\t\t\tpathname, tgt_ino, entry->target_dev, is_fuse);\n"
    "}\n"
    "\n"
)

# add_sus_path: fuse branch and normal branch (unique log strings).
# 60_add_per_uid_sus_path.patch inserts susfs_sus_path_uid_upsert() between the
# set_bit() calls and the log line, so that call is optional in every anchor.
RE_FUSE = re.compile(
    r"(?m)^([ \t]*)set_bit\(AS_FLAGS_SUS_PATH, &fi->inode\.i_mapping->flags\);\n"
    r"([ \t]*)set_bit\(AS_FLAGS_SUS_PATH, &inode->i_mapping->flags\);\n"
    r"\2(?:susfs_sus_path_uid_upsert\([^\n]*\);\n)?"
    r"\2SUSFS_LOGI\(\"flagged AS_FLAGS_SUS_PATH on pathname: '%s', fi->nodeid:")
RE_NORMAL = re.compile(
    r"(?m)^([ \t]*)set_bit\(AS_FLAGS_SUS_PATH, &inode->i_mapping->flags\);\n"
    r"\1(?:susfs_sus_path_uid_upsert\([^\n]*\);\n)?"
    r"\1SUSFS_LOGI\(\"flagged AS_FLAGS_SUS_PATH on pathname: '%s', ino: '%lu'")
RE_LOOP_FUSE = re.compile(
    r"(?m)^([ \t]*)set_bit\(AS_FLAGS_SUS_PATH, &fi->inode\.i_mapping->flags\);\n"
    r"([ \t]*)set_bit\(AS_FLAGS_SUS_PATH, &inode->i_mapping->flags\);\n"
    r"\2(?:susfs_sus_path_uid_upsert\([^\n]*\);\n)?"
    r"\2SUSFS_LOGI\(\"re-flag AS_FLAGS_SUS_PATH on path '%s', fi->inode\.i_ino:")
RE_LOOP_NORMAL = re.compile(
    r"(?m)^([ \t]*)set_bit\(AS_FLAGS_SUS_PATH, &inode->i_mapping->flags\);\n"
    r"\1(?:susfs_sus_path_uid_upsert\([^\n]*\);\n)?"
    r"\1SUSFS_LOGI\(\"re-flag AS_FLAGS_SUS_PATH on path '%s', inode->i_ino:")


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


def sub_re_one(text, rx, repl_fn, path, label):
    matches = list(rx.finditer(text))
    if len(matches) != 1:
        die("anchor '" + label + "' matched " + str(len(matches)) +
            " times (expected 1) in " + path)
    return rx.sub(repl_fn, text, count=1)


def main():
    if len(sys.argv) != 2:
        die("usage: patch_susfs_kstat_autoreg.py <kernel_root>")
    root = sys.argv[1]
    base = None
    for candidate in (os.path.join(root, "common"), root):
        if os.path.isfile(os.path.join(candidate, REL)):
            base = candidate
            break
    if base is None:
        die("fs/susfs.c not found under " + root + " (directly or in common/)")

    path = os.path.join(base, REL)
    text = load(path)
    print("[ABK] kernel tree: " + base)

    if MARKER in text:
        print("[ABK] skip: " + path + " already carries " + MARKER)
    else:
        if "CONFIG_KSU_SUSFS_SUS_PATH" not in text or "CONFIG_KSU_SUSFS_SUS_KSTAT" not in text:
            die("fs/susfs.c lacks the SUS_PATH/SUS_KSTAT blocks; the SUSFS patch is missing")
        if "static int susfs_mark_inode_sus_kstat(" not in text:
            die("fs/susfs.c lacks susfs_mark_inode_sus_kstat(); unexpected SUSFS baseline")
        if "#include <linux/moduleparam.h>" not in text:
            text = sub_one(text, "#include <linux/jump_label.h>\n",
                           "#include <linux/jump_label.h>\n"
                           "#include <linux/moduleparam.h> /* ABK " + MARKER + " */\n",
                           path, "jump_label include")

        text = sub_one(
            text,
            "static inline bool is_i_uid_not_allowed(uid_t i_uid) {\n"
            "\treturn likely(current_uid().val != i_uid);\n"
            "}\n",
            "static inline bool is_i_uid_not_allowed(uid_t i_uid) {\n"
            "\treturn likely(current_uid().val != i_uid);\n"
            "}\n" + PROTO,
            path, "is_i_uid_not_allowed helper")

        text = sub_one(text, DEF_ANCHOR, HELPER_DEF + DEF_ANCHOR, path,
                       "susfs_add_sus_kstat definition")

        text = sub_re_one(
            text, RE_FUSE,
            lambda m: (m.group(1) + HELPER +
                       "(path.mnt, inode, info.target_pathname);\n" + m.group(0)),
            path, "add_sus_path fuse branch")
        text = sub_re_one(
            text, RE_NORMAL,
            lambda m: (m.group(1) + HELPER +
                       "(path.mnt, inode, info.target_pathname);\n" + m.group(0)),
            path, "add_sus_path normal branch")
        text = sub_re_one(
            text, RE_LOOP_FUSE,
            lambda m: (m.group(1) + HELPER +
                       "(path.mnt, inode, cursor->target_pathname);\n" + m.group(0)),
            path, "sus_path_loop fuse branch")
        text = sub_re_one(
            text, RE_LOOP_NORMAL,
            lambda m: (m.group(1) + HELPER +
                       "(path.mnt, inode, cursor->target_pathname);\n" + m.group(0)),
            path, "sus_path_loop normal branch")

        save(path, text)
        print("[ABK] patch: " + path)

    text = load(path)
    checks = [
        ("/* ABK " + MARKER, "marker"), 
        ("static void " + HELPER + "(struct vfsmount *mnt, struct inode *inode,", "prototype"),
        ("static bool abk_sus_path_kstat __read_mostly = true;", "gate default"),
        ("core_param(abk_sus_path_kstat", "boot parameter"),
        ("entry->is_fuse = is_fuse;", "kstat entry fill"),
        ("susfs_mark_inode_sus_kstat(entry->info.target_pathname, entry, false)", "non-sus resolve"),
    ]
    for needle, label in checks:
        if text.count(needle) < 1:
            die("verification failed: " + label + " missing from " + path)
    calls = text.count(HELPER + "(") - 2  # prototype + definition + 4 call sites
    if calls != 4:
        die("verification failed: " + str(calls) + " " + HELPER +
            " call sites (expected 4: add_sus_path x2, sus_path_loop x2)")

    print("[ABK] sus_path kstat autoreg OK: " + MARKER + " (" + HELPER +
          ", 4 call sites, abk_sus_path_kstat=0 kill switch)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
