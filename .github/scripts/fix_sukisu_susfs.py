#!/usr/bin/env python3
from pathlib import Path
import re
import sys


def die(message):
    raise SystemExit(message)


def write_if_changed(path, text, original, changed_files):
    if text != original:
        path.write_text(text)
        changed_files.append(str(path))


def ensure_include(text, include, anchor):
    if include in text:
        return text
    if anchor not in text:
        die(f"include anchor not found for {include}: {anchor!r}")
    return text.replace(anchor, anchor + include + "\n", 1)


def replace_or_confirm(text, old, new, marker, description):
    if old in text:
        return text.replace(old, new, 1)
    if marker in text:
        return text
    die(f"missing {description}")


def find_ksu_dir(root):
    candidates = (
        root / "common/drivers/kernelsu",
        root / "drivers/kernelsu",
        root / "KernelSU/kernel",
        root / "kernel",
    )
    for path in candidates:
        if (path / "Kbuild").exists():
            return path
    die("SukiSU source directory not found")


def patch_sucompat_header(path, changed_files):
    original = path.read_text()
    text = original

    text = ensure_include(text, "#include <linux/fs.h>", "#include <linux/types.h>\n")
    text = ensure_include(text, "#include <linux/static_key.h>", "#include <linux/types.h>\n")
    text = ensure_include(text, "#include <linux/version.h>", "#include <linux/types.h>\n")
    text = text.replace(
        "extern bool ksu_su_compat_enabled;",
        "extern struct static_key_true ksu_su_compat_enabled;",
    )

    old = "int ksu_handle_stat(int *dfd, const char __user **filename_user, int *flags);"
    new = """#if defined(CONFIG_KSU_SUSFS) && LINUX_VERSION_CODE >= KERNEL_VERSION(6, 1, 0)
int ksu_handle_stat(int *dfd, struct filename **filename, int *flags);
#else
int ksu_handle_stat(int *dfd, const char __user **filename_user, int *flags);
#endif"""
    if "struct filename **filename" not in text:
        if old not in text:
            die("missing sucompat stat prototype")
        text = text.replace(old, new, 1)

    write_if_changed(path, text, original, changed_files)


def patch_sucompat_c(path, changed_files):
    original = path.read_text()
    text = original

    text = ensure_include(text, "#include <linux/err.h>", "#include <linux/compiler_types.h>\n")
    text = ensure_include(text, "#include <linux/static_key.h>", "#include <linux/compiler_types.h>\n")
    text = text.replace(
        "bool ksu_su_compat_enabled __read_mostly = true;",
        "DEFINE_STATIC_KEY_TRUE(ksu_su_compat_enabled);",
    )
    text = text.replace(
        "    *value = ksu_su_compat_enabled ? 1 : 0;",
        "    *value = static_key_enabled(&ksu_su_compat_enabled) ? 1 : 0;",
    )
    text = text.replace(
        "    ksu_su_compat_enabled = enable;",
        "    if (enable)\n"
        "        static_branch_enable(&ksu_su_compat_enabled);\n"
        "    else\n"
        "        static_branch_disable(&ksu_su_compat_enabled);",
    )

    if "int ksu_handle_execveat_sucompat" not in text:
        marker = "\nint ksu_handle_faccessat("
        if marker not in text:
            die(f"missing faccessat insertion anchor: {path}")
        block = r'''
#ifdef CONFIG_KSU_SUSFS
extern void ksu_handle_execveat_ksud(const char *path, void *argv);

int ksu_handle_execveat_sucompat(int *fd, struct filename **filename_ptr,
                                 void *argv, void *envp, int *flags)
{
    struct filename *filename;
    int ret;

    (void)fd;
    (void)argv;
    (void)envp;
    (void)flags;

    if (unlikely(!filename_ptr || !*filename_ptr || IS_ERR(*filename_ptr)))
        return 0;

    filename = *filename_ptr;
    if (unlikely(!filename->name))
        return 0;

    if (!ksu_is_allow_uid_for_current(current_uid().val))
        return 0;

    if (likely(memcmp(filename->name, SU_PATH, sizeof(SU_PATH))))
        return 0;

    pr_info("ksu_handle_execveat_sucompat: su found\n");
    memcpy((void *)filename->name, KSUD_PATH, sizeof(KSUD_PATH));

    ret = escape_with_root_profile();
    if (ret)
        pr_err("escape_with_root_profile() failed: %d\n", ret);

    return 0;
}

int ksu_handle_execveat(int *fd, struct filename **filename_ptr, void *argv,
                        void *envp, int *flags)
{
    if (filename_ptr && *filename_ptr && !IS_ERR(*filename_ptr) && (*filename_ptr)->name)
        ksu_handle_execveat_ksud((*filename_ptr)->name, argv);

    return ksu_handle_execveat_sucompat(fd, filename_ptr, argv, envp, flags);
}
#endif
'''
        text = text.replace(marker, block + marker, 1)

    if "int ksu_handle_stat(int *dfd, struct filename **filename" not in text:
        pattern = re.compile(
            r"(int ksu_handle_stat\(int \*dfd, const char __user \*\*filename_user, int \*flags\)\n"
            r"\{.*?\n\})\n\nlong ksu_handle_execve_sucompat",
            re.S,
        )
        match = pattern.search(text)
        if not match:
            die(f"missing stat function anchor: {path}")
        old_func = match.group(1)
        new_func = f"""#if defined(CONFIG_KSU_SUSFS) && LINUX_VERSION_CODE >= KERNEL_VERSION(6, 1, 0)
int ksu_handle_stat(int *dfd, struct filename **filename, int *flags)
{{
    (void)dfd;
    (void)flags;

    if (unlikely(!filename || !*filename || IS_ERR(*filename) || !(*filename)->name))
        return 0;

    if (!ksu_is_allow_uid_for_current(current_uid().val))
        return 0;

    if (likely(memcmp((*filename)->name, SU_PATH, sizeof(SU_PATH))))
        return 0;

    pr_info("newfstatat su->sh!\\n");
    memcpy((void *)(*filename)->name, SH_PATH, sizeof(SH_PATH));
    return 0;
}}
#else
{old_func}
#endif"""
        text = text[: match.start(1)] + new_func + text[match.end(1) :]

    write_if_changed(path, text, original, changed_files)


