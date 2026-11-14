#!/usr/bin/env python3
"""ABK: make getpriority/setpriority deny hidden uids, and wire the supercall.

Usage:
    patch_susfs_hidden_uid_oracle.py <kernel_root>

Run AFTER patch_susfs_hidden_uid.py (which adds the hidden-uid set to
kernel_patches/fs/susfs.c), after the susfs sources are copied to
common/, and after patch_susfs_app_uid_gate.py (which installs
abk_sus_path_app_uid_gate).

Edits:
    common/kernel/sys.c      getpriority(PRIO_USER, hidden_uid)  -> -ESRCH
                             setpriority(PRIO_USER, hidden_uid)  -> -ESRCH
    KernelSU/.../supercall/dispatch.c
                             case CMD_SUSFS_ADD_HIDDEN_UID -> susfs_add_hidden_uid()
                             plus a local prototype

The gate lives in susfs_uid_is_hidden(): only app-uid processes outside the
ksu domain see the denial; su/ksu tooling and uid < 10000 keep the truth.

Every missing or ambiguous anchor aborts with exit code 2 and an ::error::
line.  Re-running is a no-op.
"""
import os
import sys

MARKER = "SUSFS_HIDDEN_UID_ABK"
CMD = "CMD_SUSFS_ADD_HIDDEN_UID"
HANDLER = "susfs_add_hidden_uid"
LOOKUP = "susfs_uid_is_hidden"
HIDE_FROM_CMD = "CMD_SUSFS_ADD_SUS_PATH_HIDE_FROM"

INCLUDE_ANCHOR = "#include <linux/uidgid.h>\n#include <linux/cred.h>\n"
INCLUDE_ADD = (
    INCLUDE_ANCHOR
    + "#ifdef CONFIG_KSU_SUSFS_SUS_PATH\n"
    "#include <linux/susfs.h> /* ABK " + MARKER + " */\n"
    "#endif\n"
)

HOOK = (
    "#ifdef CONFIG_KSU_SUSFS_SUS_PATH\n"
    "\t/* ABK " + MARKER + ": a hidden uid must not look alive to an app */\n"
    "\tif (which == PRIO_USER && who >= 0 && " + LOOKUP + "((uid_t)who))\n"
    "\t\treturn -ESRCH;\n"
    "#endif // CONFIG_KSU_SUSFS_SUS_PATH\n"
)

SETPRIO_ANCHOR = (
    "SYSCALL_DEFINE3(setpriority, int, which, int, who, int, niceval)\n"
    "{\n"
    "\tstruct task_struct *g, *p;\n"
    "\tstruct user_struct *user;\n"
    "\tconst struct cred *cred = current_cred();\n"
    "\tint error = -EINVAL;\n"
    "\tstruct pid *pgrp;\n"
    "\tkuid_t uid;\n"
    "\n"
    "\tif (which > PRIO_USER || which < PRIO_PROCESS)\n"
    "\t\tgoto out;\n"
)

SETPRIO_ADD = (
    "SYSCALL_DEFINE3(setpriority, int, which, int, who, int, niceval)\n"
    "{\n"
    "\tstruct task_struct *g, *p;\n"
    "\tstruct user_struct *user;\n"
    "\tconst struct cred *cred = current_cred();\n"
    "\tint error = -EINVAL;\n"
    "\tstruct pid *pgrp;\n"
    "\tkuid_t uid;\n"
    "\n"
    + HOOK
    + "\n"
    "\tif (which > PRIO_USER || which < PRIO_PROCESS)\n"
    "\t\tgoto out;\n"
)

GETPRIO_ANCHOR = (
    "SYSCALL_DEFINE2(getpriority, int, which, int, who)\n"
    "{\n"
    "\tstruct task_struct *g, *p;\n"
    "\tstruct user_struct *user;\n"
    "\tconst struct cred *cred = current_cred();\n"
    "\tlong niceval, retval = -ESRCH;\n"
    "\tstruct pid *pgrp;\n"
    "\tkuid_t uid;\n"
    "\n"
    "\tif (which > PRIO_USER || which < PRIO_PROCESS)\n"
    "\t\treturn -EINVAL;\n"
)

GETPRIO_ADD = (
    "SYSCALL_DEFINE2(getpriority, int, which, int, who)\n"
    "{\n"
    "\tstruct task_struct *g, *p;\n"
    "\tstruct user_struct *user;\n"
    "\tconst struct cred *cred = current_cred();\n"
    "\tlong niceval, retval = -ESRCH;\n"
    "\tstruct pid *pgrp;\n"
    "\tkuid_t uid;\n"
    "\n"
    + HOOK
    + "\n"
    "\tif (which > PRIO_USER || which < PRIO_PROCESS)\n"
    "\t\treturn -EINVAL;\n"
)

DISPATCH_INCLUDE = "#include <linux/susfs_def.h>\n"
DISPATCH_PROTO = (
    "#ifdef CONFIG_KSU_SUSFS_SUS_PATH\n"
    "/* ABK " + MARKER + " */\n"
    "void " + HANDLER + "(void __user **user_info);\n"
    "#endif\n"
)

