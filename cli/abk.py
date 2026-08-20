#!/usr/bin/env python3

import argparse
import contextlib
import errno
import io
import json
import os
import re
import shutil
import ssl
import sys
import tempfile
import threading
import time
import webbrowser
from pathlib import Path, PurePosixPath
from urllib.request import HTTPRedirectHandler, HTTPSHandler, Request, build_opener
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, urljoin, urlparse
import zipfile
import hashlib
import hmac
import base64
import binascii

# Crypto backend: prefer cryptography, then accept either PyCryptodome namespace.
try:
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import padding
    _CRYPTO_BACKEND = 'cryptography'
except ImportError:
    try:
        from Cryptodome.PublicKey import RSA
        from Cryptodome.Signature import pkcs1_15
        from Cryptodome.Hash import SHA256
        _CRYPTO_BACKEND = 'pycryptodome'
    except ImportError:
        try:
            from Crypto.PublicKey import RSA
            from Crypto.Signature import pkcs1_15
            from Crypto.Hash import SHA256
            _CRYPTO_BACKEND = 'pycryptodome'
        except ImportError:
            _CRYPTO_BACKEND = None

# PyInstaller bundle: point SSL certs to certifi if available
try:
    import certifi
    _CERTIFI_CA_BUNDLE = certifi.where()
    os.environ.setdefault('SSL_CERT_FILE', _CERTIFI_CA_BUNDLE)
except ImportError:
    _CERTIFI_CA_BUNDLE = None

sys.path.insert(0, str(Path(__file__).parent))
from i18n import (
    SUPPORTED_LANGUAGES,
    language_storage_id,
    load_translations,
    normalize_language_tag,
    t,
)


def configure_stdio():
    json_mode = "--json" in sys.argv[1:]
    for stream_name in ("stdout", "stderr"):
        stream = getattr(sys, stream_name, None)
        if stream is None or not hasattr(stream, "reconfigure"):
            continue
        try:
            # Avoid crashing on terminals or pipelines whose locale encoding
            # cannot represent translated help or status output.
            options = {"errors": "replace"}
            if json_mode:
                options.update({"encoding": "utf-8", "newline": "\n"})
            stream.reconfigure(**options)
        except Exception:
            pass


GITHUB_API = "https://api.github.com"
GITHUB_OAUTH_DEVICE_URL = "https://github.com/login/device/code"
GITHUB_OAUTH_TOKEN_URL = "https://github.com/login/oauth/access_token"
SOURCE_REPO_OWNER = "xingguangcuican6666"
SOURCE_REPO_NAME = "ABK"
DEFAULT_REPO = f"{SOURCE_REPO_OWNER}/{SOURCE_REPO_NAME}"
if os.name == "nt" and os.environ.get("APPDATA"):
    CONFIG_DIR = Path(os.environ["APPDATA"]) / "abk"
else:
    CONFIG_DIR = Path(os.environ.get("XDG_CONFIG_HOME", Path.home() / ".config")) / "abk"
CONFIG_FILE = CONFIG_DIR / "config.json"
CLIENT_ID_FALLBACK = "Ov23li8skGo6AFPBeSTh"
SIGNING_SECRET_NAME = "ABK_ARTIFACT_SIGNING_KEY_BASE64"
SIGNING_RELEASE_TAG = "abk-artifact-key"
SIGNING_PUBLIC_KEY_ASSET = "abk-artifact-signing-public.pem"
SIGNING_KEY_VERSION = 1
SIGNING_STATE_CONFIG_KEY = "signing_keys"
MAX_SIGNING_KEY_FILE_SIZE = 64 * 1024
CONFIG_LOCK_FILE = ".config.lock"
CLI_VERSION = "0.1.0"
JSON_SCHEMA_VERSION = 1
MAX_MANIFEST_SIZE = 1024 * 1024
MAX_SIGNATURE_SIZE = 64 * 1024
MAX_PAYLOAD_SIZE = 8 * 1024 * 1024 * 1024
MAX_ARTIFACT_DOWNLOAD_SIZE = MAX_PAYLOAD_SIZE + 64 * 1024 * 1024

_CONFIG_THREAD_LOCK = threading.RLock()
_CONFIG_LOCK_STATE = threading.local()

WORKFLOWS = {
    "a12": {"file": "kernel-a12-5-10.yml", "name": t("build_target_a12"), "android": "android12", "kernel": "5.10"},
    "a13": {"file": "kernel-a13-5-15.yml", "name": t("build_target_a13"), "android": "android13", "kernel": "5.15"},
    "a14": {"file": "kernel-a14-6-1.yml", "name": t("build_target_a14"), "android": "android14", "kernel": "6.1"},
    "a15": {"file": "kernel-a15-6-6.yml", "name": t("build_target_a15"), "android": "android15", "kernel": "6.6"},
    "a16": {"file": "kernel-a16-6-12.yml", "name": t("build_target_a16"), "android": "android16", "kernel": "6.12"},
    "custom": {"file": "kernel-custom.yml", "name": t("build_target_custom")},
    "oneplus": {"file": "oneplus-custom.yml", "name": t("build_target_oneplus")},
}

ANDROID_VERSIONS = ["android12", "android13", "android14", "android15", "android16"]
KERNEL_VERSIONS = ["5.10", "5.15", "6.1", "6.6", "6.12"]

MATRIX_TARGETS = ["a12", "a13", "a14", "a15", "a16"]
MATRIX_TARGETS_ALL = MATRIX_TARGETS + ["both", "full", "all-managers"]
KSU_ALL_VARIANTS = ["Official", "SukiSU", "ReSukiSU"]
MANAGER_VARIANT_ALIASES = {
    "official": "Official",
    "sukisu": "SukiSU",
    "resukisu": "ReSukiSU",
    "none": "None",
}

FULL_MATRIX_WORKFLOWS = {
    "full": "kernel-full-feature-matrix.yml",
    "all-managers": "all-managers-full-feature-matrix.yml",
}

# These names are part of the machine-readable CLI contract. GitHub reports
# workflow runs by the top-level `name:` in each workflow, not by the
# translated label shown by the interactive CLI.
WORKFLOW_RUNTIME_NAMES = {
    "kernel-a12-5-10.yml": "内核构建 - Android 12 (5.10)",
    "kernel-a13-5-15.yml": "内核构建 - Android 13 (5.15)",
    "kernel-a14-6-1.yml": "内核构建 - Android 14 (6.1)",
    "kernel-a15-6-6.yml": "内核构建 - Android 15 (6.6)",
    "kernel-a16-6-12.yml": "内核构建 - Android 16 (6.12)",
    "kernel-custom.yml": "Android 内核构建-自定义",
    "oneplus-custom.yml": "OnePlus 内核构建-自定义",
    "kernel-full-feature-matrix.yml": "全属性内核构建矩阵",
    "all-managers-full-feature-matrix.yml": "全管理器全矩阵编译",
}

KSU_VARIANTS = ["None", "Official", "SukiSU", "ReSukiSU"]
KSU_BRANCH_MAP = {
    "stable": "Stable(标准)", "Stable": "Stable(标准)",
    "latest": "Latest(最新)", "Latest": "Latest(最新)",
    "dev": "Dev(开发)", "Dev": "Dev(开发)",
    "custom": "Custom(自定义)", "Custom": "Custom(自定义)",
}
KSU_BRANCH_VALUES = [
    "Stable(标准)",
    "Latest(最新)",
    "Dev(开发)",
    "Custom(自定义)",
]

def resolve_ksu_branch(b):
    return KSU_BRANCH_MAP.get(b, b) if b else "Stable(标准)"


def resolve_plan_ksu_branch(variant, branch):
    """Apply Android's per-variant branch normalization to one build plan."""
    if variant == "None":
        return "Stable(标准)"
    return resolve_ksu_branch(branch)


def supports_kpm(variant, ksu_branch=None, *, oneplus=False):
    """Return whether the selected KernelSU source exposes KPM.

    Keep this aligned with Android's KernelSupport.isKpmSupported contract.
    """
    if oneplus:
        return variant in {"SukiSU", "ReSukiSU"}
    if variant == "SukiSU":
        return True
    if variant != "ReSukiSU":
        return False
    return resolve_ksu_branch(ksu_branch) in {
        "Stable(标准)",
        "Custom(自定义)",
    }


def default_download_dir():
    """Return the platform-native default artifact download directory."""
    return Path.home() / "Downloads"


def selected_manager_variants(value):
    """Return the manager variants selected by an all-managers build."""
    raw = (value or "all").strip()
    if raw.lower() in {"all", "*"}:
        return set(MANAGER_VARIANT_ALIASES.values())
    tokens = {
        item.strip().lower().replace("-", "").replace("_", "")
        for item in raw.split(",")
    }
    return {
        MANAGER_VARIANT_ALIASES[token]
        for token in tokens
        if token in MANAGER_VARIANT_ALIASES
    }


VIRT_OPTIONS = ["off", "on", "678", "123", "345"]

ONEPLUS_DEVICES = {
    "oneplus_15": {"name": "OnePlus 15", "cpu": "sm8850", "android": "android16", "kernel": "6.12"},
    "oneplus_15t": {"name": "OnePlus 15T", "cpu": "sm8850", "android": "android16", "kernel": "6.12"},
    "oneplus_13_b": {"name": "OnePlus 13", "cpu": "sm8750", "android": "android15", "kernel": "6.6"},
    "oneplus_13s_b": {"name": "OnePlus 13s", "cpu": "sm8750", "android": "android15", "kernel": "6.6"},
    "oneplus_13t_b": {"name": "OnePlus 13T", "cpu": "sm8750", "android": "android15", "kernel": "6.6"},
    "oneplus_ace5_pro_b": {"name": "OnePlus Ace5 Pro", "cpu": "sm8750", "android": "android15", "kernel": "6.6"},
    "oneplus_ace_6": {"name": "OnePlus Ace 6", "cpu": "sm8750", "android": "android15", "kernel": "6.6"},
    "oneplus_pad_2_pro_b": {"name": "OnePlus Pad 2 Pro", "cpu": "sm8750", "android": "android15", "kernel": "6.6"},
    "oneplus_pad_3_b": {"name": "OnePlus Pad 3", "cpu": "sm8750", "android": "android15", "kernel": "6.6"},
    "oneplus_ace5_ultra_b": {"name": "OnePlus Ace5 Ultra", "cpu": "mt6991", "android": "android15", "kernel": "6.6"},
    "oneplus_turbo_6": {"name": "OnePlus Turbo 6", "cpu": "sm8735", "android": "android15", "kernel": "6.6"},
    "oneplus_12_b": {"name": "OnePlus 12", "cpu": "sm8650", "android": "android14", "kernel": "6.1"},
    "oneplus_ace3_pro_b": {"name": "OnePlus Ace3 Pro", "cpu": "sm8650", "android": "android14", "kernel": "6.1"},
    "oneplus_ace5_b": {"name": "OnePlus Ace5", "cpu": "sm8650", "android": "android14", "kernel": "6.1"},
    "oneplus_13r_b": {"name": "OnePlus 13R", "cpu": "sm8650", "android": "android14", "kernel": "6.1"},
    "oneplus_pad2_b": {"name": "OnePlus Pad 2", "cpu": "sm8650", "android": "android14", "kernel": "6.1"},
    "oneplus_pad_pro_b": {"name": "OnePlus Pad Pro", "cpu": "sm8650", "android": "android14", "kernel": "6.1"},
    "oneplus_ace5_race_b": {"name": "OnePlus Ace5 Race", "cpu": "mt6989", "android": "android14", "kernel": "6.1"},
    "oneplus_nord_5_b": {"name": "OnePlus Nord 5", "cpu": "sm8635", "android": "android14", "kernel": "6.1"},
    "oneplus_11_b": {"name": "OnePlus 11", "cpu": "sm8550", "android": "android13", "kernel": "5.15"},
    "oneplus_12r_b": {"name": "OnePlus 12R", "cpu": "sm8550", "android": "android13", "kernel": "5.15"},
    "oneplus_ace2_pro_b": {"name": "OnePlus Ace2 Pro", "cpu": "sm8550", "android": "android13", "kernel": "5.15"},
    "oneplus_ace3_b": {"name": "OnePlus Ace3", "cpu": "sm8550", "android": "android13", "kernel": "5.15"},
    "oneplus_open_b": {"name": "OnePlus Open", "cpu": "sm8550", "android": "android13", "kernel": "5.15"},
    "oneplus_10t_v": {"name": "OnePlus 10T", "cpu": "sm8475", "android": "android12", "kernel": "5.10"},
    "oneplus_11r_b": {"name": "OnePlus 11R", "cpu": "sm8475", "android": "android12", "kernel": "5.10"},
    "oneplus_ace2_b": {"name": "OnePlus Ace2", "cpu": "sm8475", "android": "android12", "kernel": "5.10"},
    "oneplus_ace_pro_v": {"name": "OnePlus Ace Pro", "cpu": "sm8475", "android": "android12", "kernel": "5.10"},
    "oneplus_10_pro_b": {"name": "OnePlus 10 Pro", "cpu": "sm8450", "android": "android12", "kernel": "5.10"},
    "oneplus_ace_3v_b": {"name": "OnePlus Ace 3V", "cpu": "sm7675", "android": "android14", "kernel": "6.1"},
    "oneplus_turbo_6v": {"name": "OnePlus Turbo 6V", "cpu": "sm7635", "android": "android14", "kernel": "6.1"},
    "oneplus_nord_4_b": {"name": "OnePlus Nord 4", "cpu": "sm7675", "android": "android14", "kernel": "6.1"},
    "oneplus_nord_ce4_lite_5g": {"name": "OnePlus Nord CE4 Lite 5G", "cpu": "sm6375", "android": "android14", "kernel": "6.1"},
    "oneplus_nord_ce4_b": {"name": "OnePlus Nord CE4", "cpu": "sm7550", "android": "android13", "kernel": "5.15"},
}

ONEPLUS_SUSFS_SUPPORTED = {
    ("android14", "6.1"),
    ("android15", "6.6"),
    ("android16", "6.12"),
}

UNSAFE_WORKFLOW_TEXT_CHARS = frozenset(('"', "'", "`", "$", "\\"))


def _has_unsafe_workflow_text(value, max_length):
    if not isinstance(value, str) or len(value) > max_length:
        return True
    return any(
        ord(char) < 32 or ord(char) == 127 or char in UNSAFE_WORKFLOW_TEXT_CHARS
        for char in value
    )


def _valid_git_ref(value, allow_history_suffix=False):
    if not isinstance(value, str) or not value or len(value) > 255:
        return False
    ref = value
    if allow_history_suffix and ":" in value:
        match = re.fullmatch(r"([A-Za-z0-9._/-]+):([0-9]+)", value)
        if not match or not 1 <= int(match.group(2)) <= 100:
            return False
        ref = match.group(1)
    if (
        not ref
        or ref == "@"
        or ref.startswith(("/", "-"))
        or ref.endswith(("/", "."))
        or ".." in ref
        or "@{" in ref
        or "//" in ref
        or any(ord(char) <= 32 or ord(char) == 127 for char in ref)
        or any(char in ref for char in "~^:?*[\\")
    ):
        return False
    return all(
        part and not part.startswith(".") and not part.lower().endswith(".lock")
        for part in ref.split("/")
    )


def invalid_build_argument(args):
    """Return the first unsafe CLI flag before it reaches an existing workflow."""
    ref = getattr(args, "ref", None)
    if ref and not _valid_git_ref(ref):
        return "--ref"

    if not getattr(args, "oneplus", False):
        custom_ref = getattr(args, "custom_ref", None)
        if custom_ref and not _valid_git_ref(custom_ref, allow_history_suffix=True):
            return "--custom-ref"

    version = getattr(args, "version", None)
    if version and re.fullmatch(r"[A-Za-z0-9._+-]{1,96}", version) is None:
        return "--version"

    revision = getattr(args, "revision", None)
    if revision and re.fullmatch(r"r[1-9][0-9]{0,30}", revision) is None:
        return "--revision"

    build_time = getattr(args, "build_time", None)
    if build_time and (
        _has_unsafe_workflow_text(build_time, 128)
        or re.fullmatch(r"[A-Za-z0-9:+,./ _-]+", build_time) is None
    ):
        return "--build-time"

    sub_level = getattr(args, "sub_level", None)
    if sub_level and sub_level != "X":
        if (
            re.fullmatch(r"[0-9]{1,4}", sub_level) is None
            or int(sub_level) > 9999
        ):
            return "--sub-level"

    patch_level = getattr(args, "os_patch_level", None)
    if (
        patch_level
        and patch_level != "lts"
        and re.fullmatch(r"20[0-9]{2}-(0[1-9]|1[0-2])", patch_level) is None
    ):
        return "--os-patch-level"

    extra_algos = getattr(args, "zram_extra_algos", None)
    if extra_algos:
        algos = [item.strip() for item in extra_algos.split(",")]
        if (
            len(algos) > 16
            or any(
                not item or re.fullmatch(r"[A-Za-z0-9_+-]{1,32}", item) is None
                for item in algos
            )
        ):
            return "--zram-extra-algos"

    custom_modules = getattr(args, "custom_modules", None)
    if custom_modules and _has_unsafe_workflow_text(custom_modules, 4096):
        return "--custom-modules"

    manager_variants = getattr(args, "manager_variants", None)
    if manager_variants:
        raw = manager_variants.strip()
        if raw.lower() not in {"all", "*"}:
            allowed = {"official", "sukisu", "resukisu", "none"}
            tokens = [
                token.strip().lower().replace("-", "").replace("_", "")
                for token in raw.split(",")
            ]
            if not tokens or any(token not in allowed for token in tokens):
                return "--manager-variants"

    kpm_password = getattr(args, "kpm_password", None)
    if kpm_password and (
        len(kpm_password) > 256
        or any(ord(char) < 32 or ord(char) == 127 for char in kpm_password)
    ):
        return "--kpm-password"
    return None


def validate_oneplus_build(args, device_info=None):
    errors = []
    warnings = []
    
    if args.zram:
        args.zram = False
        warnings.append(t("op_no_zram"))
    if args.ddk:
        args.ddk = False
        warnings.append(t("op_no_ddk"))
    if args.ntsync:
        args.ntsync = False
        warnings.append(t("op_no_ntsync"))
    if args.networking:
        args.networking = False
        warnings.append(t("op_no_networking"))
    if args.rekernel:
        args.rekernel = False
        warnings.append(t("op_no_rekernel"))
    if args.virt and args.virt != "off":
        args.virt = "off"
        warnings.append(t("op_no_virt"))
    if args.custom_ref:
        args.custom_ref = ""
        warnings.append(t("op_no_custom_ref"))
    if args.zram_full_algo:
        args.zram_full_algo = False
        warnings.append(t("op_no_zram_algo"))
    if args.zram_extra_algos:
        args.zram_extra_algos = ""
        warnings.append(t("op_no_zram_extra"))
    if args.custom_modules:
        args.custom_modules = ""
        warnings.append(t("op_no_custom_modules"))
    if args.kpm_password:
        args.kpm_password = ""
        warnings.append(t("op_no_kpm_password"))
    
    if device_info:
        cpu = device_info.get("cpu", "")
        android = device_info.get("android", "")
        kernel = device_info.get("kernel", "")
        
        if cpu.startswith("mt") and args.proxy_optimization:
            args.proxy_optimization = False
            warnings.append(t("op_mtk_no_proxy", cpu=cpu))
        
        if (android, kernel) not in ONEPLUS_SUSFS_SUPPORTED:
            if args.susfs:
                args.susfs = False
                warnings.append(t("op_no_susfs", android=android, kernel=kernel))
        
    return errors, warnings


def load_config():
    if CONFIG_FILE.exists():
        try:
            if os.name != "nt":
                CONFIG_DIR.chmod(0o700)
                CONFIG_FILE.chmod(0o600)
            data = json.loads(CONFIG_FILE.read_text(encoding="utf-8"))
            return data if isinstance(data, dict) else {}
        except (OSError, UnicodeError, json.JSONDecodeError):
            pass
    return {}


def save_config(config):
    with _config_process_lock():
        CONFIG_DIR.mkdir(parents=True, exist_ok=True, mode=0o700)
        if os.name != "nt":
            CONFIG_DIR.chmod(0o700)

        payload = json.dumps(config, indent=2, ensure_ascii=False) + "\n"
        fd, temp_name = tempfile.mkstemp(
            prefix=".config-", suffix=".tmp", dir=CONFIG_DIR
        )
        try:
            if os.name != "nt":
                os.fchmod(fd, 0o600)
            with os.fdopen(fd, "w", encoding="utf-8") as stream:
                fd = None
                stream.write(payload)
                stream.flush()
                os.fsync(stream.fileno())
            os.replace(temp_name, CONFIG_FILE)
            if os.name != "nt":
                CONFIG_FILE.chmod(0o600)
        finally:
            if fd is not None:
                os.close(fd)
            try:
                Path(temp_name).unlink()
            except FileNotFoundError:
                pass


def get_token(args):
    config = load_config()
    return (
        getattr(args, "token", None)
        or os.environ.get("GITHUB_TOKEN")
        or os.environ.get("GH_TOKEN")
        or config.get("token")
    )


def get_client_id():
    config = load_config()
    return config.get("client_id") or os.environ.get("ABK_CLIENT_ID") or CLIENT_ID_FALLBACK


def request_device_code():
    client_id = get_client_id()
    data = urlencode({
        "client_id": client_id,
        # `repo` is sufficient for classic OAuth tokens to dispatch workflows.
        # The broader `workflow` scope would also allow modifying workflow files.
        "scope": "repo"
    }).encode()
    
    req = Request(
        GITHUB_OAUTH_DEVICE_URL,
        data=data,
        headers={
            "Accept": "application/json",
            "User-Agent": "ABK-CLI"
        }
    )
    
    try:
        with _open_without_redirect(req, timeout=30) as resp:
            result = json.loads(resp.read())
            return result if isinstance(result, dict) else None
    except Exception as exc:
        print(t("err_req_failed_with_error", error=exc), file=sys.stderr)
        return None


