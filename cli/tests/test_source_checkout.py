"""Exercise scoped private-source credentials with real local Git."""
import base64
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
CHECKOUT = ROOT / ".github/scripts/checkout-custom-source.sh"


@unittest.skipUnless(os.name != "nt" and shutil.which("git") and shutil.which("bash"), "requires POSIX Git and Bash")
class SourceCheckoutTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.remote = self.root / "remote"
        self.real_git = shutil.which("git")
        self.config = self.root / "global.gitconfig"
        self.config.write_text('[user]\n\tname = Checkout test\n\temail = test@example.invalid\n', encoding="utf-8")
        self.original_config = self.config.read_bytes()
        self.env = {k: v for k, v in os.environ.items() if not k.startswith("GIT_CONFIG_")}
        self.env.update(
            GIT_CONFIG_GLOBAL=str(self.config), GIT_CONFIG_SYSTEM=os.devnull,
            GIT_CONFIG_COUNT="1", GIT_CONFIG_KEY_0="review.inherited", GIT_CONFIG_VALUE_0="preserved",
            GITHUB_STEP_SUMMARY=str(self.root / "summary"),
        )
        self.git("init", "-b", "gki-android15-6.6", str(self.remote))
        (self.remote / "source").write_text("pinned source", encoding="utf-8")
        self.git("-C", str(self.remote), "add", ".")
        self.git("-C", str(self.remote), "commit", "-m", "pinned")
        self.pinned = self.git("-C", str(self.remote), "rev-parse", "HEAD")
        (self.remote / "source").write_text("new upstream source", encoding="utf-8")
        self.git("-C", str(self.remote), "commit", "-am", "new upstream")

    def git(self, *args):
        result = subprocess.run([self.real_git, *args], env=self.env, text=True, encoding="utf-8", capture_output=True, check=True)
        return result.stdout.strip()

    def install_git_observer(self):
        directory = self.root / "bin"
        directory.mkdir()
        observer = directory / "git"
        observer.write_text('''#!/usr/bin/env bash
set -eu
if [ "${REVIEW_OBSERVE:-}" = true ]; then
  "$REVIEW_REAL_GIT" config --get review.inherited >> "$REVIEW_INHERITED_LOG"
  "$REVIEW_REAL_GIT" config --get-urlmatch http.extraheader https://github.com/example/private.git >> "$REVIEW_AUTH_LOG" || true
fi
exec "$REVIEW_REAL_GIT" "$@"
''', encoding="utf-8")
        observer.chmod(0o755)
        self.env.update(PATH=str(directory) + os.pathsep + self.env["PATH"],
                        REVIEW_REAL_GIT=self.real_git, REVIEW_OBSERVE="true",
                        REVIEW_INHERITED_LOG=str(self.root / "inherited.log"),
                        REVIEW_AUTH_LOG=str(self.root / "auth.log"))

    def run_checkout(self, private=True, fail=False):
        work = self.root / f"work-{private}-{fail}"
        work.mkdir()
        env = dict(self.env, SOURCE_PRIVATE=str(private).lower(),
                   ABK_CUSTOM_SOURCE_GITHUB_TOKEN="test-token-never-real",
                   SOURCE_REPO=(self.root / "missing").as_uri() if fail else self.remote.as_uri(),
                   SOURCE_COMMIT=self.pinned)
        result = subprocess.run([shutil.which("bash"), str(CHECKOUT)], cwd=work, env=env,
                                capture_output=True, text=True, encoding="utf-8", errors="replace")
        if fail:
            self.assertNotEqual(result.returncode, 0)
        else:
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(self.git("-C", str(work / "common"), "rev-parse", "HEAD"), self.pinned)
        self.assertEqual(self.config.read_bytes(), self.original_config)
        # No newly written Git config contains credentials.
        for path in self.root.rglob("config"):
            if path.is_file():
                value = path.read_text(encoding="utf-8")
                self.assertNotIn("extraheader", value.lower())
                self.assertNotIn("test-token-never-real", value)
        return result

    def test_private_auth_is_scoped_for_success(self):
        self.install_git_observer()
        expected = "AUTHORIZATION: basic " + base64.b64encode(b"x-access-token:test-token-never-real").decode()
        self.run_checkout()
        headers = (self.root / "auth.log").read_text().splitlines()
        self.assertTrue(headers)
        self.assertEqual(set(headers), {expected})
        self.assertEqual(set((self.root / "inherited.log").read_text().splitlines()), {"preserved"})
        self.assertEqual(self.git("config", "--get", "review.inherited"), "preserved")

    def test_failure_does_not_leave_credentials(self):
        self.run_checkout(fail=True)

    def test_private_auth_accepts_wrapping_base64_without_gnu_flags(self):
        self.install_git_observer()
        encoder = self.root / "bin/base64"
        encoder.write_text('''#!/usr/bin/env python3
import base64
import sys
assert len(sys.argv) == 1, "GNU-only base64 flags are not supported"
encoded = base64.b64encode(sys.stdin.buffer.read()).decode()
sys.stdout.write("\\r\\n".join(encoded[i:i+8] for i in range(0, len(encoded), 8)) + "\\r\\n")
''', encoding="utf-8")
        encoder.chmod(0o755)
        self.run_checkout()
        expected = "AUTHORIZATION: basic " + base64.b64encode(b"x-access-token:test-token-never-real").decode()
        self.assertEqual(set((self.root / "auth.log").read_text().splitlines()), {expected})

    def test_public_checkout_does_not_set_auth_headers(self):
        self.install_git_observer()
        self.run_checkout(private=False)
        self.assertEqual((self.root / "auth.log").read_text(), "")

    def test_missing_private_token_fails_before_checkout(self):
        work = self.root / "missing-token"
        work.mkdir()
        env = dict(self.env, SOURCE_PRIVATE="true", ABK_CUSTOM_SOURCE_GITHUB_TOKEN="")
        result = subprocess.run([shutil.which("bash"), str(CHECKOUT)], cwd=work, env=env, capture_output=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse((work / "common").exists())
        self.assertEqual(self.config.read_bytes(), self.original_config)
