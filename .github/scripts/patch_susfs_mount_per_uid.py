#!/usr/bin/env python3
"""ABK: per-target mount view (only configured app uids see the filtered world).

Usage:
    patch_susfs_mount_per_uid.py <kernel_root>

Run AFTER patch_susfs_policy_file.py and patch_susfs_mount_app_uid.py.

Why: today every app-uid process (uid >= 10000) gets the SUSFS mount view, whether
or not the app is configured in HMA.  The per-app-view spec asks for the opposite
direction: start from the untouched system view and change it only for the apps the
configuration actually manages (the targets), leaving system services, preinstalled
apps and everything else with the real view - the same "don't touch the system"
rule that keeps the IME working.

Mechanics:
    * a policy line "4|<uid>|1" marks that uid as managed;
    * susfs_uid_mark_managed() / susfs_uid_is_managed() keep a small hash table;
    * the mountinfo filter in fs/proc_namespace.c additionally requires
      susfs_uid_is_managed(), so only managed uids see the filtered mounts.

Kill switch: abk_mount_per_uid=0 restores "every app uid is filtered" without a
kernel rebuild (standard __setup parameter).

Touched files (relative to <kernel_root>, directly or under common/):
    fs/susfs.c                     uid table + policy mode 4
    fs/proc_namespace.c            mountinfo/mounts filter gate
    include/linux/susfs.h          declarations
"""
import os
import re
import sys

MARKER = "MOUNT_PER_UID_ABK"
POLICY_PATH = "/data/adb/hma_susfs/policy.tsv"


def die(msg):
    sys.stderr.write("::error::" + msg + "\n")
    sys.exit(2)


def find(root, rel):
    for base in (os.path.join(root, "common"), root):
        p = os.path.join(base, rel)
        if os.path.isfile(p):
            return p
    die("expected file not found: " + rel)


def load(p):
    with open(p, "r", encoding="utf-8", newline="") as fh:
        return fh.read()


def save(p, text):
    with open(p, "w", encoding="utf-8", newline="") as fh:
        fh.write(text)


TABLE_C = (
    "/* ABK " + MARKER + ": uids whose mount view is filtered (the HMA targets).\n"
    " *\n"
    " * Bitmap on purpose: uid range of an Android device is bounded (< 200000) and\n"
    " * a bitmap keeps the check to one test_bit() on the mountinfo hot path.\n"
    " */\n"
    "#define ABK_MANAGED_UID_MAX 200000\n"
    "static DECLARE_BITMAP(abk_managed_uids, ABK_MANAGED_UID_MAX);\n"
    "static bool abk_mount_per_uid __read_mostly = true;\n"
    "\n"
    "static int __init abk_mount_per_uid_setup(char *str)\n"
    "{\n"
    "\tint v = 0;\n"
    "\n"
    "\tif (!str)\n"
    "\t\treturn 1;\n"
    "\tif (kstrtoint(str, 0, &v) == 0)\n"
    "\t\tabk_mount_per_uid = (v != 0);\n"
    "\treturn 1;\n"
    "}\n"
    "__setup(\"abk_mount_per_uid\", abk_mount_per_uid_setup);\n"
    "\n"
    "void susfs_uid_mark_managed(int uid)\n"
    "{\n"
    "\tif (uid < 0 || uid >= ABK_MANAGED_UID_MAX)\n"
    "\t\treturn;\n"
    "\tset_bit((unsigned int)uid, abk_managed_uids);\n"
    "}\n"
    "\n"
    "bool susfs_uid_is_managed(unsigned int uid)\n"
    "{\n"
    "\tif (!abk_mount_per_uid)\n"
    "\t\treturn true; /* legacy behaviour: every app uid is filtered */\n"
    "\tif (uid >= ABK_MANAGED_UID_MAX)\n"
    "\t\treturn false;\n"
    "\treturn test_bit((unsigned int)uid, abk_managed_uids);\n"
    "}\n"
    "\n"
)

HDR_DECL = (
    "/* ABK " + MARKER + " */\n"
    "void susfs_uid_mark_managed(int uid);\n"
    "bool susfs_uid_is_managed(unsigned int uid);\n"
)


