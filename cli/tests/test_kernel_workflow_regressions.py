import importlib.util
import json
import os
import re
import shlex
import shutil
import subprocess
import sys
import tempfile
import textwrap
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
WORKFLOW_PATH = ROOT / ".github" / "workflows" / "build.yml"
REF_SCRIPT_PATH = ROOT / ".github" / "scripts" / "resolve-ksu-ref.sh"
KSU_COMPAT_PATH = ROOT / ".github" / "scripts" / "ensure-ksu-compat.py"


class KernelWorkflowRegressionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = WORKFLOW_PATH.read_text(encoding="utf-8")
        cls.ref_script = REF_SCRIPT_PATH.read_text(encoding="utf-8")
        spec = importlib.util.spec_from_file_location("ensure_ksu_compat", KSU_COMPAT_PATH)
        cls.ksu_compat = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.ksu_compat)

    def test_sukisu_setup_accepts_resolved_bare_sha(self):
        block = self._step_run_block("添加 KernelSU")
        case = block.split('"SukiSU")', 1)[1].split('"BakaSU")', 1)[0]

        self.assertIn('requested_ref="$BRANCH"', case)
        self.assertNotIn('${BRANCH#-s }', case)
        self.assertIn('bash "$setup_script" "$requested_ref"', case)
        self.assertIn('requested_head="$(git -C KernelSU rev-parse', case)

    def test_development_refs_are_reachable_successful_main_builds(self):
        expected = {
            "OFFICIAL_DEV_REF": "08a3b087e49227c8a6731c5f1114998b5e25255b",
            "SUKISU_DEV_REF": "cf87e3f4ddd3f6e5464d85acf56aaa6950e70841",
            # Baka-SU/BakaSU ships no dev branch, so Dev collapses onto the same main HEAD as Stable.
            "BAKASU_DEV_REF": "9dbce02e511ea6b6305a238b84e456f6a92e1d0b",
        }
        for variable, sha in expected.items():
            with self.subTest(variable=variable):
                self.assertRegex(
                    self.ref_script,
                    rf'(?m)^{variable}="{sha}"$',
                )

    def test_resolved_sha_defaults_to_selected_variant_ref(self):
        self.assertIn(
            'emit_env "RESOLVED_KSU_SHA" "${RESOLVED_KSU_SHA:-$BRANCH}"',
            self.ref_script,
        )

    def test_susfs_compatibility_step_covers_sukisu_variants(self):
        step = self.workflow.split("- name: 确保 KernelSU SUSFS ABI 兼容", 1)[1].split("- name: 配置 SukiSU 管理器信息", 1)[0]
        self.assertIn(
            "if: (inputs.ksu_variant == 'SukiSU' || inputs.ksu_variant == 'BakaSU') && inputs.enable_susfs",
            step,
        )
        self.assertIn("custom-source-feature-env.sh", step)
        self.assertIn("ABK_FEATURE_ID: kernelsu", step)

    def test_sukisu_nested_supercall_gets_susfs_fd_compatibility(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            ksu = root / "KernelSU" / "kernel"
            supercall = ksu / "supercall"
            feature = ksu / "feature"
            supercall.mkdir(parents=True)
            feature.mkdir()
            (ksu / "Kbuild").write_text("obj-y += supercall/\n", encoding="utf-8")
            source = supercall / "supercall.c"
            header = supercall / "supercall.h"
            source.write_text(
                "int ksu_install_fd(void) { return 0; }\n"
                "void __init ksu_supercalls_init(void) {}\n",
                encoding="utf-8",
            )
            header.write_text("int ksu_install_fd(void);\n", encoding="utf-8")
            (feature / "kernel_umount.c").write_text(
                "int ksu_kernel_umount_enabled;\n"
                "\nstatic const struct ksu_feature_handler kernel_umount_handler = {};\n",
                encoding="utf-8",
            )

            self.ksu_compat.main(str(root))

            self.assertIn("int ksu_install_su_fd(void)", source.read_text(encoding="utf-8"))
            self.assertIn("int ksu_install_su_fd(void);", header.read_text(encoding="utf-8"))
            self.assertIn(
                "static int kernel_umount_feature_set(u64 value)",
                (feature / "kernel_umount.c").read_text(encoding="utf-8"),
            )

    def test_scoped_su_fd_compatibility_preserves_session_permission(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            ksu = Path(temp_dir)
            source = ksu / "supercall.c"
            source.write_text(
                "#define KSU_DRIVER_PERMISSION_SU_SESSION (1UL << 0)\n"
                "static int ksu_install_fd_with_permissions(unsigned int flags, unsigned long permissions) { return 0; }\n"
                "int ksu_install_fd(void) { return ksu_install_fd_with_permissions(O_CLOEXEC, 0); }\n"
                "void __init ksu_supercalls_init(void) {}\n",
                encoding="utf-8",
            )

            self.ksu_compat.ensure_su_fd(ksu)

            self.assertIn(
                "ksu_install_fd_with_permissions(O_CLOEXEC, KSU_DRIVER_PERMISSION_SU_SESSION)",
                source.read_text(encoding="utf-8"),
            )

    def test_android12_ntsync_compat_filters_incompatible_lockdep_hunk(self):
        block = self._step_run_block("应用 NTsync 补丁")
        self.assertIn("apply_android12_ntsync_compat", block)
        self.assertIn('source.find("@@ -305,25 +310,29")', block)
        self.assertIn('source.find("@@ -5308,13 +5309,13")', block)
        self.assertIn("#define lockdep_assert(cond)", block)
        self.assertIn("return LOCK_STATE_HELD;", block)
        self.assertIn(
            'if [[ "$ABK_ANDROID_VERSION" == "android12" && "$ABK_KERNEL_VERSION" == "5.10" ]]',
            block,
        )

    def test_android12_statfs_repair_injects_verified_declaration(self):
        block = self._step_run_block("应用 SUSFS 补丁")
        self.assertIn(
            'declaration = "extern int susfs_sus_kstat_spoof_vfs_statfs(struct inode *inode, '
            'struct kstatfs *buf, bool *is_fuse);"',
            block,
        )
        self.assertIn("declared > call", block)
        self.assertIn('#include <linux/security.h>', block)
        self.assertIn('security_sb_statfs(', block)
        self.assertNotIn(
            "android12-5.10 Official fs/statfs.c 缺少 susfs_def.h",
            block,
        )
        statfs_repair = block.split("# Android 12/5.10 的上游补丁", 1)[1].split("# Android 13 - 5.15 修复", 1)[0]
        self.assertNotIn('[[ "$ABK_KSU_VARIANT" == "Official" ]]', statfs_repair)
        self.assertIn("fix_fdinfo_declarations", block)
        self.assertIn('declarations.append("\\tstruct mount *mnt;\\n")', block)

    def test_sukisu_post_exec_wrapper_installs_su_session_fd(self):
        block = self._step_run_block("最终修复 SukiSU/BakaSU 源码兼容")
        self.assertIn("ensure_post_execveat_wrapper", block)
        self.assertIn('#include "supercall/supercall.h"', block)
        self.assertIn("int ksu_handle_post_execveat_sucompat(", block)
        self.assertIn("(void)ksu_install_su_fd();", block)

    def test_official_post_exec_wrapper_is_preserved_after_rewrite(self):
        block = self._step_run_block("修复 Official SUSFS 源码兼容")
        self.assertIn('int ksu_handle_post_execveat_sucompat(', block)
        self.assertIn('(void)ksu_install_su_fd();', block)
        self.assertIn('#include "supercall/supercall.h"', block)

    def test_susfs_common_file_fallbacks_cover_upstream_api_renames(self):
        block = self._step_run_block("应用 SUSFS 补丁")
        self.assertIn('mnt_userns', block)
        self.assertIn('text.replace("mnt_userns", "idmap")', block)
        self.assertIn('ensure_susfs_super_compat', block)
        self.assertIn('DEFAULT_KSU_MNT_MINOR_DEV', block)
        self.assertIn('susfs_get_non_sus_mnt_id_unique_from_mnt', block)

    def test_stat_api_rewrite_is_gated_to_native_idmap_kernels(self):
        block = self._step_run_block("应用 SUSFS 补丁")
        self.assertIn("native_new_api = \"struct mnt_idmap\" in text", block)
        self.assertIn(
            'if "mnt_userns" in text and native_new_api:',
            block,
        )

    def test_namespace_tail_repair_closes_truncated_susfs_hunk(self):
        block = self._step_run_block("应用 SUSFS 补丁")
        self.assertIn("ensure_susfs_namespace_tail", block)
        self.assertIn(
            'if (!mnt->mnt.mnt_root || IS_ERR(mnt->mnt.mnt_root)) {',
            block,
        )
        self.assertIn("#endif // #ifdef CONFIG_KSU_SUSFS", block)

    def test_android16_uses_native_ntsync_source(self):
        block = self._step_run_block("应用 NTsync 补丁")
        self.assertIn('if [[ "$ABK_ANDROID_VERSION" != "android16"', block)
        self.assertIn('NTsync 基础源码已由 android16-6.12 内核提供', block)

    def test_android14_builtin_ntsync_does_not_require_symbol_export(self):
        block = self._step_run_block("应用 NTsync 补丁")
        self.assertNotIn("EXPORT_SYMBOL", block)
        self.assertNotIn("拒绝启用 CONFIG_NTSYNC", block)
        self.assertIn("ensure_defconfig_value CONFIG_NTSYNC y", block)

    @unittest.skipUnless(
        sys.platform.startswith("linux") and all(shutil.which(tool) for tool in ("bash", "diff", "grep", "sed", "strings")),
        "Linux kernel build command requires GNU/Linux tools",
    )
    def test_bazel_disk_cache_uses_runner_environment_and_survives_retries(self):
        step = re.search(
            r"(?ms)^      - name: 编译内核\n(?P<body>.*?)(?=^      - name: |\Z)",
            self.workflow,
        )
        self.assertIsNotNone(step, "kernel build step not found")
        command = re.search(r"(?ms)^          command: \|\n(?P<body>.*)\Z", step.group("body"))
        self.assertIsNotNone(command, "retry action command not found")
        build_script = textwrap.dedent(command.group("body"))
        inputs = {"android_version": "android15", "kernel_version": "6.6", "sub_level": "77", "ksu_variant": "SukiSU"}
        for name, value in inputs.items():
            build_script = build_script.replace("${{ inputs." + name + " }}", value)
        self.assertNotIn("${{", build_script, "build fixture contains unresolved workflow inputs")

        for use_xdg in (False, True):
            with self.subTest(xdg_cache_home=use_xdg), tempfile.TemporaryDirectory() as temp:
                root = Path(temp)
                home = root / "runner home with spaces"
                home.mkdir()
                cache_base = root / "xdg cache with spaces" if use_xdg else home / ".cache"
                disk_cache = cache_base / "bazel-disk"
                kernel = root / "kernel tree with spaces"
                config = kernel / "common/arch/arm64/configs/gki_defconfig"
                config.parent.mkdir(parents=True)
                original_config = "CONFIG_64BIT=y\n"
                Path(str(config) + ".orig").write_text(original_config, encoding="utf-8")
                (kernel / "common/build.config.gki.aarch64").write_text(
                    "BUILD_SYSTEM_DLKM=1\nMODULES_ORDER=android/gki_aarch64_modules\nKMI_SYMBOL_LIST_STRICT_MODE=1\n",
                    encoding="utf-8",
                )
                tools = kernel / "tools"
                tools.mkdir()
                argv_log = root / "bazel argv.jsonl"
                stub = root / "bazel stub.py"
                stub.write_text(textwrap.dedent("""\
                    import json
                    import os
                    import sys
                    from pathlib import Path

                    args = sys.argv[1:]
                    with open(os.environ["BAZEL_ARGV_LOG"], "a", encoding="utf-8") as log:
                        log.write(json.dumps(args) + "\\n")
                    caches = [arg.split("=", 1)[1] for arg in args if arg.startswith("--disk_cache=")]
                    if caches != [os.environ["EXPECTED_BAZEL_CACHE"]]:
                        raise SystemExit("disk cache must belong to the runner environment")
                    if not Path(caches[0]).is_dir():
                        raise SystemExit("disk cache must exist before starting Bazel")
                    # A real write checks permissions, instead of trusting os.access.
                    (Path(caches[0]) / "bazel-write-check").write_text("writable", encoding="utf-8")
                    image = Path("bazel-bin/common/kernel_aarch64/Image")
                    image.parent.mkdir(parents=True, exist_ok=True)
                    image.write_bytes(b"Linux version 6.6.77-test\\n")
                    """), encoding="utf-8")
                bazel = tools / "bazel"
                bazel.write_text(
                    f"#!/bin/sh\nexec {shlex.quote(sys.executable)} {shlex.quote(str(stub))} \"$@\"\n",
                    encoding="utf-8",
                )
                bazel.chmod(0o755)
                script = root / "build.sh"
                script.write_text(build_script, encoding="utf-8", newline="\n")
                env = dict(os.environ)
                for name in ("BASH_ENV", "ENV", "XDG_CACHE_HOME"):
                    env.pop(name, None)
                env.update(
                    HOME=str(home), KERNEL_ROOT=str(kernel), DEFCONFIG=str(config),
                    KSU_LATEST_COMMIT_DATE="fixture", SUSFS_LATEST_COMMIT_DATE="fixture",
                    BAZEL_ARGV_LOG=str(argv_log), EXPECTED_BAZEL_CACHE=str(disk_cache),
                )
                if use_xdg:
                    env["XDG_CACHE_HOME"] = str(cache_base)
                expected_args = [
                    "build", f"--disk_cache={disk_cache}", "--config=fast", "--lto=thin",
                    "--defconfig_fragment=//common:arch/arm64/configs/ksu.fragment",
                    "//common:kernel_aarch64_dist",
                ]
                for attempt in range(2):
                    config.write_text(original_config + "CONFIG_KSU=y\n", encoding="utf-8")
                    result = subprocess.run(
                        [shutil.which("bash"), str(script)], env=env,
                        capture_output=True, text=True, encoding="utf-8",
                    )
                    self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
                    calls = [json.loads(line) for line in argv_log.read_text(encoding="utf-8").splitlines()]
                    self.assertEqual(calls, [expected_args] * (attempt + 1))
                    self.assertEqual(config.read_text(encoding="utf-8"), original_config)
                    self.assertEqual(
                        (kernel / "common/arch/arm64/configs/ksu.fragment").read_text(encoding="utf-8"),
                        "CONFIG_KSU=y\n",
                    )
                    self.assertEqual((disk_cache / "bazel-write-check").read_text(encoding="utf-8"), "writable")
                    sentinel = disk_cache / "retained-cache-entry"
                    if attempt == 0:
                        sentinel.write_text("reuse this entry", encoding="utf-8")
                    else:
                        self.assertEqual(sentinel.read_text(encoding="utf-8"), "reuse this entry")
                if use_xdg:
                    self.assertFalse((home / ".cache/bazel-disk").exists())

    def _step_run_block(self, name):
        match = re.search(
            rf"(?ms)^      - name: {re.escape(name)}\n.*?^        run: \|\n"
            rf"(?P<body>.*?)(?=^      - name: |^    [A-Za-z0-9_-]+:|\Z)",
            self.workflow,
        )
        self.assertIsNotNone(match, f"step not found: {name}")
        return match.group("body")


if __name__ == "__main__":
    unittest.main()
