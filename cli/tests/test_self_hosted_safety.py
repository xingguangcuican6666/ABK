"""Exercise runner-sensitive shell snippets without modifying the host."""
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import textwrap
import unittest


ROOT = Path(__file__).resolve().parents[2]


def step_script(workflow, name):
    text = (ROOT / ".github/workflows" / workflow).read_text(encoding="utf-8")
    step = text.split(f"      - name: {name}\n", 1)[1].split("      - name: ", 1)[0]
    return textwrap.dedent(step.split("        run: |\n", 1)[1])


@unittest.skipUnless(os.name != "nt" and shutil.which("bash"), "requires POSIX Bash")
class SelfHostedSafetyTests(unittest.TestCase):
    @unittest.skipUnless(shutil.which("cc"), "requires a C compiler")
    def test_oneplus_fallback_fd_hook_only_accepts_successful_ksud_exec(self):
        script = step_script("oneplus-build.yml", "修复 SukiSU/BakaSU sulog 兼容")
        code = script.split('python3 - "$KSU_BUILD_DIR/feature/sucompat.c" <<\'PY\'\n', 1)[1].split("\nPY", 1)[0]
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            source = root / "sucompat.c"
            source.write_text('#include "ksu.h"\n\n// sucompat: permitted process can execute \'su\' to gain root access.\n')
            subprocess.run([sys.executable, "-c", code, str(source)], check=True, capture_output=True)
            generated = source.read_text()
            hook = generated.split("#ifdef CONFIG_KSU_SUSFS\n", 1)[1].split("\n#endif", 1)[0]
            harness = r'''
#include <assert.h>
#include <stdint.h>
#include <string.h>
#define KSUD_PATH "/data/adb/ksud"
#define IS_ERR(p) ((uintptr_t)(p) >= (uintptr_t)-4095)
struct filename { const char *name; };
static int count;
static int ksu_install_su_fd(void) { return ++count; }
'''
            harness += hook + r'''
int main(void) {
    struct filename f = {"/system/bin/app_process64"}, *ptr = &f;
    int fd=0, flags=0, retval=0;
    ksu_handle_post_execveat_sucompat(&fd,&ptr,0,0,&flags,&retval);
    assert(count==0);
    f.name=KSUD_PATH; retval=-1;
    ksu_handle_post_execveat_sucompat(&fd,&ptr,0,0,&flags,&retval);
    assert(count==0);
    retval=0;
    ksu_handle_post_execveat_sucompat(&fd,&ptr,0,0,&flags,&retval);
    assert(count==1);
    ksu_handle_post_execveat_sucompat(&fd,0,0,0,&flags,&retval);
    assert(count==1);
    return 0;
}
'''
            c_path = root / "hook.c"
            c_path.write_text(harness)
            binary = root / "hook"
            compiled = subprocess.run([shutil.which("cc"), str(c_path), "-o", str(binary)], capture_output=True)
            self.assertEqual(compiled.returncode, 0, compiled.stderr)
            subprocess.run([str(binary)], check=True, capture_output=True)
            native = 'int ksu_handle_post_execveat_sucompat(void) { return 99; }\n'
            source.write_text(native)
            subprocess.run([sys.executable, "-c", code, str(source)], check=True, capture_output=True)
            self.assertEqual(source.read_text(), native)

    def test_oneplus_mtk_disables_kpm_while_qualcomm_preserves_it(self):
        script = step_script("oneplus-build.yml", "构建信息摘要")
        for cpu in ("mt6991", "sm8750"):
            for variant in ("SukiSU", "BakaSU"):
                with self.subTest(cpu=cpu, variant=variant), tempfile.TemporaryDirectory() as temp:
                    output = Path(temp) / "env"
                    env = {**os.environ, "CPU": cpu, "DEVICE_MANIFEST": "test-device",
                           "KERNEL_VERSION": "6.6", "ANDROID_VERSION": "android15",
                           "KSU_VARIANT": variant, "USE_KPM": "true", "USE_LZ4KD": "false",
                           "ENABLE_SUSFS": "true", "USE_PROXY_OPTIMIZATION": "true",
                           "USE_UNICODE_BYPASS": "false", "USE_BBG": "false", "USE_BBR": "false",
                           "GITHUB_ENV": str(output)}
                    result = subprocess.run([shutil.which("bash"), "-eu", "-c", script], env=env,
                                            capture_output=True, text=True, encoding="utf-8")
                    self.assertEqual(result.returncode, 0, result.stderr)
                    values = dict(line.split("=", 1) for line in output.read_text().splitlines())
                    self.assertEqual(values["EFFECTIVE_KPM"], "false" if cpu.startswith("mt") else "true")

    def test_oneplus_detects_native_kpm_without_ripgrep(self):
        script = step_script("oneplus-build.yml", "配置 defconfig")
        script = 'kpm_config_supported="false"' + script.split('kpm_config_supported="false"', 1)[1]
        script = script.split('if [ "$EFFECTIVE_KPM"', 1)[0]
        script = 'rg() { return 127; };\n' + script + '\nprintf "%s" "$kpm_config_supported"\n'
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            kconfig = root / "KernelSU/kernel/Kconfig"
            kconfig.parent.mkdir(parents=True)
            for contents, expected in (("config KPM_EXTRA\n", "false"), ("config KPM\n", "true")):
                with self.subTest(contents=contents):
                    kconfig.write_text(contents)
                    env = {**os.environ, "KERNEL_ROOT": str(root), "KSU_VARIANT": "SukiSU",
                           "kpm_kconfig_ready": "false", "kpm_kconfig_path": ""}
                    result = subprocess.run([shutil.which("bash"), "-eu", "-c", script], env=env,
                                            capture_output=True, text=True, encoding="utf-8")
                    self.assertEqual(result.returncode, 0, result.stderr)
                    self.assertEqual(result.stdout, expected)

    def test_chrome_source_cleanup_only_runs_on_hosted_runners(self):
        for workflow in ("build.yml", "oneplus-build.yml"):
            text = (ROOT / ".github/workflows" / workflow).read_text(encoding="utf-8")
            function = re.search(r"(?ms)^          disable_broken_chrome_source\(\) \{.*?^          \}", text).group()
            function = textwrap.dedent(function)
            for environment in ("self-hosted", "github-hosted"):
                with self.subTest(workflow=workflow, environment=environment), tempfile.TemporaryDirectory() as temp:
                    trace = Path(temp) / "sudo.log"
                    script = '''sudo() {
  printf '%s\\n' "$*" >> "$REVIEW_TRACE"
  if [ "$1" = grep ]; then printf '/mock/chrome.list\\n'; fi
}
'''
                    script += function.replace("${{ runner.environment }}", environment)
                    script += "\ndisable_broken_chrome_source\n"
                    result = subprocess.run([shutil.which("bash"), "-eu", "-c", script],
                                            env={**os.environ, "REVIEW_TRACE": str(trace)},
                                            capture_output=True, text=True, encoding="utf-8")
                    self.assertEqual(result.returncode, 0, result.stderr)
                    if environment == "self-hosted":
                        self.assertFalse(trace.exists())
                    else:
                        calls = trace.read_text().splitlines()
                        self.assertEqual(len(calls), 2)
                        self.assertEqual(calls[-1], "rm -f /mock/chrome.list")

    def test_virtualization_checkout_replaces_stale_runner_temp_directory(self):
        script = step_script("build.yml", "克隆虚拟化支持补丁仓库")
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            patches = root / "virtualization-support-patches"
            patches.mkdir()
            (patches / "stale").write_text("old checkout", encoding="utf-8")
            sentinel = root / "unrelated"
            sentinel.write_text("keep", encoding="utf-8")
            # Git is simulated; deletion and path handling execute unchanged.
            script = '''git() {
  test "$1" = clone
  destination="${@: -1}"
  test ! -e "$destination"
  mkdir -p "$destination/Documentation/resources/kernel-patches/GKI"
  printf 'fresh' > "$destination/source"
}
''' + script
            env_file = root / "github-env"
            for _ in range(2):
                result = subprocess.run([shutil.which("bash"), "-eu", "-c", script],
                                        env={**os.environ, "RUNNER_TEMP": str(root), "GITHUB_ENV": str(env_file)},
                                        capture_output=True, text=True, encoding="utf-8")
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertFalse((patches / "stale").exists())
                self.assertEqual((patches / "source").read_text(), "fresh")
                self.assertEqual(sentinel.read_text(), "keep")
            expected = f"VIRTUALIZATION_SUPPORT_PATCHES={patches}/Documentation/resources/kernel-patches/GKI"
            self.assertEqual(env_file.read_text().splitlines(), [expected, expected])
