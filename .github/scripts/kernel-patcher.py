#!/usr/bin/env python3
"""ABK compatibility entry point for the NyaSU ksuhook SDK orchestrator."""

from __future__ import annotations

import importlib.util
import os
import sys
from pathlib import Path


def main(argv: list[str] | None = None) -> int:
    sdk_value = os.environ.get("NYASU_SDK_DIR")
    sdk = Path(sdk_value) if sdk_value else Path(__file__).resolve().parents[2] / "nyasu-sdk" / "sdk"
    module_path = sdk / "ksuhook" / "orchestrator.py"
    if not module_path.is_file():
        print(f"error: 找不到 NyaSU SDK 编排器: {module_path}", file=sys.stderr)
        return 2
    spec = importlib.util.spec_from_file_location("ksuhook_orchestrator", module_path)
    if spec is None or spec.loader is None:
        print(f"error: 无法加载 NyaSU SDK 编排器: {module_path}", file=sys.stderr)
        return 2
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module.main(argv)


if __name__ == "__main__":
    raise SystemExit(main())
