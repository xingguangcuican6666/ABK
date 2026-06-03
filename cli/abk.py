#!/usr/bin/env python3

import argparse
import json
import os
import sys
import time
import webbrowser
from pathlib import Path
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode

sys.path.insert(0, str(Path(__file__).parent))
from i18n import t, load_translations, detect_language


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
CONFIG_DIR = Path.home() / ".config" / "abk"
CONFIG_FILE = CONFIG_DIR / "config.json"
CLIENT_ID_FALLBACK = "Ov23li8skGo6AFPBeSTh"

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
        
        ksu = args.ksu_variant or "ReSukiSU"
        if ksu not in ("SukiSU", "ReSukiSU"):
            if args.kpm:
                args.kpm = False
                warnings.append(t("op_no_kpm_ksu", ksu=ksu))
    
    return errors, warnings


def load_config():
    if CONFIG_FILE.exists():
        try:
            return json.loads(CONFIG_FILE.read_text())
        except json.JSONDecodeError:
            pass
    return {}


def save_config(config):
    CONFIG_DIR.mkdir(parents=True, exist_ok=True)
    CONFIG_FILE.write_text(json.dumps(config, indent=2, ensure_ascii=False))


def get_client_id():
    config = load_config()
    return config.get("client_id") or os.environ.get("ABK_CLIENT_ID") or CLIENT_ID_FALLBACK


def request_device_code():
    client_id = get_client_id()
    data = urlencode({
        "client_id": client_id,
        "scope": "repo workflow"
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
        with urlopen(req) as resp:
            return json.loads(resp.read())
    except Exception as e:
        print(t("err_req_failed_with_error", error=e), file=sys.stderr)
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
        with urlopen(req) as resp:
            result = json.loads(resp.read())
    except HTTPError as e:
        return {"success": False, "error": f"http_{e.code}"}
    except Exception as e:
        return {"success": False, "error": str(e)}
    
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
    
    if not result:
        print(t("err_req_failed"), file=sys.stderr)
        return None
    
    device_code = result["device_code"]
    user_code = result["user_code"]
    verification_uri = result["verification_uri"]
    interval = result.get("interval", 5)
    expires_in = result.get("expires_in", 900)
    
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
        webbrowser.open(verification_uri)
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
            elif error and not error.startswith("http"):
                print(f"\n{t('err_auth_failed', error=error)}", file=sys.stderr)
                return None
    except KeyboardInterrupt:
        print(f"\n{t('login_cancelled')}")
        return None
    
    print(f"\n{t('err_auth_timeout')}", file=sys.stderr)
    return None


class GitHubClient:
    def __init__(self, token=None, repo=None):
        config = load_config()
        self.token = (
            token 
            or os.environ.get("GITHUB_TOKEN") 
            or os.environ.get("GH_TOKEN")
            or config.get("token")
        )
        self.repo = repo
        self.username = None
        self.fork_repo = None
        
        if self.token:
            self._detect_user()
        
        if not self.repo:
            if self.fork_repo:
                self.repo = self.fork_repo.get("full_name")
            else:
                self.repo = os.environ.get("ABK_REPO", DEFAULT_REPO)

    def _detect_user(self):
        try:
            user = self.get("/user")
            self.username = user.get("login")
            fork = self.get_fork()
            if fork:
                self.fork_repo = fork
        except Exception:
            pass

    def get_default_branch(self):
        try:
            repo_info = self.get(f"/repos/{self.repo}")
            return repo_info.get("default_branch", "dev")
        except Exception:
            return "dev"

    def _request(self, method, path, data=None):
        url = f"{GITHUB_API}{path}" if not path.startswith("http") else path
        headers = {
            "Authorization": f"token {self.token}",
            "Accept": "application/vnd.github.v3+json",
            "User-Agent": "ABK-CLI",
        }
        if data:
            headers["Content-Type"] = "application/json"
            data = json.dumps(data).encode()

        req = Request(url, data=data, headers=headers, method=method)
        try:
            with urlopen(req) as resp:
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
            raise Exception(t("err_api_error", code=e.code, msg=msg))
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
        except Exception:
            pass
        return None

    def create_fork(self, owner=None, repo=None):
        owner = owner or SOURCE_REPO_OWNER
        repo = repo or SOURCE_REPO_NAME
        return self.post(f"/repos/{owner}/{repo}/forks")

    def check_behind(self, fork_owner=None, fork_repo=None, upstream_owner=None, upstream_repo=None):
        fork_owner = fork_owner or self.username
        fork_repo = fork_repo or SOURCE_REPO_NAME
        upstream_owner = upstream_owner or SOURCE_REPO_OWNER
        upstream_repo = upstream_repo or SOURCE_REPO_NAME
        
        try:
            result = self.get(f"/repos/{upstream_owner}/{upstream_repo}/compare/main...{fork_owner}:main")
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
        owner = owner or self.username
        repo = repo or SOURCE_REPO_NAME
        return self.put(f"/repos/{owner}/{repo}/merge-upstream", {"branch": branch})

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
            "Authorization": f"token {self.token}",
            "Accept": "application/vnd.github.v3+json",
            "User-Agent": "ABK-CLI",
        }
        req = Request(url, headers=headers)
        try:
            with urlopen(req) as resp:
                content = resp.read()
                filename = f"artifact-{artifact_id}.zip"
                output_path = Path(output_dir) / filename
                output_path.write_bytes(content)
                return str(output_path)
        except HTTPError as e:
            if e.code == 302:
                redirect_url = e.headers.get("Location")
                if redirect_url:
                    with urlopen(redirect_url) as resp:
                        content = resp.read()
                        filename = f"artifact-{artifact_id}.zip"
                        output_path = Path(output_dir) / filename
                        output_path.write_bytes(content)
                        return str(output_path)
            return None

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
            "needs_sync": behind.get("behind_by", 0) > 0
        }


