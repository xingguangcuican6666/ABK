#!/usr/bin/env python3
"""ABK: load the whole per-target policy in ONE supercall (kernel side).

Usage:
    patch_susfs_policy_file.py <kernel_root>

Why: the userspace sync issues one ksu_susfs call per (path, uid) pair - 3163
hide_from calls plus 557 uid_from calls on this device.  That is slow, it has to
run from a settled process context, and any failure in the middle (stale lock,
supercall not ready, overlapping syncs) leaves a half-applied table that looks
like "hiding is off".

This patcher adds a kernel-side loader:

    ksu_susfs add_sus_path_hide_from /data/adb/hma_susfs/.abk-policy 0

When the target pathname ends with "/.abk-policy" the handler does not touch that
path at all; it reads /data/adb/hma_susfs/policy.tsv and applies every line:

    <mode>|<uid>|<path>          mode 1 = hide from uid, mode 2 = hide + loop

so the whole table is built inside one syscall, in kernel context.

Touched file:
    fs/susfs.c   (inserts susfs_load_policy_file() and the magic-path check)

Requires patch_susfs_hide_from_uid.py and patch_susfs_loop_mode.py to have run
first (the loader calls susfs_sus_path_uid_upsert() and susfs_loop_queue_path()).
Re-running is a no-op; a missing anchor aborts with exit code 2.
"""
import os
import sys

MARKER = "POLICY_FILE_ABK"
POLICY_PATH = "/data/adb/hma_susfs/policy.tsv"
MAGIC = "/.abk-policy"

REL_SRC = os.path.join("fs", "susfs.c")

HANDLER_ANCHOR = "void susfs_add_sus_path_hide_from(void __user **user_info) {\n"

COPY_BLOCK = (
    "\tif (copy_from_user(&info, (struct st_susfs_sus_path __user*)*user_info, sizeof(info))) {\n"
    "\t\tinfo.err = -EFAULT;\n"
    "\t\tgoto out_copy_to_user;\n"
    "\t}\n"
)

