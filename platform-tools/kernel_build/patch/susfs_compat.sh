#!/usr/bin/env bash
# 外围拓展补丁：把 build.yml 中的大段逻辑收敛为可维护的脚本。
#
# 由 .github/workflows/build.yml 以 `bash platform-tools/kernel_build/patch/susfs_compat.sh <子命令>` 调用；
# 工作流输入以 ABK_INPUT_* 形式由 clean/env.sh summary 导出，脚本内不出现模板语法。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"


# ---- 修复 BakaSU SUSFS 源码兼容 ----
bakasu() {
    cd "$KERNEL_ROOT"
    set -euo pipefail
    echo "BakaSU SUSFS compat cwd: $(pwd)"

    python3 - <<'PY'
from pathlib import Path
import re

def preprocessor_delta(text):
    opens = 0
    closes = 0
    for line in text.splitlines():
        stripped = line.strip()
        if stripped.startswith("#if"):
            opens += 1
        elif stripped.startswith("#endif"):
            closes += 1
    return opens - closes

def ensure_include(text, include_line, anchors, path):
    if include_line in text:
        return text
    for anchor in anchors:
        if anchor in text:
            return text.replace(anchor, anchor + include_line, 1)
    raise SystemExit(f"{path} missing include anchor for {include_line.strip()}")

def patch_bakasu_selinux_hide(path):
    original = path.read_text()
    text = original

    text = re.sub(
        r"(?m)^\s*(?:static\s+|__maybe_static\s+)?struct\s+selinux_state\s+fake_state\s*;",
        "struct selinux_state fake_state;",
        text,
    )
    text = re.sub(
        r"(?m)^\s*(?:static\s+|__maybe_static\s+)?bool\s+ksu_selinux_hide_running\s+__read_mostly\s*=\s*false\s*;",
        "bool ksu_selinux_hide_running __read_mostly = false;",
        text,
    )
    text = re.sub(
        r"(?m)^\s*(?:static\s+|__maybe_static\s+)?bool\s+ksu_selinux_hide_enabled\s+__read_mostly\s*=\s*false\s*;",
        "bool ksu_selinux_hide_enabled __read_mostly = false;",
        text,
    )
    text = re.sub(
        r"(?m)^\s*(?:static\s+|__maybe_static\s+)?int\s+security_context_to_sid_with_policy\s*\(",
        "int security_context_to_sid_with_policy(",
        text,
    )
    text = re.sub(
        r"(?m)^\s*(?:static\s+|__maybe_static\s+)?int\s+security_sid_to_context_with_policy\s*\(",
        "int security_sid_to_context_with_policy(",
        text,
    )
    text = re.sub(
        r"(?m)^\s*(?:static\s+|__maybe_static\s+)?void\s+__nocfi\s+security_compute_av_user_with_policy\s*\(",
        "void __nocfi security_compute_av_user_with_policy(",
        text,
    )
    text = re.sub(
        r"(?m)^\s*(?:static\s+|__maybe_static\s+)?void\s+security_compute_av_user_with_policy\s*\(",
        "void security_compute_av_user_with_policy(",
        text,
    )

    text = re.sub(
        r"(?m)^\s*(?:static\s+|__maybe_static\s+)?DEFINE_STATIC_KEY_FALSE\s*\(\s*fake_status_initialize_key\s*\)\s*;",
        "DEFINE_STATIC_KEY_FALSE(fake_status_initialize_key);",
        text,
    )
    text = re.sub(
        r"(?m)^\s*(?:static\s+|__maybe_static\s+)?struct\s+page\s*\*\s*fake_status\s*(?:=\s*NULL)?\s*;",
        "struct page *fake_status = NULL;",
        text,
    )
    text = re.sub(
        r"(?m)^\s*(?:static\s+|__maybe_static\s+)?void\s+initialize_fake_status\s*\(\s*(?:void)?\s*\)",
        "void initialize_fake_status(void)",
        text,
    )

    fake_status_additions = []
    if not re.search(r"(?m)^\s*DEFINE_STATIC_KEY_FALSE\s*\(\s*fake_status_initialize_key\s*\)\s*;", text):
        fake_status_additions.append("DEFINE_STATIC_KEY_FALSE(fake_status_initialize_key);")
    if not re.search(r"(?m)^\s*struct\s+page\s*\*\s*fake_status\s*(?:=\s*NULL)?\s*;", text):
        fake_status_additions.append("struct page *fake_status = NULL;")
    if not re.search(r"(?m)^\s*void\s+initialize_fake_status\s*\(\s*(?:void)?\s*\)\s*\{", text):
        fake_status_additions.extend([
            "void initialize_fake_status(void)",
            "{",
            "}",
        ])
    if fake_status_additions:
        text = ensure_include(
            text,
            "#include <linux/jump_label.h>\n",
            ("#include <linux/mutex.h>\n", "#include <linux/version.h>\n"),
            path,
        )
        anchor = "bool ksu_selinux_hide_running __read_mostly = false;\n"
        if anchor not in text:
            raise SystemExit(f"{path} missing fake status insertion anchor")
        block = "\n".join([
            "",
            "#ifdef CONFIG_KSU_SUSFS",
            *fake_status_additions,
            "#endif",
            "",
        ])
        text = text.replace(anchor, anchor + block, 1)

    if text != original:
        path.write_text(text)
        print(f"Patched BakaSU selinux_hide compatibility exports: {path}")

    final_text = path.read_text()
    required = (
        "struct selinux_state fake_state;",
        "DEFINE_STATIC_KEY_FALSE(fake_status_initialize_key);",
        "struct page *fake_status = NULL;",
        "bool ksu_selinux_hide_enabled __read_mostly",
        "bool ksu_selinux_hide_running __read_mostly",
        "void initialize_fake_status(",
        "int security_context_to_sid_with_policy(",
        "int security_sid_to_context_with_policy(",
    )
    missing = [marker for marker in required if marker not in final_text]
    if missing:
        raise SystemExit(f"{path} missing BakaSU SELinux compatibility markers: {missing}")

    forbidden_patterns = {
        "fake_state": r"(?m)^\s*(?:static|__maybe_static)\s+struct\s+selinux_state\s+fake_state\s*;",
        "ksu_selinux_hide_running": r"(?m)^\s*(?:static|__maybe_static)\s+bool\s+ksu_selinux_hide_running\s+__read_mostly\s*=\s*false\s*;",
        "ksu_selinux_hide_enabled": r"(?m)^\s*(?:static|__maybe_static)\s+bool\s+ksu_selinux_hide_enabled\s+__read_mostly\s*=\s*false\s*;",
        "security_context_to_sid_with_policy": r"(?m)^\s*(?:static|__maybe_static)\s+int\s+security_context_to_sid_with_policy\s*\(",
        "security_sid_to_context_with_policy": r"(?m)^\s*(?:static|__maybe_static)\s+int\s+security_sid_to_context_with_policy\s*\(",
        "security_compute_av_user_with_policy __nocfi": r"(?m)^\s*(?:static|__maybe_static)\s+void\s+__nocfi\s+security_compute_av_user_with_policy\s*\(",
        "security_compute_av_user_with_policy": r"(?m)^\s*(?:static|__maybe_static)\s+void\s+security_compute_av_user_with_policy\s*\(",
    }
    present = [
        name for name, pattern in forbidden_patterns.items()
        if re.search(pattern, final_text)
    ]
    if present:
        raise SystemExit(f"{path} still has non-exported BakaSU SELinux symbols: {present}")

    if (
        "void __nocfi security_compute_av_user_with_policy(" not in final_text
        and "void security_compute_av_user_with_policy(" not in final_text
    ):
        raise SystemExit(f"{path} missing exported security_compute_av_user_with_policy()")

def patch_bakasu_rules(path):
    original = path.read_text()
    text = re.sub(
        r"(?m)^\s*(?:static\s+|__maybe_static\s+)?struct\s+selinux_policy\s*\*\s*backup_sepolicy\s*;",
        "struct selinux_policy *backup_sepolicy;",
        original,
    )

    if text != original:
        path.write_text(text)
        print(f"Patched BakaSU backup_sepolicy owner: {path}")

    final_text = path.read_text()
    if "struct selinux_policy *backup_sepolicy;" not in final_text:
        raise SystemExit(f"{path} missing backup_sepolicy definition")
    forbidden = (
        "static struct selinux_policy *backup_sepolicy;",
        "__maybe_static struct selinux_policy *backup_sepolicy;",
    )
    present = [marker for marker in forbidden if marker in final_text]
    if present:
        raise SystemExit(f"{path} still has non-exported backup_sepolicy definition: {present}")

def patch_bakasu_selinux(path):
    original = path.read_text()
    text = original
    marker = "/* ABK: simonpunk SUSFS SELinux hooks reference KernelSU selinux_hide globals. */"
    if marker in text:
        start = text.index(marker)
        start = text.rfind("\n", 0, start) + 1 if "\n" in text[:start] else 0
        end_marker = "#endif\n#endif"
        end = text.find(end_marker, start)
        if end == -1:
            raise SystemExit(f"{path} contains stale ABK SELinux compat marker without closing guards")
        end += len(end_marker)
        while end < len(text) and text[end] == "\n":
            end += 1
        text = text[:start] + text[end:]

    if text != original:
        path.write_text(text)
        print(f"Removed duplicate-prone BakaSU SELinux globals from {path}")

    final_text = path.read_text()
    forbidden = (
        marker,
        "struct selinux_state fake_state;",
        "bool ksu_selinux_hide_running __read_mostly = false;",
        "bool ksu_selinux_hide_enabled __read_mostly = false;",
        "struct selinux_policy *backup_sepolicy;",
        "int security_context_to_sid_with_policy(struct selinux_policy *policy, const char *scontext, u32 scontext_len,",
        "int security_sid_to_context_with_policy(struct selinux_policy *policy, u32 sid, char **scontext,",
        "void security_compute_av_user_with_policy(struct selinux_policy *policy, u32 ssid, u32 tsid, u16 tclass,",
    )
    present = [marker_text for marker_text in forbidden if marker_text in final_text]
    if present:
        raise SystemExit(f"{path} still contains duplicate-prone BakaSU SELinux definitions: {present}")

variant = "${ABK_INPUT_KSU_VARIANT}"
candidates = []
seen = set()
roots = [Path("KernelSU/kernel")]
if variant == "BakaSU":
    roots.extend([Path("common/drivers/kernelsu"), Path("drivers/kernelsu")])

for base in roots:
    resolved = base.resolve()
    if resolved in seen:
        continue
    if variant == "BakaSU":
        markers = ["Kbuild", "core/init.c", "feature/sucompat.c", "sulog/event.c"]
        if any((base / marker).exists() for marker in markers):
            candidates.append(base)
            seen.add(resolved)
    elif (base / "ksu.c").exists():
        candidates.append(base)
        seen.add(resolved)

if not candidates:
    if variant == "BakaSU":
        raise SystemExit("未找到实际 BakaSU 源码目录，无法应用 SUSFS 兼容修复")
    raise SystemExit("未找到 KernelSU/kernel/ksu.c，无法应用 SukiSU/BakaSU SUSFS 兼容修复")

for kernel_dir in candidates:
    print(f"检查 SukiSU/BakaSU SUSFS 源码目录: {kernel_dir}")
    ksu_c = kernel_dir / "ksu.c"
    tree_text = "\n".join(
        path.read_text(errors="ignore")
        for path in kernel_dir.rglob("*")
        if path.is_file()
    )

    true_symbols = [
        "ksu_is_init_rc_hook_enabled",
        "ksu_is_input_hook_enabled",
    ]
    false_symbols = [
        "ksu_init_rc_hook_key_false",
        "ksu_input_hook_key_false",
    ]

    missing_true = [
        sym for sym in true_symbols
        if f"DEFINE_STATIC_KEY_TRUE({sym}" not in tree_text
    ]
    missing_false = [
        sym for sym in false_symbols
        if f"DEFINE_STATIC_KEY_FALSE({sym}" not in tree_text
    ]

    if not missing_true and not missing_false:
        print("SukiSU/BakaSU SUSFS static key definitions already exist.")
    else:
        if not ksu_c.exists():
            print(f"  {kernel_dir} 未提供 ksu.c，跳过 static key 注入")
            text = ""
        else:
            text = ksu_c.read_text()
        anchor = '#include "infra/kernel_compat.h"\n'
        if ksu_c.exists() and anchor not in text:
            if variant == "BakaSU":
                print(f"  {ksu_c} 未找到 kernel_compat.h include anchor，跳过 static key 注入")
                continue
            raise SystemExit("kernel_compat.h include anchor not found in KernelSU/kernel/ksu.c")
        elif ksu_c.exists():
            block = [
                "",
                "/* ABK: SukiSU/BakaSU builtin SUSFS expects these inline hook static keys. */",
                "#ifdef CONFIG_KSU_SUSFS",
            ]
            block.extend(f"DEFINE_STATIC_KEY_TRUE({sym});" for sym in missing_true)
            if missing_false:
                block.append("#if defined(KSU_COMPAT_USE_STATIC_KEY)")
                block.extend(f"DEFINE_STATIC_KEY_FALSE({sym});" for sym in missing_false)
                block.append("#endif")
            block.extend(["#endif", ""])

            ksu_c.write_text(text.replace(anchor, anchor + "\n".join(block), 1))
            print("Injected missing SukiSU/BakaSU SUSFS static key definitions:", ", ".join(missing_true + missing_false))

    if variant == "BakaSU":
        selinux_hide_c = kernel_dir / "feature/selinux_hide.c"
        rules_c = kernel_dir / "selinux/rules.c"
        selinux_c = kernel_dir / "selinux/selinux.c"
        if not selinux_hide_c.exists():
            raise SystemExit(f"{kernel_dir} missing feature/selinux_hide.c")
        if not rules_c.exists():
            raise SystemExit(f"{kernel_dir} missing selinux/rules.c")
        if not selinux_c.exists():
            raise SystemExit(f"{kernel_dir} missing selinux/selinux.c")
        patch_bakasu_selinux_hide(selinux_hide_c)
        patch_bakasu_rules(rules_c)
        patch_bakasu_selinux(selinux_c)

    sucompat_c = kernel_dir / "feature/sucompat.c"
    if sucompat_c.exists():
        text = sucompat_c.read_text()
        marker = "\n// dead code\n"
        guard = "#endif /* CONFIG_KSU_SUSFS */\n"
        if marker in text and guard not in text:
            before_marker = text.split(marker, 1)[0]
            if preprocessor_delta(before_marker) > 0:
                sucompat_c.write_text(text.replace(marker, "\n" + guard + marker, 1))
                print("Closed missing SukiSU/BakaSU builtin CONFIG_KSU_SUSFS block in feature/sucompat.c")

    sulog_event_c = kernel_dir / "sulog/event.c"
    if sulog_event_c.exists():
        text = sulog_event_c.read_text()
        marker = "builtin SUSFS ksu_sulog_capture() expects argv_user as a pointer"
        new = "\n".join([
            "    /* ABK: builtin SUSFS ksu_sulog_capture() expects argv_user as a pointer. */",
            "#ifdef CONFIG_KSU_SUSFS",
            "    pending = ksu_sulog_capture(KSU_SULOG_EVENT_IOCTL_GRANT_ROOT, NULL, NULL, gfp);",
            "#else",
            "    pending = ksu_sulog_capture(KSU_SULOG_EVENT_IOCTL_GRANT_ROOT, NULL, USER_ARG_NULL, gfp);",
            "#endif",
        ])
        if marker not in text:
            for old in (
                "    pending = ksu_sulog_capture(KSU_SULOG_EVENT_IOCTL_GRANT_ROOT, NULL, USER_ARG_NULL, gfp);",
                "    pending = ksu_sulog_capture(KSU_SULOG_EVENT_IOCTL_GRANT_ROOT, NULL, &USER_ARG_NULL, gfp);",
            ):
                if old in text:
                    sulog_event_c.write_text(text.replace(old, new, 1))
                    print("Fixed SukiSU/BakaSU builtin sulog grant_root argv type for CONFIG_KSU_SUSFS")
                    break
PY

}

