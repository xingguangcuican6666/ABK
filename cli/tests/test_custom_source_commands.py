import contextlib
import io
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


class SourceSecretClient:
    token = "login-token"
    username = "alice"
    repo = "alice/ABK"
    fork_repo = None
    authentication_error = None

    def __init__(self, configured=False):
        self.configured = configured
        self.updates = []
        self.deletes = []
        self.checks = []

    def get_fork(self, owner=None, repo=None):
        return {
            "full_name": "alice/ABK",
            "name": "ABK",
            "owner": {"login": "alice"},
        }

    def repository_secret_exists(self, name):
        self.checks.append(name)
        self.last_checked = name
        return self.configured

    def create_or_update_secret(self, name, value):
        self.updates.append((name, value))
        self.configured = True
        return True

    def delete_repository_secret(self, name):
        self.deletes.append(name)
        self.configured = False


def secret_args(**overrides):
    values = {
        "token": "login-token",
        "repo": None,
        "verbose": False,
        "json": False,
        "source_secret_action": "status",
        "source_token_file": None,
        "use_login_token": False,
        "yes": False,
    }
    values.update(overrides)
    return SimpleNamespace(**values)


class SourceSecretCommandTests(unittest.TestCase):
    def setUp(self):
        patcher = mock.patch.dict(os.environ, {"ABK_CUSTOM_SOURCE_TOKEN": ""})
        patcher.start()
        self.addCleanup(patcher.stop)

    def run_command(self, client, **overrides):
        args = secret_args(**overrides)
        output = io.StringIO()
        with (
            mock.patch.object(abk, "get_token", return_value="login-token"),
            mock.patch.object(abk, "make_client", return_value=client),
            contextlib.redirect_stdout(output),
            contextlib.redirect_stderr(output),
        ):
            result = abk.cmd_source_secret(args)
        return result, output.getvalue(), args

    def test_set_reads_file_and_never_prints_private_token(self):
        private_token = "github_pat_private_source_example"
        with tempfile.TemporaryDirectory() as temp_dir:
            token_file = Path(temp_dir) / "source.token"
            token_file.write_text(private_token + "\n", encoding="utf-8")
            client = SourceSecretClient(configured=False)

            result, output, _ = self.run_command(
                client,
                source_secret_action="set",
                source_token_file=str(token_file),
            )

        self.assertEqual(0, result, output)
        self.assertEqual(
            [(abk.CUSTOM_SOURCE_SECRET_NAME, private_token)],
            client.updates,
        )
        self.assertEqual([], client.checks)
        self.assertNotIn(private_token, output)
        self.assertIn("configured", output)

    def test_set_can_explicitly_reuse_login_token(self):
        client = SourceSecretClient(configured=False)

        result, output, _ = self.run_command(
            client,
            source_secret_action="set",
            use_login_token=True,
        )

        self.assertEqual(0, result, output)
        self.assertEqual(
            [(abk.CUSTOM_SOURCE_SECRET_NAME, "login-token")],
            client.updates,
        )
        self.assertNotIn("login-token", output)

    def test_json_set_without_explicit_token_source_never_prompts(self):
        client = SourceSecretClient(configured=False)
        with mock.patch.object(abk.getpass, "getpass") as prompt:
            result, output, args = self.run_command(
                client,
                source_secret_action="set",
                json=True,
            )

        self.assertEqual(2, result, output)
        prompt.assert_not_called()
        self.assertEqual("invalid_arguments", args._json_result["errorCode"])
        self.assertEqual([], client.updates)

    def test_json_set_rejects_stdin_without_reading_it(self):
        client = SourceSecretClient(configured=False)
        stdin = mock.Mock()
        stdin.read.side_effect = AssertionError("stdin must not be read")
        with mock.patch.object(abk.sys, "stdin", stdin):
            result, output, args = self.run_command(
                client,
                source_secret_action="set",
                source_token_file="-",
                json=True,
            )

        self.assertEqual(2, result, output)
        stdin.read.assert_not_called()
        self.assertEqual("invalid_arguments", args._json_result["errorCode"])
        self.assertEqual([], client.updates)
        self.assertEqual([], client.checks)

    def test_status_and_idempotent_delete_report_repository_state(self):
        configured = SourceSecretClient(configured=True)
        result, output, args = self.run_command(
            configured,
            source_secret_action="status",
            json=True,
        )
        self.assertEqual(0, result, output)
        self.assertTrue(args._json_result["configured"])

        result, output, args = self.run_command(
            configured,
            source_secret_action="delete",
            yes=True,
            json=True,
        )
        self.assertEqual(0, result, output)
        self.assertEqual([abk.CUSTOM_SOURCE_SECRET_NAME], configured.deletes)
        self.assertFalse(args._json_result["configured"])
        self.assertTrue(args._json_result["changed"])

        result, output, args = self.run_command(
            configured,
            source_secret_action="delete",
            yes=True,
            json=True,
        )
        self.assertEqual(0, result, output)
        self.assertFalse(args._json_result["changed"])


