import contextlib
import io
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest import mock


CLI_DIR = Path(__file__).resolve().parents[1]
if str(CLI_DIR) not in sys.path:
    sys.path.insert(0, str(CLI_DIR))

import abk  # noqa: E402


def build_args(**overrides):
    """Return the complete argument shape expected by cmd_build."""
    values = {
        "token": "test-token",
        "repo": None,
        "verbose": False,
        "lang": None,
        "matrix": None,
        "oneplus": False,
        "source_repo": None,
        "source_ref": None,
        "source_private": False,
        "source_defconfigs": None,
        "source_device_label": None,
        "source_kernel_version": None,
        "ref": "dev",
        "ksu_variant": "Official",
        "ksu_branch": None,
        "custom_ref": None,
        "version": None,
        "device": None,
        "virt": "off",
        "kpm_password": None,
        "build_time": None,
        "force": False,
        "dry_run": False,
        "android_version": "android12",
        "kernel_version": "5.10",
        "sub_level": "101",
        "os_patch_level": "2026-01",
        "revision": None,
        "build_scope": None,
        "manager_variants": None,
        "zram": False,
        "bbg": False,
        "ddk": False,
        "kpm": False,
        "susfs": True,
        "rekernel": False,
        "oneplus_8e": False,
        "lz4kd": False,
        "bbr": False,
        "proxy_optimization": False,
        "unicode_bypass": False,
        "ntsync": False,
        "networking": False,
        "zram_full_algo": False,
        "zram_extra_algos": None,
        "custom_modules": None,
        "kernel_options_file": None,
    }
    values.update(overrides)
    return SimpleNamespace(**values)


def status_args(**overrides):
    values = {
        "token": "test-token",
        "repo": None,
        "verbose": False,
        "lang": None,
        "run_id": None,
        "target": None,
        "status": "all",
        "limit": 10,
        "cancel": None,
        "rerun": None,
    }
    values.update(overrides)
    return SimpleNamespace(**values)


def artifact_args(**overrides):
    values = {
        "token": "test-token",
        "repo": None,
        "verbose": False,
        "lang": None,
        "run_id": 12345,
        "download": True,
        "output": None,
        "set_download_dir": None,
    }
    values.update(overrides)
    return SimpleNamespace(**values)


class RecordingGitHubClient:
    def __init__(
        self,
        *,
        fork=None,
        trigger_error=None,
        trigger_response=None,
        source_secret_exists=False,
    ):
        self.token = "test-token"
        self.username = "alice"
        self.repo = fork["full_name"] if fork else abk.DEFAULT_REPO
        self.fork_repo = fork
        self._fork = fork
        self.trigger_error = trigger_error
        self.trigger_response = trigger_response or {}
        self.source_secret_exists = source_secret_exists
        self.create_fork_calls = 0
        self.sync_fork_calls = 0
        self.check_behind_calls = 0
        self.trigger_calls = []
        self.get_run_calls = []
        self.list_runs_calls = []

    def get_fork(self, owner=None, repo=None):
        return self._fork

    def create_fork(self, owner=None, repo=None):
        self.create_fork_calls += 1
        self._fork = {
            "full_name": "alice/ABK",
            "name": "ABK",
            "owner": {"login": "alice"},
            "default_branch": "dev",
        }
        self.fork_repo = self._fork
        return self._fork

    def check_behind(self, *args, **kwargs):
        self.check_behind_calls += 1
        return {"behind_by": 0, "ahead_by": 0, "status": "identical"}

    def sync_fork(self, branch=None):
        self.sync_fork_calls += 1
        return {}

    def get_default_branch(self):
        return "dev"

    def trigger_workflow(self, workflow_file, ref, inputs):
        self.trigger_calls.append(
            {
                "repo": self.repo,
                "workflow_file": workflow_file,
                "ref": ref,
                "inputs": inputs,
            }
        )
        if self.trigger_error is not None:
            raise self.trigger_error
        return self.trigger_response

    def repository_secret_exists(self, name):
        self.last_secret_name = name
        return self.source_secret_exists

    def get_run(self, run_id):
        self.get_run_calls.append(run_id)
        return {
            "id": run_id,
            "name": "ABK test build",
            "status": "completed",
            "conclusion": "success",
            "created_at": "2026-07-11T00:00:00Z",
        }

    def list_runs(self, workflow_file=None, status=None, per_page=10):
        self.list_runs_calls.append(
            {
                "workflow_file": workflow_file,
                "status": status,
                "per_page": per_page,
            }
        )
        return {
            "workflow_runs": [
                {
                    "id": 99,
                    "name": "ABK test build",
                    "status": "completed",
                    "conclusion": "success",
                    "created_at": "2026-07-11T00:00:00Z",
                }
            ]
        }


