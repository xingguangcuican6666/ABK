#!/usr/bin/env python3
"""Persistent GitHub credential storage for the ABK CLI."""

from __future__ import annotations

import base64
import binascii
import hashlib
import hmac
import json
import os
import re
import secrets
import stat
import subprocess
import sys
import tempfile
from dataclasses import dataclass
from pathlib import Path


try:
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM

    _AES_BACKEND = "cryptography"
    _PYAES = None
except ImportError:
    AESGCM = None
    try:
        from Cryptodome.Cipher import AES as _PYAES

        _AES_BACKEND = "pycryptodomex"
    except ImportError:
        try:
            from Crypto.Cipher import AES as _PYAES

            _AES_BACKEND = "pycryptodome"
        except ImportError:
            _PYAES = None
            _AES_BACKEND = None


CREDENTIAL_FILE_NAME = "credentials.json"
CREDENTIAL_PENDING_FILE_NAME = "credentials.pending.json"
CREDENTIAL_KEY_FILE_NAME = "credentials.key"
CREDENTIAL_FORMAT_VERSION = 1
CREDENTIAL_FALLBACK_FORMAT_VERSION = 2
CREDENTIAL_KEY_FORMAT_VERSION = 1
CREDENTIAL_SERVICE = "ABK CLI"
CREDENTIAL_ACCOUNT = "github.com"
CREDENTIAL_CLEANUP_BLOCKED_KIND = "native-credential-cleanup-blocked"
CREDENTIAL_CLEANUP_BLOCKED_STATE = "provider-unknown"
FALLBACK_BACKEND = "machine-bound-aes-gcm"
LOCAL_KEY_FALLBACK_BACKEND = "local-key-aes-gcm"
FALLBACK_BACKENDS = frozenset({FALLBACK_BACKEND, LOCAL_KEY_FALLBACK_BACKEND})
MAX_CREDENTIAL_FILE_SIZE = 64 * 1024
MAX_CREDENTIAL_KEY_FILE_SIZE = 256
MAX_TOKEN_SIZE = 4096
MAX_NATIVE_PROVIDER_SIZE = 256
_SEED_SIZE = 32
_LOCAL_KEY_SIZE = 32
_NONCE_SIZE = 12
_TAG_SIZE = 16
_HKDF_INFO = b"abk-cli/github-token/key/v1"
_LOCAL_KEY_HKDF_INFO = b"abk-cli/github-token/local-key/v1"
_MACHINE_KEY_HKDF_INFO = b"abk-cli/github-token/machine-local-key/v2"
_LOCAL_KEY_V2_HKDF_INFO = b"abk-cli/github-token/local-key/v2"
_MASTER_KEY_ID_CONTEXT = b"abk-cli/github-token/master-key-id/v2"
_AAD = b"abk-cli|github.com|credential|v1|hkdf-sha256|aes-256-gcm"
_LOCAL_KEY_AAD = (
    b"abk-cli|github.com|credential|local-key|v1|hkdf-sha256|aes-256-gcm"
)
_MACHINE_KEY_AAD = (
    b"abk-cli|github.com|credential|machine-local-key|v2|"
    b"hkdf-sha256|aes-256-gcm"
)
_LOCAL_KEY_V2_AAD = (
    b"abk-cli|github.com|credential|local-key|v2|hkdf-sha256|aes-256-gcm"
)


class CredentialStoreError(RuntimeError):
    """Base class for persistent credential storage failures."""


class NativeStoreUnavailable(CredentialStoreError):
    """Raised when no supported native credential store can be used."""


class NativeStoreError(CredentialStoreError):
    """Raised when an available native credential store rejects an operation."""


class NativeRollbackError(NativeStoreError):
    """Raised when a partially changed native credential cannot be restored."""


class CredentialCorrupt(CredentialStoreError):
    """Raised when encrypted credential metadata cannot be authenticated."""


class _MachineIdentifierUnavailable(CredentialCorrupt):
    """Raised when an otherwise structured machine-bound record cannot derive."""


@dataclass(frozen=True)
class StoreResult:
    backend: str
    degraded: bool
    location: str


class KeyringBackend:
    """Small adapter around one explicitly selected system keyring backend."""

    def __init__(self, backend, name, errors):
        self._backend = backend
        self.name = name
        self._errors = errors

    def _translate(self, operation, exc):
        unavailable = tuple(
            error
            for error in (
                getattr(self._errors, "NoKeyringError", None),
                getattr(self._errors, "InitError", None),
            )
            if isinstance(error, type)
        )
        if unavailable and isinstance(exc, unavailable):
            raise NativeStoreUnavailable(
                f"{self.name} is unavailable"
            ) from exc
        raise NativeStoreError(
            f"{self.name} could not {operation} the GitHub credential"
        ) from exc

    def get(self):
        try:
            return self._backend.get_password(
                CREDENTIAL_SERVICE,
                CREDENTIAL_ACCOUNT,
            )
        except Exception as exc:
            self._translate("read", exc)

    def set(self, token):
        try:
            self._backend.set_password(
                CREDENTIAL_SERVICE,
                CREDENTIAL_ACCOUNT,
                token,
            )
        except Exception as exc:
            self._translate("store", exc)

    def delete(self):
        existing = self.get()
        if existing is None:
            return False
        try:
            self._backend.delete_password(
                CREDENTIAL_SERVICE,
                CREDENTIAL_ACCOUNT,
            )
            return True
        except Exception as exc:
            try:
                if self.get() is None:
                    return True
            except CredentialStoreError:
                pass
            self._translate("delete", exc)


def create_native_backend(platform_name=None):
    """Return only ABK-approved OS credential backends.

    Generic keyring discovery is deliberately avoided because user-installed
    third-party backends may store secrets in plaintext.
    """
    platform_name = platform_name or sys.platform
    try:
        from keyring import errors

        if platform_name == "win32":
            from keyring.backends.Windows import WinVaultKeyring

            backend = WinVaultKeyring()
            name = "windows-credential-manager"
        elif platform_name == "darwin":
            from keyring.backends.macOS import Keyring

            backend = Keyring()
            name = "macos-keychain"
        elif platform_name.startswith("linux"):
            from keyring.backends.SecretService import Keyring

            backend = Keyring()
            name = "secret-service"
        else:
            raise NativeStoreUnavailable(
                f"no supported native credential store for {platform_name}"
            )

        try:
            priority = backend.priority
        except Exception as exc:
            raise NativeStoreUnavailable(
                f"{name} is unavailable"
            ) from exc
        if priority <= 0:
            raise NativeStoreUnavailable(f"{name} is unavailable")
        return KeyringBackend(backend, name, errors)
    except NativeStoreUnavailable:
        raise
    except (ImportError, ModuleNotFoundError) as exc:
        raise NativeStoreUnavailable(
            "system credential storage support is not installed"
        ) from exc
    except Exception as exc:
        raise NativeStoreUnavailable(
            "system credential storage is unavailable"
        ) from exc


