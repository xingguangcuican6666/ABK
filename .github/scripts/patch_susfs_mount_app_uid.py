#!/usr/bin/env python3
"""ABK: apply the SUSFS mount redirection to every app-uid process.

Usage:
    patch_susfs_mount_app_uid.py <kernel_root>

Background (measured against susfs4ksu gki-android14-6.1, SUSFS v2.3.0):

  fs/proc_namespace.c already exports the filtered/redirected mount view to
  every process outside the ksu domain once CMD_SUSFS_HIDE_SUS_MNTS_FOR_NON_SU_PROCS
  is enabled:

        if (static_branch_likely(&susfs_is_hide_sus_mnts_for_non_su_procs_enabled))
                if (likely(!susfs_is_current_ksu_domain()))
                        return mounts_open_common(inode, file, susfs_show_mountinfo);

  but the two remaining places that translate a sus mount back to its non-sus
  mount still fire only for TIF_PROC_UMOUNTED:

    fs/namespace.c  __lookup_mnt()                        -> path resolution
    fs/proc/fd.c    seq_show() (fdinfo mnt_id/ino)         -> /proc/<pid>/fdinfo

  ksu sets TIF_PROC_UMOUNTED only on processes it explicitly unmounted, so a
  normal or isolated app process keeps walking the sus mount while its
  mountinfo hides it: same path, two different answers.  That is the
  mountinfo/stat consistency class of check, and it also exposes the module /
  HMA mount through fdinfo.

This patcher installs the same predicate in both files:

    umounted                                      -> redirect (unchanged)
    hide_sus_mnts enabled + app uid + not ksu     -> redirect (new)
    everything else                               -> real mount (unchanged)

Kill switch: abk_sus_path_app_uid_gate=0 is reused where present; this
patcher's own additions are additive and only widen an existing gate for
app-uid processes, so su/ksu and uid<10000 keep the real view.

Touched files (relative to <kernel_root>, directly or under common/):
    fs/namespace.c
    fs/proc/fd.c

Every missing or ambiguous anchor aborts with exit code 2 and an ::error::
line.  Re-running is a no-op.
"""
import os
import re
import sys

MARKER = "SUS_MNT_APP_UID_GATE_ABK"
HELPER = "abk_mount_app_target"
REL_NS = os.path.join("fs", "namespace.c")
REL_FD = os.path.join("fs", "proc", "fd.c")

HELPER_TEXT = (
    "#ifdef CONFIG_KSU_SUSFS_SUS_MOUNT\n"
    "/* ABK " + MARKER + ": the mountinfo view is already filtered for every\n"
    " * non-ksu process, so path resolution and fdinfo must use the same\n"
    " * predicate or an app sees a sus mount that its mountinfo does not list.\n"
    " * su/ksu and uid < 10000 keep the real view.\n"
    " */\n"
    "extern bool susfs_is_current_ksu_domain(void);\n"
    "static inline bool " + HELPER + "(void)\n"
    "{\n"
    "\tif (likely(susfs_is_current_proc_umounted()))\n"
    "\t\treturn true;\n"
    "\tif (!susfs_is_current_app_uid())\n"
    "\t\treturn false;\n"
    "\treturn !susfs_is_current_ksu_domain();\n"
    "}\n"
    "#endif // #ifdef CONFIG_KSU_SUSFS_SUS_MOUNT\n"
    "\n"
)

# The 6.1 LTS header hunk of the upstream SUSFS patch does not apply to every
# kernel tag, so build.yml falls back to a sed that injects the same block with
# a different tail ("#define CL_COPY_MNT_NS BIT(25)" without the copy_mnt_ns()
# comment and a bare "#endif").  Anchor on the define only and accept both
# spellings of the closing guard.
NS_DEFINE_RE = re.compile(r"^#define CL_COPY_MNT_NS BIT\(25\)[^\n]*\n", re.M)

NS_GATE_OLD = (
    "\tif (susfs_is_current_proc_umounted()) {\n"
    "\t\thlist_for_each_entry_rcu(p, head, mnt_hash)\n"
)
NS_GATE_NEW = (
    "\t/* ABK " + MARKER + ": app-uid processes too */\n"
    "\tif (" + HELPER + "()) {\n"
    "\t\thlist_for_each_entry_rcu(p, head, mnt_hash)\n"
)

