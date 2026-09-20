#!/usr/bin/env python3
"""Maintain the signed requested/effective/skipped feature status document."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


def read_status(path: Path) -> dict:
    if not path.is_file():
        return {"requested": {}, "effective": {}, "skipped": []}
    return json.loads(path.read_text(encoding="utf-8"))


def write_status(path: Path, value: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def parse_feature(value: str) -> tuple[str, object]:
    name, separator, raw = value.partition("=")
    if not separator or not name.strip():
        raise argparse.ArgumentTypeError("feature must use NAME=VALUE")
    normalized = raw.strip()
    if normalized.lower() in {"true", "false"}:
        parsed: object = normalized.lower() == "true"
    else:
        parsed = normalized
    return name.strip(), parsed


def init_command(args: argparse.Namespace) -> int:
    requested = dict(args.feature)
    status = {"requested": requested, "effective": dict(requested), "skipped": []}
    write_status(Path(args.file), status)
    return 0


def mark_command(args: argparse.Namespace) -> int:
    path = Path(args.file)
    status = read_status(path)
    status.setdefault("requested", {})
    status.setdefault("effective", {})
    skipped = status.setdefault("skipped", [])
    if args.state == "skipped":
        requested = status["requested"].get(args.id)
        status["effective"][args.id] = False if isinstance(requested, bool) else "None"
        skipped[:] = [item for item in skipped if item.get("id") != args.id]
        skipped.append(
            {
                "id": args.id,
                "reason_code": args.reason_code,
                "message": args.message,
            }
        )
    else:
        status["effective"][args.id] = status["requested"].get(args.id, True)
        skipped[:] = [item for item in skipped if item.get("id") != args.id]
    write_status(path, status)
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)
    init_parser = subparsers.add_parser("init")
    init_parser.add_argument("--file", required=True)
    init_parser.add_argument("--feature", type=parse_feature, action="append", default=[])
    init_parser.set_defaults(func=init_command)

    mark_parser = subparsers.add_parser("mark")
    mark_parser.add_argument("--file", required=True)
    mark_parser.add_argument("--id", required=True)
    mark_parser.add_argument("--state", choices=("effective", "skipped"), required=True)
    mark_parser.add_argument("--reason-code", default="")
    mark_parser.add_argument("--message", default="")
    mark_parser.set_defaults(func=mark_command)
    return parser


def main() -> int:
    args = build_parser().parse_args()
    return args.func(args)


if __name__ == "__main__":
    raise SystemExit(main())
