#!/usr/bin/env python3
"""ABK: make the sus_path loop carry per-uid semantics (mode).

Usage:
    patch_susfs_loop_mode.py <kernel_root>

Problem: sus_path entries live in the inode (AS_FLAGS_* + SUS_PATH_UID_HLIST),
so an evicted inode or a re-created app data dir loses the hiding even though
the configuration still lists the path.  sus_path_loop stores path strings and
re-applies them on every app spawn, but it only knows "visible to <uid>" -
add_sus_path_loop() always sets AS_FLAGS_SUS_PATH and upserts mode 0 - so the
per-target hide semantics (mode 1, hide from one uid only) cannot be expressed.

This patcher gives the loop entries a mode and teaches the worker both
semantics, so hide_from() can be queued in the same path-driven list:

    mode 0 : AS_FLAGS_SUS_PATH        + upsert(uid, 0)   visible to uid
    mode 1 : AS_FLAGS_SUS_PATH_HIDE_FROM + upsert(uid, 1) hidden from uid

susfs_add_sus_path_hide_from() queues (path, uid, 1) after a successful add, so
the next app spawn re-applies it from the path - no inode dependency.

Run after 60_add_per_uid_sus_path.patch and patch_susfs_hide_from_uid.py.
Re-running is a no-op; missing anchors abort with exit code 2.
"""
import os
import sys

MARKER = "SUS_PATH_LOOP_MODE_ABK"

REL_SRC = os.path.join("fs", "susfs.c")
REL_HDR = os.path.join("include", "linux", "susfs.h")

STRUCT_OLD = (
    "struct st_susfs_sus_path_list {\n"
    "\tstruct list_head                        list;\n"
    "\tstruct st_susfs_sus_path                info;\n"
    "\tchar                                    target_pathname[SUSFS_MAX_LEN_PATHNAME];\n"
    "};\n"
)
STRUCT_NEW = (
    "struct st_susfs_sus_path_list {\n"
    "\tstruct list_head                        list;\n"
    "\tstruct st_susfs_sus_path                info;\n"
    "\tchar                                    target_pathname[SUSFS_MAX_LEN_PATHNAME];\n"
    "\tint                                     mode; /* ABK " + MARKER + ": 0 = visible to uid, 1 = hidden from uid */\n"
    "};\n"
)

LOOP_UID_LINE = "\tnew_list->info.uid = info.uid;\n"
LOOP_MODE_ADD = "\tnew_list->mode = 0; /* ABK " + MARKER + " */\n"

HELPER_ANCHOR = "void susfs_add_sus_path_hide_from(void __user **user_info) {\n"

HELPER = (
    "/* ABK " + MARKER + ": queue a path for the loop worker, deduplicated so a\n"
    " * repeated sync does not grow the list without bound.\n"
    " */\n"
    "static void susfs_loop_queue_path(const char *path, int uid, int mode)\n"
    "{\n"
    "\tstruct st_susfs_sus_path_list *new_list = NULL, *cursor = NULL;\n"
    "\n"
    "\tif (!path || !*path)\n"
    "\t\treturn;\n"
    "\n"
    "\tmutex_lock(&susfs_mutex_lock_sus_path);\n"
    "\tlist_for_each_entry(cursor, &LH_SUS_PATH_LOOP, list) {\n"
    "\t\tif (cursor->mode == mode && cursor->info.uid == uid &&\n"
    "\t\t    !strcmp(cursor->target_pathname, path)) {\n"
    "\t\t\tmutex_unlock(&susfs_mutex_lock_sus_path);\n"
    "\t\t\treturn;\n"
    "\t\t}\n"
    "\t}\n"
    "\n"
    "\tnew_list = kzalloc(sizeof(*new_list), GFP_KERNEL);\n"
    "\tif (!new_list) {\n"
    "\t\tmutex_unlock(&susfs_mutex_lock_sus_path);\n"
    "\t\treturn;\n"
    "\t}\n"
    "\tstrscpy(new_list->info.target_pathname, path, SUSFS_MAX_LEN_PATHNAME - 1);\n"
    "\tstrscpy(new_list->target_pathname, path, SUSFS_MAX_LEN_PATHNAME - 1);\n"
    "\tnew_list->info.uid = uid;\n"
    "\tnew_list->mode = mode;\n"
    "\tINIT_LIST_HEAD(&new_list->list);\n"
    "\tlist_add_tail_rcu(&new_list->list, &LH_SUS_PATH_LOOP);\n"
    "\tmutex_unlock(&susfs_mutex_lock_sus_path);\n"
    "\tSUSFS_LOGI(\"loop queue: path '%s', uid %d, mode %d\\n\", path, uid, mode);\n"
    "}\n"
    "\n"
)

# hide_from success sites -> queue mode 1
QUEUE_SITES = (
    ("\t\tsusfs_sus_path_uid_upsert(&fi->inode, info.uid, 1);\n",
     "\t\tsusfs_loop_queue_path(info.target_pathname, info.uid, 1);\n"),
    ("\tsusfs_sus_path_uid_upsert(inode, info.uid, 1);\n",
     "\tsusfs_loop_queue_path(info.target_pathname, info.uid, 1);\n"),
)

