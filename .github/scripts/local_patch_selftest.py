#!/usr/bin/env python3
"""Rehearse the ABK kernel patch chain locally, in exactly the CI order.

Usage:
    local_patch_selftest.py                        # fixtures (fast, offline)
    local_patch_selftest.py --kernel-root <dir>    # a real tree (common/ or repo root)
    local_patch_selftest.py --list                 # print the chain, do not run

Why: every "build failed" round in this project came from a patcher anchor, not from
kernel code, and each round costs a 30+ minute CI build.  This script applies the same
patchers in the same order as .github/workflows/build.yml, so a broken anchor fails in
about a second.

Two modes:

* ``--kernel-root`` runs the real chain against a real kernel tree (use this before
  pushing anything that touches fs/susfs.c, fs/readdir.c, fs/proc_namespace.c or
  include/linux/susfs.h).  Every patcher is run; the first non-zero exit is reported
  with its log tail.
* fixtures mode builds a small synthetic tree that mirrors the *post-SUSFS* text our
  patchers expect (5 readdir actors, 5 callback structs, the hide-from fast path, the
  serialised policy load, two mountinfo show functions, the loop prototype).  Patchers
  whose target file or anchor the fixture does not carry are reported as SKIP, so the
  run still proves ordering, marker handling and idempotency for the covered ones.

The chain is parsed from build.yml, never hard-coded: whenever a patcher is added,
removed or reordered there, this script follows.
"""
import argparse
import os
import re
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
WORKFLOW = os.path.join(HERE, "..", "workflows", "build.yml")
SCRIPT_RE = re.compile(r"\.github/scripts/([A-Za-z0-9_]+\.py)")

# Patchers that this fixture tree can actually exercise (the rest need files the fixture
# does not model; they are reported as SKIP instead of failing the run).
FIXTURE_PATCHERS = (
    "patch_susfs_readdir_filter.py",
    "patch_abk_app_vis.py",
)

SRC = r'''#include <linux/fs.h>

#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 6, 0)
bool susfs_is_inode_sus_path(struct inode *inode)
{
	struct inode *sus_inode = inode;

	/* ABK SUS_PATH_UID_HIDE_FROM_ABK: per-target hide-from-uid deny fast path */
	if (unlikely(test_bit(AS_FLAGS_SUS_PATH_HIDE_FROM, &sus_inode->i_mapping->flags) &&
		    susfs_sus_path_uid_is_denied(sus_inode)))
		return true;
	return false;
}
#else
/* pre-6.6 variant: the brace sits on the same line, which is why the app-visibility
 * patcher (anchored on a trailing newline) only matches the 6.6+ definition. */
bool susfs_is_inode_sus_path(struct inode *inode) {
	struct inode *sus_inode = inode;

	return false;
}
#endif

static void susfs_hide_from_stub(struct st_susfs_sus_path_hlist *entry)
{
	if (entry->mode == 1)
		return;
}

static int susfs_magic_handler(void)
{
	if (strstr(info.target_pathname, "/.abk-policy")) {
		mutex_lock(&susfs_mutex_lock_policy_load);
		info.err = susfs_load_policy_file();
		mutex_unlock(&susfs_mutex_lock_policy_load);
	}
	return 0;
}
'''

HDR = (
    "void susfs_add_sus_path_loop(void __user **user_info);\n"
    "bool susfs_is_ino_sus_path_hidden(u64 ino, u64 dev);\n"
)