LOADER = (
    "/* ABK " + MARKER + ": apply a whole policy file in one supercall.\n"
    " *\n"
    " * Every line is <mode>|<uid>|<path>:\n"
    " *   mode 1 -> hide the path from that uid only (hide_from semantics)\n"
    " *   mode 2 -> same, and additionally queue it for the loop worker\n"
    " *\n"
    " * A rule is only counted as applied when the inode is flagged AND the uid table\n"
    " * entry exists - that is what susfs_is_inode_sus_path() actually tests, so a\n"
    " * table entry alone would report success without enforcing anything.\n"
    " * mode 2 entries are queued before the inode lookup: the loop worker is\n"
    " * path-driven and must keep retrying even while the path does not exist yet.\n"
    " */\n"
    "#define ABK_POLICY_MAX (4 * 1024 * 1024)\n"
    "static DEFINE_MUTEX(susfs_mutex_lock_policy_load);\n"
    "\n"
    "static void abk_policy_flag_inode(struct inode *inode)\n"
    "{\n"
    "\tif (!inode || !inode->i_mapping)\n"
    "\t\treturn;\n"
    "\tif (inode->i_sb->s_magic == FUSE_SUPER_MAGIC) {\n"
    "\t\tstruct fuse_inode *fi = get_fuse_inode(inode);\n"
    "\t\tif (fi && fi->inode.i_mapping)\n"
    "\t\t\tset_bit(AS_FLAGS_SUS_PATH_HIDE_FROM, &fi->inode.i_mapping->flags);\n"
    "\t}\n"
    "\tset_bit(AS_FLAGS_SUS_PATH_HIDE_FROM, &inode->i_mapping->flags);\n"
    "}\n"
    "\n"
    "static int susfs_load_policy_file(void)\n"
    "{\n"
    "\tstruct file *fp = NULL;\n"
    "\tloff_t pos = 0;\n"
    "\tchar *buf = NULL;\n"
    "\tchar *line = NULL;\n"
    "\tssize_t len = 0;\n"
    "\tint applied = 0;\n"
    "\tint deferred = 0; /* path absent: nothing to hide yet */\n"
    "\tint failed = 0;   /* rule exists but could not be installed */\n"
    "\n"
    "\tfp = filp_open(\"" + POLICY_PATH + "\", O_RDONLY, 0);\n"
    "\tif (IS_ERR(fp)) {\n"
    "\t\tSUSFS_LOGE(\"policy: open '%s' failed: %ld\\n\", \"" + POLICY_PATH + "\", PTR_ERR(fp));\n"
    "\t\treturn -ENOENT;\n"
    "\t}\n"
    "\n"
    "\tbuf = kvmalloc(ABK_POLICY_MAX, GFP_KERNEL);\n"
    "\tif (!buf) {\n"
    "\t\tfilp_close(fp, NULL);\n"
    "\t\treturn -ENOMEM;\n"
    "\t}\n"
    "\n"
    "\tlen = kernel_read(fp, buf, ABK_POLICY_MAX, &pos);\n"
    "\tfilp_close(fp, NULL);\n"
    "\tif (len <= 0) {\n"
    "\t\tkvfree(buf);\n"
    "\t\tSUSFS_LOGE(\"policy: read failed: %zd\\n\", len);\n"
    "\t\treturn -EINVAL;\n"
    "\t}\n"
    "\tif (len == ABK_POLICY_MAX) {\n"
    "\t\t/* A full buffer may mean the file continues; refuse rather than apply a prefix. */\n"
    "\t\tkvfree(buf);\n"
    "\t\tSUSFS_LOGE(\"policy: file larger than %d bytes, refusing\\n\", ABK_POLICY_MAX);\n"
    "\t\treturn -EFBIG;\n"
    "\t}\n"
    "\tbuf[len] = '\\0';\n"
    "\n"
    "\tline = buf;\n"
    "\twhile (line && *line) {\n"
    "\t\tchar *nl = strchr(line, '\\n');\n"
    "\t\tchar *bar1 = NULL;\n"
    "\t\tchar *bar2 = NULL;\n"
    "\t\tchar *target = NULL;\n"
    "\t\tchar *next = NULL;\n"
    "\t\tstruct path kpath;\n"
    "\t\tstruct inode *ino_target = NULL;\n"
    "\t\tint mode = 0;\n"
    "\t\tint uid = 0;\n"
    "\t\tint kerr = 0;\n"
    "\n"
    "\t\tif (nl) {\n"
    "\t\t\t*nl = '\\0';\n"
    "\t\t\tnext = nl + 1;\n"
    "\t\t}\n"
    "\n"
    "\t\tbar1 = strchr(line, '|');\n"
    "\t\tbar2 = bar1 ? strchr(bar1 + 1, '|') : NULL;\n"
    "\t\tif (!bar1 || !bar2) {\n"
    "\t\t\tline = next;\n"
    "\t\t\tcontinue;\n"
    "\t\t}\n"
    "\n"
    "\t\t*bar1 = '\\0';\n"
    "\t\t*bar2 = '\\0';\n"
    "\t\ttarget = bar2 + 1;\n"
    "\t\tmode = (int)simple_strtoul(line, NULL, 10);\n"
    "\t\tuid = (int)simple_strtoul(bar1 + 1, NULL, 10);\n"
    "\n"
    "\t\t/* The loop list is path-driven: register it before touching the inode. */\n"
    "\t\tif (*target && mode == 2)\n"
    "\t\t\tsusfs_loop_queue_path(target, uid, 1);\n"
    "\n"
    "\t\tkerr = kern_path(target, LOOKUP_FOLLOW, &kpath);\n"
    "\t\tif (kerr == 0) {\n"
    "\t\t\tino_target = kpath.dentry ? kpath.dentry->d_inode : NULL;\n"
    "\t\t\t/* A FUSE inode carries i_ino == 0; the real number lives in the\n"
    "\t\t\t * fuse_inode, exactly like the CLI handler does it.  Using 0 would\n"
    "\t\t\t * create a wildcard entry, so resolve it before the upsert.\n"
    "\t\t\t */\n"
    "\t\t\tif (ino_target && ino_target->i_sb->s_magic == FUSE_SUPER_MAGIC) {\n"
    "\t\t\t\tstruct fuse_inode *fi = get_fuse_inode(ino_target);\n"
    "\t\t\t\tino_target = (fi && fi->inode.i_mapping) ? &fi->inode : NULL;\n"
    "\t\t\t}\n"
    "\t\t\tif (ino_target && ino_target->i_ino != 0 &&\n"
    "\t\t\t    susfs_sus_path_uid_upsert(ino_target, uid, 1)) {\n"
    "\t\t\t\tabk_policy_flag_inode(kpath.dentry->d_inode);\n"
    "\t\t\t\tapplied++;\n"
    "\t\t\t} else {\n"
    "\t\t\t\tfailed++;\n"
    "\t\t\t}\n"
    "\t\t\tpath_put(&kpath);\n"
    "\t\t} else if (kerr == -ENOENT) {\n"
    "\t\t\tdeferred++;\n"
    "\t\t} else {\n"
    "\t\t\tfailed++;\n"
    "\t\t}\n"
    "\n"
    "\t\tline = next;\n"
    "\t}\n"
    "\n"
    "\tkvfree(buf);\n"
    "\tSUSFS_LOGI(\"policy: applied %d, deferred %d, failed %d\\n\", applied, deferred, failed);\n"
    "\tif (failed)\n"
    "\t\treturn -EIO;\n"
    "\treturn (applied > 0 || deferred > 0) ? 0 : -ENOENT;\n"
    "}\n"
    "\n"
)
MAGIC_CHECK = (
    "\t/* ABK " + MARKER + ": magic path -> load the policy file instead of hiding it */\n"
    "\tif (strstr(info.target_pathname, \"" + MAGIC + "\")) {\n"
    "\t\t/* ABK: serialise the whole load - two concurrent reloads would interleave\n"
    "\t\t * their managed-uid registrations.  The load sleeps (read + synchronize_rcu),\n"
    "\t\t * so a mutex is required, and every path below must drop it. */\n"
    "\t\tmutex_lock(&susfs_mutex_lock_policy_load);\n"
    "\t\tinfo.err = susfs_load_policy_file();\n"
    "\t\tmutex_unlock(&susfs_mutex_lock_policy_load);\n"
    "\t\tSUSFS_LOGI(\"policy load via magic path -> ret: %d\\n\", info.err);\n"
    "\t\tgoto out_copy_to_user;\n"
    "\t}\n"
)


