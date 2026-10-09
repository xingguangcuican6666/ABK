import contextlib
import io
import json
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock


CLI_DIR = Path(__file__).resolve().parents[1]
if str(CLI_DIR) not in sys.path:
    sys.path.insert(0, str(CLI_DIR))

import abk  # noqa: E402


class ContractClient:
    token = "test-token"
    username = "alice"
    repo = "alice/ABK"
    fork_repo = None

    def __init__(self):
        self.downloaded_ids = []
        self.output_dir = None

    def get_user(self):
        return {"login": "alice"}

    def get_fork(self, owner=None, repo=None):
        return {
            "full_name": "alice/ABK",
            "name": "ABK",
            "owner": {"login": "alice"},
        }

    def check_behind(self, *args, **kwargs):
        return {"behind_by": 0, "ahead_by": 2, "status": "ahead"}

    def get_default_branch(self):
        return "dev"

    def list_runs(self, workflow_file=None, status=None, per_page=10):
        return {
            "workflow_runs": [{
                "id": 101,
                "name": abk.WORKFLOW_RUNTIME_NAMES["kernel-a14-6-1.yml"],
                "display_title": "Build Android 14",
                "status": "completed",
                "conclusion": "success",
                "event": "workflow_dispatch",
                "head_branch": "dev",
                "html_url": "https://github.test/alice/ABK/actions/runs/101",
                "created_at": "2026-07-16T00:00:00Z",
                "updated_at": "2026-07-16T00:02:00Z",
                "run_number": 7,
            }]
        }

    def get_run(self, run_id):
        return self.list_runs()["workflow_runs"][0] | {"id": run_id}

    def trigger_workflow(self, workflow_file, ref, inputs):
        return {
            "workflow_run_id": 4242,
            "run_url": "https://api.github.test/repos/alice/ABK/actions/runs/4242",
            "html_url": "https://github.test/alice/ABK/actions/runs/4242",
        }

    def list_artifacts(self, run_id):
        return {
            "total_count": 2,
            "artifacts": [
                {
                    "id": 77,
                    "name": "kernel-a",
                    "size_in_bytes": 128,
                    "expired": False,
                    "archive_download_url": "https://api.github.test/artifacts/77/zip",
                },
                {
                    "id": 78,
                    "name": "kernel-b",
                    "size_in_bytes": 256,
                    "expired": False,
                    "archive_download_url": "https://api.github.test/artifacts/78/zip",
                },
            ],
        }

    def download_artifact(self, artifact_id, output_dir):
        self.downloaded_ids.append(artifact_id)
        self.output_dir = Path(output_dir)
        path = self.output_dir / f"artifact-{artifact_id}.zip"
        path.write_bytes(b"artifact")
        return str(path)

    def get_published_signing_key(self):
        return None

    def repository_secret_exists(self, name):
        return False


