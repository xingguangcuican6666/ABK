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
    @classmethod
    def setUpClass(cls):
        cls.workflow = (ROOT / ".github/workflows" / "build.yml").read_text(encoding="utf-8")

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
            workspace = root / "workspace"
            (workspace / ".github" / "scripts").mkdir(parents=True)
            (workspace / "config").mkdir()
            (workspace / ".github/scripts/cached-downloads.py").write_text("", encoding="utf-8")
            patches = root / "virtualization-support-patches"
            patches.mkdir()
            (patches / "stale").write_text("old checkout", encoding="utf-8")
            sentinel = root / "unrelated"
            sentinel.write_text("keep", encoding="utf-8")
            # The downloader and Git are simulated; deletion, ref resolution and
            # path handling execute unchanged.
            script = '''python3() {
  test "$1" = "$GITHUB_WORKSPACE/.github/scripts/cached-downloads.py"
  test "$2" = clone
  test "$3" = https://github.com/ravindu644/Droidspaces-OSS.git
  ref=""
  while (($# > 0)); do
    if [[ "$1" == "--ref" ]]; then ref="$2"; shift; fi
    shift
  done
  test -n "$ref"
  destination="$RUNNER_TEMP/virtualization-support-patches"
  test ! -e "$destination"
  mkdir -p "$destination/Documentation/resources/kernel-patches/GKI"
  printf '%s\\n' "$ref" > "$destination/requested-ref"
  printf 'fresh' > "$destination/source"
}
git() {
  case "$1" in
    -C)
      test -d "$2/Documentation/resources/kernel-patches/GKI"
      case "$3" in
        rev-parse) printf '0123456789abcdef0123456789abcdef01234567\\n' ;;
        *) return 1 ;;
      esac
      ;;
    *) return 1 ;;
  esac
}
''' + script
            env_file = root / "github-env"
            for ref, expected_ref in ((None, "v7.1.0"), ("v6.6.0", "v6.6.0")):
                (workspace / "config" / "config").write_text(
                    "custom=true\ngki-android14-6.1=abc\n" + (f"droidspaces={ref}\n" if ref else ""),
                    encoding="utf-8",
                )
                env_file.write_text("", encoding="utf-8")
                result = subprocess.run([shutil.which("bash"), "-eu", "-c", script],
                                        env={**os.environ, "RUNNER_TEMP": str(root),
                                             "GITHUB_ENV": str(env_file),
                                             "GITHUB_WORKSPACE": str(workspace),
                                             "ABK_DROIDSPACES_DEFAULT_REF": "v7.1.0"},
                                        capture_output=True, text=True, encoding="utf-8")
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertFalse((patches / "stale").exists())
                self.assertEqual((patches / "source").read_text(), "fresh")
                self.assertEqual(sentinel.read_text(), "keep")
                self.assertEqual(
                    (patches / "requested-ref").read_text().strip(), expected_ref)
                lines = env_file.read_text().splitlines()
                self.assertEqual(lines, [
                    f"VIRTUALIZATION_PATCHES_REF={expected_ref}",
                    "VIRTUALIZATION_PATCHES_SHA=0123456789abcdef0123456789abcdef01234567",
                    f"VIRTUALIZATION_SUPPORT_PATCHES={patches}/Documentation/resources/kernel-patches/GKI",
                ])

    def test_virtualization_ref_defaults_to_pinned_upstream_tag(self):
        """An empty config/config value must fall back to the pinned release tag."""
        script = step_script("build.yml", "克隆虚拟化支持补丁仓库")
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            workspace = root / "workspace"
            (workspace / ".github" / "scripts").mkdir(parents=True)
            (workspace / "config").mkdir()
            (workspace / ".github/scripts/cached-downloads.py").write_text("", encoding="utf-8")
            (workspace / "config" / "config").write_text(
                "# droidspaces=v6.0.0\ndroidspaces=\n", encoding="utf-8")
            script = '''python3() {
  while (($# > 0)); do
    if [[ "$1" == "--ref" ]]; then printf '%s\\n' "$2" > "$RUNNER_TEMP/ref.txt"; fi
    shift
  done
  mkdir -p "$RUNNER_TEMP/virtualization-support-patches/Documentation/resources/kernel-patches/GKI"
}
git() {
  case "$3" in
    rev-parse) printf 'cafef00d\\n' ;;
    describe) printf 'v7.1.0\\n' ;;
  esac
}
''' + script
            env_file = root / "github-env"
            result = subprocess.run([shutil.which("bash"), "-eu", "-c", script],
                                    env={**os.environ, "RUNNER_TEMP": str(root),
                                         "GITHUB_ENV": str(env_file),
                                         "GITHUB_WORKSPACE": str(workspace),
                                         "ABK_DROIDSPACES_DEFAULT_REF": "v7.1.0"},
                                    capture_output=True, text=True, encoding="utf-8")
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual((root / "ref.txt").read_text().strip(), "v7.1.0")
            self.assertIn("VIRTUALIZATION_PATCHES_REF=v7.1.0",
                          env_file.read_text())

    def test_virtualization_matches_upstream_gki_requirements(self):
        """The v7.1.0 GKI defconfig list must be covered, and its forbidden
        resource-limit options must stay off because no kABI patch covers them."""
        step = self.workflow.split("      - name: 集成虚拟化支持\n", 1)[1].split(
            "      - name: ", 1)[0]
        required = (
            "CONFIG_SYSVIPC", "CONFIG_POSIX_MQUEUE", "CONFIG_IPC_NS", "CONFIG_PID_NS",
            "CONFIG_DEVTMPFS", "CONFIG_USER_NS", "CONFIG_IP6_NF_NAT",
            "CONFIG_IP6_NF_TARGET_MASQUERADE", "CONFIG_MACVLAN",
            "CONFIG_NETFILTER_XT_MATCH_ADDRTYPE", "CONFIG_NETFILTER_XT_TARGET_LOG",
            "CONFIG_NETFILTER_XT_MATCH_RECENT", "CONFIG_IP_SET", "CONFIG_IP_SET_HASH_IP",
            "CONFIG_IP_SET_HASH_NET", "CONFIG_NETFILTER_XT_SET", "CONFIG_TMPFS_XATTR",
            "CONFIG_TMPFS_POSIX_ACL",
        )
        for symbol in required:
            with self.subTest(symbol=symbol):
                self.assertRegex(
                    step, rf"enable_(required_config|config_if_defined) {symbol}\b")
        for symbol in ("CONFIG_CFS_BANDWIDTH", "CONFIG_CGROUP_PIDS"):
            with self.subTest(symbol=symbol):
                self.assertNotRegex(
                    step, rf"enable_(required_config|config_if_defined) {symbol}\b")
                self.assertIn(symbol, step)

    def test_local_build_virtualization_matches_workflow_config_list(self):
        """desktop/assets/local_build/rebuild.sh and new_test/rebuild.sh stay in sync
        and enable the same upstream GKI options as build.yml."""
        copies = {
            path: (ROOT / path).read_text(encoding="utf-8")
            for path in ("desktop/assets/local_build/rebuild.sh", "new_test/rebuild.sh")
        }
        self.assertEqual(len(set(copies.values())), 1, "local build scripts diverged")
        rebuild = next(iter(copies.values()))
        function = rebuild.split("apply_virtualization_support() {", 1)[1].split(
            "\napply_zram_stack() {", 1)[0]
        step = self.workflow.split("      - name: 集成虚拟化支持\n", 1)[1].split(
            "      - name: ", 1)[0]
        for symbol in ("CONFIG_SYSVIPC", "CONFIG_POSIX_MQUEUE", "CONFIG_IPC_NS",
                       "CONFIG_PID_NS", "CONFIG_DEVTMPFS", "CONFIG_USER_NS",
                       "CONFIG_IP6_NF_NAT", "CONFIG_IP6_NF_TARGET_MASQUERADE",
                       "CONFIG_MACVLAN", "CONFIG_TMPFS_XATTR", "CONFIG_TMPFS_POSIX_ACL"):
            with self.subTest(symbol=symbol):
                self.assertIn(f"enable_virtualization_config {symbol}", function)
                self.assertIn(symbol, step)
        for symbol in ("CONFIG_CFS_BANDWIDTH", "CONFIG_CGROUP_PIDS"):
            with self.subTest(symbol=symbol):
                self.assertNotIn(f"enable_virtualization_config {symbol}", function)
                self.assertIn(symbol, function)
        # Every supported kernel line keeps its kABI patch, including 6.12/6.18 and 5.10.
        for marker in ("kernel-6.12/001.GKI-6.12-or-above-fix_sysvipc_kabi.patch",
                       "below-kernel-6.12/001.GKI-below-6.12-fix_sysvipc_kabi_",
                       "002.5.10_or_lower_use_android_abi_padding_for_posix_mqueue.patch"):
            with self.subTest(marker=marker):
                self.assertIn(marker, function)
        self.assertIn("droidspaces", function)
        self.assertIn("5.10|5.15|6.1|6.6|6.12|6.18", function)

    def test_virtualization_covers_android17_6_18(self):
        """build.yml routes 6.18 through the 6.12-or-above patch and its IPC_NS
        symbol exports, and the local build path supports the same line."""
        step = step_script("build.yml", "集成虚拟化支持")
        self.assertIn("6.12|6.18)", step)
        self.assertIn('if [ "$KERNEL_VER" = "6.12" ] || [ "$KERNEL_VER" = "6.18" ]; then', step)
        self.assertIn("EXPORT_SYMBOL(init_ipc_ns);", step)
        self.assertIn("EXPORT_SYMBOL(put_ipc_ns);", step)

        rebuild = (ROOT / "desktop/assets/local_build/rebuild.sh").read_text(encoding="utf-8")
        function = rebuild.split("apply_virtualization_support() {", 1)[1].split(
            "\napply_zram_stack() {", 1)[0]
        self.assertIn("5.10|5.15|6.1|6.6|6.12|6.18", function)
        self.assertEqual(function.count('== "6.12" || "$KERNEL_VERSION" == "6.18"'), 4)

    def test_droidspaces_pin_is_recorded_in_config(self):
        config = (ROOT / "config" / "config").read_text(encoding="utf-8")
        self.assertRegex(config, re.compile(r"^droidspaces=v\d+\.\d+\.\d+$", re.M))
        self.assertIn("CONFIG_CFS_BANDWIDTH", config)
        self.assertIn("CONFIG_MACVLAN", config)

    @unittest.skipUnless(shutil.which("python3"), "requires python3")
    def test_sysvipc_slot_fallback_honours_existing_kabi_occupancy(self):
        """The in-place SYSVIPC kABI fallback must work for any slot whose reserve
        block is still free, and must refuse a slot that ACK already occupies."""
        step = step_script("build.yml", "集成虚拟化支持")
        code = step.split("<<'PY'\n", 1)[1].split("\nPY", 1)[0]
        self.assertNotIn("${{", code, "fixture contains unresolved workflow inputs")

        def sched_header(occupied):
            uses = "".join(
                f"\tANDROID_KABI_USE({slot}, void *field_{slot});\n" for slot in occupied
            )
            reserves = "".join(f"\tANDROID_KABI_RESERVE({slot});\n" for slot in range(1, 9))
            return (
                "struct task_struct {\n"
                "#ifdef CONFIG_SYSVIPC\n"
                "\tstruct sysv_sem\t\t\tsysvsem;\n"
                "\tstruct sysv_shm\t\t\tsysvshm;\n"
                "#endif\n"
                "\tunsigned int\t\t\trt_priority;\n"
                f"{uses}{reserves}}};\n"
            )

        # task_struct KABI occupancy measured on the ACK trees: 5.10 uses slots 1
        # and 2, 5.15 uses 1, 6.1 and 6.12 use 1/2/3, 6.6 uses 1 and 2.
        for occupied in ("12", "1", "123", "12"):
            for slot in ("678", "123", "345"):
                taken = set(occupied) & set(slot)
                with self.subTest(occupied=occupied, slot=slot):
                    with tempfile.TemporaryDirectory() as temp:
                        header = Path(temp) / "sched.h"
                        header.write_text(sched_header(sorted(set(occupied))), encoding="utf-8")
                        result = subprocess.run(
                            [sys.executable, "-c", code],
                            env={**os.environ, "ABK_SYSVIPC_SLOT": slot,
                                 "ABK_SYSVIPC_SCHED": str(header)},
                            capture_output=True, text=True, encoding="utf-8")
                        if taken:
                            self.assertNotEqual(0, result.returncode)
                            self.assertIn("already taken", result.stderr)
                            # A refused slot must leave the header untouched.
                            self.assertEqual(header.read_text(encoding="utf-8"),
                                             sched_header(sorted(set(occupied))))
                            continue
                        self.assertEqual(0, result.returncode, result.stderr)
                        text = header.read_text(encoding="utf-8")
                        first, second, third = slot
                        self.assertIn(f"ANDROID_KABI_USE({first}, struct sysv_sem sysvsem);", text)
                        self.assertIn(
                            f"_ANDROID_KABI_REPLACE(ANDROID_KABI_RESERVE({second}); "
                            f"ANDROID_KABI_RESERVE({third}), struct sysv_shm sysvshm);", text)
                        self.assertIn("// struct sysv_sem", text)
                        self.assertIn(f"\tANDROID_KABI_RESERVE({first});", text)
                        for used in sorted(set(occupied)):
                            self.assertIn(f"ANDROID_KABI_USE({used}, void *field_{used});", text)

        # Slot validation rejects anything that is not three distinct reserves.
        for slot in ("", "67", "6789", "668", "078", "abc"):
            with self.subTest(slot=slot):
                with tempfile.TemporaryDirectory() as temp:
                    header = Path(temp) / "sched.h"
                    header.write_text(sched_header([]), encoding="utf-8")
                    result = subprocess.run(
                        [sys.executable, "-c", code],
                        env={**os.environ, "ABK_SYSVIPC_SLOT": slot,
                             "ABK_SYSVIPC_SCHED": str(header)},
                        capture_output=True, text=True, encoding="utf-8")
                    self.assertNotEqual(0, result.returncode)
                    self.assertIn("invalid slot", result.stderr)
