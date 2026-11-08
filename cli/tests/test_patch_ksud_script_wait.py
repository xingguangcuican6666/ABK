import pathlib
import shutil
import subprocess
import sys
import tempfile
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]
SCRIPT_PATH = ROOT / ".github" / "scripts" / "patch-ksud-script-wait.py"
UAPI_PATCH_PATH = ROOT / ".github" / "scripts" / "patch-ksud-uapi-compat.py"
APP_WORKFLOW_PATH = ROOT / ".github" / "workflows" / "build-abk-app.yml"
APP_DEV_WORKFLOW_PATH = ROOT / ".github" / "workflows" / "build-abk-app-dev.yml"

SUKISU_URL = "https://github.com/SukiSU-Ultra/SukiSU-Ultra.git"

MARKER = "ABK upstream fix: exec_stage_lua call left out of step with run_stage"
FIXED_CALL = (
    'crate::module::exec_stage_lua(stage, !matches!(wait, ScriptWait::NoWait), "kernelsu")'
)
CFG_GATE = '#[cfg(all(target_os = "android", target_arch = "aarch64"))]'

# Each broken form upstream has shipped, with the compiler error it produces. 932d9bd2
# left the call naming `block`; 1c56b5e9 then swapped it to `wait` without widening
# exec_stage_lua. Both are cfg-gated away from upstream's own host-target CI.
BROKEN_FORMS = {
    "E0425": 'crate::module::exec_stage_lua(stage, block, "kernelsu")',
    "E0308": 'crate::module::exec_stage_lua(stage, wait, "kernelsu")',
}


