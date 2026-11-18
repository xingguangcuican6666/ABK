import argparse
import contextlib
import importlib.util
import io
import json
import os
import pathlib
import tempfile
import unittest
from unittest import mock


ROOT = pathlib.Path(__file__).resolve().parents[2]
SCRIPT_PATH = ROOT / "cli" / "abk.py"


class _TempConfig:
    """Import cli/abk.py with cwd and both config roots pointed at a temporary tree."""

    def __init__(self, root: pathlib.Path, xdg: pathlib.Path):
        self.root = root
        self.xdg = xdg

    def __enter__(self):
        self._previous_cwd = pathlib.Path.cwd()
        self._env = mock.patch.dict(
            os.environ,
            {"XDG_CONFIG_HOME": str(self.xdg), "APPDATA": str(self.xdg), "ABK_LANG": "en-US"},
        )
        # Register the undo before doing anything that can fail: __exit__ is not called
        # when __enter__ raises, and leaving the process inside a deleted temp directory
        # breaks every later test.
        try:
            os.chdir(self.root)
            # APPDATA wins over XDG on Windows, so pin both to the temporary tree and
            # keep the developer's real ~/.config or %APPDATA%\abk out of the test.
            self._env.start()
            spec = importlib.util.spec_from_file_location("abk_cli_under_test", SCRIPT_PATH)
            self.module = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(self.module)
            # i18n caches its catalog in module state, and other test modules may
            # already have loaded it for a different language; pin it so the assertions
            # below can check the English wording.
            self.module.load_translations("en-US")
        except BaseException:
            self._restore()
            raise
        return self.module

    def _restore(self):
        self._env.stop()
        os.chdir(self._previous_cwd)

    def __exit__(self, *exc):
        self._restore()
        return False


