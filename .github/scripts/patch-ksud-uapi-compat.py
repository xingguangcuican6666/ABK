#!/usr/bin/env python3
from __future__ import annotations

import pathlib
import sys


MARKER = "ABK legacy uapi=0 compatibility"

ORIGINAL = """pub fn ensure_uapi_version_matched() -> anyhow::Result<()> {
    let kernel_uapi = get_info().uapi_version;
    let userspace_uapi = uapi_version();
    if kernel_uapi != userspace_uapi {
        bail!(
            "UAPI version mismatch: kernel={kernel_uapi}, ksud={userspace_uapi}. Please update KernelSU!"
        );
    }
    Ok(())
}
"""

PATCHED = f"""pub fn ensure_uapi_version_matched() -> anyhow::Result<()> {{
    let kernel_uapi = get_info().uapi_version;
    let userspace_uapi = uapi_version();
    if kernel_uapi == 0 {{
        eprintln!(
            "[ABK] {MARKER}: kernel reports legacy uapi=0, continue with userspace uapi={{userspace_uapi}}"
        );
        return Ok(());
    }}
    if kernel_uapi != userspace_uapi {{
        bail!(
            "UAPI version mismatch: kernel={{kernel_uapi}}, ksud={{userspace_uapi}}. Please update KernelSU!"
        );
    }}
    Ok(())
}}
"""


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: patch-ksud-uapi-compat.py <sukisu-source-dir>", file=sys.stderr)
        return 2

    source_dir = pathlib.Path(sys.argv[1]).resolve()
    target = source_dir / "userspace" / "ksud" / "src" / "ksucalls.rs"
    if not target.is_file():
        print(f"::error::{target} not found", file=sys.stderr)
        return 1

    text = target.read_text(encoding="utf-8")
    if MARKER in text:
        print(f"ksud uapi compat patch already present: {target}")
        return 0

    if ORIGINAL not in text:
        print(f"::error::expected ensure_uapi_version_matched block not found in {target}", file=sys.stderr)
        return 1

    target.write_text(text.replace(ORIGINAL, PATCHED), encoding="utf-8")
    print(f"patched ksud legacy uapi compatibility: {target}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