class PatchKsudScriptWaitTests(unittest.TestCase):
    """The carried fix for the upstream ksud build breaks.

    Upstream has twice broken the one call site in run_stage that only an Android
    aarch64 build compiles. This patch carries the fix so the app builds keep working,
    and no-ops once upstream compiles it.
    """

    def _run(self, tmp: pathlib.Path, source: str, script: pathlib.Path | None = None):
        # Resolved at call time rather than as a default argument so a test can point
        # this at a different script; a default would be bound at def time and any such
        # override would silently be ignored.
        script = SCRIPT_PATH if script is None else script
        ksud_src = tmp / "userspace" / "ksud" / "src"
        ksud_src.mkdir(parents=True, exist_ok=True)
        (ksud_src / "init_event.rs").write_text(source, encoding="utf-8")
        result = subprocess.run(
            [sys.executable, str(script), str(tmp)],
            capture_output=True,
            text=True,
        )
        return result, (ksud_src / "init_event.rs").read_text(encoding="utf-8")

    @staticmethod
    def _source(call: str) -> str:
        """The call site exactly as upstream writes it.

        The patch anchors on the comment, the cfg gate and the call together, so the
        fixture has to carry all three -- a fixture that only spelled the call would
        test the "refuses unexpected surroundings" path instead of the happy one.
        """
        return (
            "use crate::module::ScriptWait;\n"
            "\n"
            "pub fn run_stage(stage: &str, wait: ScriptWait) {\n"
            "    // execute regular modules stage scripts\n"
            '    if let Err(e) = crate::module::exec_stage_script(stage, wait) {\n'
            '        warn!("Failed to exec {stage} scripts: {e}");\n'
            "    }\n"
            "\n"
            "    // run lua stage script\n"
            f"    {CFG_GATE}\n"
            f"    if let Err(e) = {call} {{\n"
            '        warn!("Failed to exec {stage} lua: {e}");\n'
            "    }\n"
            "}\n"
        )

    def test_patches_the_original_e0425_form(self):
        """932d9bd2: run_stage kept its old `block` name at the lua call site."""
        with tempfile.TemporaryDirectory() as temp_dir:
            result, patched = self._run(
                pathlib.Path(temp_dir), self._source(BROKEN_FORMS["E0425"])
            )

            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn(FIXED_CALL, patched)
            self.assertNotIn(BROKEN_FORMS["E0425"], patched)
            self.assertIn(CFG_GATE, patched)
            self.assertIn("pub fn run_stage(stage: &str, wait: ScriptWait) {", patched)

    def test_patches_the_reworked_e0308_form(self):
        """1c56b5e9: the call now passes `wait`, but exec_stage_lua still wants a bool.

        This is the form current upstream main ships. It arrived the day after the fix
        was written, so a patch that only knew the first shape would have silently
        no-op'd its way past a build break it was written to prevent.
        """
        with tempfile.TemporaryDirectory() as temp_dir:
            result, patched = self._run(
                pathlib.Path(temp_dir), self._source(BROKEN_FORMS["E0308"])
            )

            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn(FIXED_CALL, patched)
            self.assertNotIn(BROKEN_FORMS["E0308"], patched)
            self.assertIn(CFG_GATE, patched)

    def test_reports_which_upstream_shape_it_found(self):
        """The label names the compiler error, so the log says what was hit.

        Without it a broken build reports only that some call was rewritten, and the
        next person to look has to diff upstream by hand to work out which revision this
        patch is now carrying.
        """
        for name, call in BROKEN_FORMS.items():
            with tempfile.TemporaryDirectory() as temp_dir, self.subTest(form=name):
                result, _ = self._run(pathlib.Path(temp_dir), self._source(call))

                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertIn(name, result.stdout)

    def test_is_idempotent(self):
        """Running twice must not double-apply the fix.

        The workflow calls this once per job, but a retried job re-runs the whole step
        against a fresh clone, and a locally cached tree could be patched twice. Without
        the marker guard the second pass would rewrite a line that no longer matches and
        fail the build.
        """
        with tempfile.TemporaryDirectory() as temp_dir:
            tmp = pathlib.Path(temp_dir)
            first, patched = self._run(tmp, self._source(BROKEN_FORMS["E0308"]))
            self.assertEqual(first.returncode, 0, first.stderr)

            second, again = self._run(tmp, patched)

            self.assertEqual(second.returncode, 0, second.stderr)
            self.assertEqual(again, patched)
            self.assertEqual(again.count(FIXED_CALL), 1)
            self.assertIn("already present", second.stdout)

    def test_refuses_to_touch_a_source_it_does_not_understand(self):
        """A matching call inside unexpected surrounding code means upstream refactored.

        Patching anyway would risk rewriting a signature the patch has not read. Failing
        is the honest outcome -- the alternative is a silently mangled file that still
        fails to build.
        """
        for name, call in BROKEN_FORMS.items():
            with tempfile.TemporaryDirectory() as temp_dir, self.subTest(form=name):
                result, patched = self._run(
                    pathlib.Path(temp_dir),
                    "pub fn run_stage(stage: &str, wait: ScriptWait) {\n"
                    f"    if let Err(e) = {call} {{\n"
                    "        handle(stage, e);\n"
                    "    }\n"
                    "}\n",
                )

                self.assertEqual(result.returncode, 1, result.stdout)
                self.assertIn("surrounding block does not match", result.stderr)
                self.assertIn(call, patched)

    def test_exits_loudly_when_the_source_file_is_gone(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            empty = pathlib.Path(temp_dir) / "no-such-ksud"
            empty.mkdir()
            result = subprocess.run(
                [sys.executable, str(SCRIPT_PATH), str(empty)],
                capture_output=True,
                text=True,
            )

            self.assertEqual(result.returncode, 1)
            self.assertIn("not found", result.stderr)

    def test_becomes_a_no_op_once_upstream_compiles(self):
        """The exit condition for this whole fix: upstream no longer needs carrying.

        When the broken call is simply absent -- upstream fixed it, or refactored the call
        away -- the patch must succeed silently. Failing here would break the build at
        exactly the moment the patch stops being needed.
        """
        with tempfile.TemporaryDirectory() as temp_dir:
            already_fixed = self._source(FIXED_CALL)
            result, patched = self._run(pathlib.Path(temp_dir), already_fixed)

            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(patched, already_fixed)
            self.assertIn("nothing to do", result.stdout)

    def test_the_mapping_preserves_the_pre_refactor_call_semantics(self):
        """The boolean handed to exec_stage_lua must match what callers passed before.

        932d9bd2 replaced `block: bool` with `wait: ScriptWait`, and exec_stage_lua still
        wants a plain bool. run_stage has three callers: post-mount passes a deadline,
        service and boot-completed pass NoWait. Before the refactor those were true, false
        and false. Any mapping other than NoWait -> false breaks one of them, and because
        run_lua ignores the flag (`_wait` is never read) nothing would notice at runtime --
        only the built binary's behaviour would quietly differ. So assert the table
        directly rather than trusting the expression to read correctly.
        """
        self.assertIn("matches!(wait, ScriptWait::NoWait)", FIXED_CALL)

        # Mirrors run_stage's callers in upstream init_event.rs, as the argument they
        # pass. The fixed expression is `!matches!(wait, ScriptWait::NoWait)`.
        post_mount = "Until(..)"  # run_stage("post-mount", ScriptWait::Until(..))
        service = "NoWait"  # run_stage("service", ScriptWait::NoWait)
        boot_completed = "NoWait"  # run_stage("boot-completed", ScriptWait::NoWait)

        def passes_bool(wait: str) -> bool:
            """The fixed expression, evaluated the way rustc would."""
            return not (wait == "NoWait")

        # Before the refactor these three were true, false and false.
        self.assertTrue(passes_bool(post_mount))
        self.assertFalse(passes_bool(service))
        self.assertFalse(passes_bool(boot_completed))

    def test_both_workflows_apply_both_patches(self):
        for workflow_path in (APP_WORKFLOW_PATH, APP_DEV_WORKFLOW_PATH):
            workflow = workflow_path.read_text(encoding="utf-8")
            with self.subTest(workflow=workflow_path.name):
                self.assertIn(
                    'python3 .github/scripts/patch-ksud-uapi-compat.py "$source_dir"',
                    workflow,
                )
                self.assertIn(
                    'python3 .github/scripts/patch-ksud-script-wait.py "$source_dir"',
                    workflow,
                )

    def test_uapi_patch_still_applies_beside_it(self):
        """Both patches edit the same tree, sequentially, in one job.

        This script targets init_event.rs and the uapi patch targets ksucalls.rs, so they
        cannot collide today -- but only if both still find their anchors. If upstream
        moves either function, whichever fails first would be the one that stops the
        build, and it would be worth knowing which.
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

            for script in (UAPI_PATCH_PATH, SCRIPT_PATH):
                result = subprocess.run(
                    [sys.executable, str(script), str(source_dir)],
                    capture_output=True,
                    text=True,
                )
                with self.subTest(script=script.name):
                    self.assertEqual(result.returncode, 0, result.stderr)


if __name__ == "__main__":
    unittest.main()