def cmd_login(args):
    token = device_flow_login()
    if token:
        config = load_config()
        config["token"] = token
        save_config(config)
        print()
        print(t("token_saved_to", path=CONFIG_FILE))
        
        client = GitHubClient(token=token)
        try:
            user = client.get_user()
            print(t("logged_in_as", user=user.get('login', 'Unknown')))
            
            print(t("checking_fork"))
            fork_status = client.check_and_prompt_sync()
            
            if fork_status and fork_status.get("needs_fork"):
                create = input(t("ask_create_fork")).strip().lower()
                if create == 'y':
                    client.create_fork()
                    print(t("fork_created_generic"))
            elif fork_status and fork_status.get("needs_sync"):
                print(t("fork_behind_upstream", n=fork_status['behind_by']))
                sync = input(t("ask_sync")).strip().lower()
                if sync == 'y':
                    client.sync_fork()
                    print(t("fork_sync_done"))
            elif fork_status and not fork_status.get("needs_fork"):
                print(t("fork_up_to_date"))
        except Exception as e:
            print(t("login_verify_failed", error=e), file=sys.stderr)


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


def cmd_whoami(args):
    config = load_config()
    token = (
        args.token 
        or os.environ.get("GITHUB_TOKEN") 
        or os.environ.get("GH_TOKEN")
        or config.get("token")
    )
    
    if not token:
        print(t("logout_not"))
        print(t("run_login_hint"))
        return
    
    client = GitHubClient(token=token)
    try:
        user = client.get_user()
        print(t("status_user", user=user.get('login', 'Unknown')))
        
        fork = client.get_fork()
        if fork:
            print(f"Fork: {fork.get('full_name')}")
            
            behind = client.check_behind()
            if behind.get("behind_by", 0) > 0:
                print(t("status_behind", n=behind['behind_by']))
            else:
                print(t("status_synced"))
        else:
            print(t("fork_not_detected"))
            print(t("hint_run_fork"))
    except Exception as e:
        print(t("login_verify_failed", error=e), file=sys.stderr)


def cmd_fork(args):
    config = load_config()
    token = (
        args.token 
        or os.environ.get("GITHUB_TOKEN") 
        or os.environ.get("GH_TOKEN")
        or config.get("token")
    )
    
    if not token:
        print(t("err_no_token"), file=sys.stderr)
        sys.exit(1)
    
    client = GitHubClient(token=token)
    
    try:
        fork = client.get_fork()
        if fork:
            print(t("fork_exists", fork=fork.get('full_name')))
            
            behind = client.check_behind()
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
            print(t("fork_created", fork=result.get('full_name')))
    except Exception as e:
        print(t("err_fork_failed", error=e), file=sys.stderr)
        sys.exit(1)