class JsonContractTests(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp_dir.cleanup)
        config_dir = Path(self.temp_dir.name) / "config"
        self.config_patches = (
            mock.patch.object(abk, "CONFIG_DIR", config_dir),
            mock.patch.object(abk, "CONFIG_FILE", config_dir / "config.json"),
            mock.patch.dict(
                os.environ,
                {"GITHUB_TOKEN": "", "GH_TOKEN": "", "ABK_SIGNING_KEY": ""},
            ),
        )
        for patcher in self.config_patches:
            patcher.start()
            self.addCleanup(patcher.stop)

    def _run_main(self, argv):
        stdout = io.StringIO()
        stderr = io.StringIO()
        with (
            mock.patch.object(sys, "argv", argv),
            contextlib.redirect_stdout(stdout),
            contextlib.redirect_stderr(stderr),
        ):
            try:
                exit_code = abk.main()
            except SystemExit as exc:
                exit_code = exc.code
        output = stdout.getvalue()
        payload = json.loads(output)
        self.assertEqual(1, len(output.splitlines()), output)
        self.assertEqual(abk.CLI_VERSION, payload["cliVersion"])
        return exit_code, payload, stderr.getvalue()

    def test_human_version_flag_reports_the_cli_version(self):
        for argv in (
            ["abk", "--version"],
            ["abk", "--version", "--", "--json"],
        ):
            with self.subTest(argv=argv):
                stdout = io.StringIO()
                stderr = io.StringIO()
                with (
                    mock.patch.object(sys, "argv", argv),
                    contextlib.redirect_stdout(stdout),
                    contextlib.redirect_stderr(stderr),
                    self.assertRaises(SystemExit) as raised,
                ):
                    abk.main()

                self.assertEqual(0, raised.exception.code)
                self.assertEqual(f"abk {abk.CLI_VERSION}\n", stdout.getvalue())
                self.assertEqual("", stderr.getvalue())

    def test_json_version_flag_reports_one_machine_document(self):
        for argv in (
            ["abk", "--json", "--version"],
            ["abk", "--version", "--json"],
        ):
            with self.subTest(argv=argv):
                exit_code, payload, stderr = self._run_main(argv)

                self.assertEqual(0, exit_code)
                self.assertTrue(payload["ok"])
                self.assertEqual("version", payload["command"])
                self.assertIsNone(payload["error"])
                self.assertIsNone(payload["errorCode"])
                self.assertEqual("", stderr)

    def test_json_stdio_is_forced_to_utf8(self):
        stdout = mock.Mock()
        stderr = mock.Mock()
        with (
            mock.patch.object(sys, "argv", ["abk", "--json", "list"]),
            mock.patch.object(sys, "stdout", stdout),
            mock.patch.object(sys, "stderr", stderr),
        ):
            abk.configure_stdio()

        stdout.reconfigure.assert_called_once_with(
            errors="replace",
            encoding="utf-8",
            newline="\n",
        )
        stderr.reconfigure.assert_called_once_with(
            errors="replace",
            encoding="utf-8",
            newline="\n",
        )

    def test_whoami_logged_out_is_a_successful_machine_state(self):
        exit_code, payload, stderr = self._run_main(["abk", "--json", "whoami"])

        self.assertEqual(0, exit_code)
        self.assertTrue(payload["ok"])
        self.assertFalse(payload["loggedIn"])
        self.assertEqual(abk.DEFAULT_REPO, payload["repo"])
        self.assertIsNone(payload["user"])
        self.assertEqual("whoami", payload["command"])
        self.assertEqual("", stderr)

    def test_explicit_repo_whoami_preserves_user_identity_for_user_token(self):
        client = mock.Mock()
        client.authentication_error = None
        client.repo = "org/custom-abk"
        client.get.return_value = {"full_name": "org/custom-abk"}
        client.get_user.return_value = {"login": "alice"}
        with (
            mock.patch.object(abk, "make_client", return_value=client),
            mock.patch.object(
                abk,
                "_signing_key_metadata",
                return_value=(True, "repository"),
            ) as metadata,
        ):
            exit_code, payload, stderr = self._run_main([
                "abk",
                "--json",
                "--token",
                "test-token",
                "--repo",
                "org/custom-abk",
                "whoami",
            ])

        self.assertEqual(0, exit_code)
        self.assertEqual("org/custom-abk", payload["repo"])
        self.assertFalse(payload["needsFork"])
        self.assertFalse(payload["needsSync"])
        self.assertEqual({"login": "alice"}, payload["user"])
        self.assertIsNone(payload["fork"])
        self.assertEqual("repository", payload["signingKeySource"])
        client.get.assert_called_once_with("/repos/org/custom-abk")
        client.get_user.assert_called_once_with()
        client.get_fork.assert_not_called()
        client.check_behind.assert_not_called()
        metadata.assert_called_once_with("org/custom-abk", client)
        self.assertEqual("", stderr)

    def test_explicit_repo_whoami_supports_installation_token_without_user(self):
        client = mock.Mock()
        client.authentication_error = None
        client.repo = "org/custom-abk"
        client.get.return_value = {"full_name": "org/custom-abk"}
        client.get_user.side_effect = abk.GitHubAPIError(
            403,
            "resource not accessible by integration",
        )
        with (
            mock.patch.object(abk, "make_client", return_value=client),
            mock.patch.object(
                abk,
                "_signing_key_metadata",
                return_value=(False, None),
            ),
        ):
            exit_code, payload, stderr = self._run_main([
                "abk",
                "--json",
                "--token",
                "ghs_installation-token",
                "--repo",
                "org/custom-abk",
                "whoami",
            ])

        self.assertEqual(0, exit_code)
        self.assertTrue(payload["loggedIn"])
        self.assertIsNone(payload["user"])
        client.get.assert_called_once_with("/repos/org/custom-abk")
        client.get_user.assert_not_called()
        self.assertEqual("", stderr)

    def test_explicit_repo_whoami_maps_an_invalid_token_to_not_authenticated(self):
        client = mock.Mock()
        client.authentication_error = None
        client.repo = "org/custom-abk"
        client.get.side_effect = abk.GitHubAPIError(401, "bad credentials")
        with mock.patch.object(abk, "make_client", return_value=client):
            exit_code, payload, stderr = self._run_main([
                "abk",
                "--json",
                "--token",
                "expired-token",
                "--repo",
                "org/custom-abk",
                "whoami",
            ])

        self.assertEqual(1, exit_code)
        self.assertFalse(payload["ok"])
        self.assertEqual("not_authenticated", payload["errorCode"])
        client.get_user.assert_not_called()
        self.assertEqual("", stderr)

    def test_explicit_repo_whoami_maps_user_probe_401_to_not_authenticated(self):
        client = mock.Mock()
        client.authentication_error = None
        client.repo = "org/custom-abk"
        client.get.return_value = {"full_name": "org/custom-abk"}
        client.get_user.side_effect = abk.GitHubAPIError(401, "bad credentials")
        with mock.patch.object(abk, "make_client", return_value=client):
            exit_code, payload, stderr = self._run_main([
                "abk",
                "--json",
                "--token",
                "expired-user-token",
                "--repo",
                "org/custom-abk",
                "whoami",
            ])

        self.assertEqual(1, exit_code)
        self.assertFalse(payload["ok"])
        self.assertEqual("not_authenticated", payload["errorCode"])
        client.get.assert_called_once_with("/repos/org/custom-abk")
        client.get_user.assert_called_once_with()
        self.assertEqual("", stderr)

    def test_whoami_without_a_fork_does_not_adopt_upstream_signing_metadata(self):
        client = mock.Mock()
        client.authentication_error = None
        client.repo = abk.DEFAULT_REPO
        client.get_user.return_value = {"login": "alice"}
        client.get_fork.return_value = None
        with (
            mock.patch.object(abk, "make_client", return_value=client),
            mock.patch.object(abk, "_signing_key_metadata") as metadata,
        ):
            exit_code, payload, stderr = self._run_main([
                "abk",
                "--json",
                "--token",
                "test-token",
                "whoami",
            ])

        self.assertEqual(0, exit_code)
        self.assertTrue(payload["needsFork"])
        self.assertFalse(payload["signingKeyAvailable"])
        self.assertIsNone(payload["signingKeySource"])
        metadata.assert_not_called()
        self.assertEqual("", stderr)

    def test_repo_is_rejected_for_login_and_sync_before_side_effects(self):
        for command in ("login", "sync"):
            with self.subTest(command=command):
                with (
                    mock.patch.object(abk, "device_flow_login") as login,
                    mock.patch.object(abk, "make_client") as make_client,
                ):
                    exit_code, payload, stderr = self._run_main([
                        "abk",
                        "--json",
                        "--repo",
                        "org/custom-abk",
                        command,
                    ])

                self.assertEqual(2, exit_code)
                self.assertEqual("invalid_arguments", payload["errorCode"])
                login.assert_not_called()
                make_client.assert_not_called()
                self.assertEqual("", stderr)

    def test_environment_repo_is_rejected_for_sync_before_side_effects(self):
        with (
            mock.patch.dict(os.environ, {"ABK_REPO": "org/environment-abk"}),
            mock.patch.object(abk, "make_client") as make_client,
        ):
            exit_code, payload, stderr = self._run_main([
                "abk", "--json", "sync",
            ])

        self.assertEqual(2, exit_code)
        self.assertEqual("sync", payload["command"])
        self.assertEqual("invalid_arguments", payload["errorCode"])
        self.assertIn("ABK_REPO", payload["error"])
        make_client.assert_not_called()
        self.assertEqual("", stderr)

    def test_sync_success_reports_signing_lock_as_warning(self):
        class BehindForkClient(ContractClient):
            authentication_error = None

            def __init__(self):
                super().__init__()
                self.syncs = 0

            def check_behind(self, *args, **kwargs):
                return {"behind_by": 2, "ahead_by": 0, "status": "behind"}

            def sync_fork(self):
                self.syncs += 1

        client = BehindForkClient()
        with (
            mock.patch.object(abk, "make_client", return_value=client),
            mock.patch.object(
                abk,
                "ensure_signing_key",
                side_effect=abk.SigningStateIndeterminateError("repair signing state"),
            ),
        ):
            exit_code, payload, stderr = self._run_main([
                "abk", "--json", "--token", "test-token", "sync",
            ])

        self.assertEqual(0, exit_code)
        self.assertTrue(payload["ok"])
        self.assertTrue(payload["changed"])
        self.assertEqual(2, payload["behindByBefore"])
        self.assertTrue(payload["signingStateIndeterminate"])
        self.assertEqual(
            "signing_state_indeterminate",
            payload["warnings"][0]["code"],
        )
        self.assertEqual(1, client.syncs)
        self.assertEqual("", stderr)

    def test_fork_success_reports_signing_lock_as_warning(self):
        client = ContractClient()
        client.authentication_error = None
        client.repo = "org/custom-abk"
        with (
            mock.patch.object(abk, "make_client", return_value=client),
            mock.patch.object(
                abk,
                "ensure_signing_key",
                side_effect=abk.SigningStateIndeterminateError("repair signing state"),
            ),
        ):
            exit_code, payload, stderr = self._run_main([
                "abk", "--json", "--token", "test-token", "--repo",
                "org/custom-abk", "fork",
            ])

        self.assertEqual(0, exit_code)
        self.assertTrue(payload["ok"])
        self.assertEqual("org/custom-abk", payload["repo"])
        self.assertFalse(payload["created"])
        self.assertTrue(payload["signingStateIndeterminate"])
        self.assertEqual(
            "signing_state_indeterminate",
            payload["warnings"][0]["code"],
        )
        self.assertEqual("", stderr)

    def test_login_success_is_not_reversed_by_signing_lock(self):
        client = ContractClient()
        client.authentication_error = None
        client.check_and_prompt_sync = mock.Mock(return_value={"needs_fork": False})
        stdout = io.StringIO()
        stderr = io.StringIO()
        with (
            mock.patch.object(sys, "argv", ["abk", "login"]),
            mock.patch.object(abk, "device_flow_login", return_value="test-token"),
            mock.patch.object(abk, "make_client", return_value=client),
            mock.patch.object(
                abk,
                "ensure_signing_key",
                side_effect=abk.SigningStateIndeterminateError("repair signing state"),
            ),
            contextlib.redirect_stdout(stdout),
            contextlib.redirect_stderr(stderr),
        ):
            exit_code = abk.main()

        self.assertEqual(0, exit_code)
        self.assertEqual("test-token", abk.load_config()["token"])
        self.assertIn("repair signing state", stderr.getvalue())
        self.assertNotIn("Login/fork check failed", stderr.getvalue())

    def test_json_without_a_command_is_a_single_parser_error_document(self):
        exit_code, payload, stderr = self._run_main(["abk", "--json"])

        self.assertEqual(2, exit_code)
        self.assertFalse(payload["ok"])
        self.assertIsNone(payload["command"])
        self.assertEqual("invalid_arguments", payload["errorCode"])
        self.assertIn("command", payload["error"])
        self.assertEqual("", stderr)

    def test_json_parser_errors_redact_cli_secrets(self):
        secret = "parser-secret"
        exit_code, payload, stderr = self._run_main([
            "abk", "--json", "--token", secret, "--lang", secret, "whoami",
        ])

        self.assertEqual(2, exit_code)
        self.assertEqual("invalid_arguments", payload["errorCode"])
        self.assertNotIn(secret, json.dumps(payload))
        self.assertIn("***", payload["error"])
        self.assertEqual("", stderr)

    def test_human_parser_errors_redact_cli_secrets(self):
        secret = "human-parser-secret"
        stdout = io.StringIO()
        stderr = io.StringIO()
        with (
            mock.patch.object(
                sys,
                "argv",
                ["abk", "whoami", "--token", secret],
            ),
            contextlib.redirect_stdout(stdout),
            contextlib.redirect_stderr(stderr),
            self.assertRaises(SystemExit) as raised,
        ):
            abk.main()

        self.assertEqual(2, raised.exception.code)
        self.assertNotIn(secret, stdout.getvalue() + stderr.getvalue())
        self.assertIn("***", stderr.getvalue())

    def test_json_rejects_non_positive_run_and_artifact_ids(self):
        cases = (
            ["abk", "--json", "status", "--run-id", "0"],
            ["abk", "--json", "status", "--cancel", "-1"],
            ["abk", "--json", "status", "--rerun", "0"],
            ["abk", "--json", "artifacts", "--run-id", "-1"],
            ["abk", "--json", "artifacts", "--artifact-id", "0"],
        )
        for argv in cases:
            with self.subTest(argv=argv):
                exit_code, payload, stderr = self._run_main(argv)

                self.assertEqual(2, exit_code)
                self.assertEqual("invalid_arguments", payload["errorCode"])
                self.assertEqual(argv[2], payload["command"])
                self.assertIn("positive integer", payload["error"])
                self.assertEqual("", stderr)

    def test_json_choice_errors_report_the_selected_subcommand(self):
        exit_code, payload, stderr = self._run_main([
            "abk", "--json", "build", "--matrix", "not-a-target",
        ])

        self.assertEqual(2, exit_code)
        self.assertEqual("build", payload["command"])
        self.assertEqual("invalid_arguments", payload["errorCode"])
        self.assertEqual("", stderr)

    def test_logout_reports_credentials_that_remain_after_stored_token_removal(self):
        abk.save_config({"token": "stored-token"})
        with mock.patch.dict(os.environ, {"GITHUB_TOKEN": "environment-token"}):
            exit_code, payload, stderr = self._run_main([
                "abk", "--json", "logout",
            ])

        self.assertEqual(0, exit_code)
        self.assertTrue(payload["loggedIn"])
        self.assertTrue(payload["storedTokenRemoved"])
        self.assertNotIn("token", abk.load_config())
        self.assertEqual("", stderr)

    def test_logout_without_other_credentials_reports_logged_out(self):
        abk.save_config({"token": "stored-token"})

        exit_code, payload, stderr = self._run_main([
            "abk", "--json", "logout",
        ])

        self.assertEqual(0, exit_code)
        self.assertFalse(payload["loggedIn"])
        self.assertTrue(payload["storedTokenRemoved"])
        self.assertEqual("", stderr)

    def test_overlapping_secrets_are_fully_redacted(self):
        payload = abk._redact_json_secrets(
            {"error": "request exposed abcdef"},
            {"abc", "abcdef"},
        )

        self.assertEqual("request exposed ***", payload["error"])

    def test_sensitive_json_keys_are_redacted_without_secret_inventory(self):
        payload = abk._redact_json_secrets(
            {
                "token": "token-not-collected",
                "nested": {
                    "kpm_password": "password-not-collected",
                    "private_key": "private-key-not-collected",
                    "safe": "public-value",
                },
            },
            set(),
        )

        self.assertEqual("***", payload["token"])
        self.assertEqual("***", payload["nested"]["kpm_password"])
        self.assertEqual("***", payload["nested"]["private_key"])
        self.assertEqual("public-value", payload["nested"]["safe"])

    def test_login_json_never_starts_an_interactive_device_flow(self):
        with mock.patch.object(abk, "device_flow_login") as login:
            exit_code, payload, stderr = self._run_main(["abk", "--json", "login"])

        self.assertEqual(1, exit_code)
        self.assertFalse(payload["ok"])
        self.assertEqual("interaction_required", payload["errorCode"])
        self.assertEqual("", stderr)
        login.assert_not_called()

    def test_json_language_persistence_failure_stays_one_document(self):
        with mock.patch.object(
            abk,
            "_persist_requested_language",
            side_effect=OSError("config is read-only"),
        ):
            exit_code, payload, stderr = self._run_main([
                "abk", "--json", "--lang", "en-us", "list",
            ])

        self.assertEqual(1, exit_code)
        self.assertFalse(payload["ok"])
        self.assertEqual("unexpected_error", payload["errorCode"])
        self.assertIn("read-only", payload["error"])
        self.assertEqual("", stderr)

    def test_language_selected_with_help_is_persisted_before_argparse_exits(self):
        stdout = io.StringIO()
        stderr = io.StringIO()
        self.addCleanup(abk.refresh_workflow_names)
        self.addCleanup(abk.load_translations, "zh-CN")
        with (
            mock.patch.object(
                sys,
                "argv",
                ["abk", "--lang", "en-us", "--help"],
            ),
            contextlib.redirect_stdout(stdout),
            contextlib.redirect_stderr(stderr),
            self.assertRaises(SystemExit) as raised,
        ):
            abk.main()

        self.assertEqual(0, raised.exception.code)
        self.assertIn("usage:", stdout.getvalue())
        self.assertEqual("", stderr.getvalue())
        saved = json.loads(abk.CONFIG_FILE.read_text(encoding="utf-8"))
        self.assertEqual("en-us", saved["lang"])

    def test_help_uses_the_last_language_selected_before_the_help_flag(self):
        stdout = io.StringIO()
        stderr = io.StringIO()
        self.addCleanup(abk.refresh_workflow_names)
        self.addCleanup(abk.load_translations, "zh-CN")
        with (
            mock.patch.object(
                sys,
                "argv",
                [
                    "abk",
                    "--lang",
                    "en-us",
                    "--lang=fr-FR",
                    "--help",
                    "--lang",
                    "de-DE",
                ],
            ),
            contextlib.redirect_stdout(stdout),
            contextlib.redirect_stderr(stderr),
            self.assertRaises(SystemExit) as raised,
        ):
            abk.main()

        self.assertEqual(0, raised.exception.code)
        self.assertIn("Afficher cette aide et quitter", stdout.getvalue())
        self.assertEqual("", stderr.getvalue())
        saved = json.loads(abk.CONFIG_FILE.read_text(encoding="utf-8"))
        self.assertEqual("fr-fr", saved["lang"])

    def test_language_pre_scan_ignores_options_after_double_dash(self):
        self.assertEqual(
            "en-us",
            abk.requested_language([
                "--lang",
                "en-us",
                "--",
                "--lang",
                "fr-fr",
            ]),
        )

    def test_legacy_language_alias_uses_a_backward_compatible_storage_id(self):
        self.addCleanup(abk.refresh_workflow_names)
        self.addCleanup(abk.load_translations, "zh-CN")

        exit_code, payload, stderr = self._run_main([
            "abk",
            "--json",
            "--lang",
            "jp-neko",
            "list",
        ])

        self.assertEqual(0, exit_code)
        self.assertTrue(payload["ok"])
        self.assertEqual("", stderr)
        saved = json.loads(abk.CONFIG_FILE.read_text(encoding="utf-8"))
        self.assertEqual("jp-neko", saved["lang"])

    def test_explicit_language_does_not_fallback_from_an_invalid_tag(self):
        exit_code, payload, stderr = self._run_main([
            "abk",
            "--json",
            "--lang",
            "zh-zak0",
            "list",
        ])

        self.assertEqual(2, exit_code)
        self.assertFalse(payload["ok"])
        self.assertEqual("invalid_arguments", payload["errorCode"])
        self.assertFalse(abk.CONFIG_FILE.exists())
        self.assertEqual("", stderr)

    def test_build_preflight_errors_use_invalid_arguments_code(self):
        exit_code, payload, stderr = self._run_main([
            "abk",
            "--json",
            "build",
            "--matrix",
            "full",
            "--ksu-branch",
            "Custom",
        ])

        self.assertEqual(2, exit_code)
        self.assertFalse(payload["ok"])
        self.assertEqual("invalid_arguments", payload["errorCode"])
        self.assertEqual("", stderr)

    def test_build_without_token_is_not_authenticated(self):
        exit_code, payload, stderr = self._run_main([
            "abk",
            "--json",
            "build",
            "--matrix",
            "a14",
        ])

        self.assertEqual(1, exit_code)
        self.assertFalse(payload["ok"])
        self.assertEqual("not_authenticated", payload["errorCode"])
        self.assertEqual([], payload["dispatches"])
        self.assertEqual("", stderr)

    def test_invalid_token_is_not_reported_as_a_missing_fork(self):
        client = mock.Mock()
        client.authentication_error = abk.GitHubAPIError(401, "bad credentials")
        with mock.patch.object(abk, "make_client", return_value=client):
            exit_code, payload, stderr = self._run_main([
                "abk",
                "--json",
                "--token",
                "bad-token",
                "whoami",
            ])

        self.assertEqual(1, exit_code)
        self.assertEqual("not_authenticated", payload["errorCode"])
        self.assertNotEqual("fork_not_found", payload["errorCode"])
        self.assertEqual("", stderr)

    def test_forbidden_identity_probe_is_not_reported_as_logged_out(self):
        client = mock.Mock()
        client.authentication_error = abk.GitHubAPIError(403, "forbidden")
        with mock.patch.object(abk, "make_client", return_value=client):
            exit_code, payload, stderr = self._run_main([
                "abk",
                "--json",
                "--token",
                "test-token",
                "whoami",
            ])

        self.assertEqual(1, exit_code)
        self.assertEqual("authentication_failed", payload["errorCode"])
        self.assertEqual("", stderr)

    def test_authentication_errors_redact_the_clients_effective_token(self):
        token = "fresh-device-flow-token"
        client = mock.Mock()
        client.token = token
        client.authentication_error = RuntimeError(f"request echoed {token}")
        with mock.patch.object(abk, "make_client", return_value=client):
            exit_code, payload, stderr = self._run_main([
                "abk",
                "--json",
                "--token",
                token,
                "whoami",
            ])

        self.assertEqual(1, exit_code)
        self.assertNotIn(token, json.dumps(payload))
        self.assertIn("***", payload["error"])
        self.assertEqual("", stderr)

    def test_human_authentication_errors_redact_the_clients_effective_token(self):
        token = "human-device-flow-token"
        client = mock.Mock()
        client.token = token
        client.authentication_error = RuntimeError(f"request echoed {token}")
        stdout = io.StringIO()
        stderr = io.StringIO()
        with (
            mock.patch.object(abk, "make_client", return_value=client),
            mock.patch.object(
                sys,
                "argv",
                ["abk", "--token", token, "whoami"],
            ),
            contextlib.redirect_stdout(stdout),
            contextlib.redirect_stderr(stderr),
        ):
            exit_code = abk.main()

        rendered = stdout.getvalue() + stderr.getvalue()
        self.assertEqual(1, exit_code)
        self.assertNotIn(token, rendered)
        self.assertIn("***", rendered)

    def test_default_branch_failure_is_a_repository_setup_error(self):
        client = mock.Mock()
        client.authentication_error = None
        client.repo = "alice/ABK"
        client.get_default_branch.side_effect = RuntimeError("branch lookup failed")
        with (
            mock.patch.object(abk, "make_client", return_value=client),
            mock.patch.object(abk, "prepare_build_repository", return_value=True),
        ):
            exit_code, payload, stderr = self._run_main([
                "abk",
                "--json",
                "--token",
                "test-token",
                "build",
                "--matrix",
                "a14",
            ])

        self.assertEqual(1, exit_code)
        self.assertEqual("repository_setup_failed", payload["errorCode"])
        self.assertIn("branch lookup failed", payload["error"])
        self.assertEqual("", stderr)

    def test_build_dry_run_matches_machine_schema_and_redacts_password(self):
        argv = [
            "abk", "--json", "build", "--dry-run", "--matrix", "a14",
            "--ksu", "SukiSU", "--kpm", "--kpm-password", "contract-secret",
            "--force",
        ]
        exit_code, payload, stderr = self._run_main(argv)

        self.assertEqual(0, exit_code)
        self.assertTrue(payload["dryRun"])
        self.assertEqual(1, payload["total"])
        dispatch = payload["dispatches"][0]
        self.assertEqual("kernel-a14-6-1.yml", dispatch["workflowFile"])
        self.assertEqual("内核构建 - Android 14 (6.1)", dispatch["workflowName"])
        self.assertEqual("a14", dispatch["target"])
        self.assertEqual("SukiSU", dispatch["ksuVariant"])
        self.assertEqual("***", dispatch["inputs"]["kpm_password"])
        self.assertNotIn("contract-secret", json.dumps(payload))
        self.assertEqual("", stderr)

    def test_single_character_password_does_not_corrupt_structured_fields(self):
        argv = [
            "abk", "--json", "build", "--dry-run", "--matrix", "a14",
            "--ksu", "SukiSU", "--kpm", "--kpm-password", "a", "--force",
        ]
        exit_code, payload, stderr = self._run_main(argv)

        self.assertEqual(0, exit_code)
        dispatch = payload["dispatches"][0]
        self.assertEqual("kernel-a14-6-1.yml", dispatch["workflowFile"])
        self.assertEqual("内核构建 - Android 14 (6.1)", dispatch["workflowName"])
        self.assertEqual("a14", dispatch["target"])
        self.assertEqual("***", dispatch["inputs"]["kpm_password"])
        self.assertEqual("", stderr)

    def test_build_returns_precise_dispatch_run_details(self):
        client = ContractClient()
        argv = [
            "abk", "--json", "--repo", "alice/ABK", "build", "--matrix", "a14",
            "--ksu", "ReSukiSU", "--no-kpm", "--force",
        ]
        with (
            mock.patch.object(abk, "get_token", return_value="test-token"),
            mock.patch.object(abk, "GitHubClient", return_value=client),
            mock.patch.object(abk, "ensure_signing_key", return_value="public-key"),
        ):
            exit_code, payload, _ = self._run_main(argv)

        self.assertEqual(0, exit_code)
        dispatch = payload["dispatches"][0]
        self.assertEqual(4242, dispatch["runId"])
        self.assertIn("/runs/4242", dispatch["runUrl"])
        self.assertEqual(4242, payload["run"]["id"])
        self.assertEqual("completed", payload["run"]["status"])
        self.assertEqual(7, payload["run"]["runNumber"])

    def test_build_does_not_invent_run_state_when_detail_lookup_fails(self):
        client = ContractClient()
        client.get_run = mock.Mock(side_effect=RuntimeError("run not visible yet"))
        argv = [
            "abk", "--json", "--repo", "alice/ABK", "build", "--matrix", "a14",
            "--ksu", "ReSukiSU", "--no-kpm", "--force",
        ]
        with (
            mock.patch.object(abk, "get_token", return_value="test-token"),
            mock.patch.object(abk, "GitHubClient", return_value=client),
            mock.patch.object(abk, "ensure_signing_key", return_value="public-key"),
        ):
            exit_code, payload, _ = self._run_main(argv)

        self.assertEqual(0, exit_code)
        self.assertEqual(4242, payload["run"]["id"])
        self.assertEqual("", payload["run"]["status"])
        self.assertEqual(0, payload["run"]["runNumber"])

    def test_partial_dispatch_failure_reports_every_plan_without_secrets(self):
        class PartialFailureClient(ContractClient):
            def __init__(self):
                super().__init__()
                self.dispatch_count = 0

            def trigger_workflow(self, workflow_file, ref, inputs):
                self.dispatch_count += 1
                if self.dispatch_count == 2:
                    raise RuntimeError("dispatch rejected contract-secret")
                return {
                    "workflow_run_id": 5000 + self.dispatch_count,
                    "run_url": f"https://api.github.test/runs/{5000 + self.dispatch_count}",
                    "html_url": f"https://github.test/runs/{5000 + self.dispatch_count}",
                }

        client = PartialFailureClient()
        argv = [
            "abk", "--json", "--repo", "alice/ABK", "build", "--matrix", "both",
            "--ksu", "SukiSU", "--kpm", "--kpm-password", "contract-secret",
            "--force",
        ]
        with (
            mock.patch.object(abk, "get_token", return_value="test-token"),
            mock.patch.object(abk, "GitHubClient", return_value=client),
            mock.patch.object(abk, "ensure_signing_key", return_value="public-key"),
        ):
            exit_code, payload, stderr = self._run_main(argv)

        self.assertEqual(1, exit_code)
        self.assertFalse(payload["ok"])
        self.assertEqual(5, payload["total"])
        self.assertEqual(5, len(payload["dispatches"]))
        self.assertEqual(1, sum(item["status"] == "failed" for item in payload["dispatches"]))
        self.assertIsNone(payload["run"])
        self.assertEqual(4, len(payload["runs"]))
        self.assertNotIn("contract-secret", json.dumps(payload))
        self.assertNotIn("contract-secret", stderr)

    def test_list_oneplus_returns_machine_readable_devices(self):
        exit_code, payload, stderr = self._run_main([
            "abk", "--json", "list", "--oneplus",
        ])

        self.assertEqual(0, exit_code)
        self.assertEqual(len(abk.ONEPLUS_DEVICES), payload["total"])
        self.assertEqual(len(abk.ONEPLUS_DEVICES), len(payload["devices"]))
        self.assertIn("id", payload["devices"][0])
        self.assertIn("androidVersion", payload["devices"][0])
        self.assertEqual("", stderr)

    def test_self_test_reports_every_frozen_runtime_dependency(self):
        exit_code, payload, stderr = self._run_main([
            "abk", "--json", "self-test",
        ])

        self.assertEqual(0, exit_code)
        self.assertEqual(1, payload["schemaVersion"])
        self.assertTrue(payload["cryptoBackend"])
        self.assertTrue(payload["pynacl"])
        self.assertTrue(payload["caBundle"])
        self.assertTrue(payload["tlsContext"])
        self.assertEqual("", stderr)

    def test_status_normalizes_github_run_fields(self):
        client = ContractClient()
        with (
            mock.patch.object(abk, "get_token", return_value="test-token"),
            mock.patch.object(abk, "GitHubClient", return_value=client),
        ):
            exit_code, payload, _ = self._run_main(["abk", "--json", "status"])

        self.assertEqual(0, exit_code)
        self.assertEqual(1, payload["total"])
        run = payload["runs"][0]
        self.assertEqual("Build Android 14", run["displayTitle"])
        self.assertEqual("workflow_dispatch", run["event"])
        self.assertEqual("dev", run["headBranch"])
        self.assertEqual(7, run["runNumber"])

    def test_artifact_id_downloads_only_the_selected_artifact(self):
        client = ContractClient()
        output_dir = Path(self.temp_dir.name) / "downloads"
        verification = {
            "verified": True,
            "status": "verified",
            "message": "verified",
            "bundles": [],
        }
        argv = [
            "abk", "--json", "artifacts", "--run-id", "101", "--download",
            "--artifact-id", "78", "--output", str(output_dir),
        ]
        with (
            mock.patch.object(abk, "get_token", return_value="test-token"),
            mock.patch.object(abk, "GitHubClient", return_value=client),
            mock.patch.object(abk, "resolve_verification_key", return_value="key"),
            mock.patch.object(abk, "verify_artifact_archive", return_value=verification),
        ):
            exit_code, payload, _ = self._run_main(argv)

        self.assertEqual(0, exit_code)
        self.assertEqual([78], client.downloaded_ids)
        self.assertEqual([78], [item["id"] for item in payload["artifacts"]])
        self.assertTrue(Path(payload["downloads"][0]["path"]).is_file())

    def test_json_verification_failure_deletes_file_without_reading_stdin(self):
        client = ContractClient()
        output_dir = Path(self.temp_dir.name) / "unverified"
        verification = {
            "verified": False,
            "status": "unverified",
            "message": "signature missing",
            "bundles": [],
        }

        class RejectStdin:
            def readline(self):
                raise AssertionError("JSON mode must not read stdin")

        argv = [
            "abk", "--json", "artifacts", "--run-id", "101", "--download",
            "--artifact-id", "77", "--output", str(output_dir),
        ]
        with (
            mock.patch.object(abk, "get_token", return_value="test-token"),
            mock.patch.object(abk, "GitHubClient", return_value=client),
            mock.patch.object(abk, "resolve_verification_key", return_value="key"),
            mock.patch.object(abk, "verify_artifact_archive", return_value=verification),
            mock.patch.object(sys, "stdin", RejectStdin()),
        ):
            exit_code, payload, _ = self._run_main(argv)

        self.assertEqual(1, exit_code)
        self.assertEqual("artifact_verification_failed", payload["errorCode"])
        self.assertIsNone(payload["downloads"][0]["path"])
        self.assertFalse((output_dir / "artifact-77.zip").exists())

    def test_disabled_verification_keeps_download_and_skips_verifier(self):
        client = ContractClient()
        output_dir = Path(self.temp_dir.name) / "verification-disabled"
        abk._save_signing_disabled_state({}, "alice/ABK")
        argv = [
            "abk", "--json", "artifacts", "--run-id", "101", "--download",
            "--artifact-id", "77", "--output", str(output_dir),
        ]
        with (
            mock.patch.object(abk, "get_token", return_value="test-token"),
            mock.patch.object(abk, "GitHubClient", return_value=client),
            mock.patch.object(abk, "verify_artifact_archive") as verify,
        ):
            exit_code, payload, _ = self._run_main(argv)

        self.assertEqual(0, exit_code)
        self.assertFalse(payload["verificationEnabled"])
        self.assertEqual("disabled", payload["downloads"][0]["verification"]["status"])
        self.assertTrue(Path(payload["downloads"][0]["path"]).is_file())
        self.assertIsNone(payload["downloads"][0]["error"])
        verify.assert_not_called()


