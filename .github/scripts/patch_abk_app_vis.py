#!/usr/bin/env python3
"""ABK: app-side visibility hardenings (empty dirs, devpts ownership, /dev mounts).

Usage:
    patch_abk_app_vis.py <kernel_root>

Three independent rules, all gated on "the caller is an app uid" (>= 10000, i.e. not
root, not system, not the ksu domain):

1. **Empty listing for selected directories** (default ``/data/local/tmp``).
   The directory itself stays visible and stat-able; a non-root readdir of it returns
   no entries, so exploit staging areas and container sockets disappear without
   breaking paths that scripts expect to exist.  Configure with
   ``abk_empty_dirs=/data/local/tmp,/some/other``.

2. **devpts ownership** (``/dev/pts/N``).
   A pty is created for one uid; every other uid sees a root-owned entry that on a
   stock device is unreadable.  Duck Detector flags exactly that
   ("/dev/pts/0: Abnormal Permission Detected").  An app now only sees the pty
   entries it owns itself.

3. **Foreign mounts below /dev**.
   A container/root daemon mounts images or tmpfs under /dev; those mountpoints are
   root-owned and use a non-dev filesystem (ext4/f2fs/erofs/...), which no stock
   device has.  Mountinfo for app uids skips them; devtmpfs/devpts/tmpfs/mqueue and
   friends stay visible.

Touched: fs/susfs.c, fs/readdir.c, fs/proc_namespace.c, include/linux/susfs.h.
Run after patch_susfs_readdir_filter.py, patch_susfs_mount_app_uid.py and the
mount_per_uid patcher (it anchors on their output).
"""
import os
import sys

MARKER = "ABK_APP_VIS"
REL = {
    "src": os.path.join("fs", "susfs.c"),
    "hdr": os.path.join("include", "linux", "susfs.h"),
    "readdir": os.path.join("fs", "readdir.c"),
    "mntns": os.path.join("fs", "proc_namespace.c"),
}

