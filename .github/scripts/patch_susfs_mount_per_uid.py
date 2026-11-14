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
MARKER_HELPER = "MOUNT_PER_UID_HELPER_ABK"
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
    " * Open-addressed set instead of a bitmap: an Android UID is\n"
    " * userId * 100000 + appId, so a secondary user (e.g. user 10 -> 1010123) does\n"
    " * not fit a small bitmap and silently losing it would report success without\n"
    " * filtering.  Slot value 0 means empty, so uids are stored as (uid + 1).\n"
    " * Removal only happens through the full clear below, so no tombstones.\n"
    " */\n"
    "#define ABK_MANAGED_UID_SLOTS 4096\n"
    "static u32 abk_managed_uid_slots[ABK_MANAGED_UID_SLOTS];\n"
    "static bool abk_mount_per_uid __read_mostly = true;\n"
    "\n"
    "static inline unsigned int abk_uid_hash(u32 v)\n"
    "{\n"
    "\treturn (unsigned int)((v * 2654435761u) % ABK_MANAGED_UID_SLOTS);\n"
    "}\n"
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
    "/* Returns false when the set is full - the caller must count that as a failure\n"
    " * instead of claiming the uid is managed.\n"
    " */\n"
    "bool susfs_uid_mark_managed(int uid)\n"
    "{\n"
    "\tu32 v;\n"
    "\tunsigned int i, n;\n"
    "\n"
    "\tif (uid < 0)\n"
    "\t\treturn false;\n"
    "\tv = (u32)uid + 1;\n"
    "\ti = abk_uid_hash(v);\n"
    "\tfor (n = 0; n < ABK_MANAGED_UID_SLOTS; n++) {\n"
    "\t\tunsigned int s = (i + n) % ABK_MANAGED_UID_SLOTS;\n"
    "\t\tif (READ_ONCE(abk_managed_uid_slots[s]) == v)\n"
    "\t\t\treturn true;\n"
    "\t\tif (READ_ONCE(abk_managed_uid_slots[s]) == 0) {\n"
    "\t\t\tWRITE_ONCE(abk_managed_uid_slots[s], v);\n"
    "\t\t\treturn true;\n"
    "\t\t}\n"
    "\t}\n"
    "\treturn false;\n"
    "}\n"
    "\n"
    "void susfs_uid_clear_managed(void)\n"
    "{\n"
    "\tmemset(abk_managed_uid_slots, 0, sizeof(abk_managed_uid_slots));\n"
    "}\n"
    "\n"
    "bool susfs_uid_is_managed(unsigned int uid)\n"
    "{\n"
    "\tu32 v;\n"
    "\tunsigned int i, n;\n"
    "\n"
    "\tif (!abk_mount_per_uid)\n"
    "\t\treturn true; /* legacy behaviour: every app uid is filtered */\n"
    "\t/* isolated app processes (90000-99999) never appear in /data/user/0, so\n"
    "\t * userspace cannot list them - treat them like any other app process.\n"
    "\t */\n"
    "\tif (uid >= 90000 && uid <= 99999)\n"
    "\t\treturn true;\n"
    "\tv = uid + 1;\n"
    "\ti = abk_uid_hash(v);\n"
    "\tfor (n = 0; n < ABK_MANAGED_UID_SLOTS; n++) {\n"
    "\t\tunsigned int s = (i + n) % ABK_MANAGED_UID_SLOTS;\n"
    "\t\tif (READ_ONCE(abk_managed_uid_slots[s]) == v)\n"
    "\t\t\treturn true;\n"
    "\t\tif (READ_ONCE(abk_managed_uid_slots[s]) == 0)\n"
    "\t\t\treturn false;\n"
    "\t}\n"
    "\treturn false;\n"
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
        + "\t\t\t/* ABK " + MARKER + ": only count uids the set actually recorded */\n"
        + "\t\t\tif (susfs_uid_mark_managed(uid))\n"
        + "\t\t\t\tapplied++;\n"
        + "\t\t\telse\n"
        + "\t\t\t\tfailed++;\n"
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


