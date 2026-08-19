import base64
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

import credential_store  # noqa: E402


class FakeNativeBackend:
    name = "test-native"

    def __init__(self, token=None):
        self.token = token
        self.deleted = False

    def get(self):
        return self.token

    def set(self, token):
        self.token = token

    def delete(self):
        existed = self.token is not None
        self.token = None
        self.deleted = True
        return existed


class TemporarilyUnavailableBackend(FakeNativeBackend):
    def __init__(self, token=None):
        super().__init__(token)
        self.available = True

    def _require_available(self):
        if not self.available:
            raise credential_store.NativeStoreUnavailable("not available")

    def get(self):
        self._require_available()
        return super().get()

    def set(self, token):
        self._require_available()
        return super().set(token)

    def delete(self):
        self._require_available()
        return super().delete()


class CredentialStoreTests(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp_dir.cleanup)
        self.directory = Path(self.temp_dir.name) / "config"
        self.machine_id = b"linux:test-machine-id"

    def _unavailable(self):
        raise credential_store.NativeStoreUnavailable("not available")

    def _fallback_store(self, machine_id=None):
        return credential_store.CredentialStore(
            self.directory,
            native_backend_factory=self._unavailable,
            machine_id_provider=lambda: machine_id or self.machine_id,
        )

    def _local_fallback_store(self, machine_id_provider=None):
        return credential_store.CredentialStore(
            self.directory,
            native_backend_factory=self._unavailable,
            machine_id_provider=machine_id_provider or (lambda: b""),
        )

    def _write_legacy_machine_fixture(self, store):
        store.directory.mkdir(parents=True, exist_ok=True)
        store.path.write_text(
            json.dumps({
                "version": 1,
                "backend": "machine-bound-aes-gcm",
                "native_cleanup_pending": False,
                "kdf": "hkdf-sha256",
                "cipher": "aes-256-gcm",
                "seed": "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
                "nonce": "AAECAwQFBgcICQoL",
                "ciphertext": "8hJW3Sq/1FkVlvOSjDjitQ==",
                "tag": "G+EVrGVUymc/VoyYuvLGQw==",
            }),
            encoding="utf-8",
        )

    def _write_legacy_local_key_fixture(self, store):
        store._write_json_file(
            store.key_path,
            {
                "version": 1,
                "kind": "local-credential-master-key",
                "key": "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
            },
            prefix=".credential-key-",
        )
        store._write_metadata({
            "version": 1,
            "backend": "local-key-aes-gcm",
            "native_cleanup_pending": False,
            "kdf": "hkdf-sha256",
            "cipher": "aes-256-gcm",
            "seed": "ICEiIyQlJicoKSorLC0uLzAxMjM0NTY3ODk6Ozw9Pj8=",
            "nonce": "AAECAwQFBgcICQoL",
            "ciphertext": "4JTmQokAnSHLIB5Fv1GcVT3M",
            "tag": "cdV5GPM/FQ/eEazChb0RjA==",
        })

    def _write_legacy_pending_machine_fixture(self, store):
        store._write_metadata({
            "version": 1,
            "backend": "machine-bound-aes-gcm",
            "native_cleanup_pending": True,
            "native_cleanup_provider": "test-native",
            "kdf": "hkdf-sha256",
            "cipher": "aes-256-gcm",
            "seed": "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
            "nonce": "AAECAwQFBgcICQoL",
            "ciphertext": "7gVUkSqumEcXnbqPjTSqr0od+gY=",
            "tag": "iAJG2lP9vOvUr8LtZBi8Rw==",
        })

    def test_native_backend_is_preferred_and_verified(self):
        backend = FakeNativeBackend()
        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=lambda: backend,
            machine_id_provider=lambda: self.machine_id,
        )

        result = store.store("github-token")

        self.assertFalse(result.degraded)
        self.assertEqual("test-native", result.backend)
        self.assertEqual("github-token", store.read())
        metadata = json.loads(store.path.read_text(encoding="utf-8"))
        self.assertEqual("native", metadata["backend"])
        self.assertNotIn("github-token", store.path.read_text(encoding="utf-8"))

    def test_native_read_rejects_non_string_credentials(self):
        backend = FakeNativeBackend()
        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=lambda: backend,
            machine_id_provider=lambda: self.machine_id,
        )
        store.store("github-token")
        store._load_or_create_local_key()
        backend.token = b"invalid-token-bytes"

        with self.assertRaises(credential_store.NativeStoreError):
            store.read()

        self.assertTrue(store.key_path.exists())

    def test_machine_bound_v2_requires_a_separate_secret_key(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        store = self._fallback_store()

        result = store.store("github-token")

        self.assertTrue(result.degraded)
        self.assertEqual(credential_store.FALLBACK_BACKEND, result.backend)
        self.assertEqual("github-token", store.read())
        raw = store.path.read_text(encoding="utf-8")
        self.assertNotIn("github-token", raw)
        metadata = json.loads(raw)
        self.assertEqual(
            credential_store.CREDENTIAL_FALLBACK_FORMAT_VERSION,
            metadata["version"],
        )
        self.assertFalse(metadata["native_cleanup_pending"])
        self.assertEqual(32, len(base64.b64decode(metadata["seed"])))
        self.assertEqual(12, len(base64.b64decode(metadata["nonce"])))
        self.assertEqual(16, len(base64.b64decode(metadata["tag"])))
        self.assertTrue(store.key_path.exists())
        self.assertNotIn(
            "github-token",
            store.key_path.read_text(encoding="utf-8"),
        )
        if os.name != "nt":
            self.assertEqual(0o700, self.directory.stat().st_mode & 0o777)
            self.assertEqual(0o600, store.path.stat().st_mode & 0o777)
            self.assertEqual(0o600, store.key_path.stat().st_mode & 0o777)

        copied = credential_store.CredentialStore(
            Path(self.temp_dir.name) / "copied-config",
            native_backend_factory=self._unavailable,
            machine_id_provider=lambda: self.machine_id,
        )
        copied._write_metadata(metadata)

        with self.assertRaises(credential_store.CredentialCorrupt):
            copied.read(include_native=False)

    def test_prechange_machine_bound_v1_fixture_remains_readable(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        store = self._fallback_store(b"linux:compat-machine-id")
        self._write_legacy_machine_fixture(store)

        self.assertEqual("pre-change-token", store.read(include_native=False))
        self.assertFalse(store.key_path.exists())

    def test_legacy_machine_v1_migrates_to_secret_backed_v2(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        store = self._fallback_store(b"linux:compat-machine-id")
        self._write_legacy_machine_fixture(store)

        self.assertEqual("pre-change-token", store.read())

        metadata = json.loads(store.path.read_text(encoding="utf-8"))
        self.assertEqual(
            credential_store.CREDENTIAL_FALLBACK_FORMAT_VERSION,
            metadata["version"],
        )
        self.assertEqual(credential_store.FALLBACK_BACKEND, metadata["backend"])
        self.assertTrue(store.key_path.exists())
        self.assertEqual("pre-change-token", store.read(include_native=False))

    def test_provider_bound_legacy_machine_v1_migrates_when_native_unavailable(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        backend_factory = mock.Mock(
            side_effect=credential_store.NativeStoreUnavailable("not available")
        )
        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=backend_factory,
            machine_id_provider=lambda: b"linux:compat-machine-id",
        )
        self._write_legacy_pending_machine_fixture(store)

        self.assertEqual("legacy-pending-token", store.read())

        backend_factory.assert_called_once_with()
        metadata = json.loads(store.path.read_text(encoding="utf-8"))
        self.assertEqual(
            credential_store.CREDENTIAL_FALLBACK_FORMAT_VERSION,
            metadata["version"],
        )
        self.assertTrue(metadata["native_cleanup_pending"])
        self.assertEqual("test-native", metadata["native_cleanup_provider"])
        self.assertTrue(store.key_path.exists())
        self.assertEqual(
            "legacy-pending-token",
            store.read(include_native=False),
        )

    def test_failed_legacy_machine_migration_preserves_v1_record(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        store = self._fallback_store(b"linux:compat-machine-id")
        self._write_legacy_machine_fixture(store)
        metadata_before = store.path.read_bytes()

        with mock.patch.object(
            store,
            "_write_metadata",
            side_effect=OSError("read-only filesystem"),
        ):
            self.assertEqual("pre-change-token", store.read())

        self.assertEqual(metadata_before, store.path.read_bytes())
        self.assertFalse(store.key_path.exists())
        self.assertEqual(
            "pre-change-token",
            store.read(include_native=False),
        )

    def test_legacy_local_key_v1_fixture_remains_readable(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        store = self._local_fallback_store()
        self._write_legacy_local_key_fixture(store)

        self.assertEqual(
            "legacy-local-token",
            store.read(include_native=False),
        )

    def test_legacy_local_key_v1_migrates_to_machine_bound_v2(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=self._unavailable,
            machine_id_provider=lambda: self.machine_id,
        )
        self._write_legacy_local_key_fixture(store)
        key_before = store.key_path.read_bytes()

        self.assertEqual("legacy-local-token", store.read())

        metadata = json.loads(store.path.read_text(encoding="utf-8"))
        self.assertEqual(
            credential_store.CREDENTIAL_FALLBACK_FORMAT_VERSION,
            metadata["version"],
        )
        self.assertEqual(credential_store.FALLBACK_BACKEND, metadata["backend"])
        self.assertEqual(key_before, store.key_path.read_bytes())
        self.assertEqual(
            "legacy-local-token",
            store.read(include_native=False),
        )

    @unittest.skipIf(os.name == "nt", "POSIX permissions only")
    def test_read_repairs_restored_fallback_permissions(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        store = self._fallback_store()
        store.store("github-token")
        self.directory.chmod(0o755)
        store.path.chmod(0o644)
        store.key_path.chmod(0o644)

        self.assertEqual("github-token", store.read())

        self.assertEqual(0o700, self.directory.stat().st_mode & 0o777)
        self.assertEqual(0o600, store.path.stat().st_mode & 0o777)
        self.assertEqual(0o600, store.key_path.stat().st_mode & 0o777)

    def test_fallback_notice_runs_before_persistence(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        store = self._fallback_store()
        observations = []

        store.store(
            "github-token",
            before_fallback=lambda: observations.append(store.path.exists()),
        )

        self.assertEqual([False], observations)

    def test_rewriting_fallback_uses_fresh_seed_and_nonce(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        store = self._fallback_store()
        store.store("first-token")
        first = json.loads(store.path.read_text(encoding="utf-8"))
        master_key = store.key_path.read_bytes()

        store.store("second-token")
        second = json.loads(store.path.read_text(encoding="utf-8"))

        self.assertNotEqual(first["seed"], second["seed"])
        self.assertNotEqual(first["nonce"], second["nonce"])
        self.assertEqual(master_key, store.key_path.read_bytes())
        self.assertEqual("second-token", store.read())

    def test_machine_identifier_change_fails_closed(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        store = self._fallback_store()
        store.store("github-token")
        moved = self._fallback_store(b"linux:different-machine")

        with self.assertRaises(credential_store.CredentialCorrupt):
            moved.read()

    def test_tampering_is_detected(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        store = self._fallback_store()
        store.store("github-token")
        metadata = json.loads(store.path.read_text(encoding="utf-8"))
        tag = bytearray(base64.b64decode(metadata["tag"]))
        tag[0] ^= 1
        metadata["tag"] = base64.b64encode(tag).decode("ascii")
        store.path.write_text(json.dumps(metadata), encoding="utf-8")

        with self.assertRaises(credential_store.CredentialCorrupt):
            store.read()

    def test_cleanup_state_tampering_is_authenticated(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        store = self._fallback_store()
        store.store("github-token")
        metadata = json.loads(store.path.read_text(encoding="utf-8"))
        metadata["native_cleanup_pending"] = True
        store.path.write_text(json.dumps(metadata), encoding="utf-8")

        with self.assertRaises(credential_store.CredentialCorrupt):
            store.read()

    def test_invalid_metadata_is_rejected_and_can_be_reset(self):
        self.directory.mkdir(parents=True)
        path = self.directory / credential_store.CREDENTIAL_FILE_NAME
        path.write_text('{"version": 99}', encoding="utf-8")
        store = self._fallback_store()

        with self.assertRaises(credential_store.CredentialCorrupt):
            store.read()
        with self.assertRaises(credential_store.CredentialCorrupt):
            store.delete()
        self.assertFalse(path.exists())
        self.assertTrue(store.pending_path.exists())

    def test_untrusted_metadata_is_retained_without_a_safety_marker(self):
        self.directory.mkdir(parents=True)
        path = self.directory / credential_store.CREDENTIAL_FILE_NAME
        path.write_text('{"version": 99}', encoding="utf-8")
        backend_factory = mock.Mock()
        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=backend_factory,
            machine_id_provider=lambda: self.machine_id,
        )
        store._load_or_create_local_key()

        with (
            mock.patch.object(
                store,
                "_write_pending_metadata",
                side_effect=OSError("disk full"),
            ),
            self.assertRaises(credential_store.CredentialCorrupt),
        ):
            store.delete()

        backend_factory.assert_not_called()
        self.assertTrue(path.exists())
        self.assertTrue(store.key_path.exists())
        self.assertFalse(store.pending_path.exists())

    def test_cleanup_blocked_retry_finishes_interrupted_local_reset(self):
        self.directory.mkdir(parents=True)
        path = self.directory / credential_store.CREDENTIAL_FILE_NAME
        path.write_text('{"version": 99}', encoding="utf-8")
        backend_factory = mock.Mock()
        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=backend_factory,
            machine_id_provider=lambda: self.machine_id,
        )
        store._load_or_create_local_key()

        with (
            mock.patch.object(
                store,
                "_remove_metadata",
                side_effect=SystemExit("simulated process interruption"),
            ),
            self.assertRaises(SystemExit),
        ):
            store.delete()

        self.assertEqual(
            store._cleanup_blocked_metadata(),
            json.loads(store.pending_path.read_text(encoding="utf-8")),
        )
        self.assertTrue(path.exists())
        self.assertTrue(store.key_path.exists())

        with self.assertRaises(credential_store.CredentialCorrupt) as raised:
            store.delete()

        self.assertIn("manual native cleanup", str(raised.exception))
        backend_factory.assert_not_called()
        self.assertFalse(path.exists())
        self.assertFalse(store.key_path.exists())
        self.assertTrue(store.pending_path.exists())

    def test_locked_native_backend_does_not_downgrade(self):
        class LockedBackend(FakeNativeBackend):
            def set(self, token):
                raise credential_store.NativeStoreError("locked")

        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=lambda: LockedBackend(),
            machine_id_provider=lambda: self.machine_id,
        )

        with self.assertRaises(credential_store.NativeStoreError):
            store.store("github-token")
        self.assertFalse(store.path.exists())

    def test_local_key_fallback_round_trips_without_machine_identifier(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        store = self._local_fallback_store()

        result = store.store("github-token")

        self.assertTrue(result.degraded)
        self.assertEqual(
            credential_store.LOCAL_KEY_FALLBACK_BACKEND,
            result.backend,
        )
        self.assertEqual("github-token", store.read())
        credential_document = store.path.read_text(encoding="utf-8")
        key_document = store.key_path.read_text(encoding="utf-8")
        self.assertNotIn("github-token", credential_document + key_document)
        key_metadata = json.loads(key_document)
        metadata = json.loads(credential_document)
        self.assertEqual(
            credential_store.CREDENTIAL_FALLBACK_FORMAT_VERSION,
            metadata["version"],
        )
        self.assertEqual({"version", "kind", "key"}, set(key_metadata))
        self.assertEqual(
            32,
            len(base64.b64decode(key_metadata["key"], validate=True)),
        )
        if os.name != "nt":
            self.assertEqual(0o700, self.directory.stat().st_mode & 0o777)
            self.assertEqual(0o600, store.path.stat().st_mode & 0o777)
            self.assertEqual(0o600, store.key_path.stat().st_mode & 0o777)

    def test_local_fallback_warnings_run_before_any_persistence(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        store = self._local_fallback_store()
        observations = []

        store.store(
            "github-token",
            before_fallback=lambda: observations.append(
                ("fallback", store.path.exists(), store.key_path.exists())
            ),
            before_local_fallback=lambda: observations.append(
                ("local", store.path.exists(), store.key_path.exists())
            ),
        )

        self.assertEqual(
            [("fallback", False, False), ("local", False, False)],
            observations,
        )

    def test_local_fallback_rewrite_reuses_key_with_fresh_record_values(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        store = self._local_fallback_store()
        store.store("first-token")
        first_metadata = json.loads(store.path.read_text(encoding="utf-8"))
        first_key = store.key_path.read_bytes()

        store.store("second-token")

        second_metadata = json.loads(store.path.read_text(encoding="utf-8"))
        self.assertEqual(first_key, store.key_path.read_bytes())
        self.assertNotEqual(first_metadata["seed"], second_metadata["seed"])
        self.assertNotEqual(first_metadata["nonce"], second_metadata["nonce"])
        self.assertEqual("second-token", store.read())

    def test_missing_local_key_fails_closed_without_regeneration(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        store = self._local_fallback_store()
        store.store("github-token")
        metadata_before = store.path.read_bytes()
        store.key_path.unlink()

        with self.assertRaises(credential_store.CredentialCorrupt):
            store.read()
        with self.assertRaises(credential_store.CredentialCorrupt):
            store.store("replacement-token")

        self.assertEqual(metadata_before, store.path.read_bytes())
        self.assertFalse(store.key_path.exists())

    def test_wrong_local_key_fails_authentication_without_overwrite(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        store = self._local_fallback_store()
        store.store("github-token")
        metadata_before = store.path.read_bytes()
        key_metadata = json.loads(store.key_path.read_text(encoding="utf-8"))
        key_metadata["key"] = base64.b64encode(os.urandom(32)).decode("ascii")
        store.key_path.write_text(json.dumps(key_metadata), encoding="utf-8")
        wrong_key = store.key_path.read_bytes()

        with self.assertRaises(credential_store.CredentialCorrupt):
            store.read()
        with self.assertRaises(credential_store.CredentialCorrupt):
            store.store("replacement-token")

        self.assertEqual(metadata_before, store.path.read_bytes())
        self.assertEqual(wrong_key, store.key_path.read_bytes())

    def test_machine_v2_missing_key_cannot_use_verified_recovery(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        store = self._fallback_store()
        store.store("github-token")
        metadata_before = store.path.read_bytes()
        store.key_path.unlink()
        store._machine_id_provider = lambda: b""

        with self.assertRaises(credential_store.CredentialCorrupt):
            store.read(include_native=False)
        with self.assertRaises(credential_store.CredentialCorrupt):
            store.store("verified-token", allow_recovery=True)

        self.assertEqual(metadata_before, store.path.read_bytes())
        self.assertFalse(store.key_path.exists())

    def test_machine_v2_wrong_key_cannot_use_verified_recovery(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        store = self._fallback_store()
        store.store("github-token")
        metadata_before = store.path.read_bytes()
        key_metadata = json.loads(store.key_path.read_text(encoding="utf-8"))
        key_metadata["key"] = base64.b64encode(os.urandom(32)).decode("ascii")
        store.key_path.write_text(json.dumps(key_metadata), encoding="utf-8")
        wrong_key = store.key_path.read_bytes()
        store._machine_id_provider = lambda: b""

        with self.assertRaises(credential_store.CredentialCorrupt):
            store.read(include_native=False)
        with self.assertRaises(credential_store.CredentialCorrupt):
            store.store("verified-token", allow_recovery=True)

        self.assertEqual(metadata_before, store.path.read_bytes())
        self.assertEqual(wrong_key, store.key_path.read_bytes())

    def test_machine_v2_master_key_identifier_is_authenticated(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        store = self._fallback_store()
        store.store("github-token")
        metadata = json.loads(store.path.read_text(encoding="utf-8"))
        metadata["key_id"] = base64.b64encode(os.urandom(32)).decode("ascii")
        store.path.write_text(json.dumps(metadata), encoding="utf-8")

        with self.assertRaises(credential_store.CredentialCorrupt):
            store.read(include_native=False)

    def test_v2_records_cannot_be_downgraded_to_v1(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        cases = (
            ("machine", lambda: self.machine_id),
            ("local", lambda: b""),
        )
        for name, machine_id_provider in cases:
            with self.subTest(backend=name):
                directory = Path(self.temp_dir.name) / f"downgrade-{name}"
                store = credential_store.CredentialStore(
                    directory,
                    native_backend_factory=self._unavailable,
                    machine_id_provider=machine_id_provider,
                )
                store.store("github-token")
                metadata = json.loads(
                    store.path.read_text(encoding="utf-8")
                )
                metadata["version"] = credential_store.CREDENTIAL_FORMAT_VERSION
                metadata.pop("key_id")
                store.path.write_text(json.dumps(metadata), encoding="utf-8")

                with self.assertRaises(credential_store.CredentialCorrupt):
                    store.read(include_native=False)

    def test_local_backend_field_tampering_is_authenticated(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        machine_id = None

        def machine_id_provider():
            if machine_id is None:
                raise credential_store.NativeStoreUnavailable("not available")
            return machine_id

        store = self._local_fallback_store(machine_id_provider)
        store.store("github-token")
        metadata = json.loads(store.path.read_text(encoding="utf-8"))
        metadata["backend"] = credential_store.FALLBACK_BACKEND
        store.path.write_text(json.dumps(metadata), encoding="utf-8")
        machine_id = self.machine_id

        with self.assertRaises(credential_store.CredentialCorrupt):
            store.read(include_native=False)

    def test_local_key_write_failure_creates_no_credential_metadata(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        store = self._local_fallback_store()

        with (
            mock.patch.object(
                store,
                "_write_json_file",
                side_effect=OSError("read-only filesystem"),
            ),
            self.assertRaises(credential_store.CredentialStoreError),
        ):
            store.store("github-token")

        self.assertFalse(store.path.exists())
        self.assertFalse(store.key_path.exists())

    @unittest.skipIf(os.name == "nt", "POSIX permissions only")
    def test_local_directory_permission_failure_is_normalized(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        store = self._local_fallback_store()

        with (
            mock.patch.object(
                Path,
                "chmod",
                side_effect=PermissionError("permission denied"),
            ),
            self.assertRaises(credential_store.CredentialStoreError),
        ):
            store.store("github-token")

        self.assertFalse(store.path.exists())
        self.assertFalse(store.key_path.exists())

    def test_local_key_and_metadata_publication_sync_directory_entries(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        store = self._local_fallback_store()

        with mock.patch.object(
            store,
            "_fsync_directory",
            wraps=store._fsync_directory,
        ) as sync_directory:
            store.store("github-token")

        self.assertEqual(2, sync_directory.call_count)

    def test_credential_file_removals_sync_directory_entries(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        store = self._fallback_store()
        store.store("github-token")
        store._write_pending_metadata(
            store._native_transaction_metadata(FakeNativeBackend())
        )

        with mock.patch.object(
            store,
            "_fsync_directory",
            wraps=store._fsync_directory,
        ) as sync_directory:
            self.assertTrue(store._remove_metadata())
            self.assertTrue(store._remove_pending_metadata())
            self.assertTrue(store._remove_local_key())

        self.assertEqual(3, sync_directory.call_count)

    def test_failed_removal_sync_never_reports_success(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        store = self._fallback_store()
        store.store("github-token")
        store._write_pending_metadata(
            store._native_transaction_metadata(FakeNativeBackend())
        )
        removals = (
            (store._remove_metadata, store.path),
            (store._remove_pending_metadata, store.pending_path),
            (store._remove_local_key, store.key_path),
        )

        for remove, path in removals:
            with (
                self.subTest(path=path.name),
                mock.patch.object(
                    store,
                    "_fsync_directory",
                    side_effect=OSError("sync failed"),
                ),
                self.assertRaises(credential_store.CredentialStoreError),
            ):
                remove()
            self.assertFalse(path.exists())

            with mock.patch.object(
                store,
                "_fsync_directory",
                wraps=store._fsync_directory,
            ) as sync_directory:
                self.assertFalse(remove())
            sync_directory.assert_called_once_with()

    def test_absent_credential_entry_still_requires_directory_sync(self):
        store = self._fallback_store()
        store.directory.mkdir(parents=True)
        removals = (
            store._remove_metadata,
            store._remove_pending_metadata,
            store._remove_local_key,
        )

        for remove in removals:
            with (
                self.subTest(remove=remove.__name__),
                mock.patch.object(
                    store,
                    "_fsync_directory",
                    side_effect=OSError("sync failed"),
                ),
                self.assertRaises(credential_store.CredentialStoreError),
            ):
                remove()

    def test_removal_from_a_stateless_store_is_a_noop(self):
        store = self._fallback_store()

        self.assertFalse(store.delete())

        self.assertFalse(store.directory.exists())

    def test_first_local_metadata_failure_leaves_only_an_orphan_key(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        store = self._local_fallback_store()

        with (
            mock.patch.object(
                store,
                "_write_metadata",
                side_effect=OSError("read-only filesystem"),
            ),
            self.assertRaises(credential_store.CredentialStoreError),
        ):
            store.store("github-token")

        self.assertFalse(store.path.exists())
        self.assertTrue(store.key_path.exists())
        self.assertNotIn(
            "github-token",
            store.key_path.read_text(encoding="utf-8"),
        )

    def test_failed_local_rewrite_preserves_previous_credential(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        store = self._local_fallback_store()
        store.store("old-token")
        metadata_before = store.path.read_bytes()
        key_before = store.key_path.read_bytes()

        with (
            mock.patch.object(
                store,
                "_write_metadata",
                side_effect=OSError("read-only filesystem"),
            ),
            self.assertRaises(credential_store.CredentialStoreError),
        ):
            store.store("new-token")

        self.assertEqual(metadata_before, store.path.read_bytes())
        self.assertEqual(key_before, store.key_path.read_bytes())
        self.assertEqual("old-token", store.read())

    def test_local_fallback_adds_machine_binding_without_dropping_secret(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        machine_id = None

        def machine_id_provider():
            if machine_id is None:
                raise credential_store.NativeStoreUnavailable("not available")
            return machine_id

        store = self._local_fallback_store(machine_id_provider)
        store.store("github-token")
        self.assertTrue(store.key_path.exists())
        key_before = store.key_path.read_bytes()
        machine_id = self.machine_id

        self.assertEqual("github-token", store.read())

        metadata = json.loads(store.path.read_text(encoding="utf-8"))
        self.assertEqual(credential_store.FALLBACK_BACKEND, metadata["backend"])
        self.assertEqual(
            credential_store.CREDENTIAL_FALLBACK_FORMAT_VERSION,
            metadata["version"],
        )
        self.assertEqual(key_before, store.key_path.read_bytes())
        self.assertEqual("github-token", store.read())

    def test_machine_binding_upgrade_never_removes_required_secret(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        machine_id = None

        def machine_id_provider():
            if machine_id is None:
                raise credential_store.NativeStoreUnavailable("not available")
            return machine_id

        store = self._local_fallback_store(machine_id_provider)
        store.store("github-token")
        machine_id = self.machine_id

        with mock.patch.object(
            store,
            "_remove_local_key",
            side_effect=AssertionError("required key must not be removed"),
        ) as remove_key:
            self.assertEqual("github-token", store.read())

        metadata = json.loads(store.path.read_text(encoding="utf-8"))
        self.assertEqual(credential_store.FALLBACK_BACKEND, metadata["backend"])
        self.assertTrue(store.key_path.exists())
        remove_key.assert_not_called()

        self.assertEqual("github-token", store.read())
        self.assertTrue(store.key_path.exists())

    def test_machine_fallback_read_never_removes_required_secret(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        store = self._fallback_store()
        store.store("github-token")
        store._load_or_create_local_key()

        self.assertEqual("github-token", store.read(include_native=False))
        self.assertTrue(store.key_path.exists())

        self.assertEqual("github-token", store.read())
        self.assertTrue(store.key_path.exists())

    def test_local_fallback_upgrades_when_native_storage_appears(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        backend = FakeNativeBackend()
        native_available = False

        def backend_factory():
            if not native_available:
                raise credential_store.NativeStoreUnavailable("not available")
            return backend

        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=backend_factory,
            machine_id_provider=lambda: b"",
        )
        store.store("github-token")
        self.assertTrue(store.key_path.exists())
        native_available = True

        self.assertEqual("github-token", store.read())

        metadata = json.loads(store.path.read_text(encoding="utf-8"))
        self.assertEqual("native", metadata["backend"])
        self.assertEqual("github-token", backend.token)
        self.assertFalse(store.key_path.exists())

    def test_native_read_retries_orphan_key_cleanup_after_upgrade(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        backend = FakeNativeBackend()
        native_available = False

        def backend_factory():
            if not native_available:
                raise credential_store.NativeStoreUnavailable("not available")
            return backend

        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=backend_factory,
            machine_id_provider=lambda: b"",
        )
        store.store("github-token")
        native_available = True

        with mock.patch.object(
            store,
            "_remove_local_key",
            side_effect=credential_store.CredentialStoreError(
                "temporary cleanup failure"
            ),
        ):
            self.assertEqual("github-token", store.read())

        metadata = json.loads(store.path.read_text(encoding="utf-8"))
        self.assertEqual("native", metadata["backend"])
        self.assertTrue(store.key_path.exists())

        self.assertEqual("github-token", store.read())
        self.assertFalse(store.key_path.exists())

    def test_failed_native_upgrade_keeps_local_fallback_and_key(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")

        class SetFailureBackend(FakeNativeBackend):
            def set(self, token):
                raise credential_store.NativeStoreError("locked")

        backend = SetFailureBackend()
        native_available = False

        def backend_factory():
            if not native_available:
                raise credential_store.NativeStoreUnavailable("not available")
            return backend

        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=backend_factory,
            machine_id_provider=lambda: b"",
        )
        store.store("github-token")
        metadata_before = store.path.read_bytes()
        key_before = store.key_path.read_bytes()
        native_available = True

        self.assertEqual("github-token", store.read())

        self.assertEqual(metadata_before, store.path.read_bytes())
        self.assertEqual(key_before, store.key_path.read_bytes())
        self.assertFalse(store.pending_path.exists())
        self.assertIsNone(backend.token)

    def test_uncertain_native_upgrade_retains_local_key_until_logout(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")

        class RollbackFailureBackend(FakeNativeBackend):
            def __init__(self):
                super().__init__()
                self.fail_next_read = False
                self.allow_cleanup = False

            def set(self, token):
                super().set(token)
                self.fail_next_read = True

            def get(self):
                if self.fail_next_read:
                    self.fail_next_read = False
                    raise credential_store.NativeStoreUnavailable(
                        "provider disappeared"
                    )
                return super().get()

            def delete(self):
                if not self.allow_cleanup:
                    raise credential_store.NativeStoreError("cleanup failed")
                return super().delete()

        backend = RollbackFailureBackend()
        native_available = False

        def backend_factory():
            if not native_available:
                raise credential_store.NativeStoreUnavailable("not available")
            return backend

        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=backend_factory,
            machine_id_provider=lambda: b"",
        )
        store.store("github-token")
        native_available = True

        with self.assertRaises(credential_store.NativeRollbackError):
            store.read()

        self.assertTrue(store.path.exists())
        self.assertTrue(store.key_path.exists())
        self.assertTrue(store.pending_path.exists())

        backend.allow_cleanup = True
        self.assertTrue(store.delete())
        self.assertFalse(store.path.exists())
        self.assertFalse(store.key_path.exists())
        self.assertFalse(store.pending_path.exists())

    def test_lost_pending_write_response_blocks_local_to_machine_upgrade(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        backend = FakeNativeBackend()
        native_available = False
        machine_id = None

        def backend_factory():
            if not native_available:
                raise credential_store.NativeStoreUnavailable("not available")
            return backend

        def machine_id_provider():
            if machine_id is None:
                raise credential_store.NativeStoreUnavailable("not available")
            return machine_id

        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=backend_factory,
            machine_id_provider=machine_id_provider,
        )
        store.store("github-token")
        local_metadata = store.path.read_bytes()
        local_key = store.key_path.read_bytes()
        native_available = True
        machine_id = self.machine_id
        write_pending = store._write_pending_metadata

        def lost_pending_response(metadata):
            write_pending(metadata)
            raise OSError("response was lost")

        with (
            mock.patch.object(
                store,
                "_write_pending_metadata",
                side_effect=lost_pending_response,
            ),
            self.assertRaises(credential_store.NativeRollbackError),
        ):
            store.read()

        self.assertEqual(local_metadata, store.path.read_bytes())
        self.assertEqual(local_key, store.key_path.read_bytes())
        self.assertTrue(store.pending_path.exists())
        self.assertIsNone(backend.token)

        self.assertTrue(store.delete())
        self.assertFalse(store.path.exists())
        self.assertFalse(store.key_path.exists())
        self.assertFalse(store.pending_path.exists())

    def test_machine_fallback_never_downgrades_when_identifier_disappears(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        machine_id = self.machine_id

        def machine_id_provider():
            if machine_id is None:
                raise credential_store.NativeStoreUnavailable("not available")
            return machine_id

        store = self._fallback_store()
        store._machine_id_provider = machine_id_provider
        store.store("github-token")
        metadata_before = store.path.read_bytes()
        machine_id = None

        with self.assertRaises(credential_store.CredentialCorrupt):
            store.store("replacement-token")

        self.assertEqual(metadata_before, store.path.read_bytes())
        self.assertTrue(store.key_path.exists())

        with self.assertRaises(credential_store.CredentialCorrupt):
            store.delete()
        self.assertFalse(store.path.exists())
        self.assertTrue(store.pending_path.exists())

    def test_verified_login_recovers_when_machine_identifier_disappears(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        machine_id = self.machine_id

        def machine_id_provider():
            if machine_id is None:
                raise credential_store.NativeStoreUnavailable("not available")
            return machine_id

        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=self._unavailable,
            machine_id_provider=machine_id_provider,
        )
        store.store("old-token")
        machine_id = None
        warnings = []

        result = store.store(
            "fresh-token",
            allow_recovery=True,
            before_fallback=lambda: warnings.append("fallback"),
            before_local_fallback=lambda: warnings.append("local"),
        )

        self.assertTrue(result.degraded)
        self.assertEqual(
            credential_store.LOCAL_KEY_FALLBACK_BACKEND,
            result.backend,
        )
        self.assertEqual(["fallback", "local"], warnings)
        self.assertEqual("fresh-token", store.read(include_native=False))
        metadata = json.loads(store.path.read_text(encoding="utf-8"))
        self.assertFalse(metadata["native_cleanup_pending"])
        self.assertNotIn("native_cleanup_provider", metadata)
        self.assertTrue(store.key_path.exists())

    def test_verified_login_does_not_replace_changed_machine_record(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        machine_id = self.machine_id
        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=self._unavailable,
            machine_id_provider=lambda: machine_id,
        )
        store.store("old-token")
        metadata_before = store.path.read_bytes()
        machine_id = b"linux:different-machine-id"

        with self.assertRaises(credential_store.CredentialCorrupt):
            store.store("fresh-token", allow_recovery=True)

        self.assertEqual(metadata_before, store.path.read_bytes())
        self.assertTrue(store.key_path.exists())

    def test_failed_machine_recovery_preserves_old_record_for_retry(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        machine_id = self.machine_id

        def machine_id_provider():
            if machine_id is None:
                raise credential_store.NativeStoreUnavailable("not available")
            return machine_id

        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=self._unavailable,
            machine_id_provider=machine_id_provider,
        )
        store.store("old-token")
        metadata_before = json.loads(store.path.read_text(encoding="utf-8"))
        machine_id = None

        with (
            mock.patch.object(
                store,
                "_write_metadata",
                side_effect=OSError("disk full"),
            ),
            self.assertRaises(credential_store.CredentialStoreError),
        ):
            store.store("fresh-token", allow_recovery=True)

        self.assertEqual(
            metadata_before,
            json.loads(store.path.read_text(encoding="utf-8")),
        )
        self.assertTrue(store.key_path.exists())
        machine_id = self.machine_id
        self.assertEqual("old-token", store.read(include_native=False))

        machine_id = None
        result = store.store("fresh-token", allow_recovery=True)
        self.assertEqual(
            credential_store.LOCAL_KEY_FALLBACK_BACKEND,
            result.backend,
        )
        self.assertEqual("fresh-token", store.read(include_native=False))

    def test_logout_removes_local_fallback_and_orphan_key(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        store = self._local_fallback_store()
        store.store("github-token")

        self.assertTrue(store.delete())
        self.assertFalse(store.path.exists())
        self.assertFalse(store.key_path.exists())

        store._load_or_create_local_key()
        self.assertTrue(store.delete())
        self.assertFalse(store.key_path.exists())

    def test_logout_resets_local_fallback_with_corrupt_key(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        store = self._local_fallback_store()
        store.store("github-token")
        store.key_path.write_text('{"version": 999}', encoding="utf-8")

        with self.assertRaises(credential_store.CredentialCorrupt):
            store.delete()

        self.assertFalse(store.path.exists())
        self.assertFalse(store.key_path.exists())
        self.assertTrue(store.pending_path.exists())

    @unittest.skipIf(os.name == "nt", "POSIX permissions only")
    def test_local_fallback_read_repairs_key_permissions(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        store = self._local_fallback_store()
        store.store("github-token")
        self.directory.chmod(0o755)
        store.path.chmod(0o644)
        store.key_path.chmod(0o644)

        self.assertEqual("github-token", store.read())

        self.assertEqual(0o700, self.directory.stat().st_mode & 0o777)
        self.assertEqual(0o600, store.path.stat().st_mode & 0o777)
        self.assertEqual(0o600, store.key_path.stat().st_mode & 0o777)

    def test_delete_removes_native_credential_and_marker(self):
        backend = FakeNativeBackend()
        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=lambda: backend,
            machine_id_provider=lambda: self.machine_id,
        )
        store.store("github-token")

        self.assertTrue(store.delete())

        self.assertTrue(backend.deleted)
        self.assertFalse(store.path.exists())
        self.assertIsNone(backend.token)

    def test_native_metadata_write_failure_rolls_back_credential(self):
        backend = FakeNativeBackend()
        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=lambda: backend,
            machine_id_provider=lambda: self.machine_id,
        )

        with (
            mock.patch.object(
                store,
                "_write_metadata",
                side_effect=OSError("disk full"),
            ),
            self.assertRaises(credential_store.CredentialStoreError),
        ):
            store.store("github-token")

        self.assertIsNone(backend.token)
        self.assertTrue(backend.deleted)
        self.assertFalse(store.path.exists())

    def test_native_metadata_write_failure_restores_previous_credential(self):
        backend = FakeNativeBackend()
        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=lambda: backend,
            machine_id_provider=lambda: self.machine_id,
        )
        store.store("old-token")

        with (
            mock.patch.object(
                store,
                "_write_metadata",
                side_effect=OSError("disk full"),
            ),
            self.assertRaises(credential_store.CredentialStoreError),
        ):
            store.store("new-token")

        self.assertEqual("old-token", backend.token)
        self.assertEqual("old-token", store.read())

    def test_post_set_unavailability_rolls_back_without_fallback(self):
        class ReadFailsAfterSetBackend(FakeNativeBackend):
            def __init__(self):
                super().__init__()
                self.fail_next_read = False

            def set(self, token):
                super().set(token)
                if token == "new-token":
                    self.fail_next_read = True

            def get(self):
                if self.fail_next_read:
                    self.fail_next_read = False
                    raise credential_store.NativeStoreUnavailable(
                        "provider disappeared"
                    )
                return super().get()

        backend = ReadFailsAfterSetBackend()
        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=lambda: backend,
            machine_id_provider=lambda: self.machine_id,
        )
        fallback_notices = []

        with self.assertRaises(credential_store.NativeStoreUnavailable):
            store.store(
                "new-token",
                before_fallback=lambda: fallback_notices.append(True),
            )

        self.assertIsNone(backend.token)
        self.assertFalse(store.path.exists())
        self.assertEqual([], fallback_notices)

    def test_fallback_upgrade_rolls_back_a_failed_native_write(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")

        class ReadFailsAfterSetBackend(FakeNativeBackend):
            def __init__(self):
                super().__init__()
                self.fail_next_read = False

            def set(self, token):
                super().set(token)
                self.fail_next_read = True

            def get(self):
                if self.fail_next_read:
                    self.fail_next_read = False
                    raise credential_store.NativeStoreUnavailable(
                        "provider disappeared"
                    )
                return super().get()

        backend = ReadFailsAfterSetBackend()
        native_available = False

        def backend_factory():
            if not native_available:
                raise credential_store.NativeStoreUnavailable("not available")
            return backend

        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=backend_factory,
            machine_id_provider=lambda: self.machine_id,
        )
        store.store("fallback-token")
        native_available = True

        self.assertEqual("fallback-token", store.read())

        self.assertIsNone(backend.token)
        metadata = json.loads(store.path.read_text(encoding="utf-8"))
        self.assertEqual(credential_store.FALLBACK_BACKEND, metadata["backend"])
        self.assertTrue(store.key_path.exists())

    def test_failed_native_rollback_leaves_write_ahead_cleanup_state(self):
        class RollbackFailureBackend(FakeNativeBackend):
            def __init__(self):
                super().__init__()
                self.fail_next_read = False

            def set(self, token):
                super().set(token)
                self.fail_next_read = True

            def get(self):
                if self.fail_next_read:
                    self.fail_next_read = False
                    raise credential_store.NativeStoreUnavailable(
                        "provider disappeared"
                    )
                return super().get()

            def delete(self):
                raise credential_store.NativeStoreError("cleanup failed")

        backend = RollbackFailureBackend()
        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=lambda: backend,
            machine_id_provider=lambda: self.machine_id,
        )

        with self.assertRaises(credential_store.NativeRollbackError):
            store.store("new-token")

        self.assertEqual("new-token", backend.token)
        self.assertFalse(store.path.exists())
        metadata = json.loads(store.pending_path.read_text(encoding="utf-8"))
        self.assertEqual("cleanup-required", metadata["state"])
        self.assertNotIn("new-token", store.pending_path.read_text(encoding="utf-8"))
        with self.assertRaises(credential_store.NativeRollbackError):
            store.read()

        metadata["state"] = "clean"
        store.pending_path.write_text(json.dumps(metadata), encoding="utf-8")
        with self.assertRaises(credential_store.CredentialCorrupt):
            store.read()

    def test_failed_native_rollback_without_aes_state_stays_fail_closed(self):
        class RollbackFailureBackend(FakeNativeBackend):
            def __init__(self):
                super().__init__()
                self.fail_verification = False
                self.fail_rollback = False

            def set(self, token):
                if self.fail_rollback and token == "old-token":
                    raise credential_store.NativeStoreError("rollback failed")
                super().set(token)
                if token == "new-token":
                    self.fail_verification = True
                    self.fail_rollback = True

            def get(self):
                if self.fail_verification:
                    self.fail_verification = False
                    raise credential_store.NativeStoreUnavailable(
                        "provider disappeared"
                    )
                return super().get()

        backend = RollbackFailureBackend()
        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=lambda: backend,
            machine_id_provider=lambda: (_ for _ in ()).throw(
                credential_store.NativeStoreUnavailable(
                    "machine identifier unavailable"
                )
            ),
        )
        store.store("old-token")

        with self.assertRaises(credential_store.NativeRollbackError):
            store.store("new-token")

        self.assertEqual("new-token", backend.token)
        primary = store.path.read_text(encoding="utf-8")
        pending = store.pending_path.read_text(encoding="utf-8")
        self.assertNotIn("old-token", primary + pending)
        self.assertNotIn("new-token", primary + pending)
        metadata = json.loads(pending)
        self.assertEqual("cleanup-required", metadata["state"])
        with self.assertRaises(credential_store.NativeRollbackError):
            store.read()
        with self.assertRaises(credential_store.NativeRollbackError):
            store.store("third-token")

        metadata["kind"] = "native"
        store.pending_path.write_text(json.dumps(metadata), encoding="utf-8")
        with self.assertRaises(credential_store.CredentialCorrupt):
            store.read()

    def test_failed_write_ahead_marker_prevents_native_mutation(self):
        backend = FakeNativeBackend()
        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=lambda: backend,
            machine_id_provider=lambda: self.machine_id,
        )
        store.store("old-token")

        with (
            mock.patch.object(
                store,
                "_write_pending_metadata",
                side_effect=OSError("read-only filesystem"),
            ),
            self.assertRaises(credential_store.CredentialStoreError),
        ):
            store.store("new-token")

        self.assertEqual("old-token", backend.token)
        self.assertFalse(store.pending_path.exists())
        self.assertEqual("old-token", store.read())

    def test_logout_resets_corrupt_pending_marker_after_verified_cleanup(self):
        backend = FakeNativeBackend("uncertain-token")
        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=lambda: backend,
            machine_id_provider=lambda: self.machine_id,
        )
        store._write_metadata(store._native_metadata(backend))
        store.pending_path.write_text(
            json.dumps({
                "version": 1,
                "provider": backend.name,
                "service": credential_store.CREDENTIAL_SERVICE,
                "account": credential_store.CREDENTIAL_ACCOUNT,
                "unexpected": True,
            }),
            encoding="utf-8",
        )

        self.assertTrue(store.delete())

        self.assertIsNone(backend.token)
        self.assertFalse(store.path.exists())
        self.assertFalse(store.pending_path.exists())

    def test_logout_retains_corrupt_pending_without_provider_bound_primary(self):
        backend = FakeNativeBackend("unrelated-token")
        backend_factory = mock.Mock(return_value=backend)
        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=backend_factory,
            machine_id_provider=lambda: self.machine_id,
        )
        store.directory.mkdir(parents=True)
        store.pending_path.write_text(
            json.dumps({
                "version": 1,
                "provider": "test-native",
                "service": credential_store.CREDENTIAL_SERVICE,
                "account": credential_store.CREDENTIAL_ACCOUNT,
                "unexpected": True,
            }),
            encoding="utf-8",
        )

        with self.assertRaises(credential_store.CredentialCorrupt) as raised:
            store.delete()

        self.assertIn("manual native cleanup", str(raised.exception))
        backend_factory.assert_not_called()
        self.assertEqual("unrelated-token", backend.token)
        self.assertFalse(backend.deleted)
        self.assertTrue(store.pending_path.exists())

    def test_logout_retains_corrupt_pending_marker_for_other_provider(self):
        backend = FakeNativeBackend("current-provider-token")
        backend_factory = mock.Mock(return_value=backend)
        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=backend_factory,
            machine_id_provider=lambda: self.machine_id,
        )
        store._write_metadata(store._native_metadata(backend))
        store.pending_path.write_text(
            json.dumps({
                "version": 1,
                "provider": "different-native-provider",
                "service": credential_store.CREDENTIAL_SERVICE,
                "account": credential_store.CREDENTIAL_ACCOUNT,
                "unexpected": True,
            }),
            encoding="utf-8",
        )

        with self.assertRaises(credential_store.CredentialCorrupt):
            store.delete()

        backend_factory.assert_not_called()
        self.assertEqual("current-provider-token", backend.token)
        self.assertTrue(store.path.exists())
        self.assertTrue(store.pending_path.exists())

    def test_corrupt_pending_rejects_clean_authenticated_fallback_primary(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        backend = FakeNativeBackend("unrelated-token")
        backend_factory = mock.Mock(return_value=backend)
        store = self._fallback_store()
        store.store("fallback-token")
        primary_before = store.path.read_bytes()
        store._native_backend_factory = backend_factory
        store._write_pending_metadata({
            "version": credential_store.CREDENTIAL_FORMAT_VERSION,
            "provider": backend.name,
            "service": credential_store.CREDENTIAL_SERVICE,
            "account": credential_store.CREDENTIAL_ACCOUNT,
            "unexpected": True,
        })

        with self.assertRaises(credential_store.CredentialCorrupt):
            store.delete()

        backend_factory.assert_not_called()
        self.assertEqual("unrelated-token", backend.token)
        self.assertEqual(primary_before, store.path.read_bytes())
        self.assertTrue(store.pending_path.exists())

    def test_corrupt_pending_accepts_authenticated_deferred_provider(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        backend = FakeNativeBackend("stale-native-token")
        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=lambda: backend,
            machine_id_provider=lambda: self.machine_id,
        )
        store._write_metadata(store._fallback_metadata(
            "fresh-token",
            native_cleanup_pending=True,
            native_cleanup_provider=backend.name,
        ))
        store._write_pending_metadata({
            "version": credential_store.CREDENTIAL_FORMAT_VERSION,
            "provider": backend.name,
            "service": credential_store.CREDENTIAL_SERVICE,
            "account": credential_store.CREDENTIAL_ACCOUNT,
            "unexpected": True,
        })

        self.assertTrue(store.delete())

        self.assertIsNone(backend.token)
        self.assertFalse(store.path.exists())
        self.assertFalse(store.pending_path.exists())

    def test_corrupt_pending_cleanup_retries_with_provider_primary_intact(self):
        backend = FakeNativeBackend("uncertain-token")
        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=lambda: backend,
            machine_id_provider=lambda: self.machine_id,
        )
        store._write_metadata(store._native_metadata(backend))
        store._write_pending_metadata({
            "version": credential_store.CREDENTIAL_FORMAT_VERSION,
            "provider": backend.name,
            "service": credential_store.CREDENTIAL_SERVICE,
            "account": credential_store.CREDENTIAL_ACCOUNT,
            "unexpected": True,
        })

        with (
            mock.patch.object(
                store,
                "_remove_pending_metadata",
                side_effect=credential_store.CredentialStoreError("disk full"),
            ),
            self.assertRaises(credential_store.CredentialCorrupt),
        ):
            store.delete()

        self.assertIsNone(backend.token)
        self.assertTrue(store.path.exists())
        self.assertTrue(store.pending_path.exists())

        self.assertTrue(store.delete())
        self.assertFalse(store.path.exists())
        self.assertFalse(store.pending_path.exists())

    def test_valid_write_ahead_marker_can_cleanup_without_primary(self):
        backend = FakeNativeBackend("uncertain-token")
        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=lambda: backend,
            machine_id_provider=lambda: self.machine_id,
        )
        store._write_pending_metadata(store._native_transaction_metadata(backend))

        self.assertTrue(store.delete())

        self.assertIsNone(backend.token)
        self.assertFalse(store.path.exists())
        self.assertFalse(store.pending_path.exists())

    def test_pending_cleanup_retains_conflicting_native_primary(self):
        backend = FakeNativeBackend("pending-provider-token")
        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=lambda: backend,
            machine_id_provider=lambda: self.machine_id,
        )
        store._write_metadata({
            "version": credential_store.CREDENTIAL_FORMAT_VERSION,
            "backend": "native",
            "provider": "different-native-provider",
            "service": credential_store.CREDENTIAL_SERVICE,
            "account": credential_store.CREDENTIAL_ACCOUNT,
        })
        store._write_pending_metadata(
            store._native_transaction_metadata(backend)
        )

        with self.assertRaises(credential_store.NativeStoreUnavailable):
            store.delete()

        self.assertEqual("pending-provider-token", backend.token)
        self.assertFalse(backend.deleted)
        self.assertTrue(store.path.exists())
        self.assertTrue(store.pending_path.exists())

    def test_uncertain_fallback_upgrade_retains_cleanup_retry_state(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")

        class RollbackFailureBackend(FakeNativeBackend):
            def __init__(self):
                super().__init__()
                self.fail_next_read = False
                self.allow_cleanup = False

            def set(self, token):
                super().set(token)
                self.fail_next_read = True

            def get(self):
                if self.fail_next_read:
                    self.fail_next_read = False
                    raise credential_store.NativeStoreUnavailable(
                        "provider disappeared"
                    )
                return super().get()

            def delete(self):
                if not self.allow_cleanup:
                    raise credential_store.NativeStoreError("cleanup failed")
                return super().delete()

        backend = RollbackFailureBackend()
        factory_available = False

        def backend_factory():
            if not factory_available:
                raise credential_store.NativeStoreUnavailable("not available")
            return backend

        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=backend_factory,
            machine_id_provider=lambda: self.machine_id,
        )
        store.store("fallback-token")
        factory_available = True

        with self.assertRaises(credential_store.NativeRollbackError):
            store.read()

        metadata = json.loads(store.path.read_text(encoding="utf-8"))
        self.assertFalse(metadata["native_cleanup_pending"])
        self.assertEqual("fallback-token", store._decrypt_fallback(metadata))
        self.assertTrue(store.pending_path.exists())

        factory_available = False
        with self.assertRaises(credential_store.NativeStoreUnavailable):
            store.delete()
        self.assertTrue(store.path.exists())
        self.assertTrue(store.pending_path.exists())

        factory_available = True
        backend.allow_cleanup = True
        self.assertTrue(store.delete())
        self.assertIsNone(backend.token)
        self.assertFalse(store.path.exists())
        self.assertFalse(store.pending_path.exists())

    def test_fallback_upgrades_when_native_storage_becomes_available(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        backend = FakeNativeBackend()
        native_available = False

        def backend_factory():
            if not native_available:
                raise credential_store.NativeStoreUnavailable("not available")
            return backend

        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=backend_factory,
            machine_id_provider=lambda: self.machine_id,
        )
        store.store("github-token")
        native_available = True

        self.assertEqual("github-token", store.read())

        metadata = json.loads(store.path.read_text(encoding="utf-8"))
        self.assertEqual("native", metadata["backend"])
        self.assertEqual("github-token", backend.token)
        self.assertFalse(store.key_path.exists())

    def test_clean_fallback_delete_does_not_guess_native_provider(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        backend = FakeNativeBackend("stale-native-token")
        backend_factory = mock.Mock(return_value=backend)
        store = self._fallback_store()
        store.store("fallback-token")
        store._native_backend_factory = backend_factory

        self.assertTrue(store.delete())

        backend_factory.assert_not_called()
        self.assertEqual("stale-native-token", backend.token)
        self.assertFalse(backend.deleted)
        self.assertFalse(store.path.exists())

    def test_clean_legacy_machine_logout_does_not_guess_native_provider(self):
        backend = FakeNativeBackend("unrelated-token")
        backend_factory = mock.Mock(return_value=backend)
        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=backend_factory,
            machine_id_provider=lambda: b"linux:compat-machine-id",
        )
        self._write_legacy_machine_fixture(store)

        self.assertTrue(store.delete())

        backend_factory.assert_not_called()
        self.assertEqual("unrelated-token", backend.token)
        self.assertFalse(backend.deleted)
        self.assertFalse(store.path.exists())

    def test_legacy_deferred_logout_requires_the_recorded_native_provider(self):
        recorded_backend = FakeNativeBackend("stale-native-token")

        class DifferentBackend(FakeNativeBackend):
            name = "different-native"

        different_backend = DifferentBackend("unrelated-token")
        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=lambda: different_backend,
            machine_id_provider=lambda: b"linux:compat-machine-id",
        )
        self._write_legacy_pending_machine_fixture(store)

        with self.assertRaises(credential_store.NativeStoreUnavailable):
            store.delete()

        self.assertEqual("unrelated-token", different_backend.token)
        self.assertFalse(different_backend.deleted)
        self.assertEqual("stale-native-token", recorded_backend.token)
        self.assertTrue(store.path.exists())

        store._native_backend_factory = lambda: recorded_backend
        self.assertTrue(store.delete())
        self.assertIsNone(recorded_backend.token)
        self.assertFalse(store.path.exists())

    def test_delete_without_marker_only_removes_orphaned_local_key(self):
        backend = FakeNativeBackend("orphaned-token")
        backend_factory = mock.Mock(return_value=backend)
        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=backend_factory,
            machine_id_provider=lambda: self.machine_id,
        )
        store._load_or_create_local_key()

        self.assertTrue(store.delete())

        backend_factory.assert_not_called()
        self.assertEqual("orphaned-token", backend.token)
        self.assertFalse(backend.deleted)
        self.assertFalse(store.path.exists())
        self.assertFalse(store.key_path.exists())

    def test_failed_native_delete_keeps_valid_marker_for_retry(self):
        class DeleteFailureBackend(FakeNativeBackend):
            def delete(self):
                raise credential_store.NativeStoreError("credential is locked")

        backend = DeleteFailureBackend()
        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=lambda: backend,
            machine_id_provider=lambda: self.machine_id,
        )
        store.store("github-token")

        with self.assertRaises(credential_store.NativeStoreError):
            store.delete()

        self.assertEqual("github-token", backend.token)
        self.assertTrue(store.path.exists())

    def test_unverified_native_delete_keeps_valid_marker_for_retry(self):
        class UnverifiedDeleteBackend(FakeNativeBackend):
            def delete(self):
                self.deleted = True
                return True

        backend = UnverifiedDeleteBackend()
        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=lambda: backend,
            machine_id_provider=lambda: self.machine_id,
        )
        store.store("github-token")

        with self.assertRaises(credential_store.NativeStoreError):
            store.delete()

        self.assertEqual("github-token", backend.token)
        self.assertTrue(store.path.exists())

    def test_corrupt_metadata_is_reset_without_native_cleanup(self):
        self.directory.mkdir(parents=True)
        path = self.directory / credential_store.CREDENTIAL_FILE_NAME
        path.write_text('{"version": 99}', encoding="utf-8")
        backend = FakeNativeBackend("current-provider-token")
        backend_factory = mock.Mock(return_value=backend)
        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=backend_factory,
            machine_id_provider=lambda: self.machine_id,
        )
        store._load_or_create_local_key()

        with self.assertRaises(credential_store.CredentialCorrupt) as raised:
            store.delete()

        self.assertIn("manual native cleanup", str(raised.exception))
        self.assertEqual(
            store._cleanup_blocked_metadata(),
            json.loads(store.pending_path.read_text(encoding="utf-8")),
        )
        backend_factory.assert_not_called()
        self.assertEqual("current-provider-token", backend.token)
        self.assertFalse(backend.deleted)
        self.assertFalse(path.exists())
        self.assertFalse(store.key_path.exists())

        with self.assertRaises(credential_store.CredentialCorrupt):
            store.delete()

        backend_factory.assert_not_called()
        self.assertEqual("current-provider-token", backend.token)
        self.assertTrue(store.pending_path.exists())

    def test_native_provider_mismatch_fails_closed(self):
        backend = FakeNativeBackend()
        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=lambda: backend,
            machine_id_provider=lambda: self.machine_id,
        )
        store.store("github-token")
        metadata = json.loads(store.path.read_text(encoding="utf-8"))
        metadata["provider"] = "different-native"
        store.path.write_text(json.dumps(metadata), encoding="utf-8")

        with self.assertRaises(credential_store.NativeStoreUnavailable):
            store.read()

        with self.assertRaises(credential_store.NativeStoreUnavailable):
            store.delete()

        self.assertEqual("github-token", backend.token)
        self.assertFalse(backend.deleted)
        self.assertTrue(store.path.exists())

    def test_verified_login_falls_back_until_native_provider_recovers(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        backend = TemporarilyUnavailableBackend()

        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=lambda: backend,
            machine_id_provider=lambda: self.machine_id,
        )
        store.store("old-token")
        native_metadata = store.path.read_bytes()
        backend.available = False

        with self.assertRaises(credential_store.NativeStoreUnavailable):
            store.store("fresh-token")

        self.assertEqual(native_metadata, store.path.read_bytes())
        result = store.store("fresh-token", allow_recovery=True)

        self.assertTrue(result.degraded)
        self.assertEqual(credential_store.FALLBACK_BACKEND, result.backend)
        self.assertEqual("old-token", backend.token)
        metadata = json.loads(store.path.read_text(encoding="utf-8"))
        self.assertTrue(metadata["native_cleanup_pending"])
        self.assertEqual(
            backend.name,
            metadata["native_cleanup_provider"],
        )
        self.assertTrue(store.key_path.exists())
        self.assertEqual("fresh-token", store.read(include_native=False))
        self.assertEqual("fresh-token", store.read())

        store._native_backend_factory = self._unavailable
        repeated = store.store("newer-fallback", allow_recovery=True)
        repeated_metadata = json.loads(
            store.path.read_text(encoding="utf-8")
        )
        self.assertTrue(repeated.degraded)
        self.assertEqual(
            backend.name,
            repeated_metadata["native_cleanup_provider"],
        )
        self.assertEqual(
            "newer-fallback",
            store.read(include_native=False),
        )

        store._native_backend_factory = lambda: backend
        backend.available = True
        upgraded = store.store("newest-token", allow_recovery=True)

        self.assertFalse(upgraded.degraded)
        self.assertEqual("newest-token", backend.token)
        self.assertEqual("newest-token", store.read())
        metadata = json.loads(store.path.read_text(encoding="utf-8"))
        self.assertEqual("native", metadata["backend"])
        self.assertFalse(store.key_path.exists())

    def test_verified_login_rejects_unvalidated_native_provider(self):
        backend = FakeNativeBackend()
        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=lambda: backend,
            machine_id_provider=lambda: self.machine_id,
        )
        store.store("old-token")
        metadata = json.loads(store.path.read_text(encoding="utf-8"))
        metadata["provider"] = "attacker-controlled-provider"
        store.path.write_text(json.dumps(metadata), encoding="utf-8")
        metadata_before = store.path.read_bytes()
        warnings = []
        store._native_backend_factory = self._unavailable

        with self.assertRaises(credential_store.NativeStoreUnavailable):
            store.store(
                "verified-token",
                allow_recovery=True,
                before_fallback=lambda: warnings.append("fallback"),
            )

        self.assertEqual([], warnings)
        self.assertEqual(metadata_before, store.path.read_bytes())
        self.assertFalse(store.key_path.exists())
        self.assertEqual("old-token", backend.token)

    def test_verified_login_rejects_native_provider_mismatch_before_read(self):
        recorded_backend = FakeNativeBackend()
        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=lambda: recorded_backend,
            machine_id_provider=lambda: self.machine_id,
        )
        store.store("old-token")
        metadata_before = store.path.read_bytes()

        class DifferentBackend(FakeNativeBackend):
            name = "different-native"

            def get(self):
                raise AssertionError("an unverified provider must not be read")

            def set(self, token):
                raise AssertionError("an unverified provider must not be written")

            def delete(self):
                raise AssertionError("an unverified provider must not be deleted")

        store._native_backend_factory = lambda: DifferentBackend()

        with self.assertRaises(credential_store.NativeStoreUnavailable):
            store.store("verified-token", allow_recovery=True)

        self.assertEqual(metadata_before, store.path.read_bytes())
        self.assertFalse(store.key_path.exists())
        self.assertEqual("old-token", recorded_backend.token)

    def test_deferred_fallback_auto_heals_when_provider_recovers(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        backend = TemporarilyUnavailableBackend()

        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=lambda: backend,
            machine_id_provider=lambda: self.machine_id,
        )
        store.store("old-token")
        backend.available = False
        store.store("fresh-token", allow_recovery=True)
        self.assertEqual("old-token", backend.token)
        self.assertTrue(store.key_path.exists())

        backend.available = True
        self.assertEqual("fresh-token", store.read())

        metadata = json.loads(store.path.read_text(encoding="utf-8"))
        self.assertEqual("native", metadata["backend"])
        self.assertEqual(backend.name, metadata["provider"])
        self.assertEqual("fresh-token", backend.token)
        self.assertFalse(store.key_path.exists())
        self.assertFalse(store.pending_path.exists())

    def test_deferred_read_never_touches_a_different_native_provider(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        recorded_backend = TemporarilyUnavailableBackend()

        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=lambda: recorded_backend,
            machine_id_provider=lambda: self.machine_id,
        )
        store.store("old-token")
        recorded_backend.available = False
        store.store("fresh-token", allow_recovery=True)
        metadata_before = store.path.read_bytes()
        key_before = store.key_path.read_bytes()

        class DifferentBackend(FakeNativeBackend):
            name = "different-native"

            def get(self):
                raise AssertionError("different provider must not be read")

            def set(self, token):
                raise AssertionError("different provider must not be written")

            def delete(self):
                raise AssertionError("different provider must not be deleted")

        store._native_backend_factory = lambda: DifferentBackend("other-token")

        self.assertEqual("fresh-token", store.read())
        self.assertEqual(metadata_before, store.path.read_bytes())
        self.assertEqual(key_before, store.key_path.read_bytes())
        self.assertFalse(store.pending_path.exists())

    def test_failed_deferred_auto_heal_preserves_fallback_for_retry(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")

        class SetFailureBackend(TemporarilyUnavailableBackend):
            fail_next_write = False

            def set(self, token):
                if self.fail_next_write:
                    self.fail_next_write = False
                    raise credential_store.NativeStoreError("locked")
                super().set(token)

        backend = SetFailureBackend()
        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=lambda: backend,
            machine_id_provider=lambda: self.machine_id,
        )
        store.store("old-token")
        backend.available = False
        store.store("fresh-token", allow_recovery=True)
        metadata_before = store.path.read_bytes()
        key_before = store.key_path.read_bytes()

        backend.available = True
        backend.fail_next_write = True
        self.assertEqual("fresh-token", store.read())

        self.assertEqual("old-token", backend.token)
        self.assertEqual(metadata_before, store.path.read_bytes())
        self.assertEqual(key_before, store.key_path.read_bytes())
        self.assertFalse(store.pending_path.exists())

    def test_deferred_local_fallback_adds_machine_binding(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        backend = TemporarilyUnavailableBackend()
        machine_id = None

        def machine_id_provider():
            if machine_id is None:
                raise credential_store.NativeStoreUnavailable("not available")
            return machine_id

        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=lambda: backend,
            machine_id_provider=machine_id_provider,
        )
        store.store("old-token")
        backend.available = False
        store.store("fresh-token", allow_recovery=True)
        metadata = json.loads(store.path.read_text(encoding="utf-8"))
        self.assertEqual(
            credential_store.LOCAL_KEY_FALLBACK_BACKEND,
            metadata["backend"],
        )
        key_before = store.key_path.read_bytes()

        machine_id = self.machine_id
        self.assertEqual("fresh-token", store.read())

        metadata = json.loads(store.path.read_text(encoding="utf-8"))
        self.assertEqual(credential_store.FALLBACK_BACKEND, metadata["backend"])
        self.assertTrue(metadata["native_cleanup_pending"])
        self.assertEqual(
            backend.name,
            metadata["native_cleanup_provider"],
        )
        self.assertEqual(key_before, store.key_path.read_bytes())
        self.assertEqual("old-token", backend.token)
        self.assertEqual("fresh-token", store.read(include_native=False))

    def test_verified_login_uses_local_fallback_for_deferred_native_cleanup(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        backend = TemporarilyUnavailableBackend()

        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=lambda: backend,
            machine_id_provider=lambda: b"",
        )
        store.store("old-token")
        backend.available = False

        result = store.store("fresh-token", allow_recovery=True)

        self.assertEqual(
            credential_store.LOCAL_KEY_FALLBACK_BACKEND,
            result.backend,
        )
        self.assertEqual("old-token", backend.token)
        self.assertEqual("fresh-token", store.read(include_native=False))
        metadata = json.loads(store.path.read_text(encoding="utf-8"))
        self.assertTrue(metadata["native_cleanup_pending"])
        self.assertEqual(
            backend.name,
            metadata["native_cleanup_provider"],
        )
        self.assertTrue(store.key_path.exists())

    def test_machine_loss_does_not_discard_deferred_native_cleanup(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        backend = TemporarilyUnavailableBackend()
        machine_id = self.machine_id

        def machine_id_provider():
            if machine_id is None:
                raise credential_store.NativeStoreUnavailable("not available")
            return machine_id

        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=lambda: backend,
            machine_id_provider=machine_id_provider,
        )
        store.store("old-token")
        backend.available = False
        store.store("fresh-token", allow_recovery=True)
        metadata_before = store.path.read_bytes()
        machine_id = None

        with self.assertRaises(credential_store.NativeRollbackError):
            store.store("newest-token", allow_recovery=True)

        self.assertEqual(metadata_before, store.path.read_bytes())
        self.assertEqual("old-token", backend.token)
        self.assertTrue(store.key_path.exists())

    def test_deferred_cleanup_never_touches_a_different_native_provider(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        recorded_backend = TemporarilyUnavailableBackend()
        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=lambda: recorded_backend,
            machine_id_provider=lambda: self.machine_id,
        )
        store.store("old-token")
        recorded_backend.available = False
        result = store.store("fresh-token", allow_recovery=True)
        recorded_backend.available = True

        class DifferentBackend(FakeNativeBackend):
            name = "different-native"

            def get(self):
                raise AssertionError("different provider must not be read")

            def delete(self):
                raise AssertionError("different provider must not be deleted")

        different_backend = DifferentBackend("other-token")
        store._native_backend_factory = lambda: different_backend

        self.assertTrue(result.degraded)
        self.assertEqual("old-token", recorded_backend.token)
        metadata = json.loads(store.path.read_text(encoding="utf-8"))
        self.assertEqual(
            recorded_backend.name,
            metadata["native_cleanup_provider"],
        )
        with self.assertRaises(credential_store.NativeStoreUnavailable):
            store.delete()
        self.assertEqual("other-token", different_backend.token)
        self.assertTrue(store.path.exists())

        store._native_backend_factory = lambda: recorded_backend
        self.assertTrue(store.delete())
        self.assertIsNone(recorded_backend.token)
        self.assertFalse(store.path.exists())

    def test_native_operation_error_does_not_trigger_recovery_fallback(self):
        class LockedBackend(FakeNativeBackend):
            locked = False

            def get(self):
                if self.locked:
                    raise credential_store.NativeStoreError("locked")
                return super().get()

        backend = LockedBackend()
        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=lambda: backend,
            machine_id_provider=lambda: self.machine_id,
        )
        store.store("old-token")
        metadata_before = store.path.read_bytes()
        backend.locked = True

        with self.assertRaises(credential_store.NativeStoreError):
            store.store("fresh-token", allow_recovery=True)

        self.assertEqual("old-token", backend.token)
        self.assertEqual(metadata_before, store.path.read_bytes())

    def test_failed_native_recovery_fallback_restores_native_metadata(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        backend = TemporarilyUnavailableBackend()
        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=lambda: backend,
            machine_id_provider=lambda: self.machine_id,
        )
        store.store("old-token")
        metadata_before = json.loads(store.path.read_text(encoding="utf-8"))
        backend.available = False
        write_metadata = store._write_metadata

        def reject_fallback(metadata):
            if metadata.get("backend") in credential_store.FALLBACK_BACKENDS:
                raise OSError("disk full")
            return write_metadata(metadata)

        with (
            mock.patch.object(
                store,
                "_write_metadata",
                side_effect=reject_fallback,
            ),
            self.assertRaises(credential_store.CredentialStoreError),
        ):
            store.store("fresh-token", allow_recovery=True)

        self.assertEqual("old-token", backend.token)
        self.assertEqual(
            metadata_before,
            json.loads(store.path.read_text(encoding="utf-8")),
        )
        self.assertFalse(store.pending_path.exists())

    def test_deferred_cleanup_provider_is_authenticated(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        backend = TemporarilyUnavailableBackend()

        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=lambda: backend,
            machine_id_provider=lambda: self.machine_id,
        )
        store.store("old-token")
        backend.available = False
        store.store("fresh-token", allow_recovery=True)
        metadata = json.loads(store.path.read_text(encoding="utf-8"))
        metadata["native_cleanup_provider"] = "different-native"
        store.path.write_text(json.dumps(metadata), encoding="utf-8")

        with self.assertRaises(credential_store.CredentialCorrupt):
            store.read(include_native=False)

    def test_legacy_pending_fallback_without_provider_fails_closed(self):
        if credential_store._AES_BACKEND is None:
            self.skipTest("AES-GCM backend unavailable")
        store = self._fallback_store()
        store.store("old-token")
        metadata = json.loads(store.path.read_text(encoding="utf-8"))
        metadata["native_cleanup_pending"] = True
        store.path.write_text(json.dumps(metadata), encoding="utf-8")

        with self.assertRaises(credential_store.CredentialCorrupt):
            store.read(include_native=False)
        with self.assertRaises(credential_store.CredentialCorrupt):
            store.store("fresh-token", allow_recovery=True)

    def test_keyring_delete_error_does_not_claim_success_while_token_remains(self):
        class PasswordDeleteError(Exception):
            pass

        class Errors:
            pass

        class Backend:
            token = "github-token"

            def get_password(self, service, account):
                return self.token

            def delete_password(self, service, account):
                raise PasswordDeleteError("delete failed")

        backend = Backend()
        adapter = credential_store.KeyringBackend(
            backend,
            "test-keyring",
            Errors,
        )

        with self.assertRaises(credential_store.NativeStoreError):
            adapter.delete()

        self.assertEqual("github-token", backend.token)

    def test_keyring_delete_error_is_success_when_post_read_confirms_absence(self):
        class PasswordDeleteError(Exception):
            pass

        class Errors:
            pass

        class Backend:
            token = "github-token"

            def get_password(self, service, account):
                return self.token

            def delete_password(self, service, account):
                self.token = None
                raise PasswordDeleteError("response was lost")

        backend = Backend()
        adapter = credential_store.KeyringBackend(
            backend,
            "test-keyring",
            Errors,
        )

        self.assertTrue(adapter.delete())
        self.assertIsNone(backend.token)

    def test_no_marker_read_has_no_filesystem_side_effect(self):
        store = self._fallback_store()

        self.assertIsNone(store.read())

        self.assertFalse(self.directory.exists())

    def test_hkdf_derivation_is_stable_and_domain_separated(self):
        first = credential_store._hkdf_sha256(
            bytes(range(32)),
            b"linux:test-machine-id",
        )
        second = credential_store._hkdf_sha256(
            bytes(range(32)),
            b"linux:different-machine-id",
        )

        self.assertEqual(
            "70d615c39ee8d05832535b63aec247de7aff239b23487e2466bd078455009746",
            first.hex(),
        )
        self.assertNotEqual(first, second)

        master_key = bytes(range(32))
        seed = bytes(range(32, 64))
        machine_key = credential_store._machine_key_hkdf_sha256(
            master_key,
            seed,
            b"linux:test-machine-id",
        )
        local_key = credential_store._local_key_v2_hkdf_sha256(
            master_key,
            seed,
        )
        self.assertEqual(
            "5db4e371a81e1383d05be12148f919c77b1eff53dca9212f1c64fc2e4058b606",
            machine_key.hex(),
        )
        self.assertEqual(
            "a9d0b7068a628bdb4b440bb6d5caeb7dc66b718c21b084128d5dfabe930a6102",
            local_key.hex(),
        )
        self.assertNotEqual(machine_key, local_key)
        self.assertNotEqual(
            machine_key,
            credential_store._machine_key_hkdf_sha256(
                master_key,
                seed,
                b"linux:different-machine-id",
            ),
        )
        self.assertNotEqual(
            machine_key,
            credential_store._machine_key_hkdf_sha256(
                bytes(reversed(master_key)),
                seed,
                b"linux:test-machine-id",
            ),
        )

    def test_aes_gcm_self_test_exercises_selected_backend(self):
        if credential_store._AES_BACKEND is None:
            with self.assertRaises(credential_store.CredentialStoreError):
                credential_store.aes_gcm_self_test()
        else:
            self.assertEqual(
                credential_store._AES_BACKEND,
                credential_store.aes_gcm_self_test(),
            )

    def test_v2_fallback_is_cross_compatible_with_pycryptodome(self):
        try:
            from Cryptodome.Cipher import AES as fallback_aes
            backend_name = "pycryptodomex"
        except ImportError:
            try:
                from Crypto.Cipher import AES as fallback_aes
                backend_name = "pycryptodome"
            except ImportError:
                self.skipTest("PyCryptodome unavailable")
        store = self._fallback_store()
        store.store("default-backend-token")

        with (
            mock.patch.object(credential_store, "_AES_BACKEND", backend_name),
            mock.patch.object(credential_store, "_PYAES", fallback_aes),
        ):
            self.assertEqual(
                "default-backend-token",
                store.read(include_native=False),
            )
            store.store("pycryptodome-token")

        self.assertEqual(
            "pycryptodome-token",
            store.read(include_native=False),
        )

    def test_pycryptodome_reads_legacy_machine_v1_fixture(self):
        try:
            from Cryptodome.Cipher import AES as fallback_aes
            backend_name = "pycryptodomex"
        except ImportError:
            try:
                from Crypto.Cipher import AES as fallback_aes
                backend_name = "pycryptodome"
            except ImportError:
                self.skipTest("PyCryptodome unavailable")
        store = self._fallback_store(b"linux:compat-machine-id")
        self._write_legacy_machine_fixture(store)

        with (
            mock.patch.object(credential_store, "_AES_BACKEND", backend_name),
            mock.patch.object(credential_store, "_PYAES", fallback_aes),
        ):
            self.assertEqual(
                "pre-change-token",
                store.read(include_native=False),
            )

    def test_pycryptodome_local_key_fallback_round_trips(self):
        try:
            from Cryptodome.Cipher import AES as fallback_aes
            backend_name = "pycryptodomex"
        except ImportError:
            try:
                from Crypto.Cipher import AES as fallback_aes
                backend_name = "pycryptodome"
            except ImportError:
                self.skipTest("PyCryptodome unavailable")
        store = self._local_fallback_store()

        with (
            mock.patch.object(credential_store, "_AES_BACKEND", backend_name),
            mock.patch.object(credential_store, "_PYAES", fallback_aes),
        ):
            store.store("github-token")
            self.assertEqual("github-token", store.read())

    def test_pycryptodome_deferred_cleanup_provider_round_trips(self):
        try:
            from Cryptodome.Cipher import AES as fallback_aes
            backend_name = "pycryptodomex"
        except ImportError:
            try:
                from Crypto.Cipher import AES as fallback_aes
                backend_name = "pycryptodome"
            except ImportError:
                self.skipTest("PyCryptodome unavailable")
        backend = TemporarilyUnavailableBackend()

        store = credential_store.CredentialStore(
            self.directory,
            native_backend_factory=lambda: backend,
            machine_id_provider=lambda: self.machine_id,
        )

        with (
            mock.patch.object(credential_store, "_AES_BACKEND", backend_name),
            mock.patch.object(credential_store, "_PYAES", fallback_aes),
        ):
            store.store("old-token")
            backend.available = False
            store.store("fresh-token", allow_recovery=True)
            self.assertEqual("fresh-token", store.read(include_native=False))


if __name__ == "__main__":
    unittest.main()