SRC_BLOCK = (
    "\n/* ABK " + MARKER + ": app-side visibility rules (see patch_abk_app_vis.py). */\n"
    "#include <linux/mount.h>\n"
    "#include <linux/dcache.h>\n"
    "#include <linux/magic.h>\n"
    "\n"
    "static char abk_empty_dirs[192] = \"/data/local/tmp\";\n"
    "static int __init abk_empty_dirs_setup(char *str)\n"
    "{\n"
    "\tif (str && *str)\n"
    "\t\tstrscpy(abk_empty_dirs, str, sizeof(abk_empty_dirs));\n"
    "\treturn 1;\n"
    "}\n"
    "__setup(\"abk_empty_dirs\", abk_empty_dirs_setup);\n"
    "\n"
    "#define ABK_MASK_DIRS 8\n"
    "struct abk_mask_dir {\n"
    "\tu64 ino;\n"
    "\tu64 dev;\n"
    "};\n"
    "static struct abk_mask_dir abk_mask_dirs[ABK_MASK_DIRS];\n"
    "static bool abk_mask_ready;\n"
    "\n"
    "/* Resolve the configured directories.  Called from the policy supercall (root,\n"
    " * no locks held): doing a path walk from the readdir hook would take the listed\n"
    " * directory's i_rwsem again.\n"
    " */\n"
    "void susfs_dir_mask_init(void)\n"
    "{\n"
    "\tchar buf[sizeof(abk_empty_dirs)];\n"
    "\tchar *p, *item;\n"
    "\tstruct path kp;\n"
    "\tint i = 0;\n"
    "\n"
    "\tif (abk_mask_ready)\n"
    "\t\treturn;\n"
    "\tstrscpy(buf, abk_empty_dirs, sizeof(buf));\n"
    "\tp = buf;\n"
    "\twhile (p && *p && i < ABK_MASK_DIRS) {\n"
    "\t\titem = strsep(&p, \",\");\n"
    "\t\tif (!item || !*item)\n"
    "\t\t\tcontinue;\n"
    "\t\tif (!kern_path(item, LOOKUP_FOLLOW, &kp)) {\n"
    "\t\t\tif (kp.dentry && kp.dentry->d_inode) {\n"
    "\t\t\t\tabk_mask_dirs[i].ino = kp.dentry->d_inode->i_ino;\n"
    "\t\t\t\tabk_mask_dirs[i].dev = kp.dentry->d_inode->i_sb->s_dev;\n"
    "\t\t\t\ti++;\n"
    "\t\t\t}\n"
    "\t\t\tpath_put(&kp);\n"
    "\t\t}\n"
    "\t}\n"
    "\tabk_mask_ready = true;\n"
    "}\n"
    "EXPORT_SYMBOL_GPL(susfs_dir_mask_init);\n"
    "\n"
    "/* true = the caller must not see this directory's entries */\n"
    "bool susfs_is_dir_masked(u64 ino, u64 dev)\n"
    "{\n"
    "\tint i;\n"
    "\n"
    "\tif (current_uid().val < 10000)\n"
    "\t\treturn false;\n"
    "\tif (unlikely(!abk_mask_ready))\n"
    "\t\treturn false;\n"
    "\tfor (i = 0; i < ABK_MASK_DIRS; i++)\n"
    "\t\tif (abk_mask_dirs[i].ino == ino && abk_mask_dirs[i].dev == dev)\n"
    "\t\t\treturn true;\n"
    "\treturn false;\n"
    "}\n"
    "EXPORT_SYMBOL_GPL(susfs_is_dir_masked);\n"
    "\n"
    "static bool abk_mount_under_dev(struct mount *r)\n"
    "{\n"
    "\tstruct dentry *parent;\n"
    "\n"
    "\tif (!r->mnt_mountpoint)\n"
    "\t\treturn false;\n"
    "\tparent = r->mnt_mountpoint->d_parent;\n"
    "\tif (!parent)\n"
    "\t\treturn false;\n"
    "\treturn parent->d_name.len == 3 &&\n"
    "\t       !memcmp(parent->d_name.name, \"dev\", 3);\n"
    "}\n"
    "\n"
    "static bool abk_dev_fs_whitelisted(const char *type)\n"
    "{\n"
    "\tstatic const char *const ok[] = {\n"
    "\t\t\"devtmpfs\", \"devpts\", \"tmpfs\", \"mqueue\", \"binder\", \"binderfs\",\n"
    "\t\t\"cgroup\", \"cgroup2\", \"selinuxfs\", \"proc\", \"sysfs\", \"configfs\",\n"
    "\t\t\"bpf\", \"tracefs\", \"debugfs\", \"fuse\", \"fuseblk\", \"overlay\",\n"
    "\t};\n"
    "\tint i;\n"
    "\n"
    "\tfor (i = 0; i < (int)ARRAY_SIZE(ok); i++)\n"
    "\t\tif (!strcmp(type, ok[i]))\n"
    "\t\t\treturn true;\n"
    "\treturn false;\n"
    "}\n"
    "\n"
    "/* true = hide this mount from app uids (container/root staging below /dev) */\n"
    "bool susfs_is_foreign_dev_mount(struct mount *r)\n"
    "{\n"
    "\tif (!r || !r->mnt_sb || !r->mnt_sb->s_type)\n"
    "\t\treturn false;\n"
    "\tif (current_uid().val < 10000)\n"
    "\t\treturn false;\n"
    "\tif (!abk_mount_under_dev(r))\n"
    "\t\treturn false;\n"
    "\tif (abk_dev_fs_whitelisted(r->mnt_sb->s_type->name))\n"
    "\t\treturn false;\n"
    "\t/* only root-owned mountpoints: a system mountpoint is never root here */\n"
    "\tif (!r->mnt_mountpoint->d_inode ||\n"
    "\t    r->mnt_mountpoint->d_inode->i_uid.val != 0)\n"
    "\t\treturn false;\n"
    "\treturn true;\n"
    "}\n"
    "EXPORT_SYMBOL_GPL(susfs_is_foreign_dev_mount);\n"
)

