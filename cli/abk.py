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
    "a12": {"file": "kernel-a12-5-10.yml", "name": "Android 12 (5.10)", "android": "android12", "kernel": "5.10"},
    "a13": {"file": "kernel-a13-5-15.yml", "name": "Android 13 (5.15)", "android": "android13", "kernel": "5.15"},
    "a14": {"file": "kernel-a14-6-1.yml", "name": "Android 14 (6.1)", "android": "android14", "kernel": "6.1"},
    "a15": {"file": "kernel-a15-6-6.yml", "name": "Android 15 (6.6)", "android": "android15", "kernel": "6.6"},
    "a16": {"file": "kernel-a16-6-12.yml", "name": "Android 16 (6.12)", "android": "android16", "kernel": "6.12"},
    "custom": {"file": "kernel-custom.yml", "name": "自定义内核构建"},
    "oneplus": {"file": "oneplus-custom.yml", "name": "OnePlus/Oplus"},
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
KSU_BRANCHES = ["Stable(标准)", "Dev(开发)", "Custom(自定义)"]
VIRT_OPTIONS = ["off", "678", "123", "345"]


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
        print(f"请求授权码失败: {e}", file=sys.stderr)
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

    def sync_fork(self, owner=None, repo=None, branch="main"):
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
            raise Exception("未登录，请先运行 'abk login'")
        
        if not self.username:
            raise Exception("无法获取用户信息")
        
        fork = self.get_fork()
        if fork:
            return {"action": "exists", "fork": fork}
        
        print(f"未检测到 fork，正在创建...")
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
        print(f"Token 已保存到: {CONFIG_FILE}")
        
        client = GitHubClient(token=token)
        try:
            user = client.get_user()
            print(f"登录为: {user.get('login', 'Unknown')}")
            
            print("\n检查 fork 状态...")
            fork_status = client.check_and_prompt_sync()
            
            if fork_status and fork_status.get("needs_fork"):
                create = input("是否创建 fork? (y/n): ").strip().lower()
                if create == 'y':
                    client.create_fork()
                    print("Fork 已创建!")
            elif fork_status and fork_status.get("needs_sync"):
                print(f"你的 fork 落后上游 {fork_status['behind_by']} 个提交")
                sync = input("是否同步? (y/n): ").strip().lower()
                if sync == 'y':
                    client.sync_fork()
                    print("同步完成!")
            elif fork_status and not fork_status.get("needs_fork"):
                print("Fork 已存在且是最新的")
        except Exception as e:
            print(f"验证失败: {e}", file=sys.stderr)


def cmd_logout(args):
    if CONFIG_FILE.exists():
        config = load_config()
        if "token" in config:
            del config["token"]
            save_config(config)
            print("已登出，Token 已移除")
        else:
            print("未登录")
    else:
        print("未登录")


def cmd_whoami(args):
    config = load_config()
    token = (
        args.token 
        or os.environ.get("GITHUB_TOKEN") 
        or os.environ.get("GH_TOKEN")
        or config.get("token")
    )
    
    if not token:
        print("未登录")
        print("请运行 'abk login' 登录")
        return
    
    client = GitHubClient(token=token)
    try:
        user = client.get_user()
        print(f"用户: {user.get('login', 'Unknown')}")
        
        fork = client.get_fork()
        if fork:
            print(f"Fork: {fork.get('full_name')}")
            
            behind = client.check_behind()
            if behind.get("behind_by", 0) > 0:
                print(f"状态: 落后上游 {behind['behind_by']} 个提交 (需要同步)")
            else:
                print("状态: 已同步")
        else:
            print("Fork: 未检测到")
            print("提示: 运行 'abk fork' 创建 fork")
    except Exception as e:
        print(f"验证失败: {e}", file=sys.stderr)


