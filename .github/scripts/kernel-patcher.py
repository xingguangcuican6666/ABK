#!/usr/bin/env python3
"""KernelSU 补丁模块编排：克隆仓库 → 依赖分组 → 分组打补丁 → 合并 → 生成 NyaSU 补丁。

两个子命令：

``clone``
    解析 ``ABK_INPUT_KSUHOOK_MODULES``（``|`` 分隔，每项 ``repo_url[@ref]``），
    逐个克隆到 ``$GITHUB_WORKSPACE/ksuhook_module_NN-<name>``，写出清单 TSV。

``apply``
    读取清单，发现模块，按 ``depends`` 求连通分量作为分组，在**同一个 base 提交**上
    为每组建 git worktree 应用用户补丁，再合并到集成分支，最后对整棵 KernelSU 树
    跑一次 ``ksuhook generate``，把结果整体回填到活树。

用户补丁路径以 ``KERNEL_ROOT`` 为根，例如 ``KernelSU/kernel/foo.c``、
``common/drivers/bar.c``；``ksuhook generate`` 产出的补丁以 ``KernelSU`` 仓库为根。
"""

from __future__ import annotations

import argparse
import hashlib
import os
import re
import shutil
import subprocess
import sys
import tempfile
from dataclasses import dataclass, field
from pathlib import Path

URL_PREFIXES = ("https://", "http://", "ssh://", "git@", "git://")
MODULE_IDENT = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*$")
DIFF_HEADER = re.compile(r"^diff --git a/(?P<path>\S+) b/(?P<path2>\S+)$", re.M)

# 允许作为补丁落点的 KERNEL_ROOT 一级子树；其它路径一律拒绝。
PATCH_ROOTS = ("KernelSU", "common")


class PatchError(Exception):
    """编排失败：消息直接以 ``::error::`` 形式输出给 GitHub Actions。"""