HDR_BLOCK = (
    "\n/* ABK " + MARKER + " */\n"
    "void susfs_dir_mask_init(void);\n"
    "bool susfs_is_dir_masked(u64 ino, u64 dev);\n"
    "struct mount;\n"
    "bool susfs_is_foreign_dev_mount(struct mount *r);\n"
)

DEVPTS_RULE = (
    "\t/* ABK " + MARKER + ": a pty belongs to its owner; other uids see a\n"
    "\t * root-owned entry that stock devices make unreadable anyway.\n"
    "\t */\n"
    "\tif (inode->i_sb->s_magic == DEVPTS_SUPER_MAGIC &&\n"
    "\t    current_uid().val >= 10000 &&\n"
    "\t    inode->i_uid.val != current_uid().val)\n"
    "\t\treturn true;\n"
)

MASK_CALL = (
    "\t/* ABK " + MARKER + ": listed directory is masked for app uids */\n"
    "\tif (unlikely(susfs_is_dir_masked((u64)buf->dir_ino, (u64)buf->dir_dev)))\n"
    "\t\treturn %s;\n"
)

MNT_COND_OLD = "\tif (r->mnt_id >= DEFAULT_KSU_MNT_ID)\n\t\treturn 0;\n"
MNT_COND_NEW = (
    "\tif (r->mnt_id >= DEFAULT_KSU_MNT_ID)\n"
    "\t\treturn 0;\n"
    "\t/* ABK " + MARKER + ": container/root mounts below /dev are not real */\n"
    "\tif (unlikely(susfs_is_foreign_dev_mount(r)))\n"
    "\t\treturn 0;\n"
)

