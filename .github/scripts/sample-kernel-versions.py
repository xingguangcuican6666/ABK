#!/usr/bin/env python3
"""随机抽取 GKI 内核版本组合，用于 PR 抽样编译检查。

候选池与 .github/workflows/kernel-a*.yml 的 matrix 严格一致（也被
cli/tests/test_kernel_random_sampling.py 交叉校验，防止两处漂移）。
输出 GitHub Actions matrix 可直接 fromJSON() 的 JSON：
    {"include": [ {"android_version": ..., "kernel_version": ..., "sub_level": ...,
                   "os_patch_level": ..., "revision": ..., "ksu_variant": ...}, ... ],
     "count": N, "seed": "...", "source": "random" | "fixed"}
"""

from __future__ import annotations

import argparse
import json
import random
import sys
from pathlib import Path

DEFAULT_VARIANTS = ("Official", "SukiSU", "BakaSU")

# (android_version, kernel_version, sub_level, os_patch_level, revision)
# android12/5.10 必须带 revision（与 kernel-a12-5-10.yml matrix 相同取值）；
# X/lts 只在自动构建矩阵启用的内核线出现（android16/android17 均未启用，故不列出）。
CANDIDATES: tuple[tuple[str, str, str, str, str], ...] = (
    ("android12", "5.10", "66", "2022-01", "r11"),
    ("android12", "5.10", "81", "2022-03", "r11"),
    ("android12", "5.10", "101", "2022-04", "r28"),
    ("android12", "5.10", "110", "2022-07", "r1"),
    ("android12", "5.10", "117", "2022-09", "r1"),
    ("android12", "5.10", "136", "2022-11", "r15"),
    ("android12", "5.10", "149", "2023-01", "r1"),
    ("android12", "5.10", "160", "2023-03", "r1"),
    ("android12", "5.10", "168", "2023-04", "r9"),
    ("android12", "5.10", "177", "2023-07", "r3"),
    ("android12", "5.10", "185", "2023-09", "r1"),
    ("android12", "5.10", "198", "2024-01", "r17"),
    ("android12", "5.10", "205", "2024-03", "r1"),
    ("android12", "5.10", "209", "2024-05", "r13"),
    ("android12", "5.10", "218", "2024-08", "r14"),
    ("android12", "5.10", "226", "2024-11", "r8"),
    ("android12", "5.10", "233", "2025-02", "r1"),
    ("android12", "5.10", "236", "2025-05", "r1"),
    ("android12", "5.10", "237", "2025-06", "r1"),
    ("android12", "5.10", "240", "2025-09", "r1"),
    ("android12", "5.10", "246", "2025-12", "r1"),
    ("android12", "5.10", "X", "lts", "r1"),
    ("android13", "5.15", "74", "2023-01", ""),
    ("android13", "5.15", "78", "2023-03", ""),
    ("android13", "5.15", "94", "2023-05", ""),
    ("android13", "5.15", "104", "2023-07", ""),
    ("android13", "5.15", "119", "2023-09", ""),
    ("android13", "5.15", "123", "2023-11", ""),
    ("android13", "5.15", "137", "2024-01", ""),
    ("android13", "5.15", "144", "2024-03", ""),
    ("android13", "5.15", "148", "2024-05", ""),
    ("android13", "5.15", "149", "2024-07", ""),
    ("android13", "5.15", "151", "2024-08", ""),
    ("android13", "5.15", "153", "2024-09", ""),
    ("android13", "5.15", "167", "2024-11", ""),
    ("android13", "5.15", "170", "2025-01", ""),
    ("android13", "5.15", "178", "2025-03", ""),
    ("android13", "5.15", "180", "2025-05", ""),
    ("android13", "5.15", "185", "2025-07", ""),
    ("android13", "5.15", "189", "2025-09", ""),
    ("android13", "5.15", "194", "2025-12", ""),
    ("android13", "5.15", "X", "lts", ""),
    ("android14", "6.1", "25", "2023-10", ""),
    ("android14", "6.1", "43", "2023-11", ""),
    ("android14", "6.1", "57", "2024-01", ""),
    ("android14", "6.1", "68", "2024-03", ""),
    ("android14", "6.1", "75", "2024-05", ""),
    ("android14", "6.1", "78", "2024-06", ""),
    ("android14", "6.1", "84", "2024-07", ""),
    ("android14", "6.1", "90", "2024-08", ""),
    ("android14", "6.1", "93", "2024-09", ""),
    ("android14", "6.1", "99", "2024-10", ""),
    ("android14", "6.1", "112", "2024-11", ""),
    ("android14", "6.1", "115", "2024-12", ""),
    ("android14", "6.1", "118", "2025-01", ""),
    ("android14", "6.1", "124", "2025-02", ""),
    ("android14", "6.1", "128", "2025-03", ""),
    ("android14", "6.1", "129", "2025-04", ""),
    ("android14", "6.1", "134", "2025-05", ""),
    ("android14", "6.1", "138", "2025-06", ""),
    ("android14", "6.1", "145", "2025-09", ""),
    ("android14", "6.1", "157", "2025-12", ""),
    ("android14", "6.1", "162", "2026-03", ""),
    ("android14", "6.1", "X", "lts", ""),
    ("android15", "6.6", "50", "2024-10", ""),
    ("android15", "6.6", "56", "2024-11", ""),
    ("android15", "6.6", "57", "2024-12", ""),
    ("android15", "6.6", "58", "2025-01", ""),
    ("android15", "6.6", "66", "2025-02", ""),
    ("android15", "6.6", "77", "2025-03", ""),
    ("android15", "6.6", "82", "2025-04", ""),
    ("android15", "6.6", "87", "2025-05", ""),
    ("android15", "6.6", "89", "2025-06", ""),
    ("android15", "6.6", "98", "2025-09", ""),
    ("android15", "6.6", "102", "2025-10", ""),
    ("android15", "6.6", "118", "2026-01", ""),
    ("android15", "6.6", "127", "2026-04", ""),
    ("android15", "6.6", "X", "lts", ""),
    ("android16", "6.12", "23", "2025-06", ""),
    ("android16", "6.12", "38", "2025-09", ""),
    ("android16", "6.12", "58", "2025-12", ""),
    ("android17", "6.18", "20", "2026-04", ""),
    ("android17", "6.18", "21", "2026-06", ""),
    ("android17", "6.18", "32", "2026-09", ""),
)