def patch_syscall_bridge(path, changed_files):
    original = path.read_text()
    text = original

    text = text.replace(
        "if (!ksu_su_compat_enabled)",
        "if (!static_branch_likely(&ksu_su_compat_enabled))",
    )
    text = text.replace(
        "} else if (ksu_su_compat_enabled) {",
        "} else if (static_branch_likely(&ksu_su_compat_enabled)) {",
    )

    if "CONFIG_KSU_SUSFS\n    return ksu_syscall_table[orig_nr](regs);" not in text:
        pattern = re.compile(
            r"long __nocfi ksu_hook_newfstatat\(int orig_nr, const struct pt_regs \*regs\)\n"
            r"\{.*?\n\}\n\nlong __nocfi ksu_hook_faccessat",
            re.S,
        )
        match = pattern.search(text)
        if not match:
            die(f"missing newfstatat function anchor: {path}")
        new_func = r'''long __nocfi ksu_hook_newfstatat(int orig_nr, const struct pt_regs *regs)
{
#ifdef CONFIG_KSU_SUSFS
    return ksu_syscall_table[orig_nr](regs);
#else
    int *dfd;
    const char __user **filename_user;
    int *flags;

    if (!static_branch_likely(&ksu_su_compat_enabled))
        return ksu_syscall_table[orig_nr](regs);

    dfd = (int *)&PT_REGS_PARM1(regs);
    filename_user = (const char __user **)&PT_REGS_PARM2(regs);
    flags = (int *)&PT_REGS_SYSCALL_PARM4(regs);
    ksu_handle_stat(dfd, filename_user, flags);

    return ksu_syscall_table[orig_nr](regs);
#endif
}

long __nocfi ksu_hook_faccessat'''
        text = text[: match.start()] + new_func + text[match.end() :]

    write_if_changed(path, text, original, changed_files)