def cmd_sync(args):
    config = load_config()
    token = (
        args.token 
        or os.environ.get("GITHUB_TOKEN") 
        or os.environ.get("GH_TOKEN")
        or config.get("token")
    )
    
    if not token:
        print(t("err_no_token"), file=sys.stderr)
        sys.exit(1)
    
    client = GitHubClient(token=token)
    
    try:
        fork = client.get_fork()
        if not fork:
            print(t("err_no_fork"), file=sys.stderr)
            sys.exit(1)
        
        behind = client.check_behind()
        if behind.get("behind_by", 0) == 0:
            print(t("fork_already_latest"))
            return
        
        print(t("syncing_n_commits", n=behind['behind_by']))
        client.sync_fork()
        print(t("fork_sync_done"))
    except Exception as e:
        print(t("err_sync_failed", error=e), file=sys.stderr)
        sys.exit(1)


def cmd_status(args):
    config = load_config()
    token = (
        args.token 
        or os.environ.get("GITHUB_TOKEN") 
        or os.environ.get("GH_TOKEN")
        or config.get("token")
    )
    
    if not token:
        print(t("err_no_token"), file=sys.stderr)
        sys.exit(1)
    
    client = GitHubClient(token=token)

    if args.cancel:
        try:
            client.cancel_run(args.cancel)
            print(t("cancel_ok", id=args.cancel))
        except Exception as e:
            print(t("cancel_fail", error=e), file=sys.stderr)
        return

    if args.rerun:
        try:
            client.rerun(args.rerun)
            print(t("rerun_ok", id=args.rerun))
        except Exception as e:
            print(t("rerun_fail", error=e), file=sys.stderr)
        return
    
    client = GitHubClient(token=token)
    
    try:
        fork = client.get_fork()
        if not fork:
            print(t("err_no_fork"))
            return
        
        behind = client.check_behind()
        if behind.get("behind_by", 0) > 0:
            print(t("warn_behind_upstream", n=behind['behind_by']))
            print(t("run_abk_sync"))
            print()
        
        runs = client.list_runs(per_page=args.limit)
        workflow_runs = runs.get("workflow_runs", [])
        
        if not workflow_runs:
            print(t("status_no_builds"))
            return
        
        print(t("status_recent", n=len(workflow_runs)))
        for run in workflow_runs:
            status_icon = "✓" if run.get("conclusion") == "success" else "✗" if run.get("conclusion") == "failure" else "…" if run["status"] == "in_progress" else "○"
            created = run["created_at"][:19].replace("T", " ")
            print(f"  {status_icon} #{run['id']} | {run.get('name', '')} | {created}")
    except Exception as e:
        print(t("fetch_status_failed", error=e), file=sys.stderr)


