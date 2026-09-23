"""Tests for custom-source.py kernel version detection, profile mapping, and override."""

import importlib.util
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CUSTOM_SOURCE_PATH = ROOT / ".github" / "scripts" / "custom-source.py"


def _load_module():
    spec = importlib.util.spec_from_file_location("custom_source", CUSTOM_SOURCE_PATH)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class ResolveBuildProfileTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.cs = _load_module()

    def test_below_lowest_line_maps_to_5_10(self):
        for major, patch in [(4, 14), (4, 19), (5, 4), (5, 9)]:
            with self.subTest(version=f"{major}.{patch}"):
                (pm, pp), android = self.cs.resolve_build_profile(major, patch)
                self.assertEqual((pm, pp), (5, 10))
                self.assertEqual(android, "android12")

    def test_exact_supported_lines(self):
        cases = {
            (5, 10): "android12",
            (5, 15): "android13",
            (6, 1): "android14",
            (6, 6): "android15",
            (6, 12): "android16",
        }
        for (major, patch), android in cases.items():
            with self.subTest(version=f"{major}.{patch}"):
                (pm, pp), got = self.cs.resolve_build_profile(major, patch)
                self.assertEqual((pm, pp), (major, patch))
                self.assertEqual(got, android)

    def test_between_lines_floors_down(self):
        self.assertEqual(self.cs.resolve_build_profile(5, 11)[0], (5, 10))
        self.assertEqual(self.cs.resolve_build_profile(5, 16)[0], (5, 15))
        self.assertEqual(self.cs.resolve_build_profile(6, 5)[0], (6, 1))
        self.assertEqual(self.cs.resolve_build_profile(6, 11)[0], (6, 6))

    def test_at_or_above_highest_line_maps_to_6_12(self):
        for major, patch in [(6, 12), (6, 13), (7, 0), (8, 3)]:
            with self.subTest(version=f"{major}.{patch}"):
                (pm, pp), android = self.cs.resolve_build_profile(major, patch)
                self.assertEqual((pm, pp), (6, 12))
                self.assertEqual(android, "android16")


class ParseKernelVersionOverrideTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.cs = _load_module()

    def test_two_component(self):
        self.assertEqual(self.cs.parse_kernel_version_override("5.10"), (5, 10, None))

    def test_three_component(self):
        self.assertEqual(self.cs.parse_kernel_version_override("6.13.42"), (6, 13, 42))

    def test_whitespace_tolerated(self):
        self.assertEqual(self.cs.parse_kernel_version_override("  5.4.210 "), (5, 4, 210))

    def test_invalid_raises(self):
        for bad in ["abc", "5", "5.", "5.x", "5.10.", "v5.10", "5.10.1.2"]:
            with self.subTest(value=bad):
                with self.assertRaises(self.cs.CustomSourceError):
                    self.cs.parse_kernel_version_override(bad)


class ValidateTreeTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.cs = _load_module()

    def _make_tree(self, version, patchlevel, sublevel):
        tmp = tempfile.mkdtemp()
        root = Path(tmp)
        (root / "arch/arm64/configs").mkdir(parents=True)
        (root / "scripts/kconfig").mkdir(parents=True)
        (root / "Makefile").write_text(
            f"VERSION = {version}\nPATCHLEVEL = {patchlevel}\nSUBLEVEL = {sublevel}\n",
            encoding="utf-8",
        )
        (root / "arch/arm64/configs/gki_defconfig").write_text("", encoding="utf-8")
        (root / "scripts/kconfig/merge_config.sh").write_text("", encoding="utf-8")
        (root / "build.config.gki.aarch64").write_text("", encoding="utf-8")
        return root

    def test_no_override_uses_makefile(self):
        root = self._make_tree(5, 10, 177)
        result = self.cs.validate_tree(root, ["gki_defconfig"])
        self.assertEqual(result["kernel_version"], "5.10")
        self.assertEqual(result["android_version"], "android12")
        self.assertEqual(result["sub_level"], "177")
        self.assertEqual(result["full_kernel_version"], "5.10.177")

    def test_below_5_10_source_maps_to_android12(self):
        root = self._make_tree(5, 4, 210)
        result = self.cs.validate_tree(root, ["gki_defconfig"])
        self.assertEqual(result["kernel_version"], "5.10")
        self.assertEqual(result["android_version"], "android12")
        self.assertEqual(result["full_kernel_version"], "5.4.210")

    def test_future_source_maps_to_android16(self):
        root = self._make_tree(6, 13, 5)
        result = self.cs.validate_tree(root, ["gki_defconfig"])
        self.assertEqual(result["kernel_version"], "6.12")
        self.assertEqual(result["android_version"], "android16")
        self.assertEqual(result["full_kernel_version"], "6.13.5")

    def test_override_full_version(self):
        root = self._make_tree(5, 10, 177)
        result = self.cs.validate_tree(root, ["gki_defconfig"], kernel_version_override="6.6.50")
        self.assertEqual(result["kernel_version"], "6.6")
        self.assertEqual(result["android_version"], "android15")
        self.assertEqual(result["sub_level"], "50")
        self.assertEqual(result["full_kernel_version"], "6.6.50")

    def test_override_without_sublevel_falls_back_to_makefile(self):
        root = self._make_tree(5, 10, 177)
        result = self.cs.validate_tree(root, ["gki_defconfig"], kernel_version_override="6.13")
        self.assertEqual(result["kernel_version"], "6.12")
        self.assertEqual(result["android_version"], "android16")
        self.assertEqual(result["sub_level"], "177")  # 回落 Makefile sublevel
        self.assertEqual(result["full_kernel_version"], "6.13.177")

    def test_override_invalid_raises(self):
        root = self._make_tree(5, 10, 177)
        with self.assertRaises(self.cs.CustomSourceError):
            self.cs.validate_tree(root, ["gki_defconfig"], kernel_version_override="nonsense")

    def test_empty_override_uses_makefile(self):
        root = self._make_tree(5, 15, 99)
        result = self.cs.validate_tree(root, ["gki_defconfig"], kernel_version_override="")
        self.assertEqual(result["kernel_version"], "5.15")
        self.assertEqual(result["full_kernel_version"], "5.15.99")


if __name__ == "__main__":
    unittest.main()
