#!/usr/bin/env python3
"""ABK: per-target hidden-UID sets for SUSFS.

Why: the global add_hidden_uid table answers every caller the same way, so
hiding one package's uid from ONE app also distorts every other app's view
(and can even hide the detector's own uid).  This patcher adds a second,
per-target table keyed by (hidden_uid, target_uid): only processes whose uid
equals target_uid see the hidden uid as dead.

Usage:
    patch_susfs_hidden_uid_from.py <susfs4ksu_dir> <kernel_root>

Adds:
  * CMD_SUSFS_ADD_HIDDEN_UID_FROM = 0x55556 (kernel + ksu_susfs CLI)
  * ABK_HIDDEN_UID_FROM_HLIST + abk_hidden_uid_from_upsert()
  * bool susfs_uid_is_hidden_from(uid_t uid, uid_t caller)
  * susfs_add_hidden_uid_from(): supercall handler
  * ksu_susfs add_hidden_uid_from <hidden_uid> <target_uid>
  * rewires getpriority()/setpriority() to the caller-aware lookup
  * injects the supercall dispatch case for 0x55556

Every missing or ambiguous anchor aborts with exit code 2 and an ::error::
line.  Re-running is a no-op.
"""
import os
import sys

MARKER = "SUSFS_HIDDEN_UID_FROM_ABK"
CMD = "CMD_SUSFS_ADD_HIDDEN_UID_FROM"
HANDLER = "susfs_add_hidden_uid_from"
LOOKUP = "susfs_uid_is_hidden_from"
CLI = "add_hidden_uid_from"
GLOBAL_CMD = "CMD_SUSFS_ADD_HIDDEN_UID"
GLOBAL_LOOKUP = "susfs_uid_is_hidden"

REL = {
    "def": "kernel_patches/include/linux/susfs_def.h",
    "hdr": "kernel_patches/include/linux/susfs.h",
    "src": "kernel_patches/fs/susfs.c",
    "cli": "ksu_susfs/jni/features/sus_path.c",
    "clih": "ksu_susfs/jni/features/sus_path.h",
    "clim": "ksu_susfs/jni/main.c",
}

DEF_ANCHOR = "#define " + GLOBAL_CMD + " 0x55555"
DEF_LINE = "#define " + CMD + " 0x55556 /* ABK " + MARKER + " */\n"

HDR_PROTO_ANCHOR = "bool " + GLOBAL_LOOKUP + "(uid_t uid);"
HDR_ADD = (
    "\n"
    "/* ABK " + MARKER + ": per-target hidden uid ABI */\n"
    "struct st_susfs_hidden_uid_from {\n"
    "\tuid_t                                   uid;\n"
    "\tuid_t                                   target_uid;\n"
    "\tint                                     err;\n"
    "};\n"
    "\n"
    "struct st_susfs_hidden_uid_from_hlist {\n"
    "\tstruct hlist_node                       node;\n"
    "\tuid_t                                   uid;\n"
    "\tuid_t                                   target_uid;\n"
    "};\n"
    "\n"
    "void " + HANDLER + "(void __user **user_info); /* ABK " + MARKER + " */\n"
    "bool " + LOOKUP + "(uid_t uid, uid_t caller); /* ABK " + MARKER + " */\n"
)

SRC_ANCHOR = "static DEFINE_HASHTABLE(ABK_HIDDEN_UID_HLIST, 8);"

