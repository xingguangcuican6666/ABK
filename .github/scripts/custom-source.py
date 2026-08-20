#!/usr/bin/env python3
"""Validate and prepare LineageOS-like kernel source trees for ABK."""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path, PurePosixPath
from urllib.parse import urlsplit, urlunsplit


SUPPORTED_KERNELS = {
    (5, 10): "android12",
    (5, 15): "android13",
    (6, 1): "android14",
    (6, 6): "android15",
    (6, 12): "android16",
}
MONTH_PATTERN = re.compile(r"^\d{4}-(0[1-9]|1[0-2])$")
FULL_SHA_PATTERN = re.compile(r"^[0-9a-fA-F]{40}$")


class CustomSourceError(ValueError):
    pass


def normalize_source_url(value: str, private: bool = False) -> str:
    raw = value.strip()
    parsed = urlsplit(raw)
    if parsed.scheme.lower() != "https":
        raise CustomSourceError("source repository must use HTTPS")
    if not parsed.hostname:
        raise CustomSourceError("source repository is missing a host")
    if parsed.username or parsed.password:
        raise CustomSourceError("source repository URL must not contain credentials")
    if parsed.query or parsed.fragment:
        raise CustomSourceError("source repository URL must not contain a query or fragment")
    if parsed.port not in (None, 443):
        raise CustomSourceError("source repository must use the default HTTPS port")

    path = re.sub(r"/+", "/", parsed.path).rstrip("/")
    if not path or path == "/":
        raise CustomSourceError("source repository URL is missing a repository path")
    segments = [segment for segment in path.split("/") if segment]
    if any(segment in {".", ".."} for segment in segments):
        raise CustomSourceError("source repository URL contains path traversal")
    if private and parsed.hostname.lower() != "github.com":
        raise CustomSourceError("private source repositories are supported only on github.com")
    if private and len(segments) != 2:
        raise CustomSourceError("private GitHub source URL must use OWNER/REPO format")

    host = parsed.hostname.lower()
    normalized_path = "/" + "/".join(segments)
    return urlunsplit(("https", host, normalized_path, "", ""))


def parse_makefile_version(makefile: Path) -> tuple[int, int, int]:
    values: dict[str, int] = {}
    wanted = {"VERSION", "PATCHLEVEL", "SUBLEVEL"}
    for line in makefile.read_text(encoding="utf-8", errors="replace").splitlines():
        match = re.match(r"^\s*(VERSION|PATCHLEVEL|SUBLEVEL)\s*=\s*(\d+)\s*$", line)
        if match:
            values[match.group(1)] = int(match.group(2))
    missing = wanted - values.keys()
    if missing:
        raise CustomSourceError(f"Makefile is missing numeric version fields: {', '.join(sorted(missing))}")
    return values["VERSION"], values["PATCHLEVEL"], values["SUBLEVEL"]


def parse_defconfigs(raw: str) -> list[str]:
    entries = [line.strip() for line in raw.replace("\r", "").split("\n") if line.strip()]
    if not entries:
        raise CustomSourceError("at least one defconfig is required")
    if "gki_defconfig" not in entries:
        raise CustomSourceError("defconfig list must contain gki_defconfig")
    for entry in entries:
        path = PurePosixPath(entry)
        if path.is_absolute() or not path.parts:
            raise CustomSourceError(f"invalid defconfig path: {entry}")
        if any(part in {"", ".", ".."} for part in path.parts):
            raise CustomSourceError(f"unsafe defconfig path: {entry}")
        if "\\" in entry:
            raise CustomSourceError(f"defconfig path must use forward slashes: {entry}")
    return entries


def validate_tree(root: Path, defconfigs: list[str]) -> dict[str, object]:
    root = root.resolve()
    required = [
        root / "Makefile",
        root / "arch/arm64/configs",
        root / "scripts/kconfig/merge_config.sh",
    ]
    for path in required:
        if not path.exists():
            raise CustomSourceError(f"source tree is missing {path.relative_to(root)}")

    legacy = (root / "build.config.gki.aarch64").is_file()
    bazel_file = root / "BUILD.bazel"
    bazel = bazel_file.is_file() and any(
        marker in bazel_file.read_text(encoding="utf-8", errors="replace")
        for marker in ("kernel_aarch64", "define_common_kernels")
    )
    if not legacy and not bazel:
        raise CustomSourceError(
            "source tree must provide build.config.gki.aarch64 or a kernel_aarch64 Kleaf target"
        )

    gitmodules = root / ".gitmodules"
    if gitmodules.is_file() and gitmodules.read_text(encoding="utf-8", errors="replace").strip():
        raise CustomSourceError("Git submodules are not supported for custom source builds")
    attributes = root / ".gitattributes"
    if attributes.is_file() and re.search(
        r"(?:^|\s)filter=lfs(?:\s|$)",
        attributes.read_text(encoding="utf-8", errors="replace"),
        re.MULTILINE,
    ):
        raise CustomSourceError("Git LFS is not supported for custom source builds")

    config_root = (root / "arch/arm64/configs").resolve()
    for entry in defconfigs:
        candidate = config_root.joinpath(*PurePosixPath(entry).parts)
        try:
            resolved = candidate.resolve(strict=True)
        except FileNotFoundError as exc:
            raise CustomSourceError(f"defconfig does not exist: {entry}") from exc
        if not resolved.is_relative_to(config_root):
            raise CustomSourceError(f"defconfig escapes arch/arm64/configs: {entry}")
        if not resolved.is_file():
            raise CustomSourceError(f"defconfig is not a regular file: {entry}")

    major, patchlevel, sublevel = parse_makefile_version(root / "Makefile")
    android = SUPPORTED_KERNELS.get((major, patchlevel))
    if android is None:
        raise CustomSourceError(
            f"unsupported kernel line {major}.{patchlevel}; supported lines are 5.10, 5.15, 6.1, 6.6, 6.12"
        )
    return {
        "android_version": android,
        "kernel_version": f"{major}.{patchlevel}",
        "sub_level": str(sublevel),
        "full_kernel_version": f"{major}.{patchlevel}.{sublevel}",
        "build_backend": "legacy" if legacy else "kleaf",
    }