def patch_symbol_resolver(path, changed_files):
    original = path.read_text()
    text = original

    old = r'''void *ksu_resolve_symbol_for_functable_hook(const char *symbol_name)
{
    void *addr;
    size_t symbol_len;

    if (!symbol_name || !symbol_name[0])
        return NULL;

    symbol_len = strlen(symbol_name);

    // Prefer find_kernel_symbol_exact since it uses binary search in higher kernel version

#if !USE_KCFI
    // Try .cfi_jt suffix first
    char cfi_name[KSYM_NAME_LEN];
    snprintf(cfi_name, sizeof(cfi_name), "%s.cfi_jt", symbol_name);
    addr = (void *)find_kernel_symbol_exact(cfi_name);
    if (addr)
        return addr;

    addr = resolve_symbol_variant(symbol_name, symbol_len);
    if (addr)
        return addr;

    return (void *)find_kernel_symbol_exact(symbol_name);
#else
    addr = (void *)find_kernel_symbol_exact(symbol_name);
    if (addr)
        return addr;

    return resolve_symbol_variant(symbol_name, symbol_len);
#endif
}'''
    new = r'''void *ksu_resolve_symbol_for_functable_hook(const char *symbol_name)
{
    void *addr;
    size_t symbol_len;
    bool selinux_setprocattr_fallback = false;

    if (!symbol_name || !symbol_name[0])
        return NULL;

    symbol_len = strlen(symbol_name);
    selinux_setprocattr_fallback = !strcmp(symbol_name, "selinux_setprocattr");

    // Prefer find_kernel_symbol_exact since it uses binary search in higher kernel version

#if !USE_KCFI
    // Try .cfi_jt suffix first
    char cfi_name[KSYM_NAME_LEN];
    snprintf(cfi_name, sizeof(cfi_name), "%s.cfi_jt", symbol_name);
    addr = (void *)find_kernel_symbol_exact(cfi_name);
    if (addr)
        return addr;

    if (selinux_setprocattr_fallback) {
        snprintf(cfi_name, sizeof(cfi_name), "%s.cfi_jt", "security_setprocattr");
        addr = (void *)find_kernel_symbol_exact(cfi_name);
        if (addr) {
            pr_info("%s: resolved fallback %s via .cfi_jt\n", __func__, "security_setprocattr");
            return addr;
        }
    }

    addr = resolve_symbol_variant(symbol_name, symbol_len);
    if (addr)
        return addr;

    addr = (void *)find_kernel_symbol_exact(symbol_name);
    if (addr)
        return addr;

    if (selinux_setprocattr_fallback) {
        addr = resolve_symbol_variant("security_setprocattr", strlen("security_setprocattr"));
        if (addr) {
            pr_info("%s: resolved fallback %s via variant lookup\n", __func__, "security_setprocattr");
            return addr;
        }

        addr = (void *)find_kernel_symbol_exact("security_setprocattr");
        if (addr) {
            pr_info("%s: resolved fallback %s via exact lookup\n", __func__, "security_setprocattr");
            return addr;
        }
    }

    return NULL;
#else
    addr = (void *)find_kernel_symbol_exact(symbol_name);
    if (addr)
        return addr;

    if (selinux_setprocattr_fallback) {
        addr = (void *)find_kernel_symbol_exact("security_setprocattr");
        if (addr) {
            pr_info("%s: resolved fallback %s via exact lookup\n", __func__, "security_setprocattr");
            return addr;
        }
    }

    addr = resolve_symbol_variant(symbol_name, symbol_len);
    if (addr)
        return addr;

    if (selinux_setprocattr_fallback) {
        addr = resolve_symbol_variant("security_setprocattr", strlen("security_setprocattr"));
        if (addr) {
            pr_info("%s: resolved fallback %s via variant lookup\n", __func__, "security_setprocattr");
            return addr;
        }
    }

    return NULL;
#endif
}

/* ABK: fallback selinux_setprocattr to security_setprocattr for SukiSU selinux_hide. */'''
    text = replace_or_confirm(
        text,
        old,
        new,
        "ABK: fallback selinux_setprocattr to security_setprocattr for SukiSU selinux_hide.",
        "symbol_resolver selinux_setprocattr fallback",
    )

    write_if_changed(path, text, original, changed_files)