SRC_BLOCK = (
    "/* ABK " + MARKER + ": per-target hidden uid set.\n"
    " * Hiding uid X from app Y must not change what app Z sees, so entries are\n"
    " * keyed by (uid, target_uid) and the lookup takes the caller from\n"
    " * current_uid().  Add-only; rebuilt by the device scripts every boot.\n"
    " */\n"
    "static DEFINE_HASHTABLE(ABK_HIDDEN_UID_FROM_HLIST, 8);\n"
    "static DEFINE_MUTEX(abk_hidden_uid_from_mutex);\n"
    "\n"
    "static int abk_hidden_uid_from_upsert(uid_t uid, uid_t target_uid)\n"
    "{\n"
    "\tstruct st_susfs_hidden_uid_from_hlist *entry = NULL;\n"
    "\tstruct st_susfs_hidden_uid_from_hlist *new_entry = NULL;\n"
    "\n"
    "\tmutex_lock(&abk_hidden_uid_from_mutex);\n"
    "\thash_for_each_possible(ABK_HIDDEN_UID_FROM_HLIST, entry, node, uid) {\n"
    "\t\tif (entry->uid == uid && entry->target_uid == target_uid) {\n"
    "\t\t\tmutex_unlock(&abk_hidden_uid_from_mutex);\n"
    "\t\t\treturn 0;\n"
    "\t\t}\n"
    "\t}\n"
    "\n"
    "\tnew_entry = kzalloc(sizeof(*new_entry), GFP_KERNEL);\n"
    "\tif (!new_entry) {\n"
    "\t\tmutex_unlock(&abk_hidden_uid_from_mutex);\n"
    "\t\treturn -ENOMEM;\n"
    "\t}\n"
    "\tnew_entry->uid = uid;\n"
    "\tnew_entry->target_uid = target_uid;\n"
    "\thash_add_rcu(ABK_HIDDEN_UID_FROM_HLIST, &new_entry->node, uid);\n"
    "\tmutex_unlock(&abk_hidden_uid_from_mutex);\n"
    "\n"
    "\tSUSFS_LOGI(\"hidden uid from set: uid '%u' hidden from '%u'\\n\", uid, target_uid);\n"
    "\treturn 0;\n"
    "}\n"
    "\n"
    "bool " + LOOKUP + "(uid_t uid, uid_t caller)\n"
    "{\n"
    "\tstruct st_susfs_hidden_uid_from_hlist *entry = NULL;\n"
    "\tbool hidden = false;\n"
    "\n"
    "\tif (unlikely(!READ_ONCE(abk_sus_path_app_uid_gate)))\n"
    "\t\treturn false;\n"
    "\tif (susfs_is_current_ksu_domain())\n"
    "\t\treturn false;\n"
    "\tif (!susfs_is_current_app_uid())\n"
    "\t\treturn false;\n"
    "\tif (uid < 10000 || caller < 10000)\n"
    "\t\treturn false;\n"
    "\n"
    "\trcu_read_lock();\n"
    "\thash_for_each_possible_rcu(ABK_HIDDEN_UID_FROM_HLIST, entry, node, uid) {\n"
    "\t\tif (entry->uid == uid && entry->target_uid == caller) {\n"
    "\t\t\thidden = true;\n"
    "\t\t\tbreak;\n"
    "\t\t}\n"
    "\t}\n"
    "\trcu_read_unlock();\n"
    "\n"
    "\treturn hidden;\n"
    "}\n"
    "\n"
    "void " + HANDLER + "(void __user **user_info)\n"
    "{\n"
    "\tstruct st_susfs_hidden_uid_from info = {0};\n"
    "\n"
    "\tif (copy_from_user(&info, (struct st_susfs_hidden_uid_from __user *)*user_info, sizeof(info))) {\n"
    "\t\tinfo.err = -EFAULT;\n"
    "\t\tgoto out_copy_to_user;\n"
    "\t}\n"
    "\n"
    "\tif (info.uid < 10000 || info.target_uid < 10000) {\n"
    "\t\tSUSFS_LOGE(\"hidden uid from: rejecting non-app uid '%u' -> '%u'\\n\", info.uid, info.target_uid);\n"
    "\t\tinfo.err = -EINVAL;\n"
    "\t\tgoto out_copy_to_user;\n"
    "\t}\n"
    "\n"
    "\tinfo.err = abk_hidden_uid_from_upsert(info.uid, info.target_uid);\n"
    "\tSUSFS_LOGI(\"CMD_SUSFS_ADD_HIDDEN_UID_FROM -> ret: %d, uid: '%u', target: '%u'\\n\",\n"
    "\t\t   info.err, info.uid, info.target_uid);\n"
    "\n"
    "out_copy_to_user:\n"
    "\tif (copy_to_user(&((struct st_susfs_hidden_uid_from __user *)*user_info)->err,\n"
    "\t\t\t &info.err, sizeof(info.err)))\n"
    "\t\tinfo.err = -EFAULT;\n"
    "}\n"
    "\n"
)

CLI_STRUCT_ANCHOR = "struct st_susfs_hidden_uid {"
CLI_STRUCT_ADD = (
    "/* ABK " + MARKER + ": add_hidden_uid_from ABI (must match include/linux/susfs.h) */\n"
    "struct st_susfs_hidden_uid_from {\n"
    "\tunsigned int            uid;\n"
    "\tunsigned int            target_uid;\n"
    "\tint                     err;\n"
    "};\n"
    "\n"
)

