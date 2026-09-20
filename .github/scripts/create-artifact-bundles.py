#!/usr/bin/env python3
"""Create signed ABK artifact bundles with backward-compatible schema 1 manifests."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import subprocess
import tempfile
from datetime import datetime, timezone
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile


MANIFEST_NAME = "ABK_BUNDLE_MANIFEST.json"
SIGNATURE_NAME = "ABK_BUNDLE_MANIFEST.sig"
TEXT_MANIFEST_NAME = "ABK_BUNDLE_MANIFEST.txt"
DOC_NAMES = ("LICENSE", "THIRD_PARTY_NOTICES.md")


def artifact_type(name: str) -> str:
    lower = name.lower()
    if lower.endswith(".img"):
        return "KERNEL_IMG"
    if "anykernel" in lower or "ak3" in lower:
        return "ANYKERNEL3"
    return "OTHER"


def payload_kind(name: str) -> str | None:
    lower = name.lower()
    if lower.endswith("images.zip"):
        return "KERNEL_IMAGE_SET"
    if "anykernel" in lower or "ak3" in lower:
        return "ANYKERNEL3"
    return None


def load_json_env(name: str, default: object) -> object:
    value = os.environ.get(name, "").strip()
    return json.loads(value) if value else default


def custom_source_manifest() -> dict[str, object] | None:
    if os.environ.get("ABK_SOURCE_MODE") != "custom_git":
        return None
    defconfigs = load_json_env("ABK_CUSTOM_SOURCE_DEFCONFIGS_JSON", [])
    return {
        "mode": "lineage_like_git",
        "url": os.environ["ABK_CUSTOM_SOURCE_URL"],
        "access": os.environ.get("ABK_CUSTOM_SOURCE_ACCESS", "public"),
        "requested_ref": os.environ["ABK_CUSTOM_SOURCE_REF"],
        "resolved_commit": os.environ["ABK_CUSTOM_SOURCE_COMMIT"],
        "kernel_version": os.environ["ABK_CUSTOM_SOURCE_KERNEL_VERSION"],
        "android_version": os.environ["ABK_CUSTOM_SOURCE_ANDROID_VERSION"],
        "toolchain_patch_level": os.environ["ABK_CUSTOM_SOURCE_PATCH_LEVEL"],
        "device_label": os.environ.get("ABK_CUSTOM_SOURCE_DEVICE_LABEL", ""),
        "defconfigs": defconfigs,
    }


def load_feature_status() -> dict[str, object]:
    path_value = os.environ.get("ABK_CUSTOM_SOURCE_FEATURE_STATUS", "")
    path = Path(path_value) if path_value else None
    if path and path.is_file():
        return json.loads(path.read_text(encoding="utf-8"))
    if os.environ.get("ABK_SOURCE_MODE") == "custom_git":
        raise SystemExit("custom source feature status is missing")
    return {"requested": {}, "effective": {}, "skipped": []}


def validate_bundle(bundle_path: Path, key_file: Path | None, signature_required: bool) -> None:
    with ZipFile(bundle_path) as archive:
        manifest_bytes = archive.read(MANIFEST_NAME)
        manifest = json.loads(manifest_bytes)
        payload_bytes = archive.read(manifest["payload_name"])
        if len(payload_bytes) != manifest["payload_size_bytes"]:
            raise SystemExit(f"payload size verification failed for {bundle_path.name}")
        if hashlib.sha256(payload_bytes).hexdigest() != manifest["payload_sha256"]:
            raise SystemExit(f"payload digest verification failed for {bundle_path.name}")
        signature = archive.read(SIGNATURE_NAME) if SIGNATURE_NAME in archive.namelist() else None

    if signature_required and signature is None:
        raise SystemExit(f"missing signature in {bundle_path.name}")
    if signature is None or key_file is None:
        return
    with tempfile.TemporaryDirectory(prefix="abk-bundle-verify-") as temp_value:
        temp = Path(temp_value)
        public_key = temp / "public.pem"
        manifest_file = temp / "manifest.json"
        signature_file = temp / "manifest.sig"
        manifest_file.write_bytes(manifest_bytes)
        signature_file.write_bytes(signature)
        subprocess.run(
            ["openssl", "pkey", "-in", str(key_file), "-pubout", "-out", str(public_key)],
            check=True,
            stdout=subprocess.DEVNULL,
        )
        subprocess.run(
            [
                "openssl", "dgst", "-sha256", "-verify", str(public_key),
                "-signature", str(signature_file), str(manifest_file),
            ],
            check=True,
            stdout=subprocess.DEVNULL,
        )


def human_manifest(payload_name: str, manifest: dict[str, object]) -> str:
    lines = [f"payload={payload_name}"]
    source = manifest.get("kernel_source")
    if isinstance(source, dict):
        lines.extend(
            [
                f"source_url={source.get('url', '')}",
                f"source_ref={source.get('requested_ref', '')}",
                f"source_commit={source.get('resolved_commit', '')}",
                f"source_access={source.get('access', '')}",
                f"kernel_version={source.get('kernel_version', '')}",
                f"android_version={source.get('android_version', '')}",
                f"toolchain_patch_level={source.get('toolchain_patch_level', '')}",
                f"device_label={source.get('device_label', '')}",
            ]
        )
        for index, item in enumerate(source.get("defconfigs", []), start=1):
            lines.append(f"defconfig_{index}={item}")
    feature_status = manifest.get("feature_status")
    if isinstance(feature_status, dict):
        for item in feature_status.get("skipped", []):
            lines.append(
                "skipped_feature="
                f"{item.get('id', '')}:{item.get('reason_code', '')}:{item.get('message', '')}"
            )
    return "\n".join(lines) + "\n"


def iter_payloads(root: Path, custom_source: bool) -> list[Path]:
    payloads: list[Path] = []
    for payload in sorted(root.glob("*")):
        if not payload.is_file() or payload.name.endswith(".bundle.zip"):
            continue
        if payload.suffix.lower() == ".img" or payload.name.endswith("AnyKernel3.zip"):
            payloads.append(payload)
        elif custom_source and payload.name.endswith("Images.zip"):
            payloads.append(payload)
    return payloads


def create_bundles(root: Path, key_file: Path | None, require_signature: bool) -> list[Path]:
    custom_source = os.environ.get("ABK_SOURCE_MODE") == "custom_git"
    if require_signature and (key_file is None or not key_file.is_file()):
        raise SystemExit("custom source bundles require ABK artifact signing")

    docs = {name: root / name for name in DOC_NAMES}
    for path in docs.values():
        if not path.is_file():
            raise SystemExit(f"missing compliance document: {path.name}")

    source = custom_source_manifest()
    feature_status = load_feature_status() if custom_source else None
    created: list[Path] = []
    for payload in iter_payloads(root, custom_source):
        bundle_path = payload.with_name(f"{payload.name}.bundle.zip")
        payload_bytes = payload.read_bytes()
        manifest: dict[str, object] = {
            "schema": 1,
            "bundle_name": bundle_path.name,
            "artifact_type": artifact_type(payload.name),
            "run_id": int(os.environ.get("GITHUB_RUN_ID", "0")),
            "payload_name": payload.name,
            "payload_sha256": hashlib.sha256(payload_bytes).hexdigest(),
            "payload_size_bytes": len(payload_bytes),
            "created_at": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        }
        kind = payload_kind(payload.name)
        if kind:
            manifest["payload_kind"] = kind
        if source is not None:
            manifest["kernel_source"] = source
            manifest["feature_status"] = feature_status
            skipped = feature_status.get("skipped", []) if isinstance(feature_status, dict) else []
            manifest["client_notice"] = {
                "type": "custom_source_build",
                "version": 1,
                "capability": "custom_source_notice_v1",
                "severity": "warning" if skipped else "info",
                "review_before_flash": True,
            }

        manifest_bytes = json.dumps(manifest, sort_keys=True, separators=(",", ":")).encode("utf-8")
        with ZipFile(bundle_path, "w", compression=ZIP_DEFLATED, compresslevel=9) as archive:
            archive.writestr(MANIFEST_NAME, manifest_bytes)
            if key_file and key_file.is_file():
                signature = subprocess.check_output(
                    ["openssl", "dgst", "-sha256", "-sign", str(key_file), "-binary"],
                    input=manifest_bytes,
                )
                archive.writestr(SIGNATURE_NAME, signature)
            archive.writestr(TEXT_MANIFEST_NAME, human_manifest(payload.name, manifest))
            archive.write(payload, arcname=payload.name)
            for doc_name, doc_path in docs.items():
                archive.write(doc_path, arcname=doc_name)
        validate_bundle(bundle_path, key_file, require_signature)
        created.append(bundle_path)
    return created


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default=".")
    parser.add_argument("--key-file")
    parser.add_argument("--require-signature", action="store_true")
    args = parser.parse_args()
    key_file = Path(args.key_file) if args.key_file else None
    created = create_bundles(Path(args.root), key_file, args.require_signature)
    if not created:
        raise SystemExit("no bundle payloads were found")
    for path in created:
        print(path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