class WorkflowActivationTests(unittest.TestCase):
    def test_disabled_workflow_is_enabled_and_rechecked(self):
        client = object.__new__(abk.GitHubClient)
        client.repo = "alice/ABK"
        client.get = mock.Mock(
            side_effect=[
                {"id": 77, "state": "disabled_fork"},
                {"id": 77, "state": "active"},
            ]
        )
        client.put = mock.Mock(return_value={})

        with mock.patch.object(abk.time, "sleep") as sleep:
            result = client.ensure_workflow_active("kernel-source.yml")

        self.assertEqual("active", result["state"])
        sleep.assert_called_once_with(1)
        client.put.assert_called_once_with(
            "/repos/alice/ABK/actions/workflows/77/enable"
        )

    def test_workflow_enablement_retries_eventual_github_state(self):
        client = object.__new__(abk.GitHubClient)
        client.repo = "alice/ABK"
        client.get = mock.Mock(
            side_effect=[
                {"id": 77, "state": "disabled_fork"},
                {"id": 77, "state": "disabled_fork"},
                {"id": 77, "state": "active"},
            ]
        )
        client.put = mock.Mock(return_value={})

        with mock.patch.object(abk.time, "sleep") as sleep:
            result = client.ensure_workflow_active("kernel-source.yml")

        self.assertEqual("active", result["state"])
        self.assertEqual(2, sleep.call_count)

    def test_manually_disabled_workflow_is_never_overridden(self):
        client = object.__new__(abk.GitHubClient)
        client.repo = "alice/ABK"
        client.get = mock.Mock(return_value={"id": 77, "state": "disabled_manually"})
        client.put = mock.Mock(return_value={})

        with self.assertRaisesRegex(RuntimeError, "enable it explicitly"):
            client.ensure_workflow_active("kernel-source.yml")

        client.put.assert_not_called()

    def test_workflow_enablement_fails_closed_when_state_stays_disabled(self):
        client = object.__new__(abk.GitHubClient)
        client.repo = "alice/ABK"
        client.get = mock.Mock(
            side_effect=[
                {"id": 77, "state": "disabled_fork"},
                {"id": 77, "state": "disabled_fork"},
                {"id": 77, "state": "disabled_fork"},
                {"id": 77, "state": "disabled_fork"},
            ]
        )
        client.put = mock.Mock(return_value={})

        with (
            mock.patch.object(abk.time, "sleep") as sleep,
            self.assertRaisesRegex(RuntimeError, "still disabled"),
        ):
            client.ensure_workflow_active("kernel-source.yml")
        self.assertEqual(3, sleep.call_count)


class DispatchDetailsTests(unittest.TestCase):
    def test_nested_workflow_run_is_supported_for_human_handoff(self):
        response = {
            "workflow_run": {
                "id": 4242,
                "url": "https://api.github.test/runs/4242",
                "html_url": "https://github.test/runs/4242",
            }
        }

        self.assertEqual(
            (
                4242,
                "https://api.github.test/runs/4242",
                "https://github.test/runs/4242",
            ),
            abk._dispatch_run_details(response),
        )


class KernelOptionsFileTests(unittest.TestCase):
    def test_invalid_line_is_rejected_with_line_number(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            path = Path(temp_dir) / "bad.config"
            path.write_text("CONFIG_VALID=y\nnot a config\n", encoding="utf-8")

            with self.assertRaisesRegex(ValueError, "line 2"):
                abk.load_custom_kernel_options(path)

    def test_oversized_file_is_rejected_before_reading(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            path = Path(temp_dir) / "large.config"
            path.write_bytes(b"x" * (abk.MAX_KERNEL_OPTIONS_SIZE + 1))

            with self.assertRaisesRegex(ValueError, "exceeds"):
                abk.load_custom_kernel_options(path)

    def test_make_expansion_is_rejected_from_raw_values(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            path = Path(temp_dir) / "unsafe.config"
            path.write_text('CONFIG_LOCALVERSION="$(id)"\n', encoding="utf-8")

            with self.assertRaisesRegex(ValueError, "invalid kernel option value"):
                abk.load_custom_kernel_options(path)

    def test_app_escaped_quotes_and_backslashes_are_decoded(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            path = Path(temp_dir) / "escaped.config"
            path.write_text(
                'CONFIG_LOCALVERSION=\\"-abk\\"\n'
                'CONFIG_PATH="foo\\\\bar"\n',
                encoding="utf-8",
            )

            result = abk.load_custom_kernel_options(path)

        self.assertEqual(
            'CONFIG_LOCALVERSION="-abk"\nCONFIG_PATH="foo\\bar"',
            result,
        )

    def test_bare_symbol_matches_app_ignore_and_removes_prior_value(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            path = Path(temp_dir) / "ignore.config"
            path.write_text(
                "CONFIG_DROP=y\nCONFIG_KEEP=m\nCONFIG_DROP\n",
                encoding="utf-8",
            )

            result = abk.load_custom_kernel_options(path)

        self.assertEqual("CONFIG_KEEP=m", result)


if __name__ == "__main__":
    unittest.main()
