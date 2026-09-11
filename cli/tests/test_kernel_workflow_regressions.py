import importlib.util
import re
import tempfile
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
        case = block.split('"SukiSU")', 1)[1].split('"ReSukiSU")', 1)[0]

        self.assertIn('requested_ref="$BRANCH"', case)
        self.assertNotIn('${BRANCH#-s }', case)
        self.assertIn('bash "$setup_script" "$requested_ref"', case)
        self.assertIn('requested_head="$(git -C KernelSU rev-parse', case)

    def test_development_refs_are_reachable_successful_main_builds(self):
        expected = {
            "OFFICIAL_DEV_REF": "33d0c9205df47b6b1b61c25c13afa164b88871d1",
            "SUKISU_DEV_REF": "9fbe8fe8ca90c62c259c5894bf96d02ac31209b9",
            "RESUKISU_DEV_REF": "246d3e52e667cb72ce8f70c93b70d3b42b100b76",
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
            "if: (inputs.ksu_variant == 'SukiSU' || inputs.ksu_variant == 'ReSukiSU') && inputs.enable_susfs",
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
            "extern int susfs_sus_kstat_spoof_vfs_statfs(struct inode *inode, "
            "struct kstatfs *buf, bool *is_fuse);",
            block,
        )
        self.assertNotIn(
            "android12-5.10 Official fs/statfs.c 缺少 susfs_def.h",
            block,
        )
        statfs_repair = block.split("# Android 12/5.10 的上游补丁", 1)[1].split("# Android 13 - 5.15 修复", 1)[0]
        self.assertNotIn('[[ "$ABK_KSU_VARIANT" == "Official" ]]', statfs_repair)
        self.assertIn("fix_fdinfo_declarations", block)
        self.assertIn('declarations.append("\\tstruct mount *mnt;\\n")', block)

    def test_android16_uses_native_ntsync_source(self):
        block = self._step_run_block("应用 NTsync 补丁")
        self.assertIn('if [[ "$ABK_ANDROID_VERSION" != "android16"', block)
        self.assertIn('NTsync 基础源码已由 android16-6.12 内核提供', block)

    def test_android14_builtin_ntsync_does_not_require_symbol_export(self):
        block = self._step_run_block("应用 NTsync 补丁")
        self.assertNotIn("EXPORT_SYMBOL", block)
        self.assertNotIn("拒绝启用 CONFIG_NTSYNC", block)
        self.assertIn("ensure_defconfig_value CONFIG_NTSYNC y", block)

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
