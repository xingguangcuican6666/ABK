#!/usr/bin/env python3
"""ABK: make the per-uid sus_path upsert report failure, and propagate it.

Usage:
    patch_susfs_upsert_result.py <kernel_root>

Problem (security review, 2026-11-14):

    static void susfs_sus_path_uid_upsert(...)      /* returns nothing */
    {
            ...
            entry = kzalloc(sizeof(*entry), GFP_KERNEL);
            if (!entry) {
                    SUSFS_LOGE("failed allocating sus_path uid entry\n");
                    return;                          /* swallowed */
            }
            ...
    }

    void susfs_add_sus_path_hide_from(void __user **user_info)
    {
            ...
            susfs_sus_path_uid_upsert(inode, info.uid, 1);
            info.err = 0;                            /* success anyway */
    }

So a failed allocation would still report success: the inode flag is set, the deny
entry is missing, and every caller (including the policy loader) believes the path
is hidden.  Loop replay can repair it on a later app spawn, but "success" is not an
enforcement guarantee.

This patcher:

  * turns the upsert into `static bool` (true = entry present, false = ENOMEM);
  * makes susfs_add_sus_path_hide_from() return -ENOMEM instead of success when the
    deny entry could not be installed (both the plain and the FUSE branch);
  * makes susfs_load_policy_file() count failures, log them, and return -EIO when
    any entry failed, so the magic-path call cannot report "applied N" on a partial
    publish.

Touched file: fs/susfs.c (after patch_susfs_hide_from_uid.py,
patch_susfs_loop_mode.py and patch_susfs_policy_file.py).
Re-running is a no-op; missing anchors abort with exit code 2.
"""
import os
import re
import sys

MARKER = "UPSERT_RESULT_ABK"
REL_SRC = os.path.join("fs", "susfs.c")

OLD_PROTO = "static void susfs_sus_path_uid_upsert(struct inode *inode, int uid, int mode);"
NEW_PROTO = "static bool susfs_sus_path_uid_upsert(struct inode *inode, int uid, int mode); /* ABK " + MARKER + " */"

OLD_DEF = "static void susfs_sus_path_uid_upsert(struct inode *inode, int uid, int mode) {\n"
NEW_DEF = "static bool susfs_sus_path_uid_upsert(struct inode *inode, int uid, int mode) {\n"

OLD_GUARD = "\tif (!inode || uid < 0)\n\t\treturn;\n"
NEW_GUARD = "\tif (!inode || uid < 0)\n\t\treturn false;\n"

OLD_ALLOC = (
    "\t\tif (!entry) {\n"
    "\t\t\tSUSFS_LOGE(\"failed allocating sus_path uid entry\\n\");\n"
    "\t\t\tmutex_unlock(&susfs_mutex_lock_sus_path_uid);\n"
    "\t\t\treturn;\n"
    "\t\t}\n"
)
NEW_ALLOC = (
    "\t\tif (!entry) {\n"
    "\t\t\tSUSFS_LOGE(\"failed allocating sus_path uid entry\\n\");\n"
    "\t\t\tmutex_unlock(&susfs_mutex_lock_sus_path_uid);\n"
    "\t\t\treturn false;\n"
    "\t\t}\n"
)

# Tail: the function ends with mutex_unlock(&susfs_mutex_lock_sus_path_uid) + "}",
# not necessarily right after the log line, so insert before the closing brace that
# follows the unlock inside this function.
TAIL_NEEDLE = "\tmutex_unlock(&susfs_mutex_lock_sus_path_uid);\n}\n"
TAIL_PATCH = "\tmutex_unlock(&susfs_mutex_lock_sus_path_uid);\n\treturn true; /* ABK " + MARKER + " */\n}\n"

# hide_from handler: FUSE branch (indented with two tabs) and the plain branch
HIDE_FUSE_OLD = "\t\tsusfs_sus_path_uid_upsert(&fi->inode, info.uid, 1);\n"
HIDE_FUSE_RE = r"(?P<ind>\t+)susfs_sus_path_uid_upsert\(&fi->inode, info\.uid, 1\);\n"
HIDE_PLAIN_RE = r"(?P<ind>\t+)susfs_sus_path_uid_upsert\(inode, info\.uid, 1\);\n"
HIDE_FUSE_NEW = (
    "\t\tif (!susfs_sus_path_uid_upsert(&fi->inode, info.uid, 1)) {\n"
    "\t\t\tSUSFS_LOGE(\"sus_path uid entry allocation failed (fuse)\\n\");\n"
    "\t\t\tinfo.err = -ENOMEM;\n"
    "\t\t\tgoto out_path_put_path;\n"
    "\t\t}\n"
)
HIDE_PLAIN_OLD = "\tsusfs_sus_path_uid_upsert(inode, info.uid, 1);\n"
HIDE_PLAIN_NEW = (
    "\tif (!susfs_sus_path_uid_upsert(inode, info.uid, 1)) {\n"
    "\t\tSUSFS_LOGE(\"sus_path uid entry allocation failed\\n\");\n"
    "\t\tinfo.err = -ENOMEM;\n"
    "\t\tgoto out_path_put_path;\n"
    "\t}\n"
)

