#!/usr/bin/env python3
"""Keep unpatched downloads outside the Actions workspace on local runners."""
import argparse
import contextlib
import errno
import hashlib
import os
import re
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path


def run(args, cwd=None, capture=False, retry=False):
    env = dict(os.environ)
    # Objects referenced by an in-flight checkout must not be auto-pruned.
    count = int(env.get("GIT_CONFIG_COUNT", "0"))
    env.update(GIT_CONFIG_COUNT=str(count + 1),
               **{f"GIT_CONFIG_KEY_{count}": "gc.auto", f"GIT_CONFIG_VALUE_{count}": "0"})
    for attempt in range(3 if retry else 1):
        result = subprocess.run(args, cwd=cwd, env=env, text=True, encoding="utf-8",
                                errors="replace", stdout=subprocess.PIPE if capture else None)
        if result.returncode == 0:
            return result.stdout.strip() if capture else ""
        if retry and attempt < 2:
            time.sleep(2 * (attempt + 1))
    raise RuntimeError(f"{Path(args[0]).name} failed with exit code {result.returncode}")


def key(value):
    return hashlib.sha256(value.encode()).hexdigest()[:24]


def cache_root():
    value = os.environ.get("ABK_LOCAL_CACHE_ROOT", "")
    return Path(value).resolve() if value else None