# worker: branch on the entry mode
WORKER_FUSE_OLD = (
    "\t\t\t\tset_bit(AS_FLAGS_SUS_PATH, &fi->inode.i_mapping->flags);\n"
    "\t\t\t\tset_bit(AS_FLAGS_SUS_PATH, &inode->i_mapping->flags);\n"
    "\t\t\t\tsusfs_sus_path_uid_upsert(&fi->inode, cursor->info.uid, 0);\n"
)
WORKER_FUSE_NEW = (
    "\t\t\t\t/* ABK " + MARKER + ": loop entries carry the per-uid mode */\n"
    "\t\t\t\tif (cursor->mode == 1) {\n"
    "\t\t\t\t\tset_bit(AS_FLAGS_SUS_PATH_HIDE_FROM, &fi->inode.i_mapping->flags);\n"
    "\t\t\t\t\tset_bit(AS_FLAGS_SUS_PATH_HIDE_FROM, &inode->i_mapping->flags);\n"
    "\t\t\t\t\tsusfs_sus_path_uid_upsert(&fi->inode, cursor->info.uid, 1);\n"
    "\t\t\t\t} else {\n"
    "\t\t\t\t\tset_bit(AS_FLAGS_SUS_PATH, &fi->inode.i_mapping->flags);\n"
    "\t\t\t\t\tset_bit(AS_FLAGS_SUS_PATH, &inode->i_mapping->flags);\n"
    "\t\t\t\t\tsusfs_sus_path_uid_upsert(&fi->inode, cursor->info.uid, 0);\n"
    "\t\t\t\t}\n"
)

WORKER_PLAIN_OLD = (
    "\t\t\t\tset_bit(AS_FLAGS_SUS_PATH, &inode->i_mapping->flags);\n"
    "\t\t\t\tsusfs_sus_path_uid_upsert(inode, cursor->info.uid, 0);\n"
)
WORKER_PLAIN_NEW = (
    "\t\t\t\t/* ABK " + MARKER + ": loop entries carry the per-uid mode */\n"
    "\t\t\t\tif (cursor->mode == 1) {\n"
    "\t\t\t\t\tset_bit(AS_FLAGS_SUS_PATH_HIDE_FROM, &inode->i_mapping->flags);\n"
    "\t\t\t\t\tsusfs_sus_path_uid_upsert(inode, cursor->info.uid, 1);\n"
    "\t\t\t\t} else {\n"
    "\t\t\t\t\tset_bit(AS_FLAGS_SUS_PATH, &inode->i_mapping->flags);\n"
    "\t\t\t\t\tsusfs_sus_path_uid_upsert(inode, cursor->info.uid, 0);\n"
    "\t\t\t\t}\n"
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
        die("usage: patch_susfs_loop_mode.py <kernel_root>")
    root = sys.argv[1]
    base = None
    for candidate in (os.path.join(root, "common"), root):
        if all(os.path.isfile(os.path.join(candidate, rel)) for rel in (REL_SRC, REL_HDR)):
            base = candidate
            break
    if base is None:
        die("kernel sources not found under " + root)

    src = os.path.join(base, REL_SRC)
    hdr = os.path.join(base, REL_HDR)

    h = load(hdr)
    if MARKER not in h:
        if STRUCT_OLD not in h:
            die("anchor 'st_susfs_sus_path_list' missing from " + hdr)
        save(hdr, h.replace(STRUCT_OLD, STRUCT_NEW, 1))

    s = load(src)
    if MARKER in s:
        print("[ABK] skip: " + src + " already carries " + MARKER)
        return 0

    if LOOP_UID_LINE not in s:
        die("anchor 'new_list->info.uid' missing from " + src + "; run patch 60 first")
    # only the add_sus_path_loop() copy carries this exact context
    idx = s.index(LOOP_UID_LINE)
    s = s[:idx] + LOOP_UID_LINE + LOOP_MODE_ADD + s[idx + len(LOOP_UID_LINE):]

    if HELPER_ANCHOR not in s:
        die("anchor 'susfs_add_sus_path_hide_from()' missing from " + src +
            "; run patch_susfs_hide_from_uid.py first")
    s = s.replace(HELPER_ANCHOR, HELPER + HELPER_ANCHOR, 1)

    for old, new in QUEUE_SITES:
        if old not in s:
            die("anchor '" + old.strip() + "' missing from " + src)
        s = s.replace(old, old + new, 1)

    if WORKER_FUSE_OLD not in s:
        die("worker fuse block not found in " + src)
    s = s.replace(WORKER_FUSE_OLD, WORKER_FUSE_NEW, 1)
    if WORKER_PLAIN_OLD not in s:
        die("worker plain block not found in " + src)
    s = s.replace(WORKER_PLAIN_OLD, WORKER_PLAIN_NEW, 1)

    save(src, s)

    if load(hdr).count(MARKER) < 1 or load(src).count(MARKER) < 4:
        die("verification failed: " + MARKER + " markers missing")
    print("[ABK] SUSFS loop mode OK: " + MARKER +
          " (loop entries carry mode; hide_from is queued as mode 1 and re-applied on app spawn)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