def die(msg):
    sys.stderr.write("::error::" + msg + "\n")
    sys.exit(2)


def resolve(root):
    """The build passes the repo root; the kernel tree is usually <root>/common."""
    for base in (os.path.join(root, "common"), root):
        candidate = os.path.join(base, REL_SRC)
        if os.path.isfile(candidate):
            return candidate
    die("expected file not found: " + REL_SRC + " (looked in <root>/common and <root>)")


def main():
    if len(sys.argv) != 2:
        die("usage: patch_susfs_policy_file.py <kernel_root>")
    root = sys.argv[1]
    path = resolve(root)

    with open(path, "r", encoding="utf-8", newline="") as fh:
        text = fh.read()

    if MARKER in text:
        print("skip (already patched): " + REL_SRC)
        return

    handler_at = text.find(HANDLER_ANCHOR)
    if handler_at < 0:
        die("anchor '" + HANDLER_ANCHOR.strip() + "' not found in " + REL_SRC)

    copy_at = text.find(COPY_BLOCK, handler_at)
    if copy_at < 0:
        die("copy_from_user block not found inside susfs_add_sus_path_hide_from")

    insert_at = copy_at + len(COPY_BLOCK)

    # the loader must be defined before the handler that calls it
    text = text[:handler_at] + LOADER + text[handler_at:]
    insert_at += len(LOADER)

    text = text[:insert_at] + MAGIC_CHECK + text[insert_at:]

    # forward declaration: susfs_loop_queue_path() is defined above the handler by
    # patch_susfs_loop_mode.py, but the upsert helper only has a prototype there.
    proto = "static void susfs_sus_path_uid_upsert(struct inode *inode, int uid, int mode);"
    if proto in text and text.find(proto) > text.find("static int susfs_load_policy_file(void)"):
        die("susfs_sus_path_uid_upsert prototype is missing before the loader")

    with open(path, "w", encoding="utf-8", newline="") as fh:
        fh.write(text)
    print("patch: " + REL_SRC)


if __name__ == "__main__":
    main()