def cmd_build(args):
    config = load_config()
    token = (
        args.token 
        or os.environ.get("GITHUB_TOKEN") 
        or os.environ.get("GH_TOKEN")
        or config.get("token")
    )
    
    if not token:
        print(t("err_no_token"), file=sys.stderr)
        sys.exit(1)
    
    client = GitHubClient(token=token)
    
    # 处理特殊全量工作流
    if args.matrix in ("full", "all-managers"):
        wf_file = FULL_MATRIX_WORKFLOWS[args.matrix]
        
        if args.matrix == "full":
            name = t("build_target_full")
            inputs = {
                "kernelsu_variant": args.ksu_variant or "ReSukiSU",
                "kernelsu_branch": resolve_ksu_branch(args.ksu_branch),
                "version": args.version or "",
                "revision": args.revision or "r11",
                "kpm_password": args.kpm_password or "",
                "enable_susfs": str(args.susfs).lower(),
                "use_zram": str(args.zram).lower(),
                "use_bbg": str(args.bbg).lower(),
                "use_ddk": str(args.ddk).lower(),
                "use_kpm": str(args.kpm).lower(),
                "use_rekernel": str(args.rekernel).lower(),
                "use_ntsync": str(args.ntsync).lower(),
                "use_networking": str(args.networking).lower(),
                "zram_full_algo": str(args.zram_full_algo).lower(),
                "zram_extra_algos": args.zram_extra_algos or "",
            }
        else:
            name = t("build_target_all_managers")
            inputs = {
                "build_scope": args.build_scope or "Both",
                "manager_variants": args.manager_variants or "all",
                "kernelsu_branch": resolve_ksu_branch(args.ksu_branch),
                "version": args.version or "",
                "revision": args.revision or "r11",
                "kpm_password": args.kpm_password or "",
                "enable_susfs": str(args.susfs).lower(),
                "use_zram": str(args.zram).lower(),
                "use_bbg": str(args.bbg).lower(),
                "use_ddk": str(args.ddk).lower(),
                "use_kpm": str(args.kpm).lower(),
                "use_rekernel": str(args.rekernel).lower(),
                "use_ntsync": str(args.ntsync).lower(),
                "use_networking": str(args.networking).lower(),
                "zram_full_algo": str(args.zram_full_algo).lower(),
                "zram_extra_algos": args.zram_extra_algos or "",
            }
        
        ref = args.ref or client.get_default_branch()
        print(t("triggering_name", name=name))
        if args.dry_run:
            print(f"  " + t("dry_run_skip"))
        else:
            try:
                client.trigger_workflow(wf_file, ref, inputs)
                print(f"  " + t("triggered_ok"))
            except Exception as e:
                print(t("build_triggered_fail", error=e))
                if "404" in str(e):
                    print(t("workflow_404_hint"))
        print(t("build_check_status"))
        return
    
    if args.oneplus:
        if not args.device:
            print(t("err_need_device"), file=sys.stderr)
            sys.exit(1)
        
        device_info = ONEPLUS_DEVICES.get(args.device)
        if not device_info:
            print(t("err_unknown_device", device=args.device), file=sys.stderr)
            print(t("err_available_devices", devices=", ".join(ONEPLUS_DEVICES.keys())), file=sys.stderr)
            sys.exit(1)
        
        errors, warnings = validate_oneplus_build(args, device_info)
        for w in warnings:
            print(t("warning_prefix") + " " + t(w))
        if errors:
            for e in errors:
                print(t("error_prefix") + " " + t(e), file=sys.stderr)
            sys.exit(1)

    # 确定矩阵目标列表
    if args.matrix:
        if args.matrix == "both":
            matrix_targets = MATRIX_TARGETS
        else:
            matrix_targets = [args.matrix]
    elif args.oneplus:
        matrix_targets = ["oneplus"]
    else:
        matrix_targets = ["custom"]
    
    # 确定 KSU 变体列表
    if args.ksu_variant == "all":
        ksu_variants = KSU_ALL_VARIANTS
    else:
        ksu_variants = [args.ksu_variant or "ReSukiSU"]
    
    # 检查 fork
    try:
        fork = client.get_fork()
        if not fork:
            print(t("fork_no_detect_creating"))
            client.create_fork()
            print(t("fork_created_generic"))
        else:
            behind = client.check_behind()
            if behind.get("behind_by", 0) > 0:
                print(t("warn_behind_upstream", n=behind['behind_by']))
                if not args.force:
                    sync = input(t("ask_sync")).strip().lower()
                    if sync == 'y':
                        client.sync_fork()
                        print(t("fork_sync_done"))
    except Exception as e:
        print(t("err_fork_failed", error=e), file=sys.stderr)
        if not args.force:
            sys.exit(1)
    
    total = len(matrix_targets) * len(ksu_variants)
    count = 0
    
    for tk in matrix_targets:
        for kv in ksu_variants:
            count += 1
            if total > 1:
                print(f"\n[{count}/{total}] ", end="")
            
            if tk == "oneplus":
                workflow = WORKFLOWS["oneplus"]
                if not args.device:
                    print(t("err_need_device"), file=sys.stderr)
                    sys.exit(1)
            elif tk == "custom":
                workflow = WORKFLOWS["custom"]
                if not args.sub_level:
                    print(t("err_need_sub_level"), file=sys.stderr)
                    sys.exit(1)
                if not args.os_patch_level:
                    print(t("err_need_os_patch"), file=sys.stderr)
                    sys.exit(1)
            else:
                workflow = WORKFLOWS[tk]
            
            inputs = {
                "kernelsu_variant": kv,
                "kernelsu_branch": resolve_ksu_branch(args.ksu_branch),
                "use_zram": str(args.zram).lower(),
                "use_bbg": str(args.bbg).lower(),
                "use_ddk": str(args.ddk).lower(),
                "use_kpm": str(args.kpm).lower(),
                "use_rekernel": str(args.rekernel).lower(),
                "cancel_susfs": str(not args.susfs).lower(),
                "use_ntsync": str(args.ntsync).lower(),
                "use_networking": str(args.networking).lower(),
                "zram_full_algo": str(args.zram_full_algo).lower(),
            }
            
            if tk == "custom":
                inputs["supp_op"] = str(args.oneplus_8e).lower()
                inputs["android_version"] = args.android_version or "android12"
                inputs["kernel_version"] = args.kernel_version or "5.10"
                inputs["sub_level"] = args.sub_level
                inputs["os_patch_level"] = args.os_patch_level
                if args.revision:
                    inputs["revision"] = args.revision
            elif tk == "oneplus":
                inputs["ksu_variant"] = kv
                inputs["device_manifest"] = args.device
                inputs["cpu"] = device_info["cpu"]
                inputs["android_version"] = device_info["android"]
                inputs["kernel_version"] = device_info["kernel"]
                inputs["enable_susfs"] = str(args.susfs).lower()
                inputs["use_kpm"] = str(args.kpm).lower()
                inputs["use_lz4kd"] = str(args.lz4kd).lower()
                inputs["use_bbg"] = str(args.bbg).lower()
                inputs["use_bbr"] = str(args.bbr).lower()
                inputs["use_proxy_optimization"] = str(args.proxy_optimization).lower()
                inputs["use_unicode_bypass"] = str(args.unicode_bypass).lower()
                inputs.pop("kernelsu_variant", None)
                inputs.pop("kernelsu_branch", None)
                inputs.pop("cancel_susfs", None)
                inputs.pop("supp_op", None)
                inputs.pop("use_zram", None)
                inputs.pop("use_ddk", None)
                inputs.pop("use_ntsync", None)
                inputs.pop("use_networking", None)
                inputs.pop("use_rekernel", None)
                inputs.pop("zram_full_algo", None)
            elif not args.matrix or args.matrix == "both":
                pass
            
            if tk != "oneplus":
                if args.virt and args.virt != "off":
                    inputs["virtualization_support"] = args.virt
                if args.version:
                    inputs["version"] = args.version
                if args.custom_ref:
                    inputs["custom_ref"] = args.custom_ref
                if args.kpm_password:
                    inputs["kpm_password"] = args.kpm_password
                if args.zram_extra_algos:
                    inputs["zram_extra_algos"] = args.zram_extra_algos
                if args.build_time:
                    inputs["build_time"] = args.build_time
                if args.custom_modules:
                    inputs["use_custom_external_modules"] = "true"
                    inputs["custom_external_modules"] = args.custom_modules
            
            ref = args.ref or client.get_default_branch()
            print(t("triggering_name", name=f"{workflow['name']} ({kv})"))
            print(f"  " + t("build_feat_line", susfs=t("enabled") if args.susfs else t("disabled"), zram=t("enabled") if args.zram else t("disabled"), bbg=t("enabled") if args.bbg else t("disabled"), ddk=t("enabled") if args.ddk else t("disabled"), kpm=t("enabled") if args.kpm else t("disabled"), rekernel=t("enabled") if args.rekernel else t("disabled"), ntsync=t("enabled") if args.ntsync else t("disabled"), networking=t("enabled") if args.networking else t("disabled")))
            
            if args.dry_run:
                print(f"  " + t("build_triggered_dry"))
            else:
                try:
                    client.trigger_workflow(workflow["file"], ref, inputs)
                    print(t("build_triggered_ok"))
                except Exception as e:
                    print(t("build_triggered_fail", error=e))
                    if "404" in str(e):
                        print(t("workflow_404_hint"))
    
    if total > 1:
        print(t("build_multiple_count", count=count))
    print(t("build_check_status"))
    print(t("build_actions_url", repo=client.repo))


