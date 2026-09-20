import base64
import contextlib
import errno
import io
import json
import os
import subprocess
import sys
import tempfile
import threading
import unittest
from concurrent.futures import ThreadPoolExecutor
from email.message import Message
from pathlib import Path
from unittest import mock
from urllib.error import HTTPError


CLI_DIR = Path(__file__).resolve().parents[1]
if str(CLI_DIR) not in sys.path:
    sys.path.insert(0, str(CLI_DIR))

import abk  # noqa: E402


class SigningClient:
    token = "test-token"

    def __init__(self, *, repo="alice/ABK", secret_exists=False, published_key=None):
        self.repo = repo
        self.secret_exists = secret_exists
        self.published_key = published_key
        self.secret_updates = []
        self.publications = []
        self.secret_deletes = []
        self.events = []

    def repository_secret_exists(self, name):
        return self.secret_exists

    def get_published_signing_key(self):
        return self.published_key

    def create_or_update_secret(self, name, value):
        self.events.append("secret")
        self.secret_updates.append((name, value))
        self.secret_exists = True
        return True

    def publish_signing_key(self, value):
        self.events.append("public")
        self.publications.append(value)
        self.published_key = value
        return True

    def replace_published_signing_key(
        self,
        value,
        *,
        expected_previous_key=None,
    ):
        self.events.append("public_replace")
        self.publications.append(value)
        self.published_key = value
        return True

    def delete_published_signing_key(self):
        self.events.append("public_delete")
        previous = self.published_key
        self.published_key = None
        return previous

    def delete_repository_secret(self, name):
        self.events.append("secret_delete")
        self.secret_deletes.append(name)
        self.secret_exists = False


