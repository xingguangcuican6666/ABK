#!/usr/bin/env python3
"""Decode the versioned build-module envelope without executing user input."""

from __future__ import annotations

import argparse
import json
import os
import re
import secrets
from pathlib import Path

DEFAULT_SDK_REF = "c6473f97ff516f2b3184b6a2d63753c8591a8edb"
FIELDS = {"schema_version", "external_modules", "ksuhook_modules", "ksuhook_sdk_ref"}


def unique_object(pairs: list[tuple[str, object]]) -> dict[str, object]:
    result: dict[str, object] = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"Duplicate module-options field: {key}")
        result[key] = value
    return result


def decode_options(raw: str) -> dict[str, str]:
    if not raw.lstrip().startswith(("{", "[")):
        return {"external_modules": raw, "ksuhook_modules": "", "ksuhook_sdk_ref": ""}
    try:
        value = json.loads(raw, object_pairs_hook=unique_object)
    except (ValueError, TypeError) as exc:
        raise ValueError("Invalid build-module options JSON") from exc
    if not isinstance(value, dict):
        raise ValueError("Build-module options must be an object")
    if type(value.get("schema_version")) is not int or value["schema_version"] != 1:
        raise ValueError("Unsupported build-module options schema_version")
    if set(value) - FIELDS:
        raise ValueError("Unknown build-module options fields")
    result = {}
    for key in ("external_modules", "ksuhook_modules", "ksuhook_sdk_ref"):
        field = value.get(key, "")
        if not isinstance(field, str) or "\0" in field:
            raise ValueError(f"Build-module option {key} must be a string without NUL")
        result[key] = field
    # SDK revisions are immutable commits, never executable expressions or branches.
    if result["ksuhook_sdk_ref"] and not re.fullmatch(r"[0-9a-fA-F]{40}", result["ksuhook_sdk_ref"]):
        raise ValueError("NyaSU SDK ref must be a full 40-character commit SHA")
    return result


def effective_sdk_ref(raw: str) -> str:
    ref = raw.strip() or DEFAULT_SDK_REF
    if not re.fullmatch(r"[0-9a-fA-F]{40}", ref):
        raise ValueError("NyaSU SDK ref must be a full 40-character commit SHA")
    return ref


def write_outputs(path: Path, values: dict[str, str]) -> None:
    with path.open("a", encoding="utf-8", newline="\n") as stream:
        for key, value in values.items():
            delimiter = f"abk_{secrets.token_hex(24)}"
            while delimiter in value.splitlines():
                delimiter = f"abk_{secrets.token_hex(24)}"
            stream.write(f"{key}<<{delimiter}\n{value}\n{delimiter}\n")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sdk-only", action="store_true")
    args = parser.parse_args()
    if args.sdk_only:
        values = {"sdk_ref": effective_sdk_ref(os.environ.get("ABK_INPUT_KSUHOOK_SDK_REF", ""))}
    else:
        values = decode_options(os.environ.get("ABK_BUILD_MODULE_OPTIONS", ""))
        if values["ksuhook_modules"].strip() and os.environ.get("ABK_KSU_VARIANT") == "None":
            raise ValueError("KernelSU patch modules require a KernelSU variant")
    write_outputs(Path(os.environ["GITHUB_OUTPUT"]), values)


if __name__ == "__main__":
    main()