def cmd_fork(args):
    config = load_config()
    token = (
        args.token 
        or os.environ.get("GITHUB_TOKEN") 
        or os.environ.get("GH_TOKEN")
        or config.get("token")
    )
    
    if not token:
        print("未登录，请先运行 'abk login'", file=sys.stderr)
        sys.exit(1)
    
    client = GitHubClient(token=token)
    
    try:
        fork = client.get_fork()
        if fork:
            print(f"Fork 已存在: {fork.get('full_name')}")
            
            behind = client.check_behind()
            if behind.get("behind_by", 0) > 0:
                print(f"落后上游 {behind['behind_by']} 个提交")
                if not args.no_sync:
                    print("正在同步...")
                    client.sync_fork()
                    print("同步完成!")
            else:
                print("Fork 已是最新的")
        else:
            print("正在创建 fork...")
            result = client.create_fork()
            print(f"Fork 已创建: {result.get('full_name')}")
    except Exception as e:
        print(f"操作失败: {e}", file=sys.stderr)
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
        print("未登录，请先运行 'abk login'", file=sys.stderr)
        sys.exit(1)
    
    client = GitHubClient(token=token)
    
    try:
        fork = client.get_fork()
        if not fork:
            print("未检测到 fork，请先运行 'abk fork'", file=sys.stderr)
            sys.exit(1)
        
        behind = client.check_behind()
        if behind.get("behind_by", 0) == 0:
            print("Fork 已是最新的")
            return
        
        print(f"正在同步 (落后 {behind['behind_by']} 个提交)...")
        client.sync_fork()
        print("同步完成!")
    except Exception as e:
        print(f"同步失败: {e}", file=sys.stderr)
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
        print("未登录，请先运行 'abk login'", file=sys.stderr)
        sys.exit(1)
    
    client = GitHubClient(token=token)
    
    try:
        fork = client.get_fork()
        if not fork:
            print("未检测到 fork，请先运行 'abk fork'")
            return
        
        behind = client.check_behind()
        if behind.get("behind_by", 0) > 0:
            print(f"警告: Fork 落后上游 {behind['behind_by']} 个提交")
            print("运行 'abk sync' 同步")
            print()
        
        runs = client.list_runs(per_page=args.limit)
        workflow_runs = runs.get("workflow_runs", [])
        
        if not workflow_runs:
            print("没有构建记录")
            return
        
        print(f"最近 {len(workflow_runs)} 个构建:\n")
        for run in workflow_runs:
            status_icon = "✓" if run.get("conclusion") == "success" else "✗" if run.get("conclusion") == "failure" else "…" if run["status"] == "in_progress" else "○"
            created = run["created_at"][:19].replace("T", " ")
            print(f"  {status_icon} #{run['id']} | {run.get('name', '')} | {created}")
    except Exception as e:
        print(f"获取状态失败: {e}", file=sys.stderr)