CLI_HELP_ANCHOR = "\tlog(\"      |--> su / ksu domain and uid < 10000 keep the real answer\\n\");\n"
CLI_HELP_ADD = (
    "\tlog(\"    add_hidden_uid_from <hidden_uid> <target_uid>\\n\");\n"
    "\tlog(\"      |--> Hides <hidden_uid> ONLY from processes running as <target_uid>\\n\");\n"
    "\tlog(\"      |--> other apps keep the real answer (no global uid distortion)\\n\");\n"
)

CLI_TAIL_ANCHOR = (
    "\tPRT_MSG_IF_CMD_NOT_SUPPORTED(info.err, " + GLOBAL_CMD + ");\n"
    "\treturn info.err;\n"
    "}\n"
)

CLI_FN = (
    "\n"
    "int " + CLI + "(int argc, char *argv[]) {\n"
    "\tstruct st_susfs_hidden_uid_from info = {0};\n"
    "\tchar *end = NULL;\n"
    "\tunsigned long uid, target_uid;\n"
    "\n"
    "\tif (argc != 4) {\n"
    "\t\tprint_help();\n"
    "\t\treturn -EINVAL;\n"
    "\t}\n"
    "\n"
    "\terrno = 0;\n"
    "\tuid = strtoul(argv[2], &end, 10);\n"
    "\tif (errno || !end || *end != '\\0' || uid < 10000 || uid > 0xffffffffUL) {\n"
    "\t\tlog(\"[-] invalid hidden uid: %s\\n\", argv[2]);\n"
    "\t\treturn -EINVAL;\n"
    "\t}\n"
    "\terrno = 0;\n"
    "\ttarget_uid = strtoul(argv[3], &end, 10);\n"
    "\tif (errno || !end || *end != '\\0' || target_uid < 10000 || target_uid > 0xffffffffUL) {\n"
    "\t\tlog(\"[-] invalid target uid: %s\\n\", argv[3]);\n"
    "\t\treturn -EINVAL;\n"
    "\t}\n"
    "\n"
    "\tinfo.uid = (unsigned int)uid;\n"
    "\tinfo.target_uid = (unsigned int)target_uid;\n"
    "\tinfo.err = ERR_CMD_NOT_SUPPORTED;\n"
    "\tsyscall(SYS_reboot, KSU_INSTALL_MAGIC1, SUSFS_MAGIC, " + CMD + ", &info);\n"
    "\tPRT_MSG_IF_CMD_NOT_SUPPORTED(info.err, " + CMD + ");\n"
    "\treturn info.err;\n"
    "}\n"
)

CLIH_ANCHOR = "int add_hidden_uid(int argc, char *argv[]);"
CLIH_ADD = "int " + CLI + "(int argc, char *argv[]); /* ABK " + MARKER + " */\n"

CLIM_ANCHOR = (
    "\tif (!strcmp(argv[1], \"add_hidden_uid\"))\n"
    "\t\treturn add_hidden_uid(argc, argv);\n"
)
CLIM_ADD = (
    CLIM_ANCHOR
    + "\t/* ABK " + MARKER + " */\n"
    "\tif (!strcmp(argv[1], \"" + CLI + "\"))\n"
    "\t\treturn " + CLI + "(argc, argv);\n"
)

SYS_CALL = GLOBAL_LOOKUP + "((uid_t)who)"
SYS_CALL_NEW = "(" + LOOKUP + "((uid_t)who, current_uid().val) || " + GLOBAL_LOOKUP + "((uid_t)who))"

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
DISPATCH_GUARD = (
    "#ifndef " + CMD + "\n"
    "#define " + CMD + " 0x55556\n"
    "#endif\n"
)
DISPATCH_ANCHOR = "case " + GLOBAL_CMD + ":"


def die(msg):
    sys.stderr.write("::error::" + msg + "\n")
    sys.exit(2)


def load(root, key):
    path = os.path.join(root, REL[key])
    if not os.path.isfile(path):
        die("expected file not found: " + REL[key])
    with open(path, "r", encoding="utf-8", newline="") as fh:
        return path, fh.read()


def load_path(path):
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