def cmd_artifacts(args):
    config = load_config()
    token = (
        args.token 
        or os.environ.get("GITHUB_TOKEN") 
        or os.environ.get("GH_TOKEN")
        or config.get("token")
    )
    
    if not token:
        print(t("err_no_token"), file=sys.stderr)
        sys.exit(1)
    
    client = GitHubClient(token=token)

    if not args.run_id:
        print(t("err_need_run_id"), file=sys.stderr)
        sys.exit(1)

    try:
        artifacts = client.list_artifacts(args.run_id)
        if not artifacts.get("artifacts"):
            print(t("artifacts_no_artifacts"))
            return

        print(t("artifacts_list", id=args.run_id))
        for art in artifacts["artifacts"]:
            size_kb = art["size_in_bytes"] / 1024
            print(f"  {art['id']} | {art['name']} | {size_kb:.1f} KB")

        if args.download:
            output_dir = args.output or "."
            Path(output_dir).mkdir(parents=True, exist_ok=True)
            print(f"\n" + t("artifacts_download_to", dir=output_dir))
            for art in artifacts["artifacts"]:
                print(f"  " + t("artifacts_downloading", name=art["name"]))
                path = client.download_artifact(art["id"], output_dir)
                if path:
                    print(f"    -> {path}")
    except Exception as e:
        print(t("err_fork_failed", error=e), file=sys.stderr)