def _machine_identifier(platform_name=None):
    platform_name = platform_name or sys.platform
    if platform_name == "win32":
        try:
            import winreg

            path = r"SOFTWARE\Microsoft\Cryptography"
            access = winreg.KEY_READ
            if hasattr(winreg, "KEY_WOW64_64KEY"):
                access |= winreg.KEY_WOW64_64KEY
            with winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE, path, 0, access) as key:
                value, _ = winreg.QueryValueEx(key, "MachineGuid")
        except (ImportError, OSError) as exc:
            raise NativeStoreUnavailable(
                "Windows MachineGuid is unavailable"
            ) from exc
        value = str(value).strip()
        if value:
            return f"windows:{value}".encode("utf-8")
    elif platform_name == "darwin":
        try:
            result = subprocess.run(
                ["/usr/sbin/ioreg", "-rd1", "-c", "IOPlatformExpertDevice"],
                check=False,
                capture_output=True,
                text=True,
                timeout=10,
            )
        except (OSError, subprocess.TimeoutExpired) as exc:
            raise NativeStoreUnavailable(
                "macOS platform UUID is unavailable"
            ) from exc
        match = re.search(r'"IOPlatformUUID"\s*=\s*"([^"]+)"', result.stdout)
        if result.returncode == 0 and match:
            return f"macos:{match.group(1)}".encode("utf-8")
    else:
        for path in (Path("/etc/machine-id"), Path("/var/lib/dbus/machine-id")):
            try:
                value = path.read_text(encoding="ascii").strip()
            except (OSError, UnicodeError):
                continue
            if value and value != "uninitialized":
                return f"linux:{value}".encode("ascii")
    raise NativeStoreUnavailable("a stable machine identifier is unavailable")


def _hkdf_sha256(seed, machine_id, length=32):
    salt = hashlib.sha256(b"abk-cli/machine/v1\0" + machine_id).digest()
    pseudorandom_key = hmac.new(salt, seed, hashlib.sha256).digest()
    output = b""
    previous = b""
    counter = 1
    while len(output) < length:
        previous = hmac.new(
            pseudorandom_key,
            previous + _HKDF_INFO + bytes((counter,)),
            hashlib.sha256,
        ).digest()
        output += previous
        counter += 1
    return output[:length]


def _local_key_hkdf_sha256(master_key, seed, length=32):
    """Derive a legacy v1 record key from a separate random master key."""
    salt = hashlib.sha256(b"abk-cli/local-key/v1\0" + seed).digest()
    pseudorandom_key = hmac.new(salt, master_key, hashlib.sha256).digest()
    output = b""
    previous = b""
    counter = 1
    while len(output) < length:
        previous = hmac.new(
            pseudorandom_key,
            previous + _LOCAL_KEY_HKDF_INFO + bytes((counter,)),
            hashlib.sha256,
        ).digest()
        output += previous
        counter += 1
    return output[:length]


def _machine_key_hkdf_sha256(master_key, seed, machine_id, length=32):
    """Derive a v2 record key from a secret master and machine binding."""
    salt = hashlib.sha256(
        b"abk-cli/machine-local-key/v2\0" + machine_id + b"\0" + seed
    ).digest()
    pseudorandom_key = hmac.new(salt, master_key, hashlib.sha256).digest()
    output = b""
    previous = b""
    counter = 1
    while len(output) < length:
        previous = hmac.new(
            pseudorandom_key,
            previous + _MACHINE_KEY_HKDF_INFO + bytes((counter,)),
            hashlib.sha256,
        ).digest()
        output += previous
        counter += 1
    return output[:length]


def _local_key_v2_hkdf_sha256(master_key, seed, length=32):
    """Derive a v2 record key without a machine identifier."""
    salt = hashlib.sha256(b"abk-cli/local-key/v2\0" + seed).digest()
    pseudorandom_key = hmac.new(salt, master_key, hashlib.sha256).digest()
    output = b""
    previous = b""
    counter = 1
    while len(output) < length:
        previous = hmac.new(
            pseudorandom_key,
            previous + _LOCAL_KEY_V2_HKDF_INFO + bytes((counter,)),
            hashlib.sha256,
        ).digest()
        output += previous
        counter += 1
    return output[:length]


def _master_key_id(master_key):
    """Return a public commitment to a uniformly random local master key."""
    return hmac.new(
        master_key,
        _MASTER_KEY_ID_CONTEXT,
        hashlib.sha256,
    ).digest()


def _valid_native_provider(value):
    if not isinstance(value, str) or not value:
        return False
    try:
        encoded = value.encode("utf-8")
    except UnicodeError:
        return False
    return len(encoded) <= MAX_NATIVE_PROVIDER_SIZE


def _fallback_aad(base, native_cleanup_pending, native_cleanup_provider):
    state = b"pending" if native_cleanup_pending else b"clean"
    aad = base + b"|native-cleanup=" + state
    if native_cleanup_provider is not None:
        encoded_provider = native_cleanup_provider.encode("utf-8")
        aad += (
            b"|native-provider="
            + len(encoded_provider).to_bytes(2, "big")
            + encoded_provider
        )
    return aad


def _credential_aad(
    native_cleanup_pending=False,
    native_cleanup_provider=None,
):
    return _fallback_aad(
        _AAD,
        native_cleanup_pending,
        native_cleanup_provider,
    )


def _local_key_credential_aad(
    native_cleanup_pending=False,
    native_cleanup_provider=None,
):
    return _fallback_aad(
        _LOCAL_KEY_AAD,
        native_cleanup_pending,
        native_cleanup_provider,
    )


def _machine_key_credential_aad(
    key_id,
    native_cleanup_pending=False,
    native_cleanup_provider=None,
):
    return _fallback_aad(
        _MACHINE_KEY_AAD + b"|master-key-id=" + key_id,
        native_cleanup_pending,
        native_cleanup_provider,
    )


def _local_key_v2_credential_aad(
    key_id,
    native_cleanup_pending=False,
    native_cleanup_provider=None,
):
    return _fallback_aad(
        _LOCAL_KEY_V2_AAD + b"|master-key-id=" + key_id,
        native_cleanup_pending,
        native_cleanup_provider,
    )


def _encrypt_aes_gcm(key, nonce, plaintext, aad=None):
    aad = _credential_aad() if aad is None else aad
    if _AES_BACKEND == "cryptography":
        encrypted = AESGCM(key).encrypt(nonce, plaintext, aad)
        return encrypted[:-_TAG_SIZE], encrypted[-_TAG_SIZE:]
    if _AES_BACKEND in {"pycryptodome", "pycryptodomex"}:
        cipher = _PYAES.new(key, _PYAES.MODE_GCM, nonce=nonce, mac_len=_TAG_SIZE)
        cipher.update(aad)
        return cipher.encrypt_and_digest(plaintext)
    raise CredentialStoreError(
        "persistent credentials require cryptography or PyCryptodome AES-GCM"
    )


