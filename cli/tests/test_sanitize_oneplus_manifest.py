import pathlib
import subprocess
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET


ROOT = pathlib.Path(__file__).resolve().parents[2]
SCRIPT_PATH = ROOT / ".github" / "scripts" / "sanitize-oneplus-manifest.py"

MANIFEST = """<?xml version="1.0" encoding="UTF-8"?>
<manifest>
  <!-- keep this note: it documents why the remote is pinned -->
  <remote name="aosp" fetch="https://android.googlesource.com/" />
  <project path="kernel_platform/common" name="kernel/common" remote="aosp" />

  <!-- asuite is not needed by the kernel build -->
  <project path="kernel_platform/prebuilts/asuite" name="kernel_platform/prebuilts/asuite" remote="aosp" />
  <project path="kernel_platform/prebuilts/tradefed" name="kernel_platform/tools/tradefederation/prebuilts" remote="aosp" />
  <project path="kernel_platform/build/kernel" name="kernel/build" remote="aosp" />
</manifest>
"""


class SanitizeOnePlusManifestTests(unittest.TestCase):
    """The manifest rewrite only drops the two optional prebuilt projects."""

    def _run(self, tmp: pathlib.Path, manifest_text: str) -> tuple[str, str]:
        manifest = tmp / "default.xml"
        manifest.write_text(manifest_text, encoding="utf-8")
        proc = subprocess.run(
            [sys.executable, str(SCRIPT_PATH), str(manifest)],
            capture_output=True,
            text=True,
        )
        if proc.returncode != 0:
            self.fail(f"script failed: {proc.returncode}\n{proc.stderr}")
        return manifest.read_text(encoding="utf-8"), proc.stdout

    def test_removes_only_the_optional_prebuilts(self):
        with tempfile.TemporaryDirectory() as raw:
            rewritten, output = self._run(pathlib.Path(raw), MANIFEST)

        self.assertNotIn("prebuilts/asuite", rewritten)
        self.assertNotIn("tradefederation", rewritten)
        self.assertIn('path="kernel_platform/common"', rewritten)
        self.assertIn('path="kernel_platform/build/kernel"', rewritten)
        self.assertIn("Removed optional OnePlus prebuilts", output)

        root = ET.fromstring(rewritten)
        self.assertEqual(len(root.findall("project")), 2)

    def test_keeps_manifest_comments(self):
        with tempfile.TemporaryDirectory() as raw:
            rewritten, _ = self._run(pathlib.Path(raw), MANIFEST)

        self.assertIn("keep this note", rewritten)
        self.assertIn("asuite is not needed by the kernel build", rewritten)

    def test_leaves_a_clean_manifest_untouched(self):
        clean = """<?xml version="1.0" encoding="UTF-8"?>
<manifest>
  <project path="kernel_platform/common" name="kernel/common" />
</manifest>
"""
        with tempfile.TemporaryDirectory() as raw:
            rewritten, output = self._run(pathlib.Path(raw), clean)

        self.assertEqual(rewritten, clean)
        self.assertIn("No optional OnePlus prebuilts needed removal.", output)