def poll_device_token_once(device_code):
    client_id = get_client_id()
    
    data = urlencode({
        "client_id": client_id,
        "device_code": device_code,
        "grant_type": "urn:ietf:params:oauth:grant-type:device_code"
    }).encode()
    
    req = Request(
        GITHUB_OAUTH_TOKEN_URL,
        data=data,
        headers={
            "Accept": "application/json",
            "User-Agent": "ABK-CLI"
        }
    )
    
    try:
        with _open_without_redirect(req, timeout=30) as resp:
            result = json.loads(resp.read())
    except HTTPError as exc:
        return {"success": False, "error": f"http_{exc.code}"}
    except Exception as exc:
        return {"success": False, "error": str(exc)}

    if not isinstance(result, dict):
        return {"success": False, "error": "invalid_response"}
    
    if "access_token" in result:
        return {"success": True, "token": result["access_token"]}
    
    error = result.get("error")
    if error == "authorization_pending":
        return {"success": False, "error": "pending"}
    elif error == "slow_down":
        return {"success": False, "error": "slow_down"}
    elif error in ["expired_token", "access_denied"]:
        return {"success": False, "error": error}
    
    return {"success": False, "error": "unknown"}


def device_flow_login():
    print(t("login_requesting"))
    result = request_device_code()
    
    required = ("device_code", "user_code", "verification_uri")
    if not isinstance(result, dict) or any(not result.get(key) for key in required):
        detail = result.get("error_description") or result.get("error") if isinstance(result, dict) else None
        print(t("err_req_failed_with_error", error=detail or "invalid response"), file=sys.stderr)
        return None
    
    device_code = result["device_code"]
    user_code = result["user_code"]
    verification_uri = result["verification_uri"]
    try:
        interval = max(1, int(result.get("interval", 5)))
        expires_in = max(1, int(result.get("expires_in", 900)))
    except (TypeError, ValueError):
        print(t("err_req_failed"), file=sys.stderr)
        return None
    
    print()
    print("=" * 50)
    print(f"  {t('login_title')}")
    print("=" * 50)
    print()
    print(f"  {t('login_step1')}: {verification_uri}")
    print(f"  {t('login_step2')}: {user_code}")
    print()
    print("=" * 50)
    print()
    
    try:
        if webbrowser.open(verification_uri):
            print(t("login_browser_open"))
    except Exception:
        pass
    
    print(t("login_waiting"))
    print(t("press_ctrl_c"))
    print()
    
    start_time = time.time()
    current_interval = interval
    
    try:
        while time.time() - start_time < expires_in:
            time.sleep(current_interval)
            
            poll_result = poll_device_token_once(device_code)
            
            if poll_result.get("success"):
                print(f"\n{t('login_success')}")
                return poll_result["token"]
            
            error = poll_result.get("error")
            if error == "pending":
                continue
            elif error == "slow_down":
                current_interval += 5
                continue
            elif error == "expired_token":
                print(f"\n{t('err_auth_expired')}", file=sys.stderr)
                return None
            elif error == "access_denied":
                print(f"\n{t('err_auth_denied')}", file=sys.stderr)
                return None
            elif error:
                print(f"\n{t('err_auth_failed', error=error)}", file=sys.stderr)
                return None
    except KeyboardInterrupt:
        print(f"\n{t('login_cancelled')}")
        return None
    
    print(f"\n{t('err_auth_timeout')}", file=sys.stderr)
    return None