def patch_lsm_hook(path, changed_files):
    original = path.read_text()
    text = original

    marker = "ABK: prefer selinux slot for setprocattr hook patching."
    if marker not in text:
        text = text.replace(
            '    pr_info("target: 0x%lx %pSb\\n", (unsigned long)target, target);\n',
            '    pr_info("target: 0x%lx %pSb\\n", (unsigned long)target, target);\n'
            '\n'
            '    bool prefer_selinux_slot =\n'
            '        !strcmp(hook->head_name ?: "", "setprocattr") && !strcmp(target_name, "selinux_setprocattr");\n'
            '    /* ABK: prefer selinux slot for setprocattr hook patching. */\n',
            1,
        )

        text = text.replace(
            "        if (current_origin != target) {\n"
            "            continue;\n"
            "        }\n",
            "        if (prefer_selinux_slot) {\n"
            "            if (!entry->lsm || strcmp(entry->lsm, \"selinux\"))\n"
            "                continue;\n"
            "        } else if (current_origin != target) {\n"
            "            continue;\n"
            "        }\n",
            1,
        )

        text = text.replace(
            "            if (current_origin == target) {\n"
            "                pr_info(\"found %s (target %s) at head offset %ld (provided %ld)\\n\", hook->head_name, hook->target_name,\n"
            "                        (unsigned long)head - heads_addr, hook->head_offset);\n"
            "                selected_entry = entry;\n"
            "                selected_slot = slot;\n"
            "                selected_origin = current_origin;\n"
            "                break;\n"
            "            }\n",
            "            if (prefer_selinux_slot) {\n"
            "                if (!entry->lsm || strcmp(entry->lsm, \"selinux\"))\n"
            "                    continue;\n"
            "                pr_info(\"found %s selinux slot at head offset %ld (provided %ld)\\n\", hook->head_name,\n"
            "                        (unsigned long)head - heads_addr, hook->head_offset);\n"
            "                selected_entry = entry;\n"
            "                selected_slot = slot;\n"
            "                selected_origin = current_origin;\n"
            "                break;\n"
            "            }\n"
            "            if (current_origin == target) {\n"
            "                pr_info(\"found %s (target %s) at head offset %ld (provided %ld)\\n\", hook->head_name, hook->target_name,\n"
            "                        (unsigned long)head - heads_addr, hook->head_offset);\n"
            "                selected_entry = entry;\n"
            "                selected_slot = slot;\n"
            "                selected_origin = current_origin;\n"
            "                break;\n"
            "            }\n",
            1,
        )

    write_if_changed(path, text, original, changed_files)


def patch_runtime(path, changed_files):
    original = path.read_text()
    text = original

    text = ensure_include(text, "#include <linux/static_key.h>", "#include <linux/printk.h>\n")
    if "DEFINE_STATIC_KEY_TRUE(ksu_is_init_rc_hook_enabled);" not in text:
        anchor = '#include "hook/syscall_event_bridge.h"\n'
        if anchor not in text:
            die(f"missing runtime static key anchor: {path}")
        block = (
            "\n#ifdef CONFIG_KSU_SUSFS\n"
            "DEFINE_STATIC_KEY_TRUE(ksu_is_init_rc_hook_enabled);\n"
            "DEFINE_STATIC_KEY_TRUE(ksu_is_input_hook_enabled);\n"
            "#endif\n"
        )
        text = text.replace(anchor, anchor + block, 1)

    old_stop = r'''static void stop_init_rc_hook()
{
    ksu_syscall_table_unhook(__NR_read);
    ksu_syscall_table_unhook(__NR_fstat);
    pr_info("unregister init_rc syscall hook\n");
}'''
    new_stop = r'''static void stop_init_rc_hook()
{
#ifdef CONFIG_KSU_SUSFS
    if (static_key_enabled(&ksu_is_init_rc_hook_enabled)) {
        static_branch_disable(&ksu_is_init_rc_hook_enabled);
        pr_info("ksu init rc inline hook disabled\n");
    }
#else
    ksu_syscall_table_unhook(__NR_read);
    ksu_syscall_table_unhook(__NR_fstat);
    pr_info("unregister init_rc syscall hook\n");
#endif
}'''
    text = replace_or_confirm(
        text,
        old_stop,
        new_stop,
        "ksu init rc inline hook disabled",
        "stop_init_rc_hook",
    )

    text = replace_or_confirm(
        text,
        "static void ksu_handle_sys_read(unsigned int fd, char __user **buf_ptr, size_t *count_ptr)",
        "void ksu_handle_sys_read(unsigned int fd)",
        "void ksu_handle_sys_read(unsigned int fd)",
        "ksu_handle_sys_read signature",
    )
    text = text.replace(
        "    char __user **buf_ptr = (char __user **)&PT_REGS_PARM2(regs);\n"
        "    size_t *count_ptr = (size_t *)&PT_REGS_PARM3(regs);\n\n"
        "    ksu_handle_sys_read(fd, buf_ptr, count_ptr);",
        "    ksu_handle_sys_read(fd);",
    )

    if "void ksu_handle_vfs_fstat(int fd, loff_t *kstat_size_ptr)" not in text:
        marker = "\nstatic long (*orig_sys_read)(const struct pt_regs *regs);"
        if marker not in text:
            die(f"missing vfs_fstat insertion anchor: {path}")
        block = r'''
void ksu_handle_vfs_fstat(int fd, loff_t *kstat_size_ptr)
{
    loff_t new_size;
    struct file *file;

    if (!kstat_size_ptr)
        return;

    file = fget(fd);
    if (!file)
        return;

    if (is_init_rc(file)) {
        new_size = *kstat_size_ptr + ksu_rc_len;
        pr_info("stat init.rc");
        pr_info("adding ksu_rc_len: %lld -> %lld", *kstat_size_ptr, new_size);
        *kstat_size_ptr = new_size;
    }
    fput(file);
}
'''
        text = text.replace(marker, block + marker, 1)

    old_stop_input = r'''void ksu_stop_input_hook_runtime(void)
{
    static bool input_hook_stopped = false;
    if (input_hook_stopped) {
        return;
    }
    input_hook_stopped = true;
    bool ret = schedule_work(&stop_input_hook_work);
    pr_info("unregister input kprobe: %d!\n", ret);
}'''
    new_stop_input = r'''void ksu_stop_input_hook_runtime(void)
{
    static bool input_hook_stopped = false;
    if (input_hook_stopped) {
        return;
    }
    input_hook_stopped = true;
#ifdef CONFIG_KSU_SUSFS
    if (static_key_enabled(&ksu_is_input_hook_enabled)) {
        static_branch_disable(&ksu_is_input_hook_enabled);
        pr_info("ksu input inline hook disabled\n");
    }
#endif
    bool ret = schedule_work(&stop_input_hook_work);
    pr_info("unregister input kprobe: %d!\n", ret);
}'''
    text = replace_or_confirm(
        text,
        old_stop_input,
        new_stop_input,
        "ksu input inline hook disabled",
        "ksu_stop_input_hook_runtime",
    )

    old_init = r'''void __init ksu_ksud_init()
{
    int ret;

    ksu_syscall_table_hook(__NR_read, ksu_sys_read, &orig_sys_read);
    ksu_syscall_table_hook(__NR_fstat, ksu_sys_fstat, &orig_sys_fstat);

    ret = register_kprobe(&input_event_kp);
    pr_info("ksud: input_event_kp: %d\n", ret);

    INIT_WORK(&stop_input_hook_work, do_stop_input_hook);
}'''
    new_init = r'''void __init ksu_ksud_init()
{
    int ret;

#ifndef CONFIG_KSU_SUSFS
    ksu_syscall_table_hook(__NR_read, ksu_sys_read, &orig_sys_read);
    ksu_syscall_table_hook(__NR_fstat, ksu_sys_fstat, &orig_sys_fstat);
#else
    pr_info("ksud: using SUSFS inline init.rc hooks\n");
#endif

    ret = register_kprobe(&input_event_kp);
    pr_info("ksud: input_event_kp: %d\n", ret);

    INIT_WORK(&stop_input_hook_work, do_stop_input_hook);
}'''
    text = replace_or_confirm(
        text,
        old_init,
        new_init,
        "using SUSFS inline init.rc hooks",
        "ksu_ksud_init",
    )

    write_if_changed(path, text, original, changed_files)


