#!/usr/bin/env python3
"""ABK: give BakaSU/SukiSU the SUSFS sus-type + AVD registration hooks.

Usage:
    patch_susfs_bakasu_sus_types.py <kernel_root>

Background (susfs4ksu gki-android14-6.1, a0ac906):

    Up to 5192aa2d the kernel-side SUSFS patch hid SELinux contexts with plain
    wrappers around selinuxfs (ksu_selinux_hide_running + a fake status page).
    Since the "selinux_hide: Reuse upstream selinux_hide existing code" /
    "Refactor selinux_hide" commits, SUSFS moved to a registration model:

      * kernel/fs/susfs.c keeps SUS_TYPE_HLIST and an AVD spoof table and
        exports susfs_add_sus_type() / susfs_add_sus_avd_allowed_by_type();
      * security/selinux/{selinuxfs,ss/services,status}.c consult those tables
        in susfs_security_context_to_sid() / susfs_security_compute_av_user()
        / the status page hooks;
      * KernelSU is expected to register every type and every rule it adds.

    Those two registration calls live in 10_enable_susfs_for_ksu.patch:

        kernel/selinux/sepolicy.c  add_type():      susfs_add_sus_type(type_name, type->value);
        kernel/selinux/sepolicy.c  add_rule_raw():  susfs_add_sus_avd_allowed_by_type(
                                                        src->value, tgt->value, cls->value,
                                                        node->datum.u.data);

    ABK only applies that KernelSU patch for the Official variant
    ("BakaSU 使用内置 SUSFS 支持"), so a BakaSU kernel runs the new kernel-side
    hooks with an empty SUS_TYPE_HLIST: u:r:ksu:s0 / u:object_r:ksu_file:s0 /
    magisk / lsposed contexts still resolve, and rules such as
    "untrusted_app -> ksu_file read" still compute allowed.

    This patcher adds the same two registration calls to BakaSU/SukiSU's
    sepolicy.c, guarded by CONFIG_KSU_SUSFS, exactly like the upstream
    KernelSU patch does.

Behaviour:
    * no kernel/selinux/sepolicy.c        -> not a BakaSU tree, skip;
    * sepolicy.c without the anchors     -> skip (unknown layout);
    * already patched                    -> no-op.

Touched file: <ksu_dir>/kernel/selinux/sepolicy.c
"""
import os
import sys

MARKER = "SUSFS_SUS_TYPE_REG_ABK"
REL = os.path.join("kernel", "selinux", "sepolicy.c")

CANDIDATES = (
    os.path.join("KernelSU", "kernel"),
    os.path.join("common", "KernelSU", "kernel"),
    os.path.join("KernelSU"),
    os.path.join("drivers", "kernelsu"),
    os.path.join("common", "drivers", "kernelsu"),
    "",
)

INCLUDE_ANCHOR = '#include "ss/symtab.h"\n'
INCLUDE_ANCHOR_FALLBACK = '#include "selinux/sepolicy.h"\n'

EXTERN_BLOCK = (
    "\n#ifdef CONFIG_KSU_SUSFS\n"
    "/* ABK " + MARKER + ": SUSFS >= a0ac906 hides SELinux contexts through\n"
    " * SUS_TYPE_HLIST / the AVD spoof table, and expects KernelSU to register\n"
    " * every type and rule it adds (see 10_enable_susfs_for_ksu.patch).\n"
    " */\n"
    "extern int susfs_add_sus_type(const char *secctx, u32 type_value);\n"
    "extern int susfs_add_sus_avd_allowed_by_type(u32 s_type, u32 t_type, u16 type, u32 orig_avd_allowed);\n"
    "#endif // #ifdef CONFIG_KSU_SUSFS\n"
)

RULE_ANCHOR = (
    "        if (invert && effect != AVTAB_AUDITDENY) {\n"
    "            node = avtab_search_node(&db->te_avtab, &key);\n"
    "            if (!node)\n"
    "                return true;\n"
    "        } else {\n"
    "            node = get_avtab_node(db, &key, NULL);\n"
    "            if (!node)\n"
    "                return false;\n"
    "        }\n"
)
RULE_ADD = (
    "\n#ifdef CONFIG_KSU_SUSFS\n"
    "        /* ABK " + MARKER + ": remember the pre-KernelSU decision */\n"
    "        susfs_add_sus_avd_allowed_by_type(src->value, tgt->value, cls->value, node->datum.u.data);\n"
    "#endif // #ifdef CONFIG_KSU_SUSFS\n"
)