def patch_susfs_c(root):
    p = find(root, os.path.join("fs", "susfs.c"))
    text = load(p)
    if MARKER in text:
        return
    anchor = "static int susfs_load_policy_file(void)\n"
    if anchor not in text:
        die("susfs_load_policy_file() not found - run patch_susfs_policy_file.py first")
    text = text.replace(anchor, TABLE_C + anchor, 1)

    # policy mode 4: mark a uid as managed instead of touching a path
    parse_anchor = (
        "\t\t*bar1 = '\\0';\n"
        "\t\t*bar2 = '\\0';\n"
        "\t\ttarget = bar2 + 1;\n"
        "\t\tmode = (int)simple_strtoul(line, NULL, 10);\n"
        "\t\tuid = (int)simple_strtoul(bar1 + 1, NULL, 10);\n"
    )
    if parse_anchor not in text:
        die("policy parser block not found in fs/susfs.c")
    text = text.replace(
        parse_anchor,
        parse_anchor
        + "\t\tif (mode == 4) {\n"
        + "\t\t\tsusfs_uid_mark_managed(uid); /* ABK " + MARKER + " */\n"
        + "\t\t\tapplied++;\n"
        + "\t\t\tline = next;\n"
        + "\t\t\tcontinue;\n"
        + "\t\t}\n",
        1,
    )
    save(p, text)


def patch_header(root):
    p = find(root, os.path.join("include", "linux", "susfs.h"))
    text = load(p)
    if MARKER in text:
        return
    anchor = "struct st_susfs_sus_path {\n"
    if anchor not in text:
        die("struct st_susfs_sus_path not found in include/linux/susfs.h")
    text = text.replace(anchor, HDR_DECL + "\n" + anchor, 1)
    save(p, text)


def patch_proc_namespace(root):
    p = find(root, os.path.join("fs", "proc_namespace.c"))
    text = load(p)
    if MARKER in text:
        return
    # The block may already carry extra conditions from patch_susfs_mount_app_uid.py,
    # so locate the sus-mount branch and guard the filtered return instead of
    # matching the exact three lines.
    # Declare the helper locally as well: the include in this file is not
    # guaranteed to be ordered before the branch on every SUSFS revision (the
    # first build failed with an implicit-declaration error here).
    if "susfs_uid_is_managed" not in text:
        decl = "extern bool susfs_uid_is_managed(unsigned int uid); /* ABK " + MARKER + " */\n"
        m_inc = re.search(r"(#include <linux/susfs\.h>\n)", text)
        if m_inc:
            text = text[:m_inc.end(1)] + decl + text[m_inc.end(1):]
        else:
            m_last = list(re.finditer(r"^#include [^\n]*\n", text, re.M))
            if not m_last:
                die("no #include block found in fs/proc_namespace.c")
            pos = m_last[-1].end(0)
            text = text[:pos] + decl + text[pos:]

    idx = text.find("susfs_is_hide_sus_mnts_for_non_su_procs_enabled")
    if idx < 0:
        die("susfs mountinfo branch not found in fs/proc_namespace.c")

    window = text[idx:idx + 2000]

    # The upstream text differs between SUSFS revisions (the call may carry extra
    # arguments or be wrapped in a helper), so take the first return statement that
    # follows the flag instead of matching the exact call.
    m = re.search(r"(\n(\t+)return [^\n]*;)", window)
    if not m:
        sys.stderr.write("::warning::sus-mount branch text:\n" + window[:600] + "\n")
        die("no return statement after the sus-mount branch in fs/proc_namespace.c")

    indent = m.group(2)
    guard = (
        "\n" + indent + "/* ABK " + MARKER + ": only the configured targets get the filtered view */"
        "\n" + indent + "if (susfs_uid_is_managed(current_uid().val))"
    )
    insert_at = idx + m.start(1)
    text = text[:insert_at] + guard + text[insert_at:]
    save(p, text)


def main():
    if len(sys.argv) != 2:
        die("usage: patch_susfs_mount_per_uid.py <kernel_root>")
    root = sys.argv[1]
    patch_susfs_c(root)
    patch_header(root)
    patch_proc_namespace(root)
    print("patch: per-target mount view (" + MARKER + ")")


if __name__ == "__main__":
    main()
