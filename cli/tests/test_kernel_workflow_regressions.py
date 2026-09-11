import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
WORKFLOW_PATH = ROOT / ".github" / "workflows" / "build.yml"
REF_SCRIPT_PATH = ROOT / ".github" / "scripts" / "resolve-ksu-ref.sh"


class KernelWorkflowRegressionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = WORKFLOW_PATH.read_text(encoding="utf-8")
        cls.ref_script = REF_SCRIPT_PATH.read_text(encoding="utf-8")

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
