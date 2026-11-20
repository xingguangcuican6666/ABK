"""Check production runner patterns with the native ICU engine used by Android."""
import contextlib
import ctypes
import ctypes.util
import json
import re
import sys
import unittest
from pathlib import Path


SOURCE = Path(__file__).resolve().parents[2] / (
    "app/src/main/java/com/abk/kernel/data/repository/KernelRunnerSettingsStore.kt"
)


def production_pattern(name):
    source = SOURCE.read_text(encoding="utf-8")
    prefix = rf"\bval\s+{re.escape(name)}\s*=\s*Regex\("
    raw = re.search(prefix + r'"""(.*?)"""\)', source, re.DOTALL)
    if raw:
        return raw.group(1)
    quoted = re.search(prefix + r'("(?:\\.|[^"\\])*")\)', source)
    if not quoted:
        raise AssertionError(f"Cannot extract production Regex: {name}")
    return json.loads(quoted.group(1))


def utf16(text):
    encoded = text.encode("utf-16-le" if sys.byteorder == "little" else "utf-16-be")
    return (ctypes.c_uint16 * (len(encoded) // 2)).from_buffer_copy(encoded)


class ParseError(ctypes.Structure):
    _fields_ = [("line", ctypes.c_int32), ("offset", ctypes.c_int32),
                ("pre_context", ctypes.c_uint16 * 16), ("post_context", ctypes.c_uint16 * 16)]


class KernelRunnerIcuRegexTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        library_name = ctypes.util.find_library("icui18n")
        if not library_name:
            raise unittest.SkipTest("native ICU regex library (icui18n) is not installed")
        try:
            cls.library = ctypes.CDLL(library_name)
        except OSError as error:
            raise unittest.SkipTest(f"native ICU regex library is unavailable: {error}") from error
        # Linux uses e.g. uregex_open_74; other ICU builds export unsuffixed names.
        suffixes = [""] + ["_" + version for version in re.findall(r"\d+", Path(library_name).name)]
        cls.api = {}
        signatures = {
            "open": ([ctypes.POINTER(ctypes.c_uint16), ctypes.c_int32, ctypes.c_uint32,
                      ctypes.POINTER(ParseError), ctypes.POINTER(ctypes.c_int32)], ctypes.c_void_p),
            "close": ([ctypes.c_void_p], None),
            "setText": ([ctypes.c_void_p, ctypes.POINTER(ctypes.c_uint16), ctypes.c_int32,
                         ctypes.POINTER(ctypes.c_int32)], None),
            "matches": ([ctypes.c_void_p, ctypes.c_int32, ctypes.POINTER(ctypes.c_int32)], ctypes.c_int8),
            "start": ([ctypes.c_void_p, ctypes.c_int32, ctypes.POINTER(ctypes.c_int32)], ctypes.c_int32),
            "end": ([ctypes.c_void_p, ctypes.c_int32, ctypes.POINTER(ctypes.c_int32)], ctypes.c_int32),
        }
        for name, (arguments, result) in signatures.items():
            for suffix in suffixes:
                function = getattr(cls.library, "uregex_" + name + suffix, None)
                if function is not None:
                    function.argtypes = arguments
                    function.restype = result
                    cls.api[name] = function
                    break
            else:
                raise AssertionError(f"ICU library lacks uregex_{name}: {library_name}")

    @contextlib.contextmanager
    def compiled(self, name):
        pattern = production_pattern(name)
        encoded = utf16(pattern)
        status = ctypes.c_int32(0)
        parse = ParseError()
        handle = self.api["open"](encoded, len(encoded), 0, ctypes.byref(parse), ctypes.byref(status))
        try:
            self.assertLessEqual(status.value, 0,
                                 f"{name}: native ICU error {status.value} at offset {parse.offset}: {pattern}")
            self.assertTrue(handle, f"ICU returned no compiled pattern for {name}")
            yield handle
        finally:
            if handle:
                self.api["close"](handle)

    def matches(self, handle, text, capture=False):
        encoded = utf16(text)
        status = ctypes.c_int32(0)
        self.api["setText"](handle, encoded, len(encoded), ctypes.byref(status))
        matched = bool(self.api["matches"](handle, 0, ctypes.byref(status)))
        captured = None
        if matched and capture:
            start = self.api["start"](handle, 1, ctypes.byref(status))
            end = self.api["end"](handle, 1, ctypes.byref(status))
            # Fixtures below are ASCII, so ICU's UTF-16 offsets index text directly.
            captured = text[start:end]
        self.assertLessEqual(status.value, 0, f"native ICU matching error {status.value}")
        return matched, captured

    def test_runner_expression_compiles_and_captures_github_expressions(self):
        with self.compiled("runnerExpression") as handle:
            for target in ("KERNEL", "ONEPLUS"):
                expression = (
                    f" vars.{target}_SELF_HOSTED == 'false' && 'ubuntu-latest' || "
                    f"(startsWith(vars.{target}_RUNNER, '[') && fromJSON(vars.{target}_RUNNER) "
                    f"|| vars.{target}_RUNNER || 'ubuntu-latest') "
                )
                for comment in ("", " # keep runner labels"):
                    line = "    runs-on: ${{" + expression + "}}" + comment
                    with self.subTest(target=target, comment=comment):
                        self.assertEqual(self.matches(handle, line, capture=True), (True, expression))

    def test_runner_expression_rejects_comments_and_plain_labels(self):
        with self.compiled("runnerExpression") as handle:
            for line in (" # runs-on: ${{ vars.KERNEL_RUNNER }}", "runs-on: ubuntu-latest",
                         "runs-on: ${{ vars.KERNEL_RUNNER }", "runner: ${{ vars.KERNEL_RUNNER }}"):
                with self.subTest(line=line):
                    self.assertEqual(self.matches(handle, line), (False, None))

    def test_hosted_label_pattern_compiles_and_matches_complete_labels(self):
        with self.compiled("hostedLabel") as handle:
            for label in ("ubuntu-latest", "WINDOWS-2025", "macos-15-large"):
                with self.subTest(label=label):
                    self.assertEqual(self.matches(handle, label), (True, None))
            for label in ("self-hosted", "kernel-builder", "my-ubuntu-latest"):
                with self.subTest(label=label):
                    self.assertEqual(self.matches(handle, label), (False, None))


if __name__ == "__main__":
    unittest.main()