class SecurityRegressionTests(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp_dir.cleanup)
        self.config_dir = Path(self.temp_dir.name) / "config"
        self.config_file = self.config_dir / "config.json"
        self.config_patches = (
            mock.patch.object(abk, "CONFIG_DIR", self.config_dir),
            mock.patch.object(abk, "CONFIG_FILE", self.config_file),
            mock.patch.dict(os.environ, {}, clear=False),
        )
        for patcher in self.config_patches:
            patcher.start()
            self.addCleanup(patcher.stop)
        os.environ.pop("ABK_SIGNING_KEY", None)

    def _generated_pem_pair(self):
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
        return private_b64, public_pem, private_pem

    def test_signing_setup_uploads_private_secret_and_publishes_only_public_key(self):
        if not abk._CRYPTO_BACKEND:
            self.skipTest("RSA backend unavailable")
        client = SigningClient()

        with contextlib.redirect_stdout(io.StringIO()):
            public_key = abk.ensure_signing_key(client)

        self.assertEqual(abk.SIGNING_SECRET_NAME, client.secret_updates[0][0])
        private_material = base64.b64decode(client.secret_updates[0][1])
        if abk._CRYPTO_BACKEND == "cryptography":
            from cryptography.hazmat.primitives import serialization

            private_key = serialization.load_der_private_key(
                private_material,
                password=None,
            )
            self.assertEqual(2048, private_key.key_size)
        else:
            self.assertTrue(abk.RSA.import_key(private_material).has_private())
        self.assertEqual([public_key], client.publications)
        self.assertEqual(["public", "secret"], client.events)
        config = abk.load_config()
        state = config[abk.SIGNING_STATE_CONFIG_KEY]["alice/abk"]
        self.assertEqual(public_key, state["public_key"])
        self.assertEqual(abk.SIGNING_SECRET_NAME, state["secret_name"])
        self.assertNotIn("PRIVATE KEY", self.config_file.read_text(encoding="utf-8"))
        if os.name != "nt":
            self.assertEqual(0o700, self.config_dir.stat().st_mode & 0o777)
            self.assertEqual(0o600, self.config_file.stat().st_mode & 0o777)
            self.assertEqual(
                0o600,
                (self.config_dir / abk.CONFIG_LOCK_FILE).stat().st_mode & 0o777,
            )

    def test_custom_signing_key_import_normalizes_android_compatible_material(self):
        if not abk._CRYPTO_BACKEND:
            self.skipTest("RSA backend unavailable")
        expected_private, public_pem, private_pem = self._generated_pem_pair()

        private_b64, normalized_public, fingerprint = abk.load_signing_keypair(
            public_pem,
            private_pem,
        )

        self.assertEqual(expected_private, private_b64)
        self.assertEqual(
            abk.normalize_signing_public_key(public_pem).strip(),
            normalized_public.strip(),
        )
        self.assertRegex(fingerprint, r"\A[0-9a-f]{64}\Z")

    def test_custom_signing_key_import_supports_pycryptodome_fallback(self):
        try:
            from Cryptodome.PublicKey import RSA as fallback_rsa
        except ImportError:
            try:
                from Crypto.PublicKey import RSA as fallback_rsa
            except ImportError:
                self.skipTest("PyCryptodome unavailable")
        private_key = fallback_rsa.generate(2048)
        private_der = private_key.export_key("DER", pkcs=8)
        private_b64 = base64.b64encode(private_der).decode("ascii")
        wrapped = "\n".join(
            private_b64[index:index + 64]
            for index in range(0, len(private_b64), 64)
        )
        private_pem = (
            "-----BEGIN PRIVATE KEY-----\n"
            f"{wrapped}\n"
            "-----END PRIVATE KEY-----\n"
        )
        public_pem = private_key.publickey().export_key("PEM").decode("ascii")

        with (
            mock.patch.object(abk, "_CRYPTO_BACKEND", "pycryptodome"),
            mock.patch.object(abk, "RSA", fallback_rsa, create=True),
        ):
            normalized_private, normalized_public, fingerprint = (
                abk.load_signing_keypair(public_pem, private_pem)
            )
            renormalized_public = abk.normalize_signing_public_key(
                normalized_public
            )

        self.assertTrue(base64.b64decode(normalized_private))
        self.assertIn("BEGIN PUBLIC KEY", normalized_public)
        self.assertTrue(normalized_public.endswith("\n"))
        self.assertEqual(normalized_public, renormalized_public)
        self.assertRegex(fingerprint, r"\A[0-9a-f]{64}\Z")

    def test_signing_setup_canonicalizes_pycryptodome_publication(self):
        try:
            from Cryptodome.PublicKey import RSA as fallback_rsa
        except ImportError:
            try:
                from Crypto.PublicKey import RSA as fallback_rsa
            except ImportError:
                self.skipTest("PyCryptodome unavailable")
        client = SigningClient()

        with (
            mock.patch.object(abk, "_CRYPTO_BACKEND", "pycryptodome"),
            mock.patch.object(abk, "RSA", fallback_rsa, create=True),
            contextlib.redirect_stdout(io.StringIO()),
        ):
            public_key = abk.ensure_signing_key(client)

        self.assertTrue(public_key.endswith("\n"))
        self.assertEqual([public_key], client.publications)

    def test_custom_signing_key_import_rejects_mismatched_pair(self):
        if not abk._CRYPTO_BACKEND:
            self.skipTest("RSA backend unavailable")
        _, public_pem, _ = self._generated_pem_pair()
        _, _, other_private_pem = self._generated_pem_pair()

        with self.assertRaisesRegex(ValueError, "do not match"):
            abk.load_signing_keypair(public_pem, other_private_pem)

    def test_custom_signing_key_import_rejects_non_pkcs8_pem_label(self):
        if not abk._CRYPTO_BACKEND:
            self.skipTest("RSA backend unavailable")
        _, public_pem, private_pem = self._generated_pem_pair()
        private_pem = private_pem.replace("PRIVATE KEY", "RSA PRIVATE KEY")

        with self.assertRaisesRegex(ValueError, "unencrypted PRIVATE KEY"):
            abk.load_signing_keypair(public_pem, private_pem)

    def test_custom_signing_key_import_rejects_rsa_smaller_than_2048_bits(self):
        if not abk._CRYPTO_BACKEND:
            self.skipTest("RSA backend unavailable")
        if abk._CRYPTO_BACKEND == "cryptography":
            from cryptography.hazmat.primitives import serialization
            from cryptography.hazmat.primitives.asymmetric import rsa

            private_key = rsa.generate_private_key(public_exponent=65537, key_size=1024)
            private_pem = private_key.private_bytes(
                serialization.Encoding.PEM,
                serialization.PrivateFormat.PKCS8,
                serialization.NoEncryption(),
            ).decode("ascii")
            public_pem = private_key.public_key().public_bytes(
                serialization.Encoding.PEM,
                serialization.PublicFormat.SubjectPublicKeyInfo,
            ).decode("ascii")
        else:
            private_key = abk.RSA.generate(1024)
            private_der = private_key.export_key("DER", pkcs=8)
            private_b64 = base64.b64encode(private_der).decode("ascii")
            wrapped = "\n".join(
                private_b64[index:index + 64]
                for index in range(0, len(private_b64), 64)
            )
            private_pem = (
                "-----BEGIN PRIVATE KEY-----\n"
                f"{wrapped}\n"
                "-----END PRIVATE KEY-----\n"
            )
            public_pem = private_key.publickey().export_key("PEM").decode("ascii")

        with self.assertRaisesRegex(ValueError, "at least 2048 bits"):
            abk.load_signing_keypair(public_pem, private_pem)

    def test_install_custom_signing_key_rotates_public_then_secret_and_saves_no_private(self):
        if not abk._CRYPTO_BACKEND:
            self.skipTest("RSA backend unavailable")
        _, old_public, _ = self._generated_pem_pair()
        private_b64, public_pem, private_pem = self._generated_pem_pair()
        imported_private, imported_public, fingerprint = abk.load_signing_keypair(
            public_pem,
            private_pem,
        )
        self.assertEqual(private_b64, imported_private)
        client = SigningClient(secret_exists=True, published_key=old_public)

        result = abk.install_signing_keypair(
            client,
            imported_private,
            imported_public,
        )

        self.assertEqual(["secret_delete", "public_replace", "secret"], client.events)
        self.assertEqual(fingerprint, result["fingerprint"])
        self.assertTrue(result["changed"])
        config_text = self.config_file.read_text(encoding="utf-8")
        self.assertNotIn(imported_private, config_text)
        self.assertNotIn("PRIVATE KEY", config_text)
        state = abk.load_config()[abk.SIGNING_STATE_CONFIG_KEY]["alice/abk"]
        self.assertTrue(state["verification_enabled"])
        self.assertEqual(imported_public, state["public_key"])

    def test_install_custom_signing_key_rejects_stale_environment_override_before_writes(self):
        if not abk._CRYPTO_BACKEND:
            self.skipTest("RSA backend unavailable")
        _, old_public, _ = self._generated_pem_pair()
        private_b64, new_public, _ = self._generated_pem_pair()
        client = SigningClient(secret_exists=True, published_key=old_public)

        with mock.patch.dict(os.environ, {"ABK_SIGNING_KEY": old_public}):
            with self.assertRaisesRegex(RuntimeError, "conflicts"):
                abk.install_signing_keypair(client, private_b64, new_public)

        self.assertEqual([], client.events)

    def test_public_asset_failed_upload_does_not_delete_concurrent_replacement(self):
        class ConcurrentAssetClient:
            def __init__(self):
                self.assets = [{
                    "id": 1,
                    "name": abk.SIGNING_PUBLIC_KEY_ASSET,
                    "url": "old",
                }]
                self.asset_text = {"old": "OLD", "concurrent": "CONCURRENT"}
                self.events = []

            def get_release_by_tag(self, tag):
                return {"id": 10}

            def list_release_assets(self, release_id):
                self.events.append("list")
                return list(self.assets)

            def delete_release_asset(self, asset_id):
                self.events.append(f"delete:{asset_id}")
                self.assets = [asset for asset in self.assets if asset["id"] != asset_id]

            def _upload_signing_key_asset(self, release, public_key_pem):
                self.events.append("upload:new")
                self.assets = [{
                    "id": 2,
                    "name": abk.SIGNING_PUBLIC_KEY_ASSET,
                    "url": "concurrent",
                }]
                raise RuntimeError("simulated lost upload response")

            def _download_release_asset_text(self, url):
                self.events.append(f"download:{url}")
                return self.asset_text[url]

        client = ConcurrentAssetClient()

        with self.assertRaisesRegex(
            abk.SigningStateIndeterminateError,
            "different signing public key appeared",
        ):
            abk.GitHubClient.replace_published_signing_key(
                client,
                "NEW",
                expected_previous_key="OLD",
            )

        self.assertEqual(
            [
                "list",
                "download:old",
                "delete:1",
                "upload:new",
                "list",
                "download:concurrent",
            ],
            client.events,
        )
        self.assertEqual([2], [asset["id"] for asset in client.assets])

    def test_public_asset_snapshot_check_preserves_concurrent_pair(self):
        class ConcurrentPairClient:
            def __init__(self):
                self.assets = [{
                    "id": 2,
                    "name": abk.SIGNING_PUBLIC_KEY_ASSET,
                    "url": "concurrent",
                }]
                self.events = []

            def get_release_by_tag(self, tag):
                return {"id": 10}

            def list_release_assets(self, release_id):
                self.events.append("list")
                return list(self.assets)

            def delete_release_asset(self, asset_id):
                self.events.append(f"delete:{asset_id}")

            def _upload_signing_key_asset(self, release, public_key_pem):
                self.events.append("upload:new")
                return True

            def _download_release_asset_text(self, url):
                self.events.append(f"download:{url}")
                return "CONCURRENT"

        client = ConcurrentPairClient()

        with self.assertRaisesRegex(
            abk.SigningStateIndeterminateError,
            "public key changed before replacement",
        ):
            abk.GitHubClient.replace_published_signing_key(
                client,
                "NEW",
                expected_previous_key="OLD",
            )

        self.assertEqual(["list", "download:concurrent"], client.events)
        self.assertEqual([2], [asset["id"] for asset in client.assets])

    def test_public_asset_lost_upload_response_adopts_own_committed_asset(self):
        class LostResponseClient:
            def __init__(self):
                self.assets = [{
                    "id": 1,
                    "name": abk.SIGNING_PUBLIC_KEY_ASSET,
                    "url": "old",
                }]
                self.asset_text = {"old": "OLD", "new": "NEW"}
                self.events = []

            def get_release_by_tag(self, tag):
                return {"id": 10}

            def list_release_assets(self, release_id):
                self.events.append("list")
                return list(self.assets)

            def delete_release_asset(self, asset_id):
                self.events.append(f"delete:{asset_id}")
                self.assets = [asset for asset in self.assets if asset["id"] != asset_id]

            def _upload_signing_key_asset(self, release, public_key_pem):
                self.events.append("upload:new")
                self.assets = [{
                    "id": 2,
                    "name": abk.SIGNING_PUBLIC_KEY_ASSET,
                    "url": "new",
                }]
                raise RuntimeError("simulated lost upload response")

            def _download_release_asset_text(self, url):
                self.events.append(f"download:{url}")
                return self.asset_text[url]

        client = LostResponseClient()

        replaced = abk.GitHubClient.replace_published_signing_key(
            client,
            "NEW",
            expected_previous_key="OLD",
        )

        self.assertTrue(replaced)
        self.assertEqual(
            [
                "list",
                "download:old",
                "delete:1",
                "upload:new",
                "list",
                "download:new",
            ],
            client.events,
        )
        self.assertEqual([2], [asset["id"] for asset in client.assets])

    def test_public_asset_delete_reuses_snapshot_without_second_download(self):
        class SnapshottedAssetClient:
            def __init__(self):
                self.assets = [{
                    "id": 1,
                    "name": abk.SIGNING_PUBLIC_KEY_ASSET,
                    "url": "old",
                }]
                self.events = []

            def get_release_by_tag(self, tag):
                return {"id": 10}

            def list_release_assets(self, release_id):
                self.events.append("list")
                return list(self.assets)

            def _download_release_asset_text(self, url):
                self.events.append(f"download:{url}")
                return "OLD"

            def delete_release_asset(self, asset_id):
                self.events.append(f"delete:{asset_id}")
                self.assets = [asset for asset in self.assets if asset["id"] != asset_id]

        client = SnapshottedAssetClient()
        snapshot = abk.GitHubClient.get_published_signing_key_snapshot(client)

        abk.GitHubClient.delete_published_signing_key(
            client,
            snapshot=snapshot,
        )

        self.assertEqual(["list", "download:old", "delete:1"], client.events)
        self.assertEqual([], client.assets)

    def test_disable_signing_deletes_remote_material_and_prevents_implicit_reenable(self):
        if not abk._CRYPTO_BACKEND:
            self.skipTest("RSA backend unavailable")
        _, public_key, _ = self._generated_pem_pair()
        client = SigningClient(secret_exists=True, published_key=public_key)
        abk._save_signing_state({}, client.repo, public_key)

        result = abk.disable_signing_verification(client)

        self.assertTrue(result["changed"])
        self.assertEqual(["secret_delete", "public_delete"], client.events)
        self.assertFalse(abk.signing_verification_enabled(client.repo))
        self.assertIsNone(abk.get_signing_key(client.repo))
        client.events.clear()
        self.assertIsNone(abk.ensure_signing_key(client))
        self.assertEqual([], client.events)

    def test_disabled_signing_preference_is_scoped_to_repository(self):
        abk._save_signing_disabled_state({}, "alice/ABK")

        self.assertFalse(abk.signing_verification_enabled("alice/ABK"))
        self.assertTrue(abk.signing_verification_enabled("bob/ABK"))

    def test_external_reenable_blocks_disabled_cli_instead_of_skipping_verification(self):
        if not abk._CRYPTO_BACKEND:
            self.skipTest("RSA backend unavailable")
        _, public_key, _ = self._generated_pem_pair()
        abk._save_signing_disabled_state({}, "alice/ABK")
        client = SigningClient(secret_exists=True, published_key=public_key)

        with self.assertRaisesRegex(RuntimeError, "re-enabled by another client"):
            abk.ensure_signing_key(client)
        with self.assertRaisesRegex(RuntimeError, "re-enabled by another client"):
            abk.resolve_verification_key(client)

        self.assertEqual([], client.events)

    def test_empty_remote_public_asset_never_falls_back_to_cached_key(self):
        if not abk._CRYPTO_BACKEND:
            self.skipTest("RSA backend unavailable")
        _, public_key, _ = self._generated_pem_pair()
        abk._save_signing_state({}, "alice/ABK", public_key)
        client = SigningClient(secret_exists=True, published_key=" \n")

        with self.assertRaises(ValueError):
            abk.resolve_verification_key(client)

        self.assertEqual([], client.events)

    def test_disabled_repository_setup_allows_unsigned_build_without_key_mutation(self):
        abk._save_signing_disabled_state({}, "alice/ABK")
        client = SigningClient()
        client.repo_explicit = True
        args = mock.Mock(
            repo="alice/ABK",
            json=False,
            token=None,
            kpm_password=None,
            dry_run=False,
        )

        self.assertTrue(abk.prepare_build_repository(client, args))

        self.assertEqual([], client.events)
        self.assertFalse(client.secret_exists)
        self.assertIsNone(client.published_key)

    def test_disable_keeps_public_key_when_secret_delete_fails(self):
        if not abk._CRYPTO_BACKEND:
            self.skipTest("RSA backend unavailable")
        _, public_key, _ = self._generated_pem_pair()

        class SecretDeleteFailure(SigningClient):
            def delete_repository_secret(self, name):
                self.events.append("secret_delete")
                raise RuntimeError("simulated secret delete failure")

        client = SecretDeleteFailure(secret_exists=True, published_key=public_key)

        with self.assertRaisesRegex(RuntimeError, "simulated secret delete failure"):
            abk.disable_signing_verification(client)

        self.assertEqual(["secret_delete"], client.events)
        self.assertEqual(public_key.strip(), client.published_key.strip())
        self.assertTrue(client.secret_exists)
        self.assertTrue(abk.signing_verification_enabled(client.repo))

    def test_disable_completes_when_secret_delete_succeeds_but_response_is_lost(self):
        if not abk._CRYPTO_BACKEND:
            self.skipTest("RSA backend unavailable")
        _, public_key, _ = self._generated_pem_pair()

        class LostDeleteResponse(SigningClient):
            def delete_repository_secret(self, name):
                self.events.append("secret_delete")
                self.secret_exists = False
                raise RuntimeError("simulated lost delete response")

        client = LostDeleteResponse(secret_exists=True, published_key=public_key)

        result = abk.disable_signing_verification(client)

        self.assertTrue(result["changed"])
        self.assertEqual(["secret_delete", "public_delete"], client.events)
        self.assertFalse(client.secret_exists)
        self.assertIsNone(client.published_key)
        self.assertFalse(abk.signing_verification_enabled(client.repo))

    def test_disable_delete_failure_after_secret_removal_sets_safety_lock(self):
        if not abk._CRYPTO_BACKEND:
            self.skipTest("RSA backend unavailable")
        _, public_key, _ = self._generated_pem_pair()

        class PublicDeleteFailure(SigningClient):
            def delete_published_signing_key(self):
                self.events.append("public_delete_failed")
                raise RuntimeError("simulated asset enumeration failure")

        client = PublicDeleteFailure(secret_exists=True, published_key=public_key)

        with self.assertRaisesRegex(
            abk.SigningStateIndeterminateError,
            "did not complete signing public key deletion",
        ):
            abk.disable_signing_verification(client)

        self.assertEqual(
            ["secret_delete", "public_delete_failed"],
            client.events,
        )
        state = abk.load_config()[abk.SIGNING_STATE_CONFIG_KEY]["alice/abk"]
        self.assertTrue(state["indeterminate"])

    def test_failed_rotation_cannot_leave_old_secret_behind_new_public_key(self):
        if not abk._CRYPTO_BACKEND:
            self.skipTest("RSA backend unavailable")
        _, old_public, _ = self._generated_pem_pair()
        private_b64, new_public, _ = self._generated_pem_pair()

        class SecretWriteFailure(SigningClient):
            def create_or_update_secret(self, name, value):
                self.events.append("secret")
                raise RuntimeError("simulated secret write failure")

        client = SecretWriteFailure(secret_exists=True, published_key=old_public)
        with (
            mock.patch.object(abk.time, "sleep"),
            self.assertRaisesRegex(
                abk.SigningStateIndeterminateError,
                "incomplete rotation was safety-locked",
            ),
        ):
            abk.install_signing_keypair(client, private_b64, new_public)

        self.assertFalse(client.secret_exists)
        self.assertEqual(new_public.strip(), client.published_key.strip())
        state = abk.load_config()[abk.SIGNING_STATE_CONFIG_KEY]["alice/abk"]
        self.assertTrue(state["indeterminate"])
        with self.assertRaises(abk.SigningStateIndeterminateError):
            abk.ensure_signing_key(client)
        with self.assertRaises(abk.SigningStateIndeterminateError):
            abk.resolve_verification_key(client)

    def test_rotation_stops_if_secret_reappears_before_public_key_change(self):
        if not abk._CRYPTO_BACKEND:
            self.skipTest("RSA backend unavailable")
        _, old_public, _ = self._generated_pem_pair()
        private_b64, new_public, _ = self._generated_pem_pair()

        class SecretReappearsBeforePublicChange(SigningClient):
            def __init__(self):
                super().__init__(secret_exists=True, published_key=old_public)
                self.secret_checks = 0

            def repository_secret_exists(self, name):
                self.secret_checks += 1
                if self.secret_checks == 3:
                    self.secret_exists = True
                return self.secret_exists

        client = SecretReappearsBeforePublicChange()

        with self.assertRaisesRegex(
            abk.SigningStateIndeterminateError,
            "reappeared concurrently before public-key rotation",
        ):
            abk.install_signing_keypair(client, private_b64, new_public)

        self.assertEqual(["secret_delete"], client.events)
        self.assertTrue(client.secret_exists)
        self.assertEqual(old_public.strip(), client.published_key.strip())
        state = abk.load_config()[abk.SIGNING_STATE_CONFIG_KEY]["alice/abk"]
        self.assertTrue(state["indeterminate"])

    def test_rotation_preserves_secret_recreated_during_public_key_change(self):
        if not abk._CRYPTO_BACKEND:
            self.skipTest("RSA backend unavailable")
        _, old_public, _ = self._generated_pem_pair()
        private_b64, new_public, _ = self._generated_pem_pair()

        class SecretReappearsDuringPublicChange(SigningClient):
            def replace_published_signing_key(
                self,
                value,
                *,
                expected_previous_key=None,
            ):
                replaced = super().replace_published_signing_key(
                    value,
                    expected_previous_key=expected_previous_key,
                )
                self.secret_exists = True
                return replaced

        client = SecretReappearsDuringPublicChange(
            secret_exists=True,
            published_key=old_public,
        )

        with self.assertRaisesRegex(
            abk.SigningStateIndeterminateError,
            "reappeared during public-key rotation",
        ):
            abk.install_signing_keypair(client, private_b64, new_public)

        self.assertEqual(
            ["secret_delete", "public_replace"],
            client.events,
        )
        self.assertTrue(client.secret_exists)
        self.assertEqual(new_public.strip(), client.published_key.strip())
        state = abk.load_config()[abk.SIGNING_STATE_CONFIG_KEY]["alice/abk"]
        self.assertTrue(state["indeterminate"])

    def test_rotation_does_not_delete_completed_concurrent_keypair(self):
        if not abk._CRYPTO_BACKEND:
            self.skipTest("RSA backend unavailable")
        _, old_public, _ = self._generated_pem_pair()
        private_b64, new_public, _ = self._generated_pem_pair()
        _, concurrent_public, _ = self._generated_pem_pair()

        class ConcurrentRotationWins(SigningClient):
            def replace_published_signing_key(
                self,
                value,
                *,
                expected_previous_key=None,
            ):
                self.events.append("public_replace")
                self.published_key = concurrent_public
                self.secret_exists = True
                return True

        client = ConcurrentRotationWins(
            secret_exists=False,
            published_key=old_public,
        )

        with self.assertRaisesRegex(
            abk.SigningStateIndeterminateError,
            "reappeared during public-key rotation",
        ):
            abk.install_signing_keypair(client, private_b64, new_public)

        self.assertEqual(["public_replace"], client.events)
        self.assertTrue(client.secret_exists)
        self.assertEqual(concurrent_public.strip(), client.published_key.strip())
        state = abk.load_config()[abk.SIGNING_STATE_CONFIG_KEY]["alice/abk"]
        self.assertTrue(state["indeterminate"])

    def test_rotation_locks_if_public_key_changes_without_a_secret(self):
        if not abk._CRYPTO_BACKEND:
            self.skipTest("RSA backend unavailable")
        _, old_public, _ = self._generated_pem_pair()
        private_b64, new_public, _ = self._generated_pem_pair()
        _, concurrent_public, _ = self._generated_pem_pair()

        class ConcurrentPublicKeyWins(SigningClient):
            def replace_published_signing_key(
                self,
                value,
                *,
                expected_previous_key=None,
            ):
                self.events.append("public_replace")
                self.published_key = concurrent_public
                return True

        client = ConcurrentPublicKeyWins(
            secret_exists=False,
            published_key=old_public,
        )

        with self.assertRaisesRegex(
            abk.SigningStateIndeterminateError,
            "public key changed during rotation",
        ):
            abk.install_signing_keypair(client, private_b64, new_public)

        self.assertEqual(["public_replace"], client.events)
        self.assertFalse(client.secret_exists)
        self.assertEqual(concurrent_public.strip(), client.published_key.strip())
        state = abk.load_config()[abk.SIGNING_STATE_CONFIG_KEY]["alice/abk"]
        self.assertTrue(state["indeterminate"])

    def test_rotation_preserves_completed_pair_after_concurrent_secret_put(self):
        if not abk._CRYPTO_BACKEND:
            self.skipTest("RSA backend unavailable")
        _, old_public, _ = self._generated_pem_pair()
        private_b64, new_public, _ = self._generated_pem_pair()
        _, concurrent_public, _ = self._generated_pem_pair()

        class ConcurrentPairCompletesAfterSecretPut(SigningClient):
            def create_or_update_secret(self, name, value):
                created = super().create_or_update_secret(name, value)
                self.events.append("concurrent_pair_complete")
                self.published_key = concurrent_public
                return created

        client = ConcurrentPairCompletesAfterSecretPut(
            secret_exists=False,
            published_key=old_public,
        )

        with self.assertRaisesRegex(
            abk.SigningStateIndeterminateError,
            "write-only Secret may belong to either client",
        ):
            abk.install_signing_keypair(client, private_b64, new_public)

        self.assertEqual(
            ["public_replace", "secret", "concurrent_pair_complete"],
            client.events,
        )
        self.assertTrue(client.secret_exists)
        self.assertEqual(concurrent_public.strip(), client.published_key.strip())
        state = abk.load_config()[abk.SIGNING_STATE_CONFIG_KEY]["alice/abk"]
        self.assertTrue(state["indeterminate"])
        with self.assertRaises(abk.SigningStateIndeterminateError):
            abk.ensure_signing_key(client)
        with self.assertRaises(abk.SigningStateIndeterminateError):
            abk.resolve_verification_key(client)

    def test_rotation_lost_put_then_concurrent_pair_is_not_deleted(self):
        if not abk._CRYPTO_BACKEND:
            self.skipTest("RSA backend unavailable")
        _, old_public, _ = self._generated_pem_pair()
        private_b64, new_public, _ = self._generated_pem_pair()
        _, concurrent_public, _ = self._generated_pem_pair()

        class LostPutThenConcurrentPair(SigningClient):
            def create_or_update_secret(self, name, value):
                super().create_or_update_secret(name, value)
                self.events.append("lost_response")
                self.published_key = concurrent_public
                raise RuntimeError("simulated lost PUT response")

        client = LostPutThenConcurrentPair(
            secret_exists=False,
            published_key=old_public,
        )

        with (
            mock.patch.object(abk.time, "sleep"),
            self.assertRaisesRegex(
                abk.SigningStateIndeterminateError,
                "public key changed before the Secret update could be confirmed",
            ),
        ):
            abk.install_signing_keypair(client, private_b64, new_public)

        self.assertEqual(
            ["public_replace", "secret", "lost_response"],
            client.events,
        )
        self.assertTrue(client.secret_exists)
        self.assertEqual(concurrent_public.strip(), client.published_key.strip())
        state = abk.load_config()[abk.SIGNING_STATE_CONFIG_KEY]["alice/abk"]
        self.assertTrue(state["indeterminate"])

    def test_rotation_lost_put_then_public_read_failure_sets_safety_lock(self):
        if not abk._CRYPTO_BACKEND:
            self.skipTest("RSA backend unavailable")
        _, old_public, _ = self._generated_pem_pair()
        private_b64, new_public, _ = self._generated_pem_pair()
        _, concurrent_public, _ = self._generated_pem_pair()

        class LostPutThenPublicReadFailure(SigningClient):
            def __init__(self):
                super().__init__(secret_exists=False, published_key=old_public)
                self.public_reads = 0

            def get_published_signing_key(self):
                self.public_reads += 1
                if self.public_reads == 4:
                    self.events.append("public_read_failed")
                    raise RuntimeError("simulated public key read failure")
                return self.published_key

            def create_or_update_secret(self, name, value):
                super().create_or_update_secret(name, value)
                self.events.append("lost_response_and_concurrent_public")
                self.published_key = concurrent_public
                raise RuntimeError("simulated lost PUT response")

        client = LostPutThenPublicReadFailure()

        with (
            mock.patch.object(abk.time, "sleep"),
            self.assertRaisesRegex(
                abk.SigningStateIndeterminateError,
                "did not confirm the signing public key before the Secret update",
            ),
        ):
            abk.install_signing_keypair(client, private_b64, new_public)

        self.assertEqual(
            [
                "public_replace",
                "secret",
                "lost_response_and_concurrent_public",
                "public_read_failed",
            ],
            client.events,
        )
        self.assertTrue(client.secret_exists)
        self.assertEqual(concurrent_public.strip(), client.published_key.strip())
        state = abk.load_config()[abk.SIGNING_STATE_CONFIG_KEY]["alice/abk"]
        self.assertTrue(state["indeterminate"])
        with self.assertRaises(abk.SigningStateIndeterminateError):
            abk.ensure_signing_key(client)

    def test_disable_preserves_pair_completed_during_final_absence_check(self):
        if not abk._CRYPTO_BACKEND:
            self.skipTest("RSA backend unavailable")
        _, public_key, _ = self._generated_pem_pair()
        _, concurrent_public, _ = self._generated_pem_pair()

        class PairCompletesDuringFinalCheck(SigningClient):
            def __init__(self):
                super().__init__(secret_exists=True, published_key=public_key)
                self.secret_checks = 0

            def repository_secret_exists(self, name):
                self.secret_checks += 1
                if self.secret_checks == 4:
                    self.events.append("concurrent_pair_complete")
                    self.published_key = concurrent_public
                    self.secret_exists = True
                return self.secret_exists

        client = PairCompletesDuringFinalCheck()

        with self.assertRaisesRegex(
            abk.SigningStateIndeterminateError,
            "concurrent material was not touched",
        ):
            abk.disable_signing_verification(client)

        self.assertEqual(
            ["secret_delete", "public_delete", "concurrent_pair_complete"],
            client.events,
        )
        self.assertTrue(client.secret_exists)
        self.assertEqual(concurrent_public, client.published_key)
        state = abk.load_config()[abk.SIGNING_STATE_CONFIG_KEY]["alice/abk"]
        self.assertTrue(state["indeterminate"])

    def test_disable_locks_when_empty_public_asset_appears_during_final_check(self):
        if not abk._CRYPTO_BACKEND:
            self.skipTest("RSA backend unavailable")
        _, public_key, _ = self._generated_pem_pair()

        class EmptyAssetAppearsDuringFinalCheck(SigningClient):
            def __init__(self):
                super().__init__(secret_exists=True, published_key=public_key)
                self.public_reads = 0

            def get_published_signing_key(self):
                self.public_reads += 1
                if self.public_reads == 3:
                    self.events.append("empty_asset_appeared")
                    self.published_key = ""
                return self.published_key

        client = EmptyAssetAppearsDuringFinalCheck()

        with self.assertRaises(abk.SigningStateIndeterminateError):
            abk.disable_signing_verification(client)

        self.assertEqual(
            ["secret_delete", "public_delete", "empty_asset_appeared"],
            client.events,
        )
        state = abk.load_config()[abk.SIGNING_STATE_CONFIG_KEY]["alice/abk"]
        self.assertTrue(state["indeterminate"])

    def test_missing_crypto_backend_fails_cleanly(self):
        with mock.patch.object(abk, "_CRYPTO_BACKEND", None):
            with self.assertRaisesRegex(RuntimeError, "requires cryptography"):
                abk.generate_signing_keypair()

    def test_logout_without_config_has_no_filesystem_side_effect(self):
        args = mock.Mock(json=False)
        with (
            mock.patch.object(abk, "_config_process_lock") as process_lock,
            contextlib.redirect_stdout(io.StringIO()),
        ):
            result = abk.cmd_logout(args)

        self.assertEqual(0, result)
        process_lock.assert_not_called()
        self.assertFalse(self.config_dir.exists())

    def test_signing_metadata_rejects_invalid_environment_and_config_keys(self):
        config = {
            abk.SIGNING_STATE_CONFIG_KEY: {
                "alice/abk": {
                    "public_key": "not-an-rsa-key",
                    "secret_name": abk.SIGNING_SECRET_NAME,
                    "version": abk.SIGNING_KEY_VERSION,
                }
            }
        }
        abk.save_config(config)

        with mock.patch.dict(
            os.environ,
            {"ABK_SIGNING_KEY": "also-not-an-rsa-key"},
        ):
            available, source = abk._signing_key_metadata("alice/ABK")

        self.assertFalse(available)
        self.assertIsNone(source)

    def test_existing_published_key_is_adopted_without_rotating_secret(self):
        if not abk._CRYPTO_BACKEND:
            self.skipTest("RSA backend unavailable")
        _, public_key = abk.generate_signing_keypair()
        client = SigningClient(secret_exists=True, published_key=public_key)

        result = abk.ensure_signing_key(client)

        self.assertEqual(
            abk.normalize_signing_public_key(public_key).strip(), result.strip()
        )
        self.assertEqual([], client.secret_updates)
        self.assertEqual([], client.publications)

    def test_android_key_rotation_updates_cli_repo_state(self):
        if not abk._CRYPTO_BACKEND:
            self.skipTest("RSA backend unavailable")
        _, old_public_key = abk.generate_signing_keypair()
        _, android_public_key = abk.generate_signing_keypair()
        config = {}
        abk._save_signing_state(config, "alice/ABK", old_public_key)
        client = SigningClient(
            secret_exists=True,
            published_key=android_public_key,
        )

        result = abk.ensure_signing_key(client)

        self.assertEqual(
            abk.normalize_signing_public_key(android_public_key).strip(),
            result.strip(),
        )
        state = abk.load_config()[abk.SIGNING_STATE_CONFIG_KEY]["alice/abk"]
        self.assertEqual(result, state["public_key"])

    def test_valid_android_key_repairs_a_damaged_cli_cache(self):
        if not abk._CRYPTO_BACKEND:
            self.skipTest("RSA backend unavailable")
        _, android_public_key = abk.generate_signing_keypair()
        abk.save_config({
            abk.SIGNING_STATE_CONFIG_KEY: {
                "alice/abk": {
                    "public_key": "damaged-local-cache",
                    "secret_name": abk.SIGNING_SECRET_NAME,
                    "version": abk.SIGNING_KEY_VERSION,
                }
            }
        })
        client = SigningClient(
            secret_exists=True,
            published_key=android_public_key,
        )

        result = abk.ensure_signing_key(client)

        expected = abk.normalize_signing_public_key(android_public_key)
        self.assertEqual(expected.strip(), result.strip())
        state = abk.load_config()[abk.SIGNING_STATE_CONFIG_KEY]["alice/abk"]
        self.assertEqual(expected.strip(), state["public_key"].strip())
        self.assertEqual([], client.secret_updates)
        self.assertEqual([], client.publications)

    def test_signing_state_is_scoped_to_repository(self):
        if not abk._CRYPTO_BACKEND:
            self.skipTest("RSA backend unavailable")
        alice = SigningClient(repo="alice/ABK")
        bob = SigningClient(repo="bob/ABK")

        with contextlib.redirect_stdout(io.StringIO()):
            alice_key = abk.ensure_signing_key(alice)
            bob_key = abk.ensure_signing_key(bob)

        states = abk.load_config()[abk.SIGNING_STATE_CONFIG_KEY]
        self.assertEqual(alice_key, states["alice/abk"]["public_key"])
        self.assertEqual(bob_key, states["bob/abk"]["public_key"])
        self.assertNotEqual(alice_key, bob_key)

    def test_public_key_override_requires_matching_repository_secret(self):
        if not abk._CRYPTO_BACKEND:
            self.skipTest("RSA backend unavailable")
        _, public_key = abk.generate_signing_keypair()
        client = SigningClient(secret_exists=False)

        with mock.patch.dict(os.environ, {"ABK_SIGNING_KEY": public_key}):
            with self.assertRaisesRegex(RuntimeError, "private GitHub secret is missing"):
                abk.ensure_signing_key(client)

    def test_publication_failure_never_deletes_write_only_secret(self):
        if not abk._CRYPTO_BACKEND:
            self.skipTest("RSA backend unavailable")

        class FailingPublisher(SigningClient):
            def publish_signing_key(self, value):
                raise RuntimeError("simulated publication failure")

        client = FailingPublisher()
        with contextlib.redirect_stdout(io.StringIO()):
            with self.assertRaisesRegex(RuntimeError, "publication failed before"):
                abk.ensure_signing_key(client)

        self.assertEqual([], client.secret_deletes)
        self.assertEqual([], client.secret_updates)
        self.assertFalse(self.config_file.exists())

    def test_android_winning_initialization_is_adopted_before_cli_secret_write(self):
        if not abk._CRYPTO_BACKEND:
            self.skipTest("RSA backend unavailable")
        cli_private, cli_public = abk.generate_signing_keypair()
        _, android_public = abk.generate_signing_keypair()

        class AndroidWinsClient(SigningClient):
            def publish_signing_key(self, value):
                self.events.append("android-public-and-secret")
                self.published_key = android_public
                self.secret_exists = True
                return False

        client = AndroidWinsClient()
        with (
            mock.patch.object(
                abk,
                "generate_signing_keypair",
                return_value=(cli_private, cli_public),
            ),
            contextlib.redirect_stdout(io.StringIO()),
        ):
            result = abk.ensure_signing_key(client)

        self.assertEqual(
            abk.normalize_signing_public_key(android_public).strip(),
            result.strip(),
        )
        self.assertEqual([], client.secret_updates)

    def test_android_completion_after_cli_publication_stops_cli_secret_write(self):
        if not abk._CRYPTO_BACKEND:
            self.skipTest("RSA backend unavailable")
        cli_private, cli_public = abk.generate_signing_keypair()
        _, android_public = abk.generate_signing_keypair()

        class LateAndroidWinsClient(SigningClient):
            def __init__(self):
                super().__init__()
                self.secret_checks = 0

            def repository_secret_exists(self, name):
                self.secret_checks += 1
                if self.secret_checks == 2:
                    self.secret_exists = True
                    self.published_key = android_public
                return self.secret_exists

        client = LateAndroidWinsClient()
        with (
            mock.patch.object(
                abk,
                "generate_signing_keypair",
                return_value=(cli_private, cli_public),
            ),
            contextlib.redirect_stdout(io.StringIO()),
        ):
            with self.assertRaisesRegex(RuntimeError, "appeared concurrently"):
                abk.ensure_signing_key(client)

        self.assertEqual([], client.secret_updates)
        self.assertEqual(2, client.secret_checks)

    def test_transient_signing_secret_failure_is_retried(self):
        if not abk._CRYPTO_BACKEND:
            self.skipTest("RSA backend unavailable")

        class TransientSecretClient(SigningClient):
            def create_or_update_secret(self, name, value):
                self.events.append("secret")
                self.secret_updates.append((name, value))
                if len(self.secret_updates) == 1:
                    return False
                self.secret_exists = True
                return True

        client = TransientSecretClient()
        with (
            mock.patch.object(abk.time, "sleep") as sleep,
            contextlib.redirect_stdout(io.StringIO()),
        ):
            abk.ensure_signing_key(client)

        self.assertEqual(2, len(client.secret_updates))
        self.assertEqual(client.secret_updates[0], client.secret_updates[1])
        sleep.assert_called_once_with(0.5)

    def test_lost_secret_response_does_not_repeat_the_put(self):
        if not abk._CRYPTO_BACKEND:
            self.skipTest("RSA backend unavailable")

        class LostResponseClient(SigningClient):
            def create_or_update_secret(self, name, value):
                self.events.append("secret")
                self.secret_updates.append((name, value))
                self.secret_exists = True
                raise TimeoutError("response lost after GitHub accepted the PUT")

        client = LostResponseClient()
        with (
            mock.patch.object(abk.time, "sleep") as sleep,
            contextlib.redirect_stdout(io.StringIO()),
        ):
            abk.ensure_signing_key(client)

        self.assertEqual(1, len(client.secret_updates))
        sleep.assert_called_once_with(0.5)

    def test_secret_retry_stops_when_android_rotates_during_backoff(self):
        if not abk._CRYPTO_BACKEND:
            self.skipTest("RSA backend unavailable")
        _, android_public = abk.generate_signing_keypair()

        class FirstPutFailsClient(SigningClient):
            def create_or_update_secret(self, name, value):
                self.events.append("secret")
                self.secret_updates.append((name, value))
                return False

        client = FirstPutFailsClient()

        def android_finishes(_delay):
            client.published_key = android_public
            client.secret_exists = True

        with (
            mock.patch.object(abk.time, "sleep", side_effect=android_finishes),
            contextlib.redirect_stdout(io.StringIO()),
            self.assertRaisesRegex(RuntimeError, "public key changed"),
        ):
            abk.ensure_signing_key(client)

        self.assertEqual(1, len(client.secret_updates))

    def test_signing_state_save_merges_the_latest_config_snapshot(self):
        if not abk._CRYPTO_BACKEND:
            self.skipTest("RSA backend unavailable")
        _, public_key = abk.generate_signing_keypair()

        class ConcurrentConfigClient(SigningClient):
            def get_published_signing_key(self):
                abk.save_config({"token": "fresh-token", "download_dir": "/fresh"})
                return self.published_key

        client = ConcurrentConfigClient(
            secret_exists=True,
            published_key=public_key,
        )
        abk.ensure_signing_key(client)

        config = abk.load_config()
        self.assertEqual("fresh-token", config["token"])
        self.assertEqual("/fresh", config["download_dir"])
        self.assertIn("alice/abk", config[abk.SIGNING_STATE_CONFIG_KEY])

    def test_concurrent_signing_initialization_publishes_one_matching_pair(self):
        if not abk._CRYPTO_BACKEND:
            self.skipTest("RSA backend unavailable")
        private_key, public_key = abk.generate_signing_keypair()
        state_lock = threading.Lock()
        state = {
            "secret_exists": False,
            "published_key": None,
            "secret_updates": [],
            "publications": [],
            "secret_deletes": [],
        }

        class SharedSigningClient:
            token = "test-token"
            repo = "alice/ABK"

            def repository_secret_exists(self, name):
                with state_lock:
                    return state["secret_exists"]

            def get_published_signing_key(self):
                with state_lock:
                    return state["published_key"]

            def create_or_update_secret(self, name, value):
                with state_lock:
                    state["secret_exists"] = True
                    state["secret_updates"].append((name, value))
                return True

            def publish_signing_key(self, value):
                with state_lock:
                    state["published_key"] = value
                    state["publications"].append(value)
                return True

            def delete_repository_secret(self, name):
                with state_lock:
                    state["secret_deletes"].append(name)

        barrier = threading.Barrier(2)

        def initialize():
            barrier.wait(timeout=5)
            return abk.ensure_signing_key(SharedSigningClient())

        with mock.patch.object(
            abk,
            "generate_signing_keypair",
            return_value=(private_key, public_key),
        ) as generate:
            with contextlib.redirect_stdout(io.StringIO()):
                with ThreadPoolExecutor(max_workers=2) as executor:
                    results = list(executor.map(lambda _: initialize(), range(2)))

        self.assertEqual(1, generate.call_count)
        self.assertEqual(1, len(state["secret_updates"]))
        self.assertEqual(1, len(state["publications"]))
        self.assertEqual([], state["secret_deletes"])
        self.assertEqual(results[0].strip(), results[1].strip())
        self.assertEqual(state["published_key"].strip(), results[0].strip())

    def test_config_lock_serializes_independent_python_processes(self):
        script = """
import json
import time
import abk
with abk._config_process_lock(timeout=5):
    started = time.monotonic()
    time.sleep(0.2)
    finished = time.monotonic()
print(json.dumps({"started": started, "finished": finished}))
"""
        env = os.environ.copy()
        env["PYTHONPATH"] = str(CLI_DIR)
        env["XDG_CONFIG_HOME"] = self.temp_dir.name
        env["APPDATA"] = self.temp_dir.name
        processes = [
            subprocess.Popen(
                [sys.executable, "-c", script],
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                env=env,
            )
            for _ in range(2)
        ]
        intervals = []
        for process in processes:
            stdout, stderr = process.communicate(timeout=10)
            self.assertEqual(0, process.returncode, stderr)
            intervals.append(json.loads(stdout))

        intervals.sort(key=lambda item: item["started"])
        self.assertGreaterEqual(
            intervals[1]["started"],
            intervals[0]["finished"] - 0.01,
        )

    @unittest.skipIf(os.name == "nt", "POSIX flock-specific regression")
    def test_config_lock_does_not_retry_permanent_platform_errors(self):
        error = OSError(errno.ENOTSUP, "locking unsupported")
        with (
            mock.patch("fcntl.flock", side_effect=error),
            mock.patch.object(abk.time, "sleep") as sleep,
            self.assertRaises(OSError) as raised,
        ):
            with abk._config_process_lock(timeout=120):
                self.fail("lock unexpectedly acquired")

        self.assertEqual(errno.ENOTSUP, raised.exception.errno)
        sleep.assert_not_called()

    def test_artifact_redirect_does_not_forward_authorization(self):
        location = "https://objects.example.test/signed-artifact"
        headers = Message()
        headers["Location"] = location

        class RedirectingOpener:
            def __init__(self):
                self.requests = []

            def open(self, request, timeout=None):
                self.requests.append(request)
                if len(self.requests) == 1:
                    raise HTTPError(
                        request.full_url,
                        302,
                        "Found",
                        headers,
                        io.BytesIO(),
                    )
                return io.BytesIO(b"artifact bytes")

        client = object.__new__(abk.GitHubClient)
        client.token = "SECRET"
        client.repo = "alice/ABK"
        client.verbose = False
        opener = RedirectingOpener()

        with mock.patch.object(abk, "build_opener", return_value=opener):
            path = client.download_artifact(123, self.temp_dir.name)

        self.assertEqual(b"artifact bytes", Path(path).read_bytes())
        self.assertEqual(2, len(opener.requests))
        self.assertIsNotNone(opener.requests[0].get_header("Authorization"))
        self.assertIsNone(opener.requests[1].get_header("Authorization"))

    def test_artifact_same_origin_redirect_preserves_authorization(self):
        location = "https://api.github.com/repos/alice/ABK/actions/artifacts/456/zip"
        headers = Message()
        headers["Location"] = location

        class RedirectingOpener:
            def __init__(self):
                self.requests = []

            def open(self, request, timeout=None):
                self.requests.append(request)
                if len(self.requests) == 1:
                    raise HTTPError(
                        request.full_url,
                        301,
                        "Moved Permanently",
                        headers,
                        io.BytesIO(),
                    )
                return io.BytesIO(b"artifact bytes")

        client = object.__new__(abk.GitHubClient)
        client.token = "SECRET"
        client.repo = "alice/ABK"
        client.verbose = False
        opener = RedirectingOpener()

        with mock.patch.object(abk, "build_opener", return_value=opener):
            path = client.download_artifact(123, self.temp_dir.name)

        self.assertEqual(b"artifact bytes", Path(path).read_bytes())
        self.assertEqual(2, len(opener.requests))
        self.assertIsNotNone(opener.requests[0].get_header("Authorization"))
        self.assertIsNotNone(opener.requests[1].get_header("Authorization"))

    def test_github_api_follows_same_origin_redirect_with_method_and_token(self):
        headers = Message()
        headers["Location"] = "https://api.github.com/repos/alice/new/issues"

        class RedirectingOpener:
            def __init__(self):
                self.requests = []

            def open(self, request, timeout=None):
                self.requests.append(request)
                if len(self.requests) == 1:
                    raise HTTPError(
                        request.full_url,
                        301,
                        "Moved Permanently",
                        headers,
                        io.BytesIO(),
                    )
                return io.BytesIO(b'{"number":1}')

        client = object.__new__(abk.GitHubClient)
        client.token = "SECRET"
        client.verbose = False
        opener = RedirectingOpener()

        with mock.patch.object(abk, "build_opener", return_value=opener):
            result = client.post("/repos/alice/old/issues", {"title": "test"})

        self.assertEqual({"number": 1}, result)
        self.assertEqual(2, len(opener.requests))
        self.assertEqual("POST", opener.requests[1].get_method())
        self.assertEqual(opener.requests[0].data, opener.requests[1].data)
        self.assertIsNotNone(opener.requests[1].get_header("Authorization"))

    def test_github_api_rejects_cross_origin_redirect(self):
        headers = Message()
        headers["Location"] = "https://attacker.example.test/collect"

        class RedirectingOpener:
            def __init__(self):
                self.requests = []

            def open(self, request, timeout=None):
                self.requests.append(request)
                raise HTTPError(
                    request.full_url,
                    302,
                    "Found",
                    headers,
                    io.BytesIO(),
                )

        client = object.__new__(abk.GitHubClient)
        client.token = "SECRET"
        client.verbose = False
        opener = RedirectingOpener()

        with (
            mock.patch.object(abk, "build_opener", return_value=opener),
            self.assertRaisesRegex(RuntimeError, "unsafe redirect"),
        ):
            client.get("/user")

        self.assertEqual(1, len(opener.requests))

    def test_oauth_device_requests_fail_closed_on_redirect(self):
        headers = Message()
        headers["Location"] = "https://attacker.example.test/oauth"

        class RedirectingOpener:
            def __init__(self):
                self.requests = []

            def open(self, request, timeout=None):
                self.requests.append(request)
                raise HTTPError(
                    request.full_url,
                    302,
                    "Found",
                    headers,
                    io.BytesIO(),
                )

        for operation in ("device_code", "device_token"):
            with self.subTest(operation=operation):
                opener = RedirectingOpener()
                with (
                    mock.patch.object(abk, "build_opener", return_value=opener) as build,
                    contextlib.redirect_stderr(io.StringIO()),
                ):
                    if operation == "device_code":
                        result = abk.request_device_code()
                        self.assertIsNone(result)
                    else:
                        result = abk.poll_device_token_once("sensitive-device-code")
                        self.assertEqual("http_302", result["error"])

                self.assertEqual(1, len(opener.requests))
                if operation == "device_token":
                    self.assertIn(b"sensitive-device-code", opener.requests[0].data)
                handlers = build.call_args.args
                self.assertTrue(any(isinstance(item, abk.HTTPSHandler) for item in handlers))
                self.assertTrue(
                    any(isinstance(item, abk._NoRedirectHandler) for item in handlers)
                )

    def test_network_opener_uses_the_same_tls_context_factory_as_self_test(self):
        opener = mock.Mock()
        context = mock.sentinel.tls_context
        request = abk.Request("https://api.github.com/user")

        with (
            mock.patch.object(abk, "_create_tls_context", return_value=context) as create,
            mock.patch.object(abk, "build_opener", return_value=opener) as build,
        ):
            abk._open_without_redirect(request, timeout=30)

        create.assert_called_once_with()
        self.assertTrue(
            any(isinstance(item, abk.HTTPSHandler) for item in build.call_args.args)
        )
        opener.open.assert_called_once_with(request, timeout=30)

    def test_verbose_api_logging_never_includes_request_url_or_secret_name(self):
        client = abk.GitHubClient(
            token="sensitive-token",
            repo="alice/ABK",
            verbose=True,
        )
        secret_path = (
            f"/repos/alice/ABK/actions/secrets/{abk.SIGNING_SECRET_NAME}"
        )
        signed_path = secret_path + "?signature=sensitive-signature"
        stderr = io.StringIO()

        with (
            mock.patch.object(
                abk,
                "_open_same_origin_redirect",
                return_value=io.BytesIO(b"{}"),
            ),
            contextlib.redirect_stderr(stderr),
        ):
            result = client._request("GET", signed_path)

        rendered = stderr.getvalue()
        self.assertEqual({}, result)
        self.assertEqual("> GET GitHub API request\n", rendered)
        self.assertNotIn(secret_path, rendered)
        self.assertNotIn(abk.SIGNING_SECRET_NAME, rendered)
        self.assertNotIn("sensitive-signature", rendered)
        self.assertNotIn("sensitive-token", rendered)

    def test_public_build_inputs_never_copy_the_password_value(self):
        private_inputs = {
            "use_kpm": "true",
            "kpm_password": "sensitive-password",
        }

        public_inputs = abk._redacted_inputs(private_inputs)

        self.assertEqual("true", public_inputs["use_kpm"])
        self.assertEqual("***", public_inputs["kpm_password"])
        self.assertNotIn("sensitive-password", json.dumps(public_inputs))
        self.assertEqual("sensitive-password", private_inputs["kpm_password"])
        self.assertEqual("", abk._redacted_inputs({"kpm_password": ""})["kpm_password"])

    def test_tls_context_preserves_an_explicit_custom_ca_bundle(self):
        ca_bundle = Path(self.temp_dir.name) / "corporate-ca.pem"
        ca_bundle.write_text("test CA", encoding="utf-8")
        context = mock.sentinel.tls_context

        with (
            mock.patch.dict(os.environ, {"SSL_CERT_FILE": str(ca_bundle)}),
            mock.patch.object(
                abk.ssl,
                "create_default_context",
                return_value=context,
            ) as create,
        ):
            result = abk._create_tls_context()

        self.assertIs(context, result)
        create.assert_called_once_with()

    def test_artifact_redirect_rejects_later_http_downgrade(self):
        first_headers = Message()
        first_headers["Location"] = "https://objects.example.test/first"
        second_headers = Message()
        second_headers["Location"] = "http://objects.example.test/plaintext"

        class DowngradingOpener:
            def __init__(self):
                self.calls = 0

            def open(self, request, timeout=None):
                self.calls += 1
                headers = first_headers if self.calls == 1 else second_headers
                raise HTTPError(
                    request.full_url,
                    302,
                    "Found",
                    headers,
                    io.BytesIO(),
                )

        client = object.__new__(abk.GitHubClient)
        client.token = "SECRET"
        client.repo = "alice/ABK"
        opener = DowngradingOpener()

        with (
            mock.patch.object(abk, "build_opener", return_value=opener),
            self.assertRaisesRegex(RuntimeError, "unsafe URL"),
        ):
            client.download_artifact(123, self.temp_dir.name)

        self.assertFalse((Path(self.temp_dir.name) / "artifact-123.zip").exists())
        self.assertEqual([], list(Path(self.temp_dir.name).glob(".artifact-123-*.tmp")))

    def test_release_asset_rejects_non_github_url_before_sending_token(self):
        client = object.__new__(abk.GitHubClient)
        client.token = "SECRET"
        opener = mock.Mock()

        with (
            mock.patch.object(abk, "build_opener", opener),
            self.assertRaisesRegex(RuntimeError, "unsafe URL"),
        ):
            client._download_release_asset_text(
                "https://objects.example.test/untrusted-key.pem"
            )

        opener.assert_not_called()

    def test_release_upload_rejects_nonstandard_https_port(self):
        client = object.__new__(abk.GitHubClient)
        client.token = "SECRET"
        client.get_release_by_tag = mock.Mock(return_value={
            "assets": [],
            "upload_url": "https://uploads.github.com:444/repos/alice/ABK/releases/1/assets{?name}",
        })

        with (
            mock.patch.object(abk, "_open_same_origin_redirect") as open_request,
            self.assertRaisesRegex(RuntimeError, "unsafe release upload URL"),
        ):
            client.publish_signing_key("public key")

        open_request.assert_not_called()

    def test_oversized_artifact_does_not_replace_existing_file(self):
        output_dir = Path(self.temp_dir.name)
        existing = output_dir / "artifact-123.zip"
        existing.write_bytes(b"old artifact")

        class StaticOpener:
            def open(self, request, timeout=None):
                return io.BytesIO(b"12345")

        client = object.__new__(abk.GitHubClient)
        client.token = "SECRET"
        client.repo = "alice/ABK"

        with (
            mock.patch.object(abk, "build_opener", return_value=StaticOpener()),
            mock.patch.object(abk, "MAX_ARTIFACT_DOWNLOAD_SIZE", 4),
            self.assertRaisesRegex(RuntimeError, "unexpectedly large"),
        ):
            client.download_artifact(123, output_dir)

        self.assertEqual(b"old artifact", existing.read_bytes())
        self.assertEqual([], list(output_dir.glob(".artifact-123-*.tmp")))

    def test_artifact_content_length_mismatch_is_rejected(self):
        response_headers = Message()
        response_headers["Content-Length"] = "6"

        class Response(io.BytesIO):
            headers = response_headers

        class StaticOpener:
            def open(self, request, timeout=None):
                return Response(b"12345")

        client = object.__new__(abk.GitHubClient)
        client.token = "SECRET"
        client.repo = "alice/ABK"

        with (
            mock.patch.object(abk, "build_opener", return_value=StaticOpener()),
            self.assertRaisesRegex(RuntimeError, "truncated"),
        ):
            client.download_artifact(123, self.temp_dir.name)

        self.assertFalse((Path(self.temp_dir.name) / "artifact-123.zip").exists())


if __name__ == "__main__":
    unittest.main()