@contextlib.contextmanager
def locked(root, identity):
    import fcntl
    directory = root / "locks"
    directory.mkdir(parents=True, exist_ok=True)
    with (directory / (key(identity) + ".lock")).open("a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        yield


def report(label, before, after):
    state = "unchanged: reused local objects" if before == after else "updated: fetched missing objects"
    print(f"[download-cache] {label}: {state} ({after[:12]})", flush=True)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as handle:
            handle.write(f"- `{label}`: {state}; `{before[:12] or 'empty'}` → `{after[:12]}`\n")


def configure():
    enabled = (os.environ.get("ABK_RUNNER_ENVIRONMENT") == "self-hosted"
               and os.environ.get("KERNEL_LOCAL_CACHE", "auto").lower() != "off")
    root = None
    if enabled:
        setting = os.environ.get("KERNEL_LOCAL_CACHE_DIR", "")
        if setting and not Path(setting).is_absolute():
            raise ValueError("KERNEL_LOCAL_CACHE_DIR must be an absolute path")
        base = Path(setting).expanduser() if setting else Path.home() / ".cache/abk-downloads"
        root = (base / key(os.environ.get("GITHUB_REPOSITORY", "local")) / "v1").resolve()
        workspace = Path(os.environ["GITHUB_WORKSPACE"]).resolve()
        if root == workspace or workspace in root.parents:
            raise ValueError("download cache must be outside GITHUB_WORKSPACE")
        root.mkdir(parents=True, exist_ok=True, mode=0o700)
    values = {"ABK_LOCAL_CACHE_ENABLED": str(enabled).lower(), "ABK_LOCAL_CACHE_ROOT": str(root or "")}
    with open(os.environ["GITHUB_ENV"], "a", encoding="utf-8") as handle:
        for name, value in values.items():
            handle.write(f"{name}={value}\n")
    print(f"[download-cache] local cache {'enabled: ' + str(root) if enabled else 'disabled'}")


def resolve_remote(url, ref):
    if not ref:
        output = run(["git", "ls-remote", "--symref", url, "HEAD"], capture=True, retry=True)
        sha = next(line.split()[0] for line in output.splitlines() if line.endswith("\tHEAD") and not line.startswith("ref:"))
        branch = next((line.split()[1] for line in output.splitlines() if line.startswith("ref:")), "")
        return sha, branch or "HEAD"
    if ref.startswith("refs/tags/"):
        refs = [ref, ref + "^{}"]
    elif ref.startswith("refs/"):
        refs = [ref]
    else:
        refs = [f"refs/heads/{ref}", f"refs/tags/{ref}", f"refs/tags/{ref}^{{}}"]
    output = run(["git", "ls-remote", url, *refs], capture=True, retry=True)
    found = {name: sha for sha, name in (line.split() for line in output.splitlines())}
    for candidate in ([refs[0], refs[-1], refs[1]] if len(refs) == 3 else list(reversed(refs))):
        if candidate in found:
            return found[candidate], candidate.removesuffix("^{}")
    raise ValueError(f"requested Git ref not found: {ref}")


def has_commit(directory, sha):
    return subprocess.run(["git", "--git-dir", str(directory), "cat-file", "-e", sha + "^{commit}"],
                          stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0


def clone(url, destination, ref="", depth=0):
    destination = Path(destination).resolve()
    root = cache_root()
    if root and (root == destination or root in destination.parents):
        raise ValueError("build checkout must be outside the download cache")
    if destination.exists():
        raise ValueError(f"fresh checkout destination already exists: {destination}")
    if not root:
        command = ["git", "clone"]
        if depth:
            command += ["--depth", str(depth)]
        if ref:
            command += ["--branch", ref]
        run(command + [url, str(destination)])
        return
    identity = f"git:{url}"
    with locked(root, identity):
        mirror = root / "git" / (key(url) + ".git")
        mirror.parent.mkdir(parents=True, exist_ok=True)
        if not mirror.exists():
            run(["git", "init", "--bare", "--initial-branch=abk-cache", str(mirror)])
            run(["git", "--git-dir", str(mirror), "remote", "add", "origin", url])
        elif run(["git", "--git-dir", str(mirror), "remote", "get-url", "origin"], capture=True) != url:
            raise ValueError("cache remote does not match requested repository")
        pinned = bool(re.fullmatch(r"[0-9a-fA-F]{40}", ref))
        sha, remote_ref = (ref.lower(), "") if pinned else resolve_remote(url, ref)
        cache_ref = "refs/heads/abk-cache-" + key(ref or "HEAD")
        previous = subprocess.run(["git", "--git-dir", str(mirror), "rev-parse", "--verify", cache_ref],
                                  capture_output=True, text=True, encoding="utf-8").stdout.strip()
        if not has_commit(mirror, sha) or (not depth and (mirror / "shallow").exists()):
            command = ["git", "--git-dir", str(mirror), "fetch", "--no-tags"]
            if depth:
                command += ["--depth", str(depth)]
            elif (mirror / "shallow").exists():
                command += ["--unshallow"]
            command += ["origin", remote_ref or sha]
            run(command, retry=True)
        if not has_commit(mirror, sha):
            raise RuntimeError("remote changed during fetch; retry instead of building another commit")
        run(["git", "--git-dir", str(mirror), "update-ref", cache_ref, sha])
        run(["git", "--git-dir", str(mirror), "symbolic-ref", "HEAD", cache_ref])
        # Shallow repositories use local upload-pack; full clones can hard-link
        # immutable objects. Neither shares a writable source tree.
        transport = "--no-local" if (mirror / "shallow").exists() else "--local"
        run(["git", "clone", transport, "--no-checkout", str(mirror), str(destination)])
        run(["git", "-C", str(destination), "remote", "set-url", "origin", url])
        run(["git", "-C", str(destination), "checkout", "--detach", sha])
        report(destination.name, previous, sha)


def revisions(manifest):
    tree = ET.parse(manifest)
    return {project.get("path", project.get("name")): project.get("revision", "")
            for project in tree.getroot().iter("project")}


def configure_kernel_branch(client, branch):
    output = run(["git", "ls-remote", "https://android.googlesource.com/kernel/common",
                  f"refs/heads/{branch}", f"refs/heads/deprecated/{branch}"], capture=True, retry=True)
    refs = {name: sha for sha, name in (line.split() for line in output.splitlines())}
    selected = f"refs/heads/{branch}" if f"refs/heads/{branch}" in refs else f"refs/heads/deprecated/{branch}"
    if selected not in refs:
        raise ValueError(f"kernel branch not found: {branch}")
    directory = client / ".repo/local_manifests"
    directory.mkdir(parents=True, exist_ok=True)
    override = ET.Element("manifest")
    ET.SubElement(override, "extend-project", name="kernel/common", revision=selected, upstream=selected)
    ET.ElementTree(override).write(directory / "abk-kernel-branch.xml", encoding="unicode")
    return refs[selected] + "\t" + selected


def sync_repo(repo, manifest_url, branch, destination, kernel_branch=""):
    root = cache_root()
    if not root:
        raise ValueError("repo cache requested while local caching is disabled")
    destination = Path(destination).resolve()
    if root == destination or root in destination.parents:
        raise ValueError("build checkout must be outside the download cache")
    if (destination / ".repo").exists():
        raise ValueError("repo build destination must be a fresh checkout")
    identity = f"repo:{manifest_url}:{branch}"
    client = root / "repo" / key(identity)
    client.mkdir(parents=True, exist_ok=True)
    with locked(root, identity):
        pinned = client / "abk-resolved.xml"
        previous = revisions(pinned) if pinned.exists() else {}
        init = [repo, "init", "--depth=1", "--no-clone-bundle", "-u", manifest_url,
                "-b", branch, "--repo-rev=v2.16"]
        run(init, cwd=client, retry=True)
        remote_branch = configure_kernel_branch(client, kernel_branch) if kernel_branch else ""
        # The canonical client is never patched; only its remote source is synced.
        run([repo, "sync", "-c", "-d", "--optimized-fetch", "--no-tags", "--fail-fast",
             "-j", str(os.cpu_count() or 4)], cwd=client, retry=True)
        run([repo, "forall", "-c", "git reset --hard HEAD && git clean -ffdx"], cwd=client)
        run([repo, "manifest", "-r", "-o", str(pinned)], cwd=client)
        current = revisions(pinned)
        for name, sha in sorted(current.items()):
            report(name, previous.get(name, ""), sha)
        for name in sorted(previous.keys() - current.keys()):
            print(f"[download-cache] {name}: removed from manifest")
        # repo 2.16 --reference does not propagate shallow boundaries. Copy the
        # complete, clean checkout metadata instead; only immutable pack/index
        # files may be hard-linked. Source files must be independent of cache.
        def copy_file(source, target):
            relative = Path(source).relative_to(client)
            if relative.parts[0] == ".repo" and Path(source).suffix in (".pack", ".idx", ".rev"):
                try:
                    os.link(source, target)
                    return target
                except OSError as error:
                    if error.errno not in (errno.EXDEV, errno.EPERM, errno.EOPNOTSUPP):
                        raise
            return shutil.copy2(source, target)

        shutil.copytree(client, destination, dirs_exist_ok=True, symlinks=True, copy_function=copy_file)
        for name, sha in current.items():
            actual = run(["git", "-C", str(destination / name), "rev-parse", "HEAD"], capture=True)
            if actual != sha:
                raise RuntimeError(f"cached checkout does not match pinned revision: {name}")
        if remote_branch and os.environ.get("GITHUB_ENV"):
            with open(os.environ["GITHUB_ENV"], "a", encoding="utf-8") as handle:
                handle.write("REMOTE_BRANCH=" + remote_branch + "\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("configure")
    clone_parser = commands.add_parser("clone")
    clone_parser.add_argument("url")
    clone_parser.add_argument("destination")
    clone_parser.add_argument("--ref", default="")
    clone_parser.add_argument("--depth", type=int, default=0)
    sync_parser = commands.add_parser("sync-repo")
    sync_parser.add_argument("--repo", required=True)
    sync_parser.add_argument("--manifest-url", required=True)
    sync_parser.add_argument("--branch", required=True)
    sync_parser.add_argument("--destination", required=True)
    sync_parser.add_argument("--kernel-branch", default="")
    args = parser.parse_args()
    if args.command == "configure":
        configure()
    elif args.command == "clone":
        clone(args.url, args.destination, args.ref, args.depth)
    else:
        sync_repo(args.repo, args.manifest_url, args.branch, args.destination, args.kernel_branch)


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, RuntimeError) as error:
        print(f"::error::{error}", file=sys.stderr)
        sys.exit(1)