class _NoRedirectHandler(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


_DOWNLOAD_REDIRECT_CODES = (301, 302, 303, 307, 308)


def _create_tls_context():
    """Create the exact TLS context used by every CLI HTTPS request."""
    ca_bundle = os.environ.get("SSL_CERT_FILE")
    if ca_bundle:
        if not Path(ca_bundle).is_file():
            raise RuntimeError("configured CA bundle is missing")
    # The no-argument form uses OpenSSL's complete default path set.  It
    # honors SSL_CERT_FILE and SSL_CERT_DIR together, preserving enterprise
    # CA directories while our import-time default supplies certifi in frozen
    # bundles that otherwise lack a usable cafile.
    return ssl.create_default_context()


def _build_network_opener(*handlers):
    return build_opener(HTTPSHandler(context=_create_tls_context()), *handlers)


def _validated_https_url(url, unsafe_label):
    parsed = urlparse(url or "")
    if (
        parsed.scheme.lower() != "https"
        or not parsed.hostname
        or parsed.username is not None
        or parsed.password is not None
    ):
        raise RuntimeError(f"{unsafe_label} returned an unsafe URL")
    return parsed


def _url_origin(parsed):
    return (
        parsed.scheme.casefold(),
        parsed.hostname.casefold(),
        parsed.port or 443,
    )


def _open_without_redirect(request, timeout):
    return _build_network_opener(_NoRedirectHandler()).open(
        request,
        timeout=timeout,
    )


def _open_same_origin_redirect(
    request,
    timeout,
    unsafe_label,
    max_redirects=5,
):
    """Follow bounded HTTPS redirects without sending credentials elsewhere."""
    opener = _build_network_opener(_NoRedirectHandler())
    initial = _validated_https_url(request.full_url, unsafe_label)
    allowed_origin = _url_origin(initial)
    current = request
    for redirect_count in range(max_redirects + 1):
        current_url = _validated_https_url(current.full_url, unsafe_label)
        if _url_origin(current_url) != allowed_origin:
            raise RuntimeError(f"{unsafe_label} returned an unsafe redirect")
        try:
            return opener.open(current, timeout=timeout)
        except HTTPError as exc:
            if exc.code not in _DOWNLOAD_REDIRECT_CODES:
                raise
            location = exc.headers.get("Location")
            if redirect_count >= max_redirects or not location:
                exc.close()
                raise RuntimeError(f"{unsafe_label} returned too many redirects")
            next_url = urljoin(current.full_url, location)
            exc.close()
            parsed = _validated_https_url(next_url, unsafe_label)
            if _url_origin(parsed) != allowed_origin:
                raise RuntimeError(f"{unsafe_label} returned an unsafe redirect")
            current = Request(
                next_url,
                data=current.data,
                headers=dict(current.header_items()),
                method=current.get_method(),
            )
    raise RuntimeError(f"{unsafe_label} returned too many redirects")


def _open_https_download(request, timeout, unsafe_label, max_redirects=5):
    """Follow HTTPS downloads, retaining credentials only on the same origin."""
    opener = _build_network_opener(_NoRedirectHandler())
    current = request
    for redirect_count in range(max_redirects + 1):
        _validated_https_url(current.full_url, unsafe_label)
        try:
            return opener.open(current, timeout=timeout)
        except HTTPError as exc:
            if exc.code not in _DOWNLOAD_REDIRECT_CODES:
                raise
            location = exc.headers.get("Location")
            if redirect_count >= max_redirects or not location:
                exc.close()
                raise RuntimeError(f"{unsafe_label} returned too many redirects")
            next_url = urljoin(current.full_url, location)
            exc.close()
            current_origin = _url_origin(
                _validated_https_url(current.full_url, unsafe_label)
            )
            next_origin = _url_origin(
                _validated_https_url(next_url, unsafe_label)
            )
            if next_origin == current_origin:
                headers = dict(current.header_items())
            else:
                headers = {
                    "Accept": "application/octet-stream",
                    "User-Agent": "ABK-CLI",
                }
            current = Request(
                next_url,
                headers=headers,
            )
    raise RuntimeError(f"{unsafe_label} returned too many redirects")


class GitHubAPIError(RuntimeError):
    def __init__(self, status_code, message):
        super().__init__(message)
        self.status_code = status_code


class GitHubClient:
    def __init__(self, token=None, repo=None, verbose=False):
        config = load_config()
        self.token = (
            token 
            or os.environ.get("GITHUB_TOKEN") 
            or os.environ.get("GH_TOKEN")
            or config.get("token")
        )
        self.repo = repo or os.environ.get("ABK_REPO")
        self.repo_explicit = bool(self.repo)
        self.verbose = verbose
        self.username = None
        self.fork_repo = None
        self.authentication_error = None
        self.fork_detection_error = None
        
        if self.token and not self.repo_explicit:
            self._detect_user(detect_fork=True)
        
        if not self.repo:
            if self.fork_repo:
                self.repo = self.fork_repo.get("full_name")
            else:
                self.repo = DEFAULT_REPO

    def _detect_user(self, detect_fork=True):
        try:
            user = self.get("/user")
            self.username = user.get("login")
        except Exception as exc:
            self.authentication_error = exc
            return
        if detect_fork:
            try:
                fork = self.get_fork()
                if fork:
                    self.fork_repo = fork
            except Exception as exc:
                # Fork discovery is repository setup, not token validation.
                # Commands that need a fork will retry and report it in their
                # own operation-specific error shape.
                self.fork_detection_error = exc

    def get_default_branch(self):
        repo_info = self.get(f"/repos/{self.repo}")
        return repo_info.get("default_branch") or "dev"

    def _request(self, method, path, data=None):
        url = f"{GITHUB_API}{path}" if not path.startswith("http") else path
        headers = {
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "ABK-CLI",
        }
        parsed = urlparse(url)
        if self.token and parsed.scheme == "https" and parsed.hostname == "api.github.com":
            headers["Authorization"] = f"Bearer {self.token}"
        if data:
            headers["Content-Type"] = "application/json"
            data = json.dumps(data).encode()

        if self.verbose:
            # Request URLs can contain secret identifiers or signed query
            # parameters.  Keep verbose output useful without ever treating
            # the full URL as log-safe data.
            print(f"> {method} GitHub API request", file=sys.stderr)
        req = Request(url, data=data, headers=headers, method=method)
        try:
            with _open_same_origin_redirect(
                req,
                timeout=30,
                unsafe_label="GitHub API",
            ) as resp:
                body = resp.read()
                if not body:
                    return {}
                return json.loads(body)
        except HTTPError as e:
            body = e.read().decode()
            try:
                err = json.loads(body)
                msg = err.get("message", body)
            except json.JSONDecodeError:
                msg = body
            raise GitHubAPIError(e.code, t("err_api_error", code=e.code, msg=msg))
        except URLError as e:
            raise Exception(t("err_network_error", reason=e.reason))

    def get(self, path):
        return self._request("GET", path)

    def post(self, path, data=None):
        return self._request("POST", path, data)

    def put(self, path, data=None):
        return self._request("PUT", path, data)

    def get_user(self):
        return self.get("/user")

    def get_fork(self, owner=None, repo=None):
        if not self.username:
            return None
        
        owner = owner or SOURCE_REPO_OWNER
        repo = repo or SOURCE_REPO_NAME
        
        try:
            user_repo = self.get(f"/repos/{self.username}/{repo}")
            if user_repo.get("fork") and user_repo.get("parent", {}).get("full_name") == f"{owner}/{repo}":
                if not getattr(self, "repo_explicit", False):
                    self.fork_repo = user_repo
                    full_name = user_repo.get("full_name")
                    if full_name:
                        self.repo = full_name
                return user_repo
        except GitHubAPIError as exc:
            if exc.status_code != 404:
                raise
        return None

    def create_fork(self, owner=None, repo=None):
        owner = owner or SOURCE_REPO_OWNER
        repo = repo or SOURCE_REPO_NAME
        return self.post(f"/repos/{owner}/{repo}/forks")

    def wait_for_repo_ready(self, full_name, timeout=30):
        """Wait for GitHub's asynchronous fork creation to become readable."""
        deadline = time.monotonic() + timeout
        last_error = None
        while time.monotonic() < deadline:
            try:
                result = self.get(f"/repos/{full_name}")
                self.repo = result.get("full_name", full_name)
                self.fork_repo = result
                return result
            except Exception as exc:
                last_error = exc
                time.sleep(2)
        raise RuntimeError(f"fork {full_name} was not ready: {last_error}")

    def check_behind(self, fork_owner=None, fork_repo=None, upstream_owner=None, upstream_repo=None):
        fork_owner = fork_owner or self.username
        fork_repo = fork_repo or SOURCE_REPO_NAME
        upstream_owner = upstream_owner or SOURCE_REPO_OWNER
        upstream_repo = upstream_repo or SOURCE_REPO_NAME

        if not fork_owner:
            return {"behind_by": 0, "ahead_by": 0, "error": "cannot detect fork owner"}

        try:
            upstream_info = self.get(f"/repos/{upstream_owner}/{upstream_repo}")
            upstream_branch = upstream_info.get("default_branch", "main")
        except Exception as e:
            return {"behind_by": 0, "ahead_by": 0, "error": f"cannot get upstream info: {e}"}

        try:
            result = self.get(f"/repos/{upstream_owner}/{upstream_repo}/compare/{upstream_branch}...{fork_owner}:{upstream_branch}")
            return {
                "behind_by": result.get("behind_by", 0),
                "ahead_by": result.get("ahead_by", 0),
                "status": result.get("status", "identical")
            }
        except Exception as e:
            return {"behind_by": 0, "ahead_by": 0, "error": str(e)}

    def cancel_run(self, run_id):
        return self.post(f"/repos/{self.repo}/actions/runs/{run_id}/cancel")

    def rerun(self, run_id):
        return self.post(f"/repos/{self.repo}/actions/runs/{run_id}/rerun")

    def sync_fork(self, branch=None):
        if not self.username:
            raise RuntimeError("cannot detect user")
        if self.fork_repo:
            owner = self.fork_repo["owner"]["login"]
            repo = self.fork_repo["name"]
        else:
            owner = self.username
            repo = SOURCE_REPO_NAME
        if branch is None:
            upstream_info = self.get(f"/repos/{SOURCE_REPO_OWNER}/{SOURCE_REPO_NAME}")
            branch = upstream_info.get("default_branch") or "dev"
        return self.post(f"/repos/{owner}/{repo}/merge-upstream", {"branch": branch})

    def trigger_workflow(self, workflow_file, ref, inputs):
        path = f"/repos/{self.repo}/actions/workflows/{workflow_file}/dispatches"
        return self.post(path, {
            "ref": ref,
            "inputs": inputs,
            "return_run_details": True,
        })

    def list_runs(self, workflow_file=None, status=None, per_page=10):
        params = {"per_page": per_page}
        if status:
            params["status"] = status
        if workflow_file:
            path = f"/repos/{self.repo}/actions/workflows/{workflow_file}/runs?{urlencode(params)}"
        else:
            path = f"/repos/{self.repo}/actions/runs?{urlencode(params)}"
        return self.get(path)

    def get_run(self, run_id):
        return self.get(f"/repos/{self.repo}/actions/runs/{run_id}")

    def list_artifacts(self, run_id):
        artifacts = []
        seen_ids = set()
        first_response = None
        page = 1
        while True:
            params = urlencode({"per_page": 100, "page": page})
            response = self.get(
                f"/repos/{self.repo}/actions/runs/{run_id}/artifacts?{params}"
            )
            if first_response is None:
                first_response = dict(response)
            page_items = response.get("artifacts", [])
            if not isinstance(page_items, list):
                page_items = []
            for artifact in page_items:
                artifact_id = (
                    artifact.get("id") if isinstance(artifact, dict) else None
                )
                if artifact_id is not None:
                    if artifact_id in seen_ids:
                        continue
                    seen_ids.add(artifact_id)
                artifacts.append(artifact)
            if not page_items:
                break
            if len(page_items) < 100:
                break
            page += 1

        result = first_response or {}
        result["artifacts"] = artifacts
        result["total_count"] = len(artifacts)
        return result

    def download_artifact(self, artifact_id, output_dir="."):
        url = f"{GITHUB_API}/repos/{self.repo}/actions/artifacts/{artifact_id}/zip"
        headers = {
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "ABK-CLI",
        }
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        req = Request(url, headers=headers)
        response = None
        try:
            response = _open_https_download(
                req,
                timeout=60,
                unsafe_label="GitHub artifact download",
            )

            output_dir = Path(output_dir)
            output_dir.mkdir(parents=True, exist_ok=True)
            output_path = output_dir / f"artifact-{artifact_id}.zip"
            fd, temp_name = tempfile.mkstemp(
                prefix=f".artifact-{artifact_id}-", suffix=".tmp", dir=output_dir
            )
            try:
                with os.fdopen(fd, "wb") as stream:
                    fd = None
                    content_length = None
                    response_headers = getattr(response, "headers", None)
                    if response_headers is not None:
                        raw_length = response_headers.get("Content-Length")
                        if raw_length not in (None, ""):
                            try:
                                content_length = int(raw_length)
                            except (TypeError, ValueError) as exc:
                                raise RuntimeError(
                                    "artifact response has an invalid Content-Length"
                                ) from exc
                            if (
                                content_length < 0
                                or content_length > MAX_ARTIFACT_DOWNLOAD_SIZE
                            ):
                                raise RuntimeError("artifact download is unexpectedly large")

                    downloaded = 0
                    while True:
                        remaining = MAX_ARTIFACT_DOWNLOAD_SIZE - downloaded
                        chunk = response.read(min(1024 * 1024, remaining + 1))
                        if not chunk:
                            break
                        downloaded += len(chunk)
                        if downloaded > MAX_ARTIFACT_DOWNLOAD_SIZE:
                            raise RuntimeError("artifact download is unexpectedly large")
                        stream.write(chunk)
                    if content_length is not None and downloaded != content_length:
                        raise RuntimeError("artifact download was truncated")
                    stream.flush()
                    os.fsync(stream.fileno())
                response.close()
                response = None
                os.replace(temp_name, output_path)
            finally:
                if fd is not None:
                    os.close(fd)
                try:
                    Path(temp_name).unlink()
                except FileNotFoundError:
                    pass
            return str(output_path)
        finally:
            if response is not None:
                response.close()

    def ensure_fork(self):
        if not self.token:
            raise Exception(t("err_no_token"))
        
        if not self.username:
            raise Exception(t("err_no_user_info"))
        
        fork = self.get_fork()
        if fork:
            return {"action": "exists", "fork": fork}
        
        print(t("fork_no_detect_creating"))
        result = self.create_fork()
        return {"action": "created", "fork": result}

    def check_and_prompt_sync(self):
        if not self.username:
            return None
        
        fork = self.get_fork()
        if not fork:
            return {"needs_fork": True}
        
        behind = self.check_behind()
        return {
            "needs_fork": False,
            "fork": fork,
            "behind_by": behind.get("behind_by", 0),
            "needs_sync": behind.get("behind_by", 0) > 0,
            "error": behind.get("error"),
        }

    def _api_url(self, path):
        return f"{GITHUB_API}/repos/{self.repo}/{path.lstrip('/')}"

    def get_repo_public_key(self):
        url = self._api_url("actions/secrets/public-key")
        req = Request(url, headers={
            "Authorization": f"Bearer {self.token}",
            "Accept": "application/vnd.github+json",
            "User-Agent": "ABK-CLI",
        })
        with _open_same_origin_redirect(
            req,
            timeout=30,
            unsafe_label="GitHub API",
        ) as resp:
            return json.loads(resp.read())

    def create_or_update_secret(self, secret_name, secret_value):
        pub = self.get_repo_public_key()
        import nacl.bindings
        key_bytes = base64.b64decode(pub['key'])
        encrypted = nacl.bindings.crypto_box_seal(secret_value.encode(), key_bytes)
        encrypted_b64 = base64.b64encode(encrypted).decode()
        data = json.dumps({
            "encrypted_value": encrypted_b64,
            "key_id": pub['key_id'],
        }).encode()
        url = self._api_url(f"actions/secrets/{secret_name}")
        req = Request(url, data=data, method='PUT', headers={
            "Authorization": f"Bearer {self.token}",
            "Accept": "application/vnd.github+json",
            "User-Agent": "ABK-CLI",
            "Content-Type": "application/json",
        })
        with _open_same_origin_redirect(
            req,
            timeout=30,
            unsafe_label="GitHub API",
        ) as resp:
            return resp.status in (201, 204)

    def repository_secret_exists(self, secret_name):
        try:
            self.get(f"/repos/{self.repo}/actions/secrets/{secret_name}")
            return True
        except GitHubAPIError as exc:
            if exc.status_code == 404:
                return False
            raise

    def delete_repository_secret(self, secret_name):
        try:
            self._request("DELETE", f"/repos/{self.repo}/actions/secrets/{secret_name}")
        except GitHubAPIError as exc:
            if exc.status_code != 404:
                raise

    def get_release_by_tag(self, tag):
        try:
            return self.get(f"/repos/{self.repo}/releases/tags/{tag}")
        except GitHubAPIError as exc:
            if exc.status_code == 404:
                return None
            raise

    def create_release(self, tag):
        return self.post(
            f"/repos/{self.repo}/releases",
            {
                "tag_name": tag,
                "target_commitish": self.get_default_branch(),
                "name": "ABK Artifact Signing Key",
                "body": "ABK fork-scoped artifact signing public key.",
                "prerelease": True,
            },
        )

    def list_release_assets(self, release_id):
        assets = []
        seen_ids = set()
        page = 1
        while True:
            params = urlencode({"per_page": 100, "page": page})
            page_assets = self.get(
                f"/repos/{self.repo}/releases/{release_id}/assets?{params}"
            )
            if not isinstance(page_assets, list):
                raise RuntimeError("GitHub returned an invalid release asset list")
            for asset in page_assets:
                asset_id = asset.get("id") if isinstance(asset, dict) else None
                if asset_id is not None:
                    if asset_id in seen_ids:
                        continue
                    seen_ids.add(asset_id)
                assets.append(asset)
            if not page_assets or len(page_assets) < 100:
                break
            page += 1
        return assets

    def delete_release_asset(self, asset_id):
        try:
            self._request(
                "DELETE",
                f"/repos/{self.repo}/releases/assets/{int(asset_id)}",
            )
        except GitHubAPIError as exc:
            if exc.status_code != 404:
                raise

    def _download_release_asset_text(self, asset_url):
        parsed = _validated_https_url(asset_url, "GitHub release asset")
        api_origin = urlparse(GITHUB_API)
        if (
            parsed.hostname.lower() != api_origin.hostname.lower()
            or parsed.port not in (None, 443)
        ):
            raise RuntimeError("GitHub release asset returned an unsafe URL")
        headers = {
            "Accept": "application/octet-stream",
            "User-Agent": "ABK-CLI",
        }
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        request = Request(
            asset_url,
            headers=headers,
        )
        response = None
        try:
            response = _open_https_download(
                request,
                timeout=30,
                unsafe_label="GitHub release asset",
            )
            content = response.read(MAX_MANIFEST_SIZE + 1)
            if len(content) > MAX_MANIFEST_SIZE:
                raise RuntimeError("published signing key is unexpectedly large")
            return content.decode("utf-8").strip()
        finally:
            if response is not None:
                response.close()

    def get_published_signing_key_snapshot(self):
        """Capture the published key and the exact asset IDs that supplied it."""
        release = self.get_release_by_tag(SIGNING_RELEASE_TAG)
        if not release:
            return {
                "public_key": None,
                "release_id": None,
                "asset_ids": [],
            }
        assets = (
            self.list_release_assets(release["id"])
            if release.get("id") is not None
            else release.get("assets", [])
        )
        signing_assets = [
            asset for asset in assets
            if asset.get("name") == SIGNING_PUBLIC_KEY_ASSET
        ]
        keys = [
            self._download_release_asset_text(asset["url"])
            for asset in signing_assets
        ]
        if keys and any(key.strip() != keys[0].strip() for key in keys[1:]):
            raise SigningStateIndeterminateError(
                "multiple different artifact signing public keys are published"
            )
        return {
            "public_key": keys[0] if keys else None,
            "release_id": release.get("id"),
            "asset_ids": [asset["id"] for asset in signing_assets],
        }

    def get_published_signing_key(self):
        return self.get_published_signing_key_snapshot()["public_key"]

    def _upload_signing_key_asset(self, release, public_key_pem):
        upload_url = str(release.get("upload_url", "")).split("{", 1)[0]
        parsed = urlparse(upload_url)
        if (
            parsed.scheme != "https"
            or parsed.hostname != "uploads.github.com"
            or parsed.port not in (None, 443)
            or parsed.username is not None
            or parsed.password is not None
        ):
            raise RuntimeError("GitHub returned an unsafe release upload URL")
        url = f"{upload_url}?{urlencode({'name': SIGNING_PUBLIC_KEY_ASSET})}"
        request = Request(
            url,
            data=public_key_pem.encode("utf-8"),
            method="POST",
            headers={
                "Authorization": f"Bearer {self.token}",
                "Accept": "application/vnd.github+json",
                "Content-Type": "application/x-pem-file",
                "User-Agent": "ABK-CLI",
            },
        )
        with _open_same_origin_redirect(
            request,
            timeout=30,
            unsafe_label="GitHub release upload",
        ) as response:
            return response.status in (200, 201)

    def publish_signing_key(self, public_key_pem):
        release = self.get_release_by_tag(SIGNING_RELEASE_TAG)
        if not release:
            release = self.create_release(SIGNING_RELEASE_TAG)

        assets = (
            self.list_release_assets(release["id"])
            if release.get("id") is not None
            else release.get("assets", [])
        )
        for asset in assets:
            if asset.get("name") == SIGNING_PUBLIC_KEY_ASSET:
                current = self._download_release_asset_text(asset["url"])
                if current.strip() == public_key_pem.strip():
                    return True
                raise RuntimeError("a different artifact signing public key is already published")

        return self._upload_signing_key_asset(release, public_key_pem)

    def replace_published_signing_key(
        self,
        public_key_pem,
        *,
        expected_previous_key=None,
    ):
        """Replace only the fixed-name assets from the caller's observed snapshot."""
        release = self.get_release_by_tag(SIGNING_RELEASE_TAG)
        if not release:
            release = self.create_release(SIGNING_RELEASE_TAG)
        assets = self.list_release_assets(release["id"])
        signing_assets = [
            asset for asset in assets
            if asset.get("name") == SIGNING_PUBLIC_KEY_ASSET
        ]
        if expected_previous_key is None:
            if signing_assets:
                raise SigningStateIndeterminateError(
                    "a signing public key appeared before replacement; the "
                    "concurrent asset was not touched"
                )
        else:
            if not signing_assets:
                raise SigningStateIndeterminateError(
                    "the previously observed signing public key disappeared before "
                    "replacement"
                )
            try:
                current_keys = [
                    self._download_release_asset_text(asset["url"]).strip()
                    for asset in signing_assets
                ]
            except Exception as exc:
                raise SigningStateIndeterminateError(
                    "GitHub did not confirm the old signing public key snapshot; "
                    "no asset was touched"
                ) from exc
            if any(
                key != expected_previous_key.strip()
                for key in current_keys
            ):
                raise SigningStateIndeterminateError(
                    "the signing public key changed before replacement; the "
                    "concurrent asset was not touched"
                )
        try:
            for asset in signing_assets:
                self.delete_release_asset(asset["id"])
        except Exception as exc:
            raise SigningStateIndeterminateError(
                "signing public key replacement stopped while deleting the old asset"
            ) from exc

        upload_error = None
        try:
            if self._upload_signing_key_asset(release, public_key_pem):
                return True
            upload_error = RuntimeError(
                "GitHub rejected the signing public key asset"
            )
        except Exception as exc:
            upload_error = exc

        # A failed response can mean the upload committed. Never implement a
        # rollback by deleting whatever fixed-name asset exists now: Android or
        # another CLI may have completed its own matching keypair meanwhile.
        try:
            current_assets = [
                asset for asset in self.list_release_assets(release["id"])
                if asset.get("name") == SIGNING_PUBLIC_KEY_ASSET
            ]
            if current_assets:
                current_keys = [
                    self._download_release_asset_text(asset["url"]).strip()
                    for asset in current_assets
                ]
                if all(key == public_key_pem.strip() for key in current_keys):
                    return True
                raise SigningStateIndeterminateError(
                    "a different signing public key appeared while this "
                    "replacement was being confirmed; it was not touched"
                )
        except SigningStateIndeterminateError:
            raise
        except Exception as exc:
            raise SigningStateIndeterminateError(
                "GitHub did not confirm which signing public key asset is active; "
                "no concurrent asset was touched"
            ) from exc
        raise SigningStateIndeterminateError(
            "GitHub did not confirm the replacement signing public key asset"
        ) from upload_error

    def delete_published_signing_key(self, *, snapshot=None):
        """Delete only the fixed-name asset IDs captured by the caller."""
        snapshot = snapshot or self.get_published_signing_key_snapshot()
        try:
            for asset_id in snapshot.get("asset_ids", []):
                self.delete_release_asset(asset_id)
        except Exception as exc:
            raise SigningStateIndeterminateError(
                "signing public key deletion stopped before all original assets "
                "were confirmed absent; no concurrent asset was touched"
            ) from exc


def _signing_repo_key(repo):
    return str(repo or "").strip().lower()


def _get_signing_state(config, repo):
    states = config.get(SIGNING_STATE_CONFIG_KEY, {})
    if not isinstance(states, dict):
        return {}
    state = states.get(_signing_repo_key(repo), {})
    return state if isinstance(state, dict) else {}


def _write_signing_state(config, repo, state):
    repo_key = _signing_repo_key(repo)
    if not repo_key:
        raise ValueError("cannot save artifact signing state without a repository")
    with _config_process_lock():
        latest = load_config()
        states = latest.get(SIGNING_STATE_CONFIG_KEY, {})
        if not isinstance(states, dict):
            states = {}
        states[repo_key] = dict(state)
        latest[SIGNING_STATE_CONFIG_KEY] = states
        # Remove the old global state so a key from one fork can never be reused
        # implicitly for another account or explicit --repo target.
        for legacy_key in ("signing_key", "signing_secret_name", "signing_key_version"):
            latest.pop(legacy_key, None)
        save_config(latest)
        config.clear()
        config.update(latest)


def _save_signing_state(config, repo, public_key_pem):
    _write_signing_state(
        config,
        repo,
        {
            "public_key": public_key_pem,
            "secret_name": SIGNING_SECRET_NAME,
            "version": SIGNING_KEY_VERSION,
            "verification_enabled": True,
        },
    )


def _save_signing_disabled_state(config, repo):
    _write_signing_state(
        config,
        repo,
        {
            "version": SIGNING_KEY_VERSION,
            "verification_enabled": False,
        },
    )


def _save_signing_indeterminate_state(config, repo):
    latest = load_config()
    verification_enabled = signing_verification_enabled(repo, latest)
    _write_signing_state(
        config,
        repo,
        {
            "version": SIGNING_KEY_VERSION,
            "verification_enabled": verification_enabled,
            "indeterminate": True,
        },
    )


def signing_verification_enabled(repo=None, config=None):
    """Return the repo-scoped CLI verification preference (enabled by default)."""
    config = load_config() if config is None else config
    if repo:
        return _get_signing_state(config, repo).get("verification_enabled") is not False
    states = config.get(SIGNING_STATE_CONFIG_KEY, {})
    if isinstance(states, dict) and len(states) == 1:
        state = next(iter(states.values()))
        if isinstance(state, dict):
            return state.get("verification_enabled") is not False
    return True


def _assert_remote_signing_disabled(client):
    remote_snapshot = _read_remote_signing_snapshot(client)
    secret_exists = remote_snapshot["secret_exists"]
    if remote_snapshot["public_key_exists"] or secret_exists:
        raise RuntimeError(
            "signing material was re-enabled by another client; run "
            "'abk signing enable' to trust it or 'abk signing disable --yes' "
            "to remove it again"
        )


def get_signing_key(repo=None):
    """Load a public verification key scoped to one GitHub repository."""
    external_key = os.environ.get("ABK_SIGNING_KEY")
    if external_key:
        return external_key
    config = load_config()
    if repo:
        return _get_signing_state(config, repo).get("public_key")
    states = config.get(SIGNING_STATE_CONFIG_KEY, {})
    if isinstance(states, dict) and len(states) == 1:
        state = next(iter(states.values()))
        if isinstance(state, dict):
            return state.get("public_key")
    return None


def generate_signing_keypair():
    """Return Android-compatible (PKCS#8 DER base64, public-key PEM)."""
    if not _CRYPTO_BACKEND:
        raise RuntimeError(
            "Artifact signing requires cryptography, pycryptodomex, or pycryptodome"
        )
    if _CRYPTO_BACKEND == 'cryptography':
        from cryptography.hazmat.primitives.asymmetric import rsa
        key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        private_key_der = key.private_bytes(
            encoding=serialization.Encoding.DER,
            format=serialization.PrivateFormat.PKCS8,
            encryption_algorithm=serialization.NoEncryption(),
        )
        public_key_pem = key.public_key().public_bytes(
            encoding=serialization.Encoding.PEM,
            format=serialization.PublicFormat.SubjectPublicKeyInfo,
        ).decode()
    else:
        key = RSA.generate(2048)
        private_key_der = key.export_key('DER', passphrase=None, pkcs=8)
        public_key_pem = key.publickey().export_key('PEM').decode()
    return (
        base64.b64encode(private_key_der).decode("ascii"),
        public_key_pem.rstrip("\r\n") + "\n",
    )


def _decode_signing_pem(value, pem_type):
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{pem_type.lower()} PEM is empty")
    if len(value.encode("utf-8")) > MAX_SIGNING_KEY_FILE_SIZE:
        raise ValueError(f"{pem_type.lower()} PEM is unexpectedly large")
    pattern = re.compile(
        rf"\A\s*-----BEGIN {re.escape(pem_type)}-----\s*"
        rf"(?P<body>[A-Za-z0-9+/=\s]+?)\s*"
        rf"-----END {re.escape(pem_type)}-----\s*\Z"
    )
    match = pattern.fullmatch(value)
    if not match:
        raise ValueError(f"expected an unencrypted {pem_type} PEM block")
    encoded = "".join(match.group("body").split())
    try:
        decoded = base64.b64decode(encoded, validate=True)
    except (ValueError, binascii.Error) as exc:
        raise ValueError(f"invalid {pem_type.lower()} PEM") from exc
    if not decoded:
        raise ValueError(f"{pem_type.lower()} PEM is empty")
    return decoded


def load_signing_keypair(public_key_pem, private_key_pem):
    """Return an Android-compatible (PKCS#8 DER base64, SPKI public PEM) pair."""
    if not _CRYPTO_BACKEND:
        raise RuntimeError(
            "Artifact signing requires cryptography, pycryptodomex, or pycryptodome"
        )
    public_der = _decode_signing_pem(public_key_pem, "PUBLIC KEY")
    private_der = _decode_signing_pem(private_key_pem, "PRIVATE KEY")

    try:
        if _CRYPTO_BACKEND == "cryptography":
            from cryptography.hazmat.primitives.asymmetric import rsa

            public_key = serialization.load_der_public_key(public_der)
            private_key = serialization.load_der_private_key(private_der, password=None)
            if not isinstance(public_key, rsa.RSAPublicKey):
                raise ValueError("artifact signing public key must be RSA")
            if not isinstance(private_key, rsa.RSAPrivateKey):
                raise ValueError("artifact signing private key must be RSA")
            if public_key.key_size < 2048 or private_key.key_size < 2048:
                raise ValueError("artifact signing RSA key must be at least 2048 bits")
            if private_key.public_key().public_numbers() != public_key.public_numbers():
                raise ValueError("artifact signing public and private keys do not match")
            normalized_public_der = public_key.public_bytes(
                encoding=serialization.Encoding.DER,
                format=serialization.PublicFormat.SubjectPublicKeyInfo,
            )
            normalized_public_pem = public_key.public_bytes(
                encoding=serialization.Encoding.PEM,
                format=serialization.PublicFormat.SubjectPublicKeyInfo,
            ).decode("ascii")
            normalized_private_der = private_key.private_bytes(
                encoding=serialization.Encoding.DER,
                format=serialization.PrivateFormat.PKCS8,
                encryption_algorithm=serialization.NoEncryption(),
            )
        else:
            public_key = RSA.import_key(public_der)
            private_key = RSA.import_key(private_der)
            if public_key.has_private():
                raise ValueError("artifact signing public key must not contain private material")
            if not private_key.has_private():
                raise ValueError("artifact signing private key is incomplete")
            if public_key.size_in_bits() < 2048 or private_key.size_in_bits() < 2048:
                raise ValueError("artifact signing RSA key must be at least 2048 bits")
            if (public_key.n, public_key.e) != (private_key.n, private_key.e):
                raise ValueError("artifact signing public and private keys do not match")
            normalized_public_der = public_key.publickey().export_key("DER")
            normalized_public_pem = public_key.publickey().export_key("PEM").decode("ascii")
            if not normalized_public_pem.endswith("\n"):
                normalized_public_pem += "\n"
            normalized_private_der = private_key.export_key(
                "DER",
                passphrase=None,
                pkcs=8,
            )
    except ValueError:
        raise
    except Exception as exc:
        raise ValueError("invalid artifact signing key pair") from exc

    fingerprint = hashlib.sha256(normalized_public_der).hexdigest()
    return (
        base64.b64encode(normalized_private_der).decode("ascii"),
        normalized_public_pem,
        fingerprint,
    )


def signing_key_fingerprint(public_key_pem):
    normalized = normalize_signing_public_key(public_key_pem)
    if _CRYPTO_BACKEND == "cryptography":
        key = serialization.load_pem_public_key(normalized.encode("ascii"))
        der = key.public_bytes(
            encoding=serialization.Encoding.DER,
            format=serialization.PublicFormat.SubjectPublicKeyInfo,
        )
    else:
        der = RSA.import_key(normalized).publickey().export_key("DER")
    return hashlib.sha256(der).hexdigest()


def _safe_signing_key_fingerprint(public_key_pem):
    if not public_key_pem:
        return None
    try:
        return signing_key_fingerprint(public_key_pem)
    except Exception:
        return None


def read_signing_key_file(path, label):
    key_path = Path(path).expanduser()
    try:
        size = key_path.stat().st_size
    except OSError as exc:
        raise ValueError(f"cannot read {label} key file: {key_path}") from exc
    if not key_path.is_file():
        raise ValueError(f"{label} key path is not a file: {key_path}")
    if size > MAX_SIGNING_KEY_FILE_SIZE:
        raise ValueError(f"{label} key file is unexpectedly large")
    try:
        return key_path.read_text(encoding="utf-8")
    except (OSError, UnicodeError) as exc:
        raise ValueError(f"cannot read {label} key file: {key_path}") from exc


def normalize_signing_public_key(public_key_pem):
    if not _CRYPTO_BACKEND:
        raise RuntimeError(
            "Artifact signing requires cryptography, pycryptodomex, or pycryptodome"
        )
    if not isinstance(public_key_pem, str) or not public_key_pem.strip():
        raise ValueError("artifact signing public key is empty")
    if _CRYPTO_BACKEND == 'cryptography':
        from cryptography.hazmat.primitives.asymmetric import rsa

        key = serialization.load_pem_public_key(public_key_pem.encode("ascii"))
        if not isinstance(key, rsa.RSAPublicKey):
            raise ValueError("artifact signing key must be RSA")
        if key.key_size < 2048:
            raise ValueError("artifact signing RSA key must be at least 2048 bits")
        normalized_public_pem = key.public_bytes(
            encoding=serialization.Encoding.PEM,
            format=serialization.PublicFormat.SubjectPublicKeyInfo,
        ).decode("ascii")
    else:
        key = RSA.import_key(public_key_pem)
        if key.size_in_bits() < 2048:
            raise ValueError("artifact signing RSA key must be at least 2048 bits")
        normalized_public_pem = key.publickey().export_key('PEM').decode("ascii")
    return normalized_public_pem.rstrip("\r\n") + "\n"


@contextlib.contextmanager
def _config_process_lock(timeout=120):
    """Serialize config transactions across threads and local CLI processes."""
    with _CONFIG_THREAD_LOCK:
        depth = getattr(_CONFIG_LOCK_STATE, "depth", 0)
        if depth:
            _CONFIG_LOCK_STATE.depth = depth + 1
            try:
                yield
            finally:
                _CONFIG_LOCK_STATE.depth -= 1
            return

        CONFIG_DIR.mkdir(parents=True, exist_ok=True, mode=0o700)
        if os.name != "nt":
            CONFIG_DIR.chmod(0o700)
        lock_path = CONFIG_DIR / CONFIG_LOCK_FILE
        flags = os.O_RDWR | os.O_CREAT
        if hasattr(os, "O_BINARY"):
            flags |= os.O_BINARY
        fd = os.open(lock_path, flags, 0o600)
        try:
            stream = os.fdopen(fd, "r+b", buffering=0)
        except Exception:
            os.close(fd)
            raise
        locked = False
        try:
            if os.name != "nt":
                os.fchmod(stream.fileno(), 0o600)
            stream.seek(0, os.SEEK_END)
            if stream.tell() == 0:
                stream.write(b"\0")
            deadline = time.monotonic() + timeout
            contention_errnos = {errno.EACCES, errno.EAGAIN}
            if os.name == "nt" and hasattr(errno, "EDEADLK"):
                contention_errnos.add(errno.EDEADLK)
            while True:
                try:
                    stream.seek(0)
                    if os.name == "nt":
                        import msvcrt

                        msvcrt.locking(stream.fileno(), msvcrt.LK_NBLCK, 1)
                    else:
                        import fcntl

                        fcntl.flock(stream.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
                    locked = True
                    break
                except (BlockingIOError, OSError) as exc:
                    if exc.errno not in contention_errnos:
                        raise
                    if time.monotonic() >= deadline:
                        raise TimeoutError(
                            "timed out waiting for the ABK config lock"
                        ) from exc
                    time.sleep(0.1)
            _CONFIG_LOCK_STATE.depth = 1
            try:
                yield
            finally:
                _CONFIG_LOCK_STATE.depth = 0
        finally:
            if locked:
                try:
                    stream.seek(0)
                    if os.name == "nt":
                        import msvcrt

                        msvcrt.locking(stream.fileno(), msvcrt.LK_UNLCK, 1)
                    else:
                        import fcntl

                        fcntl.flock(stream.fileno(), fcntl.LOCK_UN)
                except OSError:
                    pass
            stream.close()


def ensure_signing_key(client, *, force_enable=False):
    """Ensure the target repo has a private signing secret; retain only its public key."""
    if not client.token:
        raise RuntimeError(t("err_no_token"))
    if not client.repo or (
        client.repo == DEFAULT_REPO and not getattr(client, "repo_explicit", False)
    ):
        raise RuntimeError("artifact signing must be configured on a fork or explicit repo")

    # `load_config()` and every remote read intentionally happen after the
    # lock is acquired so a process that waited cannot act on stale state.
    with _config_process_lock():
        return _ensure_signing_key_locked(client, force_enable=force_enable)


def _publish_and_confirm_signing_key(client, public_key_pem):
    if not client.publish_signing_key(public_key_pem):
        raise RuntimeError("GitHub did not accept the signing public key asset")
    published = client.get_published_signing_key()
    if not published:
        raise RuntimeError("GitHub did not return the published signing public key")
    published = normalize_signing_public_key(published)
    expected = normalize_signing_public_key(public_key_pem)
    if published.strip() != expected.strip():
        raise RuntimeError("artifact signing public key changed concurrently")
    return published


def _create_or_update_signing_secret(
    client,
    private_key_b64,
    public_key_pem,
    attempts=3,
):
    """Retry a Secret PUT only while the matching public key remains stable."""
    expected_key = normalize_signing_public_key(public_key_pem).strip()
    last_error = None
    for attempt in range(attempts):
        if attempt:
            time.sleep(0.5 * attempt)

        # Android publishes its public asset before its Secret.  Read the
        # public key on both sides of the Secret existence check so a rotation
        # during a retry never leads to a blind overwrite with our private key.
        public_before = client.get_published_signing_key()
        secret_exists = client.repository_secret_exists(SIGNING_SECRET_NAME)
        public_after = client.get_published_signing_key()
        normalized_keys = [
            normalize_signing_public_key(value).strip()
            for value in (public_before, public_after)
            if value
        ]
        if (
            len(normalized_keys) != 2
            or any(value != expected_key for value in normalized_keys)
        ):
            raise RuntimeError(
                "artifact signing public key changed before secret publication; "
                "retry after Android finishes"
            )
        if secret_exists:
            if attempt:
                # The previous PUT may have reached GitHub even if its response
                # was lost.  Never overwrite an existing write-only Secret.
                return
            raise RuntimeError(
                "artifact signing secret appeared concurrently; retry after "
                "Android finishes"
            )

        try:
            if client.create_or_update_secret(SIGNING_SECRET_NAME, private_key_b64):
                return
            last_error = RuntimeError("GitHub rejected the signing secret update")
        except Exception as exc:
            last_error = exc
    raise RuntimeError(
        "GitHub did not accept the artifact signing secret; regenerate "
        "signing material from the ABK app"
    ) from last_error


def _ensure_signing_key_locked(client, *, force_enable=False):
    config = load_config()
    external_key = os.environ.get("ABK_SIGNING_KEY")
    state = _get_signing_state(config, client.repo)
    if state.get("indeterminate") is True:
        raise SigningStateIndeterminateError(
            "a previous signing-key operation left the repository state "
            "indeterminate; repair it with 'abk signing import', "
            "'abk signing rotate --yes', or 'abk signing disable --yes'"
        )
    if state.get("verification_enabled") is False and not force_enable:
        _assert_remote_signing_disabled(client)
        return None
    existing = external_key or state.get("public_key")
    initialized = (
        state.get("secret_name") == SIGNING_SECRET_NAME
        and state.get("version") == SIGNING_KEY_VERSION
    )
    secret_exists = client.repository_secret_exists(SIGNING_SECRET_NAME)
    published_key = client.get_published_signing_key()
    if published_key:
        published_key = normalize_signing_public_key(published_key)

    if external_key:
        existing = normalize_signing_public_key(external_key)
        if published_key and published_key.strip() != existing.strip():
            raise RuntimeError(
                "ABK_SIGNING_KEY does not match the public key published by the target repo"
            )
        if not secret_exists:
            raise RuntimeError(
                "ABK_SIGNING_KEY provides only a public key, but the matching private "
                "GitHub secret is missing"
            )
        if not published_key:
            _publish_and_confirm_signing_key(client, existing)
        return existing

    if existing and initialized:
        if published_key:
            # The Android app is the authoritative key manager.  Prefer its
            # valid remote key even if the CLI's cached copy is stale or
            # damaged.
            existing = published_key
        else:
            existing = normalize_signing_public_key(existing)
        if not secret_exists:
            raise RuntimeError(
                "the signing public key exists but the private GitHub secret is missing; "
                "regenerate signing material from the ABK app"
            )
        if not published_key:
            _publish_and_confirm_signing_key(client, existing)
        _save_signing_state(config, client.repo, existing)
        return existing

    if published_key:
        if not secret_exists:
            raise RuntimeError(
                "the published signing key has no matching private GitHub secret; "
                "regenerate signing material from the ABK app"
            )
        _save_signing_state(config, client.repo, published_key)
        return published_key

    if secret_exists:
        raise RuntimeError(
            "the signing secret already exists but its public key is unavailable; "
            "set ABK_SIGNING_KEY or publish the key from the ABK app"
        )

    print(t("signing_key_generating"))
    private_key_b64, public_key_pem = generate_signing_keypair()
    try:
        public_key_pem = _publish_and_confirm_signing_key(client, public_key_pem)
    except Exception as exc:
        # A same-name release asset is the closest available remote CAS.  If
        # Android won the race and already completed its matching Secret, adopt
        # that pair without ever overwriting the Secret with our private key.
        competing_key = client.get_published_signing_key()
        if competing_key:
            competing_key = normalize_signing_public_key(competing_key)
            candidate_key = normalize_signing_public_key(public_key_pem)
            if competing_key.strip() == candidate_key.strip():
                # The upload succeeded but its response/confirmation failed;
                # this process still owns the matching private material.
                public_key_pem = competing_key
            elif client.repository_secret_exists(SIGNING_SECRET_NAME):
                confirmed_competing_key = client.get_published_signing_key()
                if not confirmed_competing_key or (
                    normalize_signing_public_key(confirmed_competing_key).strip()
                    != competing_key.strip()
                ):
                    raise RuntimeError(
                        "another signing initializer changed its public key; "
                        "retry after Android finishes"
                    ) from exc
                _save_signing_state(config, client.repo, competing_key)
                return competing_key
            else:
                raise RuntimeError(
                    "another signing initializer is still publishing its secret"
                ) from exc
        else:
            raise RuntimeError(
                "artifact signing public key publication failed before the private "
                "secret was changed"
            ) from exc

    # Publish first so a competing Android/CLI initializer can win via the
    # unique asset name before this process changes the write-only Secret.
    current_key = client.get_published_signing_key()
    if not current_key or (
        normalize_signing_public_key(current_key).strip() != public_key_pem.strip()
    ):
        raise RuntimeError(
            "artifact signing public key changed before secret publication; "
            "retry after Android finishes"
        )

    # Android also initializes this pair public-key-first.  Recheck immediately
    # before the Secret PUT so a completed Android initialization is adopted
    # instead of being overwritten with this process's private key.
    if client.repository_secret_exists(SIGNING_SECRET_NAME):
        raise RuntimeError(
            "artifact signing secret appeared concurrently; retry after "
            "Android finishes"
        )

    _create_or_update_signing_secret(client, private_key_b64, public_key_pem)
    confirmed_key = client.get_published_signing_key()
    if not confirmed_key or (
        normalize_signing_public_key(confirmed_key).strip() != public_key_pem.strip()
    ):
        raise RuntimeError(
            "artifact signing material changed concurrently; regenerate it from "
            "the ABK app"
        )

    _save_signing_state(config, client.repo, public_key_pem)
    print(t("signing_key_generated"))
    return public_key_pem


class SigningStateIndeterminateError(RuntimeError):
    pass


class SigningArgumentsError(ValueError):
    pass


class SigningKeyInputError(ValueError):
    pass


class SigningRemoteStateChangedError(RuntimeError):
    pass


def _capture_published_signing_key_snapshot(client):
    capture = getattr(client, "get_published_signing_key_snapshot", None)
    if callable(capture):
        return capture()
    return {
        "public_key": client.get_published_signing_key(),
        "release_id": None,
        "asset_ids": None,
    }


def _signing_public_snapshot_identity(public_key):
    if public_key is None:
        return None
    try:
        return normalize_signing_public_key(public_key).strip()
    except Exception:
        return f"invalid:{str(public_key).strip()}"


def _read_remote_signing_snapshot(client, *, published_snapshot=None):
    published_snapshot = (
        published_snapshot
        if published_snapshot is not None
        else _capture_published_signing_key_snapshot(client)
    )
    public_key = published_snapshot.get("public_key")
    asset_ids = published_snapshot.get("asset_ids")
    public_key_exists = (
        bool(asset_ids)
        if asset_ids is not None
        else public_key is not None
    )
    return {
        "public_key": public_key,
        "public_key_exists": public_key_exists,
        "public_key_identity": _signing_public_snapshot_identity(public_key),
        "secret_exists": bool(
            client.repository_secret_exists(SIGNING_SECRET_NAME)
        ),
        "published_snapshot": published_snapshot,
    }


def _expected_remote_signing_snapshot(snapshot):
    return {
        "public_key_identity": snapshot["public_key_identity"],
        "public_key_exists": snapshot["public_key_exists"],
        "secret_exists": snapshot["secret_exists"],
    }


def _assert_expected_remote_signing_snapshot(current, expected):
    if expected is None:
        return
    if (
        current["public_key_identity"] != expected.get("public_key_identity")
        or current["public_key_exists"] != bool(expected.get("public_key_exists"))
        or current["secret_exists"] != bool(expected.get("secret_exists"))
    ):
        raise SigningRemoteStateChangedError(
            "the remote signing state changed after it was inspected; no remote "
            "material was touched, so rerun the command and confirm the new state"
        )


def _delete_published_signing_key_snapshot(client, snapshot):
    if snapshot.get("asset_ids") is None:
        client.delete_published_signing_key()
        return
    client.delete_published_signing_key(snapshot=snapshot)


def _ensure_signing_key_for_repository_setup(client):
    """Keep account/fork operations successful when only signing is locked."""
    try:
        ensure_signing_key(client)
    except SigningStateIndeterminateError as exc:
        warning = {
            "code": "signing_state_indeterminate",
            "message": str(exc),
        }
        print(f"{t('warning_prefix')} {warning['message']}", file=sys.stderr)
        return warning
    return None


@contextlib.contextmanager
def _persist_indeterminate_signing_state_on_error(client):
    try:
        yield
    except SigningStateIndeterminateError as exc:
        try:
            _save_signing_indeterminate_state(load_config(), client.repo)
        except Exception as state_exc:
            raise SigningStateIndeterminateError(
                "the remote signing state is indeterminate and the local safety "
                "lock could not be saved"
            ) from state_exc
        raise


def _delete_signing_secret_confirmed(client):
    delete_error = None
    try:
        client.delete_repository_secret(SIGNING_SECRET_NAME)
    except Exception as exc:
        delete_error = exc
    try:
        secret_still_exists = client.repository_secret_exists(SIGNING_SECRET_NAME)
    except Exception as exc:
        raise SigningStateIndeterminateError(
            "GitHub did not confirm whether the signing Secret was deleted; "
            "the remote signing state may be indeterminate"
        ) from exc
    if secret_still_exists:
        if delete_error is not None:
            raise delete_error
        raise RuntimeError("GitHub did not delete the signing Secret")


def _signing_secret_exists_confirmed(client, context):
    try:
        return client.repository_secret_exists(SIGNING_SECRET_NAME)
    except Exception as exc:
        raise SigningStateIndeterminateError(
            f"GitHub did not confirm the signing Secret state {context}; "
            "the remote signing state may be indeterminate"
        ) from exc


def _abort_if_signing_secret_reappeared(client, context):
    if _signing_secret_exists_confirmed(client, context):
        raise SigningStateIndeterminateError(
            f"the signing Secret reappeared {context}; its ownership cannot be "
            "confirmed, so it was not touched"
        )


def _put_rotated_signing_secret(
    client,
    private_key_b64,
    public_key_pem,
    attempts=3,
):
    """Intentionally replace the write-only Secret with one validated keypair."""
    expected_key = normalize_signing_public_key(public_key_pem).strip()
    last_error = None
    for attempt in range(attempts):
        if attempt:
            time.sleep(0.5 * attempt)
        try:
            published = client.get_published_signing_key()
            public_key_confirmed = bool(published) and (
                normalize_signing_public_key(published).strip() == expected_key
            )
        except Exception as exc:
            raise SigningStateIndeterminateError(
                "GitHub did not confirm the signing public key before the Secret "
                "update; the active Secret was not touched"
            ) from exc
        if not public_key_confirmed:
            raise SigningStateIndeterminateError(
                "artifact signing public key changed before the Secret update "
                "could be confirmed; the active Secret was not touched"
            )
        try:
            accepted = client.create_or_update_secret(
                SIGNING_SECRET_NAME,
                private_key_b64,
            )
            if accepted:
                confirmed = client.get_published_signing_key()
                if not confirmed or (
                    normalize_signing_public_key(confirmed).strip() != expected_key
                ):
                    raise SigningStateIndeterminateError(
                        "artifact signing public key changed during Secret rotation; "
                        "the active write-only Secret may belong to either client and "
                        "was not touched"
                    )
                return
            last_error = RuntimeError("GitHub rejected the signing secret update")
        except SigningStateIndeterminateError:
            raise
        except Exception as exc:
            last_error = exc
    if _signing_secret_exists_confirmed(
        client,
        "after the signing Secret update could not be confirmed",
    ):
        raise SigningStateIndeterminateError(
            "GitHub did not confirm the signing Secret update; the active "
            "write-only Secret was not touched because its ownership is unknown"
        ) from last_error
    raise SigningStateIndeterminateError(
        "GitHub did not confirm the signing Secret update; no active Secret "
        "was found, but the incomplete rotation was safety-locked"
    ) from last_error


def install_signing_keypair(
    client,
    private_key_b64,
    public_key_pem,
    *,
    expected_remote_snapshot=None,
):
    """Install or rotate one validated signing pair without persisting its private key."""
    if not client.token:
        raise RuntimeError(t("err_no_token"))
    if not client.repo or (
        client.repo == DEFAULT_REPO and not getattr(client, "repo_explicit", False)
    ):
        raise RuntimeError("artifact signing must be configured on a fork or explicit repo")

    public_key_pem = normalize_signing_public_key(public_key_pem)
    fingerprint = signing_key_fingerprint(public_key_pem)
    external_key = os.environ.get("ABK_SIGNING_KEY")
    if external_key and (
        normalize_signing_public_key(external_key).strip() != public_key_pem.strip()
    ):
        raise RuntimeError(
            "ABK_SIGNING_KEY conflicts with the signing key being installed; "
            "remove or update the environment variable first"
        )

    with (
        _config_process_lock(),
        _persist_indeterminate_signing_state_on_error(client),
    ):
        verification_was_enabled = signing_verification_enabled(client.repo)
        remote_snapshot = _read_remote_signing_snapshot(client)
        _assert_expected_remote_signing_snapshot(
            remote_snapshot,
            expected_remote_snapshot,
        )
        secret_existed = remote_snapshot["secret_exists"]
        old_public_key = remote_snapshot["public_key"]
        try:
            old_normalized = (
                normalize_signing_public_key(old_public_key)
                if old_public_key
                else None
            )
        except Exception:
            old_normalized = None
        public_key_changed = (
            not old_normalized or old_normalized.strip() != public_key_pem.strip()
        )
        if secret_existed:
            _delete_signing_secret_confirmed(client)
        if _signing_secret_exists_confirmed(client, "before public-key rotation"):
            raise SigningStateIndeterminateError(
                "the signing Secret reappeared concurrently before public-key "
                "rotation; the public key was not changed"
            )
        if public_key_changed:
            if not client.replace_published_signing_key(
                public_key_pem,
                expected_previous_key=old_public_key,
            ):
                raise RuntimeError("GitHub rejected the signing public key asset")
            _abort_if_signing_secret_reappeared(
                client,
                "during public-key rotation",
            )
        try:
            confirmed = client.get_published_signing_key()
            public_key_confirmed = bool(confirmed) and (
                normalize_signing_public_key(confirmed).strip()
                == public_key_pem.strip()
            )
        except Exception as exc:
            raise SigningStateIndeterminateError(
                "GitHub did not confirm the signing public key after rotation"
            ) from exc
        if not public_key_confirmed:
            raise SigningStateIndeterminateError(
                "the signing public key changed during rotation"
            )

        _abort_if_signing_secret_reappeared(
            client,
            "before the new private key was installed",
        )

        _put_rotated_signing_secret(client, private_key_b64, public_key_pem)
        config = load_config()
        _save_signing_state(config, client.repo, public_key_pem)
        return {
            "changed": (
                public_key_changed
                or not secret_existed
                or not verification_was_enabled
            ),
            "public_key_changed": public_key_changed,
            "public_key": public_key_pem,
            "fingerprint": fingerprint,
            "previous_fingerprint": (
                _safe_signing_key_fingerprint(old_normalized)
            ),
        }


def disable_signing_verification(client, *, expected_remote_snapshot=None):
    """Delete fork signing material and persist a repo-scoped disabled preference."""
    if not client.token:
        raise RuntimeError(t("err_no_token"))
    if not client.repo or (
        client.repo == DEFAULT_REPO and not getattr(client, "repo_explicit", False)
    ):
        raise RuntimeError("artifact signing must be configured on a fork or explicit repo")

    with (
        _config_process_lock(),
        _persist_indeterminate_signing_state_on_error(client),
    ):
        enabled_before = signing_verification_enabled(client.repo)
        remote_snapshot = _read_remote_signing_snapshot(client)
        _assert_expected_remote_signing_snapshot(
            remote_snapshot,
            expected_remote_snapshot,
        )
        secret_existed = remote_snapshot["secret_exists"]
        old_public_key = remote_snapshot["public_key"]
        if secret_existed:
            _delete_signing_secret_confirmed(client)
        _abort_if_signing_secret_reappeared(
            client,
            "before signing verification was disabled",
        )
        try:
            _delete_published_signing_key_snapshot(
                client,
                remote_snapshot["published_snapshot"],
            )
        except SigningStateIndeterminateError:
            raise
        except Exception as exc:
            raise SigningStateIndeterminateError(
                "GitHub did not complete signing public key deletion after the "
                "Secret was removed"
            ) from exc

        try:
            public_before = client.get_published_signing_key()
            secret_after = client.repository_secret_exists(SIGNING_SECRET_NAME)
            public_after = client.get_published_signing_key()
        except Exception as exc:
            raise SigningStateIndeterminateError(
                "GitHub did not confirm that signing material is absent after "
                "verification was disabled"
            ) from exc
        if public_before is not None or secret_after or public_after is not None:
            raise SigningStateIndeterminateError(
                "signing material appeared while verification was being disabled; "
                "the concurrent material was not touched"
            )

        config = load_config()
        _save_signing_disabled_state(config, client.repo)
        return {
            "changed": bool(
                secret_existed
                or remote_snapshot["public_key_exists"]
                or enabled_before
            ),
            "previous_fingerprint": _safe_signing_key_fingerprint(old_public_key),
        }


def get_signing_status(client):
    config = load_config()
    enabled = signing_verification_enabled(client.repo, config)
    local_state = _get_signing_state(config, client.repo)
    local_key = local_state.get("public_key")
    local_state_indeterminate = local_state.get("indeterminate") is True
    remote_snapshot = _read_remote_signing_snapshot(client)
    published_key = remote_snapshot["public_key"]
    public_key_exists = remote_snapshot["public_key_exists"]
    secret_exists = remote_snapshot["secret_exists"]
    published_fingerprint = _safe_signing_key_fingerprint(published_key)
    if public_key_exists and published_fingerprint is None:
        remote_state = "invalid_public_key"
    elif public_key_exists and secret_exists:
        remote_state = "present_unverified"
    elif public_key_exists:
        remote_state = "public_only"
    elif secret_exists:
        remote_state = "secret_only"
    else:
        remote_state = "absent"
    local_fingerprint = None
    if local_key:
        try:
            local_fingerprint = signing_key_fingerprint(local_key)
        except Exception:
            pass
    return {
        "verification_enabled": enabled,
        "remote_state": remote_state,
        "signing_key_configured": bool(
            published_fingerprint is not None and secret_exists
        ),
        "signing_ready": (
            False
            if local_state_indeterminate
            else (
                None
                if published_fingerprint is not None and secret_exists
                else False
            )
        ),
        "local_state_indeterminate": local_state_indeterminate,
        "public_key_fingerprint": published_fingerprint,
        "local_key_fingerprint": local_fingerprint,
        "remote_snapshot": _expected_remote_signing_snapshot(remote_snapshot),
    }


def resolve_verification_key(client):
    state = _get_signing_state(load_config(), client.repo)
    if state.get("indeterminate") is True:
        raise SigningStateIndeterminateError(
            "a previous signing-key operation left the repository state "
            "indeterminate; repair it with 'abk signing import', "
            "'abk signing rotate --yes', or 'abk signing disable --yes'"
        )
    if not signing_verification_enabled(client.repo):
        _assert_remote_signing_disabled(client)
        return None
    external_key = os.environ.get("ABK_SIGNING_KEY")
    if external_key:
        return normalize_signing_public_key(external_key)

    local_key = get_signing_key(client.repo)
    try:
        local_key = normalize_signing_public_key(local_key) if local_key else None
    except Exception:
        local_key = None

    try:
        published_key = client.get_published_signing_key()
    except Exception:
        if local_key:
            return local_key
        raise
    if published_key is None:
        return local_key

    published_key = normalize_signing_public_key(published_key)
    if not local_key or published_key.strip() != local_key.strip():
        config = load_config()
        _save_signing_state(config, client.repo, published_key)
    return published_key


def _verify_result(verified, status, message, **extra):
    result = {"verified": verified, "status": status, "message": message}
    result.update(extra)
    return result


def _safe_zip_member_name(name):
    if not name or "\\" in name or name.startswith("/"):
        return False
    parts = PurePosixPath(name).parts
    return bool(parts) and all(part not in ("", ".", "..") for part in parts)


def _stream_digest(stream, size_limit=MAX_PAYLOAD_SIZE):
    digest = hashlib.sha256()
    actual_size = 0
    while True:
        chunk = stream.read(1024 * 1024)
        if not chunk:
            break
        actual_size += len(chunk)
        if actual_size > size_limit:
            return None, actual_size
        digest.update(chunk)
    return digest.hexdigest(), actual_size


def verify_artifact_bundle(
    bundle_path,
    public_key_pem=None,
    expected_bundle_name=None,
    expected_run_id=None,
):
    """Verify one signed ABK bundle and fail closed on every missing field."""
    bundle_path = Path(bundle_path)
    if not bundle_path.name.lower().endswith('.bundle.zip'):
        return _verify_result(False, 'skip', t("artifact_verify_skip"))

    try:
        with zipfile.ZipFile(bundle_path, 'r') as archive:
            names = archive.namelist()
            manifest_name = 'ABK_BUNDLE_MANIFEST.json'
            signature_name = 'ABK_BUNDLE_MANIFEST.sig'
            if names.count(manifest_name) != 1 or names.count(signature_name) != 1:
                return _verify_result(False, 'unverified', t("artifact_unverified_warning"))

            manifest_info = archive.getinfo(manifest_name)
            signature_info = archive.getinfo(signature_name)
            if manifest_info.file_size > MAX_MANIFEST_SIZE or signature_info.file_size > MAX_SIGNATURE_SIZE:
                return _verify_result(False, 'unverified', t("artifact_unverified_warning"))

            manifest_bytes = archive.read(manifest_info)
            signature_bytes = archive.read(signature_info)
            try:
                manifest = json.loads(manifest_bytes.decode("utf-8"))
            except (UnicodeError, json.JSONDecodeError):
                return _verify_result(False, 'unverified', t("artifact_unverified_warning"))
            if not isinstance(manifest, dict):
                return _verify_result(False, 'unverified', t("artifact_unverified_warning"))

            if not public_key_pem or not _CRYPTO_BACKEND:
                return _verify_result(False, 'no_key', t("artifact_verify_no_key"))

            try:
                if _CRYPTO_BACKEND == 'cryptography':
                    pub = serialization.load_pem_public_key(public_key_pem.encode("ascii"))
                    pub.verify(
                        signature_bytes,
                        manifest_bytes,
                        padding.PKCS1v15(),
                        hashes.SHA256(),
                    )
                else:
                    pub = RSA.import_key(public_key_pem)
                    digest = SHA256.new(manifest_bytes)
                    pkcs1_15.new(pub).verify(digest, signature_bytes)
            except Exception:
                return _verify_result(False, 'unverified', t("artifact_unverified_warning"))

            payload_name = manifest.get('payload_name')
            expected_sha256 = manifest.get('payload_sha256')
            payload_size = manifest.get('payload_size_bytes')
            manifest_bundle_name = manifest.get('bundle_name')
            run_id = manifest.get('run_id')
            artifact_type = manifest.get('artifact_type')
            bundle_name = expected_bundle_name or bundle_path.name

            required_fields_valid = (
                manifest.get('schema') == 1
                and isinstance(manifest_bundle_name, str)
                and manifest_bundle_name == bundle_name
                and isinstance(artifact_type, str)
                and artifact_type in {'KERNEL_IMG', 'ANYKERNEL3', 'OTHER'}
                and isinstance(run_id, int)
                and not isinstance(run_id, bool)
                and isinstance(payload_name, str)
                and _safe_zip_member_name(payload_name)
                and payload_name not in {manifest_name, signature_name}
                and isinstance(expected_sha256, str)
                and re.fullmatch(r'[0-9a-fA-F]{64}', expected_sha256.strip()) is not None
                and isinstance(payload_size, int)
                and not isinstance(payload_size, bool)
                and 0 <= payload_size <= MAX_PAYLOAD_SIZE
            )
            if not required_fields_valid:
                return _verify_result(False, 'unverified', t("artifact_unverified_warning"))
            if expected_run_id is not None and run_id != int(expected_run_id):
                return _verify_result(False, 'unverified', t("artifact_unverified_warning"))
            if names.count(payload_name) != 1:
                return _verify_result(False, 'unverified', t("artifact_unverified_warning"))

            payload_info = archive.getinfo(payload_name)
            if payload_info.is_dir() or payload_info.file_size != payload_size:
                return _verify_result(False, 'unverified', t("artifact_unverified_warning"))

            with archive.open(payload_info, 'r') as payload_stream:
                actual_digest, actual_size = _stream_digest(payload_stream)
            if actual_size != payload_size or not hmac.compare_digest(
                actual_digest or "", expected_sha256.strip().lower()
            ):
                return _verify_result(False, 'unverified', t("artifact_unverified_warning"))

            return _verify_result(
                True,
                'verified',
                t("artifact_verified_ok"),
                manifest=manifest,
            )
    except zipfile.BadZipFile:
        return _verify_result(False, 'error', 'Invalid zip file')
    except Exception as exc:
        return _verify_result(False, 'error', f'Verification error: {exc}')


def verify_artifact_archive(archive_path, public_key_pem=None, expected_run_id=None):
    """Verify signed bundles inside a GitHub Actions artifact archive."""
    archive_path = Path(archive_path)
    try:
        with zipfile.ZipFile(archive_path, 'r') as outer:
            names = outer.namelist()
            if 'ABK_BUNDLE_MANIFEST.json' in names:
                direct = verify_artifact_bundle(
                    archive_path,
                    public_key_pem,
                    expected_run_id=expected_run_id,
                )
                return _verify_result(
                    direct['verified'], direct['status'], direct['message'], bundles=[direct]
                )

            bundle_infos = [
                info for info in outer.infolist()
                if not info.is_dir() and info.filename.lower().endswith('.bundle.zip')
            ]
            if not bundle_infos:
                return _verify_result(False, 'skip', t("artifact_verify_skip"), bundles=[])

            results = []
            seen_names = set()
            for info in bundle_infos:
                if (
                    not _safe_zip_member_name(info.filename)
                    or info.filename in seen_names
                    or info.file_size > MAX_PAYLOAD_SIZE
                ):
                    results.append(_verify_result(
                        False, 'unverified', t("artifact_unverified_warning"),
                        bundle=info.filename,
                    ))
                    continue
                seen_names.add(info.filename)

                fd, temp_name = tempfile.mkstemp(suffix='.bundle.zip')
                try:
                    with os.fdopen(fd, 'wb') as output, outer.open(info, 'r') as source:
                        fd = None
                        shutil.copyfileobj(source, output, length=1024 * 1024)
                    result = verify_artifact_bundle(
                        temp_name,
                        public_key_pem,
                        expected_bundle_name=PurePosixPath(info.filename).name,
                        expected_run_id=expected_run_id,
                    )
                    result['bundle'] = info.filename
                    results.append(result)
                finally:
                    if fd is not None:
                        os.close(fd)
                    try:
                        Path(temp_name).unlink()
                    except FileNotFoundError:
                        pass

            verified = bool(results) and all(item['verified'] for item in results)
            if verified:
                return _verify_result(
                    True, 'verified', t("artifact_verified_ok"), bundles=results
                )
            first_failure = next(item for item in results if not item['verified'])
            return _verify_result(
                False, first_failure['status'], first_failure['message'], bundles=results
            )
    except zipfile.BadZipFile:
        return _verify_result(False, 'error', 'Invalid artifact archive', bundles=[])
    except Exception as exc:
        return _verify_result(
            False, 'error', f'Artifact verification error: {exc}', bundles=[]
        )


def _target_repo(args):
    """Return the explicit repository selected by CLI option or environment."""
    return getattr(args, "repo", None) or os.environ.get("ABK_REPO")


def _repo_is_explicit(client, args):
    explicit = getattr(client, "repo_explicit", None)
    if isinstance(explicit, bool):
        return explicit
    return bool(_target_repo(args))


def make_client(args, token):
    return GitHubClient(
        token=token,
        repo=_target_repo(args),
        verbose=getattr(args, "verbose", False),
    )


def _json_mode(args):
    return bool(getattr(args, "json", False))


def _set_json_result(args, **payload):
    if _json_mode(args):
        args._json_result = payload


def _set_json_error(args, message, error_code="operation_failed", **payload):
    _set_json_result(
        args,
        ok=False,
        error=str(message),
        errorCode=error_code,
        **payload,
    )


def _set_build_error(args, message, error_code, *, repo=None, stage=None, warnings=None):
    payload = {
        "repo": repo,
        "dryRun": bool(getattr(args, "dry_run", False)),
        "total": 0,
        "run": None,
        "runs": [],
        "dispatches": [],
        "warnings": list(warnings or []),
    }
    if stage is not None:
        payload["stage"] = stage
    _set_json_error(args, message, error_code, **payload)


def _authentication_error_code(error, default="authentication_failed"):
    if isinstance(error, GitHubAPIError) and error.status_code == 401:
        return "not_authenticated"
    return default


def _is_installation_access_token(token):
    # GitHub documents ghs_ as the installation-access-token prefix. Keep the
    # endpoint fallback below for older or nonstandard credentials, but avoid
    # an unsupported GET /user request for the normal installation-token path.
    return isinstance(token, str) and token.startswith("ghs_")


def _report_client_authentication_error(client, args, **payload):
    error = getattr(client, "authentication_error", None)
    if not isinstance(error, BaseException):
        return False
    secrets = _collect_json_secrets(args)
    client_token = getattr(client, "token", None)
    if isinstance(client_token, str) and client_token:
        secrets.add(client_token)
    redacted_error = _redact_secret_text(
        str(error),
        secrets,
    )
    message = t("login_verify_failed", error=redacted_error)
    print(message, file=sys.stderr)
    error_code = _authentication_error_code(error)
    _set_json_error(args, redacted_error, error_code, **payload)
    return True


def _normalize_run(run):
    return {
        "id": run.get("id"),
        "name": run.get("name") or "",
        "displayTitle": run.get("display_title") or run.get("name") or "",
        "status": run.get("status") or "",
        "conclusion": run.get("conclusion"),
        "event": run.get("event"),
        "headBranch": run.get("head_branch"),
        "htmlUrl": run.get("html_url"),
        "createdAt": run.get("created_at"),
        "updatedAt": run.get("updated_at"),
        "runNumber": run.get("run_number") or 0,
    }


def _normalize_artifact(artifact):
    return {
        "id": artifact.get("id"),
        "name": artifact.get("name") or "",
        "sizeBytes": artifact.get("size_in_bytes") or 0,
        "expired": bool(artifact.get("expired", False)),
        "archiveDownloadUrl": artifact.get("archive_download_url"),
    }


def _signing_key_metadata(repo, client=None):
    if repo and not signing_verification_enabled(repo):
        return False, None
    candidates = (
        ("environment", os.environ.get("ABK_SIGNING_KEY")),
        ("config", get_signing_key(repo) if repo else None),
    )
    for source, candidate in candidates:
        if not candidate:
            continue
        try:
            normalize_signing_public_key(candidate)
            return True, source
        except Exception:
            pass
    if client is not None:
        try:
            published = client.get_published_signing_key()
            if published:
                normalize_signing_public_key(published)
                return True, "repository"
        except Exception:
            pass
    return False, None


def _dispatch_run_details(response):
    response = response if isinstance(response, dict) else {}
    run_id = (
        response.get("workflow_run_id")
        or response.get("run_id")
        or response.get("id")
    )
    run_url = (
        response.get("workflow_run_url")
        or response.get("run_url")
        or response.get("url")
    )
    html_url = response.get("workflow_run_html_url") or response.get("html_url")
    return run_id, run_url, html_url


def prepare_build_repository(client, args):
    """Select a writable target repository and configure artifact signing."""
    if _repo_is_explicit(client, args):
        try:
            ensure_signing_key(client)
            return True
        except Exception as exc:
            message = _redact_secret_text(str(exc), _collect_json_secrets(args))
            print(t("err_fork_failed", error=message), file=sys.stderr)
            _set_build_error(
                args,
                message,
                "repository_setup_failed",
                repo=client.repo,
                stage="prepare_repository",
            )
            return False

    try:
        fork = client.get_fork()
        if not fork:
            print(t("fork_no_detect_creating"))
            fork = client.create_fork()
            full_name = fork.get("full_name") or (
                f"{client.username}/{SOURCE_REPO_NAME}" if client.username else None
            )
            if not full_name:
                raise RuntimeError("GitHub did not return the new fork name")
            client.repo = full_name
            client.fork_repo = fork
            wait_for_repo = getattr(client, "wait_for_repo_ready", None)
            if callable(wait_for_repo):
                fork = wait_for_repo(full_name)
            print(t("fork_created_generic"))
        else:
            client.repo = fork.get("full_name", client.repo)
            client.fork_repo = fork
            behind = client.check_behind()
            if behind.get("error"):
                raise RuntimeError(behind["error"])
            if behind.get("behind_by", 0) > 0:
                print(t("warn_behind_upstream", n=behind['behind_by']))
                if not args.force:
                    if _json_mode(args):
                        raise RuntimeError(
                            "fork is behind upstream; sync it before building"
                        )
                    sync = input(t("ask_sync")).strip().lower()
                    if sync in ('y', 'yes'):
                        client.sync_fork()
                        print(t("fork_sync_done"))

        ensure_signing_key(client)
        return True
    except Exception as exc:
        message = _redact_secret_text(str(exc), _collect_json_secrets(args))
        print(t("err_fork_failed", error=message), file=sys.stderr)
        _set_build_error(
            args,
            message,
            "repository_setup_failed",
            repo=client.repo,
            stage="prepare_repository",
        )
        return False


def print_workflow_run(run):
    conclusion = run.get("conclusion")
    status = run.get("status", "unknown")
    status_icon = (
        "✓" if conclusion == "success"
        else "✗" if conclusion == "failure"
        else "…" if status == "in_progress"
        else "○"
    )
    created = str(run.get("created_at", ""))[:19].replace("T", " ")
    print(f"  {status_icon} #{run.get('id', '?')} | {run.get('name', '')} | {status} | {created}")


def cmd_login(args):
    if _json_mode(args):
        message = "login requires the interactive device flow"
        print(message, file=sys.stderr)
        _set_json_error(args, message, "interaction_required")
        return 1

    token = device_flow_login()
    if not token:
        return 1

    client = make_client(args, token)
    if _report_client_authentication_error(client, args):
        return 1
    try:
        user = client.get_user()
        with _config_process_lock():
            config = load_config()
            config["token"] = token
            save_config(config)
        print()
        print(t("token_saved_to", path=CONFIG_FILE))
        print(t("logged_in_as", user=user.get('login', 'Unknown')))
        print(t("checking_fork"))
        fork_status = client.check_and_prompt_sync()
        if fork_status and fork_status.get("error"):
            raise RuntimeError(fork_status["error"])

        if fork_status and fork_status.get("needs_fork"):
            create = input(t("ask_create_fork")).strip().lower()
            if create in ('y', 'yes'):
                fork = client.create_fork()
                full_name = fork.get("full_name") or f"{client.username}/{SOURCE_REPO_NAME}"
                client.repo = full_name
                client.fork_repo = fork
                wait_for_repo = getattr(client, "wait_for_repo_ready", None)
                if callable(wait_for_repo):
                    wait_for_repo(full_name)
                _ensure_signing_key_for_repository_setup(client)
                print(t("fork_created_generic"))
        elif fork_status and fork_status.get("needs_sync"):
            print(t("fork_behind_upstream", n=fork_status['behind_by']))
            sync = input(t("ask_sync")).strip().lower()
            if sync in ('y', 'yes'):
                client.sync_fork()
                print(t("fork_sync_done"))
            _ensure_signing_key_for_repository_setup(client)
        elif fork_status and not fork_status.get("needs_fork"):
            print(t("fork_up_to_date"))
            _ensure_signing_key_for_repository_setup(client)
        return 0
    except Exception as exc:
        print(t("login_check_failed", error=exc), file=sys.stderr)
        return 1


def cmd_logout(args):
    if not CONFIG_FILE.exists():
        print(t("logout_not"))
        _set_json_result(
            args,
            ok=True,
            loggedIn=bool(get_token(args)),
            storedTokenRemoved=False,
        )
        return 0

    with _config_process_lock():
        if CONFIG_FILE.exists():
            config = load_config()
            if "token" in config:
                del config["token"]
                save_config(config)
                removed = True
            else:
                removed = False
        else:
            removed = False
    if removed:
        print(t("logged_out_token_removed"))
    else:
        print(t("logout_not"))
    _set_json_result(
        args,
        ok=True,
        loggedIn=bool(get_token(args)),
        storedTokenRemoved=removed,
    )
    return 0


def cmd_whoami(args):
    token = get_token(args)

    if not token:
        if _json_mode(args):
            repo = _target_repo(args) or DEFAULT_REPO
            configured_dir = load_config().get("download_dir")
            _set_json_result(
                args,
                ok=True,
                loggedIn=False,
                repo=repo,
                needsFork=False,
                needsSync=False,
                behindBy=0,
                aheadBy=0,
                user=None,
                fork=None,
                signingKeyAvailable=False,
                signingKeySource=None,
                signingVerificationEnabled=signing_verification_enabled(repo),
                downloadDir=configured_dir or str(default_download_dir()),
            )
            return 0
        print(t("logout_not"))
        print(t("run_login_hint"))
        return 1

    client = make_client(args, token)
    if _report_client_authentication_error(client, args):
        return 1
    try:
        explicit_repo = _repo_is_explicit(client, args)
        if explicit_repo:
            # Repository-scoped credentials, including GitHub App installation
            # tokens, can access a repository without representing a user.
            # Validate repository access first, then preserve user identity for
            # OAuth/PAT credentials while tolerating /user for installation
            # tokens only after the credential has already proved usable.
            client.get(f"/repos/{client.repo}")
            if _is_installation_access_token(token):
                user = None
            else:
                try:
                    user = client.get_user()
                except GitHubAPIError as exc:
                    if exc.status_code != 403:
                        raise
                    user = None
        else:
            user = client.get_user()

        if user is not None:
            print(t("status_user", user=user.get('login', 'Unknown')))

        fork = None if explicit_repo else client.get_fork()
        behind = {"behind_by": 0, "ahead_by": 0}
        if explicit_repo:
            print(f"Repository: {client.repo}")
        elif fork:
            print(f"Fork: {fork.get('full_name')}")

            behind = client.check_behind()
            if behind.get("error"):
                print(behind["error"], file=sys.stderr)
                _set_json_error(args, behind["error"], "fork_status_failed")
                return 1
            elif behind.get("behind_by", 0) > 0:
                print(t("status_behind", n=behind['behind_by']))
            else:
                print(t("status_synced"))
        elif not explicit_repo:
            print(t("fork_not_detected"))
            print(t("hint_run_fork"))
        if _json_mode(args):
            repo = client.repo if explicit_repo else (
                fork.get("full_name") if fork else client.repo
            )
            if explicit_repo:
                key_available, key_source = _signing_key_metadata(repo, client)
            elif fork:
                key_available, key_source = _signing_key_metadata(repo, client)
            else:
                key_available, key_source = False, None
            configured_dir = load_config().get("download_dir")
            _set_json_result(
                args,
                ok=True,
                loggedIn=True,
                repo=repo,
                needsFork=False if explicit_repo else not bool(fork),
                needsSync=(
                    False
                    if explicit_repo
                    else bool(behind.get("behind_by", 0) > 0)
                ),
                behindBy=behind.get("behind_by", 0),
                aheadBy=behind.get("ahead_by", 0),
                user=(
                    {"login": user.get("login", "")}
                    if user is not None
                    else None
                ),
                fork=(
                    {"fullName": fork.get("full_name", "")}
                    if fork and not explicit_repo
                    else None
                ),
                signingKeyAvailable=key_available,
                signingKeySource=key_source,
                signingVerificationEnabled=signing_verification_enabled(repo),
                downloadDir=configured_dir or str(default_download_dir()),
            )
        return 0
    except Exception as exc:
        print(t("login_verify_failed", error=exc), file=sys.stderr)
        _set_json_error(
            args,
            exc,
            _authentication_error_code(
                exc,
                default="login_verification_failed",
            ),
        )
        return 1


def cmd_fork(args):
    token = get_token(args)
    
    if not token:
        print(t("err_no_token"), file=sys.stderr)
        _set_json_error(args, t("err_no_token"), "not_authenticated")
        return 1
    
    client = make_client(args, token)
    if _report_client_authentication_error(client, args):
        return 1
    
    try:
        created = False
        sync_requested = False
        if _repo_is_explicit(client, args):
            print(t("fork_exists", fork=client.repo))
            signing_warning = _ensure_signing_key_for_repository_setup(client)
            _set_json_result(
                args,
                ok=True,
                created=False,
                syncRequested=False,
                repo=client.repo,
                fork={"fullName": client.repo},
                warnings=[signing_warning] if signing_warning else [],
                signingStateIndeterminate=bool(signing_warning),
            )
            return 0

        fork = client.get_fork()
        if fork:
            client.repo = fork.get("full_name", client.repo)
            client.fork_repo = fork
            print(t("fork_exists", fork=fork.get('full_name')))
            
            behind = client.check_behind()
            if behind.get("error"):
                raise RuntimeError(behind["error"])
            if behind.get("behind_by", 0) > 0:
                print(t("fork_behind", n=behind['behind_by']))
                if not args.no_sync:
                    print(t("fork_syncing"))
                    client.sync_fork()
                    sync_requested = True
                    print(t("fork_sync_done"))
            else:
                print(t("fork_already_latest"))
        else:
            print(t("fork_creating"))
            result = client.create_fork()
            full_name = result.get("full_name") or f"{client.username}/{SOURCE_REPO_NAME}"
            client.repo = full_name
            client.fork_repo = result
            wait_for_repo = getattr(client, "wait_for_repo_ready", None)
            if callable(wait_for_repo):
                wait_for_repo(full_name)
            print(t("fork_created", fork=result.get('full_name')))
            created = True
        signing_warning = _ensure_signing_key_for_repository_setup(client)
        _set_json_result(
            args,
            ok=True,
            created=created,
            syncRequested=sync_requested,
            repo=client.repo,
            fork={"fullName": client.repo},
            warnings=[signing_warning] if signing_warning else [],
            signingStateIndeterminate=bool(signing_warning),
        )
        return 0
    except Exception as exc:
        print(t("err_fork_failed", error=exc), file=sys.stderr)
        _set_json_error(args, exc, "fork_failed")
        return 1


def cmd_sync(args):
    token = get_token(args)
    
    if not token:
        print(t("err_no_token"), file=sys.stderr)
        _set_json_error(args, t("err_no_token"), "not_authenticated")
        return 1
    
    client = make_client(args, token)
    if _report_client_authentication_error(client, args):
        return 1
    
    try:
        fork = client.get_fork()
        if not fork:
            print(t("err_no_fork"), file=sys.stderr)
            _set_json_error(args, t("err_no_fork"), "fork_not_found")
            return 1
        client.repo = fork.get("full_name", client.repo)
        client.fork_repo = fork
        
        behind = client.check_behind()
        if behind.get("error"):
            raise RuntimeError(behind["error"])
        behind_before = behind.get("behind_by", 0)
        changed = behind_before > 0
        if not changed:
            print(t("fork_already_latest"))
        else:
            print(t("syncing_n_commits", n=behind_before))
            client.sync_fork()
            print(t("fork_sync_done"))
        signing_warning = _ensure_signing_key_for_repository_setup(client)
        _set_json_result(
            args,
            ok=True,
            changed=changed,
            behindByBefore=behind_before,
            repo=client.repo,
            fork={"fullName": client.repo},
            warnings=[signing_warning] if signing_warning else [],
            signingStateIndeterminate=bool(signing_warning),
        )
        return 0
    except Exception as exc:
        print(t("err_sync_failed", error=exc), file=sys.stderr)
        _set_json_error(args, exc, "sync_failed")
        return 1


def _select_signing_repository(client, args):
    if _repo_is_explicit(client, args):
        client.get(f"/repos/{client.repo}")
        return client.repo
    fork = client.get_fork()
    if not fork:
        raise RuntimeError(t("err_no_fork"))
    full_name = fork.get("full_name")
    if not full_name:
        raise RuntimeError("GitHub did not return the fork repository name")
    client.repo = full_name
    client.fork_repo = fork
    return full_name


def _confirm_signing_action(args, prompt):
    if getattr(args, "dry_run", False) or getattr(args, "yes", False):
        return True
    if _json_mode(args):
        _set_json_error(
            args,
            "this signing operation requires --yes",
            "confirmation_required",
            action=args.signing_action,
            repo=getattr(args, "_signing_repo", None),
            dryRun=False,
        )
        return False
    answer = input(prompt).strip().lower()
    return answer in {"y", "yes", "j", "ja", "o", "oui", "s", "si", "sí"}


def cmd_signing(args):
    action = args.signing_action
    public_file = getattr(args, "public_key_file", None)
    private_file = getattr(args, "private_key_file", None)
    try:
        if action == "import":
            if not public_file or not private_file:
                raise SigningArgumentsError(
                    "signing import requires --public-key-file and --private-key-file"
                )
        elif public_file or private_file:
            raise SigningArgumentsError("key files are only supported by signing import")
        if action == "status" and (args.yes or args.dry_run):
            raise SigningArgumentsError(
                "signing status does not support --yes or --dry-run"
            )
        if action == "enable" and args.yes:
            raise SigningArgumentsError("signing enable does not support --yes")
    except SigningArgumentsError as exc:
        message = _redact_secret_text(str(exc), _collect_json_secrets(args))
        print(message, file=sys.stderr)
        _set_json_error(
            args,
            message,
            "invalid_arguments",
            action=action,
            repo=None,
            dryRun=bool(args.dry_run),
        )
        return 2

    token = get_token(args)
    if not token:
        print(t("err_no_token"), file=sys.stderr)
        _set_json_error(
            args,
            t("err_no_token"),
            "not_authenticated",
            action=args.signing_action,
        )
        return 1

    client = make_client(args, token)
    if _report_client_authentication_error(
        client,
        args,
        action=args.signing_action,
    ):
        return 1

    try:
        repo = _select_signing_repository(client, args)
        args._signing_repo = repo

        status = get_signing_status(client)
        if action == "status":
            print(t("signing_status_repo", repo=repo))
            print(
                t(
                    "signing_status_verification",
                    status=(t("enabled") if status["verification_enabled"] else t("disabled")),
                )
            )
            print(
                t(
                    "signing_status_remote",
                    state=t(f"signing_state_{status['remote_state']}"),
                )
            )
            if status["local_state_indeterminate"]:
                print(t("signing_status_indeterminate"))
            if status["public_key_fingerprint"]:
                print(
                    t(
                        "signing_status_fingerprint",
                        fingerprint=status["public_key_fingerprint"],
                    )
                )
            _set_json_result(
                args,
                ok=True,
                action=action,
                repo=repo,
                dryRun=False,
                changed=False,
                verificationEnabled=status["verification_enabled"],
                signingKeyConfigured=status["signing_key_configured"],
                signingReady=status["signing_ready"],
                signingState=status["remote_state"],
                localStateIndeterminate=status["local_state_indeterminate"],
                publicKeyFingerprint=status["public_key_fingerprint"],
                localKeyFingerprint=status["local_key_fingerprint"],
            )
            return 0

        if action == "import":
            try:
                public_text = read_signing_key_file(public_file, "public")
                private_text = read_signing_key_file(private_file, "private")
                private_b64, public_pem, fingerprint = load_signing_keypair(
                    public_text,
                    private_text,
                )
                del private_text
            except ValueError as exc:
                raise SigningKeyInputError(str(exc)) from exc
        elif action == "rotate":
            if args.dry_run:
                private_b64 = public_pem = fingerprint = None
            else:
                private_b64, public_pem = generate_signing_keypair()
                fingerprint = signing_key_fingerprint(public_pem)
        else:
            private_b64 = public_pem = fingerprint = None

        if action in {"import", "rotate"}:
            previous = status["public_key_fingerprint"]
            public_key_changed = (
                True
                if action == "rotate" and args.dry_run
                else previous != fingerprint
            )
            changed = (
                public_key_changed
                or not status["signing_key_configured"]
                or not status["verification_enabled"]
            )
            if (
                public_key_changed
                and status["remote_state"] != "absent"
                and not _confirm_signing_action(args, t("signing_confirm_rotate"))
            ):
                if not _json_mode(args):
                    print(t("signing_cancelled"))
                return 1
            if args.dry_run:
                _set_json_result(
                    args,
                    ok=True,
                    action=action,
                    repo=repo,
                    dryRun=True,
                    changed=changed,
                    verificationEnabled=True,
                    signingKeyConfigured=True,
                    signingReady=True,
                    publicKeyFingerprint=fingerprint,
                    previousPublicKeyFingerprint=previous,
                    invalidatedPreviousBundles=bool(public_key_changed and previous),
                    willGenerateKey=action == "rotate",
                )
                print(t("signing_dry_run", action=action, repo=repo))
                return 0
            result = install_signing_keypair(
                client,
                private_b64,
                public_pem,
                expected_remote_snapshot=status["remote_snapshot"],
            )
            print(
                t(
                    "signing_imported" if action == "import" else "signing_rotated",
                    repo=repo,
                )
            )
            _set_json_result(
                args,
                ok=True,
                action=action,
                repo=repo,
                dryRun=False,
                changed=result["changed"],
                verificationEnabled=True,
                signingKeyConfigured=True,
                signingReady=True,
                publicKeyFingerprint=result["fingerprint"],
                previousPublicKeyFingerprint=result["previous_fingerprint"],
                invalidatedPreviousBundles=bool(
                    result["public_key_changed"]
                    and result["previous_fingerprint"]
                ),
            )
            return 0

        if action == "enable":
            if (
                not status["signing_key_configured"]
                and status["remote_state"] != "absent"
            ):
                raise RuntimeError(
                    "signing enable cannot repair a partial remote signing state; "
                    "use signing import or signing rotate --yes"
                )
            enable_changed = (
                not status["verification_enabled"]
                or not status["signing_key_configured"]
            )
            if args.dry_run:
                _set_json_result(
                    args,
                    ok=True,
                    action=action,
                    repo=repo,
                    dryRun=True,
                    changed=enable_changed,
                    verificationEnabled=True,
                    signingKeyConfigured=True,
                    signingReady=(
                        status["signing_ready"]
                        if status["signing_key_configured"]
                        else True
                    ),
                    publicKeyFingerprint=status["public_key_fingerprint"],
                    willGenerateKey=not status["signing_key_configured"],
                )
                print(t("signing_dry_run", action=action, repo=repo))
                return 0
            if status["signing_key_configured"]:
                public_pem = ensure_signing_key(client, force_enable=True)
                fingerprint = signing_key_fingerprint(public_pem)
            else:
                private_b64, public_pem = generate_signing_keypair()
                installed = install_signing_keypair(
                    client,
                    private_b64,
                    public_pem,
                    expected_remote_snapshot=status["remote_snapshot"],
                )
                fingerprint = installed["fingerprint"]
            print(t("signing_enabled", repo=repo))
            _set_json_result(
                args,
                ok=True,
                action=action,
                repo=repo,
                dryRun=False,
                changed=enable_changed,
                verificationEnabled=True,
                signingKeyConfigured=True,
                signingReady=(
                    status["signing_ready"]
                    if status["signing_key_configured"]
                    else True
                ),
                publicKeyFingerprint=fingerprint,
            )
            return 0

        if action == "disable":
            needs_change = status["verification_enabled"] or status["remote_state"] != "absent"
            if needs_change and not _confirm_signing_action(
                args,
                t("signing_confirm_disable", repo=repo),
            ):
                if not _json_mode(args):
                    print(t("signing_cancelled"))
                return 1
            if args.dry_run:
                _set_json_result(
                    args,
                    ok=True,
                    action=action,
                    repo=repo,
                    dryRun=True,
                    changed=needs_change,
                    verificationEnabled=False,
                    signingKeyConfigured=False,
                    signingReady=False,
                    publicKeyFingerprint=None,
                    previousPublicKeyFingerprint=status["public_key_fingerprint"],
                )
                print(t("signing_dry_run", action=action, repo=repo))
                return 0
            result = disable_signing_verification(
                client,
                expected_remote_snapshot=status["remote_snapshot"],
            )
            print(t("signing_disabled", repo=repo))
            _set_json_result(
                args,
                ok=True,
                action=action,
                repo=repo,
                dryRun=False,
                changed=result["changed"],
                verificationEnabled=False,
                signingKeyConfigured=False,
                signingReady=False,
                publicKeyFingerprint=None,
                previousPublicKeyFingerprint=result["previous_fingerprint"],
            )
            return 0

        raise SigningArgumentsError(f"unsupported signing action: {action}")
    except SigningStateIndeterminateError as exc:
        message = _redact_secret_text(str(exc), _collect_json_secrets(args))
        print(message, file=sys.stderr)
        _set_json_error(
            args,
            message,
            "signing_state_indeterminate",
            action=args.signing_action,
            repo=getattr(args, "_signing_repo", None),
            dryRun=bool(args.dry_run),
        )
        return 1
    except SigningKeyInputError as exc:
        message = _redact_secret_text(str(exc), _collect_json_secrets(args))
        print(message, file=sys.stderr)
        _set_json_error(
            args,
            message,
            "signing_key_invalid",
            action=args.signing_action,
            repo=getattr(args, "_signing_repo", None),
            dryRun=bool(args.dry_run),
        )
        return 2
    except SigningArgumentsError as exc:
        message = _redact_secret_text(str(exc), _collect_json_secrets(args))
        print(message, file=sys.stderr)
        _set_json_error(
            args,
            message,
            "invalid_arguments",
            action=args.signing_action,
            repo=getattr(args, "_signing_repo", None),
            dryRun=bool(args.dry_run),
        )
        return 2
    except Exception as exc:
        message = _redact_secret_text(str(exc), _collect_json_secrets(args))
        print(message, file=sys.stderr)
        _set_json_error(
            args,
            message,
            "signing_operation_failed",
            action=args.signing_action,
            repo=getattr(args, "_signing_repo", None),
            dryRun=bool(args.dry_run),
        )
        return 1


def cmd_status(args):
    token = get_token(args)
    
    if not token:
        print(t("err_no_token"), file=sys.stderr)
        _set_json_error(args, t("err_no_token"), "not_authenticated", run=None, runs=[], total=0)
        return 1
    
    client = make_client(args, token)
    if _report_client_authentication_error(
        client,
        args,
        run=None,
        runs=[],
        total=0,
    ):
        return 1

    if not _repo_is_explicit(client, args):
        try:
            fork = client.get_fork()
            if not fork:
                print(t("err_no_fork"), file=sys.stderr)
                _set_json_error(args, t("err_no_fork"), "fork_not_found", run=None, runs=[], total=0)
                return 1
            client.repo = fork.get("full_name", client.repo)
            client.fork_repo = fork
        except Exception as exc:
            print(t("fetch_status_failed", error=exc), file=sys.stderr)
            _set_json_error(args, exc, "status_failed", run=None, runs=[], total=0)
            return 1

    if args.cancel:
        try:
            client.cancel_run(args.cancel)
            print(t("cancel_ok", id=args.cancel))
            _set_json_result(args, ok=True, action="cancel", runId=args.cancel, repo=client.repo)
        except Exception as exc:
            print(t("cancel_fail", error=exc), file=sys.stderr)
            _set_json_error(args, exc, "cancel_failed", runId=args.cancel, repo=client.repo)
            return 1
        return 0

    if args.rerun:
        try:
            client.rerun(args.rerun)
            print(t("rerun_ok", id=args.rerun))
            _set_json_result(args, ok=True, action="rerun", runId=args.rerun, repo=client.repo)
        except Exception as exc:
            print(t("rerun_fail", error=exc), file=sys.stderr)
            _set_json_error(args, exc, "rerun_failed", runId=args.rerun, repo=client.repo)
            return 1
        return 0

    try:
        if not _repo_is_explicit(client, args):
            behind = client.check_behind()
            if behind.get("error"):
                print(behind["error"], file=sys.stderr)
            elif behind.get("behind_by", 0) > 0:
                print(t("warn_behind_upstream", n=behind['behind_by']))
                print(t("run_abk_sync"))
                print()

        if args.run_id:
            run = client.get_run(args.run_id)
            print_workflow_run(run)
            _set_json_result(
                args,
                ok=True,
                repo=client.repo,
                total=1,
                run=_normalize_run(run),
                runs=[],
            )
            return 0

        if args.target in WORKFLOWS:
            workflow_file = WORKFLOWS[args.target]["file"]
        elif args.target in FULL_MATRIX_WORKFLOWS:
            workflow_file = FULL_MATRIX_WORKFLOWS[args.target]
        else:
            workflow_file = None
        status_filter = None if args.status == "all" else args.status
        runs = client.list_runs(
            workflow_file=workflow_file,
            status=status_filter,
            per_page=args.limit,
        )
        workflow_runs = runs.get("workflow_runs", [])
        
        if not workflow_runs:
            print(t("status_no_builds"))
            _set_json_result(
                args,
                ok=True,
                repo=client.repo,
                total=0,
                run=None,
                runs=[],
            )
            return 0
        
        print(t("status_recent", n=len(workflow_runs)))
        for run in workflow_runs:
            print_workflow_run(run)
        normalized_runs = [_normalize_run(run) for run in workflow_runs]
        _set_json_result(
            args,
            ok=True,
            repo=client.repo,
            total=len(normalized_runs),
            run=None,
            runs=normalized_runs,
        )
        return 0
    except Exception as exc:
        print(t("fetch_status_failed", error=exc), file=sys.stderr)
        _set_json_error(args, exc, "status_failed", run=None, runs=[], total=0)
        return 1


def _set_build_defaults(args):
    full_mode = args.matrix in ("full", "all-managers")
    all_managers = args.matrix == "all-managers"
    defaults = {
        "zram": full_mode,
        "bbg": full_mode,
        "ddk": full_mode,
        "kpm": full_mode,
        "susfs": True,
        "rekernel": full_mode,
        "oneplus_8e": full_mode,
        "ntsync": full_mode,
        "networking": full_mode,
        "zram_full_algo": full_mode,
        "lz4kd": all_managers,
        "bbr": all_managers,
        "proxy_optimization": all_managers,
        "unicode_bypass": all_managers,
    }
    for name, default in defaults.items():
        if getattr(args, name, None) is None:
            setattr(args, name, default)
    if getattr(args, "virt", None) is None:
        args.virt = "on" if all_managers else "off"


def normalize_virtualization_support(kernel_version, value):
    """Map the common CLI option to the selected workflow's input contract."""
    value = value or "off"
    if value == "off":
        return "off"
    if kernel_version == "6.12":
        return "on"
    if value == "on":
        return "678"
    return value


def _susfs_enabled(args, variant):
    """Return the effective SUSFS state after Android's None normalization."""
    return bool(args.susfs) and variant != "None"


def _standard_build_inputs(
    args,
    variant,
    kernel_version="5.10",
    *,
    supports_supp_op=False,
):
    kpm_enabled = bool(args.kpm) and supports_kpm(variant, args.ksu_branch)
    susfs_enabled = _susfs_enabled(args, variant)
    virtualization_support = normalize_virtualization_support(
        kernel_version,
        args.virt,
    )
    inputs = {
        "kernelsu_variant": variant,
        "kernelsu_branch": resolve_plan_ksu_branch(variant, args.ksu_branch),
        "use_zram": str(bool(args.zram)).lower(),
        "use_bbg": str(bool(args.bbg)).lower(),
        "use_ddk": str(bool(args.ddk)).lower(),
        "use_kpm": str(kpm_enabled).lower(),
        "use_rekernel": str(bool(args.rekernel)).lower(),
        "cancel_susfs": str(not susfs_enabled).lower(),
        "use_ntsync": str(bool(args.ntsync)).lower(),
        "use_networking": str(bool(args.networking)).lower(),
        "zram_full_algo": str(bool(args.zram_full_algo)).lower(),
    }
    if virtualization_support != "off":
        inputs["virtualization_support"] = virtualization_support
    if supports_supp_op:
        inputs["supp_op"] = str(bool(args.oneplus_8e)).lower()
    if args.version:
        inputs["version"] = args.version
    if args.custom_ref and variant != "None":
        inputs["custom_ref"] = args.custom_ref
    if args.kpm_password and kpm_enabled:
        inputs["kpm_password"] = args.kpm_password
    if args.zram_extra_algos:
        inputs["zram_extra_algos"] = args.zram_extra_algos
    if args.build_time:
        inputs["build_time"] = args.build_time
    if args.custom_modules:
        inputs["custom_external_modules"] = args.custom_modules
    return inputs


def _full_matrix_inputs(args, variant):
    kpm_enabled = bool(args.kpm) and supports_kpm(variant, args.ksu_branch)
    return {
        "kernelsu_variant": variant,
        "kernelsu_branch": resolve_plan_ksu_branch(variant, args.ksu_branch),
        "version": args.version or "",
        "revision": args.revision or "r11",
        "build_time": args.build_time or "",
        "kpm_password": args.kpm_password if kpm_enabled and args.kpm_password else "",
        "enable_susfs": str(_susfs_enabled(args, variant)).lower(),
        "use_zram": str(bool(args.zram)).lower(),
        "use_bbg": str(bool(args.bbg)).lower(),
        "use_ddk": str(bool(args.ddk)).lower(),
        "use_kpm": str(kpm_enabled).lower(),
        "use_rekernel": str(bool(args.rekernel)).lower(),
        "use_ntsync": str(bool(args.ntsync)).lower(),
        "use_networking": str(bool(args.networking)).lower(),
        "supp_op": str(bool(args.oneplus_8e)).lower(),
        "virtualization_support": args.virt,
        "zram_full_algo": str(bool(args.zram_full_algo)).lower(),
        "zram_extra_algos": args.zram_extra_algos or "",
        "custom_external_modules": args.custom_modules or "",
    }


def _all_managers_inputs(args):
    manager_variants = selected_manager_variants(args.manager_variants)
    build_scope = args.build_scope or "Both"
    selected_gki_supports_kpm = False
    if build_scope in ("Both", "GKI"):
        selected_gki_supports_kpm = any(
            supports_kpm(variant, args.ksu_branch)
            for variant in manager_variants
        )
    include_kpm_password = bool(args.kpm) and selected_gki_supports_kpm
    oneplus_kpm_enabled = bool(args.kpm)
    oneplus_options = {
        "enable_susfs": bool(args.susfs),
        "use_kpm": oneplus_kpm_enabled,
        "use_lz4kd": bool(args.lz4kd),
        "use_bbg": bool(args.bbg),
        "use_bbr": bool(args.bbr),
        "use_proxy_optimization": bool(args.proxy_optimization),
        "use_unicode_bypass": bool(args.unicode_bypass),
    }
    return {
        "build_scope": build_scope,
        "manager_variants": args.manager_variants or "all",
        "kernelsu_branch": resolve_ksu_branch(args.ksu_branch),
        "version": args.version or "",
        "revision": args.revision or "r11",
        "build_time": args.build_time or "",
        "kpm_password": (
            args.kpm_password
            if include_kpm_password and args.kpm_password
            else ""
        ),
        "enable_susfs": str(bool(args.susfs)).lower(),
        "use_zram": str(bool(args.zram)).lower(),
        "use_bbg": str(bool(args.bbg)).lower(),
        "use_ddk": str(bool(args.ddk)).lower(),
        "use_kpm": str(bool(args.kpm)).lower(),
        "use_rekernel": str(bool(args.rekernel)).lower(),
        "use_ntsync": str(bool(args.ntsync)).lower(),
        "use_networking": str(bool(args.networking)).lower(),
        "supp_op": str(bool(args.oneplus_8e)).lower(),
        "virtualization_support": args.virt,
        "zram_full_algo": str(bool(args.zram_full_algo)).lower(),
        "zram_extra_algos": args.zram_extra_algos or "",
        "custom_external_modules": args.custom_modules or "",
        "oneplus_options_json": json.dumps(oneplus_options, separators=(",", ":")),
    }


def _redacted_inputs(inputs):
    # Build the public representation field by field so the password value is
    # never copied into an object that may be rendered in logs or JSON.
    result = {}
    for name, value in inputs.items():
        if name == "kpm_password":
            result[name] = "***" if value else ""
            continue
        result[name] = value
    return result


def _build_plan_id(plan, ref):
    canonical = json.dumps(
        {
            "workflow": plan["workflow"],
            "ref": ref,
            "inputs": _redacted_inputs(plan["inputs"]),
        },
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    )
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()[:16]


def cmd_build(args):
    warning_messages = []
    if args.matrix and args.oneplus:
        message = "--matrix and --oneplus are mutually exclusive"
        print(message, file=sys.stderr)
        _set_build_error(args, message, "invalid_arguments")
        return 2

    effective_ksu_branch = resolve_ksu_branch(args.ksu_branch)
    if not args.oneplus:
        if args.matrix in ("full", "all-managers") and (
            args.custom_ref
            or (
                effective_ksu_branch == "Custom(自定义)"
                and not (
                    args.matrix == "full"
                    and args.ksu_variant == "None"
                )
            )
        ):
            message = t("err_custom_ref_unsupported_matrix", matrix=args.matrix)
            print(message, file=sys.stderr)
            _set_build_error(args, message, "invalid_arguments")
            return 2
        if args.custom_ref and effective_ksu_branch != "Custom(自定义)":
            message = t("err_custom_ref_requires_custom_branch")
            print(message, file=sys.stderr)
            _set_build_error(args, message, "invalid_arguments")
            return 2
        if (
            effective_ksu_branch == "Custom(自定义)"
            and args.ksu_variant != "None"
            and not args.custom_ref
        ):
            message = t("err_custom_branch_requires_ref")
            print(message, file=sys.stderr)
            _set_build_error(args, message, "invalid_arguments")
            return 2

    _set_build_defaults(args)
    if args.revision:
        args.revision = args.revision.lower()
    if args.sub_level and args.sub_level.lower() == "x":
        args.sub_level = "X"
    if args.os_patch_level and args.os_patch_level.lower() == "lts":
        args.os_patch_level = "lts"

    if args.revision:
        revision_supported = args.matrix in ("full", "all-managers") or (
            not args.matrix
            and not args.oneplus
            and (args.kernel_version or "5.10") == "5.10"
        )
        if not revision_supported:
            message = (
                "--revision is supported only by custom 5.10, full, "
                "and all-managers builds"
            )
            print(message, file=sys.stderr)
            _set_build_error(args, message, "invalid_arguments")
            return 2

    if not args.matrix and not args.oneplus:
        selected_line = (
            args.android_version or "android12",
            args.kernel_version or "5.10",
        )
        supported_lines = {
            (workflow["android"], workflow["kernel"])
            for workflow in WORKFLOWS.values()
            if "android" in workflow and "kernel" in workflow
        }
        if selected_line not in supported_lines:
            message = (
                "unsupported Android/kernel combination: "
                f"{selected_line[0]}/{selected_line[1]}"
            )
            print(message, file=sys.stderr)
            _set_build_error(args, message, "invalid_arguments")
            return 2
        if (args.sub_level == "X") != (args.os_patch_level == "lts"):
            message = "--sub-level X and --os-patch-level lts must be used together"
            print(message, file=sys.stderr)
            _set_build_error(args, message, "invalid_arguments")
            return 2

    device_info = None
    if args.oneplus:
        if not args.device:
            message = t("err_need_device")
            print(message, file=sys.stderr)
            _set_build_error(args, message, "invalid_arguments")
            return 2
        device_info = ONEPLUS_DEVICES.get(args.device)
        if not device_info:
            message = t("err_unknown_device", device=args.device)
            print(message, file=sys.stderr)
            print(
                t("err_available_devices", devices=", ".join(ONEPLUS_DEVICES.keys())),
                file=sys.stderr,
            )
            _set_build_error(args, message, "invalid_arguments")
            return 2
        errors, warnings = validate_oneplus_build(args, device_info)
        for warning in warnings:
            warning_messages.append(warning)
            print(t("warning_prefix") + " " + warning)
        if errors:
            for error in errors:
                print(t("error_prefix") + " " + error, file=sys.stderr)
            _set_build_error(
                args,
                errors[0],
                "invalid_arguments",
                warnings=warning_messages,
            )
            return 2
    elif not args.matrix:
        if not args.sub_level:
            message = t("err_need_sub_level")
            print(message, file=sys.stderr)
            _set_build_error(args, message, "invalid_arguments")
            return 2
        if not args.os_patch_level:
            message = t("err_need_os_patch")
            print(message, file=sys.stderr)
            _set_build_error(args, message, "invalid_arguments")
            return 2

    invalid_argument = invalid_build_argument(args)
    if invalid_argument:
        message = t("err_invalid_build_arg", name=invalid_argument)
        print(message, file=sys.stderr)
        _set_build_error(args, message, "invalid_arguments")
        return 2

    plans = []
    if args.matrix == "full":
        variants = (
            KSU_ALL_VARIANTS
            if args.ksu_variant == "all"
            else [args.ksu_variant or "ReSukiSU"]
        )
        for variant in variants:
            if args.kpm and not supports_kpm(variant, args.ksu_branch):
                selection = (
                    f"{variant} "
                    f"({resolve_plan_ksu_branch(variant, args.ksu_branch)})"
                )
                warning = t("op_no_kpm_ksu", ksu=selection)
                warning_messages.append(warning)
                print(t("warning_prefix") + " " + warning)
            plans.append({
                "workflow": FULL_MATRIX_WORKFLOWS["full"],
                "name": f"{t('build_target_full')} ({variant})",
                "target": "full",
                "ksu_variant": variant,
                "inputs": _full_matrix_inputs(args, variant),
            })
    elif args.matrix == "all-managers":
        manager_variants = selected_manager_variants(args.manager_variants)
        plans.append({
            "workflow": FULL_MATRIX_WORKFLOWS["all-managers"],
            "name": t("build_target_all_managers"),
            "target": "all-managers",
            "ksu_variant": None,
            "inputs": _all_managers_inputs(args),
        })
    else:
        if args.matrix == "both":
            targets = MATRIX_TARGETS
        elif args.matrix:
            targets = [args.matrix]
        elif args.oneplus:
            targets = ["oneplus"]
        else:
            targets = ["custom"]

        variants = (
            KSU_ALL_VARIANTS
            if args.ksu_variant == "all"
            else [args.ksu_variant or "ReSukiSU"]
        )
        for target in targets:
            for variant in variants:
                workflow = WORKFLOWS[target]
                if target == "oneplus":
                    kpm_enabled = bool(args.kpm) and supports_kpm(
                        variant,
                        oneplus=True,
                    )
                    if args.kpm and not kpm_enabled:
                        selection = f"{variant} (OnePlus main)"
                        warning = t("op_no_kpm_ksu", ksu=selection)
                        warning_messages.append(warning)
                        print(t("warning_prefix") + " " + warning)
                    inputs = {
                        "ksu_variant": variant,
                        "device_manifest": args.device,
                        "cpu": device_info["cpu"],
                        "android_version": device_info["android"],
                        "kernel_version": device_info["kernel"],
                        "enable_susfs": str(_susfs_enabled(args, variant)).lower(),
                        "use_kpm": str(kpm_enabled).lower(),
                        "use_lz4kd": str(bool(args.lz4kd)).lower(),
                        "use_bbg": str(bool(args.bbg)).lower(),
                        "use_bbr": str(bool(args.bbr)).lower(),
                        "use_proxy_optimization": str(bool(args.proxy_optimization)).lower(),
                        "use_unicode_bypass": str(bool(args.unicode_bypass)).lower(),
                    }
                else:
                    if args.kpm and not supports_kpm(variant, args.ksu_branch):
                        selection = (
                            f"{variant} "
                            f"({resolve_plan_ksu_branch(variant, args.ksu_branch)})"
                        )
                        warning = t("op_no_kpm_ksu", ksu=selection)
                        warning_messages.append(warning)
                        print(t("warning_prefix") + " " + warning)
                    kernel_version = (
                        args.kernel_version or "5.10"
                        if target == "custom"
                        else workflow["kernel"]
                    )
                    inputs = _standard_build_inputs(
                        args,
                        variant,
                        kernel_version,
                        supports_supp_op=target in ("a15", "a16"),
                    )
                    if target == "custom":
                        inputs.update({
                            "supp_op": str(bool(args.oneplus_8e)).lower(),
                            "android_version": args.android_version or "android12",
                            "kernel_version": args.kernel_version or "5.10",
                            "sub_level": args.sub_level,
                            "os_patch_level": args.os_patch_level,
                        })
                        if args.revision:
                            inputs["revision"] = args.revision
                plans.append({
                    "workflow": workflow["file"],
                    "name": f"{workflow['name']} ({variant})",
                    "target": target,
                    "ksu_variant": variant,
                    "inputs": inputs,
                })

    client = None
    if args.dry_run:
        ref = args.ref or "dev"
        repo_name = _target_repo(args) or "<auto-fork>"
    else:
        token = get_token(args)
        if not token:
            message = t("err_no_token")
            print(message, file=sys.stderr)
            _set_build_error(
                args,
                message,
                "not_authenticated",
            )
            return 1
        client = make_client(args, token)
        if _report_client_authentication_error(
            client,
            args,
            repo=_target_repo(args),
            dryRun=False,
            total=0,
            run=None,
            runs=[],
            dispatches=[],
            warnings=warning_messages,
        ):
            return 1
        if not prepare_build_repository(client, args):
            return 1
        try:
            ref = args.ref or client.get_default_branch()
        except Exception as exc:
            message = _redact_secret_text(str(exc), _collect_json_secrets(args))
            print(t("err_fork_failed", error=message), file=sys.stderr)
            _set_build_error(
                args,
                message,
                "repository_setup_failed",
                repo=client.repo,
                stage="resolve_default_branch",
            )
            return 1
        repo_name = client.repo

    failures = 0
    successes = 0
    dispatches = []
    dispatched_runs = []
    failure_messages = []
    for index, plan in enumerate(plans, start=1):
        dispatch = {
            "planId": _build_plan_id(plan, ref),
            "workflowFile": plan["workflow"],
            "workflowName": WORKFLOW_RUNTIME_NAMES.get(
                plan["workflow"], plan["name"]
            ),
            "target": plan["target"],
            "ksuVariant": plan["ksu_variant"],
            "ref": ref,
            "inputs": _redacted_inputs(plan["inputs"]),
            "runId": None,
            "runUrl": None,
            "htmlUrl": None,
            "status": "planned" if args.dry_run else "pending",
            "error": None,
        }
        if len(plans) > 1:
            print(f"\n[{index}/{len(plans)}] ", end="")
        print(t("triggering_name", name=plan["name"]))
        plan_kpm_enabled = plan["inputs"].get("use_kpm", str(bool(args.kpm)).lower()) == "true"
        if "enable_susfs" in plan["inputs"]:
            plan_susfs_enabled = (
                str(plan["inputs"]["enable_susfs"]).lower() == "true"
            )
        elif "cancel_susfs" in plan["inputs"]:
            plan_susfs_enabled = (
                str(plan["inputs"]["cancel_susfs"]).lower() != "true"
            )
        else:
            plan_susfs_enabled = bool(args.susfs)
        print(
            "  " + t(
                "build_feat_line",
                susfs=t("enabled") if plan_susfs_enabled else t("disabled"),
                zram=t("enabled") if args.zram else t("disabled"),
                bbg=t("enabled") if args.bbg else t("disabled"),
                ddk=t("enabled") if args.ddk else t("disabled"),
                kpm=t("enabled") if plan_kpm_enabled else t("disabled"),
                rekernel=t("enabled") if args.rekernel else t("disabled"),
                ntsync=t("enabled") if args.ntsync else t("disabled"),
                networking=t("enabled") if args.networking else t("disabled"),
            )
        )
        if args.dry_run:
            print("  " + t("dry_run_skip"))
            print(f"  repo={repo_name} ref={ref} workflow={plan['workflow']}")
            print(
                "  inputs="
                + json.dumps(
                    _redacted_inputs(plan["inputs"]),
                    ensure_ascii=False,
                    sort_keys=True,
                )
            )
            successes += 1
            dispatch["status"] = "dry-run"
            dispatches.append(dispatch)
            continue

        try:
            response = client.trigger_workflow(plan["workflow"], ref, plan["inputs"])
            run_id, run_url, html_url = _dispatch_run_details(response)
            dispatch.update({
                "runId": run_id,
                "runUrl": run_url,
                "htmlUrl": html_url,
                "status": "dispatched",
            })
            if _json_mode(args):
                response_run = (
                    response.get("workflow_run")
                    if isinstance(response, dict)
                    and isinstance(response.get("workflow_run"), dict)
                    else None
                )
                if response_run is None and run_id:
                    try:
                        fetched_run = client.get_run(run_id)
                    except Exception:
                        fetched_run = None
                    if isinstance(fetched_run, dict):
                        response_run = fetched_run
                    else:
                        response_run = {
                            "id": run_id,
                            "name": dispatch["workflowName"],
                            "display_title": dispatch["workflowName"],
                            "html_url": html_url,
                            "head_branch": ref,
                            "event": "workflow_dispatch",
                        }
                if response_run:
                    dispatched_runs.append(_normalize_run(response_run))
            print(t("build_triggered_ok"))
            successes += 1
        except Exception as exc:
            failures += 1
            message = _redact_secret_text(
                str(exc),
                _collect_json_secrets(args),
            )
            failure_messages.append(message)
            dispatch.update({"status": "failed", "error": message})
            print(t("build_triggered_fail", error=message), file=sys.stderr)
            if "404" in message:
                print(t("workflow_404_hint"), file=sys.stderr)
        dispatches.append(dispatch)

    if not args.dry_run and len(plans) > 1 and successes:
        print(t("build_multiple_count", count=successes))
    if not args.dry_run:
        print(t("build_check_status"))
        print(t("build_actions_url", repo=client.repo))
    _set_json_result(
        args,
        ok=not bool(failures),
        repo=repo_name,
        dryRun=bool(args.dry_run),
        total=len(plans),
        run=(
            dispatched_runs[0]
            if len(plans) == 1 and dispatched_runs
            else None
        ),
        runs=dispatched_runs if len(plans) > 1 else [],
        dispatches=dispatches,
        warnings=warning_messages,
        error="; ".join(failure_messages) if failure_messages else None,
        errorCode="dispatch_failed" if failures else None,
    )
    return 1 if failures else 0


def cmd_artifacts(args):
    configured_dir = None
    if args.set_download_dir:
        configured_dir = str(Path(args.set_download_dir).expanduser().resolve())
        with _config_process_lock():
            config = load_config()
            config["download_dir"] = configured_dir
            save_config(config)
        print(t("download_dir_saved", dir=configured_dir))
        if (
            not args.run_id
            and not args.download
            and getattr(args, "artifact_id", None) is None
        ):
            _set_json_result(
                args,
                ok=True,
                runId=None,
                total=0,
                artifacts=[],
                downloads=[],
                downloadDir=configured_dir,
            )
            return 0

    if not args.run_id:
        print(t("err_need_run_id"), file=sys.stderr)
        _set_json_error(
            args,
            t("err_need_run_id"),
            "invalid_arguments",
            runId=None,
            total=0,
            artifacts=[],
            downloads=[],
            downloadDir=configured_dir,
        )
        return 2

    token = get_token(args)
    if not token:
        print(t("err_no_token"), file=sys.stderr)
        _set_json_error(
            args,
            t("err_no_token"),
            "not_authenticated",
            runId=args.run_id,
            total=0,
            artifacts=[],
            downloads=[],
        )
        return 1
    client = make_client(args, token)

    if _report_client_authentication_error(
        client,
        args,
        runId=args.run_id,
        total=0,
        artifacts=[],
        downloads=[],
    ):
        return 1

    if not _repo_is_explicit(client, args):
        try:
            fork = client.get_fork()
            if not fork:
                print(t("err_no_fork"), file=sys.stderr)
                _set_json_error(
                    args,
                    t("err_no_fork"),
                    "fork_not_found",
                    runId=args.run_id,
                    total=0,
                    artifacts=[],
                    downloads=[],
                )
                return 1
            client.repo = fork.get("full_name", client.repo)
            client.fork_repo = fork
        except Exception as exc:
            print(f"Artifact operation failed: {exc}", file=sys.stderr)
            _set_json_error(
                args,
                exc,
                "artifact_operation_failed",
                runId=args.run_id,
                total=0,
                artifacts=[],
                downloads=[],
            )
            return 1

    try:
        response = client.list_artifacts(args.run_id)
        all_artifacts = response.get("artifacts", [])
        artifact_id = getattr(args, "artifact_id", None)
        artifacts = all_artifacts
        if artifact_id is not None:
            artifacts = [art for art in all_artifacts if art.get("id") == artifact_id]
            if not artifacts:
                message = f"artifact {artifact_id} does not belong to run {args.run_id}"
                print(message, file=sys.stderr)
                _set_json_error(
                    args,
                    message,
                    "artifact_not_found",
                    repo=client.repo,
                    runId=args.run_id,
                    total=0,
                    artifacts=[],
                    downloads=[],
                )
                return 1

        config = load_config()
        output_dir = Path(
            args.output
            or configured_dir
            or config.get("download_dir")
            or default_download_dir()
        ).expanduser().resolve()
        normalized_artifacts = [_normalize_artifact(art) for art in artifacts]
        downloads = []
        verification_enabled = signing_verification_enabled(client.repo)

        if not artifacts:
            print(t("artifacts_no_artifacts"))
            _set_json_result(
                args,
                ok=True,
                repo=client.repo,
                runId=args.run_id,
                total=0,
                artifacts=[],
                downloads=[],
                downloadDir=str(output_dir),
                verificationEnabled=verification_enabled,
            )
            return 0

        print(t("artifacts_list", id=args.run_id))
        for art in artifacts:
            size_kb = art["size_in_bytes"] / 1024
            print(f"  {art['id']} | {art['name']} | {size_kb:.1f} KB")

        if args.download:
            Path(output_dir).mkdir(parents=True, exist_ok=True)
            print(f"\n" + t("artifacts_download_to", dir=output_dir))
            signing_key = resolve_verification_key(client)
            failures = 0
            failure_messages = []
            verification_failed = False
            for art in artifacts:
                print(f"  " + t("artifacts_downloading", name=art["name"]))
                try:
                    path = client.download_artifact(art["id"], output_dir)
                except Exception as exc:
                    failures += 1
                    failure_messages.append(str(exc))
                    downloads.append({
                        "artifactId": art["id"],
                        "name": art["name"],
                        "path": None,
                        "verification": None,
                        "error": str(exc),
                    })
                    print(f"    download failed: {exc}", file=sys.stderr)
                    continue

                path = str(Path(path).expanduser().resolve())
                print(f"    -> {path}")
                if verification_enabled:
                    print(f"    " + t("artifact_verifying"))
                    result = verify_artifact_archive(path, signing_key, args.run_id)
                else:
                    result = _verify_result(
                        False,
                        "disabled",
                        t("artifact_verification_disabled"),
                        bundles=[],
                    )
                for bundle in result.get("bundles", []):
                    label = bundle.get("bundle", Path(path).name)
                    icon = "✓" if bundle["verified"] else "⚠"
                    print(f"    {icon} {label}: {bundle['message']}")
                if not result.get("bundles"):
                    print(f"    ⚠ {result['message']}")

                if not result['verified'] and result.get("status") != "disabled":
                    failures += 1
                    verification_failed = True
                    failure_messages.append(result.get("message") or "verification failed")
                    if _json_mode(args):
                        try:
                            Path(path).unlink()
                        except FileNotFoundError:
                            pass
                        retained_path = None
                        print(f"    {t('artifact_verify_skip_user')}")
                    else:
                        retained_path = path
                        sys.stdout.write("    " + t("artifact_verify_confirm"))
                        sys.stdout.flush()
                        answer = sys.stdin.readline().strip().lower()
                        if answer not in ('y', 'yes', 'j', 'ja', 'o', 'oui', 's', 'si', 'sí'):
                            try:
                                Path(path).unlink()
                            except FileNotFoundError:
                                pass
                            retained_path = None
                            print(f"    {t('artifact_verify_skip_user')}")
                else:
                    retained_path = path
                downloads.append({
                    "artifactId": art["id"],
                    "name": art["name"],
                    "path": retained_path,
                    "verification": result,
                    "error": (
                        None
                        if result.get("verified") or result.get("status") == "disabled"
                        else result.get("message")
                    ),
                })
            if failures:
                _set_json_result(
                    args,
                    ok=False,
                    repo=client.repo,
                    runId=args.run_id,
                    total=len(normalized_artifacts),
                    artifacts=normalized_artifacts,
                    downloads=downloads,
                    downloadDir=str(output_dir),
                    verificationEnabled=verification_enabled,
                    error="; ".join(failure_messages),
                    errorCode=(
                        "artifact_verification_failed"
                        if verification_failed
                        else "artifact_download_failed"
                    ),
                )
                return 1
        _set_json_result(
            args,
            ok=True,
            repo=client.repo,
            runId=args.run_id,
            total=len(normalized_artifacts),
            artifacts=normalized_artifacts,
            downloads=downloads,
            downloadDir=str(output_dir),
            verificationEnabled=verification_enabled,
        )
        return 0
    except Exception as exc:
        print(f"Artifact operation failed: {exc}", file=sys.stderr)
        _set_json_error(
            args,
            exc,
            "artifact_operation_failed",
            repo=client.repo,
            runId=args.run_id,
            total=0,
            artifacts=[],
            downloads=[],
        )
        return 1


def cmd_list(args):
    if args.oneplus:
        print(t("op_list_title"))
        for did, info in ONEPLUS_DEVICES.items():
            print(f"  {did:<35} {info['name']:<20} {info['cpu']:<10} {info['android']} {info['kernel']}")
        devices = [
            {
                "id": did,
                "name": info["name"],
                "cpu": info["cpu"],
                "androidVersion": info["android"],
                "kernelVersion": info["kernel"],
            }
            for did, info in ONEPLUS_DEVICES.items()
        ]
        _set_json_result(
            args,
            ok=True,
            total=len(devices),
            devices=devices,
        )
        return 0

    print("=" * 50)
    print(t("list_title"))
    for key in MATRIX_TARGETS:
        wf = WORKFLOWS[key]
        print(f"  --matrix {key:<10} {wf['name']}")
    print(f"  --matrix {'both':<10} both")
    print(f"  --matrix {'full':<10} full")
    print(f"  --matrix {'all-managers':<10} all-managers")
    print(f"  --oneplus{'':<10} (--device required)")
    print(f"\n  " + t("default_build_info"))

    print(f"\n{t('ksu_variants_label')}")
    print("  " + " / ".join(KSU_VARIANTS + ["all"]))

    print(f"\n{t('ksu_branches_label')}")
    print("  Stable / Latest / Dev / Custom")

    print(f"\n{t('op_features_title')}")
    print(f"  --[no-]lz4kd  --[no-]bbr  --[no-]proxy-optimization  --[no-]unicode-bypass")

    print(f"\n{t('features_title')}")
    print(f"  --[no-]zram  --[no-]bbg  --[no-]ddk  --[no-]kpm")
    print(f"  --[no-]susfs  --[no-]rekernel  --[no-]ntsync  --[no-]networking")
    print(f"  --[no-]oneplus-8e  --[no-]zram-full-algo  --zram-extra-algos")

    print(f"\n{t('commands_label')}")
    cmds = [("login", "cmd_login_help"),("logout", "cmd_logout_help"),("whoami", "cmd_whoami_help"),
            ("fork", "cmd_fork_help"),("sync", "cmd_sync_help"),("build", "cmd_build_help"),
            ("status", "cmd_status_help"),("artifacts", "cmd_artifacts_help"),
            ("signing", "cmd_signing_help"),("list", "cmd_list_help")]
    for cmd, key in cmds:
        print(f"  abk {cmd:<12} {t(key)}")

    print("\n  abk build --help | abk status --help")
    _set_json_result(
        args,
        ok=True,
        targets=list(MATRIX_TARGETS_ALL),
        ksuVariants=list(KSU_VARIANTS) + ["all"],
    )
    return 0


def cmd_self_test(args):
    """Exercise dependencies required by the frozen CLI bundle."""
    try:
        _, public_key = generate_signing_keypair()
        normalize_signing_public_key(public_key)
        import nacl.bindings
        import certifi as certifi_module

        nacl.bindings.sodium_init()
        ca_bundle = certifi_module.where()
        if not Path(ca_bundle).is_file():
            raise RuntimeError("certifi CA bundle is missing")
        _create_tls_context()
        _set_json_result(
            args,
            ok=True,
            cryptoBackend=_CRYPTO_BACKEND,
            pynacl=True,
            caBundle=True,
            tlsContext=True,
        )
        print("ABK CLI self-test: ok")
        return 0
    except Exception as exc:
        print(f"ABK CLI self-test failed: {exc}", file=sys.stderr)
        _set_json_error(args, exc, "self_test_failed")
        return 1


def refresh_workflow_names():
    name_keys = {
        "a12": "build_target_a12",
        "a13": "build_target_a13",
        "a14": "build_target_a14",
        "a15": "build_target_a15",
        "a16": "build_target_a16",
        "custom": "build_target_custom",
        "oneplus": "build_target_oneplus",
    }
    for target, key in name_keys.items():
        WORKFLOWS[target]["name"] = t(key)


def requested_language(argv, stop_at_help=False):
    requested = None
    for index, arg in enumerate(argv):
        if arg == "--" or (stop_at_help and arg in {"-h", "--help"}):
            break
        if arg == "--lang" and index + 1 < len(argv):
            requested = argv[index + 1]
        if arg.startswith("--lang="):
            requested = arg.split("=", 1)[1]
    return requested


def supported_language_tag(value):
    normalized = normalize_language_tag(value, allow_fallback=False)
    if normalized is None:
        raise argparse.ArgumentTypeError(f"unsupported language tag: {value}")
    return normalized


def repo_slug(value):
    pattern = r"[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})/[A-Za-z0-9_.-]{1,100}"
    if not re.fullmatch(pattern, value or ""):
        raise argparse.ArgumentTypeError("repository must use OWNER/REPO format")
    return value


def status_limit(value):
    number = int(value)
    if not 1 <= number <= 100:
        raise argparse.ArgumentTypeError("limit must be between 1 and 100")
    return number


def positive_int(value):
    try:
        number = int(value)
    except ValueError as exc:
        raise argparse.ArgumentTypeError("must be a positive integer") from exc
    if number <= 0:
        raise argparse.ArgumentTypeError("must be a positive integer")
    return number


class ABKArgumentParser(argparse.ArgumentParser):
    def error(self, message):
        message = _redact_secret_text(message, _collect_json_secrets())
        if "--json" in sys.argv[1:]:
            candidate = self.prog.rsplit(maxsplit=1)[-1]
            command = getattr(self, "_command_hint", None)
            if command is None and candidate in {
                "login", "logout", "whoami", "fork", "sync", "build",
                "status", "artifacts", "signing", "list", "self-test",
            }:
                command = candidate
            payload = {
                "schemaVersion": JSON_SCHEMA_VERSION,
                "cliVersion": CLI_VERSION,
                "ok": False,
                "command": command,
                "error": message,
                "errorCode": "invalid_arguments",
            }
            self._print_message(
                json.dumps(payload, ensure_ascii=False, separators=(",", ":")) + "\n",
                sys.stdout,
            )
            self.exit(2)
        super().error(message)


class ABKVersionAction(argparse.Action):
    def __init__(self, option_strings, dest, **kwargs):
        super().__init__(option_strings, dest, nargs=0, **kwargs)

    def __call__(self, parser, namespace, values, option_string=None):
        raw_argv = sys.argv[1:]
        option_argv = (
            raw_argv[:raw_argv.index("--")]
            if "--" in raw_argv
            else raw_argv
        )
        if "--json" in option_argv:
            payload = {
                "schemaVersion": JSON_SCHEMA_VERSION,
                "cliVersion": CLI_VERSION,
                "ok": True,
                "command": "version",
                "error": None,
                "errorCode": None,
            }
            message = json.dumps(
                payload,
                ensure_ascii=False,
                separators=(",", ":"),
            )
        else:
            message = f"{parser.prog} {CLI_VERSION}"
        parser._print_message(message + "\n", sys.stdout)
        parser.exit()


def _collect_json_secrets(args=None, argv=None):
    stored_token = None
    if CONFIG_FILE.exists():
        stored_token = load_config().get("token")
    values = {
        value
        for value in (
            getattr(args, "token", None) if args is not None else None,
            getattr(args, "kpm_password", None) if args is not None else None,
            os.environ.get("GITHUB_TOKEN"),
            os.environ.get("GH_TOKEN"),
            os.environ.get("ABK_KPM_PASSWORD"),
            stored_token,
        )
        if isinstance(value, str) and value
    }
    raw_args = list(sys.argv[1:] if argv is None else argv)
    secret_flags = ("--token", "--kpm-password")
    for index, item in enumerate(raw_args):
        if item in secret_flags and index + 1 < len(raw_args):
            value = raw_args[index + 1]
            if value:
                values.add(value)
            continue
        for flag in secret_flags:
            prefix = flag + "="
            if item.startswith(prefix) and item[len(prefix):]:
                values.add(item[len(prefix):])
                break
    return values


def _redact_secret_text(value, secrets):
    for secret in sorted(secrets, key=len, reverse=True):
        value = value.replace(secret, "***")
    return value


_SENSITIVE_JSON_KEYS = frozenset({
    "access_token",
    "accesstoken",
    "api_key",
    "apikey",
    "authorization",
    "client_secret",
    "clientsecret",
    "cookie",
    "kpm_password",
    "kpmpassword",
    "password",
    "private_key",
    "privatekey",
    "secret",
    "secret_value",
    "secretvalue",
    "token",
})


def _is_sensitive_json_key(key):
    if not isinstance(key, str):
        return False
    normalized = re.sub(r"[^a-z0-9]", "", key.lower())
    return normalized in {
        re.sub(r"[^a-z0-9]", "", item)
        for item in _SENSITIVE_JSON_KEYS
    }


def _redact_json_secrets(value, secrets, sensitive_context=False):
    if isinstance(value, dict):
        redacted = {}
        for key, item in value.items():
            if _is_sensitive_json_key(key):
                redacted[key] = None if item is None else "***"
                continue
            redacted[key] = _redact_json_secrets(
                item,
                secrets,
                sensitive_context or (
                    isinstance(key, str)
                    and key.lower() in {
                        "error",
                        "message",
                        "detail",
                        "details",
                        "stderr",
                        "stdout",
                        "output",
                    }
                ),
            )
        return redacted
    if isinstance(value, list):
        return [
            _redact_json_secrets(item, secrets, sensitive_context)
            for item in value
        ]
    if isinstance(value, str) and sensitive_context:
        value = _redact_secret_text(value, secrets)
    elif isinstance(value, str):
        # Avoid corrupting ordinary fields when a deliberately short password
        # is supplied, while still masking normal-length tokens anywhere in a
        # machine-readable response.
        value = _redact_secret_text(
            value,
            {secret for secret in secrets if len(secret) >= 3},
        )
    return value


def _run_json_command(args):
    captured_stdout = io.StringIO()
    captured_stderr = io.StringIO()
    try:
        with (
            contextlib.redirect_stdout(captured_stdout),
            contextlib.redirect_stderr(captured_stderr),
        ):
            _persist_requested_language(getattr(args, "lang", None))
            result = args.func(args)
        exit_code = result if isinstance(result, int) else 0
    except KeyboardInterrupt:
        exit_code = 1
        args._json_result = {
            "ok": False,
            "error": "operation cancelled",
            "errorCode": "cancelled",
        }
    except Exception as exc:
        exit_code = 1
        args._json_result = {
            "ok": False,
            "error": str(exc),
            "errorCode": "unexpected_error",
        }

    payload = getattr(args, "_json_result", None)
    if not isinstance(payload, dict):
        lines = [
            line.strip()
            for line in (captured_stderr.getvalue() or captured_stdout.getvalue()).splitlines()
            if line.strip()
        ]
        payload = {"ok": exit_code == 0}
        if exit_code != 0:
            payload.update({
                "error": lines[-1] if lines else "command failed",
                "errorCode": (
                    "invalid_arguments" if exit_code == 2 else "command_failed"
                ),
            })

    payload = dict(payload)
    payload["ok"] = exit_code == 0 and payload.get("ok", True) is not False
    payload.setdefault("schemaVersion", JSON_SCHEMA_VERSION)
    payload.setdefault("cliVersion", CLI_VERSION)
    payload.setdefault("command", args.command)
    payload.setdefault("error", None)
    payload.setdefault("errorCode", None)
    secrets = _collect_json_secrets(args)
    redacted_payload = _redact_json_secrets(payload, secrets)
    json_document = json.dumps(
        redacted_payload,
        ensure_ascii=False,
        separators=(",", ":"),
        default=str,
    )
    sys.stdout.write(
        json_document + "\n"
    )
    sys.stdout.flush()
    return exit_code


def _persist_requested_language(language):
    stored_language = language_storage_id(language)
    if stored_language is None:
        return
    with _config_process_lock():
        config = load_config()
        if config.get("lang") != stored_language:
            config["lang"] = stored_language
            save_config(config)


def main():
    # 提前检测 --lang 以确保帮助文本使用正确语言
    raw_argv = sys.argv[1:]
    option_argv = raw_argv[:raw_argv.index("--")] if "--" in raw_argv else raw_argv
    help_requested = any(arg in {"-h", "--help"} for arg in option_argv)
    early_language = normalize_language_tag(
        requested_language(option_argv, stop_at_help=help_requested),
        allow_fallback=False,
    )
    if early_language:
        load_translations(early_language)
        refresh_workflow_names()

        # argparse exits while handling --help, so persist that selection now.
        # Other invocations retain the normal post-parse behavior below; JSON
        # commands keep persistence inside their output-capture boundary.
        if help_requested:
            _persist_requested_language(early_language)
    
    parser = ABKArgumentParser(
        prog="abk",
        description=t("abk_cli_desc_full"),
        formatter_class=argparse.RawDescriptionHelpFormatter,
        add_help=False)
    parser.add_argument("-h", "--help", action="help", default=argparse.SUPPRESS,
        help=t("help_flag"))
    parser.add_argument(
        "--version",
        action=ABKVersionAction,
        help=t("help_version"),
    )
    parser.add_argument("--token", help=t("help_token"))
    parser.add_argument("--repo", type=repo_slug, help=t("help_repo"))
    parser.add_argument("--verbose", "-v", action="store_true", help=t("help_verbose"))
    parser.add_argument(
        "--lang",
        type=supported_language_tag,
        choices=SUPPORTED_LANGUAGES,
        help=t("help_lang"),
    )
    parser.add_argument(
        "--json",
        action="store_true",
        help=t("arg_json"),
    )

    subparsers = parser.add_subparsers(dest="command", help=t("help_subcommands"))

    # login
    login_parser = subparsers.add_parser("login", 
        help=t("cmd_login_help"),
        description=t("cmd_login_desc"))
    login_parser.set_defaults(func=cmd_login)

    # logout
    logout_parser = subparsers.add_parser("logout", 
        help=t("cmd_logout_help"),
        description=t("cmd_logout_desc"))
    logout_parser.set_defaults(func=cmd_logout)

    # whoami
    whoami_parser = subparsers.add_parser("whoami", 
        help=t("cmd_whoami_help"),
        description=t("cmd_whoami_desc"))
    whoami_parser.set_defaults(func=cmd_whoami)

    # fork
    fork_parser = subparsers.add_parser("fork", 
        help=t("cmd_fork_help"),
        description=t("cmd_fork_desc"))
    fork_parser.add_argument("--no-sync", action="store_true", help=t("arg_no_sync"))
    fork_parser.set_defaults(func=cmd_fork)

    # sync
    sync_parser = subparsers.add_parser("sync", 
        help=t("cmd_sync_help"),
        description=t("cmd_sync_desc"))
    sync_parser.set_defaults(func=cmd_sync)

    # build
    build_parser = subparsers.add_parser("build", 
        help=t("cmd_build_help"),
        description=t("cmd_build_desc"),
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=t("build_epilog"))
    build_mode = build_parser.add_mutually_exclusive_group()
    build_mode.add_argument("--matrix", choices=MATRIX_TARGETS_ALL, help=t("arg_matrix"))
    build_mode.add_argument("--oneplus", action="store_true", help=t("arg_oneplus"))
    build_parser.add_argument("--ref", help=t("arg_ref"))
    build_parser.add_argument("--ksu", dest="ksu_variant", choices=KSU_VARIANTS + ["all"], help=t("arg_ksu"))
    build_parser.add_argument(
        "--ksu-branch",
        choices=["Stable", "Latest", "Dev", "Custom"],
        help=t("arg_ksu_branch"),
    )
    build_parser.add_argument("--custom-ref", help=t("arg_custom_ref"))
    build_parser.add_argument("--version", help=t("arg_version"))
    build_parser.add_argument("--device", help=t("arg_device"))
    build_parser.add_argument("--virt", choices=VIRT_OPTIONS, default=None, help=t("arg_virt"))
    build_parser.add_argument(
        "--kpm-password",
        default=os.environ.get("ABK_KPM_PASSWORD"),
        help=t("arg_kpm_password"),
    )
    build_parser.add_argument("--build-time", help=t("arg_build_time"))
    build_parser.add_argument("--force", action="store_true", help=t("arg_force"))
    build_parser.add_argument("--dry-run", action="store_true", help=t("arg_dry_run"))
    
    build_parser.add_argument("--android-version", choices=ANDROID_VERSIONS, help=t("arg_android_version"))
    build_parser.add_argument("--kernel-version", choices=KERNEL_VERSIONS, help=t("arg_kernel_version"))
    build_parser.add_argument("--sub-level", help=t("arg_sub_level"))
    build_parser.add_argument("--os-patch-level", help=t("arg_os_patch_level"))
    build_parser.add_argument("--revision", help=t("arg_revision"))
    
    build_parser.add_argument("--build-scope", choices=["Both", "GKI", "OnePlus"], help=t("arg_build_scope"))
    build_parser.add_argument("--manager-variants", help=t("arg_manager_variants"))
    
    build_parser.add_argument("--zram", action="store_true", help=t("arg_zram"))
    build_parser.add_argument("--no-zram", dest="zram", action="store_false", help=t("arg_no_zram"))
    build_parser.add_argument("--bbg", action="store_true", help=t("arg_bbg"))
    build_parser.add_argument("--no-bbg", dest="bbg", action="store_false", help=t("arg_no_bbg"))
    build_parser.add_argument("--ddk", action="store_true", help=t("arg_ddk"))
    build_parser.add_argument("--no-ddk", dest="ddk", action="store_false", help=t("arg_no_ddk"))
    build_parser.add_argument("--kpm", action="store_true", help=t("arg_kpm_flag"))
    build_parser.add_argument("--no-kpm", dest="kpm", action="store_false", help=t("arg_no_kpm_flag"))
    build_parser.add_argument("--susfs", action="store_true", help=t("arg_susfs"))
    build_parser.add_argument("--no-susfs", dest="susfs", action="store_false", help=t("arg_no_susfs"))
    build_parser.add_argument("--rekernel", action="store_true", help=t("arg_rekernel"))
    build_parser.add_argument("--no-rekernel", dest="rekernel", action="store_false", help=t("arg_no_rekernel"))
    build_parser.add_argument("--oneplus-8e", action="store_true", help=t("arg_oneplus_8e"))
    build_parser.add_argument(
        "--no-oneplus-8e",
        dest="oneplus_8e",
        action="store_false",
        help=argparse.SUPPRESS,
    )
    build_parser.add_argument("--lz4kd", action="store_true", help=t("arg_lz4kd"))
    build_parser.add_argument("--no-lz4kd", dest="lz4kd", action="store_false", help=argparse.SUPPRESS)
    build_parser.add_argument("--bbr", action="store_true", help=t("arg_bbr"))
    build_parser.add_argument("--no-bbr", dest="bbr", action="store_false", help=argparse.SUPPRESS)
    build_parser.add_argument("--proxy-optimization", action="store_true", help=t("arg_proxy"))
    build_parser.add_argument(
        "--no-proxy-optimization",
        dest="proxy_optimization",
        action="store_false",
        help=argparse.SUPPRESS,
    )
    build_parser.add_argument("--unicode-bypass", action="store_true", help=t("arg_unicode"))
    build_parser.add_argument(
        "--no-unicode-bypass",
        dest="unicode_bypass",
        action="store_false",
        help=argparse.SUPPRESS,
    )
    build_parser.add_argument("--ntsync", action="store_true", help=t("arg_ntsync"))
    build_parser.add_argument(
        "--no-ntsync",
        dest="ntsync",
        action="store_false",
        help=t("arg_no_ntsync"),
    )
    build_parser.add_argument("--networking", action="store_true", help=t("arg_networking"))
    build_parser.add_argument(
        "--no-networking",
        dest="networking",
        action="store_false",
        help=t("arg_no_networking"),
    )
    build_parser.add_argument("--zram-full-algo", action="store_true", help=t("arg_zram_full_algo"))
    build_parser.add_argument(
        "--no-zram-full-algo",
        dest="zram_full_algo",
        action="store_false",
        help=argparse.SUPPRESS,
    )
    build_parser.add_argument("--zram-extra-algos", help=t("arg_zram_extra_algos"))
    build_parser.add_argument("--custom-modules", help=t("arg_custom_modules"))
    build_parser.set_defaults(
        func=cmd_build,
        zram=None,
        bbg=None,
        ddk=None,
        kpm=None,
        susfs=None,
        rekernel=None,
        oneplus_8e=None,
        lz4kd=None,
        bbr=None,
        proxy_optimization=None,
        unicode_bypass=None,
        ntsync=None,
        networking=None,
        zram_full_algo=None,
    )

    # status
    status_parser = subparsers.add_parser("status", 
        help=t("cmd_status_help"),
        description=t("cmd_status_desc"))
    run_action = status_parser.add_mutually_exclusive_group()
    run_action.add_argument("--run-id", type=positive_int, help=t("arg_run_id_status"))
    status_parser.add_argument(
        "--target",
        choices=list(WORKFLOWS) + list(FULL_MATRIX_WORKFLOWS),
        help=t("arg_target"),
    )
    status_parser.add_argument(
        "--status",
        choices=["all", "queued", "in_progress", "completed"],
        default="all",
        help=t("arg_status_filter"),
    )
    status_parser.add_argument("--limit", type=status_limit, default=10, help=t("arg_limit"))
    run_action.add_argument("--cancel", type=positive_int, metavar="RUN_ID", help=t("arg_cancel"))
    run_action.add_argument("--rerun", type=positive_int, metavar="RUN_ID", help=t("arg_rerun"))
    status_parser.set_defaults(func=cmd_status)

    # artifacts
    artifacts_parser = subparsers.add_parser("artifacts", 
        help=t("cmd_artifacts_help"),
        description=t("cmd_artifacts_desc"))
    artifacts_parser.add_argument("--run-id", type=positive_int, help=t("arg_run_id"))
    artifacts_parser.add_argument(
        "--artifact-id",
        type=positive_int,
        help=t("arg_artifact_id"),
    )
    artifacts_parser.add_argument("--download", action="store_true", help=t("arg_download"))
    artifacts_parser.add_argument(
        "--output",
        "-o",
        help=t("arg_output", dir=default_download_dir()),
    )
    artifacts_parser.add_argument("--set-download-dir", metavar="DIR", help=t("arg_set_download_dir"))
    artifacts_parser.set_defaults(func=cmd_artifacts)

    signing_parser = subparsers.add_parser(
        "signing",
        help=t("cmd_signing_help"),
        description=t("cmd_signing_desc"),
    )
    signing_parser.add_argument(
        "signing_action",
        nargs="?",
        default="status",
        choices=["status", "import", "rotate", "enable", "disable"],
        help=t("arg_signing_action"),
    )
    signing_parser.add_argument(
        "--public-key-file",
        metavar="FILE",
        help=t("arg_public_key_file"),
    )
    signing_parser.add_argument(
        "--private-key-file",
        metavar="FILE",
        help=t("arg_private_key_file"),
    )
    signing_parser.add_argument(
        "--yes",
        action="store_true",
        help=t("arg_yes"),
    )
    signing_parser.add_argument(
        "--dry-run",
        action="store_true",
        help=t("arg_signing_dry_run"),
    )
    signing_parser.set_defaults(func=cmd_signing)

    # list
    list_parser = subparsers.add_parser("list", 
        help=t("cmd_list_help"),
        description=t("cmd_list_desc"))
    list_parser.add_argument("--oneplus", action="store_true", help=t("arg_oneplus"))
    list_parser.set_defaults(func=cmd_list)

    self_test_parser = subparsers.add_parser(
        "self-test",
        help=t("cmd_self_test_help"),
    )
    self_test_parser.set_defaults(func=cmd_self_test)

    args = parser.parse_args()
    if args.lang:
        load_translations(args.lang)
        refresh_workflow_names()

    if not args.command:
        if args.json:
            parser.error("a command is required")
        _persist_requested_language(args.lang)
        parser.print_help()
        return 0

    if _target_repo(args) and args.command in {"login", "sync"}:
        source = "--repo" if args.repo else "ABK_REPO"
        parser._command_hint = args.command
        parser.error(f"{source} is not supported by {args.command}")

    if args.json:
        return _run_json_command(args)
    _persist_requested_language(args.lang)
    return args.func(args) or 0


if __name__ == "__main__":
    configure_stdio()
    sys.exit(main())
