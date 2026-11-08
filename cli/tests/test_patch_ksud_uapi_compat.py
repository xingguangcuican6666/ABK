import pathlib
import shutil
import subprocess
import sys
import tempfile
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]
SCRIPT_PATH = ROOT / ".github" / "scripts" / "patch-ksud-uapi-compat.py"

SUKISU_URL = "https://github.com/SukiSU-Ultra/SukiSU-Ultra.git"

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


class PatchKsudUapiCompatTests(unittest.TestCase):
    """ksud refuses to run at all when its compiled-in UAPI version doesn't exactly
    match what the kernel reports (`ensure_uapi_version_matched` in ksucalls.rs).

    The app always bundles ksud built from SukiSU-Ultra main (build-abk-app.yml), but
    the kernels this project builds pin specific, often older, KernelSU/SukiSU/BakaSU
    commits (resolve-ksu-ref.sh). Every UAPI bump upstream has made so far only adds
    new commands/fields -- see the comment above PATCHED in the script itself -- so a
    newer ksud talking to an older kernel is safe; it just won't exercise the newer
    bits. This patch relaxes the exact-match gate to that one-directional tolerance
    without touching the case upstream still should guard: a kernel newer than this
    ksud build, which may expect behavior this build predates.
    """

    def _run(self, tmp: pathlib.Path, source: str):
        ksud_src = tmp / "userspace" / "ksud" / "src"
        ksud_src.mkdir(parents=True, exist_ok=True)
        (ksud_src / "ksucalls.rs").write_text(source, encoding="utf-8")
        result = subprocess.run(
            [sys.executable, str(SCRIPT_PATH), str(tmp)],
            capture_output=True,
            text=True,
        )
        return result, (ksud_src / "ksucalls.rs").read_text(encoding="utf-8")

    @staticmethod
    def _source(body: str = ORIGINAL) -> str:
        """A minimal but structurally realistic ksucalls.rs.

        The patch only anchors on the ensure_uapi_version_matched block, but wrapping
        it in the surrounding helpers it actually calls keeps the fixture honest about
        what upstream's file looks like around that function.
        """
        return (
            "use anyhow::{bail, Result};\n\n"
            "pub const fn uapi_version() -> u32 {\n"
            "    ksu_uapi::KERNEL_SU_UAPI_VERSION\n"
            "}\n\n"
            f"{body}"
        )

    def test_patches_fresh_source(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            result, patched = self._run(pathlib.Path(temp_dir), self._source())

            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn(MARKER, patched)
            self.assertIn("if kernel_uapi > userspace_uapi {", patched)
            self.assertIn("if kernel_uapi != userspace_uapi {", patched)
            self.assertIn(
                'UAPI version mismatch: kernel={kernel_uapi}, ksud={userspace_uapi}. Please update KernelSU!',
                patched,
            )
            # The original gate bailed as soon as the two values merely differed --
            # that unconditional form must be gone, not just supplemented.
            self.assertNotIn(ORIGINAL, patched)

    def test_idempotent_second_run_is_noop(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            tmp = pathlib.Path(temp_dir)
            _, first_pass = self._run(tmp, self._source())
            second_result, second_pass = self._run(tmp, first_pass)

            self.assertEqual(second_result.returncode, 0, second_result.stderr)
            self.assertEqual(first_pass, second_pass)
            self.assertIn("already present", second_result.stdout)

    def test_missing_anchor_fails_loudly(self):
        """If upstream ever reshapes this function, the script must say so, not no-op.

        A silent no-op here would ship an unpatched ksud that still hard-fails on any
        UAPI mismatch -- the exact bug this patch exists to fix -- while CI stays green.
        """
        with tempfile.TemporaryDirectory() as temp_dir:
            drifted = self._source(
                'pub fn ensure_uapi_version_matched() -> anyhow::Result<()> {\n'
                "    Ok(()) // upstream rewrote this entirely\n"
                "}\n"
            )
            result, patched = self._run(pathlib.Path(temp_dir), drifted)

            self.assertEqual(result.returncode, 1)
            self.assertIn("ensure_uapi_version_matched", result.stderr)
            self.assertEqual(patched, drifted)

    def test_missing_file_fails_loudly(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            result = subprocess.run(
                [sys.executable, str(SCRIPT_PATH), temp_dir],
                capture_output=True,
                text=True,
            )
            self.assertEqual(result.returncode, 1)
            self.assertIn("ksucalls.rs", result.stderr)
            self.assertIn("not found", result.stderr)

    def test_patched_function_compiles_and_behaves_per_direction(self):
        """Exercise the actual generated Rust, not just its text shape.

        String assertions can't catch a brace-nesting slip that still produces
        plausible-looking text. This compiles the patched function standalone against
        stub get_info()/uapi_version() and checks all three directions: kernel older
        (continues), equal (continues silently), kernel newer (still bails).
        """
        if not shutil.which("rustc"):
            self.skipTest("rustc not available")

        with tempfile.TemporaryDirectory() as temp_dir:
            tmp = pathlib.Path(temp_dir)
            _, patched = self._run(tmp, self._source())

        func_start = patched.index("pub fn ensure_uapi_version_matched")
        func_body = patched[func_start:]

        harness_dir = pathlib.Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, harness_dir, ignore_errors=True)
        harness_src = harness_dir / "check.rs"
        harness_bin = harness_dir / "check"
        harness_src.write_text(
            "macro_rules! bail {\n"
            "    ($($arg:tt)*) => { return Err(format!($($arg)*)) };\n"
            "}\n\n"
            "struct Info { uapi_version: u32 }\n\n"
            "fn get_info() -> Info {\n"
            "    Info { uapi_version: std::env::var(\"TEST_KERNEL_UAPI\").unwrap().parse().unwrap() }\n"
            "}\n\n"
            "const fn uapi_version() -> u32 { 5 }\n\n"
            + func_body.replace("anyhow::Result<()>", "Result<(), String>")
            + "\n"
            "fn main() {\n"
            "    match ensure_uapi_version_matched() {\n"
            '        Ok(()) => println!("OK"),\n'
            '        Err(e) => println!("ERR: {e}"),\n'
            "    }\n"
            "}\n",
            encoding="utf-8",
        )
        compile_result = subprocess.run(
            ["rustc", "--edition", "2021", "-o", str(harness_bin), str(harness_src)],
            capture_output=True,
            text=True,
        )
        self.assertEqual(compile_result.returncode, 0, compile_result.stderr)

        cases = {
            "0": "OK",  # legacy pre-versioning kernel: still tolerated
            "2": "OK",  # older real UAPI version: now tolerated (this bug report)
            "5": "OK",  # exact match: unaffected happy path
            "6": "ERR",  # kernel newer than this ksud build: still refused
        }
        for kernel_uapi, expected_prefix in cases.items():
            with self.subTest(kernel_uapi=kernel_uapi):
                run_result = subprocess.run(
                    [str(harness_bin)],
                    capture_output=True,
                    text=True,
                    env={"TEST_KERNEL_UAPI": kernel_uapi, "PATH": "/usr/bin:/bin"},
                )
                self.assertEqual(run_result.returncode, 0, run_result.stderr)
                self.assertTrue(
                    run_result.stdout.startswith(expected_prefix),
                    f"kernel_uapi={kernel_uapi}: {run_result.stdout!r}",
                )

    def test_live_upstream_still_matches_anchor(self):
        """Guard against upstream reshaping ensure_uapi_version_matched again.

        SukiSU-Ultra main is exactly what build-abk-app.yml clones and patches, so this
        is the same check that workflow effectively makes -- just runnable locally
        without waiting on a full app build to find out the anchor went stale.
        """
        if not shutil.which("git"):
            self.skipTest("git is required to check out upstream SukiSU")
        with tempfile.TemporaryDirectory() as temp_dir:
            source_dir = pathlib.Path(temp_dir) / "sukisu"
            clone = subprocess.run(
                ["git", "clone", "--depth", "1", "--branch", "main", SUKISU_URL, str(source_dir)],
                capture_output=True,
                text=True,
            )
            if clone.returncode != 0:
                self.skipTest(f"could not clone SukiSU-Ultra: {clone.stderr.strip()[:200]}")

            result = subprocess.run(
                [sys.executable, str(SCRIPT_PATH), str(source_dir)],
                capture_output=True,
                text=True,
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn(
                MARKER,
                (source_dir / "userspace" / "ksud" / "src" / "ksucalls.rs").read_text(encoding="utf-8"),
            )


if __name__ == "__main__":
    unittest.main()