def patch_policy_loader_clear(root):
    """Empty the managed-uid set before applying a new policy file.

    The loader is called again whenever the magic path is used; without a clear, a
    uid removed from the policy would keep its filtered mount view until reboot.
    """
    p = find(root, os.path.join("fs", "susfs.c"))
    text = load(p)
    if "susfs_uid_clear_managed();" in text:
        return
    anchor = "\tline = buf;\n"
    if text.count(anchor) != 1:
        die("policy loader anchor 'line = buf;' matched %d times (expected 1)" % text.count(anchor))
    text = text.replace(
        anchor,
        "\tsusfs_uid_clear_managed(); /* ABK " + MARKER + ": set mirrors the current file */\n" + anchor,
        1,
    )
    save(p, text)


def patch_statfs_gate(root):
    """Make statfs() use the same gate as the three mount tables."""
    for rel in (os.path.join("fs", "statfs.c"), os.path.join("fs", "namespace.c")):
        try:
            p = find(root, rel)
        except SystemExit:
            continue
        text = load(p)
        if MARKER in text and "susfs_uid_is_managed" in text:
            return
        tail = "\treturn !susfs_is_current_ksu_domain();\n}\n"
        if text.count(tail) != 1:
            continue
        text = text.replace(
            tail,
            "\tif (susfs_is_current_ksu_domain())\n"
            "\t\treturn false;\n"
            "\t/* ABK " + MARKER + ": same managed-uid gate as mountinfo/path/fdinfo */\n"
            "\treturn susfs_uid_is_managed(current_uid().val);\n"
            "}\n",
            1,
        )
        decl = "extern bool susfs_uid_is_managed(unsigned int uid);\n"
        if decl not in text:
            anchor = "static bool abk_statfs_must_use_non_sus_mnt(void)"
            if anchor not in text:
                continue
            text = text.replace(anchor, decl + anchor, 1)
        save(p, text)
        return


def patch_proc_namespace(root):
    """Gate the three mount-view entries on the managed-uid bitmap.

    Real upstream text (gki-android14-6.1, 50_add_susfs_in_gki-android14-6.1.patch):

        if (static_branch_likely(&susfs_is_hide_sus_mnts_for_non_su_procs_enabled)) {
            if (likely(!susfs_is_current_ksu_domain()))
                return mounts_open_common(inode, file, susfs_show_mountinfo);
        }
        return mounts_open_common(inode, file, show_mountinfo);

    Only the *condition* is extended - no statement is added or removed, so every
    path keeps its original return value (the earlier attempt guarded the return
    itself and tripped -Werror=return-type).
    """
    p = find(root, os.path.join("fs", "proc_namespace.c"))
    text = load(p)
    if MARKER in text:
        return

    decl_anchor = "extern bool susfs_is_current_ksu_domain(void);\n"
    if decl_anchor not in text:
        die("extern susfs_is_current_ksu_domain() not found in fs/proc_namespace.c")
    text = text.replace(
        decl_anchor,
        decl_anchor
        + "extern bool susfs_uid_is_managed(unsigned int uid); /* ABK " + MARKER + " */\n",
        1,
    )

    cond_old = (
        "\tif (static_branch_likely(&susfs_is_hide_sus_mnts_for_non_su_procs_enabled)) {\n"
        "\t\tif (likely(!susfs_is_current_ksu_domain()))\n"
    )
    cond_new = (
        "\tif (static_branch_likely(&susfs_is_hide_sus_mnts_for_non_su_procs_enabled)) {\n"
        "\t\t/* ABK " + MARKER + ": only the configured targets get the filtered view */\n"
        "\t\tif (likely(!susfs_is_current_ksu_domain()) &&\n"
        "\t\t    susfs_uid_is_managed(current_uid().val))\n"
    )
    n = text.count(cond_old)
    if n != 3:
        die("expected 3 sus-mount conditions (mounts/mountinfo/mountstats), found %d" % n)
    text = text.replace(cond_old, cond_new)
    save(p, text)