def cmd_list(args):
    if args.oneplus:
        print(t("op_list_title"))
        for did, info in ONEPLUS_DEVICES.items():
            print(f"  {did:<35} {info['name']:<20} {info['cpu']:<10} {info['android']} {info['kernel']}")
        return

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

    print(f"\n{t('ksu_branches_label')}")

    print(f"\n{t('op_features_title')}")
    print(f"  --lz4kd  --bbr  --proxy-optimization  --unicode-bypass")

    print(f"\n{t('features_title')}")
    print(f"  --[no-]zram  --[no-]bbg  --[no-]ddk  --[no-]kpm")
    print(f"  --[no-]susfs  --[no-]rekernel  --[no-]ntsync  --[no-]networking")
    print(f"  --oneplus-8e  --zram-full-algo  --zram-extra-algos")

    print(f"\n{t('commands_label')}")
    cmds = [("login", "cmd_login_help"),("logout", "cmd_logout_help"),("whoami", "cmd_whoami_help"),
            ("fork", "cmd_fork_help"),("sync", "cmd_sync_help"),("build", "cmd_build_help"),
            ("status", "cmd_status_help"),("artifacts", "cmd_artifacts_help"),("list", "cmd_list_help")]
    for cmd, key in cmds:
        print(f"  abk {cmd:<12} {t(key)}")

    print(f"\n更多: abk build --help | abk status --help")


