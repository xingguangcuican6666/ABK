"""Exercise cache reuse, upstream changes and clean checkouts with real Git."""
import importlib.util
import errno
import os
import re
import shutil
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path
from unittest import mock


ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("cached_downloads", ROOT / ".github/scripts/cached-downloads.py")
CACHE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CACHE)


class CacheConfigurationTests(unittest.TestCase):
    def test_hosted_runner_and_opt_out_do_not_create_local_cache(self):
        for environment, mode in (("github-hosted", "auto"), ("self-hosted", "off")):
            with self.subTest(environment=environment), tempfile.TemporaryDirectory() as temp:
                root = Path(temp)
                values = {"ABK_RUNNER_ENVIRONMENT": environment, "KERNEL_LOCAL_CACHE": mode,
                          "KERNEL_LOCAL_CACHE_DIR": str(root / "cache"), "GITHUB_ENV": str(root / "env")}
                with mock.patch.dict(os.environ, values, clear=True):
                    CACHE.configure()
                self.assertIn("ABK_LOCAL_CACHE_ENABLED=false", (root / "env").read_text())
                self.assertFalse((root / "cache").exists())

    def test_cache_inside_workspace_is_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            values = {"ABK_RUNNER_ENVIRONMENT": "self-hosted", "GITHUB_WORKSPACE": str(root),
                      "KERNEL_LOCAL_CACHE_DIR": str(root / "cache"), "GITHUB_ENV": str(root / "env")}
            with mock.patch.dict(os.environ, values, clear=True):
                with self.assertRaisesRegex(ValueError, "outside GITHUB_WORKSPACE"):
                    CACHE.configure()
            self.assertFalse((root / "cache").exists())


