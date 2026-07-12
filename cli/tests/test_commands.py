import contextlib
import io
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
    def __init__(self, *, fork=None, trigger_error=None):
        self.token = "test-token"
        self.username = "alice"
        self.repo = fork["full_name"] if fork else abk.DEFAULT_REPO
        self.fork_repo = fork
        self._fork = fork
        self.trigger_error = trigger_error
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
        return {}

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
                    "name": "ReSukiSU_kernel-test",
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
            ksu_variant="ReSukiSU",
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

    def test_resukisu_dev_dry_run_disables_kpm(self):
        client = RecordingGitHubClient(fork=None)

        result, output = self._run_build(
            client,
            ksu_variant="ReSukiSU",
            ksu_branch="Dev",
            kpm=True,
            kpm_password="secret",
            dry_run=True,
        )

        self.assertEqual(0, result, output)
        self.assertIn('"use_kpm": "false"', output)
        self.assertNotIn('"kpm_password"', output)
        self.assertIn("ReSukiSU", output)

    def test_oneplus_resukisu_dry_run_disables_kpm(self):
        client = RecordingGitHubClient(fork=None)

        result, output = self._run_build(
            client,
            oneplus=True,
            device="oneplus_12_b",
            ksu_variant="ReSukiSU",
            kpm=True,
            dry_run=True,
        )

        self.assertEqual(0, result, output)
        self.assertIn('"use_kpm": "false"', output)
        self.assertIn("ReSukiSU", output)

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
        client = RecordingGitHubClient(fork=None)

        result, output = self._run_build(
            client,
            ksu_variant="SukiSU",
            kpm=True,
            kpm_password="safe-$password!",
            dry_run=True,
        )

        self.assertEqual(0, result, output)
        self.assertNotIn("safe-$password!", output)
        self.assertIn('"kpm_password": "***"', output)

    def test_first_build_dispatches_to_newly_created_user_fork(self):
        client = RecordingGitHubClient(fork=None)

        self._run_build(client)

        self.assertEqual(1, client.create_fork_calls)
        self.assertEqual(1, len(client.trigger_calls))
        self.assertEqual("alice/ABK", client.trigger_calls[0]["repo"])

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