def patch_mount_consistency_files(root):
    """Make path resolution and fdinfo agree with the new mountinfo gate.

    patch_susfs_mount_app_uid.py installed this helper in fs/namespace.c and
    fs/proc/fd.c:

        static inline bool abk_mount_app_target(void)
        {
                if (likely(susfs_is_current_proc_umounted()))
                        return true;
                if (!susfs_is_current_app_uid())
                        return false;
                return !susfs_is_current_ksu_domain();
        }

    i.e. every app uid still gets the redirected view, while the mountinfo gate now
    only filters managed uids - exactly the "two different mount views" divergence
    duck detector reports (cross-process mount tables).  Give the helper the same
    predicate as the mountinfo gate.
    """
    old = (
        "static inline bool abk_mount_app_target(void)\n"
        "{\n"
        "\tif (likely(susfs_is_current_proc_umounted()))\n"
        "\t\treturn true;\n"
        "\tif (!susfs_is_current_app_uid())\n"
        "\t\treturn false;\n"
        "\treturn !susfs_is_current_ksu_domain();\n"
        "}\n"
    )
    new = (
        "extern bool susfs_uid_is_managed(unsigned int uid);\n"
        "static inline bool abk_mount_app_target(void)\n"
        "{\n"
        "\tif (likely(susfs_is_current_proc_umounted()))\n"
        "\t\treturn true;\n"
        "\tif (!susfs_is_current_app_uid())\n"
        "\t\treturn false;\n"
        "\tif (susfs_is_current_ksu_domain())\n"
        "\t\treturn false;\n"
        "\t/* ABK " + MARKER + ": path resolution and fdinfo follow the mountinfo gate */\n"
        "\treturn susfs_uid_is_managed(current_uid().val);\n"
        "}\n"
    )
    touched = 0
    for rel in (os.path.join("fs", "namespace.c"), os.path.join("fs", "proc", "fd.c")):
        p = find(root, rel)
        text = load(p)
        if MARKER_HELPER in text:
            continue
        if old not in text:
            die("abk_mount_app_target() helper not found in " + rel)
        text = text.replace(old, new, 1)
        text = text.replace(
            "static inline bool abk_mount_app_target(void)",
            "/* ABK " + MARKER_HELPER + " */\nstatic inline bool abk_mount_app_target(void)",
            1,
        )
        save(p, text)
        touched += 1
    print("mount consistency helper updated in %d file(s)" % touched)

def diagnose(root):
    """Print the real proc_namespace.c text around the sus-mount branch."""
    p = find(root, os.path.join("fs", "proc_namespace.c"))
    text = load(p)
    idx = text.find("susfs_is_hide_sus_mnts_for_non_su_procs_enabled")
    print("=====DIAG-BEGIN=====")
    print("file: " + p + "  bytes: " + str(len(text)) + "  idx: " + str(idx))
    if idx >= 0:
        print(text[max(0, idx - 1200):idx + 2000])
    print("=====DIAG-END=====")


def main():
    args = [a for a in sys.argv[1:] if a != "--diagnose"]
    if len(args) != 1:
        die("usage: patch_susfs_mount_per_uid.py <kernel_root> [--diagnose]")
    root = args[0]
    if "--diagnose" in sys.argv:
        diagnose(root)
        return
    patch_susfs_c(root)
    patch_header(root)
    patch_proc_namespace(root)
    patch_mount_consistency_files(root)
    patch_policy_loader_clear(root)
    patch_statfs_gate(root)
    print("patch: per-target mount view (" + MARKER + ")")


if __name__ == "__main__":
    main()
