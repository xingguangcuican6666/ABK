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


class SigningCommandClient:
    token = "test-token"
    repo = "alice/ABK"
    repo_explicit = True
    authentication_error = None

    def __init__(self, *, secret_exists=False, published_key=None):
        self.secret_exists = secret_exists
        self.published_key = published_key
        self.events = []

    def get(self, path):
        if path == "/repos/alice/ABK":
            return {"full_name": self.repo}
        raise AssertionError(path)

    def get_published_signing_key(self):
        return self.published_key

    def repository_secret_exists(self, name):
        return self.secret_exists

    def publish_signing_key(self, value):
        self.events.append("public")
        self.published_key = value
        return True

    def replace_published_signing_key(
        self,
        value,
        *,
        expected_previous_key=None,
    ):
        self.events.append("public_replace")
        self.published_key = value
        return True

    def delete_published_signing_key(self):
        self.events.append("public_delete")
        previous = self.published_key
        self.published_key = None
        return previous

    def create_or_update_secret(self, name, value):
        self.events.append("secret")
        self.secret_exists = True
        return True

    def delete_repository_secret(self, name):
        self.events.append("secret_delete")
        self.secret_exists = False


class SigningCommandTests(unittest.TestCase):
    def setUp(self):
        if not abk._CRYPTO_BACKEND:
            self.skipTest("RSA backend unavailable")
        self.temp_dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp_dir.cleanup)
        config_dir = Path(self.temp_dir.name) / "config"
        self.patches = (
            mock.patch.object(abk, "CONFIG_DIR", config_dir),
            mock.patch.object(abk, "CONFIG_FILE", config_dir / "config.json"),
            mock.patch.dict(
                os.environ,
                {
                    "GITHUB_TOKEN": "",
                    "GH_TOKEN": "",
                    "ABK_SIGNING_KEY": "",
                },
            ),
        )
        for patcher in self.patches:
            patcher.start()
            self.addCleanup(patcher.stop)

    def _pem_pair(self):
        private_b64, public_pem = abk.generate_signing_keypair()
        wrapped = "\n".join(
            private_b64[index:index + 64]
            for index in range(0, len(private_b64), 64)
        )
        private_pem = (
            "-----BEGIN PRIVATE KEY-----\n"
            f"{wrapped}\n"
            "-----END PRIVATE KEY-----\n"
        )
        public_path = Path(self.temp_dir.name) / f"public-{len(list(Path(self.temp_dir.name).glob('public-*')))}.pem"
        private_path = Path(self.temp_dir.name) / f"private-{len(list(Path(self.temp_dir.name).glob('private-*')))}.pem"
        public_path.write_text(public_pem, encoding="utf-8")
        private_path.write_text(private_pem, encoding="utf-8")
        return private_b64, public_pem, private_pem, public_path, private_path

    def _run_json(self, argv, client):
        stdout = io.StringIO()
        stderr = io.StringIO()
        with (
            mock.patch.object(sys, "argv", argv),
            mock.patch.object(abk, "get_token", return_value="test-token"),
            mock.patch.object(abk, "GitHubClient", return_value=client),
            contextlib.redirect_stdout(stdout),
            contextlib.redirect_stderr(stderr),
        ):
            try:
                exit_code = abk.main()
            except SystemExit as exc:
                exit_code = exc.code
        output = stdout.getvalue()
        self.assertEqual(1, len(output.splitlines()), output)
        return exit_code, json.loads(output), stderr.getvalue()

    def test_status_reports_repo_scoped_remote_state(self):
        _, public_pem, _, _, _ = self._pem_pair()
        client = SigningCommandClient(secret_exists=True, published_key=public_pem)

        exit_code, payload, stderr = self._run_json(
            ["abk", "--json", "--repo", "alice/ABK", "signing", "status"],
            client,
        )

        self.assertEqual(0, exit_code)
        self.assertEqual("present_unverified", payload["signingState"])
        self.assertTrue(payload["verificationEnabled"])
        self.assertTrue(payload["signingKeyConfigured"])
        self.assertFalse(payload["localStateIndeterminate"])
        self.assertRegex(payload["publicKeyFingerprint"], r"\A[0-9a-f]{64}\Z")
        self.assertEqual("", stderr)

    def test_status_reports_local_indeterminate_safety_lock(self):
        _, public_pem, _, _, _ = self._pem_pair()
        abk._save_signing_indeterminate_state({}, "alice/ABK")
        client = SigningCommandClient(secret_exists=True, published_key=public_pem)

        exit_code, payload, stderr = self._run_json(
            ["abk", "--json", "--repo", "alice/ABK", "signing", "status"],
            client,
        )

        self.assertEqual(0, exit_code)
        self.assertTrue(payload["localStateIndeterminate"])
        self.assertFalse(payload["signingReady"])
        self.assertEqual("present_unverified", payload["signingState"])
        self.assertEqual("", stderr)

    def test_invalid_signing_action_uses_json_argument_error_contract(self):
        client = SigningCommandClient()

        exit_code, payload, _ = self._run_json(
            ["abk", "--json", "signing", "unknown"],
            client,
        )

        self.assertEqual(2, exit_code)
        self.assertEqual("signing", payload["command"])
        self.assertEqual("invalid_arguments", payload["errorCode"])

    def test_status_rejects_action_inapplicable_dry_run_flag(self):
        client = SigningCommandClient()

        exit_code, payload, _ = self._run_json(
            [
                "abk", "--json", "--repo", "alice/ABK", "signing", "status",
                "--dry-run",
            ],
            client,
        )

        self.assertEqual(2, exit_code)
        self.assertEqual("invalid_arguments", payload["errorCode"])

    def test_argument_validation_runs_before_authentication_or_remote_calls(self):
        stdout = io.StringIO()
        stderr = io.StringIO()
        with (
            mock.patch.object(
                sys,
                "argv",
                ["abk", "--json", "signing", "status", "--dry-run"],
            ),
            mock.patch.object(abk, "get_token") as get_token,
            mock.patch.object(abk, "GitHubClient") as github_client,
            contextlib.redirect_stdout(stdout),
            contextlib.redirect_stderr(stderr),
        ):
            exit_code = abk.main()

        payload = json.loads(stdout.getvalue())
        self.assertEqual(2, exit_code)
        self.assertEqual("invalid_arguments", payload["errorCode"])
        self.assertEqual("status", payload["action"])
        get_token.assert_not_called()
        github_client.assert_not_called()
        self.assertEqual("", stderr.getvalue())

    def test_import_dry_run_validates_files_without_writing_remote_or_config(self):
        _, _, private_pem, public_path, private_path = self._pem_pair()
        client = SigningCommandClient()

        exit_code, payload, stderr = self._run_json(
            [
                "abk", "--json", "--repo", "alice/ABK", "signing", "import",
                "--public-key-file", str(public_path),
                "--private-key-file", str(private_path),
                "--dry-run",
            ],
            client,
        )

        self.assertEqual(0, exit_code)
        self.assertTrue(payload["dryRun"])
        self.assertTrue(payload["changed"])
        self.assertTrue(payload["verificationEnabled"])
        self.assertTrue(payload["signingKeyConfigured"])
        self.assertTrue(payload["signingReady"])
        self.assertEqual([], client.events)
        self.assertFalse(abk.CONFIG_FILE.exists())
        serialized = json.dumps(payload)
        self.assertNotIn(private_pem, serialized)
        self.assertNotIn("PRIVATE KEY", serialized)
        self.assertEqual("", stderr)

    def test_import_requires_both_key_files(self):
        client = SigningCommandClient()

        exit_code, payload, _ = self._run_json(
            [
                "abk", "--json", "--repo", "alice/ABK", "signing", "import",
                "--public-key-file", "public.pem",
            ],
            client,
        )

        self.assertEqual(2, exit_code)
        self.assertEqual("invalid_arguments", payload["errorCode"])
        self.assertEqual([], client.events)

    def test_json_import_rotation_requires_yes_without_reading_stdin(self):
        _, old_public, _, _, _ = self._pem_pair()
        _, _, _, public_path, private_path = self._pem_pair()
        client = SigningCommandClient(secret_exists=True, published_key=old_public)

        class RejectStdin:
            def readline(self):
                raise AssertionError("JSON signing command must not read stdin")

        with mock.patch.object(sys, "stdin", RejectStdin()):
            exit_code, payload, _ = self._run_json(
                [
                    "abk", "--json", "--repo", "alice/ABK", "signing", "import",
                    "--public-key-file", str(public_path),
                    "--private-key-file", str(private_path),
                ],
                client,
            )

        self.assertEqual(1, exit_code)
        self.assertEqual("confirmation_required", payload["errorCode"])
        self.assertEqual([], client.events)

    def test_rotate_dry_run_does_not_generate_or_report_ephemeral_key(self):
        _, public_key, _, _, _ = self._pem_pair()
        client = SigningCommandClient(secret_exists=True, published_key=public_key)

        with mock.patch.object(abk, "generate_signing_keypair") as generate:
            exit_code, payload, stderr = self._run_json(
                [
                    "abk", "--json", "--repo", "alice/ABK", "signing", "rotate",
                    "--dry-run",
                ],
                client,
            )

        self.assertEqual(0, exit_code)
        self.assertTrue(payload["dryRun"])
        self.assertTrue(payload["willGenerateKey"])
        self.assertTrue(payload["signingKeyConfigured"])
        self.assertTrue(payload["signingReady"])
        self.assertIsNone(payload["publicKeyFingerprint"])
        self.assertEqual([], client.events)
        generate.assert_not_called()
        self.assertEqual("", stderr)

    def test_import_with_yes_rotates_pair_without_exposing_private_material(self):
        _, old_public, _, _, _ = self._pem_pair()
        private_b64, _, private_pem, public_path, private_path = self._pem_pair()
        client = SigningCommandClient(secret_exists=True, published_key=old_public)

        exit_code, payload, stderr = self._run_json(
            [
                "abk", "--json", "--repo", "alice/ABK", "signing", "import",
                "--public-key-file", str(public_path),
                "--private-key-file", str(private_path),
                "--yes",
            ],
            client,
        )

        self.assertEqual(0, exit_code)
        self.assertEqual(["secret_delete", "public_replace", "secret"], client.events)
        self.assertTrue(payload["invalidatedPreviousBundles"])
        output = json.dumps(payload)
        self.assertNotIn(private_b64, output)
        self.assertNotIn(private_pem, output)
        self.assertNotIn("PRIVATE KEY", output)
        self.assertNotIn(private_b64, abk.CONFIG_FILE.read_text(encoding="utf-8"))
        self.assertEqual("", stderr)

    def test_import_repairs_malformed_remote_public_key(self):
        _, _, _, public_path, private_path = self._pem_pair()
        client = SigningCommandClient(
            secret_exists=True,
            published_key="not a PEM public key",
        )

        exit_code, payload, stderr = self._run_json(
            [
                "abk", "--json", "--repo", "alice/ABK", "signing", "import",
                "--public-key-file", str(public_path),
                "--private-key-file", str(private_path),
                "--yes",
            ],
            client,
        )

        self.assertEqual(0, exit_code)
        self.assertEqual(["secret_delete", "public_replace", "secret"], client.events)
        self.assertTrue(payload["signingReady"])
        self.assertEqual("", stderr)

    def test_status_reports_malformed_remote_public_key_without_crashing(self):
        client = SigningCommandClient(
            secret_exists=True,
            published_key="not a PEM public key",
        )

        exit_code, payload, stderr = self._run_json(
            ["abk", "--json", "--repo", "alice/ABK", "signing", "status"],
            client,
        )

        self.assertEqual(0, exit_code)
        self.assertEqual("invalid_public_key", payload["signingState"])
        self.assertFalse(payload["signingKeyConfigured"])
        self.assertFalse(payload["signingReady"])
        self.assertIsNone(payload["publicKeyFingerprint"])
        self.assertEqual("", stderr)

    def test_status_treats_empty_public_asset_as_invalid_not_absent(self):
        client = SigningCommandClient(
            secret_exists=False,
            published_key="   \n",
        )

        exit_code, payload, stderr = self._run_json(
            ["abk", "--json", "--repo", "alice/ABK", "signing", "status"],
            client,
        )

        self.assertEqual(0, exit_code)
        self.assertEqual("invalid_public_key", payload["signingState"])
        self.assertFalse(payload["signingKeyConfigured"])
        self.assertIsNone(payload["publicKeyFingerprint"])
        self.assertEqual("", stderr)

    def test_disable_with_yes_persists_preference_and_removes_remote_material(self):
        _, public_pem, _, _, _ = self._pem_pair()
        client = SigningCommandClient(secret_exists=True, published_key=public_pem)

        exit_code, payload, stderr = self._run_json(
            [
                "abk", "--json", "--repo", "alice/ABK", "signing", "disable",
                "--yes",
            ],
            client,
        )

        self.assertEqual(0, exit_code)
        self.assertEqual(["secret_delete", "public_delete"], client.events)
        self.assertFalse(payload["verificationEnabled"])
        self.assertFalse(payload["signingKeyConfigured"])
        self.assertFalse(abk.signing_verification_enabled("alice/ABK"))
        self.assertEqual("", stderr)

    def test_disable_dry_run_reports_projected_disabled_state(self):
        _, public_pem, _, _, _ = self._pem_pair()
        client = SigningCommandClient(secret_exists=True, published_key=public_pem)

        exit_code, payload, stderr = self._run_json(
            [
                "abk", "--json", "--repo", "alice/ABK", "signing", "disable",
                "--dry-run",
            ],
            client,
        )

        self.assertEqual(0, exit_code)
        self.assertFalse(payload["verificationEnabled"])
        self.assertFalse(payload["signingKeyConfigured"])
        self.assertFalse(payload["signingReady"])
        self.assertIsNone(payload["publicKeyFingerprint"])
        self.assertEqual(
            abk.signing_key_fingerprint(public_pem),
            payload["previousPublicKeyFingerprint"],
        )
        self.assertEqual([], client.events)
        self.assertTrue(client.secret_exists)
        self.assertEqual(public_pem, client.published_key)
        self.assertEqual("", stderr)

    def test_disable_reports_change_when_removing_empty_public_asset(self):
        abk._save_signing_disabled_state({}, "alice/ABK")
        client = SigningCommandClient(published_key=" \n")

        exit_code, payload, stderr = self._run_json(
            [
                "abk", "--json", "--repo", "alice/ABK", "signing", "disable",
                "--yes",
            ],
            client,
        )

        self.assertEqual(0, exit_code)
        self.assertTrue(payload["changed"])
        self.assertEqual(["public_delete"], client.events)
        self.assertIsNone(client.published_key)
        self.assertEqual("", stderr)

    def test_enable_repairs_absent_remote_material_and_flips_preference_last(self):
        abk._save_signing_disabled_state({}, "alice/ABK")
        client = SigningCommandClient()

        exit_code, payload, stderr = self._run_json(
            ["abk", "--json", "--repo", "alice/ABK", "signing", "enable"],
            client,
        )

        self.assertEqual(0, exit_code)
        self.assertEqual(["public_replace", "secret"], client.events)
        self.assertTrue(payload["verificationEnabled"])
        self.assertTrue(payload["signingReady"])
        self.assertTrue(abk.signing_verification_enabled("alice/ABK"))
        self.assertEqual("", stderr)

    def test_enable_dry_run_reports_projected_ready_state(self):
        abk._save_signing_disabled_state({}, "alice/ABK")
        client = SigningCommandClient()

        exit_code, payload, stderr = self._run_json(
            [
                "abk", "--json", "--repo", "alice/ABK", "signing", "enable",
                "--dry-run",
            ],
            client,
        )

        self.assertEqual(0, exit_code)
        self.assertTrue(payload["verificationEnabled"])
        self.assertTrue(payload["signingKeyConfigured"])
        self.assertTrue(payload["signingReady"])
        self.assertTrue(payload["willGenerateKey"])
        self.assertEqual([], client.events)
        self.assertFalse(abk.signing_verification_enabled("alice/ABK"))
        self.assertEqual("", stderr)

    def test_install_aborts_when_absent_status_is_replaced_by_android_pair(self):
        _, concurrent_public, _, _, _ = self._pem_pair()

        class AndroidPairAppearsAfterStatus(SigningCommandClient):
            def __init__(self):
                super().__init__()
                self.public_reads = 0

            def get_published_signing_key(self):
                self.public_reads += 1
                if self.public_reads == 2:
                    self.published_key = concurrent_public
                    self.secret_exists = True
                return self.published_key

        for action in ("rotate", "enable"):
            with self.subTest(action=action):
                if action == "enable":
                    abk._save_signing_disabled_state({}, "alice/ABK")
                client = AndroidPairAppearsAfterStatus()

                exit_code, payload, stderr = self._run_json(
                    [
                        "abk", "--json", "--repo", "alice/ABK", "signing", action,
                    ],
                    client,
                )

                self.assertEqual(1, exit_code)
                self.assertEqual("signing_operation_failed", payload["errorCode"])
                self.assertIn("changed after it was inspected", payload["error"])
                self.assertEqual([], client.events)
                self.assertTrue(client.secret_exists)
                self.assertEqual(concurrent_public, client.published_key)
                state = abk._get_signing_state(abk.load_config(), "alice/ABK")
                self.assertIsNot(state.get("indeterminate"), True)
                self.assertEqual("", stderr)

    def test_enable_adopts_existing_material_without_claiming_pair_validation(self):
        _, public_pem, _, _, _ = self._pem_pair()
        abk._save_signing_disabled_state({}, "alice/ABK")
        client = SigningCommandClient(
            secret_exists=True,
            published_key=public_pem,
        )

        exit_code, payload, stderr = self._run_json(
            ["abk", "--json", "--repo", "alice/ABK", "signing", "enable"],
            client,
        )

        self.assertEqual(0, exit_code)
        self.assertEqual([], client.events)
        self.assertTrue(payload["verificationEnabled"])
        self.assertTrue(payload["signingKeyConfigured"])
        self.assertIsNone(payload["signingReady"])
        self.assertTrue(abk.signing_verification_enabled("alice/ABK"))
        self.assertEqual("", stderr)

    def test_enable_refuses_partial_remote_state_without_replacing_keys(self):
        _, public_pem, _, _, _ = self._pem_pair()
        abk._save_signing_disabled_state({}, "alice/ABK")
        client = SigningCommandClient(
            secret_exists=False,
            published_key=public_pem,
        )

        exit_code, payload, stderr = self._run_json(
            ["abk", "--json", "--repo", "alice/ABK", "signing", "enable"],
            client,
        )

        self.assertEqual(1, exit_code)
        self.assertEqual("signing_operation_failed", payload["errorCode"])
        self.assertIn("partial remote signing state", payload["error"])
        self.assertEqual([], client.events)
        self.assertFalse(abk.signing_verification_enabled("alice/ABK"))
        self.assertEqual("", stderr)

    def test_enable_failure_keeps_disabled_preference(self):
        abk._save_signing_disabled_state({}, "alice/ABK")
        client = SigningCommandClient()

        with mock.patch.object(
            abk,
            "install_signing_keypair",
            side_effect=RuntimeError("simulated enable failure"),
        ):
            exit_code, payload, _ = self._run_json(
                ["abk", "--json", "--repo", "alice/ABK", "signing", "enable"],
                client,
            )

        self.assertEqual(1, exit_code)
        self.assertEqual("signing_operation_failed", payload["errorCode"])
        self.assertFalse(abk.signing_verification_enabled("alice/ABK"))


if __name__ == "__main__":
    unittest.main()