READDIR = r'''#include <asm/unaligned.h>

#ifdef CONFIG_KSU_SUSFS_SUS_PATH
#include <linux/susfs_def.h>
#endif

extern bool susfs_is_inode_sus_path(struct inode *inode);

struct readdir_callback {
	struct dir_context ctx;
	struct old_linux_dirent __user * dirent;
#ifdef CONFIG_KSU_SUSFS_SUS_PATH
	struct super_block *sb;
#endif
	int result;
};

struct getdents_callback {
	struct dir_context ctx;
	struct linux_dirent __user * current_dir;
#ifdef CONFIG_KSU_SUSFS_SUS_PATH
	struct super_block *sb;
#endif
	int prev_reclen;
	int error;
};

struct getdents64_callback {
	struct dir_context ctx;
	struct linux_dirent64 __user * current_dir;
#ifdef CONFIG_KSU_SUSFS_SUS_PATH
	struct super_block *sb;
#endif
	int prev_reclen;
	int error;
};

struct compat_readdir_callback {
	struct dir_context ctx;
	struct compat_old_linux_dirent __user * dirent;
#ifdef CONFIG_KSU_SUSFS_SUS_PATH
	struct super_block *sb;
#endif
	int result;
};

struct compat_getdents_callback {
	struct dir_context ctx;
	struct compat_linux_dirent __user * current_dir;
#ifdef CONFIG_KSU_SUSFS_SUS_PATH
	struct super_block *sb;
#endif
	int prev_reclen;
	int error;
};

static bool filldir(struct dir_context *ctx, const char *name, int namlen, loff_t offset, u64 ino, unsigned int d_type)
{
	struct getdents_callback *buf = container_of(ctx, struct getdents_callback, ctx);
	struct inode *inode;
	buf->error = verify_dirent_name(name, namlen);
	if (unlikely(buf->error))
		return false;
#ifdef CONFIG_KSU_SUSFS_SUS_PATH
	inode = ilookup(buf->sb, ino);
	if (!inode) {
		goto orig_flow;
	}
	if (susfs_is_inode_sus_path(inode)) {
		iput(inode);
		return true;
	}
	iput(inode);
orig_flow:
#endif
	return true;
}

static bool fillonedir(struct dir_context *ctx, const char *name, int namlen, loff_t offset, u64 ino, unsigned int d_type)
{
	struct readdir_callback *buf = container_of(ctx, struct readdir_callback, ctx);
#ifdef CONFIG_KSU_SUSFS_SUS_PATH
	inode = ilookup(buf->sb, ino);
	if (!inode) {
		goto orig_flow;
	}
	if (susfs_is_inode_sus_path(inode)) {
		iput(inode);
		return true;
	}
	iput(inode);
orig_flow:
#endif
	return true;
}

static bool compat_filldir(struct dir_context *ctx, const char *name, int namlen, loff_t offset, u64 ino, unsigned int d_type)
{
	struct compat_getdents_callback *buf = container_of(ctx, struct compat_getdents_callback, ctx);
#ifdef CONFIG_KSU_SUSFS_SUS_PATH
	inode = ilookup(buf->sb, ino);
	if (!inode) {
		goto orig_flow;
	}
	if (susfs_is_inode_sus_path(inode)) {
		iput(inode);
		return true;
	}
	iput(inode);
orig_flow:
#endif
	return true;
}

static bool filldir64(struct dir_context *ctx, const char *name, int namlen, loff_t offset, u64 ino, unsigned int d_type)
{
	struct getdents64_callback *buf = container_of(ctx, struct getdents64_callback, ctx);
#ifdef CONFIG_KSU_SUSFS_SUS_PATH
	inode = ilookup(buf->sb, ino);
	if (!inode) {
		goto orig_flow;
	}
	if (susfs_is_inode_sus_path(inode)) {
		iput(inode);
		return true;
	}
	iput(inode);
orig_flow:
#endif
	return true;
}

static bool compat_fillonedir(struct dir_context *ctx, const char *name, int namlen, loff_t offset, u64 ino, unsigned int d_type)
{
	struct compat_readdir_callback *buf = container_of(ctx, struct compat_readdir_callback, ctx);
#ifdef CONFIG_KSU_SUSFS_SUS_PATH
	inode = ilookup(buf->sb, ino);
	if (!inode) {
		goto orig_flow;
	}
	if (susfs_is_inode_sus_path(inode)) {
		iput(inode);
		return true;
	}
	iput(inode);
orig_flow:
#endif
	return true;
}

SYSCALL_DEFINE3(getdents, unsigned int, fd, struct linux_dirent __user *, dirent, unsigned int, count)
{
#ifdef CONFIG_KSU_SUSFS_SUS_PATH
	buf.sb = f.file->f_inode->i_sb;
#endif
	return 0;
}

SYSCALL_DEFINE3(getdents64, unsigned int, fd, struct linux_dirent64 __user *, dirent, unsigned int, count)
{
#ifdef CONFIG_KSU_SUSFS_SUS_PATH
	buf.sb = f.file->f_inode->i_sb;
#endif
	return 0;
}

SYSCALL_DEFINE3(old_readdir, unsigned int, fd, struct old_linux_dirent __user *, dirent, unsigned int, count)
{
#ifdef CONFIG_KSU_SUSFS_SUS_PATH
	buf.sb = f.file->f_inode->i_sb;
#endif
	return 0;
}
'''

