import hashlib
import json
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path


CLI_DIR = Path(__file__).resolve().parents[1]
if str(CLI_DIR) not in sys.path:
    sys.path.insert(0, str(CLI_DIR))

import abk  # noqa: E402


class ArtifactVerificationTests(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp_dir.cleanup)

    def _generate_key_and_signer(self):
        if abk._CRYPTO_BACKEND == "cryptography":
            from cryptography.hazmat.primitives import hashes, serialization
            from cryptography.hazmat.primitives.asymmetric import padding, rsa

            private_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
            public_key = private_key.public_key().public_bytes(
                encoding=serialization.Encoding.PEM,
                format=serialization.PublicFormat.SubjectPublicKeyInfo,
            ).decode("ascii")

            def sign(data):
                return private_key.sign(data, padding.PKCS1v15(), hashes.SHA256())

            return public_key, sign

        if abk._CRYPTO_BACKEND == "pycryptodome":
            try:
                from Cryptodome.Hash import SHA256
                from Cryptodome.PublicKey import RSA
                from Cryptodome.Signature import pkcs1_15
            except ImportError:
                from Crypto.Hash import SHA256
                from Crypto.PublicKey import RSA
                from Crypto.Signature import pkcs1_15

            private_key = RSA.generate(2048)
            public_key = private_key.publickey().export_key("PEM").decode("ascii")

            def sign(data):
                return pkcs1_15.new(private_key).sign(SHA256.new(data))

            return public_key, sign

        self.skipTest("artifact verification requires an RSA backend")

    def _write_signed_bundle(self, *, include_payload):
        payload_name = "kernel-image.zip"
        payload = b"signed ABK test payload\n"
        bundle = Path(self.temp_dir.name) / "artifact.bundle.zip"
        manifest = {
            "schema": 1,
            "bundle_name": bundle.name,
            "artifact_type": "KERNEL_IMG",
            "run_id": 12345,
            "payload_name": payload_name,
            "payload_sha256": hashlib.sha256(payload).hexdigest(),
            "payload_size_bytes": len(payload),
            "created_at": "2026-07-11T00:00:00Z",
        }
        manifest_bytes = json.dumps(
            manifest, ensure_ascii=False, sort_keys=True, separators=(",", ":")
        ).encode("utf-8")
        public_key, sign = self._generate_key_and_signer()

        with zipfile.ZipFile(bundle, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            if include_payload:
                archive.writestr(payload_name, payload)
            archive.writestr("ABK_BUNDLE_MANIFEST.json", manifest_bytes)
            archive.writestr("ABK_BUNDLE_MANIFEST.sig", sign(manifest_bytes))
        return bundle, public_key

    def test_valid_signed_bundle_is_verified(self):
        bundle, public_key = self._write_signed_bundle(include_payload=True)

        result = abk.verify_artifact_bundle(str(bundle), public_key)

        self.assertTrue(result["verified"], result)
        self.assertEqual("verified", result["status"])

    def test_valid_signature_with_missing_payload_is_rejected(self):
        """A signed manifest must not authenticate a different/missing payload."""
        bundle, public_key = self._write_signed_bundle(include_payload=False)

        result = abk.verify_artifact_bundle(str(bundle), public_key)

        self.assertFalse(result["verified"], result)
        self.assertNotEqual("verified", result["status"])

    def test_github_outer_archive_verifies_nested_bundle(self):
        bundle, public_key = self._write_signed_bundle(include_payload=True)
        outer = Path(self.temp_dir.name) / "artifact-77.zip"
        with zipfile.ZipFile(outer, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            archive.write(bundle, arcname=bundle.name)

        result = abk.verify_artifact_archive(outer, public_key, expected_run_id=12345)

        self.assertTrue(result["verified"], result)
        self.assertEqual("verified", result["status"])
        self.assertEqual(1, len(result["bundles"]))

    def test_bundle_from_wrong_run_is_rejected(self):
        bundle, public_key = self._write_signed_bundle(include_payload=True)

        result = abk.verify_artifact_bundle(
            bundle, public_key, expected_run_id=99999
        )

        self.assertFalse(result["verified"], result)


if __name__ == "__main__":
    unittest.main()
