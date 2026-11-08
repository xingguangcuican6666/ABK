import json
import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT_PATH = ROOT / ".github" / "scripts" / "resolve-lkm-run.sh"
ACTION_PATH = ROOT / ".github" / "actions" / "prepare-abk-app-inputs" / "action.yml"
APP_WORKFLOW_PATH = ROOT / ".github" / "workflows" / "build-abk-app.yml"
APP_DEV_WORKFLOW_PATH = ROOT / ".github" / "workflows" / "build-abk-app-dev.yml"

# The script under test is a POSIX CI helper: bash plus jq, mktemp, sed and herestrings.
# It only ever runs on the ubuntu-latest jobs that build the app, so these tests are
# POSIX-only -- Git-for-Windows' bash is present and even has jq, but does not reproduce
# the script faithfully enough to assert on its exit status. The suite additionally runs
# inside the minimal python:3.12-bookworm image used for the 32-bit cross-builds, which
# has bash but no jq.
SKIP_REASON = "resolve-lkm-run.sh needs POSIX bash with jq; this platform cannot run it"
CAN_RUN_SCRIPT = os.name != "nt" and all(
    shutil.which(tool) for tool in ("bash", "jq")
)


def _looks_like_missing_tool(stderr: str) -> bool:
    """Detect bash's "command not found" in the languages it actually emits.

    An English build says "jq: command not found"; the Debian-based image used for the
    32-bit cross-builds says "jq: 未找到命令".
    """
    return "not found" in stderr or "未找到命令" in stderr or "找不到" in stderr

# Stands in for curl so the script's real request/response handling runs unchanged.
FAKE_CURL = """#!/usr/bin/env bash
set -euo pipefail
url=""
out=""
prev=""
for arg in "$@"; do
  case "$prev" in
    -o) out="$arg" ;;
  esac
  case "$arg" in
    http*) url="$arg" ;;
  esac
  prev="$arg"
done
printf '%s\\n' "$url" >> "$FAKE_CURL_SEEN"

case "$url" in
  *'/actions/runs/'*'/artifacts'*)
    run_id="$(printf '%s' "$url" | sed -n 's|.*/actions/runs/\\([0-9]*\\)/artifacts.*|\\1|p')"
    body="$(python3 -c 'import json,sys
table = json.load(open(sys.argv[1]))
sys.stdout.write(json.dumps(table.get(sys.argv[2], {"total_count": 0, "artifacts": []})))' \\
      "$FAKE_CURL_ARTIFACTS" "$run_id")"
    ;;
  *)
    body="$(cat "$FAKE_CURL_RUNS")"
    ;;
esac

if [ -n "$out" ]; then
  printf '%s' "$body" > "$out"
else
  printf '%s' "$body"
fi
"""