MNTNS = r'''extern bool susfs_is_current_ksu_domain(void);

static int susfs_show_mountinfo(struct seq_file *m, struct vfsmount *mnt)
{
	struct mount *r = real_mount(mnt);
	if (r->mnt_id >= DEFAULT_KSU_MNT_ID)
		return 0;
	return 0;
}

static int susfs_show_vfsstat(struct seq_file *m, struct vfsmount *mnt)
{
	struct mount *r = real_mount(mnt);
	if (r->mnt_id >= DEFAULT_KSU_MNT_ID)
		return 0;
	return 0;
}

extern bool susfs_is_current_ksu_domain(void);
'''


def chain():
    """Patchers in the order build.yml runs them."""
    with open(WORKFLOW, "r", encoding="utf-8") as fh:
        text = fh.read()
    seen = []
    for name in SCRIPT_RE.findall(text):
        if name not in seen:
            seen.append(name)
    return [n for n in seen if n.startswith("patch_")]


def write_fixtures(root):
    layout = {
        os.path.join("common", "fs", "susfs.c"): SRC,
        os.path.join("common", "include", "linux", "susfs.h"): HDR,
        os.path.join("common", "fs", "readdir.c"): READDIR,
        os.path.join("common", "fs", "proc_namespace.c"): MNTNS,
    }
    for rel, text in layout.items():
        path = os.path.join(root, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8", newline="") as fh:
            fh.write(text)


def run(patcher, root):
    path = os.path.join(HERE, patcher)
    if not os.path.isfile(path):
        return "MISSING", ""
    proc = subprocess.run([sys.executable, path, root],
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                          universal_newlines=True)
    out = (proc.stdout or "") + (proc.stderr or "")
    return ("OK" if proc.returncode == 0 else "FAIL"), out.strip()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--kernel-root")
    ap.add_argument("--list", action="store_true")
    ap.add_argument("--keep", action="store_true", help="keep the fixture tree")
    args = ap.parse_args()

    patchers = chain()
    if args.list:
        for i, name in enumerate(patchers, 1):
            print("%2d. %s" % (i, name))
        return 0

    tmp = None
    if args.kernel_root:
        root = args.kernel_root
        if not os.path.isdir(os.path.join(root, "common")):
            print("note: %s has no common/, using it as the tree root" % root)
    else:
        tmp = tempfile.mkdtemp(prefix="abk-fixtures-")
        root = os.path.join(tmp, "tree")
        write_fixtures(root)
        print("fixtures: " + root)

    failures = []
    skipped = []
    for name in patchers:
        if not args.kernel_root and name not in FIXTURE_PATCHERS:
            skipped.append(name)
            continue
        status, out = run(name, root)
        if status == "MISSING":
            skipped.append(name)
            continue
        print("[%s] %s" % (status, name))
        if status == "FAIL":
            print("       " + out.replace("\n", "\n       ")[-1500:])
            failures.append(name)

    if skipped:
        print("SKIP (not covered by the fixture tree): " + ", ".join(skipped))
    if tmp and not args.keep:
        shutil.rmtree(tmp, ignore_errors=True)
    elif tmp:
        print("fixtures kept at " + root)

    if failures:
        print("FAILED: " + ", ".join(failures))
        return 1
    print("all run patchers applied cleanly")
    return 0


if __name__ == "__main__":
    sys.exit(main())
