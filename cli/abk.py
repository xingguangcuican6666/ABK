#!/usr/bin/env python3

import argparse
import json
import os
import re
import shutil
import sys
import tempfile
import time
import webbrowser
from pathlib import Path, PurePosixPath
from urllib.request import HTTPRedirectHandler, Request, build_opener, urlopen
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode, urlparse
import zipfile
import hashlib
import hmac
import base64

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
    os.environ.setdefault('SSL_CERT_FILE', certifi.where())
except ImportError:
    pass

sys.path.insert(0, str(Path(__file__).parent))
from i18n import t, load_translations


def configure_stdio():
    for stream_name in ("stdout", "stderr"):
        stream = getattr(sys, stream_name, None)
        if stream is None or not hasattr(stream, "reconfigure"):
            continue
        try:
            # Avoid crashing on terminals or pipelines whose locale encoding
            # cannot represent translated help or status output.
            stream.reconfigure(errors="replace")
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
MAX_MANIFEST_SIZE = 1024 * 1024
MAX_SIGNATURE_SIZE = 64 * 1024
MAX_PAYLOAD_SIZE = 8 * 1024 * 1024 * 1024

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

KSU_VARIANTS = ["None", "Official", "SukiSU", "ReSukiSU"]
KSU_BRANCH_MAP = {
    "stable": "Stable(标准)", "Stable": "Stable(标准)",
    "dev": "Dev(开发)", "Dev": "Dev(开发)",
    "custom": "Custom(自定义)", "Custom": "Custom(自定义)",
}
KSU_BRANCH_VALUES = ["Stable(标准)", "Dev(开发)", "Custom(自定义)"]

def resolve_ksu_branch(b):
    return KSU_BRANCH_MAP.get(b, b) if b else "Stable(标准)"


def supports_kpm(variant, ksu_branch=None, *, oneplus=False):
    """Return whether the selected KernelSU source exposes KPM.

    ReSukiSU provides KPM in stable tags, while OnePlus builds pin its main
    branch. SukiSU exposes KPM on both standard and OnePlus build paths.
    """
    if variant == "SukiSU":
        return True
    if variant != "ReSukiSU" or oneplus:
        return False
    return resolve_ksu_branch(ksu_branch) == "Stable(标准)"


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


VIRT_OPTIONS = ["off", "678", "123", "345"]

