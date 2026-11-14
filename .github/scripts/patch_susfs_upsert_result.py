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

# Guard / alloc failure / success tail are rewritten on the function body slice:
# the body has exactly two "return;" statements (bad args, kzalloc failure) and must
# end with "return true;", so exact indentation no longer matters.
BODY_OLD_HEAD = "static void susfs_sus_path_uid_upsert(struct inode *inode, int uid, int mode) {\n"
BODY_NEW_HEAD = "static bool susfs_sus_path_uid_upsert(struct inode *inode, int uid, int mode) {\n"


def patch_upsert_body(text):
    """Turn every early 'return;' into 'return false;' and add 'return true;'.

    The guard block is whatever the patch chain left there - the ino-0 guard
    (patch_susfs_ino_zero_guard.py) adds a second condition, so counting returns is
    wrong.  Every 'return;' inside this function is a failure path (argument guards
    and the kzalloc failure), so all of them become 'return false;' and the success
    path is added before the closing brace.
    """
    if text.count(BODY_OLD_HEAD) != 1:
        die("upsert definition head matched %d times (expected 1)" % text.count(BODY_OLD_HEAD))
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
    return text[:start] + body + text[end:]

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

# Policy loader: only count what really landed, and fail the call when a rule that
# exists on disk could not be installed.  A path that does not exist (ENOENT) is not
# a failure - nothing can leak through it - but a mode-2 entry is still queued so the
# loop worker retries it on the next app spawn ("deferred").
POLICY_RE = re.compile(
    r"(?P<i>\t+)if \(\*target && kern_path\(target, LOOKUP_FOLLOW, &kpath\) == 0\) \{\n"
    r"\t+if \(kpath\.dentry && kpath\.dentry->d_inode\) \{\n"
    r"\t+susfs_sus_path_uid_upsert\(kpath\.dentry->d_inode, uid, 1\);\n"
    r"\t+if \(mode == 2\)\n"
    r"\t+susfs_loop_queue_path\(target, uid, 1\);\n"
    r"\t+applied\+\+;\n"
    r"\t+\}\n"
    r"\t+path_put\(&kpath\);\n"
    r"\t+\}\n"
)

POLICY_DECL_OLD = "\tint applied = 0;\n"
POLICY_DECL_NEW = (
    "\tint applied = 0;\n"
    "\tint deferred = 0; /* ABK " + MARKER + ": path absent, nothing to hide */\n"
    "\tint failed = 0;   /* ABK " + MARKER + ": rule exists but was not installed */\n"
    "\tint kerr = 0;     /* ABK " + MARKER + " */\n"
)

POLICY_RET_RE = re.compile(
    r"\tkvfree\(buf\);\n"
    r"\tSUSFS_LOGI\(\"policy: applied %d entries\\n\", applied\);\n"
    r"\treturn applied > 0 \? 0 : -ENOENT;\n"
)

POLICY_RET_NEW = (
    "\tkvfree(buf);\n"
    "\tSUSFS_LOGI(\"policy: applied %d, deferred %d, failed %d\\n\", applied, deferred, failed);\n"
    "\tif (failed)\n"
    "\t\treturn -EIO; /* ABK " + MARKER + ": a partial publish is a failure */\n"
    "\treturn (applied > 0 || deferred > 0) ? 0 : -ENOENT;\n"
)


def patch_policy_upsert(text):
    matches = list(POLICY_RE.finditer(text))
    if len(matches) != 1:
        die("policy loader upsert matched %d times (expected 1)" % len(matches))
    m = matches[0]
    i = m.group("i")
    i1, i2, i3 = i + "\t", i + "\t\t", i + "\t\t\t"
    body = (
        i + "kerr = kern_path(target, LOOKUP_FOLLOW, &kpath);\n"
        + i + "if (kerr == 0) {\n"
        + i1 + "if (kpath.dentry && kpath.dentry->d_inode) {\n"
        + i2 + "if (!susfs_sus_path_uid_upsert(kpath.dentry->d_inode, uid, 1)) {\n"
        + i3 + "/* ABK " + MARKER + ": never report a publish we did not install */\n"
        + i3 + "failed++;\n"
        + i2 + "} else {\n"
        + i3 + "if (mode == 2)\n"
        + i3 + "\tsusfs_loop_queue_path(target, uid, 1);\n"
        + i3 + "applied++;\n"
        + i2 + "}\n"
        + i1 + "} else {\n"
        + i2 + "/* positive dentry without an inode: nothing can be enforced */\n"
        + i2 + "failed++;\n"
        + i1 + "}\n"
        + i1 + "path_put(&kpath);\n"
        + i + "} else if (kerr == -ENOENT) {\n"
        + i1 + "/* the path does not exist (yet): nothing can leak through it */\n"
        + i1 + "if (mode == 2)\n"
        + i2 + "susfs_loop_queue_path(target, uid, 1);\n"
        + i1 + "deferred++;\n"
        + i + "} else {\n"
        + i1 + "failed++;\n"
        + i + "}\n"
    )
    text = text[:m.start()] + body + text[m.end():]

    text = replace_once(text, POLICY_DECL_OLD, POLICY_DECL_NEW, "policy loader counters")
    matches = list(POLICY_RET_RE.finditer(text))
    if len(matches) != 1:
        die("policy loader return matched %d times (expected 1)" % len(matches))
    m = matches[0]
    return text[:m.start()] + POLICY_RET_NEW + text[m.end():]

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

    if "static bool susfs_sus_path_uid_upsert" in text and ("return true; /* ABK " + MARKER) in text:
        print("skip (already patched): " + REL_SRC)
        return

    text = replace_once(text, OLD_PROTO, NEW_PROTO, "upsert prototype")



    text = patch_upsert_body(text)
    text = replace_once_re(text, HIDE_FUSE_RE, "hide_from fuse call")
    text = replace_once_re(text, HIDE_PLAIN_RE, "hide_from plain call")
    text = patch_policy_upsert(text)

    with open(path, "w", encoding="utf-8", newline="") as fh:
        fh.write(text)
    print("patch: upsert result propagation (" + MARKER + ")")


if __name__ == "__main__":
    main()
