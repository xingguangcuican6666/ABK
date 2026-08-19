import json
import os
import re
import subprocess
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET
import zipfile
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
    def test_custom_source_workflow_has_exactly_25_inputs(self):
        inputs = workflow_dispatch_inputs(REPO_ROOT / ".github" / "workflows" / "kernel-source.yml")
        self.assertEqual(25, len(inputs))
        self.assertTrue({"source_repo", "source_ref", "source_private", "defconfigs", "os_patch_level"} <= inputs)

    def test_custom_source_helpers_validate_safe_urls_and_defconfigs(self):
        import importlib.util

        module_path = REPO_ROOT / ".github" / "scripts" / "custom-source.py"
        spec = importlib.util.spec_from_file_location("custom_source", module_path)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        self.assertEqual(
            "https://github.com/LineageOS/android_kernel_xiaomi_sm8635.git",
            module.normalize_source_url("https://github.com/LineageOS/android_kernel_xiaomi_sm8635.git"),
        )
        with self.assertRaises(module.CustomSourceError):
            module.normalize_source_url("https://user:pass@github.com/a/b.git", private=True)
        with self.assertRaises(module.CustomSourceError):
            module.parse_defconfigs("vendor/foo.config\n")
        self.assertEqual(
            ["vendor/foo.config", "gki_defconfig", "vendor/foo.config"],
            module.parse_defconfigs("vendor/foo.config\ngki_defconfig\nvendor/foo.config\n"),
        )

    def test_custom_source_kernel_mapping_and_tree_contract(self):
        import importlib.util

        module_path = REPO_ROOT / ".github" / "scripts" / "custom-source.py"
        spec = importlib.util.spec_from_file_location("custom_source_tree", module_path)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        expected = {
            (5, 10): "android12",
            (5, 15): "android13",
            (6, 1): "android14",
            (6, 6): "android15",
            (6, 12): "android16",
        }
        self.assertEqual(expected, module.SUPPORTED_KERNELS)

        with tempfile.TemporaryDirectory() as temp_value:
            root = Path(temp_value)
            (root / "arch/arm64/configs/vendor").mkdir(parents=True)
            (root / "scripts/kconfig").mkdir(parents=True)
            (root / "Makefile").write_text("VERSION = 6\nPATCHLEVEL = 1\nSUBLEVEL = 174\n")
            (root / "build.config.gki.aarch64").write_text("BUILD_CONFIG=1\n")
            (root / "scripts/kconfig/merge_config.sh").write_text("#!/bin/sh\n")
            (root / "arch/arm64/configs/gki_defconfig").write_text("CONFIG_BASE=y\n")
            (root / "arch/arm64/configs/vendor/peridot_GKI.config").write_text("CONFIG_VENDOR=y\n")
            details = module.validate_tree(root, ["gki_defconfig", "vendor/peridot_GKI.config"])
            self.assertEqual("android14", details["android_version"])
            self.assertEqual("6.1.174", details["full_kernel_version"])

            (root / "Makefile").write_text("VERSION = 4\nPATCHLEVEL = 19\nSUBLEVEL = 1\n")
            with self.assertRaises(module.CustomSourceError):
                module.validate_tree(root, ["gki_defconfig"])

    def test_custom_source_feature_status_records_dependency_skip(self):
        script = REPO_ROOT / ".github" / "scripts" / "custom-source-feature-status.py"
        with tempfile.TemporaryDirectory() as temp_value:
            status = Path(temp_value) / "status.json"
            subprocess.run(
                [sys.executable, str(script), "init", "--file", str(status), "--feature", "kernelsu=true", "--feature", "kpm=true"],
                check=True,
            )
            subprocess.run(
                [
                    sys.executable, str(script), "mark", "--file", str(status), "--id", "kpm",
                    "--state", "skipped", "--reason-code", "dependency_skipped",
                    "--message", "KernelSU integration was skipped",
                ],
                check=True,
            )
            value = json.loads(status.read_text())
            self.assertFalse(value["effective"]["kpm"])
            self.assertEqual("dependency_skipped", value["skipped"][0]["reason_code"])

    def test_custom_source_bundler_signs_extended_schema_one_manifest(self):
        script = REPO_ROOT / ".github" / "scripts" / "create-artifact-bundles.py"
        with tempfile.TemporaryDirectory() as temp_value:
            root = Path(temp_value)
            key = root / "signing.pem"
            subprocess.run(
                ["openssl", "genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:2048", "-out", str(key)],
                check=True,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
            (root / "LICENSE").write_text("license\n")
            (root / "THIRD_PARTY_NOTICES.md").write_text("notices\n")
            (root / "android14-6.1.174-2025-09-Images.zip").write_bytes(b"images")
            (root / "android14-6.1.174-2025-09-AnyKernel3.zip").write_bytes(b"anykernel")
            status = root / "feature-status.json"
            status.write_text(json.dumps({
                "requested": {"zram": True},
                "effective": {"zram": False},
                "skipped": [{"id": "zram", "reason_code": "patch_failed", "message": "failed"}],
            }))
            env = os.environ.copy()
            env.update({
                "ABK_SOURCE_MODE": "custom_git",
                "ABK_CUSTOM_SOURCE_URL": "https://github.com/example/private.git",
                "ABK_CUSTOM_SOURCE_ACCESS": "github_private",
                "ABK_CUSTOM_SOURCE_REF": "lineage-23.2",
                "ABK_CUSTOM_SOURCE_COMMIT": "a" * 40,
                "ABK_CUSTOM_SOURCE_KERNEL_VERSION": "6.1.174",
                "ABK_CUSTOM_SOURCE_ANDROID_VERSION": "android14",
                "ABK_CUSTOM_SOURCE_PATCH_LEVEL": "2025-09",
                "ABK_CUSTOM_SOURCE_DEVICE_LABEL": "POCO F6 / peridot",
                "ABK_CUSTOM_SOURCE_DEFCONFIGS_JSON": json.dumps(["gki_defconfig", "vendor/peridot_GKI.config"]),
                "ABK_CUSTOM_SOURCE_FEATURE_STATUS": str(status),
                "GITHUB_RUN_ID": "74",
            })
            subprocess.run(
                [sys.executable, str(script), "--root", str(root), "--key-file", str(key), "--require-signature"],
                check=True,
                env=env,
            )

            bundles = sorted(root.glob("*.bundle.zip"))
            self.assertEqual(2, len(bundles))
            manifests = []
            for bundle in bundles:
                with zipfile.ZipFile(bundle) as archive:
                    manifest = json.loads(archive.read("ABK_BUNDLE_MANIFEST.json"))
                    manifests.append(manifest)
                    self.assertIn("ABK_BUNDLE_MANIFEST.sig", archive.namelist())
                    self.assertEqual(1, manifest["schema"])
                    self.assertEqual("a" * 40, manifest["kernel_source"]["resolved_commit"])
                    self.assertEqual("patch_failed", manifest["feature_status"]["skipped"][0]["reason_code"])
                    self.assertEqual("custom_source_notice_v1", manifest["client_notice"]["capability"])
            image_manifest = next(item for item in manifests if item["artifact_type"] == "OTHER")
            self.assertEqual("KERNEL_IMAGE_SET", image_manifest["payload_kind"])

    def test_custom_source_feature_rollback_preserves_other_feature_commits(self):
        hook = REPO_ROOT / ".github" / "scripts" / "custom-source-feature-env.sh"
        status_script = REPO_ROOT / ".github" / "scripts" / "custom-source-feature-status.py"
        with tempfile.TemporaryDirectory() as temp_value:
            temp = Path(temp_value)
            source = temp / "source"
            source.mkdir()
            subprocess.run(["git", "init"], cwd=source, check=True, stdout=subprocess.DEVNULL)
            subprocess.run(["git", "config", "user.name", "ABK Test"], cwd=source, check=True)
            subprocess.run(["git", "config", "user.email", "abk@example.invalid"], cwd=source, check=True)
            (source / "base").write_text("base\n")
            subprocess.run(["git", "add", "base"], cwd=source, check=True)
            subprocess.run(["git", "commit", "-m", "baseline"], cwd=source, check=True, stdout=subprocess.DEVNULL)
            baseline = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=source, text=True).strip()
            status = temp / "status.json"
            subprocess.run(
                [sys.executable, str(status_script), "init", "--file", str(status), "--feature", "zram=true", "--feature", "bbg=true"],
                check=True,
            )
            common_env = os.environ.copy()
            common_env.update({
                "BASH_ENV": str(hook),
                "ABK_SOURCE_MODE": "custom_git",
                "ABK_CUSTOM_SOURCE_ROOT": str(source),
                "ABK_CUSTOM_SOURCE_TRANSACTION_DIR": str(temp / "transactions"),
                "ABK_CUSTOM_SOURCE_FEATURE_STATUS": str(status),
                "ABK_CUSTOM_SOURCE_BASELINE": baseline,
                "KERNEL_ROOT": str(temp),
                "GITHUB_WORKSPACE": str(REPO_ROOT),
            })
            for feature, command in (("zram", "echo zram > zram.txt"), ("bbg", "echo bbg > bbg.txt")):
                env = common_env.copy()
                env["ABK_FEATURE_ID"] = feature
                subprocess.run(["bash", "-c", command], cwd=source, env=env, check=True)

            env = common_env.copy()
            env.update({"ABK_FEATURE_ID": "zram", "ABK_FEATURE_FAILURE_MESSAGE": "zram failed"})
            subprocess.run(["bash", "-c", "echo broken >> zram.txt; false"], cwd=source, env=env, check=True)

            self.assertFalse((source / "zram.txt").exists())
            self.assertEqual("bbg\n", (source / "bbg.txt").read_text())
            value = json.loads(status.read_text())
            self.assertFalse(value["effective"]["zram"])
            self.assertTrue(value["effective"]["bbg"])

    def test_custom_source_kernelsu_rollback_removes_external_tree(self):
        hook = REPO_ROOT / ".github" / "scripts" / "custom-source-feature-env.sh"
        status_script = REPO_ROOT / ".github" / "scripts" / "custom-source-feature-status.py"
        with tempfile.TemporaryDirectory() as temp_value:
            root = Path(temp_value)
            source = root / "common"
            source.mkdir()
            subprocess.run(["git", "init"], cwd=source, check=True, stdout=subprocess.DEVNULL)
            subprocess.run(["git", "config", "user.name", "ABK Test"], cwd=source, check=True)
            subprocess.run(["git", "config", "user.email", "abk@example.invalid"], cwd=source, check=True)
            (source / "base").write_text("base\n")
            subprocess.run(["git", "add", "base"], cwd=source, check=True)
            subprocess.run(["git", "commit", "-m", "baseline"], cwd=source, check=True, stdout=subprocess.DEVNULL)
            baseline = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=source, text=True).strip()
            status = root / "status.json"
            subprocess.run(
                [sys.executable, str(status_script), "init", "--file", str(status), "--feature", "kernelsu=true", "--feature", "susfs=true", "--feature", "kpm=true"],
                check=True,
            )
            env = os.environ.copy()
            env.update({
                "BASH_ENV": str(hook),
                "ABK_SOURCE_MODE": "custom_git",
                "ABK_FEATURE_ID": "kernelsu",
                "ABK_FEATURE_FAILURE_MESSAGE": "KernelSU failed",
                "ABK_CUSTOM_SOURCE_ROOT": str(source),
                "ABK_CUSTOM_SOURCE_TRANSACTION_DIR": str(root / "transactions"),
                "ABK_CUSTOM_SOURCE_FEATURE_STATUS": str(status),
                "ABK_CUSTOM_SOURCE_BASELINE": baseline,
                "KERNEL_ROOT": str(root),
                "GITHUB_WORKSPACE": str(REPO_ROOT),
            })
            subprocess.run(
                ["bash", "-c", "mkdir KernelSU; echo source > KernelSU/file; echo ksu > common/ksu.txt"],
                cwd=root,
                env=env,
                check=True,
            )
            subprocess.run(
                ["bash", "-c", "echo broken >> KernelSU/file; false"],
                cwd=root,
                env=env,
                check=True,
            )

            self.assertFalse((root / "KernelSU").exists())
            self.assertFalse((source / "ksu.txt").exists())
            value = json.loads(status.read_text())
            self.assertFalse(value["effective"]["kernelsu"])
            self.assertEqual("dependency_skipped", next(x for x in value["skipped"] if x["id"] == "susfs")["reason_code"])

    def test_custom_source_external_git_rollback_preserves_other_features(self):
        hook = REPO_ROOT / ".github" / "scripts" / "custom-source-feature-env.sh"
        status_script = REPO_ROOT / ".github" / "scripts" / "custom-source-feature-status.py"
        with tempfile.TemporaryDirectory() as temp_value:
            root = Path(temp_value)
            source = root / "common"
            external = root / "KernelSU"
            for repository in (source, external):
                repository.mkdir()
                subprocess.run(["git", "init"], cwd=repository, check=True, stdout=subprocess.DEVNULL)
                subprocess.run(["git", "config", "user.name", "ABK Test"], cwd=repository, check=True)
                subprocess.run(["git", "config", "user.email", "abk@example.invalid"], cwd=repository, check=True)
                (repository / "base").write_text("base\n")
                subprocess.run(["git", "add", "base"], cwd=repository, check=True)
                subprocess.run(["git", "commit", "-m", "baseline"], cwd=repository, check=True, stdout=subprocess.DEVNULL)

            baseline = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=source, text=True).strip()
            status = root / "status.json"
            subprocess.run(
                [sys.executable, str(status_script), "init", "--file", str(status), "--feature", "susfs=true", "--feature", "kpm=true"],
                check=True,
            )
            common_env = os.environ.copy()
            common_env.update({
                "BASH_ENV": str(hook),
                "ABK_SOURCE_MODE": "custom_git",
                "ABK_CUSTOM_SOURCE_ROOT": str(source),
                "ABK_CUSTOM_SOURCE_TRANSACTION_DIR": str(root / "transactions"),
                "ABK_CUSTOM_SOURCE_FEATURE_STATUS": str(status),
                "ABK_CUSTOM_SOURCE_BASELINE": baseline,
                "KERNEL_ROOT": str(root),
                "GITHUB_WORKSPACE": str(REPO_ROOT),
            })
            for feature, filename in (("susfs", "susfs.txt"), ("kpm", "kpm.txt")):
                env = common_env.copy()
                env["ABK_FEATURE_ID"] = feature
                subprocess.run(["bash", "-c", f"echo {feature} > KernelSU/{filename}"], cwd=root, env=env, check=True)

            env = common_env.copy()
            env.update({"ABK_FEATURE_ID": "susfs", "ABK_FEATURE_FAILURE_MESSAGE": "SUSFS failed"})
            subprocess.run(["bash", "-c", "echo broken >> KernelSU/susfs.txt; false"], cwd=root, env=env, check=True)

            self.assertFalse((external / "susfs.txt").exists())
            self.assertEqual("kpm\n", (external / "kpm.txt").read_text())
            value = json.loads(status.read_text())
            self.assertFalse(value["effective"]["susfs"])
            self.assertTrue(value["effective"]["kpm"])

    def test_desktop_workflow_limits_github_token_permissions(self):
        workflow = (
            REPO_ROOT / ".github" / "workflows" / "build-abk-desktop.yml"
        ).read_text(encoding="utf-8")

        self.assertIn("permissions:\n  contents: read", workflow)

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

    def test_oneplus_15_catalog_matches_workflow_and_matrix_contracts(self):
        expected = {
            "oneplus_15": {
                "name": "OnePlus 15",
                "cpu": "sm8850",
                "android": "android16",
                "kernel": "6.12",
            },
            "oneplus_15t": {
                "name": "OnePlus 15T",
                "cpu": "sm8850",
                "android": "android16",
                "kernel": "6.12",
            },
        }
        custom_workflow = (
            REPO_ROOT / ".github" / "workflows" / "oneplus-custom.yml"
        ).read_text(encoding="utf-8")
        build_workflow = (
            REPO_ROOT / ".github" / "workflows" / "oneplus-build.yml"
        ).read_text(encoding="utf-8")
        matrix_workflow = (
            REPO_ROOT / ".github" / "workflows" / "oneplus-full-feature-matrix.yml"
        ).read_text(encoding="utf-8")

        for manifest, profile in expected.items():
            with self.subTest(manifest=manifest):
                self.assertEqual(profile, abk.ONEPLUS_DEVICES[manifest])
                self.assertIn(f"- {manifest}", custom_workflow)
                self.assertIn(f'{manifest}) device_name="{profile["name"]}"', build_workflow)
                self.assertIn(f'"{manifest}"', matrix_workflow)

        self.assertIn("- sm8850", custom_workflow)
        self.assertIn('- "6.12"', custom_workflow)
        self.assertIn('"5": ("android16", "6.12")', matrix_workflow)
        self.assertIn("clang-r536225", build_workflow)
        self.assertIn("prebuilts/rust/linux-x86/1.82.0/bin/rustc", build_workflow)
        self.assertIn(("android16", "6.12"), abk.ONEPLUS_SUSFS_SUPPORTED)

    def test_oneplus_manifest_sanitizer_removes_unfetchable_optional_prebuilts(self):
        script = REPO_ROOT / ".github" / "scripts" / "sanitize-oneplus-manifest.py"
        build_workflow = (
            REPO_ROOT / ".github" / "workflows" / "oneplus-build.yml"
        ).read_text(encoding="utf-8")

        self.assertIn("sanitize-oneplus-manifest.py", build_workflow)
        self.assertIn("oneplus_15|oneplus_15t)", build_workflow)
        self.assertLess(
            build_workflow.index("repo init"),
            build_workflow.index("sanitize-oneplus-manifest.py"),
        )
        self.assertLess(
            build_workflow.index("sanitize-oneplus-manifest.py"),
            build_workflow.index("repo sync -c"),
        )

        manifest = """<?xml version="1.0" encoding="UTF-8"?>
<manifest>
  <project name="kernel_platform/prebuilts/asuite" path="kernel_platform/prebuilts/asuite" revision="bad"/>
  <project name="kernel_platform/tools/tradefederation/prebuilts" path="kernel_platform/prebuilts/tradefed" revision="bad"/>
  <project name="android_kernel_common_oneplus_sm8850" path="kernel_platform/common" revision="good"/>
</manifest>
"""
        with tempfile.TemporaryDirectory() as tmpdir:
            manifest_path = Path(tmpdir) / "oneplus_15t.xml"
            manifest_path.write_text(manifest, encoding="utf-8")

            result = subprocess.run(
                [sys.executable, str(script), str(manifest_path)],
                check=True,
                capture_output=True,
                text=True,
            )

            self.assertIn("kernel_platform/prebuilts/asuite", result.stdout)
            root = ET.parse(manifest_path).getroot()
            remaining_paths = {project.get("path") for project in root.findall("project")}

        self.assertNotIn("kernel_platform/prebuilts/asuite", remaining_paths)
        self.assertNotIn("kernel_platform/prebuilts/tradefed", remaining_paths)
        self.assertIn("kernel_platform/common", remaining_paths)

    def test_oneplus_checkout_runs_after_build_space_mount(self):
        build_workflow = (
            REPO_ROOT / ".github" / "workflows" / "oneplus-build.yml"
        ).read_text(encoding="utf-8")

        self.assertLess(
            build_workflow.index("easimon/maximize-build-space@master"),
            build_workflow.index("actions/checkout@v6"),
        )

    def test_oneplus_build_installs_gendwarfksyms_dependencies(self):
        build_workflow = (
            REPO_ROOT / ".github" / "workflows" / "oneplus-build.yml"
        ).read_text(encoding="utf-8")
        install_start = build_workflow.index("apt-get install")
        install_end = build_workflow.index("if ! command -v repo", install_start)
        dependency_block = build_workflow[install_start:install_end]

        self.assertIn("libelf-dev", dependency_block)
        self.assertIn("libdw-dev", dependency_block)
        self.assertIn("zlib1g-dev", dependency_block)

    def test_oneplus_official_kernelsu_uapi_is_materialized_when_symlink_is_unusable(self):
        script = (
            REPO_ROOT
            / ".github"
            / "scripts"
            / "ensure-kernelsu-uapi.sh"
        )
        build_workflow = (
            REPO_ROOT / ".github" / "workflows" / "oneplus-build.yml"
        ).read_text(encoding="utf-8")

        self.assertIn("ensure-kernelsu-uapi.sh", build_workflow)
        self.assertLess(
            build_workflow.index("setup.sh"),
            build_workflow.index("ensure-kernelsu-uapi.sh"),
        )
        compile_start = build_workflow.index("- name: 编译内核")
        compile_block = build_workflow[compile_start:]
        for include_root in (
            "$KERNEL_ROOT/KernelSU",
            "$KERNEL_ROOT/KernelSU/kernel",
            "$KERNEL_ROOT/KernelSU/kernel/include",
        ):
            self.assertIn(f'"KCFLAGS+=-I{include_root}"', compile_block)
        self.assertIn('"${KSU_KCFLAGS[@]}"', compile_block)

        if sys.platform == "win32":
            self.skipTest("Kernel workflow Bash integration runs on Linux only")

        with tempfile.TemporaryDirectory() as tmpdir:
            kernel_root = Path(tmpdir) / "kernel_platform"
            source_dir = kernel_root / "KernelSU" / "uapi"
            include_dir = kernel_root / "KernelSU" / "kernel" / "include" / "uapi"
            source_dir.mkdir(parents=True)
            include_dir.parent.mkdir(parents=True)
            (source_dir / "app_profile.h").write_text(
                "/* test header */\n",
                encoding="utf-8",
            )
            # This is how a symlink can appear when the checkout cannot preserve it.
            include_dir.write_text("../../uapi", encoding="utf-8")

            result = subprocess.run(
                ["bash", str(script), kernel_root.as_posix()],
                check=True,
                capture_output=True,
                text=True,
            )

            self.assertIn("materialized", result.stdout)
            self.assertTrue((include_dir / "app_profile.h").is_file())
            self.assertEqual(
                "/* test header */\n",
                (include_dir / "app_profile.h").read_text(encoding="utf-8"),
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