ONEPLUS_DEVICES = {
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
    if revision and re.fullmatch(r"[A-Za-z0-9._+-]{1,32}", revision) is None:
        return "--revision"

    build_time = getattr(args, "build_time", None)
    if build_time and (
        _has_unsafe_workflow_text(build_time, 128)
        or re.fullmatch(r"[A-Za-z0-9:+,./ _-]+", build_time) is None
    ):
        return "--build-time"

    sub_level = getattr(args, "sub_level", None)
    if sub_level and (
        re.fullmatch(r"[0-9]{1,4}", sub_level) is None
        or int(sub_level) > 9999
    ):
        return "--sub-level"

    patch_level = getattr(args, "os_patch_level", None)
    if patch_level and re.fullmatch(r"20[0-9]{2}-(0[1-9]|1[0-2])", patch_level) is None:
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
    CONFIG_DIR.mkdir(parents=True, exist_ok=True, mode=0o700)
    if os.name != "nt":
        CONFIG_DIR.chmod(0o700)

    payload = json.dumps(config, indent=2, ensure_ascii=False) + "\n"
    fd, temp_name = tempfile.mkstemp(prefix=".config-", suffix=".tmp", dir=CONFIG_DIR)
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
        with urlopen(req, timeout=30) as resp:
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
        with urlopen(req, timeout=30) as resp:
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
        self.repo = repo
        self.repo_explicit = bool(repo)
        self.verbose = verbose
        self.username = None
        self.fork_repo = None
        
        if self.token:
            self._detect_user(detect_fork=not self.repo_explicit)
        
        if not self.repo:
            if self.fork_repo:
                self.repo = self.fork_repo.get("full_name")
            else:
                self.repo = os.environ.get("ABK_REPO", DEFAULT_REPO)

    def _detect_user(self, detect_fork=True):
        try:
            user = self.get("/user")
            self.username = user.get("login")
            if detect_fork:
                fork = self.get_fork()
                if fork:
                    self.fork_repo = fork
        except Exception as e:
            print(t("login_verify_failed", error=e), file=sys.stderr)

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
            print(f"> {method} {url}", file=sys.stderr)
        req = Request(url, data=data, headers=headers, method=method)
        try:
            with urlopen(req, timeout=30) as resp:
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
        return self.post(path, {"ref": ref, "inputs": inputs})

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
        return self.get(f"/repos/{self.repo}/actions/runs/{run_id}/artifacts")

    def download_artifact(self, artifact_id, output_dir="."):
        url = f"{GITHUB_API}/repos/{self.repo}/actions/artifacts/{artifact_id}/zip"
        headers = {
            "Authorization": f"Bearer {self.token}",
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "ABK-CLI",
        }
        req = Request(url, headers=headers)
        opener = build_opener(_NoRedirectHandler())
        response = None
        try:
            try:
                response = opener.open(req, timeout=30)
            except HTTPError as exc:
                if exc.code not in (301, 302, 303, 307, 308):
                    raise
                location = exc.headers.get("Location")
                exc.close()
                parsed = urlparse(location or "")
                if parsed.scheme != "https" or not parsed.hostname:
                    raise RuntimeError("GitHub returned an unsafe artifact redirect")
                # Never forward the GitHub credential to the object-storage origin.
                redirected = Request(location, headers={"User-Agent": "ABK-CLI"})
                response = urlopen(redirected, timeout=60)

            output_dir = Path(output_dir)
            output_dir.mkdir(parents=True, exist_ok=True)
            output_path = output_dir / f"artifact-{artifact_id}.zip"
            fd, temp_name = tempfile.mkstemp(
                prefix=f".artifact-{artifact_id}-", suffix=".tmp", dir=output_dir
            )
            try:
                with os.fdopen(fd, "wb") as stream:
                    fd = None
                    shutil.copyfileobj(response, stream, length=1024 * 1024)
                    stream.flush()
                    os.fsync(stream.fileno())
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
        with urlopen(req, timeout=30) as resp:
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
        with urlopen(req, timeout=30) as resp:
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

    def _download_release_asset_text(self, asset_url):
        request = Request(
            asset_url,
            headers={
                "Authorization": f"Bearer {self.token}",
                "Accept": "application/octet-stream",
                "User-Agent": "ABK-CLI",
            },
        )
        opener = build_opener(_NoRedirectHandler())
        response = None
        try:
            try:
                response = opener.open(request, timeout=30)
            except HTTPError as exc:
                if exc.code not in (301, 302, 303, 307, 308):
                    raise
                location = exc.headers.get("Location")
                exc.close()
                parsed = urlparse(location or "")
                if parsed.scheme != "https" or not parsed.hostname:
                    raise RuntimeError("GitHub returned an unsafe release-asset redirect")
                response = urlopen(
                    Request(location, headers={"User-Agent": "ABK-CLI"}),
                    timeout=30,
                )
            content = response.read(MAX_MANIFEST_SIZE + 1)
            if len(content) > MAX_MANIFEST_SIZE:
                raise RuntimeError("published signing key is unexpectedly large")
            return content.decode("utf-8").strip()
        finally:
            if response is not None:
                response.close()

    def get_published_signing_key(self):
        release = self.get_release_by_tag(SIGNING_RELEASE_TAG)
        if not release:
            return None
        for asset in release.get("assets", []):
            if asset.get("name") == SIGNING_PUBLIC_KEY_ASSET:
                return self._download_release_asset_text(asset["url"])
        return None

    def publish_signing_key(self, public_key_pem):
        release = self.get_release_by_tag(SIGNING_RELEASE_TAG)
        if not release:
            release = self.create_release(SIGNING_RELEASE_TAG)

        for asset in release.get("assets", []):
            if asset.get("name") == SIGNING_PUBLIC_KEY_ASSET:
                current = self._download_release_asset_text(asset["url"])
                if current.strip() == public_key_pem.strip():
                    return True
                raise RuntimeError("a different artifact signing public key is already published")

        upload_url = str(release.get("upload_url", "")).split("{", 1)[0]
        parsed = urlparse(upload_url)
        if parsed.scheme != "https" or parsed.hostname != "uploads.github.com":
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
        with urlopen(request, timeout=30) as response:
            return response.status in (200, 201)


def _signing_repo_key(repo):
    return str(repo or "").strip().lower()


def _get_signing_state(config, repo):
    states = config.get(SIGNING_STATE_CONFIG_KEY, {})
    if not isinstance(states, dict):
        return {}
    state = states.get(_signing_repo_key(repo), {})
    return state if isinstance(state, dict) else {}


def _save_signing_state(config, repo, public_key_pem):
    repo_key = _signing_repo_key(repo)
    if not repo_key:
        raise ValueError("cannot save an artifact signing key without a repository")
    states = config.get(SIGNING_STATE_CONFIG_KEY, {})
    if not isinstance(states, dict):
        states = {}
    states[repo_key] = {
        "public_key": public_key_pem,
        "secret_name": SIGNING_SECRET_NAME,
        "version": SIGNING_KEY_VERSION,
    }
    config[SIGNING_STATE_CONFIG_KEY] = states
    # Remove the old global state so a key from one fork can never be reused
    # implicitly for another account or explicit --repo target.
    for legacy_key in ("signing_key", "signing_secret_name", "signing_key_version"):
        config.pop(legacy_key, None)
    save_config(config)


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
    return base64.b64encode(private_key_der).decode("ascii"), public_key_pem


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
        return key.public_bytes(
            encoding=serialization.Encoding.PEM,
            format=serialization.PublicFormat.SubjectPublicKeyInfo,
        ).decode("ascii")

    key = RSA.import_key(public_key_pem)
    if key.size_in_bits() < 2048:
        raise ValueError("artifact signing RSA key must be at least 2048 bits")
    return key.publickey().export_key('PEM').decode("ascii")


def ensure_signing_key(client):
    """Ensure the target repo has a private signing secret; retain only its public key."""
    config = load_config()
    external_key = os.environ.get("ABK_SIGNING_KEY")
    state = _get_signing_state(config, client.repo)
    existing = external_key or state.get("public_key")
    initialized = (
        state.get("secret_name") == SIGNING_SECRET_NAME
        and state.get("version") == SIGNING_KEY_VERSION
    )
    if not client.token:
        raise RuntimeError(t("err_no_token"))
    if not client.repo or (
        client.repo == DEFAULT_REPO and not getattr(client, "repo_explicit", False)
    ):
        raise RuntimeError("artifact signing must be configured on a fork or explicit repo")

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
            client.publish_signing_key(existing)
        return existing

    if existing and initialized:
        existing = normalize_signing_public_key(existing)
        if published_key and published_key.strip() != existing.strip():
            # The Android app is the authoritative key manager and may have
            # rotated this fork's material since the CLI last ran.
            existing = published_key
        if not secret_exists:
            raise RuntimeError(
                "the signing public key exists but the private GitHub secret is missing; "
                "regenerate signing material from the ABK app"
            )
        if not published_key:
            client.publish_signing_key(existing)
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
    if not client.create_or_update_secret(SIGNING_SECRET_NAME, private_key_b64):
        raise RuntimeError("GitHub did not accept the artifact signing secret")

    try:
        if not client.publish_signing_key(public_key_pem):
            raise RuntimeError("GitHub did not accept the signing public key asset")
    except Exception:
        client.delete_repository_secret(SIGNING_SECRET_NAME)
        raise

    _save_signing_state(config, client.repo, public_key_pem)
    print(t("signing_key_generated"))
    return public_key_pem


def resolve_verification_key(client):
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
    if not published_key:
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


def make_client(args, token):
    return GitHubClient(
        token=token,
        repo=getattr(args, "repo", None),
        verbose=getattr(args, "verbose", False),
    )


def prepare_build_repository(client, args):
    """Select a writable target repository and configure artifact signing."""
    if getattr(args, "repo", None):
        try:
            ensure_signing_key(client)
            return True
        except Exception as exc:
            print(t("err_fork_failed", error=exc), file=sys.stderr)
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
                    sync = input(t("ask_sync")).strip().lower()
                    if sync in ('y', 'yes'):
                        client.sync_fork()
                        print(t("fork_sync_done"))

        ensure_signing_key(client)
        return True
    except Exception as exc:
        print(t("err_fork_failed", error=exc), file=sys.stderr)
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
    token = device_flow_login()
    if not token:
        return 1

    client = make_client(args, token)
    try:
        user = client.get_user()
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
                ensure_signing_key(client)
                print(t("fork_created_generic"))
        elif fork_status and fork_status.get("needs_sync"):
            print(t("fork_behind_upstream", n=fork_status['behind_by']))
            sync = input(t("ask_sync")).strip().lower()
            if sync in ('y', 'yes'):
                client.sync_fork()
                print(t("fork_sync_done"))
            ensure_signing_key(client)
        elif fork_status and not fork_status.get("needs_fork"):
            print(t("fork_up_to_date"))
            ensure_signing_key(client)
        return 0
    except Exception as exc:
        print(t("login_check_failed", error=exc), file=sys.stderr)
        return 1


def cmd_logout(args):
    if CONFIG_FILE.exists():
        config = load_config()
        if "token" in config:
            del config["token"]
            save_config(config)
            print(t("logged_out_token_removed"))
        else:
            print(t("logout_not"))
    else:
        print(t("logout_not"))
    return 0


def cmd_whoami(args):
    token = get_token(args)
    
    if not token:
        print(t("logout_not"))
        print(t("run_login_hint"))
        return 1
    
    client = make_client(args, token)
    try:
        user = client.get_user()
        print(t("status_user", user=user.get('login', 'Unknown')))
        
        fork = client.get_fork()
        if fork:
            print(f"Fork: {fork.get('full_name')}")
            
            behind = client.check_behind()
            if behind.get("error"):
                print(behind["error"], file=sys.stderr)
                return 1
            elif behind.get("behind_by", 0) > 0:
                print(t("status_behind", n=behind['behind_by']))
            else:
                print(t("status_synced"))
        else:
            print(t("fork_not_detected"))
            print(t("hint_run_fork"))
        return 0
    except Exception as exc:
        print(t("login_verify_failed", error=exc), file=sys.stderr)
        return 1


def cmd_fork(args):
    token = get_token(args)
    
    if not token:
        print(t("err_no_token"), file=sys.stderr)
        return 1
    
    client = make_client(args, token)
    
    try:
        if getattr(args, "repo", None):
            print(t("fork_exists", fork=client.repo))
            ensure_signing_key(client)
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
        ensure_signing_key(client)
        return 0
    except Exception as exc:
        print(t("err_fork_failed", error=exc), file=sys.stderr)
        return 1


def cmd_sync(args):
    token = get_token(args)
    
    if not token:
        print(t("err_no_token"), file=sys.stderr)
        return 1
    
    client = make_client(args, token)
    
    try:
        fork = client.get_fork()
        if not fork:
            print(t("err_no_fork"), file=sys.stderr)
            return 1
        client.repo = fork.get("full_name", client.repo)
        client.fork_repo = fork
        
        behind = client.check_behind()
        if behind.get("error"):
            raise RuntimeError(behind["error"])
        if behind.get("behind_by", 0) == 0:
            print(t("fork_already_latest"))
        else:
            print(t("syncing_n_commits", n=behind['behind_by']))
            client.sync_fork()
            print(t("fork_sync_done"))
        ensure_signing_key(client)
        return 0
    except Exception as exc:
        print(t("err_sync_failed", error=exc), file=sys.stderr)
        return 1


def cmd_status(args):
    token = get_token(args)
    
    if not token:
        print(t("err_no_token"), file=sys.stderr)
        return 1
    
    client = make_client(args, token)

    if not getattr(args, "repo", None):
        try:
            fork = client.get_fork()
            if not fork:
                print(t("err_no_fork"), file=sys.stderr)
                return 1
            client.repo = fork.get("full_name", client.repo)
            client.fork_repo = fork
        except Exception as exc:
            print(t("fetch_status_failed", error=exc), file=sys.stderr)
            return 1

    if args.cancel:
        try:
            client.cancel_run(args.cancel)
            print(t("cancel_ok", id=args.cancel))
        except Exception as exc:
            print(t("cancel_fail", error=exc), file=sys.stderr)
            return 1
        return 0

    if args.rerun:
        try:
            client.rerun(args.rerun)
            print(t("rerun_ok", id=args.rerun))
        except Exception as exc:
            print(t("rerun_fail", error=exc), file=sys.stderr)
            return 1
        return 0

    try:
        if not getattr(args, "repo", None):
            behind = client.check_behind()
            if behind.get("error"):
                print(behind["error"], file=sys.stderr)
            elif behind.get("behind_by", 0) > 0:
                print(t("warn_behind_upstream", n=behind['behind_by']))
                print(t("run_abk_sync"))
                print()

        if args.run_id:
            print_workflow_run(client.get_run(args.run_id))
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
            return 0
        
        print(t("status_recent", n=len(workflow_runs)))
        for run in workflow_runs:
            print_workflow_run(run)
        return 0
    except Exception as exc:
        print(t("fetch_status_failed", error=exc), file=sys.stderr)
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


def _standard_build_inputs(args, variant):
    kpm_enabled = bool(args.kpm) and supports_kpm(variant, args.ksu_branch)
    inputs = {
        "kernelsu_variant": variant,
        "kernelsu_branch": resolve_ksu_branch(args.ksu_branch),
        "use_zram": str(bool(args.zram)).lower(),
        "use_bbg": str(bool(args.bbg)).lower(),
        "use_ddk": str(bool(args.ddk)).lower(),
        "use_kpm": str(kpm_enabled).lower(),
        "use_rekernel": str(bool(args.rekernel)).lower(),
        "cancel_susfs": str(not bool(args.susfs)).lower(),
        "use_ntsync": str(bool(args.ntsync)).lower(),
        "use_networking": str(bool(args.networking)).lower(),
        "zram_full_algo": str(bool(args.zram_full_algo)).lower(),
    }
    if args.virt != "off":
        inputs["virtualization_support"] = args.virt
    if args.version:
        inputs["version"] = args.version
    if args.custom_ref:
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
        "kernelsu_branch": resolve_ksu_branch(args.ksu_branch),
        "version": args.version or "",
        "revision": args.revision or "r11",
        "build_time": args.build_time or "",
        "kpm_password": args.kpm_password if kpm_enabled and args.kpm_password else "",
        "enable_susfs": str(bool(args.susfs)).lower(),
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
    oneplus_kpm_enabled = bool(args.kpm) and "ReSukiSU" not in manager_variants
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
        "build_scope": args.build_scope or "Both",
        "manager_variants": args.manager_variants or "all",
        "kernelsu_branch": resolve_ksu_branch(args.ksu_branch),
        "version": args.version or "",
        "revision": args.revision or "r11",
        "build_time": args.build_time or "",
        "kpm_password": args.kpm_password or "",
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
    result = dict(inputs)
    if result.get("kpm_password"):
        result["kpm_password"] = "***"
    return result


def cmd_build(args):
    if args.matrix and args.oneplus:
        print("--matrix and --oneplus are mutually exclusive", file=sys.stderr)
        return 2

    effective_ksu_branch = resolve_ksu_branch(args.ksu_branch)
    if not args.oneplus:
        if args.matrix in ("full", "all-managers") and (
            args.custom_ref or effective_ksu_branch == "Custom(自定义)"
        ):
            print(
                t("err_custom_ref_unsupported_matrix", matrix=args.matrix),
                file=sys.stderr,
            )
            return 2
        if args.custom_ref and effective_ksu_branch != "Custom(自定义)":
            print(t("err_custom_ref_requires_custom_branch"), file=sys.stderr)
            return 2
        if (
            effective_ksu_branch == "Custom(自定义)"
            and args.ksu_variant != "None"
            and not args.custom_ref
        ):
            print(t("err_custom_branch_requires_ref"), file=sys.stderr)
            return 2

    _set_build_defaults(args)
    device_info = None
    if args.oneplus:
        if not args.device:
            print(t("err_need_device"), file=sys.stderr)
            return 2
        device_info = ONEPLUS_DEVICES.get(args.device)
        if not device_info:
            print(t("err_unknown_device", device=args.device), file=sys.stderr)
            print(
                t("err_available_devices", devices=", ".join(ONEPLUS_DEVICES.keys())),
                file=sys.stderr,
            )
            return 2
        errors, warnings = validate_oneplus_build(args, device_info)
        for warning in warnings:
            print(t("warning_prefix") + " " + warning)
        if errors:
            for error in errors:
                print(t("error_prefix") + " " + error, file=sys.stderr)
            return 2
    elif not args.matrix:
        if not args.sub_level:
            print(t("err_need_sub_level"), file=sys.stderr)
            return 2
        if not args.os_patch_level:
            print(t("err_need_os_patch"), file=sys.stderr)
            return 2

    invalid_argument = invalid_build_argument(args)
    if invalid_argument:
        print(t("err_invalid_build_arg", name=invalid_argument), file=sys.stderr)
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
                selection = f"{variant} ({resolve_ksu_branch(args.ksu_branch)})"
                print(t("warning_prefix") + " " + t("op_no_kpm_ksu", ksu=selection))
            plans.append({
                "workflow": FULL_MATRIX_WORKFLOWS["full"],
                "name": f"{t('build_target_full')} ({variant})",
                "inputs": _full_matrix_inputs(args, variant),
            })
    elif args.matrix == "all-managers":
        manager_variants = selected_manager_variants(args.manager_variants)
        if (
            args.kpm
            and (args.build_scope or "Both") != "GKI"
            and "ReSukiSU" in manager_variants
        ):
            print(
                t("warning_prefix")
                + " "
                + t("op_no_kpm_ksu", ksu="ReSukiSU (OnePlus main)")
            )
        plans.append({
            "workflow": FULL_MATRIX_WORKFLOWS["all-managers"],
            "name": t("build_target_all_managers"),
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
                        print(t("warning_prefix") + " " + t("op_no_kpm_ksu", ksu=selection))
                    inputs = {
                        "ksu_variant": variant,
                        "device_manifest": args.device,
                        "cpu": device_info["cpu"],
                        "android_version": device_info["android"],
                        "kernel_version": device_info["kernel"],
                        "enable_susfs": str(bool(args.susfs)).lower(),
                        "use_kpm": str(kpm_enabled).lower(),
                        "use_lz4kd": str(bool(args.lz4kd)).lower(),
                        "use_bbg": str(bool(args.bbg)).lower(),
                        "use_bbr": str(bool(args.bbr)).lower(),
                        "use_proxy_optimization": str(bool(args.proxy_optimization)).lower(),
                        "use_unicode_bypass": str(bool(args.unicode_bypass)).lower(),
                    }
                else:
                    if args.kpm and not supports_kpm(variant, args.ksu_branch):
                        selection = f"{variant} ({resolve_ksu_branch(args.ksu_branch)})"
                        print(t("warning_prefix") + " " + t("op_no_kpm_ksu", ksu=selection))
                    inputs = _standard_build_inputs(args, variant)
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
                    "inputs": inputs,
                })

    client = None
    if args.dry_run:
        ref = args.ref or "dev"
        repo_name = args.repo or "<auto-fork>"
    else:
        token = get_token(args)
        if not token:
            print(t("err_no_token"), file=sys.stderr)
            return 1
        client = make_client(args, token)
        if not prepare_build_repository(client, args):
            return 1
        ref = args.ref or client.get_default_branch()
        repo_name = client.repo

    failures = 0
    successes = 0
    for index, plan in enumerate(plans, start=1):
        if len(plans) > 1:
            print(f"\n[{index}/{len(plans)}] ", end="")
        print(t("triggering_name", name=plan["name"]))
        plan_kpm_enabled = plan["inputs"].get("use_kpm", str(bool(args.kpm)).lower()) == "true"
        print(
            "  " + t(
                "build_feat_line",
                susfs=t("enabled") if args.susfs else t("disabled"),
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
            continue

        try:
            client.trigger_workflow(plan["workflow"], ref, plan["inputs"])
            print(t("build_triggered_ok"))
            successes += 1
        except Exception as exc:
            failures += 1
            print(t("build_triggered_fail", error=exc), file=sys.stderr)
            if "404" in str(exc):
                print(t("workflow_404_hint"), file=sys.stderr)

    if not args.dry_run and len(plans) > 1 and successes:
        print(t("build_multiple_count", count=successes))
    if not args.dry_run:
        print(t("build_check_status"))
        print(t("build_actions_url", repo=client.repo))
    return 1 if failures else 0


def cmd_artifacts(args):
    if args.set_download_dir:
        config = load_config()
        configured_dir = str(Path(args.set_download_dir).expanduser().resolve())
        config["download_dir"] = configured_dir
        save_config(config)
        print(t("download_dir_saved", dir=configured_dir))
        if not args.run_id and not args.download:
            return 0

    if not args.run_id:
        print(t("err_need_run_id"), file=sys.stderr)
        return 2

    token = get_token(args)
    if not token:
        print(t("err_no_token"), file=sys.stderr)
        return 1
    client = make_client(args, token)

    if not getattr(args, "repo", None):
        try:
            fork = client.get_fork()
            if not fork:
                print(t("err_no_fork"), file=sys.stderr)
                return 1
            client.repo = fork.get("full_name", client.repo)
            client.fork_repo = fork
        except Exception as exc:
            print(f"Artifact operation failed: {exc}", file=sys.stderr)
            return 1

    try:
        artifacts = client.list_artifacts(args.run_id)
        if not artifacts.get("artifacts"):
            print(t("artifacts_no_artifacts"))
            return 0

        print(t("artifacts_list", id=args.run_id))
        for art in artifacts["artifacts"]:
            size_kb = art["size_in_bytes"] / 1024
            print(f"  {art['id']} | {art['name']} | {size_kb:.1f} KB")

        if args.download:
            config = load_config()
            output_dir = Path(
                args.output or config.get("download_dir") or default_download_dir()
            ).expanduser()
            Path(output_dir).mkdir(parents=True, exist_ok=True)
            print(f"\n" + t("artifacts_download_to", dir=output_dir))
            signing_key = resolve_verification_key(client)
            failures = 0
            for art in artifacts["artifacts"]:
                print(f"  " + t("artifacts_downloading", name=art["name"]))
                try:
                    path = client.download_artifact(art["id"], output_dir)
                except Exception as exc:
                    failures += 1
                    print(f"    download failed: {exc}", file=sys.stderr)
                    continue

                print(f"    -> {path}")
                print(f"    " + t("artifact_verifying"))
                result = verify_artifact_archive(path, signing_key, args.run_id)
                for bundle in result.get("bundles", []):
                    label = bundle.get("bundle", Path(path).name)
                    icon = "✓" if bundle["verified"] else "⚠"
                    print(f"    {icon} {label}: {bundle['message']}")
                if not result.get("bundles"):
                    print(f"    ⚠ {result['message']}")

                if not result['verified']:
                    failures += 1
                    sys.stdout.write("    " + t("artifact_verify_confirm"))
                    sys.stdout.flush()
                    answer = sys.stdin.readline().strip().lower()
                    if answer not in ('y', 'yes', 'j', 'ja', 'o', 'oui', 's', 'si', 'sí'):
                        try:
                            Path(path).unlink()
                        except FileNotFoundError:
                            pass
                        print(f"    {t('artifact_verify_skip_user')}")
            if failures:
                return 1
        return 0
    except Exception as exc:
        print(f"Artifact operation failed: {exc}", file=sys.stderr)
        return 1


def cmd_list(args):
    if args.oneplus:
        print(t("op_list_title"))
        for did, info in ONEPLUS_DEVICES.items():
            print(f"  {did:<35} {info['name']:<20} {info['cpu']:<10} {info['android']} {info['kernel']}")
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
    print("  Stable / Dev / Custom")

    print(f"\n{t('op_features_title')}")
    print(f"  --[no-]lz4kd  --[no-]bbr  --[no-]proxy-optimization  --[no-]unicode-bypass")

    print(f"\n{t('features_title')}")
    print(f"  --[no-]zram  --[no-]bbg  --[no-]ddk  --[no-]kpm")
    print(f"  --[no-]susfs  --[no-]rekernel  --[no-]ntsync  --[no-]networking")
    print(f"  --[no-]oneplus-8e  --[no-]zram-full-algo  --zram-extra-algos")

    print(f"\n{t('commands_label')}")
    cmds = [("login", "cmd_login_help"),("logout", "cmd_logout_help"),("whoami", "cmd_whoami_help"),
            ("fork", "cmd_fork_help"),("sync", "cmd_sync_help"),("build", "cmd_build_help"),
            ("status", "cmd_status_help"),("artifacts", "cmd_artifacts_help"),("list", "cmd_list_help")]
    for cmd, key in cmds:
        print(f"  abk {cmd:<12} {t(key)}")

    print("\n  abk build --help | abk status --help")
    return 0


SUPPORTED_LANGUAGES = (
    "zh-cn", "en-us", "ru-ru", "ja-jp", "ko-kr", "hi-in", "de-de",
    "fr-fr", "es-es", "pt-br", "jp-neko", "zh-neko", "eo", "zh-zako",
)


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


def requested_language(argv):
    for index, arg in enumerate(argv):
        if arg == "--lang" and index + 1 < len(argv):
            return argv[index + 1]
        if arg.startswith("--lang="):
            return arg.split("=", 1)[1]
    return None


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


def main():
    # 提前检测 --lang 以确保帮助文本使用正确语言
    early_language = requested_language(sys.argv[1:])
    if early_language in SUPPORTED_LANGUAGES:
        load_translations(early_language)
        refresh_workflow_names()
        config = load_config()
        if config.get("lang") != early_language:
            config["lang"] = early_language
            save_config(config)
    
    parser = argparse.ArgumentParser(
        prog="abk",
        description=t("abk_cli_desc_full"),
        formatter_class=argparse.RawDescriptionHelpFormatter,
        add_help=False)
    parser.add_argument("-h", "--help", action="help", default=argparse.SUPPRESS,
        help=t("help_flag"))
    parser.add_argument("--token", help=t("help_token"))
    parser.add_argument("--repo", type=repo_slug, help=t("help_repo"))
    parser.add_argument("--verbose", "-v", action="store_true", help=t("help_verbose"))
    parser.add_argument("--lang", choices=SUPPORTED_LANGUAGES, help=t("help_lang"))

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
    build_parser.add_argument("--ksu-branch", choices=["Stable","Dev","Custom"], help=t("arg_ksu_branch"))
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
    run_action.add_argument("--run-id", type=int, help=t("arg_run_id_status"))
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
    run_action.add_argument("--cancel", type=int, metavar="RUN_ID", help=t("arg_cancel"))
    run_action.add_argument("--rerun", type=int, metavar="RUN_ID", help=t("arg_rerun"))
    status_parser.set_defaults(func=cmd_status)

    # artifacts
    artifacts_parser = subparsers.add_parser("artifacts", 
        help=t("cmd_artifacts_help"),
        description=t("cmd_artifacts_desc"))
    artifacts_parser.add_argument("--run-id", type=int, help=t("arg_run_id"))
    artifacts_parser.add_argument("--download", action="store_true", help=t("arg_download"))
    artifacts_parser.add_argument(
        "--output",
        "-o",
        help=t("arg_output", dir=default_download_dir()),
    )
    artifacts_parser.add_argument("--set-download-dir", metavar="DIR", help=t("arg_set_download_dir"))
    artifacts_parser.set_defaults(func=cmd_artifacts)

    # list
    list_parser = subparsers.add_parser("list", 
        help=t("cmd_list_help"),
        description=t("cmd_list_desc"))
    list_parser.add_argument("--oneplus", action="store_true", help=t("arg_oneplus"))
    list_parser.set_defaults(func=cmd_list)

    args = parser.parse_args()
    if args.lang:
        load_translations(args.lang)
        refresh_workflow_names()

    if not args.command:
        parser.print_help()
        return 0

    return args.func(args) or 0


if __name__ == "__main__":
    configure_stdio()
    sys.exit(main())