def insert_after_line(text, prefix, addition, path, label):
    hits = [i for i, line in enumerate(text.splitlines(True)) if line.startswith(prefix)]
    if len(hits) != 1:
        die("line anchor '" + label + "' matched " + str(len(hits)) + " times (expected 1) in " + path)
    lines = text.splitlines(True)
    lines[hits[0]] = lines[hits[0]] + addition
    return "".join(lines)


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


def find_case_end(text, pos):
    i = text.find("{", pos)
    if i == -1:
        return -1
    depth = 0
    for j in range(i, len(text)):
        c = text[j]
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
            if depth == 0:
                return j
    return -1


def apply_def(path):
    text = load_path(path)
    if MARKER in text:
        return "skip", path
    text = insert_after_line(text, DEF_ANCHOR, DEF_LINE, path, DEF_ANCHOR)
    save(path, text)
    return "patch", path


def apply_hdr(path):
    text = load_path(path)
    if MARKER in text:
        return "skip", path
    text = insert_after_line(text, HDR_PROTO_ANCHOR, HDR_ADD, path, HDR_PROTO_ANCHOR)
    save(path, text)
    return "patch", path


def apply_src(path):
    text = load_path(path)
    if MARKER in text:
        return "skip", path
    text = sub_one(text, SRC_ANCHOR, SRC_BLOCK + SRC_ANCHOR, path, SRC_ANCHOR)
    save(path, text)
    return "patch", path


def patch_def(root):
    return apply_def(os.path.join(root, REL["def"]))


def patch_hdr(root):
    return apply_hdr(os.path.join(root, REL["hdr"]))


def patch_src(root):
    return apply_src(os.path.join(root, REL["src"]))


def patch_tree(kernel_root):
    """The kernel tree keeps its own copy of the susfs files, generated from
    the susfs4ksu templates BEFORE this step runs.  Patch the tree copies too,
    otherwise kernel/sys.c sees no susfs_uid_is_hidden_from declaration."""
    base = find_base(kernel_root)
    if base is None:
        die("kernel base not found under " + kernel_root)
    out = []
    for rel, fn in (("include/linux/susfs_def.h", apply_def),
                    ("include/linux/susfs.h", apply_hdr),
                    ("fs/susfs.c", apply_src)):
        path = os.path.join(base, rel)
        if not os.path.isfile(path):
            die("kernel tree file missing: " + path)
        out.append(fn(path))
    return out


def patch_cli(root):
    path, text = load(root, "cli")
    if MARKER in text:
        return "skip", REL["cli"]
    text = sub_one(text, CLI_STRUCT_ANCHOR, CLI_STRUCT_ADD + CLI_STRUCT_ANCHOR, path, "hidden uid struct")
    text = insert_after_line(text, CLI_HELP_ANCHOR, CLI_HELP_ADD, path, "hidden uid help")
    text = sub_one(text, CLI_TAIL_ANCHOR, CLI_TAIL_ANCHOR + CLI_FN, path, "add_hidden_uid tail")
    save(path, text)
    return "patch", REL["cli"]


def patch_clih(root):
    path, text = load(root, "clih")
    if MARKER in text:
        return "skip", REL["clih"]
    text = insert_after_line(text, CLIH_ANCHOR, CLIH_ADD, path, CLIH_ANCHOR)
    # The CLI is a separate build: the kernel header that apply_def() patches is not
    # visible here, so the command id must be defined in this header as well,
    # otherwise sus_path.c fails on an undefined CMD_SUSFS_ADD_HIDDEN_UID_FROM.
    if DEF_LINE not in text:
        text = text.replace(CLIH_ANCHOR, DEF_LINE + "\n" + CLIH_ANCHOR, 1)
    save(path, text)
    return "patch", REL["clih"]


def patch_clim(root):
    path, text = load(root, "clim")
    if MARKER in text:
        return "skip", REL["clim"]
    text = sub_one(text, CLIM_ANCHOR, CLIM_ADD, path, "main.c add_hidden_uid dispatch")
    save(path, text)
    return "patch", REL["clim"]


def patch_sys(kernel_root):
    base = find_base(kernel_root)
    if base is None:
        die("kernel base with kernel/sys.c + fs/susfs.c not found under " + kernel_root)
    path = os.path.join(base, "kernel", "sys.c")
    text = load_path(path)
    if LOOKUP in text:
        return "skip", path
    count = text.count(SYS_CALL)
    if count != 2:
        die("uid oracle call sites matched " + str(count) + " times in " + path + " (expected 2)")
    text = text.replace(SYS_CALL, SYS_CALL_NEW)
    save(path, text)
    return "patch", path