class ResolveLkmRunTests(unittest.TestCase):
    """Covers the run selection the app bundler depends on.

    The bundler needs a run that is both successful and still downloadable. Artifact
    retention is 90 days, so an expired run keeps reporting conclusion=success while
    its artifact list comes back empty -- picking the newest success alone hands the
    bundler nothing at all.
    """

    @staticmethod
    def _run_entry(run_id, created_at, conclusion="success", status="completed"):
        return {
            "id": run_id,
            "created_at": created_at,
            "status": status,
            "conclusion": conclusion,
            "head_sha": f"sha{run_id}",
        }

    @staticmethod
    def _artifacts(*names, expired=False):
        return {
            "total_count": len(names),
            "artifacts": [
                {"id": index, "name": name, "expired": expired}
                for index, name in enumerate(names, start=1)
            ],
        }

    def _fake_api(self, tmp: Path, runs, artifacts_by_run):
        """Serve canned API responses; return (bin_dir, requested_urls_path)."""
        runs_file = tmp / "runs.json"
        runs_file.write_text(json.dumps(runs), encoding="utf-8")
        artifacts_file = tmp / "artifacts.json"
        artifacts_file.write_text(json.dumps(artifacts_by_run), encoding="utf-8")

        bindir = tmp / "bin"
        bindir.mkdir()
        curl = bindir / "curl"
        curl.write_text(FAKE_CURL, encoding="utf-8")
        curl.chmod(0o755)
        return bindir, tmp / "requested.txt", runs_file, artifacts_file

    def _run(self, bindir, requested, runs_file, artifacts_file, env_extra=None):
        env = dict(os.environ)
        env["PATH"] = f"{bindir}{os.pathsep}{env['PATH']}"
        env["GITHUB_TOKEN"] = "test-token"
        env["LKM_REPO"] = "owner/repo"
        env["FAKE_CURL_SEEN"] = str(requested)
        env["FAKE_CURL_RUNS"] = str(runs_file)
        env["FAKE_CURL_ARTIFACTS"] = str(artifacts_file)
        env.update(env_extra or {})
        result = subprocess.run(
            ["bash", str(SCRIPT_PATH)],
            capture_output=True,
            text=True,
            env=env,
            cwd=str(ROOT),
        )
        # A missing tool shows up as exit 127 with a "not found" line on stderr, in
        # whatever language bash is built with. Both forms are checked because the
        # 32-bit cross-builds run the suite inside python:3.12-bookworm, whose bash
        # reports in Chinese.
        if result.returncode == 127 or _looks_like_missing_tool(result.stderr):
            self.fail(
                f"{SKIP_REASON}: rc={result.returncode} stderr={result.stderr!r}"
            )
        return result

    @unittest.skipUnless(CAN_RUN_SCRIPT, SKIP_REASON)
    def test_expired_newest_run_falls_back_to_older_live_run(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            bindir, requested, runs_file, artifacts_file = self._fake_api(
                Path(temp_dir),
                {
                    "total_count": 2,
                    "workflow_runs": [
                        self._run_entry(900, "2026-10-05T12:00:00Z"),
                        self._run_entry(800, "2026-09-01T12:00:00Z"),
                    ],
                },
                {
                    "900": self._artifacts(expired=True),
                    "800": self._artifacts("kernelsu-stable-android14-6.1-lkm"),
                },
            )

            result = self._run(bindir, requested, runs_file, artifacts_file)

            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("run_id=800", result.stdout)
            self.assertIn("head_sha=sha800", result.stdout)

    @unittest.skipUnless(CAN_RUN_SCRIPT, SKIP_REASON)
    def test_newest_live_run_wins_without_probing_older_ones(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            bindir, requested, runs_file, artifacts_file = self._fake_api(
                Path(temp_dir),
                {
                    "total_count": 2,
                    "workflow_runs": [
                        self._run_entry(900, "2026-10-05T12:00:00Z"),
                        self._run_entry(800, "2026-09-01T12:00:00Z"),
                    ],
                },
                {"900": self._artifacts("kernelsu-stable-android14-6.1-lkm")},
            )

            result = self._run(bindir, requested, runs_file, artifacts_file)

            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("run_id=900", result.stdout)
            # No reason to probe an older run once the newest one has live artifacts.
            self.assertNotIn("800", requested.read_text(encoding="utf-8"))

    @unittest.skipUnless(CAN_RUN_SCRIPT, SKIP_REASON)
    def test_runs_are_ordered_by_created_at_not_response_order(self):
        """The API does not guarantee response order, so the script sorts.

        This is the failure that motivated the helper: with per_page=1 the listing can
        hand back a months-old run whose artifacts have since expired, and the app
        build then bundles zero files.

        Both runs here are live, so falling back cannot rescue a wrong pick -- the only
        thing that can tell them apart is the ordering. A script that trusts response
        position answers 800; only an explicit sort answers 900.
        """
        with tempfile.TemporaryDirectory() as temp_dir:
            bindir, requested, runs_file, artifacts_file = self._fake_api(
                Path(temp_dir),
                {
                    "total_count": 2,
                    # Oldest first, to catch a script that trusts position.
                    "workflow_runs": [
                        self._run_entry(800, "2026-09-01T12:00:00Z"),
                        self._run_entry(900, "2026-10-05T12:00:00Z"),
                    ],
                },
                {
                    "800": self._artifacts("kernelsu-stable-android14-6.1-lkm"),
                    "900": self._artifacts("sukisu-stable-android15-6.6-lkm"),
                },
            )

            result = self._run(bindir, requested, runs_file, artifacts_file)

            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("run_id=900", result.stdout)
            self.assertNotIn("run_id=800", result.stdout)

    @unittest.skipUnless(CAN_RUN_SCRIPT, SKIP_REASON)
    def test_failed_and_in_progress_runs_are_never_candidates(self):
        """A failed run can be newer than the last good one; it is never the answer.

        The failure run here is live, so nothing but the status filter could rule it
        out -- a script that trusts the API's own status=success query instead of
        re-checking each candidate would happily bundle artifacts from a red build.
        """
        with tempfile.TemporaryDirectory() as temp_dir:
            bindir, requested, runs_file, artifacts_file = self._fake_api(
                Path(temp_dir),
                {
                    "total_count": 3,
                    "workflow_runs": [
                        self._run_entry(900, "2026-10-05T12:00:00Z", conclusion="failure"),
                        self._run_entry(
                            850, "2026-10-05T11:00:00Z", conclusion=None, status="in_progress"
                        ),
                        self._run_entry(800, "2026-09-01T12:00:00Z"),
                    ],
                },
                {
                    "900": self._artifacts("kernelsu-stable-android14-6.1-lkm"),
                    "850": self._artifacts("kernelsu-stable-android14-6.1-lkm"),
                    "800": self._artifacts("kernelsu-stable-android14-6.1-lkm"),
                },
            )

            result = self._run(bindir, requested, runs_file, artifacts_file)

            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("run_id=800", result.stdout)
            # It must not even ask about them: a live failed run is not a fallback.
            asked = requested.read_text(encoding="utf-8")
            self.assertNotIn("/actions/runs/900/artifacts", asked)
            self.assertNotIn("/actions/runs/850/artifacts", asked)

    @unittest.skipUnless(CAN_RUN_SCRIPT, SKIP_REASON)
    def test_partially_expired_run_is_still_usable(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            bindir, requested, runs_file, artifacts_file = self._fake_api(
                Path(temp_dir),
                {
                    "total_count": 1,
                    "workflow_runs": [self._run_entry(900, "2026-10-05T12:00:00Z")],
                },
                {
                    "900": {
                        "total_count": 2,
                        "artifacts": [
                            {
                                "id": 1,
                                "name": "kernelsu-stable-android14-6.1-lkm",
                                "expired": True,
                            },
                            {
                                "id": 2,
                                "name": "sukisu-stable-android15-6.6-lkm",
                                "expired": False,
                            },
                        ],
                    }
                },
            )

            result = self._run(bindir, requested, runs_file, artifacts_file)

            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("run_id=900", result.stdout)
            self.assertIn("artifact_count=1", result.stdout)

    @unittest.skipUnless(CAN_RUN_SCRIPT, SKIP_REASON)
    def test_all_runs_expired_fails_loudly(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            bindir, requested, runs_file, artifacts_file = self._fake_api(
                Path(temp_dir),
                {
                    "total_count": 2,
                    "workflow_runs": [
                        self._run_entry(900, "2026-10-05T12:00:00Z"),
                        self._run_entry(800, "2026-09-01T12:00:00Z"),
                    ],
                },
                {
                    "900": self._artifacts(expired=True),
                    "800": self._artifacts(expired=True),
                },
            )

            result = self._run(bindir, requested, runs_file, artifacts_file)

            self.assertEqual(result.returncode, 1)
            self.assertIn("re-run lkm.yml", result.stderr)
            self.assertEqual(result.stdout, "")

    @unittest.skipUnless(CAN_RUN_SCRIPT, SKIP_REASON)
    def test_no_successful_run_fails_loudly(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            bindir, requested, runs_file, artifacts_file = self._fake_api(
                Path(temp_dir), {"total_count": 0, "workflow_runs": []}, {}
            )

            result = self._run(bindir, requested, runs_file, artifacts_file)

            self.assertEqual(result.returncode, 1)
            self.assertIn("no successful", result.stderr)

    @unittest.skipUnless(CAN_RUN_SCRIPT, SKIP_REASON)
    def test_missing_token_is_rejected_before_any_api_call(self):
        env = dict(os.environ)
        env.pop("GITHUB_TOKEN", None)
        env["LKM_REPO"] = "owner/repo"

        result = subprocess.run(
            ["bash", str(SCRIPT_PATH)],
            capture_output=True,
            text=True,
            env=env,
            cwd=str(ROOT),
        )

        self.assertEqual(result.returncode, 1)
        self.assertIn("GITHUB_TOKEN", result.stderr)

    def test_cache_key_and_bundle_use_the_same_resolved_run(self):
        """One resolution, two consumers.

        The inputs step keys the cache on head_sha and the bundler step downloads by
        run id. Looking the run up independently in each place could name two different
        runs -- a cache built from one and assets from another -- so the action emits
        the run id and both workflows read it from the step output.
        """
        action = ACTION_PATH.read_text(encoding="utf-8")
        self.assertIn("resolve-lkm-run.sh", action)
        self.assertIn("lkm_run_id", action)

        for workflow_path in (APP_WORKFLOW_PATH, APP_DEV_WORKFLOW_PATH):
            workflow = workflow_path.read_text(encoding="utf-8")
            with self.subTest(workflow=workflow_path.name):
                self.assertIn(
                    'run_id="${{ steps.abk-inputs.outputs.lkm_run_id }}"', workflow
                )
                self.assertNotIn(".workflow_runs[0]", workflow)


if __name__ == "__main__":
    unittest.main()