class ArtifactGitHubClient:
    def __init__(self, output_dir, *, download_error=None):
        self.token = "test-token"
        self.username = "alice"
        self.repo = abk.DEFAULT_REPO
        self.fork_repo = None
        self.output_dir = Path(output_dir)
        self.download_error = download_error

    def get_fork(self, owner=None, repo=None):
        return {
            "full_name": "alice/ABK",
            "name": "ABK",
            "owner": {"login": "alice"},
        }

    def list_artifacts(self, run_id):
        return {
            "artifacts": [
                {
                    "id": 77,
                    "name": "BakaSU_kernel-test",
                    "size_in_bytes": 128,
                }
            ]
        }

    def download_artifact(self, artifact_id, output_dir):
        if self.download_error:
            raise self.download_error
        path = Path(output_dir) / f"artifact-{artifact_id}.zip"
        path.write_bytes(b"test artifact")
        return str(path)

class CommandBehaviorTests(unittest.TestCase):
    def test_human_whoami_does_not_fetch_signing_metadata(self):
        client = mock.Mock()
        client.repo = "alice/ABK"
        client.get_user.return_value = {"login": "alice"}
        client.get_fork.return_value = {
            "full_name": "alice/ABK",
            "name": "ABK",
            "owner": {"login": "alice"},
        }
        client.check_behind.return_value = {
            "behind_by": 0,
            "ahead_by": 0,
            "status": "identical",
        }
        args = SimpleNamespace(
            token="test-token",
            repo=None,
            verbose=False,
            json=False,
        )

        with (
            mock.patch.object(abk, "get_token", return_value="test-token"),
            mock.patch.object(abk, "make_client", return_value=client),
            contextlib.redirect_stdout(io.StringIO()),
        ):
            result = abk.cmd_whoami(args)

        self.assertEqual(0, result)
        client.get_published_signing_key.assert_not_called()

    def _run_build(self, client, **overrides):
        args = build_args(**overrides)
        output = io.StringIO()
        with contextlib.ExitStack() as stack:
            stack.enter_context(
                mock.patch.object(abk, "get_token", return_value="test-token")
            )
            stack.enter_context(mock.patch.object(abk, "GitHubClient", return_value=client))
            stack.enter_context(
                mock.patch.object(abk, "ensure_signing_key", return_value="test-key")
            )
            stack.enter_context(mock.patch.object(abk.time, "sleep", return_value=None))
            stack.enter_context(contextlib.redirect_stdout(output))
            stack.enter_context(contextlib.redirect_stderr(output))
            result = abk.cmd_build(args)
        return result, output.getvalue()

    def _run_status(self, client, **overrides):
        args = status_args(**overrides)
        output = io.StringIO()
        with contextlib.ExitStack() as stack:
            stack.enter_context(
                mock.patch.object(abk, "get_token", return_value="test-token")
            )
            stack.enter_context(mock.patch.object(abk, "GitHubClient", return_value=client))
            stack.enter_context(contextlib.redirect_stdout(output))
            stack.enter_context(contextlib.redirect_stderr(output))
            result = abk.cmd_status(args)
        return result, output.getvalue()

    def _run_artifacts(self, client, verification, *, answer="", **overrides):
        args = artifact_args(output=str(client.output_dir), **overrides)
        output = io.StringIO()
        with contextlib.ExitStack() as stack:
            stack.enter_context(
                mock.patch.object(abk, "get_token", return_value="test-token")
            )
            stack.enter_context(mock.patch.object(abk, "GitHubClient", return_value=client))
            stack.enter_context(
                mock.patch.object(abk, "resolve_verification_key", return_value="test-key")
            )
            stack.enter_context(
                mock.patch.object(abk, "verify_artifact_archive", return_value=verification)
            )
            stack.enter_context(contextlib.redirect_stdout(output))
            stack.enter_context(contextlib.redirect_stderr(output))
            stack.enter_context(mock.patch.object(sys, "stdin", io.StringIO(answer)))
            result = abk.cmd_artifacts(args)
        return result, output.getvalue()

    def test_dry_run_does_not_create_sync_or_dispatch(self):
        client = RecordingGitHubClient(fork=None)

        self._run_build(client, dry_run=True)

        self.assertEqual(0, client.create_fork_calls)
        self.assertEqual(0, client.sync_fork_calls)
        self.assertEqual([], client.trigger_calls)

    def test_custom_source_dry_run_matches_app_workflow_contract(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            options_file = Path(temp_dir) / "kernel.config"
            options_file.write_text(
                "CONFIG_TEST=y\n"
                "# CONFIG_UNUSED is not set\n"
                "CONFIG_TEST=m\n",
                encoding="utf-8",
            )
            client = RecordingGitHubClient(fork=None)
            result, output = self._run_build(
                client,
                source_repo="https://GitHub.com/example/kernel.git/",
                source_ref="lineage-23.2",
                source_defconfigs=["gki_defconfig", "vendor/device_defconfig"],
                source_device_label="example-device",
                source_kernel_version="6.13.4",
                android_version=None,
                kernel_version=None,
                sub_level=None,
                os_patch_level="LTS",
                revision=None,
                ksu_variant=None,
                kernel_options_file=str(options_file),
                dry_run=True,
            )

        self.assertEqual(0, result, output)
        inputs_line = next(
            line.strip().removeprefix("inputs=")
            for line in output.splitlines()
            if line.strip().startswith("inputs=")
        )
        inputs = json.loads(inputs_line)
        self.assertEqual(25, len(inputs))
        self.assertEqual("https://github.com/example/kernel.git", inputs["source_repo"])
        self.assertEqual("lineage-23.2", inputs["source_ref"])
        self.assertEqual(
            "gki_defconfig\nvendor/device_defconfig",
            inputs["defconfigs"],
        )
        self.assertEqual(
            '{"os_patch_level":"lts","kernel_version_override":"6.13.4"}',
            inputs["version_overrides"],
        )
        self.assertEqual("None", inputs["kernelsu_variant"])
        self.assertEqual("true", inputs["cancel_susfs"])
        self.assertEqual("false", inputs["use_kpm"])
        self.assertEqual("", inputs["build_time"])
        self.assertEqual(
            "# CONFIG_UNUSED is not set\nCONFIG_TEST=m",
            inputs["custom_kernel_options"],
        )
        self.assertIn("workflow=kernel-source.yml", output)

    def test_custom_source_validation_fails_before_remote_calls(self):
        cases = (
            {"source_repo": "http://github.com/acme/kernel"},
            {"source_repo": "https://github.com/acme/kernel name"},
            {"source_repo": "https://github.com/acme/%2e%2e/kernel"},
            {"source_repo": "https://[2001:db8::1]/acme/kernel"},
            {"source_repo": "https://user:secret@github.com/acme/kernel"},
            {"source_repo": "https://github.com/acme/kernel?token=secret"},
            {
                "source_repo": "https://gitlab.com/acme/kernel",
                "source_private": True,
            },
            {"source_ref": "../unsafe"},
            {"source_defconfigs": ["vendor/device_defconfig"]},
            {"source_defconfigs": ["gki_defconfig", "../escape"]},
            {"source_defconfigs": ["gki_defconfig", "vendor/./device_defconfig"]},
            {"source_kernel_version": "6.x"},
            {"source_kernel_version": None, "virt": "on"},
            {"os_patch_level": "2026-13"},
        )
        for override in cases:
            with self.subTest(override=override):
                client = RecordingGitHubClient(fork=None)
                source = {
                    "source_repo": "https://github.com/acme/kernel",
                    "source_ref": "main",
                    "source_defconfigs": ["gki_defconfig"],
                    "android_version": None,
                    "kernel_version": None,
                    "sub_level": None,
                    "os_patch_level": "2026-01",
                    "revision": None,
                    "ksu_variant": None,
                    "dry_run": True,
                }
                source.update(override)
                result, output = self._run_build(client, **source)

                self.assertEqual(2, result, output)
                self.assertEqual(0, client.create_fork_calls)
                self.assertEqual([], client.trigger_calls)

    def test_custom_source_susfs_defaults_off_but_can_be_enabled(self):
        common = {
            "source_repo": "https://github.com/acme/kernel",
            "source_ref": "main",
            "source_defconfigs": ["gki_defconfig"],
            "source_kernel_version": "5.10",
            "android_version": None,
            "kernel_version": None,
            "sub_level": None,
            "os_patch_level": "2026-01",
            "revision": None,
            "ksu_variant": "BakaSU",
            "dry_run": True,
        }
        for susfs, expected in ((None, "true"), (True, "false")):
            with self.subTest(susfs=susfs):
                client = RecordingGitHubClient(fork=None)
                result, output = self._run_build(client, susfs=susfs, **common)
                self.assertEqual(0, result, output)
                inputs_line = next(
                    line.strip().removeprefix("inputs=")
                    for line in output.splitlines()
                    if line.strip().startswith("inputs=")
                )
                self.assertEqual(
                    expected,
                    json.loads(inputs_line)["cancel_susfs"],
                )

    def test_custom_source_virtualization_uses_detected_build_profile(self):
        common = {
            "source_repo": "https://github.com/acme/kernel",
            "source_ref": "main",
            "source_defconfigs": ["gki_defconfig"],
            "android_version": None,
            "kernel_version": None,
            "sub_level": None,
            "os_patch_level": "2026-01",
            "revision": None,
            "ksu_variant": None,
            "dry_run": True,
        }
        cases = (
            ("5.10.177", "on", "678"),
            ("6.13.4", "123", "on"),
        )
        for version, requested, expected in cases:
            with self.subTest(version=version, requested=requested):
                client = RecordingGitHubClient(fork=None)
                result, output = self._run_build(
                    client,
                    source_kernel_version=version,
                    virt=requested,
                    **common,
                )
                self.assertEqual(0, result, output)
                inputs_line = next(
                    line.strip().removeprefix("inputs=")
                    for line in output.splitlines()
                    if line.strip().startswith("inputs=")
                )
                self.assertEqual(
                    expected,
                    json.loads(inputs_line)["virtualization_support"],
                )

    def test_custom_source_618_disables_susfs_and_bbg(self):
        client = RecordingGitHubClient(fork=None)
        result, output = self._run_build(
            client,
            source_repo="https://github.com/acme/kernel",
            source_ref="main",
            source_defconfigs=["gki_defconfig"],
            source_kernel_version="6.18.21",
            os_patch_level="2026-06",
            android_version=None,
            kernel_version=None,
            sub_level=None,
            revision=None,
            ksu_variant=None,
            susfs=True,
            bbg=True,
            dry_run=True,
        )

        self.assertEqual(0, result, output)
        inputs_line = next(
            line.strip().removeprefix("inputs=")
            for line in output.splitlines()
            if line.strip().startswith("inputs=")
        )
        inputs = json.loads(inputs_line)
        self.assertEqual("true", inputs["cancel_susfs"])
        self.assertEqual("false", inputs["use_bbg"])

    def test_private_custom_source_requires_configured_secret(self):
        fork = {"full_name": "alice/ABK", "name": "ABK", "owner": {"login": "alice"}}
        common = {
            "source_repo": "https://github.com/acme/private-kernel",
            "source_ref": "main",
            "source_private": True,
            "android_version": None,
            "kernel_version": None,
            "sub_level": None,
            "os_patch_level": "2026-01",
            "revision": None,
            "ksu_variant": None,
        }

        missing = RecordingGitHubClient(fork=fork, source_secret_exists=False)
        result, output = self._run_build(missing, **common)
        self.assertEqual(1, result, output)
        self.assertIn(abk.CUSTOM_SOURCE_SECRET_NAME, output)
        self.assertIn("source-secret set", output)
        self.assertEqual([], missing.trigger_calls)

        configured = RecordingGitHubClient(fork=fork, source_secret_exists=True)
        result, output = self._run_build(configured, **common)
        self.assertEqual(0, result, output)
        self.assertEqual(abk.CUSTOM_SOURCE_SECRET_NAME, configured.last_secret_name)
        self.assertEqual("kernel-source.yml", configured.trigger_calls[0]["workflow_file"])
        self.assertEqual("true", configured.trigger_calls[0]["inputs"]["source_private"])

    def test_kernel_options_and_current_time_semantics_reach_standard_builds(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            options_file = Path(temp_dir) / "kernel.config"
            options_file.write_text(
                "CONFIG_ALPHA=y\r\nCONFIG_BETA=n\r\n",
                encoding="utf-8",
            )
            client = RecordingGitHubClient(fork=None)
            result, output = self._run_build(
                client,
                kernel_options_file=str(options_file),
                build_time=None,
                dry_run=True,
            )

        self.assertEqual(0, result, output)
        self.assertIn('"build_time": ""', output)
        self.assertIn(
            '"custom_kernel_options": "CONFIG_ALPHA=y\\n# CONFIG_BETA is not set"',
            output,
        )

    def test_oversized_combined_workflow_inputs_fail_before_dispatch(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            options_file = Path(temp_dir) / "near-limit.config"
            options_file.write_text(
                "\n".join(
                    f"CONFIG_OPT_{index:03d}=" + ("x" * 495)
                    for index in range(128)
                ),
                encoding="utf-8",
            )
            self.assertLessEqual(
                options_file.stat().st_size,
                abk.MAX_KERNEL_OPTIONS_SIZE,
            )

            modes = (
                {},
                {"matrix": "full"},
                {
                    "source_repo": "https://github.com/acme/kernel",
                    "source_ref": "main",
                    "source_kernel_version": "6.12",
                    "android_version": None,
                    "kernel_version": None,
                    "sub_level": None,
                    "revision": None,
                    "os_patch_level": "2026-01",
                    "ksu_variant": None,
                },
            )
            for mode in modes:
                with self.subTest(mode=mode or {"target": "custom"}):
                    client = RecordingGitHubClient(fork=None)
                    result, output = self._run_build(
                        client,
                        kernel_options_file=str(options_file),
                        dry_run=True,
                        **mode,
                    )

                    self.assertEqual(2, result, output)
                    self.assertIn("workflow inputs", output)
                    self.assertIn(
                        str(abk.MAX_WORKFLOW_DISPATCH_INPUT_BYTES),
                        output,
                    )
                    self.assertEqual([], client.trigger_calls)
                    self.assertEqual(0, client.create_fork_calls)

    def test_build_enables_workflow_before_dispatch(self):
        fork = {"full_name": "alice/ABK", "name": "ABK", "owner": {"login": "alice"}}
        client = RecordingGitHubClient(fork=fork)
        client.ensure_workflow_active = mock.Mock()

        result, output = self._run_build(client)

        self.assertEqual(0, result, output)
        client.ensure_workflow_active.assert_called_once_with("kernel-custom.yml")
        self.assertEqual(1, len(client.trigger_calls))

    def test_build_output_hands_off_exact_run_commands(self):
        fork = {"full_name": "alice/ABK", "name": "ABK", "owner": {"login": "alice"}}
        client = RecordingGitHubClient(
            fork=fork,
            trigger_response={
                "workflow_run_id": 4242,
                "run_url": "https://api.github.test/repos/alice/ABK/actions/runs/4242",
                "html_url": "https://github.test/alice/ABK/actions/runs/4242",
            },
        )

        result, output = self._run_build(client)

        self.assertEqual(0, result, output)
        self.assertIn("#4242", output)
        self.assertIn("https://github.test/alice/ABK/actions/runs/4242", output)
        self.assertIn("abk --repo alice/ABK status --run-id 4242", output)
        self.assertIn(
            "abk --repo alice/ABK artifacts --run-id 4242 --download",
            output,
        )
        self.assertNotIn("test-token", output)

    def test_explicit_repo_build_never_switches_to_the_users_default_fork(self):
        client = RecordingGitHubClient(fork=None)
        client.repo = "org/custom-abk"

        result, output = self._run_build(
            client,
            repo="org/custom-abk",
        )

        self.assertEqual(0, result, output)
        self.assertEqual(0, client.create_fork_calls)
        self.assertEqual(0, client.check_behind_calls)
        self.assertEqual(0, client.sync_fork_calls)
        self.assertEqual(1, len(client.trigger_calls))
        self.assertEqual("org/custom-abk", client.trigger_calls[0]["repo"])

    def test_environment_repo_is_explicit_for_target_commands(self):
        with mock.patch.dict(os.environ, {"ABK_REPO": "org/environment-abk"}):
            build_client = RecordingGitHubClient(fork=None)
            build_client.repo = "org/environment-abk"
            result, output = self._run_build(build_client)

            self.assertEqual(0, result, output)
            self.assertEqual(0, build_client.create_fork_calls)
            self.assertEqual(0, build_client.check_behind_calls)
            self.assertEqual(
                "org/environment-abk",
                build_client.trigger_calls[0]["repo"],
            )

            status_client = RecordingGitHubClient(fork=None)
            status_client.repo = "org/environment-abk"
            result, output = self._run_status(status_client)

            self.assertEqual(0, result, output)
            self.assertEqual(0, status_client.check_behind_calls)
            self.assertEqual(1, len(status_client.list_runs_calls))

            with tempfile.TemporaryDirectory() as temp_dir:
                artifact_client = ArtifactGitHubClient(temp_dir)
                artifact_client.repo = "org/environment-abk"
                artifact_client.get_fork = mock.Mock(
                    side_effect=AssertionError("explicit repo must not probe a fork")
                )
                verification = {
                    "verified": True,
                    "status": "verified",
                    "message": "verified",
                    "bundles": [],
                }
                result, output = self._run_artifacts(
                    artifact_client,
                    verification,
                )

                self.assertEqual(0, result, output)
                artifact_client.get_fork.assert_not_called()

    def test_full_dry_run_uses_full_feature_defaults(self):
        client = RecordingGitHubClient(fork=None)
        defaulted = {
            "zram": None,
            "bbg": None,
            "ddk": None,
            "kpm": None,
            "susfs": None,
            "rekernel": None,
            "oneplus_8e": None,
            "ntsync": None,
            "networking": None,
            "zram_full_algo": None,
            "lz4kd": None,
            "bbr": None,
            "proxy_optimization": None,
            "unicode_bypass": None,
            "virt": None,
        }

        _, output = self._run_build(
            client,
            matrix="full",
            ksu_variant="BakaSU",
            dry_run=True,
            **defaulted,
        )

        for input_name in (
            "use_zram",
            "use_bbg",
            "use_ddk",
            "use_kpm",
            "use_rekernel",
            "use_ntsync",
            "use_networking",
            "zram_full_algo",
        ):
            self.assertIn(f'"{input_name}": "true"', output)
        self.assertEqual(0, client.create_fork_calls)

    def test_bakasu_dev_dry_run_disables_kpm(self):
        client = RecordingGitHubClient(fork=None)

        result, output = self._run_build(
            client,
            ksu_variant="BakaSU",
            ksu_branch="Dev",
            kpm=True,
            kpm_password="secret",
            dry_run=True,
        )

        self.assertEqual(0, result, output)
        self.assertIn('"use_kpm": "false"', output)
        self.assertNotIn('"kpm_password"', output)
        self.assertIn("BakaSU", output)

    def test_oneplus_bakasu_dry_run_enables_supported_kpm(self):
        client = RecordingGitHubClient(fork=None)

        result, output = self._run_build(
            client,
            oneplus=True,
            device="oneplus_12_b",
            ksu_variant="BakaSU",
            kpm=True,
            dry_run=True,
        )

        self.assertEqual(0, result, output)
        self.assertIn('"use_kpm": "true"', output)
        self.assertIn("BakaSU", output)

    def test_oneplus_15t_dry_run_dispatches_sm8850_android16(self):
        client = RecordingGitHubClient(fork=None)

        result, output = self._run_build(
            client,
            oneplus=True,
            device="oneplus_15t",
            ksu_variant="SukiSU",
            susfs=True,
            dry_run=True,
        )

        self.assertEqual(0, result, output)
        self.assertIn('"cpu": "sm8850"', output)
        self.assertIn('"android_version": "android16"', output)
        self.assertIn('"kernel_version": "6.12"', output)
        self.assertIn('"enable_susfs": "true"', output)

    def test_none_dry_runs_disable_susfs_for_direct_build_modes(self):
        cases = (
            ({"ksu_variant": "None", "dry_run": True}, '"cancel_susfs": "true"'),
            (
                {"matrix": "full", "ksu_variant": "None", "dry_run": True},
                '"enable_susfs": "false"',
            ),
            (
                {
                    "oneplus": True,
                    "device": "oneplus_12_b",
                    "ksu_variant": "None",
                    "dry_run": True,
                },
                '"enable_susfs": "false"',
            ),
        )
        for overrides, expected in cases:
            with self.subTest(overrides=overrides):
                client = RecordingGitHubClient(fork=None)
                result, output = self._run_build(client, **overrides)

                self.assertEqual(0, result, output)
                self.assertIn(expected, output)

    def test_a15_and_a16_dry_runs_forward_oneplus_8e_support(self):
        for matrix in ("a15", "a16"):
            with self.subTest(matrix=matrix):
                client = RecordingGitHubClient(fork=None)
                result, output = self._run_build(
                    client,
                    matrix=matrix,
                    oneplus_8e=True,
                    dry_run=True,
                )

                self.assertEqual(0, result, output)
                self.assertIn('"supp_op": "true"', output)

        client = RecordingGitHubClient(fork=None)
        result, output = self._run_build(
            client,
            matrix="a14",
            oneplus_8e=True,
            dry_run=True,
        )
        self.assertEqual(0, result, output)
        self.assertNotIn('"supp_op"', output)

    def test_existing_full_matrix_contract_rejects_custom_ref(self):
        client = RecordingGitHubClient(fork=None)

        result, output = self._run_build(
            client,
            matrix="full",
            ksu_branch="Custom",
            custom_ref="feature/test",
            dry_run=True,
        )

        self.assertEqual(2, result, output)
        self.assertEqual([], client.trigger_calls)

    def test_custom_workflow_forwards_custom_ref(self):
        client = RecordingGitHubClient(fork=None)

        result, output = self._run_build(
            client,
            ksu_branch="Custom",
            custom_ref="feature/test",
            dry_run=True,
        )

        self.assertEqual(0, result, output)
        self.assertIn('"custom_ref": "feature/test"', output)

    def test_a16_dry_run_normalizes_legacy_virtualization_slot(self):
        client = RecordingGitHubClient(fork=None)

        result, output = self._run_build(
            client,
            matrix="a16",
            virt="123",
            dry_run=True,
        )

        self.assertEqual(0, result, output)
        self.assertIn('"virtualization_support": "on"', output)
        self.assertNotIn('"virtualization_support": "123"', output)

    def test_old_kernel_dry_run_maps_common_virtualization_on_to_678(self):
        client = RecordingGitHubClient(fork=None)

        result, output = self._run_build(
            client,
            matrix="a14",
            virt="on",
            dry_run=True,
        )

        self.assertEqual(0, result, output)
        self.assertIn('"virtualization_support": "678"', output)

    def test_revision_is_rejected_when_selected_workflow_cannot_accept_it(self):
        for matrix in ("a12", "a13", "a16", "both"):
            with self.subTest(matrix=matrix):
                client = RecordingGitHubClient(fork=None)
                result, output = self._run_build(
                    client,
                    matrix=matrix,
                    revision="r99",
                    dry_run=True,
                )

                self.assertEqual(2, result, output)
                self.assertIn("--revision", output)
                self.assertEqual([], client.trigger_calls)

    def test_custom_non_510_revision_is_rejected(self):
        client = RecordingGitHubClient(fork=None)

        result, output = self._run_build(
            client,
            android_version="android14",
            kernel_version="6.1",
            revision="r99",
            dry_run=True,
        )

        self.assertEqual(2, result, output)
        self.assertIn("--revision", output)
        self.assertEqual([], client.trigger_calls)

    def test_custom_revision_is_normalized_and_validated(self):
        client = RecordingGitHubClient(fork=None)

        result, output = self._run_build(
            client,
            revision="R11",
            dry_run=True,
        )

        self.assertEqual(0, result, output)
        self.assertIn('"revision": "r11"', output)

        invalid_client = RecordingGitHubClient(fork=None)
        invalid_result, invalid_output = self._run_build(
            invalid_client,
            revision="FOO",
            dry_run=True,
        )
        self.assertEqual(2, invalid_result, invalid_output)
        self.assertEqual([], invalid_client.trigger_calls)

    def test_custom_lts_pair_is_accepted_and_must_be_complete(self):
        client = RecordingGitHubClient(fork=None)

        result, output = self._run_build(
            client,
            sub_level="x",
            os_patch_level="LTS",
            dry_run=True,
        )

        self.assertEqual(0, result, output)
        self.assertIn('"sub_level": "X"', output)
        self.assertIn('"os_patch_level": "lts"', output)

        for sub_level, patch_level in (("X", "2026-01"), ("101", "lts")):
            with self.subTest(sub_level=sub_level, patch_level=patch_level):
                invalid_client = RecordingGitHubClient(fork=None)
                invalid_result, invalid_output = self._run_build(
                    invalid_client,
                    sub_level=sub_level,
                    os_patch_level=patch_level,
                    dry_run=True,
                )
                self.assertEqual(2, invalid_result, invalid_output)
                self.assertEqual([], invalid_client.trigger_calls)

    def test_custom_android_kernel_pair_must_match_upstream_contract(self):
        client = RecordingGitHubClient(fork=None)

        result, output = self._run_build(
            client,
            android_version="android12",
            kernel_version="6.12",
            dry_run=True,
        )

        self.assertEqual(2, result, output)
        self.assertIn("unsupported Android/kernel combination", output)
        self.assertEqual([], client.trigger_calls)

    def test_unsafe_freeform_workflow_inputs_are_rejected(self):
        dangerous_values = {
            "ref": "dev\nMALICIOUS=value",
            "version": 'safe"; touch /tmp/abk-pwned; echo "',
            "revision": "r11;id",
            "build_time": "$(id)",
            "custom_modules": "https://example.test/repo$(id);after_patch",
            "custom_ref": "feature/test\nMALICIOUS=value",
            "sub_level": "101;id",
            "os_patch_level": "2026-01;id",
            "zram_extra_algos": "zstd,$(id)",
            "manager_variants": "Official,$(id)",
            "kpm_password": "secret\nMALICIOUS=value",
        }
        for argument, value in dangerous_values.items():
            with self.subTest(argument=argument):
                client = RecordingGitHubClient(fork=None)
                overrides = {argument: value, "dry_run": True}
                if argument == "custom_ref":
                    overrides["ksu_branch"] = "Custom"

                result, _ = self._run_build(client, **overrides)

                self.assertEqual(2, result)
                self.assertEqual(0, client.create_fork_calls)
                self.assertEqual([], client.trigger_calls)

    def test_dry_run_redacts_kpm_password(self):
        cases = (
            ("standard", {}),
            ("full", {"matrix": "full"}),
            (
                "all-managers",
                {"matrix": "all-managers", "manager_variants": "SukiSU"},
            ),
        )
        for label, overrides in cases:
            with self.subTest(mode=label):
                client = RecordingGitHubClient(fork=None)
                result, output = self._run_build(
                    client,
                    ksu_variant="SukiSU",
                    kpm=True,
                    kpm_password="safe-$password!",
                    dry_run=True,
                    **overrides,
                )

                self.assertEqual(0, result, output)
                self.assertNotIn("safe-$password!", output)
                self.assertIn('"kpm_password": "***"', output)

    def test_first_build_dispatches_to_newly_created_user_fork(self):
        client = RecordingGitHubClient(fork=None)

        result, output = self._run_build(client)

        self.assertEqual(0, result, output)
        self.assertEqual(1, client.create_fork_calls)
        self.assertEqual(1, len(client.trigger_calls))
        self.assertEqual("alice/ABK", client.trigger_calls[0]["repo"])
        self.assertIn("https://github.com/alice/ABK/actions", output)
        self.assertNotIn("--run-id None", output)

    def test_status_run_id_fetches_exact_run(self):
        fork = {"full_name": "alice/ABK", "name": "ABK", "owner": {"login": "alice"}}
        client = RecordingGitHubClient(fork=fork)

        self._run_status(client, run_id=4242, limit=3)

        self.assertEqual([4242], client.get_run_calls)
        self.assertEqual([], client.list_runs_calls)

    def test_status_target_and_status_filter_are_forwarded(self):
        fork = {"full_name": "alice/ABK", "name": "ABK", "owner": {"login": "alice"}}
        client = RecordingGitHubClient(fork=fork)

        self._run_status(client, target="a14", status="completed", limit=7)

        self.assertEqual(
            [
                {
                    "workflow_file": abk.WORKFLOWS["a14"]["file"],
                    "status": "completed",
                    "per_page": 7,
                }
            ],
            client.list_runs_calls,
        )

    def test_dispatch_failure_has_nonzero_command_outcome(self):
        fork = {"full_name": "alice/ABK", "name": "ABK", "owner": {"login": "alice"}}
        client = RecordingGitHubClient(
            fork=fork, trigger_error=RuntimeError("simulated dispatch failure")
        )
        output = io.StringIO()
        argv = [
            "abk",
            "--token",
            "test-token",
            "build",
            "--ref",
            "dev",
            "--ksu",
            "Official",
            "--sub-level",
            "101",
            "--os-patch-level",
            "2026-01",
        ]

        with contextlib.ExitStack() as stack:
            stack.enter_context(mock.patch.object(sys, "argv", argv))
            stack.enter_context(
                mock.patch.object(abk, "get_token", return_value="test-token")
            )
            stack.enter_context(mock.patch.object(abk, "GitHubClient", return_value=client))
            stack.enter_context(
                mock.patch.object(abk, "ensure_signing_key", return_value="test-key")
            )
            stack.enter_context(mock.patch.object(abk.time, "sleep", return_value=None))
            stack.enter_context(contextlib.redirect_stdout(output))
            stack.enter_context(contextlib.redirect_stderr(output))
            try:
                result = abk.main()
            except SystemExit as exc:
                self.assertNotIn(exc.code, (None, 0), output.getvalue())
            else:
                self.assertIsInstance(result, int, output.getvalue())
                self.assertNotEqual(0, result, output.getvalue())

    def test_human_dispatch_failure_redacts_kpm_password(self):
        password = "human-secret-password"
        fork = {"full_name": "alice/ABK", "name": "ABK", "owner": {"login": "alice"}}
        client = RecordingGitHubClient(
            fork=fork,
            trigger_error=RuntimeError(f"dispatch rejected {password}"),
        )

        result, output = self._run_build(
            client,
            ksu_variant="SukiSU",
            kpm=True,
            kpm_password=password,
        )

        self.assertEqual(1, result)
        self.assertNotIn(password, output)
        self.assertIn("***", output)
        self.assertEqual(
            password,
            client.trigger_calls[0]["inputs"]["kpm_password"],
        )

    def test_verified_artifact_download_succeeds(self):
        with tempfile.TemporaryDirectory() as output_dir:
            client = ArtifactGitHubClient(output_dir)
            verification = {
                "verified": True,
                "status": "verified",
                "message": "verified",
                "bundles": [
                    {
                        "verified": True,
                        "status": "verified",
                        "message": "verified",
                        "bundle": "kernel.bundle.zip",
                    }
                ],
            }

            result, _ = self._run_artifacts(client, verification)

            self.assertEqual(0, result)
            self.assertTrue((Path(output_dir) / "artifact-77.zip").exists())

    def test_rejected_unverified_artifact_is_deleted_and_fails(self):
        with tempfile.TemporaryDirectory() as output_dir:
            client = ArtifactGitHubClient(output_dir)
            verification = {
                "verified": False,
                "status": "unverified",
                "message": "unverified",
                "bundles": [
                    {
                        "verified": False,
                        "status": "unverified",
                        "message": "unverified",
                        "bundle": "kernel.bundle.zip",
                    }
                ],
            }

            result, _ = self._run_artifacts(client, verification, answer="n\n")

            self.assertEqual(1, result)
            self.assertFalse((Path(output_dir) / "artifact-77.zip").exists())

    def test_artifact_download_failure_returns_nonzero(self):
        with tempfile.TemporaryDirectory() as output_dir:
            client = ArtifactGitHubClient(
                output_dir,
                download_error=RuntimeError("simulated download failure"),
            )

            result, _ = self._run_artifacts(client, verification={})

            self.assertEqual(1, result)

    def test_artifact_help_uses_platform_default_download_dir(self):
        output = io.StringIO()
        argv = ["abk", "artifacts", "--help"]

        with mock.patch.object(sys, "argv", argv), contextlib.redirect_stdout(output):
            with self.assertRaises(SystemExit) as raised:
                abk.main()

        help_text = output.getvalue()
        self.assertEqual(0, raised.exception.code)
        self.assertIn(str(abk.default_download_dir()), help_text)
        self.assertNotIn("~/Downloads", help_text)
        self.assertNotIn("{dir}", help_text)


if __name__ == "__main__":
    unittest.main()