class GitHubClientContractTests(unittest.TestCase):
    def test_explicit_repository_client_skips_identity_probe(self):
        with mock.patch.object(abk.GitHubClient, "_detect_user") as detect:
            client = abk.GitHubClient(
                token="installation-token",
                repo="org/ABK",
            )

        detect.assert_not_called()
        self.assertEqual("org/ABK", client.repo)
        self.assertIsNone(client.username)
        self.assertIsNone(client.authentication_error)

    def test_environment_repository_client_skips_identity_probe(self):
        with (
            mock.patch.dict(os.environ, {"ABK_REPO": "org/ABK"}),
            mock.patch.object(abk.GitHubClient, "_detect_user") as detect,
        ):
            client = abk.GitHubClient(token="installation-token")

        detect.assert_not_called()
        self.assertEqual("org/ABK", client.repo)
        self.assertTrue(client.repo_explicit)
        self.assertIsNone(client.authentication_error)

    def test_fork_probe_failure_does_not_undo_successful_authentication(self):
        client = object.__new__(abk.GitHubClient)
        client.repo = abk.DEFAULT_REPO
        client.repo_explicit = False
        client.username = None
        client.fork_repo = None
        client.authentication_error = None
        client.fork_detection_error = None
        client.get = mock.Mock(side_effect=[
            {"login": "alice"},
            RuntimeError("fork lookup unavailable"),
            {
                "fork": True,
                "full_name": "alice/ABK",
                "parent": {"full_name": abk.DEFAULT_REPO},
            },
        ])

        client._detect_user(detect_fork=True)

        self.assertEqual("alice", client.username)
        self.assertIsNone(client.authentication_error)
        self.assertIsInstance(client.fork_detection_error, RuntimeError)
        self.assertIsNone(client.fork_repo)

        fork = client.get_fork()

        self.assertEqual("alice/ABK", fork["full_name"])
        self.assertEqual("alice/ABK", client.repo)
        self.assertEqual(fork, client.fork_repo)

    def test_artifact_listing_fetches_every_page(self):
        client = object.__new__(abk.GitHubClient)
        client.repo = "alice/ABK"
        first = [{"id": value} for value in range(1, 101)]
        second = [{"id": value} for value in range(101, 166)]
        client.get = mock.Mock(side_effect=[
            {"total_count": 165, "artifacts": first},
            {"total_count": 165, "artifacts": second},
        ])

        result = client.list_artifacts(999)

        self.assertEqual(165, len(result["artifacts"]))
        self.assertIn("per_page=100", client.get.call_args_list[0].args[0])
        self.assertIn("page=2", client.get.call_args_list[1].args[0])

    def test_artifact_listing_deduplicates_shifted_pages_without_omissions(self):
        client = object.__new__(abk.GitHubClient)
        client.repo = "alice/ABK"
        first = [{"id": value} for value in range(1, 101)]
        shifted_second = [{"id": value} for value in range(100, 200)]
        final = [{"id": 200}]
        client.get = mock.Mock(side_effect=[
            {"total_count": 200, "artifacts": first},
            {"total_count": 201, "artifacts": shifted_second},
            {"total_count": 201, "artifacts": final},
        ])

        result = client.list_artifacts(999)

        ids = [artifact["id"] for artifact in result["artifacts"]]
        self.assertEqual(list(range(1, 201)), ids)
        self.assertEqual(200, result["total_count"])
        self.assertEqual(3, client.get.call_count)

    def test_workflow_dispatch_requests_run_details(self):
        client = object.__new__(abk.GitHubClient)
        client.repo = "alice/ABK"
        client.post = mock.Mock(return_value={"workflow_run_id": 42})

        result = client.trigger_workflow("kernel-a14-6-1.yml", "dev", {"x": "y"})

        self.assertEqual(42, result["workflow_run_id"])
        payload = client.post.call_args.args[1]
        self.assertIs(True, payload["return_run_details"])


if __name__ == "__main__":
    unittest.main()