def patch_selinux_hide(path, changed_files):
    original = path.read_text()
    text = original

    text = text.replace("static struct selinux_state fake_state;", "struct selinux_state fake_state;")
    text = text.replace(
        "static bool ksu_selinux_hide_running __read_mostly = false;",
        "bool ksu_selinux_hide_running __read_mostly = false;",
    )
    text = text.replace(
        "static bool ksu_selinux_hide_enabled __read_mostly = false;",
        "bool ksu_selinux_hide_enabled __read_mostly = false;",
    )
    text = text.replace(
        "static DEFINE_STATIC_KEY_FALSE(fake_status_initialize_key);",
        "DEFINE_STATIC_KEY_FALSE(fake_status_initialize_key);",
    )
    text = text.replace(
        "static struct page *fake_status = NULL;",
        "struct page *fake_status = NULL;",
    )
    text = re.sub(
        r"(?m)^static void initialize_fake_status\s*\(\s*(?:void)?\s*\)",
        "void initialize_fake_status(void)",
        text,
    )
    if "DEFINE_STATIC_KEY_FALSE(fake_status_initialize_key)" not in text:
        if "jump_label.h" not in text:
            text = ensure_include(text, "#include <linux/jump_label.h>", "#include <linux/mutex.h>\n")
        anchor = "bool ksu_selinux_hide_running __read_mostly = false;\n"
        if anchor not in text:
            die(f"missing selinux_hide fake status anchor: {path}")
        block = r'''

#ifdef CONFIG_KSU_SUSFS
DEFINE_STATIC_KEY_FALSE(fake_status_initialize_key);
struct page *fake_status = NULL;

void initialize_fake_status(void)
{
}
#endif
'''
        text = text.replace(anchor, anchor + block, 1)
    text = text.replace(
        "static int security_context_to_sid_with_policy(",
        "int security_context_to_sid_with_policy(",
    )
    text = text.replace(
        "static int security_sid_to_context_with_policy(",
        "int security_sid_to_context_with_policy(",
    )
    text = text.replace(
        "static void __nocfi security_compute_av_user_with_policy(",
        "void __nocfi security_compute_av_user_with_policy(",
    )
    text = text.replace(
        "static void security_compute_av_user_with_policy(",
        "void security_compute_av_user_with_policy(",
    )

    write_if_changed(path, text, original, changed_files)