def patch_dispatch(kernel_root):
    path = find_dispatch(kernel_root)
    if path is None:
        die("KernelSU supercall/dispatch.c not found under " + kernel_root)
    text = load_path(path)
    if CMD in text:
        return "skip", path
    if text.count(DISPATCH_ANCHOR) != 1:
        die("dispatch anchor '" + DISPATCH_ANCHOR + "' matched " + str(text.count(DISPATCH_ANCHOR)) +
            " times (expected 1); run the hidden-uid oracle step first")
    pos = text.find(DISPATCH_ANCHOR)
    end = find_case_end(text, pos)
    if end == -1:
        die("could not find the end of the " + GLOBAL_CMD + " case in " + path)
    text = text[:end + 1] + DISPATCH_CASE + text[end + 1:]
    inc = text.find(DISPATCH_INCLUDE)
    if inc != -1 and "#define " + CMD not in text:
        text = text[:inc] + DISPATCH_GUARD + text[inc:]
    save(path, text)
    return "patch", path


def main():
    if len(sys.argv) != 3:
        die("usage: patch_susfs_hidden_uid_from.py <susfs4ksu_dir> <kernel_root>")
    susfs_root = sys.argv[1]
    kernel_root = sys.argv[2]
    if not os.path.isdir(susfs_root):
        die("susfs4ksu dir not found: " + susfs_root)
    if not os.path.isdir(kernel_root):
        die("kernel root not found: " + kernel_root)

    results = [
        patch_def(susfs_root),
        patch_hdr(susfs_root),
        patch_src(susfs_root),
        patch_cli(susfs_root),
        patch_clih(susfs_root),
        patch_clim(susfs_root),
        patch_sys(kernel_root),
        patch_dispatch(kernel_root),
    ]
    results.extend(patch_tree(kernel_root))
    for state, name in results:
        print("[ABK] per-target hidden uid: " + state + " " + name)

    _, sdef = load(susfs_root, "def")
    _, shdr = load(susfs_root, "hdr")
    _, ssrc = load(susfs_root, "src")
    _, scli = load(susfs_root, "cli")
    _, sh = load(susfs_root, "clih")
    _, sm = load(susfs_root, "clim")
    base = find_base(kernel_root)
    sys_text = load_path(os.path.join(base, "kernel", "sys.c")) if base else ""
    disp = load_path(find_dispatch(kernel_root)) if find_dispatch(kernel_root) else ""
    tree_def = load_path(os.path.join(base, "include", "linux", "susfs_def.h")) if base else ""
    tree_hdr = load_path(os.path.join(base, "include", "linux", "susfs.h")) if base else ""
    tree_src = load_path(os.path.join(base, "fs", "susfs.c")) if base else ""

    checks = [
        (CMD in sdef, "susfs_def.h CMD"),
        (HANDLER in shdr and LOOKUP in shdr, "susfs.h prototypes"),
        ("ABK_HIDDEN_UID_FROM_HLIST" in ssrc and LOOKUP in ssrc, "susfs.c table+lookup"),
        (CLI in scli, "sus_path.c CLI fn"),
        (CLI in sh, "sus_path.h CLI proto"),
        (CLI in sm, "main.c CLI dispatch"),
        (LOOKUP in sys_text and "current_uid().val" in sys_text, "kernel/sys.c caller-aware hook"),
        ((("case " + CMD + ":") in disp) and (HANDLER + "(arg);" in disp), "dispatch.c case"),
        (CMD in tree_def, "tree susfs_def.h CMD"),
        (HANDLER in tree_hdr and LOOKUP in tree_hdr, "tree susfs.h prototypes"),
        ("ABK_HIDDEN_UID_FROM_HLIST" in tree_src and LOOKUP in tree_src, "tree susfs.c table+lookup"),
    ]
    bad = [name for ok, name in checks if not ok]
    if bad:
        die("per-target hidden uid self-check FAILED: " + ", ".join(bad))
    print("[ABK] per-target hidden uid verified: " + CMD + " = 0x55556, "
          + LOOKUP + "(uid, current_uid()), add_hidden_uid_from CLI, sys.c + dispatch.c wired")


if __name__ == "__main__":
    main()
