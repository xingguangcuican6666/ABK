"""PR 内核抽样编译检查的回归测试。

覆盖三处防漂移约束：
1. sample-kernel-versions.py 的候选池与 kernel-a*.yml 的 matrix 完全一致
   （新增/删除 sub_level 或 patch level 时两边必须同步）。
2. 抽样行为：3~6 个、组合不重复、同种子可复现、字段合法、root 实现取自变体池。
3. kernel-pr-sampling.yml 的功能参数全关、触发路径覆盖工作流/源码/补丁。
"""

import importlib.util
import json
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SAMPLER_PATH = ROOT / ".github" / "scripts" / "sample-kernel-versions.py"
WORKFLOW_PATH = ROOT / ".github" / "workflows" / "kernel-pr-sampling.yml"
KERNEL_MATRIX_WORKFLOWS = {
    "android12": ("5.10", ROOT / ".github" / "workflows" / "kernel-a12-5-10.yml"),
    "android13": ("5.15", ROOT / ".github" / "workflows" / "kernel-a13-5-15.yml"),
    "android14": ("6.1", ROOT / ".github" / "workflows" / "kernel-a14-6-1.yml"),
    "android15": ("6.6", ROOT / ".github" / "workflows" / "kernel-a15-6-6.yml"),
    "android16": ("6.12", ROOT / ".github" / "workflows" / "kernel-a16-6-12.yml"),
    "android17": ("6.18", ROOT / ".github" / "workflows" / "kernel-a17-6-18.yml"),
}
VALID_COMBOS = {"android12", "android13", "android14", "android15", "android16", "android17"}


def load_sampler():
    spec = importlib.util.spec_from_file_location("sample_kernel_versions", SAMPLER_PATH)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def parse_matrix_entries(workflow_text: str) -> list[tuple[str, str, str]]:
    """从 kernel-a*.yml 提取生效的 matrix include 条目 (sub_level, os_patch_level, revision)。"""
    include_block = workflow_text.split("matrix:", 1)[1].split("uses:", 1)[0]
    entries: list[tuple[str, str, str]] = []
    current: dict[str, str] = {}
    for line in include_block.splitlines():
        stripped = line.strip()
        if stripped.startswith("#"):
            continue
        match = re.match(r'-?\s*(sub_level|os_patch_level|revision):\s*"([^"]*)"', stripped)
        if not match:
            continue
        key, value = match.group(1), match.group(2)
        if key == "sub_level":
            if current:
                entries.append(
                    (current.get("sub_level", ""), current.get("os_patch_level", ""), current.get("revision", ""))
                )
            current = {"sub_level": value}
        else:
            current[key] = value
    if current:
        entries.append(
            (current.get("sub_level", ""), current.get("os_patch_level", ""), current.get("revision", ""))
        )
    return entries


class SamplerCandidatePoolTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.sampler = load_sampler()

    def test_candidate_pool_matches_kernel_matrices(self):
        expected: set[tuple[str, str, str, str, str]] = set()
        for android_version, (kernel_version, path) in KERNEL_MATRIX_WORKFLOWS.items():
            with self.subTest(workflow=path.name):
                for sub_level, os_patch_level, revision in parse_matrix_entries(
                    path.read_text(encoding="utf-8")
                ):
                    expected.add(
                        (android_version, kernel_version, sub_level, os_patch_level, revision)
                    )
        self.assertEqual(expected, set(self.sampler.CANDIDATES))

    def test_android12_entries_always_carry_revision(self):
        for entry in self.sampler.CANDIDATES:
            if entry[0] == "android12":
                self.assertRegex(entry[4], r"^r\d+$")
            else:
                self.assertEqual(entry[4], "")

    def test_lts_entries_use_x_sub_level(self):
        for entry in self.sampler.CANDIDATES:
            if entry[3] == "lts":
                self.assertEqual(entry[2], "X")


class SamplerBehaviorTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.sampler = load_sampler()

    def sample(self, seed: str, count_min: int = 3, count_max: int = 6, variants=None):
        import argparse
        import io
        import contextlib

        argv = [
            "sample-kernel-versions.py",
            "--seed", seed,
            "--count-min", str(count_min),
            "--count-max", str(count_max),
        ]
        if variants:
            argv += ["--variants", variants]
        parser = argparse.ArgumentParser()
        parser.add_argument("--seed", required=True)
        parser.add_argument("--count-min", type=int, default=3)
        parser.add_argument("--count-max", type=int, default=6)
        parser.add_argument("--variants", default=",".join(self.sampler.DEFAULT_VARIANTS))
        parser.add_argument("--fixed", action="append", default=[])
        parser.add_argument("--output", default="-")
        args = parser.parse_args(argv[1:])

        # 复用被测模块的抽样逻辑
        rng = __import__("random").Random(args.seed)
        variants_tuple = tuple(v.strip() for v in args.variants.split(",") if v.strip())
        count = rng.randint(args.count_min, min(args.count_max, len(self.sampler.CANDIDATES)))
        picked = rng.sample(self.sampler.CANDIDATES, count)
        return [
            {
                "android_version": android,
                "kernel_version": kernel,
                "sub_level": sub,
                "os_patch_level": patch,
                "revision": revision,
                "ksu_variant": rng.choice(variants_tuple),
            }
            for android, kernel, sub, patch, revision in picked
        ]

    def run_cli(self, seed: str, *extra: str) -> dict:
        import subprocess
        import sys

        result = subprocess.run(
            [sys.executable, "-I", str(SAMPLER_PATH), "--seed", seed, *extra],
            capture_output=True,
            text=True,
            check=True,
        )
        return json.loads(result.stdout)

    def test_cli_sample_count_within_range_and_unique(self):
        for seed in ("pr-1", "pr-2", "pr-42-sha-run", "another-seed"):
            with self.subTest(seed=seed):
                payload = self.run_cli(seed)
                self.assertIn(payload["count"], range(3, 7))
                self.assertEqual(payload["count"], len(payload["include"]))
                keys = [
                    (e["android_version"], e["kernel_version"], e["sub_level"], e["os_patch_level"])
                    for e in payload["include"]
                ]
                self.assertEqual(len(keys), len(set(keys)))

    def test_cli_same_seed_is_reproducible(self):
        first = self.run_cli("stable-seed")
        second = self.run_cli("stable-seed")
        self.assertEqual(first, second)

    def test_cli_different_seeds_diverge(self):
        first = self.run_cli("seed-a")
        second = self.run_cli("seed-b")
        self.assertNotEqual(
            [(e["sub_level"], e["android_version"]) for e in first["include"]],
            [(e["sub_level"], e["android_version"]) for e in second["include"]],
        )

    def test_cli_entries_are_valid_combos(self):
        payload = self.run_cli("validation-seed", "--count-max", "20")
        candidates = set(self.sampler.CANDIDATES)
        variants = set(self.sampler.DEFAULT_VARIANTS)
        for entry in payload["include"]:
            combo = (
                entry["android_version"],
                entry["kernel_version"],
                entry["sub_level"],
                entry["os_patch_level"],
                entry["revision"],
            )
            self.assertIn(combo, candidates)
            self.assertIn(entry["ksu_variant"], variants)
            self.assertIn(entry["android_version"], VALID_COMBOS)

    def test_cli_variant_restriction_is_honored(self):
        payload = self.run_cli("variant-seed", "--variants", "Official", "--count-max", "20")
        for entry in payload["include"]:
            self.assertEqual(entry["ksu_variant"], "Official")

    def test_cli_fixed_combos_skip_sampling(self):
        payload = self.run_cli(
            "fixed-seed",
            "--fixed", "android12 5.10 66 2022-01 r11 BakaSU",
            "--fixed", "android16 6.12 23 2025-06",
        )
        self.assertEqual(payload["source"], "fixed")
        self.assertEqual(payload["count"], 2)
        self.assertEqual(payload["include"][0]["ksu_variant"], "BakaSU")
        # 未指定变体时默认 BakaSU
        self.assertEqual(payload["include"][1]["ksu_variant"], "BakaSU")

    def test_cli_rejects_unknown_fixed_combo(self):
        import subprocess
        import sys

        result = subprocess.run(
            [
                sys.executable, "-I", str(SAMPLER_PATH),
                "--seed", "bad", "--fixed", "android99 9.9 1 2099-01",
            ],
            capture_output=True,
            text=True,
            # Windows 子进程管道默认按 ANSI 代码页（cp936）解码；脚本已
            # reconfigure UTF-8，这里显式按 UTF-8 解码，任何平台一致。
            encoding="utf-8",
        )
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("候选池", result.stderr)


class WorkflowDefinitionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = WORKFLOW_PATH.read_text(encoding="utf-8")

    def test_caller_permissions_cover_build_workflow(self):
        # build.yml 顶层声明 contents: write / actions: write；调用方权限
        # 低于被调用方会让 GitHub 在启动阶段直接拒绝（startup_failure）。
        permissions = self.workflow.split("permissions:", 1)[1].split("on:", 1)[0]
        self.assertIn("contents: write", permissions)
        self.assertIn("actions: write", permissions)

    def test_matrix_output_only_contains_include(self):
        # matrix 轴只允许 include（数组）；count/seed/source 混入
        # fromJSON(matrix) 会导致 strategy 评估失败，build job 全部跳过。
        sample_job = self.workflow.split("  sample:", 1)[1].split("  build:", 1)[0]
        matrix_line = next(
            line for line in sample_job.splitlines() if 'echo "matrix=' in line
        )
        self.assertIn("{include: .include}", matrix_line)
        count_line = next(
            line for line in sample_job.splitlines() if 'echo "count=' in line
        )
        self.assertIn(".count", count_line)

    def test_build_job_disables_all_feature_flags(self):
        build_job = self.workflow.split("  build:", 1)[1].split("  result:", 1)[0]
        for flag, expected in (
            ("use_zram", "false"),
            ("use_bbg", "false"),
            ("use_ddk", "false"),
            ("use_ntsync", "false"),
            ("use_networking", "false"),
            ("use_kpm", "false"),
            ("use_rekernel", "false"),
            ("enable_susfs", "false"),
            ("supp_op", "false"),
            ("virtualization_support", '"off"'),
            ("zram_full_algo", "false"),
            ("upload_aux_artifacts", "false"),
        ):
            with self.subTest(flag=flag):
                self.assertIn(f"{flag}: {expected}", build_job)
        for empty in ("zram_extra_algos", "kpm_password", "custom_external_modules", "custom_kernel_options"):
            with self.subTest(empty=empty):
                self.assertIn(f'{empty}: ""', build_job)

    def test_build_job_uses_sampled_matrix_and_build_workflow(self):
        build_job = self.workflow.split("  build:", 1)[1].split("  result:", 1)[0]
        self.assertIn("matrix: ${{ fromJSON(needs.sample.outputs.matrix) }}", build_job)
        self.assertIn("uses: ./.github/workflows/build.yml", build_job)
        self.assertIn("fail-fast: false", build_job)
        self.assertIn("ksu_variant: ${{ matrix.ksu_variant }}", build_job)

    def test_trigger_paths_cover_workflow_source_and_patches(self):
        triggers = self.workflow.split("jobs:", 1)[0]
        required_paths = (
            ".github/workflows/build.yml",
            ".github/scripts/**",
            "ddk/**",
            "modules/**",
            "zram/**",
            "hmbird_patch.c",
        )
        for path in required_paths:
            with self.subTest(path=path):
                self.assertIn(f'- "{path}"', triggers)

    def test_result_job_gates_on_build_and_rejects(self):
        result_job = self.workflow.split("  result:", 1)[1]
        self.assertIn("needs.build.result", result_job)
        self.assertIn('"*-Rejects"', result_job)
        self.assertIn("补丁冲突", result_job)

    def test_seed_includes_pr_head_sha(self):
        self.assertIn("github.sha", self.workflow)
        self.assertIn("github.run_attempt", self.workflow)


if __name__ == "__main__":
    unittest.main()