def patch_selinux_c(path, changed_files):
    original = path.read_text()
    text = original

    if "u32 susfs_ksu_sid __read_mostly" not in text:
        anchor = "u32 ksu_file_sid __read_mostly = 0;\n"
        if anchor not in text:
            die(f"missing selinux sid anchor: {path}")
        block = r'''

#ifdef CONFIG_KSU_SUSFS
#define KERNEL_PRIV_APP_CONTEXT "u:r:priv_app:s0:c512,c768"

u32 susfs_ksu_sid __read_mostly = 0;
u32 susfs_init_sid __read_mostly = 0;
u32 susfs_zygote_sid __read_mostly = 0;
u32 susfs_priv_app_sid __read_mostly = 0;

static void susfs_set_sid(const char *secctx_name, u32 *out_sid)
{
    int err;

    if (!secctx_name || !out_sid)
        return;

    err = security_secctx_to_secid(secctx_name, strlen(secctx_name), out_sid);
    if (err) {
        pr_warn("Failed to cache SUSFS SID for %s: %d\n", secctx_name, err);
        *out_sid = 0;
    }
}

void susfs_set_batch_sid(void)
{
    susfs_set_sid(KERNEL_SU_CONTEXT, &susfs_ksu_sid);
    susfs_set_sid(INIT_CONTEXT, &susfs_init_sid);
    susfs_set_sid(ZYGOTE_CONTEXT, &susfs_zygote_sid);
    susfs_set_sid(KERNEL_PRIV_APP_CONTEXT, &susfs_priv_app_sid);
}

bool susfs_is_sid_equal(const struct cred *cred, u32 sid2)
{
#if LINUX_VERSION_CODE < KERNEL_VERSION(6, 18, 0)
    const struct task_security_struct *tsec = selinux_cred(cred);
#else
    const struct cred_security_struct *tsec = selinux_cred(cred);
#endif

    return tsec && tsec->sid == sid2;
}

u32 susfs_get_sid_from_name(const char *secctx_name)
{
    u32 out_sid = 0;
    susfs_set_sid(secctx_name, &out_sid);
    return out_sid;
}

u32 susfs_get_current_sid(void)
{
    return current_sid();
}

bool susfs_is_current_zygote_domain(void)
{
    return unlikely(current_sid() == susfs_zygote_sid);
}

bool susfs_is_current_ksu_domain(void)
{
    return unlikely(current_sid() == susfs_ksu_sid);
}

bool susfs_is_current_init_domain(void)
{
    return unlikely(current_sid() == susfs_init_sid);
}
#endif
'''
        text = text.replace(anchor, anchor + block, 1)

    cache_marker = r'''    } else {
        pr_info("Cached ksu_file SID: %u\n", ksu_file_sid);
    }
}'''
    if "susfs_set_batch_sid();" not in text:
        replacement = r'''    } else {
        pr_info("Cached ksu_file SID: %u\n", ksu_file_sid);
    }
#ifdef CONFIG_KSU_SUSFS
    susfs_set_batch_sid();
#endif
}'''
        text = replace_or_confirm(
            text,
            cache_marker,
            replacement,
            "susfs_set_batch_sid();",
            "cache_sid SUSFS call",
        )

    write_if_changed(path, text, original, changed_files)


def patch_selinux_h(path, changed_files):
    original = path.read_text()
    text = original

    if "susfs_is_current_ksu_domain" not in text:
        anchor = "extern u32 ksu_file_sid;\n"
        if anchor not in text:
            die(f"missing selinux header anchor: {path}")
        block = r'''

#ifdef CONFIG_KSU_SUSFS
extern u32 susfs_ksu_sid;
extern u32 susfs_init_sid;
extern u32 susfs_zygote_sid;
extern u32 susfs_priv_app_sid;
bool susfs_is_sid_equal(const struct cred *cred, u32 sid2);
u32 susfs_get_sid_from_name(const char *secctx_name);
u32 susfs_get_current_sid(void);
void susfs_set_batch_sid(void);
bool susfs_is_current_zygote_domain(void);
bool susfs_is_current_ksu_domain(void);
bool susfs_is_current_init_domain(void);
#endif
'''
        text = text.replace(anchor, anchor + block, 1)

    write_if_changed(path, text, original, changed_files)