def main():
    # 提前检测 --lang 以确保帮助文本使用正确语言
    if "--lang" in sys.argv:
        idx = sys.argv.index("--lang")
        if idx + 1 < len(sys.argv) and sys.argv[idx + 1] in ("zh-cn", "en-us", "ru-ru", "ja-jp", "ko-kr", "hi-in", "de-de", "fr-fr", "es-es", "pt-br", "jp-neko", "zh-neko", "eo", "zh-zako"):
            load_translations(sys.argv[idx + 1])
    
    parser = argparse.ArgumentParser(
        prog="abk",
        description=t("abk_cli_desc_full"),
        formatter_class=argparse.RawDescriptionHelpFormatter,
        add_help=False)
    parser.add_argument("-h", "--help", action="help", default=argparse.SUPPRESS,
        help=t("help_flag"))
    parser.add_argument("--token", help=t("help_token"))
    parser.add_argument("--repo", help=t("help_repo"))
    parser.add_argument("--verbose", "-v", action="store_true", help=t("help_verbose"))
    parser.add_argument("--lang", choices=["zh-cn", "en-us", "ru-ru", "ja-jp", "ko-kr", "hi-in", "de-de", "fr-fr", "es-es", "pt-br", "jp-neko", "zh-neko", "eo", "zh-zako"], help=t("help_lang"))

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
    build_parser.add_argument("--matrix", choices=MATRIX_TARGETS_ALL, help=t("arg_matrix"))
    build_parser.add_argument("--oneplus", action="store_true", help=t("arg_oneplus"))
    build_parser.add_argument("--ref", help=t("arg_ref"))
    build_parser.add_argument("--ksu", dest="ksu_variant", choices=KSU_VARIANTS + ["all"], help=t("arg_ksu"))
    build_parser.add_argument("--ksu-branch", choices=["Stable","Dev","Custom"], help=t("arg_ksu_branch"))
    build_parser.add_argument("--custom-ref", help=t("arg_custom_ref"))
    build_parser.add_argument("--version", help=t("arg_version"))
    build_parser.add_argument("--device", help=t("arg_device"))
    build_parser.add_argument("--virt", choices=VIRT_OPTIONS, default="off", help=t("arg_virt"))
    build_parser.add_argument("--kpm-password", help=t("arg_kpm_password"))
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
    
    build_parser.add_argument("--zram", action="store_true", default=False, help=t("arg_zram"))
    build_parser.add_argument("--no-zram", dest="zram", action="store_false", help=t("arg_no_zram"))
    build_parser.add_argument("--bbg", action="store_true", default=False, help=t("arg_bbg"))
    build_parser.add_argument("--no-bbg", dest="bbg", action="store_false", help=t("arg_no_bbg"))
    build_parser.add_argument("--ddk", action="store_true", default=False, help=t("arg_ddk"))
    build_parser.add_argument("--no-ddk", dest="ddk", action="store_false", help=t("arg_no_ddk"))
    build_parser.add_argument("--kpm", action="store_true", default=False, help=t("arg_kpm_flag"))
    build_parser.add_argument("--no-kpm", dest="kpm", action="store_false", help=t("arg_no_kpm_flag"))
    build_parser.add_argument("--susfs", action="store_true", default=True, help=t("arg_susfs"))
    build_parser.add_argument("--no-susfs", dest="susfs", action="store_false", help=t("arg_no_susfs"))
    build_parser.add_argument("--rekernel", action="store_true", default=False, help=t("arg_rekernel"))
    build_parser.add_argument("--no-rekernel", dest="rekernel", action="store_false", help=t("arg_no_rekernel"))
    build_parser.add_argument("--oneplus-8e", action="store_true", default=False, help=t("arg_oneplus_8e"))
    build_parser.add_argument("--lz4kd", action="store_true", help=t("arg_lz4kd"))
    build_parser.add_argument("--bbr", action="store_true", help=t("arg_bbr"))
    build_parser.add_argument("--proxy-optimization", action="store_true", help=t("arg_proxy"))
    build_parser.add_argument("--unicode-bypass", action="store_true", help=t("arg_unicode"))
    build_parser.add_argument("--ntsync", action="store_true", default=False, help=t("arg_ntsync"))
    build_parser.add_argument("--networking", action="store_true", default=False, help=t("arg_networking"))
    build_parser.add_argument("--zram-full-algo", action="store_true", default=False, help=t("arg_zram_full_algo"))
    build_parser.add_argument("--zram-extra-algos", help=t("arg_zram_extra_algos"))
    build_parser.add_argument("--custom-modules", help=t("arg_custom_modules"))
    build_parser.set_defaults(func=cmd_build)

    # status
    status_parser = subparsers.add_parser("status", 
        help=t("cmd_status_help"),
        description=t("cmd_status_desc"))
    status_parser.add_argument("--run-id", type=int, help=t("arg_run_id_status"))
    status_parser.add_argument("--target", choices=WORKFLOWS.keys(), help=t("arg_target"))
    status_parser.add_argument("--status", choices=["all", "queued", "in_progress", "completed"], default="all", help=t("arg_status_filter"))
    status_parser.add_argument("--limit", type=int, default=10, help=t("arg_limit"))
    status_parser.add_argument("--cancel", type=int, metavar="RUN_ID", help=t("arg_cancel"))
    status_parser.add_argument("--rerun", type=int, metavar="RUN_ID", help=t("arg_rerun"))
    status_parser.set_defaults(func=cmd_status)

    # artifacts
    artifacts_parser = subparsers.add_parser("artifacts", 
        help=t("cmd_artifacts_help"),
        description=t("cmd_artifacts_desc"))
    artifacts_parser.add_argument("--run-id", type=int, help=t("arg_run_id"))
    artifacts_parser.add_argument("--download", action="store_true", help=t("arg_download"))
    artifacts_parser.add_argument("--output", "-o", help=t("arg_output"))
    artifacts_parser.set_defaults(func=cmd_artifacts)

    # list
    list_parser = subparsers.add_parser("list", 
        help=t("cmd_list_help"),
        description=t("cmd_list_desc"))
    list_parser.add_argument("--oneplus", action="store_true", help="列出 OnePlus 设备")
    list_parser.set_defaults(func=cmd_list)

    args = parser.parse_args()
    if args.lang:
        load_translations(args.lang)
        config = load_config()
        config["lang"] = args.lang
        save_config(config)

    if not args.command:
        parser.print_help()
        sys.exit(0)

    args.func(args)


if __name__ == "__main__":
    configure_stdio()
    main()