class LegacyConfigWarningTests(unittest.TestCase):
    """A config written into the working directory by the empty-XDG bug is reported."""

    def _load(self, module):
        stderr = io.StringIO()
        with contextlib.redirect_stderr(stderr):
            config = module.load_config()
        return config, stderr.getvalue()

    def test_warns_and_leaves_the_legacy_file_alone(self):
        with tempfile.TemporaryDirectory() as raw:
            tmp = pathlib.Path(raw)
            legacy = tmp / "abk" / "config.json"
            legacy.parent.mkdir()
            original = json.dumps({"access_token": "gho_legacy"}, indent=2) + "\n"
            legacy.write_text(original, encoding="utf-8")

            xdg = tmp / "xdg"
            with _TempConfig(tmp, xdg) as module:
                config, stderr = self._load(module)

            self.assertEqual(config, {})
            normalised = stderr.replace(os.sep, "/")
            self.assertIn("abk/config.json", normalised)
            self.assertIn("may have been written", stderr)
            self.assertEqual(legacy.read_text(encoding="utf-8"), original)
            self.assertFalse((xdg / "abk" / "config.json").exists())

    def test_notice_is_only_printed_once(self):
        with tempfile.TemporaryDirectory() as raw:
            tmp = pathlib.Path(raw)
            (tmp / "abk").mkdir()
            (tmp / "abk" / "config.json").write_text("{}\n", encoding="utf-8")

            with _TempConfig(tmp, tmp / "xdg") as module:
                _, first = self._load(module)
                _, second = self._load(module)

            self.assertIn("may have been written", first)
            self.assertEqual(second, "")

    def test_check_stays_armed_until_a_file_appears(self):
        """A file restored after the first load (for example during login) is still reported."""
        with tempfile.TemporaryDirectory() as raw:
            tmp = pathlib.Path(raw)
            with _TempConfig(tmp, tmp / "xdg") as module:
                _, first = self._load(module)
                self.assertEqual(first, "")

                (tmp / "abk").mkdir()
                (tmp / "abk" / "config.json").write_text("{}\n", encoding="utf-8")
                _, second = self._load(module)

            self.assertIn("may have been written", second)

    def test_no_warning_without_a_legacy_file(self):
        with tempfile.TemporaryDirectory() as raw:
            tmp = pathlib.Path(raw)
            with _TempConfig(tmp, tmp / "xdg") as module:
                _, stderr = self._load(module)
            self.assertEqual(stderr, "")

    def test_no_warning_when_the_legacy_file_is_the_real_config(self):
        """cwd/abk/config.json *is* the active config here, so there is nothing to migrate."""
        with tempfile.TemporaryDirectory() as raw:
            tmp = pathlib.Path(raw)
            stored = {"access_token": "gho_active", "lang": "en-US"}
            (tmp / "abk").mkdir()
            (tmp / "abk" / "config.json").write_text(
                json.dumps(stored), encoding="utf-8"
            )

            with _TempConfig(tmp, tmp) as module:
                self.assertEqual(module.CONFIG_FILE, tmp / "abk" / "config.json")
                config, stderr = self._load(module)

            self.assertEqual(stderr, "")
            self.assertEqual(config.get("access_token"), "gho_active")

    def test_symlink_loop_is_not_a_crash(self):
        """A looping CONFIG_FILE makes Path.resolve() raise; that must not escape."""
        with tempfile.TemporaryDirectory() as raw:
            tmp = pathlib.Path(raw)
            (tmp / "abk").mkdir()
            (tmp / "abk" / "config.json").write_text("{}\n", encoding="utf-8")

            xdg = tmp / "xdg"
            (xdg / "abk").mkdir(parents=True)
            loop = xdg / "abk" / "config.json"
            try:
                loop.symlink_to(loop)
            except OSError as exc:
                # Windows without developer mode (and some filesystems) refuse to
                # create symlinks: that is an environment limitation, not a regression.
                self.skipTest(f"cannot create symlinks here: {exc}")

            with _TempConfig(tmp, xdg) as module:
                self.assertFalse(module.CONFIG_FILE.exists())
                config, stderr = self._load(module)

            self.assertEqual(config, {})
            # The active path cannot be resolved, so there is nothing useful to compare
            # against: stay quiet instead of crashing or guessing.
            self.assertEqual(stderr, "")

    def test_json_mode_still_reports_the_notice(self):
        """--json captures stderr while the command runs; the notice must survive it."""
        with tempfile.TemporaryDirectory() as raw:
            tmp = pathlib.Path(raw)
            (tmp / "abk").mkdir()
            (tmp / "abk" / "config.json").write_text("{}\n", encoding="utf-8")

            with _TempConfig(tmp, tmp / "xdg") as module:
                def fake_whoami(args):  # noqa: ARG001 - mimics a command that reads the config
                    args._json_result = {"loggedIn": bool(module.load_config())}
                    return 0

                args = argparse.Namespace(command="whoami", func=fake_whoami)
                stdout, stderr = io.StringIO(), io.StringIO()
                with (
                    contextlib.redirect_stdout(stdout),
                    contextlib.redirect_stderr(stderr),
                ):
                    exit_code = module._run_json_command(args)

            self.assertEqual(exit_code, 0)
            payload = json.loads(stdout.getvalue())
            self.assertFalse(payload["loggedIn"])
            self.assertIn("may have been written", stderr.getvalue())

    @unittest.skipIf(os.name == "nt", "chmod does not remove traverse permission on Windows")
    @unittest.skipIf(hasattr(os, "geteuid") and os.geteuid() == 0, "root ignores permissions")
    def test_unreadable_legacy_directory_is_not_a_crash(self):
        """An untraversable abk/ in the working directory must not break credential lookup."""
        with tempfile.TemporaryDirectory() as raw:
            tmp = pathlib.Path(raw)
            blocked = tmp / "abk"
            blocked.mkdir()
            (blocked / "config.json").write_text("{}\n", encoding="utf-8")
            blocked.chmod(0o600)

            try:
                with _TempConfig(tmp, tmp / "xdg") as module:
                    self.assertRaises(PermissionError, module.LEGACY_RELATIVE_CONFIG.is_file)
                    config, stderr = self._load(module)
            finally:
                blocked.chmod(0o755)

            self.assertEqual(config, {})
            self.assertEqual(stderr, "")

    def test_closed_stderr_does_not_drop_the_json(self):
        """A dead stderr pipe is not a reason to lose the command's own output."""
        class BrokenStream(io.StringIO):
            def write(self, _text):
                raise BrokenPipeError(32, "Broken pipe")

        with tempfile.TemporaryDirectory() as raw:
            tmp = pathlib.Path(raw)
            (tmp / "abk").mkdir()
            (tmp / "abk" / "config.json").write_text("{}\n", encoding="utf-8")

            with _TempConfig(tmp, tmp / "xdg") as module:
                def fake_whoami(args):
                    args._json_result = {"loggedIn": bool(module.load_config())}
                    return 0

                args = argparse.Namespace(command="whoami", func=fake_whoami)
                stdout = io.StringIO()
                with (
                    contextlib.redirect_stdout(stdout),
                    contextlib.redirect_stderr(BrokenStream()),
                ):
                    exit_code = module._run_json_command(args)

            self.assertEqual(exit_code, 0)
            self.assertFalse(json.loads(stdout.getvalue())["loggedIn"])