DISPATCH_CASE = (
    "\n"
    "\tcase " + CMD + ": {\n"
    "\t\t" + HANDLER + "(arg);\n"
    "\t\treturn 0;\n"
    "\t}\n"
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


def sub_one(text, needle, repl, path, label):
    count = text.count(needle)
    if count != 1:
        die("anchor '" + label + "' matched " + str(count) + " times (expected 1) in " + path)
    return text.replace(needle, repl, 1)


def find_base(root):
    for candidate in (os.path.join(root, "common"), root):
        if (os.path.isfile(os.path.join(candidate, "kernel", "sys.c")) and
                os.path.isfile(os.path.join(candidate, "fs", "susfs.c"))):
            return candidate
    return None


def find_dispatch(root):
    preferred = os.path.join(root, "KernelSU", "kernel", "supercall", "dispatch.c")
    if os.path.isfile(preferred):
        return preferred
    for dirpath, dirnames, filenames in os.walk(root):
        if "dispatch.c" in filenames and os.path.basename(dirpath) == "supercall":
            return os.path.join(dirpath, "dispatch.c")
    return None


def patch_sys(base):
    path = os.path.join(base, "kernel", "sys.c")
    text = load(path)
    if MARKER in text:
        return "skip", path
    if "CONFIG_KSU_SUSFS_SUS_PATH" not in text and "CONFIG_KSU_SUSFS" not in text:
        # The generated config is not text here; this check only guards a badly
        # assembled tree.  The include is compiled out when SUSFS is disabled.
        pass
    text = sub_one(text, INCLUDE_ANCHOR, INCLUDE_ADD, path, "linux/uidgid.h include")
    text = sub_one(text, SETPRIO_ANCHOR, SETPRIO_ADD, path, "setpriority syscall")
    text = sub_one(text, GETPRIO_ANCHOR, GETPRIO_ADD, path, "getpriority syscall")
    save(path, text)
    return "patch", path


def find_case_end(text, pos):
    start = text.find("{", pos)
    if start == -1:
        return -1
    depth = 0
    for i in range(start, len(text)):
        if text[i] == "{":
            depth += 1
        elif text[i] == "}":
            depth -= 1
            if depth == 0:
                return i
    return -1


def patch_dispatch(path):
    text = load(path)
    if MARKER in text:
        return "skip", path
    text = sub_one(text, DISPATCH_INCLUDE, DISPATCH_INCLUDE + DISPATCH_PROTO, path,
                   "susfs_def.h include")
    anchor = "case " + HIDE_FROM_CMD + ":"
    hits = text.count(anchor)
    if hits != 1:
        die("anchor '" + anchor + "' matched " + str(hits) + " times (expected 1) in " + path +
            "; run the hide_from dispatch injection first")
    pos = text.find(anchor)
    end = find_case_end(text, pos)
    if end == -1:
        die("could not find the end of the " + HIDE_FROM_CMD + " case in " + path)
    # Insert immediately after the case-closing brace so the new case can
    # never land outside the enclosing #ifdef, regardless of blank lines.
    text = text[:end + 1] + DISPATCH_CASE + text[end + 1:]
    save(path, text)
    return "patch", path


def main():
    if len(sys.argv) != 2:
        die("usage: patch_susfs_hidden_uid_oracle.py <kernel_root>")
    root = sys.argv[1]
    base = find_base(root)
    if base is None:
        die("kernel tree not found under " + root + " (need kernel/sys.c and fs/susfs.c)")
    dispatch = find_dispatch(root)
    if dispatch is None:
        die("KernelSU supercall/dispatch.c not found under " + root)

    print("[ABK] kernel tree: " + base)
    action, path = patch_sys(base)
    print("[ABK] " + action + ": " + path)
    action, path = patch_dispatch(dispatch)
    print("[ABK] " + action + ": " + path)

    sys_c = load(os.path.join(base, "kernel", "sys.c"))
    src = load(os.path.join(base, "fs", "susfs.c"))
    hdr = load(os.path.join(base, "include", "linux", "susfs.h"))
    sdef = load(os.path.join(base, "include", "linux", "susfs_def.h"))
    disp = load(dispatch)

    checks = [
        (sys_c, MARKER, "sys.c marker"),
        (sys_c, "which == PRIO_USER && who >= 0 && " + LOOKUP + "((uid_t)who)", "sys.c hook"),
        (src, MARKER, "susfs.c marker"),
        (src, "bool " + LOOKUP + "(uid_t uid)", "susfs.c lookup definition"),
        (src, HANDLER + "(void __user **user_info)", "susfs.c handler definition"),
        (hdr, "bool " + LOOKUP + "(uid_t uid);", "susfs.h lookup prototype"),
        (hdr, HANDLER, "susfs.h handler prototype"),
        (sdef, CMD, "susfs_def.h command id"),
        (disp, "case " + CMD + ":", "dispatch case"),
        (disp, HANDLER + "(arg);", "dispatch handler call"),
    ]
    for text, needle, label in checks:
        if needle not in text:
            die("verification failed: " + label + " ('" + needle + "') missing")

    if sys_c.count(LOOKUP) != 2:
        die("verification failed: " + LOOKUP + " referenced " + str(sys_c.count(LOOKUP)) +
            " times in kernel/sys.c (expected 2: getpriority + setpriority)")
    if src.count("abk_sus_path_app_uid_gate") < 2:
        die("verification failed: abk_sus_path_app_uid_gate missing from fs/susfs.c; " +
            "run patch_susfs_app_uid_gate.py first")
    if src.index("static bool abk_sus_path_app_uid_gate") > src.index("bool " + LOOKUP + "(uid_t uid)"):
        die("verification failed: " + LOOKUP + " is defined before abk_sus_path_app_uid_gate")

    print("[ABK] hidden-uid UID oracle OK: getpriority+setpriority(PRIO_USER, hidden uid) -> -ESRCH, " +
          "dispatch " + CMD + " -> " + HANDLER)
    return 0


if __name__ == "__main__":
    sys.exit(main())
