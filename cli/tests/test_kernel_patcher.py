"""ABK compatibility wrapper for the NyaSU SDK multi-worktree orchestrator."""

from __future__ import annotations

import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PATCHER_PATH = ROOT / ".github" / "scripts" / "kernel-patcher.py"


class KernelPatcherEntrypointTests(unittest.TestCase):
    def test_fails_clearly_when_sdk_orchestrator_is_missing(self):
        with tempfile.TemporaryDirectory() as temp:
            env = {"PATH": os.environ.get("PATH", ""), "NYASU_SDK_DIR": temp}
            result = subprocess.run(
                [sys.executable, str(PATCHER_PATH), "--help"],
                cwd=ROOT,
                env=env,
                capture_output=True,
                text=True,
            )
        self.assertEqual(result.returncode, 2)
        self.assertIn("找不到 NyaSU SDK 编排器", result.stderr)

    def test_loads_orchestrator_from_nyasu_sdk_dir_and_forwards_argv(self):
        with tempfile.TemporaryDirectory() as temp:
            sdk = Path(temp) / "sdk"
            (sdk / "ksuhook").mkdir(parents=True)
            (sdk / "ksuhook" / "orchestrator.py").write_text(
                "import sys\n"
                "def main(argv=None):\n"
                "    print('forwarded: ' + ' '.join(argv or sys.argv[1:]))\n"
                "    return 0\n",
                encoding="utf-8",
            )
            env = {"PATH": os.environ.get("PATH", ""), "NYASU_SDK_DIR": str(sdk)}
            result = subprocess.run(
                [sys.executable, str(PATCHER_PATH), "clone", "--modules", "https://x/a.git"],
                cwd=ROOT,
                env=env,
                capture_output=True,
                text=True,
            )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("forwarded: clone --modules https://x/a.git", result.stdout)

    def test_orchestrator_error_exit_code_is_propagated(self):
        with tempfile.TemporaryDirectory() as temp:
            sdk = Path(temp) / "sdk"
            (sdk / "ksuhook").mkdir(parents=True)
            (sdk / "ksuhook" / "orchestrator.py").write_text(
                "def main(argv=None):\n"
                "    return 3\n",
                encoding="utf-8",
            )
            env = {"PATH": os.environ.get("PATH", ""), "NYASU_SDK_DIR": str(sdk)}
            result = subprocess.run(
                [sys.executable, str(PATCHER_PATH), "apply"],
                cwd=ROOT,
                env=env,
                capture_output=True,
                text=True,
            )
        self.assertEqual(result.returncode, 3)


if __name__ == "__main__":
    unittest.main()
