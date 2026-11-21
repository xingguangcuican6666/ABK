"""Offline contract checks; never dispatch or execute a kernel build."""
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / ".github/scripts/build-module-options.py"
spec = importlib.util.spec_from_file_location("build_module_options", SCRIPT)
options = importlib.util.module_from_spec(spec)
spec.loader.exec_module(options)


class BuildModuleOptionsTest(unittest.TestCase):
    def test_legacy_strings_are_unchanged(self):
        for value in ("", "module:https://example.test/repo;after_patch|set:r#id;before", "abc\n中文$()"):
            self.assertEqual(value, options.decode_options(value)["external_modules"])
            self.assertEqual("", options.decode_options(value)["ksuhook_modules"])

    def test_envelope(self):
        payload = {"schema_version": 1, "external_modules": "module:r;s", "ksuhook_modules": "git@host:path@tag|https://host/r@v1", "ksuhook_sdk_ref": "a" * 40}
        self.assertEqual({k: v for k, v in payload.items() if k != "schema_version"}, options.decode_options(json.dumps(payload)))
        self.assertEqual("", options.decode_options('{"schema_version":1}')["external_modules"])

    def test_invalid_envelopes_fail_closed(self):
        for value in ('{', '[]', '{"schema_version":true}', '{"schema_version":2}', '{"schema_version":1,"unknown":"x"}', '{"schema_version":1,"ksuhook_modules":[]}', '{"schema_version":1,"external_modules":null}', '{"schema_version":1,"schema_version":1}', '{"schema_version":1,"ksuhook_sdk_ref":"main"}', '{"schema_version":1,"ksuhook_modules":"\\u0000"}'):
            with self.subTest(value=value), self.assertRaises(ValueError):
                options.decode_options(value)

    def test_sdk_default_and_explicit_commit(self):
        for value in ("", "   "):
            self.assertEqual(options.DEFAULT_SDK_REF, options.effective_sdk_ref(value))
        self.assertEqual("B" * 40, options.effective_sdk_ref("B" * 40))
        with self.assertRaises(ValueError):
            options.effective_sdk_ref("main")

    def test_multiline_output_does_not_inject_keys(self):
        value = 'line1\nkey=evil\nabk_end\n中文\n${{ secrets.NO }}\n'
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "output"
            options.write_outputs(output, {"external_modules": value, "ksuhook_modules": ""})
            lines = output.read_text().splitlines(keepends=True)
            decoded = {}
            while lines:
                key, delimiter = lines.pop(0).rstrip("\n").split("<<", 1)
                contents = []
                while lines[0] != delimiter + "\n":
                    contents.append(lines.pop(0))
                lines.pop(0)
                decoded[key] = "".join(contents)[:-1]
            self.assertEqual(value, decoded["external_modules"])
            self.assertEqual({"external_modules", "ksuhook_modules"}, set(decoded))

    def test_cli_rejects_patches_without_kernelsu(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "output"
            env = dict(os.environ, GITHUB_OUTPUT=str(output), ABK_KSU_VARIANT="None", ABK_BUILD_MODULE_OPTIONS=json.dumps({"schema_version": 1, "ksuhook_modules": "https://host/r"}))
            result = subprocess.run([sys.executable, str(SCRIPT)], env=env, capture_output=True)
            self.assertNotEqual(0, result.returncode)
            self.assertFalse(output.exists())

    def test_dispatch_inputs_keep_twenty_five_fields(self):
        # Count only exact six-space dispatch keys, not nested options or job outputs.
        import re
        for name in ("kernel-custom.yml", "kernel-source.yml"):
            text = (ROOT / ".github/workflows" / name).read_text()
            declaration = text.split("jobs:", 1)[0]
            keys = re.findall(r"^      ([a-z_]+):", declaration, re.M)
            self.assertEqual(25, len(keys), (name, keys))
            self.assertNotIn("ksuhook_modules", keys)
            self.assertIn("custom_external_modules", keys)
            self.assertIn("ABK_BUILD_MODULE_OPTIONS_SCHEMA: 1", text)
            for field in ("external_modules", "ksuhook_modules", "ksuhook_sdk_ref"):
                self.assertIn("needs.preflight.outputs." + field, text)

    def test_sdk_summary_and_checkout_use_same_effective_ref(self):
        text = (ROOT / ".github/workflows/build.yml").read_text()
        self.assertIn("--sdk-only", text)
        self.assertIn("ABK_INPUT_KSUHOOK_SDK_REF: ${{ steps.module-sdk.outputs.sdk_ref }}", text)
        self.assertIn("ref: ${{ steps.module-sdk.outputs.sdk_ref }}", text)
        self.assertIn("if: inputs.ksuhook_modules != ''\n        uses: actions/checkout@v6", text)


if __name__ == "__main__":
    unittest.main()
