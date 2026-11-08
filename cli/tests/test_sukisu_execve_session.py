"""Exercise the generated SUSFS exec hooks through their real C return values."""
import ast
import importlib.util
import os
import re
import shutil
import subprocess
import tempfile
import textwrap
import time
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / ".github/scripts/fix_sukisu_susfs.py"
WORKFLOW = ROOT / ".github/workflows/build.yml"


def c_function(text, name):
    start = re.search(rf"(?m)^int {name}\(", text).start()
    opening = text.index("{", start)
    depth = 1
    for end in range(opening + 1, len(text)):
        depth += (text[end] == "{") - (text[end] == "}")
        if depth == 0:
            return text[start:end + 1]
    raise AssertionError(f"unclosed function: {name}")


@unittest.skipUnless(shutil.which("cc"), "exec-hook regression tests require a C compiler")
class SukiSUExecSessionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        spec = importlib.util.spec_from_file_location("fix_sukisu_susfs", SCRIPT)
        cls.patcher = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.patcher)
        workflow = WORKFLOW.read_text(encoding="utf-8")
        step = workflow.split("- name: 最终修复 SukiSU/BakaSU 源码兼容", 1)[1]
        code = textwrap.dedent(step.split("python3 - <<'PY'\n", 1)[1].split("\n          PY", 1)[0])
        functions = [node for node in ast.parse(code).body if isinstance(node, ast.FunctionDef)]
        # Workflow progress messages are not test output; Windows CI may use
        # a console encoding that cannot represent the Chinese messages.
        namespace = {"Path": Path, "re": re, "print": lambda *args, **kwargs: None}
        exec(compile(ast.Module(body=functions, type_ignores=[]), str(WORKFLOW), "exec"), namespace)
        cls.ensure_post = staticmethod(namespace["ensure_post_execveat_wrapper"])

    def generated_source(self, path, modern=True):
        if modern:
            handlers = 'long ksu_handle_stat_sucompat(int orig_nr, struct pt_regs *regs) { return 0; }\n'
        else:
            handlers = (
                '\nint ksu_handle_faccessat(int *dfd, const char __user **filename_user, int *mode, int *flags)\n{ return 0; }\n'
                '\nint ksu_handle_stat(int *dfd, const char __user **filename_user, int *flags)\n{\n    return 0;\n}\n\n'
                'long ksu_handle_execve_sucompat(void) { return 0; }\n'
            )
        path.write_text(
            '#include <linux/compiler_types.h>\n#include "ksu.h"\n'
            + handlers + "\n// sucompat: permitted process can execute 'su' to gain root access.\n",
            encoding="utf-8",
        )
        self.patcher.patch_sucompat_c(path, [])
        self.ensure_post(path)
        return path.read_text(encoding="utf-8")

    def run_hooks(self, source, directory):
        hooks = "\n".join(c_function(source, name) for name in (
            "ksu_handle_execveat_sucompat", "ksu_handle_execveat", "ksu_handle_post_execveat_sucompat",
        ))
        # The caller matches common/fs/exec.c: only a zero hook return marks
        # a su session, and the descriptor is installed after successful exec.
        harness = r'''
#include <assert.h>
#include <errno.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#define __user
#define GFP_KERNEL 0
#define unlikely(x) (x)
#define likely(x) (x)
#define IS_ERR(x) ((uintptr_t)(x) >= (uintptr_t)-4095)
#define KSUD_PATH "/data/adb/ksud"
#define SU_PATH "/system/bin/su"
#define pr_info(...) ((void)0)
#define pr_err(...) ((void)0)
struct filename { const char *name; };
struct ksu_sulog_pending_event {};
struct uid { int val; };
static const char su_path[] = "/system/bin/su";
static bool allowed;
static int profile_result, fd_count;
static struct uid current_uid(void) { return (struct uid){0}; }
static bool ksu_is_allow_uid_for_current(int uid) { return allowed; }
static int escape_with_root_profile(void) { return profile_result; }
static struct ksu_sulog_pending_event *ksu_sulog_capture_sucompat(const char *name, const char *const *argv, int gfp) { return NULL; }
static void ksu_sulog_emit_pending(struct ksu_sulog_pending_event *event, int ret, int gfp) {}
static int ksu_install_su_fd(void) { fd_count++; return 3; }
static void ksu_handle_execveat_ksud(const char *name, void *argv) {}
'''
        harness += hooks + r'''
static void check(const char *path, bool permitted, int profile_ret, int exec_ret, bool early_boot, int expected) {
    char name[128];
    strcpy(name, path);
    struct filename filename = {name}, *ptr = &filename;
    int fd = -100, flags = 0;
    allowed = permitted;
    profile_result = profile_ret;
    fd_count = 0;
    bool is_su_session = !(early_boot ? ksu_handle_execveat(&fd, &ptr, NULL, NULL, &flags)
        : ksu_handle_execveat_sucompat(&fd, &ptr, NULL, NULL, &flags));
    assert(is_su_session == (!strcmp(path, su_path) && permitted && profile_ret == 0));
    if (is_su_session)
        ksu_handle_post_execveat_sucompat(&fd, &ptr, NULL, NULL, &flags, &exec_ret);
    if (fd_count != expected) {
        fprintf(stderr, "%s: got %d unexpected driver FDs, expected %d\n", path, fd_count, expected);
        return;
    }
    return;
}
int main(void) {
    for (int early = 0; early < 2; early++) {
        check("/system/bin/app_process64", true, 0, 0, early, 0);
        assert(fd_count == 0);
        check("/system/bin/init", true, 0, 0, early, 0);
        assert(fd_count == 0);
        check("/system/bin/su", false, 0, 0, early, 0);
        assert(fd_count == 0);
        check("/system/bin/su", true, -EPERM, 0, early, 0);
        assert(fd_count == 0);
        check("/system/bin/su", true, 0, -ENOENT, early, 0);
        assert(fd_count == 0);
        check("/system/bin/su", true, 0, 0, early, 1);
        assert(fd_count == 1);
    }
    /* Defense against a stale caller that still reports a false su session. */
    char name[] = "/system/bin/app_process64";
    struct filename filename = {name}, *ptr = &filename;
    int fd = -100, flags = 0, ret = 0;
    fd_count = 0;
    ksu_handle_post_execveat_sucompat(&fd, &ptr, NULL, NULL, &flags, &ret);
    assert(fd_count == 0);
    puts("normal exec, denied/failed su, successful su, and stale caller: passed");
    return 0;
}
'''
        c_path = directory / "hooks.c"
        binary = directory / ("hooks.exe" if os.name == "nt" else "hooks")
        c_path.write_text(harness, encoding="utf-8")
        compiled = subprocess.run([shutil.which("cc"), "-std=gnu11", str(c_path), "-o", str(binary)], capture_output=True, text=True, encoding="utf-8", errors="replace")
        self.assertEqual(compiled.returncode, 0, compiled.stderr)
        try:
            result = subprocess.run([str(binary)], capture_output=True, text=True, encoding="utf-8", errors="replace")
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        finally:
            # Windows runners can briefly retain a handle to the exited binary.
            # Retry that cleanup without suppressing persistent errors.
            for attempt in range(50):
                try:
                    binary.unlink(missing_ok=True)
                    break
                except PermissionError:
                    if os.name != "nt" or attempt == 49:
                        raise
                    time.sleep(0.1)

    def test_generated_hooks_do_not_inject_fd_into_zygote(self):
        for modern in (True, False):
            with self.subTest(modern=modern), tempfile.TemporaryDirectory() as temp:
                directory = Path(temp)
                self.run_hooks(self.generated_source(directory / "sucompat.c", modern), directory)

    def test_reapplying_fixes_repairs_old_generated_hooks(self):
        for modern in (True, False):
            with self.subTest(modern=modern), tempfile.TemporaryDirectory() as temp:
                directory = Path(temp)
                path = directory / "sucompat.c"
                source = self.generated_source(path, modern)
                pre = c_function(source, "ksu_handle_execveat_sucompat")
                old_pre = pre.replace("return 1;", "return 0;").replace("return ret;", "return 0;")
                post = c_function(source, "ksu_handle_post_execveat_sucompat")
                old_post = post[:post.index("{") + 1] + "\n    if (*retval >= 0)\n        (void)ksu_install_su_fd();\n    return 0;\n}"
                path.write_text(source.replace(pre, old_pre).replace(post, old_post), encoding="utf-8")
                self.patcher.patch_sucompat_c(path, [])
                self.ensure_post(path)
                repaired = path.read_text(encoding="utf-8")
                self.run_hooks(repaired, directory)
                self.patcher.patch_sucompat_c(path, [])
                self.ensure_post(path)
                self.assertEqual(path.read_text(encoding="utf-8"), repaired)


if __name__ == "__main__":
    unittest.main()