@unittest.skipUnless(os.name != "nt" and shutil.which("git"), "local download cache requires POSIX Git")
class GitDownloadCacheTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        previous_directory = os.getcwd()
        os.chdir(self.root)
        self.addCleanup(os.chdir, previous_directory)
        self.remote = self.root / "remote"
        self.git("init", "-b", "main", str(self.remote))
        self.git("-C", str(self.remote), "config", "user.name", "Cache test")
        self.git("-C", str(self.remote), "config", "user.email", "cache@example.test")
        self.sha = self.commit("original")
        self.url = self.remote.as_uri()
        self.environment = mock.patch.dict(os.environ, {
            "ABK_LOCAL_CACHE_ROOT": str(self.root / "cache"), "GITHUB_STEP_SUMMARY": str(self.root / "summary"),
            "GIT_CONFIG_GLOBAL": os.devnull, "GIT_CONFIG_SYSTEM": os.devnull,
        })
        self.environment.start()
        self.addCleanup(self.environment.stop)
        self.calls = mock.patch.object(CACHE, "run", wraps=CACHE.run)
        self.runner = self.calls.start()
        self.addCleanup(self.calls.stop)

    def git(self, *args):
        return subprocess.run([shutil.which("git"), *args], check=True, capture_output=True,
                              text=True, encoding="utf-8").stdout.strip()

    def commit(self, text):
        (self.remote / "source").write_text(text, encoding="utf-8")
        self.git("-C", str(self.remote), "add", "source")
        self.git("-C", str(self.remote), "commit", "-m", text)
        return self.git("-C", str(self.remote), "rev-parse", "HEAD")

    def fetch_count(self):
        return sum("fetch" in call.args[0] for call in self.runner.call_args_list)

    def test_unchanged_branch_uses_objects_and_discards_previous_build_modifications(self):
        first = self.root / "build-one"
        CACHE.clone(self.url, first, "main")
        count = self.fetch_count()
        (first / "source").write_text("patched by previous build")
        (first / "old-module").write_text("untracked build file")
        second = self.root / "build-two"
        CACHE.clone(self.url, second, "main")
        self.assertEqual(self.fetch_count(), count)
        self.assertEqual((second / "source").read_text(), "original")
        self.assertFalse((second / "old-module").exists())
        self.assertEqual(self.git("-C", str(second), "status", "--porcelain"), "")
        self.assertIn("unchanged: reused", (self.root / "summary").read_text())

    def test_changed_branch_fetches_new_commit_and_keeps_the_previous_checkout(self):
        first = self.root / "build-one"
        CACHE.clone(self.url, first, "main")
        count = self.fetch_count()
        changed = self.commit("upstream update")
        second = self.root / "build-two"
        CACHE.clone(self.url, second, "main")
        self.assertEqual(self.fetch_count(), count + 1)
        self.assertEqual(self.git("-C", str(second), "rev-parse", "HEAD"), changed)
        self.assertEqual((first / "source").read_text(), "original")
        self.assertEqual((second / "source").read_text(), "upstream update")

    def test_cached_immutable_commit_works_without_contacting_the_remote(self):
        CACHE.clone(self.url, self.root / "build-one", self.sha)
        count = self.fetch_count()
        shutil.rmtree(self.remote)
        second = self.root / "build-two"
        CACHE.clone(self.url, second, self.sha)
        self.assertEqual(self.fetch_count(), count)
        self.assertEqual((second / "source").read_text(), "original")

    def test_annotated_tag_resolves_to_the_commit(self):
        self.git("-C", str(self.remote), "tag", "-a", "v1", "-m", "first version")
        for index, ref in enumerate(("v1", "refs/tags/v1")):
            destination = self.root / f"build-{index}"
            CACHE.clone(self.url, destination, ref)
            self.assertEqual(self.git("-C", str(destination), "rev-parse", "HEAD"), self.sha)

    def test_shallow_downloads_can_be_reused_and_upgraded_to_full_history(self):
        latest = self.commit("second commit")
        first = self.root / "shallow"
        CACHE.clone(self.url, first, "main", depth=1)
        self.assertEqual(self.git("-C", str(first), "rev-list", "--count", "HEAD"), "1")
        count = self.fetch_count()
        CACHE.clone(self.url, self.root / "shallow-two", "main", depth=1)
        self.assertEqual(self.fetch_count(), count)
        full = self.root / "full"
        CACHE.clone(self.url, full, "main")
        self.assertEqual(self.git("-C", str(full), "rev-list", "--count", "HEAD"), "2")
        self.assertEqual(self.git("-C", str(full), "rev-parse", "HEAD"), latest)

    def test_unavailable_branch_does_not_fall_back_to_a_stale_checkout(self):
        CACHE.clone(self.url, self.root / "build-one", "main")
        self.git("-C", str(self.remote), "branch", "-m", "renamed")
        destination = self.root / "build-two"
        with self.assertRaisesRegex(ValueError, "requested Git ref not found"):
            CACHE.clone(self.url, destination, "main")
        self.assertFalse(destination.exists())

    def test_same_ref_names_in_different_repositories_do_not_share_commits(self):
        CACHE.clone(self.url, self.root / "build-one", "main")
        other = self.root / "other"
        self.git("clone", str(self.remote), str(other))
        self.git("-C", str(other), "config", "user.name", "Other test")
        self.git("-C", str(other), "config", "user.email", "other@example.test")
        (other / "source").write_text("other repository")
        self.git("-C", str(other), "commit", "-am", "other repository")
        destination = self.root / "build-two"
        CACHE.clone(other.as_uri(), destination, "main")
        self.assertEqual((destination / "source").read_text(), "other repository")

    def test_checkout_inside_cache_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "outside the download cache"):
            CACHE.clone(self.url, self.root / "cache/build", "main")

    @unittest.skipUnless(shutil.which("bash"), "requires Bash")
    def test_workflow_resolves_short_pins_after_upstream_advances(self):
        branch = "gki-android15-6.6"
        self.git("-C", str(self.remote), "branch", "-m", branch)
        self.commit("new upstream source")
        text = (ROOT / ".github/workflows/build.yml").read_text(encoding="utf-8")
        step = text.split("- name: 克隆依赖仓库", 1)[1].split("- name: 克隆自定义外部模块", 1)[0]
        script = textwrap.dedent(step.split("run: |\n", 1)[1])
        retry = re.search(r"(?ms)^retry_git_clone\(\) \{.*?^\}", script).group()
        block = script.split('if [ "${{ inputs.enable_susfs }}" == "true" ]; then', 1)[1]
        block = 'if [ "true" == "true" ]; then' + block.split('echo "准备补丁资源..."', 1)[0]
        block = block.replace('https://gitlab.com/simonpunk/susfs4ksu.git', self.url)
        script = 'set -euo pipefail\n' + retry + '\n' + block
        for index, pin in enumerate((self.sha[:12], self.sha)):
            work = self.root / f"pinned-build-{index}"
            (work / "config").mkdir(parents=True)
            (work / "config/config").write_text(f"custom=true\n{branch}={pin}\n", encoding="utf-8")
            env = dict(os.environ, ABK_LOCAL_CACHE_ENABLED="true", GITHUB_WORKSPACE=str(ROOT),
                       SUSFS_BRANCH=branch, GITHUB_ENV=str(work / "env"))
            result = subprocess.run([shutil.which("bash"), "-c", script], cwd=work, env=env,
                                    text=True, encoding="utf-8", capture_output=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(self.git("-C", str(work / "susfs4ksu"), "rev-parse", "HEAD"), self.sha)
            self.assertEqual((work / "susfs4ksu/source").read_text(), "original")
            self.commit(f"upstream update after build {index}")


@unittest.skipUnless(os.name != "nt" and os.environ.get("ABK_TEST_REPO"), "repo integration requires ABK_TEST_REPO")
class RepoDownloadCacheTests(unittest.TestCase):
    def test_manifest_updates_and_clean_builds_reuse_project_objects(self):
        def git(*args):
            return subprocess.run([shutil.which("git"), *map(str, args)], check=True, capture_output=True,
                                  text=True, encoding="utf-8").stdout.strip()

        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            servers = root / "servers"
            servers.mkdir()
            for name in ("alpha", "beta", "manifest"):
                project = servers / (name + ".git")
                git("init", "-b", "main", project)
                git("-C", project, "config", "user.name", "Repo test")
                git("-C", project, "config", "user.email", "repo@example.test")
                if name != "manifest":
                    (project / "source").write_text(name + " original")
                else:
                    (project / "default.xml").write_text(
                        f'<manifest><remote name="local" fetch="{servers.as_uri()}"/>'
                        '<default remote="local" revision="main"/>'
                        '<project name="alpha" path="common"><copyfile src="source" dest="copy"/></project>'
                        '<project name="beta" path="prebuilts/clang"/></manifest>')
                git("-C", project, "add", ".")
                git("-C", project, "commit", "-m", "initial")
                if name != "manifest":
                    git("-C", project, "commit", "--allow-empty", "-m", "second commit")
            values = {"ABK_LOCAL_CACHE_ROOT": str(root / "cache"), "GITHUB_STEP_SUMMARY": str(root / "summary"),
                      "REPO_URL": os.environ["ABK_TEST_REPO_SOURCE"], "GIT_CONFIG_SYSTEM": os.devnull,
                      "GIT_CONFIG_COUNT": "3", "GIT_CONFIG_KEY_0": "user.name", "GIT_CONFIG_VALUE_0": "Repo test",
                      "GIT_CONFIG_KEY_1": "user.email", "GIT_CONFIG_VALUE_1": "repo@example.test",
                      "GIT_CONFIG_KEY_2": "fetch.unpackLimit", "GIT_CONFIG_VALUE_2": "1"}
            with mock.patch.dict(os.environ, values):
                def build(name, links=True):
                    destination = root / name
                    destination.mkdir()
                    CACHE.sync_repo(os.environ["ABK_TEST_REPO"], (servers / "manifest.git").as_uri(),
                                    "main", destination)
                    # Immutable project packs are local hard links; source files
                    # and shallow-boundary metadata belong to this checkout.
                    packs = list((destination / ".repo/project-objects").rglob("*.pack"))
                    self.assertTrue(packs)
                    self.assertEqual(all(path.stat().st_nlink >= 2 for path in packs), links)
                    self.assertEqual(git("-C", destination / "common", "rev-list", "--count", "HEAD"), "1")
                    return destination

                first = build("first")
                (first / "common/source").write_text("patched by the build")
                (first / "common/stale-module").write_text("old module")
                second = build("second")
                self.assertEqual((second / "common/source").read_text(), "alpha original")
                self.assertEqual((second / "copy").read_text(), "alpha original")
                self.assertFalse((second / "common/stale-module").exists())
                beta_before = git("-C", second / "prebuilts/clang", "rev-parse", "HEAD")
                alpha = servers / "alpha.git"
                (alpha / "source").write_text("alpha updated")
                git("-C", alpha, "commit", "-am", "upstream update")
                third = build("third")
                self.assertEqual((third / "common/source").read_text(), "alpha updated")
                self.assertEqual(git("-C", third / "prebuilts/clang", "rev-parse", "HEAD"), beta_before)
                self.assertIn("common`: unchanged: reused", (root / "summary").read_text())
                with mock.patch.object(CACHE.os, "link", side_effect=OSError(errno.EXDEV, "different device")):
                    fourth = build("fourth", links=False)
                self.assertEqual((fourth / "common/source").read_text(), "alpha updated")


if __name__ == "__main__":
    unittest.main()