def parse_fixed_combo(line: str) -> dict[str, str]:
    """解析一行固定组合：android kernel sub patch [revision] [variant]。"""
    fields = line.split()
    if len(fields) < 4:
        raise SystemExit(f"固定组合格式错误（至少 4 个字段）: {line!r}")
    android, kernel, sub, patch = fields[:4]
    revision = fields[4] if len(fields) > 4 else ""
    variant = fields[5] if len(fields) > 5 else "BakaSU"
    if (android, kernel, sub, patch, revision) not in CANDIDATES:
        known = " ".join((android, kernel, sub, patch, revision))
        raise SystemExit(
            f"固定组合不在候选池内（需与 kernel-a*.yml matrix 一致）: {known!r}"
        )
    if variant not in DEFAULT_VARIANTS:
        raise SystemExit(f"未知 KernelSU 变体: {variant!r}，可选: {', '.join(DEFAULT_VARIANTS)}")
    return {
        "android_version": android,
        "kernel_version": kernel,
        "sub_level": sub,
        "os_patch_level": patch,
        "revision": revision,
        "ksu_variant": variant,
    }


def main() -> None:
    # Windows runner 的 stdout/stderr 默认按控制台 ANSI 代码页（如 cp936）
    # 编码，中文消息会被 backslashreplace 成 \uXXXX 转义；强制 UTF-8，
    # 保证任何平台上错误消息与 JSON 输出一致。
    for stream in (sys.stdout, sys.stderr):
        if hasattr(stream, "reconfigure"):
            stream.reconfigure(encoding="utf-8")

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--seed", required=True, help="随机种子（PR 号 + head SHA + run_id）")
    parser.add_argument("--count-min", type=int, default=3, help="最小抽取数量")
    parser.add_argument("--count-max", type=int, default=6, help="最大抽取数量")
    parser.add_argument(
        "--variants",
        default=",".join(DEFAULT_VARIANTS),
        help="参与随机的 KernelSU 变体（逗号分隔）",
    )
    parser.add_argument(
        "--fixed",
        action="append",
        default=[],
        metavar="COMBO",
        help="固定组合（跳过随机抽样），可重复；格式: android kernel sub patch [revision] [variant]",
    )
    parser.add_argument("--output", default="-", help="JSON 输出路径（默认 stdout）")
    args = parser.parse_args()

    variants = tuple(v.strip() for v in args.variants.split(",") if v.strip())
    unknown = [v for v in variants if v not in DEFAULT_VARIANTS]
    if unknown:
        raise SystemExit(f"未知 KernelSU 变体: {unknown}，可选: {', '.join(DEFAULT_VARIANTS)}")
    if not variants:
        raise SystemExit("KernelSU 变体列表为空")

    if args.count_min < 0 or args.count_max < args.count_min:
        raise SystemExit("--count-min/--count-max 参数不合法")

    if args.fixed:
        include = [parse_fixed_combo(line) for line in args.fixed]
        source = "fixed"
        count = len(include)
    else:
        rng = random.Random(args.seed)
        count = rng.randint(args.count_min, min(args.count_max, len(CANDIDATES)))
        picked = rng.sample(CANDIDATES, count)
        include = [
            {
                "android_version": android,
                "kernel_version": kernel,
                "sub_level": sub,
                "os_patch_level": patch,
                "revision": revision,
                "ksu_variant": rng.choice(variants),
            }
            for android, kernel, sub, patch, revision in picked
        ]
        source = "random"

    result = {"include": include, "count": count, "seed": args.seed, "source": source}

    payload = json.dumps(result, ensure_ascii=False, indent=2)
    if args.output == "-":
        print(payload)
    else:
        Path(args.output).write_text(payload + "\n", encoding="utf-8")

    print(f"抽样 {count} 个内核组合 (source={source}, seed={args.seed})", file=sys.stderr)


if __name__ == "__main__":
    main()