TYPE_ANCHOR = (
    "    int i;\n"
    "    for (i = 0; i < db->p_roles.nprim; ++i) {\n"
    "        ebitmap_set_bit(&db->role_val_to_struct[i]->types, value - 1, 1);\n"
    "    }\n"
)
TYPE_ADD = (
    "    int i;\n"
    "    for (i = 0; i < db->p_roles.nprim; ++i) {\n"
    "        ebitmap_set_bit(&db->role_val_to_struct[i]->types, value - 1, 1);\n"
    "    }\n"
    "\n"
    "#ifdef CONFIG_KSU_SUSFS\n"
    "    susfs_add_sus_type(type_name, type->value);\n"
    "#endif // #ifdef CONFIG_KSU_SUSFS\n"
)


def die(msg):
    sys.stderr.write("::error::" + msg + "\n")
    sys.exit(2)


def find_sepolicy(root):
    for rel in CANDIDATES:
        path = os.path.join(root, rel, REL)
        if os.path.isfile(path):
            return path
    return None


def main():
    if len(sys.argv) != 2:
        die("usage: patch_susfs_bakasu_sus_types.py <kernel_root>")
    root = sys.argv[1]

    path = find_sepolicy(root)
    if path is None:
        print("[ABK] skip: no KernelSU-style kernel/selinux/sepolicy.c under " + root)
        return 0

    with open(path, "r", encoding="utf-8", newline="") as fh:
        text = fh.read()

    if MARKER in text:
        print("[ABK] skip: " + path + " already carries " + MARKER)
        return 0

    if "susfs_add_sus_type" in text:
        print("[ABK] skip: " + path + " already registers sus types")
        return 0

    if RULE_ANCHOR not in text or TYPE_ANCHOR not in text:
        print("[ABK] skip: " + path + " has an unknown sepolicy layout")
        return 0

    if text.count(RULE_ANCHOR) != 1:
        die("anchor 'add_rule_raw avtab node lookup' is ambiguous in " + path)
    type_hits = text.count(TYPE_ANCHOR)
    if type_hits < 1:
        die("anchor 'add_type role_val_to_struct tail' not found in " + path)

    include_anchor = INCLUDE_ANCHOR if INCLUDE_ANCHOR in text else INCLUDE_ANCHOR_FALLBACK
    if include_anchor not in text:
        die("no include anchor for the extern block in " + path)
    text = text.replace(include_anchor, include_anchor + EXTERN_BLOCK, 1)

    text = text.replace(RULE_ANCHOR, RULE_ANCHOR + RULE_ADD, 1)
    # add_type() keeps one body per preprocessor branch; only one compiles, so
    # register in every branch to stay independent of the compat selection.
    text = text.replace(TYPE_ANCHOR, TYPE_ADD)

    with open(path, "w", encoding="utf-8", newline="") as fh:
        fh.write(text)

    print("[ABK] patch: " + path)

    with open(path, "r", encoding="utf-8", newline="") as fh:
        check = fh.read()
    if check.count(MARKER) != 2:
        die("verification failed: expected 2 " + MARKER + " markers in " + path +
            ", found " + str(check.count(MARKER)))
    if check.count("susfs_add_sus_type(type_name, type->value);") != type_hits:
        die("verification failed: add_type registration missing from " + path +
            " (expected " + str(type_hits) + " sites)")
    if check.count("susfs_add_sus_avd_allowed_by_type(src->value, tgt->value, cls->value, node->datum.u.data);") != 1:
        die("verification failed: add_rule_raw registration missing from " + path)
    print("[ABK] SUSFS sus-type registration OK: " + MARKER +
          " (add_type + add_rule_raw now feed SUS_TYPE_HLIST / AVD spoof table)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