def _decrypt_aes_gcm(key, nonce, ciphertext, tag, aad=None):
    aad = _credential_aad() if aad is None else aad
    try:
        if _AES_BACKEND == "cryptography":
            return AESGCM(key).decrypt(nonce, ciphertext + tag, aad)
        if _AES_BACKEND in {"pycryptodome", "pycryptodomex"}:
            cipher = _PYAES.new(
                key,
                _PYAES.MODE_GCM,
                nonce=nonce,
                mac_len=_TAG_SIZE,
            )
            cipher.update(aad)
            return cipher.decrypt_and_verify(ciphertext, tag)
    except Exception as exc:
        raise CredentialCorrupt(
            "the encrypted GitHub credential failed authentication"
        ) from exc
    raise CredentialStoreError(
        "persistent credentials require cryptography or PyCryptodome AES-GCM"
    )


def aes_gcm_self_test():
    """Exercise the AES-GCM implementation included in a source or frozen CLI."""
    key = hashlib.sha256(b"abk-cli/credential-self-test/key").digest()
    nonce = hashlib.sha256(b"abk-cli/credential-self-test/nonce").digest()[:12]
    plaintext = b"abk-cli-credential-self-test"
    ciphertext, tag = _encrypt_aes_gcm(key, nonce, plaintext)
    if _decrypt_aes_gcm(key, nonce, ciphertext, tag) != plaintext:
        raise CredentialStoreError("credential AES-GCM self-test failed")
    return _AES_BACKEND


def _encode(value):
    return base64.b64encode(value).decode("ascii")


def _decode(value, *, name, expected_size=None, maximum_size=None):
    if not isinstance(value, str):
        raise CredentialCorrupt(f"credential field {name} is invalid")
    try:
        decoded = base64.b64decode(value, validate=True)
    except (ValueError, binascii.Error) as exc:
        raise CredentialCorrupt(
            f"credential field {name} is not valid Base64"
        ) from exc
    if expected_size is not None and len(decoded) != expected_size:
        raise CredentialCorrupt(f"credential field {name} has an invalid size")
    if maximum_size is not None and len(decoded) > maximum_size:
        raise CredentialCorrupt(f"credential field {name} is too large")
    return decoded