def cmd_build(args):
    config = load_config()
    token = (
        args.token 
        or os.environ.get("GITHUB_TOKEN") 
        or os.environ.get("GH_TOKEN")
        or config.get("token")
    )
    
    if not token:
        print("未登录，请先运行 'abk login'", file=sys.stderr)
        sys.exit(1)
    
    client = GitHubClient(token=token)
    
    # 处理特殊全量工作流
    if args.matrix in ("full", "all-managers"):
        wf_file = FULL_MATRIX_WORKFLOWS[args.matrix]
        
        if args.matrix == "full":
            name = "全属性内核构建矩阵"
            inputs = {
                "kernelsu_variant": args.ksu_variant or "ReSukiSU",
                "kernelsu_branch": args.ksu_branch or "Dev(开发)",
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
            name = "全管理器全矩阵编译"
            inputs = {
                "build_scope": args.build_scope or "Both",
                "manager_variants": args.manager_variants or "all",
                "kernelsu_branch": args.ksu_branch or "Dev(开发)",
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
        
        ref = args.ref or "dev"
        print(f"触发 {name}...")
        if args.dry_run:
            print("  [DRY-RUN] 跳过，去掉 --dry-run 触发")
        else:
            try:
                client.trigger_workflow(wf_file, ref, inputs)
                print("  ✓ 已触发")
            except Exception as e:
                print(f"  ✗ 失败: {e}")
        print(f"查看状态: abk status")
        return
    
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
            print("未检测到 fork，正在创建...")
            client.create_fork()
            print("Fork 已创建!")
        else:
            behind = client.check_behind()
            if behind.get("behind_by", 0) > 0:
                print(f"警告: Fork 落后上游 {behind['behind_by']} 个提交")
                if not args.force:
                    sync = input("是否先同步? (y/n): ").strip().lower()
                    if sync == 'y':
                        client.sync_fork()
                        print("同步完成!")
    except Exception as e:
        print(f"检查 fork 失败: {e}", file=sys.stderr)
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
                    print("错误: OnePlus 构建需要 --device", file=sys.stderr)
                    sys.exit(1)
            elif tk == "custom":
                workflow = WORKFLOWS["custom"]
                if not args.sub_level:
                    print("错误: 需要 --sub-level (子版本号)", file=sys.stderr)
                    sys.exit(1)
                if not args.os_patch_level:
                    print("错误: 需要 --os-patch-level (安全补丁级别)", file=sys.stderr)
                    sys.exit(1)
            else:
                workflow = WORKFLOWS[tk]
            
            inputs = {
                "kernelsu_variant": kv,
                "kernelsu_branch": args.ksu_branch or "Stable(标准)",
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
                inputs["supp_op"] = str(args.oneplus_8e).lower()
                inputs["device"] = args.device
            elif not args.matrix or args.matrix == "both":
                pass
            
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
            if args.custom_modules:
                inputs["use_custom_external_modules"] = "true"
                inputs["custom_external_modules"] = args.custom_modules
            
            ref = args.ref or "dev"
            print(f"触发 {workflow['name']} ({kv})...")
            print(f"  SUSFS: {'启用' if args.susfs else '禁用'}, ZRAM: {'启用' if args.zram else '禁用'}, BBG: {'启用' if args.bbg else '禁用'}, DDK: {'启用' if args.ddk else '禁用'}, KPM: {'启用' if args.kpm else '禁用'}, Re-Kernel: {'启用' if args.rekernel else '禁用'}, NTsync: {'启用' if args.ntsync else '禁用'}, 网络增强: {'启用' if args.networking else '禁用'}")
            
            if args.dry_run:
                print(f"  [DRY-RUN] 跳过")
            else:
                try:
                    client.trigger_workflow(workflow["file"], ref, inputs)
                    print(f"  ✓ 已触发")
                except Exception as e:
                    print(f"  ✗ 失败: {e}")
    
    if total > 1:
        print(f"\n共触发 {count} 个构建")
    print(f"查看状态: abk status")
    print(f"GitHub Actions: https://github.com/{client.repo}/actions")


def cmd_artifacts(args):
    config = load_config()
    token = (
        args.token 
        or os.environ.get("GITHUB_TOKEN") 
        or os.environ.get("GH_TOKEN")
        or config.get("token")
    )
    
    if not token:
        print("未登录，请先运行 'abk login'", file=sys.stderr)
        sys.exit(1)
    
    client = GitHubClient(token=token)

    if not args.run_id:
        print("错误: 需要指定 --run-id", file=sys.stderr)
        sys.exit(1)

    try:
        artifacts = client.list_artifacts(args.run_id)
        if not artifacts.get("artifacts"):
            print("该构建没有产物")
            return

        print(f"构建 #{args.run_id} 的产物:\n")
        for art in artifacts["artifacts"]:
            size_kb = art["size_in_bytes"] / 1024
            print(f"  {art['id']} | {art['name']} | {size_kb:.1f} KB")

        if args.download:
            output_dir = args.output or "."
            Path(output_dir).mkdir(parents=True, exist_ok=True)
            print(f"\n下载到: {output_dir}")
            for art in artifacts["artifacts"]:
                print(f"  下载 {art['name']}...")
                path = client.download_artifact(art["id"], output_dir)
                if path:
                    print(f"    -> {path}")
    except Exception as e:
        print(f"操作失败: {e}", file=sys.stderr)


def cmd_list(args):
    print("可用构建目标:\n")
    for key in MATRIX_TARGETS:
        wf = WORKFLOWS[key]
        print(f"  --matrix {key:9} - {wf['name']} (矩阵构建所有子版本)")
    print(f"  --oneplus        - OnePlus/Oplus 设备 (需 --device)")
    print(f"\n默认: 自定义构建 (kernel-custom.yml)，需 --sub-level 和 --os-patch-level")

    print("\nKernelSU 变体:")
    for v in KSU_VARIANTS:
        print(f"  {v}")

    print("\nKernelSU 分支:")
    for b in KSU_BRANCHES:
        print(f"  {b}")

    print("\n虚拟化支持选项:")
    for o in VIRT_OPTIONS:
        print(f"  {o}")

    print("\n命令:")
    print("  abk login                                # 登录 GitHub")
    print("  abk logout                               # 登出")
    print("  abk whoami                               # 显示当前用户")
    print("  abk fork                                 # 创建/检查 fork")
    print("  abk sync                                 # 同步 fork 与上游")
    print("  abk build a15 --ksu ReSukiSU             # 构建内核")
    print("  abk status                               # 查看构建状态")
    print("  abk artifacts --run-id 12345 --download  # 下载构建产物")


def main():
    # 提前检测 --lang 以确保帮助文本使用正确语言
    if "--lang" in sys.argv:
        idx = sys.argv.index("--lang")
        if idx + 1 < len(sys.argv):
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
    parser.add_argument("--lang", choices=["zh-cn", "en-us"], help=t("help_lang"))

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
    fork_parser.add_argument("--no-sync", action="store_true", help="不同步 fork (即使落后上游)")
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
        epilog="""
内核版本选项:
  --android-version   Android 版本 (默认: android12)
  --kernel-version    内核主版本 (默认: 5.10)
  --sub-level         子版本号 (必需)
  --os-patch-level    安全补丁级别 (必需)
  --revision          修订版本 (仅 5.10)

矩阵构建 (构建所有子版本):
  --matrix android14        使用 kernel-a14-6-1.yml

OnePlus 构建:
  --oneplus                 使用 oneplus-custom.yml (需 --device)

KernelSU 变体:
  None / Official / SukiSU / ReSukiSU (默认)

KernelSU 分支:
  Stable(标准) / Dev(开发) / Custom(自定义)

虚拟化支持: off (默认) / 678 / 123 / 345

示例:
  abk build --sub-level 162 --os-patch-level 2026-03
  abk build --sub-level 162 --os-patch-level 2026-03 --ksu Official --zram
  abk build --android-version android14 --kernel-version 6.1 --sub-level 162 --os-patch-level 2026-03
  abk build --matrix a15                                    # 矩阵构建
  abk build --matrix both                                   # 全版本矩阵构建
  abk build --matrix full                                   # 全属性内核构建矩阵
  abk build --matrix all-managers                           # 全管理器全矩阵编译
  abk build --oneplus --device oneplus12                    # OnePlus 构建""")
    build_parser.add_argument("--matrix", choices=MATRIX_TARGETS_ALL, help=t("arg_matrix"))
    build_parser.add_argument("--oneplus", action="store_true", help=t("arg_oneplus"))
    build_parser.add_argument("--ref", default="dev", help=t("arg_ref"))
    build_parser.add_argument("--ksu", dest="ksu_variant", choices=KSU_VARIANTS + ["all"], help=t("arg_ksu"))
    build_parser.add_argument("--ksu-branch", choices=KSU_BRANCHES, help=t("arg_ksu_branch"))
    build_parser.add_argument("--custom-ref", help=t("arg_custom_ref"))
    build_parser.add_argument("--version", help=t("arg_version"))
    build_parser.add_argument("--device", help=t("arg_device"))
    build_parser.add_argument("--virt", choices=VIRT_OPTIONS, default="off", help=t("arg_virt"))
    build_parser.add_argument("--kpm-password", help=t("arg_kpm_password"))
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
    status_parser.add_argument("--run-id", type=int, help="查看特定构建运行")
    status_parser.add_argument("--target", choices=WORKFLOWS.keys(), help="按构建目标过滤")
    status_parser.add_argument("--status", choices=["all", "queued", "in_progress", "completed"], default="all", help="按状态过滤 (默认: all)")
    status_parser.add_argument("--limit", type=int, default=10, help="显示数量 (默认: 10)")
    status_parser.set_defaults(func=cmd_status)

    # artifacts
    artifacts_parser = subparsers.add_parser("artifacts", 
        help=t("cmd_artifacts_help"),
        description=t("cmd_artifacts_desc"))
    artifacts_parser.add_argument("--run-id", type=int, help="构建运行 ID")
    artifacts_parser.add_argument("--download", action="store_true", help=t("arg_download"))
    artifacts_parser.add_argument("--output", "-o", help=t("arg_output"))
    artifacts_parser.set_defaults(func=cmd_artifacts)

    # list
    list_parser = subparsers.add_parser("list", 
        help=t("cmd_list_help"),
        description=t("cmd_list_desc"))
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
    main()
