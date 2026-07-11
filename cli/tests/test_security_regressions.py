import base64
import contextlib
import io
import os
import sys
import tempfile
import unittest
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

    def repository_secret_exists(self, name):
        return self.secret_exists

    def get_published_signing_key(self):
        return self.published_key

    def create_or_update_secret(self, name, value):
        self.secret_updates.append((name, value))
        self.secret_exists = True
        return True

    def publish_signing_key(self, value):
        self.publications.append(value)
        self.published_key = value
        return True

    def delete_repository_secret(self, name):
        self.secret_deletes.append(name)


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
        config = abk.load_config()
        state = config[abk.SIGNING_STATE_CONFIG_KEY]["alice/abk"]
        self.assertEqual(public_key, state["public_key"])
        self.assertEqual(abk.SIGNING_SECRET_NAME, state["secret_name"])
        self.assertNotIn("PRIVATE KEY", self.config_file.read_text(encoding="utf-8"))
        if os.name != "nt":
            self.assertEqual(0o700, self.config_dir.stat().st_mode & 0o777)
            self.assertEqual(0o600, self.config_file.stat().st_mode & 0o777)

    def test_missing_crypto_backend_fails_cleanly(self):
        with mock.patch.object(abk, "_CRYPTO_BACKEND", None):
            with self.assertRaisesRegex(RuntimeError, "requires cryptography"):
                abk.generate_signing_keypair()

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

    def test_publication_failure_rolls_back_private_secret(self):
        if not abk._CRYPTO_BACKEND:
            self.skipTest("RSA backend unavailable")

        class FailingPublisher(SigningClient):
            def publish_signing_key(self, value):
                raise RuntimeError("simulated publication failure")

        client = FailingPublisher()
        with contextlib.redirect_stdout(io.StringIO()):
            with self.assertRaisesRegex(RuntimeError, "publication failure"):
                abk.ensure_signing_key(client)

        self.assertEqual([abk.SIGNING_SECRET_NAME], client.secret_deletes)
        self.assertFalse(self.config_file.exists())

    def test_artifact_redirect_does_not_forward_authorization(self):
        location = "https://objects.example.test/signed-artifact"
        headers = Message()
        headers["Location"] = location

        class RedirectingOpener:
            def open(self, request, timeout=None):
                raise HTTPError(request.full_url, 302, "Found", headers, io.BytesIO())

        redirected_requests = []

        def redirected_urlopen(request, timeout=None):
            redirected_requests.append(request)
            return io.BytesIO(b"artifact bytes")

        client = object.__new__(abk.GitHubClient)
        client.token = "SECRET"
        client.repo = "alice/ABK"
        client.verbose = False

        with (
            mock.patch.object(abk, "build_opener", return_value=RedirectingOpener()),
            mock.patch.object(abk, "urlopen", side_effect=redirected_urlopen),
        ):
            path = client.download_artifact(123, self.temp_dir.name)

        self.assertEqual(b"artifact bytes", Path(path).read_bytes())
        self.assertEqual(1, len(redirected_requests))
        self.assertNotIn("Authorization", redirected_requests[0].headers)


if __name__ == "__main__":
    unittest.main()