def _run(cmd: list[str], cwd: Path | None = None, check: bool = True) -> subprocess.CompletedProcess:
    result = subprocess.run(
        cmd,
        cwd=str(cwd) if cwd else None,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    if check and result.returncode != 0:
        detail = (result.stderr or result.stdout or "").strip()
        raise PatchError(f"命令失败（{result.returncode}）：{' '.join(cmd)}\n{detail}")
    return result


def _git(*args: str, cwd: Path, check: bool = True) -> str:
    return _run(["git", *args], cwd=cwd, check=check).stdout


# ---------------------------------------------------------------- clone


@dataclass(frozen=True)
class RepoSpec:
    url: str
    ref: str

    @property
    def name(self) -> str:
        base = self.url.rstrip("/").rsplit("/", 1)[-1]
        if base.endswith(".git"):
            base = base[: -len(".git")]
        cleaned = re.sub(r"[^A-Za-z0-9._-]", "_", base)
        return cleaned or "module"


def parse_module_spec(text: str) -> list[RepoSpec]:
    """解析 ``a|b@ref|c``；空串返回空列表。

    ``git@host:path`` 形式的 scp 地址本身含 ``@``，因此从右侧切分：
    只有 ``@`` 之后不含 ``/`` 与 ``:`` 时才视作 ref。
    """
    specs: list[RepoSpec] = []
    for raw in text.replace("\r", "").split("|"):
        item = raw.strip()
        if not item:
            continue
        url, ref = item, ""
        if "@" in item:
            head, _, tail = item.rpartition("@")
            # scp 风格 `git@host:path`：切出来的 head 以 host 开头、tail 含路径分隔符。
            if head and "/" not in tail and ":" not in tail:
                url, ref = head, tail
        url = url.strip()
        ref = ref.strip()
        if not url:
            raise PatchError(f"模块条目缺少仓库地址: {item!r}")
        if not url.startswith(URL_PREFIXES):
            raise PatchError(f"模块仓库协议不支持: {url}（支持 {' / '.join(URL_PREFIXES)}）")
        if "@" in ref:
            raise PatchError(f"模块 ref 不能包含 '@': {item!r}")
        specs.append(RepoSpec(url=url, ref=ref))
    return specs


@dataclass
class Clone:
    index: int
    spec: RepoSpec
    path: Path
    head_sha: str


def clone_modules(specs: list[RepoSpec], dest_root: Path, log=print) -> list[Clone]:
    clones: list[Clone] = []
    for index, spec in enumerate(specs, start=1):
        target = dest_root / f"ksuhook_module_{index:02d}-{spec.name}"
        if target.exists():
            shutil.rmtree(target)
        log(f"克隆补丁模块 #{index}: {spec.url}" + (f" @ {spec.ref}" if spec.ref else ""))
        if spec.ref:
            _run(
                ["git", "clone", "--filter=blob:none", "--no-checkout", spec.url, str(target)],
            )
            _run(["git", "checkout", "--detach", spec.ref], cwd=target)
        else:
            _run(["git", "clone", "--depth", "1", spec.url, str(target)])
        head = _git("rev-parse", "HEAD", cwd=target).strip()
        clones.append(Clone(index=index, spec=spec, path=target, head_sha=head))
        log(f"  -> {target.name} ({head[:12]})")
    return clones


def write_manifest(clones: list[Clone], manifest: Path) -> None:
    lines = ["index\tdir\trepo\tref\thead_sha"]
    for clone in clones:
        lines.append(
            "\t".join(
                [
                    str(clone.index),
                    str(clone.path),
                    clone.spec.url,
                    clone.spec.ref,
                    clone.head_sha,
                ]
            )
        )
    manifest.write_text("\n".join(lines) + "\n", encoding="utf-8")


def read_manifest(manifest: Path) -> list[Clone]:
    clones: list[Clone] = []
    if not manifest.is_file():
        return clones
    for line in manifest.read_text(encoding="utf-8").splitlines()[1:]:
        if not line.strip():
            continue
        index, directory, repo, ref, head = line.split("\t")
        clones.append(
            Clone(
                index=int(index),
                spec=RepoSpec(url=repo, ref=ref),
                path=Path(directory),
                head_sha=head,
            )
        )
    return clones


# ---------------------------------------------------------------- modules


@dataclass
class ModuleInfo:
    root: Path
    id: str
    priority: int
    depends: tuple[str, ...]

    def patches_for(self, android_version: str, kernel_version: str) -> list[Path]:
        line = f"{android_version}-{kernel_version}"
        directory = self.root / "patches" / line
        if not directory.is_dir():
            return []
        return sorted(
            path
            for path in directory.iterdir()
            if path.is_file() and path.suffix in (".patch", ".diff")
        )


def _read_prop(path: Path) -> dict[str, str]:
    props: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        props[key.strip()] = value.strip()
    return props


def discover_modules(dirs: list[Path]) -> list[ModuleInfo]:
    """接受模块目录或其父目录，展开成模块列表。"""
    found: list[Path] = []
    for directory in dirs:
        if not directory.is_dir():
            continue
        if (directory / "module.prop").is_file():
            found.append(directory)
            continue
        found.extend(
            child
            for child in sorted(directory.iterdir())
            if child.is_dir() and (child / "module.prop").is_file()
        )
    modules: list[ModuleInfo] = []
    for root in found:
        props = _read_prop(root / "module.prop")
        module_id = props.get("id", "")
        if not MODULE_IDENT.match(module_id):
            raise PatchError(f"{root}/module.prop: 非法模块 id {module_id!r}")
        try:
            priority = int(props.get("priority", "50"))
        except ValueError as exc:
            raise PatchError(f"{root}/module.prop: priority 不是整数") from exc
        depends = tuple(
            item.strip()
            for item in props.get("depends", "").split(",")
            if item.strip()
        )
        for dep in depends:
            if not MODULE_IDENT.match(dep):
                raise PatchError(f"{root}/module.prop: depends 含非法 id {dep!r}")
        modules.append(ModuleInfo(root=root, id=module_id, priority=priority, depends=depends))
    ids = [module.id for module in modules]
    dupes = sorted({i for i in ids if ids.count(i) > 1})
    if dupes:
        raise PatchError(f"模块 id 重复: {dupes}")
    return sorted(modules, key=lambda m: m.id)


@dataclass
class Group:
    modules: list[ModuleInfo] = field(default_factory=list)

    @property
    def name(self) -> str:
        return "+".join(module.id for module in self.modules)


def group_modules(modules: list[ModuleInfo]) -> list[Group]:
    """按 depends 求连通分量；缺失依赖与环都直接失败。"""
    by_id = {module.id: module for module in modules}
    missing = {
        module.id: sorted(dep for dep in module.depends if dep not in by_id)
        for module in modules
    }
    missing = {k: v for k, v in missing.items() if v}
    if missing:
        detail = "; ".join(f"{k} -> {v}" for k, v in sorted(missing.items()))
        raise PatchError(f"模块依赖不存在: {detail}")

    adjacency: dict[str, set[str]] = {module.id: set() for module in modules}
    for module in modules:
        for dep in module.depends:
            adjacency[module.id].add(dep)
            adjacency[dep].add(module.id)

    groups: list[Group] = []
    seen: set[str] = set()
    for module in modules:
        if module.id in seen:
            continue
        stack = [module.id]
        members: list[ModuleInfo] = []
        while stack:
            current = stack.pop()
            if current in seen:
                continue
            seen.add(current)
            members.append(by_id[current])
            stack.extend(sorted(adjacency[current] - seen))
        groups.append(Group(modules=sorted(members, key=lambda m: m.id)))
    return sorted(groups, key=lambda g: g.modules[0].id)


def detect_cycle(modules: list[ModuleInfo]) -> list[str] | None:
    """有向依赖图上的环检测（``depends`` 方向），返回环路径或 None。"""
    graph = {module.id: module.depends for module in modules}
    state: dict[str, int] = {}
    stack: list[str] = []

    def visit(node: str) -> list[str] | None:
        state[node] = 1
        stack.append(node)
        for dep in graph.get(node, ()):
            if dep not in graph:
                continue
            if state.get(dep) == 1:
                return stack[stack.index(dep):] + [dep]
            if state.get(dep, 0) == 0:
                found = visit(dep)
                if found:
                    return found
        stack.pop()
        state[node] = 2
        return None

    for module in modules:
        if state.get(module.id, 0) == 0:
            found = visit(module.id)
            if found:
                return found
    return None


def order_patches(modules: list[ModuleInfo], android_version: str, kernel_version: str):
    """组内应用顺序：priority 降序 → id 升序 → 补丁文件名升序。"""
    ordered = sorted(modules, key=lambda m: (-m.priority, m.id))
    plan = []
    for module in ordered:
        for patch in module.patches_for(android_version, kernel_version):
            plan.append((module, patch))
    return plan


# ---------------------------------------------------------------- patch text


def split_patch(text: str) -> list[tuple[str, str]]:
    """把 unified diff 切成 ``(路径, 单段补丁)``；路径取 ``a/`` 之后的部分。"""
    sections = []
    matches = list(DIFF_HEADER.finditer(text))
    if not matches:
        raise PatchError("补丁里没有任何 diff --git 段")
    if matches[0].start() > 0 and text[: matches[0].start()].strip():
        raise PatchError("补丁开头有 diff --git 之外的内容，无法安全解析")
    for index, match in enumerate(matches):
        end = matches[index + 1].start() if index + 1 < len(matches) else len(text)
        sections.append((match.group("path"), text[match.start():end]))
    return sections


def patch_root_of(path: str) -> str:
    if path.startswith("/"):
        raise PatchError(f"补丁含绝对路径: {path}")
    parts = Path(path).parts
    if not parts or ".." in parts:
        raise PatchError(f"补丁路径非法: {path}")
    root = parts[0]
    if root not in PATCH_ROOTS:
        raise PatchError(
            f"补丁落点必须是 {' / '.join(PATCH_ROOTS)} 之下，实际: {path}"
        )
    return root


def route_patch(text: str) -> dict[str, str]:
    """按一级目录把用户补丁拆成 ``{repo: 补丁文本}``。"""
    buckets: dict[str, list[str]] = {}
    for path, section in split_patch(text):
        root = patch_root_of(path)
        buckets.setdefault(root, []).append(section)
    return {root: "".join(sections) for root, sections in buckets.items()}


# ---------------------------------------------------------------- repos


@dataclass
class RepoState:
    name: str
    path: Path
    base: str

    def worktree(self, target: Path) -> None:
        if target.exists():
            _git("worktree", "remove", "--force", str(target), cwd=self.path, check=False)
            shutil.rmtree(target, ignore_errors=True)
        _git("worktree", "add", "--detach", str(target), self.base, cwd=self.path)

    def drop_worktree(self, target: Path) -> None:
        _git("worktree", "remove", "--force", str(target), cwd=self.path, check=False)
        shutil.rmtree(target, ignore_errors=True)

    def diff(self, worktree: Path) -> str:
        _git("add", "-A", "-N", cwd=worktree)
        return _git("diff", "--binary", self.base, cwd=worktree)

    def worktree_at(self, layout: str, group: str | None = None) -> Path:
        """工作区路径布局：``<layout>/groups/<group>/<repo>`` 或 ``<layout>/integration/<repo>``。

        group 名里的 ``+``、``.`` 等字符在各仓库目录名前保持一致，
        避免 ``groups/mod_a`` 与 ``groups/mod_a+mod_b`` 互相包含。
        """
        if group is None:
            return Path(layout) / "integration" / self.name
        return Path(layout) / "groups" / _safe(group) / self.name


def is_git_repo(path: Path) -> bool:
    if not path.is_dir():
        return False
    result = _run(["git", "rev-parse", "--git-dir"], cwd=path, check=False)
    return result.returncode == 0


def snapshot_base(repo: Path) -> str:
    """把当前脏工作树固化成 commit 对象，但**不**移动 HEAD、不新增分支。"""
    _git("add", "-A", cwd=repo)
    tree = _git("write-tree", cwd=repo).strip()
    return _git(
        "-c", "user.name=abk-kernel-patcher",
        "-c", "user.email=abk-kernel-patcher@localhost",
        "commit-tree", tree, "-m", "abk ksuhook base",
        cwd=repo,
    ).strip()


# ---------------------------------------------------------------- sdk


def sdk_env(sdk_dir: Path) -> dict[str, str]:
    env = dict(os.environ)
    env["PYTHONPATH"] = str(sdk_dir) + (
        os.pathsep + env["PYTHONPATH"] if env.get("PYTHONPATH") else ""
    )
    return env


def run_ksuhook(sdk_dir: Path, args: list[str], log=print) -> None:
    env = sdk_env(sdk_dir)
    result = subprocess.run(
        [sys.executable, "-m", "ksuhook", *args],
        env=env,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        cwd=str(sdk_dir),
    )
    if log is not None:
        for line in (result.stdout + result.stderr).splitlines():
            if line.strip():
                log(f"  ksuhook: {line}")
    if result.returncode != 0:
        raise PatchError(
            f"ksuhook {' '.join(args)} 失败（{result.returncode}）"
        )


# ---------------------------------------------------------------- apply


@dataclass
class ApplyResult:
    modules: list[ModuleInfo]
    groups: list[Group]
    group_patch_counts: dict[str, int]
    final_sha256: str


def apply_modules(
    *,
    module_dirs: list[Path],
    kernel_root: Path,
    sdk_dir: Path,
    android_version: str,
    kernel_version: str,
    workroot: Path,
    log=print,
) -> ApplyResult:
    modules = discover_modules(module_dirs)
    if not modules:
        raise PatchError("未发现任何补丁模块（module.prop 缺失？）")

    cycle = detect_cycle(modules)
    if cycle:
        raise PatchError("模块依赖成环: " + " -> ".join(cycle))

    groups = group_modules(modules)

    ksu_repo_path = kernel_root / "KernelSU"
    if not ksu_repo_path.is_dir():
        raise PatchError(f"未找到 KernelSU 子树: {ksu_repo_path}")

    run_ksuhook(
        sdk_dir,
        ["validate", "--repo", str(ksu_repo_path), "--packs", *[str(m.root) for m in modules]],
        log=log,
    )

    # ---- 收集补丁并按 repo 路由
    group_patches: dict[str, dict[str, list[str]]] = {}
    routed: dict[str, dict[str, str]] = {}
    for group in groups:
        per_repo: dict[str, list[str]] = {}
        for module, patch in order_patches(group.modules, android_version, kernel_version):
            text = patch.read_text(encoding="utf-8")
            try:
                buckets = route_patch(text)
            except PatchError as exc:
                raise PatchError(f"{module.id} / {patch.name}: {exc}") from exc
            for repo, body in buckets.items():
                per_repo.setdefault(repo, []).append(body)
        group_patches[group.name] = per_repo
        for repo in per_repo:
            routed.setdefault(repo, {})

    # ---- base 快照
    repos: dict[str, RepoState] = {}
    for name in PATCH_ROOTS:
        path = kernel_root / name
        if not path.is_dir():
            continue
        if not is_git_repo(path):
            if name in routed:
                raise PatchError(f"{path} 不是 git 仓库，无法应用落在这里的补丁")
            log(f"::warning::{path} 不是 git 仓库，跳过该子树的补丁编排")
            continue
        repos[name] = RepoState(name=name, path=path, base=snapshot_base(path))
        log(f"base {name}: {repos[name].base[:12]}")

    # ---- 分组工作区
    group_diffs: dict[str, dict[str, str]] = {}
    for group in groups:
        per_repo = group_patches[group.name]
        if not per_repo:
            continue
        diffs: dict[str, str] = {}
        for repo_name, texts in sorted(per_repo.items()):
            state = repos[repo_name]
            tree = state.worktree_at(workroot, group.name)
            state.worktree(tree)
            try:
                for index, body in enumerate(texts, start=1):
                    patch_file = workroot / "groups" / _safe(group.name) / f"{repo_name}-{index}.patch"
                    patch_file.write_text(body, encoding="utf-8")
                    # 用户补丁以 KERNEL_ROOT 为根（a/common/...）；在 <repo> worktree 里用 -p2
                    # 去掉 a/ 与仓库目录两层，落到仓库内相对路径。
                    _run(["git", "apply", "--check", "-p2", str(patch_file)], cwd=tree)
                    _run(["git", "apply", "-p2", str(patch_file)], cwd=tree)
                diffs[repo_name] = state.diff(tree)
            finally:
                state.drop_worktree(tree)
        group_diffs[group.name] = diffs

    # ---- 合并到集成树
    integration: dict[str, Path] = {}
    for repo_name, state in repos.items():
        tree = state.worktree_at(workroot)
        state.worktree(tree)
        integration[repo_name] = tree

    for group in groups:
        for repo_name, body in sorted(group_diffs.get(group.name, {}).items()):
            tree = integration[repo_name]
            patch_file = workroot / "integration" / f"merge-{_safe(group.name)}-{repo_name}.patch"
            patch_file.write_text(body, encoding="utf-8")
            check = _run(["git", "apply", "--check", "-p1", str(patch_file)], cwd=tree, check=False)
            if check.returncode != 0:
                raise PatchError(
                    f"分组 {group.name} 合并到 {repo_name} 冲突:\n"
                    + (check.stderr or check.stdout).strip()
                )
            _run(["git", "apply", "-p1", str(patch_file)], cwd=tree)
        log(f"已合并分组 {group.name}")

    # ---- NyaSU 生成（仅一次，作用于合并后的 KernelSU）
    sdkgen_repo = integration.get("KernelSU")
    generated_sha = ""
    if sdkgen_repo is not None:
        out = workroot / "ksuhook-sdkgen.patch"
        run_ksuhook(
            sdk_dir,
            [
                "generate",
                "--repo", str(sdkgen_repo),
                "--packs", *[str(m.root) for m in modules],
                "--out", str(out),
            ],
            log=log,
        )
        _run(["git", "apply", "--check", "-p1", str(out)], cwd=sdkgen_repo)
        _run(["git", "apply", "-p1", str(out)], cwd=sdkgen_repo)
        generated_sha = hashlib.sha256(out.read_bytes()).hexdigest()
        log(f"NyaSU 生成补丁 sha256: {generated_sha[:16]}…")

    # ---- 回填活树
    final_digest = hashlib.sha256()
    for repo_name, state in sorted(repos.items()):
        body = state.diff(integration[repo_name])
        if not body.strip():
            log(f"{repo_name}: 无改动")
            continue
        final_digest.update(body.encode("utf-8"))
        patch_file = workroot / f"final-{repo_name}.patch"
        patch_file.write_text(body, encoding="utf-8")
        _run(["git", "apply", "--check", "-p1", str(patch_file)], cwd=state.path)
        _run(["git", "apply", "-p1", str(patch_file)], cwd=state.path)
        state.drop_worktree(integration[repo_name])
        log(f"{repo_name}: 已回填活树")

    counts = {
        name: sum(len(v) for v in per_repo.values())
        for name, per_repo in group_patches.items()
    }
    return ApplyResult(
        modules=modules,
        groups=groups,
        group_patch_counts=counts,
        final_sha256=final_digest.hexdigest() if counts and any(counts.values()) else generated_sha,
    )


def _safe(name: str) -> str:
    return re.sub(r"[^A-Za-z0-9._-]", "_", name)


# ---------------------------------------------------------------- summary


def write_summary(result: ApplyResult, clones: list[Clone], path: Path | None) -> None:
    lines = ["## KernelSU 补丁模块", ""]
    if clones:
        lines.append("| # | 仓库 | ref | HEAD |")
        lines.append("|---|---|---|---|")
        for clone in clones:
            lines.append(
                f"| {clone.index} | {clone.spec.url} | {clone.spec.ref or '-'} | `{clone.head_sha[:12]}` |"
            )
        lines.append("")
    lines.append(f"模块数: {len(result.modules)}，分组数: {len(result.groups)}")
    lines.append("")
    lines.append("| 分组 | 模块 | 补丁数 |")
    lines.append("|---|---|---|")
    for group in result.groups:
        ids = ", ".join(module.id for module in group.modules)
        lines.append(f"| {group.name} | {ids} | {result.group_patch_counts.get(group.name, 0)} |")
    lines.append("")
    lines.append(f"最终补丁 sha256: `{result.final_sha256}`")
    text = "\n".join(lines) + "\n"
    if path is not None:
        path.write_text(text, encoding="utf-8")
    print(text)


# ---------------------------------------------------------------- cli


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="kernel-patcher")
    sub = parser.add_subparsers(dest="command", required=True)

    p_clone = sub.add_parser("clone", help="克隆模块仓库并写清单")
    p_clone.add_argument("--modules", default=os.environ.get("ABK_INPUT_KSUHOOK_MODULES", ""))
    p_clone.add_argument("--dest", default=os.environ.get("GITHUB_WORKSPACE", os.getcwd()))
    p_clone.add_argument(
        "--manifest",
        default=os.environ.get("KSUHOOK_MODULE_MANIFEST", ""),
    )

    p_apply = sub.add_parser("apply", help="分组应用补丁并生成 NyaSU 补丁")
    p_apply.add_argument("--manifest", default=os.environ.get("KSUHOOK_MODULE_MANIFEST", ""))
    p_apply.add_argument("--kernel-root", default=os.environ.get("KERNEL_ROOT", ""))
    p_apply.add_argument("--sdk", default=os.environ.get("KSUHOOK_SDK_DIR", ""))
    p_apply.add_argument(
        "--android-version",
        default=os.environ.get("ABK_INPUT_ANDROID_VERSION", ""),
    )
    p_apply.add_argument(
        "--kernel-version",
        default=os.environ.get("ABK_INPUT_KERNEL_VERSION", ""),
    )
    p_apply.add_argument(
        "--workroot",
        default=os.environ.get("KSUHOOK_WORKROOT", ""),
    )
    p_apply.add_argument("--summary", default=os.environ.get("GITHUB_STEP_SUMMARY", ""))

    args = parser.parse_args(argv)
    try:
        if args.command == "clone":
            dest = Path(args.dest)
            manifest = Path(args.manifest) if args.manifest else dest / "ksuhook_modules.tsv"
            specs = parse_module_spec(args.modules)
            if not specs:
                print("未配置补丁模块，跳过克隆。")
                manifest.write_text("index\tdir\trepo\tref\thead_sha\n", encoding="utf-8")
                return 0
            clones = clone_modules(specs, dest)
            write_manifest(clones, manifest)
            print(f"清单已写入 {manifest}")
            return 0

        manifest = Path(args.manifest) if args.manifest else Path("ksuhook_modules.tsv")
        clones = read_manifest(manifest)
        if not clones:
            print("清单为空，跳过补丁编排。")
            return 0
        kernel_root = Path(args.kernel_root) if args.kernel_root else Path("")
        if not args.kernel_root:
            raise PatchError("缺少 KERNEL_ROOT")
        sdk_dir = Path(args.sdk) if args.sdk else Path("")
        if not args.sdk:
            raise PatchError("缺少 KSUHOOK_SDK_DIR")
        if not args.android_version or not args.kernel_version:
            raise PatchError("缺少 android/kernel 版本输入")
        workroot = (
            Path(args.workroot)
            if args.workroot
            else Path(tempfile.mkdtemp(prefix="ksuhook-", dir=os.environ.get("RUNNER_TEMP")))
        )
        workroot.mkdir(parents=True, exist_ok=True)
        result = apply_modules(
            module_dirs=[clone.path for clone in clones],
            kernel_root=kernel_root,
            sdk_dir=sdk_dir,
            android_version=args.android_version,
            kernel_version=args.kernel_version,
            workroot=workroot,
        )
        write_summary(result, clones, Path(args.summary) if args.summary else None)
        return 0
    except PatchError as exc:
        print(f"::error::{exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
