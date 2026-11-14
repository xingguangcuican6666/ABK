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

A failed allocation would still report success: the inode flag is set, the deny entry
is missing, and every caller (including the policy loader) believes the path is
hidden.  Loop replay can repair it on a later app spawn, but "success" is not an
enforcement guarantee.

A second defect: uid < 0 (ksu_susfs add_sus_path <path>, "no per-uid exception")
returned early and left the previous visible-uid entry in the hash, so that uid kept
seeing a path that was re-registered as globally hidden.

This patcher:

  * turns the upsert into `static bool` (true = entry present, false = ENOMEM);
  * converts every early `return;` in it to `return false;` (the argument guards,
    including the ino-0 check, and the kzalloc failure) and adds `return true;`;
  * adds susfs_sus_path_uid_clear() and calls it for uid < 0;
  * makes susfs_add_sus_path_hide_from() return -ENOMEM instead of success when the
    deny entry could not be installed (plain and FUSE branch).

Touched file: fs/susfs.c (after patch_susfs_hide_from_uid.py,
patch_susfs_loop_mode.py, patch_susfs_ino_zero_guard.py and
patch_susfs_policy_file.py).  Re-running is a no-op; missing anchors abort with exit
code 2.
"""
import os
import re
import sys

MARKER = "UPSERT_RESULT_ABK"
REL_SRC = os.path.join("fs", "susfs.c")

OLD_PROTO = "static void susfs_sus_path_uid_upsert(struct inode *inode, int uid, int mode);"
NEW_PROTO = ("static bool susfs_sus_path_uid_upsert(struct inode *inode, int uid, int mode);"
             " /* ABK " + MARKER + " */")

BODY_OLD_HEAD = "static void susfs_sus_path_uid_upsert(struct inode *inode, int uid, int mode) {\n"
BODY_NEW_HEAD = "static bool susfs_sus_path_uid_upsert(struct inode *inode, int uid, int mode) {\n"

# uid < 0 ("no per-uid exception", i.e. ksu_susfs add_sus_path <path>) must revoke the
# entries left by an earlier call that named a visible uid.
CLEAR_FN = (
    "/* ABK " + MARKER + ": drop every per-uid entry belonging to this inode. */\n"
    "static void susfs_sus_path_uid_clear(struct inode *inode)\n"
    "{\n"
    "\tstruct st_susfs_sus_path_hlist *entry;\n"
    "\tstruct hlist_node *tmp;\n"
    "\tstruct st_susfs_sus_path_hlist *doomed[8];\n"
    "\tint i, n;\n"
    "\n"
    "\tif (!inode || inode->i_ino == 0)\n"
    "\t\treturn;\n"
    "\n"
    "\tfor (;;) {\n"
    "\t\tn = 0;\n"
    "\t\tmutex_lock(&susfs_mutex_lock_sus_path_uid);\n"
    "\t\thash_for_each_possible_safe(SUS_PATH_UID_HLIST, entry, tmp, node,\n"
    "\t\t\t\t\t    inode->i_ino) {\n"
    "\t\t\tif (entry->target_ino == inode->i_ino &&\n"
    "\t\t\t    entry->target_dev == inode->i_sb->s_dev) {\n"
    "\t\t\t\thash_del_rcu(&entry->node);\n"
    "\t\t\t\tdoomed[n++] = entry;\n"
    "\t\t\t\tif (n == ARRAY_SIZE(doomed))\n"
    "\t\t\t\t\tbreak;\n"
    "\t\t\t}\n"
    "\t\t}\n"
    "\t\tmutex_unlock(&susfs_mutex_lock_sus_path_uid);\n"
    "\t\tif (!n)\n"
    "\t\t\tbreak;\n"
    "\t\tsynchronize_rcu();\n"
    "\t\tfor (i = 0; i < n; i++)\n"
    "\t\t\tkfree(doomed[i]);\n"
    "\t}\n"
    "}\n"
    "\n"
)

GUARD_INO0 = "\tif (!inode || inode->i_ino == 0 || uid < 0)\n\t\treturn false;\n"
GUARD_PLAIN = "\tif (!inode || uid < 0)\n\t\treturn false;\n"
GUARD_REVOKE = (
    "\n\tif (uid < 0) {\n"
    "\t\t/* ABK " + MARKER + ": revoke every per-uid entry for this inode */\n"
    "\t\tsusfs_sus_path_uid_clear(inode);\n"
    "\t\treturn true;\n"
    "\t}\n"
)

HIDE_FUSE_RE = r"(?P<ind>\t+)susfs_sus_path_uid_upsert\(&fi->inode, info\.uid, 1\);\n"
HIDE_PLAIN_RE = r"(?P<ind>\t+)susfs_sus_path_uid_upsert\(inode, info\.uid, 1\);\n"


def die(msg):
    sys.stderr.write("::error::" + msg + "\n")
    sys.exit(2)


def resolve(root):
    """The kernel workflow passes the repo root; the tree is usually in common/."""
    for base in (os.path.join(root, "common"), root):
        p = os.path.join(base, REL_SRC)
        if os.path.isfile(p):
            return p
    die("expected file not found: " + REL_SRC + " (looked in <root>/common and <root>)")


def patch_upsert_body(text):
    """bool upsert: every early return is a failure, plus the uid<0 revoke path."""
    if text.count(BODY_OLD_HEAD) != 1:
        die("upsert definition head matched %d times (expected 1)" % text.count(BODY_OLD_HEAD))

    start = text.index(BODY_OLD_HEAD)
    text = text[:start] + CLEAR_FN + text[start:]
    start = text.index(BODY_OLD_HEAD)
    end = text.index("\n}\n", start) + len("\n}\n")
    body = text[start:end]

    n_returns = body.count("return;")
    if n_returns < 2:
        die("upsert body has %d 'return;' statements (expected at least 2)" % n_returns)
    if "return true;" in body:
        die("upsert body already returns true - unexpected")

    body = body.replace("return;", "return false;")
    close = body.rindex("\n}\n")
    body = body[:close] + "\n\treturn true; /* ABK " + MARKER + " */" + body[close:]
    body = body.replace(BODY_OLD_HEAD, BODY_NEW_HEAD, 1)

    # The converted guard still rejects uid < 0, which would make the revoke branch
    # below unreachable.  Drop that clause from the guard and handle it explicitly.
    guard = None
    for candidate in (GUARD_INO0, GUARD_PLAIN):
        if body.count(candidate) == 1:
            guard = candidate
            break
    if guard is None:
        die("upsert argument guard not found after the return conversion")
    relaxed = guard.replace(" || uid < 0", "")
    if relaxed == guard:
        die("upsert argument guard does not contain the uid < 0 clause")
    body = body.replace(guard, relaxed + GUARD_REVOKE, 1)
    return text[:start] + body + text[end:]


def wrap_hide_calls(text):
    """Report -ENOMEM instead of success when the deny entry cannot be installed."""
    for pattern, target in ((HIDE_FUSE_RE, "&fi->inode"), (HIDE_PLAIN_RE, "inode")):
        matches = list(re.finditer(pattern, text))
        if len(matches) != 1:
            die("hide_from call anchor matched %d times (expected 1)" % len(matches))
        m = matches[0]
        ind = m.group("ind")
        body = (
            ind + "/* ABK " + MARKER + ": a failed deny-entry install is not success */\n"
            + ind + "if (!susfs_sus_path_uid_upsert(" + target + ", info.uid, 1)) {\n"
            + ind + "\tSUSFS_LOGE(\"sus_path uid entry allocation failed\\n\");\n"
            + ind + "\tinfo.err = -ENOMEM;\n"
            + ind + "\tgoto out_path_put_path;\n"
            + ind + "}\n"
        )
        text = text[:m.start()] + body + text[m.end():]
    return text


def main():
    if len(sys.argv) != 2:
        die("usage: patch_susfs_upsert_result.py <kernel_root>")
    path = resolve(sys.argv[1])
    with open(path, "r", encoding="utf-8", newline="") as fh:
        text = fh.read()

    if "static bool susfs_sus_path_uid_upsert" in text and ("return true; /* ABK " + MARKER) in text:
        print("skip (already patched): " + REL_SRC)
        return

    if text.count(OLD_PROTO) == 1:
        text = text.replace(OLD_PROTO, NEW_PROTO, 1)
    elif text.count(NEW_PROTO) != 1:
        die("upsert prototype anchor not found in " + REL_SRC)

    text = patch_upsert_body(text)
    text = wrap_hide_calls(text)

    with open(path, "w", encoding="utf-8", newline="") as fh:
        fh.write(text)
    print("patch: upsert result propagation (" + MARKER + ")")


if __name__ == "__main__":
    main()
