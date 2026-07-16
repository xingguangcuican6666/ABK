import json
import re
import sys
import unittest
from pathlib import Path
from types import SimpleNamespace


REPO_ROOT = Path(__file__).resolve().parents[2]
CLI_DIR = REPO_ROOT / "cli"
if str(CLI_DIR) not in sys.path:
    sys.path.insert(0, str(CLI_DIR))

import abk  # noqa: E402


def workflow_dispatch_inputs(path):
    """Read top-level workflow_dispatch input names without a YAML dependency."""
    names = set()
    in_dispatch = False
    in_inputs = False
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        indent = len(line) - len(line.lstrip())
        if indent == 2 and stripped == "workflow_dispatch:":
            in_dispatch = True
            in_inputs = False
            continue
        if in_dispatch and indent == 4 and stripped == "inputs:":
            in_inputs = True
            continue
        if in_inputs and indent == 6:
            match = re.fullmatch(r"([A-Za-z0-9_]+):", stripped)
            if match:
                names.add(match.group(1))
                continue
        if in_inputs and stripped and indent <= 4:
            break
    return names


def build_args(**overrides):
    values = {
        "ksu_branch": None,
        "custom_ref": None,
        "version": None,
        "revision": None,
        "build_time": None,
        "kpm_password": None,
        "susfs": True,
        "zram": False,
        "bbg": False,
        "ddk": False,
        "kpm": False,
        "rekernel": False,
        "ntsync": False,
        "networking": False,
        "oneplus_8e": False,
        "virt": "off",
        "zram_full_algo": False,
        "zram_extra_algos": None,
        "custom_modules": None,
        "build_scope": None,
        "manager_variants": None,
        "lz4kd": False,
        "bbr": False,
        "proxy_optimization": False,
        "unicode_bypass": False,
    }
    values.update(overrides)
    return SimpleNamespace(**values)


