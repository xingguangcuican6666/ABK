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
    " * The table is built here, in kernel context, so userspace does not have to\n"
    " * issue thousands of separate calls (and cannot leave a half-applied table).\n"
    " */\n"
    "#define ABK_POLICY_MAX (4 * 1024 * 1024)\n"
    "static int susfs_load_policy_file(void)\n"
    "{\n"
    "\tstruct file *fp = NULL;\n"
    "\tloff_t pos = 0;\n"
    "\tchar *buf = NULL;\n"
    "\tchar *line = NULL;\n"
    "\tssize_t len = 0;\n"
    "\tint applied = 0;\n"
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
    "\tlen = kernel_read(fp, buf, ABK_POLICY_MAX - 1, &pos);\n"
    "\tfilp_close(fp, NULL);\n"
    "\tif (len <= 0) {\n"
    "\t\tkvfree(buf);\n"
    "\t\tSUSFS_LOGE(\"policy: read failed: %zd\\n\", len);\n"
    "\t\treturn -EINVAL;\n"
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
    "\t\tint mode = 0;\n"
    "\t\tint uid = 0;\n"
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
    "\t\tif (*target && kern_path(target, LOOKUP_FOLLOW, &kpath) == 0) {\n"
    "\t\t\tif (kpath.dentry && kpath.dentry->d_inode) {\n"
    "\t\t\t\tsusfs_sus_path_uid_upsert(kpath.dentry->d_inode, uid, 1);\n"
    "\t\t\t\tif (mode == 2)\n"
    "\t\t\t\t\tsusfs_loop_queue_path(target, uid, 1);\n"
    "\t\t\t\tapplied++;\n"
    "\t\t\t}\n"
    "\t\t\tpath_put(&kpath);\n"
    "\t\t}\n"
    "\n"
    "\t\tline = next;\n"
    "\t}\n"
    "\n"
    "\tkvfree(buf);\n"
    "\tSUSFS_LOGI(\"policy: applied %d entries\\n\", applied);\n"
    "\treturn applied > 0 ? 0 : -ENOENT;\n"
    "}\n"
    "\n"
)

MAGIC_CHECK = (
    "\t/* ABK " + MARKER + ": magic path -> load the policy file instead of hiding it */\n"
    "\tif (strstr(info.target_pathname, \"" + MAGIC + "\")) {\n"
    "\t\tinfo.err = susfs_load_policy_file();\n"
    "\t\tSUSFS_LOGI(\"policy load via magic path -> ret: %d\\n\", info.err);\n"
    "\t\tgoto out_copy_to_user;\n"
    "\t}\n"
)


def die(msg):
    sys.stderr.write("::error::" + msg + "\n")
    sys.exit(2)


def main():
    if len(sys.argv) != 2:
        die("usage: patch_susfs_policy_file.py <kernel_root>")
    root = sys.argv[1]
    path = os.path.join(root, REL_SRC)
    if not os.path.isfile(path):
        die("expected file not found: " + REL_SRC)

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
