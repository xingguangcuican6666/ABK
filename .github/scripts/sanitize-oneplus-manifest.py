#!/usr/bin/env python3
"""Remove optional OnePlus manifest projects that break repo sync.

Some upstream OnePlus manifests pin Android test prebuilts to CLO LA commits or
upstream branches that are not advertised by the remote anymore. Those prebuilts
are not used by the kernel build, but `repo sync -c` still fetches them and can
fail before the kernel source is available.
"""

from __future__ import annotations

import argparse
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


EXCLUDED_PROJECTS = (
    {
        "path": "kernel_platform/prebuilts/asuite",
        "name": "kernel_platform/prebuilts/asuite",
    },
    {
        "path": "kernel_platform/prebuilts/tradefed",
        "name": "kernel_platform/tools/tradefederation/prebuilts",
    },
)

EXCLUDED_PATHS = {project["path"] for project in EXCLUDED_PROJECTS}
EXCLUDED_NAMES = {project["name"] for project in EXCLUDED_PROJECTS}


def sanitize_manifest(manifest_path: Path) -> list[str]:
    """Remove known optional broken projects from a repo manifest."""

    manifest_path = Path(manifest_path)
    if not manifest_path.is_file():
        raise FileNotFoundError(f"manifest not found: {manifest_path}")

    tree = ET.parse(manifest_path)
    root = tree.getroot()
    removed: list[str] = []

    for project in list(root.findall("project")):
        path = project.get("path") or project.get("name") or ""
        name = project.get("name") or ""
        if path in EXCLUDED_PATHS or name in EXCLUDED_NAMES:
            root.remove(project)
            if name and name != path:
                removed.append(f"{path} ({name})")
            else:
                removed.append(path)

    if removed:
        if hasattr(ET, "indent"):
            ET.indent(tree, space="  ")
        tree.write(manifest_path, encoding="UTF-8", xml_declaration=True)

    return removed


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(
        description="Sanitize a OnePlus repo manifest before repo sync.",
    )
    parser.add_argument("manifest", type=Path, help="Path to the repo manifest XML")
    args = parser.parse_args(argv)

    try:
        removed = sanitize_manifest(args.manifest)
    except Exception as exc:
        print(f"::error::{exc}", file=sys.stderr)
        return 1

    if removed:
        print("Removed optional OnePlus prebuilts from manifest:")
        for project in removed:
            print(f"- {project}")
    else:
        print("No optional OnePlus prebuilts needed removal.")

    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