# policy loader: count only what really landed, and fail the call when it did not.
# Indentation is captured so the same code works for the 3/4-tab layout of the real
# tree and for any reformatting.
POLICY_RE = re.compile(
    r"(?P<i1>\t+)if \(kpath\.dentry && kpath\.dentry->d_inode\) \{\n"
    r"(?P<i2>\t+)susfs_sus_path_uid_upsert\(kpath\.dentry->d_inode, uid, 1\);\n"
    r"(?P<i3>\t+)if \(mode == 2\)\n"
    r"(?P<i4>\t+)susfs_loop_queue_path\(target, uid, 1\);\n"
    r"(?P<i5>\t+)applied\+\+;\n"
    r"(?P<i6>\t+)\}\n"
)


def patch_policy_upsert(text):
    matches = list(POLICY_RE.finditer(text))
    if len(matches) != 1:
        die("policy loader upsert matched %d times (expected 1)" % len(matches))
    m = matches[0]
    i1, i2, i3, i4, i5, i6 = (m.group("i%d" % n) for n in range(1, 7))
    body = (
        i1 + "if (kpath.dentry && kpath.dentry->d_inode) {\n"
        + i2 + "if (!susfs_sus_path_uid_upsert(kpath.dentry->d_inode, uid, 1)) {\n"
        + i2 + "\t/* ABK " + MARKER + ": never report a publish we did not install */\n"
        + i2 + "\tfailed++;\n"
        + i2 + "} else {\n"
        + i3 + "if (mode == 2)\n"
        + i4 + "susfs_loop_queue_path(target, uid, 1);\n"
        + i5 + "applied++;\n"
        + i2 + "}\n"
        + i6 + "}\n"
    )
    return text[:m.start()] + body + text[m.end():]

POLICY_DECL_OLD = "\tint applied = 0;\n"
POLICY_DECL_NEW = "\tint applied = 0;\n\tint failed = 0; /* ABK " + MARKER + " */\n"

POLICY_RET_OLD = (
    "\tkvfree(buf);\n"
    "\tSUSFS_LOGI(\"policy: applied %d entries\\n\", applied);\n"
    "\treturn applied > 0 ? 0 : -ENOENT;\n"
)
POLICY_RET_NEW = (
    "\tkvfree(buf);\n"
    "\tSUSFS_LOGI(\"policy: applied %d entries, %d failed\\n\", applied, failed);\n"
    "\tif (failed)\n"
    "\t\treturn -EIO; /* ABK " + MARKER + ": partial publish is a failure */\n"
    "\treturn applied > 0 ? 0 : -ENOENT;\n"
)


def die(msg):
    sys.stderr.write("::error::" + msg + "\n")
    sys.exit(2)


def resolve(root):
    for base in (os.path.join(root, "common"), root):
        p = os.path.join(base, REL_SRC)
        if os.path.isfile(p):
            return p
    die("expected file not found: " + REL_SRC)


def replace_once_re(text, pattern, label):
    """Wrap a hide_from upsert call in a failure check, keeping its indentation."""
    global _repl
    matches = list(re.finditer(pattern, text))
    if len(matches) != 1:
        die("anchor '%s' matched %d times (expected 1)" % (label, len(matches)))
    m = matches[0]
    ind = m.group("ind")
    body = (
        ind + "/* ABK " + MARKER + ": a failed deny-entry install is not success */\n"
        + ind + "if (!susfs_sus_path_uid_upsert(" + ("&fi->inode" if "fi->inode" in m.group(0) else "inode") + ", info.uid, 1)) {\n"
        + ind + "\tSUSFS_LOGE(\"sus_path uid entry allocation failed\\n\");\n"
        + ind + "\tinfo.err = -ENOMEM;\n"
        + ind + "\tgoto out_path_put_path;\n"
        + ind + "}\n"
    )
    return text[:m.start()] + body + text[m.end():]

def replace_once(text, old, new, label):
    n = text.count(old)
    if n != 1:
        die("anchor '%s' matched %d times (expected 1)" % (label, n))
    return text.replace(old, new, 1)


def main():
    if len(sys.argv) != 2:
        die("usage: patch_susfs_upsert_result.py <kernel_root>")
    path = resolve(sys.argv[1])
    with open(path, "r", encoding="utf-8", newline="") as fh:
        text = fh.read()

    if MARKER in text:
        print("skip (already patched): " + REL_SRC)
        return

    text = replace_once(text, OLD_PROTO, NEW_PROTO, "upsert prototype")
    text = replace_once(text, OLD_DEF, NEW_DEF, "upsert definition")
    text = replace_once(text, OLD_GUARD, NEW_GUARD, "upsert guard")
    text = replace_once(text, OLD_ALLOC, NEW_ALLOC, "upsert alloc failure")
    text = replace_once(text, TAIL_NEEDLE, TAIL_PATCH, "upsert success tail")
    text = replace_once_re(text, HIDE_FUSE_RE, "hide_from fuse call")
    text = replace_once_re(text, HIDE_PLAIN_RE, "hide_from plain call")
    text = replace_once(text, POLICY_DECL_OLD, POLICY_DECL_NEW, "policy loader counter")
    text = patch_policy_upsert(text)
    text = replace_once(text, POLICY_RET_OLD, POLICY_RET_NEW, "policy loader return")

    with open(path, "w", encoding="utf-8", newline="") as fh:
        fh.write(text)
    print("patch: upsert result propagation (" + MARKER + ")")


if __name__ == "__main__":
    main()
