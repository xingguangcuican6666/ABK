#!/usr/bin/env python3
from __future__ import annotations

import pathlib
import sys


MARKER = "ABK backward-compatible uapi check"

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

# KernelSU's UAPI bumps to date (2 allowlist v4 flags, 3 scoped su-session fd, 4
# KSU_GET_INFO_FLAG_BUNDLED, 5 EVENT_SERVICES) only ever add new commands/fields;
# none has changed an existing ioctl number or struct layout. So an older kernel is
# safe to drive with a newer ksud -- it just won't exercise the newer bits. A kernel
# newer than this ksud build is the genuinely risky direction (it may expect ksud to
# know about behavior this build predates), so that case still hard-fails.
PATCHED = f"""pub fn ensure_uapi_version_matched() -> anyhow::Result<()> {{
    let kernel_uapi = get_info().uapi_version;
    let userspace_uapi = uapi_version();
    if kernel_uapi > userspace_uapi {{
        bail!(
            "UAPI version mismatch: kernel={{kernel_uapi}}, ksud={{userspace_uapi}}. Please update KernelSU!"
        );
    }}
    if kernel_uapi != userspace_uapi {{
        eprintln!(
            "[ABK] {MARKER}: kernel uapi={{kernel_uapi}} is older than ksud uapi={{userspace_uapi}}, continuing for backward compatibility"
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
    print(f"patched ksud backward-compatible uapi check: {target}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