class CredentialStore:
    def __init__(
        self,
        directory,
        *,
        native_backend_factory=create_native_backend,
        machine_id_provider=_machine_identifier,
    ):
        self.directory = Path(directory)
        self.path = self.directory / CREDENTIAL_FILE_NAME
        self.pending_path = self.directory / CREDENTIAL_PENDING_FILE_NAME
        self.key_path = self.directory / CREDENTIAL_KEY_FILE_NAME
        self._native_backend_factory = native_backend_factory
        self._machine_id_provider = machine_id_provider

    def _read_metadata(self):
        try:
            try:
                file_status = self.path.lstat()
            except FileNotFoundError:
                return None
            if not stat.S_ISREG(file_status.st_mode):
                raise CredentialCorrupt(
                    "credential metadata is not a regular file"
                )
            if os.name != "nt":
                self.directory.chmod(0o700)
                self.path.chmod(0o600)
            if file_status.st_size > MAX_CREDENTIAL_FILE_SIZE:
                raise CredentialCorrupt("credential metadata is too large")
            metadata = json.loads(self.path.read_text(encoding="utf-8"))
        except CredentialCorrupt:
            raise
        except (OSError, UnicodeError, json.JSONDecodeError) as exc:
            raise CredentialCorrupt("credential metadata is unreadable") from exc
        if not isinstance(metadata, dict):
            raise CredentialCorrupt("credential metadata is invalid")
        version = metadata.get("version")
        supported_fallback = (
            version == CREDENTIAL_FALLBACK_FORMAT_VERSION
            and metadata.get("backend") in FALLBACK_BACKENDS
        )
        if version != CREDENTIAL_FORMAT_VERSION and not supported_fallback:
            raise CredentialCorrupt("credential metadata version is unsupported")
        return metadata

    def _read_pending_document(self):
        try:
            try:
                file_status = self.pending_path.lstat()
            except FileNotFoundError:
                return None
            if not stat.S_ISREG(file_status.st_mode):
                raise CredentialCorrupt(
                    "native credential transaction marker is not a regular file"
                )
            if os.name != "nt":
                self.directory.chmod(0o700)
                self.pending_path.chmod(0o600)
            if file_status.st_size > MAX_CREDENTIAL_FILE_SIZE:
                raise CredentialCorrupt(
                    "native credential transaction marker is too large"
                )
            metadata = json.loads(
                self.pending_path.read_text(encoding="utf-8")
            )
        except CredentialCorrupt:
            raise
        except (OSError, UnicodeError, json.JSONDecodeError) as exc:
            raise CredentialCorrupt(
                "native credential transaction marker is unreadable"
            ) from exc
        return metadata

    def _cleanup_blocked_metadata(self):
        return {
            "version": CREDENTIAL_FORMAT_VERSION,
            "kind": CREDENTIAL_CLEANUP_BLOCKED_KIND,
            "state": CREDENTIAL_CLEANUP_BLOCKED_STATE,
        }

    def _is_cleanup_blocked(self, metadata):
        return metadata == self._cleanup_blocked_metadata()

    def _read_pending_metadata(self):
        metadata = self._read_pending_document()
        if metadata is not None:
            if self._is_cleanup_blocked(metadata):
                raise CredentialCorrupt(
                    "native credential cleanup requires manual intervention"
                )
            self._validate_native_transaction(metadata)
        return metadata

    def _write_json_file(self, path, metadata, *, prefix):
        self.directory.mkdir(parents=True, exist_ok=True, mode=0o700)
        if os.name != "nt":
            self.directory.chmod(0o700)
        payload = json.dumps(
            metadata,
            indent=2,
            ensure_ascii=True,
            sort_keys=True,
        ) + "\n"
        fd, temporary_name = tempfile.mkstemp(
            prefix=prefix,
            suffix=".tmp",
            dir=self.directory,
        )
        try:
            if os.name != "nt":
                os.fchmod(fd, 0o600)
            with os.fdopen(fd, "w", encoding="utf-8") as stream:
                fd = None
                stream.write(payload)
                stream.flush()
                os.fsync(stream.fileno())
            os.replace(temporary_name, path)
            self._fsync_directory()
        finally:
            if fd is not None:
                os.close(fd)
            try:
                Path(temporary_name).unlink()
            except FileNotFoundError:
                pass

    def _fsync_directory(self):
        if os.name == "nt":
            return
        flags = os.O_RDONLY | getattr(os, "O_DIRECTORY", 0)
        directory_fd = os.open(self.directory, flags)
        try:
            os.fsync(directory_fd)
        finally:
            os.close(directory_fd)

    def _write_metadata(self, metadata):
        self._write_json_file(
            self.path,
            metadata,
            prefix=".credentials-",
        )

    def _write_pending_metadata(self, metadata):
        self._write_json_file(
            self.pending_path,
            metadata,
            prefix=".credential-pending-",
        )

    def _remove_file(self, path, *, remove_error, sync_error):
        removed = True
        try:
            path.unlink()
        except FileNotFoundError:
            removed = False
        except OSError as exc:
            raise CredentialStoreError(remove_error) from exc
        try:
            self._fsync_directory()
        except OSError as exc:
            if not removed and isinstance(exc, FileNotFoundError):
                # A stateless store has no directory entry to make durable.
                return False
            raise CredentialStoreError(sync_error) from exc
        return removed

    def _remove_metadata(self):
        return self._remove_file(
            self.path,
            remove_error="credential metadata could not be removed",
            sync_error=(
                "credential metadata removal could not be synchronized"
            ),
        )

    def _remove_pending_metadata(self):
        return self._remove_file(
            self.pending_path,
            remove_error=(
                "native credential transaction marker could not be removed"
            ),
            sync_error=(
                "native credential transaction marker removal could not be "
                "synchronized"
            ),
        )

    def _read_local_key(self, *, required=True):
        try:
            try:
                file_status = self.key_path.lstat()
            except FileNotFoundError:
                if required:
                    raise CredentialCorrupt(
                        "the local credential encryption key is missing"
                    )
                return None
            if not stat.S_ISREG(file_status.st_mode):
                raise CredentialCorrupt(
                    "the local credential encryption key is not a regular file"
                )
            if os.name != "nt":
                self.directory.chmod(0o700)
                self.key_path.chmod(0o600)
            if file_status.st_size > MAX_CREDENTIAL_KEY_FILE_SIZE:
                raise CredentialCorrupt(
                    "the local credential encryption key is too large"
                )
            key_metadata = json.loads(
                self.key_path.read_text(encoding="utf-8")
            )
        except CredentialCorrupt:
            raise
        except (OSError, UnicodeError, json.JSONDecodeError) as exc:
            raise CredentialCorrupt(
                "the local credential encryption key is unreadable"
            ) from exc
        if (
            not isinstance(key_metadata, dict)
            or set(key_metadata) != {"version", "kind", "key"}
            or key_metadata.get("version") != CREDENTIAL_KEY_FORMAT_VERSION
            or key_metadata.get("kind") != "local-credential-master-key"
        ):
            raise CredentialCorrupt(
                "the local credential encryption key metadata is invalid"
            )
        return _decode(
            key_metadata["key"],
            name="local encryption key",
            expected_size=_LOCAL_KEY_SIZE,
        )

    def _load_or_create_local_key(self):
        existing_key = self._read_local_key(required=False)
        if existing_key is not None:
            return existing_key

        try:
            key = secrets.token_bytes(_LOCAL_KEY_SIZE)
            key_metadata = {
                "version": CREDENTIAL_KEY_FORMAT_VERSION,
                "kind": "local-credential-master-key",
                "key": _encode(key),
            }
            self._write_json_file(
                self.key_path,
                key_metadata,
                prefix=".credential-key-",
            )
        except Exception as exc:
            raise CredentialStoreError(
                "the local credential encryption key could not be persisted"
            ) from exc

        persisted_key = self._read_local_key()
        if not hmac.compare_digest(persisted_key, key):
            raise CredentialStoreError(
                "the local credential encryption key failed verification"
            )
        return persisted_key

    def _remove_local_key(self):
        return self._remove_file(
            self.key_path,
            remove_error=(
                "the local credential encryption key could not be removed"
            ),
            sync_error=(
                "the local credential encryption key removal could not be "
                "synchronized"
            ),
        )

    def _remove_unused_local_key(self):
        try:
            return self._remove_local_key()
        except CredentialStoreError:
            # An unpaired random key contains no credential. Logout retries
            # cleanup without making an otherwise valid store operation fail.
            return False

    def _validated_machine_id(self):
        machine_id = self._machine_id_provider()
        if not isinstance(machine_id, bytes) or not machine_id:
            raise NativeStoreUnavailable(
                "a stable machine identifier is unavailable"
            )
        return machine_id

    def _select_new_fallback_backend(self):
        try:
            return FALLBACK_BACKEND, self._validated_machine_id()
        except NativeStoreUnavailable:
            return LOCAL_KEY_FALLBACK_BACKEND, None

    def _fallback_metadata(
        self,
        token,
        *,
        backend=FALLBACK_BACKEND,
        machine_id=None,
        native_cleanup_pending=False,
        native_cleanup_provider=None,
    ):
        if _AES_BACKEND is None:
            raise CredentialStoreError(
                "persistent credentials require an AES-GCM backend"
            )
        if native_cleanup_pending:
            if not _valid_native_provider(native_cleanup_provider):
                raise CredentialStoreError(
                    "the deferred native credential provider is invalid"
                )
        elif native_cleanup_provider is not None:
            raise CredentialStoreError(
                "a native cleanup provider requires pending cleanup"
            )
        encoded_token = token.encode("utf-8")
        if not encoded_token or len(encoded_token) > MAX_TOKEN_SIZE:
            raise CredentialStoreError("the GitHub credential has an invalid size")
        seed = secrets.token_bytes(_SEED_SIZE)
        nonce = secrets.token_bytes(_NONCE_SIZE)
        if backend == FALLBACK_BACKEND:
            master_key = self._load_or_create_local_key()
            key_id = _master_key_id(master_key)
            machine_id = machine_id or self._validated_machine_id()
            key = _machine_key_hkdf_sha256(
                master_key,
                seed,
                machine_id,
            )
            aad = _machine_key_credential_aad(
                key_id,
                native_cleanup_pending,
                native_cleanup_provider,
            )
        elif backend == LOCAL_KEY_FALLBACK_BACKEND:
            master_key = self._load_or_create_local_key()
            key_id = _master_key_id(master_key)
            key = _local_key_v2_hkdf_sha256(master_key, seed)
            aad = _local_key_v2_credential_aad(
                key_id,
                native_cleanup_pending,
                native_cleanup_provider,
            )
        else:
            raise CredentialStoreError("credential fallback backend is unsupported")
        ciphertext, tag = _encrypt_aes_gcm(
            key,
            nonce,
            encoded_token,
            aad,
        )
        metadata = {
            "version": CREDENTIAL_FALLBACK_FORMAT_VERSION,
            "backend": backend,
            "native_cleanup_pending": native_cleanup_pending,
            "kdf": "hkdf-sha256",
            "cipher": "aes-256-gcm",
            "seed": _encode(seed),
            "nonce": _encode(nonce),
            "ciphertext": _encode(ciphertext),
            "tag": _encode(tag),
            "key_id": _encode(key_id),
        }
        if native_cleanup_pending:
            metadata["native_cleanup_provider"] = native_cleanup_provider
        return metadata

    def _decrypt_fallback(self, metadata):
        expected = {
            "version",
            "backend",
            "native_cleanup_pending",
            "kdf",
            "cipher",
            "seed",
            "nonce",
            "ciphertext",
            "tag",
        }
        native_cleanup_pending = metadata.get("native_cleanup_pending")
        if native_cleanup_pending is True:
            expected.add("native_cleanup_provider")
        if metadata.get("version") == CREDENTIAL_FALLBACK_FORMAT_VERSION:
            expected.add("key_id")
        if set(metadata) != expected:
            raise CredentialCorrupt("credential metadata fields are invalid")
        backend = metadata.get("backend")
        if (
            backend not in FALLBACK_BACKENDS
            or not isinstance(native_cleanup_pending, bool)
            or metadata.get("kdf") != "hkdf-sha256"
            or metadata.get("cipher") != "aes-256-gcm"
        ):
            raise CredentialCorrupt("credential algorithms are unsupported")
        native_cleanup_provider = None
        if native_cleanup_pending:
            native_cleanup_provider = metadata.get(
                "native_cleanup_provider"
            )
            if not _valid_native_provider(native_cleanup_provider):
                raise CredentialCorrupt(
                    "the deferred native credential provider is invalid"
                )
        seed = _decode(metadata["seed"], name="seed", expected_size=_SEED_SIZE)
        nonce = _decode(metadata["nonce"], name="nonce", expected_size=_NONCE_SIZE)
        ciphertext = _decode(
            metadata["ciphertext"],
            name="ciphertext",
            maximum_size=MAX_TOKEN_SIZE,
        )
        tag = _decode(metadata["tag"], name="tag", expected_size=_TAG_SIZE)
        version = metadata.get("version")
        if version not in {
            CREDENTIAL_FORMAT_VERSION,
            CREDENTIAL_FALLBACK_FORMAT_VERSION,
        }:
            raise CredentialCorrupt("credential metadata version is unsupported")
        if (
            version == CREDENTIAL_FORMAT_VERSION
            and backend == FALLBACK_BACKEND
        ):
            try:
                machine_id = self._validated_machine_id()
            except NativeStoreUnavailable as exc:
                raise _MachineIdentifierUnavailable(
                    "the machine identifier is unavailable"
                ) from exc
            key = _hkdf_sha256(seed, machine_id)
            aad = _credential_aad(
                native_cleanup_pending,
                native_cleanup_provider,
            )
        elif (
            version == CREDENTIAL_FORMAT_VERSION
            and backend == LOCAL_KEY_FALLBACK_BACKEND
        ):
            master_key = self._read_local_key()
            key = _local_key_hkdf_sha256(master_key, seed)
            aad = _local_key_credential_aad(
                native_cleanup_pending,
                native_cleanup_provider,
            )
        elif backend == FALLBACK_BACKEND:
            # Read the secret first so a missing or damaged key can never be
            # misclassified as the recoverable machine-identifier case.
            master_key = self._read_local_key()
            key_id = _decode(
                metadata["key_id"],
                name="master key identifier",
                expected_size=hashlib.sha256().digest_size,
            )
            if not hmac.compare_digest(key_id, _master_key_id(master_key)):
                raise CredentialCorrupt(
                    "the local credential encryption key does not match"
                )
            try:
                machine_id = self._validated_machine_id()
            except NativeStoreUnavailable as exc:
                raise _MachineIdentifierUnavailable(
                    "the machine identifier is unavailable"
                ) from exc
            key = _machine_key_hkdf_sha256(
                master_key,
                seed,
                machine_id,
            )
            aad = _machine_key_credential_aad(
                key_id,
                native_cleanup_pending,
                native_cleanup_provider,
            )
        else:
            master_key = self._read_local_key()
            key_id = _decode(
                metadata["key_id"],
                name="master key identifier",
                expected_size=hashlib.sha256().digest_size,
            )
            if not hmac.compare_digest(key_id, _master_key_id(master_key)):
                raise CredentialCorrupt(
                    "the local credential encryption key does not match"
                )
            key = _local_key_v2_hkdf_sha256(master_key, seed)
            aad = _local_key_v2_credential_aad(
                key_id,
                native_cleanup_pending,
                native_cleanup_provider,
            )
        plaintext = _decrypt_aes_gcm(
            key,
            nonce,
            ciphertext,
            tag,
            aad,
        )
        try:
            token = plaintext.decode("utf-8")
        except UnicodeError as exc:
            raise CredentialCorrupt("the decrypted GitHub credential is invalid") from exc
        if not token or len(plaintext) > MAX_TOKEN_SIZE:
            raise CredentialCorrupt("the decrypted GitHub credential has an invalid size")
        return token

    def read(self, *, include_native=True):
        if self._read_pending_metadata() is not None:
            raise NativeRollbackError("native credential cleanup is pending")
        metadata = self._read_metadata()
        if metadata is None:
            return None
        backend_name = metadata.get("backend")
        if backend_name in FALLBACK_BACKENDS:
            token = self._decrypt_fallback(metadata)
            if include_native:
                required_native_provider = None
                if metadata["native_cleanup_pending"]:
                    # Only the provider authenticated in the fallback record
                    # may replace its stale native credential.
                    required_native_provider = metadata[
                        "native_cleanup_provider"
                    ]
                upgraded = self._upgrade_fallback_to_native(
                    token,
                    required_native_provider=required_native_provider,
                )
                if (
                    not upgraded
                    and (
                        backend_name == LOCAL_KEY_FALLBACK_BACKEND
                        or metadata["version"] == CREDENTIAL_FORMAT_VERSION
                    )
                ):
                    migrated = self._upgrade_fallback_to_machine_key(
                        token,
                        metadata,
                    )
                    if (
                        not migrated
                        and backend_name == FALLBACK_BACKEND
                        and metadata["version"] == CREDENTIAL_FORMAT_VERSION
                    ):
                        self._remove_unused_local_key()
            return token
        if backend_name == "native":
            expected = {"version", "backend", "provider", "service", "account"}
            if set(metadata) != expected:
                raise CredentialCorrupt("native credential metadata is invalid")
            if (
                metadata.get("service") != CREDENTIAL_SERVICE
                or metadata.get("account") != CREDENTIAL_ACCOUNT
                or not _valid_native_provider(metadata.get("provider"))
            ):
                raise CredentialCorrupt("native credential identity is invalid")
            if not include_native:
                return None
            backend = self._native_backend_factory()
            if metadata.get("provider") != backend.name:
                raise NativeStoreUnavailable(
                    "the configured native credential provider is unavailable"
                )
            token = backend.get()
            if token is not None and not isinstance(token, str):
                raise NativeStoreError(
                    f"{backend.name} returned an invalid GitHub credential"
                )
            self._remove_unused_local_key()
            return token
        raise CredentialCorrupt("credential backend is unsupported")

    def _native_metadata(self, backend):
        return {
            "version": CREDENTIAL_FORMAT_VERSION,
            "backend": "native",
            "provider": backend.name,
            "service": CREDENTIAL_SERVICE,
            "account": CREDENTIAL_ACCOUNT,
        }

    def _native_transaction_metadata(self, backend):
        return {
            "version": CREDENTIAL_FORMAT_VERSION,
            "kind": "native-credential-transaction",
            "state": "cleanup-required",
            "provider": backend.name,
            "service": CREDENTIAL_SERVICE,
            "account": CREDENTIAL_ACCOUNT,
        }

    def _validate_native_transaction(self, metadata):
        expected = {
            "version",
            "kind",
            "state",
            "provider",
            "service",
            "account",
        }
        if (
            not isinstance(metadata, dict)
            or set(metadata) != expected
            or metadata.get("version") != CREDENTIAL_FORMAT_VERSION
            or metadata.get("kind") != "native-credential-transaction"
            or metadata.get("state") != "cleanup-required"
            or not isinstance(metadata.get("provider"), str)
            or not metadata["provider"]
            or metadata.get("service") != CREDENTIAL_SERVICE
            or metadata.get("account") != CREDENTIAL_ACCOUNT
        ):
            raise CredentialCorrupt(
                "native credential transaction marker is invalid"
            )

    def _restore_native_credential(self, backend, previous_token):
        try:
            if previous_token is None:
                backend.delete()
            else:
                backend.set(previous_token)
            restored = backend.get()
        except CredentialStoreError as exc:
            raise NativeRollbackError(
                f"{backend.name} could not restore the previous GitHub credential"
            ) from exc
        if previous_token is None:
            restored_ok = restored is None
        else:
            restored_ok = (
                isinstance(restored, str)
                and hmac.compare_digest(restored, previous_token)
            )
        if not restored_ok:
            raise NativeRollbackError(
                f"{backend.name} did not verify the restored GitHub credential"
            )

    def _replace_native_credential(self, backend, token, previous_token):
        try:
            backend.set(token)
            stored = backend.get()
            if not isinstance(stored, str) or not hmac.compare_digest(stored, token):
                raise NativeStoreError(
                    f"{backend.name} did not verify the stored GitHub credential"
                )
        except CredentialStoreError:
            try:
                self._restore_native_credential(backend, previous_token)
            except NativeRollbackError as rollback_exc:
                raise NativeRollbackError(
                    f"{backend.name} failed to store the GitHub credential and "
                    "could not restore its previous value"
                ) from rollback_exc
            raise

    def _restore_primary_metadata(self, existing_metadata):
        try:
            current_metadata = self._read_metadata()
        except CredentialStoreError:
            current_metadata = object()
        if current_metadata == existing_metadata:
            return
        if existing_metadata is None:
            self._remove_metadata()
        else:
            self._write_metadata(existing_metadata)
        if self._read_metadata() != existing_metadata:
            raise CredentialStoreError(
                "previous credential metadata could not be restored"
            )

    def _persist_fallback_metadata(self, token, metadata, existing_metadata):
        try:
            self._write_metadata(metadata)
            persisted_metadata = self._read_metadata()
            if persisted_metadata != metadata:
                raise CredentialStoreError(
                    "encrypted credential metadata failed verification"
                )
            persisted_token = self._decrypt_fallback(persisted_metadata)
            if not hmac.compare_digest(persisted_token, token):
                raise CredentialStoreError(
                    "the encrypted GitHub credential failed verification"
                )
        except Exception as exc:
            try:
                self._restore_primary_metadata(existing_metadata)
            except Exception as rollback_exc:
                raise CredentialStoreError(
                    "encrypted credential metadata failed and the previous "
                    "state could not be restored"
                ) from rollback_exc
            if isinstance(exc, CredentialStoreError):
                raise
            raise CredentialStoreError(
                "encrypted credential metadata could not be persisted"
            ) from exc

    def _store_native_transaction(
        self,
        backend,
        token,
        previous_token,
        existing_metadata,
    ):
        pending_metadata = self._native_transaction_metadata(backend)
        try:
            self._write_pending_metadata(pending_metadata)
            if self._read_pending_metadata() != pending_metadata:
                raise CredentialStoreError(
                    "native credential transaction marker failed verification"
                )
        except Exception as exc:
            raise CredentialStoreError(
                "native credential transaction could not be started"
            ) from exc

        try:
            self._replace_native_credential(backend, token, previous_token)
        except NativeRollbackError:
            # The write-ahead marker remains authoritative and prevents any
            # later process from reading an uncertain native credential.
            raise
        except CredentialStoreError:
            try:
                self._remove_pending_metadata()
            except CredentialStoreError as cleanup_exc:
                raise NativeRollbackError(
                    "native credential rollback succeeded, but its transaction "
                    "marker could not be cleared"
                ) from cleanup_exc
            raise

        try:
            self._write_metadata(self._native_metadata(backend))
        except Exception as exc:
            try:
                self._restore_native_credential(backend, previous_token)
                self._restore_primary_metadata(existing_metadata)
                self._remove_pending_metadata()
            except Exception as rollback_exc:
                raise NativeRollbackError(
                    "native credential metadata failed and the previous state "
                    "could not be restored"
                ) from rollback_exc
            raise CredentialStoreError(
                "native credential metadata could not be persisted"
            ) from exc

        try:
            self._remove_pending_metadata()
        except CredentialStoreError as exc:
            raise NativeRollbackError(
                "native credential was stored, but its transaction marker "
                "could not be cleared"
            ) from exc

    def _upgrade_fallback_to_native(
        self,
        token,
        *,
        required_native_provider=None,
    ):
        existing_metadata = self._read_metadata()
        try:
            backend = self._native_backend_factory()
            if (
                required_native_provider is not None
                and backend.name != required_native_provider
            ):
                return False
            previous_token = backend.get()
        except CredentialStoreError:
            return False
        if previous_token is not None and not isinstance(previous_token, str):
            return False
        try:
            self._store_native_transaction(
                backend,
                token,
                previous_token,
                existing_metadata,
            )
        except NativeRollbackError:
            raise
        except CredentialStoreError as exc:
            # The authenticated fallback remains authoritative until every
            # native write and metadata step succeeds.
            try:
                pending_metadata = self._read_pending_document()
            except CredentialStoreError as pending_exc:
                raise NativeRollbackError(
                    "native credential transaction status is uncertain"
                ) from pending_exc
            if pending_metadata is not None:
                raise NativeRollbackError(
                    "native credential transaction status is uncertain"
                ) from exc
            return False
        self._remove_unused_local_key()
        return True

    def _upgrade_fallback_to_machine_key(self, token, existing_metadata):
        try:
            machine_id = self._validated_machine_id()
        except NativeStoreUnavailable:
            return False
        try:
            native_cleanup_pending = existing_metadata[
                "native_cleanup_pending"
            ]
            metadata = self._fallback_metadata(
                token,
                backend=FALLBACK_BACKEND,
                machine_id=machine_id,
                native_cleanup_pending=native_cleanup_pending,
                native_cleanup_provider=existing_metadata.get(
                    "native_cleanup_provider"
                ),
            )
            self._persist_fallback_metadata(
                token,
                metadata,
                existing_metadata,
            )
        except CredentialStoreError as exc:
            try:
                restored_metadata = self._read_metadata()
                restored_token = self._decrypt_fallback(restored_metadata)
            except Exception:
                raise exc
            if (
                restored_metadata == existing_metadata
                and hmac.compare_digest(restored_token, token)
            ):
                return False
            raise exc
        return True

    def _store_fallback_credential(
        self,
        token,
        existing_metadata,
        *,
        native_cleanup_provider=None,
        force_local=False,
        before_fallback=None,
        before_local_fallback=None,
    ):
        if force_local:
            fallback_backend = LOCAL_KEY_FALLBACK_BACKEND
            machine_id = None
        else:
            fallback_backend, machine_id = self._select_new_fallback_backend()
        if before_fallback is not None:
            before_fallback()
        if (
            fallback_backend == LOCAL_KEY_FALLBACK_BACKEND
            and before_local_fallback is not None
        ):
            before_local_fallback()
        metadata = self._fallback_metadata(
            token,
            backend=fallback_backend,
            machine_id=machine_id,
            native_cleanup_pending=native_cleanup_provider is not None,
            native_cleanup_provider=native_cleanup_provider,
        )
        self._persist_fallback_metadata(
            token,
            metadata,
            existing_metadata,
        )
        return StoreResult(
            backend=fallback_backend,
            degraded=True,
            location=str(self.path),
        )

    def store(
        self,
        token,
        *,
        before_fallback=None,
        before_local_fallback=None,
        allow_recovery=False,
    ):
        if not isinstance(token, str) or not token:
            raise CredentialStoreError("the GitHub credential is empty")
        if len(token.encode("utf-8")) > MAX_TOKEN_SIZE:
            raise CredentialStoreError("the GitHub credential is too large")
        if self._read_pending_metadata() is not None:
            raise NativeRollbackError("native credential cleanup is pending")
        existing_metadata = self._read_metadata()
        required_native_provider = None
        unverified_native_provider = None
        machine_identifier_recovery = False
        if existing_metadata is not None:
            existing_backend = existing_metadata.get("backend")
            if existing_backend in FALLBACK_BACKENDS:
                try:
                    self._decrypt_fallback(existing_metadata)
                except _MachineIdentifierUnavailable:
                    if not allow_recovery:
                        raise
                    if existing_metadata.get("native_cleanup_pending"):
                        raise NativeRollbackError(
                            "native credential cleanup is pending"
                        )
                    machine_identifier_recovery = True
                if existing_metadata["native_cleanup_pending"]:
                    if not allow_recovery:
                        raise NativeRollbackError(
                            "native credential cleanup is pending"
                        )
                    required_native_provider = existing_metadata[
                        "native_cleanup_provider"
                    ]
            elif existing_backend == "native":
                self.read(include_native=False)
                # Native metadata is plaintext. Do not authenticate its
                # provider merely because a verified login supplied a new
                # token; first match it against the backend selected by this
                # installation.
                unverified_native_provider = existing_metadata["provider"]
            else:
                raise CredentialCorrupt("credential backend is unsupported")
        try:
            backend = self._native_backend_factory()
        except NativeStoreUnavailable:
            if unverified_native_provider is not None:
                raise
            if required_native_provider is not None and not allow_recovery:
                raise
            return self._store_fallback_credential(
                token,
                existing_metadata,
                native_cleanup_provider=required_native_provider,
                force_local=machine_identifier_recovery,
                before_fallback=before_fallback,
                before_local_fallback=before_local_fallback,
            )
        if unverified_native_provider is not None:
            if backend.name != unverified_native_provider:
                raise NativeStoreUnavailable(
                    "the configured native credential provider is unavailable"
                )
            required_native_provider = backend.name
        if (
            required_native_provider is not None
            and backend.name != required_native_provider
        ):
            if not allow_recovery:
                raise NativeStoreUnavailable(
                    "the configured native credential provider is unavailable"
                )
            return self._store_fallback_credential(
                token,
                existing_metadata,
                native_cleanup_provider=required_native_provider,
                force_local=machine_identifier_recovery,
                before_fallback=before_fallback,
                before_local_fallback=before_local_fallback,
            )
        try:
            previous_token = backend.get()
        except NativeStoreUnavailable:
            if required_native_provider is not None and not allow_recovery:
                raise
            return self._store_fallback_credential(
                token,
                existing_metadata,
                native_cleanup_provider=required_native_provider,
                force_local=machine_identifier_recovery,
                before_fallback=before_fallback,
                before_local_fallback=before_local_fallback,
            )
        if previous_token is not None and not isinstance(previous_token, str):
            raise NativeStoreError(
                f"{backend.name} returned an invalid GitHub credential"
            )
        self._store_native_transaction(
            backend,
            token,
            previous_token,
            existing_metadata,
        )
        self._remove_unused_local_key()
        return StoreResult(
            backend=backend.name,
            degraded=False,
            location=backend.name,
        )

    def _delete_native_and_verify(self, backend):
        removed = backend.delete()
        if backend.get() is not None:
            raise NativeStoreError(
                f"{backend.name} did not verify GitHub credential deletion"
            )
        return removed

    def _ensure_pending_primary_compatible(
        self,
        pending_metadata,
        *,
        require_provider_binding=False,
    ):
        try:
            primary_metadata = self._read_metadata()
        except CredentialCorrupt as exc:
            raise CredentialCorrupt(
                "primary credential metadata cannot be reconciled with "
                "pending native cleanup"
            ) from exc
        if primary_metadata is None:
            if require_provider_binding:
                raise CredentialCorrupt(
                    "damaged native cleanup metadata has no matching primary"
                )
            return
        primary_backend = primary_metadata.get("backend")
        if primary_backend in FALLBACK_BACKENDS:
            self._decrypt_fallback(primary_metadata)
            if primary_metadata["native_cleanup_pending"]:
                if (
                    primary_metadata["native_cleanup_provider"]
                    != pending_metadata["provider"]
                ):
                    raise NativeStoreUnavailable(
                        "pending native cleanup conflicts with the deferred provider"
                    )
                return
            if require_provider_binding:
                raise CredentialCorrupt(
                    "primary credential metadata does not bind a native provider"
                )
            return
        expected = {"version", "backend", "provider", "service", "account"}
        if (
            primary_backend != "native"
            or set(primary_metadata) != expected
            or not isinstance(primary_metadata.get("provider"), str)
            or not primary_metadata["provider"]
            or primary_metadata.get("service") != CREDENTIAL_SERVICE
            or primary_metadata.get("account") != CREDENTIAL_ACCOUNT
        ):
            raise CredentialCorrupt(
                "primary credential metadata cannot identify its native "
                "provider safely"
            )
        if primary_metadata["provider"] != pending_metadata["provider"]:
            raise NativeStoreUnavailable(
                "pending native cleanup conflicts with the configured provider"
            )

    def delete(self):
        try:
            pending_metadata = self._read_pending_document()
        except CredentialCorrupt as exc:
            raise CredentialCorrupt(
                "native credential transaction metadata cannot be safely reset"
            ) from exc
        if pending_metadata is not None:
            if self._is_cleanup_blocked(pending_metadata):
                return self._complete_cleanup_blocked_reset()
            try:
                self._validate_native_transaction(pending_metadata)
            except CredentialCorrupt as exc:
                return self._delete_corrupt_pending(pending_metadata, exc)
            self._ensure_pending_primary_compatible(pending_metadata)
            try:
                backend = self._native_backend_factory()
            except NativeStoreUnavailable:
                # Keep the marker until the recorded provider is available.
                raise
            if pending_metadata.get("provider") != backend.name:
                raise NativeStoreUnavailable(
                    "the pending native credential provider is unavailable"
                )
            native_removed = self._delete_native_and_verify(backend)
            marker_removed = self._remove_metadata()
            key_removed = self._remove_local_key()
            pending_removed = self._remove_pending_metadata()
            return (
                native_removed
                or marker_removed
                or key_removed
                or pending_removed
            )
        try:
            metadata = self._read_metadata()
        except CredentialCorrupt as exc:
            return self._delete_unusable_metadata(exc)
        if metadata is None:
            # No provider-bound local state exists, so never guess which
            # native backend may own an otherwise orphaned credential.
            key_removed = self._remove_local_key()
            return key_removed
        if metadata.get("backend") in FALLBACK_BACKENDS:
            try:
                self._decrypt_fallback(metadata)
            except CredentialCorrupt as exc:
                return self._delete_unusable_metadata(exc)
            cleanup_provider = None
            if metadata["native_cleanup_pending"]:
                cleanup_provider = metadata["native_cleanup_provider"]
            native_removed = False
            if cleanup_provider is not None:
                try:
                    backend = self._native_backend_factory()
                except NativeStoreUnavailable:
                    raise NativeStoreUnavailable(
                        "native credential cleanup is still pending"
                    )
                if backend.name != cleanup_provider:
                    raise NativeStoreUnavailable(
                        "the deferred native credential provider is unavailable"
                    )
                native_removed = self._delete_native_and_verify(backend)
            marker_removed = self._remove_metadata()
            key_removed = self._remove_local_key()
            return native_removed or marker_removed or key_removed
        expected = {"version", "backend", "provider", "service", "account"}
        if (
            metadata.get("backend") != "native"
            or set(metadata) != expected
            or not isinstance(metadata.get("provider"), str)
            or not metadata["provider"]
            or metadata.get("service") != CREDENTIAL_SERVICE
            or metadata.get("account") != CREDENTIAL_ACCOUNT
        ):
            return self._delete_unusable_metadata()
        try:
            backend = self._native_backend_factory()
        except NativeStoreUnavailable:
            # A valid native marker identifies a credential that still needs
            # cleanup. Keep it so a later logout can retry the fixed account.
            raise
        if metadata.get("provider") != backend.name:
            raise NativeStoreUnavailable(
                "the configured native credential provider is unavailable"
            )
        removed = self._delete_native_and_verify(backend)
        marker_removed = self._remove_metadata()
        key_removed = self._remove_local_key()
        return removed or marker_removed or key_removed

    def _delete_corrupt_pending(self, metadata, marker_error):
        """Reset a damaged transaction marker only after verified cleanup."""
        if (
            not isinstance(metadata, dict)
            or not isinstance(metadata.get("provider"), str)
            or not metadata["provider"]
            or metadata.get("service") != CREDENTIAL_SERVICE
            or metadata.get("account") != CREDENTIAL_ACCOUNT
        ):
            raise CredentialCorrupt(
                "native credential transaction metadata cannot identify its "
                "provider safely"
            ) from marker_error
        try:
            self._ensure_pending_primary_compatible(
                metadata,
                require_provider_binding=True,
            )
            backend = self._native_backend_factory()
            if metadata["provider"] != backend.name:
                raise NativeStoreUnavailable(
                    "the damaged native credential provider is unavailable"
                )
            native_removed = self._delete_native_and_verify(backend)
        except CredentialStoreError as exc:
            raise CredentialCorrupt(
                "native credential transaction metadata is damaged and cleanup "
                "could not be verified; manual native cleanup is required"
            ) from exc
        try:
            # Discard the damaged WAL while the provider-bound primary still
            # exists. If a later local cleanup step fails, logout can safely
            # retry from that primary instead of losing its provider proof.
            pending_removed = self._remove_pending_metadata()
            marker_removed = self._remove_metadata()
            key_removed = self._remove_local_key()
        except CredentialStoreError as exc:
            raise CredentialCorrupt(
                "native credential cleanup succeeded, but damaged transaction "
                "metadata could not be reset"
            ) from exc
        return (
            native_removed
            or marker_removed
            or key_removed
            or pending_removed
        )

    def _delete_unusable_metadata(self, metadata_error=None):
        """Reset untrusted local state without guessing its native provider."""
        cleanup_blocked = self._cleanup_blocked_metadata()
        try:
            self._write_pending_metadata(cleanup_blocked)
            if self._read_pending_document() != cleanup_blocked:
                raise CredentialStoreError(
                    "native cleanup safety marker failed verification"
                )
        except (CredentialStoreError, OSError) as exc:
            raise CredentialCorrupt(
                "untrusted credential metadata could not be safely reset"
            ) from exc
        return self._complete_cleanup_blocked_reset(metadata_error)

    def _complete_cleanup_blocked_reset(self, metadata_error=None):
        """Finish local cleanup while retaining the manual-cleanup marker."""
        try:
            self._remove_metadata()
            self._remove_local_key()
        except (CredentialStoreError, OSError) as exc:
            raise CredentialCorrupt(
                "untrusted credential metadata could not be safely reset"
            ) from exc
        raise CredentialCorrupt(
            "credential metadata was reset without modifying native credential "
            "storage; manual native cleanup is required"
        ) from metadata_error