def patch_supercall(path, changed_files):
    original = path.read_text()
    text = original

    text = ensure_include(text, "#include <linux/cred.h>", "#include <linux/anon_inodes.h>\n")
    if "#include <linux/susfs.h>" not in text:
        anchor = "#include <linux/version.h>\n"
        if anchor not in text:
            die(f"missing supercall include anchor: {path}")
        text = text.replace(
            anchor,
            anchor + "#ifdef CONFIG_KSU_SUSFS\n#include <linux/susfs.h>\n#endif\n",
            1,
        )

    if "int ksu_handle_sys_reboot" not in text:
        marker = "\nstatic int reboot_handler_pre(struct kprobe *p, struct pt_regs *regs)"
        if marker not in text:
            die(f"missing reboot handler anchor: {path}")
        block = r'''
#ifdef CONFIG_KSU_SUSFS
int ksu_handle_sys_reboot(int magic1, int magic2, unsigned int cmd, void __user **arg)
{
    if (magic1 != KSU_INSTALL_MAGIC1)
        return -EINVAL;

    if (magic2 == SUSFS_MAGIC && current_uid().val == 0) {
        switch (cmd) {
#ifdef CONFIG_KSU_SUSFS_SUS_PATH
        case CMD_SUSFS_ADD_SUS_PATH:
            susfs_add_sus_path(arg);
            return 0;
        case CMD_SUSFS_ADD_SUS_PATH_LOOP:
            susfs_add_sus_path_loop(arg);
            return 0;
#endif
#ifdef CONFIG_KSU_SUSFS_SUS_MOUNT
        case CMD_SUSFS_HIDE_SUS_MNTS_FOR_NON_SU_PROCS:
            susfs_set_hide_sus_mnts_for_non_su_procs(arg);
            return 0;
#endif
#ifdef CONFIG_KSU_SUSFS_SUS_KSTAT
        case CMD_SUSFS_ADD_SUS_KSTAT:
        case CMD_SUSFS_ADD_SUS_KSTAT_STATICALLY:
            susfs_add_sus_kstat(arg);
            return 0;
        case CMD_SUSFS_UPDATE_SUS_KSTAT:
            susfs_update_sus_kstat(arg);
            return 0;
#endif
#ifdef CONFIG_KSU_SUSFS_SPOOF_UNAME
        case CMD_SUSFS_SET_UNAME:
            susfs_set_uname(arg);
            return 0;
#endif
#ifdef CONFIG_KSU_SUSFS_ENABLE_LOG
        case CMD_SUSFS_ENABLE_LOG:
            susfs_enable_log(arg);
            return 0;
#endif
#ifdef CONFIG_KSU_SUSFS_SPOOF_CMDLINE_OR_BOOTCONFIG
        case CMD_SUSFS_SET_CMDLINE_OR_BOOTCONFIG:
            susfs_set_cmdline_or_bootconfig(arg);
            return 0;
#endif
#ifdef CONFIG_KSU_SUSFS_OPEN_REDIRECT
        case CMD_SUSFS_ADD_OPEN_REDIRECT:
            susfs_add_open_redirect(arg);
            return 0;
#endif
#ifdef CONFIG_KSU_SUSFS_SUS_MAP
        case CMD_SUSFS_ADD_SUS_MAP:
            susfs_add_sus_map(arg);
            return 0;
#endif
        case CMD_SUSFS_ENABLE_AVC_LOG_SPOOFING:
            susfs_set_avc_log_spoofing(arg);
            return 0;
        case CMD_SUSFS_SHOW_ENABLED_FEATURES:
            susfs_get_enabled_features(arg);
            return 0;
        case CMD_SUSFS_SHOW_VARIANT:
            susfs_show_variant(arg);
            return 0;
        case CMD_SUSFS_SHOW_VERSION:
            susfs_show_version(arg);
            return 0;
        default:
            return -EINVAL;
        }
    }

    return -EINVAL;
}
#endif
'''
        text = text.replace(marker, "\n" + block + marker, 1)

    write_if_changed(path, text, original, changed_files)