def write_github_output(values: dict[str, object], output_path: Path) -> None:
    with output_path.open("a", encoding="utf-8") as handle:
        for key, value in values.items():
            if isinstance(value, (list, dict)):
                rendered = json.dumps(value, ensure_ascii=False, separators=(",", ":"))
            else:
                rendered = str(value)
            handle.write(f"{key}={rendered}\n")


def inspect_command(args: argparse.Namespace) -> int:
    private = args.access == "github_private"
    url = normalize_source_url(args.source_url, private=private)
    if not MONTH_PATTERN.fullmatch(args.os_patch_level):
        raise CustomSourceError("os patch level must use YYYY-MM")
    if not args.source_ref.strip() or any(ord(char) < 0x20 for char in args.source_ref):
        raise CustomSourceError("source ref is required")
    if not FULL_SHA_PATTERN.fullmatch(args.resolved_commit):
        raise CustomSourceError("resolved commit must be a full 40-character SHA")

    defconfigs = parse_defconfigs(args.defconfigs)
    details = validate_tree(Path(args.source_dir), defconfigs)
    result: dict[str, object] = {
        **details,
        "source_url": url,
        "source_ref": args.source_ref.strip(),
        "source_access": args.access,
        "source_commit": args.resolved_commit.lower(),
        "source_defconfigs": defconfigs,
        "source_device_label": args.device_label.strip(),
        "os_patch_level": args.os_patch_level,
    }
    if any(ord(char) < 0x20 for char in result["source_device_label"]):
        raise CustomSourceError("device label must not contain control characters")
    if args.github_output:
        write_github_output(result, Path(args.github_output))
    print(json.dumps(result, ensure_ascii=False, sort_keys=True))
    return 0


def normalize_command(args: argparse.Namespace) -> int:
    print(normalize_source_url(args.source_url, private=args.access == "github_private"))
    return 0


def merge_command(args: argparse.Namespace) -> int:
    root = Path(args.source_dir).resolve()
    defconfigs = parse_defconfigs(args.defconfigs)
    validate_tree(root, defconfigs)
    config_root = root / "arch/arm64/configs"
    output = Path(args.output).resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = output.with_name(f".{output.name}.abk-merge")
    if temporary.exists():
        temporary.unlink()

    command = [
        "bash",
        str(root / "scripts/kconfig/merge_config.sh"),
        "-m",
        "-r",
        "-O",
        str(temporary.parent),
        *[str(config_root / entry) for entry in defconfigs],
    ]
    env = os.environ.copy()
    env["KCONFIG_CONFIG"] = str(temporary)
    subprocess.run(command, cwd=root, env=env, check=True)
    generated = temporary if temporary.is_file() else temporary.parent / ".config"
    if not generated.is_file():
        raise CustomSourceError("merge_config.sh did not produce a merged config")
    shutil.copyfile(generated, output)
    if generated != output:
        generated.unlink(missing_ok=True)
    print(f"merged {len(defconfigs)} defconfigs into {output}")
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)

    normalize_parser = subparsers.add_parser("normalize-url")
    normalize_parser.add_argument("--source-url", required=True)
    normalize_parser.add_argument("--access", choices=("public", "github_private"), required=True)
    normalize_parser.set_defaults(func=normalize_command)

    inspect_parser = subparsers.add_parser("inspect")
    inspect_parser.add_argument("--source-dir", required=True)
    inspect_parser.add_argument("--source-url", required=True)
    inspect_parser.add_argument("--source-ref", required=True)
    inspect_parser.add_argument("--resolved-commit", required=True)
    inspect_parser.add_argument("--access", choices=("public", "github_private"), required=True)
    inspect_parser.add_argument("--os-patch-level", required=True)
    inspect_parser.add_argument("--defconfigs", required=True)
    inspect_parser.add_argument("--device-label", default="")
    inspect_parser.add_argument("--github-output")
    inspect_parser.set_defaults(func=inspect_command)

    merge_parser = subparsers.add_parser("merge-defconfigs")
    merge_parser.add_argument("--source-dir", required=True)
    merge_parser.add_argument("--defconfigs", required=True)
    merge_parser.add_argument("--output", required=True)
    merge_parser.set_defaults(func=merge_command)
    return parser


def main() -> int:
    args = build_parser().parse_args()
    try:
        return args.func(args)
    except (CustomSourceError, subprocess.CalledProcessError) as exc:
        print(f"custom source validation failed: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