FD_ANCHOR = "static int seq_show(struct seq_file *m, void *v)\n{\n"
FD_GATE_OLD = (
    "\tif (likely(susfs_is_current_proc_umounted())) {\n"
    "\t\tstruct mount *mnt = real_mount(file->f_path.mnt);\n"
)
FD_GATE_NEW = (
    "\t/* ABK " + MARKER + ": app-uid processes too */\n"
    "\tif (likely(" + HELPER + "())) {\n"
    "\t\tstruct mount *mnt = real_mount(file->f_path.mnt);\n"
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


def insert_helper_after_ns_block(text, path):
    """Insert HELPER_TEXT right after the SUS_MOUNT guard that declares
    CL_COPY_MNT_NS, tolerating the build.yml 6.1 fallback spelling."""
    matches = list(NS_DEFINE_RE.finditer(text))
    if len(matches) != 1:
        die("anchor 'CL_COPY_MNT_NS block' matched " + str(len(matches)) +
            " times (expected 1) in " + path)
    end = matches[0].end()
    insert_at = end
    offset = 0
    for line in text[end:].splitlines(keepends=True)[:3]:
        stripped = line.strip()
        offset += len(line)
        if stripped.startswith("#endif"):
            insert_at = end + offset
            break
        if stripped and not stripped.startswith("#"):
            break
    return text[:insert_at] + HELPER_TEXT + text[insert_at:]


def main():
    if len(sys.argv) != 2:
        die("usage: patch_susfs_mount_app_uid.py <kernel_root>")
    root = sys.argv[1]
    base = None
    for candidate in (os.path.join(root, "common"), root):
        if all(os.path.isfile(os.path.join(candidate, rel)) for rel in (REL_NS, REL_FD)):
            base = candidate
            break
    if base is None:
        die("fs/namespace.c + fs/proc/fd.c not found under " + root +
            " (directly or in common/); the SUSFS mount patch is missing")

    ns = os.path.join(base, REL_NS)
    fd = os.path.join(base, REL_FD)
    print("[ABK] kernel tree: " + base)

    # ---- fs/namespace.c -------------------------------------------------
    text = load(ns)
    if MARKER in text:
        print("[ABK] skip: " + ns + " already carries " + MARKER)
    else:
        if NS_GATE_OLD not in text:
            die("the SUSFS __lookup_mnt() umounted gate is missing from " + ns +
                "; the SUSFS mount patch is missing or already changed")
        text = insert_helper_after_ns_block(text, ns)
        text = sub_one(text, NS_GATE_OLD, NS_GATE_NEW, ns,
                       "__lookup_mnt umounted gate")
        save(ns, text)
        print("[ABK] patch: " + ns)

    # ---- fs/proc/fd.c ---------------------------------------------------
    text = load(fd)
    if MARKER in text:
        print("[ABK] skip: " + fd + " already carries " + MARKER)
    else:
        if FD_GATE_OLD not in text:
            die("the SUSFS fdinfo umounted gate is missing from " + fd +
                "; the SUSFS mount patch is missing or already changed")
        text = sub_one(text, FD_ANCHOR, HELPER_TEXT + FD_ANCHOR, fd,
                       "seq_show definition")
        text = sub_one(text, FD_GATE_OLD, FD_GATE_NEW, fd, "fdinfo umounted gate")
        save(fd, text)
        print("[ABK] patch: " + fd)

    # ---- verification ---------------------------------------------------
    for path, label in ((ns, "fs/namespace.c"), (fd, "fs/proc/fd.c")):
        text = load(path)
        if MARKER not in text:
            die("verification failed: " + MARKER + " missing from " + path)
        if "static inline bool " + HELPER + "(void)" not in text:
            die("verification failed: " + HELPER + " definition missing from " + path)
        if "if (susfs_is_current_proc_umounted()) {\n\t\thlist_for_each_entry_rcu" in text:
            die("verification failed: the umounted-only __lookup_mnt gate is still in " + path)
    ns_text = load(ns)
    fd_text = load(fd)
    if ns_text.count(HELPER) != 2:
        die("verification failed: " + HELPER + " referenced " + str(ns_text.count(HELPER)) +
            " times in fs/namespace.c (expected 2: definition + gate)")
    if fd_text.count(HELPER) != 2:
        die("verification failed: " + HELPER + " referenced " + str(fd_text.count(HELPER)) +
            " times in fs/proc/fd.c (expected 2: definition + gate)")

    print("[ABK] mount redirect app-uid gate OK: " + MARKER +
          " (fs/namespace.c __lookup_mnt, fs/proc/fd.c fdinfo; apps + isolated, ksu/uid<10000 unchanged)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
