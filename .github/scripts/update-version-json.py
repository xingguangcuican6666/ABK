#!/usr/bin/env python3

import argparse
import json
import re
import sys
from pathlib import Path

UNSTABLE_DOWNLOAD_URLS = {
    "normal": "https://nightly.link/xingguangcuican6666/ABK/workflows/build-abk-app/dev/abk-apks.zip",
    "dev": "https://nightly.link/xingguangcuican6666/ABK/workflows/build-abk-app-dev/dev/abk-apks.zip",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Update unstable app metadata in version.json")
    parser.add_argument("--channel", choices=sorted(UNSTABLE_DOWNLOAD_URLS.keys()), required=True)
    parser.add_argument("--workflow-name", required=True)
    parser.add_argument("--run-id", type=int, required=True)
    parser.add_argument("--commit-sha", required=True)
    parser.add_argument("--published-at", required=True)
    parser.add_argument("--build-timestamp-epoch-millis", type=int, required=True)
    parser.add_argument("--version-json", default="version.json")
    parser.add_argument("--build-file", default="app/build.gradle.kts")
    return parser.parse_args()


def read_app_version(build_file: Path) -> tuple[int, str]:
    text = build_file.read_text(encoding="utf-8")
    version_code_patterns = [
        r"^\s*val\s+appVersionCode\s*=\s*(\d+)\s*$",
        r"^\s*versionCode\s*=\s*(\d+)\s*$",
    ]
    version_name_patterns = [
        r'^\s*val\s+appVersionName\s*=\s*"([^"]+)"\s*$',
        r'^\s*versionName\s*=\s*"([^"]+)"\s*$',
    ]

    version_code = None
    version_name = None
    for pattern in version_code_patterns:
        match = re.search(pattern, text, re.MULTILINE)
        if match:
            version_code = int(match.group(1))
            break
    for pattern in version_name_patterns:
        match = re.search(pattern, text, re.MULTILINE)
        if match:
            version_name = match.group(1)
            break

    if version_code is None or not version_name:
        raise SystemExit(f"Unable to parse app version from {build_file}")
    return version_code, version_name


def ensure_object(value):
    return value if isinstance(value, dict) else {}


def main() -> int:
    args = parse_args()
    version_json_path = Path(args.version_json)
    build_file = Path(args.build_file)
    version_code, version_name = read_app_version(build_file)

    if version_json_path.exists():
        raw = json.loads(version_json_path.read_text(encoding="utf-8"))
        if not isinstance(raw, dict):
            raise SystemExit("version.json root must be an object")
        data = raw
    else:
        data = {}

    stable = ensure_object(data.get("stable"))
    unstable = ensure_object(data.get("unstable"))

    stable["normal"] = ensure_object(stable.get("normal"))
    stable["dev"] = ensure_object(stable.get("dev"))
    unstable["normal"] = ensure_object(unstable.get("normal"))
    unstable["dev"] = ensure_object(unstable.get("dev"))
    unstable[args.channel] = {
        "versionName": version_name,
        "versionCode": version_code,
        "downloadUrl": UNSTABLE_DOWNLOAD_URLS[args.channel],
        "publishedAt": args.published_at,
        "buildTimestampEpochMillis": args.build_timestamp_epoch_millis,
        "sourceWorkflow": args.workflow_name,
        "commitSha": args.commit_sha,
        "runId": args.run_id,
    }

    data["stable"] = stable
    data["unstable"] = unstable
    version_json_path.write_text(
        json.dumps(data, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