# ---- 修复 Official SUSFS 源码兼容 ----
official() {
    cd "$KERNEL_ROOT"
    set -euo pipefail

    python3 - <<'PY'
from pathlib import Path
from textwrap import dedent
import re

INIT_BODY = """int __init kernelsu_init(void)
{
#ifdef CONFIG_KSU_DEBUG
    pr_alert("*************************************************************");
    pr_alert("**     NOTICE NOTICE NOTICE NOTICE NOTICE NOTICE NOTICE    **");
    pr_alert("**                                                         **");
    pr_alert("**         You are running KernelSU in DEBUG mode          **");
    pr_alert("**                                                         **");
    pr_alert("**     NOTICE NOTICE NOTICE NOTICE NOTICE NOTICE NOTICE    **");
    pr_alert("*************************************************************");
#endif

    if (allow_shell) {
        pr_alert("shell is allowed at init!");
    }

    ksu_cred = prepare_creds();
    if (!ksu_cred) {
        pr_err("prepare cred failed!\\n");
    }

#ifdef CONFIG_KSU_SUSFS
    susfs_init();
#endif // #ifdef CONFIG_KSU_SUSFS

    ksu_feature_init();

    ksu_supercalls_init();

    ksu_sucompat_init();

    ksu_setuid_hook_init();

    ksu_sulog_init();

    ksu_adb_root_init();
    ksu_selinux_hide_init();

    ksu_allowlist_init();

    ksu_throne_tracker_init();

    ksu_ksud_init();

    ksu_file_wrapper_init();

    return 0;
}
"""

EXIT_BODY = """void __exit kernelsu_exit(void)
{
    ksu_supercalls_exit();

    // Wait for any in-flight RCU readers (e.g. handler traversing allow_list)
    synchronize_rcu();

    // Now safe to release data structures
    ksu_observer_exit();

    ksu_throne_tracker_exit();

    ksu_allowlist_exit();

    ksu_selinux_hide_exit();
    ksu_adb_root_exit();

    ksu_sulog_exit();

    ksu_feature_exit();

    if (ksu_cred) {
        put_cred(ksu_cred);
    }
}
"""

def replace_function(text, signature, replacement):
    start = text.find(signature)
    if start == -1:
        raise SystemExit(f"function not found: {signature}")

    brace = text.find("{", start)
    if brace == -1:
        raise SystemExit(f"function body not found: {signature}")

    depth = 0
    end = None
    for index in range(brace, len(text)):
        char = text[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                end = index + 1
                break

    if end is None:
        raise SystemExit(f"function body is not balanced: {signature}")

    return text[:start] + replacement + text[end:]

def ensure_line_after(text, anchor, line):
    if line in text:
        return text
    if anchor not in text:
        raise SystemExit(f"include anchor not found: {anchor.strip()}")
    return text.replace(anchor, anchor + line, 1)

def ensure_include(text, include, anchor):
    if include in text:
        return text
    if anchor in text:
        return text.replace(anchor, anchor + include + "\n", 1)
    return include + "\n" + text

def replace_first_function(text, signatures, replacement):
    for signature in signatures:
        pattern = re.compile(re.escape(signature) + r"\s*\{")
        match = pattern.search(text)
        if not match:
            continue
        start = match.start()
        brace = match.end() - 1
        depth = 0
        for index in range(brace, len(text)):
            if text[index] == "{":
                depth += 1
            elif text[index] == "}":
                depth -= 1
                if depth == 0:
                    return text[:start] + replacement + text[index + 1:]
        raise SystemExit(f"function body is not balanced: {signature}")
    raise SystemExit(f"function not found: {' / '.join(signatures)}")

def ensure_official_runtime_compat(ksu_dir):
    path = ksu_dir / "runtime/ksud_integration.c"
    if not path.exists():
        return False

    text = path.read_text()
    original = text

    text = re.sub(
        r"(?m)^(?:static\s+)?void\s+ksu_handle_sys_read\s*\(\s*unsigned int fd\s*,\s*char __user \*\*buf_ptr\s*,\s*size_t \*count_ptr\s*\)",
        "void ksu_handle_sys_read(unsigned int fd)",
        text,
        count=1,
    )
    text = re.sub(
        r"(?m)^static void\s+ksu_handle_sys_read\s*\(\s*unsigned int fd\s*\)",
        "void ksu_handle_sys_read(unsigned int fd)",
        text,
        count=1,
    )
    text = text.replace("ksu_handle_sys_read(fd, buf_ptr, count_ptr);", "ksu_handle_sys_read(fd);")
    text = ensure_include(text, "#include <linux/static_key.h>", "#include <linux/printk.h>\n")
    if "register_kprobe(" in text or "unregister_kprobe(" in text or "struct kprobe" in text:
        text = ensure_include(text, "#include <linux/kprobes.h>", "#include <linux/input-event-codes.h>\n")

    if "DEFINE_STATIC_KEY_TRUE(ksu_is_init_rc_hook_enabled);" not in text:
        block = dedent("""\

        #ifdef CONFIG_KSU_SUSFS
        DEFINE_STATIC_KEY_TRUE(ksu_is_init_rc_hook_enabled);
        DEFINE_STATIC_KEY_TRUE(ksu_is_input_hook_enabled);
        #endif
        """)
        anchor = '#include "hook/syscall_event_bridge.h"\n'
        if anchor not in text:
            raise SystemExit(f"{path} 缺少 syscall_event_bridge include，无法注入 Official runtime static key")
        text = text.replace(anchor, anchor + block, 1)

    decls = []
    if not re.search(r"(?m)^static void stop_init_rc_hook\s*\(\s*(?:void)?\s*\)\s*;", text):
        decls.append("static void stop_init_rc_hook(void);")
    if not re.search(r"(?m)^void ksu_stop_input_hook_runtime\s*\(\s*(?:void)?\s*\)\s*;", text):
        decls.append("void ksu_stop_input_hook_runtime(void);")
    if "stop_input_hook_work" in text and not re.search(r"(?m)^static struct work_struct stop_input_hook_work\s*;", text):
        decls.append("static struct work_struct stop_input_hook_work;")
    if decls:
        block = "\n/* ABK: Official SUSFS runtime compatibility declarations. */\n" + "\n".join(decls) + "\n"
        for anchor in ("// clang-format on\n", '#include "hook/syscall_event_bridge.h"\n'):
            if anchor in text:
                text = text.replace(anchor, anchor + block, 1)
                break
        else:
            raise SystemExit(f"{path} 缺少 runtime 声明插入锚点")

    if (
        not re.search(r"(?m)^void\s+ksu_handle_vfs_fstat\s*\(", text)
        or "orig_sys_fstat(" in text
        or "statbuf" in text
    ):
        load_module = "        load_module_rc_once();\n" if "load_module_rc_once(" in text else ""
        extra_expr = "ksu_rc_len + module_rc_len" if "module_rc_len" in text else "ksu_rc_len"
        log_args = (
            ', orig_size, *kstat_size_ptr, ksu_rc_len, module_rc_len'
            if "module_rc_len" in text
            else ', orig_size, *kstat_size_ptr, ksu_rc_len'
        )
        log_fmt = (
            'adding rc len: %lld -> %lld (static=%zu module=%zu)'
            if "module_rc_len" in text
            else 'adding rc len: %lld -> %lld (static=%zu)'
        )
        block = (
            "void ksu_handle_vfs_fstat(int fd, loff_t *kstat_size_ptr)\n"
            "{\n"
            "    loff_t orig_size;\n"
            "    size_t extra;\n"
            "    struct file *file;\n\n"
            "    if (!kstat_size_ptr)\n"
            "        return;\n\n"
            "    file = fget(fd);\n"
            "    if (!file)\n"
            "        return;\n\n"
            "    if (is_init_rc(file)) {\n"
            "        orig_size = *kstat_size_ptr;\n"
            f"{load_module}"
            f"        extra = {extra_expr};\n"
            "        *kstat_size_ptr = orig_size + extra;\n"
            f"        pr_info(\"{log_fmt}\"{log_args});\n"
            "    }\n"
            "    fput(file);\n"
            "}\n\n"
        )
        replaced = False
        for signature in (
            "void ksu_handle_vfs_fstat(int fd, loff_t *kstat_size_ptr)",
            "static long ksu_sys_fstat(const struct pt_regs *regs)",
        ):
            if re.search(re.escape(signature) + r"\s*\{", text):
                text = replace_first_function(text, (signature,), block)
                replaced = True
                break
        if not replaced:
            for anchor in (
                "\nstatic long (*orig_sys_read)(const struct pt_regs *regs);",
                "\nstatic unsigned int volumedown_pressed_count",
            ):
                if anchor in text:
                    text = text.replace(anchor, "\n" + block + anchor.lstrip("\n"), 1)
                    replaced = True
                    break
        if not replaced:
            raise SystemExit(f"{path} 缺少 ksu_handle_vfs_fstat 插入锚点")

    if "ksu init rc inline hook disabled" not in text and "ksu_syscall_table_unhook(" in text:
        text = replace_first_function(
            text,
            ("static void stop_init_rc_hook()", "static void stop_init_rc_hook(void)"),
            dedent("""\
            static void stop_init_rc_hook(void)
            {
            #ifdef CONFIG_KSU_SUSFS
                if (static_key_enabled(&ksu_is_init_rc_hook_enabled)) {
                    static_branch_disable(&ksu_is_init_rc_hook_enabled);
                    pr_info("ksu init rc inline hook disabled\\n");
                }
            #else
                ksu_syscall_table_unhook(__NR_read);
                ksu_syscall_table_unhook(__NR_fstat);
                pr_info("unregister init_rc syscall hook\\n");
            #endif
            }
            """),
        )

    if (
        "ksu input inline hook disabled" not in text
        and re.search(r"(?m)^void\s+ksu_stop_input_hook_runtime\s*\([^;{]*\)\s*\{", text)
    ):
        text = replace_first_function(
            text,
            ("void ksu_stop_input_hook_runtime(void)", "void ksu_stop_input_hook_runtime()"),
            dedent("""\
            void ksu_stop_input_hook_runtime(void)
            {
                static bool input_hook_stopped = false;
                bool ret;

                if (input_hook_stopped) {
                    return;
                }
                input_hook_stopped = true;
            #ifdef CONFIG_KSU_SUSFS
                if (static_key_enabled(&ksu_is_input_hook_enabled)) {
                    static_branch_disable(&ksu_is_input_hook_enabled);
                    pr_info("ksu input inline hook disabled\\n");
                }
            #endif
                ret = schedule_work(&stop_input_hook_work);
                pr_info("unregister input kprobe: %d!\\n", ret);
            }
            """),
        )

    if "ksud: using SUSFS inline init.rc hooks" not in text and "ksu_syscall_table_hook(" in text:
        text = replace_first_function(
            text,
            ("void __init ksu_ksud_init()", "void __init ksu_ksud_init(void)"),
            dedent("""\
            void __init ksu_ksud_init(void)
            {
                int ret;

            #ifndef CONFIG_KSU_SUSFS
                ksu_syscall_table_hook(__NR_read, ksu_sys_read, &orig_sys_read);
                ksu_syscall_table_hook(__NR_fstat, ksu_sys_fstat, &orig_sys_fstat);
            #else
                pr_info("ksud: using SUSFS inline init.rc hooks\\n");
            #endif

                ret = register_kprobe(&input_event_kp);
                pr_info("ksud: input_event_kp: %d\\n", ret);

                INIT_WORK(&stop_input_hook_work, do_stop_input_hook);
            }
            """),
        )

    if "ksu_handle_execveat_ksud(path, &argv);" in text:
        text = replace_first_function(
            text,
            ("void ksu_execve_hook_ksud(const struct pt_regs *regs)",),
            dedent("""\
            void ksu_execve_hook_ksud(const struct pt_regs *regs)
            {
            #ifdef CONFIG_KSU_SUSFS
                /* ABK: Official SUSFS uses inline execve hooks instead. */
                (void)regs;
            #else
                const char __user **filename_user = (const char **)&PT_REGS_PARM1(regs);
                const char __user *const __user *__argv = (const char __user *const __user *)PT_REGS_PARM2(regs);
                struct user_arg_ptr argv = { .ptr.native = __argv };
                char path[32];
                long ret;
                unsigned long addr;
                const char __user *fn;

                if (!filename_user)
                    return;

                addr = untagged_addr((unsigned long)*filename_user);
                fn = (const char __user *)addr;

                memset(path, 0, sizeof(path));
                ret = strncpy_from_user(path, fn, 32);
                if (ret < 0) {
                    pr_err("Access filename failed for execve_handler_pre\\n");
                    return;
                }

                ksu_handle_execveat_ksud(path, &argv);
            #endif
            }
            """),
        )

    if text == original:
        return False

    path.write_text(text)
    print(f"已修复 {path} 的 Official SUSFS runtime 兼容")
    return True

def ensure_official_boot_event_compat(ksu_dir):
    path = ksu_dir / "runtime/boot_event.c"
    if not path.exists():
        return False

    text = path.read_text()
    original = text
    if "void ksu_stop_input_hook_runtime(void);" not in text:
        text = ensure_line_after(
            text,
            '#include "runtime/ksud.h"\n',
            "void ksu_stop_input_hook_runtime(void);\n",
        )

    if text == original:
        return False

    path.write_text(text)
    print(f"已补齐 {path} 的 ksu_stop_input_hook_runtime 声明")
    return True

def official_sulog_argv_is_pointer(event_text, symbol):
    pattern = re.compile(
        rf"{re.escape(symbol)}\s*\([^;]*?\bstruct\s+user_arg_ptr\s*\*\s*argv_user\b",
        re.S,
    )
    return pattern.search(event_text) is not None

def ensure_official_sulog_argv_compat(ksu_dir):
    event_h = ksu_dir / "sulog/event.h"
    if not event_h.exists():
        return False

    event_text = event_h.read_text()

    def patch_call_second_arg(path, symbol):
        if not path.exists():
            raise SystemExit(f"未找到 {path}，无法修复 {symbol} argv 参数类型")

        text = path.read_text()
        match = re.search(
            rf"{re.escape(symbol)}\s*\(\s*[^,\n]+,\s*([^,\n]+)\s*,",
            text,
        )
        if not match:
            return False

        current_arg = match.group(1).strip()
        if current_arg in (
            "(struct user_arg_ptr *)argv_user",
            "(struct user_arg_ptr*)argv_user",
            "&argv",
        ):
            return False

        if current_arg != "argv_user":
            raise SystemExit(f"{path} 的 {symbol} 第二参数为未知形态: {current_arg}")

        updated = (
            text[:match.start(1)]
            + "(struct user_arg_ptr *)argv_user"
            + text[match.end(1):]
        )
        path.write_text(updated)
        print(f"已修复 {path} 的 {symbol} argv 参数类型")
        return True

    changed = False
    if official_sulog_argv_is_pointer(event_text, "ksu_sulog_capture_sucompat"):
        changed = patch_call_second_arg(
            ksu_dir / "feature/sucompat.c",
            "ksu_sulog_capture_sucompat",
        ) or changed

    if official_sulog_argv_is_pointer(event_text, "ksu_sulog_capture_root_execve"):
        changed = patch_call_second_arg(
            ksu_dir / "hook/syscall_event_bridge.c",
            "ksu_sulog_capture_root_execve",
        ) or changed

    return changed

def ensure_official_app_profile_compat(ksu_dir):
    changed = False

    header = ksu_dir / "policy/app_profile.h"
    if header.exists():
        header_text = header.read_text()
        updated_header = re.sub(
            r"(?m)^void\s+escape_to_root_for_init\s*\(\s*void\s*\)\s*;",
            "int escape_to_root_for_init(void);",
            header_text,
        )
        if updated_header != header_text:
            header.write_text(updated_header)
            changed = True
            print(f"已修复 {header} 的 escape_to_root_for_init 声明")

    path = ksu_dir / "policy/app_profile.c"
    if not path.exists():
        return changed

    text = path.read_text()
    original = text

    text = text.replace('#include "hook/tp_marker.h"\n', "")
    text = re.sub(
        r"(?m)^static\s+void\s+disable_seccomp\s*\(\s*void\s*\)",
        "void disable_seccomp(void)",
        text,
        count=1,
    )
    text = re.sub(
        r"(?m)^([ \t]*)disable_seccomp\(\);\n",
        r"\1if (likely(test_thread_flag(TIF_SECCOMP)))\n\1    disable_seccomp();\n",
        text,
        count=1,
    )
    text = re.sub(
        r"(?ms)\n[ \t]*for_each_thread\s*\(\s*p\s*,\s*t\s*\)\s*\{\n"
        r"[ \t]*ksu_set_task_tracepoint_flag\s*\(\s*t\s*\)\s*;\n"
        r"[ \t]*\}\n",
        "\n",
        text,
        count=1,
    )
    if "for_each_thread" not in text:
        text = re.sub(
            r"(?m)^[ \t]*struct\s+task_struct\s+\*p\s*=\s*current\s*;\n",
            "",
            text,
            count=1,
        )
        text = re.sub(
            r"(?m)^[ \t]*struct\s+task_struct\s+\*t\s*;\n",
            "",
            text,
            count=1,
        )
    text = re.sub(
        r"(?m)^void\s+escape_to_root_for_init\s*\(\s*void\s*\)",
        "int escape_to_root_for_init(void)",
        text,
        count=1,
    )
    if "int escape_to_root_for_init(void)" in text:
        text = re.sub(
            r'(?m)^([ \t]*)pr_err\("Failed to prepare init\'s creds!\\n"\);\n([ \t]*)return;\n',
            r'\1pr_err("Failed to prepare init\'s creds!\\n");\n\1return -EINVAL;\n',
            text,
            count=1,
        )
        if re.search(r"(?ms)int\s+escape_to_root_for_init\s*\(\s*void\s*\)\s*\{.*?commit_creds\(cred\);\n\s*\}", text):
            text = re.sub(
                r"(?ms)(int\s+escape_to_root_for_init\s*\(\s*void\s*\)\s*\{.*?commit_creds\(cred\);\n)(\s*\})",
                r"\1\n    return 0;\n\2",
                text,
                count=1,
            )

    if text != original:
        path.write_text(text)
        changed = True
        print(f"已修复 {path} 的 Official app_profile 兼容")

    return changed

def ensure_official_sucompat_execveat_compat(ksu_dir):
    changed = False

    header = ksu_dir / "feature/sucompat.h"
    desired_header = dedent("""\
    #ifndef __KSU_H_SUCOMPAT
    #define __KSU_H_SUCOMPAT
    #include <asm/ptrace.h>
    #include <linux/types.h>

    struct filename;
    struct user_arg_ptr;
    struct static_key_true;

    extern struct static_key_true ksu_su_compat_enabled;

    void ksu_sucompat_init(void);
    void ksu_sucompat_exit(void);

    // Handler functions exported for hook_manager / SUSFS execveat path
    int ksu_handle_faccessat(int *dfd, const char __user **filename_user, int *mode, int *__unused_flags);
    #if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 1, 0) && defined(CONFIG_KSU_SUSFS)
    int ksu_handle_stat(int *dfd, struct filename **filename, int *flags);
    #else
    int ksu_handle_stat(int *dfd, const char __user **filename_user, int *flags);
    #endif
    int ksu_handle_execveat_init(struct filename *filename, struct user_arg_ptr *argv_user, struct user_arg_ptr *envp_user);
    int ksu_handle_execveat_sucompat(int *fd, struct filename **filename_ptr, void *argv_user,
                                     void *envp_user, int *__never_use_flags);
    int ksu_handle_execveat(int *fd, struct filename **filename_ptr, void *argv,
                            void *envp, int *flags);
    int ksu_handle_post_execveat_sucompat(int *fd, struct filename **filename_ptr,
                                           void *argv_user, void *envp_user,
                                           int *flags, int *retval);
    long ksu_handle_execve_sucompat(const char __user **filename_user, int orig_nr, const struct pt_regs *regs);

    #endif
    """)
    if header.exists():
        header_text = header.read_text()
        if header_text != desired_header:
            header.write_text(desired_header)
            changed = True
            print(f"已重写 {header} 的 Official sucompat 声明")

    path = ksu_dir / "feature/sucompat.c"
    if not path.exists():
        return changed

    text = path.read_text()
    original = text

    text = ensure_include(text, "#include <linux/jump_label.h>", "#include <linux/printk.h>\n")
    text = ensure_include(text, "#include <linux/susfs_def.h>", "#include <linux/ptrace.h>\n")
    text = ensure_include(text, "#include <linux/namei.h>", "#include <linux/susfs_def.h>\n")
    text = ensure_include(text, "#include <linux/minmax.h>", "#include <linux/namei.h>\n")
    text = ensure_include(text, "#include <linux/fs_struct.h>", "#include <linux/minmax.h>\n")
    text = ensure_include(text, '#include "selinux/selinux.h"', "#include <linux/fs_struct.h>\n")
    text = ensure_include(text, '#include "objsec.h"', '#include "selinux/selinux.h"\n')
    text = ensure_include(text, '#include "feature/adb_root.h"', '#include "feature/sucompat.h"\n')
    text = ensure_include(text, '#include "uapi/sulog.h"', '#include "sulog/event.h"\n')
    text = ensure_include(text, '#include "supercall/supercall.h"', '#include "feature/adb_root.h"\n')
    text = text.replace('#include "ksu.h"\n', "")
    text = text.replace('#include "util.h"\n', "")

    replacement = dedent("""\
    #define SU_PATH "/system/bin/su"
    #define SH_PATH "/system/bin/sh"

    static const char sh_path[] = SH_PATH;
    static const char su_path[] = SU_PATH;
    static const char ksud_path[] = KSUD_PATH;

    DEFINE_STATIC_KEY_TRUE(ksu_su_compat_enabled);

    static int su_compat_feature_get(u64 *value)
    {
        if (static_key_enabled(&ksu_su_compat_enabled))
            *value = 1;
        else
            *value = 0;
        return 0;
    }

    static int su_compat_feature_set(u64 value)
    {
        bool enable = value != 0;
        if (enable)
            static_branch_enable(&ksu_su_compat_enabled);
        else
            static_branch_disable(&ksu_su_compat_enabled);
        pr_info("su_compat: set to %d\\n", enable);
        return 0;
    }

    static const struct ksu_feature_handler su_compat_handler = {
        .feature_id = KSU_FEATURE_SU_COMPAT,
        .name = "su_compat",
        .get_handler = su_compat_feature_get,
        .set_handler = su_compat_feature_set,
    };

    static void __user *userspace_stack_buffer(const void *d, size_t len)
    {
        char __user *p = (void __user *)current_user_stack_pointer() - len;

        return copy_to_user(p, d, len) ? NULL : p;
    }

    static char __user *sh_user_path(void)
    {
        static const char sh_path_local[] = "/system/bin/sh";

        return userspace_stack_buffer(sh_path_local, sizeof(sh_path_local));
    }

    static char __user *ksud_user_path(void)
    {
        static const char ksud_path_local[] = KSUD_PATH;

        return userspace_stack_buffer(ksud_path_local, sizeof(ksud_path_local));
    }

    static const char __user *get_user_arg_ptr(struct user_arg_ptr argv, int nr)
    {
        const char __user *native;

    #ifdef CONFIG_COMPAT
        if (unlikely(argv.is_compat)) {
            compat_uptr_t compat;

            if (get_user(compat, argv.ptr.compat + nr))
                return ERR_PTR(-EFAULT);

            return compat_ptr(compat);
        }
    #endif

        if (get_user(native, argv.ptr.native + nr))
            return ERR_PTR(-EFAULT);

        return native;
    }

    /*
     * return 0 -> No further checks should be required afterwards
     * return non-zero -> Further checks should be continued afterwards
     */
    int ksu_handle_execveat_init(struct filename *filename, struct user_arg_ptr *argv_user, struct user_arg_ptr *envp_user)
    {
        if (current->pid != 1 && is_init(get_current_cred())) {
            int ret;
            if (unlikely(strcmp(filename->name, KSUD_PATH) == 0)) {
                const char __user *argv_user_ptr = get_user_arg_ptr(*argv_user, 0);
                struct ksu_sulog_pending_event *pending_sucompat = NULL;

                if (!argv_user_ptr || IS_ERR(argv_user_ptr)) {
                    pr_err("!argv_user_ptr || IS_ERR(argv_user_ptr)\\n");
                    return -EFAULT;
                }
                pr_info("hook_manager: escape to root for init executing ksud: %d\\n", current->pid);
                pending_sucompat = ksu_sulog_capture_sucompat(filename->name, (struct user_arg_ptr *)argv_user, GFP_KERNEL);
                ret = escape_to_root_for_init();
                ksu_sulog_emit_pending(pending_sucompat, ret, GFP_KERNEL);
                if (ret) {
                    pr_err("escape_to_root_for_init() failed: %d\\n", ret);
                }
                return ret;
            } else if (likely(strstr(filename->name, "/app_process") == NULL &&
                               strstr(filename->name, "/adbd") == NULL) &&
                       !susfs_is_current_proc_umounted()) {
                pr_info("susfs: mark no sucompat checks for pid: '%d', exec: '%s'\\n", current->pid, filename->name);
                susfs_set_current_proc_umounted();
                return 0;
            }

    #ifdef CONFIG_COMPAT
            if (unlikely(envp_user->is_compat))
                ret = 0; /* ABK: adb-root API no longer accepts filename/envp arguments. */
            else
                ret = 0; /* ABK: adb-root API no longer accepts filename/envp arguments. */
    #else
            ret = 0; /* ABK: adb-root API no longer accepts filename/envp arguments. */
    #endif

            if (ret) {
                pr_err("adb root failed: %d\\n", ret);
                return ret;
            }
        }

        return -EINVAL;
    }

    int ksu_handle_execveat_sucompat(int *fd, struct filename **filename_ptr,
                                     void *argv_user, void *envp_user,
                                     int *__never_use_flags)
    {
        struct filename *filename;
        const char __user *argv_user_ptr;
        struct ksu_sulog_pending_event *pending_sucompat = NULL;
        int ret;

        if (unlikely(!filename_ptr || !*filename_ptr || !argv_user || !envp_user))
            return 1;

        filename = *filename_ptr;
        if (IS_ERR(filename) || !filename->name)
            return 1;

        /* Zero means a successful su session to the SUSFS caller. */
        if (!ksu_handle_execveat_init(filename, (struct user_arg_ptr *)argv_user, (struct user_arg_ptr *)envp_user))
            return strcmp(filename->name, KSUD_PATH) ? 1 : 0;

        if (likely(memcmp(filename->name, su_path, sizeof(su_path))))
            return 1;

        if (current_chrooted()) {
            pr_err("ksu_handle_execveat_sucompat: su found but NOT allowed! Because current process is running in chrooted environment\\n");
            return 1;
        }

        argv_user_ptr = get_user_arg_ptr(*((struct user_arg_ptr *)argv_user), 0);
        if (!argv_user_ptr || IS_ERR(argv_user_ptr)) {
            pr_err("!argv_user_ptr || IS_ERR(argv_user_ptr)\\n");
            return -EFAULT;
        }

        pr_info("ksu_handle_execveat_sucompat: su found\\n");

        memcpy((void *)filename->name, ksud_path, sizeof(ksud_path));

        pending_sucompat = ksu_sulog_capture_sucompat(filename->name, (struct user_arg_ptr *)argv_user, GFP_KERNEL);

        ret = escape_with_root_profile();
        if (ret)
            pr_err("escape_with_root_profile() failed: %d\\n", ret);

        ksu_sulog_emit_pending(pending_sucompat, ret, GFP_KERNEL);
        return ret;
    }

    #ifdef CONFIG_KSU_SUSFS
    int ksu_handle_post_execveat_sucompat(int *fd, struct filename **filename_ptr,
                                           void *argv_user, void *envp_user,
                                           int *flags, int *retval)
    {
        /* Never inject a driver FD into an ordinary or failed exec. */
        if (!retval || *retval < 0 || !filename_ptr || !*filename_ptr ||
            IS_ERR(*filename_ptr) || !(*filename_ptr)->name ||
            strcmp((*filename_ptr)->name, KSUD_PATH))
            return 0;
        (void)ksu_install_su_fd();
        return 0;
    }
    #endif

    int ksu_handle_execveat(int *fd, struct filename **filename_ptr, void *argv,
                            void *envp, int *flags)
    {
        if (ksu_handle_execveat_ksud(fd, filename_ptr, argv, envp, flags))
            return 1;

        return ksu_handle_execveat_sucompat(fd, filename_ptr, argv, envp, flags);
    }

    int ksu_handle_faccessat(int *dfd, const char __user **filename_user, int *mode,
                             int *__unused_flags)
    {
        char path[sizeof(su_path) + 1] = {0};

        strncpy_from_user(path, *filename_user, sizeof(path));

        if (unlikely(!memcmp(path, su_path, sizeof(su_path)))) {
            if (current_chrooted()) {
                pr_err("ksu_handle_faccessat: su found but NOT allowed! Because current process is running in chrooted environment\\n");
                return 0;
            }
            pr_info("ksu_handle_faccessat: su->sh!\\n");
            *filename_user = sh_user_path();
        }

        return 0;
    }

    #if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 1, 0)
    int ksu_handle_stat(int *dfd, struct filename **filename, int *flags)
    {
        if (unlikely(IS_ERR(*filename) || (*filename)->name == NULL))
            return 0;

        if (likely(memcmp((*filename)->name, su_path, sizeof(su_path))))
            return 0;

        if (current_chrooted()) {
            pr_err("ksu_handle_stat: su found but NOT allowed! Because current process is running in chrooted environment\\n");
            return 0;
        }
        pr_info("ksu_handle_stat: su->sh!\\n");
        memcpy((void *)((*filename)->name), sh_path, sizeof(sh_path));
        return 0;
    }
    #else
    int ksu_handle_stat(int *dfd, const char __user **filename_user, int *flags)
    {
        char path[sizeof(su_path) + 1] = {0};

        if (unlikely(!filename_user))
            return 0;

        strncpy_from_user(path, *filename_user, sizeof(path));

        if (unlikely(!memcmp(path, su_path, sizeof(su_path)))) {
            if (current_chrooted()) {
                pr_err("ksu_handle_stat: su found but NOT allowed! Because current process is running in chrooted environment\\n");
                return 0;
            }
            pr_info("ksu_handle_stat: su->sh!\\n");
            *filename_user = sh_user_path();
        }

        return 0;
    }
    #endif // #if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 1, 0)

    long ksu_handle_execve_sucompat(const char __user **filename_user, int orig_nr, const struct pt_regs *regs)
    {
        /* ABK: Official SUSFS routes execveat through fs/exec.c direct hooks instead. */
        (void)filename_user;
        (void)orig_nr;
        (void)regs;
        return -EINVAL;
    }

    // sucompat: permitted process can execute 'su' to gain root access.
    """)

    # ABK: 新版 susfs4ksu 的 10_enable_susfs_for_ksu.patch 会把
    # struct user_arg_ptr 与 static inline get_user_arg_ptr 注入
    # runtime/ksud.h，而 sucompat.c 直接 include 该头文件；此时必须
    # 去掉注入文本里的同名定义，否则报 redefinition of 'get_user_arg_ptr'。
    # 旧版补丁树（ksud.h 无此定义）仍保留注入定义。
    ksud_header = ksu_dir / "runtime/ksud.h"
    if ksud_header.exists() and "static inline const char __user *get_user_arg_ptr" in ksud_header.read_text():
        stripped = re.sub(
            r"static const char __user \*get_user_arg_ptr\(struct user_arg_ptr argv, int nr\)\n\{.*?\n\}\n\n",
            "",
            replacement,
            count=1,
            flags=re.S,
        )
        if stripped == replacement:
            raise SystemExit(f"{path}: runtime/ksud.h 已定义 get_user_arg_ptr，但未能在注入文本中定位重复定义")
        replacement = stripped
        print(f"runtime/ksud.h 已提供 get_user_arg_ptr，已移除 {path} 注入文本中的重复定义")

    region_pattern = re.compile(
        r'#define SU_PATH "/system/bin/su"\n#define SH_PATH "/system/bin/sh"\n.*?\n// sucompat: permitted process can execute \'su\' to gain root access\.\n',
        re.S,
    )
    if not region_pattern.search(text):
        raise SystemExit(f"{path} 缺少 Official sucompat execveat 兼容替换锚点")
    # Keep C escape sequences literal; re.sub() would reinterpret \n in the replacement text.
    text = region_pattern.sub(lambda _match: replacement, text, count=1)

    if text != original:
        path.write_text(text)
        changed = True
        print(f"已重写 {path} 的 Official sucompat execveat 兼容实现")

    return changed

def ensure_line_after_any(text, anchors, line, error_message):
    if line in text:
        return text
    for anchor in anchors:
        if anchor in text:
            return text.replace(anchor, anchor + line, 1)
    raise SystemExit(error_message)

def normalize_official_selinux_hide_exports(text):
    text = re.sub(
        r"(?m)^\s*(?:static\s+|__maybe_static\s+)*struct\s+selinux_state\s+fake_state\s*;",
        "struct selinux_state fake_state;",
        text,
    )
    text = re.sub(
        r"(?m)^\s*(?:static\s+|__maybe_static\s+)*bool\s+ksu_selinux_hide_running\s+__read_mostly\s*=\s*false\s*;",
        "bool ksu_selinux_hide_running __read_mostly = false;",
        text,
    )
    text = re.sub(
        r"(?m)^\s*(?:static\s+|__maybe_static\s+)*bool\s+ksu_selinux_hide_enabled\s+__read_mostly\s*=\s*false\s*;",
        "bool ksu_selinux_hide_enabled __read_mostly = false;",
        text,
    )
    text = re.sub(
        r"(?m)^\s*(?:static\s+|__maybe_static\s+)*int\s+security_context_to_sid_with_policy\s*\(",
        "int security_context_to_sid_with_policy(",
        text,
    )
    text = re.sub(
        r"(?m)^\s*(?:static\s+|__maybe_static\s+)*int\s+security_sid_to_context_with_policy\s*\(",
        "int security_sid_to_context_with_policy(",
        text,
    )
    text = re.sub(
        r"(?m)^\s*(?:static\s+|__maybe_static\s+)*void\s+__nocfi\s+security_compute_av_user_with_policy\s*\(",
        "void __nocfi security_compute_av_user_with_policy(",
        text,
    )
    text = re.sub(
        r"(?m)^\s*(?:static\s+|__maybe_static\s+)*void\s+security_compute_av_user_with_policy\s*\(",
        "void security_compute_av_user_with_policy(",
        text,
    )
    text = re.sub(
        r"(?m)^\s*(?:static\s+|__maybe_static\s+)*DEFINE_STATIC_KEY_FALSE\s*\(\s*fake_status_initialize_key\s*\)\s*;",
        "DEFINE_STATIC_KEY_FALSE(fake_status_initialize_key);",
        text,
    )
    text = re.sub(
        r"(?m)^\s*(?:static\s+|__maybe_static\s+)*struct\s+page\s*\*\s*fake_status\s*(?:=\s*NULL)?\s*;",
        "struct page *fake_status = NULL;",
        text,
    )
    text = re.sub(
        r"(?m)^\s*(?:static\s+|__maybe_static\s+)*void\s+initialize_fake_status\s*\(\s*(?:void)?\s*\)",
        "void initialize_fake_status(void)",
        text,
    )
    return text

def ensure_official_selinux_hide_exports(text, path):
    text = normalize_official_selinux_hide_exports(text)

    if not re.search(r"(?m)^\s*bool\s+ksu_selinux_hide_enabled\s+__read_mostly\s*=\s*false\s*;", text):
        text = ensure_line_after_any(
            text,
            ("static DEFINE_MUTEX(selinux_hide_mutex);\n", "DEFINE_MUTEX(selinux_hide_mutex);\n"),
            "bool ksu_selinux_hide_enabled __read_mostly = false;\n",
            f"{path} 缺少 selinux_hide_mutex 锚点，无法插入 ksu_selinux_hide_enabled",
        )
    if not re.search(r"(?m)^\s*bool\s+ksu_selinux_hide_running\s+__read_mostly\s*=\s*false\s*;", text):
        text = ensure_line_after_any(
            text,
            ("bool ksu_selinux_hide_enabled __read_mostly = false;\n",),
            "bool ksu_selinux_hide_running __read_mostly = false;\n",
            f"{path} 缺少 ksu_selinux_hide_enabled 锚点，无法插入 ksu_selinux_hide_running",
        )
    if not re.search(r"(?m)^\s*struct\s+selinux_state\s+fake_state\s*;", text):
        text = ensure_line_after_any(
            text,
            ("bool ksu_selinux_hide_running __read_mostly = false;\n",),
            "struct selinux_state fake_state;\n",
            f"{path} 缺少 ksu_selinux_hide_running 锚点，无法插入 fake_state",
        )

    fake_status_additions = []
    if not re.search(r"(?m)^\s*DEFINE_STATIC_KEY_FALSE\s*\(\s*fake_status_initialize_key\s*\)\s*;", text):
        fake_status_additions.append("DEFINE_STATIC_KEY_FALSE(fake_status_initialize_key);")
    if not re.search(r"(?m)^\s*struct\s+page\s*\*\s*fake_status\s*=\s*NULL\s*;", text):
        fake_status_additions.append("struct page *fake_status = NULL;")
    if not re.search(r"(?m)^\s*void\s+initialize_fake_status\s*\(\s*(?:void)?\s*\)\s*\{", text):
        fake_status_additions.extend([
            "void initialize_fake_status(void)",
            "{",
            "}",
        ])
    if fake_status_additions:
        if "#include <linux/jump_label.h>" not in text:
            text = ensure_include(text, "#include <linux/jump_label.h>", "#include <linux/mutex.h>\n")
        block = "\n".join([
            "",
            "#ifdef CONFIG_KSU_SUSFS",
            *fake_status_additions,
            "#endif",
            "",
        ])
        text = ensure_line_after_any(
            text,
            ("bool ksu_selinux_hide_running __read_mostly = false;\n",),
            block,
            f"{path} 缺少 fake status 插入锚点",
        )

    return text

def validate_official_selinux_hide_exports(text, path, scope):
    required_patterns = {
        "ksu_selinux_hide_enabled": r"(?m)^\s*bool\s+ksu_selinux_hide_enabled\s+__read_mostly\s*=\s*false\s*;",
        "ksu_selinux_hide_running": r"(?m)^\s*bool\s+ksu_selinux_hide_running\s+__read_mostly\s*=\s*false\s*;",
        "fake_state": r"(?m)^\s*struct\s+selinux_state\s+fake_state\s*;",
        "fake_status_initialize_key": r"(?m)^\s*DEFINE_STATIC_KEY_FALSE\s*\(\s*fake_status_initialize_key\s*\)\s*;",
        "fake_status": r"(?m)^\s*struct\s+page\s*\*\s*fake_status\s*=\s*NULL\s*;",
        "initialize_fake_status": r"(?m)^\s*void\s+initialize_fake_status\s*\(\s*(?:void)?\s*\)\s*\{",
    }
    missing = [name for name, pattern in required_patterns.items() if not re.search(pattern, text)]
    if missing:
        raise SystemExit(f"{path} 缺少 {scope}: {missing}")

    forbidden_patterns = {
        "ksu_selinux_hide_enabled": r"(?m)^\s*(?:static|__maybe_static)\s+bool\s+ksu_selinux_hide_enabled\s+__read_mostly\s*=\s*false\s*;",
        "ksu_selinux_hide_running": r"(?m)^\s*(?:static|__maybe_static)\s+bool\s+ksu_selinux_hide_running\s+__read_mostly\s*=\s*false\s*;",
        "fake_state": r"(?m)^\s*(?:static|__maybe_static)\s+struct\s+selinux_state\s+fake_state\s*;",
        "fake_status_initialize_key": r"(?m)^\s*(?:static|__maybe_static)\s+DEFINE_STATIC_KEY_FALSE\s*\(\s*fake_status_initialize_key\s*\)\s*;",
        "fake_status": r"(?m)^\s*(?:static|__maybe_static)\s+struct\s+page\s*\*\s*fake_status\s*(?:=\s*NULL)?\s*;",
        "initialize_fake_status": r"(?m)^\s*(?:static|__maybe_static)\s+void\s+initialize_fake_status\s*\(",
    }
    stale = [name for name, pattern in forbidden_patterns.items() if re.search(pattern, text)]
    if stale:
        raise SystemExit(f"{path} 仍残留 static {scope}: {stale}")

    running_match = re.search(
        r"(?m)^\s*bool\s+ksu_selinux_hide_running\s+__read_mostly\s*=\s*false\s*;",
        text,
    )
    if not running_match:
        raise SystemExit(f"{path} 缺少 {scope} 锚点: ksu_selinux_hide_running")

    for name, pattern in (
        ("fake_status_initialize_key", r"(?m)^\s*DEFINE_STATIC_KEY_FALSE\s*\(\s*fake_status_initialize_key\s*\)\s*;"),
        ("fake_status", r"(?m)^\s*struct\s+page\s*\*\s*fake_status\s*=\s*NULL\s*;"),
        ("initialize_fake_status", r"(?m)^\s*void\s+initialize_fake_status\s*\(\s*(?:void)?\s*\)\s*\{"),
    ):
        match = re.search(pattern, text)
        if not match:
            raise SystemExit(f"{path} 缺少 {scope}: {name}")
        if match.start() < running_match.start():
            raise SystemExit(f"{path} 的 {name} 仍位于错误的预处理分支中")

def patch_official_selinux_hide(ksu_dir):
    path = ksu_dir / "feature/selinux_hide.c"
    if not path.exists():
        return False

    text = path.read_text()
    original = text

    text = ensure_official_selinux_hide_exports(text, path)

    dump_is_pointer = re.search(r"\(\s*\*\s*security_dump_masked_av_fn\s*\)", text) is not None
    compute_is_pointer = re.search(r"\(\s*\*\s*context_struct_compute_av_fn\s*\)", text) is not None

    if dump_is_pointer:
        text = re.sub(
            r"(?m)^([ \t]*)if \(security_dump_masked_av_fn\)\s*$",
            r"\1if (security_dump_masked_av_fn != NULL)",
            text,
        )
    else:
        text = re.sub(
            r'(?m)^([ \t]*)if \(security_dump_masked_av_fn\)\n[ \t]*security_dump_masked_av_fn\(policydb, scontext, tcontext, tclass, masked, "bounds"\);\n',
            r'\1security_dump_masked_av_fn(policydb, scontext, tcontext, tclass, masked, "bounds");\n',
            text,
        )

    if compute_is_pointer:
        text = re.sub(
            r"(?m)^([ \t]*)if \(context_struct_compute_av_fn\) \{\s*$",
            r"\1if (context_struct_compute_av_fn != NULL) {",
            text,
        )
    else:
        text = re.sub(
            r"(?m)^([ \t]*)if \(context_struct_compute_av_fn\) \{\n"
            r"[ \t]*context_struct_compute_av_fn\(policydb, scontext, tcontext, tclass, avd, NULL\);\n"
            r"[ \t]*\} else \{\n"
            r"[ \t]*context_struct_compute_av\(policydb, scontext, tcontext, tclass, avd, NULL\);\n"
            r"[ \t]*\}\n",
            r"\1context_struct_compute_av_fn(policydb, scontext, tcontext, tclass, avd, NULL);\n",
            text,
        )

    validate_official_selinux_hide_exports(text, path, "Official selinux_hide 导出")

    if text == original:
        return False

    path.write_text(text)
    print(f"已修复 {path} 的 Official selinux_hide 导出与函数指针判断")
    return True

def ensure_official_susfs_objects(ksu_dir):
    kbuild = ksu_dir / "Kbuild"
    if not kbuild.exists():
        return False

    text = kbuild.read_text()
    missing = []
    if "feature/selinux_hide.o" not in text:
        missing.append("kernelsu-objs += feature/selinux_hide.o")
    if "hook/lsm_hook.o" not in text:
        missing.append("kernelsu-objs += hook/lsm_hook.o")
    if "hook/arm64/patch_memory.o" not in text:
        missing.extend([
            "ifeq ($(CONFIG_ARM64),y)",
            "kernelsu-objs += hook/arm64/patch_memory.o",
            "endif",
        ])
    if not missing:
        return False

    block = "\n".join(["", "# ABK: selinux_hide.c still needs these hook helpers."] + missing + [""])
    kbuild.write_text(text.rstrip() + "\n" + block)
    print(f"已补回 {kbuild} 的 Official SUSFS hook helper 对象")
    return True

def ensure_official_symbol_resolver_exports(ksu_dir):
    path = ksu_dir / "hook/lsm_hook.c"
    if not path.exists():
        raise SystemExit(f"未找到 {path}，无法补齐 Official SUSFS hook 符号")

    text = path.read_text()
    additions = []
    if "abk_ksu_lookup_symbol(" not in text:
        additions.append(
            "#include <linux/kallsyms.h>\n"
            "#include <linux/kernel.h>\n\n"
            "static void *abk_ksu_lookup_symbol(const char *symbol_name)\n"
            "{\n"
            "#ifdef CONFIG_KALLSYMS\n"
            "    unsigned long addr;\n"
            "    char cfi_name[KSYM_NAME_LEN];\n\n"
            "    if (!symbol_name || !symbol_name[0])\n"
            "        return NULL;\n\n"
            "    addr = kallsyms_lookup_name(symbol_name);\n"
            "    if (addr)\n"
            "        return (void *)addr;\n\n"
            "    if (scnprintf(cfi_name, sizeof(cfi_name), \"%s%s\", symbol_name, \".cfi_jt\") >= sizeof(cfi_name))\n"
            "        return NULL;\n\n"
            "    addr = kallsyms_lookup_name(cfi_name);\n"
            "    return addr ? (void *)addr : NULL;\n"
            "#else\n"
            "    return NULL;\n"
            "#endif\n"
            "}\n"
        )
    if not re.search(r"(?m)^\s*void\s+\*\s*ksu_resolve_symbol_for_functable_hook\s*\(", text):
        additions.append("void *ksu_resolve_symbol_for_functable_hook(const char *symbol_name)\n{\n    return abk_ksu_lookup_symbol(symbol_name);\n}\n")
    if not re.search(r"(?m)^\s*unsigned long\s+find_kernel_symbol_exact\s*\(", text):
        additions.append("unsigned long find_kernel_symbol_exact(const char *symbol_name)\n{\n    return (unsigned long)abk_ksu_lookup_symbol(symbol_name);\n}\n")
    if not re.search(r"(?m)^\s*void\s+ksu_init_symbol_resolver\s*\(", text):
        additions.append("void ksu_init_symbol_resolver(void)\n{\n}\n")

    if not additions:
        return False

    block = "\n/* ABK: Official SUSFS lsm_hook local symbol resolver exports. */\n" + "\n".join(additions)
    path.write_text(text.rstrip() + "\n" + block)
    print(f"已补齐 {path} 的 Official SUSFS hook 符号")
    return True

def ensure_official_susfs_dispatch_compat(ksu_dir):
    path = ksu_dir / "supercall/dispatch.c"
    if not path.exists():
        raise SystemExit(f"未找到 {path}，无法补齐 Official SUSFS supercall 声明")

    text = path.read_text()
    if not any(marker in text for marker in ("SUSFS_MAGIC", "CMD_SUSFS_", "susfs_")):
        return False

    original = text
    missing = []
    if "#include <linux/namei.h>" not in text:
        missing.append("#include <linux/namei.h>")
    if "#include <linux/susfs.h>" not in text:
        missing.append("#include <linux/susfs.h>")
    if missing:
        line = "\n".join(missing) + "\n"
        for anchor in (
            "#include <linux/thread_info.h>\n",
            "#include <linux/version.h>\n",
            "#include <linux/uaccess.h>\n",
        ):
            if anchor in text:
                text = text.replace(anchor, anchor + line, 1)
                break
        else:
            raise SystemExit(f"{path} 缺少 Official SUSFS supercall include 插入锚点")

    if text == original:
        return False

    path.write_text(text)
    print(f"已补齐 {path} 的 Official SUSFS supercall 声明")
    return True

ksu_dir = Path("common/drivers/kernelsu")
if not ksu_dir.exists():
    raise SystemExit("未找到 common/drivers/kernelsu，无法修复 Official SUSFS")

changed = False
init_c = ksu_dir / "core/init.c"
if not init_c.exists():
    raise SystemExit(f"未找到 {init_c}")

text = init_c.read_text()
original = text

text = ensure_line_after(text, "#include <linux/moduleparam.h>\n", "#include <linux/susfs.h>\n")
text = text.replace('#include "hook/syscall_hook_manager.h"\n', "")
text = text.replace('#include "hook/syscall_hook.h"\n', "")
text = ensure_line_after(text, '#include "feature/adb_root.h"\n', '#include "hook/setuid_hook.h"\n')
text = ensure_line_after(text, '#include "hook/setuid_hook.h"\n', '#include "feature/sucompat.h"\n')
text = text.replace("bool ksu_late_loaded;\n", "")
text = re.sub(
    r'\n#if defined\(__x86_64__\)\n#include <asm/cpufeature\.h>\n#include <linux/version\.h>\n#ifndef X86_FEATURE_INDIRECT_SAFE\n#error "FATAL: Your kernel is missing the indirect syscall bypass patches!"\n#endif\n#endif\n',
    "\n",
    text,
    count=1,
)

text = replace_function(text, "int __init kernelsu_init(void)", INIT_BODY)
text = replace_function(text, "void __exit kernelsu_exit(void)", EXIT_BODY)

if "ksu_late_loaded" in text or "ksu_syscall_hook_manager_" in text:
    raise SystemExit(f"{init_c} 仍残留 Official SUSFS 不兼容符号")
for required in ("susfs_init();", "ksu_sucompat_init();", "ksu_setuid_hook_init();"):
    if required not in text:
        raise SystemExit(f"{init_c} 缺少必要初始化: {required}")

if text != original:
    init_c.write_text(text)
    changed = True
    print(f"已修复 {init_c}")
else:
    print(f"{init_c} 已满足 Official SUSFS 兼容要求")

include_ksu = ksu_dir / "include/ksu.h"
if include_ksu.exists():
    header = include_ksu.read_text()
    updated = header.replace("extern bool ksu_late_loaded;\n", "")
    if updated != header:
        include_ksu.write_text(updated)
        changed = True
        print(f"已移除 {include_ksu} 中的 ksu_late_loaded 声明")

changed = ensure_official_susfs_objects(ksu_dir) or changed
changed = patch_official_selinux_hide(ksu_dir) or changed
changed = ensure_official_symbol_resolver_exports(ksu_dir) or changed
changed = ensure_official_susfs_dispatch_compat(ksu_dir) or changed
changed = ensure_official_runtime_compat(ksu_dir) or changed
changed = ensure_official_boot_event_compat(ksu_dir) or changed
changed = ensure_official_sulog_argv_compat(ksu_dir) or changed
changed = ensure_official_app_profile_compat(ksu_dir) or changed
changed = ensure_official_sucompat_execveat_compat(ksu_dir) or changed

runtime_c = ksu_dir / "runtime/ksud_integration.c"
if runtime_c.exists():
    runtime_text = runtime_c.read_text()
    if re.search(r"(?m)^static void\s+ksu_handle_sys_read\s*\(", runtime_text):
        raise SystemExit(f"{runtime_c} 仍包含 static ksu_handle_sys_read，SUSFS fs/read_write.c 无法链接")
    if not re.search(r"(?m)^void\s+ksu_handle_sys_read\s*\(\s*unsigned int fd\s*\)", runtime_text):
        raise SystemExit(f"{runtime_c} 缺少单参数 ksu_handle_sys_read 导出")
    if not re.search(r"(?m)^void\s+ksu_handle_vfs_fstat\s*\(", runtime_text):
        raise SystemExit(f"{runtime_c} 缺少 ksu_handle_vfs_fstat 导出")
    if "orig_sys_fstat(" in runtime_text or "statbuf" in runtime_text:
        raise SystemExit(f"{runtime_c} 仍残留旧版 fstat hook 体")
    required_runtime = []
    if "ksu_syscall_table_unhook(" in runtime_text:
        required_runtime.append("ksu init rc inline hook disabled")
    if "ksu_syscall_table_hook(" in runtime_text:
        required_runtime.append("ksud: using SUSFS inline init.rc hooks")
    if re.search(r"(?m)^void\s+ksu_stop_input_hook_runtime\s*\([^;{]*\)\s*\{", runtime_text):
        required_runtime.append("ksu input inline hook disabled")
    missing = [marker for marker in required_runtime if marker not in runtime_text]
    if missing:
        raise SystemExit(f"{runtime_c} 缺少 Official SUSFS runtime 兼容标记: {missing}")
    if (
        "ksu_handle_execveat_ksud(path, &argv);" in runtime_text
        and "Official SUSFS uses inline execve hooks instead" not in runtime_text
    ):
        raise SystemExit(f"{runtime_c} 仍包含不兼容的 2 参数 ksu_handle_execveat_ksud 调用")

boot_event_c = ksu_dir / "runtime/boot_event.c"
if boot_event_c.exists() and "void ksu_stop_input_hook_runtime(void);" not in boot_event_c.read_text():
    raise SystemExit(f"{boot_event_c} 缺少 ksu_stop_input_hook_runtime 声明")

event_h = ksu_dir / "sulog/event.h"
if event_h.exists():
    event_text = event_h.read_text()

    sucompat_c = ksu_dir / "feature/sucompat.c"
    if official_sulog_argv_is_pointer(event_text, "ksu_sulog_capture_sucompat"):
        if not sucompat_c.exists():
            raise SystemExit(f"未找到 {sucompat_c}，无法校验 Official sulog sucompat argv 兼容")
        if re.search(
            r"ksu_sulog_capture_sucompat\s*\(\s*[^,\n]+,\s*argv_user\s*,",
            sucompat_c.read_text(),
        ):
            raise SystemExit(f"{sucompat_c} 仍以裸 argv_user 调用 ksu_sulog_capture_sucompat")

    bridge_c = ksu_dir / "hook/syscall_event_bridge.c"
    if official_sulog_argv_is_pointer(event_text, "ksu_sulog_capture_root_execve"):
        if not bridge_c.exists():
            raise SystemExit(f"未找到 {bridge_c}，无法校验 Official sulog root_execve argv 兼容")
        if re.search(
            r"ksu_sulog_capture_root_execve\s*\(\s*[^,\n]+,\s*argv_user\s*,",
            bridge_c.read_text(),
        ):
            raise SystemExit(f"{bridge_c} 仍以裸 argv_user 调用 ksu_sulog_capture_root_execve")

app_profile_c = ksu_dir / "policy/app_profile.c"
if app_profile_c.exists():
    app_profile_text = app_profile_c.read_text()
    if re.search(r"(?m)^\s*static\s+void\s+disable_seccomp\s*\(", app_profile_text):
        raise SystemExit(f"{app_profile_c} 仍保留 static disable_seccomp，Official SUSFS 外部调用会链接失败")
    if "for_each_thread (p, t)" in app_profile_text or "ksu_set_task_tracepoint_flag(t);" in app_profile_text:
        raise SystemExit(f"{app_profile_c} 仍残留 Official SUSFS 不兼容的 tp_marker 线程遍历逻辑")
    if re.search(r"(?m)^void\s+escape_to_root_for_init\s*\(\s*void\s*\)", app_profile_text):
        raise SystemExit(f"{app_profile_c} 仍保留旧版 void escape_to_root_for_init 定义")

app_profile_h = ksu_dir / "policy/app_profile.h"
if app_profile_h.exists():
    if re.search(r"(?m)^void\s+escape_to_root_for_init\s*\(\s*void\s*\)\s*;", app_profile_h.read_text()):
        raise SystemExit(f"{app_profile_h} 仍保留旧版 void escape_to_root_for_init 声明")

sucompat_h = ksu_dir / "feature/sucompat.h"
if sucompat_h.exists():
    sucompat_header = sucompat_h.read_text()
    if re.search(r"(?m)^extern\s+bool\s+ksu_su_compat_enabled\s*;", sucompat_header):
        raise SystemExit(f"{sucompat_h} 仍保留旧版 bool ksu_su_compat_enabled 声明")
    for marker in (
        "extern struct static_key_true ksu_su_compat_enabled;",
        "int ksu_handle_execveat_init(struct filename *filename, struct user_arg_ptr *argv_user, struct user_arg_ptr *envp_user);",
        "int ksu_handle_execveat_sucompat(int *fd, struct filename **filename_ptr, void *argv_user,",
        "int ksu_handle_execveat(int *fd, struct filename **filename_ptr, void *argv,",
    ):
        if marker not in sucompat_header:
            raise SystemExit(f"{sucompat_h} 缺少 Official sucompat execveat 声明: {marker}")

sucompat_c = ksu_dir / "feature/sucompat.c"
if sucompat_c.exists():
    sucompat_text = sucompat_c.read_text()
    for marker in (
        "DEFINE_STATIC_KEY_TRUE(ksu_su_compat_enabled);",
        "int ksu_handle_execveat_init(struct filename *filename, struct user_arg_ptr *argv_user, struct user_arg_ptr *envp_user)",
        "int ksu_handle_execveat_sucompat(int *fd, struct filename **filename_ptr,",
        "int ksu_handle_execveat(int *fd, struct filename **filename_ptr, void *argv,",
    ):
        if marker not in sucompat_text:
            raise SystemExit(f"{sucompat_c} 缺少 Official sucompat execveat 实现: {marker}")
    if "ksu_syscall_table[" in sucompat_text:
        raise SystemExit(f"{sucompat_c} 仍残留旧版 ksu_syscall_table execve 路径")

dispatch_c = ksu_dir / "supercall/dispatch.c"
if dispatch_c.exists():
    dispatch_text = dispatch_c.read_text()
    if any(marker in dispatch_text for marker in ("SUSFS_MAGIC", "CMD_SUSFS_", "susfs_")):
        missing = [
            include
            for include in ("#include <linux/namei.h>", "#include <linux/susfs.h>")
            if include not in dispatch_text
        ]
        if missing:
            raise SystemExit(f"{dispatch_c} 缺少 Official SUSFS supercall include: {missing}")

selinux_hide_c = ksu_dir / "feature/selinux_hide.c"
if selinux_hide_c.exists():
    validate_official_selinux_hide_exports(
        selinux_hide_c.read_text(),
        selinux_hide_c,
        "Official SUSFS SELinux 导出",
    )

if not changed:
    print("Official SUSFS 源码兼容修复无需修改。")
PY

}

# ---- 修复 Official SUSFS 6.1-6.18 SELinux 导出 ----
selinux-export() {
    cd "$KERNEL_ROOT"
    set -euo pipefail

    python3 - <<'PY'
from pathlib import Path
import re

def ensure_line_after_any(text, anchors, line, error_message):
    if line in text:
        return text
    for anchor in anchors:
        if anchor in text:
            return text.replace(anchor, anchor + line, 1)
    raise SystemExit(error_message)

def ensure_include(text, include, anchor):
    if include in text:
        return text
    if anchor in text:
        return text.replace(anchor, anchor + include + "\n", 1)
    return include + "\n" + text

def normalize_official_selinux_hide_exports(text):
    text = re.sub(
        r"(?m)^\s*(?:static\s+|__maybe_static\s+)*struct\s+selinux_state\s+fake_state\s*;",
        "struct selinux_state fake_state;",
        text,
    )
    text = re.sub(
        r"(?m)^\s*(?:static\s+|__maybe_static\s+)*bool\s+ksu_selinux_hide_enabled\s+__read_mostly\s*=\s*false\s*;",
        "bool ksu_selinux_hide_enabled __read_mostly = false;",
        text,
    )
    text = re.sub(
        r"(?m)^\s*(?:static\s+|__maybe_static\s+)*bool\s+ksu_selinux_hide_running\s+__read_mostly\s*=\s*false\s*;",
        "bool ksu_selinux_hide_running __read_mostly = false;",
        text,
    )
    text = re.sub(
        r"(?m)^\s*(?:static\s+|__maybe_static\s+)*DEFINE_STATIC_KEY_FALSE\s*\(\s*fake_status_initialize_key\s*\)\s*;",
        "DEFINE_STATIC_KEY_FALSE(fake_status_initialize_key);",
        text,
    )
    text = re.sub(
        r"(?m)^\s*(?:static\s+|__maybe_static\s+)*struct\s+page\s*\*\s*fake_status\s*(?:=\s*NULL)?\s*;",
        "struct page *fake_status = NULL;",
        text,
    )
    text = re.sub(
        r"(?m)^\s*(?:static\s+|__maybe_static\s+)*void\s+initialize_fake_status\s*\(\s*(?:void)?\s*\)",
        "void initialize_fake_status(void)",
        text,
    )
    return text

def ensure_official_selinux_hide_exports(text, path):
    text = normalize_official_selinux_hide_exports(text)

    if not re.search(r"(?m)^\s*bool\s+ksu_selinux_hide_enabled\s+__read_mostly\s*=\s*false\s*;", text):
        text = ensure_line_after_any(
            text,
            ("static DEFINE_MUTEX(selinux_hide_mutex);\n", "DEFINE_MUTEX(selinux_hide_mutex);\n"),
            "bool ksu_selinux_hide_enabled __read_mostly = false;\n",
            f"{path} 缺少 selinux_hide_mutex 锚点，无法插入 ksu_selinux_hide_enabled",
        )
    if not re.search(r"(?m)^\s*bool\s+ksu_selinux_hide_running\s+__read_mostly\s*=\s*false\s*;", text):
        text = ensure_line_after_any(
            text,
            ("bool ksu_selinux_hide_enabled __read_mostly = false;\n",),
            "bool ksu_selinux_hide_running __read_mostly = false;\n",
            f"{path} 缺少 ksu_selinux_hide_enabled 锚点，无法插入 ksu_selinux_hide_running",
        )
    if not re.search(r"(?m)^\s*struct\s+selinux_state\s+fake_state\s*;", text):
        text = ensure_line_after_any(
            text,
            ("bool ksu_selinux_hide_running __read_mostly = false;\n",),
            "struct selinux_state fake_state;\n",
            f"{path} 缺少 ksu_selinux_hide_running 锚点，无法插入 fake_state",
        )

    fake_status_additions = []
    if not re.search(r"(?m)^\s*DEFINE_STATIC_KEY_FALSE\s*\(\s*fake_status_initialize_key\s*\)\s*;", text):
        fake_status_additions.append("DEFINE_STATIC_KEY_FALSE(fake_status_initialize_key);")
    if not re.search(r"(?m)^\s*struct\s+page\s*\*\s*fake_status\s*=\s*NULL\s*;", text):
        fake_status_additions.append("struct page *fake_status = NULL;")
    if not re.search(r"(?m)^\s*void\s+initialize_fake_status\s*\(\s*(?:void)?\s*\)\s*\{", text):
        fake_status_additions.extend([
            "void initialize_fake_status(void)",
            "{",
            "}",
        ])
    if fake_status_additions:
        if "#include <linux/jump_label.h>" not in text:
            text = ensure_include(text, "#include <linux/jump_label.h>", "#include <linux/mutex.h>\n")
        block = "\n".join([
            "",
            "#ifdef CONFIG_KSU_SUSFS",
            *fake_status_additions,
            "#endif",
            "",
        ])
        text = ensure_line_after_any(
            text,
            ("bool ksu_selinux_hide_running __read_mostly = false;\n",),
            block,
            f"{path} 缺少 fake status 插入锚点",
        )

    return text

def validate_official_selinux_hide_exports(text, path, scope):
    required_patterns = {
        "ksu_selinux_hide_enabled": r"(?m)^\s*bool\s+ksu_selinux_hide_enabled\s+__read_mostly\s*=\s*false\s*;",
        "ksu_selinux_hide_running": r"(?m)^\s*bool\s+ksu_selinux_hide_running\s+__read_mostly\s*=\s*false\s*;",
        "fake_state": r"(?m)^\s*struct\s+selinux_state\s+fake_state\s*;",
        "fake_status_initialize_key": r"(?m)^\s*DEFINE_STATIC_KEY_FALSE\s*\(\s*fake_status_initialize_key\s*\)\s*;",
        "fake_status": r"(?m)^\s*struct\s+page\s*\*\s*fake_status\s*=\s*NULL\s*;",
        "initialize_fake_status": r"(?m)^\s*void\s+initialize_fake_status\s*\(\s*(?:void)?\s*\)\s*\{",
    }
    missing = [name for name, pattern in required_patterns.items() if not re.search(pattern, text)]
    if missing:
        raise SystemExit(f"{path} 缺少 {scope}: {missing}")

    forbidden_patterns = {
        "ksu_selinux_hide_enabled": r"(?m)^\s*(?:static|__maybe_static)\s+bool\s+ksu_selinux_hide_enabled\s+__read_mostly\s*=\s*false\s*;",
        "ksu_selinux_hide_running": r"(?m)^\s*(?:static|__maybe_static)\s+bool\s+ksu_selinux_hide_running\s+__read_mostly\s*=\s*false\s*;",
        "fake_state": r"(?m)^\s*(?:static|__maybe_static)\s+struct\s+selinux_state\s+fake_state\s*;",
        "fake_status_initialize_key": r"(?m)^\s*(?:static|__maybe_static)\s+DEFINE_STATIC_KEY_FALSE\s*\(\s*fake_status_initialize_key\s*\)\s*;",
        "fake_status": r"(?m)^\s*(?:static|__maybe_static)\s+struct\s+page\s*\*\s*fake_status\s*(?:=\s*NULL)?\s*;",
        "initialize_fake_status": r"(?m)^\s*(?:static|__maybe_static)\s+void\s+initialize_fake_status\s*\(",
    }
    stale = [name for name, pattern in forbidden_patterns.items() if re.search(pattern, text)]
    if stale:
        raise SystemExit(f"{path} 仍残留 static {scope}: {stale}")

    running_match = re.search(
        r"(?m)^\s*bool\s+ksu_selinux_hide_running\s+__read_mostly\s*=\s*false\s*;",
        text,
    )
    if not running_match:
        raise SystemExit(f"{path} 缺少 {scope} 锚点: ksu_selinux_hide_running")

    for name, pattern in (
        ("fake_status_initialize_key", r"(?m)^\s*DEFINE_STATIC_KEY_FALSE\s*\(\s*fake_status_initialize_key\s*\)\s*;"),
        ("fake_status", r"(?m)^\s*struct\s+page\s*\*\s*fake_status\s*=\s*NULL\s*;"),
        ("initialize_fake_status", r"(?m)^\s*void\s+initialize_fake_status\s*\(\s*(?:void)?\s*\)\s*\{"),
    ):
        match = re.search(pattern, text)
        if not match:
            raise SystemExit(f"{path} 缺少 {scope}: {name}")
        if match.start() < running_match.start():
            raise SystemExit(f"{path} 的 {name} 仍位于错误的预处理分支中")

path = Path("common/drivers/kernelsu/feature/selinux_hide.c")
if not path.exists():
    raise SystemExit(f"未找到 {path}")

text = path.read_text()
original = text

text = ensure_official_selinux_hide_exports(text, path)
validate_official_selinux_hide_exports(text, path, "Official 6.1-6.18 SELinux 导出")

if text != original:
    path.write_text(text)
    print(f"已修复 {path} 的 Official 6.1-6.18 SELinux 导出")
else:
    print(f"{path} 的 Official 6.1-6.18 SELinux 导出已满足要求")
PY

}

# ---- 补齐 SukiSU SUSFS 内联 hook 符号 ----
inline-hooks() {
    cd "$KERNEL_ROOT"
    set -euo pipefail
    python3 "$GITHUB_WORKSPACE/.github/scripts/fix_sukisu_susfs.py" "$KERNEL_ROOT"

}

# ---- 最终修复 SukiSU/BakaSU 源码兼容 ----
final() {
    cd "$KERNEL_ROOT"
    set -euo pipefail

    python3 - <<'PY'
from pathlib import Path
import re

OPEN_DIRECTIVE = re.compile(r"^\s*#\s*(if|ifdef|ifndef)\b")
ENDIF_DIRECTIVE = re.compile(r"^\s*#\s*endif\b")

def directive_delta(line):
    if OPEN_DIRECTIVE.match(line):
        return 1
    if ENDIF_DIRECTIVE.match(line):
        return -1
    return 0

def preprocessor_delta(text):
    depth = 0
    for line in text.splitlines():
        depth += directive_delta(line)
    return depth

def drop_unmatched_endifs(text):
    depth = 0
    output = []
    removed = []
    for number, line in enumerate(text.splitlines(keepends=True), 1):
        delta = directive_delta(line)
        if delta > 0:
            depth += 1
            output.append(line)
        elif delta < 0:
            if depth == 0:
                removed.append(number)
                continue
            depth -= 1
            output.append(line)
        else:
            output.append(line)
    return "".join(output), removed, depth

def repair_sucompat(path):
    text = path.read_text()
    changed = False
    marker = "\n// dead code\n"
    guard = "#endif /* CONFIG_KSU_SUSFS */\n"

    if marker in text and guard not in text:
        before_marker = text.split(marker, 1)[0]
        if preprocessor_delta(before_marker) > 0:
            text = text.replace(marker, "\n" + guard + marker, 1)
            changed = True
            print(f"  已补齐 {path} 的 CONFIG_KSU_SUSFS 结束符")

    repaired, removed, remaining_depth = drop_unmatched_endifs(text)
    if removed:
        changed = True
        print(f"  已移除 {path} 中无匹配 #if 的 #endif 行: {', '.join(map(str, removed))}")

    if remaining_depth != 0:
        raise SystemExit(f"{path} 仍存在未闭合的 #if/#ifdef/#ifndef: depth={remaining_depth}")

    if changed:
        path.write_text(repaired)

    return changed

def ensure_post_execveat_wrapper(path):
    text = path.read_text()
    guard = """    /* ABK: never inject an anonymous driver fd into ordinary execs. */
    if (!retval || *retval < 0 || !filename_ptr || !*filename_ptr ||
        IS_ERR(*filename_ptr) || strcmp((*filename_ptr)->name, KSUD_PATH))
        return 0;
"""
    if "int ksu_handle_post_execveat_sucompat(" in text:
        old = "    if (*retval >= 0)\n        (void)ksu_install_su_fd();"
        if "ABK: never inject an anonymous driver fd" not in text and old in text:
            path.write_text(text.replace(old, guard + old, 1))
            print(f"  已修复 {path} 的 post-exec su-session fd 范围")
            return True
        return False
    marker = "\n// sucompat: permitted process can execute 'su' to gain root access."
    if marker not in text:
        return False
    if '#include "supercall/supercall.h"' not in text:
        include_anchor = '#include "ksu.h" // IWYU pragma: keep\n'
        if include_anchor not in text:
            include_anchor = '#include "ksu.h"\n'
        if include_anchor not in text:
            raise SystemExit(f"无法定位 {path} 的 supercall.h include 插入点")
        text = text.replace(include_anchor, include_anchor + '#include "supercall/supercall.h"\n', 1)
    block = """

#ifdef CONFIG_KSU_SUSFS
int ksu_handle_post_execveat_sucompat(int *fd, struct filename **filename_ptr,
                                      void *argv_user, void *envp_user,
                                      int *flags, int *retval)
{
    /* ABK: never inject an anonymous driver fd into ordinary execs. */
    if (!retval || *retval < 0 || !filename_ptr || !*filename_ptr ||
        IS_ERR(*filename_ptr) || strcmp((*filename_ptr)->name, KSUD_PATH))
        return 0;
    if (*retval >= 0)
        (void)ksu_install_su_fd();
    return 0;
}
#endif
"""
    path.write_text(text.replace(marker, block + marker, 1))
    print(f"  已补齐 {path} 的 SUSFS post-exec su-session fd wrapper")
    return True

candidates = []
for base in (Path("common/drivers/kernelsu"), Path("drivers/kernelsu"), Path("KernelSU/kernel")):
    if base.exists() and base not in candidates:
        candidates.append(base)

if not candidates:
    raise SystemExit("未找到 SukiSU 源码目录，无法执行最终兼容修复")

changed = False

for kernel_dir in candidates:
    print(f"检查 SukiSU 源码目录: {kernel_dir}")

    sucompat_c = kernel_dir / "feature/sucompat.c"
    if sucompat_c.exists():
        changed = repair_sucompat(sucompat_c) or changed
        if Path("common/fs/exec.c").is_file() and "ksu_handle_post_execveat_sucompat(" in Path("common/fs/exec.c").read_text():
            changed = ensure_post_execveat_wrapper(sucompat_c) or changed

    sulog_event_c = kernel_dir / "sulog/event.c"
    if sulog_event_c.exists():
        text = sulog_event_c.read_text()
        marker = "builtin SUSFS ksu_sulog_capture() expects argv_user as a pointer"
        new = "\n".join([
            "    /* ABK: builtin SUSFS ksu_sulog_capture() expects argv_user as a pointer. */",
            "#ifdef CONFIG_KSU_SUSFS",
            "    pending = ksu_sulog_capture(KSU_SULOG_EVENT_IOCTL_GRANT_ROOT, NULL, NULL, gfp);",
            "#else",
            "    pending = ksu_sulog_capture(KSU_SULOG_EVENT_IOCTL_GRANT_ROOT, NULL, USER_ARG_NULL, gfp);",
            "#endif",
        ])
        if marker not in text:
            for old in (
                "    pending = ksu_sulog_capture(KSU_SULOG_EVENT_IOCTL_GRANT_ROOT, NULL, USER_ARG_NULL, gfp);",
                "    pending = ksu_sulog_capture(KSU_SULOG_EVENT_IOCTL_GRANT_ROOT, NULL, &USER_ARG_NULL, gfp);",
            ):
                if old in text:
                    sulog_event_c.write_text(text.replace(old, new, 1))
                    changed = True
                    print(f"  已修复 {sulog_event_c} 的 grant_root argv 参数类型")
                    break

if not changed:
    print("SukiSU 源码兼容修复无需修改。")
PY

    KSU_BUILD_DIR=""
    for dir in common/drivers/kernelsu drivers/kernelsu KernelSU/kernel; do
      if [ -d "$dir" ]; then
        KSU_BUILD_DIR="$dir"
        break
      fi
    done

    if [ -z "$KSU_BUILD_DIR" ]; then
      echo "::error::未找到实际 SukiSU 编译目录"
      exit 1
    fi

    if [ -f "$KSU_BUILD_DIR/feature/sucompat.c" ]; then
      python3 - "$KSU_BUILD_DIR/feature/sucompat.c" <<'PY'
import sys
from pathlib import Path
import re

path = Path(sys.argv[1])
depth = 0
for number, line in enumerate(path.read_text().splitlines(), 1):
    if re.match(r"^\s*#\s*(if|ifdef|ifndef)\b", line):
        depth += 1
    elif re.match(r"^\s*#\s*endif\b", line):
        if depth == 0:
            raise SystemExit(f"{path} 第 {number} 行存在无匹配 #if 的 #endif")
        depth -= 1
if depth != 0:
    raise SystemExit(f"{path} 仍存在未闭合的 #if/#ifdef/#ifndef: depth={depth}")
PY
    fi

    if [ -f "$KSU_BUILD_DIR/sulog/event.c" ]; then
      if grep -qF 'struct user_arg_ptr *argv_user' "$KSU_BUILD_DIR/sulog/event.c" \
        && grep -Eq 'NULL, (&)?USER_ARG_NULL, gfp' "$KSU_BUILD_DIR/sulog/event.c" \
        && ! grep -qF 'builtin SUSFS ksu_sulog_capture() expects argv_user as a pointer' "$KSU_BUILD_DIR/sulog/event.c"; then
        echo "::error::SukiSU/BakaSU sulog/event.c 仍存在 argv 参数类型不兼容调用: $KSU_BUILD_DIR/sulog/event.c"
        exit 1
      fi
    fi

}

# ---- 修复 SukiSU/BakaSU android16-6.12 / android17-6.18 源码兼容 ----
a16-612() {
    cd "$KERNEL_ROOT"
    set -euo pipefail

    KSU_BUILD_DIR=""
    for dir in common/drivers/kernelsu drivers/kernelsu KernelSU/kernel; do
      if [ -d "$dir" ]; then
        KSU_BUILD_DIR="$dir"
        break
      fi
    done

    if [ -z "$KSU_BUILD_DIR" ]; then
      echo "::error::未找到实际 SukiSU/BakaSU 编译目录"
      exit 1
    fi

    python3 - "$KSU_BUILD_DIR" <<'PY'
import sys
from pathlib import Path

ksu_dir = Path(sys.argv[1])
changed = False

netlink_h = Path("common/include/linux/netlink.h")
netlink_text = netlink_h.read_text(errors="ignore") if netlink_h.exists() else ""

for rel in ("kpm/super_access.c", "extension/kpm/super_access.c"):
    super_access_c = ksu_dir / rel
    if not super_access_c.exists():
        continue
    text = super_access_c.read_text()
    original = text

    # 6.12+ 的 netlink_kernel_cfg 去掉了 cb_mutex，旧 pin 还在探测它
    if "cb_mutex" not in netlink_text:
        line = "DEFINE_MEMBER(netlink_kernel_cfg, cb_mutex)\n"
        if line in text:
            text = text.replace(line, "", 1)
            print("已移除 android16-6.12/android17-6.18 不存在的 netlink_kernel_cfg.cb_mutex 成员探测")

    mnt_seq = "DEFINE_MEMBER(mnt_namespace, seq)\n"
    guarded = "#if LINUX_VERSION_CODE < KERNEL_VERSION(6, 18, 0)\n" + mnt_seq
    if mnt_seq in text and guarded not in text:
        text = text.replace(
            mnt_seq,
            "#if LINUX_VERSION_CODE < KERNEL_VERSION(6, 18, 0)\n" + mnt_seq + "#endif\n",
            1,
        )
        print("已为 mnt_namespace.seq 补 6.18 门控（6.18 起内核已移除该成员）")

    if text != original:
        super_access_c.write_text(text)
        changed = True

lsm_hooks_h = Path("common/include/linux/lsm_hooks.h")
lsm_hook_c = ksu_dir / "hook/lsm_hook.c"
if lsm_hook_c.exists() and lsm_hooks_h.exists():
    lsm_hooks_text = lsm_hooks_h.read_text(errors="ignore")
    if "const struct lsm_id *lsmid" in lsm_hooks_text:
        text = lsm_hook_c.read_text()
        old_call = '    security_add_hooks(ksu_hooks, ARRAY_SIZE(ksu_hooks), "ksu");'
        if old_call in text and "ksu_lsmid" not in text:
            anchor = "\nvoid __init ksu_lsm_hook_init(void)\n"
            old_block = (
                "#if LINUX_VERSION_CODE >= KERNEL_VERSION(4, 11, 0)\n"
                f"{old_call}\n"
                "#else"
            )
            new_block = (
                "#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 12, 0)\n"
                "    security_add_hooks(ksu_hooks, ARRAY_SIZE(ksu_hooks), &ksu_lsmid);\n"
                "#elif LINUX_VERSION_CODE >= KERNEL_VERSION(4, 11, 0)\n"
                f"{old_call}\n"
                "#else"
            )
            lsmid_block = "\n".join([
                "",
                "/* ABK: android16-6.12/android17-6.18 security_add_hooks() expects struct lsm_id. */",
                "#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 12, 0)",
                "static const struct lsm_id ksu_lsmid = {",
                '    .name = "ksu",',
                "    .id = 0,",
                "};",
                "#endif",
                "",
            ])
            if anchor not in text:
                raise SystemExit(f"ksu_lsm_hook_init anchor not found: {lsm_hook_c}")
            if old_block not in text:
                raise SystemExit(f"security_add_hooks compatibility block not found: {lsm_hook_c}")
            text = text.replace(anchor, lsmid_block + anchor, 1)
            text = text.replace(old_block, new_block, 1)
            lsm_hook_c.write_text(text)
            changed = True
            print("已适配 android16-6.12/android17-6.18 security_add_hooks() 的 struct lsm_id 参数")

if not changed:
    print("SukiSU/BakaSU android16-6.12/android17-6.18 专项兼容修复无需修改。")
PY

}

case "${1:-}" in
    bakasu) bakasu ;;
    official) official ;;
    selinux-export) selinux-export ;;
    inline-hooks) inline-hooks ;;
    final) final ;;
    a16-612) a16-612 ;;
    *)
        echo "用法: $0 {bakasu|official|selinux-export|inline-hooks|final|a16-612}" >&2
        exit 2
        ;;
esac