def verify(ksu_dir):
    required = {
        ksu_dir / "runtime/ksud_integration.c": (
            "DEFINE_STATIC_KEY_TRUE(ksu_is_init_rc_hook_enabled)",
            "DEFINE_STATIC_KEY_TRUE(ksu_is_input_hook_enabled)",
            "void ksu_handle_sys_read(unsigned int fd)",
            "void ksu_handle_vfs_fstat(int fd, loff_t *kstat_size_ptr)",
        ),
        ksu_dir / "infra/symbol_resolver.c": (
            "ABK: fallback selinux_setprocattr to security_setprocattr for SukiSU selinux_hide.",
            'selinux_setprocattr_fallback = !strcmp(symbol_name, "selinux_setprocattr")',
            '"security_setprocattr",',
        ),
        ksu_dir / "hook/lsm_hook.c": (
            "ABK: prefer selinux slot for setprocattr hook patching.",
            'prefer_selinux_slot =',
            'entry->lsm, "selinux"',
        ),
        ksu_dir / "feature/sucompat.c": (
            "DEFINE_STATIC_KEY_TRUE(ksu_su_compat_enabled)",
            "int ksu_handle_execveat_sucompat",
            "int ksu_handle_execveat",
            "int ksu_handle_stat(int *dfd, struct filename **filename",
        ),
        ksu_dir / "selinux/selinux.c": (
            "u32 susfs_ksu_sid __read_mostly",
            "u32 susfs_priv_app_sid __read_mostly",
            "bool susfs_is_current_ksu_domain",
        ),
        ksu_dir / "feature/selinux_hide.c": (
            "struct selinux_state fake_state;",
            "DEFINE_STATIC_KEY_FALSE(fake_status_initialize_key)",
            "struct page *fake_status",
            "bool ksu_selinux_hide_enabled __read_mostly",
            "bool ksu_selinux_hide_running __read_mostly",
            "void initialize_fake_status(",
            "int security_context_to_sid_with_policy(",
            "int security_sid_to_context_with_policy(",
        ),
        ksu_dir / "supercall/supercall.c": ("int ksu_handle_sys_reboot",),
    }
    for path, markers in required.items():
        data = path.read_text()
        missing = [marker for marker in markers if marker not in data]
        if missing:
            die(f"{path} missing markers: {missing}")

    selinux_hide = (ksu_dir / "feature/selinux_hide.c").read_text()
    forbidden = (
        "static int security_context_to_sid_with_policy(",
        "static int security_sid_to_context_with_policy(",
        "static void __nocfi security_compute_av_user_with_policy(",
        "static void security_compute_av_user_with_policy(",
    )
    present = [marker for marker in forbidden if marker in selinux_hide]
    if present:
        die(f"{ksu_dir / 'feature/selinux_hide.c'} still has non-exported SELinux compat symbols: {present}")
    if (
        "void __nocfi security_compute_av_user_with_policy(" not in selinux_hide
        and "void security_compute_av_user_with_policy(" not in selinux_hide
    ):
        die(f"{ksu_dir / 'feature/selinux_hide.c'} missing exported security_compute_av_user_with_policy()")


def main():
    if len(sys.argv) != 2:
        die("usage: fix_sukisu_susfs.py <kernel-root>")

    root = Path(sys.argv[1]).resolve()
    ksu_dir = find_ksu_dir(root)
    changed_files = []

    patch_sucompat_header(ksu_dir / "feature/sucompat.h", changed_files)
    patch_sucompat_c(ksu_dir / "feature/sucompat.c", changed_files)
    patch_symbol_resolver(ksu_dir / "infra/symbol_resolver.c", changed_files)
    patch_lsm_hook(ksu_dir / "hook/lsm_hook.c", changed_files)
    patch_syscall_bridge(ksu_dir / "hook/syscall_event_bridge.c", changed_files)
    patch_runtime(ksu_dir / "runtime/ksud_integration.c", changed_files)
    patch_selinux_hide(ksu_dir / "feature/selinux_hide.c", changed_files)
    patch_selinux_c(ksu_dir / "selinux/selinux.c", changed_files)
    patch_selinux_h(ksu_dir / "selinux/selinux.h", changed_files)
    patch_supercall(ksu_dir / "supercall/supercall.c", changed_files)
    verify(ksu_dir)

    if changed_files:
        print("Patched SukiSU SUSFS compatibility files:")
        for file in changed_files:
            print(f"  {file}")
    else:
        print("SukiSU SUSFS compatibility files already patched.")


if __name__ == "__main__":
    main()