class WorkflowContractTests(unittest.TestCase):
    def assert_inputs_supported(self, workflow_name, inputs):
        declared = workflow_dispatch_inputs(
            REPO_ROOT / ".github" / "workflows" / workflow_name
        )
        self.assertTrue(declared, workflow_name)
        self.assertEqual(set(), set(inputs) - declared, workflow_name)

    def test_cli_dispatch_inputs_match_existing_abk_workflows(self):
        standard = abk._standard_build_inputs(build_args(), "Official")
        for target in ("a12", "a13", "a14", "a15", "a16"):
            self.assert_inputs_supported(abk.WORKFLOWS[target]["file"], standard)

        custom = dict(standard)
        custom.update(
            {
                "supp_op": "false",
                "android_version": "android12",
                "kernel_version": "5.10",
                "sub_level": "101",
                "os_patch_level": "2026-01",
            }
        )
        self.assert_inputs_supported(abk.WORKFLOWS["custom"]["file"], custom)

        oneplus = {
            "ksu_variant",
            "device_manifest",
            "cpu",
            "android_version",
            "kernel_version",
            "enable_susfs",
            "use_kpm",
            "use_lz4kd",
            "use_bbg",
            "use_bbr",
            "use_proxy_optimization",
            "use_unicode_bypass",
        }
        self.assert_inputs_supported(abk.WORKFLOWS["oneplus"]["file"], oneplus)

        full = abk._full_matrix_inputs(build_args(), "ReSukiSU")
        self.assert_inputs_supported(abk.FULL_MATRIX_WORKFLOWS["full"], full)

        all_managers = abk._all_managers_inputs(build_args())
        self.assert_inputs_supported(
            abk.FULL_MATRIX_WORKFLOWS["all-managers"],
            all_managers,
        )

    def test_kpm_support_matches_the_selected_ksu_source(self):
        cases = (
            ("SukiSU", "Stable", False, True),
            ("SukiSU", "Latest", False, True),
            ("SukiSU", "Dev", False, True),
            ("ReSukiSU", None, False, True),
            ("ReSukiSU", "Stable", False, True),
            ("ReSukiSU", "Latest", False, False),
            ("ReSukiSU", "Dev", False, False),
            ("ReSukiSU", "Custom", False, True),
            ("SukiSU", "Stable", True, True),
            ("ReSukiSU", "Stable", True, True),
            ("Official", "Stable", False, False),
            ("Official", "Stable", True, False),
            ("None", "Stable", False, False),
        )
        for variant, branch, oneplus, expected in cases:
            with self.subTest(variant=variant, branch=branch, oneplus=oneplus):
                self.assertEqual(
                    expected,
                    abk.supports_kpm(variant, branch, oneplus=oneplus),
                )

    def test_virtualization_values_are_normalized_for_each_kernel_contract(self):
        cases = (
            ("5.10", "off", "off"),
            ("5.10", "on", "678"),
            ("5.10", "123", "123"),
            ("6.6", "345", "345"),
            ("6.12", "on", "on"),
            ("6.12", "678", "on"),
            ("6.12", "123", "on"),
        )
        for kernel_version, requested, expected in cases:
            with self.subTest(kernel=kernel_version, requested=requested):
                self.assertEqual(
                    expected,
                    abk.normalize_virtualization_support(
                        kernel_version,
                        requested,
                    ),
                )

    def test_standard_inputs_never_send_an_invalid_a16_virtualization_slot(self):
        inputs = abk._standard_build_inputs(
            build_args(virt="123"),
            "Official",
            "6.12",
        )

        self.assertEqual("on", inputs["virtualization_support"])

    def test_none_variant_disables_susfs_like_android_normalization(self):
        standard = abk._standard_build_inputs(
            build_args(
                susfs=True,
                ksu_branch="Custom",
                custom_ref="feature/ignored-for-none",
            ),
            "None",
        )
        full = abk._full_matrix_inputs(
            build_args(susfs=True, ksu_branch="Dev"),
            "None",
        )

        self.assertEqual("true", standard["cancel_susfs"])
        self.assertEqual("Stable(标准)", standard["kernelsu_branch"])
        self.assertNotIn("custom_ref", standard)
        self.assertEqual("false", full["enable_susfs"])
        self.assertEqual("Stable(标准)", full["kernelsu_branch"])

    def test_supp_op_is_only_added_to_supporting_standard_workflows(self):
        unsupported = abk._standard_build_inputs(
            build_args(oneplus_8e=True),
            "Official",
            "6.1",
        )
        supported = abk._standard_build_inputs(
            build_args(oneplus_8e=True),
            "Official",
            "6.6",
            supports_supp_op=True,
        )

        self.assertNotIn("supp_op", unsupported)
        self.assertEqual("true", supported["supp_op"])

    def test_unsupported_resukisu_inputs_disable_kpm_and_password(self):
        args = build_args(kpm=True, ksu_branch="Dev", kpm_password="secret")

        standard = abk._standard_build_inputs(args, "ReSukiSU")
        full = abk._full_matrix_inputs(args, "ReSukiSU")

        self.assertEqual("false", standard["use_kpm"])
        self.assertNotIn("kpm_password", standard)
        self.assertEqual("false", full["use_kpm"])
        self.assertEqual("", full["kpm_password"])

        latest = abk._standard_build_inputs(
            build_args(kpm=True, ksu_branch="Latest", kpm_password="secret"),
            "ReSukiSU",
        )
        custom = abk._standard_build_inputs(
            build_args(kpm=True, ksu_branch="Custom", kpm_password="secret"),
            "ReSukiSU",
        )
        self.assertEqual("false", latest["use_kpm"])
        self.assertNotIn("kpm_password", latest)
        self.assertEqual("true", custom["use_kpm"])
        self.assertEqual("secret", custom["kpm_password"])

    def test_all_managers_delegates_oneplus_kpm_filtering_to_workflow(self):
        all_variants = abk._all_managers_inputs(
            build_args(kpm=True, manager_variants="all")
        )
        sukisu_only = abk._all_managers_inputs(
            build_args(kpm=True, manager_variants="SukiSU")
        )

        self.assertEqual("true", all_variants["use_kpm"])
        self.assertTrue(json.loads(all_variants["oneplus_options_json"])["use_kpm"])
        self.assertTrue(json.loads(sukisu_only["oneplus_options_json"])["use_kpm"])

    def test_all_managers_only_forwards_kpm_password_when_supported(self):
        disabled = abk._all_managers_inputs(
            build_args(kpm=False, kpm_password="secret", manager_variants="SukiSU")
        )
        unsupported = abk._all_managers_inputs(
            build_args(kpm=True, kpm_password="secret", manager_variants="Official")
        )
        oneplus_only = abk._all_managers_inputs(
            build_args(
                kpm=True,
                kpm_password="secret",
                manager_variants="SukiSU",
                build_scope="OnePlus",
            )
        )
        supported = abk._all_managers_inputs(
            build_args(kpm=True, kpm_password="secret", manager_variants="SukiSU")
        )

        self.assertEqual("", disabled["kpm_password"])
        self.assertEqual("", unsupported["kpm_password"])
        self.assertEqual("", oneplus_only["kpm_password"])
        self.assertEqual("secret", supported["kpm_password"])

    def test_machine_readable_workflow_names_match_github_workflow_names(self):
        for workflow_file, expected_name in abk.WORKFLOW_RUNTIME_NAMES.items():
            with self.subTest(workflow=workflow_file):
                workflow = (
                    REPO_ROOT / ".github" / "workflows" / workflow_file
                ).read_text(encoding="utf-8")
                actual_name = next(
                    line.split(":", 1)[1].strip()
                    for line in workflow.splitlines()
                    if line.startswith("name:")
                )
                self.assertEqual(expected_name, actual_name)

    def test_signing_identifiers_match_android_authority(self):
        android = (
            REPO_ROOT
            / "app"
            / "src"
            / "main"
            / "java"
            / "com"
            / "abk"
            / "kernel"
            / "viewmodel"
            / "MainViewModel.kt"
        ).read_text(encoding="utf-8")
        build = (
            REPO_ROOT / ".github" / "workflows" / "build.yml"
        ).read_text(encoding="utf-8")

        self.assertIn(
            f'FORK_ARTIFACT_SIGNING_SECRET_NAME = "{abk.SIGNING_SECRET_NAME}"',
            android,
        )
        self.assertIn(
            f'FORK_ARTIFACT_SIGNING_RELEASE_TAG = "{abk.SIGNING_RELEASE_TAG}"',
            android,
        )
        self.assertIn(
            f'FORK_ARTIFACT_SIGNING_PUBLIC_KEY_ASSET_NAME = "{abk.SIGNING_PUBLIC_KEY_ASSET}"',
            android,
        )
        self.assertIn(f"secrets.{abk.SIGNING_SECRET_NAME}", build)

    def test_cli_packaging_workflow_runs_regression_tests(self):
        workflow = (
            REPO_ROOT / ".github" / "workflows" / "build-abk-cli.yml"
        ).read_text(encoding="utf-8")
        self.assertIn("python -m unittest discover -s cli/tests -v", workflow)
        self.assertIn("github.event_name == 'workflow_dispatch' && github.run_id", workflow)
        self.assertGreaterEqual(workflow.count("--json self-test"), 4)
        self.assertGreaterEqual(
            workflow.count("--json build --dry-run --matrix a14"),
            4,
        )
        self.assertIn('result["schemaVersion"] == 1', workflow)
        self.assertIn('result["tlsContext"] is True', workflow)
        self.assertIn('"LICENSE"', workflow)
        for dependency in abk.WORKFLOW_RUNTIME_NAMES:
            self.assertIn(f'".github/workflows/{dependency}"', workflow)
        self.assertNotIn("raw.githubusercontent.com", workflow)
        self.assertIn("cp LICENSE dist/abk/LICENSE", workflow)
        self.assertIn('Copy-Item -LiteralPath "LICENSE"', workflow)

    def test_cross_packaging_uses_fast_compatible_crypto_fallback(self):
        workflow = (
            REPO_ROOT / ".github" / "workflows" / "build-abk-cli.yml"
        ).read_text(encoding="utf-8")
        cross_workflow = workflow.split(
            "- name: Prepare Linux cross-packaging runtime",
            maxsplit=1,
        )[1]

        self.assertNotIn(
            "python -m pip install cryptography 2>/dev/null",
            cross_workflow,
        )
        self.assertEqual(
            2,
            cross_workflow.count(
                'assert abk._CRYPTO_BACKEND == \\\"pycryptodome\\\"'
            ),
        )
        self.assertEqual(
            2,
            cross_workflow.count('$pip_cache:/root/.cache/pip'),
        )
        self.assertEqual(2, cross_workflow.count("trap restore_pip_cache_owner EXIT"))
        self.assertIn('--entrypoint /bin/true', cross_workflow)
        self.assertIn('--install "$CROSS_BINFMT"', cross_workflow)


if __name__ == "__main__":
    unittest.main()
