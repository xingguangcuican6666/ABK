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

from cli.tests import kernel_scripts


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

    def test_checkout_precedes_script_steps(self):
        # 被 workflow_call 复用的工作流不会隐式 checkout：任何调用
        # platform-tools/kernel_build 的步骤之前都必须先检出仓库，
        # 否则脚本不存在，步骤以 127 直接失败。
        starts = [m.start() for m in re.finditer(r"(?m)^      - name: ", self.workflow)]
        self.assertTrue(starts, "no steps found in build.yml")
        kinds = []
        for index, start in enumerate(starts):
            end = starts[index + 1] if index + 1 < len(starts) else len(self.workflow)
            kinds.append(self.workflow[start:end])

        positions = {}
        for index, block in enumerate(kinds):
            if re.search(r"(?m)^        uses: actions/checkout@", block):
                positions.setdefault("checkout", index)
            if "platform-tools/kernel_build/" in block:
                positions.setdefault("script", index)

        self.assertIn("checkout", positions, "build.yml lost its checkout step")
        self.assertIn("script", positions, "build.yml lost its kernel_build script steps")
        self.assertLess(
            positions["checkout"],
            positions["script"],
            "检出代码仓库 must run before the first platform-tools/kernel_build step",
        )

    def test_rejects_collection_calls_existing_script(self):
        step = kernel_scripts.step("收集补丁冲突文件", self.workflow)
        self.assertEqual("collect-rejects", step.subcommand)
        self.assertTrue(
            step.path.is_file(),
            f"collect-rejects step points at a missing script: {step.path}",
        )

    def test_nyasu_sdk_orchestrator_is_the_only_patcher_implementation(self):
        patcher = (ROOT / ".github" / "scripts" / "kernel-patcher.py").read_text(encoding="utf-8")
        self.assertIn('sdk / "ksuhook" / "orchestrator.py"', patcher)
        self.assertIn('os.environ.get("NYASU_SDK_DIR")', patcher)
        # 编排实现只在 NyaSU SDK 里：ABK 侧不得再出现自己的 worktree/分组逻辑。
        for removed in (
            "def apply_modules(",
            "def group_modules(",
            "def snapshot_base(",
            "def parse_module_spec(",
        ):
            self.assertNotIn(removed, patcher)

    def test_ksuhook_steps_run_in_the_right_order(self):
        starts = [m.start() for m in re.finditer(r"(?m)^      - name: ", self.workflow)]
        self.assertTrue(starts, "no steps found in build.yml")
        order = {}
        for index, start in enumerate(starts):
            end = starts[index + 1] if index + 1 < len(starts) else len(self.workflow)
            block = self.workflow[start:end]
            name = block.split("\n", 1)[0][len("      - name: "):].strip()
            order.setdefault(name, index)

        for required in ("检出 NyaSU SDK", "克隆 KernelSU 补丁模块", "应用 KernelSU 补丁模块", "编译内核"):
            self.assertIn(required, order, f"build.yml 缺少步骤: {required}")

        self.assertLess(order["检出 NyaSU SDK"], order["克隆 KernelSU 补丁模块"])
        self.assertLess(order["克隆 KernelSU 补丁模块"], order["应用 KernelSU 补丁模块"])
        self.assertLess(order["应用 KernelSU 补丁模块"], order["编译内核"])

    def test_ksuhook_inputs_are_declared_and_exported(self):
        self.assertIn("      patch_modules:", self.workflow)
        self.assertIn("      nyasu_sdk_ref:", self.workflow)
        self.assertIn("ABK_INPUT_PATCH_MODULES:", self.workflow)
        self.assertIn("ABK_INPUT_NYASU_SDK_REF:", self.workflow)

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
        # android16-6.12 与 android17-6.18 都自带原生 ntsync.c 与 UAPI，基础补丁不再应用。
        block = self._step_run_block("应用 NTsync 补丁")
        self.assertIn(
            'if [[ "$ABK_ANDROID_VERSION" == "android16" && "$ABK_KERNEL_VERSION" == "6.12" ]]',
            block,
        )
        self.assertIn(
            '|| [[ "$ABK_ANDROID_VERSION" == "android17" && "$ABK_KERNEL_VERSION" == "6.18" ]]',
            block,
        )
        self.assertIn('NATIVE_NTSYNC="true"', block)
        self.assertIn('NTsync 基础源码已由 $', block)

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
        step = kernel_scripts.step("编译内核", self.workflow)
        step_definition = re.search(
            r"(?ms)^      - name: 编译内核\n(?P<body>.*?)(?=^      - name: |\Z)",
            self.workflow,
        )
        self.assertIsNotNone(step_definition, "kernel build step not found")
        self.assertIn("uses: nick-fields/retry@v4", step_definition.group("body"))
        self.assertEqual(step.subcommand, "compile")
        build_script = step.body
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
                    ABK_INPUT_ANDROID_VERSION="android15", ABK_INPUT_KERNEL_VERSION="6.6",
                    ABK_INPUT_SUB_LEVEL="77", ABK_INPUT_KSU_VARIANT="SukiSU",
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

    def test_bazel_attribute_cleanup_handles_all_known_forms(self):
        script = kernel_scripts.step("配置内核名称", self.workflow).python_code()

        cases = {
            "quoted-dict": (
                "define_common_kernels(target_configs = {\n"
                '    "kernel_aarch64": {\n'
                '        "kmi_symbol_list_strict_mode": True,\n'
                '        "protected_exports_list": "android/abi_gki_protected_exports_aarch64",\n'
                '        "makefile": ":Makefile",\n'
                "    },\n"
                "})\n"
            ),
            "bare-assignment": (
                "common_kernel(\n"
                '    name = "kernel_aarch64",\n'
                "    kmi_symbol_list_strict_mode = True,\n"
                '    makefile = ":Makefile",\n'
                ")\n"
            ),
            "multi-line-select": (
                "common_kernel(\n"
                '    name = "kernel_aarch64",\n'
                "    kmi_symbol_list_strict_mode = select({\n"
                '        "//conditions:default": True,\n'
                "    }),\n"
                '    makefile = ":Makefile",\n'
                ")\n"
            ),
        }
        for label, source in cases.items():
            with self.subTest(form=label):
                with tempfile.TemporaryDirectory() as tmp:
                    common = Path(tmp) / "common"
                    common.mkdir()
                    target = common / "BUILD.bazel"
                    target.write_text(source, encoding="utf-8")
                    result = subprocess.run(
                        [sys.executable, "-c", script],
                        cwd=tmp,
                        capture_output=True,
                        text=True,
                        encoding="utf-8",
                        errors="replace",
                        env={**os.environ, "PYTHONIOENCODING": "utf-8"},
                    )
                    self.assertEqual(0, result.returncode, result.stderr)
                    rewritten = target.read_text(encoding="utf-8")
                    self.assertNotIn("kmi_symbol_list_strict_mode", rewritten)
                    self.assertNotIn("protected_exports_list", rewritten)
                    self.assertIn("kernel_aarch64", rewritten)

    def _step_run_block(self, name):
        return kernel_scripts.step(name, self.workflow).body


if __name__ == "__main__":
    unittest.main()
