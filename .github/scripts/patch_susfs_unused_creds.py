#!/usr/bin/env python3
"""ABK: keep the SUSFS cred helpers out of the build when their only callers are.

Usage:
    patch_susfs_unused_creds.py <kernel_root>

Background (susfs4ksu gki-android14-6.1, a0ac906, i.e. SUSFS with the
priority_uid_filter feature):

    kernel_patches/fs/susfs.c defines two file-local helpers

        static struct cred *susfs_switch_to_ksu_domain(void) { ... }
        static void susfs_restore_creds(struct cred *orig_cred) { ... }

    at the top of the file, outside every preprocessor guard, but their only
    callers live in the block

        #ifdef CONFIG_KSU_SUSFS_PRIORITY_UID_FILTER
        ...
        #endif

    The KernelSU side Kconfig that ABK builds against (BakaSU 4.2.0-rc3)
    does not define CONFIG_KSU_SUSFS_PRIORITY_UID_FILTER at all, so the
    callers are compiled out while the helpers are not:

        fs/susfs.c:184:21: error: unused function 'susfs_switch_to_ksu_domain' [-Werror,-Wunused-function]
        fs/susfs.c:218:13: error: unused function 'susfs_restore_creds' [-Werror,-Wunused-function]

    This patcher wraps the two helpers in the same guard their callers use,
    so the file compiles both with and without the feature.

Behaviour:
    * no fs/susfs.c, or a susfs.c without these helpers -> nothing to do, ok;
    * helpers present and unguarded                      -> wrap them;
    * helpers present and already wrapped                -> no-op.

Touched file (relative to <kernel_root>, directly or under common/):
    fs/susfs.c

Every unexpected shape aborts with exit code 2 and an ::error:: line.
Re-running is a no-op.
"""
import os
import sys

MARKER = "SUSFS_UNUSED_CREDS_GUARD_ABK"
GUARD = "CONFIG_KSU_SUSFS_PRIORITY_UID_FILTER"
REL = os.path.join("fs", "susfs.c")

START = "static struct cred *susfs_switch_to_ksu_domain(void) {\n"
END = (
    "static void susfs_restore_creds(struct cred *orig_cred) {\n"
    "\tif (!orig_cred)\n"
    "\t\treturn;\n"
    "\n"
    "\tcommit_creds(orig_cred);\n"
    "}\n"
)

OPEN_TEXT = (
    "#ifdef " + GUARD + "\n"
    "/* ABK " + MARKER + ": upstream keeps these two file-local helpers outside\n"
    " * the guard that compiles their only callers (the priority_uid_filter\n"
    " * commands), so a KernelSU Kconfig without " + GUARD + "\n"
    " * turns them into -Werror=unused-function failures.  Keep them inside the\n"
    " * same guard as the callers.\n"
    " */\n"
)
CLOSE_TEXT = "#endif // " + GUARD + "\n"


def with_eol(text, eol):
    return text.replace("\n", eol) if eol != "\n" else text


def die(msg):
    sys.stderr.write("::error::" + msg + "\n")
    sys.exit(2)


def find_source(root):
    for candidate in (os.path.join(root, "common"), root):
        path = os.path.join(candidate, REL)
        if os.path.isfile(path):
            return path
    return None


def main():
    if len(sys.argv) != 2:
        die("usage: patch_susfs_unused_creds.py <kernel_root>")
    root = sys.argv[1]

    path = find_source(root)
    if path is None:
        print("[ABK] skip: fs/susfs.c not found under " + root)
        return 0

    with open(path, "r", encoding="utf-8", newline="") as fh:
        text = fh.read()

    if MARKER in text:
        print("[ABK] skip: " + path + " already carries " + MARKER)
        return 0

    eol = "\r\n" if "\r\n" in text else "\n"
    start = with_eol(START, eol)
    end = with_eol(END, eol)
    open_text = with_eol(OPEN_TEXT, eol)
    close_text = with_eol(CLOSE_TEXT, eol)

    if start not in text and end not in text:
        print("[ABK] skip: " + path + " has no priority_uid_filter cred helpers")
        return 0

    if text.count(start) != 1:
        die("anchor 'susfs_switch_to_ksu_domain definition' matched " +
            str(text.count(start)) + " times (expected 1) in " + path)
    if text.count(end) != 1:
        die("anchor 'susfs_restore_creds definition' matched " +
            str(text.count(end)) + " times (expected 1) in " + path)

    if text.index(end) < text.index(start):
        die("susfs_restore_creds() appears before susfs_switch_to_ksu_domain() in " +
            path + "; refusing to guess the guard bounds")

    text = text.replace(start, open_text + start, 1)
    text = text.replace(end, end + close_text, 1)

    with open(path, "w", encoding="utf-8", newline="") as fh:
        fh.write(text)

    print("[ABK] patch: " + path)

    with open(path, "r", encoding="utf-8", newline="") as fh:
        check = fh.read()
    if check.count(MARKER) != 1:
        die("verification failed: " + MARKER + " missing from " + path)
    guarded = (
        open_text + start in check
        and end + close_text in check
    )
    if not guarded:
        die("verification failed: the cred helpers are not inside " + GUARD +
            " in " + path)
    print("[ABK] SUSFS cred helper guard OK: " + MARKER +
          " (fs/susfs.c helpers now compile only with " + GUARD + ")")
    return 0


if __name__ == "__main__":
    sys.exit(main())