POLICY_ANCHOR = "\t\tmutex_lock(&susfs_mutex_lock_policy_load);\n"
POLICY_ADD = (
    "\t\tsusfs_dir_mask_init(); /* ABK " + MARKER + " */\n"
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


def base_of(root):
    for candidate in (os.path.join(root, "common"), root):
        if os.path.isfile(os.path.join(candidate, REL["src"])):
            return candidate
    die("SUSFS sources not found under " + root)


def patch_src(path):
    text = load(path)
    if MARKER in text:
        return "skip"
    anchor = "bool susfs_is_inode_sus_path(struct inode *inode)\n"
    if text.count(anchor) != 1:
        die("susfs_is_inode_sus_path definition anchor matched %d times" % text.count(anchor))
    text = text.replace(anchor, SRC_BLOCK + "\n" + anchor, 1)

    fast = "\t/* ABK SUS_PATH_UID_HIDE_FROM_ABK: per-target hide-from-uid deny fast path */\n"
    if text.count(fast) != 1:
        die("hide-from fast path anchor matched %d times" % text.count(fast))
    text = text.replace(fast, DEVPTS_RULE + fast, 1)

    # initialise the mask list from the policy supercall (root context, no locks)
    if text.count(POLICY_ANCHOR) != 1:
        die("policy load mutex anchor matched %d times" % text.count(POLICY_ANCHOR))
    text = text.replace(POLICY_ANCHOR, POLICY_ADD + POLICY_ANCHOR, 1)
    save(path, text)
    return "patch"


def patch_hdr(path):
    text = load(path)
    if MARKER in text:
        return "skip"
    # susfs.h declares it with an extern, the definition does not.
    anchor = None
    for candidate in ("extern bool susfs_is_inode_sus_path(struct inode *inode);\n",
                      "bool susfs_is_inode_sus_path(struct inode *inode);\n"):
        if text.count(candidate) == 1:
            anchor = candidate
            break
    if anchor is None:
        die("susfs.h predicate anchor matched %d/%d times" %
            (text.count("extern bool susfs_is_inode_sus_path(struct inode *inode);\n"),
             text.count("bool susfs_is_inode_sus_path(struct inode *inode);\n")))
    text = text.replace(anchor, anchor + HDR_BLOCK, 1)
    save(path, text)
    return "patch"


def patch_readdir(path):
    text = load(path)
    if MARKER in text:
        return "skip"

    field_anchor = "#ifdef CONFIG_KSU_SUSFS_SUS_PATH\n\tstruct super_block *sb;\n#endif\n"
    n_fields = text.count(field_anchor)
    if n_fields < 1:
        die("readdir callback sb field anchor not found in " + path)
    text = text.replace(
        field_anchor,
        "#ifdef CONFIG_KSU_SUSFS_SUS_PATH\n"
        "\tstruct super_block *sb;\n"
        "\tu64 dir_ino; /* ABK " + MARKER + " */\n"
        "\tu64 dir_dev; /* ABK " + MARKER + " */\n"
        "#endif\n",
    )

    sb_anchor = "\tbuf.sb = f.file->f_inode->i_sb;\n"
    n_sb = text.count(sb_anchor)
    if n_sb < 1:
        die("readdir sb assignment anchor not found in " + path)
    text = text.replace(
        sb_anchor,
        sb_anchor
        + "\tbuf.dir_ino = f.file->f_inode->i_ino; /* ABK " + MARKER + " */\n"
        + "\tbuf.dir_dev = f.file->f_inode->i_sb->s_dev; /* ABK " + MARKER + " */\n",
    )

    # readdir.c does not include susfs.h: declare the helper locally
    incl = "extern bool susfs_is_inode_sus_path(struct inode *inode);\n"
    if text.count(incl) != 1:
        die("readdir predicate extern anchor matched %d times" % text.count(incl))
    text = text.replace(
        incl,
        incl + "extern bool susfs_is_dir_masked(u64 ino, u64 dev); /* ABK " + MARKER + " */\n",
        1,
    )

    ilo = "\tinode = ilookup(buf->sb, ino);\n"
    n_ilo = text.count(ilo)
    if n_ilo < 3:
        die("readdir ilookup anchor matched %d times (expected >= 3)" % n_ilo)
    for _ in range(n_ilo):
        pos = text.index(ilo)
        head = text[:pos]
        kind = "true"
        if head.rfind("static int ") > head.rfind("static bool "):
            kind = "0"
        text = text[:pos] + (MASK_CALL % kind) + text[pos:]
    save(path, text)
    return "patch (%d actors, %d structs)" % (n_ilo, n_fields)


def patch_mntns(path):
    text = load(path)
    if MARKER in text:
        return "skip"
    # proc_namespace.c does not include susfs.h either: declare the helper locally
    extern_anchor = "extern bool susfs_is_current_ksu_domain(void);\n"
    if text.count(extern_anchor) != 1:
        die("proc_namespace ksu-domain extern anchor matched %d times" % text.count(extern_anchor))
    text = text.replace(
        extern_anchor,
        extern_anchor
        + "extern bool susfs_is_foreign_dev_mount(struct mount *r); /* ABK " + MARKER + " */\n",
        1,
    )
    n = text.count(MNT_COND_OLD)
    if n != 2:
        die("mountinfo KSU-mount anchor matched %d times (expected 2)" % n)
    text = text.replace(MNT_COND_OLD, MNT_COND_NEW)
    save(path, text)
    return "patch (%d show functions)" % n


def main():
    if len(sys.argv) != 2:
        die("usage: patch_abk_app_vis.py <kernel_root>")
    base = base_of(sys.argv[1])
    print("[ABK] kernel tree: " + base)
    print("[ABK] " + patch_src(os.path.join(base, REL["src"])) + ": " + REL["src"])
    print("[ABK] " + patch_hdr(os.path.join(base, REL["hdr"])) + ": " + REL["hdr"])
    print("[ABK] " + patch_readdir(os.path.join(base, REL["readdir"])) + ": " + REL["readdir"])
    print("[ABK] " + patch_mntns(os.path.join(base, REL["mntns"])) + ": " + REL["mntns"])
    return 0


if __name__ == "__main__":
    sys.exit(main())
